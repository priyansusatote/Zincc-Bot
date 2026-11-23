package io.zincc.integrations;

import java.util.*;
import io.zincc.identity.SessionPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class RunnerLeaseTest {

	@Test
	void singleUsePairingAndFencedReceiptsSurviveLostContactAndRevocation() throws Exception {
		try (var pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start()) {
			var admin = new JdbcTemplate(pg.getPostgresDatabase());
			admin.execute("CREATE ROLE zincc_reader NOLOGIN");
			admin.execute("CREATE ROLE runner_test LOGIN IN ROLE zincc_reader");
			Flyway.configure().dataSource(pg.getPostgresDatabase()).defaultSchema("zincc").load().migrate();
			var source = pg.getDatabase("runner_test", "postgres");
			var org = UUID.randomUUID();
			var ws = UUID.randomUUID();
			var who = UUID.randomUUID();
			var other = UUID.randomUUID();
			admin.update("INSERT INTO zincc.organization VALUES(?)", org);
			admin.update("INSERT INTO zincc.workspace VALUES(?,?,'Test')", ws, org);
			admin.update("INSERT INTO zincc.principal VALUES(?),(?)", who, other);
			admin.update("INSERT INTO zincc.workspace_membership VALUES(?,?,'owner'),(?,?,'member')", ws, who, ws,
					other);
			var json = new JsonMapper();
			var env = new MockEnvironment();
			var store = new IntegrationStore(source, new ProviderClient(env, json), env, json);
			var api = new RunnerApi(store, new SessionPrincipal(source) {
				@Override
				public Optional<UUID> resolve(OidcUser unused) {
					return Optional.of(who);
				}
			});
			var code = api.invite(null, ws).getBody().code().split("\\.");
			UUID id = UUID.fromString(code[2]);
			var pair = new RunnerApi.Pulse(ws, who, id, "pair", true, true, null, null);
			var token = "Bearer " + api.exchange("Bearer " + code[3], pair).getBody().credential();
			assertThatThrownBy(() -> api.exchange("Bearer " + code[3], pair)).hasMessageContaining("401");
			var poll = new RunnerApi.Pulse(ws, who, id, "poll", false, false, null, null);
			assertThatThrownBy(
					() -> api.exchange(token, new RunnerApi.Pulse(ws, other, id, "poll", true, true, null, null)))
				.hasMessageContaining("404");
			api.command(null, ws, id, new RunnerApi.Command("inventory"));
			assertThatThrownBy(() -> api.command(null, ws, id, new RunnerApi.Command("browser")))
				.hasMessageContaining("409");
			UUID job = api.exchange(token, poll).getBody().job();
			assertThat(api.exchange(token, new RunnerApi.Pulse(ws, who, id, "renew", false, false, job, null))
				.getBody()
				.state()).isEqualTo("leased");
			var done = new RunnerApi.Pulse(ws, who, id, "complete", false, false, job, 7);
			assertThat(api.exchange(token, done).getBody().state()).isEqualTo("complete");
			assertThatThrownBy(() -> api.exchange(token, done)).hasMessageContaining("409");
			api.command(null, ws, id, new RunnerApi.Command("browser"));
			UUID expired = api.exchange(token, poll).getBody().job();
			admin.update("UPDATE zincc.runner_job SET lease_until=now() WHERE id=?", expired);
			assertThatThrownBy(
					() -> api.exchange(token, new RunnerApi.Pulse(ws, who, id, "complete", false, false, expired, 1)))
				.hasMessageContaining("409");
			api.command(null, ws, id, new RunnerApi.Command("browser"));
			UUID inflight = api.exchange(token, poll).getBody().job();
			api.command(null, ws, id, new RunnerApi.Command("pause"));
			assertThat(api.exchange(token, poll).getBody().state()).isEqualTo("paused");
			api.command(null, ws, id, new RunnerApi.Command("resume"));
			assertThatThrownBy(
					() -> api.exchange(token, new RunnerApi.Pulse(ws, who, id, "renew", false, false, inflight, null)))
				.hasMessageContaining("409");
			api.command(null, ws, id, new RunnerApi.Command("revoke"));
			assertThatThrownBy(() -> api.exchange(token, poll)).hasMessageContaining("401");
			assertThat(api.list(null, ws).jobs()).hasSize(3);
		}
	}

}
