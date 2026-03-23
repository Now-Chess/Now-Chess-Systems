# Chess Check / Checkmate / Stalemate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add check detection, checkmate (win by opponent having no legal reply while in check), and stalemate (draw by opponent having no legal reply while not in check) to the chess game loop.

**Architecture:** A new `GameRules` object owns all check-aware logic; the existing `MoveValidator` keeps its geometric-only contract unchanged. `GameController.processMove` calls `GameRules.gameStatus` after each move and returns new `MoveResult` variants (`MovedInCheck`, `Checkmate`, `Stalemate`). Terminal states reset the board.

**Tech Stack:** Scala 3.5, ScalaTest (`AnyFunSuite with Matchers`), Gradle (`:modules:core:test`)

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `modules/core/src/main/scala/de/nowchess/chess/logic/GameRules.scala` | **Create** | `isInCheck`, `legalMoves`, `gameStatus`, `PositionStatus` enum |
| `modules/core/src/test/scala/de/nowchess/chess/logic/GameRulesTest.scala` | **Create** | Unit tests for all three `GameRules` methods |
| `modules/core/src/main/scala/de/nowchess/chess/controller/GameController.scala` | **Modify** | Add `MovedInCheck`/`Checkmate`/`Stalemate` to `MoveResult`; wire `processMove` and `gameLoop` |
| `modules/core/src/test/scala/de/nowchess/chess/controller/GameControllerTest.scala` | **Modify** | Add `processMove` and `gameLoop` tests for the three new results |

---

## Task 1: Create `GameRules` stub

**Files:**
- Create: `modules/core/src/main/scala/de/nowchess/chess/logic/GameRules.scala`

- [ ] **Step 1: Create the stub file**

```scala
package de.nowchess.chess.logic

import de.nowchess.api.board.*

enum PositionStatus:
  case Normal, InCheck, Mated, Drawn

object GameRules:

  /** True if `color`'s king is under attack on this board. */
  def isInCheck(board: Board, color: Color): Boolean = false

  /** All (from, to) moves for `color` that do not leave their own king in check. */
  def legalMoves(board: Board, color: Color): Set[(Square, Square)] = Set.empty

  /** Position status for the side whose turn it is (`color`). */
  def gameStatus(board: Board, color: Color): PositionStatus = PositionStatus.Normal
```

- [ ] **Step 2: Verify the project compiles**

```bash
./gradlew :modules:core:compileScala
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/scala/de/nowchess/chess/logic/GameRules.scala
git commit -m "feat: add GameRules stub with PositionStatus enum"
```

---

## Task 2: Write `GameRulesTest` (all tests must fail)

**Files:**
- Create: `modules/core/src/test/scala/de/nowchess/chess/logic/GameRulesTest.scala`

- [ ] **Step 1: Create the test file**

```scala
package de.nowchess.chess.logic

import de.nowchess.api.board.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameRulesTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private def board(entries: (Square, Piece)*): Board = Board(entries.toMap)

  // ──── isInCheck ──────────────────────────────────────────────────────

  test("isInCheck: king attacked by enemy rook on same rank"):
    // White King E1, Black Rook A1 — rook slides along rank 1 to E1
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R1) -> Piece.BlackRook
    )
    GameRules.isInCheck(b, Color.White) shouldBe true

  test("isInCheck: king not attacked"):
    // Black Rook A3 does not cover E1
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R3) -> Piece.BlackRook
    )
    GameRules.isInCheck(b, Color.White) shouldBe false

  test("isInCheck: no king on board returns false"):
    val b = board(sq(File.A, Rank.R1) -> Piece.BlackRook)
    GameRules.isInCheck(b, Color.White) shouldBe false

  // ──── legalMoves ─────────────────────────────────────────────────────

  test("legalMoves: move that exposes own king to rook is excluded"):
    // White King E1, White Rook E4 (pinned on E-file), Black Rook E8
    // Moving the White Rook off the E-file would expose the king
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.E, Rank.R4) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackRook
    )
    val moves = GameRules.legalMoves(b, Color.White)
    moves should not contain (sq(File.E, Rank.R4) -> sq(File.D, Rank.R4))

  test("legalMoves: move that blocks check is included"):
    // White King E1 in check from Black Rook E8; White Rook A5 can interpose on E5
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R5) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackRook
    )
    val moves = GameRules.legalMoves(b, Color.White)
    moves should contain(sq(File.A, Rank.R5) -> sq(File.E, Rank.R5))

  // ──── gameStatus ──────────────────────────────────────────────────────

  test("gameStatus: checkmate returns Mated"):
    // White Qh8, Ka6; Black Ka8
    // Qh8 attacks Ka8 along rank 8; all escape squares covered (spec-verified position)
    val b = board(
      sq(File.H, Rank.R8) -> Piece.WhiteQueen,
      sq(File.A, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    )
    GameRules.gameStatus(b, Color.Black) shouldBe PositionStatus.Mated

  test("gameStatus: stalemate returns Drawn"):
    // White Qb6, Kc6; Black Ka8
    // Black king has no legal moves and is not in check (spec-verified position)
    val b = board(
      sq(File.B, Rank.R6) -> Piece.WhiteQueen,
      sq(File.C, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    )
    GameRules.gameStatus(b, Color.Black) shouldBe PositionStatus.Drawn

  test("gameStatus: king in check with legal escape returns InCheck"):
    // White Ra8 attacks Black Ke8 along rank 8; king can escape to d7, e7, f7
    val b = board(
      sq(File.A, Rank.R8) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackKing
    )
    GameRules.gameStatus(b, Color.Black) shouldBe PositionStatus.InCheck

  test("gameStatus: normal starting position returns Normal"):
    GameRules.gameStatus(Board.initial, Color.White) shouldBe PositionStatus.Normal
```

- [ ] **Step 2: Run the tests and confirm they all fail**

```bash
./gradlew :modules:core:test --tests "de.nowchess.chess.logic.GameRulesTest"
```

Expected: all 8 tests FAIL (stubs always return `false` / `Set.empty` / `Normal`)

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/test/scala/de/nowchess/chess/logic/GameRulesTest.scala
git commit -m "test: add failing GameRulesTest for check/checkmate/stalemate"
```

---

## Task 3: Implement `GameRules`

**Files:**
- Modify: `modules/core/src/main/scala/de/nowchess/chess/logic/GameRules.scala`

- [ ] **Step 1: Replace the stub bodies with real implementations**

```scala
package de.nowchess.chess.logic

import de.nowchess.api.board.*

enum PositionStatus:
  case Normal, InCheck, Mated, Drawn

object GameRules:

  def isInCheck(board: Board, color: Color): Boolean =
    board.pieces
      .collectFirst { case (sq, Piece(`color`, PieceType.King)) => sq }
      .exists { kingSq =>
        board.pieces.exists { case (sq, piece) =>
          piece.color != color &&
          MoveValidator.legalTargets(board, sq).contains(kingSq)
        }
      }

  def legalMoves(board: Board, color: Color): Set[(Square, Square)] =
    board.pieces
      .collect { case (from, piece) if piece.color == color => from }
      .flatMap { from =>
        MoveValidator.legalTargets(board, from)
          .filter { to =>
            val (newBoard, _) = board.withMove(from, to)
            !isInCheck(newBoard, color)
          }
          .map(to => from -> to)
      }
      .toSet

  def gameStatus(board: Board, color: Color): PositionStatus =
    val moves   = legalMoves(board, color)
    val inCheck = isInCheck(board, color)
    if moves.isEmpty && inCheck then PositionStatus.Mated
    else if moves.isEmpty       then PositionStatus.Drawn
    else if inCheck             then PositionStatus.InCheck
    else                             PositionStatus.Normal
```

- [ ] **Step 2: Run the GameRules tests and confirm they all pass**

```bash
./gradlew :modules:core:test --tests "de.nowchess.chess.logic.GameRulesTest"
```

Expected: all 8 tests PASS

- [ ] **Step 3: Run the full test suite to make sure nothing regressed**

```bash
./gradlew :modules:core:test
```

Expected: `BUILD SUCCESSFUL`, all existing tests still pass

- [ ] **Step 4: Commit**

```bash
git add modules/core/src/main/scala/de/nowchess/chess/logic/GameRules.scala
git commit -m "feat: implement GameRules with isInCheck, legalMoves, gameStatus"
```

---

## Task 4: Add new `MoveResult` variants and stub `processMove`

**Files:**
- Modify: `modules/core/src/main/scala/de/nowchess/chess/controller/GameController.scala`

- [ ] **Step 1: Add three new variants to `MoveResult` and import `GameRules`**

In `GameController.scala`, update the `MoveResult` object and `processMove`. The new variants go after `Moved`. The import of `GameRules`/`PositionStatus` is added at the top. The stub `processMove` calls `GameRules.gameStatus` but always maps to `Moved` — this makes it compile while the new tests will fail:

```scala
package de.nowchess.chess.controller

import scala.io.StdIn
import de.nowchess.api.board.{Board, Color, Piece}
import de.nowchess.chess.logic.{MoveValidator, GameRules, PositionStatus}
import de.nowchess.chess.view.Renderer

// ---------------------------------------------------------------------------
// Result ADT returned by the pure processMove function
// ---------------------------------------------------------------------------

sealed trait MoveResult
object MoveResult:
  case object Quit                                                                   extends MoveResult
  case class  InvalidFormat(raw: String)                                             extends MoveResult
  case object NoPiece                                                                extends MoveResult
  case object WrongColor                                                             extends MoveResult
  case object IllegalMove                                                            extends MoveResult
  case class  Moved(newBoard: Board, captured: Option[Piece], newTurn: Color)       extends MoveResult
  case class  MovedInCheck(newBoard: Board, captured: Option[Piece], newTurn: Color) extends MoveResult
  case class  Checkmate(winner: Color)                                               extends MoveResult
  case object Stalemate                                                              extends MoveResult

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

object GameController:

  def processMove(board: Board, turn: Color, raw: String): MoveResult =
    raw.trim match
      case "quit" | "q" =>
        MoveResult.Quit
      case trimmed =>
        Parser.parseMove(trimmed) match
          case None =>
            MoveResult.InvalidFormat(trimmed)
          case Some((from, to)) =>
            board.pieceAt(from) match
              case None =>
                MoveResult.NoPiece
              case Some(piece) if piece.color != turn =>
                MoveResult.WrongColor
              case Some(_) =>
                if !MoveValidator.isLegal(board, from, to) then
                  MoveResult.IllegalMove
                else
                  val (newBoard, captured) = board.withMove(from, to)
                  MoveResult.Moved(newBoard, captured, turn.opposite) // stub — Task 6 will fix

  def gameLoop(board: Board, turn: Color): Unit =
    println()
    print(Renderer.render(board))
    println(s"${turn.label}'s turn. Enter move: ")
    val input = Option(StdIn.readLine()).getOrElse("quit").trim
    processMove(board, turn, input) match
      case MoveResult.Quit =>
        println("Game over. Goodbye!")
      case MoveResult.InvalidFormat(raw) =>
        println(s"Invalid move format '$raw'. Use coordinate notation, e.g. e2e4.")
        gameLoop(board, turn)
      case MoveResult.NoPiece =>
        println(s"No piece on ${Parser.parseMove(input).map(_._1).fold("?")(_.toString)}.")
        gameLoop(board, turn)
      case MoveResult.WrongColor =>
        println(s"That is not your piece.")
        gameLoop(board, turn)
      case MoveResult.IllegalMove =>
        println(s"Illegal move.")
        gameLoop(board, turn)
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        val prevTurn = newTurn.opposite
        captured.foreach: cap =>
          val toSq = Parser.parseMove(input).map(_._2).fold("?")(_.toString)
          println(s"${prevTurn.label} captures ${cap.color.label} ${cap.pieceType.label} on $toSq")
        gameLoop(newBoard, newTurn)
      case MoveResult.MovedInCheck(newBoard, captured, newTurn) => // stub — Task 6
        gameLoop(newBoard, newTurn)
      case MoveResult.Checkmate(winner) =>                         // stub — Task 6
        gameLoop(Board.initial, Color.White)
      case MoveResult.Stalemate =>                                 // stub — Task 6
        gameLoop(Board.initial, Color.White)
```

- [ ] **Step 2: Confirm everything still compiles and existing tests pass**

```bash
./gradlew :modules:core:test
```

Expected: `BUILD SUCCESSFUL` — existing tests still pass, no compilation errors

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/main/scala/de/nowchess/chess/controller/GameController.scala
git commit -m "feat: add MovedInCheck/Checkmate/Stalemate MoveResult variants (stub dispatch)"
```

---

## Task 5: Write new `GameControllerTest` cases (all must fail)

**Files:**
- Modify: `modules/core/src/test/scala/de/nowchess/chess/controller/GameControllerTest.scala`

- [ ] **Step 1: Append the following tests to the existing file**

Add after the last existing test (the `gameLoop: capture` test). Add the `captureOutput` helper alongside `withInput`:

```scala
  // ──── helpers ────────────────────────────────────────────────────────

  private def captureOutput(block: => Unit): String =
    val out = java.io.ByteArrayOutputStream()
    scala.Console.withOut(out)(block)
    out.toString("UTF-8")

  // ──── processMove: check / checkmate / stalemate ─────────────────────

  test("processMove: legal move that delivers check returns MovedInCheck"):
    // White Ra1, Ka3; Black Kh8 — White plays Ra1-Ra8, putting Kh8 in check
    // (Ra8 attacks along rank 8: b8..h8; king escapes to g7/g8/h7 — InCheck, not Mated)
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.A, Rank.R3) -> Piece.WhiteKing,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    ))
    GameController.processMove(b, Color.White, "a1a8") match
      case MoveResult.MovedInCheck(_, _, newTurn) => newTurn shouldBe Color.Black
      case other => fail(s"Expected MovedInCheck, got $other")

  test("processMove: legal move that results in checkmate returns Checkmate"):
    // White Qa1, Ka6; Black Ka8 — White plays Qa1-Qh8 (diagonal a1-h8)
    // After Qh8: White Qh8 + Ka6 vs Black Ka8 = checkmate (spec-verified)
    // Note: Qa1 does NOT currently attack Ka8 (path along file A is blocked by Ka6)
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteQueen,
      sq(File.A, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    GameController.processMove(b, Color.White, "a1h8") match
      case MoveResult.Checkmate(winner) => winner shouldBe Color.White
      case other => fail(s"Expected Checkmate(White), got $other")

  test("processMove: legal move that results in stalemate returns Stalemate"):
    // White Qb1, Kc6; Black Ka8 — White plays Qb1-Qb6
    // After Qb6: White Qb6 + Kc6 vs Black Ka8 = stalemate (spec-verified)
    val b = Board(Map(
      sq(File.B, Rank.R1) -> Piece.WhiteQueen,
      sq(File.C, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    GameController.processMove(b, Color.White, "b1b6") match
      case MoveResult.Stalemate => succeed
      case other => fail(s"Expected Stalemate, got $other")

  // ──── gameLoop: check / checkmate / stalemate ─────────────────────────

  test("gameLoop: checkmate prints winner message and resets to new game"):
    // Same position as checkmate processMove test above; after Qa1-Qh8 game resets
    // Second move "quit" exits the new game cleanly
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteQueen,
      sq(File.A, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    val output = captureOutput:
      withInput("a1h8\nquit\n"):
        GameController.gameLoop(b, Color.White)
    output should include("Checkmate! White wins.")

  test("gameLoop: stalemate prints draw message and resets to new game"):
    val b = Board(Map(
      sq(File.B, Rank.R1) -> Piece.WhiteQueen,
      sq(File.C, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    val output = captureOutput:
      withInput("b1b6\nquit\n"):
        GameController.gameLoop(b, Color.White)
    output should include("Stalemate! The game is a draw.")

  test("gameLoop: MovedInCheck without capture prints check message"):
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.A, Rank.R3) -> Piece.WhiteKing,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    ))
    val output = captureOutput:
      withInput("a1a8\nquit\n"):
        GameController.gameLoop(b, Color.White)
    output should include("Black is in check!")

  test("gameLoop: MovedInCheck with capture prints both capture and check message"):
    // White Rook A1 captures Black Pawn on A8, putting Black King (H8) in check
    // Ra8 attacks rank 8 → Black Kh8 is in check; king can escape to g7/g8/h7
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.A, Rank.R3) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackPawn,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    ))
    val output = captureOutput:
      withInput("a1a8\nquit\n"):
        GameController.gameLoop(b, Color.White)
    output should include("captures")
    output should include("Black is in check!")
```

- [ ] **Step 2: Run only the new tests and confirm they fail**

```bash
./gradlew :modules:core:test --tests "de.nowchess.chess.controller.GameControllerTest"
```

Expected: the 7 new tests FAIL; the existing 17 tests PASS

- [ ] **Step 3: Commit**

```bash
git add modules/core/src/test/scala/de/nowchess/chess/controller/GameControllerTest.scala
git commit -m "test: add failing GameControllerTest cases for check/checkmate/stalemate"
```

---

## Task 6: Implement `processMove` dispatch and `gameLoop` branches

**Files:**
- Modify: `modules/core/src/main/scala/de/nowchess/chess/controller/GameController.scala`

- [ ] **Step 1: Replace the stub `processMove` else-branch and the three stub `gameLoop` cases**

Replace only the `else` branch inside `processMove` (keep everything else identical):

```scala
                else
                  val (newBoard, captured) = board.withMove(from, to)
                  GameRules.gameStatus(newBoard, turn.opposite) match
                    case PositionStatus.Normal  => MoveResult.Moved(newBoard, captured, turn.opposite)
                    case PositionStatus.InCheck => MoveResult.MovedInCheck(newBoard, captured, turn.opposite)
                    case PositionStatus.Mated   => MoveResult.Checkmate(turn)
                    case PositionStatus.Drawn   => MoveResult.Stalemate
```

Replace the three stub `gameLoop` cases:

```scala
      case MoveResult.MovedInCheck(newBoard, captured, newTurn) =>
        val prevTurn = newTurn.opposite
        captured.foreach: cap =>
          val toSq = Parser.parseMove(input).map(_._2).fold("?")(_.toString)
          println(s"${prevTurn.label} captures ${cap.color.label} ${cap.pieceType.label} on $toSq")
        println(s"${newTurn.label} is in check!")
        gameLoop(newBoard, newTurn)
      case MoveResult.Checkmate(winner) =>
        println(s"Checkmate! ${winner.label} wins.")
        gameLoop(Board.initial, Color.White)
      case MoveResult.Stalemate =>
        println("Stalemate! The game is a draw.")
        gameLoop(Board.initial, Color.White)
```

- [ ] **Step 2: Run all controller tests**

```bash
./gradlew :modules:core:test --tests "de.nowchess.chess.controller.GameControllerTest"
```

Expected: all 24 tests PASS

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew :modules:core:test
```

Expected: `BUILD SUCCESSFUL`, all tests pass

- [ ] **Step 4: Commit**

```bash
git add modules/core/src/main/scala/de/nowchess/chess/controller/GameController.scala
git commit -m "feat: wire check/checkmate/stalemate into processMove and gameLoop"
```

---

## Task 7: Coverage check and final verification

- [ ] **Step 1: Run the full build with coverage**

```bash
./gradlew :modules:core:test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Check coverage gaps**

```bash
python jacoco-reporter/scoverage_coverage_gaps.py modules/core/build/reports/scoverageTest/scoverage.xml
```

Review output. If any newly added method falls below the thresholds from `CLAUDE.md` (branch ≥ 90%, line ≥ 95%, method ≥ 90%), add targeted tests to close the gaps before considering the task done.

- [ ] **Step 3: Commit coverage fixes (if any)**

```bash
git add -p
git commit -m "test: improve coverage for GameRules and GameController"
```
