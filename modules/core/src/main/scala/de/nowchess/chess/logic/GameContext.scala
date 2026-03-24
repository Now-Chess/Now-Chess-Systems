package de.nowchess.chess.logic

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.game.CastlingRights

enum CastleSide:
  case Kingside, Queenside

case class GameContext(
  board: Board,
  whiteCastling: CastlingRights,
  blackCastling: CastlingRights
):
  def castlingFor(color: Color): CastlingRights =
    if color == Color.White then whiteCastling else blackCastling

  def withUpdatedRights(color: Color, rights: CastlingRights): GameContext =
    if color == Color.White then copy(whiteCastling = rights)
    else copy(blackCastling = rights)

object GameContext:
  /** Convenience constructor for test boards: no castling rights on either side. */
  def apply(board: Board): GameContext =
    GameContext(board, CastlingRights.None, CastlingRights.None)

  val initial: GameContext =
    GameContext(Board.initial, CastlingRights.Both, CastlingRights.Both)

extension (b: Board)
  def withCastle(color: Color, side: CastleSide): Board =
    val (kingFrom, kingTo, rookFrom, rookTo) = (color, side) match
      case (Color.White, CastleSide.Kingside)  =>
        (Square(File.E, Rank.R1), Square(File.G, Rank.R1),
         Square(File.H, Rank.R1), Square(File.F, Rank.R1))
      case (Color.White, CastleSide.Queenside) =>
        (Square(File.E, Rank.R1), Square(File.C, Rank.R1),
         Square(File.A, Rank.R1), Square(File.D, Rank.R1))
      case (Color.Black, CastleSide.Kingside)  =>
        (Square(File.E, Rank.R8), Square(File.G, Rank.R8),
         Square(File.H, Rank.R8), Square(File.F, Rank.R8))
      case (Color.Black, CastleSide.Queenside) =>
        (Square(File.E, Rank.R8), Square(File.C, Rank.R8),
         Square(File.A, Rank.R8), Square(File.D, Rank.R8))
    val king = Piece(color, PieceType.King)
    val rook = Piece(color, PieceType.Rook)
    Board(b.pieces.removed(kingFrom).removed(rookFrom)
                  .updated(kingTo, king).updated(rookTo, rook))
