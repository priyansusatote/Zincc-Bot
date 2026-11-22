# Workbench conversations

After applying Flyway V5, run the packaged workbench with the OIDC profile, a provisioned workspace membership and a configured model route. For the existing free OpenRouter routes, use `SPRING_PROFILES_ACTIVE=oidc,openrouter-free` with the server-side key configured. Open the workspace, choose a model, and send the first message. The workbench lists the thread under the opening words of that message. Each thread belongs to its creator within that workspace. [Workbench setup](workbench.md) · [Browser sign-in](browser-sessions.md) · [Model configuration](model-gateway.md)

## Client contract

All paths below start with `/api/workspaces/{workspaceId}/conversations`. Authentication comes from the session. Every operation checks current workspace membership. Database row policies also restrict turns and events to the thread owner. Use the session CSRF header on POST.

| Operation | Behavior |
| --- | --- |
| GET collection | Lists personal threads and configured model route IDs. |
| POST `/{id}/turns` | Send `turnId`, `revision`, `route` and `question`. Choose a fresh conversation UUID and revision zero for the first turn. A 204 acknowledges a processed command or an exact duplicate. Read the snapshot or replay for the generation outcome. A stale revision, changed payload for an existing ID, or competing active generation returns 409. |
| GET `/{id}` | Returns ordered turns, their accumulated answers, run states and a consistent revision. |
| GET `/{id}/events?after=N` | Returns a finite UTF-8 SSE batch after N. The SSE ID is the conversation revision. The event name is `turn.v1`. The payload carries the turn ID, route, kind, body and timestamp. `Last-Event-ID` takes precedence over the query cursor. A cursor behind retention gets 410. A negative or future cursor gets 409. In either case, fetch a snapshot, then continue from its revision. |

A streaming route saves text in chunks and waits for the model gateway’s completion receipt. A text-only route publishes the full answer in its completion event. The client polls the durable stream and ignores already-applied IDs. Disconnecting the browser does not trigger a fresh generation. If the worker dies, the next authorized access after the two-minute deadline records an interruption. Starting another turn is a user decision.

## Current bounds

The Ollama adapter in Spring AI 2.0.2 uses final evaluation counts when exposing a finish reason. Zincc marks a response interrupted when the adapter cannot supply that receipt. Protocol fixtures cover recognized completion and truncation.

Each thread holds up to 64 turns. A question accepts 8,000 Java string characters. Each selected route receives the current question and up to 24,000 characters of the most recent contiguous suffix of completed question/answer pairs, skipping interrupted turns. Stored transcripts can therefore extend beyond the model’s working context. The last 128 events support incremental replay. Older turns remain in the snapshot. The directory currently lists all personal threads in the workspace. Heavy concurrent use and large directories still require load qualification.

[Typed HTTP contract](../../frontend/openapi.json) · [Decision](../architecture-decisions/0012-conversation-replay.md) · [Learning note](../learning-notes/012-conversation-replay.md)
