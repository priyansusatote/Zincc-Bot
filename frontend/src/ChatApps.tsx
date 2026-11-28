import { useEffect, useRef, useState } from "react";
import { client, check, type Session } from "./client";
import type { components } from "./generated/api";

const base = "/api/workspaces/{workspaceId}/integrations" as const;
const names: Record<string, string> = {
	gmail: "Gmail",
	slack: "Slack",
	calendar: "Google Calendar",
};
const failures: Record<number, string> = {
	400: "Check the selected model and use a request of 2,000 characters or fewer.",
	401: "Your session expired. Sign in again.",
	403: "You no longer have permission to read this account.",
	404: "This account or workspace is no longer accessible.",
	409: "Refresh the account in Connections and routines, then try again.",
	429: "The model or planner is busy. Choose another model or retry later.",
	502: "The model could not select a supported operation. Try another model.",
	504: "The model timed out. Choose another model or try again.",
};
type Props = {
	workspace: string;
	session: Session;
	question: string;
	setQuestion: (s: string) => void;
	initialRoute: string;
	close: () => void;
	connect: () => void;
};

export function ChatApps({
	workspace,
	session,
	question,
	setQuestion,
	initialRoute,
	close,
	connect,
}: Props) {
	const [catalog, setCatalog] =
		useState<components["schemas"]["ChatSourceDirectory"]>();
	const [feed, setFeed] = useState("");
	const [route, setRoute] = useState(initialRoute);
	const [result, setResult] = useState<components["schemas"]["ChatRead"]>();
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	const [revision, reload] = useState(0);
	const pending = useRef<AbortController | null>(null);
	// biome-ignore lint/correctness/useExhaustiveDependencies: reload rechecks current grants.
	useEffect(() => {
		const ctl = new AbortController();
		setCatalog(undefined);
		setResult(undefined);
		setError("");
		client
			.GET(`${base}/chat-sources`, {
				params: { path: { workspaceId: workspace } },
				signal: ctl.signal,
			})
			.then((r) => {
				check(r.response);
				if (!ctl.signal.aborted) setCatalog(r.data);
			})
			.catch(() => {
				if (!ctl.signal.aborted)
					setError("Couldn’t load your app connections.");
			});
		return () => {
			ctl.abort();
			pending.current?.abort();
		};
	}, [workspace, revision]);
	const source = catalog?.sources.find((s) => s.id === feed);
	async function read() {
		if (
			pending.current ||
			!source?.ready ||
			!catalog?.routes.includes(route) ||
			!question.trim() ||
			question.length > 2000
		)
			return;
		const ctl = new AbortController();
		pending.current = ctl;
		setBusy(true);
		setResult(undefined);
		setError("");
		try {
			const r = await client.POST(`${base}/chat-read`, {
				params: { path: { workspaceId: workspace } },
				headers: { [session.csrfHeader]: session.csrfToken },
				signal: ctl.signal,
				body: { feed, route, question },
			});
			if (ctl.signal.aborted) return;
			if (!r.response.ok || !r.data)
				setError(
					failures[r.response.status] ?? "Couldn’t confirm the app read.",
				);
			else setResult(r.data);
		} catch {
			if (!ctl.signal.aborted) setError("The app read was interrupted.");
		} finally {
			pending.current = null;
			if (!ctl.signal.aborted) setBusy(false);
		}
	}
	return (
		<section className="chat-apps" aria-label="Connected apps">
			<div className="app-actions">
				<strong>Ask a connected app</strong>
				<button type="button" onClick={close}>
					Back to chat
				</button>
			</div>
			{catalog ? (
				catalog.sources.length > 0 ? (
					<form
						onSubmit={(e) => {
							e.preventDefault();
							void read();
						}}
					>
						<div className="app-fields">
							<label>
								App account
								<select
									value={feed}
									disabled={busy}
									onChange={(e) => {
										setFeed(e.target.value);
										setResult(undefined);
										setError("");
									}}
								>
									<option value="">Choose an account</option>
									{catalog.sources.map((s) => (
										<option key={s.id} value={s.id}>
											{names[s.provider] ?? s.provider} · {s.label}
											{s.fixture ? " · sample" : ""}
											{!s.ready ? " · needs sync" : ""}
										</option>
									))}
								</select>
							</label>
							<label>
								Planner model
								<select
									value={catalog.routes.includes(route) ? route : ""}
									disabled={busy}
									onChange={(e) => {
										setRoute(e.target.value);
										setResult(undefined);
									}}
								>
									<option value="">Choose a model</option>
									{catalog.routes.map((id) => (
										<option key={id}>{id}</option>
									))}
								</select>
							</label>
						</div>
						<label>
							Request
							<textarea
								value={question}
								maxLength={2000}
								placeholder="What are the latest items in this account?"
								disabled={busy}
								onChange={(e) => {
									setQuestion(e.target.value);
									setResult(undefined);
								}}
							/>
						</label>
						<small>
							The planner receives your request and the app type. Zincc reads up
							to three synced items locally. Results stay out of chat history.
						</small>
						{source && !source.ready && (
							<p>
								Sync this account in Connections and routines before reading it.
							</p>
						)}
						{!catalog.routes.length && (
							<p>Enable a text model to plan this read.</p>
						)}
						<button
							type="submit"
							disabled={
								busy ||
								!source?.ready ||
								!catalog.routes.includes(route) ||
								!question.trim() ||
								question.length > 2000
							}
						>
							{busy ? "Reading…" : "Run app request"}
						</button>
					</form>
				) : (
					<p>
						Connect Gmail, Google Calendar or a Slack channel to read recent
						items.
					</p>
				)
			) : (
				!error && <p role="status">Loading accounts…</p>
			)}
			{error && <p role="alert">{error}</p>}
			{result && (
				<section aria-label="App result" className="app-result">
					<strong>
						{names[result.provider] ?? result.provider} · {result.label}
						{result.fixture ? " · sample" : ""}
					</strong>
					{result.action === "unsupported" ? (
						<p>
							This tool can list recent synced items. It cannot perform the
							requested operation.
						</p>
					) : (
						<>
							<small>
								Read recent items · last sync{" "}
								{new Date(result.syncedAt ?? "").toLocaleString()}
							</small>
							{result.items.length ? (
								<ol>
									{result.items.map((item, i) => (
										// biome-ignore lint/suspicious/noArrayIndexKey: a read returns a fixed snapshot without provider item IDs.
										<li key={`${item.stamp}-${i}`}>{item.summary}</li>
									))}
								</ol>
							) : (
								<p>No recent items in the synced account.</p>
							)}
						</>
					)}
				</section>
			)}
			<div className="app-actions">
				<button type="button" onClick={connect}>
					Manage connections
				</button>
				<button
					type="button"
					disabled={busy}
					onClick={() => reload((v) => v + 1)}
				>
					Refresh accounts
				</button>
			</div>
		</section>
	);
}
