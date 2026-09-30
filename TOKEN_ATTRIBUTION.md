# Token attribution: why GV-arm harness requests cost more than grep (2026-09-30)

Question: round 3 measured gv arms at 51.6k avg input vs grep 46.5k, despite
gv making fewer tool calls. Method: re-ran one arm per side (gv-3, grep-3)
with the identical six-question task, harvested per-request usage series and
message content from the child journals/chunks before closing the agents.

Round-3 child logs were closed-deleted before this analysis, so gv-3/grep-3
are the evidence base (they reproduce the round-3 pattern: gv-3 4 calls
44.8k input; grep-3 6 calls 59.0k).

## Per-request usage series

gv-3 (4 calls, input 44777):

| call | input | cached | uncached | delta | new content since prev |
|---|---|---|---|---|---|
| 1 | 8480 | 8384 | 96 | +8480 | base (system+registry+task) |
| 2 | 9933 | 8448 | 1485 | +1453 | skill result 5236B |
| 3 | 11922 | 9984 | 1938 | +1989 | search_tool_functions result 7341B |
| 4 | 14442 | 12224 | 2218 | +2520 | run_typescript result 7452B |

grep-3 (6 calls, input 58961): base 8420; deltas +573, +523, +589, +575,
+567 - one small bash result (444-1538B) per turn.

## Findings

1. The per-request fixed cost is IDENTICAL between arms: 8480 vs 8420
   tokens for call 1. The capabilities checkpoint is byte-identical (15941
   bytes, 19 connectors). The 12 gausvibe MCP tools add NOTHING to the
   request: they are reachable only through run_typescript, so their
   schemas never enter the tools array. Tool registry is NOT the cause.

2. The GV-arm excess is context content that grep arms never load, and
   which is re-sent (cached) on every subsequent request:
   - skill load: 5236B (~1.3k tok), lands in turn 1-2, re-sent in all
     remaining calls: ~3.9k of total input in a 4-call arm.
   - connector discovery: search_tool_functions returns 7341B (~1.8k tok)
     to learn tool names that the task prompt had already listed. Re-sent
     in all remaining calls: ~3.6k.
   - Together: ~12.6KB preamble, ~7.5k of a 44.8k total (17%), purely to
     establish how to call the tools.

3. Per-turn result bulk: one run_typescript result carrying all six
   answers is 7452B; a grep turn adds only 444-1538B. GV trades many small
   turns for few large ones.

4. Base re-send dominates totals: every call re-sends the 8.4k prefix
   (cached). grep-3's 6 calls re-sent it 6x (50.5k of its 59.0k total).
   Fewer calls is why gv-3 came out CHEAPER overall in this run. In round
   3 proper, call counts were equal (4.5 vs 4.5 avg), so the 12.6KB
   preamble + bulkier results made gv ~5k more expensive - all of it in
   the cached portion (round 3 uncached: gv 6.1k vs grep 7.2k, gv lower).

5. The TS-code-as-prompt tax: each gv tool call ships a full TypeScript
   program in call args (112-1294B per call) vs one-line grep commands;
   these become input on the next call.

## Recommendations

- Pre-inline the gausvibe tool list (names + one-line arg shapes) into the
  skill or task prompt so arms skip search_tool_functions discovery:
  saves ~1.8k tok x resends.
- Slim gausvibe-query SKILL.md (5236B) to a compact cheat-sheet; or have
  the daemon expose a `tools` MCP tool returning compact usage text.
- Keep tool results compact: server-side answer formatting (JSON wrappers,
  boilerplate in run_typescript results) is 1-2KB of the 7.4KB result.
- Batch multiple questions per run_typescript call (gv-1's pattern): fewer
  base re-sends and fewer wrapper results; gv-3's 4 calls beat grep-3's 6
  calls by 14k input for the same answers.

## Provenance

gv-3 = child-5cb3a860b72664468aa29dfc, grep-3 =
child-b36f4a32cff33146dd733c31; harvested 2026-09-30, closed after
harvest. Both answered all six questions correctly; gv-3 used
ask/classes only, no fallbacks.
