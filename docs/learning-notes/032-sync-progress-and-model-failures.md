# Observing a long-running sync

A connection can be making progress while its first full snapshot is still unavailable. The private registry now reports the number of items committed for each feed. The UI polls that count. The first-sync view does not expose partially synchronized subjects. Gmail reads at most 32 message IDs per initial page with at most four concurrent metadata requests per page. A failed read aborts the page and preserves its cursor.

A chat turn has a similar distinction between state and cause. A terminal interruption now records a bounded category in the same transaction as the partial answer and usage. Snapshot and replay expose the category independently of answer text. This keeps provider diagnostics out of saved conversations. Old turns retain the generic label because their original cause was never recorded.

Implemented and locally evaluated topics include cursor consistency, bounded I/O concurrency, HTTP rate limits and durable failure reporting. The database regression checks progress before the first full snapshot becomes readable. A protocol test exercises HTTP 429, and the signed-in conversation test checks a provider length stop. [Integration guide](../modules/phase-five-integrations.md) · [Model gateway](../modules/model-gateway.md)
