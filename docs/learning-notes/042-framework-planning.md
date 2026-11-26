# One planning turn across three SDKs

**Implemented and tested.** Google ADK, LangGraph and PydanticAI each run a planning turn in the network-disabled worker. The worker signals that it needs inference. Spring sends the original authorized prompt to the selected model and returns only its text. The worker emits a typed proposal. Spring rechecks the grant and applies the same approval rules used by the native planner.

The checkpoint lives in the Spring run and action tables. This matters when a worker exits after proposing a write. Each real SDK reached a durable approval wait with a controlled model response. Replacing the worker and cancelling the run consumed no second model call. The graph has one planning node per turn. Resuming arbitrary multi-node customer graphs would require a richer checkpoint contract.

AG-UI replay is a different interface. A saved completion may produce final text, text-end and run-finished frames. Separate cursors allow reconnecting between them. [Implementation and limits](../modules/phase-nine-builder.md)
