# Checking the effect outside the database

P3.4 implemented a bounded task-list read and one exact-title create. Each write includes a unique marker in provider notes. A matching response or reconciliation read gives Zincc a receipt ID. The sandbox maintains its own durable state and can be inspected independently of the run ledger.

The lost-acknowledgment test makes the create outcome ambiguous. A later read finds the task and completes the run with exactly one POST. Absence on the first page is insufficient proof of failure. The user can recheck an unknown run without resending it.

[Component and operating limits](../modules/phase-three-agents.md)
