package de.nowchess.chess.registry

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.board.Color
import de.nowchess.api.game.{ClockState, CorrespondenceClockState, GameContext, GameMode, LiveClockState, TimeControl}
import de.nowchess.api.move.Move
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import de.nowchess.chess.client.{GameRecordDto, StoreServiceClient}
import de.nowchess.chess.controller.Parser
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.grpc.RuleSetGrpcAdapter
import de.nowchess.chess.config.RedisConfig
import de.nowchess.chess.grpc.IoGrpcClientWrapper
import de.nowchess.chess.resource.GameDtoMapper
import io.quarkus.redis.datasource.RedisDataSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.rest.client.inject.RestClient
import scala.compiletime.uninitialized
import org.jboss.logging.Logger
import scala.util.Try
import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class RedisGameRegistry extends GameRegistry:
  @Inject
  // scalafix:off DisableSyntax.var
  var redis: RedisDataSource                              = uninitialized
  @Inject var redisConfig: RedisConfig                    = uninitialized
  @Inject var objectMapper: ObjectMapper                  = uninitialized
  @Inject var ioClient: IoGrpcClientWrapper               = uninitialized
  @Inject var ruleSetAdapter: RuleSetGrpcAdapter          = uninitialized
  @Inject @RestClient var storeClient: StoreServiceClient = uninitialized
  // scalafix:on

  private val log          = Logger.getLogger(classOf[RedisGameRegistry])
  private val localEngines = ConcurrentHashMap[String, GameEntry]()
  private val rng          = new SecureRandom()

  private def cacheKey(gameId: String) = s"${redisConfig.prefix}:game:entry:$gameId"

  def generateId(): String =
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    Iterator.continually(rng.nextInt(chars.length)).map(chars).take(8).mkString

  def store(entry: GameEntry): Unit =
    localEngines.put(entry.gameId, entry)
    val combined = ioClient.exportCombined(entry.engine.context)
    redis.value(classOf[String]).setex(cacheKey(entry.gameId), 1800L, toJson(entry, combined.fen, combined.pgn))
    log.infof(
      "Stored game %s in registry (white=%s black=%s)",
      entry.gameId,
      entry.white.displayName,
      entry.black.displayName,
    )

  def get(gameId: String): Option[GameEntry] =
    Option(localEngines.get(gameId)) match
      case Some(localEntry) =>
        readRedisDto(gameId).flatMap(dto => Try(reconstruct(dto)).toOption) match
          case Some(redisEntry) if !sameSnapshot(localEntry, redisEntry) =>
            localEngines.put(gameId, redisEntry)
            Some(redisEntry)
          case _ => Some(localEntry)
      case None => fromRedis(gameId).orElse(fromDb(gameId))

  def update(entry: GameEntry): Unit =
    localEngines.put(entry.gameId, entry)
    val combined = ioClient.exportCombined(entry.engine.context)
    redis.value(classOf[String]).setex(cacheKey(entry.gameId), 1800L, toJson(entry, combined.fen, combined.pgn))

  private def readRedisDto(gameId: String): Option[GameCacheDto] =
    Try(Option(redis.value(classOf[String]).get(cacheKey(gameId)))).toOption.flatten.flatMap { json =>
      Try(objectMapper.readValue(json, classOf[GameCacheDto])).toOption
    }

  private def fromRedis(gameId: String): Option[GameEntry] =
    readRedisDto(gameId)
      .flatMap { dto =>
        Try(reconstruct(dto)).toOption.orElse {
          log.warnf("Failed to reconstruct game %s from Redis", gameId)
          None
        }
      }
      .map { entry =>
        localEngines.put(gameId, entry)
        log.infof("Loaded game %s from Redis cache", gameId)
        entry
      }

  private def fromDb(gameId: String): Option[GameEntry] =
    Try {
      val record = storeClient.getGame(gameId)
      val dto = GameCacheDto(
        gameId = record.gameId,
        fen = record.fen,
        pgn = record.pgn,
        whiteId = record.whiteId,
        whiteName = record.whiteName,
        blackId = record.blackId,
        blackName = record.blackName,
        mode = record.mode,
        resigned = record.resigned,
        limitSeconds = Option(record.limitSeconds).map(_.intValue),
        incrementSeconds = Option(record.incrementSeconds).map(_.intValue),
        daysPerMove = Option(record.daysPerMove).map(_.intValue),
        whiteRemainingMs = Option(record.whiteRemainingMs).map(_.longValue),
        blackRemainingMs = Option(record.blackRemainingMs).map(_.longValue),
        incrementMs = Option(record.incrementMs).map(_.longValue),
        clockLastTickAt = Option(record.clockLastTickAt).map(_.longValue),
        clockMoveDeadline = Option(record.clockMoveDeadline).map(_.longValue),
        clockActiveColor = Option(record.clockActiveColor),
        pendingDrawOffer = Option(record.pendingDrawOffer),
      )
      (dto, reconstruct(dto))
    } match
      case scala.util.Success((dto, entry)) =>
        log.infof("Loaded game %s from store service", gameId)
        localEngines.put(gameId, entry)
        redis.value(classOf[String]).setex(cacheKey(gameId), 1800L, objectMapper.writeValueAsString(dto))
        Some(entry)
      case scala.util.Failure(ex) =>
        log.warnf(ex, "Failed to load game %s from store service", gameId)
        None

  private def reconstruct(dto: GameCacheDto): GameEntry =
    val ctx = if dto.pgn.nonEmpty then ioClient.importPgn(dto.pgn) else GameContext.initial
    val tc = (dto.limitSeconds, dto.daysPerMove) match
      case (Some(l), _)    => TimeControl.Clock(l, dto.incrementSeconds.getOrElse(0))
      case (None, Some(d)) => TimeControl.Correspondence(d)
      case _               => TimeControl.Unlimited
    val toColor: String => Color = s => if s == "white" then Color.White else Color.Black
    val restoredClock: Option[ClockState] =
      dto.clockLastTickAt
        .map { tick =>
          LiveClockState(
            whiteRemainingMs = dto.whiteRemainingMs.get,
            blackRemainingMs = dto.blackRemainingMs.get,
            incrementMs = dto.incrementMs.get,
            lastTickAt = Instant.ofEpochMilli(tick),
            activeColor = toColor(dto.clockActiveColor.get),
          )
        }
        .orElse {
          dto.clockMoveDeadline.map { deadline =>
            CorrespondenceClockState(
              moveDeadline = Instant.ofEpochMilli(deadline),
              daysPerMove = dto.daysPerMove.get,
              activeColor = toColor(dto.clockActiveColor.get),
            )
          }
        }
    val restoredDrawOffer       = dto.pendingDrawOffer.map(toColor)
    val restoredTakebackRequest = dto.pendingTakebackRequest.map(toColor)
    val redoMoves = dto.redoStack.flatMap { uci =>
      Parser.parseMove(uci).flatMap { case (from, to, pp) =>
        ruleSetAdapter
          .legalMoves(ctx)(from)
          .find(m => m.to == to && (pp.isEmpty || m.moveType == de.nowchess.api.move.MoveType.Promotion(pp.get)))
      }
    }
    val engine = GameEngine(
      initialContext = ctx,
      ruleSet = ruleSetAdapter,
      timeControl = tc,
      initialClockState = restoredClock,
      initialDrawOffer = restoredDrawOffer,
      initialRedoStack = redoMoves,
      initialTakebackRequest = restoredTakebackRequest,
    )
    GameEntry(
      gameId = dto.gameId,
      engine = engine,
      white = PlayerInfo(PlayerId(dto.whiteId), dto.whiteName),
      black = PlayerInfo(PlayerId(dto.blackId), dto.blackName),
      resigned = dto.resigned,
      mode = if dto.mode == "Authenticated" then GameMode.Authenticated else GameMode.Open,
    )

  private def toJson(entry: GameEntry, fen: String, pgn: String): String =
    objectMapper.writeValueAsString(toDto(entry, fen, pgn))

  private def toDto(entry: GameEntry, fen: String, pgn: String): GameCacheDto =
    val clock = entry.engine.currentClockState
    GameCacheDto(
      gameId = entry.gameId,
      whiteId = entry.white.id.value,
      whiteName = entry.white.displayName,
      blackId = entry.black.id.value,
      blackName = entry.black.displayName,
      mode = entry.mode.toString,
      pgn = pgn,
      fen = fen,
      resigned = entry.resigned,
      limitSeconds = entry.engine.timeControl match { case TimeControl.Clock(l, _) => Some(l); case _ => None },
      incrementSeconds = entry.engine.timeControl match { case TimeControl.Clock(_, i) => Some(i); case _ => None },
      daysPerMove = entry.engine.timeControl match { case TimeControl.Correspondence(d) => Some(d); case _ => None },
      whiteRemainingMs = clock.collect { case c: LiveClockState => c.whiteRemainingMs },
      blackRemainingMs = clock.collect { case c: LiveClockState => c.blackRemainingMs },
      incrementMs = clock.collect { case c: LiveClockState => c.incrementMs },
      clockLastTickAt = clock.collect { case c: LiveClockState => c.lastTickAt.toEpochMilli },
      clockMoveDeadline = clock.collect { case c: CorrespondenceClockState => c.moveDeadline.toEpochMilli },
      clockActiveColor = clock.map(_.activeColor.label.toLowerCase),
      pendingDrawOffer = entry.engine.pendingDrawOfferBy.map(_.label.toLowerCase),
      redoStack = entry.engine.redoStackMoves.map(GameDtoMapper.moveToUci),
      pendingTakebackRequest = entry.engine.pendingTakebackRequestBy.map(_.label.toLowerCase),
    )

  private def sameSnapshot(localEntry: GameEntry, redisEntry: GameEntry): Boolean =
    entryHash(localEntry).exists(localHash => entryHash(redisEntry).contains(localHash))

  private def entryHash(entry: GameEntry): Option[String] =
    Try {
      val combined      = ioClient.exportCombined(entry.engine.context)
      val canonicalJson = objectMapper.writeValueAsString(toDto(entry, combined.fen, combined.pgn))
      val digest        = MessageDigest.getInstance("SHA-256").digest(canonicalJson.getBytes(StandardCharsets.UTF_8))
      digest.map("%02x".format(_)).mkString
    }.toOption
