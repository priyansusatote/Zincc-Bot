import test from "node:test";
import assert from "node:assert/strict";
import { createHash, generateKeyPairSync, sign } from "node:crypto";
import {
	callback,
	registration,
	identity,
	catalog,
	completedText,
} from "./chatgpt-plan.mjs";

test("registration binds host, PKCE, callback state and returning client", () => {
	const a = registration("http://127.0.0.1:3001/auth/callback", "host");
	assert.equal(
		a.url.searchParams.get("code_challenge"),
		createHash("sha256").update(a.verifier).digest("base64url"),
	);
	const query = new URLSearchParams({
		state: a.state,
		code: "sample",
		client_id: "issued",
	});
	assert.equal(callback(query, a).client, "issued");
	assert.throws(() => callback(query, { ...a, state: "other" }));
	assert.throws(() => callback(query, { ...a, previous: { client: "other" } }));
	query.append("code", "duplicate");
	assert.throws(() => callback(query, a));
});

test("signed identity is bound to nonce, audience and stable account", () => {
	const { publicKey, privateKey } = generateKeyPairSync("rsa", {
		modulusLength: 2048,
	});
	const key = { ...publicKey.export({ format: "jwk" }), kid: "one" };
	const claims = {
		iss: "https://auth.openai.com",
		aud: "issued",
		nonce: "n",
		exp: 999,
		sub: "A",
	};
	const encode = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
	const jwt = (c) => {
		const body = encode({ kid: "one", alg: "RS256" }) + "." + encode(c);
		return (
			body +
			"." +
			sign("RSA-SHA256", Buffer.from(body), privateKey).toString("base64url")
		);
	};
	const attempt = { nonce: "n" };
	const token = jwt(claims);
	assert.ok(identity(token, [key], "issued", attempt, 10));
	for (const changed of [
		{ nonce: "old" },
		{ exp: 1 },
		{ aud: ["issued", "other"] },
		{ sub: "" },
	]) {
		assert.throws(() =>
			identity(jwt({ ...claims, ...changed }), [key], "issued", attempt, 10),
		);
	}
	assert.throws(() =>
		identity(
			token,
			[key],
			"issued",
			{ ...attempt, previous: { subject: "other" } },
			10,
		),
	);
});

test("catalog and stream bind completion to the explicitly selected model", async () => {
	assert.deepEqual(
		catalog({
			models: [
				{ slug: "x", visibility: "list" },
				{ slug: "y", visibility: "hidden" },
			],
		}),
		["x"],
	);
	const completed = {
		type: "response.completed",
		response: {
			model: "x",
			status: "completed",
			output: [
				{
					type: "message",
					role: "assistant",
					content: [{ type: "output_text", text: "ready" }],
				},
			],
		},
	};
	const bytes = Buffer.from("data: " + JSON.stringify(completed) + "\r\n\r\n");
	assert.equal(
		await completedText([bytes.subarray(0, 11), bytes.subarray(11)], "x"),
		"ready",
	);
	await assert.rejects(completedText([bytes], "y"), /unconfirmed/);
	await assert.rejects(
		completedText([Buffer.from("data: [DONE]\n\n")], "x"),
		/before completion/,
	);
});
