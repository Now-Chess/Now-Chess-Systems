package de.nowchess.chess.engine

import de.nowchess.api.board.Color
import de.nowchess.chess.observer.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameEngineSpecialMovesTest extends AnyFunSuite with Matchers:

  // ── Castling ────────────────────────────────────────────────────

  test("kingside castling executes successfully"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // FEN: white king on e1, rook on h1, f1/g1 clear
    EngineTestHelpers.loadFen(engine, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQK2R w KQkq - 0 1")
    observer.clear()

    engine.processUserInput("e1g1")

    observer.hasEvent[MoveExecutedEvent] shouldBe true
    engine.turn shouldBe Color.Black

  test("queenside castling executes successfully"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // FEN: white king on e1, rook on a1, b1/c1/d1 clear
    EngineTestHelpers.loadFen(engine, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1")
    observer.clear()

    engine.processUserInput("e1c1")

    observer.hasEvent[MoveExecutedEvent] shouldBe true
    engine.turn shouldBe Color.Black

  test("undo castling emits PGN notation"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    EngineTestHelpers.loadFen(engine, "k7/8/8/8/8/8/8/R3K3 w Q - 0 1")
    observer.clear()

    engine.processUserInput("e1c1")
    observer.clear()
    engine.undo()

    val evt = observer.getEvent[MoveUndoneEvent]
    evt.isDefined shouldBe true
    evt.get.pgnNotation shouldBe "O-O-O"

  // ── En passant ──────────────────────────────────────────────────

  test("en passant capture executes successfully"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // FEN: white pawn e5, black pawn d5 (just pushed), en passant square d6
    EngineTestHelpers.loadFen(engine, "k7/8/8/3pP3/8/8/8/7K w - d6 0 1")
    observer.clear()

    engine.processUserInput("e5d6")

    observer.hasEvent[MoveExecutedEvent] shouldBe true
    val moveEvt = observer.getEvent[MoveExecutedEvent]
    moveEvt.get.capturedPiece shouldBe defined // pawn was captured

  test("undo en passant emits file-x-destination notation"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    EngineTestHelpers.loadFen(engine, "k7/8/8/3pP3/8/8/8/7K w - d6 0 1")
    observer.clear()

    engine.processUserInput("e5d6")
    observer.clear()
    engine.undo()

    val evt = observer.getEvent[MoveUndoneEvent]
    evt.isDefined shouldBe true
    evt.get.pgnNotation shouldBe "exd6"

  // ── Pawn promotion ─────────────────────────────────────────────

  test("pawn reaching back rank without promotion suffix fires InvalidMoveEvent"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    EngineTestHelpers.loadFen(engine, "8/4P3/4k3/8/8/8/8/8 w - - 0 1")
    observer.clear()

    engine.processUserInput("e7e8")

    observer.hasEvent[InvalidMoveEvent] shouldBe true

  test("e7e8q promotes to Queen"):
    val engine = EngineTestHelpers.makeEngine()

    EngineTestHelpers.loadFen(engine, "8/4P3/8/8/8/8/k7/8 w - - 0 1")
    engine.processUserInput("e7e8q")

    engine.turn shouldBe Color.Black

  test("e7e8r promotes to Rook"):
    val engine = EngineTestHelpers.makeEngine()

    EngineTestHelpers.loadFen(engine, "8/4P3/8/8/8/8/k7/8 w - - 0 1")
    engine.processUserInput("e7e8r")

    engine.turn shouldBe Color.Black

  test("e7e8b promotes to Bishop"):
    val engine = EngineTestHelpers.makeEngine()

    EngineTestHelpers.loadFen(engine, "8/4P3/8/8/8/8/k7/8 w - - 0 1")
    engine.processUserInput("e7e8b")

    engine.turn shouldBe Color.Black

  test("e7e8n promotes to Knight"):
    val engine = EngineTestHelpers.makeEngine()

    EngineTestHelpers.loadFen(engine, "8/4P3/8/8/8/8/k7/8 w - - 0 1")
    engine.processUserInput("e7e8n")

    engine.turn shouldBe Color.Black

  test("promotion with discovered check emits CheckDetectedEvent"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    EngineTestHelpers.loadFen(engine, "8/4P3/4k3/8/8/8/8/4K3 w - - 0 1")
    observer.clear()

    engine.processUserInput("e7e8q")

    observer.hasEvent[CheckDetectedEvent] shouldBe true

  test("promotion with checkmate emits CheckmateEvent"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    EngineTestHelpers.loadFen(engine, "k7/7P/1K6/8/8/8/8/8 w - - 0 1")
    observer.clear()

    engine.processUserInput("h7h8q")

    observer.hasEvent[CheckmateEvent] shouldBe true

  test("undo promotion emits notation with piece suffix"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // White rook on h2 keeps material sufficient (K+B+R vs K) after bishop promotion
    EngineTestHelpers.loadFen(engine, "8/4P3/4k3/8/8/8/7R/7K w - - 0 1")
    engine.processUserInput("e7e8b")
    observer.clear()

    engine.undo()

    val evt = observer.getEvent[MoveUndoneEvent]
    evt.isDefined shouldBe true
    evt.get.pgnNotation shouldBe "e8=B"

  test("black pawn e2e1q promotes to queen"):
    val engine = EngineTestHelpers.makeEngine()

    EngineTestHelpers.loadFen(engine, "8/8/8/8/8/4k3/4p3/8 b - - 0 1")
    engine.processUserInput("e2e1q")

    engine.turn shouldBe Color.White

  // ── Promotion capturing ────────────────────────────────────────

  test("pawn promotion with capture executes"):
    val engine = EngineTestHelpers.makeEngine()

    EngineTestHelpers.loadFen(engine, "3n4/4P3/4k3/8/8/8/8/4K3 w - - 0 1")
    engine.processUserInput("e7d8q")

    engine.turn shouldBe Color.Black
