# Filtrowanie po ocenie LLM (S-04) — Plan Brief

> Full plan: `context/changes/filter-by-assessment/plan.md`
> Research: `context/changes/filter-by-assessment/research.md`

## What & Why

Użytkownik zawęża feed po spółce lub temacie (tekst dopasowany do fragmentu, którym post wskazuje cel), po rodzaju celu i po stanowisku autora wobec tego celu (FR-011a, must-have). Stanowisko jest w kontrakcie V2 przypisane do konkretnego celu, więc filtr musi łączyć tekst, rodzaj i stanowisko **tego samego** celu, a nie dowolnych celów posta (ryzyko z roadmapy).

## Starting Point

`GET /api/posts` stronicuje ocenione posty, a potem dociąga ich najnowszą ocenę `ok`; cele leżą w JSONB `assessments.output`. Feed po #48 (`feed-view-ui-hardening`) ma jedno źródło stanu strony, numerowane wczytania, stany ładowania/błędu/pustki i mock `/api/posts`; nie ma pól formularza, a karta nie pokazuje rodzaju celu.

## Desired End State

Nad listą formularz „Spółka lub temat”, „Rodzaj celu”, „Stanowisko”, „Filtruj” / „Wyczyść filtry”; filtr żyje w adresie (`/?q=cdr&kind=company&direction=bullish`). Linia wyniku „Ocenione posty: N” albo „Pasujące do filtra: N”, pusty wynik filtra „Żaden post nie pasuje do filtra.” z wyczyszczeniem. Karta pokazuje rodzaj przy każdym celu („CDR · spółka — wzrostowo”). Bez filtra feed działa jak dziś.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kolejność wobec #48 | S-04 po merge `feed-view-ui-hardening` | #48 przepisał `FeedView`/`PostCard`/mock i przekazał S-04 D2 i D3. | Plan (użytkownik) |
| „Ten sam cel” | Wszystkie warunki na jednym elemencie `targets[]` (jeden `EXISTS`) | Stanowisko jest zagnieżdżone w celu; osobne warunki dają błąd z ryzyka roadmapy. | Research |
| Która ocena | Najnowsza `ok` (jak karta), bez unikalnego indeksu | Spójne z tym, co widać; dziś i tak jedna `ok` na post. | Research / Plan |
| Pola filtra | Tekst + rodzaj + kierunek, po jednej wartości (`<select>` z „dowolny”) | Natywne kontrolki, jeden parametr na pole, krótki formularz przy 375 px. | Plan |
| Kierunki | Wszystkie 6 z etykietami karty; bez filtra ostrożności | Brak ukrytych wartości; „bez stanowiska” to połowa celów w próbce. | Plan |
| Dopasowanie tekstu | Podciąg bez wielkości liter (`ILIKE`, ucieczka `%`/`_`), bez `unaccent` | Bez migracji; „orlen” znajduje Orlenu/#orlen, „żabka” — ŻABKA; „zabka” ≠ „Żabka” przyjęte. | Plan |
| Układ nad listą (D3) | Formularz + linia wyniku, pager tylko na dole, strona 50 | Liczba wyników odpowiada na filtr; bez zdublowanych kontrolek. | Plan |
| Karta | Rodzaj przy każdym celu, bez wyróżniania pasującego | Widać, czemu post pasuje; brak drugiej implementacji dopasowania w SPA. | Plan |
| API | `q`, `kind`, `direction` (małe litery, konwerter po `value()`); 400 dla nieznanej wartości i `q` > 100 | Te same wartości co w `assessment.output`; istniejąca ścieżka `invalid_request`. | Plan |
| Zapytanie | Bez filtra dzisiejsze `findPage`; z filtrem natywne z `countQuery` i własnym `ORDER BY` | Zero zmian zachowania bez filtra; `total` liczony po filtrze. | Plan |
| Zachowanie formularza | Zatwierdzenie zeruje stronę, bez przewijania i przenoszenia fokusu; wynik w `role="status"` | Fokus zostaje przy filtrze, czytnik ogłasza liczbę wyników. | Plan |
| Pola i komponent | Globalny styl `search`/`select` w `style.css`; `FeedFilter.vue` w `src/components/` | Wzorzec pola daty; formularz testowalny i na kitchen sink. | Plan |

## Scope

**In scope:** parametry i zapytanie `GET /api/posts`, konwerter enumów, testy IT; `fetchPosts` z filtrem, filtr w adresie, `FeedFilter`, linia wyniku, pusty wynik filtra, rodzaj na karcie, etykiety rodzajów, mock z filtrowaniem, kitchen sink, macierz 7 stanów, reguła w `REPOSITORY_GUIDELINES.md`, weryfikacja w PR env.

**Out of scope:** wielokrotny wybór, filtr ostrożności, `unaccent`, synonimy i rozpoznawanie spółek, wyróżnianie pasującego celu, drugi pager i zmiana rozmiaru strony, inne filtry i sortowanie, migracje/indeksy/denormalizacja, `LoginView`, `AppHeader` przy 375 px.

## Architecture / Approach

`PostController.list` buduje `PostFilter(q, kind, direction)`; `PostRepository.findAssessedPage` bez filtra woła dzisiejsze `findPage`, z filtrem — natywne zapytanie: posty `assessed` ⨝ ich najnowsza ocena `ok` (`LATERAL … LIMIT 1`) i `EXISTS` po `output -> 'targets'` z trzema warunkami na tym samym elemencie, `ORDER BY published_at DESC, id DESC`, osobne zliczenie. SPA: `filterFromQuery(route.query)` → `fetchPosts(page, 50, filter)`; `FeedFilter` emituje `apply`/`clear` → `router.push`; klucz wczytania = strona + filtr, reszta mechaniki stronicowania z #48 bez zmian.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. API — filtr w `GET /api/posts` | Parametry, natywne zapytanie, testy IT (ten sam cel, polskie litery, najnowsza `ok`, `total`, 400) | Natywny SQL: sortowanie, typy parametrów `null`, ucieczka `ILIKE` |
| 2. SPA — filtr w feedzie | Klient, filtr w adresie, `FeedFilter`, linia wyniku, pusty wynik, rodzaj na karcie, mock, testy | Klucz wczytania strona + filtr bez regresji stronicowania z #48 |
| 3. Stany, zrzuty i PR env | Macierz 7 stanów, reguła UI, filtr na prawdziwych danych i lokalizacja bazy Railway | Lokalizacja bazy Railway inna niż lokalna (składanie wielkości polskich liter) |

**Prerequisites:** #48 zmergowany (`404679f`); gałąź `feature/filter-by-assessment` na aktualnym `origin/main`; issue #17 (`S-04: …`); PR env z danymi (przebieg zakresu dat w `/admin`).
**Estimated effort:** ~2–3 sesje w 3 fazach.

## Open Risks & Assumptions

- Lokalizacja produkcyjnej bazy nie była sprawdzana; lokalnie i w testach `en_US.utf8` składa wielkość polskich liter. Faza 3 sprawdza PR env, a runbook w issue #17 — produkcję po merge.
- PR #50 (S-05) i przyszłe S-03 zmieniają `FeedView`, `PostCard`, `FeedView.test.ts`, mock i `PostController`; konflikt rozwiązuje zmiana mergowana później.
- Wyszukiwanie tekstowe nie łączy synonimów i form bez polskich znaków („PKN” ≠ „Orlen”, „zabka” ≠ „Żabka”) — świadomie przyjęte w PRD.
- Filtr przegląda JSONB bez indeksu; przy dzisiejszym wolumenie tanie, przy dziesiątkach tysięcy postów — osobna zmiana z pomiarem.

## Success Criteria (Summary)

- „CDR” + „wzrostowo” nie zwraca posta, w którym CDR jest bez stanowiska, a wzrostowo jest inny cel; „orlen” znajduje Orlen, Orlenu i #orlen.
- Filtr przeżywa odświeżenie, link i wstecz/dalej; pusty wynik filtra jest inny niż brak postów i daje się wyczyścić jednym przyciskiem.
- Formularz i nowe stany przechodzą macierz 7 stanów w obu motywach i przy 375 px oraz działają w Railway PR env na prawdziwych danych.
