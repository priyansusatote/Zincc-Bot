import assert from "node:assert/strict";
import { chromium } from "@playwright/test";

// One real model request, using an existing ready sample feed on the local workbench.
const {
	ZINCC_TEST_USERNAME: username,
	ZINCC_TEST_PASSWORD: password,
	ZINCC_TEST_PLANNER_ROUTE: route,
} = process.env;
if (!username || !password || !route)
	throw Error("Configure the test account and planner route.");
const browser = await chromium.launch({
	channel: process.env.ZINCC_BROWSER_CHANNEL || "msedge",
});
try {
	const page = await browser.newPage({ baseURL: "http://127.0.0.1:8080" });
	await page.goto("/workbench/");
	await page.getByRole("link", { name: "Sign in", exact: true }).click();
	assert.equal(new URL(page.url()).origin, "http://127.0.0.1:18081");
	await page.locator("#username").fill(username);
	await page.locator("#password").fill(password);
	await page.locator("#kc-login").click();
	await page
		.getByRole("button", { name: "New conversation", exact: true })
		.click();
	const directory = page.waitForResponse((r) =>
		r.url().endsWith("/integrations/chat-sources"),
	);
	await page
		.getByRole("button", { name: "Connected apps", exact: true })
		.click();
	const response = await directory;
	assert.equal(response.status(), 200);
	const catalog = await response.json();
	assert.ok(catalog.routes.includes(route), "Requested route is unavailable.");
	const feed = catalog.sources.find((s) => s.fixture && s.ready);
	assert.ok(feed, "Sync a sample account before running qualification.");
	await page.getByLabel("App account").selectOption(feed.id);
	await page.getByLabel("Planner model").selectOption(route);
	await page
		.getByLabel("Request", { exact: true })
		.fill("List the latest items from this account.");
	const completion = page.waitForResponse(
		(r) => r.url().endsWith("/integrations/chat-read"),
		{ timeout: 75000 },
	);
	await page.getByRole("button", { name: "Run app request" }).click();
	const result = await completion;
	assert.equal(
		result.status(),
		200,
		"The selected planner did not complete the request.",
	);
	const { action, fixture, items } = await result.json();
	assert.equal(fixture, true);
	assert.equal(action, "read_recent");
	assert.ok(items.length > 0 && items.length <= 3);
	await page.getByRole("region", { name: "App result" }).waitFor();
	assert.equal(await page.locator(".app-result li").count(), items.length);
	console.log(
		JSON.stringify({
			route,
			action,
			sample: fixture,
			renderedItems: items.length,
		}),
	);
} finally {
	await browser.close();
}
