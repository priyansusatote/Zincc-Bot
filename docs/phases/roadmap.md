# Implementation roadmap

**P1 through P5 implemented for the local workbench.** [P5 qualification and account setup](../modules/phase-five-integrations.md). [P4 implementation and qualification](../modules/private-knowledge.md). The P3 batch passed the local sandbox, worker restart, durable approval and duplicate-write gates. [Operate the agent workbench](../modules/phase-three-agents.md). The 22 November batch included P2.3 through P2.5. [Routing](../learning-notes/013-route-selection.md) · [Research](../learning-notes/014-cited-search.md) · [Evaluation and costs](../modules/phase-two-evaluation.md). The user authorized P6.1–P6.4 together. The fixed worker broker, outbound device leases and offline browser handoff are implemented locally. One Docker Cloud Micro lifecycle and DNS policy benchmark passed. Multi-tenant qualification remains open. [P6 evidence](../modules/phase-six-runners.md). The user authorizes one scoped task at a time. [P2.1 live qualification](../learning-notes/011-openrouter.md) · [P2.2 replay qualification](../learning-notes/012-conversation-replay.md)

## Task completion contract

Before work, record the authorized task, acceptance criteria, and dependencies. Finish functionality, meaningful verification, updated docs/ADRs/status, and the focused component learning note. Explain failures and limitations. Stop at the task boundary. No hidden “while here” platform expansion.

The [learning coverage matrix and lab backlog](../learning-roadmap.md) are part of the proposed delivery/teaching plan as of **15 November**. Interleave L-RAG-01, L-GRAPH-01, L-INFER-01, L-TUNE-01, L-PREF-01, L-MAS-01, L-DIST-01 and L-SEC-01 only after their prerequisites and individual authorization. A lab has a reviewable experiment result; its technology is not automatically a production dependency. System design is taught in every relevant component, not reserved for P12.

The order below builds a usable vertical slice early, then adds stronger capabilities. Broad individual/team/local support is architectural; each edition's operational maturity grows through the phases. Time estimates require observed implementation velocity and budget; this is a dependency roadmap, not a promised calendar.

**Scope revision, 14 November:** the user includes formerly deferred capabilities in the full product. P13–P16 below are added as expansion epics. The intended sequence is now **P1–P10 → P13–P16 → final P11 polish → full P12 release qualification**. Phase IDs remain stable references, not numerical execution order. Test/security/operations work continues throughout. Expansion epics must be split into small individually authorized tasks before implementation.

**Dependency review, 16 November:** the sequence is dependency-driven within those phases. Add P3.0, P3.4a and P4.0 below. Pull relevant P6 isolation qualification forward before enabling any hostile multi-tenant parser/code/plugin workload; P5 executable customer packages cannot ship before that gate. Early component evaluation and operational checks belong to their introducing task. Final P12 repeats/deepens them. See [the review](../PLAN-REVIEW.md) and [operations](../modules/deployment-and-operations.md).

| Prerequisite | Consumer / rule |
| --- | --- |
| P1.2/P1.3 identity/resource grants | All private data and shared-agent execution; offboarding/revocation tests begin here |
| P2.1 fixtures and usage capture | Model selection from its first adapter; P2.5 consolidates, not starts, evaluation |
| P3.0 immutable agent definition | P3 loop and P5 automations; P7 expands into child graphs and richer management |
| P3.3 action authority plus P3.4a connection contract | First provider write; P5 reuses this contract instead of creating another |
| P4.0 worker restrictions | Parser/analytics and curated subprocess tools; not permission for arbitrary customer code |
| P6.2 or equivalent qualified isolation | Public hostile multi-tenant execution; untrusted executable P5 packages, P9 adapters and P10 scripts |
| Per-feature restore/privacy/overload tests | Before releasing that feature to real users; not postponed until P12 |

## P0 — Research and plan

| ID | Deliverable | Completion evidence |
| --- | --- | --- |
| P0.1 | Source-backed plan, architecture, versions, reference ledger, task/teaching rules | Documentation present; links/consistency checked; no application built |

## P1 — Repository, identity, and minimal workbench

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| **P1.1** | Repository conventions and Spring Boot foundation only | Maven wrapper/JDK pin, one bootable service, health endpoint, formatting/build checks, basic module-boundary rule; teach DI, startup, dependency management; no auth/AI yet |
| P1.2 | PostgreSQL schema/migrations and workspace membership | Migration from empty DB, rollback/recovery strategy, tenant-scoped queries; teach transactions, keys, RLS and pooled connection context |
| P1.3 | OIDC session and authorization boundary | Login/logout, session/CSRF controls as applicable, cross-workspace denied; teach authentication versus authorization |
| P1.4 | Accessible React workbench shell and typed API client | Build/type checks, keyboard navigation, error/loading states, account/workspace switch; teach contracts and UI state |

P1.1 verified the local build. P1.2 verified database isolation. P1.3 extends that boundary to authenticated requests.

## P2 — Useful model-neutral assistant and research

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P2.1 | Model gateway with capability registry | Start evaluation fixtures and usage capture; configured provider smoke tests, malformed output/unsupported capability handled; teach ports/adapters |
| P2.2 | Conversation persistence and resumable event stream | Reconnect/dedup, expired cursor/snapshot and concurrent message tests; specify AG-UI mapping without requiring its SDK; teach event sequencing and optimistic revisions |
| P2.3 | Local-only route and rule-based router | No external fallback in local-only test; model quality differences visible; teach routing constraints and evaluation |
| P2.4 | Web search, evidence records, cited answer | Requested/final URL, observation time, cache status and partial crawl coverage. Compare source support and freshness under bounded crawling. [Firecrawl review](../research/2026-11-19-firecrawl-turboquant.md) |
| P2.5 | Evaluation baseline and cost accounting | Initial 60-case suite and usage reconciliation; teach task success, confidence calibration, and cost per successful task |

Gate: useful cited research with explicit cloud/local limits. No unsupported claim that every local model can perform tool use.

P2 conversations do not yet claim durable autonomous actions; an interrupted model response is visibly interrupted. P2.5 can freeze cases for later workflows, but marks them pending until genuine execution exists. Initial backend cases, UI contracts and privacy/usage checks ship with each component.

## P3 — Durable agent harness and governed actions

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P3.0 | Minimal persistent agent definition and immutable version | Name, instructions, model policy, tool grants and owner/delegation; version changes do not mutate active runs; teach definitions versus executions |
| P3.1 | Run state machine, transactional outbox, Temporal worker | Kill/restart around dispatch and durable wait; teach at-least-once delivery and workflow replay |
| P3.2 | Bounded native agent loop and checkpoints | Tool/model failure, step budget, cancellation, no-progress stop; teach plan/act/observe/verify |
| P3.3 | Tool manifests, policy engine, approval/action ledger | Changed payload/revocation denied, duplicate intent deduped, unknown outcome exposed; teach idempotency and TOCTOU |
| P3.4a | Minimal connection, credential and receipt boundary | One provider's OAuth/resource grants, secret references, refresh ownership and revocation; fixtures first, live accounts only when configured; teach credential lifecycle |
| P3.4 | One read + one controlled write connector vertical slice | Real receipt or explicit sandbox fixture, timeout reconciliation; teach external consistency and OAuth scopes |

Gate: no duplicate write in failure tests; UI can distinguish waiting, failed, unknown, and verified complete.

## P4 — Knowledge, PDFs, and dashboards

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P4.0 | Fixed-purpose isolated worker execution boundary | Pinned image/process, no API secrets, bounded resources/output, selected mounts and enforced egress; parser crash cannot access product DB; teach isolation before ingestion |
| P4.1 | Upload/object lifecycle and isolated parsing | Corrupt/encrypted/oversize files handled; page/table provenance retained; teach staged ingestion |
| P4.2 | Hybrid private retrieval and citations | Tenant/ACL tests, Recall@k baseline, source deletion propagation; teach embeddings, full-text search and reranking |
| P4.3 | Versioned table extraction and analytical worker | Known totals/units exact, malicious SQL denied, reproducible transformations; teach columnar data and deterministic computation |
| P4.4 | Persistent dashboard with source drill-down | Filters and exports agree with dataset, revision conflict handled; teach data lineage and typed visualization specs |

Gate: report-to-dashboard journey has traceable numbers. Difficult extraction shows uncertainty rather than fabricated precision.

P4.0 becomes the minimal execution-provider contract reused/extended in P6, not a second broker. Trusted local development containers are not hostile-tenant qualification. Before public multi-tenant document processing, complete the applicable P6.2 isolation gate. Fixed allowlisted transformations may precede arbitrary generated Python/SQL execution; the latter requires the stronger sandbox gate.

## P5 — Connectors, MCP, skills, and recurring work

The user authorized the full P5.1–P5.5 batch including Calendar. The local acceptance scope uses sample accounts, the real isolated MCP tool and P4 dashboards. External provider qualification requires user consent. [Implementation and limits](../modules/phase-five-integrations.md)

The [provider follow-up](../modules/model-gateway.md#provider-follow-up-for-the-local-workbench) tracks the user's OpenRouter, Numes, Context.dev, OpenCode and Google AI Pro options for separately scoped qualification.

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P5.1 | Expand connection contract into registry and full email sync | Reuse P3.4a, then incremental sync, revoked scope, multi-account separation; teach cursor-based sync |
| P5.2 | Slack connector, then calendar as separately authorized subtask | Pagination/rate limits, channel scope and partial failure; teach webhook replay/dedup |
| P5.3 | MCP adapter and curated tool package | Version negotiation, malformed schemas, bounded metadata, credential scope; teach protocol versus policy |
| P5.4 | Skill package install/version/update lifecycle | Manifest validation, integrity, broadened permissions, rollback; teach supply-chain boundaries |
| P5.5 | Durable time/event automations | DST, overlap, missed run, duplicate event, self-trigger loop, pause; teach durable scheduling |

Gate: daily inbox/Slack briefing and report refresh with visible grants, receipts, and pause controls. Add other connectors one per authorized task; registry completeness is not integration completeness.

P5.3 begins with a qualified remote MCP server or fixed-purpose restricted subprocess. P5.4 begins with declarative instruction/manifest packages. Customer executable plugins, install hooks and arbitrary stdio servers remain disabled until P6 isolation qualification; complete that dependency before declaring the executable package feature finished. Signature/schema checks alone are insufficient.

## P6 — Sandboxes and customer-owned compute

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P6.1 | Extend P4.0 worker contract into general sandbox broker | Resource/mount/egress limits, termination, no host socket exposure; teach process/container boundaries |
| P6.2 | Hosted isolation backend qualification | Benchmark CubeSandbox or suitable alternative; hostile tenant tests, snapshot/secret handling; teach VM/gVisor trade-offs |
| P6.3 | Customer runner enrolment and local controls | Outbound auth, revoke/pause, selected folders, lost heartbeat; teach device identity and leases |
| P6.4 | Browser worker and user takeover | Account-profile isolation, browser receipts, handoff, diagnostic tooling; teach browser/session security |

Gate: isolated execution on a supported environment with documented limitations. Windows native app automation is a separate later capability, not implied by Docker support.

## P7 — Multiple agents, memory, caching, and improvement

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P7.1 | Expand P3.0 agent management and bounded parent/child execution | Delegation lineage, resource grants, fan-out budget, cancellation, merge conflicts; teach DAGs, concurrency, fencing |
| P7.2 | Context/cache optimization | Measured cache savings, ACL invalidation, stale-write and stampede tests; teach cache keys/invalidation |
| P7.3 | Source-linked memory and user editor | Correction/deletion/contradiction tests, scoped recall; teach working/episodic/procedural memory |
| P7.4 | Offline harness experiment and promotion pipeline | Fixed holdout, regression/cost report, rollback; teach experimental controls and prompt/skill versioning |

P7.4 includes a [tool discovery experiment](../research/2026-11-24-code-mode-and-design-agent.md). Compare direct calls, on-demand schemas and brokered programmatic calls on identical fixtures before changing the default harness.

Gate: each added agent/cache/memory policy demonstrates benefit against the baseline. No unrestricted production self-rewriting.

## P8 — Live conversation that performs actions

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P8.1 | Voice transport and turn handling | Tested STT/TTS latency, barge-in, reconnect, text fallback; teach streaming media versus control plane |
| P8.2 | Live Gmail/Slack read and draft/send flow | Stable intent IDs, exact approvals, partial results, duplicate speech; teach asynchronous action coordination |
| P8.3 | Local voice route and multilingual qualification | Hardware/capability matrix, Hindi/English tests, retention controls; teach modality routing and real-time budgets |

Gate: the user's “check email/Slack while talking” journey works with honest freshness and failure reporting. Camera/meeting/avatar support follows in P14 and is included in the full requested scope.

## P9 — Customer framework adapters and builder

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P9.1 | Framework contract and ADK adapter | Shared cancellation/approval/checkpoint suite and first qualified AG-UI event adapter; split adapter delivery if needed; teach contract conformance |
| P9.2 | LangChain/LangGraph adapter | No overlapping side-effect retries, persisted graph state; teach inner versus outer orchestration |
| P9.3 | PydanticAI adapter | Validated outputs/tools through gateway; teach typed dependencies and boundary validation |
| P9.4 | Agent builder and external-agent interface | Version/diff/rollback, governed versus unmanaged tools visible; teach declarative definitions and trust boundaries |
| P9.5 | Connected engine/account contract and offline fixture | Model versus engine distinction, consent/billing/device status, scoped tools, cancellation and recovery; prerequisites P3 and relevant P6 controls |
| P9.6 | Eligible direct ChatGPT plan-use adapter | Official protocol, edition eligibility, identity versus inference consent, model discovery, revocation and allowance limits; no silent API fallback |
| P9.7 | Codex engine adapter | Official app-server lifecycle, supported authentication, isolated account/session and native tool enforcement; fixtures then live qualification |
| P9.8 | Claude Code engine adapter | Unmodified CLI and provider-owned sign-in, streaming/MCP, scope checks and provider conditions; separate authorization from P9.7 |
| P9.9 | Grok Build engine adapter | Official ACP/CLI and supported account, MCP/native-tool checks, allowance handling and OS qualification; API route remains separate |
| P9.5a | Connection and model picker | After P9.5, exercise agent defaults, conversation overrides, per-account discovery, explicit model acknowledgement, billing labels and next-run changes with fixtures; surface qualified routes as adapters pass |
| P9.10 | OpenCode engine and Go qualification | Discover provider-qualified models, distinguish Go from separately billed connections, and verify effective model and allowance behavior |
| P9.11 | Antigravity engine qualification | Use official CLI/ACP sign-in and verify account-specific discovery, supported controls, deployment eligibility and execution on the selected runner |
| P9.12 | Kimi Code engine qualification | Verify official login/ACP and distinguish membership from API billing; a rejected or unapplied explicit model change must stop dispatch |

P9.5–P9.9 are the user's additive proposed scope from 16 November, not implementation approval. See [connected AI engines](../modules/connected-ai-engines.md). Desktop distribution depends on P13; each provider/OS/edition is qualified separately. Other engines may be added through the same contract. LoopForge-inspired reusable-workflow and rework UX refinements remain [recommendations for review](../research/2026-11-16-loopforge-review.md).

Gate: a customer can run one automation on each supported framework with documented differences. AG-UI/A2A are added only for concrete interoperability needs.

## P10 — Rich artifacts and multimodal production

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P10.1 | One document renderer, then each format separately | Source+preview+export, layout verification, editable provenance; teach rendering pipelines |
| P10.2 | Image generation/editing adapter | Model/cost/rights metadata, failure and cancellation; teach multimodal artifacts |
| P10.3 | Scene Studio and audio/video production, decomposed below | Separate interactive scenes and qualified video exports; every subtask individually authorized |
| P10.4 | Editable site/app artifact and controlled publication | Preview isolation, source revisions, destination/permission check; teach build versus publish boundaries |
| P10.5 | Shared creative brief, versioned brand kit and cross-format review | One approved brief produces an editable slide and social graphic. A correction propagates to both exports with revision history. Extend to motion and qualified Slack input separately. [Acceptance journey](../research/2026-11-24-code-mode-and-design-agent.md) |

**Scene Studio expansion, 15 November:** P10.3 is now a parent reference, not a single implementation task. [The module specification](../modules/scene-studio.md) defines the feature. Dependencies include P3 durable jobs, P6 isolated execution, P7 bounded review and existing artifact storage; reuse these components rather than introduce a second control plane. P10.4 reuses preview isolation from P10.3a.

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P10.3a | Scene manifests, immutable revisions and isolated preview contract | Restricted fixture loads; hostile import/network/message tests; teach executable artifacts and trust boundaries |
| P10.3b | One prompt-to-2D animation path | Editable source and a repeatable frame clock; seed/seek tests; teach generation versus rendering |
| P10.3c | One procedural 3D scene profile | Basic movement/viewpoints, capability detection, quality settings and measured device behavior; teach shaders, scene graphs and GPU limits; split higher-level/raw WebGPU profiles if needed |
| P10.3d | Bounded visual-review and repair loop | Fixed views plus motion sample, structured defects, revision ownership, cost/stagnation stop; compare with generator-only baseline |
| P10.3e | One finite camera timeline and video export profile | Qualify Hyperframes or direct capture adapter, correct frames/duration, verified GPU capture where required, playable export; teach deterministic jobs and encoder backpressure |
| P10.3f | Audio and captions for exported scenes | Narration or offline synthesis plus one mix/caption path, sync and mute tests; qualify remaining media variants separately |
| P10.3g | Project packaging, import adaptation and private delivery | Editable bundle, conditional self-contained HTML, retained notices, unsupported-import report, access-controlled download and version restore |
| P10.3h | Render recovery and qualification | GPU/worker-loss, duplicate job, cancellation, fencing, cache invalidation and device-matrix tests; qualify further export profiles as separate tasks |

These are planning boundaries, split further before work if necessary. Essential limits, isolation and error handling ship with each component; P10.3h deepens recovery and load qualification. A procedural underwater scene is one benchmark/example, not the only product template.

Gate: artifact quality verified in its native format. A generated source file alone is not a successful finished deliverable. Scene exports include a declared render profile and verification evidence; an attractive screenshot alone does not prove temporal quality or performance.

## P11 — Premium UI polish after main capabilities

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P11.1 | Visual system and workbench refinement | Typography/spacing/motion tokens, responsive layouts, real states; teach design systems |
| P11.2 | Artifact editing, agent activity, approval UX | Usability sessions on completed end-to-end journeys; teach feedback and progressive disclosure |
| P11.3 | Accessibility/performance and device packaging decision | Keyboard/screen-reader/contrast checks, frontend budgets; teach accessibility and rendering performance |

Gate: coherent individual/team/local UX across completed core and expansion capabilities. Native mobile/desktop clients are in P13; responsive web remains the initial baseline. Run this final polish phase after P13–P16.

## P12 — Production qualification and broader editions

| ID | Scoped task | Acceptance / learning focus |
| --- | --- | --- |
| P12.1 | Isolation, abuse, and reliability review | Adversarial suite, load/cost report, provider outage drills; teach failure domains |
| P12.2 | Deployment, backup/restore, upgrades | Restore drill, workflow/schema compatibility, rollback; teach RPO/RTO and migrations |
| P12.3 | Team administration and usage controls | SSO/provider setup, roles, quotas, audit export, retention; teach operational tenancy |
| P12.4 | Self-hosted/offline release qualification | Reproducible install, egress tests, model/hardware matrix, update docs; teach portability limits |
| P12.5 | Release readiness and measured comparison | Expanded 200+ case evaluation, known issues, support/runbooks, benchmark methodology; teach honest product claims |

Security/operations checks run throughout development. This phase deepens qualification; it is not permission to postpone basic protection until the end.

Before any real-user cohort, qualify its enabled subset: tenant/resource isolation, revocation, ambiguous external outcomes, bounded cost/load, backup restoration, retention and incident controls. P12.3 adds enterprise administration; basic team permissions and offboarding are already prerequisites. P12.4 packages offline/self-hosted distributions; local-only data policy is tested from P2.3 onward.

## P13 — Native clients and broader local execution

These are requested scope epics; split each into reviewable tasks at entry.

| ID | Epic | Acceptance boundary |
| --- | --- | --- |
| P13.1 | Desktop clients for Windows/macOS; qualify Linux separately | Install/update, authenticated session, notifications, runner enrolment and revoke controls |
| P13.2 | Native desktop automation bridge | Per-OS application/file grants, enforced pause/takeover leases and action verification; Windows acceptance: open Paint, draw through UI, save and verify; split OS/driver tasks before implementation |
| P13.3 | Android client | Chat, live voice, progress, approvals, notifications, reconnect and accessible controls |
| P13.4 | iOS client | Equivalent supported journeys, platform permissions and reconnect; no claim of unrestricted phone app control |
| P13.5 | Browser extension | Selected page/tab context, visible grants, safe messaging and account separation |

## P14 — Screen, meetings, and avatar experience

| ID | Epic | Acceptance boundary |
| --- | --- | --- |
| P14.1 | Camera/screen context | User-selected capture, privacy controls, bounded media processing and source freshness |
| P14.2 | Meeting capture and follow-up | Supported meeting provider, participant/recording controls, transcript and action-item verification |
| P14.3 | Active meeting participation | Provider-supported join/speak/chat, turn handling, tool permissions and interruption tests |
| P14.4 | Avatar option | Optional visual layer over the tested live agent, latency/fallback and identity controls |

Scheduling a meeting is not equivalent to a bot joining and speaking. Qualify those provider capabilities independently.

## P15 — Business and daily-life workflow packs

Each pack requires multiple small connector, skill and end-to-end tasks; do not implement a whole row under one vague authorization. Connector targets are in [the catalogue](../product-scope-and-connectors.md).

| ID | Epic | Acceptance boundary |
| --- | --- | --- |
| P15.1 | Sales/CRM prospecting | Source-linked lead research, explicit scoring criteria, CRM deduplication and verified outreach |
| P15.2 | Recruiting/ATS coordination | Authorized sourcing, evidence tied to job criteria, ATS updates and scheduling; human hiring decisions |
| P15.3 | Support operations | Contextual investigation, ticket updates/replies and separately scoped refunds/account changes |
| P15.4 | Expenses/accounting | Source-linked extraction, exact arithmetic, duplicate prevention and reconciled entries |
| P15.5 | Subscriptions/returns | Renewal/refund state, supported cancellation/claim flow and confirmation evidence |
| P15.6 | Travel/shopping/bookings | Provider qualification, final price/itinerary constraints, payment/booking receipt and cancellation handling |
| P15.7 | Permits/inspection coordination | One jurisdiction at a time, source/identity/signature requirements and submission verification |
| P15.8 | Home/family assistance | Shared calendars and maintenance records; optional device actions with specific grants |
| P15.9 | Trading-related automation | Research/alerts, paper-trading tests, then qualified explicitly specified order workflows; no silent live-order upgrade |

## P16 — Public ecosystem and portable agents

| ID | Epic | Acceptance boundary |
| --- | --- | --- |
| P16.1 | Publisher and package trust | Publisher identity, integrity/signatures, dependency/license record and permission manifest |
| P16.2 | Submission/review lifecycle | Sandbox qualification, evidence, approval state, updates and rollback |
| P16.3 | Marketplace operations | Discovery, install/remove, reports, revocation and moderation; no unreviewed privilege expansion |
| P16.4 | Agent/skill export and import | Portable definitions with compatibility report; credentials never bundled; private memory export separately controlled |

After these epics, complete final P11 polish and full P12 release qualification. Interim working releases remain useful milestones; they are not completion of the requested flagship scope.

## P1.1 acceptance boundary (completed)

Deliver only a reproducibly bootable Spring service, pinned build, minimal module conventions, health check, and build/format verification with its learning report. Update version evidence if releases changed. Do not add authentication, chat, frontend, Temporal, models, connectors, or sandbox infrastructure in this task.

Needed before later tasks, not blockers for planning: hosting budget/region; identity provider; API credentials; local CPU/GPU/OS/Docker constraints; artifact storage choice; product license/distribution policy. Ask at the point the answer changes implementation.
