package io.zincc.research;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Only title and search parameters vary. Retrieved content never supplies a network
 * destination.
 */
class WikipediaSource implements WebResearch.Source {

	static final URI API = URI.create("https://en.wikipedia.org/w/api.php");

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(3))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	private final URI api;

	private final JsonMapper json = new JsonMapper();

	WikipediaSource(URI api) {
		this.api = api;
	}

	private JsonNode query(String parameters) throws IOException, InterruptedException {
		var request = HttpRequest
			.newBuilder(URI.create(api + "?action=query&format=json&formatversion=2&" + parameters))
			.header("User-Agent", "Zincc/0.1 (https://github.com/priyansusatote/Zincc-Bot)")
			.timeout(Duration.ofSeconds(5))
			.GET()
			.build();
		var response = client.send(request, BodyHandlers.limiting(BodyHandlers.ofByteArray(), 131072));
		if (response.statusCode() != 200)
			throw new IOException("Search provider unavailable");
		var query = json.readTree(response.body()).path("query");
		if (!query.isObject())
			throw new IOException("Invalid search response");
		return query;
	}

	static String encoded(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	public List<String> search(String question) throws IOException, InterruptedException {
		var hits = query("list=search&srnamespace=0&srprop=&srlimit=6&srsearch=" + encoded(question)).path("search");
		if (!hits.isArray())
			throw new IOException("Missing search results");
		var titles = new ArrayList<String>();
		for (var hit : hits) {
			var title = hit.path("title").asString("");
			if (!title.isBlank() && title.length() <= 512 && !titles.contains(title))
				titles.add(title);
			if (titles.size() == 6)
				break;
		}
		return titles;
	}

	static String article(String title) {
		return "https://en.wikipedia.org/wiki/" + encoded(title.replace(' ', '_'));
	}

	public WebResearch.Page fetch(String title) throws IOException, InterruptedException {
		var pages = query("prop=extracts&exintro=1&explaintext=1&exchars=3000&redirects=1&titles=" + encoded(title))
			.path("pages");
		if (!pages.isArray() || pages.size() != 1)
			throw new IOException("Missing page");
		var page = pages.get(0);
		String resolved = page.path("title").asString("");
		String extract = page.path("extract").asString("");
		if (resolved.isBlank() || extract.isBlank() || extract.length() > 8192 || page.path("ns").asInt(-1) != 0)
			throw new IOException("Page unavailable");
		return new WebResearch.Page(resolved, article(title), article(resolved), extract);
	}

}
