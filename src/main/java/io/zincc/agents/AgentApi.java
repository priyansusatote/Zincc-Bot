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

	@GetMapping("/{id}/versions")
	java.util.List<Agent> history(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID id, @RequestParam(defaultValue = "2147483647") int before) {
		return store.history(scope(user, workspace), id, before);
	}

	record Connect(boolean active) {
	}

	record FixtureAccount(String label) {
	}

	@GetMapping("/{id}/engines")
	ConnectedEngines.Picker engines(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID id) {
		return store.engines.picker(scope(user, workspace), id);
	}

	@PostMapping("/engines/fixture")
	void connectEngine(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@RequestBody FixtureAccount account) {
		store.engines.connect(scope(user, workspace), account.label);
	}

	@DeleteMapping("/engines/{connection}")
	void revokeEngine(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID connection) {
		store.engines.revoke(scope(user, workspace), connection);
	}

	@PutMapping("/{id}/engines/selection")
	void selectEngine(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id,
			@RequestBody ConnectedEngines.Choice choice) {
		store.engines.choose(scope(user, workspace), id, choice);
	}

	@PostMapping("/{id}/engine-runs")
	Map<String, UUID> startEngine(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID id, @RequestBody ConnectedEngines.StartEngine cmd) {
		return Map.of("id", store.engines.start(scope(user, workspace), id, cmd));
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

	@PostMapping("/{id}/team")
	Map<String, UUID> team(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id,
			@RequestBody AgentTeams.Request command) {
		return Map.of("id", store.teams.start(scope(user, workspace), id, command));
	}

	@GetMapping("/memory")
	java.util.List<AgentMemory.Note> recall(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@RequestParam(defaultValue = "") String q) {
		return store.memory.recall(scope(user, workspace), q);
	}

	@PostMapping("/memory")
	void saveMemory(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@RequestBody AgentMemory.Edit edit) {
		store.memory.edit(scope(user, workspace), edit);
	}

	@DeleteMapping("/memory/{id}")
	void deleteMemory(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id,
			@RequestParam int revision) {
		store.memory.remove(scope(user, workspace), id, revision);
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
