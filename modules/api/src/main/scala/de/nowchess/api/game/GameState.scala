package de.nowchess.api.game

import de.nowchess.api.board.{Color, Square}

/**
 * Castling availability flags for one side.
 *
 * @param kingSide  king-side castling still legally available
 * @param queenSide queen-side castling still legally available
 */
final case class CastlingRights(kingSide: Boolean, queenSide: Boolean)

object CastlingRights:
  val None: CastlingRights = CastlingRights(kingSide = false, queenSide = false)
  val Both: CastlingRights = CastlingRights(kingSide = true, queenSide = true)

/** Outcome of a finished game. */
enum GameResult:
  case WhiteWins
  case BlackWins
  case Draw

/** Lifecycle state of a game. */
enum GameStatus:
  case NotStarted
  case InProgress
  case Finished(result: GameResult)

/**
 * A FEN-compatible snapshot of board and game state.
 *
 * The board is represented as a FEN piece-placement string (rank 8 to rank 1,
 * separated by '/').  All other fields mirror standard FEN fields.
 *
 * @param piecePlacement  FEN piece-placement field, e.g.
 *                        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
 * @param activeColor     side to move
 * @param castlingWhite   castling rights for White
 * @param castlingBlack   castling rights for Black
 * @param enPassantTarget square behind the double-pushed pawn, if any
 * @param halfMoveClock   plies since last capture or pawn advance (50-move rule)
 * @param fullMoveNumber  increments after Black's move, starts at 1
 * @param status          current lifecycle status of the game
 */
final case class GameState(
  piecePlacement: String,
  activeColor: Color,
  castlingWhite: CastlingRights,
  castlingBlack: CastlingRights,
  enPassantTarget: Option[Square],
  halfMoveClock: Int,
  fullMoveNumber: Int,
  status: GameStatus
)

object GameState:
  /** Standard starting position. */
  val initial: GameState = GameState(
    piecePlacement  = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
    activeColor     = Color.White,
    castlingWhite   = CastlingRights.Both,
    castlingBlack   = CastlingRights.Both,
    enPassantTarget = None,
    halfMoveClock   = 0,
    fullMoveNumber  = 1,
    status          = GameStatus.InProgress
  )
