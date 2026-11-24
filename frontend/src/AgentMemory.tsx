import { useEffect, useState } from "react";
import { client, check, type Session } from "./client";
import type { components } from "./generated/api";
type Note = components["schemas"]["MemoryNote"];
type View = components["schemas"]["AgentView"];
const base = "/api/workspaces/{workspaceId}/agents/memory" as const;
export function AgentMemory({
	workspace,
	session,
	runs,
	initial = "",
}: {
	workspace: string;
	session: Session;
	runs: View["runs"];
	initial?: string;
}) {
	const [query, setQuery] = useState(initial);
	const [search, submit] = useState(initial);
	const [revision, reload] = useState(0);
	const [notes, show] = useState<Note[]>([]);
	const [edit, select] = useState<Note>();
	const [error, fail] = useState("");
	const [busy, lock] = useState(false);
	// biome-ignore lint/correctness/useExhaustiveDependencies: reload after mutations
	useEffect(() => {
		const abort = new AbortController();
		show([]);
		client
			.GET(base, {
				params: { path: { workspaceId: workspace }, query: { q: search } },
				signal: abort.signal,
			})
			.then((r) => {
				check(r.response);
				if (!abort.signal.aborted) show(r.data ?? []);
			})
			.catch(() => {
				if (!abort.signal.aborted) fail("Couldn’t read private memory.");
			});
		return () => abort.abort();
	}, [workspace, search, revision]);
	const params = { path: { workspaceId: workspace } };
	const headers = { [session.csrfHeader]: session.csrfToken };
	async function change(fn: () => Promise<Response>) {
		if (busy) return;
		lock(true);
		fail("");
		try {
			check(await fn());
			select(undefined);
			submit("");
			setQuery("");
		} catch {
			fail("Change declined. Check the source, topic and latest revision.");
			select(undefined);
		} finally {
			reload((n) => n + 1);
			lock(false);
		}
	}
	const sources = runs.filter((r) => r.state === "completed");
	return (
		<section aria-label="Private agent memory">
			<h3>Private memory</h3>
			<form
				onSubmit={(e) => {
					e.preventDefault();
					submit(query);
					reload((n) => n + 1);
				}}
			>
				<label>
					Recall notes
					<input
						value={query}
						maxLength={2000}
						onChange={(e) => setQuery(e.target.value)}
					/>
				</label>
				<button type="submit">Search memory</button>
			</form>
			{notes.map((n) => (
				<article key={n.id}>
					<strong>{n.topic}</strong>
					<p>{n.fact}</p>
					<small>
						Source run {n.sourceId.slice(0, 8)} · {n.sourceGoal} · revision{" "}
						{n.revision}
					</small>{" "}
					<button type="button" disabled={busy} onClick={() => select(n)}>
						Correct note
					</button>
					<button
						type="button"
						disabled={busy}
						onClick={() =>
							void change(
								async () =>
									(
										await client.DELETE(`${base}/{id}`, {
											headers,
											params: {
												path: { ...params.path, id: n.id },
												query: { revision: n.revision },
											},
										})
									).response,
							)
						}
					>
						Delete note
					</button>
				</article>
			))}
			{!notes.length && <p>No matching notes.</p>}
			{(sources.length > 0 || edit) && (
				<form
					key={`${edit?.id ?? "new"}-${revision}`}
					onSubmit={(e) => {
						e.preventDefault();
						const form = new FormData(e.currentTarget);
						void change(
							async () =>
								(
									await client.POST(base, {
										params,
										headers,
										body: {
											id: edit?.id ?? crypto.randomUUID(),
											topic: edit?.topic ?? String(form.get("topic")),
											fact: String(form.get("fact")),
											sourceId: String(form.get("source")),
											expectedVersion: edit?.revision ?? 0,
										},
									})
								).response,
						);
					}}
				>
					<label>
						Memory topic
						<input
							name="topic"
							defaultValue={edit?.topic}
							readOnly={Boolean(edit)}
							required
							maxLength={80}
						/>
					</label>
					<label>
						Fact to remember
						<input
							name="fact"
							defaultValue={edit?.fact}
							required
							maxLength={600}
						/>
					</label>
					<label>
						Source run
						<select name="source" defaultValue={edit?.sourceId} required>
							{edit && !sources.some((r) => r.id === edit.sourceId) && (
								<option value={edit.sourceId}>{edit.sourceGoal}</option>
							)}
							{sources.map((r) => (
								<option key={r.id} value={r.id}>
									{r.goal} · {r.id.slice(0, 8)}
								</option>
							))}
						</select>
					</label>
					<button type="submit" disabled={busy}>
						{edit ? "Save correction" : "Remember fact"}
					</button>
					{edit && (
						<button type="button" onClick={() => select(undefined)}>
							Cancel correction
						</button>
					)}
				</form>
			)}
			{error && <p role="alert">{error}</p>}
		</section>
	);
}
