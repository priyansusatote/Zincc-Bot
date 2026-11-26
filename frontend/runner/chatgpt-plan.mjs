// Device-local public client. The control plane never receives provider credentials.
import {
	createHash,
	createPublicKey,
	randomBytes,
	randomUUID,
	verify,
} from "node:crypto";
import { createServer } from "node:http";
import { mkdir, open, readFile, rename, unlink } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { parseArgs } from "node:util";

const issuer = "https://auth.openai.com";
const resource = "https://api.openai.com/v1";
const planScope = "chatgpt.tokens.use.direct";
const random = () => randomBytes(32).toString("base64url");

export function registration(redirect, host, previous) {
	const state = random(),
		nonce = random(),
		verifier = random();
	const url = new URL("/api/accounts/authorize", issuer);
	const client = previous?.client ?? "dynamic_agent_client";
	url.search = new URLSearchParams({
		response_type: "code",
		client_id: client,
		redirect_uri: redirect,
		ext_agent_host_id: host,
		resource,
		state,
		nonce,
		scope: `openid profile email offline_access resource.invoke ${planScope}`,
		code_challenge_method: "S256",
		code_challenge: createHash("sha256").update(verifier).digest("base64url"),
		...(previous ? {} : { agent_name_hint: "Zincc" }),
	}).toString();
	return { url, state, nonce, verifier, redirect, previous };
}

export function callback(query, attempt) {
	for (const key of ["state", "code", "client_id", "error"]) {
		if (query.getAll(key).length > 1) throw Error("Ambiguous callback");
	}
	if (query.get("state") !== attempt.state || query.has("error"))
		throw Error("Authorization declined or expired");
	const client = query.get("client_id") ?? attempt.previous?.client;
	const code = query.get("code");
	if (
		!client ||
		client === "dynamic_agent_client" ||
		client.length > 256 ||
		!code ||
		code.length > 8192 ||
		(attempt.previous && client !== attempt.previous.client)
	)
		throw Error("Registration mismatch");
	return { client, code };
}

export function identity(
	token,
	keys,
	client,
	attempt,
	now = Date.now() / 1000,
) {
	const pieces = token.split(".");
	if (pieces.length !== 3) throw Error("Invalid ID token");
	const [header, claims] = pieces
		.slice(0, 2)
		.map((s) => JSON.parse(Buffer.from(s, "base64url")));
	const key = keys.find(
		(k) =>
			k.kid === header.kid &&
			k.kty === "RSA" &&
			(!k.use || k.use === "sig") &&
			(!k.alg || k.alg === "RS256"),
	);
	if (
		header.alg !== "RS256" ||
		!key ||
		!verify(
			"RSA-SHA256",
			Buffer.from(pieces.slice(0, 2).join(".")),
			createPublicKey({ key, format: "jwk" }),
			Buffer.from(pieces[2], "base64url"),
		)
	)
		throw Error("ID token signature rejected");
	const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
	if (
		claims.iss !== issuer ||
		!audiences.includes(client) ||
		((audiences.length > 1 || claims.azp) && claims.azp !== client) ||
		!Number.isFinite(claims.exp) ||
		claims.exp <= now ||
		(claims.nbf !== undefined &&
			(!Number.isFinite(claims.nbf) || claims.nbf > now)) ||
		claims.nonce !== attempt.nonce ||
		typeof claims.sub !== "string" ||
		!claims.sub
	)
		throw Error("ID token claims rejected");
	const subject = createHash("sha256").update(claims.sub).digest("hex");
	if (attempt.previous && subject !== attempt.previous.subject)
		throw Error("Different provider account; choose another profile");
	return subject;
}

async function jsonRequest(url, options = {}) {
	const res = await fetch(url, {
		...options,
		redirect: "error",
		signal: AbortSignal.timeout(20000),
	});
	if (!res.ok) throw Error(`Provider request failed (HTTP ${res.status})`);
	return res.json();
}

async function loadJson(path) {
	try {
		return JSON.parse(await readFile(path, "utf8"));
	} catch (e) {
		if (e.code === "ENOENT") return null;
		throw e;
	}
}

async function signIn(host, previous) {
	let attempt, settle, reject;
	const response = new Promise((yes, no) => {
		settle = yes;
		reject = no;
	});
	const server = createServer((req, res) => {
		const uri = new URL(req.url, "http://127.0.0.1");
		res.setHeader("Cache-Control", "no-store");
		res.setHeader("Referrer-Policy", "no-referrer");
		if (
			!attempt ||
			req.method !== "GET" ||
			req.headers.host !== new URL(attempt.redirect).host ||
			uri.pathname !== "/auth/callback"
		) {
			res.writeHead(404).end();
			return;
		}
		if (uri.searchParams.get("state") !== attempt.state) {
			res.writeHead(400).end();
			return;
		}
		try {
			const code = callback(uri.searchParams, attempt);
			res.end("Return to Zincc to finish the check.");
			settle(code);
		} catch (e) {
			res.writeHead(400).end("Authorization declined.");
			reject(e);
		}
	});
	await new Promise((yes, no) => {
		server.once("error", no);
		server.listen(0, "127.0.0.1", yes);
	});
	const timer = setTimeout(() => reject(Error("Sign-in timed out")), 180000);
	let code, client;
	try {
		attempt = registration(
			`http://127.0.0.1:${server.address().port}/auth/callback`,
			host,
			previous,
		);
		console.log(
			"Continue with ChatGPT in your browser to authorize this local Zincc connection.\n" +
				attempt.url.href,
		);
		({ code, client } = await response);
	} finally {
		clearTimeout(timer);
		server.close();
		server.closeAllConnections();
	}
	const token = await jsonRequest(issuer + "/api/accounts/oauth/token", {
		method: "POST",
		body: new URLSearchParams({
			grant_type: "authorization_code",
			code,
			client_id: client,
			code_verifier: attempt.verifier,
			redirect_uri: attempt.redirect,
			resource,
		}),
	});
	const jwks = await jsonRequest(issuer + "/.well-known/jwks.json");
	const subject = identity(token.id_token, jwks.keys, client, attempt);
	if (
		!String(token.scope).split(/\s+/).includes(planScope) ||
		typeof token.access_token !== "string" ||
		!token.access_token
	)
		throw Error("Plan-use permission unavailable");
	return { client, subject, access: token.access_token };
}

export function catalog(data) {
	if (!Array.isArray(data.models)) throw Error("Invalid account catalog");
	return data.models
		.filter((m) => m.visibility === "list" && typeof m.slug === "string")
		.map((m) => m.slug);
}

export async function completedText(body, model) {
	let pending = "",
		size = 0,
		data = [];
	const utf8 = new TextDecoder("utf-8", { fatal: true });
	function frame(line) {
		if (line.startsWith("data:")) {
			data.push(line.slice(5).trimStart());
			return;
		}
		if (line || !data.length) return;
		const payload = data.join("\n");
		data = [];
		if (payload === "[DONE]") return;
		const event = JSON.parse(payload);
		if (
			["error", "response.failed", "response.incomplete"].includes(event.type)
		)
			throw Error("Provider did not complete this turn");
		if (event.type !== "response.completed") return;
		if (
			event.response?.status !== "completed" ||
			event.response.model !== model
		)
			throw Error("Effective model or completion status unconfirmed");
		const text = event.response.output
			.filter((i) => i.type === "message" && i.role === "assistant")
			.flatMap((i) => i.content)
			.filter((c) => c.type === "output_text")
			.map((c) => c.text)
			.join("");
		if (!text || text.length > 32000) throw Error("No bounded text result");
		return text;
	}
	for await (const chunk of body) {
		size += chunk.byteLength;
		if (size > 2_000_000) throw Error("Provider stream exceeded limit");
		pending += utf8.decode(chunk, { stream: true });
		let eol;
		while ((eol = pending.indexOf("\n")) >= 0) {
			const line = pending.slice(0, eol).replace(/\r$/, "");
			pending = pending.slice(eol + 1);
			const text = frame(line);
			if (text !== undefined) return text;
		}
	}
	throw Error("Provider stream ended before completion");
}

export async function openPlan(profileName) {
	if (!/^[a-zA-Z0-9_-]{1,48}$/.test(profileName))
		throw Error("Invalid local profile name");
	if (/^(1|true)$/i.test(process.env.ZINCC_LOCAL_ONLY ?? ""))
		throw Error("Cloud connection disabled by local-only policy");
	const dir = join(homedir(), ".zincc", "plan-connections");
	await mkdir(dir, { recursive: true, mode: 0o700 });
	// Serialize registration on this host. The file contains no provider credentials.
	const lock = join(dir, "register.lock");
	const guard = await open(lock, "wx", 0o600).catch((e) => {
		if (e.code === "EEXIST")
			throw Error(
				"Registration already locked. If the prior process crashed, remove its stale register.lock.",
			);
		throw e;
	});
	try {
		const hostPath = join(dir, "host.json"),
			profile = join(dir, profileName + ".account.json");
		let host = await loadJson(hostPath);
		if (!host) {
			host = { id: randomUUID() };
			await atomicJson(hostPath, host);
		}
		const result = await signIn(host.id, await loadJson(profile));
		await atomicJson(profile, {
			client: result.client,
			subject: result.subject,
		});
		const models = catalog(
			await jsonRequest(resource + "/models", {
				headers: { Authorization: "Bearer " + result.access },
			}),
		);
		return {
			provider: "chatgpt-plan",
			models,
			async run(model, instructions, input, signal) {
				if (!models.includes(model))
					throw Error("Selected model is unavailable on this account");
				const response = await fetch(resource + "/responses", {
					method: "POST",
					redirect: "error",
					signal: AbortSignal.any([signal, AbortSignal.timeout(60000)]),
					headers: {
						Authorization: "Bearer " + result.access,
						"Content-Type": "application/json",
					},
					body: JSON.stringify({
						model,
						instructions,
						input: [{ role: "user", content: input }],
						store: false,
						stream: true,
					}),
				});
				if (!response.ok) {
					const category = {
						401: "authorization",
						403: "authorization",
						429: "allowance",
					}[response.status];
					throw Object.assign(
						Error("Provider returned HTTP " + response.status),
						{ category },
					);
				}
				return completedText(response.body, model);
			},
		};
	} finally {
		await guard.close();
		await unlink(lock);
	}
}

async function main() {
	const { values } = parseArgs({
		options: {
			profile: { type: "string", default: "default" },
			model: { type: "string" },
		},
	});
	const plan = await openPlan(values.profile);
	if (!values.model) {
		console.log(
			JSON.stringify({ billing: "ChatGPT plan", models: plan.models }),
		);
		return;
	}
	if (process.stdin.isTTY) throw Error("Supply one text request on stdin");
	let input = "";
	process.stdin.setEncoding("utf8");
	for await (const chunk of process.stdin) {
		input += chunk;
		if (input.length > 8000) throw Error("Input exceeds 8000 characters");
	}
	if (!input.trim()) throw Error("Empty request");
	console.log(
		await plan.run(values.model, "", input, new AbortController().signal),
	);
}

async function atomicJson(path, value) {
	const tmp = path + "." + randomUUID() + ".tmp";
	const file = await open(tmp, "wx", 0o600);
	try {
		await file.writeFile(JSON.stringify(value));
	} finally {
		await file.close();
	}
	await rename(tmp, path);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href)
	main().catch((e) => {
		console.error(
			e instanceof SyntaxError ? "Provider returned malformed JSON" : e.message,
		);
		process.exitCode = 1;
	});
