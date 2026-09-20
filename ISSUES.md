# GausVibe Issues Tracker

This document tracks issues encountered during development and implementation of optimizations.

---

## Open Issues

### Phase 5: Closed-Loop Validation

#### Issue #1: Cannot compile GausVibe without Maven
- **Date**: 2026-09-20
- **Severity**: HIGH
- **Description**: Maven (`mvn`) is not installed on the system, preventing compilation and testing of self-editing capability
- **Impact**: Cannot validate end-to-end self-editing workflow (parse → query → edit → serialize → compile)
- **Workaround**: Manual compilation with `javac` using classpath from ~/.m2/repository
- **Solution**: Install Maven or use alternative build approach
- **Status**: BLOCKER for Phase 5 validation

#### Issue #2: Round-trip compilation not verified
- **Date**: 2026-09-20
- **Severity**: HIGH
- **Description**: Self-editing round-trip produces source files but compilation cannot be verified without Maven
- **Impact**: Cannot confirm modified GausVibe actually works
- **Components involved**:
  - `JavaParserConfig` - parsing ✅
  - `GraphQueryEngine` - querying ✅
  - `EditCommand` - editing ✅
  - `ASTSourceSerializer` - serialization ✅
  - Compilation verification ❌ BLOCKED
- **Workaround**: Tests created that validate all components except compilation
- **Solution**: Install Maven and run: `mvn test -Dtest=SelfEditRoundTripTest`
- **Status**: BLOCKED by ISSUES.md #1 (Maven not installed)
- **Tests available**: See `SelfEditRoundTripTest.java` and `SelfEditValidationTest.java`

---

## Resolved Issues

### Compilation Errors (Phase 0)

#### Issue #1: CommandLineInterface.java line 707 - Unescaped single quotes in JSON
- **Date**: 2026-09-20
- **Severity**: HIGH
- **Description**: JSON example used single quotes which are invalid in Java string literals
- **Fix**: Replaced single quotes with properly escaped double quotes
- **File**: `src/main/java/dk/gausdalfind/cli/CommandLineInterface.java:707`
- **Commit**: e4544f5
- **Status**: ✅ RESOLVED

#### Issue #2: CommandLineInterface.java lines 751, 785, 815, 829, 844, 850, 866 - Double-quote escaping
- **Date**: 2026-09-20
- **Severity**: HIGH
- **Description**: Strings had unescaped double quotes (`"Classes (""` instead of `"Classes (\""`)
- **Fix**: Added proper escaping for all double quotes in format strings
- **Files**: `src/main/java/dk/gausdalfind/cli/CommandLineInterface.java`
- **Commit**: e4544f5
- **Status**: ✅ RESOLVED

#### Issue #3: EditCommand.java line 274 - Illegal escape characters in regex
- **Date**: 2026-09-20
- **Severity**: HIGH
- **Description**: Regex pattern had invalid Java escape sequences (`\{` and `\}`)
- **Fix**: Simplified regex to `][{]+` which doesn't require special escaping
- **File**: `src/main/java/dk/gausdalfind/cli/EditCommand.java:274`
- **Commit**: e4544f5
- **Status**: ✅ RESOLVED

---

## Known Limitations

1. **Build System Dependency**: GausVibe requires Maven for compilation. Without Maven, full integration testing is difficult.
2. **Self-Editing Not Validated**: While components exist, end-to-end self-editing has not been proven.

---

## Issue Template

```
### Issue #X: [Title]
- **Date**: YYYY-MM-DD
- **Severity**: [HIGH/MEDIUM/LOW]
- **Description**: [Detailed description]
- **Impact**: [What this blocks or affects]
- **Components involved**: [List of files/classes]
- **Workaround**: [If any]
- **Solution**: [Proposed solution]
- **Status**: [TODO/IN PROGRESS/RESOLVED]
```

---

## Severity Levels

- **HIGH**: Blocks major functionality, prevents forward progress
- **MEDIUM**: Degrades functionality, has workarounds
- **LOW**: Minor issues, cosmetic or edge cases
