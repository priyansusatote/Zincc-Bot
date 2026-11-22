package io.zincc.research;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Hosted public-search adapter. Destinations and credentials are operator controlled. */
final class FirecrawlSource implements WebResearch.Source {

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(3))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	private final URI api;

	private final String key;

	private final Set<String> domains;

	private final JsonMapper json = new JsonMapper();

	FirecrawlSource(URI api, String key, Set<String> domains) {
		if (key.isBlank() || domains.isEmpty()
				|| domains.stream().anyMatch(d -> !d.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")))
			throw new IllegalArgumentException("Firecrawl requires a credential and explicit public domains");
		this.api = api;
		this.key = key;
		this.domains = Set.copyOf(domains);
	}

	private JsonNode post(String action, Map<String, Object> body) throws IOException, InterruptedException {
		var request = HttpRequest.newBuilder(api.resolve(action))
			.timeout(Duration.ofSeconds(10))
			.header("Authorization", "Bearer " + key)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
			.build();
		var response = client.send(request, BodyHandlers.limiting(BodyHandlers.ofByteArray(), 262144));
		if (response.statusCode() != 200)
			throw new IOException("Research service unavailable");
		try {
			var root = json.readTree(response.body());
			if (!root.path("success").asBoolean())
				throw new IOException("Research request failed");
			return root.path("data");
		}
		catch (JacksonException ex) {
			throw new IOException("Invalid provider response");
		}
	}

	private boolean allowed(String url) {
		try {
			var uri = URI.create(url);
			return url.length() <= 2048 && "https".equals(uri.getScheme()) && uri.getHost() != null
					&& domains.contains(uri.getHost()) && uri.getUserInfo() == null && uri.getPort() == -1
					&& uri.getFragment() == null;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	public List<String> search(String query) throws IOException, InterruptedException {
		var hits = post("search",
				Map.of("query", query, "limit", 6, "includeDomains", domains, "sources", List.of("web")))
			.path("web");
		if (!hits.isArray())
			throw new IOException("Invalid search response");
		var urls = new ArrayList<String>();
		for (var hit : hits) {
			String url = hit.path("url").asString("");
			if (allowed(url) && !urls.contains(url))
				urls.add(url);
			if (urls.size() == 6)
				break;
		}
		return urls;
	}

	public WebResearch.Page fetch(String url) throws IOException, InterruptedException {
		if (!allowed(url))
			throw new IOException("Unapproved source");
		var data = post("scrape", Map.of("url", url, "formats", List.of("markdown"), "onlyMainContent", true, "maxAge",
				0, "timeout", 7000));
		var meta = data.path("metadata");
		String finalUrl = meta.path("url").asString("");
		String content = data.path("markdown").asString("");
		if (!allowed(finalUrl) || meta.path("statusCode").asInt() != 200 || content.isBlank())
			throw new IOException("Missing verified page");
		return new WebResearch.Page(meta.path("title").asString(finalUrl), url, finalUrl,
				content.substring(0, Math.min(content.length(), 8192)));
	}

}
