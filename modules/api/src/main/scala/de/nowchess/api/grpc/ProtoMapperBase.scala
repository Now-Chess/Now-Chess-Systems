package de.nowchess.api.grpc

import de.nowchess.api.board.{CastlingRights as DomainCastlingRights, *}
import de.nowchess.api.game.{DrawReason, GameContext, GameResult, WinReason}
import de.nowchess.api.move.{Move as DomainMove, MoveType, PromotionPiece}

import scala.jdk.CollectionConverters.*

trait ProtoMapperBase[PC, PPT, PMK, PM, PSP, PBoard, PCR, PRK, PGC]:
  def toProtoColor(c: Color): PC
  def fromProtoColor(c: PC): Color
  def toProtoPieceType(pt: PieceType): PPT
  def fromProtoPieceType(pt: PPT): PieceType
  def toProtoMoveKind(mt: MoveType): PMK
  def fromProtoMoveKind(k: PMK): MoveType

  def toProtoMove(m: DomainMove): PM
  def fromProtoMove(m: PM): Option[DomainMove]

  def toProtoSquarePiece(sq: Square, piece: Piece): PSP
  def fromProtoSquarePiece(sp: PSP): Option[(Square, Piece)]

  def toProtoBoard(board: Board): java.util.List[PSP]
  def fromProtoBoard(pieces: java.util.List[PSP]): Board

  def toProtoResultKind(r: Option[GameResult]): PRK
  def fromProtoResultKind(k: PRK): Option[GameResult]

  def toProtoCastlingRights(cr: DomainCastlingRights): PCR
  def fromProtoCastlingRights(pcr: PCR): DomainCastlingRights

  def toProtoGameContext(ctx: GameContext): PGC
  def fromProtoGameContext(p: PGC): GameContext

object ProtoMapperBase:
  def colorConversions[PC](white: PC, black: PC): (Color => PC, PC => Color) =
    (
      (c: Color) =>
        c match
          case Color.White => white
          case Color.Black => black,
      (pc: PC) =>
        if pc == white then Color.White
        else Color.Black,
    )

  def pieceTypeConversions[PPT](
      pawn: PPT,
      knight: PPT,
      bishop: PPT,
      rook: PPT,
      queen: PPT,
      king: PPT,
  ): (PieceType => PPT, PPT => PieceType) =
    (
      (pt: PieceType) =>
        pt match
          case PieceType.Pawn   => pawn
          case PieceType.Knight => knight
          case PieceType.Bishop => bishop
          case PieceType.Rook   => rook
          case PieceType.Queen  => queen
          case PieceType.King   => king,
      (ppt: PPT) =>
        if ppt == pawn then PieceType.Pawn
        else if ppt == knight then PieceType.Knight
        else if ppt == bishop then PieceType.Bishop
        else if ppt == rook then PieceType.Rook
        else if ppt == queen then PieceType.Queen
        else PieceType.King,
    )

  def moveKindConversions[PMK](
      quiet: PMK,
      capture: PMK,
      castleKingside: PMK,
      castleQueenside: PMK,
      enPassant: PMK,
      promoQueen: PMK,
      promoRook: PMK,
      promoBishop: PMK,
      promoKnight: PMK,
  ): (MoveType => PMK, PMK => MoveType) =
    (
      (mt: MoveType) =>
        mt match
          case MoveType.Normal(false)                    => quiet
          case MoveType.Normal(true)                     => capture
          case MoveType.CastleKingside                   => castleKingside
          case MoveType.CastleQueenside                  => castleQueenside
          case MoveType.EnPassant                        => enPassant
          case MoveType.Promotion(PromotionPiece.Queen)  => promoQueen
          case MoveType.Promotion(PromotionPiece.Rook)   => promoRook
          case MoveType.Promotion(PromotionPiece.Bishop) => promoBishop
          case MoveType.Promotion(PromotionPiece.Knight) => promoKnight,
      (pmk: PMK) =>
        if pmk == quiet then MoveType.Normal(false)
        else if pmk == capture then MoveType.Normal(true)
        else if pmk == castleKingside then MoveType.CastleKingside
        else if pmk == castleQueenside then MoveType.CastleQueenside
        else if pmk == enPassant then MoveType.EnPassant
        else if pmk == promoQueen then MoveType.Promotion(PromotionPiece.Queen)
        else if pmk == promoRook then MoveType.Promotion(PromotionPiece.Rook)
        else if pmk == promoBishop then MoveType.Promotion(PromotionPiece.Bishop)
        else if pmk == promoKnight then MoveType.Promotion(PromotionPiece.Knight)
        else MoveType.Normal(false),
    )
