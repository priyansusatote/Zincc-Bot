package io.zincc.agents;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class TasksConnectorTest {

	@Test
	void refreshIsBoundToOneGrantAndReceiptRequiresExactMarkerAndTitle() throws Exception {
		var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var refreshes = new AtomicInteger();
		var bearer = new AtomicReference<String>();
		var form = new AtomicReference<String>();
		var run = UUID.randomUUID();
		var subject = new AgentStore.Scope(UUID.randomUUID(), UUID.randomUUID());
		var json = new JsonMapper();
		http.createContext("/", e -> {
			try (e) {
				String body;
				if (e.getRequestURI().getPath().equals("/token")) {
					form.set(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
					body = "{\"access_token\":\"bearer" + refreshes.incrementAndGet()
							+ "\",\"token_type\":\"Bearer\",\"expires_in\":300}";
				}
				else if (e.getRequestURI().getPath().equals("/tasks/v1/lists/personal/tasks")) {
					bearer.set(e.getRequestHeaders().getFirst("Authorization"));
					if (e.getRequestMethod().equals("POST")) {
						var sent = json.readTree(e.getRequestBody());
						body = json.writeValueAsString(java.util.Map.of("id", "receipt", "title",
								sent.path("title").asString(), "notes", sent.path("notes").asString()));
					}
					else
						body = "{\"kind\":\"tasks#tasks\",\"items\":[]}";
				}
				else {
					e.sendResponseHeaders(404, -1);
					return;
				}
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				e.sendResponseHeaders(200, bytes.length);
				e.getResponseBody().write(bytes);
			}
		});
		http.start();
		try {
			var env = new MockEnvironment().withProperty("zincc.agent.sandbox", "true")
				.withProperty("zincc.tasks.sandbox-port", Integer.toString(http.getAddress().getPort()))
				.withProperty("zincc.tasks.workspace", subject.workspace().toString())
				.withProperty("zincc.tasks.actor", subject.actor().toString())
				.withProperty("zincc.tasks.list", "personal")
				.withProperty("ZINCC_TASKS_CLIENT_ID", "client")
				.withProperty("ZINCC_TASKS_CLIENT_SECRET", "secret")
				.withProperty("ZINCC_TASKS_REFRESH_TOKEN", "test-token");
			var tasks = new TasksConnector(env, json);
			assertThat(tasks.configured(subject)).isTrue();
			assertThat(tasks.configured(new AgentStore.Scope(subject.workspace(), UUID.randomUUID()))).isFalse();
			var generation = UUID.randomUUID();
			tasks.list(generation);
			assertThat(tasks.create(generation, run, "Review report")).isEqualTo("receipt");
			assertThat(form.get()).contains("grant_type=refresh_token", "refresh_token=test-token");
			assertThat(refreshes.get()).isOne();
			assertThat(bearer.get()).isEqualTo("Bearer bearer1");
			tasks.list(UUID.randomUUID());
			assertThat(refreshes.get()).isEqualTo(2);
			env.setProperty("zincc.agent.sandbox", "false");
			env.setProperty("zincc.local-only", "true");
			assertThat(tasks.configured(subject)).isFalse();
			assertThat(TasksConnector.receipt(json.readTree("{\"id\":\"other\",\"title\":\"Review report\"}"), run,
					"Review report"))
				.isEmpty();
		}
		finally {
			http.stop(0);
		}
	}

}
