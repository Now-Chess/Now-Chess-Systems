package de.nowchess.ui.terminal

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.observer.*
import de.nowchess.api.board.{Board, Color, File, Rank, Square}
import de.nowchess.chess.logic.GameHistory

class TerminalUITest extends AnyFunSuite with Matchers {

  test("TerminalUI should start, print initial state, and correctly respond to 'q'") {
    val in = new ByteArrayInputStream("q\n".getBytes)
    val out = new ByteArrayOutputStream()

    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    val output = out.toString
    output should include("White's turn.")
    output should include("Game over. Goodbye!")
  }

  test("TerminalUI should ignore empty inputs and re-print prompt") {
    val in = new ByteArrayInputStream("\nq\n".getBytes)
    val out = new ByteArrayOutputStream()

    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    val output = out.toString
    // Prompt appears three times: Initial, after empty, on exit.
    output.split("White's turn.").length should be > 2
  }

  test("TerminalUI should explicitly handle empty input by re-prompting") {
    val in = new ByteArrayInputStream("\n\nq\n".getBytes)
    val out = new ByteArrayOutputStream()

    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    val output = out.toString
    // With two empty inputs, prompt should appear at least 4 times:
    // 1. Initial board display
    // 2. After first empty input
    // 3. After second empty input
    // 4. Before quit
    val promptCount = output.split("White's turn.").length
    promptCount should be >= 4
    output should include("Game over. Goodbye!")
  }

  test("TerminalUI printPrompt should include undo and redo hints if engine returns true") {
    val in = new ByteArrayInputStream("\nq\n".getBytes)
    val out = new ByteArrayOutputStream()

    val engine = new GameEngine() {
      // Stub engine to force undo/redo to true
      override def canUndo: Boolean = true
      override def canRedo: Boolean = true
    }
    
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    val output = out.toString
    output should include("[undo]")
    output should include("[redo]")
  }

  test("TerminalUI onGameEvent should properly format InvalidMoveEvent") {
    val out = new ByteArrayOutputStream()
    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withOut(out) {
      ui.onGameEvent(InvalidMoveEvent(Board(Map.empty), GameHistory(), Color.Black, "Invalid move format"))
    }

    out.toString should include("⚠️")
    out.toString should include("Invalid move format")
  }

  test("TerminalUI onGameEvent should properly format CheckDetectedEvent") {
    val out = new ByteArrayOutputStream()
    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withOut(out) {
      ui.onGameEvent(CheckDetectedEvent(Board(Map.empty), GameHistory(), Color.Black))
    }

    out.toString should include("Black is in check!")
  }

  test("TerminalUI onGameEvent should properly format CheckmateEvent") {
    val out = new ByteArrayOutputStream()
    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withOut(out) {
      ui.onGameEvent(CheckmateEvent(Board(Map.empty), GameHistory(), Color.Black, Color.White))
    }

    val ostr = out.toString
    ostr should include("Checkmate! White wins.")
  }

  test("TerminalUI onGameEvent should properly format StalemateEvent") {
    val out = new ByteArrayOutputStream()
    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withOut(out) {
      ui.onGameEvent(StalemateEvent(Board(Map.empty), GameHistory(), Color.Black))
    }

    out.toString should include("Stalemate! The game is a draw.")
  }

  test("TerminalUI onGameEvent should properly format BoardResetEvent") {
    val out = new ByteArrayOutputStream()
    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withOut(out) {
      ui.onGameEvent(BoardResetEvent(Board(Map.empty), GameHistory(), Color.White))
    }

    out.toString should include("Board has been reset to initial position.")
  }

  test("TerminalUI onGameEvent should properly format MoveExecutedEvent with capturing piece") {
    val out = new ByteArrayOutputStream()
    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withOut(out) {
      ui.onGameEvent(MoveExecutedEvent(Board(Map.empty), GameHistory(), Color.Black, "A1", "A8", Some("Knight(White)")))
    }

    out.toString should include("Captured: Knight(White) on A8") // Depending on how piece/coord serialize
  }

  test("TerminalUI processes valid move input via processUserInput") {
    val in = new ByteArrayInputStream("e2e4\nq\n".getBytes)
    val out = new ByteArrayOutputStream()

    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    val output = out.toString
    output should include("White's turn.")
    output should include("Game over. Goodbye!")
    // The move should have been processed and the board displayed
    engine.turn shouldBe Color.Black
  }

  test("TerminalUI shows promotion prompt on PromotionRequiredEvent") {
    val out = new ByteArrayOutputStream()
    val engine = new GameEngine()
    val ui = new TerminalUI(engine)

    Console.withOut(out) {
      ui.onGameEvent(PromotionRequiredEvent(
        Board(Map.empty), GameHistory(), Color.White,
        Square(File.E, Rank.R7), Square(File.E, Rank.R8)
      ))
    }

    out.toString should include("Promote to")
  }

  test("TerminalUI routes promotion choice to engine.completePromotion") {
    import de.nowchess.api.move.PromotionPiece

    var capturedPiece: Option[PromotionPiece] = None

    val engine = new GameEngine() {
      override def processUserInput(rawInput: String): Unit =
        if rawInput.trim == "e7e8" then
          notifyObservers(PromotionRequiredEvent(
            Board(Map.empty), GameHistory.empty, Color.White,
            Square(File.E, Rank.R7), Square(File.E, Rank.R8)
          ))
      override def completePromotion(piece: PromotionPiece): Unit =
        capturedPiece = Some(piece)
        notifyObservers(MoveExecutedEvent(Board(Map.empty), GameHistory.empty, Color.Black, "e7", "e8", None))
    }

    val in = new ByteArrayInputStream("e7e8\nq\nquit\n".getBytes)
    val out = new ByteArrayOutputStream()
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    capturedPiece should be(Some(PromotionPiece.Queen))
    out.toString should include("Promote to")
  }

  test("TerminalUI re-prompts on invalid promotion choice") {
    import de.nowchess.api.move.PromotionPiece

    var capturedPiece: Option[PromotionPiece] = None

    val engine = new GameEngine() {
      override def processUserInput(rawInput: String): Unit =
        if rawInput.trim == "e7e8" then
          notifyObservers(PromotionRequiredEvent(
            Board(Map.empty), GameHistory.empty, Color.White,
            Square(File.E, Rank.R7), Square(File.E, Rank.R8)
          ))
      override def completePromotion(piece: PromotionPiece): Unit =
        capturedPiece = Some(piece)
        notifyObservers(MoveExecutedEvent(Board(Map.empty), GameHistory.empty, Color.Black, "e7", "e8", None))
    }

    // "x" is invalid, then "r" for rook
    val in = new ByteArrayInputStream("e7e8\nx\nr\nquit\n".getBytes)
    val out = new ByteArrayOutputStream()
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    capturedPiece should be(Some(PromotionPiece.Rook))
    out.toString should include("Invalid")
  }

  test("TerminalUI routes Bishop promotion choice to engine.completePromotion") {
    import de.nowchess.api.move.PromotionPiece

    var capturedPiece: Option[PromotionPiece] = None

    val engine = new GameEngine() {
      override def processUserInput(rawInput: String): Unit =
        if rawInput.trim == "e7e8" then
          notifyObservers(PromotionRequiredEvent(
            Board(Map.empty), GameHistory.empty, Color.White,
            Square(File.E, Rank.R7), Square(File.E, Rank.R8)
          ))
      override def completePromotion(piece: PromotionPiece): Unit =
        capturedPiece = Some(piece)
        notifyObservers(MoveExecutedEvent(Board(Map.empty), GameHistory.empty, Color.Black, "e7", "e8", None))
    }

    val in = new ByteArrayInputStream("e7e8\nb\nquit\n".getBytes)
    val out = new ByteArrayOutputStream()
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    capturedPiece should be(Some(PromotionPiece.Bishop))
  }

  test("TerminalUI routes Knight promotion choice to engine.completePromotion") {
    import de.nowchess.api.move.PromotionPiece

    var capturedPiece: Option[PromotionPiece] = None

    val engine = new GameEngine() {
      override def processUserInput(rawInput: String): Unit =
        if rawInput.trim == "e7e8" then
          notifyObservers(PromotionRequiredEvent(
            Board(Map.empty), GameHistory.empty, Color.White,
            Square(File.E, Rank.R7), Square(File.E, Rank.R8)
          ))
      override def completePromotion(piece: PromotionPiece): Unit =
        capturedPiece = Some(piece)
        notifyObservers(MoveExecutedEvent(Board(Map.empty), GameHistory.empty, Color.Black, "e7", "e8", None))
    }

    val in = new ByteArrayInputStream("e7e8\nn\nquit\n".getBytes)
    val out = new ByteArrayOutputStream()
    val ui = new TerminalUI(engine)

    Console.withIn(in) {
      Console.withOut(out) {
        ui.start()
      }
    }

    capturedPiece should be(Some(PromotionPiece.Knight))
  }
}
