package de.nowchess.io.fen

import de.nowchess.api.board.*
import de.nowchess.api.error.GameError
import de.nowchess.api.game.GameContext
import de.nowchess.api.io.GameContextImport

object FenParser extends GameContextImport:

  /** Parse a complete FEN string into a GameContext. Returns Left with error message if the format is invalid.
    */
  def parseFen(fen: String): Either[GameError, GameContext] =
    val parts = fen.trim.split("\\s+")
    if parts.length != 6 then
      Left(GameError.ParseError(s"Invalid FEN: expected 6 space-separated fields, got ${parts.length}"))
    else
      for
        board <- parseBoard(parts(0)).toRight(GameError.ParseError("Invalid FEN: invalid board position"))
        activeColor <- parseColor(parts(1)).toRight(
          GameError.ParseError("Invalid FEN: invalid active color (expected 'w' or 'b')"),
        )
        castlingRights <- parseCastling(parts(2)).toRight(GameError.ParseError("Invalid FEN: invalid castling rights"))
        enPassant <- parseEnPassant(parts(3)).toRight(GameError.ParseError("Invalid FEN: invalid en passant square"))
        halfMoveClock <- parts(4).toIntOption.toRight(
          GameError.ParseError("Invalid FEN: invalid half-move clock (expected integer)"),
        )
        fullMoveNumber <- parts(5).toIntOption.toRight(
          GameError.ParseError("Invalid FEN: invalid full move number (expected integer)"),
        )
        _ <- Either.cond(
          halfMoveClock >= 0 && fullMoveNumber >= 1,
          (),
          GameError.ParseError("Invalid FEN: invalid move counts"),
        )
      yield GameContext(
        board = board,
        turn = activeColor,
        castlingRights = castlingRights,
        enPassantSquare = enPassant,
        halfMoveClock = halfMoveClock,
        moves = List.empty,
      )

  def importGameContext(input: String): Either[GameError, GameContext] =
    parseFen(input)

  /** Parse active color ("w" or "b"). */
  private def parseColor(s: String): Option[Color] =
    if s == "w" then Some(Color.White)
    else if s == "b" then Some(Color.Black)
    else None

  /** Parse castling rights string (e.g. "KQkq", "K", "-") into unified castling rights. */
  private def parseCastling(s: String): Option[CastlingRights] =
    if s == "-" then Some(CastlingRights.None)
    else if s.length <= 4 && s.forall(c => "KQkq".contains(c)) then
      Some(
        CastlingRights(
          whiteKingSide = s.contains('K'),
          whiteQueenSide = s.contains('Q'),
          blackKingSide = s.contains('k'),
          blackQueenSide = s.contains('q'),
        ),
      )
    else None

  /** Parse en passant target square ("-" for none, or algebraic like "e3"). */
  private def parseEnPassant(s: String): Option[Option[Square]] =
    if s == "-" then Some(None)
    else Square.fromAlgebraic(s).map(Some(_))

  /** Parses a FEN piece-placement string (rank 8 to rank 1, separated by '/') into a Board. Returns None if the format
    * is invalid.
    */
  def parseBoard(fen: String): Option[Board] =
    val rankStrings = fen.split("/", -1)
    if rankStrings.length != 8 then None
    else
      // Parse each rank, collecting all (Square, Piece) pairs or failing on the first error
      val parsedRanks: Option[List[List[(Square, Piece)]]] =
        rankStrings.zipWithIndex.foldLeft(Option(List.empty[List[(Square, Piece)]])):
          case (None, _) => None
          case (Some(acc), (rankStr, rankIdx)) =>
            val rank = Rank.values(7 - rankIdx) // ranks go 8→1, so reverse
            parsePieceRank(rankStr, rank).map(squares => acc :+ squares)
      parsedRanks.map(ranks => Board(ranks.flatten.toMap))

  /** Parse a single rank string (e.g. "rnbqkbnr" or "p3p3") into a list of (Square, Piece) pairs. Returns None if the
    * rank string contains invalid characters or the wrong number of files.
    */
  private def parsePieceRank(rankStr: String, rank: Rank): Option[List[(Square, Piece)]] =
    val (fileIdx, failed, squares) = rankStr.foldLeft((0, false, List.empty[(Square, Piece)])):
      case ((idx, true, acc), _) => (idx, true, acc)
      case ((idx, false, acc), c) =>
        if idx > 7 then (idx, true, acc)
        else if c.isDigit then (idx + c.asDigit, false, acc)
        else
          charToPiece(c) match
            case None => (idx, true, acc)
            case Some(piece) =>
              (idx + 1, false, acc :+ (Square(File.values(idx), rank) -> piece))
    if failed || fileIdx != 8 then None
    else Some(squares)

  /** Convert a FEN piece character to a Piece. Uppercase = White, lowercase = Black. */
  private def charToPiece(c: Char): Option[Piece] =
    val color = if Character.isUpperCase(c) then Color.White else Color.Black
    val pieceTypeOpt = c.toLower match
      case 'p' => Some(PieceType.Pawn)
      case 'n' => Some(PieceType.Knight)
      case 'b' => Some(PieceType.Bishop)
      case 'r' => Some(PieceType.Rook)
      case 'q' => Some(PieceType.Queen)
      case 'k' => Some(PieceType.King)
      case _   => None
    pieceTypeOpt.map(pt => Piece(color, pt))
