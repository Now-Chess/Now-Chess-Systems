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

Finds the king square for `color` by scanning `board.pieces` for a `Piece(color, PieceType.King)`. If no king is found (constructed/test boards), returns `false`.

Then checks whether any enemy piece's `MoveValidator.legalTargets` contains that square. This works correctly for all piece types, including the king: `kingTargets` returns the squares the king can move to, which are identical to the squares the king attacks, so using `legalTargets` for attack detection is correct by design.

Returns `true` if the king square is covered by at least one enemy piece.


#### `legalMoves(board, color)`

1. Filter `board.pieces` to entries where `piece.color == color`.
2. For each such `(from, piece)`, call `MoveValidator.legalTargets(board, from)` to get geometric candidates.
3. For each candidate `to`, apply `board.withMove(from, to)` to get `newBoard`.
4. Keep only moves where `isInCheck(newBoard, color)` is `false` (i.e., the move does not leave own king in check).
5. Return the full set of `(from, to)` pairs that survive this filter.

#### `gameStatus(board, color)`

Returns a `PositionStatus` enum value based on `legalMoves(board, color)` and `isInCheck(board, color)`:

- `Mated`    — `legalMoves` is empty **and** king is in check → the side to move has been checkmated
- `Drawn`    — `legalMoves` is empty **and** king is **not** in check → stalemate (draw)
- `InCheck`  — `legalMoves` is non-empty **and** king is in check → game continues under check
- `Normal`   — otherwise

#### Local `PositionStatus` enum

Defined in `GameRules.scala`. Names are intentionally distinct from `MoveResult` variants to avoid unqualified-name collisions in `GameController.scala`:

```scala
enum PositionStatus:
  case Normal, InCheck, Mated, Drawn
```

---

### Modified: `MoveResult` (in `GameController.scala`)

Three new variants; existing variants are unchanged:

| Variant | When used |
|---|---|
| `MovedInCheck(newBoard, captured, newTurn)` | Move was legal; opponent is now in check but has legal replies |
| `Checkmate(winner: Color)` | Move was legal; opponent is `Mated` → `winner` is the side that just moved |
| `Stalemate` | Move was legal; opponent is `Drawn` (no legal reply, not in check) |

`Moved` continues to be used when `gameStatus` returns `Normal`.

---

### Modified: `GameController.processMove`

After computing `(newBoard, captured)` from `board.withMove`:

1. Call `GameRules.gameStatus(newBoard, newTurn)`.
2. Map to the appropriate `MoveResult`:

```
PositionStatus.Normal   → Moved(newBoard, captured, newTurn)
PositionStatus.InCheck  → MovedInCheck(newBoard, captured, newTurn)
PositionStatus.Mated    → Checkmate(turn)   // turn = the side that just moved
PositionStatus.Drawn    → Stalemate
```

---

### Modified: `GameController.gameLoop`

**New terminal branches** (both print a message then restart):

- `Checkmate(winner)` → print `"Checkmate! {winner.label} wins."`, then recurse with `(Board.initial, Color.White)`
- `Stalemate`         → print `"Stalemate! The game is a draw."`, then recurse with `(Board.initial, Color.White)`

**New non-terminal branch:**

- `MovedInCheck(newBoard, captured, newTurn)` → print the same optional capture message as `Moved` (when `captured.isDefined`), then print `"{newTurn.label} is in check!"`, then recurse with `(newBoard, newTurn)`

**Restart vs. exit:** Checkmate and stalemate restart the game automatically (no prompt). This is intentionally asymmetric with `Quit`, which exits. `Quit` is an explicit user request to stop; Checkmate/Stalemate are natural game endings that should roll into a new game.

---

## Test Strategy

All tests are unit tests extending `AnyFunSuite with Matchers with JUnitSuiteLike`.

### `GameRulesTest` — new file

| Scenario | Method | Expected |
|---|---|---|
| King attacked by enemy rook on same rank | `isInCheck` | `true` |
| King not attacked (only own pieces nearby) | `isInCheck` | `false` |
| No king on board (constructed board) | `isInCheck` | `false` |
| Move that exposes own king to rook is excluded | `legalMoves` | does not contain that move |
| Move that blocks check is included | `legalMoves` | contains the blocking move |
| Checkmate: White Qh8, Ka6; Black Ka8 — Black king is in check (Qh8 along rank 8), cannot escape to a7 (Ka6), b7 (Ka6), or b8 (Qh8) | `gameStatus` | `Mated` |
| Stalemate: White Qb6, Kc6; Black Ka8 — Black king has no legal moves (a7/b7/b8 all controlled by Qb6), not in check | `gameStatus` | `Drawn` |
| King in check with at least one escape square | `gameStatus` | `InCheck` |
| Normal midgame position, not in check, has moves | `gameStatus` | `Normal` |

### `GameControllerTest` additions — new `processMove` cases

| Scenario | Expected `MoveResult` |
|---|---|
| Move leaves opponent in check (has escape) | `MovedInCheck` |
| Move results in checkmate | `Checkmate(winner)` where winner is the side that moved |
| Move results in stalemate | `Stalemate` |

### `GameControllerTest` additions — new `gameLoop` cases

| Scenario | Expected output / behavior |
|---|---|
| `gameLoop` receives `Checkmate(White)` | Prints "Checkmate! White wins." and continues (new game) |
| `gameLoop` receives `Stalemate` | Prints "Stalemate! The game is a draw." and continues (new game) |
| `gameLoop` receives `MovedInCheck` with a capture | Prints capture message AND check message |
| `gameLoop` receives `MovedInCheck` without a capture | Prints check message only |

---

## Development Workflow (TDD)

1. Create `GameRules.scala` with empty/stub method bodies that compile but return placeholder values (`false`, `Set.empty`, `PositionStatus.Normal`).
2. Write all `GameRulesTest` tests — they should **fail**.
3. Implement `GameRules` logic until `GameRulesTest` is green.
4. Add new `MoveResult` variants to `GameController.scala`; update `processMove` to call `GameRules.gameStatus` (stub the match arms initially).
5. Write new `GameControllerTest` cases — they should **fail**.
6. Implement `processMove` match arms and `gameLoop` new branches until all tests pass.
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
