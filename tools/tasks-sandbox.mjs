// Loopback Google Tasks protocol fixture. Task data survives process restarts.
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { randomUUID, timingSafeEqual } from "node:crypto";

const {
	ZINCC_TASKS_CLIENT_ID: clientId,
	ZINCC_TASKS_CLIENT_SECRET: clientSecret,
	ZINCC_TASKS_REFRESH_TOKEN: refresh,
	ZINCC_TASKS_FIXTURE_DIR: directory,
	ZINCC_TASKS_LIST: list,
} = process.env;
if (![clientId, clientSecret, refresh, directory, list].every(Boolean))
	throw Error("Missing fixture configuration");
const state = path.join(directory, "sandbox-tasks.json");
const tokens = new Map();
function equal(a, b) {
	const left = Buffer.from(a ?? "");
	const right = Buffer.from(b);
	return left.length === right.length && timingSafeEqual(left, right);
}
function read() {
	return fs.existsSync(state) ? JSON.parse(fs.readFileSync(state, "utf8")) : [];
}
async function handle(req, res) {
	function send(code, value) {
		res.writeHead(code, {
			"content-type": "application/json",
			"cache-control": "no-store",
		});
		res.end(JSON.stringify(value));
	}
	const url = new URL(req.url, "http://127.0.0.1");
	if (req.headers.host !== "127.0.0.1:18462") return send(400, {});
	let body = "";
	for await (const chunk of req) {
		body += chunk;
		if (body.length > 8192) return send(413, {});
	}
	if (req.method === "POST" && url.pathname === "/token") {
		const form = new URLSearchParams(body);
		if (
			form.get("grant_type") !== "refresh_token" ||
			!equal(form.get("client_id"), clientId) ||
			!equal(form.get("client_secret"), clientSecret) ||
			!equal(form.get("refresh_token"), refresh)
		)
			return send(401, { error: "invalid_grant" });
		for (const [t, expiry] of tokens)
			if (expiry <= Date.now()) tokens.delete(t);
		const token = randomUUID();
		tokens.set(token, Date.now() + 600000);
		return send(200, {
			access_token: token,
			token_type: "Bearer",
			expires_in: 600,
		});
	}
	if (
		!req.headers.authorization?.startsWith("Bearer ") ||
		!(tokens.get(req.headers.authorization.slice(7)) > Date.now())
	)
		return send(401, {});
	if (url.pathname !== "/tasks/v1/lists/" + list + "/tasks")
		return send(404, {});
	const tasks = read();
	if (req.method === "GET")
		return send(200, {
			kind: "tasks#tasks",
			items: tasks.slice(-100).reverse(),
		});
	if (req.method !== "POST") return send(405, {});
	const task = JSON.parse(body);
	if (
		typeof task.title !== "string" ||
		!task.title.trim() ||
		task.title.length > 250 ||
		typeof task.notes !== "string"
	)
		return send(400, {});
	const stored = {
		id: randomUUID(),
		title: task.title,
		notes: task.notes,
		status: "needsAction",
	};
	tasks.push(stored);
	fs.writeFileSync(state + ".tmp", JSON.stringify(tasks), { mode: 0o600 });
	fs.renameSync(state + ".tmp", state);
	return send(200, stored);
}
http
	.createServer((req, res) => {
		handle(req, res).catch(() => {
			if (!res.headersSent) res.writeHead(500);
			res.end();
		});
	})
	.listen(18462, "127.0.0.1");
