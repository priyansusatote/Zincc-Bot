# Account grants and resumable sync

A Gmail sync reads a baseline history ID before enumerating inbox pages. Only the final page publishes the baseline. The next cycle asks Gmail which messages changed, then rechecks their current labels. Persisting a page without its checkpoint would allow a crash to skip or replay content incorrectly.

**Implemented and tested** — separate connection IDs for two accounts, encrypted credential storage, owner-scoped RLS and a restart between sample pages. The database test checks that partial content stays hidden. A scripted provider also expires a history cursor and removes a previously cited item during the replacement snapshot. The old receipt stays withdrawn after sync recovers. A Google user must complete provider consent to qualify real inbox access.

[Component guide](../modules/phase-five-integrations.md) · [Design decision](../architecture-decisions/0016-private-integrations.md)
