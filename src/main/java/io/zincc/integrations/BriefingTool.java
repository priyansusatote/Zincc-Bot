package io.zincc.integrations;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import io.zincc.sandbox.SandboxBroker;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("agents")
class BriefingTool {

	static final String VERSION = "2025-06-18";

	private final Environment env;

	private final JsonMapper json;

	BriefingTool(Environment env, JsonMapper json) throws IOException {
		this.env = env;
		this.json = json;

	}

	String run(List<String> sources) {
		String image = env.getProperty("zincc.knowledge.image", "");
		if (!image.matches("sha256:[a-f0-9]{64}"))
			throw new IllegalStateException("Curated worker unavailable");
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor(); var sandbox = SandboxBroker.briefing(image)) {
			var process = sandbox.process();
			var future = tasks.submit(() -> exchange(process.getInputStream(), process.getOutputStream(), sources));
			return future.get(20, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Tool interrupted");
		}
		catch (Exception e) {
			throw new IllegalStateException("Curated tool unavailable");
		}
	}

	String exchange(InputStream raw, OutputStream out, List<String> sources) throws IOException {
		if (sources.size() > 12 || sources.stream().anyMatch(s -> s.length() > 600))
			throw new IOException();
		var input = new BufferedInputStream(raw);
		send(out, 1, "initialize", Map.of("protocolVersion", VERSION, "capabilities", Map.of(), "clientInfo",
				Map.of("name", "zincc", "version", "1")));
		var init = read(input, 1);
		if (!VERSION.equals(init.path("protocolVersion").asString(""))
				|| !init.path("capabilities").path("tools").isObject())
			throw new IOException();
		send(out, 0, "notifications/initialized", Map.of());
		send(out, 2, "tools/list", Map.of());
		var list = read(input, 2);
		var tools = list.path("tools");
		var schema = json.readTree(
				"""
						{"type":"object","properties":{"sources":{"type":"array","items":{"type":"string","maxLength":600},"maxItems":12}},"required":["sources"],"additionalProperties":false}
						""");
		if (list.has("nextCursor") || !tools.isArray() || tools.size() != 1
				|| !"briefing".equals(tools.get(0).path("name").asString(""))
				|| !schema.equals(tools.get(0).path("inputSchema")))
			throw new IOException();
		send(out, 3, "tools/call", Map.of("name", "briefing", "arguments", Map.of("sources", sources)));
		var reply = read(input, 3);
		var c = reply.path("content");
		if (reply.path("isError").asBoolean(false) || !c.isArray() || c.size() != 1
				|| !"text".equals(c.get(0).path("type").asString("")) || !c.get(0).path("text").isString())
			throw new IOException();
		String output = c.get(0).path("text").asString();
		if (output.length() > 7300)
			throw new IOException();
		return output;
	}

	private void send(OutputStream out, int id, String method, Object params) throws IOException {
		var req = new HashMap<String, Object>(Map.of("jsonrpc", "2.0", "method", method, "params", params));
		if (id > 0)
			req.put("id", id);
		out.write(json.writeValueAsBytes(req));
		out.write(10);
		out.flush();
	}

	private JsonNode read(InputStream in, int id) throws IOException {
		var b = new ByteArrayOutputStream();
		for (int c; (c = in.read()) != 10;) {
			if (c < 0 || b.size() >= 40000)
				throw new IOException();
			b.write(c);
		}
		var r = json.readTree(b.toByteArray());
		if (r == null || !"2.0".equals(r.path("jsonrpc").asString("")) || !r.path("id").isIntegralNumber()
				|| r.path("id").asInt() != id || r.has("error") || !r.path("result").isObject())
			throw new IOException();
		return r.get("result");
	}

}
