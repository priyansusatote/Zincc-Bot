# Phase 2 evaluation and model cost

## Repeat the qualification

Run `.\mvnw.cmd -Pworkbench verify` after building the frontend. The [frozen v1 manifest](../../src/test/resources/eval/p2-v1.json) defines ten cases in each of six families, eight development and two held-out cases per family. The acceptance threshold for each implemented deterministic case is an exact match to its frozen expectation. Each case has a stable ID in the test reports. Preserve v1 and publish a new version if changing cases.

| Family | Executed | What is graded |
| --- | ---: | --- |
| Public research | 10 | Correct evidence sentence or abstention on fixed source text |
| Structured extraction | 10 | Gateway admits or rejects the supplied model response under its declared schema |
| Local operation | 10 | Eligible route or empty selection for a configured privacy/capability fixture |
| Recovery | 10 | Correct events or rejection after a cursor on real PostgreSQL, including retention boundaries |
| PDF analytics | 0 | Ten gold outcomes await P4 |
| Inbox triage | 0 | Ten gold outcomes await P5 |

The inventory test enforces the manifest's size, IDs and split. The four active groups execute production components. Structured output fixtures measure validation, and local cases measure routing. Neither score estimates model intelligence. The [route probe](../learning-notes/013-route-selection.md) and the optional real-source test provide separate live observations. There is no statistical confidence estimate from the single live trials. Frozen holdouts are visible to developers, so reserve a separate unseen set for qualification of future model changes.

## Reconcile one model billing window

Apply Flyway V5. PostgreSQL saves the route, provider and model when accepting a turn. Completion and interruption update that same row with available token counts. Request retries use the original turn identity. Expired, historical and provider-failed requests can lack counts. Rows from before V5 retain null acceptance times. Keep export files outside Git. Use an authorized reporting session scoped to the same account and window as the provider statement. P2 stores conversation inference only.

Export CSV with this query using `psql` CSV output. Supply inclusive UTC start and exclusive end variables such as `2026-11-22T00:00Z` and `2026-11-23T00:00Z`. Use a window whose calls have settled. When a provider assigns calls by a different timestamp, reconcile boundary calls before comparing amounts.

```sql
SELECT conversation_id::text || '/' || id::text AS attempt, provider, model_name AS model,
       state, input_tokens, output_tokens
FROM zincc.conversation_turn
WHERE accepted_at >= :'start'::timestamptz AND accepted_at < :'end'::timestamptz
ORDER BY accepted_at, conversation_id, id;
```

Normalize one provider statement for that same traffic into a CSV with `provider,model,input_tokens,output_tokens,billed_usd`. Include an explicit zero on a verified free statement. Provide optional independent task labels in a third CSV with `attempt,success,confidence`. Use 1 or 0 for verified task success. An optional probability must have been made before grading. Use blank when unavailable. Use exactly one receipt per attempt and one statement line per provider/model. Keep accounts with shared credentials or unrelated traffic in separate reporting windows or attribute them upstream.

```powershell
python -B tools/reconcile_usage.py receipts.csv statement.csv --labels labels.csv
python -B -m unittest discover -s tools -p test_reconcile_usage.py
```

The report compares input and output totals separately. Missing receipts, missing statements and mismatches remain visible. Model USD per verified success includes the provider's billed spend for failed attempts. The report emits that ratio only with fully reconciled totals, complete labels and a positive denominator. The optional Brier score is the mean squared error between predictions and binary outcomes; inspect larger, stratified sets before using it to choose a router. Search credits, local compute and infrastructure have different cost sources and are outside this model-only report. No live billing statement or confidence dataset was supplied.

[Reconciliation tool](../../tools/reconcile_usage.py) · [Learning report](../learning-notes/015-evaluation-accounting.md)
