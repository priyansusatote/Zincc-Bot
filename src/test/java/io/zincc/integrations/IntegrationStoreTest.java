package io.zincc.integrations;

import java.util.*;
import java.time.*;
import javax.sql.DataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import static io.zincc.integrations.IntegrationStore.*;
import static org.assertj.core.api.Assertions.*;

class IntegrationStoreTest {

	@Test
	void permissionedJobsAndCheckpointsSurviveRestart() throws Exception {
		try (var pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start()) {
			var admin = new JdbcTemplate(pg.getPostgresDatabase());
			admin.execute("CREATE ROLE zincc_reader NOLOGIN");
			admin.execute("CREATE ROLE integration_runtime LOGIN IN ROLE zincc_reader");
			Flyway.configure().dataSource(pg.getPostgresDatabase()).defaultSchema("zincc").load().migrate();
			var org = UUID.randomUUID();
			var ws = UUID.randomUUID();
			var who = UUID.randomUUID();
			var other = UUID.randomUUID();
			admin.update("INSERT INTO zincc.organization VALUES(?)", org);
			admin.update("INSERT INTO zincc.workspace VALUES(?,?,'Test')", ws, org);
			admin.update("INSERT INTO zincc.principal VALUES(?),(?)", who, other);
			admin.update("INSERT INTO zincc.workspace_membership VALUES(?,?,'owner'),(?,?,'member')", ws, who, ws,
					other);
			DataSource source = pg.getDatabase("integration_runtime", "postgres");
			var json = new JsonMapper();
			var env = new MockEnvironment().withProperty("ZINCC_CONNECTION_KEY", "45".repeat(32))
				.withProperty("ZINCC_GOOGLE_CLIENT_ID", "test")
				.withProperty("ZINCC_GOOGLE_CLIENT_SECRET", "test");
			var provider = new ProviderClient(env, json);
			var store = new IntegrationStore(source, provider, env, json);
			var alice = new Scope(ws, who);
			var bob = new Scope(ws, other);
			var mail = store.add(alice, "gmail", "First account", "", true, null);
			var second = store.add(alice, "gmail", "Second account", "", true, null);
			var live = store.add(alice, "gmail", "Encrypted test", "", false,
					new ProviderClient.Grant("test-refresh-token", ProviderClient.GMAIL, ""));
			assertThat(admin.queryForObject("SELECT secret FROM zincc.feed WHERE id=?", String.class, live))
				.doesNotContain("test-refresh-token");
			store.revoke(alice, live);
			assertThat(store.sync(alice, mail, true)).isFalse();
			assertThat(store.scoped(alice, () -> store.summaries(mail))).isEmpty();
			assertThat(store.scoped(alice, store::feeds)).filteredOn(f -> f.id().equals(mail))
				.extracting(Feed::itemCount)
				.containsExactly(1L);
			var restarted = new IntegrationStore(source, provider, env, json);
			assertThat(restarted.sync(alice, mail, true)).isTrue();
			assertThat(store.scoped(alice, () -> store.summaries(mail))).hasSize(2);
			assertThat(store.scoped(alice, () -> store.summaries(second))).isEmpty();
			assertThat(store.scoped(bob, store::feeds)).isEmpty();
			assertThatThrownBy(() -> store.revoke(bob, mail)).hasMessageContaining("404");
			var tool = new BriefingTool(env, json) {
				@Override
				String run(List<String> lines) {
					return String.join("\n", lines);
				}
			};
			var jobs = new Routines(store, tool, null);
			var v1 = store.skill("briefing", 1);
			var v2 = store.skill("briefing", 2);
			store.install(alice, v1.name(), 1, v1.digest(), Set.of("gmail"));
			assertThatThrownBy(() -> store.install(alice, v2.name(), 2, v2.digest(), Set.of("gmail")))
				.hasMessageContaining("409");
			var routine = jobs.create(alice,
					new Routines.Create("Daily", "briefing", 1, "daily", "UTC", "08:00", List.of(mail), null, null));
			var attempt = UUID.randomUUID();
			jobs.command(alice, routine, "run", attempt);
			jobs.command(alice, routine, "run", attempt);
			var job = admin.queryForObject("SELECT id FROM zincc.routine_run", UUID.class);
			new Routines(restarted, tool, null).execute(alice, job);
			jobs.execute(alice, job);
			assertThat(store.scoped(alice, jobs::history).getFirst().get("receipt").toString())
				.contains("Invoice ready");
			store.install(alice, v2.name(), 2, v2.digest(), Set.copyOf(v2.permissions()));
			assertThatThrownBy(() -> jobs.command(alice, routine, "resume", null)).hasMessageContaining("409");
			store.install(alice, v1.name(), 1, v1.digest(), Set.of("gmail"));
			jobs.command(alice, routine, "resume", null);
			admin.update("UPDATE zincc.routine SET next_time=now()-interval '3 days' WHERE id=?", routine);
			jobs.daily(alice, routine);
			jobs.daily(alice, routine);
			assertThat(admin.queryForObject("SELECT count(*) FROM zincc.routine_run", Integer.class)).isEqualTo(2);
			jobs.command(alice, routine, "pause", null);
			assertThat(
					admin.queryForObject("SELECT count(*) FROM zincc.routine_run WHERE state='queued'", Integer.class))
				.isZero();
			var event = jobs.create(alice,
					new Routines.Create("Changes", "briefing", 1, "event", "UTC", "08:00", List.of(mail), null, null));
			store.signal(alice, mail, "ev1");
			store.signal(alice, mail, "ev1");
			assertThat(admin.queryForObject("SELECT count(*) FROM zincc.feed_signal", Integer.class)).isOne();
			var eventId = UUID.randomUUID();
			store.sampleEvent(alice, mail, eventId);
			store.sampleEvent(alice, mail, eventId);
			store.sync(alice, mail, true);
			store.sync(alice, mail, true);
			assertThat(admin.queryForObject("SELECT count(*) FROM zincc.routine_run WHERE routine_id=?", Integer.class,
					event))
				.isOne();
			// An unavailable second account stays visible as a partial source.
			var partial = jobs.create(alice, new Routines.Create("Partial", "briefing", 1, "daily", "UTC", "09:00",
					List.of(mail, second), null, null));
			jobs.command(alice, partial, "run", UUID.randomUUID());
			var partialId = admin.queryForObject("SELECT id FROM zincc.routine_run WHERE routine_id=?", UUID.class,
					partial);
			jobs.execute(alice, partialId);
			var partialReceipt = store.scoped(alice, jobs::history).getFirst();
			assertThat(partialReceipt.get("state")).isEqualTo("partial");
			assertThat(partialReceipt.get("receipt").toString()).contains("Invoice ready",
					"Second account · source unavailable");

			// Model an expired history cursor, then a full snapshot in which a previously
			// cited message disappeared.
			var responses = new java.util.ArrayDeque<Object>();
			responses.add(new ProviderClient.Page(
					List.of(new ProviderClient.Item("old", "Previously permitted", "1", false)), "", "", "h1", true));
			responses.add(new ProviderClient.Failure("gone", 300));
			responses.add(new ProviderClient.Page(
					List.of(new ProviderClient.Item("new", "Current subject", "2", false)), "", "", "h2", true));
			var scripted = new ProviderClient(env, json) {
				@Override
				Page fetch(String provider, String resource, String secret, String cursor, String page, String anchor) {
					assertThat(secret).isEqualTo("private-refresh");
					var response = responses.removeFirst();
					if (response instanceof Failure f) {
						assertThat(cursor).isEqualTo("h1");
						throw f;
					}
					assertThat(cursor).isEmpty();
					return (Page) response;
				}
			};
			var realStore = new IntegrationStore(source, scripted, env, json);
			var tracked = realStore.add(alice, "gmail", "History test", "", false,
					new ProviderClient.Grant("private-refresh", ProviderClient.GMAIL, ""));
			assertThat(realStore.sync(alice, tracked, true)).isTrue();
			var snapshot = jobs.create(alice, new Routines.Create("Snapshot", "briefing", 1, "daily", "UTC", "07:00",
					List.of(tracked), null, null));
			jobs.command(alice, snapshot, "run", UUID.randomUUID());
			var snapshotId = admin.queryForObject("SELECT id FROM zincc.routine_run WHERE routine_id=?", UUID.class,
					snapshot);
			jobs.execute(alice, snapshotId);
			assertThat(store.scoped(alice, jobs::history).toString()).contains("Previously permitted");
			assertThat(realStore.sync(alice, tracked, true)).isFalse();
			assertThat(store.scoped(alice, jobs::history).toString()).doesNotContain("Previously permitted");
			assertThat(realStore.sync(alice, tracked, true)).isTrue();
			assertThat(realStore.scoped(alice, () -> realStore.summaries(tracked))).extracting(Summary::summary)
				.containsExactly("Current subject");
			assertThat(store.scoped(alice, jobs::history).toString()).doesNotContain("Previously permitted");
			store.revoke(alice, mail);
			assertThat(store.scoped(alice, jobs::history).toString()).doesNotContain("Invoice ready");
			assertThat(store.scoped(alice, () -> store.summaries(mail))).isEmpty();
			admin.update("DELETE FROM zincc.workspace_membership WHERE principal_id=?", who);
			assertThat(store.db.queryForList("SELECT * FROM zincc.integration_due()")).isEmpty();
			assertThatThrownBy(() -> store.scoped(alice, store::feeds)).hasMessageContaining("404");
		}
	}

}
