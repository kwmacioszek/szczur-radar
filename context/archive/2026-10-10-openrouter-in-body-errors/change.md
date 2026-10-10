---
change_id: openrouter-in-body-errors
title: Openrouter in body errors
status: archived
created: 2026-10-10
updated: 2026-10-10
archived_at: 2026-10-10T13:30:39Z
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- Issue #39, PR #40. Worked in the worktree `.claude/worktrees/openrouter-in-body-errors` (UI work runs in parallel in the main checkout).
- Phase 1, outside the plan: `PipelineAgentIT.aStoppingInstanceInterruptsItsRunAndPutsItsPostsBack` (added in the S-02 review, already on `main`) picked any assessed post; a post decided by the prefilter never calls the LLM, so the test waited 30 s and failed at random. It now picks a post assessed by the model, like the leftover test.
- Implementation review triage F1 (2026-10-10): the user requested fixing `choices[0].error` in this worktree. The client classifies errors after partial output before accepting the result; regression tests and package guidance were added. See `reviews/impl-review.md` and `follow-ups/review-fixes.md`.
