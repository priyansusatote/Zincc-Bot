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

		OPENAI, OPENROUTER, NUMES, ANTHROPIC, OLLAMA

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
	record Settings(List<Entry> routes, List<Entry> additionalRoutes) {
		Settings {
			routes = routes == null ? List.of() : List.copyOf(routes);
			additionalRoutes = additionalRoutes == null ? List.of() : List.copyOf(additionalRoutes);
		}
	}

	static final Duration DEADLINE = Duration.ofSeconds(60);

	@Bean
	ModelGateway modelGateway(Settings settings, MeterRegistry meters, Environment env) {
		boolean localOnly = env.getProperty("zincc.local-only", Boolean.class, false);
		boolean offlineOllama = env.getProperty("zincc.models.ollama-offline", Boolean.class, false);
		var eligible = java.util.stream.Stream.concat(settings.routes().stream(), settings.additionalRoutes().stream())
			.filter(e -> !localOnly || local(e, offlineOllama))
			.toList();
		var ports = eligible.stream().map(e -> switch (e.provider()) {
			case OPENAI -> port(e, "https://api.openai.com/v1", env.getRequiredProperty("ZINCC_OPENAI_API_KEY"));
			case OPENROUTER -> port(e, "https://openrouter.ai/api/v1", env.getRequiredProperty("OPENROUTER_API_KEY"));
			case NUMES -> port(e, "https://api.numes.ai/api/v1", env.getRequiredProperty("NUMES_API_KEY"));
			case ANTHROPIC -> port(e, "https://api.anthropic.com", env.getRequiredProperty("ZINCC_ANTHROPIC_API_KEY"));
			case OLLAMA ->
				port(e, "http://127.0.0.1:" + env.getProperty("zincc.models.ollama-port", Integer.class, 11434), null);
		}).toList();
		var gateway = new ModelGateway(List.copyOf(ports), meters, DEADLINE);
		var localIds = eligible.stream().filter(e -> local(e, offlineOllama)).map(Entry::id).toList();
		var preferences = new java.util.LinkedHashMap<String, List<String>>();
		for (String preset : List.of("fast", "balanced", "deep", "local")) {
			String order = env.getProperty("zincc.models.presets." + preset, "");
			var ids = order.isBlank() && preset.equals("local") ? localIds
					: java.util.Arrays.stream(order.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
			preferences.put(preset, preset.equals("local") ? ids.stream().filter(localIds::contains).toList() : ids);
		}
		gateway.configurePresets(preferences);
		return gateway;
	}

	private static boolean local(Entry entry, boolean offlineOllama) {
		return offlineOllama && entry.provider() == Provider.OLLAMA
				&& !entry.model().toLowerCase(java.util.Locale.ROOT).contains("cloud");
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
			case OPENAI, OPENROUTER,
					NUMES ->
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
