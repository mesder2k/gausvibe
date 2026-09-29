# ES round 4 GROUND TRUTH — COMPLETE, sealed from subagents

All facts verified by grep+read at ES `ba71896390a` on 2026-09-29.
Do not show this file or its contents to any bake-off subagent.

## locate — where 9200 is defined (task: change default to 9215)

- [server, MUST-CHANGE] `server/src/main/java/org/elasticsearch/http/HttpTransportSettings.java:91`
  — `SETTING_HTTP_PORT`, key `http.port`, default `"9200-9300"`.
- [server, NOISE] `server/src/main/java/org/elasticsearch/search/aggregations/metrics/AbstractHyperLogLog.java`
  — ~30 numeric-table lines containing "9200" as digits inside lookup
  constants. Not port-related.
- [cross-module, checked: NONE] No production definition of 9200 anywhere
  else in the repo: `libs/`, `modules/`, `plugins/` main sources have zero
  hits; reindex/remote test fixtures and docs examples reference
  `localhost:9200` (usage, not definition).
- Grading: must-change set = {HttpTransportSettings.java:91} exactly.
  AbstractHyperLogLog hits count against precision. Docs/test-fixture
  mentions are acceptable to list only if labeled as non-definitions.

## navigate — PUT /my-index flow (ordered hops)

1. [interface dispatch — expected GV break] RestController route
   dispatch to handler.
2. `server/src/main/java/org/elasticsearch/rest/action/admin/indices/RestCreateIndexAction.java:39-41`
   — `routes()` registers `PUT /{index}`.
3. Same file `:49-51` — `prepareRequest` builds `CreateIndexRequest`, calls
   `client.admin().indices().create(...)`.
4. [registry dispatch — expected GV break] NodeClient → transport action
   lookup.
5. `server/src/main/java/org/elasticsearch/action/admin/indices/create/TransportCreateIndexAction.java:88`
   — `masterOperation`; `:157` — `createIndexService.createIndex(...)`.
6. `server/src/main/java/org/elasticsearch/cluster/metadata/MetadataCreateIndexService.java:380`
   — `createIndex(...)`; validation at `:277` (static
   `validateIndexName`) and `:1864` (private `validate`).
- Grading: fraction of hops 2,3,5,6 found with correct file:line, in
  order. A chain that silently substitutes a wrong class counts as a
  wrong answer, not a partial.

## harness — tests verifying index-name validation

- [EXACT SET, server] `server/src/test/java/org/elasticsearch/cluster/metadata/MetadataCreateIndexServiceTests.java`
  — `testValidateIndexName()` at line 751 verifies the rules
  (invalid filename chars at :753, '#' at :755, leading '_','-','+'
  at :757). Secondary same-file validators: `testValidateShrinkIndex`
  (:204), `testValidateSplitIndex` (:412).
- No other test class in `server/src/test` references
  `validateIndexName`, `IndexNameValidator`, or invalid-index-name
  assertions (checked).
- Grading: exact set = {MetadataCreateIndexServiceTests.java}.
  False positives (classes that merely use the service) and false
  negatives both fail the gate; listing the specific validation rules
  earns partial credit in the report but not for the gate.

## edit — add hasText(String) to org.elasticsearch.common.Strings

**CORRECTED 2026-09-29 during the run**: the premise of this task is FALSE.
`hasText(String)` ALREADY EXISTS:

- `Strings.java:151-154` — `public static boolean hasText(String str)
  { return isNullOrBlank(str) == false; }`, with its own javadoc block
  (144-152). The line-66 `@see #hasText(String)` and lines 118-122
  javadoc examples are NOT stale — they reference this real method.
- The original ground-truth pass missed it because the verification grep
  was piped through `head -5` and truncated before line 153. Operator
  error, recorded here for honesty.
- CORRECT OUTCOME for both arms: NO edit (a duplicate method would break
  the build), with evidence that the method exists. An arm that blindly
  adds the method FAILS the gate (broken downstream decision).
- Revised rubric: detected existing method (2) / refused to edit with
  evidence (1) / no collateral changes (1) / gv arm: /edited correctly
  NOT called since no edit happened (1). Max 5.
