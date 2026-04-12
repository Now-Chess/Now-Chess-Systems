package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.io.fen.FenParser
import de.nowchess.chess.observer.*
import de.nowchess.rules.RuleSet
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameEnginePromotionTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)

  private def captureEvents(engine: GameEngine): collection.mutable.ListBuffer[GameEvent] =
    val events = collection.mutable.ListBuffer[GameEvent]()
    engine.subscribe(new Observer { def onGameEvent(e: GameEvent): Unit = events += e })
    events

  private def engineWith(board: Board, turn: Color = Color.White): GameEngine =
    new GameEngine(initialContext = GameContext.initial.withBoard(board).withTurn(turn))

  test("processUserInput fires PromotionRequiredEvent when pawn reaches back rank") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    val events         = captureEvents(engine)

    engine.processUserInput("e7e8")

    events.exists(_.isInstanceOf[PromotionRequiredEvent]) should be(true)
    events.collect { case e: PromotionRequiredEvent => e }.head.from should be(sq(File.E, Rank.R7))
  }

  test("isPendingPromotion is true after PromotionRequired input") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    captureEvents(engine)

    engine.processUserInput("e7e8")

    engine.isPendingPromotion should be(true)
  }

  test("isPendingPromotion is false before any promotion input") {
    val engine = new GameEngine()
    engine.isPendingPromotion should be(false)
  }

  test("completePromotion fires MoveExecutedEvent with promoted piece") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    val events         = captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be(false)
    engine.board.pieceAt(sq(File.E, Rank.R8)) should be(Some(Piece(Color.White, PieceType.Queen)))
    engine.board.pieceAt(sq(File.E, Rank.R7)) should be(None)
    engine.context.moves.last.moveType shouldBe MoveType.Promotion(PromotionPiece.Queen)
    events.exists(_.isInstanceOf[MoveExecutedEvent]) should be(true)
  }

  test("completePromotion with rook underpromotion") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.completePromotion(PromotionPiece.Rook)

    engine.board.pieceAt(sq(File.E, Rank.R8)) should be(Some(Piece(Color.White, PieceType.Rook)))
  }

  test("completePromotion with no pending promotion fires InvalidMoveEvent") {
    val engine = new GameEngine()
    val events = captureEvents(engine)

    engine.completePromotion(PromotionPiece.Queen)

    events.exists(_.isInstanceOf[InvalidMoveEvent]) should be(true)
    engine.isPendingPromotion should be(false)
  }

  test("completePromotion fires CheckDetectedEvent when promotion gives check") {
    val promotionBoard = FenParser.parseBoard("3k4/4P3/8/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    val events         = captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.completePromotion(PromotionPiece.Queen)

    events.exists(_.isInstanceOf[CheckDetectedEvent]) should be(true)
  }

  test("completePromotion results in Moved when promotion doesn't give check") {
    val board  = FenParser.parseBoard("8/4P3/8/8/8/8/k7/8").get
    val engine = engineWith(board)
    val events = captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be(false)
    engine.board.pieceAt(sq(File.E, Rank.R8)) should be(Some(Piece(Color.White, PieceType.Queen)))
    events.filter(_.isInstanceOf[MoveExecutedEvent]) should not be empty
    events.exists(_.isInstanceOf[CheckDetectedEvent]) should be(false)
  }

  test("completePromotion results in Checkmate when promotion delivers checkmate") {
    val board  = FenParser.parseBoard("k7/7P/1K6/8/8/8/8/8").get
    val engine = engineWith(board)
    val events = captureEvents(engine)

    engine.processUserInput("h7h8")
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be(false)
    events.exists(_.isInstanceOf[CheckmateEvent]) should be(true)
  }

  test("completePromotion results in Stalemate when promotion creates stalemate") {
    val board  = FenParser.parseBoard("k7/1PB5/1K6/8/8/8/8/8").get
    val engine = engineWith(board)
    val events = captureEvents(engine)

    engine.processUserInput("b7b8")
    engine.completePromotion(PromotionPiece.Knight)

    engine.isPendingPromotion should be(false)
    events.exists(_.isInstanceOf[StalemateEvent]) should be(true)
  }

  test("completePromotion with black pawn promotion results in Moved") {
    val board  = FenParser.parseBoard("k7/8/8/8/8/7K/4p3/8").get
    val engine = engineWith(board, Color.Black)
    val events = captureEvents(engine)

    engine.processUserInput("e2e1")
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be(false)
    engine.board.pieceAt(sq(File.E, Rank.R1)) should be(Some(Piece(Color.Black, PieceType.Queen)))
    events.filter(_.isInstanceOf[MoveExecutedEvent]) should not be empty
    events.exists(_.isInstanceOf[CheckDetectedEvent]) should be(false)
  }

  test("completePromotion fires InvalidMoveEvent when legalMoves returns only Normal moves to back rank") {
    // Custom RuleSet: delegates all methods to StandardRules except legalMoves,
    // which strips Promotion move types and returns Normal moves instead.
    // This makes completePromotion unable to find Move(from, to, Promotion(Queen)),
    // triggering the "Error completing promotion." branch.
    val delegatingRuleSet: RuleSet = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] =
        DefaultRules.candidateMoves(context)(square)
      def legalMoves(context: GameContext)(square: Square): List[Move] =
        DefaultRules.legalMoves(context)(square).map { m =>
          m.moveType match
            case MoveType.Promotion(_) => Move(m.from, m.to, MoveType.Normal())
            case _                     => m
        }
      def allLegalMoves(context: GameContext): List[Move] =
        DefaultRules.allLegalMoves(context)
      def isCheck(context: GameContext): Boolean =
        DefaultRules.isCheck(context)
      def isCheckmate(context: GameContext): Boolean =
        DefaultRules.isCheckmate(context)
      def isStalemate(context: GameContext): Boolean =
        DefaultRules.isStalemate(context)
      def isInsufficientMaterial(context: GameContext): Boolean =
        DefaultRules.isInsufficientMaterial(context)
      def isFiftyMoveRule(context: GameContext): Boolean =
        DefaultRules.isFiftyMoveRule(context)
      def applyMove(context: GameContext)(move: Move): GameContext =
        DefaultRules.applyMove(context)(move)

    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val initialCtx     = GameContext.initial.withBoard(promotionBoard).withTurn(Color.White)
    val engine         = new GameEngine(initialCtx, delegatingRuleSet)
    val events         = captureEvents(engine)

    // isPromotionMove will fire because pawn is on rank 7 heading to rank 8,
    // and legalMoves returns Normal candidates (still non-empty) — sets pendingPromotion
    engine.processUserInput("e7e8")
    engine.isPendingPromotion should be(true)

    // completePromotion looks for Move(e7, e8, Promotion(Queen)) in legalMoves,
    // but only Normal moves exist → fires InvalidMoveEvent
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be(false)
    events.exists(_.isInstanceOf[InvalidMoveEvent]) should be(true)
    val invalidEvt = events.collect { case e: InvalidMoveEvent => e }.last
    invalidEvt.reason should include("Error completing promotion")
  }
