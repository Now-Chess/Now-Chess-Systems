package de.nowchess.io.fen

import de.nowchess.api.board.*
import de.nowchess.api.game.GameContext
import de.nowchess.api.io.GameContextExport

object FenExporter extends GameContextExport:

  /** Convert a Board to FEN piece-placement string (rank 8 to rank 1, separated by '/'). */
  def boardToFen(board: Board): String =
    Rank.values.reverse
      .map(rank => buildRankString(board, rank))
      .mkString("/")

  /** Build the FEN representation for a single rank. */
  private def buildRankString(board: Board, rank: Rank): String =
    val rankSquares = File.values.map(file => Square(file, rank))
    val (result, emptyCount) = rankSquares.foldLeft(("", 0)):
      case ((acc, empty), square) =>
        board.pieceAt(square) match
          case Some(piece) =>
            val flushed = if empty > 0 then acc + empty.toString else acc
            (flushed + pieceToFenChar(piece), 0)
          case None =>
            (acc, empty + 1)
    if emptyCount > 0 then result + emptyCount.toString else result

  /** Convert a GameContext to a complete FEN string. */
  def gameContextToFen(context: GameContext): String =
    val piecePlacement = boardToFen(context.board)
    val activeColor    = if context.turn == Color.White then "w" else "b"
    val castling       = castlingString(context.castlingRights)
    val enPassant      = context.enPassantSquare.map(_.toString).getOrElse("-")
    val fullMoveNumber = 1 + (context.moves.length / 2)
    s"$piecePlacement $activeColor $castling $enPassant ${context.halfMoveClock} $fullMoveNumber"

  def exportGameContext(context: GameContext): String = gameContextToFen(context)

  /** Convert castling rights to FEN notation. */
  private def castlingString(rights: CastlingRights): String =
    val wk     = if rights.whiteKingSide then "K" else ""
    val wq     = if rights.whiteQueenSide then "Q" else ""
    val bk     = if rights.blackKingSide then "k" else ""
    val bq     = if rights.blackQueenSide then "q" else ""
    val result = s"$wk$wq$bk$bq"
    if result.isEmpty then "-" else result

  /** Convert a Piece to its FEN character (uppercase = White, lowercase = Black). */
  private def pieceToFenChar(piece: Piece): Char =
    val base = piece.pieceType match
      case PieceType.Pawn   => 'p'
      case PieceType.Knight => 'n'
      case PieceType.Bishop => 'b'
      case PieceType.Rook   => 'r'
      case PieceType.Queen  => 'q'
      case PieceType.King   => 'k'
    if piece.color == Color.White then base.toUpper else base
