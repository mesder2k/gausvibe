"""Drive the GausVibe MCP server with the same mcp SDK client Vibe uses."""
import asyncio
import sys

sys.path.insert(0, "/Users/magnusfind/.local/share/uv/tools/mistral-vibe/lib/python3.12/site-packages")

from mcp import ClientSession, StdioServerParameters  # noqa: E402
from mcp.client.stdio import stdio_client  # noqa: E402


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
                init = await session.initialize()
                print("initialize ok:", init.serverInfo.name, init.serverInfo.version)
                tools = await session.list_tools()
                print("tools:", [t.name for t in tools.tools])
                result = await session.call_tool("ask", {"question": "where is Calculator defined"})
                print("call ok, isError:", result.isError)
                for c in result.content:
                    print("text:", getattr(c, "text", "")[:120])
    except Exception as e:
        print("FAILED:", type(e).__name__, str(e)[:500])
        sys.exit(1)


asyncio.run(main())
