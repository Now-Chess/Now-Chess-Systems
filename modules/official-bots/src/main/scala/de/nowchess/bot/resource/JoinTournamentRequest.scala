package de.nowchess.bot.resource

case class JoinTournamentRequest(
    tournamentId: String,
    botToken: String,
    difficulty: String,
    serverUrl: Option[String],
)
