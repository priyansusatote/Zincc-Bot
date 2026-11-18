# Agent-Reach: source review and proposed feature additions

Reviewed **18 November 2026**. [Panniantong/Agent-Reach](https://github.com/Panniantong/Agent-Reach), pinned revision [`c70ae6da5d39bc90e09ad2ae0575ccc89f31e284`](https://github.com/Panniantong/Agent-Reach/tree/c70ae6da5d39bc90e09ad2ae0575ccc89f31e284). The checkout has **124 tracked files**; its manifest identifies version **1.6.1**, Python >=3.10 and beta development status. The source was inspected outside Zincc. No installer, skill, browser session, upstream test or application was run.

**Verdict:** useful inspiration for research-source access, connector health and evidence quality. Consider a selectively qualified read/search adapter later. Do not replace Zincc's harness, connector gateway, RAG pipeline or browser broker with its installer/runtime. Recommendations remain an addendum while the user reviews the plan; no build approval is inferred.

## What it contributes

Agent-Reach exposes platform-specific read/search actions through a Python CLI, with a registry, backend selection, setup/session management and normalized JSON results. The inspected registry lists GitHub, Twitter/X, YouTube, Reddit, Facebook, Instagram, Bilibili, XiaoHongShu, LinkedIn, BOSS, V2EX, Xueqiu, Xiaoyuzhou and generic Web. Registered actions and availability vary: a listed platform is not a promise of every read or write operation.

The README classifies channels as default, optional or experimental. Selected source shows hosted Exa/Jina paths and external CLIs/browser tooling; transcription routes include Groq/OpenAI. Its broad zero-API-fee positioning must not become a Zincc pricing guarantee. Hosted services, paid transcription, compute and maintenance can still cost money, and upstream access may change. The repository is not an autonomous multi-agent orchestration system or a complete document retrieval system.

## Features worth adding or sharpening

| Proposal | What a Zincc user could do | Integration into our existing plan |
| --- | --- | --- |
| **Research across selected platforms** | Ask an agent to compare public discussions, repository activity and available video/podcast transcripts, then produce a cited brief or monitor changes. | Extend P2 research and P5 connectors; optional regional-source packs can later extend P15/P16. Qualify providers individually, not all channels at once. |
| **Visible evidence coverage** | See whether a result is a search snippet, public page, authenticated record, subtitle or generated transcript; know when full text/comments/freshness are unverified. | Extend source provenance in P2/P4 and artifact views. A weak fallback must not quietly look equivalent to a complete source. |
| **Connector health and repair guidance** | See “sign-in needed,” “allowance reached,” “service changed” or “temporarily unavailable,” with the appropriate next action. | Refine the existing connection-health panel and readiness checks. Health should be tracked per action/account/version; an installed tool is not proof of working access. |
| **Transcript-backed research** | Ask questions about accessible videos/podcasts and combine their evidence with reports or web pages. | P4/P10 ingestion; retain original source, language, extraction method and timestamps when actually available. Missing captions may offer a separately authorized transcription route. Never fabricate timestamps. |

These broaden the tools available to user-defined agents. They do not create a fixed list of allowed agent roles. Much of this extends existing scope; evidence coverage and actionable repair guidance are the strongest UX refinements.

## Source findings and Zincc adaptations

### Typed results and meaningful failure states

`agent_reach/runtime/result.py` defines a versioned result envelope with source/action/backend, normalized items, elapsed time, attempt history and typed errors. It distinguishes invalid input, missing setup/login, timeout, rate limit, not found and blocked access. `channels/base.py` declares action parameters and ordered backends rather than requiring the model to parse each tool's arbitrary output.

Zincc should adapt the contract idea, adding tenant/principal scope, source revision, retrieval time, stable evidence IDs, content limits and redaction. Do not blindly retain upstream `raw` payloads: they may contain irrelevant/private data or hostile instructions. Retrieved content remains evidence, not agent authority.

### Public results are explicitly weaker evidence

`runtime/public_web.py` checks source scope, rejects obvious login/challenge shells and labels public results as search results or page text with completeness unverified. Its text heuristics are not proof of completeness. Selected `test_public_first.py` assertions verify that a successful public lookup need not install software or open a browser.

Zincc should choose a suitable permitted route for the question. Public results are useful for general research; they cannot answer “what is new in my private inbox?” or establish that all comments/records were checked. Do not move confidential queries to hosted search as an automatic fallback. Privacy rules apply before routing.

### Bounded fallback, not retries around access failures

Selected `runtime/dispatch.py` sections validate parameters, apply time/cooldown budgets, record attempts and only allow certain transient failures to use a public fallback. Its terminal-error set prevents backend hopping after login requirements, rate limits, blocked access or not-found results. Inspected tests assert that these failures do not invoke the public fallback and that unsuccessful public content preserves the original site error.

For Zincc, scope cooldowns to the right provider/account/action and respect provider retry metadata. Keep cached connector failures separate from cached content. A web-search provider's rate limit must not be misreported as a destination site's login problem. Do not use fallback to evade an access restriction or turn an unavailable source into a fabricated “no results.”

### Session reuse needs explicit product controls

The README describes copying site login state into a dedicated browser. The inspected `runtime/session_reuse.py` path filters site cookies, updates the destination snapshot, attempts rollback on failure and records `verified: false` until access is separately checked.

Do not automatically adopt account-cookie copying as Zincc's default. Prefer supported OAuth or user sign-in to a dedicated profile. Any optional local session reuse needs explicit site/account selection and consent, device-local storage, revocation/deletion, no model exposure and no cross-user transfer. The original browser being unchanged does not remove the sensitivity of the copied session. Zincc's existing identity, credential broker and per-account browser boundaries remain authoritative.

### URL checks are one layer, not complete SSRF protection

`utils/url.py` rejects non-HTTP(S), userinfo, malformed ports, lookalike hosts and private/local literal addresses, including some alternate IPv4 forms. The inspected helper does not resolve arbitrary hostnames to establish their final network destination. Zincc must still enforce resolved-IP and redirect checks, DNS-rebinding defenses and network egress isolation at execution time; an input string validator alone is insufficient. This observation is a design qualification requirement, not a reproduced exploit finding.

### Dependencies and deployment

The tool registry includes browser/CLI installation and singleton tooling assumptions. Those must not become agent-controlled global installation in a hosted multi-tenant product. Use pinned, reviewed worker images, explicit tool allowlists, isolated account state, resource limits and separate qualification for Windows. Do not run the upstream install skill merely to gain a new source connector.

## Coverage and reuse ledger

| Category | Evidence / disposition |
| --- | --- |
| Complete small files read | Root LICENSE and pyproject.toml, SECURITY.md, channel registry, `runtime/result.py`, `runtime/public_web.py` |
| Selected source sections | Channel/action base contract; `utils/url.py`; dispatch validation, cooldown and fallback; session-cookie preparation; Web provider/error paths; YouTube URL/tool setup; tool registry and transcription provider/limit searches |
| Selected tests | Public-first no-install/no-browser assertions; transient fallback and terminal-error tests; URL/security test inventory |
| Not fully audited | Every adapter/backend, installer, browser bridge, all credential paths, complete subprocess/HTTP lifecycle, every dependency and all 124 files |
| Direct code/skills used | **None.** Downloaded for static inspection only. |
| Ideas recommended | Evidence coverage labels, typed recoverable errors, per-channel health and bounded fallback, optional broader source/transcript access |
| Possible future code use | Narrow MIT-covered utility/tests or isolated adapter after exact-file/dependency review and contract tests; original notices retained |
| Not adopted | Broad free/universal-access claims, automatic browser-session copying, global installs, raw upstream data as authority, or the CLI as a replacement for Zincc's platform |

Root license is MIT, with the copyright notice naming Agent Eyes. Dependency licenses and each service's access conditions require separate qualification. A license to reuse source does not establish permission or reliability for every external platform operation.

**Completion:** static research and proposals documented. No live platform access, runtime, security or performance verification is claimed. Main plan remains ready for user review; implementation still awaits explicit approval.
