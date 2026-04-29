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
Use consistently.

## Modules

| Module | Role | Depends on |
|--------|------|-----------|
| `api`  | Model / shared types | (none) |
| `core` | Primary business logic | api, rule |
| `rule` | Game rules | api |
| `bot`  | Bots and AI | api,rule,io |
| `io`   | Export formats | api, core |

## Style

- Immutable data, pure functions.
- Functions under 30 lines. Need "and"? Split it.
- Cyclomatic complexity under 15.
- No comments. Names carry intent. Comment non-obvious algorithms only.
- Scan duplicated logic. Extract.
- Follow default Sonar style for Scala.
- `Option`/`Either` for fallible ops. Skip exceptions for control flow.
- Naming: types PascalCase, functions/values camelCase.

## Code Quality

- **Coverage:** 100% condition coverage required in `api`, `core`, `rule`, `io` (mandatory); `ui` exempt.

### Linters

- **scalafmt** — Enforces formatting. Check: `./gradlew spotlessScalaCheck`. Refactor: `./gradlew spotlessScalaApply`.
- **scalafix** — Enforces style, detects unused imports/code. Run: `./gradlew scalafix`.

## Architecture Decisions

- **Immutable state as primary model:** GameContext (api) holds board, history, player state—immutable throughout. Each move → new GameContext. Enables undo/redo without side effects.
- **Observer pattern for UI decoupling:** GameEngine publishes move/state events; CommandInvoker queues moves; UI listens (no polling). GameEngine never imports UI.
- **RuleSet trait encapsulates rules:** Move generation, check, castling, en passant all in RuleSet impl. GameEngine calls rules as black box; rules don't know rest of core.
- **Polyglot hash must follow spec index layout:** Piece keys use interleaved mapping `(pieceType * 2 + colorBit)` (black=0, white=1). Castling keys: `768..771`. En-passant file keys: `772..779`, XORed only if side-to-move has capturable en passant. Side-to-move key: `780` (white).
- **Alpha-beta uses sequential PV search by default:** Parallel split disabled (fixed-window futures removed pruning effectiveness). Sequential PV default. Correctness + pruning quality > speculative parallelism.
- **Search hash is updated incrementally per move:** Bot search updates Zobrist keys from parent hash with move deltas, not recomputing piece scans per node.

## Rules

- **Tests are the spec.** Don't modify to pass. Fix requirements/code. Update only if requirements change.
- Never read build folders. Ask permission if needed.
- Keep file current with decisions + conventions.

---

## Instructions for Claude Code

### Two-Step Rule (mandatory)
**Step 1 — Orient:** Use wiki articles to find WHERE things live.
**Step 2 — Verify:** Read source files from wiki BEFORE coding.

Wiki = structural summaries (routes, models, file locations). No function logic, middleware internals, runtime behavior. Don't code from wiki alone—read sources.

Read in order at session start:
1. `.codesight/wiki/index.md` — orientation map (~200 tokens)
2. `.codesight/wiki/overview.md` — architecture overview (~500 tokens)
3. Domain article (e.g. `.codesight/wiki/auth.md`) → check "Source Files" section → read those files
4. `.codesight/CODESIGHT.md` — full context map for deep exploration

`[inferred]` routes = regex-detected. Verify sources. ⚠ in wiki? Re-run `codesight --wiki`.

Or use the codesight MCP server for on-demand queries:
- `codesight_get_wiki_article` — read a specific wiki article by name
- `codesight_get_wiki_index` — get the wiki index
- `codesight_get_summary` — quick project overview
- `codesight_get_routes --prefix /api/users` — filtered routes
- `codesight_get_blast_radius --file src/lib/db.ts` — impact analysis before changes
- `codesight_get_schema --model users` — specific model details

Consult codesight context first. Saves ~16.893 tokens/conversation.

## graphify

graphify knowledge graph at graphify-out/.

Rules:
- Architecture/codebase questions? Read graphify-out/GRAPH_REPORT.md (god nodes, communities).
- graphify-out/wiki/index.md exists? Use it (not raw files).
- Code modified? Run `python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"` to sync graph.