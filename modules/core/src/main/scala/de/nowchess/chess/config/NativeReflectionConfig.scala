package de.nowchess.chess.config

import de.nowchess.api.dto.*
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[ApiErrorDto],
    classOf[CreateGameRequestDto],
    classOf[ErrorEventDto],
    classOf[GameFullDto],
    classOf[GameFullEventDto],
    classOf[GameStateDto],
    classOf[GameStateEventDto],
    classOf[ImportFenRequestDto],
    classOf[ImportPgnRequestDto],
    classOf[LegalMoveDto],
    classOf[LegalMovesResponseDto],
    classOf[OkResponseDto],
    classOf[PlayerInfoDto],
  ),
)
class NativeReflectionConfig
