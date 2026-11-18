import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const proxy = Object.fromEntries(
	["/api/", "/oauth2/", "/login/oauth2/"].map((path) => [
		path,
		"http://127.0.0.1:8080",
	]),
);
export default defineConfig({
	base: "/workbench/",
	plugins: [react()],
	build: { license: { fileName: "third-party-licenses.md" } },
	server: { host: "127.0.0.1", proxy },
	preview: { host: "127.0.0.1", proxy },
});
