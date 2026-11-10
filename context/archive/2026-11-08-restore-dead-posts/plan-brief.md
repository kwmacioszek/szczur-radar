# Admin przywraca martwe posty (S-33) — Plan Brief

> Full plan: `context/changes/restore-dead-posts/plan.md`

## What & Why

Post po trzeciej nieudanej próbie oceny staje się martwy (`dead`) i dziś nie ma drogi powrotu, więc zła noc dostawcy albo zmiana promptu na zawsze zabiera ocenę całej partii postów (R-04, Q-18). FR-034 (must-have): admin zaznacza martwe posty w `/admin` i przywraca je do oceny — post traci martwy stan, próby są zerowane, kolejny przebieg ocenia go od nowa. Issue #143.

## Starting Point

Lista „Nieudane posty” w `/admin` pokazuje martwe posty z wyłączonym checkboxem, a ponowienie wybranych odmawia im (400 `post_dead`). Stan posta zmienia tylko `PostTransitions` (warunkowe `UPDATE`, strażnik `PostColumnsIT`); ograniczenie bazy wymaga `dead` ⇔ 3 próby i `stored` ⇔ 0 prób.

## Desired End State

Admin zaznacza martwe posty obok innych i klika „Przywróć martwe (M)”; posty wracają do `stored` z 0 prób, znikają z listy, a nocny przebieg RETRY (albo RETRY przed zakresem admina) ocenia je od nowa z pełnymi trzema próbami. Historia starych prób zostaje, a w logu jest linia, kto i co przywrócił.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Po przywróceniu | Tylko przywróć, bez startu przebiegu | Dokładnie FR-034; bez kosztu LLM i bez 409 przy przywracaniu. |
| Historia prób | Wiersze `assessments` zostają, zeruje się licznik | „Każda próba oceny” jest przechowywana; S-35 liczy klasy błędów. |
| Zaznaczenie mieszane | Jedno zaznaczenie, dwa przyciski na podzbiorach z liczbami | Zaznaczenie mieszane nigdy nie kończy się `post_dead`. |
| Post już nie martwy, przebieg w toku | Przywróć te, które są martwe; `{restored: N}`; działa podczas przebiegu | Idempotentne, bez nowego kodu błędu; żaden krok przebiegu nie pisze martwego posta. |
| Miejsce przejścia | `PostTransitions.restore` + scenariusz w `PostColumnsIT` | Jedyny dom zmian stanu posta, ze strażnikiem. |
| Trasa | `POST /api/admin/posts/restore`, `{postIds}` 1–100, 200 `{restored}` | Obok listy nieudanych; walidacja jak przy ponowieniu wybranych. |
| Audyt | Linia INFO w logu, bez tabeli i migracji | Wymaganie nie żąda śladu w bazie. |

## Scope

**In scope:**
- Przejście `dead → stored` (0 prób, 0 błędów technicznych) i trasa admina z testami i wpisem w `ADMIN_ROUTES`
- Widok `/admin`: zaznaczanie martwych, dwa przyciski, komunikaty, ponowne wczytanie listy; atrapa API; testy Vitest
- Dokumentacja (`pipeline/CLAUDE.md`, javadoc `POST_DEAD`, wytyczne), renderer roadmapy, sekcja S-33 w `deploy-plan.md`
- Scenariusz w Railway PR env wykonany przez agenta

**Out of scope:**
- Start przebiegu przy przywróceniu, usuwanie historii prób, tabela audytu, nowy kod błędu, plik kontraktu `{restored}` (S-41)
- Liczniki i powody minionych przebiegów, dzienne podsumowanie martwych (S-34), wyłącznik serii odmów
- Stronicowanie listy nieudanych, inne zmiany „Ponów zaznaczone”

## Architecture / Approach

`AdminView` → `restorePosts` (`src/api/posts.ts`) → `POST /api/admin/posts/restore` (`posts/api/DeadPostController`, sesja admina, filtr Origin) → `PostTransitions.restore` (jeden `UPDATE … WHERE stage = 'dead'`) → post jest „leftoverem” → kolejny przebieg RETRY go bierze (`AssessmentSelection`, `claim`).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Przejście i trasa API | `restore`, trasa, strażnicy, dokumentacja | Nowa zależność `posts/api` → `pipeline/state` (świadomie przyjęta) |
| 2. Widok `/admin` | Dwa przyciski na podzbiorach, komunikaty, atrapa, testy | Zmiana zachowania zaznaczenia przy „Ponów zaznaczone” |
| 3. Weryfikacja w PR env i runbook | Dowód end-to-end, sekcja S-33 | Martwy post w PR env trzeba zrobić SQL-em |

**Prerequisites:** gałąź `feature/restore-dead-posts` z `origin/main`, issue #143, dostęp agenta do PR env (Railway CLI) i logowanie właściciela w przeglądarce.
**Estimated effort:** ~1–2 sesje w 3 fazach.

## Open Risks & Assumptions

- Przebieg RETRY, który już przeszedł krok wyboru, nie weźmie posta przywróconego w jego trakcie — weźmie go następny przebieg.
- Przywrócony post, który znów trzy razy zawiedzie, wróci na listę jako martwy z więcej niż trzema wierszami prób w historii (licznik mówi 3).

## Success Criteria (Summary)

- Admin przywraca zaznaczone martwe posty jednym kliknięciem i widzi, ile przywrócono.
- Kolejny przebieg ocenia przywrócone posty od nowa (test `PipelineRunControllerIT` w CI i scenariusz w PR env).
- CI zielone, a strażnicy przejść i tras admina znają nową operację.
