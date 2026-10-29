# Review follow-ups — run-attention-single-rule

Source: `reviews/impl-review.md` (2026-10-28).

## F1: the tone and status label still read the skipped counter

- **Where**: `szczur-radar-web/src/runs.ts`:
  - `runTone`: a `succeeded` run with `skipped > 0` gets `warning`;
  - `runStatusLabel` / `hasSkipped`: the "pominięte: N" suffix.
- **What's left**: S-22 removed the second copy of the "wymaga uwagi" rule from the reason texts. The SPA still works out the "skipped" part of the rule from the counter in two places: the run's tone and its status label. This was a decision of the S-22 plan: tone and status text stay in the SPA, and the bell's payload (`NotifiedRun`) carries no codes. It is harmless today, because the tone agrees with the rule.
- **Follow-up candidate**:
  - S-24 (`api-contract-guard`), as a "rule repeated on both sides";
  - or a later change that gives `NotifiedRun` the `attentionReasons` codes, so the tone can follow the codes everywhere.
- **To do when the PR opens**: a comment in issue S-22 naming this remaining copy (an outward action, not done in the review).
