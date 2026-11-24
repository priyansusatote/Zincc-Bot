# P7 tool-discovery evaluation

24 November 2026. The fixed objectives were first inbox subject, total calendar events and sum of report amounts. Each strategy received the same synthetic records and each objective with catalog sizes 3 and 32. There were six planned trials per route and strategy. Each page contained at most two rows. A program could request up to three pages. No retries or write tools were offered.

Direct describes all available schemas. Lookup asks the planner for a search term and returns matches. Both ordinary-call variants expose the retrieved rows to the final model call. Program interprets a validated tool/reduction pair and returns only the scalar. Pagination is bounded by the harness in all three variants. This experiment does not evaluate arbitrary generated JavaScript.

| Route | Strategy | Correct / attempted | Model or fixture calls | Prompt characters | Tool result characters | Reported input / output tokens | Median ms per attempted trial |
| --- | --- | --- | --- | --- | --- | --- | --- |
| fixture | direct | 6 / 6 | 12 | 13,378 | 168 | unavailable | 0.0 |
| fixture | lookup | 6 / 6 | 18 | 2,686 | 168 | unavailable | 0.0 |
| fixture | program | 6 / 6 | 12 | 2,042 | 46 | unavailable | 0.0 |
| nvidia/nemotron-3.5-lightning:free | direct | 1 / 6 | 12 | 13,378 | 168 | 3459 / 148 | 1820.5 |
| nvidia/nemotron-3.5-lightning:free | lookup | 0 / 6 | 11 | 1,265 | 0 | 464 / 446 | 1836.0 |
| nvidia/nemotron-3.5-lightning:free | program | 0 / 6 | 11 | 1,268 | 0 | 464 / 451 | 1593.0 |
| google/gemma-4-26b-a4b-it:free | direct | 0 / 1 | 1 | 442 | 0 | unavailable | 719.0 |
| nvidia/nemotron-3.5-super-vl:free | direct | 2 / 4 | 8 | 8,938 | 142 | 2300 / 103 | 3851.5 |
| nvidia/nemotron-3.5-super-vl:free | lookup | 0 / 4 | 7 | 912 | 45 | 278 / 54 | 1562.5 |
| nvidia/nemotron-3.5-super-vl:free | program | 1 / 3 | 6 | 782 | 16 | 281 / 81 | 4328.0 |

The fixture is a scripted reference planner with known correct actions. Its character savings establish the mechanics of schema selection and reductions. Real models must find a relevant tool and produce a valid answer. Some trials ended at discovery or plan validation before a result was returned. Low token totals for a failed strategy can therefore reflect early termination. Timing includes provider latency and failures. Counts are reported usage from received responses. Charges and inference cache hits were not measured.

**Decision.** Lightning completed its cohort, with one correct answer in eighteen trials. Gemma encountered HTTP 429 on the first attempt. Super VL completed eleven attempts before HTTP 429 stopped its cohort. Treat the latter two comparisons as incomplete. No tested model candidate qualified for promotion. The fixture could promote the program, run a subsequent calendar trial using it and roll back to direct. Production serving retains its original tool interface.

**Provenance.** Live reports used evaluator digests 8f2943237fc5, f8acd965e2b9. The completed release fixture used f5915f504271. The fixture catalog was made explicitly grant-filtered and malformed planner output handling tightened during qualification. Live results describe their executed revisions.

[Experiment implementation](../../tools/harness_eval.py) · [Operating the workbench](../modules/phase-seven-agents.md)

## Numes free models

On 24 November the account catalog returned nine free model IDs with zero listed rates. An arithmetic smoke test returned the expected JSON on Z.ai GLM 5.3 Flash, DealignAI GLM 5.3 Flash FP8 and NVIDIA Nemotron 3 Ultra. Five other routes returned HTTP 503 and DealignAI GLM 5.3 FP8 timed out. DeepSeek V4.1 Flash also returned 503 on the report-sum task. These are observations of the free endpoints at test time. [Numes catalog semantics](https://numes.ai/docs/models)

The workbench exercised synthetic inputs through a signed-in session and the existing Temporal workers. GLM Flash completed chat, failed the single-agent run and failed the team when a reviewer received a provider error. Ultra completed chat and returned the required structured finish action. Both Ultra reviewers completed. After selecting both reviews, a fresh session verified both original strings in the persisted parent receipt. One matched its requested text exactly and the other omitted the final period.

The same frozen tool tasks, budgets, validators and catalogs used for OpenRouter ran against both models. Counts below include every planned trial at evaluator revision `4c714ca24991`. An earlier GLM availability probe stopped at a 503 in its first trial and is excluded from the table.

| Numes model | Direct correct / 6 | Lookup correct / 6 | Program correct / 6 | Trials ending in HTTP 503 | Other transport or invalid results | Validated but incorrect |
| --- | --- | --- | --- | --- | --- | --- |
| `z-ai/glm-5.3-flash:free` | 1 | 0 | 0 | 7 | 10 | 0 |
| `nvidia/nemotron-3-ultra-550b-a55b:free` | 3 | 0 | 1 | 10 | 3 | 1 |

Neither route met the promotion gate. The existing generic error category combines transport and output-validation failures, so those trials cannot support a model-only accuracy estimate. Only the two routes in the table received the full comparison. The team and benchmark used synthetic data. No separate billing audit was performed.
