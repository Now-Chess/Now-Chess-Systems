package de.nowchess.chess.grpc

import de.nowchess.api.board.*
import de.nowchess.api.board.CastlingRights as DomainCastlingRights
import de.nowchess.api.game.{DrawReason, GameContext, GameResult, WinReason}
import de.nowchess.api.grpc.ProtoMapperBase
import de.nowchess.api.move.{Move as DomainMove, MoveType}
import de.nowchess.core.proto.*

import scala.jdk.CollectionConverters.*

object CoreProtoMapper
    extends ProtoMapperBase[
      ProtoColor,
      ProtoPieceType,
      ProtoMoveKind,
      ProtoMove,
      ProtoSquarePiece,
      java.util.List[ProtoSquarePiece],
      ProtoCastlingRights,
      ProtoGameResultKind,
      ProtoGameContext,
    ]:
  private val (colorTo, colorFrom) = ProtoMapperBase.colorConversions(ProtoColor.WHITE, ProtoColor.BLACK)
  private val (pieceTypeTo, pieceTypeFrom) = ProtoMapperBase.pieceTypeConversions(
    ProtoPieceType.PAWN,
    ProtoPieceType.KNIGHT,
    ProtoPieceType.BISHOP,
    ProtoPieceType.ROOK,
    ProtoPieceType.QUEEN,
    ProtoPieceType.KING,
  )
  private val (moveKindTo, moveKindFrom) = ProtoMapperBase.moveKindConversions(
    ProtoMoveKind.QUIET,
    ProtoMoveKind.CAPTURE,
    ProtoMoveKind.CASTLE_KINGSIDE,
    ProtoMoveKind.CASTLE_QUEENSIDE,
    ProtoMoveKind.EN_PASSANT,
    ProtoMoveKind.PROMO_QUEEN,
    ProtoMoveKind.PROMO_ROOK,
    ProtoMoveKind.PROMO_BISHOP,
    ProtoMoveKind.PROMO_KNIGHT,
  )

  override def toProtoColor(c: Color): ProtoColor                = colorTo(c)
  override def fromProtoColor(c: ProtoColor): Color              = colorFrom(c)
  override def toProtoPieceType(pt: PieceType): ProtoPieceType   = pieceTypeTo(pt)
  override def fromProtoPieceType(pt: ProtoPieceType): PieceType = pieceTypeFrom(pt)
  override def toProtoMoveKind(mt: MoveType): ProtoMoveKind      = moveKindTo(mt)
  override def fromProtoMoveKind(k: ProtoMoveKind): MoveType     = moveKindFrom(k)

  override def toProtoMove(m: DomainMove): ProtoMove =
    ProtoMove
      .newBuilder()
      .setFrom(m.from.toString)
      .setTo(m.to.toString)
      .setMoveKind(toProtoMoveKind(m.moveType))
      .build()

  override def fromProtoMove(m: ProtoMove): Option[DomainMove] =
    for
      from <- Square.fromAlgebraic(m.getFrom)
      to   <- Square.fromAlgebraic(m.getTo)
    yield DomainMove(from, to, fromProtoMoveKind(m.getMoveKind))

  override def toProtoSquarePiece(sq: Square, piece: Piece): ProtoSquarePiece =
    ProtoSquarePiece
      .newBuilder()
      .setSquare(sq.toString)
      .setPiece(
        ProtoPiece
          .newBuilder()
          .setColor(toProtoColor(piece.color))
          .setPieceType(toProtoPieceType(piece.pieceType))
          .build(),
      )
      .build()

  override def fromProtoSquarePiece(sp: ProtoSquarePiece): Option[(Square, Piece)] =
    Square
      .fromAlgebraic(sp.getSquare)
      .map(_ -> Piece(fromProtoColor(sp.getPiece.getColor), fromProtoPieceType(sp.getPiece.getPieceType)))

  override def toProtoBoard(board: Board): java.util.List[ProtoSquarePiece] =
    board.pieces
      .map((sq, piece) => toProtoSquarePiece(sq, piece))
      .toSeq
      .asJava

  override def fromProtoBoard(pieces: java.util.List[ProtoSquarePiece]): Board =
    Board(
      pieces.asScala
        .flatMap(fromProtoSquarePiece)
        .toMap,
    )

  override def toProtoResultKind(r: Option[GameResult]): ProtoGameResultKind = r match
    case None                                                     => ProtoGameResultKind.ONGOING
    case Some(GameResult.Win(Color.White, WinReason.Checkmate))   => ProtoGameResultKind.WIN_CHECKMATE_W
    case Some(GameResult.Win(Color.Black, WinReason.Checkmate))   => ProtoGameResultKind.WIN_CHECKMATE_B
    case Some(GameResult.Win(Color.White, WinReason.Resignation)) => ProtoGameResultKind.WIN_RESIGN_W
    case Some(GameResult.Win(Color.Black, WinReason.Resignation)) => ProtoGameResultKind.WIN_RESIGN_B
    case Some(GameResult.Win(Color.White, WinReason.TimeControl)) => ProtoGameResultKind.WIN_TIME_W
    case Some(GameResult.Win(Color.Black, WinReason.TimeControl)) => ProtoGameResultKind.WIN_TIME_B
    case Some(GameResult.Draw(DrawReason.Stalemate))              => ProtoGameResultKind.DRAW_STALEMATE
    case Some(GameResult.Draw(DrawReason.InsufficientMaterial))   => ProtoGameResultKind.DRAW_INSUFFICIENT
    case Some(GameResult.Draw(DrawReason.FiftyMoveRule))          => ProtoGameResultKind.DRAW_FIFTY_MOVE
    case Some(GameResult.Draw(DrawReason.ThreefoldRepetition))    => ProtoGameResultKind.DRAW_THREEFOLD
    case Some(GameResult.Draw(DrawReason.Agreement))              => ProtoGameResultKind.DRAW_AGREEMENT

  override def fromProtoResultKind(k: ProtoGameResultKind): Option[GameResult] = k match
    case ProtoGameResultKind.ONGOING           => None
    case ProtoGameResultKind.WIN_CHECKMATE_W   => Some(GameResult.Win(Color.White, WinReason.Checkmate))
    case ProtoGameResultKind.WIN_CHECKMATE_B   => Some(GameResult.Win(Color.Black, WinReason.Checkmate))
    case ProtoGameResultKind.WIN_RESIGN_W      => Some(GameResult.Win(Color.White, WinReason.Resignation))
    case ProtoGameResultKind.WIN_RESIGN_B      => Some(GameResult.Win(Color.Black, WinReason.Resignation))
    case ProtoGameResultKind.WIN_TIME_W        => Some(GameResult.Win(Color.White, WinReason.TimeControl))
    case ProtoGameResultKind.WIN_TIME_B        => Some(GameResult.Win(Color.Black, WinReason.TimeControl))
    case ProtoGameResultKind.DRAW_STALEMATE    => Some(GameResult.Draw(DrawReason.Stalemate))
    case ProtoGameResultKind.DRAW_INSUFFICIENT => Some(GameResult.Draw(DrawReason.InsufficientMaterial))
    case ProtoGameResultKind.DRAW_FIFTY_MOVE   => Some(GameResult.Draw(DrawReason.FiftyMoveRule))
    case ProtoGameResultKind.DRAW_THREEFOLD    => Some(GameResult.Draw(DrawReason.ThreefoldRepetition))
    case ProtoGameResultKind.DRAW_AGREEMENT    => Some(GameResult.Draw(DrawReason.Agreement))
    case _                                     => None

  override def toProtoCastlingRights(cr: DomainCastlingRights): ProtoCastlingRights =
    ProtoCastlingRights
      .newBuilder()
      .setWhiteKingSide(cr.whiteKingSide)
      .setWhiteQueenSide(cr.whiteQueenSide)
      .setBlackKingSide(cr.blackKingSide)
      .setBlackQueenSide(cr.blackQueenSide)
      .build()

  override def fromProtoCastlingRights(pcr: ProtoCastlingRights): DomainCastlingRights =
    DomainCastlingRights(pcr.getWhiteKingSide, pcr.getWhiteQueenSide, pcr.getBlackKingSide, pcr.getBlackQueenSide)

  override def toProtoGameContext(ctx: GameContext): ProtoGameContext =
    ProtoGameContext
      .newBuilder()
      .addAllBoard(toProtoBoard(ctx.board))
      .setTurn(toProtoColor(ctx.turn))
      .setCastlingRights(toProtoCastlingRights(ctx.castlingRights))
      .setEnPassantSquare(ctx.enPassantSquare.map(_.toString).getOrElse(""))
      .setHalfMoveClock(ctx.halfMoveClock)
      .addAllMoves(ctx.moves.map(toProtoMove).asJava)
      .setResult(toProtoResultKind(ctx.result))
      .addAllInitialBoard(toProtoBoard(ctx.initialBoard))
      .build()

  override def fromProtoGameContext(p: ProtoGameContext): GameContext =
    GameContext(
      board = fromProtoBoard(p.getBoardList),
      turn = fromProtoColor(p.getTurn),
      castlingRights = fromProtoCastlingRights(p.getCastlingRights),
      enPassantSquare = Option(p.getEnPassantSquare).filter(_.nonEmpty).flatMap(Square.fromAlgebraic),
      halfMoveClock = p.getHalfMoveClock,
      moves = p.getMovesList.asScala.flatMap(fromProtoMove).toList,
      result = fromProtoResultKind(p.getResult),
      initialBoard = fromProtoBoard(p.getInitialBoardList),
    )
