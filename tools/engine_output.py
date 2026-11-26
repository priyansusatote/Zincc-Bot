"""Validate an OpenCode standalone completion against its explicit route."""
import json


def opencode_result(stdout, returncode, model, exported=None):
    text, model_ids, sessions = [], set(), set()
    finished = False
    for line in stdout.splitlines():
        if not line.strip():
            continue
        e = json.loads(line)
        event, part = e.get("type"), e.get("part") or {}
        if event == "error":
            status = (e.get("error") or {}).get("status")
            raise ValueError({401:"authorization",403:"authorization",429:"allowance"}.get(status,"unconfirmed"))
        if event in ("tool_use","tool_call") or part.get("type")=="tool":
            raise ValueError("unexpected-tool")
        if e.get("sessionID"):
            sessions.add(e["sessionID"])
        if event=="text":
            text.append(part["text"])
        if event=="step_finish" and part.get("reason")=="stop":
            finished=True
        if part.get("modelID"):
            model_ids.add((part.get("providerID"),part["modelID"]))
    if returncode or not finished or len(sessions)!=1 or not 0<len("".join(text).strip())<=32000:
        raise ValueError("unconfirmed")
    if not model_ids and exported:
        transcript = exported(next(iter(sessions)))
        if transcript.get("info",{}).get("id") != next(iter(sessions)):
            raise ValueError("model")
        for msg in transcript.get("messages",[]):
            info=msg.get("info",{})
            if info.get("role")=="assistant":
                model_ids.add((info.get("providerID"),info.get("modelID")))
                if any(p.get("type")=="tool" for p in msg.get("parts",[])):
                    raise ValueError("unexpected-tool")
    if model_ids != {tuple(model.split("/",1))}:
        raise ValueError("model")
    return "".join(text)
