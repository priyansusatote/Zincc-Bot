# ADR 0009: Separate model connections from connected agent engines

Status: **Proposed**. Date: 16 November 2026. User requested existing AI subscription/CLI use as an addition while reviewing the plan; application implementation remains unapproved.

## Problem

Users want their existing AI plan or signed-in coding agent to perform Zincc tasks. Treating every such connection as a generic LLM endpoint would lose the provider engine's tool loop, approvals, lifecycle and authentication constraints. Treating the external engine as the entire platform would lose Zincc's durable runs, identity and action authority.

## Decision

Retain the existing model-provider and framework contracts. Add a distinct agent-engine adapter contract for official CLI/app-server/ACP integrations. A supported direct plan-use OAuth route is a model connection; a connected agent runtime owns its inner loop. Spring and Temporal retain platform state, grants, outer orchestration and evidence. The execution host owns the qualified credential lifecycle. Full behavior and eligibility gates are in [connected AI engines](../modules/connected-ai-engines.md).

Native tools outside Zincc's MCP gateway require independent engine and sandbox controls. Qualify managed enforcement per adapter; do not infer it from a generic protocol or prompt. Preserve account and deployment boundaries, and never silently switch plan use to API billing.

## Alternatives

- **API keys and local models only:** simpler, but omits the explicitly requested account/engine experience.
- **Use subscription tokens as generic API keys everywhere:** conflates distinct provider contracts, entitlements and credential custody; rejected.
- **Adopt the DuskBot backend or fork its adapters:** conflicts with Java platform ownership and creates licensing/maintenance questions; rejected. Its source-available license is not a blanket commercial reuse grant.
- **Let each engine control platform state and all tools:** makes permissions, retries and receipts inconsistent across providers; rejected.

## Trade-offs and consequences

Users gain engine choice and potentially lower setup friction. Costs include protocol/version maintenance, native tool enforcement, per-account session isolation, platform-specific packaging and provider eligibility checks. Not every route or edition will support identical models, media, streaming or unattended execution. A local engine binary can still use a remote model.

Documentation-only addition: no provider accounts connected, no upstream source copied and no installed engine. Current official-source evidence is linked from the [DuskBot review](../research/2026-11-16-duskbot-review.md); it must be refreshed before release.

## Verification before acceptance

Run adapter contracts for lifecycle/events, scope enforcement, identity isolation, consent/revocation, cancellation, allowance exhaustion and safe recovery after ambiguous side effects. Qualify native Windows and Linux separately. Test credentials must not enter logs or fixtures; mark simulated and real provider results separately. Accept this ADR only with the relevant scoped task and evidence, not from the existence of a repository example.
