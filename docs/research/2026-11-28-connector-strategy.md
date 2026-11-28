# Connector strategy and agent product gaps

28 November 2026. Research recommendation. Adoption depends on a live trial.

## What a connector platform would solve

The implemented MCP briefing client negotiates with one curated server. Gmail, primary Calendar and public Slack channel intake have separate adapters. The task agent has its own task-list tools. The missing piece is a common way for a user's agent to discover an authorized operation, execute it on the right account, inspect the result and choose its next step. The [signed-in audit](2026-11-27-live-capability-audit.md) documents the current boundaries.

**Select Composio Hobby for the hosted pilot.** Use the recommended Sessions API with its direct tools preset. Allow only the two selected GitHub issue operations and pin the connected account. Spring owns the workspace/member mapping, repository restriction and approval before writes. Composio labels the standalone direct-execution API legacy. Keep native adapters for existing feeds and disable hosted connector execution in local-only workspaces. [Quickstart](https://docs.composio.dev/docs/quickstart) · [Sessions and direct tools](https://docs.composio.dev/docs/sessions-vs-direct-execution) · [Account selection](https://docs.composio.dev/kb/guide/mcp-tool-router-sessions)

**Setup and quota.** Prepare one server-side project key, `COMPOSIO_API_KEY`, in the ignored root `.env`. The user will separately authorize GitHub through a Connect Link during the pilot. On the developer platform Hobby plan, 100,000 monthly tool calls and 50,000 trigger events include managed-app sublimits of 20,000 calls and 10,000 events. Connections through your own apps, API keys or MCP are unlimited. Managed apps include 1,000 connected accounts. Three team seats are included. Hobby custom toolkits are shared within the Composio project, so private user-uploaded toolkits need separate treatment. The pilot has not executed a Composio tool. [Platform pricing](https://composio.dev/pricing/platform)

**Local engine choice.** A bot may select a hosted API, a local model or a supported signed-in CLI. The paired device owns its CLI login. A model subscription and an app OAuth grant serve different purposes. The first CLI-to-app integration should request a narrowly scoped operation through Spring and return its receipt. Existing paired engine runs accept text with external tools disabled. Nod's prepared suggestions, source-linked memory and outcome-based tests are product precedents. [Nod source review](2026-11-16-nod-review.md) · [Zincc engine qualification](../modules/phase-nine-builder.md)

**Evaluate Nango if continuous sync or customer hosting leads the decision.** Nango offers tools, OAuth, webhooks and sync functions, including customizable integrations. Compare the rights and capabilities of the precise deployment edition, credential custody, deletion propagation and operating costs. An installed self-hosted connector still needs network access to call an online app. [Nango documentation](https://nango.dev/docs/getting-started/intro-to-nango) · [Repository and deployment options](https://github.com/NangoHQ/nango)

**Keep a direct API or provider MCP option.** A vendor's own interface can make sense for an important capability, offline data or specialized permission requirements. Maintaining each provider's pagination, retries, OAuth and schema changes takes engineering time. Expose the same internal operation contract regardless of its implementation.

## Proposed trial

Use one disposable GitHub repository and two workspace members. Ask for a list of open issues and an explicitly approved new issue. Have chat and a saved agent invoke the same dispatcher. Derive the user identity server-side, pin the account and repository, restrict operations, validate arguments, and preserve the approval and result receipt in Zincc. An authorization grant belongs to a member within a workspace.

Compare the issue list with the provider, follow the created issue URL, test cross-member access, revoked consent and a timeout after submission. Reconcile an ambiguous write before retrying. Measure setup time, latency, billable calls, bytes sent outside the workspace and cost per completed job. Require correct reads and exactly one confirmed issue for the approved write. These measurements decide whether to extend the adapter. No credentials are needed for this design review.

## What will make the product feel different

Grok Bot describes sorting mail and drafting replies. Meta describes handing Muse a multi-step job that can continue in the background. The relevant benchmark is a finished job users can verify. Compare that experience with the flows below. [Grok Bot workflows](https://docs.x.ai/grok-bot/use-cases) · [Meta Muse](https://ai.meta.com/muse/)

| Priority | Gap in Zincc | Research question and acceptance example |
| --- | --- | --- |
| 1 | Users pick a different screen for chat, research, routines and agent actions | Can one ordinary request select tools and accounts, ask only for missing information and show source links? Try a morning briefing with a calendar conflict and a follow-up. |
| 2 | Review teams and durable task runs exist, but cross-app plans are narrow | Can a run read two sources, use the first result in its next action, pause for a correction, survive a restart and resume without duplicating a write? |
| 3 | Saved routines use a small fixed skill set | Can “repeat this each weekday” turn a successful run into a scoped routine with a visible next run, pause control and notification only when something changes? |
| 4 | Working API models and subscription runners use separate paths | Can a user tell which model is online, whether it supports the job, who receives the data and what consumes their allowance? Test offline, throttled and revoked accounts. |
| 5 | Search is limited to English Wikipedia. Voice actions and general computer control remain targets | Test a time-sensitive research question, interrupt a spoken request before it acts, and complete a benign Windows application task with a user-visible handoff. |
| 6 | PDF provenance, private memory and bounded reviewers work in separate flows | Can an agent remember a user's corrections, cite the exact report page and deliver a reusable artifact? Compare one agent with parallel reviewers on quality, latency and cost. |

Start with priorities 1 and 2 on the same task. A broker can accelerate the tool integration. Zincc still owns action selection, verification, recovery and the user experience.
