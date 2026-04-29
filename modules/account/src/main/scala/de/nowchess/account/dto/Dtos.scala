package de.nowchess.account.dto

case class RegisterRequest(username: String, email: String, password: String)

case class LoginRequest(username: String, password: String)

case class TokenResponse(token: String)

case class PlayerInfo(id: String, name: String, rating: Int)

case class PublicAccountDto(id: String, username: String, rating: Int, createdAt: String)

case class TimeControlDto(`type`: String, limit: Option[Int], increment: Option[Int])

case class ChallengeRequest(color: String, timeControl: TimeControlDto)

case class ChallengeDto(
    id: String,
    challenger: PlayerInfo,
    destUser: PlayerInfo,
    variant: String,
    color: String,
    timeControl: TimeControlDto,
    status: String,
    declineReason: Option[String],
    gameId: Option[String],
    createdAt: String,
    expiresAt: String,
)

case class DeclineRequest(reason: Option[String])

case class ChallengeListDto(in: List[ChallengeDto], out: List[ChallengeDto])

case class ErrorDto(error: String)

case class CreateBotAccountRequest(name: String)

case class UpdateBotNameRequest(name: String)

case class BotAccountDto(id: String, name: String, rating: Int, createdAt: String)

case class BotAccountWithTokenDto(id: String, name: String, rating: Int, token: String, createdAt: String)

case class RotatedTokenDto(token: String)

case class OfficialBotAccountDto(id: String, name: String, rating: Int, createdAt: String)

case class OfficialChallengeResponse(gameId: String, botName: String, difficulty: Int)
