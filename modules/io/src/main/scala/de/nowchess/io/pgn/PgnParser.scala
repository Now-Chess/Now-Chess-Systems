package de.nowchess.io.pgn

import de.nowchess.api.board.*
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.game.GameContext
import de.nowchess.io.GameContextImport
import de.nowchess.rules.sets.DefaultRules

/** A parsed PGN game containing headers and the resolved move list. */
case class PgnGame(
    headers: Map[String, String],
    moves: List[Move],
)

object PgnParser extends GameContextImport:

  /** Strictly validate a PGN text. Returns Right(PgnGame) if every move token is a legal move in the evolving position.
    * Returns Left(error message) on the first illegal or impossible move, or any unrecognised token.
    */
  def validatePgn(pgn: String): Either[String, PgnGame] =
    val lines               = pgn.split("\n").map(_.trim)
    val (headerLines, rest) = lines.span(_.startsWith("["))
    val headers             = parseHeaders(headerLines)
    val moveText            = rest.mkString(" ")
    validateMovesText(moveText).map(moves => PgnGame(headers, moves))

  /** Import a PGN text into a GameContext by validating and replaying all moves. Returns Right(GameContext) with all
    * moves applied and .moves populated. Returns Left(error message) if validation fails or move replay encounters an
    * issue.
    */
  def importGameContext(input: String): Either[String, GameContext] =
    validatePgn(input).flatMap { game =>
      Right(game.moves.foldLeft(GameContext.initial)((ctx, move) => DefaultRules.applyMove(ctx)(move)))
    }

  /** Parse a complete PGN text into a PgnGame with headers and moves. Always succeeds (returns Some); malformed tokens
    * are silently skipped.
    */
  def parsePgn(pgn: String): Option[PgnGame] =
    val lines               = pgn.split("\n").map(_.trim)
    val (headerLines, rest) = lines.span(_.startsWith("["))
    val headers             = parseHeaders(headerLines)
    val moveText            = rest.mkString(" ")
    val moves               = parseMovesText(moveText)
    Some(PgnGame(headers, moves))

  /** Parse PGN header lines of the form [Key "Value"]. */
  private def parseHeaders(lines: Array[String]): Map[String, String] =
    val pattern = """^\[(\w+)\s+"([^"]*)"\s*]$""".r
    lines.flatMap(line => pattern.findFirstMatchIn(line).map(m => m.group(1) -> m.group(2))).toMap

  /** Parse the move-text section (e.g. "1. e4 e5 2. Nf3") into resolved Moves. */
  private def parseMovesText(moveText: String): List[Move] =
    val tokens = moveText.split("\\s+").filter(_.nonEmpty)
    val (_, _, moves) = tokens.foldLeft(
      (GameContext.initial, Color.White, List.empty[Move]),
    ):
      case (state @ (ctx, color, acc), token) =>
        if isMoveNumberOrResult(token) then state
        else
          parseAlgebraicMove(token, ctx, color) match
            case None => state
            case Some(move) =>
              val nextCtx = DefaultRules.applyMove(ctx)(move)
              (nextCtx, color.opposite, acc :+ move)
    moves

  /** True for move-number tokens ("1.", "12.") and PGN result tokens. */
  private def isMoveNumberOrResult(token: String): Boolean =
    token.matches("""\d+\.""") ||
      token == "*" ||
      token == "1-0" ||
      token == "0-1" ||
      token == "1/2-1/2"

  /** Parse a single algebraic notation token into a Move, given the current game context. */
  def parseAlgebraicMove(notation: String, ctx: GameContext, color: Color): Option[Move] =
    notation match
      case "O-O" | "O-O+" | "O-O#" =>
        val rank = if color == Color.White then Rank.R1 else Rank.R8
        val move = Move(Square(File.E, rank), Square(File.G, rank), MoveType.CastleKingside)
        Option.when(DefaultRules.legalMoves(ctx)(Square(File.E, rank)).contains(move))(move)

      case "O-O-O" | "O-O-O+" | "O-O-O#" =>
        val rank = if color == Color.White then Rank.R1 else Rank.R8
        val move = Move(Square(File.E, rank), Square(File.C, rank), MoveType.CastleQueenside)
        Option.when(DefaultRules.legalMoves(ctx)(Square(File.E, rank)).contains(move))(move)

      case _ =>
        parseRegularMove(notation, ctx, color)

  /** Parse regular algebraic notation (pawn moves, piece moves, captures, disambiguation). */
  private def parseRegularMove(notation: String, ctx: GameContext, color: Color): Option[Move] =
    val clean = notation
      .replace("+", "")
      .replace("#", "")
      .replace("x", "")
      .replaceAll("=[NBRQ]$", "")

    if clean.length < 2 then None
    else
      val destStr = clean.takeRight(2)
      Square
        .fromAlgebraic(destStr)
        .flatMap: toSquare =>
          val disambig = clean.dropRight(2)

          val requiredPieceType: Option[PieceType] =
            if disambig.nonEmpty && disambig.head.isUpper then charToPieceType(disambig.head)
            else if clean.head.isUpper then charToPieceType(clean.head)
            else Some(PieceType.Pawn)

          val hint =
            if disambig.nonEmpty && disambig.head.isUpper then disambig.tail
            else disambig

          val promotion = extractPromotion(notation)

          // Get all legal moves for this color that reach toSquare
          val allLegal = DefaultRules.allLegalMoves(ctx)
          val candidates = allLegal.filter { move =>
            move.to == toSquare &&
            ctx.board
              .pieceAt(move.from)
              .exists(p =>
                p.color == color &&
                  requiredPieceType.forall(_ == p.pieceType),
              ) &&
            (hint.isEmpty || matchesHint(move.from, hint)) &&
            promotionMatches(move, promotion)
          }

          candidates.headOption

  /** True if `sq` matches a disambiguation hint (file letter, rank digit, or both). */
  private def matchesHint(sq: Square, hint: String): Boolean =
    hint.forall(c =>
      if c >= 'a' && c <= 'h' then sq.file.toString.equalsIgnoreCase(c.toString)
      else if c >= '1' && c <= '8' then sq.rank.ordinal == (c - '1')
      else true,
    )

  private def promotionMatches(move: Move, promotion: Option[PromotionPiece]): Boolean =
    promotion match
      case None =>
        move.moveType match
          case MoveType.Normal(_) | MoveType.EnPassant | MoveType.CastleKingside | MoveType.CastleQueenside => true
          case _                                                                                            => false
      case Some(pp) => move.moveType == MoveType.Promotion(pp)

  /** Extract a promotion piece from a notation string containing =Q/=R/=B/=N. */
  private[pgn] def extractPromotion(notation: String): Option[PromotionPiece] =
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

  // ── Strict validation helpers ─────────────────────────────────────────────

  /** Walk all move tokens, failing immediately on any unresolvable or illegal move. */
  private def validateMovesText(moveText: String): Either[String, List[Move]] =
    val tokens = moveText.split("\\s+").filter(_.nonEmpty)
    tokens
      .foldLeft(
        Right((GameContext.initial, Color.White, List.empty[Move])): Either[String, (GameContext, Color, List[Move])],
      ) { case (acc, token) =>
        acc.flatMap { case (ctx, color, moves) =>
          if isMoveNumberOrResult(token) then Right((ctx, color, moves))
          else
            parseAlgebraicMove(token, ctx, color) match
              case None => Left(s"Illegal or impossible move: '$token'")
              case Some(move) =>
                val nextCtx = DefaultRules.applyMove(ctx)(move)
                Right((nextCtx, color.opposite, moves :+ move))
        }
      }
      .map(_._3)
