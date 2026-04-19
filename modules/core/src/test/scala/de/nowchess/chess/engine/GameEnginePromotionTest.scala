package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.game.{DrawReason, GameContext}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.io.fen.FenParser
import de.nowchess.chess.observer.{
  CheckDetectedEvent,
  CheckmateEvent,
  DrawEvent,
  GameEvent,
  InvalidMoveEvent,
  InvalidMoveReason,
  MoveExecutedEvent,
  Observer,
}
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

  test("processUserInput without promotion suffix fires InvalidMoveEvent when pawn reaches back rank") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    val events         = captureEvents(engine)

    engine.processUserInput("e7e8")

    events.exists {
      case InvalidMoveEvent(_, InvalidMoveReason.PromotionPieceRequired) => true
      case _                                                             => false
    } should be(true)
  }

  test("processUserInput with queen promotion fires MoveExecutedEvent and places queen") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    val events         = captureEvents(engine)

    engine.processUserInput("e7e8q")

    engine.board.pieceAt(sq(File.E, Rank.R8)) should be(Some(Piece(Color.White, PieceType.Queen)))
    engine.board.pieceAt(sq(File.E, Rank.R7)) should be(None)
    engine.context.moves.last.moveType shouldBe MoveType.Promotion(PromotionPiece.Queen)
    events.exists {
      case _: MoveExecutedEvent => true
      case _                    => false
    } should be(true)
  }

  test("processUserInput with rook underpromotion places rook") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    captureEvents(engine)

    engine.processUserInput("e7e8r")

    engine.board.pieceAt(sq(File.E, Rank.R8)) should be(Some(Piece(Color.White, PieceType.Rook)))
  }

  test("processUserInput with bishop underpromotion places bishop") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    captureEvents(engine)

    engine.processUserInput("e7e8b")

    engine.board.pieceAt(sq(File.E, Rank.R8)) should be(Some(Piece(Color.White, PieceType.Bishop)))
  }

  test("processUserInput with knight underpromotion places knight") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    captureEvents(engine)

    engine.processUserInput("e7e8n")

    engine.board.pieceAt(sq(File.E, Rank.R8)) should be(Some(Piece(Color.White, PieceType.Knight)))
  }

  test("processUserInput e7e8q fires CheckDetectedEvent when promotion gives check") {
    val promotionBoard = FenParser.parseBoard("3k4/4P3/8/8/8/8/8/8").get
    val engine         = engineWith(promotionBoard)
    val events         = captureEvents(engine)

    engine.processUserInput("e7e8q")

    events.exists {
      case _: CheckDetectedEvent => true
      case _                     => false
    } should be(true)
  }

  test("processUserInput e7e8q does not fire CheckDetectedEvent when promotion doesn't give check") {
    val board  = FenParser.parseBoard("8/4P3/8/8/8/8/k7/8").get
    val engine = engineWith(board)
    val events = captureEvents(engine)

    engine.processUserInput("e7e8q")

    engine.board.pieceAt(sq(File.E, Rank.R8)) should be(Some(Piece(Color.White, PieceType.Queen)))
    events.filter {
      case _: MoveExecutedEvent => true
      case _                    => false
    } should not be empty
    events.exists {
      case _: CheckDetectedEvent => true
      case _                     => false
    } should be(false)
  }

  test("processUserInput h7h8q fires CheckmateEvent when promotion delivers checkmate") {
    val board  = FenParser.parseBoard("k7/7P/1K6/8/8/8/8/8").get
    val engine = engineWith(board)
    val events = captureEvents(engine)

    engine.processUserInput("h7h8q")

    events.exists {
      case _: CheckmateEvent => true
      case _                 => false
    } should be(true)
  }

  test("processUserInput b7b8n fires DrawEvent with Stalemate when promotion creates stalemate") {
    val board  = FenParser.parseBoard("k7/1PB5/1K6/8/8/8/8/8").get
    val engine = engineWith(board)
    val events = captureEvents(engine)

    engine.processUserInput("b7b8n")

    events.exists {
      case DrawEvent(_, DrawReason.Stalemate) => true
      case _                                  => false
    } should be(true)
  }

  test("processUserInput e2e1q with black pawn promotes to queen") {
    val board  = FenParser.parseBoard("k7/8/8/8/8/7K/4p3/8").get
    val engine = engineWith(board, Color.Black)
    val events = captureEvents(engine)

    engine.processUserInput("e2e1q")

    engine.board.pieceAt(sq(File.E, Rank.R1)) should be(Some(Piece(Color.Black, PieceType.Queen)))
    events.filter {
      case _: MoveExecutedEvent => true
      case _                    => false
    } should not be empty
    events.exists {
      case _: CheckDetectedEvent => true
      case _                     => false
    } should be(false)
  }

  test("processUserInput fires InvalidMoveEvent when promotion piece has no matching legal move") {
    // Custom RuleSet: strips Promotion move types and returns Normal moves instead,
    // so Move(e7, e8, Promotion(Queen)) is not in legal moves — triggers error branch.
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
      def isThreefoldRepetition(context: GameContext): Boolean =
        DefaultRules.isThreefoldRepetition(context)
      def applyMove(context: GameContext)(move: Move): GameContext =
        DefaultRules.applyMove(context)(move)

    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val initialCtx     = GameContext.initial.withBoard(promotionBoard).withTurn(Color.White)
    val engine         = new GameEngine(initialCtx, delegatingRuleSet)
    val events         = captureEvents(engine)

    // legalMoves returns Normal candidates (non-empty) but no Promotion(Queen) move
    engine.processUserInput("e7e8q")

    events.exists {
      case _: InvalidMoveEvent => true
      case _                   => false
    } should be(true)
    val invalidEvt = events.collect { case e: InvalidMoveEvent => e }.last
    invalidEvt.reason shouldBe InvalidMoveReason.PromotionPieceInvalid
  }
