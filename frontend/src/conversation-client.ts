import { ApiError, check, client, type Session } from "./client";
import type { components } from "./generated/api";
type Schema = components["schemas"];
export type Directory = Schema["ConversationDirectory"];
export type Snapshot = Schema["ConversationSnapshot"];
export type Command = Schema["TurnCommand"];
type Event = Schema["ConversationEvent"];

const root = "/api/workspaces/{workspaceId}/conversations" as const;
export async function directory(
	workspaceId: string,
	signal: AbortSignal,
): Promise<Directory> {
	const { data, response } = await client.GET(root, {
		params: { path: { workspaceId } },
		signal,
	});
	check(response);
	if (
		!data ||
		!Array.isArray(data.threads) ||
		!data.threads.every(
			(t) => typeof t.id === "string" && typeof t.title === "string",
		) ||
		!Array.isArray(data.routes) ||
		!data.routes.every((r) => typeof r === "string")
	)
		throw Error("Invalid directory");
	return data;
}
export async function snapshot(
	workspaceId: string,
	id: string,
	signal: AbortSignal,
): Promise<Snapshot> {
	const { data, response } = await client.GET(`${root}/{id}`, {
		params: { path: { workspaceId, id } },
		signal,
	});
	check(response);
	if (
		!data ||
		!Number.isSafeInteger(data.revision) ||
		data.revision < 0 ||
		!Array.isArray(data.turns) ||
		!data.turns.every(
			(t) =>
				typeof t.id === "string" &&
				typeof t.question === "string" &&
				typeof t.answer === "string" &&
				typeof t.route === "string" &&
				["running", "completed", "interrupted"].includes(t.state),
		)
	)
		throw Error("Invalid snapshot");
	return data;
}
export async function submit(
	workspaceId: string,
	id: string,
	cmd: Command,
	session: Session,
) {
	const { response } = await client.POST(`${root}/{id}/turns`, {
		params: { path: { workspaceId, id } },
		body: cmd,
		headers: { [session.csrfHeader]: session.csrfToken },
	});
	check(response);
}
export async function replay(
	workspaceId: string,
	id: string,
	state: Snapshot,
	signal: AbortSignal,
): Promise<Snapshot> {
	const { data, response } = await client.GET(`${root}/{id}/events`, {
		params: { path: { workspaceId, id }, query: { after: state.revision } },
		headers: { Accept: "text/event-stream" },
		parseAs: "text",
		signal,
	});
	if (response.status === 410 || response.status === 409)
		return snapshot(workspaceId, id, signal);
	check(response);
	if (
		!response.headers.get("content-type")?.startsWith("text/event-stream") ||
		typeof data !== "string"
	)
		throw new ApiError(502);
	let next = state;
	for (const frame of data.split(/\r?\n\r?\n/)) {
		const lines = frame.split(/\r?\n/);
		if (!lines.includes("event: turn.v1")) continue;
		const row = JSON.parse(
			lines.find((l) => l.startsWith("data: "))?.slice(6) ?? "null",
		) as Event | null;
		if (
			!row ||
			!Number.isSafeInteger(row.seq) ||
			!lines.includes(`id: ${row.seq}`) ||
			typeof row.turnId !== "string" ||
			typeof row.body !== "string" ||
			typeof row.route !== "string" ||
			!["started", "delta", "completed", "interrupted"].includes(row.kind)
		)
			throw Error("Invalid event");
		if (row.seq <= next.revision) continue;
		const last = next.turns.at(-1);
		if (
			row.seq !== next.revision + 1 ||
			(row.kind === "started"
				? last?.state === "running"
				: last?.id !== row.turnId || last.state !== "running")
		)
			return snapshot(workspaceId, id, signal);
		if (row.kind === "started")
			next = {
				revision: row.seq,
				turns: [
					...next.turns,
					{
						id: row.turnId,
						route: row.route,
						question: row.body,
						answer: "",
						state: "running",
					},
				],
			};
		else if (last)
			next = {
				revision: row.seq,
				turns: [
					...next.turns.slice(0, -1),
					{
						...last,
						answer: last.answer + row.body,
						failure: row.failure,
						state: row.kind === "delta" ? "running" : row.kind,
					},
				],
			};
	}
	return next;
}
