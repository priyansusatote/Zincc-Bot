package io.zincc.identity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Local protocol fixture. Tests authorize a one-use code in place of a provider login
 * screen.
 */
final class LocalOidcIssuer implements AutoCloseable {

	private final HttpServer server;

	private final RSAKey key;

	private final Map<String, String> nonces = new ConcurrentHashMap<>();

	private final JsonMapper json = new JsonMapper();

	LocalOidcIssuer() throws IOException, JOSEException {
		this.key = new RSAKeyGenerator(2048).keyID("test").generate();
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/.well-known/openid-configuration",
				request -> reply(request, 200,
						Map.of("issuer", issuer(), "authorization_endpoint", issuer() + "/authorize", "token_endpoint",
								issuer() + "/token", "jwks_uri", issuer() + "/keys", "subject_types_supported",
								new String[] { "public" }, "id_token_signing_alg_values_supported",
								new String[] { "RS256" }, "response_types_supported", new String[] { "code" })));
		this.server.createContext("/keys",
				request -> reply(request, 200, Map.of("keys", new Object[] { this.key.toPublicJWK().toJSONObject() })));
		this.server.createContext("/token", this::exchange);
		this.server.start();
	}

	String issuer() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	String authorize(String nonce) {
		String code = UUID.randomUUID().toString();
		this.nonces.put(code, nonce);
		return code;
	}

	private void exchange(HttpExchange request) throws IOException {
		var form = new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		String nonce = null;
		for (String field : form.split("&")) {
			if (field.startsWith("code=")) {
				nonce = this.nonces.remove(URLDecoder.decode(field.substring(5), StandardCharsets.UTF_8));
			}
		}
		if (nonce == null) {
			reply(request, 400, Map.of("error", "invalid_grant"));
			return;
		}
		var now = Instant.now();
		var claims = new JWTClaimsSet.Builder().issuer(issuer())
			.subject("local-user")
			.audience("zincc-test")
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plusSeconds(300)))
			.claim("nonce", nonce)
			.build();
		var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
		try {
			token.sign(new RSASSASigner(this.key));
		}
		catch (JOSEException ex) {
			throw new IOException(ex);
		}
		reply(request, 200, Map.of("access_token", "fixture", "token_type", "Bearer", "id_token", token.serialize()));
	}

	private void reply(HttpExchange request, int status, Object response) throws IOException {
		try (request) {
			byte[] bytes = this.json.writeValueAsBytes(response);
			request.getResponseHeaders().set("Content-Type", "application/json");
			request.sendResponseHeaders(status, bytes.length);
			request.getResponseBody().write(bytes);
		}
	}

	@Override
	public void close() {
		this.server.stop(0);
	}

}
