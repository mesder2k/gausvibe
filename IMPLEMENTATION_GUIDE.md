# GausVibe Implementation Guide: Token-Optimized Version

## 🎯 **Executive Summary**

This guide provides a **token-focused implementation plan** for GausVibe, prioritizing optimizations that **save LLM tokens** by replacing verbose shell operations with concise, structured graph queries.

**Key Metric**: Token savings > Performance improvements
- Current shell operations: **500-10,000+ tokens per query**
- Target GausVibe queries: **5-50 tokens per query**
- **Expected savings: 85-99%**

---

## 📊 **The Token Problem**

### Why Tokens Matter More Than Speed

| Cost Factor | Impact | Current | With GausVibe |
|-------------|--------|---------|---------------|
| Token usage | **HIGH** - Direct cost | 500-10,000+/query | 5-50/query |
| API latency | Medium | 1-10s | 0.1-1s |
| Compute cost | Low-Medium | Minimal | Minimal |

**Token costs dominate**: A single `grep -r` can cost **$0.50-$5.00** in API fees, while the compute time is negligible.

### Real-World Token Usage Comparison

**Scenario**: Find all usages of a method in a 1000-file project

| Approach | Input Tokens | Output Tokens | Total Tokens | Cost (GPT-4) |
|----------|--------------|---------------|--------------|---------------|
| Shell (`grep -r`) | 20 | 5000+ | 5020+ | $0.45+ |
| Shell (`find` + `xargs grep`) | 50 | 8000+ | 8050+ | $0.72+ |
| GausVibe (current) | 20 | 500 | 520 | $0.05 |
| **GausVibe (optimized)** | **20** | **10** | **30** | **$0.003** |

**Savings**: 99.4% cost reduction

---

## 🚀 **Implementation Plan Overview**

### **3 Phases, 4 Weeks, 95-99% Token Savings**

```
Phase 1 (Week 1): Quick Token Wins
├── Compact Query Responses (80-90% savings)
├── Smart Result Limiting (95-99% savings) 
├── Minimal Call Graph Queries (90-95% savings)
└── Integration & Testing
    
Phase 2 (Week 2): High-Impact Token Savings
├── Text Search Index (95%+ savings)
├── Structured File Content (80-90% savings)
└── Integration & Testing
    
Phase 3 (Weeks 3-4): Advanced Token Savings
├── Paginated Queries (98%+ savings)
├── Diff Queries (90%+ savings)
├── Query Result Caching (100% savings for repeats)
└── Integration & Testing
```

**Total Effort**: 4 weeks
**Token Savings**: 85-99%
**Cost Savings**: 85-99%

---

## 📋 **Detailed Implementation Plan**

### **PHASE 1: Quick Token Wins (Week 1)**

#### **Day 1: Compact Query Responses**

**Goal**: Reduce query response size by 80-90%

**Tasks**:
1. Add `compactMode` flag to `GraphQueryEngine` (default: `true`)
2. Create compact format methods for all node types:
   ```java
   // Compact class format
   private String formatClassCompact(ClassNode cls) {
       return cls.getQualifiedName();
   }
   
   // Compact class list
   private String formatClassListCompact(List<ClassNode> classes) {
       return String.join("\n", classes.stream()
           .map(ClassNode::getQualifiedName)
           .collect(Collectors.toList()));
   }
   ```
3. Update CLI to support `--compact` and `--verbose` flags
4. Add token counting to responses for monitoring

**Files to Modify**:
- `src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java`
- `src/main/java/dk/gausdalfind/cli/CommandLineInterface.java`

**Testing**:
- Verify compact mode returns minimal data
- Verify token count reduction ≥80%
- Verify backward compatibility

**Token Savings**: 80-90%

---

#### **Day 2: Smart Result Limiting**

**Goal**: Reduce result set size by 95-99%

**Tasks**:
1. Add `defaultLimit` to `GraphQueryEngine` (default: `10`)
2. Update all list-returning methods to support limits:
   ```java
   public List<ClassNode> findClassesByName(String name, int limit) {
       List<ClassNode> all = indexes.getClassesByName(name);
       return all.stream().limit(limit).collect(Collectors.toList());
   }
   ```
3. Add `--limit N` and `--show-all` CLI parameters
4. Add truncation message: "... and N more"

**Files to Modify**:
- `src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java`
- `src/main/java/dk/gausdalfind/cli/CommandLineInterface.java`
- `src/main/java/dk/gausdalfind/cli/QueryParser.java`

**Testing**:
- Verify default limit is 10
- Verify `--limit 20` works
- Verify `--show-all` returns all results
- Verify token count reduction ≥95%

**Token Savings**: 95-99%

---

#### **Day 3: Minimal Call Graph Queries**

**Goal**: Reduce call graph query responses by 90-95%

**Tasks**:
1. Add minimal versions of call graph methods:
   ```java
   public List<String> getCallersMinimal(MethodNode method) {
       return getCallers(method).stream()
           .map(m -> m.getQualifiedName())
           .collect(Collectors.toList());
   }
   ```
2. Add CLI support for minimal call graph queries
3. Update existing call graph queries to use minimal format by default

**Files to Modify**:
- `src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java`

**Testing**:
- Verify callers query returns only FQNs
- Verify one result per line
- Verify token count reduction ≥90%

**Token Savings**: 90-95%

---

#### **Days 4-5: Integration & Testing**

**Tasks**:
1. Write unit tests for compact mode
2. Write unit tests for result limiting
3. Write integration tests for CLI
4. Manual testing with real codebases
5. Document all changes

**Deliverables**:
- All Phase 1 features working
- All tests passing
- Documentation updated

---

### **PHASE 2: High-Impact Token Savings (Week 2)**

#### **Day 6: Text Search Index (Minimal Output)**

**Goal**: Replace `grep -r` with structured text search (95%+ savings)

**Tasks**:
1. Create `TextSearchIndex.java` class
2. Implement `build()` method to index all nodes
3. Implement `searchMinimal(String query)` to return only FQNs
4. Integrate with `GraphQueryEngine`
5. Add CLI command: `query:search:text:QUERY [--limit N]`

**Files to Create**:
- `src/main/java/dk/gausdalfind/queries/TextSearchIndex.java`

**Files to Modify**:
- `src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java`
- `src/main/java/dk/gausdalfind/cli/CommandLineInterface.java`
- `src/main/java/dk/gausdalfind/cli/QueryParser.java`

**Testing**:
- Verify text search returns only FQNs
- Verify search accuracy
- Verify token count reduction ≥95%

**Token Savings**: 95%+

**Example**:
```
# Before: grep -r "calculateTotal" src/ → 5000 tokens
# After: query:search:text:calculateTotal → 15 tokens
```

---

#### **Day 7: Structured File Content Queries**

**Goal**: Replace `cat file.java` with structured queries (80-90% savings)

**Tasks**:
1. Add `getFileStructured(Path file)` method
2. Add focus parameter support: `all`, `classes`, `methods`, `fields`, `imports`
3. Return only structure, not full file content
4. Add CLI command: `query:file:PATH [--focus FOCUS]`

**Files to Modify**:
- `src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java`
- `src/main/java/dk/gausdalfind/cli/CommandLineInterface.java`

**Testing**:
- Verify structured output doesn't include method bodies
- Verify focus parameter works
- Verify token count reduction ≥80%

**Token Savings**: 80-90%

**Example**:
```
# Before: cat Calculator.java → 2000 tokens
# After: query:file:Calculator.java → 100 tokens
```

---

#### **Days 8-10: Integration & Testing**

**Tasks**:
1. Write unit tests for TextSearchIndex
2. Write unit tests for structured file content
3. Write integration tests for CLI
4. Performance benchmarking
5. Documentation

---

### **PHASE 3: Advanced Token Savings (Weeks 3-4)**

#### **Days 11-12: Paginated Queries**

**Goal**: Enable large result sets with minimal token usage (98%+ savings)

**Tasks**:
1. Create `PaginatedResult<T>` class
2. Add pagination support to query methods
3. Add CLI support: `--limit N`, `--cursor CURSOR`
4. Add "Next page" hint to output
5. Write tests

**Files to Create**:
- `src/main/java/dk/gausdalfind/queries/PaginatedResult.java`

**Files to Modify**:
- `src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java`
- `src/main/java/dk/gausdalfind/cli/CommandLineInterface.java`

**Token Savings**: 98%+

---

#### **Days 13-14: Diff Queries**

**Goal**: Replace `git diff` with structured diffs (90%+ savings)

**Tasks**:
1. Add file timestamp tracking
2. Add `getChangesSince(Path file, long timestamp)` method
3. Return structured diff (added, removed, modified)
4. Add CLI command: `query:diff:FILE [--since TIMESTAMP]`
5. Write tests

**Files to Modify**:
- `src/main/java/dk/gausdalfind/graph/GausVibeBuilder.java`
- `src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java`
- `src/main/java/dk/gausdalfind/cli/CommandLineInterface.java`

**Token Savings**: 90%+

---

#### **Day 15: Query Result Caching**

**Goal**: Cache query results to avoid re-running queries (100% savings for repeats)

**Tasks**:
1. Add query result cache to `GraphQueryEngine`
2. Cache key: query type + parameters hash
3. Cache value: result + timestamp
4. Add TTL (default: 5 minutes)
5. Add `--no-cache` flag
6. Write tests

**Files to Modify**:
- `src/main/java/dk/gausdalfind/queries/GraphQueryEngine.java`

**Token Savings**: 100% for repeated queries

---

#### **Days 16-20: Integration & Final Testing**

**Tasks**:
1. Write comprehensive unit tests
2. Write integration tests
3. Performance benchmarking
4. Documentation
5. Final validation

---

## 📊 **Token Savings by Phase**

### **Phase 1: Quick Wins (Week 1)**

| Operation | Before | After | Savings | Status |
|-----------|--------|-------|---------|--------|
| File discovery | 500-2000 | 10-50 | 90-99% | ✅ + Implemented |
| Method lookup | 200-500 | 10-20 | 90-95% | ⏳ |
| Class lookup | 200-500 | 10-20 | 90-95% | ⏳ |
| Callers/Callees | 500-2000 | 10-50 | 90-95% | ⏳ |
| List queries | 1000-5000 | 10-100 | 95-99% | ⏳ |

**Phase 1 Total Savings**: 80-95%

---

### **Phase 2: High-Impact (Week 2)**

| Operation | Before | After | Savings | Status |
|-----------|--------|-------|---------|--------|
| Text search | 1000-5000 | 10-50 | 95-99% | ⏳ |
| File content | 1000-10000 | 50-200 | 80-95% | ⏳ |
| Code search | 2000-10000 | 10-100 | 95-99% | ⏳ |

**Phase 2 Total Savings**: 80-95% (cumulative: 90-98%)

---

### **Phase 3: Advanced (Weeks 3-4)**

| Operation | Before | After | Savings | Status |
|-----------|--------|-------|---------|--------|
| Large result sets | 5000-50000 | 10-100 | 98%+ | ⏳ |
| Diffs | 500-5000 | 20-100 | 90-99% | ⏳ |
| Repeated queries | Varies | 0 | 100% | ⏳ |

**Phase 3 Total Savings**: 90-99%+ (cumulative: 95-99%)

---

## 💰 **Cost Savings Calculation**

### **Assumptions**
- **GPT-4 pricing**: $0.03/1K tokens (input) + $0.06/1K tokens (output) = **$0.09/1K tokens**
- **Average session**: 100 interactions
- **Average tokens per interaction**: 5,000 (current) → 50 (optimized)

### **Monthly Cost Comparison**

| Phase | Tokens/Session | Sessions/Month | Tokens/Month | Cost/Month | Annual Cost |
|-------|---------------|----------------|--------------|-------------|--------------|
| Current | 500,000 | 100 | 50,000,000 | $4,500 | **$54,000** |
| Phase 1 | 100,000 | 100 | 10,000,000 | $900 | **$10,800** |
| Phase 2 | 20,000 | 100 | 2,000,000 | $180 | **$2,160** |
| Phase 3 | 5,000 | 100 | 500,000 | $45 | **$540** |

**Annual Savings**:
- Phase 1: **$43,200** (80% savings)
- Phase 2: **$51,840** (95% savings)
- Phase 3: **$53,460** (99% savings)

---

## 🎯 **Quick Start: 1-Day Implementation**

If you only have **1 day**, implement these changes for **immediate 80-90% token savings**:

### **Morning (4 hours): Compact Mode**

```bash
# 1. Modify GraphQueryEngine.java
cd /Users/magnusfind/Documents/find-shadow-model/gausvibe

# Add compactMode flag and update format methods
```

### **Afternoon (4 hours): Result Limiting**

```bash
# 2. Add defaultLimit and update query methods
```

**Result**: 80-95% token savings on day 1

---

## 📚 **Key Documents**

| Document | Purpose | Location |
|----------|---------|----------|
| Implementation Guide | This file - Step-by-step plan | `IMPLEMENTATION_GUIDE.md` |
| Token Savings Plan | Detailed analysis and examples | `TOKEN_SAVINGS_PLAN.md` |
| Token Savings Checklist | Task tracking | `TOKEN_SAVINGS_CHECKLIST.md` |
| Performance Plan | All optimizations | `PERFORMANCE_OPTIMIZATION_PLAN.md` |
| Optimization Summary | Summary of all work | `OPTIMIZATION_SUMMARY.md` |

---

## 🚀 **Getting Started**

### **Prerequisites**
- Java 17+ development environment
- GausVibe repository cloned
- Basic understanding of GausVibe architecture

### **First Steps**

1. **Read this guide** - Understand the plan
2. **Read TOKEN_SAVINGS_PLAN.md** - See detailed examples
3. **Check out the code** - Review current implementation
4. **Start with Phase 1** - Implement compact mode and limiting

### **Development Workflow**

```bash
# Build the project
mvn clean compile

# Run tests
mvn test

# Test a query
java -jar target/gausvibe.jar query --graph graph.json "class:com.example.Calculator"
```

---

## ✅ **Success Criteria**

### **Phase 1 Complete**
- [ ] Compact mode default enabled
- [ ] Result limiting default to 10
- [ ] Token usage reduced by ≥80%
- [ ] All existing tests pass
- [ ] New tests added

### **Phase 2 Complete**
- [ ] Text search available
- [ ] Structured file content available
- [ ] Token usage reduced by ≥90%
- [ ] All tests pass

### **Phase 3 Complete**
- [ ] Paginated queries work
- [ ] Diff queries available
- [ ] Query caching implemented
- [ ] Token usage reduced by ≥95%
- [ ] All tests pass

---

## 💡 **Tips for Success**

### **1. Focus on Token Reduction First**
- Compact responses > Fast responses
- Less data = fewer tokens = lower cost

### **2. Default to Minimal**
- Make minimal output the default
- Require explicit flags for verbose output

### **3. Limit Aggressively**
- Default limit of 10 is good
- Most LLMs only need first few results
- Can always request more with `--show-all`

### **4. Measure Token Usage**
- Add token counting to responses
- Monitor token usage in production
- Optimize based on real data

### **5. Maintain Backward Compatibility**
- Keep existing verbose modes
- Add flags for new behavior
- Don't break existing users

---

## 🎉 **Expected Outcomes**

### **After Phase 1 (1 week)**
- ✅ 80-95% token savings on common operations
- ✅ Faster responses (less data to transmit)
- ✅ Better LLM reasoning (less noise in responses)
- ✅ Cost savings: 60-80%

### **After Phase 2 (2 weeks)**
- ✅ 90-95% token savings overall
- ✅ Full text search capability
- ✅ Structured file queries
- ✅ Cost savings: 90-95%

### **After Phase 3 (4 weeks)**
- ✅ 95-99%+ token savings overall
- ✅ All advanced features working
- ✅ Pagination for large datasets
- ✅ Diff queries for changes
- ✅ Query caching for repeats
- ✅ Cost savings: 95-99%

---

## 📞 **Support & Resources**

### **Need Help?**
- Check the detailed plans in `TOKEN_SAVINGS_PLAN.md`
- Review the source code comments
- Look at the test files for examples

### **Contributing**
- Fork the repository
- Implement a feature from this guide
- Add tests
- Submit a PR

---

## 🎯 **Conclusion**

By following this implementation guide, you can transform GausVibe from a useful tool into a **cost-efficient, token-optimized powerhouse** for LLM coding operations.

**Investment**: 4 weeks of development
**Return**: 95-99% reduction in LLM token costs

**For a team using GausVibe regularly, this could save tens of thousands of dollars per year in API costs alone.**

---

**Status**: Ready to implement
**Next Step**: Start with Phase 1, Day 1 - Compact Query Responses
**Estimated Completion**: 4 weeks for full optimization

🚀 **Let's save some tokens!**
