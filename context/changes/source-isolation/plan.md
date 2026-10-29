# S-20: awaria albo cisza jednego źródła nie psuje przebiegu pozostałych — plan implementacji

## Overview

Krok `fetch` przebiegu zakresu pobiera dziś źródła po kolei bez izolacji: wyjątek jednego źródła wychodzi z pętli i kończy przebieg `failed` bez zapisu liczników, a źródło bez posta wywraca po pętli cały krok, także posty pozostałych źródeł. Między źródłami nic nie zgłasza postępu strażnikowi, liczniki są tylko sumami, a pokrycie dnia bierze listę `sources` przebiegu, więc po dodaniu drugiego źródła cichy subreddit zrobiłby z nocy Wykopu `failed`, a „Dni bez danych” pokazałyby lukę Wykopu spowodowaną Redditem.

Ta zmiana izoluje źródła **w jednym przebiegu**: `SourceIngest` idzie dalej po padłym albo pustym źródle, wynik każdego źródła trafia jednym warunkowym zapisem do nowej tabeli `pipeline_run_sources` (to zarazem postęp dla strażnika), posty zdrowych źródeł są zapisywane i oceniane, a przebieg kończy się `partial` z powodem nazywającym źródło (`source_failed`, `source_empty`), albo `failed` jak w S-08, gdy żadne źródło nie dało posta. Pokrycie dnia i strona przebiegu liczą się per źródło. Rejestracja stałej `Source.REDDIT` (bez kodu źródła) daje testom drugie źródło.

Slice S-20 roadmapy M-3 (gwiazda przewodnia, cel `quality`): audyt po M-2 S1, S2, S5; destylacja R-05 / D-01 (kandydat #1); NFR `prd.md:268` i `prd.md:259`; FR-023 jako odbiorca.

## Current State Analysis

- **Szew źródeł** — `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/SourceIngest.java:82-108`: pętla po `target.sources()` z checkpointem przerwania przed każdym źródłem (`:88`), wyjątek fetchera wychodzi z pętli (`:92`), puste źródła są zbierane (`:97-99`) i po pętli rzucają `NoPostsFetchedException(empty, fetched, skipped)` z sumami (`:104-105`); `fetchers` to `Map<Source, SourceFetcher>` z beanów (`:34-36`), więc dwa fetchery tego samego `Source` nie przejdą konstruktora. `resolve` (`:44-56`) i `states()` (`:59-65`) biorą tylko beany.
- **Sekwencja** — `pipeline/flow/RunSequence.java:90-109`: `fetch` łapie tylko `NoPostsFetchedException`, zapisuje sumy liczników przed porzuceniem (S-08) i rzuca dalej; inny wyjątek źródła kończy krok `failed` bez liczników (audyt S1). Status końcowy liczy wyłącznie ocena: `RunFlow.AssessedBatch.status()` (`flow/RunFlow.java:145-150`), błąd przebiegu to `errorSummary()` (`:127-139`) zapisany w `assess` (`RunSequence.java:136`).
- **Liczniki i postęp** — `pipeline/state/RunRecorder.java:106-124` `count` dodaje sumy i przesuwa `last_activity_at`; `openStep`/`endStep` (`:306-341`) to jedyny postęp kroku `fetch` (`pipeline/CLAUDE.md:81-90`). Termin pobrania Wykopu `FETCH_DEADLINE` 10 min + 2 × timeout jest przypięty poniżej `PipelineStallSettings.MIN_DEFAULT` 15 min tylko per źródło (`sources/wykop/WykopRepository.java:55-61`, `test/sources/wykop/WykopRepositoryTest.java:317-324`); dwa zdrowe źródła po kolei dają ok. 22 min bez postępu (audyt S2).
- **Model przebiegu** — `pipeline/run/PipelineRun.java:52-55,75-95`: `sources TEXT[]` (lista żądana), `fetched`, `skipped` jako sumy; `attentionReasons(status, dead, processingErrors, skipped)` (`:296-321`) to czysta funkcja wiersza; `AttentionReason` (`pipeline/run/AttentionReason.java`) ma `failed`, `interrupted`, `dead_posts`, `processing_errors`, `skipped`, kolejność stałych = kolejność w odpowiedzi; `RunView` (`pipeline/api/PipelineRunController.java:221-239`) wysyła sumy i `sources`.
- **Pokrycie dnia i świeżość** — `pipeline/run/MissingDays.java:110-125` `Coverage.of(run, zone)` bierze `run.getSources()`; `pipeline/run/PipelineRunRepository.java:88-95,101-115` `findCoveringSince` i `findEarliestRangeFrom` (`array_position(r.sources, :source)`) liczą pokrycie z listy `sources` przebiegów `succeeded`/`partial`. S-13 zapisało założenie, że taki przebieg oznacza „każde źródło pobrało się i dało post” (`context/archive/2026-10-24-missed-night-detection/plan.md:13`) — S-20 to założenie unieważnia. Świeżość (`findLastUpdate`, `:72-81`) jest globalna i zostaje (S-29).
- **Schemat** — `db/migration/V3__create_pipeline_and_posts.sql:7-40,43-59` (`pipeline_runs`, `pipeline_run_steps` z `id UUID DEFAULT gen_random_uuid()`), `V5__pipeline_run_sources.sql:6-10` (`sources TEXT[]`, `CHECK … sources <@ ARRAY['wykop']`), trzy zbiory `CHECK` ze źródłem: `ck_posts_source` (`V3:111`), `ck_raw_payloads_source` (`V3:75`), `ck_pipeline_runs_sources`. `persistence/ClosedSetsIT` (`REGISTRY`, `:77-98`) wymaga równości każdego zapisywanego enumu ze zbiorem `CHECK` jego kolumny; `tools/db-backup/tables.sh:17-29` klasyfikuje każdą tabelę.
- **Cron i alert** — `pipeline/api/InternalPipelineRunController.java:104-125` `RunState` ma tylko sumy (bez `error` i `sources`); `tools/pipeline-trigger/trigger.sh:206-215,324` czyta status i sumy; `notifications/RunNotifications.java:128-150` `attentionDetail` niesie `dead`/`errors`/`skipped` i `error` przebiegu (do 300 znaków). Kod wyjścia crona (0 dla `partial`) jest kontraktem, na którym S-18 opiera „pomiń dubel crona” (`RunNotifications.java:119-121`).
- **SPA** — `szczur-radar-web/src/api/pipeline.ts:21,33-73` (`AttentionReason`, `Run` z `sources: string[]`, sumami), `src/runs.ts:74-98` (tekst powodów z kodów API, S-22), `src/views/RunDetailView.vue:67-79,178-187,221-228` (liczniki, `Źródła` z `run.sources`), `src/labels.ts:72` (`source: { wykop: 'Wykop' }`), `dev/mock-api.js:91-160` (fixtury przebiegów).
- **Testy** — `test/pipeline/step/SourceIngestTest.java:120-141`: fałszywy fetcher ma `Source.WYKOP` na sztywno, 8 przypadków jednego źródła; każdy IT silnika podmienia `WykopRepository` lokalnym serwerem przez własną `@TestConfiguration` (`PipelineRunIT.java:872-885` i 5 kopii); brak testu z dwoma źródłami, z rzucającym fetcherem, z postępem w `fetch` i z sumą terminów; `MissingDaysTest` tylko Wykop (Parked F2); `MigrationsIT:181-182` odrzuca `ARRAY['reddit']`, `PipelineRunControllerIT:243` oczekuje 400 dla `"sources":["reddit"]`; `MigrationsIT` migruje wszystko przy starcie kontekstu — nie ma wzorca testu backfillu.

## Desired End State

- Przebieg zakresu z kilkoma źródłami zapisuje i ocenia posty źródeł, które dały posty, gdy inne źródło rzuciło wyjątek albo nie dało żadnego posta. Kończy się `partial` z powodami `source_failed` / `source_empty` (dzwonek, strona przebiegu, Telegram `RUN_NEEDS_ATTENTION`, cron kod 0). Gdy żadne źródło nie dało posta, kończy się `failed` z błędem nazywającym każde źródło (S-08 bez zmian: dzwonek, cron kod 1, etykieta świeżości stoi).
- Wynik każdego źródła (`succeeded` / `empty` / `failed`, `fetched`, `skipped`, błąd) jest trwały w `pipeline_run_sources`, zapisany tuż po tym źródle jednym zapisem warunkowym na `running`, który przesuwa `last_activity_at`: dwa zdrowe źródła o łącznym czasie powyżej progu strażnika nie są zatrzymane jako „bez postępu”.
- Dzień jest pokryty dla źródła tylko wtedy, gdy przebieg `succeeded`/`partial` ma dla niego wiersz `succeeded`. Backfill zachowuje pokrycie historycznych zakresów, których zakończony krok `fetch` dał posty. Historyczne puste przebiegi dostają `empty` i nie pokrywają dni: bez innego udanego pobrania mogą ujawnić brakujący dzień lub przesunąć pierwszy pokryty dzień źródła. To zamierzona korekta historii, nie gwarancja niezmienionej listy „Dni bez danych”.
- Admin widzi na stronie przebiegu wynik per źródło (status, pobrane, pominięte, błąd); `RunView` ma `sourceResults`, `sourcesFailed`, `sourcesEmpty`. Kontrakt crona, format alertu, `/sources` i formularz admina bez zmian.
- `Source.REDDIT` jest zarejestrowane (stała + cztery zbiory `CHECK`), bez fetchera: `/sources` nadal wymienia tylko Wykop, `sources: ["reddit"]` → 503 `source_not_configured`. Testy silnika mają fałszywe drugie źródło.
- Weryfikacja: `./mvnw -B verify`, `npm test`, `npm run build`, `tools/db-backup/test.sh` zielone; IT backfillu; w PR env przebieg admina ma wiersz Wykopu i `sourceResults` w JSON.

### Key Discoveries:

- Zapis per źródło nie wymaga zmiany portu `SourceFetcher` (S-06 odrzuciło puls w porcie, `context/archive/2026-10-15-pipeline-run-history-dashboard/plan.md:77`): wystarczy, że `RunSequence` zapisze wynik po każdym źródle — to zapis warunkowy jak każdy w `RunRecorder`, więc i postęp.
- Klient Wykopu zachowuje flagę przerwania (`sources/wykop/WykopApiClient.java:243,261`, `WykopRepository.java:259`), więc checkpoint przed kolejnym źródłem kończy krok także po wyjątku „z przerwania”; a kto zatrzymuje przebieg (strażnik, `onClose`, odzyskiwanie), najpierw pisze jego koniec (`pipeline/CLAUDE.md:158-160`), więc zapis warunkowy na `running` nigdy nie doda wiersza przerwanemu źródłu.
- `attentionReasons` ma zostać czystą funkcją wiersza przebiegu: dwa liczniki `sources_failed` / `sources_empty` w `pipeline_runs` (wzorzec `dead`, `skipped`) dają regułę bez złączenia; szczegół daje tabela.
- Drugie źródło w testach wymaga stałej enumu w kodzie produkcyjnym: `Source` jest zamknięty, `SourceIngest` kluczuje po `Source`, a `ClosedSetsIT` czyni migrację `CHECK` obowiązkową. Rejestracja `REDDIT` bez beana nic nie zmienia dla admina i crona (`states()` wymienia beany), a S-27 dostaje krok 1 z `sources/CLAUDE.md` gotowy.
- Fałszywy fetcher w IT musi być **nieskonfigurowany domyślnie** i włączany flagą per scenariusz (jak `wykopDown`): `startRange(…, List.of())` rozwiązuje skonfigurowane źródła w chwili żądania, więc istniejące scenariusze jednego źródła nie zmieniają się, a `/sources` w innych IT dalej zwraca `[wykop]`.
- Mapowanie wyniku źródła z liczników fetchera jest deterministyczne: `Fetched(posts, fetched, skipped)` daje `posts.size() == fetched − skipped` (`WykopFetcher.java:46-55`), więc `empty` ⇔ `fetched == skipped`, a liczby postów nie trzeba przechowywać.
- Backfill historii ma źródło prawdy w kroku `fetch`: krok `succeeded` → `succeeded` (albo `empty`, gdy `fetched = skipped`: przebiegi sprzed S-08 kończyły pusty dzień `succeeded`); krok `failed` z tekstem `gave no post to store` → `empty` (S-08, `PipelineRunIT.java:528-532`), inny `failed` → `failed` z błędem kroku; krok `running`/`interrupted` albo brak → bez wiersza. Przed V12 każdy przebieg miał jedno źródło, więc sumy przebiegu są licznikami tego źródła.
- API i web wdrażają się osobno: `RunView` dostaje pola, które stary SPA ignoruje; nowy SPA traktuje `sourceResults` jako opcjonalne (wzorzec `attentionReasons` z S-22), więc okno wdrożenia nie daje pustych tekstów.
- Testy silnika trzymają reguły S-26 (`pipeline/CLAUDE.md:417-431`): kolejność zamiast czasu; czas mierzą tylko scenariusze samego strażnika, więc dowód „suma terminów nie zatrzymuje przebiegu” trafia do `RunWatchdogIT` (próg 5 s).

## What We're NOT Doing

- Nie zmieniamy świeżości (`findLastUpdate`, etykieta „Dane zaktualizowane”) ani nie oznaczamy źródła w feedzie — S-29 (`source-aware-feed`) policzy świeżość per źródło z `pipeline_run_sources`.
- Nie dodajemy kodu Reddita (klient, repozytorium, mapper, fetcher, `REDDIT_*` w IaC i CI, etykiety poza `labels.ts`) — S-27; tu tylko rejestracja stałej i zbiorów `CHECK`.
- Nie zmieniamy kontraktu crona: kody wyjścia (`partial` → 0), klucze `GET /internal/pipeline-runs/{id}`, `trigger.sh`, `tools/pipeline-trigger/test.sh` bez zmian; reguła S-18 „pomiń dubel crona” bez zmian.
- Nie zmieniamy formatu alertu `RUN_NEEDS_ATTENTION` (`attentionDetail`): nazwę padłego źródła niesie `error` przebiegu, który alert już cytuje.
- Nie wprowadzamy osobnych przebiegów na źródło (wariant B wywiadu) ani osobnych kroków `fetch` per źródło (wariant C): `fetch` zostaje jednym krokiem, reguła „wyjątek kroku kończy przebieg” bez zmian.
- Nie pobieramy źródeł równolegle i nie dodajemy pulsu postępu do portu `SourceFetcher`; nie przenosimy pętli ponowień do `sources/` (krok 5 z `sources/CLAUDE.md`, S-27).
- Nie pokazujemy wyniku per źródło w liście `/admin` ani w dzwonku (`NotifiedRun` bez zmian); teksty powodów jak dotąd tylko na stronie przebiegu (decyzja S-22).
- Nie zmieniamy, które przebiegi wymagają uwagi poza dwoma nowymi powodami; `source_empty` alarmuje jak każdy powód (decyzja wywiadu).
- Nie dodajemy ograniczeń `CHECK` wiążących status wiersza z licznikami (`succeeded ⇒ fetched > skipped` itp.): dane historyczne mogłyby zatrzymać `migrate` na produkcji; pilnują tego testy kodu.
- Nie przenosimy składania „Dni bez danych” do usługi (S-13 F5 zostaje Parked) i nie edytujemy `context/domain/` ani `context/changes/post-m2-audit/`.
- Nie przechodzimy przez `/10x-ui` jako osobną zmianę: sekcja na stronie przebiegu stosuje kontrakt UI z `REPOSITORY_GUIDELINES.md` (tokeny, `StatusBadge`, screenshoty) w fazie 4.

## Implementation Approach

Najpierw model (faza 1): migracja V12 z tabelą `pipeline_run_sources`, licznikami i backfillem, rejestracja `REDDIT`, encja i zapis `RunRecorder.source` — sama w sobie nie zmienia zachowania przebiegu, ale daje zieloną bramkę bazy (`ClosedSetsIT`, `JpaSchemaIT`, `MigrationsIT`, `RunRecorderIT`, IT backfillu, `db-backup-test`). Potem silnik (faza 2): izolacja w `SourceIngest`, zapis po każdym źródle w `RunSequence`, status i błąd przebiegu, nowe powody, fałszywe drugie źródło i scenariusze IT. Następnie pokrycie dnia (faza 3) przechodzi na wiersze `succeeded`: zachowuje pokrycie potwierdzone pobraniem postów i koryguje historię pustych przebiegów zgodnie z Desired End State. Dopiero wtedy API i SPA (faza 4) pokazują wynik per źródło, a faza 5 domyka dokumenty, runbook i weryfikację w PR env.

Reguła statusu: krok `fetch` kończy się `failed` wyłącznie, gdy żadne źródło nie dało posta (`NoPostsFetchedException` nazywa każde źródło z jego wynikiem). Gdy choć jedno dało posty, krok kończy się `succeeded` i przebieg idzie dalej; po ocenie status to `partial`, jeśli jakiekolwiek źródło nie jest `succeeded` albo ocena dała porażki, inaczej `succeeded`. Błąd przebiegu = podsumowanie źródeł nie-`succeeded` i podsumowanie oceny, złączone ` | `.

Kolejność powodów: `failed` albo `interrupted` same; dla `partial` / `succeeded`: `source_failed`, `source_empty`, `dead_posts`, `processing_errors`, `skipped` (pobranie przed oceną).

## Critical Implementation Details

- **Zapis wyniku źródła poza `try` źródła.** Wyjątek fetchera jest wynikiem `failed` tego źródła; wyjątek zapisu wyniku (baza niedostępna) kończy krok i przebieg z tym błędem — przebieg i tak nie zapisze postów. Dotychczasowe „liczniki przed porażką, nieudany zapis stłumiony z WARN” z S-08 zastępuje zapis per źródło wewnątrz kroku; `RunSequenceTest` przypina nowy przebieg zdarzeń.
- **Przerwanie między źródłami.** Checkpoint stoi przed każdym źródłem, a wynik źródła zapisuje się warunkowo na `running`; kto zatrzymuje przebieg, pisze najpierw. Wyjątek „z przerwania” z klienta źródła (flaga zachowana) jest więc łapany jak każda awaria, jego wiersz nie powstaje, a checkpoint przed kolejnym źródłem kończy krok `RunInterruptedException`. Po ostatnim źródle nie ma checkpointu: krok oddaje paczkę albo rzuca, a `openStep` kroku `store` odmawia (`RunNoLongerRunningException`). Nowy scenariusz `SourceIngestTest` przypina, że drugie źródło nie jest pobierane po przerwaniu.
- **Backfill i pokrycie w tej samej wersji schematu.** Pokrycie przechodzi na wiersze w fazie 3, ale wiersze historii powstają w migracji fazy 1, żeby nowe zapytania znały dotychczasowe udane pobrania i pierwszy pokryty dzień źródła. IT backfillu migruje Flyway do wersji 11 (`Flyway.configure().target("11")`), sieje przebiegi z krokami `fetch` i migruje do końca. Puste historyczne `succeeded` stają się `empty`: nie zapewniają pokrycia ani nie wyznaczają początku okna źródła. W fazie 3 ten sam test obejmuje odczyt po migracji przez produkcyjne zapytania pokrycia i `MissingDays`.
- **Dwa testy odwracają oczekiwanie** po rejestracji `REDDIT`: `MigrationsIT:181-182` (odrzucenie `ARRAY['reddit']` → odrzucenie nieznanej wartości, np. `'twitter'`, a `'reddit'` przyjęte) i `PipelineRunControllerIT:243` (`"sources":["reddit"]` → 503 `source_not_configured`, a 400 dla nieznanej wartości).

## Phase 1: Model i rejestracja — `pipeline_run_sources`, liczniki, `Source.REDDIT`

### Overview

Schemat i model wyniku per źródło, zapis `RunRecorder.source`, rejestracja drugiego źródła i strażnicy zbiorów. Zachowanie przebiegu bez zmian: nic jeszcze nie woła nowego zapisu. Na początku fazy, przed pierwszym commitem kodu: gałąź `feature/source-isolation` (istnieje jako worktree z `origin/main`), PR z `Refs #114`, komentarz w issue #114 (id zmiany, gałąź, PR); pierwszy commit to folder zmiany (`change.md`, `plan.md`, `plan-brief.md`) razem ze statusem `in-progress` S-20 w roadmapie (`/10x-implement` aktualizuje też dane w `tools/render_roadmap_svg.py` i regeneruje `docs/visuals/roadmap.svg`).

### Changes Required:

#### 1. Migracja V12

**File**: `szczur-radar-api/src/main/resources/db/migration/V12__pipeline_run_sources.sql` (nowy)

**Intent**: Dać wynikowi każdego źródła trwałe miejsce, przebiegowi liczniki do reguły uwagi, a drugiemu źródłu rejestrację w zbiorach `CHECK`; wypełnić historię z kroku `fetch`, zachowując pokrycie udanych pobrań z postami i oznaczając historyczne puste pobrania jako `empty`.

**Contract**:
- `pipeline_runs`: `ADD COLUMN sources_failed INTEGER NOT NULL DEFAULT 0`, `ADD COLUMN sources_empty INTEGER NOT NULL DEFAULT 0`.
- Zbiory `CHECK` ze źródłem poszerzone o `'reddit'` przez `DROP CONSTRAINT` + `ADD CONSTRAINT` pod tą samą nazwą (wzorzec `V8` dla `ck_pipeline_run_steps_status`): `ck_posts_source`, `ck_raw_payloads_source`, `ck_pipeline_runs_sources` (`sources <@ ARRAY['wykop','reddit']::TEXT[]`, reszta warunku jak w V5).
- `CREATE TABLE pipeline_run_sources` w stylu `pipeline_run_steps` (`V3:43-59`): `id UUID NOT NULL DEFAULT gen_random_uuid()`, `run_id UUID NOT NULL` (FK `pipeline_runs`), `source TEXT NOT NULL`, `status TEXT NOT NULL`, `fetched INTEGER NOT NULL`, `skipped INTEGER NOT NULL`, `error TEXT NULL`, `finished_at TIMESTAMPTZ NOT NULL DEFAULT now()`; `ck_pipeline_run_sources_source CHECK (source IN ('wykop','reddit'))`, `ck_pipeline_run_sources_status CHECK (status IN ('succeeded','empty','failed'))`, `ck_pipeline_run_sources_counts CHECK (fetched >= 0 AND skipped >= 0 AND skipped <= fetched)`, `ck_pipeline_run_sources_error CHECK ((status = 'failed') = (error IS NOT NULL))`, `uq_pipeline_run_sources_run_source UNIQUE (run_id, source)`. Komentarz nagłówka: znaczenie statusów (`succeeded` — dało co najmniej jeden post; `empty` — odpowiedziało, bez posta; `failed` — rzuciło, `error` to `<SimpleName>: <message>`), że wiersz pisze `RunRecorder.source` tylko w przebiegu `running`, że rejestracja źródła dopisuje je także tu, i fix-forward.
- Backfill: dla każdego przebiegu `kind = 'range'` i każdego `s` z `unnest(sources)`, z jego ostatnim krokiem `fetch` (`ORDER BY started_at DESC, id DESC LIMIT 1`): krok `succeeded` → `succeeded`, gdy `fetched > skipped`, inaczej `empty`; krok `failed` z `error LIKE '%gave no post to store%'` → `empty`; inny `failed` → `failed` z `COALESCE(error, 'unknown (before V12)')`; brak kroku albo krok `running`/`interrupted` → bez wiersza. `fetched`/`skipped` z przebiegu, `finished_at = COALESCE(krok.finished_at, przebieg.finished_at, przebieg.created_at)`. Potem `UPDATE pipeline_runs SET sources_failed = (liczba wierszy failed), sources_empty = (liczba wierszy empty)`.

#### 2. Rejestracja drugiego źródła

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/Source.java`

**Intent**: Dać testom i S-27 drugą wartość zamkniętego zbioru; bez fetchera nic nie zmienia się dla admina i crona.

**Contract**: stała `REDDIT` po `WYKOP` (kolejność deklaracji to kolejność `Source` w odpowiedziach i w „Dniach bez danych”); javadoc: zarejestrowane, bez implementacji do S-27 (`sources/CLAUDE.md`, krok 1).

#### 3. Zamknięty zbiór wyniku źródła i konwerter

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/SourceFetchStatus.java` (nowy), `szczur-radar-api/src/main/java/pl/szczurradar/api/persistence/converters/SourceFetchStatusConverter.java` (nowy)

**Intent**: Status wyniku źródła jako enum zapisywany w bazie, po wzorcu `StepStatus` i `StepStatusConverter`.

**Contract**: `public enum SourceFetchStatus { SUCCEEDED, EMPTY, FAILED }` z `@JsonValue value()` (małe litery); konwerter `@Converter(autoApply = true)` na `EnumValueConverter<SourceFetchStatus>`.

#### 4. Encja i repozytorium

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunSource.java` (nowy), `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunSourceRepository.java` (nowy)

**Intent**: Odczyt wierszy per źródło dla API i pokrycia; zapis należy do `RunRecorder` (SQL), jak przy `PipelineRunStep`.

**Contract**: encja `pipeline_run_sources` z polami `id`, `runId`, `source` (`Source`), `status` (`SourceFetchStatus`), `fetched`, `skipped`, `error`, `finishedAt`, gettery, konstruktor dla JPA i konstruktor testowy; `JpaSchemaIT` waliduje mapowanie. Repozytorium `JpaRepository<PipelineRunSource, UUID>` z `findByRunId(UUID)` i `findByRunIdIn(Collection<UUID>)` (jedno zapytanie na stronę przebiegów, jak `PipelineRunStepRepository`); porządek `Source` nadaje czytelnik.

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java`

**Intent**: Liczniki do reguły uwagi w wierszu przebiegu.

**Contract**: pola `sourcesFailed`, `sourcesEmpty` (`insertable = false, updatable = false`, jak `llmRetries`), gettery; reguła `attentionReasons` zmienia się dopiero w fazie 2.

#### 5. Zapis wyniku źródła

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/RunRecorder.java`

**Intent**: Jeden zapis warunkowy na źródło: liczniki przebiegu, jego aktywność i wiersz wyniku razem, tylko w przebiegu `running`.

**Contract**: `public boolean source(UUID runId, Source source, SourceFetchStatus status, int fetched, int skipped, @Nullable String error)` — jedna instrukcja po wzorcu `openStep`: `WITH run AS (UPDATE pipeline_runs SET fetched = fetched + :fetched, skipped = skipped + :skipped, sources_failed = sources_failed + :failed, sources_empty = sources_empty + :empty, last_activity_at = now() WHERE id = :id AND status = :running RETURNING id) INSERT INTO pipeline_run_sources (run_id, source, status, fetched, skipped, error) SELECT id, :source, :status, :fetched, :skipped, :error FROM run RETURNING id`; `:failed`/`:empty` to 0/1 ze statusu; `error` ucięty jak każdy błąd (`truncate`); wartości enumów bindowane z `value()`; zwraca, czy zapisano. INFO `Run <id>: <source> <status>: fetched N, skipped M` (z `; <error>` dla `failed`). `Counters` bez zmian.

#### 6. Strażnicy zbiorów i tabel

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsIT.java`

**Intent**: Objąć nową tabelę regułą „enum = `CHECK`”.

**Contract**: dwa wpisy `REGISTRY`: `Source` → `pipeline_run_sources.source` (`PipelineRunSource`, `source`) i `SourceFetchStatus` → `pipeline_run_sources.status` (`PipelineRunSource`, `status`).

**File**: `tools/db-backup/tables.sh`

**Intent**: Kopia bazy zna nową tabelę (inaczej `db-backup-test` i `restore-check.sh` są czerwone).

**Contract**: wpis `"pipeline_run_sources 12 counted"` (wiersze tylko przybywają).

#### 7. Testy fazy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/MigrationsIT.java`

**Intent**: Przypiąć zbiory `CHECK` nowej tabeli i poszerzone zbiory źródeł.

**Contract**: `ARRAY['reddit']` w `pipeline_runs.sources` przyjęte, nieznana wartość (`'twitter'`) odrzucona (zastępuje `:181-182`); `'reddit'` przyjęte w `posts.source` i `raw_payloads.source`; `pipeline_run_sources`: nieznany `status` i `source` odrzucone, `skipped > fetched` odrzucone, `failed` bez `error` i `succeeded` z `error` odrzucone, drugi wiersz `(run_id, source)` odrzucony.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java`

**Intent**: Rejestracja `REDDIT` zmienia odpowiedź już w fazie 1, więc jej bramka backendu musi oczekiwać nowego kontraktu.

**Contract**: przenieść `"sources":["reddit"]` z pętli żądań oczekujących 400 do osobnego przypadku 503 `source_not_configured`; w pętli 400 `invalid_request` użyć nieznanego `"twitter"`. `/sources` nadal wymienia tylko Wykop. Testy nowych pól `RunView` dochodzą dopiero w fazie 4.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/RunWatchdogIT.java`

**Intent**: Nowy FK nie blokuje sprzątania bazy w istniejących testach po migracji V12.

**Contract**: w obu metodach `reset()` dodać `pipeline_run_sources` do tego samego polecenia `TRUNCATE`, które obejmuje `pipeline_runs`, już w fazie 1. Bez tego FK blokuje `TRUNCATE` również przy pustej nowej tabeli. Scenariusze dwóch źródeł i reset fetchera dochodzą w fazie 2.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/V12BackfillIT.java` (nowy)

**Intent**: Dowód reguły backfillu na danych sprzed V12, bo `MigrationsIT` migruje wszystko przy starcie.

**Contract**: kontekst bez auto-migracji (albo własny `Flyway.configure().dataSource(...).target("11").load().migrate()` na kontenerze z `TestcontainersConfiguration`), wsiane SQL-em przebiegi RANGE z krokiem `fetch`: `succeeded` z `fetched 16, skipped 2`; `succeeded` z `fetched 0, skipped 0` (sprzed S-08); `failed` z `wykop gave no post to store (fetched 5, skipped 5)`; `failed` z `WykopApiException: …`; `interrupted`; bez kroku; `retry`; potem `migrate()` do końca i asercje: wiersze `succeeded`/`empty`/`empty`/`failed` z błędem kroku, brak wierszy dla trzech ostatnich, liczniki `sources_failed`/`sources_empty` przebiegów, `finished_at` z kroku.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/RunRecorderIT.java`

**Intent**: Każdy strażnik nowego zapisu osobno, jak dla `count` i `touch`.

**Contract**: `source(...)` w przebiegu `running` dodaje wiersz, zwiększa `fetched`/`skipped`/`sources_failed`/`sources_empty` i przesuwa `last_activity_at`; w przebiegu `queued` i zakończonym nic nie zmienia i zwraca `false`; drugi wiersz tego samego źródła narusza klucz unikalny; długi błąd ucięty.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/JpaSchemaIT.java`

**Intent**: Encja zgodna z migracją.

**Contract**: zapis i odczyt `PipelineRunSource` obu statusów z błędem i bez; `Source.REDDIT` w `pipeline_runs.sources` zapisany i odczytany.

### Success Criteria:

#### Automated Verification:

- Bramka backendu przechodzi (w tym `ClosedSetsIT`, `JpaSchemaIT`, `MigrationsIT`, `V12BackfillIT`, `RunRecorderIT`): `cd szczur-radar-api && ./mvnw -B verify`
- Kopia bazy zna nową tabelę: `tools/db-backup/test.sh` kończy się kodem 0
- PR istnieje i odwołuje się do issue: `gh pr view --json body -q .body | grep -q 'Refs #114'`

**Implementation Note**: Faza bez kroków ręcznych; po zielonej bramce przejdź do fazy 2.

---

## Phase 2: Izolacja w silniku — `SourceIngest`, `RunSequence`, status i powody

### Overview

Padłe albo puste źródło nie zatrzymuje pobrania pozostałych: wynik każdego źródła jest zgłaszany i zapisywany tuż po nim, przebieg kończy się `partial` z powodem nazywającym źródło, a `failed` tylko bez żadnego posta. Fałszywe drugie źródło w testach dowodzi tego bez API Reddita.

### Changes Required:

#### 1. Dane między krokami

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunFlow.java`

**Intent**: Paczka pobrania niesie wynik każdego źródła, nie tylko sumy.

**Contract**: rekord `SourceFetch(Source source, SourceFetchStatus status, int fetched, int skipped, @Nullable String error)` (`error` tylko dla `FAILED`, w postaci `<SimpleName>: <message>` jak błąd przebiegu w `PipelineRunService.execute`); `FetchedBatch(runId, posts, fetched, skipped, List<SourceFetch> sources)` z `degraded()` (jakiekolwiek źródło nie `SUCCEEDED`) i `summary()` — tekst dla błędu przebiegu: dla każdego źródła nie `SUCCEEDED` `<source> failed: <error>` albo `<source> gave no post to store (fetched N, skipped M)`, złączone `; `, pusty gdy wszystkie `SUCCEEDED`. `AssessedBatch.status()` i `errorSummary()` bez zmian.

#### 2. Wyjątek pustego pobrania

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/NoPostsFetchedException.java`

**Intent**: Nazywać każde źródło z jego wynikiem, gdy żadne nie dało posta — także padłe.

**Contract**: konstruktor `NoPostsFetchedException(List<SourceFetch> outcomes)`; wiadomość to ten sam tekst co `FetchedBatch.summary()`; `sources()` (wszystkie), `fetched()`/`skipped()` (sumy) zostają dla logu i testów; `EmptySource` znika.

#### 3. Izolacja w kroku

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/SourceIngest.java`

**Intent**: Awaria albo cisza źródła jest jego wynikiem, nie końcem kroku; każdy wynik jest zgłaszany od razu.

**Contract**: `public FetchedBatch fetch(RangeTarget target, Consumer<SourceFetch> recorded)`; dla każdego źródła: checkpoint (bez zmian), potem w `try`: niekonfigurowane → `SourceNotConfiguredException`, `fetch`, WARN o pominiętych jak dziś, wynik `SUCCEEDED` albo `EMPTY`; `catch (RuntimeException ex)` → wynik `FAILED` z `ex.getClass().getSimpleName() + ": " + ex.getMessage()` i WARN `Run <id>: <source> failed: <message>` z wyjątkiem; poza `try`: `recorded.accept(wynik)`, posty do paczki. Po pętli: żadne źródło `SUCCEEDED` → `NoPostsFetchedException(outcomes)`, inaczej `FetchedBatch`. Javadoc opisuje izolację, zapis przez wywołującego i zachowanie przy przerwaniu (Critical Implementation Details).

#### 4. Sekwencja: zapis per źródło, status i błąd

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunSequence.java`

**Intent**: Zapisać wynik każdego źródła w chwili jego końca (postęp), a status i błąd przebiegu złożyć z pobrania i oceny.

**Contract**: `fetch` woła `ingest.fetch(target, outcome -> recorder.source(runId, …))` wewnątrz `recorder.step(…, FETCH, …)`; znika `recorder.count(Counters.fetched(...))` i blok `catch (NoPostsFetchedException)` (liczniki i wiersze zapisane po drodze). Gałąź RANGE przenosi `FetchedBatch` do końca: `assess` zapisuje `recorder.error(runId, join(" | ", fetch.summary(), assessed.errorSummary()))`, gdy którykolwiek niepusty; `summarize` daje `PARTIAL`, gdy `assessed.status() == SUCCEEDED && fetch.degraded()`, inaczej status oceny. RETRY/POSTS bez zmian. Javadoc klasy: reguła statusu i że krok `fetch` kończy się `failed` tylko bez żadnego posta.

#### 5. Reguła uwagi

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/AttentionReason.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java`

**Intent**: Padłe i puste źródło to powody uwagi z tej samej reguły, którą czytają dzwonek, alert i SPA.

**Contract**: stałe `SOURCE_FAILED("source_failed")`, `SOURCE_EMPTY("source_empty")` po `INTERRUPTED`; `attentionReasons(status, dead, processingErrors, skipped, sourcesFailed, sourcesEmpty)`: `PARTIAL` i `SUCCEEDED` dodają w tej kolejności `SOURCE_FAILED` gdy `sourcesFailed > 0`, `SOURCE_EMPTY` gdy `sourcesEmpty > 0`, potem jak dotąd (`dead_posts`, `processing_errors` tylko dla `PARTIAL`, `skipped`); `FAILED`/`INTERRUPTED`/`QUEUED`/`RUNNING` bez zmian. Javadoc powodów i reguły.

#### 6. Fałszywe drugie źródło

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/ScriptedSourceFetcher.java` (nowy)

**Intent**: Jedno źródło testowe dla `SourceIngestTest` i IT silnika, bez Springa i bez sieci.

**Contract**: `SourceFetcher` dla podanego `Source` z przełącznikami `volatile`: `configured`, odpowiedź (`Fetched` z postami / pusta / rzucany `RuntimeException` / trzymana na zatrzasku bez limitu, przerwanie → wyjątek z zachowaną flagą / opóźnienie o `Duration` tylko dla scenariuszy strażnika), licznik wywołań i ostatni zakres; posty jako `SourcePost` z `source` fetchera i unikalnymi `sourceItemId`. IT rejestrują go przez `@TestConfiguration` (`@Bean SourceFetcher redditFetcher()`), domyślnie `configured = false`, włączany per scenariusz.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/ScriptedSourceConfiguration.java` (nowy)

**Intent**: Testowe drugie źródło przechodzi przez produkcyjne mapowanie RAW i ocenę, bez kopiowania logiki mappera.

**Contract**:
- Wspólna `@TestConfiguration` importowana jawnie przez IT używające drugiego źródła rejestruje `ScriptedSourceFetcher(Source.REDDIT)` oraz adapter `RawInputMapper`: `source()` zwraca `REDDIT`, a `map(raw)` deleguje do produkcyjnego beana `WykopPosts.map(raw)`.
- RAW pochodzi z istniejącego `fixtures/wykop/entry.json`. Post powstaje przez `WykopPosts.post(...)`; test zmienia tylko `SourcePost.source` na `REDDIT`, zachowując RAW i pozostałe pola. Nie tworzymy parsera testowego ani stałej odpowiedzi mappera. Scenariusz z awarią Wykopu sprawdza ocenę posta drugiego źródła oraz zgodność treści zapisanego wejścia oceny z fixture.
- To fixture izolacji silnika pod drugim kluczem źródła, nie dowód obsługi formatu Reddita. S-27 musi sprawdzić integrację rzeczywistego Reddita z jego produkcyjnym mapperem i fixture Reddita; adapter Wykopu nie zastępuje tego testu.

#### 7. Testy jednostkowe

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/step/SourceIngestTest.java`

**Intent**: Dowód izolacji bez Springa: dwa źródła, każde zakończenie, przerwanie.

**Contract**: fałszywy fetcher z parametrem `Source` (`ScriptedSourceFetcher`); przypadki: dwa źródła z postami → paczka z obu, wyniki `SUCCEEDED` w kolejności `Source`, `recorded` wywołane dwa razy w tej kolejności; jedno puste → posty drugiego, wynik `EMPTY` z licznikami, sumy; jedno rzuca → posty drugiego, wynik `FAILED` z `<SimpleName>: <message>`, WARN z wyjątkiem; jedno puste i jedno rzuca → `NoPostsFetchedException` nazywający oba, `recorded` wywołane dla obu przed wyjątkiem; źródło niekonfigurowane w chwili pobrania → `FAILED` (`SourceNotConfiguredException: …`); flaga przerwania ustawiona przed drugim źródłem → `RunInterruptedException`, drugie nie pobrane, pierwsze zgłoszone, flaga zachowana. Dotychczasowe przypadki jednego źródła zostają z nową sygnaturą (wiadomość pustego źródła bez zmian).

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/flow/RunSequenceTest.java`

**Intent**: Przypiąć zapis per źródło w kroku, status i błąd z obu części.

**Contract**: zdarzenia `source(<source>, <status>, …)` w kolejności wewnątrz `fetch:started … fetch:succeeded`; paczka z jednym `FAILED` i czystą oceną → `error` = podsumowanie źródeł, `finish(PARTIAL)`; paczka czysta z oceną `partial` → jak dziś; obie części → złączone ` | `; `NoPostsFetchedException` → `fetch:failed:<msg>` bez `count fetched` i bez dalszego kroku. Przypadki „liczniki przed porażką” i „stłumiony nieudany zapis” usunięte (Critical Implementation Details).

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/step/PostStoreTest.java`

**Intent**: Istniejący konsument `FetchedBatch` kompiluje się po zmianie jego konstruktora.

**Contract**: uzupełnić wszystkie konstrukcje `FetchedBatch` o wyniki źródeł zgodne z postami i licznikami fixture; zachować dotychczasowe asercje zachowania `PostStore`.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/run/PipelineRunTest.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/notifications/RunNotificationsTest.java`

**Intent**: Nowe powody w regule i w powiadomieniach.

**Contract**: `PARTIAL` z `sourcesFailed`/`sourcesEmpty` osobno i razem z pozostałymi → `[source_failed, source_empty, dead_posts, processing_errors, skipped]`; `SUCCEEDED` z `sourcesEmpty` → `[source_empty]`; `FAILED` z licznikami → `[failed]`. `RunNotificationsTest`: przebieg crona `partial` z samym `sourcesEmpty = 1` powiadamia adminów i podnosi `RUN_NEEDS_ATTENTION` (detal bez zmian formatu).

#### 8. Testy integracyjne

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java`

**Intent**: Pierwszy dowód S1 na całej sekwencji z Postgresem: posty zdrowego źródła zapisane i ocenione, gdy drugie padło albo milczy.

**Contract**: import `ScriptedSourceConfiguration` z `ScriptedSourceFetcher(Source.REDDIT)` i adapterem mappera (fetcher domyślnie niekonfigurowany; `reset()` wyłącza go, a `TRUNCATE` obejmuje `pipeline_run_sources` od fazy 1); scenariusze: reddit rzuca, Wykop ma posty → przebieg `partial`, posty Wykopu `assessed`, `attentionReasons` `["source_failed"]`, wiersze `wykop succeeded` i `reddit failed` z błędem, `sources_failed = 1`, `error` z `reddit failed: …`, krok `fetch` `succeeded`, powiadomienie i alert; reddit pusty → `partial`, `["source_empty"]`, wiersz `empty`; Wykop `wykopDown`, reddit z postami → `partial` z `source_failed` dla `wykop` i posty reddita ocenione; oba martwe (puste + rzucający) → `failed` na `fetch`, `error` nazywa oba, dwa wiersze, żadnego posta; przebieg zatrzymany między źródłami (reddit trzymany na zatrzasku, `onClose`) → bez wiersza reddita, wiersz Wykopu zapisany, bez ERROR w logu. Istniejące scenariusze jednego źródła bez zmian poza nową wiadomością wyjątku (bez zmian treści) i wierszem `wykop` na stronie przebiegu.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/RunWatchdogIT.java`

**Intent**: Dowód S2: suma czasu pobrania kilku źródeł nie zatrzymuje przebiegu, bo zapis po każdym źródle jest postępem.

**Contract**: z progiem `5s`: Wykop lokalny opóźniony ~3 s i `ScriptedSourceFetcher(REDDIT)` z opóźnieniem ~3 s → przebieg nie zatrzymany, oba wiersze `succeeded`, `last_activity_at` przesunięte między źródłami (scenariusz strażnika mierzy czas wobec progu z marginesem, jak pozostałe).

#### 9. Dokumentacja pakietów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md`

**Intent**: Notatki silnika i źródeł zgodne z nowym zachowaniem.

**Contract**: `pipeline/CLAUDE.md` — drzewo (`run/`: `PipelineRunSource`, `SourceFetchStatus`; `state/`: `RunRecorder.source`), wiersz `step/SourceIngest`, punkt `last_activity_at` (postęp po każdym źródle; termin per źródło nadal poniżej `MIN_DEFAULT`), linie logu (INFO wyniku źródła, WARN awarii), reguła pustego pobrania (`failed` tylko bez żadnego posta; `partial` z `source_failed`/`source_empty`; `RUN_NEEDS_ATTENTION` jak dla każdego `partial`), lista testów. `sources/CLAUDE.md` — krok 1 (cztery zbiory `CHECK`, `REDDIT` zarejestrowane), wiersz `SourceFetcher` („a failure of the source itself is thrown: the source's result is `failed`, the run goes on with the other sources”), krok 7 (test dwóch źródeł istnieje: `SourceIngestTest`, `PipelineRunIT`; izolacja nie jest już Parked), zdanie `:52` prawdziwe także w zachowaniu.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe szwu przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest='SourceIngestTest,RunSequenceTest,PipelineRunTest,RunNotificationsTest'`
- Pełna bramka backendu przechodzi (w tym `PipelineRunIT`, `RunWatchdogIT`, `NotificationsIT`, `PipelineRunControllerIT`): `cd szczur-radar-api && ./mvnw -B verify`
- Krok `fetch` nie zapisuje już sum osobno od wyników źródeł: `rg -n 'Counters\.fetched\(' szczur-radar-api/src/main` nie znajduje nic

**Implementation Note**: Faza bez kroków ręcznych; po zielonej bramce przejdź do fazy 3.

---

## Phase 3: Pokrycie dnia per źródło

### Overview

„Dni bez danych” liczą pokrycie ze źródeł, które naprawdę dały posty: wiersze `succeeded` przebiegów `succeeded`/`partial`. Backfill z fazy 1 zachowuje takie pokrycie Wykopu, a historyczne puste przebiegi wyklucza zarówno z pokrycia, jak i z wyznaczania pierwszego pokrytego dnia. Źródło bez żadnego udanego pobrania z postami nadal nie ma brakujących dni — obowiązuje dotychczasowa reguła początku jego okna.

### Changes Required:

#### 1. Zapytania pokrycia

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java`

**Intent**: Źródłem prawdy o pokryciu jest wiersz `succeeded` źródła, nie lista żądana.

**Contract**: `findCoveringSince(Instant since)` zwraca projekcję `CoveredRange(Instant rangeFrom, Instant rangeTo, Source source)` z JPQL po `PipelineRunSource s join PipelineRun r on s.runId = r.id` z `r.kind = RANGE`, `r.status in (SUCCEEDED, PARTIAL)`, `s.status = SUCCEEDED`, `r.rangeTo >= :since`; `findFirstCoveredFrom(Collection<Source>)` → `min(r.rangeFrom)` z tym samym złączeniem per źródło; zapytanie `findEarliestRangeFrom` z `array_position` i jego komentarz znikają. Javadoc `findLastUpdate` bez zmian (świeżość globalna, S-29).

#### 2. Składanie dni bez danych

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/MissingDays.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java`

**Intent**: `Coverage` z wyników per źródło.

**Contract**: `Coverage.of(CoveredRange range, ZoneId zone)` (jeden `Coverage` na wiersz, zbiór jednego źródła) zastępuje `Coverage.of(PipelineRun, ZoneId)`; javadoc punktu 1 reguły: „dzień jest pokryty dla źródła, gdy przebieg RANGE `succeeded`/`partial` ma dla niego wynik `succeeded`”. `missingDays()` w kontrolerze składa z nowej projekcji; javadoc trasy aktualny.

#### 3. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/run/MissingDaysTest.java`

**Intent**: Zamknąć Parked F2 z S-13: dwa źródła z różnymi lukami.

**Contract**: dzień pokryty tylko dla Wykopu (pokrycie `{wykop}`) → brakuje dla reddita; dzień bez obu → `sources` w kolejności `Source` (`[wykop, reddit]`); reddit nigdy niepokryty → bez dni (istniejący przypadek rozszerzony o dwa skonfigurowane źródła); pozostałe przypadki bez zmian.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/MissingDaysIT.java`

**Intent**: Pokrycie z wierszy na Postgresie i przebieg mieszany.

**Contract**: pomocnik siejący przebieg dodaje wiersz `succeeded` dla każdego źródła przebiegu (SQL); nowy przypadek: fałszywy fetcher reddita skonfigurowany (`@Import` konfiguracji z fazy 2, włączony), przebieg `partial` z wierszami `wykop succeeded` i `reddit failed` → dzień pokryty tylko dla Wykopu, dokładny JSON `{"day":…,"sources":["reddit"]}`; pierwszy pokryty dzień źródła liczony z wierszy; dotychczasowe dokładne JSON-y bez zmian.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/V12BackfillIT.java` (rozszerzenie testu z fazy 1)

**Intent**: Dowód skutku backfillu dla „Dni bez danych”, nie tylko poprawności wstawionych wierszy.

**Contract**: na odizolowanej bazie zmigrowanej do V11 zasiać jednodniowe przebiegi Wykopu ze statusem przebiegu i kroku `fetch` równym `succeeded`: 14 października 2026 bez postów (`fetched = skipped = 0`), 15 października z postami, 16 października bez postów. Po migracji do V12 odczytać tę samą bazę przez `PipelineRunRepository.findCoveringSince` / `findFirstCoveredFrom` i złożyć `MissingDays` dla `today = 2026-10-18`: pierwszy pokryty dzień to 15 października, a lista braków to dokładnie `[{day: 2026-10-16, sources: [wykop]}]`. Wariant z dodatkowym historycznym udanym pobraniem postów za 16 października daje pustą listę braków; wariant z samymi pustymi historycznymi przebiegami nie ma pierwszego pokrytego dnia i również daje pustą listę. Użyć produkcyjnych zapytań i `MissingDays`, bez kopiowania ich SQL lub reguły w teście. Rozszerzenie należy do fazy 3, gdy nowe zapytania już istnieją, i przechodzi w jej pełnej bramce `./mvnw -B verify`.

#### 4. Dokumentacja pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: Reguła pokrycia w notatkach silnika.

**Contract**: wiersz `PipelineRunController` (`missing-days`) i opis `MissingDays` mówią o wyniku `succeeded` źródła.

### Success Criteria:

#### Automated Verification:

- Testy pokrycia przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest=MissingDaysTest`
- Pełna bramka backendu przechodzi (w tym `MissingDaysIT`, `PipelineRunWithoutSourceIT`, `FreshnessIT`): `cd szczur-radar-api && ./mvnw -B verify`
- Pokrycie nie czyta już listy żądanej: `rg -n 'array_position' szczur-radar-api/src/main` nie znajduje nic

**Implementation Note**: Faza bez kroków ręcznych; po zielonej bramce przejdź do fazy 4.

---

## Phase 4: API i SPA — wynik per źródło na stronie przebiegu

### Overview

`RunView` niesie wynik każdego źródła i dwa liczniki; strona przebiegu pokazuje je w sekcji „Wynik per źródło”, a teksty nowych powodów nazywają źródło. Atrapa, kitchen sink i screenshoty w obu motywach i przy 375 px.

### Changes Required:

#### 1. Odpowiedź przebiegu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java`

**Intent**: Admin dostaje wynik per źródło tam, gdzie dostaje kroki.

**Contract**: `RunView` + `int sourcesFailed`, `int sourcesEmpty` (po `skipped`) i `List<SourceResultView> sourceResults` (po `steps`); rekord `SourceResultView(Source source, SourceFetchStatus status, int fetched, int skipped, @Nullable String error, Instant finishedAt)` w kolejności `Source`; lista przebiegów ładuje wiersze jednym `findByRunIdIn`, strona przebiegu `findByRunId`; `/sources`, `StartRequest`, `missing-days` bez zmian poza fazą 3. `InternalPipelineRunController.RunState` bez zmian.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java`

**Intent**: Przypiąć nowe pola, zachowując ustalone w fazie 1 odpowiedzi dla zarejestrowanego i nieznanego źródła.

**Contract**: przebieg z wierszami `wykop succeeded` i `reddit failed` (SQL) → `$.sourceResults` w kolejności `Source` z pełnymi polami, `$.sourcesFailed = 1`, `$.sourcesEmpty = 0`; lista → `$.items[*].sourceResults` dla strony. Testy odpowiedzi 503 dla `reddit`, 400 dla `twitter` i niezmienionego `/sources` są już częścią fazy 1 i nadal przechodzą.

#### 2. Kontrakt odpowiedzi w SPA

**File**: `szczur-radar-web/src/api/pipeline.ts`

**Intent**: Typy zgodne z `RunView`.

**Contract**: `AttentionReason` + `'source_failed' | 'source_empty'`; `SourceFetchStatus = 'succeeded' | 'empty' | 'failed'`; `interface SourceResult { source: string; status: SourceFetchStatus; fetched: number; skipped: number; error: string | null; finishedAt: string }`; `Run` + `sourcesFailed: number`, `sourcesEmpty: number`, `sourceResults?: SourceResult[]` (opcjonalne: API sprzed S-20 go nie wysyła). Komentarze w stylu pliku.

#### 3. Tekst powodów i ton wyniku źródła

**File**: `szczur-radar-web/src/runs.ts`, `szczur-radar-web/src/labels.ts`

**Intent**: Powody nazywają źródło; status wyniku ma ton i etykietę.

**Contract**: `AttentionFields` + `sourceResults`; `source_failed` → `błąd pobrania: <etykiety źródeł failed po przecinku>` (bez wyników: `błąd pobrania źródła`); `source_empty` → `bez postów: <etykieta> (pobrano N, pominięto M)` dla każdego `empty` (bez wyników: `źródło nie dało postów`); `export function sourceTone(status: SourceFetchStatus): BadgeTone` (`succeeded` → `success`, `empty` → `warning`, `failed` → `destructive`); `labels.ts`: `source.reddit = 'Reddit'`, zbiór `sourceStatus` (`succeeded: 'pobrane'`, `empty: 'bez postów'`, `failed: 'błąd'`). `runTone`/`runStatusLabel` bez zmian (`partial` już jest ostrzeżeniem).

#### 4. Strona przebiegu

**File**: `szczur-radar-web/src/views/RunDetailView.vue`

**Intent**: Admin widzi pobrane i pominięte elementy osobno dla każdego źródła.

**Contract**: sekcja `part` „Wynik per źródło” między „Liczniki” a „Kroki”, tylko dla `kind === 'range'`: lista `<li>` per `sourceResults` z etykietą źródła, `StatusBadge` (`sourceTone`, `label('sourceStatus', …)`), figurami `pobrane N · pominięte M · posty N−M` i `error` jak `step-error`; pusty stan dla przebiegu bez wierszy (`Jeszcze żadne źródło nie zakończyło pobrania.`); brak pola (`sourceResults` nieobecne) → sekcja ukryta. `dd` „Źródła” w `meta` zostaje jako lista żądana. Style tylko z tokenów (`src/styleContract.test.ts` ma ten plik na liście `CLEANED`); bez nowego komponentu współdzielonego, chyba że implementujący wydzieli listę — wtedy trafia do `src/components/` i na kitchen sink.

#### 5. Atrapa i kitchen sink

**File**: `szczur-radar-web/dev/mock-api.js`, `szczur-radar-web/src/views/KitchenSinkView.vue`

**Intent**: Screenshoty i podgląd każdego stanu bez API.

**Contract**: każdy przebieg zakresu atrapy ma `sourceResults` (`nightly()`: `wykop succeeded`) i liczniki; nowe fixtury `r-source-failed` (noc crona `partial`: `wykop succeeded`, `reddit failed` z błędem `WykopApiException`-podobnym, `attentionReasons: ['source_failed']`, `error` z `reddit failed: …`) i `r-source-empty` (`['source_empty']`); nagłówek pliku opisuje oba; `NOTIFICATIONS 'unread'` dostaje `r-source-failed`. Kitchen sink: powiadomienie o przebiegu `partial` z `source_failed` w liście „każdy powód” (`notifications`), bez nowej sekcji, o ile nie powstał komponent.

#### 6. Testy Vitest

**File**: `szczur-radar-web/src/runs.test.ts`, `szczur-radar-web/src/views/RunDetailView.test.ts`, `szczur-radar-web/src/views/AdminView.test.ts`

**Intent**: Teksty i sekcja w każdym stanie.

**Contract**: `runs.test.ts` — teksty `source_failed`/`source_empty` z wynikami i bez pola, `sourceTone` dla trzech statusów; `RunDetailView.test.ts` — sekcja z dwoma wierszami (etykiety, tony, figury, błąd), pusty stan dla przebiegu zakresu bez wierszy, brak sekcji dla `retry` i gdy pola nie ma, „Wymaga uwagi: błąd pobrania: Reddit”.

`AdminView.test.ts`: pomocnik `run(): Run` uzupełniony o wymagane `sourcesFailed: 0` i `sourcesEmpty: 0`, z możliwością nadpisania przez `overrides`; dotychczasowe asercje widoku bez zmian. Plik uczestniczy w typechecku `npm run build`.

#### 7. Screenshoty

**File**: `context/changes/source-isolation/ui/README.md` (nowy) i zrzuty obok

**Intent**: Gate wizualny z kontraktu UI.

**Contract**: atrapa + `playwright-cli`: `/admin/runs/r-source-failed` i `/admin/runs/r-source-empty` w jasnym i ciemnym motywie oraz przy 375 px; macierz 7 stanów sekcji: default (oba zrzuty), empty (`r-running` przed końcem pobrania albo fixture bez wierszy), loading i error (stany strony, istniejące zrzuty wystarczą lub N/A z powodem), hover / focus-visible / disabled N/A (sekcja bez elementów interaktywnych); README z listą plików i N/A.

### Success Criteria:

#### Automated Verification:

- Bramka backendu przechodzi (w tym `PipelineRunControllerIT`): `cd szczur-radar-api && ./mvnw -B verify`
- Testy web przechodzą: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą (w tym `styleContract.test.ts` w `npm test`): `cd szczur-radar-web && npm run build`
- Zrzuty sekcji istnieją dla obu fixtur, obu motywów i 375 px: `ls context/changes/source-isolation/ui/*.png | wc -l` ≥ 6 i `ui/README.md` wymienia każdy stan macierzy albo N/A z powodem

#### Manual Verification:

- Polskie teksty sekcji „Wynik per źródło” i powodów `source_failed`/`source_empty` są zrozumiałe dla admina (ocena użytkownika na zrzutach)

**Implementation Note**: Po zielonych bramkach i akceptacji tekstów przejdź do fazy 5.

---

## Phase 5: Dokumenty, runbook i weryfikacja w PR env

### Overview

Dokumenty mówią prawdę o nowym zachowaniu, operator ma runbook po merge'u, a PR env pokazuje wiersz Wykopu na prawdziwym przebiegu. Merge wykonuje człowiek po `/10x-impl-review` i `/10x-archive` na tej samej gałęzi.

### Changes Required:

#### 1. PRD

**File**: `context/foundation/prd.md`

**Intent**: Doprecyzowania pod oba NFR, które S-20 zmienia; bez zmiany treści wymagań.

**Contract**: pod NFR sygnału operatora (`prd.md:259`, po doprecyzowaniu S-17): „Doprecyzowanie (2026-10-29, S-20 `source-isolation`)” — zdanie S-08 „przebieg RANGE, w którym źródło nie dało ani jednego posta … kończy się `failed`” dotyczy od S-20 przebiegu, w którym **żadne** źródło nie dało posta; przebieg, w którym jedno źródło padło albo nic nie dało, a inne dało posty, zapisuje i ocenia te posty i kończy się `partial` z powodem `source_failed`/`source_empty` (dzwonek, `RUN_NEEDS_ATTENTION` w Telegramie, cron kod 0, etykieta świeżości jak przy każdym `partial`); „Dni bez danych” liczą pokrycie ze źródeł, które dały posty. Pod NFR źródeł (`prd.md:268`): rejestracja źródła obejmuje też `pipeline_run_sources.source`; izolacja źródeł w silniku; stała `reddit` zarejestrowana bez implementacji (S-27).

#### 2. Wytyczne i README

**File**: `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`, `tools/pipeline-trigger/README.md`

**Intent**: Agent i operator czytają aktualne reguły.

**Contract**: `REPOSITORY_GUIDELINES.md` — twarda zasada rejestracji źródła (cztery zbiory `CHECK`), opis pakietu `pipeline` (`pipeline_run_sources`, `SourceFetchStatus`, `RunRecorder.source`), punkt Deployment o strażniku/S-08 (S-20: izolacja, `partial` z powodami źródła, pokrycie per źródło), sekcja UI (`sourceTone` w `src/runs.ts`); `szczur-radar-api/README.md` — opis przebiegu zakresu i pustego przebiegu; `tools/pipeline-trigger/README.md` — zdanie, że `partial` może nieść padłe źródło (kod 0, szczegół w `/admin`).

#### 3. Runbook

**File**: `context/deployment/deploy-plan.md`

**Intent**: Sekcja S-20 w konwencji S-21 (status prawdziwy także po merge'u).

**Contract**: `## S-20: awaria albo cisza jednego źródła nie psuje przebiegu pozostałych (source-isolation)`; `**Status:** delivered by PR #<N>; production verification and closure in issue #114.`; „What changed for the operator” (Telegram: `RUN_NEEDS_ATTENTION` z `error: reddit failed: …` dla przebiegu `partial`; strona przebiegu z wynikiem per źródło; „Dni bez danych” per źródło; `failed` tylko bez żadnego posta; kontrakt crona bez zmian); „Migration” (V12 w pre-deploy `migrate`: nowa tabela z backfillem z kroku `fetch`, liczniki, `reddit` w zbiorach `CHECK`; co sprawdzić, gdy `migrate` padnie: fix-forward); „PR-env verification” (wyniki fazy); „Runbook — after the merge (operator; tracked in issue #114)”: 0. zasady (bez merge'a przy `RUNNING`, poza oknem nocy); 1. CI na `main` i pre-deploy `migrate` zielone; 2. odczyt produkcji: liczba wierszy `pipeline_run_sources` = liczba przebiegów RANGE z krokiem `fetch` zakończonym `succeeded`/`failed` (SQL przez `railway connect postgres` albo strona ostatniej nocy w `/admin` z wierszem `wykop`), „Dni bez danych” zgodne z pokryciem z wierszy `succeeded` i pierwszym pokrytym dniem źródła; ewentualną różnicę względem stanu sprzed deployu wyjaśniają konkretne historyczne puste przebiegi sklasyfikowane jako `empty`, a nie utrata udanych pobrań; 3. pierwsza noc po merge'u: strona przebiegu ma wiersz `wykop succeeded`, cron kod 0; 4. komentarz i zamknięcie #114 po weryfikacji. „Rollback: fix-forward only”.

#### 4. Weryfikacja w PR env

**File**: komentarze w issue #114, sekcja S-20 w `deploy-plan.md`

**Intent**: Dowód na prawdziwym API i SPA, że nowe pola i zachowanie jednego źródła działają, a rejestracja `reddit` nic nie zmienia dla admina.

**Contract**: w PR env (`szczur-radar-pr-<N>`): przebieg admina za D-2 → `GET /api/admin/pipeline-runs/{id}` ma `sourceResults` z jednym wierszem `wykop succeeded` i liczniki, `attentionReasons` jak dotąd; strona `/admin/runs/{id}` pokazuje sekcję; `POST /api/admin/pipeline-runs` z `sources: ["reddit"]` → 503 `source_not_configured`; `GET /api/admin/pipeline-runs/sources` → tylko `wykop`; `GET /api/admin/pipeline-runs/missing-days` odpowiada zgodnie z wynikami źródeł po migracji: historyczne `empty` nie pokrywają dnia ani nie wyznaczają początku okna źródła; dla danych bez takich korekt lista pozostaje jak przed deployem PR env. Wyniki (id przebiegu, odpowiedzi, zrzut) w komentarzu issue #114 i w sekcji runbooka.

### Success Criteria:

#### Automated Verification:

- CI PR-a zielone: `gh pr checks --watch` kończy się bez porażki
- PR env: JSON przebiegu admina ma wiersz Wykopu: `curl -sb <cookie> <web-origin>/api/admin/pipeline-runs/<id> | jq -e '.sourceResults | length == 1 and .[0].source == "wykop" and .[0].status == "succeeded"'`
- PR env: `reddit` jest zarejestrowane, ale nieskonfigurowane: `curl -s -o /dev/null -w '%{http_code}' -b <cookie> -H 'Content-Type: application/json' -d '{"from":"<D-2>","to":"<D-2>","sources":["reddit"]}' <web-origin>/api/admin/pipeline-runs` daje `503`, a `GET …/pipeline-runs/sources` daje `[{"source":"wykop","configured":true}]`
- Dokumenty wymieniają S-20: `rg -n 'S-20' context/foundation/prd.md context/deployment/deploy-plan.md REPOSITORY_GUIDELINES.md` znajduje każdy z trzech plików

#### Manual Verification:

- Operator ocenia treść doprecyzowań PRD i sekcji runbooka S-20 (w tym kroki po merge'u) i podejmuje decyzję o merge'u po `/10x-impl-review` i `/10x-archive`

**Implementation Note**: Merge wykonuje wyłącznie człowiek; weryfikacja produkcji i zamknięcie issue #114 idą runbookiem, nie przez Progress.

---

## Testing Strategy

### Unit Tests:

- `SourceIngestTest`: dwa źródła w każdym zakończeniu (posty / puste / wyjątek / niekonfigurowane), oba martwe, przerwanie między źródłami, zgłaszanie wyników w kolejności.
- `RunSequenceTest`: zapis per źródło wewnątrz kroku `fetch`, status `partial` z pobrania, złączony błąd, pusty fetch bez dalszego kroku.
- `PipelineRunTest`, `RunNotificationsTest`: nowe powody w regule i w powiadomieniach.
- `MissingDaysTest`: dwa źródła z różnymi lukami (Parked F2 z S-13).
- Vitest: `runs.test.ts`, `RunDetailView.test.ts`, `styleContract.test.ts`.

### Integration Tests:

- `RunRecorderIT` (zapis warunkowy), `MigrationsIT` (zbiory `CHECK`), `V12BackfillIT` (reguła backfillu w fazie 1; w fazie 3 także pokrycie i pierwszy pokryty dzień odczytane produkcyjnymi zapytaniami po migracji), `JpaSchemaIT`, `ClosedSetsIT`.
- `PipelineRunIT` z fałszywym drugim źródłem: posty zdrowego źródła ocenione przy padłym albo pustym drugim, oba martwe → `failed`, zatrzymanie między źródłami.
- `RunWatchdogIT`: dwa źródła o łącznym czasie powyżej progu nie zatrzymane.
- `MissingDaysIT`: pokrycie z wierszy, przebieg mieszany.
- `PipelineRunControllerIT`: `sourceResults`, liczniki, 503 dla `reddit`.
- `tools/db-backup/test.sh`: nowa tabela w kopii.

### Manual Testing Steps:

1. Ocena polskich tekstów sekcji „Wynik per źródło” i powodów na zrzutach z atrapy (faza 4).
2. Ocena doprecyzowań PRD i runbooka S-20; decyzja o merge'u (faza 5).
3. Po merge'u (runbook, issue #114): kontrola backfillu na produkcji i pierwsza noc z wierszem `wykop`.

## Performance Considerations

Jeden dodatkowy `UPDATE … RETURNING` + `INSERT` na źródło na przebieg (dziś jedno źródło, docelowo kilka): pomijalne. Lista przebiegów ładuje wiersze jednym zapytaniem na stronę (do 100 przebiegów), jak kroki. Zapytania pokrycia łączą dwie małe tabele (setki przebiegów) po kluczu głównym; indeks `uq_pipeline_run_sources_run_source` obsługuje odczyt per przebieg. Backfill V12 to jedna instrukcja na tabelę o setkach wierszy.

## Migration Notes

- Zamierzona korekta historii: puste przebiegi sprzed S-08, choć miały status `succeeded`, dostają wynik źródła `empty`. Nie pokrywają dni ani nie wyznaczają pierwszego pokrytego dnia źródła. Po deployu może więc zmienić się lista „Dni bez danych”; dla źródła bez żadnego pokrycia pozostaje pusta. Runbook ma wyjaśniać różnice na podstawie historycznych przebiegów, zamiast wymagać identycznej listy przed i po migracji.

- V12 działa w pre-deploy `migrate` Railway (ta sama ścieżka co V8–V11): nowa tabela, dwie kolumny z domyślnymi zerami, poszerzenie trzech `CHECK` przez `DROP`/`ADD`, backfill z kroku `fetch`. Fix-forward: błąd migracji na produkcji naprawia V13, nigdy edycja V12.
- Przebieg trwający w starej instancji podczas deployu (reguła „nie merguj przy `RUNNING`”) nie ma wierszy per źródło: po przerwaniu przez odzyskiwanie nie dostanie ich; pokrycie go nie liczy (przebieg `interrupted`), więc bez skutku.
- Okno wdrożenia API → web: stary SPA ignoruje nowe pola; nowy SPA przy starym API ukrywa sekcję i daje teksty bez nazw źródeł.
- Usunięcie w przyszłości: nigdy — zbiory zamknięte są append-only (`reddit` zostaje nawet, gdyby S-27 wybrało inną drogę).

## References

- Roadmapa: `context/foundation/roadmap.md` (S-20, linie 146–157; S-27, S-29)
- Audyt po M-2: `context/changes/post-m2-audit/research.md` (S1, S2, S5, sekcja „Testowalność drugiego źródła”)
- Destylacja: `context/domain/domain-distillation.md` (R-05, R-07, D-01, kandydat #1, Q-02, Q-06)
- Decyzje wcześniejszych wycinków: `context/archive/2026-10-18-empty-run-signal/plan.md` (S-08), `context/archive/2026-10-24-missed-night-detection/plan.md:13,60-64` (S-13), `context/archive/2026-10-15-pipeline-run-history-dashboard/plan.md:77` (S-06, postęp `fetch`), `context/archive/2026-10-24-error-visibility/plan-brief.md:18,30,79` (S-18), `context/archive/2026-10-27-run-attention-single-rule/plan.md` (S-22), `context/archive/2026-10-27-engine-test-stability/` (S-26)
- Kod szwu: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/SourceIngest.java:82-108`, `pipeline/flow/RunSequence.java:90-109`, `pipeline/flow/RunFlow.java:78-84,116-152`, `pipeline/state/RunRecorder.java:106-135,306-341`, `pipeline/run/PipelineRun.java:296-321`, `pipeline/run/MissingDays.java:110-125`, `pipeline/run/PipelineRunRepository.java:88-115`
- Migracje: `szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:43-59,75,111`, `V5__pipeline_run_sources.sql`, `V8__pipeline_run_progress.sql:21-23`
- Strażnicy: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsIT.java:77-98`, `tools/db-backup/tables.sh:17-29`
- SPA: `szczur-radar-web/src/runs.ts`, `src/api/pipeline.ts`, `src/views/RunDetailView.vue`, `src/labels.ts:72`, `dev/mock-api.js`
- Lekcje: `context/foundation/lessons.md` („Ponowienia źródła żyją w jego kliencie…”, „Kryterium, które agent sprawdzi poleceniem, zapisuj jako Automated”, „Uzasadniaj zakres celem użytkownika”)
- Issue: https://github.com/kwmacioszek/szczur-radar/issues/114

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Model i rejestracja — `pipeline_run_sources`, liczniki, `Source.REDDIT`

#### Automated

- [ ] 1.1 Bramka backendu przechodzi (w tym `ClosedSetsIT`, `JpaSchemaIT`, `MigrationsIT`, `V12BackfillIT`, `RunRecorderIT`): `cd szczur-radar-api && ./mvnw -B verify`
- [ ] 1.2 Kopia bazy zna nową tabelę: `tools/db-backup/test.sh` kończy się kodem 0
- [ ] 1.3 PR istnieje i odwołuje się do issue: `gh pr view --json body -q .body | grep -q 'Refs #114'`

### Phase 2: Izolacja w silniku — `SourceIngest`, `RunSequence`, status i powody

#### Automated

- [ ] 2.1 Testy jednostkowe szwu przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest='SourceIngestTest,RunSequenceTest,PipelineRunTest,RunNotificationsTest'`
- [ ] 2.2 Pełna bramka backendu przechodzi (w tym `PipelineRunIT`, `RunWatchdogIT`, `NotificationsIT`, `PipelineRunControllerIT`): `cd szczur-radar-api && ./mvnw -B verify`
- [ ] 2.3 Krok `fetch` nie zapisuje już sum osobno od wyników źródeł: `rg -n 'Counters\.fetched\(' szczur-radar-api/src/main` nie znajduje nic

### Phase 3: Pokrycie dnia per źródło

#### Automated

- [ ] 3.1 Testy pokrycia przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest=MissingDaysTest`
- [ ] 3.2 Pełna bramka backendu przechodzi (w tym `MissingDaysIT`, `PipelineRunWithoutSourceIT`, `FreshnessIT`): `cd szczur-radar-api && ./mvnw -B verify`
- [ ] 3.3 Pokrycie nie czyta już listy żądanej: `rg -n 'array_position' szczur-radar-api/src/main` nie znajduje nic

### Phase 4: API i SPA — wynik per źródło na stronie przebiegu

#### Automated

- [ ] 4.1 Bramka backendu przechodzi (w tym `PipelineRunControllerIT`): `cd szczur-radar-api && ./mvnw -B verify`
- [ ] 4.2 Testy web przechodzą: `cd szczur-radar-web && npm test`
- [ ] 4.3 Typecheck i build przechodzą (w tym `styleContract.test.ts` w `npm test`): `cd szczur-radar-web && npm run build`
- [ ] 4.4 Zrzuty sekcji istnieją dla obu fixtur, obu motywów i 375 px: `ls context/changes/source-isolation/ui/*.png | wc -l` ≥ 6 i `ui/README.md` wymienia każdy stan macierzy albo N/A z powodem

#### Manual

- [ ] 4.5 Polskie teksty sekcji „Wynik per źródło” i powodów `source_failed`/`source_empty` są zrozumiałe dla admina (ocena użytkownika na zrzutach)

### Phase 5: Dokumenty, runbook i weryfikacja w PR env

#### Automated

- [ ] 5.1 CI PR-a zielone: `gh pr checks --watch` kończy się bez porażki
- [ ] 5.2 PR env: JSON przebiegu admina ma wiersz Wykopu: `curl -sb <cookie> <web-origin>/api/admin/pipeline-runs/<id> | jq -e '.sourceResults | length == 1 and .[0].source == "wykop" and .[0].status == "succeeded"'`
- [ ] 5.3 PR env: `reddit` jest zarejestrowane, ale nieskonfigurowane: `curl -s -o /dev/null -w '%{http_code}' -b <cookie> -H 'Content-Type: application/json' -d '{"from":"<D-2>","to":"<D-2>","sources":["reddit"]}' <web-origin>/api/admin/pipeline-runs` daje `503`, a `GET …/pipeline-runs/sources` daje `[{"source":"wykop","configured":true}]`
- [ ] 5.4 Dokumenty wymieniają S-20: `rg -n 'S-20' context/foundation/prd.md context/deployment/deploy-plan.md REPOSITORY_GUIDELINES.md` znajduje każdy z trzech plików

#### Manual

- [ ] 5.5 Operator ocenia treść doprecyzowań PRD i sekcji runbooka S-20 (w tym kroki po merge'u) i podejmuje decyzję o merge'u po `/10x-impl-review` i `/10x-archive`
