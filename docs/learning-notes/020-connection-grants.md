# Who owns the connector credential

P3.4a implemented one resource-scoped Google Tasks OAuth connection and a local protocol fixture. A configured workspace and principal can activate the binding. A delegate needs a separate authorized binding. The connector exchanges a refresh credential for a bearer token and serializes refresh within the process.

Revocation invalidates Zincc's grant generation. Reconnecting cannot revive the old approval. Provider-side account revocation is separate. The local sandbox exercises the token endpoint and authenticated resource requests. Real-account consent, rotation and multi-replica token refresh have not been qualified.

[Component and operating limits](../modules/phase-three-agents.md)
