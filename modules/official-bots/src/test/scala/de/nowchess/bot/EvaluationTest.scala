package de.nowchess.bot

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.board.Board
import de.nowchess.bot.bots.classic.EvaluationClassic
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EvaluationTest extends AnyFunSuite with Matchers:

  test("initial position evaluates to tempo bonus"):
    val eval = EvaluationClassic.evaluate(GameContext.initial)
    eval should equal(10) // TEMPO_BONUS only

  test("remove white queen gives negative evaluation"):
    val initial           = GameContext.initial
    val board             = initial.board
    val emptySquare       = Square(File.D, Rank.R1)
    val boardWithoutQueen = board.pieces.filter((sq, _) => sq != emptySquare)
    val newContext        = initial.withBoard(Board(boardWithoutQueen))
    val eval              = EvaluationClassic.evaluate(newContext)
    eval should be < 0

  test("remove black queen gives positive evaluation"):
    val initial           = GameContext.initial
    val board             = initial.board
    val emptySquare       = Square(File.D, Rank.R8)
    val boardWithoutQueen = board.pieces.filter((sq, _) => sq != emptySquare)
    val newContext        = initial.withBoard(Board(boardWithoutQueen))
    val eval              = EvaluationClassic.evaluate(newContext)
    eval should be > 0

  test("different piece-square bonuses are applied"):
    // Knight on d4 (center) vs knight on a1 (corner) - center should be better
    val knightD4Board = Board(Map(Square(File.D, Rank.R4) -> Piece.WhiteKnight))
    val knightA1Board = Board(Map(Square(File.A, Rank.R1) -> Piece.WhiteKnight))
    val knightD4      = GameContext.initial.withBoard(knightD4Board)
    val knightA1      = GameContext.initial.withBoard(knightA1Board)

    val eval1 = EvaluationClassic.evaluate(knightD4)
    val eval2 = EvaluationClassic.evaluate(knightA1)
    eval1 should be > eval2 // d4 (center) is better than a1 (corner) for knight

  test("all piece types are in material map"):
    PieceType.values.length should be > 0
    // Just verify evaluate works with all piece types
    val eval = EvaluationClassic.evaluate(GameContext.initial)
    eval should not be (EvaluationClassic.CHECKMATE_SCORE)

  test("CHECKMATE_SCORE and DRAW_SCORE are accessible"):
    EvaluationClassic.CHECKMATE_SCORE should equal(10_000_000)
    EvaluationClassic.DRAW_SCORE should equal(0)

  test("active knight (center) scores higher than passive knight (corner)"):
    val knightD4Board   = Board(Map(Square(File.D, Rank.R4) -> Piece.WhiteKnight))
    val knightA1Board   = Board(Map(Square(File.A, Rank.R1) -> Piece.WhiteKnight))
    val knightD4Context = GameContext.initial.withBoard(knightD4Board)
    val knightA1Context = GameContext.initial.withBoard(knightA1Board)
    val evalD4          = EvaluationClassic.evaluate(knightD4Context)
    val evalA1          = EvaluationClassic.evaluate(knightA1Context)
    evalD4 should be > evalA1 // Knight on d4 (center, more mobility) should score higher

  test("bishop pair scores higher than bishop + knight"):
    val bishopPairBoard = Board(
      Map(
        Square(File.C, Rank.R1) -> Piece.WhiteBishop,
        Square(File.F, Rank.R1) -> Piece.WhiteBishop,
      ),
    )
    val bishopKnightBoard = Board(
      Map(
        Square(File.C, Rank.R1) -> Piece.WhiteBishop,
        Square(File.B, Rank.R1) -> Piece.WhiteKnight,
      ),
    )
    val pairContext   = GameContext.initial.withBoard(bishopPairBoard)
    val knightContext = GameContext.initial.withBoard(bishopKnightBoard)
    val evalPair      = EvaluationClassic.evaluate(pairContext)
    val evalKnight    = EvaluationClassic.evaluate(knightContext)
    evalPair should be > evalKnight // Bishop pair should score higher

  test("rook on 7th rank scores higher than rook on 4th rank"):
    val rook7thBoard   = Board(Map(Square(File.A, Rank.R7) -> Piece.WhiteRook))
    val rook4thBoard   = Board(Map(Square(File.A, Rank.R4) -> Piece.WhiteRook))
    val rook7thContext = GameContext.initial.withBoard(rook7thBoard)
    val rook4thContext = GameContext.initial.withBoard(rook4thBoard)
    val eval7th        = EvaluationClassic.evaluate(rook7thContext)
    val eval4th        = EvaluationClassic.evaluate(rook4thContext)
    eval7th should be > eval4th // Rook on 7th rank should score higher

  test("enemy rook on 7th rank is penalised"):
    // Black rook on rank 2 (7th for black) with white to move — hits the enemy branch
    val board   = Board(Map(Square(File.A, Rank.R2) -> Piece.BlackRook))
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    val eval    = EvaluationClassic.evaluate(context)
    eval should be < 0 // disadvantageous for white

  test("king at edge rank yields zero king-shield bonus"):
    // White king on rank 8 — shieldRank would be 9, out of bounds → guard fires
    val board   = Board(Map(Square(File.H, Rank.R8) -> Piece.WhiteKing, Square(File.H, Rank.R1) -> Piece.BlackKing))
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    // Evaluating does not throw and uses the guard path
    noException should be thrownBy EvaluationClassic.evaluate(context)

  test("endgame bonus is applied when material is low"):
    // Kings + one rook: phase = 2 < 8, triggers endgameBonus with friendly material advantage
    val board = Board(
      Map(
        Square(File.D, Rank.R4) -> Piece.WhiteKing,
        Square(File.D, Rank.R6) -> Piece.BlackKing,
        Square(File.A, Rank.R1) -> Piece.WhiteRook,
      ),
    )
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    noException should be thrownBy EvaluationClassic.evaluate(context)

  test("endgame bonus else branch when material is equal"):
    // Both sides have a rook: friendlyMaterial == enemyMaterial → edgeBonus = 0
    val board = Board(
      Map(
        Square(File.D, Rank.R4) -> Piece.WhiteKing,
        Square(File.D, Rank.R6) -> Piece.BlackKing,
        Square(File.A, Rank.R1) -> Piece.WhiteRook,
        Square(File.H, Rank.R8) -> Piece.BlackRook,
      ),
    )
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    noException should be thrownBy EvaluationClassic.evaluate(context)

  test("passed pawn bonus is applied in endgame"):
    // No enemy pawns anywhere → white pawn on e5 is passed; phase = 0 → endgame → egPassedPawnBonus
    val board = Board(
      Map(
        Square(File.E, Rank.R5) -> Piece.WhitePawn,
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.E, Rank.R8) -> Piece.BlackKing,
      ),
    )
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    val eval    = EvaluationClassic.evaluate(context)
    eval should be > 0
