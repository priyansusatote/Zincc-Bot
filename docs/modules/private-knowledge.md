# Use private documents and dashboards

Open **Documents and dashboards** in the signed-in workbench. Select a PDF and choose **Upload PDF**. After it becomes ready, select it and type a question. **Find cited passages** returns passages from the document. **Source page** opens the extracted text for that page. **Download original PDF** lets you compare extraction with the actual page. Check the original when the page is marked OCR or when exact wording matters.

To make a dashboard, select an extracted table, name the dashboard, choose the numeric column and enter its unit. Select the column to group by and optionally filter to one displayed value. Save to see the decimal total, group chart, filtered rows and original cell coordinates. The CSV uses that saved revision. An update from another tab can produce a revision conflict. Reopen the dashboard and review the latest settings.

The document owner sees the source and its derived content while still a member of that workspace. Deletion removes the live source, chunks, embeddings, datasets and dashboards together. Existing database backups follow their own retention policy.

## Run and verify

Apply Flyway V7, start the existing OIDC workspace and enable the additional `knowledge` Spring profile. Use Linux/amd64 containers and configure the control plane with access to the local Docker daemon. Build the image and pass its immutable local ID as the value of `ZINCC_KNOWLEDGE_IMAGE`.

```powershell
docker build --platform linux/amd64 -t zincc-knowledge:p4 worker/knowledge
$env:ZINCC_KNOWLEDGE_IMAGE = docker image inspect zincc-knowledge:p4 --format '{{.Id}}'
$env:SPRING_PROFILES_ACTIVE = 'oidc,openrouter-free,agents,knowledge'
```

The local worker uses pdfplumber for digital PDF text and ruled tables, Tesseract for up to six image-only pages, and quantized English MiniLM for vectors. Each operation gets a fresh non-root container with no network, a read-only root filesystem, no mounts, dropped capabilities, capped CPU, memory, processes, request and response sizes, and an independent wall deadline. At most two worker calls run per application process. Build-time downloads are pinned to the base image, wheel hashes and model commit. The frozen OS package list records the qualified build.

Guardrails include 8 MiB per upload, 60 pages, 360 text chunks, 24 tables and 100 document rows per owner/workspace. An upload reuses an existing SHA-256 source in the same owner scope. The same file can retry a failed or expired parse. Publication is all-or-nothing and rejects a superseded claim. Password-protected files are rejected. OCR is English and page-level. OCR text has no structured table geometry. Ruled tables are scoped to single pages. Borderless and merged tables, cross-page reconstruction, inferred units, and explanatory model-generated answers require further qualification.

Retrieval uses PostgreSQL English full-text ranks and capped exact cosine ranks over one private document. It fuses reciprocal ranks and returns up to five source passages. The dimensionality is 384, with the model revision and pooling contract stored with the document. Query and source text stay local. Analytics runs decimal arithmetic with explicit units, detects mixed or mismatched values in a column called Currency, Unit or Units, and reports blank values separately. A malformed number stops calculation. CSV adds source page and row and escapes cells that could start spreadsheet formulas.

For repeatable qualification, generate synthetic PDFs with `python worker/knowledge/test_fixture.py`. Add `--scan` and set `ZINCC_KNOWLEDGE_OCR_TEST=true` to include the scan fixture when Poppler is installed. Set `ZINCC_KNOWLEDGE_TEST_IMAGE` to the qualified image ID and run Maven verification. The store integration test uses PostgreSQL to detect cross-principal and revoked-member disclosure, stale publish and revision conflicts, and retained data after deletion.

[Phase decisions](../architecture-decisions/0015-private-document-pipeline.md) · [Parser](https://github.com/jsvine/pdfplumber) · [Local embedding model](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)
