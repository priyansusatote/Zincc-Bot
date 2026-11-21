package io.zincc.conversation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.zincc.conversation.ConversationStore.Command;
import io.zincc.conversation.ConversationStore.Scope;
import io.zincc.conversation.ConversationStore.Snapshot;
import io.zincc.conversation.ConversationStore.ThreadInfo;
import io.zincc.conversation.ConversationStore.Turn;
import io.zincc.identity.SessionPrincipal;
import io.zincc.model.ModelGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@Profile("oidc")
@RequestMapping("/api/workspaces/{workspace}/conversations")
class ConversationApi {

	private final ConversationStore store;

	private final SessionPrincipal principals;

	private final ModelGateway gateway;

	private final JsonMapper json;

	ConversationApi(ConversationStore store, SessionPrincipal principals, ModelGateway gateway, JsonMapper json) {
		this.store = store;
		this.principals = principals;
		this.gateway = gateway;
		this.json = json;
	}

	private Scope scope(OidcUser user, UUID workspace) {
		return new Scope(principals.resolve(user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)),
				workspace);
	}

	record Directory(List<ThreadInfo> threads, List<String> routes) {
	}

	@GetMapping
	Directory list(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace) {
		return new Directory(store.directory(scope(user, workspace)),
				gateway.routes().stream().map(ModelGateway.Route::id).toList());
	}

	@GetMapping("/{id}")
	Snapshot snapshot(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id) {
		return store.snapshot(scope(user, workspace), id);
	}

	@GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	ResponseEntity<String> replay(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID id, @RequestParam(defaultValue = "0") long after,
			@RequestHeader(name = "Last-Event-ID", required = false) Long last) {
		var frames = new StringBuilder("retry: 1500\n\n");
		for (var e : store.replay(scope(user, workspace), id, last == null ? after : last)) {
			frames.append("id: ")
				.append(e.seq())
				.append("\nevent: turn.v1\ndata: ")
				.append(json.writeValueAsString(e))
				.append("\n\n");
		}
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.contentType(new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8))
			.body(frames.toString());
	}

	@PostMapping("/{id}/turns")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void send(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID id,
			@RequestBody Command cmd) {
		var scope = scope(user, workspace);
		if (cmd.turnId() == null || cmd.revision() == null || cmd.revision() < 0 || cmd.question() == null
				|| cmd.question().isBlank() || cmd.question().length() > 8000 || cmd.question().indexOf('\0') >= 0)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		var route = gateway.routes()
			.stream()
			.filter(r -> r.id().equals(cmd.route()))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
		var reservation = store.reserve(scope, id, cmd);
		if (reservation.isEmpty())
			return;
		var input = new ModelGateway.Input(context(reservation.get()), cmd.question());
		try {
			if (route.capabilities().contains(ModelGateway.Capability.TEXT_STREAM)) {
				generateStream(scope, id, cmd, input);
			}
			else {
				var result = gateway.answer(cmd.route(), input).block();
				store.append(scope, id, cmd.turnId(), "completed", result.text());
			}
		}
		catch (ModelGateway.GatewayException ex) {
			store.append(scope, id, cmd.turnId(), "interrupted", "");
		}
	}

	private void generateStream(Scope scope, UUID id, Command cmd, ModelGateway.Input input) {
		var pending = new StringBuilder();
		long flushed = System.nanoTime() - 150_000_000L;
		try (var stream = gateway.streamAnswer(cmd.route(), input).toStream(1)) {
			var iterator = stream.iterator();
			while (iterator.hasNext()) {
				var e = iterator.next();
				if (e instanceof ModelGateway.Delta d) {
					pending.append(d.text());
					long now = System.nanoTime();
					if (now - flushed >= 150_000_000 || pending.length() >= 1024) {
						store.append(scope, id, cmd.turnId(), "delta", pending.toString());
						pending.setLength(0);
						flushed = now;
					}
				}
				else if (e instanceof ModelGateway.Finished) {
					store.append(scope, id, cmd.turnId(), "completed", pending.toString());
				}
			}
		}
		catch (ModelGateway.GatewayException ex) {
			store.append(scope, id, cmd.turnId(), "interrupted", pending.toString());
		}
	}

	// Keep recent complete exchanges intact with a bounded model input.
	private static List<ModelGateway.Exchange> context(List<Turn> turns) {
		var history = new ArrayList<ModelGateway.Exchange>();
		int budget = 24000;
		for (var turn : turns.reversed()) {
			if (!turn.state().equals("completed"))
				continue;
			budget -= turn.question().length() + turn.answer().length();
			if (budget < 0)
				break;
			history.add(new ModelGateway.Exchange(turn.question(), turn.answer()));
		}
		return history.reversed();
	}

}
