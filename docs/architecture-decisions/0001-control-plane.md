# ADR 0001 — Spring control plane with isolated specialists

The Spring control-plane foundation was adopted in P1.1 on 18 November 2026. Worker isolation, domain ownership and cross-language contracts await their implementing tasks. [Version and qualification record](../technology-baseline.md).

## Problem

Zincc needs strong product authorization, durable state, and operational clarity while benefiting from Python's AI/document ecosystem. The user explicitly requires Spring Boot as the main backend.

## Decision

Use a Java/Spring Boot modular monolith for identity, projects, agents, connections, policy, action records, and usage. Run durable workers separately. Add Python/browser/media workers for specific capabilities through versioned contracts. Domain modules own their data and expose narrow application interfaces.

## Alternatives

- All-Python service: convenient for AI libraries, but ignores the requested Java foundation and couples product ownership to agent-library behavior.
- All-Java implementation: simpler language footprint but forces avoidable work for OCR/ML/media integrations.
- Microservices for every module: independent deployment, but premature distributed transactions, operational burden, and slower local development.
- Fork OpenBot's entire stack: faster access to a demo, but substitutes its Hono/backend dependencies for the requested architecture and inherits assumptions we have not qualified.

## Trade-offs and consequences

Multiple languages require contract tests and separate dependency images. A modular monolith needs enforced boundaries to avoid becoming a shared-table system. API and heavy workers scale separately. Later extraction is possible because contracts and ownership are explicit, not because every class is abstracted preemptively.

## Validation

Boot/build verification, forbidden dependency checks, contract fixtures across Java/Python, and a task that survives worker failure without compromising the API.
