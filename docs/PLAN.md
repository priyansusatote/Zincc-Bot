# Zincc project plan

Initial planning snapshot on **14 November 2026**, reviewed on **16 November**, approved on **18 November**. P1.1 delivered the local Spring Boot foundation. See the [current implementation status](status.md). Dated revisions below preserve the planning history.

Scope clarification, 14 November: the user explicitly wants the previously deferred client, desktop, meeting, avatar, commerce, and marketplace capabilities included in the full product. See [action scope and named connectors](product-scope-and-connectors.md). Later phases are delivery stages, not removed scope. Implementation still awaits approval.

Learning clarification, **15 November**: the short completion summaries were not a complete curriculum. [The engineering learning roadmap](learning-roadmap.md) now maps system design, advanced retrieval/GraphRAG, agentic/multi-agent orchestration, tool calling, HITL, evaluations, inference/KV cache, and fine-tuning to core tasks and explicit hands-on labs. Lab coverage does not imply automatic production adoption or current authorization to execute.

## 1. Product direction

**Clarification and addition, 16 November:** users can create their own general-purpose or specialist agents; examples and template packs are not a closed capability menu. The user also requested [existing AI plans and connected agent engines](modules/connected-ai-engines.md), added alongside API keys, local models and existing frameworks. Provider/edition eligibility is explicit. This adds proposed P9.5–P9.9 tasks; it does not authorize implementation. [LoopForge recommendations](research/2026-11-16-loopforge-review.md) and [DuskBot findings](research/2026-11-16-duskbot-review.md) record the source evidence and additional feature candidates.

Feature addition, **15 November**: [Scene Studio](modules/scene-studio.md) expands P10 with code-generated interactive worlds, editable motion graphics, bounded visual critique and video export. The supplied ocean example has been resolved to its original project and selected source files reviewed. No runtime implementation is authorized by this planning revision.

Build a persistent AI work platform that can understand a request, gather evidence, produce useful artifacts, carry out permitted actions, and maintain ongoing responsibilities. It must work for **individuals, teams, and self-hosted/local users**, as clarified by the user.

The ambition is broad, but “better than every flagship” is a hypothesis to test, not an engineering guarantee. Zincc's defensible advantages should be:

1. Model and framework portability, including explicit local-only operation.
2. Reliable completion with inspectable receipts, citations, checkpoints, and recovery.
3. Transparent per-task cost, route choices, permissions, and memory.
4. One coherent flow across conversation, documents, dashboards, voice, and recurring work.
5. User-owned compute and data as first-class deployment choices.
6. Reusable agents and skills that improve through evaluated, versioned changes.

Functional inspiration comes from the products in [platform research](research/platforms.md). Zincc will have its own identity, code, and interaction design.

## 2. Product surfaces

| Surface | User job | Essential behavior |
| --- | --- | --- |
| Home / Inbox | See what needs attention | Results, decisions, failed runs, upcoming work, meaningful changes |
| Chat / Workbench | Ask and get work done | Streaming conversation, attachments, editable plan, sources, progress, artifacts |
| Projects | Keep context together | Files, instructions, shared knowledge, memory, agents, permissions |
| Agents | Give recurring responsibilities | Profiles, tools, framework, execution environment, budgets, schedules, versions |
| Automations | Run work on events or time | Trigger preview, conditions, action policy, history, pause, replay from safe checkpoint |
| Knowledge | Work with private information | Upload, ingestion status, cited search, document versions, access controls |
| Canvas / Artifacts | Review and edit results | Reports, tables, charts, slides, websites, generated media, provenance |
| Live | Talk while actions happen | Interruptible voice, live action cards, typed fallback, call history |
| Connections / Plugins | Add capability | OAuth, MCP, scoped credentials, curated bundles, permissions, connection health |
| Activity / Usage | Understand execution | Task graph, retries, receipts, cost, tool activity, errors |
| Settings / Admin | Control the platform | Identity, roles, data location, models, retention, policy, quotas, deployment health |

Premium polish comes after core capability completion, as requested. Navigation, accessible components, usable approval cards, and clear error states start early because they affect correctness.

## 3. Capability inventory

### Persistent agents and automation

Create named agents for research, inbox triage, reporting, developer work, or a custom responsibility. Each has a versioned definition, owner, workspace, permitted tools, model policy, memory scope, budget, and execution environment. An agent is a durable identity; it does not require a continuously running container.

Support scheduled jobs, event triggers, bounded monitoring, milestones, condition-based wakeups, quiet hours, human decisions, and resumption after failures. Daily work includes meeting preparation, draft replies, report updates, document reconciliation, competitor monitoring, and issue triage.

“Automate any daily work” means an extensible architecture and expanding capability catalogue. A service still needs a usable API or browser path, valid credentials, permission, and appropriate execution capacity.

### Research and knowledge

Fast web lookup, deeper multi-source research, private-document search, source comparisons, research notebooks, saved collections, and scheduled refresh. Answers must distinguish evidence, inference, conflicts, and missing information.

PDF/report workflows preserve text, layout, page references, tables, figures, reading order, source versions, and extraction confidence. RAG finds relevant evidence; validated code computes results. Detailed design: [document intelligence](modules/knowledge-and-analytics.md).

### Analytics

Upload reports or connect a supported dataset, select relevant measures, inspect extracted data, and generate a persistent dashboard. Support filters, comparisons, time series, drill-down to source pages, formula transparency, dataset corrections, exports, refresh schedules, and sharing controls.

Do not manufacture numbers from prose. An unstructured report can support a narrative or evidence view even when it cannot support a numerical dashboard.

### Parallel and multimodal work

Use specialist workers for research, planning, extraction, verification, charting, code, images, speech, or video. Run independent branches concurrently and serialize conflicting writes. Each branch has a budget and output contract. One task owns final synthesis.

Support text, PDFs, images, audio, video, and structured data. Creation includes DOCX/PDF/XLSX/PPTX, charts, simple sites/apps, images, narration, and edited video. File creation, live editing, and hosted publication are separate capabilities.

### Code-generated scenes and video

Users can describe a world or upload HTML/JavaScript, generate an interactive preview, change it through conversation, direct a camera path, and export a video or editable bundle. Support procedural 3D environments, 2D motion graphics, product scenes, educational animations and source-backed data stories. Add narration/captions/audio through existing media capabilities.

Keep interactive play and reproducible export as separate execution modes. An isolated browser/GPU worker renders actual frames; a bounded visual reviewer and deterministic checks guide repairs. The Spring backend retains artifact versions, permissions, budgets and durable jobs. Model routing remains provider-independent. Single-file delivery is available where feasible; source size, visual quality and device performance are measured separately. See [Scene Studio](modules/scene-studio.md) and [ADR 0007](architecture-decisions/0007-code-generated-scenes.md).

### Live action agent

Example: “Check my email and Slack. Any new notifications?”

The voice session can acknowledge immediately while authorized read tools run concurrently. It gives an as-of time, source counts, and any unavailable connection. Follow-ups such as “draft a reply” create an artifact; “send it” goes through the same action policy as typed requests. Progress can continue after the voice call ends if the task was assigned that way.

Start with voice and action cards. Camera/screen context, meeting participation, and an avatar option are included in the requested full scope and delivered in later phases. VoiceStudio is primarily a voice production reference; it is not a complete low-latency action-agent backend.

### Customer agent building

Provide a default Zincc native agent and three optional framework adapters:

- **Google ADK** for its agent/workflow ecosystem.
- **LangChain with LangGraph** for explicit stateful graph execution.
- **PydanticAI** as the third option for typed agents, validated outputs, and model portability.

Framework choice is an advanced option, not a required onboarding question. Agents built elsewhere can join through a constrained protocol adapter. Their security and durability guarantees must be displayed accurately.

### Skills and plugins

Versioned bundles can include instructions, tools, schemas, UI components, assets, and runnable dependencies. They declare requested capabilities. Discover first, inspect permissions, install a pinned revision, test in isolation, then enable.

Start with curated first-party skills: research brief, inbox digest, meeting prep, PDF Q&A, report-to-dashboard, document creation, and developer issue triage. Customer uploads and a public marketplace follow after isolation and update controls are proven.

## 4. Delivery increments for all user groups

| Increment | Individuals | Teams | Self-hosted/local |
| --- | --- | --- | --- |
| Foundation | Account, project, chat | Tenant and membership model | Local development profile |
| Useful alpha | Cited research and file analysis | Shared project with enforced access | Local endpoint, local document processing |
| Action beta | Inbox/calendar tasks and automations | Shared agents, reviews, audit | Customer-controlled runner |
| Agent platform | Multiple agents and framework choices | Team policies and usage budgets | Docker/VM and private deployment options |
| Flagship release | Voice, artifacts, persistent responsibilities | Admin operations, SSO, collaboration | Qualified offline mode and upgrade/backup tools |
| Polish release | Refined desktop/mobile workbench | Consistent team experience | Clear resource and capability diagnostics |

Serving everyone does not mean every enterprise control or native mobile app ships in the alpha. The core schema and interfaces must avoid painting any edition into a corner.

## 5. Architecture decisions in plain language

- **Spring Boot owns the product.** It decides who can access data, what may run, how much may be spent, and what actually happened.
- **Temporal owns durable job progression.** A task can wait for hours without keeping an HTTP request or worker thread alive. It does not make external writes exactly once.
- **The harness owns agent behavior.** Planning, context assembly, model calls, tool proposals, verification, and bounded replanning live behind a versioned interface.
- **The tool gateway owns governed external actions.** Selecting another framework does not widen a governed worker's grants; its credentials and egress enforce that boundary.
- **Governance has an enforceable boundary.** Hosted governed workers have constrained network/credentials; externally hosted agents with private tools are explicitly unmanaged for those tools. A protocol adapter cannot enforce policies inside someone else's machine.
- **Sandboxes own execution isolation.** They contain untrusted code and browsers; the trusted API server never runs customer code directly.
- **PostgreSQL is the initial system of record.** Use relational state, a transactional outbox, full-text search, and pgvector before adding specialized databases.
- **Python is a specialist worker language.** It is useful for OCR, document parsing, ML models, data analysis, and framework adapters; it does not replace the requested Java backend.

See [ARCHITECTURE.md](../ARCHITECTURE.md) and the [ADRs](architecture-decisions/README.md).

## 6. How we will measure “better”

Create a frozen, permissioned evaluation set before sophisticated routing or multi-agent expansion. Proposed initial set: 60 synthetic or public-data tasks, 10 each for research, structured extraction, PDF analytics, inbox triage, recovery, and local-model operation. Expand to at least 200 varied cases for release qualification.

Start component fixtures and usage capture in P2.1; P2.5 consolidates the suite. Stage future workflow cases as pending until their component exists, never as passing mocks. Freeze task definitions/splits and record explicit dataset versions when expanding. Set acceptance thresholds before comparing candidates, report uncertainty/repeated trials, and retain adversarial/long-running cases alongside happy paths. The 60/200 counts are coverage floors, not proof of quality or flagship superiority.

Measure:

- Validated task completion, artifact correctness, citation support, and numerical correctness.
- Unauthorized-action and cross-tenant-access tests: zero successful violations in the test suite.
- Duplicate side effects, recovery success, cancellation behavior, and stale-result rejection.
- Total cost per successful task, including failed attempts, model review, search, OCR, sandbox time, and voice minutes.
- Latency distributions, not just average response time.
- User interruptions per successful task and clarity of failure/approval states.
- Quality by model, language, document type, framework, and execution mode.

A router is accepted only if it reduces cost or latency without exceeding an agreed quality regression threshold. An extra agent is accepted only when it improves the measured outcome enough to justify coordination cost.

Initial engineering targets, to be benchmarked rather than advertised: p95 API acceptance under 500 ms excluding external calls; first visible job acknowledgement under one second; voice acknowledgement around one second on the tested network; 100 concurrent active runs as an initial controlled load target. Define hardware, corpus, provider, and measurement windows with each benchmark.

The target is 100 admitted runs including durable waits, with separately bounded model/browser/GPU execution. [Deployment and operations](modules/deployment-and-operations.md) defines workload examples, availability/recovery targets, queue limits and pre-cohort gates.

## 7. UI/UX direction

A [generated workbench concept](design/workbench-concept-01.md), dated 16 November, illustrates the proposed conversation plus live-computer layout. It is a visual study, not an implemented UI or approved final design. The initial P1.4 shell builds the navigation and real empty/loading/error states; the pictured Windows action arrives with P13.2.

Use a calm workbench with a conversation column, a resizable artifact/evidence pane, and an optional activity drawer. Prioritize legible typography, excellent empty/loading/error states, clear task ownership, keyboard access, responsive layouts, and reduced-motion support.

The user's four screenshot references, reviewed 16 November, refine the proposed personal default to an agent roster beside conversation, with the artifact/computer pane opened contextually. Pin the primary assistant, surface specialist activity and verified result/routine cards, and place broader workspace tools in compact secondary navigation. Support light and dark themes; the reference images do not select a final theme. The design note records attachment-specific observations.

Borrow principles: Perplexity's source-oriented research, Grok Bot's task visibility, Muse/Dots' approachable ongoing relationship, OpenBot's visible computer handoff, Apple's hierarchy/accessibility, and Lovable's direct artifact editing. These are functional references, not permission to copy assets.

Expose “Fast / Balanced / Deep / Local” presets, with advanced route settings behind a details panel. Show what will leave the device. Separate “reading,” “drafting,” “waiting for approval,” “executing,” and “verified complete.” Never use a streaming animation as proof an action succeeded.

Aim for WCAG 2.2 AA. Test keyboard-only navigation, screen readers, focus restoration, contrast, long content, upload failure, revoked connections, slow networks, and narrow screens. Use real task states, not a static marketing mockup.

## 8. Cost and deployment strategy

Support hosted, private-cloud/self-hosted, and local/hybrid deployments through configuration and adapters. Fully local mode includes the control plane, storage, model, embeddings, parser, and tool execution on user-controlled infrastructure. A local model behind a cloud control plane is hybrid, not fully local.

Model cost per successful task:

`(model input + output + cache writes/reads + embeddings/reranking + search + OCR + sandbox compute + storage/egress + voice/video + retries) / verified successful tasks`

Use budget reservations before spawning work and settlement after usage arrives. Limit parallel branches, runtime, output tokens, polling, video duration, and recurring-job frequency. Idle agents sleep; event-driven work replaces repeated model polling where possible.

No defensible rupee/dollar operating estimate exists until usage, hosting region, hardware, provider credentials, and task benchmarks are known. During the foundation phase, create a cost worksheet using measured workloads and current provider pricing. Do not interpret the user's chat subscriptions as production API access.

## 9. Your models during development

Yes: top-tier reasoning is unnecessary for every task. Use the available Sol model for routine scoped implementation and explanations; reserve Astra for difficult architecture, concurrency, security, or unresolved debugging. Gemini Argon can provide an independent review of a concrete design or multimodal result.

This is a suggested working allocation, not a claim that one model always wins. Judge the actual output and tests. When outside input is useful, prepare a compact review packet containing the problem, invariants, alternatives, relevant files, and questions. Never include credentials or private user data.

There is no external-model request needed to finish this planning task.

## 10. Scope that needs later qualification

The full requested scope now includes native clients, broader desktop control, meeting participation, an avatar option, public plugin publishing, and supported shopping/booking actions. These require specific implementation and qualification phases. Purchasing uses scoped instructions and supported payment flows; it is not an unrestricted grant. Production changes remain explicitly permissioned. Regulated advice, biometric identification, and unbounded self-modification are not implied by general-purpose automation.

Team real-time coediting can begin with document versions and optimistic concurrency; add CRDT collaboration only for demonstrated simultaneous editing needs. Add Kafka, a dedicated search cluster, a graph database, and multi-region infrastructure only when measured demands justify them.

Open questions do not prevent a foundation task: hosting budget/region, local machine/GPU specifications, first production identity provider, initial connected accounts, product licensing, and desired distribution channels. The reference names “hygen” and “TencentDB” have documented inferred resolutions.

Personal, team, self-hosted-online and fully offline profiles share product contracts but have different supported capabilities and operating costs. [ADR 0008](architecture-decisions/0008-delegated-authority-and-data-sharing.md) specifies shared-agent authority and output audiences. [AG-UI integration](modules/agent-ui-contract.md) separates interface interoperability from durable execution. No optional reference backend, online license renewal or model subscription is silently required by the offline design.

## 11. Completion and next step

This deliverable completes the initial **research-backed planning task**. Its scope is architecture/design review of the referenced material and relevant implementation samples, not execution or a line-by-line audit of every repository.

The deeper 16 November review corrects phase dependencies and reference assumptions, adds operational/delegation/protocol contracts, and retains the full requested scope. All implementation and adoption gates remain pending. Consult [the review](PLAN-REVIEW.md) for the final decision/concern register.

The approved roadmap contains individually scoped tasks, each with verification and a focused teaching report. [P1.3 is complete](learning-notes/008-browser-sessions.md). P1.4 requires separate authorization.
