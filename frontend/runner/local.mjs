import { opendir, realpath, stat } from "node:fs/promises";
import { resolve } from "node:path";
import { parseArgs } from "node:util";
import { pathToFileURL } from "node:url";
import { createInterface } from "node:readline/promises";
import { setTimeout as sleep } from "node:timers/promises";
import { chromium } from "@playwright/test";

export async function inventory(folder, signal) {
	signal.throwIfAborted();
	const dir = await opendir(folder);
	let count = 0;
	let visited = 0;
	for await (const item of dir) {
		signal.throwIfAborted();
		if (++visited > 10000) throw new Error("Folder exceeds listing limit");
		if (item.isFile()) count++;
	}
	return count;
}

const reviewPage = `<!doctype html><html lang="en"><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'unsafe-inline';form-action 'none'">
<title>Zincc draft review</title><style>body{font:20px system-ui;max-width:34rem;margin:12vh auto}button,select{font:inherit;padding:.5em}h1{font-size:2rem}</style>
<h1>Review briefing priority</h1><p id="state">Zincc is preparing your draft.</p>
<label>Priority <select disabled><option>Low</option><option>Normal</option><option>High</option></select></label>
<button disabled>Return control to Zincc</button></html>`;

export async function reviewBrowser(
	signal,
	channel,
	onHandoff,
	headless = false,
) {
	signal.throwIfAborted();
	const browser = await chromium.launch({
		channel,
		headless,
		chromiumSandbox: true,
		timeout: 12000,
	});
	const abort = () => {
		void browser.close().catch(() => {});
	};
	signal.addEventListener("abort", abort, { once: true });
	try {
		signal.throwIfAborted();
		const context = await browser.newContext({
			offline: true,
			acceptDownloads: false,
			serviceWorkers: "block",
		});
		await context.route("**/*", (route) => route.abort());
		const page = await context.newPage();
		await page.setContent(reviewPage);
		// This transition transfers ownership before enabling human input. No queued agent gestures survive it.
		await page.evaluate(() => {
			const select = document.querySelector("select");
			const button = document.querySelector("button");
			select.selectedIndex = 1;
			select.disabled = false;
			document.querySelector("#state").textContent =
				"You have control. Choose a priority and return control when ready.";
			button.disabled = false;
			button.onclick = () => {
				if (button.disabled) return;
				select.disabled = true;
				button.disabled = true;
				document.documentElement.dataset.reviewed = "yes";
				document.querySelector("#state").textContent =
					"Review returned to Zincc.";
			};
		});
		await onHandoff?.(page);
		await page.waitForFunction(
			() => document.documentElement.dataset.reviewed === "yes",
			null,
			{ timeout: 285000 },
		);
		signal.throwIfAborted();
		// Fresh observation after handoff. The only exportable receipt is the selected enum index.
		return await page
			.locator("select:disabled")
			.evaluate((el) => el.selectedIndex);
	} finally {
		signal.removeEventListener("abort", abort);
		await browser.close();
	}
}

async function main() {
	const { values } = parseArgs({
		options: {
			server: { type: "string", default: "http://127.0.0.1:8080" },
			folder: { type: "string" },
			browser: { type: "boolean", default: false },
			channel: {
				type: "string",
				default: process.platform === "win32" ? "msedge" : "chromium",
			},
		},
	});
	const url = new URL(values.server);
	if (
		url.username ||
		url.password ||
		url.pathname !== "/" ||
		url.search ||
		url.hash ||
		!(
			url.protocol === "https:" ||
			(url.protocol === "http:" &&
				["localhost", "127.0.0.1", "[::1]"].includes(url.hostname))
		)
	)
		throw new Error("Use a server HTTPS origin or loopback HTTP");
	if (!values.browser && !values.folder)
		throw new Error("Select --folder or --browser");
	const folder = values.folder ? await realpath(resolve(values.folder)) : null;
	const folderIdentity = folder ? await stat(folder, { bigint: true }) : null;
	if (folderIdentity && !folderIdentity.isDirectory())
		throw new Error("Select a directory");
	console.log(
		`Pairing with ${url.origin}. Grants: ${folder ? "top-level file count" : "no folder"}, ${values.browser ? "offline browser review" : "no browser"}. Ctrl+C disconnects.`,
	);
	const tty = createInterface({ input: process.stdin, output: process.stdout });
	const code = (await tty.question("One-time pairing code: ")).trim();
	tty.pause();
	const parts = code.split(".");
	if (parts.length !== 4) throw new Error("Invalid pairing code");
	const [workspace, owner, device] = parts;
	let token = parts[3];
	const shutdown = new AbortController();
	process.once("SIGINT", () => shutdown.abort());
	process.once("SIGTERM", () => shutdown.abort());
	const exchange = async (action, fields = {}) => {
		const res = await fetch(new URL("/api/device/exchange", url), {
			method: "POST",
			redirect: "error",
			headers: {
				"Content-Type": "application/json",
				Authorization: "Bearer " + token,
			},
			signal: AbortSignal.any([shutdown.signal, AbortSignal.timeout(5000)]),
			body: JSON.stringify({
				workspace,
				owner,
				device,
				action,
				folder: Boolean(folder),
				browser: values.browser,
				...fields,
			}),
		});
		if (!res.ok)
			throw new Error("Device request declined (" + res.status + ")");
		return res.json();
	};
	const pair = await exchange("pair", {
		folder: Boolean(folder),
		browser: values.browser,
	});
	token = pair.credential;
	let paused = false;
	let running;
	tty.on("line", (line) => {
		switch (line.trim().toLowerCase()) {
			case "pause":
				paused = true;
				running?.abort();
				console.log("Paused locally.");
				break;
			case "resume":
				paused = false;
				console.log("Resumed locally.");
				break;
			case "quit":
				shutdown.abort();
				break;
		}
	});
	tty.on("SIGINT", () => shutdown.abort());
	tty.resume();
	console.log(
		"Paired. Queue a job in Customer runners. Type pause, resume or quit.",
	);
	try {
		while (!shutdown.signal.aborted) {
			if (paused) {
				await sleep(500, undefined, { signal: shutdown.signal });
				continue;
			}
			const assignment = await exchange("poll");
			if (assignment.state === "leased") {
				if (paused) {
					await exchange("failed", { job: assignment.job });
					continue;
				}
				const task = new AbortController();
				running = task;
				const signal = AbortSignal.any([task.signal, shutdown.signal]);
				const job = assignment.job;
				const heartbeat = (async () => {
					try {
						while (!signal.aborted) {
							await sleep(3500, undefined, { signal });
							const response = await exchange("renew", { job });
							if (response.state !== "leased") throw new Error("Lease ended");
						}
					} catch {
						task.abort();
					}
				})();
				try {
					let result;
					if (assignment.kind === "inventory" && folder) {
						const unchanged = async () => {
							const current = await stat(folder, { bigint: true });
							return (
								current.dev === folderIdentity.dev &&
								current.ino === folderIdentity.ino &&
								(await realpath(folder)) === folder
							);
						};
						if (!(await unchanged())) throw new Error("Folder grant changed");
						result = await inventory(folder, signal);
						if (!(await unchanged())) throw new Error("Folder grant changed");
					} else if (assignment.kind === "browser" && values.browser) {
						result = await reviewBrowser(signal, values.channel);
					} else throw new Error("Local grant unavailable");
					signal.throwIfAborted();
					await exchange("complete", { job, result });
				} catch {
					if (!shutdown.signal.aborted && (paused || !signal.aborted)) {
						console.error(
							"Job ended locally. Check the folder grant or browser availability.",
						);
						await exchange("failed", { job });
					}
				} finally {
					task.abort();
					await heartbeat;
					running = undefined;
				}
			}
			await sleep(2000, undefined, { signal: shutdown.signal });
		}
	} finally {
		tty.close();
	}
}
if (
	process.argv[1] &&
	import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
	main().catch((e) => {
		if (e.name !== "AbortError") {
			console.error(e.message);
			process.exitCode = 1;
		}
	});
}
