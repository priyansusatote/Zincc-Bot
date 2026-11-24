# P7.3 · Correct a remembered fact

**Implemented and tested.** An owner records a topic and fact against a completed run. The workbench shows its source goal and revision. Two different values for the same normalized topic require an explicit correction. An edit includes the revision the user saw. If another tab already updated it, the older edit gets a conflict.

Recall returns up to eight matching notes for a keyword query. A local agent run records a match count. Opening its sources performs fresh authorized recall, so corrected or deleted text does not survive as a copied fact in that receipt. No model provider receives notes during recall.

The database tests cover cross-owner access, stale edits, contradictory same-topic creation, correction, explicit deletion and source deletion. Meaning conflicts expressed under different topic names still require the user's judgment.
