package de.nowchess.bot.resource

case class JoinTournamentRequest(
    tournamentId: String,
    difficulty: String,
    serverUrl: Option[String],
)
