# P2.1 — Model capabilities and generation

**Implemented and tested against protocol fixtures.** Zincc can ask three different model services for the same kind of result through one Java interface. The caller chooses a route and the kind of answer it needs. Asking a text-only route for a JSON object fails before any provider receives the prompt.

## How a request runs

1. Spring builds the registry from operator configuration. A route binds one model, provider, set of capabilities and output limit.
2. At subscription, the gateway checks the request. Its adapter translates the prompt to the selected provider’s protocol. The application sees Zincc types throughout.
3. For structured output, the gateway checks the terminal reason, parses exactly one JSON object, then validates required fields. Even if an answer contains valid JSON, a provider reporting that the token limit cut generation short makes the generation incomplete.
4. Streaming adds a wrinkle. OpenAI can send usage in a final frame after the last word. Closing the response on the last text delta would lose those counts. The gateway waits for stream completion before issuing a receipt. Cancellation releases the subscription and a single deadline bounds total waiting time.

## What the tests teach

The same synthetic city question crosses real SDK serialization and parsing for all three adapters. Loopback fixtures control the reply so we can reproduce malformed data and unusual event sequences. These tests establish the protocol contract. A live smoke test checks account access and a model’s availability. An evaluation set is needed to measure how well that model answers real tasks.

**Exercise.** Have a stream deliver a useful sentence followed by a token-limit finish reason. Which events should the UI display, and which event would justify marking the conversation complete?

[Gateway contract and smoke test](../modules/model-gateway.md) · [Adapter walkthrough in code](../../src/main/java/io/zincc/model/SpringChatPort.java) · [Failure and cancellation tests](../../src/test/java/io/zincc/model/ModelGatewayTest.java)
