import { useEffect, useRef, useState } from "react";
import { client, type Session } from "./client";
import type { components } from "./generated/api";

export function Research({
	workspace,
	session,
	refresh,
	source,
}: {
	workspace: string;
	session: Session;
	refresh: () => void;
	source: string;
}) {
	const [query, setQuery] = useState("");
	const [fresh, setFresh] = useState(false);
	const [result, setResult] =
		useState<components["schemas"]["ResearchAnswer"]>();
	const [message, setMessage] = useState("");
	const [busy, setBusy] = useState(false);
	const active = useRef<AbortController | null>(null);
	useEffect(() => () => active.current?.abort(), []);
	async function search() {
		if (active.current || !query.trim()) return;
		const controller = new AbortController();
		active.current = controller;
		setBusy(true);
		setMessage("");
		setResult(undefined);
		try {
			const { data, response } = await client.POST(
				"/api/workspaces/{workspaceId}/research",
				{
					params: { path: { workspaceId: workspace } },
					body: { query: query.trim(), fresh },
					headers: { [session.csrfHeader]: session.csrfToken },
					signal: controller.signal,
				},
			);
			if (controller.signal.aborted) return;
			if (response.status === 401 || response.status === 404) {
				refresh();
				return;
			}
			if (!response.ok || !data) {
				setMessage(
					response.status === 403
						? "Public research is unavailable in this session."
						: response.status === 429
							? "Research is busy. Try again shortly."
							: "Search is unavailable. Try again.",
				);
				return;
			}
			if (
				!Array.isArray(data.citations) ||
				![data.discovered, data.attempted, data.fetched].every(
					(n) => Number.isInteger(n) && n >= 0,
				) ||
				!data.citations.every(
					(e) =>
						typeof e.quote === "string" &&
						typeof e.title === "string" &&
						[e.finalUrl, e.requestedUrl].every(
							(url) => typeof url === "string" && url.startsWith("https://"),
						),
				)
			) {
				setMessage("Couldn’t read the search results.");
				return;
			}
			setResult(data);
		} catch {
			if (!controller.signal.aborted)
				setMessage("Couldn’t reach public research.");
		} finally {
			active.current = null;
			if (!controller.signal.aborted) setBusy(false);
		}
	}
	return (
		<details className="conversation">
			<summary>Public web research</summary>
			<form
				onSubmit={(e) => {
					e.preventDefault();
					void search();
				}}
			>
				<label>
					Search {source}
					<input
						maxLength={300}
						value={query}
						onChange={(e) => setQuery(e.target.value)}
						required
					/>
				</label>
				<small>Only these search terms are sent to {source}.</small>
				<label>
					<input
						type="checkbox"
						checked={fresh}
						onChange={(e) => setFresh(e.target.checked)}
					/>{" "}
					Fetch fresh pages
				</label>
				<button type="submit" disabled={busy || !query.trim()}>
					{busy ? "Searching…" : "Search sources"}
				</button>
			</form>
			{message && <p role="alert">{message}</p>}
			{result && (
				<section aria-label="Research results">
					<p role="status">
						Read {result.fetched} of {result.discovered} discovered pages.
						Attempted {result.attempted}.
					</p>
					{!result.citations.length && (
						<p>No supporting passage found in the pages read.</p>
					)}
					{result.citations.map((e) => (
						<figure key={e.finalUrl}>
							<blockquote>{e.quote}</blockquote>
							<figcaption>
								<a href={e.finalUrl} target="_blank" rel="noreferrer">
									{e.title}
								</a>{" "}
								· {e.cached ? "Cached" : "Fetched"} ·{" "}
								<time dateTime={e.observedAt}>
									{new Date(e.observedAt).toLocaleString()}
								</time>
								{e.requestedUrl !== e.finalUrl && (
									<>
										{" "}
										·{" "}
										<a href={e.requestedUrl} target="_blank" rel="noreferrer">
											Originally requested page
										</a>
									</>
								)}
							</figcaption>
						</figure>
					))}
				</section>
			)}
		</details>
	);
}
