package io.zincc.integrations;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("agents")
class ProviderClient {

	static final String GMAIL = "https://www.googleapis.com/auth/gmail.readonly";
	static final String CALENDAR = "https://www.googleapis.com/auth/calendar.events.readonly";

	record Grant(String secret, String scope, String team) {
	}

	record Item(String id, String summary, String stamp, boolean removed) {
	}

	record Page(List<Item> items, String next, String anchor, String checkpoint, boolean snapshot) {
	}

	static class Failure extends RuntimeException {

		final String state;

		final int delay;

		Failure(String state, int delay) {
			super(state);
			this.state = state;
			this.delay = delay;
		}

	}

	private final Environment env;

	private final JsonMapper json;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(3))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	ProviderClient(Environment env, JsonMapper json) {
		this.env = env;
		this.json = json;
	}

	String scope(String provider) {
		return switch (provider) {
			case "gmail" -> GMAIL;
			case "calendar" -> CALENDAR;
			case "slack" -> "channels:history";
			default -> throw new IllegalArgumentException();
		};
	}

	private String key(String provider, String field) {
		return env.getProperty("ZINCC_" + ("slack".equals(provider) ? "SLACK" : "GOOGLE") + "_" + field, "");
	}

	String callback(String p) {
		return env.getProperty("slack".equals(p) ? "ZINCC_SLACK_CALLBACK" : "ZINCC_GOOGLE_CALLBACK",
				"http://127.0.0.1:8080/api/integrations/callback");
	}

	boolean available(String p) {
		return !env.getProperty("zincc.local-only", Boolean.class, false) && !key(p, "CLIENT_ID").isBlank()
				&& !key(p, "CLIENT_SECRET").isBlank() && (!p.equals("slack") || callback(p).startsWith("https://"));
	}

	static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String form(Map<String, String> fields) {
		return fields.entrySet()
			.stream()
			.map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
			.collect(Collectors.joining("&"));
	}

	static String verifier() {
		byte[] bytes = new byte[32];
		new java.security.SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String challenge(String verifier) {
		try {
			return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(java.security.MessageDigest.getInstance("SHA-256")
					.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	String authorize(String p, String state, String verifier) {
		if (!available(p))
			throw new Failure("unconfigured", 0);
		var args = new HashMap<>(Map.of("client_id", key(p, "CLIENT_ID"), "redirect_uri", callback(p), "scope",
				scope(p), "state", state));
		if (p.equals("slack"))
			return "https://slack.com/oauth/v2/authorize?" + form(args);
		args.put("response_type", "code");
		args.put("code_challenge_method", "S256");
		args.put("code_challenge", challenge(verifier));
		args.put("access_type", "offline");
		args.put("prompt", "consent select_account");
		return "https://accounts.google.com/o/oauth2/v2/auth?" + form(args);
	}

	private JsonNode token(String p, Map<String, String> fields) {
		var args = new HashMap<>(fields);
		args.put("client_id", key(p, "CLIENT_ID"));
		args.put("client_secret", key(p, "CLIENT_SECRET"));
		return request(HttpRequest
			.newBuilder(URI.create(p.equals("slack") ? "https://slack.com/api/oauth.v2.access"
					: "https://oauth2.googleapis.com/token"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form(args))));
	}

	Grant exchange(String p, String code, String verifier) {
		var args = new HashMap<>(Map.of("grant_type", "authorization_code", "redirect_uri", callback(p), "code", code));
		if (!p.equals("slack"))
			args.put("code_verifier", verifier);
		var reply = token(p, args);
		if (p.equals("slack") && reply.has("refresh_token"))
			throw new Failure("unsupported_token_rotation", 0);
		String granted = reply.path("scope").asString("");
		if (!Arrays.asList(granted.split("[ ,]+")).contains(scope(p)))
			throw new Failure("revoked", 0);
		return new Grant(required(reply, p.equals("slack") ? "access_token" : "refresh_token"), scope(p),
				p.equals("slack") ? required(reply.path("team"), "id") : "");
	}

	JsonNode request(HttpRequest.Builder req) {
		try {
			var r = http.send(req.timeout(Duration.ofSeconds(8)).build(),
					HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), 1048576));
			if (r.statusCode() == 429)
				throw new Failure("rate_limited",
						(int) Math.clamp(r.headers().firstValueAsLong("Retry-After").orElse(60), 1, 86400));
			if (r.statusCode() == 401)
				throw new Failure("revoked", 0);
			if (r.statusCode() == 410 || r.statusCode() == 404)
				throw new Failure("gone", 300);
			JsonNode body = json.readTree(r.body());
			if (body == null || !body.isObject())
				throw new Failure("unavailable", 60);
			String error = body.path("error").asString("");
			if (Set
				.of("invalid_grant", "token_revoked", "account_inactive", "invalid_auth", "missing_scope",
						"not_in_channel", "channel_not_found")
				.contains(error))
				throw new Failure("revoked", 0);
			if (r.statusCode() == 403) {
				String reason = body.path("error").path("errors").path(0).path("reason").asString("");
				if (reason.contains("RateLimit") || reason.contains("rateLimit"))
					throw new Failure("rate_limited", 60);
				throw new Failure("permission_denied", 300);
			}
			if (r.statusCode() / 100 != 2 || !body.path("ok").asBoolean(true))
				throw new Failure("unavailable", 60);
			return body;
		}
		catch (Failure f) {
			throw f;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new Failure("unavailable", 60);
		}
		catch (Exception e) {
			throw new Failure("unavailable", 60);
		}
	}

	JsonNode get(String base, Map<String, String> params, String access) {
		return request(HttpRequest.newBuilder(URI.create(base + "?" + form(params)))
			.header("Authorization", "Bearer " + access)
			.GET());
	}

	static String required(JsonNode obj, String key) {
		String s = obj.path(key).asString("");
		if (s.isBlank() || s.length() > 8192)
			throw new Failure("invalid_response", 300);
		return s;
	}

	private static String brief(String text) {
		return text.substring(0, Math.min(text.length(), 500));
	}

	Page fetch(String p, String resource, String secret, String cursor, String page, String anchor) {
		if (!available(p))
			throw new Failure("unconfigured", 300);
		String access = p.equals("slack") ? secret
				: required(token(p, Map.of("grant_type", "refresh_token", "refresh_token", secret)), "access_token");
		return switch (p) {
			case "gmail" -> mail(access, cursor, page, anchor);
			case "calendar" -> calendar(access, cursor, page);
			case "slack" -> slack(access, resource, page, anchor);
			default -> throw new IllegalArgumentException();
		};
	}

	private static final String MAIL = "https://gmail.googleapis.com/gmail/v1/users/me/";

	Page mail(String access, String cursor, String page, String anchor) {
		boolean full = cursor.isEmpty();
		if (full && anchor.isEmpty())
			anchor = required(get(MAIL + "profile", Map.of(), access), "historyId");
		var params = new HashMap<String, String>();
		params.put("maxResults", "8");
		if (!page.isEmpty())
			params.put("pageToken", page);
		if (full)
			params.put("labelIds", "INBOX");
		else
			params.put("startHistoryId", cursor);
		JsonNode response = get(MAIL + (full ? "messages" : "history"), params, access);
		var ids = new LinkedHashSet<String>();
		if (full) {
			for (var m : response.path("messages"))
				ids.add(required(m, "id"));
		}
		else
			for (var h : response.path("history"))
				for (var m : h.path("messages"))
					ids.add(required(m, "id"));
		if (ids.size() > 16)
			throw new Failure("reset", 1);
		var items = new ArrayList<Item>();
		for (String id : ids) {
			try {
				var m = get(MAIL + "messages/" + enc(id), Map.of("format", "metadata", "metadataHeaders", "Subject"),
						access);
				boolean inbox = false;
				for (var label : m.path("labelIds"))
					if (label.asString("").equals("INBOX"))
						inbox = true;
				String subject = "(no subject)";
				for (var header : m.path("payload").path("headers"))
					if (header.path("name").asString("").equalsIgnoreCase("Subject"))
						subject = header.path("value").asString(subject);
				items.add(new Item(id, brief(subject), m.path("internalDate").asString(""), !inbox));
			}
			catch (Failure f) {
				if (!f.state.equals("gone"))
					throw f;
				items.add(new Item(id, "", "", true));
			}
		}
		return new Page(items, response.path("nextPageToken").asString(""), anchor,
				full ? anchor : required(response, "historyId"), full);
	}

	Page calendar(String access, String cursor, String page) {
		var params = new HashMap<String, String>();
		params.put("maxResults", "100");
		params.put("showDeleted", "true");
		if (!cursor.isEmpty())
			params.put("syncToken", cursor);
		if (!page.isEmpty())
			params.put("pageToken", page);
		var r = get("https://www.googleapis.com/calendar/v3/calendars/primary/events", params, access);
		var items = new ArrayList<Item>();
		for (var e : r.path("items"))
			items.add(new Item(required(e, "id"), brief(e.path("summary").asString("(untitled event)")),
					e.path("start").path("dateTime").asString(e.path("start").path("date").asString("")),
					"cancelled".equals(e.path("status").asString(""))));
		String next = r.path("nextPageToken").asString("");
		return new Page(items, next, "", next.isEmpty() ? required(r, "nextSyncToken") : "", cursor.isEmpty());
	}

	Page slack(String access, String channel, String page, String anchor) {
		if (anchor.isEmpty())
			anchor = Long.toString(Instant.now().getEpochSecond());
		var params = new HashMap<>(Map.of("channel", channel, "limit", "15", "oldest",
				Long.toString(Long.parseLong(anchor) - 86400), "latest", anchor, "inclusive", "true"));
		if (!page.isEmpty())
			params.put("cursor", page);
		var r = get("https://slack.com/api/conversations.history", params, access);
		if (!r.path("messages").isArray())
			throw new Failure("invalid_response", 300);
		var items = new ArrayList<Item>();
		for (var m : r.path("messages")) {
			String ts = required(m, "ts");
			items.add(new Item(ts, brief(m.path("text").asString("")), ts, false));
		}
		String next = r.path("response_metadata").path("next_cursor").asString("");
		if (next.isEmpty() && r.path("has_more").asBoolean(false))
			throw new Failure("invalid_response", 300);
		return new Page(items, next, anchor, anchor, true);
	}

	static Page fixture(String p, String cursor, String page, int revision) {
		var entries = switch (p) {
			case "gmail" -> List.of("Design review moved to Thursday", "Invoice ready for review");
			case "slack" -> List.of("Release checklist is ready", "Design review notes are posted");
			default -> List.of("Design review", "Project check-in");
		};
		String latest = "fixture-v" + revision;
		if (!cursor.isEmpty()) {
			if (latest.equals(cursor))
				return new Page(List.of(), "", "", cursor, false);
			return new Page(
					List.of(new Item("incoming", "Sample update " + revision, Integer.toString(revision + 2), false)),
					"", "", latest, false);
		}
		int i = page.isEmpty() ? 0 : 1;
		return new Page(List.of(new Item("example-" + i, entries.get(i), Integer.toString(i + 1), false)),
				i == 0 ? "second" : "", "baseline", "fixture-v1", true);
	}

}
