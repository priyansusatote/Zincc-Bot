# ADR 0007 — Editable scenes with separate interactive and video execution

Date: **15 November 2026**. Status: **Proposed**. Related: [Scene Studio specification](../modules/scene-studio.md).

## Problem

Users want prompt/code-generated visual experiences like the supplied underwater demo, plus editable video exports. Free-running interactive code cannot automatically provide repeatable offline frames, and arbitrary uploaded scripts must not share the trusted product environment.

## Decision

Add Scene Studio to P10. Keep structured manifests and readable immutable source revisions as the editable artifact. Support distinct interactive playback and frame-addressed export profiles, with a fixed-step replay option for stateful simulations. Reuse Spring for policy/metadata, Temporal for durable progression, object storage for outputs, and isolated TypeScript/browser/GPU workers for execution.

Prefer DOM/Canvas for suitable 2D work and a higher-level 3D library for general scenes. Qualify raw WebGPU as a specialized profile. Evaluate Hyperframes behind a Zincc-owned renderer contract; do not expose its experimental custom adapter API as the permanent product API. Route generation and visual review by capability without a mandatory model vendor.

A bounded generate-render-inspect-repair workflow combines deterministic validators, a visual critic and user review. Keep performance qualification separate from aesthetic judgment. Publish or share only through existing grants and artifact controls.

## Alternatives

- Video-generation models only: useful for footage but do not supply editable procedural worlds, precise data charts or interactive controls. Remain an optional asset source.
- Screenshot-record every live preview: simple, but timing, dropped frames and audio synchronization depend on runtime speed. Appropriate only as an explicitly labeled quick recording, not the main repeatable export contract.
- Build a complete bespoke graphics/encoding engine: maximum control, disproportionate initial maintenance. Use proven components where their conformance and license checks pass.
- Require raw WebGPU and a tiny single file for everything: narrows compatible hardware and makes ordinary editing harder. Offer compact output when feasible; retain maintainable source.
- Treat arbitrary HTML as immediately exportable: incompatible clocks, network requests, libraries and permissions make this unreliable. Import reports identify required adaptations.

## Trade-offs and consequences

GPU-capable isolated workers, visual evaluation and render queues add cost and operational work. Stateful simulations can limit frame parallelism. Cross-hardware pixel identity cannot be assumed. A reviewer model can miss defects or disagree with a user's taste; it is supplementary evidence.

Source-level editing gives controllable objects, timing and reusable designs. Existing job/permission infrastructure provides consistent cancellation, access and recovery. Asset licensing, notices, browser/codec compatibility and offline behavior must be verified at adoption. Public repositories are candidates, not copied implementation.

## Validation before acceptance

Prove isolated preview, unsupported GPU behavior, frame/replay conformance, audio synchronization, worker crash recovery, cancellation, tenant cache boundaries, and playable exports on a declared hardware/browser matrix. Compare bounded visual review against a single-generator baseline. Record exact dependencies and notices before marking this ADR accepted.
