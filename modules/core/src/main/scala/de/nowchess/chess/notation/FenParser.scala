package de.nowchess.chess.notation

import de.nowchess.api.board.*
import de.nowchess.api.game.{CastlingRights, GameState, GameStatus}

object FenParser:

  /** Parse a complete FEN string into a GameState.
   *  Returns None if the format is invalid. */
  def parseFen(fen: String): Option[GameState] =
    val parts = fen.trim.split("\\s+")
    Option.when(parts.length == 6)(parts).flatMap: parts =>
      for
        _ <- parseBoard(parts(0))
        activeColor <- parseColor(parts(1))
        castlingRights <- parseCastling(parts(2))
        enPassant <- parseEnPassant(parts(3))
        halfMoveClock <- parts(4).toIntOption
        fullMoveNumber <- parts(5).toIntOption
        if halfMoveClock >= 0 && fullMoveNumber >= 1
      yield GameState(
        piecePlacement = parts(0),
        activeColor = activeColor,
        castlingWhite = castlingRights._1,
        castlingBlack = castlingRights._2,
        enPassantTarget = enPassant,
        halfMoveClock = halfMoveClock,
        fullMoveNumber = fullMoveNumber,
        status = GameStatus.InProgress
      )

  /** Parse active color ("w" or "b"). */
  private def parseColor(s: String): Option[Color] =
    if s == "w" then Some(Color.White)
    else if s == "b" then Some(Color.Black)
    else None

  /** Parse castling rights string (e.g. "KQkq", "K", "-") into rights for White and Black. */
  private def parseCastling(s: String): Option[(CastlingRights, CastlingRights)] =
    if s == "-" then
      Some((CastlingRights.None, CastlingRights.None))
    else if s.length <= 4 && s.forall(c => "KQkq".contains(c)) then
      val white = CastlingRights(kingSide = s.contains('K'), queenSide = s.contains('Q'))
      val black = CastlingRights(kingSide = s.contains('k'), queenSide = s.contains('q'))
      Some((white, black))
    else
      None

  /** Parse en passant target square ("-" for none, or algebraic like "e3"). */
  private def parseEnPassant(s: String): Option[Option[Square]] =
    if s == "-" then Some(None)
    else Square.fromAlgebraic(s).map(Some(_))

  /** Parses a FEN piece-placement string (rank 8 to rank 1, separated by '/') into a Board.
   *  Returns None if the format is invalid. */
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

  /** Parse a single rank string (e.g. "rnbqkbnr" or "p3p3") into a list of (Square, Piece) pairs.
   *  Returns None if the rank string contains invalid characters or the wrong number of files. */
  private def parsePieceRank(rankStr: String, rank: Rank): Option[List[(Square, Piece)]] =
    var fileIdx = 0
    val squares = scala.collection.mutable.ListBuffer[(Square, Piece)]()
    var failed = false

    for c <- rankStr if !failed do
      if fileIdx > 7 then
        failed = true
      else if c.isDigit then
        fileIdx += c.asDigit
      else
        charToPiece(c) match
          case None => failed = true
          case Some(piece) =>
            val file = File.values(fileIdx)
            squares += (Square(file, rank) -> piece)
            fileIdx += 1

    if failed || fileIdx != 8 then None
    else Some(squares.toList)

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
