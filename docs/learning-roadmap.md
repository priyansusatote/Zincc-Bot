# Zincc engineering learning roadmap

Workbench UI state ownership and accessible navigation are implemented and browser-tested. [Component note](learning-notes/047-workbench-navigation.md)

| P9 topic | Coverage | Evidence |
| --- | --- | --- |
| Start preconditions, decision projections and device recovery | Implemented for engine runs, member-scoped approvals and paired desktop jobs | [Workbench decisions](learning-notes/046-workbench-decisions.md) |
| Framework isolation, checkpoints and AG-UI replay | Implemented and tested with three SDKs and controlled inference | [Planning turns](learning-notes/042-framework-planning.md) |
| Immutable revisions and optimistic concurrency | Implemented, database and browser verified | [Definition history](learning-notes/043-versioned-builder.md) |
| Engine identity, consent, allowance and model acknowledgement | Implemented paired delivery and private grants, verified Windows Antigravity inference, and experimentally evaluated OpenCode Free rate limits | [Engine qualification](learning-notes/044-private-engine-connections.md) · [Single-use device claims](learning-notes/045-paired-engine-delivery.md) |

| P7 topic | Coverage | Evidence |
| --- | --- | --- |
| Delegation, fan-out and cancellation | Implemented, concurrent database and worker tests | [Team runs](learning-notes/037-team-runs.md) |
| Context caching and ACL invalidation | Implemented, cold readers and revision changes evaluated | [Projection cache](learning-notes/038-context-cache.md) |
| Private source-linked memory | Implemented, correction, deletion and recall tested | [Memory editor](learning-notes/039-source-linked-memory.md) |
| Harness optimization | Offline promotion implemented, fixture and model comparisons evaluated | [Holdout results](phases/p7-evaluation.md) |
| Provider qualification | Numes gateway implemented, signed-in tasks and cross-model tool selection experimentally evaluated | [Route qualification](learning-notes/041-numes-route-qualification.md) |

| P6 topic | Coverage | Component note |
| --- | --- | --- |
| Resource admission and container lifecycle | Implemented, local Docker evaluation | [Broker](learning-notes/033-sandbox-broker.md) |
| VM isolation and snapshot policy | One hosted lifecycle, shape and domain policy evaluated. Tenant and snapshot isolation remain open. | [Qualification](learning-notes/034-hosted-isolation.md) |
| Device authentication, leases and RLS | Implemented, PostgreSQL integration tested | [Runner leases](learning-notes/035-runner-leases.md) |
| Isolated browser context and human takeover | Implemented, Edge exercised | [Browser handoff](learning-notes/036-browser-takeover.md) |

P1.1 implemented startup, dependency management, HTTP health and a package-dependency test on 18 November 2026. See the [component learning report](learning-notes/006-spring-foundation.md). Later entries describe planned coverage.

## 1. What the earlier summary missed

P2.2 implemented and tested idempotent sends, optimistic revisions, row-level isolation, interrupted generation and cursor recovery. [Conversation replay learning note](learning-notes/012-conversation-replay.md)

The P2.1 OpenRouter follow-up implemented provider-specific credential binding and verified a live text greeting through the free Lightning route. [OpenRouter learning note](learning-notes/011-openrouter.md)

P2.1 implemented the provider boundary, declared capabilities, output validation, streamed completion and usage capture. Fixture outcomes establish protocol behavior. Live task quality remains to be measured. [Model gateway learning note](learning-notes/010-model-gateway.md)

P1.4 implemented generated API types, runtime boundary validation, and accessible async workspace selection. [Workbench learning note](learning-notes/009-workbench.md)

P1.3 implemented browser authentication, CSRF-protected sign-out and per-request workspace authorization. [Session learning note](learning-notes/008-browser-sessions.md)

P1.2 implemented and tested composite keys, row-level security, pooled transaction context, revocation visibility and DDL rollback. [Workspace learning note](learning-notes/007-workspace-membership.md)

System design, agent loops, tool calling, orchestration, parallel agents, human approval, retrieval, and evaluation already appeared in the architecture/module documents. The short completion message did not enumerate them. Inference internals, transformer KV caching, and a hands-on fine-tuning path were insufficiently explicit. GraphRAG had only a deferred adoption note. This document gives those topics concrete learning outcomes and experiments.

The **16 November review** adds explicit design-study coverage for delegation/audience controls, AG-UI state boundaries, capacity/admission, retained workflow data and quarantined restores. See [review learning note 005](learning-notes/005-architecture-review.md). These are designed/studied, not implemented or experimentally evaluated. Evaluation fixtures now begin in P2.1; P2.5 consolidates them.

Three coverage modes:

- **Core:** implemented and explained within the product task that needs it.
- **Lab:** a scoped hands-on comparison with reproducible evidence. Completing the experiment is a learning goal; deploying its approach is a separate decision.
- **Study:** source/code/design walkthrough and trade-off analysis. Use this for infrastructure that requires disproportionate resources or has no demonstrated product need.

All work remains individually authorized. A lab is not permission to install dependencies, train a model, acquire a GPU, or incur cloud cost now. Use available hardware and small datasets first; record any resource blocker rather than claiming a simulation proves real serving/training performance.

## 2. Backend and system-design coverage

| Topic | Zincc context and what to learn | Mode / phase | Required evidence |
| --- | --- | --- | --- |
| High-level system design | Requirements, boundaries, capacity estimates, latency/cost budgets, failure domains, deployment choices | Core, P1–P12 | Architecture diagram, assumptions and capacity worksheet |
| Low-level design | Domain modules, interfaces, state machines, invariants, dependency injection, testable provider adapters | Core, P1–P3 | Implemented component walkthrough and boundary checks |
| Data modelling | Tenant/project/run/action/document schemas, normalization, indexes, query plans, migration strategy | Core, P1/P4 | Schema, migration test and query-plan evidence |
| Transactions and consistency | Isolation, lost updates, optimistic concurrency, atomic budget reservations, outbox and reconciliation | Core, P1/P3/P7 | Concurrent/duplicate/crash tests |
| Distributed execution | Timeouts, deadlines, retries with jitter, idempotency, durable waits, compensation and workflow upgrades | Core, P3/P5 | Recovery drill and duplicate-effect prevention |
| Concurrency | Bounded fan-out, admission control, tenant fairness, cancellation, leases, fencing and merge ownership | Core, P3/P7/P12 | Measured concurrent workload and stale-writer rejection |
| Application caching / key-value stores | In-process cache, Valkey when justified, cache-aside, TTLs, eviction, invalidation, stampedes, safe cache keys | Core/lab, P7 | Hit/miss/latency measurements and revocation tests |
| Graph algorithms and data structures | Workflow DAGs, dependency ordering, cycles, traversal and task cancellation propagation | Core, P3/P7/P9 | Graph invariants and execution tests |
| Events and messaging | Webhooks, ordering, deduplication, transactional outbox, queue lag, consumer ownership | Core, P3/P5 | Delivery/replay test and lag telemetry |
| Kafka/streaming alternatives | Partitions, consumer groups, offsets, replay, schema evolution and when a broker is warranted | Lab/study, L-DIST-01 | Comparison with existing queue/outbox; no forced production broker |
| API/network design | REST/OpenAPI, SSE reconnect, WebSocket control, WebRTC media, versioning, rate limits and backpressure | Core, P1/P2/P8 | Contract/reconnect/overload tests |
| Security and identity | OAuth/OIDC, tenant isolation, secrets, scoped grants, prompt-injection boundaries, sandbox escape surface | Core, P1/P3/P5/P6 | Threat model and adversarial boundary tests |
| Operations and scale | Metrics/logs/traces, SLOs/error budgets, profiling, connection pools, rollout and rollback, backup/restore | Core, P12 with foundations earlier | Load report, restore drill and runbooks |
| Replication/partitioning | Strong/eventual consistency, read replicas, sharding, CAP trade-offs under partitions, multi-region recovery | Study/lab, L-DIST-01/P12 | Workload-driven design comparison; deploy only if needed |

System design is a continuous discipline across the project, not one chapter at the end. Each ADR must connect a choice to requirements and operational consequences.

## 3. AI, agentic AI, and model-serving coverage

| Topic | What we will implement, inspect, or measure | Mode / phase |
| --- | --- | --- |
| Model fundamentals | Tokenization/chat templates, embeddings, attention intuition, context windows, sampling, reasoning effort, model capabilities | Core/study, P2 |
| AI inference | Prefill and autoregressive decode, streaming, CPU/GPU memory, throughput/latency, concurrency, batching and overload | Core/lab, P2/P12/L-INFER-01 |
| Transformer KV cache | Attention K/V reuse, prefix reuse, memory pressure, cache isolation, paged allocation, quantization/offload trade-offs | Lab, L-INFER-01 |
| Advanced serving | Speculative decoding, tensor/pipeline parallelism, prefill/decode separation, routing and warm/cold workers | Lab/study, L-INFER-01; hardware-dependent |
| Model routing | Capability and privacy filters, cost/quality cascades, fallback, calibration and adaptive effort | Core/lab, P2/P7 |
| Agentic AI / harness | Plan/act/observe/verify, bounded replanning, progress detection, tool discovery, context management and stopping | Core, P3/P7 |
| Tool/function calling | JSON schemas, argument validation, dispatch, result normalization, parallel reads, action verification and MCP | Core, P3/P5 |
| Human-in-the-loop (HITL) | Durable approval, clarification, correction, takeover, rejection, expiry and resumption | Core, P3/P6/P8/P9 |
| Agent orchestration | Temporal outer workflow, inner agent graph, checkpoints, event routing, one retry owner per side effect | Core, P3/P9 |
| Multi-agent systems | Supervisor/worker, delegation, handoff, independent review, bounded hierarchy and artifact-based communication | Core/lab, P7/P9/L-MAS-01 |
| Parallel agents | DAG fan-out/fan-in, context isolation, shared-resource conflicts, budget allocation and cancellation | Core/lab, P7/L-MAS-01 |
| Memory and context engineering | Working/episodic/procedural memory, retrieval, progressive disclosure, compaction, provenance and deletion | Core, P7 |
| Semantic/vector search | Embedding spaces, similarity, normalization, ANN/HNSW concepts, filtering, reranking and index generations | Core/lab, P4/L-RAG-01 |
| Advanced/agentic RAG | Structural chunking, query decomposition/rewriting, hybrid retrieval, fusion, reranking, follow-up retrieval and abstention | Core/lab, P4/L-RAG-01 |
| Multimodal retrieval | Text/table/image evidence, page-image embeddings, multi-vector late interaction and contextual embeddings | Core/lab, P4/L-RAG-01 |
| GraphRAG / knowledge graphs | Entity/relation extraction, identity resolution, source-backed edges, graph search, community summaries and graph/text retrieval | Lab, L-GRAPH-01; measured product adoption |
| Agent evaluation | Outcome and tool-argument correctness, action receipts, trajectory diagnostics, recovery, HITL behavior and cost | Core, P2.5 onward / L-MAS-01 |
| RAG/AI evaluation | Recall@k, MRR/nDCG, evidence support, abstention, numerical accuracy, robustness, latency and calibrated human/model judging | Core/lab, P2.5/P4/P12 |
| Fine-tuning | Data preparation, SFT, LoRA/QLoRA, train/validation/test splits, losses, overfitting, regression and serving adapters | Lab, L-TUNE-01 |
| Preference learning | Chosen/rejected data, DPO experiment, reward design/gaming; RLHF/RLAIF/GRPO conceptual comparison | Lab/study, L-PREF-01 |
| Distillation | Teacher-generated examples, quality filtering, rights/provenance and small-model task specialization | Study/lab extension to L-TUNE-01 |
| Multimodal/live agents | Speech recognition/generation, realtime models, turn handling, interruption, video context and asynchronous tools | Core, P8/P10/P14 |
| Code-generated media and visual critique | Procedural scenes, shaders/GPU pipelines, frame clocks versus stateful simulation, source revisions, image/motion evaluation, bounded critic/repair and human creative review | Core, P10.3a–P10.3h; [Scene Studio](modules/scene-studio.md) |
| LLMOps / MLOps | Dataset/model/prompt/skill/index versions, eval gates, canary release, rollback, drift and privacy | Core/lab, P2/P7/P12 and all model labs |

“Latest” means checking primary evidence at the point of adoption. These topics include durable foundations and newer techniques; they are not all inventions of 2026. No technique is automatically the best for every task.

Scene Studio also supplies backend/system-design learning: GPU worker admission and scheduling, durable render recovery, bounded encoding buffers, immutable content hashes, tenant-scoped frame caching and untrusted browser isolation. A graphics **scene graph** expresses object transforms/hierarchy; it is distinct from an agent execution DAG, knowledge graph or ANN index graph. Its learning evidence includes a rendered artifact, timing/recovery report and bounded-review comparison, not only generated source.

## 4. Caches that must not be confused

| Mechanism | What it stores | Who controls it in Zincc |
| --- | --- | --- |
| Application key-value cache | API reads, computed results or short-lived metadata | Spring services / optional Valkey |
| Retrieval/embedding cache | Search results or vectors for a specific index/model/access scope | Knowledge workers and platform |
| Transformer KV cache | Previously computed attention key/value tensors | Inference engine; usually opaque behind a cloud API |
| Prefix cache | Reusable inference state for an identical compatible token prefix | Serving engine or provider, subject to its matching/isolation rules |
| Semantic answer cache | Candidate earlier answer for a similar question | Optional platform optimization requiring evidence/access/freshness checks |

For an autoregressive transformer, retaining previous attention K/V tensors avoids recomputing them at every generated token. It consumes memory and does not store an authoritative answer or permanent user memory. [Transformers explanation](https://huggingface.co/docs/transformers/cache_explanation).

For a conventional decoder with uniform full attention, an illustrative uncompressed KV memory estimate is: **2 × batch × cached tokens × layers × KV heads × head dimension × bytes per element**. With batch 1, 8,192 tokens, 32 layers, 8 KV heads, head dimension 128 and two-byte elements, this is about **1 GiB**, excluding weights and runtime overhead. This is an architectural example, not a benchmark; sliding-window, compressed/latent, hybrid, quantized and distributed architectures require different accounting.

Prefix reuse requires compatible model/adapter, tokens and execution state; matching natural-language meaning is insufficient. vLLM documents reuse of a matching existing prefix and isolation mechanisms such as cache salting. Zincc must select trusted cache namespaces and test privacy boundaries. [vLLM prefix behavior](https://raw.githubusercontent.com/vllm-project/vllm/main/docs/features/automatic_prefix_caching.md), [cache design](https://docs.vllm.ai/en/latest/design/prefix_caching/).

The lab measures cold versus warm prefixes, prompt lengths, output lengths and concurrency. Track TTFT, inter-token latency, total latency, throughput, memory and cache hit behavior. Compare cache strategies only on supported model/runtime combinations. [Cache strategies](https://huggingface.co/docs/transformers/kv_cache), [vLLM metrics](https://docs.vllm.ai/en/latest/usage/metrics/).

## 5. Three different graph concepts

1. **Execution graph:** nodes are steps/agents; edges are dependencies or conditional transitions. It schedules work.
2. **Knowledge graph:** nodes are entities; edges are source-backed relationships. It supports relationship questions and GraphRAG.
3. **Vector index graph:** an ANN structure such as HNSW connects vector neighborhoods to accelerate retrieval. Its edges are not business facts.

A graph database is a storage option. LangGraph is an execution framework. GraphRAG is a retrieval approach. Using one does not imply using the others. A simple knowledge graph may be stored in relational tables during a lab; adopt a dedicated graph database only when its queries and scale justify it.

Microsoft GraphRAG is a useful concrete implementation to study: graph/community indexing and local, global, DRIFT and basic query modes. Compare those approaches with the same hybrid-text baseline; graph extraction, summaries and updates have costs and can contain errors. [GraphRAG](https://microsoft.github.io/graphrag/), [query modes](https://microsoft.github.io/graphrag/query/overview/).

## 6. Evolved RAG: a measured progression

Build a baseline, then change one component at a time:

1. Layout/OCR-aware ingestion with page/region/table provenance and versioned sources.
2. Structural/parent-child chunking; compare chunk strategies on retrieval labels.
3. Lexical + dense retrieval with fusion and reranking. PostgreSQL full-text ranking is the initial lexical route; it is not automatically BM25. A BM25 baseline may be added as a comparison.
4. Query decomposition, multi-query rewriting, metadata/ACL filters and temporal freshness.
5. Agentic follow-up retrieval when a measurable evidence gap remains; bounded attempts and explicit insufficient-evidence output.
6. Structured table/SQL routes for calculation and multimodal routes for figures/scans.
7. Contextual embeddings and multi-vector visual/late-interaction retrieval as measured variants.
8. GraphRAG for entity relationships, multi-hop evidence and corpus-level questions.

Self-reflective/corrective/adaptive retrieval patterns belong in the comparison; an LLM's self-assessment is not a trustworthy correctness label. Compare against long-context reading where appropriate. Evaluate accuracy, evidence coverage, access isolation, freshness, maintenance cost and latency, not only answer fluency.

The **28 October 2026 contextual-embeddings paper** is a recent candidate, while ColPali is an earlier foundation for visual multi-vector retrieval. Their published claims do not establish superiority on Zincc data. [Contextual embeddings](https://arxiv.org/abs/2610.38854), [ColPali](https://arxiv.org/abs/2407.01449).

## 7. Fine-tuning is an explicit hands-on track

Start with one bounded task such as extraction into a schema or selecting a permitted tool. Compare a prompted base model, retrieval-enhanced baseline where applicable, and a fine-tuned adapter on a frozen held-out set. Use permitted public/synthetic data first. Ordinary customer conversations do not automatically become training data.

The lab covers data curation/deduplication, template/tokenization compatibility, train/validation/test separation by source/entity as needed, supervised training, checkpoint selection, overfitting, general capability regressions, model/adapter registry and inference deployment. Training loss alone is not acceptance evidence. Never use the final test set to select prompts/checkpoints and then report it as untouched.

LoRA trains compact updates while freezing the base weights; QLoRA combines adapter training with a quantized base. Choose a model/hardware-compatible method and record actual memory/time/cost. SFT and DPO solve different training problems; DPO requires preference data. [LoRA concepts](https://huggingface.co/docs/peft/main/conceptual_guides/lora), [QLoRA paper](https://arxiv.org/abs/2305.14314), [SFT](https://huggingface.co/docs/trl/sft_trainer), [DPO](https://huggingface.co/docs/trl/dpo_trainer).

Fine-tuning, RAG, memory updates and harness optimization are distinct. Fine-tuning changes weights/adapters; RAG supplies current evidence; memory persists selected context; harness optimization changes surrounding execution. They can complement each other. Full foundation-model pretraining is a study topic, not a promised training run for this product.

The conceptual LoRA URL currently resolves only on the upstream main documentation; it is conceptual evidence, not permission to install an unreleased dependency. Select and pin stable training/serving versions when the lab is authorized.

## 8. Scoped lab backlog

All labs are **pending, not authorized to execute yet**. Each receives its own focused learning note, experiment manifest, reproducible commands, results and adoption decision.

| Lab ID | Prerequisites | Hands-on deliverable / acceptance |
| --- | --- | --- |
| L-RAG-01 | P2.5 and P4.2 | Fixed corpus/queries, hybrid baseline and selected retrieval variants; recall/ranking/support/cost report; change one variable at a time |
| L-GRAPH-01 | L-RAG-01 | Source-linked entity/relationship graph and graph/text comparison; entity merge errors, ACL/deletion propagation and index/update costs |
| L-INFER-01 | P2.3 and evaluation/metrics foundation | Local serving benchmark for KV/prefix caching, concurrency and supported quantization. Record hardware and runtime. Compare baseline and compressed KV memory, latency and answer quality on identical workloads when supported. [TurboQuant experiment design](research/2026-11-19-firecrawl-turboquant.md) |
| L-TUNE-01 | P2.5 and a verified small-model baseline | One SFT/LoRA or compatible QLoRA adapter, held-out evaluation and served comparison; data rights, training cost and rollback recorded |
| L-PREF-01 | L-TUNE-01 and suitable preference examples | Small DPO comparison with behavior/regression checks; study reward hacking and alternative RL approaches |
| L-MAS-01 | P7.1 | Single-agent versus sequential versus bounded parallel specialists on equal task sets/budgets; outcome, communication cost, conflicts and cancellation |
| L-DIST-01 | P3.1 and stable event contracts | Outbox/queue failure lab; optional Kafka consumer/replay exercise; duplicates, ordering, rebalancing and recovery explained before any adoption |
| L-SEC-01 | P3.3/P4.2/P6 as applicable | Tool/approval/RAG prompt-injection and tenant-boundary cases with reproducible outcomes, fixes and remaining limitations |

These labs interleave with their prerequisites rather than all waiting until the final release. Large-scale distributed training, custom GPU kernels and multi-region production deployment remain study/qualified-extension work unless an actual need and resources are established.

## Phase 2 component evidence

| Topic | Evidence and level |
| --- | --- |
| Capability routing and privacy | Implemented deterministic selection. Two free cloud routes experimentally compared on fixed synthetic prompts. [Report](learning-notes/013-route-selection.md) |
| Retrieval and citations | Implemented lexical passage selection and explicit cache provenance. Tested freshness and partial retrieval, including a live source. [Report](learning-notes/014-cited-search.md) |
| Task evaluation and cost | Implemented frozen regression cases, durable turn usage and an offline statement reconciler. Studied calibration and exercised the Brier calculation with synthetic labels. [Report](learning-notes/015-evaluation-accounting.md) |

## Phase 3 component evidence

| Implemented topic | Evidence |
| --- | --- |
| Versioning, delegation, optimistic concurrency | [Definitions](learning-notes/016-agent-versions.md) |
| Outbox, at-least-once delivery and replay | [Durable dispatch and restart qualification](learning-notes/017-durable-dispatch.md) |
| Bounded model loop, checkpoints, cancellation and no-progress detection | [Agent execution](learning-notes/018-bounded-loop.md) |
| Human approval, idempotency, grant revocation and ambiguous outcomes | [Action ledger](learning-notes/019-action-approval.md) |
| OAuth refresh ownership, per-principal resource grants | [Connection boundary](learning-notes/020-connection-grants.md) |
| External consistency, receipt verification and read-only reconciliation | [Connector slice](learning-notes/021-task-receipts.md) |

The local failure drill experimentally evaluated replay across process and service restarts. Hosted Google Tasks, multi-replica operation and production scaling remain unqualified.

## Phase 4 component evidence

| Implemented topic | Evidence |
| --- | --- |
| Container resource, process and egress limits | [Worker boundary](learning-notes/022-document-isolation.md) |
| PDF lifecycle, OCR and atomic publication | [Ingestion](learning-notes/023-document-ingestion.md) |
| Embeddings, full-text ranking and fusion | [Retrieval and controlled Recall@1 check](learning-notes/024-hybrid-retrieval.md) |
| Fixed transformations, missing values and unit checks | [Decimal analytics](learning-notes/025-decimal-analytics.md) |
| Dataset lineage, optimistic revisions and export | [Dashboards](learning-notes/026-private-dashboards.md) |

## Phase 5 component evidence

| Implemented and locally evaluated topic | Component note |
| --- | --- |
| Principal-specific OAuth, checkpoint transactions and account separation | [Account sync](learning-notes/027-account-sync.md) |
| Calendar deltas, Slack event authenticity and retry behavior | [Provider events](learning-notes/028-channel-calendar.md) |
| MCP negotiation, schema limits and credential isolation | [Curated MCP](learning-notes/029-curated-mcp.md) |
| Digest pinning, permission expansion and rollback | [Skill versions](learning-notes/030-skill-versions.md) |
| Wall-clock scheduling, idempotency and restart behavior | [Routine recovery](learning-notes/031-routine-recovery.md) |

[Operational follow-up on bounded reads, progress visibility and rate-limit reporting](learning-notes/032-sync-progress-and-model-failures.md)

## 9. Evidence of learning and completion

For every component, the learning note identifies the covered topics from this matrix and link to implemented code or experiment evidence. Preserve five states: planned, studied, implemented, experimentally evaluated, and production-adopted. None implies the others.

Require an execution trace, relevant tests, failure exercise, trade-off/ADR, small exercise, and interview questions. For stochastic AI evaluations, record model/version, prompts, data split, sampling settings, repeat count and uncertainty; seeds do not guarantee identical provider behavior. Validate judge quality on human-labeled examples. Agent outcome evaluation must not insist that every valid solution has one exact tool sequence.

This roadmap creates opportunities to develop expertise through implementation, measurement, explanation and exercises. Merely including a technology name—or observing generated code—does not establish mastery.
