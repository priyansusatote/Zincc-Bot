"""Measure one disposable Docker Cloud sandbox. Without --run this prints the plan."""
import argparse
import json
import subprocess
import time
import uuid


def sbx(*args, timeout=45):
    response = subprocess.run(
        ["sbx", "--cloud", *args], capture_output=True, text=True, timeout=timeout
    )
    if response.returncode:
        # Provider output can contain tenant details. Keep it out of shared evidence.
        raise RuntimeError(f"sbx {args[0]} exited {response.returncode}")
    return response.stdout.strip()


def qualify():
    name = "zincc-qualification-" + uuid.uuid4().hex[:14]
    sbx("diagnose")
    start = time.monotonic()
    print(f"Disposable resource: {name}", flush=True)
    try:
        sbx("create", "--name", name, "--cpus", "1", "--memory", "2g",
            "--ttl", "15m", "--on-timeout", "delete", "shell", timeout=140)
        sbx("policy", "init", "deny-all", "--sandbox", name)
        ready_seconds = round(time.monotonic() - start, 2)
        # Only fixed synthetic commands. No host paths, templates, MCP or account secrets.
        probe = """set -eu
command -v curl >/dev/null
test ! -e /home/agent/workspace/.env
test ! -e /home/agent/workspace/.git
test -z "${ZINCC_CONNECTION_KEY:-}"
test ! -e /tmp/zincc-qualification-marker
printf synthetic > /tmp/zincc-qualification-marker
test "$(cat /tmp/zincc-qualification-marker)" = synthetic
printf ok"""
        if sbx("exec", name, "sh", "-c", probe) != "ok":
            raise RuntimeError("Unexpected guest observation")
        # An HTTP response from a policy proxy can mean that it reached the proxy.
        # Never interpret a generic networking error as proof of a provider policy.
        status = sbx("exec", name, "sh", "-c",
                     "curl --silent --max-time 5 -o /dev/null -w '%{http_code}' https://example.com || true")
        if status.startswith(("2", "3")):
            raise RuntimeError("The HTTPS destination was reachable under the candidate policy")
        measurements = []
        for _ in range(3):
            begin = time.monotonic()
            if sbx("exec", name, "true"):
                raise RuntimeError("Unexpected command output")
            measurements.append(round(time.monotonic() - begin, 3))
        print(json.dumps({"create_and_policy_seconds": ready_seconds,
                          "exec_roundtrip_seconds": measurements,
                          "https_probe_status": status,
                          "snapshot_restore": "disabled by Zincc",
                          "qualification": "requires independent policy log and isolation review"}))
    finally:
        sbx("rm", "--force", name)
        # Inventory is private. Only expose presence of this randomly generated resource.
        for _ in range(10):
            if name not in sbx("ls"):
                print("Deletion confirmed")
                break
            time.sleep(1)
        else:
            raise RuntimeError("Deletion not confirmed; inspect the named sandbox")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true", help="create billable cloud compute")
    if parser.parse_args().run:
        qualify()
    else:
        print("Dry run: one micro sandbox, 15-minute delete TTL, synthetic probes and forced removal.")
