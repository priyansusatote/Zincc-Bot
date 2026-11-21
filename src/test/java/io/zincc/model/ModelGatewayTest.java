package io.zincc.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelGatewayTest {

	private final AtomicInteger calls = new AtomicInteger();

	private final AtomicBoolean cancelled = new AtomicBoolean();

	private ModelGateway gateway(Set<ModelGateway.Capability> capabilities, String output, Duration timeout) {
		var port = new ModelGateway.Port() {
			public ModelGateway.Route route() {
				return new ModelGateway.Route("test", "fixture", "v1", capabilities, 64);
			}

			public ModelGateway.Part call(ModelGateway.Input prompt, String instructions) {
				calls.incrementAndGet();
				return new ModelGateway.Part(output, null, "stop");
			}

			public Flux<ModelGateway.Part> stream(ModelGateway.Input prompt) {
				calls.incrementAndGet();
				return Flux.<ModelGateway.Part>never().doOnCancel(() -> cancelled.set(true));
			}
		};
		return new ModelGateway(List.of(port), new SimpleMeterRegistry(), timeout);
	}

	@Test
	void rejectsUnknownRouteAndUnsupportedCapabilityBeforeDispatch() {
		var gateway = gateway(Set.of(ModelGateway.Capability.TEXT), "hi", Duration.ofSeconds(1));
		assertFailure(() -> gateway.text("unknown", "hello").block(), ModelGateway.Failure.UNKNOWN_ROUTE);
		assertFailure(() -> gateway.jsonObject("test", "hello", Map.of()).block(), ModelGateway.Failure.UNSUPPORTED);
		assertThat(this.calls.get()).isZero();
		assertThat(gateway.text("test", "hello").block().usage()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "not json", "[]", "{}", "{\"ok\":1}", "{\"ok\":true} {}", "{\"ok\":true,\"ok\":false}" })
	void rejectsInvalidOrAmbiguousStructuredOutput(String output) {
		var gateway = gateway(Set.of(ModelGateway.Capability.JSON_OBJECT), output, Duration.ofSeconds(1));
		assertFailure(() -> gateway.jsonObject("test", "Is this valid?", Map.of("ok", ModelGateway.FieldType.BOOLEAN))
			.block(), ModelGateway.Failure.INVALID_OUTPUT);
	}

	@Test
	void streamingDeadlineAndCallerCancellationReleaseTheSubscription() {
		var gateway = gateway(Set.of(ModelGateway.Capability.TEXT_STREAM), "", Duration.ofMillis(150));
		assertFailure(() -> gateway.streamText("test", "hello").collectList().block(), ModelGateway.Failure.TIMEOUT);
		assertThat(this.cancelled.get()).isTrue();
		this.cancelled.set(false);
		var subscription = gateway.streamText("test", "hello").subscribe();
		subscription.dispose();
		assertThat(this.cancelled.get()).isTrue();
	}

	private static void assertFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
			ModelGateway.Failure reason) {
		assertThatThrownBy(action).isInstanceOfSatisfying(ModelGateway.GatewayException.class,
				error -> assertThat(error.failure()).isEqualTo(reason));
	}

}
