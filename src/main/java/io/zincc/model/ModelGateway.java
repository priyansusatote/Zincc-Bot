package io.zincc.model;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Explicit route selection. Each subscription performs one generation. */
public final class ModelGateway {

	public enum Capability {

		TEXT, JSON_OBJECT, TEXT_STREAM

	}

	public enum FieldType {

		STRING, NUMBER, BOOLEAN

	}

	public enum Failure {

		UNKNOWN_ROUTE, UNSUPPORTED, INVALID_REQUEST, INVALID_OUTPUT, TIMEOUT, PROVIDER_ERROR

	}

	public record Route(String id, String provider, String model, Set<Capability> capabilities, int outputTokenLimit) {
		public Route {
			capabilities = Set.copyOf(capabilities);
		}
	}

	public record Usage(Integer inputTokens, Integer outputTokens) {
	}

	public record Result(Route route, String text, Usage usage, String finishReason) {
	}

	public sealed interface Event permits Delta, Finished {

	}

	public record Delta(String text) implements Event {
	}

	public record Finished(Route route, Usage usage, String finishReason) implements Event {
	}

	public static final class GatewayException extends RuntimeException {

		private final Failure failure;

		GatewayException(Failure failure) {
			super(failure.name());
			this.failure = failure;
		}

		public Failure failure() {
			return this.failure;
		}

	}

	interface Port {

		Route route();

		Part call(String prompt, String instructions);

		Flux<Part> stream(String prompt);

	}

	record Part(String text, Usage usage, String finishReason) {
	}

	private static final int MAX_CHARACTERS = 131072;

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
		.build();

	private final Map<String, Port> ports;

	private final MeterRegistry meters;

	private final Duration deadline;

	ModelGateway(Collection<Port> ports, MeterRegistry meters, Duration deadline) {
		this.ports = ports.stream().collect(Collectors.toUnmodifiableMap(p -> p.route().id(), Function.identity()));
		this.meters = meters;
		this.deadline = deadline;
	}

	public List<Route> routes() {
		return this.ports.values().stream().map(Port::route).sorted(java.util.Comparator.comparing(Route::id)).toList();
	}

	public Mono<Result> text(String routeId, String prompt) {
		return complete(routeId, prompt, Capability.TEXT, Map.of());
	}

	/** Validate an object and its required top-level fields before exposing it. */
	public Mono<Result> jsonObject(String routeId, String prompt, Map<String, FieldType> fields) {
		if (fields == null || fields.size() > 64
				|| fields.keySet().stream().anyMatch(k -> k == null || !k.matches("[A-Za-z_][A-Za-z_0-9]{0,63}"))
				|| fields.values().stream().anyMatch(java.util.Objects::isNull)) {
			return Mono.error(new GatewayException(Failure.INVALID_REQUEST));
		}
		return complete(routeId, prompt, Capability.JSON_OBJECT, Map.copyOf(fields));
	}

	private Mono<Result> complete(String id, String prompt, Capability capability, Map<String, FieldType> fields) {
		return Mono.defer(() -> {
			Port port = require(id, prompt, capability);
			String instructions = capability == Capability.JSON_OBJECT
					? "Return exactly one JSON object. Required field types: " + new java.util.TreeMap<>(fields) : null;
			return Mono.fromCallable(() -> port.call(prompt, instructions))
				.subscribeOn(Schedulers.boundedElastic())
				.timeout(this.deadline)
				.map(part -> {
					recordUsage(port.route(), part.usage());
					requireComplete(part.finishReason());
					if (part.text().isBlank() || part.text().length() > MAX_CHARACTERS)
						throw invalidOutput();
					if (capability == Capability.JSON_OBJECT)
						validate(part.text(), fields);
					return new Result(port.route(), part.text(), part.usage(), part.finishReason());
				});
		}).onErrorMap(ModelGateway::normalize);
	}

	public Flux<Event> streamText(String id, String prompt) {
		return Flux.defer(() -> {
			Port port = require(id, prompt, Capability.TEXT_STREAM);
			var state = new StreamState();
			long expiresAt = System.nanoTime() + this.deadline.toNanos();
			return port.stream(prompt).<Event>handle((part, sink) -> {
				state.accept(part);
				if (!part.text().isEmpty())
					sink.next(new Delta(part.text()));
			}).concatWith(Mono.fromCallable(() -> {
				recordUsage(port.route(), state.usage);
				requireComplete(state.reason);
				if (!state.hasContent)
					throw invalidOutput();
				return new Finished(port.route(), state.usage, state.reason);
			}))
				.timeout(Mono.delay(this.deadline),
						event -> Mono.delay(Duration.ofNanos(Math.max(0, expiresAt - System.nanoTime()))));
		}).onErrorMap(ModelGateway::normalize);
	}

	private Port require(String id, String prompt, Capability capability) {
		if (id == null || prompt == null || prompt.isBlank() || prompt.length() > MAX_CHARACTERS)
			throw new GatewayException(Failure.INVALID_REQUEST);
		Port port = this.ports.get(id);
		if (port == null)
			throw new GatewayException(Failure.UNKNOWN_ROUTE);
		if (!port.route().capabilities().contains(capability))
			throw new GatewayException(Failure.UNSUPPORTED);
		return port;
	}

	private static void validate(String text, Map<String, FieldType> fields) {
		JsonNode object;
		try {
			object = JSON.readTree(text);
		}
		catch (tools.jackson.core.JacksonException ex) {
			throw invalidOutput();
		}
		if (!object.isObject())
			throw invalidOutput();
		fields.forEach((name, type) -> {
			JsonNode value = object.path(name);
			boolean valid = switch (type) {
				case STRING -> value.isString();
				case NUMBER -> value.isNumber();
				case BOOLEAN -> value.isBoolean();
			};
			if (!valid)
				throw invalidOutput();
		});
	}

	private static void requireComplete(String reason) {
		if (reason == null
				|| !Set.of("stop", "end_turn", "stop_sequence").contains(reason.toLowerCase(java.util.Locale.ROOT)))
			throw invalidOutput();
	}

	private static GatewayException invalidOutput() {
		return new GatewayException(Failure.INVALID_OUTPUT);
	}

	private static Throwable normalize(Throwable error) {
		if (error instanceof GatewayException)
			return error;
		return new GatewayException(
				error instanceof java.util.concurrent.TimeoutException ? Failure.TIMEOUT : Failure.PROVIDER_ERROR);
	}

	private void recordUsage(Route route, Usage usage) {
		if (usage == null)
			return;
		if (usage.inputTokens() != null)
			this.meters.counter("zincc.model.tokens", "route", route.id(), "direction", "input")
				.increment(usage.inputTokens());
		if (usage.outputTokens() != null)
			this.meters.counter("zincc.model.tokens", "route", route.id(), "direction", "output")
				.increment(usage.outputTokens());
	}

	private static final class StreamState {

		private int characters;

		private boolean hasContent;

		private String reason;

		private Usage usage;

		void accept(Part part) {
			if (part.text().length() > MAX_CHARACTERS - this.characters)
				throw invalidOutput();
			this.characters += part.text().length();
			this.hasContent |= !part.text().isBlank();
			if (part.finishReason() != null && !part.finishReason().isBlank())
				this.reason = part.finishReason();
			if (part.usage() != null)
				this.usage = part.usage();
		}

	}

}
