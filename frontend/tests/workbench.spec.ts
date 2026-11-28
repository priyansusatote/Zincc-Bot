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

test("workspace navigation keeps the chat draft and fits a phone", async ({
	page,
}, info) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}`;
	const threads = ["Morning briefing", "Quarterly report", "Design review"].map(
		(title, i) => ({ id: `10000000-0000-4000-8000-00000000001${i}`, title }),
	);
	await page.route(`${base}/conversations`, (r) =>
		r.fulfill({
			json: {
				threads,
				routes: ["local"],
				presets: {},
				agentsEnabled: true,
				publicResearch: true,
				knowledgeEnabled: true,
			},
		}),
	);
	await page.route(`${base}/conversations/${threads[0].id}`, (r) =>
		r.fulfill({ json: { revision: 0, turns: [] } }),
	);
	await page.goto("/workbench/");
	await page
		.getByRole("button", { name: "New conversation", exact: true })
		.click();
	await expect(
		page.getByRole("heading", { name: "What are we working on?" }),
	).toBeVisible();
	await page.emulateMedia({ colorScheme: "dark" });
	await page.evaluate(() => document.fonts.ready);
	await page.screenshot({ path: info.outputPath("desktop.png") });
	await page
		.getByLabel("Message", { exact: true })
		.fill("Compare both reports");
	await page.getByRole("button", { name: "Research a topic" }).click();
	await expect(page.getByLabel("Search English Wikipedia")).toBeVisible();
	await page.getByRole("button", { name: "Chat", exact: true }).click();
	await expect(page.getByLabel("Message", { exact: true })).toHaveValue(
		"Compare both reports",
	);
	await page.setViewportSize({ width: 390, height: 844 });
	await page.getByRole("button", { name: "Workspace menu" }).click();
	await page.getByRole("button", { name: "Public web research" }).click();
	await expect(page.getByLabel("Search English Wikipedia")).toBeVisible();
	await expect(
		page.getByRole("button", { name: "Workspace menu" }),
	).toHaveAttribute("aria-expanded", "false");
	await page.getByRole("button", { name: "Workspace menu" }).click();
	await page
		.getByRole("button", { name: "New conversation", exact: true })
		.click();
	expect(
		await page.evaluate(
			() => document.documentElement.scrollWidth <= innerWidth,
		),
	).toBeTruthy();
	await page.screenshot({ path: info.outputPath("mobile.png") });
});

test("chat reads only the selected app and keeps private results out of ordinary chat", async ({
	page,
}) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}`;
	const feed = "40000000-0000-4000-8000-000000000003";
	await page.route(`${base}/conversations`, (r) =>
		r.fulfill({ json: { threads: [], routes: ["desk"], agentsEnabled: true } }),
	);
	await page.route(`${base}/integrations/chat-sources`, (r) =>
		r.fulfill({
			json: {
				sources: [
					{
						id: feed,
						provider: "gmail",
						label: "Personal inbox",
						fixture: true,
						ready: true,
						syncedAt: "2026-11-27T12:00:00Z",
					},
				],
				routes: ["desk"],
			},
		}),
	);
	let attempts = 0;
	await page.route(`${base}/integrations/chat-read`, async (r) => {
		expect(await r.request().headerValue(session.csrfHeader)).toBe(
			session.csrfToken,
		);
		expect(r.request().postDataJSON()).toEqual({
			feed,
			route: "desk",
			question: "List my latest subjects",
		});
		attempts++;
		if (attempts > 1) return r.fulfill({ status: 409 });
		return r.fulfill({
			json: {
				action: "read_recent",
				provider: "gmail",
				fixture: true,
				label: "Personal inbox",
				syncedAt: "2026-11-27T12:00:00Z",
				items: [{ stamp: "1", summary: "Synthetic subject" }],
			},
		});
	});
	await page.goto("/workbench/");
	await page
		.getByLabel("Message", { exact: true })
		.fill("List my latest subjects");
	await page
		.getByRole("button", { name: "Connected apps", exact: true })
		.click();
	await expect(
		page.getByRole("button", { name: "Run app request" }),
	).toBeDisabled();
	await page.getByLabel("App account").selectOption(feed);
	await page.getByLabel("Planner model").selectOption("desk");
	await page.getByRole("button", { name: "Run app request" }).click();
	await expect(page.getByRole("region", { name: "App result" })).toContainText(
		"Synthetic subject",
	);
	await page.getByRole("button", { name: "Run app request" }).click();
	await expect(page.getByRole("alert")).toContainText("Refresh the account");
	await expect(page.getByText("Synthetic subject")).toHaveCount(0);
	await page.getByRole("button", { name: "Back to chat" }).click();
	await expect(page.getByLabel("Message", { exact: true })).toHaveValue(
		"List my latest subjects",
	);
});

test("agent approval binds the displayed task; an uncertain write offers only reconciliation", async ({
	page,
}) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}`;
	const runId = "20000000-0000-4000-8000-000000000099";
	let state = "waiting";
	await page.route(`${base}/conversations`, (r) =>
		r.fulfill({ json: { threads: [], routes: [], agentsEnabled: true } }),
	);
	await page.route(`${base}/agents`, (r) =>
		r.fulfill({
			json: {
				agents: [],
				routes: [],
				available: true,
				provider: "sandbox-tasks",
				connection: { active: true },
				runs: [
					{
						id: runId,
						goal: "Draft review task",
						version: 1,
						state,
						steps: 1,
						title: "Review proposal",
						resource: "personal",
						digest: "fixed-digest",
						outcome: "",
						receipt: state === "completed" ? "task-1" : null,
					},
				],
			},
		}),
	);
	await page.route(`${base}/agents/runs/${runId}/decision`, async (r) => {
		const command = r.request().postDataJSON();
		expect(await r.request().headerValue(session.csrfHeader)).toBe(
			session.csrfToken,
		);
		expect(command).toEqual({
			operation: state === "waiting" ? "approve" : "reconcile",
			digest: "fixed-digest",
		});
		state = state === "waiting" ? "unknown" : "completed";
		await r.fulfill({ status: 200, body: "" });
	});
	await page.goto("/workbench/");
	await page.getByText("Agents and runs", { exact: true }).click();
	const inbox = page.getByRole("region", { name: "Needs you" });
	await inbox.getByRole("link", { name: /Review task creation/ }).click();
	await expect(page).toHaveURL(new RegExp(`#run-${runId}$`));
	await expect(
		page.getByText("Task title: Review proposal · List: personal"),
	).toBeVisible();
	await page.getByRole("button", { name: "Approve exact task" }).click();
	await expect(
		page.getByRole("button", { name: "Approve exact task" }),
	).toHaveCount(0);
	await page.getByRole("button", { name: "Recheck provider receipt" }).click();
	await expect(page.getByText("Provider receipt: task-1")).toBeVisible();
	await expect(inbox.getByRole("link")).toHaveCount(0);
});

test("engine run uses the displayed override and runner recovery explains the device grant", async ({
	page,
}) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}`;
	const agent = "10000000-0000-4000-8000-000000000024";
	const device = "10000000-0000-4000-8000-000000000025";
	const thread = "10000000-0000-4000-8000-000000000026";
	await page.route(`${base}/conversations`, (r) =>
		r.fulfill({
			json: {
				agentsEnabled: true,
				threads: [{ id: thread, title: "Weekly review" }],
				routes: [],
			},
		}),
	);
	await page.route(`${base}/conversations/${thread}`, (r) =>
		r.fulfill({ json: { id: thread, title: "Weekly review", turns: [] } }),
	);
	await page.route(`${base}/runners`, (r) =>
		r.fulfill({
			json: {
				devices: [
					{
						id: device,
						state: "active",
						online: false,
						folder: true,
						browser: false,
					},
				],
				jobs: [
					{
						id: "old-job",
						device,
						kind: "inventory",
						state: "failed",
						failureReason: "folder-changed",
					},
				],
			},
		}),
	);
	await page.route(`${base}/agents`, (r) =>
		r.fulfill({
			json: {
				agents: [{ id: agent, name: "Reviewer", version: 1 }],
				routes: [],
				connection: { active: true },
				runs: [],
			},
		}),
	);
	await page.route(`${base}/agents/${agent}/engines`, (r) =>
		r.fulfill({
			json: {
				accounts: [
					{
						id: device,
						label: "Work plan",
						provider: "chatgpt-plan",
						active: true,
						online: true,
						generation: 3,
						models: ["fast", "deep"],
						remainingPercent: 0,
						windowMinutes: 300,
						observedAt: "2026-11-27T01:00:00Z",
					},
				],
				choices: [
					{ thread: null, connection: device, model: "fast" },
					{ thread, connection: device, model: "deep" },
				],
				receipts: [],
			},
		}),
	);
	await page.route(`${base}/agents/${agent}/engine-runs`, (r) => {
		expect(r.request().postDataJSON().expected).toEqual({
			connection: device,
			model: "deep",
			generation: 3,
		});
		return r.fulfill({ status: 409 });
	});
	await page.goto("/workbench/");
	await page.getByText("Customer runners", { exact: true }).click();
	await expect(page.getByText(/No recent heartbeat/)).toBeVisible();
	await expect(page.getByText(/The granted folder moved/)).toBeVisible();
	await expect(
		page.getByRole("button", { name: "Count files" }),
	).toBeDisabled();
	await page.getByText("Agents and runs", { exact: true }).click();
	await page.getByRole("button", { name: /^Reviewer .*v1/ }).click();
	await page.getByText("Connected engines", { exact: true }).click();
	await expect(
		page.getByText(/Next engine run · Work plan fast · Agent default/),
	).toBeVisible();
	await expect(page.getByText(/0% remaining in the 300-minute/)).toBeVisible();
	await page.getByLabel("Applies to").selectOption(thread);
	await expect(
		page.getByText(/Next engine run · Work plan deep · Conversation override/),
	).toBeVisible();
	await page.getByLabel("Task for the selected engine").fill("Review");
	await page.getByRole("button", { name: "Run selected engine" }).click();
	await expect(
		page.getByText(/Review the refreshed selection and receipt/),
	).toBeVisible();
});

test("workbench resolves presets and shows public source coverage", async ({
	page,
}) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}`;
	await page.route(`${base}/conversations`, (r) =>
		r.fulfill({
			json: {
				threads: [],
				routes: ["desk"],
				presets: { local: "desk" },
				publicResearch: true,
			},
		}),
	);
	await page.route(`${base}/research`, async (r) => {
		expect(r.request().postDataJSON()).toEqual({ query: "Earth", fresh: true });
		expect(await r.request().headerValue(session.csrfHeader)).toBe(
			session.csrfToken,
		);
		await r.fulfill({
			json: {
				discovered: 4,
				attempted: 3,
				fetched: 1,
				citations: [
					{
						title: "Earth",
						quote: "Earth is a planet.",
						requestedUrl: "https://en.wikipedia.org/wiki/Earth",
						finalUrl: "https://en.wikipedia.org/wiki/Earth",
						observedAt: "2026-11-22T00:00:00Z",
						cached: false,
					},
				],
			},
		});
	});
	await page.goto("/workbench/");
	await page.getByLabel("Model route").selectOption("@local");
	await page.getByText("Public web research", { exact: true }).click();
	await page.getByLabel("Search English Wikipedia").fill("Earth");
	await page.getByLabel("Fetch fresh pages").check();
	await page.getByRole("button", { name: "Search sources" }).click();
	await expect(
		page.getByText("Read 1 of 4 discovered pages. Attempted 3."),
	).toBeVisible();
	await expect(page.getByRole("link", { name: "Earth" })).toHaveAttribute(
		"href",
		"https://en.wikipedia.org/wiki/Earth",
	);
});

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

test("a conflicting team review can be retained with attribution and private memory stays editable", async ({
	page,
}) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}`;
	const parent = "10000000-0000-4000-8000-000000000011";
	const child = "10000000-0000-4000-8000-000000000012";
	const noteId = "10000000-0000-4000-8000-000000000013";
	let state = "waiting",
		fact = "EUR",
		version = 1;
	await page.route(`${base}/conversations`, (r) =>
		r.fulfill({ json: { threads: [], routes: [], agentsEnabled: true } }),
	);
	await page.route(`${base}/agents`, (r) =>
		r.fulfill({
			json: {
				agents: [],
				routes: [],
				available: true,
				provider: "sandbox-tasks",
				connection: { active: true },
				runs: [
					{
						id: parent,
						goal: "Synthetic review",
						mode: "team",
						version: 1,
						steps: 0,
						state,
						outcome: "Review results",
					},
				],
				teamMembers: [
					{
						parentId: parent,
						childId: child,
						agentId: child,
						slot: 1,
						state: "completed",
						outcome: "Review A",
					},
				],
			},
		}),
	);
	await page.route(`${base}/agents/runs/${parent}/decision`, (r) => {
		expect(r.request().postDataJSON()).toEqual({
			operation: "select",
			digest: "all",
		});
		state = "completed";
		return r.fulfill({ status: 200 });
	});
	await page.route(/\/agents\/memory(?:\?.*)?$/, (r) => {
		if (r.request().method() === "POST") {
			const body = r.request().postDataJSON();
			expect(body.expectedVersion).toBe(version);
			fact = body.fact;
			version++;
			return r.fulfill({ status: 200 });
		}
		return r.fulfill({
			json: [
				{
					id: noteId,
					topic: "currency",
					fact,
					sourceId: parent,
					sourceGoal: "Synthetic review",
					revision: version,
				},
			],
		});
	});
	await page.goto("/workbench/");
	await page.getByText("Agents and runs", { exact: true }).click();
	await expect(
		page.getByRole("button", { name: "Approve exact task" }),
	).toHaveCount(0);
	await page
		.getByRole("button", { name: "Keep all attributed reviews" })
		.click();
	await page.getByRole("button", { name: "Open private memory" }).click();
	await page.getByRole("button", { name: "Correct note" }).click();
	await page.getByLabel("Fact to remember").fill("GBP");
	await page.getByRole("button", { name: "Save correction" }).click();
	await expect(page.getByText("GBP", { exact: true })).toBeVisible();
});
test("submitting a team keeps the multiline brief and the selected local methods", async ({
	page,
}) => {
	await ready(page, [first]);
	const base = `**/api/workspaces/${first.workspaceId}`;
	const agents = ["Lead", "Editor", "Analyst"].map((name, i) => ({
		id: `10000000-0000-4000-8000-00000000000${i}`,
		name,
		version: 1,
		mine: true,
		route: "model",
	}));
	await page.route(`${base}/conversations`, (r) =>
		r.fulfill({ json: { threads: [], routes: [], agentsEnabled: true } }),
	);
	await page.route(`${base}/agents`, (r) =>
		r.fulfill({
			json: {
				agents,
				routes: ["model"],
				available: true,
				provider: "sandbox-tasks",
				connection: { active: false },
				runs: [],
			},
		}),
	);
	const goal = "Launch review\n- [ ] Review copy\n- [ ] Verify totals";
	await page.route(`${base}/agents/${agents[0].id}/team`, (r) =>
		r.fulfill({ json: { id: r.request().postDataJSON().requestId } }),
	);
	await page.goto("/workbench/");
	await page.getByText("Agents and runs", { exact: true }).click();
	await page.getByRole("button", { name: /^Lead .*v1/ }).click();
	await page.getByLabel("Run mode").selectOption("team");
	await page.getByRole("checkbox", { name: "Editor", exact: true }).check();
	await page.getByRole("checkbox", { name: "Analyst", exact: true }).check();
	await page.getByLabel("Editor review method").selectOption("word-count");
	await page.getByLabel("Analyst review method").selectOption("checklist");
	await page.getByLabel("Goal", { exact: true }).fill(goal);
	const submitted = page.waitForRequest(
		(r) => r.method() === "POST" && r.url().endsWith("/team"),
	);
	await page.getByRole("button", { name: "Start run" }).click();
	expect((await submitted).postDataJSON()).toMatchObject({
		goal,
		reviewers: [agents[1].id, agents[2].id],
		checks: { [agents[1].id]: "word-count", [agents[2].id]: "checklist" },
	});
});
