# Architecture decision records

The table tracks adoption by component. Initial research was completed on 14 November 2026. At adoption, record the task, tested versions, acceptance date, and any changed consequences.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-control-plane.md) | Spring modular monolith and isolated specialists | Control-plane foundation adopted in P1.1, workers pending |
| [0002](0002-durable-execution.md) | Temporal progression plus action ledger and outbox | Proposed |
| [0003](0003-model-and-framework-portability.md) | Model/framework contracts and measured routing | Proposed |
| [0004](0004-data-retrieval-and-memory.md) | PostgreSQL-first data and evidence-based analytics | Proposed |
| [0005](0005-execution-isolation.md) | Brokered sandboxes and outbound customer runners | Proposed |
| [0006](0006-client-and-delivery.md) | React workbench, staged capability delivery, later polish | Proposed |
| [0007](0007-code-generated-scenes.md) | Editable scenes with isolated interactive and video runtimes | Proposed |
| [0008](0008-delegated-authority-and-data-sharing.md) | Explicit delegated authority and audience-bound sharing | Proposed |
| [0009](0009-connected-ai-engines.md) | Separate model connections from official connected agent engines and their account lifecycles | Proposed |
| [0010](0010-workspace-access.md) | Transaction-local workspace membership reads under a restricted PostgreSQL role | Adopted in P1.2 |

Future ADRs must state context/problem, decision, alternatives, trade-offs, consequences, and verification. A library name alone is not a design decision.
