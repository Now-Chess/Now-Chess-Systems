package de.nowchess.io.json

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.nowchess.api.board.*
import de.nowchess.api.error.GameError
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.game.GameContext
import de.nowchess.api.io.GameContextImport

import scala.util.Try

/** Imports a GameContext from JSON format using Jackson.
  *
  * Parses JSON exported by JsonExporter and reconstructs the GameContext including:
  *   - Board state
  *   - Current turn
  *   - Castling rights
  *   - En passant square
  *   - Half-move clock
  *   - Move history
  *
  * Returns Left(error message) if the JSON is malformed or invalid.
  */
object JsonParser extends GameContextImport:

  private val mapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def importGameContext(input: String): Either[GameError, GameContext] =
    Try(mapper.readValue(input, classOf[JsonGameRecord])).toEither.left
      .map(e => GameError.ParseError("JSON parsing error: " + e.getMessage))
      .flatMap { data =>
        val gs       = data.gameState.getOrElse(JsonGameState())
        val rawBoard = gs.board.getOrElse(Nil)
        val rawTurn  = gs.turn.getOrElse("White")
        val rawCr    = gs.castlingRights.getOrElse(JsonCastlingRights())
        val rawHmc   = gs.halfMoveClock.getOrElse(0)
        val rawMoves = data.moves.getOrElse(Nil)

        for
          board <- parseBoard(rawBoard)
          turn  <- parseTurn(rawTurn)
          castlingRights  = parseCastlingRights(rawCr)
          enPassantSquare = gs.enPassantSquare.flatMap(s => Square.fromAlgebraic(s))
          moves <- parseMoves(rawMoves)
        yield GameContext(
          board = board,
          turn = turn,
          castlingRights = castlingRights,
          enPassantSquare = enPassantSquare,
          halfMoveClock = rawHmc,
          moves = moves,
        )
      }

  private def parseBoard(pieces: List[JsonPiece]): Either[GameError, Board] =
    val parsedPieces = pieces.flatMap { p =>
      for
        sq    <- p.square.flatMap(Square.fromAlgebraic)
        color <- p.color.flatMap(parseColor)
        pt    <- p.piece.flatMap(parsePieceType)
      yield (sq, Piece(color, pt))
    }
    Right(Board(parsedPieces.toMap))

  private def parseTurn(color: String): Either[GameError, Color] =
    parseColor(color).toRight(GameError.ParseError(s"Invalid turn color: $color"))

  private def parseColor(color: String): Option[Color] =
    if color == "White" then Some(Color.White)
    else if color == "Black" then Some(Color.Black)
    else None

  private def parsePieceType(pt: String): Option[PieceType] =
    pt match
      case "Pawn"   => Some(PieceType.Pawn)
      case "Knight" => Some(PieceType.Knight)
      case "Bishop" => Some(PieceType.Bishop)
      case "Rook"   => Some(PieceType.Rook)
      case "Queen"  => Some(PieceType.Queen)
      case "King"   => Some(PieceType.King)
      case _        => None

  private def parseCastlingRights(cr: JsonCastlingRights): CastlingRights =
    CastlingRights(
      cr.whiteKingSide.getOrElse(false),
      cr.whiteQueenSide.getOrElse(false),
      cr.blackKingSide.getOrElse(false),
      cr.blackQueenSide.getOrElse(false),
    )

  private def parseMoves(moves: List[JsonMove]): Either[GameError, List[Move]] =
    Right(moves.flatMap { m =>
      for
        from     <- m.from.flatMap(Square.fromAlgebraic)
        to       <- m.to.flatMap(Square.fromAlgebraic)
        moveType <- m.`type`.flatMap(parseMoveType)
      yield Move(from, to, moveType)
    })

  private def parseMoveType(mt: JsonMoveType): Option[MoveType] =
    mt.`type` match
      case Some("normal") =>
        Some(MoveType.Normal(mt.isCapture.getOrElse(false)))
      case Some("castleKingside") =>
        Some(MoveType.CastleKingside)
      case Some("castleQueenside") =>
        Some(MoveType.CastleQueenside)
      case Some("enPassant") =>
        Some(MoveType.EnPassant)
      case Some("promotion") =>
        val piece = mt.promotionPiece match
          case Some("queen")  => PromotionPiece.Queen
          case Some("rook")   => PromotionPiece.Rook
          case Some("bishop") => PromotionPiece.Bishop
          case Some("knight") => PromotionPiece.Knight
          case _              => PromotionPiece.Queen // default
        Some(MoveType.Promotion(piece))
      case _ => None
