# Jakość ocen per źródło — nieudane próby według klas (S-35) — plan implementacji

## Overview

Guardrail „jakość danych z LLM musi być wysoka” (R-15) nie ma dziś miary na produkcji (destylacja #1, FR-033). S-35 daje operatorowi stałą, liczoną z danych produkcyjnych miarę jakości ocen **osobno dla każdego źródła**: udział nieudanych prób oceny w oknie 7 dni, w podziale na klasy błędu kontraktu (`contract-v2.md` §12). Miarę widać co noc w podsumowaniu nocy (ntfy + log crona) i w nowej sekcji `/admin`. Gdy udział źródła **przejdzie przez jego próg** (Wykop 5 %, Reddit 25 %), operator dostaje osobny alarm ntfy o priorytecie 4, bez czytania logów.

Część „udział tez w innym języku niż post” z FR-033 robi S-36 tym samym kanałem i w tym samym oknie.

## Current State Analysis

- **Dane są.** Każda zapisana próba to wiersz `assessments` (`V3__create_pipeline_and_posts.sql:127-178`): `status` `ok`/`failed`, `failure_class` (7 wartości, `ck_assessments_failure_class` :162-164), `result_source` `model`/`prefilter`, `run_id`, `assessed_at`. Źródło tylko przez `posts.source` (`join Post p on p.id = a.postId`, wzór `AssessmentRepository.java:42-61`). Enum `agents/assessor/validation/FailureClass.java:11-48`.
- **Nic nie liczy po klasach.** `AssessmentRepository.sumBySource` (`:51-61`) daje ok/failed/tokeny na źródło i liczy wiersze prefiltra jako `ok`. Klasa błędu widać tylko per post w liście „Nieudane posty” (`posts/api/FailedPostController.java:104-113`).
- **Jedyny indeks `assessments`** to `ix_assessments_post_latest (post_id, assessed_at DESC, id DESC)` (V3:178); zapytanie po zakresie `assessed_at` przeszukałoby całą, wciąż rosnącą tabelę co noc i przy każdym wejściu na `/admin`.
- **Kanał sygnału już jest.** `pipeline/NightSummary` (S-34) składa tekst nocy z linią na źródło (`SourceLine` :251-284, `MAX_BYTES` 4096 :81), `InternalPipelineRunController` (`POST /internal/pipeline-runs/nightly-summary` :109-121) wysyła go jako `AlertEvent.NIGHT_SUMMARY` (priorytet 3) przez `OperatorAlertQueue` i zwraca cronowi, który loguje `summary:` (`tools/pipeline-trigger/trigger.sh:231-250`). Kolejka: ten sam (zdarzenie, przebieg) raz na 10 min, maks. 2 zdarzenia na minutę, reszta czeka (do 20) — nic nie ginie przy dwóch zdarzeniach naraz (`alerts/OperatorAlertQueue.java:31-38`).
- **Trzy różne miary w obiegu:** 17,3 % na Reddicie (S-28) to udział **prób** (81 z 467); 16,6 % z #121 to udział **postów** na pierwszej próbie; 2,3 % Wykopu to próby z odpowiedzią modelu. Ten plan przyjmuje definicję S-28 (próby z modelem).
- **Dane z produkcji (2026-11-11, `evidence/README.md`):** okno 7 dni — Wykop w stanie ustalonym 2,0–2,8 % (dominuje `fabricated_evidence` ~1,8 %), Reddit 14,6–18,4 % (`schema_violation` ~8 %, `fabricated_evidence` ~7–8 %). Pojedyncza noc Wykopa przy 60–82 próbach skacze do 10–11 %. Incydent 2026-10-09 (`provider_error` 23 z 43) daje okno 19,0 % 13.10.
- **Ograniczenia:** `pipeline/` nigdy nie nazywa źródła (`pipeline/CLAUDE.md`), nowe źródło zmienia tylko pozyskanie i mapowanie (PRD NFR), więc próg per źródło żyje przy źródle (`sources/Source`). PRD v3: nieudana próba posta, który ma jeszcze próby, nie jest powodem „wymaga uwagi” — sygnał S-35 jest zbiorczym udziałem w oknie, nie nowym `AttentionReason`. Precedens S-32: progi bezpieczników to stałe w kodzie, bez zmiennej.

## Desired End State

- Co noc podsumowanie nocy (ntfy `NIGHT_SUMMARY` i log crona `summary:`) ma linię jakości dla każdego źródła z próbami w oknie, np. (liczby z produkcji, okno 2026-11-05…2026-11-11):

  ```
  reddit quality 7d (2026-11-05…2026-11-11): failed 16.9% of 598 attempts (schema_violation 7.7%, fabricated_evidence 7.7%, domain_invariant_violation 1.5%); threshold 25%
  wykop quality 7d (2026-11-05…2026-11-11): failed 2.6% of 3134 attempts (schema_violation 0.3%, invalid_reference 0.1%, fabricated_evidence 2.0%, domain_invariant_violation 0.2%); threshold 5%
  ```

  ponad progiem z sufiksem `; ABOVE THRESHOLD`, a przy mniej niż 100 próbach `…: 46 attempts, fewer than 100, threshold not checked`.
- W noc, gdy okno źródła `[D−6, D]` przekracza próg, a okno poprzedniej nocy (`[D−7, D−1]` ucięte do chwili `now − 24 h`, czyli to, co widziało wczorajsze podsumowanie) go nie przekraczało (albo miało mniej niż 100 prób), API wysyła jedno zdarzenie `ASSESSMENT_QUALITY` (ntfy, priorytet 4, tytuł `Szczur Radar ASSESSMENT_QUALITY`) dla wszystkich takich źródeł. Na danych z ostatnich 35 dni dałoby to dokładnie jeden alarm: Wykop 2026-10-13.
- `/admin` ma sekcję „Jakość ocen (7 dni)” z `GET /api/admin/assessment-quality`: na źródło liczba prób, udział nieudanych, udziały klas, próg i stan (`w normie` / `ponad progiem` / `za mało prób`), w obu motywach i przy 375 px.
- Dokumentacja (PRD FR-033 z progami, runbook S-35, `pipeline/CLAUDE.md`, `sources/CLAUDE.md`, `REPOSITORY_GUIDELINES.md`, README crona) opisuje miarę, progi i co zrobić po alarmie.

Weryfikacja: `./mvnw -B verify`, `npm test`, `npm run build`, `tools/pipeline-trigger/test.sh`, CI PR-a zielone, podsumowanie i sekcja `/admin` sprawdzone w PR env przez agenta.

### Key Discoveries:

- Wzorzec zapytania per źródło: JPQL `select new …(p.source, …) from Assessment a join Post p on p.id = a.postId … group by p.source` — `posts/AssessmentRepository.java:56-61`; `SourceCount.bySource` (`posts/SourceCount.java:16-24`).
- Data nocy w `NightSummary` to „dziś w Warszawie” z wstrzykniętego `Clock` (`NightSummary.java:151`, `PipelineRunService.ZONE`) — okno liczy się od tej samej daty, więc noc i `/admin` mówią to samo.
- `NightSummaryTest.java:105-118` woła konstruktor `SourceLine` pozycyjnie (13 argumentów), format przypina tylko `NightSummaryTest`; `tools/pipeline-trigger/test.sh:91-98` to stała atrapy API (jej komentarz: „the format is pinned there, not here”), którą `log_has_the_summary` (`:312-317`) tylko odbija, a README crona `:64-68` to przykład logu — nowe linie ich nie zepsują, aktualizujemy je dla spójności przykładu.
- `AlertEvent` niesie opis i priorytet w konstruktorze (`alerts/AlertEvent.java:51-60`); żaden test nie wylicza `values()`, priorytet przypina `alerts/NtfyAlertsTest.java:44-68` przykładami.
- Nowa trasa `/api/admin/` czytana przez SPA: plik kontraktu w `szczur-radar-web/src/contract/`, próbka w `samples.ts`, wpis `SHAPES` w `contract/ContractRegistry.java:60`, `ADMIN_ROUTES` (sprawdzane przez `AuthorizationCompletenessIT`, `auth/AdminRoleIT`); wartość z etykietą → `rules.json` `values` + `SpaRulesTest.java:93-96` + `rules.test.ts`.
- Sekcje `AdminView.vue`: „Nowy przebieg” :333, „Dni bez danych” :373, „Ostatnie przebiegi” :397, „Nieudane posty” :451 — nowa sekcja idzie wzorem „Dni bez danych” (osobne pobranie, `aria-busy`, `StatusMessage` na błąd).

## What We're NOT Doing

- **Udział tez w innym języku niż post** (druga część FR-033) — S-36, tym samym kanałem i oknem.
- **Udział postów na pierwszej próbie** (miara 16,6 % z #121) — wybrano jedną miarę, próby z modelem; numer próby po przywróceniu martwego posta (S-33) i tak byłby niejednoznaczny.
- **Progi per klasa i progi względne** (do własnej historii źródła) — próg jest jeden na źródło, na sumie klas; klasy są tylko pokazywane.
- **Zmienna środowiskowa dla progów** — stała przy `Source`, zmiana progu = PR (wzór S-32).
- **Alarm co noc, dopóki ponad progiem** — tylko przy przejściu; trwanie problemu widać po `ABOVE THRESHOLD` w cichym podsumowaniu i w `/admin`.
- **Nowy `AttentionReason`, powiadomienie w dzwonku, nowa tabela, zapisany stan alarmu** — reguła przejścia liczy oba okna z danych.
- **Historia udziałów w `/admin`** (wykres, lista dni) — sekcja pokazuje bieżące okno.
- **Cicha zmiana modelu u dostawcy** (reszta wpisu Parked „Ochrona przed dryfem modelu”) — zostaje w Parked.
- **Kroki po merge'u** (pierwsza noc na produkcji, zamknięcie issue) — runbook w `deploy-plan.md` i komentarze w issue, nie Progress.

## Implementation Approach

Jeden komponent w `posts/` liczy okno jakości z jednego zapytania zbiorczego (źródło × klasa) i stosuje regułę progu z wartości przy `Source`. Ten sam komponent zasila trzy miejsca: podsumowanie nocy (linie + decyzja o alarmie przy przejściu, wołany z daty nocy), zdarzenie `ASSESSMENT_QUALITY` w kontrolerze crona i trasę `/api/admin/assessment-quality`. Reguła jest bezstanowa: przejście to porównanie okna kończącego się dziś z oknem poprzedniej nocy, uciętym do chwili `now − 24 h` (to, co widziało wczorajsze podsumowanie). Kolejność faz: miara → kanał nocny → widok → weryfikacja w PR env.

## Critical Implementation Details

- **Reguła progu w liczbach całkowitych.** „Ponad progiem” to `attempts >= MIN_ATTEMPTS && failed * 100 > threshold * attempts` (ostro większy, bez zmiennoprzecinkowych porównań); 5,0 % przy progu 5 % to jeszcze `ok`. Procenty w tekście z jednym miejscem po przecinku, kropka, `Locale.ROOT`, `RoundingMode.HALF_UP` (2,58 % → `2.6%`). Test przypina oba brzegi.
- **Okno w dniach Warszawy.** `[D−6, D]` to `[początek D−6 w Europe/Warsaw, początek D+1 w Europe/Warsaw)` jako `Instant`y z `ZonedDateTime` (dzień 23/25-godzinny przy zmianie czasu). Nocne przebiegi crona (00:30 UTC = 01:30/02:30 w Warszawie) leżą w dniu `D`, więc okno `[D−7, D−1]` nie zawiera tej nocy.
- **Okno porównania ucięte do wczorajszego podsumowania.** Okno poprzedniej nocy to `[początek D−7, min(początek D, now − 24 h))`, nie pełny dzień `D−1`: próby z przebiegów admina w ciągu `D−1` (także posty przywrócone przez S-33) zostały zapisane po wczorajszym podsumowaniu, a policzone w porównaniu mogłyby ukryć przejście (zły przebieg admina w dzień → „ponad dwa dni z rzędu”, alarmu nie ma nigdy) albo dać drugi alarm w tym samym epizodzie. Cron startuje zawsze o 00:30 UTC, więc `now − 24 h` odpowiada chwili wczorajszego podsumowania z dokładnością do różnicy czasu trwania nocnych przebiegów; reguła nadal nie zapisuje stanu.

## Phase 1: Miara i reguła progu (backend)

### Overview

Próg przy źródle, zapytanie zbiorcze po oknie, komponent okna jakości z regułą stanu i przejścia oraz indeks, który to zapytanie obsługuje.

### Changes Required:

#### 1. Próg przy źródle

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/Source.java`, `sources/CLAUDE.md`, `REPOSITORY_GUIDELINES.md` (Hard Rules, rejestracja źródła)

**Intent**: Każde źródło deklaruje swój próg udziału nieudanych prób, więc `posts/` i `pipeline/` stosują go bez nazywania źródła, a nowe źródło nie skompiluje się bez progu.

**Contract**: `WYKOP(5)`, `REDDIT(25)`; `int failureThresholdPercent()`. Javadoc: skąd wartości (`context/changes/per-source-failure-quality/evidence/README.md`), że zmiana to PR. Rejestracja nowego źródła w `REPOSITORY_GUIDELINES.md` i `sources/CLAUDE.md` wymienia też próg. Test jednostkowy przypina obie wartości.

#### 2. Zapytanie zbiorcze

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/AssessmentRepository.java` (+ rekord wiersza w `posts/`)

**Intent**: Jedno zapytanie zwraca dla okna `[from, until)` liczbę prób z modelem na (źródło, klasa), z wierszem `ok` jako klasą `null`, bez wierszy prefiltra.

**Contract**: `List<SourceClassCount> countModelAttemptsBySourceAndClass(Instant from, Instant until)`; rekord `SourceClassCount(Source source, @Nullable FailureClass failureClass, long count)`; filtr `a.resultSource = MODEL and a.assessedAt >= :from and a.assessedAt < :until`, `group by p.source, a.failureClass`. Enumy bindowane parametrami, nie literałami w JPQL (jak `sumWithStatusesBySource`).

#### 3. Indeks

**File**: `szczur-radar-api/src/main/resources/db/migration/V14__assessments_assessed_at_index.sql`

**Intent**: Zapytanie po zakresie `assessed_at` nie przeszukuje całej tabeli przy każdej nocy i wejściu na `/admin`.

**Contract**: `CREATE INDEX ix_assessments_assessed_at ON assessments (assessed_at);` z komentarzem (S-35). Bez nowej tabeli, więc `tools/db-backup/tables.sh` bez zmian; `JpaSchemaIT` i `MigrationsIT` zielone.

#### 4. Okno jakości i reguła

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/AssessmentQuality.java` (+ rekordy, enum stanu)

**Intent**: Komponent tylko do odczytu, który dla dnia `D` (Warszawa) zwraca okno `[D−6, D]` z wynikiem na źródło i dla nocy mówi, które źródła przeszły przez próg.

**Contract**:
- stałe `WINDOW_DAYS = 7`, `MIN_ATTEMPTS = 100`;
- strefa jako parametr, nie import: korzeń `posts/` nie importuje nic z `pipeline/` (a `pipeline/` importuje `posts/`), więc `PipelineRunService.ZONE` podają wołający (`NightSummary`, kontroler admina), wzorem `MissingDays.Coverage.of(range, ZONE)` (`PipelineRunController.java:167`); bez drugiego literału `"Europe/Warsaw"`;
- `QualityWindow window(LocalDate to, ZoneId zone)` → `QualityWindow(LocalDate from, LocalDate to, List<SourceQuality> sources)`, źródła z co najmniej jedną próbą w oknie, w kolejności `Source`;
- `SourceQuality(Source source, long attempts, long failed, Map<FailureClass, Long> classes /* tylko niezerowe, kolejność FailureClass */, int thresholdPercent, QualityStatus status)`;
- `enum QualityStatus { OK, ABOVE_THRESHOLD, NOT_ENOUGH_ATTEMPTS }` z `value()` małymi literami (`ok`, `above_threshold`, `not_enough_attempts`) i `@JsonValue`; nie jest zapisywany w bazie (bez `CHECK`, bez `ClosedSetsIT`);
- `List<SourceQuality> crossed(LocalDate night, Instant now, ZoneId zone)`: źródła ze stanem `ABOVE_THRESHOLD` w `window(night)`, które w oknie poprzedniej nocy `[początek night − 7, min(początek night, now − 24 h))` nie miały `ABOVE_THRESHOLD`; okno liczone wewnętrznie z parametrem `until` (to samo zapytanie `countModelAttemptsBySourceAndClass(from, until)`), `now` z `Clock` wołającego; dwa zapytania, żadnego zapisanego stanu;
- reguła i zaokrąglanie jak w „Critical Implementation Details”.

### Success Criteria:

#### Automated Verification:

- `AssessmentQualityTest` (unit, bez Springa): `ok` / `above_threshold` / `not_enough_attempts`; brzegi — 99 i 100 prób, udział równy progowi (`ok`) i o jedną próbę wyżej (`above_threshold`); przejście: wczoraj `ok` → dziś ponad (alarm), wczoraj za mało prób → dziś ponad (alarm), ponad dwa dni z rzędu (bez alarmu), dziś za mało prób (bez alarmu); klasy niezerowe w kolejności `FailureClass`
- `AssessmentQualityIT` (Postgres): prefiltr pominięty, próby przebiegów admina i crona liczone, wiersz z `assessed_at` 23:59 dnia `D−7` poza oknem `[D−6, D]` i w oknie `[D−7, D−1]`, granica dnia przy zmianie czasu (25-godzinna doba), dwa źródła z różnymi klasami; nieudane próby admina z `D−1` o 12:00 poza oknem porównania `crossed` (przejście dziś daje alarm)
- Test progów przy `Source` przypina `WYKOP` 5 i `REDDIT` 25
- `./mvnw -B verify` zielone (`JpaSchemaIT`, `MigrationsIT` z V14)

---

## Phase 2: Podsumowanie nocy i alarm

### Overview

Linie jakości w podsumowaniu nocy, nowe zdarzenie ntfy przy przejściu, testy API i crona, dokumentacja.

### Changes Required:

#### 1. Linie jakości w podsumowaniu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/NightSummary.java`, `pipeline/NightSummaryTest.java`, `pipeline/NightSummaryIT.java`

**Intent**: `NightSummary.of` dokłada okno jakości dla daty nocy i listę źródeł, które przeszły przez próg; tekst dostaje jedną linię jakości na źródło z okna, po liniach źródeł, przed „Dead in admin runs…”.

**Contract**: `Night` dostaje `QualityWindow quality` i `List<SourceQuality> crossed`; format linii jak w „Desired End State” (`<source> quality 7d (<from>…<to>): failed X.X% of N attempts (<klasa> X.X%, …); threshold T%`, `; ABOVE THRESHOLD` przy `above_threshold`, `…: N attempts, fewer than 100, threshold not checked` przy `not_enough_attempts`, bez nawiasu klas przy 0 nieudanych). Przykład w javadocu klasy i przykładowa noc w `NightSummaryTest` zaktualizowane; test 4096 bajtów zostaje zielony z liniami jakości dla każdego źródła.

#### 2. Zdarzenie `ASSESSMENT_QUALITY`

**File**: `alerts/AlertEvent.java`, `pipeline/api/InternalPipelineRunController.java`, `alerts/NtfyAlertsTest.java`, `pipeline/api/InternalPipelineRunIT.java`

**Intent**: Gdy `crossed` nie jest puste, kontroler — po `NIGHT_SUMMARY` — wysyła jedno zdarzenie o priorytecie 4 z opisem, co sprawdzić.

**Contract**: `ASSESSMENT_QUALITY("the share of failed assessment attempts of a source crossed its threshold over the last 7 days; check the classes in /admin and the failed posts", HIGH_PRIORITY)`; `OperatorAlertQueue.raise(alerts, ASSESSMENT_QUALITY, night.alertRunId(), detail)`; detail: na źródło `<source>: failed X.X% of N attempts in 7 days (<from>…<to>), above its threshold T% (the day before: Y.Y% of M attempts | M attempts, fewer than 100) (<klasy>)`, źródła rozdzielone `; `, na końcu `; /admin`. Bez treści postów i bez sekretów. Odpowiedź trasy (`{"text": …}`) bez zmian kształtu.

#### 3. Cron

**File**: `tools/pipeline-trigger/test.sh`, `tools/pipeline-trigger/README.md`

**Intent**: Dla spójności przykładu (formatu nie przypinają — robi to `NightSummaryTest`) atrapa API i asercje logu crona mają nowe linie, README pokazuje przykładowy log z nimi; `trigger.sh` się nie zmienia (loguje każdą linię).

**Contract**: fixture `SUMMARY` (`test.sh:91-97`) i `log_has_the_summary` (`:312-317`) z liniami jakości; sample logu w README (`:64-68`).

#### 4. Dokumentacja i PRD

**File**: `pipeline/CLAUDE.md` (NightSummary, tabela zdarzeń), `REPOSITORY_GUIDELINES.md` (`alerts`, `pipeline`, Daily run), `context/deployment/deploy-plan.md` (nowa sekcja S-35), `context/foundation/prd.md` (FR-033), `context/foundation/roadmap.md` (S-35: odnośnik `contract-v2.md` §9 → §12)

**Intent**: Miara, progi, okno, reguła przejścia i postępowanie po alarmie są opisane tam, gdzie operator i agent ich szukają.

**Contract**:
- PRD: doprecyzowanie pod FR-033 z datą 2026-11-11 (progi Wykop 5 %, Reddit 25 %, okno 7 dni, minimum 100 prób, alarm przy przejściu, mianownik: próby z modelem; dane w `evidence/`), FR bez zmian treści; jedno zdanie, że mianownik to zapisane próby (`assessments`), więc awaria dostawcy bez odpowiedzi po ponowieniach (S-09), błędy klienta i naszego kodu (S-17), przerwany przebieg i posty pominięte po wyłączniku odmów (S-32) są poza miarą i mają własne sygnały (`failed` przebiegu); `provider_error`/`timeout` w mierze to tylko wywołania, które zużyły próbę;
- `deploy-plan.md` sekcja `## S-35: …` ze statusem „**Status:** delivered by PR #N; production verification and closure in issue #M.”, opisem linii i alarmu, postępowaniem po `ASSESSMENT_QUALITY` (która klasa rośnie → lista „Nieudane posty”, recordings/model, możliwy incydent dostawcy jak 2026-10-09), zdaniem o tym, czego miara nie liczy (to samo co w PRD: awarie bez odpowiedzi i błędy techniczne mają sygnały S-09/S-17/S-32), zmianą progu (PR w `Source`) i runbookiem pierwszej nocy po merge'u (log crona z liniami `quality 7d`, wiadomość w ntfy);
- roadmapa: tylko poprawka odnośnika w wpisie S-35 (status zmienia `/10x-implement`); po zmianie `roadmap.md` dane w `tools/render_roadmap_svg.py` i `python3 tools/render_roadmap_svg.py`.

### Success Criteria:

#### Automated Verification:

- `NightSummaryTest` zielony z przykładową nocą zawierającą linię `ok`, `ABOVE THRESHOLD` i `fewer than 100`, oraz z limitem 4096 bajtów
- `NightSummaryIT` zielony: linie jakości z zasianych wierszy, `crossed` z jednym źródłem przechodzącym przez próg i drugim ponad progiem dwa dni z rzędu
- `InternalPipelineRunIT` zielony: `ASSESSMENT_QUALITY` wysłane raz dla przejścia (z `NIGHT_SUMMARY`), niewysłane bez przejścia
- `NtfyAlertsTest` zielony: `ASSESSMENT_QUALITY` z `Priority: 4` i tytułem `Szczur Radar ASSESSMENT_QUALITY`
- `tools/pipeline-trigger/test.sh` zielony
- `./mvnw -B verify` zielone

---

## Phase 3: Sekcja w `/admin`

### Overview

Trasa admina z bieżącym oknem jakości, kontrakt SPA i sekcja „Jakość ocen (7 dni)” w `AdminView`.

### Changes Required:

#### 1. Trasa

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/AssessmentQualityController.java`; testy `AuthorizationCompletenessIT` / `AdminRoleIT` (`ADMIN_ROUTES`), `contract/ContractRegistry.java` (`SHAPES`), nowe `posts/api/AssessmentQualityControllerIT.java`

**Intent**: `GET /api/admin/assessment-quality` zwraca `AssessmentQuality.window(dziś w Warszawie)` z wstrzykniętego `Clock`; tylko admin, tylko odczyt.

**Contract**: `{"from": "YYYY-MM-DD", "to": "YYYY-MM-DD", "windowDays": 7, "minAttempts": 100, "sources": [{"source", "attempts", "failed", "classes": [{"failureClass", "count"}], "thresholdPercent", "status"}]}` — liczby, nie procenty (SPA formatuje udział do wyświetlenia, stan liczy API); `classes` tylko niezerowe, w kolejności `FailureClass`; `sources` puste bez prób w oknie. Plik kontraktu zapisuje IT (`ContractFiles`), z każdym stanem `status` i niepustą listą `classes`. Kształt to rekordy zagnieżdżone w kontrolerze, nie `QualityWindow` wprost (jego `Map` nie zna `ContractVariantsTest.leaf()`, a `record()` wymaga kluczy JSON równych komponentom): `QualityView(LocalDate from, LocalDate to, int windowDays, int minAttempts, List<SourceQualityView> sources)`, `SourceQualityView(Source source, long attempts, long failed, List<ClassCountView> classes, int thresholdPercent, QualityStatus status)`, `ClassCountView(FailureClass failureClass, long count)`, z `QualityView.of(QualityWindow)` wzorem `PipelineRunController.MissingDaysView.of` (:211-221); wpis `SHAPES` z `type("…AssessmentQualityController$QualityView")` (proste nazwy rekordów unikalne w kontrakcie).

#### 2. Kontrakt SPA

**File**: `szczur-radar-web/src/contract/assessment-quality.json`, `src/contract/samples.ts`, `src/contract/rules.json` (`values.qualityStatus`), `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/SpaRulesTest.java`, `szczur-radar-web/src/api/quality.ts`, `src/labels.ts`

**Intent**: Typ odpowiedzi bez pól opcjonalnych, próbka zgodna z plikiem, wartości stanu z etykietą w rejestrze reguł po obu stronach.

**Contract**: `fetchAssessmentQuality()` przez `src/api/client.ts`; typ `QualityStatus = 'ok' | 'above_threshold' | 'not_enough_attempts'`; etykiety `w normie` / `ponad progiem` / `za mało prób` w `labels.ts`; `shapes.test.ts`, `responseTypes.test.ts`, `rules.test.ts`, `SpaRulesTest`, `ContractRoutesIT`, `ContractVariantsTest` zielone.

#### 3. Sekcja w `AdminView`

**File**: `szczur-radar-web/src/views/AdminView.vue`, `src/views/AdminView.test.ts`, `szczur-radar-web/dev/mock-api.js`

**Intent**: Sekcja „Jakość ocen (7 dni)” wzorem „Dni bez danych”: okno (od–do), na źródło nazwa (`postOrigin`/etykieta źródła), udział nieudanych z liczbą prób, udziały klas z etykietami `failureClass`, próg i stan jako `StatusBadge` (ton: `ok` → `success`, `above_threshold` → `warning`, `not_enough_attempts` → `neutral`; tony z `StatusBadge.vue:3`), pusty stan „Brak ocen w ostatnich 7 dniach”, błąd przez `StatusMessage`, ładowanie przez `aria-busy`.

**Contract**: tylko istniejące komponenty i tokeny (`styleContract.test.ts`); jeśli powstanie nowy wspólny komponent, idzie do `src/components/` i na kitchen sink. Mock serwuje trasę w trybie domyślnym, pustym i błędu. Procent formatowany w SPA tylko do wyświetlenia (`Intl.NumberFormat('pl-PL')`, jedno miejsce po przecinku).

### Success Criteria:

#### Automated Verification:

- `AssessmentQualityControllerIT` zielony: 401 bez sesji, 403 dla użytkownika bez roli admina, treść dla fixed `Clock` równa plikowi kontraktu
- `./mvnw -B verify` zielone (`AuthorizationCompletenessIT`, `ContractRoutesIT`, `ContractVariantsTest`, `SpaRulesTest`)
- `npm test` zielone (`AdminView.test.ts`: dane, pusty stan, błąd, ładowanie; `shapes.test.ts`, `rules.test.ts`, `responseTypes.test.ts`, `styleContract.test.ts`)
- `npm run build` zielone
- Zrzuty `playwright-cli` sekcji na `dev/mock-api.js`: oba motywy, 375 px, 7 stanów (default, hover, focus-visible, disabled, error, empty, loading) albo N/A z powodem, zapisane w folderze zmiany

---

## Phase 4: Weryfikacja w PR env

### Overview

CI PR-a, trasa i sekcja `/admin` oraz podsumowanie nocy w Railway PR env, sprawdzone przez agenta; ocena formatu przez właściciela.

### Changes Required:

#### 1. Kontrola w PR env (agent)

**File**: brak zmian kodu; wyniki w komentarzu issue S-35

**Intent**: Pokazać na żywym środowisku, że trasa i sekcja działają, a cron loguje i wysyła linie jakości.

**Contract**: jednorazowe konto admina w PR env (lekcja „W PR env agent sam zakłada jednorazowe konto admina…”: `create-user --role admin` przez `railway ssh`, link i hasło tylko w zmiennych powłoki, ciasteczka w scratchpadzie); `curl` `GET /api/admin/assessment-quality` z nagłówkiem `Origin`; `playwright-cli` sekcji `/admin` z ciasteczkiem; noc w PR env procedurą S-34 (losowy testowy temat ntfy jako `NTFY_URL`, `TRIGGER_IN_ANY_ENVIRONMENT=true` i tymczasowy harmonogram crona — prawdziwe wywołania Wykopu, Reddita i LLM są w PR env dozwolone), potem usunięcie tych zmiennych. `ASSESSMENT_QUALITY` w PR env się nie pojawi (za mało prób) — dowodzą go testy z fazy 2.

### Success Criteria:

#### Automated Verification:

- CI PR-a zielone: `gh pr checks <PR> --watch` kończy się bez czerwonego joba
- `GET /api/admin/assessment-quality` w PR env zwraca 200 z kluczami `from`, `to`, `windowDays`, `minAttempts`, `sources` (`curl … | jq -e 'has("sources")'`), a bez sesji 401
- Log crona w PR env (`railway logs -s pipeline-trigger -e szczur-radar-pr-<N>`) ma linie `summary: … quality 7d …` przed `OK: …`, a testowy temat ntfy (`curl -s "https://ntfy.sh/<topic>/json?poll=1&since=2h"`) wiadomość `Szczur Radar NIGHT_SUMMARY` z tymi liniami
- Zrzut sekcji „Jakość ocen (7 dni)” w PR env przez `playwright-cli` z ciasteczkiem jednorazowego admina, zapisany w folderze zmiany; zmienne testowe (`NTFY_URL`, `TRIGGER_IN_ANY_ENVIRONMENT`, harmonogram) usunięte z PR env

#### Manual Verification:

- Właściciel akceptuje format linii jakości w podsumowaniu nocy i opis zdarzenia `ASSESSMENT_QUALITY` (wiadomość z PR env i przykład z testu)
- Właściciel akceptuje wygląd i słowa sekcji „Jakość ocen (7 dni)” (zrzuty z fazy 3 i PR env)

---

## Testing Strategy

### Unit Tests:

- `AssessmentQualityTest`: reguła stanu na brzegach (99/100 prób, udział równy progowi i o jedną próbę wyżej), cztery przypadki przejścia, kolejność i pomijanie zerowych klas, zaokrąglanie `HALF_UP`.
- `NightSummaryTest`: linie jakości we wszystkich trzech stanach, brak nawiasu klas przy 0 nieudanych, limit 4096 bajtów.
- `NtfyAlertsTest`: priorytet i tytuł nowego zdarzenia.
- Vitest `AdminView.test.ts`: dane, pusty stan, błąd, ładowanie; testy kontraktu SPA.

### Integration Tests:

- `AssessmentQualityIT`: zapytanie na Postgresie — prefiltr, przebiegi admina i crona, granice dni Warszawy (także 25-godzinna doba), dwa źródła.
- `AssessmentQualityControllerIT`: trasa admina z autoryzacją i plikiem kontraktu.
- `NightSummaryIT`, `InternalPipelineRunIT`: linie i alarm przy przejściu z zasianych wierszy, brak alarmu bez przejścia.
- `tools/pipeline-trigger/test.sh`: log crona z nowymi liniami.

### Manual Testing Steps:

1. Właściciel czyta wiadomość `NIGHT_SUMMARY` z PR env i przykład `ASSESSMENT_QUALITY` z testu, akceptuje format.
2. Właściciel ogląda zrzuty sekcji `/admin` (fazy 3 i 4), akceptuje słowa i wygląd.

## Performance Considerations

Dwa zapytania na noc (okno dziś i wczoraj) i jedno na wejście w `/admin`, każde po zakresie `assessed_at` (~3 700 prób z modelem w oknie 7 dni dziś) przez nowy indeks `ix_assessments_assessed_at`; złączenie z `posts` po kluczu głównym.

## Migration Notes

V14 tylko dodaje indeks (`CREATE INDEX`, nie `CONCURRENTLY` — Flyway w transakcji; tabela ma dziś kilkadziesiąt tysięcy wierszy, blokada zapisu trwa ułamek sekundy w pre-deploy `migrate`, przed startem nowej wersji API). Wycofanie: revert PR-a zostawia indeks (fix-forward, nieszkodliwy); progi zmienia się PR-em w `Source`.

## References

- Roadmapa: `context/foundation/roadmap.md` — S-35 (`:223-234`), S-36 (`:236-248`), Parked „Ochrona przed dryfem modelu” (`:405`)
- PRD: FR-033 (`context/foundation/prd.md:268-269`), doprecyzowanie PRD v3 o nieudanych próbach bez sygnału (`:287`)
- Kontrakt: `context/foundation/llm-assessment/contract-v2.md` §12 (`:334-376`)
- Destylacja: `context/domain/domain-distillation.md` — ranking #1 (`:310`), R-15 (`:214`)
- Dane z produkcji: `context/changes/per-source-failure-quality/evidence/README.md`, `evidence/prod-failures-2026-11-11.csv`
- Pomiar S-28: `context/archive/2026-10-30-reddit-quality-and-cost/report-2026-10-31.md` (`:29-39`, `:119`)
- Precedensy: S-34 `context/changes/dead-posts-daily-digest/plan.md`, `context/deployment/deploy-plan.md` (`:2281-2372`); S-32 `context/archive/2026-11-08-provider-refusal-breaker/plan-brief.md` (`:21-23`)
- Kod: `pipeline/NightSummary.java:108-156`, `:251-284`; `posts/AssessmentRepository.java:42-61`; `pipeline/api/InternalPipelineRunController.java:109-121`; `alerts/AlertEvent.java:51-60`; `alerts/OperatorAlertQueue.java:31-38`; `szczur-radar-web/src/views/AdminView.vue:373-396`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Miara i reguła progu (backend)

#### Automated

- [x] 1.1 `AssessmentQualityTest` (unit): stany, brzegi 99/100 prób i progu, cztery przypadki przejścia, kolejność klas — c7c816e
- [x] 1.2 `AssessmentQualityIT` (Postgres): prefiltr pominięty, przebiegi admina i crona, granice dni Warszawy z 25-godzinną dobą, dwa źródła, okno porównania ucięte do `now − 24 h` — c7c816e
- [x] 1.3 Test progów przy `Source` przypina `WYKOP` 5 i `REDDIT` 25 — c7c816e
- [x] 1.4 `./mvnw -B verify` zielone (`JpaSchemaIT`, `MigrationsIT` z V14) — c7c816e

### Phase 2: Podsumowanie nocy i alarm

#### Automated

- [x] 2.1 `NightSummaryTest` zielony z liniami `ok`, `ABOVE THRESHOLD`, `fewer than 100` i limitem 4096 bajtów — 3d49983
- [x] 2.2 `NightSummaryIT` zielony: linie jakości i `crossed` (przejście vs ponad progiem dwa dni z rzędu) — 3d49983
- [x] 2.3 `InternalPipelineRunIT` zielony: `ASSESSMENT_QUALITY` raz przy przejściu, brak bez przejścia — 3d49983
- [x] 2.4 `NtfyAlertsTest` zielony: `ASSESSMENT_QUALITY` z `Priority: 4` i tytułem — 3d49983
- [x] 2.5 `tools/pipeline-trigger/test.sh` zielony — 3d49983
- [x] 2.6 `./mvnw -B verify` zielone — 3d49983

### Phase 3: Sekcja w `/admin`

#### Automated

- [x] 3.1 `AssessmentQualityControllerIT` zielony: 401, 403, treść dla fixed `Clock` równa plikowi kontraktu
- [x] 3.2 `./mvnw -B verify` zielone (`AuthorizationCompletenessIT`, `ContractRoutesIT`, `ContractVariantsTest`, `SpaRulesTest`)
- [x] 3.3 `npm test` zielone (`AdminView.test.ts` i testy kontraktu SPA, `styleContract.test.ts`)
- [x] 3.4 `npm run build` zielone
- [x] 3.5 Zrzuty `playwright-cli` sekcji na mocku: oba motywy, 375 px, 7 stanów lub N/A z powodem

### Phase 4: Weryfikacja w PR env

#### Automated

- [ ] 4.1 CI PR-a zielone (`gh pr checks <PR> --watch`)
- [ ] 4.2 `GET /api/admin/assessment-quality` w PR env: 200 z kluczami okna, 401 bez sesji
- [ ] 4.3 Log crona i testowy temat ntfy w PR env z liniami `quality 7d`
- [ ] 4.4 Zrzut sekcji w PR env przez `playwright-cli`, zmienne testowe usunięte z PR env

#### Manual

- [ ] 4.5 Właściciel akceptuje format linii jakości i opis `ASSESSMENT_QUALITY`
- [ ] 4.6 Właściciel akceptuje wygląd i słowa sekcji „Jakość ocen (7 dni)”
