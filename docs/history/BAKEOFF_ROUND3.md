# Mini Bake-off Round 3: GausVibe deterministic tools vs grep

Instruction file for the next Vibe session. Rounds 1 and 2 ran the same
procedure but the GausVibe side only ever had the 7 original tools
(`ask, tests, callpath, edited, feedback, changes, stats`), because a Vibe
session pins its plugin snapshot at session start and `--resume` restores the
stale one even after Vibe is restarted. A fresh session resolves plugins from
current disk, so this round is the first that can actually exercise the new
deterministic tools (`classes, class_members, class_detail, search, packages`)
on the two questions that previously failed.

## Precondition checks — do these first, in order

1. Daemon: `tools.mcp_gausvibe.stats` (via run_typescript) must report
   ~4.1k nodes for this repo, `stale: false`.
2. Tool registry: connector discovery (`search_tool_functions`, mode
   `all_connector_capabilities`, connectors `["mcp_gausvibe"]`) must list
   **12** functions. If it lists only 7, STOP and report: the session is
   running a stale plugin snapshot; the user must run `/reload-plugins` or
   start a fresh session. Do not run the bake-off with 7 tools — it
   reproduces round 2 at ~400k tokens of cost.
3. If desired, smoke-test determinism directly:
   `tools.mcp_gausvibe.classes({ package: 'dk.gausdalfind.queries' })` should
   return 7 classes including `TextSearchIndexTest`.

## Procedure

1. Spawn 4 subagents SEQUENTIALLY via `tools.agent.spawn`; after each spawn,
   check `tools.agent.list` confirms registration. Agent names `grep-1`,
   `grep-2`, `gv-1`, `gv-2` should be free in a fresh session; if a name is
   rejected as reserved, fall back to `grep-3`/`grep-4`/`gv-3`/`gv-4`.
   - `grep-1`, `grep-2`: may use ONLY grep/rg/find/read_file. No gausvibe
     tools, no requests to localhost:8094.
   - `gv-1`, `gv-2`: may use ONLY the gausvibe MCP tools, called via
     run_typescript as `tools.mcp_gausvibe.<name>`. No grep, no file reads.
     If a tool call errors or `ask` returns `matched=null`, note that per
     question and only then fall back to grep, explicitly marked.
2. Every subagent gets this identical READ-ONLY task (no file edits):

   ```
   Answer concisely, with file paths where applicable. For each answer,
   state which tool you used.
   Q1. Which file defines the class GausVibeServer?
   Q2. What methods does the class GausVibeMcpServer have?
   Q3. List the classes in package dk.gausdalfind.queries.
   Q4. Where is the class TextSearchIndex defined?
   Q5. Which test classes verify GraphQueryEngine?
   Q6. What classes implement the interface JavaGraphQuery?
   ```

   plus the arm's tool constraints (above) and the repo path
   `/Users/magnusfind/Documents/find-shadow-model/gausvibe`.
3. `tools.agent.wait` for all four. Record each child session id from the
   completion notifications. Notification id format is
   `subagent:child-<ID>:<NAME>:1:completed` — map the directory to the arm by
   the NAME in the notification, not by arrival order. (Round 2 initially
   mapped them wrong; the harvester then attributed gv sessions to grep
   labels. Always cross-check against each agent's tool-call log if unsure.)
4. BEFORE closing any subagent (closing deletes its logs), harvest:
   ```
   cd /Users/magnusfind/Documents/find-shadow-model/gausvibe && python3 \
     etl/etl_mini_bakeoff.py \
     --child grep-1=$HOME/.vibe/logs/session/unified/child-<ID1> \
     --child grep-2=... --child gv-1=... --child gv-2=...
   ```
5. Close all four subagents.

## Report

- Per-arm averages: input tokens, uncached input (input − cached_input),
  output tokens, tool calls, result bytes, llm calls.
- Per question: did the gv arms answer deterministically (matched tool,
  no fallback), or hit `matched=null` / tool errors / fallbacks? The whole
  point of this round: Q3 should go through `classes`/`packages` and Q6
  through `class_members`/`class_detail`-style deterministic routes instead
  of free-form `ask`.
- Answer agreement across arms (ground truth for Q5: the files in src/test
  referencing GraphQueryEngine are SelfEditValidationTest, TestGraphBuilder,
  UpdateFileTest, SelfEditRoundTripTest, AskResolutionTest, TestCoverageTest;
  CallPathTest does NOT reference it. In round 2 the graph's `tests` results
  included CallPathTest and missed AskResolutionTest — check whether that
  is still the case).
- Comparison against baselines:
  - Pre-deterministic baseline: gv arm 38.6k input, 3 tool calls, 8.9KB
    result bytes; grep arm 72.3k input, 7 calls, 26.6KB.
  - Round 2 (2026-09-29, 7-tool surface): grep arm avg 84.5k input,
    9.5k uncached, 1756 output, 10 tool calls, 32.2KB result bytes;
    gv arm avg 90.0k input, 4.5k uncached, 1954 output, 7 calls, 13.7KB.
    GV input was inflated by tool-discovery calls (`search_tool_functions`)
    and batching all six questions per `ask`; cached_input was 85–95% of
    input in every arm, so uncached input is the meaningful cost metric.

## Round 2 reference data (for the comparison)

Per-agent harvest, round 2:

| arm | llm_calls | input | cached_input | output | tool_calls | result_bytes |
|---|---|---|---|---|---|---|
| grep-1 | 8 | 109119 | 98304 | 1885 | 13 | 33426 |
| grep-2 | 5 | 59847 | 51712 | 1827 | 7 | 31049 |
| gv-1 | 8 | 86498 | 82560 | 1950 | 7 | 11418 |
| gv-2 | 8 | 93402 | 88320 | 1957 | 7 | 15937 |

Round 2 per-question behavior of the gv arms: Q1/Q2/Q4/Q6 deterministic via
`ask` routing; Q3 failed both arms (`ask` misrouted to the unrelated
`unindexPackage` method, wrong-symbol matches rather than `matched=null`)
and required an explicitly marked grep fallback; Q5 partially wrong in both
gv arms (false positive CallPathTest, false negative AskResolutionTest).
