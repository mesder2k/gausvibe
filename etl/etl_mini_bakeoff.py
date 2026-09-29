#!/usr/bin/env python3
"""Mini-bakeoff harvester: per-child token usage from unified-harness journals.

Usage:
  python3 etl/etl_mini_bakeoff.py --child <name>=<child-dir> [--child <name>=<child-dir> ...]

Sums LLM token usage from journal/*.jsonl (payload.result.result.usage) and
tool-call counts plus result bytes from chunks/*.json, per named child session.
"""
import argparse
import json
from pathlib import Path


def harvest(cdir: Path):
    toks = {"input": 0, "cached_input": 0, "output": 0, "llm_calls": 0}
    for jf in cdir.glob("journal/*.jsonl"):
        with open(jf) as fh:
            for line in fh:
                try:
                    rec = json.loads(line)
                except Exception:
                    continue
                u = ((rec.get("payload") or {}).get("result") or {})
                u = (u.get("result") or {}).get("usage")
                if u:
                    toks["input"] += u.get("input_tokens", 0)
                    toks["cached_input"] += u.get("cached_input_tokens", 0)
                    toks["output"] += u.get("output_tokens", 0)
                    toks["llm_calls"] += 1

    calls, result_bytes, tools = 0, 0, []
    for cf in sorted(cdir.glob("chunks/*.json")):
        arr = json.loads(cf.read_text())
        for e in arr:
            msg = e.get("message") or {}
            role = msg.get("role")
            if role == "assistant":
                for p in msg.get("content") or []:
                    if p.get("type") == "tool_call":
                        calls += 1
                        tools.append(p.get("name", "?"))
            elif role == "tool":
                result_bytes += sum(
                    len(t.get("text", "")) for t in (msg.get("content") or []) if isinstance(t, dict)
                )
    return toks, calls, result_bytes, tools


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--child", action="append", required=True,
                    help="<name>=<child-session-dir>, repeatable")
    args = ap.parse_args()

    for spec in args.child:
        name, _, path = spec.partition("=")
        toks, calls, result_bytes, tools = harvest(Path(path))
        print(f"{name}: llm_calls={toks['llm_calls']} "
              f"input={toks['input']} cached_input={toks['cached_input']} "
              f"output={toks['output']} tool_calls={calls} "
              f"result_bytes={result_bytes}")
        print(f"  tools: {tools}")


if __name__ == "__main__":
    main()
