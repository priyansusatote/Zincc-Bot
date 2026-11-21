import { expect, test, type Page } from "@playwright/test";
import type { Membership, Session } from "../src/client";

const session: Session = {
	accountLabel: "Alex",
	provisioned: true,
	csrfHeader: "X-CSRF-TOKEN",
	csrfToken: "test-value",
};
const first: Membership = {
	workspaceId: "00000000-0000-4000-8000-000000000001",
	workspaceName: "Design",
	role: "owner",
};
const second: Membership = {
	workspaceId: "00000000-0000-4000-8000-000000000002",
	workspaceName: "Operations",
	role: "member",
};

async function ready(page: Page, workspaces: Membership[] = [first, second]) {
	await page.route("**/api/session", (route) =>
		route.fulfill({ json: session }),
	);
	await page.route("**/api/workspaces", (route) =>
		route.fulfill({ json: workspaces }),
	);
	for (const workspace of workspaces) {
		await page.route(
			`**/api/workspaces/${workspace.workspaceId}/conversations`,
			(route) => route.fulfill({ json: { threads: [], routes: [] } }),
		);
		await page.route(`**/api/workspaces/${workspace.workspaceId}`, (route) =>
			route.fulfill({ json: workspace }),
		);
	}
}

test("replay resumes from the snapshot, ignores duplicate deltas and recovers an expired cursor", async ({
	page,
}) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}/conversations`;
	const id = "10000000-0000-4000-8000-000000000001";
	const turn = {
		id: "20000000-0000-4000-8000-000000000001",
		route: "demo",
		question: "Status?",
		answer: "",
		state: "running",
	};
	await page.route(base, (r) =>
		r.fulfill({
			json: { threads: [{ id, title: "Status?" }], routes: ["demo"] },
		}),
	);
	let snapshots = 0;
	await page.route(`${base}/${id}`, (r) => {
		snapshots++;
		return r.fulfill({
			json:
				snapshots === 1
					? { revision: 1, turns: [turn] }
					: {
							revision: 150,
							turns: [{ ...turn, answer: "Saved reply", state: "interrupted" }],
						},
		});
	});
	const event = {
		seq: 2,
		turnId: turn.id,
		kind: "delta",
		body: "Café",
		route: "demo",
		at: "2026-11-21T01:00:00Z",
	};
	const frame = `id: 2\nevent: turn.v1\ndata: ${JSON.stringify(event)}\n\n`;
	await page.route(`${base}/${id}/events?after=1`, (r) =>
		r.fulfill({ contentType: "text/event-stream", body: frame + frame }),
	);
	let release: () => void = () => {};
	const gate = new Promise<void>((resolve) => {
		release = resolve;
	});
	await page.route(`${base}/${id}/events?after=2`, async (r) => {
		await gate;
		await r.fulfill({ status: 410 });
	});
	await page.goto("/workbench/");
	await expect(page.getByText("Café", { exact: true })).toBeVisible();
	release();
	await expect(page.getByText("Response interrupted")).toBeVisible();
	await expect(page.getByText("Saved reply")).toBeVisible();
});

test("uncertain message delivery retries the same command with CSRF", async ({
	page,
}) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}/conversations`;
	await page.route(base, (r) =>
		r.fulfill({ json: { threads: [], routes: ["demo"] } }),
	);
	await page.route(`${base}/*/events?*`, (r) =>
		r.fulfill({ contentType: "text/event-stream", body: "retry: 1500\n\n" }),
	);
	const sent: unknown[] = [];
	await page.route(`${base}/*/turns`, async (r) => {
		sent.push(r.request().postDataJSON());
		expect(await r.request().headerValue("X-CSRF-TOKEN")).toBe(
			session.csrfToken,
		);
		if (sent.length === 1) return r.abort("failed");
		return r.fulfill({ status: 204 });
	});
	await page.route(`${base}/*`, (r) =>
		r.fulfill({
			json: {
				revision: 2,
				turns: [
					{
						id: "20000000-0000-4000-8000-000000000002",
						question: "Hello",
						answer: "Hello back",
						state: "completed",
						route: "demo",
					},
				],
			},
		}),
	);
	await page.goto("/workbench/");
	await page.getByLabel("Model route").selectOption("demo");
	await page.getByLabel("Message", { exact: true }).fill("Hello");
	await page.getByRole("button", { name: "Send message" }).click();
	await page.getByRole("button", { name: "Retry message" }).click();
	await expect(page.getByText("Hello back")).toBeVisible();
	expect(sent).toHaveLength(2);
	expect(sent[1]).toEqual(sent[0]);
});

test("signed-out visitor can reach sign-in by keyboard", async ({ page }) => {
	await page.route("**/api/session", (r) => r.fulfill({ status: 401 }));
	await page.goto("/workbench/");
	const login = page.getByRole("link", { name: "Sign in", exact: true });
	await expect(login).toHaveAttribute("href", "/oauth2/authorization/zincc");
	await page.keyboard.press("Tab");
	await expect(
		page.getByRole("link", { name: "Skip to content" }),
	).toBeFocused();
	await page.keyboard.press("Enter");
	await page.keyboard.press("Tab");
	await expect(login).toBeFocused();
});

test("keyboard workspace switch rechecks access and renders the current role", async ({
	page,
}) => {
	await ready(page);
	await page.goto("/workbench/");
	await expect(page.getByText("owner", { exact: true })).toBeVisible();
	const picker = page.getByRole("combobox", { name: "Switch workspace" });
	await picker.focus();
	await page.keyboard.press("ArrowDown");
	await page.keyboard.press("Enter");
	await expect(page.getByRole("heading", { name: "Operations" })).toBeVisible();
	await expect(page.getByText("member", { exact: true })).toBeVisible();
	await expect(page.getByText("owner", { exact: true })).toHaveCount(0);
	await page.setViewportSize({ width: 360, height: 780 });
	expect(
		await page.evaluate(
			() => document.documentElement.scrollWidth <= innerWidth,
		),
	).toBeTruthy();
});

test("account switch signs out with CSRF before asking for a provider account", async ({
	page,
}) => {
	await ready(page);
	await page.route("**/api/logout", async (r) => {
		expect(r.request().method()).toBe("POST");
		expect(await r.request().headerValue("X-CSRF-TOKEN")).toBe(
			session.csrfToken,
		);
		await r.fulfill({ status: 204 });
	});
	await page.route("**/oauth2/authorization/zincc?account=switch", (r) =>
		r.fulfill({ body: "Provider account selection" }),
	);
	await page.goto("/workbench/");
	await page.getByRole("button", { name: "Account", exact: true }).click();
	await page
		.getByRole("button", { name: "Switch account", exact: true })
		.click();
	await expect(page).toHaveURL(/account=switch/);
});

test("empty directory and revoked workspace offer a refresh", async ({
	page,
}) => {
	await ready(page, [first]);
	await page.route(`**/api/workspaces/${first.workspaceId}`, (r) =>
		r.fulfill({ status: 404 }),
	);
	await page.goto("/workbench/");
	await expect(
		page.getByRole("heading", { name: "Workspace unavailable" }),
	).toBeVisible();
	await page.route("**/api/workspaces", (r) => r.fulfill({ json: [] }));
	await page.getByRole("button", { name: "Refresh workspaces" }).click();
	await expect(
		page.getByRole("heading", { name: "No workspaces assigned" }),
	).toBeVisible();
});

test("loading, invalid response and retry recover without exposing session data", async ({
	page,
}) => {
	let release: () => void = () => {};
	const gate = new Promise<void>((resolve) => {
		release = resolve;
	});
	await page.route("**/api/session", async (r) => {
		await gate;
		await r.fulfill({ json: {} });
	});
	await page.goto("/workbench/");
	await expect(page.getByRole("status")).toHaveText("Loading your workbench…");
	release();
	await expect(
		page.getByRole("heading", { name: "Couldn’t reach your workbench" }),
	).toBeVisible();
	await page.route("**/api/session", (r) =>
		r.fulfill({ json: { ...session, provisioned: false } }),
	);
	await page.getByRole("button", { name: "Try again" }).click();
	await expect(
		page.getByRole("heading", { name: "Workspace access pending" }),
	).toBeVisible();
});
