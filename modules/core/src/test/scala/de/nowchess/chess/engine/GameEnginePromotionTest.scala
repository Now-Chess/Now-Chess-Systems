package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.move.PromotionPiece
import de.nowchess.chess.logic.GameHistory
import de.nowchess.chess.notation.FenParser
import de.nowchess.chess.observer.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameEnginePromotionTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)

  private def captureEvents(engine: GameEngine): collection.mutable.ListBuffer[GameEvent] =
    val events = collection.mutable.ListBuffer[GameEvent]()
    engine.subscribe(new Observer { def onGameEvent(e: GameEvent): Unit = events += e })
    events

  test("processUserInput fires PromotionRequiredEvent when pawn reaches back rank") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine = new GameEngine(initialBoard = promotionBoard)
    val events = captureEvents(engine)

    engine.processUserInput("e7e8")

    events.exists(_.isInstanceOf[PromotionRequiredEvent]) should be (true)
    events.collect { case e: PromotionRequiredEvent => e }.head.from should be (sq(File.E, Rank.R7))
  }

  test("isPendingPromotion is true after PromotionRequired input") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine = new GameEngine(initialBoard = promotionBoard)
    captureEvents(engine)

    engine.processUserInput("e7e8")

    engine.isPendingPromotion should be (true)
  }

  test("isPendingPromotion is false before any promotion input") {
    val engine = new GameEngine()
    engine.isPendingPromotion should be (false)
  }

  test("completePromotion fires MoveExecutedEvent with promoted piece") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine = new GameEngine(initialBoard = promotionBoard)
    val events = captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be (false)
    engine.board.pieceAt(sq(File.E, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Queen)))
    engine.board.pieceAt(sq(File.E, Rank.R7)) should be (None)
    engine.history.moves.head.promotionPiece should be (Some(PromotionPiece.Queen))
    events.exists(_.isInstanceOf[MoveExecutedEvent]) should be (true)
  }

  test("completePromotion with rook underpromotion") {
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val engine = new GameEngine(initialBoard = promotionBoard)
    captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.completePromotion(PromotionPiece.Rook)

    engine.board.pieceAt(sq(File.E, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Rook)))
  }

  test("completePromotion with no pending promotion fires InvalidMoveEvent") {
    val engine = new GameEngine()
    val events = captureEvents(engine)

    engine.completePromotion(PromotionPiece.Queen)

    events.exists(_.isInstanceOf[InvalidMoveEvent]) should be (true)
    engine.isPendingPromotion should be (false)
  }

  test("completePromotion fires CheckDetectedEvent when promotion gives check") {
    val promotionBoard = FenParser.parseBoard("3k4/4P3/8/8/8/8/8/8").get
    val engine = new GameEngine(initialBoard = promotionBoard)
    val events = captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.completePromotion(PromotionPiece.Queen)

    events.exists(_.isInstanceOf[CheckDetectedEvent]) should be (true)
  }

  test("completePromotion results in Moved when promotion doesn't give check") {
    // White pawn on e7, black king on a2 (far away, not in check after promotion)
    val board = FenParser.parseBoard("8/4P3/8/8/8/8/k7/8").get
    val engine = new GameEngine(initialBoard = board)
    val events = captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be (false)
    engine.board.pieceAt(sq(File.E, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Queen)))
    events.filter(_.isInstanceOf[MoveExecutedEvent]) should not be empty
    events.exists(_.isInstanceOf[CheckDetectedEvent]) should be (false)
  }

  test("completePromotion results in Checkmate when promotion delivers checkmate") {
    // Black king on a8, white king on b6, white pawn on h7
    // h7->h8=Q delivers checkmate
    val board = FenParser.parseBoard("k7/7P/1K6/8/8/8/8/8").get
    val engine = new GameEngine(initialBoard = board)
    val events = captureEvents(engine)

    engine.processUserInput("h7h8")
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be (false)
    events.exists(_.isInstanceOf[CheckmateEvent]) should be (true)
  }

  test("completePromotion results in Stalemate when promotion creates stalemate") {
    // Black king on a8, white pawn on b7, white bishop on c7, white king on b6
    // b7->b8=N: no check; Ka8 has no legal moves -> stalemate
    val board = FenParser.parseBoard("k7/1PB5/1K6/8/8/8/8/8").get
    val engine = new GameEngine(initialBoard = board)
    val events = captureEvents(engine)

    engine.processUserInput("b7b8")
    engine.completePromotion(PromotionPiece.Knight)

    engine.isPendingPromotion should be (false)
    events.exists(_.isInstanceOf[StalemateEvent]) should be (true)
  }

  test("completePromotion with black pawn promotion results in Moved") {
    // Black pawn e2, white king h3 (not on rank 1 or file e), black king a8
    // e2->e1=Q: queen on e1 does not attack h3 -> normal Moved
    val board = FenParser.parseBoard("k7/8/8/8/8/7K/4p3/8").get
    val engine = new GameEngine(initialBoard = board, initialTurn = Color.Black)
    val events = captureEvents(engine)

    engine.processUserInput("e2e1")
    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be (false)
    engine.board.pieceAt(sq(File.E, Rank.R1)) should be (Some(Piece(Color.Black, PieceType.Queen)))
    events.filter(_.isInstanceOf[MoveExecutedEvent]) should not be empty
    events.exists(_.isInstanceOf[CheckDetectedEvent]) should be (false)
  }

  test("completePromotion catch-all fires InvalidMoveEvent for unexpected MoveResult") {
    // Inject a function that returns an unexpected MoveResult to hit the catch-all case
    val promotionBoard = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val stubFn: (de.nowchess.api.board.Board, de.nowchess.chess.logic.GameHistory, Square, Square, PromotionPiece, Color) => de.nowchess.chess.controller.MoveResult =
      (_, _, _, _, _, _) => de.nowchess.chess.controller.MoveResult.NoPiece
    val engine = new GameEngine(initialBoard = promotionBoard, completePromotionFn = stubFn)
    val events = captureEvents(engine)

    engine.processUserInput("e7e8")
    engine.isPendingPromotion should be (true)

    engine.completePromotion(PromotionPiece.Queen)

    engine.isPendingPromotion should be (false)
    events.exists(_.isInstanceOf[InvalidMoveEvent]) should be (true)
  }
