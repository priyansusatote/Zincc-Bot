# P6.1 · One broker for two worker protocols

**Implemented and exercised locally.** A PDF parser consumes one binary payload. A briefing server exchanges several MCP frames. Both now acquire the same kind of sandbox handle. The broker owns the Docker command, process admission and removal. Each caller keeps its own protocol, output bounds and timeout. A shared semaphore prevents the two worker families from independently filling all available slots.

**Verification.** The Docker test inspects the running container's privileges, memory, PID limit, mount and network configuration, then closes the handle and checks removal. The knowledge suite still exercises actual parsing and embeddings. These observations apply to trusted fixed programs on one host. Shared-kernel isolation for public customer code requires separate qualification.
