#!/usr/bin/env python3
"""Round-4 attribution harvester: per-request token usage + context-content
attribution from unified-harness child session journals/chunks.

For each named child session:
- per-LLM-call usage series (input/cached/uncached/output and the delta vs
  the previous call = what that turn added to the context)
- tool-result bytes by tool (skill, search_tool_functions, run_typescript,
  bash, read_file, ...)
- assistant tool-call argument bytes by tool (the TS-program / command tax)
- per-call list of (tool, result_bytes) in order, for question-level mapping

Usage:
  python3 etl/etl_bakeoff_attribution.py --child <name>=<dir> [...] [--out out.json]
"""
import argparse
import json
from pathlib import Path


def usage_series(cdir: Path):
    out = []
    for jf in sorted(cdir.glob("journal/*.jsonl")):
        with open(jf) as fh:
            for line in fh:
                try:
                    rec = json.loads(line)
                except Exception:
                    continue
                p = rec.get("payload") or {}
                if rec.get("type") != "action_result":
                    continue
                res = p.get("result") or {}
                rr = res.get("result") if isinstance(res.get("result"), dict) else None
                u = (rr or {}).get("usage")
                if u and u.get("input_tokens"):
                    out.append(u)
    return out


def chunk_events(cdir: Path):
    """Conversation events in order: assistant tool-call args, tool results."""
    events = []
    for cf in sorted(cdir.glob("chunks/*.json")):
        arr = json.loads(cf.read_text())
        for e in arr:
            msg = e.get("message") or {}
            role = msg.get("role")
            if role == "assistant":
                for c in msg.get("content") or []:
                    if isinstance(c, dict) and c.get("type") == "tool_call":
                        name = c.get("name", "?")
                        args_b = len(json.dumps(c.get("function") or c.get("arguments") or {}))
                        events.append(("call", name, args_b))
            elif role == "tool":
                name = msg.get("name") or "?"
                b = sum(len(t.get("text", "")) for t in (msg.get("content") or [])
                        if isinstance(t, dict))
                events.append(("result", name, b))
    return events


def harvest(cdir: Path):
    us = usage_series(cdir)
    ev = chunk_events(cdir)

    calls = len(us)
    deltas = []
    prev = 0
    for u in us:
        deltas.append(u["input_tokens"] - prev)
        prev = u["input_tokens"]

    agg_results, agg_args = {}, {}
    for kind, name, b in ev:
        if kind == "result":
            agg_results[name] = agg_results.get(name, 0) + b
        else:
            agg_args[name] = agg_args.get(name, 0) + b

    base = deltas[0] if deltas else 0
    return {
        "llm_calls": calls,
        "input": sum(u["input_tokens"] for u in us),
        "cached_input": sum(u["cached_input_tokens"] for u in us),
        "uncached_input": sum(u["input_tokens"] - u["cached_input_tokens"] for u in us),
        "output": sum(u["output_tokens"] for u in us),
        "series": [
            {"call": i + 1, "input": u["input_tokens"],
             "cached": u["cached_input_tokens"],
             "uncached": u["input_tokens"] - u["cached_input_tokens"],
             "output": u["output_tokens"], "added": d}
            for i, (u, d) in enumerate(zip(us, deltas))
        ],
        "base_first_call": base,
        "result_bytes_by_tool": agg_results,
        "call_args_bytes_by_tool": agg_args,
        "events": [{"kind": k, "tool": n, "bytes": b} for k, n, b in ev],
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--child", action="append", required=True,
                    help="<name>=<child-session-dir>, repeatable")
    ap.add_argument("--out", default=None, help="optional JSON output path")
    args = ap.parse_args()

    report = {}
    for spec in args.child:
        name, _, path = spec.partition("=")
        r = harvest(Path(path))
        report[name] = r
        print(f"{name}: calls={r['llm_calls']} input={r['input']} "
              f"uncached={r['uncached_input']} out={r['output']} base={r['base_first_call']}")
        print(f"  series: {[s['added'] for s in r['series']]}")
        print(f"  result bytes: {r['result_bytes_by_tool']}")
        print(f"  call-arg bytes: {r['call_args_bytes_by_tool']}")

    print("\n== arm comparison ==")
    print(f"{'arm':12s} {'calls':>5s} {'input':>7s} {'uncached':>8s} {'resB':>7s}")
    for name, r in report.items():
        resb = sum(r["result_bytes_by_tool"].values())
        print(f"{name:12s} {r['llm_calls']:5d} {r['input']:7d} "
              f"{r['uncached_input']:8d} {resb:7d}")

    if args.out:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out).write_text(json.dumps(report, indent=1))
        print(f"\nwrote {args.out}")


if __name__ == "__main__":
    main()
