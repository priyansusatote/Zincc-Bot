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
			verifyEngineDelivery(api, admin, ws, who);
		}
	}

	private void verifyEngineDelivery(RunnerApi api, JdbcTemplate admin, UUID ws, UUID owner) {
		var code = api.invite(null, ws).getBody().code().split("\\.");
		var device = UUID.fromString(code[2]);
		var catalog = new DeviceEngines.Catalog("chatgpt-plan", "Test plan", List.of("test-model"));
		var pair = new RunnerApi.Pulse(ws, owner, device, "pair", false, false, null, null, catalog, null);
		api.localOnly = true;
		assertThatThrownBy(() -> api.exchange("Bearer " + code[3], pair)).hasMessageContaining("409");
		api.localOnly = false;
		var token = "Bearer " + api.exchange("Bearer " + code[3], pair).getBody().credential();
		var agent = UUID.randomUUID();
		var run = UUID.randomUUID();
		admin.update("INSERT INTO zincc.agent VALUES(?,?,?,null,1)", agent, ws, owner);
		admin.update(
				"INSERT INTO zincc.agent_version(agent_id,version,name,instructions,route,can_read,can_write) VALUES(?,1,'test','Use plain text','local',false,false)",
				agent);
		admin.update(
				"INSERT INTO zincc.agent_run(id,workspace_id,actor_id,agent_id,version,goal,mode,state) VALUES(?,?,?,?,1,'private task','engine-fixture','running')",
				run, ws, owner, agent);
		admin.update(
				"INSERT INTO zincc.engine_receipt(run_id,connection_id,generation,model) VALUES(?,?,1,'test-model')",
				run, device);
		var poll = new RunnerApi.Pulse(ws, owner, device, "engine-poll", false, false, null, null);
		var offer = api.exchange(token, poll).getBody();
		assertThat(offer.model()).isEqualTo("test-model");
		assertThat(offer.goal()).isNull();
		var wrong = new RunnerApi.Pulse(ws, owner, device, "engine-claim", false, false, run, null, null,
				new DeviceEngines.Event("different", null, null));
		assertThatThrownBy(() -> api.exchange(token, wrong)).hasMessageContaining("409");
		var claim = new RunnerApi.Pulse(ws, owner, device, "engine-claim", false, false, run, null, null,
				new DeviceEngines.Event("test-model", null, null));
		var payload = api.exchange(token, claim).getBody();
		assertThat(payload.goal()).isEqualTo("private task");
		assertThat(payload.instructions()).isEqualTo("Use plain text");
		assertThatThrownBy(() -> api.exchange(token, claim)).hasMessageContaining("409");
		assertThat(api.exchange(token, poll).getBody().state()).isEqualTo("idle");
		var complete = new RunnerApi.Pulse(ws, owner, device, "engine-complete", false, false, run, null, null,
				new DeviceEngines.Event("test-model", "result", null, new DeviceEngines.Allowance(87.5, 300)));
		admin.update("UPDATE zincc.engine_connection SET generation=2 WHERE id=?", device);
		assertThatThrownBy(() -> api.exchange(token, complete)).hasMessageContaining("409");
		admin.update("UPDATE zincc.engine_connection SET generation=1 WHERE id=?", device);
		admin.update("UPDATE zincc.agent_run SET state='cancelled' WHERE id=?", run);
		assertThatThrownBy(() -> api.exchange(token, complete)).hasMessageContaining("409");
		admin.update("UPDATE zincc.agent_run SET state='running' WHERE id=?", run);
		assertThat(api.exchange(token, complete).getBody().state()).isEqualTo("completed");
		assertThat(admin.queryForObject(
				"SELECT primary_remaining FROM zincc.engine_connection WHERE id=? AND primary_window_minutes=300 AND allowance_observed_at IS NOT NULL",
				Double.class, device))
			.isEqualTo(87.5);
		assertThatThrownBy(() -> api.exchange(token, complete)).hasMessageContaining("409");
		assertThat(admin.queryForObject("SELECT outcome FROM zincc.agent_run WHERE id=?", String.class, run))
			.isEqualTo("result");
	}

}
