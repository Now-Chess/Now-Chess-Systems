package de.nowchess.tournament.dto

import java.time.Instant

case class BotRef(id: String, name: String)

case class Clock(limit: Int, increment: Int)

case class Variant(key: String, name: String)

case class CreateTournamentForm(
    name: String,
    nbRounds: Int,
    clockLimit: Int,
    clockIncrement: Int,
    rated: Boolean = true,
)

case class ResultDto(
    rank: Int,
    points: Double,
    tieBreak: Double,
    bot: BotRef,
    nbGames: Int,
    wins: Int,
    draws: Int,
    losses: Int,
)

case class Standing(page: Int, players: List[ResultDto])

case class TournamentDto(
    id: String,
    fullName: String,
    clock: Clock,
    variant: Variant,
    rated: Boolean,
    nbPlayers: Int,
    nbRounds: Int,
    createdBy: String,
    startsAt: Option[String],
    status: String,
    round: Int,
    standing: Standing,
    winner: Option[BotRef],
)

case class TournamentListDto(
    created: List[TournamentDto],
    started: List[TournamentDto],
    finished: List[TournamentDto],
)

case class PairingDto(
    id: String,
    round: Int,
    white: Option[BotRef],
    black: BotRef,
    gameId: Option[String],
    winner: Option[String],
)

case class GameExportDto(
    id: String,
    round: Int,
    white: BotRef,
    black: BotRef,
    winner: Option[String],
    moves: String,
)

case class RoundPairingsDto(round: Int, pairings: List[PairingDto])

case class ErrorDto(error: String)

case class OkDto(ok: Boolean = true)

case class ReplicateTournamentRequest(
    id: String,
    fullName: String,
    nbRounds: Int,
    clockLimit: Int,
    clockIncrement: Int,
    rated: Boolean,
    createdBy: String,
    startsAt: Instant,
    status: String,
)
