# Nod: scoped source review and proposed refinements

Reviewed: **16 November 2026**. Repository: [n8n-io/nod](https://github.com/n8n-io/nod). Pinned revision: [`866158b15302256b718e89dcaf5afdb5751adddb`](https://github.com/n8n-io/nod/tree/866158b15302256b718e89dcaf5afdb5751adddb).

**Verdict:** a useful reference for proactive personal assistance, memory provenance, credential-operation recovery and realistic agent evaluations. It reinforces Zincc's direction; it does not justify replacing Spring Boot, Temporal or the model/framework adapter boundaries. The recommendations below are an isolated addendum while the user reviews the finalized plan. They are **not implemented features or silently accepted changes to the main roadmap**.

## What Nod is

The inspected architecture combines a Node/Fastify server, React UI, Pi agent runtime, PostgreSQL/pg-boss scheduling, and containerized workspace/browser execution. Persistent task sessions have separate histories and execution resources, with owner-level shared workspace and memory. Its n8n skill connects to an external n8n MCP service; that does not mean Nod's core durable execution is the n8n workflow engine.

This is primarily a personal, single-owner reference. Local hosting is not a guarantee that task data stays offline: configured model providers and external services can receive data. Windows installation support does not establish native Windows app control such as drawing in the host's Paint application.

## Review scope and evidence

The pinned tree contains **1,493 tracked files**. This was a targeted static review of architecture-sensitive paths, not a line-by-line audit of the entire repository. The checkout was stored outside Zincc. No upstream installers, application, tests or evaluations were executed.

| Area | Files inspected and depth |
| --- | --- |
| Product and architecture | README; architecture document; selected proactivity, task-session and security-model documentation; complete SECURITY.md and NOTICE; package manifest excerpt and root license header |
| Proactive work | Selected `src/server/proactivity.ts` scheduling, evidence and persistence sections; `src/runtime/index.ts` proactive tool restrictions |
| Memory | Complete `src/server/memory-ops.ts` and `writing-style.ts`; selected `memory.ts` and `memory-learning.ts` ownership, source and generation checks |
| Authority | Complete `src/server/trust-basis.ts` and `trust-review.ts`; selected `trust.ts` decision path |
| Credential operations | Complete `src/server/vault-use.ts`; selected `vault-access.ts`; `tests/vault.test.ts` excerpts covering uncertainty and duplicate claims |
| Scheduling and isolation | Selected `src/server/thread.ts` queue/transaction and recovery sections; complete `compose.yaml`; runtime and provider directory inventory |
| Evaluation | Complete evals README, `evals/src/grade.ts`, `evals/gate.json` and `evals/src/scenarios/ambiguous-recipient-asks.ts`; memory-test inventory and partial excerpts only |
| External workflow integration | Complete `src/runtime/skills/n8n/SKILL.md`, read as source material, not installed or followed as instructions |

Not comprehensively audited: all provider adapters, frontend code, desktop packaging, browser origin/frame handling, migration/recovery behavior, dependency graph, speech binaries or every test. Published success-rate claims were not independently reproduced.

## Recommended additions and refinements

### 1. An opt-in prepared-work inbox

Nod uses source changes, bounded observation and persisted evidence to prepare useful work proactively. Its proactive runtime restricts ordinary action tools; it can hand off to task execution. The persistence path rechecks source versions, preferences and current configuration before committing results. This is stronger inspiration than simply running a large prompt every few minutes.

**Zincc proposal:** an agent notices a changed email, ticket or calendar event, prepares a private draft or recommendation, and places it in a prepared-work inbox. Show why it was prepared, source freshness, what remains and the next available action. Add quiet hours, deduplication, snooze, dismissal and usefulness feedback. Notify only when warranted.

Use opt-in sources and separate preparation budgets. Apply current access permissions when collecting, presenting and acting on evidence. A connector outage means unknown freshness, not “nothing new.” Background source content cannot grant permission for actions. Sending, booking or editing still passes through Zincc's normal action gateway. Reuse the planned Temporal scheduling and connector machinery; do not introduce a second scheduler.

**Mapping:** P5 automation and P7 memory/personalization, with a UI view in final polish. This is the most useful explicit product refinement.

### 2. Memory origin lanes and an editable writing-style profile

Nod's memory operations validate source references, protect stated entries from inferred replacement, and treat sensitive inferred information separately. Its memory control and learning paths use owner-origin messages and generation checks. Quote matching establishes textual provenance; it does not prove that a proposed memory is true or authorized.

**Zincc proposal:** visibly distinguish owner-stated preferences, inferred preferences awaiting review, facts retrieved from external sources, and learned procedures. Do not let a statement in a received email become an instruction or a personal preference automatically. Preserve correction, deletion, provenance and access controls already present in Zincc's plan.

An optional writing-style profile could learn tone from authorized examples, remain editable, and be disabled independently. Nod strips quoted/forwarded material and signatures before style extraction, but its output checks do not prove that every identifying fact is removed. Zincc needs constrained extraction, validation and user review; model prompts alone are insufficient. Source examples still follow the selected model's privacy policy. This is personalization, not automatic weight training.

**Mapping:** P7. Extends existing memory requirements rather than adding a new memory platform.

### 3. Private credential entry with explicit uncertain outcomes

Nod's vault-use code claims an operation, performs the external step outside the database transaction, and records completion. Ambiguous failures become `uncertain`; tests exercise duplicate claims and lost-commit outcomes. That is valuable because a database rollback cannot undo a password already entered into a browser.

**Zincc proposal:** reuse the state-machine and test ideas for a secure sign-in panel or password-manager adapter. Bind permission to account, origin, intended operation and current session; keep secrets out of model context, transcripts and ordinary evidence captures. After an ambiguous outcome, inspect and reconcile before repeating the operation.

Do not copy single-process startup recovery into a distributed deployment: another worker may still be active. Zincc needs lease-aware recovery and fencing. Full origin/frame/redirect handling remains a future audit requirement. Credential entry permission is not blanket permission for subsequent account actions. Payment-card storage is not proposed by this addendum.

**Mapping:** P6 credential/execution controls and P13 native desktop capabilities. Refines the existing action ledger and credential broker.

### 4. Authority receipts and realistic evaluation worlds

Nod associates actions with evidence such as owner input, standing rules or scheduled instructions. It also uses model-based trust review. **Zincc should retain deterministic permission enforcement**: models can interpret intent or request clarification, but cannot mint grants or override hard policy. Add a user-readable “why this action was allowed” receipt referencing the exact instruction or grant revision.

Its evaluation framework runs the agent against simulated apps and checks resulting state. The inspected ambiguous-recipient scenario includes two people named Ana and separately checks that clarification happened before sending, exactly one message reached the intended person and no message reached the other person. This is useful acceptance-test design for Zincc.

Keep hard invariants separate from subjective content judging. Nod's grader supports an optional model-judged alternative for some checks; Zincc must not allow a model score to override failed permission, recipient, duplicate-action or budget invariants. Record judge failures and skipped cases separately. Some eval tooling can still use public-network tools and paid models; a simulated app world is not automatically an offline or free test.

**Mapping:** P2 onward evaluation fixtures, the action gateway and final qualification. Existing requirements gain more concrete scenarios and evidence presentation.

## Reuse disposition

| Category | Decision |
| --- | --- |
| Direct code or skills incorporated | **None.** Nothing copied into Zincc, installed or executed. |
| Ideas recommended | Prepared-work inbox, bounded change-driven observation, memory-origin distinctions, optional style profile, action-authority receipts, uncertainty/recovery test cases and world-state evaluations. |
| Possible later selective code reuse | Narrow test fixtures or utilities only after exact-file license/dependency review, adaptation and tests. No dependency adoption is approved now. |
| Analyzed but not selected as Zincc core | Node/Pi runtime and pg-boss execution stack. They do not replace the chosen Java control plane and Temporal durability. |
| Optional later integration | Customer-owned n8n workflow invocation through a bounded API/MCP adapter. Review that service's own license and permissions separately; it is not a mandatory Zincc dependency. |
| Patterns requiring modification | Single-owner memory/workspace sharing, single-process recovery and model-mediated authority evaluation need tenant isolation, delegated identity, lease-aware recovery and deterministic policy. |
| Not inferred from this reference | Safe hostile multi-tenancy, native host Windows automation, complete offline operation, universal task success, or independently proven benchmark superiority. |

## Licensing, deployment and operational limits

The root license header and package metadata identify Apache-2.0, but the complete NOTICE identifies GPL-3.0-or-later components in local speech and specifically `src/providers/local-speech/phonemizer-worker.ts`. **Do not treat every file and bundled dependency as uniformly Apache-licensed.** Exact paths, notices and transitive assets must be qualified before redistribution; this review is not redistribution clearance.

The container configuration contains useful restrictions, including dropped capabilities, resource limits and a read-only browser root, alongside a network sidecar with NET_ADMIN. These are deployment facts, not proof of isolation for mutually hostile tenants. Zincc's stronger hosted sandbox qualification remains necessary. The upstream dependency manifest also includes prereleases; its version set should not be copied into Zincc's stable-release baseline.

## Completion and recommendation status

- Completed: pinned static source research, a reuse ledger and concrete improvement proposals.
- Verified here: inspected source behavior and documentation consistency within the stated coverage. No runtime correctness, security, performance or benchmark result is claimed.
- Product files changed: this addendum, the reference index and research status only. Main architecture, phase ordering and implementation scope remain unchanged.
- Next step: the user can review the current finalized plan alongside this addendum. Accepting any recommendation should subsequently update its module and scoped task; it does not authorize building that task.
- **Main plan remains ready for user review. Build approval is still pending.**
