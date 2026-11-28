# 0019. Keep connector results outside the planner and chat history

Adopted for the local workbench on 27 November 2026.

**Problem.** An ordinary language-model reply cannot establish what is in someone's inbox. The existing sync service already maintains a private projection per account. The user needs a way to select an account and get an observable result from chat.

**Decision.** Ask the selected model to classify a request for one account. Give it the request and provider type. Accept only `read_recent` or `unsupported`. The browser supplies the account ID and Spring resolves it under current workspace membership and row security. Recheck the grant and ten-minute freshness window after inference, then use the existing three-item read. Return sample status, account and sync time with the result. Keep retrieved items out of ordinary conversation persistence and provider prompts.

**Alternatives.** A direct “show latest” button would be faster and simpler for this single operation. A planner gives unsupported natural-language requests an explicit outcome and exercises the boundary needed for further operations. Exposing all accounts or provider APIs to the model would require a separate tool registry, scope enforcement and write reconciliation. Copying results into a saved thread would require a policy for withdrawal after revocation.

**Consequences.** A model outage can prevent a read even when sync data is healthy. A mistaken classification can select the permitted read, so the explicit account choice, bounded response and absence of write operations matter. A grant revoked after delivery cannot retract content already displayed. The server avoids holding a DB connection through the inference timeout and limits simultaneous planner calls per service instance. Browser and database coverage is recorded in the [component note](../learning-notes/048-chat-connected-apps.md).
