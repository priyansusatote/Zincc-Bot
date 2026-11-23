# Definitions and executions

P3.0 implemented immutable agent definitions and workspace-scoped delegation. Saving compares the expected revision with the current revision and appends a version under one transaction. A run references the version it started with. Editing instructions affects subsequent runs. Removing a grant or delegate also restricts existing runs at their next authorization check.

The delegation test exposed a PostgreSQL detail. A row lock can require update visibility even for a read. We serialize definition edits with an exclusive transaction-scoped advisory lock and let invokers share that lock. Row policies still decide which agent each caller can see. A delegate can run a version and cannot edit it or read the owner's private run.

[Component and operating limits](../modules/phase-three-agents.md)
