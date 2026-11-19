package io.zincc.model;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelProtocolTest {

	private final JsonMapper json = new JsonMapper();

	private final AtomicReference<JsonNode> sent = new AtomicReference<>();

	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

	private HttpServer server;

	private String origin;

	private final AtomicInteger requests = new AtomicInteger();

	@BeforeEach
	void start() throws Exception {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.start();
		this.origin = "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		this.server.stop(0);
		this.meters.close();
	}

	private ModelGateway gateway(ModelConfiguration.Provider provider, String path, String type, String wire) {
		return gateway(provider, path, type, wire, 200);
	}

	private ModelGateway gateway(ModelConfiguration.Provider provider, String path, String type, String wire,
			int status) {
		this.server.createContext(path, exchange -> {
			try (exchange) {
				this.requests.incrementAndGet();
				this.sent.set(this.json.readTree(exchange.getRequestBody()));
				byte[] data = wire.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", type);
				exchange.sendResponseHeaders(status, data.length);
				exchange.getResponseBody().write(data);
			}
		});
		String base = this.origin + switch (provider) {
			case OPENAI -> "/v1";
			case OPENROUTER -> "/api/v1";
			default -> "";
		};
		var entry = new ModelConfiguration.Entry("fixture", provider, "test-model", 64, true, true);
		return new ModelGateway(List.of(ModelConfiguration.port(entry, base, "fixture-key")), this.meters,
				Duration.ofSeconds(8));
	}

	@ParameterizedTest
	@EnumSource(ModelConfiguration.Provider.class)
	void sameStructuredTaskUsesEachProvidersNativeProtocol(ModelConfiguration.Provider provider) {
		String answer = "{\"city\":\"Oslo\"}";
		Map<String, Object> reply;
		String path;
		Map<String, Object> assistant = Map.of("role", "assistant", "content", answer);
		switch (provider) {
			case OPENAI, OPENROUTER -> {
				path = provider == ModelConfiguration.Provider.OPENROUTER ? "/api/v1/chat/completions"
						: "/v1/chat/completions";
				reply = Map.of("id", "chatcmpl-test", "object", "chat.completion", "created", 1, "model", "test-model",
						"choices", List.of(Map.of("index", 0, "message", assistant, "finish_reason", "stop")), "usage",
						Map.of("prompt_tokens", 15, "completion_tokens", 6, "total_tokens", 21));
			}
			case ANTHROPIC -> {
				path = "/v1/messages";
				reply = Map.of("id", "msg_test", "type", "message", "role", "assistant", "model", "test-model",
						"content", List.of(Map.of("type", "text", "text", answer)), "stop_reason", "end_turn", "usage",
						Map.of("input_tokens", 15, "output_tokens", 6));
			}
			case OLLAMA -> {
				path = "/api/chat";
				reply = Map.of("model", "test-model", "created_at", "2026-11-19T00:00:00Z", "message", assistant,
						"done", true, "done_reason", "stop", "prompt_eval_count", 15, "eval_count", 6);
			}
			default -> throw new AssertionError(provider);
		}
		var gateway = gateway(provider, path, "application/json", this.json.writeValueAsString(reply));
		var result = gateway
			.jsonObject("fixture", "Name Norway's capital in the city field.",
					Map.of("city", ModelGateway.FieldType.STRING))
			.block();
		assertThat(result.text()).isEqualTo(answer);
		assertThat(result.usage()).isEqualTo(new ModelGateway.Usage(15, 6));
		assertThat(this.meters.get("zincc.model.tokens").tag("direction", "output").counter().count()).isEqualTo(6);
		assertThat(this.sent.get().path("model").asString()).isEqualTo("test-model");
		assertThat(this.sent.get().toString()).contains("Name Norway", "city");
		if (provider == ModelConfiguration.Provider.OPENAI) {
			assertThat(this.sent.get().hasNonNull("stream_options")).isFalse();
		}
	}

	@Test
	void providerFailureDoesNotRetryOrExposeResponseBody() {
		var gateway = gateway(ModelConfiguration.Provider.OPENAI, "/v1/chat/completions", "application/json",
				"{\"error\":{\"message\":\"private upstream diagnostic\"}}", 503);
		assertThatThrownBy(() -> gateway.text("fixture", "hello").block())
			.isInstanceOfSatisfying(ModelGateway.GatewayException.class, error -> {
				assertThat(error.failure()).isEqualTo(ModelGateway.Failure.PROVIDER_ERROR);
				assertThat(error.getMessage()).doesNotContain("private");
			});
		assertThat(this.requests.get()).isOne();
	}

	@Test
	void openAiUsageOnlyFrameCompletesTheStream() {
		String wire = """
				data: {"id":"c","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Hi"},"finish_reason":null}]}

				data: {"id":"c","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

				data: {"id":"c","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[],"usage":{"prompt_tokens":4,"completion_tokens":1,"total_tokens":5}}

				data: [DONE]

				""";
		assertStream(gateway(ModelConfiguration.Provider.OPENAI, "/v1/chat/completions", "text/event-stream", wire));
		assertThat(this.sent.get().path("stream_options").path("include_usage").asBoolean()).isTrue();
	}

	@Test
	void anthropicUsageSpansStartAndStopEvents() {
		String wire = """
				event: message_start
				data: {"type":"message_start","message":{"id":"m","type":"message","role":"assistant","model":"test-model","content":[],"usage":{"input_tokens":4,"output_tokens":0}}}

				event: content_block_start
				data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

				event: content_block_delta
				data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}

				event: content_block_stop
				data: {"type":"content_block_stop","index":0}

				event: message_delta
				data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":1}}

				event: message_stop
				data: {"type":"message_stop"}

				""";
		assertStream(gateway(ModelConfiguration.Provider.ANTHROPIC, "/v1/messages", "text/event-stream", wire));
	}

	@Test
	void ollamaStreamingProducesDeltasThenOneReceipt() {
		String wire = """
				{"model":"test-model","created_at":"2026-11-19T00:00:00Z","message":{"role":"assistant","content":"Hi"},"done":false}
				{"model":"test-model","created_at":"2026-11-19T00:00:00Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":4,"eval_count":1}
				""";
		var gateway = gateway(ModelConfiguration.Provider.OLLAMA, "/api/chat", "application/x-ndjson", wire);
		assertStream(gateway);
	}

	private void assertStream(ModelGateway gateway) {
		var events = gateway.streamText("fixture", "Say hi.").collectList().block();
		assertThat(events).hasSize(2);
		assertThat(events.getFirst()).isEqualTo(new ModelGateway.Delta("Hi"));
		assertThat(((ModelGateway.Finished) events.getLast()).usage()).isEqualTo(new ModelGateway.Usage(4, 1));
	}

}
