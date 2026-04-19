package de.nowchess.bot

import de.nowchess.api.board.{Board, Color, File, Piece, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.bot.logic.MoveOrdering
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MoveOrderingTest extends AnyFunSuite with Matchers:

  test("queen capture ranks higher than rook capture"):
    val board = Board(
      Map(
        Square(File.E, Rank.R4) -> Piece.WhiteQueen,
        Square(File.E, Rank.R5) -> Piece.BlackQueen,
        Square(File.E, Rank.R6) -> Piece.BlackRook,
      ),
    )
    val context      = GameContext.initial.withBoard(board).withTurn(Color.White)
    val queenCapture = Move(Square(File.E, Rank.R4), Square(File.E, Rank.R5), MoveType.Normal(true))
    val rookCapture  = Move(Square(File.E, Rank.R4), Square(File.E, Rank.R6), MoveType.Normal(true))

    val queenScore = MoveOrdering.score(context, queenCapture, None)
    val rookScore  = MoveOrdering.score(context, rookCapture, None)
    queenScore should be > rookScore

  test("quiet move ranks lower than capture"):
    val board = Board(
      Map(
        Square(File.E, Rank.R4) -> Piece.WhiteQueen,
        Square(File.E, Rank.R5) -> Piece.BlackPawn,
      ),
    )
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    val capture = Move(Square(File.E, Rank.R4), Square(File.E, Rank.R5), MoveType.Normal(true))
    val quiet   = Move(Square(File.E, Rank.R4), Square(File.D, Rank.R5))

    val captureScore = MoveOrdering.score(context, capture, None)
    val quietScore   = MoveOrdering.score(context, quiet, None)
    captureScore should be > quietScore

  test("TT best move ranks first"):
    val board = Board(
      Map(
        Square(File.E, Rank.R4) -> Piece.WhiteQueen,
        Square(File.E, Rank.R5) -> Piece.BlackPawn,
        Square(File.D, Rank.R5) -> Piece.BlackPawn,
      ),
    )
    val context      = GameContext.initial.withBoard(board).withTurn(Color.White)
    val bestMove     = Move(Square(File.E, Rank.R4), Square(File.D, Rank.R5), MoveType.Normal(true))
    val otherCapture = Move(Square(File.E, Rank.R4), Square(File.E, Rank.R5), MoveType.Normal(true))

    val bestScore  = MoveOrdering.score(context, bestMove, Some(bestMove))
    val otherScore = MoveOrdering.score(context, otherCapture, Some(bestMove))
    bestScore should equal(Int.MaxValue)
    otherScore should be < bestScore

  test("promotion to queen ranks high"):
    val board = Board(
      Map(
        Square(File.E, Rank.R7) -> Piece.WhitePawn,
      ),
    )
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    val promotionQueen =
      Move(Square(File.E, Rank.R7), Square(File.E, Rank.R8), MoveType.Promotion(PromotionPiece.Queen))
    val promotionKnight =
      Move(Square(File.E, Rank.R7), Square(File.E, Rank.R8), MoveType.Promotion(PromotionPiece.Knight))

    val queenScore  = MoveOrdering.score(context, promotionQueen, None)
    val knightScore = MoveOrdering.score(context, promotionKnight, None)
    queenScore should be > knightScore
    queenScore should be > 100_000 // Queen promotion score is > 100_000

  test("en passant is treated as capture"):
    val board = Board(
      Map(
        Square(File.E, Rank.R5) -> Piece.WhitePawn,
        Square(File.D, Rank.R5) -> Piece.BlackPawn,
      ),
    )
    val context   = GameContext.initial.withBoard(board).withTurn(Color.White)
    val epCapture = Move(Square(File.E, Rank.R5), Square(File.D, Rank.R6), MoveType.EnPassant)
    val quiet     = Move(Square(File.E, Rank.R5), Square(File.E, Rank.R6))

    val epScore    = MoveOrdering.score(context, epCapture, None)
    val quietScore = MoveOrdering.score(context, quiet, None)
    epScore should be > quietScore

  test("sort returns moves ordered by score"):
    val board = Board(
      Map(
        Square(File.E, Rank.R4) -> Piece.WhiteQueen,
        Square(File.E, Rank.R5) -> Piece.BlackPawn,
        Square(File.D, Rank.R5) -> Piece.BlackRook,
      ),
    )
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    val moves = List(
      Move(Square(File.E, Rank.R4), Square(File.D, Rank.R5), MoveType.Normal(true)), // Rook capture
      Move(Square(File.E, Rank.R4), Square(File.E, Rank.R5), MoveType.Normal(true)), // Pawn capture
      Move(Square(File.E, Rank.R4), Square(File.E, Rank.R6)),                        // Quiet
    )
    val sorted = MoveOrdering.sort(context, moves, None)
    // Rook capture should be first (higher victim value)
    sorted.head.to should equal(Square(File.D, Rank.R5))
    // Pawn capture should be second
    sorted(1).to should equal(Square(File.E, Rank.R5))
    // Quiet should be last
    sorted.last.to should equal(Square(File.E, Rank.R6))

  test("castling move is quiet (not capture)"):
    val board = Board(
      Map(
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.H, Rank.R1) -> Piece.WhiteRook,
      ),
    )
    val context    = GameContext.initial.withBoard(board)
    val castleMove = Move(Square(File.E, Rank.R1), Square(File.G, Rank.R1), MoveType.CastleKingside)
    val score      = MoveOrdering.score(context, castleMove, None)
    score should equal(0) // Quiet move

  test("all MoveType variants are handled in victimValue"):
    val board = Board(
      Map(
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.H, Rank.R1) -> Piece.WhiteRook,
        Square(File.E, Rank.R2) -> Piece.WhitePawn,
      ),
    )
    val context = GameContext.initial.withBoard(board)
    // Test castling queenside - should have victim value 0
    val castleQs = Move(Square(File.E, Rank.R1), Square(File.C, Rank.R1), MoveType.CastleQueenside)
    val scoreQs  = MoveOrdering.score(context, castleQs, None)
    scoreQs should equal(0)

  test("attackerValue covers all piece types"):
    val board = Board(
      Map(
        Square(File.A, Rank.R1) -> Piece.WhiteRook,
        Square(File.B, Rank.R1) -> Piece.WhiteKnight,
        Square(File.C, Rank.R1) -> Piece.WhiteBishop,
        Square(File.D, Rank.R1) -> Piece.WhiteQueen,
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.F, Rank.R2) -> Piece.WhitePawn,
      ),
    )
    val context = GameContext.initial.withBoard(board)
    // Create captures with each piece type
    val rookCapture   = Move(Square(File.A, Rank.R1), Square(File.A, Rank.R8), MoveType.Normal(true))
    val knightCapture = Move(Square(File.B, Rank.R1), Square(File.A, Rank.R8), MoveType.Normal(true))
    val bishopCapture = Move(Square(File.C, Rank.R1), Square(File.A, Rank.R8), MoveType.Normal(true))
    val queenCapture  = Move(Square(File.D, Rank.R1), Square(File.A, Rank.R8), MoveType.Normal(true))
    val kingCapture   = Move(Square(File.E, Rank.R1), Square(File.A, Rank.R8), MoveType.Normal(true))
    val pawnCapture   = Move(Square(File.F, Rank.R2), Square(File.A, Rank.R8), MoveType.Normal(true))

    // Just verify all are scored without error
    MoveOrdering.score(context, rookCapture, None) should be >= 0
    MoveOrdering.score(context, knightCapture, None) should be >= 0
    MoveOrdering.score(context, bishopCapture, None) should be >= 0
    MoveOrdering.score(context, queenCapture, None) should be >= 0
    MoveOrdering.score(context, kingCapture, None) should be >= 0
    MoveOrdering.score(context, pawnCapture, None) should be >= 0

  test("promotion capture is distinct from quiet promotion"):
    val board = Board(
      Map(
        Square(File.E, Rank.R7) -> Piece.WhitePawn,
        Square(File.D, Rank.R8) -> Piece.BlackPawn,
      ),
    )
    val context = GameContext.initial.withBoard(board)
    // Promotion with capture
    val promotionWithCapture =
      Move(Square(File.E, Rank.R7), Square(File.D, Rank.R8), MoveType.Promotion(PromotionPiece.Queen))
    // Regular queen promotion (no capture)
    val quietPromotion =
      Move(Square(File.E, Rank.R7), Square(File.E, Rank.R8), MoveType.Promotion(PromotionPiece.Queen))
    val score1 = MoveOrdering.score(context, promotionWithCapture, None)
    val score2 = MoveOrdering.score(context, quietPromotion, None)
    score1 should be > score2

  test("non-Queen promotion captures trigger promotionPieceType for Knight, Bishop, Rook"):
    val board = Board(
      Map(
        Square(File.E, Rank.R7) -> Piece.WhitePawn,
        Square(File.D, Rank.R8) -> Piece.BlackRook,
      ),
    )
    val context     = GameContext.initial.withBoard(board).withTurn(Color.White)
    val knightPromo = Move(Square(File.E, Rank.R7), Square(File.D, Rank.R8), MoveType.Promotion(PromotionPiece.Knight))
    val bishopPromo = Move(Square(File.E, Rank.R7), Square(File.D, Rank.R8), MoveType.Promotion(PromotionPiece.Bishop))
    val rookPromo   = Move(Square(File.E, Rank.R7), Square(File.D, Rank.R8), MoveType.Promotion(PromotionPiece.Rook))
    MoveOrdering.score(context, knightPromo, None) should be > 0
    MoveOrdering.score(context, bishopPromo, None) should be > 0
    MoveOrdering.score(context, rookPromo, None) should be > 0

  test("negative SEE capture path is scored below neutral capture baseline"):
    val board = Board(
      Map(
        Square(File.D, Rank.R4) -> Piece.WhiteQueen,
        Square(File.D, Rank.R5) -> Piece.BlackPawn,
        Square(File.D, Rank.R8) -> Piece.BlackRook,
      ),
    )
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    val move    = Move(Square(File.D, Rank.R4), Square(File.D, Rank.R5), MoveType.Normal(true))

    MoveOrdering.score(context, move, None) should be < 100_000

  test("non-capture move keeps fallback scoring at zero"):
    val board   = Board(Map(Square(File.E, Rank.R1) -> Piece.WhiteKing, Square(File.A, Rank.R8) -> Piece.BlackKing))
    val context = GameContext.initial.withBoard(board).withTurn(Color.White)
    val castle  = Move(Square(File.E, Rank.R1), Square(File.G, Rank.R1), MoveType.CastleKingside)

    MoveOrdering.score(context, castle, None) should be(0)
