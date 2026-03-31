package de.nowchess.chess.notation

import de.nowchess.api.board.*
import de.nowchess.api.move.PromotionPiece
import de.nowchess.chess.logic.{CastleSide, GameHistory, HistoryMove, GameRules, MoveValidator, withCastle}

/** A parsed PGN game containing headers and the resolved move list. */
case class PgnGame(
  headers: Map[String, String],
  moves: List[HistoryMove]
)

object PgnParser:

  /** Parse a complete PGN text into a PgnGame with headers and moves.
   *  Always succeeds (returns Some); malformed tokens are silently skipped. */
  def parsePgn(pgn: String): Option[PgnGame] =
    val lines = pgn.split("\n").map(_.trim)
    val (headerLines, rest) = lines.span(_.startsWith("["))

    val headers  = parseHeaders(headerLines)
    val moveText = rest.mkString(" ")
    val moves    = parseMovesText(moveText)

    Some(PgnGame(headers, moves))

  /** Parse PGN header lines of the form [Key "Value"]. */
  private def parseHeaders(lines: Array[String]): Map[String, String] =
    val pattern = """^\[(\w+)\s+"([^"]*)"\s*\]$""".r
    lines.flatMap(line => pattern.findFirstMatchIn(line).map(m => m.group(1) -> m.group(2))).toMap

  /** Parse the move-text section (e.g. "1. e4 e5 2. Nf3") into resolved HistoryMoves. */
  private def parseMovesText(moveText: String): List[HistoryMove] =
    val tokens = moveText.split("\\s+").filter(_.nonEmpty)

    // Fold over tokens, threading (board, history, currentColor, accumulator)
    val (_, _, _, moves) = tokens.foldLeft(
      (Board.initial, GameHistory.empty, Color.White, List.empty[HistoryMove])
    ):
      case (state @ (board, history, color, acc), token) =>
        // Skip move-number markers (e.g. "1.", "2.") and result tokens
        if isMoveNumberOrResult(token) then state
        else
          parseAlgebraicMove(token, board, history, color) match
            case None       => state  // unrecognised token — skip silently
            case Some(move) =>
              val newBoard   = applyMoveToBoard(board, move, color)
              val newHistory = history.addMove(move)
              (newBoard, newHistory, color.opposite, acc :+ move)

    moves

  /** Apply a single HistoryMove to a Board, handling castling and promotion. */
  private def applyMoveToBoard(board: Board, move: HistoryMove, color: Color): Board =
    move.castleSide match
      case Some(side) => board.withCastle(color, side)
      case None =>
        val (boardAfterMove, _) = board.withMove(move.from, move.to)
        move.promotionPiece match
          case Some(pp) =>
            val pieceType = pp match
              case PromotionPiece.Queen  => PieceType.Queen
              case PromotionPiece.Rook   => PieceType.Rook
              case PromotionPiece.Bishop => PieceType.Bishop
              case PromotionPiece.Knight => PieceType.Knight
            boardAfterMove.updated(move.to, Piece(color, pieceType))
          case None => boardAfterMove

  /** True for move-number tokens ("1.", "12.") and PGN result tokens. */
  private def isMoveNumberOrResult(token: String): Boolean =
    token.matches("""\d+\.""") ||
    token == "*"               ||
    token == "1-0"             ||
    token == "0-1"             ||
    token == "1/2-1/2"

  /** Parse a single algebraic notation token into a HistoryMove, given the current board state. */
  def parseAlgebraicMove(notation: String, board: Board, history: GameHistory, color: Color): Option[HistoryMove] =
    notation match
      case "O-O" | "O-O+" | "O-O#" =>
        val rank = if color == Color.White then Rank.R1 else Rank.R8
        Some(HistoryMove(Square(File.E, rank), Square(File.G, rank), Some(CastleSide.Kingside)))

      case "O-O-O" | "O-O-O+" | "O-O-O#" =>
        val rank = if color == Color.White then Rank.R1 else Rank.R8
        Some(HistoryMove(Square(File.E, rank), Square(File.C, rank), Some(CastleSide.Queenside)))

      case _ =>
        parseRegularMove(notation, board, history, color)

  /** Parse regular algebraic notation (pawn moves, piece moves, captures, disambiguation). */
  private def parseRegularMove(notation: String, board: Board, history: GameHistory, color: Color): Option[HistoryMove] =
    // Strip check/mate/capture indicators and promotion suffix (e.g. =Q)
    val clean = notation
      .replace("+", "")
      .replace("#", "")
      .replace("x", "")
      .replaceAll("=[NBRQ]$", "")

    // The destination square is always the last two characters
    if clean.length < 2 then None
    else
      val destStr = clean.takeRight(2)
      Square.fromAlgebraic(destStr).flatMap: toSquare =>
        val disambig = clean.dropRight(2) // "" | "N"|"B"|"R"|"Q"|"K" | file | rank | file+rank

        // Determine required piece type: upper-case first char = piece letter; else pawn
        val requiredPieceType: Option[PieceType] =
          if disambig.nonEmpty && disambig.head.isUpper then charToPieceType(disambig.head)
          else if clean.head.isUpper then charToPieceType(clean.head)
          else Some(PieceType.Pawn)

        // Collect the disambiguation hint that remains after stripping the piece letter
        val hint =
          if disambig.nonEmpty && disambig.head.isUpper then disambig.tail
          else disambig  // hint is file/rank info or empty

        // Candidate source squares: pieces of `color` that can geometrically reach `toSquare`.
        // We prefer pieces that can actually reach the target; if none can (positionally illegal
        // PGN input), fall back to any piece of the matching type belonging to `color`.
        val reachable: Set[Square] =
          board.pieces.collect {
            case (from, piece) if piece.color == color &&
              MoveValidator.legalTargets(board, from).contains(toSquare) => from
          }.toSet

        val candidates: Set[Square] =
          if reachable.nonEmpty then reachable
          else
            // Fallback for positionally-illegal but syntactically valid PGN notation:
            // find any piece of `color` with the correct piece type on the board.
            board.pieces.collect {
              case (from, piece) if piece.color == color => from
            }.toSet

        // Filter by required piece type
        val byPiece = candidates.filter(from =>
          requiredPieceType.forall(pt => board.pieceAt(from).exists(_.pieceType == pt))
        )

        // Apply disambiguation hint (file letter or rank digit)
        val disambiguated =
          if hint.isEmpty then byPiece
          else byPiece.filter(from => matchesHint(from, hint))

        val promotion = extractPromotion(notation)
        disambiguated.headOption.map(from => HistoryMove(from, toSquare, None, promotion))

  /** True if `sq` matches a disambiguation hint (file letter, rank digit, or both). */
  private def matchesHint(sq: Square, hint: String): Boolean =
    hint.forall(c => if c >= 'a' && c <= 'h' then sq.file.toString.equalsIgnoreCase(c.toString)
    else if c >= '1' && c <= '8' then sq.rank.ordinal == (c - '1')
    else true)

  /** Extract a promotion piece from a notation string containing =Q/=R/=B/=N. */
  private[notation] def extractPromotion(notation: String): Option[PromotionPiece] =
    val promotionPattern = """=([A-Z])""".r
    promotionPattern.findFirstMatchIn(notation).flatMap { m =>
      m.group(1) match
        case "Q" => Some(PromotionPiece.Queen)
        case "R" => Some(PromotionPiece.Rook)
        case "B" => Some(PromotionPiece.Bishop)
        case "N" => Some(PromotionPiece.Knight)
        case _   => None
    }

  /** Convert a piece-letter character to a PieceType. */
  private def charToPieceType(c: Char): Option[PieceType] =
    c match
      case 'N' => Some(PieceType.Knight)
      case 'B' => Some(PieceType.Bishop)
      case 'R' => Some(PieceType.Rook)
      case 'Q' => Some(PieceType.Queen)
      case 'K' => Some(PieceType.King)
      case _   => None
