# Bake-off Round 4: Cost Attribution at Scale

Instruction file for a FRESH Vibe session (started after the slim skill was
deployed 2026-09-30). Purpose: measure where reading/context tokens
actually go across arms, to rank the next big savings. Diagnostic round -
do NOT pre-tune the server (known `tests` verbosity is part of what we're
measuring; the CallPathTest link-filter defect is tracked separately).

## Precondition checks - do these first, in order

1. Daemon up: `curl -s -m 3 http://localhost:8094/stats` reports
   project = .../gausvibe, 4678 nodes, stale:false. If down, start per
   RUNBOOK "Running the daemon" (classpath from mcp.json now points at
   javaparser 3.28.2 - do not sed it).
2. 12 MCP tools: `search_tool_functions` mode=all_connector_capabilities
   connectors=["mcp_gausvibe"] must list 12 functions.
3. Session's pinned plugin is the SLIM skill: the newest
   ~/.vibe/logs/session/plugins/packages/*/skills/gausvibe-query/SKILL.md
   must be 2214 bytes (contains `callpath({from, to, depth})`). If it is
   the old 5308-byte curl-era file, STOP: start a fresh session.

## Arms - spawn SEQUENTIALLY via tools.agent.spawn, verify with
tools.agent.list after each

8 arms, names r4-grep-1 r4-grep-2 r4-gv-1 r4-gv-2 r4-gvb-1 r4-gvb-2
r4-curl-1 r4-curl-2. All get the SAME 12-question task below plus their
arm constraints and the repo path
/Users/magnusfind/Documents/find-shadow-model/gausvibe.

- r4-grep-1/2: ONLY grep/rg/find/ls shell commands and file reads. No
  gausvibe tools, no localhost requests.
- r4-gv-1/2: ONLY gausvibe MCP tools via run_typescript
  (tools.mcp_gausvibe.<name>). No grep, no file reads, no curl. If ask
  returns matched=null, note it and fall back to grep, marked.
- r4-gvb-1/2: same as gv, PLUS this instruction: "Batch as many
  questions as possible into each run_typescript call - one program may
  await several tools.mcp_gausvibe calls and return all results."
- r4-curl-1/2: ONLY bash+curl against http://localhost:8094. No grep,
  no file reads, no gausvibe MCP tools. The daemon's GET / lists
  endpoints; GET /ask?q=<url-encoded question> answers in plain
  language. Use --data-urlencode or encode manually.

## Task (identical for all arms, read-only, no file edits)

```
Answer concisely, with file paths where applicable. For each answer,
state which tool/endpoint you used.
Q1. Which file defines the class GausVibeServer?
Q2. What methods does the class GausVibeMcpServer have?
Q3. List the classes in package dk.gausdalfind.queries.
Q4. Where is the class TextSearchIndex defined?
Q5. Which test classes verify GraphQueryEngine?
Q6. What classes implement the interface JavaGraphQuery?
Q7. Who calls the method handleLine?
Q8. Who uses the field excludePattern?
Q9. Where is the number 8094 defined?
Q10. How does execution flow from main to handleLine in GausVibeMcpServer?
Q11. What packages are in the project?
Q12. Search for classes named Cache.
```

(12 questions chosen to span payload sizes: locations/small, members,
package listing, tests/big, implementations, callers, field usage,
literal value, callpath, packages, search.)

## Harvest - BEFORE closing any agent

```
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe && python3 \
  etl/etl_bakeoff_attribution.py \
  --child r4-grep-1=$HOME/.vibe/logs/session/unified/child-<ID1> \
  ... (all 8, ids from the completion notifications - map by NAME in the
  notification id, not arrival order) --out etl/results-r4/attribution.json
```

Cross-check mapping against each child's chunk tool names (gv arms:
run_typescript; grep arms: bash; curl arms: bash with curl in args).
Then close all 8 agents.

## Analysis the executing session should produce

Write BAKEOFF_ROUND4_RESULTS.md with:
1. Per-arm averages: calls, input, uncached, output, result bytes
   (compare round 3: grep 46.5k/10.5 calls, gv 51.6k/3.5 calls).
2. Did gv arms call search_tool_functions (discovery)? Expect zero with
   the slim skill; if not, that is finding #1.
3. Batching effect: r4-gvb vs r4-gv call counts and totals (hypothesis:
   fewer calls -> fewer 8.4k base re-sends -> big input drop).
4. Transport effect: r4-curl vs r4-gv (arg bytes, retries from quoting
   failures, total input; hypothesis: near-parity).
5. Per-question payload sizes (from the attribution JSON: tool-result
   bytes in call order): rank question types by result bytes; expected
   leader is Q5 tests (~4KB with absolute paths) - sizes the
   relative-path + tests-verbosity trims.
6. Per-call input growth series per arm: base (call 1), and what each
   turn added (skill bytes, discovery bytes, args, results).
7. Ranked list: biggest remaining savings with measured sizes.

## Cost note

8 arms x 12 questions: expect 60-90k input per arm, 85-95% cached
(~10k uncached total per arm). Round-2 cost ~400k input for comparison.
