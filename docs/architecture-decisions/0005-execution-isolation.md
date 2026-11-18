# ADR 0005 — Brokered sandboxes and customer runners

Status: **Proposed**. Related: [security and sandboxes](../modules/security-and-sandboxes.md).

## Problem

Customers need code/browser execution and may want their own Docker/VM. Untrusted code must not inherit API-server credentials or host control.

## Decision

Use a sandbox-provider contract and trusted broker. Qualify a microVM or gVisor-backed hosted backend. Offer an explicitly enrolled customer runner with outbound authentication, local grants, resource limits, and revoke/pause controls. Keep native desktop automation a separate capability.

## Alternatives

- Run code in the Spring process: simplest call path but unacceptable privilege and failure coupling.
- Expose customer Docker over the internet: easy remote management but an excessive host-control surface.
- One privileged container for all agents: low cost but poor tenant and credential boundaries.
- Kubernetes from day one: supports cluster scheduling but adds unnecessary early complexity.

## Trade-offs and consequences

Stronger isolation costs memory, startup time, and operational expertise. Snapshot/warm-pool efficiency introduces data-remanence risks. Local runners improve user ownership but depend on hardware, uptime, and safe local enrolment. CubeSandbox/Substrate remain candidates until their documented gaps and host requirements are qualified.

16 November dependency correction: P4.0 establishes a fixed-purpose restricted worker contract before document processing. P6 extends it and qualifies hostile-tenant execution before public parsing, generated code or executable plugins. Package import/install hooks are execution too and belong inside isolation. Proxy settings alone are not a network boundary; monotonic lease generations and final-executor checks are required for stale-job rejection.

## Validation

Cross-tenant filesystem/network tests, secret injection boundary, malicious job limits, stale lease rejection, runner revocation, snapshot restore authorization, and browser account isolation.
