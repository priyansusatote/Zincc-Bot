# Scope and connector clarification — learning report

Date: **14 November 2026**. Documentation-only revision before plan approval.

## A. What did we build?

An explicit action-product scope, a named connector catalogue, and expansion epics for the features the user confirmed they also want. No application behavior was built.

## B. How does it work internally?

The proposed product turns a request into a permissioned run. A selected agent implementation chooses steps; tools perform external operations; evidence and receipts establish the result. Persistent agent identity, a task run, and its temporary execution environment are separate objects. Several agents can cooperate without sharing all account access.

## C. Architecture and design decisions

Keep Spring Boot as the control plane, the Zincc harness as the default agent runtime, Temporal as durable progression, and ADK/LangGraph/PydanticAI as selectable adapters. Stacking all frameworks in each call would add redundant state and retries. Add native clients and domain workflows as later epics to preserve small implementation boundaries. Keep final polish after the enlarged functional scope.

## D. Important technologies and concepts

A connector is a capability contract, not just an OAuth login. Its supported reads/writes, events, error behavior, and receipt verification determine what an agent can actually do. Browser access does not guarantee universal service support. Durable workflows preserve progression across waits/restarts; external side effects still require idempotency and outcome reconciliation. Without durable progression, Zincc would need to build comparable recovery and scheduling mechanisms itself.

## E. Real-world use cases

Chief-of-staff briefings, CRM research, ATS coordination, support updates, bug reproduction, expenses, permit coordination, subscriptions/returns, household work, and supported bookings/orders now have explicit acceptance boundaries in the scope document. A use-case label becomes a deliverable only when its end-to-end behavior is verified.

## F. Edge cases and failure scenarios

Provider access may be unavailable; expose that capability gap. A booking can time out after confirmation; reconcile before retry. Two agents can target the same record; serialize or reject conflicting revisions. A device may sleep; apply the declared missed-run policy. A connector may read files but lack write permission; never present that as complete action support.

## G. Trade-offs and limitations

The expanded product increases integration maintenance, client work, operational cost, and test coverage. A finite catalogue is reviewable, but its provider access is not guaranteed. Native clients cannot be treated as unrestricted permission to control every mobile/desktop app. Several domain epics must be decomposed before implementation.

## H. Document walkthrough

- [Scope and connectors](../product-scope-and-connectors.md): action expectations, workflow map, named catalogue and boundaries.
- [Roadmap](../phases/roadmap.md): P13–P16 additions and revised dependency order.
- [Plan](../PLAN.md): full-scope clarification.
- [Live module](../modules/live-and-connectors.md): references the canonical catalogue.
- [Client ADR](../architecture-decisions/0006-client-and-delivery.md): updated proposed client/delivery decision.
- [Status](../status.md): proposal remains unapproved for implementation.

## I. What should I remember?

1. An agent can compose tools beyond predefined templates.
2. Persistent identity does not require permanent active compute.
3. One chosen agent implementation can serve a run; all frameworks need not be stacked.
4. Durable orchestration does not make external writes exactly once.
5. A named connector requires tested operations and verified access.
6. Adding scope changes dependencies and release criteria.

## J. Interview preparation

**Why separate an agent definition from a run?** One reusable identity can own many isolated executions with independent budgets/history.

**Why do Temporal and LangGraph coexist?** Temporal can own cross-service durability while a chosen adapter owns inner graph state; retry/checkpoint ownership must be explicit.

**How do you prove an action connector works?** Verify a permitted external change and receipt, plus revocation, retries, duplicates, and ambiguous outcomes—not merely successful authentication.

## K. Learning exercise

Choose a subscription-cancellation workflow and list the required reads, writes, permissions, receipts, and ambiguous failure states. Compare an API implementation with a browser implementation.

## L. Completion status

Scope/catalogue/roadmap clarification completed. Validation passed across **23 Markdown files and 66 local links**, with no broken local targets, unbalanced fences, trailing whitespace, or missing A–L sections. Zero application files exist. No runtime tests or API integrations were executed. Remaining limitations: target-provider access and compatibility are unverified; expanded epics need task decomposition. Next implementation task remains P1.1, awaiting explicit authorization.
