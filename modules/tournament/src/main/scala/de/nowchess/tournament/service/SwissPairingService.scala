package de.nowchess.tournament.service

import de.nowchess.tournament.domain.{TournamentPairing, TournamentParticipant}
import java.util.concurrent.ThreadLocalRandom

object SwissPairingService:

  def computePairings(
      participants: List[TournamentParticipant],
      pastPairings: List[TournamentPairing],
  ): (List[(TournamentParticipant, TournamentParticipant)], Option[TournamentParticipant]) =
    val sorted              = sortParticipants(participants)
    val (remaining, byeOpt) = extractByePlayer(sorted)
    val pairs               = buildPairs(remaining, pastPairings)
    (pairs, byeOpt)

  private def sortParticipants(participants: List[TournamentParticipant]): List[TournamentParticipant] =
    participants.sortWith { (a, b) =>
      if a.points != b.points then a.points > b.points
      else if a.tieBreak != b.tieBreak then a.tieBreak > b.tieBreak
      else a.botName < b.botName
    }

  private def extractByePlayer(
      sorted: List[TournamentParticipant],
  ): (List[TournamentParticipant], Option[TournamentParticipant]) =
    if sorted.size % 2 == 0 then (sorted, None)
    else
      val minByes  = sorted.map(_.byeCount).min
      val byeIndex = sorted.lastIndexWhere(_.byeCount == minByes)
      val bye      = sorted(byeIndex)
      (sorted.filterNot(_ eq bye), Some(bye))

  private def buildPairs(
      players: List[TournamentParticipant],
      pastPairings: List[TournamentPairing],
  ): List[(TournamentParticipant, TournamentParticipant)] =
    val arr = players.toArray
    resolveConflicts(arr, pastPairings)
    arr
      .grouped(2)
      .flatMap {
        case Array(a, b) => Some(assignColors(a, b))
        case _           => None
      }
      .toList

  private def resolveConflicts(arr: Array[TournamentParticipant], pastPairings: List[TournamentPairing]): Unit =
    // scalafix:off DisableSyntax.var
    var i = 0
    // scalafix:on DisableSyntax.var
    while i < arr.length - 1 do
      if havePlayedBefore(arr(i), arr(i + 1), pastPairings) && i + 2 < arr.length then
        val tmp = arr(i + 1)
        arr(i + 1) = arr(i + 2)
        arr(i + 2) = tmp
      i += 2

  private def havePlayedBefore(
      a: TournamentParticipant,
      b: TournamentParticipant,
      pastPairings: List[TournamentPairing],
  ): Boolean =
    pastPairings.exists(p =>
      (p.whiteId == a.botId && p.blackId == b.botId) ||
        (p.whiteId == b.botId && p.blackId == a.botId),
    )

  private def assignColors(
      a: TournamentParticipant,
      b: TournamentParticipant,
  ): (TournamentParticipant, TournamentParticipant) =
    if ThreadLocalRandom.current().nextBoolean() then (a, b) else (b, a)
