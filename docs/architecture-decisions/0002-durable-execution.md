# ADR 0002 — Durable orchestration and explicit external effects

Status: **Adopted in P3, 23 November 2026**, for the loopback Temporal deployment and PostgreSQL-backed authority checks. The local restart drill recovered a queued run and a pending approval, and duplicate delivery produced one provider task. [Qualification](../modules/phase-three-agents.md). Related: [runtime](../modules/agent-runtime.md).

## Problem

Agents may run for minutes or days, wait for approval, lose workers, and retry provider calls. Duplicate side effects are unacceptable even when delivery is at least once.

## Decision

Introduce Temporal with real background runs. Use a PostgreSQL transactional outbox for accepted jobs, stable workflow IDs, deterministic workflows, I/O activities, and an independent action ledger. Record ambiguous external outcomes and reconcile before retrying. Keep large context in versioned storage references.

## Alternatives

- In-process asynchronous tasks: simple, but process restart loses progress and long waits consume brittle application state.
- Database queue/custom state machine only: viable at small scope, but Zincc would implement durable waits, replay, scheduling, versioning, retries, and operations itself.
- Kafka as workflow engine: useful event transport, but not automatically approval state, durable timers, or side-effect correctness.
- Framework checkpoints alone: helpful inside an agent graph, insufficient as a uniform product-wide execution contract.

## Trade-offs and consequences

Temporal adds infrastructure, replay constraints, history limits, and upgrade discipline. It does not transact atomically with Gmail or Slack. Product records and workflow progression need explicit ownership and reconciliation. An inner framework graph must not independently retry an action that the outer workflow also retries.

16 November clarification: retain this durability contract for self-hosted/local automation rather than silently substituting an in-memory scheduler. Keep sensitive content out of history/metadata, qualify payload and failure encryption, and restore with dispatch disabled until external receipts/revocations are reconciled. See [operations](../modules/deployment-and-operations.md). This adds operational cost but avoids a second execution engine with different safety semantics.

## Validation

Crash before/after external dispatch, duplicate outbox delivery, long approval wait, cancellation, expired grant, worker-version upgrade, and unknown-result recovery. A test passes only if the final external effect and recorded receipt agree.
