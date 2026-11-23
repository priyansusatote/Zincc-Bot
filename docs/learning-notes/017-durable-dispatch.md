# Recovering an accepted run

P3.1 implemented a transactional outbox and Temporal workflow. The accept transaction inserts the run and outbox row together. The dispatcher uses the run UUID for the Temporal workflow ID and removes the outbox row after acknowledgment. A duplicate start of that same workflow cannot launch another execution.

A human approval is durable in PostgreSQL even when its Temporal signal is lost. A workflow timer rechecks the recorded decision. Recovery tests distinguish a database command retry, a repeated workflow start and a repeated provider call. Each needs its own identity and verification boundary.

[Component and operating limits](../modules/phase-three-agents.md)
