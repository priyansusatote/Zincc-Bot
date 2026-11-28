# How a chat request gets real account data

**Implemented and locally evaluated.** The model selects a tool, Spring enforces the account grant, and the browser renders the result. The learning topics are tool selection, transaction boundaries, data minimization and integration testing.

1. The browser lists accounts visible to the signed-in member. The user chooses one and a planner. The backend checks the selected feed's owner, workspace, sync state and freshness in one transaction.
2. After that transaction ends, the model receives the request and provider type. The gateway accepts a validated object or an exact action name, depending on the model's capabilities. An invalid response ends the request.
3. A second transaction rechecks the grant. If it still permits the read, Spring returns up to three items from the chosen feed, plus source and sync time. The items never enter a subsequent model prompt or the saved conversation.

**Why check twice?** A user can revoke the grant while the provider is thinking. The first check prevents an unauthorized request from using the model. The second prevents release of data under an expired grant. Holding a database connection throughout model inference would tie the connection pool to provider latency.

**Verification.** The PostgreSQL test uses two members, a stale feed, malformed model output and revocation during inference. The browser test exercises account selection, the read result and removal of a prior result after a failed retry. A sample validates the workflow without disclosing a personal subject or event. [Backend test](../../src/test/java/io/zincc/integrations/ChatSourceApiTest.java) · [Live sample qualification](../../frontend/tools/qualify-chat-app.mjs)

**Trade-off.** A single read operation would also work as a plain button. The planner adds latency and a failure mode. Measure useful classifications and unsupported requests before exposing more operations. The next engineering challenge is applying the same grant across chat, an agent run and a scheduled briefing.
