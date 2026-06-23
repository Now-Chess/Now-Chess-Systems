package de.nowchess.bot.config

import de.nowchess.bot.resource.{JoinTournamentRequest, JoinTournamentResponse}
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[JoinTournamentRequest],
    classOf[JoinTournamentResponse],
  ),
)
class NativeReflectionConfig
