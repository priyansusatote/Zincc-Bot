import { useState } from "react";
import { client, check } from "./client";
import type { components } from "./generated/api";

type Agent = components["schemas"]["AgentVersion"];
const endpoint = "/api/workspaces/{workspaceId}/agents/{id}/versions";
const fields = [
	"name",
	"instructions",
	"route",
	"framework",
	"canRead",
	"canWrite",
	"delegateId",
] as const;
const labels = [
	"Name",
	"Instructions",
	"Model route",
	"Framework",
	"Read task list",
	"Propose task creation",
	"Delegate",
];

export function VersionHistory({
	agent,
	workspace,
	headers,
	reload,
}: {
	agent: Agent;
	workspace: string;
	headers: Record<string, string>;
	reload: () => Promise<void>;
}) {
	const [versions, setVersions] = useState<Agent[]>([]);
	const [more, setMore] = useState(true);
	const [selection, select] = useState(0);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const path = { workspaceId: workspace, id: agent.id };
	const prior = versions.find((v) => v.version === selection);
	async function load() {
		setBusy(true);
		setError("");
		try {
			const before = versions.at(-1)?.version ?? agent.version;
			const result = await client.GET(endpoint, {
				params: { path, query: { before } },
			});
			check(result.response);
			const data = result.data ?? [];
			setVersions([...versions, ...data]);
			setMore(data.length === 20);
		} catch {
			setError("Couldn’t read version history.");
		} finally {
			setBusy(false);
		}
	}
	async function restore(version: Agent) {
		setBusy(true);
		setError("");
		try {
			const {
				name,
				instructions,
				route,
				framework,
				canRead,
				canWrite,
				delegateId,
			} = version;
			const result = await client.POST(endpoint, {
				params: { path },
				headers,
				body: {
					name,
					instructions,
					route,
					framework,
					canRead,
					canWrite,
					delegateId,
					expectedVersion: agent.version,
				},
			});
			check(result.response);
			await reload();
		} catch {
			setError(
				"Couldn’t restore this version. Recheck the current version, route and grants.",
			);
		} finally {
			setBusy(false);
		}
	}
	if (agent.version === 1) return null;
	return (
		<details>
			<summary>Version history</summary>
			{more && (
				<button type="button" disabled={busy} onClick={() => void load()}>
					{versions.length ? "Load earlier versions" : "Load versions"}
				</button>
			)}
			{versions.length > 0 && (
				<label>
					Compare with current v{agent.version}
					<select
						value={selection}
						onChange={(e) => select(Number(e.target.value))}
					>
						<option value={0}>Select version</option>
						{versions.map((v) => (
							<option key={v.version} value={v.version}>
								v{v.version}
							</option>
						))}
					</select>
				</label>
			)}
			{prior && (
				<>
					<ul>
						{fields.map(
							(f, i) =>
								prior[f] !== agent[f] && (
									<li key={f}>
										{labels[i]} · current {String(agent[f] ?? "none")} → restore{" "}
										{String(prior[f] ?? "none")}
									</li>
								),
						)}
					</ul>
					<button
						disabled={busy}
						type="button"
						onClick={() => void restore(prior)}
					>
						Restore v{prior.version} as a new version
					</button>
				</>
			)}
			{error && <p role="alert">{error}</p>}
		</details>
	);
}
