# P6.3 · Enrolment and device leases

**Implemented and tested.** A signed-in owner creates a single-use pairing grant. The first authenticated device request replaces its digest with the digest of a new bearer token. PostgreSQL row locks serialize concurrent transitions. Every operation runs with workspace and principal scope under row-level security.

A lease gives the runner temporary authority to finish one job. Losing contact expires that authority even if the job process survives. The receipt update includes the device, job ID, current state and expiry in its database predicate. A late response cannot revive cancelled work. The integration test tries a second pairing, cross-owner access, duplicate submission, expiry, pause and permanent revocation.

**Trade-off.** Keeping the credential only in runner memory simplifies local secret management and makes a restart require a fresh pairing.

**Wire-level regression.** The first live check paired successfully but the next poll returned HTTP 400. Direct Java tests constructed the request with two primitive grant flags. The CLI had omitted those fields after pairing. The CLI now sends both on every envelope. A real child-process test checks the serialized fields and token rotation. The signed-in workbench then displayed the expected receipt.
