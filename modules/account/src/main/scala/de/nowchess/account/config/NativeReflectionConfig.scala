package de.nowchess.account.config

import de.nowchess.account.client.{CoreCreateGameRequest, CoreGameResponse, CorePlayerInfo, CoreTimeControl}
import de.nowchess.account.domain.{
  BotAccount,
  Challenge,
  ChallengeColor,
  ChallengeStatus,
  DeclineReason,
  OfficialBotAccount,
  TimeControl,
  UserAccount,
}
import de.nowchess.account.dto.*
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[UserAccount],
    classOf[BotAccount],
    classOf[OfficialBotAccount],
    classOf[Challenge],
    classOf[ChallengeColor],
    classOf[ChallengeStatus],
    classOf[DeclineReason],
    classOf[TimeControl],
    classOf[LoginRequest],
    classOf[TokenResponse],
    classOf[PlayerInfo],
    classOf[PublicAccountDto],
    classOf[BotAccountDto],
    classOf[BotAccountWithTokenDto],
    classOf[OfficialBotAccountDto],
    classOf[CreateBotAccountRequest],
    classOf[UpdateBotNameRequest],
    classOf[RotatedTokenDto],
    classOf[TimeControlDto],
    classOf[ChallengeRequest],
    classOf[ChallengeDto],
    classOf[DeclineRequest],
    classOf[ChallengeListDto],
    classOf[ErrorDto],
    classOf[CorePlayerInfo],
    classOf[CoreTimeControl],
    classOf[CoreCreateGameRequest],
    classOf[CoreGameResponse],
    classOf[OfficialChallengeResponse],
  ),
)
class NativeReflectionConfig
