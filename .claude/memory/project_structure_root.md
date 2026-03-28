---
name: project-root-structure
description: Top-level project structure, modules list, and navigation notes for NowChessSystems
type: project
---

# NowChessSystems — Root Structure

## Directory layout (skip `build/`, `.gradle/`, `.idea/`)

```
NowChessSystems/
├── build.gradle.kts          # Root: sonarqube plugin, VERSIONS map
├── settings.gradle.kts       # include(":modules:core", ":modules:api")
├── gradlew / gradlew.bat
├── CLAUDE.md                 # Project instructions for Claude Code
├── .claude/
│   ├── CLAUDE.MD             # Working agreement (plan/verify/unresolved)
│   ├── settings.json
│   └── agents/               # architect, code-reviewer, gradle-builder, scala-implementer, test-writer
├── docs/
│   ├── Claude-Skills.md
│   ├── Security.md
│   └── unresolved.md
├── jacoco-reporter/          # Python scripts for coverage gap reporting
└── modules/
    ├── api/                  # Shared domain types (no logic)
    └── core/                 # TUI chess engine + game logic
```

## Modules

| Module | Gradle path | Purpose |
|--------|-------------|---------|
| `api` | `:modules:api` | Shared domain model: Board, Piece, Move, GameState, ApiResponse |
| `core` | `:modules:core` | TUI chess app: game logic, move validation, rendering |

`core` depends on `api` via `implementation(project(":modules:api"))`.

## VERSIONS (root `build.gradle.kts`)

| Key | Value |
|-----|-------|
| `QUARKUS_SCALA3` | 1.0.0 |
| `SCALA3` | 3.5.1 |
| `SCALA_LIBRARY` | 2.13.18 |
| `SCALATEST` | 3.2.19 |
| `SCALATEST_JUNIT` | 0.1.11 |
| `SCOVERAGE` | 2.1.1 |

## Navigation rules
- **Always skip** `build/`, `.gradle/`, `.idea/` when exploring — they are generated artifacts
- Tests use `AnyFunSuite with Matchers` (ScalaTest), not JUnit `@Test`
- No Quarkus in current modules — Quarkus is planned for future services
- Agent workflow: architect → scala-implementer → test-writer → gradle-builder → code-reviewer
