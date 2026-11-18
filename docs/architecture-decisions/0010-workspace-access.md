# ADR 0010 — Workspace membership reads

Adopted in P1.2 on 18 November 2026.

**Problem.** A guessed workspace ID or an omitted query filter could disclose another workspace. A pool can lend the same database session to different principals.

**Decision.** The trusted control plane binds principal and workspace IDs inside a fresh read transaction. A restricted runtime login executes a membership join. Row-level policies on both tables independently enforce the scope. Flyway runs under a schema owner outside the service.

**Alternatives and consequences.** Application-only filtering makes every query responsible for isolation. A separate database per workspace adds provisioning and migration work for each tenant. Shared tables with RLS keep one migration stream and add policy testing and a dependency on trustworthy identity binding. A new transaction can need a second connection when called inside an existing transaction. Call the lookup before opening a longer write transaction. Administrative provisioning remains a separate operation.

**Evidence.** [Isolation and recovery tests](../../src/test/java/io/zincc/workspace/WorkspaceMembershipsTest.java) · [operator instructions](../modules/workspace-membership.md).
