# Deployment, capacity and operational contracts

Review date: **16 November 2026**. Proposed requirements, not measured service guarantees. Related: [architecture](../../ARCHITECTURE.md), [roadmap](../phases/roadmap.md).

## Supported deployment profiles

| Profile | Product state and execution | Qualification boundary |
| --- | --- | --- |
| Hosted personal/team | Hosted Spring, PostgreSQL, object store, Temporal and isolated workers | Same tenancy rules for personal and team workspaces; region and provider disclosure |
| Hybrid with customer runner | Hosted control plane, selected local tools/models | Instructions, metadata and permitted results cross the network; not fully local |
| Self-hosted online | Customer-operated control plane and storage; chosen online providers | Signed/pinned distribution, documented identity setup, backup, updates and connector app registration |
| Fully local/offline | All enabled services, model weights, OCR, embeddings, rendering assets and identity available locally | Preloaded dependencies and egress-denial test; no internet-dependent license renewal or hidden fallback in the qualified profile |

Offline is a supported capability subset, not a promise that Gmail or web search works disconnected. Feature availability derives from the deployment's actual capabilities. A native client alone does not provide a local backend. Local durable automation still needs persistent PostgreSQL/Temporal services and an awake host; do not introduce a second, less reliable scheduler just to hide this footprint. A future lightweight engine requires a separate ADR and the same recovery suite.

Do not promise minimum hardware until measured. Publish CPU/RAM/disk/GPU needs for each qualified profile, including model weights, context/KV memory, OCR, media, idle services and upgrade headroom. GPU rendering and local inference compete for resources; allocate them explicitly. Windows/WSL Linux guests, native Windows control and GPU passthrough are independently qualified capabilities.

## Capacity and admission control

An agent definition consumes metadata while idle. Distinguish registered agents, open streams, queued runs, waiting approvals, executing model calls and occupied sandboxes. “100 active runs” does not mean 100 simultaneous GPU sessions.

Initial load target: **100 admitted runs**, including durable waits, with separately configured execution limits. Before claiming capacity, publish workload mix, machine sizes, provider quotas, prompt/output lengths, sandbox occupancy, index size and burst duration. A planning example, not a benchmark: 100 tasks/hour with 120 seconds of active execution average about 3.3 concurrent executions; 100 simultaneous arrivals are a different burst. An eight-slot queue with each slot occupied for two minutes has an ideal ceiling of 240 tasks/hour before overhead. Queue delay and tail latency rise before that ceiling, so reserve headroom.

Enforce admission at workspace, deployment, provider and resource-pool levels. Bound queue depth, context size, tool output, open streams, active children, browser sessions, uploads and render duration. Separate interactive, scheduled, ingestion and media queues; add fair scheduling within each. Repeated provider throttling reduces dispatch, not a storm of retries. Every job has a deadline and an overload outcome visible to its user.

Scale API replicas for request/stream pressure and workers for queue age/resource pressure. Limit connection pools across all replicas to a database-wide budget. Isolate ingestion/query concurrency before adopting another database. Index generations can temporarily double storage. High-volume progress/token events are batched; persist business transitions and bounded checkpoints, not every token as a transactional row.

## Service objectives and release gates

| Objective | Proposed target or rule | Evidence required |
| --- | --- | --- |
| Request acceptance | p95 below 500 ms excluding external calls, under declared load | Load report including authentication and database latency |
| User acknowledgement | Visible queued/running state within one second on tested network | End-to-end trace; not model completion latency |
| Hosted control-plane availability | Initial production target 99.5% per calendar month for eligible API requests | Define probes, exclusions and failure budget before public beta; provider outages reported separately |
| Recovery | RPO ≤15 minutes, RTO ≤4 hours for declared hosted profile | Restore drill of product DB, artifacts, workflow persistence and keys |
| Task correctness | Workflow-specific threshold, plus no successful unauthorized writes in the release suite | Human-labeled and deterministic outcomes, repeated stochastic trials and uncertainty |
| Voice/media | Per-route measured latency and supported duration/resolution | Device/network/engine matrix; no universal one-second speech guarantee |

These are design targets, not an SLA. A finite zero-failure test suite does not prove zero security risk. Before a public cohort, run its isolation, recovery and cost tests; P12 later repeats and expands qualification. Do not wait until every marketplace/native feature exists to test backups or expose overload.

## Failure ownership

| Failure | Required response |
| --- | --- |
| PostgreSQL unavailable | Reject new authoritative writes; no tools execute on a stale permission guess |
| Temporal unavailable | An already-committed outbox can await dispatch within a bounded backlog; report queued state and stop admission when full |
| Provider call times out | Safe reads retry within one owner's budget; writes enter reconciliation/unknown state |
| Policy or credential broker unavailable | Fail closed for dispatch; display connection failure |
| Runner partition or sleeping laptop | Stop new dispatch; expire leases locally; reconcile before resumption; no automatic cloud migration |
| SSE cursor expired | Return a scoped snapshot plus new cursor; do not silently omit missing transitions |
| Object upload succeeds but DB commit fails | Keep temporary/quarantined object; idempotent finalization or expiry cleanup |
| Worker dies after generating output | Verify immutable object manifest and action receipts before resuming/publishing |

Health distinguishes liveness from readiness. Restarting a healthy process during an external-provider outage is not recovery. Use bounded circuit breaking, jitter, per-provider concurrency and manual quarantine. Operations must support inspecting stuck actions, stopping an automation and revoking a runner without an LLM.

## Privacy, history and disaster recovery

Temporal persists workflow/activity inputs and results. Keep raw private content in authorized encrypted storage and pass opaque references; avoid PII in workflow IDs, search attributes, headers, exception text and logs. Use supported payload codecs where needed and validate failure encoding as well as successful payloads. A codec does not encrypt every metadata field. See [Temporal encryption guidance](https://docs.temporal.io/production-deployment/data-encryption).

Define retention per data class: conversation content, action receipt, audit metadata, recordings, source documents, vectors, memory, checkpoints, rendered outputs, workflow history and backups. Deletion first denies live access, then removes derivatives asynchronously with an inspectable job. It cannot retract a document already downloaded or a message already sent. Explain backup expiry and any justified retained audit metadata; do not promise instant erasure of all historical copies. Key rotation and recovery must be tested without making undeleted tenants' data unreadable.

Restore into a quarantined environment with schedulers, webhook delivery and external writes disabled. Reconcile external receipts, grants, deletion tombstones, usage and workflow/product state before enabling dispatch. Restoring an older database must not resend last week's email or resurrect deleted private data. Preserve a recovery source for deletion/revocation records newer than the restored snapshot. Verify both backup integrity and actual restoration.

## Release, support and cost controls

Use per-cohort feature flags and immutable agent/tool/prompt/model versions. Roll back code with compatible schema/workflow history; a database restore is disaster recovery, not ordinary deployment rollback. Expand/contract migrations and staged worker versions precede destructive schema cleanup.

Record estimated, reserved and provider-reported usage separately. Reserve budget before a call or child run; settle idempotently by usage-event ID, reconcile late bills and expire abandoned reservations. Cancellation cannot guarantee that an already-dispatched paid request costs nothing. Keep a margin for delayed provider billing; label soft estimates and enforced dispatch ceilings separately. User-supplied keys still consume quotas and potentially money.

Maintain operator runbooks for stuck/unknown actions, compromised credentials, provider outage, lost runner, failed migration, restoration, deletion and runaway cost. Support access is least-privilege, time-limited and audited; never a default right to read all conversations. Public plugin publishing requires takedown/revocation and affected-run review as well as installation UX.

Early developer builds do not need billing subscriptions or a marketplace payment service. Hosted commercial plans, if chosen, require a separately scoped entitlements/billing task before charging customers; provider usage accounting is already core.
