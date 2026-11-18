# Scene Studio feature planning

Date: **15 November 2026**. Documentation-only task. No scene generator, renderer, exported video or application code was built.

## A. What did we build?

A researched specification for creating interactive scenes and videos from prompts or supplied HTML/JavaScript. The feature is now explicit in Zincc's proposed product scope, task roadmap, architecture and learning matrix.

## B. How does it work internally?

The planned flow validates a request, creates a scene manifest, generates code, runs it in isolation, collects visual/runtime evidence, repairs bounded defects and exports the selected revision. Interactive navigation uses live controls; video export uses a finite camera path and a controlled frame clock. Spring owns the job and permissions; workers render pixels.

## C. Architecture and design decisions

Reuse the platform's durable jobs, artifact storage and sandbox broker. Keep readable source and immutable render inputs so users can revise a scene and workers can recover safely. Wrap renderer dependencies behind a small contract. A video-model-only approach would lose precise editable scene behavior; building an entire custom engine initially would add avoidable maintenance.

## D. Important technologies and concepts

- **Procedural generation:** code and parameters generate geometry or motion instead of shipping all visual assets. Small code can still produce a large GPU workload.
- **Shaders and scene graphs:** GPU programs draw/compute visuals; object hierarchies organize transforms. Neither is the same as an agent workflow DAG or a knowledge graph.
- **Controlled time:** export derives scene time from the frame index. Stateful simulations additionally need replay/checkpoints; a fixed random seed is insufficient by itself.
- **Visual critique:** rendered views and motion clips support defect detection, while deterministic checks catch errors and timing failures. A critic model is fallible and gets a bounded repair budget.
- **Temporal and sandboxing:** durable progression protects long jobs against worker failure; isolation protects the product from executable generated code. Without these boundaries, long renders either lose work or expose the trusted service to scripts.
- **Caching:** immutable inputs and render profiles identify reusable artifacts. Permissions remain authoritative even on a cache hit.

## E. Real-world use cases

Zincc users could create explorable educational environments, product presentations, animated reports, training simulations or camera-directed procedural scenes. The original ocean project and the existing Hyperframes reference inform this design; their exact evidence and limits are in [the reference ledger](../research/references.md).

## F. Edge cases and failure scenarios

A missing GPU needs an explicit fallback or unsupported result. A device/worker crash must not publish a partial movie as complete. Duplicate jobs converge on immutable inputs and a single final manifest. Concurrent edits create distinct revisions. Malicious HTML cannot receive platform credentials. High load needs admission limits and bounded buffers. A visually plausible still can hide flicker or broken audio, so temporal checks remain necessary.

## G. Trade-offs and limitations

GPU isolation and video encoding cost more than serving a static page. Quality depends on scene complexity, models and hardware. Exporting arbitrary third-party HTML may require adaptation. Tiny bundles, photorealism and fast rendering are separate goals. No universal file-size, creation-time or cross-hardware pixel identity guarantee is made. Hardware and exact dependencies remain unqualified.

## H. Document walkthrough

[Scene Studio](../modules/scene-studio.md) defines user behavior, execution, contracts, failure handling and acceptance gates. [ADR 0007](../architecture-decisions/0007-code-generated-scenes.md) records the choice and alternatives. [P10.3a–P10.3h](../phases/roadmap.md) decomposes delivery. [The reuse ledger](../research/references.md) separates ideas, inspected code and possible future incorporation. There are no implemented classes or methods to walk through yet.

## I. What should I remember?

1. HTML packages a scene; JavaScript and graphics APIs generate its behavior.
2. Generating code uses models; playing each animation frame normally does not.
3. Interactive play and offline export need different timing contracts.
4. A seed does not make every simulation randomly seekable.
5. GPU capability and visual quality require actual measurements.
6. Generated scenes are untrusted programs, even when an AI reviewed them.
7. Visual review needs motion evidence, bounded iteration and human judgment.
8. An open-source example is not an implemented platform feature.

## J. Interview preparation

**How would you export a game-like scene reliably?** Convert user movement to a camera/input track, fix time steps and assets, replay or evaluate the scene at defined frames, then verify capture and encoding.

**Why not run generated HTML beside the main application?** Shared origin and privileges could expose user sessions and data. Isolate the origin and worker, constrain communication/network access and enforce resource limits.

**What belongs in a render cache key?** Tenant/access scope, immutable source/assets, seed, camera/timing settings and the qualified renderer/browser/font/encoder profile, including relevant hardware differences.

**How do you evaluate a visual-review agent?** Compare detected and repaired defects against curated examples and human judgment, include temporal regressions, and measure cost and success against generation without a critic.

## K. Learning exercise

Describe a fish's horizontal position as a function of time and a fixed seed. Then describe a fish whose position depends on nearby fish every simulation step. Explain why the first can be evaluated directly at frame 900 while the second may need replay or a checkpoint.

## L. Completion status

Research, feature specification, proposed ADR and roadmap synchronization completed. Validation passed across **28 Markdown files and 103 local links**, with no broken targets, unbalanced fences, duplicate subsection headings, trailing whitespace or missing A–L sections; zero non-Markdown application/experiment files exist. Documentation verification is recorded in [status](../status.md). No scene was executed, video exported, dependency installed or performance benchmark run. Implementation and hardware/renderer qualification remain pending. Next implementation remains the separately authorized P1.1 foundation task; Scene Studio is planned within P10.
