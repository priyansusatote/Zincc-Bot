# Public web research

Open **Public web research** in an authenticated workspace. Search English Wikipedia with up to 300 characters. The request sends only the entered terms. Results include verbatim passages, requested and final article URLs, observation time, cache status and coverage. Only the research query leaves through this adapter. The account's chat history and credentials stay out of that request. Search results are transient.

The POST endpoint at `/api/workspaces/{workspaceId}/research` requires a session, current membership and CSRF token. The input fields are `query` and `fresh`. Local-only deployments suppress the UI and reject calls before search. Set `zincc.research.enabled=false` to disable it independently.

The adapter discovers at most six titles and reads the first three introductions. Every call goes to the fixed HTTPS MediaWiki endpoint, accepts at most 128 KiB and has a five-second deadline. HTTP redirects are refused. Wiki title redirects resolve inside that API. Four searches can run concurrently per process. The bounded public-page cache holds up to 128 titles for five minutes. Search errors return 502; page errors reduce coverage. “Fetch fresh pages” re-reads selected introductions. The source's observation time describes retrieval rather than original publication.

Run `WebResearchTest` for offline protocol tests. Set `ZINCC_WIKI_SMOKE=true` to include a real public lookup. This initial corpus covers English Wikipedia. Broader providers can implement the source contract. [Search API](https://www.mediawiki.org/wiki/API:Search) · [TextExtracts](https://www.mediawiki.org/wiki/Extension:TextExtracts) · [Learning report](../learning-notes/014-cited-search.md)
