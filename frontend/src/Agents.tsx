import { useCallback, useEffect, useRef, useState } from "react";
import { client, check, type Session } from "./client";
import type { components } from "./generated/api";
const base = "/api/workspaces/{workspaceId}/agents" as const;
type View = components["schemas"]["AgentView"];
type Props = { workspace: string; session: Session; refresh: () => void };
export function Agents(props: Props) {
	const [open, setOpen] = useState(false);
	return (
		<details
			className="conversation"
			onToggle={(e) => setOpen(e.currentTarget.open)}
		>
			<summary>Agents and runs</summary>
			{open && <Panel {...props} />}
		</details>
	);
}
function Panel({ workspace, session, refresh }: Props) {
	const [view, setView] = useState<View>();
	const [id, select] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const [mode, setMode] = useState("model");
	const [goal, setGoal] = useState("");
	const attempt = useRef("");
	const alive = useRef(true);
	const load = useCallback(
		async (signal?: AbortSignal) => {
			const result = await client.GET(base, {
				params: { path: { workspaceId: workspace } },
				signal,
			});
			if (result.response.status === 401 || result.response.status === 404) {
				if (!signal?.aborted) refresh();
				return;
			}
			check(result.response);
			if (!result.data) throw Error("Empty workspace");
			if (alive.current && !signal?.aborted) setView(result.data);
		},
		[workspace, refresh],
	);
	useEffect(() => {
		alive.current = true;
		const ctl = new AbortController();
		let timer: ReturnType<typeof setTimeout>;
		async function poll() {
			try {
				await load(ctl.signal);
			} catch {
				if (!ctl.signal.aborted) setError("Couldn’t load agents.");
			}
			if (!ctl.signal.aborted) timer = setTimeout(poll, 2000);
		}
		void poll();
		return () => {
			alive.current = false;
			ctl.abort();
			clearTimeout(timer);
		};
	}, [load]);
	async function act(fn: () => Promise<void>) {
		if (busy) return;
		setBusy(true);
		setError("");
		try {
			await fn();
			await load();
		} catch {
			if (alive.current)
				setError(
					"Couldn’t confirm this change. Review the latest state and permissions before retrying.",
				);
		} finally {
			if (alive.current) setBusy(false);
		}
	}
	if (!view) return <p role="status">{error || "Loading agents…"}</p>;
	const agent = view.agents.find((a) => a.id === id);
	const path = { workspaceId: workspace };
	const headers = { [session.csrfHeader]: session.csrfToken };
	return (
		<section aria-label="Agent workspace">
			<p>
				{view.provider === "sandbox-tasks"
					? "Local task sandbox"
					: "Google Tasks"}{" "}
				· {view.connection.active ? "Connected" : "Disconnected"}
			</p>
			<button
				type="button"
				disabled={busy || (!view.available && !view.connection.active)}
				onClick={() =>
					void act(async () => {
						const r = await client.POST(`${base}/connection`, {
							params: { path },
							headers,
							body: { active: !view.connection.active },
						});
						check(r.response);
					})
				}
			>
				{view.connection.active
					? "Revoke task connection"
					: "Connect task list"}
			</button>
			<label>
				Agent
				<select
					value={id}
					onChange={(e) => {
						select(e.target.value);
						attempt.current = "";
					}}
				>
					<option value="">New agent</option>
					{view.agents.map((a) => (
						<option key={a.id} value={a.id}>
							{a.name} · v{a.version}
						</option>
					))}
				</select>
			</label>
			{(!agent || agent.mine) && (
				<details key={agent?.id ?? "new"} open={!agent}>
					<summary>{agent ? "Edit definition" : "Agent definition"}</summary>
					<form
						key={agent?.version ?? 0}
						onSubmit={(e) => {
							e.preventDefault();
							const data = new FormData(e.currentTarget);
							const field = (key: string) => String(data.get(key) ?? "");
							void act(async () => {
								const key = agent?.id ?? crypto.randomUUID();
								const r = await client.POST(`${base}/{id}/versions`, {
									params: { path: { ...path, id: key } },
									headers,
									body: {
										name: field("name"),
										instructions: field("instructions"),
										route: field("route"),
										canRead: data.has("read"),
										canWrite: data.has("write"),
										delegateId: field("delegate") || null,
										expectedVersion: agent?.version ?? 0,
									},
								});
								check(r.response);
								select(key);
							});
						}}
					>
						<label>
							Agent name
							<input
								name="name"
								defaultValue={agent?.name}
								maxLength={80}
								required
							/>
						</label>
						<label>
							Instructions
							<textarea
								name="instructions"
								defaultValue={agent?.instructions}
								maxLength={3000}
								required
							/>
						</label>
						<label>
							Agent model
							<select name="route" defaultValue={agent?.route} required>
								{view.routes.map((r) => (
									<option key={r}>{r}</option>
								))}
							</select>
						</label>
						<label>
							<input
								type="checkbox"
								name="read"
								defaultChecked={agent?.canRead ?? true}
							/>
							Read tasks
						</label>
						<label>
							<input
								type="checkbox"
								name="write"
								defaultChecked={agent?.canWrite}
							/>
							Propose task creation
						</label>
						<label>
							Delegate principal ID (optional)
							<input name="delegate" defaultValue={agent?.delegateId ?? ""} />
						</label>
						<button type="submit" disabled={busy || !view.routes.length}>
							{agent ? "Save new version" : "Create agent"}
						</button>
					</form>
				</details>
			)}
			{agent && (
				<form
					onSubmit={(e) => {
						e.preventDefault();
						void act(async () => {
							attempt.current ||= crypto.randomUUID();
							const r = await client.POST(`${base}/{id}/runs`, {
								params: { path: { ...path, id: agent.id } },
								headers,
								body: { requestId: attempt.current, goal, mode },
							});
							check(r.response);
							attempt.current = "";
							setGoal("");
						});
					}}
				>
					<label>
						Run mode
						<select
							value={mode}
							onChange={(e) => {
								setMode(e.target.value);
								attempt.current = "";
							}}
						>
							<option value="model">Let the agent choose</option>
							<option value="list">List tasks</option>
							<option value="create">Create a task with approval</option>
						</select>
					</label>
					<label>
						{mode === "create" ? "Task title" : "Goal"}
						<input
							value={goal}
							onChange={(e) => {
								setGoal(e.target.value);
								attempt.current = "";
							}}
							required
							maxLength={mode === "create" ? 250 : 2000}
						/>
					</label>
					<button type="submit" disabled={busy}>
						Start run
					</button>
				</form>
			)}
			{error && <p role="alert">{error}</p>}
			{view.runs.map((r) => (
				<article key={r.id} className="exchange">
					<h3>{r.goal}</h3>
					<p>
						v{r.version} · {r.state} · {r.steps} steps
					</p>
					<p>{r.outcome}</p>
					{r.title && (
						<p>
							Task title: {r.title} · List: {r.resource}
						</p>
					)}
					{r.receipt && <p>Provider receipt: {r.receipt}</p>}
					{(r.state === "waiting"
						? ["approve", "cancel"]
						: r.state === "unknown"
							? ["reconcile"]
							: ["queued", "running", "approved"].includes(r.state)
								? ["cancel"]
								: []
					).map((op) => (
						<button
							type="button"
							key={op}
							disabled={busy}
							onClick={() =>
								void act(async () => {
									const v = await client.POST(`${base}/runs/{id}/decision`, {
										params: { path: { ...path, id: r.id } },
										headers,
										body: { operation: op, digest: r.digest },
									});
									check(v.response);
								})
							}
						>
							{op === "approve"
								? "Approve exact task"
								: op === "cancel"
									? "Cancel run"
									: "Recheck provider receipt"}
						</button>
					))}
				</article>
			))}
		</section>
	);
}
