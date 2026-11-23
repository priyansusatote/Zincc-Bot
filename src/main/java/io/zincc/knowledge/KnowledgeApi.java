package io.zincc.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.zincc.identity.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static io.zincc.knowledge.KnowledgeStore.*;
import static io.zincc.knowledge.KnowledgeWorker.Operation.*;

@RestController
@Profile("oidc & knowledge")
@RequestMapping("/api/workspaces/{workspace}/knowledge")
class KnowledgeApi {

	private final KnowledgeStore store;

	private final KnowledgeWorker worker;

	private final SessionPrincipal principals;

	private final JsonMapper json;

	KnowledgeApi(KnowledgeStore store, KnowledgeWorker worker, SessionPrincipal principals, JsonMapper json) {
		this.store = store;
		this.worker = worker;
		this.principals = principals;
		this.json = json;
	}

	private Scope scope(OidcUser user, UUID workspace) {
		return new Scope(workspace, principals.resolve(user).orElseThrow(KnowledgeStore::missing));
	}

	@GetMapping
	List<Document> list(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace) {
		return store.documents(scope(user, workspace));
	}

	@PostMapping
	Document upload(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@RequestParam("file") MultipartFile file) throws IOException {
		var scope = scope(user, workspace);
		String name = file.getOriginalFilename();
		if (!valid(name, 170) || name.contains("/") || name.contains("\\") || file.isEmpty())
			throw bad("Invalid file");
		if (file.getSize() > KnowledgeWorker.INPUT_LIMIT)
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
		var source = file.getBytes();
		var claim = store.claim(scope, name, source, worker.revision());
		if (claim.started() != null) {
			try {
				var result = worker.run(PARSE, source);
				String error = result.path("error").asString("");
				if (!error.isEmpty())
					store.failed(scope, claim, switch (error) {
						case "encrypted_pdf" -> "Password-protected PDF. Upload an unlocked copy.";
						case "no_text_layer" -> "No extractable text. Upload a searchable PDF.";
						case "too_many_pages" -> "PDF exceeds the 60-page limit.";
						default -> "Invalid or unreadable PDF.";
					});
				else {
					validateParsed(result);
					store.publish(scope, claim, result);
				}
			}
			catch (RuntimeException ex) {
				store.failed(scope, claim, "Document processing failed. Upload the same file to retry.");
				throw ex;
			}
		}
		return store.document(scope, claim.id());
	}

	private static void validateParsed(JsonNode result) {
		int pages = result.path("pages").asInt();
		var chunks = result.path("chunks");
		var tables = result.path("tables");
		if (pages < 1 || pages > 60 || !chunks.isArray() || chunks.size() > 360 || !tables.isArray()
				|| tables.size() > 24 || !result.path("missing").isArray() || !result.path("scanned").isArray()
				|| (chunks.isEmpty() && tables.isEmpty()))
			throw invalidWorker();
		for (var chunk : chunks)
			if (!pageIn(chunk, pages) || !validExcerpt(chunk.path("text")) || !vector(chunk.path("vector")))
				throw invalidWorker();
		for (var table : tables) {
			var rows = table.path("rows");
			var cells = table.path("cells");
			if (!pageIn(table, pages) || !rows.isArray() || rows.size() < 2 || rows.size() > 101
					|| cells.size() != rows.size())
				throw invalidWorker();
			int width = rows.get(0).size();
			if (width < 2 || width > 12)
				throw invalidWorker();
			for (int i = 0; i < rows.size(); i++)
				if (!rows.get(i).isArray() || rows.get(i).size() != width || cells.get(i).size() != width
						|| rows.get(i).valueStream().anyMatch(x -> !x.isString() || x.asString().length() > 250))
					throw invalidWorker();
		}
	}

	private static boolean pageIn(JsonNode item, int pages) {
		int page = item.path("page").asInt();
		return page >= 1 && page <= pages;
	}

	private static boolean validExcerpt(JsonNode value) {
		return value.isString() && !value.asString().isBlank() && value.asString().length() <= 1600;
	}

	private static boolean vector(JsonNode value) {
		return value.isArray() && value.size() == 384
				&& value.valueStream().allMatch(n -> n.isNumber() && Math.abs(n.asDouble()) <= 1);
	}

	private static ResponseStatusException invalidWorker() {
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid document worker output");
	}

	@DeleteMapping("/{doc}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID doc) {
		store.delete(scope(user, workspace), doc);
	}

	@GetMapping("/{doc}/source")
	ResponseEntity<byte[]> source(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID doc) {
		return attachment(store.source(scope(user, workspace), doc), "document.pdf", MediaType.APPLICATION_PDF);
	}

	@GetMapping("/{doc}/pages/{page}")
	List<Evidence> page(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID doc,
			@PathVariable int page) {
		return store.page(scope(user, workspace), doc, page);
	}

	record Search(String query) {
	}

	@PostMapping("/{doc}/search")
	List<Evidence> search(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID doc,
			@RequestBody Search search) {
		if (!valid(search.query, 300))
			throw bad("Invalid question");
		var scope = scope(user, workspace);
		store.scoped(scope, () -> store.ready(doc));
		var vector = worker.run(EMBED, json.writeValueAsBytes(Map.of("query", search.query))).path("vector");
		if (!vector(vector))
			throw invalidWorker();
		return store.retrieve(scope, doc, search.query, vector);
	}

	@GetMapping("/{doc}/datasets")
	List<Dataset> datasets(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID doc) {
		return store.datasets(scope(user, workspace), doc);
	}

	@GetMapping("/{doc}/dashboards")
	List<Dashboard> dashboards(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID doc) {
		return store.dashboards(scope(user, workspace), doc);
	}

	@PutMapping("/{doc}/dashboards/{id}")
	Map<String, Integer> save(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID doc, @PathVariable UUID id, @RequestBody Settings settings) {
		return Map.of("revision", store.save(scope(user, workspace), doc, id, settings));
	}

	record Row(int sourceRow, JsonNode values, JsonNode cells) {
	}

	record Report(Dashboard dashboard, int page, JsonNode headers, List<Row> rows, JsonNode groups, String total,
			int missing, String error) {
	}

	@GetMapping("/{doc}/dashboards/{id}")
	Report report(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace, @PathVariable UUID doc,
			@PathVariable UUID id) {
		return calculate(scope(user, workspace), doc, id);
	}

	private Report calculate(Scope scope, UUID doc, UUID id) {
		var snapshot = store.snapshot(scope, doc, id);
		var config = snapshot.dashboard();
		var dataset = snapshot.dataset();
		var rows = dataset.data().path("rows");
		var args = json.createObjectNode();
		args.set("rows", rows);
		args.put("column", config.measure())
			.put("category", config.category())
			.put("selected", config.selected())
			.put("unit", config.unit());
		var result = worker.run(AGGREGATE, json.writeValueAsBytes(args));
		if (!store.snapshot(scope, doc, id).dashboard().equals(config))
			throw conflict("Dashboard revision changed");
		String error = result.path("error").asString("");
		if (error.equals("ambiguous_number"))
			return new Report(config, dataset.page(), rows.get(0), List.of(), json.createArrayNode(), null, 0,
					"Check the unit or number format on table row " + result.path("row").asInt() + ".");
		if (error.equals("mixed_or_mismatched_units"))
			return new Report(config, dataset.page(), rows.get(0), List.of(), json.createArrayNode(), null, 0,
					"Select rows with one unit and match it to the dashboard unit.");
		if (!error.isEmpty() || !result.path("total").isString() || !result.path("rows").isArray()
				|| !result.path("groups").isArray())
			throw invalidWorker();
		var filtered = new ArrayList<Row>();
		for (var ix : result.path("rows")) {
			int row = ix.asInt();
			if (row < 1 || row >= rows.size())
				throw invalidWorker();
			filtered.add(new Row(row + 1, rows.get(row), dataset.data().path("cells").get(row)));
		}
		return new Report(config, dataset.page(), rows.get(0), List.copyOf(filtered), result.path("groups"),
				result.path("total").asString(), result.path("missing").asInt(), "");
	}

	@GetMapping("/{doc}/dashboards/{id}/export")
	ResponseEntity<byte[]> export(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@PathVariable UUID doc, @PathVariable UUID id, @RequestParam int revision) {
		var report = calculate(scope(user, workspace), doc, id);
		if (report.dashboard.revision() != revision)
			throw conflict("Dashboard revision changed");
		if (!report.error.isEmpty())
			throw conflict(report.error);
		var csv = new StringBuilder();
		csvRow(csv, report.headers, "Source page", "Table row");
		for (Row row : report.rows)
			csvRow(csv, row.values, Integer.toString(report.page), Integer.toString(row.sourceRow));
		return attachment(csv.toString().getBytes(StandardCharsets.UTF_8), "dashboard.csv",
				new MediaType("text", "csv", StandardCharsets.UTF_8));
	}

	private static void csvRow(StringBuilder csv, JsonNode values, String... extra) {
		for (var value : values) {
			String cell = value.asString("");
			if (cell.stripLeading().matches("(?s)^[=+@-].*") && !cell.matches("[+-]?[0-9]+(?:\\.[0-9]+)?"))
				cell = "'" + cell;
			csv.append('"').append(cell.replace("\"", "\"\"")).append("\",");
		}
		csv.append(String.join(",", extra)).append("\r\n");
	}

	private static ResponseEntity<byte[]> attachment(byte[] data, String name, MediaType type) {
		return ResponseEntity.ok()
			.contentType(type)
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(data);
	}

	private static ResponseStatusException bad(String reason) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
	}

}
