package de.nowchess.ws.resource

import io.quarkus.redis.datasource.pubsub.PubSubCommands

final case class ConnectionMeta(
    gameId: String,
    subscriber: PubSubCommands.RedisSubscriber,
    playerId: Option[String],
)
