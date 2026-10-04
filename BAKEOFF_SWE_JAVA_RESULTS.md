# SWE-bench-java lookup bakeoff: final results and verdict

Cumulative results of the gv-vs-grep lookup bakeoff (Todoist 6hg7Mm8GP2C7jpVW):
gv-only vs grep-only arms answering benchmark-derived structural questions
at exact base commits, strict/lenient scored against grep-verified ground
truth, harvested with etl/etl_bakeoff_attribution.py before arm close.
Protocol: BAKEOFF_ROUND5_SWE.md. Question sets: etl/swe-java-questions/.

- Rounds 5/r5b/r5c: google/gson-1391 + jackson-core-1053 (small repos,
  10 questions, 8 arms per round), before and after the router/payload fix
  PRs (#4, #5, #7).
- Round 6 (r6v3): apache/dubbo-10638 + dubbo-11781 (large-repo cell,
  4,387 files / 457k lines, 10 questions, 8 arms) after the graph-build fix
  PR (#9: comment-glued names, constructor-chained CALLS, UTF-8 response
  length, %-double-decode). Attribution: etl/results-r6v3/*.json.

Two earlier round-6 attempts (r6, r6v2) ran against stale, partially
unfixed builds (PR #9 not yet merged / target/classes not recompiled) and
are discarded; their data is preserved in /tmp/invalid-results-r6*.

## Cost (billable = uncached input + output, avg per arm)

| repo | gv | grep | ratio |
|---|---|---|---|
| gson (r5, pre-fix) | 24,534 | 5,752 | 4.3x |
| gson (r5c, post-fix) | 12,428 | 7,777 | 1.6x |
| jackson (r5c, post-fix) | 11,122 | 4,029 | 2.8x |
| dubbo-10638 (r6v3) | 9,958 | 8,044 | **1.24x** |
| dubbo-11781 (r6v3) | 24,034 | 6,170 | 3.9x |

grep's cost did NOT explode on dubbo (6.2k-8.0k, same band as the small
repos): for symbol-targeted structural questions, both tools' cost tracks
the question, not the repo size. The pre-registered "grep cost scales with
repo noise" hypothesis is NOT confirmed for this question type. gv's worst
arm (30k on 11781) probed heavily; its best set (10638) ran at 1.24x with
zero fallbacks - arm strategy variance dominates at this n.

## Correctness (strict, corrected GTs)

| round | gv | grep |
|---|---|---|
| r5 (small repos, pre-fix) | 18/20 | 17/20 |
| r5b (post-router fixes) | 19/20 | 17/20 |
| r5c (post-payload caps) | 18/20 | 18/20 |
| r6v3 (dubbo, post-graph fixes) | 18/20 | 19/20 |

Miss profiles are qualitatively different and stable:
- grep misses are COMPLETENESS failures from search strategy: the
  same-class caller ($Gson$Types.getSupertype) missed by 6/6 grep arms
  across r5/r5b/r5c; PojoUtils.realize0 missed by 1/2 dubbo grep arms
  (r6v3) - and confidently wrong ("only one production caller").
- gv misses are PHRASING-sensitive route failures (who-calls asked at
  class level returns 0; per-method asking fixes it - arms that did found
  complete sets) and the documented method-start-line format. gv failure
  mode is honest null / partial with correct parts; grep's is
  confident-wrong.

## Ground truth was wrong four times - and the arms found it

1. gson Q2: getSupertype is a same-class caller of resolve (gv found it;
   grep and the GT derivation pattern could not).
2. dubbo-10638 Q2: CompatibleFilter.onResponse is a second production
   caller (all arms found it; the GT missed it).
3. dubbo-11781 Q2: CacheableFailbackRegistry is a third caller (3/4 arms
   found it; the GT missed it).
4. dubbo-10638 Q3: the field-usage index records one usage line per
   method, so a both-lines strict GT is unservable by design (GT
   annotated).
All corrected in etl/swe-java-questions/*.json. Lesson: derive caller GT
with the graph or bare-name greps, never `Class.method(` patterns.

## Verdict (the bakeoff task's question: do the graph tools pay for
themselves on real workloads?)

- On billable cost alone: NO - gv runs 1.2-4x grep at n=2 arms per cell,
  and the large-repo cost crossover did not materialize for
  symbol-targeted questions.
- On correctness: roughly parity on strict scores, with an asymmetric
  failure profile that favors gv: complete caller sets (its clearest
  repeated win), honest nulls instead of confident-wrong answers, and a
  measured record of catching GT derivation errors that grep-based
  methodology (including ours) structurally repeats.
- The fixes mattered enormously: gv went 4.3x -> 1.2x billable on its best
  large-repo cell purely from engineering (router honesty, payload caps,
  graph-build correctness) - none of it model work.

Net: gausvibe is a correctness/robustness instrument at a ~2x average cost
premium, not a cost win, on the pre-patch lookup layer of real SWE tasks.
Use it where confident-wrong answers are expensive and callers/completeness
matter; use grep where cost dominates. The harness should prefer gv for
call-graph-shaped lookups and grep for literal noise-scans, and the
matched=null fallback path is the right seam between them.

## Follow-ups still open

- Graph-build nondeterminism: same commit+code resolved 61,659 vs 72,302
  call sites across builds (parallel parse order). Todoist 6hg8G2Vc539PWpC4.
- The MCP plugin jar still predates PR #4's client-side changes (fields
  relation, verbose, methods/link params) - rebuild + redeploy.
- Q1-style free-text "where is the X machinery" questions mis-route to
  field-usage noise before class-members recovery; a class-alias or
  semantic-locate route would remove the remaining rephrase cost.

## Payload-diet verification run (r6d)

After the diet (4KB answer ceiling, 10-row count-first callers, 20-row
tests answers - PR #9 commit acfbdb2), the expensive 11781 cell was
re-run with 4 fresh arms. Attribution: etl/results-r6d/.

| arm | pre-diet billable | diet billable |
|---|---|---|
| gv-1 | 30,015 (74.6k resB) | 14,718 (47.6k resB) |
| gv-2 | 18,054 (58.5k resB) | 35,180 (104k resB, 15 calls) |
| grep avg | 6,170 | 6,600 (stable: 5.2-7.1k across all 3 runs) |

Mechanically the diet works: the 57KB single-ask case is impossible and
the best gv arm halved. But the AVERAGE is flat (24.0k -> 24.9k): capped
answers induce narrowing - more lean probes, each re-sending the ~9-10k
base context. gv's remaining cost is PROBE COUNT, not payload, and
probe count varies 2x between gv arms on identical questions (7 vs 15
calls) while grep arms are rock-stable.

Levers, ranked by expected impact (follow-ups ticketed):
1. First-shot routing quality - every mis-route costs a full call cycle
   (class-level "who calls X" nulls, Q1 fuzzy mis-matches).
2. Batched asks in the skill (one run_typescript program, several asks)
   - amortizes the per-call base; r4 measured -15.6% input from batching.
3. The ceiling stays as tail insurance regardless.
