package io.zincc.model;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRegistryConfigurationTest {

	private final ApplicationContextRunner context = new ApplicationContextRunner()
		.withUserConfiguration(ModelConfiguration.class)
		.withBean(MeterRegistry.class, SimpleMeterRegistry::new);

	@Test
	void noRoutesRequiresNoProviderCredentials() {
		this.context.run(app -> assertThat(app.getBean(ModelGateway.class).routes()).isEmpty());
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
