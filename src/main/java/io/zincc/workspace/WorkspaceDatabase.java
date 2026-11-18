package io.zincc.workspace;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("postgres")
class WorkspaceDatabase {

	@Bean
	HikariDataSource workspaceDataSource(Environment environment) {
		var config = new HikariConfig();
		config.setJdbcUrl(environment.getRequiredProperty("zincc.db.url"));
		config.setUsername(environment.getRequiredProperty("zincc.db.user"));
		config.setPassword(environment.getRequiredProperty("zincc.db.password"));
		config.setPoolName("workspace");
		config.setMaximumPoolSize(environment.getProperty("zincc.db.pool-size", Integer.class, 10));
		return new HikariDataSource(config);
	}

	@Bean
	WorkspaceMemberships workspaceMemberships(DataSource dataSource) {
		var jdbc = new JdbcTemplate(dataSource);
		Boolean scoped = jdbc.queryForObject("""
				SELECT row_security_active('zincc.workspace') AND row_security_active('zincc.workspace_membership')
				    AND has_table_privilege('zincc.workspace', 'SELECT')
				    AND has_table_privilege('zincc.workspace_membership', 'SELECT')
				""", Boolean.class);
		if (!Boolean.TRUE.equals(scoped)) {
			throw new IllegalStateException("Workspace database login requires SELECT and active row security");
		}
		return new WorkspaceMemberships(dataSource);
	}

}
