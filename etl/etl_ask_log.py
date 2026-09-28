#!/usr/bin/env python3
"""
Summarize GausVibe /ask logs (ask-log.jsonl).

Reads one or more ask-log files written by QuestionLogger and reports:
  - total questions and hit rate
  - distribution of matched query types
  - avg answer size per type
  - full list of unmatched questions (the index backlog)

Usage:
    python3 etl_ask_log.py [--log PATH ...] [--out-dir <this dir>]

Default log path: <project>/.gausvibe/ask-log.jsonl for each sibling project
of the gausvibe repo that contains one.
"""

import argparse
import json
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path


def find_default_logs():
    base = Path(__file__).resolve().parent.parent.parent
    logs = []
    for d in sorted(base.iterdir()):
        if d.is_dir():
            p = d / ".gausvibe" / "ask-log.jsonl"
            if p.exists():
                logs.append(p)
    return logs


def parse_log(path: Path):
    entries = []
    with open(path) as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                entries.append(json.loads(line))
            except json.JSONDecodeError:
                print(f"skip malformed line in {path}: {line[:80]}", file=sys.stderr)
    return entries


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log", action="append", default=None,
                    help="ask-log.jsonl file (repeatable); default: auto-discover sibling projects")
    ap.add_argument("--out-dir", default=str(Path(__file__).parent))
    args = ap.parse_args()

    log_paths = [Path(p) for p in args.log] if args.log else find_default_logs()
    if not log_paths:
        print("No ask logs found. Pass --log /path/to/ask-log.jsonl", file=sys.stderr)
        sys.exit(1)

    entries = []
    for p in log_paths:
        entries.extend(parse_log(p))

    matched = Counter()
    bytes_by = defaultdict(int)
    unmatched = []
    projects = Counter()
    for e in entries:
        projects[e.get("project", "?")] += 1
        m = e.get("matched")
        if m is None:
            unmatched.append(e)
        else:
            matched[m] += 1
        bytes_by[m] += e.get("answer_bytes", 0)

    total = len(entries)
    hit = sum(matched.values())
    report = {
        "generated_at": datetime.now().isoformat(),
        "logs": [str(p) for p in log_paths],
        "projects": dict(projects),
        "total_questions": total,
        "answered": hit,
        "unanswered": total - hit,
        "hit_rate": round(hit / total, 4) if total else None,
        "matched_distribution": {
            m: {
                "count": c,
                "share": round(c / total, 4),
                "avg_answer_bytes": round(bytes_by[m] / c, 1),
            } for m, c in matched.most_common()
        },
        "unmatched_questions": [
            {"question": e.get("question"), "identifiers": e.get("identifiers"),
             "reason": e.get("reason"), "project": e.get("project")}
            for e in unmatched
        ],
    }

    out = Path(args.out_dir) / "ask_log_report.json"
    out.write_text(json.dumps(report, indent=2))

    lines = []
    lines.append("# GausVibe /ask log summary\n")
    lines.append(f"Generated: {report['generated_at']}")
    lines.append(f"Logs: {', '.join(report['logs'])}")
    lines.append(f"Projects: {dict(projects)}")
    lines.append(f"Questions: {total} | answered: {hit} | unanswered: {total - hit} "
                 f"| hit rate: {report['hit_rate'] * 100 if report['hit_rate'] is not None else 0:.1f}%\n")
    lines.append("## Matched question types\n")
    lines.append("| type | count | share | avg answer bytes |")
    lines.append("|---|---|---|---|")
    for m, d in report["matched_distribution"].items():
        lines.append(f"| {m} | {d['count']} | {d['share']*100:.1f}% | {d['avg_answer_bytes']} |")
    lines.append("\n## Unmatched questions (index backlog)\n")
    for e in report["unmatched_questions"]:
        ident = ", ".join(e.get("identifiers") or [])
        lines.append(f"- \"{e['question']}\"{f' [{ident}]' if ident else ''}")
    md = Path(args.out_dir) / "ask_log_report.md"
    md.write_text("\n".join(lines))

    print(f"wrote {out}")
    print(f"wrote {md}")
    print(f"questions: {total}, hit rate: "
          f"{report['hit_rate']*100 if report['hit_rate'] is not None else 0:.1f}%")
    for m, d in report["matched_distribution"].items():
        print(f"  {m:16s} {d['count']:4d} ({d['share']*100:4.1f}%)")
    print(f"  unmatched:       {total-hit:4d}")


if __name__ == "__main__":
    main()
