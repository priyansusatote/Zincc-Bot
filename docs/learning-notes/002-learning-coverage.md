# Learning coverage revision

Date: **15 November 2026**. Documentation-only planning task; no application or experiment executed.

## A. What did we build?

An explicit learning matrix and eight scoped lab proposals covering the user's omitted topics: system design, graphs, inference/KV caching, advanced RAG, multi-agent orchestration, tool calling, HITL, evaluation and fine-tuning.

## B. How does it work internally?

Every topic maps to product phases or a lab with prerequisites and required evidence. Future component reports distinguish studied, implemented, experimentally evaluated and production-adopted work. A comparison can be completed even when its result recommends retaining the simpler product approach.

## C. Architecture and design decisions

Keep the production architecture grounded in requirements while providing hands-on exploration. This avoids hiding advanced topics behind “later” and avoids adding infrastructure without evidence. The alternative of installing every named tool would increase maintenance without proving learning or product quality.

## D. Important technologies and concepts

Application caches store reusable application data; transformer KV caches hold inference state. Workflow graphs schedule steps; knowledge graphs describe source-backed relationships; ANN graphs accelerate similarity search. RAG supplies evidence, fine-tuning changes model weights/adapters, memory persists selected context, and harness optimization changes execution around the model. These distinctions determine ownership, cost and validation.

## E. Real-world use cases

A report analyst benefits from measured retrieval/GraphRAG comparisons. A local agent benefits from inference memory/latency profiling. A repeated extraction task may benefit from a small tuned adapter. Multiple research workers need coordination and outcome evaluation before their extra cost is justified.

## F. Edge cases and failure scenarios

Training/test leakage can make weak adaptations look strong. Graph entity merges can connect unrelated records. Prefix caches can expose cross-tenant timing information if isolation is ignored. More parallel agents can exhaust quotas or duplicate actions. Labs must include these failure cases and report untested hardware paths.

## G. Trade-offs and limitations

This expands learning work and requires time, data, and sometimes GPU resources. Small experiments do not establish large-cluster performance. Hardware-dependent extensions remain explicitly unmeasured until run. No training data, dependency installation, cloud spend or production adoption is authorized by this documentation revision.

## H. Document walkthrough

[learning-roadmap.md](../learning-roadmap.md) is the canonical matrix and lab backlog. [AGENTS.md](../../AGENTS.md) now requires topic/evidence tracking. [The roadmap](../phases/roadmap.md) adds lab prerequisites to delivery. Runtime and knowledge modules distinguish experimental coverage from production adoption. [The baseline](../technology-baseline.md) records lab tool families without inventing version pins.

## I. What should I remember?

1. System design spans requirements, data, concurrency, capacity and operations.
2. Redis/Valkey-style key-value caching is different from transformer KV caching.
3. Execution graphs, knowledge graphs and vector-index graphs serve different jobs.
4. Fine-tuning and RAG are complementary but solve different problems.
5. Agent evaluation includes actions/recovery, not only fluent answers.
6. Learning coverage needs experiments and explanation, not a list of packages.

## J. Interview preparation

**Why can prefix caching fail to help?** Requests may lack reusable compatible prefixes, caches may be cold/evicted, or the workload may be dominated by decoding or tools rather than prompt processing.

**When might GraphRAG help?** Relationship or corpus-level questions may benefit; compare correctness and indexing/update cost against a hybrid baseline and preserve source permissions.

**How would you evaluate a fine-tuned tool-calling model?** Use held-out tasks, argument/schema correctness, permitted-action outcomes, capability regressions, latency and cost, with leakage-controlled data splits.

## K. Learning exercise

For a PDF-answering agent, classify each proposed improvement as retrieval, inference, training, memory or orchestration: reranking, KV caching, LoRA, saved preferences, and parallel document workers. State one metric and one failure case for each.

## L. Completion status

Learning matrix, lab backlog and synchronized guidance completed. Documentation validation passed across **25 Markdown files and 79 local links**, with no missing local targets, unbalanced fences, trailing whitespace or missing A–L sections. Zero non-Markdown application/experiment files exist. No runtime tests, benchmarks or training were performed. All hands-on learning/implementation tasks remain pending; next implementation remains P1.1 after authorization.
