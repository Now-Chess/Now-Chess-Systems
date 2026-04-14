package de.nowchess.api.game

import de.nowchess.api.board.Color

/** Outcome of a finished game. */
enum GameResult:
  case Win(color: Color)
  case Draw(reason: DrawReason)
