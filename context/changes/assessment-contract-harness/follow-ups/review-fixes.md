# Review follow-ups — assessment-contract-harness

Items queued by `/10x-impl-review` triage that are not code fixes of the reviewed phase.

## Phase 5 review (2026-10-04, `reviews/impl-review-phase-5.md`)

- [x] **F4 → phase 6 report:** give the target metrics (recall, precision, direction accuracy,
  dominant direction, contradictions) with and without the 7 gold targets covered by contract §6.5a,
  written after labeling the same posts: 10-01-005 „krach”, 10-01-014 „dzien kibla”, 10-01-023
  „pompa”, 10-01-024 „KRACHU”, 10-02-005 „spadki”, 10-02-043 „Pompujemy”, 10-02-057 „bessa”.
- [x] **F7 → phase 6 report:** metric legend as in `measurement/README.md` („Metric definitions”);
  direction accuracy counts an unmatched gold target as wrong (user's decision).
- [ ] **F5 → PR description:** „`sources/wykop` (`WykopApiClient`, `WykopRepository`) is ready but
  not wired (no bean, route or schedule) — S-02 builds on it.” Comment the same on the S-02 issue
  when the PR is opened.
- [x] **F9 → before merge:** remove the temporary links feature from `pilot/labeling-gold.html`
  („Wczytaj linki”, the „otwórz na Wykopie” link, the `links` key in localStorage) and delete the local
  `~/.config/szczur-radar/gold-links.json` once labeling is final (user: „to powinno zostać
  wyczyszczone przed mergem PR”).

## Phase 6 review (2026-10-06, `reviews/impl-review-phase-6.md`)

- [ ] **F9 → PR description:** the direction and contradiction thresholds in `GoldMetrics` were relaxed
  (≥75 % → ≥70 %, ≤3 % → ≤10 %) in the same phase and from the same report that chose `luna` (user's
  decision, report `gold-2026-10-06.md`). `RecordingsReplayTest` therefore guards Luna against
  regression (margin: 2 targets on direction, 1 on contradictions); it is not an independent quality
  check. The original thresholds are the goal of post-MVP "LLM hardening".
