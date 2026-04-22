package de.nowchess.bot

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType}
import de.nowchess.bot.ai.Evaluation
import de.nowchess.bot.bots.HybridBot
import de.nowchess.bot.util.{PolyglotBook, PolyglotHash}
import de.nowchess.api.rules.RuleSet
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{DataOutputStream, FileOutputStream}
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Using

class HybridBotTest extends AnyFunSuite with Matchers:

  test("HybridBot name includes difficulty"):
    val bot = HybridBot(BotDifficulty.Easy)
    bot.name should include("HybridBot")
    bot.name should include("Easy")

  test("HybridBot nextMove returns a move on the initial position"):
    val bot  = HybridBot(BotDifficulty.Easy)
    val move = bot.nextMove(GameContext.initial)
    move should not be None

  test("HybridBot nextMove returns None when no legal moves"):
    val noMovesRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = Nil
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = true
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context
    val bot  = HybridBot(BotDifficulty.Easy, noMovesRules)
    val move = bot.nextMove(GameContext.initial)
    move should be(None)

  test("HybridBot with empty book falls through to search"):
    val emptyBook = PolyglotBook("/nonexistent/book.bin")
    val bot       = HybridBot(BotDifficulty.Easy, book = Some(emptyBook))
    val move      = bot.nextMove(GameContext.initial)
    move should not be None

  test("HybridBot skips move repeated three times"):
    val repeatedMove = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())
    val onlyMoveRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = List(repeatedMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context
    val ctx = GameContext.initial.copy(moves = List(repeatedMove, repeatedMove, repeatedMove))
    val bot = HybridBot(BotDifficulty.Easy, onlyMoveRules)
    bot.nextMove(ctx) should be(None)

  test("HybridBot uses book move when available"):
    val tempFile = Files.createTempFile("hybrid_book", ".bin")
    try
      val ctx         = GameContext.initial
      val hash        = PolyglotHash.hash(ctx)
      val e2e4: Short = (4 | (3 << 3) | (4 << 6) | (1 << 9)).toShort

      Using(DataOutputStream(FileOutputStream(tempFile.toFile))) { dos =>
        dos.writeLong(hash)
        dos.writeShort(e2e4)
        dos.writeShort(100)
        dos.writeInt(0)
      }.get

      val book = PolyglotBook(tempFile.toString)
      val bot  = HybridBot(BotDifficulty.Easy, book = Some(book))
      bot.nextMove(ctx) should be(Some(Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())))
    finally Files.deleteIfExists(tempFile)

  test("HybridBot reports veto when classical and NNUE differ above threshold"):
    val forcedMove = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R3), MoveType.Normal())
    val oneMoveRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = List(forcedMove)
      def legalMoves(context: GameContext)(square: Square): List[Move]     = List(forcedMove)
      def allLegalMoves(context: GameContext): List[Move]                  = List(forcedMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext =
        context.copy(turn = context.turn.opposite, moves = context.moves :+ move)

    object LowNnue extends Evaluation:
      val CHECKMATE_SCORE: Int                = 10_000_000
      val DRAW_SCORE: Int                     = 0
      def evaluate(context: GameContext): Int = 0

    object HighClassic extends Evaluation:
      val CHECKMATE_SCORE: Int                = 10_000_000
      val DRAW_SCORE: Int                     = 0
      def evaluate(context: GameContext): Int = 10_000

    val reported = AtomicBoolean(false)
    val bot = HybridBot(
      BotDifficulty.Easy,
      rules = oneMoveRules,
      nnueEvaluation = LowNnue,
      classicalEvaluation = HighClassic,
      vetoReporter = _ => reported.set(true),
    )

    bot.nextMove(GameContext.initial) should be(Some(forcedMove))
    reported.get should be(true)

  test("HybridBot default veto reporter prints when threshold is exceeded"):
    val forcedMove = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R3), MoveType.Normal())
    val oneMoveRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = List(forcedMove)
      def legalMoves(context: GameContext)(square: Square): List[Move]     = List(forcedMove)
      def allLegalMoves(context: GameContext): List[Move]                  = List(forcedMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext =
        context.copy(turn = context.turn.opposite, moves = context.moves :+ move)

    object LowNnue extends Evaluation:
      val CHECKMATE_SCORE: Int                = 10_000_000
      val DRAW_SCORE: Int                     = 0
      def evaluate(context: GameContext): Int = 0

    object HighClassic extends Evaluation:
      val CHECKMATE_SCORE: Int                = 10_000_000
      val DRAW_SCORE: Int                     = 0
      def evaluate(context: GameContext): Int = 10_000

    val bot = HybridBot(
      BotDifficulty.Easy,
      rules = oneMoveRules,
      nnueEvaluation = LowNnue,
      classicalEvaluation = HighClassic,
    )

    val printed = Console.withOut(new java.io.ByteArrayOutputStream()) {
      bot.nextMove(GameContext.initial)
    }
    printed should be(Some(forcedMove))
