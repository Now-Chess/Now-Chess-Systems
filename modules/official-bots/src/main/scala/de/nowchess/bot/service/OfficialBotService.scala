package de.nowchess.bot.service

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.bot.BotController
import de.nowchess.bot.BotDifficulty
import de.nowchess.bot.config.RedisConfig
import de.nowchess.io.fen.FenParser
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import java.util.function.Consumer

@ApplicationScoped
class OfficialBotService:

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource       = uninitialized
  @Inject var redisConfig: RedisConfig     = uninitialized
  @Inject var objectMapper: ObjectMapper   = uninitialized
  @Inject var botController: BotController = uninitialized
  // scalafix:on DisableSyntax.var

  private val terminalStatuses =
    Set("checkmate", "resign", "timeout", "stalemate", "insufficientMaterial", "draw")

  def onStart(@Observes event: StartupEvent): Unit =
    BotController.listBots.foreach(subscribeToEventChannel)

  private def subscribeToEventChannel(botName: String): Unit =
    val handler: Consumer[String] = msg => handleBotEvent(botName, msg)
    redis.pubsub(classOf[String]).subscribe(s"${redisConfig.prefix}:bot:$botName:events", handler)
    ()

  private def handleBotEvent(botName: String, msg: String): Unit =
    try
      val node = objectMapper.readTree(msg)
      if node.path("type").asText() == "gameStart" then
        val gameId       = node.path("gameId").asText()
        val playingAs    = node.path("playingAs").asText()
        val difficulty   = node.path("difficulty").asInt(1400)
        val botAccountId = node.path("botAccountId").asText()
        watchGame(botName, gameId, playingAs, difficulty, botAccountId)
    catch case _: Exception => ()

  private def watchGame(
      botName: String,
      gameId: String,
      playingAs: String,
      difficulty: Int,
      botAccountId: String,
  ): Unit =
    val handler: Consumer[String] = msg => handleGameEvent(botName, gameId, playingAs, difficulty, botAccountId, msg)
    redis.pubsub(classOf[String]).subscribe(s"${redisConfig.prefix}:game:$gameId:s2c", handler)
    ()

  private def handleGameEvent(
      botName: String,
      gameId: String,
      playingAs: String,
      difficulty: Int,
      botAccountId: String,
      msg: String,
  ): Unit =
    try
      val node   = objectMapper.readTree(msg)
      val status = node.path("state").path("status").asText("")
      if !terminalStatuses.contains(status) then
        val turn = node.path("state").path("turn").asText("")
        if turn == playingAs then
          val fen = node.path("state").path("fen").asText()
          computeAndSendMove(botName, gameId, fen, difficulty, botAccountId)
    catch case _: Exception => ()

  private def computeAndSendMove(
      botName: String,
      gameId: String,
      fen: String,
      difficulty: Int,
      botAccountId: String,
  ): Unit =
    val level = DifficultyMapper.fromElo(difficulty).getOrElse(BotDifficulty.Medium)
    botController.getBot(botName).orElse(botController.getBot(level.toString.toLowerCase)).foreach { bot =>
      FenParser.parseFen(fen).toOption.foreach { context =>
        bot(context).foreach { move =>
          val uci      = toUci(move)
          val c2sTopic = s"${redisConfig.prefix}:game:$gameId:c2s"
          val moveMsg  = s"""{"type":"MOVE","uci":"$uci","playerId":"$botAccountId"}"""
          redis.pubsub(classOf[String]).publish(c2sTopic, moveMsg)
          ()
        }
      }
    }

  private def toUci(move: Move): String =
    val base = s"${move.from}${move.to}"
    move.moveType match
      case MoveType.Promotion(piece) => base + promotionChar(piece)
      case _                         => base

  private def promotionChar(piece: PromotionPiece): String =
    piece match
      case PromotionPiece.Knight => "n"
      case PromotionPiece.Bishop => "b"
      case PromotionPiece.Rook   => "r"
      case PromotionPiece.Queen  => "q"
