# Supplied reference review and reuse ledger

**Snapshot: 14 November 2026. Actual copied code/skills: none. Installed reference packages: none.** “Adopt” below means proposed design use; “candidate” requires a pinned revision, compatibility test, license/notice check, and security review before implementation.

**Architecture follow-up, 16 November:** [additional pinned source findings and AG-UI review](2026-11-16-architecture-follow-up.md) update the evidence below. The original entries retain their review depth; this does not claim a whole-repository audit. Repeated OpenBot/OpenMausBot links are consolidated.

Review depth is stated per entry. Repository documentation is not a full source audit. Main/master URLs are mutable; record commit SHA and exact copied paths when reuse actually occurs. License notes are observations from a license file or repository metadata, not a conclusion about the whole future product's licensing.

**18 November follow-up.** The [OpenMausBot model picker review](2026-11-18-openmaus-model-picker.md) traces conversation defaults, account discovery and selected-model confirmation. It adds OpenCode, Antigravity and Kimi Code qualification targets to Phase 9.

## 1. CopilotKit/OpenBot — repeated links consolidated

Inspected [README](https://raw.githubusercontent.com/CopilotKit/OpenBot/main/README.md), [docs index](https://raw.githubusercontent.com/CopilotKit/OpenBot/main/docs/README.md), [architecture](https://raw.githubusercontent.com/CopilotKit/OpenBot/main/docs/architecture.md), [approval design](https://raw.githubusercontent.com/CopilotKit/OpenBot/main/docs/approvals.md), and [MIT license](https://raw.githubusercontent.com/CopilotKit/OpenBot/main/LICENSE).

**Use:** computer handoff, activity/approval UX, AG-UI integration patterns, routines, and external-agent contracts. **Change:** Spring control plane, platform-owned action ledger, deterministic semantic tool manifests. Name/button-based action classification alone is too fragile. Do not assume external agents' private tools are governed. **Limit:** a documented source path, server/src/approvals/policy.ts, returned 404; policy implementation was not verified at that path. No fork or dependency selected. Maps to P3, P5, P6, P9, P11.

**16 November correction:** pinned checkout `a28e96ccd87378c3376ccd977b59341f931747c9` contains no server implementation directory. README/deployment configuration describe published backend images and separately licensed CopilotKit Intelligence, including online local-license renewal. MIT applies to the application template; do not extend that observation to all runtime components. Selected adapter code and approval API contract were inspected; server enforcement remains unverified. Zincc does not adopt that backend or license dependency. See the follow-up ledger for exact files.

## 2. OpenBMB/PilotDeck

Resolved the truncated link to [repository](https://github.com/OpenBMB/PilotDeck), [README](https://raw.githubusercontent.com/OpenBMB/PilotDeck/main/README.md), and [official site](https://pilotdeck.openbmb.cn/). Documentation describes workspace-scoped files/skills/memory, inspectable memory, consolidation/rollback, and routing.

**Use:** memory editor, isolated workspaces, reversible memory changes, route-cost visibility. **Change:** evidence-linked memory and bounded offline evaluation. **Disposition:** concept reference; repository metadata indicates AGPL-3.0, so copying/integration waits for the product licensing decision and exact-file review. Performance claims were not replicated. Maps to P4, P7.

## 3. MiniMax-AI/skills

Inspected [catalogue](https://raw.githubusercontent.com/MiniMax-AI/skills/main/README.md), [PDF skill](https://raw.githubusercontent.com/MiniMax-AI/skills/main/skills/minimax-pdf/SKILL.md), and [MIT license](https://raw.githubusercontent.com/MiniMax-AI/skills/main/LICENSE). The catalogue includes design/development and office/media skills; the PDF skill separates creation/fill/reformat workflows with renderable structure.

**Use:** curated artifact skills, structured content contracts, render-and-inspect quality gates. **Change:** remove vendor coupling, review scripts/dependencies, enforce sandbox permissions, add provenance. PDF creation instructions do not solve ingestion/RAG. **Disposition:** selective candidate, no skill installed or executed. Maps to P5, P10.

## 4. Hygen

“hygen GitHub” is inferred as [jondot/hygen](https://github.com/jondot/hygen), distinct from HeyGen. Inspected README and [LICENSE.txt](https://raw.githubusercontent.com/jondot/hygen/master/LICENSE.txt), which is MIT. It provides repository-local templates, prompts, file generation, and insertion.

**Use:** optional internal module/connector scaffolding after patterns stabilize. **Change:** templates owned by Zincc, generated output reviewed, no untrusted runtime templates. **Disposition:** deferred developer tooling, not an agent runtime dependency. Initial guesses LICENSE and LICENSE.t returned 404; actual LICENSE.txt was found and checked. Maps to P1/P5 only if repetition warrants it.

## 5. TencentDB memory

The broad “TencentDB” reference is inferred as [TencentDB-Agent-Memory](https://github.com/TencentCloud/TencentDB-Agent-Memory), not the whole Tencent database product family. Inspected [README](https://raw.githubusercontent.com/TencentCloud/TencentDB-Agent-Memory/main/README.md) and [MIT license](https://raw.githubusercontent.com/TencentCloud/TencentDB-Agent-Memory/main/LICENSE).

**Use:** layered conversation/atomic/scenario/persona memory, compact references that can recover full evidence, and evaluated procedural knowledge. **Change:** tenant scoping, deletion propagation, confidence/conflict handling, user correction, no automatic promotion of preferences into authorization. **Disposition:** concepts first; storage implementation remains Zincc's PostgreSQL design. No token-saving claim accepted without measurement. Maps to P4/P7.

## 6. OpenClaw loop, graph, and context engineering

Inspected [README](https://raw.githubusercontent.com/openclaw/openclaw/main/README.md), [agent loop](https://raw.githubusercontent.com/openclaw/openclaw/main/docs/concepts/agent-loop.md), and [context](https://raw.githubusercontent.com/openclaw/openclaw/main/docs/concepts/context.md).

**Use:** session serialization, writer fencing, lifecycle events, progressive tool context, bounded compaction, and recoverable source pointers. **Change:** durable Java platform contracts and separate authorization. No named “Graph” package was verified. **Disposition:** design inspiration; exact revision/license review required before code reuse. Maps to P3/P7.

## 7. google/elephant-goldfish-model

Inspected [repository](https://github.com/google/elephant-goldfish-model) and [README](https://raw.githubusercontent.com/google/elephant-goldfish-model/main/README.md). Apache-2.0 indicated by repository metadata. It is a software design/review workflow using context-rich design and fresh review perspectives, **not a connector framework**.

**Use:** explicit specs, independent design review, and drift checks; possibly a future development skill. **Change:** integrate with Zincc's authorized task boundaries and ADRs. **Disposition:** methodology only; upstream instructions were not installed or treated as instructions for this session. Maps to development quality and P7 experiments.

## 8. google-research/harness2

Inspected [README](https://raw.githubusercontent.com/google-research/harness2/main/README.md) and [pyproject.toml](https://raw.githubusercontent.com/google-research/harness2/main/pyproject.toml). Research package 0.1.0; metadata indicates Apache-2.0. The inspected runtime constraint is Python >=3.12,<3.14 on Linux.

**Use:** offline propose/probe/compose experiments over prompts, guidance, memory, skills, scripts, and agent composition. **Change:** immutable security policy, holdout evaluations, promotion records, rollback. **Disposition:** research candidate, not the production orchestrator. It changes harness components, not model weights, and does not prove any weak model reaches flagship quality. Maps to P7.4.

## 9. Shubhamsaboo/awesome-llm-apps

Inspected the [main catalogue](https://raw.githubusercontent.com/Shubhamsaboo/awesome-llm-apps/main/README.md), covering starter/advanced agents, multi-agent work, RAG, memory, MCP, voice, generative UI, skills, and always-on work. Repository metadata indicates Apache-2.0. This is a broad example collection, not one deployable architecture.

Selected relevant deeper samples:

| Sample inspected | Useful idea | Production change needed |
| --- | --- | --- |
| [Typed RAG README](https://github.com/Shubhamsaboo/awesome-llm-apps/tree/main/rag_tutorials/agentic_typed_rag_pydanticai) and [agent.py](https://raw.githubusercontent.com/Shubhamsaboo/awesome-llm-apps/main/rag_tutorials/agentic_typed_rag_pydanticai/agent.py) | Typed answers, retrieval gate, source/chunk/quote checks | Durable tenant indexes and claim-level support; retaining one valid citation does not validate every sentence; similarity is not truth probability |
| [Always-on HN briefing](https://raw.githubusercontent.com/Shubhamsaboo/awesome-llm-apps/main/always_on_agents/always_on_hn_briefing_agent/README.md) | Scheduled briefing, ranking, dry-run and delivery separation | Durable triggers, provider grants, duplicate suppression, audit and retry semantics |
| [Dashboard canvas](https://raw.githubusercontent.com/Shubhamsaboo/awesome-llm-apps/main/generative_ui_agents/ai-dashboard-canvas-agent/README.md) | Persistent addressable charts and agent/UI events | Source-linked datasets, authorization, validated chart specs; commit dependency locks despite sample's lockfile policy |
| [Live insurance agent team](https://raw.githubusercontent.com/Shubhamsaboo/awesome-llm-apps/main/voice_ai_agents/insurance_claim_live_agent_team/README.md) | Conversation with concurrent background work and visible notes | Replace in-memory/mock state, add durable actions and policy; do not imply actual claim submission |

**Disposition:** scenario and test inspiration for P2–P10, selected code candidates only after exact-path review. No examples were executed. The whole catalogue was surveyed, but every nested application's code was **not** audited. Further source inspection occurs when a specific component is selected; that limitation remains explicit.

## 10. TencentCloud/CubeSandbox

Inspected [master README](https://raw.githubusercontent.com/TencentCloud/CubeSandbox/master/README.md), [quickstart](https://raw.githubusercontent.com/TencentCloud/CubeSandbox/master/docs/guide/quickstart.md), and [license](https://raw.githubusercontent.com/TencentCloud/CubeSandbox/master/LICENSE). The default branch is master; main initially returned 404. Apache-2.0 includes third-party notices/exceptions that also need review.

**Use:** candidate microVM backend, suspend/restore, warm environments, standardized sandbox API. **Change:** tenant identity, quotas, approval, and environment lifecycle remain Zincc responsibilities. **Disposition:** benchmark 0.8.0 as an optional backend in P6. Linux/KVM requirements and preview features must be qualified. Published latency/memory numbers were not reproduced.

## 11. agent-substrate/substrate

Inspected [README](https://raw.githubusercontent.com/agent-substrate/substrate/main/README.md), [architecture](https://raw.githubusercontent.com/agent-substrate/substrate/main/docs/concepts/architecture.md), and [security](https://raw.githubusercontent.com/agent-substrate/substrate/main/docs/security/security.md). Repository metadata indicates Apache-2.0.

**Use:** actor identity separate from warm execution workers, pause/resume, credential proxy, default-deny egress. **Change:** Zincc owns auth, registry, lineage, and policy. **Disposition:** later cluster adapter; Kubernetes complexity is unnecessary for foundation. The security document identifies incomplete per-actor storage/restore protections and elevated worker privileges. Those are adoption gates, not features to assume complete. Maps to P6/P12.

## 12. google/adk-recipes

Inspected [README](https://raw.githubusercontent.com/google/adk-recipes/main/README.md) and [recipe checklist](https://raw.githubusercontent.com/google/adk-recipes/main/docs/recipe-checklist.md). Apache-2.0 indicated by metadata; recipes distinguish core, contributed, and plugin examples.

**Use:** session/OAuth/guardrail patterns, runnable examples with purpose/ownership metadata, SKILL.md/EVAL.yaml discipline. **Change:** Zincc's Java ownership, provider neutrality, isolated workers, and deployment targets. **Disposition:** selected adapter/skill candidates, not evidence that a recipe is production-ready. Maps to P5/P9.

## 13. ChromeDevTools/chrome-devtools-mcp

Inspected [README](https://raw.githubusercontent.com/ChromeDevTools/chrome-devtools-mcp/main/README.md). It exposes browser inspection/automation, console/network/performance data, screenshots, and related tooling.

**Use:** isolated browser diagnostic capability and development verification. **Change:** pin versions, isolate profiles, enforce egress and tool policy, configure telemetry/CrUX behavior explicitly. Chrome support is not proof all Chromium variants behave identically. **Disposition:** candidate in P6; inspect the exact release license/dependencies before packaging. No browser was controlled in this planning task.

## 14. cloudflare/security-audit-skill

Inspected [README](https://raw.githubusercontent.com/cloudflare/security-audit-skill/main/README.md) and [MIT license](https://raw.githubusercontent.com/cloudflare/security-audit-skill/main/LICENSE). Useful methodology separates coverage, findings, independent validation, and final structured outcomes.

**Use:** evidence-led component security reviews and adversarial evaluation cases. **Change:** require reproducible findings and track confirmed, rejected, and needs-validation outcomes. **Disposition:** methodology/possible future skill; not invoked or installed here. The referenced AI-AND-LLM.md raw path returned 404, so its contents were not verified. Maps to quality gates throughout and P12.

## 15. thedotmack/claude-mem

Inspected [README](https://raw.githubusercontent.com/thedotmack/claude-mem/main/README.md) and [current LICENSE](https://raw.githubusercontent.com/thedotmack/claude-mem/main/LICENSE), which is Apache-2.0 at the inspected path. Do not assume an older license based on project history.

**Use:** compact observation index, on-demand detail retrieval, inspectable persistent memory. **Change:** model-neutral storage, privacy defaults, source deletion, explicit cloud/telemetry behavior. **Disposition:** concept inspiration, not importing its observer or authentication stack. Maps to P7.3.

## 16. heygen-com/hyperframes

Inspected [README](https://raw.githubusercontent.com/heygen-com/hyperframes/main/README.md); on 15 November also checked the [Apache-2.0 license text](https://raw.githubusercontent.com/heygen-com/hyperframes/main/LICENSE), [frame adapter contract](https://raw.githubusercontent.com/heygen-com/hyperframes/main/docs/concepts/frame-adapters.mdx) and [deterministic timing guidance](https://raw.githubusercontent.com/heygen-com/hyperframes/main/docs/concepts/determinism.mdx). It renders seekable HTML/CSS/media scenes into video using a Node/FFmpeg pipeline; some offerings are separate paid capabilities. The custom frame adapter API is experimental v0; exact-version conformance still needs testing.

**Use:** editable scene source → preview → reproducible rendered video. **Change:** sandbox untrusted HTML/assets, retain provenance, respect media/font rights, distinguish core from paid features. **Disposition:** preferred candidate for the P10.3e Scene Studio video adapter, subject to qualification; not a video model or an installed dependency. No skills or source copied. General raw WebGPU compatibility has not been proved.

## 17. debpalash/VoiceStudio

Inspected [README](https://raw.githubusercontent.com/debpalash/VoiceStudio/main/README.md) and [feature catalogue](https://raw.githubusercontent.com/debpalash/VoiceStudio/main/docs/feature-catalog.md). Documentation covers local voice production, engine management, transcription/dubbing, batch work, and local interfaces. Metadata indicates AGPL-3.0 plus commercial licensing; engine/model licenses are separate.

**Use:** local engine capability/health UI and optional voice-production integration. **Change:** consent controls, GPU/resource placement, per-engine licensing, no claim that planned cloud features are shipped. **Disposition:** concept or separately connected user service pending license review, not embedded core code. It does not replace a realtime conversation transport. Maps to P8/P10.

## 18. Noniv/abyssal-garden — newly supplied ocean example

Resolved the user's social-post description through the [author's post](https://www.reddit.com/r/ClaudeAI/comments/1xw376f/i_built_a_swimmable_underwater_world_in_one_97_kb/) to [the repository](https://github.com/Noniv/abyssal-garden) and [demo](https://noniv.github.io/abyssal-garden/). Research date: 15 November 2026. Reviewed README, original prompt, MIT license, package/build files, development benchmark and quality selection code, plus selected initialization/device-loss sections of main.js. The generated index.html was fetched but its minified script was not fully inspected. No browser execution or full shader audit occurred; the source-directory/API listing endpoints were unavailable in the research tool, so readable raw paths supplied the selected files.

- **Ideas adopted into the proposal:** procedural scenes, small distributable bundles, visual review isolated from code reasoning, fixed camera evidence, timing-based qualification and quality tiers.
- **Code analyzed only:** [build.mjs](https://raw.githubusercontent.com/Noniv/abyssal-garden/main/source/build.mjs), [dev.js](https://raw.githubusercontent.com/Noniv/abyssal-garden/main/source/src/dev.js), [quality.js](https://raw.githubusercontent.com/Noniv/abyssal-garden/main/source/src/quality.js), and selected [main.js](https://raw.githubusercontent.com/Noniv/abyssal-garden/main/source/src/main.js) sections. Device-loss handling and performance instrumentation inform future acceptance cases.
- **Possible selective reuse:** a future licensed example/template or specific utility after revision pinning, tests and notice review. The [MIT text](https://raw.githubusercontent.com/Noniv/abyssal-garden/main/LICENSE) names Maksymilian Dendura. Nothing has been copied or installed.
- **Not adopted as requirements:** the particular model, hardware target, engine prohibition, unlimited refinement, exact tiny bundle size or reported creation time. These do not establish a general product guarantee. README currently says 96 KB while the post/repository description says 97 KB; neither was measured here.

Maps to [Scene Studio](../modules/scene-studio.md), P10.3a–P10.3h and ADR 0007. The source's real-time fly-through is useful test inspiration; it is not already a deterministic video export interface.

## Additional references and precise reuse accounting

**Agent-Reach, 18 November:** [Panniantong/Agent-Reach](https://github.com/Panniantong/Agent-Reach), pinned `c70ae6da5d39bc90e09ad2ae0575ccc89f31e284`. [Scoped source review](2026-11-18-agent-reach-review.md) covers normalized results, public evidence labeling, selected fallback/session paths and tests. MIT root; no code copied or executed. Optional research-source adapters and connection-health/evidence UX are recommendations, not installed capabilities.

**3D Fluffy, 18 November:** [3dfluffy.app](https://3dfluffy.app/) recorded as a user-supplied mascot inspiration for final P11 polish in the [design note](../design/workbench-concept-01.md). Live page retrieval failed; visual/asset/license inspection remains pending until the design phase. No assets downloaded or reused.

**LoopForge, 16 November:** [Tencent/LoopForge](https://github.com/Tencent/LoopForge), revision `3e02e408b6ebdb4eca1435ca698dbaf75ac78f04`. [Source review and feature recommendations](2026-11-16-loopforge-review.md): workflow graphs, typed gates, rework, artifact restoration and lease/attempt semantics. MIT root plus third-party terms; no code or skills copied/executed. Recommendations extend reusable workflow and review UX rather than replacing Spring/Temporal.

**DuskBot, 16 November:** [DuskBotAI/duskbot](https://github.com/DuskBotAI/duskbot), revision `058c67f88c390783bdd1054128b31419209c8167`. [Source and provider-account review](2026-11-16-duskbot-review.md): selected Codex, ChatGPT plan, Claude Code and Grok Build adapters plus official-provider verification. Source-available PolyForm Noncommercial with original OpenMausBot attribution; no direct reuse. Live website retrieval failed; repository landing-page source was inspected. The user-requested [connected AI engine proposal](../modules/connected-ai-engines.md) uses official interfaces as its intended implementation basis.

**Nod reference added 16 November:** [n8n-io/nod](https://github.com/n8n-io/nod), pinned `866158b15302256b718e89dcaf5afdb5751adddb`. [Scoped source review and proposed refinements](2026-11-16-nod-review.md) covers proactivity, memory, authority, credential-operation recovery and evaluation paths. No code copied or executed. Recommendations are isolated from the main plan while the user reviews it. Root Apache-2.0 metadata has file/dependency exceptions identified in NOTICE, including GPL-licensed local speech components; qualification is required before reuse.

**AG-UI source reference added 16 November:** [ag-ui-protocol/ag-ui](https://github.com/ag-ui-protocol/ag-ui), pinned `2330368a2a74573902858867072406638ba3073d`. MIT LICENSE, selected TypeScript event/state/completion code and test excerpts inspected. Proposed selective SDK use for event interoperability, with [Zincc-owned protocol mapping](../modules/agent-ui-contract.md). No code copied or SDK installed; no audit of every integration. Windows path-length errors left some .NET baseline files unmaterialized; inspected TypeScript files were available. Tracking query parameters in the user's link were unnecessary for source identification.

**16 November additions:** Kiri, Superbot, Timon, Rakazo and OpenMausBot have a [pinned source review and reuse ledger](2026-11-16-agent-repositories.md). Superbot's 16 source/build files received a complete static first pass; larger repositories received explicitly scoped inspections. No product code was copied. Source downloads outside Zincc were for inspection only.

Grok Bot, Dots, Muse, Perplexity, Apple, Lovable, and Mercor are covered in [platform research](platforms.md). Jev and the three framework choices are covered in [runtime design](../modules/agent-runtime.md). Spring/Temporal/parser/local-provider releases are in [the baseline](../technology-baseline.md).

When adoption begins, add an entry with: upstream URL, commit/tag, reviewed files, SPDX license/notice evidence, copied paths, local destination, modifications, dependency graph, tests, maintainer, and rollback/removal plan. A concept-inspired implementation should say so; it must not be reported as copied upstream code. No entry in this document currently represents completed code reuse.
