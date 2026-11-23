package io.zincc.agents;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import io.zincc.agents.AgentStore.Scope;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One operator-provisioned OAuth grant for one actor and task list. */
@Component
@Profile("agents")
class TasksConnector {

	private final Environment env;

	private final JsonMapper json;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(3))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	private String access;

	private Instant expires = Instant.EPOCH;

	private UUID cachedGeneration;

	TasksConnector(Environment env, JsonMapper json) {
		this.env = env;
		this.json = json;
	}

	boolean sandbox() {
		return env.getProperty("zincc.agent.sandbox", Boolean.class, false);
	}

	String reference() {
		return sandbox() ? "sandbox-tasks" : "google-tasks";
	}

	String resource() {
		return env.getProperty("zincc.tasks.list", "");
	}

	boolean configured(Scope scope) {
		return (sandbox() || !env.getProperty("zincc.local-only", Boolean.class, false))
				&& scope.workspace().toString().equals(env.getProperty("zincc.tasks.workspace"))
				&& scope.actor().toString().equals(env.getProperty("zincc.tasks.actor"))
				&& resource().matches("[A-Za-z0-9_@=-]{1,200}")
				&& !env.getProperty("ZINCC_TASKS_REFRESH_TOKEN", "").isBlank()
				&& !env.getProperty("ZINCC_TASKS_CLIENT_SECRET", "").isBlank()
				&& !env.getProperty("ZINCC_TASKS_CLIENT_ID", "").isBlank();
	}

	private String origin() {
		return sandbox() ? "http://127.0.0.1:" + env.getProperty("zincc.tasks.sandbox-port", Integer.class, 18462)
				: "https://tasks.googleapis.com";
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private synchronized String token(UUID generation) throws IOException, InterruptedException {
		if (generation.equals(cachedGeneration) && Instant.now().isBefore(expires))
			return access;
		String form = "grant_type=refresh_token&refresh_token="
				+ encode(env.getRequiredProperty("ZINCC_TASKS_REFRESH_TOKEN")) + "&client_id="
				+ encode(env.getRequiredProperty("ZINCC_TASKS_CLIENT_ID")) + "&client_secret="
				+ encode(env.getRequiredProperty("ZINCC_TASKS_CLIENT_SECRET"));
		URI endpoint = URI.create(sandbox() ? origin() + "/token" : "https://oauth2.googleapis.com/token");
		var reply = request(HttpRequest.newBuilder(endpoint)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form)));
		String value = reply.path("access_token").asString("");
		if (value.isBlank() || !"Bearer".equalsIgnoreCase(reply.path("token_type").asString("")))
			throw new IOException("OAuth grant unavailable");
		cachedGeneration = generation;
		access = value;
		expires = Instant.now().plusSeconds(Math.clamp(reply.path("expires_in").asLong(0) - 30, 0, 3600));
		return value;
	}

	private JsonNode request(HttpRequest.Builder builder) throws IOException, InterruptedException {
		var response = http.send(builder.timeout(Duration.ofSeconds(8)).build(),
				BodyHandlers.limiting(BodyHandlers.ofByteArray(), 262144));
		if (response.statusCode() / 100 != 2)
			throw new IOException("Provider request failed");
		try {
			return json.readTree(response.body());
		}
		catch (tools.jackson.core.JacksonException ex) {
			throw new IOException("Invalid provider response");
		}
	}

	private HttpRequest.Builder call(UUID generation, String suffix) throws IOException, InterruptedException {
		return HttpRequest.newBuilder(URI.create(origin() + "/tasks/v1/lists/" + resource() + "/tasks" + suffix))
			.header("Authorization", "Bearer " + token(generation));
	}

	JsonNode list(UUID generation) throws IOException, InterruptedException {
		var reply = request(call(generation, "?maxResults=100&showCompleted=true&showHidden=true").GET());
		if (!reply.path("items").isArray() && !"tasks#tasks".equals(reply.path("kind").asString("")))
			throw new IOException("Invalid list");
		return reply;
	}

	String create(UUID generation, UUID run, String title) throws IOException, InterruptedException {
		var payload = Map.of("title", title, "notes", marker(run));
		var reply = request(call(generation, "").header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))));
		return receipt(reply, run, title);
	}

	static String marker(UUID run) {
		return "Zincc action " + run;
	}

	static String receipt(JsonNode task, UUID run, String title) {
		String id = task.path("id").asString("");
		return task.path("notes").asString("").equals(marker(run)) && task.path("title").asString("").equals(title)
				&& !task.path("deleted").asBoolean(false) && !id.isBlank() && id.length() <= 512 ? id : "";
	}

	String reconcile(UUID generation, UUID run, String title) throws IOException, InterruptedException {
		String found = "";
		for (var task : list(generation).path("items")) {
			String id = receipt(task, run, title);
			if (!id.isEmpty()) {
				if (!found.isEmpty())
					return "";
				found = id;
			}
		}
		return found;
	}

}
