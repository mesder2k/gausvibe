#!/usr/bin/env python3
"""
Question-Taxonomy ETL for Vibe session logs.

Mines ~/.vibe/logs/session/*/ and answers: what questions does the harness
ask of the codebase before it is allowed to edit, and where does that cost
concentrate?

For every tool call in every session we classify:
  1. OPERATION   - what was done (search, locate, read-file, mutate, build, ...)
  2. QUESTION   - why, derived from the assistant's reasoning_content
                  (locate-symbol, harness-check, usage, shape, config, verify-edit, ...)
  3. POSITION   - before/after the session's first file mutation
  4. COST       - bytes of tool result returned (proxy for context tokens)

Outputs:
  question_taxonomy_report.json  - full machine-readable results
  question_taxonomy_report.md    - human summary

Usage:
    python3 etl_question_taxonomy.py [--log-dir ~/.vibe/logs/session] [--out-dir <this dir>]
"""

import argparse
import json
import os
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

# ---------------------------------------------------------------------------
# Operation classification
# ---------------------------------------------------------------------------

SEARCH_CMDS = {"grep", "rg", "ag", "ack"}
LOCATE_CMDS = {"find", "ls", "tree", "locate", "fd", "glob", "fdind"}
READ_CMDS = {"cat", "head", "tail", "less", "more", "nl", "sed", "awk", "bat", "od", "paste"}
META_CMDS = {"wc", "stat", "du", "file", "which", "basename", "dirname", "readlink", "shasum", "md5sum", "diff", "comm", "cmp"}
VCS_READ = {"log", "show", "status", "diff", "blame", "rev-parse", "ls-files", "describe", "branch"}
VCS_MUTATE = {"commit", "add", "checkout", "reset", "stash", "apply", "rebase", "merge", "push", "pull", "restore", "clean", "rm"}
MUTATE_CMDS = {"sed", "tee", "touch", "mkdir", "mv", "cp", "rm", "chmod", "ln", "patch", "truncate", "install"}
BUILD_CMDS = {"mvn", "gradle", "javac", "java", "pytest", "pip", "npm", "make", "cmake", "go", "cargo", "python", "python3", "pytest", "tox", "npx"}


def first_tokens(command: str, n: int):
    return command.strip().split()[:n]


def classify_bash(command: str) -> tuple:
    """Classify a bash command into (operation, is_mutation)."""
    cmd = command.strip()
    # pipes / redirects: classify by the dominant intent (leftmost meaningful stage)
    stages = [s.strip() for s in re.split(r"\||&&|;", cmd) if s.strip()]
    ops = []
    for stage in stages:
        toks = first_tokens(stage, 3)
        if not toks:
            continue
        if toks[0] == "git" and len(toks) > 1:
            sub = toks[1]
            if sub in VCS_MUTATE:
                ops.append(("git-mutate", True))
            elif sub in VCS_READ:
                ops.append(("git-history", False))
            else:
                ops.append(("git-other", False))
            continue
        base = toks[0]
        if base.startswith("./") or "/" in base:
            ops.append(("run-binary", False))
            continue
        if base in SEARCH_CMDS:
            ops.append(("search", False))
        elif base in LOCATE_CMDS:
            ops.append(("locate", False))
        elif base in READ_CMDS:
            # sed -i is a mutation
            if base == "sed" and "-i" in toks:
                ops.append(("mutate", True))
            else:
                ops.append(("read", False))
        elif base in META_CMDS:
            ops.append(("inspect", False))
        elif base in MUTATE_CMDS:
            ops.append(("mutate", True))
        elif base in BUILD_CMDS:
            ops.append(("build", False))
        elif base == "echo" or base == "printf":
            ops.append(("echo", False))
        elif base == "curl" or base == "wget":
            ops.append(("network", False))
        elif base == "cd" or base == "pwd" or base == "export" or base == "env":
            ops.append(("env", False))
        else:
            ops.append(("other", False))
    if not ops:
        return ("other", False)
    # mutation wins if any stage mutates
    is_mut = any(m for _, m in ops)
    # pick the most informative (most "readful") stage as the operation label
    priority = ["read-file", "search", "read", "locate", "git-history", "build",
                "inspect", "git-mutate", "mutate", "run-binary", "git-other",
                "network", "echo", "env", "other"]
    label = min(ops, key=lambda o: priority.index(o[0]) if o[0] in priority else len(priority))[0]
    return (label, is_mut)


def classify_call(name: str, arguments: dict) -> tuple:
    """Classify a tool call -> (operation, is_mutation, target)."""
    target = ""
    if name == "bash":
        command = arguments.get("command", "")
        op, mut = classify_bash(command)
        target = command
        return (op, mut, target)
    if name == "read_file":
        return ("read-file", False, arguments.get("path", ""))
    if name == "edit":
        return ("mutate", True, arguments.get("file_path", arguments.get("path", "")))
    if name == "write_file":
        return ("mutate", True, arguments.get("path", ""))
    if name in ("grep", "search"):
        return ("search", False, json.dumps(arguments))
    if name == "search_tool_functions":
        return ("tool-discovery", False, json.dumps(arguments))
    if name in ("write", "create", "apply_patch", "multiedit"):
        return ("mutate", True, json.dumps(arguments))
    if name in ("todo", "ask_user_question", "skill", "sleep"):
        return ("meta", False, json.dumps(arguments))
    return ("other", False, json.dumps(arguments))


# ---------------------------------------------------------------------------
# Question classification (from reasoning content)
# ---------------------------------------------------------------------------

QUESTION_RULES = [
    ("verify-edit", re.compile(
        r"verify (my|the|that)|check (my|the) (change|edit|diff)|review (the|my) diff|"
        r"confirm (the|my) (change|edit)|make sure (the|my) (change|edit)|see if (the|my) (change|edit)|"
        r"compile|ran successfully|build (succeed|pass)", re.I)),
    ("harness-check", re.compile(
        r"\btest(s|ing)?\b|assert|harness|grader|grading|evaluat|hidden (test|requirement)|"
        r"check(ing)? (that|if|whether)|pass(ing)? (the )?check|verif(y|ies|ication)", re.I)),
    ("usage", re.compile(
        r"who calls|callers|where (it|they)('s| is| are| was| were)? (used|called|referenced|imported)|"
        r"usages|references to|consumers|depends on (this|the)", re.I)),
    ("locate", re.compile(
        r"where (is|are|does|do)|which (file|class|module|package)|locat(e|ion|ing)|"
        r"find (the|a|all|any)? ?(file|class|function|method|definition|declaration|symbol|constant|module)|"
        r"look for|search for|file contains", re.I)),
    ("config-build", re.compile(
        r"pom\.xml|build\.gradle|package\.json|dependenc|compiler|classpath|build (config|file)|"
        r"makefile|settings|\.env|config(uration)? file|version of", re.I)),
    ("conventions", re.compile(
        r"agents\.md|readme|convention|style guide|guideline|documentation|docs|"
        r"how (the )?project (is )?(organiz|structur)", re.I)),
    ("shape", re.compile(
        r"read (the|this|it)|look at (the|this)|contents of|see (what|how|if|whether)|"
        r"understand|inspect (the|this|how)|examine|what (it|the) (does|contains|looks like)|"
        r"check (the|this|what|how)|implementation", re.I)),
]


def classify_question(reasoning: str, operation: str, pre_mutation: bool, target: str):
    """Returns (question, was_inferred)."""
    if not reasoning:
        return infer_question(operation, target), True
    # verify-edit only counts post-mutation
    for qname, rx in QUESTION_RULES:
        if qname == "verify-edit" and pre_mutation:
            # post-mutation reads with matching reasoning are verification;
            # pre-mutation reads that merely mention "check that" are not.
            continue
        if rx.search(reasoning):
            return qname, False
    return infer_question(operation, target), True


CONFIG_FILES = {
    "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
    "gradlew", "gradlew.bat", "package.json", "package-lock.json", "makefile", "Makefile",
    "CMakeLists.txt", "Jenkinsfile", "Dockerfile", "docker-compose.yml", "requirements.txt",
    "setup.py", "pyproject.toml", "Cargo.toml", "go.mod",
}
CONFIG_RX = re.compile(
    r"(^|/)(\.github/|\.gitlab-ci|\.ci/|\.buildkite/|\.circleci/|target/|build/|out/|"
    r"[\w.-]+\.(xml|gradle|properties|toml|yaml|yml|cfg|conf|ini|env)|"
    r"(pom|build|settings|gradlew))", re.I)
TEST_RX = re.compile(r"(^|/)(src/test|tests?/|__tests__|test_[\w.-]+\.[jt]sx?)|([A-Za-z0-9_]+Test\.(java|kt|py|go|ts|js)|[a-z_]+_test\.(py|go))", re.I)
DOCS_RX = re.compile(r"(^|/)(AGENTS\.md|README|CONTRIBUTING|LICENSE|CHANGELOG|docs/|\.md$)", re.I)
SOURCE_RX = re.compile(r"\.(java|kt|py|ts|tsx|js|jsx|go|rs|c|h|cpp|hpp|rb|scala|sh|sql)$", re.I)


def infer_question(operation: str, target: str) -> str:
    """Fallback classification when no reasoning is available."""
    t = target.strip()
    if operation in ("git-history", "inspect", "env", "tool-discovery"):
        return "orientation"
    if operation == "locate":
        return "locate"
    if operation == "search":
        return "locate"
    # read / read-file: classify by target
    if TEST_RX.search(t):
        return "harness-check"
    if CONFIG_RX.search(t) or any(t.endswith("/" + f) or t == f or t.endswith(f) for f in CONFIG_FILES):
        return "config-build"
    if DOCS_RX.search(t):
        return "conventions"
    if SOURCE_RX.search(t):
        return "shape"
    return "orientation"


def reasoning_snippet(reasoning: str) -> str:
    if not reasoning:
        return ""
    s = re.sub(r"\s+", " ", reasoning).strip()
    return s[:220]


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------

def parse_unified_child(child_dir: Path):
    """Parse a 'unified harness' child session (subagent) from chunks/*.json.

    Each chunk file is an array of {id, message: {role, content: [...]}, source}.
    Assistant content parts: {"type": "reasoning", "content": [{"text", "type":"text"}]},
    {"type": "text", "text"}, {"type": "tool_call", "name", "arguments_json"}.
    Tool results: {"role": "tool", "content": [{"text", "type":"text"}]}.
    """
    chunks = sorted(child_dir.glob("chunks/*.json"))
    if not chunks:
        return None
    entries = []
    seen = set()
    for cf in chunks:
        try:
            arr = json.loads(cf.read_text())
        except Exception:
            continue
        for e in arr:
            eid = e.get("id")
            if eid and eid in seen:
                continue
            if eid:
                seen.add(eid)
            entries.append(e)

    events = []
    pending = {}
    first_user = ""
    for e in entries:
        msg = e.get("message") or {}
        role = msg.get("role")
        parts = msg.get("content") or []
        if role == "assistant":
            reasoning = " ".join(
                (p.get("text") or " ".join(t.get("text", "") for t in (p.get("content") or [])))
                for p in parts if p.get("type") == "reasoning"
            )
            for p in parts:
                if p.get("type") == "tool_call":
                    name = p.get("name", "")
                    arguments = {}
                    raw = p.get("arguments_json")
                    if not raw:
                        a = p.get("arguments") or {}
                        raw = a.get("raw")
                        if isinstance(a.get("value"), dict):
                            arguments = a["value"]
                    if not arguments and raw:
                        try:
                            arguments = json.loads(raw)
                        except Exception:
                            arguments = {"raw": raw}
                    op, mut, target = classify_call(name, arguments)
                    ev = {
                        "tool": name,
                        "operation": op,
                        "is_mutation": mut,
                        "target": target,
                        "reasoning": reasoning,
                        "result_bytes": 0,
                        "ok": True,
                    }
                    events.append(ev)
        elif role == "tool":
            # tool messages follow their calls; attach to the next unassigned event
            ev = None
            for cand in events:
                if cand.get("_resolved"):
                    continue
                ev = cand
                break
            if ev is None:
                continue
            text = " ".join(t.get("text", "") for t in parts if isinstance(t, dict))
            ev["result_bytes"] = len(text)
            ev["_resolved"] = True
        elif role == "user" and not first_user:
            first_user = " ".join(
                (t.get("text", "") if isinstance(t, dict) else str(t)) for t in parts
            )
    for ev in events:
        ev.pop("_resolved", None)
    return {"meta": {"task": first_user[:200], "child": child_dir.name}, "events": events}


def parse_session(session_dir: Path):
    messages_file = session_dir / "messages.jsonl"
    meta_file = session_dir / "meta.json"
    if not messages_file.exists():
        return None
    meta = {}
    if meta_file.exists():
        try:
            meta = json.loads(meta_file.read_text())
        except Exception:
            pass

    events = []  # ordered tool-call events
    pending = {}  # tool_call_id -> event dict

    with open(messages_file) as fh:
        for line in fh:
            try:
                msg = json.loads(line)
            except Exception:
                continue
            role = msg.get("role")
            if role == "assistant":
                reasoning = msg.get("reasoning_content") or ""
                for call in msg.get("tool_calls") or []:
                    func = call.get("function") or {}
                    name = func.get("name", "")
                    try:
                        arguments = json.loads(func.get("arguments", "{}") or "{}")
                    except Exception:
                        arguments = {"raw": func.get("arguments", "")}
                    op, mut, target = classify_call(name, arguments)
                    ev = {
                        "tool": name,
                        "operation": op,
                        "is_mutation": mut,
                        "target": target,
                        "reasoning": reasoning,
                        "result_bytes": 0,
                        "ok": True,
                    }
                    pending[call.get("id")] = ev
                    events.append(ev)
            elif role == "tool":
                ev = pending.get(msg.get("tool_call_id"))
                if ev is None:
                    continue
                content = msg.get("content") or ""
                ev["result_bytes"] = len(content)
                tr = msg.get("tool_result") or {}
                if isinstance(tr, dict):
                    rc = tr.get("returncode", tr.get("exit_code"))
                    if rc not in (None, 0):
                        ev["ok"] = False

    return {"meta": meta, "events": events}


# ---------------------------------------------------------------------------
# Aggregation
# ---------------------------------------------------------------------------

EXT_RX = re.compile(r"\.([A-Za-z0-9]+)\b")


def target_extensions(events):
    c = Counter()
    for ev in events:
        t = ev["target"]
        for m in EXT_RX.finditer(t):
            ext = m.group(1).lower()
            if ext in ("java", "py", "ts", "js", "md", "json", "xml", "yaml", "yml", "toml",
                       "gradle", "properties", "sh", "txt", "sql", "rb", "go", "rs", "c", "h", "cpp"):
                c[ext] += 1
                break
    return c


def analyze_session(parsed):
    events = parsed["events"]
    meta = parsed["meta"]
    first_mut_idx = None
    for i, ev in enumerate(events):
        if ev["is_mutation"]:
            first_mut_idx = i
            break

    reads_pre = 0
    reads_post = 0
    bytes_pre = 0
    bytes_post = 0
    for i, ev in enumerate(events):
        if ev["operation"] in ("read", "read-file", "search", "locate", "git-history", "inspect"):
            if first_mut_idx is None or i < first_mut_idx:
                reads_pre += 1
                bytes_pre += ev["result_bytes"]
            else:
                reads_post += 1
                bytes_post += ev["result_bytes"]

    total_bytes = sum(ev["result_bytes"] for ev in events)
    total_calls = len(events)
    stats = meta.get("stats") or {}

    return {
        "session_id": meta.get("session_id", parsed and "?"),
        "workdir": (meta.get("environment") or {}).get("working_directory", "?"),
        "title": meta.get("title"),
        "n_tool_calls": total_calls,
        "n_read_calls": reads_pre + reads_post,
        "read_calls_pre_mutation": reads_pre,
        "read_bytes_pre_mutation": bytes_pre,
        "read_bytes_post_mutation": bytes_post,
        "total_result_bytes": total_bytes,
        "prompt_tokens": stats.get("session_prompt_tokens", 0),
        "completion_tokens": stats.get("session_completion_tokens", 0),
        "first_mutation_index": first_mut_idx,
        "events": events,
    }


def question_taxonomy(sessions):
    q_counter = Counter()
    q_bytes = Counter()
    q_pre = Counter()
    q_inferred = Counter()
    q_examples = defaultdict(list)
    read_ops = {"read", "read-file", "search", "locate", "git-history", "inspect"}
    for s in sessions:
        first_mut = s["first_mutation_index"]
        for i, ev in enumerate(s["events"]):
            if ev["operation"] not in read_ops:
                continue
            pre = (first_mut is None or i < first_mut)
            q, inferred = classify_question(ev["reasoning"], ev["operation"], pre, ev["target"])
            q_counter[q] += 1
            q_bytes[q] += ev["result_bytes"]
            if inferred:
                q_inferred[q] += 1
            if pre:
                q_pre[q] += 1
            if len(q_examples[q]) < 12:
                q_examples[q].append({
                    "session": s.get("workdir", s.get("task", "?")),
                    "operation": ev["operation"],
                    "target": ev["target"][:200],
                    "why": reasoning_snippet(ev["reasoning"]),
                    "pre_mutation": pre,
                    "result_bytes": ev["result_bytes"],
                })
    return q_counter, q_bytes, q_pre, q_inferred, q_examples


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log-dir", default=os.path.expanduser("~/.vibe/logs/session"))
    ap.add_argument("--unified-dir", default=os.path.expanduser("~/.vibe/logs/session/unified"))
    ap.add_argument("--out-dir", default=str(Path(__file__).parent))
    args = ap.parse_args()

    log_dir = Path(args.log_dir)
    unified_dir = Path(args.unified_dir)
    out_dir = Path(args.out_dir)

    sessions = []
    for sd in sorted(log_dir.glob("session_*")):
        if not sd.is_dir():
            continue
        try:
            parsed = parse_session(sd)
        except Exception as e:
            print(f"skip {sd.name}: {e}", file=sys.stderr)
            continue
        if parsed is None or not parsed["events"]:
            continue
        s = analyze_session(parsed)
        s["source"] = "historical"
        sessions.append(s)

    experiments = []
    for cd in sorted(unified_dir.glob("child-*")):
        if not cd.is_dir():
            continue
        try:
            parsed = parse_unified_child(cd)
        except Exception as e:
            print(f"skip {cd.name}: {e}", file=sys.stderr)
            continue
        if parsed is None or not parsed["events"]:
            continue
        s = analyze_session(parsed)
        s["source"] = "experiment"
        s["task"] = parsed["meta"].get("task", "")
        experiments.append(s)

    # ---- global question taxonomy ------------------------------------
    op_counter = Counter()
    op_bytes = Counter()
    for s in sessions + experiments:
        for ev in s["events"]:
            op_counter[ev["operation"]] += 1
            op_bytes[ev["operation"]] += ev["result_bytes"]

    q_counter, q_bytes, q_pre, q_inferred, q_examples = question_taxonomy(sessions)
    q_counter_x, q_bytes_x, q_pre_x, q_inferred_x, q_examples_x = question_taxonomy(experiments)

    # ---- pre-mutation concentration ----------------------------------
    read_ops = {"read", "read-file", "search", "locate", "git-history", "inspect"}
    tot_read_bytes = sum(q_bytes.values())
    pre_share = sum(q_pre.values()) / max(1, sum(q_counter.values()))
    pre_byte_share = (sum(
        ev["result_bytes"]
        for s in sessions
        for i, ev in enumerate(s["events"])
        if ev["operation"] in read_ops and (s["first_mutation_index"] is None or i < s["first_mutation_index"])
    ) / max(1, tot_read_bytes))

    ext_counter = Counter()
    for s in sessions:
        ext_counter.update(target_extensions(
            [ev for ev in s["events"] if ev["operation"] in read_ops]))

    # grep pattern mining
    grep_rx = re.compile(r"(?:grep|rg)\s+(?:-[A-Za-z]+\s+)*[\"']?(\w[\w.\-*/\[\]]{2,60})[\"']?")
    grep_patterns = Counter()
    for s in sessions:
        for ev in s["events"]:
            if ev["operation"] == "search":
                for m in grep_rx.finditer(ev["target"]):
                    grep_patterns[m.group(1)] += 1

    report = {
        "generated_at": datetime.now().isoformat(),
        "n_sessions": len(sessions),
        "sessions": [{
            k: v for k, v in s.items() if k != "events"
        } for s in sessions],
        "question_taxonomy": {
            q: {
                "calls": q_counter[q],
                "result_bytes": q_bytes[q],
                "calls_pre_mutation": q_pre[q],
                "calls_inferred": q_inferred[q],
                "share_of_read_calls": round(q_counter[q] / max(1, sum(q_counter.values())), 4),
                "examples": q_examples[q],
            } for q in q_counter
        },
        "operation_taxonomy": {
            op: {"calls": op_counter[op], "result_bytes": op_bytes[op]}
            for op in op_counter
        },
        "pre_mutation": {
            "read_calls_pre_share": round(pre_share, 4),
            "read_result_bytes_pre_share": round(pre_byte_share, 4),
            "sessions_with_mutation": sum(1 for s in sessions if s["first_mutation_index"] is not None),
            "sessions_read_only": sum(1 for s in sessions if s["first_mutation_index"] is None),
            "avg_read_calls_before_first_mutation": round(
                sum(s["read_calls_pre_mutation"] for s in sessions) / max(1, len(sessions)), 2),
        },
        "target_extensions": dict(ext_counter.most_common(15)),
        "top_grep_patterns": dict(grep_patterns.most_common(25)),
        "experiments": {
            "n_sessions": len(experiments),
            "question_taxonomy": {
                q: {
                    "calls": q_counter_x[q],
                    "result_bytes": q_bytes_x[q],
                    "calls_pre_mutation": q_pre_x[q],
                    "calls_inferred": q_inferred_x[q],
                    "share_of_read_calls": round(q_counter_x[q] / max(1, sum(q_counter_x.values())), 4),
                    "examples": q_examples_x[q],
                } for q in q_counter_x
            },
            "sessions": [
                {
                    "task": s.get("task", "")[:160],
                    "n_tool_calls": s["n_tool_calls"],
                    "n_read_calls": s["n_read_calls"],
                    "total_result_bytes": s["total_result_bytes"],
                    "first_mutation_index": s["first_mutation_index"],
                } for s in experiments
            ],
        },
    }

    json_path = out_dir / "question_taxonomy_report.json"
    json_path.write_text(json.dumps(report, indent=2))

    # ---- markdown summary --------------------------------------------
    lines = []
    lines.append("# Question Taxonomy Report: what the harness asks the code\n")
    lines.append(f"Generated: {report['generated_at']}  ")
    lines.append(f"Sessions analyzed: {report['n_sessions']}\n")
    lines.append("## Pre-mutation concentration\n")
    pm = report["pre_mutation"]
    lines.append(f"- read calls before first mutation: **{pm['read_calls_pre_share']*100:.1f}%** of all read calls")
    lines.append(f"- read result bytes before first mutation: **{pm['read_result_bytes_pre_share']*100:.1f}%** of all read bytes")
    lines.append(f"- avg read calls per session before first mutation: {pm['avg_read_calls_before_first_mutation']}")
    lines.append(f"- sessions with mutation: {pm['sessions_with_mutation']}; read-only sessions: {pm['sessions_read_only']}\n")
    lines.append("## Question taxonomy (share of read calls)\n")
    lines.append("| question | calls | share | pre-mutation calls | result MB |")
    lines.append("|---|---|---|---|---|")
    for q, d in sorted(report["question_taxonomy"].items(),
                       key=lambda kv: -kv[1]["calls"]):
        lines.append(f"| {q} | {d['calls']} | {d['share_of_read_calls']*100:.1f}% | "
                     f"{d['calls_pre_mutation']} | {d['result_bytes']/1e6:.2f} |")
    lines.append("\n## Operation taxonomy\n")
    lines.append("| operation | calls | result MB |")
    lines.append("|---|---|---|")
    for op, d in sorted(report["operation_taxonomy"].items(),
                         key=lambda kv: -kv[1]["calls"]):
        lines.append(f"| {op} | {d['calls']} | {d['result_bytes']/1e6:.2f} |")
    lines.append("\n## Target file extensions (read/locate/search)\n")
    for ext, n in report["target_extensions"].items():
        lines.append(f"- .{ext}: {n}")
    lines.append("\n## Top grep patterns\n")
    for p, n in report["top_grep_patterns"].items():
        lines.append(f"- `{p}`: {n}")

    # experiment section
    if report["experiments"]["n_sessions"]:
        lines.append("\n## Controlled experiments (Elasticsearch, big repo)\n")
        lines.append("| task | tool calls | read calls | result KB |")
        lines.append("|---|---|---|---|")
        for s in report["experiments"]["sessions"]:
            lines.append(f"| {s['task'][:60]} | {s['n_tool_calls']} | "
                         f"{s['n_read_calls']} | {s['total_result_bytes']/1e3:.1f} |")
        lines.append("\n| question | calls | share | result KB |")
        lines.append("|---|---|---|---|")
        for q, d in sorted(report["experiments"]["question_taxonomy"].items(),
                           key=lambda kv: -kv[1]["calls"]):
            lines.append(f"| {q} | {d['calls']} | {d['share_of_read_calls']*100:.1f}% | "
                         f"{d['result_bytes']/1e3:.1f} |")

    md_path = out_dir / "question_taxonomy_report.md"
    md_path.write_text("\n".join(lines))

    print(f"wrote {json_path}")
    print(f"wrote {md_path}")
    print(f"sessions: {report['n_sessions']}")
    print("question taxonomy (calls):")
    for q, d in sorted(report["question_taxonomy"].items(), key=lambda kv: -kv[1]["calls"]):
        print(f"  {q:16s} {d['calls']:5d}  ({d['share_of_read_calls']*100:.1f}%)  pre-mut: {d['calls_pre_mutation']}")


if __name__ == "__main__":
    main()
