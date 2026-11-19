# Model gateway

P2.1 adds an internal Spring bean for explicitly selected model routes. It can produce text, validate a JSON object, or stream text. The service starts with an empty registry until an operator configures routes. Registration describes the intended model and capabilities. A live call is needed to verify the provider account and model combination.

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

Ollama connects to loopback port 11434 by default. Set `zincc.models.ollama-port` if the local service uses another port. A loopback connection alone cannot attest how that service handles data downstream. P2.3 owns end-to-end local-only qualification.

With the project JDK configured, run the one-request smoke test in PowerShell. This sends a synthetic greeting to the selected model. A cloud route may incur provider charges.

```powershell
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = 'file:./application-local.yaml'
$env:ZINCC_MODEL_SMOKE_ROUTE = 'desk'
.\mvnw.cmd '-Dtest=ConfiguredModelSmokeTest' test
```

For a cloud route, set `provider` to `openai` or `anthropic`, choose a model accessible to that API account, and supply `ZINCC_OPENAI_API_KEY` or `ZINCC_ANTHROPIC_API_KEY` in the service environment. Each route needs a unique stable ID and a positive output token limit up to 32,768. Text is available on every route. Streaming and JSON validation are opt-in capabilities. Existing subscription/CLI engines have separate qualification tasks in P9.

## Caller contract

- `routes()` returns configured identities and capabilities. Callers supply a route ID on every request. Each Reactor subscription starts one generation. Subscribing twice can incur two charges.
- `text()` returns complete text. `jsonObject()` requests one object and validates required top-level string, number or boolean fields. Extra fields are allowed. Invalid types, duplicate keys, trailing content and incomplete generation fail the request. Correct syntax alone does not establish factual accuracy.
- `streamText()` emits provisional deltas followed by one completion receipt on success. An error can follow visible text. Save a response as completed only after receiving the receipt. The caller receives a timeout after the 60-second deadline. A disconnected caller cannot establish that the provider stopped billing.
- Micrometer records available input and output counts under `zincc.model.tokens`, tagged by route ID and direction. Absent usage stays unknown. Interrupted generations may lack final counts. Billing reconciliation belongs to P2.5.

[Gateway](../../src/main/java/io/zincc/model/ModelGateway.java) · [Provider configuration](../../src/main/java/io/zincc/model/ModelConfiguration.java) · [Protocol tests](../../src/test/java/io/zincc/model/ModelProtocolTest.java)
