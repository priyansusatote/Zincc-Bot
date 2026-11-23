package io.zincc.integrations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

final class SkillCatalog {

	record Skill(String name, int version, List<String> permissions, String instructions, String digest) {
	}

	static List<Skill> load(JsonMapper json) {
		var catalog = new ArrayList<Skill>();
		for (var entry : Map
			.of("briefing-1", "4a561801e0cedae6bbac1cbb7f1d30cdc64ad635ae9d70fa2298fc71c3109eef", "briefing-2",
					"347c5b975ec85bcc2d5abe6635d3b2ad7b915e929d081d0a79cf539db86a8e3f", "report-1",
					"0a3e2b3a87462067fd0fce6d326634c0dc52f0ed39480cdcf89e7c5a94ff2f6f")
			.entrySet()) {
			try {
				String content = new ClassPathResource("integrations/" + entry.getKey() + ".json")
					.getContentAsString(StandardCharsets.UTF_8)
					.replace("\r\n", "\n")
					.strip();
				String digest = HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
				var manifest = json.readTree(content);
				String name = ProviderClient.required(manifest, "name");
				int version = manifest.path("version").asInt();
				var perms = new ArrayList<String>();
				for (var p : manifest.path("permissions"))
					perms.add(p.asString());
				if (!digest.equals(entry.getValue()) || manifest.size() != 4
						|| !entry.getKey().equals(name + "-" + version) || perms.isEmpty()
						|| !Set.of("gmail", "slack", "calendar", "dashboard").containsAll(perms))
					throw new IllegalArgumentException();
				catalog.add(new Skill(name, version, List.copyOf(perms),
						ProviderClient.required(manifest, "instructions"), digest));
			}
			catch (Exception ex) {
				throw new IllegalStateException("Invalid curated skill " + entry.getKey(), ex);
			}
		}
		catalog.sort(Comparator.comparing(Skill::name).thenComparingInt(Skill::version));
		return List.copyOf(catalog);
	}

}
