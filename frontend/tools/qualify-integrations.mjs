import assert from "node:assert/strict";
import { chromium, expect } from "@playwright/test";

// Run in a dedicated local test workspace. The examples stay available for inspection.
const username = process.env.ZINCC_TEST_USERNAME;
const password = process.env.ZINCC_TEST_PASSWORD;
if (!username || !password)
	throw Error("Set test account environment variables.");
const browser = await chromium.launch({
	channel: process.env.ZINCC_BROWSER_CHANNEL || "msedge",
});
try {
	const page = await browser.newPage({ baseURL: "http://127.0.0.1:8080" });
	page.setDefaultTimeout(30000);
	await page.goto("/workbench/");
	await page.getByRole("link", { name: "Sign in" }).click();
	await page.locator("#username").fill(username);
	await page.locator("#password").fill(password);
	await page.locator("#kc-login").click();
	await page
		.getByText("Connections and routines", { exact: true })
		.first()
		.click();
	const panel = page.getByRole("region", {
		name: "Connections and routines",
		exact: true,
	});
	const name = (kind) => "P5 sample " + kind;
	const row = (label) =>
		panel
			.locator("li")
			.filter({ has: page.getByText(label, { exact: true }) })
			.first();
	async function account(p, label) {
		if ((await row(label).count()) === 0) {
			await panel.getByRole("checkbox", { name: "Use sample account" }).check();
			await panel
				.getByLabel(/^Provider/)
				.and(page.getByRole("combobox"))
				.selectOption(p);
			await panel.getByLabel("Account label").fill(label);
			await panel.getByRole("button", { name: "Add sample account" }).click();
		}
		await row(label).filter({ hasText: "ready" }).waitFor();
	}
	await panel.getByRole("heading", { name: "Connected accounts" }).waitFor();
	await account("gmail", name("inbox"));
	await account("slack", name("channel"));
	await account("calendar", name("calendar"));
	async function install(value) {
		await panel.getByLabel("Skill version").selectOption(value);
		const b = panel.getByRole("button", {
			name: "Approve permissions and install",
		});
		if (await b.count()) await b.click();
		await panel
			.getByRole("button", { name: "Installed", exact: true })
			.waitFor();
	}
	async function routine(label, trigger, feeds, dashboard) {
		if ((await row(label).count()) === 0) {
			await panel.getByLabel("Routine name").fill(label);
			await panel
				.getByLabel(/^Trigger/)
				.and(page.getByRole("combobox"))
				.selectOption(trigger);
			for (const f of feeds)
				await panel.getByRole("checkbox", { name: f, exact: true }).check();
			if (dashboard)
				await panel
					.getByLabel("Saved dashboard")
					.selectOption({ label: dashboard });
			await panel
				.getByRole("button", { name: "Create routine", exact: true })
				.click();
		}
		const resume = row(label).getByRole("button", {
			name: "Resume",
			exact: true,
		});
		if (await resume.count()) await resume.click();
		await row(label)
			.getByRole("button", { name: "Pause", exact: true })
			.waitFor();
	}
	function receipts(label) {
		return panel
			.locator("li")
			.filter({ has: page.locator("pre") })
			.filter({ has: page.getByText(label, { exact: true }) });
	}
	const counts = new Map();
	async function remember(label) {
		counts.set(label, await receipts(label).count());
	}
	async function receipt(label, text) {
		const entries = receipts(label);
		if (counts.has(label))
			await expect(entries).toHaveCount(counts.get(label) + 1, {
				timeout: 60000,
			});
		await expect(entries.first()).toContainText(text, { timeout: 60000 });
		assert.match(await entries.first().innerText(), /complete/);
	}
	async function pause(label) {
		await row(label)
			.getByRole("button", { name: "Pause", exact: true })
			.click();
		await row(label)
			.getByRole("button", { name: "Resume", exact: true })
			.waitFor();
	}
	await install("briefing-2");
	const feeds = ["inbox", "channel", "calendar"].map(name);
	await routine(name("daily briefing"), "daily", feeds);
	await remember(name("daily briefing"));
	await row(name("daily briefing"))
		.getByRole("button", { name: "Run now" })
		.click();
	await receipt(name("daily briefing"), "Release checklist is ready");
	await receipt(name("daily briefing"), "Invoice ready for review");
	await pause(name("daily briefing"));
	await routine(name("source changes"), "event", [feeds[0]]);
	await remember(name("source changes"));
	const accountRow = row(feeds[0]);
	await accountRow.getByText("Grant and sync", { exact: true }).click();
	await accountRow
		.getByRole("button", { name: "Simulate incoming item" })
		.click();
	await receipt(name("source changes"), "Sample update");
	await pause(name("source changes"));
	await install("report-1");
	await routine(
		name("report refresh"),
		"daily",
		[],
		process.env.ZINCC_TEST_DASHBOARD || "Invoice totals",
	);
	await remember(name("report refresh"));
	await row(name("report refresh"))
		.getByRole("button", { name: "Run now" })
		.click();
	await receipt(
		name("report refresh"),
		process.env.ZINCC_TEST_EXPECTED_TOTAL || "17.00 USD",
	);
	await pause(name("report refresh"));
	await page.reload();
	await page
		.getByText("Connections and routines", { exact: true })
		.first()
		.click();
	await receipt(name("report refresh"), "source page");
	console.log(
		"Passed: sample provider sync, approved skill, daily briefing, event delivery, dashboard refresh, pause and receipt persistence.",
	);
} finally {
	await browser.close();
}
