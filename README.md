# Zincc

In **Agents and runs**, choose a framework, compare agent versions, restore an earlier definition or run a task on your paired computer. A Windows Antigravity session completed a live Gemini Flash task through the workbench. [P9 walkthrough and provider qualification](docs/modules/phase-nine-builder.md)

The local workbench supports parallel agent reviews, local text checks, editable private memory and source-linked recall. Open **Agents and runs** to try them. [P7 walkthrough and evaluation](docs/modules/phase-seven-agents.md).

To try Numes with your own inference key, enable the `numes-free` profile and choose **numes-glm-flash-free** or **numes-ultra-free** in the model picker. [Model setup](docs/modules/model-gateway.md#numes-free-routes)

**Phase 5 adds Connections and routines.** Combine selected Gmail, Slack and Calendar sources into a private briefing, schedule a dashboard refresh, inspect its receipt and pause future runs. Real Gmail first sync shows its saved-item count while paging. Sample accounts work on localhost. Real accounts require provider consent. [Try Phase 5 or configure OAuth](docs/modules/phase-five-integrations.md)

**Phase 4 is implemented.** Upload a private PDF, find cited passages with local embeddings, and turn a ruled table into a filterable dashboard and CSV. [Run and use the document workbench](docs/modules/private-knowledge.md)

**Phase 3 is implemented.** In the signed-in workbench, open **Agents and runs** to create or version an agent, run it, review an exact proposed task, and inspect its provider receipt. The local qualification uses a persistent task sandbox. [Agent setup and limits](docs/modules/phase-three-agents.md)

**Phase 2 is implemented.** The workbench offers model-route presets, private conversations and public search with cited passages. Local-only deployments exclude cloud inference and public search. The evaluation suite and usage report cover the first working components. [Model settings](docs/modules/model-gateway.md) · [Search sources](docs/modules/public-research.md) · [Evaluation and costs](docs/modules/phase-two-evaluation.md)

**P2.2 adds private workspace conversations.** Choose a model route, send follow-ups, and reopen the saved transcript. The workbench catches up from the last event it received. [Run conversations](docs/modules/conversations.md).

Zincc is building a model-independent platform for persistent AI agents, daily automation, research, document analytics, live voice actions, and code-generated scenes.

**P2.1 adds the internal model gateway.** Java callers can request text, validated JSON or streamed text according to each route’s capabilities. The gateway supports OpenAI, Anthropic, Ollama and OpenRouter. An opt-in profile configures two free OpenRouter text routes. [Configure a model and run the smoke test](docs/modules/model-gateway.md).

The browser workbench supports sign-in, workspace selection, membership details, account switching and sign-out. [Build and open the workbench](docs/modules/workbench.md) · [Configure OIDC](docs/modules/browser-sessions.md) · [PostgreSQL setup](docs/modules/workspace-membership.md).

## Run the foundation

Set `JAVA_HOME` to a Temurin 25.0.5+8 JDK. Run these commands in PowerShell from the repository root. The wrapper downloads Maven on first use, and Maven caches build dependencies locally.

```powershell
.\mvnw.cmd verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/zincc-control-plane-0.1.0-SNAPSHOT.jar
```

In a second terminal, run `curl.exe -fsS http://127.0.0.1:8080/actuator/health`. Expect `{"status":"UP"}`. Stop the service with Ctrl+C. On Unix, use `sh mvnw verify` and `"$JAVA_HOME/bin/java" -jar target/zincc-control-plane-0.1.0-SNAPSHOT.jar`. Windows x64 is the verified environment. Apply the Java formatter with the wrapper goal `spring-javaformat:apply`.

## Project documents

| Document | Purpose |
| --- | --- |
| [Final plan review](docs/PLAN-REVIEW.md) | Changes, architectural decisions, concerns and approval boundary |
| [Project plan](docs/PLAN.md) | Product capabilities, priorities, use cases, and delivery strategy |
| [Action scope and connectors](docs/product-scope-and-connectors.md) | Requested flagship workflows, named connector waves, and expanded scope |
| [Engineering learning roadmap](docs/learning-roadmap.md) | System design, advanced RAG, agents, evaluation, inference/KV cache, graphs and fine-tuning labs |
| [Architecture](ARCHITECTURE.md) | System boundaries, data, APIs, execution, and operational design |
| [Deployment and operations](docs/modules/deployment-and-operations.md) | Hosted/hybrid/offline profiles, capacity, failure handling, recovery and cost |
| [Agent UI contract](docs/modules/agent-ui-contract.md) | AG-UI mapping, event durability, state patches and approvals |
| [Platform research](docs/research/platforms.md) | Grok Bot, Dots, Muse, OpenClaw, OpenBot, and Perplexity |
| [Reference review](docs/research/references.md) | Every supplied reference, findings, proposed use, and review limits |
| [Deeper source findings](docs/research/2026-11-16-architecture-follow-up.md) | OpenBot, Timon, Rakazo, OpenMausBot, Kiri and AG-UI evidence/limits |
| [Technology baseline](docs/technology-baseline.md) | Observed releases, proposed versions, and compatibility gates |
| [Implementation roadmap](docs/phases/roadmap.md) | Small tasks, dependencies, acceptance tests, and learning topics |
| [Runtime design](docs/modules/agent-runtime.md) | Harness, routing, parallel agents, memory, and caching |
| [Private document workbench](docs/modules/private-knowledge.md) | Active upload, OCR, retrieval and dashboard scope |
| [Document intelligence](docs/modules/knowledge-and-analytics.md) | PDF parsing, RAG, numerical analysis, and dashboards |
| [Execution security](docs/modules/security-and-sandboxes.md) | Hosted sandboxes, users' Docker/VMs, credentials, and approvals |
| [Live actions and integrations](docs/modules/live-and-connectors.md) | Voice, connectors, MCP, plugins, and automations |
| [Scene Studio](docs/modules/scene-studio.md) | Prompt/HTML to interactive worlds, visual critique and rendered video |
| [Architecture decisions](docs/architecture-decisions/README.md) | Proposed decisions and alternatives |
| [Status](docs/status.md) | Completed, pending, and blocked work |
| [Learning journal](docs/learning-notes/000-planning.md) | First technical report; future reports use relevant A–L prompts |
| [Agent instructions](AGENTS.md) | Mandatory development and teaching protocol |

The two Maven launcher scripts come from the Apache Maven Wrapper 3.3.4 only-script distribution. Their [license](.mvn/wrapper/LICENSE) and [notice](.mvn/wrapper/NOTICE) accompany them. The product license decision remains open.

The workbench includes Gmail, Slack, Google Calendar and customer runners. Pair a local runner to count files in a selected directory or review an offline browser draft. The Docker Cloud lifecycle benchmark passed. Public multi-tenant execution remains gated. [P6 setup](docs/modules/phase-six-runners.md).
