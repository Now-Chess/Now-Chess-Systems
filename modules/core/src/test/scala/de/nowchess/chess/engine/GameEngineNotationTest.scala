package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, File, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.io.fen.FenParser
import de.nowchess.chess.observer.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Tests that exercise moveToPgn branches not covered by other test files:
  *   - CastleQueenside (line 223)
  *   - EnPassant notation (lines 224-225) and computeCaptured EnPassant (lines 254-255)
  *   - Promotion(Bishop) notation (line 230)
  *   - King normal move notation (line 246)
  */
class GameEngineNotationTest extends AnyFunSuite with Matchers:

  private def captureEvents(engine: GameEngine): collection.mutable.ListBuffer[GameEvent] =
    val buf = collection.mutable.ListBuffer[GameEvent]()
    engine.subscribe(new Observer { def onGameEvent(e: GameEvent): Unit = buf += e })
    buf

  // ── Queenside castling (line 223) ──────────────────────────────────

  test("undo after queenside castling emits MoveUndoneEvent with O-O-O notation"):
    // FEN: White king on e1, queenside rook on a1, b1/c1/d1 clear, black king away
    val board = FenParser.parseBoard("k7/8/8/8/8/8/8/R3K3").get
    // Castling rights: white queen-side only (no king-side rook present)
    val castlingRights = de.nowchess.api.board.CastlingRights(
      whiteKingSide = false,
      whiteQueenSide = true,
      blackKingSide = false,
      blackQueenSide = false,
    )
    val ctx = GameContext.initial
      .withBoard(board)
      .withTurn(Color.White)
      .withCastlingRights(castlingRights)

    val engine = new GameEngine(ctx)
    val events = captureEvents(engine)

    // White castles queenside: e1c1
    engine.processUserInput("e1c1")
    events.exists {
      case _: MoveExecutedEvent => true
      case _                    => false
    } should be(true)

    events.clear()
    engine.undo()

    val evt = events.collect { case e: MoveUndoneEvent => e }.head
    evt.pgnNotation shouldBe "O-O-O"

  // ── En passant notation + computeCaptured (lines 224-225, 254-255) ─

  test("undo after en passant emits MoveUndoneEvent with file-x-destination notation"):
    // White pawn on e5, black pawn on d5 (just double-pushed), en passant square d6
    val board    = FenParser.parseBoard("k7/8/8/3pP3/8/8/8/7K").get
    val epSquare = Square.fromAlgebraic("d6")
    val ctx = GameContext.initial
      .withBoard(board)
      .withTurn(Color.White)
      .withEnPassantSquare(epSquare)
      .withCastlingRights(de.nowchess.api.board.CastlingRights(false, false, false, false))

    val engine = new GameEngine(ctx)
    val events = captureEvents(engine)

    // White pawn on e5 captures en passant to d6
    engine.processUserInput("e5d6")
    events.exists {
      case _: MoveExecutedEvent => true
      case _                    => false
    } should be(true)

    // Verify the captured pawn was found (computeCaptured EnPassant branch)
    val moveEvt = events.collect { case e: MoveExecutedEvent => e }.head
    moveEvt.capturedPiece shouldBe defined
    moveEvt.capturedPiece.get should include("Black")

    events.clear()
    engine.undo()

    val undoEvt = events.collect { case e: MoveUndoneEvent => e }.head
    undoEvt.pgnNotation shouldBe "exd6"

  // ── Bishop underpromotion notation (line 230) ──────────────────────

  test("undo after bishop underpromotion emits MoveUndoneEvent with =B notation"):
    // White rook on h2 keeps material sufficient (K+R+B vs K) after bishop promotion
    val board = FenParser.parseBoard("8/4P3/8/8/8/8/k6R/7K").get
    val ctx = GameContext.initial
      .withBoard(board)
      .withTurn(Color.White)
      .withCastlingRights(de.nowchess.api.board.CastlingRights(false, false, false, false))

    val engine = new GameEngine(ctx)
    val events = captureEvents(engine)

    engine.processUserInput("e7e8b")

    events.clear()
    engine.undo()

    val evt = events.collect { case e: MoveUndoneEvent => e }.head
    evt.pgnNotation shouldBe "e8=B"

  // ── King normal move notation (line 246) ───────────────────────────

  test("undo after king move emits MoveUndoneEvent with K notation"):
    // Black pawn on h7 prevents K-vs-K insufficient-material draw; white king on e1, no castling rights
    val board = FenParser.parseBoard("k7/7p/8/8/8/8/8/4K3").get
    val ctx = GameContext.initial
      .withBoard(board)
      .withTurn(Color.White)
      .withCastlingRights(de.nowchess.api.board.CastlingRights(false, false, false, false))

    val engine = new GameEngine(ctx)
    val events = captureEvents(engine)

    // King moves e1 -> f1
    engine.processUserInput("e1f1")
    events.exists {
      case _: MoveExecutedEvent => true
      case _                    => false
    } should be(true)

    events.clear()
    engine.undo()

    val evt = events.collect { case e: MoveUndoneEvent => e }.head
    evt.pgnNotation should startWith("K")
    evt.pgnNotation should include("f1")
