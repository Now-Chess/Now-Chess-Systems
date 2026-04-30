# NowChess Bruno API Collection

Complete API collection for all NowChess microservices.

## Structure

```
bruno/
├── collection.bru              # Collection metadata
├── environments/               # Environment configurations
│   ├── local.bru              # Local development (http://localhost)
│   ├── staging.bru            # Staging (https://st.nowchess.janis-eccarius.de)
│   └── prod.bru               # Production (https://nowchess.janis-eccarius.de)
├── core/                       # Core service endpoints (port 8080)
│   ├── game.bru               # Game management
│   ├── rules.bru              # Rule validation (@InternalOnly)
│   ├── io.bru                 # Import/Export (@InternalOnly)
│   └── coordinator.bru        # Orchestration
├── account/                    # Account service (port 8083)
│   ├── account.bru            # User & bot accounts
│   ├── challenge.bru          # Player challenges
│   └── official-challenge.bru # Official bot challenges
├── store/                      # Store service (port 8085)
│   └── game.bru               # Game persistence
├── ws/                         # WebSocket (port 8084)
│   ├── game-ws.bru            # Game real-time updates
│   └── user-ws.bru            # User notifications
└── bot/                        # Bot integration
    └── events.bru             # Bot event streaming
```

## Ingress Routing

Based on `ingress-nginx` configuration:

```yaml
/api/account  → nowchess-account-active:8083
/ws           → nowchess-ws-active:8084
/api/store    → nowchess-store-active:8085
/api          → nowchess-core-active:8080
```

## Environments

### Local Development
```
baseUrl: http://localhost:8080
accountBaseUrl: http://localhost:8083
storeBaseUrl: http://localhost:8085
wsBaseUrl: ws://localhost:8084
```

### Staging
```
baseUrl: https://st.nowchess.janis-eccarius.de/api
wsBaseUrl: wss://st.nowchess.janis-eccarius.de/ws
```

### Production
```
baseUrl: https://nowchess.janis-eccarius.de/api
wsBaseUrl: wss://nowchess.janis-eccarius.de/ws
```

## Environment Variables

Set these in your Bruno environment for authentication:
- `token`: JWT token for regular user operations
- `adminToken`: JWT token with Admin role
- `botToken`: Bot account JWT token
- `internalToken`: Internal service-to-service token
- `gameId`: Game identifier for game endpoints
- `username`: User username for profile/challenge operations
- `playerId`: Player ID for store operations
- `botId`: Bot ID for bot endpoints
- `botName`: Official bot name for challenges
- `difficulty`: Bot difficulty 1000-2800 (ELO)
- `color`: white/black/random for bot challenges

## Security Notes

From `@modules/security/src`:
- **InternalOnly** endpoints require internal service authentication
  - `/api/rules/*`
  - `/io/*`
- **@RolesAllowed** endpoints require JWT with specific roles
  - Admin operations
  - Authenticated user operations
- Internal filters validate service-to-service calls

## API Endpoints Summary

### Public Endpoints (No Auth Required)
- `GET /api/account/{username}` - Public profile
- `GET /api/account/official-bots` - List official bots
- `GET /game/{gameId}` - Game record
- `GET /api/board/game/{gameId}` - Game state
- `GET /api/board/game/{gameId}/moves` - Legal moves

### Authenticated Endpoints (JWT Required)
- `POST /api/account` - Register
- `POST /api/account/login` - Login
- `GET /api/account/me` - Current user profile
- `POST /api/challenge/*` - Challenge operations
- `POST /api/challenge/official/{botName}` - Challenge official bot
- `POST /api/account/bots` - Create bot account
- `GET /api/bot/stream/events` - Bot event stream
- `WS /api/board/game/{gameId}/ws` - Game WebSocket
- `WS /api/user/ws` - User notifications

### Internal Endpoints (@InternalOnly)
- `POST /api/rules/*` - Rule validation
- `POST /io/*` - Format conversion
- `POST /api/board/game` - Create game

## Usage

1. **Select Environment**: Click the environment dropdown and choose local/staging/prod
2. **Set Variables**: Update `token`, `gameId`, `username`, etc. as needed
3. **Send Request**: Click Send on any endpoint
4. **WebSocket**: Use Bruno's WebSocket support or connect manually with:
   ```bash
   websocat wss://nowchess.janis-eccarius.de/ws/api/board/game/GAMEID/ws
   ```

## Example Workflows

### Play a Game
1. `POST /api/account` - Create user
2. `POST /api/account/login` - Get JWT token
3. `POST /api/challenge/official/Stockfish` - Challenge a bot
4. Use returned `gameId` for subsequent moves
5. `WS /api/board/game/{gameId}/ws` - Connect for real-time updates
6. `POST /api/board/game/{gameId}/move/{uci}` - Make moves

### Manage Bot Account
1. `POST /api/account` - Create main account
2. `POST /api/account/login` - Get token
3. `POST /api/account/bots` - Create bot account
4. Copy bot token from response
5. `GET /api/bot/stream/events?botId={botId}` - Listen for game invites
6. `POST /api/bot/game/{gameId}/move/{uci}` - Submit moves

## Notes

- All timestamps are ISO 8601 format
- Move notation uses UCI (e.g., "e2e4")
- Game IDs are auto-generated UUIDs
- WebSocket connections auto-close on inactivity (check your server timeout)
