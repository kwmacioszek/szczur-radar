---
date: 2026-11-11T12:27:34+0100
researcher: kwmacioszek (Claude Opus 5.5)
git_commit: 7b263b0
branch: feature/contract-bound-mocks
repository: kwmacioszek/szczur-radar
topic: "S-43: co dziś wiąże atrapy API (dev/mock-api.js, atrapa pipeline-trigger, stuby deploy-gate) z prawdziwym API i na czym można je oprzeć"
tags: [research, mock-api, contract, spa-api-contract, pipeline-trigger, deploy-gate, rules-registry, m-4, s-43]
status: complete
last_updated: 2026-11-11
last_updated_by: kwmacioszek (Claude Opus 5.5)
---

# Research: atrapy związane z kontraktem (S-43)

**Date**: 2026-11-11T12:27:34+0100
**Researcher**: kwmacioszek (Claude Opus 5.5)
**Git Commit**: 7b263b0 (`main` po S-41, PR #163; kod atrapy SPA ostatnio zmieniony w S-33, `6e7fa94`)
**Branch**: feature/contract-bound-mocks
**Repository**: kwmacioszek/szczur-radar

Ścieżki: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `test/` = `szczur-radar-api/src/test/java/pl/szczurradar/api/`, `web/` = `szczur-radar-web/`, `mock` = `web/dev/mock-api.js`, `T` = `tools/pipeline-trigger/trigger.sh`, `S` = `tools/pipeline-trigger/test.sh`, `G` = `tools/deploy-gate/test.sh`, `IPRC` = `api/pipeline/api/InternalPipelineRunController.java`, `IPRIT` = `test/pipeline/api/InternalPipelineRunIT.java`.

## Research Question

Wycinek S-43 (`context/foundation/roadmap.md:322-331`). Outcome: `dev/mock-api.js` odpowiada z plików kontraktu albo test porównuje jego odpowiedzi z kontraktem (audyt: 7 rozbieżności), a atrapy narzędzi (`pipeline-trigger`, stuby `deploy-gate`) czytają statusy i kształty z rejestru. Pytania:

1. Jak dziś działa i jest uruchamiana atrapa SPA, czy test może ją wywołać, i które rozbieżności z API (audyt z 2026-11-01) nadal istnieją po S-29, S-33, S-34, S-41, S-44?
2. Na jakiej infrastrukturze kontraktu z S-24/S-41 można oprzeć porównanie (co porównuje Java, co Vitest, jakie są gotowe funkcje i ograniczenia)?
3. Od jakich tras, pól i wartości API zależą `trigger.sh` i `wait-safe.sh`/`verify-apply.sh`, jak ich atrapy to odtwarzają i jaki „rejestr” narzędzia mogłyby czytać?

Wynik to materiał dla `/10x-plan`, nie plan.

## Summary

- **Atrapa SPA nadal ma wszystkie 7 rozbieżności z audytu i 5 nowych** (tabela w §1.3). Klucze wszystkich 14 kształtów z rejestru są zgodne z plikami kontraktu (porównanie drzew kluczy skryptem badacza). Rozjeżdżają się zachowania i wartości: źródła tylko `wykop` (`mock:420`), start przebiegu zawsze 409 (`mock:430`), logowanie niezamockowane (spada do 404 z `mock:504`), identyfikatory bez UUID i 404 zamiast 400, stronicowanie i limity bez walidacji, tryb `anonymous` tylko na `/me`.
- **Nic poza dwoma skanami tekstu nie sprawdza atrapy** (`rules.test.ts:82-84`: literał limitu strony; `:255-276`: kody błędów). Dlatego zmiana nazwy pola w atrapie przechodzi (eksperyment X4 z audytu).
- **Atrapa to skrypt `playwright-cli run-code`, bez `export`**: jedno wyrażenie `async page => {…}` (`mock:93`), które instaluje `page.route` na `/api/` (`mock:398-399`). Badacz sprawdził w scratchpadzie, że wykonanie tekstu (`(0,eval)(src)`) nie ma skutków ubocznych i daje funkcję. Po podaniu atrapy obiektów `page`/`route` każda trasa odpowiada. Vitest ma już tekst atrapy przez `?raw` (`rules.test.ts:2`), więc test może ją wywołać bez przeglądarki. Trzeba przy tym obsłużyć 60-sekundowe `setTimeout` (`mock:396`, `:478`, `:498`), stan na instalację (`readIds` `mock:229`, mutowana tablica `failed`) i tryby zapisane jako stałe `const` (`mock:374-379`).
- **Odpowiadanie wyłącznie z plików kontraktu straciłoby prawie wszystkie stany do zrzutów ekranu** (§1.5). Pliki niosą po jednym, dwóch wariantach na kształt, a atrapa ma m.in. 32 przebiegi, 120 postów z filtrem, stany ładowania i błędu oraz względne czasy. Realne kierunki to test porównujący odpowiedzi atrapy z kontraktem albo budowanie wariantów atrapy na bazie plików.
- **Po stronie Javy pliki kontraktu są tylko porównywane, nigdy zapisywane** (`ContractFiles.assertMatches`, `test/contract/ContractFiles.java:44-54`). Porównanie to ścisła równość po maskowaniu UUID i instantów: kolejność kluczy bez znaczenia, kolejność tablic ma znaczenie. Vitest porównuje próbki z plikami wartościowo (`shapes.test.ts:20`). **W TS nie ma funkcji porównującej sam kształt.** Odpowiednik w Javie (`ContractVariantsTest.Walk.record`, `:304-314`) jest prywatny.
- **`/internal/**` nie należy do żadnego rejestru** (`ContractRoutesIT.java:41` filtruje do `/api/`). `/api/deploy-safety`, `/api/ready` i `/api/health` są w `NOT_READ_BY_SPA` bez plików (`ContractRegistry.java:64-66`). `DeploySafetyReason` nie ma w `rules.json`.
- **Atrapa `pipeline-trigger` jest dziś zgodna co do kluczy i kodów** (16 pól `RunState` przypiętych przez `IPRIT:251`, 202 `[{id,kind}]` przez `IPRIT:235`). Wiąże się z API tylko ręcznymi kopiami: 6 statusów w `classify` (`T:210-219`), pola z jq, kody błędów, a w atrapie stałe wartości. Dwie różnice semantyczne:
  - `assessTotal: 251` dla każdego stanu poza `queued`, także dla przebiegu zakończonego przed krokiem oceny (`S:181`); API wysyła wtedy null (`IPRIT:262-263`).
  - `nightly-summary` ignoruje ciało żądania, więc 400/404 nie są modelowane.
- **Stuby `deploy-gate` po stronie API są małe.** `wait-safe.sh` czyta `.safe` i `.reason` jako dowolny niepusty tekst (`wait-safe.sh:91-95`) oraz kody HTTP. `verify-apply.sh` czyta z `/api/ready` tylko kod statusu (`:202`, `:311-313`). Stub `ready` wysyła `{"status":"ok"}` (`G:610`), a API `{"status":"ready"}`. To dziś nieszkodliwe, bo ciało nie jest czytane. Pozostałe stuby i `fixtures/*.json` dotyczą CLI `railway`, nie API.
- **Wzorce, na których można się oprzeć, już są w repo:**
  - `rules.json` ma `values.runStatus`, `runKind`, `interruptionCause`, `step` i `errorCodes`; sprawdza go `SpaRulesTest` wobec enumów.
  - Wspólną tabelę danych czytaną przez test Javy i test powłoki ma `tools/healthchecks/fixtures/ping-urls.txt` (`AlertSettingsTest:34` przez `Path.of("../tools/…")`, `tools/healthchecks/test.sh:442-446`).
  - Każdy job CI robi pełny checkout. Testy narzędzi działają na hoście i mogą czytać pliki repo.

## Detailed Findings

### 1. Atrapa SPA `web/dev/mock-api.js`

#### 1.1 Uruchamianie i odbiorcy

- Nagłówek opisuje tryby i procedurę: `npx vite`, `playwright-cli open`, `playwright-cli run-code --filename=szczur-radar-web/dev/mock-api.js` (`mock:1-91`). Inny tryb wymaga uruchomienia kopii pliku z podmienioną stałą (`mock:3-4`). Przełączników przez env ani query nie ma.
- `vite.config.ts` tylko proxuje `/api` na :8000 i nie odwołuje się do atrapy (raport badacza).
- Odbiorcy:
  - `rules.test.ts:2` importuje atrapę jako tekst `?raw`;
  - `REPOSITORY_GUIDELINES.md` (sekcja UI) opisuje ją jako narzędzie zrzutów ekranu;
  - roadmapa i `verification-scope.md` przewidują ją jako podłogę pod E2E w M-5 (`roadmap.md:391`).
- Typy: `tsconfig.app.json:14` obejmuje tylko `src/**`, więc atrapa jest nietypowanym JS. Import `?raw` typuje się przez deklarację `vite/client` (`tsconfig.app.json:5`). Obraz web kopiuje cały kontekst (`web/Dockerfile:10`), a `.dockerignore` nie wyklucza `dev/` (`.dockerignore:1-8`), więc obraz widzi plik atrapy.
- Niezweryfikowane: badacz infrastruktury wnioskuje z konfiguracji (bez uruchomienia), że zwykły `import` pliku JS z testu w `src/` mógłby nie przejść `vue-tsc` bez `allowJs` albo pliku `.d.ts`. `?raw` działa dziś bez tego.

#### 1.2 Trasy (handler `mock:399-505`)

| Metoda + ścieżka | Status | Ciało |
|---|---|---|
| dowolna `/api/auth/me` | 200 user `u1` / 401 `not_authenticated` (AUTH) | `mock:402-406` |
| dowolna `/api/auth/logout` | 204 | `mock:407` |
| dowolna `/api/auth/activation/lookup`, `/api/auth/activation` | 200 `{email}` / 204 / 400 `activation_link_invalid` lub `password_policy` / 500 / zawieszenie (ACTIVATION) | `mock:408-417` |
| `/api/admin/**` | MODE `loading` = zawieszenie, `error` = 500 | `mock:418-419` |
| `/api/admin/pipeline-runs/sources` | 200 `[{wykop,true}]` | `mock:420` |
| `/api/admin/pipeline-runs/missing-days` | 200 / 500 (MISSING) | `missingDays()` `mock:384-387` |
| GET `/api/admin/pipeline-runs?page&size` | 200, 32 przebiegi | `allRuns()` i buildery `mock:98-226`, stronicowanie `mock:422-428` |
| inna metoda `/api/admin/pipeline-runs` | zawsze 409 `pipeline_busy` | `mock:430` |
| `/api/admin/pipeline-runs/{id}` | 200 / 404 `not_found` | `mock:431-435` |
| `/api/admin/notifications`, `/read`, `/{id}/read` | 200 / 204 / 404 / 500 | `mock:436-451` (wg badacza) |
| `/api/admin/posts/failed` | 200, zawsze `page:0,size:50` | `mock:452-456` |
| POST `/api/admin/posts/restore` | 200 `{restored}` / 400 `invalid_request` | `mock:457-467` |
| `/api/freshness` | 200 / 500 (FRESHNESS) | `mock:389-395`, `:468-471` |
| `/api/posts?page&size&q&kind&direction` | 200 / 400 / 500 / zawieszenie | `mock:472-494` |
| `/api/posts/{id}[/raw]` | 200 / 404 `post_not_found` / 500 / zawieszenie | `mock:495-503` |
| cokolwiek innego | 404 `not_found` | `mock:504` |

Metodę sprawdzają tylko GET listy przebiegów (`mock:422`) i POST restore (`mock:457`). Każda trasa `/api/` wołana w `web/src/api/*.ts` ma odpowiedź w atrapie poza `POST /api/auth/login` (`web/src/api/auth.ts:13`). Badacz przejrzał wszystkie literały `/api/` w tych plikach.

#### 1.3 Rozbieżności z API (stan na `7b263b0`)

Kształt: drzewa kluczy wszystkich ciał z tabeli §1.2, które mają plik kontraktu, są zgodne z plikami (porównanie skryptem z scratchpadu; różni się tylko kolejność kluczy). Warianty null rozjeżdżają się w obie strony:
- atrapa ma kroki `running` z `finishedAt`/`itemsOut` null, których nie ma `run.json`;
- atrapa nigdy nie wysyła null w `webUrl`/`author` ani powiadomienia z `run: null`, a kontrakt je ma.

| # | Rozbieżność | Atrapa | API | Werdykt |
|---|---|---|---|---|
| 1 | źródła | `[{wykop,true}]` (`mock:420`), choć przebiegi, świeżość i feed atrapy używają Reddita | każde zarejestrowane źródło (`sources.json`: wykop + reddit) | nadal |
| 2 | start przebiegu | zawsze 409 `pipeline_busy` (`mock:430`), także dla ponowienia `postIds` | 202 `List<RunView>` (`api/pipeline/api/PipelineRunController.java:100-111`, `started-runs.json`) | nadal |
| 3 | logowanie | niezamockowane → 404 `not_found` (`mock:504`) | 200 / 401 `invalid_credentials` / 429 (`AuthController`) | nadal |
| 4 | identyfikatory | `u1`, `r-*`, `n-*`, `feed-N` (`mock:405,434,448,501`); nieznany id → 404 | UUID; id bez UUID → 400 (`ApiExceptionHandler.java:37-41`) | nadal |
| 5 | nieudane posty | ignoruje `page`/`size` (`mock:455`) | odsyła żądane wartości (`FailedPostController`) | nadal |
| 6 | naruszenia próby | `violations: []` przy `schema_violation`, `fabricated_evidence`, także `provider_error` (`mock:250-252`) | w kontrakcie każda próba ma ≥1 naruszenie (`failed-posts-page.json`) | nadal; czy API zapisuje kiedykolwiek pustą listę — niesprawdzone |
| 7 | limity | `size=500` → 200 na postach i przebiegach; `page=-1` na `/api/posts` → 200 z `page:-1`; sprawdzany tylko `page > 21474836` (`mock:476`) | `size` ≤ 100, `@Min(0)` (`PostController.java:88`) → 400 | nadal |
| N1 | restore | `{postIds:['p3']}` → 200 (`mock:460`) | `List<UUID>` → 400 (`DeadPostController.java:59`; wniosek z obsługi `HttpMessageNotReadableException`, nieuruchamiane) | nowa |
| N2 | `AUTH='anonymous'` | 401 tylko na `/me` (`mock:402-406`); trasy admina i postów dalej dają dane | każda trasa poza publicznymi → 401 | nowa |
| N3 | rola `user` | AUTH zna tylko `admin`/`anonymous` (`mock:376`) | 403 na `/api/admin/**` dla zwykłego użytkownika | nowa (brak wariantu) |
| N4 | czas wyniku źródła | `r-source-failed`: wynik Reddita kończy się 00:31:58, a krok `fetch` 00:30:40 (`mock:102` vs `:171`) | wynik źródła zapisuje się w trakcie `fetch` | nowa (semantyka) |
| N5 | dni bez danych | zawsze tylko `wykop` (`mock:386`) | per skonfigurowane źródło (`missing-days.json` ma reddit) | nowa |

Dodatkowo `/raw` zwraca wpis w formacie Wykopu także dla postów z Reddita (`mock:62-63`, wg badacza).

#### 1.4 Semantyka zgodna z API (wg badacza)

- Powody „wymaga uwagi” w przebiegach atrapy odpowiadają `PipelineRun.attentionReasons` (`api/pipeline/run/PipelineRun.java:321-347`).
- `fetched`/`skipped` źródeł sumują się do liczników przebiegu, a `sourcesFailed`/`sourcesEmpty` zgadzają się z `sourceResults`.
- `store.itemsOut` = `newPosts`.
- Filtr feedu przycina białe znaki z §10 tym samym zbiorem co SPA (`mock:486-487`).

#### 1.5 Co atrapa pokrywa, a pliki kontraktu nie

- Atrapa ma, a kontrakt nie:
  - przebiegi: `running` z `inFlight`, `failed` (puste źródło, watchdog), `interrupted` `deploy`/`lost`, `source_empty`, 32 przebiegi na 2 stronach;
  - feed: 120 postów, filtr z semantyką i przypadkami 400, kanały Reddita, wszystkie kierunki i rodzaje celów, post z 5 celami;
  - nieudany post z `provider_error` i `technicalFailures`;
  - 6 powiadomień z trwałym stanem przeczytania;
  - tryby świeżości, dni bez danych i aktywacji, stany ładowania, błędu i pustki, względne czasy.
- Kontrakt ma, a atrapa nie:
  - przebiegi `retry` i `posts`, przebieg `queued`, przyczyna `restart`;
  - powód `processing_errors`, `modifiers: ['humor']`, klasa `timeout`;
  - powiadomienie `run: null`.

### 2. Infrastruktura kontraktu po S-41

#### 2.1 Java: tylko porównanie

- `ContractFiles.assertMatches(json, fileName)` (`test/contract/ContractFiles.java:44-54`) parsuje ciało, maskuje je i wymaga równości z plikiem. Gdy plik brakuje albo się różni, wypisuje zamaskowane ciało. Plik poprawia się ręcznie, kopiując to ciało (`:40-43`).
- Katalog: `../szczur-radar-web/src/contract/` (`:23`).
- Maskowanie (`:57-79`) działa rekursywnie: pełny UUID staje się `<uuid>`, a instant `\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z` staje się `<instant>`. Daty `yyyy-MM-dd` nie są maskowane.
- Wywołania per plik:

  | Plik | Test |
  |---|---|
  | `user.json` | `AuthIT:435,439` |
  | `activation-lookup.json` | `ActivationIT:95` |
  | `notifications.json` | `NotificationsIT:282` |
  | `notifications-without-run.json` | `NotificationsIT:294`, serializowany z obiektu, bo baza nie trzyma tego stanu |
  | `sources.json`, `started-runs.json`, `run.json`, `runs-page.json` | `PipelineRunControllerIT:169,195,511,578` |
  | `freshness.json` | `FreshnessIT:218` |
  | `missing-days.json` | `MissingDaysIT:203` |
  | `post.json`, `posts-page.json`, `failed-posts-page.json`, `restore-result.json` | `PostsApiIT:265,290,326,702` |

#### 2.2 Rejestr tras

- `ContractRegistry.SHAPES` (`:77-95`) ma 14 tras i 14 plików, z typami Javy ładowanymi przez `Class.forName`.
- `NOT_READ_BY_SPA` (`:63-72`) ma 9 tras z powodami, m.in.:
  - `GET /api/health`: probe Railway i monitora;
  - `GET /api/ready`: CI i monitor;
  - `GET /api/deploy-safety`: „the CI deploy gate reads it (tools/deploy-gate)”.
- `ContractRoutesIT` bierze tylko wzorce zaczynające się od `/api/` (`:41`, javadoc `:24-25`). Trasy `/internal/**` (`IPRC:82`, `:94`, `:109`) są poza kontraktem.
- `ContractVariantsTest`:
  - każdy komponent `@Nullable` osiągniętego rekordu jest null w ≥1 pliku i nie-null w innym; każda lista jest niepusta co najmniej raz (`:107-151`);
  - każdy obiekt ma dokładnie klucze swojego rekordu (`:313`);
  - katalog zawiera dokładnie pliki rejestru, bez `rules.json` (`:75-93`);
  - `KNOWN_GAPS` (`:54-63`): `PostItem.assessment`, `LastAttempt.failureClass`, `NotifiedRun.finishedAt`.

#### 2.3 Vitest

- `shapes.test.ts:8-12` zbiera pliki przez `import.meta.glob(['./*.json','!./rules.json'])`. `:16` wymaga, żeby klucze `SAMPLES` były równe nazwom plików, a `:20` sprawdza `toEqual` próbki z plikiem, czyli równość wartości. Próbki trzymają `'<uuid>'`/`'<instant>'` jako literały (`samples.ts:7`). W TS nie ma kodu maskującego.
- `responseTypes.test.ts` skanuje `src/api/*.ts` i odrzuca pola `?:` poza `ALLOWED` oraz `Partial<` (`:7-58`).
- W `src/contract/` nie ma eksportowanej funkcji porównującej kształt ani zbiór kluczy (`optionalFields`, `valuesOf`, `sorted` są lokalne: `responseTypes.test.ts:26`, `rules.test.ts:40,44`). Test atrapy wobec kontraktu potrzebuje nowej funkcji.
- Plik kontraktu nie mówi, które pola są nullable. Wiedzę o wariantach null daje dopiero suma plików danego rekordu, a mapowanie zagnieżdżonego obiektu na rekord zna tylko Java (`ContractVariantsTest.Walk`).

#### 2.4 `rules.json`

- Sekcje:
  - `whitespace`: 29 punktów kodowych;
  - `limits`: m.in. `runsPageMaxSize`, `pageMax`, `feedQueryMaxLength`;
  - `values`: 18 zbiorów w kolejności enumów, m.in. `runKind`, `runStatus`, `interruptionCause`, `step`, `stepStatus`, `source`, `sourceStatus`, `attentionReason`;
  - `errorCodes`: 15 kodów.

  `SpaRulesTest.java:77-103` wymaga dokładnie tych sekcji. Brak w nim m.in. ról, rodzajów powiadomień i `DeploySafetyReason`.
- Czytają go tylko `SpaRulesTest` i `rules.test.ts` (`git grep` nie znajduje czytnika w `tools/`).
- Haczyk: `rules.test.ts:86-96` wymaga etykiety SPA dla każdego zbioru `values` poza `attentionReason` i `itemType`. Zbiór czytany tylko przez narzędzie (np. powody `deploy-safety`) wymagałby kolejnego wyjątku albo osobnej sekcji.

#### 2.5 Ograniczenia z wcześniejszych zmian

- Pliki kontraktu są w module web, bo obraz web typuje testy z własnego kontekstu (S-24 `plan.md:33`, `:228`).
- S-24 świadomie zostawił atrapę bez typów i wspólnych danych. Skan obejmuje w niej tylko kody błędów; kopie białych znaków, limitu `q` i tekstów przerwania nie są skanowane (`context/archive/2026-10-29-api-contract-guard/plan.md:51`, K5).
- S-41 przekazał do S-43 atrapę SPA, atrapę `pipeline-trigger`, stuby `deploy-gate` i atrapę crona dla `/internal/**` (`context/archive/2026-11-08-api-contract-shapes-round-two/plan.md:58`, `:62`). Atrapy w S-41 nie zmieniał, bo już wysyłała oba pola, które stały się wymagane (`:203`).

### 3. Atrapy narzędzi

#### 3.1 `pipeline-trigger`

| Zależność `trigger.sh` | Źródło w API | Przypięte przez | Atrapa zgodna? |
|---|---|---|---|
| `POST /daily` → 202 `[{id,kind}]`; `.id` z regexem `^[0-9a-f-]{36}$` (`T:297-305`) | `IPRC:82-88`, `StartedRun` `:124` | `IPRIT:235` ścisłe ciało | tak (`S:159`) |
| 409 ponawiane, `detail` nieczytane (`T:277`) | `ErrorCode.PIPELINE_BUSY`, `PipelineRunExceptionHandler` | `IPRIT:321-323` | tak (`S:86`) |
| 503 z `detail == source_not_configured` → błąd (`T:265`) | `ErrorCode:63`, handler `:30-32` | tylko na trasie admina (`PipelineRunWithoutSourceIT:75`, `PipelineRunControllerIT:287`), nie na `/internal/daily` | tak (`S:86`) |
| 401 (`T:287`) | `JsonAuthenticationEntryPoint` | `IPRIT:97,162-171` | tak (`S:117`) |
| `GET /{id}` → 200 `RunState` (16 komponentów, `IPRC:158-161`) | — | `IPRIT:251` `containsOnlyKeys` (audyt podawał `:231`) | klucze tak (`S:176-183`) |
| `.status` → `classify` na 6 wartościach (`T:210-219`); nieznana wartość → `return 1` | `RunStatus` (6 wartości) | `IPRIT` dla `failed`, `running`, `interrupted` (`:258`, `:288`, `:305`); `classify` niezwiązane z enumem | tak |
| tętno: `.step .assessTotal .assessedOk .assessedFailed .processingErrors .lastActivityAt` (`T:224-228`, `?` dla null) | `RunState` | `IPRIT:288-296` | częściowo: `assessTotal: 251` dla każdego stanu poza `queued` (`S:181`); API wysyła null przed krokiem oceny (`IPRIT:262-263`); `step` w atrapie to `assess` przy `running`, inaczej null (`S:180`) — atrapa nigdy nie wysyła `fetch` (audyt pisał o kroku `fetch` nieściśle) |
| `.interruptionCause` (`T:343`) | `InterruptionCause` (4 wartości) | `IPRIT:305` (`deploy`) | tak, ale tylko `deploy` (`S:180`) |
| liczniki końcowe (`T:349`) | `RunState` | `IPRIT:251-266` | tak |
| 404 na GET → błąd (`T:325`) | `IPRC:99` | `IPRIT:328-332` | tak (`S:169`) |
| `POST /nightly-summary` z `{runs:[ids]}` (`T:238-239`), czyta niepuste `.text` (`T:244`) | `IPRC:109-121`, `NightlySummary(String text)` `:145` | `IPRIT:350-357` ścisłe, 400/404 `:366-384` | częściowo: 200 `{text}` zgodne (`S:140`), ciało żądania ignorowane, więc 400 `invalid_request` i 404 nieznanego id niemodelowane; `S` sam sprawdza wysłane id (`S:551-563`) |

Kopie wartości API wpisane ręcznie:
- w `trigger.sh`: ścieżki (`:137`, `:239`, `:257`, `:318`), kody HTTP, `source_not_configured`, 6 statusów, każde pole jq, klucz `runs`, regex UUID;
- w `test.sh`: kody błędów `pipeline_busy`, `source_not_configured`, `not_authenticated`, `internal_error`, `not_found` (`S:86,117,141,151,169`), kształt 202, 16 pól z wartościami, lista pól starego API (`S:186`), statusy i rodzaje w przypadkach testowych.

Środowisko testu:
- Atrapa to Python w kontenerze `python:3.14-alpine`; kod i przypadek przekazuje env, bez montowania (`S:209-212`). Kontener nie widzi plików repo.
- `S` działa na hoście, więc może czytać pliki repo i przekazać je przez env (jak `STUB_CASE`) albo `-v`.
- Obraz crona budowany jest z kontekstu `tools/pipeline-trigger` (`S:336`) i kopiuje tylko `trigger.sh`. Samo `trigger.sh` w działaniu nie potrzebuje rejestru.

#### 3.2 `deploy-gate`

| Zależność | Źródło w API | Przypięte przez | Stub zgodny? |
|---|---|---|---|
| `GET /api/deploy-safety`: `.safe` boolean, `.reason` niepusty tekst, tylko logowany (`wait-safe.sh:91-95`) | `DeploySafetyController:50-86`, `DeploySafetyReason:13` (package-private) | `DeploySafetyIT:76,83,90` ściśle | tak (`G:436-438`) |
| 401/404 → przepuść; 000/5xx → ponów; inne → exit 1 (`wait-safe.sh:89,124,128,139`) | 503 `{"status":"unavailable"}` (`DeploySafetyController:56`) | `DeploySafetyIT:221-223` | tak (`G:439`) |
| `/api/ready`: tylko kod statusu (`verify-apply.sh:202,311-313`) | `HealthController:43-53` `{"status":"ready"}` | `HealthIT:36-37,68-69` | ciało nie (`G:610`: `{"status":"ok"}`), dziś nieszkodliwe |

`web-url.sh` (stałe domeny Railway) i `lib.sh` (ogólne `http_get`) nie zależą od kształtu API. Stub CLI `railway` (`G:510-580`) i `tools/deploy-gate/fixtures/*.json` (plany `railway config plan --json`) dotyczą Railway, nie API, więc są poza zakresem S-43.

#### 3.3 Wzorce rejestru i CI

- **Tabela wspólna dla Javy i powłoki:** `tools/healthchecks/fixtures/ping-urls.txt`. Jej nagłówek (`:1-9`) wymienia, kto ją czyta. `AlertSettingsTest:34` czyta ją przez `Path.of("../tools/healthchecks/fixtures/ping-urls.txt")`, ten sam mechanizm co `ContractFiles:23`. `tools/healthchecks/test.sh:442-446` czyta tę samą tabelę, wyciąga sedem literał `PING_PATTERN` z `AlertSettings.java` (`:445`, `:475`) i porównuje bloki znaczników w obu cronach (`:447-465`).
- **CI** (`.github/workflows/ci.yml`):
  - każdy job ma pełny `actions/checkout@v4`;
  - `backend` działa w `szczur-radar-api` (`:25`, `:27`) i czyta `../tools/` oraz `../szczur-radar-web/`;
  - `pipeline-trigger-test` (`:217-219`) potrzebuje Dockera i openssl, `deploy-gate-test` (`:232-234`) bash, jq, curl i python3; żaden z nich nie ma kroku setup-node ani setup-python.
- **Runnery self-hosted** (rezerwa): `tools/ci-runner/setup.sh:28` instaluje docker, git, jq i curl, a python3 nie jest tam nazwany wprost. Niezweryfikowane, czy obraz Ubuntu 24.04 go ma; `deploy-gate-test` już dziś go wymaga.

## Code References

- `szczur-radar-web/dev/mock-api.js:1-91` — nagłówek: tryby, procedura `playwright-cli run-code`
- `szczur-radar-web/dev/mock-api.js:93-399` — buildery danych, stałe trybów (`:374-379`), instalacja `page.route`
- `szczur-radar-web/dev/mock-api.js:399-505` — handler; `:420` źródła, `:430` start 409, `:455` stronicowanie nieudanych, `:457-467` restore, `:476` limit strony, `:504` catch-all 404
- `szczur-radar-web/src/contract/rules.test.ts:2`, `:82-84`, `:255-276` — jedyne dzisiejsze kontrole atrapy (skan tekstu)
- `szczur-radar-web/src/contract/shapes.test.ts:8-20` — próbki = pliki (równość wartości)
- `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractFiles.java:23-79` — porównanie i maskowanie
- `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractRegistry.java:63-95` — `NOT_READ_BY_SPA`, `SHAPES`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractRoutesIT.java:24-41` — tylko `/api/`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractVariantsTest.java:54-151`, `:304-314` — warianty, `KNOWN_GAPS`, porównanie kluczy z rekordem
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunController.java:82-161` — trasy crona, `StartedRun`, `NightlySummary`, `RunState`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java:235`, `:251`, `:262-263`, `:350-384` — przypięte kształty crona
- `tools/pipeline-trigger/trigger.sh:206-228`, `:238-349` — `classify`, `progress`, odczyty jq
- `tools/pipeline-trigger/test.sh:76-186`, `:209-212`, `:336` — atrapa API w Pythonie, kontener, kontekst obrazu
- `tools/deploy-gate/wait-safe.sh:89-139`, `tools/deploy-gate/verify-apply.sh:202`, `:311-313` — odczyty API bramki
- `tools/deploy-gate/test.sh:436-439`, `:610` — stuby `deploy-safety` i `ready`
- `tools/healthchecks/fixtures/ping-urls.txt:1-9`, `tools/healthchecks/test.sh:442-475`, `AlertSettingsTest:34` — wzorzec wspólnej tabeli

## Architecture Insights

- **Klucze atrapy SPA są dziś zgodne, ale tylko dlatego, że ktoś je ręcznie dopasował.** Nic tego nie pilnuje (X4). Rozbieżności są w zachowaniu tras (statusy, walidacja, stronicowanie, sesja) i w danych (źródła, warianty null). Test samych kluczy zaczerwieni się na X4, ale nie złapie żadnej z 12 rozbieżności z §1.3. Te wymagają poprawek atrapy, a do ich utrzymania testu zachowania tras.
- **Repo ma dwa wzorce wiązania kopii:**
  - Plik prawdy porównywany po obu stronach (pliki kontraktu, `rules.json`, `ping-urls.txt`). Nadaje się do kształtów i zbiorów wartości.
  - Skan tekstu kopii (`rules.test.ts` na atrapie, `healthchecks/test.sh` na `PING_PATTERN`). Jest tani, ale widzi tylko formy, które zna.
- **Kontrakt `/internal/**` i `/api/deploy-safety` nie ma domu.** Rejestr SPA celowo filtruje do tras czytanych przez SPA (`ContractRoutesIT:41`, `ContractVariantsTest:75-93`, etykiety w `rules.test.ts:86-96`). Plik kontraktu crona w `web/src/contract/` złamałby zasadę „pliki tego, co czyta SPA”, a wymagałby zmian w trzech strażnikach. Precedens `ping-urls.txt` pokazuje drugą drogę: plik w `tools/<narzędzie>/fixtures/` czytany przez IT Javy przez `../tools/…`.
- **Atrapa SPA żyje poza typami i poza `src/`.** Każdy mechanizm „atrapa czyta pliki kontraktu” musi działać w kontekście `playwright-cli run-code`. Niezweryfikowane, czy taki skrypt może czytać pliki albo importować moduły. Dzisiejszy plik nie importuje niczego.

## Historical Context (from prior changes)

Audyt `context/changes/post-m3-verification-audit/research.md` (2026-11-01, `2ca80c5`):
- **„7 rozbieżności”** (`:153-163`): potwierdzone, wszystkie nadal istnieją (§1.3). Doszło 5 nowych.
- **„Mock pilnuje tylko skan literałów kodów błędów”** (`:165`): częściowo nieaktualne. Od S-44 jest też skan literału limitu strony (`rules.test.ts:82-84`). Kształtu nadal nie pilnuje nic.
- **„atrapa `pipeline-trigger` dziś zgodna, nic jej nie wiąże”** (`:167-174`): potwierdzone. Anchory w IT przesunęły się (`:215` → `:235`, `:231` → `:251`).
- **„`assessTotal: 251` także w kroku `fetch`”** (`:173`): częściowo. Wartość 251 jest wysyłana dla każdego stanu poza `queued`, ale atrapa nigdy nie wysyła kroku `fetch`.
- **„stuby deploy-gate nieczytane”** (`:308`): rozstrzygnięte w §3.2.

S-24 `context/archive/2026-10-29-api-contract-guard/plan.md:51` (K5): atrapa celowo bez typów i wspólnych danych. S-43 jest miejscem, gdzie ta decyzja może się zmienić.

S-41 `context/archive/2026-11-08-api-contract-shapes-round-two/plan.md:58`, `:62`, `:203`: atrapy i `/internal/**` przekazane do S-43.

Roadmapa `context/foundation/roadmap.md:391` i `verification-scope.md`: M-5 postawi testy E2E (Playwright) na tej atrapie, więc atrapa ma odpowiadać jak API, także dla logowania i strażnika sesji.

## Related Research

- `context/changes/post-m3-verification-audit/research.md` — audyt §3, eksperyment X4, „Materiał dla M-4” p. 4
- `context/archive/2026-10-29-api-contract-guard/research.md` — pierwszy przegląd atrapy (kopie reguł, `internal_error`)
- `context/archive/2026-11-08-api-contract-shapes-round-two/` — pliki kontraktu drugiej rundy

## Open Questions

Wybory dla `/10x-plan` (fakty wyżej, decyzja należy do planu i użytkownika):

1. **Sposób powiązania atrapy SPA z kontraktem.** Obie opcje zachowują stany do zrzutów ekranu:
   - **(a) Test Vitest wywołuje atrapę z `?raw`** z atrapą obiektów `page`/`route`, odpytuje każdą trasę z `SHAPES` i porównuje ciało z plikiem kontraktu co do kształtu. Potrzebna jest nowa funkcja porównania kształtu i trzeba zdecydować o wariantach null, bo plik ich nie opisuje (§2.3).
   - **(b) Atrapa buduje warianty na bazie plików kontraktu.** Wymaga sprawdzenia, czy `playwright-cli run-code` potrafi czytać pliki; dziś niezweryfikowane.

   Odpowiadanie wyłącznie z plików odpada przez §1.5.
2. **Czy test ma sprawdzać także zachowanie tras** (statusy dla logowania, 202 startu, 400 dla id bez UUID, `size` > 100, `page` < 0, 401 w trybie anonimowym, 403 dla roli `user`), czy tylko kształt ciał. Bez tego 12 rozbieżności z §1.3 może wrócić po poprawce.
3. **Identyfikatory atrapy:** przejście na UUID (zgodnie z API, ale adresy z nagłówka atrapy, np. `/admin/runs/r-running`, `/posts/feed-5`, przestaną działać) albo zostawienie czytelnych id i nazwanie tego jako znanej różnicy.
4. **Zakres poprawek semantycznych:** `violations: []`, czas wyniku źródła po końcu `fetch`, `/raw` Wykopu dla Reddita, `assessTotal` w atrapie crona. Wynikają z „odpowiadają tak jak prawdziwe API”, ale nie są kształtem.
5. **Dom kontraktu tras crona i bramki:** plik w `tools/<narzędzie>/fixtures/` (precedens `ping-urls.txt`) albo nowa sekcja w rejestrze web. Do tego jak `trigger.sh`/`test.sh` mają czytać `runStatus` (np. jq na `rules.json` na hoście wobec `classify`) i czy powody `deploy-safety` trafiają do `rules.json`, co wymaga wyjątku od etykiet w `rules.test.ts:86-96`.
6. **Niezweryfikowane fakty, które plan może potrzebować sprawdzić:**
   - czy restore z id bez UUID daje 400 (wniosek z handlera);
   - czy API kiedykolwiek zapisuje próbę z pustą listą naruszeń;
   - czy python3 jest na runnerach self-hosted (rezerwa);
   - czy `playwright-cli run-code` może czytać pliki (tylko dla opcji 1b).
