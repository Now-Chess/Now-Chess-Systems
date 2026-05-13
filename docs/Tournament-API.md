# NowChess Tournament API

Swiss-system bot tournaments. Bots are paired by score each round; all bots play every round (no eliminations). Game moves flow through the existing board and bot endpoints — the tournament module only orchestrates pairings, standings, and lifecycle.

---

## Base path

```
/api/tournament
```

Routing: `/api/tournament` → `nowchess-tournament-active:8086`

---

## Authentication

All endpoints require a valid JWT (`Authorization: Bearer <token>`).  
Bot-facing streaming endpoints additionally require the token's subject to match the registered `botId`.

---

## Data models

### Tournament

```json
{
  "id": "t7kXq2",
  "name": "Friday Night Bots",
  "status": "created | started | finished",
  "rounds": 5,
  "currentRound": 2,
  "timeControl": {
    "limitSeconds": 300,
    "incrementSeconds": 3
  },
  "createdBy": "userId",
  "createdAt": "2026-05-13T18:00:00Z",
  "startedAt": "2026-05-13T18:05:00Z",
  "finishedAt": null
}
```

### Standing

```json
{
  "rank": 1,
  "botId": "bot_abc",
  "botName": "StockfishClone",
  "points": 3.5,
  "wins": 3,
  "draws": 1,
  "losses": 0,
  "buchholz": 9.0
}
```

Tiebreaker: Buchholz score (sum of opponents' points).

### Pairing

```json
{
  "round": 2,
  "whiteBot": "bot_abc",
  "blackBot": "bot_xyz",
  "gameId": "j0nPtcjl",
  "result": "white | black | draw | ongoing"
}
```

### TournamentEvent (SSE)

```json
{ "type": "tournamentStarted", "tournamentId": "t7kXq2" }
{ "type": "roundStarted",      "tournamentId": "t7kXq2", "round": 2 }
{ "type": "pairingReady",      "tournamentId": "t7kXq2", "round": 2, "gameId": "j0nPtcjl", "color": "white" }
{ "type": "roundFinished",     "tournamentId": "t7kXq2", "round": 2 }
{ "type": "tournamentFinished","tournamentId": "t7kXq2" }
```

---

## Endpoints

### Tournament lifecycle

#### Create tournament

```
POST /api/tournament
```

Body:

```json
{
  "name": "Friday Night Bots",
  "rounds": 5,
  "timeControl": {
    "limitSeconds": 300,
    "incrementSeconds": 3
  }
}
```

Response `201 Created`:

```json
{ "id": "t7kXq2" }
```

The creator becomes the tournament director. Only the director can start and delete the tournament.

---

#### Get tournament

```
GET /api/tournament/{tournamentId}
```

Response `200 OK`: `Tournament` object.

---

#### List tournaments

```
GET /api/tournament
```

Query params:

| Param    | Type                            | Default   |
|----------|---------------------------------|-----------|
| `status` | `created\|started\|finished`    | (all)     |
| `limit`  | integer (max 50)                | 20        |
| `offset` | integer                         | 0         |

Response `200 OK`:

```json
{
  "tournaments": [ /* Tournament[] */ ],
  "total": 42
}
```

---

#### Start tournament

```
POST /api/tournament/{tournamentId}/start
```

Requires at least 2 registered bots. Computes round 1 pairings (random for round 1; score-based from round 2). Creates one game per pairing via `POST /api/board/game`.

Response `200 OK`: updated `Tournament` object.

---

#### Delete tournament

```
DELETE /api/tournament/{tournamentId}
```

Only allowed while `status == "created"`. Response `204 No Content`.

---

### Bot registration

#### Register bot

```
POST /api/tournament/{tournamentId}/bots
```

Registers a bot for the tournament. Must be called before the tournament starts.  
The token subject must match the bot being registered.

Body:

```json
{ "botId": "bot_abc" }
```

Response `200 OK`:

```json
{ "botId": "bot_abc", "tournamentId": "t7kXq2" }
```

---

#### Unregister bot

```
DELETE /api/tournament/{tournamentId}/bots/{botId}
```

Only allowed while `status == "created"`. Response `204 No Content`.

---

#### List registered bots

```
GET /api/tournament/{tournamentId}/bots
```

Response `200 OK`:

```json
{
  "bots": [
    { "botId": "bot_abc", "botName": "StockfishClone" }
  ]
}
```

---

### Standings and pairings

#### Get standings

```
GET /api/tournament/{tournamentId}/standings
```

Response `200 OK`:

```json
{ "standings": [ /* Standing[] */ ] }
```

---

#### Get pairings for a round

```
GET /api/tournament/{tournamentId}/rounds/{round}/pairings
```

Response `200 OK`:

```json
{ "pairings": [ /* Pairing[] */ ] }
```

---

### Bot streaming

#### Stream tournament events

```
GET /api/tournament/{tournamentId}/stream
```

Headers: `Accept: text/event-stream`

Server-Sent Events stream scoped to this tournament. The bot receives `pairingReady` events when it is assigned a game, at which point it should connect to the existing bot game stream:

```
GET /bot/stream/game/{gameId}    (existing endpoint)
POST /bot/game/{gameId}/move/{uci}  (existing endpoint)
```

The tournament module never sends moves — bots do that themselves through the existing bot endpoints.

---

## Typical bot flow

```
1. POST /api/tournament                          # director creates tournament
2. POST /api/tournament/{id}/bots               # each bot registers
3. POST /api/tournament/{id}/start              # director starts
4. GET  /api/tournament/{id}/stream  (SSE)      # each bot opens stream

   -- per round --
5. receive: pairingReady { gameId, color }
6. GET  /bot/stream/game/{gameId}               # existing endpoint
7. POST /bot/game/{gameId}/move/{uci}           # existing endpoint, repeated
   -- game ends --

8. receive: roundFinished
9. GET  /api/tournament/{id}/standings          # optional, inspect scores
   -- repeat 5–9 for each round --

10. receive: tournamentFinished
11. GET /api/tournament/{id}/standings          # final ranking
```

---

## Error responses

| Status | Meaning                                              |
|--------|------------------------------------------------------|
| 400    | Invalid request body or parameters                   |
| 401    | Missing or invalid JWT                               |
| 403    | Action not allowed (wrong director, wrong bot, etc.) |
| 404    | Tournament or bot not found                          |
| 409    | Tournament already started / bot already registered  |
