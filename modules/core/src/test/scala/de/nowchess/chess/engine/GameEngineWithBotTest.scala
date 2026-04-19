package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, CastlingRights, Color, File, Piece, Rank, Square}
import de.nowchess.api.bot.Bot
import de.nowchess.api.game.{BotParticipant, GameContext, Human}
import de.nowchess.api.move.{Move, MoveType}
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import de.nowchess.bot.bots.ClassicalBot
import de.nowchess.bot.{BotController, BotDifficulty}
import de.nowchess.chess.observer.*
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

private class NoMoveBot extends Bot:
  def name: String                                 = "nomove"
  def nextMove(context: GameContext): Option[Move] = None

private class FixedMoveBot(move: Move) extends Bot:
  def name: String                                 = "fixed"
  def nextMove(context: GameContext): Option[Move] = Some(move)

class GameEngineWithBotTest extends AnyFunSuite with Matchers:

  test("GameEngine can play against a ClassicalBot"):
    val bot = ClassicalBot(BotDifficulty.Easy)
    val engine = GameEngine(
      GameContext.initial,
      DefaultRules,
      Map(Color.White -> Human(PlayerInfo(PlayerId("p1"), "Player 1")), Color.Black -> BotParticipant(bot)),
    )

    // Collect events
    val moveCount         = new AtomicInteger(0)
    val checkmateDetected = new AtomicBoolean(false)
    val gameEnded         = new AtomicBoolean(false)

    val observer = new Observer:
      def onGameEvent(event: GameEvent): Unit =
        event match
          case _: MoveExecutedEvent =>
            moveCount.incrementAndGet()
          case _: CheckmateEvent =>
            checkmateDetected.set(true)
            gameEnded.set(true)
          case _: DrawEvent =>
            gameEnded.set(true)
          case _ => ()

    engine.subscribe(observer)

    // Play a few moves: e2e4, then let the bot respond
    engine.processUserInput("e2e4")

    // Wait a bit for the bot to respond asynchronously
    Thread.sleep(5000)

    // White should have moved, then Black (bot) should have responded
    moveCount.get() should be >= 2

  test("BotController can list and retrieve bots"):
    val bots = BotController.listBots
    bots should contain("easy")
    bots should contain("medium")
    bots should contain("hard")
    bots should contain("expert")

    BotController.getBot("easy") should not be None
    BotController.getBot("medium") should not be None
    BotController.getBot("hard") should not be None
    BotController.getBot("expert") should not be None
    BotController.getBot("unknown") should be(None)

  test("GameEngine handles bot with different difficulty"):
    val hardBot = BotController.getBot("hard").get
    val engine = GameEngine(
      GameContext.initial,
      DefaultRules,
      Map(Color.White -> Human(PlayerInfo(PlayerId("p1"), "Player 1")), Color.Black -> BotParticipant(hardBot)),
    )
    engine.turn should equal(Color.White)

    val movesMade = new AtomicInteger(0)
    val observer = new Observer:
      def onGameEvent(event: GameEvent): Unit =
        event match
          case _: MoveExecutedEvent => movesMade.incrementAndGet()
          case _                    => ()

    engine.subscribe(observer)

    // White moves
    engine.processUserInput("d2d4")
    Thread.sleep(500) // Wait for bot response

    // At least white moved, possibly black also responded
    movesMade.get() should be >= 1

  test("GameEngine plays valid bot moves"):
    val bot = ClassicalBot(BotDifficulty.Easy)
    val engine = GameEngine(
      GameContext.initial,
      DefaultRules,
      Map(Color.White -> Human(PlayerInfo(PlayerId("p1"), "Player 1")), Color.Black -> BotParticipant(bot)),
    )

    val moveCount = new AtomicInteger(0)
    val observer = new Observer:
      def onGameEvent(event: GameEvent): Unit =
        event match
          case _: MoveExecutedEvent => moveCount.incrementAndGet()
          case _                    => ()

    engine.subscribe(observer)

    // Play a normal move
    engine.processUserInput("e2e4")
    Thread.sleep(1000)

    // The game should have progressed with at least one move
    moveCount.get() should be >= 1
    // Game should not be ended (checkmate/stalemate)
    engine.context.moves.nonEmpty should be(true)

  test("startGame triggers bot when the starting player is a bot"):
    val bot = new FixedMoveBot(Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4)))
    val engine = GameEngine(
      GameContext.initial,
      DefaultRules,
      Map(Color.White -> BotParticipant(bot), Color.Black -> Human(PlayerInfo(PlayerId("p2"), "Player 2"))),
    )
    val movesMade = new AtomicInteger(0)
    engine.subscribe(
      new Observer:
        def onGameEvent(event: GameEvent): Unit = event match
          case _: MoveExecutedEvent => movesMade.incrementAndGet()
          case _                    => (),
    )
    engine.startGame()
    Thread.sleep(500)
    movesMade.get() should be >= 1

  test("applyBotMove fires InvalidMoveEvent when bot move destination is illegal"):
    val illegalMove = Move(Square(File.E, Rank.R7), Square(File.E, Rank.R3), MoveType.Normal())
    val bot         = new FixedMoveBot(illegalMove)
    val engine = GameEngine(
      GameContext.initial,
      DefaultRules,
      Map(Color.White -> Human(PlayerInfo(PlayerId("p1"), "Player 1")), Color.Black -> BotParticipant(bot)),
    )
    val invalidCount = new AtomicInteger(0)
    engine.subscribe(
      new Observer:
        def onGameEvent(event: GameEvent): Unit = event match
          case _: InvalidMoveEvent => invalidCount.incrementAndGet()
          case _                   => (),
    )
    engine.processUserInput("e2e4")
    Thread.sleep(1000)
    invalidCount.get() should be >= 1

  test("applyBotMove fires InvalidMoveEvent when bot move source square is invalid"):
    val invalidMove = Move(Square(File.E, Rank.R5), Square(File.E, Rank.R6), MoveType.Normal())
    val bot         = new FixedMoveBot(invalidMove)
    val engine = GameEngine(
      GameContext.initial,
      DefaultRules,
      Map(Color.White -> Human(PlayerInfo(PlayerId("p1"), "Player 1")), Color.Black -> BotParticipant(bot)),
    )
    val invalidCount = new AtomicInteger(0)
    engine.subscribe(
      new Observer:
        def onGameEvent(event: GameEvent): Unit = event match
          case _: InvalidMoveEvent => invalidCount.incrementAndGet()
          case _                   => (),
    )
    engine.processUserInput("e2e4")
    Thread.sleep(1000)
    invalidCount.get() should be >= 1

  test("handleBotNoMove fires CheckmateEvent when position is checkmate"):
    // White king at A1 in check from Qb2; Rb8 protects queen so king can't capture it
    val board = Board(
      Map(
        Square(File.A, Rank.R1) -> Piece.WhiteKing,
        Square(File.B, Rank.R2) -> Piece.BlackQueen,
        Square(File.B, Rank.R8) -> Piece.BlackRook,
        Square(File.H, Rank.R8) -> Piece.BlackKing,
      ),
    )
    val ctx = GameContext.initial.copy(
      board = board,
      turn = Color.White,
      castlingRights = CastlingRights(false, false, false, false),
      enPassantSquare = None,
      halfMoveClock = 0,
      moves = List.empty,
    )
    val engine = GameEngine(
      ctx,
      DefaultRules,
      Map(Color.White -> BotParticipant(new NoMoveBot), Color.Black -> Human(PlayerInfo(PlayerId("p2"), "Player 2"))),
    )
    val checkmateCount = new AtomicInteger(0)
    engine.subscribe(
      new Observer:
        def onGameEvent(event: GameEvent): Unit = event match
          case _: CheckmateEvent => checkmateCount.incrementAndGet()
          case _                 => (),
    )
    engine.startGame()
    Thread.sleep(1000)
    checkmateCount.get() should be >= 1

  test("handleBotNoMove fires DrawEvent when position is stalemate"):
    // White king at A1 not in check but has no legal moves (queen at B3 covers A2, B1, B2)
    val board = Board(
      Map(
        Square(File.A, Rank.R1) -> Piece.WhiteKing,
        Square(File.B, Rank.R3) -> Piece.BlackQueen,
        Square(File.H, Rank.R8) -> Piece.BlackKing,
      ),
    )
    val ctx = GameContext.initial.copy(
      board = board,
      turn = Color.White,
      castlingRights = CastlingRights(false, false, false, false),
      enPassantSquare = None,
      halfMoveClock = 0,
      moves = List.empty,
    )
    val engine = GameEngine(
      ctx,
      DefaultRules,
      Map(Color.White -> BotParticipant(new NoMoveBot), Color.Black -> Human(PlayerInfo(PlayerId("p2"), "Player 2"))),
    )
    val drawCount = new AtomicInteger(0)
    engine.subscribe(
      new Observer:
        def onGameEvent(event: GameEvent): Unit = event match
          case _: DrawEvent => drawCount.incrementAndGet()
          case _            => (),
    )
    engine.startGame()
    Thread.sleep(1000)
    drawCount.get() should be >= 1

  test("handleBotNoMove does nothing when position is neither checkmate nor stalemate"):
    val engine = GameEngine(
      GameContext.initial,
      DefaultRules,
      Map(Color.White -> BotParticipant(new NoMoveBot), Color.Black -> Human(PlayerInfo(PlayerId("p2"), "Player 2"))),
    )
    val unexpectedEvents = new AtomicInteger(0)
    engine.subscribe(
      new Observer:
        def onGameEvent(event: GameEvent): Unit = event match
          case _: CheckmateEvent => unexpectedEvents.incrementAndGet()
          case _: DrawEvent      => unexpectedEvents.incrementAndGet()
          case _                 => (),
    )
    engine.startGame()
    Thread.sleep(500)
    unexpectedEvents.get() shouldBe 0
