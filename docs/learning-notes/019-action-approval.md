# Binding approval to an exact write

P3.3 implemented tool manifests and an action ledger. The digest covers the run, immutable version, operation, grant generation, connection reference, list and title. Approval and dispatch each check the current grant. The write claim commits before the external call.

A crash after the claim can look identical to a crash after the provider committed. Retrying the POST could create two tasks. Recovery therefore reads back evidence and can return unknown. Tests exercise a changed digest, cancelled proposal, revoked permission and reconnect under a new generation.

[Component and operating limits](../modules/phase-three-agents.md)
