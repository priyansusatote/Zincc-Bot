package io.zincc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

	@LocalServerPort
	private int port;

	private HttpResponse<String> get(String path) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
			.timeout(Duration.ofSeconds(5))
			.GET()
			.build();
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	@Test
	void healthReportsOnlyAggregateStatus() throws Exception {
		var response = get("/actuator/health");
		assertThat(response.statusCode()).isEqualTo(200);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("{\"status\":\"UP\"}", response.body());
	}

	@Test
	void environmentEndpointIsUnavailable() throws Exception {
		assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
	}

}
