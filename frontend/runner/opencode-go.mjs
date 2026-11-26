import { pathToFileURL } from "node:url";

// The usage endpoint verifies Go entitlement independently of public model discovery.
export async function goUsage(key, request = fetch) {
	if (!key?.trim()) throw Error("Set ZINCC_OPENCODE_GO_KEY in this terminal");
	if (/^(1|true)$/i.test(process.env.ZINCC_LOCAL_ONLY ?? ""))
		throw Error("Local-only policy is active");
	const response = await request("https://opencode.ai/inference/go/v1/usage", {
		headers: { Authorization: "Bearer " + key, "User-Agent": "Zincc/0.1" },
		redirect: "error",
		signal: AbortSignal.timeout(10000),
	});
	if (response.status === 401) throw Error("Go credential declined");
	if (response.status === 403)
		throw Error("Go subscription unavailable for this key");
	if (!response.ok)
		throw Error("Go usage check returned HTTP " + response.status);
	const { usage } = await response.json();
	const meters = {};
	for (const name of ["rolling", "weekly", "monthly"]) {
		const meter = usage?.[name];
		if (
			!meter ||
			!["ok", "rate-limited"].includes(meter.status) ||
			!Number.isInteger(meter.percent) ||
			meter.percent < 0 ||
			meter.percent > 100 ||
			typeof meter.resetsAt !== "string" ||
			!Number.isFinite(Date.parse(meter.resetsAt))
		) {
			throw Error("Incomplete provider usage report");
		}
		meters[name] = {
			status: meter.status,
			percentUsed: meter.percent,
			resetsAt: meter.resetsAt,
		};
	}
	return {
		provider: "OpenCode Go",
		checkedAt: new Date().toISOString(),
		meters,
	};
}

if (
	process.argv[1] &&
	import.meta.url === pathToFileURL(process.argv[1]).href
) {
	goUsage(process.env.ZINCC_OPENCODE_GO_KEY)
		.then((result) => console.log(JSON.stringify(result, null, 2)))
		.catch((error) => {
			console.error(error.message);
			process.exitCode = 1;
		});
}
