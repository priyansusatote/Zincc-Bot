package io.zincc.identity;

import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import io.zincc.workspace.WorkspaceMemberships;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("oidc")
class SessionApi {

	private final JdbcTemplate jdbc;

	private final WorkspaceMemberships memberships;

	SessionApi(DataSource source, WorkspaceMemberships memberships) {
		this.jdbc = new JdbcTemplate(source);
		this.memberships = memberships;
	}

	@GetMapping("/api/session")
	SessionView session(@AuthenticationPrincipal OidcUser user, CsrfToken csrf) {
		return new SessionView(principal(user).isPresent(), csrf.getHeaderName(), csrf.getToken());
	}

	@GetMapping("/api/workspaces/{workspaceId}")
	WorkspaceMemberships.Membership workspace(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspaceId) {
		return principal(user).flatMap(id -> this.memberships.find(id, workspaceId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private Optional<UUID> principal(OidcUser user) {
		var token = user.getIdToken();
		return Optional.ofNullable(this.jdbc.queryForObject("SELECT zincc.resolve_oidc_principal(?, ?)", UUID.class,
				token.getClaimAsString("iss"), token.getSubject()));
	}

	record SessionView(boolean provisioned, String csrfHeader, String csrfToken) {
	}

}
