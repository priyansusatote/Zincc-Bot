# Desktop control and reference review

Date: **16 November 2026**. Research/design only; no product implementation.

## A. What did we build?

A pinned review of five repositories, concrete Windows action acceptance criteria and one generated workbench concept.

## B. How does it work internally?

The proposed bridge observes a Windows app, validates its target, applies a bounded action and checks the result. A control lease selects the actor allowed to provide input. Artifact evidence establishes completion. Research distinguishes source behavior from README promises.

## C. Architecture and design decisions

Retain Spring as the product backend and isolate the OS bridge. Use normalized runtime adapters and scoped grants. A Linux container or separate virtual screen does not substitute for Windows host automation or tenant isolation. Whole-repository forks would introduce competing runtimes and trust assumptions.

## D. Important technologies and concepts

UI Automation exposes semantic controls; visual grounding helps with canvases. Leases expire ownership; fencing rejects commands from an old owner. Transactional scheduling avoids losing a trigger between two writes. Plugin manifests describe capabilities before code loads. These concepts protect concrete actions rather than merely improving chat output.

## E. Real-world use cases

Draw and save in Paint, edit supported desktop documents, operate legacy business applications, hand a login screen to the user, or let several agents share a computer sequentially while researching concurrently.

## F. Edge cases and failure scenarios

Handle wrong foreground windows, DPI changes, lock/elevation screens, held keys after cancellation, driver crashes, stale ownership and double takeover. A paused agent cannot keep issuing input through a secondary tool. Verify exports, delete derived/private state according to retention, and reconcile uncertain external writes before retry.

## G. Trade-offs and limitations

Host automation is less isolated than a disposable guest. App-specific grants require enforcement beyond UI labels. Source inspection does not prove performance or security. Only Superbot's source/build file set received a complete read; larger repositories received selected review. Exact reuse remains subject to file/dependency licensing and compatibility review.

## H. Document and source walkthrough

[The source review](../research/2026-11-16-agent-repositories.md) lists all 16 Superbot files and other inspected paths. [Sandbox design](../modules/security-and-sandboxes.md) specifies Windows control. [The concept](../design/workbench-concept-01.md) records layout and generation brief. No Zincc runtime classes exist yet.

## I. What should I remember?

1. A personal agent needs execution tools, not only a model.
2. A guest desktop and Windows host desktop are different capabilities.
3. Separate screens do not create tenant isolation.
4. Takeover must revoke agent input in the backend and driver.
5. Successful process exit is not verified task completion.
6. Read licenses per component, including enterprise exceptions.
7. Inventory, static review and tested behavior are distinct evidence levels.

## J. Interview preparation

**How do two agents share a desktop?** Serialize interactive input with ownership leases and fencing; allow independent non-GUI work concurrently.

**How can a scheduler lose work?** Updating the last-run timestamp before separately enqueueing a run leaves a crash window; use atomic persistence and idempotent dispatch.

**Why is a takeover button insufficient?** UI changes cannot prevent a worker from continuing to send input; enforce ownership at dispatch and the local driver.

## K. Learning exercise

Write a five-state desktop-control diagram covering agent control, takeover pending, human control, paused and disconnected. Specify which state accepts mouse input and what happens to a stale command.

## L. Completion status

Research report, Windows acceptance clarification and concept image completed. Static source reads and image inspection performed; no upstream or product runtime tests executed. Documentation checks are recorded in status. Full larger-repository audits and all implementation remain pending. P1.1 remains the next separately authorized implementation task.
