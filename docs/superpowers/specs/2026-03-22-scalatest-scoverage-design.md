# Design: Add ScalaTest + Replace JaCoCo with Scoverage

**Date:** 2026-03-22
**Status:** Approved

## Summary

Replace the current JUnit-only test setup and JaCoCo coverage with ScalaTest (via its JUnit 5 bridge) and Scoverage across both `modules/core` and `modules/api`.

## Motivation

- The CLAUDE.md working agreement prescribes `AnyFunSuite with Matchers with JUnitSuiteLike` as the unit test style, which requires ScalaTest.
- Scoverage is the standard Scala code coverage tool and understands Scala semantics; JaCoCo's JVM bytecode instrumentation is less accurate for Scala code.

## Scope

Two modules are affected: `modules/core` and `modules/api`. The root `build.gradle.kts` is updated for shared dependency versions only.

## Changes

### Root `build.gradle.kts`

Add to the `versions` map (dependency versions only — plugin version is hardcoded per module, see note below):
- `SCALATEST` → `3.2.19`
- `SCALATESTPLUS_JUNIT5` → `3.2.19.1`

> **Note on plugin versioning:** Gradle resolves the `plugins {}` block before `rootProject.extra` is available, so the Scoverage plugin version (`8.1`) must be declared inline in each module's `plugins {}` block. It cannot be read from the root versions map.

### `modules/core/build.gradle.kts` and `modules/api/build.gradle.kts`

Both modules require the same set of changes. Both currently have **two separate `tasks.test {}` blocks** that must be merged into one.

**Plugins block:**
- Remove `jacoco`
- Add `id("org.scoverage") version "8.1"`

**Dependencies block:**
- Remove `testImplementation(platform("org.junit:junit-bom:5.10.0"))`
- Remove `testImplementation("org.junit.jupiter:junit-jupiter")`
- Remove `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`
- Add `testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")`
- Add `testImplementation("org.scalatestplus:junit-5-11_3:${versions["SCALATESTPLUS_JUNIT5"]!!}")`

**Task wiring — merge both `tasks.test {}` blocks into one and replace jacoco wiring:**

Both `modules/core` and `modules/api` currently have two `tasks.test {}` blocks. Delete both and replace with the following single merged block placed **after** the `dependencies {}` block (conventional position):

```kotlin
tasks.test {
    useJUnitPlatform()   // required — scalatestplus JUnit 5 bridge relies on this
    finalizedBy(tasks.reportScoverage)
}
tasks.reportScoverage {
    dependsOn(tasks.test)
}
```

> Note: `modules/api` does not currently have `useJUnitPlatform()` — it must be **added** (not just kept) in the merged block.

Remove the `jacocoTestReport` task block entirely from both modules.

**Task name confirmation:** The Scoverage Gradle plugin 8.1 registers `reportScoverage` as the HTML report task.

## Versions

| Artifact | Version | Notes |
|---|---|---|
| `org.scalatest:scalatest_3` | 3.2.19 | Core ScalaTest for Scala 3 |
| `org.scalatestplus:junit-5-11_3` | 3.2.19.1 | JUnit 5.11 runner bridge; `.1` = build 1 |
| Scoverage Gradle plugin | 8.1 | Hardcoded inline in `plugins {}` block |

## Testing the Change

After applying:
1. `./gradlew :modules:core:test` and `./gradlew :modules:api:test` must pass (green, even with zero test files).
2. `./gradlew :modules:core:reportScoverage` must produce a coverage report.
3. `./gradlew build` must be fully green.

## Files Modified

- `build.gradle.kts` (root) — add two version entries
- `modules/core/build.gradle.kts` — plugin, deps, merge two `tasks.test` blocks, replace jacoco wiring
- `modules/api/build.gradle.kts` — plugin, deps, merge two `tasks.test` blocks, add `useJUnitPlatform()`, replace jacoco wiring

No new source files are created.
