package de.nowchess.bot.util

import de.nowchess.api.board.{Color, Piece, PieceType, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}

import scala.util.Random

object ZobristHash:

  // 768 entries: 64 squares * 12 piece variants (2 colors * 6 piece types)
  private val pieceRands: Array[Long] = Array.ofDim(768)

  // Side-to-move: XOR when Black to move
  private val sideToMoveRand: Long = Random(0x1badb002L).nextLong() // NOSONAR

  // 4 entries: White kingside, White queenside, Black kingside, Black queenside
  private val castlingRands: Array[Long] = Array.ofDim(4)

  // 8 entries: one per file (a-h)
  private val enPassantRands: Array[Long] = Array.ofDim(8)

  // Initialize all random values using a seeded RNG for reproducibility
  locally:
    val rng = Random(0x1badb002L) // NOSONAR
    for i <- 0 until 768 do pieceRands(i) = rng.nextLong()
    for i <- 0 until 4 do castlingRands(i) = rng.nextLong()
    for i <- 0 until 8 do enPassantRands(i) = rng.nextLong()

  /** Compute a 64-bit Zobrist hash for a GameContext. */
  def hash(context: GameContext): Long =
    val piecesHash = context.board.pieces.foldLeft(0L) { case (h, (square, piece)) =>
      val squareIndex = square.rank.ordinal * 8 + square.file.ordinal
      val colorIndex  = if piece.color == Color.White then 0 else 1
      val pieceIndex  = colorIndex * 6 + piece.pieceType.ordinal
      h ^ pieceRands(squareIndex * 12 + pieceIndex)
    }
    val h1 = if context.turn == Color.Black then piecesHash ^ sideToMoveRand else piecesHash
    val h2 = if context.castlingRights.whiteKingSide then h1 ^ castlingRands(0) else h1
    val h3 = if context.castlingRights.whiteQueenSide then h2 ^ castlingRands(1) else h2
    val h4 = if context.castlingRights.blackKingSide then h3 ^ castlingRands(2) else h3
    val h5 = if context.castlingRights.blackQueenSide then h4 ^ castlingRands(3) else h4
    context.enPassantSquare.fold(h5)(sq => h5 ^ enPassantRands(sq.file.ordinal))

  def nextHash(context: GameContext, currentHash: Long, move: Move, nextContext: GameContext): Long =
    val h0 = currentHash ^ sideToMoveRand
    val h1 = toggleCastling(h0, context, nextContext)
    val h2 = toggleEnPassant(h1, context, nextContext)
    move.moveType match
      case MoveType.CastleKingside | MoveType.CastleQueenside =>
        applyCastleDelta(h2, context.turn, move.moveType == MoveType.CastleKingside)
      case MoveType.EnPassant =>
        applyEnPassantDelta(h2, context, move)
      case MoveType.Promotion(piece) =>
        applyPromotionDelta(h2, context, move, piece)
      case MoveType.Normal(_) =>
        applyNormalDelta(h2, context, move)

  private def applyNormalDelta(h0: Long, context: GameContext, move: Move): Long =
    context.board.pieceAt(move.from).fold(h0) { mover =>
      val h1 = h0 ^ pieceKey(move.from, mover)
      val h2 = context.board.pieceAt(move.to).fold(h1)(captured => h1 ^ pieceKey(move.to, captured))
      h2 ^ pieceKey(move.to, mover)
    }

  private def applyPromotionDelta(h0: Long, context: GameContext, move: Move, promoted: PromotionPiece): Long =
    context.board.pieceAt(move.from).fold(h0) { pawn =>
      val h1 = h0 ^ pieceKey(move.from, pawn)
      val h2 = context.board.pieceAt(move.to).fold(h1)(captured => h1 ^ pieceKey(move.to, captured))
      h2 ^ pieceKey(move.to, Piece(context.turn, promotedPieceType(promoted)))
    }

  private def applyEnPassantDelta(h0: Long, context: GameContext, move: Move): Long =
    context.board.pieceAt(move.from).fold(h0) { pawn =>
      val capturedSquare = Square(move.to.file, move.from.rank)
      val h1             = h0 ^ pieceKey(move.from, pawn)
      val h2 = context.board.pieceAt(capturedSquare).fold(h1)(captured => h1 ^ pieceKey(capturedSquare, captured))
      h2 ^ pieceKey(move.to, pawn)
    }

  private def applyCastleDelta(h0: Long, color: Color, kingside: Boolean): Long =
    val rank = if color == Color.White then Rank.R1 else Rank.R8
    val (kingFrom, kingTo, rookFrom, rookTo) =
      if kingside then
        (
          Square(de.nowchess.api.board.File.E, rank),
          Square(de.nowchess.api.board.File.G, rank),
          Square(de.nowchess.api.board.File.H, rank),
          Square(de.nowchess.api.board.File.F, rank),
        )
      else
        (
          Square(de.nowchess.api.board.File.E, rank),
          Square(de.nowchess.api.board.File.C, rank),
          Square(de.nowchess.api.board.File.A, rank),
          Square(de.nowchess.api.board.File.D, rank),
        )
    val king = Piece(color, PieceType.King)
    val rook = Piece(color, PieceType.Rook)
    h0 ^ pieceKey(kingFrom, king) ^ pieceKey(kingTo, king) ^ pieceKey(rookFrom, rook) ^ pieceKey(rookTo, rook)

  private def promotedPieceType(promotion: PromotionPiece): PieceType = promotion match
    case PromotionPiece.Knight => PieceType.Knight
    case PromotionPiece.Bishop => PieceType.Bishop
    case PromotionPiece.Rook   => PieceType.Rook
    case PromotionPiece.Queen  => PieceType.Queen

  private def toggleCastling(h0: Long, before: GameContext, after: GameContext): Long =
    val h1 =
      if before.castlingRights.whiteKingSide != after.castlingRights.whiteKingSide then h0 ^ castlingRands(0) else h0
    val h2 =
      if before.castlingRights.whiteQueenSide != after.castlingRights.whiteQueenSide then h1 ^ castlingRands(1) else h1
    val h3 =
      if before.castlingRights.blackKingSide != after.castlingRights.blackKingSide then h2 ^ castlingRands(2) else h2
    if before.castlingRights.blackQueenSide != after.castlingRights.blackQueenSide then h3 ^ castlingRands(3) else h3

  private def toggleEnPassant(h0: Long, before: GameContext, after: GameContext): Long =
    val h1 = before.enPassantSquare.fold(h0)(sq => h0 ^ enPassantRands(sq.file.ordinal))
    after.enPassantSquare.fold(h1)(sq => h1 ^ enPassantRands(sq.file.ordinal))

  private def pieceKey(square: Square, piece: Piece): Long =
    val squareIndex = square.rank.ordinal * 8 + square.file.ordinal
    val colorIndex  = if piece.color == Color.White then 0 else 1
    val pieceIndex  = colorIndex * 6 + piece.pieceType.ordinal
    pieceRands(squareIndex * 12 + pieceIndex)
