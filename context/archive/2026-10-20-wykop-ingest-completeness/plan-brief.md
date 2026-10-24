# S-16: Wykop nie gubi postów po cichu — Plan Brief

> Full plan: `context/changes/wykop-ingest-completeness/plan.md`

## What & Why

Pobieranie z Wykopu potrafi dziś skończyć się „kompletne”, choć zgubiło elementy (pusta strona przed `total`, duplikaty liczone jako nowe, przesunięcie stron po usunięciu wpisu), a jeden chwilowy błąd Wykopu (429, 5xx, transport) kończy całą noc `failed`. S-16 (audyt B5, B6) zamyka obie luki tylko w źródle — bez zmian w silniku, porcie i API.

## Starting Point

`WykopRepository` stronicuje do `pagination.total`, licząc każdy element (także duplikat), i kończy na pustej stronie bez błędu. `WykopApiClient` celowo nie ponawia niczego poza jednym odświeżeniem tokenu po 401. Akcja `fetch` jest `FIRE_ONCE`, a watchdog nie widzi postępu w trakcie pobierania — po 15 min ciszy zatrzymuje przebieg.

## Desired End State

Spokojna noc wygląda jak dziś (te same żądania i posty). Niespójne stronicowanie jest rozpoznane i naprawione jednym ponownym czytaniem albo kończy się czytelnym błędem `INCOMPLETE_PAGING` — nigdy wynikiem „kompletnym” z dziurą. Chwilowa awaria Wykopu jest ponawiana jawnie (WARN) i noc kończy się `succeeded`; trwała kończy się `failed` z prawdziwą przyczyną, zawsze przed watchdogiem. Niewiadoma „czy wyszukiwarka zwraca usunięte elementy” jest zamknięta pomiarem na żywym API.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Wykrywanie niekompletności | Unikalne elementy + 3 warunki niespójności: pusta/krótka strona przed `total`, unikalnych < `total`, zmiana `total` między stronami | Tylko zmiana `total` łapie przesunięcie po usunięciu (50 + 49 = 99 wygląda na komplet) | Plan |
| Naprawa | Jedno ponowne czytanie zasobu, które zastępuje pierwsze | Usunięcie w trakcie ~3 s stronicowania jest rzadkie, a każdy dzień i tak jest pobierany dwie noce; 3 przejścia i okna czasu to przesada | Plan (użytkownik) |
| Wynik, gdy nadal niespójne | Błąd pobierania `INCOMPLETE_PAGING` → przebieg `failed` | Nigdy „kompletne” z dziurą; istniejąca ścieżka powiadomień, bez zmiany portu | Plan |
| Miejsce ponowień | `WykopApiClient` (warstwa transportu źródła), GET i `POST auth` | Zła strona kosztuje jedno żądanie, `FIRE_ONCE` nietknięte | Plan |
| Polityka ponowień | 429/5xx/transport, do 3 prób, `Retry-After` ≤ 30 s, inaczej 2 s / 4 s, WARN przy każdym | Wzorce `TransportRetry`, jawnie, nie „po cichu” | Plan |
| Granica czasu | Termin 10 min na całe pobranie, sprawdzany przed każdą próbą i czekaniem; test przypina relację z `MIN_DEFAULT` 15 min | Gwarancja, że pobieranie kończy się przed watchdogiem z prawdziwą przyczyną | Plan |
| Usunięte elementy | Ręczna sonda na żywym API (`WykopPagingProbe`, measurement), wynik w `change.md` | Zamyka niewiadomą z roadmapy na prawdziwych danych | Plan |
| Konfiguracja | Stałe w kodzie, bez zmiennych `WYKOP_*` | Zero zmian IaC i w `.railway/railway.ts` | Plan |

## Scope

**In scope:** sonda pomiarowa; ponowienia i termin w `WykopApiClient`; spójność stronicowania w `WykopRepository`; nowe `WykopFailure` (`DEADLINE_EXCEEDED`, `INCOMPLETE_PAGING`); `LocalWykopServer` z nagłówkami; testy z liczeniem żądań; dokumentacja `sources/`, `pipeline/`, `REPOSITORY_GUIDELINES.md`; nowa lekcja; weryfikacja w PR env.

**Out of scope:** okna czasu ≤ 1 strona; zapis z ostrzeżeniem przy brakach; puls postępu przez port `SourceFetcher`; zmienne środowiskowe do strojenia; ponowienia w silniku; wspólna klasa ponowień z `llm/`; izolacja źródeł, D-3+, DST (B11).

## Architecture / Approach

`WykopRepository.itemsAddedBetween` wyznacza termin (`client.deadline(10 min)`) i dla każdego zasobu robi przejście stron; przy niespójności — drugie przejście od strony 1, a przy drugiej niespójności błąd. Każde `client.get(…, deadline)` ponawia chwilowe błędy z czekaniem (wstrzykiwany `Sleeper` i zegar), ale nie zaczyna próby ani czekania, które przekroczyłoby termin. Pipeline widzi tylko to, co dziś: `Fetched` albo wyjątek.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Sonda na żywym API | Rozkład `status`, częstość niespójności, wniosek w `change.md` | Elementy nie-`visible` z tekstem → bramka decyzyjna przed fazą 3 |
| 2. Ponowienia i termin w kliencie | 429/5xx/transport ponawiane w terminie 10 min, testy z liczeniem żądań | Wyciek sekretu w nowym logu WARN; nieznane limity Wykopu |
| 3. Spójność stronicowania | Unikalne, 3 warunki, jedno ponowne czytanie, `INCOMPLETE_PAGING` | Fałszywa niespójność, która co noc wymusza drugie czytanie |
| 4. Domknięcie | Lekcja, przebieg w PR env, issue S-16 | Przebieg w PR env w oknie nocnym lub przy `RUNNING` |

**Prerequisites:** klucze Wykopu w powłoce operatora (sonda); PR env z kluczami Wykopu (job `railway-config`).
**Estimated effort:** ~2–3 sesje w 4 fazach (faza 1 i 4 małe, głównie ręczne).

## Open Risks & Assumptions

- Limity Wykopu są nieznane; ponowienia (≤ 3 na wymianę, czekanie ≥ 2 s) nie zwiększają tempa ponad dzisiejszą pauzę 300 ms, ale PR env i produkcja dzielą klucze.
- `PIPELINE_STALL_TIMEOUT` ustawiony ręcznie poniżej 15 min nadal może zatrzymać pobieranie przed terminem (dziś tylko WARN przy starcie).
- Zakładamy, że `total` jest stały w spokojnej odpowiedzi; sonda to sprawdzi — jeśli nie, warunek (c) wymaga korekty przed fazą 3.

## Success Criteria (Summary)

- Przesunięcie po usunięciu, pusta strona przed `total` i duplikaty nie dają już wyniku „kompletnego” — test na każdy przypadek.
- Chwilowe 429/5xx/transport nie zabijają nocy; trwała awaria kończy się `failed` z prawdziwą przyczyną w około 10 min + 2 × time-out żądania, przed watchdogiem.
- `./mvnw -B verify` i CI zielone; przebieg w PR env `succeeded` z `fetched` zgodnym z `total`.
