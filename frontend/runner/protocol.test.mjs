import test from "node:test";
import assert from "node:assert/strict";
import { once } from "node:events";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

test("foreground client redeems once and uses its device credential for polls and receipts", {
	timeout: 15000,
}, async () => {
	const folder = await mkdtemp(join(tmpdir(), "zincc-wire-"));
	const actions = [];
	const received = Promise.withResolvers();
	const pairKey = "a".repeat(43);
	const deviceKey = "b".repeat(43);
	const server = createServer(async (req, res) => {
		try {
			assert.equal(req.url, "/api/device/exchange");
			let text = "";
			for await (const chunk of req) text += chunk;
			const event = JSON.parse(text);
			assert.equal(event.folder, true);
			assert.equal(event.browser, false);
			assert.ok(!text.includes(folder));
			actions.push(event.action);
			assert.equal(
				req.headers.authorization,
				"Bearer " + (event.action === "pair" ? pairKey : deviceKey),
			);
			const reply =
				event.action === "pair"
					? { state: "active", credential: deviceKey }
					: event.action === "poll"
						? { state: "leased", job: "test-job", kind: "inventory" }
						: { state: "complete" };
			res.writeHead(200, { "Content-Type": "application/json" });
			res.end(JSON.stringify(reply));
			if (event.action === "complete") {
				assert.equal(event.result, 1);
				received.resolve();
			}
		} catch (e) {
			res.writeHead(400);
			res.end();
			received.reject(e);
		}
	});
	let child;
	try {
		await writeFile(join(folder, "sample"), "fixture");
		server.listen(0, "127.0.0.1");
		await once(server, "listening");
		child = spawn(
			process.execPath,
			[
				fileURLToPath(new URL("./local.mjs", import.meta.url)),
				"--folder",
				folder,
				"--server",
				"http://127.0.0.1:" + server.address().port,
			],
			{ stdio: ["pipe", "pipe", "ignore"], windowsHide: true },
		);
		child.stdout.on("data", (chunk) => {
			if (chunk.toString().includes("One-time pairing code"))
				child.stdin.write("workspace.owner.device." + pairKey + "\n");
		});
		const exited = once(child, "exit").then(() => {
			throw Error("Runner exited before completion");
		});
		await Promise.race([received.promise, exited]);
		assert.deepEqual(actions, ["pair", "poll", "complete"]);
	} finally {
		child?.kill();
		server.closeAllConnections();
		server.close();
		await rm(folder, { recursive: true, force: true });
	}
});
