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
		await page.route(`**/api/workspaces/${workspace.workspaceId}`, (route) =>
			route.fulfill({ json: workspace }),
		);
	}
}

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
