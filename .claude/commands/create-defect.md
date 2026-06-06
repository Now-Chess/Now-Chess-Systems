# Create Defect in YouTrack

Automated defect creation workflow. Topic/hint: `$ARGUMENTS`

## Step 1 — Gather Context

Use `AskUserQuestion` tool to ask the user (max 4 questions at once):

1. **Component** — Where does the bug occur? (e.g. move generation, FEN parsing, castling, UI, API)
2. **What breaks** — What is the actual (broken) behavior?
3. **Expected** — What should happen instead?
4. **Reproducibility** — Is it always reproducible? Any known trigger conditions?

If `$ARGUMENTS` already answers some of these, skip those questions.

## Step 2 — Research (if needed)

If the bug involves domain logic or rules:
- Search repo for relevant code (`Grep`/`Bash`).
- Check test files for existing coverage of the broken area.
- Do NOT guess at root cause. Surface findings before drafting.

## Step 3 — Draft Defect

Compose the full defect report using this template:

```
Summary

[One-sentence description of what is broken.]


Steps to Reproduce

1. Step one
2. Step two
3. Step three


Expected Behavior

[What should happen.]


Actual Behavior

[What actually happens.]


Environment / Notes

[Any relevant context: FEN positions, game conditions, config, browser, OS — only if applicable.]
```

Rules:
- Steps must be minimal and reproducible.
- Expected vs actual: concrete and unambiguous.
- Omit "Environment / Notes" section if not relevant.

## Step 4 — Clarify

Show the draft to the user.
**Use `AskUserQuestion` tool to ask:**
- Are steps to reproduce complete and accurate?
- Severity: Blocker / Critical / Major / Minor / Trivial?
- Any related tickets or recent changes to link?

Incorporate feedback. Repeat until user approves.

## Step 5 — Determine Project

- Frontend / UI / UX → project: `NCWF`
- Backend / coordinator / systems / bot / engine → project: `NCS`

If ambiguous, ask the user.

## Step 6 — Create Issue

Call `mcp__youtrack__create_issue` with:
- `project`: determined in Step 5
- `summary`: concise title describing what is broken (≤72 chars, sentence case)
- `description`: full formatted defect report from Step 3 (Markdown)
- `type`: `Bug`

## Step 7 — Report

Display the created issue ID and URL.
Ask if a linked investigation or fix task is needed.
