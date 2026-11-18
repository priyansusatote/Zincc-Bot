# Final product and architecture review

Date: **16 November 2026**. **Ready for the user's plan review and approval. Application development is not authorized or started.** This is a reviewed proposal, not a production-readiness certification.

## Reading order

**Post-review addition, 16 November:** the user requested existing-subscription/agent-engine support while reading this plan. [Connected AI engines](modules/connected-ai-engines.md), proposed ADR 0009 and P9.5–P9.9 add that option without replacing the existing stack. The custom-agent scope is now explicit in the initial learning note and product scope. [LoopForge](research/2026-11-16-loopforge-review.md) and [DuskBot](research/2026-11-16-duskbot-review.md) have separate evidence/recommendation addenda. The plan remains a proposal awaiting approval; the original review does not constitute runtime qualification of these new adapters.

1. This review: changes, decisions and unresolved trade-offs.
2. [Project plan](PLAN.md) and [user workflows/connector catalogue](product-scope-and-connectors.md).
3. [Architecture](../ARCHITECTURE.md), [deployment/operations](modules/deployment-and-operations.md) and [ADRs](architecture-decisions/README.md).
4. [Implementation roadmap](phases/roadmap.md), [learning coverage](learning-roadmap.md), then [reference evidence](research/2026-11-16-architecture-follow-up.md) and [version baseline](technology-baseline.md).

## Review scope and conclusion

Reviewed product journeys, personal/team/self-hosted editions, the entire documented architecture, stack choices, state ownership, failure recovery, scale, isolation, data handling, AI/runtime/framework design, retrieval/analytics, voice, desktop, scenes, connector targets, learning requirements and phase dependencies. Compared architecture-sensitive assumptions with selected source paths and primary documentation. The [follow-up evidence ledger](research/2026-11-16-architecture-follow-up.md) precisely distinguishes files read, documentation-only evidence and unresolved runtime qualification.

The core direction is sound: Spring modular monolith, PostgreSQL authority, Temporal durability, a model-neutral native harness, governed tools and isolated specialist workers. There is no evidence that replacing it with a full reference-repository fork, many microservices, or several overlapping agent frameworks would improve the product now.

The full requested ambition remains in scope. “Can perform work” means supported, authorized digital actions with verified outcomes; it is not a guarantee to operate every website/app or outperform every proprietary agent. Provider permissions, model capability and execution environment remain real constraints.

## Material corrections made

| Gap or incorrect assumption | Correction | Primary document |
| --- | --- | --- |
| Isolation appeared after parsing/plugins | P4.0 introduces constrained worker execution before hostile documents; P5 cannot load untrusted executable plugins until P6 isolation qualifies | Roadmap/security |
| Agent definitions appeared too late | Minimal versioned definition in P3.0; P7 adds orchestration, not first identity | Roadmap/runtime |
| A connector write appeared before connection infrastructure | P3.4 requires P3.4a minimal credential/grant/receipt contract; P5 expands catalogue/sync | Roadmap |
| Evaluation was a later standalone step | Fixtures, usage capture and component cases begin in P2.1; P2.5 consolidates the baseline | Roadmap/plan |
| Shared agents could imply owner privileges | Explicit initiator/effective principal, delegated grants, output audience and offboarding | ADR 0008/security |
| Protocol finish could look like task success | AG-UI presentation adapter, platform-owned durable events and verified completion | Agent UI contract |
| “Local” hid several different deployments | Hosted, hybrid, self-hosted online and offline profiles with explicit services/egress | Deployment/operations |
| Large ambitions lacked operating boundaries | Admission/fairness, target workload, failure matrix, restore quarantine and usage settlement | Deployment/operations |
| Privacy omitted durable history implications | Opaque references, safe metadata, payload/failure encoding, deletion and backup constraints | Architecture/operations |
| OpenBot MIT label implied full backend availability | Document published backend images and separately licensed Intelligence; no full backend audit claim | Reference ledger |
| Plugin manifest looked sufficient for safety | Validation before loading plus process/network isolation; signatures are identity, not safety proof | Security/connectors |
| Latest baseline had drift | Refresh observed Maven, React, Vite and pnpm versions; retain dated evidence and adoption-time compatibility gates | Technology baseline |

## Product readiness by audience

| Audience | First useful validated journey | Additional full-product obligations |
| --- | --- | --- |
| Individual | Research/PDF answer with citations, then daily email/Slack briefing and permitted scheduling | Persistent specialists, voice, desktop actions, artifacts/media and life-workflow packs |
| Team | Shared project/agent with correct resource visibility and auditable approvals | Offboarding, service identities, SSO/retention, budgets and collaboration controls |
| Self-hosted/local | Customer-controlled model and document processing with explicit network policy | Reproducible installation, backup/upgrade, hardware matrix, local runners and qualified offline profile |

Every milestone must expose a real user journey, empty/loading/error states, pause/cancel and an honest capability list. Connector state progresses from planned to experimental to qualified to deprecated/blocked. Account-specific availability is separate from product qualification. No catalogue entry is advertised as operational merely because its logo exists.

Full-scope phases remain P1–P10, P13–P16, final P11 polish and P12 broad qualification. Dependency exceptions in the roadmap bring safety foundations forward. Interim releases qualify their own enabled features; final P12 is not the first security or restore test. This retains the user's requested scope without pretending it is one small deliverable.

## Decisions and reasons

- Keep Spring in charge of identity, grants, budgets and action records. Framework/model changes should not redefine trust or data ownership.
- Keep Temporal for long waits, recovery and automation. Accept its operational footprint across supported durable profiles; don't build a second lightweight scheduler accidentally.
- Keep PostgreSQL/pgvector initially, with isolated analytics and object storage. Scale by measured workload before introducing Kafka, a graph store, vector cluster or Kubernetes.
- Offer native Java first; ADK, LangGraph/LangChain and PydanticAI are optional conformant adapters. One runtime implementation normally owns a run; all actions share the same authority and retry rules.
- Use AG-UI where interoperability benefits justify it. Durable cursor/replay, permissions, real action completion and data minimization remain Zincc responsibilities.
- Treat executable artifacts, MCP processes, plugins and browsers as separate untrusted workloads. A tool manifest, model judge or attractive UI is not an isolation boundary.
- Keep GraphRAG, fine-tuning, advanced inference/KV caching and harness optimization as explicit measured learning tracks. Production adoption requires improvement against the baseline; none is silently removed.

## Remaining concerns and when they must be resolved

| Concern | Consequence / decision point | Plan approval blocked? |
| --- | --- | --- |
| Very broad feature and connector scope | Significant sustained development/support effort; no delivery date or cost claim without measured velocity; split epics before authorization | No; scope and sequencing are explicit |
| Hardware, hosting region and monthly budget unknown | Determine placement, capacity, model/GPU choices before paid infrastructure or hardware-dependent tasks | No |
| Identity provider and product license undecided | Provider before P1.3; license before distribution or incompatible code reuse; no copied upstream code now | No |
| Provider API/partner access | Each connector requires qualified account/scopes/operations; blocked providers need a documented alternative or explicit scope decision | No; universal coverage is not promised |
| Small/local model quality varies | Benchmark capability-specific tasks; reject unsupported routes; no private-to-cloud fallback | No |
| Sandbox/GPU/native desktop support unproved | P4.0/P6/P10/P13 gates; disable unqualified execution paths | No |
| Framework/AG-UI evolution | Pin supported subsets, compare adapter semantics and test upgrades; no main-branch dependency assumption | No |
| Restore, deletion and action reconciliation are hard | First-class tests/runbooks before real affected cohorts; external writes cannot be rolled back by restoring a DB | No |
| Some upstream code unavailable or not fully inspected | Evidence level remains visible; exact adopted paths and dependencies need further review | No; no blind code reuse is approved |

These are implementation/adoption gates, not hidden claims of completion. Discovery of a constraint that changes user-visible scope requires an explicit plan amendment. Do not quietly mark a blocked requested feature complete.

## Approval boundary

The documentation review is complete and the plan is ready for approval. Approval of direction alone does not authorize all phases. The next proposed implementation unit remains **P1.1 only**: a bootable, pinned Spring foundation, health check, minimal module conventions, build/format verification and A–L report. No authentication, AI, frontend, connectors or infrastructure rollout belongs to that task.

See [status](status.md) for document verification and [the review learning report](learning-notes/005-architecture-review.md) for the engineering rationale. No application code or installed dependency was changed in this review.
