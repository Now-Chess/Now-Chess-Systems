# Split Story into Subtasks in YouTrack

Split a user story into smaller, implementable subtasks. Story ID: `$ARGUMENTS`

## Step 1 — Fetch Story

Call `mcp__youtrack__get_issue` with ID `$ARGUMENTS`.
Extract and display: summary, description, acceptance criteria, implementation notes.

## Step 2 — Research (if needed)

If the story involves unfamiliar domain logic or technical constraints:
- Search repo for relevant code (`Grep`/`Bash`).
- Use `WebSearch` for external standards or protocols.
- Do NOT guess. Surface findings before proposing splits.

## Step 3 — Propose Split

Analyse the story and propose a set of subtasks. Rules:
- Each subtask = one unit of work, completable independently or in sequence.
- No subtask should exceed ~2 days of work.
- Name subtasks in imperative mood (e.g. "Implement move validation endpoint").
- Cover the full scope of the parent story — no gaps.

Show proposed subtask list to user (titles only) and ask:
**Use `AskUserQuestion` tool:**
- Does the split look right?
- Any subtasks to add, remove, or merge?
- Should any subtask be assigned to a specific person?

Incorporate feedback. Repeat until user approves the list.

## Step 4 — Draft Each Subtask

For each approved subtask, compose description using this template:

```
[Brief description of what needs to be done for this subtask.]


Steps / Tasks

- Task 1
- Task 2
- Task 3


Definition of Done

What must be true for this subtask to be considered complete:

- Code implemented
- Tests passed
- Reviewed and merged
```

Rules:
- Steps/Tasks: concrete, ordered where order matters.
- Definition of Done: adjust per subtask — not all subtasks need the same criteria (e.g. a research spike has different DoD than an implementation task).
- Keep description short — one paragraph max.

## Step 5 — Determine Project per Subtask

Assign each subtask's project based on its content — do **not** inherit blindly from parent:

- Backend code (game engine, bots, API, services, coordinator) → `NCS`
- Frontend code (UI, UX, web app) → `NCWF`
- Kubernetes, pipelines, CI/CD, DB setup, infrastructure → `NCI`

If a subtask's project is ambiguous, ask the user before creating it.

## Step 6 — Create Subtasks

For each subtask call `mcp__youtrack__create_issue` with:
- `project`: from Step 5
- `summary`: subtask title (≤72 chars, sentence case)
- `description`: full formatted description from Step 4 (Markdown)
- `type`: `Task`

Then call `mcp__youtrack__link_issues` to link each created subtask to `$ARGUMENTS` with relation `subtask of`.

## Step 6b — Inter-Subtask Links

If subtasks must be done in sequence (one depends on output of another), add ordering links:
- For each dependency pair call `mcp__youtrack__link_issues` with relation `is blocked by` (subtask B is blocked by subtask A).

Ask the user to confirm sequencing before adding these links:

> Do any subtasks have ordering dependencies? (e.g. "Implement X must come before Add tests for X")

## Step 6c — External Links

Scan `$ARGUMENTS` description and implementation notes for any referenced issue IDs not already linked. For each:

| Situation | Relation |
|-----------|---------|
| Parent story blocks another epic/story | `blocks` |
| Story depends on another epic completing | `is blocked by` |
| Related story in same domain | `relates to` |
| This story duplicates or supersedes | `duplicates` |

Suggest links to the user and call `mcp__youtrack__link_issues` on confirmation.

## Step 7 — Report

List all created subtask IDs and summaries.
List all links created (subtask-of, blocking chains, external).
Display parent story link.
Ask if any subtask needs further splitting.
