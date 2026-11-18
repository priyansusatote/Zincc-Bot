# Live actions, connectors, plugins, and automations

**Proposed design.** A single action system serves typed chat, scheduled work, and live voice.

## Connection architecture

A connector implements authorization, capabilities, typed reads/writes, pagination, rate limits, incremental sync, revocation, health checks, and receipts. Initial priorities are email, Slack, calendar, then files, GitHub, Notion, and other demanded services. User credentials and provider developer-app setup are required at integration time; no accounts are assumed connected now.

The complete current target list and delivery waves are maintained in [action scope and connectors](../product-scope-and-connectors.md). Later business/life integrations are part of the requested full product, with provider-specific access qualification.

Store a connection's owner, workspace sharing policy, granted scopes, expiry, resource selection, sync cursor, and secret reference. Multiple accounts from one provider remain distinct. Team agent access is the intersection of agent grants, workspace policy, and the connection owner's allowed resources.

Also evaluate the initiating caller or explicit service delegation and output audience under [ADR 0008](../architecture-decisions/0008-delegated-authority-and-data-sharing.md). P3.4a establishes this minimal contract; P5 expands it. A shared mailbox can use an assigned service identity, but sharing an agent does not automatically share a personal mailbox.

Prefer event-driven incremental sync over frequent full scans. Track provider event IDs and cursors, handle duplicate/out-of-order events, reconcile periodically, and expose stale or revoked status. Rate limits use bounded retry with jitter and provider hints. A partial Slack failure must not become “no new messages.”

Handle expired cursors with a bounded resync, renew expiring subscriptions and separately track data freshness and ACL freshness. Recheck authorization before delivering indexed content; when source permissions are stale/unknown, deny sensitive retrieval until reconciled. Define reconnect behavior per provider so resync does not resend old notifications. Deduplicate under connection/account scope, not a provider-wide ID alone.

## MCP, AG-UI, and A2A

These protocols serve different boundaries:

- **MCP** supplies tool/resource capabilities. The [current specification](https://modelcontextprotocol.io/specification/latest) resolved to 2026-07-28 during research. Core requests are self-contained; extensions such as tasks, skills, or embedded apps require explicit support. Maintain adapters for supported older servers and negotiate capabilities. Do not assume every MCP server has durable task support.
- **AG-UI** supplies agent-to-interface events and interaction patterns. It does not itself provide tenant isolation, durable execution, or action authorization. [Official introduction](https://docs.ag-ui.com/introduction).
- **A2A** is a candidate for cooperation with external agents. Treat remote agents as separately trusted services with explicit identities and capability contracts. Do not equate a remote task result with a verified local action. [Official specification](https://a2a-protocol.org/latest/).

Remote MCP servers get network and credential scope, health checks, timeouts, schema validation, pinned versions where available, and an approval mapping. Local stdio servers execute in a restricted process/sandbox, never inside the API server. Tool descriptions and returned content are untrusted. Cap payloads and tool counts; load only relevant metadata into model context.

The [AG-UI adapter contract](agent-ui-contract.md) adds specific event/state/HITL mapping and version gates. A UI approval response is input to the authenticated action service, not a grant by itself. MCP credentials are audience-bound; tool schema/permission changes invalidate affected approvals and require requalification.

The proposed plugin manifest includes package ID/version, publisher, integrity digest, license, instructions, tool schemas, UI assets, runtime/dependencies, network/data permissions, credential needs, compatibility, and tests. Install, update, rollback, and uninstall are separate operations. Updates that broaden permissions require a fresh grant. Begin with curated packages, then customer packages, then a marketplace after governance works.

P5 may initially support declarative skills and approved fixed tools. Third-party executable packages and build/install hooks require qualified P6 isolation before import or execution. Freeze each active run's package revision; an update applies to new runs, while a security revocation can stop affected active runs. Track dependency reachability, uninstall data policy and emergency package disablement. Never infer runtime safety from a signature alone.

## Automations

An automation is a versioned trigger + filter + agent/workflow + scope + budget + output destination. Examples: daily inbox briefing, notification on a monitored source change, monthly report refresh, meeting prep, or developer issue triage.

Time triggers specify IANA time zone, daylight-saving behavior, missed-run policy, overlap policy, and maximum catch-up. Events require deduplication and a stable causal ID. Avoid automation loops where the agent's own write triggers itself repeatedly. Set frequency, runtime, and spend ceilings.

Support dry-run preview, pause, disable, trigger history, failure notification, and safe manual replay. Durable scheduling is introduced before customer-facing always-on promises. A user laptop that is asleep cannot execute local jobs; show whether work waits, skips, or runs elsewhere according to explicit policy.

Standing grants can allow bounded routine actions. Sending a first-time message to a new recipient or changing scope may need a decision even if drafting and reading were preauthorized. Policy state is evaluated when dispatch happens, not only when a schedule was created.

## Live voice execution

Use a WebRTC media layer, with [LiveKit Agents](https://docs.livekit.io/agents/) as the first candidate. Spring remains the policy/run authority; a media worker handles audio timing, turn detection, STT/TTS or a realtime speech model. Start with a replaceable STT → agent → TTS path and compare with realtime models for latency and quality.

Example flow:

1. User starts a session; authorize microphone and selected workspace/agent. Issue short-lived media credentials.
2. Speech becomes a stable utterance/intent ID. Acknowledge while a durable task begins.
3. Read Gmail and Slack concurrently through existing grants. Tag results with source, account, as-of time, and availability.
4. Summarize concisely, show result cards, and retain a typed transcript according to policy.
5. “Draft a reply” creates an editable draft. “Send it” binds to the exact visible draft and recipient; dispatch follows current policy.
6. If the user interrupts, stop speech playback promptly. Cancel pending work where possible, but explain any action already committed.

Separate audio cancellation, task cancellation, and external action reversal. Barge-in cannot unsend an email. Deduplicate repeated utterances/reconnects. If a result arrives for an obsolete question, don't speak it over the new answer. Long tools may finish asynchronously with a clear state card.

Handle noisy audio, partial transcripts, uncertain names/dates/amounts, headset changes, dropped connections, provider timeouts, and mixed Hindi/English. Confirm ambiguous action-critical fields. Never require voice for essential controls; provide keyboard/text and accessible status.

Voice privacy: explicit recording controls, visible microphone state, configurable retention, source-aware redaction, and no voice cloning without permission. An avatar option and meeting participation are included in the full requested scope, with later implementation tasks. Mercor is an interaction reference for conversational pacing, not evidence of an action framework we can reuse.

## Artifact and media workers

Document creation uses structured content → format renderer → preview → validation → versioned artifact. Provide editable source plus PDF/export where useful. Publishing or sending an artifact is a separate governed action.

Images route to image models or local engines. Audio production may integrate a user-controlled service. Video combines source scenes/assets, media processing, and a suitable generation provider; preserve prompts, sources, licenses, and editability. [Hyperframes](https://github.com/heygen-com/hyperframes) is a candidate for HTML-based renderable video scenes, not a general video-generation model.

Run renderers and untrusted HTML in isolated workers. Set duration/resolution/compute limits, prevent remote asset exfiltration, verify output playback and page layout, and avoid treating a successful process exit as a usable artifact.

## Acceptance journeys

Test live read with one provider down; duplicate “send” after reconnect; barge-in before and after dispatch; revoked OAuth during a call; time-zone/DST scheduled run; repeated webhook; sleeping local machine; plugin update with new permissions; MCP malformed output; and a task continuing after a call ends under the chosen policy.
