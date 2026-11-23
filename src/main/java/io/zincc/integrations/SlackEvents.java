package io.zincc.integrations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@Profile("agents & oidc")
class SlackEvents {

	private final IntegrationStore store;

	private final Environment env;

	private final JsonMapper json;

	SlackEvents(IntegrationStore store, Environment env, JsonMapper json) {
		this.store = store;
		this.env = env;
		this.json = json;
	}

	static boolean verified(String secret, String timestamp, String signature, byte[] body, Instant now) {
		if (secret.isBlank() || timestamp == null || !timestamp.matches("[0-9]{10}") || signature == null
				|| !signature.matches("v0=[a-f0-9]{64}")
				|| Math.abs(now.getEpochSecond() - Long.parseLong(timestamp)) > 300)
			return false;
		try {
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.US_ASCII));
			return MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(signature.substring(3)));
		}
		catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	@PostMapping("/api/integrations/slack/events")
	Map<String, String> receive(HttpServletRequest req) throws IOException {
		byte[] bytes = req.getInputStream().readNBytes(65537);
		if (bytes.length > 65536)
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
		if (env.getProperty("zincc.local-only", Boolean.class, false)
				|| !verified(env.getProperty("ZINCC_SLACK_SIGNING_SECRET", ""),
						req.getHeader("X-Slack-Request-Timestamp"), req.getHeader("X-Slack-Signature"), bytes,
						Instant.now()))
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		var payload = json.readTree(bytes);
		if (payload == null || !payload.isObject())
			throw IntegrationStore.bad("Invalid event");
		if ("url_verification".equals(payload.path("type").asString("")))
			return Map.of("challenge", ProviderClient.required(payload, "challenge"));
		var event = payload.path("event");
		String eid = payload.path("event_id").asString("");
		if (!"event_callback".equals(payload.path("type").asString(""))
				|| !"message".equals(event.path("type").asString("")) || event.has("bot_id")
				|| event.path("subtype").asString("").equals("bot_message") || !eid.matches("[A-Za-z0-9_-]{1,128}"))
			return Map.of();
		for (var route : store.db.queryForList("SELECT * FROM zincc.slack_routes(?,?)",
				payload.path("team_id").asString(""), event.path("channel").asString(""))) {
			store.signal(new IntegrationStore.Scope((UUID) route.get("workspace_id"), (UUID) route.get("owner_id")),
					(UUID) route.get("id"), eid);
		}
		return Map.of();
	}

}
