# P4.1 · Publish a complete document version

**Implemented** separate upload claim and publication transactions. The parser detects PDF content and extracts page-tagged text and ruled-table cell geometry. Local OCR adds text for image-only pages. A failed claim can be retried. A late worker cannot overwrite a newer claim. PostgreSQL tests show scoped deduplication and cascade deletion. Scan OCR and digital table tests use original synthetic fixtures.

[Feature, verification and limits](../modules/private-knowledge.md)
