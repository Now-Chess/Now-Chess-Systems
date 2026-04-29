# NowChessSystems — Overview

> **Navigation aid.** This article shows WHERE things live (routes, models, files). Read actual source files before implementing new features or making changes.

**NowChessSystems** is a scala project built with raw-http.

## Scale

1 middleware layers · 1 environment variables

## High-Impact Files

Changes to these files have the widest blast radius across the codebase:

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` — imported by **74** files
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` — imported by **66** files
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` — imported by **52** files
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` — imported by **42** files
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` — imported by **27** files
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` — imported by **21** files

## Required Environment Variables

- `STOCKFISH_PATH` — `modules/bot/python/nnue.py`

---
_Back to [index.md](./index.md) · Generated 2026-04-23_