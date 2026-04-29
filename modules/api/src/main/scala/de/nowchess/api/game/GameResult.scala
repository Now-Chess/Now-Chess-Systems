package de.nowchess.api.game

import de.nowchess.api.board.Color

/** Outcome of a finished game. */
enum GameResult:
  case Win(color: Color, winReason: WinReason)
  case Draw(reason: DrawReason)

enum WinReason:
  case Checkmate
  case Resignation
  case TimeControl
