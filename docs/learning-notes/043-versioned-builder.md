# Restoring an agent while older runs exist

**Implemented and tested.** Suppose v1 used ADK and v2 used LangGraph. Restoring v1 creates v3 from the prior settings, preserving the history and the version referenced by every accepted run. The editor shows differences in instructions, model, framework, tools and delegate before the owner restores a version.

The save includes the current expected version. If another edit wins the database lock first, the stale restore receives a conflict. Reusing the normal save path also revalidates the route and delegate. The signed-in browser restored v1 as v3. The database test confirmed an earlier run still pointed to v1. [Builder guide](../modules/phase-nine-builder.md)
