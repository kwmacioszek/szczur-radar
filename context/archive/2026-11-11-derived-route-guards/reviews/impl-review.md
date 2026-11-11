<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Strażnik tras admina i `/internal` bez ręcznej listy (S-40)

- **Plan**: context/changes/derived-route-guards/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2
- **Date**: 2026-11-11
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Evidence

- Plan adherence: każda zmiana z faz 1–2 = MATCH (MappedRoutes `routesUnder` przez `PathPattern`, literały kontraktu; `AdminRoleIT.everyAdminRouteRejectsPlainUsers` z kotwicami i zbieraniem błędów; `AuthorizationCompletenessIT` bez `ADMIN_ROUTES`, z kontrolą `RouterFunction` i wzorców `PUBLIC_ROUTES`; `InternalProbeController`; obie klasy `/internal` po wyliczonych trasach przez `internal(...)`; dokumentacja). Pokrycie nie zmalało: 403 sprawdza teraz także treść `forbidden`, sesja admina na `/internal` obejmuje każdą trasę z treścią.
- `src/main`: tylko 3 linie javadocu `SecurityConfig` (`git diff --stat origin/main...HEAD -- szczur-radar-api/src/main`).
- 1.1 + 2.1: `AdminRoleIT, AuthorizationCompletenessIT, InternalPipelineRunIT, InternalPipelineRunWithoutTokenIT` — 35 testów, 0 błędów (lokalnie, z `llm.env`).
- 1.2 powtórzony w przeglądzie: `AdminRoleIT` czerwony z `GET /api/admin/llm/break -> 200`; cofnięty, `git status --porcelain szczur-radar-api/src/main` puste.
- 2.2 powtórzony w przeglądzie: `InternalPipelineRunIT` (8 porażek) i `InternalPipelineRunWithoutTokenIT` (3) czerwone z `GET /internal/probe -> 200`; cofnięty, `src/main` czysty.
- 1.3 nie powtórzony; typ `org.springframework.web.servlet.function.RouterFunction` i brak takich beanów frameworka sprawdzone w kodzie.
- 1.4: `git grep -n ADMIN_ROUTES -- ':!context'` puste.
- 1.5 / 2.3: lokalny pełny `verify` w przeglądzie padł środowiskowo (powłoka bez `LLM_*` → `InvalidSettingsException`, 370 IT bez kontekstu); dowód: job CI `backend (Maven verify)` zielony na HEAD `d8e672c` (PR #165).
- 2.4: `gh pr checks 165` — wszystkie joby `pass`.
- Manual: brak wierszy Manual w planie.

## Findings

### F1 — Kolejność importów, długa linia i javadoc MappedRoutes

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/web/MappedRoutes.java:16-18,21-22,31; szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java:18-19
- **Detail**: `.editorconfig` porządkuje importy alfabetycznie w grupach: `PathPatternParser` stoi przed `web.servlet…` (MappedRoutes), `http.server.PathContainer` przed `http.HttpMethod` (AuthorizationCompletenessIT). Javadoc `ADMIN_PATHS` (l. 31) ma 133 znaki przy `max_line_length = 120` i mówi o „`SecurityConfig`'s own constant”, a `SecurityConfig` ma tylko literał w regule. Javadoc klasy wymienia użytkowników `AdminRoleIT`, `AuthorizationCompletenessIT`, `ContractRoutesIT`, bez dwóch klas `/internal`, które teraz też używają `routesUnder`.
- **Fix**: Optimize Imports w obu plikach; zawinąć javadoc `ADMIN_PATHS` z „not read from `SecurityConfig`”; dopisać `InternalPipelineRunIT` i `InternalPipelineRunWithoutTokenIT` do listy w javadocu klasy.
- **Decision**: FIXED

### F2 — Komunikat porażki `/internal` gubi treść odpowiedzi

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java:399
- **Detail**: Dla statusu innego niż 401 treść jest zastępowana `""`, więc przebieg psujący 2.2 dał `GET /internal/probe -> 200 ` bez ciała (`InternalPipelineRunWithoutTokenIT` pokazuje `{"status":"ok"}`). Żadna z pętli `/internal` nie dołącza `getUnresolvedException()`, jak robi `AdminRoleIT`. Test nadal czerwienieje; trudniej tylko zdiagnozować regresję.
- **Fix**: Zawsze dołączać `bodyOf(result)` (i nazwę nierozwiązanego wyjątku, gdy jest) do wpisu porażki.
- **Decision**: FIXED

### F3 — `AdminRoleIT` bez atrapy `LlmClient`

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AdminRoleIT.java:70
- **Detail**: Gdyby reguła admina się cofnęła, pętla wywołałaby `GET /api/admin/llm/check`, który łączy się z prawdziwym dostawcą LLM kluczem CI. Ryzyko nie jest nowe: trasa była na dawnej liście `ADMIN_ROUTES`. Wyprowadzony zbiór wejdzie też w każdą przyszłą trasę admina ze skutkami ubocznymi, ale tylko przy regresji, którą test i tak zgłosi.
- **Fix**: `@MockitoBean LlmClient` w `AdminRoleIT`; klasa ma już własny klucz kontekstu przez `@Import(AdminProbeController)`, więc nie dochodzi nowy kontekst.
- **Decision**: FIXED

### F4 — Niezatwierdzona zmiana statusu S-40 w roadmapie bez SVG

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/foundation/roadmap.md (S-40, ready → in-progress, niezatwierdzone); tools/render_roadmap_svg.py:216
- **Detail**: Drzewo robocze ma niezatwierdzoną zmianę roadmapy (S-40 `in-progress`, `updated: 2026-11-11`), a `REPOSITORY_GUIDELINES.md` wymaga przy każdej zmianie roadmapy aktualizacji danych w `tools/render_roadmap_svg.py` (S-40 wciąż `"ready"`; skrypt zna status `"progress"`) i regeneracji `docs/visuals/roadmap.svg`.
- **Fix**: Zacommitować roadmapę razem z `"progress"` dla S-40 w `render_roadmap_svg.py` i przegenerowanym SVG (albo zostawić do `/10x-archive`, który i tak ustawi `done`).
- **Decision**: FIXED (Fix A)
