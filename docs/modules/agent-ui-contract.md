# Agent-to-interface contract and AG-UI

Date: **16 November 2026**. Proposed; no SDK installed. Related: [runtime](agent-runtime.md), [review evidence](../research/2026-11-16-architecture-follow-up.md).

## Decision

Use Zincc's authenticated REST commands and durable sequenced event envelope as the product contract. Qualify an AG-UI adapter for framework interoperability and reusable frontend integrations. Define the mapping during P2.2; implement and test the first adapter in P9.1. The initial workbench need not depend on the complete CopilotKit/OpenBot stack.

[AG-UI](https://github.com/ag-ui-protocol/ag-ui) supplies message/tool/state/activity events and human-interaction patterns. Its public [event documentation](https://docs.ag-ui.com/concepts/events) distinguishes draft extensions from supported events. Pin a released SDK/protocol subset before implementation; a main-branch type does not establish support in every framework adapter.

## Boundary mapping

| AG-UI concept | Zincc interpretation |
| --- | --- |
| Thread/run identifiers | Map to authorized conversation/run records; never accept ownership from a client-supplied ID |
| Text start/content/end | Bounded presentation deltas; persist completed messages/checkpoints with access scope |
| Tool-call arguments/results | Untrusted proposal/result observation; dispatch through tool gateway and verify receipts |
| State snapshot/delta | Presentation state only; validated schema, allowed patch paths, size/depth limits and base revision |
| Run finished/error/interrupt | Adapter execution state; platform completion still depends on outstanding children, actions and verification |
| Activity/subagent events | Attributed progress for supported versions; suppress private child context |
| Raw/custom/metadata/reasoning fields | Allowlist and sanitize; do not expose secrets or hidden reasoning through passthrough |
| Client tools/HITL | UI interaction requests; authenticated server approval binds exact action and current policy |

AG-UI client tools are supplied in `RunAgentInput.tools`; backend tools are configured separately. Neither a browser-declared tool nor a returned “approved” field grants authority. A frontend may open a pane or collect input, while external writes remain server-governed. [Tool contract](https://docs.ag-ui.com/concepts/tools).

The inspected base event lacks Zincc's required persisted sequence/cursor. At the gateway, wrap normalized events with event ID, run sequence, tenant/access scope, schema version and timestamp. Deduplicate replay and enforce allowed transitions. Do not reinterpret token-stream completion or AG-UI's success outcome as proof an email was delivered.

Keep state patches out of permission, budget, credential, approval and authoritative run records. A missed/invalid patch requests a fresh authorized snapshot; do not apply it optimistically to an unknown base. Do not log entire state/patch content when reporting an error. Revocation ends subscriptions and prevents fresh snapshots; already-rendered information cannot be retroactively unseen.

## Qualification

Contract tests cover fragmented tool arguments, malformed events, duplicate/reordered messages, reconnect after cursor expiry, changed state revision, private fields, unauthorized thread IDs, child attribution, interruption and cancellation. Test each selected framework independently. No promise that AG-UI implements persistence, tenancy, action receipts, billing or sandboxing on Zincc's behalf.
