# Admin przywraca martwe posty (S-33) — plan implementacji

## Overview

Post, którego trzecia próba oceny się nie udała, staje się martwy (`dead`) i dziś nie ma drogi powrotu: zła noc dostawcy albo zmiana promptu przepada na zawsze (destylacja R-04, Q-18). FR-034 (must-have, PRD v3): admin zaznacza martwe posty na liście „Nieudane posty” w `/admin` i przywraca je do oceny — post traci martwy stan, jego próby są zerowane, a kolejny przebieg ocenia go od nowa. Issue: #143.

## Current State Analysis

- Jedynym pisarzem stanu `dead` jest `PostTransitions.Outcome.after` (`szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/PostTransitions.java:201-210`); nic nie zapisuje przejścia z `dead`.
- `ck_posts_stage_attempts` (`V3__create_pipeline_and_posts.sql:116-121`) wymaga `dead` ⇔ `assessment_attempts = 3` i `stored` ⇔ `0`, więc przejście `dead → stored` musi w jednym `UPDATE` ustawić oba pola; `dead → failed` jest niemożliwe (`failed` ma 1–2 próby).
- Martwy post ma zawsze `technical_failures = 0` i `last_technical_error = NULL` (`record` je zeruje, `countTechnicalFailure` dotyka tylko `stored`/`failed`), a `assessing_run_id = NULL`.
- Żaden krok przebiegu nie zapisuje martwego posta (`claim` tylko `stored`/`failed`, reszta tylko `assessing`), więc przywrócenie nie koliduje z trwającym przebiegiem.
- Post `stored` z 0 prób i 0 błędów technicznych to „leftover” (`PostRepository.findLeftoverIds`, `:38-45`): nocny cron dołoży przebieg RETRY (`PipelineRunService.startRange:185`), a post znika z listy nieudanych (`findFailedPage`, `:80-88`).
- Lista nieudanych: `posts/api/FailedPostController` (`GET /api/admin/posts/failed`); ponowienie wybranych: `POST /api/admin/pipeline-runs` z `postIds`, które odmawia martwym (400 `post_dead`, `PipelineRunService.startPosts:195-204`).
- SPA: `szczur-radar-web/src/views/AdminView.vue` — checkbox martwego posta wyłączony (`:422-427`), `retryable` bez martwych (`:244`), `loadFailed` wyrzuca z zaznaczenia id spoza `retryable` (`:251`), „Ponów zaznaczone” (`:450-452`), komunikaty `StatusMessage` (`:454-455`); po ponowieniu lista nieudanych nie jest odświeżana (`retry()`, `:259-274`).
- Strażnicy: `pipeline/state/PostColumnsIT` (każda publiczna metoda `PostTransitions` musi mieć scenariusz, `:178-193`), `auth/AuthorizationCompletenessIT.ADMIN_ROUTES` (`:63-73`) i `auth/AdminRoleIT` (403 dla zwykłego użytkownika, lista dokładnie równa trasom `/api/admin/**`).

## Desired End State

- `POST /api/admin/posts/restore` z ciałem `{"postIds": [...]}` (1–100 id, bez `null`; inaczej 400 `invalid_request`) przywraca te z nich, które są `dead`: `stage = stored`, `assessment_attempts = 0`, `technical_failures = 0`, `last_technical_error = NULL`, `updated_at = now()` — jednym warunkowym `UPDATE`. Odpowiedź 200 `{"restored": N}`. Id, które nie są martwe (już przywrócone, ocenione, nieistniejące), zostają bez zmian; trasa działa także podczas przebiegu. Wiersze `assessments` posta zostają.
- Linia INFO w logu: kto przywrócił, ile z ilu, które id.
- `/admin`, „Nieudane posty”: martwe posty da się zaznaczyć; dwa przyciski działają na podzbiorach zaznaczenia — „Ponów zaznaczone (N)” na postach do ponowienia, „Przywróć martwe (M)” na martwych; każdy wyłączony przy 0. Po przywróceniu: komunikat `role="status"`, lista nieudanych wczytana ponownie (przywrócony post z niej znika), zaznaczenie bez przywróconych.
- Kolejny przebieg (nocny RETRY albo RETRY przed zakresem admina) ocenia przywrócony post od nowa, z pełnymi trzema próbami.
- Weryfikacja: `./mvnw -B verify`, `npm test`, `npm run build`, scenariusz w Railway PR env wykonany przez agenta.

### Key Discoveries:

- `PostTransitions` to jedyne miejsce zmian stanu posta i ma strażnika `PostColumnsIT` — przywrócenie idzie tam, nie do repozytorium JPA.
- `posts/api` nie importuje dziś niczego z `pipeline`; nowy kontroler w `posts/api` wołający `pipeline/state/PostTransitions` dodaje tę zależność (jak `pipeline` → `posts`). Brak ArchUnit; akceptujemy, bo trasa należy do listy nieudanych (`/api/admin/posts/…`), a stan posta do `PostTransitions`.
- Mutacja admina bez kodu błędu domenowego: wzorzec `notifications/api/NotificationController` (`:72-85`); walidacja listy id: `PipelineRunController.StartRequest` (`:178-194`, `MAX_POSTS = 100`, `:67`).
- CSRF: `OriginGuardFilter` obejmuje każdy POST pod `/api/**`; nowa trasa nie potrzebuje nic więcej.
- `dev/mock-api.js` ma martwy post `p3` (`:241-250`); każdy nie-GET na `/api/admin/pipeline-runs` daje 409 (`:426`); nowa trasa idzie przed fallbackiem 404 (`:485`).
- Test `AdminView.test.ts:343-365` („retries the checked posts, never a dead one”) przypina dzisiejszą blokadę i musi się zmienić.

## What We're NOT Doing

- Przywrócenie nie uruchamia przebiegu (decyzja: „tylko przywróć”); ocenę daje kolejny przebieg.
- Nie usuwamy starych wierszy `assessments` (historia prób zostaje; S-35 liczy klasy błędów).
- Bez nowej tabeli audytu i migracji — ślad to linia INFO w logu.
- Bez nowego kodu błędu i bez zmiany `rules.json`; bez pliku kontraktu dla `{restored}` (kształty odpowiedzi SPA to S-41).
- Bez odmowy podczas przebiegu (`pipeline_busy`) i bez advisory locka.
- Liczniki `dead` i powód `dead_posts` minionych przebiegów zostają (to historia przebiegu); dzienne podsumowanie martwych postów to S-34, wyłącznik serii odmów — osobne wymaganie.
- Bez zmiany zachowania „Ponów zaznaczone” poza działaniem na podzbiorze zaznaczenia i liczbą w etykiecie (np. odświeżanie listy po ponowieniu zostaje jak jest).
- Bez stronicowania listy nieudanych.

## Implementation Approach

Najpierw przejście stanu z jego strażnikiem i trasa API (faza 1), potem widok (faza 2), na końcu weryfikacja w PR env z runbookiem (faza 3). Przejście to jeden warunkowy `UPDATE … WHERE stage = 'dead'` — zgodny z regułą pakietu „każda zmiana stanu to jeden warunkowy `UPDATE`, którego liczba wierszy mówi, czy zaszła”, idempotentny i bez blokad.

## Phase 1: Przejście i trasa API

### Overview

Nowe przejście `dead → stored` w `PostTransitions`, trasa admina, strażnicy i dokumentacja pakietów.

### Changes Required:

#### 1. Przejście stanu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/PostTransitions.java`

**Intent**: Dodać publiczną metodę przywracającą martwe posty do oceny; jedyny pisarz wyjścia ze stanu `dead`.

**Contract**: `public int restore(Collection<UUID> postIds)` — jeden `UPDATE posts SET stage = :stored, assessment_attempts = 0, technical_failures = 0, last_technical_error = NULL, updated_at = now() WHERE id IN (:ids) AND stage = :dead`; zwraca liczbę przywróconych. Wartości etapów wiązane z enuma (jak `stages(...)`), nigdy w SQL. Javadoc klasy wymienia nowe przejście.

#### 2. Trasa admina

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/DeadPostController.java` (nowy, package-private jak `FailedPostController`)

**Intent**: Trasa przywracająca zaznaczone martwe posty; waliduje listę jak `StartRequest` i loguje operację.

**Contract**: `POST /api/admin/posts/restore`, `consumes = application/json`, ciało `record RestoreRequest(List<UUID> postIds)`; poprawne tylko przy 1–100 id bez `null` (stała `MAX_POSTS = 100` własna albo współdzielona z `PipelineRunController` — implementujący wybiera bez duplikowania literału), inaczej `ApiError.response(ErrorCode.INVALID_REQUEST)`. Duplikaty id dozwolone (liczy `UPDATE`). Odpowiedź 200 `record RestoreResult(int restored)`. Linia INFO: `Admin <userId> restored <N> of <M> dead post(s) for assessment: <ids>` (id admina z `authentication.getName()`, bez treści postów).

#### 3. Testy przejścia

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/PostColumnsIT.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/PostRepositoryIT.java`

**Intent**: Scenariusz `restore` w `PostColumnsIT` (martwy post z 3 próbami → `row(STORED, 0, null, 0, null)`, kolumny jednorazowe bez zmian, `updated_at` przesunięte; przebieg w `given()` zakończony, np. `SUCCEEDED`). W `PostRepositoryIT`: przywraca tylko `dead` spośród `stored`/`failed`/`assessing`/`assessed`/`dead` (pozostałe wiersze bez zmian), zwraca liczbę, drugie wywołanie zwraca 0, wiersze `assessments` posta zostają, przywrócony post jest w `findLeftoverIds`.

**Contract**: nazwa scenariusza `"restore"` z `method = "restore"` (wymóg `everyTransitionHasAScenario`).

#### 4. Testy trasy i strażnicy tras

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java`

**Intent**: Wpis `"POST /api/admin/posts/restore"` w `ADMIN_ROUTES` (401 bez sesji i 403 dla zwykłego użytkownika dają istniejące testy). Scenariusze w `PostsApiIT` (`@Transactional`, bez atrapy LLM — nie startuje tu przebiegów): przywrócenie martwego → 200 `{"restored":1}`, post znika z `GET /api/admin/posts/failed`, jego próby `assessments` zostają, a `posts.findIdsInStage(List.of(id), DEAD)` jest puste (ta sama kontrola co w `startPosts`); lista mieszana (martwy + `failed` + nieistniejące id) → `restored: 1`, `failed` bez zmian; powtórzenie → `restored: 0`; pusta lista, 101 id, `null` w liście, brak pola → 400 `invalid_request`; przywrócenie przy przebiegu `running` (wiersz wstawiony SQL-em, cofany z testem) → 200. Koniec do końca w `PipelineRunControllerIT` (nietransakcyjny, `@MockitoBean LlmClient`, `awaitIdle`), na wzór `retryingADeadPostIsRefusedAndARetryableOneIsQueued` (`:272-287`): martwy post → przebieg wybranych daje 400 `post_dead` → przywrócenie → przebieg wybranych 202 → `awaitIdle()` → post `assessed` z jednym wierszem `assessments` więcej (asercja po końcu przebiegu, bo `awaitIdle` zamienia pozostałe leftovery w martwe, `:152`).

**Contract**: dokładne ciało odpowiedzi `{"restored":N}`.

#### 5. Dokumentacja w kodzie

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ErrorCode.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/FailedPostController.java`, `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`

**Intent**: `pipeline/CLAUDE.md` — wiersz `state/PostTransitions` z `restore`, reguła „Attempts: three faults” (trzecia próba czyni post `dead`, z którego wraca tylko przywrócenie admina), lista testów (`PostColumnsIT`, `PostRepositoryIT`, `PostsApiIT`). Javadoc `ErrorCode.POST_DEAD`: zamiast „reviving one is outside S-02” — martwy post wraca przez `POST /api/admin/posts/restore` (S-33). Javadoc `FailedPostController`: martwe przywraca `DeadPostController`. `REPOSITORY_GUIDELINES.md`, opis pakietu `posts`: jedno zdanie o przywracaniu martwych postów. Etykietę przycisku cytują `szczur-radar-api/README.md:133` i `pipeline/CLAUDE.md:364` („Ponów zaznaczone”) — obok niej „Przywróć martwe” dla martwych postów.

**Contract**: tylko proza.

#### 6. Roadmapa w rendererze

**File**: `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Reguła repozytorium: zmiana `roadmap.md` (status S-33 `planning`/`in-progress`) aktualizuje dane renderera i SVG.

**Contract**: krotka `"S-33"` w danych renderera ze statusem `"progress"` („W toku”) — renderer nie zna `planning` ani `in-progress` (`STATUS`, `tools/render_roadmap_svg.py:29-36`), oba mapują się na `progress`; `python3 tools/render_roadmap_svg.py`.

### Success Criteria:

#### Automated Verification:

- Testy integracyjne i jednostkowe backendu przechodzą (`cd szczur-radar-api && ./mvnw -B verify`)
- `PostColumnsIT.everyTransitionHasAScenario` jest czerwony po usunięciu scenariusza `restore` (sprawdzenie celowego złamania, potem przywrócenie)
- `AdminRoleIT` i `AuthorizationCompletenessIT` przechodzą z nową trasą w `ADMIN_ROUTES`
- SVG roadmapy wygenerowany ponownie bez błędu (`python3 tools/render_roadmap_svg.py`) i `git diff --stat` pokazuje zmianę `docs/visuals/roadmap.svg`

---

## Phase 2: Widok `/admin`

### Overview

Martwe posty da się zaznaczyć, a przywrócenie jest drugim przyciskiem obok ponowienia.

### Changes Required:

#### 1. Klient API

**File**: `szczur-radar-web/src/api/posts.ts`

**Intent**: Funkcja wołająca nową trasę.

**Contract**: `restorePosts(postIds: string[]): Promise<{ restored: number }>` — `POST /api/admin/posts/restore` z `{ postIds }` przez `apiFetch`.

#### 2. Widok

**File**: `szczur-radar-web/src/views/AdminView.vue`

**Intent**: Jedno zaznaczenie, dwa podzbiory. Checkbox martwego posta aktywny; `loadFailed` wyrzuca z zaznaczenia tylko id, których nie ma już na liście. „Ponów zaznaczone (N)” wysyła tylko zaznaczone posty do ponowienia i usuwa je z zaznaczenia; „Przywróć martwe (M)” (`AppButton`, `busy` z etykietą „Przywracanie…”) wysyła tylko zaznaczone martwe, usuwa je z zaznaczenia i wczytuje listę nieudanych ponownie. Komunikaty przez `StatusMessage`: sukces „Przywrócono N post(ów) do oceny. Oceni je kolejny przebieg.”, `restored: 0` „Żaden z zaznaczonych postów nie był już martwy.”, błąd ogólny „Nie udało się przywrócić postów.” Oba przyciski wyłączone przy pustym podzbiorze i podczas trwającej akcji drugiego.

**Contract**: bez nowych literałów kolorów i rozmiarów (plik jest na liście `CLEANED` w `styleContract.test.ts`); tylko `AppButton` i `StatusMessage`.

#### 3. Atrapa API

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Trasa przywracania do zrzutów ekranu: zamienia wskazane martwe posty atrapy na `stored` z 0 prób (znikają z listy) i odpowiada `{restored: N}`; tryby `loading`/`error` dziedziczy z prefiksu `/api/admin`. Nagłówek pliku opisuje trasę.

**Contract**: `POST /api/admin/posts/restore` przed fallbackiem 404.

#### 4. Testy

**File**: `szczur-radar-web/src/views/AdminView.test.ts`, `szczur-radar-web/src/api/pipeline.test.ts` (albo test `posts.ts`, gdzie są testy `fetchFailedPosts`)

**Intent**: Zastąpić „never a dead one”: martwy post da się zaznaczyć; przy zaznaczeniu mieszanym `retryPosts` dostaje tylko posty do ponowienia, a `restorePosts` tylko martwe; liczby w etykietach przycisków; każdy przycisk wyłączony przy pustym podzbiorze; po przywróceniu komunikat `role="status"`, `fetchFailedPosts` wołane ponownie, przywrócone id znikają z zaznaczenia; `restored: 0` i błąd dają swoje komunikaty (`role="alert"` dla błędu); `aria-busy` i „Przywracanie…” w trakcie. Test klienta: URL, metoda i ciało `restorePosts`.

**Contract**: trzy testy szukają przycisku po dokładnym tekście `'Ponów zaznaczone'` i zmieniają się razem z etykietą (szukanie po prefiksie albo po nazwie z liczbą): `AdminView.test.ts:355` („retries the checked posts, never a dead one” — przepisany wyżej), `:381` („shows a post on hold … and retries it”), `:409` („says the retry is under way …”); ich asercje poza etykietą zostają.

### Success Criteria:

#### Automated Verification:

- Testy SPA przechodzą (`cd szczur-radar-web && npm test`)
- Typecheck i build przechodzą (`npm run build`)
- Agent sprawdza sekcję „Nieudane posty” na atrapie (`dev/mock-api.js` + `playwright-cli`): zaznaczenie mieszane, dwa przyciski z liczbami, komunikat po przywróceniu i zniknięcie posta; oba motywy, 375 px; 7 stanów (default, hover, focus-visible, disabled, error, empty, loading) jako zrzut albo N/A z powodem

#### Manual Verification:

- Właściciel akceptuje etykiety przycisków i treść komunikatów w PR

---

## Phase 3: Weryfikacja w PR env i runbook

### Overview

Scenariusz end-to-end w Railway PR env i sekcja runbooka dla operatora.

### Changes Required:

#### 1. Runbook

**File**: `context/deployment/deploy-plan.md`

**Intent**: Nowa sekcja `## S-33: admin przywraca martwe posty (restore-dead-posts)`: linia „**Status:** delivered by PR #N; production verification and closure in issue #143.” (lekcja o statusie prawdziwym po merge'u); kiedy przywracać (zła noc dostawcy, zmiana promptu), co się dzieje (stan, próby, historia zostaje, kolejny przebieg), trasa i linia logu, weryfikacja po merge'u tylko do odczytu (log INFO, brak martwych postów przywróconych przez pomyłkę).

**Contract**: tylko proza.

#### 2. Scenariusz w PR env

**Intent**: Agent w PR env tej gałęzi: zapewnia post w bazie (przebieg zakresu z panelu albo istniejący), czyni jeden post martwym SQL-em przez `railway ssh -s postgres -e szczur-radar-pr-<N> -- psql -U postgres -d railway -c "<SQL>"` (tunel `railway connect` się zrywa, `deploy-plan.md:1081,1593`; precedens: S-11, `:1595-1598`) (`UPDATE posts SET stage = 'dead', assessment_attempts = 3 WHERE id = … AND stage IN ('stored','failed','assessed')` — zgodne z `ck_posts_stage_attempts`), otwiera `/admin` w widocznej przeglądarce (`playwright-cli`, właściciel loguje się sam), zaznacza i przywraca post, sprawdza komunikat i zniknięcie z listy, uruchamia przebieg jednego dnia i sprawdza, że RETRY ocenił przywrócony post (`stage` i nowy wiersz `assessments` w bazie). Dowody (polecenia, wyniki, zrzut) w komentarzu issue #143.

**Contract**: bez zmian w produkcji; tylko PR env.

### Success Criteria:

#### Automated Verification:

- CI PR jest zielone (`gh pr checks <PR> --watch`, w tym `railway-config` i deploy PR env)
- W PR env przywrócony post ma `stage = 'stored'`, `assessment_attempts = 0` zaraz po przywróceniu (SQL przez `railway ssh -s postgres -e szczur-radar-pr-<N> -- psql …`, wynik w komentarzu issue)
- Po przebiegu w PR env ten post ma `stage IN ('assessed','failed')` i o jeden wiersz `assessments` więcej niż przed przywróceniem (SQL przez `railway ssh -s postgres … -- psql …`)
- Log API w PR env ma linię `restored 1 of 1 dead post(s)` (`railway logs` z `grep`)
- Agent sprawdza `/admin` w PR env przez `playwright-cli`: komunikat po przywróceniu i brak posta na liście (zrzut w komentarzu issue)
- Komentarz w issue #143 z gałęzią, PR i dowodami (`gh issue view 143 --comments`)

#### Manual Verification:

- Właściciel loguje się w oknie przeglądarki otwartym przez agenta (hasło nie przechodzi przez agenta)
- Właściciel akceptuje sekcję S-33 w `deploy-plan.md`

---

## Testing Strategy

### Unit Tests:

- SPA (Vitest): podzbiory zaznaczenia, etykiety z liczbami, wyłączanie, komunikaty, ponowne wczytanie listy, klient `restorePosts`.

### Integration Tests:

- `PostColumnsIT` (scenariusz `restore`), `PostRepositoryIT` (tylko `dead`, liczba, idempotencja, historia, leftover), `PostsApiIT` (trasa, walidacja, lista mieszana, przebieg w toku, post nie jest już martwy), `PipelineRunControllerIT` (martwy → `post_dead` → przywrócenie → przebieg wybranych → `assessed`), `AdminRoleIT` / `AuthorizationCompletenessIT` (401, 403).

### Manual Testing Steps:

1. Zalogowanie właściciela w oknie agenta w PR env.
2. Ocena etykiet i komunikatów w PR.

## Performance Considerations

Jeden `UPDATE` po kluczu głównym dla najwyżej 100 id; bez wpływu na przebieg.

## Migration Notes

Brak migracji: ograniczenia `V3` już dopuszczają `stored` z 0 prób. Na produkcji martwe posty z przed wdrożenia da się przywrócić od razu po merge'u.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-33); PRD: `context/foundation/prd.md:270-271` (FR-034), `:163` (FR-015, grupa (a))
- Destylacja: `context/domain/domain-distillation.md` (R-04, Q-18)
- Podobne: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/PostTransitions.java`, `notifications/api/NotificationController.java:72-85`, `pipeline/api/PipelineRunController.java:99-110,178-194`, `szczur-radar-web/src/views/AdminView.vue:235-274,413-456`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Przejście i trasa API

#### Automated

- [x] 1.1 Testy integracyjne i jednostkowe backendu przechodzą (`./mvnw -B verify`) — 83d3420
- [x] 1.2 `PostColumnsIT.everyTransitionHasAScenario` jest czerwony po usunięciu scenariusza `restore` — 83d3420
- [x] 1.3 `AdminRoleIT` i `AuthorizationCompletenessIT` przechodzą z nową trasą w `ADMIN_ROUTES` — 83d3420
- [x] 1.4 SVG roadmapy wygenerowany ponownie bez błędu — 83d3420

### Phase 2: Widok `/admin`

#### Automated

- [x] 2.1 Testy SPA przechodzą (`npm test`) — e022c03
- [x] 2.2 Typecheck i build przechodzą (`npm run build`) — e022c03
- [x] 2.3 Agent sprawdza sekcję „Nieudane posty” na atrapie przez `playwright-cli` (oba motywy, 375 px, 7 stanów) — e022c03

#### Manual

- [ ] 2.4 Właściciel akceptuje etykiety przycisków i treść komunikatów w PR

### Phase 3: Weryfikacja w PR env i runbook

#### Automated

- [x] 3.1 CI PR jest zielone — d298688
- [x] 3.2 W PR env przywrócony post ma `stored` i 0 prób (SQL) — d298688
- [x] 3.3 Po przebiegu w PR env przywrócony post jest oceniony z nowym wierszem `assessments` (SQL) — d298688
- [x] 3.4 Log API w PR env ma linię przywrócenia — d298688
- [x] 3.5 Agent sprawdza `/admin` w PR env przez `playwright-cli` — d298688
- [x] 3.6 Komentarz w issue #143 z gałęzią, PR i dowodami — d298688

#### Manual

- [ ] 3.7 Właściciel loguje się w oknie przeglądarki otwartym przez agenta
- [ ] 3.8 Właściciel akceptuje sekcję S-33 w `deploy-plan.md`
