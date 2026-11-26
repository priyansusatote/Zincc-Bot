"""Device-local protocol qualification. No hooks into Zincc's cloud model gateway."""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import queue
import subprocess
import tempfile
import threading
import time


FREE_MODEL = "opencode/deepseek-v4.1-flash-free"
TEXT_AGENT = "zincc-check"
ADAPTERS = {"opencode": ["acp"], "antigravity": ["acp"],
            "grok": ["--no-auto-update", "agent", "stdio"], "kimi": ["acp"], "codex": ["app-server"]}


def isolated_environment(root):
    keep = {"PATH", "SYSTEMROOT", "WINDIR", "COMSPEC", "PATHEXT", "NUMBER_OF_PROCESSORS",
            "PROCESSOR_ARCHITECTURE", "LANG", "LC_ALL"}
    env = {k: v for k, v in os.environ.items() if k.upper() in keep}
    for name in ("HOME", "USERPROFILE", "APPDATA", "LOCALAPPDATA", "TEMP", "TMP", "XDG_CONFIG_HOME",
                 "XDG_DATA_HOME", "XDG_STATE_HOME", "XDG_CACHE_HOME", "OPENCODE_CONFIG_DIR", "CODEX_HOME"):
        folder = root / name
        folder.mkdir()
        env[name] = str(folder)
    deny = {"*": "deny"}
    config = {"enabled_providers": ["opencode"], "model": FREE_MODEL, "small_model": FREE_MODEL,
              "permission": deny, "default_agent": TEXT_AGENT, "mcp": {}, "plugin": [],
              "agent": {TEXT_AGENT: {"mode": "primary", "permission": deny, "description": "Text-only check."}}}
    env.update(OPENCODE_DISABLE_AUTOUPDATE="1", OPENCODE_DISABLE_CLAUDE_CODE="1",
               OPENCODE_DISABLE_PROJECT_CONFIG="1", OPENCODE_DISABLE_DEFAULT_PLUGINS="1",
               OPENCODE_CONFIG_CONTENT=json.dumps(config))
    return env


def permissions_denied(rules):
    # Last matching rule wins. A wildcard denial must supersede every possible allow.
    for rule in reversed(rules):
        if rule.get("effect") != "deny":
            return False
        if rule.get("action") == "*" and rule.get("resource") == "*":
            return True
    return False


def inspect_opencode(binary, root, env):
    def command(*args):
        return subprocess.run([binary, *args, "--standalone"], cwd=root, env=env, check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=35,
                              encoding="utf-8").stdout
    if FREE_MODEL not in command("models").splitlines():
        raise ValueError("Free model missing from standalone catalog")
    entries = json.loads(command("debug", "config"))
    configs = [entry["info"] for entry in entries if isinstance(entry.get("info"), dict)]
    if len(configs) != 1:
        raise ValueError("Effective standalone configuration unavailable")
    config = configs[0]
    selection = config.get("model") or {}
    if (selection.get("providerID") != "opencode" or selection.get("model") != FREE_MODEL.split("/", 1)[1]
            or config.get("default_agent") != TEXT_AGENT or config.get("mcp") or config.get("plugins")):
        raise ValueError("Standalone provider, model, agent or extensions differ from the verified configuration")
    agents = json.loads(command("debug", "agents"))
    matches = [a for a in agents if a.get("name") == TEXT_AGENT]
    if len(matches) != 1 or not permissions_denied(matches[0].get("permissions", matches[0].get("permission", []))):
        raise ValueError("Tool denial could not be confirmed")


def standalone_turn(binary, root, env):
    # v2.4.1's run/debug commands support private standalone services; its ACP command does not.
    prompt = "Return exactly the single word ready."
    print("Submitting one synthetic free OpenCode turn.", flush=True)
    result = subprocess.run([binary, "run", "--standalone", "--format", "json", "--agent", TEXT_AGENT,
                             "--model", FREE_MODEL, "--title", "Zincc synthetic readiness check", prompt],
                            cwd=root, env=env, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                            stderr=subprocess.DEVNULL, timeout=100, encoding="utf-8")
    answer = []
    observed_models = set()
    tools = False
    finished = False
    for line in result.stdout.splitlines():
        if not line.startswith("{"):
            continue
        event = json.loads(line)
        part = event.get("part") or {}
        if event.get("type") == "text":
            answer.append(part.get("text", ""))
        if event.get("type") in ("tool_call", "tool_use") or part.get("type") == "tool":
            tools = True
        if event.get("type") == "step_finish":
            finished = True
        if part.get("modelID"):
            observed_models.add(part["modelID"])
    return {"engine": "opencode", "transport": "standalone CLI", "requestedModel": FREE_MODEL,
            "exitCode": result.returncode,
            "completed": result.returncode == 0 and finished, "ready": "".join(answer).strip().lower() == "ready",
            "modelEchoMatched": FREE_MODEL.split("/", 1)[1] in observed_models if observed_models else None,
            "toolRequested": tools}


class Rpc:
    def __init__(self, process):
        self.process = process
        self.incoming = queue.Queue(maxsize=128)
        self.sequence = 0
        self.text = ""
        self.tool_requested = False
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        try:
            while True:
                line = self.process.stdout.readline(262145)
                if len(line) > 262144 or not line.endswith(b"\n"):
                    break
                self.incoming.put(json.loads(line), timeout=2)
        except (ValueError, OSError, queue.Full):
            pass
        finally:
            try:
                self.incoming.put(None, timeout=2)
            except queue.Full:
                pass

    def send(self, frame):
        self.process.stdin.write(json.dumps({"jsonrpc": "2.0", **frame}).encode() + b"\n")
        self.process.stdin.flush()

    def call(self, method, params, timeout=35):
        self.sequence += 1
        self.send({"id": self.sequence, "method": method, "params": params})
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            msg = self.incoming.get(timeout=max(0.01, deadline - time.monotonic()))
            if not isinstance(msg, dict):
                raise ValueError("Engine closed the protocol stream")
            if "method" in msg and "id" in msg:
                self.tool_requested = True
                self.send({"id": msg["id"], "error": {"code": -32601, "message": "Client capability unavailable"}})
            elif msg.get("method") == "session/update":
                update = msg["params"]["update"]
                if update.get("sessionUpdate") in ("tool_call", "tool_call_update"):
                    self.tool_requested = True
                content = update.get("content", {})
                if update.get("sessionUpdate") == "agent_message_chunk" and content.get("type") == "text":
                    self.text = (self.text + content["text"])[:1024]
            elif msg.get("id") == self.sequence:
                if "error" in msg:
                    raise ValueError(f"{method} rejected (RPC {msg['error'].get('code')})")
                return msg["result"]
        raise TimeoutError(method + " timed out")


@contextmanager
def rpc_process(binary, args, cwd, env):
    with subprocess.Popen([binary, *args], cwd=cwd, env=env, stdin=subprocess.PIPE,
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL) as process:
        try:
            yield Rpc(process)
        finally:
            process.stdin.close()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()


def selections(session):
    options = {o.get("category"): o.get("currentValue") for o in session.get("configOptions", [])}
    mode = options.get("mode") or (session.get("modes") or {}).get("currentModeId")
    model = options.get("model") or (session.get("models") or {}).get("currentModelId")
    return mode, model


def probe(binary, engine, live, select_model=None):
    with tempfile.TemporaryDirectory(prefix="zincc-engine-", ignore_cleanup_errors=True) as tmp:
        root = Path(tmp)
        env = isolated_environment(root)
        work = root / "empty-project"
        work.mkdir()
        if engine == "claude":
            version = subprocess.run([binary, "--version"], cwd=work, env=env, capture_output=True,
                                     timeout=12, check=True, encoding="utf-8").stdout.split()[0]
            help_text = subprocess.run([binary, "--help"], cwd=work, env=env, capture_output=True,
                                       timeout=15, check=True, encoding="utf-8").stdout
            required = ("--tools", "--strict-mcp-config", "--output-format", "--permission-mode", "--model")
            return {"engine": engine, "version": version, "cliControlsAdvertised": all(s in help_text for s in required),
                    "inference": "untested"}
        if engine == "opencode":
            inspect_opencode(binary, work, env)
            if live:
                return standalone_turn(binary, work, env)
        with rpc_process(binary, ADAPTERS[engine], work, env) as rpc:
            if engine == "codex":
                rpc.call("initialize", {"clientInfo": {"name": "zincc", "version": "0.1.0"}, "capabilities": {}})
                rpc.send({"method": "initialized"})
                catalog = rpc.call("model/list", {"limit": 20})
                return {"engine": engine, "initialized": True, "bundledOrCachedModelCount": len(catalog["data"]),
                        "inference": "untested"}
            hello = rpc.call("initialize", {"protocolVersion": 1, "clientCapabilities": {},
                                            "clientInfo": {"name": "Zincc", "version": "0.1.0"}})
            if hello.get("protocolVersion") != 1:
                raise ValueError("Unsupported ACP version")
            result = {"engine": engine, "protocol": 1, "inference": "untested"}
            result["advertisedAuthMethods"] = len(hello.get("authMethods", []))
            session = rpc.call("session/new", {"cwd": str(work), "mcpServers": []})
            mode, model = selections(session)
            result.update(sessionCreated=True, selectedModelReported=bool(model))
            if engine == "opencode":
                result.update(modelConfirmed=model == FREE_MODEL, agentConfirmed=mode == TEXT_AGENT)
            if select_model is not None:
                result["selectedModelConfirmed"] = confirm_selection(rpc, session, select_model)
            return result


def confirm_selection(rpc, session, expected):
    """An empty selector acknowledgement cannot attest to a switch."""
    options = [o for o in session.get("configOptions", []) if o.get("category") == "model"]
    if len(options) != 1:
        return selections(session)[1] == expected
    option = options[0]
    if option.get("currentValue") == expected:
        return True
    if not any(v.get("value") == expected for v in option.get("options", [])):
        return False
    reply = rpc.call("session/set_config_option", {"sessionId": session["sessionId"],
                     "configId": option["id"], "value": expected})
    return selections(reply)[1] == expected


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("engine", choices=[*ADAPTERS, "claude"])
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--one-free-opencode-turn", action="store_true")
    parser.add_argument("--select-model", help="Check an explicit ACP model selection without sending task content")
    opts = parser.parse_args()
    if not opts.binary.is_absolute() or not opts.binary.is_file():
        parser.error("Supply the installed executable's absolute path")
    if opts.one_free_opencode_turn and opts.engine != "opencode":
        parser.error("The synthetic inference check requires OpenCode")
    if opts.select_model and (opts.engine in ("codex", "claude") or opts.one_free_opencode_turn):
        parser.error("Model selection qualification uses a prompt-free ACP session")
    try:
        observation = probe(str(opts.binary), opts.engine, opts.one_free_opencode_turn, opts.select_model)
        print(json.dumps(observation))
        if opts.one_free_opencode_turn and (not observation["completed"] or not observation["ready"]
                or observation["toolRequested"] or observation["modelEchoMatched"] is not True):
            parser.exit(1, "Free-route qualification incomplete. No automatic retry.\n")
        if opts.select_model and not observation.get("selectedModelConfirmed"):
            parser.exit(1, "Explicit model unconfirmed. No task content sent.\n")
    except (TimeoutError, subprocess.SubprocessError, OSError, ValueError, queue.Empty) as error:
        parser.exit(1, f"Qualification stopped: {error.__class__.__name__}: {error}\n")
