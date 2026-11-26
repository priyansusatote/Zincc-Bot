import { useState, useRef } from "react";
import { client, check, type Session } from "./client";
import { directory } from "./conversation-client";
import type { components } from "./generated/api";

const root = "/api/workspaces/{workspaceId}/agents";
export function EnginePicker({
	workspace,
	agent,
	session,
}: {
	workspace: string;
	agent: string;
	session: Session;
}) {
	const [view, setView] = useState<components["schemas"]["EnginePicker"]>();
	const [threads, setThreads] = useState<{ id: string; title: string }[]>([]);
	const [thread, setThread] = useState("");
	const [connection, setConnection] = useState("");
	const [model, setModel] = useState("fixture/word-count");
	const [label, setLabel] = useState("My local fixture");
	const [goal, setGoal] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const attempt = useRef("");
	const path = { workspaceId: workspace, id: agent };
	const headers = { [session.csrfHeader]: session.csrfToken };
	async function load() {
		const r = await client.GET(`${root}/{id}/engines`, { params: { path } });
		check(r.response);
		setView(r.data);
		setThreads(
			(await directory(workspace, new AbortController().signal)).threads,
		);
	}
	async function act(fn?: () => Promise<{ response: Response }>) {
		setBusy(true);
		setError("");
		try {
			if (fn) check((await fn()).response);
			await load();
		} catch {
			setError(
				"Couldn’t confirm the operation. Refresh and check the connection and selected model.",
			);
		} finally {
			setBusy(false);
		}
	}
	const selected =
		view?.choices.find((c) => thread && c.thread === thread) ??
		view?.choices.find((c) => !c.thread);
	return (
		<details
			onToggle={(e) => {
				if (e.currentTarget.open && !view && !busy) void act();
			}}
		>
			<summary>Connected engines</summary>
			<p>
				Offline fixture · this Zincc server · no subscription usage · no
				external tools
			</p>
			{view && (
				<>
					<label>
						Connection name
						<input
							maxLength={80}
							value={label}
							onChange={(e) => setLabel(e.target.value)}
						/>
					</label>
					<button
						type="button"
						disabled={busy || !label.trim()}
						onClick={() =>
							void act(() =>
								client.POST(`${root}/engines/fixture`, {
									params: { path },
									headers,
									body: { label },
								}),
							)
						}
					>
						Connect local fixture
					</button>
					{view.accounts.map((a) => (
						<p key={a.id}>
							{a.label} · {a.active ? "connected" : "revoked"}{" "}
							{a.active && (
								<button
									disabled={busy}
									type="button"
									onClick={() =>
										void act(() =>
											client.DELETE(`${root}/engines/{connection}`, {
												params: { path: { ...path, connection: a.id } },
												headers,
											}),
										)
									}
								>
									Revoke {a.label}
								</button>
							)}
						</p>
					))}
					<label>
						Applies to
						<select
							value={thread}
							onChange={(e) => {
								setThread(e.target.value);
								attempt.current = "";
							}}
						>
							<option value="">Agent default</option>
							{threads.map((t) => (
								<option key={t.id} value={t.id}>
									{t.title}
								</option>
							))}
						</select>
					</label>
					<p>
						Effective selection ·{" "}
						{view.accounts.find((a) => a.id === selected?.connection)?.label ??
							"None"}{" "}
						{selected?.model}
					</p>
					<label>
						Engine account
						<select
							value={connection}
							onChange={(e) => setConnection(e.target.value)}
						>
							<option value="">Choose account</option>
							{view.accounts
								.filter((a) => a.active)
								.map((a) => (
									<option key={a.id} value={a.id}>
										{a.label}
									</option>
								))}
						</select>
					</label>
					<label>
						Engine model
						<select value={model} onChange={(e) => setModel(e.target.value)}>
							{view.models.map((m) => (
								<option key={m}>{m}</option>
							))}
						</select>
					</label>
					<button
						type="button"
						disabled={busy || !connection}
						onClick={() =>
							void act(() =>
								client.PUT(`${root}/{id}/engines/selection`, {
									params: { path },
									headers,
									body: { thread: thread || null, connection, model },
								}),
							)
						}
					>
						Save selection
					</button>
					<button
						type="button"
						disabled={
							busy || !view.choices.some((c) => c.thread === (thread || null))
						}
						onClick={() =>
							void act(() =>
								client.PUT(`${root}/{id}/engines/selection`, {
									params: { path },
									headers,
									body: {
										thread: thread || null,
										connection: null,
										model: null,
									},
								}),
							)
						}
					>
						{thread ? "Inherit agent default" : "Clear default"}
					</button>
					<label>
						Text to check
						<textarea
							value={goal}
							maxLength={2000}
							onChange={(e) => {
								setGoal(e.target.value);
								attempt.current = "";
							}}
						/>
					</label>
					<button
						type="button"
						disabled={busy || !selected || !goal.trim()}
						onClick={() =>
							void act(async () => {
								attempt.current ||= crypto.randomUUID();
								const r = await client.POST(`${root}/{id}/engine-runs`, {
									params: { path },
									headers,
									body: {
										requestId: attempt.current,
										goal,
										thread: thread || null,
									},
								});
								if (r.response.ok) {
									attempt.current = "";
									setGoal("");
								}
								return r;
							})
						}
					>
						Run selected engine
					</button>
					<ul>
						{view.receipts.map((r) => (
							<li key={r.run}>
								{view.accounts.find((a) => a.id === r.connection)?.label} ·{" "}
								{r.model} · grant v{r.generation} ·{" "}
								{r.acknowledged
									? "model acknowledged"
									: "awaiting acknowledgement"}
							</li>
						))}
					</ul>
				</>
			)}
			<button type="button" disabled={busy} onClick={() => void act()}>
				Refresh connections and receipts
			</button>
			{error && <p role="alert">{error}</p>}
		</details>
	);
}
