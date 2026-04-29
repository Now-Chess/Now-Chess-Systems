package de.nowchess.bot

import de.nowchess.api.board.{Board, CastlingRights, Color, File, Piece, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.bot.util.ZobristHash
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ZobristHashTest extends AnyFunSuite with Matchers:

  test("hash is deterministic"):
    val hash1 = ZobristHash.hash(GameContext.initial)
    val hash2 = ZobristHash.hash(GameContext.initial)
    hash1 should equal(hash2)

  test("hash differs after a pawn move"):
    val initial = GameContext.initial
    // Move pawn from e2 to e4
    val board     = initial.board.pieces
    val newBoard  = board.removed(Square(File.E, Rank.R2)).updated(Square(File.E, Rank.R4), Piece.WhitePawn)
    val afterMove = initial.withBoard(Board(newBoard)).withTurn(Color.Black)
    val hash1     = ZobristHash.hash(initial)
    val hash2     = ZobristHash.hash(afterMove)
    hash1 should not equal hash2

  test("hash includes castling rights"):
    val ctx1  = GameContext.initial
    val ctx2  = ctx1.withCastlingRights(CastlingRights.None)
    val hash1 = ZobristHash.hash(ctx1)
    val hash2 = ZobristHash.hash(ctx2)
    hash1 should not equal hash2

  test("hash includes en-passant square"):
    val ctx1  = GameContext.initial
    val ctx2  = ctx1.withEnPassantSquare(Some(Square(File.E, Rank.R3)))
    val hash1 = ZobristHash.hash(ctx1)
    val hash2 = ZobristHash.hash(ctx2)
    hash1 should not equal hash2

  test("hash includes side to move"):
    val ctx1  = GameContext.initial
    val ctx2  = ctx1.withTurn(Color.Black)
    val hash1 = ZobristHash.hash(ctx1)
    val hash2 = ZobristHash.hash(ctx2)
    hash1 should not equal hash2

  test("nextHash matches recomputed hash for a normal move"):
    val context     = GameContext.initial
    val move        = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val next        = DefaultRules.applyMove(context)(move)
    val incremental = ZobristHash.nextHash(context, ZobristHash.hash(context), move, next)
    incremental should equal(ZobristHash.hash(next))

  test("nextHash matches recomputed hash for promotion and castling"):
    val promotionBoard = Board(
      Map(
        Square(File.E, Rank.R7) -> Piece.WhitePawn,
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.H, Rank.R1) -> Piece.WhiteRook,
        Square(File.E, Rank.R8) -> Piece.BlackKing,
      ),
    )
    val promotionContext = GameContext.initial
      .withBoard(promotionBoard)
      .withTurn(Color.White)
      .withCastlingRights(CastlingRights.All)
    val promotionMove = Move(Square(File.E, Rank.R7), Square(File.E, Rank.R8), MoveType.Promotion(PromotionPiece.Queen))
    val promotionNext = DefaultRules.applyMove(promotionContext)(promotionMove)
    val promotionHash =
      ZobristHash.nextHash(promotionContext, ZobristHash.hash(promotionContext), promotionMove, promotionNext)
    promotionHash should equal(ZobristHash.hash(promotionNext))

    val castleBoard = Board(
      Map(
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.H, Rank.R1) -> Piece.WhiteRook,
        Square(File.E, Rank.R8) -> Piece.BlackKing,
      ),
    )
    val castleContext = GameContext.initial
      .withBoard(castleBoard)
      .withTurn(Color.White)
      .withCastlingRights(
        CastlingRights(whiteKingSide = true, whiteQueenSide = false, blackKingSide = false, blackQueenSide = false),
      )
    val castleMove = Move(Square(File.E, Rank.R1), Square(File.G, Rank.R1), MoveType.CastleKingside)
    val castleNext = DefaultRules.applyMove(castleContext)(castleMove)
    val castleHash = ZobristHash.nextHash(castleContext, ZobristHash.hash(castleContext), castleMove, castleNext)
    castleHash should equal(ZobristHash.hash(castleNext))

  test("nextHash matches recomputed hash for queenside castling"):
    val board = Board(
      Map(
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.A, Rank.R1) -> Piece.WhiteRook,
        Square(File.E, Rank.R8) -> Piece.BlackKing,
      ),
    )
    val ctx = GameContext.initial
      .withBoard(board)
      .withTurn(Color.White)
      .withCastlingRights(
        CastlingRights(whiteKingSide = false, whiteQueenSide = true, blackKingSide = false, blackQueenSide = false),
      )
    val move = Move(Square(File.E, Rank.R1), Square(File.C, Rank.R1), MoveType.CastleQueenside)
    val next = DefaultRules.applyMove(ctx)(move)
    ZobristHash.nextHash(ctx, ZobristHash.hash(ctx), move, next) should equal(ZobristHash.hash(next))

  test("nextHash matches recomputed hash for en passant"):
    val board = Board(
      Map(
        Square(File.E, Rank.R5) -> Piece.WhitePawn,
        Square(File.D, Rank.R5) -> Piece.BlackPawn,
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.E, Rank.R8) -> Piece.BlackKing,
      ),
    )
    val ctx = GameContext.initial
      .withBoard(board)
      .withTurn(Color.White)
      .withEnPassantSquare(Some(Square(File.D, Rank.R6)))
    val move = Move(Square(File.E, Rank.R5), Square(File.D, Rank.R6), MoveType.EnPassant)
    val next = DefaultRules.applyMove(ctx)(move)
    ZobristHash.nextHash(ctx, ZobristHash.hash(ctx), move, next) should equal(ZobristHash.hash(next))

  test("nextHash matches recomputed hash for black kingside castling"):
    val board = Board(
      Map(
        Square(File.E, Rank.R8) -> Piece.BlackKing,
        Square(File.H, Rank.R8) -> Piece.BlackRook,
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
      ),
    )
    val ctx = GameContext.initial
      .withBoard(board)
      .withTurn(Color.Black)
      .withCastlingRights(
        CastlingRights(whiteKingSide = false, whiteQueenSide = false, blackKingSide = true, blackQueenSide = false),
      )
    val move = Move(Square(File.E, Rank.R8), Square(File.G, Rank.R8), MoveType.CastleKingside)
    val next = DefaultRules.applyMove(ctx)(move)
    ZobristHash.nextHash(ctx, ZobristHash.hash(ctx), move, next) should equal(ZobristHash.hash(next))

  test("nextHash matches recomputed hash for knight and rook promotions"):
    val board = Board(
      Map(
        Square(File.E, Rank.R7) -> Piece.WhitePawn,
        Square(File.E, Rank.R1) -> Piece.WhiteKing,
        Square(File.E, Rank.R8) -> Piece.BlackKing,
      ),
    )
    val ctx = GameContext.initial
      .withBoard(board)
      .withTurn(Color.White)
      .withCastlingRights(CastlingRights(false, false, false, false))

    for pp <- List(PromotionPiece.Knight, PromotionPiece.Bishop, PromotionPiece.Rook) do
      val move = Move(Square(File.E, Rank.R7), Square(File.E, Rank.R8), MoveType.Promotion(pp))
      val next = DefaultRules.applyMove(ctx)(move)
      ZobristHash.nextHash(ctx, ZobristHash.hash(ctx), move, next) should equal(ZobristHash.hash(next))
