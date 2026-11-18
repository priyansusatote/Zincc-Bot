# Existing AI plans and connected agent engines

Date: **16 November 2026**. User-requested **additive proposed feature**. Build approval pending. Complements the [native harness/framework design](agent-runtime.md); it does not replace API-key, local-model, ADK, LangGraph or PydanticAI support. [Research and provider evidence](../research/2026-11-16-duskbot-review.md); [proposed ADR](../architecture-decisions/0009-connected-ai-engines.md).

## Product promise

Users can connect supported existing AI accounts or an installed, signed-in agent engine and choose it for their Zincc agents. They can still use API keys, organizational gateways and local models. Each connection clearly states what is available on that account, where execution happens, what consumes subscription allowance versus separately billed usage, and which tools Zincc can govern.

This is not a promise that every consumer subscription can be converted into unrestricted API access. Account entitlement, supported interfaces, provider terms, edition and model capabilities determine availability. Neither provider sign-in nor a plan grants access to all existing chats, connectors or the user's entire machine.

## Two distinct integration contracts

| Contract | Owner of the agent loop | Examples | Zincc responsibilities |
| --- | --- | --- | --- |
| Model connection | Zincc native harness or selected framework | API key, local inference, eligible direct ChatGPT plan-use route | Context, loop, structured calls, action policy, checkpoints and completion evidence |
| Agent-engine connection | Connected provider engine | Codex app-server, unmodified Claude Code, Grok Build ACP, qualified custom ACP engine | Task delegation, engine/session lifecycle, normalized events, bounded MCP tools, outer workflow and independent action authorization |

Model ID, engine type, authentication mode, execution location and billing source are separate fields. A framework adapter is also not the same thing as a provider account. Do not nest several independent agent loops around the same task without defining exactly which one owns progress and recovery.

## Proposed user flow

1. Open **Connections → AI** and choose a supported plan, API provider, local endpoint or installed engine.
2. Select this device or a qualified enrolled runner. Discover executable/version and capabilities without running an arbitrary command supplied by a webpage.
3. Complete the provider's supported sign-in/consent flow. Display the verified account and granted capabilities; do not label file presence as verified authentication.
4. Run a small, disclosed connection check. Distinguish successful login, model discovery, inference and governed tool execution.
5. Assign the connection to one or more agents. Display permitted tools, execution environment and allowance/cost source before running.
6. On expiry, revoked access, exhausted allowance or unsupported model, pause with a precise recovery action. A user may explicitly choose another qualified route; do not silently incur API charges.

Separate optional identity sign-in from linking an AI connection to an already authenticated Zincc user. Provider identity cannot bypass workspace membership, enterprise SSO or connector permissions.

## Provider qualification targets

| Target | Proposed supported path | Gate before shipping |
| --- | --- | --- |
| ChatGPT plan use | Official Sign in with ChatGPT plan-use protocol; eligible local/open-source edition first | Zincc distribution eligibility, actual account consent/model discovery, supported inference capabilities, secure token lifecycle; paid/remote edition requires its applicable program access |
| Codex | Official app-server/CLI interface and supported authentication on the execution host | Versioned protocol suite, native tool controls, approval/cancel behavior, session recovery and deployment-specific authentication qualification |
| Claude Code | Unmodified official CLI, provider-owned login and structured streaming/MCP interface | Current provider conditions, each user's own account, no credential extraction or replacement OAuth implementation, scoped native-tool enforcement |
| Grok Build | Official CLI/ACP interface with its supported authentication | Verify account entitlement, protocol/version, MCP and native-tool restrictions, Windows and Linux behavior independently |
| Other engines | Qualified ACP or other documented integration | No blanket compatibility based on an engine name; own adapter evidence and provider eligibility |

Provider evidence was checked on 16 November 2026; recheck at implementation and release. Exact CLI/SDK versions are not locked by this planning task. A website login cookie is not an integration contract. Where a supported route is unavailable, expose API/local alternatives without substituting them automatically.

**Direct ChatGPT plan-use limitation:** the official route currently has [preview restrictions](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations). It does not provide hosted image generation, file search, Code Interpreter, native computer use, hosted MCP or audio/video APIs. Zincc can supply separately governed tools and its own retrieval, but those services have their own execution/cost requirements. Use the required streaming/no-server-storage request contract and adapter-specific history handling. Treat this route as an opt-in preview until qualified; it is not a reason to downgrade the stable core baseline. Other provider/API routes continue to serve modalities this route lacks.

## Execution and security architecture

Spring owns workspace identity, grants, budgets, engine registry, durable runs, task receipts and policy. Temporal owns dispatch and durable waits. A local companion or isolated execution worker hosts the engine process and its account store. The browser renderer receives safe connection status and sign-in links, not refresh tokens. Secrets remain in the approved host's protected storage; POSIX file modes alone are not a Windows ACL guarantee.

For direct OAuth plan use, implement fresh state/nonce/PKCE, issuer/audience checks, consent verification, allowlisted callback handling, protected per-account storage, serialized rotating refresh and explicit revocation. Follow the provider's official contract; prefer a qualified official SDK where available. For provider-owned CLI login, let the unmodified CLI own its credentials. Do not mix these two credential lifecycles or synchronize another user's home directory.

Each engine receives a short-lived tool session bound to tenant, user, agent version, run and execution generation. Expose only permitted Zincc tools. Every tool invocation independently checks current grants and approved intent. MCP alone does not prevent native shell, file or network tools from bypassing Zincc: restrict those through supported engine controls plus OS/sandbox policy. An adapter that cannot enforce a required restriction fails qualification for that managed profile. An explicitly unmanaged local mode must be labelled as such, not marketed as centrally enforced.

Provider-process failure does not mean the task had no effect. Store external session/thread IDs, normalized completion states, usage and receipt references. Resume only where the adapter proves safe continuity. Before restarting an ambiguous turn, reconcile actions through the ledger. Cancellation first withdraws further dispatch, then stops the engine and descendants; already committed actions remain visible. Account changes invalidate relevant warm sessions and pending approvals; signing out does not itself prove an in-flight process stopped.

## Individual, team and hosted behavior

- **Individual/local:** user-owned login and selected folders/apps, device-local credentials, clear awake/online requirement for scheduled work. Cloud-backed engines still transmit selected task data to the provider.
- **Team:** separate per-member engine accounts or an explicitly qualified organization arrangement. Sharing an agent does not share a personal subscription, tokens or authority. Billing/source and delegated identity remain visible.
- **Self-hosted:** customer-controlled execution host and data policy. Support remote callbacks/device flows only where officially supported. No automatic copying of credentials from a laptop to a server.
- **Zincc-hosted:** provider program eligibility and isolated account lifecycle are separate release gates. Do not assume the local subscription path permits SaaS usage pooling/resale.

Usage accounting distinguishes provider-reported tokens, reported allowance/reset, estimated API cost and unknown values. Unknown remaining allowance is not zero or unlimited. Rate-limit by account as well as tenant/run so multiple agents cannot independently consume an unbounded shared allowance. Keep background jobs queued or paused on exhaustion, with an explicit fallback policy. Local-only requests exclude every route that sends data to a cloud provider, even if the engine binary runs locally.

## Acceptance and teaching focus

- Login consent denied, identity-only success, expired/revoked auth, wrong account and account switch during an active run.
- Unknown executable/version, unsupported model/tool, native-tool bypass attempts, inherited global MCP/config and private account separation.
- Simultaneous refresh and concurrent agents on one allowance; exhaustion without silent paid fallback; no fabricated exact usage percentages.
- Windows credential ACLs, browser callback validation, renderer/log redaction and process-tree cancellation.
- Restart after external action, lost completion event, duplicate tool request, stale approval and reconcilable unknown outcome.
- Offline runner, sleeping laptop and remote disconnection; scheduler timezone and missed-run policy stay Zincc-owned.
- Fixtures first; real provider login/inference checks only with configured user accounts. Record fixture versus live results explicitly.

Teaching topics: OAuth/OIDC versus API authentication; model versus agent-engine interfaces; MCP versus ACP versus AG-UI; subprocess supervision; inner/outer workflow ownership; credential rotation; admission control; idempotency and authoritative action receipts.

## Delivery boundaries

P9.5 defines the common engine/account contract and offline fixture. P9.6 qualifies the direct ChatGPT plan route, P9.7 Codex, P9.8 Claude Code and P9.9 Grok Build, each as a separately authorized task. P3 policy/ledger and relevant P6 execution controls are prerequisites; consumer desktop packaging depends on P13. A local test adapter can precede the polished desktop app, but cannot claim supported end-user installation until packaging and OS tests pass.

The provider choices are initial targets, not a closed catalogue. No implementation, provider authentication, live test or dependency installation has happened.
