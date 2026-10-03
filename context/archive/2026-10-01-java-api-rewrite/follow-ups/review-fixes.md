# Implementation review fixes

Source: `../reviews/impl-review.md`. Reviewed phases: 1, 2, 3, 4.

## F1 — Zakodowana ścieżka omija filtr Origin

- **Decision:** user selected „Napraw teraz”, 2026-10-03.
- **Status:** FIXED; delivered in the review-fixes PR (branch `fix/java-api-rewrite-review-fixes`), merged by a human.
- [x] Replace raw URI prefix comparison with Spring Security's `PathPatternRequestMatcher` for `/api/**`.
- [x] Add real HTTP regression coverage for `/api/auth/logout`, `/%61pi/auth/logout`, `/a%70i/auth/logout` and `/ap%69/auth/logout`, with foreign and same Origin.
- [x] Verify rejection returns 403 `cross_origin` without `Set-Cookie`; allowed logout still returns 204 and expires the cookie.
- [x] Verify regression before fix: three encoded-prefix cases returned 204 instead of 403.
- [x] Run full `./mvnw -B verify`: 70 unit + 57 integration tests passed, no failures/errors/skips.
- [x] Verify all eight scenarios through local Caddy with the repository Caddyfile; stop temporary API and proxy afterward.

Files changed: `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/OriginGuardFilter.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/OriginGuardHttpIT.java`.

## F2 — Aktywne instrukcje i status wdrożenia

- **Decision:** user selected „Napraw teraz”, 2026-10-03.
- **Status:** FIXED; delivered in the review-fixes PR (branch `fix/java-api-rewrite-review-fixes`), merged by a human.
- [x] Update the active pipeline instruction to Java/Embabel, retaining the independent scheduled job boundary.
- [x] Record production close-out with the existing verification evidence from issue #25 and label the completed switch runbook historical.
- [x] Mark F-03 done in the roadmap, record its cold-start measurement and remove the stale F-02 waiting note.
- [x] Synchronize `tools/render_roadmap.swift` and regenerate `docs/visuals/roadmap-mvp.png`: F-03 deployed, 2/10 completed, 2026-10-03.
- [x] Inspect the regenerated image and run `git diff --check`.

Files changed: `REPOSITORY_GUIDELINES.md`, `context/deployment/deploy-plan.md`, `context/foundation/roadmap.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`.

Triage complete: F1 and F2 fixed; no pending findings. Implementation-review fixes are delivered in their own PR (branch `fix/java-api-rewrite-review-fixes`), separate from the production release already verified in issue #25.
