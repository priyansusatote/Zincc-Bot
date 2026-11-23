package io.zincc.knowledge;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static io.zincc.knowledge.KnowledgeWorker.Operation.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ZINCC_KNOWLEDGE_TEST_IMAGE", matches = "sha256:[a-f0-9]{64}")
class KnowledgeWorkerTest {

	private final JsonMapper json = new JsonMapper();

	private final KnowledgeWorker worker = new KnowledgeWorker(System.getenv("ZINCC_KNOWLEDGE_TEST_IMAGE"), json);

	@Test
	void parserAndLocalEmbeddingsRankAnUnseenParaphrase() throws Exception {
		var pdf = worker.run(PARSE, Files.readAllBytes(Path.of("target/p4-invoices.pdf")));
		assertThat(pdf.path("error").isMissingNode()).as("Worker result: %s", pdf.path("error")).isTrue();
		assertThat(pdf.path("pages").asInt()).isEqualTo(2);
		assertThat(pdf.path("tables").get(0).path("rows").get(1).get(1).asString()).isEqualTo("12.25");
		assertThat(pdf.path("tables").get(0).path("cells").get(1).get(1).size()).isEqualTo(4);
		String[] queries = { "cost of buying goods", "the organization's buying costs",
				"invoices from the north branch", "where do flightless seabirds live", "shoreline habitat",
				"penguin colony" };
		for (int i = 0; i < queries.length; i++) {
			var request = json.createObjectNode().put("query", queries[i]);
			var vector = worker.run(EMBED, json.writeValueAsBytes(request)).path("vector");
			assertThat(vector.size()).isEqualTo(384);
			double first = similarity(vector, pdf.path("chunks").get(0).path("vector"));
			double second = similarity(vector, pdf.path("chunks").get(1).path("vector"));
			assertThat(first > second).as("top page for %s", queries[i]).isEqualTo(i < 3);
		}
	}

	@Test
	void exactMathDistinguishesMissingValuesAndRejectsMixedUnitsAndUnrecognizedInput() {
		var args = json.readTree("""
				{"rows":[["Region","Price","Currency"],["North","12.25","USD"],["North","4.75","USD"],
				["South","","USD"],["South","-2.50","USD"]],"column":1,"category":0,"selected":null,"unit":"USD"}
				""");
		var all = worker.run(AGGREGATE, json.writeValueAsBytes(args));
		assertThat(all.path("total").asString()).isEqualTo("14.50");
		assertThat(all.path("missing").asInt()).isOne();
		var filtered = (tools.jackson.databind.node.ObjectNode) args;
		filtered.put("selected", "North");
		assertThat(worker.run(AGGREGATE, json.writeValueAsBytes(filtered)).path("total").asString()).isEqualTo("17.00");
		filtered.put("unit", "EUR");
		assertThat(worker.run(AGGREGATE, json.writeValueAsBytes(filtered)).path("error").asString())
			.isEqualTo("mixed_or_mismatched_units");
		assertThat(worker.run(PARSE, "not a PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8))
			.path("error")
			.asString()).isEqualTo("invalid_pdf");
	}

	private static double similarity(JsonNode a, JsonNode b) {
		double result = 0;
		for (int i = 0; i < a.size(); i++)
			result += a.get(i).asDouble() * b.get(i).asDouble();
		return result;
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "ZINCC_KNOWLEDGE_OCR_TEST", matches = "true")
	void imageOnlyPdfUsesOcrWithPageProvenance() throws Exception {
		var result = worker.run(PARSE, Files.readAllBytes(Path.of("target/p4-scan.pdf")));
		assertThat(result.path("scanned").size()).isOne();
		assertThat(result.path("scanned").get(0).asInt()).isOne();
		assertThat(result.path("chunks").get(0).path("text").asString()).containsIgnoringCase("Aster invoices");
		assertThat(result.path("tables")).isEmpty();
	}

}
