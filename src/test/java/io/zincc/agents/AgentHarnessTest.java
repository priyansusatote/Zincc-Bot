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

	@Test
	void parallelReviewersHaveLineageAndReturnOnlyOneApprovedResult() throws Exception {
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		store.save(s, first, new Definition("Copy", "Check wording", "model", true, true, null, 0));
		store.save(s, second, new Definition("Amounts", "Check totals", "model", true, true, null, 0));
		var request = new AgentTeams.Request(UUID.randomUUID(), "Review the proposal", List.of(first, second));
		UUID parent = store.teams.start(s, agent, request);
		assertThat(store.teams.start(s, agent, request)).isEqualTo(parent);
		assertThatThrownBy(() -> store.teams.start(s, agent,
				new AgentTeams.Request(parent, request.goal(), List.of(second, first))))
			.hasMessageContaining("409");
		assertThatThrownBy(() -> store.teams.start(s, agent,
				new AgentTeams.Request(UUID.randomUUID(), "goal", List.of(first, first))))
			.hasMessageContaining("400");
		var kids = store.scoped(s, () -> store.teams.members(parent));
		assertThat(kids).extracting(AgentTeams.Member::agentId).containsExactly(first, second);
		assertThat(tick(parent)).isEqualTo("joining");
		var barrier = new java.util.concurrent.CyclicBarrier(2);
		when(models.text(eq("model"), anyString())).thenAnswer(invocation -> {
			barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
			String prompt = invocation.getArgument(1);
			return Mono.just(new ModelGateway.Result(models.routes().getFirst(),
					prompt.contains("Check totals") ? "Amount mismatch" : "Wording mismatch", null, "stop"));
		});
		try (var tasks = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var a = tasks.submit(() -> tick(kids.getFirst().childId()));
			var b = tasks.submit(() -> tick(kids.getLast().childId()));
			assertThat(a.get()).isEqualTo("completed");
			assertThat(b.get()).isEqualTo("completed");
		}
		assertThat(tick(parent)).isEqualTo("waiting");
		assertThatThrownBy(() -> store.decision(s, parent, "select", UUID.randomUUID().toString()))
			.hasMessageContaining("409");
		store.decision(s, parent, "select", kids.getLast().childId().toString());
		assertThat(run(parent).outcome()).isEqualTo("Amount mismatch");
		var combined = store.teams.start(s, agent,
				new AgentTeams.Request(UUID.randomUUID(), "Review the proposal again", List.of(first, second)));
		var children = store.scoped(s, () -> store.teams.members(combined));
		try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var a = pool.submit(() -> tick(children.getFirst().childId()));
			var b = pool.submit(() -> tick(children.getLast().childId()));
			assertThat(a.get()).isEqualTo("completed");
			assertThat(b.get()).isEqualTo("completed");
		}
		assertThat(tick(combined)).isEqualTo("waiting");
		store.decision(s, combined, "select", "all");
		assertThat(run(combined).outcome()).isEqualTo("Reviewer 1 � Wording mismatch\n\nReviewer 2 � Amount mismatch");
		verify(tasks, never()).list(any());
		verify(tasks, never()).create(any(), any(), any());
	}

	@Test
	void parentCancellationFencesAnAlreadyRunningModelResponse() throws Exception {
		UUID reviewer = UUID.randomUUID();
		store.save(s, reviewer, definition(0, true, null));
		var parent = store.teams.start(s, agent, new AgentTeams.Request(UUID.randomUUID(), "goal", List.of(reviewer)));
		var child = store.scoped(s, () -> store.teams.members(parent)).getFirst().childId();
		var started = new java.util.concurrent.CountDownLatch(1);
		var release = new java.util.concurrent.CountDownLatch(1);
		when(models.text(eq("model"), anyString())).thenAnswer(i -> {
			started.countDown();
			release.await(6, java.util.concurrent.TimeUnit.SECONDS);
			return Mono.just(new ModelGateway.Result(models.routes().getFirst(), "late result", null, "stop"));
		});
		try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var working = pool.submit(() -> tick(child));
			try {
				assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
				store.decision(s, parent, "cancel", null);
			}
			finally {
				release.countDown();
			}
			assertThat(working.get()).isEqualTo("cancelled");
		}
		assertThat(run(child).outcome()).doesNotContain("late result");
		assertThat(tick(parent)).isEqualTo("cancelled");
	}

	@Test
	void privateRecallCoalescesReadersAndRechecksRevisionsMembershipAndSourceDeletion() throws Exception {
		var source = start("list");
		store.scoped(s, () -> store.set(source, "completed", "Source fixture"));
		var mem = store.memory;
		var id = UUID.randomUUID();
		mem.edit(s, new AgentMemory.Edit(id, "Report currency", "USD", source, 0));
		long before = mem.loads.sum();
		try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = java.util.stream.IntStream.range(0, 16)
				.mapToObj(i -> pool.submit(() -> mem.recall(s, "currency")))
				.toList();
			for (var future : futures)
				assertThat(future.get()).extracting(AgentMemory.Note::fact).containsExactly("USD");
		}
		assertThat(mem.loads.sum() - before).isOne();
		var foreign = new Scope(workspace, other);
		assertThat(mem.recall(foreign, "currency")).isEmpty();
		assertThatThrownBy(() -> mem.edit(foreign, new AgentMemory.Edit(UUID.randomUUID(), "x", "y", source, 0)))
			.hasMessageContaining("404");
		assertThatThrownBy(
				() -> mem.edit(s, new AgentMemory.Edit(UUID.randomUUID(), "REPORT CURRENCY", "EUR", source, 0)))
			.hasMessageContaining("409");
		mem.edit(s, new AgentMemory.Edit(id, "Report currency", "EUR", source, 1));
		assertThatThrownBy(() -> mem.edit(s, new AgentMemory.Edit(id, "Report currency", "JPY", source, 1)))
			.hasMessageContaining("409");
		assertThat(mem.recall(s, "USD")).isEmpty();
		assertThat(mem.recall(s, "EUR")).extracting(AgentMemory.Note::sourceId).containsExactly(source);
		admin.update("DELETE FROM zincc.workspace_membership WHERE workspace_id=? AND principal_id=?", workspace,
				owner);
		try {
			assertThatThrownBy(() -> mem.recall(s, "EUR")).hasMessageContaining("404");
		}
		finally {
			admin.update("INSERT INTO zincc.workspace_membership VALUES(?,?,'owner')", workspace, owner);
		}
		var recall = store.start(s, agent, new Start(UUID.randomUUID(), "EUR", "recall"));
		assertThat(tick(recall)).isEqualTo("completed");
		assertThat(run(recall).outcome()).contains("1").doesNotContain("EUR");
		mem.remove(s, id, 2);
		assertThat(mem.recall(s, "EUR")).isEmpty();
		mem.edit(s, new AgentMemory.Edit(UUID.randomUUID(), "Report currency", "GBP", source, 0));
		assertThat(mem.recall(s, "GBP")).hasSize(1);
		admin.update("DELETE FROM zincc.agent_outbox WHERE run_id=?", source);
		admin.update("DELETE FROM zincc.agent_run WHERE id=?", source);
		assertThat(mem.recall(s, "GBP")).isEmpty();
	}

	@Test
	void userSelectedLocalReviewNeedsNoModelOrConnectorAndCannotChangeOnReplay() {
		UUID reviewer = UUID.randomUUID();
		store.save(s, reviewer, definition(0, false, null));
		var key = UUID.randomUUID();
		var goal = "- [ ] A\n- [x] B";
		var req = new AgentTeams.Request(key, goal, List.of(reviewer), java.util.Map.of(reviewer, "checklist"));
		store.teams.start(s, agent, req);
		assertThatThrownBy(() -> store.teams.start(s, agent, new AgentTeams.Request(key, goal, List.of(reviewer))))
			.hasMessageContaining("409");
		var child = store.scoped(s, () -> store.teams.members(key)).getFirst();
		assertThat(tick(child.childId())).isEqualTo("completed");
		assertThat(tick(key)).isEqualTo("completed");
		assertThat(run(key).outcome()).isEqualTo("Unchecked checklist items: 1");
		verify(models, never()).text(any(), any());
	}

}
