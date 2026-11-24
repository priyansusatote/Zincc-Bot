# P7.2 · Cache a projection and recheck authority

**Implemented and measured.** A private recall normally joins notes to source runs. For sixteen simultaneous recalls on a cold key, the integration test counted one full projection load. The other fifteen readers reused it. Every request still checked membership and read the current generation. This is a reduction in source loads. Model tokens and real-user latency were outside that measurement.

A database trigger advances the generation in the transaction that changes a note. A fresh read after commit sees the new generation. Bounded striped locks coalesce loads on one service process. The cache holds at most 64 owners' projections, with a two-minute reuse window, periodic cleanup, and immediate local eviction on edits. Removing membership denied a warm-cache request. Deleting a source run invalidated its note through the same trigger.

For inference KV caching, the object being reused would be attention state inside a model server. This component reuses an application query result.
