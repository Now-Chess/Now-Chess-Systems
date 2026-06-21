package de.nowchess.tournament.service

import de.nowchess.tournament.dto.ExternalTournamentServer
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.{Optional, UUID}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class TournamentServerRegistry:

  @ConfigProperty(name = "nowchess.tournament.external-servers")
  var externalServers: Optional[String] = scala.compiletime.uninitialized

  private val servers     = new ConcurrentHashMap[String, ExternalTournamentServer]()
  private val tournaments = new ConcurrentHashMap[String, String]()

  @PostConstruct
  def init(): Unit =
    if externalServers.isPresent then
      externalServers.get().split(",").map(_.trim).filter(_.nonEmpty).foreach { url =>
        register(url, url)
      }

  def register(label: String, url: String): ExternalTournamentServer =
    val id     = UUID.randomUUID().toString
    val server = ExternalTournamentServer(id, label, url.stripSuffix("/"))
    servers.put(id, server)
    server

  def list(): List[ExternalTournamentServer] =
    servers.values().asScala.toList

  def remove(id: String): Boolean =
    Option(servers.remove(id)).isDefined

  def serverUrls(): List[String] =
    servers.values().asScala.map(_.url).toList

  def bindTournament(tournamentId: String, serverUrl: String): Unit =
    tournaments.put(tournamentId, serverUrl)

  def findServerUrl(tournamentId: String): Option[String] =
    Option(tournaments.get(tournamentId))
