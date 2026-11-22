package io.zincc.eval;

import java.io.IOException;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class FrozenCases {

	private FrozenCases() {
	}

	public static Stream<JsonNode> group(String name) throws IOException {
		try (var input = FrozenCases.class.getResourceAsStream("/eval/p2-v1.json")) {
			var rows = new JsonMapper().readTree(input).path("groups").path(name);
			if (rows.size() != 10)
				throw new IllegalStateException("Expected ten frozen cases for " + name);
			return rows.valueStream();
		}
	}

}
