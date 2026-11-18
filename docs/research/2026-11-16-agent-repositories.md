# Additional agent repository review

Date: **16 November 2026**. Static source review only. No upstream application, installer, test suite or skill executed. Source snapshots are in a temporary review directory outside Zincc; no upstream implementation is incorporated into the product.

## Coverage and decisions

| Repository | Inspected commit | Tracked files | Review depth | Decision for Zincc |
| --- | --- | --- | --- | --- |
| [Kiri](https://github.com/libracoder/kiri) | `260cf66b9b56963a0170857c2b4306c38814e108` | 422 | Inventory, README, feature/security docs, license header, selected Windows resource code | Optional customer container-runtime compatibility candidate; not mandatory infrastructure or native Windows control |
| [Superbot](https://github.com/davidondrej/superbot) | `652e100fc3412239fd5a07ca7bb8566213c5b6fa` | 50 | Every source/build file listed below, selected docs and configuration | Adopt adapter/screen/handoff ideas; do not fork its backend or inherit its shared trust model |
| [Timon](https://github.com/ai-timon/timon) | `f00ffa23b7a7d829cfcb6b81940aa9a9e4c426ef` | 5,796 | Inventory, README opening, licensing, selected permission/plugin code and tests | Plugin lifecycle, explicit approval scope and coding-team ideas; no core copying proposed |
| [Rakazo](https://github.com/elie222/rakazo) | `572755b4179c88f0303a3026023331b1c3ed5f87` | 5,557 | Inventory, README opening, license header, selected computer-control/MCP code and control tests | Strong reference for control leases, takeover and provider-independent computer UX |
| [OpenMausBot](https://github.com/milind-soni/OpenMausBot) | `9f04922546a245d90cb6d7f870172eb8cc8f4973` | 8,385 | Inventory, README opening, licensing, local-computer docs, selected grant/viewer code and grant tests | Local device grants, process ownership, restricted remote viewer and platform capability reporting |

File counts include documentation, assets, translations, tests and other material, not only application source. The four larger projects did **not** receive a complete line-by-line audit. Superbot received a complete first-pass read of its 16 source/build files; this is not a formal security audit, dependency audit or runtime qualification. No guarantee of completeness or absence of defects follows from reading a file.

## Superbot: per-file source coverage

All paths are relative to the pinned Superbot snapshot. Read all lines of these files; total 3,252 lines. Embedded tests were read, not run. Cargo.lock was inventoried, not audited package by package; binary screenshots, external CLI implementations and every prose document are outside the complete source pass.

| File | Lines | Responsibility and conclusion |
| --- | ---: | --- |
| Cargo.toml | 7 | Workspace/release configuration; no reason to replace Spring |
| server/Cargo.toml | 22 | Axum/Tokio/SQLite dependencies; manifest is not a compatibility test |
| deploy/install-computer.sh | 62 | Linux display/browser installer; inspect only, not appropriate as a Windows native bridge |
| server/src/adapters/mod.rs | 234 | Common run/event interface, subprocess lifecycle, model catalogue cache; useful contract separation |
| server/src/adapters/claude.rs | 80 | CLI arguments and event mapping; bypass-permission flag must not become Zincc policy |
| server/src/adapters/codex.rs | 93 | CLI resume/events; bypass-approval/sandbox invocation unsuitable as a default governed runtime |
| server/src/adapters/pi.rs | 76 | Session files and JSON event mapping; useful portability example |
| server/src/bin/botctl.rs | 105 | Shell-accessible routine/peer/handoff API; use scoped identities and standard HTTP/JSON clients in Zincc |
| server/src/cron.rs | 94 | Cron parser, polling and embedded tests; teaches schedule semantics and atomic-delivery gaps |
| server/src/db.rs | 328 | SQLite bot/message/routine state; retain relational modelling idea, not single shared connection as our team architecture |
| server/src/main.rs | 478 | HTTP/auth/routes/SSE/bootstrap; single installation authentication differs from tenant and actor authorization |
| server/src/runner.rs | 489 | Per-bot queue, context, peer messages, handoff and restart behavior; useful small harness example |
| server/src/screen.rs | 191 | On-demand Xvfb/Openbox/D-Bus screens, idle reclamation; explicitly not a security boundary |
| web/app.js | 569 | Thin chat client, reconnect resync, model settings, screen panel, routines; inspect UI behavior separately from captions |
| web/index.html | 115 | Workbench structure and screen iframe; some buttons have no implemented action |
| web/styles.css | 309 | Dark theme/layout/interaction styles; original Zincc design will use its own components/assets |

### Useful patterns

Normalize harness events once, keep bot identity distinct from an active process, serialize work per bot, persist messages before queueing, reclaim idle screens, expose structured handoff cards and resynchronize disconnected clients. Restart recovery deliberately reports interrupted runs instead of blindly repeating them; Zincc extends that principle with action reconciliation and durable checkpoints.

### Concrete findings relevant to Zincc

These are static observations/inferences at the pinned revision, not reproduced exploits or runtime failures:

1. **Approval boundary:** Claude/Codex adapters pass bypass flags, while the runner's instruction to ask before irreversible actions is prompt text. A prompt is not an enforceable action gateway. Keep Zincc policy outside model-controlled code.
2. **Shared privilege:** the runner supplies a shared bot API token and workspace; child processes inherit the environment, and screens run as one OS user. Per-bot directories/displays do not establish tenant separation. Use scoped service identity, secret brokering and isolated execution.
3. **Takeover:** web/app.js toggles an iframe overlay in setTakeover; that handler does not revoke agent input or acquire a server-side lease. Zincc needs enforceable ownership and a fencing generation, not just a clickable shield.
4. **Schedule crash window:** cron.rs updates last_run_at before a separate message insertion. A crash between those writes can lose that scheduled occurrence. Use a transaction/outbox and an occurrence idempotency key, plus explicit timezone/missed-run policy.
5. **Completion:** runner.rs can emit “Done.” when no replies/errors were observed. A successful subprocess exit is insufficient evidence that the requested external result exists. Require task-specific verification.
6. **Deletion:** the UI confirmation says memory is erased, but the inspected deletion path deletes DB state and screen processes without removing the bot's memory/browser directory. Zincc needs an explicit retention/deletion workflow and accurate UI wording.
7. **Handoff correlation:** send_message checks bot ownership when updating a choice, but the subsequent take_handoff lookup is by message id without passing the expected bot. In a future multi-actor design this needs strict actor/bot/run/action binding; the current product uses a different, shared installation trust model.
8. **Resource limits:** a run deadline exists, but stderr collection and JSON-line reading are not size-bounded in the adapter. Bound subprocess output, queues, peer depth and tenant concurrency in Zincc.

README labels approvals as forthcoming and describes some computer work as planned, while screen/handoff implementation already exists. Report specific inspected behavior rather than treating either marketing text or screenshots as a complete feature inventory. Its grok-bot-internals notes partly rely on a bot reporting its environment; those are not independently verified xAI architectural facts.

## Other repositories: inspected evidence

### Kiri

README and docs/features.md describe a Docker-compatible runtime; Windows uses WSL2. SECURITY.md recognizes daemon, container, VM and certificate-authority risks. Selected `crates/kiri-win/src/wslconfig.rs` lines 1–110 read/write CPU and memory settings; its comments correctly expose that restarting WSL affects more than one distro. Adopt explicit host-impact communication and capability checks. Do not treat vendor speed claims as benchmarks or enable privileged containers/public tunnels automatically. Root license header: Apache-2.0; exact redistributed files/dependencies still require notice review.

### Timon

Read `app/src/lib/approval-scopes.ts` and its tests, plus `app/src/lib/plugin-host/plugin-permissions.ts`. They constrain recognized scopes and bind grants to plugin identities; disposal checks prevent an old load from revoking a newer grant. Inspected the opening contract/version sections of `orchestrator/src/plugins/manifest.ts` (1–110), not its entire validator. Useful lesson: validate declarations before importing plugin code and make API compatibility explicit. Browser-side checks alone are not a hostile-plugin sandbox.

LICENSING.md identifies AGPL-3.0-only core, a plugin exception and MIT SDK/examples. Those are different reuse categories, not blanket permission to embed the entire core under any product license. Proposed disposition: independent implementation of ideas; SDK/example code only a future exact-path candidate.

### Rakazo

Inspected `apps/api/src/computer-control.ts` lines 1–150 and 351–451, function inventory, `computer-control.test.ts`, and `mcp-approval.ts` lines 1–150. Control acquisition uses conditional DB updates, holder identity, expiry, a lease id and an incremented fence; takeover scheduling failure has cleanup. The code explicitly allows an already-running action to finish while blocking subsequent actions. This is a useful contract for truthful takeover UX; Zincc must additionally qualify the actual driver enforcing it.

MCP decision code scopes lookups to the actor/space and updates cards with transaction-related helpers. These selected paths inform review cases; they do not prove every connector or permission route. Root license header: Apache-2.0. Possible later reuse: isolated TypeScript contract/UI/test patterns after complete component review. Retain Spring/PostgreSQL/Temporal ownership instead of importing its entire backend.

### OpenMausBot

Read `electron/local-computer-grant.mjs`, its node-test file, `electron/desktop-viewer-permissions.mjs`, and `apps/docs/content/docs/computers/local-computer.mdx`. Grants target the current server child and refresh when the driver changes. Viewer permission checks bind a limited capability list to the viewer origin and reject opaque origins. These are useful lifecycle and origin-validation ideas, not proof of OS-level isolation.

The inspected local-computer documentation describes macOS permissions and temporarily disabled Ubuntu host control. It does not establish native Windows Paint support. Windows desktop packaging and a Linux guest desktop are different from controlling Windows host apps. Validate every OS/driver path separately.

LICENSING.md specifies Apache-2.0 outside an enterprise carve-out; enterprise code uses separate source-available terms. No enterprise code is selected for copying. Proposed disposition: independent grant/driver lifecycle design and possible later OSS utility adaptation with notices.

## Changes to Zincc's proposal

- Make Windows Paint an explicit P13.2 acceptance journey: launch, draw through the app UI, save to a granted folder, inspect the saved artifact, and handle interruption.
- Require server-and-driver-enforced takeover ownership, expiry and fencing; show in-flight action status honestly.
- Model application capability per OS and desktop session. Container runtime, guest computer and host desktop are separate grants.
- Add plugin reload/revocation, scheduling crash-window, artifact-verification and deletion tests to existing phases.
- Keep Kiri optional. No new framework, database or Rust backend is required by these references.

**Direct product code reuse this turn: none. Ideas incorporated: all five references where described. Entire repositories ignored: none. Deliberately not adopted: shared privileged trust, permission bypass defaults, UI-only takeover, unsupported platform claims and unreviewed enterprise code.**
