# Architecture follow-up: source evidence and adoption limits

Date: **16 November 2026**. Static inspection only. No upstream application, test suite, installer or skill was executed; no upstream code was copied into Zincc. Public review checkouts are outside the product directory. This supplements [the reference ledger](references.md) and [the earlier five-repository review](2026-11-16-agent-repositories.md).

## Coverage and findings

The existing plan, architecture, module specifications, roadmap, ADRs, learning coverage, connector catalogue, UI note, status and research records were reviewed for consistency and missing boundaries. Source inspection was concentrated on decisions that could change architecture; it was not a new full audit of every repository.

| Reference and revision | Additional files/sections inspected | Finding and Zincc consequence |
| --- | --- | --- |
| [OpenBot](https://github.com/CopilotKit/OpenBot/tree/a28e96ccd87378c3376ccd977b59341f931747c9), `a28e96ccd87378c3376ccd977b59341f931747c9` | Checkout tree; README architecture/licensing; LICENSE; backend-images.env; docs/architecture.md policy/computers; docs/approvals.md first 85 lines; api/openapi/approvals.yaml first 75 lines; complete agent-pydantic-ai/src/main.py | Public checkout contains template/client/adapters/contracts; expected server implementation directory is absent and deployment uses published backend images. Intelligence is separately licensed, including local license renewal. Do not describe this as a wholly auditable MIT backend or assume air-gapped operation. Retain UX/event/adapter ideas; no backend fork selected |
| [Timon](https://github.com/ai-timon/timon/tree/f00ffa23b7a7d829cfcb6b81940aa9a9e4c426ef), same pinned snapshot | orchestrator/src/plugins/manifest.ts lines 977–1091; loader.ts lines 811–898, in addition to earlier permission source/tests | Manifest validation is useful. Loader imports a module before checking its exported injection requirements; top-level import code is already executable. This is an architectural observation, not a demonstrated exploit. Zincc validates declarative manifests before code loading and loads third-party code only inside an isolated worker |
| [Rakazo](https://github.com/elie222/rakazo/tree/572755b4179c88f0303a3026023331b1c3ed5f87), same snapshot | packages/adapters/src/computer-lifecycle.ts lines 896–1050, plus earlier API control path/tests | Owned-run row lock and lease fence guard acquisition/renewal; release preserves an expired record so the fence does not reset. Adopt monotonic generation/tombstone invariant, with final executor enforcement. Full driver path has not been audited; DB fencing alone is not proof of driver enforcement |
| [OpenMausBot](https://github.com/milind-soni/OpenMausBot/tree/9f04922546a245d90cb6d7f870172eb8cc8f4973), repeated user link consolidated | Complete server/local-computer-proxy.ts; local-computer.ts first 180 lines; mcp-bridge.ts lines 131–210 and call-site search, plus earlier Electron grants/permission files/tests | Proxy validates loopback destination and strips control descriptor variables before spawning child. Gate serializes calls, fails closed on failed checks and rejects old queued input. Use per-call control checks and expiry tests. Broader inherited child environment still needs an explicit allowlist in Zincc. Descriptor support for win32 is not a verified Paint driver |
| [Kiri](https://github.com/libracoder/kiri/tree/260cf66b9b56963a0170857c2b4306c38814e108), same snapshot | crates/kiri-init/src/linux/container.rs first 145 lines, in addition to earlier WSL config/docs | Native namespace/cgroup setup and Docker-style capability defaults are container runtime details, not proof of hostile-tenant isolation or host Windows control. Keep optional local backend qualification; do not inherit privileged options/default capabilities |
| [AG-UI](https://github.com/ag-ui-protocol/ag-ui/tree/2330368a2a74573902858867072406638ba3073d), `2330368a2a74573902858867072406638ba3073d` | LICENSE; core/package.json; selected generated/types.ts BaseEvent and RunFinishedEvent; client/apply/default.ts state patch and completion branches; first 135 lines of default.state.test.ts; official events/tools docs | MIT source; checkout core package declares 1.0.4/protocol 1.0, not a confirmed release pin. Base events do not supply Zincc's durable cursor. Completion may default to protocol success; state patches affect client state. Add adapter contract, safe field mapping, snapshot recovery and separate action verification |

AG-UI checkout was partial: Windows path-length errors prevented some long .NET baseline filenames from being materialized. The listed TypeScript files were available and inspected. No .NET SDK audit is claimed; this does not block evaluating the chosen TypeScript boundary. Do not report all 6,051 tracked paths as read.

OpenBot's old `server/src/approvals/policy.ts` 404 is now explained by the inspected checkout layout. Policy behavior described in docs remains documentary evidence, not inspected backend enforcement. Its documented proxy alone does not stop raw socket egress; Zincc requires host/network enforcement independent of proxy environment variables. Root MIT license does not determine terms for separate services or images.

## Rechecked architecture references

- [CubeSandbox quickstart](https://raw.githubusercontent.com/TencentCloud/CubeSandbox/master/docs/guide/quickstart.md): native KVM or its PVM host path needs host-specific qualification. Current guide requires XFS-backed storage and documents a custom-kernel/root setup for PVM, plus MySQL/Redis in that distribution. These are real operational dependencies; adopting it is not simply adding a library to Spring. No installation or kernel change performed.
- [Substrate security](https://raw.githubusercontent.com/agent-substrate/substrate/main/docs/security/security.md): refine the earlier broad “storage gaps” description. It reports segregation implemented, while per-actor encryption/restore authorization and other controls have gaps; reset, quotas and elevated worker capabilities remain qualification concerns. No isolation test was executed.
- [Harness2 README](https://raw.githubusercontent.com/google-research/harness2/main/README.md): fixed-weight harness improvement is a useful offline experiment. Its documented unrestricted executor network and disabled sandbox are unsuitable production defaults. Retain bounded offline promotion, never permission-policy self-modification.
- [MCP security guidance](https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices): add audience-bound credentials, no token passthrough, strict OAuth destination handling and server identity/session checks. Protocol interoperability does not replace authorization.
- [Temporal payload encryption](https://docs.temporal.io/production-deployment/data-encryption): history contains inputs/results; codec coverage and failure serialization need explicit testing. Add retention-safe payload references and quarantined restore.

## What was not newly audited

Superbot's prior complete 16-source/build-file first pass remains valid at its recorded snapshot. PilotDeck, MiniMax skills, Hygen, TencentDB memory, OpenClaw, elephant-goldfish-model, awesome-llm-apps, ADK recipes, Chrome DevTools MCP, Cloudflare audit skill, claude-mem, Hyperframes, VoiceStudio and the ocean demo retain the exact review depth recorded in the main ledger. Their implementation claims are not upgraded by this review. Catalogue/examples/methodology are sufficient for planning their optional roles; full candidate-component and transitive-dependency inspection remains a prerequisite to actual reuse.

No reference is silently ignored. Concept-only or deferred items have a reason in the ledger. OpenBot and OpenMausBot duplicates are consolidated. Proprietary products remain public-behavior references, not source audits. Unavailable backend source is explicitly unavailable, rather than “reviewed.”

## Reuse accounting after this review

| Category | Disposition |
| --- | --- |
| Direct copied source or installed upstream skills | **None** |
| Potential dependency | AG-UI SDK adapter; parser/media/sandbox/framework candidates already listed; qualification required |
| Source-analyzed design inspiration | Superbot queue/adapters; Timon manifests; Rakazo leases; OpenMausBot call gate; OpenBot adapter/UI; Kiri placement limits |
| Documentation/methodology inspiration | Remaining references at their stated ledger depth; use cases and test ideas |
| Not selected | Whole OpenBot backend/Intelligence dependency, Timon in-process third-party loader, reference demo permission bypasses, floating backend images, unbounded research executors |

Selection means a design proposal, not adoption. At implementation, pin the actual release, inspect all code in the selected execution path and applicable licenses, review dependencies, run conformance/security/recovery tests, and record local destinations/notices before copying anything.
