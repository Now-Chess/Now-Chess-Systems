package de.nowchess.api.game

import de.nowchess.api.board.{Board, CastlingRights, Color, PieceType, Square}
import de.nowchess.api.move.Move

/** Immutable bundle of complete game state. All state changes produce new GameContext instances.
  */
case class GameContext(
    board: Board,
    turn: Color,
    castlingRights: CastlingRights,
    enPassantSquare: Option[Square],
    halfMoveClock: Int,
    moves: List[Move],
    result: Option[GameResult] = None,
    initialBoard: Board = Board.initial,
):
  private lazy val whiteKingSquare: Option[Square] =
    board.pieces.find((_, p) => p.color == Color.White && p.pieceType == PieceType.King).map(_._1)
  private lazy val blackKingSquare: Option[Square] =
    board.pieces.find((_, p) => p.color == Color.Black && p.pieceType == PieceType.King).map(_._1)
  def kingSquare(color: Color): Option[Square] =
    if color == Color.White then whiteKingSquare else blackKingSquare

  /** Create new context with updated board. */
  def withBoard(newBoard: Board): GameContext = copy(board = newBoard)

  /** Create new context with updated turn. */
  def withTurn(newTurn: Color): GameContext = copy(turn = newTurn)

  /** Create new context with updated castling rights. */
  def withCastlingRights(newRights: CastlingRights): GameContext = copy(castlingRights = newRights)

  /** Create new context with updated en passant square. */
  def withEnPassantSquare(newSq: Option[Square]): GameContext = copy(enPassantSquare = newSq)

  /** Create new context with updated half-move clock. */
  def withHalfMoveClock(newClock: Int): GameContext = copy(halfMoveClock = newClock)

  /** Create new context with move appended to history. */
  def withMove(move: Move): GameContext = copy(moves = moves :+ move)

  /** Create new context with updated result. */
  def withResult(newResult: Option[GameResult]): GameContext = copy(result = newResult)

object GameContext:
  /** Initial position: white to move, all castling rights, no en passant. */
  def initial: GameContext = GameContext(
    board = Board.initial,
    turn = Color.White,
    castlingRights = CastlingRights.Initial,
    enPassantSquare = None,
    halfMoveClock = 0,
    moves = List.empty,
  )
