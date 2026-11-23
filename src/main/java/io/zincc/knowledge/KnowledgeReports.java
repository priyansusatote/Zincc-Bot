package io.zincc.knowledge;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;
import static io.zincc.knowledge.KnowledgeWorker.Operation.*;
import static io.zincc.knowledge.KnowledgeStore.*;
import tools.jackson.databind.JsonNode;

@Service
@Profile("knowledge")
public class KnowledgeReports {

	record Row(int sourceRow, JsonNode values, JsonNode cells) {
	}

	record Report(Dashboard dashboard, int page, JsonNode headers, List<Row> rows, JsonNode groups, String total,
			int missing, String error) {
	}

	private final KnowledgeStore store;

	private final KnowledgeWorker worker;

	private final JsonMapper json;

	KnowledgeReports(KnowledgeStore store, KnowledgeWorker worker, JsonMapper json) {
		this.store = store;
		this.worker = worker;
		this.json = json;
	}

	public String refresh(UUID workspace, UUID owner, UUID document, UUID dashboard) {
		var report = calculate(new Scope(workspace, owner), document, dashboard);
		if (!report.error().isEmpty())
			throw conflict("Review dashboard settings");
		return report.dashboard().name() + " · revision " + report.dashboard().revision() + " · " + report.total() + " "
				+ report.dashboard().unit() + " · source page " + report.page();
	}

	Report calculate(Scope scope, UUID doc, UUID id) {
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
			throw new IllegalStateException("Invalid document worker output");
		var filtered = new ArrayList<Row>();
		for (var ix : result.path("rows")) {
			int row = ix.asInt();
			if (row < 1 || row >= rows.size())
				throw new IllegalStateException("Invalid document worker output");
			filtered.add(new Row(row + 1, rows.get(row), dataset.data().path("cells").get(row)));
		}
		return new Report(config, dataset.page(), rows.get(0), List.copyOf(filtered), result.path("groups"),
				result.path("total").asString(), result.path("missing").asInt(), "");
	}

}
