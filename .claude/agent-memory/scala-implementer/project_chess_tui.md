---
name: chess_tui_implementation
description: Chess TUI in modules/core — MVC sub-packages under de.nowchess.chess
type: project
---

Chess TUI standalone app implemented in `modules/core`, root package `de.nowchess.chess`.

**Why:** Initial feature to demonstrate the system's TUI capability per ADR-001. Refactored to MVC pattern to separate concerns.

**How to apply:** When extending chess logic (legality, castling, en passant, promotion), build on the existing `Board` opaque type in `model` and add extension methods there. The `@main` entry point is `chessMain` in `Main.scala` (root package). Game loop lives in `GameController`.

Package layout after MVC refactor:
- `de.nowchess.chess.model` — `Model.scala`: `Color`, `PieceType`, `Piece`, `Square`, `Board` (opaque type)
- `de.nowchess.chess.view` — `Renderer.scala`: ANSI board renderer
- `de.nowchess.chess.controller` — `Parser.scala`: coordinate-notation parser; `GameController.scala`: game loop
- `de.nowchess.chess` — `Main.scala`: `@main def chessMain()`

Key design choices:
- `Board` is an opaque type over `Map[Square, Piece]` with extension methods
- `Color` and `PieceType` are Scala 3 enums
- `Renderer.render` returns `String`, never prints
- `Parser.parseMove` returns `Option[(Square, Square)]` — coordinate notation only (e.g. `e2e4`)
- No move legality validation — moves are applied as-is
- ANSI 256-colour background codes used for light/dark squares (48;5;223 beige, 48;5;130 brown)
