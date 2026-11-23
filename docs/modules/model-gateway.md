# Model gateway

P2.1 adds an internal Spring bean for explicitly selected model routes. It can produce text, validate a JSON object, or stream text. The service starts with an empty registry until an operator configures routes. Registration describes the intended model and capabilities. A live call is needed to verify the provider account and model combination.

## Provider follow-up for the local workbench

The current free profile exposes `lightning-free` and `gemma-free`. The following candidates come from the workspace owner's available accounts and credits. Recheck free availability and run synthetic chat and agent-step checks before making any of them a default.

| Source | Qualification to carry forward |
| --- | --- |
| OpenRouter | Use the existing `OPENROUTER_API_KEY` to evaluate `baidu/ernie-5.5-flash-preview:free` and `particle-ai/particle-1:free`. Compare completion, structured agent steps, latency and free-tier errors. Keep the exact selected route visible. [ERNIE](https://openrouter.ai/baidu/ernie-5.5-flash-preview:free) · [Particle-1](https://openrouter.ai/particle-ai/particle-1:free) |
| Numes AI | Qualify a separate OpenAI-compatible adapter using `NUMES_API_KEY`. Check `deepseek/deepseek-v4.1-flash:free`, `z-ai/glm-5.3-flash:free`, and the user-supplied candidates `glm-5.3-uncensored`, `glm-5.3-flash-uncensored`, and `dealignai/deepseek-v4.1-flash-uncensored-fp8:free` against the account's model list for exact IDs and price before calling them. [Model-list contract](https://numes.ai/docs/api-reference/models/list-models) |
| Context.dev | Use the available credits for a research adapter with `CONTEXT_DEV_API_KEY`. Evaluate search and page extraction against the current evidence contract for citations, freshness, and page coverage. Research outputs can feed a separate answer model. [Provider capabilities](https://docs.context.dev/agent-quickstart.md) |
| OpenCode and Google AI Pro / Antigravity | Discover eligible models and account allowances through each provider's supported path in P9.10 and P9.11. Display engine, effective model, and billing source together. [Engine qualification](connected-ai-engines.md) · [OpenCode catalog](https://opencode.ai/docs/console/models/) |

Keep cloud routes and public research adapters unavailable for local-only requests. P4 PDF parsing, OCR, and embeddings already run locally. Provider comparisons are pending qualification.

## Configure and exercise a route

### OpenRouter free text routes

From the repository root, use the `openrouter-free` profile with `OPENROUTER_API_KEY` in the environment or the gitignored local `.env` file. This profile reads that file as Java properties, so enter the key as an unquoted value. Select one route for each test. Both routes use a maximum of 512 output tokens and send only the synthetic greeting during this test.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'openrouter-free'
$env:ZINCC_MODEL_SMOKE_ROUTE = 'lightning-free'
.\mvnw.cmd '-Dtest=ConfiguredModelSmokeTest' test
```

`lightning-free` selects `nvidia/nemotron-3.5-lightning:free`. To exercise Gemma separately, set the smoke route to `gemma-free`, which selects `google/gemma-4-26b-a4b-it:free`. A rate limit ends that request. Each route keeps its selected model. Use a fresh shell for the ordinary offline suite so the live test stays opt-in. [Route configuration](../../src/main/resources/application-openrouter-free.properties)

### Operator-defined routes

For an existing Ollama installation, save this as the gitignored `application-local.yaml` in the project root. Replace the placeholder with an installed model name.

```yaml
zincc:
  models:
    routes:
      - id: desk
        provider: ollama
        model: "<installed-model-name>"
        output-token-limit: 128
        streaming: true
        json-object: true
```

Ollama connects to loopback port 11434 by default. Set `zincc.models.ollama-port` if the local service uses another port. A loopback connection alone cannot attest how that service handles data downstream. The local-only deployment setting and operator qualification are described below.

With the project JDK configured, run the one-request smoke test in PowerShell. This sends a synthetic greeting to the selected model. A cloud route may incur provider charges.

```powershell
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = 'file:./application-local.yaml'
$env:ZINCC_MODEL_SMOKE_ROUTE = 'desk'
.\mvnw.cmd '-Dtest=ConfiguredModelSmokeTest' test
```

For a cloud route, set `provider` to `openai` or `anthropic`, choose a model accessible to that API account, and supply `ZINCC_OPENAI_API_KEY` or `ZINCC_ANTHROPIC_API_KEY` in the service environment. Each route needs a unique stable ID and a positive output token limit up to 32,768. Text is available on every route. Streaming and JSON validation are opt-in capabilities. Existing subscription/CLI engines have separate qualification tasks in P9.

## Caller contract

P2.3 adds ordered preset selection. Set `zincc.models.presets.fast`, `.balanced`, or `.deep` to comma-separated route IDs in operator preference order. The workbench displays the resolved route beside each available preset and stores that ID. Local defaults to eligible offline Ollama routes in registration order. Errors after dispatch end the attempt.

For an inference-local deployment, set `zincc.local-only=true`. Disable cloud features **on the Ollama service** with `OLLAMA_NO_CLOUD=1`, restart it, and verify its offline setting and network restrictions. Then set `zincc.models.ollama-offline=true` in Zincc. Without that declaration, local-only starts with no eligible routes. Local-only also disables public web research. Host the control plane, database and identity service locally for a fully local installation. [Ollama offline configuration](https://docs.ollama.com/faq#how-do-i-disable-ollamas-cloud-features) · [Selection and quality probe](../learning-notes/013-route-selection.md)

- `routes()` returns configured identities and capabilities. Callers supply a route ID on every request. Each Reactor subscription starts one generation. Subscribing twice can incur two charges.
- `text()` returns complete text. `jsonObject()` requests one object and validates required top-level string, number or boolean fields. Extra fields are allowed. Invalid types, duplicate keys, trailing content and incomplete generation fail the request. Correct syntax alone does not establish factual accuracy.
- `streamText()` emits provisional deltas followed by one completion receipt on success. An error can follow visible text. Save a response as completed only after receiving the receipt. The caller receives a timeout after the 60-second deadline. A disconnected caller cannot establish that the provider stopped billing.
- Micrometer records available input and output counts under `zincc.model.tokens`, tagged by route ID and direction. Absent usage stays unknown. Interrupted generations may lack final counts. [P2.5](phase-two-evaluation.md) records conversation receipts and reconciles provider totals.

[Gateway](../../src/main/java/io/zincc/model/ModelGateway.java) · [Provider configuration](../../src/main/java/io/zincc/model/ModelConfiguration.java) · [Protocol tests](../../src/test/java/io/zincc/model/ModelProtocolTest.java)
