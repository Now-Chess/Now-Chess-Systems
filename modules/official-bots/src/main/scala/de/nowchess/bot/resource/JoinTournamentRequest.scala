package de.nowchess.bot.resource

case class JoinTournamentRequest(
    tournamentId: String,
    botToken: Option[String],
    difficulty: String,
    serverUrl: Option[String],
)
