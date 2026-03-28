---
name: module-api-structure
description: File and type overview for the modules/api module (shared domain types)
type: project
---

# Module: `modules/api`

**Purpose:** Shared domain model — pure data types with no game logic. Depended on by `modules/core`.

**Gradle:** `id("scala")`, no `application` plugin. No Quarkus. Uses scoverage plugin.

**Package root:** `de.nowchess.api`

## Source files (`src/main/scala/de/nowchess/api/`)

### `board/`
| File | Contents |
|------|----------|
| `Board.scala` | `opaque type Board = Map[Square, Piece]` — extensions: `pieceAt`, `withMove`, `pieces`; `Board.initial` sets up start position |
| `Color.scala` | `enum Color { White, Black }` — `.opposite`, `.label` |
| `Piece.scala` | `case class Piece(color, pieceType)` — convenience vals `WhitePawn`…`BlackKing` |
| `PieceType.scala` | `enum PieceType { Pawn, Knight, Bishop, Rook, Queen, King }` — `.label` |
| `Square.scala` | `enum File { A–H }`, `enum Rank { R1–R8 }`, `case class Square(file, rank)` — `.toString` algebraic, `Square.fromAlgebraic(s)` |

### `game/`
| File | Contents |
|------|----------|
| `GameState.scala` | `case class CastlingRights(kingSide, queenSide)` + `.None`/`.Both`; `enum GameResult { WhiteWins, BlackWins, Draw }`; `enum GameStatus { NotStarted, InProgress, Finished(result) }`; `case class GameState(piecePlacement, activeColor, castlingWhite, castlingBlack, enPassantTarget, halfMoveClock, fullMoveNumber, status)` — FEN-compatible snapshot |

### `move/`
| File | Contents |
|------|----------|
| `Move.scala` | `enum PromotionPiece { Knight, Bishop, Rook, Queen }`; `enum MoveType { Normal, CastleKingside, CastleQueenside, EnPassant, Promotion(piece) }`; `case class Move(from, to, moveType = Normal)` |

### `player/`
| File | Contents |
|------|----------|
| `PlayerInfo.scala` | `opaque type PlayerId = String`; `case class PlayerInfo(id: PlayerId, displayName: String)` |

### `response/`
| File | Contents |
|------|----------|
| `ApiResponse.scala` | `sealed trait ApiResponse[+A]` → `Success[A](data)` / `Failure(errors)`; `case class ApiError(code, message, field?)`; `case class Pagination(page, pageSize, totalItems)` + `.totalPages`; `case class PagedResponse[A](items, pagination)` |

## Test files (`src/test/scala/de/nowchess/api/`)
Mirror of main structure — one `*Test.scala` per source file using `AnyFunSuite with Matchers`.

## Notes
- `GameState` is FEN-style but `Board` (in `core`) is a `Map[Square,Piece]` — the two are separate representations
- `CastlingRights` is defined here in `api`; the castling logic lives in `core`
