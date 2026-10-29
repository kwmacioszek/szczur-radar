# S-20: awaria albo cisza jednego źródła nie psuje przebiegu pozostałych — Plan Brief

> Full plan: `context/changes/source-isolation/plan.md`
> Research: `context/changes/post-m2-audit/research.md` (S1, S2, S5, „Testowalność drugiego źródła”) i `context/domain/domain-distillation.md` (R-05, D-01, kandydat #1) — zamiast osobnego `research.md`

## What & Why

Krok `fetch` pobiera źródła po kolei bez izolacji: wyjątek jednego kończy przebieg `failed` bez liczników, a źródło bez posta wywraca po pętli cały krok razem z postami pozostałych. Między źródłami nic nie zgłasza postępu strażnikowi, liczniki są sumami, a pokrycie dnia bierze listę żądanych źródeł. Po dodaniu Reddita cichy subreddit zrobiłby z nocy Wykopu `failed`, a „Dni bez danych” pokazałyby lukę Wykopu. NFR „nowe źródło zmienia tylko pozyskanie i mapowanie” jest dziś prawdziwy w strukturze kodu, nie w zachowaniu silnika — to gwiazda przewodnia M-3.

## Starting Point

`SourceIngest.fetch` zbiera posty i sumy z pętli po źródłach; `RunSequence.fetch` łapie tylko `NoPostsFetchedException` i zapisuje sumy przed porażką (S-08). Status przebiegu liczy wyłącznie ocena (`AssessedBatch.status()`), a `attentionReasons` to czysta funkcja wiersza `pipeline_runs`. `MissingDays` liczy pokrycie z `run.sources` przebiegów `succeeded`/`partial` z założeniem S-13, że taki przebieg oznacza „każde źródło dało post”. `Source` ma jedną stałą, fetchery są kluczowane po `Source`, a `ClosedSetsIT` wymaga migracji `CHECK` dla każdej nowej stałej; testy mają tylko jedno źródło.

## Desired End State

Przebieg zakresu z kilkoma źródłami zapisuje i ocenia posty źródeł, które dały posty, gdy inne padło albo milczy, i kończy się `partial` z powodem `source_failed` / `source_empty` (dzwonek, Telegram `RUN_NEEDS_ATTENTION`, cron kod 0); `failed` tylko wtedy, gdy żadne źródło nie dało posta (S-08). Wynik każdego źródła jest trwały w `pipeline_run_sources` i zapisany tuż po tym źródle, co jest postępem dla strażnika. Dzień jest pokryty tylko dla źródła z wynikiem `succeeded`, historia dostaje backfill. Historyczne puste przebiegi dostają `empty` i przestają pokrywać dni lub wyznaczać pierwszy pokryty dzień źródła; lista „Dni bez danych” może się przez to zmienić — to zamierzona korekta historii. Admin widzi na stronie przebiegu wynik per źródło. `reddit` jest zarejestrowane w zbiorach `CHECK` bez kodu źródła, więc testy mają drugie źródło, a dla admina i crona nic się nie zmienia.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kształt izolacji | Jeden przebieg, wynik per źródło w nowej tabeli `pipeline_run_sources` (nie: przebieg na źródło, nie: krok `fetch` per źródło) | Noc zostaje jednym wierszem w `/admin`, kod wyjścia crona dalej znaczy „noc nic nie przyniosła”, a awaria źródła jest „częściowym błędem” z PRD, dla którego S-18 zbudowało kanał | Plan (wywiad) |
| Status przebiegu z padłym albo pustym źródłem przy zdrowym drugim | `partial` z powodami `source_failed` / `source_empty`; `failed` tylko bez żadnego posta | Posty zdrowego źródła liczą się do świeżości i pokrycia jak przy każdym `partial`; S-08 zostaje dla przypadku bez postów | Plan (wywiad) |
| Alarm dla cichego źródła | Jak każdy powód: dzwonek + `RUN_NEEDS_ATTENTION` w Telegramie | Jedna reguła z S-22 zostaje jedną; S-08 ustaliło, że zero postów w dwa dni to niemal na pewno awaria | Plan (wywiad) |
| Pokrycie dnia | Z wierszy `succeeded` przebiegów `succeeded`/`partial`, backfill historii z kroku `fetch` w tej samej migracji; puste historyczne przebiegi stają się `empty` | Zachowujemy pokrycie udanych pobrań z postami i korygujemy historię pustych pobrań, zamiast gwarantować identyczną listę braków | Plan + triage F3 |
| Postęp dla strażnika | Zapis wyniku źródła tuż po nim, warunkowy na `running`, przesuwa `last_activity_at`; port `SourceFetcher` bez zmian | S-06 odrzuciło puls w porcie; zapis między źródłami wystarcza, bo każde źródło ma termin poniżej `MIN_DEFAULT` | Research (audyt S2) + Plan |
| Reguła uwagi | Liczniki `sources_failed`/`sources_empty` w `pipeline_runs`; kody po `interrupted`, przed `dead_posts` | `attentionReasons` zostaje czystą funkcją wiersza (wzorzec `dead`, `skipped`); pobranie przed oceną | Plan |
| Drugie źródło w testach | `Source.REDDIT` zarejestrowane (stała + cztery zbiory `CHECK`) bez fetchera; fałszywy fetcher w `src/test`, domyślnie niekonfigurowany | Enum jest zamknięty i kluczuje fetchery, `ClosedSetsIT` wymaga migracji; `/sources` i istniejące scenariusze bez zmian | Research (audyt, testowalność) + Plan |
| Kontrakt crona i alertu | Bez zmian (kody wyjścia, klucze `RunState`, `attentionDetail`) | S-18 opiera „pomiń dubel crona” na kodach crona; nazwę źródła niesie `error` przebiegu, który alert cytuje | Plan |
| Nieudany zapis wyniku źródła | Kończy krok i przebieg (bez tłumienia z WARN jak w S-08) | Baza niedostępna i tak zatrzyma zapis postów; zapis per źródło jest wewnątrz kroku | Plan |
| Ograniczenia `CHECK` status ↔ liczniki | Nie dodajemy (tylko nieujemność, `skipped <= fetched`, `failed ⇔ error`) | Dane historyczne mogłyby zatrzymać `migrate` na produkcji; pilnują testy | Plan |

## Scope

**In scope:**
- migracja V12: `pipeline_run_sources`, liczniki `sources_failed`/`sources_empty`, `reddit` w zbiorach `CHECK`, backfill; encja, konwerter, `RunRecorder.source`, wpisy `ClosedSetsIT` i `tables.sh`, IT backfillu;
- `SourceIngest`/`RunSequence`: izolacja, zapis per źródło, status `partial`, błąd przebiegu, `NoPostsFetchedException` nazywający każde źródło, powody `source_failed`/`source_empty`;
- fałszywe drugie źródło i scenariusze `SourceIngestTest`, `RunSequenceTest`, `PipelineRunIT`, `RunWatchdogIT`, `MissingDaysTest`/`IT`, `PipelineRunControllerIT`;
- pokrycie dnia z wierszy `succeeded`;
- `RunView.sourceResults` + liczniki, typy SPA, teksty powodów, sekcja „Wynik per źródło” na stronie przebiegu, etykieta `reddit`, atrapa, kitchen sink, screenshoty;
- `pipeline/CLAUDE.md`, `sources/CLAUDE.md`, PRD, `REPOSITORY_GUIDELINES.md`, README, sekcja S-20 w `deploy-plan.md`, issue #114, weryfikacja w PR env.

**Out of scope:**
- świeżość per źródło i oznaczenie źródła w feedzie (S-29);
- kod Reddita, `REDDIT_*` w IaC i CI (S-27);
- kody wyjścia i klucze crona, format alertu;
- osobne przebiegi na źródło, kroki `fetch` per źródło, pobieranie równoległe, puls w porcie `SourceFetcher`;
- wynik per źródło w liście `/admin` i w dzwonku;
- `CHECK` wiążące status z licznikami; S-13 F5; edycja `context/domain/` i audytu; osobna zmiana `/10x-ui`.

## Architecture / Approach

`SourceIngest.fetch(target, recorded)` idzie po źródłach z checkpointem przerwania przed każdym; wyjątek źródła to jego wynik `failed`, brak posta to `empty`; każdy wynik trafia przez `recorded` do `RunRecorder.source` — jednej instrukcji, która dodaje sumy i liczniki przebiegu, przesuwa `last_activity_at` i wstawia wiersz `pipeline_run_sources`, wyłącznie w przebiegu `running`. Po pętli: żadne źródło `succeeded` → `NoPostsFetchedException` (przebieg `failed`, jak S-08); inaczej `FetchedBatch` z postami zdrowych źródeł idzie do `store` → `select` → `assess`, a `summarize` daje `partial`, gdy jakieś źródło nie jest `succeeded` lub ocena dała porażki; `error` przebiegu to podsumowanie źródeł i oceny. `attentionReasons` czyta liczniki wiersza; `RunView` dołącza `sourceResults`; `MissingDays` liczy pokrycie z wierszy `succeeded`. Fałszywy `ScriptedSourceFetcher(REDDIT)` w `src/test` dowodzi całości bez API Reddita.

Testowe drugie źródło używa adaptera `RawInputMapper(REDDIT)` delegującego do produkcyjnego `WykopPosts` i istniejącego RAW Wykopu; sprawdza izolację silnika. Integrację formatu Reddita z jego produkcyjnym mapperem i rzeczywistym fixture musi zweryfikować S-27.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Model i rejestracja | V12 (tabela, liczniki, `reddit` w `CHECK`, backfill), encja, `RunRecorder.source`, strażnicy, IT backfillu; od razu 503 dla `reddit` w teście API i nowa tabela w obu `TRUNCATE` IT silnika | Backfill na danych sprzed S-08/S-17 (puste `succeeded`, kroki bez błędu) — reguła z `COALESCE` i IT z Flyway `target("11")` |
| 2. Izolacja w silniku | `SourceIngest`/`RunSequence` per źródło, status i błąd, powody, fałszywe źródło, IT silnika i strażnika | Przerwanie między źródłami i zapis warunkowy — przypięte `SourceIngestTest` i scenariuszem `onClose` w `PipelineRunIT` |
| 3. Pokrycie dnia per źródło | Zapytania i `MissingDays` z wierszy `succeeded`, test dwóch źródeł (Parked F2); `V12BackfillIT` sprawdza pokrycie produkcyjnymi zapytaniami po migracji | Historyczne puste `succeeded` nie pokrywają dni ani nie wyznaczają początku okna; test przypina ujawnioną lukę, pokrycie innym przebiegiem i historię bez postów |
| 4. API i SPA | `RunView.sourceResults`, typy, teksty powodów, sekcja na stronie przebiegu, atrapa, screenshoty | Okno wdrożenia API → web: pole opcjonalne w SPA, teksty bez nazw źródeł przy starym API |
| 5. Dokumenty, runbook i PR env | PRD, wytyczne, README, sekcja S-20 `deploy-plan.md`, issue #114, weryfikacja w PR env | PR env ma tylko Wykop — izolację dowodzą IT, PR env dowodzi pól, rejestracji `reddit` i braku regresji |

**Prerequisites:** gałąź `feature/source-isolation` z `origin/main` po S-22/S-25 (istnieje jako worktree); issue #114; Docker do `./mvnw -B verify` i `tools/db-backup/test.sh`; `playwright-cli` do zrzutów.
**Estimated effort:** ~5 sesji (po jednej na fazę; faza 2 największa).

## Open Risks & Assumptions

- Backfill zakłada, że przed V12 każdy przebieg zakresu miał jedno źródło (prawda od V5) i że błąd kroku `fetch` pustego przebiegu zawiera `gave no post to store` (tekst S-08); przebieg z krokiem `fetch` bez błędu i bez postów sprzed S-08 dostaje `empty`. Nie zakładamy identycznych „Dni bez danych” po deployu: runbook wyjaśnia możliwą korektę na podstawie historii, a `V12BackfillIT` w fazie 3 przypina wynik. Źródło bez żadnego udanego pobrania z postami nadal nie ma brakujących dni.
- Dwa testy odwracają oczekiwanie po rejestracji `reddit` (`MigrationsIT:181`, `PipelineRunControllerIT:243`) — oba w fazie 1, razem z rozszerzeniem `TRUNCATE` w `PipelineRunIT` i `RunWatchdogIT`; faza 2 aktualizuje też konstruktory w `PostStoreTest`, a faza 4 wymagane pola w `AdminView.test.ts`.
- `source_empty` dla małego, rzadko aktywnego subredditu da wiadomość w Telegramie co taką noc — dobór kanałów w S-27 musi to uwzględnić.
- Nieudany zapis wyniku źródła kończy przebieg (bez tłumienia z S-08) — przyjęte: bez bazy przebieg i tak nie zapisze postów.
- Przebieg trwający w chwili deployu nie ma wierszy per źródło; pokrycie go nie liczy (`interrupted`), więc bez skutku.
- Scenariusz `RunWatchdogIT` z dwoma opóźnionymi źródłami mierzy czas wobec progu 5 s — dopuszczalne tylko dla scenariuszy strażnika (S-26), z marginesem.

## Success Criteria (Summary)

- Test z dwoma źródłami, z których jedno rzuca albo jest puste, kończy przebieg `partial` z zapisanymi i ocenionymi postami zdrowego źródła, powodem nazywającym źródło i dniem pokrytym tylko dla niego; oba martwe → `failed` jak w S-08.
- Dwa zdrowe źródła o łącznym czasie powyżej progu strażnika nie są zatrzymane jako „bez postępu”.
- Admin widzi na stronie przebiegu wynik per źródło; `./mvnw -B verify`, `npm test`, `npm run build`, `tools/db-backup/test.sh` zielone; w PR env przebieg admina ma wiersz `wykop succeeded`, a `sources: ["reddit"]` daje 503.
