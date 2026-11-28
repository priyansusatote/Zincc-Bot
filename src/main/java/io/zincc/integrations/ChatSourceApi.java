package io.zincc.integrations;

import io.zincc.identity.SessionPrincipal;
import io.zincc.model.ModelGateway;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static io.zincc.integrations.IntegrationStore.*;
import static io.zincc.model.ModelGateway.Capability.*;

@RestController
@Profile("agents & oidc")
class ChatSourceApi {

	private final IntegrationStore store;

	private final SessionPrincipal principals;

	private final ModelGateway models;

	private final JsonMapper json;

	private final Semaphore planners = new Semaphore(4);

	ChatSourceApi(IntegrationStore store, SessionPrincipal principals, ModelGateway models, JsonMapper json) {
		this.store = store;
		this.principals = principals;
		this.models = models;
		this.json = json;
	}

	record Source(UUID id, String provider, String label, boolean fixture, boolean ready, Instant syncedAt) {
	}

	record Directory(List<Source> sources, List<String> routes) {
	}

	record Request(UUID feed, String route, String question) {
	}

	record Result(String action, String provider, String label, boolean fixture, Instant syncedAt,
			List<Summary> items) {
	}

	private Scope scope(OidcUser user, UUID workspace) {
		return new Scope(workspace, principals.resolve(user).orElseThrow(IntegrationStore::missing));
	}

	private List<ModelGateway.Route> routes() {
		return models.routes()
			.stream()
			.filter(r -> r.capabilities().contains(JSON_OBJECT) || r.capabilities().contains(TEXT))
			.toList();
	}

	@GetMapping(IntegrationApi.BASE + "/chat-sources")
	Directory directory(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace) {
		return store.scoped(scope(user, workspace),
				() -> new Directory(sources(null), routes().stream().map(ModelGateway.Route::id).toList()));
	}

	private List<Source> sources(UUID id) {
		String select = """
				SELECT id, provider, label, fixture,
				  coalesce(state='ready' AND synced_at>now()-interval '10 minutes',false), synced_at
				FROM zincc.feed WHERE state<>'revoked'
				""";
		return store.db.query(select + (id == null ? " ORDER BY label,id" : " AND id=? FOR SHARE"),
				(r, n) -> new Source(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getBoolean(4),
						r.getBoolean(5), r.getTimestamp(6) == null ? null : r.getTimestamp(6).toInstant()),
				id == null ? new Object[0] : new Object[] { id });
	}

	private Source ready(UUID feed) {
		var source = sources(feed).stream().findFirst().orElseThrow(IntegrationStore::missing);
		if (!source.ready())
			throw conflict("Sync this account before reading it");
		return source;
	}

	@PostMapping(IntegrationApi.BASE + "/chat-read")
	Result read(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @RequestBody Request request) {
		return read(scope(user, workspace), request);
	}

	Result read(Scope s, Request cmd) {
		if (cmd.feed() == null || cmd.question() == null || cmd.question().isBlank() || cmd.question().length() > 2000
				|| cmd.question().indexOf(0) >= 0 || cmd.route() == null || cmd.route().length() > 64)
			throw bad("Choose an account, route and question of up to 2000 characters");
		var route = routes().stream()
			.filter(r -> r.id().equals(cmd.route()))
			.findFirst()
			.orElseThrow(() -> bad("Choose an available planner route"));
		var provider = store.scoped(s, () -> ready(cmd.feed()).provider());
		if (!planners.tryAcquire())
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Source planner busy");
		String action;
		try {
			action = plan(route, provider, cmd.question());
		}
		finally {
			planners.release();
		}
		// Never hold a database connection across inference. Revocation, sync expiry and
		// membership changes are checked again before returning even an empty result.
		return store.scoped(s, () -> {
			var source = ready(cmd.feed());
			var items = action.equals("read_recent") ? store.summaries(cmd.feed()) : List.<Summary>of();
			return new Result(action, source.provider(), source.label(), source.fixture(), source.syncedAt(), items);
		});
	}

	private String plan(ModelGateway.Route route, String provider, String question) {
		var structured = route.capabilities().contains(JSON_OBJECT);
		var prompt = """
				Choose a tool for the user's request about a selected %s account.
				read_recent lists up to three recent synchronized subjects, event titles or channel summaries.
				unsupported covers all other requests, including writing, filtering, searching or querying message bodies.
				Do not answer the request or invent tool results. %s
				User request:
				%s"""
			.formatted(provider, structured ? "Return a JSON object containing only the string field action."
					: "Return exactly read_recent or unsupported, without punctuation.", question);
		String action;
		try {
			var generation = structured
					? models.jsonObject(route.id(), prompt, Map.of("action", ModelGateway.FieldType.STRING))
					: models.text(route.id(), prompt);
			var response = generation.block();
			if (response == null || response.text() == null || response.text().length() > 256)
				throw invalid();
			action = response.text().strip();
			if (structured) {
				var object = json.readTree(action);
				if (!object.isObject() || object.size() != 1 || !object.path("action").isString())
					throw invalid();
				action = object.path("action").asString();
			}
		}
		catch (ModelGateway.GatewayException ex) {
			throw switch (ex.failure()) {
				case RATE_LIMITED ->
					new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Provider rate limit reached");
				case TIMEOUT -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Provider timed out");
				default -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
						"The model did not produce a usable tool choice");
			};
		}
		catch (tools.jackson.core.JacksonException ex) {
			throw invalid();
		}
		if (!action.equals("read_recent") && !action.equals("unsupported"))
			throw invalid();
		return action;
	}

	private static ResponseStatusException invalid() {
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Model returned an invalid tool choice");
	}

}
