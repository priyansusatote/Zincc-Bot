# P1.4 · Switching workspaces without showing the previous workspace

The workspace picker uses two requests. First, Spring lists memberships for the issuer and subject in the signed-in session. Then the selected workspace is fetched through the existing access check. Membership can change between those requests. A 404 produces a recovery screen with a way to refresh the list. [Directory migration](../../src/main/resources/db/migration/V3__own_workspace_directory.sql)

There is also a browser race. If a slow request for Design finishes after the user switches to Operations, its response must never replace the Operations view. React keys the selected view by workspace ID. When that view unmounts, it aborts its fetch and ignores a late response. Session loading, sign-in, missing access and request failure each have a distinct state. [Workbench source](../../frontend/src/Workbench.tsx)

TypeScript checks which fields the frontend expects. The running server, network or proxy can still return something else. The client validates the JSON before rendering it. One browser test deliberately returns malformed session data, verifies the recovery action, and retries successfully. [Typed client](../../frontend/src/client.ts) · [Browser tests](../../frontend/tests/workbench.spec.ts)
