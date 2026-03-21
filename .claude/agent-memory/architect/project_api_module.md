---
name: api-shared-models module
description: Status and design decisions for the modules/api shared-models library
type: project
---

`modules/api` is established as the shared-models library for NowChessSystems.

**Why:** All microservices need a common chess domain vocabulary (Square, Move, GameState, etc.) and cross-cutting API envelope types (ApiResponse, ApiError). Without a shared module, types diverge and cause serialisation mismatches.

**How to apply:** When designing any new service, confirm it declares `implementation(project(":modules:api"))` and does not duplicate any of the types already present. New cross-cutting types (used by 2+ services) should go into `modules/api`, not into a service module.

Package layout:
- `de.nowchess.api.board`    — Color, PieceType, Piece, File, Rank, Square
- `de.nowchess.api.game`     — CastlingRights, GameState, GameResult, GameStatus
- `de.nowchess.api.move`     — MoveType, Move, PromotionPiece
- `de.nowchess.api.player`   — PlayerId (opaque type), PlayerInfo
- `de.nowchess.api.response` — ApiResponse[A], ApiError, Pagination, PagedResponse[A]

ADR: `docs/adr/ADR-002-api-shared-models.md`
