import { useCallback, useEffect, useRef, useState } from "react";
import { Bot, Plus } from "lucide-react";
import { AgentMemory } from "./AgentMemory";
import { VersionHistory } from "./VersionHistory";
import { EnginePicker } from "./EnginePicker";
import { client, check, type Session } from "./client";
import type { components } from "./generated/api";
const base = "/api/workspaces/{workspaceId}/agents" as const;
type View = components["schemas"]["AgentView"];
type Props = { workspace: string; session: Session; refresh: () => void };
export function Agents({ workspace, session, refresh }: Props) {
	const [view, setView] = useState<View>();
	const [id, select] = useState("");
	const [creating, setCreating] = useState(false);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const [mode, setMode] = useState("model");
	const [checks, setChecks] = useState<Record<string, string>>({});
	const [reviewers, setReviewers] = useState<string[]>([]);
	const [memoryQuery, openMemory] = useState<string | null>(null);
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
	const GoalField = mode === "team" ? "textarea" : "input";
	const path = { workspaceId: workspace };
	const headers = { [session.csrfHeader]: session.csrfToken };
	const pending = view.runs.filter(
		(r) => r.state === "waiting" || (r.state === "unknown" && r.digest),
	);
	return (
		<section aria-label="Agent workspace">
			<div className="roster-head">
				<h3>Your agents</h3>
				<button
					type="button"
					onClick={() => {
						select("");
						setCreating(true);
						setReviewers([]);
						attempt.current = "";
					}}
				>
					<Plus size={15} /> New agent
				</button>
			</div>
			<div className="agent-roster">
				{view.agents.map((a) => (
					<button
						type="button"
						key={a.id}
						aria-pressed={a.id === id}
						onClick={() => {
							select(a.id);
							setCreating(false);
							setReviewers([]);
							attempt.current = "";
						}}
					>
						<span className="agent-icon">
							<Bot size={20} />
						</span>
						<span className="agent-caption">
							<strong>{a.name}</strong>
							<small>
								{a.route || "Agent"} · v{a.version}
							</small>
						</span>
					</button>
				))}
			</div>
			<section className="decision-tray" aria-label="Needs you">
				<h3>Needs you · {pending.length}</h3>
				{pending.length === 0 && <p>No decisions waiting.</p>}
				<ul>
					{pending.map((r) => (
						<li key={r.id}>
							<a href={`#run-${r.id}`}>
								{view.agents.find((a) => a.id === r.agentId)?.name ?? "Agent"} ·{" "}
								{r.mode === "team"
									? "Choose reviews"
									: r.state === "unknown"
										? "Check delivery"
										: "Review task creation"}{" "}
								· {r.title || r.goal}
							</a>
						</li>
					))}
				</ul>
			</section>
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

			{agent && (
				<EnginePicker
					key={agent.id}
					workspace={workspace}
					agent={agent.id}
					session={session}
				/>
			)}
			{agent?.mine && (
				<VersionHistory
					key={`${agent.id}:${agent.version}`}
					agent={agent}
					workspace={workspace}
					headers={headers}
					reload={load}
				/>
			)}
			{((!agent && (creating || view.agents.length === 0)) || agent?.mine) && (
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
										framework: field("framework") || "native",
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
							Planning framework
							<select
								name="framework"
								defaultValue={agent?.framework ?? "native"}
							>
								{(view.frameworks ?? ["native"]).map((f) => (
									<option key={f}>{f}</option>
								))}
							</select>
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
							const r =
								mode === "team"
									? await client.POST(`${base}/{id}/team`, {
											params: { path: { ...path, id: agent.id } },
											headers,
											body: {
												requestId: attempt.current,
												goal,
												reviewers,
												checks: Object.fromEntries(
													reviewers.map((id) => [id, checks[id] ?? "opinion"]),
												),
											},
										})
									: await client.POST(`${base}/{id}/runs`, {
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
							<option value="team">Parallel independent reviews</option>
							<option value="recall">Recall private memory locally</option>
							<option value="model">Let the agent choose</option>
							<option value="list">List tasks</option>
							<option value="create">Create a task with approval</option>
						</select>
					</label>
					{mode === "team" && (
						<fieldset>
							<legend>Choose up to three reviewers</legend>
							{view.agents
								.filter((a) => a.id !== id)
								.map((a) => (
									<label key={a.id}>
										<input
											type="checkbox"
											checked={reviewers.includes(a.id)}
											disabled={
												!reviewers.includes(a.id) && reviewers.length >= 3
											}
											onChange={(e) => {
												setReviewers(
													e.target.checked
														? [...reviewers, a.id]
														: reviewers.filter((x) => x !== a.id),
												);
												attempt.current = "";
											}}
										/>
										{a.name}
									</label>
								))}
							{reviewers.map((id) => (
								<label key={id}>
									{view.agents.find((a) => a.id === id)?.name} review method
									<select
										value={checks[id] ?? "opinion"}
										onChange={(e) => {
											setChecks({ ...checks, [id]: e.target.value });
											attempt.current = "";
										}}
									>
										<option value="opinion">Model opinion</option>
										<option value="word-count">Local word count</option>
										<option value="checklist">
											Local unchecked-item count
										</option>
									</select>
								</label>
							))}
							<p>
								Model reviewers send the goal and their role to the selected
								model. Local checks use only the submitted text. All reviewers
								have no connector permissions.
							</p>
						</fieldset>
					)}
					<label htmlFor={`agent-goal-${workspace}`}>
						{mode === "create" ? "Task title" : "Goal"}
						<GoalField
							id={`agent-goal-${workspace}`}
							value={goal}
							onChange={(e) => {
								setGoal(e.target.value);
								attempt.current = "";
							}}
							required
							maxLength={mode === "create" ? 250 : 2000}
						/>
					</label>
					<button
						type="submit"
						disabled={busy || (mode === "team" && !reviewers.length)}
					>
						Start run
					</button>
				</form>
			)}
			{error && <p role="alert">{error}</p>}
			<button
				type="button"
				onClick={() => openMemory(memoryQuery === null ? "" : null)}
			>
				{memoryQuery === null ? "Open private memory" : "Close private memory"}
			</button>
			{memoryQuery !== null && (
				<AgentMemory
					key={workspace + memoryQuery}
					initial={memoryQuery}
					workspace={workspace}
					session={session}
					runs={view.runs}
				/>
			)}
			{view.runs.map((r) => (
				<article
					key={r.id}
					id={`run-${r.id}`}
					tabIndex={-1}
					className="exchange"
				>
					<h3>{r.goal}</h3>
					<p>
						v{r.version} · {r.state} · {r.steps} steps
					</p>
					<p>{r.outcome}</p>
					{r.mode === "recall" && r.state === "completed" && (
						<button type="button" onClick={() => openMemory(r.goal)}>
							Open current sources
						</button>
					)}
					{r.mode === "team" && (
						<ol>
							{view.teamMembers
								?.filter((m) => m.parentId === r.id)
								.map((m) => (
									<li key={m.childId}>
										{view.agents.find((a) => a.id === m.agentId)?.name ??
											"Reviewer"}{" "}
										· {m.state} {m.outcome}
										{r.state === "waiting" && (
											<button
												type="button"
												disabled={busy}
												onClick={() =>
													void act(async () => {
														check(
															(
																await client.POST(
																	`${base}/runs/{id}/decision`,
																	{
																		params: { path: { ...path, id: r.id } },
																		headers,
																		body: {
																			operation: "select",
																			digest: m.childId,
																		},
																	},
																)
															).response,
														);
													})
												}
											>
												Keep this review
											</button>
										)}
									</li>
								))}
						</ol>
					)}
					{r.mode === "team" && r.state === "waiting" && (
						<button
							type="button"
							disabled={busy}
							onClick={() =>
								void act(async () => {
									check(
										(
											await client.POST(`${base}/runs/{id}/decision`, {
												params: { path: { ...path, id: r.id } },
												headers,
												body: { operation: "select", digest: "all" },
											})
										).response,
									);
								})
							}
						>
							Keep all attributed reviews
						</button>
					)}
					{r.title && (
						<p>
							Task title: {r.title} · List: {r.resource}
						</p>
					)}
					{r.receipt && <p>Provider receipt: {r.receipt}</p>}
					{(r.state === "waiting"
						? r.mode === "team"
							? ["cancel"]
							: ["approve", "cancel"]
						: r.state === "unknown"
							? r.digest
								? ["reconcile"]
								: []
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
