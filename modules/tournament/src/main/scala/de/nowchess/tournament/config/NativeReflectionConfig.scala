package de.nowchess.tournament.config

import de.nowchess.api.dto.GameWritebackEventDto
import de.nowchess.tournament.client.{CoreCreateGameRequest, CoreGameResponse, CorePlayerInfo, CoreTimeControl}
import de.nowchess.tournament.domain.{Tournament, TournamentPairing, TournamentParticipant}
import de.nowchess.tournament.dto.*
import de.nowchess.tournament.error.TournamentError
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[Tournament],
    classOf[TournamentPairing],
    classOf[TournamentParticipant],
    classOf[TournamentError],
    classOf[BotRef],
    classOf[Clock],
    classOf[Variant],
    classOf[CreateTournamentForm],
    classOf[ResultDto],
    classOf[Standing],
    classOf[TournamentDto],
    classOf[TournamentListDto],
    classOf[PairingDto],
    classOf[GameExportDto],
    classOf[RoundPairingsDto],
    classOf[ErrorDto],
    classOf[OkDto],
    classOf[ReplicateTournamentRequest],
    classOf[CorePlayerInfo],
    classOf[CoreTimeControl],
    classOf[CoreCreateGameRequest],
    classOf[CoreGameResponse],
    classOf[GameWritebackEventDto],
    classOf[ExternalTournamentServer],
    classOf[RegisterServerRequest],
    classOf[ExternalTournamentServerList],
  ),
)
class NativeReflectionConfig
