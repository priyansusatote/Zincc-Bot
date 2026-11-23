# P6 execution and customer runners

## Try a local runner

1. Open the signed-in workbench and expand **Customer runners**. Click **Create pairing code**. The code expires in ten minutes and works once.
2. With the repository's Node and frontend dependencies installed, run `node frontend/runner/local.mjs --browser` from the project root. On Windows it launches Edge. Add `--folder "C:\path\to\test-folder"` to grant a shallow file count. Use a directory you control. On another machine specify `--server https://your-zincc-host`. Plain HTTP is limited to loopback.
3. Paste the code at the prompt. The device opens outbound connections only. Its bearer credential lives in process memory. Pair again after exiting. The database stores only a digest.
4. Choose **Count files** or **Review in browser**. The browser opens a private, offline draft. Choose the priority, then **Return control to Zincc**. The workbench receipt shows the confirmed choice. Folder inventory returns a count of direct regular files, skips symlink entries and nested directories, and fails above 10,000 directory entries. Filenames, file data and local paths stay on the device.
5. Type `pause` in the runner terminal to stop polling and interrupt local work. Type `resume` to continue, or `quit` to disconnect. **Pause** in the workbench also cancels outstanding leases. **Revoke runner** invalidates its credential. The browser closes on a lost renewal. At rest, an offline device loses its online indicator after 25 seconds.

## Recovery and diagnosis

A queued job expires after one minute. Claiming it starts a 25-second renewable lease and a five-minute absolute deadline. Browser handoff uses this same deadline. The device polls every two seconds while idle. While working, it renews every 3.5 seconds with a five-second request timeout. The server checks time and owner at receipt commit. After a lost response, inspect the existing receipt before queuing another job. Uncertain work expires without automatic repetition.

If a command is disabled, check **online**, the local startup flags and any previous work. Only one job can be active per device. For a failed browser job, check Edge availability and rerun the browser test below. An expired job usually means lost contact or an unattended review. The owner sees job type, state, device and the bounded result. The service stores no recording or browsing history.

## Placement and limits

The server Docker broker accepts four fixed operations spanning documents and curated MCP. It enforces three shared process slots, pinned images, no host mounts or network, restricted users, bounded memory/CPU/PIDs and named termination. Docker shares the engine kernel. The foreground customer runner has a separate trust model. It runs trusted local code, opens a fresh browser per job and accepts two fixed operations. The OS account remains trusted. Protect the selected folder's ancestry from concurrent local replacement. Signed installers, unattended services, persistent browser login, general website navigation and desktop gestures require further work.

## Hosted qualification

The Docker Cloud candidate offers a managed microVM. CubeSandbox requires a compatible Linux virtualization host. Local Docker Desktop only supplied an ordinary container runtime during this review. Hosted measurements require a subscribed account and an authorized budget. Run `python tools/qualify-cloud-sandbox.py` to inspect the dry-run plan. With explicit authorization, `--run` provisions one 1-vCPU / 2-GiB sandbox with a 15-minute delete timeout, applies a sandbox-specific deny policy, checks synthetic state, measures three exec round trips and removes the sandbox. Record the effective policy, denied request evidence, resource config, deletion and timings. A reachability check alone cannot establish enforced egress isolation.

Zincc exposes no cloud snapshot save/restore or template-export capability. Before accepting external executable packages, qualify isolation between independently authorized tenants, effective cloud account/kit egress and secret policy, cleanup after interruption, fingerprinted guest image and resource enforcement. A single synthetic lifecycle benchmark only covers part of that gate. [Docker lifecycle](https://docs.docker.com/ai/sandboxes/cloud/usage/) · [gVisor placement](https://gvisor.dev/docs/user_guide/quick_start/docker/)

## Verification commands

- `node --test frontend/runner/local.test.mjs frontend/runner/protocol.test.mjs` exercises a real browser, fresh state, cancellation, direct-file counts and the CLI request format. Set `ZINCC_BROWSER_CHANNEL` to a supported installed browser.
- `./mvnw -Pworkbench verify` runs database lease tests. Set `ZINCC_KNOWLEDGE_TEST_IMAGE` to the built knowledge image ID for live Docker checks, with the P4 PDF fixtures present.
- `pnpm --dir frontend build` checks OpenAPI and TypeScript. `pnpm --dir frontend test:e2e` covers workbench regressions.

## Local acceptance on 23 November 2026

Flyway V10 applied to the local workbench. A signed-in Edge session paired a separate foreground Node process, queued a job against a one-file synthetic directory, observed its completed receipt, paused and resumed the device, then revoked it. The full Java verification reported 105 cases, 101 passed and four optional external probes skipped. Live Docker inspection and the document-worker tests ran. All nine existing workbench browser regressions passed.
