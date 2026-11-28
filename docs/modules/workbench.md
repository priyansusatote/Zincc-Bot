# Web workbench

Use **New conversation** to start a thread, select its model in the composer, and find saved conversations in the sidebar. Switching between a tool and the current chat preserves its pending draft. Changing workspace or signing out closes that workspace's views.

**Agents and runs** shows the agent roster, outstanding decisions and run receipts. Select an agent to work with its definition, engine and runs. Research, documents, routines and customer devices have separate destinations, visible when enabled for the workspace. On a phone, open **Workspace menu** to reach those destinations. The account button is in the top bar. The palette follows the device appearance setting.

![Zincc conversation workspace with sample history](../design/workbench-desktop.png)

The screenshot uses synthetic workspace and conversation data. [Phone view](../design/workbench-phone.png) · [Learning note](../learning-notes/047-workbench-navigation.md)

## Run the packaged application

See the [signed-in capability audit](../research/2026-11-27-live-capability-audit.md) for the coverage of each destination.

### Read a connected account

In **Chat → Connected apps**, select a Gmail inbox, primary Google Calendar or Slack channel. Choose a planner, type “List the latest items from this account” and select **Run app request**. Check the account label, sample indicator and last sync time above the results. A successful empty read means the synced account contains no entries to show.

Choose **Manage connections** to authorize a real account. A sample requires selecting **Use sample account**. If a feed needs a sync, use **Sync now** in its connection row, then return and refresh the account list. The model receives the request you type and the provider type. The returned subjects, event titles or channel summaries remain in this view until you change the selection, leave it or run another request. Full-text search, reply drafting and sending require additional tools.

Use Node 26.13.1, pnpm 12.21.0 and the [project JDK](../../README.md). From the repository root on Windows:

```powershell
Set-Location frontend
pnpm install --frozen-lockfile
pnpm build
Set-Location ..
.\mvnw.cmd -Pworkbench verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/zincc-control-plane-0.1.0-SNAPSHOT.jar
```

Open `http://127.0.0.1:8080/workbench/`. The default Spring profile shows the shell and explains how to enable sign-in. For real workspace access, apply migrations through V5 and configure the [OIDC profile](browser-sessions.md). Set its public base URL to the browser origin. The bundled application lands on the workbench after login. A plain backend build can still run independently. Use a clean build when switching Maven packaging profiles.

For frontend development, start Spring on port 8080 and run `pnpm dev` inside `frontend`. Open the Vite origin under `/workbench/`. The dev server proxies API, login and callback paths to Spring. Set `ZINCC_WORKBENCH_ENABLED=true` for the unbundled service and register the **Vite origin** as the public callback for that development client.

## Contract and verification

The [OpenAPI contract](../../frontend/openapi.json) defines the session, workspace and conversation endpoints. After changing it, run `pnpm generate`. The build checks that the generated file is current, type-checks, lints, and bundles the client. The client also checks response shapes at runtime.

After building, run `pnpm exec playwright install chromium` then `pnpm test:e2e`. To use an installed Edge browser instead, set `ZINCC_BROWSER_CHANNEL=msedge`. The browser tests use HTTP fixtures to exercise loading, invalid responses, retries, empty states, revoked access, keyboard navigation and account switching. The Java suite verifies real database filtering and the local OIDC flow. Windows x64 with Edge is the qualified browser environment for this task.
