package de.nowchess.io

import de.nowchess.api.game.GameContext

trait GameContextExport:

  def exportGameContext(context: GameContext): String
