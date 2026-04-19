package de.nowchess.bot

import de.nowchess.api.board.Square
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.move.MoveType
import de.nowchess.bot.bots.ClassicalBot
import de.nowchess.rules.RuleSet
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import de.nowchess.rules.sets.DefaultRules

class ClassicalBotTest extends AnyFunSuite with Matchers:

  test("name returns expected format"):
    val botEasy = ClassicalBot(BotDifficulty.Easy)
    botEasy.name should include("ClassicalBot")
    botEasy.name should include("Easy")

    val botMedium = ClassicalBot(BotDifficulty.Medium)
    botMedium.name should include("Medium")

  test("nextMove on initial position returns a move"):
    val bot  = ClassicalBot(BotDifficulty.Easy)
    val move = bot.nextMove(GameContext.initial)
    move should not be None

  test("nextMove returns None for position with no legal moves"):
    val stubRules = new RuleSet:
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

    val bot  = ClassicalBot(BotDifficulty.Easy, stubRules)
    val move = bot.nextMove(GameContext.initial)
    move should be(None)

  test("all BotDifficulty values work"):
    BotDifficulty.values.foreach { difficulty =>
      val bot  = ClassicalBot(difficulty)
      val move = bot.nextMove(GameContext.initial)
      // All difficulties should return a move on the initial position
      move should not be None
    }

  test("custom RuleSet injection works"):
    val moveToReturn = Move(
      de.nowchess.api.board.Square(de.nowchess.api.board.File.E, de.nowchess.api.board.Rank.R2),
      de.nowchess.api.board.Square(de.nowchess.api.board.File.E, de.nowchess.api.board.Rank.R4),
      de.nowchess.api.move.MoveType.Normal(),
    )

    val stubRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = List(moveToReturn)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val bot  = ClassicalBot(BotDifficulty.Easy, stubRules)
    val move = bot.nextMove(GameContext.initial)
    move should be(Some(moveToReturn))

  test("nextMove skips a move repeated three times in a row"):
    val repeatedMove = Move(
      de.nowchess.api.board.Square(de.nowchess.api.board.File.E, de.nowchess.api.board.Rank.R2),
      de.nowchess.api.board.Square(de.nowchess.api.board.File.E, de.nowchess.api.board.Rank.R4),
      MoveType.Normal(),
    )

    val stubRules = new RuleSet:
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

    val context = GameContext.initial.copy(moves = List(repeatedMove, repeatedMove, repeatedMove))
    val bot     = ClassicalBot(BotDifficulty.Easy, stubRules)

    bot.nextMove(context) should be(None)
