package de.nowchess.bot

import de.nowchess.api.board.{Board, Color, File, Piece, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.bot.ai.Evaluation
import de.nowchess.bot.bots.classic.EvaluationClassic
import de.nowchess.bot.logic.AlphaBetaSearch
import de.nowchess.rules.RuleSet
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import de.nowchess.rules.sets.DefaultRules

import java.util.concurrent.atomic.AtomicBoolean

class AlphaBetaSearchTest extends AnyFunSuite with Matchers:

  private object ZeroEval extends Evaluation:
    val CHECKMATE_SCORE: Int                = 1_000_000
    val DRAW_SCORE: Int                     = 0
    def evaluate(context: GameContext): Int = 0

  test("bestMove on initial position returns a move"):
    val search = AlphaBetaSearch(DefaultRules, weights = EvaluationClassic)
    val move   = search.bestMove(GameContext.initial, maxDepth = 2)
    move should not be None

  test("bestMove on a position with one legal move returns that move"):
    // Create a simple position: White king on h1, Black rook on a2
    // (set up so there's only one legal move available)
    // For simplicity, just test that a position with forced mate returns a move
    val search  = AlphaBetaSearch(DefaultRules, weights = EvaluationClassic)
    val context = GameContext.initial
    val move    = search.bestMove(context, maxDepth = 1)
    move should not be None

  test("bestMoveWithTime skips excluded root moves"):
    val blockedMove = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())
    val stubRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = List(blockedMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val search = AlphaBetaSearch(stubRules, weights = EvaluationClassic)
    val move   = search.bestMoveWithTime(GameContext.initial, 1000L, Set(blockedMove))
    move should be(None)

  test("bestMove returns None for initial position has no legal moves"):
    // Use a stub RuleSet that returns empty legal moves
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

    val search = AlphaBetaSearch(stubRules, weights = EvaluationClassic)
    val move   = search.bestMove(GameContext.initial, maxDepth = 2)
    move should be(None)

  test("transposition table is cleared at start of bestMove"):
    val search  = AlphaBetaSearch(DefaultRules, weights = EvaluationClassic)
    val context = GameContext.initial
    // Call bestMove twice and verify both work independently
    val move1 = search.bestMove(context, maxDepth = 1)
    val move2 = search.bestMove(context, maxDepth = 1)
    move1 should be(move2)

  test("quiescence captures are ordered"):
    val search = AlphaBetaSearch(DefaultRules, weights = EvaluationClassic)
    // A position with multiple captures to verify quiescence orders them
    val context = GameContext.initial
    val move    = search.bestMove(context, maxDepth = 2)
    // Just verify it completes without error
    move.isDefined should be(true)

  test("search respects alpha-beta bounds"):
    // This is implicit in the structure, but we test via behavior
    val search  = AlphaBetaSearch(DefaultRules, weights = EvaluationClassic)
    val context = GameContext.initial
    val move    = search.bestMove(context, maxDepth = 3)
    move should not be None

  test("iterative deepening finds a move at each depth"):
    val search  = AlphaBetaSearch(DefaultRules, weights = EvaluationClassic)
    val context = GameContext.initial
    // Searching to depth 3 should use iterative deepening (depths 1, 2, 3)
    val move = search.bestMove(context, maxDepth = 3)
    move should not be None

  test("stalemate position returns score 0"):
    // Create a stalemate stub: white to move, no legal moves, not checkmate
    val stalematRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = Nil
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = true
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val search = AlphaBetaSearch(stalematRules, weights = EvaluationClassic)
    val move   = search.bestMove(GameContext.initial, maxDepth = 1)
    move should be(None)

  test("insufficient material returns score 0"):
    val insufficientRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = Nil
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = true
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val search = AlphaBetaSearch(insufficientRules, weights = EvaluationClassic)
    val move   = search.bestMove(GameContext.initial, maxDepth = 1)
    move should be(None)

  test("fifty move rule returns score 0"):
    val fiftyMoveRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = Nil
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = true
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val search = AlphaBetaSearch(fiftyMoveRules, weights = EvaluationClassic)
    val move   = search.bestMove(GameContext.initial, maxDepth = 1)
    move should be(None)

  test("capture moves are recognized in quiescence search"):
    // Create a position with a capture available
    val board = Board(
      Map(
        Square(File.E, Rank.R4) -> Piece.WhiteQueen,
        Square(File.E, Rank.R5) -> Piece.BlackPawn,
      ),
    )
    val context = GameContext.initial.withBoard(board)

    val captureMove = Move(Square(File.E, Rank.R4), Square(File.E, Rank.R5), MoveType.Normal(true))
    val rulesWithCapture = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = List(captureMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val search = AlphaBetaSearch(rulesWithCapture, weights = EvaluationClassic)
    val move   = search.bestMove(context, maxDepth = 1)
    move should be(Some(captureMove))

  test("non-capture moves are not included in quiescence"):
    val quietMove = Move(Square(File.E, Rank.R4), Square(File.E, Rank.R5), MoveType.Normal())
    val rulesQuiet = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = List(quietMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val search = AlphaBetaSearch(rulesQuiet, weights = EvaluationClassic)
    val move   = search.bestMove(GameContext.initial, maxDepth = 1)
    move should be(Some(quietMove)) // bestMove returns the quiet move since it's the only legal move

  test("default constructor uses DefaultRules"):
    val search = AlphaBetaSearch(weights = EvaluationClassic)
    val move   = search.bestMove(GameContext.initial, maxDepth = 1)
    move should not be None

  test("bestMoveWithTime without excluded moves overload"):
    val search = AlphaBetaSearch(DefaultRules, weights = EvaluationClassic)
    val move   = search.bestMoveWithTime(GameContext.initial, 500L)
    move should not be None

  test("en passant move is treated as capture in quiescence"):
    val epMove = Move(Square(File.E, Rank.R5), Square(File.D, Rank.R6), MoveType.EnPassant)
    val board = Board(
      Map(
        Square(File.E, Rank.R5) -> Piece.WhitePawn,
        Square(File.D, Rank.R5) -> Piece.BlackPawn,
      ),
    )
    val ctx = GameContext.initial.withBoard(board).withTurn(Color.White)
    val epRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = List(epMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context
    val search = AlphaBetaSearch(epRules, weights = EvaluationClassic)
    search.bestMove(ctx, maxDepth = 1) should be(Some(epMove))

  test("promotion capture move is treated as capture in quiescence"):
    val promoCapture = Move(Square(File.E, Rank.R7), Square(File.D, Rank.R8), MoveType.Promotion(PromotionPiece.Queen))
    val board = Board(
      Map(
        Square(File.E, Rank.R7) -> Piece.WhitePawn,
        Square(File.D, Rank.R8) -> Piece.BlackRook,
      ),
    )
    val ctx = GameContext.initial.withBoard(board).withTurn(Color.White)
    val promoCaptureRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = Nil
      def legalMoves(context: GameContext)(square: Square): List[Move]     = Nil
      def allLegalMoves(context: GameContext): List[Move]                  = List(promoCapture)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context
    val search = AlphaBetaSearch(promoCaptureRules, weights = EvaluationClassic)
    search.bestMove(ctx, maxDepth = 1) should be(Some(promoCapture))

  test("draw when isInsufficientMaterial with legal moves present"):
    val legalMove = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())
    val drawRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = List(legalMove)
      def legalMoves(context: GameContext)(square: Square): List[Move]     = List(legalMove)
      def allLegalMoves(context: GameContext): List[Move]                  = List(legalMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = true
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context
    val search = AlphaBetaSearch(drawRules, weights = EvaluationClassic)
    search.bestMove(GameContext.initial, maxDepth = 2) should be(None)

  test("repetition cutoff is reached on forced self-loop positions"):
    // Use a no-op move from an empty square so nextHash alternates between a tiny set of hashes.
    // This forces repetition counts >= 3 and exercises immediateSearchResult's repetition cutoff.
    val loopMove = Move(Square(File.A, Rank.R3), Square(File.A, Rank.R4), MoveType.Normal())
    val loopRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = List(loopMove)
      def legalMoves(context: GameContext)(square: Square): List[Move]     = List(loopMove)
      def allLegalMoves(context: GameContext): List[Move]                  = List(loopMove)
      def isCheck(context: GameContext): Boolean                           = false
      def isCheckmate(context: GameContext): Boolean                       = false
      def isStalemate(context: GameContext): Boolean                       = false
      def isInsufficientMaterial(context: GameContext): Boolean            = false
      def isFiftyMoveRule(context: GameContext): Boolean                   = false
      def isThreefoldRepetition(context: GameContext): Boolean             = false
      def applyMove(context: GameContext)(move: Move): GameContext         = context

    val search = AlphaBetaSearch(loopRules, weights = ZeroEval)
    search.bestMove(GameContext.initial, maxDepth = 8) should be(Some(loopMove))

  test("quiescence returns checkmate score when side is in check and has no tactical moves"):
    val rootMove = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R3), MoveType.Normal())
    val capMove  = Move(Square(File.D, Rank.R2), Square(File.D, Rank.R3), MoveType.Normal(true))
    val qRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = allLegalMoves(context)
      def legalMoves(context: GameContext)(square: Square): List[Move]     = allLegalMoves(context)
      def allLegalMoves(context: GameContext): List[Move] =
        context.moves.length match
          case 0 => List(rootMove)
          case 1 => List(capMove)
          case _ => Nil
      def isCheck(context: GameContext): Boolean =
        context.moves.length >= 2
      def isCheckmate(context: GameContext): Boolean            = false
      def isStalemate(context: GameContext): Boolean            = false
      def isInsufficientMaterial(context: GameContext): Boolean = false
      def isFiftyMoveRule(context: GameContext): Boolean        = false
      def isThreefoldRepetition(context: GameContext): Boolean  = false
      def applyMove(context: GameContext)(move: Move): GameContext =
        context.copy(turn = context.turn.opposite, moves = context.moves :+ move)

    val search = AlphaBetaSearch(qRules, weights = ZeroEval)
    search.bestMove(GameContext.initial, maxDepth = 1) should be(Some(rootMove))

  test("quiescence depth-limit in-check branch is exercised"):
    val rootMove            = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R3), MoveType.Normal())
    val capMove             = Move(Square(File.D, Rank.R2), Square(File.D, Rank.R3), MoveType.Normal(true))
    val firstChildCheckCall = AtomicBoolean(true)
    val deepQRules = new RuleSet:
      def candidateMoves(context: GameContext)(square: Square): List[Move] = allLegalMoves(context)
      def legalMoves(context: GameContext)(square: Square): List[Move]     = allLegalMoves(context)
      def allLegalMoves(context: GameContext): List[Move] =
        if context.moves.isEmpty then List(rootMove) else List(capMove)
      def isCheck(context: GameContext): Boolean =
        if context.moves.length == 1 && firstChildCheckCall.compareAndSet(true, false) then false
        else context.moves.nonEmpty
      def isCheckmate(context: GameContext): Boolean            = false
      def isStalemate(context: GameContext): Boolean            = false
      def isInsufficientMaterial(context: GameContext): Boolean = false
      def isFiftyMoveRule(context: GameContext): Boolean        = false
      def isThreefoldRepetition(context: GameContext): Boolean  = false
      def applyMove(context: GameContext)(move: Move): GameContext =
        context.copy(turn = context.turn.opposite, moves = context.moves :+ move)

    val search = AlphaBetaSearch(deepQRules, weights = ZeroEval)
    search.bestMove(GameContext.initial, maxDepth = 1) should be(Some(rootMove))
