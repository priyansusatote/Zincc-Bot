# Platform research and feature mapping

**Research cutoff: 14 November 2026.** Public official documentation and selected public source files were inspected. These are documented capabilities, not independent performance tests or access to proprietary internals. Availability can vary by account, region, device, and rollout. No claim of exhaustive knowledge of undocumented features is intended.

## Resolved identities

“GrokBot” is interpreted as xAI **Grok Bot**, “gpt dots” as **ChatGPT Dots**, and “Meta Muse bot” as Meta **Muse**, based on the official product pages below. “OpenBot” means **CopilotKit/OpenBot**, not similarly named robotics projects. The truncated PilotD reference resolves to **OpenBMB/PilotDeck** and its supplied official website.

## Grok Bot

The [Grok Bot introduction](https://x.ai/news/introducing-grok-bot) and [1.0 announcement](https://x.ai/news/grok-bot-1-0) describe persistent cloud-computer teammates, proactive follow-ups, shareable bots, connected services, and artifact creation. The 1.0 release adds stronger team workflows, permission-aware sharing, and activity/approval visibility. Its meeting participation is described through chat; that is not evidence of unrestricted spoken participation in every video service.

The [overview](https://docs.x.ai/grok-bot/overview) covers chat/voice interaction, multiple bots, tools, and integrations. **Zincc mapping:** persistent agent identities, shared team agents, meaningful background updates, receipts, and a visible work computer. Proposed improvement: independently test model portability, per-agent resource scopes, and recovery rather than depend on one provider ecosystem. Do not infer Grok's private scheduler or security implementation from its UI.

## ChatGPT Dots

[Official Dots documentation](https://learn.chatgpt.com/docs/dots.md), [getting started](https://help.openai.com/en/articles/20001530-getting-started-with-your-dot), and the [introduction](https://openai.com/index/introducing-dots/) describe an ongoing assistant with a cloud computer/browser, background work, memory/preferences, voice, and connected tools across supported surfaces. Dots can coordinate parallel work and sleep/wake around ongoing responsibilities.

**Zincc mapping:** continuity across channels, an inbox of completed work and needed decisions, project memory, resumable responsibilities, and accessible voice. Keep channel identity and disclosure permissions explicit: remembering private context does not authorize posting it in a shared channel. Product functionality is a reference, not an available backend library or proof the user's subscription supplies an API.

## Meta Muse

Meta's [announcement](https://about.fb.com/news/2026/09/introducing-muse-personal-ai-agent/) and [Muse product page](https://ai.meta.com/muse/) position Muse as a proactive personal agent spanning goals, documents/media, connected apps, browser actions, and ongoing context. Official material describes dedicated execution environments, separated credentials, outbound checks, and user decisions for critical actions.

**Zincc mapping:** approachable goals, anticipatory assistance within grants, source-linked preferences, transparent action status, and isolated execution. These are vendor-described controls, not independently verified security guarantees. The product page is dynamic and supplied limited readable body text; the official announcement and indexed official material were the usable evidence. Private internals remain unknown.

## OpenClaw

The [README](https://raw.githubusercontent.com/openclaw/openclaw/main/README.md), [agent-loop document](https://raw.githubusercontent.com/openclaw/openclaw/main/docs/concepts/agent-loop.md), and [context document](https://raw.githubusercontent.com/openclaw/openclaw/main/docs/concepts/context.md) provide more implementation-level evidence than proprietary product pages. Useful concepts include gateway-managed sessions, bounded agent loops, serialized session writes, stale-writer protection, lifecycle events, context budgeting, skills, and compaction.

**Zincc mapping:** explicit run state, safe concurrency, versioned context assembly, and recoverable evidence pointers. A “graph” reference is interpreted as workflow/state-graph concepts; no separate official product named OpenClaw Graph was established. Do not copy its gateway wholesale into the Spring backend or assume all providers share one context limit.

## CopilotKit OpenBot

16 November source follow-up: its public MIT application template is distinct from published backend images and separately licensed CopilotKit Intelligence. The inspected checkout did not expose server enforcement source, and local license renewal needs connectivity according to its README. See [precise revision and coverage](2026-11-16-architecture-follow-up.md). It is not a fully inspected air-gapped backend candidate for Zincc.

The [README](https://github.com/CopilotKit/OpenBot), [architecture](https://raw.githubusercontent.com/CopilotKit/OpenBot/main/docs/architecture.md), and [approvals](https://raw.githubusercontent.com/CopilotKit/OpenBot/main/docs/approvals.md) describe agents, a visible computer/browser, schedules, connections, policies, and external-agent integration. Its stack includes a React/Vite client, Hono API, PostgreSQL, CopilotKit Intelligence, and AG-UI integration; it is not a ready-made Spring Boot backend.

**Zincc mapping:** inspectable activity, approval cards, user takeover, connected agents, and computer artifacts. Use selected interaction/protocol ideas; replace backend ownership with Zincc's contracts. External dependency and deployment requirements must be inspected before calling any fork self-contained. Brought-in agents with private tools may bypass central governance; Zincc must identify that boundary clearly.

## Perplexity through the research cutoff

The latest visible official [changelog](https://www.perplexity.ai/changelog) entry was **6 November 2026**. The following covers major relevant feature families, not every historical experiment, subscription entitlement, or device-specific variation.

| Official source | Documented direction | Zincc application |
| --- | --- | --- |
| [Search API](https://docs.perplexity.ai/docs/search/quickstart) | Ranked web results, filters, multi-query retrieval, extracted content | Pluggable search providers; search and answer generation are separate capabilities |
| [Computer overview](https://www.perplexity.ai/en-GB/hub/academy/what-is-perplexity-computer) | Multi-step work across models, tools, and deliverables | Modality-aware routing and artifact-producing workflows |
| [6 Nov: Teammates, local Mac, voice](https://www.perplexity.ai/changelog/teammates-fully-local-computer-on-mac-and-voice-mode) | Ongoing teammates; local/hybrid operation; voice actions; Agent Plugins; shared project controls; background compaction; contextual embeddings and typed decisions | Persistent responsibilities, local policy, bundled capabilities, versioned context compaction, retrieval evaluation |
| [19 Oct: Ultrafast and Decisions](https://www.perplexity.ai/changelog/ultrafast-computer-models-decisions-api-and-connector-controls) | Faster routes, typed classification/scoring, role-based connector controls, local folders, multimodal retrieval, sandbox work | Cost-aware decisions, account/resource permissions, isolated compute; no assumed benchmark advantage |
| [5 Oct: Automations and visualizations](https://www.perplexity.ai/changelog/automations-inline-visualizations-and-gpt-6-1-sol) | Time/event automations, interactive visualizations, video workflows, local Windows improvements, browser tooling, fast search, versioned API profiles | Durable triggers, inline evidence-backed charts, media workers, runner availability, versioned agent definitions |
| [21 Sep: Effort and marketplace](https://www.perplexity.ai/changelog/effort-mode-gpt-6-astra-and-skills-marketplace) | Effort controls, skills marketplace, hybrid compute, side conversations | Fast/Balanced/Deep/Local presets, curated skills, separate task discussions |
| [4 Aug: Shared workspaces](https://www.perplexity.ai/changelog/shared-workspaces-personal-computer-for-windows-and-model-council) | Shared project files/memory, Windows personal compute, model council, voice improvements | Shared project permissions and optional independent reviews; consensus is not proof |
| [13 July: Brain and publishing](https://www.perplexity.ai/changelog/brain-faster-computer-models-website-publishing) | Editable source-linked memory, task routing, publication controls, model analytics | Memory editor, route visibility, separate publication approval, usage accounting |

Search, Deep Research, Computer, Teammates, Comet/browser assistance, and developer APIs form complementary surfaces. Zincc should connect these user jobs through one identity and evidence model. Do not reproduce licensed financial datasets or assume access to proprietary search indexes. Ingestion/search providers need their own terms, costs, and credentials.

## UI and live interaction references

[Apple's Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) inform hierarchy, consistency, feedback, and inclusion. [Lovable's preview toolbar](https://docs.lovable.dev/features/preview-toolbar) supports pointing at elements, inline text changes, annotations, and comments. Zincc can use similar direct manipulation for artifacts with revision history and permissions.

[Mercor's expert experience](https://www.mercor.com/experts/) is a reference for conversational interviewing interaction. Its private technical architecture was not established. Zincc's live action design is independently specified in [the live module](../modules/live-and-connectors.md).

No screenshots or proprietary UI source were copied. Visual polish is a later phase; accessibility and honest task states are foundational.

## Feature coverage and proposed differentiation

| Family | Zincc commitment | Delivery gate |
| --- | --- | --- |
| Cited research | Web + private sources with freshness and provenance | Retrieval and citation evaluation |
| Always-on work | Persistent identities, durable triggers, sleep/wake | Crash/recovery and duplicate-action tests |
| Live action | Voice and typed input share one tool gateway | Latency, interruption, ambiguous-action tests |
| Multiple agents/models | Bounded specialization with budgets | Better measured task outcome than baseline |
| Local ownership | Local-only and hybrid modes with visible data flow | Egress and capability qualification |
| Document analytics | Source-linked datasets, formulas, dashboards | Table/numerical correctness corpus |
| Plugins | Versioned capabilities, scoped credentials, rollback | Isolation and permission-change tests |
| Memory | Inspectable, correctable, source-linked | Revocation/deletion and contradiction tests |
| Premium UX | Calm workbench and editable artifacts | Real workflow usability and accessibility |

These are proposed product goals. Zincc is not yet superior to, feature-complete with, or deployed against any reference platform.
