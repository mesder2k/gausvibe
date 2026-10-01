# ES Bake-off Round 4: paired GV vs grep arms with quality gates

Instruction file. Round 4 exists to answer the question the mini-bakeoffs
cannot: on a large codebase, does GausVibe's answer quality translate into
better downstream decisions per token? Design: the 4 canonical ES tasks
from the RUNBOOK (locate, navigate, harness, edit), each run by TWO arms
with identical prompts — one grep-only, one GV-only — graded against
operator-collected ground truth, with per-agent token harvest from session
journals. Headline metric: **uncached tokens to a correct answer**.

Everything below was verified against ES `ba71896390a` on 2026-09-29.

## Preconditions — do these first, in order

1. ES checkout clean and pinned:
   `git -C /Users/magnusfind/Documents/find-shadow-model/elasticsearch status --short`
   must be empty; record `git rev-parse HEAD` in the results. If the SHA
   differs from `ba71896390a`, re-verify the ground-truth seeds below
   before running.
2. Tool registry: `search_tool_functions` (mode `all_connector_capabilities`,
   connectors ["mcp_gausvibe"]) must list **12** functions. If 7, STOP —
   stale plugin snapshot; see RUNBOOK "MCP tools in Vibe" for the
   descriptor-cache fix.
3. Swap the daemon to the ES graph (~20 min build, ~350k nodes):
   ```bash
   pkill -f "dk.gausdalfind.server.GausVibeServer --project"
   mkdir -p /tmp/gv-es
   CP=$(jq -r '.mcpServers.gausvibe.args[1]' ~/.vibe/plugins/gausvibe/mcp.json)
   nohup java -Xmx6g -cp "$CP" dk.gausdalfind.server.GausVibeServer \
     --project /Users/magnusfind/Documents/find-shadow-model/elasticsearch/server \
     --port 8094 > /tmp/gv-es/daemon.log 2>&1 &
   ```
   Poll `curl -s -m 2 http://localhost:8094/stats` until `stale:false`,
   `rebuilding:false`, nodes ~350k. The MCP proxy is stateless HTTP, so a
   mid-session swap needs NO fresh Vibe session as long as the tool list is
   unchanged (it is — same 12 tools).
4. Smoke-test the ES graph (via run_typescript):
   - `classes({package:'org.elasticsearch.common'})` returns classes
     including `Strings`.
   - `ask('where is Strings defined')` → `org/elasticsearch/common/Strings.java`.
   - `tests({class_fqn:'org.elasticsearch.cluster.metadata.MetadataCreateIndexService'})`
     returns tests including `MetadataCreateIndexServiceTests`.

## Ground truth — operator pre-pass, BEFORE spawning any agent

Write the result to `etl/results-es4/ground-truth.md`. Subagents must
NEVER see this file or be told its contents. Collect it with grep+read
across the WHOLE elasticsearch repo (GV indexes only the `server`
module — mark each item `server` or `cross-module`; the cross-module
subset is GV's structural blind spot, to be measured, not hidden).

Seeded facts (verified at `ba71896390a`, refine but do not trust blindly):

- **locate**: `server/src/main/java/org/elasticsearch/http/HttpTransportSettings.java:91`
  defines `http.port` default `"9200-9300"` (`SETTING_HTTP_PORT`). A raw
  grep for `9200` in server hits ~30 numeric-noise lines
  (AbstractHyperLogLog tables) — noise handling is part of the task.
  Cross-module: check at minimum `libs/`, `modules/`, `distribution/`,
  and docs/yaml rest tests for the port default.
- **navigate**: operator traces PUT /my-index at this SHA: RestController
  dispatch → `RestCreateIndexAction` → `TransportCreateIndexAction` →
  `MetadataCreateIndexService.createIndex(...)` → index creation. Verify
  each hop's file:line and note where the chain crosses interface
  dispatch (GV's callpath cannot cross interface dispatch — RUNBOOK
  backlog item 2; expect the gv arm to break the chain there).
- **harness**: `server/src/test/java/org/elasticsearch/cluster/metadata/MetadataCreateIndexServiceTests.java`
  references `validateIndexName`. Widen: grep for index-name validation
  (`IndexNameValidator`, `validateIndexName`, `index.name` constraints)
  across `server/src/test`; record the exact test classes and which
  validation rules each verifies.
- **edit**: `Strings.java` has NO `hasText` method — only a stale javadoc
  reference (`@see #hasText(String)` at line 66, examples at 118-122).
  Canonical insertion point: near `hasLength` (lines 68-107). A correct
  answer notes the stale javadoc. Ground truth = one diff rubric, below.

## Procedure

1. Eight subagents, spawn SEQUENTIALLY via `tools.agent.spawn`, check
   `tools.agent.list` after each. Names (round-4 convention):
   `es4-locate-grep`, `es4-locate-gv`, `es4-navigate-grep`,
   `es4-navigate-gv`, `es4-harness-grep`, `es4-harness-gv`,
   `es4-edit-grep`, `es4-edit-gv`. Child-session limits apply (RUNBOOK):
   `tools.agent.wait` → harvest → close EACH agent before spawning the
   next pair.
2. Order matters for the edit task: run `es4-edit-gv` FIRST (graph
   matches the pristine tree), verify + archive the diff, then
   `git -C .../elasticsearch checkout -- server/src/main` to reset, then
   `es4-edit-grep`. Re-verify the tree is clean between arms.
3. Arm constraints (identical for all tasks, with one edit exception):
   - grep arm: ONLY grep/rg/find/read_file/cat. No mcp_gausvibe tools,
     no requests to localhost:8094. For the edit task they MAY edit the
     target file (the constraint covers search, not writing).
   - gv arm: ONLY `tools.mcp_gausvibe.<name>` via run_typescript (12
     tools). No grep, no source file reads. For the edit task they MAY
     write the edit to the target file (edit_file/bash) and MUST report
     it via `tools.mcp_gausvibe.edited` afterwards. If a tool errors or
     `ask` returns `matched=null`, note it per task and only then fall
     back to grep, explicitly marked.
4. Task prompts (same for both arms; deliverable format fixed so grading
   is comparable):
   - locate: "The default HTTP REST port 9200 must change to 9215. Find
     every place where 9200 is defined or defaults the port, with
     file:line, and state which single place(s) must change. Read-only;
     deliver a final list."
   - navigate: "Explain how PUT /my-index flows from HTTP transport to
     index creation, with file:line per hop. Read-only; deliver the
     ordered hop list."
   - harness: "Which test classes verify index-name validation? Deliver
     the list with file paths and one line on what each verifies.
     Read-only."
   - edit: "Add `public static boolean hasText(String str)` to
     org.elasticsearch.common.Strings, consistent with the existing
     javadoc. Do not change anything else. Deliver the final diff."
     Repo path: `/Users/magnusfind/Documents/find-shadow-model/elasticsearch/server`.
     Append the arm constraints and (edit only) the write/report rules.
5. Per agent: `tools.agent.wait` (timeoutMs 3600000 — ES tasks are long),
   harvest IMMEDIATELY (closing deletes logs):
   ```bash
   cd /Users/magnusfind/Documents/find-shadow-model/gausvibe && python3 \
     etl/etl_mini_bakeoff.py --child <name>=$HOME/.vibe/logs/session/unified/child-<ID> [...]
   ```
   then `tools.agent.close`.
6. After both edit arms: verify
   `git -C .../elasticsearch status --short` is clean (edits archived to
   `etl/results-es4/` first). Restore the gausvibe-repo daemon on 8094
   afterwards if other work continues (RUNBOOK fast-start command).

## Grading

Per task and arm:

- **Correctness** (gate; graded against ground-truth.md):
  - locate: precision/recall over the ground-truth file:line set; the
    must-change set must be exactly right. Numeric-noise hits count
    against precision.
  - navigate: fraction of ground-truth hops found in order; note
    explicitly where (if) the chain breaks at interface dispatch.
  - harness: exact test-class set; false positives/negatives listed.
  - edit: diff rubric — right file (1), correct method body and
    signature per javadoc contract (1), no collateral changes (1),
    noticed the stale javadoc references (1), gv arm reported /edited
    (1). Optional operator step if time permits:
    `./gradlew :server:compileJava` — record but do not gate on it.
- **Cost** (from ETL): llm_calls, input, cached_input, uncached, output,
  tool_calls, result_bytes.
- **Headline**: uncached-to-correct — uncached tokens for arms that pass
  the correctness gate; arms that fail are reported as
  `uncached + FAIL`, never averaged with passing arms.

## Report

- Per-task table: arm × {correctness, uncached, input, calls, result
  bytes, fallbacks used}.
- GV-specific observations: matched=null rates, wrong-route answers,
  grep fallbacks, and the three known structural gaps (no constants
  index for locate; interface dispatch for navigate; single-module
  scope for locate cross-module items).
- Baselines for comparison: v3 per-task input (navigate 664k, locate
  331k, harness 169k, edit 92k — 93% cached), v0/v1/v3 table in the
  RUNBOOK, and mini-bakeoff round 3b (gv 5.5k uncached avg, 3.5 calls;
  grep 3.8k uncached, 13 calls). NOTE: v3 ran ONE agent per task
  unpaired with no ground-truth gate; round 4 is not directly
  comparable on correctness — compare cost direction and gap sizes, and
  say so in the report.
- End with the actual decision the round exists for: per task, which
  arm produced the trustworthy answer the operator could act on, and
  what it cost in uncached tokens.
