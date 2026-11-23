package io.zincc.knowledge;

import java.util.UUID;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class KnowledgeStoreTest {

	@Test
	void scopesSourcePassagesDatasetsAndDashboardsAndRepublishesOnlyNewClaims() throws Exception {
		try (var pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start()) {
			var owner = new JdbcTemplate(pg.getPostgresDatabase());
			owner.execute("CREATE ROLE zincc_reader NOLOGIN");
			owner.execute("CREATE ROLE knowledge_runtime LOGIN IN ROLE zincc_reader");
			Flyway.configure().dataSource(pg.getPostgresDatabase()).defaultSchema("zincc").load().migrate();
			var org = UUID.randomUUID();
			var workspace = UUID.randomUUID();
			var a = UUID.randomUUID();
			var b = UUID.randomUUID();
			owner.update("INSERT INTO zincc.organization VALUES(?)", org);
			owner.update("INSERT INTO zincc.workspace VALUES(?,?,'Knowledge')", workspace, org);
			owner.update("INSERT INTO zincc.principal VALUES(?),(?)", a, b);
			owner.update("INSERT INTO zincc.workspace_membership VALUES(?,?,'owner'),(?,?,'member')", workspace, a,
					workspace, b);
			var json = new JsonMapper();
			var store = new KnowledgeStore(pg.getDatabase("knowledge_runtime", "postgres"), json);
			var alice = new KnowledgeStore.Scope(workspace, a);
			var bob = new KnowledgeStore.Scope(workspace, b);
			byte[] pdf = { 1, 2, 3 };
			var first = store.claim(alice, "private.pdf", pdf, "image");
			store.failed(alice, first, "failed");
			var retry = store.claim(alice, "private.pdf", pdf, "image");
			assertThat(retry.id()).isEqualTo(first.id());
			var parsed = json.readTree("""
					{"pages":2,"missing":[],"scanned":[],"chunks":[
					  {"page":1,"text":"North branch purchasing expenses.","vector":[1.0,0.0]},
					  {"page":2,"text":"Penguin habitat and islands.","vector":[0.0,1.0]}],
					  "tables":[{"page":1,"rows":[["Branch","Cost"],["North","12.25"],["North","4.75"]]}]}
					""");
			assertThatThrownBy(() -> store.publish(alice, first, parsed)).hasMessageContaining("404");
			store.publish(alice, retry, parsed);
			assertThat(store.claim(alice, "duplicate.pdf", pdf, "image").started()).isNull();
			assertThat(store.source(alice, retry.id())).containsExactly(pdf);
			assertThat(store.retrieve(alice, retry.id(), "expenses", json.readTree("[1,0]")))
				.extracting(KnowledgeStore.Evidence::page)
				.containsExactly(1);
			UUID dashboard = UUID.randomUUID();
			var create = new KnowledgeStore.Settings(1, "North", 1, 0, "USD", "North", 0);
			assertThat(store.save(alice, retry.id(), dashboard, create)).isOne();
			assertThatThrownBy(() -> store.save(alice, retry.id(), dashboard, create)).hasMessageContaining("409");
			assertThat(store.documents(bob)).isEmpty();
			assertThatThrownBy(() -> store.source(bob, retry.id())).hasMessageContaining("404");
			assertThatThrownBy(() -> store.page(bob, retry.id(), 1)).hasMessageContaining("404");
			assertThatThrownBy(() -> store.datasets(bob, retry.id())).hasMessageContaining("404");
			assertThatThrownBy(() -> store.snapshot(bob, retry.id(), dashboard)).hasMessageContaining("404");
			owner.update("DELETE FROM zincc.workspace_membership WHERE workspace_id=? AND principal_id=?", workspace,
					a);
			assertThatThrownBy(() -> store.snapshot(alice, retry.id(), dashboard)).hasMessageContaining("404");
			owner.update("INSERT INTO zincc.workspace_membership VALUES(?,?,'owner')", workspace, a);
			store.delete(alice, retry.id());
			assertThat(owner.queryForObject("SELECT count(*) FROM zincc.document_chunk", Long.class)).isZero();
			assertThat(owner.queryForObject("SELECT count(*) FROM zincc.document_dashboard", Long.class)).isZero();
			assertThat(owner.queryForObject("SELECT count(*) FROM zincc.document_dataset", Long.class)).isZero();
		}
	}

}
