# Jakość ocen per źródło — tezy w innym języku niż post (S-36) — plan implementacji

## Overview

Reguła kontraktu „`statement` jest w języku posta” (`contract-v2.md` §8, Q-16) obowiązuje bez wyjątków, ale pilnuje jej tylko prompt i nikt jej nie mierzy na produkcji (R-38, D-14). S-36 domyka FR-033: operator widzi **osobno dla każdego źródła** udział tez w innym języku niż post, w tym samym oknie 7 dni i tym samym kanałem co S-35 — linia w podsumowaniu nocy, wiersz w sekcji `/admin` i alarm ntfy przy przejściu przez próg (Wykop 2 %, Reddit 30 %).

Przy okazji powstaje `posts.language`: język posta zapisany w bazie (stanowisko właściciela z 2026-10-29), ustawiany z języka źródła. Miara czyta język z posta, więc późniejsze wykrywanie języka zmieni tylko zapis kolumny.

## Current State Analysis

- **Tezy są w bazie.** Udana próba ma `assessments.output` (JSONB, niepuste dokładnie przy `status = 'ok'`, `V3__create_pipeline_and_posts.sql:139,165-167`), tezy pod `output->'claims'->i->>'statement'` (`agents/assessor/contract/Claim.java:14`, do 240 znaków). Źródło posta to `posts.source`.
- **Języka nie ma nigdzie w produkcie.** Brak pola w schemacie wyjścia, w `posts`, w `Source` i biblioteki w `pom.xml`. Kontrakt §2 mówi, że język „zna backend” (`contract-v2.md:75`), ale nic go nie zapisuje.
- **Jedyna reguła języka to kod pomiarowy S-28** (`src/test/.../measurement/RedditMeasures.java:51-60,115-151`): teza polska przy polskiej literze albo 2 polskich słowach funkcyjnych, każda inna angielska. `src/main` nie może importować `measurement/`.
- **Kanał S-35 jest gotowy.** `posts/AssessmentQuality` liczy okno `[D−6, D]` i przejście przez próg bez zapisanego stanu (`AssessmentQuality.java:60-87`); `pipeline/NightSummary` pisze linię `quality 7d` na źródło i treść alarmu (`NightSummary.java:361-419`); `GET /api/admin/assessment-quality` zasila sekcję „Jakość ocen (7 dni)” (`AssessmentQualityController.java:36-82`, `AdminView.vue:461-478`).
- **Kolejka alarmów odrzuca powtórkę.** To samo zdarzenie dla tego samego przebiegu w ciągu 10 min przepada (`alerts/OperatorAlertQueue.java:56,180-191`), więc drugi `ASSESSMENT_QUALITY` z tego samego żądania by zginął.
- **Dane z produkcji (2026-11-12, `evidence/README.md`):**
  - Wykop: 0,32 % tez w innym języku (40 z 12 327), okna 7 dni 0,09–0,86 %.
  - Reddit: 16,98 % (819 z 4 824), okna 14,01–20,76 %.
  - Nierozstrzygnięte tezy to 16–18 % na obu źródłach. Na Wykopie 29 z 30 sprawdzonych to krótkie polskie tezy bez polskich znaków, więc reguła S-28 dałaby tam 16,6 % fałszywych naruszeń.
  - Na Wykopie 43 posty z 9 331 mają tekst po angielsku. Język ze źródła liczy ich 28 poprawnych angielskich tez jako naruszenia i pomija 22 polskie; różnica w mierze to 0,04 pp.

## Desired End State

- Każdy post ma `posts.language` (`pl` albo `en`): nowy dostaje język swojego źródła przy zapisie, istniejące wypełnia migracja.
- Co noc podsumowanie nocy ma po liniach `quality 7d` linię języka dla każdego źródła z tezami w oknie, np. (liczby z produkcji, okno 2026-11-06…2026-11-12):

  ```
  wykop language 7d (2026-11-06…2026-11-12): other language 0.3% of 3476 theses; threshold 2%
  reddit language 7d (2026-11-06…2026-11-12): other language 16.1% of 2857 theses; threshold 30%
  ```

  ponad progiem z sufiksem `; ABOVE THRESHOLD`, a przy mniej niż 500 tezach `…: 46 theses, fewer than 500, threshold not checked`.
- W noc, gdy udział źródła przechodzi przez próg (okno dzisiejsze ponad progiem, a okno, które widziało poprzednie podsumowanie, nie albo miało mniej niż 500 tez), API wysyła **jedno** zdarzenie `ASSESSMENT_QUALITY` z każdą miarą, która tej nocy przeszła — nieudane próby i język tez razem.
- Sekcja „Jakość ocen (7 dni)” w `/admin` pokazuje przy każdym źródle drugi wiersz: udział tez w innym języku, liczbę tez, próg i stan.
- Awaria odczytu tez kosztuje tylko miarę języka: w nocy jedną linię `language 7d (…): unavailable (…)`, w `/admin` wiersz języka bez liczb. Miara nieudanych prób z S-35 działa wtedy bez zmian.
- Dokumentacja (PRD FR-033, kontrakt §8, runbook S-36, `CLAUDE.md` pakietów, `REPOSITORY_GUIDELINES.md`, roadmapa) opisuje miarę, progi i postępowanie po alarmie.

Weryfikacja: `./mvnw -B verify`, `npm test`, `npm run build`, `tools/pipeline-trigger/test.sh`, `tools/db-backup/test.sh`, CI PR-a zielone, podsumowanie i sekcja `/admin` sprawdzone w PR env przez agenta.

### Key Discoveries:

- Wzór kolumny z backfillem: `V13__posts_channel.sql` (`ADD COLUMN`, `UPDATE`, `SET NOT NULL`, `CHECK`) i `persistence/V13BackfillIT` (migracja do wersji sprzed zmiany, wstawienie postów, migracja do końca).
- `Post` powstaje w jednym miejscu produkcyjnym (`pipeline/step/PostStore.java:213`), a konstruktor zna `Source` (`posts/Post.java:135-150`) — język ustawi się w konstruktorze, bez nowego argumentu.
- Posty wstawia SQL-em przy najnowszym schemacie 6 miejsc w testach (`MigrationsIT.java:207,420`, `NightSummaryIT.java:215`, `AssessmentQualityIT.java:151`, `AssessmentQualityControllerIT.java:143`, `InternalPipelineRunIT.java:437`) i `tools/db-backup/test.sh:250`. `V13BackfillIT.java:60` wstawia przy schemacie V12, więc `language` tam nie istnieje.
- `MigrationsIT.java:38-39` przypina listę wersji (`containsExactly("1", … "15")`).
- Zapisywany enum: konwerter w `persistence/converters/` (wzór `SourceConverter`), wpis w rejestrze `persistence/ClosedSetsIT`, kolumna w `SET_ONCE` w `pipeline/state/PostColumnsIT.java:78-80`.
- Zapytanie natywne zwraca `List<Object[]>`, a metoda domyślna mapuje wiersze z `Source.fromValue` (`posts/PostRepository.java:78-91`); wartości enumów binduje się jako `.value()`. Rekord z enumami nie przyjmie wiersza zapytania natywnego wprost.
- `text/` to wspólny pakiet-liść (`Whitespace`, `Nul`), z którego korzystają `sources/` i `posts/`; w repo nie ma testu zależności pakietów.
- Reguła progu w liczbach całkowitych i zapis procentu: `AssessmentQuality.status` i `percent` (`AssessmentQuality.java:93-109`).
- Jakość czytana jest w osobnej transakcji tylko do odczytu; wyjątek daje linię `unavailable`, a podsumowanie i tak wychodzi (`NightSummary.java:174-187`). Trasa admina nie ma takiego `catch` (`AssessmentQualityController.java:39`).
- Stan z etykietą idzie przez `rules.json` `values.qualityStatus`, `labels.ts:73`, `SpaRulesTest` i `rules.test.ts`. Pliki kontraktu `assessment-quality.json` i `assessment-quality-few-attempts.json` edytuje się ręcznie; `AssessmentQualityControllerIT` tylko je porównuje (`ContractFiles.assertMatches`) i przy różnicy drukuje treść do wklejenia.
- Skrypt rysunku roadmapy nie niesie listy Parked (`tools/render_roadmap_svg.py:229-247`); rysunek zmienia tylko status karty S-36 (`:213`), a skrypt zna statusy `proposed`, `ready`, `progress`, `done`, `blocked`, `parked`.

## What We're NOT Doing

- **Wykrywanie języka z tekstu posta.** `posts.language` dostaje język źródła (decyzja właściciela 2026-11-12). Angielski wpis na Wykopie ma zapisane `pl`.
- **Pole `language` w wyjściu modelu**, zmiana promptu, schematu i `PROMPT_VERSION`.
- **Naprawa przyczyny** (prompt po angielsku albo dwujęzyczny) — osobna decyzja §16 po pomiarze; obecne 17 % na Reddicie jest tłem miary.
- **Język posta w API postów i w UI** (karta, strona posta, filtr) — kolumna jest zapisana, nie pokazana.
- **Biblioteka do wykrywania języka** i języki inne niż polski i angielski.
- **Osobne zdarzenie alarmu dla języka**, alarm co noc, dopóki ponad progiem, zapisany stan alarmu, nowy `AttentionReason`.
- **Próg jako zmienna środowiskowa** — stała przy `Source`, zmiana progu to PR.
- **Zmiana rekordów miary S-35** (`SourceQuality`, `QualityCrossing`, `QualityWindow`) — miara języka ma własne.
- **Zmiana reguły w `measurement/RedditMeasures`** — generator raportu S-28 zostaje przy swojej regule dwuwartościowej.
- **Historia udziałów w `/admin`** — sekcja pokazuje bieżące okno.
- **Kroki po merge'u** (pierwsza noc na produkcji, kontrola backfillu, zamknięcie issue) — runbook w `deploy-plan.md` i komentarze w issue, nie Progress.

## Implementation Approach

Język staje się wartością przy źródle i przy poście, a reguła rozpoznawania języka tekstu żyje w `text/`. Baza tylko wyciąga tezy udanych prób z okna razem z językiem ich posta; klasyfikuje je kod, więc reguła istnieje w jednym miejscu. `AssessmentQuality` dostaje drugą miarę obok pierwszej, z własnymi rekordami i własnym odczytem: to samo okno, ta sama reguła progu i to samo porównanie dwóch okien, ale osobna domena awarii. Podsumowanie nocy, alarm i trasa admina rozszerzają się o nią bez nowej trasy i nowego zdarzenia. Kolejność faz: język w modelu → miara → kanał nocny → widok → weryfikacja w PR env.

## Critical Implementation Details

- **Naruszeniem jest tylko dowód drugiego języka.** Teza liczy się jako „w innym języku”, gdy reguła znajdzie w niej język i jest to inny język niż `posts.language`. Teza nierozstrzygnięta (liczby, tickery, krótkie zdanie bez słów funkcyjnych) nie jest naruszeniem. Reguła dwuwartościowa dałaby na Wykopie 16,6 % fałszywych naruszeń (`evidence/README.md`).
- **Kolejność w regule.** Najpierw polski (polska litera albo co najmniej 2 polskie słowa funkcyjne), potem angielski (co najmniej 2 angielskie słowa funkcyjne), inaczej brak rozstrzygnięcia. Polska teza z angielskim cytatem zostaje polska. Listy słów nie zawierają słów wspólnych dla obu języków (`to, on, by, do, we, a, i, no, as, was, but`).
- **Osobna transakcja dla miary języka.** Błąd zapytania unieważnia całą transakcję Postgresa, więc odczyt tez w transakcji okna jakości zabrałby też zapytania o próby. `NightSummary` czyta miarę języka po oknie jakości, w osobnej transakcji tylko do odczytu, z własnym `catch`.
- **Jedno zdarzenie na noc.** Przejścia obu miar idą w treści jednego `ASSESSMENT_QUALITY`; drugie wywołanie `raise` z tym samym zdarzeniem i przebiegiem kolejka by odrzuciła.
- **Migracja `NOT NULL` bez wartości domyślnej.** Starszy kod nie ustawia `language`, więc post zapisany przez stare API między `migrate` a startem nowej wersji zostanie odrzucony (od S-38 widoczny jako `skipped`). Strażnik deployu i reguła „bez merge'a w trakcie przebiegu” już to zamykają; revert PR-a wymaga nowej migracji zdejmującej `NOT NULL`.

## Phase 1: Język przy źródle i przy poście

### Overview

Wartość języka, reguła rozpoznawania języka tekstu, język i próg przy źródle oraz kolumna `posts.language` z backfillem i strażnikami.

### Changes Required:

#### 1. Język i reguła dowodu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/text/Language.java`, `text/LanguageEvidence.java`, `src/test/.../text/LanguageEvidenceTest.java`

**Intent**: Jedno miejsce, które mówi, w jakim języku jest tekst, o ile da się to stwierdzić. Korzystają z niego `sources/` (język źródła) i `posts/` (miara).

**Contract**:
- `enum Language { PL("pl"), EN("en") }` z `value()` i `fromValue(String)` (wzór `Source`); zapisywany w bazie.
- `static Optional<Language> LanguageEvidence.of(@Nullable String text)`:
  - `PL`, gdy tekst ma literę z `[ąćęłńóśźż]` (bez względu na wielkość) albo co najmniej 2 wystąpienia słów z listy polskiej — tych samych 14 co w S-28: `że, się, nie, jest, oraz, ale, czy, będzie, które, jednak, dla, przez, jako, lub`;
  - w przeciwnym razie `EN`, gdy ma co najmniej 2 wystąpienia słów z listy angielskiej (34 słowa z `evidence/README.md`);
  - w przeciwnym razie puste; puste także dla `null` i tekstu bez liter.
- Słowa to ciągi liter Unicode (`\p{L}+`) po `toLowerCase(Locale.ROOT)`; każde wystąpienie się liczy; minimum 2 dla obu języków jako stała.

#### 2. Język i próg przy źródle

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/Source.java`, `sources/SourceTest.java`, `sources/CLAUDE.md`, `REPOSITORY_GUIDELINES.md` (Hard Rules, rejestracja źródła)

**Intent**: Każde źródło deklaruje język swoich postów i próg udziału tez w innym języku, więc nowe źródło nie skompiluje się bez nich.

**Contract**: `WYKOP(5, Language.PL, 2)`, `REDDIT(25, Language.EN, 30)`; `Language language()`, `int otherLanguageThresholdPercent()`. Javadoc: skąd wartości (`context/changes/statement-language-quality/evidence/README.md`: okna Wykopu 0,09–0,86 %, Reddita 14,01–20,76 %), że zmiana to PR. Opis rejestracji źródła w obu dokumentach wymienia język i oba progi.

#### 3. Kolumna `posts.language`

**File**: `szczur-radar-api/src/main/resources/db/migration/V16__posts_language.sql`, `posts/Post.java`, `persistence/converters/LanguageConverter.java`

**Intent**: Język posta jest zapisany przy poście; nowy post dostaje język swojego źródła, istniejące wypełnia migracja.

**Contract**:
- migracja wzorem V13: `ADD COLUMN language TEXT`, `UPDATE posts SET language = …` według `source` (`wykop` → `pl`, `reddit` → `en`), `SET NOT NULL`, `ck_posts_language CHECK (language IN ('pl', 'en'))`; komentarz mówi, że wartość to język źródła i że miara S-36 czyta ją z posta;
- `Post.language` (`nullable = false`), ustawiane w konstruktorze z `source.language()` — sygnatura konstruktora bez zmian; `getLanguage()`;
- konwerter wzorem `SourceConverter` (`@Converter(autoApply = true)`).
- Jeśli `main` dostanie wcześniej inną migrację `V16`, numer zmienia się przed merge'em.

#### 4. Strażnicy i dane testowe

**File**: `persistence/ClosedSetsIT.java`, `pipeline/state/PostColumnsIT.java`, `MigrationsIT.java`, nowe `persistence/V16BackfillIT.java`, testy wstawiające posty SQL-em przy najnowszym schemacie, `tools/db-backup/test.sh`

**Intent**: Nowa kolumna przechodzi przez istniejące strażniki, a backfill ma własny test.

**Contract**:
- wpis `StoredEnum.of(Language.class, "posts", "language", "Post", "language")` w rejestrze `ClosedSetsIT`; `language` w `SET_ONCE` w `PostColumnsIT`;
- `MigrationsIT.java:38-39`: lista wersji z `"16"`;
- `V16BackfillIT` wzorem `V13BackfillIT`: migracja do wersji 15, posty obu źródeł z `channel` i bez `language`, migracja do końca; posty dostają `pl` / `en`, kolumna jest `NOT NULL`, `CHECK` odrzuca inną wartość;
- `INSERT INTO posts` dostają `language` w: `MigrationsIT` (dwa miejsca), `NightSummaryIT`, `AssessmentQualityIT`, `AssessmentQualityControllerIT`, `InternalPipelineRunIT` i `tools/db-backup/test.sh:250`. Pomocnicy sparametryzowani źródłem wyprowadzają język ze źródła;
- `V13BackfillIT` zostaje bez zmian (wstawia przy schemacie V12; jego posty wypełni V16).

#### 5. Karta S-36 na rysunku roadmapy

**File**: `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Gdy `/10x-implement` ustawia w roadmapie status S-36 na `in-progress`, rysunek roadmapy pokazuje to samo.

**Contract**: status S-36 w danych skryptu `progress` (wszędzie, gdzie skrypt go trzyma), `python3 tools/render_roadmap_svg.py` regeneruje SVG.

### Success Criteria:

#### Automated Verification:

- `LanguageEvidenceTest` zielony: polska litera, 2 polskie słowa, 1 polskie słowo (puste), 2 angielskie słowa, 1 angielskie słowo (puste), same liczby i tickery (puste), polska litera obok angielskich słów (`PL`), słowa wspólne dla obu języków nie liczą się, wielkość liter, `null`
- `SourceTest` przypina język i próg języka obu źródeł (`WYKOP` `pl` 2, `REDDIT` `en` 30)
- `V16BackfillIT` zielony: posty obu źródeł sprzed migracji dostają język źródła, `CHECK` odrzuca inną wartość
- `./mvnw -B verify` zielone (`JpaSchemaIT`, `MigrationsIT`, `ClosedSetsIT`, `PostColumnsIT`)
- `tools/db-backup/test.sh` zielony
- `python3 tools/render_roadmap_svg.py` kończy się kodem 0, a `git diff --stat docs/visuals/roadmap.svg` pokazuje zmianę karty S-36

---

## Phase 2: Miara języka tez

### Overview

Zapytanie o tezy z okna, udział tez w innym języku i jego stan jako własne okno miary w `AssessmentQuality`, przejście przez próg tej miary oraz jedna nazwa stanu „za mało danych” dla obu miar.

### Changes Required:

#### 1. Stan wspólny dla obu miar

**File**: `posts/QualityStatus.java` i jego użycia w API (`AssessmentQuality`, `NightSummary`, testy); `szczur-radar-web/src/contract/rules.json`, `src/labels.ts`, `src/api/quality.ts`, `src/contract/assessment-quality-few-attempts.json`, `src/contract/samples.ts`, `dev/mock-api.js`, `src/views/AdminView.vue`, `src/contract/rules.test.ts`, `src/contract/mockApi.test.ts`, `src/views/AdminView.test.ts`

**Intent**: Stan „poniżej minimum” przestaje mówić o próbach, bo dla miary języka brakuje tez. Jedna wartość obsługuje obie miary.

**Contract**: `NOT_ENOUGH_ATTEMPTS` → `NOT_ENOUGH_DATA`, wartość JSON `not_enough_data`, etykieta „za mało danych”; kolejność wartości bez zmian. Zmiana tylko nazwy, bez zmiany zachowania; `SpaRulesTest` nie wymaga zmiany w Javie (porównuje `rules.json` z wartościami enuma). Obie strony zmieniają się w tym samym commicie.

#### 2. Zapytanie o tezy

**File**: `posts/AssessmentRepository.java` (+ rekord wiersza w `posts/`)

**Intent**: Jedno zapytanie zwraca dla okna `[from, until)` każdą tezę udanej próby z wywołaniem modelu razem ze źródłem i językiem jej posta. Klasyfikacja zostaje w Javie.

**Contract**:
- metoda domyślna `List<PostStatement> findModelStatements(Instant from, Instant until)`; rekord `PostStatement(Source source, Language postLanguage, String statement)`;
- pod nią zapytanie natywne zwracające `List<Object[]>` (wzór `PostRepository.java:78-91`): `assessments` z `posts`, tezy z `jsonb_array_elements` po `a.output -> 'claims'`, filtr `status`, `result_source` i `assessed_at >= :from AND assessed_at < :until` (indeks `ix_assessments_assessed_at`); wiersze mapuje metoda domyślna przez `Source.fromValue` i `Language.fromValue`;
- `status` i `result_source` bindowane jako `.value()` enumów (`AssessmentResult.Status.OK`, `Provenance.Source.MODEL`), nie literałami;
- próba bez tez nie daje wiersza; próba, której `claims` nie jest tablicą, też nie (warunek na `jsonb_typeof`), zamiast wywracać zapytanie.

#### 3. Okno miary języka i przejście

**File**: `posts/AssessmentQuality.java`, nowe `posts/LanguageWindow.java`, `posts/SourceLanguage.java`, `posts/LanguageCrossing.java`

**Intent**: Miara języka ma własne okno i własne przejście przez próg, liczone tą samą regułą i tym samym porównaniem dwóch okien co miara prób. Rekordy miary prób zostają bez zmian, więc awaria jednego odczytu nie dotyka drugiego.

**Contract**:
- stała `MIN_THESES = 500`;
- `LanguageWindow(LocalDate from, LocalDate to, List<SourceLanguage> sources)` — źródła z co najmniej jedną tezą w oknie, w kolejności `Source`;
- `SourceLanguage(Source source, long theses, long otherLanguage, int thresholdPercent, QualityStatus status)`;
- `LanguageCrossing(SourceLanguage tonight, @Nullable SourceLanguage before)`;
- `LanguageWindow languageWindow(LocalDate to, ZoneId zone)` — to samo okno `[to − 6, to]` co `window`;
- `List<LanguageCrossing> languageCrossed(LanguageWindow tonight, Instant now, ZoneId zone)` — okno poprzedniej nocy jak w `crossed`: `[początek D−7, min(początek D, now − 24 h))`;
- teza jest „w innym języku”, gdy `LanguageEvidence.of(statement)` zwraca język różny od `postLanguage`;
- próg z `source.otherLanguageThresholdPercent()`; reguła progu wspólna z miarą prób, uogólniona na minimum: poniżej minimum `NOT_ENOUGH_DATA`, inaczej `otherLanguage × 100 > threshold × theses` → `ABOVE_THRESHOLD` (ostro większy);
- `window` i `crossed` oraz rekordy `QualityWindow`, `SourceQuality`, `QualityCrossing` bez zmian.

#### 4. Dane testowe z tezami

**File**: `posts/AssessmentQualityIT.java`

**Intent**: Dotychczasowe testy sieją udane próby z `output = '{}'`, czyli bez tez. Testy miary języka potrzebują prób z tezami i postów z wybranym językiem.

**Contract**: pomocnik siejący próbę z listą tez w `output.claims` oraz post z podanym `language` (domyślnie język źródła).

### Success Criteria:

#### Automated Verification:

- `AssessmentQualityTest` zielony: brzegi 499 i 500 tez, udział równy progowi (`ok`) i o jedną tezę wyżej (`above_threshold`), teza nierozstrzygnięta nie jest naruszeniem, przejście miary języka (wczoraj `ok` → dziś ponad; wczoraj za mało tez → dziś ponad; ponad dwie noce z rzędu bez przejścia)
- `AssessmentQualityIT` zielony (Postgres): tezy tylko z udanych prób z modelem (prefiltr i nieudane pominięte), próba bez tez i próba z `claims` niebędącym tablicą bez wiersza, granice okna, post Wykopu z `language = 'en'` — jego polska teza liczy się jako inny język (miara czyta kolumnę, nie źródło)
- `./mvnw -B verify` zielone (`SpaRulesTest`, `ContractVariantsTest` po zmianie nazwy stanu)
- `npm test` i `npm run build` zielone po zmianie nazwy stanu

---

## Phase 3: Podsumowanie nocy i alarm

### Overview

Linia języka w podsumowaniu nocy z własnym odczytem, jedno zdarzenie z obiema miarami, przykłady crona i dokumentacja.

### Changes Required:

#### 1. Linia języka

**File**: `pipeline/NightSummary.java`, `pipeline/NightSummaryTest.java`, `pipeline/NightSummaryIT.java`

**Intent**: Po liniach `quality 7d` podsumowanie ma linię języka dla każdego źródła z okna miary języka, przed „Dead in admin runs…”. Awaria odczytu tez kosztuje tylko te linie.

**Contract**:
- odczyt miary języka po oknie jakości, w osobnej transakcji tylko do odczytu, z własnym `catch` (jak `quality()`): wyjątek daje ERROR w logu, puste okno i klasę wyjątku;
- `Night` dostaje `LanguageWindow language`, `List<LanguageCrossing> languageCrossed`, `@Nullable String languageUnavailable` (z tym samym niezmiennikiem co `qualityUnavailable`);
- linia: `<source> language 7d (<from>…<to>): other language X.X% of N theses; threshold T%`, z `; ABOVE THRESHOLD` przy `above_threshold`; przy `not_enough_data` `…: N theses, fewer than 500, threshold not checked`;
- przy awarii jedna linia `language 7d (<from>…<to>): unavailable (<klasa wyjątku>)` w miejscu linii języka; linie `quality 7d` bez zmian;
- kolejność: wszystkie linie `quality`, potem wszystkie linie `language`, obie w kolejności `Source`;
- Javadoc klasy z nowym przykładem; test limitu 4096 bajtów zielony z liniami języka.

#### 2. Jedno zdarzenie z obiema miarami

**File**: `pipeline/NightSummary.java` (`qualityAlert`), `alerts/AlertEvent.java`, `pipeline/api/InternalPipelineRunIT.java`, `alerts/NtfyAlertsTest.java`

**Intent**: Treść `ASSESSMENT_QUALITY` wymienia każde przejście tej nocy, a opis zdarzenia mówi o obu miarach.

**Contract**:
- `qualityAlert()` jest niepuste, gdy przeszła którakolwiek miara: najpierw części nieudanych prób (bez zmian), potem części języka, każda grupa w kolejności `Source`; części rozdzielone `; `, na końcu `; /admin`;
- część przejścia języka: `<source>: other language X.X% of N theses in 7 days (<from>…<to>), above its threshold T% (the day before: Y.Y% of M theses | M theses, fewer than 500)`;
- opis `ASSESSMENT_QUALITY` obejmuje obie miary (udział nieudanych prób albo udział tez w innym języku niż post) i mówi, co sprawdzić w `/admin`; priorytet 4 bez zmian;
- kontroler dalej woła `raise` raz; odpowiedź trasy bez zmiany kształtu.

#### 3. Cron

**File**: `tools/pipeline-trigger/test.sh`, `tools/pipeline-trigger/README.md`

**Intent**: Atrapa API i przykład logu pokazują nowe linie; `trigger.sh` się nie zmienia (loguje każdą linię).

**Contract**: fixture `SUMMARY` i `log_has_the_summary` w `test.sh` z liniami `language 7d`; przykładowy log w README.

#### 4. Dokumentacja

**File**: `pipeline/CLAUDE.md`, `sources/CLAUDE.md`, `REPOSITORY_GUIDELINES.md`, `context/deployment/deploy-plan.md`, `context/foundation/prd.md`, `context/foundation/llm-assessment/contract-v2.md`, `context/foundation/roadmap.md`

**Intent**: Miara, progi, reguła języka i postępowanie po alarmie są opisane tam, gdzie operator i agent ich szukają.

**Contract**:
- PRD: doprecyzowanie pod FR-033 z datą (miara: tezy udanych prób z modelem; naruszeniem jest dowód drugiego języka; język posta z `posts.language` = język źródła; progi Wykop 2 %, Reddit 30 %; minimum 500 tez; okno i reguła przejścia jak w S-35; dane w `evidence/`; znane ograniczenie: angielskie wpisy na Wykopie). Doprecyzowanie pod wpisem z `prd.md:248`: język posta jest zapisany od S-36, nadal niepokazywany.
- kontrakt §8: doprecyzowanie z datą, jak reguła jest mierzona (odsyłacz do FR-033 i `evidence/`), bez zmiany reguły.
- `deploy-plan.md`: nowa sekcja `## S-36: …` ze statusem „**Status:** delivered by PR #N; production verification and closure in issue #M.”, opisem linii i alarmu, postępowaniem po alarmie o języku (zmiana modelu u dostawcy, zmiana promptu, nowy kanał w innym języku; tezy widać na stronie posta), znaczeniem linii `unavailable`, zmianą progu (PR w `Source`), kontrolą backfillu po merge'u (`SELECT language, count(*) FROM posts GROUP BY 1` tylko do odczytu), czasem odpowiedzi trasy admina na produkcji i pierwszą nocą na produkcji.
- roadmapa: wpis Parked „Język posta w bazie” zamknięty przez S-36 (kolumna jest, wykrywanie z tekstu zostaje jako możliwy następny krok). Rysunek roadmapy nie niesie listy Parked, więc ta edycja go nie zmienia; skrypt i tak uruchamia się po zmianie `roadmap.md`.
- `pipeline/CLAUDE.md` i `REPOSITORY_GUIDELINES.md`: `NightSummary`, `alerts`, `posts`, „Daily run” i „Out-of-app alert channel” wymieniają linię języka, jej osobną awarię i obie miary w `ASSESSMENT_QUALITY`.

### Success Criteria:

#### Automated Verification:

- `NightSummaryTest` zielony: linia języka w stanach `ok`, `ABOVE THRESHOLD` i `fewer than 500`, linia `language 7d … unavailable` obok zwykłych linii `quality 7d`, kolejność linii, treść alarmu z samym przejściem języka i z obiema miarami, limit 4096 bajtów
- `NightSummaryIT` zielony: linie języka z zasianych tez, jedno źródło przechodzi próg języka, drugie jest ponad nim dwie noce z rzędu
- `InternalPipelineRunIT` zielony: `ASSESSMENT_QUALITY` wysłane raz przy przejściu samej miary języka i raz przy przejściu obu, niewysłane bez przejścia; przy wyjątku z zapytania o tezy (szpieg `AssessmentRepository`, wzór `PostStoreIT`) podsumowanie ma linie `quality 7d`, linię `language 7d … unavailable` i alarm o nieudanych próbach
- `NtfyAlertsTest` zielony: `ASSESSMENT_QUALITY` z `Priority: 4`
- `tools/pipeline-trigger/test.sh` zielony
- `./mvnw -B verify` zielone
- `python3 tools/render_roadmap_svg.py` kończy się kodem 0 po zamknięciu wpisu Parked

---

## Phase 4: Sekcja w `/admin`

### Overview

Pola języka w odpowiedzi trasy admina, kontrakt SPA i wiersz języka w sekcji „Jakość ocen (7 dni)”.

### Changes Required:

#### 1. Trasa

**File**: `posts/api/AssessmentQualityController.java`, `posts/api/AssessmentQualityControllerIT.java`, `contract/ContractRegistry.java`

**Intent**: `GET /api/admin/assessment-quality` zwraca przy każdym źródle miarę języka i minimum tez. Awaria odczytu tez nie zabiera miary prób.

**Contract**:
- `QualityView` dostaje `minTheses` (500); `SourceQualityView` dostaje `@Nullable LanguageView language`;
- `LanguageView(long theses, long otherLanguage, int thresholdPercent, QualityStatus status)` — nazwa unikalna wśród rekordów kontraktu; liczby, nie procenty;
- `language` jest `null` tylko wtedy, gdy odczyt tez rzucił wyjątek (kontroler łapie `RuntimeException`, ERROR w logu, miara prób wraca normalnie); źródło z próbami i bez tez dostaje `LanguageView(0, 0, próg, NOT_ENOUGH_DATA)`;
- wyjątek z odczytu prób daje 500 jak dziś.
- Pliki kontraktu edytuje się ręcznie, IT je porównuje. Trzy pliki: `assessment-quality.json` (jedno źródło `ok` przy udziale równym progowi, jedno `above_threshold`), `assessment-quality-few-attempts.json` (źródło `not_enough_data`) i nowy `assessment-quality-language-unavailable.json` (`language: null`, z IT ze szpiegiem `AssessmentRepository`). Nowy plik ma wpis w `ContractRegistry.SHAPES`; `@Nullable` ma oba warianty w plikach, więc bez wpisu w `KNOWN_GAPS`.

#### 2. Kontrakt SPA

**File**: `szczur-radar-web/src/contract/assessment-quality.json`, `src/contract/assessment-quality-few-attempts.json`, `src/contract/assessment-quality-language-unavailable.json`, `src/contract/samples.ts`, `src/api/quality.ts`

**Intent**: Typ odpowiedzi bez pól opcjonalnych, próbki zgodne z plikami.

**Contract**: `LanguageShare { theses: number; otherLanguage: number; thresholdPercent: number; status: QualityStatus }`; `SourceQuality.language: LanguageShare | null`; `QualityWindow.minTheses`; próbka i wpis `SAMPLES` dla trzeciego pliku; `shapes.test.ts`, `responseTypes.test.ts`, `ContractRoutesIT`, `ContractVariantsTest` zielone.

#### 3. Wiersz w sekcji

**File**: `szczur-radar-web/src/views/AdminView.vue`, `src/views/AdminView.test.ts`, `dev/mock-api.js`, `src/contract/mockApi.test.ts`

**Intent**: Przy każdym źródle sekcja pokazuje dwie miary, każdą z własnym stanem: nieudane próby (jak dziś) i tezy w innym języku niż post.

**Contract**:
- wiersz języka: „tezy w innym języku niż post X,X% z N tez · próg T%” ze `StatusBadge` w tych samych tonach co pierwsza miara; przy `not_enough_data` liczba tez bez udziału; przy `language === null` tekst „tezy w innym języku niż post: nie udało się policzyć”, bez `StatusBadge`;
- opis okna wymienia oba minima (100 prób, 500 tez);
- to wiersz danych w istniejącej sekcji: tylko istniejące komponenty i tokeny (`styleContract.test.ts`), bez zmiany stylu i bez nowego wspólnego komponentu, więc bez ścieżki `/10x-ui`;
- atrapa podaje `language` w każdym trybie `QUALITY` i ma nowy tryb `nolang` (`language: null`); `mockApi.test.ts` ma trasę dla trzeciego pliku kontraktu i sprawdza, że stan języka wynika z liczb i `minTheses` tak jak w API.

### Success Criteria:

#### Automated Verification:

- `AssessmentQualityControllerIT` zielony: treść dla stałego `Clock` równa trzem plikom kontraktu, z miarą języka w trzech stanach i z `language: null` przy wyjątku z zapytania o tezy
- `./mvnw -B verify` zielone (`ContractRoutesIT`, `ContractVariantsTest`, `SpaRulesTest`)
- `npm test` zielone (`AdminView.test.ts`: wiersz języka w trzech stanach i przy `null`; `shapes.test.ts`, `responseTypes.test.ts`, `mockApi.test.ts`, `styleContract.test.ts`)
- `npm run build` zielone
- Zrzuty `playwright-cli` sekcji na `dev/mock-api.js`: oba motywy, 375 px, 7 stanów (default, hover, focus-visible, disabled, error, empty, loading) albo N/A z powodem, zapisane w folderze zmiany

---

## Phase 5: Weryfikacja w PR env

### Overview

CI PR-a, trasa, sekcja `/admin` i podsumowanie nocy w Railway PR env sprawdzone przez agenta; ocena formatu i słów przez właściciela.

### Changes Required:

#### 1. Kontrola w PR env (agent)

**File**: brak zmian kodu; wyniki w komentarzu issue S-36

**Intent**: Pokazać na żywym środowisku, że migracja przeszła, trasa i sekcja działają, a cron loguje i wysyła linie języka.

**Contract**: procedura z S-35: jednorazowe konto admina w PR env (lekcja „W PR env agent sam zakłada jednorazowe konto admina…”), `curl` trasy z nagłówkiem `Origin`, `playwright-cli` sekcji z ciasteczkiem, noc w PR env procedurą S-34 (losowy testowy temat ntfy jako `NTFY_URL`, `TRIGGER_IN_ANY_ENVIRONMENT=true`, tymczasowy harmonogram crona), potem usunięcie tych zmiennych. `ASSESSMENT_QUALITY` w PR env się nie pojawi (jedna noc, brak okna porównania ponad progiem) — dowodzą go testy z fazy 3.

### Success Criteria:

#### Automated Verification:

- CI PR-a zielone: `gh pr checks <PR> --watch` kończy się bez czerwonego joba
- `GET /api/admin/assessment-quality` w PR env zwraca 200 z `minTheses` i z `language` przy każdym źródle (`curl … | jq -e '.minTheses == 500 and (.sources | all(.language != null))'`), a bez sesji 401
- Log crona w PR env (`railway logs -s pipeline-trigger -e szczur-radar-pr-<N>`) ma linie `summary: … language 7d …` po liniach `quality 7d`, a testowy temat ntfy wiadomość `Szczur Radar NIGHT_SUMMARY` z tymi liniami
- Zrzut sekcji „Jakość ocen (7 dni)” w PR env przez `playwright-cli` z ciasteczkiem jednorazowego admina, zapisany w folderze zmiany; zmienne testowe (`NTFY_URL`, `TRIGGER_IN_ANY_ENVIRONMENT`, harmonogram) usunięte z PR env

#### Manual Verification:

- Właściciel akceptuje format linii `language 7d` i treść `ASSESSMENT_QUALITY` z miarą języka (wiadomość z PR env i przykład z testu)
- Właściciel akceptuje wygląd i słowa wiersza języka w sekcji „Jakość ocen (7 dni)”, w tym etykietę „za mało danych” w miejsce „za mało prób”

---

## Testing Strategy

### Unit Tests:

- `LanguageEvidenceTest`: oba języki, brak rozstrzygnięcia, kolejność reguł, słowa wspólne dla obu języków.
- `AssessmentQualityTest`: stan miary języka na brzegach (499/500 tez, udział równy progowi i o jedną tezę wyżej), przejścia miary języka.
- `NightSummaryTest`: linie języka w trzech stanach, linia `unavailable` samej miary języka, treść alarmu, limit 4096 bajtów.
- `SourceTest`: język i oba progi.
- Vitest: `AdminView.test.ts`, testy kontraktu SPA i atrapy.

### Integration Tests:

- `V16BackfillIT`: backfill i `CHECK`.
- `AssessmentQualityIT`: zapytanie o tezy na Postgresie, miara czyta `posts.language`, `claims` niebędące tablicą pominięte.
- `AssessmentQualityControllerIT`: trasa z trzema plikami kontraktu, w tym `language: null` przy awarii odczytu tez.
- `NightSummaryIT`, `InternalPipelineRunIT`: linie, jedno zdarzenie przy przejściu, awaria odczytu tez obok działającej miary prób.
- `tools/pipeline-trigger/test.sh`, `tools/db-backup/test.sh`.

### Manual Testing Steps:

1. Właściciel czyta wiadomość `NIGHT_SUMMARY` z PR env i przykład `ASSESSMENT_QUALITY` z testu, akceptuje format.
2. Właściciel ogląda zrzuty sekcji `/admin` (fazy 4 i 5), akceptuje słowa i wygląd.

## Performance Considerations

Zapytanie o tezy zwraca na okno ok. 7 tys. krótkich tekstów (produkcja: do 3 916 tez Wykopu i 3 182 Reddita) i odczytuje `output` ok. 3,5 tys. prób przez indeks `ix_assessments_assessed_at`. Wykonuje się dwa razy na noc i raz przy każdym odczycie sekcji w `/admin`, gdzie odpowiedź czeka na oba odczyty. Klasyfikacja w pamięci to jedno przejście po tekście do 240 znaków. Koszt rośnie z liczbą tez w 7 dniach, nie z historią. Czas odpowiedzi trasy na produkcji sprawdza runbook po merge'u.

## Migration Notes

V16 dodaje kolumnę, wypełnia ją jednym `UPDATE` według `source` i zakłada `NOT NULL` z `CHECK` (wzór V13; tabela ma dziś kilkadziesiąt tysięcy wierszy, przepisanie trwa sekundy w pre-deploy `migrate`). Merge poza przebiegiem pipeline'u (istniejąca reguła). Wycofanie: revert PR-a zostawia kolumnę `NOT NULL`, której stary kod nie ustawia, więc revert musi nieść migrację `V17` zdejmującą `NOT NULL` (fix-forward). Progi zmienia się PR-em w `Source`.

## References

- Badanie: `context/changes/statement-language-quality/research.md`
- Dane z produkcji: `context/changes/statement-language-quality/evidence/README.md` i dwa pliki CSV
- Przegląd planu: `context/changes/statement-language-quality/reviews/plan-review.md`
- Roadmapa: `context/foundation/roadmap.md` — S-36 (`:236-248`), Parked „Język posta w bazie” (`:422`)
- PRD: FR-033 (`context/foundation/prd.md:268-270`), wpis o języku posta (`:248`)
- Kontrakt: `context/foundation/llm-assessment/contract-v2.md` §8 (`:259-264`), §2 (`:75`)
- Wzór S-35: `context/archive/2026-11-11-per-source-failure-quality/plan.md`, runbook `context/deployment/deploy-plan.md` (`:2373-2418`)
- Pomiar S-28: `context/archive/2026-10-30-reddit-quality-and-cost/report-2026-10-31.md` (`:60-66`, `:106-108`, `:132`)
- Kod: `posts/AssessmentQuality.java:60-156`; `posts/AssessmentRepository.java:71-78`; `posts/PostRepository.java:78-91`; `pipeline/NightSummary.java:174-187`, `:349-419`; `alerts/AlertEvent.java:28-29`; `alerts/OperatorAlertQueue.java:180-191`; `sources/Source.java:15-37`; `posts/Post.java:135-150`; `db/migration/V13__posts_channel.sql`; `measurement/RedditMeasures.java:51-60`; `szczur-radar-web/src/views/AdminView.vue:126-178`, `:461-478`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Język przy źródle i przy poście

#### Automated

- [x] 1.1 `LanguageEvidenceTest` zielony: oba języki, brak rozstrzygnięcia, kolejność reguł, słowa wspólne
- [x] 1.2 `SourceTest` przypina język i próg języka obu źródeł
- [x] 1.3 `V16BackfillIT` zielony: backfill językiem źródła, `CHECK` odrzuca inną wartość
- [x] 1.4 `./mvnw -B verify` zielone (`JpaSchemaIT`, `MigrationsIT`, `ClosedSetsIT`, `PostColumnsIT`)
- [x] 1.5 `tools/db-backup/test.sh` zielony
- [x] 1.6 `python3 tools/render_roadmap_svg.py` kończy się kodem 0 i zmienia kartę S-36 w `docs/visuals/roadmap.svg`

### Phase 2: Miara języka tez

#### Automated

- [ ] 2.1 `AssessmentQualityTest` zielony: brzegi 499/500 tez i progu, nierozstrzygnięta teza, przejścia miary języka
- [ ] 2.2 `AssessmentQualityIT` zielony: tezy tylko z udanych prób z modelem, `claims` niebędące tablicą pominięte, granice okna, miara czyta `posts.language`
- [ ] 2.3 `./mvnw -B verify` zielone (`SpaRulesTest`, `ContractVariantsTest` po zmianie nazwy stanu)
- [ ] 2.4 `npm test` i `npm run build` zielone po zmianie nazwy stanu

### Phase 3: Podsumowanie nocy i alarm

#### Automated

- [ ] 3.1 `NightSummaryTest` zielony: linia języka w trzech stanach, linia `unavailable` samej miary języka, kolejność linii, treść alarmu, limit 4096 bajtów
- [ ] 3.2 `NightSummaryIT` zielony: linie języka i przejście progu języka z zasianych tez
- [ ] 3.3 `InternalPipelineRunIT` zielony: `ASSESSMENT_QUALITY` raz przy przejściu języka i przy obu miarach, brak bez przejścia, awaria zapytania o tezy obok działającej miary prób
- [ ] 3.4 `NtfyAlertsTest` zielony: `ASSESSMENT_QUALITY` z `Priority: 4`
- [ ] 3.5 `tools/pipeline-trigger/test.sh` zielony
- [ ] 3.6 `./mvnw -B verify` zielone
- [ ] 3.7 `python3 tools/render_roadmap_svg.py` kończy się kodem 0 po zamknięciu wpisu Parked

### Phase 4: Sekcja w `/admin`

#### Automated

- [ ] 4.1 `AssessmentQualityControllerIT` zielony: treść równa trzem plikom kontraktu, miara języka w trzech stanach i `language: null` przy awarii odczytu tez
- [ ] 4.2 `./mvnw -B verify` zielone (`ContractRoutesIT`, `ContractVariantsTest`, `SpaRulesTest`)
- [ ] 4.3 `npm test` zielone (`AdminView.test.ts`, testy kontraktu SPA i atrapy, `styleContract.test.ts`)
- [ ] 4.4 `npm run build` zielone
- [ ] 4.5 Zrzuty `playwright-cli` sekcji na mocku: oba motywy, 375 px, 7 stanów lub N/A z powodem

### Phase 5: Weryfikacja w PR env

#### Automated

- [ ] 5.1 CI PR-a zielone (`gh pr checks <PR> --watch`)
- [ ] 5.2 `GET /api/admin/assessment-quality` w PR env: 200 z `minTheses` i `language` przy każdym źródle, 401 bez sesji
- [ ] 5.3 Log crona i testowy temat ntfy w PR env z liniami `language 7d`
- [ ] 5.4 Zrzut sekcji w PR env przez `playwright-cli`, zmienne testowe usunięte z PR env

#### Manual

- [ ] 5.5 Właściciel akceptuje format linii `language 7d` i treść `ASSESSMENT_QUALITY` z miarą języka
- [ ] 5.6 Właściciel akceptuje wygląd i słowa wiersza języka, w tym etykietę „za mało danych”
