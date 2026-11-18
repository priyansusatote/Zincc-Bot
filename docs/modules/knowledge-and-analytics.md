# Documents, retrieval, and trustworthy analytics

**Proposed design.** Goal: turn private documents into supported answers and reproducible analysis, including PDFs with difficult layout.

P4.0 establishes restricted fixed-purpose worker execution before parsing. Public hostile multi-tenant processing and arbitrary generated analysis require the applicable P6 isolation gate. The same execution contract is extended later; parser/OCR binaries never run with control-plane credentials.

## Ingestion pipeline

1. Authorize upload; issue a size-limited upload capability. Quarantine the object and record a content hash and immutable source version.
2. Validate actual file type, page/size limits, encryption state, decompression limits, and parser permissions. Do not execute embedded scripts, macros, or remote references. Malware scanning is one layer, not proof of safety.
3. Route digital PDFs to layout-aware parsing; scanned pages to OCR; complex figures/tables to a targeted vision fallback only when policy allows it. Password-protected or damaged documents get a clear recoverable state.
4. Produce a canonical document representation: page, reading order, text block, heading, table/cell, figure/caption, bounding box, language, confidence, parser revision, and source offsets.
5. Preserve raw source and parser output separately. Normalize whitespace and recurring headers carefully; never silently discard footnotes, negative signs, units, or table titles.
6. Chunk by structure and purpose. Keep parent/child relationships, page ranges, section headings, table metadata, and source IDs. Split oversized tables with repeated headers and linked full-table artifacts.
7. Build versioned full-text and embedding indexes. Publish the new index generation atomically after validation. Resume failed stages by checksum and stage version.
8. Show ingestion status, extraction warnings, and a source preview. A user correction creates a new derived version and invalidates dependent results.

[Docling](https://pypi.org/project/docling/) is the preferred parser candidate because it provides structured document conversion and layout/table processing. Benchmark it on our corpus before adoption. A vision LLM is an expensive fallback for selected pages, not a substitute for retaining source geometry.

## Retrieval pipeline

Use query classification, optional rewriting, and parallel lexical/dense retrieval. Apply tenant and resource-access restrictions before candidates are exposed, then recheck before answer delivery. Avoid a global approximate index that retrieves private material into an unauthorized reranker.

Initial lexical search uses PostgreSQL full-text ranking; dense retrieval uses pgvector. Merge candidates with reciprocal-rank fusion, rerank the bounded set, and expand neighboring/parent blocks when context is needed. Tune candidate counts using recall and latency measurements. For selective access filters, test approximate-index recall and use exact/filter-aware strategies when necessary.

Treat each embedding model/revision/dimension as a separate index generation. Rebuild in the background and switch explicitly; never compare vectors from incompatible spaces. Multimodal and late-interaction embeddings are evaluation candidates requiring different storage/scoring, not drop-in replacements for a single-vector column.

Question types need different routes:

| Question | Route |
| --- | --- |
| “What does this clause say?” | Source retrieval with nearby definitions and page citation |
| “What changed across reports?” | Version-aware retrieval and structured comparison |
| “Total revenue by quarter?” | Validated table extraction and numerical query |
| “What is in this figure?” | Figure/caption retrieval with selected image understanding |
| “What happened this week?” | Fresh connector/web data plus explicit as-of time |
| “Summarize the whole report” | Section coverage plan, not only top similarity hits |

The answer schema links each substantive claim to evidence identifiers. Validate source existence, version, access, quoted span, and page/region. Check support for the claim, not merely whether a matching quote exists. If sources disagree, show the disagreement. If evidence is insufficient, report the gap. Similarity scores and model-generated confidence are not calibrated probabilities of truth.

For sensitive grounded answers, validate evidence/access before emitting the corresponding claim; provisional model tokens must not expose unauthorized excerpts while a later check is pending. Revocation interrupts further delivery and invalidates cached context. Distinguish a live draft from a verified cited answer, and never claim revocation can erase text already displayed.

The [typed RAG sample](https://github.com/Shubhamsaboo/awesome-llm-apps/tree/main/rag_tutorials/agentic_typed_rag_pydanticai) demonstrates useful output/citation validation, but its in-memory retrieval and permissive survival of partially valid citations are insufficient for Zincc's claim-level guarantees. We need per-claim support, tenant checks, durable versions, and evaluation.

## PDF to dashboard

Extract a dataset before drawing a chart. Each cell or measure carries source page/region, original text, normalized value, unit, currency, period, extraction method, and confidence. Distinguish zero, missing, redacted, and not applicable. Resolve whether “12.4” means currency units, millions, or a percentage before comparing it.

Validate table shape, row/column headers, totals, repeated tables, footnotes, currency changes, fiscal calendars, and restatements. Request a correction when an ambiguous value materially changes the answer. Financial ratios and totals use deterministic decimal calculations with documented formulas; the model proposes the transformation rather than doing arithmetic in prose.

Run analytical SQL/Python in a constrained worker over a versioned dataset snapshot. DuckDB/Arrow are candidates for local columnar analysis. Deny arbitrary network access, extension installation, filesystem paths, and SQL that touches the application database. Cap CPU, memory, rows, query time, and export size. Sandbox alone does not validate a wrong formula.

Dashboard definitions contain typed chart specifications, measures, filters, source dataset versions, and transformation references. Use a vetted renderer; reject arbitrary JavaScript/HTML in chart fields. Allow table view, drill-down to source, corrected values, explicit refresh, export, and accessible summaries. Apply workspace permissions to both dashboard and underlying datasets.

Updates create new artifact versions. A scheduled refresh reports changed data and failed sources instead of quietly mixing old and new periods. Sharing a dashboard must not accidentally grant access to hidden documents or private columns.

Derived outputs inherit source restrictions by default; intentional wider publication creates an explicitly reviewed audience-bound artifact under [ADR 0008](../architecture-decisions/0008-delegated-authority-and-data-sharing.md). ACL checks also cover aggregates, chart labels, export filenames and summaries. A retrieved secret can leak without exposing the underlying file. Freshness of the document and freshness of its access rights are distinct; stale permission sync must not permit disclosure.

## Evaluation and operations

Build a labeled corpus covering digital/scanned PDFs, multi-column pages, merged cells, rotated pages, tables spanning pages, footnotes, mixed languages, and poor images. Include public/synthetic finance, operational, and narrative reports; avoid private uploads in source control.

Measure parsing coverage, reading order, table-cell accuracy, retrieval Recall@k, citation validity/support, numerical exactness, refusal quality, latency, and cost per usable document. Compare OCR/vision strategies on the same pages. Keep parser and prompt versions in reports.

Failure handling: retry transient download/OCR failures by stage; mark corrupt sources clearly; resume without double indexing; stop overlarge jobs before resource exhaustion; redact secrets in logs; tombstone deleted sources and derived caches/memories; maintain retention and backup policy. A deletion job must reach vectors and generated artifacts, not only the original upload row.

Deferred for production adoption until measured need: graph retrieval, dedicated search/vector clusters, learned query planners, domain fine-tuning, and automatic chart interpretation as authoritative numeric extraction. These require a baseline to demonstrate value. Learning coverage is explicit: [L-RAG-01 and L-GRAPH-01](../learning-roadmap.md) provide hands-on retrieval/GraphRAG comparisons, and L-TUNE-01 covers model adaptation. A successful experiment and a production adoption decision are separate milestones.
