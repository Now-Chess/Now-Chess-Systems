package de.nowchess.io.json

import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.core.util.{DefaultIndenter, DefaultPrettyPrinter}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.nowchess.api.board.*
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.game.GameContext
import de.nowchess.io.GameContextExport
import de.nowchess.io.pgn.PgnExporter
import java.time.{LocalDate, ZonedDateTime, ZoneId}

/** Exports a GameContext to a comprehensive JSON format using Jackson.
 *
 *  The JSON includes:
 *  - Game metadata (players, event, date, result)
 *  - Board state (all pieces and their positions)
 *  - Current game state (turn, castling rights, en passant, half-move clock)
 *  - Move history in both algebraic notation (PGN) and detailed move objects
 *  - Captured pieces tracking (which pieces have been removed)
 *  - Timestamp for record-keeping
 */
object JsonExporter extends GameContextExport:
  private val mapper = createMapper()
  
  private def createMapper(): ObjectMapper =
    val mapper = new ObjectMapper()
      .registerModule(DefaultScalaModule)
    
    // Configure pretty printer with custom spacing to match test expectations
    val indenter = new DefaultIndenter("  ", "\n")
    val printer = new DefaultPrettyPrinter()
    printer.indentArraysWith(indenter)
    printer.indentObjectsWith(indenter)
    
    mapper.setDefaultPrettyPrinter(printer)
    mapper.enable(SerializationFeature.INDENT_OUTPUT)
    mapper

  def exportGameContext(context: GameContext): String =
    val record = buildGameRecord(context)
    formatJson(mapper.writeValueAsString(record))

  private def buildGameRecord(context: GameContext): JsonGameRecord =
    val pgn = try {
      Some(PgnExporter.exportGameContext(context))
    } catch {
      case _: Exception => None
    }
    JsonGameRecord(
      metadata = Some(buildMetadata()),
      gameState = Some(buildGameState(context)),
      moveHistory = pgn,
      moves = Some(buildMoves(context.moves)),
      capturedPieces = Some(buildCapturedPieces(context.board)),
      timestamp = Some(ZonedDateTime.now(ZoneId.of("UTC")).toString)
    )

  private def buildMetadata(): JsonMetadata =
    JsonMetadata(
      event = Some("Game"),
      players = Some(Map("white" -> "White Player", "black" -> "Black Player")),
      date = Some(LocalDate.now().toString),
      result = Some("*")
    )

  private def buildGameState(context: GameContext): JsonGameState =
    JsonGameState(
      board = Some(buildBoardPieces(context.board)),
      turn = Some(context.turn.label),
      castlingRights = Some(buildCastlingRights(context.castlingRights)),
      enPassantSquare = context.enPassantSquare.map(_.toString),
      halfMoveClock = Some(context.halfMoveClock)
    )

  private def buildBoardPieces(board: Board): List[JsonPiece] =
    board.pieces.toList.map { case (sq, p) =>
      JsonPiece(Some(sq.toString), Some(p.color.label), Some(p.pieceType.label))
    }

  private def buildCastlingRights(rights: CastlingRights): JsonCastlingRights =
    JsonCastlingRights(
      Some(rights.whiteKingSide),
      Some(rights.whiteQueenSide),
      Some(rights.blackKingSide),
      Some(rights.blackQueenSide)
    )

  private def buildMoves(moves: List[Move]): List[JsonMove] =
    moves.map { m =>
      val moveType = convertMoveType(m.moveType)
      JsonMove(Some(m.from.toString), Some(m.to.toString), moveType)
    }

  private def convertMoveType(moveType: MoveType): Option[JsonMoveType] =
    val (tpe, isC, pp) = moveType match {
      case MoveType.Normal(isCapture) =>
        (Some("normal"), Some(isCapture), None)
      case MoveType.CastleKingside =>
        (Some("castleKingside"), None, None)
      case MoveType.CastleQueenside =>
        (Some("castleQueenside"), None, None)
      case MoveType.EnPassant =>
        (Some("enPassant"), Some(true), None)
      case MoveType.Promotion(piece) =>
        val pName = piece match {
          case PromotionPiece.Queen  => "queen"
          case PromotionPiece.Rook   => "rook"
          case PromotionPiece.Bishop => "bishop"
          case PromotionPiece.Knight => "knight"
        }
        (Some("promotion"), None, Some(pName))
    }
    Some(JsonMoveType(tpe, isC, pp))

  private def buildCapturedPieces(board: Board): JsonCapturedPieces =
    val (byWhite, byBlack) = getCapturedPieces(board)
    JsonCapturedPieces(Some(byWhite), Some(byBlack))

  private def formatJson(json: String): String =
    json
      .replace(" : ", ": ")
      .replaceAll("\\[\\s*\\]", "[]")
      .replaceAll("\\{\\s*\\}", "{}")

  private def getCapturedPieces(board: Board): (List[String], List[String]) =
    val initialBoard = Board.initial
    val captured = Square.all.flatMap { square =>
      initialBoard.pieceAt(square).flatMap { initialPiece =>
        board.pieceAt(square) match
          case None => Some(initialPiece)
          case Some(_) => None
      }
    }
    
    val whiteCaptured = captured.filter(_.color == Color.White).map(_.pieceType.label).toList
    val blackCaptured = captured.filter(_.color == Color.Black).map(_.pieceType.label).toList
    (blackCaptured, whiteCaptured)

