# 0014 — Versioned agents and exact approval for one task-list write

**Adopted in P3, 23 November 2026.** An agent's instructions, its caller and the authority to create a task have different lifetimes. Replaying an old plan must preserve its meaning without restoring a revoked permission.

**Decision.** Store versions as insert-only rows and pin each run to one of them. A delegate invokes under their own identity. Check pinned and current tool grants and current workspace membership. Bind each proposed write to the connection generation, list, title, version and run. Serialize version changes with a transaction-scoped advisory lock. Approval is durable, and the write claim is recorded before the network request. A matching provider receipt is required for a verified task creation. An ambiguous write may only be reconciled by reading provider evidence.

**Alternatives and consequences.** Mutable definitions would let an update quietly retarget a waiting run. Owner-credential delegation would let a teammate inherit a private account. Retrying a timed-out create would make duplicate tasks possible. The adopted approach adds an approval step and occasionally leaves an outcome unknown even when the provider succeeded. It accepts one bounded task-list grant per deployment during P3 qualification. Production tenant-wide onboarding, key rotation and fleet-level credential coordination need subsequent work.

[Durable execution](0002-durable-execution.md) · [Run guide and limits](../modules/phase-three-agents.md) · [PostgreSQL and failure tests](../../src/test/java/io/zincc/agents/AgentHarnessTest.java)
