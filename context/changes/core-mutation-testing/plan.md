# Testy mutacyjne rdzenia (S-45) — plan implementacji

## Overview

Zielony test mówi tylko tyle, że kod robi to, co test sprawdza. Nie mówi, czy test złapie błąd (`verification-scope.md:7`). Audyt po M-3 pokazał, że dzisiejsza ochrona rdzenia bywa przypadkowa: trzyma, bo jakiś test akurat coś zapisuje, a nie dlatego, że ktoś ją zaplanował (`post-m3-verification-audit/research.md:253`, eksperymenty X1, X2).

S-45 mierzy ją systematycznie:

- PIT mutuje kod Javy rdzenia: przebieg i warunkowe zapisy stanu, walidator kontraktu V2 oraz rozliczanie prób oceny.
- Każdy mutant, który przeżył, jest zabity testem albo wpisany do rejestru z powodem.
- Strażnik rejestru czerwieni CI przy ocalałym spoza rejestru i przy wpisie, który przestał być potrzebny.
- Strażników zapisanych w SQL PIT nie zmutuje. Psujemy je raz ręcznie, a luki zamykamy testami IT.

## Current State Analysis

- **PIT-a nie ma.** W `szczur-radar-api/pom.xml` nie ma pitest ani JaCoCo. Spring Boot 4.0.8 daje JUnit Jupiter i Platform 6.0.3, Mockito 5.20.0 i Surefire/Failsafe 3.5.6. Agenta Mockito nikt nie podpina: nie ma `argLine`, Mockito dołącza się samo.
- **Testy:**
  - Surefire uruchamia `**/*Test.java`, z `groups`/`excludedGroups` z właściwości `surefire.groups` i `surefire.excludedGroups` (`pom.xml:28-30`, `:175-185`).
  - Profil `measurement` zamienia te właściwości (`pom.xml:212-221`).
  - Failsafe uruchamia `**/*IT.java` w losowej kolejności (`pom.xml:188-207`).
  - Licznik: 95 `*Test` i 41 `*IT`. W trzech obszarach rdzenia jest 29 `*Test` i 20 `*IT`.
- **Logika rdzenia w Javie, którą PIT zmutuje:**
  - `pipeline/assess/ConcurrentAssessmentDispatcher` (661 linii): trzy wyłączniki serii, licznik odmów, reset serii, ponowienia, typ `Outcome`.
  - `pipeline/flow/RunSequence`, czyli kolejność kroków i status przebiegu.
  - `pipeline/run/PipelineRun.attentionReasons` (`PipelineRun.java:316-350`).
  - `pipeline/state/PostTransitions.Outcome.after`, czyli liczenie prób i próg martwego posta (`PostTransitions.java:224-233`).
  - `pipeline/step/*`, `PipelineRunService`, `RunWatchdog`.
  - Cały walidator `agents/assessor/**`: `OutputParser`, 15 reguł D1–D15 w `validation/rules/`, `Prefilter`, `Assessor`.
  - `posts/AssessmentQuality`.
  - Walidator ma wyłącznie testy jednostkowe (`ContractV2RulesTest`, `RuleBasedValidatorTest`, `OutputParserTest`, `ContractExamplesTest`).
- **Logika rdzenia w SQL, której PIT nie zmutuje:**
  - Warunkowe zapisy „tylko gdy przebieg trwa” to stringi SQL w `JdbcClient`:
    - `RunRecorder.java`: 17 zapytań, na przykład `:64`, `:98`, `:143`, `:179`, `:358`, `:379`, oraz `BY_ANOTHER_DEPLOYMENT` (`:48`).
    - `PostTransitions.java`: `RELEASE_SET` z `CASE WHEN assessment_attempts = 0` (`:44-47`), `COUNT_FAILURE` (`:52`), `claim` (`:74`), `record` (`:97`), `restore` (`:195`) i inne.
  - Predykaty prób w zapytaniach repozytoriów: `PostRepository.java:43,56,88`, `AssessmentRepository.java:64,81,110`, `PipelineRunRepository.java:59,93,113,133`.
  - Chronią je tylko testy IT: `RunRecorderIT`, `PostColumnsIT`, `PostRepositoryIT`, `PipelineRunIT`, `AssessmentQualityIT` i inne. W testach jednostkowych `RunRecorder` i `PostTransitions` są mockami.
- **CI:**
  - `ci.yml` uruchamia się na `push` do `main` i na `pull_request`. Nie ma `workflow_dispatch` ani `schedule`.
  - Job `backend` trwa 3,5–4 min, z `timeout-minutes: 15` (`ci.yml:20-64`).
  - `railway-config` ma `needs:` na każdym jobie testowym (`ci.yml:266-274`).
  - Railway Wait for CI czyta wynik całego workflow.
- **Wzorzec rejestru wyjątków:** `KNOWN_GAPS` w `ContractVariantsTest.java:34-50` to mapa wpis → powód. Wpis, który przestał być potrzebny, czerwieni test.

## Desired End State

- `./mvnw -B -Pmutation test` (z `szczur-radar-api/`):
  - mutuje klasy rdzenia grupą `STRONGER` i uruchamia przeciw nim tylko `*Test`, bez Dockera i bez sekretów;
  - zapisuje raport `target/pit-reports/mutations.xml` i `index.html`;
  - kończy się błędem, gdy któryś mutant `SURVIVED` albo `NO_COVERAGE` nie ma wpisu w rejestrze `AcceptedMutants`, albo gdy wpis rejestru nie odpowiada już żadnemu ocalałemu.
- Na końcu S-45 strażnik jest zielony. Każdy wpis rejestru ma konkretny powód: mutant równoważny (dlaczego), nieosiągalna gałąź obronna albo podobny.
- Job `mutation` w `ci.yml` uruchamia to polecenie przy każdym PR i pushu na `main`. Chodzi równolegle z `backend`, jest w `needs:` joba `railway-config` i mieści się w 10 min na `ubuntu-latest`.
- `context/changes/core-mutation-testing/sql-breaks.md` wymienia każdego strażnika SQL rdzenia: jego zepsucie i test IT, który je łapie. Luki z tej tabeli zamykają nowe testy IT.
- `REPOSITORY_GUIDELINES.md` i `szczur-radar-api/README.md` mówią, jak uruchomić pomiar i co zrobić z ocalałym mutantem.

### Key Discoveries:

- PIT 1.30.2 (ASM 9.11) obsługuje bajtkod Javy 25. `pitest-junit5-plugin` 1.2.3 wymaga pitest ≥ 1.19.4. pitest-maven sam dokłada `junit-platform-launcher` w wersji platformy projektu, ale JUnit 6 pod Mavenem nie jest potwierdzony przez nikogo. Otwarte issue pitest-junit5-plugin#113 (Gradle, starsze wersje) opisuje uruchomienie z zerem testów.
- PIT domyślnie (`parseSurefireConfig=true`) przejmuje z Surefire `groups`, `excludedGroups` i `excludes`, ale nie `includes`. Bez `targetTests` uruchomiłby też `*IT`.
- Otwarty PIT nie ma znacznika „ten mutant przyjęty”. Są tylko adnotacje pomijające całe metody i klasy (`FANN`: `@DoNotMutate`, `@Generated`) oraz `excludedMethods`. Rejestr per mutant musi więc być nasz.
- Wywołania logowania PIT pomija domyślnie (`avoidCallsTo`).
- Kod rdzenia nie woła `Instant.now()`: `Assessor` dostaje `Clock`, dyspozytor `LongSupplier nanoTime`. Mutanty zegara nie są więc problemem.

## What We're NOT Doing

- Nie uruchamiamy PIT na `*IT` (Spring, Testcontainers): mutanty Javy mierzymy tylko testami jednostkowymi.
- Nie mutujemy SQL i nie budujemy narzędzia, które by to robiło. Strażników SQL psujemy jednorazowo ręcznie (Faza 5), a w CI pilnują ich istniejące i nowe testy IT.
- Nie mutujemy PIT-em `RunRecorder` ani `PostTransitions` (poza `PostTransitions$Outcome`). To opakowania SQL, które testy jednostkowe tylko mockują; ich SQL i logikę Javy psujemy ręcznie w Fazie 5.
- Nie mierzymy `pipeline/NightSummary`, `pipeline/run/MissingDays`, kontrolerów `*/api/*`, `PipelineStallSettings`, `PipelineStartupRecovery`, `sources/`, `llm/` ani reszty aplikacji.
- Nie robimy osobnego przeglądu jakości testów (mocki, pełne ciała odpowiedzi, zegar). Test zabijający ocalałego ma sprawdzać zachowanie, ale testów, których PIT nie wskazał, nie przeglądamy.
- Nie wprowadzamy progu procentowego (`mutationThreshold`), historii przyrostowej (`withHistory`) ani płatnych rozszerzeń Arcmutate.
- Nie zmieniamy zachowania kodu produkcyjnego. Jeśli ocalały mutant ujawni prawdziwy błąd, implementacja staje i pyta właściciela; błędu nie naprawia po cichu.
- Nie dodajemy osobnego workflow: wszystko zostaje w `ci.yml`.

## Implementation Approach

1. Najpierw sprawdzamy, czy PIT w ogóle działa na tym stosie. Krótki spike rozstrzyga, czy JUnit 6 pod Mavenem znajduje testy. Potem mierzymy bazę: liczby per klasa i czas.
2. Zanim zaczniemy rozpatrywać ocalałe, powstaje rejestr i strażnik. Logika porównania ma własne testy jednostkowe na fiksturach raportu, więc chodzi też w zwykłym `./mvnw test`.
3. Ocalałe rozpatrujemy w dwóch fazach według obszaru, żeby commity dało się przejrzeć: najpierw walidator i jakość ocen, potem przebieg.
4. Zepsucia SQL to osobna faza: inna metoda (ręczna, z IT) i inne pliki.
5. Job CI i dokumentacja wchodzą na końcu, gdy strażnik jest już zielony. Wcześniej czerwony job blokowałby deploy PR env.

## Critical Implementation Details

**Kolejność w profilu `mutation`.** Strażnik `SurvivingMutantsTest` musi uruchomić się po PIT, z tego samego polecenia. Dodatkowe wykonanie Surefire w profilu połączyłoby się z wtyczką Surefire z głównego builda i mogłoby ruszyć przed PIT. Dlatego:

- PIT podpinamy do fazy `process-test-classes`: klasy testów są już skompilowane, a faza `test` jeszcze się nie zaczęła.
- Domyślne wykonanie Surefire w profilu uruchamia tylko tag `mutation`, przez właściwości `surefire.groups=mutation` i `surefire.excludedGroups=measurement`, tak jak profil `measurement`.
- Ponieważ PIT przejąłby te `groups` i uruchomił tylko strażnika, PIT dostaje `parseSurefireConfig=false` oraz własne `excludedGroups` `measurement,mutation` i `targetTests`.

**Tożsamość mutanta.** Klucz to `mutatedClass`, `mutatedMethod`, `methodDescription` (deskryptor JVM, który rozróżnia przeciążenia), `mutator` i `description` z `mutations.xml`, bez `lineNumber`, żeby edycja pliku nie unieważniała rejestru. Jeden klucz może wystąpić kilka razy (ta sama mutacja w kilku liniach metody), więc porównanie liczy krotności. Metody lambd (`lambda$name$N`) zmieniają numer po dodaniu lambdy, dlatego klucz normalizuje ich nazwę do `lambda$name` (bez numeru). Mutanty lambd przyjmuje się więc na tych samych zasadach co inne.

**Statusy.** Za ocalałe uznajemy `SURVIVED` i `NO_COVERAGE`. `KILLED`, `TIMED_OUT`, `MEMORY_ERROR`, `RUN_ERROR` i `NON_VIABLE` PIT liczy jako wykryte i my też.

## Phase 1: PIT w buildzie i pomiar bazowy

### Overview

Profil `mutation` uruchamia PIT na klasach rdzenia. Spike rozstrzyga zgodność z JUnit 6, a pomiar bazowy daje liczby per klasa i czas.

### Changes Required:

#### 1. Profil `mutation`

**File**: `szczur-radar-api/pom.xml`

**Intent**: Dodaje pomiar mutacyjny, który chodzi tylko na żądanie (`-Pmutation`) i nie zmienia zwykłego `test` ani `verify`.

**Contract**:

- Profil `mutation` ma `org.pitest:pitest-maven` 1.30.2, z zależnością wtyczki `org.pitest:pitest-junit5-plugin` 1.2.3 (wersje we właściwościach) i celem `mutationCoverage` w fazie `process-test-classes`.
- Konfiguracja:
  - `targetClasses`:
    - `pl.szczurradar.api.agents.assessor.*`
    - `pl.szczurradar.api.pipeline.assess.*`, `pl.szczurradar.api.pipeline.flow.*`, `pl.szczurradar.api.pipeline.state.*`, `pl.szczurradar.api.pipeline.run.*`, `pl.szczurradar.api.pipeline.step.*`
    - `pl.szczurradar.api.pipeline.PipelineRunService*`, `pl.szczurradar.api.pipeline.RunWatchdog*`
    - `pl.szczurradar.api.posts.AssessmentQuality*`
  - `excludedClasses`: `pl.szczurradar.api.pipeline.run.MissingDays*` i `pl.szczurradar.api.*.api.*` (podpakiety `api`). Nie `*.api.*`: w globie PIT `*` to `.*` i pasuje też do kropek, więc `*.api.*` pasowałby do pakietu bazowego `pl.szczurradar.api` i wykluczył cały kod.
  - Także `pl.szczurradar.api.pipeline.state.RunRecorder*` i `pl.szczurradar.api.pipeline.state.PostTransitions` (bez `*`, więc `PostTransitions$Outcome` zostaje w pomiarze, bo testuje go `RunOutcomeTest`). Żaden `*Test` nie tworzy tych klas, wszędzie są mockami. Ich mutanty byłyby więc w całości `NO_COVERAGE`, a pokrywa je tylko IT, czego nie wyraża żadna kategoria powodu. Chroni je Faza 5.
  - `targetTests`: `pl.szczurradar.api.*Test`.
  - `parseSurefireConfig=false`, `excludedGroups` `measurement,mutation`.
  - `mutators` `STRONGER`, `outputFormats` `XML,HTML`, `timestampedReports=false`.
  - `threads` z właściwości `pit.threads`, domyślnie 4.
- Właściwość `surefire.excludedGroups` w głównym buildzie zmienia się na `measurement,mutation`. Profil ustawia `surefire.groups=mutation` i `surefire.excludedGroups=measurement` (patrz Critical Implementation Details).
- Jeśli PIT zgłosi 0 testów albo nie znajdzie launchera JUnit 6, najpierw dodaj jawną zależność wtyczki `org.junit.platform:junit-platform-launcher` w wersji platformy z BOM. Jeśli to nie pomoże, zatrzymaj się i opisz problem właścicielowi. Nie przechodź na inny silnik testów.

#### 2. Pomiar bazowy

**File**: `context/changes/core-mutation-testing/mutation-baseline.md`

**Intent**: Zapisuje stan przed zabijaniem mutantów, żeby później było widać, co zmieniła praca w Fazach 3 i 4.

**Contract**:

- Nagłówek: SHA, wersje (PIT, wtyczka, JUnit), `threads` i czas ściany lokalnie.
- Tabela per klasa: wygenerowane, zabite, `SURVIVED`, `NO_COVERAGE`, `TIMED_OUT`, siła testów.
- Wiersz sumy dla każdego z trzech obszarów i łącznie.
- Warunek stopu: jeśli lokalny pomiar przy `threads=4` trwa dłużej niż 10 min, zatrzymaj się i przedstaw właścicielowi opcje (węższy zakres albo inne ustawienie w CI), zanim pójdziesz dalej.
- Stabilność: pomiar uruchamiamy dwa razy na tym samym SHA i porównujemy status każdego mutanta (klucz z Critical Implementation Details plus `lineNumber`). Wynik porównania jest w nagłówku. Mutant o różnym statusie w dwóch przebiegach oznacza stop i decyzję właściciela (`timeoutConstant`, `timeoutFactor`, `threads`), bo job blokujący deploy nie może czerwienieć losowo (S-30).

### Success Criteria:

#### Automated Verification:

- `./mvnw -B test` z `szczur-radar-api/` jest zielony, a raport Surefire nie zawiera klas z tagiem `mutation`. Liczba testów jest taka sama jak na `origin/main`, bo żaden taki test jeszcze nie istnieje.
- `./mvnw -B -Pmutation test` z `szczur-radar-api/` kończy się kodem 0 i tworzy `target/pit-reports/mutations.xml` z co najmniej jednym mutantem `KILLED` w `agents.assessor.validation` (spike JUnit 6 pod Mavenem: testy naprawdę chodzą).
- W `target/pit-reports/mutations.xml` nie ma mutanta w klasie spoza zakresu: `grep -cE '<mutatedClass>(pl\.szczurradar\.api\.[a-z.]*\.api\.|[^<]*(MissingDays|NightSummary|RunRecorder)|[^<]*\.PostTransitions</)' target/pit-reports/mutations.xml` = 0, a mutanty `PostTransitions$Outcome` są w raporcie.
- PIT nie uruchamia żadnej klasy `*IT` ani testu z tagiem `measurement`. PIT nie zapisuje logów w `target/`, więc sprawdza to jednorazowy przebieg z parametrem pitest-maven `fullMutationMatrix=true`: `grep -cE 'IT[.$\[]|\.measurement\.|measurement(Method|Class|ThroughTheContainer)'` na elementach `coveringTests`/`killingTests`/`succeedingTests` w `mutations.xml` = 0.
- `context/changes/core-mutation-testing/mutation-baseline.md` istnieje, ma tabelę per klasa i czas pomiaru. Czas przy `threads=4` wynosi ≤ 10 min albo właściciel zatwierdził dalszą drogę.
- Dwa pomiary na tym samym SHA dają identyczny status każdego mutanta (porównanie zapisane w `mutation-baseline.md`) albo właściciel zatwierdził ustawienie, które to zapewnia.

---

## Phase 2: Rejestr i strażnik

### Overview

Powstaje rejestr przyjętych mutantów i strażnik, który porównuje go z raportem PIT w obie strony. Logika porównania ma własne testy na fiksturach, uruchamiane w zwykłym `./mvnw test`.

### Changes Required:

#### 1. Odczyt raportu i porównanie

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/mutation/` (nowy pakiet testowy)

**Intent**: Czysta logika: czyta `mutations.xml` i porównuje ocalałe z rejestrem jako multizbiory.

**Contract**:

- Rekord mutanta: `mutatedClass`, `mutatedMethod`, `methodDescription`, `mutator`, `description`, `status`. `lineNumber` służy tylko do komunikatu.
- Wynik porównania ma dwie listy: ocalałe bez wpisu (z kluczem i linią) oraz wpisy bez ocalałego.
- `SURVIVED` i `NO_COVERAGE` to ocalałe, każdy inny status to wykryte.
- Parser XML z JDK, bez nowej zależności.

#### 2. Rejestr `AcceptedMutants`

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/mutation/AcceptedMutants.java`

**Intent**: Jedno miejsce na przyjęte mutanty z powodem, w stylu `KNOWN_GAPS` (`ContractVariantsTest.java:50`).

**Contract**:

- Lista wpisów: klucz mutanta i powód.
- Powód jest niepusty i konkretny: mówi, dlaczego mutant jest równoważny albo dlaczego zabijający go test nic by nie chronił.
- Powód zaczyna się od kategorii:
  - `równoważny:` — dlaczego zachowanie się nie zmienia;
  - `obronny:` — gałąź nieosiągalna przy kontrakcie wejścia, z odwołaniem do miejsca, które ją wyklucza;
  - `tekst:` — mutant zmienia tylko tekst komunikatu, który nie jest kontraktem.
- Javadoc klasy podaje regułę: ocalały → test na zachowaniu albo wpis z powodem; metoda lambdy w kluczu jest bez numeru (`lambda$name`).
- Na koniec tej fazy rejestr jest pusty.

#### 3. Strażnik na prawdziwym raporcie

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/mutation/SurvivingMutantsTest.java`

**Intent**: Uruchamiany tylko w profilu `mutation` (`@Tag("mutation")`), zaraz po PIT. Czerwony przy każdej rozbieżności.

**Contract**:

- Czyta `target/pit-reports/mutations.xml`. Brak pliku to błąd z poleceniem `./mvnw -Pmutation test`, nie pominięcie testu.
- Komunikat błędu wypisuje każdego ocalałego bez wpisu jako gotowy do wklejenia wpis rejestru (z linią w komentarzu) i każdy zbędny wpis.

#### 4. Testy porównania na fiksturach

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/mutation/MutantRegistryTest.java`, `szczur-radar-api/src/test/resources/fixtures/pit/*.xml`

**Intent**: Dowodzi, że strażnik łapie każdy rodzaj rozbieżności. Chodzi w zwykłym `./mvnw test`, więc pilnuje go zwykłe CI.

**Contract**: przypadki:

- ocalały bez wpisu → na liście;
- `NO_COVERAGE` bez wpisu → na liście;
- ocalały z wpisem → OK;
- wpis, którego mutant jest `KILLED` albo `TIMED_OUT` → wpis zbędny;
- dwa ocalałe z tym samym kluczem i jeden wpis → jeden bez wpisu;
- ten sam klucz w innej linii nadal pasuje;
- mutant w `lambda$name$3` pasuje do wpisu z `lambda$name` (inny numer lambdy nadal pasuje);
- ta sama nazwa metody z innym `methodDescription` (inne przeciążenie) → brak dopasowania;
- pusty powód → błąd;
- powód bez prefiksu kategorii → błąd (reguła sprawdzana też na prawdziwym `AcceptedMutants`).

Fikstury to wycinki prawdziwego `mutations.xml` z Fazy 1.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B test` z `szczur-radar-api/` jest zielony i uruchamia `MutantRegistryTest`, ale nie `SurvivingMutantsTest` (raport Surefire).
- `./mvnw -B -Pmutation test` z `szczur-radar-api/` kończy się błędem z `SurvivingMutantsTest`, a liczba ocalałych bez wpisu w komunikacie równa się sumie `SURVIVED` + `NO_COVERAGE` z `mutation-baseline.md`, bo rejestr jest pusty.
- Celowe zepsucie: odwrócone porównanie statusu w logice porównania (na przykład `NO_COVERAGE` liczone jako wykryte) czerwieni `MutantRegistryTest`. Po cofnięciu zepsucia test jest znowu zielony.

---

## Phase 3: Ocalałe — walidator V2 i jakość ocen

### Overview

Każdy ocalały w `agents.assessor.*` i `posts.AssessmentQuality*` zostaje zabity testem albo wpisany do `AcceptedMutants` z powodem.

### Changes Required:

#### 1. Testy zabijające

**File**: istniejące testy obszaru: `agents/assessor/**/*Test.java` (m.in. `ContractV2RulesTest`, `RuleBasedValidatorTest`, `OutputParserTest`, `PrefilterTest`, `AssessorTest`, `ProvenanceTest`) i `posts/AssessmentQualityTest.java`.

**Intent**: Nowy przypadek albo mocniejsza asercja zabija ocalałego przez obserwowalne zachowanie: wynik walidacji, rodzaj naruszenia, wynik parsowania, liczba, próg. Nie przez `verify()` na mocku.

**Contract**:

- Nowe dane testowe biorą się z `ContractFixtures` i `ContractExamples`, jeśli pasują.
- Przypadki graniczne nazywa się w nazwie testu, na przykład granica D-reguły albo próg 100 prób.
- Bez zmian w `src/main`.

#### 2. Wpisy rejestru

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/mutation/AcceptedMutants.java`

**Intent**: Przyjmuje tylko mutanty, których test nie odróżni od oryginału albo których zabicie nic nie chroni.

**Contract**: każdy wpis ma powód z kategorią z Fazy 2 (`równoważny:`, `obronny:`, `tekst:`).

### Success Criteria:

#### Automated Verification:

- `./mvnw -B test` z `szczur-radar-api/` jest zielony.
- `./mvnw -B -Pmutation test` z `szczur-radar-api/`: komunikat strażnika nie wymienia żadnego ocalałego bez wpisu w klasie `pl.szczurradar.api.agents.assessor.*` ani `pl.szczurradar.api.posts.AssessmentQuality*` (`grep -c` = 0 na wyjściu).
- `git diff --stat origin/main -- szczur-radar-api/src/main` jest pusty.
- Każdy wpis `AcceptedMutants` w tych klasach zaczyna powód od `równoważny:`, `obronny:` albo `tekst:` (sprawdza to `MutantRegistryTest` z Fazy 2 w `./mvnw -B test`).

---

## Phase 4: Ocalałe — przebieg

### Overview

To samo dla `pipeline.assess/flow/state/run/step`, `PipelineRunService` i `RunWatchdog`. Na koniec strażnik na prawdziwym raporcie jest zielony.

### Changes Required:

#### 1. Testy zabijające

**File**: istniejące testy obszaru, m.in. `pipeline/assess/ConcurrentAssessmentDispatcherTest.java`, `pipeline/flow/RunSequenceTest.java`, `pipeline/run/PipelineRunTest.java`, `pipeline/state/RunOutcomeTest.java`, `pipeline/step/*Test.java`, `pipeline/PipelineRunServiceTest.java`, `pipeline/RunWatchdogTest.java`.

**Intent**: Jak w Fazie 3: zabicie przez zachowanie, czyli status i wynik przebiegu, kolejność kroków, zatrzymanie po serii, powody „wymaga uwagi”, liczbę prób.

**Contract**:

- Tam, gdzie zachowaniem jest właśnie warunkowy zapis stanu (`RunRecorder`, `PostTransitions` są w tych testach mockami), dopuszczalna jest asercja na wywołanie z dokładnymi argumentami. Tylko wtedy, gdy to wywołanie jest wynikiem, który klasa ma dać.
- Bez zmian w `src/main`.

#### 2. Wpisy rejestru

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/mutation/AcceptedMutants.java`

**Intent**: Jak w Fazie 3.

**Contract**: te same kategorie powodów. `RunRecorder` i `PostTransitions` (bez `Outcome`) są poza pomiarem PIT (Faza 1), a ich logikę chroni Faza 5. Jeśli inna klasa w zakresie okaże się w pomiarze bazowym pokryta wyłącznie przez IT (na przykład setter encji), implementacja staje i pyta właściciela: wykluczyć ją tak samo czy zabić testem jednostkowym. Nie przyjmuje się jej mutantów jako `obronny:` ani `równoważny:`.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B -Pmutation test` z `szczur-radar-api/` kończy się kodem 0: strażnik nie zgłasza ocalałego bez wpisu ani zbędnego wpisu.
- `./mvnw -B verify` z `szczur-radar-api/` jest zielony.
- `git diff --stat origin/main -- szczur-radar-api/src/main` jest pusty.
- Celowe zepsucie: usunięcie jednego wpisu z `AcceptedMutants` czerwieni `./mvnw -B -Pmutation test` i wypisuje ten mutant. Po przywróceniu wpisu jest zielono.
- `context/changes/core-mutation-testing/mutation-baseline.md` ma drugą tabelę „po S-45” z tymi samymi kolumnami i liczbą wpisów rejestru per obszar.

---

## Phase 5: Ręczne zepsucia SQL

### Overview

Każdy strażnik SQL rdzenia zostaje raz zepsuty, a wynik trafia do tabeli „zepsucie → IT, który złapał”. Luki zamykają nowe testy IT. Kod produkcyjny po fazie jest niezmieniony.

### Changes Required:

#### 1. Tabela zepsuć

**File**: `context/changes/core-mutation-testing/sql-breaks.md`

**Intent**: Pokazuje, że warunkowe zapisy stanu i predykaty prób mają test, który łapie ich zepsucie. To ta sama metoda co X1 i X2 w audycie po M-3.

**Contract**: jeden wiersz na strażnika, z kolumnami: miejsce (`plik:linia`), zepsucie (co zmienione), test, który złapał (`Klasa#metoda`), albo „luka → nowy test”. Zakres:

- `RunRecorder.java`:
  - `BY_ANOTHER_DEPLOYMENT` (`:48`);
  - `start` (`:64`), `isRunning` (`:72`), `finish` (`:81`), `fail` (`:98`), `count` (`:109`), `source` (`:143`), `storeRejected` (`:179`), `touch` (`:203`), `error` (`:210`), `inFlight` (`:218`), `interrupt` (`:283`), `stall` (`:303`), `interruptAll` (`:322`), `interruptOrphans` (`:358`), `openStep` (`:379`), `endStep` (`:397`), `closeSteps` (`:420`).
- `PostTransitions.java`:
  - `RELEASE_SET` / `RELEASE_WHERE` (`:44-47`), `COUNT_FAILURE` (`:52`);
  - `claim` (`:74`), `record` (`:97`), zwolnienie jednego posta (`:121`), błąd techniczny z zwolnieniem (`:134`), `countTechnicalFailure` (`:152`), zwolnienie przebiegu (`:164`), `releaseOrphans` (`:175`), `restore` (`:195`).
- Logika Javy w klasach wyłączonych z PIT (Faza 1), po jednym wierszu na zepsucie:
  - `RunRecorder.step` (`:250-273`): krok zamknięty jako `failed`, gdy `work` rzuca (usunięcie `endStep` w `catch`); wyjątek zamknięcia dołączony jako `suppressed`, a nie zastępujący (usunięcie `addSuppressed` albo rzucenie `endFailure`); brak `work` przy przebiegu, który już się skończył (usunięcie `orElseThrow`).
  - `PostTransitions.error` (`:207-210`): obcięcie do `MAX_ERROR_LENGTH` (granica `>` na `>=` i usunięcie obcięcia).
- `PostRepository.java:43,56,88`, `AssessmentRepository.java:64,81,110`, `PipelineRunRepository.java:59,93,113,133`.

Jedno zepsucie na strażnika: usunięcie albo odwrócenie warunku, który nadaje mu sens, na przykład `AND status = :running`, `assessment_attempts = 0` albo `< :maxAttempts`.

#### 2. Testy IT dla luk

**File**: istniejące klasy IT danego strażnika: `pipeline/state/RunRecorderIT.java`, `pipeline/state/PostColumnsIT.java`, `posts/PostRepositoryIT.java`, `posts/AssessmentQualityIT.java`, `pipeline/PipelineRunIT.java` i inne.

**Intent**: Dla każdej luki nowy przypadek w IT, który czerwienieje przy tym zepsuciu i jest zielony bez niego.

**Contract**:

- Dane przez istniejące pomocniki (`PostSeeds`, `OwnRuns`, `DbIsolationExtension`).
- Testy nie zależą od kolejności klas (S-30).
- Każdy nowy test jest dowiedziony ponownym nałożeniem zepsucia, a wynik trafia do tabeli.

### Success Criteria:

#### Automated Verification:

- `sql-breaks.md` ma wiersz dla każdego strażnika z listy w Changes Required (liczba wierszy = liczba pozycji), a każdy wiersz wskazuje test, który łapie zepsucie.
- Każdy nowy test IT był czerwony przy swoim zepsuciu i jest zielony bez niego. Wynik obu przebiegów, z `-Dit.test=<Klasa>`, jest zapisany w wierszu tabeli.
- `./mvnw -B verify` z `szczur-radar-api/` jest zielony.
- `git diff --stat origin/main -- szczur-radar-api/src/main` jest pusty.

---

## Phase 6: Job `mutation` w CI i dokumentacja

### Overview

Pomiar chodzi przy każdym PR i pushu na `main` i blokuje deploy jak każdy inny job testowy. Reguła trafia do wytycznych.

### Changes Required:

#### 1. Job `mutation`

**File**: `.github/workflows/ci.yml`

**Intent**: Regresja: nowy kod rdzenia bez testu, który zabija jego mutanty, czerwieni PR. Bez filtra ścieżek.

**Contract**:

- Job `mutation`: ten sam `runs-on`, `actions/checkout`, `actions/setup-java` (temurin 25, `cache: maven`) i `working-directory` co `backend`.
- Krok `./mvnw -B -Pmutation test`. Bez sekretów LLM i bez Dockera.
- `timeout-minutes: 15` z komentarzem o zmierzonym czasie.
- Krok `actions/upload-artifact` z `target/pit-reports/` przy niepowodzeniu (`if: failure()`), z krótką retencją.
- Job dopisany do `needs:` joba `railway-config`.

#### 2. Wytyczne i README

**File**: `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/CLAUDE.md`

**Intent**: Następny agent wie, że rdzeń jest mierzony i co zrobić z ocalałym.

**Contract**:

- `REPOSITORY_GUIDELINES.md`:
  - w „Deployment” (lista jobów CI) job `mutation`;
  - w „Coding Style & Testing” reguła: klasy rdzenia (lista w profilu `mutation`) mierzy PIT; ocalały → test na zachowaniu albo wpis w `AcceptedMutants` z powodem `równoważny:`, `obronny:` lub `tekst:`; zbędny wpis czerwieni; strażniki SQL rdzenia chronią IT (`sql-breaks.md` S-45); lokalnie `./mvnw -Pmutation test`, raport w `target/pit-reports/index.html`.
- `szczur-radar-api/README.md`: sekcja o pomiarze mutacyjnym.
- Oba `CLAUDE.md`: jedno zdanie, że pakiet jest w zakresie PIT.

### Success Criteria:

#### Automated Verification:

- Job `mutation` jest w `needs:` joba `railway-config` (`yq '.jobs."railway-config".needs' .github/workflows/ci.yml` zawiera `mutation`).
- Na PR S-45 job `mutation` jest zielony, a jego czas wynosi ≤ 10 min (`gh run view <id> --json jobs`, różnica `completedAt` − `startedAt` dla joba `mutation`).
- Cały workflow `ci.yml` na PR S-45 jest zielony (`gh pr checks`).
- Job `mutation` jest zielony także w drugim przebiegu na tym samym commicie (`gh run rerun <id> --job <job-id>`), więc strażnik nie czerwienieje losowo.
- `REPOSITORY_GUIDELINES.md` i `szczur-radar-api/README.md` wymieniają `-Pmutation` i `AcceptedMutants` (`grep`).

#### Manual Verification:

- Właściciel przegląda wpisy `AcceptedMutants` i tabelę `sql-breaks.md` i akceptuje powody przyjętych mutantów.

---

## Testing Strategy

### Unit Tests:

- `MutantRegistryTest` na fiksturach `mutations.xml`: każdy rodzaj rozbieżności (ocalały bez wpisu, `NO_COVERAGE`, zbędny wpis, krotność, inna linia, pusty powód, brak prefiksu kategorii).
- Testy zabijające ocalałe w istniejących klasach `*Test` rdzenia: asercje na zachowanie, przypadki graniczne nazwane w nazwie testu.

### Integration Tests:

- Nowe przypadki w istniejących `*IT` dla luk z `sql-breaks.md`, każdy dowiedziony zepsuciem.
- Pełne `./mvnw -B verify` po Fazach 4 i 5.

### Manual Testing Steps:

1. Przegląd powodów w `AcceptedMutants` (właściciel).
2. Przegląd tabeli `sql-breaks.md` (właściciel).

## Performance Considerations

- `ubuntu-latest` ma 4 vCPU, a `threads=4` to domyślna wartość profilu. Job `mutation` chodzi równolegle z `backend` (~4 min), więc wydłuża krytyczną ścieżkę CI tylko o nadwyżkę ponad `backend`.
- Granica to 10 min: Faza 1 sprawdza ją lokalnie, Faza 6 w CI. Przekroczenie oznacza stop i decyzję właściciela, nie ciche zawężenie zakresu.
- `ConcurrentAssessmentDispatcherTest` używa wątków. Mutant pętli albo czekania kończy się `TIMED_OUT`, co liczy się jako wykryty. Mutant, który raz przeżywa, a raz kończy się timeoutem, to niestabilność do zbadania, nie wpis rejestru.

## Migration Notes

Nie dotyczy: bez zmian schematu, danych i zmiennych Railway. Nowy job w `ci.yml` zaczyna blokować deploy od merge'a S-45, więc PR, który zmienia rdzeń i jest otwarty równolegle, po rebase musi spełnić strażnika.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-45, `:346-357`), issue #155.
- Zakres: `context/foundation/verification-scope.md:19,27`.
- Audyt: `context/changes/post-m3-verification-audit/research.md:65-72,253,302`.
- Destylacja: `context/domain/domain-distillation.md:186-228` (R-02, R-10, R-11, R-19, R-20).
- Wzorzec rejestru: `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractVariantsTest.java:34-50`.
- PIT: https://pitest.org/quickstart/maven/, https://pitest.org/quickstart/mutators/, https://github.com/pitest/pitest-junit5-plugin/issues/113.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: PIT w buildzie i pomiar bazowy

#### Automated

- [x] 1.1 `./mvnw -B test` zielony bez testów z tagiem `mutation` — 3f8a5ed
- [x] 1.2 `./mvnw -B -Pmutation test` tworzy `mutations.xml` z zabitym mutantem w `agents.assessor.validation` — 3f8a5ed
- [x] 1.3 Brak mutantów w klasach spoza zakresu — 3f8a5ed
- [x] 1.4 PIT nie uruchamia `*IT` ani testów `measurement` — 3f8a5ed
- [x] 1.5 `mutation-baseline.md` z tabelą per klasa i czasem ≤ 10 min (albo decyzja właściciela) — 3f8a5ed
- [x] 1.6 Dwa pomiary dają identyczne statusy mutantów — 3f8a5ed

### Phase 2: Rejestr i strażnik

#### Automated

- [x] 2.1 `./mvnw -B test` uruchamia `MutantRegistryTest`, nie `SurvivingMutantsTest` — cc15976
- [x] 2.2 Strażnik przy pustym rejestrze zgłasza tylu ocalałych, ile pomiar bazowy — cc15976
- [x] 2.3 Celowe zepsucie porównania czerwieni `MutantRegistryTest` — cc15976

### Phase 3: Ocalałe — walidator V2 i jakość ocen

#### Automated

- [x] 3.1 `./mvnw -B test` zielony — 53fa884
- [x] 3.2 Brak ocalałych bez wpisu w `agents.assessor.*` i `posts.AssessmentQuality*` — 53fa884
- [x] 3.3 Brak zmian w `src/main` — 53fa884
- [x] 3.4 Powody wpisów z prefiksem kategorii, pilnowane przez `MutantRegistryTest` z Fazy 2 — 53fa884

### Phase 4: Ocalałe — przebieg

#### Automated

- [x] 4.1 `./mvnw -B -Pmutation test` zielony — d6b1460
- [x] 4.2 `./mvnw -B verify` zielony — d6b1460
- [x] 4.3 Brak zmian w `src/main` — d6b1460
- [x] 4.4 Usunięty wpis rejestru czerwieni strażnika — d6b1460
- [x] 4.5 Tabela „po S-45” w `mutation-baseline.md` — d6b1460

### Phase 5: Ręczne zepsucia SQL

#### Automated

- [x] 5.1 `sql-breaks.md` ma wiersz z łapiącym testem dla każdego strażnika SQL
- [x] 5.2 Każdy nowy IT dowiedziony zepsuciem
- [x] 5.3 `./mvnw -B verify` zielony
- [x] 5.4 Brak zmian w `src/main`

### Phase 6: Job `mutation` w CI i dokumentacja

#### Automated

- [ ] 6.1 `mutation` w `needs:` joba `railway-config`
- [ ] 6.2 Job `mutation` zielony na PR i ≤ 10 min
- [ ] 6.3 Cały `ci.yml` zielony na PR
- [ ] 6.4 Wytyczne i README opisują `-Pmutation` i `AcceptedMutants`
- [ ] 6.5 Job `mutation` zielony także w drugim przebiegu

#### Manual

- [ ] 6.6 Właściciel akceptuje powody w `AcceptedMutants` i tabelę `sql-breaks.md`
