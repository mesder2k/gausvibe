"""Validate the gausvibe plugin's mcp.json against Vibe's own schema models."""
import json
import sys

sys.path.insert(0, "/Users/magnusfind/.local/share/uv/tools/mistral-vibe/lib/python3.12/site-packages")

from vibe.core.plugins import _native  # noqa: E402

path = "/Users/magnusfind/.vibe/plugins/gausvibe/mcp.json"
with open(path) as fh:
    raw = json.load(fh)

try:
    config = _native._MCPConfiguration.model_validate(raw)
except Exception as e:
    print("CONFIG-LEVEL FAIL:", e)
    sys.exit(1)

for name, server in config.mcp_servers.items():
    try:
        parsed = _native._MCPStdioServer.model_validate(server)
        print(f"OK  {name}: stdio command={parsed.command} args={len(parsed.args)}")
    except Exception as e:
        try:
            parsed = _native._MCPStreamableHTTPServer.model_validate(server)
            print(f"OK  {name}: streamable-http")
        except Exception:
            print(f"SERVER-LEVEL FAIL {name}:", e)
            sys.exit(1)
print("mcp.json valid")
