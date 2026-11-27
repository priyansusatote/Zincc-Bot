import { useEffect, useState } from "react";
import { check, client, type Session } from "./client";
import type { components } from "./generated/api";
type View = components["schemas"]["RunnerView"];
type Action = "inventory" | "browser" | "pause" | "resume" | "revoke";
const base = "/api/workspaces/{workspaceId}/runners" as const;
type Props = { workspace: string; session: Session };
function deviceGuidance(device: View["devices"][number]) {
	switch (device.state) {
		case "pairing":
			return "Enter the pairing code in the foreground runner on this device.";
		case "expired":
			return "The pairing code expired. Create a new code on this page.";
		case "revoked":
			return "Access was revoked. Pair again to grant access for a new task.";
		case "paused":
			return "Resume here when ready. Previously cancelled jobs require a new request.";
		case "active":
			if (!device.online)
				return "No recent heartbeat. If the local runner is paused, type resume there. If it has closed, create a new code and pair it again.";
			if (!device.folder && !device.browser)
				return "This device has no folder or browser grant. To run a desktop job, pair a separate runner with the required grant.";
			return "For additional capabilities, start a new pairing with the required local grant.";
	}
}

function jobGuidance(job: View["jobs"][number]) {
	if (job.state === "failed") {
		switch (job.failureReason) {
			case "access-denied":
				return "The operating system denied access. Review local permissions before requesting another job.";
			case "folder-changed":
				return "The granted folder moved or changed. Pair again with the intended folder.";
			case "local-pause":
				return "Paused on the device. Type resume in the runner before requesting another job.";
		}
	}
	switch (job.state) {
		case "queued":
			return "Waiting for the paired runner to collect this job.";
		case "leased":
			return job.kind === "browser"
				? "Open the review window on this device and return control when ready."
				: "The runner is counting files in the granted folder.";
		case "expired":
			return "The job timed out. Check the runner connection before starting a new job.";
		case "cancelled":
			return "This job ended. Review the device grant before requesting another job.";
		case "failed":
			return job.kind === "browser"
				? "Check the local runner and its browser installation, then request a new review."
				: "Check the local runner and that its granted folder still exists at the original path.";
		default:
			return "";
	}
}
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
			<p>
				For ChatGPT plan access, run{" "}
				<code>node frontend/runner/local.mjs --engine chatgpt-plan</code> on
				this PC. For OpenCode Free or Antigravity, use{" "}
				<code>--engine opencode-free</code> or <code>--engine antigravity</code>{" "}
				and supply <code>--binary</code> with the installed CLI path. After
				pairing, select the account in Agents and runs.
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
						<span>
							· Folder count {d.folder ? "granted" : "unavailable"} · Browser
							review {d.browser ? "granted" : "unavailable"}.{" "}
						</span>
					)}
					{deviceGuidance(d)}{" "}
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
					{j.kind === "browser" ? "Priority review" : "File count"} · {j.state}{" "}
					{jobGuidance(j)}
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
