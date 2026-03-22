---
name: contract-first-test-writing
description: Use when the architect has produced an OpenAPI contract but scala-implementer has not yet written any source code - write failing tests from the contract so implementation has a target to satisfy
---

# Contract-First Test Writing (TDD Red Phase)

## Overview

Write tests from the API contract **before** any implementation exists. Tests will fail — that is correct and expected. The scala-implementer's job is to make them green.

**Iron Law:** Never look at `src/main/scala`. If it exists, ignore it. Derive every assertion from `docs/api/{service}.yaml` and the relevant ADR in `docs/adr/`.

## Workflow

### 1. Read the contract

```
docs/api/{service-name}.yaml   ← OpenAPI spec (required)
docs/adr/                      ← ADRs for domain rules and data shapes
```

Extract for each endpoint:
- HTTP method + path
- Request body shape and required fields
- Response status codes and body shape
- Error cases (4xx, 5xx) documented in the spec

### 2. Write `@QuarkusTest` integration tests (one per endpoint)

Cover for every endpoint:

| Scenario | What to assert |
|----------|---------------|
| Happy path | Correct 2xx status + response body shape |
| Missing required field | 400 response |
| Invalid input | 400 or 422 response |
| Not found | 404 response (where applicable) |
| Error contract | Response body matches error schema |

```scala
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test
import jakarta.ws.rs.core.MediaType

@QuarkusTest
class MoveEndpointTest:

  @Test
  def validMove_returns200(): Unit =
    given()
      .contentType(MediaType.APPLICATION_JSON)
      .body("""{"from":"e2","to":"e4"}""")
    .when()
      .post("/api/moves")
    .`then`()
      .statusCode(200)

  @Test
  def missingField_returns400(): Unit =
    given()
      .contentType(MediaType.APPLICATION_JSON)
      .body("""{"from":"e2"}""")
    .when()
      .post("/api/moves")
    .`then`()
      .statusCode(400)
```

### 3. Write unit tests for domain rules

For every domain invariant described in the ADR (validation rules, state machines, error conditions), write a ScalaTest unit test:

```scala
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitSuiteLike

class MoveValidatorTest extends AnyFunSuite with Matchers with JUnitSuiteLike:

  test("invalid square is rejected") {
    val result = MoveValidator.validate("z9", "e4")
    assert(result.isLeft)
  }
```

### 4. Confirm tests compile but fail

```bash
./gradlew :modules:{service-name}:test
```

Expected outcome: **compilation succeeds, tests fail** (no implementation yet).

If compilation fails, fix the test code — do not create implementation code.

If tests somehow pass, the contract is already implemented; notify the team-lead.

### 5. Hand off to scala-implementer

Leave a comment at the top of the primary test file:

```scala
// RED: These tests define the contract for {service-name}.
// scala-implementer: make them green without modifying test assertions.
```

## Rules

- **No peeking at `src/main/scala`** — tests must be derived from the contract only.
- Use `@QuarkusTest` + REST Assured for HTTP endpoints — `@Test` methods must be explicitly typed `: Unit`.
- Use `AnyFunSuite with Matchers with JUnitSuiteLike` for pure domain logic unit tests — no `@Test`, no `: Unit` needed.
- Do not mock the implementation — tests call real endpoints, real domain code.
- Do not write happy-path-only tests; every documented error case needs a test.

## After Implementation: Coverage Check

Once scala-implementer is done and tests are green, run the coverage reporter to find any gaps the contract tests missed:

```bash
python3 jacoco-reporter/jacoco_coverage_gaps.py \
  modules/{service-name}/build/reports/jacoco/test/jacocoTestReport.xml \
  --output agent
```

Use the `jacoco-coverage-gaps` skill to close remaining gaps.
