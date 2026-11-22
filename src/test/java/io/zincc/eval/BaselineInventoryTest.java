package io.zincc.eval;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BaselineInventoryTest {

	@Test
	void freezeSixtyDistinctCasesAndKeepFutureWorkExplicit() throws Exception {
		var ids = new HashSet<String>();
		for (String name : List.of("research", "structured", "local", "recovery", "pdf", "inbox")) {
			var cases = FrozenCases.group(name).toList();
			assertThat(cases.stream().filter(c -> c.path("split").asString().equals("holdout")).count()).isEqualTo(2);
			for (var row : cases) {
				assertThat(ids.add(row.path("id").asString())).isTrue();
				assertThat(row.has("prerequisite")).isEqualTo(name.equals("pdf") || name.equals("inbox"));
				if (row.has("prerequisite"))
					assertThat(row.path("gold").asString()).isNotBlank();
			}
		}
		assertThat(ids).hasSize(60);
	}

}
