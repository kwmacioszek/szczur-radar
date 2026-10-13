# Review follow-ups — cli-user-onboarding

Items queued by `/10x-impl-review` triage that are not code fixes of the reviewed change.

## Full-plan review (2026-10-12, `reviews/impl-review.md`)

- [ ] **F4 → a separate change (duration settings).** `ActivationSettings.linkTtl` and `LlmProviderProperties.timeout` parse a duration setting the same way: environment value → `DurationStyle.detectAndParse` → a `problems` list. Each also has its own copy of `trimmed`.
  - The rules differ. `ACTIVATION_LINK_TTL` refuses a bare number, but `LLM_TIMEOUT=120` is still accepted as 120 ms (the pitfall in `deploy-plan.md`, F-02 and "Rule for variables").
  - Plan: one shared parser (e.g. `config/DurationSetting`) that requires a unit and checks min/max, used by both settings.
  - Before merging that change, read production's `LLM_TIMEOUT` (`railway variable list -s szczur-radar-api -e production`). A bare number there would make `migrate` and the start fail, and the deploy would stop.
  - User's decision 2026-10-12: not in S-07.
