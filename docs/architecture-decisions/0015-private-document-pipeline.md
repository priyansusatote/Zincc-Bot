# 0015 — Local document extraction, private indexes and reproducible dashboards

**Adopted in P4 for the local workbench.** A private source can affect search results, dashboard numbers, and exported rows. All of those outputs need the same owner and membership checks. Parsing a PDF must run outside the service that holds the database and provider credentials.

**Decision.** Spring keeps immutable, size-limited source bytes and derived versions in PostgreSQL for this single-host slice. Each fixed worker operation runs in a fresh container pinned by image ID. The launcher passes input and output as bounded streams. The worker has no network, mounts, provider keys, or Docker socket. PostgreSQL atomically exposes extracted passages and versioned tables. Hybrid ranking combines full-text and local embedding candidates. The answer surface displays source excerpts with page links. Dashboard measures run as a fixed decimal transformation. The dashboard revision pins its table, columns and filter for display and export.

**Alternatives and consequences.** An object store would scale source storage independently and enable direct uploads. Bounded byte storage keeps publication and deletion transactional for P4. An approximate vector index would reduce query cost over a large corpus. Exact cosine scores stay simple for up to 360 chunks per file. Local embeddings keep query and document text on the host. Extractive passages reduce generation errors and require the reader to interpret evidence. Fixed transformations support exact, reviewable arithmetic and restrict the kinds of questions the dashboard can answer.

Container isolation was qualified for the local workstation. Public hostile uploads still need the P6 isolation gate. Broad cross-document ingestion and background re-indexing will need separate workload and access qualification.

[Run guide](../modules/private-knowledge.md) · [Worker code](../../worker/knowledge/worker.py)
