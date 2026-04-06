package de.nowchess.api.board

/**
 * Unified castling rights tracker for all four sides.
 * Tracks whether castling is still available for each side and direction.
 *
 * @param whiteKingSide   White's king-side castling (0-0) still legally available
 * @param whiteQueenSide  White's queen-side castling (0-0-0) still legally available
 * @param blackKingSide   Black's king-side castling (0-0) still legally available
 * @param blackQueenSide  Black's queen-side castling (0-0-0) still legally available
 */
final case class CastlingRights(
  whiteKingSide: Boolean,
  whiteQueenSide: Boolean,
  blackKingSide: Boolean,
  blackQueenSide: Boolean
):
  /**
   * Check if either side has any castling rights remaining.
   */
  def hasAnyRights: Boolean =
    whiteKingSide || whiteQueenSide || blackKingSide || blackQueenSide

  /**
   * Check if a specific color has any castling rights remaining.
   */
  def hasRights(color: Color): Boolean = color match
    case Color.White => whiteKingSide || whiteQueenSide
    case Color.Black => blackKingSide || blackQueenSide

  /**
   * Revoke all castling rights for a specific color.
   */
  def revokeColor(color: Color): CastlingRights = color match
    case Color.White => copy(whiteKingSide = false, whiteQueenSide = false)
    case Color.Black => copy(blackKingSide = false, blackQueenSide = false)

  /**
   * Revoke a specific castling right.
   */
  def revokeKingSide(color: Color): CastlingRights = color match
    case Color.White => copy(whiteKingSide = false)
    case Color.Black => copy(blackKingSide = false)

  /**
   * Revoke a specific castling right.
   */
  def revokeQueenSide(color: Color): CastlingRights = color match
    case Color.White => copy(whiteQueenSide = false)
    case Color.Black => copy(blackQueenSide = false)

object CastlingRights:
  /** No castling rights for any side. */
  val None: CastlingRights = CastlingRights(
    whiteKingSide = false,
    whiteQueenSide = false,
    blackKingSide = false,
    blackQueenSide = false
  )

  /** All castling rights available. */
  val All: CastlingRights = CastlingRights(
    whiteKingSide = true,
    whiteQueenSide = true,
    blackKingSide = true,
    blackQueenSide = true
  )

  /** Standard starting position castling rights (both sides can castle both ways). */
  val Initial: CastlingRights = All
