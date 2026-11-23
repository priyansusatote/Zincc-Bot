# Architecture decision records

The table tracks adoption by component. Initial research was completed on 14 November 2026. At adoption, record the task, tested versions, acceptance date, and any changed consequences.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-control-plane.md) | Spring modular monolith and isolated specialists | Control-plane foundation adopted in P1.1, workers pending |
| [0002](0002-durable-execution.md) | Temporal progression plus action ledger and outbox | Adopted in P3 |
| [0003](0003-model-and-framework-portability.md) | Model/framework contracts and measured routing | Gateway adopted in P2.1, routing and frameworks proposed |
| [0004](0004-data-retrieval-and-memory.md) | PostgreSQL-first data and evidence-based analytics | Proposed |
| [0005](0005-execution-isolation.md) | Brokered sandboxes and outbound customer runners | Proposed |
| [0006](0006-client-and-delivery.md) | React workbench, staged capability delivery, later polish | Web shell adopted in P1.4 |
| [0007](0007-code-generated-scenes.md) | Editable scenes with isolated interactive and video runtimes | Proposed |
| [0008](0008-delegated-authority-and-data-sharing.md) | Explicit delegated authority and audience-bound sharing | Proposed |
| [0009](0009-connected-ai-engines.md) | Separate model connections from official connected agent engines and their account lifecycles | Proposed |
| [0010](0010-workspace-access.md) | Transaction-local workspace membership reads under a restricted PostgreSQL role | Adopted in P1.2 |
| [0011](0011-browser-identity.md) | OIDC browser sessions with live workspace authorization | Adopted in P1.3 |
| [0012](0012-conversation-replay.md) | Atomic turn projection and replay within a private conversation | Adopted in P2.2 |
| [0013](0013-phase-two-boundaries.md) | Inference locality, explicit public research and atomic turn usage | Adopted in P2.3–P2.5 |
| [0014](0014-agent-versions-and-connector.md) | Pin definitions, intersect grants, bind approval to one provider action | Adopted in P3 |

Future ADRs must state context/problem, decision, alternatives, trade-offs, consequences, and verification. A library name alone is not a design decision.
