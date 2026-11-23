import { useEffect, useState } from "react";
import { check, client, type Session } from "./client";
import type { components } from "./generated/api";
type View = components["schemas"]["RunnerView"];
type Action = "inventory" | "browser" | "pause" | "resume" | "revoke";
const base = "/api/workspaces/{workspaceId}/runners" as const;
type Props = { workspace: string; session: Session };
export function Runners(props: Props) {
	const [open, setOpen] = useState(false);
	return (
		<details
			className="conversation"
			onToggle={(e) => setOpen(e.currentTarget.open)}
		>
			<summary>Customer runners</summary>
			{open && <Panel {...props} />}
		</details>
	);
}
function Panel({ workspace, session }: Props) {
	const [view, setView] = useState<View>();
	const [code, setCode] = useState("");
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	const [revision, reload] = useState(0);
	// biome-ignore lint/correctness/useExhaustiveDependencies: refresh after commands
	useEffect(() => {
		const controller = new AbortController();
		let timer: ReturnType<typeof setTimeout>;
		async function load() {
			try {
				const r = await client.GET(base, {
					params: { path: { workspaceId: workspace } },
					signal: controller.signal,
				});
				check(r.response);
				if (!controller.signal.aborted) setView(r.data);
			} catch {
				if (!controller.signal.aborted) setError("Couldn’t load runners.");
			}
			if (!controller.signal.aborted) timer = setTimeout(load, 3000);
		}
		void load();
		return () => {
			controller.abort();
			clearTimeout(timer);
		};
	}, [workspace, revision]);
	async function act(device?: string, action?: Action) {
		if (busy) return;
		setBusy(true);
		setError("");
		const headers = { [session.csrfHeader]: session.csrfToken };
		try {
			if (device && action) {
				const r = await client.POST(`${base}/{device}`, {
					params: { path: { workspaceId: workspace, device } },
					headers,
					body: { action },
				});
				check(r.response);
			} else {
				const r = await client.POST(base, {
					params: { path: { workspaceId: workspace } },
					headers,
				});
				check(r.response);
				setCode(r.data?.code ?? "");
			}
			reload((n) => n + 1);
		} catch {
			setError(
				"Request declined. Check the runner connection, grants and pending job.",
			);
		} finally {
			setBusy(false);
		}
	}
	return (
		<section aria-label="Customer runners">
			<p>
				On your computer run{" "}
				<code>node frontend/runner/local.mjs --browser</code>. To share a
				top-level file count, add <code>--folder "your folder"</code>.
			</p>
			<button type="button" disabled={busy} onClick={() => void act()}>
				Create pairing code
			</button>
			{code && (
				<p>
					Paste into the local runner within ten minutes. <code>{code}</code>{" "}
					<button type="button" onClick={() => setCode("")}>
						Hide code
					</button>
				</p>
			)}
			{error && <p role="alert">{error}</p>}
			{view?.devices.map((d) => (
				<p key={d.id}>
					<strong>Runner {d.id.slice(0, 8)}</strong> · {d.state}
					{d.state === "active" && (d.online ? " · online" : " · offline")}{" "}
					{d.state === "active" && (
						<>
							{d.folder && (
								<button
									type="button"
									disabled={busy || !d.online}
									onClick={() => void act(d.id, "inventory")}
								>
									Count files
								</button>
							)}
							{d.browser && (
								<button
									type="button"
									disabled={busy || !d.online}
									onClick={() => void act(d.id, "browser")}
								>
									Review in browser
								</button>
							)}
							<button
								type="button"
								disabled={busy}
								onClick={() => void act(d.id, "pause")}
							>
								Pause
							</button>
						</>
					)}
					{d.state === "paused" && (
						<button
							type="button"
							disabled={busy}
							onClick={() => void act(d.id, "resume")}
						>
							Resume
						</button>
					)}
					{["active", "paused", "pairing"].includes(d.state) && (
						<button
							type="button"
							disabled={busy}
							onClick={() => void act(d.id, "revoke")}
						>
							Revoke runner
						</button>
					)}
				</p>
			))}
			{Boolean(view?.jobs.length) && <h3>Runner receipts</h3>}
			{view?.jobs.map((j) => (
				<p key={j.id}>
					Runner {j.device.slice(0, 8)} ·{" "}
					{j.kind === "browser" ? "Priority review" : "File count"} · {j.state}
					{j.state === "complete" &&
						typeof j.result === "number" &&
						(j.kind === "browser"
							? ` · ${["Low", "Normal", "High"][j.result]}`
							: ` · ${j.result} files`)}
				</p>
			))}
		</section>
	);
}
