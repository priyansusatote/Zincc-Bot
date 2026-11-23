package io.zincc.integrations;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("agents")
class BriefingTool {

	static final String VERSION = "2025-06-18";

	private final Environment env;

	private final JsonMapper json;

	private final String script;

	BriefingTool(Environment env, JsonMapper json) throws IOException {
		this.env = env;
		this.json = json;
		this.script = new ClassPathResource("integrations/briefing_mcp.py").getContentAsString(StandardCharsets.UTF_8);
	}

	String run(List<String> sources) {
		String image = env.getProperty("zincc.knowledge.image", "");
		if (!image.matches("sha256:[a-f0-9]{64}"))
			throw new IllegalStateException("Curated worker unavailable");
		String name = "zincc-brief-" + UUID.randomUUID();
		try {
			var process = new ProcessBuilder("docker", "run", "--rm", "-i", "--pull=never", "--name", name,
					"--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
					"--user=65532:65532", "--pids-limit=12", "--memory=64m", "--memory-swap=64m", "--cpus=0.5",
					"--log-driver=none", "--entrypoint=/usr/bin/timeout", image, "--kill-after=2s", "25s", "python",
					"-I", "-B", "-u", "-c",
					"exec(__import__('base64').b64decode('"
							+ Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8)) + "'))")
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
				var future = tasks.submit(() -> exchange(process.getInputStream(), process.getOutputStream(), sources));
				try {
					return future.get(20, TimeUnit.SECONDS);
				}
				finally {
					process.getOutputStream().close();
					if (!process.waitFor(2, TimeUnit.SECONDS)) {
						process.destroyForcibly();
						var cleanup = new ProcessBuilder("docker", "rm", "-f", name)
							.redirectError(ProcessBuilder.Redirect.DISCARD)
							.redirectOutput(ProcessBuilder.Redirect.DISCARD)
							.start();
						if (!cleanup.waitFor(3, TimeUnit.SECONDS))
							cleanup.destroyForcibly();
					}
				}
			}
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
