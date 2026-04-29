package de.nowchess.chess.engine

import de.nowchess.api.board.Color
import de.nowchess.api.game.WinReason.Checkmate
import de.nowchess.api.game.{DrawReason, GameResult}
import de.nowchess.chess.observer.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameEngineOutcomesTest extends AnyFunSuite with Matchers:

  // ── Checkmate ───────────────────────────────────────────────────

  test("checkmate ends game with CheckmateEvent"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    engine.processUserInput("f2f3")
    engine.processUserInput("e7e5")
    engine.processUserInput("g2g4")
    observer.clear()

    engine.processUserInput("d8h4")

    observer.hasEvent[CheckmateEvent] shouldBe true
    engine.context.result shouldBe Some(GameResult.Win(Color.Black, Checkmate))

  test("checkmate with white winner"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    engine.processUserInput("f1c4")
    engine.processUserInput("b8c6")
    engine.processUserInput("d1h5")
    engine.processUserInput("g8f6")
    observer.clear()

    engine.processUserInput("h5f7")

    val evt = observer.getEvent[CheckmateEvent]
    evt.isDefined shouldBe true
    evt.get.winner shouldBe Color.White
    engine.context.result shouldBe Some(GameResult.Win(Color.White, Checkmate))

  // ── Stalemate ───────────────────────────────────────────────────

  test("stalemate ends game with DrawEvent(Stalemate)"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    val moves = List(
      "e2e3",
      "a7a5",
      "d1h5",
      "a8a6",
      "h5a5",
      "h7h5",
      "h2h4",
      "a6h6",
      "a5c7",
      "f7f6",
      "c7d7",
      "e8f7",
      "d7b7",
      "d8d3",
      "b7b8",
      "d3h7",
      "b8c8",
      "f7g6",
    )
    moves.foreach(engine.processUserInput)
    observer.clear()

    engine.processUserInput("c8e6")

    val evt = observer.getEvent[DrawEvent]
    evt.isDefined shouldBe true
    evt.get.reason shouldBe DrawReason.Stalemate
    engine.context.result shouldBe Some(GameResult.Draw(DrawReason.Stalemate))

  test("stalemate board is not reset after draw"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    val moves = List(
      "e2e3",
      "a7a5",
      "d1h5",
      "a8a6",
      "h5a5",
      "h7h5",
      "h2h4",
      "a6h6",
      "a5c7",
      "f7f6",
      "c7d7",
      "e8f7",
      "d7b7",
      "d8d3",
      "b7b8",
      "d3h7",
      "b8c8",
      "f7g6",
      "c8e6",
    )

    moves.foreach(engine.processUserInput)

    observer.hasEvent[DrawEvent] shouldBe true
    engine.turn shouldBe Color.Black

  // ── Check detection ────────────────────────────────────────────

  test("check detected after move puts king in check"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    engine.processUserInput("f1c4")
    engine.processUserInput("g8f6")
    observer.clear()

    engine.processUserInput("c4f7")

    observer.hasEvent[CheckDetectedEvent] shouldBe true

  test("check by knight"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // White has K+N+Q so the position is not insufficient material after Nd4f5
    EngineTestHelpers.loadFen(engine, "8/4k3/8/8/3N4/8/8/3QK3 w - - 0 1")
    observer.clear()

    engine.processUserInput("d4f5")

    observer.hasEvent[CheckDetectedEvent] shouldBe true

  // ── Fifty-move rule ────────────────────────────────────────────

  test("fifty-move rule triggers when half-move clock reaches 100"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    EngineTestHelpers.loadFen(engine, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 99 1")
    observer.clear()

    engine.processUserInput("g1f3")

    observer.hasEvent[FiftyMoveRuleAvailableEvent] shouldBe true

  test("fifty-move rule clock resets on pawn move"):
    val engine = EngineTestHelpers.makeEngine()

    EngineTestHelpers.loadFen(engine, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 50 1")
    engine.processUserInput("a2a3")

    // Clock should reset to 0 after pawn move
    engine.context.halfMoveClock shouldBe 0

  test("fifty-move rule clock resets on capture"):
    val engine = EngineTestHelpers.makeEngine()

    // FEN: white pawn on e5, black pawn on d6, clock at 50
    EngineTestHelpers.loadFen(engine, "4k3/8/3p4/4P3/8/8/8/4K3 w - - 50 1")
    engine.processUserInput("e5d6")

    // Clock should reset to 0 after capture
    engine.context.halfMoveClock shouldBe 0

  // ── Draw claim ────────────────────────────────────────────────

  test("draw can be claimed when fifty-move rule is available"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    EngineTestHelpers.loadFen(engine, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 100 1")
    observer.clear()

    engine.processUserInput("draw")

    val evt = observer.getEvent[DrawEvent]
    evt.isDefined shouldBe true
    evt.get.reason shouldBe DrawReason.FiftyMoveRule
    engine.context.result shouldBe Some(GameResult.Draw(DrawReason.FiftyMoveRule))

  test("draw cannot be claimed when not available"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    engine.processUserInput("draw")

    observer.hasEvent[InvalidMoveEvent] shouldBe true

  // ── Insufficient material ──────────────────────────────────────────

  test("insufficient material fires DrawEvent(InsufficientMaterial) after capture"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // White Bishop d4 captures Black Rook g7, leaving K+B vs K (insufficient material).
    // Black king on g8 can still move (f7/h7 not controlled), so it is not stalemate.
    EngineTestHelpers.loadFen(engine, "6k1/6r1/8/8/3B4/8/8/K7 w - - 0 1")
    observer.clear()

    engine.processUserInput("d4g7")

    val evt = observer.getEvent[DrawEvent]
    evt.isDefined shouldBe true
    evt.get.reason shouldBe DrawReason.InsufficientMaterial
    engine.context.result shouldBe Some(GameResult.Draw(DrawReason.InsufficientMaterial))

  // ── Threefold Repetition ──────────────────────────────────────────

  test("draw command rejected when neither 50-move rule nor threefold repetition available"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    engine.processUserInput("e2e4")
    observer.clear()

    engine.processUserInput("draw")

    observer.hasEvent[InvalidMoveEvent] shouldBe true

  test("threefold repetition fires ThreefoldRepetitionAvailableEvent after 8-move shuffle"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // Both knights shuffle home: initial position occurs 3 times on move 8 (Ng8)
    engine.processUserInput("g1f3")
    engine.processUserInput("g8f6")
    engine.processUserInput("f3g1")
    engine.processUserInput("f6g8")
    engine.processUserInput("g1f3")
    engine.processUserInput("g8f6")
    engine.processUserInput("f3g1")
    observer.clear()

    engine.processUserInput("f6g8") // 3rd occurrence of initial position

    observer.hasEvent[ThreefoldRepetitionAvailableEvent] shouldBe true
    engine.context.result shouldBe None // claimable, not automatic

  test("draw claim via threefold repetition ends game with DrawEvent"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    engine.processUserInput("g1f3")
    engine.processUserInput("g8f6")
    engine.processUserInput("f3g1")
    engine.processUserInput("f6g8")
    engine.processUserInput("g1f3")
    engine.processUserInput("g8f6")
    engine.processUserInput("f3g1")
    engine.processUserInput("f6g8") // threefold now available

    observer.clear()
    engine.processUserInput("draw")

    val evt = observer.getEvent[DrawEvent]
    evt.isDefined shouldBe true
    evt.get.reason shouldBe DrawReason.ThreefoldRepetition
    engine.context.result shouldBe Some(GameResult.Draw(DrawReason.ThreefoldRepetition))

  test("loadPosition with non-empty moves preserves context as-is"):
    val engine   = EngineTestHelpers.makeEngine()
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)

    // Build a context that already has a move in its history
    val move = de.nowchess.api.move.Move(
      de.nowchess.api.board.Square(de.nowchess.api.board.File.E, de.nowchess.api.board.Rank.R2),
      de.nowchess.api.board.Square(de.nowchess.api.board.File.E, de.nowchess.api.board.Rank.R4),
    )
    val ctxWithMove = de.nowchess.api.game.GameContext.initial.withMove(move)

    engine.loadPosition(ctxWithMove)

    observer.hasEvent[BoardResetEvent] shouldBe true
