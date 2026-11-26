package io.zincc.agents;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;
import io.zincc.sandbox.SandboxBroker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class FrameworkPlanner {

	private FrameworkPlanner() {
	}

	static String plan(String image, String framework, String prompt, Supplier<String> inference, JsonMapper json) {
		try (var worker = SandboxBroker.framework(image, framework)) {
			var process = worker.process();
			var input = new BufferedInputStream(process.getInputStream());
			var output = process.getOutputStream();
			output.write((json.writeValueAsString(Map.of("prompt", prompt)) + "\n").getBytes(StandardCharsets.UTF_8));
			output.flush();
			var request = read(input, json);
			if (request.size() != 1 || !request.path("op").asString("").equals("infer"))
				throw new IOException("Expected inference request");
			output.write(
					(json.writeValueAsString(Map.of("text", inference.get())) + "\n").getBytes(StandardCharsets.UTF_8));
			output.close();
			var result = read(input, json);
			if (result.size() != 2 || !result.path("op").asString("").equals("propose")
					|| !result.path("step").isObject())
				throw new IOException("Expected planning proposal");
			return json.writeValueAsString(result.get("step"));
		}
		catch (IOException e) {
			throw new IllegalStateException("Framework worker unavailable", e);
		}
	}

	private static JsonNode read(BufferedInputStream input, JsonMapper json) throws IOException {
		var bytes = new ByteArrayOutputStream();
		while (bytes.size() < 16000) {
			int b = input.read();
			if (b == '\n')
				return json.readTree(bytes.toByteArray());
			if (b < 0)
				break;
			bytes.write(b);
		}
		throw new IOException("Incomplete framework frame");
	}

}
