package io.zincc.sandbox;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only fixed application-owned programs can cross this boundary. */
public final class SandboxBroker {

	private static final Semaphore slots = new Semaphore(3);

	private SandboxBroker() {
	}

	public static Handle knowledge(String image, String operation) throws IOException {
		if (!Set.of("parse", "aggregate", "embed").contains(operation))
			throw new IllegalArgumentException();
		return launch(image, false, List.of(operation));
	}

	public static Handle briefing(String image) throws IOException {
		byte[] script;
		try (var input = SandboxBroker.class.getResourceAsStream("/integrations/briefing_mcp.py")) {
			script = Objects.requireNonNull(input).readAllBytes();
		}
		return launch(image, true, List.of("--kill-after=2s", "25s", "python", "-I", "-B", "-u", "-c",
				"exec(__import__('base64').b64decode('" + Base64.getEncoder().encodeToString(script) + "'))"));
	}

	public static Handle framework(String image, String framework) throws IOException {
		if (!Set.of("adk", "langgraph", "pydantic-ai").contains(framework))
			throw new IllegalArgumentException("Unknown framework");
		return launch(image, false, List.of(framework));
	}

	static List<String> command(String image, String name, boolean small, List<String> program) {
		if (!image.matches("sha256:[a-f0-9]{64}"))
			throw new IllegalArgumentException("Pinned image required");
		String memory = small ? "64m" : "768m";
		var args = new ArrayList<>(List.of("docker", "run", "--rm", "--interactive", "--pull=never", "--name", name,
				"--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges", "--ipc=none",
				"--user=65532:65532", "--pids-limit=" + (small ? 12 : 48), "--memory=" + memory,
				"--memory-swap=" + memory, "--cpus=" + (small ? "0.5" : "1"), "--tmpfs",
				"/tmp:rw,noexec,nosuid,nodev,size=24m", "--log-driver=none"));
		if (small)
			args.add("--entrypoint=/usr/bin/timeout");
		args.add(image);
		args.addAll(program);
		return List.copyOf(args);
	}

	private static Handle launch(String image, boolean small, List<String> program) throws IOException {
		String name = "zincc-sandbox-" + UUID.randomUUID();
		var args = command(image, name, small, program);
		if (!slots.tryAcquire())
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Sandbox busy");
		try {
			return new Handle(name, new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start());
		}
		catch (IOException | RuntimeException e) {
			slots.release();
			throw e;
		}
	}

	public static final class Handle implements AutoCloseable {

		private final String name;

		private final Process process;

		private boolean closed;

		private Handle(String name, Process process) {
			this.name = name;
			this.process = process;
		}

		String nameForDiagnostics() {
			return name;
		}

		public Process process() {
			return process;
		}

		@Override
		public synchronized void close() {
			if (closed)
				return;
			closed = true;
			try {
				if (process.isAlive())
					process.destroyForcibly();
				var cleanup = new ProcessBuilder("docker", "rm", "-f", name)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
				if (!cleanup.waitFor(4, TimeUnit.SECONDS))
					cleanup.destroyForcibly();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (IOException ignored) {
				// Pinned worker entrypoints also enforce an independent deadline.
			}
			finally {
				slots.release();
			}
		}

	}

}
