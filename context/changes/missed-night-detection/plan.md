# S-13: dni bez danych w /admin — plan implementacji

## Overview

Admin widzi w `/admin` dni z ostatnich 14 dni (najpóźniej D-2), których nie objął żaden udany przebieg zakresu, osobno dla każdego skonfigurowanego źródła. Jednym kliknięciem wpisuje taki zakres do formularza „Nowy przebieg” i uruchamia go sam.

Alarm o złej nocy już daje F-04 (healthchecks.io → Telegram, `research.md` §1). S-13 dokłada tylko to, czego F-04 nie mówi: który dzień został bez danych i jak go uzupełnić. Bez automatycznego uzupełniania i bez nowego alarmu (decyzja po badaniu, wariant B).

## Current State Analysis

- **Nocny przebieg i dziury.** Nocny przebieg pobiera D-2…D-1 według kalendarza warszawskiego (`pipeline/api/InternalPipelineRunController.java:65-71`). Dzień X pobierają więc noce X+1 i X+2. Dziura (dzień bez udanego przebiegu) powstaje dopiero po dwóch złych nocach z rzędu. Nic jej dziś nie pokazuje ani nie uzupełnia (audyt C3, `research.md` §2).
- **Dane do policzenia pokrycia już są.** `pipeline_runs` ma `kind`, `status`, `range_from`/`range_to` (`TIMESTAMPTZ`, obie granice włącznie, od 00:00:00 do 23:59:59 w Warszawie, `PipelineRunService.java:147-153`) i `sources TEXT[]` z wartościami `Source.value()` (`run/PipelineRun.java:50-53,131,196`).
- **Znaczenie `succeeded`/`partial`.** Przebieg RANGE kończy się tak tylko wtedy, gdy każde źródło z `sources` pobrało się bez błędu i dało co najmniej jeden post w zakresie (`step/SourceIngest.java:69-102`).
- **Ręczny zakres.** `POST /api/admin/pipeline-runs` przyjmuje `{from, to, sources}`: `from ≤ to ≤ dziś`, najwyżej 7 dni (`MAX_RANGE_DAYS`, `PipelineRunController.java:54,140-147`). Formularz w `szczur-radar-web/src/views/AdminView.vue:247-285` ma `from`, `to` i `chosenSources` jako `ref`, więc da się je wypełnić z zewnątrz formularza.
- **Etykieta świeżości** pokazuje koniec ostatniego udanego przebiegu z najdalszym zakresem (`run/PipelineRunRepository.java:44-53`) i zostaje bez zmian.

## Desired End State

- Trasa `GET /api/admin/pipeline-runs/missing-days` (tylko admin) zwraca sprawdzane okno i listę dni bez danych z brakującymi źródłami.
- W `/admin`, pod formularzem „Nowy przebieg”, jest sekcja „Dni bez danych”:
  - zakresy kolejnych dni, każdy z przyciskiem „Wpisz do formularza”, który ustawia „Od”, „Do” i źródła;
  - albo komunikat, że w oknie nie ma dni bez danych;
  - stany ładowania i błędu jak w pozostałych sekcjach.
- Po przebiegu, który pokrył dziurę, lista po odświeżeniu jej nie pokazuje.

Przykład. Dziś (Warszawa) jest 2026-10-24, więc okno to 2026-10-09…2026-10-22. Dla Wykopu udane przebiegi pokryły 2026-10-09…2026-10-18 i 2026-10-21…2026-10-22. Wtedy:
- trasa zwraca `days = [{day: 2026-10-19, sources: [wykop]}, {day: 2026-10-20, sources: [wykop]}]`;
- sekcja pokazuje jeden zakres 19–20 października 2026 · Wykop;
- przycisk wpisuje `from = 2026-10-19`, `to = 2026-10-20`, źródła = [wykop].

### Key Discoveries:

- `PipelineRunController.java:124-127`: trasa `/sources` z dosłowną ścieżką stoi obok `/{id}`, więc `/missing-days` pasuje do tego samego wzorca.
- `auth/AuthorizationCompletenessIT.java:64-67`: lista `ADMIN_ROUTES` (sprawdzana też przez `AdminRoleIT`) musi dostać nową trasę.
- `step/SourceIngest.java` `states()`: lista źródeł z flagą `configured`, ta sama, którą widzi formularz.
- `szczur-radar-web/src/styleContract.test.ts:95-96`: `AdminView.vue` jest na liście `CLEANED`. Nowa sekcja bierze wartości tylko z tokenów (`--space-*`, `--font-size-*`, `--radius*`) i używa istniejących komponentów (`AppButton`, `StatusMessage`).
- `szczur-radar-web/dev/mock-api.js:1-60`: tryby ustawia się osobnymi zmiennymi (np. `FRESHNESS`). `MODE` `loading`/`error` działa na wywołania admina jako pierwsze.
- `api/InternalPipelineRunIT.java:101,372-391`: wzorzec stałego `Clock` (`MutableClock`, `@TestConfiguration`) dla testów zależnych od dnia warszawskiego.
- `szczur-radar-web/src/labels.ts:88` `formatDate`, `src/runs.ts:101` `runRange`: istniejące formatowanie dat do ponownego użycia.

## What We're NOT Doing

- Automatycznego uzupełniania dni w nocnym przebiegu (wariant C odrzucony: ryzyko `TRIGGER_RUN_TIMEOUT` 90 min i fałszywego alarmu, `research.md` „Decyzje po badaniu”).
- Nowego alarmu poza aplikacją ani zdarzenia w `alerts/AlertEvent`: każdą złą noc crona już zgłasza F-04.
- Powiadomienia w dzwonku: wymagałoby migracji klucza `uq_notifications_recipient_kind_run` dla wierszy bez `run_id`.
- Zmiany etykiety „Dane zaktualizowane” w feedzie i czegokolwiek dla zwykłych użytkowników.
- Definicji „brak postów w dniu” (z `posts.published_at`). Dzień pobrany, ale z małą liczbą postów, nie trafia na listę.
- Dni starszych niż okno 14 dni (D-15…D-2) i badania, jak głęboko wstecz Wykop serwuje dane.
- Nadzoru synchronizacji NAS → Google Drive.
- Migracji bazy, zmian w cronach, `.railway/railway.ts` i CI.

## Implementation Approach

Pokrycie liczy czysta, testowana jednostkowo funkcja z listy udanych przebiegów RANGE, dnia „dziś” (Warszawa, z wstrzykniętego `Clock`) i zbioru skonfigurowanych źródeł. Repozytorium dostarcza tylko dane. Kontroler je składa jak przy `/sources`.

Front dostaje listę dni i sam grupuje je w zakresy. Ograniczenie 7 dni to reguła formularza i trasy startu, więc należy do UI, nie do kontraktu dni bez danych.

Reguły pokrycia (te same w kodzie, testach i dokumentach):

1. **Pokrycie dnia.** Dzień warszawski X jest pokryty dla źródła S, gdy istnieje przebieg `kind = range`, `status ∈ {succeeded, partial}`, `S ∈ sources`, dla którego `LocalDate.ofInstant(range_from, ZONE) ≤ X ≤ LocalDate.ofInstant(range_to, ZONE)`. Liczą się przebiegi crona i admina.
2. **Okno.** Okno to `[D-15, D-2]`, czyli 14 dni, gdzie D = dziś w Warszawie. D-1 i D są poza oknem, bo nocny cron jeszcze je pobierze.
3. **Początek okna dla źródła.** Dla źródła S okno zaczyna się od `max(D-15, pierwszy pokryty dzień S w całej historii)`. Źródło, które nigdy nie miało udanego przebiegu, nie daje żadnych dni. Dzięki temu czas sprzed startu produkcji albo sprzed dodania źródła nie wygląda jak dziura.
4. **Które źródła.** Liczą się tylko źródła skonfigurowane (`SourceIngest.states()`, `configured = true`), bo formularz może uruchomić tylko je.
5. **Odpowiedź.** Lista zawiera dzień, gdy brakuje go co najmniej jednemu źródłu. `sources` wymienia brakujące źródła w kolejności `Source`. Dni są rosnąco.

## Critical Implementation Details

- **Fałszywa dziura tuż po północy.** Po jednej złej nocy dzień D-2 jest niepokryty od północy (Warszawa) do końca najbliższego nocnego przebiegu, który ma go w zakresie. To ok. 2–3 h (cron 00:30 UTC + czas przebiegu). Sekcja mówi o tym jednym zdaniem pod listą, a przypadek ma test jednostkowy. Logika go nie ukrywa.
- **Zmiana czasu.** 2026-10-25 trwa 25 h. Dni wyznacza się zawsze przez `LocalDate.ofInstant(…, PipelineRunService.ZONE)`, nigdy dzieląc czas przez 24 h.
- **Wartości źródeł i statusów w SQL.** Bind z enumów (`Source.value()`, `RunKind`, `RunStatus`), nigdy literał w zapytaniu (`pipeline/CLAUDE.md`, `RunRecorder`).

## Faza 1: API — dni bez danych

### Overview

Czysta funkcja pokrycia, zapytania o udane przebiegi zakresu i trasa admina z testami.

### Changes Required:

#### 1. Funkcja pokrycia dni

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/MissingDays.java` (nowy; nazwa do decyzji implementującego, w pakiecie `run/` obok modelu)

**Intent**: Liczy dni bez danych według reguł 1–5 z „Implementation Approach”. Bez Springa i bazy, żeby granice dało się przypiąć testem jednostkowym.

**Contract**: wejście to `today: LocalDate` (Warszawa), skonfigurowane źródła (`List<Source>`), pokrycia w oknie (zakresy dni udanych przebiegów RANGE z ich źródłami) i pierwszy pokryty dzień każdego źródła. Wynik to okno `from`/`to` (`D-15`, `D-2`) i lista `(LocalDate day, List<Source> sources)` rosnąco. Stałe: długość okna 14 dni i koniec D-2 jako nazwane stałe z Javadoc uzasadniającym D-2 (dwie noce na dzień).

#### 2. Zapytania w repozytorium

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java`

**Intent**: Dostarcza dane dla funkcji pokrycia: udane przebiegi RANGE, których zakres sięga okna, i pierwszy pokryty dzień każdego źródła w całej historii.

**Contract**: dwie metody, statusy `SUCCEEDED`/`PARTIAL` i `RunKind.RANGE` bindowane z enumów jak w `findLastUpdate`:
- przebiegi RANGE `succeeded`/`partial` z `rangeTo ≥ początek okna` (zwraca encje albo projekcję `rangeFrom`, `rangeTo`, `sources`);
- najwcześniejszy `range_from` takich przebiegów na źródło. Natywnie `unnest(sources)` z `group by`, albo inaczej, jeśli implementujący znajdzie prostszy zapis. Wartości źródeł zamieniane na `Source` przez `Source.fromValue`.

Bez nowego indeksu: przebiegów jest ok. jeden na dobę.

#### 3. Trasa admina

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java`

**Intent**: `GET /api/admin/pipeline-runs/missing-days` zwraca dni bez danych dla panelu admina. Dzień „dziś” pochodzi z wstrzykniętego `Clock`, źródła z `SourceIngest.states()`.

**Contract**: odpowiedź `200 {from: "YYYY-MM-DD", to: "YYYY-MM-DD", days: [{day: "YYYY-MM-DD", sources: ["wykop"]}]}`, rekordy obok `RunPage` / `RunView`. Bez parametrów, sesja admina (ścieżka `/api/admin/`). Javadoc trasy nazywa reguły pokrycia i okno.

#### 4. Lista tras admina

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java`

**Intent**: Nowa trasa trafia do `ADMIN_ROUTES`, więc `AuthorizationCompletenessIT` sprawdza 401 bez sesji, a `AdminRoleIT` 403 dla zwykłego użytkownika.

**Contract**: wpis `"GET /api/admin/pipeline-runs/missing-days"`.

#### 5. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/run/MissingDaysTest.java` (nowy), `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/MissingDaysIT.java` (nowy; albo przypadki w `PipelineRunControllerIT`)

**Intent**: Przypina reguły pokrycia jednostkowo, a trasę i zapytania na Postgresie ze stałym `Clock`.

**Contract**:
- Test jednostkowy:
  - przykład z „Desired End State” (2026-10-24 → dni 19 i 20 dla Wykopu);
  - D-1 i D nigdy na liście;
  - okno od pierwszego pokrytego dnia źródła;
  - źródło bez udanego przebiegu → brak dni;
  - dzień 2026-10-25 (zmiana czasu) pokryty przebiegiem jednodniowym;
  - nakładające się zakresy;
  - dwa źródła z różnymi lukami (`sources` per dzień);
  - fałszywa dziura po jednej złej nocy: dziś tuż po północy, D-2 niepokryty → jest na liście.
- IT:
  - `failed`, `interrupted`, `queued` i RETRY/POSTS nie pokrywają;
  - `partial` pokrywa;
  - przebieg admina pokrywa;
  - źródło nieskonfigurowane pominięte (o ile wzorzec testów źródeł na to pozwala, np. `PipelineRunWithoutSourceIT`);
  - dokładne klucze JSON.

#### 6. Dokument pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: Wiersz `PipelineRunController` w tabeli „Entries” wymienia `missing-days` i jego reguły w jednym zdaniu. W „Tests” jest nowy test.

**Contract**: sekcje „Entries (`api/`)” i „Tests”.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-api && ./mvnw -B test`
- Testy integracyjne (z nowym IT, `AuthorizationCompletenessIT`, `AdminRoleIT`) przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- Celowe złamanie reguły (np. koniec okna D-1 zamiast D-2 albo `failed` liczony jako pokrycie) czerwieni `MissingDaysTest`/IT. Sprawdzone raz i cofnięte.

**Implementation Note**: Po zielonych testach fazy przejdź do fazy 2. Faza 1 nie ma kroku ręcznego.

---

## Faza 2: Web — sekcja „Dni bez danych” w /admin

### Overview

Klient API, grupowanie dni w zakresy, sekcja w `AdminView` z wypełnianiem formularza, testy, tryb atrapy i zrzuty.

### Changes Required:

#### 1. Klient API

**File**: `szczur-radar-web/src/api/pipeline.ts`

**Intent**: Typy i funkcja `fetchMissingDays()` według kontraktu trasy z fazy 1.

**Contract**: `interface MissingDays { from: string; to: string; days: { day: string; sources: string[] }[] }`, `fetchMissingDays(): Promise<MissingDays>` przez `apiFetch` na `/api/admin/pipeline-runs/missing-days`. Komentarz nagłówkowy pliku już wskazuje kontroler.

#### 2. Grupowanie w zakresy

**File**: `szczur-radar-web/src/missingDays.ts` (nowy) + `src/missingDays.test.ts`

**Intent**: Zamienia listę dni na zakresy do formularza. Kolejne dni z tym samym zbiorem brakujących źródeł tworzą jeden zakres, cięty na kawałki po najwyżej 7 dni, bo tyle przyjmuje start.

**Contract**:
- `missingRanges(days): { from: string; to: string; sources: string[] }[]`, rosnąco. Dzień przerwy albo inny zbiór źródeł zaczyna nowy zakres.
- Stała 7 z komentarzem wskazującym `PipelineRunController.MAX_RANGE_DAYS`.
- Kalendarz na datach `YYYY-MM-DD` bez strefy przeglądarki (np. `Date.UTC`), żeby zmiana czasu nie przesuwała dni.
- Testy:
  - przykład z „Desired End State”;
  - przerwa w dniach;
  - różne zbiory źródeł;
  - 9 kolejnych dni → zakresy 7 + 2;
  - pusta lista;
  - przejście przez 2026-10-25.

#### 3. Sekcja w widoku

**File**: `szczur-radar-web/src/views/AdminView.vue`

**Intent**: Sekcja „Dni bez danych” między „Nowy przebieg” a „Ostatnie przebiegi”:
- lista zakresów z datami (`formatDate` / format jak `runRange`) i nazwami źródeł (`label('source', …)`);
- przy każdym zakresie przycisk „Wpisz do formularza”, który ustawia `from`, `to` i `chosenSources` (tylko źródła skonfigurowane), czyści `startMessage`/`startError` i przenosi fokus na pole „Od”;
- zdanie pod listą o możliwej fałszywej dziurze D-2 tuż po północy;
- stany jak w pozostałych sekcjach: `aria-busy` i „Wczytywanie…” przy pierwszym ładowaniu, `StatusMessage tone="error"` przy błędzie, przy pustej liście komunikat z oknem („Brak dni bez danych od … do …”).

Wczytywana przy montowaniu, przez „Odśwież” (`refresh()`) i po tym, jak przebieg z listy przestaje być aktywny, bo wtedy pokrycie mogło się zmienić.

**Contract**: wartości tylko z tokenów (plik jest na liście `CLEANED`). Przyciski to `AppButton` (secondary), komunikaty to `StatusMessage`. Bez nowego komponentu współdzielonego, więc bez wpisu w kitchen sink. Pola formularza zostają globalnymi polami z `style.css`.

#### 4. Testy widoku

**File**: `szczur-radar-web/src/views/AdminView.test.ts`

**Intent**: Przypina zachowanie sekcji.

**Contract**:
- lista zakresów z przykładu;
- kliknięcie „Wpisz do formularza” ustawia wartości pól „Od”/„Do” i zaznaczenie źródeł oraz przenosi fokus na „Od”;
- pusta lista z oknem;
- błąd;
- ponowne wczytanie po „Odśwież”.

Mocki API jak w istniejących testach pliku.

#### 5. Atrapa API

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Trasa `/api/admin/pipeline-runs/missing-days` dla zrzutów.

**Contract**: zmienna `MISSING`, niezależna od `MODE` (`MODE` `loading`/`error` działa pierwsze):
- `'gaps'` (domyślnie): dwa zakresy względem dzisiejszej daty, jeden 3-dniowy i jeden 1-dniowy, Wykop;
- `'none'`: pusta lista;
- `'error'`: 500.

Opis w komentarzu nagłówkowym jak przy `FRESHNESS`.

### Success Criteria:

#### Automated Verification:

- Testy Vitest przechodzą (z `missingDays.test.ts`, `AdminView.test.ts`, `styleContract.test.ts`): `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`

#### Manual Verification:

- Zrzuty `/admin` z atrapą (`playwright-cli` + `dev/mock-api.js`, procedura z nagłówka atrapy), wykonane przez agenta, w jasnym i ciemnym motywie oraz przy 375 px. Dla 7 stanów sekcji zrzut albo N/A z powodem: default (`gaps`), hover i focus-visible przycisku „Wpisz do formularza”, disabled (N/A: przycisk nie ma stanu wyłączonego), error (`MISSING='error'`), empty (`'none'`), loading (`MODE='loading'`). Człowiek ogląda zrzuty i akceptuje wygląd.

**Implementation Note**: Po zielonych testach i zrzutach poczekaj na akceptację wyglądu przez człowieka, zanim przejdziesz do fazy 3.

---

## Faza 3: Dokumenty i weryfikacja w PR env

### Overview

Runbook operatora i sprawdzenie całości na prawdziwym Wykopie w PR env.

### Changes Required:

#### 1. Runbook

**File**: `context/deployment/deploy-plan.md`

**Intent**: Nowa sekcja „S-13: dni bez danych (missed-night-detection)” z linią Status prawdziwą po merge'u, zgodnie z lekcją o linii Status: „**Status:** delivered by PR #N; production verification and closure in issue #72.”

Treść sekcji:
- co pokazuje lista (reguły 1–5, okno D-15…D-2, fałszywa dziura po północy);
- że alarm o złej nocy daje F-04 (odsyłacz do sekcji F-04);
- runbook „po alarmie w Telegramie”: poczekaj na następną noc albo otwórz `/admin` → „Dni bez danych” → „Wpisz do formularza” → „Uruchom”, z zasadami okna bez merge'a;
- weryfikacja produkcji po merge'u: lista otwiera się i jest pusta albo zgodna z historią przebiegów. Wynik idzie w komentarzu issue #72.

**Contract**: nowa sekcja na końcu pliku, w stylu sekcji F-04.

#### 2. Wytyczne repozytorium

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Jedno zdanie w punkcie „Daily run” sekcji Deployment: dni bez udanego przebiegu zakresu z ostatnich 14 dni (do D-2) widać w `/admin` w sekcji „Dni bez danych”, skąd admin uzupełnia je ręcznie (S-13); alarm o złej nocy daje F-04.

**Contract**: punkt „Daily run (since S-05)”.

#### 3. Weryfikacja w PR env

**File**: brak (operacja w PR env `szczur-radar-pr-<N>`)

**Intent**: Sprawdza całość na prawdziwych danych: Wykop i LLM z ustawień CI, w PR env dozwolone. Nocny cron jest tam pominięty, więc pokrycie tworzą tylko przebiegi admina.

Kroki:
1. Sprawdź `missing-days` przed startem i zanotuj stan.
2. Uruchom przebieg zakresu jednego dnia D-3.
3. Po jego końcu `missing-days` pokazuje D-2 (Wykop), a sekcja zakres D-2.
4. „Wpisz do formularza” → „Uruchom”.
5. Po końcu przebiegu lista jest pusta.

Nie pushuj na gałąź, dopóki przebieg jest `RUNNING`.

**Contract**: dowody (odpowiedzi trasy, zrzut sekcji, id przebiegów) w komentarzu issue #72.

### Success Criteria:

#### Automated Verification:

- Pełny CI na PR jest zielony (wszystkie joby `.github/workflows/ci.yml`, w tym `backend`, testy web, `railway-config`): `gh pr checks <N> --watch`

#### Manual Verification:

- Scenariusz w PR env (przebieg D-3, lista pokazuje D-2, wypełnienie i uruchomienie, lista pusta) wykonany przez agenta z dowodami w issue #72. Człowiek akceptuje dowody.
- Człowiek akceptuje sekcję S-13 w `deploy-plan.md` (zgodność runbooka z zachowaniem).

**Implementation Note**: Weryfikacja produkcji po merge'u nie jest krokiem Progress. Żyje w runbooku i w issue #72 (lekcja „Implementacja, przegląd i archiwizacja na jednej gałęzi”).

---

## Testing Strategy

### Unit Tests:

- `MissingDaysTest`: reguły 1–5, D-1/D poza oknem, początek okna od pierwszego pokrytego dnia, zmiana czasu 2026-10-25, dwa źródła, fałszywa dziura po północy.
- `missingDays.test.ts`: grupowanie, cięcie po 7 dni, przerwy, różne źródła, zmiana czasu.

### Integration Tests:

- IT trasy na Postgresie ze stałym `Clock`: które statusy i rodzaje pokrywają, przebieg admina, źródło nieskonfigurowane, klucze JSON.
- `AuthorizationCompletenessIT` / `AdminRoleIT`: 401 bez sesji i 403 dla zwykłego użytkownika.
- `AdminView.test.ts`: stany sekcji i wypełnianie formularza.

### Manual Testing Steps:

1. Zrzuty `/admin` z atrapą w trybach `gaps`, `none`, `error` i `MODE='loading'`, w obu motywach i przy 375 px.
2. Scenariusz w PR env z fazy 3.

## Performance Considerations

Trasa czyta przebiegi z ok. 14 dni (kilkanaście–kilkadziesiąt wierszy) i jedną agregację po źródłach. Nowy indeks nie jest potrzebny, bo tabela rośnie o ok. jeden wiersz na dobę. Wywołanie budzi uśpione API tylko wtedy, gdy admin otwiera `/admin`, tak jak pozostałe sekcje.

## Migration Notes

Bez migracji i bez zmian danych. Wycofanie to zwykły revert.

## References

- Research: `context/changes/missed-night-detection/research.md` (sekcje 2–5 i „Decyzje po badaniu”)
- Audyt C2/C3: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:91-92`
- F-04 (alarm o złej nocy): `context/deployment/deploy-plan.md`, sekcja F-04; issue #66
- Wzorzec trasy: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java:124-127`
- Wzorzec stałego `Clock`: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java:101,372-391`
- Issue: #72 (S-13)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: API — dni bez danych

#### Automated

- [x] 1.1 Testy jednostkowe przechodzą: `cd szczur-radar-api && ./mvnw -B test` — 06854b5
- [x] 1.2 Testy integracyjne (z nowym IT, `AuthorizationCompletenessIT`, `AdminRoleIT`) przechodzą: `cd szczur-radar-api && ./mvnw -B verify` — 06854b5
- [x] 1.3 Celowe złamanie reguły czerwieni `MissingDaysTest`/IT (sprawdzone raz i cofnięte) — 06854b5

### Phase 2: Web — sekcja „Dni bez danych” w /admin

#### Automated

- [x] 2.1 Testy Vitest przechodzą: `cd szczur-radar-web && npm test` — 6ebb057
- [x] 2.2 Typecheck i build przechodzą: `cd szczur-radar-web && npm run build` — 6ebb057

#### Manual

- [ ] 2.3 Zrzuty `/admin` z atrapą (7 stanów, oba motywy, 375 px) zaakceptowane przez człowieka

### Phase 3: Dokumenty i weryfikacja w PR env

#### Automated

- [x] 3.1 Pełny CI na PR jest zielony: `gh pr checks <N> --watch` — dd554fa

#### Manual

- [ ] 3.2 Scenariusz w PR env (D-3 → lista D-2 → wypełnienie i uruchomienie → lista pusta) z dowodami w issue #72
- [ ] 3.3 Człowiek akceptuje sekcję S-13 w `deploy-plan.md`
