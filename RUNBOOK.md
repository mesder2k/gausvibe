# GausVibe Runbook

Operational guide: running the daemon, the MCP tools, and the Elasticsearch
bakeoff experiments. Last updated after the v3 bakeoff (2026-09-29).

## Architecture

```
Vibe session --MCP stdio--> GausVibeMcpServer --HTTP--> GausVibeServer daemon (owns the graph)
```

- The daemon parses the project into a graph at startup and keeps it in
  memory. It is the ONLY state holder: graph, staleness baseline,
  ask/feedback/edit logs. Run it once with `nohup`; it survives Vibe restarts.
- The MCP process is a stateless proxy Vibe spawns per session. It must
  start instantly, so it never builds a graph. Tools fail open with a
  "not reachable" hint until the daemon is up.

## Running the daemon

The runtime artifact is the shaded jar installed at
`~/.vibe/plugins/gausvibe/gausvibe.jar` (rebuild + reinstall with
`./scripts/build-artifact.sh --install` in the gausvibe repo; the version is
the git commit count). The git repo is not needed at run time.

Fast (gausvibe repo itself, ~5s):

```bash
nohup java -jar ~/.vibe/plugins/gausvibe/gausvibe.jar server \
  --project /Users/magnusfind/Documents/find-shadow-model/gausvibe \
  --port 8094 \
  > /tmp/gausvibe-daemon.log 2>&1 &
```

Elasticsearch `server` module (~20 min build, ~350k nodes incl. tests):

```bash
nohup java -Xmx6g -jar ~/.vibe/plugins/gausvibe/gausvibe.jar server \
  --project /Users/magnusfind/Documents/find-shadow-model/elasticsearch/server \
  --port 8094 \
  > /tmp/gausvibe-daemon.log 2>&1 &
```

Check: `curl -s -m 2 http://localhost:8094/stats` (or `... && echo UP || echo DOWN`).
The daemon answers whatever project it indexed - questions about any other
project return wrong-project answers. Only one project per port.

Use explicit log paths OUTSIDE the ES checkout (e.g. /tmp/gv-es/) so the
user's ES tree stays clean. Default log location is `<project>/.gausvibe/`.

Endpoints: `/ask?q=`, `/tests/{fqn}`, `/callpath?from=&to=&depth=`,
`POST /edited`, `POST /feedback`, `POST /refresh`, `/changes`, `/stats`,
`GET /classes/...`, `/search?q=`. Use `curl -s -G --data-urlencode "q=..."`
for URL encoding (no python needed).

## MCP tools in Vibe

The gausvibe plugin (`~/.vibe/plugins/gausvibe/`) ships `mcp.json` with the
`gausvibe` stdio server. Tools appear as `gausvibe_ask`, `gausvibe_tests`,
`gausvibe_callpath`, `gausvibe_search`, `gausvibe_class_detail`,
`gausvibe_class_members`, `gausvibe_classes`, `gausvibe_packages`,
`gausvibe_edited`, `gausvibe_feedback`, `gausvibe_changes`,
`gausvibe_stats` - but ONLY in sessions started AFTER the plugin files
existed AND after Vibe has discovered them (see the descriptor-cache bug
below). Mid-session `/reload-plugins` is unreliable (see Vibe bugs).

There are TWO pinning layers, and both must be current for the tools to
appear. A fresh session re-pins the plugin FILES (mcp.json etc.) from
disk, but resolves the MCP TOOL LIST from a persistent descriptor cache
at `~/.vibe/logs/mcp-descriptors/{plugins,unified}/`, keyed by a server
fingerprint derived from the server CONFIG - not from the binary the
classpath points at. Recompiling `target/classes` does not invalidate
the cache, so fresh sessions keep serving the OLD tool list. This broke
round 3's precondition 2 on 2026-09-29: the 12-tool binary was compiled
at 18:42, but every session after the 18:34 descriptor discovery was
still served the 7-tool list (verified: the live binary answered
tools/list with 12 via `etl/repro_mcp_client.py`; the session's pinned
`core_input` checkpoint had 7). Fix when the tool list changes while
mcp.json is unchanged:

```bash
rm ~/.vibe/logs/mcp-descriptors/plugins/*.json
rm ~/.vibe/logs/mcp-descriptors/unified/*.json
# then start a FRESH session; absence forces a new tools/list discovery
```

`ask` is restricted to named-symbol structural questions (its description
enumerates the supported patterns); listing, enumeration, and name lookup
go through the deterministic tools (`search`, `classes` - requires a
`package` or `prefix` filter and caps at `limit` (default 500),
`class_detail`, `class_members`, `packages`). `/classes` on the daemon
gained `?package=&prefix=&limit=` filters and returns `count` (slice) plus
`total` (all matches).

Classpath embedded in `mcp.json` args[1] must be regenerated when
dependencies change:

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/gv-deps-cp.txt
# then rewrite mcp.json with target/classes:$(cat /tmp/gv-deps-cp.txt)
python3 etl/validate_mcp.py   # validates against Vibe's own models
```

### Known Vibe harness bugs (verified from logs, all Vibe-side)

- `/reload-plugins` fails on non-quiescent sessions ("cannot re-pin... /
  cannot rebind subagent agent types") - unimplemented `child Runtime
  access` in mistralai_vibe_local_harness. Fix: start a fresh session
  (`/new`), which pins plugins at startup.
- MCP tool descriptors are cached per server-config fingerprint under
  `~/.vibe/logs/mcp-descriptors/`; a fresh session re-pins plugin files
  but reuses the cached tool list. Recompiling the server does not
  refresh it - delete the cache files (see "MCP tools in Vibe") and
  start a fresh session.
- `agent.spawn` can return success without registering when the child
  limit is hit. Always close finished subagents; spawn sequentially and
  check `agent.list` if tools.agent.wait says "Unknown subagent".
- Harvest subagent logs BEFORE `agent.close` - closing deletes the session
  logs under `~/.vibe/logs/session/unified/child-*/`.

## Running the bakeoff (the experiment)

Purpose: measure whether the GausVibe index displaces grep. Metrics that
matter are TOKENS (input tokens = cumulative context re-reads), not call
counts. The ETL harvests per-session token usage from journal records.

Procedure:

1. Start the daemon on the ES `server` module (above), wait for
   `curl localhost:8094/stats`.
2. From a Vibe session with MCP tools loaded, spawn 4 subagents with the
   standard GausVibe preamble (server URL, endpoints, feedback
   instructions) + one of these four fixed tasks:
   - locate: "change default HTTP REST port 9200 to 9215; find where 9200
     is defined everywhere" (read-only)
   - navigate: "explain how PUT /my-index flows from HTTP transport to
     index creation with file:line" (read-only)
   - harness: "which tests verify index-name validation" (read-only)
   - edit: "add hasText(String) to org.elasticsearch.common.Strings"
     (checks existing + reports via /edited)
   Names must be unique per session (es4-locate, ...). Spawn SEQUENTIALLY.
3. `tools.agent.wait` each; harvest immediately (see below); then close.
4. Verify the ES tree stayed clean: `git -C .../elasticsearch status`.

Harvesting:

```bash
# ask/feedback/edit logs from wherever --ask-log pointed
cd gausvibe/etl
python3 etl_ask_log.py --log /tmp/gv-es/ask-log.jsonl   # hit rate, types, unmatched
jq -r '"\(.rating): \(.question) -- \(.comment)"' /tmp/gv-es/feedback-log.jsonl

# session traces + tokens (run BEFORE closing subagents)
python3 etl_question_taxonomy.py
jq -r '.experiments.sessions[] | "\(.n_tool_calls) calls, \((.total_result_bytes/1000|floor))KB, in=\((.input_tokens/1000|floor))k: \(.task[0:70])"' question_taxonomy_report.json
```

GV-byte share per session (context bytes that came from GausVibe answers):
tool results containing "8094" in the child chunks files.

### Baselines (same 4 ES tasks)

| run | index state | calls | result bytes | input tokens |
|---|---|---|---|---|
| v0 | none (grep only) | 83 | ~180KB | not captured (logs lost) |
| v1 | basic /ask+feedback | 102 | ~235KB | not captured (logs lost) |
| v3 | tests+callpath+ambiguity+edited | 110 | ~246KB | 1.26M total (93% cached) |

v3 per-task input tokens: navigate 664k, locate 331k, harness 169k,
edit 92k. GV byte share: navigate 16%, locate 9%, harness 44%, edit 48%.
Conclusion so far: the index SUPPLEMENTS grep rather than replacing it;
the expensive sessions (79% of tokens) are exactly where coverage gaps
push agents back to grep.

## Feature backlog (evidence-ranked after v3)

1. Constants/setting-value index ("where is 9200 the default") - kills the
   confidently-wrong fuzzy fallback on locate-type questions.
2. Interface-dispatch edges for CALLS (callpath cannot cross
   RestHandler.handleRequest -> implementation).
3. Multi-module indexing (netty module outside the server-module graph).
4. Low-confidence fuzzy matches should return candidates, not guesses
   (two of the four v3 "wrong" feedbacks were this pattern).

Fixed during v3 follow-up (need a daemon restart to take effect):
- test-class methods score 15 lower in fuzzy resolution (validateIndexName
  vs testValidateIndexName tie)
- common exact method names no longer beat fuzzy in location questions
  (index() vs validateIndexName)

## Build/test

```bash
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
mvn test    # 162/162 as of the MCP commit (ef129a8)
```

Repo layout pointers: server `src/main/java/dk/gausdalfind/server/`
(GausVibeServer, GausVibeMcpServer, QuestionLogger, StalenessMonitor),
graph engine in `graph/` + `queries/`, ETL in `etl/`.
