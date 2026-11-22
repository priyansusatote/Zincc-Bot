package io.zincc.research;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class FirecrawlSourceTest {

	@Test
	void onlyApprovedResultsAreScrapedAndFinalDestinationsAreChecked() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var reads = new AtomicInteger();
		var search = new AtomicReference<JsonNode>();
		var scrape = new AtomicReference<JsonNode>();
		var auth = new AtomicReference<String>();
		server.createContext("/v2/", e -> {
			try (e) {
				var body = new JsonMapper().readTree(e.getRequestBody());
				auth.set(e.getRequestHeaders().getFirst("Authorization"));
				String response;
				if (e.getRequestURI().getPath().endsWith("/search")) {
					search.set(body);
					response = """
							{"success":true,"data":{"web":[{"url":"https://docs.example/a"},{"url":"https://other.example/private"},{"url":"http://docs.example/b"},{"url":"https://docs.example/redirect"}]}}
							""";
				}
				else {
					reads.incrementAndGet();
					scrape.set(body);
					String finalUrl = body.path("url").asString().endsWith("/a") ? "https://docs.example/earth"
							: "https://other.example/b";
					response = """
							{"success":true,"data":{"markdown":"Earth is a planet.","metadata":{"title":"Earth","url":"%s","statusCode":200}}}
							"""
						.formatted(finalUrl);
				}
				byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
				e.sendResponseHeaders(200, bytes.length);
				e.getResponseBody().write(bytes);
			}
		});
		server.start();
		try {
			var source = new FirecrawlSource(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/"),
					"test", Set.of("docs.example"));
			var result = new WebResearch(source, Clock.systemUTC(), true).answer("Earth", true);
			assertThat(auth.get()).isEqualTo("Bearer test");
			assertThat(search.get().path("includeDomains").get(0).asString()).isEqualTo("docs.example");
			assertThat(scrape.get().path("maxAge").asInt(-1)).isZero();
			assertThat(reads.get()).isEqualTo(2);
			assertThat(result.fetched()).isOne();
			assertThat(result.citations().getFirst().finalUrl()).endsWith("/earth");
			assertThat(result.citations().getFirst().requestedUrl()).endsWith("/a");
		}
		finally {
			server.stop(0);
		}
	}

}
