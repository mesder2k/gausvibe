#!/usr/bin/env python3
"""Byte distribution of file reads across historical Vibe sessions.

Tests the slice-endpoint premise: slicing pays off only when the read target
is much larger than a method body (~1-2KB). Prints the distribution of
read-file result bytes, the share of bytes from large files, and the
read-amplification ratio (bytes read / file count).

Usage:
  python3 etl/read_bytes_dist.py [--log-dir ~/.vibe/logs/session]
"""
import argparse
import json
import os
from pathlib import Path

BUCKETS = [0, 1024, 4096, 16384, 65536, 1 << 20]


def bucket_label(n):
    for i in range(len(BUCKETS) - 1, -1, -1):
        if n >= BUCKETS[i]:
            if i + 1 < len(BUCKETS):
                return f"{BUCKETS[i]//1024}k-{BUCKETS[i+1]//1024}k"
            return f">{BUCKETS[i]//1024}k"
    return "<1k"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log-dir", default=os.path.expanduser("~/.vibe/logs/session"))
    args = ap.parse_args()

    sizes = []
    for sd in sorted(Path(args.log_dir).glob("session_*")):
        mf = sd / "messages.jsonl"
        if not mf.exists():
            continue
        with open(mf) as fh:
            for line in fh:
                try:
                    msg = json.loads(line)
                except Exception:
                    continue
                if msg.get("role") != "tool":
                    continue
                # match to preceding assistant tool_call is complex; use the
                # tool result size for read-shaped calls only. We approximate
                # by reading tool_call ids from assistant messages.
                sizes.append(len(str(msg.get("content") or "")))
    # The above counts every tool result; refine below by re-walking with ids.
    sizes = []
    pending = {}
    for sd in sorted(Path(args.log_dir).glob("session_*")):
        mf = sd / "messages.jsonl"
        if not mf.exists():
            continue
        with open(mf) as fh:
            for line in fh:
                try:
                    msg = json.loads(line)
                except Exception:
                    continue
                role = msg.get("role")
                if role == "assistant":
                    for call in msg.get("tool_calls") or []:
                        func = call.get("function") or {}
                        if func.get("name") in ("read_file", "cat", "head", "tail", "sed"):
                            pending[call.get("id")] = func.get("name")
                elif role == "tool":
                    if msg.get("tool_call_id") in pending:
                        sizes.append(len(str(msg.get("content") or "")))
                        pending.pop(msg.get("tool_call_id"), None)

    if not sizes:
        print("no read results found")
        return

    sizes.sort()
    total = sum(sizes)
    n = len(sizes)
    print(f"read-shaped tool results: {n}, total bytes: {total/1e6:.2f} MB")
    print(f"median: {sizes[n//2]}  p90: {sizes[int(n*0.9)]}  max: {sizes[-1]}")

    by_bucket = {}
    for s in sizes:
        b = bucket_label(s)
        by_bucket.setdefault(b, [0, 0])
        by_bucket[b][0] += 1
        by_bucket[b][1] += s
    print(f"{'bucket':12s} {'count':>6s} {'bytes':>12s} {'byte share':>10s}")
    for b in sorted(by_bucket, key=lambda k: min(int(x) for x in k.replace(">", "").replace("k", "").split("-") if x)):
        c, bs = by_bucket[b]
        print(f"{b:12s} {c:6d} {bs:12d} {100*bs/total:9.1f}%")

    big = sum(s for s in sizes if s >= 16384)
    print(f"\nshare of bytes from results >= 16KB: {100*big/total:.1f}%")
    print(f"count of results >= 16KB: {sum(1 for s in sizes if s >= 16384)} of {n}")


if __name__ == "__main__":
    main()
