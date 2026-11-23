package io.zincc.agents;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import io.zincc.model.ModelGateway;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import tools.jackson.databind.json.JsonMapper;
import reactor.core.publisher.Mono;
import static io.zincc.agents.AgentStore.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentHarnessTest {

	EmbeddedPostgres pg;

	JdbcTemplate admin;

	AgentStore store;

	AgentEngine engine;

	TasksConnector tasks;

	ModelGateway models;

	JsonMapper json = new JsonMapper();

	UUID workspace = UUID.randomUUID(), owner = UUID.randomUUID(), other = UUID.randomUUID(), agent;

	Scope s = new Scope(workspace, owner);

	@BeforeAll
	void database() throws Exception {
		pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start();
		admin = new JdbcTemplate(pg.getPostgresDatabase());
		admin.execute("CREATE ROLE zincc_reader NOLOGIN");
		admin.execute("CREATE ROLE runtime LOGIN IN ROLE zincc_reader");
		Flyway.configure().dataSource(pg.getPostgresDatabase()).defaultSchema("zincc").load().migrate();
		var org = UUID.randomUUID();
		admin.update("INSERT INTO zincc.organization VALUES(?)", org);
		admin.update("INSERT INTO zincc.principal VALUES(?),(?)", owner, other);
		admin.update("INSERT INTO zincc.workspace VALUES(?,?,'Private')", workspace, org);
		admin.update("INSERT INTO zincc.workspace_membership VALUES(?,?,'owner'),(?,?,'member')", workspace, owner,
				workspace, other);
	}

	@AfterAll
	void close() throws Exception {
		if (pg != null)
			pg.close();
	}

	@BeforeEach
	void setup() {
		tasks = mock(TasksConnector.class);
		models = mock(ModelGateway.class);
		when(tasks.configured(s)).thenReturn(true);
		when(tasks.resource()).thenReturn("list-a");
		when(tasks.reference()).thenReturn("test-grant");
		when(models.routes()).thenReturn(List
			.of(new ModelGateway.Route("model", "test", "v1", java.util.Set.of(ModelGateway.Capability.TEXT), 128)));
		store = new AgentStore(pg.getDatabase("runtime", "postgres"), tasks, models);
		engine = new AgentEngine(store, tasks, models, json);
		agent = UUID.randomUUID();
		store.save(s, agent, definition(0, true, null));
		store.connect(s, true);
	}

	Definition definition(int version, boolean write, UUID delegate) {
		return new Definition("Daily", "Help with tasks", "model", true, write, delegate, version);
	}

	UUID start(String mode) {
		return store.start(s, agent, new Start(UUID.randomUUID(), "Buy apples", mode));
	}

	String tick(UUID id) {
		return engine.advance(workspace.toString(), owner.toString(), id.toString(), false);
	}

	Run run(UUID id) {
		return store.scoped(s, () -> store.lock(id));
	}

	void approve(UUID id) {
		store.decision(s, id, "approve", run(id).digest());
	}

	@Test
	void versionsAndOutboxRemainScopedAndDuplicateStartsDoNotRerun() {
		UUID id = start("create");
		assertThat(store.start(s, agent, new Start(id, "Buy apples", "create"))).isEqualTo(id);
		assertThatThrownBy(() -> store.start(s, agent, new Start(id, "Changed", "create"))).hasMessageContaining("409");
		store.save(s, agent, definition(1, true, other));
		assertThat(run(id).version()).isOne();
		assertThat(store.pending()).anyMatch(w -> w.run().equals(id));
		var delegated = new Scope(workspace, other);
		assertThat(store.view(delegated).agents()).extracting(Agent::id).contains(agent);
		assertThat(store.view(delegated).runs()).extracting(Run::id).doesNotContain(id);
		assertThatThrownBy(() -> store.save(delegated, agent, definition(2, true, null))).hasMessageContaining("409");
		var delegatedRun = store.start(delegated, agent, new Start(UUID.randomUUID(), "Private", "list"));
		store.save(s, agent, definition(2, true, null));
		assertThat(engine.advance(workspace.toString(), other.toString(), delegatedRun.toString(), false))
			.isEqualTo("failed");
	}

	@Test
	void changedApprovalAndRevokedOrReconnectedGrantsCannotDispatch() throws Exception {
		UUID id = start("create");
		assertThat(tick(id)).isEqualTo("waiting");
		assertThatThrownBy(() -> store.decision(s, id, "approve", "altered")).hasMessageContaining("409");
		approve(id);
		store.connect(s, false);
		store.connect(s, true);
		assertThat(tick(id)).isEqualTo("failed");
		verify(tasks, never()).create(any(), any(), any());
		var next = start("create");
		assertThat(tick(next)).isEqualTo("waiting");
		approve(next);
		store.save(s, agent, definition(1, false, null));
		assertThat(tick(next)).isEqualTo("failed");
		verify(tasks, never()).create(any(), any(), any());
	}

	@Test
	void lostAcknowledgementReconcilesWithoutAnotherWrite() throws Exception {
		var id = start("create");
		tick(id);
		approve(id);
		when(tasks.create(any(), eq(id), anyString())).thenThrow(new IOException("lost receipt"));
		when(tasks.reconcile(any(), eq(id), anyString())).thenReturn("", "task-17");
		assertThat(tick(id)).isEqualTo("unknown");
		assertThat(tick(id)).isEqualTo("unknown");
		assertThat(engine.reconcile(s, id)).isEqualTo("completed");
		assertThat(run(id).receipt()).isEqualTo("task-17");
		verify(tasks, times(1)).create(any(), eq(id), anyString());
	}

	@Test
	void recoveredClaimOnlyChecksReceiptAndCancelledRunNeverWrites() throws Exception {
		var id = start("create");
		tick(id);
		approve(id);
		store.scoped(s, () -> store.set(id, "dispatching", "Crash after claiming"));
		when(tasks.reconcile(any(), eq(id), anyString())).thenReturn("receipt");
		assertThat(tick(id)).isEqualTo("completed");
		var cancelled = start("create");
		tick(cancelled);
		store.decision(s, cancelled, "cancel", null);
		assertThat(tick(cancelled)).isEqualTo("cancelled");
		verify(tasks, never()).create(any(), any(), any());
	}

	@Test
	void identicalReadStopsLoopAndInvalidModelStepFails() throws Exception {
		var id = start("model");
		var response = new ModelGateway.Result(models.routes().getFirst(), "{\"action\":\"list\",\"value\":\"\"}", null,
				"stop");
		when(models.text(eq("model"), anyString())).thenReturn(Mono.just(response));
		when(tasks.list(any())).thenReturn(json.readTree("{\"items\":[{\"title\":\"one\"}]}"));
		assertThat(tick(id)).isEqualTo("running");
		assertThat(tick(id)).isEqualTo("failed");
		assertThat(run(id).outcome()).contains("no progress");
		var bad = start("model");
		when(models.text(eq("model"), anyString())).thenReturn(Mono.error(new IllegalStateException()));
		assertThat(tick(bad)).isEqualTo("failed");
		var over = start("model");
		admin.update("UPDATE zincc.agent_run SET steps=6 WHERE id=?", over);
		assertThat(tick(over)).isEqualTo("failed");
		assertThat(run(over).outcome()).contains("budget");
	}

}
