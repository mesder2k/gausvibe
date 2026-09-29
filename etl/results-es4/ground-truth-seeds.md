# ES round 4 ground-truth SEEDS — PARTIAL, operator must complete

Status: seeds verified at ES `ba71896390a` on 2026-09-29 by grep+read.
This file is INCOMPLETE on purpose: the operator pre-pass (BAKEOFF_ES_ROUND4.md
"Ground truth") must finish it BEFORE any subagent is spawned. Subagents must
never be shown this file.

## locate — where 9200 is defined (port default)

- [server, VERIFIED] `server/src/main/java/org/elasticsearch/http/HttpTransportSettings.java:91`
  — `SETTING_HTTP_PORT`, Setting key `http.port`, default `"9200-9300"`.
  This is the must-change place for the 9200→9215 task within the server module.
- [server, VERIFIED] A raw `grep -rn 9200 src/main/java` also hits ~30 numeric
  noise lines, all in `search/aggregations/metrics/AbstractHyperLogLog.java`
  (lookup tables). Not port-related. Noise handling counts toward precision.
- [TODO cross-module] Sweep `libs/`, `modules/` (incl. transport-netty4),
  `plugins/`, `distribution/`, `docs/`, rest test yaml for further 9200
  defaults or references; mark each server vs cross-module. Known suspects:
  netty transport defaults, test fixtures, docker/packaging config.

## navigate — PUT /my-index flow (verified hops)

- [VERIFIED] `rest/RestController` route dispatch (interface dispatch to
  handler impl — expect GV callpath to break here).
- [VERIFIED] `server/src/main/java/org/elasticsearch/rest/action/admin/indices/RestCreateIndexAction.java:39-41`
  — `routes()` registers `PUT /{index}`; `:49-51` `prepareRequest` builds
  `CreateIndexRequest` and calls `client.admin().indices().create(...)`.
- [VERIFIED] NodeClient → transport action lookup (registry dispatch —
  second expected GV break point).
- [VERIFIED] `server/src/main/java/org/elasticsearch/action/admin/indices/create/TransportCreateIndexAction.java:49`
  — class; `:88` `masterOperation`; `:157` `createIndexService.createIndex(...)`.
- [VERIFIED] `server/src/main/java/org/elasticsearch/cluster/metadata/MetadataCreateIndexService.java`
  — `createIndex(...)`; index-name validation lives here
  (`validateIndexName` and friends).
- [TODO] Exact file:line of `MetadataCreateIndexService.createIndex` signature
  and the validation entry (`validateIndexName`) for the final hop.

## harness — tests verifying index-name validation

- [server, VERIFIED] `server/src/test/java/org/elasticsearch/cluster/metadata/MetadataCreateIndexServiceTests.java`
  — references `validateIndexName`.
- [TODO] Widen: grep `validateIndexName|IndexNameValidator|index.name`
  constraints across `server/src/test`; also check `IndexSettings`/`IndexNameExpressionResolver`
  test classes; record per class what validation it covers and mark exact-set
  vs partial reference. CallPathTest-style false positives (classes that merely
  USE the service) must be distinguished from tests that VERIFY validation.

## edit — hasText(String) in org.elasticsearch.common.Strings

- [VERIFIED] `Strings.java` has NO `hasText` method. Stale javadoc only:
  line 66 `@see #hasText(String)` (on `hasLength`), lines 118-122 usage
  examples. A correct answer notices these.
- [VERIFIED] Sane insertion point: adjacent to `hasLength(CharSequence)`
  (lines 68-78). Expected body per javadoc contract:
  `return hasLength(str) && str.chars().anyMatch(c -> !Character.isWhitespace(c));`
  (operator: confirm exact idiom when completing ground truth).
- Rubric: right file (1) / correct signature+body (1) / no collateral
  changes (1) / noticed stale javadoc (1) / gv arm reported /edited (1).
