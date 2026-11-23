package io.zincc.integrations;

import java.io.*;
import java.net.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class IntegrationContractTest {

	final JsonMapper json = new JsonMapper();

	@Test
	void providerPaginationAndScopedDeletions() {
		ProviderClient client = new ProviderClient(new MockEnvironment(), json) {
			@Override
			JsonNode get(String path, Map<String, String> args, String access) {
				assertThat(access).isEqualTo("actor-only");
				if (path.endsWith("profile"))
					return json.readTree("{\"historyId\":\"101\"}");
				if (path.endsWith("/messages")) {
					assertThat(args).containsEntry("labelIds", "INBOX");
					return json.readTree("{\"messages\":[{\"id\":\"m1\"}],\"nextPageToken\":\"p2\"}");
				}
				if (path.endsWith("m1"))
					return json.readTree(
							"{\"labelIds\":[\"INBOX\"],\"payload\":{\"headers\":[{\"name\":\"Subject\",\"value\":\"One\"}]}}");
				if (path.endsWith("/history")) {
					assertThat(args).containsEntry("startHistoryId", "101");
					return json.readTree("{\"historyId\":\"104\",\"history\":[{\"messages\":[{\"id\":\"gone\"}]}]}");
				}
				if (path.endsWith("gone"))
					throw new Failure("gone", 300);
				if (path.contains("calendars")) {
					assertThat(args).containsEntry("pageToken", "p2").containsEntry("syncToken", "c1");
					return json
						.readTree("{\"nextSyncToken\":\"c2\",\"items\":[{\"id\":\"e\",\"status\":\"cancelled\"}]}");
				}
				assertThat(args).containsEntry("channel", "C123")
					.containsEntry("cursor", "p2")
					.containsEntry("latest", "100000");
				return json.readTree("{\"ok\":true,\"messages\":[{\"ts\":\"1000.4\",\"text\":\"Lunch\"}]}");
			}
		};
		var full = client.mail("actor-only", "", "", "");
		assertThat(full.anchor()).isEqualTo("101");
		assertThat(full.next()).isEqualTo("p2");
		assertThat(full.items().getFirst().summary()).isEqualTo("One");
		assertThat(client.mail("actor-only", "101", "", "").items().getFirst().removed()).isTrue();
		var cal = client.calendar("actor-only", "c1", "p2");
		assertThat(cal.checkpoint()).isEqualTo("c2");
		assertThat(cal.items().getFirst().removed()).isTrue();
		assertThat(client.slack("actor-only", "C123", "p2", "100000").items().getFirst().summary()).isEqualTo("Lunch");
	}

	@Test
	void httpFailuresPreserveRetryAndRevocation() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", e -> {
			boolean limit = e.getRequestURI().getPath().equals("/limit");
			byte[] bytes = "{\"ok\":false,\"error\":\"token_revoked\"}".getBytes(StandardCharsets.UTF_8);
			e.getResponseHeaders().add("Retry-After", "120");
			e.sendResponseHeaders(limit ? 429 : 200, bytes.length);
			try (var out = e.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		try {
			var c = new ProviderClient(new MockEnvironment(), json);
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			assertThatExceptionOfType(ProviderClient.Failure.class)
				.isThrownBy(() -> c.request(HttpRequest.newBuilder(URI.create(base + "/limit")).GET()))
				.satisfies(f -> assertThat(f.delay).isEqualTo(120));
			assertThatThrownBy(() -> c.request(HttpRequest.newBuilder(URI.create(base + "/invalid")).GET()))
				.hasMessage("revoked");
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void dailySlotsSkipSpringGapAndChooseOnlyFirstAutumnOccurrence() {
		var z = ZoneId.of("America/New_York");
		assertThat(Routines.next(Instant.parse("2026-03-08T05:00:00Z"), z, LocalTime.of(2, 30)))
			.isEqualTo("2026-03-09T06:30:00Z");
		assertThat(Routines.next(Instant.parse("2026-11-01T04:00:00Z"), z, LocalTime.of(1, 30)))
			.isEqualTo("2026-11-01T05:30:00Z");
		assertThat(Routines.next(Instant.parse("2026-11-01T05:30:00Z"), z, LocalTime.of(1, 30)))
			.isEqualTo("2026-11-02T06:30:00Z");
	}

	@Test
	void rejectsBadHandshakeAndSchemaBeforeSendingAnySourceData() throws Exception {
		var tool = new BriefingTool(new MockEnvironment(), json);
		String start = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{\"tools\":{}}}}\n";
		for (String wire : List.of(start.replace("2025-06-18", "unknown"), start
				+ "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"briefing\",\"inputSchema\":{}}]}}\n",
				"x".repeat(41000))) {
			var sent = new ByteArrayOutputStream();
			assertThatThrownBy(() -> tool.exchange(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)),
					sent, List.of("private mailbox")))
				.isInstanceOf(IOException.class);
			assertThat(sent.toString(StandardCharsets.UTF_8)).doesNotContain("private mailbox");
		}
	}

	@Test
	void signedSlackEventsExpireAndBindTheRawBody() throws Exception {
		var stamp = "1795431000";
		var now = Instant.ofEpochSecond(Long.parseLong(stamp));
		var bytes = "{\"event\":1}".getBytes(StandardCharsets.UTF_8);
		var mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("test".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		mac.update(("v0:" + stamp + ":").getBytes(StandardCharsets.UTF_8));
		String sig = "v0=" + HexFormat.of().formatHex(mac.doFinal(bytes));
		assertThat(SlackEvents.verified("test", stamp, sig, bytes, now)).isTrue();
		assertThat(SlackEvents.verified("test", stamp, sig, new byte[1], now)).isFalse();
		assertThat(SlackEvents.verified("test", stamp, sig, bytes, now.plusSeconds(301))).isFalse();
	}

}
