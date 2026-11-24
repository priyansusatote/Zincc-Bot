# ADR 0017 · Team runs, private memory and harness promotion

Adopted for the P7 local workbench on 24 November 2026.

A team must survive a control-plane restart, stop when its owner cancels, and keep conflicting answers identifiable. A saved fact must remain attributable and editable under the same workspace authorization as its source.

**Decision.** Persist the parent, ordered reviewer assignments, pinned versions and outbox entries in one PostgreSQL transaction. Reuse P3's Temporal workflows to schedule child runs independently. The authenticated caller selects one to three distinct reviewers. Local text checks and model opinions receive the submitted goal. Each has zero connector permissions. The parent compares terminal outputs in assignment order. When they differ the owner can select one or keep all with attribution.

**Alternatives and consequences.** In-process futures alone would lose the assignment after restart. A recursive agent network would make resource grants and budgets harder to verify. This implementation supports one delegation level, up to four active teams per owner/workspace, and no tool writes inside reviewers. An already issued model request may consume quota after cancellation. The parent lock and child step fence prevent its late result from committing.

Store user-authored notes with a completed source run, normalized topic and optimistic revision. Recall caches a bounded projection per owner/workspace. Each request verifies membership and reads its database generation. The mutation and generation change commit together, including cascaded source deletion. A per-process cache avoids a separate cache service. Each replica may incur its own cold load.

Evaluate tool-discovery policies on fixed synthetic objectives in a separate offline harness. A local operator may promote only a candidate with a complete, passing cohort and lower measured token use on that model. Deterministic fixture evaluations compare characters and can promote only within the fixture route. SQLite records local experiment and selection history. The serving application's tool grants and model routes are outside that experiment. [Implementation and qualification](../modules/phase-seven-agents.md)
