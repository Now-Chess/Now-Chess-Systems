---
name: keep-structure-memory-updated
description: Always update the project structure memory files when adding, removing, or changing source files
type: feedback
---

After any change that adds, removes, renames, or significantly alters a source file, update the relevant structure memory file:

- New/renamed/deleted file in `modules/api` → update `project_structure_api.md`
- New/renamed/deleted file in `modules/core` → update `project_structure_core.md`
- New module, dependency version change, or new top-level directory → update `project_structure_root.md`
- New module added → create a new `project_structure_<module>.md` and add it to `MEMORY.md`

**Why:** Structure memories are the primary navigation aid. Stale entries cause wasted exploration.

**How to apply:** Treat the structure memory update as part of completing any implementation task — do it in the same session, not as a follow-up.
