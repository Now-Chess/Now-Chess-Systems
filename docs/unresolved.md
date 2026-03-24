# Unresolved Issues

## [2026-03-24] JUnitSuiteLike mixin not available for ScalaTest 3.2.19 with Scala 3

**Requirement / Bug:**
CLAUDE.md prescribes that all unit tests should extend `AnyFunSuite with Matchers with JUnitSuiteLike`. However, the `JUnitSuiteLike` trait cannot be resolved in the current build configuration.

**Root Cause (if known):**
- ScalaTest 3.2.19 for Scala 3 does not provide `JUnitSuiteLike` in any public package.
- The `co.helmethair:scalatest-junit-runner:0.1.11` dependency does not expose this trait.
- There is no `org.scalatest:scalatest-junit_3` artifact available for version 3.2.19.
- The trait may have been removed or changed in the ScalaTest 3.x → Scala 3 migration.

**Attempted Fixes:**
1. Tried importing from `org.scalatest.junit.JUnitSuiteLike` — not found
2. Tried importing from `org.scalatestplus.junit.JUnitSuiteLike` — not found
3. Tried importing from `co.helmethair.scalatest.junit.JUnitSuiteLike` — not found
4. Attempted to add `org.scalatest:scalatest-junit_3:3.2.19` dependency — artifact does not exist in Maven Central

**Suggested Next Step:**
1. Either find the correct ScalaTest artifact/import for Scala 3 JUnit integration, or
2. Update CLAUDE.md to reflect the actual constraint that unit tests should extend `AnyFunSuite with Matchers` (without `JUnitSuiteLike`), or
3. Investigate whether a different test runner or configuration is needed to achieve JUnit integration with ScalaTest 3 in Scala 3
