package de.nowchess.chess.logic

import de.nowchess.api.board.*

object MoveValidator:

  /** Returns true if the move is geometrically legal for the piece on `from`,
   *  ignoring check/pin but respecting:
   *  - correct movement pattern for the piece type
   *  - cannot capture own pieces
   *  - sliding pieces (bishop, rook, queen) are blocked by intervening pieces
   */
  def isLegal(board: Board, from: Square, to: Square): Boolean =
    legalTargets(board, from).contains(to)

  /** All squares a piece on `from` can legally move to (same rules as isLegal). */
  def legalTargets(board: Board, from: Square): Set[Square] =
    board.pieceAt(from) match
      case None        => Set.empty
      case Some(piece) =>
        piece.pieceType match
          case PieceType.Pawn   => pawnTargets(board, from, piece.color)
          case PieceType.Knight => knightTargets(board, from, piece.color)
          case PieceType.Bishop => slide(board, from, piece.color, diagonalDeltas)
          case PieceType.Rook   => slide(board, from, piece.color, orthogonalDeltas)
          case PieceType.Queen  => slide(board, from, piece.color, diagonalDeltas ++ orthogonalDeltas)
          case PieceType.King   => kingTargets(board, from, piece.color)

  // ── helpers ────────────────────────────────────────────────────────────────

  private val diagonalDeltas:    List[(Int, Int)] = List((1, 1), (1, -1), (-1, 1), (-1, -1))
  private val orthogonalDeltas:  List[(Int, Int)] = List((1, 0), (-1, 0), (0, 1), (0, -1))
  private val knightDeltas:      List[(Int, Int)] =
    List((1, 2), (1, -2), (-1, 2), (-1, -2), (2, 1), (2, -1), (-2, 1), (-2, -1))

  /** Try to construct a Square from integer file/rank indices (0-based). */
  private def squareAt(fileIdx: Int, rankIdx: Int): Option[Square] =
    Option.when(fileIdx >= 0 && fileIdx <= 7 && rankIdx >= 0 && rankIdx <= 7)(
      Square(File.values(fileIdx), Rank.values(rankIdx))
    )

  /** True when `sq` is occupied by a piece of `color`. */
  private def isOwnPiece(board: Board, sq: Square, color: Color): Boolean =
    board.pieceAt(sq).exists(_.color == color)

  /** True when `sq` is occupied by a piece of the opposite color. */
  private def isEnemyPiece(board: Board, sq: Square, color: Color): Boolean =
    board.pieceAt(sq).exists(_.color != color)

  /** Sliding move generation along a list of direction deltas.
   *  Each direction continues until the board edge, an own piece, or the first
   *  enemy piece (which is included as a capture target).
   */
  private def slide(board: Board, from: Square, color: Color, deltas: List[(Int, Int)]): Set[Square] =
    val fi = from.file.ordinal
    val ri = from.rank.ordinal
    deltas.flatMap: (df, dr) =>
      Iterator
        .iterate((fi + df, ri + dr)) { case (f, r) => (f + df, r + dr) }
        .takeWhile { case (f, r) => f >= 0 && f <= 7 && r >= 0 && r <= 7 }
        .map { case (f, r) => Square(File.values(f), Rank.values(r)) }
        .foldLeft((List.empty[Square], false)):
          case ((acc, stopped), sq) =>
            if stopped then (acc, true)
            else if isOwnPiece(board, sq, color) then (acc, true)          // blocked — stop, no capture
            else if isEnemyPiece(board, sq, color) then (acc :+ sq, true)  // capture — stop after
            else (acc :+ sq, false)                                        // empty — continue
        ._1
    .toSet

  private def pawnTargets(board: Board, from: Square, color: Color): Set[Square] =
    val fi       = from.file.ordinal
    val ri       = from.rank.ordinal
    val dir      = if color == Color.White then 1 else -1
    val startRank = if color == Color.White then 1 else 6  // R2 = ordinal 1, R7 = ordinal 6

    val oneStep = squareAt(fi, ri + dir)

    // Forward one square (only if empty)
    val forward1: Set[Square] = oneStep match
      case Some(sq) if board.pieceAt(sq).isEmpty => Set(sq)
      case _                                     => Set.empty

    // Forward two squares from starting rank (only if both intermediate squares are empty)
    val forward2: Set[Square] =
      if ri == startRank && forward1.nonEmpty then
        squareAt(fi, ri + 2 * dir) match
          case Some(sq) if board.pieceAt(sq).isEmpty => Set(sq)
          case _                                     => Set.empty
      else Set.empty

    // Diagonal captures (only if enemy piece present)
    val captures: Set[Square] =
      List(-1, 1).flatMap: df =>
        squareAt(fi + df, ri + dir).filter(sq => isEnemyPiece(board, sq, color))
      .toSet

    forward1 ++ forward2 ++ captures

  private def knightTargets(board: Board, from: Square, color: Color): Set[Square] =
    val fi = from.file.ordinal
    val ri = from.rank.ordinal
    knightDeltas.flatMap: (df, dr) =>
      squareAt(fi + df, ri + dr).filterNot(sq => isOwnPiece(board, sq, color))
    .toSet

  private def kingTargets(board: Board, from: Square, color: Color): Set[Square] =
    val fi = from.file.ordinal
    val ri = from.rank.ordinal
    (diagonalDeltas ++ orthogonalDeltas).flatMap: (df, dr) =>
      squareAt(fi + df, ri + dr).filterNot(sq => isOwnPiece(board, sq, color))
    .toSet
