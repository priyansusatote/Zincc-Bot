package io.zincc.model;

import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class LocalRoutingTest {

	@org.junit.jupiter.api.TestFactory
	java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> frozenRoutingCases() throws Exception {
		return io.zincc.eval.FrozenCases.group("local")
			.map(row -> org.junit.jupiter.api.DynamicTest.dynamicTest(row.path("id").asString(), () -> {
				String preset = row.path("preset").asString();
				var env = new MockEnvironment().withProperty("OPENROUTER_API_KEY", "fixture")
					.withProperty("NUMES_API_KEY", "fixture-numes")
					.withProperty("zincc.local-only", row.path("localOnly").toString())
					.withProperty("zincc.models.ollama-offline", row.path("offline").toString())
					.withProperty("zincc.models.presets." + preset, row.path("order").asString());
				var gateway = new ModelConfiguration().modelGateway(routes, new SimpleMeterRegistry(), env);
				var selected = gateway.selections(ModelGateway.Capability.valueOf(row.path("capability").asString()));
				assertThat(selected.getOrDefault(preset, "")).isEqualTo(row.path("gold").asString());
			}));
	}

	private final ModelConfiguration.Settings routes = new ModelConfiguration.Settings(List.of(
			new ModelConfiguration.Entry("cloud", ModelConfiguration.Provider.OPENROUTER, "remote", 32, true, false),
			new ModelConfiguration.Entry("relay", ModelConfiguration.Provider.OLLAMA, "remote:cloud", 32, true, false),
			new ModelConfiguration.Entry("desk", ModelConfiguration.Provider.OLLAMA, "fixture", 32, false, true)),
			List.of(new ModelConfiguration.Entry("numes", ModelConfiguration.Provider.NUMES, "fixture:free", 32, false,
					false)));

	@Test
	void localDeploymentCannotConstructOrSelectCloudEvenWithExplicitId() {
		var env = new MockEnvironment().withProperty("zincc.local-only", "true")
			.withProperty("zincc.models.ollama-offline", "true")
			.withProperty("zincc.models.ollama-port", "1")
			.withProperty("zincc.models.presets.fast", "cloud,desk")
			.withProperty("zincc.models.presets.local", "relay,desk");
		var gateway = new ModelConfiguration().modelGateway(routes, new SimpleMeterRegistry(), env);
		assertThat(gateway.routes()).extracting(ModelGateway.Route::id).containsExactly("desk");
		assertThat(gateway.selections(ModelGateway.Capability.TEXT)).containsEntry("fast", "desk")
			.containsEntry("local", "desk");
		assertThat(gateway.selections(ModelGateway.Capability.JSON_OBJECT)).isEmpty();
		assertThatThrownBy(() -> gateway.text("cloud", "private").block()).hasMessage("UNKNOWN_ROUTE");
		assertThatThrownBy(() -> gateway.text("desk", "private").block()).hasMessage("PROVIDER_ERROR");
	}

	@Test
	void unqualifiedLocalRuntimeLeavesNoEligibleRoutes() {
		var env = new MockEnvironment().withProperty("zincc.local-only", "true");
		assertThat(new ModelConfiguration().modelGateway(routes, new SimpleMeterRegistry(), env).routes()).isEmpty();
	}

}
