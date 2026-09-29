# ES round 4 harvest record (etl_mini_bakeoff.py output, 2026-09-29)

Graph: ES server module @ ba71896390a, 370265 nodes, 604098 edges.
All 8 arms completed. One harness incident: es4-navigate-gv FAILED
(Vibe RustHarnessNotification empty-message validation bug) and was
retried as es4-navigate-gv2; the failed session's cost is recorded
below as an incident, excluded from arm averages.

es4-locate-grep: llm_calls=6 input=79744 cached_input=71232 output=1613 tool_calls=6 result_bytes=31808
es4-locate-gv: llm_calls=10 input=194272 cached_input=182656 output=12453 tool_calls=10 result_bytes=45111
es4-navigate-grep: llm_calls=42 input=995595 cached_input=970432 output=9448 tool_calls=55 result_bytes=101031
es4-navigate-gv-FAILED (incident): llm_calls=8 input=144574 cached_input=127168 output=5557 tool_calls=9 result_bytes=83632
es4-navigate-gv2: llm_calls=18 input=513476 cached_input=483584 output=17353 tool_calls=17 result_bytes=129536
es4-harness-grep: llm_calls=10 input=131114 cached_input=122944 output=3797 tool_calls=16 result_bytes=31564
es4-harness-gv: llm_calls=7 input=86212 cached_input=80256 output=3778 tool_calls=6 result_bytes=22340
es4-edit-gv: llm_calls=10 input=139693 cached_input=132928 output=8081 tool_calls=11 result_bytes=21953
es4-edit-grep: llm_calls=8 input=92858 cached_input=87488 output=3996 tool_calls=10 result_bytes=18264

Correctness gates (vs etl/results-es4/ground-truth.md):
- locate: both arms PASS (must-change = HttpTransportSettings.java:91)
- navigate: both arms PASS (all ground-truth hops, ordered)
- harness: both arms PASS (core class + verified extras; no false positives)
- edit: both arms PASS (detected false premise, refused duplicate edit)

Grading notes:
- harness-grep extras spot-checked and verified: IndexNameExpressionResolverTests,
  SelectorResolverTests, RolloverRequestTests (asserts "Invalid index name
  [alias-index::*]..." at :254), IndexNameGeneratorTests,
  CrossProjectIndexExpressionsRewriterTests all assert name-validation rules.
- edit task premise was FALSE: hasText(String) exists at Strings.java:153.
  Ground truth corrected mid-run; both arms correctly refused the edit.
- Operator ground-truth error recorded: the original Strings.java check was
  truncated by `head -5`, hiding line 153.

ES tree verified clean after the run (daemon logs moved to
etl/results-es4/daemon-logs/).
