package de.nowchess.store.config

import de.nowchess.api.dto.GameWritebackEventDto
import de.nowchess.store.domain.GameRecord
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[GameRecord],
    classOf[GameWritebackEventDto],
  ),
)
class NativeReflectionConfig
