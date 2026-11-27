# P9 builder, frameworks and engine connections

## Try the builder

Open **Agents and runs** in the signed-in workbench. Edit a definition, select a planning framework and choose the model route separately. **Version history** compares an older revision with the current one. Restoring it saves a new revision and checks the current route, owner and delegate. A run already accepted keeps its original revision.

To enable the SDKs on another installation, build the pinned Linux/amd64 worker with `docker build --platform linux/amd64 -t zincc-frameworks:p9 worker/frameworks`. Set `ZINCC_FRAMEWORK_IMAGE` to the local image ID returned by `docker image inspect --format '{{.Id}}' zincc-frameworks:p9`, migrate the database and start the existing agents workbench. With an empty worker configuration, the builder offers the native planner. Each Docker worker has a bounded lifetime and a read-only filesystem. The container has no network or host mounts.

| Choice | What runs today |
| --- | --- |
| Native | The Spring planning step calls the configured model gateway directly. |
| ADK | An ADK runner invokes a custom agent and returns its final text event. |
| LangGraph | A compiled one-node graph asks for inference and returns a proposed step. The Spring run row persists the observation and step number between planning turns. |
| PydanticAI | A PydanticAI agent uses a function model backed by the same gateway. A strict Pydantic schema validates the resulting proposal. |

The shared protocol permits one inference exchange per planning turn. Spring owns the prompt, token-bearing provider client and grants. Each framework can propose a task-list read, a task creation or a final answer. A proposed write enters the existing approval ledger. Arbitrary customer Python imports, user-supplied graphs and tool plugins have no execution path in this worker.

The external interface uses the authenticated workspace API. `GET …/agents/{id}/versions?before=N` pages owner-visible history. For conversation clients, `GET …/conversations/{id}/ag-ui` projects saved text events as AG-UI SSE. Clients may resume using the last event ID from that endpoint. Each frame carries its own cursor, including multiple frames from one saved event. Re-fetch the authorized snapshot on HTTP 410. This adapter covers text lifecycle and failure events.

## Try an engine connection

### Decisions and device recovery

In **Agents and runs**, **Needs you** lists pending task approvals, team review choices and uncertain task writes that can be reconciled. Select an entry to jump to the original run. Decisions disappear on the next successful panel refresh after they resolve.

In **Connected engines**, select the agent default or a conversation and check **Next engine run**. Save a model for future runs or return the conversation to its inherited default. A conflicting change in another tab causes a refresh before you can submit against the new selection. A ChatGPT plan response can supply a primary usage window. The workbench shows the most recently recorded percentage and time for the selected account. Refresh connections and receipts to see newly received measurements. The value describes that observation and can change between requests. [Provider header semantics](https://github.com/openai/codex/blob/main/codex-rs/codex-api/src/rate_limits.rs)

In **Customer runners**, check the device's last heartbeat and grants, then read the job receipt. A local pause, an OS denial, or a replaced folder can require different action. Re-pairing creates a new device identity. The existing folder count and offline browser review are the supported desktop jobs.

Select an agent, open **Connected engines** and connect a **local fixture**. Save a word-count or checklist selection as the agent default or as a private conversation override. Run a short piece of text. **Refresh connections and receipts** shows the model acknowledgement and grant generation. The run list shows the result. The fixture executes locally, has zero subscription usage and receives no external tools.

An accepted run freezes the model, account, grant generation and optional conversation. Its next activity checks the grant again before consuming the goal. Reconnecting increments the generation. A revoked pin stops the run even when another default exists. Saved choices belong to the executing member. The fixture picker and its conversation overrides apply to engine runs launched here. Ordinary chat retains its model gateway picker.

## Pair a provider on Windows

Start the local workbench with its agents profile. In **Customer runners** choose **Create pairing code**. From the repository root start one foreground runner using one of the commands below. The CLI routes require Python 3 on PATH. Use the actual executable installed on the selected device.

```powershell
node frontend/runner/local.mjs --engine antigravity --binary 'C:\path\to\agy.exe' --profile personal
node frontend/runner/local.mjs --engine opencode-free --binary 'C:\path\to\opencode-cli.exe' --profile personal
node frontend/runner/local.mjs --engine chatgpt-plan --profile personal
```

Complete any provider sign-in, then paste the pairing code into the terminal. In **Agents and runs → Connected engines**, refresh, select the account and model, and save the selection. The foreground process must stay open. The provider receives the pinned agent instructions and goal after model acknowledgement. Select **Run selected engine** and refresh the run list to see its result. Each CLI engine has an isolated working directory. The private device session uses loopback Zincc access. The test device was revoked after the live check. To use your account, start a new pairing.

**Windows evidence on 26 November.** A signed-in Edge workbench paired Antigravity, listed five choices, selected `gemini-3.8-flash` and received `ready` from one synthetic task. The completed run and model acknowledgement were read back through the API. The OpenCode Free CLI emitted HTTP 429 on its separate synthetic check. ChatGPT consent did not produce a verified paired account on this PC. [Learning note](../learning-notes/045-paired-engine-delivery.md) · [OpenCode and AppuBot review](../research/2026-11-26-opencode-appubot.md)

## Provider qualification

The standalone probes below complement the paired runner. A CLI handshake establishes protocol availability. A live workbench run also requires a member grant, model acknowledgement and a stored outcome.

| Task | Delivered interface | Remaining qualification |
| --- | --- | --- |
| P9.6 direct ChatGPT plan | Local public-client sign-in, loopback callback, PKCE, ID-token verification, account-specific catalog, optional bounded streaming inference | Live plan-use consent, model access and provider-side revocation. The initial client keeps tokens in memory and requires a new sign-in for the next process. |
| P9.7 Codex | Clean-profile app-server initialization and model-list probe | Signed-in session lifecycle, enforced native tool restrictions and action receipts on a paired device |
| P9.8 Claude Code | Local executable and native CLI option probe | Supported account, streaming/MCP execution and enforced tool restrictions |
| P9.9 Grok Build | Prompt-free ACP handshake using the official Windows stdio command | Signed-in session creation, account-specific discovery and device enforcement |
| P9.10 OpenCode | Installed v2.4.1 Windows CLI, isolated standalone free-model discovery, effective agent and tool-permission checks; paired device transport and error handling | Free completion is unconfirmed after HTTP 429. Go subscription billing still needs separate qualification. |
| P9.11 Antigravity | Completed a signed-in workbench task on the paired Windows CLI with a confirmed model | Exact remaining allowance, other operating systems and additional versions remain unverified |
| P9.12 Kimi Code | Prompt-free ACP discovery and explicit model acknowledgement check | Signed-in membership, effective model and allowance verification |

Run the prompt-free checks with `python tools/engine_probe.py ENGINE --binary ABSOLUTE_PATH`. An ACP client can request a model using `--select-model ID`. Unknown or unacknowledged changes fail the check. Local fixture, OpenCode Free, OpenCode Go and direct ChatGPT plan usage are separate billing sources. There is no automatic paid fallback.

The local ChatGPT-plan client is for an eligible locally hosted edition. From a private terminal, `node frontend/runner/chatgpt-plan.mjs --profile personal` starts an interactive catalog check. Complete the printed provider consent URL on the same device. A model request also requires an explicit catalog slug via `--model` and text on stdin. Only the opaque host ID, issued client registration and hashed subject persist in the user's local profile. A failed/expired session requires explicit reauthorization. `ZINCC_LOCAL_ONLY=true` disables this cloud path. Hosted distribution requires separate provider eligibility. [OpenAI registration](https://developers.openai.com/siwc/token-sharing-open-source/sign-in) · [Provider model and inference contract](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)

### Windows CLI observations, 26 November

| Engine | Release tested | Observation with a fresh profile |
| --- | --- | --- |
| Codex | 0.172.0 stable | The app server initialized and returned 11 entries from its bundled or cached model list. No account entitlement was inferred. |
| Claude Code | 2.1.332 | The official binary reported switches for model selection, tool restrictions, strict MCP configuration and structured output. Enforcement under an authenticated turn remains open. |
| Grok Build | 1.0.92 | ACP initialized. The engine rejected session creation with RPC error -32000. No task was submitted. |
| OpenCode | 2.4.1 | The existing Windows installation listed the free DeepSeek route. Its effective standalone configuration selected the requested model and a wildcard tool denial. One synthetic prompt was submitted. The probe did not confirm a completed answer or the serving model from the result. The capture does not establish whether the provider, CLI or event parser caused the missing confirmation. |
| Antigravity CLI | 1.5.18 | The checksum-verified CLI initialized ACP, advertised four authentication methods and created a session that reported a selected model. No subscription inference was exercised. |
| Kimi Code | 2.8.0 | The checksum-verified replacement for the retired Python CLI initialized ACP. Session creation returned RPC error -32000 before any task content was sent. |

These initial CLI observations predate the paired-device run above. Production distribution and macOS/Linux qualification remain open. [OpenCode free pricing](https://opencode.ai/docs/zen/) · [Codex app-server](https://developers.openai.com/codex/app-server) · [Claude Code](https://code.claude.com/docs/en/headless) · [Grok Build](https://docs.x.ai/build/overview) · [Antigravity ACP](https://antigravity.google/docs/cli/acp) · [Kimi Code](https://github.com/MoonshotAI/kimi-code)

OpenCode Go also exposes authenticated rolling, weekly and monthly allowance readings. From a private terminal with `ZINCC_OPENCODE_GO_KEY` set, run `node frontend/runner/opencode-go.mjs` to check entitlement and provider-reported usage. This read-only command uses the Go usage endpoint. Tests use synthetic responses. No Go credential was available for live verification. “Extra Usage” is a separate account setting that can charge the workspace balance. [Go usage and billing](https://opencode.ai/docs/console/go/)

## Verification

The paired-device build discovered 118 Java tests. 111 passed and seven optional tests were skipped. All 11 Edge regressions, nine Node protocol and usage tests, and four Python selection and output tests passed.

The signed-in Edge journey selected a real framework, compared revisions, restored v1 as v3, created a private fixture connection and completed a Temporal word count. The Java tests exercised the three installed frameworks with controlled model answers, an approval wait across worker replacement, cancellation, tenant scope, revocation and receipt replay. AG-UI replay frames were validated by the installed protocol SDK. The direct plan client uses locally signed tokens and synthetic streamed events in its protocol tests. [Frameworks and checkpoints](../learning-notes/042-framework-planning.md) · [Versioned builder](../learning-notes/043-versioned-builder.md) · [Engine qualification](../learning-notes/044-private-engine-connections.md)
