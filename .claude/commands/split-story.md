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

## Step 5 — Determine Project

Inherit project from parent story (`$ARGUMENTS`):
- If parent is `NCWF-*` → subtasks go in `NCWF`
- If parent is `NCS-*` → subtasks go in `NCS`

## Step 6 — Create Subtasks

For each subtask call `mcp__youtrack__create_issue` with:
- `project`: from Step 5
- `summary`: subtask title (≤72 chars, sentence case)
- `description`: full formatted description from Step 4 (Markdown)
- `type`: `Task`

Then call `mcp__youtrack__link_issues` to link each created subtask to `$ARGUMENTS` with relation `subtask of`.

## Step 7 — Report

List all created subtask IDs and summaries.
Display parent story link.
Ask if any subtask needs further splitting.
