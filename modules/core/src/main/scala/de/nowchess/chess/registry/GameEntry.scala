package de.nowchess.chess.registry

import de.nowchess.api.board.Color
import de.nowchess.api.player.PlayerInfo
import de.nowchess.chess.engine.GameEngine

final case class GameEntry(
    gameId: String,
    engine: GameEngine,
    white: PlayerInfo,
    black: PlayerInfo,
    resigned: Boolean = false,
)
