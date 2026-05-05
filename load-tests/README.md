# NowChess Load Testing with k6

Performance testing suite for NowChess services using k6.

## Installation

```bash
# Install k6 (macOS)
brew install k6

# Or download from https://k6.io/docs/getting-started/installation/
```

## Test Scenarios

### 1. Ramp-Up Test
Gradually increases load from 10 to 100 concurrent users.

```bash
k6 run ramp-up.js
```

Target: Identify system behavior under gradual load increase.

### 2. Stress Test
Incremental load increase up to 500 concurrent users to find breaking point.

```bash
k6 run stress-test.js
```

Target: Determine system capacity and failure point.

### 3. Spike Test
Sudden traffic surge (baseline 50 → 500 users instantly).

```bash
k6 run spike-test.js
```

Target: Test recovery and resilience to sudden spikes.

### 4. Constant Load Test
Maintains 50 VUs for 10 minutes.

```bash
k6 run constant-load.js
```

Target: Check stability under sustained load.

## Environment Variables

```bash
# Override service endpoints
export BASE_URL=http://localhost:8080
export ACCOUNT_HOST=http://localhost:8083
export STORE_HOST=http://localhost:8085

k6 run ramp-up.js
```

## Prerequisites

Ensure services are running:
- Core: `localhost:8080`
- Account: `localhost:8083`
- Store: `localhost:8085`
- Redis: `localhost:6379` (with increased pool size)

## Metrics Interpretation

- `http_req_duration`: Response time (p95, p99 percentiles matter most)
- `http_req_failed`: Failed requests (connection errors, errors, non-2xx responses)
- `vus`: Virtual Users (concurrent connections)
- `iterations`: Completed test cycles per VU

## Results

k6 generates HTML report output. Use with:

```bash
k6 run --out=csv=results.csv ramp-up.js
```

## Troubleshooting

If you see:
- **"max pool wait timeout"** → Redis pool still too small or maxWaitTime too short
- **"connection refused"** → Service not running
- **"high p99 latency"** → System approaching capacity

Increase Redis pool settings in `modules/*/src/main/resources/application.yml`:
```yaml
quarkus:
  redis:
    pool:
      max-size: 128  # Increase if still hitting limits
      max-waiting: 256
```
