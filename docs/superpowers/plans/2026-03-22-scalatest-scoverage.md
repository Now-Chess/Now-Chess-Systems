# ScalaTest + Scoverage Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace JaCoCo with Scoverage and add ScalaTest (with its JUnit 5 bridge) as the test library across all modules.

**Architecture:** Three build files are modified — the root for shared dependency versions, and each module for plugins, dependencies, and task wiring. No source files are created. The Scoverage Gradle plugin is applied per-module with its version hardcoded inline (Gradle resolves `plugins {}` before `rootProject.extra` is available).

**Tech Stack:** Scala 3, Gradle (Kotlin DSL), ScalaTest 3.2.19, scalatestplus-junit-5-11 3.2.19.1, Scoverage Gradle plugin 8.1.

---

## File Map

| File | Change |
|---|---|
| `build.gradle.kts` (root) | Add `SCALATEST` and `SCALATESTPLUS_JUNIT5` version entries |
| `modules/core/build.gradle.kts` | Replace `jacoco` with `org.scoverage`; swap JUnit deps for ScalaTest; merge two `tasks.test {}` blocks |
| `modules/api/build.gradle.kts` | Same as core; also add missing `useJUnitPlatform()` |

---

### Task 1: Add ScalaTest version entries to root build

**Files:**
- Modify: `build.gradle.kts` (root)

- [ ] **Step 1: Add version entries**

Open `build.gradle.kts` at the root. The `versions` map currently looks like:

```kotlin
val versions = mapOf(
    "QUARKUS_SCALA3" to "1.0.0",
    "SCALA3"         to "3.5.1",
    "SCALA_LIBRARY"  to "2.13.18"
)
```

Add two entries so it becomes:

```kotlin
val versions = mapOf(
    "QUARKUS_SCALA3"        to "1.0.0",
    "SCALA3"                to "3.5.1",
    "SCALA_LIBRARY"         to "2.13.18",
    "SCALATEST"             to "3.2.19",
    "SCALATESTPLUS_JUNIT5"  to "3.2.19.1"
)
```

- [ ] **Step 2: Verify the root build file parses**

```bash
./gradlew help --quiet
```

Expected: exits 0 with no errors.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add ScalaTest version entries to root versions map"
```

---

### Task 2: Migrate `modules/core` to ScalaTest + Scoverage

**Files:**
- Modify: `modules/core/build.gradle.kts`

- [ ] **Step 1: Replace the `jacoco` plugin with `org.scoverage`**

In the `plugins {}` block, replace:
```kotlin
jacoco
```
with:
```kotlin
id("org.scoverage") version "8.1"
```

The full plugins block should be:
```kotlin
plugins {
    id("scala")
    id("org.scoverage") version "8.1"
    application
}
```

- [ ] **Step 2: Swap JUnit dependencies for ScalaTest**

In the `dependencies {}` block, remove:
```kotlin
testImplementation(platform("org.junit:junit-bom:5.10.0"))
testImplementation("org.junit.jupiter:junit-jupiter")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

Add in their place:
```kotlin
testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
testImplementation("org.scalatestplus:junit-5-11_3:${versions["SCALATESTPLUS_JUNIT5"]!!}")
```

- [ ] **Step 3: Merge the two `tasks.test {}` blocks and replace jacoco wiring**

The file currently has two separate `tasks.test {}` blocks and a `tasks.jacocoTestReport {}` block. Delete all three. Add the following single merged block **after** the `dependencies {}` block:

```kotlin
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.reportScoverage)
}
tasks.reportScoverage {
    dependsOn(tasks.test)
}
```

- [ ] **Step 4: Run the tests**

```bash
./gradlew :modules:core:test
```

Expected: BUILD SUCCESSFUL. (Zero tests is fine — there are no test files yet. The build must not fail with dependency resolution or plugin errors.)

- [ ] **Step 5: Run the coverage report**

```bash
./gradlew :modules:core:reportScoverage
```

Expected: BUILD SUCCESSFUL. A report is generated under `modules/core/build/reports/scoverage/`.

- [ ] **Step 6: Commit**

```bash
git add modules/core/build.gradle.kts
git commit -m "build(core): replace JaCoCo with Scoverage, add ScalaTest dependencies"
```

---

### Task 3: Migrate `modules/api` to ScalaTest + Scoverage

**Files:**
- Modify: `modules/api/build.gradle.kts`

- [ ] **Step 1: Replace the `jacoco` plugin with `org.scoverage`**

In the `plugins {}` block, replace:
```kotlin
jacoco
```
with:
```kotlin
id("org.scoverage") version "8.1"
```

The full plugins block should be:
```kotlin
plugins {
    id("scala")
    id("org.scoverage") version "8.1"
}
```

- [ ] **Step 2: Swap JUnit dependencies for ScalaTest**

In the `dependencies {}` block, remove:
```kotlin
testImplementation(platform("org.junit:junit-bom:5.10.0"))
testImplementation("org.junit.jupiter:junit-jupiter")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

Add in their place:
```kotlin
testImplementation("org.scalatest:scalatest_3:${versions["SCALATEST"]!!}")
testImplementation("org.scalatestplus:junit-5-11_3:${versions["SCALATESTPLUS_JUNIT5"]!!}")
```

- [ ] **Step 3: Merge the two `tasks.test {}` blocks and replace jacoco wiring**

The `modules/api` file also has two `tasks.test {}` blocks and a `jacocoTestReport` block. Delete all three. Add the following merged block **after** the `dependencies {}` block:

```kotlin
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.reportScoverage)
}
tasks.reportScoverage {
    dependsOn(tasks.test)
}
```

> Note: `modules/api` did not previously have `useJUnitPlatform()` — it is being **added** here, not preserved.

- [ ] **Step 4: Run the tests**

```bash
./gradlew :modules:api:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the coverage report**

```bash
./gradlew :modules:api:reportScoverage
```

Expected: BUILD SUCCESSFUL. A report is generated under `modules/api/build/reports/scoverage/`.

- [ ] **Step 6: Commit**

```bash
git add modules/api/build.gradle.kts
git commit -m "build(api): replace JaCoCo with Scoverage, add ScalaTest dependencies"
```

---

### Task 4: Full build verification

- [ ] **Step 1: Run the full build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL with no errors across all modules.

- [ ] **Step 2: Confirm no JaCoCo references remain**

```bash
grep -r "jacoco\|jacocoTestReport" --include="*.kts" .
```

Expected: no output (zero matches).
