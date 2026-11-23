package io.zincc.identity;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SessionSecurity {

	@Bean
	@Profile("!oidc")
	SecurityFilterChain healthOnly(HttpSecurity http) throws Exception {
		return http
			.authorizeHttpRequests(
					auth -> auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/workbench", "/workbench/**")
						.permitAll()
						.anyRequest()
						.denyAll())
			.requestCache(cache -> cache.disable())
			.logout(logout -> logout.disable())
			.exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, ex) -> res.setStatus(404)))
			.build();
	}

	@Bean
	@Profile("oidc")
	SecurityFilterChain browserSessions(HttpSecurity http, ClientRegistrationRepository registrations,
			HttpServletRequest currentRequest, @Value("${zincc.workbench.enabled:false}") boolean workbench)
			throws Exception {
		var requests = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
		requests.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce().andThen(builder -> {
			if ("switch".equals(currentRequest.getParameter("account"))) {
				builder.additionalParameters(params -> params.put("prompt", "select_account"));
			}
		}));
		return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/integrations/slack/events", "/api/device/exchange"))
			.authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/integrations/slack/events", "/api/device/exchange")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/workspaces/{workspaceId}/runners",
						"/api/workspaces/{workspaceId}/runners/{device}")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/workspaces/{workspaceId}/runners", "/api/integrations/callback",
						"/api/workspaces/{workspaceId}/integrations")
				.authenticated()
				.requestMatchers(HttpMethod.POST, "/api/workspaces/{workspaceId}/integrations/connections",
						"/api/workspaces/{workspaceId}/integrations/connections/{id}",
						"/api/workspaces/{workspaceId}/integrations/skills",
						"/api/workspaces/{workspaceId}/integrations/routines",
						"/api/workspaces/{workspaceId}/integrations/routines/{id}")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/workspaces/{workspaceId}/knowledge",
						"/api/workspaces/{workspaceId}/knowledge/{id}/source",
						"/api/workspaces/{workspaceId}/knowledge/{id}/pages/{page}",
						"/api/workspaces/{workspaceId}/knowledge/{id}/datasets",
						"/api/workspaces/{workspaceId}/knowledge/{id}/dashboards",
						"/api/workspaces/{workspaceId}/knowledge/{id}/dashboards/{dashboard}",
						"/api/workspaces/{workspaceId}/knowledge/{id}/dashboards/{dashboard}/export")
				.authenticated()
				.requestMatchers(HttpMethod.POST, "/api/workspaces/{workspaceId}/knowledge",
						"/api/workspaces/{workspaceId}/knowledge/{id}/search")
				.authenticated()
				.requestMatchers(HttpMethod.PUT, "/api/workspaces/{workspaceId}/knowledge/{id}/dashboards/{dashboard}")
				.authenticated()
				.requestMatchers(HttpMethod.DELETE, "/api/workspaces/{workspaceId}/knowledge/{id}")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/workspaces/{workspaceId}/agents")
				.authenticated()
				.requestMatchers(HttpMethod.POST, "/api/workspaces/{workspaceId}/agents/{id}/versions",
						"/api/workspaces/{workspaceId}/agents/{id}/runs",
						"/api/workspaces/{workspaceId}/agents/connection",
						"/api/workspaces/{workspaceId}/agents/runs/{id}/decision")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/workbench", "/workbench/**")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/workspaces/{workspaceId}/conversations/{id}/turns",
						"/api/workspaces/{workspaceId}/research")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/workspaces/{workspaceId}/conversations",
						"/api/workspaces/{workspaceId}/conversations/{id}",
						"/api/workspaces/{workspaceId}/conversations/{id}/events")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/session", "/api/workspaces", "/api/workspaces/{workspaceId}")
				.authenticated()
				.anyRequest()
				.denyAll())
			.oauth2Login(login -> login.loginPage("/oauth2/authorization/zincc")
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(requests))
				.authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
				.defaultSuccessUrl(workbench || new ClassPathResource("static/workbench/index.html").exists()
						? "/workbench/" : "/api/session", true)
				.failureHandler((req, res, ex) -> res.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
			.sessionManagement(sessions -> sessions.sessionFixation(fixation -> fixation.changeSessionId()))
			.requestCache(cache -> cache.disable())
			.logout(logout -> logout.logoutUrl("/api/logout")
				.deleteCookies("ZINCCSESSION")
				.logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT)))
			.exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, ex) -> res.setStatus(401)))
			.build();
	}

}
