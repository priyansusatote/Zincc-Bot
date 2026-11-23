# Bounding and checkpointing model work

P3.2 implemented a native plan, validate, act and observe loop. The worker increments a persistent step counter before requesting a model decision. The checkpoint includes the latest observation and its grant generation. It rechecks the run state and step number before publishing late results.

The six-step budget bounds retries after a worker crash. An identical repeated list observation stops a loop that makes no progress. Tests also cover provider/model failure, cancellation and exhausted budget. A model-only final response has its own label. Only an actual provider receipt establishes a verified write.

[Component and operating limits](../modules/phase-three-agents.md)
