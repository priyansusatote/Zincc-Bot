# Qualifying a free inference route

**Implemented — provider routing.** The Spring workbench can load the `numes-free` profile alongside its existing route list. Chat and agent activities use the same gateway. Each Numes request goes to a fixed provider origin using its own key. The offline experiment records both provider and model, so results from different providers cannot qualify each other's routes.

**Experimentally evaluated — availability and task accuracy.** A model catalog lookup checks credentials and prices. A signed-in chat then checks whether the gateway can obtain and persist a visible answer. A single agent additionally needs a valid next action. A team has to collect every required review before the owner can keep them. A single successful chat establishes only the first of these execution paths. [Measured outcomes](../phases/p7-evaluation.md#numes-free-models)

**Reading the comparison.** For a trial that returns HTTP 503, record a failed user task and an unavailable model-quality observation. For a completed trial, inspect the selected tool, reduction and final result together. Before promoting a cheaper strategy, compare total reported tokens across the same complete set of correct tasks.

**Verification.** The workbench Maven build passed with 102 tests passing and eight skipped. Three Python harness tests passed, including a credential and destination check for the Numes transport.
