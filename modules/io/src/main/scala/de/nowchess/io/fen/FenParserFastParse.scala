package de.nowchess.io.fen

import fastparse.*
import fastparse.NoWhitespace.*
import de.nowchess.api.board.*
import de.nowchess.api.error.GameError
import de.nowchess.api.game.GameContext
import FenParserSupport.*
import de.nowchess.api.io.GameContextImport

object FenParserFastParse extends GameContextImport:

  // ── Low-level parsers ────────────────────────────────────────────────────

  private def pieceChar(using P[Any]): P[Piece] =
    CharIn("prnbqkPRNBQK").!.map { s =>
      val c     = s.head
      val color = if c.isUpper then Color.White else Color.Black
      Piece(color, charToPieceType(c.toLower))
    }

  private def emptyCount(using P[Any]): P[Int] =
    CharIn("1-8").!.map(_.toInt)

  private def rankToken(using P[Any]): P[RankToken] =
    pieceChar.map(PieceToken.apply) | emptyCount.map(EmptyToken.apply)

  // ── Rank parser ──────────────────────────────────────────────────────────

  private def rankParser(rank: Rank)(using P[Any]): P[List[(Square, Piece)]] =
    rankToken.rep(1).flatMap { tokens =>
      buildSquares(rank, tokens) match
        case Some(squares) => Pass(squares)
        case None          => Fail
    }

  // ── Board parser ─────────────────────────────────────────────────────────

  private def sep(using P[Any]): P[Unit] = LiteralStr("/").map(_ => ())

  private def boardParser(using P[Any]): P[Board] =
    (rankParser(Rank.R8) ~ sep ~
      rankParser(Rank.R7) ~ sep ~
      rankParser(Rank.R6) ~ sep ~
      rankParser(Rank.R5) ~ sep ~
      rankParser(Rank.R4) ~ sep ~
      rankParser(Rank.R3) ~ sep ~
      rankParser(Rank.R2) ~ sep ~
      rankParser(Rank.R1)).map { case (r8, r7, r6, r5, r4, r3, r2, r1) =>
      Board((r8 ++ r7 ++ r6 ++ r5 ++ r4 ++ r3 ++ r2 ++ r1).toMap)
    }

  // ── Color parser ─────────────────────────────────────────────────────────

  private def colorParser(using P[Any]): P[Color] =
    (LiteralStr("w") | LiteralStr("b")).!.map {
      case "w" => Color.White
      case _   => Color.Black
    }

  // ── Castling parser ──────────────────────────────────────────────────────

  private def castlingParser(using P[Any]): P[CastlingRights] =
    LiteralStr("-").map(_ => CastlingRights.None) |
      CharsWhileIn("KQkq").!.map { s =>
        CastlingRights(
          whiteKingSide = s.contains('K'),
          whiteQueenSide = s.contains('Q'),
          blackKingSide = s.contains('k'),
          blackQueenSide = s.contains('q'),
        )
      }

  // ── En passant parser ────────────────────────────────────────────────────

  private def enPassantParser(using P[Any]): P[Option[Square]] =
    LiteralStr("-").map(_ => Option.empty[Square]) |
      (CharIn("a-h") ~ CharIn("1-8")).!.map(s => Square.fromAlgebraic(s))

  // ── Clock parser ─────────────────────────────────────────────────────────

  private def clockParser(using P[Any]): P[Int] =
    CharsWhileIn("0-9").!.map(_.toInt)

  // ── Space helper ─────────────────────────────────────────────────────────

  private def sp(using P[Any]): P[Unit] = LiteralStr(" ").map(_ => ())

  // ── Full FEN parser ──────────────────────────────────────────────────────

  private def fenParser(using P[Any]): P[GameContext] =
    (boardParser ~ sp ~ colorParser ~ sp ~ castlingParser ~ sp ~
      enPassantParser ~ sp ~ clockParser ~ sp ~ clockParser ~ End).map {
      case (board, color, castling, ep, halfMove, _) =>
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

  def parseFen(fen: String): Either[GameError, GameContext] =
    parse(fen, fenParser(using _)) match
      case Parsed.Success(ctx, _) => Right(ctx)
      case f: Parsed.Failure      => Left(GameError.ParseError(s"Invalid FEN: ${f.msg}"))

  private def boardParserFull(using P[Any]): P[Board] =
    boardParser ~ End

  def parseBoard(fen: String): Option[Board] =
    parse(fen, boardParserFull(using _)) match
      case Parsed.Success(board, _) => Some(board)
      case _                        => None

  def importGameContext(input: String): Either[GameError, GameContext] =
    parseFen(input)
