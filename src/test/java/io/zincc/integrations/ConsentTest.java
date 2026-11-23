package io.zincc.integrations;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import io.zincc.identity.SessionPrincipal;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsentTest {

	@Test
	void consentIsSingleUseAndBoundToTheSignedInActor() {
		var principals = mock(SessionPrincipal.class);
		var user = mock(OidcUser.class);
		var session = new MockHttpSession();
		var scope = new IntegrationStore.Scope(UUID.randomUUID(), UUID.randomUUID());
		var api = new IntegrationApi(principals, null, null);
		var req = new IntegrationApi.Connect("gmail", "Work", "", false);
		session.setAttribute(IntegrationApi.class.getName() + ".consent",
				new IntegrationApi.Consent("correct", "verifier", scope, req, Instant.now().plusSeconds(60)));
		assertThatThrownBy(() -> api.callback(user, session, "wrong", "unused")).hasMessageContaining("400");
		verifyNoInteractions(principals);
		when(principals.resolve(user)).thenReturn(java.util.Optional.of(UUID.randomUUID()));
		assertThatThrownBy(() -> api.callback(user, session, "correct", "unused")).hasMessageContaining("400");
		assertThatThrownBy(() -> api.callback(user, session, "correct", "unused")).hasMessageContaining("400");
		verify(principals, times(1)).resolve(user);
	}

	@Test
	void googleConsentRequestsOneScopeAndPkceAndLocalOnlyDisablesIt() {
		var env = new MockEnvironment().withProperty("ZINCC_GOOGLE_CLIENT_ID", "example")
			.withProperty("ZINCC_GOOGLE_CLIENT_SECRET", "private");
		var p = new ProviderClient(env, new JsonMapper());
		String verifier = ProviderClient.verifier();
		assertThat(verifier).hasSize(43);
		assertThat(p.authorize("calendar", "random", verifier))
			.contains("code_challenge_method=S256", ProviderClient.challenge(verifier), "calendar.events.readonly",
					"state=random")
			.doesNotContain(verifier, "gmail", "private");
		env.setProperty("zincc.local-only", "true");
		assertThat(p.available("calendar")).isFalse();
	}

}
