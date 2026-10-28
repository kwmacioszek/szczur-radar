# S-23: zamknięte zbiory i kolumny posta pilnowane w CI — Plan Brief

> Full plan: `context/changes/closed-sets-guard/plan.md`
> Research: `context/changes/post-m2-audit/research.md` (E4) i `context/domain/domain-distillation.md` (R-17, wiersz 4)

## What & Why

Dziś nowa stała enumu zapisywanego w bazie bez migracji `CHECK` (np. `Source.REDDIT` w S-27) wychodzi dopiero naruszeniem `CHECK` w nocnym przebiegu na produkcji. Tak samo kolumna dodana do `posts`, której nie resetują ani nie ustawiają warunkowe zapisy stanu w `PostTransitions`. Oba błędy przechodzą testy i kompilację. S-23 dodaje dwa testy integracyjne, które łapią je w CI, zanim cokolwiek trafi na `main`.

## Starting Point

20 enumów (22 pary enum → kolumna, `Source` w trzech kolumnach) ma dziś `CHECK` równy swoim wartościom, ale żaden test tego nie sprawdza: `MigrationsIT` testuje tylko odrzucenie jednej nieznanej wartości. `posts` ma 21 kolumn (15 ustawianych raz, 6 stanu próby), zapisywanych przez jeden `INSERT` JPA i 7 metod `UPDATE` w `PostTransitions`; nikt nie testuje `updated_at` ani tego, że kolumny ustawiane raz przetrwają przejście.

## Desired End State

Autor nowego enumu, nowej migracji z `CHECK` albo nowej kolumny posta dostaje czerwony test z komunikatem, co dopisać i gdzie (migracja, rejestr testu, `PostTransitions`). Dziś oba testy są zielone. Opisy pakietów nazywają test, który pilnuje każdej reguły.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Ścisłość enum ↔ `CHECK` | równość zbiorów | łapie też migrację z wartością bez stałej; zgodne z append-only R-17 | Plan |
| Lista par i nowe enumy | jawny rejestr 22 par + kompletność z metamodelu JPA i `pg_constraint` | czytelne, standardowe API JPA, łapie nowe pole-enum i nowy `CHECK` bez enumu | Plan |
| Siła strażnika `posts` | klasyfikacja kolumn + macierz 7 przejść | nowa kolumna stanu wymaga decyzji dla każdego przejścia, nie tylko etykiety | Plan |
| Zakres R-19 | tylko `posts`; `CHECK` przejść i `pipeline_runs` poza zakresem | zgodne z outcome slice'a i lekcją o zakresie; zero zmian schematu | Plan |
| Złożone `CHECK`-i z literałami | nie liczone | to reguła semantyczna, nie członkostwo w zbiorze | Plan |
| Weryfikacja | CI + celowe złamania, bez PR env | zmiana wyłącznie testowa | Research (E4: „mały i czysto testowy”) |

## Scope

**In scope:**
- `test/persistence/ClosedSetsIT` — rejestr, równość z `CHECK`, dwie kontrole kompletności, test parsera
- `test/pipeline/state/PostColumnsIT` — klasyfikacja 21 kolumn, scenariusze 7 przejść (pełny wiersz przed/po, `updated_at`)
- Jedno zdanie w `sources/`, `llm/`, `agents/assessor/`, `pipeline/CLAUDE.md`, `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`

**Out of scope:**
- `CHECK`/trigger przejść stanu przebiegu (wiersz 6 destylacji), strażnik kolumn `pipeline_runs`
- Złożone `CHECK`-i, enumy w JSONB, stałe wersji bez `CHECK`
- Kod produkcyjny, migracje, `MigrationsIT`, PR env, `deploy-plan.md`

## Architecture / Approach

Każdy strażnik to test na prawdziwym Postgresie po migracjach (adnotacje jak `MigrationsIT`, współdzielony kontekst i kontener), z jawnym oczekiwaniem w kodzie testu i kontrolą kompletności wobec rzeczywistości: metamodel JPA i `pg_constraint` dla enumów, `information_schema` i metody `PostTransitions` dla posta. Parser `CHECK` obsługuje trzy postacie `pg_get_constraintdef` (lista, jedna wartość, tablica `<@`). `PostColumnsIT` nie jest `@Transactional`, bo `now()` to czas początku transakcji.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Strażnik zamkniętych zbiorów | `ClosedSetsIT` + dopiski w opisach | parser `CHECK` dający pusty zbiór „na zielono” — pusty zbiór to błąd, parser ma własny test |
| 2. Strażnik kolumn posta | `PostColumnsIT` + dopisek w `pipeline/CLAUDE.md` | macierz utrwalająca obecny błąd zamiast reguły — przegląd ręczny względem opisu przejść |

**Prerequisites:** Docker (Testcontainers), gałąź `feature/closed-sets-guard` z `origin/main`, issue #117.
**Estimated effort:** ~1 sesja, 2 fazy, 2 commity + docs.

## Open Risks & Assumptions

- Zakładamy, że Postgres 18 zwraca definicje `CHECK` w tych samych trzech postaciach co dziś; zmiana formatu da czerwony test parsera, nie cichy przepust.
- `PostColumnsIT` bez `@Transactional` musi izolować scenariusze własnymi id (inne testy na tym kontekście nie mogą zobaczyć jego wierszy jako przeszkody).

## Success Criteria (Summary)

- `./mvnw -B verify` i CI zielone; oba testy przechodzą same.
- `Source.REDDIT` bez migracji, nowa kolumna `posts` bez klasyfikacji i `record` bez resetu `technical_failures` dają czerwone testy z czytelnym komunikatem.
