# P6.4 · Returning control to the user

**Implemented and evaluated in Edge.** The local worker creates a new browser and context, prepares a priority choice, and enables the form for human input. Automation waits. Returning control locks the choice before the worker reads the final DOM value, so the receipt records the user's actual edit. Closing the context discards session state.

A real browser test changes the selected priority twice in separate jobs, observes fresh defaults and empty cookies, and checks the recorded choice. A second test aborts a session during handoff and verifies its page closes. This implements a curated offline browser task. Live website accounts, arbitrary URLs and Windows application control have different permission requirements.
