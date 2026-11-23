import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { inventory, reviewBrowser } from "./local.mjs";
test("local inventory counts direct files only", async () => {
	const root = await mkdtemp(join(tmpdir(), "zincc-p6-"));
	try {
		await mkdir(join(root, "sub"));
		await writeFile(join(root, "one.txt"), "fixture");
		await writeFile(join(root, "sub", "private.txt"), "fixture");
		assert.equal(await inventory(root, new AbortController().signal), 1);
		await assert.rejects(inventory(root, AbortSignal.abort()));
	} finally {
		await rm(root, { recursive: true, force: true });
	}
});
test("browser hands over the same page and starts clean on the next job", async () => {
	const channel = process.env.ZINCC_BROWSER_CHANNEL || "msedge";
	for (const priority of [2, 0]) {
		const result = await reviewBrowser(
			new AbortController().signal,
			channel,
			async (page) => {
				assert.equal(await page.locator("select").inputValue(), "Normal");
				assert.deepEqual(await page.context().cookies(), []);
				await page
					.context()
					.addCookies([
						{
							name: "session_fixture",
							value: "synthetic",
							url: "https://review.invalid",
						},
					]);
				assert.equal((await page.context().cookies()).length, 1);
				assert.equal(await page.evaluate(() => navigator.onLine), false);
				await page.locator("select").selectOption({ index: priority });
				await page
					.getByRole("button", { name: "Return control to Zincc" })
					.click();
				assert.equal(await page.locator("select").isDisabled(), true);
			},
			true,
		);
		assert.equal(result, priority);
	}
});
test("loss of lease closes human browser session", async () => {
	const controller = new AbortController();
	let session;
	await assert.rejects(
		reviewBrowser(
			controller.signal,
			process.env.ZINCC_BROWSER_CHANNEL || "msedge",
			async (page) => {
				session = page;
				controller.abort();
			},
			true,
		),
	);
	assert.ok(session?.isClosed());
});
