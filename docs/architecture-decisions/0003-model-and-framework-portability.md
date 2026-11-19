# ADR 0003 — Model and framework portability

Status: **Model gateway and explicit capability registry adopted in P2.1.** Policy routing and framework adapters are proposed for subsequent tasks. [Implemented boundary and verification](../modules/model-gateway.md) · [Versions](../technology-baseline.md)

## Problem

Zincc must use cloud or local models, choose specialist modalities, and let customers use ADK, LangChain, or another strong framework without losing platform policy.

## Decision

Define model requests/results and a tested capability registry independent of provider DTOs. Use Spring AI where stable support fits and direct adapters when necessary. Start with explicit routing rules and evaluate before learned routing. Offer native Java plus ADK, LangChain/LangGraph, and PydanticAI worker adapters. All governed tools pass through the same gateway.

## Alternatives

- One flagship provider: fastest initial integration but violates portability/local requirements and creates unnecessary cost dependency.
- “OpenAI-compatible” endpoint as the whole abstraction: common transport, but schema/tool/vision/caching semantics still differ.
- One framework as platform authority: easier initially, but framework choice then dictates product security and persistence.
- Many agents for every task: increases cost, latency, and coordination failures without guaranteed quality.
- PydanticAI alternatives: a team-oriented orchestration framework could simplify some demos; typed validation and focused adapter scope are more useful for this platform's third option. Reconsider only with a concrete unsupported customer requirement.

## Trade-offs and consequences

Capability differences remain visible; portability is not identical behavior. Local models may require smaller tasks and more deterministic checks, and some jobs remain unsupported. Routing decisions include data location and cost constraints before quality ranking. Each framework image needs its own conformance suite and checkpoint policy.

## Validation

Same task contracts on two cloud providers and one local model; schema failure, missing tool support, cancellation, usage accounting, local-only egress, and measured quality/cost regression checks.
