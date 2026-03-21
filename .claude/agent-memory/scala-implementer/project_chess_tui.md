---
name: chess_tui_implementation
description: Chess TUI implemented in modules/core under de.nowchess.chess — model, renderer, parser, game loop
type: project
---

Chess TUI standalone app implemented in `modules/core`, package `de.nowchess.chess`.

**Why:** Initial feature to demonstrate the system's TUI capability per ADR-001.

**How to apply:** When extending the chess logic (legality, castling, en passant, promotion), build on the existing `Model.scala` opaque `Board` type and add methods via extension. The `@main` entry point is `chessMain` in `Game.scala`. `Test.scala` still exists as a separate hello-world stub — do not remove it.

Key design choices:
- `Board` is an opaque type over `Map[Square, Piece]` with extension methods
- `Color` and `PieceType` are Scala 3 enums
- `Renderer.render` returns `String`, never prints
- `Parser.parseMove` returns `Option[(Square, Square)]` — coordinate notation only (e.g. `e2e4`)
- No move legality validation — moves are applied as-is
- ANSI 256-colour background codes used for light/dark squares (48;5;223 beige, 48;5;130 brown)
