# Bake-off 2: Head-to-Head Agent Bake-Off — Full Protocol

## Concept

Two agent sessions, two identical checkouts of the same Java repo, same change
request. Tab A works with normal tools (read/grep/edit). Tab B gets the same
task plus GausVibe (build graph once, query it, then read only what it needs
to edit). Compare what each session spent and what it produced.

This is the realistic version of bake-off 1: it measures what we actually care
about — whether a pre-built code graph reduces the tokens, tool calls, and
time an agent needs to make a correct change — using real agent behavior
instead of simulated baselines.

```
             ┌──────────────────────────────┐
   Task ────►│ Tab A: petclinic-control      │──► diff + tokens + time + score
             │ Standard tools (grep/read)   │
             └──────────────────────────────┘
             ┌──────────────────────────────┐
   Task ────►│ Tab B: petclinic-gausvibe    │──► diff + tokens + time + score
             │ Same prompt + GausVibe CLI   │
             └──────────────────────────────┘
```

## Why this beats bake-off 1

| Bake-off 1 flaw | Fixed by |
|---|---|
| Toy 60-LOC calculator app | Real OSS project (~10k LOC) |
| Simulated "paste everything" baseline | Real agentic baseline (the actual competitor) |
| chars/4 token estimate | Exact tokens from `/status` in each session |
| Structural trivia questions | "Make this change" — action tasks |
| No success criteria | Build + tests must pass; diff graded against a rubric |
| Single run | 2–3 repeats per task; graph reuse measured separately |

---

## Part 1: Setup (once) — complete script

The script below does everything: clones both tabs, pins them to the same
PetClinic commit, verifies the test suite passes on both, builds GausVibe and
its dependency classpath, installs the `gv` wrapper, builds the graph, and
writes `~/bakeoff2/results/environment.md` with the actual recorded values.

It is safe to re-run: existing clones are fetched and re-pinned instead of
re-cloned. To move to a newer PetClinic later, change `PETCLINIC_SHA` and
re-run (then correct Part 3's file map if the layout changed).

The pinned SHA is PetClinic `main` HEAD as of 2026-08-26:

```
818c4136ea971c21674525f9053de0d9c7ad8cfe
```

```bash
#!/bin/bash
# Bake-off 2 setup — clones, pins, verifies, builds graph, records environment.
# Safe to re-run: existing clones are reused and re-pinned, not re-cloned.

set -euo pipefail

# ---- Configuration -----------------------------------------------------
PETCLINIC_SHA="818c4136ea971c21674525f9053de0d9c7ad8cfe"   # main HEAD 2026-08-26
PETCLINIC_URL="https://github.com/spring-projects/spring-petclinic.git"
BASE_DIR="$HOME/bakeoff2"
CONTROL_DIR="$BASE_DIR/petclinic-control"    # tab A
GV_CLONE_DIR="$BASE_DIR/petclinic-gausvibe" # tab B
RESULTS_DIR="$BASE_DIR/results"
GV_HOME="/Users/magnusfind/Documents/find-shadow-model/gausvibe"

echo "=== Bake-off 2 setup ==="
mkdir -p "$RESULTS_DIR" "$BASE_DIR/bin"

if [ ! -d "$GV_HOME" ]; then
    echo "ERROR: GausVibe not found at $GV_HOME" >&2
    exit 1
fi

# ---- 1. Clone or fetch, pin both tabs to the same SHA -------------------
clone_and_pin() {
    local dir="$1"
    if [ ! -d "$dir/.git" ]; then
        echo "Cloning PetClinic into $dir ..."
        git clone "$PETCLINIC_URL" "$dir"
    else
        echo "Updating existing clone $dir ..."
        git -C "$dir" fetch origin main
    fi
    git -C "$dir" checkout --detach "$PETCLINIC_SHA"
    git -C "$dir" clean -fdq

    local sha
    sha=$(git -C "$dir" rev-parse HEAD)
    if [ "$sha" != "$PETCLINIC_SHA" ]; then
        echo "ERROR: $dir is at $sha, expected $PETCLINIC_SHA" >&2
        exit 1
    fi
    echo "  $dir @ $sha"
}

echo ""
echo "Step 1: clones (pinned at $PETCLINIC_SHA)"
clone_and_pin "$CONTROL_DIR"
clone_and_pin "$GV_CLONE_DIR"

# ---- 2. Verify both clones pass tests ------------------------------------
echo ""
echo "Step 2: test builds (first run downloads Maven deps; be patient)"
for d in "$CONTROL_DIR" "$GV_CLONE_DIR"; do
    echo "  Building $(basename "$d") ..."
    (cd "$d" && ./mvnw -q test) || {
        echo "ERROR: tests failed in $d — fix the environment before running the bake-off" >&2
        exit 1
    }
    echo "  $(basename "$d"): tests pass"
done

# ---- 3. Build GausVibe + dependency classpath ----------------------------
echo ""
echo "Step 3: GausVibe build and classpath"
(cd "$GV_HOME" && mvn -q compile)
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
    mvn -q -f "$GV_HOME/pom.xml" dependency:build-classpath \
    -Dmdep.outputFile=/tmp/classpath.txt
echo "  classpath written to /tmp/classpath.txt"

# ---- 4. Install the gv wrapper (used by tab B) ---------------------------
echo ""
echo "Step 4: installing gv wrapper"
cat > "$BASE_DIR/bin/gv" << 'EOF'
#!/bin/bash
# GausVibe CLI wrapper for the bake-off
GV_HOME="/Users/magnusfind/Documents/find-shadow-model/gausvibe"
exec java -cp "$GV_HOME/target/classes:$(cat /tmp/classpath.txt)" dk.gausdalfind.Main "$@"
EOF
chmod +x "$BASE_DIR/bin/gv"

# ---- 5. Build the graph and smoke-test it --------------------------------
echo ""
echo "Step 5: building the graph"
"$BASE_DIR/bin/gv" build --project "$GV_CLONE_DIR" \
    --output "$BASE_DIR/graph.json" --serialize

echo "  Smoke test: query class:all"
"$BASE_DIR/bin/gv" query --graph "$BASE_DIR/graph.json" "class:all" | head -5

# ---- 6. Write the environment record ------------------------------------
echo ""
echo "Step 6: writing $RESULTS_DIR/environment.md"
cat > "$RESULTS_DIR/environment.md" << EOF
# Environment record — generated by setup script

Generated:                       $(date)
PetClinic SHA (both clones):     $PETCLINIC_SHA
GausVibe SHA:                    $(git -C "$GV_HOME" rev-parse HEAD)
Java:                            $(java -version 2>&1 | head -1)
Vibe version:                    $(vibe --version 2>/dev/null || echo "not on PATH — record by hand")
Graph file:                      $BASE_DIR/graph.json
Graph size:                      $(wc -c < "$BASE_DIR/graph.json" | tr -d ' ') bytes (~$(($(wc -c < "$BASE_DIR/graph.json") / 4)) tokens est.)
Classpath:                       /tmp/classpath.txt (regenerate after reboot — see bake-off-2.md Part 1)

Model (both tabs):               FILL IN from /model
Thinking level (both tabs):      FILL IN from /thinking
Agent profile:                   FILL IN (default: accept-edits)
EOF

echo ""
echo "=== Setup complete ==="
echo "Tab A (control):   vibe --workdir $CONTROL_DIR"
echo "Tab B (GausVibe):  vibe --workdir $GV_CLONE_DIR"
echo "Graph:             $BASE_DIR/graph.json"
echo "Environment record: $RESULTS_DIR/environment.md  (fill in model/thinking after opening both tabs)"
```

Notes on the script:

- **`--detach` pinning:** both clones are checked out as a detached HEAD at
  the SHA so nothing that happens upstream on PetClinic `main` can drift into
  the experiment mid-run.
- **Classpath caveat:** `/tmp/classpath.txt` is wiped on reboot. If tab B's
  `gv` starts failing with ClassNotFound errors, re-run step 3 of the script
  (or the whole script — it is idempotent and skips the long parts).
- **`git clean -fdq` on re-run:** it discards uncommitted changes in the
  clones. That is the same reset the per-session protocol (Part 4, step 1)
  performs anyway — archive diffs to `~/bakeoff2/results/` before re-running.
- The graph lives outside the clones (`~/bakeoff2/graph.json`) so clone
  resets and re-pins never delete it.

### Post-setup: verify the grading file map

The ground-truth file lists in Part 3 reflect PetClinic's layout at the pinned
SHA. Spend two minutes confirming them right after setup:

```bash
ls ~/bakeoff2/petclinic-control/src/main/java/org/springframework/samples/petclinic/model/ \
   ~/bakeoff2/petclinic-control/src/main/java/org/springframework/samples/petclinic/owner/ \
   ~/bakeoff2/petclinic-control/src/main/resources/templates/owners/ \
   ~/bakeoff2/petclinic-control/src/main/resources/templates/pets/ \
   ~/bakeoff2/petclinic-control/src/main/resources/templates/visits/
```

If anything has moved, correct Part 3 before running — the rubric references
these paths.

---

## Part 2: The Tab B preamble

Every tab B prompt = the preamble below + the task prompt, verbatim, as one
message. Tab A gets the task prompt alone.

```
You have the GausVibe code-graph CLI available as the command ~/bakeoff2/bin/gv.
Use it to locate code instead of searching with grep/find. It indexes this
Java repository as a graph of classes and methods.

- The graph already exists at ~/bakeoff2/graph.json — do not rebuild it unless
  that file is missing. To rebuild (only if missing):
  ~/bakeoff2/bin/gv build --project . --output ~/bakeoff2/graph.json --serialize

- To query it:
  ~/bakeoff2/bin/gv query --graph ~/bakeoff2/graph.json "<query>"

- Example queries:
  "class:all"
  "method:all"
  "method:class:<fully.qualified.ClassName>"
  "class:implementations:<fully.qualified.InterfaceName>"
  "class:subclasses:<fully.qualified.ClassName>"

Use the graph to decide which files matter, then read only the files you need
to edit. Do not bulk-grep the codebase.
```

---

## Part 3: Tasks, ground truth, and rubrics

Each task = one fresh session per tab. Scoring per task: build+tests (2 pts),
functional correctness (2 pts), minimal diff (1 pt) — max 5. Record the score
fraction and which points were lost.

### Task 1: Add a field to an entity (touches model + persistence + view + form)

Prompt (both tabs):

```
Make this change to this repo:

Add a "weight" field (type double) to Pet. Persist it in the database,
include it in the pet creation and edit forms with validation (required,
must be positive), and display it on the owner and pet details pages.

Requirements:
- ./mvnw -q test must pass when you are done
- Make the minimal change set that satisfies the requirement
- When finished, summarize which files you changed and why
```

Tab B: prepend the Part 2 preamble.

**Ground truth — files a complete solution touches** (verify per 1.8):

| Layer | Expected file |
|---|---|
| Entity | `src/main/java/org/springframework/samples/petclinic/model/Pet.java` — add `weight` field + getter/setter + `@NotNull @Positive` constraints |
| Schema | `src/main/resources/db/*/schema.sql` — add `weight` column to `pets` table (every DB dialect present) |
| Data (optional) | `src/main/resources/db/*/data.sql` — only if seed rows need values |
| View | `src/main/resources/templates/pets/createOrUpdatePetForm.html` — input field for weight |
| View | `src/main/resources/templates/owners/ownerDetails.html` — display weight in the pet record list |
| Messages (optional) | `src/main/resources/messages/messages.properties` — only if adding a custom validation message |

**Rubric:**
- 2 pts — `./mvnw -q test` passes
- 2 pts — field round-trips to DB, form accepts 4.5, form rejects missing and
  -1, details page shows it
- 1 pt — diff touches only the layers above; no drive-by refactors

### Task 2: New repository query + controller path

Prompt (both tabs):

```
Make this change to this repo:

Add the ability to search owners by city. Add a findByCity method to the
owner repository and wire it into the owners list page so that passing
?city=<name> as a query parameter filters the owner list by that city.
With no parameter, the page behaves exactly as before.

Requirements:
- ./mvnw -q test must pass when you are done
- Make the minimal change set that satisfies the requirement
- When finished, summarize which files you changed and why
```

**Ground truth:**

| Layer | Expected file |
|---|---|
| Repository interface | `.../owner/OwnerRepository.java` — add `Collection<Owner> findByCity(String city)` |
| JPA impl | `.../owner/JpaOwnerRepository.java` — implement with a JPQL or derived query |
| Controller | `.../owner/OwnerController.java` — list endpoint accepts optional `city` param, calls the new method when present |
| View | `templates/owners/ownersList.html` — usually no change needed; only touch if filtering requires it |

**Rubric:**
- 2 pts — tests pass
- 2 pts — `?city=` filters correctly; absent param yields the full list
  exactly as before
- 1 pt — no change to unrelated search behavior (lastName search intact)

### Task 3: Cross-cutting validation

Prompt (both tabs):

```
Make this change to this repo:

Reject any Visit whose date is in the future. Validate on the visit form
path and show an error message styled consistently with the existing
validation errors in this application.

Requirements:
- ./mvnw -q test must pass when you are done
- Make the minimal change set that satisfies the requirement
- When finished, summarize which files you changed and why
```

**Ground truth:**

| Layer | Expected file |
|---|---|
| Form path | `.../owner/VisitController.java` — reject future dates (e.g. `@PastOrPresent` on the field, or a check in the controller init/process path) |
| Entity (alternative) | `.../model/Visit.java` — bean-validation annotation is an acceptable variant |
| Messages | `messages/messages.properties` — error text consistent with existing keys |
| View | usually none; error rendering follows the template's existing `th:errors` blocks |

**Rubric:**
- 2 pts — tests pass
- 2 pts — a future visit date is rejected with a visible, correctly styled
  error; past dates still work
- 1 pt — validation lives in one coherent place (not duplicated across
  controller and entity in a way that fights itself)

### Task 4: Project-wide rename (impact analysis — the graph's sweet spot)

Run this task last in each cycle: it renames an accessor that the graph
indexes, so afterwards the graph is stale (see Part 6, threat 6).

Prompt (both tabs):

```
Make this change to this repo:

Rename PetType.getName() to PetType.getLabel() everywhere in the codebase,
updating every caller so the codebase compiles. Do not rename getName()
methods on unrelated classes.

Requirements:
- ./mvnw -q test must pass when you are done
- Make the minimal change set that satisfies the requirement
- When finished, summarize which files you changed and why
```

**Ground truth — callers of `PetType.getName()`:** at minimum
`.../owner/PetValidator.java` (or wherever pet type comparison happens),
`PetController`/`OwnerController` view-model paths, and any template
referencing the type name. The definitive check is the compiler plus:

```bash
grep -rn "\.getName()" --include="*.java" | grep -i pettype
grep -rn "getName" src/main/resources/templates/
```

**Rubric:**
- 2 pts — tests pass (this is the real caller-coverage check: the suite's
  MockMvc tests touch the type-name rendering paths)
- 2 pts — no remaining `PetType` references to the old accessor (grep above
  is clean for PetType)
- 1 pt — unrelated `getName()` methods (Owner, Vet, Pet, etc.) untouched

### Task 5 (tab B only): Graph amortization

Repeat Tasks 1–3 in tab B with fresh sessions, **without rebuilding the
graph**. Two ways to run it — pick one and note it in the results:

- **Sequential (recommended):** after the Tasks 1–4 cycle, reset tab B's
  clone and run Tasks 1–3 again as Task 5a/5b/5c. The graph now describes
  the original code, not the current tree — which is exactly the stale-graph
  scenario.
- **Interleaved:** run each task twice back-to-back (first run builds
  understanding, second run measures reuse) — noisier, skip unless you want
  per-task reuse data specifically.

Compare each Task 5 run's tokens against tab A's cost for the same task to get
the amortized advantage per task.

---

## Part 4: Per-session protocol (checklist)

Run this exact sequence for every task, in both tabs.

1. **Reset the clone** so every task starts from a clean tree:

   ```bash
   cd ~/bakeoff2/petclinic-control && git checkout . && git clean -fd
   cd ~/bakeoff2/petclinic-gausvibe && git checkout . && git clean -fd
   ```

   Keep `~/bakeoff2/graph.json` — it is outside the clones on purpose.
   Optionally stash the previous task's diff is already archived (step 7),
   so `git checkout .` only discards what you chose to discard.

2. **Start a fresh session in each tab** — never continue an old session
   (`/continue` carries prior context and taints the token count):

   ```bash
   vibe --workdir ~/bakeoff2/petclinic-control    # tab A
   vibe --workdir ~/bakeoff2/petclinic-gausvibe   # tab B
   ```

3. **Confirm the environment matches**: `/model` in both tabs shows the same
   model; `/thinking` shows the same level. Write both into the run sheet.

4. **Paste the prompt.** Tab B gets preamble + task text as one message.

5. **Let it run to completion. No mid-session steering** — the agent finding
   things on its own is the capability being measured. If it asks a
   clarifying question, answer only with information already implied by the
   prompt; note that you did.

6. **Record usage before closing the tab:**
   - `/status` — total input tokens, output tokens, tool calls, duration
   - `/log` — the session log path under `~/.vibe/logs/session/` (keep it in
     the run sheet; transcripts are audited in step 9 and used for the
     files-read analysis)

7. **Verify and archive the result:**

   ```bash
   cd ~/bakeoff2/petclinic-control   # or the gausvibe clone
   ./mvnw -q test
   git diff --stat > ~/bakeoff2/results/task<N>-<control|gausvibe>-run<K>.stat
   git diff          > ~/bakeoff2/results/task<N>-<control|gausvibe>-run<K>.diff
   ```

8. **Grade against the task's rubric** (0–5) and fill the run sheet before
   resetting. A failed build still scores 0 on the build criterion but keep
   the token data — failed-run cost is itself a finding.

9. **Audit the tab B transcript**: confirm `gv query` calls appear. Sessions
   that ignored the tool and grepped anyway are mislabeled control runs —
   do not count them as GausVibe results (but note them; tool-adoption rate
   is a real result too).

### Run sheet (copy per session)

```markdown
## Task <N> — tab <A|B> — run <K>
Date/time started:
Model:                Thinking:
Session log path:     (/log)
Graph rebuilt this session? (tab B):  Y/N
Prompt exactly as specified?         Y/N
Steering given?                      none / <describe>
---
Input tokens:         Output tokens:
Tool calls:            Tool kinds used (grep/read/bash/gv/edit counts):
Duration:
---
./mvnw -q test:       PASS / FAIL
Score:                _/5   (build _ / function _ / minimal _)
Files changed:        (from .stat)
GV queries issued:    (tab B, count + list)
Notable behavior:
```

### Failure handling

- **Session can't finish / gives up:** record tokens used so far, score 0 for
  unfinished work, mark "abandoned" in notes. Do not retry in the same
  session — a fresh retry is a new run and both attempts get rows.
- **Tests fail at verification:** score the build criterion 0, function 0,
  still award minimality if the diff itself is sane. Keep the row.
- **`gv` command fails in tab B** (e.g. classpath wiped by reboot): fix the
  environment, reset, and re-run the session; mark the aborted attempt as
  invalid in notes but keep it out of the averages.
- **Both tabs fail the same task:** the task may be too hard for the model or
  underspecified. Note it, fix the prompt, and re-run both tabs — never
  re-run just one tab.

---

## Part 5: Results and analysis

### Results table

| Task | Tab | Model | In tok | Out tok | Tool calls | Time | Tests | Score | Files read | GV used | Notes |
|------|-----|-------|--------|---------|------------|------|-------|-------|------------|---------|-------|
| 1 | A control | | | | | | Y/N | /5 | | n/a | |
| 1 | B GausVibe | | | | | | Y/N | /5 | | Y/N | |

Per task, compute:

- Token delta: (B − A) tokens and % change
- Tool-call delta — grep-heavy exploration vs. query + targeted reads
- Files read per session (from the transcript) — did tab B read only its
  edit targets while tab A read broadly?
- For Task 5: marginal cost per tab B task vs. flat cost per tab A task

### Graph-build accounting

State up front — the recommendation: **charge the one-time graph build to
tab B's Task 1 only**, then report Task 5 as the amortized view. Also record
the graph JSON size separately; it is the fixed cost number people will ask
about.

### Cost math (optional)

If model prices are configured in `~/.vibe/config.toml` (`input_price`,
`output_price` per million tokens), compute:

```
cost = (input_tokens  / 1e6) * input_price
     + (output_tokens / 1e6) * output_price
```

Use the same prices for both tabs (they are the same model). Skip this
section entirely if prices are not configured — token counts alone are the
comparable quantity.

### Token-counting caveats

- **Cached input tokens**: if `/status` breaks out cached vs. uncached input,
  record all three numbers. Cached tokens are cheaper but not free; keep the
  comparison on total tokens and note the cache split.
- **Compaction**: if a session runs `/compact` (manual or automatic), the
  token totals still reflect what was actually spent — but the transcript
  gets a summary boundary. Note any compaction in the run sheet; sessions
  that compacted are not directly comparable to ones that did not.
- **Subagents**: a session may delegate exploration to a subagent. Include
  those tokens (they are part of the session's cost) and note it.

### Repeats

Agents are stochastic; a single run is an anecdote. For headline numbers, run
each task 2–3 times per tab (fresh sessions, reset between) and report the
mean and spread. Hands-off repeats:

```bash
vibe -p "<task prompt verbatim>" --workdir ~/bakeoff2/petclinic-control --auto-approve
vibe -p "<preamble + task prompt verbatim>" --workdir ~/bakeoff2/petclinic-gausvibe --auto-approve
```

Programmatic mode uses the same default agent and auto-approves all tools, so
it is fair to both tabs; the usage summary prints at the end. Note that
programmatic runs cannot be steered and record their own logs in the same
session directory.

---

## Part 6: Threats to validity (keep these controlled)

1. **Same model, same thinking level, same agent profile in both tabs.**
   Record all three; never compare sessions run on different models.
2. **Fresh sessions only** — prior context invalidates the token count.
3. **No steering** — no hints, no corrections mid-run.
4. **Identical prompts** — task text byte-identical across tabs; only the
   preamble differs.
5. **Tab B must actually use GausVibe** — audit transcripts (Part 4, step 9).
   Track the adoption rate: how often the agent chooses the graph over grep
   when told to.
6. **Graph staleness** — the graph is built once. Task 4's rename invalidates
   the `PetType` nodes it indexes. Either run Task 4 last in each cycle
   (recommended) or rebuild the graph after it and note the rebuild cost
   separately.
7. **Session variance** — 2–3 repeats for any number you intend to quote;
   report means, not best runs.
8. **Order effects across tasks** — sessions are fresh, but the human grading
   gets faster at recognizing correct diffs; grade with the rubric, not from
   memory of previous runs.

---

## Appendix A: GausVibe query cheat sheet (PetClinic FQCNs)

For tab B sessions — expected high-value queries per task. (The agent decides
its own queries; this list is for auditing transcripts and grading whether
the graph was used well.)

Base package: `org.springframework.samples.petclinic`

| Task | Useful queries |
|---|---|
| 1 (Pet.weight) | `method:class:org.springframework.samples.petclinic.model.Pet` — enumerate Pet's members before editing; `class:subclasses:...model.NamedEntity` — what Pet inherits |
| 2 (findByCity) | `class:implementations:org.springframework.samples.petclinic.owner.OwnerRepository` — find the JPA impl to edit; `method:class:...owner.OwnerController` — find the list endpoint |
| 3 (Visit validation) | `method:class:...owner.VisitController` — find the form-processing methods; `method:class:...model.Visit` — the entity's fields |
| 4 (rename) | `method:class:...model.PetType` — confirm the accessor; `class:implementations`/`class:subclasses` around PetType and Pet to bound the caller set |

## Appendix B: Task order per cycle

```
Task 1 → Task 2 → Task 3 → Task 4 → (tab B only) Task 5a/5b/5c
         reset clones between tasks; graph persists;
         Task 4 last because it stales the graph
```

A full cycle is 8 sessions (4 tasks x 2 tabs) + 3 amortization sessions = 11.
At 2 repeats per tab that is ~19-22 sessions — doable over a few days; keep
the environment record stable for the whole period.
