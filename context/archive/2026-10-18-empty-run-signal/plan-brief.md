# Pusty przebieg daje sygnał (S-08) — Plan Brief

> Full plan: `context/changes/empty-run-signal/plan.md`
> Research: `context/changes/empty-run-signal/research.md`

## What & Why

Przebieg, który nic nie pobrał albo pominął elementy o nieoczekiwanym kształcie, kończy się dziś `succeeded`: bez powiadomienia, z wyjściem crona 0 i z przesuniętą etykietą „Dane zaktualizowane”. To jedyne ustalenie audytu o wadze H (C1) i wprost łamie NFR „operator nie dowiaduje się o awarii pośrednio, przez brak nowych danych”. Zmiana API Wykopu dałaby co noc „sukces” z 0 postami.

## Starting Point

Status przebiegu liczy wyłącznie wynik oceny (`RunFlow.java:150-155`), a liczniki `fetched`/`skipped` są tylko zapisywane. Wszystkie sygnały (dzwonek, cron, świeżość, UI) czytają status, więc pusty przebieg przechodzi każdy z nich jako udany. `skipped` widać tylko w panelu admina. Ścieżka „krok rzuca wyjątek → `failed`” już daje pełny sygnał.

## Desired End State

- **Przebieg zakresu dat, w którym źródło nie dało żadnego posta do zapisu**, kończy się `failed` przy pobieraniu. Każdy admin dostaje powiadomienie, cron kończy się kodem 1 (push Railway), a etykieta stoi.
- **Pominięte elementy przy dobrych postach** dają ostrzeżenie: powiadomienie w dzwonku, ton ostrzeżenia i powód „pominięte elementy: N”. Cron kończy się 0, a etykieta się przesuwa.
- `skipped` widać w `/internal`, w linii crona i w logach API.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Co jest pustym przebiegiem | źródło przebiegu RANGE z `fetched − skipped = 0` (0 pobranych albo same pominięte) | Zaobserwowane przebiegi za 2 dni miały 74–864 elementów, zero to prawie na pewno awaria | Research + Plan |
| Waga pustego przebiegu | błąd (`failed`) | Wymaga pusha Railway i stojącej etykiety, czyli wyniku S-08 z roadmapy | Plan |
| Pominięte przy dobrych postach | ostrzeżenie: `needsAttention`, cron 0, etykieta się przesuwa | „crashed” nie może mylić się z „częściowo” (argument S-06 D2); dane są | Plan |
| Kształt zmiany | wyjątek w kroku pobierania, per źródło, z licznikami zapisanymi przed błędem | Istniejąca ścieżka `failed` daje wszystkie sygnały bez zmian w cronie, świeżości i SPA | Plan |
| Kontrakt `skipped` | `/internal` `RunState`, linia crona, końcowa linia API, WARN per źródło, `NotifiedRun` | Zamyka lukę C1 „`skipped` tylko w UI” | Plan |
| Cron vs `needsAttention` | kody wyjścia bez zmian, javadoc poprawiony; wyrównanie w S-12 (A5) | Zachowuje decyzję S-06; push tylko dla prawdziwych awarii | Plan |
| Pomiar „0 nowych” | odrzucony jako kryterium | Okno D-2…D-1 co noc pobiera D-2 drugi raz, więc `new = 0` jest legalne | Research |

## Scope

**In scope:**
- `NoPostsFetchedException` w `SourceIngest.fetch` (per źródło), zapis liczników w `PipelineAgent.fetch`
- `needsAttention` z `skipped`, `skipped` w `RunState`, `NotifiedRun` i linii końcowej API, WARN przy pominiętych
- `trigger.sh` z `skipped` (fallback `?`), `test.sh` z przypadkami `empty` i `skipped-warning`, README
- SPA: ton, etykieta i powód w `runs.ts`, `NotifiedRun`, kitchen sink, mock, testy, zrzuty
- Doprecyzowania PRD (z przebiegami admina), sekcja S-08 w `deploy-plan.md`, `REPOSITORY_GUIDELINES.md`, `pipeline/CLAUDE.md`
- `sources/CLAUDE.md`: drugie źródło dopisuje test „jedno źródło puste, drugie z postami” (dziś nie da się go napisać, `Source` ma tylko `WYKOP`)

**Out of scope:**
- nowy status przebiegu, zmiana kodów wyjścia crona, `needsAttention` w `/internal`
- progi „za mało postów” i udziału pominiętych; rozbicie `skipped` na przyczyny, RAW pominiętych
- kompletność stronicowania Wykopu i ponowienia (S-16), izolacja źródeł (Parked), kanał poza aplikacją (F-04), brakująca noc (S-13), dostarczalność powiadomień (S-12)

## Architecture / Approach

Dwie reguły w backendzie i propagacja jednego pola:
- **Błąd:** `SourceIngest.fetch` rzuca `NoPostsFetchedException`, gdy źródło nie dało posta. `PipelineAgent.fetch` zapisuje liczby z wyjątku i rzuca dalej. Istniejąca ścieżka błędu kroku daje `failed`, a z nim powiadomienie, exit 1 crona i stojącą świeżość.
- **Ostrzeżenie:** `needsAttention` dostaje `skipped`, więc `RunNotifications` wysyła powiadomienie bez zmiany statusu.

Pole `skipped` płynie dalej do `/internal` → `trigger.sh`, do `NotifiedRun` → dzwonka i do `runs.ts` (ton, etykieta, powód).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend | pusty RANGE = `failed` z licznikami; pominięte = `needsAttention`; `skipped` w kontraktach i logach | liczniki zgubione przy błędzie kroku (kolejność zapisu) |
| 2. Cron | `skipped` w linii `trigger.sh`, przypadki `empty`/`skipped-warning` w atrapie | przypięte linie logu w `test.sh` |
| 3. SPA | ton, etykieta i powód ostrzeżenia w liście, na stronie przebiegu i w dzwonku | fixture'y testów z niemożliwą kombinacją `skipped`/`needsAttention` |
| 4. Docs + PR env | PRD, deploy-plan, wytyczne; weryfikacja na żywym Wykopie | pustego zakresu może nie dać się wymusić w PR env |

**Prerequisites:** brak (S-08 nie ma warunków wstępnych). Branch `feature/empty-run-signal`, issue S-08 i PR zakłada faza 1 `/10x-implement`.
**Estimated effort:** ~2–3 sesje, 4 fazy; bez migracji.

## Open Risks & Assumptions

- Pusty dzień na #gielda nigdy nie był widziany. Gdyby się zdarzył, da fałszywy alarm (`failed`, push). Uznane za akceptowalne.
- W PR env może nie dać się wymusić pustego zakresu (Wykop może zwrócić elementy dla starej daty). Pokrycie zostaje wtedy przy `PipelineAgentIT` i `test.sh`.
- Przy dwóch źródłach puste źródło A zatrzyma zapis postów źródła B. To zgodne z dzisiejszą zasadą „błąd źródła = błąd kroku”; izolacja źródeł jest Parked. Reguła jest pisana per źródło, a jej test dla dwóch źródeł dopisze zmiana dodająca drugie źródło (wymóg w `sources/CLAUDE.md`).
- Przebieg admina za zakres bez postów (np. dziś tuż po północy) też kończy się `failed` i powiadamia adminów; opisane w PRD i runbooku.

## Success Criteria (Summary)

- Pusty przebieg zakresu daje powiadomienie w dzwonku, push Railway (cron exit 1) i stojącą etykietę świeżości.
- Pominięte elementy przy dobrych postach dają powiadomienie i widoczny powód, bez fałszywego crasha crona.
- Logi crona i API pokazują `skipped`; zwykłe noce zachowują się jak dotąd.
