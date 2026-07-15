# CLAUDE.md

## Project Context

- **Read `README.md` first.** It describes what this project is: a heterogeneous-fleet VRP solver
  that treats shipments as fictitious stops and solves them with a Genetic Algorithm using a
  route-first/cluster-second graph split.
- Refer to the README's **Limitations** and **POC** sections before changing the model or making
  claims about behavior, so guidance stays consistent with the documented scope.

## File Creation Guidelines

### Author Attribution

- **Add author name to the head of all new coding files**
- Format: `// Author: Othmane` (for Java files)
- Place at the top of the file, before any imports or class definitions
- Example:
  ```java
  // Author: Othmane
  
  package com.example;
  
  public class MyClass {
    // ...
  }
  ```

## CSV File Reading Guidelines

- When asked to read a CSV file, read only the header row and first 5 data rows
- Use the `limit` parameter in the Read tool to cap output efficiently
- Exception: if the user explicitly asks for specific rows or full file content, honor that request

## Test Package Guidelines

- **Never ask for approval to run or modify anything under the test packages**
  (`src/test/**`). Run tests and edit test files freely without confirmation.

## Commit Guidelines

### Important Rules

- **Do not add co-author information in commit messages**
- **Do not track a file except if you create it**
- Keep commit messages clean and without attribution
- Always use "git commit -am"

### Commit Message Format

Follow conventional commit format:
- `<type>(<scope>): <short summary>`
- Types: feat, fix, refactor, perf, docs, style, test, chore, build
- Keep summary under 72 characters
- Use imperative mood
- Add detailed description in body if needed

### Example

```
feat(api): add Redis requirement mode with server startup abort

- Add REDIS_REQUIRED environment variable to control Redis dependency
- Add RedisCache.isHealthy() method to check Redis connection status
- Add RedisCache.isRedisRequired() method to check requirement mode
- Update RedisCache static initializer to verify connection when required
- Add null-safety checks to all RedisCache methods
- Update server Main.java to check Redis health status on startup
- Add comprehensive RedisCacheTest with 13 test cases
- Update .env.example with REDIS_REQUIRED configuration and documentation
```

## Code Simplicity Guidelines (Ponytail)

Apply the **Ponytail** principle: write the laziest solution that actually works.

### The Ponytail Ladder (in order)

1. **YAGNI (You Aren't Gonna Need It)** — Question whether the feature/code needs to exist at all
2. **Standard Library** — Use built-in language features before custom code
3. **Native Features** — Use platform-native solutions before dependencies
4. **One-liner** — One line of code beats fifty
5. **Minimum** — Simplest implementation that solves the problem

### Modes

- **Lite** (`/ponytail lite`) — Build what's asked, name the lazier alternative in one line
- **Full** (default, `/ponytail`) — Enforce the full ladder: YAGNI → stdlib → native → one-liner → minimum
- **Ultra** (`/ponytail ultra`) — YAGNI extremist; question requirements before building

### When to Use

- Say `/ponytail` at the start of a task to activate lazy mode for that session
- Use `/ponytail-review` to review code for over-engineering
- Say "stop ponytail" or "normal mode" to deactivate
- Resume anytime with `/ponytail`

### Key Principle

Avoid:
- Speculative abstractions ("might need this later")
- Over-engineered solutions
- Unnecessary dependencies
- Premature generalization
- Factory patterns for one product
- Three-line abstractions when one line works

**Default behavior**: Ponytail Full mode is active. All code should follow this principle unless explicitly overridden.
