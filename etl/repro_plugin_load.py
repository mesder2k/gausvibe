"""Run Vibe's full PluginResolver over the user plugins dir, printing MCP results + issues."""
import sys

sys.path.insert(0, "/Users/magnusfind/.local/share/uv/tools/mistral-vibe/lib/python3.12/site-packages")

from pathlib import Path
from vibe.core.plugins._native import PluginResolver  # noqa: E402

resolver = PluginResolver(
    user_roots=[Path.home() / ".vibe/plugins"],
    data_root_base=Path.home() / ".vibe/plugins-data",
)
resolved = resolver.resolve()

for p in getattr(resolved, "plugins", []):
    if getattr(p, "name", "") != "gausvibe":
        continue
    print("plugin:", p.name)
    issues = getattr(p, "issues", None) or getattr(resolver, "issues", [])
    for i in issues:
        print("ISSUE:", i)
    for s in getattr(p, "mcp_servers", ()):
        print("MCP:", s.source_id, "->", type(s.server).__name__, getattr(s.server, "command", None))

# also dump anything the resolver collected globally
for attr in ("issues",):
    val = getattr(resolved, attr, getattr(resolver, attr, []))
    for i in val:
        print("GLOBAL ISSUE:", i)
