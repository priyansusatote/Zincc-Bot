package io.zincc.agents;

import java.util.Map;
import java.util.UUID;
import io.zincc.identity.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import static io.zincc.agents.AgentStore.*;

@RestController
@RequestMapping("/api/workspaces/{workspace}/agents")
@Profile("agents & oidc")
class AgentApi {

	private final SessionPrincipal principals;

	private final AgentStore store;

	private final AgentRuntime runtime;

	private final AgentEngine engine;

	AgentApi(SessionPrincipal principals, AgentStore store, AgentRuntime runtime, AgentEngine engine) {
		this.principals = principals;
		this.store = store;
		this.runtime = runtime;
		this.engine = engine;
	}

	private Scope scope(OidcUser user, UUID workspace) {
		return new Scope(workspace, principals.resolve(user).orElseThrow(AgentStore::missing));
	}

	@GetMapping
	View view(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace) {
		return store.view(scope(user, workspace));
	}

	@PostMapping("/{id}/versions")
	Map<String, Integer> save(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID id, @RequestBody Definition definition) {
		return Map.of("version", store.save(scope(user, workspace), id, definition));
	}

	record Connect(boolean active) {
	}

	@PostMapping("/connection")
	void connect(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @RequestBody Connect cmd) {
		store.connect(scope(user, workspace), cmd.active);
	}

	@PostMapping("/{id}/runs")
	Map<String, UUID> start(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id,
			@RequestBody Start command) {
		return Map.of("id", store.start(scope(user, workspace), id, command));
	}

	record Decision(String operation, String digest) {
	}

	@PostMapping("/runs/{id}/decision")
	void decision(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id,
			@RequestBody Decision cmd) {
		var scope = scope(user, workspace);
		if ("reconcile".equals(cmd.operation)) {
			engine.reconcile(scope, id);
			return;
		}
		if (cmd.operation == null)
			throw bad();
		store.decision(scope, id, cmd.operation, cmd.digest);
		runtime.wake(id);
	}

}
