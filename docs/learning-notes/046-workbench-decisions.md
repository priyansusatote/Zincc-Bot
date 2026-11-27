# A model choice is a run precondition

Imagine choosing your personal account and pressing **Run** just after another tab changes the agent default to your work account. The request now includes the connection ID, model and grant generation you saw. Spring reads the current selection under a database lock and compares the three fields before inserting the run receipt. A mismatch rolls back the start. A retry of an already accepted request still finds its original receipt. This combines **optimistic concurrency** for the user’s selection with **idempotency** for submission.

**Needs you** teaches a second lesson about query design. Fifty recent runs answer “What happened lately?” An approval survives until someone resolves it. Reading open decisions as well as recent history preserves that distinction without introducing another queue to synchronize. The same database policies scope both reads to the member.

For device jobs the runner translates local failures into a small set of reason codes. The server binds the receipt to the current lease. The UI can explain a changed folder or local pause without collecting file paths. A stale heartbeat alone cannot tell whether a person closed the process or paused it.

**Coverage.** Implemented and tested selection preconditions, member-scoped decision retrieval and device recovery presentation. PostgreSQL tests exercise a changed model, grant rotation, and an approval older than 51 completed runs. Browser fixtures exercise inbox navigation, a conflicting submission, and recovery guidance. Provider header parsing is tested with synthetic observations. Live ChatGPT allowance qualification remains open.
