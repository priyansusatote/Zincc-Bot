# ADR 0011 — Browser identity and live authorization

Adopted for P1.3.

**Decision.** Use a configurable OIDC confidential client, server-side servlet sessions, and owner-provisioned issuer/subject bindings. Return workspace details only through the existing tenant-scoped membership lookup. The service can resolve an exact identity through a database function. The schema owner controls identity bindings.

**Reason.** A browser-held access token would add token storage and validation responsibilities to the workbench. Copying a workspace role into a session would delay revocation. This design keeps tokens server-side and makes subsequent workspace reads reflect committed membership changes. It costs database round trips and requires operators to provision bindings.

**Deployment boundary.** The first deployment shape is a single application instance with ephemeral sessions. Choose a shared session strategy when spanning replicas. OIDC provider selection remains an operator choice. [Configuration and qualification](../modules/browser-sessions.md)
