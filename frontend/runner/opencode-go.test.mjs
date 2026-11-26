import test from "node:test";
import assert from "node:assert/strict";
import { goUsage } from "./opencode-go.mjs";

test("reads provider usage windows using only the documented read endpoint", async () => {
	const meter = { status: "ok", percent: 4, resetsAt: "2026-11-27T11:00:00Z" };
	const result = await goUsage("fixture", async (url, opts) => {
		assert.equal(url, "https://opencode.ai/inference/go/v1/usage");
		assert.equal(opts.headers.Authorization, "Bearer fixture");
		assert.equal(opts.redirect, "error");
		return Response.json({
			usage: {
				rolling: meter,
				weekly: { ...meter, status: "rate-limited", percent: 100 },
				monthly: meter,
			},
		});
	});
	assert.equal(result.meters.weekly.percentUsed, 100);
	assert.equal(result.meters.weekly.status, "rate-limited");
});
test("rejects missing entitlement and malformed usage", async () => {
	await assert.rejects(
		goUsage("fixture", async () => new Response(null, { status: 403 })),
		/subscription unavailable/,
	);
	await assert.rejects(
		goUsage("fixture", async () => Response.json({ usage: {} })),
		/Incomplete/,
	);
});
