package de.nowchess.bot.service

import com.fasterxml.jackson.databind.ObjectMapper
import scala.util.Try

final case class TournamentBotConfig(
    serverUrl: String,
    tournamentId: String,
    token: String,
    botId: String,
    difficulty: String,
)

object TournamentBotConfig:

  private val mapper = new ObjectMapper()

  def fromEnv(env: Map[String, String]): Option[TournamentBotConfig] =
    for
      tournamentId <- env.get("TOURNAMENT_ID").filter(_.nonEmpty)
      token        <- env.get("TOURNAMENT_BOT_TOKEN").filter(_.nonEmpty)
      botId        <- jwtSubject(token)
      serverUrl  = env.getOrElse("TOURNAMENT_SERVER_URL", "http://localhost:8089")
      difficulty = env.getOrElse("TOURNAMENT_BOT_DIFFICULTY", "medium")
    yield TournamentBotConfig(serverUrl, tournamentId, token, botId, difficulty)

  def jwtSubject(token: String): Option[String] =
    Try {
      val parts = token.split("\\.")
      if parts.length >= 2 then
        val payload = new String(java.util.Base64.getUrlDecoder.decode(parts(1)))
        val sub     = mapper.readTree(payload).path("sub").asText()
        Option(sub).filter(_.nonEmpty)
      else None
    }.toOption.flatten
