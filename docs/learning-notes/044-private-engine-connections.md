# Selecting an engine before giving it a task

**Implemented with a fixture.** A conversation can pin the account and model for an engine run. The server freezes that choice at acceptance, records the engine acknowledgement in a separate checkpoint, then verifies the grant again before handing over the task. If an account is revoked and reconnected, the old run still carries the revoked generation. Database tests exercised that case and duplicate starts.

**Experimentally evaluated.** A provider CLI version establishes that a program is installed. A catalog check establishes what it offers. An explicit model acknowledgement, completion receipt and tool record answer different questions. The OpenCode free probe passed its local preflight; the single permitted inference attempt did not confirm completion. The fixture picker stays explicitly labeled. [Provider-by-provider evidence](../modules/phase-nine-builder.md#provider-qualification)
