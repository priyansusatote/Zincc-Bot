# Measuring a useful answer and its cost

**Implemented** deterministic task evaluation, provider usage reconciliation and an optional probability score. The frozen manifest separates four runnable component families from two future workflow families. A completed HTTP call and a verified task success answer different questions. We grade expected evidence, field types, local-route selection or recovery behavior independently.

**Execution path.** When a conversation turn is accepted, Zincc records the provider/model used. It writes the final token counts in the same database update as the answer's terminal state. A repeated turn keeps one receipt. An interrupted generation can still carry billable tokens. A missing provider count remains unknown. An operator can compare those receipts with a billing statement for the same workload.

**Worked example, synthetic.** Two calls use 15 input and 5 output tokens in total. One answer succeeds and the other stops early. A matched statement of $0.08 gives $0.08 per verified success. With confidence predictions of 0.8 for the successful answer and 0.4 for the failed one, the Brier score is (0.04 + 0.16) / 2 = 0.10. A real calibration conclusion needs many independent outcomes.

**Verification.** A browser-session integration test checks the persisted token counts on PostgreSQL for a duplicate submission, interrupted stream and text response. Forty frozen cases run offline. Twenty PDF/inbox gold outcomes are staged. Python tests cover missing usage, duplicates, mismatched totals, success labels and decimal arithmetic. [Run the suite and reconcile receipts](../modules/phase-two-evaluation.md)
