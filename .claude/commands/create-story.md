# Create User Story in YouTrack

Automated user-story creation workflow. Topic/hint: `$ARGUMENTS`

## Step 1 — Gather Context

Use `AskUserQuestion` tool to ask the user (max 4 questions at once):

1. **Domain** — Is this frontend (UI/UX) or backend/coordinator/systems work?
2. **User type** — Who is the actor? (e.g. player, admin, bot, system)
3. **Action** — What should the user be able to do?
4. **Goal/value** — Why? What outcome does it enable?

If `$ARGUMENTS` already answers some of these, skip those questions.

## Step 2 — Research (if needed)

If the topic involves unfamiliar domain logic, game rules, or technical constraints:
- Search the repo for relevant code (use `Grep`/`Bash` to find related files).
- Use `WebSearch` if the topic involves external standards or protocols.
- Do NOT guess. Surface findings before drafting.

## Step 3 — Draft Story

Compose the full story using this template:

```
As a [type of user]
I want to [perform an action]
So that [achieve a goal or value]


Description

[Additional context or business logic for this story.]


Acceptance Criteria

[List the specific, measurable criteria that define when this story is done:]

- Criterion 1
- Criterion 2
- Criterion 3


Implementation Notes

[Technical notes, design references, or constraints.]
```

Rules:
- User story line: plain English, present tense, from user's perspective.
- Acceptance criteria: testable, unambiguous, one condition each.
- Implementation notes: optional — only include if there are known constraints, related tickets, or design refs.

## Step 4 — Clarify Acceptance Criteria

Show the draft to the user.
**Use `AskUserQuestion` tool to ask:**
- Are the acceptance criteria complete and correct?
- Any implementation constraints to add?
- Priority (if known)?

Incorporate feedback. Repeat until user approves.

## Step 5 — Determine Project

- Frontend / UI / UX → project: `NCWF`
- Backend / coordinator / systems / bot / engine → project: `NCS`

If ambiguous, ask the user.

## Step 6 — Create Issue

Call `mcp__youtrack__create_issue` with:
- `project`: determined in Step 5
- `summary`: concise title derived from the "I want to" clause (≤72 chars, sentence case)
- `description`: full formatted story from Step 3 (Markdown)
- `type`: `Feature` (or `Task` if purely technical with no user-facing value)

## Step 7 — Report

Display the created issue ID and URL.
Ask if a linked sub-task or implementation ticket is needed.
