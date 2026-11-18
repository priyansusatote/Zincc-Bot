package io.zincc.identity;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import io.zincc.ZinccApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowserSessionTest {

	private EmbeddedPostgres postgres;

	private LocalOidcIssuer issuer;

	private ServletWebServerApplicationContext app;

	private JdbcTemplate owner;

	private final UUID principal = UUID.randomUUID();

	private final UUID workspace = UUID.randomUUID();

	private final UUID otherWorkspace = UUID.randomUUID();

	@BeforeAll
	void start() throws Exception {
		this.issuer = new LocalOidcIssuer();
		this.postgres = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start();
		var admin = new JdbcTemplate(this.postgres.getPostgresDatabase());
		admin.execute("CREATE ROLE zincc_reader NOLOGIN");
		admin.execute("CREATE ROLE migrator LOGIN");
		admin.execute("CREATE ROLE app_user LOGIN IN ROLE zincc_reader");
		admin.execute("CREATE SCHEMA zincc AUTHORIZATION migrator");
		var source = this.postgres.getDatabase("migrator", "postgres");
		this.owner = new JdbcTemplate(source);
		Flyway.configure().dataSource(source).defaultSchema("zincc").target("1").load().migrate();
		UUID org = this.owner.queryForObject("INSERT INTO zincc.organization DEFAULT VALUES RETURNING id", UUID.class);
		this.owner.update("INSERT INTO zincc.principal VALUES (?)", this.principal);
		this.owner.update("INSERT INTO zincc.workspace VALUES (?, ?, 'Mine'), (?, ?, 'Elsewhere')", this.workspace, org,
				this.otherWorkspace, org);
		this.owner.update("INSERT INTO zincc.workspace_membership VALUES (?, ?, 'owner')", this.workspace,
				this.principal);
		Flyway.configure().dataSource(source).defaultSchema("zincc").load().migrate();
		this.app = (ServletWebServerApplicationContext) new SpringApplicationBuilder(ZinccApplication.class).run(
				"--spring.profiles.active=oidc", "--server.port=0", "--server.servlet.session.cookie.secure=false",
				"--zincc.workbench.enabled=true", "--ZINCC_OIDC_ISSUER=" + this.issuer.issuer(),
				"--ZINCC_OIDC_CLIENT_ID=zincc-test", "--ZINCC_OIDC_CLIENT_SECRET=test-only",
				"--ZINCC_PUBLIC_BASE_URL={baseUrl}",
				"--zincc.db.url=jdbc:postgresql://127.0.0.1:" + this.postgres.getPort() + "/postgres",
				"--zincc.db.user=app_user", "--zincc.db.password=");
	}

	@AfterAll
	void stop() throws Exception {
		if (this.app != null)
			this.app.close();
		if (this.postgres != null)
			this.postgres.close();
		if (this.issuer != null)
			this.issuer.close();
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.app.getWebServer().getPort() + path))
			.timeout(Duration.ofSeconds(10));
	}

	private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
		return browser.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> callback(HttpClient browser, boolean validNonce) throws Exception {
		var redirect = get(browser, "/oauth2/authorization/zincc");
		assertThat(redirect.statusCode()).isEqualTo(302);
		var auth = URI.create(redirect.headers().firstValue("Location").orElseThrow());
		assertThat(auth.getPath()).isEqualTo("/authorize");
		var query = UriComponentsBuilder.fromUri(auth).build().getQueryParams();
		assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
		assertThat(query.getFirst("code_challenge")).isNotBlank();
		String code = this.issuer.authorize(validNonce ? query.getFirst("nonce") : "different-nonce");
		var result = get(browser, "/login/oauth2/code/zincc?code=" + code + "&state=" + query.getFirst("state"));
		if (validNonce) {
			String initialCookie = redirect.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
			String authenticatedCookie = result.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
			assertThat(authenticatedCookie).isNotEqualTo(initialCookie);
		}
		return result;
	}

	@Test
	void verifiedSessionChecksLiveWorkspaceAccessAndLogsOutWithCsrf() throws Exception {
		var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		try (var browser = HttpClient.newBuilder().cookieHandler(cookies).build()) {
			assertThat(get(browser, "/api/session").statusCode()).isEqualTo(401);
			assertThat(get(browser, "/api/workspaces").statusCode()).isEqualTo(401);
			var login = callback(browser, true);
			assertThat(login.statusCode()).isEqualTo(302);
			assertThat(login.headers().firstValue("Location").orElseThrow()).endsWith("/workbench/");
			assertThat(login.headers().firstValue("Set-Cookie").orElseThrow()).contains("ZINCCSESSION=", "HttpOnly",
					"SameSite=Lax");
			var json = new JsonMapper();
			this.owner.update("INSERT INTO zincc.oidc_identity VALUES (?, 'local-user', ?)",
					"https://different.invalid", this.principal);
			var session = json.readTree(get(browser, "/api/session").body());
			assertThat(session.path("provisioned").asBoolean()).isFalse();
			String mine = "/api/workspaces/" + this.workspace;
			assertThat(get(browser, mine).statusCode()).isEqualTo(404);
			assertThat(get(browser, "/api/workspaces").body()).isEqualTo("[]");
			this.owner.update("INSERT INTO zincc.oidc_identity VALUES (?, 'local-user', ?)", this.issuer.issuer(),
					this.principal);
			assertThat(json.readTree(get(browser, "/api/session").body()).path("provisioned").asBoolean()).isTrue();
			assertThat(json.readTree(get(browser, mine).body()).path("role").asString()).isEqualTo("owner");
			assertThat(get(browser, "/api/workspaces/" + this.otherWorkspace).statusCode()).isEqualTo(404);
			var directory = json.readTree(get(browser, "/api/workspaces").body());
			assertThat(directory.size()).isEqualTo(1);
			assertThat(directory.get(0).path("workspaceId").asString()).isEqualTo(this.workspace.toString());
			this.owner.update("DELETE FROM zincc.workspace_membership WHERE workspace_id = ?", this.workspace);
			assertThat(get(browser, mine).statusCode()).isEqualTo(404);
			var logout = request("/api/logout").POST(HttpRequest.BodyPublishers.noBody());
			assertThat(get(browser, "/api/workspaces").body()).isEqualTo("[]");
			assertThat(browser.send(logout.build(), HttpResponse.BodyHandlers.discarding()).statusCode())
				.isEqualTo(403);
			assertThat(get(browser, "/api/session").statusCode()).isEqualTo(200);
			logout.header(session.path("csrfHeader").asString(), session.path("csrfToken").asString());
			assertThat(browser.send(logout.build(), HttpResponse.BodyHandlers.discarding()).statusCode())
				.isEqualTo(204);
			assertThat(get(browser, "/api/session").statusCode()).isEqualTo(401);
		}
	}

	@Test
	void accountSwitchRequestsProviderAccountSelection() throws Exception {
		try (var browser = HttpClient.newHttpClient()) {
			var response = get(browser, "/oauth2/authorization/zincc?account=switch");
			assertThat(response.statusCode()).isEqualTo(302);
			var target = URI.create(response.headers().firstValue("Location").orElseThrow());
			assertThat(UriComponentsBuilder.fromUri(target).build().getQueryParams().getFirst("prompt"))
				.isEqualTo("select_account");
		}
	}

	@Test
	void failedNonceValidationLeavesBrowserSignedOut() throws Exception {
		try (var browser = HttpClient.newBuilder()
			.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
			.build()) {
			assertThat(callback(browser, false).statusCode()).isEqualTo(401);
			assertThat(get(browser, "/api/session").statusCode()).isEqualTo(401);
		}
	}

}
