# ADR 0004 — PostgreSQL-first data and evidence-based analytics

Status: **Proposed**. Related: [knowledge](../modules/knowledge-and-analytics.md).

## Problem

Documents, permissions, citations, memory, and dashboards need consistent provenance. Adding separate stores too early increases deletion, access-control, and operations complexity.

## Decision

Start with PostgreSQL for product records, full-text retrieval, pgvector, and source-linked memory metadata. Use object storage for large immutable artifacts. Parse into a versioned document structure. Use isolated analytical workers for numerical transformations. Add specialized stores only after measured constraints.

## Alternatives

- Dedicated vector database immediately: useful at some scales, but does not replace relational authority or solve document permissions.
- Graph database for all memory: models relations well, but adds operations before graph queries demonstrate value.
- LLM-only PDF answers/charts: flexible, but weak provenance and unreliable arithmetic.
- One embedding model forever: convenient, but quality/cost changes require index versioning anyway.

## Trade-offs and consequences

PostgreSQL handles mixed workloads, so indexes, query plans, and tenant filtering need careful measurement. Approximate nearest-neighbor filtering can reduce recall. Embedding upgrades require separate generations. Deletion must propagate to vectors, summaries, caches, artifacts, and backups under the retention policy. Source citations do not prove entailment; claim support needs separate checks.

## Validation

Golden-document extraction, access revocation during retrieval, index rebuild/switch, source deletion, exact numeric totals, corrupted inputs, and dashboard drill-down to immutable evidence.
