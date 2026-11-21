package io.zincc.identity;

import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
@Profile("oidc")
public class SessionPrincipal {

	private final JdbcTemplate jdbc;

	public SessionPrincipal(DataSource source) {
		this.jdbc = new JdbcTemplate(source);
	}

	public Optional<UUID> resolve(OidcUser user) {
		var token = user.getIdToken();
		return Optional.ofNullable(jdbc.queryForObject("SELECT zincc.resolve_oidc_principal(?, ?)", UUID.class,
				token.getClaimAsString("iss"), token.getSubject()));
	}

}
