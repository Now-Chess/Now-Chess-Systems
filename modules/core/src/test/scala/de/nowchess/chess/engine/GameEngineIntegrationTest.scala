package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, File, PieceType, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.chess.observer.{GameEvent, InvalidMoveEvent, MoveRedoneEvent, Observer}
import de.nowchess.io.GameContextImport
import de.nowchess.rules.RuleSet
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameEngineIntegrationTest extends AnyFunSuite with Matchers:

  private def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(fail(s"Invalid square in test: $alg"))

  private def captureEvents(engine: GameEngine): collection.mutable.ListBuffer[GameEvent] =
    val events = collection.mutable.ListBuffer[GameEvent]()
    engine.subscribe((event: GameEvent) => events += event)
    events

  test("accessors expose redo availability and command history"):
    val engine = new GameEngine()

    engine.canRedo shouldBe false
    engine.commandHistory shouldBe empty

    engine.processUserInput("e2e4")
    engine.commandHistory.nonEmpty shouldBe true

  test("processUserInput handles undo redo empty and malformed commands"):
    val engine = new GameEngine()
    val events = captureEvents(engine)

    engine.processUserInput("")
    engine.processUserInput("oops")
    engine.processUserInput("undo")
    engine.processUserInput("redo")

    events.count { case _: InvalidMoveEvent => true; case _ => false } should be >= 3

  test("processUserInput emits Illegal move for syntactically valid but illegal target"):
    val engine = new GameEngine()
    val events = captureEvents(engine)

    engine.processUserInput("e2e5")

    events.exists {
      case InvalidMoveEvent(_, reason) => reason.contains("Illegal move")
      case _                           => false
    } shouldBe true

  test("loadGame returns Left when importer fails"):

    val engine = new GameEngine()
    val failingImporter = new GameContextImport:
      def importGameContext(input: String): Either[String, GameContext] = Left("boom")

    engine.loadGame(failingImporter, "ignored") shouldBe Left("boom")

  test("loadPosition replaces context clears history and notifies reset"):
    val engine = new GameEngine()
    val events = captureEvents(engine)

    engine.processUserInput("e2e4")
    val target = GameContext.initial.withTurn(Color.Black)
    engine.loadPosition(target)

    engine.context shouldBe target
    engine.commandHistory shouldBe empty
    events.lastOption.exists { case _: de.nowchess.chess.observer.BoardResetEvent => true; case _ => false } shouldBe true

  test("redo event includes captured piece description when replaying a capture"):
    val engine = new GameEngine()
    val events = captureEvents(engine)

    EngineTestHelpers.loadFen(engine, "4k3/8/8/8/8/8/4K3/R6r w - - 0 1")
    events.clear()

    engine.processUserInput("a1h1")
    engine.processUserInput("undo")
    engine.processUserInput("redo")

    val redo = events.collectFirst { case e: MoveRedoneEvent => e }
    redo.flatMap(_.capturedPiece) shouldBe Some("Black Rook")

  test("loadGame replay handles promotion moves when pending promotion exists"):
    val promotionMove = Move(sq("e2"), sq("e8"), MoveType.Promotion(PromotionPiece.Queen))

    val permissiveRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = legalMoves(context)(square)
      def legalMoves(context: GameContext)(square: Square): List[Move] =
        if square == sq("e2") then List(promotionMove) else List.empty
      def allLegalMoves(context: GameContext): List[Move]          = List(promotionMove)
      def isCheck(context: GameContext): Boolean                   = false
      def isCheckmate(context: GameContext): Boolean               = false
      def isStalemate(context: GameContext): Boolean               = false
      def isInsufficientMaterial(context: GameContext): Boolean    = false
      def isFiftyMoveRule(context: GameContext): Boolean           = false
      def applyMove(context: GameContext)(move: Move): GameContext = DefaultRules.applyMove(context)(move)

    val engine = new GameEngine(ruleSet = permissiveRules)
    val importer = new GameContextImport:
      def importGameContext(input: String): Either[String, GameContext] =
        Right(GameContext.initial.copy(moves = List(promotionMove)))

    engine.loadGame(importer, "ignored") shouldBe Right(())
    engine.context.moves.lastOption shouldBe Some(promotionMove)

  test("loadGame replay restores previous context when promotion cannot be completed"):
    val promotionMove = Move(sq("e2"), sq("e8"), MoveType.Promotion(PromotionPiece.Queen))
    val noLegalMoves = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = List.empty
      def legalMoves(context: GameContext)(square: Square): List[Move]     = List.empty
      def allLegalMoves(context: GameContext): List[Move]                  = List.empty
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val engine = new GameEngine(ruleSet = noLegalMoves)
    engine.processUserInput("e2e4")
    val saved = engine.context

    val importer = new GameContextImport:
      def importGameContext(input: String): Either[String, GameContext] =
        Right(GameContext.initial.copy(moves = List(promotionMove)))

    val result = engine.loadGame(importer, "ignored")

    result.isLeft shouldBe true
    result.left.toOption.get should include("Promotion required")
    engine.context shouldBe saved

  test("loadGame replay executes non-promotion moves through default replay branch"):
    val normalMove = Move(sq("e2"), sq("e4"), MoveType.Normal())
    val engine     = new GameEngine()

    engine.replayMoves(List(normalMove), engine.context) shouldBe Right(())
    engine.context.moves.lastOption shouldBe Some(normalMove)

  test("replayMoves skips later moves after the first move triggers an error"):
    val engine           = new GameEngine()
    val saved            = engine.context
    val illegalPromotion = Move(sq("e2"), sq("e1"), MoveType.Promotion(PromotionPiece.Queen))
    val trailingMove     = Move(sq("e2"), sq("e4"))

    engine.replayMoves(List(illegalPromotion, trailingMove), saved) shouldBe Left("Promotion required for move e2e1")
    engine.context shouldBe saved

  test("normalMoveNotation handles missing source piece"):
    val engine = new GameEngine()
    val result = engine.normalMoveNotation(Move(sq("e3"), sq("e4")), Board.initial, isCapture = false)

    result shouldBe "e4"

  test("pieceNotation default branch returns empty string"):
    val engine = new GameEngine()
    val result = engine.pieceNotation(PieceType.Pawn)

    result shouldBe ""

  test("observerCount reflects subscribe and unsubscribe operations"):
    val engine = new GameEngine()
    val observer = new Observer:
      def onGameEvent(event: GameEvent): Unit = ()

    engine.observerCount shouldBe 0
    engine.subscribe(observer)
    engine.observerCount shouldBe 1
    engine.unsubscribe(observer)
    engine.observerCount shouldBe 0
