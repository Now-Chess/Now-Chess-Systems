package de.nowchess.chess.controller

import de.nowchess.api.board.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream

class GameControllerTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private val initial = Board.initial

  // ──── processMove ────────────────────────────────────────────────────

  test("processMove: 'quit' input returns Quit"):
    GameController.processMove(initial, Color.White, "quit") shouldBe MoveResult.Quit

  test("processMove: 'q' input returns Quit"):
    GameController.processMove(initial, Color.White, "q") shouldBe MoveResult.Quit

  test("processMove: quit with surrounding whitespace returns Quit"):
    GameController.processMove(initial, Color.White, "  quit  ") shouldBe MoveResult.Quit

  test("processMove: unparseable input returns InvalidFormat"):
    GameController.processMove(initial, Color.White, "xyz") shouldBe MoveResult.InvalidFormat("xyz")

  test("processMove: valid format but empty square returns NoPiece"):
    // E3 is empty in the initial position
    GameController.processMove(initial, Color.White, "e3e4") shouldBe MoveResult.NoPiece

  test("processMove: piece of wrong color returns WrongColor"):
    // E7 has a Black pawn; it is White's turn
    GameController.processMove(initial, Color.White, "e7e6") shouldBe MoveResult.WrongColor

  test("processMove: geometrically illegal move returns IllegalMove"):
    // White pawn at E2 cannot jump three squares to E5
    GameController.processMove(initial, Color.White, "e2e5") shouldBe MoveResult.IllegalMove

  test("processMove: legal pawn move returns Moved with updated board and flipped turn"):
    GameController.processMove(initial, Color.White, "e2e4") match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        newBoard.pieceAt(sq(File.E, Rank.R4)) shouldBe Some(Piece.WhitePawn)
        newBoard.pieceAt(sq(File.E, Rank.R2)) shouldBe None
        captured shouldBe None
        newTurn shouldBe Color.Black
      case other => fail(s"Expected Moved, got $other")

  test("processMove: legal capture returns Moved with the captured piece"):
    val captureBoard = Board(Map(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R6) -> Piece.BlackPawn
    ))
    GameController.processMove(captureBoard, Color.White, "e5d6") match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        captured shouldBe Some(Piece.BlackPawn)
        newBoard.pieceAt(sq(File.D, Rank.R6)) shouldBe Some(Piece.WhitePawn)
        newTurn shouldBe Color.Black
      case other => fail(s"Expected Moved, got $other")

  // ──── gameLoop ───────────────────────────────────────────────────────

  private def withInput(input: String)(block: => Unit): Unit =
    val stream = ByteArrayInputStream(input.getBytes("UTF-8"))
    scala.Console.withIn(stream)(scala.Console.withOut(System.out)(block))

  test("gameLoop: 'quit' exits cleanly without exception"):
    withInput("quit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: EOF (null readLine) exits via quit fallback"):
    withInput(""):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: invalid format prints message and recurses until quit"):
    withInput("badmove\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: NoPiece prints message and recurses until quit"):
    // E3 is empty in the initial position
    withInput("e3e4\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: WrongColor prints message and recurses until quit"):
    // E7 has a Black pawn; it is White's turn
    withInput("e7e6\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: IllegalMove prints message and recurses until quit"):
    withInput("e2e5\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: legal non-capture move recurses with new board then quits"):
    withInput("e2e4\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: capture move prints capture message then recurses and quits"):
    val captureBoard = Board(Map(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R6) -> Piece.BlackPawn
    ))
    withInput("e5d6\nquit\n"):
      GameController.gameLoop(captureBoard, Color.White)
