# S-11: utknięty przebieg sam wraca do obiegu — Plan Brief

> Full plan: `context/changes/stuck-run-self-heals/plan.md`
> Frame brief: `context/changes/stuck-run-self-heals/frame.md`

## What & Why

Rzeczywisty problem (z frame): każda bramka (`queue()` → 409 dla crona i admina, `/api/deploy-safety` dla bramki deployu) ufa wierszowi `queued`/`running` bez sprawdzenia, czy ktoś go jeszcze wykonuje, a jedyny mechanizm, który taki wiersz usuwa, działa przy starcie JVM — którego te same bramki, odpytując, nie dopuszczają. Skutek: nocny cron dostaje 409 przez 60 min i kończy CRASHED, a bramka deployu czeka 90 min na zombie, wstrzymując deploy, który by go usunął.

## Starting Point

Odzyskiwanie startowe (`PipelineStartupRecovery`) sprząta niedokończone przebiegi tylko przy starcie; watchdog widzi tylko przebiegi z pamięci własnej instancji i celowo nie pyta bazy w bezczynności (sen API na Railway). Okna sieroty są realne (overlap deployu ~10 s po odzyskiwaniu nowej instancji, `drainingSeconds` = 0), a nieudane odzyskiwanie startowe zdarzyło się na produkcji 2026-10-23. W bazie jest już sygnał żywotności: `last_activity_at` / `started_at` / `created_at`.

## Desired End State

Gdy ta instancja nie ma własnego przebiegu, a żaden aktywny przebieg nie miał aktywności przez `PIPELINE_STALL_TIMEOUT` (39 min na produkcji), pierwsze żądanie startu (cron albo admin) oznacza je `interrupted` z przyczyną `lost`, oddaje ich posty, powiadamia adminów i dostaje 202. `/api/deploy-safety` traktuje takie przebiegi jako bezpieczne. Każde żądanie startu oddaje też posty `assessing` po zakończonych przebiegach. SPA pokazuje „utracona instancja”.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Co jest problemem | Bramki ufają wierszowi bez żywotności; leczy tylko start | Zombie sam się podtrzymuje, bo odpytujący nie dają API zasnąć | Frame |
| Kogo boli | Nocny cron i bramka deployu | Wskazane przez użytkownika; admin w `/admin` poza celem | Frame |
| Miejsce wykrywania | Na żądanie w `queue()` pod blokadą doradczą | Zero ruchu w bezczynności; leczą ci, którzy dziś dostają 409 | Plan |
| Predykat sieroty | Pusty `own` i żaden aktywny przebieg bez aktywności w progu; wszystko albo nic | Kolejka jest jedna i sekwencyjna; żywy przebieg = żywa kolejka | Plan |
| Próg | `PIPELINE_STALL_TIMEOUT` | Ta sama miara co watchdog, bez nowej zmiennej | Plan |
| Zapis sprzątniętego | `interrupted` + nowa przyczyna `lost` (migracja `V11`) | Odróżnialne od zwykłego deployu (ton neutralny) — anomalia widoczna | Plan |
| A4 | Lecz posty (`releaseOrphans` przy każdym żądaniu), wyścig `claim` zostaw | Post wraca do obiegu bez zmiany reguły „no row locks” | Plan |
| `/api/deploy-safety` | Tylko odczyt, martwe = bezpieczne | Publiczny GET bez skutków ubocznych; deploy sam sprząta | Plan |

## Scope

**In scope:**
- Migracja `V11`, `InterruptionCause.LOST`, `RunRecorder.interruptOrphans`, sprzątanie i `releaseOrphans` w `queue()` (zatwierdzane mimo 409/400), powiadomienia i WARN.
- `PipelineStallSettings` jako jeden obiekt serwera dla watchdoga, kolejki i bramki.
- `/api/deploy-safety` z predykatem żywotności.
- SPA: typ, etykieta, ton, kitchen sink, mock.
- Dokumenty (`pipeline/CLAUDE.md`, `deploy-plan.md` S-11/S-14/S-06, `REPOSITORY_GUIDELINES.md`) i weryfikacja w PR env.

**Out of scope:**
- `FOR SHARE` w `claim` i dryf liczników (A4), zapytania watchdoga w bezczynności, kolumna dzierżawy, zmiany `trigger.sh`/`wait-safe.sh`, leczenie przez odczyty, ustawienia Railway (`overlapSeconds`/`drainingSeconds`).

## Architecture / Approach

Jedna instrukcja `UPDATE … WHERE status IN (queued, running) AND NOT EXISTS (aktywny przebieg z aktywnością w progu)` na zegarze bazy, w `queue()` pod `pg_advisory_xact_lock`, przed `existsActive`; po commicie WARN i `runEnded`. `deploy-safety` pyta `existsLiveActive(threshold)` tym samym kryterium. Wzorzec jak odzyskiwanie startowe (`interruptAll` + `releaseOrphans`), tylko wyzwalany żądaniem, nie startem.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Sprzątanie sierot w kolejce | `V11`, `lost`, sprzątanie w `queue()`, `OrphanedRunIT` | Sprzątanie wycofane razem z 409; próg wciekający do kontekstu komend operatora |
| 2. Bramka deployu ignoruje martwe | `deploy-safety` z predykatem, `DeploySafetyIT` | Rozjazd predykatu z kolejką |
| 3. SPA zna `lost` | Typ, etykieta, ton, kitchen sink/mock, Vitest | Minimalne |
| 4. Dokumenty i PR env | Runbooki prawdziwe; scenariusz z sierotą SQL-em | Dostęp SQL do PR env (`railway ssh`) |

**Prerequisites:** gałąź `feature/stuck-run-self-heals` ze świeżego `origin/main`, issue #70, PR env z bazą.
**Estimated effort:** ~2–3 sesje w 4 fazach.

## Open Risks & Assumptions

- Przebieg żywej instancji milczący dłużej niż próg zostanie sprzątnięty przez inną — założenie to samo co watchdoga (próg > najdłuższe wywołanie LLM + 5 min; fetch ma termin 10 min).
- W oknie overlapu właściciel sprzątniętego, zawieszonego przebiegu nie anuluje swojego oczekiwania — akceptowane, bo stara instancja zaraz ginie.
- Zombie młodszy niż próg nadal daje 409; cron (60 min) i bramka (90 min) ponawiają dłużej niż 39 min.
- Wycofanie wersji po pierwszym `lost` może wymagać poprawki do przodu (stary konwerter nie zna wartości).

## Success Criteria (Summary)

- Nocny cron przy zombie starszym niż próg dostaje 202, a nie godzinę 409; stary przebieg jest `interrupted`/`lost`, jego posty wracają.
- Bramka deployu przepuszcza deploy przy martwym przebiegu, a nadal czeka na żywy.
- Admin widzi „utracona instancja” i dostaje powiadomienie; API dalej zasypia w bezczynności.
