package io.zincc.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class AgUiReplayTest {

	@Test
	@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "ZINCC_FRAMEWORK_TEST_IMAGE",
			matches = "sha256:[a-f0-9]{64}")
	void pinnedAgUiSdkAcceptsTheSerializedReplay() throws Exception {
		var run = UUID.randomUUID();
		var events = List.of(new ConversationStore.Event(1, run, "started", "synthetic", Instant.EPOCH, "route", null),
				new ConversationStore.Event(2, run, "completed", "answer", Instant.EPOCH, "route", null));
		String frames = AgUiReplay.frames(UUID.randomUUID(), events, 0, JsonMapper.builder().build());
		String validate = """
				import sys
				from ag_ui.core import Event
				from pydantic import TypeAdapter
				validator=TypeAdapter(Event)
				events=[validator.validate_json(line[6:]) for line in sys.stdin if line.startswith('data: ')]
				assert [e.type.value for e in events]==['RUN_STARTED','TEXT_MESSAGE_START','TEXT_MESSAGE_CONTENT','TEXT_MESSAGE_END','RUN_FINISHED']
				""";
		var child = new ProcessBuilder("docker", "run", "--rm", "-i", "--network=none", "--read-only", "--cap-drop=ALL",
				"--entrypoint=python", System.getenv("ZINCC_FRAMEWORK_TEST_IMAGE"), "-I", "-c", validate)
			.inheritIO()
			.redirectInput(ProcessBuilder.Redirect.PIPE)
			.start();
		try (var stdin = child.getOutputStream()) {
			stdin.write(frames.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		try {
			assertThat(child.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			assertThat(child.exitValue()).isZero();
		}
		finally {
			child.destroyForcibly();
		}
	}

	@Test
	void resumesWithinInterruptedTurnWithoutRepeatingTextOrExposingUserInput() {
		var run = UUID.randomUUID();
		var thread = UUID.randomUUID();
		var events = List.of(
				new ConversationStore.Event(1, run, "started", "private user text", Instant.EPOCH, "route", null),
				new ConversationStore.Event(2, run, "interrupted", "visible partial", Instant.EPOCH, "route",
						"TIMEOUT"));
		var json = JsonMapper.builder().build();
		assertThat(AgUiReplay.frames(thread, events, 0, json))
			.contains("RUN_STARTED", "TEXT_MESSAGE_START", "visible partial", "RUN_ERROR")
			.doesNotContain("private user text", "RUN_FINISHED");
		assertThat(AgUiReplay.frames(thread, events, 17, json)).contains("TEXT_MESSAGE_END", "TIMEOUT")
			.doesNotContain("visible partial", "TEXT_MESSAGE_START");
	}

}
