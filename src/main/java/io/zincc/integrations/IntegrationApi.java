package io.zincc.integrations;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import io.zincc.identity.SessionPrincipal;
import static io.zincc.integrations.IntegrationStore.*;

@RestController
@Profile("agents & oidc")
class IntegrationApi {

	static final String BASE = "/api/workspaces/{workspace}/integrations";

	private static final String PENDING = IntegrationApi.class.getName() + ".consent";

	private final SessionPrincipal principals;

	private final IntegrationStore store;

	private final Routines routines;

	IntegrationApi(SessionPrincipal principals, IntegrationStore store, Routines routines) {
		this.principals = principals;
		this.store = store;
		this.routines = routines;
	}

	private Scope scope(OidcUser user, UUID workspace) {
		return new Scope(workspace, principals.resolve(user).orElseThrow(IntegrationStore::missing));
	}

	record View(List<Feed> feeds, List<SkillCatalog.Skill> catalog, List<Map<String, Object>> installed,
			List<Map<String, Object>> routines, List<Map<String, Object>> history, List<Map<String, Object>> dashboards,
			List<String> available, List<Map<String, Object>> taskGrants) {
	}

	@GetMapping(BASE)
	View view(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace) {
		return store.scoped(scope(user, workspace), () -> new View(store.feeds(), store.catalog,
				store.db.queryForList("SELECT package,version,digest FROM zincc.skill_install ORDER BY package"),
				store.db.queryForList(
						"SELECT id,name,package,version,trigger_kind,zone,local_time::text time,paused,next_time FROM zincc.routine ORDER BY name,id"),
				routines.history(),
				store.db.queryForList(
						"SELECT d.id,d.document_id,d.name FROM zincc.document_dashboard d ORDER BY d.name,d.id LIMIT 100"),
				List.of("gmail", "slack", "calendar").stream().filter(store::enabled).toList(),
				store.db.queryForList("SELECT reference,resource,active FROM zincc.agent_connection")));
	}

	record Connect(String provider, String label, String channel, boolean fixture) {
	}

	record Started(UUID id, String url) {
	}

	record Consent(String state, String verifier, Scope scope, Connect request, Instant expires) {
	}

	@PostMapping(BASE + "/connections")
	Started connect(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @RequestBody Connect c,
			HttpSession session) {
		var s = scope(user, workspace);
		resource(c.provider, c.channel);
		if (c.label == null || c.label.isBlank() || c.label.length() > 80)
			throw bad("Account label required");
		if (c.fixture)
			return new Started(store.add(s, c.provider, c.label, c.channel, true, null), null);
		store.scoped(s, () -> true);
		if (!store.enabled(c.provider))
			throw conflict("Configure provider credentials and connection key first");
		String nonce = UUID.randomUUID().toString();
		String verifier = ProviderClient.verifier();
		String url = store.client.authorize(c.provider, nonce, verifier);
		synchronized (session) {
			session.setAttribute(PENDING, new Consent(nonce, verifier, s, c, Instant.now().plusSeconds(600)));
		}
		return new Started(null, url);
	}

	@GetMapping("/api/integrations/callback")
	ResponseEntity<Void> callback(@AuthenticationPrincipal OidcUser user, HttpSession session,
			@RequestParam String state, @RequestParam(required = false) String code) {
		Consent pending;
		synchronized (session) {
			pending = (Consent) session.getAttribute(PENDING);
			if (pending == null || !pending.state.equals(state))
				throw bad("Consent session expired");
			session.removeAttribute(PENDING);
		}
		if (!pending.expires.isAfter(Instant.now()) || !scope(user, pending.scope.workspace()).equals(pending.scope)
				|| code == null || code.isBlank() || code.length() > 8192)
			throw bad("Consent denied or expired");
		var c = pending.request;
		store.scoped(pending.scope, () -> true);
		if (!store.enabled(c.provider))
			throw conflict("Connection unavailable");
		try {
			var grant = store.client.exchange(c.provider, code, pending.verifier);
			store.add(pending.scope, c.provider, c.label, c.channel, false, grant);
		}
		catch (ProviderClient.Failure ex) {
			throw conflict("Provider did not grant the requested account access");
		}
		return ResponseEntity.status(303)
			.location(URI.create("/workbench/"))
			.header("Referrer-Policy", "no-referrer")
			.build();
	}

	record FeedCommand(String action, UUID attempt) {
	}

	@PostMapping(BASE + "/connections/{id}")
	void connection(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id,
			@RequestBody FeedCommand c) {
		var s = scope(user, workspace);
		if ("sync".equals(c.action))
			store.sync(s, id, true);
		else if ("sample".equals(c.action))
			store.sampleEvent(s, id, c.attempt);
		else if ("revoke".equals(c.action))
			store.revoke(s, id);
		else
			throw bad("Invalid connection action");
	}

	record Install(String name, int version, String digest, Set<String> permissions) {
	}

	@PostMapping(BASE + "/skills")
	void install(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @RequestBody Install c) {
		store.install(scope(user, workspace), c.name, c.version, c.digest, c.permissions);
	}

	@PostMapping(BASE + "/routines")
	Map<String, UUID> create(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@RequestBody Routines.Create c) {
		return Map.of("id", routines.create(scope(user, workspace), c));
	}

	record Action(String action, UUID attempt) {
	}

	@PostMapping(BASE + "/routines/{id}")
	void action(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id,
			@RequestBody Action c) {
		routines.command(scope(user, workspace), id, c.action, c.attempt);
	}

}
