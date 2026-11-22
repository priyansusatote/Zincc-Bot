# Zincc status

Updated: **22 November 2026**.

## Current authorized task

The user authorized the remaining Phase 2 batch on 22 November. Scope covers P2.3 model selection and local privacy, P2.4 bounded cited public-web research, and P2.5 the frozen evaluation and usage reconciliation baseline. Acceptance follows the three entries in the [roadmap](phases/roadmap.md). P3 requires separate authorization.

**P2.2 implemented.** Private conversations persist in PostgreSQL and resume in the workbench from a cursor or snapshot. [API and limits](modules/conversations.md) · [Learning report](learning-notes/012-conversation-replay.md).

**P2.1 gateway implemented.** Protocol fixtures cover OpenAI, Anthropic, Ollama and OpenRouter. The live one-word greeting passed through Zincc using the free NVIDIA Nemotron 3.5 Lightning route on OpenRouter. [Configuration and smoke test](modules/model-gateway.md) · [Gateway learning note](learning-notes/010-model-gateway.md) · [OpenRouter learning note](learning-notes/011-openrouter.md). The [Firecrawl and TurboQuant review](research/2026-11-19-firecrawl-turboquant.md) adds evidence freshness and coverage to P2.4 and a controlled KV-cache experiment to L-INFER-01.

**P1.4 completed on 18 November 2026.** The React workbench is available in the optional same-origin JAR. A frozen install, generated-contract check, type check, lint, production bundle, five Edge browser tests and eleven Java tests passed. The packaged service loaded in Edge. Browser interaction tests used fixtures; backend authorization tests used PostgreSQL and a local OIDC issuer. [Workbench and preview](modules/workbench.md) · [Learning note](learning-notes/009-workbench.md).

## Prior planning and reference work

**18 November reference review:** completed the [Agent-Reach source review](research/2026-11-18-agent-reach-review.md), with optional feature recommendations isolated in the addendum. Recorded 3D Fluffy for final P11 mascot/visual polish; its page was not retrievable. The user is still reviewing the plan. No build, installation or mascot production is authorized by these references.

**Latest reference/scope clarification, 16 November:** completed scoped [LoopForge](research/2026-11-16-loopforge-review.md) and [DuskBot](research/2026-11-16-duskbot-review.md) research, including new-feature recommendations. Clarified that users define their own agents; learning-note examples are illustrative. Added the expressly requested [existing AI plans/engines proposal](modules/connected-ai-engines.md), ADR 0009 and P9.5–P9.9. All changes are documentation only; the user is still reviewing and has not authorized building.

**Subsequent authorized reference check, 16 November:** inspect n8n-io/nod while the user reviews the finalized plan. Completed a [pinned, scoped source review](research/2026-11-16-nod-review.md). Proposed refinements are isolated in that addendum; the main plan and application code were not changed. Build approval remains pending.

**Latest authorized scope, 16 November:** complete the deeper product/architecture review and edit documentation only, including AG-UI and the repeated OpenMausBot reference. **Review complete; plan ready for user approval. Application implementation remains unauthorized.** Start with [the final review](PLAN-REVIEW.md).

Research the supplied references and produce a detailed project plan before application development. Audience: individuals, teams, and self-hosted/local users (“all”). Main backend: Spring Boot. Product name: Zincc; “Neurex” in the supplied teaching template is treated as the same project.

Current discussion clarification: confirm actual action-taking, multiple persistent agents, concrete connector targets and runtime roles. The user requests previously deferred features as part of the full flagship scope. This updates the proposal; it is not implementation approval.

Latest clarification: make system design and advanced AI learning coverage explicit, including inference/KV caching, graphs/GraphRAG, fine-tuning, orchestration, HITL and evaluations. The plan remains unapproved for application implementation.

Latest feature addition: research the supplied procedural ocean example and add prompt/HTML-generated scenes plus video export to the proposed scope. This is the Scene Studio planning revision, not an application implementation task.

## Completed

- Agent-Reach: pinned source inspection, license/coverage ledger and recommendations for broader research access, honest evidence coverage and connector diagnostics. No upstream code copied or run. 3D Fluffy recorded as a deferred design reference, not a visually verified or reused asset.

- LoopForge/DuskBot reviews with pinned commits, scoped file coverage, reuse categories and limitations. Verified relevant official-provider integration documentation. No upstream code copied, installed or run; no provider login or real inference test. DuskBot live website was unavailable to the web tool, so its repository website source supplied the limited site review.
- Custom-agent scope clarified in the initial learning note and product scope. Existing-plan/engine support added as a proposal alongside API/local routes; current preview and hosted-edition limitations recorded. Other newly suggested workflow/import/readiness refinements are recommendations, not completed features.

- Nod research: architecture-sensitive source paths, source/license caveats and reuse categories recorded. No upstream code copied, installed or executed. Prepared-work, memory/style and credential-recovery refinements await review; no new implementation authorization is inferred.

- Deeper review of product features, personal/team/local profiles, architecture, stack, scale, security, AI/retrieval/media, operations and delivery dependencies. Added operations and AG-UI contracts, ADR 0008, a final review/concern register and A–L planning note.
- Corrected early isolation, agent-definition and connector prerequisites; brought evaluation/privacy/restore checks into introducing tasks. Retained full requested scope and task-by-task approval.
- Additional pinned OpenBot/AG-UI source inspection and selected Timon/Rakazo/OpenMausBot/Kiri follow-up; rechecked sandbox/harness/MCP/Temporal sources. [Evidence and limitations](research/2026-11-16-architecture-follow-up.md).
- Selective 16 November version refresh: Maven 3.10.1, React 19.3.1, Vite 8.3.8 and pnpm 12.21.0. Unrefreshed entries remain dated observations, not claims of latest patches.

- Reviewed four user-supplied UI screenshots and recorded attachment-specific patterns in the workbench design note. Refined the proposed personal view to agent roster plus conversation with a contextual third pane; no new mockup or application code generated for this clarification.

- Additional reference review: pinned five public source snapshots outside the product, read all 16 Superbot source/build files and selected paths in the larger repositories. [Coverage and findings](research/2026-11-16-agent-repositories.md). No upstream code executed or incorporated.
- Windows Paint journey and enforced takeover clarified in P13.2; generated [workbench concept](design/workbench-concept-01.md). Plan remains unapproved; no product runtime exists.

- P0.1 planning artifacts: product plan, system architecture, four module specifications, observed version baseline, reference/platform research, scoped roadmap, six proposed ADRs, development rules, and initial learning note.
- Official-source research and selected relevant source/document inspection. All supplied reference names/links have an entry; repeated OpenBot links are consolidated.
- Distinction between proposed ideas, direct reuse, vendor claims, and independently tested behavior. No upstream code/skills copied or executed.
- Planning clarification: action/workflow acceptance map, named connector waves, full-scope expansion epics P13–P16, and updated final polish/qualification order. See [scope and connectors](product-scope-and-connectors.md) and [learning note](learning-notes/001-scope-clarification.md).
- Learning clarification: [coverage matrix and eight lab proposals](learning-roadmap.md), component teaching rules and qualification notes. See [revision learning note](learning-notes/002-learning-coverage.md).
- Scene Studio clarification: resolved the original ocean project, reviewed selected source and licenses, added [the feature specification](modules/scene-studio.md), proposed ADR 0007, P10.3a–P10.3h and learning coverage. See [planning learning note](learning-notes/003-scene-studio-planning.md). No upstream code or skill copied or run.

## Verification

**P2.2, 21 November.** The workbench-profile JAR passed Maven verification. Of 33 Java tests, 32 passed and the opt-in live-provider test was skipped. All seven Edge tests passed against the production frontend bundle with fixture HTTP responses. The backend HTTP tests used PostgreSQL, a local OIDC issuer and a loopback model server for streaming and text-only routes. The frontend contract check, type check, lint and build passed.

**P2.1 OpenRouter follow-up, 19 November.** The opt-in Lightning greeting passed. The Gemma greeting failed twice, with HTTP 429 observed in a separate request to that route. The workbench-profile regression suite passed 30 tests and skipped the opt-in smoke test.

**P2.1, 19 November.** Maven verified the workbench-profile JAR and formatting. Of 29 Java tests, 28 passed and the opt-in live-model smoke test was skipped. Seventeen passing tests cover route configuration, gateways and HTTP protocol fixtures. Eleven cover the previous foundation, identity and workspace components. The default local Ollama port refused connections. All 268 local Markdown links resolved.

**18 November Agent-Reach/design-reference check:** 43 Markdown files and 200 local links checked; no broken targets, unbalanced fences, trailing whitespace or missing A–L learning sections. The existing concept PNG remains the only non-Markdown project file. No application/upstream tests or live source integrations ran.

**LoopForge/DuskBot and connected-engine proposal check, 16 November:** 42 Markdown files and 197 local links checked; zero broken local targets, unbalanced fences, trailing-whitespace issues or missing A–L learning sections. The only non-Markdown project file remains the existing concept PNG. No application, upstream, provider-account or operating-system integration tests ran.

**Nod addendum check, 16 November:** 38 Markdown files and 171 local links checked; no broken local targets, unbalanced code fences or trailing whitespace. The sole non-Markdown project file is still the existing concept PNG. This check did not execute upstream or application tests.

**Final deeper-review check, 16 November:** **37 Markdown files and 169 local links checked; zero broken local targets, unbalanced code fences, duplicate subsection headings, trailing-whitespace issues or missing A–L learning sections.** A direct filesystem check includes untracked documents. The sole non-Markdown project file remains the existing concept PNG; no application source or dependency files were added. No runtime, upstream, integration, load, security or compatibility tests ran. AG-UI's partial Windows checkout limitation is recorded in the source ledger and does not affect the inspected TypeScript files.

The earlier 16 November addition passed validation across **31 Markdown files and 112 local links**. These are historical counts; the deeper review adds documents and receives a fresh check below. Static review is not execution evidence; upstream test excerpts were read but not run.

The Scene Studio revision passed validation across **28 Markdown files and 103 local links**: no broken local targets, unbalanced fences, duplicate subsection headings, trailing-whitespace issues or missing A–L learning sections. Zero non-Markdown application/experiment files exist. Earlier notes retain their historical validation counts. No runtime, integration, performance, security, model-quality, rendering or training tests have run. Direct document checks include untracked files; git diff --check alone does not inspect those files.

## Pending

- P9.5–P9.9 connected-engine tasks: proposed, individually unauthorized; provider/deployment eligibility, stable protocol/version qualification and live-account verification remain gates. LoopForge and optional DuskBot feature recommendations await user review.

- P2.3–P12.5 and expansion epics P13–P16 remain pending. Execution order is P1–P10, then P13–P16, final P11 polish and full P12 qualification.
- Exact dependency locks and compatibility tests at the component's introduction.
- Pinned source/license/security review for any actual upstream code reuse.
- Scene Studio P10.3a–P10.3h: pending prerequisites and individual authorization, including GPU sandbox and render-adapter qualification. The ocean demo's size, performance and reported creation time have not been reproduced.
- L-RAG-01, L-GRAPH-01, L-INFER-01, L-TUNE-01, L-PREF-01, L-MAS-01, L-DIST-01 and L-SEC-01: planned hands-on labs; pending prerequisites and individual authorization. No lab result or production benefit is claimed.

## Decisions still open

Hosting budget and region; identity provider; initial production accounts/API access; local CPU/GPU/OS capabilities; object-storage implementation; product license; distribution channels. These affect later implementation but do not block completion of this plan.

## Research limitations and follow-ups

- Proprietary product internals were not available. Public feature documentation was reviewed, not reverse-engineered or benchmarked.
- Every supplied repository was considered, but this is not a line-by-line audit of all repositories. The large awesome-llm-apps collection received a catalogue review plus selected deeper samples.
- OpenBot's documented approval source path was absent; the pinned checkout now confirms backend images rather than a server source tree, with separately licensed Intelligence. Backend enforcement remains unverified. Cloudflare's linked AI-AND-LLM.md path remained unavailable in the earlier review.
- Muse's dynamic product page exposed limited readable text; official announcement/indexed official material supplied the usable evidence.
- Hygen and TencentDB were inferred from abbreviated names; their exact resolutions are documented. PilotDeck's truncated link was resolved.
- License metadata is provisional until an exact revision and all relevant files/dependencies are checked. PilotDeck/VoiceStudio reuse needs particular attention before any incorporation.

## Stop point

The next task awaiting authorization is P2.3, local-only routing and rule-based model selection.

Hardware, hosting budget/region, identity provider, license and API access remain task-specific decisions documented in the final review.
