# Now-Chess

Scala 3.5.1 · Gradle 9

## Commands

```
./clean      # Clear build dirs — only when necessary
./compile    # Compile all modules — always run
./test       # Run all tests
./coverage   # Check coverage
```
Try to stick to these commands for consistency.

## Modules

| Module | Role | Depends on |
|--------|------|-----------|
| `api`  | Model / shared types | (none) |
| `core` | Primary business logic | api, rule |
| `rule` | Game rules | api |
| `io`   | Export formats | api, core |
| `ui`   | Entrypoint & UI | core, io |

## Style

- Use immutable data and pure functions.
- Keep functions under 30 lines. If you need "and" to describe it, split it.
- Keep cyclomatic complexity under 15.
- Avoid comments. Let names carry intent; comment only non-obvious algorithms.
- Scan for duplicated logic before finishing. Extract it.
- Follow default Sonar style for Scala.
- Use `Option` or `Either` for fallible operations; avoid exceptions for control flow.
- Naming: types are PascalCase, functions/values are camelCase.

## Code Quality

- **Coverage:** 100% condition coverage required in `api`, `core`, `rule`, `io` (mandatory); `ui` exempt.

## Architecture Decisions

- **Immutable state as primary model:** GameContext (api) holds board, history, player state — immutable, passed through the system. Each move creates a new GameContext, enabling undo/redo without side effects.
- **Observer pattern for UI decoupling:** GameEngine publishes move/state events; CommandInvoker queues moves; UI listens to events, not polling. GameEngine never imports UI code.
- **RuleSet trait encapsulates rules:** Move generation, check, castling, en passant all in RuleSet impl. GameEngine calls rules as a black box; rules don't know about the rest of core.

## Rules

- **Tests are the spec.** Never modify tests to pass; modify requirements or code. Update tests only if requirements change.
- Never read build folders. Ask permission if needed.
- Keep this file up to date with any important decisions or conventions.