# Bake-off Round 4 Results: Cost Attribution at Scale

Executed 2026-10-01 per BAKEOFF_ROUND4.md. 8 arms (2 per configuration), all
given the identical 12 questions, all subagents saw the slim 2214-byte skill.

Preconditions verified before spawning: daemon up (4678 nodes / 6404 edges,
stale:false), `search_tool_functions` listed all 12 mcp_gausvibe functions,
newest gausvibe-query SKILL.md = 2214 bytes (contains `callpath({from, to,
depth})`).

## Per-arm harvest

input/cached/uncached are sums over LLM calls (each call re-sends the full
context; base per call = `base` column). Source: etl/results-r4/attribution.json.

| arm | llm_calls | input | cached | uncached | output | result_bytes | base |
|---|---|---|---|---|---|---|---|
| r4-grep-1 | 7 | 114,033 | 106,624 | 7,409 | 3,200 | 42,454 | 11,892 |
| r4-grep-2 | 8 | 125,470 | 115,328 | 10,142 | 3,777 | 36,402 | 10,432 |
| r4-gv-1 | 11 | 159,912 | 151,936 | 7,976 | 4,232 | 26,286 | 9,901 |
| r4-gv-2 | 7 | 109,240 | 103,488 | 5,752 | 5,539 | 20,859 | 10,157 |
| r4-gvb-1 | 10 | 149,359 | 140,224 | 9,135 | 3,708 | 28,841 | 9,300 |
| r4-gvb-2 | 6 | 78,057 | 71,872 | 6,185 | 2,587 | 21,225 | 9,300 |
| r4-curl-1 | 32 | 725,746 | 704,384 | 21,362 | 10,924 | 64,279 | 9,252 |
| r4-curl-2 | 21 | 501,301 | 450,432 | 50,869 | 13,065 | 60,664 | 9,252 |

Per-configuration averages:

| arm | llm_calls | input | uncached | output | result_bytes | input/llm_call |
|---|---|---|---|---|---|---|
| grep avg | 7.5 | 119.8k | 8.8k | 3.5k | 39.4k | 16.0k |
| gv avg | 9 | 134.6k | 6.9k | 4.9k | 23.6k | 15.0k |
| gvb avg | 8 | 113.7k | 7.7k | 3.1k | 25.0k | 14.2k |
| curl avg | 26.5 | 613.5k | 36.1k | 12.0k | 62.5k | 23.2k |

Totals: 1.96M input, 110.8k uncached (5.6%), cached 89.8-97.1% per arm —
within the predicted 85-95% band except curl-2 (89.8%). Total input ran 3x the
predicted 500-700k; the miss is entirely the curl arms (1.23M of 1.96M).

vs round 3 (docs/history/BAKEOFF_ROUND3_RESULTS.md, same sum-over-calls metric):

| metric | r3 grep | r4 grep | r3 gv | r4 gv | r4 gvb |
|---|---|---|---|---|---|
| input | 46.5k | 119.8k | 51.6k | 134.6k | 113.7k |
| llm_calls | 4.5 | 7.5 | 4.5 | 9 | 8 |
| tool_calls | 10.5 | 11.5 | 3.5 | 6.5 | 3 |
| uncached | 7.2k | 8.8k | 6.1k | 6.9k | 7.7k |
| result_bytes | 18.8k | 39.4k | 21.6k | 23.6k | 25.0k |

Uncached cost is stable across rounds (~7-9k/arm for non-curl arms). The input
doubling is a turn-count story: round 4's question set forced more LLM turns
per arm (matched=null fallbacks on Q8/Q9, retries, and — for gv — one
run_typescript program per question instead of round 3's batched asks). Every
turn re-sends the ~9.3-11.9k base.

## 1. Discovery check: NOT eliminated (finding #1)

The slim skill did not zero out `search_tool_functions`:

| arm | skill loads | search_tool_functions calls | discovery result bytes |
|---|---|---|---|
| r4-gv-1 | 2 (4,352B) | 0 | 0 |
| r4-gv-2 | 2 (4,352B) | 2 (all_connector_capabilities x2) | 948 |
| r4-gvb-1 | 0 | 3 (best_match + details x2) | 9,944 |
| r4-gvb-2 | 1 (2,176B) | 0 | 0 |

- gv-2 loaded the slim skill twice AND still ran discovery twice (cheap:
  474B each).
- gvb-1 never issued a `skill` call at all and substituted full discovery:
  best_match (1,085B) + a details dump of 8 tool declarations (8,137B) +
  one more details call (722B) = 9,944B — more than 4x the slim skill it was
  meant to replace. The discovery-skip hypothesis holds only when the skill
  actually loads; skill-load reliability is the real variable.

## 2. Batching effect (gvb vs gv)

- run_typescript programs: gv 13 (7+6) vs gvb 6 (2+4) — batching halved the
  tool-program count as designed.
- LLM calls barely moved: gv 18 vs gvb 16. Batching collapses tool calls, not
  turns: the agent still runs one turn per batch, and each turn re-sends the
  ~9-10k base. The hypothesized "each avoided call saves an 8.4k prefix
  re-send" only pays per avoided LLM turn, not per avoided tool call.
- Input: gv avg 134.6k vs gvb avg 113.7k (-15.6%). Best case is decisive:
  gvb-2 (78.1k, 6 calls) is the cheapest arm of the round; gv-1 (159.9k,
  11 calls, no batching) is the most expensive non-curl arm.
- Verdict: batching is worth roughly 20-50k input per 12-question session,
  i.e. one skipped turn ≈ one skipped ~10-16k base re-send. Put the batch
  instruction in the skill, not in ad-hoc arm prompts.

## 3. Transport effect (curl vs gv): NOT near-parity (hypothesis false)

curl avg 613.5k input vs gv 134.6k — 4.6x. Drivers, both measured:

- Turn explosion: 26.5 LLM calls vs 9. Each bash+curl request is its own
  turn re-sending the ~9.3k base (curl-1: 32 turns x base = ~296k of its
  725.7k input is base re-sends alone).
- Command tax: bash arg bytes 19.1k/19.5k per curl arm vs 5.8-10.2k of
  run_typescript args for gv arms. Both curl arms hit bare-name 404s
  (needed FQNs) and quoting retries; curl-1 used 34 bash calls, curl-2 32.
- Result bytes were also higher (62.5k vs 23.6k avg) — the daemon's raw JSON
  endpoints (e.g. /tests at 8,008B) are more verbose than ask's prose.

REST as a transport is fine; the one-turn-per-request interaction pattern is
what costs. A curl arm only breaks even if it batches many endpoints per bash
call, which the arms did not do spontaneously.

## 4. Per-question payload ranking

From etl/results-r4/per_question_bytes.json (tool-result bytes attributed to
questions by keyword-matching each call's args; batched gv programs split
equally among the questions they mention — approximate for gv arms).

| rank | question | avg result bytes/arm | what happened |
|---|---|---|---|
| 1 | Q9 literal "8094" | 8,346 | unsupported by ask; arms burned retries/probes/fallbacks (curl-2: 21.5k, curl-1: 15.9k) |
| 2 | Q8 field usage | 5,372 | matched=null in all 4 gv arms -> grep fallback; curl arms probed methods/callers to infer |
| 3 | Q2 methods list | 4,390 | ask answer is 1,986B; curl arms re-fetched (11.5k single result in curl-2) |
| 4 | Q12 search "Cache" | 3,036 | exact-match search returns 44B/none; arms enumerated packages to compensate |
| 5 | Q4/Q3/Q5 | 1.8-1.9k | normal cost |
| 6 | Q10/Q1/Q6 | 1.2-1.4k | normal cost |
| 7 | Q11/Q7 | 0.4k | cheap endpoints (/packages 295B, /callpath 239B) |

The predicted leader (Q5 tests) is NOT the leader in practice. Direct
endpoint measurements (the numbers that size the planned trims):

| endpoint | bytes |
|---|---|
| /tests/...GraphQueryEngine (raw JSON) | 8,008 |
| /ask "which test classes verify GraphQueryEngine" | ~4,200 |
| /ask "who calls handleLine" (13 callers) | 2,637 |
| /ask "what methods does GausVibeMcpServer have" | 1,986 |
| /classes/{fqn}/methods | 897 |
| /ask class-location (Q1, Q4) | 492 |
| /ask matched=null (Q8) | 574 |
| /ask fuzzy mis-match (Q9) | 526 |
| /classes?package= | 348 |
| /packages | 295 |
| /callpath | 239 |
| /classes/{fqn}/implementations | 169 |
| /search?q=Cache (no exact match) | 44 |

The tests answer is confirmed as the fattest single answer (~4.2KB via ask,
8KB raw), and the repo absolute-path prefix alone is 756B of it (14 x 53
chars) — 18% of the ask answer. Dropping the per-test-method coverage map is
what separates the 8KB raw from the 4.2KB ask answer.

But the round's real payload hogs are Q8/Q9: unsupported question types don't
just return small matched=null payloads (574B), they trigger retry loops,
probe storms, and grep fallbacks that cost 5-8KB of results plus extra turns.

## 5. Per-call input growth series (added tokens per LLM call)

base = first call; each subsequent value is what that turn added to context.

| arm | series |
|---|---|
| r4-grep-1 | 11,892 / 1,236 / 2,698 / 1,256 / 863 / 723 / 824 |
| r4-grep-2 | 10,432 / 565 / 2,236 / 1,653 / 1,974 / 1,460 / 1,429 / 1,244 |
| r4-gv-1 | 9,901 / 2,217 / 1,281 / 214 / 440 / 689 / 389 / 467 / 310 / 971 / 1,691 |
| r4-gv-2 | 10,157 / 4,335 / 1,020 / 282 / 1,276 / 677 / 721 |
| r4-gvb-1 | 9,300 / 426 / 2,147 / 240 / 3,925 / 661 / 355 / 938 / 1,084 / 412 |
| r4-gvb-2 | 9,300 / 680 / 3,172 / 1,436 / 360 / 1,141 |
| r4-curl-1 | 9,252 then 31 calls adding 234-3,126 each |
| r4-curl-2 | 9,252 / 426 / 1,017 / 4,378 / 1,068 / ... 21 calls adding 417-4,378 |

The base (system prompt + skill + task) is 9.3-11.9k and is re-sent every
call at 90-97% cache discount. Turn 2 spikes (gv-2: +4,335; gvb-1: +3,925 on
its discovery turn; curl-2: +4,378) are skill-load/discovery/first-results
costs. Steady-state turns add only ~0.3-2k.

## 6. Ranked next-savings list (measured sizes)

1. Close the ask-router gaps on Q8/Q9-class questions (field usage, literal
   lookup). Cost today: 5.4-8.3KB result bytes per arm per question plus 1-3
   extra LLM turns (~10-16k input each) for fallbacks. Concretely: add a
   field-usage route, and expose Indexes.findValuesContaining over ask/HTTP —
   the graph HAS the data (ValueIndexTest exercises it) but no route serves
   it. This also fixes a correctness bug: gv-2's fuzzy Q9 match pointed at
   ValueIndexTest:75, a misleading non-answer.
2. Trim the tests answer: ~4.2KB via ask today; relative paths save 756B
   (18%); dropping the per-test-method coverage map unless requested saves
   the 8KB->4.2KB delta (~3.8KB on the raw endpoint); add the link-type
   filter already identified in round 3.
3. Make skill load reliable (or inline the tool list into matched=null
   responses). gvb-1's missed skill load cost 9,944B of discovery results;
   gv-2's redundant discovery cost 948B. 2 of 4 gv-family arms were affected.
4. Bake the batching instruction into the skill: measured -15.6% input on
   average, up to -29% best case (gv-2 109.2k vs gvb-2 78.1k), via fewer
   LLM turns each avoiding a ~10-16k base re-send.
5. Do not offer a curl/REST-only workflow to agents: 4.6x input of gv arms
   at equal answer quality, purely from turn-per-request + command tax. If
   REST stays, document multi-endpoint batching per bash call.
6. search exact-name matching returns 44B "none" for substring queries
   (Q12), pushing arms into package enumeration to recover; a
   contains/prefix mode would remove those follow-up calls.

## Tool-quality notes (answer correctness)

- Q5: gv arms found 14 test classes (with link types); grep arms found 10-11
  (missed CallPathTest, CallGraphIndexTest, FileSystemCacheTest, which only
  the graph's exception/2-hop edges surface). Graph wins on completeness.
- Q8: all 4 gv-family arms hit matched=null -> grep fallback (rule-compliant,
  marked). The daemon hint itself says "fall back to grep".
- Q9: grep arms gave the best answer (RUNBOOK --port 8094, no source
  default); gv-2/gvb-2 accepted a fuzzy method-location match (misleading);
  curl arms could not answer at all under their constraints.
- Q3: gv-1's ask mis-matched; both gv arms recovered via the classes tool.
- Q12: no arm found a class literally named Cache (none exists); exact-match
  search semantics were the friction, not a wrong answer.
- Q10: callpath answered directly (239B payload, one direct edge) — the
  cheapest substantive answer of the round.

Raw data: etl/results-r4/attribution.json (per-call usage series + tool
event streams), etl/results-r4/per_question_bytes.json (per-question result
bytes per arm).
