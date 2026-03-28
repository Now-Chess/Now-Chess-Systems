---
name: module-core-structure
description: File and type overview for the modules/core module (TUI chess engine)
type: project
---

# Module: `modules/core`

**Purpose:** Standalone TUI chess application. All game logic, move validation, rendering. Depends on `modules/api`.

**Gradle:** `id("scala")` + `application` plugin. Main class: `de.nowchess.chess.Main`. Uses scoverage plugin.

**Package root:** `de.nowchess.chess`

## Source files (`src/main/scala/de/nowchess/chess/`)

### Root
| File | Contents |
|------|----------|
| `Main.scala` | Entry point — prints welcome, starts `GameController.gameLoop(GameContext.initial, Color.White)` |

### `controller/`
| File | Contents |
|------|----------|
| `GameController.scala` | `sealed trait MoveResult` ADT: `Quit`, `InvalidFormat`, `NoPiece`, `WrongColor`, `IllegalMove`, `Moved`, `MovedInCheck`, `Checkmate`, `Stalemate`; `object GameController` — `processMove(ctx, turn, raw): MoveResult` (pure), `gameLoop(ctx, turn)` (I/O loop), `applyRightsRevocation(...)` (castling rights bookkeeping) |
| `Parser.scala` | `object Parser` — `parseMove(input): Option[(Square, Square)]` parses coordinate notation e.g. `"e2e4"` |

### `logic/`
| File | Contents |
|------|----------|
| `GameContext.scala` | `enum CastleSide { Kingside, Queenside }`; `case class GameContext(board, whiteCastling, blackCastling)` — `.castlingFor(color)`, `.withUpdatedRights(color, rights)`; `GameContext.initial`; `extension (Board).withCastle(color, side)` moves king+rook atomically |
| `GameRules.scala` | `enum PositionStatus { Normal, InCheck, Mated, Drawn }`; `object GameRules` — `isInCheck(board, color)`, `legalMoves(ctx, color): Set[(Square,Square)]`, `gameStatus(ctx, color): PositionStatus` |
| `MoveValidator.scala` | `object MoveValidator` — `isLegal(board, from, to)`, `legalTargets(board, from): Set[Square]` (board-only, no castling), `legalTargets(ctx, from)` (context-aware, includes castling), `isCastle`, `castleSide`, `castlingTargets(ctx, color)` — full castling legality (empty squares, no check through transit) |

### `view/`
| File | Contents |
|------|----------|
| `Renderer.scala` | `object Renderer` — `render(board): String` outputs ANSI-colored board with file/rank labels |
| `PieceUnicode.scala` | `extension (Piece).unicode: String` maps each piece to its Unicode chess symbol |

## Test files (`src/test/scala/de/nowchess/chess/`)
Mirror of main structure — one `*Test.scala` per source file using `AnyFunSuite with Matchers`.

## Key design notes
- `MoveValidator` has two overloaded `legalTargets`: one takes `Board` (geometry only), one takes `GameContext` (adds castling)
- `GameRules.legalMoves` filters by check — it calls `MoveValidator.legalTargets(ctx, from)` then simulates each move
- Castling rights revocation is in `GameController.applyRightsRevocation`, triggered after every move
- No `@QuarkusTest` — this module is a plain Scala application, not a Quarkus service
