package io.zincc.integrations;

import io.zincc.identity.SessionPrincipal;
import io.zincc.model.ModelGateway;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import static io.zincc.model.ModelGateway.Capability.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatSourceApiTest {

	@Test
	void selectsOnlyAReadyMemberGrantAndRechecksAfterInference() throws Exception {
		try (var pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start()) {
			var admin = new JdbcTemplate(pg.getPostgresDatabase());
			admin.execute("CREATE ROLE zincc_reader NOLOGIN");
			admin.execute("CREATE ROLE chat_test LOGIN IN ROLE zincc_reader");
			Flyway.configure().dataSource(pg.getPostgresDatabase()).defaultSchema("zincc").load().migrate();
			var org = UUID.randomUUID();
			var workspace = UUID.randomUUID();
			var alice = new IntegrationStore.Scope(workspace, UUID.randomUUID());
			var bob = new IntegrationStore.Scope(workspace, UUID.randomUUID());
			admin.update("INSERT INTO zincc.organization VALUES(?)", org);
			admin.update("INSERT INTO zincc.workspace VALUES(?,?,'Test')", workspace, org);
			admin.update("INSERT INTO zincc.principal VALUES(?),(?)", alice.owner(), bob.owner());
			admin.update("INSERT INTO zincc.workspace_membership VALUES(?,?,'owner'),(?,?,'member')", workspace,
					alice.owner(), workspace, bob.owner());
			var json = new JsonMapper();
			var env = new MockEnvironment();
			var store = new IntegrationStore(pg.getDatabase("chat_test", "postgres"), new ProviderClient(env, json),
					env, json);
			var models = mock(ModelGateway.class);
			var principals = mock(SessionPrincipal.class);
			when(principals.resolve(null)).thenReturn(Optional.of(alice.owner()));
			var api = new ChatSourceApi(store, principals, models, json);
			var plain = new ModelGateway.Route("plain", "local", "plain", Set.of(TEXT), 64);
			var structured = new ModelGateway.Route("structured", "local", "structured", Set.of(JSON_OBJECT), 64);
			when(models.routes()).thenReturn(List.of(plain, structured));
			var feed = store.add(alice, "gmail", "Sensitive account label", "", true, null);
			store.add(bob, "calendar", "Other member", "", true, null);
			var request = new ChatSourceApi.Request(feed, "plain", "Show recent inbox subjects");
			assertThat(api.directory(null, workspace).sources()).singleElement()
				.satisfies(f -> assertThat(f.ready()).isFalse());
			assertThatThrownBy(() -> api.read(bob, request)).hasMessageContaining("404");
			assertThatThrownBy(() -> api.read(alice, request)).hasMessageContaining("409");
			verify(models, never()).text(any(), any());
			store.sync(alice, feed, true);
			store.sync(alice, feed, true);
			var localItems = store.scoped(alice, () -> store.summaries(feed));
			assertThat(localItems).hasSize(2);
			when(models.text(eq("plain"), anyString())).thenAnswer(inv -> {
				String prompt = inv.getArgument(1);
				assertThat(prompt).contains("gmail", request.question())
					.doesNotContain(feed.toString(), "Sensitive account label");
				for (var item : localItems)
					assertThat(prompt).doesNotContain(item.summary());
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
				return Mono.just(new ModelGateway.Result(plain, "read_recent", null, "stop"));
			});
			var answer = api.read(alice, request);
			assertThat(answer.items()).containsExactlyElementsOf(localItems);
			assertThat(answer.fixture()).isTrue();
			assertThat(answer.syncedAt()).isNotNull();
			var otherRequest = new ChatSourceApi.Request(feed, "structured", "Delete all email");
			when(models.jsonObject(eq("structured"), anyString(), anyMap())).thenReturn(
					Mono.just(new ModelGateway.Result(structured, "{\"action\":\"unsupported\"}", null, "stop")),
					Mono.just(new ModelGateway.Result(structured, "{\"action\":\"read_recent\",\"feed\":\"another\"}",
							null, "stop")));
			assertThat(api.read(alice, otherRequest).items()).isEmpty();
			assertThatThrownBy(() -> api.read(alice, otherRequest)).hasMessageContaining("502");
			admin.update("UPDATE zincc.feed SET synced_at=now()-interval '11 minutes' WHERE id=?", feed);
			assertThatThrownBy(() -> api.read(alice, request)).hasMessageContaining("409");
			admin.update("UPDATE zincc.feed SET synced_at=now() WHERE id=?", feed);
			doAnswer(inv -> {
				store.revoke(alice, feed);
				return Mono.just(new ModelGateway.Result(plain, "read_recent", null, "stop"));
			}).when(models).text(eq("plain"), anyString());
			assertThatThrownBy(() -> api.read(alice, request)).hasMessageContaining("404");
			assertThat(api.directory(null, workspace).sources()).isEmpty();
		}
	}

}
