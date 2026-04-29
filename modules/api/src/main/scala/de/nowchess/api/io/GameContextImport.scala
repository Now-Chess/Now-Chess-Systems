package de.nowchess.api.io

import de.nowchess.api.error.GameError
import de.nowchess.api.game.GameContext

trait GameContextImport:
  def importGameContext(input: String): Either[GameError, GameContext]
