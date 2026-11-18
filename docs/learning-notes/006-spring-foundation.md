# P1.1 · The first running Zincc service

18 November 2026 · Backend engineering and system design · [Roadmap](../phases/roadmap.md)

## A. What did we build?

Zincc’s Java control plane starts from an executable JAR. A program on the same machine can call `GET /actuator/health` and receive HTTP 200 with `{"status":"UP"}` when the configured indicators are healthy. This gives each future backend task a working build, a startup path and a testable HTTP boundary.

## B. How does it work internally?

1. The wrapper selects Maven 3.10.1. On a fresh installation it downloads the distribution and compares its SHA-256 with the repository pin. Maven resolves the build plugins and dependencies.
2. The Enforcer plugin checks the Maven and JDK versions before compilation. The Spring Boot parent supplies compatible library versions. Maven compiles the Java sources and runs the tests.
3. During packaging, the Boot plugin places application classes and their runtime dependencies into an executable archive. At verification, the format plugin checks the Java sources.
4. At runtime, Boot’s JAR launcher calls `ZinccApplication.main`. Spring creates an application context. The web and Actuator starters contribute auto-configuration, including an embedded HTTP server and a health endpoint.
5. A request reaches that server on loopback. Actuator evaluates its health contributors, aggregates their results and serializes the public status. Zincc configures which endpoints and details are exposed.

## C. Architecture and design decisions

One JVM process gives the future control-plane modules a common deployment unit. Independent services would introduce network calls and partial failures before a second capability exists. Packages directly under `io.zincc` will represent features. Static dependency checks provide early feedback as those packages appear.

Actuator already defines health aggregation and HTTP status mapping. A handwritten controller returning a constant would keep reporting success even if a registered indicator failed. A live HTTP test exercises routing, serialization and the exposure settings together. Putting an authentication system in front of this first endpoint would introduce identity decisions scheduled for later tasks. IPv4 loopback is the local default for this stage.

## D. Important technologies and concepts

**Dependency injection.** Boot assembles the health endpoint from Spring-managed components. Health contributors can join that assembly through configuration. The test also receives the actual bound port through `@LocalServerPort`, so it can use an ephemeral port. There is no reason to add an artificial service interface just to demonstrate injection.

**Dependency management.** A starter selects related libraries. The Boot parent supplies managed versions, Maven resolves the actual graph, and the wrapper selects Maven itself. These solve three different reproducibility problems. The [resolved dependency tree](../phases/p1.1-dependencies.txt) shows which libraries enter the runtime and which are test-scoped.

**System design.** Process health, service readiness for a particular operation, and successful completion of an agent task are different observations. Later, a model request can fail while this process remains healthy. An ArchUnit rule examines another kind of graph: nodes are packages and edges are compiled references. A cycle couples packages in both directions. This exercise implements a code dependency check. Agent execution graphs, knowledge graphs, retrieval and inference remain separate learning tasks.

## E. Real-world use cases

A developer can tell whether their local control plane started before debugging a client connection. A later installer can poll the same endpoint during startup. A future inbox-agent run will also need run-specific evidence, such as a successful provider response and its timestamp, to support a claim that the inbox was checked.

## F. Edge cases and failure scenarios

- An incompatible JDK or Maven is rejected during validation. An empty dependency cache requires access to the repositories. Once populated on the verified host, the cache supported an offline build.
- A busy port prevents the default server from starting. Tests request a free port from the OS. For a manual run, pass `--server.port=8081` after the JAR name.
- A crashed process cannot answer a probe. Callers need a timeout and must distinguish an unreachable service from an HTTP response reporting degraded health. A retry of this read has no persistent side effect.
- The local health contract exposes the aggregate status. Enabling more Actuator endpoints or rebinding to a network interface changes the exposure and needs review. Local processes can reach a loopback listener.
- Repeated health checks still consume server resources. Capacity, latency under load and behavior behind a reverse proxy require separate measurements.
- Generated directories in the OneDrive workspace carried the Windows read-only attribute. Maven could compile but its clean goal failed deleting an empty generated directory. Clearing the attribute on generated `target` directories fixed the failure. Offline clean builds then passed in the workspace and in an independent copy outside the sync folder.

## G. Trade-offs and limitations

The embedded server and framework make the first JAR larger than a minimal Java HTTP server. They give the next task a consistent lifecycle and dependency injection model. Pinning the JDK patch makes drift visible and requires deliberate patch upgrades. The build checks the JDK patch line, while the qualification record identifies the vendor and build actually used.

The package rules can catch statically visible cycles and reverse dependencies on the launcher. They cannot detect runtime coupling through reflection, events or shared tables. At this stage the production tree has only a launcher. A negative experiment in a separate copy demonstrated enforcement. Hosted access, separate liveness/readiness probes and production SLOs need their own deployment criteria.

## H. Code walkthrough

| File | Responsibility |
| --- | --- |
| [pom.xml](../../pom.xml) | Defines the managed Spring stack, toolchain validation, test dependencies, executable packaging and formatting check. |
| [Wrapper properties](../../.mvn/wrapper/maven-wrapper.properties) | Identifies the Maven archive and its expected digest. |
| [ZinccApplication.java](../../src/main/java/io/zincc/ZinccApplication.java) | Starts the context at the root package so future feature packages can be discovered. |
| [application.properties](../../src/main/resources/application.properties) | Sets the bind address and public management surface. |
| [HealthEndpointTest.java](../../src/test/java/io/zincc/HealthEndpointTest.java) | Sends real HTTP requests and asserts the complete health JSON and the 404 for environment inspection. |
| [ModuleBoundaryTest.java](../../src/test/java/io/zincc/ModuleBoundaryTest.java) | Imports production bytecode, forbids dependencies on the launcher and checks feature packages for cycles. |

## I. What should I remember?

1. The wrapper selects the build tool. It still needs a compatible JDK.
2. The Boot parent and the resolved dependency tree answer different questions. One defines version policy, the other records what was selected.
3. A successful compile alone does not exercise framework auto-configuration or the packaged entry point.
4. An ephemeral test port avoids collisions and keeps the normal networking path under test.
5. Treat the exact fields returned by an operational endpoint as part of its contract.
6. A known-bad fixture distinguishes a working architecture rule from one that simply has nothing to reject yet.

## J. Interview preparation

**Why run an HTTP test if Spring owns the health handler?** Zincc owns the combination of dependencies, exposure settings and HTTP contract. A default probe group added a field during development and the strict test caught it.

**Why can the health check pass while a scheduled briefing fails?** The probe observes configured process-level indicators. A briefing can separately fail authorization, provider access or workflow execution.

**Why start with a modular monolith?** The first features can share a release and local calls. Package ownership and automated checks help preserve the option to separate workloads later when isolation or scaling requires it.

**What makes an offline rebuild possible?** The wrapper distribution, plugins and dependencies must already be cached. The successful offline run qualified that state on this host.

## K. Learning exercise

In a scratch copy, add a class under `io.zincc.example` that returns `ZinccApplication.class`. Run the wrapper goal `test -Dtest=ModuleBoundaryTest`. Identify the source class and target in ArchUnit’s error, then remove the reference and rerun. Explain why table sharing between two packages would escape this rule.

## L. Completion status

**Completed.** Local service, reproducible toolchain configuration, health contract, Java format validation and package rules. Three automated tests passed. A clean offline build in an independent copy passed. Its packaged JAR returned the exact expected health JSON and HTTP 404 for the environment endpoint. A temporary reverse dependency failed the architecture test as expected.

**Qualification and debt.** Evidence covers Windows x64 with Temurin 25.0.5+8. Unix wrapper execution, production deployment and workload capacity await qualification. OneDrive can interfere with cleaning generated directories. The package checks will gain coverage as real features enter the codebase.

**Next scoped task.** P1.2 introduces PostgreSQL migrations and workspace membership. Its acceptance criteria include tenant-scoped queries and migration recovery. It awaits separate authorization.
