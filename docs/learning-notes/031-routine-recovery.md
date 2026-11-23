# Daily slots and event replay

A civil time such as 01:30 can occur twice on the same local date. The scheduler selects the first occurrence and persists an instant. A missing spring-forward time skips that date. A due transaction advances the slot and inserts a unique run together. Manual requests carry a separate retry identifier.

**Implemented and tested** — missed-slot coalescing, source-event deduplication, pause, process-state reconstruction and private receipts. The same routine row serializes overlapping jobs. If the worker dies before commit, its read-only operation can safely repeat. An external send would require a separate idempotent action ledger.

[Component guide](../modules/phase-five-integrations.md) · [Design decision](../architecture-decisions/0016-private-integrations.md)
