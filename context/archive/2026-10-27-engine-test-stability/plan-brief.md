# S-26: testy silnika bez zależności od zegara — Plan Brief

> Full plan: `context/changes/engine-test-stability/plan.md`
> Research: `context/changes/post-m2-audit/research.md` (audyt po M-2, ustalenia E1, E2, E6)

## What & Why

Testy silnika (`api/pipeline/**`) padają losowo na współdzielonym self-hosted runnerze, a S-20 i S-27 będą zmieniać ten sam obszar i potrzebują wiarygodnego CI. Plan usuwa zegar jako warunek poprawności z testów silnika, dodaje zrzut wątków przy przekroczeniu czasu, naprawia i przypina dwa okna fałszywych alarmów po S-17 i doprowadza `pipeline/CLAUDE.md` do zgodności z kodem.

## Starting Point

Test anulowania dyspozytora ma znany wyścig (test woła `cancel`, zanim dyspozytor zgłosi drugie zadanie) i nieznaną przyczynę Timeoutu z runu 39133095273, bo nic nie zrzuca wątków. Testy jednostkowe mają limit 10 s w każdym oczekiwaniu, kilka ignorowanych wyników `await` i aktywne czekanie; IT mierzą „krok skończył się < 10 s” i mają zapasy 2–3 s wobec progu watchdoga 5 s. Spóźnione przerwanie watchdoga może dać fałszywe ERROR (`stillRunning`) i fałszywy `NOTIFICATION_FAILED` w Telegramie (`runEnded`).

## Desired End State

Testy silnika czekają na zdarzenia; zawieszenie kończy klasowy `@Timeout` ze zrzutem wątków w logu joba `backend`. Wyścig E2 nie może wystąpić. Przebieg zakończony przez watchdoga nie daje fałszywego ERROR ani alertu, a prawdziwa awaria nadal alarmuje. `pipeline/CLAUDE.md` mówi prawdę o kodzie i o zasadach testów.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Okna E1 | Naprawić oba (ponowny odczyt z wyczyszczoną flagą) + testy oczekujące INFO bez alertu | Usuwa fałszywy alarm w Telegramie tym samym wzorcem, którego już używa `close()`. | Plan |
| Zakres IT | Testy jednostkowe w pełni + w IT kolejność zamiast „< 10 s”, sprawdzane `await`, szersze zapasy | Obejmuje najryzykowniejsze miejsca z inwentarza bez zmiany kodu silnika i SQL. | Plan |
| Zrzut wątków | `junit-platform.properties` (`threaddump.enabled`) + `@Timeout` na klasach silnika, zrzut w logu joba; `ci.yml` bez zmian | Odpowiada na niewiadomą z runu 39133095273 przy następnym wystąpieniu bez ruszania CI. | Plan |
| Wyścig E2 | Poprawka tylko w teście (czekać na zgłoszenie obu zadań) | Produkt jest poprawny: spóźnione zadanie widzi `cancelled` i kończy się `NotTaken`. | Research |
| `OrphanedRunIT` | Próg 5 s → 60 s | Żaden scenariusz nie czeka na próg (martwe przebiegi to `now() - 1 h`), więc zapas rośnie z 3 s do 58 s bez spowolnienia. | Plan |
| Test bezpiecznika | Pauza 3 s → 2 s | Każde wywołanie nadal krótsze od progu, trzy razem dłuższe — dowód zostaje, zapas rośnie z ~2 s do ~3 s. | Plan |
| Pętle pomocnicze IT | Zostają z limitami 30–60 s | To tylko ochrona przed zawieszeniem, która mówi, na czym test utknął. | Plan |

## Scope

**In scope:**
- `junit-platform.properties` ze zrzutem wątków; `@Timeout` na klasach testów silnika (unit i IT)
- `ConcurrentAssessmentDispatcherTest`, `PipelineRunServiceTest`, `RunWatchdogTest` bez zegara jako warunku
- Poprawki okien E1 w `PipelineRunService.stillRunning` i `RunNotifications.runEnded` + testy
- `RunWatchdogIT`, `PipelineRunIT`, `OrphanedRunIT` i IT z bramką Wykopu
- Sześć nieścisłości `pipeline/CLAUDE.md` (E6) i opis nowych zasad testów

**Out of scope:**
- Sterowany czas w IT (zegar watchdoga, `now()` bazy), zmiany `ci.yml`, limit forka surefire
- Losowa kolejność testów jednostkowych, globalny domyślny limit czasu
- Pula Hikari = 1 w IT, szew przeplotu, strażniki E4, poprawki kodu A8/B10 (tylko opis)
- Testy, które mogą fałszywie przejść, ale nie padają losowo

## Architecture / Approach

Jedna zasada dla testów silnika: czekaj na zdarzenie bez własnego limitu, a limit i zrzut wątków daje klasowy `@Timeout`; każde ograniczone oczekiwanie, które zostaje, ma sprawdzony wynik; „szybko” wyrażaj kolejnością zdarzeń (krok skończył się, zanim wstrzymane wywołanie dostało odpowiedź), nie czasem trwania. Poprawki E1: odczyt bazy, który rzucił na przerwanym wątku, jest powtarzany raz z wyczyszczoną flagą, potem flaga wraca do stanu z chwili błędu, a reguła flagi w `execute` się nie zmienia.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Zrzut wątków i testy jednostkowe bez zegara | Zrzut przy `@Timeout`, koniec wyścigu E2, deterministyczne testy dyspozytora, serwisu i watchdoga | Pula sygnalizująca zgłoszenie zadania musi zachować semantykę puli z dwoma wątkami |
| 2. Okna fałszywych alarmów E1 | Ponowny odczyt w `stillRunning` i `runEnded` + testy | Flaga przerwania przywrócona przez `runEnded` nie może przejść na następny przebieg |
| 3. IT bez zegara jako warunku | Kolejność zamiast „< 10 s”, sprawdzane `await`, próg 60 s w `OrphanedRunIT`, pauza 2 s | Wywołanie czekające bez limitu musi zwalniać `settle()` także po czerwonym teście |
| 4. Notatki silnika | `pipeline/CLAUDE.md` zgodny z kodem, CI na PR zielone | — |

**Prerequisites:** gałąź `feature/engine-test-stability` od świeżego `origin/main`, issue S-26; Docker lokalnie dla `./mvnw -B verify`.
**Estimated effort:** ~1–2 sesje w 4 fazach.

## Open Risks & Assumptions

- Przyczyna Timeoutu z runu 39133095273 zostaje nieznana do następnego wystąpienia; plan zapewnia tylko zrzut wątków, który ją pokaże.
- Scenariusze watchdoga w `RunWatchdogIT` nadal mierzą prawdziwy czas (próg 5 s): zapas ~2,5×, nie determinizm.
- Zakładamy, że JUnit 6.0.3 wypisuje zrzut na stdout, a surefire/failsafe przepuszczają go do logu joba — faza 1 sprawdza to celowym zawieszeniem.

## Success Criteria (Summary)

- `./mvnw -B verify` zielone lokalnie i na PR; testy jednostkowe silnika 20/20 zielone pod obciążeniem CPU.
- Celowe złamania (pauza w dyspozytorze, zawieszenie, brak ponownego odczytu, brak `cancel(true)`) dają oczekiwany wynik.
- Spóźnione przerwanie watchdoga nie daje już fałszywego ERROR ani `NOTIFICATION_FAILED`.
