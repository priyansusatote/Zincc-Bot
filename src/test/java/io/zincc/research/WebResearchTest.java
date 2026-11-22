package io.zincc.research;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class WebResearchTest {

	@Test
	void boundedReadsResolveTitlesAndPreserveCacheProvenance() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var calls = new AtomicInteger();
		server.createContext("/api", e -> {
			try (e) {
				String q = e.getRequestURI().getRawQuery();
				byte[] bytes = (q.contains("list=search")
						? "{\"query\":{\"search\":[{\"title\":\"Alias\"},{\"title\":\"Broken\"},{\"title\":\"Missing\"},{\"title\":\"Fourth\"}]}}"
						: "{\"query\":{\"pages\":[{\"ns\":0,\"title\":\"Earth\",\"extract\":\"Earth is a planet. Oceans cover much of Earth.\"}]}}")
					.getBytes(StandardCharsets.UTF_8);
				if (q.contains("titles=Alias"))
					calls.incrementAndGet();
				int status = q.contains("titles=Broken") || q.contains("titles=Missing") ? 503 : 200;
				e.sendResponseHeaders(status, bytes.length);
				e.getResponseBody().write(bytes);
			}
		});
		server.start();
		try {
			var research = new WebResearch(
					new WikipediaSource(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api")),
					Clock.systemUTC(), true);
			var result = research.answer("Oceans on Earth?", false);
			assertThat(result.discovered()).isEqualTo(4);
			assertThat(result.attempted()).isEqualTo(3);
			assertThat(result.fetched()).isOne();
			var citation = result.citations().getFirst();
			assertThat(citation.quote()).isEqualTo("Oceans cover much of Earth.");
			assertThat(citation.requestedUrl()).endsWith("/Alias");
			assertThat(citation.finalUrl()).endsWith("/Earth");
			assertThat(citation.cached()).isFalse();
			var cached = research.answer("Oceans?", false).citations().getFirst();
			assertThat(cached.cached()).isTrue();
			assertThat(cached.observedAt()).isEqualTo(citation.observedAt());
			assertThat(research.answer("Oceans?", true).citations().getFirst().cached()).isFalse();
			assertThat(calls.get()).isEqualTo(2);
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void disabledDeploymentNeverCallsSourceAndSearchFailureIsExplicit() {
		var calls = new AtomicInteger();
		var source = new WebResearch.Source() {
			public List<String> search(String q) throws IOException {
				calls.incrementAndGet();
				throw new IOException();
			}

			public WebResearch.Page fetch(String t) {
				throw new AssertionError();
			}
		};
		assertThatThrownBy(() -> new WebResearch(source, Clock.systemUTC(), false).answer("private", true))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("403");
		assertThat(calls.get()).isZero();
		var enabled = new WebResearch(source, Clock.systemUTC(), true);
		assertThatThrownBy(() -> enabled.answer(" ", false)).hasMessageContaining("400");
		assertThatThrownBy(() -> enabled.answer("topic", false)).hasMessageContaining("502");
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "ZINCC_WIKI_SMOKE", matches = "true")
	void livePublicSearchReturnsCitedEvidence() {
		var research = new WebResearch(new WikipediaSource(WikipediaSource.API), Clock.systemUTC(), true);
		var result = research.answer("plate tectonics", true);
		assertThat(result.citations()).isNotEmpty();
		System.out.printf("PUBLIC SEARCH discovered=%d read=%d first=%s%n", result.discovered(), result.fetched(),
				result.citations().getFirst().finalUrl());
	}

}
