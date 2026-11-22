# Browser sign-in and workspace access

## Run with your OIDC provider

1. Follow the [database setup](workspace-membership.md) and run Flyway through V5. Existing V1 principals and memberships retain their UUIDs.
2. Register a **confidential web client** supporting issuer discovery, authorization code flow, S256 PKCE and the `openid` scope. Register the exact callback `https://<your-zincc-origin>/login/oauth2/code/zincc`. Set `ZINCC_PUBLIC_BASE_URL` to that origin without a trailing slash.
3. Set `SPRING_PROFILES_ACTIVE=oidc` (includes `postgres`), the three runtime `ZINCC_DB_*` settings from the database setup, `ZINCC_OIDC_ISSUER`, `ZINCC_OIDC_CLIENT_ID`, and `ZINCC_OIDC_CLIENT_SECRET`. Use the issuer's exact URL. Keep the secret in the service environment. Serve the browser origin over HTTPS. With TLS termination, preserve a trusted public origin and restrict direct backend access. For HTTP **loopback development only**, set `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` and register that exact local callback with the provider.
4. As the schema owner, bind the provider’s verified issuer and subject to an existing internal principal using a parameterized insert into `zincc.oidc_identity(issuer, subject, principal_id)`. Use the subject issued for this client. Workspace membership is granted separately. One identity can sign in before provisioning and will see `provisioned: false`.
5. Open `/oauth2/authorization/zincc` in a browser. Successful sign-in lands on `/workbench/` when bundled or explicitly enabled, and on `/api/session` for standalone API use. The workbench account switch ends the local session then requests provider account selection. The provider controls the account chooser.

## Same-origin HTTP contract

| Request | Result |
| --- | --- |
| GET `/api/session` | 401 before sign-in. Otherwise returns `accountLabel`, `provisioned`, `csrfHeader`, and the current masked `csrfToken`. The client keeps the token in memory. |
| GET `/api/workspaces` | 401 before sign-in. Returns current memberships for the authenticated issuer and subject. An unbound identity receives an empty list. |
| GET `/api/workspaces/{uuid}` | 200 with `workspaceId`, `workspaceName`, and `role` for a current member. An absent identity binding, absent membership, or unknown workspace yields 404 after sign-in. |
| POST `/api/logout` | Send the session cookie and the CSRF token under the header name from the session response. Success returns 204 and invalidates the local session. A missing or invalid token yields 403. |

The server keeps the validated identity and authorized client in its HTTP session. The cookie is HttpOnly, Secure and SameSite=Lax by default. Its ID rotates at login and idle sessions expire after 30 minutes. A revoked membership takes effect on the next lookup. Signing out ends the Zincc session. Upstream single sign-out, immediate provider-side account deactivation, and shared sessions across replicas require deployment-specific work.

The test fixture runs a loopback issuer with ephemeral signing keys and real PostgreSQL. Qualify the chosen provider's discovery, redirect registration and browser cookie delivery on the deployed HTTPS origin before launch. [Spring Security login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html) · [OIDC identity claims](https://openid.net/specs/openid-connect-core-1_0.html#ClaimStability)
