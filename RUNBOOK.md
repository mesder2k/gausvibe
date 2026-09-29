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

Fast (gausvibe repo itself, ~5s):

```bash
CP=$(jq -r '.mcpServers.gausvibe.args[1]' ~/.vibe/plugins/gausvibe/mcp.json)
nohup java -cp "$CP" \
  dk.gausdalfind.server.GausVibeServer \
  --project /Users/magnusfind/Documents/find-shadow-model/gausvibe \
  --port 8094 \
  > /tmp/gausvibe-daemon.log 2>&1 &
```

Elasticsearch `server` module (~20 min build, ~350k nodes incl. tests):

```bash
CP=$(jq -r '.mcpServers.gausvibe.args[1]' ~/.vibe/plugins/gausvibe/mcp.json)
nohup java -Xmx6g -cp "$CP" \
  dk.gausdalfind.server.GausVibeServer \
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
`gausvibe_callpath`, `gausvibe_edited`, `gausvibe_feedback`, `gausvibe_changes`,
`gausvibe_stats` - but ONLY in sessions started AFTER the plugin files
existed. Mid-session `/reload-plugins` is unreliable (see Vibe bugs).

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
