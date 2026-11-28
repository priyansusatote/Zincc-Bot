import { useCallback, useEffect, useRef, useState } from "react";
import { check, client, type Session } from "./client";
import type { components } from "./generated/api";
type View = components["schemas"]["IntegrationView"];
type Props = { workspace: string; session: Session };
const base = "/api/workspaces/{workspaceId}/integrations" as const;
const names: Record<string, string> = {
	gmail: "Gmail",
	slack: "Slack",
	calendar: "Google Calendar",
	briefing: "Source briefing",
	report: "Dashboard refresh",
};
export function Integrations({ workspace, session }: Props) {
	const [view, setView] = useState<View>();
	const [provider, setProvider] = useState("gmail");
	const [sample, setSample] = useState(false);
	const [skillKey, selectSkill] = useState("briefing-1");
	const [trigger, setTrigger] = useState("daily");
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	const alive = useRef(true);
	const load = useCallback(
		async (signal?: AbortSignal) => {
			const result = await client.GET(base, {
				params: { path: { workspaceId: workspace } },
				signal,
			});
			check(result.response);
			if (alive.current && !signal?.aborted) setView(result.data);
		},
		[workspace],
	);
	useEffect(() => {
		const control = new AbortController();
		alive.current = true;
		let timer: ReturnType<typeof setTimeout>;
		async function poll() {
			try {
				await load(control.signal);
			} catch {
				if (!control.signal.aborted) setError("Couldn’t load integrations.");
			}
			if (!control.signal.aborted) timer = setTimeout(poll, 3000);
		}
		void poll();
		return () => {
			alive.current = false;
			control.abort();
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
					"Couldn’t complete the request. Check your grants and the latest account state.",
				);
		} finally {
			if (alive.current) setBusy(false);
		}
	}
	if (!view) return <p role="status">{error || "Loading connections…"}</p>;
	const params = { path: { workspaceId: workspace } };
	const headers = { [session.csrfHeader]: session.csrfToken };
	const skill = view.catalog.find((s) => `${s.name}-${s.version}` === skillKey);
	const installation = view.installed.find((s) => s.package === skill?.name);
	const installed = Boolean(
		installation && installation.digest === skill?.digest,
	);
	async function install(remove: boolean) {
		if (!skill) return;
		const r = await client.POST(`${base}/skills`, {
			params,
			headers,
			body: {
				name: skill.name,
				version: remove ? 0 : skill.version,
				digest: skill.digest,
				permissions: skill.permissions,
			},
		});
		check(r.response);
	}
	return (
		<section aria-label="Connections and routines">
			{error && <p role="alert">{error}</p>}
			<h3>Connected accounts</h3>
			{view.taskGrants.map((g) => (
				<p key={g.reference}>
					{g.reference} · {g.resource} · {g.active ? "Active" : "Revoked"} ·
					managed in Agents and runs
				</p>
			))}
			<form
				onSubmit={(e) => {
					e.preventDefault();
					const data = new FormData(e.currentTarget);
					void act(async () => {
						const r = await client.POST(`${base}/connections`, {
							params,
							headers,
							body: {
								provider,
								label: String(data.get("label")),
								channel: String(data.get("channel") ?? ""),
								fixture: sample,
							},
						});
						check(r.response);
						if (r.data?.url) window.location.assign(r.data.url);
					});
				}}
			>
				<label>
					Provider
					<select
						value={provider}
						onChange={(e) => setProvider(e.target.value)}
					>
						{["gmail", "slack", "calendar"].map((p) => (
							<option key={p} value={p}>
								{names[p]}
							</option>
						))}
					</select>
				</label>
				<label>
					Account label
					<input
						required
						name="label"
						maxLength={80}
						placeholder="Work inbox"
					/>
				</label>
				{provider === "slack" && (
					<label>
						Public channel ID
						<input
							name="channel"
							required
							pattern="C[A-Z0-9]{2,20}"
							defaultValue={sample ? "CDEMO" : ""}
						/>
					</label>
				)}
				<label>
					<input
						type="checkbox"
						checked={sample}
						onChange={(e) => setSample(e.target.checked)}
					/>
					Use sample account
				</label>
				{!sample && !view.available.includes(provider) && (
					<p>Configure the provider’s OAuth app to connect this account.</p>
				)}
				<button
					disabled={busy || (!sample && !view.available.includes(provider))}
					type="submit"
				>
					{sample ? "Add sample account" : "Authorize account"}
				</button>
			</form>
			<ul>
				{view.feeds.map((f) => (
					<li key={f.id}>
						<strong>{f.label}</strong> · {names[f.provider]} ·{" "}
						{f.fixture ? "Sample" : "Connected account"} · {f.resource} ·{" "}
						{f.state}
						<details>
							<summary>Grant and sync</summary>
							<p>{f.grantScope}</p>
							<p>
								{f.syncedAt
									? `Last synchronized ${new Date(f.syncedAt).toLocaleString()}`
									: f.state === "syncing"
										? `First sync in progress · ${f.itemCount} items saved. Updates automatically.`
										: "First sync pending"}
							</p>
							{(f.fixture
								? ["sync", "sample", "revoke"]
								: ["sync", "revoke"]
							).map((action) => (
								<button
									key={action}
									type="button"
									disabled={busy || f.state === "revoked"}
									onClick={() =>
										void act(async () => {
											const r = await client.POST(`${base}/connections/{id}`, {
												params: { path: { ...params.path, id: f.id } },
												headers,
												body: { action, attempt: crypto.randomUUID() },
											});
											check(r.response);
										})
									}
								>
									{action === "sync"
										? "Sync now"
										: action === "sample"
											? "Simulate incoming item"
											: "Revoke access"}
								</button>
							))}
						</details>
					</li>
				))}
			</ul>
			<h3>Skills and permissions</h3>
			<label>
				Skill version
				<select
					value={skillKey}
					onChange={(e) => {
						selectSkill(e.target.value);
						setTrigger("daily");
					}}
				>
					{view.catalog.map((s) => (
						<option key={s.digest} value={`${s.name}-${s.version}`}>
							{names[s.name]} · v{s.version}
						</option>
					))}
				</select>
			</label>
			{skill && (
				<>
					<p>
						{skill.instructions} Requires{" "}
						{skill.permissions.map((p) => names[p] ?? p).join(", ")}.
					</p>
					<button
						type="button"
						disabled={busy || installed}
						onClick={() => void act(() => install(false))}
					>
						{installed ? "Installed" : "Approve permissions and install"}
					</button>
					{installed && (
						<button
							type="button"
							disabled={busy}
							onClick={() => void act(() => install(true))}
						>
							Uninstall skill
						</button>
					)}
				</>
			)}
			{skill && installed && (
				<form
					key={skill.digest}
					onSubmit={(e) => {
						e.preventDefault();
						const d = new FormData(e.currentTarget);
						const dash = view.dashboards.find(
							(x) => x.id === d.get("dashboard"),
						);
						void act(async () => {
							const r = await client.POST(`${base}/routines`, {
								params,
								headers,
								body: {
									name: String(d.get("name")),
									skill: skill.name,
									version: skill.version,
									trigger,
									zone: String(d.get("zone") ?? "UTC"),
									time: String(d.get("time") ?? "08:00"),
									feeds: d.getAll("feeds").map(String),
									document: dash?.document_id ?? null,
									dashboard: dash?.id ?? null,
								},
							});
							check(r.response);
						});
					}}
				>
					<h3>Create a routine</h3>
					<label>
						Routine name
						<input name="name" required maxLength={80} />
					</label>
					{skill.name === "briefing" ? (
						<fieldset>
							<legend>Accounts this routine can read</legend>
							{view.feeds
								.filter(
									(f) =>
										f.state !== "revoked" &&
										skill.permissions.includes(f.provider),
								)
								.map((f) => (
									<label key={f.id}>
										<input type="checkbox" name="feeds" value={f.id} />
										{f.label}
									</label>
								))}
						</fieldset>
					) : (
						<label>
							Saved dashboard
							<select required name="dashboard">
								<option value="">Choose dashboard</option>
								{view.dashboards.map((d) => (
									<option key={d.id} value={d.id}>
										{d.name}
									</option>
								))}
							</select>
						</label>
					)}
					<label>
						Trigger
						<select
							value={trigger}
							onChange={(e) => setTrigger(e.target.value)}
						>
							<option value="daily">Daily</option>
							{skill.name === "briefing" && (
								<option value="event">When a selected source changes</option>
							)}
						</select>
					</label>
					{trigger === "daily" && (
						<>
							<label>
								Local time
								<input type="time" name="time" required defaultValue="08:00" />
							</label>
							<label>
								Time zone
								<input
									required
									name="zone"
									defaultValue={
										Intl.DateTimeFormat().resolvedOptions().timeZone
									}
								/>
							</label>
						</>
					)}
					<button type="submit" disabled={busy}>
						Create routine
					</button>
				</form>
			)}
			<h3>Your routines</h3>
			<ul>
				{view.routines.map((r) => (
					<li key={r.id}>
						<strong>{r.name}</strong> · {r.package} v{r.version} ·{" "}
						{r.paused
							? "Paused"
							: r.trigger_kind === "event"
								? "On source change"
								: `${r.time} ${r.zone}`}
						{r.next_time && !r.paused && (
							<p>Next run {new Date(r.next_time).toLocaleString()}</p>
						)}
						{["run", r.paused ? "resume" : "pause"].map((action) => (
							<button
								type="button"
								key={action}
								disabled={busy || (action === "run" && r.paused)}
								onClick={() =>
									void act(async () => {
										const reply = await client.POST(`${base}/routines/{id}`, {
											params: { path: { ...params.path, id: r.id } },
											headers,
											body: { action, attempt: crypto.randomUUID() },
										});
										check(reply.response);
									})
								}
							>
								{action === "run"
									? "Run now"
									: action === "resume"
										? "Resume"
										: "Pause"}
							</button>
						))}
					</li>
				))}
			</ul>
			<h3>Run receipts</h3>
			{view.history.length === 0 ? (
				<p>No runs yet.</p>
			) : (
				<ul>
					{view.history.map((h) => (
						<li key={h.id}>
							<strong>{h.name}</strong> · {h.state} ·{" "}
							{new Date(h.created_at).toLocaleString()}
							<pre className="routine-receipt">{h.receipt}</pre>
						</li>
					))}
				</ul>
			)}
		</section>
	);
}
