# Coordinator Module - Bug Report

## Critical Bugs

### 1. Cache Eviction Kills Correspondence Games (HIGH)
**File:** `CacheEvictionManager.scala:96-101`
**Problem:** Uses `lastHeartbeat` timestamp from GameCacheDto to determine if game is idle. But `lastHeartbeat` is set at store/update time, not move time. Correspondence games with days between moves get evicted while active.

**Current Code:**
```scala
private def extractLastUpdatedTimestamp(json: String): Long =
  Try {
    val parsed = objectMapper.readTree(json)
    Option(parsed.get("lastHeartbeat"))
      .filter(_.isTextual)
      .fold(0L)(lh => Instant.parse(lh.asText()).toEpochMilli)
  }.getOrElse(0L)
```

**Impact:** Active correspondence games deleted from cache after idle threshold (config-dependent, typically hours/days)
**Fix:** Track actual move timestamp separately in GameCacheDto or check game state instead of heartbeat

---

### 2. Concurrent Rebalance Race Condition (HIGH)
**File:** `LoadBalancer.scala:108-115`
**Problem:** `getGamesToMove()` reads games from Redis set but doesn't remove them atomically. If multiple rebalance calls run concurrently, same game can be selected in different batches and moved to multiple instances.

**Current Code:**
```scala
private def getGamesToMove(instanceId: String, count: Int): List[String] =
  try
    val setKey = s"$redisPrefix:instance:$instanceId:games"
    redis.set(classOf[String]).smembers(setKey).asScala.toList.take(count)  // Read-only, no removal
  catch
    case ex: Exception =>
      log.debugf(ex, "Failed to get games for %s", instanceId)
      List()
```

**Impact:** Game subscribed to 2+ instances, state corruption, double-processing
**Fix:** Use Redis SPOP (atomic pop) or Lua script for atomic read+remove

---

### 3. Pod Matching is Unreliable (MEDIUM)
**File:** `HealthMonitor.scala:134, 188`
**Problem:** Uses `.contains()` string matching for pod name. Pod "core-1" matches instance "core-11"; loose matching causes wrong pod operations.

**Current Code:**
```scala
instanceId.contains(podName)  // Line 134
// and
pods.find(pod => instanceId.contains(pod.getMetadata.getName))  // Line 188
```

**Impact:** Wrong pod deleted/evicted when multiple similar names exist
**Fix:** Exact match or structured ID encoding

---

## Medium Priority Bugs

### 4. Inefficient Game-to-Instance Lookup (MEDIUM)
**File:** `CacheEvictionManager.scala:104-113`
**Problem:** Linear scan through ALL instances to find which one holds a game. Runs per-game during eviction scan every 5 minutes.

**Current Code:**
```scala
private def findInstanceWithGame(gameId: String): Option[InstanceMetadata] =
  try
    instanceRegistry.getAllInstances.find { instance =>  // O(n) instances
      val setKey = s"$redisPrefix:instance:${instance.instanceId}:games"
      redis.set(classOf[String]).sismember(setKey, gameId)
    }
```

**Impact:** Eviction scans slow with many instances (100+ instances = 100+ Redis ops per game)
**Fix:** Maintain `nowchess:game:$gameId:instance` → instanceId mapping in Redis

---

### 5. Instance Registry Lookup on Pod Events (MEDIUM)
**File:** `HealthMonitor.scala:245-247`
**Problem:** Linear search through all instances every pod state change. Pod watch fires frequently.

**Current Code:**
```scala
private def findRegisteredInstance(pod: Pod): Option[InstanceMetadata] =
  val podName = pod.getMetadata.getName
  instanceRegistry.getAllInstances.find(inst => inst.instanceId.contains(podName))
```

**Impact:** O(n) lookup on hot path (pod watch events)
**Fix:** Maintain pod-name → instanceId index or use proper ID encoding

---

## Low Priority Bugs

### 6. Non-idiomatic Sorting (LOW)
**File:** `LoadBalancer.scala:72`
**Problem:** Uses `.sortBy[Int](_.subscriptionCount).reverse` instead of `.sortByDescending()`

**Current Code:**
```scala
val overloaded = instances
  .filter(_.subscriptionCount > config.maxGamesPerCore)
  .sortBy[Int](_.subscriptionCount)  // Type annotation unnecessary
  .reverse
```

**Impact:** Micro code-quality issue
**Fix:** Use `.sortByDescending(_.subscriptionCount)`

---

## Fix Priority

1. **Cache eviction (HIGH)** — Data loss risk
2. **Rebalance race (HIGH)** — State corruption risk
3. **Pod matching (MEDIUM)** — Operational blast radius
4. **Game lookup (MEDIUM)** — Performance under scale
5. **Instance lookup (MEDIUM)** — Hot path perf
6. **Sorting (LOW)** — Code style
