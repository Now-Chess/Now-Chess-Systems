package de.nowchess.api.io

import de.nowchess.api.game.GameContext

trait GameContextImport:
  def importGameContext(input: String): Either[String, GameContext]
