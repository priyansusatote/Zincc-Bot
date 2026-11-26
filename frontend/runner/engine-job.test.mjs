import test from "node:test";
import assert from "node:assert/strict";
import { runOffered } from "./engine-job.mjs";

const offer = { job: "test-run", model: "fast" };
const grant = {
	...offer,
	state: "claimed",
	instructions: "Reply briefly",
	goal: "Example",
};
test("the model acknowledges before the claim and returns one bounded receipt", async () => {
	const calls = [];
	const engine = {
		models: ["fast"],
		prepare: async (model) => calls.push("ack " + model),
		run: async (model, instructions, goal) => {
			assert.deepEqual(
				[model, instructions, goal],
				["fast", "Reply briefly", "Example"],
			);
			calls.push("inference");
			return "Ready";
		},
	};
	await runOffered(
		engine,
		offer,
		async (action, payload) => {
			calls.push(action);
			if (action === "engine-claim") {
				assert.equal(payload.event.answer, undefined);
				return grant;
			}
			assert.equal(payload.event.answer, "Ready");
			return {};
		},
		new AbortController().signal,
	);
	assert.deepEqual(calls, [
		"ack fast",
		"engine-claim",
		"inference",
		"engine-complete",
	]);
});
test("a changed grant or model cannot deliver the task", async () => {
	for (const claimed of [{ ...grant, model: "other" }, { state: "paused" }]) {
		let ran = false;
		await assert.rejects(
			runOffered(
				{
					models: ["fast"],
					run: () => {
						ran = true;
					},
				},
				offer,
				async () => claimed,
				new AbortController().signal,
			),
		);
		assert.equal(ran, false);
	}
});
test("quota failure sends a categorized receipt without retry", async () => {
	const sent = [];
	await runOffered(
		{
			models: ["fast"],
			run() {
				throw Object.assign(Error("private provider detail"), {
					category: "allowance",
				});
			},
		},
		offer,
		async (action, payload) => {
			sent.push([action, payload]);
			return grant;
		},
		new AbortController().signal,
	);
	assert.deepEqual(sent[1], [
		"engine-failed",
		{ job: "test-run", event: { model: "fast", failure: "allowance" } },
	]);
	assert.equal(sent.length, 2);
});
