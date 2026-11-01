---
date: 2026-11-01T11:14:39+0100
researcher: kwmacioszek (Claude Opus 5.5)
git_commit: 2ca80c5
branch: docs/post-m3-verification-audit
repository: kwmacioszek/szczur-radar
topic: "Audyt weryfikacji po M-3: czy testy i strażnicy łapią błędy, na które się powołujemy"
tags: [research, audit, verification, testing, guards, spa-api-contract, mock-api, pipeline, feed, m-4]
status: complete
last_updated: 2026-11-01
last_updated_by: kwmacioszek (Claude Opus 5.5)
---

# Research: audyt weryfikacji po M-3

**Date**: 2026-11-01T11:14:39+0100
**Researcher**: kwmacioszek (Claude Opus 5.5)
**Git Commit**: 2ca80c5 (`main` po PR #136; kod = `ad09e42`, S-28, ostatni commit kodu M-3)
**Branch**: docs/post-m3-verification-audit
**Repository**: kwmacioszek/szczur-radar

Ścieżki: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `test/` = `szczur-radar-api/src/test/java/pl/szczurradar/api/`, `web/` = `szczur-radar-web/`, `mig/` = `szczur-radar-api/src/main/resources/db/migration/`.

## Research Question

Czy obecne testy i strażnicy faktycznie łapią błędy, na które powołują się destylacja domeny (`context/domain/domain-distillation.md`) i mapa projektu (`context/map/repo-map.md`)? Zielony test mówi, że kod robi to, co test sprawdza, a nie że test łapie błąd (`context/foundation/verification-scope.md:7`). Zakres uzgodniony z użytkownikiem 2026-11-01:

1. strażnicy R-17 (`ClosedSetsIT`), R-22 („wymaga uwagi”), R-30 (`AuthorizationCompletenessIT`), R-43 (`SpaRulesTest`, `rules.test.ts`, `shapes.test.ts`): co sprawdzają i jaki dryf przepuszczą;
2. kształty JSON bez strażnika kontraktu (User, ActivationLookup, powiadomienia, SourceState, MissingDays, `Page<T>`);
3. zgodność `web/dev/mock-api.js` i atrapy API w `tools/pipeline-trigger/test.sh` z prawdziwym API;
4. feed bez testów `*Test` w Javie: co pokrywają testy integracyjne;
5. R-23: czy coś pilnuje, że każdy koniec przebiegu woła `RunNotifications.runEnded`;
6. D-15 (`PostStore`) i R-41 (seria odmów dostawcy): czy istnieją testy, które by to złapały.

Wynik ma być materiałem do wycinków M-4, nie planem.

## Summary

**Odpowiedź: częściowo.** Każdy z czterech strażników z punktu 1 robi to, co deklaruje, na zbiorze, który sam zna. Każdy ma jednak krawędź utrzymywaną ręcznie i dryf za tą krawędzią przechodzi przez niego bez czerwieni. Sześć eksperymentów z celowym zepsuciem (sekcja „Eksperymenty”) to potwierdza:

- dwa zepsucia schematu i konwertera przeszły przez `ClosedSetsIT` (5/5 zielone), a złapały je dopiero testy zachowania przebiegów;
- trzy zepsucia kontraktu SPA przeszły przez wszystkie 513 testów Vitest w 28 plikach i przez `vue-tsc -b`.

Poza strażnikami audyt znalazł trzy luki w zachowaniu, których nie łapie żaden test znaleziony w tym audycie:

- **Strona feedu, której przesunięcie (`page × size`) przekracza `Integer.MAX_VALUE`,** daje nieobsłużony `InvalidDataAccessApiUsageException` zamiast 400 (eksperyment X6; przez `/error` to 500 `internal_error`, wniosek z `api/config/ApiErrorController`).
- **Nieudane posty bez sygnału** (R-41, rozszerzone): przebieg `partial` z postami `failed`, ale bez `dead`, błędów technicznych, nieudanych lub pustych źródeł i `skipped`, nie ma powodu „wymaga uwagi” (`api/pipeline/run/PipelineRun.java:321-345`). Dla postów bez wcześniejszych prób, ocenianych raz na noc, seria odmów dostawcy jest więc cicha przez dwie noce, a w trzeciej te posty stają się nieodwracalnie `dead`.
- **Cicha utrata posta przy zapisie** (D-15): mechanika potwierdzona; test jednostkowy `test/pipeline/step/PostStoreTest.java:84-92` utrwala połykanie wyjątku na podstawie samego komunikatu.

Kształty z punktu 2 nie są „bez strażnika” w Javie: mają ścisłe literały w testach integracyjnych. Nie mają natomiast żadnego powiązania z typami TS, więc dryf po stronie SPA nie jest wykrywany wcale. `dev/mock-api.js` ma 7 rozbieżności z API i pilnuje go tylko skan kodów błędów. Atrapa w `tools/pipeline-trigger/test.sh` jest dziś zgodna, ale nic jej nie wiąże z API.

R-23 nie ma dziś ścieżki bez `runEnded`, lecz nie ma też strażnika strukturalnego, który zaczerwieniłby się na nowej ścieżce. Feed ma dobre testy integracyjne na kształt i walidację. Słabo pokryte są porządek przy remisach, kombinacje filtra na jednym celu i wybór najnowszej oceny `ok`.

Wniosek dla M-4: najtańsze i najbardziej wiarygodne wzmocnienie to automatyczne wyprowadzanie zbiorów, które strażnicy dziś trzymają w ręcznych listach (konwertery, trasy admina, kształty odpowiedzi, kody błędów w SPA), plus wiązanie atrap z plikami kontraktu. Pozostałe kandydaty są w sekcji „Materiał dla M-4”.

## Eksperymenty z celowym zepsuciem

Środowisko:
- jednorazowy worktree w scratchpadzie, odłączony na `2ca80c5`, usunięty po eksperymentach; nic nie scommitowano;
- testy integracyjne z atrapowymi `LLM_*` (bez klucza), Testcontainers Postgres 18;
- Vitest i `vue-tsc -b` na `node_modules` głównego checkoutu.

X3–X5 zastosowano razem. Zielony wynik całości znaczy, że żadne z trzech zepsuć nie zostało złapane.

| # | Zepsucie (minimalna zmiana) | Oczekiwany strażnik | Wynik |
|---|---|---|---|
| X1 | `api/persistence/converters/RunKindConverter.java:12`: `RunKind::value` → `RunKind::name` (zapis `RETRY` zamiast `retry`) | `ClosedSetsIT` | `ClosedSetsIT` 5/5 zielone. `PipelineRunControllerIT` 14/14 błędów w `setUp:137` (`ck_pipeline_runs_kind`). Strażnik nie widzi funkcji zapisu konwertera, łapie to test zachowania. |
| X2 | nowa migracja `V14__break.sql`: `ck_pipeline_runs_interruption_status` z `status = 'interupted'` | `ClosedSetsIT` | `ClosedSetsIT` 5/5 zielone. Czerwone: `PipelineStartupRecoveryIT` 4/4, `OrphanedRunIT` 3/10, `PipelineRunControllerIT` 1/14. Złożony `CHECK` z literałem enuma jest poza strażnikiem, łapią go testy zachowania. |
| X3 | usunięcie `attentionReasons` z `web/src/contract/run.json` i `web/src/contract/samples.ts:20` (stan po zmianie nazwy pola w Javie i regeneracji pliku) | `shapes.test.ts` + `vue-tsc` | zielone: Vitest 513/513, `vue-tsc -b` exit 0. Pole jest opcjonalne w TS (`web/src/api/pipeline.ts:71`). |
| X4 | `web/dev/mock-api.js:416`: `configured` → `enabled` | `rules.test.ts` (skan mocka) | zielone |
| X5 | `web/src/api/pipeline.ts`, w `startErrorMessage` przed `switch`: `if (err.detail === 'pipeline_bussy') return 'x'` | `rules.test.ts` (skan kodów błędów) | zielone, także `vue-tsc -b` |
| X6 | test ad hoc w `PostsApiIT`: `GET /api/posts?page=21474837&size=100`, także z `&q=xtb`; oczekiwane 400 | — (luka zachowania) | czerwony na pierwszym zapytaniu (bez filtra): `ServletException` → `InvalidDataAccessApiUsageException: Page offset exceeds Integer.MAX_VALUE (2147483647)`. Wyjątek nie jest mapowany na 400. Wariant z `q` (zapytanie natywne) nie jest rozstrzygnięty: pętla zatrzymała się na pierwszej asercji, choć log ma dwa takie wyjątki. |

## Detailed Findings

### 1. Strażnicy reguł

**R-17 `test/persistence/ClosedSetsIT.java` (IT).**
- Ręczny rejestr 24 kolumn ze zbiorem wartości (`:77-114`) jest sprawdzany z dwóch stron:
  - każdy atrybut enum encji (metamodel JPA, `:180-201`, `holdsAnEnum` `:312`) ma wpis;
  - każdy `CHECK` z listą wartości w schemacie `public` ma wpis (`:204-223`);
  - zbiory są równe w obie strony (`:153-177`);
  - literałowy `CHECK`, który nie jest listą, musi być w `NOT_A_VALUE_LIST` (`:122-126`, `:226-242`).
- Łapie (wg lektury, nie uruchamiane): nową wartość enuma bez migracji, nowy atrybut enum spoza rejestru, `CHECK` z nadmiarową wartością.
- Przepuszcza, potwierdzone eksperymentem:
  - konwerter zapisujący co innego niż rejestr (X1; rejestr liczy wartości własną lambdą, `:328-332`);
  - złożony `CHECK` z literałem enuma (X2; pomijany po nazwie).
- Przepuszcza, wg lektury: zbiór zamknięty trzymany jako `String`/`List<String>` bez `CHECK` oraz nową kolumnę `TEXT[]` bez `CHECK`.
- Testy zachowania przebiegów złapały X1 i X2, więc ochrona istnieje, tylko nie tam, gdzie wskazuje destylacja (R-17 „enforced” przez `ClosedSetsIT`).

**R-22 „wymaga uwagi”.**
- `SpaRulesTest:94,127` porównuje `values.attentionReason` w `web/src/contract/rules.json` z `AttentionReason` w kolejności deklaracji (unit).
- `web/src/contract/rules.test.ts:134-138` sprawdza, że każdy kod ma tekst inny niż sam kod.
- Nowa stała w Javie bez tekstu w SPA czerwieni ten łańcuch, tak samo zmiana nazwy kodu.
- Powiadomienie i strona przebiegu liczą „wymaga uwagi” jedną metodą `needsAttention()` (`RunNotifications.java:105`, `PipelineRunController.java:255`).
- Luki:
  - SPA kopiuje warunek powodu `skipped`: `hasSkipped` (`web/src/runs.ts:80-82`: `succeeded`/`partial` i `skipped > 0`) powtarza warunek z `PipelineRun.java:340`. Do tego `runTone` (`web/src/runs.ts:38-41`) daje ton z `skipped > 0`. Nic nie skanuje SPA pod kątem liczenia powodów. Pokrywa to tylko przykład w `RunDetailView.test.ts:297-319` (wg raportu badacza, nieczytane).
  - Unie TS i `satisfies`/`never` (`rules.test.ts:194-208`, `runs.ts:121-125`) sprawdza tylko `vue-tsc -b`. W CI `vue-tsc -b` uruchamia się wyłącznie w buildzie obrazu web (`web/Dockerfile:11`, `web/package.json:8`, `.github/workflows/ci.yml:84`), a `npm test` (`ci.yml:79`) typów nie sprawdza.

**R-30 `test/auth/AuthorizationCompletenessIT.java` (IT).**
- Trasy są wyliczane automatycznie z `RequestMappingHandlerMapping` (`:123-139`). Zmienne ścieżki dostają atrapy (`:150-178`). Każda trasa spoza `PUBLIC_ROUTES` bez sesji ma dać dokładnie 401 (`:95-117`). Permit bez wpisu w `PUBLIC_ROUTES` jest więc czerwony.
- 403 dla zwykłego użytkownika sprawdza `test/auth/AdminRoleIT.java:64-78`, ale tylko dla ręcznej listy `ADMIN_ROUTES` z 10 tras (`AuthorizationCompletenessIT.java:63-73`).
- Nic nie wymusza, żeby każda zmapowana trasa `/api/admin/**` była na tej liście. Ochrona nowej trasy admina zależy od wzorca w `api/config/SecurityConfig.java:106`, a jej 403 nie jest testowane.
- `/internal/**` bez filtra tokenu nadal daje 401 bez sesji. Łapie to `InternalPipelineRunIT:143-176`, ale tylko dla dwóch zakodowanych URI.
- Wg lektury, nie uruchamiane: `RouterFunction` i handlery zasobów nie są wyliczane.

**R-43 reguły SPA ↔ API.**
- Automatyczne:
  - zbiór białych znaków liczony po wszystkich code pointach (`SpaRulesTest:104`);
  - `errorCodes` = `ErrorCode` (`SpaRulesTest:133`).
- Ręczne:
  - mapa 6 limitów (`SpaRulesTest:59-65`) i 18 enumów (`:75-96`);
  - mapa 6 stałych SPA (`rules.test.ts:62-79`).
- Skan kodów błędów w SPA, jej testach i mocku (`rules.test.ts:222-249`) rozpoznaje trzy formy: `detail: '<code>'`, `new ApiError(<status>, '<code>')` oraz `case '<code>'` w pliku z `.detail)`. Porównanie `err.detail === '<kod>'` przechodzi (X5).
- Niezarejestrowane kopie limitu strony:
  - `PAGE_SIZE = 50` w `web/src/views/FeedView.vue:15` wobec literału `@Max(100)` w `api/posts/api/PostController.java:87`;
  - literał `'100'` w `FeedFilter.test.ts:33` (wg raportu badacza).
- Enumy spoza rejestru `rules.json` (zgodne dziś, niepilnowane): `Role`, `NotificationKind`, `PostStage`, `Provenance.Source`, `Document`/`Field`/`Assertiveness`.

### 2. Kształty JSON

**Istniejący strażnik kontraktu.**
- `test/contract/ContractFiles.java:44-54` porównuje całe drzewo JSON z plikiem: klucze, typy węzłów, null wobec wartości, kolejność w tablicach. UUID i instanty są maskowane (`:57-79`).
- Każdy plik to jeden zasiany przypadek:
  - `run.json`: tylko `GET /{id}`, `PipelineRunControllerIT:449`;
  - `post.json`: `PostsApiIT:215`;
  - `failed-post.json`: tylko `items[0]`, bez koperty strony, `PostsApiIT:234`;
  - `freshness.json`: `FreshnessIT:218`.
- Po stronie SPA `shapes.test.ts:12-24` (`toEqual`) i adnotacje typów w `samples.ts`.
- Przepuszcza:
  - pole, które staje się nullable (seed ma jedną wartość);
  - pole opcjonalne w TS (X3);
  - warianty spoza seeda;
  - puste tablice (`modifiers: []`, `flags: []` w `samples.ts`), które nie pilnują kształtu elementu.

**Kształty bez powiązania z TS.**

| Kształt | Producent | Typ TS | Test ciała (Java) |
|---|---|---|---|
| User | `api/auth/UserResponse.java:9` | `web/src/api/auth.ts:4-8` | `AuthIT:102,287`, ścisły |
| ActivationLookup | `api/auth/activation/ActivationController.java:45,73` | `web/src/api/activation.ts:11-14` | `ActivationIT:72,206`, ścisły |
| Powiadomienia | `api/notifications/api/NotificationController.java:96,105,123` | `web/src/api/notifications.ts:9-39` | `NotificationsIT:169-187` (`containsOnlyKeys`, jeden element, `run: null` nietestowane), `:192` ścisły tylko dla pustej listy |
| SourceState | `api/pipeline/step/SourceIngest.java:72` | `web/src/api/pipeline.ts:111-114` | `PipelineRunControllerIT:161`, `PipelineRunWithoutSourceIT:80`, ścisły |
| MissingDays | `api/pipeline/api/PipelineRunController.java:154,209,219` | `web/src/api/pipeline.ts:150-162` | `MissingDaysIT:248`, ścisły |
| `Page<T>` | `PostController:131`, `FailedPostController:60`, `PipelineRunController:205` | `web/src/api/posts.ts:90-95` | posty: ścieżki `$.total`/`$.size`/`$.page`, nadmiarowy klucz przejdzie; `RunPage`: koperta nieasertowana (`PipelineRunControllerIT:309-332` tylko `$.items…`) |

Ścieżki producentów `UserResponse`, `ActivationController` i `NotificationController` pochodzą z raportu badacza (sprawdzone grepem nazw plików, nie linia po linii). Zmiana w Javie z poprawionym literałem testu zostawia TS nieaktualny.

Dobrze pokryte są ciało błędu `{"detail":code}` (ścisłe literały w wielu IT i `errorCodes`) oraz `/api/deploy-safety` (`DeploySafetyIT:200`). Konsument `/api/deploy-safety`, `tools/deploy-gate/wait-safe.sh`, ma jednak własne stuby, niepowiązane z API.

### 3. Atrapy API

**`web/dev/mock-api.js` wobec API**, wg raportu badacza. Zgodne są kształty User, Lookup, listy powiadomień, RunView, FailedPost, PostItem, Freshness, MissingDays i koperty stron. Rozbieżności:

| # | Mock | API |
|---|---|---|
| 1 | `:416` źródła: tylko `[{wykop}]` (sprawdzone) | każde zarejestrowane źródło, także `reddit` (`SourceIngest.java:63-69`) |
| 2 | `:426` start przebiegu zawsze 409 `pipeline_busy` (sprawdzone) | 202 `List<RunView>`; ścieżka sukcesu nie jest zamockowana |
| 3 | `/api/auth/login` niezamockowane, spada do 404 `not_found` | 200 / 401 `invalid_credentials` / 429 (`AuthController:85-112`) |
| 4 | id `u1`, `r-*`, `n-*`, `p1` | UUID; id bez UUID to 400 `invalid_request`, w mocku 404 |
| 5 | lista nieudanych postów ignoruje `page`/`size` | API odsyła żądane wartości (`FailedPostController:51-58`) |
| 6 | `schema_violation`/`fabricated_evidence` z `violations: []` | prawdziwa próba niesie naruszenia (semantyka, nie kształt) |
| 7 | brak walidacji `size` ≤ 100 | 400 |

Mock pilnuje tylko skan literałów kodów błędów (`rules.test.ts:222-249`). Zmiana nazwy pola przechodzi (X4).

**`tools/pipeline-trigger/test.sh`**, wg raportu badacza.
- Atrapa w Pythonie (`:76-160`) jest dziś zgodna z `api/pipeline/api/InternalPipelineRunController.java`:
  - 202 `[{id,kind}]` = ścisłe ciało w `InternalPipelineRunIT:215`;
  - 16 pól stanu = `containsOnlyKeys` w `InternalPipelineRunIT:231`;
  - kody 409/503/401/404.
- `classify` (`tools/pipeline-trigger/trigger.sh:206-214`) mapuje wszystkie 6 statusów.
- Drobna różnica: atrapa domyślnie wysyła `assessTotal: 251` także w kroku `fetch`; API wysyła wtedy null.
- Nic nie wiąże atrapy ani `classify` z `RunState`/`RunStatus`. Zmiana nazwy pola albo nowy status czerwieni IT, ale po jego poprawce `test.sh` zostaje zielony, a cron na produkcji czyta pusty status.

### 4. Feed

Macierz z raportu badacza; kotwice w `test/posts/api/PostsApiIT.java` i `web/src/`. Mocne są:
- walidacja parametrów: `:159` (size 0/101, page −1/x → 400, ścisłe ciało) i `:405` (q > 100, NUL, zły kind/direction → 400);
- szczegół posta: szczegół = element listy (`:169`, całe drzewa JSON), kształt przez `post.json`, 404 i 400 (`:238`, `:256`);
- etykieta pochodzenia, świeżość (`FreshnessIT`), 401 bez sesji i lista nieudanych postów tylko dla admina.

Słabe albo brak:

| Zachowanie | Stan | Kotwica |
|---|---|---|
| porządek przy remisie `published_at` (`p.id DESC`) z filtrem | brak testu; bez filtra tylko 2 remisy, usunięcie `id DESC` wykrywane losowo | `api/posts/PostRepository.java:121`, `PostsApiIT:126` |
| `kind` i `direction` na tym samym celu | brak (posty w seedach mają po 1 celu); warunek jest dziś w jednym `EXISTS` (sprawdzone) | `PostRepository.java:114-118` |
| najnowsza ocena `ok` przy późniejszej `failed` | brak; każdy seed ma `failed` przed `ok` | `PostController.java:92`, `PostRepository.java:113` |
| strona za końcem (`items: []`, prawdziwy `total`) | brak po stronie API; SPA na tym polega | `web/src/views/FeedView.vue:203` (wg raportu) |
| `direction` przy stanowisku hedged | brak w API | `PostsApiIT:328-333` |
| `not_investment_related` w feedzie bez filtra | brak (żaden seed) | — |
| `page` z przesunięciem ponad `Integer.MAX_VALUE` | **defekt**, X6 | `PostController.java:86` (`@Min(0)` bez górnej granicy) |

Ten sam wzorzec `@Min(0)` bez górnej granicy mają `api/posts/api/FailedPostController.java:49` i `api/pipeline/api/PipelineRunController.java:113`. Dla nich nie uruchamiano eksperymentu, więc wynik nie jest ustalony.

Dodatkowo, wg raportu badacza, nieuruchamiane: `web/src/feedFilter.ts` nie usuwa U+0000. Link `/?q=a%00b` dostaje 400 i feed zostaje w stanie błędu przy każdej ponownej próbie, wbrew komentarzowi w `feedFilter.ts:1-2`.

### 5. R-23: każdy koniec przebiegu powiadamia

- Status końcowy zapisuje tylko `api/pipeline/state/RunRecorder.java`, przez sześć metod: `finish` `:79`, `fail` `:96`, `interrupt` `:247`, `stall` `:269`, `interruptAll` `:289` i `interruptOrphans` `:325`. Encja `PipelineRun` nie ma settera statusu.
- Ścieżki końca (sześć; wiersz `execute` obejmuje dwie) i ich wywołania `runEnded`, wg raportu badacza:

| Ścieżka | Wywołanie `runEnded` | Test powiadomienia |
|---|---|---|
| `execute` (koniec i `close`) | `PipelineRunService.java:386` | `PipelineRunServiceTest:377-401`, `PipelineRunIT:364,394,756` |
| watchdog | `:480` | `PipelineRunServiceTest:589-640` |
| shutdown | `:551` | `PipelineRunServiceTest:516,548`, `PipelineRunIT:449` |
| sieroty `lost` | `:270` | `OrphanedRunIT:189,315` |
| odzyskiwanie po starcie | `PipelineStartupRecovery.java:113` | `PipelineStartupRecoveryIT:99-111` |

- Dziś nie znaleziono ścieżki bez `runEnded`. Nie ma jednak strażnika strukturalnego: ani ArchUnit, ani testu przechodzącego po wszystkich ścieżkach. Nowe wywołanie `recorder.interrupt/fail/finish` poza tymi miejscami (np. przyszłe „anuluj przebieg”) nie czerwieni żadnego testu.
- Wzorzec do naśladowania już istnieje: `test/pipeline/state/PostColumnsIT.java:178-192` refleksją wymaga scenariusza dla każdej metody `PostTransitions`.

### 6. D-15 i R-41

**D-15: cicha utrata posta.**
- `api/pipeline/step/PostStore.java:68-79` (sprawdzone): każdy `DataIntegrityViolationException` przy zapisie pojedynczego posta jest połykany jako „stored meanwhile by another run”, bez logu i licznika.
- `fetched` i `skipped` liczy `SourceIngest` przed zapisem, a `new_posts` to `newPostIds.size()`. Utracony post jest więc w `fetched`, ale nie w `skipped` ani w `new_posts`, i nie daje powodu „wymaga uwagi”.
- Test jednostkowy `test/pipeline/step/PostStoreTest.java:84-92` (sprawdzone) utrwala to zachowanie wyjątkiem z komunikatem `"uq_posts_source_item"`. Wyjątek z komunikatem innego ograniczenia przeszedłby tak samo.
- Nie znaleziono IT `PostStore` z postem łamiącym coś innego niż klucz unikalny.
- Realna droga wg raportu badacza, nieuruchamiana: subreddit z U+0000 przechodzi przez `RedditMapper`, po czyszczeniu NUL w `Post.java:141` zostaje `''` i łamie `ck_posts_channel` (`mig/V13__posts_channel.sql:13`). Na prawdziwych danych Arctic Shift to mało prawdopodobne. Ryzyko leży raczej w przyszłych `CHECK`/`NOT NULL` i w nowych źródłach.

**R-41: seria odmów dostawcy.**
- Mechanika potwierdzona w `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:298-306` (sprawdzone). Zapisany wynik z co najmniej jednym wywołaniem zeruje serie awarii i błędów klienta. Odmowa (`REJECTED`, `api/llm/transport/TransportException.java:80-91`) jest zapisywana jako ocena nieudana i zużywa próbę, a trzecia daje `dead`.
- **Nowe względem destylacji (sprawdzone w `PipelineRun.java:321-345`):** w przebiegu `partial` powód powstaje tylko z `dead`, błędów technicznych, nieudanych lub pustych źródeł i `skipped`. Same nieudane próby powodu nie dają. Gdy dostawca odmawia każdemu postowi bez wcześniejszych prób, a przebieg nie ma innego powodu, seria jest więc cicha w nocy 1 i 2 (zakładając jedną ocenę posta na noc przez przebieg `RETRY`, `PipelineRunService.java:185`, wg raportu badacza): brak dzwonka, brak `RUN_NEEDS_ATTENTION`, cron kończy się kodem 0 (`tools/pipeline-trigger/trigger.sh:206-214`). W nocy 3 przychodzi `dead_posts`, a wtedy posty są już nieodwracalnie `dead`.
- Częściowa osłona: `LlmClient.CHECK_REQUEST` łapie odmowę każdego wywołania, także testowego. Nie łapie odmów specyficznych dla promptu oceny (moderacja, brak tekstu, za duży kontekst). Wg raportu badacza.
- Testy, wg raportu badacza: `ConcurrentAssessmentDispatcherTest:706-725` utrwala zerowanie serii po jednej odmowie, a `:317-332` sprawdza, że trzecia próba daje `dead`. Żaden test nie ma serii odmów na wielu postach ani przez kilka przebiegów.

## Code References

- `test/persistence/ClosedSetsIT.java:77-114` — ręczny rejestr 24 zbiorów; `:328-332` — wartości liczone własną lambdą, nie konwerterem
- `api/persistence/converters/RunKindConverter.java:12` — funkcja zapisu konwertera (X1)
- `mig/V8__pipeline_run_progress.sql:17-18` — złożony `CHECK` z literałem `'interrupted'` (X2)
- `test/auth/AuthorizationCompletenessIT.java:63-73` — ręczna lista `ADMIN_ROUTES`; `:95-117` — 401 dla każdej zmapowanej trasy
- `api/config/SecurityConfig.java:106` — wzorzec `/api/admin/**`
- `web/src/contract/rules.test.ts:222-249` — skan kodów błędów (trzy formy)
- `web/src/api/pipeline.ts:71` — opcjonalne `attentionReasons` (X3)
- `web/dev/mock-api.js:416`, `:426` — źródła tylko Wykop; start zawsze 409
- `web/src/runs.ts:80-82` — kopia warunku powodu `skipped` w SPA
- `test/contract/ContractFiles.java:44-79` — porównanie całego drzewa i maska
- `api/posts/api/PostController.java:86-87` — `@Min(0)` bez górnej granicy, `@Max(100)` jako literał
- `api/posts/PostRepository.java:110-124` — filtr na jednym celu w jednym `EXISTS`, porządek `published_at DESC, id DESC`
- `api/pipeline/step/PostStore.java:68-79` — połykanie `DataIntegrityViolationException`
- `test/pipeline/step/PostStoreTest.java:84-92` — test utrwalający połykanie
- `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:298-306` — zerowanie serii po zapisanym wyniku
- `api/pipeline/run/PipelineRun.java:321-345` — powody „wymaga uwagi”; brak powodu dla samych nieudanych prób
- `test/pipeline/state/PostColumnsIT.java:178-192` — wzorzec strażnika refleksyjnego do powtórzenia dla R-23

## Architecture Insights

- **Najsilniejszy wzorzec w repo** to ręczny rejestr sprawdzany automatycznie z dwóch stron (`ClosedSetsIT`: metamodel i `pg_constraint`). Słabnie tam, gdzie część rejestru jest kopią zamiast odczytem: wartości liczone lambdą zamiast konwerterem, `ADMIN_ROUTES` zamiast mapowania tras, mapa enumów w `SpaRulesTest`.
- **Testy zachowania łapią to, czego strażnicy nie widzą** (X1, X2). Ochrona istnieje, ale jest przypadkowa: zależy od tego, czy jakiś scenariusz akurat zapisuje daną wartość. Testy mutacyjne (PIT, `verification-scope.md:19`) zmierzą ją systematycznie.
- **Typy TS są sprawdzane w CI tylko w buildzie obrazu web.** To wystarcza dla CI, ale lokalne `npm test` ich nie widzi, a opcjonalne pola TS osłabiają kontrakt bez sygnału (X3).
- **Atrapy to ręczne bliźniaki API** (`mock-api.js`, stub w `test.sh`, stuby `deploy-gate`). Pliki kontraktu z `web/src/contract/` mogłyby być ich wspólnym źródłem prawdy.

## Historical Context (from prior changes)

Destylacja (`context/domain/domain-distillation.md`):
- **R-17, R-22, R-30, R-43 „enforced”, „treść testów nieczytana”** (`:216`, `:228`, `:255`, `:243`, `:344`): częściowo potwierdzone. Strażnicy działają na swoim zbiorze, przepuszczają dryf opisany wyżej (X1–X5).
- **D-15** (`:287`): potwierdzone. Dodatkowo test jednostkowy utrwala zachowanie, a jedna nierealistyczna droga wejścia jest znana (NUL w subreddicie).
- **R-41** (`:233`): mechanika potwierdzona. Dodatkowo nieudane próby bez `dead` nie dają powodu „wymaga uwagi”, więc seria jest cicha przez dwie noce.
- **R-23** (`:229`): potwierdzone. Dziś brak luki w wywołaniach, brak strażnika na nową ścieżkę.

Mapa projektu (`context/map/repo-map.md`):
- **„tak dla 4 z 10 kształtów; … bez strażnika”** (`:111`): częściowo. Pięć z sześciu ma ścisłe literały w IT, a koperta `RunPage` nie ma żadnej asercji. Brakuje powiązania z TS, nie testu Javy.
- **„mock-api.js: skan obejmuje tylko kody błędów; kształt unknown”** (`:117`): potwierdzone. Kształt zbadany, 7 rozbieżności.
- **„trigger.sh: zgodność atrapy unknown”** (`:116`): rozstrzygnięte. Dziś zgodna, bez powiązania.
- **„feed: 0 testów `*Test`, tylko integracyjne”** (`:178`): potwierdzone. Testy integracyjne są mocne na kształt i walidację, słabe na porządek i kombinacje filtra.

`context/changes/post-m2-audit/research.md` (kontrakt SPA ↔ API, 0 H, 6 M) dał początek S-24. Ten audyt sprawdza wynik S-24: pliki kontraktu działają na czterech kształtach z wyjątkami opisanymi w punkcie 2.

## Related Research

- `context/changes/post-m2-audit/research.md`
- `context/domain/domain-distillation.md`, `context/map/repo-map.md` (PR #136)
- `context/foundation/verification-scope.md`

## Materiał dla M-4

To kandydaci, nie podział na wycinki. Kolejność i zakres rozstrzygnie `/10x-roadmap`. Każdy kandydat ma test, który dziś byłby czerwony albo zaczerwieniłby się na znanym dryfie.

1. **Rozliczanie porażek w przebiegu** (Core/Supporting). Wymaga decyzji właściciela: czy seria odmów ma zatrzymywać przebieg bez zużycia prób i czy same nieudane próby mają dawać powód „wymaga uwagi” (destylacja Q-18). Zakres:
   - D-15: rozróżnić naruszenie klucza unikalnego od innych; inne liczyć albo kończyć błędem;
   - R-41 z nowym ustaleniem o braku sygnału.
2. **Strażnicy wyprowadzani automatycznie:**
   - `ClosedSetsIT` czyta funkcję zapisu konwertera (`convertToDatabaseColumn`) i literały złożonych `CHECK`;
   - każda zmapowana trasa `/api/admin/**` jest automatycznie w zbiorze tras admina z 403 dla zwykłej sesji;
   - wszystkie trasy `/internal/**` dają 401 bez tokenu;
   - strażnik R-23 na wzór `PostColumnsIT`.
3. **Kontrakt SPA ↔ API, druga runda:**
   - pliki kontraktu dla User, ActivationLookup, powiadomień (z `run: null`), SourceState, MissingDays i kopert `Page`/`RunPage`;
   - warianty nullable;
   - pola TS wymagane tam, gdzie API je zawsze wysyła;
   - `detail` typowany unią kodów zamiast `string`, co zamknie X5 kompilatorem;
   - limit rozmiaru strony w rejestrze;
   - enumy spoza `rules.json`.
4. **Atrapy związane z kontraktem:** `dev/mock-api.js` odpowiada z plików kontraktu albo test porównuje jego odpowiedzi z nimi (7 rozbieżności); atrapa `pipeline-trigger` i `classify` czytają statusy z rejestru.
5. **Feed:**
   - pięć testów regresji: remisy z filtrem, `kind` + `direction` na jednym celu, `ok` przed późniejszą `failed`, strona za końcem, hedged;
   - poprawki defektów: ogromny `page` (X6; do sprawdzenia `FailedPostController` i `PipelineRunController`) oraz NUL w linku.
6. **Testy mutacyjne rdzenia** (PIT). X1 i X2 pokazały, że ochronę dają dziś testy zachowania, a nie strażnicy. PIT zmierzy ją bez zgadywania.

## Open Questions

- Decyzja właściciela produktu: czy posty z serii odmów dostawcy tracą próby (Q-18 w destylacji) i czy same nieudane próby mają dawać powód „wymaga uwagi”. Założenie S-06 („sygnał przy każdym `partial` byłby co noc”) dotyczyło `dead`, nie nieudanych prób.
- Czy `FailedPostController` i `PipelineRunController` mają tę samą wadę z ogromnym `page`: nie uruchamiano.
- Czy wyjątki z `CHECK` (23514) i z NUL (22021) w batchu Hibernate 7 wychodzą jako `DataIntegrityViolationException`: wysoce prawdopodobne (X1 i X2 pokazały 23514 jako `DataIntegrityViolationException` w innych zapisach), ale nie sprawdzone dla `PostStore`.
- Nie czytano: `RunDetailView.test.ts`, `AdminView.test.ts`, `NotificationsIT` w całości, fixture'ów Vitest pisanych ręcznie (`pipeline.test.ts`, `notifications.test.ts`), stubów `tools/deploy-gate`, `RunNotifications` w środku.
