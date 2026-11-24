"""Measure one disposable Docker Cloud sandbox. Without --run this prints the plan."""
import argparse
import json
import subprocess
import time
import uuid


def sbx(*args, timeout=45):
    response = subprocess.run(
        ["sbx", "--cloud", *args], capture_output=True, encoding="utf-8", timeout=timeout
    )
    if response.returncode:
        # Provider output can contain tenant details. Keep it out of shared evidence.
        raise RuntimeError(f"sbx {args[0]} exited {response.returncode}")
    return response.stdout.strip()


def dns_denials(log, sandbox_id):
    return sum(entry["count"] for entry in log["blocked"]
               if entry.get("sandbox_id") == sandbox_id
               and entry.get("host") == "example.com"
               and entry.get("proxy_type") == "dns"
               and entry.get("rule") == "per-sandbox deny")


def qualify():
    name = "zincc-qualification-" + uuid.uuid4().hex[:14]
    sbx("diagnose")
    start = time.monotonic()
    print(f"Disposable resource: {name}", flush=True)
    target = name
    try:
        sbx("create", "--name", name, "--cpus", "1", "--memory", "2g",
            "--ttl", "15m", "--on-timeout", "delete", "--deny-network", "example.com",
            "shell", timeout=140)
        create_seconds = round(time.monotonic() - start, 2)
        spec = json.loads(sbx("inspect", name, "--json"))
        target = spec["id"]
        if (spec["cpus"], spec["memory_mib"], spec["on_timeout"]) != (1, 2048, "delete"):
            raise RuntimeError("Unexpected sandbox shape or timeout behavior")
        sbx("policy", "init", "deny-all", "--sandbox", target)
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
        if sbx("exec", target, "sh", "-c", probe) != "ok":
            raise RuntimeError("Unexpected guest observation")
        # An HTTP response from a policy proxy can mean that it reached the proxy.
        # Never interpret a generic networking error as proof of a provider policy.
        status = sbx("exec", target, "sh", "-c",
                     "curl --silent --max-time 5 -o /dev/null -w '%{http_code}' https://example.com || true")
        if status.startswith(("2", "3")):
            raise RuntimeError("The HTTPS destination was reachable under the candidate policy")
        measurements = []
        for _ in range(3):
            begin = time.monotonic()
            if sbx("exec", target, "true"):
                raise RuntimeError("Unexpected command output")
            measurements.append(round(time.monotonic() - begin, 3))
        for _ in range(10):
            denials = dns_denials(json.loads(sbx("policy", "log", target, "--json")), target)
            if denials:
                break
            time.sleep(2)
        if not denials:
            raise RuntimeError("Provider DNS denial for the test destination was not observed")
        print(json.dumps({"create_seconds": create_seconds,
                          "image": spec["image"], "dns_denials": denials,
                          "exec_roundtrip_seconds": measurements,
                          "https_probe_status": status,
                          "snapshot_restore": "disabled by Zincc",
                          "qualification": "requires independent policy log and isolation review"}))
    finally:
        sbx("rm", "--force", target)
        # Inventory is private. Only expose presence of this randomly generated resource.
        for _ in range(10):
            inventory = sbx("ls", "--json")
            json.loads(inventory)
            if name not in inventory and target not in inventory:
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
