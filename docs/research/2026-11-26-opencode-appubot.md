# OpenCode Console and AppuBot review · 26 November 2026

## Decision for the model picker

Keep three billing sources distinct. The OpenCode Free runner invokes the installed OpenCode client. Console's inference API accepts Console credentials and dispatches each model to its supported API family. OpenCode documents HTTP 403 for free-tier chat requests from other clients, even with a Console key. Go is a separately entitled connection. A Console key therefore cannot recover a rate-limited free CLI run. The live CLI check on this PC emitted an error event with status 429 after the free route and tool denial passed preflight. [OpenCode inference API](https://opencode.ai/docs/console/api/inference) · [Model list and pricing](https://opencode.ai/docs/console/models/)

## AppuBot

Source review of revision `92172c2452f0bf96fc03a9907c1a9049c5e06e4e`, licensed Apache-2.0. We inspected the following implementation areas without installing AppuBot. Direct source reuse in Zincc was zero.

| Inspected code | Decision for Zincc |
| --- | --- |
| [Profile discovery](https://github.com/sarthakguptadev/AppuBot/blob/92172c2452f0bf96fc03a9907c1a9049c5e06e4e/apps/desktop/src/main/core/services/providerProfiles.ts) and [provider state](https://github.com/sarthakguptadev/AppuBot/blob/92172c2452f0bf96fc03a9907c1a9049c5e06e4e/apps/desktop/src/main/core/services/providers.ts) | Named personal and work profiles are valuable. Zincc uses explicit pairing and provider-owned sign-in. A directory or installed CLI alone cannot establish which model accepted a run. |
| [Plan-limit refresh](https://github.com/sarthakguptadev/AppuBot/blob/92172c2452f0bf96fc03a9907c1a9049c5e06e4e/apps/desktop/src/renderer/src/features/providers/useLimits.ts) | Candidate for provider-specific qualification. Surface provider-reported balance or reset time with the observation timestamp. Share a refresh request across open views. A 429 alone cannot establish a remaining balance. |
| [Named handoffs](https://github.com/sarthakguptadev/AppuBot/blob/92172c2452f0bf96fc03a9907c1a9049c5e06e4e/packages/chat-core/src/handoff.ts) | Candidate for team chat. Let a user explicitly address another agent, preserve authorship and cap handoff depth. Resolve an explicit recipient against workspace grants. Quoted material and retrieved text must never authorize a handoff. |
| [Pending messages](https://github.com/sarthakguptadev/AppuBot/blob/92172c2452f0bf96fc03a9907c1a9049c5e06e4e/packages/chat-core/src/sendQueue.ts) and [schedule editor](https://github.com/sarthakguptadev/AppuBot/blob/92172c2452f0bf96fc03a9907c1a9049c5e06e4e/packages/chat-core/src/schedule.ts) | Consider matching pending rows to durable receipts with stable client request IDs at UI polish. Consider weekday/monthly presets on top of the existing scheduler. Keep Temporal as the owner of recurring execution. |

Plan limits, addressed handoffs and schedule presets are design candidates. The delivery in this batch is the paired engine path. [Operator guide](../modules/phase-nine-builder.md)
