"""Drive the GausVibe MCP server's deterministic tools via the mcp SDK.

Verifies the non-ask tools end to end against the daemon on :8094:
tools/list advertises them, classes enforces filters, and each proxy
returns the daemon's payload.

Usage:
  python3 etl/repro_deterministic_tools.py
"""
import asyncio
import json
import sys

sys.path.insert(0, "/Users/magnusfind/.local/share/uv/tools/mistral-vibe/lib/python3.12/site-packages")

from mcp import ClientSession, StdioServerParameters  # noqa: E402
from mcp.client.stdio import stdio_client  # noqa: E402


async def call(session, name, args):
    result = await session.call_tool(name, args)
    text = "\n".join(getattr(c, "text", "") for c in result.content)
    return {"isError": result.isError, "text": text}


async def main():
    params = StdioServerParameters(
        command="java",
        args=[
            "-cp",
            open("/tmp/gv-mcp-cp.txt").read().strip(),
            "dk.gausdalfind.server.GausVibeMcpServer",
            "--url", "http://localhost:8094",
        ],
    )
    try:
        async with stdio_client(params) as (read, write):
            async with ClientSession(read, write) as session:
                await session.initialize()
                tools = await session.list_tools()
                names = [t.name for t in tools.tools]
                print("tools:", names)
                expected = ["ask", "tests", "callpath", "search", "class_detail",
                            "class_members", "classes", "packages", "edited",
                            "feedback", "changes", "stats"]
                missing = [t for t in expected if t not in names]
                print("missing:", missing or "none")

                checks = [
                    ("search", {"name": "GausVibeServer"}),
                    ("classes", {}),
                    ("classes", {"package": "dk.gausdalfind.queries"}),
                    ("class_members", {"class_fqn": "dk.gausdalfind.server.GausVibeMcpServer"}),
                    ("class_detail", {"class_fqn": "dk.gausdalfind.queries.GraphQueryEngine"}),
                    ("packages", {}),
                ]
                ok = True
                for name, args in checks:
                    r = await call(session, name, args)
                    print(f"\n--- {name} {json.dumps(args)}")
                    print("isError:", r["isError"])
                    print(r["text"][:400])
                    if r["isError"] and name != "classes":
                        ok = False
                    if name == "classes" and not args and not r["isError"]:
                        ok = False
                print("\nVERDICT:", "PASS" if (ok and not missing) else "FAIL")
    except Exception as e:
        print("FAILED:", type(e).__name__, str(e)[:500])
        sys.exit(1)


asyncio.run(main())
