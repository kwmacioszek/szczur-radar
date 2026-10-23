# Follow-ups from the implementation review (2026-10-23)

Source: `reviews/impl-review.md`. Fixes F1–F6 landed on `feature/attempt-accounting`; only the note below
is left for later work.

## F7 — No breaker for a series of unclassified errors (for S-17 `pipeline-without-embabel`)

- **What:** a systematic error every call ends with, outside `LlmCallException` (e.g. an
  `IllegalStateException` from `TransportException.unclassified` after an SDK change), goes through every
  post of a run: each post counts one technical failure per run and is on hold after three runs. Calls that
  reach the provider before failing may be paid.
- **Why not in S-09:** the plan's goal was outages and the post's fault; the limit per post
  (`Post.MAX_TECHNICAL_FAILURES`) already bounds the damage, and before S-09 such posts went `dead`.
- **For S-17:** when the run's attempt semantics move out of Embabel, decide whether N unclassified errors
  in a row of the LLM client (not of mappers or writes) stop the step like `OUTAGE_STREAK`.
