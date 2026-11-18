# LoopForge: source review and feature recommendations

Date: **16 November 2026**. [Tencent/LoopForge](https://github.com/Tencent/LoopForge), revision [`3e02e408b6ebdb4eca1435ca698dbaf75ac78f04`](https://github.com/Tencent/LoopForge/tree/3e02e408b6ebdb4eca1435ca698dbaf75ac78f04). The checkout contains **3,360 tracked files** and was inspected outside Zincc. No upstream program, installer or test was run.

## Verdict and product relevance

LoopForge is a useful software-delivery reference: a Go/MySQL platform coordinating coding CLIs, scripts, human gates and reusable SOPs. It is not a replacement for Zincc's general-purpose personal/work agent platform, Spring control plane or Temporal execution. Its most useful contribution is a concrete way to turn successful work into a repeatable, reviewable process.

The recommendations here remain separate from the main plan under review. The later user-requested existing-subscription feature is specified separately in [connected AI engines](../modules/connected-ai-engines.md).

## Worthwhile new features or refinements

| Recommendation | User experience | Zincc adaptation and phase mapping |
| --- | --- | --- |
| **Save a successful task as a workflow** | After completing work, propose an editable reusable procedure with inputs, outputs, reviewers and triggers. For example, turn a monthly report into a scheduled workflow. | Draft from observed steps; remove one-off secrets/data, validate the graph, show permissions and test before enabling. Generalizes LoopForge's repeatable SOP idea beyond software delivery. P5/P9/P15; not automatic production self-rewriting. |
| **Send back to a particular step** | A reviewer can say “revise the analysis using this source” without restarting unrelated work. Show which later results will become stale. | Separate retry, rework and human decision. Bound iterations/cost, invalidate dependent artifacts and approvals, preserve history, reconcile external actions before replay. P3/P7/P9. |
| **Evidence-based review inbox** | Each pending decision includes the current artifact, changes since last review, test results, remaining uncertainty and a precise requested decision. | Extend existing approval/activity surfaces; bind approval to exact revision, attempt, actor and intended action. P3 and workbench polish. |
| **Software-delivery workflow pack** | An optional agent team plans a change, implements it, runs checks, reviews it and prepares a PR. | Reuse Zincc's builder and GitHub connector. Separate roles and evidence, isolated branches/workspaces, user-controlled publication. Do not make every small task use multiple agents. P9/P15 expansion; qualify one concrete delivery journey first. |

These additions make the product more useful without restricting users to predefined roles. Workflow templates remain optional, editable starting points.

## Source findings that informed the recommendations

**Versioned graphs and controlled rework.** `internal/engine/workflow_graph.go` selects a stored workflow snapshot when available, computes ready nodes from successful dependencies, validates node types/artifact contracts, and uses topological sorting to reject ordinary dependency cycles. Rework uses a separate target instead of turning the execution graph into an uncontrolled cycle. Zincc should preserve immutable run definitions and explicitly model bounded revisions. The legacy fallback to the latest definition/default local profile is compatibility behavior, not a desirable default for new Zincc runs.

**Structured outcomes rather than report-text guessing.** `gate_submit.go` parses `pass`, `rework`, `fail` and `human`, requires a reason, bounds fields, and requires an actionable question for human decisions. A rework request without a configured target fails rather than silently routing elsewhere. `gate_submit_test.go` has matching decision/invalid-target/human-question cases. `gate_bind.go` also contains legacy report-token command adjustments; prefer typed results and actual verification evidence in Zincc instead of inferring completion from a report containing a success word.

**A model verdict is not a human grant.** The structured gate result is agent-supplied. It can propose workflow progression but is not independent proof of correctness or permission to send, publish or spend. `run_ops.go` includes a human override that can mark certain failed/paused script nodes successful. In Zincc, record an explicit waiver separately from a passed check; a waived test must never become false passing evidence, and hard authorization requirements cannot be waived by an agent.

**Stale review protection.** `bot_control.go` binds optional card expectations to node status, attempt and rework count. `persistControlDecision` uses revision-checked persistence when expectations are supplied. Zincc should require these expectations for externally submitted approvals and additionally bind artifact/action digests, permissions and effective identity. The inspected LoopForge method signatures allow expectation-free callers; this review does not establish end-to-end enforcement for every entry point.

**Recovery and fencing.** Selected `internal/repo/runs.go` lease methods transactionally acquire ownership, increment execution generations and condition renew/release on owner and generation. `run_lease_test.go` includes stale-owner takeover and stale-attempt failure cases. `node_outcome.go` deliberately allows a successful outcome across an attempt mismatch when the generation and active state still match. Do not blindly transplant that assumption: Zincc should bind outputs and side effects to the exact accepted execution attempt unless an explicitly tested continuation contract allows otherwise. A lease prevents some stale database writes; it cannot undo an external API call.

**Artifact freshness on restore.** `workspace_artifacts.go` excludes the executing node and descendants when refilling archived outputs, so an old report cannot satisfy a new attempt. `internal/runtime/workspace_artifacts.go` validates paths, hashes, text and size before publication, stages complete files and creates destinations without replacing existing edits. Tests exercise concurrent restores, unsafe paths, corrupt content and symlink escape. Zincc should use these as acceptance-case inspiration while applying its object-store permissions, revision model and Windows-specific path tests.

**Parallel work still needs ownership.** LoopForge's architecture describes ready branches sharing a run workspace. Zincc must qualify write conflicts separately: prefer isolated outputs/worktrees and explicit merge ownership. Independent reviewer roles can improve coverage but are not proof that the review is independent or correct.

## Deployment, provider and license conclusions

- Deployment documentation explicitly targets trusted teams: signed-in users can see projects/requirements/runs across an instance. Docker-socket access gives control of the host daemon. Neither assumption fits Zincc's hostile multi-tenant hosted profile.
- Some auxiliary operations are documented as CodeBuddy-specific despite the broader executor list. Preserve Zincc's capability-based routing; multiple CLI choices do not by themselves prove complete provider neutrality.
- The inspected gate MCP helper resolver supports Linux/macOS targets. This is not Windows-native desktop automation evidence.
- Root LICENSE is MIT with attribution requirements. THIRD_PARTY_NOTICES records modified Superpowers material, editor distributions, Go notices and separately licensed executor packages, including proprietary Claude Code and LGPL libvips dependencies. Exact-file/dependency review remains necessary before redistribution.
- Upstream release/validation documents distinguish simulated protocol tests from paid-provider/live deployment evidence. Those results were not rerun here and are not Zincc test results.

## Coverage and reuse ledger

| Scope | What was read |
| --- | --- |
| Complete files | Root LICENSE, THIRD_PARTY_NOTICES, SECURITY.md, `docs/architecture.md`, `internal/engine/workflow_graph.go`, `gate_submit.go`, `gate_bind.go`, `bot_control.go`, `internal/runtime/workspace_artifacts.go`, `workspace_artifacts_test.go` |
| Selected implementation sections | `internal/engine/run_ops.go` approval/override/persistence, `run_control.go` notification/heartbeat, `node_outcome.go` failure/rework/fencing, `workspace_artifacts.go` restore selection; `internal/repo/runs.go` lease methods; `internal/runtime/gate_mcp.go` materialization/platform checks |
| Selected tests/docs | Gate-result test excerpts; stale-owner/attempt lease tests; deployment/account sections, conversation workspace excerpts, release evidence limits, validation excerpts and Local workflow-contract sections |
| Not comprehensively audited | Every API authorization path, runner/network boundary, frontend, all Local host integrations, complete scheduler/recovery implementation, third-party package source and all 3,360 files |

**Direct code/skills used:** none. **Ideas recommended:** reusable workflows, bounded rework, evidence review, artifact-freshness and recovery tests. **Possible selective reuse later:** MIT-covered fixtures/utilities after exact-source qualification; no adoption now. **Not selected:** Go/MySQL engine replacement, shared trusted-team access model, raw Docker-socket deployment as hosted tenant isolation, or universal manual “pass” semantics.

**Status:** scoped research complete; suggestions ready for review. No implementation approval inferred.
