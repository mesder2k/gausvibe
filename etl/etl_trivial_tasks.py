#!/usr/bin/env python3
"""ETL pass 2: trivial task types where the harness burns many tokens.

Reconstructs one full transcript per trajectory (cumulative derivation ->
row with max n_messages), attributes estimated tokens to their source
(prompt, assistant text, tool args, per-tool results, test-harness output),
measures the deliverable (written/edited LOC), classifies the task type,
and ranks categories by token burn relative to how trivial the deliverable is.

Usage:
  python3 etl_trivial_tasks.py --data-dir /path/to/data --output-dir etl/
"""
import argparse
import glob
import json
import os
import re
import sys
from collections import Counter, defaultdict

import pyarrow.parquet as pq

CHARS_PER_TOKEN = 4.0


def est_tokens(s):
    return len(s) / CHARS_PER_TOKEN if s else 0.0


def parse_args_json(a):
    try:
        return json.loads(a) if a else {}
    except Exception:
        return {}


# ---- bash command classification -------------------------------------------

RE_TEST_RUN = re.compile(
    r"(test_[a-z0-9_]*\.(sh|py)|run_verify|pytest|go test|cargo test|"
    r"make (test|check)|npm test|node --test|unittest|\.test\.)", re.I)
RE_ENV_PROBE = re.compile(r"(--version|uname|which\s+\w+|^id\b)", re.I)
RE_EXPLORE = re.compile(r"^\s*(ls|find|grep|rg|cat|head|tail|wc|file|stat|sed -n|awk|tree|du)\b")
RE_INSTALL = re.compile(r"(npm (ci|install)|pip3? install|cargo (build|update)|go (mod|get)|apt|brew)", re.I)


def classify_bash(cmd):
    if RE_TEST_RUN.search(cmd):
        return "test-run"
    if RE_INSTALL.search(cmd):
        return "install"
    if RE_EXPLORE.match(cmd):
        return "explore"
    if RE_ENV_PROBE.search(cmd):
        return "env-probe"
    return "other"


# ---- task-type classification ----------------------------------------------

RE_GIVEN_SPEC = re.compile(
    r"(bitvec\.h|\.hpp|\.h\b|interface|API|signature|skeleton|stub|"
    r"two new files|single file|package \w+|C17|dependency-free|"
    r"I want (a|the)|I need (a|the|an))", re.I)


def classify(row, deliverable_lines, n_write_edit_calls, has_write):
    user = next((m["content"] for m in row["messages"] if m["role"] == "user"), "") or ""
    if row["category"] == "dependency-planning":
        return "dependency-planning"
    if not has_write:  # no write/edit tool calls at all
        return "env-repair"
    if n_write_edit_calls <= 2 and deliverable_lines <= 10:
        return "one-edit-fix"
    if deliverable_lines <= 60 and n_write_edit_calls <= 3:
        return "small-module"
    if RE_GIVEN_SPEC.search(user):
        return "spec-interface-impl"
    return "other"


# ---- trajectory reconstruction ---------------------------------------------

def load_trajectories(data_dir):
    shas = {}
    for f in sorted(glob.glob(os.path.join(data_dir, "*.parquet"))):
        for row in pq.read_table(f).to_pylist():
            key = row["source_trajectory_sha256"]
            prev = shas.get(key)
            if prev is None or row["n_messages"] > prev["n_messages"]:
                shas[key] = row
    return list(shas.values())


def analyze(row):
    msgs = row["messages"]
    user_msg = next((m["content"] for m in msgs if m["role"] == "user"), "")

    tok = defaultdict(float)          # token attribution
    tool_counts = Counter()
    bash_classes = Counter()
    cmd_counter = Counter()          # repeated identical commands
    deliverable_lines = 0
    files_touched = set()
    n_write_edit = 0
    first_write_idx = None
    test_runs = 0
    test_fail_outputs = 0
    callid_to_kind = {}              # tool_call_id -> (tool, bash class)

    prompt_tok = sum(est_tokens(m["content"]) for m in msgs if m["role"] in ("system", "user"))

    for i, m in enumerate(msgs):
        if m["role"] == "assistant":
            tok["assistant_text"] += est_tokens(m["content"])
            tok["reasoning"] += est_tokens(m["reasoning_content"])
        for tc in m["tool_calls"] or []:
            fn = tc["function"]["name"]
            args = parse_args_json(tc["function"]["arguments"])
            arg_str = tc["function"]["arguments"] or ""
            tool_counts[fn] += 1
            kind = fn
            if fn == "bash":
                cmd = args.get("command") or ""
                cls = classify_bash(cmd)
                bash_classes[cls] += 1
                cmd_counter[cmd] += 1
                kind = f"bash:{cls}"
                if cls == "test-run":
                    test_runs += 1
            callid_to_kind[tc["id"]] = kind
            tok[f"args:{fn}"] += est_tokens(arg_str)

            if fn == "write":
                n_write_edit += 1
                deliverable_lines += (args.get("content") or "").count("\n") + 1
                if args.get("path"):
                    files_touched.add(args["path"])
                if first_write_idx is None:
                    first_write_idx = i
            elif fn == "edit":
                n_write_edit += 1
                edits = args.get("edits") or []
                if isinstance(edits, dict):
                    edits = [edits]
                for e in edits:
                    deliverable_lines += (e.get("newText") or "").count("\n") + 1
                if args.get("path"):
                    files_touched.add(args["path"])
                if first_write_idx is None:
                    first_write_idx = i
            elif fn == "bash":
                cmd = args.get("command") or ""
                if RE_TEST_RUN.search(cmd):
                    pass  # counted above
                if first_write_idx is None and re.search(
                        r"cat\s*<<|tee\s|>\s*[\w./-]+\.(sh|py|go|rs|c|h|cpp|js|ts|rb|java)", cmd):
                    first_write_idx = i
                    deliverable_lines += max(1, cmd.count("\n"))

        if m["role"] == "tool":
            kind = callid_to_kind.get(m["tool_call_id"], "tool:unknown")
            tok[f"result:{kind}"] += est_tokens(m["content"])
            if kind == "bash:test-run" and re.search(r"\bFAIL\b|failed|panicked|error:|✖", m["content"] or ""):
                test_fail_outputs += 1

    # tokens spent before the first file mutation = pure exploration/orientation
    explore_tok = 0.0
    if first_write_idx is not None:
        for i in range(first_write_idx):
            m = msgs[i]
            explore_tok += est_tokens(m["content"]) + est_tokens(m["reasoning_content"])
            for tc in m["tool_calls"] or []:
                explore_tok += est_tokens(tc["function"]["arguments"])

    result_tok = sum(v for k, v in tok.items() if k.startswith("result:"))
    total_tok = prompt_tok + tok["assistant_text"] + tok["reasoning"] + \
        sum(v for k, v in tok.items() if k.startswith("args:")) + result_tok
    repeat_cmds = sum(c - 1 for c in cmd_counter.values() if c > 1)

    return {
        "task": row["task"], "lang": row["lang"], "category": row["category"],
        "task_type": classify(row, deliverable_lines, n_write_edit, n_write_edit > 0),
        "assistant_steps": row["assistant_steps"],
        "total_tokens": total_tok, "prompt_tokens": prompt_tok,
        "assistant_text_tokens": tok["assistant_text"],
        "reasoning_tokens": tok["reasoning"],
        "tool_args_tokens": sum(v for k, v in tok.items() if k.startswith("args:")),
        "tool_result_tokens": result_tok,
        "read_result_tokens": tok.get("result:read", 0),
        "ls_result_tokens": tok.get("result:ls", 0),
        "bash_result_tokens": sum(v for k, v in tok.items()
                                  if k.startswith("result:bash:")),
        "test_output_tokens": tok.get("result:bash:test-run", 0),
        "install_output_tokens": sum(v for k, v in tok.items()
                                     if k.startswith("result:bash:install")),
        "explore_tokens": explore_tok,
        "deliverable_lines": deliverable_lines, "files_touched": len(files_touched),
        "write_edit_calls": n_write_edit,
        "tool_calls": dict(tool_counts), "bash_classes": dict(bash_classes),
        "test_runs": test_runs, "test_fail_outputs": test_fail_outputs,
        "repeated_commands": repeat_cmds,
        "user_msg": user_msg[:300],
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", default="/tmp/glm-etraces/data")
    ap.add_argument("--output-dir", default=".")
    args = ap.parse_args()

    rows = load_trajectories(args.data_dir)
    results = [analyze(r) for r in rows]
    print(f"trajectories: {len(rows)}")

    by_type = defaultdict(list)
    for r in results:
        by_type[r["task_type"]].append(r)

    print("\n== Task-type aggregate ==")
    hdr = (f"{'type':22s} {'n':>3s} {'med_tok':>9s} {'med_LOC':>8s} {'med_tok/LOC':>11s} "
           f"{'explore%':>8s} {'test_out%':>9s} {'read_out%':>9s}")
    print(hdr)
    agg = []
    for t, rs in by_type.items():
        def med(key):
            v = sorted(x[key] for x in rs)
            return v[len(v) // 2]
        med_tok, med_loc = med("total_tokens"), med("deliverable_lines")
        tot = sum(x["total_tokens"] for x in rs) or 1
        agg.append({
            "task_type": t, "n": len(rs), "median_tokens": med_tok,
            "median_loc": med_loc,
            "median_tokens_per_loc": med_tok / max(med_loc, 1),
            "explore_share": sum(x["explore_tokens"] for x in rs) / tot,
            "test_output_share": sum(x["test_output_tokens"] for x in rs) / tot,
            "read_result_share": sum(x["read_result_tokens"] for x in rs) / tot,
            "total_tokens": sum(x["total_tokens"] for x in rs),
        })
    for a in sorted(agg, key=lambda x: -x["median_tokens_per_loc"]):
        print(f"{a['task_type']:22s} {a['n']:3d} {a['median_tokens']:9.0f} "
              f"{a['median_loc']:8.0f} {a['median_tokens_per_loc']:11.0f} "
              f"{a['explore_share']*100:7.1f}% {a['test_output_share']*100:8.1f}% "
              f"{a['read_result_share']*100:8.1f}%")

    print("\n== One-edit-fix: trivial deliverable, full burn ==")
    for r in sorted((x for x in results if x["task_type"] == "one-edit-fix"),
                    key=lambda x: -x["total_tokens"]):
        print(f"{r['task']:30s} {r['lang']:8s} tok={r['total_tokens']:7.0f} "
              f"LOC={r['deliverable_lines']:3d} explore%={r['explore_tokens']/r['total_tokens']*100:4.1f} "
              f"test_out={r['test_output_tokens']:5.0f} reads={r['tool_calls'].get('read',0):2d} "
              f"test_runs={r['test_runs']} fails={r['test_fail_outputs']}")

    print("\n== Env-repair / dependency-planning (zero or tiny code deliverable) ==")
    for r in sorted((x for x in results if x["task_type"] in ("env-repair", "dependency-planning")),
                    key=lambda x: -x["total_tokens"]):
        print(f"{r['task']:40s} {r['lang']:8s} tok={r['total_tokens']:7.0f} "
              f"LOC={r['deliverable_lines']:3d} steps={r['assistant_steps']:2d} "
              f"install_out={r['install_output_tokens']:5.0f} repeats={r['repeated_commands']}")

    print("\n== Spec/interface-impl: orientation cost before first write ==")
    si = [x for x in results if x["task_type"] == "spec-interface-impl"]
    if si:
        tot = sum(x["total_tokens"] for x in si)
        print(f"n={len(si)} total={tot:.0f} explore%={sum(x['explore_tokens'] for x in si)/tot*100:.1f} "
              f"read_out%={sum(x['read_result_tokens'] for x in si)/tot*100:.1f}")
        for r in sorted(si, key=lambda x: -x["explore_tokens"] / x["total_tokens"])[:10]:
            print(f"  {r['task']:30s} tok={r['total_tokens']:7.0f} LOC={r['deliverable_lines']:4d} "
                  f"explore%={r['explore_tokens']/r['total_tokens']*100:4.1f}")

    print("\n== Waste signals (whole dataset) ==")
    tot = sum(x["total_tokens"] for x in results)
    print(f"total tokens: {tot:.0f}")
    for label, val in [
        ("test-harness output tokens", sum(x["test_output_tokens"] for x in results)),
        ("read-file result tokens", sum(x["read_result_tokens"] for x in results)),
        ("install/env output tokens", sum(x["install_output_tokens"] for x in results)),
        ("exploration before first write", sum(x["explore_tokens"] for x in results)),
        ("repeated identical commands", sum(x["repeated_commands"] for x in results)),
    ]:
        print(f"  {label:35s} {val:10.0f}  ({val/tot*100:4.1f}%)")

    out = os.path.join(args.output_dir, "trivial_tasks_results.json")
    with open(out, "w") as f:
        json.dump({"aggregate": agg, "trajectories": results}, f, indent=1, default=str)
    print(f"\nwrote {out}")


if __name__ == "__main__":
    main()
