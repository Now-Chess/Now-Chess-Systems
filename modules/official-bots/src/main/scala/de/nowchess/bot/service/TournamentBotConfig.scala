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
    fromEnvWithToken(env, None)

  def fromEnvWithToken(env: Map[String, String], resolvedToken: Option[String]): Option[TournamentBotConfig] =
    val token = env.get("TOURNAMENT_BOT_TOKEN").filter(_.nonEmpty).orElse(resolvedToken)
    for
      tournamentId <- env.get("TOURNAMENT_ID").filter(_.nonEmpty)
      tok          <- token
      botId        <- jwtSubject(tok)
      serverUrl  = env.getOrElse("TOURNAMENT_SERVICE_URL", "http://localhost:8086")
      difficulty = env.getOrElse("TOURNAMENT_BOT_DIFFICULTY", "medium")
    yield TournamentBotConfig(serverUrl, tournamentId, tok, botId, difficulty)

  def jwtSubject(token: String): Option[String] =
    Try {
      val parts = token.split("\\.")
      if parts.length >= 2 then
        val payload = new String(java.util.Base64.getUrlDecoder.decode(parts(1)))
        val sub     = mapper.readTree(payload).path("sub").asText()
        Option(sub).filter(_.nonEmpty)
      else None
    }.toOption.flatten
