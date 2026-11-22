# From a search result to a cited passage

**Implemented** bounded retrieval, extractive citations, freshness and partial coverage. A member submits a separate public query. The backend searches English Wikipedia, reads up to three article introductions and selects a verbatim sentence by query-term overlap. Each result links the requested title to the resolved article. Search results can change between runs. A five-minute cache stores only public pages and retains their observation time.

**Experimentally evaluated** with a loopback HTTP source that returns an alias and two temporarily unavailable pages. One of four discoveries provides evidence. A repeat request returns the original observation time, and a forced refresh fetches again. The live “plate tectonics” probe discovered six pages, read three and cited the Plate tectonics article.

An exact quote proves what a source said. A lexical match alone cannot establish that it answers the entire question. The interface presents passages with their source and explicitly reports when none qualify. Paraphrasing, cross-source synthesis and private document retrieval require separate evaluation.
