package de.nowchess.coordinator.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

case class InstanceMetadata(
    @JsonProperty("instanceId")
    instanceId: String,
    @JsonProperty("hostname")
    hostname: String,
    @JsonProperty("httpPort")
    httpPort: Int,
    @JsonProperty("grpcPort")
    grpcPort: Int,
    @JsonProperty("subscriptionCount")
    subscriptionCount: Int,
    @JsonProperty("localCacheSize")
    localCacheSize: Int,
    @JsonProperty("lastHeartbeat")
    lastHeartbeat: String,
    @JsonProperty("state")
    state: String = "HEALTHY",
)
