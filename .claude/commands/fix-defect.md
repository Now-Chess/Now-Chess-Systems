# Fix Defect from YouTrack

Automated defect-fix workflow. Ticket ID: `$ARGUMENTS`

## Step 1 — Fetch Ticket

Call `mcp__youtrack__get_issue` with ID `$ARGUMENTS`.
Extract and display: summary, description, steps to reproduce, Priority, Subsystem.

## Step 2 — Create Worktree

Derive branch name from ticket:
- `type` from YouTrack issue type: `bug` → `fix`, `feature`/`task` → `feat`, `refactor` → `refactor`, else `chore`
- `scope` from affected module/component (kebab-case, omit if unclear)
- `description` from ticket summary: lowercase, kebab-case, max 40 chars, drop articles

Branch format: `<type>/<ticket-id>-<description>`
Example: `fix/NOW-123-castling-validation-failure`

Call `EnterWorktree` with that branch name.
All subsequent file work happens inside this worktree.

## Step 3 — Identify Root Cause (read-only)

1. Run `./compile` — capture all errors and warnings.
2. Run `./test` — capture all failures.
3. Spawn `cavecrew-investigator` with: ticket description + compile/test output → locate root cause (files, line numbers, what's wrong).
4. **If anything is ambiguous (reproduction unclear, scope uncertain, conflicting signals), use `AskUserQuestion` tool to ask — max 4 questions at once.**
5. **Report findings to user. No file writes yet. Wait for acknowledgement before continuing.**

## Step 3b — Complexity Assessment + Subtasks

After root cause confirmed, assess scope:

**Simple** (1–2 files, single concern, < 1 hour estimated): proceed directly to Step 4.

**Complex** (3+ files, multiple concerns, or estimated > 1 hour): create subtasks before coding.

To create subtasks:
1. Break fix into discrete, independently-completable tasks (e.g. "Fix validation in RuleSet", "Add regression test for castling edge case", "Update FenParser to handle X").
2. For each subtask call `mcp__youtrack__create_issue` with:
   - `project`: same project as parent ticket
   - `summary`: concise action-oriented title
   - `type`: `Task`
   - `description`: what to do and why
3. Call `mcp__youtrack__link_issues` to link each subtask to `$ARGUMENTS` with relation `subtask of`.
4. List created subtask IDs to user.

Then proceed to Step 4, implementing subtasks in order.

## Step 4 — Fix

1. Implement fix (use `scala-implementer` agent for non-trivial changes; inline edits for small ones).
2. Run `./compile` — must be green.
3. Run `./test` — must be green.
4. Run `./lint` — must be green.
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

- `type`: same as branch type (`fix`, `feat`, `refactor`, `chore`, etc.)
- `scope`: affected module (`core`, `rule`, `api`, `bot`, `io`)
- Subject: imperative mood, no period, lowercase
- Footer `Closes $ARGUMENTS` and ticket URL always present

Push branch to remote.

## Step 7 — Comment on Ticket

After successful push, call `mcp__youtrack__add_issue_comment` on `$ARGUMENTS` with:

```
Branch `<branch-name>` pushed.

<one-sentence summary of what was changed and why>

Files changed:
- <file1>
- <file2>
```

## Step 8 — Cleanup

Call `ExitWorktree` to delete the worktree.
Report: branch pushed, ticket commented, worktree deleted, done.
