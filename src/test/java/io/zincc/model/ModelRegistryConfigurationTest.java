package io.zincc.model;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRegistryConfigurationTest {

	private final ApplicationContextRunner context = new ApplicationContextRunner()
		.withUserConfiguration(ModelConfiguration.class)
		.withBean(MeterRegistry.class, SimpleMeterRegistry::new);

	@Test
	void noRoutesRequiresNoProviderCredentials() {
		this.context.run(app -> assertThat(app.getBean(ModelGateway.class).routes()).isEmpty());
	}

	@Test
	void openRouterRequiresItsOwnCredential() {
		var entry = new ModelConfiguration.Entry("free", ModelConfiguration.Provider.OPENROUTER,
				"google/gemma-4-26b-a4b-it:free", 512, false, false);
		var settings = new ModelConfiguration.Settings(List.of(entry));
		var configuration = new ModelConfiguration();
		var env = new MockEnvironment().withProperty("ZINCC_OPENAI_API_KEY", "fixture-openai");
		var meters = new SimpleMeterRegistry();
		try {
			assertThatThrownBy(() -> configuration.modelGateway(settings, meters, env))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("OPENROUTER_API_KEY");
			env.setProperty("OPENROUTER_API_KEY", "fixture-openrouter");
			assertThat(configuration.modelGateway(settings, meters, env).routes()).singleElement()
				.satisfies(route -> assertThat(route.provider()).isEqualTo("OPENROUTER"));
		}
		finally {
			meters.close();
		}
	}

	@Test
	void configurationBindsCapabilitiesWithoutContactingTheProvider() {
		this.context
			.withPropertyValues("zincc.models.routes[0].id=desk", "zincc.models.routes[0].provider=ollama",
					"zincc.models.routes[0].model=fixture", "zincc.models.routes[0].output-token-limit=64",
					"zincc.models.routes[0].streaming=true")
			.run(app -> {
				var routes = app.getBean(ModelGateway.class).routes();
				assertThat(routes).singleElement().satisfies(route -> {
					assertThat(route.id()).isEqualTo("desk");
					assertThat(route.capabilities()).containsExactlyInAnyOrder(ModelGateway.Capability.TEXT,
							ModelGateway.Capability.TEXT_STREAM);
				});
			});
	}

}
