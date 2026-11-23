package io.zincc.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class SandboxBrokerTest {

	@Test
	void rejectsMutableImagesAndUnknownPrograms() {
		assertThatThrownBy(() -> SandboxBroker.knowledge("latest", "parse"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SandboxBroker.knowledge("sha256:" + "0".repeat(64), "sh"))
			.isInstanceOf(IllegalArgumentException.class);
		var args = SandboxBroker.command("sha256:" + "0".repeat(64), "test", true, List.of("brief"));
		assertThat(args).contains("--network=none", "--read-only", "--pids-limit=12", "--memory=64m",
				"--log-driver=none");
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "ZINCC_KNOWLEDGE_TEST_IMAGE", matches = "sha256:[a-f0-9]{64}")
	void dockerActuallyAppliesLimitsAndTerminatesNamedContainer() throws Exception {
		String id = null;
		try (var h = SandboxBroker.knowledge(System.getenv("ZINCC_KNOWLEDGE_TEST_IMAGE"), "parse")) {
			String name = h.nameForDiagnostics();
			for (int i = 0; i < 40; i++) {
				var inspect = new ProcessBuilder("docker", "inspect", name)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
				byte[] output = inspect.getInputStream().readAllBytes();
				if (inspect.waitFor() == 0) {
					var container = new JsonMapper().readTree(output).get(0);
					var host = container.path("HostConfig");
					assertThat(host.path("NetworkMode").asString()).isEqualTo("none");
					assertThat(host.path("ReadonlyRootfs").asBoolean()).isTrue();
					assertThat(host.path("Privileged").asBoolean()).isFalse();
					assertThat(host.path("Binds").size()).isZero();
					assertThat(host.path("PidsLimit").asInt()).isEqualTo(48);
					assertThat(host.path("Memory").asLong()).isEqualTo(768L * 1024 * 1024);
					assertThat(container.path("Config").path("User").asString()).isEqualTo("65532:65532");
					id = name;
					break;
				}
				Thread.sleep(150);
			}
			assertThat(id).isNotNull();
		}
		var inspect = new ProcessBuilder("docker", "inspect", id).redirectError(ProcessBuilder.Redirect.DISCARD)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.start();
		assertThat(inspect.waitFor()).isNotZero();
	}

}
