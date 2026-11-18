import { defineConfig } from "@playwright/test";

export default defineConfig({
	testDir: "./tests",
	use: {
		baseURL: "http://127.0.0.1:4173",
		channel: process.env.ZINCC_BROWSER_CHANNEL,
		viewport: { width: 1280, height: 800 },
	},
	webServer: {
		command: "pnpm preview --port 4173 --strictPort",
		url: "http://127.0.0.1:4173/workbench/",
		reuseExistingServer: false,
	},
});
