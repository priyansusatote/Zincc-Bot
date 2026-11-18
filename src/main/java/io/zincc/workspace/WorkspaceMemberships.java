package io.zincc.workspace;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class WorkspaceMemberships {

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	WorkspaceMemberships(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.transaction.setReadOnly(true);
	}

	/**
	 * The caller supplies the internal principal established by trusted identity code.
	 */
	public Optional<Membership> find(UUID principalId, UUID workspaceId) {
		Objects.requireNonNull(principalId);
		Objects.requireNonNull(workspaceId);
		return this.transaction.execute(status -> {
			this.jdbc.queryForObject("SELECT set_config('zincc.principal_id', ?, true)", String.class,
					principalId.toString());
			this.jdbc.queryForObject("SELECT set_config('zincc.workspace_id', ?, true)", String.class,
					workspaceId.toString());
			return this.jdbc.query("""
					SELECT w.display_name, m.role
					FROM zincc.workspace w JOIN zincc.workspace_membership m ON m.workspace_id = w.id
					WHERE w.id = ? AND m.principal_id = ?
					""", (rs, row) -> new Membership(workspaceId, rs.getString("display_name"), rs.getString("role")),
					workspaceId, principalId)
				.stream()
				.findFirst();
		});
	}

	public record Membership(UUID workspaceId, String workspaceName, String role) {
	}

}
