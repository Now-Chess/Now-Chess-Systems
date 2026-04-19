package de.nowchess.api.bot

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move

trait Bot {

  def name: String
  def nextMove(context: GameContext): Option[Move]

}
