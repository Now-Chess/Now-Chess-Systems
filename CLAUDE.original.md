# Now-Chess

Scala 3.5.1 · Gradle 9

## Commands

```
./clean      # Clear build dirs — only when necessary
./compile    # Compile all modules — always run
./test       # Run all tests
./coverage   # Check coverage
./lint       # Run linters
```
Try to stick to these commands for consistency.

## Modules

| Module | Role | Depends on |
|--------|------|-----------|
| `api`  | Model / shared types | (none) |
| `core` | Primary business logic | api, rule |
| `rule` | Game rules | api |
| `bot`  | Bots and AI | api,rule,io |
| `io`   | Export formats | api, core |

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

### Linters

- **scalafmt** — enforces formatting; run `./gradlew spotlessScalaCheck` to check and `./gradlew spotlessScalaApply` to refactor.
- **scalafix** — enforces style and detects unused imports/code; run `./gradlew scalafix` to apply rules.

## Architecture Decisions

- **Immutable state as primary model:** GameContext (api) holds board, history, player state — immutable, passed through the system. Each move creates a new GameContext, enabling undo/redo without side effects.
- **Observer pattern for UI decoupling:** GameEngine publishes move/state events; CommandInvoker queues moves; UI listens to events, not polling. GameEngine never imports UI code.
- **RuleSet trait encapsulates rules:** Move generation, check, castling, en passant all in RuleSet impl. GameEngine calls rules as a black box; rules don't know about the rest of core.
- **Polyglot hash must follow spec index layout:** piece keys use interleaved mapping `(pieceType * 2 + colorBit)` with black=0/white=1, castling keys are `768..771`, en-passant file keys are `772..779` and are XORed only if side-to-move has a pawn that can capture en passant, side-to-move key is `780` for white.
- **Alpha-beta uses sequential PV search by default:** parallel split was disabled because fixed-window futures removed pruning effectiveness; correctness and pruning quality take priority over speculative parallelism.
- **Search hash is updated incrementally per move:** bot search now updates Zobrist keys from parent hash with move deltas instead of recomputing piece scans at every node.

## Rules

- **Tests are the spec.** Never modify tests to pass; modify requirements or code. Update tests only if requirements change.
- Never read build folders. Ask permission if needed.
- Keep this file up to date with any important decisions or conventions.

---

## Instructions for Claude Code

### Two-Step Rule (mandatory)
**Step 1 — Orient:** Use wiki articles to find WHERE things live.
**Step 2 — Verify:** Read the actual source files listed in the wiki article BEFORE writing any code.

Wiki articles are structural summaries extracted by AST. They show routes, models, and file locations.
They do NOT show full function logic, middleware internals, or dynamic runtime behavior.
**Never write or modify code based solely on wiki content — always read source files first.**

Read in order at session start:
1. `.codesight/wiki/index.md` — orientation map (~200 tokens)
2. `.codesight/wiki/overview.md` — architecture overview (~500 tokens)
3. Domain article (e.g. `.codesight/wiki/auth.md`) → check "Source Files" section → read those files
4. `.codesight/CODESIGHT.md` — full context map for deep exploration

Routes marked `[inferred]` in wiki articles were detected via regex — verify against source before trusting.
If any source file shows ⚠ in the wiki, re-run `codesight --wiki` before proceeding.

Or use the codesight MCP server for on-demand queries:
- `codesight_get_wiki_article` — read a specific wiki article by name
- `codesight_get_wiki_index` — get the wiki index
- `codesight_get_summary` — quick project overview
- `codesight_get_routes --prefix /api/users` — filtered routes
- `codesight_get_blast_radius --file src/lib/db.ts` — impact analysis before changes
- `codesight_get_schema --model users` — specific model details

Only open specific files after consulting codesight context. This saves ~16.893 tokens per conversation.

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- After modifying code files in this session, run `python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"` to keep the graph current
