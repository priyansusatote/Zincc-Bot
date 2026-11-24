# P7.1 · Delegating without losing control of a run

**Implemented and exercised.** The lead gets a real run ID. Each reviewer gets a separate run, pinned definition and ordered assignment. The transaction that creates these rows also queues their delivery. Repeating a start with the same key and assignments returns the original parent. Changing an assignment under that key conflicts.

**Concurrency lesson.** Acquire the parent lock before a child lock. Release database locks while waiting on inference, then check the parent state, current grants and the child's step number before accepting its answer. A cancellation test held a model call open, cancelled the parent and released the answer. The late response could not overwrite the cancelled state.

**When multiple reviewers help.** In the controlled comparison, one role identified wording and another identified an amount discrepancy. Both calls reached a synchronization barrier and both findings survived the attributed merge. The operator could also retain a single finding. This verifies parallel execution and result preservation. The fixture supplies each role's answer, so it cannot establish how often two real models improve review quality.
