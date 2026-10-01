# Task management policy

Durable task tracking for this repo lives in **Todoist**, in the project
`Magnus / gausvibe` (project ID `6hfw8M9448FQV96W`), accessed through the
`tools.connector_todoist_93a7.*` connector tools (`find_tasks`, `add_tasks`,
`update_tasks`, `complete_tasks`, `find_sections`, `add_sections`,
`add_comments`).

Do NOT use markdown files (PLAN.md, TODO.md, TASK_BREAKDOWN.md, ISSUES.md,
or similar) as the task store. The existing .md plan files in this repo are
historical; do not add new ones and do not add tasks to them.

## Session start

When the user asks to work on this repo without naming a specific task, pull
the open backlog first:

1. Pull open tasks in project `6hfw8M9448FQV96W`
   (`connector_todoist_93a7.find_tasks`).
2. Present the top candidates (p1 first) and let the user pick, unless the
   user already named the task.
3. Write the in-session todo list for the chosen task's steps.

## During work

- Complete a step in the session todo list → mark the corresponding Todoist
  task complete (`connector_todoist_93a7.complete_tasks`).
- Discover follow-up work while implementing → add it as a new Todoist task
  in project `6hfw8M9448FQV96W` with priority reflecting urgency
  (`p1` urgent, `p2` this week, `p3` sometime, `p4` idea). Do not start it
  unless the user says so.
- Scope creep during a task → add it to Todoist, do not do it inline.
- Longer context for a task goes in the task's `description` or as a
  comment, not in chat and not in a file.

## Task shape

- Task content: one actionable imperative sentence.
- Description: acceptance criteria and context.
- Subtasks for steps that must all land together; separate Todoist tasks
  for independent work.
- Label `gausvibe` is not needed — project membership is enough.

## When the user says "what should I work on"

Pull open tasks from project `6hfw8M9448FQV96W`, sorted by priority, and
summarize them. Do not invent tasks from the old markdown plans unless the
user asks for that specifically.

## Artifact workflow (runtime)

The Vibe plugin launches GausVibe from the installed artifact
`~/.vibe/plugins/gausvibe/gausvibe.jar` (configured in
`~/.vibe/plugins/gausvibe/mcp.json`). The git repo is a build input only —
never launch tools via `target/classes` plus ad-hoc `.m2` classpaths.

Rules:

- Build the runnable jar with `./scripts/build-artifact.sh`. It produces
  `target/gausvibe-<N>-all.jar`, where `<N>` is the git commit count
  (`git rev-list --count HEAD`). The version is embedded in the jar
  manifest (`Implementation-Version`) and printed by
  `java -jar <jar> version`.
- After changing GausVibe code, rebuild AND reinstall so the tools run the
  new code: `./scripts/build-artifact.sh --install`.
- The jar is self-contained (shaded, no classpath needed) and dispatches
  subcommands: `server`, `mcp`, `build`, `query`, `edit`, `interactive`;
  anything else delegates to the CLI.
- The graph daemon runs separately and owns the graph:
  `nohup java -jar ~/.vibe/plugins/gausvibe/gausvibe.jar server --project <path> --port 8094 &`
  Check with `curl -s -m 2 http://localhost:8094/stats`.
- Operational detail lives in RUNBOOK.md; the old classpath-based plugin
  config is backed up at `~/.vibe/plugins/gausvibe/mcp.json.bak-classpath`.
