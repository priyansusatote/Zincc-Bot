import { spawn, spawnSync } from "node:child_process";
import { isAbsolute, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createInterface } from "node:readline";

export async function openCli(provider, binary) {
	if (!binary || !isAbsolute(binary))
		throw Error("Supply --binary with the installed CLI's absolute path");
	const windows = process.platform === "win32";
	const script = fileURLToPath(
		new URL("../../tools/device_engine.py", import.meta.url),
	);
	const child = spawn(
		windows ? "python" : "python3",
		[
			"-I",
			"-u",
			"-c",
			"import runpy,sys,pathlib; p=sys.argv.pop(1); sys.path.insert(0,str(pathlib.Path(p).parent)); runpy.run_path(p,run_name='__main__')",
			script,
			provider,
			binary,
		],
		{
			windowsHide: true,
			stdio: ["pipe", "pipe", "ignore"],
			detached: !windows,
		},
	);
	const lines = createInterface({ input: child.stdout, crlfDelay: Infinity })[
		Symbol.asyncIterator
	]();
	child.stdin.on("error", () => {});
	let closed = false;
	function close() {
		if (closed) return;
		closed = true;
		if (child.pid && child.exitCode === null && child.signalCode === null) {
			if (windows)
				spawnSync(
					join(process.env.SystemRoot, "System32", "taskkill.exe"),
					["/PID", String(child.pid), "/T", "/F"],
					{ windowsHide: true, stdio: "ignore", timeout: 5000 },
				);
			else {
				try {
					process.kill(-child.pid, "SIGTERM");
				} catch {
					/* already exited */
				}
			}
		}
		child.stdin.destroy();
		process.off("exit", close);
	}
	child.on("error", close);
	process.once("exit", close);
	async function reply(message, signal) {
		signal.throwIfAborted();
		signal.addEventListener("abort", close, { once: true });
		try {
			if (message) child.stdin.write(JSON.stringify(message) + "\n");
			const line = await lines.next();
			if (line.done || line.value.length > 250000)
				throw Error("Device engine disconnected");
			const data = JSON.parse(line.value);
			if (data.failure)
				throw Object.assign(Error("Provider check: " + data.failure), {
					category: data.failure,
				});
			return data;
		} finally {
			signal.removeEventListener("abort", close);
		}
	}
	try {
		const { models } = await reply(null, AbortSignal.timeout(260000));
		if (!Array.isArray(models) || !models.length)
			throw Error("No models available for this profile");
		return {
			provider,
			models,
			close,
			async prepare(model, signal) {
				if ((await reply({ model }, signal)).acknowledged !== model)
					throw Error("Device rejected model selection");
			},
			async run(_model, instructions, goal, signal) {
				const { answer } = await reply({ instructions, goal }, signal);
				if (typeof answer !== "string" || !answer.trim())
					throw Error("Incomplete device answer");
				return answer;
			},
		};
	} catch (e) {
		close();
		throw e;
	}
}
