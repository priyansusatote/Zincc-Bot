"""Zincc's curated read-only MCP tool. Transport is newline-delimited JSON-RPC."""
import json
import sys

schema = {"type": "object", "properties": {"sources": {"type": "array",
          "items": {"type": "string", "maxLength": 600}, "maxItems": 12}},
          "required": ["sources"], "additionalProperties": False}
initialized = False
for line in sys.stdin.buffer:
    if len(line) > 40000:
        break
    request = json.loads(line)
    method = request.get("method")
    if method == "initialize":
        result = {"protocolVersion": "2025-06-18", "capabilities": {"tools": {}},
                  "serverInfo": {"name": "zincc-briefing", "version": "1.0.0"}}
    elif method == "notifications/initialized":
        initialized = True
        continue
    elif initialized and method == "tools/list":
        result = {"tools": [{"name": "briefing", "description": "Format approved source summaries.",
                             "inputSchema": schema}]}
    elif initialized and method == "tools/call" and request["params"]["name"] == "briefing":
        args = request["params"]["arguments"]
        sources = args["sources"]
        if set(args) != {"sources"} or not isinstance(sources, list) or len(sources) > 12 or any(
                not isinstance(s, str) or len(s) > 600 for s in sources):
            break
        result = {"content": [{"type": "text", "text": "\n".join(sources) or "No recent items."}]}
    else:
        break
    print(json.dumps({"jsonrpc": "2.0", "id": request["id"], "result": result}), flush=True)
