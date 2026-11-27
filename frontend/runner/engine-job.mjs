import { setTimeout as sleep } from "node:timers/promises";

// A lost claim response is ambiguous. Only the next *unclaimed* offer can run.
export async function runOffered(engine, assignment, exchange, signal) {
	const { job, model } = assignment;
	if (!job || !engine.models.includes(model))
		throw Error("Device catalog changed");
	const event = { model };
	await engine.prepare?.(
		model,
		AbortSignal.any([signal, AbortSignal.timeout(55000)]),
	);
	let claimed;
	try {
		claimed = await exchange("engine-claim", { job, event });
	} catch (error) {
		engine.close?.();
		throw error;
	}
	if (
		claimed.state !== "claimed" ||
		claimed.job !== job ||
		claimed.model !== model ||
		typeof claimed.instructions !== "string" ||
		typeof claimed.goal !== "string"
	) {
		engine.close?.();
		throw Error("Device claim declined");
	}
	const stop = new AbortController();
	const active = AbortSignal.any([
		stop.signal,
		signal,
		AbortSignal.timeout(85000),
	]);
	const heartbeat = (async () => {
		try {
			while (!active.aborted) {
				await sleep(3000, undefined, { signal: active });
				const lease = await exchange("engine-renew", { job, event });
				if (lease.state !== "active") break;
			}
		} catch {
			/* Loss of contact ends this grant. */
		}
		stop.abort();
	})();
	try {
		active.throwIfAborted();
		let answer;
		let allowance;
		try {
			answer = await engine.run(
				model,
				claimed.instructions,
				claimed.goal,
				active,
				(reading) => {
					allowance = reading;
				},
			);
		} catch (error) {
			if (!active.aborted)
				await exchange("engine-failed", {
					job,
					event: {
						model,
						failure: error.category ?? "unconfirmed",
						...(allowance && { allowance }),
					},
				});
			return;
		}
		active.throwIfAborted();
		await exchange("engine-complete", {
			job,
			event: { model, answer, ...(allowance && { allowance }) },
		});
	} finally {
		stop.abort();
		await heartbeat;
	}
}
