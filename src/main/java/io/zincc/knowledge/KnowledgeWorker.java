package io.zincc.knowledge;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("knowledge")
class KnowledgeWorker {

	static final int INPUT_LIMIT = 8 * 1024 * 1024;
	static final String EMBEDDING_REVISION = "all-MiniLM-L6-v2@1110a243fdf4706b3f48f1d95db1a4f5529b4d41:quint8:256:mean-l2";

	private static final int OUTPUT_LIMIT = 4 * 1024 * 1024;

	private final String image;

	private final JsonMapper json;

	private final Semaphore slots = new Semaphore(2);

	KnowledgeWorker(@Value("${zincc.knowledge.image}") String image, JsonMapper json) {
		if (!image.matches("sha256:[a-f0-9]{64}"))
			throw new IllegalArgumentException("Pin the knowledge worker by its Docker image ID");
		this.image = image;
		this.json = json;
	}

	enum Operation {

		PARSE("parse"), EMBED("embed"), AGGREGATE("aggregate");

		final String command;

		Operation(String command) {
			this.command = command;
		}

	}

	String revision() {
		return image;
	}

	JsonNode run(Operation operation, byte[] input) {
		if (input.length > INPUT_LIMIT)
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
		if (!slots.tryAcquire())
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Document worker busy");
		String name = "zincc-knowledge-" + UUID.randomUUID();
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
			var process = isolated(name, operation);
			var send = tasks.submit(() -> {
				try (var stdin = process.getOutputStream()) {
					stdin.write(input);
				}
				return null;
			});
			var receive = tasks.submit(() -> {
				try (var stdout = process.getInputStream()) {
					var bytes = stdout.readNBytes(OUTPUT_LIMIT + 1);
					if (bytes.length > OUTPUT_LIMIT)
						throw new IOException("Worker output exceeded limit");
					return bytes;
				}
			});
			try {
				byte[] output = receive.get(72, TimeUnit.SECONDS);
				if (!process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() != 0)
					throw new IOException();
				send.get(1, TimeUnit.SECONDS);
				var result = json.readTree(output);
				if (result == null || !result.isObject())
					throw new IOException();
				return result;
			}
			finally {
				if (process.isAlive()) {
					process.destroyForcibly();
					clean(name);
				}
				send.cancel(true);
				receive.cancel(true);
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw unavailable();
		}
		catch (IOException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException
				| tools.jackson.core.JacksonException ex) {
			throw unavailable();
		}
		finally {
			slots.release();
		}
	}

	private Process isolated(String name, Operation operation) throws IOException {
		return new ProcessBuilder("docker", "run", "--rm", "--interactive", "--pull=never", "--name", name,
				"--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges", "--ipc=none",
				"--user=65532:65532", "--pids-limit=48", "--memory=768m", "--memory-swap=768m", "--cpus=1", "--tmpfs",
				"/tmp:rw,noexec,nosuid,nodev,size=24m", "--log-driver=none", image, operation.command)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
	}

	private static void clean(String name) {
		try {
			var cleanup = new ProcessBuilder("docker", "rm", "-f", name).redirectError(ProcessBuilder.Redirect.DISCARD)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
			if (!cleanup.waitFor(4, TimeUnit.SECONDS))
				cleanup.destroyForcibly();
		}
		catch (IOException ex) {
			/* Worker carries an independent process deadline. */ }
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static ResponseStatusException unavailable() {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Document worker unavailable");
	}

}
