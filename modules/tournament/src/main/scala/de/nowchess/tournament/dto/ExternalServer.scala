package de.nowchess.tournament.dto

case class ExternalTournamentServer(id: String, label: String, url: String)
case class RegisterServerRequest(label: String, url: String)
case class ExternalTournamentServerList(servers: List[ExternalTournamentServer])
