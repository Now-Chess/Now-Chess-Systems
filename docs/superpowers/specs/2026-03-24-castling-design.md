# Castling Implementation Design

**Date:** 2026-03-24
**Status:** Approved (rev 2)
**Branch:** castling

---

## Context

The NowChessSystems chess engine currently operates on a raw `Board` (opaque `Map[Square, Piece]`) paired with a `Color` for turn tracking. Castling requires tracking whether the king and rooks have previously moved — state that does not exist in the current engine layer. The `CastlingRights` and `MoveType.Castle*` types are already defined in the `api` module but are not wired into the engine.

---

## Approach: `GameContext` Wrapper (Option B)

Introduce a thin `GameContext` wrapper in `modules/core` that bundles `Board` with castling rights for both sides. This is the single seam through which the engine learns about castling availability without pulling in the full FEN-structured `GameState` type.

---

## Section 1 — `GameContext` Type

**Location:** `modules/core/src/main/scala/de/nowchess/chess/logic/GameContext.scala`

```scala
case class GameContext(
  board: Board,
  whiteCastling: CastlingRights,
  blackCastling: CastlingRights
):
  def castlingFor(color: Color): CastlingRights =
    if color == Color.White then whiteCastling else blackCastling

  def withUpdatedRights(color: Color, rights: CastlingRights): GameContext =
    if color == Color.White then copy(whiteCastling = rights)
    else copy(blackCastling = rights)
```

`GameContext.initial` wraps `Board.initial` with `CastlingRights.Both` for both sides.

`gameLoop` and `processMove` replace `(board: Board, turn: Color)` with `(ctx: GameContext, turn: Color)`. All `MoveResult` variants that previously carried `newBoard: Board` now carry `newCtx: GameContext`. The `gameLoop` render call becomes `Renderer.render(ctx.board)`, and all `gameLoop` pattern match arms that destructure `MoveResult.Moved(newBoard, ...)` or `MoveResult.MovedInCheck(newBoard, ...)` must be updated to destructure `newCtx` and pass it to the recursive `gameLoop` call.

---

## Section 2 — `CastleSide` and Board Extension for Castle Moves

### `CastleSide` enum

`CastleSide` is a two-value engine-internal enum defined in `core` (not in `api`). It is co-located in `GameContext.scala` — there is no separate `CastleSide.scala` file.

```scala
enum CastleSide:
  case Kingside, Queenside
```

### `withCastle` extension

`Board.withMove(from, to)` moves a single piece. Castling moves two pieces atomically. To avoid a circular dependency (`api` must not import from `core`), `withCastle` is **not** added to `Board` in the `api` module. Instead it is defined as an extension method in `core`, co-located with `GameContext`:

```scala
// inside GameContext.scala or a BoardCastleOps.scala in core
extension (b: Board)
  def withCastle(color: Color, side: CastleSide): Board = ...
```

Post-castle square assignments:
- **Kingside White:** King e1→g1, Rook h1→f1
- **Queenside White:** King e1→c1, Rook a1→d1
- **Kingside Black:** King e8→g8, Rook h8→f8
- **Queenside Black:** King e8→c8, Rook a8→d8

---

## Section 3 — `MoveValidator` Castling Logic

### Signature change

`legalTargets` and `isLegal` are extended to accept `GameContext` when the caller has full game context. To avoid breaking `GameRules.isInCheck` (which uses `legalTargets` with only a `Board` for attacked-square detection), the implementation retains a **board-only private helper** for sliding/jump/normal king targets, and a **public overload** that additionally unions castling targets when a `GameContext` is provided:

```scala
// board-only (used internally by isInCheck)
def legalTargets(board: Board, from: Square): Set[Square]

// context-aware (used by legalMoves and processMove)
def legalTargets(ctx: GameContext, from: Square): Set[Square]
```

The `GameContext` overload delegates to the `Board` overload for all piece types except King, where it additionally unions `castlingTargets(ctx, color)`.

`isLegal` is likewise overloaded:

```scala
// board-only (retained for callers that have no castling context)
def isLegal(board: Board, from: Square, to: Square): Boolean

// context-aware (used by processMove)
def isLegal(ctx: GameContext, from: Square, to: Square): Boolean
```

The context-aware `isLegal(ctx, from, to)` calls `legalTargets(ctx, from).contains(to)` — using the context-aware overload — so castling targets are included in the legality check.

### `castlingTargets` method

```scala
def castlingTargets(ctx: GameContext, color: Color): Set[Square]
```

For each side (kingside, queenside), checks all six conditions in order (failing fast):

1. `CastlingRights` flag is `true` for that side (`ctx.castlingFor(color)`)
2. King is on its home square (e1 for White, e8 for Black)
3. Relevant rook is on its home square (h-file for kingside, a-file for queenside)
4. All squares between king and rook are empty
5. King is **not currently in check** — calls `GameRules.isInCheck(ctx.board, color)` using the board-only path (no castling recursion)
6. Each square the king **passes through and lands on** is not attacked — checks that no enemy `legalTargets(board, enemySq)` (board-only) covers those squares

Transit and landing squares:
- **Kingside:** f-file, g-file (White: f1, g1; Black: f8, g8)
- **Queenside:** d-file, c-file (White: d1, c1; Black: d8, c8). Note: b1/b8 must be empty (condition 4) but the king does not pass through them, so they are not checked for attacks.

---

## Section 4 — `GameRules` Changes

`GameRules.legalMoves` must accept `GameContext` (not just `Board`) so it can enumerate castling moves as part of the legal move set. This is required for correct stalemate and checkmate detection — a position where the only legal move is to castle must not be evaluated as stalemate.

```scala
def legalMoves(ctx: GameContext, color: Color): Set[(Square, Square)]
```

Internally it calls `MoveValidator.legalTargets(ctx, from)` (the context-aware overload) for all pieces of `color`, then filters to moves that do not leave the king in check.

`isInCheck` retains its `(board: Board, color: Color)` signature — it does not need castling context.

`gameStatus` is updated to accept `GameContext`:

```scala
def gameStatus(ctx: GameContext, color: Color): PositionStatus
```

---

## Section 5 — `GameController` Changes

### Move detection and execution

`processMove` identifies a castling move by the king occupying its home square and moving exactly two files laterally:
- White: e1→g1 (kingside) or e1→c1 (queenside)
- Black: e8→g8 (kingside) or e8→c8 (queenside)

Legality is confirmed via `MoveValidator.isLegal(ctx, from, to)` (the context-aware overload, which includes castling targets). When a castling move is legal and executed:
1. Call `ctx.board.withCastle(color, side)` to move both pieces atomically.
2. Revoke **both** castling rights for the moving color in the new `GameContext`.

### Rights revocation rules (applied on every move)

After every move `(from, to)` is applied, revoke rights based on both the **source square** and the **destination square**. Both tables are checked independently and all triggered revocations are applied.

**Source square → revocation** (piece leaves its home square):

| Source square | Rights revoked |
|---------------|---------------|
| `e1` | Both White castling rights |
| `e8` | Both Black castling rights |
| `a1` | White queenside |
| `h1` | White kingside |
| `a8` | Black queenside |
| `h8` | Black kingside |

**Destination square → revocation** (a piece — including an enemy piece — arrives on a rook home square, meaning a capture removed the rook):

| Destination square | Rights revoked |
|--------------------|---------------|
| `a1` | White queenside |
| `h1` | White kingside |
| `a8` | Black queenside |
| `h8` | Black kingside |

This covers the following cases:
- **King normal move** — source square e1/e8 fires; both rights revoked.
- **King castle move** — the castle-specific step 2 revokes both rights for the moving color. Additionally, the source-square table fires (king departs e1/e8), revoking the same rights a second time. This double-revocation is idempotent and harmless. The king's destination (g1/c1/g8/c8) does not appear in the destination table, so no extra revocation fires there.
- **Own rook move** — source square a1/h1/a8/h8 fires.
- **Enemy capture on a rook home square** — destination square a1/h1/a8/h8 fires, revoking the side that lost the rook.

`processMove` also calls `GameRules.gameStatus(newCtx, turn.opposite)` — note this call passes the full `GameContext`, not just a `Board`, because `gameStatus` now accepts `GameContext`.

The revocation is applied to the `GameContext` that results from the move, before it is returned in `MoveResult`.

### Signatures

```scala
def processMove(ctx: GameContext, turn: Color, raw: String): MoveResult
def gameLoop(ctx: GameContext, turn: Color): Unit
```

`MoveResult.Moved` and `MoveResult.MovedInCheck` carry `newCtx: GameContext` instead of `newBoard: Board`. All `gameLoop` pattern match arms are updated to use `newCtx`. The render call uses `newCtx.board`.

On checkmate/stalemate reset, `GameContext.initial` is used.

---

## Section 6 — Move Notation

The player types standard coordinate notation:
- `e1g1` → White kingside castle
- `e1c1` → White queenside castle
- `e8g8` → Black kingside castle
- `e8c8` → Black queenside castle

No parser changes required. The controller identifies castling by the king moving 2 files from the home square.

---

## Section 7 — Testing

### `MoveValidatorTest`
- Castling target (g1) is returned when all kingside conditions are met (White)
- Castling target (c1) is returned when all queenside conditions are met (White)
- Castling targets returned for Black kingside (g8) and queenside (c8)
- Castling blocked when transit square is occupied (piece between king and rook)
- Castling blocked when king is in check (condition 5)
- Castling blocked when **transit square** is attacked (e.g., f1 attacked for White kingside)
- Castling blocked when **landing square** is attacked (e.g., g1 attacked for White kingside)
- Castling blocked when `kingSide = false` in `CastlingRights`
- Castling blocked when `queenSide = false` in `CastlingRights`
- Castling blocked when relevant rook is not on its home square

### `GameControllerTest`
- `processMove` with `e1g1` returns `Moved` with king on g1, rook on f1, and both White castling rights revoked in `newCtx`
- `processMove` with `e1c1` returns `Moved` with king on c1, rook on d1, and both White castling rights revoked in `newCtx`
- `processMove` castle attempt after king has moved returns `IllegalMove`
- `processMove` castle attempt after rook has moved returns `IllegalMove`
- Normal rook move from h1 revokes White kingside right in the returned `newCtx`
- Normal king move from e1 revokes both White rights in the returned `newCtx`
- Enemy capture on h1 (e.g., Black rook captures White rook on h1) revokes White kingside right in the returned `newCtx`

### `GameRulesTest`
- `legalMoves` includes castling destinations when available
- `legalMoves` excludes castling when king is in check
- `gameStatus` returns `Normal` (not `Drawn`) when the only legal move available is to castle — verifying that the `GameContext` signature change correctly prevents a false stalemate

---

## Files to Create / Modify

| Action | File |
|--------|------|
| **Create** | `modules/core/src/main/scala/de/nowchess/chess/logic/GameContext.scala` — includes `CastleSide` enum and `withCastle` Board extension |
| **Modify** | `modules/core/src/main/scala/de/nowchess/chess/logic/MoveValidator.scala` — add `castlingTargets`, board-only + context-aware `legalTargets`/`isLegal` overloads |
| **Modify** | `modules/core/src/main/scala/de/nowchess/chess/logic/GameRules.scala` — update `legalMoves` and `gameStatus` to accept `GameContext` |
| **Modify** | `modules/core/src/main/scala/de/nowchess/chess/controller/GameController.scala` — use `GameContext`; castling detection, execution, rights revocation |
| **Modify** | `modules/core/src/main/scala/de/nowchess/chess/Main.scala` — use `GameContext.initial` |
| **Modify** | `modules/core/src/test/scala/de/nowchess/chess/logic/MoveValidatorTest.scala` — new castling tests |
| **Modify** | `modules/core/src/test/scala/de/nowchess/chess/controller/GameControllerTest.scala` — update signatures + new castling tests |
| **Modify** | `modules/core/src/test/scala/de/nowchess/chess/logic/GameRulesTest.scala` — update signatures + new castling tests |
