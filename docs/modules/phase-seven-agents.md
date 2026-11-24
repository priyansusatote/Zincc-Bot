# Agent teams, private memory and offline experiments

## Try a team

In the signed-in workbench, open **Agents and runs**. Create or select a lead and at least one other agent. Choose **Parallel independent reviews**, select up to three reviewers and choose each one's method. The lead coordinates the results. A model reviewer receives the shared goal and its own pinned instructions on its configured route. **Local word count** and **Local unchecked-item count** use the submitted text on the server without inference or connector access. The latter counts lines beginning with `- [ ] ` after trimming whitespace.

Start a run. Reviewer rows expose lineage, individual outcomes and status. Matching answers complete the parent automatically. For different answers choose one or **Keep all attributed reviews**. The combined result preserves assignment order. Cancel at the parent to fence unfinished work. A model failure ends the team and prevents remaining child work from committing. The workbench keeps failed receipts.

**Storage and budgets.** Flyway V11 adds the team edges and memory tables. The existing agents profile, OIDC, PostgreSQL and Temporal service are required. No additional infrastructure or framework was introduced. Every team shares the initiating workspace and actor. Up to four active teams per actor/workspace may run, with at most three distinct children per team. Children cannot recursively delegate. Each model child is limited to two claimed attempts across activity recovery. The parent uses the existing 24-hour workflow deadline. One local probe can use multiple agents without calling any provider. Broader cross-agent data grants, general tool delegation and production capacity remain separate qualifications.

## Remember and recall

1. Open **Private memory**. Select a completed personal run as the source, name a topic, enter the fact and choose **Remember fact**.
2. Enter keywords to search. All query words must occur in the topic or fact. Empty search shows the personal editor's directory. Each owner may keep 64 notes per workspace.
3. Correct a note to revise the value for its topic. A stale edit or competing assertion for the same normalized topic returns a conflict. A different topic may express a semantic contradiction that needs human review.
4. Choose **Recall private memory locally** as an agent run mode. Its goal supplies the search terms. Open **current sources** on the receipt to look up today's values. The stored receipt contains the count at execution time. Deleting a note removes it from subsequent recalls. Removing its source run also deletes the note.

The cache stores at most 64 scoped projections per process. Every recall rechecks membership and the committed note generation. Concurrent readers of one cold key share a load. The verification issued 16 concurrent recalls and observed one source projection load. This saves source joins while preserving authorization queries. User-authored notes remain in the workspace. The model-review mode receives no automatic private memory.

## Run the offline experiment

From the repository root, run `python tools/harness_eval.py evaluate`. The command uses synthetic inputs and records its experiment ID in an ignored local SQLite ledger. Run `python tools/harness_eval.py promote --experiment <id> --strategy program`, then `python tools/harness_eval.py try --case calendar` and `python tools/harness_eval.py rollback`. Promotion and rollback change the offline harness's active selection for that route. Each run records the evaluator revision.

An explicit supported `--route` runs the same experiment against OpenRouter or Numes with synthetic prompts. For example, run `python tools/harness_eval.py evaluate --route numes/z-ai/glm-5.3-flash:free`. The provider prefix keeps evidence in the ledger specific to the serving provider. The offline default uses no provider credentials. The program interpreter supports bounded first, count and sum operations. Arbitrary JavaScript or external writes require different isolation and approval gates. A partial or inferior cohort cannot promote. [Measured results](../phases/p7-evaluation.md) · [Architecture decision](../architecture-decisions/0017-team-runs-and-private-memory.md)

## Local acceptance on 24 November 2026

The signed-in workbench paired the existing Temporal service with Flyway V11. A local team used one word-count reviewer and one unchecked-item reviewer on a synthetic brief. Both completed, the parent waited for a decision, and retaining both reviews completed it. The sample lead, editor and analyst are available in the local agent picker. The memory check created a note, corrected it, recalled the revised value and deleted it. The model-backed team reached the provider and each reviewer recorded RATE_LIMITED. Successful model result fencing and merge paths were verified with controlled model responses in integration tests.

The Java suite passed 106 tests, including Temporal worker recovery, with three optional probes skipped. All 11 Edge workbench regressions passed, including multiline team submission and memory correction. Two offline harness tests exercised grant revocation during pagination and candidate selection. The experiment also promoted a fixture candidate, used it for a subsequent trial and rolled back.

## Learning notes

[Parallelism and cancellation](../learning-notes/037-team-runs.md) · [Context caching](../learning-notes/038-context-cache.md) · [Source-linked memory](../learning-notes/039-source-linked-memory.md) · [Harness evaluation](../learning-notes/040-harness-experiments.md)
