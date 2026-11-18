# Action product scope and connector catalogue

Updated: **16 November 2026**. Status: **reviewed proposal; awaiting plan approval; no application implementation**. See [the final review](PLAN-REVIEW.md).

## User clarification

The user wants a complete action-taking flagship product, including the previously deferred native clients, desktop control, meeting/screen capabilities, avatar option, shopping/booking actions, and public plugin marketplace. “Later” now specifies delivery order within the requested full scope. It does not silently remove those capabilities. Each implementation task still needs explicit authorization.

Zincc must accept open-ended digital work in natural language and compose available tools to perform it. Templates are convenient starting points, not the only supported tasks. Completion requires a verified result, artifact, or action receipt. Missing access, unsupported operations, and uncertain outcomes must be visible; a text claim alone is not completion.

The supplied [Perplexity Teammates page](https://www.perplexity.ai/en-GB/hub/products/teammates) describes persistent, collaborative agents for ongoing work. The original unprefixed URL failed in the research tool; this official regional page was readable. This is a behavior reference, not a backend dependency. [Grok's use cases](https://docs.x.ai/grok-bot/use-cases) also distinguish assistants, recurring specialist bots, and cooperating teams.

## Multiple agents

**User-defined work is the core product.** The examples in learning notes and the workflow packs below are illustrative acceptance journeys and optional templates, not a whitelist of jobs. Users can describe a new task, create an agent from scratch, attach qualified tools/skills/MCP integrations and knowledge, and choose its trigger and execution environment. Unsupported capabilities must be identified and enabled through a qualified integration rather than simulated with a confident answer. No agents are implemented yet.

**Additional proposed AI connection option:** use an eligible existing AI plan or signed-in engine such as Codex, Claude Code or Grok Build, as well as API/local routes. See [connected AI engines](modules/connected-ai-engines.md) for provider-specific availability, account ownership and billing boundaries.

Users can create general-purpose agents and specialist agents. Each definition owns its name, responsibility, instructions, framework, model policy, memory scope, tools, credentials/grants, triggers, budget, and execution environment. The same platform capabilities can be enabled for multiple agents; a role name does not hardcode a limited script.

An agent can handle several runs, spawn bounded child work, collaborate through explicit messages/artifacts, and accept steering while work continues. Agents do not automatically share every secret, private conversation, or browser account. Standby identity and memory persist without requiring an idle container forever.

Shared agents use explicit delegation and output audiences; invoking someone else's agent does not grant their personal account access. The personal roster/chat view grows into team projects and admin views without forcing framework choices during onboarding. Hosted, hybrid, self-hosted-online and offline profiles expose their actual capabilities; an offline assistant cannot perform online connector work until connectivity is restored.

## Requested workflow acceptance map

| User workflow | Required completed behavior | Qualification boundary |
| --- | --- | --- |
| Daily chief of staff | Read fresh mail/chat/calendar data, prioritize, create briefing and permitted follow-up actions | Show unavailable/stale sources |
| Sales prospecting | Research authorized sources, score against explicit business criteria, update CRM, draft/send within grants | Source access and outreach permissions |
| Recruiting scout | Search authorized candidate sources, reconcile ATS records, summarize job-related evidence, coordinate interviews/messages | Separate assistive work from hiring decisions; validate ATS access |
| Support triage | Gather ticket/account context, investigate, classify, draft or send approved replies, update tickets | Refund/account-change operations separately scoped |
| Bug reproduction | Exercise staging, collect logs/screenshots, reproduce failures, create issue or reviewable patch | Authorized test environment; production changes separately granted |
| Expenses | Extract receipts, reconcile records, create entries, flag missing information, follow up | Prevent duplicate entries; accounting write access |
| Permits and inspections | Read documents/portal data, prepare and submit permitted forms, request/book available inspections, coordinate suppliers | Jurisdiction-specific portals, authentication, signatures and submission rules |
| Multi-bot work | Coordinate independent research/content/developer tasks and merge evidence | Shared-resource conflicts and total budget enforced |
| Trading-related work | Research, alerts, simulation/paper trading, then explicitly specified order workflows through a qualified broker | Separate real-order connector, account permissions, limits and order reconciliation |
| Subscription and return audits | Track renewals/refunds, prepare claims, execute supported cancellations/returns, verify receipts | Merchant-specific capability; some steps require user handoff |
| Home and family | Maintain records/reminders, coordinate calendars and maintenance bookings; optional device actions | Household sharing and device-specific grants |
| Shopping and travel | Monitor prices, compare choices, build baskets, perform supported bookings/orders, retain confirmations | Final item/date/price/destination constraints; provider access and payment flow |
| Code-generated scenes and video | Turn a prompt or HTML project into an editable interactive scene and camera-directed video, with visual review and export | Tested renderer/GPU profile, source rights, sandboxed execution and measured quality; see [Scene Studio](modules/scene-studio.md) |

All rows are in requested product scope. None means arbitrary operations will work on every website. If a provider denies automation or requires a human identity/signature step, Zincc exposes that boundary instead of pretending success.

## Named connector targets

This is the finite **proposed connector catalogue for this plan revision**, not a claim that every operation is currently supported by every provider. Availability, OAuth review, commercial agreements, pricing, region, scopes, and API terms must be checked for each connector. No service is connected or implemented now.

Build one connector at a time through a common contract. Prefer a supported API or qualified MCP implementation. Browser automation is a separate tested path, not a universal replacement for unavailable API access.

### Wave A — first useful product

| Family | Named targets | Initial jobs |
| --- | --- | --- |
| Email | Gmail | Read/search, draft, permitted send, labels |
| Team chat | Slack | Selected channels/threads, search/events, permitted messages |
| Calendar | Google Calendar | Availability, events, permitted scheduling |
| Workspace files | Google Drive, Google Docs, Google Sheets, Google Slides | Retrieve/create/update supported files and artifacts |
| Development | GitHub | Repository/issues/PRs and reviewable changes |
| Knowledge | Notion | Read/search and permitted page updates |

### Wave B — broader individual and team work

| Family | Named targets |
| --- | --- |
| Microsoft workspace | Outlook Mail, Outlook Calendar, Microsoft Teams, OneDrive, SharePoint, Microsoft Excel |
| Files and knowledge | Dropbox, Box, Confluence |
| Projects/development | GitLab, Jira, Linear, Trello, Asana, ClickUp |
| Operational signals | Sentry, Datadog, PagerDuty |
| Messaging | Discord, Telegram, WhatsApp Business Platform |
| Scheduling/meetings | Calendly, Zoom, Google Meet; Teams meeting capabilities through the Teams integration |
| Design | Figma |
| Databases | PostgreSQL, MySQL, SQL Server, BigQuery, Snowflake |
| Object storage | Amazon S3, Google Cloud Storage, Azure Blob Storage |

Database connectors start with restricted read access; model-generated SQL cannot modify arbitrary production tables. DOCX/PPTX creation is an artifact capability; file storage access does not imply live editing of every Office application. Meeting scheduling, transcript access, and joining/speaking are separate capabilities with separate tests.

### Wave C — business and daily-life workflows

| Family | Named targets |
| --- | --- |
| CRM/sales | HubSpot, Salesforce, Pipedrive, Zoho CRM |
| Support | Zendesk, Intercom, Freshdesk, Help Scout |
| Recruiting/ATS | Greenhouse, Lever, Ashby, Workable |
| Accounting | QuickBooks Online, Xero, Zoho Books |
| Expenses/payments | Expensify, Ramp, Stripe |
| Commerce | Shopify, WooCommerce |
| Home automation | Home Assistant |

A merchant's Stripe/Shopify connection does not grant access to a consumer's purchases at unrelated merchants. Personal expense/return workflows may instead use receipts, email, uploads, or an authorized merchant browser session.

### Wave D — targets requiring separate access qualification

| Family | Named candidates | Before implementation |
| --- | --- | --- |
| Flights/travel | Amadeus, Duffel, Expedia Group APIs | Identify applicable product, booking rights, account/partner access, payment and cancellation behavior |
| Grocery orders | Instacart Developer Platform | Verify supported cart/checkout path, region and integration access |
| Permits | Accela, OpenGov, specific municipal portals | Select jurisdiction and validate actual exposed operations/credentials |
| Brokerage | Alpaca, Interactive Brokers | Begin with simulation/paper environment; qualify supported explicit order operations and receipt reconciliation |

These provider names are evaluation targets, not guaranteed access. If a target is unavailable, record the limitation and propose a qualified alternative rather than advertise an inactive connector.

### General extension interfaces

Remote MCP, sandboxed local MCP, OpenAPI/HTTP tools, signed webhooks, RSS/Atom feeds, SFTP, selected local folders, browser sessions, and the customer Docker/VM runner. Add WebDAV/CalDAV only when a concrete self-hosted use case warrants it. Model-provider adapters are a separate catalogue from application connectors.

## Connector acceptance contract

Catalogue status is **planned → experimental → qualified → deprecated/blocked**, with a reason and tested version. All current entries are planned. A user's disconnected/expired/insufficient-scope account is a separate availability state. Qualified read support does not imply qualified writes. Provider-access blockers must remain visible and cannot be counted as delivered integrations.

Every enabled connector publishes its exact supported reads/writes and account requirements. Test connection/revocation, permissions, pagination, rate limits, retries, event deduplication, real permitted writes, and outcome verification. Trace provider request IDs and retain safe receipts. Read-only support must be labelled read-only; a mock demonstration must be labelled a mock.

Current primary evidence for representative interfaces: [Gmail](https://developers.google.com/workspace/gmail/api/guides), [Microsoft Graph](https://learn.microsoft.com/en-us/graph/overview), [Slack](https://docs.slack.dev/apis/web-api/), [HubSpot](https://developers.hubspot.com/docs/api-reference/latest/overview), [Zendesk](https://developer.zendesk.com/api-reference/), [Expedia](https://developers.expediagroup.com/docs), and [Home Assistant](https://contributors.home-assistant.io/core/internals/rest-api/). The remaining named targets still require their task-specific API/access review; this catalogue is not a completed audit of all vendor APIs.

## Runtime responsibilities

Spring Boot owns the product's identity, permissions, budgets, data and action records. The default Zincc harness owns context, planning, model/tool steps, verification and bounded replanning. Spring AI supplies useful Java model integrations behind our adapter interfaces.

Temporal records durable progression, schedules work, handles configured retries, and waits for events or decisions. Model/API calls run in activities; replay uses recorded completed results. External writes still need idempotency and reconciliation. Sources: [workflows](https://docs.temporal.io/workflows), [activities](https://docs.temporal.io/activities).

ADK, LangChain/LangGraph, and PydanticAI are selectable agent implementations behind a shared contract. A single run normally uses one chosen implementation. They are not stacked together on every model call. Temporal owns outer job durability; an adapter may own inner graph/checkpoint state, with one explicit retry owner for each external action.

A harness is the surrounding execution system, not a competing model or one required library. Harness2 is an offline experiment candidate. MCP connects tools; AG-UI serves interface events; A2A can connect external agents. These do not replace product authorization or workflow durability.

## Completion standard for a flagship

Deliver real verified end-to-end workflows, not only screens or prompts. Required evidence includes quality/cost evaluations, concurrency and recovery tests, actual connector receipts, tenant isolation, usable clients, accessible UI, observability, backup/restore, deployment/upgrade instructions, and known limitations. Every scoped implementation task still receives its A–L report and learning-journal entry.
