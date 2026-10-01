# BAKEOFF round 5+ - SWE-bench-java lookup bakeoff protocol

Repeatable protocol for the SWE-bench-java bakeoff (Todoist 6hg7Mm8GP2C7jpVW):
gv-only vs grep-only arms answering benchmark-derived structural questions
against repos pinned at exact base commits. Measures cost per correct answer
on the PRE-PATCH lookup layer - never end-to-end resolution (LLM-dominated).
Round-5 pilot results: BAKEOFF_SWE_PILOT_RESULTS.md.

## Prerequisites

1. Question sets: one JSON per instance in etl/swe-java-questions/,
   schema {task_id, repo, base_commit, questions: [{q, ground_truth_strict,
   ground_truth_lenient, route_hint}]}. Source: HF dataset
   `Daoguang/Multi-SWE-bench`, file `swe-bench-java-verified.json`
   (91 Java instances; paper arXiv 2408.14354). Select instances whose issue
   text names NO file/line (lookup-trivial issues produce ~0 searches - see
   trajectory analysis on Todoist 6hg86RQH3MCrh4mW). Ground truth must be
   grep-verified against the base commit AND cross-checked for same-class
   calls (see pitfall below).
2. Stage repos at exact base commits (keep them between rounds):

   ```bash
   mkdir -p ../bakeoff-repos
   git clone https://github.com/google/gson ../bakeoff-repos/gson
   git -C ../bakeoff-repos/gson checkout <base_commit>
   ```

3. Daemon: ONE project per port (RUNBOOK.md). Sequential repos require a
   daemon restart between them; spawn fresh arms after each restart.

   ```bash
   pkill -f dk.gausdalfind.server.GausVibeServer; sleep 2
   JAR=$(jq -r '.mcpServers.gausvibe.args[1]' ~/.vibe/plugins/gausvibe/mcp.json)
   nohup java -cp "$JAR" dk.gausdalfind.server.GausVibeServer \
     --project <abs path to repo> --port 8094 > /tmp/gv-<repo>-daemon.log 2>&1 &
   curl -s -m 2 http://localhost:8094/stats   # verify project path, stale:false
   ```

   Log location defaults to <project>/.gausvibe/ (ask-log lands there too).

## Arms

2 arms per configuration for variance. Naming: rN-<config>-<repo>-<k>,
e.g. r5-gv-gson-1, r5-grep-jackson-2. Spawn SEQUENTIALLY via
tools.agent.spawn, verify each with tools.agent.list. Same model in all
arms (tooling is the only variable). Arms receive ONLY the question
strings + their constraint - never ground truth, file lists, or base-commit
notes. Identical task text for every arm of a repo:

```
Answer concisely, with file paths where applicable. For each answer, state
which tool you used. Read-only: do not edit any file.

The questions are about the Java repository at <abs repo path> (<owner/repo>).

Q1..Qn: <verbatim question strings from the JSON - do not paraphrase>
```

- gv arm constraint: `ARM CONSTRAINT - you are the gv arm of a bakeoff.
  Use ONLY the gausvibe MCP tools via run_typescript (tools.mcp_gausvibe.<name>:
  ask, search, classes, packages, class_detail, class_members, tests, callpath).
  No grep, no rg, no find, no shell commands, no file reads, no localhost
  requests. If ask returns matched=null, note that explicitly and fall back
  to a shell grep for that one question, clearly marked as a fallback.`
- grep arm constraint: `ARM CONSTRAINT - you are the grep arm of a bakeoff.
  Use ONLY shell commands (grep, rg, find, ls) and file reads against the
  repository at <abs repo path>. No gausvibe tools, no MCP tools, no
  localhost requests.`
- NO curl arm (killed in round 4: 4.6x input at equal quality).

## Harvest - BEFORE closing any agent

Completion notifications carry the child session id; map by NAME (the
notification id embeds the agent name), not arrival order:

```
python3 etl/etl_bakeoff_attribution.py \
  --child r5-gv-gson-1=$HOME/.vibe/logs/session/unified/child-<ID1> \
  ... --out etl/results-rN/attribution-<repo>.json
```

Cross-check the name mapping against each child's tool mix (gv arms:
run_typescript; grep arms: bash). Then close all arms.

## Scoring

- strict: every required ground-truth item present and correct; +/-1 line
  tolerance (graph tools report method-start lines, grep reports call
  lines - r5 had 2 strict line fails from this). Extras are recorded but
  not penalized unless factually wrong.
- lenient: must-mention set only. Tighten per-question bars in the JSON -
  round-5 lenient was 20/20 everywhere (non-discriminating).
- Mark P* = answered via sanctioned grep fallback (gv arms) - correctness
  counts, but report the fallback rate per route.
- Report per-route matched=null rates for gv arms; verify zero fuzzy
  mis-matches (r4 Q9 regression watch).

## Pitfalls learned in round 5

1. Ground truth from `grep "Types.resolve("` MISSES same-class calls
   (written bare as `resolve(`) - both grep arms AND the original GT missed
   $Gson$Types.getSupertype as a caller; the graph found it. Always
   cross-check caller ground truth with the graph or a bare-name grep.
2. Graph answers cite method-start lines (getBoundFields:144) not call
   sites (160). Either ask questions at method+file granularity, or score
   with line tolerance, or fix call-edge line reporting.
3. tests answer payloads dominate gv cost on real repos (34 classes,
   121k result bytes worst case). Expect the gson-class instances to be
   the expensive ones; jackson-class (small classes) is near-parity.
4. gv discovery cost (search_tool_functions) recurs in some arms (17k
   bytes in r5) - keep watching; the slim-skill fix from r4 finding #3.
5. The literal route does not exist yet, but the fields relation can serve
   literal-ish questions (V_SEP round 5) at 2-3 calls vs grep's 1.

## Deliverables per round

1. etl/results-rN/attribution-<repo>.json per repo (harvested pre-close).
2. Results MD (BAKEOFF_*_RESULTS.md) with: arm cost table, strict/lenient
   score matrix, per-route null/fallback rates, findings, question-set fixes.
3. Updated question sets in etl/swe-java-questions/ (fixes the run reveals).
4. Todoist: complete the round's tasks; comment results on the bakeoff task.
