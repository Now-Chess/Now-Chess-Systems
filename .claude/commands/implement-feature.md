# Implement Feature from YouTrack

Automated feature-implementation workflow. Ticket ID: `$ARGUMENTS`

## Step 1 — Fetch Ticket

Call `mcp__youtrack__get_issue` with ID `$ARGUMENTS`.
Extract and display: summary, description, acceptance criteria, Priority, Subsystem.

## Step 2 — Create Worktree

Derive branch name from ticket:
- `type` from YouTrack issue type: `feature`/`task` → `feat`, `refactor` → `refactor`, `bug` → `fix`, else `chore`
- `scope` from affected module/component (kebab-case, omit if unclear)
- `description` from ticket summary: lowercase, kebab-case, max 40 chars, drop articles

Branch format: `<type>/<ticket-id>-<description>`
Example: `feat/NOW-456-add-bot-difficulty-slider`

Call `EnterWorktree` with that branch name.
All subsequent file work happens inside this worktree.

## Step 3 — Understand Requirements (read-only)

1. Run `./compile` — confirm baseline is green.
2. Run `./test` — confirm baseline is green.
3. Spawn `cavecrew-investigator` with: ticket description + acceptance criteria → locate affected files, relevant types/interfaces, entry points, integration touch-points.
4. **If anything is ambiguous (scope unclear, acceptance criteria missing, design decisions needed), use `AskUserQuestion` tool to ask — max 4 questions at once.**
5. **Report plan to user: what will be added/changed, which files, which modules. No file writes yet. Wait for acknowledgement before continuing.**

## Step 4 — Implement

1. Implement feature (use `scala-implementer` agent for non-trivial changes; inline edits for small ones).
2. Run `./compile` — must be green.
3. Run `./test` — must be green (add new tests for new behaviour; do not modify existing tests unless requirements changed).
4. Run `./gradlew spotlessScalaApply` — **blocking, foreground only**. Wait for completion before continuing.
5. Run `./lint` — **blocking, foreground only** (never `run_in_background`). Wait for exit code 0. Must be green.
   - If lint fails, fix all issues and re-run until exit code 0.
   - **Do NOT proceed to Step 5 until `./lint` has completed and returned exit code 0.**
If any step fails, iterate until all pass.

## Step 5 — Review

Spawn `cavecrew-reviewer` on the full diff.
Display findings grouped by severity.

## Step 6 — Confirm + Push

Show summary: ticket, branch, files changed, review findings.
**Use `AskUserQuestion` tool to ask for explicit approval before pushing.** Include any open questions about commit message scope or body if unclear.

On approval, commit following Conventional Commits:

```
<type>(<scope>): <short description, imperative, ≤50 chars>

<optional body: what changed and why, wrap at 72 chars>

Closes $ARGUMENTS
https://knockoutwhist.youtrack.cloud/issue/$ARGUMENTS
```

- `type`: same as branch type (`feat`, `refactor`, `chore`, etc.)
- `scope`: affected module (`core`, `rule`, `api`, `bot`, `io`)
- Subject: imperative mood, no period, lowercase
- Footer `Closes $ARGUMENTS` and ticket URL always present

Push branch to remote.

## Step 7 — Comment on Ticket

After successful push, call `mcp__youtrack__add_issue_comment` on `$ARGUMENTS` with:

```
Branch `<branch-name>` pushed.

<one-sentence summary of what was added and why>

Files changed:
- <file1>
- <file2>
```

## Step 8 — Cleanup

Call `ExitWorktree` with `discard_changes: true` to delete the worktree.
(Branch was pushed in step 6 — commits are safe on remote; `discard_changes: true` bypasses the local-ahead guard.)
Report: branch pushed, ticket commented, worktree deleted, done.
