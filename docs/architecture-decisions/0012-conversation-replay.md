# ADR 0012 — Persist the turn and its replay cursor together

Adopted for P2.2.

**Problem.** A browser can disconnect after the server accepts a prompt but before the browser sees the reply. It must be able to recover the conversation without paying for a second generation. Two tabs can also submit different messages based on the same history.

**Decision.** A client supplies a random turn ID and its last observed conversation revision. In PostgreSQL a short transaction locks the private conversation row, recognizes an exact retry, and reserves a new turn only at the expected revision. The model call runs outside that transaction. Each accepted update commits the answer text, event and new revision together. Readers apply events in order or load the current snapshot when the cursor expires.

**Alternatives and consequences.** Replaying the provider request can produce a different answer and a second charge. In-memory fan-out loses replay data on restart. A broker and a separate projection worker would introduce a second consistency boundary. The PostgreSQL approach uses one database and serializes updates within each conversation. Polling adds read traffic and generation occupies a request thread. An abandoned generation becomes interrupted when the next authorized read or write observes its expired deadline. The server preserves the recorded prefix for the reader.

[API, limits and verification](../modules/conversations.md)
