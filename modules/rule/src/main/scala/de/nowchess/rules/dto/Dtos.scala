package de.nowchess.rules.dto

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move

case class ContextSquareRequest(context: GameContext, square: String)

case class ContextMoveRequest(context: GameContext, move: Move)
