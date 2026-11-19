# Zincc

Zincc is building a model-independent platform for persistent AI agents, daily automation, research, document analytics, live voice actions, and code-generated scenes.

**P2.1 adds the internal model gateway.** Java callers can generate text, validate structured answers and stream text through configured OpenAI, Anthropic or Ollama routes. [Configure a model and run the smoke test](docs/modules/model-gateway.md).

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
| [Document intelligence](docs/modules/knowledge-and-analytics.md) | PDF parsing, RAG, numerical analysis, and dashboards |
| [Execution security](docs/modules/security-and-sandboxes.md) | Hosted sandboxes, users' Docker/VMs, credentials, and approvals |
| [Live actions and integrations](docs/modules/live-and-connectors.md) | Voice, connectors, MCP, plugins, and automations |
| [Scene Studio](docs/modules/scene-studio.md) | Prompt/HTML to interactive worlds, visual critique and rendered video |
| [Architecture decisions](docs/architecture-decisions/README.md) | Proposed decisions and alternatives |
| [Status](docs/status.md) | Completed, pending, and blocked work |
| [Learning journal](docs/learning-notes/000-planning.md) | First technical report; future reports use relevant A–L prompts |
| [Agent instructions](AGENTS.md) | Mandatory development and teaching protocol |

The two Maven launcher scripts come from the Apache Maven Wrapper 3.3.4 only-script distribution. Their [license](.mvn/wrapper/LICENSE) and [notice](.mvn/wrapper/NOTICE) accompany them. The product license decision remains open.

The next task awaiting authorization is **P2.2**, conversation persistence and a resumable event stream.
