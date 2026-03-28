# CLAUDE.md — NowChessSystems

## Stack
Scala 3.5.x · Quarkus + quarkus-scala3 · Hibernate/Jakarta · Lanterna TUI · K8s + ArgoCD + Kargo · Frontend TBD (Vite/React/Angular/Vue)

### Memory

Your memory is saved under .claude/memory/MEMORY.md.

## Structure
```
build.gradle.kts / settings.gradle.kts   # root; include(":modules:<svc>") per service
modules/<svc>/build.gradle.kts + src/
docs/adr/  docs/api/  docs/unresolved.md
```
Versions in root `extra["VERSIONS"]`; modules read via `rootProject.extra["VERSIONS"] as Map<String,String>`.

## Commands
```bash
./gradlew build
./gradlew :modules:<svc>:build|test
./gradlew :modules:<svc>:test --tests "de.nowchess.<svc>.<Class>"
```

## Workflow
1. **Plan** — restate requirement, list files, flag risks. Proceed unless genuine ambiguity.
2. **Tests first** — cover only new behaviour.
3. **Implement** — no scope creep.
4. **Verify** — check each requirement; confirm green build.

## Scala/Quarkus Rules
- `given`/`using` only (no `implicit`); `Option`/`Either`/`Try` (no `null`/`.get`)
- `jakarta.*` only; reactive I/O (`Uni`/`Multi`), no blocking on event loop
- Always exclude `org.scala-lang:scala-library` from Quarkus BOM
- Unit tests: `extends AnyFunSuite with Matchers` — no `@Test`, no `: Unit`
- Integration tests: `@QuarkusTest` + JUnit 5 — `@Test` methods need explicit `: Unit`

## Coverage
Line ≥ 95% · Branch ≥ 90% · Method ≥ 90% (document exceptions)
Check: `jacoco-reporter/scoverage_coverage_gaps.py modules/{svc}/build/reports/scoverageTest/scoverage.xml`
⚠️ Use `scoverageTest/`, NOT `scoverage/`.

## Bug Fixing
Fix failures immediately without asking. After 3 failed attempts → log in `docs/unresolved.md` + surface summary.

## Agents (new service)
Sequential: architect → scala-implementer → test-writer → gradle-builder → code-reviewer (review only, no self-fix)
Parallel: only when services are fully independent (no shared contracts/state).

## Unresolved (`docs/unresolved.md`)
Append only, never delete:
```
## [YYYY-MM-DD] <title>
**Requirement/Bug:** **Root Cause:** **Attempted Fixes:** **Next Step:**
```

## Done Checklist
- [ ] Plan written · files created/modified · tests green · requirements verified · unresolved logged
