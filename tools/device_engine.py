"""Foreground device protocol. Each turn needs a confirmed model before receiving task content."""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import tempfile

from engine_output import opencode_result
from engine_probe import (FREE_MODEL, TEXT_AGENT, confirm_selection, inspect_opencode,
                          isolated_environment, rpc_process)


def send(**fields):
    print(json.dumps(fields), flush=True)


def receive():
    line = sys.stdin.buffer.readline(65537)
    if not line:
        raise EOFError
    if len(line) > 65536 or not line.endswith(b"\n"):
        raise ValueError("Exceeded device message limit")
    return json.loads(line)


def content():
    task=receive()
    goal, instructions=task["goal"],task["instructions"]
    if not isinstance(goal,str) or not 0<len(goal)<=2000 or not isinstance(instructions,str) or len(instructions)>8000:
        raise ValueError("invalid-task")
    return instructions+"\n\n"+goal


def free_engine(binary, work, env):
    inspect_opencode(binary,work,env)
    send(models=[FREE_MODEL])
    while True:
        model=receive()["model"]
        if model!=FREE_MODEL:
            raise ValueError("model")
        inspect_opencode(binary,work,env)
        send(acknowledged=model)
        prompt=content()
        def command(args,stdin=None):
            return subprocess.run([binary,*args,"--standalone"],input=stdin,encoding="utf8",cwd=work,env=env,
                                  stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=70)
        def export(session):
            if not isinstance(session,str) or not re.fullmatch(r"ses_[a-zA-Z0-9_]{1,120}",session):
                raise ValueError("unconfirmed")
            result=command(["session","export",session])
            result.check_returncode()
            return json.loads(result.stdout)
        result=command(["run","--format","json","--agent",TEXT_AGENT,"--model",model],prompt)
        try:
            send(answer=opencode_result(result.stdout,result.returncode,model,export))
        except ValueError as error:
            send(failure=str(error) if str(error) in ("allowance","authorization","model") else "unconfirmed")


def google_engine(binary, work, env):
    # The provider owns personal OAuth. Keep API-key/ADC credentials out of the child.
    settings={"useG1Credits":False,"toolPermission":"strict","agentMode":"plan",
              "permissions":{"deny":[k+"(*)" for k in ("command","read_file","write_file","read_url",
                                                        "execute_url","mcp","unsandboxed")]}}
    settings_dir=Path(env["USERPROFILE"] if os.name=="nt" else env["HOME"])/".gemini"/"antigravity-cli"
    settings_dir.mkdir(parents=True,exist_ok=True)
    (settings_dir/"settings.json").write_text(json.dumps(settings),encoding="utf8")
    with rpc_process(binary,["acp","--sandbox","--disable-telemetry","--include-default-customizations=false"],work,env) as rpc:
        hello=rpc.call("initialize",{"protocolVersion":1,"clientCapabilities":{}})
        if not any(m.get("id")=="oauth-personal" for m in hello.get("authMethods",[])):
            raise ValueError("authorization")
        try:
            rpc.call("authenticate",{"methodId":"oauth-personal"},timeout=240)
        except ValueError as error:
            raise ValueError("authorization") from error
        def session():
            return rpc.call("session/new",{"cwd":str(work),"mcpServers":[]})
        first=session()
        option=next(o for o in first.get("configOptions",[]) if o.get("category")=="model")
        models=[m["value"] for m in option["options"] if isinstance(m.get("value"),str)]
        if not models:
            raise ValueError("model")
        send(models=models)
        while True:
            model=receive()["model"]
            current=first or session()
            first=None
            if model not in models or not confirm_selection(rpc,current,model):
                raise ValueError("model")
            send(acknowledged=model)
            rpc.text=""
            rpc.tool_requested=False
            result=rpc.call("session/prompt",{"sessionId":current["sessionId"],"prompt":[{"type":"text","text":content()}]},timeout=68)
            if rpc.tool_requested or result.get("stopReason")!="end_turn" or not rpc.text.strip():
                send(failure="unconfirmed")
            else:
                send(answer=rpc.text)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("provider",choices=["opencode-free","antigravity"])
    parser.add_argument("binary",type=Path)
    args=parser.parse_args()
    if not args.binary.is_absolute() or not args.binary.is_file() or os.getenv("ZINCC_LOCAL_ONLY","").lower() in ("1","true"):
        raise ValueError("Invalid executable or local-only policy")
    with tempfile.TemporaryDirectory(prefix="zincc-device-",ignore_cleanup_errors=True) as tmp:
        root=Path(tmp)
        env=isolated_environment(root)
        work=root/"empty-project"
        work.mkdir()
        if args.provider=="opencode-free":
            free_engine(str(args.binary),work,env)
        else:
            google_engine(str(args.binary),work,env)


if __name__=="__main__":
    try:
        main()
    except EOFError:
        pass
    except Exception as error:
        # Provider errors may contain private account information. Expose categories only.
        reason=str(error)
        send(failure=reason if reason in ("model","authorization","allowance") else "unconfirmed")
        sys.exit(1)
