# Agent runtime, model routing, memory, and caches

**P2.1 implements the model gateway and capability registry.** See the [implemented contract](model-gateway.md) for configuration, token usage and streaming semantics. The following sections describe the target agent runtime.

## Harness versus model

The model predicts useful next content. The harness supplies instructions, relevant context, tools, execution control, state, and verification. A better harness improves reliability but cannot guarantee that a small model can solve every task.

The default loop is: accept goal → construct bounded plan → retrieve context → select route → request a structured step → validate → dispatch permitted tools → observe → verify → finish or replan within limits. Each step records input references, route/configuration version, tool intents, artifact references, usage, and outcome. Record useful rationale, not hidden model reasoning.

Stop on verified completion, budget/deadline exhaustion, cancellation, a necessary human decision, or a diagnosed unrecoverable error. Track repeated actions and lack of progress to prevent infinite loops. Recovery resumes from checkpoints rather than repeating already committed actions.

OpenClaw's [agent loop](https://raw.githubusercontent.com/openclaw/openclaw/main/docs/concepts/agent-loop.md) is useful for session serialization and lifecycle design. Our proposed implementation keeps one current writer per conversation, fences stale writers, and emits canonical run completion independently of model-stream completion.

## Model gateway and routing

ModelRequest contains task kind, modalities, structured-output schema, required capabilities, context budget, privacy location, latency objective, maximum spend, and acceptable fallback policy. ModelResult normalizes output parts, tool proposals, usage, finish reason, and errors while preserving provider-specific metadata behind the adapter.

Capability records identify tested support for streaming, tool calls, JSON schemas, vision, audio, video, embeddings, reranking, cache behavior, cancellation, and context length. An OpenAI-compatible URL does not guarantee equivalent behavior. Probe a local model's actual template and schema support. Never invent its context limit.

| Work | First approach | Escalation |
| --- | --- | --- |
| Rules, timestamps, arithmetic, permissions | Deterministic code | No LLM authorization override |
| Classification, extraction, query rewriting | Small/local model or typed classifier | Stronger model if schema/evaluation fails |
| Fast lookup | Search API plus concise synthesis | Broader search or stronger synthesis |
| Planning and difficult synthesis | Best measured reasoning route within policy | More evidence or targeted specialist, not endless retries |
| PDF layout/OCR | Parser/OCR worker | Vision model on selected low-confidence pages |
| Embeddings/reranking | Dedicated retrieval models | Alternative index generation after evaluation |
| Images, voice, video | Modality-specific provider/local engine | Capability-compatible route only |
| Verification | Deterministic checks, evidence review | Independent model/human for unresolved judgment |

[Jev](https://docs.typesafe.ai/introduction) is a candidate for typed decisions such as relevance or route selection; it is not itself the web-search engine. Calibrate any probability threshold on Zincc's data. Policy filters run before cost ranking: local-only, geography, allowed vendors, modality, and permission constraints are hard constraints. Within eligible routes use measured success, latency, and cost. Start with explicit rules; learn routing only after enough labeled outcomes exist.

Fallback is never silent data-policy relaxation. Cloud failure may fall back to a permitted local model, but a local-only request must not move to a cloud model. A voice model cannot automatically replace a vision route. Do not retry provider writes by switching vendors.

## Framework adapters

**Additional connection type, 16 November:** [connected AI engines](connected-ai-engines.md) adds official CLI/app-server/ACP adapters and eligible direct plan-use connections. These are separate from the framework adapters below. A connected engine owns its inner loop; Zincc retains durable progression and action authority. API keys and local models remain supported. See proposed ADR 0009.

| Adapter | Why offer it | Integration rule |
| --- | --- | --- |
| Zincc native Java | Simplest default; direct Spring integration | Platform owns loop and checkpoint contract |
| Google ADK | Agent/workflow ecosystem and recipes | Python worker; tools delegated through gateway |
| LangChain + LangGraph | Explicit graphs and checkpointed agent state | Define boundary between inner graph and outer durable workflow |
| PydanticAI | Typed dependencies and structured output validation | Map validated outputs/events into shared contracts |

The [ADK recipe catalogue](https://github.com/google/adk-recipes) supplies examples, not a production runtime. [PydanticAI](https://ai.pydantic.dev/) and [LangGraph](https://docs.langchain.com/oss/python/langgraph/overview) are candidates with different programming models. These sources establish framework roles; Zincc adapter semantics are our design.

Temporal owns cross-service scheduling and long waits; a framework may own inner graph state. Do not run overlapping automatic retries around one side effect. All action attempts share the platform idempotency key. Store checkpoint ownership and adapter version explicitly; declare whether an adapter supports safe resume, cancellation, streaming, and replay. If a third-party agent runs tools outside the gateway, label those tools unmanaged and do not claim platform enforcement.

The [AG-UI contract](agent-ui-contract.md) maps framework/UI events without replacing canonical run/action records. Each adapter defines how an inner checkpoint maps to an outer durable step and rejects unsupported resume rather than replaying unknown writes. ADK's Python adapter is selected for recipe/worker compatibility, not because ADK is Python-only; a Java adapter can be evaluated later if it reduces complexity and meets the same contract.

## Parallel agents

An agent definition is persistent; a run is temporary. One user may create separate research, inbox, finance-report, and developer agents. They receive separate scopes and memory, not the owner's entire account by default.

P3.0 supplies the minimal immutable agent definition; P7.1 adds orchestration and management. Each child carries effective principal/delegation lineage and a narrower or equal capability set. Collaboration checks output audience and source access under [ADR 0008](../architecture-decisions/0008-delegated-authority-and-data-sharing.md). A shared agent's owner is not automatically the caller's authority.

Decompose work into a DAG when independent branches are useful. A parent reserves a total budget atomically, allocates sub-budgets, sets concurrency/depth limits, and owns synthesis. Children return evidence/artifact references and typed findings. Avoid concurrent edits to a shared transcript. Merge conflicting document changes using revisions and an explicit owner.

An independent reviewer can catch errors; multiple agreeing agents are not proof. Cache shared permitted research, cancel obsolete branches, propagate deadlines, and account for every branch. Add a multi-agent pattern only when evaluation beats a simpler single-agent baseline.

## Context and memory

Context is the material in the current request. Memory is persisted information selected for future use. Cache is a performance optimization. These are separate mechanisms.

Context assembly order: stable policy/tool schemas → agent/project instructions → relevant source-linked memory → task state → retrieved evidence → recent messages/tool results. Preserve trust boundaries and tool call/result pairing. Allocate a budget per section, reserve output capacity, and use summaries with pointers to recoverable raw evidence.

Memory layers:

1. Current working state and scratch artifacts for the run.
2. Episodic observations with sources, timestamps, and confidence.
3. Stable user-approved preferences and project knowledge.
4. Reusable procedural skills, promoted only after evaluation.

Use tenant/user/project scopes, expiry, contradiction handling, correction history, and edit/delete/export controls. Sensitive facts and inferred personal attributes need explicit policy; a remembered preference never grants tool permission. Revoked or deleted sources invalidate derived memories. Keep deletion tombstones so a later consolidation cannot recreate them.

Inspired by [TencentDB Agent Memory](https://github.com/TencentCloud/TencentDB-Agent-Memory) and [claude-mem](https://github.com/thedotmack/claude-mem), store compact observations plus source pointers and retrieve detail on demand. Zincc will implement its own model-neutral policy and storage layer. Background consolidation uses a snapshot revision and commits only if that revision remains valid; no overwriting newer user corrections.

## Cache design

| Layer | Key and contents | Invalidation / restriction |
| --- | --- | --- |
| Provider prompt cache | Provider-specific stable prefix and model configuration | Provider rules; no guaranteed cross-provider reuse |
| Exact response cache | Tenant, principal access fingerprint, normalized input, model/prompt/tool versions, source revisions, locale | Short TTL plus revision invalidation; safe reads only |
| Retrieval cache | Tenant/access scope, query, embedding/index generation, filters | Source/ACL/index changes invalidate |
| Embedding cache | Tenant-scoped content hash, model revision, normalization/dimension | New embedding version means a new namespace |
| Parser/artifact cache | Content hash, parser/renderer revision, options | Reuse only with matching ownership/access and rights |
| Connector read cache | Account/resource, query, provider cursor/version, timestamp | Webhook or bounded TTL; display freshness |
| Semantic answer cache | Optional similar-question candidates | Disabled initially; require equivalent intent, evidence, and access |

Never cache “send email” as if it were a read, reuse approval across changed payloads, or answer “new notifications?” from an unspecified old snapshot. Avoid cross-tenant deduplication that leaks document existence. Use bounded size, eviction, hit/miss/staleness metrics, and per-key request coalescing to reduce stampedes. Cache outage falls back to authoritative reads; it must not disable authorization.

Providers expose different prompt caching semantics: [OpenAI](https://developers.openai.com/api/docs/guides/prompt-caching) and [Anthropic](https://platform.claude.com/docs/en/build-with-claude/prompt-caching). Keep stable prefixes where supported, measure billed usage, and configure policy per adapter rather than promise a universal TTL or saving.

## Controlled improvement

The [learning roadmap](../learning-roadmap.md) adds explicit inference/KV-cache, multi-agent comparison, and fine-tuning labs. Transformer KV state belongs to the inference engine, while the cache table above describes platform/provider abstractions. Weight/adapter training is separate from memory consolidation and harness refinement. Those labs require scoped authorization and evaluation; the production runtime does not silently train on user activity.

Use [harness2](https://github.com/google-research/harness2) as research inspiration for offline candidate generation, probing, and composition. Freeze security boundaries and evaluation holdouts. A candidate prompt, skill, or routing policy receives a version, evaluation report, and rollback target before promotion. No live agent rewrites its own authorization or secretly changes production behavior.

Verification gates: crash/restart between steps; duplicate actions; malformed structured output; full context; tool prompt injection; stale checkpoint writes; local-only fallback; cache ACL revocation; concurrent budget exhaustion; runaway child agents; and task quality/cost compared with baseline.
