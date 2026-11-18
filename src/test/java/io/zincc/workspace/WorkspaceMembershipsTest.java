package io.zincc.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceMembershipsTest {

	private EmbeddedPostgres server;

	private DataSource owner;

	private JdbcTemplate admin;

	private JdbcTemplate reader;

	private HikariDataSource pool;

	private AnnotationConfigApplicationContext context;

	private WorkspaceMemberships memberships;

	private final UUID alice = UUID.randomUUID();

	private final UUID bob = UUID.randomUUID();

	private final UUID first = UUID.randomUUID();

	private final UUID second = UUID.randomUUID();

	@BeforeAll
	void start() throws Exception {
		this.server = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start();
		this.admin = new JdbcTemplate(this.server.getPostgresDatabase());
		this.admin.execute("CREATE ROLE zincc_reader NOLOGIN");
		this.admin.execute("CREATE ROLE schema_owner LOGIN");
		this.admin.execute("CREATE ROLE runtime_user LOGIN IN ROLE zincc_reader");
		this.admin.execute("CREATE SCHEMA zincc AUTHORIZATION schema_owner");
		this.owner = this.server.getDatabase("schema_owner", "postgres");
		assertThat(flyway("classpath:db/migration").migrate().migrationsExecuted).isEqualTo(1);
		var ownerJdbc = new JdbcTemplate(this.owner);
		UUID org = ownerJdbc.queryForObject("INSERT INTO zincc.organization DEFAULT VALUES RETURNING id", UUID.class);
		ownerJdbc.update("INSERT INTO zincc.principal(id) VALUES (?), (?)", this.alice, this.bob);
		ownerJdbc.update("INSERT INTO zincc.workspace VALUES (?, ?, 'Personal'), (?, ?, 'Team')", this.first, org,
				this.second, org);
		ownerJdbc.update("INSERT INTO zincc.workspace_membership VALUES (?, ?, 'owner'), (?, ?, 'member')", this.first,
				this.alice, this.second, this.bob);

		this.context = new AnnotationConfigApplicationContext();
		this.context.getEnvironment().setActiveProfiles("postgres");
		TestPropertyValues
			.of("zincc.db.url=jdbc:postgresql://127.0.0.1:" + this.server.getPort() + "/postgres",
					"zincc.db.user=runtime_user", "zincc.db.password=", "zincc.db.pool-size=1")
			.applyTo(this.context);
		this.context.register(WorkspaceDatabase.class);
		this.context.refresh();
		this.memberships = this.context.getBean(WorkspaceMemberships.class);
		this.pool = this.context.getBean(HikariDataSource.class);
		this.reader = new JdbcTemplate(this.pool);
	}

	private Flyway flyway(String... locations) {
		return Flyway.configure().dataSource(this.owner).defaultSchema("zincc").locations(locations).load();
	}

	@AfterAll
	void stop() throws Exception {
		if (this.context != null) {
			this.context.close();
		}
		if (this.server != null) {
			this.server.close();
		}
	}

	@Test
	void switchingWorkspacesOnOneConnectionRequiresActiveMembership() {
		assertThat(this.memberships.find(this.alice, this.first))
			.contains(new WorkspaceMemberships.Membership(this.first, "Personal", "owner"));
		assertThat(this.memberships.find(this.alice, this.second)).isEmpty();
		assertThat(this.memberships.find(this.bob, this.second))
			.contains(new WorkspaceMemberships.Membership(this.second, "Team", "member"));
		assertThat(this.memberships.find(this.bob, UUID.randomUUID())).isEmpty();
		assertThat(this.reader.queryForList("SELECT id FROM zincc.workspace")).isEmpty();
		assertThat(this.reader.queryForList("SELECT workspace_id FROM zincc.workspace_membership")).isEmpty();
	}

	@Test
	void unfilteredSqlIsScopedAndRollbackClearsContext() {
		var tx = new TransactionTemplate(new DataSourceTransactionManager(this.pool));
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			this.reader.queryForObject("SELECT set_config('zincc.principal_id', ?, true)", String.class,
					this.alice.toString());
			this.reader.queryForObject("SELECT set_config('zincc.workspace_id', ?, true)", String.class,
					this.first.toString());
			assertThat(this.reader.queryForList("SELECT id FROM zincc.workspace", UUID.class))
				.containsExactly(this.first);
			this.reader.queryForObject("SELECT set_config('zincc.workspace_id', ?, true)", String.class,
					this.second.toString());
			assertThat(this.reader.queryForList("SELECT id FROM zincc.workspace")).isEmpty();
			throw new IllegalStateException("rollback test");
		})).isInstanceOf(IllegalStateException.class).hasMessage("rollback test");
		assertThat(this.reader.queryForList("SELECT id FROM zincc.workspace")).isEmpty();
	}

	@Test
	void runtimeCannotGrantMembershipAndOwnerCannotBeUsedForRuntime() {
		assertThatThrownBy(() -> this.reader.update("INSERT INTO zincc.workspace_membership VALUES (?, ?, 'owner')",
				this.second, this.alice))
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.isInstanceOfSatisfying(java.sql.SQLException.class,
					cause -> assertThat(cause.getSQLState()).isEqualTo("42501"));
		assertThatThrownBy(() -> new WorkspaceDatabase().workspaceMemberships(this.owner))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void revocationAppliesOnTheNextLookup() {
		var ownerJdbc = new JdbcTemplate(this.owner);
		UUID guest = ownerJdbc.queryForObject("INSERT INTO zincc.principal DEFAULT VALUES RETURNING id", UUID.class);
		ownerJdbc.update("INSERT INTO zincc.workspace_membership VALUES (?, ?, 'member')", this.first, guest);
		assertThat(this.memberships.find(guest, this.first)).isPresent();
		ownerJdbc.update("DELETE FROM zincc.workspace_membership WHERE workspace_id = ? AND principal_id = ?",
				this.first, guest);
		assertThat(this.memberships.find(guest, this.first)).isEmpty();
		assertThat(this.memberships.find(this.alice, this.first)).isPresent();
	}

	@Test
	void failedMigrationRollsBackAndRetryKeepsExistingMemberships(@TempDir Path migrations) throws Exception {
		Path next = migrations.resolve("V2__test_recovery.sql");
		Files.writeString(next, "CREATE TABLE zincc.recovery_probe(id int); SELECT 1 / 0;");
		var nextFlyway = flyway("classpath:db/migration", "filesystem:" + migrations);
		assertThatThrownBy(nextFlyway::migrate).isInstanceOf(FlywayException.class);
		assertThat(this.admin.queryForObject("SELECT to_regclass('zincc.recovery_probe') IS NULL", Boolean.class))
			.isTrue();
		Files.writeString(next, "CREATE TABLE zincc.recovery_probe(id int);");
		assertThat(nextFlyway.migrate().migrationsExecuted).isEqualTo(1);
		nextFlyway.validate();
		assertThat(nextFlyway.migrate().migrationsExecuted).isZero();
		assertThat(this.memberships.find(this.alice, this.first)).isPresent();
	}

}
