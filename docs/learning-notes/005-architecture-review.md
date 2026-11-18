# 005 — Product and architecture review

Date: **16 November 2026**. Documentation/research task only. No application runtime implemented. Topics: system boundaries, delegation, orchestration, isolation, protocols, capacity, privacy and recovery. Coverage state: **studied/designed**, not implemented or experimentally evaluated.

## A. What did we build?

A reviewed planning package with corrected dependencies, an operations contract, explicit shared-agent authority, an AG-UI mapping and a source-evidence update. The full requested product remains proposed; approval is still pending.

## B. How does it work internally?

A proposed task passes through caller/service identity, scoped context, a versioned agent, durable progression and the governed action gateway. Model/framework events update presentation; they do not grant authority or prove completion. Workers execute within resource/network boundaries. Receipt verification determines action success. Recovery reconciles external state before allowing retries.

## C. Architecture and design decisions

Keep the Spring modular monolith because permissions, budgets and product data need a clear owner. Temporal avoids inventing a second durable scheduler. Ports/adapters isolate model/framework differences. PostgreSQL remains the initial authority; more databases would increase operational and deletion complexity before a measured need exists. Reference stacks are useful evidence, but their implementation, licensing and deployment assumptions do not automatically fit Zincc.

The main alternative was adopting an entire reference backend. OpenBot's public checkout/deployment separation and Intelligence dependency reinforce keeping the requested Java foundation. A second alternative was treating plugin manifests or client tools as a safety boundary; executable code and external actions require stronger isolation and server-side checks.

## D. Important technologies and concepts

- **Delegation:** a team member asking a shared agent must not gain its owner's private-mail privileges. Record caller, effective principal and the grant chain.
- **Fencing:** a stale worker may still run after lease expiry. The executor checks a generation; reusing generation 1 after deletion could make old work appear current. Preserve monotonic identity.
- **Durability versus presentation:** AG-UI describes interaction events. Zincc's database/workflow state and receipts establish what actually happened.
- **Admission control:** limiting active compute protects latency/cost. A hundred sleeping agents are cheap compared with a hundred simultaneous GPU jobs.
- **Data lineage:** a dashboard or summary can disclose the same secrets as its source. Access rules must follow derivatives and their output audiences.
- **Restore reconciliation:** a restored database is older than the external world. Disabling dispatch prevents it from sending an already-sent message again.

Without these boundaries, adding more agents, caches or frameworks amplifies failure instead of improving reliability. KV caching, GraphRAG and fine-tuning remain explicit learning labs; this review did not run them or establish their benefit.

## E. Real-world use cases

Zincc's shared support agent can read only the tickets and account context granted to its effective identity. Its personal chief-of-staff can publish a chosen briefing without exposing every source email. The Paint agent pauses at its actual input driver when the user takes over. A long scene render can wait without blocking the conversational API.

## F. Edge cases and failure scenarios

Revoked membership during a run blocks new retrieval/dispatch; cached content is not a permission exemption. Provider timeout after a send produces an unknown outcome until reconciled. Duplicate event delivery is deduplicated; malformed/missed UI patches trigger snapshots. A plugin can execute at import time, so inspection after import is too late. Lost runner heartbeat stops new work locally as well as centrally. Under high load, bounded admission and separate queues preserve interactive capacity; unlimited queues hide overload until users wait indefinitely.

## G. Trade-offs and limitations

Temporal and local specialist services increase installation/memory cost. Audience-aware sharing complicates UX but prevents privilege amplification. Isolation and GPU compatibility need real tests. Optional framework support multiplies contract/version maintenance. The plan retains a large scope without promising a calendar or universal capability. Static source inspection does not demonstrate exploitability, safety or performance.

## H. Document walkthrough

- [Review](../PLAN-REVIEW.md): changes, decisions, concerns and approval boundary.
- [Roadmap](../phases/roadmap.md): new prerequisites and phased acceptance.
- [Operations](../modules/deployment-and-operations.md): deployment modes, admission, recovery, retention and costs.
- [ADR 0008](../architecture-decisions/0008-delegated-authority-and-data-sharing.md): shared-agent authority and audience rules.
- [AG-UI contract](../modules/agent-ui-contract.md): event mapping and security limits.
- [Source follow-up](../research/2026-11-16-architecture-follow-up.md): inspected paths/revisions and honest gaps.

There are no implemented classes or methods to walk through yet.

## I. What should I remember?

1. Tenant membership and resource access are separate checks.
2. Agent ownership does not grant every caller the owner's authority.
3. Importing a plugin is already execution.
4. A protocol success event is not an external action receipt.
5. A database lease cannot recall an in-flight provider request.
6. Offline means the entire enabled data path can operate offline.
7. Restore workflows must account for side effects outside the backup.
8. Learning experiments and production adoption are different outcomes.

## J. Interview preparation

1. **Why not put all orchestration into LangGraph?** It can own an inner graph, but product-wide waits, actions and multiple frameworks need a consistent outer durability/authority contract.
2. **How do you prevent a shared bot from leaking owner data?** Explicit caller/service delegation, scoped retrieval/tool grants and audience checks on outputs.
3. **Why preserve a released lease record?** To prevent fence reset from accepting a stale worker; a new incarnation plus monotonic generation is another option.
4. **What makes a backup restore dangerous for agents?** External actions may already have happened; restored state can retry them unless dispatch is quarantined and reconciled.
5. **How would you size 100 agents?** Separate idle definitions, waiting runs and active resources; measure workload arrival rate, service time, concurrency and provider limits.

## K. Learning exercise

Design a permission table for a shared inbox agent: owner, teammate, workspace admin and scheduled service principal. For each, decide whether it can read private mail, read the shared mailbox, send a reply and publish a summary. Include a membership revocation halfway through a run.

## L. Completion status

Completed: plan review and documentation corrections, selected source follow-up including AG-UI/OpenMausBot, and selective dependency refresh. Verification: document links, headings, fences, A–L sections, whitespace and repository file-type inventory; final results recorded in [status](../status.md). No runtime or upstream tests ran. Remaining: adoption-time full selected-path/dependency review, compatibility/load/security/recovery tests and product decisions in the concern register. Technical debt: unimplemented contracts are planned work, not evidence of working integrations. Next recommended task after explicit authorization: **P1.1 only**.
