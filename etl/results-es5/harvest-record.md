# ES round 5 harvest record (etl_mini_bakeoff.py output, 2026-09-29)

Round 5 = round 4 repeated after the five backlog items were implemented
(commits b1e12cd..b91fdfd): line numbers, value index, interface-dispatch
edges, nested-class-context fix, graph persistence. Same 4 tasks, same
prompts, same ground truth, ES server @ ba71896390a.
Graph: 370494 nodes, 728541 edges (r4: 604098 pre-fix / 683836 with
dispatch edges only - the nested-class fix added ~45k resolvable members).

es5-locate-grep: llm_calls=7 input=94220 cached_input=66048 output=3410 tool_calls=8 result_bytes=41032
es5-locate-gv: llm_calls=8 input=162431 cached_input=146688 output=5983 tool_calls=8 result_bytes=63572
es5-navigate-grep: llm_calls=32 input=730305 cached_input=707008 output=7504 tool_calls=49 result_bytes=96059
es5-navigate-gv: llm_calls=17 input=436153 cached_input=400448 output=11980 tool_calls=16 result_bytes=154368
es5-harness-grep: llm_calls=7 input=78846 cached_input=73792 output=2994 tool_calls=11 result_bytes=17634
es5-harness-gv: llm_calls=12 input=212849 cached_input=197504 output=5400 tool_calls=11 result_bytes=68915
es5-edit-gv: llm_calls=8 input=95073 cached_input=89600 output=2684 tool_calls=7 result_bytes=17184
es5-edit-grep: llm_calls=7 input=68274 cached_input=65280 output=1588 tool_calls=6 result_bytes=7003

Correctness gates: all 8 arms PASS.
- locate: both exact (HttpTransportSettings.java:91; noise + cosmetic
  SettingsModule.java:116 classified). gv's primary hit came from the new
  value index (value-location "9200-9300" at :91).
- navigate: both complete ordered hop lists with file:line. gv hop lines
  are now graph-native; single marked fallback (IndicesService.createIndex)
  vs round 4 where ALL line numbers required grep fallback.
- harness: grep 3 core + 7 adjacent classes (verified r4 ground truth);
  gv 2 core classes with method-level detail, lower recall.
- edit: both detected the false premise (hasText(String) exists at
  Strings.java:153) and correctly refused to edit; empty diffs.

Incidents: none this round (r4's harness crash did not recur).

Note on uncached variance: single-run uncached numbers swing heavily with
the harness's cache-hit behavior (grep-locate uncached was 8.5k in r4 and
28.2k in r5 on the identical task); treat uncached as directional only at
n=1. Input, llm_calls, and tool_calls are the stable metrics.
