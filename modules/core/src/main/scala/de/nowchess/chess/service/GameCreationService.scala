package de.nowchess.chess.service

import de.nowchess.api.dto.{GameCreationRequestDto, PlayerInfoDto, TimeControlDto}
import de.nowchess.api.game.{GameContext, GameMode, TimeControl}
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.grpc.RuleSetGrpcAdapter
import de.nowchess.chess.redis.GameRedisSubscriberManager
import de.nowchess.chess.registry.{GameEntry, GameRegistry}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import scala.compiletime.uninitialized

@ApplicationScoped
class GameCreationService:

  private val log = Logger.getLogger(classOf[GameCreationService])

  // scalafix:off DisableSyntax.var
  @Inject var registry: GameRegistry                        = uninitialized
  @Inject var ruleSetAdapter: RuleSetGrpcAdapter            = uninitialized
  @Inject var subscriberManager: GameRedisSubscriberManager = uninitialized
  // scalafix:on DisableSyntax.var

  private val DefaultWhite = PlayerInfo(PlayerId("p1"), "Player 1")
  private val DefaultBlack = PlayerInfo(PlayerId("p2"), "Player 2")

  def createGame(req: GameCreationRequestDto): GameEntry =
    val white = playerInfoFrom(req.white, DefaultWhite)
    val black = playerInfoFrom(req.black, DefaultBlack)
    val tc    = toTimeControl(req.timeControl)
    val mode  = req.mode.getOrElse(GameMode.Open)
    val entry = newEntry(GameContext.initial, white, black, tc, mode)
    registry.store(entry)
    subscriberManager.subscribeGame(entry.gameId)
    log.infof(
      "Game %s created — white=%s black=%s mode=%s",
      entry.gameId,
      white.displayName,
      black.displayName,
      mode.toString,
    )
    entry

  private def playerInfoFrom(dto: Option[PlayerInfoDto], default: PlayerInfo): PlayerInfo =
    dto.fold(default)(d => PlayerInfo(PlayerId(d.id), d.displayName))

  private def toTimeControl(dto: Option[TimeControlDto]): TimeControl =
    dto match
      case None => TimeControl.Unlimited
      case Some(tc) =>
        tc.daysPerMove match
          case Some(d) => TimeControl.Correspondence(d)
          case None =>
            tc.limitSeconds.fold(TimeControl.Unlimited)(l => TimeControl.Clock(l, tc.incrementSeconds.getOrElse(0)))

  private def newEntry(
      ctx: GameContext,
      white: PlayerInfo,
      black: PlayerInfo,
      tc: TimeControl,
      mode: GameMode,
  ): GameEntry =
    GameEntry(
      registry.generateId(),
      GameEngine(initialContext = ctx, ruleSet = ruleSetAdapter, timeControl = tc),
      white,
      black,
      mode = mode,
    )
