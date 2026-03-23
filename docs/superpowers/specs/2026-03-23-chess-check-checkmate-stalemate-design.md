# Chess Check / Checkmate / Stalemate — Design Spec

**Date:** 2026-03-23
**Status:** Approved

---

## Scope

Implement check detection, checkmate (win condition), and stalemate (draw) on top of the existing normal-move rules. En passant, castling, and pawn promotion are **out of scope** for this iteration.

---

## Architecture

### New: `GameRules` object

**File:** `modules/core/src/main/scala/de/nowchess/chess/logic/GameRules.scala`

Owns all check-aware game logic. `MoveValidator` retains its documented geometric-only contract ("ignoring check/pin").

```
GameRules
  isInCheck(board, color): Boolean
  legalMoves(board, color): Set[(Square, Square)]
  gameStatus(board, color): PositionStatus
```

#### `isInCheck(board, color)`
Finds the king square for `color`, then checks whether any enemy piece's `MoveValidator.legalTargets` contains that square. Returns `true` if the king is under attack.

#### `legalMoves(board, color)`
For every piece of `color` on the board, collect `MoveValidator.legalTargets`. Filter each candidate move by applying it to the board (`board.withMove`) and verifying `isInCheck` is `false` on the resulting board. Returns the full set of `(from, to)` pairs that are truly legal.

#### `gameStatus(board, color)`
Returns a `PositionStatus` enum value:
- `Checkmate` — `legalMoves` is empty **and** king is in check → the side to move loses
- `Stalemate` — `legalMoves` is empty **and** king is **not** in check → draw
- `InCheck`   — `legalMoves` is non-empty **and** king is in check → game continues under check
- `Normal`    — otherwise

#### Local `PositionStatus` enum

Defined inside `GameRules.scala` (or its companion package):

```scala
enum PositionStatus:
  case Normal, InCheck, Checkmate, Stalemate
```

---

### Modified: `MoveResult` (in `GameController.scala`)

Two new variants are added; existing variants are unchanged:

| Variant | When used |
|---|---|
| `MovedInCheck(newBoard, captured, newTurn)` | Move was legal; opponent is now in check |
| `Checkmate(winner: Color)` | Move was legal; opponent has no legal reply → winner is the side that just moved |
| `Stalemate` | Move was legal; opponent has no legal reply and is not in check → draw |

`Moved` continues to be used for all other successful moves.

---

### Modified: `GameController.processMove`

After computing `(newBoard, captured)` from `board.withMove`:

1. Call `GameRules.gameStatus(newBoard, newTurn)`.
2. Map the result to the appropriate `MoveResult` variant.

```
Normal     → Moved(newBoard, captured, newTurn)
InCheck    → MovedInCheck(newBoard, captured, newTurn)
Checkmate  → Checkmate(turn)          // turn = the side that just moved
Stalemate  → Stalemate
```

---

### Modified: `GameController.gameLoop`

Two new terminal branches:

- `Checkmate(winner)` → print `"Checkmate! {winner} wins."`, then recurse with `(Board.initial, Color.White)`
- `Stalemate`         → print `"Stalemate! The game is a draw."`, then recurse with `(Board.initial, Color.White)`
- `MovedInCheck`      → print `"{newTurn} is in check!"`, then recurse normally with the new board and turn

---

## Test Strategy

All tests are unit tests extending `AnyFunSuite with Matchers with JUnitSuiteLike`.

### `GameRulesTest`

| Scenario | Method under test |
|---|---|
| King is attacked by an enemy rook | `isInCheck` → true |
| King is not attacked | `isInCheck` → false |
| Move that exposes king is filtered out | `legalMoves` excludes it |
| Checkmate position (e.g. back-rank mate) | `gameStatus` → Checkmate |
| Stalemate position | `gameStatus` → Stalemate |
| In-check position with at least one escape | `gameStatus` → InCheck |
| Normal position | `gameStatus` → Normal |

### `GameControllerTest` additions

| Scenario | Expected `MoveResult` |
|---|---|
| Move leaves opponent in check | `MovedInCheck` |
| Move results in checkmate | `Checkmate(winner)` |
| Move results in stalemate | `Stalemate` |

---

## Development Workflow (TDD)

1. Create `GameRules` with empty/stub method bodies (compile but return placeholder values).
2. Write all `GameRulesTest` tests — they should fail.
3. Implement `GameRules` logic until tests pass.
4. Add new `MoveResult` variants and stub `processMove` changes.
5. Write new `GameControllerTest` cases — they should fail.
6. Implement `processMove` and `gameLoop` changes until tests pass.
7. Run `./gradlew :modules:core:test` — full green build required.

---

## Files Changed

| File | Change |
|---|---|
| `modules/core/src/main/scala/de/nowchess/chess/logic/GameRules.scala` | New |
| `modules/core/src/test/scala/de/nowchess/chess/logic/GameRulesTest.scala` | New |
| `modules/core/src/main/scala/de/nowchess/chess/controller/GameController.scala` | Add `MoveResult` variants; update `processMove` and `gameLoop` |
| `modules/core/src/test/scala/de/nowchess/chess/controller/GameControllerTest.scala` | Add new test cases |

No changes to `modules/api` or `MoveValidator`.
