# SWE-bench-java bakeoff pilot (round 5): gv vs grep on real benchmark questions

Phase 1 of the SWE-bench-java bakeoff (Todoist task 6hg7Mm8GP2C7jpVW): the
10-question pilot set (Todoist parent 6hg7vQcxx9hQhCFW), derived from two
SWE-bench-java-verified instances, answered by 8 arms. Repos at exact base
commits under ../bakeoff-repos/ (created for this round, kept for the next).

## Design

- Instances: google__gson-1391 (google/gson @ 3f4ac29f), 5 questions;
  fasterxml__jackson-core-1053 (FasterXML/jackson-core @ bb778a0a), 5 questions.
  Questions span ask / callpath / field-usage / tests / literal routes; full
  ground truth in etl/swe-java-questions/*.json.
- Arms (2 per config, round-4 constraints): gv-only (tools.mcp_gausvibe.* via
  run_typescript; marked grep fallback on matched=null) and grep-only (shell
  grep/find/sed + reads). Same model for all arms. No curl arm (killed in r4).
- Daemon: one project per port; gson run first, then daemon restarted on
  jackson-core (fresh arms for the second repo). Question sets, ground truth,
  and base-commit clones were NOT given to arms - only the question strings.
- Harvest: etl/etl_bakeoff_attribution.py per arm, BEFORE closing agents.
  Child sessions: gson gv-1=f3daf40e69c21f9fde1768ad gv-2=c468658ca0fc5c9589c995b5
  grep-1=fed810d4633cc3892ba1d454 grep-2=8f6e1a2c9c30988a38b81e87; jackson
  gv-1=2c7408dd53c961f662fca5cb gv-2=8b5277b593a57a61f2f968cf
  grep-1=1c4789ddca301fd5b3bc691b grep-2=bc6def02737c4ffe8e098b09.

## Cost (per arm; from attribution-gson.json / attribution-jackson.json)

| arm | calls | input | uncached | output | result bytes |
|---|---|---|---|---|---|
| gv-gson-1 | 9 | 237,412 | 26,724 | 4,101 | 121,262 |
| gv-gson-2 | 5 | 113,112 | 12,376 | 5,867 | 72,659 |
| grep-gson-1 | 2 | 30,936 | 1,944 | 1,056 | 40,753 |
| grep-gson-2 | 8 | 99,461 | 6,469 | 2,035 | 23,247 |
| gv-jackson-1 | 8 | 108,974 | 7,022 | ~3,500 | 24,044 |
| gv-jackson-2 | 9 | 123,965 | 6,781 | ~3,500 | 23,840 |
| grep-jackson-1 | 6 | 70,162 | 5,074 | ~1,200 | 17,410 |
| grep-jackson-2 | 5 | 51,275 | 7,371 | 1,310 | 7,975 |

Billable (uncached input + output): gson gv avg ~23.2k vs grep avg ~5.4k
(4.3x); jackson gv avg ~10.3k vs grep avg ~10.0k (parity). The gson gap is
payload-driven: gv-gson-1 pulled 121k result bytes, dominated by the tests
answer (34 covering classes) and class_members on $Gson$Types - r4 savings
list #2 (trim the tests answer) reconfirmed on a real benchmark repo.
gv-gson-2 also paid 17.3k of search_tool_functions discovery bytes (r4
finding #3 again). Cheapest run of the round: grep-gson-1, all 5 questions
in 2 batched bash calls.

## Correctness (strict = required items present and correct; +/-1 line tolerance)

| question (route) | gv-1 | gv-2 | grep-1 | grep-2 |
|---|---|---|---|---|
| gson Q1 locate resolve (ask) | P | P | P | P |
| gson Q2 callers of resolve (callpath) | P | P | F | F |
| gson Q3 visitedTypeVariables (field-usage) | P* | P* | P | P |
| gson Q4 tests (tests) | P | P | P | P |
| gson Q5 fromJson binding path (callpath) | F(line) | F(line) | P | P |
| jackson Q1 Version/compareTo (ask) | P | P | P | P |
| jackson Q2 _snapshotInfo (field-usage) | P | P | P | P |
| jackson Q3 VersionUtil callers (callpath) | P | P | P | F |
| jackson Q4 version tests (tests) | P | P | P | P |
| jackson Q5 V_SEP literal (literal) | P* | P | P | P |

P* = correct via the sanctioned marked grep fallback. F(line) = right
component/method, wrong line (method-start line 144 vs call line 160).
Totals: gv 18/20 strict, grep 17/20 strict; lenient (must-mention set)
20/20 for every arm - the lenient bars are too loose to discriminate.

## Findings

1. The graph beat naive grep on caller completeness - and fixed our ground
   truth. Both gv arms found that $Gson$Types.getSupertype (L283) is a third
   caller of resolve; both grep arms (mirroring how the ground truth itself
   was derived) reported "only ReflectiveTypeAdapterFactory", because
   `grep "Types.resolve("` misses same-class calls written as bare `resolve(`.
   The original strict GT was wrong; corrected in
   etl/swe-java-questions/google__gson-1391.json. This is the round's clearest
   tool-quality win for gv and a caution for grep-based GT derivation.
2. Grep beat the graph on line precision. Graph answers report method-start
   lines; both gv arms placed the resolve call inside getBoundFields at 144
   (the method declaration) instead of 160 (the call site), failing Q5 strict.
   Question sets should either ask for method+file (tolerant) or the graph
   should expose call-site lines for call edges.
3. Field-usage behaves honestly. ask matched field-usage on the real field
   _snapshotInfo (both jackson gv arms) and returned matched=null for
   gson's visitedTypeVariables (a method parameter, not modeled as a field),
   where both arms recovered via marked fallback. No fuzzy mis-matches this
   round (contrast r4 Q9); the one wrong-route match (jackson gv-2 Q1,
   "implementations", count 0) was harmless and recovered.
4. The literal question (V_SEP) is answerable through the graph without a
   literal route: gv-jackson-2 listed VersionUtil's fields via class_members
   and used the fields relation on V_SEP - no fallback. gv-jackson-1 got
   matched=null twice and fell back. Supports the planned literal route
   (Todoist 6hg7cX8xGgcXWWH3) AND suggests a cheaper intermediate: a
   "class fields" hint in the matched=null fallback message.
5. Cost verdict for phase 1: correctness parity (both configs answer real
   benchmark questions reliably), cost favors grep - parity on jackson, 4.3x
   billable for gv on gson where the tests/class_members payloads are large.
   The tests answer remains the single biggest gv cost driver on a real repo,
   now measured at 121k result bytes worst case vs 41k for the batched grep
   arm.

## Question-set fixes revealed by this run

- google__gson-1391.json Q2: strict GT corrected (third caller getSupertype;
  naive grep GT was incomplete). Q3/Q5 annotated with round-5 behavior notes.
- fasterxml__jackson-core-1053.json: all five GTs validated; Q1 line tolerance
  (134 vs 135) and Q5 fallback notes recorded.
- Lenient bars need tightening for future rounds (all-pass everywhere).

## Next

- Phase 2 (stretch, same harness): end-to-end solving on these instances,
  gv-enabled vs not, plus organic-lookup harvesting from those sessions
  (Todoist 6hg86RQH3MCrh4mW).
- Scale question sets to the remaining instances, preferring ones whose issue
  text names no location (gson-1391 pattern) over lookup-trivial ones.
