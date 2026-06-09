package de.nowchess.tournament.service

import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class TournamentStreamManager:

  private val tournamentEmitters = new ConcurrentHashMap[String, CopyOnWriteArrayList[MultiEmitter[? >: String]]]()
  private val botEmitters        = new ConcurrentHashMap[String, CopyOnWriteArrayList[MultiEmitter[? >: String]]]()

  private def botKey(tournamentId: String, botId: String): String = s"${tournamentId}:${botId}"

  def register(tournamentId: String, botId: String, emitter: MultiEmitter[? >: String]): Unit =
    tournamentEmitters
      .computeIfAbsent(tournamentId, _ => new CopyOnWriteArrayList[MultiEmitter[? >: String]]())
      .add(emitter)
    botEmitters
      .computeIfAbsent(botKey(tournamentId, botId), _ => new CopyOnWriteArrayList[MultiEmitter[? >: String]]())
      .add(emitter)

  def unregister(tournamentId: String, botId: String, emitter: MultiEmitter[? >: String]): Unit =
    Option(tournamentEmitters.get(tournamentId)).foreach(_.remove(emitter))
    Option(botEmitters.get(botKey(tournamentId, botId))).foreach(_.remove(emitter))

  def publish(tournamentId: String, eventJson: String): Unit =
    Option(tournamentEmitters.get(tournamentId)).foreach { list =>
      list.asScala.foreach(e => scala.util.Try(e.emit(eventJson)))
    }

  def publishToBot(tournamentId: String, botId: String, eventJson: String): Unit =
    Option(botEmitters.get(botKey(tournamentId, botId))).foreach { list =>
      list.asScala.foreach(e => scala.util.Try(e.emit(eventJson)))
    }
