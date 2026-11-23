# Skill permissions across versions

Briefing v1 reads Gmail. Version 2 also understands Slack and Calendar. Each manifest has a pinned digest, and the install request must name exactly the permissions it grants. An individual routine selects a narrower set of accounts. Changing a manifest cannot silently expand an existing routine.

**Implemented and tested** — startup integrity checks, explicit install, broadened permission rejection, rollback and resume of a compatible pinned routine. Catalog entries are bundled declarative assets. Uninstall removes the grant and pauses its routines.

[Component guide](../modules/phase-five-integrations.md) · [Design decision](../architecture-decisions/0016-private-integrations.md)
