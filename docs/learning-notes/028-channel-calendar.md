# Channel scope, change hints and deletion

Slack identifies a channel and its team. A correctly signed webhook wakes the connector, while the authenticated history read determines what actually changed. Slack's cursor belongs to a frozen time window. Calendar instead provides a sync token after its last page and reports cancelled events.

**Implemented and tested** — signature tampering and expiry, pagination, rate limiting, cancellation, duplicate notifications and access withdrawal. A failed source produces an unavailable state. A useful exercise is to replay one signed notification twice and inspect the single deduplication record.

[Component guide](../modules/phase-five-integrations.md) · [Design decision](../architecture-decisions/0016-private-integrations.md)
