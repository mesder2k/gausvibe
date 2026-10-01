# GausVibe Vibe Integration - Java-First Approach

## Core Principle

**GausVibe is Java. The integration should be Java.**

No Python wrappers. No external dependencies. The GausVibe REST server (`dk.gausdalfind.server.GausVibeServer`) is pure Java and handles everything.

---

## Part 1: Pure Java Server (Already Working)

### Server Location
`src/main/java/dk/gausdalfind/server/GausVibeServer.java`

### Start It
```bash
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe
./scripts/build-artifact.sh
java -jar target/gausvibe-<version>-all.jar server \
  --project ../bakeoff1 --port 8080
```

### Test It
```bash
curl http://localhost:8080/classes
# Returns: {"count":3,"classes":["com.example.calculator.Calculator",...]}

curl http://localhost:8080/classes/com.example.calculator.Calculator/implementations
# Returns: {"interface":"...","count":1,"implementations":["...StandardCalculator"]}
```

**If this doesn't work, fix GausVibe first. The Vibe integration won't work either.**

---

## Part 2: Minimal Vibe Integration (One Case)

### Goal
When user asks: **"List all classes that implement Calculator"**
Vibe should automatically call GausVibe and return the answer.

### Solution: Single-Skill Approach

Create ONE file: `~/.vibe/plugins/gausvibe/skills/gausvibe-query/SKILL.md`

```markdown
---
name: gausvibe-query
description: Route Java "implement" questions to GausVibe server
user-invocable: false
allowed-tools:
  - file_system.bash
---

# GausVibe Minimal Integration

## WHEN TO ACTIVATE

Only for this ONE pattern:
- User asks about "classes that implement [ClassName]"
- User asks about "implementations of [ClassName]"

## DETECTION

If user message contains:
- "implement" OR "implementations"
- AND "Calculator" (or any class name)

Then use GausVibe.

## EXECUTION

```typescript
// 1. Extract class name from user question
//    "List all classes that implement Calculator" -> "Calculator"
//    "What implements Runnable" -> "Runnable"

// 2. Start server if not running (check port 8080)
const serverCheck = await tools.file_system.bash({
  command: "curl -s http://localhost:8080/ > /dev/null 2>&1 && echo RUNNING || echo STOPPED"
});

if (serverCheck.stdout.includes("STOPPED")) {
  // Start server in background
  await tools.file_system.bash({
    command: "nohup java -jar ~/.vibe/plugins/gausvibe/gausvibe.jar server " +
             "--project ../bakeoff1 --port 8080 > /tmp/gausvibe.log 2>&1 &"
  });
  await tools.self.sleep({ seconds: 3 });
}

// 3. Extract class name from question
const className = extractClassName(userQuestion);
// For "List all classes that implement Calculator" -> "Calculator"
// For "What implements java.util.List" -> "java.util.List"

// 4. Build FQN (try to guess package)
// If user says just "Calculator", try common packages
const fqn = tryResolveFQN(className);

// 5. Query GausVibe server
const result = await tools.file_system.bash({
  command: `curl -s http://localhost:8080/classes/${encodeURIComponent(fqn)}/implementations`
});

// 6. Parse and return
const data = JSON.parse(result.stdout);
if (data.count === 0) {
  return "No implementations found for " + fqn;
}
return "Classes implementing " + fqn + ": " + data.implementations.join(", ");
```

## HELPER: extractClassName

```typescript
function extractClassName(question: string): string {
  // Look for "implement" patterns
  const match = question.match(/(?:implement(s|ation)?\s+(of\s+)?)(.+?)(?:\s|$|\?|\.)/i);
  if (match) {
    return match[2].trim();
  }
  // Look for class names (capitalized words)
  const words = question.split(/\s+/);
  for (const word of words) {
    if (word === word.charAt(0).toUpperCase() + word.slice(1) && 
        !['List', 'All', 'The', 'What', 'Find', 'Show'].includes(word)) {
      return word;
    }
  }
  return null;
}
```

## HELPER: tryResolveFQN

```typescript
function tryResolveFQN(className: string): string {
  // If already FQN (has dots)
  if (className.includes('.')) {
    return className;
  }
  
  // Common packages to try
  const packages = [
    'com.example.calculator',
    'com.example',
    'java.lang',
    'java.util'
  ];
  
  // Try each package
  for (const pkg of packages) {
    const fqn = pkg + '.' + className;
    const check = await tools.file_system.bash({
      command: `curl -s http://localhost:8080/classes/${encodeURIComponent(fqn)} | grep -q "\"fqn\""`
    });
    if (check.returncode === 0) {
      return fqn;
    }
  }
  
  // Fallback to just the class name
  return className;
}
```

## COMPLETE EXAMPLE SESSION

**User:** "List all classes that implement Calculator"

**Vibe detects:**
- Contains "implement"
- Contains "Calculator"
- Pattern matches "implementations of X"

**Vibe executes:**
```typescript
const className = extractClassName("List all classes that implement Calculator");
// className = "Calculator"

const fqn = tryResolveFQN("Calculator");
// Tries: com.example.calculator.Calculator -> exists!

const result = await tools.file_system.bash({
  command: "curl -s http://localhost:8080/classes/com.example.calculator.Calculator/implementations"
});
// result = {"interface":"com.example.calculator.Calculator","count":1,"implementations":["com.example.calculator.StandardCalculator"]}

return "Classes implementing com.example.calculator.Calculator: com.example.calculator.StandardCalculator";
```

**User sees:**
> Classes implementing com.example.calculator.Calculator: com.example.calculator.StandardCalculator

## SETUP STEPS

### 1. Create plugin directory
```bash
mkdir -p ~/.vibe/plugins/gausvibe/skills/gausvibe-query
```

### 2. Create SKILL.md
Put the content above into:
`~/.vibe/plugins/gausvibe/skills/gausvibe-query/SKILL.md`

### 3. Create plugin.json
`~/.vibe/plugins/gausvibe/plugin.json`:
```json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "gausvibe",
  "version": "1.0.0",
  "description": "GausVibe Java code graph integration"
}
```

### 4. Reload Vibe
In Vibe, type:
```
/reload
```

### 5. Test
Ask Vibe: **"List all classes that implement Calculator"**

---

## Summary

| Component | Location | Purpose |
|-----------|----------|---------|
| Java Server | `gausvibe/src/main/java/dk/gausdalfind/server/` | REST API for queries |
| Vibe Plugin | `~/.vibe/plugins/gausvibe/` | Tells Vibe when to use GausVibe |
| Vibe Skill | `~/.vibe/plugins/gausvibe/skills/gausvibe-query/SKILL.md` | Pattern matching for one case |

**Minimal setup for one case:** Just the Skill file. Everything else is already in Java.
