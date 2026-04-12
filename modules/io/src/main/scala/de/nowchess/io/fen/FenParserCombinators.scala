package de.nowchess.io.fen

import de.nowchess.api.board.*
import de.nowchess.api.game.GameContext
import de.nowchess.io.GameContextImport
import scala.util.parsing.combinator.RegexParsers
import FenParserSupport.*

object FenParserCombinators extends RegexParsers with GameContextImport:

  override val skipWhitespace: Boolean = false

  // ── Piece character ──────────────────────────────────────────────────────

  private def pieceChar: Parser[Piece] =
    "[prnbqkPRNBQK]".r ^^ { s =>
      val c     = s.head
      val color = if c.isUpper then Color.White else Color.Black
      Piece(color, charToPieceType(c.toLower))
    }

  private def emptyCount: Parser[Int] =
    "[1-8]".r ^^ { s => s.toInt }

  // ── Rank parser ──────────────────────────────────────────────────────────

  private def rankToken: Parser[RankToken] =
    pieceChar ^^ PieceToken.apply | emptyCount ^^ EmptyToken.apply

  private def rankTokens: Parser[List[RankToken]] = rep1(rankToken)

  /** Parse rank string for a given Rank, producing (Square, Piece) pairs. Fails if total file count != 8 or any piece
    * placement exceeds board bounds.
    */
  private def rankParser(rank: Rank): Parser[List[(Square, Piece)]] =
    rankTokens >> { tokens =>
      buildSquares(rank, tokens) match
        case Some(squares) => success(squares)
        case None          => failure(s"Rank $rank is invalid")
    }

  // ── Board parser ─────────────────────────────────────────────────────────

  private def rankSep: Parser[String] = "/"

  /** Parse all 8 rank strings separated by '/', rank 8 down to rank 1. */
  private def boardParser: Parser[Board] =
    rankParser(Rank.R8) ~
      (rankSep ~> rankParser(Rank.R7)) ~
      (rankSep ~> rankParser(Rank.R6)) ~
      (rankSep ~> rankParser(Rank.R5)) ~
      (rankSep ~> rankParser(Rank.R4)) ~
      (rankSep ~> rankParser(Rank.R3)) ~
      (rankSep ~> rankParser(Rank.R2)) ~
      (rankSep ~> rankParser(Rank.R1)) ^^ { case r8 ~ r7 ~ r6 ~ r5 ~ r4 ~ r3 ~ r2 ~ r1 =>
        Board((r8 ++ r7 ++ r6 ++ r5 ++ r4 ++ r3 ++ r2 ++ r1).toMap)
      }

  // ── Color parser ─────────────────────────────────────────────────────────

  private def colorParser: Parser[Color] =
    ("w" | "b") ^^ {
      case "w" => Color.White
      case _   => Color.Black
    }

  // ── Castling parser ──────────────────────────────────────────────────────

  private def castlingParser: Parser[CastlingRights] =
    "-" ^^^ CastlingRights.None |
      "[KQkq]{1,4}".r ^^ { s =>
        CastlingRights(
          whiteKingSide = s.contains('K'),
          whiteQueenSide = s.contains('Q'),
          blackKingSide = s.contains('k'),
          blackQueenSide = s.contains('q'),
        )
      }

  // ── En passant parser ────────────────────────────────────────────────────

  private def enPassantParser: Parser[Option[Square]] =
    "-" ^^^ Option.empty[Square] |
      "[a-h][1-8]".r ^^ { s => Square.fromAlgebraic(s) }

  // ── Clock parser ─────────────────────────────────────────────────────────

  private def clockParser: Parser[Int] =
    """\d+""".r ^^ { _.toInt }

  // ── Full FEN parser ──────────────────────────────────────────────────────

  private def fenParser: Parser[GameContext] =
    boardParser ~ (" " ~> colorParser) ~ (" " ~> castlingParser) ~
      (" " ~> enPassantParser) ~ (" " ~> clockParser) ~ (" " ~> clockParser) ^^ {
        case board ~ color ~ castling ~ ep ~ halfMove ~ _ =>
          GameContext(
            board = board,
            turn = color,
            castlingRights = castling,
            enPassantSquare = ep,
            halfMoveClock = halfMove,
            moves = List.empty,
          )
      }

  // ── Public API ───────────────────────────────────────────────────────────

  def parseFen(fen: String): Either[String, GameContext] =
    parseAll(fenParser, fen) match
      case Success(ctx, _) => Right(ctx)
      case other           => Left(s"Invalid FEN: ${other.toString}")

  def parseBoard(fen: String): Option[Board] =
    parseAll(boardParser, fen) match
      case Success(board, _) => Some(board)
      case _                 => None

  def importGameContext(input: String): Either[String, GameContext] =
    parseFen(input)
