# Mini Bake-off Round 3 Results (2026-09-30)

First round with the full 12-tool deterministic surface live in the Vibe
session (`ask, tests, callpath, edited, feedback, changes, stats, classes,
class_members, class_detail, search, packages`). All preconditions passed:
`stats` reported 4678 nodes / 6404 edges, `stale: false`; connector discovery
listed 12 functions; smoke test `classes({package: 'dk.gausdalfind.queries'})`
returned 7 classes including `TextSearchIndexTest`.

Environment note: the round-3 attempt on 2026-09-29 was blocked twice and
today's session hit a third blocker, all fixed before this run:

1. Descriptor-cache bug (see RUNBOOK.md): stale 7-tool list served to fresh
   sessions. Fix: delete `~/.vibe/logs/mcp-descriptors/{plugins,unified}/*`.
2. `mcp.json` pinned javaparser 3.25.9 -> daemon crashed with
   `NoSuchFieldError: LanguageLevel.JAVA_21` (the code needs 3.28.2).
   mcp.json updated to 3.28.2.
3. Parallel-parse race: `Indexes.fieldUsages` (added in 19e0a3d) and
   `Indexes.literalValues` (1e60678) were plain `ArrayList`s mutated and
   iterated by parallel parse workers -> `ConcurrentModificationException`
   on 16-19 random files per build, nondeterministic graph (3601-4019
   nodes). Fixed with `Collections.synchronizedList` + synchronized
   iteration blocks; two fresh builds now agree exactly (4678/6404, zero
   parse errors). The "~4.1k nodes" expectation in BAKEOFF_ROUND3.md was
   measured during this corruption era.

## Per-agent harvest

| arm | llm_calls | input | cached_input | uncached | output | tool_calls | result_bytes |
|---|---|---|---|---|---|---|---|
| grep-1 | 5 | 48359 | 42048 | 6311 | 1534 | 7 | 5845 |
| grep-2 | 4 | 44669 | 36608 | 8061 | 1637 | 14 | 31802 |
| gv-1 | 5 | 58087 | 51712 | 6375 | 1383 | 4 | 23378 |
| gv-2 | 4 | 45173 | 39296 | 5877 | 1615 | 3 | 19843 |

Per-arm averages:

| arm | input | uncached | output | tool_calls | result_bytes | llm_calls |
|---|---|---|---|---|---|---|
| grep avg | 46514 | 7186 | 1586 | 10.5 | 18824 | 4.5 |
| gv avg | 51630 | 6126 | 1499 | 3.5 | 21611 | 4.5 |

gv arms still spent one `skill` + one `search_tool_functions` call each on
tool discovery before their first real query; those two calls account for
~20k of each gv arm's (cached) input.

## Per-question gv behavior (the point of this round)

| Q | gv-1 route | gv-2 route | fallback |
|---|---|---|---|
| Q1 GausVibeServer file | ask (class-location) | ask (class-location) | none |
| Q2 GausVibeMcpServer methods | search + class_members | ask (methods) | none |
| Q3 classes in dk.gausdalfind.queries | classes (package) | classes (package) | none |
| Q4 TextSearchIndex location | search + class_detail | ask (class-location) | none |
| Q5 tests verify GraphQueryEngine | tests | ask -> tests | none |
| Q6 implementers of JavaGraphQuery | class_members (implementations) | ask (implementers) | none |

Zero `matched=null`, zero tool errors, zero grep fallbacks - both gv arms
completed all six questions deterministically. Round 2's Q3 failure (ask
misrouted to `unindexPackage`, grep fallback required) is gone: both arms
routed Q3 through `classes`, and Q6 through `class_members`/`ask`.

## Answer agreement

Q1, Q2, Q4, Q6: all four arms agree fully (gv arms add line numbers and
member counts).

Q3: gv arms returned 7 classes including nested `TextSearchIndex$Builder`
and `TextSearchIndexTest$MockNode`; grep arms listed only the 4-5 top-level
types (nested types require reading file bodies - a grep miss, not a gv
miss).

Q5 is the interesting one. Grep ground truth in the current repo is now 11
referencing test files (both grep arms converged: SelfEditValidationTest,
FieldReceiverCallEdgeTest, IndirectCoverageTest, NestedClassContextTest,
TestGraphBuilder, UpdateFileTest, SelfEditRoundTripTest,
InterfaceDispatchEdgeTest, AskResolutionTest, ModernSyntaxParseTest,
TestCoverageTest). The BAKEOFF_ROUND3.md ground-truth list (6 files) was
stale - the repo gained 5 referencing test classes since round 2.

Round-2 graph bug recheck (gv answer = 14 test classes):
- False negative AskResolutionTest: FIXED - both gv arms include it.
- False positive CallPathTest: STILL PRESENT - both gv arms include
  CallPathTest, which does not reference GraphQueryEngine. It enters via
  2-hop call-graph links (the "behavior-verifying tests" link semantics from
  31947d2), as do FileSystemCacheTest and CallGraphIndexTest (exception-edge
  links only). gv-1 explicitly annotated the link types per class, so the
  false positive is attributable rather than silent, but under the strict
  "references" semantics it is still wrong. The `tests` tool needs either a
  link-type filter or a direct-reference fast path.

## Comparison

| metric | baseline grep | round2 grep | round3 grep | baseline gv | round2 gv | round3 gv |
|---|---|---|---|---|---|---|
| input | 72.3k | 84.5k | 46.5k | 38.6k | 90.0k | 51.6k |
| uncached | - | 9.5k | 7.2k | - | 4.5k | 6.1k |
| tool_calls | 7 | 10 | 10.5 | 3 | 7 | 3.5 |
| result_bytes | 26.6k | 32.2k | 18.8k | 8.9k | 13.7k | 21.6k |

- gv tool calls halved vs round 2 (7 -> 3.5): with deterministic tools
  available, each question is 1-2 calls instead of one batched `ask` per
  several questions. Round 2's batching-everything-into-`ask` pattern is
  gone.
- Uncached input: gv (6.1k) and grep (7.2k) are now within ~15% of each
  other; cached_input was 87-91% of input in every arm, consistent with
  round 2.
- gv result_bytes rose vs round 2 (13.7k -> 21.6k): deterministic tools
  return structured detail (member tables, line numbers, link types) that
  free-form `ask` answers didn't. grep result_bytes fell (32.2k -> 18.8k)
  with high variance (5.8k vs 31.8k) depending on how much file content the
  arm had to dump.

## Conclusion

With the 12-tool surface actually live and a deterministic graph (the
parallel-parse race fixed), the gv arm answered all six questions with no
fallbacks, fewer tool calls, and equal uncached-token cost, while
outperforming grep on Q3 (nested types) and providing link-type
attribution on Q5. Remaining defect: the `tests` tool's 2-hop/exception
link semantics still surface CallPathTest as a GraphQueryEngine verifier.

Session/run provenance: child sessions
grep-1=child-f101f32ad98e93f85fad7a58, grep-2=child-dc7382097853cc7566d799a1,
gv-1=child-726c6090cad6f5d7b4370283, gv-2=child-0038e30c09963a0f251a02ab,
harvested with `etl/etl_mini_bakeoff.py` before agent close.
