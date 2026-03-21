# ADR-002: Shared-Models Library (`modules/api`)

## Status
Accepted

## Context

NowChessSystems is a microservice platform. As soon as two or more services need to
exchange data — whether through REST, messaging, or internal function calls — they must
agree on common data types. Without a shared home for those types, the same case class
(e.g. `Square`, `Move`, `GameState`) is duplicated in every module, diverges over time,
and causes silent serialisation mismatches at runtime.

The `core` module currently owns the chess engine logic. Future modules (matchmaking,
game history, user management, notation export, etc.) will all need to refer to the
same chess domain vocabulary. A cross-cutting place to hold that vocabulary is therefore
required before any second service is built.

## Decision

We introduce `modules/api` as a **shared-models library**: a plain Scala 3 library
(no Quarkus, no Jakarta, no persistence) that contains only:

- Pure Scala 3 data types: `case class`, `sealed trait`, and `enum` definitions
- Value objects that model the chess domain (pieces, colors, squares, moves, game state)
- Cross-service API envelope types (`ApiResponse[A]`, `ApiError`, `Pagination`)
- Minimal player/user identity stubs (IDs and display names only)

Every service module that needs these types declares:

```kotlin
implementation(project(":modules:api"))
```

in its own `build.gradle.kts`. The `modules/api` module itself carries no runtime
dependencies beyond the Scala 3 standard library.

### Package layout

```
de.nowchess.api
├── board          – Color, PieceType, Piece, File, Rank, Square
├── game           – CastlingRights, GameState, GameResult, GameStatus
├── move           – MoveType, Move, PromotionPiece
├── player         – PlayerId, PlayerInfo
└── response       – ApiResponse, ApiError, Pagination
```

## What belongs in `modules/api`

| Belongs | Does NOT belong |
|---|---|
| `case class`, `sealed trait`, `enum` for chess domain | Quarkus `@ApplicationScoped` beans |
| API envelope types (`ApiResponse`, `ApiError`) | Jakarta Persistence entities (`@Entity`) |
| Player identity stubs (ID + display name) | REST resource classes |
| FEN/board-state representation types | Business logic, engine algorithms |
| Pure type aliases and value objects | Database queries or repositories |

The rule of thumb: if a type carries a framework annotation or requires I/O to produce,
it does not belong in `modules/api`.

## How other modules depend on it

1. `modules/api` is a regular Gradle subproject already declared in `settings.gradle.kts`.
2. Consuming modules add `implementation(project(":modules:api"))` — nothing else.
3. Because `modules/api` has no Quarkus BOM, consuming modules must not re-export Quarkus
   transitive dependencies through it.
4. If a future module needs JSON serialisation, it adds its own JSON library (e.g.
   `circe`, `jsoniter-scala`) as a dependency and derives codecs for the shared types
   there — codec derivation stays out of `modules/api`.

## Consequences

### Positive
- Single source of truth for all chess domain vocabulary.
- Adding a new microservice requires only one `implementation(project(":modules:api"))`
  line — no copy-paste of types.
- The library is fast to compile (no framework processing) and cheap to test in isolation.
- Enforces a strict boundary: if a type needs a framework annotation it is forced into the
  correct service module.

### Negative / Risks
- Any breaking change to a shared type (rename, field removal) is a cross-cutting change
  that touches every consuming module simultaneously.
- Developers must resist the temptation to add convenience methods or logic to these
  types; discipline is required to keep the library pure.
