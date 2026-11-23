# Using agents in the workbench

Open **Agents and runs** in a signed-in workspace. Select **Connect task list** to activate the operator-provisioned grant for your identity. Choose a name, instructions, a configured model and the read/write permissions. Saving creates version 1. Editing an existing agent adds a version. An optional delegate must be an existing member of this workspace. Their runs use their own identity, connection and private history.

**List tasks** reads up to twelve task titles. **Create a task with approval** treats your goal as the exact title. **Let the agent choose** asks the pinned model to propose a typed list, create or finish step. An invalid model response fails the run. A proposal displays its destination and exact title. Approving sends the write. A verified write shows the returned provider task ID. Cancellation is available until dispatch. An unknown outcome offers a read-only receipt check. The UI shows the most recent 50 personal runs.

## Runtime

Apply Flyway V6 before adding the `agents` Spring profile to the OIDC workbench. The service must connect to a loopback Temporal endpoint at `zincc.temporal.target`, default `127.0.0.1:17233`. The qualification used Temporal Java SDK 1.41.1 and a single local Temporal CLI server 1.10.1 with a persistent SQLite volume. The CLI dev server is intended for this workstation. Production TLS, multi-replica credential coordination, retention, queue capacity and history upgrades need separate qualification. [Dependency tree](../phases/p3-dependencies.txt) · [Temporal Java release](https://github.com/temporalio/sdk-java/releases/tag/v1.41.1)

PostgreSQL stores agent versions, run checkpoints, outbox entries, proposals, approvals and receipts. Temporal history holds routing IDs and coarse states. A restart can repeat a model call or a read that had not checkpointed. The run limits planning attempts to six and waits for human approval for up to 24 hours while the worker is available. An outage delays enforcement until recovery. Once a write is claimed, recovery only searches for its marker. An inconclusive result remains unknown. A clock tick recovers an approval notification missed while Temporal was unavailable.

## Connector scope

The initial connector uses the Google Tasks v1 list and insert wire format. The **local task sandbox** is an independent, loopback-only protocol fixture. It exchanges a configured refresh credential for short-lived bearer tokens and persists fixture tasks outside the product database. Tasks created there are visible on subsequent reads. [Fixture source](../../tools/tasks-sandbox.mjs) · [Google Tasks list](https://developers.google.com/workspace/tasks/reference/rest/v1/tasks/list) · [Google Tasks insert](https://developers.google.com/workspace/tasks/reference/rest/v1/tasks/insert)

For a previously consented Google account, an operator can configure one principal/workspace/list binding per deployment using `ZINCC_TASKS_ACTOR`, `ZINCC_TASKS_WORKSPACE`, `ZINCC_TASKS_LIST`, `ZINCC_TASKS_CLIENT_ID`, `ZINCC_TASKS_CLIENT_SECRET`, and `ZINCC_TASKS_REFRESH_TOKEN`. The read/write grant requires the Google Tasks scope `https://www.googleapis.com/auth/tasks`. Real OAuth consent onboarding and multi-account registration belong to P5. The Java adapter owns token refresh. Credentials stay in the process environment. The database stores a resource and symbolic reference. Revoke in Zincc before rotating or replacing an account grant, then reconnect to establish a new generation. To revoke upstream consent, use the provider's account controls. No real Google account was connected during P3 qualification.

For the sandbox set `ZINCC_AGENT_SANDBOX=true`. Run the fixture with Node, the same list/client/refresh settings and `ZINCC_TASKS_FIXTURE_DIR` pointing at a private directory. It listens at 127.0.0.1:18462. The service rejects the Google connector in local-only deployments. A create receipt requires both the approved title and unique run marker. Reconciliation checks the first 100 visible tasks. Missing, conflicting or edited evidence leaves an unknown outcome for human review.

[Learning journal](../learning-notes/016-agent-versions.md) · [Durability decision](../architecture-decisions/0002-durable-execution.md)

## Repeat the recovery checks

Run the workbench-profile Maven verification with PostgreSQL fixture support. To include the real Temporal regression, start the loopback dev server on port 17233 and set `ZINCC_TEMPORAL_TEST=true`. The test uses a unique queue and workflow, waits for a recorded timer, replaces the worker, wakes the workflow and checks duplicate-start rejection. A separate application drill stopped the dedicated Temporal container, submitted a repeated request, terminated the Java worker and restarted both. The queued read and prior approval wait survived. Approving the recovered proposal produced one task. Redelivering its outbox entry after completion preserved that receipt. [Temporal test](../../src/test/java/io/zincc/agents/TemporalRecoveryTest.java) · [Connector protocol test](../../src/test/java/io/zincc/agents/TasksConnectorTest.java)

The local stack used a dedicated Docker volume owned by UID/GID 1000 for Temporal's SQLite file. For example, with the image from the [version baseline](../technology-baseline.md), initialize a fresh volume and then run the service:

```powershell
docker volume create zincc-p3-temporal-data
docker run --rm --user 0:0 --entrypoint chown -v zincc-p3-temporal-data:/data temporalio/temporal:1.10.1 1000:1000 /data
docker run -d --name zincc-p3-temporal -p 127.0.0.1:17233:7233 -v zincc-p3-temporal-data:/data temporalio/temporal:1.10.1 server start-dev --ip 0.0.0.0 --db-filename /data/zincc.db --headless
```
