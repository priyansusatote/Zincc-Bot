# 0016 · Private connector feeds and read-only routines

**Adopted for the local P5 workbench, 23 November 2026.**

A daily briefing needs several differently authorized accounts. Timeouts, repeated provider events and package updates must preserve the user's choice of sources and permissions.

Each feed belongs to a principal and workspace. PostgreSQL row-level security enforces that boundary and checks current membership. OAuth state stays in the initiating browser session. AES-GCM binds each persisted credential to its workspace, owner and connection ID. Pages and cursors commit together. A partial snapshot stays unavailable. A signed webhook is a hint to fetch authoritative provider state.

Each routine pins an installed skill digest and explicit resources. Permission changes require a new install decision. An incompatible update pauses old routines. Reinstalling the previous revision and resuming restores its behavior. The curated briefing tool runs through a fixed MCP subprocess after a schema check. A deterministic formatter handles private data without inference or a provider token.

Daily and source-change triggers share a durable PostgreSQL job table. A unique cause deduplicates deliveries, and a row lock serializes runs of the same routine. These jobs perform read-only calculations. P3 continues to use Temporal and an action ledger for multi-step agents and external writes. Putting the scheduler in process memory would lose missed slots and duplicate history. A second Temporal workflow for each bounded read would add another state handoff to these jobs. Longer-running or write-capable routines need additional dispatch and policy design.

The trade-off is bounded throughput and transaction time during external reads. Local Docker supplies isolation for the one curated worker. External customer executables remain gated by P6. [Operations and qualification](../modules/phase-five-integrations.md)
