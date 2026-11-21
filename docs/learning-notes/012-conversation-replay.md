# P2.2 — Recovering a conversation after a lost response

**Implemented and integration-tested:** idempotent commands, optimistic concurrency, transaction-scoped authorization, ordered replay and snapshot recovery.

Suppose two tabs both see revision 8. Tab A sends a message. PostgreSQL locks the conversation, saves the turn ID and records event 9 in the same transaction. If A loses its connection, it can repeat the exact command. Zincc recognizes the saved ID before checking the now-stale revision. If tab B sends a different message against revision 8, it gets a conflict and must reload. The model call happens after commit, so a slow response does not hold the row lock or database connection.

The turn ID answers “Have I already accepted this request?” The revision answers “Which history did this writer see?” The event cursor answers “Which changes has this reader already applied?” The revision and cursor share one sequence. A reader that falls behind the 128-event replay window replaces its view with the persisted snapshot. A crash during generation leaves a readable partial answer and an interruption once the deadline expires.

**Verification.** Concurrent PostgreSQL requests exercised duplicate and competing sends. The authenticated HTTP test used a local OIDC issuer and model fixture to check CSRF, history, normal completion, interruption and revocation. Edge tests included duplicate events, an expired cursor and an uncertain POST retried with the original turn ID.

**Exercise.** Reverse the duplicate-ID and expected-revision checks in a local experiment. Predict what a retry of an accepted turn will return, then run the concurrent retry test.

[Store and transaction boundaries](../../src/main/java/io/zincc/conversation/ConversationStore.java) · [Browser replay](../../frontend/src/conversation-client.ts) · [Operating limits](../modules/conversations.md)
