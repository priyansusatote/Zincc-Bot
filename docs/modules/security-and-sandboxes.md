# Execution security and customer compute

**Proposed design.** Isolation and authorization must hold even when a model follows malicious instructions in a webpage or document.

## Trust boundaries and permissions

Treat the browser, external content, model output, uploaded skills, customer agent code, and MCP server responses as untrusted. Trusted services validate identity and actions independently. Prompt-based guardrails can help quality but are not an access-control boundary.

Every tool has a typed manifest: semantic operation, read/write category, data scope, credential binding, allowed destinations, idempotency behavior, expected result, rate/cost limits, and approval policy. Do not infer safety solely from a tool name or a button label.

Evaluate current actor, workspace, connection grant, resource scope, policy revision, budget, and exact arguments before dispatch. Explicitly distinguish read, reversible local edit, external write, and high-impact operation. Users can authorize recurring classes of work within bounded grants; avoid asking on every already-authorized read.

Approval binds to an action digest, recipient/resource, proposed changes, maximum scope, expiry, and actor. Editing a recipient or payload invalidates it. Recheck revocation immediately before dispatch. A model-generated “approved” string has no authority. Maintain receipts for both successful and failed attempts without storing secrets.

## Secrets and network access

Apply [delegated authority and output audiences](../architecture-decisions/0008-delegated-authority-and-data-sharing.md) within each tenant. Check effective principal, caller/delegation and recipient access, not just workspace membership. Agent sharing never implicitly exposes private memory or credentials. A read can disclose sensitive data to a model or destination: apply outbound data policy to reads and context assembly too.

Use a secret broker with encrypted storage and short-lived scoped credentials where providers allow it. Ordinary worker payloads carry references. The connector proxy injects credentials at the destination; provider refresh tokens stay out of prompts and sandbox environment dumps.

OAuth uses state validation, PKCE where appropriate, least-privilege scopes, redirect restrictions, encrypted refresh credentials, and revocation handling. Webhooks require signature verification, replay protection, body-size limits, and idempotent delivery records.

Bind OAuth/token audiences to the intended resource; never pass an arbitrary caller token through to a downstream MCP/API service. Restrict discovery/authorization destinations and validate server/session identity. Bind approvals to current service identity and schema revision. See [MCP security guidance](https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices). Refresh uses one scoped concurrent owner and atomic rotated-token replacement; revocation and expired consent stop dependent automations visibly.

Default-deny sandbox egress; allow declared destinations through a policy proxy with DNS/IP checks and protections against metadata-service access, private-network SSRF, redirects, and rebinding. A legitimate API can still receive excessive data: inspect purpose, fields, and destination scope, not just hostname.

Proxy environment variables alone do not constrain raw sockets. Enforce routes/firewall/network namespace policy outside tenant code, including DNS, IPv6 and non-HTTP paths; test bypass attempts. Local private-network destinations require explicit per-runner grants rather than weakening the hosted default. Allowlist child-process environment variables; no broad inheritance of server secrets.

Browser login is a special case: cookies and authenticated page content necessarily enter the browser environment. Isolate profiles by account/tenant, restrict exports, redact recordings, limit retention, and provide user takeover. API-secret isolation does not remove browser-session risk.

## Execution modes

| Mode | Intended use | Boundary and limitations |
| --- | --- | --- |
| Development Docker | Trusted developer experiments | Rootless where possible, restricted mounts; shared kernel is not sufficient for hostile multi-tenant code |
| Hosted hardened sandbox | Customer code/browser/media | Validated microVM or gVisor-backed isolation, resource/egress limits, isolated credentials |
| Customer runner + Docker | User-owned Linux/WSL/Docker host | Explicit local enrolment, dedicated runner, constrained jobs; user controls host |
| Customer VM | Stronger separation on owned/private compute | Dedicated Linux VM or approved virtualization backend; hardware and OS support required |
| Local desktop bridge | Explicit native app automation | Separate capability with visible permissions and pause control; container access is not desktop permission |
| Private cluster | Enterprise/private deployment | Customer identity/network/storage; same protocol and policy tests |

Never expose a Docker daemon to the public internet, mount the host Docker socket into agent code, give a remote agent a privileged container, or mount the entire home directory by default. A trusted broker may manage containers; tenant code must not inherit broker privileges.

## Customer runner enrolment

1. User installs a signed runner in their chosen environment and enrols with a short-lived one-time token.
2. Runner establishes an outbound authenticated channel and receives a distinct, revocable device identity. No inbound public Docker port is needed.
3. User selects approved directories, available models, CPU/RAM/GPU quotas, networking, and supported capabilities. Device-local controls can restrict cloud instructions further.
4. Scheduler issues a short-lived job lease with workspace, artifact references, deadline, policy, and capability limits.
5. Runner validates locally, starts an isolated environment, streams progress, and uploads only permitted results.
6. User can pause, disconnect, revoke, or remove credentials. A lost heartbeat stops new work; in-flight work follows an explicit pause/cancel policy.

Distinguish cloud-controlled execution on a local machine from a fully local control plane. Explain exactly which instructions, logs, documents, and results cross the network. Signed updates need rollback and compatibility checks; do not remotely execute arbitrary updater commands.

## Sandbox provider contract

Define create, attach, execute, stream, upload/download, snapshot, restore, suspend, terminate, health, and capability discovery. Include image digest, architecture, limits, lease identity, environment generation, egress policy, mount policy, and expiry. Snapshots preserve secrets and data; encrypt, authorize restore, and expire them accordingly.

[CubeSandbox](https://github.com/TencentCloud/CubeSandbox) is a candidate for KVM-backed microVMs and E2B-style integration. Its performance claims need our benchmarks; Linux/KVM availability is a placement requirement. [Substrate](https://github.com/agent-substrate/substrate) offers Kubernetes actor/worker and credential-broker patterns. Neither is automatically adopted as Zincc's complete security boundary.

Substrate's own [security document](https://raw.githubusercontent.com/agent-substrate/substrate/main/docs/security/security.md) describes remaining work around per-actor storage protections and worker privileges. Those gaps require specific validation/mitigation before adoption. The platform still owns user authorization and agent identity.

Start with one constrained execution backend. Compare startup/restore latency, isolation, idle cost, operational complexity, Windows/local usability, and recovery before adding a second. Warm pools need tenant-safe reset; never return another user's browser profile or disk contents.

## Isolation prerequisites and provider qualification

Isolation must precede its consumers. P4.0 introduces the fixed-purpose worker boundary before PDF parsing/analytics; executable third-party plugins wait for qualified P6 isolation. Validate package manifests/signatures/permission changes before import, and perform import/build hooks inside the worker as well. Checking an exported permission list after importing a module is too late to prevent top-level code execution. Curated instruction-only skills can arrive earlier; arbitrary code cannot.

CubeSandbox's [current quickstart](https://raw.githubusercontent.com/TencentCloud/CubeSandbox/master/docs/guide/quickstart.md) adds XFS/storage and host prerequisites; its PVM path entails kernel/root changes and its packaged deployment adds MySQL/Redis. Assess this complete footprint before selecting it. The [Substrate security matrix](https://raw.githubusercontent.com/agent-substrate/substrate/main/docs/security/security.md) distinguishes implemented segregation from incomplete restore/encryption/reset/quotas; qualify exact capabilities, not a generic “zero trust” label.

## Native Windows actions and human takeover

Clarified **16 November 2026**: P13.2 includes acting inside real Windows applications. The concrete acceptance journey is “Open Paint, draw a car through its tools, save it to a granted Pictures folder, then verify the saved image.” Generating a PNG elsewhere and opening it is a different task and does not satisfy an explicit request to draw through Paint.

The enrolled Windows bridge runs in the authorized interactive user session. Prefer UI Automation controls for discoverable menus/buttons; use screenshot grounding and bounded mouse/keyboard gestures for canvases. Microsoft documents [UI Automation](https://learn.microsoft.com/en-us/windows/win32/winauto/uiauto-uiautomationoverview) and [SendInput](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput); input injection is restricted by integrity levels. Secure desktops, elevation prompts, lock screens, missing UI support and changed app layouts require explicit handling, never an assumed bypass.

Observe → locate → validate foreground/window identity → act → observe again → verify outcome. Recheck coordinates after DPI, monitor or window changes. Limit gesture batches and release held keys/buttons on cancellation. Pause on user interference or lost session; validate file path and actual save result before completion. Local-only policy also covers screenshots and model vision calls.

Only one actor holds interactive control of a desktop at a time. Multiple agents may plan in parallel, but shared screen input is serialized. Human takeover revokes future agent input at the broker and driver using a lease/fencing generation. A dispatched gesture may already be completing; display that state rather than claiming an instantaneous rollback. Resume only after a fresh observation. Test disconnects, lease expiry, rapid double takeover, stale requests and user input races.

App-specific grants need enforcement in the driver and tool broker, plus restrictions on arbitrary shell/file tools that could bypass them. A “Paint only” chip is a proposed policy surface, not proof that a conventional desktop process has OS-enforced application confinement. Strong isolation may require a dedicated Windows user/session or VM; host control necessarily touches the user's real session. Customer Docker/WSL access alone does not confer host desktop control.

The [new repository review](../research/2026-11-16-agent-repositories.md) informs takeover, plugin and lifecycle tests. Kiri is an optional container provider qualification candidate, not this Windows bridge.

## Verification gates

Test malicious document instructions, secret exfiltration, cross-tenant object IDs, path traversal, symlink escapes, fork/zip bombs, unsafe mounts, network-policy bypass, stale approval, revoked device, worker crash, quota exhaustion, snapshot restore authorization, and browser account crossover.

Measure and record the tested scope; do not label any sandbox “unbreakable.” Add structured evidence-led reviews inspired by [Cloudflare's audit skill](https://github.com/cloudflare/security-audit-skill). Findings need reproducible evidence, severity, fix verification, and explicit unresolved status.
