package io.zincc.model;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Comparative observation only. A provider failure is a recorded outcome. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ZINCC_COMPARE_ROUTES", matches = ".+")
class RouteProbeTest {

	@Autowired
	ModelGateway gateway;

	@Test
	void compareUnderTheSamePrompt() {
		for (String id : System.getenv("ZINCC_COMPARE_ROUTES").split(",")) {
			int passed = 0;
			var started = System.nanoTime();
			for (var sample : List.of(List.of("Return just the sum of 19 and 23 as digits.", "42"),
					List.of("Return just the second word of the list: red lime blue.", "lime"))) {
				try {
					var result = gateway.text(id.strip(), sample.getFirst()).block();
					if (result.text().strip().equalsIgnoreCase(sample.getLast()))
						passed++;
					System.out.printf("PROBE route=%s matched=%s usage=%s%n", id,
							result.text().strip().equalsIgnoreCase(sample.getLast()), result.usage());
				}
				catch (ModelGateway.GatewayException e) {
					System.out.printf("PROBE route=%s failure=%s%n", id, e.failure());
				}
			}
			System.out.printf("PROBE route=%s success=%d/2 elapsedMs=%d%n", id, passed,
					(System.nanoTime() - started) / 1000000);
		}
	}

}
