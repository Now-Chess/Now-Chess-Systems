# Plan: Add Coordinator Microservice

## Context
NowChess scales `core` horizontally via shared Redis but lacks:
- **Instance visibility**: no way to list running cores or their load
- **Load balancing**: games land randomly on cores; no rebalancing
- **Failover**: dead cores orphan subscriptions; bullet chess requires <1s recovery
- **Auto-scaling**: manual ops to add/remove cores
- **Cache management**: no eviction of stale games from core memory

Bullet chess games run on move timings of <3s. 30s failover = game lost on clock. Target: **<300ms failover**.

---

## Architecture: Sub-1s Failover

### Why Not Polling/TTL
- TTL expiry: minimum 10-30s detection
- HTTP polling 3x failure: 30s minimum
- **gRPC streaming TCP drop: 50-200ms** — use this as primary

### Primary: gRPC Bidirectional Streaming
- Core opens a **persistent bidirectional stream** (`CoreHeartbeatStream`) to coordinator on startup
- Core sends heartbeat frames every **200ms**
- Core crash = TCP RST/FIN → coordinator stream error in **~50-200ms**
- Stream also carries metadata updates (subscription count changes) in real-time

### Fallback: Redis Heartbeat + K8s Watch
- Redis heartbeat key `{prefix}:instances:{instanceId}` with **5s TTL**, refreshed every **2s**
- K8s pod watch via Kubernetes Java client (event-driven; handles pod eviction/OOMKill)
- Fallback covers: network partition (TCP stays up but core is zombie), coordinator restart gap

---

## Design

### 1. Module: `modules/coordinator`
**Language**: Scala 3.5.1, Quarkus REST + gRPC  
**Ports**: HTTP 8086, gRPC 9086  
**Dependencies**: Redisson, Kubernetes Java client, Quarkus gRPC  
**Persistence**: None (all state in Redis)

---

### 2. Instance Registry

**Redis schema**:
```
{prefix}:instances:{instanceId}
  - TTL: 5s (refreshed by core every 2s via background task)
  - Value: JSON
    {
      "instanceId": "core-abc123",
      "hostname": "core-pod-3",
      "httpPort": 8080,
      "grpcPort": 9080,
      "subscriptionCount": 147,
      "localCacheSize": 147,
      "lastHeartbeat": "2026-04-26T10:15:30.123Z"
    }

{prefix}:instance:{instanceId}:games
  - Type: Redis Set (no TTL — managed explicitly)
  - Members: all gameIds currently subscribed on this instance
```

**Core changes** (new `InstanceHeartbeatService` bean in `modules/core`):
- `@PostConstruct`: generate stable `instanceId` (hostname + random suffix); open gRPC stream to coordinator; publish Redis heartbeat; register in `{prefix}:instances:{instanceId}`
- Every 200ms: send heartbeat frame on gRPC stream (carries `subscriptionCount`)
- Every 2s: refresh Redis heartbeat bucket TTL
- `subscribeGame(gameId)`: `SADD {prefix}:instance:{instanceId}:games gameId`
- `unsubscribeGame(gameId)` / `evictGame(gameId)`: `SREM {prefix}:instance:{instanceId}:games gameId`
- `@PreDestroy`: delete Redis key + games set; close gRPC stream (clean shutdown)

---

### 3. Health Monitoring (3 signals, primary fast)

| Signal | Mechanism | Detection time | Role |
|--------|-----------|---------------|------|
| **gRPC stream drop** | TCP RST/FIN on bidirectional stream | 50–200ms | Primary |
| **Redis heartbeat expiry** | `{prefix}:instances:{instanceId}` TTL=5s | 5–7s | Fallback |
| **K8s pod watch** | `CoreV1Api.listNamespacedPod` watch stream | ~instant (pod events) | Fallback |

**Dead decision**:
- gRPC stream drops → **immediate failover** (no confirmation needed; games must recover fast)
- Redis heartbeat expires (gRPC still up) → verify with single HTTP `/q/health` call → if fail: failover
- K8s pod NotReady (gRPC still up) → failover

---

### 4. Failover Protocol (<300ms target)

```
T+0ms     Core JVM crashes / network drops
T+50ms    Coordinator: gRPC stream error received
T+52ms    SMEMBERS {prefix}:instance:{instanceId}:games  → list of orphaned gameIds
T+55ms    Distribute gameIds across healthy cores (least-loaded first)
T+60ms    BatchResubscribeGames gRPC call(s) fire to healthy core(s)
T+150ms   Healthy cores resubscribed; Redis s2c topics live again
T+200ms   WebSocket clients reconnect; receive GameFullEventDto on CONNECTED
```

**Failover steps** (coordinator `FailoverService`):
1. On stream drop for `instanceId`:
   a. Mark instance DEAD in local map
   b. `SMEMBERS {prefix}:instance:{instanceId}:games`
   c. Group gameIds into batches per target core (round-robin by load)
   d. For each target core: call `BatchResubscribeGames(gameIds)`
   e. Each target core: calls `subscribeGame(gameId)` for each (loads from Redis if not in local cache)
   f. `DEL {prefix}:instance:{instanceId}:games` (cleanup)
2. Log failover event with count of games migrated + latency

---

### 5. Load Rebalancing

**Thresholds** (both must be evaluated):
1. **Absolute**: any core > 500 games → rebalance
2. **Relative**: max load > mean × 1.2 AND max - min > 50 games → rebalance

**Algorithm** (runs every 30s, min 60s between actual rebalances):
1. Read all `{prefix}:instances:*` keys → load map
2. Identify overloaded cores (exceed either threshold)
3. For each overloaded core: pick `excess = load - targetLoad` games
4. Assign excess games to underloaded cores
5. Call `UnsubscribeGames(gameIds)` on overloaded core
6. Call `BatchResubscribeGames(gameIds)` on target core
7. Overloaded core: `SREM` each game from its set
8. Target core: `SADD` each game to its set on subscribe

---

### 6. Auto-Scaling

**Metric**: avg `subscriptionCount` across all cores

**Actions**:
- avg > `scale-up-threshold` (80% of max): patch `nowchess-core` Argo Rollout `spec.replicas += 1`
- avg < `scale-down-threshold` (30% of max) AND `replicas > min-replicas`: drain one core then scale down
- Backoff: min 2-minute interval between scale events

**Argo Rollouts API**:
- CRD: `argoproj.io/v1alpha1`, Kind: `Rollout`, resource: `rollouts`
- Scale via Fabric8 `GenericKubernetesResource` patch on `spec.replicas`
- No StatefulSet — Argo Rollout owns pod lifecycle (canary/blue-green strategies respected)
- Pod watch filter: label selector `app=nowchess-core` (Rollout sets this; `rollouts-pod-template-hash` is Argo's equivalent of `pod-template-hash`)

**Drain before scale-down**:
1. Pick least-loaded core
2. Migrate all its games to other cores via `BatchResubscribeGames`
3. Call `DrainInstance(instanceId)` on that core (sets it to reject new subscriptions)
4. After drain confirmed: patch Rollout `spec.replicas -= 1`

---

### 7. Cache Eviction

**Trigger**: coordinator scans `{prefix}:game:entry:*` every 10 minutes  
**Policy**: if `now - lastUpdated > 45min` AND `gameId` in any instance's games set → call `EvictGame`  
**Effect**: core removes game from `localEngines` and `unsubscribeGame`, `SREM` from instance set

---

### 8. Proto: `coordinator_service.proto`

```proto
syntax = "proto3";
package de.nowchess.coordinator;

service CoordinatorService {
  // Core → Coordinator: bidirectional stream for liveness
  rpc HeartbeatStream(stream HeartbeatFrame) returns (stream CoordinatorCommand);

  // Coordinator → Core: batch resubscribe after failover or rebalance
  rpc BatchResubscribeGames(BatchResubscribeRequest) returns (BatchResubscribeResponse);

  // Coordinator → Core: unsubscribe games (rebalance source)
  rpc UnsubscribeGames(UnsubscribeGamesRequest) returns (UnsubscribeGamesResponse);

  // Coordinator → Core: evict idle games from local cache
  rpc EvictGames(EvictGamesRequest) returns (EvictGamesResponse);

  // Coordinator → Core: drain instance before scale-down
  rpc DrainInstance(DrainInstanceRequest) returns (DrainInstanceResponse);
}

message HeartbeatFrame {
  string instanceId = 1;
  string hostname = 2;
  int32 httpPort = 3;
  int32 grpcPort = 4;
  int32 subscriptionCount = 5;
  int32 localCacheSize = 6;
  int64 timestampMillis = 7;
}

message CoordinatorCommand {
  // Future: coordinator can push commands back (e.g., "start draining")
  string type = 1;
  string payload = 2;
}

message BatchResubscribeRequest {
  repeated string gameIds = 1;
}

message BatchResubscribeResponse {
  int32 subscribedCount = 1;
  repeated string failedGameIds = 2;
}

message UnsubscribeGamesRequest {
  repeated string gameIds = 1;
}

message UnsubscribeGamesResponse {
  int32 unsubscribedCount = 1;
}

message EvictGamesRequest {
  repeated string gameIds = 1;
}

message EvictGamesResponse {
  int32 evictedCount = 1;
}

message DrainInstanceRequest {}

message DrainInstanceResponse {
  int32 gamesMigrated = 0;
}
```

---

### 9. Coordinator REST API (internal)

- `GET /api/coordinator/instances` — all cores with load, health state
- `GET /api/coordinator/metrics` — load distribution, rebalance history
- `POST /api/coordinator/rebalance` — manual rebalance trigger
- `POST /api/coordinator/failover/{instanceId}` — manual failover
- `POST /api/coordinator/scale-up` / `scale-down` — manual scaling

---

### 10. Configuration

**`modules/coordinator/src/main/resources/application.yml`**:
```yaml
quarkus.application.name: nowchess-coordinator
quarkus.http.port: 8086
quarkus.grpc.server.port: 9086

nowchess.coordinator.max-games-per-core: 500
nowchess.coordinator.max-deviation-percent: 20
nowchess.coordinator.rebalance-interval: 30s
nowchess.coordinator.rebalance-min-interval: 60s
nowchess.coordinator.heartbeat-ttl: 5s
nowchess.coordinator.stream-heartbeat-interval: 200ms
nowchess.coordinator.cache-eviction-interval: 10m
nowchess.coordinator.game-idle-threshold: 45m
nowchess.coordinator.auto-scale-enabled: false
nowchess.coordinator.scale-up-threshold: 0.8
nowchess.coordinator.scale-down-threshold: 0.3
nowchess.coordinator.scale-min-replicas: 2
nowchess.coordinator.scale-max-replicas: 10
nowchess.coordinator.k8s-namespace: default
nowchess.coordinator.k8s-rollout-name: nowchess-core
nowchess.coordinator.k8s-rollout-label-selector: app=nowchess-core

quarkus.kubernetes-client.trust-certs: true
```

**Core `application.yml` additions**:
```yaml
nowchess.coordinator.host: localhost
nowchess.coordinator.grpc-port: 9086
nowchess.coordinator.stream-heartbeat-interval: 200ms
nowchess.coordinator.redis-heartbeat-interval: 2s
nowchess.coordinator.instance-id: ${HOSTNAME:local}-${quarkus.uuid}
```

---

### 11. Files to Create / Modify

**New — `modules/coordinator/`**:
```
build.gradle.kts
src/main/proto/coordinator_service.proto
src/main/resources/application.yml
src/main/scala/de/nowchess/coordinator/
  resource/CoordinatorResource.scala       # REST endpoints
  service/InstanceRegistry.scala           # Redis instance list + in-memory map
  service/HealthMonitor.scala              # gRPC stream watcher + Redis TTL + k8s watch
  service/FailoverService.scala            # dead core → BatchResubscribe
  service/LoadBalancer.scala               # rebalance logic
  service/AutoScaler.scala                 # k8s StatefulSet scaling
  service/CacheEvictionManager.scala       # idle game eviction
  grpc/CoordinatorGrpcServer.scala         # CoordinatorService gRPC impl (for HeartbeatStream)
```

**Modify — `modules/core/`**:
- `build.gradle.kts` — add `coordinator_service.proto` stub, keep grpc dep
- `src/main/proto/coordinator_service.proto` — copy (or symlink) proto for stub generation
- `src/main/scala/de/nowchess/chess/redis/GameRedisSubscriberManager.scala` — `SADD`/`SREM` on subscribe/unsubscribe + implement `BatchResubscribeGames`, `UnsubscribeGames`, `EvictGames`, `DrainInstance` gRPC handlers
- `src/main/scala/de/nowchess/chess/` — new `InstanceHeartbeatService.scala` (startup, gRPC stream, Redis TTL refresh)
- `src/main/resources/application.yml` — coordinator connection config

**Modify — root**:
- `settings.gradle.kts` — add `include("modules/coordinator")`

---

## Verification

1. `./compile` — coordinator and core compile cleanly
2. **Stream detection**: start core + coordinator; kill core JVM (`kill -9`); coordinator logs failover within 300ms
3. **Game continuity**: active game on killed core; WebSocket client reconnects and receives game state
4. **Rebalance**: create 600 games on core-1 (2-core setup); coordinator rebalances ~100 to core-2
5. **Fallback**: disconnect gRPC stream manually but keep core alive; Redis TTL fallback triggers within 7s
6. **Cache eviction**: create idle game; coordinator calls `EvictGames` after 45min idle
7. **REST metrics**: `curl localhost:8086/api/coordinator/metrics` returns per-core load + health
8. **Restart recovery**: restart coordinator; gRPC streams re-establish from cores; state rebuilt from Redis

---

## Dependencies (new)

- `io.fabric8:kubernetes-client:6.13.0` (Fabric8 k8s client — handles Argo `Rollout` CRD via `GenericKubernetesResource`; no Argo Java SDK needed)
- Redisson — already in core, reuse via shared config
- Quarkus gRPC — already in core, reuse
