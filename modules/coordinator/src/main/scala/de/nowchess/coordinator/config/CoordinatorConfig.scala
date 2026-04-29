package de.nowchess.coordinator.config

import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithName
import java.time.Duration

@ConfigMapping(prefix = "nowchess.coordinator")
trait CoordinatorConfig:
  @WithName("max-games-per-core")
  def maxGamesPerCore: Int

  @WithName("max-deviation-percent")
  def maxDeviationPercent: Int

  @WithName("rebalance-interval")
  def rebalanceInterval: Duration

  @WithName("rebalance-min-interval")
  def rebalanceMinInterval: Duration

  @WithName("heartbeat-ttl")
  def heartbeatTtl: Duration

  @WithName("stream-heartbeat-interval")
  def streamHeartbeatInterval: Duration

  @WithName("cache-eviction-interval")
  def cacheEvictionInterval: Duration

  @WithName("game-idle-threshold")
  def gameIdleThreshold: Duration

  @WithName("auto-scale-enabled")
  def autoScaleEnabled: Boolean

  @WithName("scale-up-threshold")
  def scaleUpThreshold: Double

  @WithName("scale-down-threshold")
  def scaleDownThreshold: Double

  @WithName("scale-min-replicas")
  def scaleMinReplicas: Int

  @WithName("scale-max-replicas")
  def scaleMaxReplicas: Int

  @WithName("k8s-namespace")
  def k8sNamespace: String

  @WithName("k8s-rollout-name")
  def k8sRolloutName: String

  @WithName("k8s-rollout-label-selector")
  def k8sRolloutLabelSelector: String
