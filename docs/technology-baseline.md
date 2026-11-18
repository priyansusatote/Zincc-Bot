# Technology baseline and adoption gates

**P1.1 qualification on 18 November 2026.** Tested on Windows x64 with Eclipse Temurin HotSpot build `25.0.5+8-LTS`, Maven 3.10.1 (wrapper 3.3.4) and Spring Boot 4.1.2 directly on the host. The wrapper pins the Maven archive SHA-256. The build also pins Enforcer 3.6.2, Spring Java Format 0.0.47 and ArchUnit 1.4.2. [Resolved dependency tree](phases/p1.1-dependencies.txt) · [tests and limitations](learning-notes/006-spring-foundation.md). Regenerate the tree with the wrapper goal `dependency:tree -DoutputFile=target/dependency-tree.txt`.

P1.2 qualified PostgreSQL 18.7 on Windows x64, pgJDBC 42.7.14, Flyway 12.4.0 and HikariCP 7.0.2. Tests launch the native server with Zonky 2.2.2 and its 18.7.0 binaries BOM. [Dependency snapshot](phases/p1.2-dependencies.txt) · [PostgreSQL release policy](https://www.postgresql.org/support/versioning/) · [Flyway PostgreSQL module](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database).

Other components retain their proposed baselines from 14 and 16 November. Recheck security patches and compatibility when each component is introduced.

P1.3 qualified the Spring Boot 4.1.2 OAuth2 client starter with its managed Spring Security 7.1.2 and Nimbus JOSE JWT 10.9.1 on the existing Windows/JDK/PostgreSQL baseline. [Resolved dependencies](phases/p1.3-dependencies.txt) · [Spring Security OIDC login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html). The local protocol fixture covers the relying-party flow. Live-provider qualification belongs to deployment.

16 November refresh: official sources reconfirm Boot 4.1.2, Spring AI 2.0.2/Boot 4.0–4.1 compatibility, PostgreSQL 18.7, TypeScript 7.0.2, Tailwind 4.3.5, ADK 2.14.1, LangGraph 1.2.22 and Docling 2.150.1. Update Maven to 3.10.1, React to 19.3.1, Vite to 8.3.8 and pnpm to 12.21.0 below. Other entries retain their original observation date; they are not claimed freshly verified latest patches. Node's official schedule reconfirmed the 26 LTS line, but the exact latest patch endpoint was unavailable; retain 26.13.0 only as the previously observed candidate and recheck before adoption.

## Core stack

P1.4 qualified Node 26.13.1 LTS, pnpm 12.21.0, React 19.3.1, Vite 8.3.8 and TypeScript 7.0.2 for the browser bundle. OpenAPI generation uses openapi-typescript 7.13.0 with its supported TypeScript 5.9.3 peer in a separate tooling workspace. The application compiler remains on 7.0.2. Native CSS handles layout, light/dark tokens and focus styles in this shell. [Lockfile](../frontend/pnpm-lock.yaml) · [Run and verify](modules/workbench.md).

| Component | Proposed baseline | Evidence and reason |
| --- | --- | --- |
| Java | Eclipse Temurin 25.0.5+8, Java 25 LTS line | [Adoptium release news](https://adoptium.net/news?tag=release-notes) and [support policy](https://adoptium.net/support); choose LTS over Java 27 feature line |
| Spring Boot | 4.1.2 | [Official project](https://spring.io/projects/spring-boot/); stable baseline observed; 4.2 RC is not selected |
| Spring AI | 2.0.2, introduced with model gateway | [Getting started](https://docs.spring.io/spring-ai/reference/getting-started.html) lists stable 2.0.2 and Boot 4.0/4.1 compatibility; 2.1 RC features must not be assumed present |
| Maven | 3.10.1, wrapper pinned at implementation | [Official downloads](https://maven.apache.org/download.cgi), rechecked 16 Nov; Maven 4 RC excluded |
| PostgreSQL | 18.7 | [Official version policy](https://www.postgresql.org/support/versioning/), released 12 Nov; PostgreSQL 19 RC2 excluded |
| pgvector | 0.8.7, introduced with private retrieval | [Project changelog](https://raw.githubusercontent.com/pgvector/pgvector/master/CHANGELOG.md); verify extension build/image against chosen Postgres |
| Temporal Java SDK | 1.40.0 | [Release](https://github.com/temporalio/sdk-java/releases/tag/v1.40.0); add with durable runs |
| Temporal server | 1.32.2 for self-hosted qualification | [Release](https://github.com/temporalio/temporal/releases/tag/v1.32.2); managed service is an alternative, not a forced dependency |
| Valkey | 9.1.3, optional later | [Release](https://github.com/valkey-io/valkey/releases/tag/9.1.3); shared cache/rate limiting when justified, not correctness authority |
| Node.js | 26.13.0 LTS observed baseline | [Official release](https://nodejs.org/en/blog/release/v26.13.0); recheck later LTS patches when frontend starts |
| React | 19.3.1 | [Publisher package metadata](https://registry.npmjs.org/react/latest), rechecked 16 Nov; match react-dom at adoption |
| Vite | 8.3.8 | [Official release](https://github.com/vitejs/vite/releases/tag/v8.3.8), rechecked 16 Nov; [release policy](https://vite.dev/releases) |
| TypeScript | 7.0.2 | [Package metadata](https://registry.npmjs.org/typescript/latest) and [7.0 announcement](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/); compiler/tooling compatibility is a foundation check |
| Tailwind CSS | 4.3.5 | [Package metadata](https://registry.npmjs.org/tailwindcss/latest); semantic design tokens rather than scattered one-off styles |
| pnpm | 12.21.0 | [Publisher package metadata](https://registry.npmjs.org/pnpm/latest), rechecked 16 Nov; commit lockfile and package-manager version |

Spring's BOM manages Spring Framework, Security, database drivers, and supported infrastructure libraries. Do not independently upgrade transitive versions by guesswork. Use Flyway migrations, Bean Validation, JUnit, and appropriate database integration tooling from a verified compatible set. Select exact optional plugin versions in P1.1 after resolving the actual dependency graph.

Spring AI is an adapter convenience, not the platform domain model. Provider features unsupported by its stable release can use a dedicated adapter with contract tests rather than forcing a preview upgrade.

## Python and AI workers

| Component | Observed / proposed version | Adoption note |
| --- | --- | --- |
| Python | Proposed compatibility baseline 3.13.18 | [Python downloads](https://www.python.org/downloads/) also list newer 3.14.10 and 3.15.2. 3.13 is an intentional older maintained line, not called latest |
| Google ADK | 2.14.1 | [PyPI](https://pypi.org/project/google-adk/); adapter phase, not product control plane |
| LangChain | 1.5.0 | [PyPI release metadata](https://pypi.org/project/langchain/), released 11 Nov; resolve provider packages with it |
| LangGraph | 1.2.22 | [PyPI](https://pypi.org/project/langgraph/), observed same-day release; require compatibility and checkpoint tests |
| PydanticAI | 2.68.0 | [PyPI](https://pypi.org/project/pydantic-ai/); typed third framework option |
| Docling | 2.150.1 | [PyPI](https://pypi.org/project/docling/), observed same-day release; qualify on golden PDFs and local hardware |
| harness2 | 0.1.0 research candidate | [pyproject](https://raw.githubusercontent.com/google-research/harness2/main/pyproject.toml) restricts Python to >=3.12,<3.14; isolate experiments |

Why not force the newest Python everywhere? ML/OCR wheels, local engine dependencies, and the harness2 constraint can lag the language release. Start qualification on 3.13.18 with a pinned Linux worker image; prove dependency resolution and performance. Move ordinary workers to 3.14/3.15 when compatible; keep incompatible research experiments in a separate image. Do not run every framework and media engine in one dependency environment.

## Protocols and deferred selections

| Area | Decision now | Exact pin later |
| --- | --- | --- |
| MCP | [2026-07-28 specification](https://modelcontextprotocol.io/specification/latest), capability-negotiated support | Java/Python SDK and supported extension matrix in P5 |
| Agent UI events | [AG-UI](https://docs.ag-ui.com/introduction) adapter with [explicit mapping](modules/agent-ui-contract.md) | Define mapping P2.2, qualify SDK in P9.1; inspected main snapshot declares core 1.0.4/protocol 1.0, not an adopted release pin |
| External agents | [A2A](https://a2a-protocol.org/latest/) evaluation | Spec/SDK version and trust contract in P9 |
| Voice media | [LiveKit Agents](https://docs.livekit.io/agents/) first candidate | Server/SDK/STT/TTS versions in P8 after a latency spike |
| Local models | [Ollama tool calling](https://docs.ollama.com/capabilities/tool-calling); compatible vLLM/llama.cpp endpoints can be added | Runtime, model revision, tokenizer/template, quantization, license, and hardware in P2/P12 |
| Sandbox | CubeSandbox 0.8.0 candidate; Substrate later cluster option | Exact backend revision, host image, kernel/virtualization requirements in P6 |
| Analytics | Isolated DuckDB/Arrow with validated chart renderer | Versions and extension policy in P4 |
| Object storage | S3-compatible API, early filesystem adapter | Hosting-specific implementation and license in P4/P12 |
| Observability | OpenTelemetry contracts with structured logs/metrics | Compatible collector/exporter versions when introduced |
| Identity | Standards-based OIDC; provider pluggable | Managed/self-hosted provider after deployment requirements |
| Artifacts / Scene Studio | Structured renderers, DOM/Canvas/Three.js, specialized WebGPU profile; Hyperframes video adapter candidate | Stable renderer, browser, GPU/driver profile, fonts and FFmpeg/codecs qualified and pinned per P10.3 task |

This deliberately avoids fabricated exact versions for components not selected yet. “Latest” URLs above are evidence sources, not dependency declarations. Production installs must not use floating @latest or unpinned container tags.

**Scene research, 15 November:** [Three.js WebGPURenderer](https://threejs.org/docs/pages/WebGPURenderer.html) and [Hyperframes frame adapters](https://raw.githubusercontent.com/heygen-com/hyperframes/main/docs/concepts/frame-adapters.mdx) were reviewed. Hyperframes documents the custom adapter interface as experimental v0; keep it behind Zincc's contract and qualify a released implementation. WebGPU-only scene features require explicit capability handling. No graphics dependency version is adopted or installed in this planning change. See [Scene Studio](modules/scene-studio.md).

## Compatibility gates

1. **P1.1 Java foundation:** resolve Boot/JDK/Maven, build, run health smoke test, verify formatting and module boundaries. Capture dependency tree and runtime image identity.
2. **P1 frontend:** prove Vite/React/TypeScript 7 plugin compatibility, type generation, lint, production build, and supported browser baseline. If a required compiler-API tool blocks TS7, document a temporary supported TS6 fallback in an ADR; do not silently downgrade.
3. **P2 model contracts:** two cloud providers plus one local route; structured output, streaming, errors, cancellation, token accounting, capability rejection, local-only policy.
4. **P3 durable runs:** SDK/server compatibility, deterministic replay, worker upgrade, crash recovery, and no duplicate external effect after retry.
5. **P4 parsing/indexes:** parser/OCR wheels, Postgres extension compatibility, document accuracy, index migration, and ACL filtering.
6. **P6/P8/P9 specialists:** isolate images per framework/engine, test host capabilities, and record exact dependencies and compatibility results.

A passing build is not a complete portability or security test. Keep software versions separate from runtime model capability and product rollout claims.

## Learning-lab tools added to qualification scope — 15 November

The [learning roadmap](learning-roadmap.md) adds explicit serving, GraphRAG, SFT/LoRA/QLoRA and preference-learning experiments. Candidate tool families include vLLM/Transformers for supported inference, Microsoft GraphRAG for a concrete graph-retrieval comparison, and Transformers/PEFT/TRL for training. No versions of these lab dependencies are adopted or installed yet. Pin stable compatible releases, model revisions and hardware requirements when the individual lab is authorized. A main-branch conceptual document is not a stable package-version declaration.
