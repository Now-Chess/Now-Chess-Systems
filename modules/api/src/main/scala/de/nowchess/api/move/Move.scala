package de.nowchess.api.move

import de.nowchess.api.board.Square

/** The piece a pawn may be promoted to (all non-pawn, non-king pieces). */
enum PromotionPiece:
  case Knight, Bishop, Rook, Queen

/** Classifies special move semantics beyond a plain quiet move or capture. */
enum MoveType:
  /** A normal move or capture with no special rule. */
  case Normal(isCapture: Boolean = false)
  /** Kingside castling (O-O). */
  case CastleKingside
  /** Queenside castling (O-O-O). */
  case CastleQueenside
  /** En-passant pawn capture. */
  case EnPassant
  /** Pawn promotion; carries the chosen promotion piece. */
  case Promotion(piece: PromotionPiece)

/**
 * A half-move (ply) in a chess game.
 *
 * @param from     origin square
 * @param to       destination square
 * @param moveType special semantics; defaults to Normal
 */
final case class Move(
  from: Square,
  to: Square,
  moveType: MoveType = MoveType.Normal()
)
