package io.zincc.model;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModelConfiguration.Settings.class)
class ModelConfiguration {

	enum Provider {

		OPENAI, OPENROUTER, ANTHROPIC, OLLAMA

	}

	record Entry(String id, Provider provider, String model, int outputTokenLimit, boolean jsonObject,
			boolean streaming) {
		Entry {
			if (id == null || !id.matches("[a-z][a-z0-9-]{0,63}") || provider == null || model == null
					|| model.isBlank() || outputTokenLimit < 1 || outputTokenLimit > 32768)
				throw new IllegalArgumentException("Invalid model route settings");
		}
	}

	@ConfigurationProperties("zincc.models")
	record Settings(List<Entry> routes) {
		Settings {
			routes = routes == null ? List.of() : List.copyOf(routes);
		}
	}

	static final Duration DEADLINE = Duration.ofSeconds(60);

	@Bean
	ModelGateway modelGateway(Settings settings, MeterRegistry meters, Environment env) {
		var ports = settings.routes().stream().map(e -> switch (e.provider()) {
			case OPENAI -> port(e, "https://api.openai.com/v1", env.getRequiredProperty("ZINCC_OPENAI_API_KEY"));
			case OPENROUTER -> port(e, "https://openrouter.ai/api/v1", env.getRequiredProperty("OPENROUTER_API_KEY"));
			case ANTHROPIC -> port(e, "https://api.anthropic.com", env.getRequiredProperty("ZINCC_ANTHROPIC_API_KEY"));
			case OLLAMA ->
				port(e, "http://127.0.0.1:" + env.getProperty("zincc.models.ollama-port", Integer.class, 11434), null);
		}).toList();
		return new ModelGateway(List.copyOf(ports), meters, DEADLINE);
	}

	static ModelGateway.Port port(Entry entry, String baseUrl, String key) {
		var caps = EnumSet.of(ModelGateway.Capability.TEXT);
		if (entry.jsonObject())
			caps.add(ModelGateway.Capability.JSON_OBJECT);
		if (entry.streaming())
			caps.add(ModelGateway.Capability.TEXT_STREAM);
		var route = new ModelGateway.Route(entry.id(), entry.provider().name(), entry.model(), caps,
				entry.outputTokenLimit());
		ChatModel chat = switch (entry.provider()) {
			case OPENAI,
					OPENROUTER ->
				OpenAiChatModel.builder()
					.options(OpenAiChatOptions.builder()
						.baseUrl(baseUrl)
						.apiKey(key)
						.model(entry.model())
						.maxCompletionTokens(entry.outputTokenLimit())
						.streamUsage(true)
						.maxRetries(0)
						.timeout(DEADLINE)
						.build())
					.build();
			case ANTHROPIC -> AnthropicChatModel.builder()
				.options(AnthropicChatOptions.builder()
					.baseUrl(baseUrl)
					.apiKey(key)
					.model(entry.model())
					.maxTokens(entry.outputTokenLimit())
					.maxRetries(0)
					.timeout(DEADLINE)
					.build())
				.build();
			case OLLAMA -> {
				var transport = new JdkClientHttpRequestFactory();
				transport.setReadTimeout(DEADLINE);
				var api = OllamaApi.builder()
					.baseUrl(baseUrl)
					.restClientBuilder(RestClient.builder().requestFactory(transport))
					.build();
				yield OllamaChatModel.builder()
					.ollamaApi(api)
					.retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build()))
					.options(OllamaChatOptions.builder()
						.model(entry.model())
						.numPredict(entry.outputTokenLimit())
						.build())
					.build();
			}
		};
		return new SpringChatPort(route, chat);
	}

}
