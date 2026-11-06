# Feed z testami regresji i granicą numeru strony — Plan Brief

> Full plan: `context/changes/feed-regression-and-paging-bounds/plan.md`

## What & Why

Slice S-44 z M-4 zamyka luki, które audyt po M-3 znalazł w feedzie: ogromny numer strony daje 500 zamiast 400 (X6), pięć znanych ścieżek feedu nie ma testów regresji, a linki z U+0000 w `q` albo z ogromnym `?page=` zostawiają feed w błędzie. Nic nie dodajemy do produktu, tylko go zabezpieczamy.

## Starting Point

Trzy listy (`/api/posts`, `/api/admin/posts/failed`, `/api/admin/pipeline-runs`) mają `@Min(0)` na `page` bez górnej granicy; przesunięcie ponad `Integer.MAX_VALUE` kończy się 500. X6 potwierdzono tylko dla feedu bez filtra. API już odrzuca NUL w `q` (400), ale `feedFilter.ts` w SPA go nie usuwa, więc taki link wysyła zapytanie, które zawsze się nie powiedzie.

## Desired End State

`page` ponad granicą daje 400 `invalid_request` na wszystkich trzech listach, a największa dozwolona strona 200 z pustą listą. `PostsApiIT` ma pięć nowych testów, z których każdy czerwienieje na celowo zepsutym kodzie. `/?q=a%00b` ładuje feed z filtrem `ab`, a `/?page=99999999999` pierwszą stronę.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Granica `page` | Statyczne `@Max(Integer.MAX_VALUE / 100)` ze stałej `PageLimits` | Przy `size ≤ 100` zawsze mieści się w incie, korzysta z istniejącej obsługi 400 bez nowej ścieżki kodu. | Plan |
| Ogromny `?page=` w SPA | Strona ponad granicą API → strona 0, granica w `rules.json` | Po poprawce API link nadal zostawiałby feed w błędzie; przegląd planu F1. | Plan review |
| NUL w linku | `normalizeQuery` usuwa U+0000 | Mieści się w funkcji, która już odbija reguły API, i feed się ładuje. | Plan |
| Dowód testów | Celowe zepsucie każdego nowego testu | Ta sama metoda co eksperymenty audytu; PIT zostaje na S-45. | Plan |
| Weryfikacja strony | `playwright-cli` na atrapie API, nie Manual | `lessons.md`: stronę sprawdza agent; atrapa już zwraca 400 na NUL. | Plan |
| Kolejność w fazie 1 | Czerwone testy przed poprawką | Rozstrzyga niewiadomą z audytu o `FailedPostController` i `PipelineRunController`. | Plan |

## Scope

**In scope:** stała `PageLimits` i `@Max` na trzech listach; testy brzegu strony; pięć testów regresji feedu z dowodem zepsucia; usuwanie NUL w `feedFilter.ts` i strona 0 dla `?page=` ponad granicą (wpis w `rules.json`), z testami Vitest i sprawdzeniem przez `playwright-cli`.

**Out of scope:** nowy kod błędu lub zmiana kontraktu; zmiana limitu `size`; stronicowanie kursorem; testy mutacyjne (S-45); przebudowa `PostsApiIT`.

## Architecture / Approach

Granica strony to jedna stała zapisana razem z maksymalnym `size` i sprawdzana adnotacją walidacji, którą obsługuje istniejący `ApiExceptionHandler`. Testy regresji dochodzą do `PostsApiIT` z istniejących pomocników `PostSeeds`. Zmiana w SPA to normalizacja `q` i granica `page` z wpisem w rejestrze reguł oraz testami.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Granica numeru strony (API) | 400 dla `page` ponad granicą na trzech listach, testy brzegu | Defekt na listach nieudanych i przebiegów nieuruchamiany w audycie; faza zaczyna od jego odtworzenia |
| 2. Testy regresji feedu (API) | Pięć testów w `PostsApiIT` z dowodem zepsucia | Zepsucie SQL może nie zaczerwienić testu; wtedy poprawiamy test |
| 3. NUL i ogromny `page` w linku feedu (SPA) | `normalizeQuery` usuwa U+0000, `?page=` ponad granicą daje stronę 0, testy i sprawdzenie na stronie | Dwie nowe kopie reguł API po stronie SPA (rejestr `rules.json`) |

**Prerequisites:** brak (`Prerequisites: —` w roadmapie); Docker dla `*IT`.
**Estimated effort:** ~1–2 sesje, 3 fazy.

## Open Risks & Assumptions

- Zakładamy, że wszystkie trzy listy mają `size ≤ 100`; test pilnuje relacji stałych.
- Jeśli czerwony test dla `FailedPostController` lub `PipelineRunController` okaże się zielony, ta lista nie miała defektu, a test zostaje jako zabezpieczenie.
- Fraza „NUL w linku” z roadmapy opisuje SPA; API już odrzucało NUL.

## Success Criteria (Summary)

- `GET` z `page` ponad granicą daje 400 na wszystkich trzech listach, a feed ma pięć testów, które potrafią zaczerwienieć.
- Link feedu z NUL albo ogromnym `?page=` nie zostawia SPA w błędzie.
