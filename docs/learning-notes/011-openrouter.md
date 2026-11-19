# P2.1 follow-up — Verifying a hosted model

**Implemented:** OpenRouter uses the existing chat-completions client. Its provider ID ties the OpenRouter credential to the fixed OpenRouter HTTPS endpoint. A fixture test checks that supplying a direct OpenAI key alone cannot initialize this provider. The opt-in local profile configures two free text routes and reads the operator's local credentials.

**Experimentally verified:** Zincc sent a synthetic one-word greeting request to the free NVIDIA Nemotron 3.5 Lightning model through the gateway. The test accepted the returned greeting only after the gateway observed a normal completion. A direct diagnostic request to the free Gemma route returned HTTP 429. A working account can encounter different capacity limits on different model routes.

The fixture exercises the wire format on demand. A live greeting adds evidence about authentication, endpoint availability and response parsing for that model at that time. Measuring task quality, streaming behavior or sustained throughput takes separate tests.

[How to reproduce the smoke test](../modules/model-gateway.md) · [Credential binding](../../src/main/java/io/zincc/model/ModelConfiguration.java) · [Live assertion](../../src/test/java/io/zincc/model/ConfiguredModelSmokeTest.java)
