# ADR 0018 — One framework planning turn per isolated worker

Adopted for the local P9 builder, November 2026.

**Problem.** The three Python SDKs have different state, model and tool interfaces. Giving each SDK provider secrets and write permissions would create competing owners for authorization, retries and recovery.

**Decision.** Run one planning turn in a fixed, hash-locked Docker image. Use bounded stdin/stdout frames to request inference from Spring and return a typed proposal. Spring and Temporal own durable checkpoints, cancellation and side effects. Pin the framework on each immutable agent revision.

**Alternatives and consequences.** A long-lived framework server could amortize interpreter startup and retain richer graph state. It would require private session isolation, new checkpoint ownership rules and credential controls. The short-lived worker costs one container startup per planning turn and currently supports a small proposal schema. Existing action receipts and approvals apply uniformly across all three frameworks. [Qualification](../modules/phase-nine-builder.md)
