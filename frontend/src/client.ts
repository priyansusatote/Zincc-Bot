import createClient from "openapi-fetch";
import type { components, paths } from "./generated/api";

export type Session = components["schemas"]["Session"];
export type Membership = components["schemas"]["Membership"];
export const client = createClient<paths>({
	credentials: "same-origin",
	cache: "no-store",
});

export class ApiError extends Error {
	constructor(readonly status: number) {
		super(`Request failed (${status})`);
	}
}

export function check(response: Response) {
	if (!response.ok) throw new ApiError(response.status);
}

function validMembership(row: unknown): row is Membership {
	if (row === null || typeof row !== "object") return false;
	return (
		"workspaceId" in row &&
		typeof row.workspaceId === "string" &&
		/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(row.workspaceId) &&
		"workspaceName" in row &&
		typeof row.workspaceName === "string" &&
		"role" in row &&
		(row.role === "member" || row.role === "owner")
	);
}

export async function readSession(signal: AbortSignal): Promise<Session> {
	const { data, response } = await client.GET("/api/session", { signal });
	check(response);
	if (
		!data ||
		typeof data.accountLabel !== "string" ||
		typeof data.provisioned !== "boolean" ||
		typeof data.csrfHeader !== "string" ||
		!/^X-[A-Za-z0-9-]+$/i.test(data.csrfHeader) ||
		typeof data.csrfToken !== "string" ||
		!data.csrfToken
	)
		throw new Error("Invalid session response");
	return data;
}

export async function listWorkspaces(
	signal: AbortSignal,
): Promise<Membership[]> {
	const { data, response } = await client.GET("/api/workspaces", { signal });
	check(response);
	if (!Array.isArray(data) || !data.every(validMembership))
		throw new Error("Invalid workspace list");
	return data;
}

export async function readWorkspace(
	id: string,
	signal: AbortSignal,
): Promise<Membership> {
	const { data, response } = await client.GET("/api/workspaces/{workspaceId}", {
		params: { path: { workspaceId: id } },
		signal,
	});
	check(response);
	if (!validMembership(data) || data.workspaceId !== id)
		throw new Error("Unexpected workspace response");
	return data;
}

export async function endSession(session: Session) {
	const { response } = await client.POST("/api/logout", {
		headers: { [session.csrfHeader]: session.csrfToken },
	});
	check(response);
	if (response.status !== 204) throw new Error("Unexpected logout response");
}
