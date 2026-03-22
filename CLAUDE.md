# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build everything
./gradlew build

# Build a single module
./gradlew :modules:<service>:build

# Run tests for a single module
./gradlew :modules:<service>:test

# Run a specific test class
./gradlew :modules:<service>:test --tests "de.nowchess.<service>.<ClassName>"
```

The only current module is `core` (`modules/core`).

## Architecture

**NowChessSystems** is a chess platform built as a Scala 3 + Quarkus microservice system.

- Multi-module Gradle project; every service lives under `modules/{service-name}`.
- Shared dependency versions live in the root `build.gradle.kts` under `extra["VERSIONS"]`.
- Each module reads versions via `rootProject.extra["VERSIONS"] as Map<String, String>`.
- `settings.gradle.kts` must `include(":modules:<service>")` for every module.

### Stack (ADR-001)
| Layer | Technology |
|---|---|
| Language | Scala 3.5.x |
| Backend framework | Quarkus + `quarkus-scala3` extension |
| Persistence | Hibernate / Jakarta Persistence |
| Frontend (TBD) | Vite; React/Angular/Vue under evaluation |
| TUI | Lanterna |
| Container orchestration | Kubernetes + ArgoCD + Kargo |

### Key Scala 3 / Quarkus Rules
- Use `given`/`using`, not `implicit` (no Scala 2 idioms).
- Use `Option`/`Either`/`Try`, never `null` or `.get`.
- Jakarta annotations only (`jakarta.*`), never `javax.*`.
- Use reactive types (`Uni`, `Multi`) for I/O; no blocking calls on the event loop.
- **Always exclude `org.scala-lang:scala-library` from Quarkus BOM** to avoid Scala 2 conflicts.
- **Unit tests use `extends AnyFunSuite with Matchers with JUnitSuiteLike`** — ScalaTest DSL, no `@Test` annotations needed.
- **Integration tests use `@QuarkusTest` with JUnit 5** — explicit `: Unit` return type still required on `@Test` methods.

### Agent Workflow (for new services)
1. **architect** → writes OpenAPI contract to `docs/api/{service}.yaml` and ADR to `docs/adr/`.
2. **scala-implementer** → reads contract, implements service under `modules/{service}/`.
3. **test-writer** → writes `@QuarkusTest` integration tests and `AnyFunSuite with Matchers with JUnitSuiteLike` unit tests.
4. **gradle-builder** → resolves any build/dependency issues.
5. **code-reviewer** → reviews; reports findings back without self-fixing.

Detailed working agreement (plan/verify/unresolved workflow) is in `.claude/CLAUDE.MD`.
