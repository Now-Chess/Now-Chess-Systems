package de.nowchess.api.game

/** Reason why a game ended in a draw. */
enum DrawReason:
  case Stalemate
  case InsufficientMaterial
  case FiftyMoveRule
  case ThreefoldRepetition
  case Agreement
