<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Backend w Javie (Spring Boot) zamiast Pythona/FastAPI

- **Plan**: context/changes/java-api-rewrite/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-03
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 0 observations
- **Git scope**: `6d4861d..5cddff2`; clean working tree at review start. Includes the implementation squash `7a406f2` and subsequent follow-ups through `5cddff2` (PR #27). No implementation changes made during review.
- **Progress**: 26/26 completed criteria, current phase 4. Original status `implemented`; review sets `impl_reviewed`. Progress checkboxes and SHA suffixes remain unchanged.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Existing automated checks pass and recorded manual criteria have supporting deployment/issue evidence. F1 is a reproduced security-control gap beyond the existing tests, so the warnings are not both minor and the review requires attention.

## Findings

### F1 — Zakodowana ścieżka omija filtr Origin

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/auth/OriginGuardFilter.java:36
- **Detail**: Filtr rozpoznaje API przez surowe `getRequestURI().startsWith("/api/")`, podczas gdy serwer i routing obsługują również ścieżkę z zakodowaną literą. Na rzeczywistym lokalnym serwerze Tomcat z jar z tego przeglądu `POST /api/auth/logout` z `Origin: https://evil.example` zwraca 403 `cross_origin`, lecz `POST /%61pi/auth/logout` z tym samym nagłówkiem dociera do kontrolera i zwraca 204 oraz `Set-Cookie: sr_session=; Max-Age=0; Path=/api; Secure; HttpOnly; SameSite=Strict`. Narusza to kontrakt fazy 2: obcy Origin ma być odrzucany przed obsługą zapisu. `Path=/api` ogranicza przesyłanie istniejącego ciasteczka na zakodowaną ścieżkę; nie wykazano przejęcia sesji ani wykonania chronionego zapisu. Potwierdzony skutek to pominięcie kontroli i odpowiedź kasująca ciasteczko bez sesji.
- **Fix**: Dopasowuj zakres filtra do znormalizowanej ścieżki tym samym mechanizmem co Spring Security; dodaj test regresyjny na prawdziwym serwerze HTTP obejmujący zwykłą i zakodowaną ścieżkę logout z obcym Origin.
  - Strength: Usuwa rozbieżność między zakresem kontroli bezpieczeństwa a faktycznym routingiem.
  - Tradeoff: Trzeba zweryfikować semantykę dopasowania również za proxy; sam MockMvc może nie odtworzyć dekodowania przez kontener.
  - Confidence: HIGH — obejście powtórzone na zbudowanym jar i Tomcat.
  - Blind spot: Nie odtwarzano tego żądania przez produkcyjny Caddy ani zachowania przeglądarki po odpowiedzi; zakres wykazanej podatności to lokalny backend.
- **Decision**: FIXED — 2026-10-03, user selected „Napraw teraz”. `OriginGuardFilter` now uses `PathPatternRequestMatcher.pathPattern("/api/**")`; `OriginGuardHttpIT` covers ordinary and percent-encoded API prefixes on real Tomcat. Before the fix, 3/8 regression cases failed (204 instead of 403); after the fix all passed. Full Maven verification: 70 unit + 57 integration tests, zero failures/errors/skips. Local Caddy using the repository's unchanged Caddyfile also passed all 8 foreign/same-origin cases; foreign Origin returns 403 without Set-Cookie, same Origin returns 204 with cookie expiration. Production was not modified.

### F2 — Aktywne instrukcje i status wdrożenia nie odzwierciedlają zakończonej zmiany

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: REPOSITORY_GUIDELINES.md:46; context/deployment/deploy-plan.md:33; context/foundation/roadmap.md:228
- **Detail**: Faza 4 wymaga aktualnych instrukcji dla agentów i statusu zgodnego z rzeczywistym wdrożeniem. Wytyczne nadal nakazują osobny pipeline LangGraph, chociaż decyzja stacku zastępuje go Embabel (`tech-stack-api.md:35`). Deploy plan nadal ma bieżący nagłówek „production switch pending”, a roadmapa opisuje przełączenie produkcji jako przyszłą pracę i F-03 jako `in-progress`. Tymczasem Progress ma 26/26, PR #26 i #27 są zmergowane, a komentarz zamykający issue #25 potwierdza migracje, gotowość API i logowanie admina na produkcji. To niespójność dokumentacji, nie brak dowodu wykonania tych czynności. Może kierować następne prace ku staremu runtime'owi lub powtarzaniu zakończonej procedury przełączenia.
- **Fix**: Zaktualizuj aktywną instrukcję pipeline'u do wybranego stacku i uzupełnij statusy o zweryfikowane zakończenie F-03 z linkiem do podsumowania issue #25; zsynchronizuj renderer roadmapy i wygeneruj ponownie grafikę zgodnie z regułą repo, zachowując wpisy historyczne.
- **Decision**: FIXED — 2026-10-03, user selected „Napraw teraz”. Active pipeline instructions now specify Java/Embabel and distinguish the existing skeleton from subsequent assessment/scheduling work. Deployment documentation records the verified production close-out from issue #25; the completed switch runbook is labelled historical. Roadmap F-03 is `done`, the cold-start question is resolved using the recorded measurement, and F-02 no longer waits for F-03. The Swift renderer and regenerated PNG show F-03 deployed, 2/10 completed and date 2026-10-03; visual inspection passed. Historical entries remain intact. Local review fixes are explicitly distinguished from the previously verified production deployment.

## Verification

### Automated

Repeated Maven commands in phases 1–4 were executed once against the final reviewed tree; the same run covers all four phases.

| Criteria | Command / evidence | Result |
|---|---|---|
| 1.1, 2.1–2.3, 3.1–3.2, 4.1 | `cd szczur-radar-api`, `source ~/.config/szczur-radar/llm.env`, `./mvnw -B verify` | PASS: 70 unit tests + 49 integration tests; zero failures/errors/skips; `BUILD SUCCESS`, 27.750 s. Real local LLM configuration; no provider calls made by the skeleton. |
| 1.2 | `git ls-files szczur-radar-api` filtered for `.py`, `pyproject.toml`, `uv.lock`, `.python-version`, `alembic.ini` | PASS: no matches. |
| 1.3–1.4, 2.4, 3.3, 4.2 | `gh pr checks 26`, `gh pr checks 27` | PASS: backend, web-unit, web-container-smoke and railway-config green for both PRs. |
| 4.3 | `rg -n 'uv run\|uv sync\|alembic\|pytest\|hypercorn\|JWT_SECRET' REPOSITORY_GUIDELINES.md README.md szczur-radar-api/README.md .railway/railway.ts .github/workflows/ci.yml` | PASS: only `.railway/railway.ts:92–93`, documented unused `JWT_SECRET: preserve()` and its comment. |
| Additional | `git diff --check` | PASS. |

The first sandboxed Maven invocation failed because its local HTTP fixture could not bind (`java.net.SocketException: Operation not permitted`) and mocking/process facilities were restricted. The authorized rerun outside the sandbox passed the full suite, including Testcontainers PostgreSQL. This was an environment restriction, not a test defect. Temporary logs: `/tmp/java-api-review-verify-unrestricted.log`; no secret values copied to this report.

The `railway config plan` PR-environment criterion was verified through the successful CI job that actually ran plan + gate + apply. The review did not repeat deployment operations against completed PR environments or production.

- [PR #26 CI](https://github.com/kwmacioszek/szczur-radar/actions/runs/37060339077)
- [PR #27 CI at reviewed HEAD 5cddff2](https://github.com/kwmacioszek/szczur-radar/actions/runs/37095901467)
- [Main CI after merge 6bc15db](https://github.com/kwmacioszek/szczur-radar/actions/runs/37096163133)

### Additional HTTP reproduction for F1

Ran the built jar locally on port 18091 with existing LLM configuration and a deliberately unavailable local database (`127.0.0.1:1`). This demonstrates the behavior without touching production data. The temporary server was stopped after the review.

```sh
curl --path-as-is -i -X POST http://127.0.0.1:18091/api/auth/logout \
  -H 'Origin: https://evil.example'
# HTTP/1.1 403
# {"detail":"cross_origin"}

curl --path-as-is -i -X POST http://127.0.0.1:18091/%61pi/auth/logout \
  -H 'Origin: https://evil.example'
# HTTP/1.1 204
# Set-Cookie: sr_session=; Max-Age=0; Expires=Thu, 1 Jan 1970 00:00:00 GMT;
#             Path=/api; Secure; HttpOnly; SameSite=Strict
```

### Manual criteria evidence

All manual Progress items are checked. They were assessed against saved operational records, not repeated as live production mutations:

- **1.5–1.6:** deploy-plan.md:43–46 and [phase 1 verification](https://github.com/kwmacioszek/szczur-radar/issues/25#issuecomment-5951046883) record V1/V2, both probes, proxy and IPv4 healthcheck.
- **2.5–2.7, 3.4–3.5:** deploy-plan.md:47–48 and [phases 2–3 verification](https://github.com/kwmacioszek/szczur-radar/issues/25#issuecomment-5955623665) record SPA login/refresh/deep link/logout, Origin, limiter, hidden CLI prompt, Embabel and session persistence through sleep.
- **4.4–4.5:** the PR-env record plus deploy-plan.md:51–56 record end-to-end behavior and measured 10–11 s cold start. The IP-header implementation and unit coverage were inspected; an independent live spoofing probe was not repeated in this review.
- **4.6–4.8:** deploy-plan.md:58–62 plus [production verification](https://github.com/kwmacioszek/szczur-radar/issues/25#issuecomment-5965426023) and [final close-out](https://github.com/kwmacioszek/szczur-radar/issues/25#issuecomment-5965517657) confirm schema cleanup/PITR, migrations and production admin login. The latter also records the sealed production LLM key and PR-env use of the CI key. GitHub reports issue #25 CLOSED. The stale repository summaries are F2.

Criterion 3.2 was evaluated using the explicit triage correction in the phase 3 block: a real configured key is required; the old immutable Progress title does not require secret-free startup.

## Scope and architecture assessment

Two parallel reviewers examined plan adherence and safety/quality respectively. Coverage included all new Java runtime classes, profiles, migrations, test classes/resources, Maven configuration, CI, IaC, Caddy and relevant documentation. Analogous removed Python auth, limiter and CLI implementations informed parity checks; trivial style differences were excluded.

- Planned source and integration points are present: Maven/Java 25, Boot 4, probes with lazy DB access, Flyway schema ownership, session persistence/renewal, JSON auth, limiter reservations, password policy, advisory-lock bootstrap and the Embabel skeleton.
- JPA in place of the originally described JDBC repository is an explicitly recorded user decision (`context/foundation/tech-stack-api.md:34`), with `JpaSchemaIT` and Flyway-only schema ownership. It is not unexplained drift.
- Retained `DATABASE_URL`/`JWT_SECRET` are documented CI deletion-gate exceptions; application runtime no longer uses them. Caddy connection retries and automated PR LLM settings are documented operational follow-ups. No SPA application contract changes were found.
- The git range also includes prior F-02 research/pilot/contract artifacts. They do not introduce F-02 runtime functionality into this rewrite and are not counted as unplanned Java feature implementation.
- No missing implementation file or consequential architectural/pattern violation was confirmed. Speculative future concerns were excluded from findings.

## Triage complete (2026-10-03)

- **F1:** FIXED locally and verified; follow-up tracked in `follow-ups/review-fixes.md`.
- **F2:** FIXED locally and verified; documentation and roadmap image are synchronized.
- **Summary:** Fixed: F1, F2 (2). Pending: none. Skipped / accepted / recorded as lessons: none.
- Original review verdict and phase coverage above are retained as the review-time record. Both findings are resolved in the working tree; changes have not been committed or deployed.
- Verification logs: `/tmp/java-origin-regression-before.log` (3 expected regression failures), `/tmp/java-origin-fix-verify.log` (`BUILD SUCCESS`, 127 tests), `/tmp/java-origin-proxy-check.log` (8 proxy cases passed).
- F2 validation: Swift renderer completed successfully; regenerated `docs/visuals/roadmap-mvp.png` inspected visually; `git diff --check` passed. No backend tests rerun for documentation-only edits after F1's passing suite.
