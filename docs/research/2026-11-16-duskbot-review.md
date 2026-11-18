# DuskBot: product, source and subscription integration review

Date: **16 November 2026**. [DuskBotAI/duskbot](https://github.com/DuskBotAI/duskbot), revision [`058c67f88c390783bdd1054128b31419209c8167`](https://github.com/DuskBotAI/duskbot/tree/058c67f88c390783bdd1054128b31419209c8167). **9,341 tracked files**, inspected selectively in a temporary checkout. No installation, login, upstream execution or tests occurred.

## Verdict

The strongest useful addition is explicit support for **existing AI plans and signed-in agent engines**, alongside Zincc's API-key and local-model routes. The user requested this as an addition, so the proposed feature is now documented in [connected AI engines](../modules/connected-ai-engines.md). Implementation still awaits approval.

DuskBot is **source-available under PolyForm Noncommercial 1.0.0**, not permissively open source as a whole. Its licensing document distinguishes original Apache-2.0 OpenMausBot code from changes made in the fork. Do not copy modified DuskBot files into a prospective commercial Zincc product without an applicable license. Use original official protocols, independently designed contracts and separately verified upstream sources instead.

## Product and website review

The README describes an Electron/React client, Node server, per-bot configuration/memory, routines, shared rooms, MCP/app connections and local or remote execution. These broadly reinforce Zincc's existing direction. The Apps & AI section describes signed-in engines running their own loops with DuskBot tools supplied over MCP, while API/local-model routes use its own loop.

The live `https://dusk.bot/` and `/docs` pages could not be retrieved by the web tool. The repository's `apps/site/public/landing/index.html` was inspected as website source, including its AI-plan positioning and sample chat/usage UI. This is not a rendered/live-site verification. The landing page labels its example bots, chats and numbers as samples.

## How the subscription feature actually works

| Route | Source inspected | Interpretation |
| --- | --- | --- |
| Codex engine | Selected `server/drivers/codex.ts` startup/configuration sections | Runs Codex app-server, exchanges structured messages, resumes threads and handles completion/approval events. This is a provider agent runtime, not just a generic model API. |
| Direct ChatGPT plan permission | Selected `chatgpt-plan-auth.ts`, Codex plan arguments and consent tests | Separate OAuth identity and plan-use permission, issued client registration, host identity, token handling and model discovery. This is distinct from simply reusing a Codex auth file. |
| Claude Code | Selected `claude.ts` launch/tool restrictions and `claude-login-auth.ts` | Runs the CLI with streaming JSON, attaches scoped MCP tools, and launches the CLI's own sign-in flow. Native account authentication remains provider-owned. |
| Grok Build | Selected `drivers/acp/grok.ts` sign-in, spawn and scope checks | Uses the `grok` agent over ACP with its sign-in flow. The separate complete `drivers/grok.ts` file is an xAI API-key adapter. These are different routes and billing arrangements. |

Conceptual flow: the user authenticates through a provider-supported route; Zincc starts the selected engine on an enrolled device or qualified worker; that engine receives a task and permitted tools; tool calls are checked by Zincc; events and results return to the workbench. A coding engine can call noncoding MCP tools too, subject to its supported interface and demonstrated task quality. Its name does not restrict the platform to coding, but general-purpose success must be evaluated.

## Official-source verification, not just repository claims

- **ChatGPT plan access:** current OpenAI documentation offers optional plan-use consent for eligible requests and accounts. Its open-source/local flow is available separately from paid/remotely hosted-app access, which requires the relevant program path. Identity alone does not permit inference or expose ChatGPT history. See [plan-use documentation](https://developers.openai.com/siwc/token-sharing-open-source) and [quickstart](https://developers.openai.com/siwc/quickstart). Zincc's product license/distribution remains undecided, so availability cannot be promised for every edition.
- **Codex:** official [authentication](https://learn.chatgpt.com/docs/auth) and [app-server](https://learn.chatgpt.com/docs/app-server) documentation establish subscription sign-in and integration interfaces. Ordinary API billing, CLI execution and direct ChatGPT plan permission are separate capabilities. Production automation must use the supported authentication arrangement for its deployment.
- **Claude Code:** current [legal/authentication guidance](https://code.claude.com/docs/en/legal-and-compliance) explicitly permits end users to sign into an unmodified Claude Code binary, including qualifying hosted arrangements under the stated conditions. It separately prohibits third-party collection/intermediation of Claude.ai credentials and routing arbitrary product requests through those credentials. Zincc should integrate the official binary and its flow, not implement a replacement Claude.ai OAuth client. The [plan usage help page](https://support.claude.com/en/articles/15036540-use-the-claude-agent-sdk-with-your-claude-plan) describes billing behavior; it is not blanket permission for every third-party authentication design.
- **Grok Build:** official [Build documentation](https://docs.x.ai/build/overview) supports browser authentication, headless use and ACP integrations. [Grok usage documentation](https://docs.x.ai/grok/faq) describes plan allowances across products; the [API account FAQ](https://docs.x.ai/console/faq/accounts) distinguishes API billing. Qualify account eligibility and adapter version; a Grok subscription is not a general xAI API key.

## Additional product ideas worth considering

The direct ChatGPT plan route also has [explicit preview limits](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations), including unavailable hosted tools and audio/video APIs. Do not market a connected plan as including every Zincc modality. Tool execution and model eligibility are qualified independently.

1. **A unified AI-connections panel:** show connected account, execution device, available models, last verified status, plan/API/local billing and limits. This belongs in the requested new feature, not an opaque API-key form.
2. **A readiness check for each agent:** before starting, explain missing tools, sign-in, model capabilities or an offline device, and provide the relevant setup action. A connected badge is not proof that inference or tool execution works.
3. **Plan-aware scheduling:** queue background jobs when an allowance is exhausted; expose reset information only when the provider supplies it. Never silently switch to paid API use or another person's subscription.
4. **Import preview:** optional later import of user-exported instructions, skills and memory, with a mapping/diff and disabled permissions until reauthorized. Existing portability scope can gain this acceptance journey. Imported credentials and untrusted instructions must not become grants.

Rooms, editable memory, routines and computer takeover already exist in Zincc's proposal. They are useful UX references, not missing foundations. DuskBot's Windows installer proves distribution intent; it does not by itself establish safe control of every native Windows app. This review saw both container and computer-driver Windows paths/test inventory, but did not qualify native desktop behavior.

## Inspection coverage and limits

- Read README through web output; root LICENSE through its principal terms; complete `docs/LICENSING.md`; account verification documents and custom-engine documentation excerpts; extracted website-source text.
- Source: selected Codex app-server/plan launch, ChatGPT plan-auth setup/storage/locking, Claude launch/MCP restrictions/sign-in parsing, Grok ACP sign-in/launch/scope checks; complete small Grok API driver.
- Tests: selected ChatGPT consent/account-isolation assertions; Grok device URL/code-validation excerpt; account-verification records and test inventory. Those fixtures use synthetic accounts; neither their existence nor upstream reports establishes a successful live login here.
- Not fully audited: all 9,341 files, every provider/connector, end-to-end permission enforcement, complete token lifecycle, desktop/relay security, native Windows implementation, imports or commercial licensing eligibility.

**Direct code or skills copied:** none. **Implementation basis recommended:** official interfaces and independent Zincc adapters. **Ideas retained:** existing-plan connectivity, provider/account/device clarity, readiness checks and quota-aware scheduling. **Not adopted:** broad claims that every subscription, OS, engine tool or deployment is interchangeable. **Status:** targeted research complete; product addition remains a proposal awaiting build approval.
