import { defineConfig } from "@playwright/test";

const port = Number(process.env.ZINCC_TEST_PORT ?? "4173");
if (!Number.isInteger(port) || port < 1 || port > 65535)
	throw new Error("Invalid test port");
const origin = `http://127.0.0.1:${port}`;

export default defineConfig({
	testDir: "./tests",
	use: {
		baseURL: origin,
		channel: process.env.ZINCC_BROWSER_CHANNEL,
		viewport: { width: 1280, height: 800 },
	},
	webServer: {
		command: `node node_modules/vite/bin/vite.js preview --port ${port} --strictPort`,
		url: `${origin}/workbench/`,
		reuseExistingServer: false,
	},
});
