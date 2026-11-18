# Planning task learning report

Date: 14 November 2026. Component: **P0.1 research and architecture plan**. This is a documentation task; no runtime functionality was implemented.

## A. What did we build?

We created an engineering blueprint for Zincc: product capabilities, data/control boundaries, durable execution, model routing, document analytics, live actions, customer compute, reference provenance, and a task-by-task roadmap. We also recorded the mandatory teaching rules so future work remains understandable.

## B. How does it work internally?

The proposed request flow is: Spring authenticates a request → commits a run plus outbox event → durable workflow schedules a worker → harness gathers evidence and proposes steps → tool gateway authorizes actions → isolated worker/provider performs them → receipts and verified artifacts return to the UI. Memory and caches assist this flow but cannot override permissions or invent completion.

The outbox solves a specific gap: a database commit can succeed while a workflow-start network call fails. Recording both the run and an event atomically lets a dispatcher retry without losing accepted work. Stable IDs make repeat delivery converge on one logical run.

## C. Architecture and design decisions

Spring is the product authority because the user chose Java and because identity, transactions, policy, and usage need consistent ownership. A modular monolith reduces early distributed-system complexity while preserving domain boundaries. Python is isolated where document/ML tooling has a concrete advantage.

Temporal is proposed for durable execution, but an action ledger remains necessary: the engine cannot know whether an email API completed just before a timeout. PostgreSQL-first storage simplifies permissions and provenance. Model/framework adapters preserve choices without pretending all models have equal capabilities.

Alternatives and consequences are recorded in [six ADRs](../architecture-decisions/README.md), including microservices, an all-Python backend, framework-only persistence, dedicated vector/graph stores, and native clients first.

## D. Important technologies and concepts

- **Dependency injection:** the future harness receives a model/tool interface rather than constructing a specific vendor client. Tests can supply a controlled implementation and deployment can select a local provider.
- **Durable workflow:** waiting for approval is persisted state, not a sleeping HTTP thread. Without durable state, restart can lose the wait or repeat work.
- **Idempotency:** the same logical action carries one key across retries. A key only helps if the receiving system or our reconciliation logic enforces it.
- **Fencing:** a worker whose lease expired must not overwrite a newer result. A monotonically newer generation protects writes even if the old worker is still alive.
- **Embeddings:** vectors help find related passages. They do not establish truth, numerical correctness, or permission to read the passage.
- **Cache invalidation:** reuse is safe only when model, input, source versions, and access scope still match. A fast stale answer to “new messages?” can be worse than a slow fresh answer.
- **Sandboxing:** limits what untrusted code can affect. A container with the host Docker socket can control the host, so the broker's privileges must stay outside agent code.
- **Model routing:** eligible routes first satisfy hard privacy/capability constraints; then cost/latency/quality decide among them. A cheaper model is useful only if task success remains acceptable.

Kafka, distributed cache, graph databases, and Kubernetes are not foundation requirements. Without them the planned early system can still work. Add them only when a concrete workload exposes a gap they solve.

## E. Real-world use cases

**Clarification added 16 November:** the four examples below illustrate the architecture; they are not a closed catalogue, implemented bots or limits on what users may create. Zincc is proposed as a general-purpose agent platform: users describe their own responsibility/task, create persistent agents, select or extend tools and knowledge, and combine them into on-demand, scheduled or event-driven work. Optional prebuilt templates accelerate setup and remain editable. Actual execution depends on available capabilities, integrations, permissions and devices; a new agent name alone does not create an unavailable integration. See [agent scope](../product-scope-and-connectors.md).

An inbox agent reads permitted mail/Slack data and prepares a briefing. A report agent extracts a PDF table, computes totals in code, and creates a dashboard whose cells link to source pages. A live voice agent starts those same jobs while talking. A local agent uses a customer machine under device-local grants. The [platform research](../research/platforms.md) documents comparable product patterns without assuming access to private implementations.

## F. Edge cases and failure scenarios

- **Crash after external write:** record an unknown outcome, reconcile provider state, and avoid a blind retry.
- **Duplicate request/event:** stable scoped IDs and unique constraints prevent duplicate logical work.
- **Concurrent agents:** reserve budgets atomically, give each branch its own output, and fence shared writes.
- **API timeout or revoked connection:** report partial availability and stop prohibited actions; absence of a response does not mean absence of new data.
- **Hostile PDF/tool response:** treat content as evidence, not authority; sandbox parsing and independently authorize tools.
- **High load:** bound queues/concurrency and separate interactive from long media jobs. An unlimited agent tree can exhaust both money and compute.
- **Deleted document:** remove its retrieval/memory/cache derivatives so old evidence is not resurrected.
- **Voice interruption:** stop playback promptly; separately report whether an action already committed.

These are design requirements, not tested implementation claims.

## G. Trade-offs and limitations

The architecture favors portability, recoverability, and inspectability. Costs include workflow infrastructure, adapter maintenance, sandbox operations, and careful versioning. Supporting all audiences expands the test matrix, so capabilities ship in stages. Local hardware and weak-model limitations remain real. No architecture can guarantee arbitrary automation across inaccessible services.

We deliberately did not install dependencies, provision cloud resources, connect accounts, copy repository code, or build the application. Public research cannot reveal every proprietary feature or prove vendor performance/security claims.

## H. Code and document walkthrough

There is no application code to walk through yet. Important actual files are:

- [PLAN.md](../PLAN.md): product scope and success criteria.
- [ARCHITECTURE.md](../../ARCHITECTURE.md): module/data ownership and execution flow.
- [agent-runtime.md](../modules/agent-runtime.md): loop, routes, frameworks, memory, caches.
- [knowledge-and-analytics.md](../modules/knowledge-and-analytics.md): parsing, retrieval, datasets, citations.
- [security-and-sandboxes.md](../modules/security-and-sandboxes.md): execution/credential boundaries and customer runners.
- [live-and-connectors.md](../modules/live-and-connectors.md): integrations, triggers, voice, media.
- [references.md](../research/references.md): what was reviewed and what could be reused.
- [technology-baseline.md](../technology-baseline.md): observed versions and compatibility gates.
- [roadmap.md](../phases/roadmap.md), [status.md](../status.md), and [AGENTS.md](../../AGENTS.md): authorized task boundaries and teaching requirements.

## I. What should I remember?

1. The model proposes; trusted services authorize and verify.
2. A persistent agent identity does not need a permanently running container.
3. Durable orchestration does not guarantee exactly-once external effects.
4. Context, long-term memory, and caches solve different problems.
5. A citation can exist yet fail to support the answer's claim.
6. Use code for arithmetic and source-linked data for dashboards.
7. Local-only means every relevant component/fallback respects that policy.
8. Small models and multiple agents must earn their place through evaluation.
9. An observed stable version is not a tested dependency set.
10. Finish one authorized component with evidence and documentation before moving on.

## J. Interview preparation

**1. How would you prevent duplicate emails when a worker crashes?** Use a stable action ID, provider idempotency if available, a dispatch/receipt ledger, and reconciliation for ambiguous outcomes. Do not promise exactly once when the provider cannot support it.

**2. Why use both Temporal and PostgreSQL?** They own different concerns: durable execution history versus product records and transactional policy/action state. Define boundaries and reconcile projections rather than duplicate authoritative state.

**3. How do you make RAG multi-tenant?** Apply access rules to ingestion, candidate retrieval, reranking, output, artifacts, and cache keys; test revocation and cross-tenant IDs. A vector namespace alone is insufficient.

**4. How can a local model participate safely despite weak tool use?** Use capability checks, smaller typed tasks, deterministic validators, bounded retries, and permitted escalation. Keep authorization outside the model and declare unsupported work.

**5. When would you add Kafka or a dedicated vector database?** When measured event throughput/replay consumers or retrieval scale/latency exceed the simpler design, with a clear migration and operations plan—not merely because the product uses AI.

## K. Learning exercise

Draw the state transitions for “send a Slack message.” Include approval, dispatch, success, failure, timeout with unknown outcome, cancellation, and retry. Mark which transitions are safe after a crash and which need provider reconciliation. No coding or real message sending is required.

## L. Completion status

Completed: research-backed planning documents and development/teaching protocol. Verification: **21 Markdown files and 54 local links checked; zero missing targets, unbalanced code fences, trailing-whitespace issues, or missing A–L sections**. Git status contains only new planning documents. Git diff --check also returned clean, but the direct checks matter because these files are untracked. Runtime tests: not applicable; no application exists. Limitations: mutable upstream references, untested dependency compatibility, partial source-depth review, and unresolved deployment/licensing choices. Technical debt: none in application code; adoption follow-ups are explicit in the source ledger. Next recommended task: **P1.1 only, after authorization**.
