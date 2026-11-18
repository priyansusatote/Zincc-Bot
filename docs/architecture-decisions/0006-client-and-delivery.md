# ADR 0006 — A web workbench with staged delivery

The web shell and generated REST client were adopted in P1.4. Native packaging and final visual polish await their respective phases. [Workbench and qualification](../modules/workbench.md).

## Problem

Zincc needs a premium cross-device experience, but visual polish cannot substitute for reliable actions. The user wants main capabilities completed before final UI polishing and wants to learn one implementation task at a time.

## Decision

Use React/TypeScript/Vite for the interactive authenticated workbench and Spring APIs. Establish accessible components, task states, and design tokens early; refine the complete experience in P11. Keep a responsive web baseline. The user's 14 November clarification includes native desktop/mobile clients in the full scope, now tracked in P13. Complete one authorized task with an A–L report before starting another.

## Alternatives

- Next.js as an additional application backend: useful for server rendering/public content, but not necessary for the core authenticated workbench and can duplicate Spring responsibilities. Revisit for a distinct public site if needed.
- Native apps first: better OS integration but multiplies client work before workflows stabilize.
- Fully polished static prototype first: can explore appearance but does not demonstrate execution reliability.
- Entire platform in one batch: prevents meaningful verification, learning, and scope control.

## Trade-offs and consequences

Initial UI will be functional and coherent rather than final. Desktop/browser integrations require the later P13 bridges. Source/evidence and action states shape layout early. Performance, accessibility, and error handling remain requirements throughout, not late decoration. With expansion scope included, final P11 polish follows P13–P16; full P12 qualification follows that. The phase numbers are stable identifiers, not the revised execution order.

## Validation

Keyboard/screen-reader use, reconnect/task-state clarity, source inspection, approval comprehension, narrow-screen layout, and end-to-end journeys with real failures.
