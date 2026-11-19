package io.zincc.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ZINCC_MODEL_SMOKE_ROUTE", matches = ".+")
class ConfiguredModelSmokeTest {

	@Autowired
	private ModelGateway gateway;

	@Test
	void configuredRouteReturnsText() {
		String route = System.getenv("ZINCC_MODEL_SMOKE_ROUTE");
		var result = this.gateway.text(route, "Reply with the single word hello.").block();
		assertThat(result.route().id()).isEqualTo(route);
		assertThat(result.text()).isNotBlank();
	}

}
