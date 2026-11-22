package io.zincc.research;

import java.util.UUID;
import io.zincc.identity.SessionPrincipal;
import io.zincc.workspace.WorkspaceMemberships;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("oidc")
class ResearchApi {

	private final SessionPrincipal principals;

	private final WorkspaceMemberships memberships;

	private final WebResearch research;

	ResearchApi(SessionPrincipal principals, WorkspaceMemberships memberships, WebResearch research) {
		this.principals = principals;
		this.memberships = memberships;
		this.research = research;
	}

	record Query(String query, boolean fresh) {
	}

	@PostMapping("/api/workspaces/{workspace}/research")
	WebResearch.Answer search(@AuthenticationPrincipal OidcUser user, @PathVariable UUID workspace,
			@RequestBody Query command) {
		principals.resolve(user)
			.flatMap(p -> memberships.find(p, workspace))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return research.answer(command.query(), command.fresh());
	}

}
