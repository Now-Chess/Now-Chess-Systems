package de.nowchess.chess.notation

import de.nowchess.api.board.*
import de.nowchess.api.game.{CastlingRights, GameState}
import de.nowchess.api.board.Color

object FenExporter:

  /** Convert a Board to FEN piece-placement string (rank 8 to rank 1, separated by '/'). */
  def boardToFen(board: Board): String =
    Rank.values.reverse
      .map(rank => buildRankString(board, rank))
      .mkString("/")

  /** Build the FEN representation for a single rank. */
  private def buildRankString(board: Board, rank: Rank): String =
    val rankSquares = File.values.map(file => Square(file, rank))
    val rankChars = scala.collection.mutable.ListBuffer[Char]()
    var emptyCount = 0

    for square <- rankSquares do
      board.pieceAt(square) match
        case Some(piece) =>
          if emptyCount > 0 then
            rankChars += emptyCount.toString.charAt(0)
            emptyCount = 0
          rankChars += pieceToPgnChar(piece)
        case None =>
          emptyCount += 1

    if emptyCount > 0 then rankChars += emptyCount.toString.charAt(0)
    rankChars.mkString

  /** Convert a GameState to a complete FEN string. */
  def gameStateToFen(state: GameState): String =
    val piecePlacement = state.piecePlacement
    val activeColor = if state.activeColor == Color.White then "w" else "b"
    val castling = castlingString(state.castlingWhite, state.castlingBlack)
    val enPassant = state.enPassantTarget.map(_.toString).getOrElse("-")
    s"$piecePlacement $activeColor $castling $enPassant ${state.halfMoveClock} ${state.fullMoveNumber}"

  /** Convert castling rights to FEN notation. */
  private def castlingString(white: CastlingRights, black: CastlingRights): String =
    val wk = if white.kingSide then "K" else ""
    val wq = if white.queenSide then "Q" else ""
    val bk = if black.kingSide then "k" else ""
    val bq = if black.queenSide then "q" else ""
    val result = s"$wk$wq$bk$bq"
    if result.isEmpty then "-" else result

  /** Convert a Piece to its FEN character (uppercase = White, lowercase = Black). */
  private def pieceToPgnChar(piece: Piece): Char =
    val base = piece.pieceType match
      case PieceType.Pawn   => 'p'
      case PieceType.Knight => 'n'
      case PieceType.Bishop => 'b'
      case PieceType.Rook   => 'r'
      case PieceType.Queen  => 'q'
      case PieceType.King   => 'k'
    if piece.color == Color.White then base.toUpper else base
