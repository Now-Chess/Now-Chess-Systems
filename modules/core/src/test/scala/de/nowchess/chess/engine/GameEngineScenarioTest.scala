package de.nowchess.chess.engine

import de.nowchess.api.board.{Color, File, Rank, Square, Piece}
import de.nowchess.api.game.GameContext
import de.nowchess.chess.observer.*
import de.nowchess.io.fen.FenParser
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class GameEngineScenarioTest extends AnyFunSuite with Matchers:

  // ── Observer wiring ────────────────────────────────────────────

  test("observer subscribe and unsubscribe behavior"):
    val engine = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)
    engine.processUserInput("e2e4")
    observer.hasEvent[MoveExecutedEvent] shouldBe true
    val countBeforeUnsubscribe = observer.eventCount
    engine.subscribe(observer)
    engine.unsubscribe(observer)
    engine.processUserInput("e2e4")
    observer.eventCount shouldBe countBeforeUnsubscribe

  // ── Initial state ──────────────────────────────────────────────

  test("initial engine state is standard"):
    val engine = EngineTestHelpers.makeEngine()
    engine.board.pieceAt(Square(File.E, Rank.R1)) shouldBe Some(Piece.WhiteKing)
    engine.turn shouldBe Color.White

  // ── Quit command ──────────────────────────────────────────────

  test("quit aliases and reset keep engine responsive"):
    val engine = EngineTestHelpers.makeEngine()
    engine.processUserInput("quit")
    engine.processUserInput("q")
    engine.processUserInput("e2e4")

    engine.reset()

    engine.board.pieceAt(Square(File.E, Rank.R2)) shouldBe Some(Piece.WhitePawn)
    engine.turn shouldBe Color.White

  // ── Turn toggling ──────────────────────────────────────────────

  test("turn toggles across valid move sequence"):
    val engine = EngineTestHelpers.makeEngine()
    engine.processUserInput("e2e4")
    engine.turn shouldBe Color.Black
    engine.processUserInput("e7e5")
    engine.turn shouldBe Color.White

  // ── Invalid moves (minimal) ────────────────────────────────────

  test("invalid move forms trigger InvalidMoveEvent and keep turn where relevant"):
    val engine = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    engine.processUserInput("h3h4")

    observer.hasEvent[InvalidMoveEvent] shouldBe true
    engine.turn shouldBe Color.White  // turn unchanged

    engine.processUserInput("e7e5")  // try to move black pawn on white's turn

    observer.hasEvent[InvalidMoveEvent] shouldBe true

    engine.processUserInput("e2e4")
    engine.processUserInput("e5e4")  // pawn backward

    observer.hasEvent[InvalidMoveEvent] shouldBe true

  // ── Undo/Redo ────────────────────────────────────────────────

  test("undo redo success and empty-history failures"):
    val engine = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    engine.undo()
    observer.hasEvent[InvalidMoveEvent] shouldBe true
    observer.clear()

    engine.processUserInput("e2e4")

    engine.undo()

    engine.board.pieceAt(Square(File.E, Rank.R2)) shouldBe Some(Piece.WhitePawn)
    engine.turn shouldBe Color.White

    engine.redo()

    engine.board.pieceAt(Square(File.E, Rank.R4)) shouldBe Some(Piece.WhitePawn)
    engine.turn shouldBe Color.Black
    observer.clear()
    engine.redo()
    observer.hasEvent[InvalidMoveEvent] shouldBe true

  // ── Fifty-move rule ────────────────────────────────────────────

  test("fifty-move event and draw claim success/failure"):
    val engine = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // Load FEN with half-move clock at 99
    EngineTestHelpers.loadFen(engine, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 99 1")
    observer.clear()

    // Use a legal non-pawn non-capture move so the clock increments to 100.
    engine.processUserInput("g1f3")

    observer.hasEvent[FiftyMoveRuleAvailableEvent] shouldBe true

    // Load position with sufficient move history for draw claim
    EngineTestHelpers.loadFen(engine, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 100 1")
    observer.clear()

    engine.processUserInput("draw")

    observer.hasEvent[DrawClaimedEvent] shouldBe true

    // Initial position has no draw available
    observer.clear()
    engine.reset()
    engine.processUserInput("draw")

    observer.hasEvent[InvalidMoveEvent] shouldBe true
