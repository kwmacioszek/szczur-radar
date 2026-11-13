---
date: 2026-11-12T08:37:11Z
researcher: Claude (Opus 5.5)
git_commit: af3046705fcb1c6eb4bc40274f00b2a77c18d389
branch: feature/pr-env-scenarios-in-ci
repository: szczur-radar
topic: "S-31: które ręczne weryfikacje PR env z S-13, S-17, S-18, S-20 i S-27 da się puścić w CI jako scenariusze na pełnym API z atrapami źródeł i LLM, na jakiej infrastrukturze testów i za jaki czas CI"
tags: [research, codebase, tests, ci, pipeline, scenarios, testcontainers, fixtures, S-31, M-4]
status: complete
last_updated: 2026-11-12
last_updated_by: Claude (Opus 5.5)
---

# Research: S-31 — ręczne weryfikacje PR env jako scenariusze w CI

**Date**: 2026-11-12T08:37:11Z
**Researcher**: Claude (Opus 5.5)
**Git Commit**: `af30467` (`main` po S-43, branch świeżo utworzony, bez własnych commitów)
**Branch**: `feature/pr-env-scenarios-in-ci`
**Repository**: szczur-radar

## Research Question

S-31 (roadmapa M-4, gwiazda przewodnia, issue #141): ręczne kroki weryfikacji PR env z wycinków S-13, S-17, S-18, S-20 i S-27 mają chodzić w CI jako scenariusze na pełnym API z Postgresem, z atrapami źródeł i LLM z prawdziwych payloadów; to, czego CI nie zobaczy, ma trafić na krótką, nazwaną listę ręczną (`context/foundation/roadmap.md:170-182`, `context/foundation/verification-scope.md:20,24-35`).

Pytania badawcze:

1. Jakie ręczne kroki PR env mają te wycinki, co każdy dowodzi i które da się odtworzyć w CI (otwarte pytanie roadmapy, `roadmap.md:178`)?
2. Jaka infrastruktura testów już istnieje (kontekst Springa, atrapy HTTP źródeł i LLM, obserwacja alertów, zegar, fixtury) i czego brakuje do scenariusza?
3. Ile dziś trwa job `backend` w CI i ile może kosztować każdy nowy scenariusz (drugie otwarte pytanie, `roadmap.md:179`)?

Poza zakresem: wybór konkretnych scenariuszy, ich kształt i decyzje produktowe — to robi `/10x-plan`.

## Summary

- **Czego brakuje: scenariusza „HTTP → przebieg naprawdę się kończy → trasy do odczytu”.** Kroki klasy A wszystkich pięciu wycinków (tabele w sekcji 1) mają wspólny łańcuch: admin startuje przebieg przez `/admin` albo `POST /api/admin/pipeline-runs`, przebieg kończy się na prawdziwym Wykopie, Arctic Shift i LLM, a agent czyta część z tych odczytów: stronę przebiegu, `missing-days`, feed, `/raw`, dzwonek, log alertu. W testach tego łańcucha w jednym kawałku nie ma.
  - Przebiegi, które kończą się ocenionymi postami, testy startują na poziomie serwisu: `PipelineRunService.startRange(...)` w `PipelineRunIT`, a wynik sprawdzają SQL-em.
  - Testy, które startują przez HTTP (`PipelineRunControllerIT`, `InternalPipelineRunIT`), mają Wykop zwracający zawsze pusty dzień. Ich przebiegi zakresu kończą się więc `failed` na `fetch` (`PipelineRunControllerIT.java:88-103`, `InternalPipelineRunIT.java:125-139`).
  - Strony przebiegu, `missing-days`, feed i `/raw` są sprawdzane na wierszach zasianych SQL-em, a nie na wyniku przebiegu: `MissingDaysIT.java:236-257`, `PipelineRunControllerIT.java:436-482`, `PostsApiIT`.
- **Klocki są już prawie wszystkie i to prawdziwe klocki.**
  - Wykop i Reddit chodzą przez lokalny serwer HTTP (`sources/LocalApiServer.java:27-122`) z prawdziwymi klientami, prawdziwym `HttpRetry`, mapperami i fixturami z prawdziwych odpowiedzi. Fixtury to dzień #gielda 2026-10-01 (12 wpisów + 4 znaleziska) i dzień r/inwestowanie 2026-10-27 (14 postów).
  - LLM ma dwie atrapy:
    - `@MockitoBean LlmClient` (7 klas IT): bez HTTP; `Assessor`, prefiltr, parser i walidator D1–D15 są prawdziwe;
    - `llm/ScriptedProviderServer.java:25-138` z prawdziwym klientem SDK z `LlmClientFactory.create(props, baseUrl, sleeper)` (`src/main/.../llm/LlmClientFactory.java:32-41`) i prawdziwym `TransportRetry`, dziś używany tylko w `LlmOutageIT`.
  - Alerty widać przez spy `OperatorAlertQueue` i linie logu. Zegar to bean `Clock`, który testy podmieniają `@Primary`.
- **Atrapa LLM „z prawdziwych payloadów” istnieje tylko częściowo.**
  - Fixtury dnia źródeł nie mają dopasowanych odpowiedzi modelu. Nagranie (`llm/recordings/`) ma 7 z 16 elementów dnia Wykopu, przez identyczne ciało wejścia w złotej próbce; reszta Wykopu i cały Reddit nie mają żadnego.
  - `measurement/Recording.response()` (`Recording.java:77-85`) umie zamienić nagranie w `LlmResponse`. Ten kod jest jednak package-private w pakiecie tylko do pomiarów.
  - Prawdziwe koperty dostawców leżą w `fixtures/deepseek|gemini|openrouter/`.
- **Klasyfikacja kroków PR env** (tabela w sekcji 1):
  - **A, do CI:** zakres kończy się z poprawnymi wynikami źródeł i krokami (S-17, S-20, S-27); awaria i cisza źródła → `partial` / `failed` (S-20); dzień brakujący i jego wypełnienie (S-13); post Reddita w feedzie i `/raw`, bez duplikatów przy powtórce (S-27); `RUN_NEEDS_ATTENTION` od przebiegu do odebranej wiadomości ntfy na lokalnym serwerze (S-18).
  - **B, częściowo:** przerwanie przez deploy w trakcie `assess` (S-17); format logu i `migrate` bez `LLM_*` (S-17); `REDDIT_SUBREDDITS` z env (S-27).
  - **C, zostaje ręczne:** dostarczenie do telefonu (Telegram, ntfy); prawdziwy SIGTERM, nakładanie się instancji i Wait for CI; prawdziwe API źródeł i LLM; harmonogram cronów; oględziny UI; czas startu na Railway; czytelność logów w Railway; przegląd dokumentów.
- **Lista ręczna z `verification-scope.md:30-35` jest za krótka i częściowo nieaktualna:**
  - od S-34 zdarzenia API idą do ntfy, a nie do Telegrama (`deploy-plan.md:1676`);
  - kroki C „oględziny UI”, „czas startu na Railway” i „przegląd runbooka i PRD” nie mieszczą się w czterech pozycjach listy.

  Kryterium „done when” M-4 („ręczna lista po deployu zawiera tylko pozycje z sekcji niżej”, `roadmap.md:93`) wymaga więc decyzji planu albo właściciela.
- **Czas CI.**
  - **Dziś:** `backend` trwa 190–225 s w 6 zielonych runach i jest ścieżką krytyczną. Testy integracyjne zajmują ok. 143–159 s: 39 klas jedna po drugiej, ok. 23 starty kontekstu Springa i 26 kontenerów Postgresa.
  - **Koszt nowej klasy z własnym kontekstem:** w zmierzonym jobie średnio ok. 2,7 s na start kontekstu i ok. 1,3 s na kontener.
  - **Koszt scenariusza:** pojedynczy przebieg na fixturach z atrapą LLM bez opóźnień trwa sekundy, co szacuję po `PipelineRunIT` (7,8 s na ~20 scenariuszy).
  - **Najwolniejsza klasa:** `RunWatchdogIT` (36,2 s), przez prawdziwe oczekiwania.
  - **Brak limitu czasu:** job `backend` nie ma `timeout-minutes`, więc GitHub domyślnie daje mu 360 min.

## Detailed Findings

### 1. Ręczne kroki PR env pięciu wycinków i ich klasyfikacja

Klasy: **A** — da się puścić w CI na pełnym API z atrapami; **B** — częściowo; **C** — zostaje ręczne. Źródła: sekcje wycinków w `context/deployment/deploy-plan.md`, plany w `context/archive/<data>-<change>/plan.md` i komentarze z dowodami w issue #72, #77, #114 i #121 (odczyt `gh api`).

#### S-13 `missed-night-detection` (`deploy-plan.md:1638-1670`, `context/archive/2026-10-24-missed-night-detection/plan.md`)

| Krok | Kotwica | Dowodzi | Opiera się na | Istniejący test | Klasa |
| --- | --- | --- | --- | --- | --- |
| Zrzuty `/admin`, sekcja „Dni bez danych”: 7 stanów, oba motywy, 375 px | plan.md:244, Progress :368; issue #72 | stany sekcji, „Wpisz do formularza” ustawia Od/Do/Źródła i fokus | przeglądarka, ocena wzrokowa | `AdminView.test.ts`, `missingDays.test.ts` (Vitest) | C (oględziny; Playwright jest w M-5, `roadmap.md:85`) |
| Przebieg D-3 → lista pokazuje D-2 → przebieg D-2 → lista pusta | plan.md:280-295, :305; Progress :378; issue #72 | zakończony `partial` przebieg zakresu pokrywa swoje dni; okno źródła zaczyna się od pierwszego pokrytego dnia; resztki kolejkują RETRY przed zakresem | prawdziwy Wykop i LLM, Railway, kliknięcie | `MissingDaysTest`, `MissingDaysIT` — tylko na zasianych wierszach (`MissingDaysIT.java:236-257`), stały zegar `:275-284` | **A** dla łańcucha API (stały `Clock`, POST D-3 → GET `missing-days` = D-2 → POST D-2 → `days: []`) |
| Akceptacja sekcji runbooka | plan.md:306, :379 | — | człowiek | — | C (przegląd dokumentu) |

Sekcja S-13 w `deploy-plan.md` nie ma podsekcji „PR-env verification”; dowód z PR env (pr-106) jest tylko w issue #72.

#### S-17 `pipeline-without-embabel` (`deploy-plan.md:1763-1847`, `context/archive/2026-10-25-pipeline-without-embabel/plan.md`)

| Krok | Kotwica | Dowodzi | Opiera się na | Istniejący test | Klasa |
| --- | --- | --- | --- | --- | --- |
| 1. Przebieg zakresu jednego dnia: `succeeded`/`partial`, każdy krok zamknięty, potem `/api/deploy-safety` `safe:true`, bez ERROR w logu | `deploy-plan.md:1791`, wynik `:1802` | silnik `RunSequence` kończy cały zakres w obrazie; strażnik deployu zwalnia po końcu | prawdziwy Wykop i LLM, Railway | `PipelineRunIT.aRangeRunFetchesStoresAndAssessesEveryPostOfTheDay` (poziom serwisu), `DeploySafetyIT.onlyEndedRunsAreSafe` (zasiane wiersze) | **A** (start HTTP → odpytanie `/api/admin/pipeline-runs/{id}` → kroki zamknięte → `deploy-safety` safe) |
| 2. Redeploy w trakcie `assess` → `interrupted`/`deploy`, 0 postów `assessing`, powiadomienie, brak ERROR; stara instancja kończy krok w sekundy | `deploy-plan.md:1792-1794`, wynik `:1803` | przerwanie dochodzi do kroku; przyczyna zapisana; posty wracają | prawdziwy SIGTERM, nakładanie się instancji przy deployu Railway, `RAILWAY_DEPLOYMENT_ID` | `PipelineRunIT` (instancja się zatrzymuje / przerwany w `assess`), `PipelineStartupRecoveryIT` (inny deployment → `deploy`), `RunWatchdogIT` | **B**: logikę kryją dziś testy; dwa konteksty z różnym `RAILWAY_DEPLOYMENT_ID` na jednej bazie to opcja. Prawdziwy SIGTERM i nakładanie zostają C |
| 3. Czas startu po wybudzeniu, 3× przed i 3× po | `deploy-plan.md:1795`, tabela `:1827-1830` | koszt startu | uśpienie i wybudzenie na Railway | — | C (czasy runnera CI nie są porównywalne z Railway) |
| 4. Format logu Spring Boota, bez linii Embabel; czytelny w Railway | `deploy-plan.md:1796`, wynik `:1805` | format logu | podgląd logów Railway | `api-container-smoke` nie szuka w logu linii Embabel i nie sprawdza kodu wyjścia 2 dla `migrate` bez `LLM_*` | B (grep i kod wyjścia do joba smoke; czytelność zostaje C) |

#### S-18 `error-visibility` (`deploy-plan.md:1672-1761`, `context/archive/2026-10-24-error-visibility/plan.md`)

Od S-34 kanałem zdarzeń jest ntfy, a nie check healthchecks.io `api-events` (`deploy-plan.md:1676`). Kroki PR env z healthchecks.io są więc nieaktualne co do kanału, ale ścieżka „przebieg → kolejka → nadawca” zostaje.

| Krok | Kotwica | Dowodzi | Opiera się na | Istniejący test | Klasa |
| --- | --- | --- | --- | --- | --- |
| 3–4. Przebieg admina 2004-01-01 → `failed` (`NoPostsFetchedException`, bez LLM) → log `RUN_NEEDS_ATTENTION … taken` → `reported: DELIVERED` | `deploy-plan.md:1743-1744`; issue #77 | przebieg wymagający uwagi daje dokładnie jedno zdarzenie przez kolejkę, a nadawca je dostarcza | prawdziwy Wykop (data sprzed jego istnienia), healthchecks.io | `NotificationsIT` (kolejka jako mock), `PipelineRunIT` (spy kolejki, log `taken`, `:826`, `:863`), `OperatorAlertQueueTest`, `NtfyAlertsTest` (`LocalNtfyServer`). Brak testu łańcucha przebieg → prawdziwa kolejka → `NtfyAlerts` → odebrana wiadomość | **A** (lokalny serwer ntfy przez nadpisanie beana `OperatorAlerts`, niżej §2.4) |
| 5. Wiadomość w Telegramie: zdarzenie, id przebiegu, `/admin/runs/<id>`, bez sekretów | `deploy-plan.md:1745` | treść i dostarczenie | Telegram (dziś aplikacja ntfy) | `NtfyAlertsTest`, `RunNotificationsTest` (treść) | B (treść sprawdzi atrapa; dostarczenie na telefon zostaje C) |
| 1, 5. Tymczasowy check (`checks.sh apply/delete`), `HEALTHCHECK_PING_URL` na API PR env | `deploy-plan.md:1736-1747` | przygotowanie i sprzątanie | healthchecks.io | — | C i nieaktualne (S-34) |

#### S-20 `source-isolation` (`deploy-plan.md:1930-2019`, `context/archive/2026-10-29-source-isolation/plan.md`)

| Krok | Kotwica | Dowodzi | Opiera się na | Istniejący test | Klasa |
| --- | --- | --- | --- | --- | --- |
| Przebieg admina D-2 → `sourceResults` z jednym wierszem `wykop succeeded`; strona przebiegu pokazuje „Wynik per źródło” | `deploy-plan.md:1970-1971`; plan.md:511, :518 | `RunRecorder.source` z prawdziwego przebiegu, widoczny w `RunView` po V12 | prawdziwy Wykop i LLM, pre-deploy `migrate` | scenariusze dwóch źródeł w `PipelineRunIT` (wiersze sprawdzane SQL-em), `PipelineRunControllerIT.aRangeRunShowsEachSourcesResult…` (zasiane wiersze) | **A** (start HTTP z Wykopem i Redditem lokalnie → GET przebiegu); zrzut ekranu zostaje C |
| `sources:["reddit"]` → 503; `/sources`; `missing-days` po migracji | `deploy-plan.md:1969, 1972` | źródło zarejestrowane, ale nieskonfigurowane; backfill | — | `PipelineRunControllerIT.aRegisteredSourceWithoutItsSettingIsNotConfigured`, `PipelineRunWithoutSourceIT`, `V12BackfillIT`, `MissingDaysIT` | A, **już pokryte**; odpowiedź `/sources` się zmieniła od S-27 (`reddit configured:false`) |
| Zrzuty i polskie teksty „Wynik per źródło” | plan.md:463, :467; `ui/README.md` | wygląd i język | przeglądarka, ocena człowieka | `RunDetailView.test.ts`, `runs.test.ts` | C |

Główne zachowanie S-20 — jedno źródło pada albo milczy → `partial` z `source_failed`/`source_empty`, posty drugiego są zapisane i ocenione; oba milczą → `failed` — nie było sprawdzane w PR env, bo Reddita jeszcze nie było (`deploy-plan.md:1966-1972`). Kryją je testy na poziomie serwisu, bez startu HTTP — `RunWatchdogIT.twoSourcesTakingLongerThanTheTimeoutTogetherAreNotStopped` dla dwóch wolnych źródeł oraz w `PipelineRunIT`: `aSourceThatFailsLeavesTheOtherSourcesPostsAssessedAndTheRunPartial`, `aSourceWithoutAPostMakesTheRunPartialWithItsReason`, `sourcesThatAllGaveNoPostFailTheRunAtTheFetchNamingEach` (Reddit padnięty → `partial`, `:792-829`). Scenariusz HTTP (strona przebiegu, dzwonek, `RUN_NEEDS_ATTENTION`, stan `/internal` dla crona z kodem 0) to klasa **A**.

#### S-27 `reddit-source` (`deploy-plan.md:2021-2134`, `context/archive/2026-10-29-reddit-source/plan.md`)

| Krok | Kotwica | Dowodzi | Opiera się na | Istniejący test | Klasa |
| --- | --- | --- | --- | --- | --- |
| `/sources` pokazuje `reddit configured:true` | `deploy-plan.md:2052` | env → `RedditConfiguration` → bean; krok CI „Ensure PR-env Reddit settings” | zmienna Railway, krok CI | `RedditConfigurationTest` (ApplicationContextRunner); IT używają `LocalReddit`, nie zmiennej (`LocalReddit.java:21`) | B (kontekst z `REDDIT_SUBREDDITS` zbudowałby prawdziwego klienta na adresie zaszytym w kodzie; przeniesienie zmiennej z CI do Railway zostaje C) |
| Przebieg D-2 `sources:["reddit"]` → `reddit succeeded`, fetched > 0, skipped 0, brak powodu `skipped`; INFO per subreddit | `deploy-plan.md:2053` | pobranie, stronicowanie, mapowanie, pominięcie usuniętych postów | prawdziwy Arctic Shift z IP Railway, prawdziwy LLM | `PipelineRunIT.removedRedditPostsAreNeitherFetchedNorSkipped`, `…aWykopOutageLeavesTheRedditPostAssessedFromItsRawPayload`, `RedditRepositoryTest`, `RedditApiClientTest` | **A** na fixturach przez HTTP; żywe archiwum (dostępność, zmiany formatu, limity) zostaje C |
| Post Reddita w feedzie z `webUrl` `…/r/ValueInvesting/…`; `/raw` ma `subreddit` | `deploy-plan.md:2054` | feed i surowe dane posta Reddita; nazwa subreddita z wielkimi literami | prawdziwe archiwum | `RedditPostsTest`, `PostsApiIT` (zasiane posty); brak testu, który czyta post Reddita przez feed po przebiegu | **A** (fixtura) |
| Ten sam zakres drugi raz → `newPosts 0` | `deploy-plan.md:2055` | brak duplikatów | — | `PipelineRunIT.theSameRangeAgainStoresNoDuplicatesAndAssessesNothingAssessed` | A (pokryte na poziomie serwisu) |
| Wynik 79 ocen: 9 `dead`, klasy błędów | `deploy-plan.md:2057` | jakość modelu CI | prawdziwy LLM | — | C (pole S-28/S-35) |
| `migrate` ze złym `REDDIT_SUBREDDITS` kończy się błędem | plan „Not verified” (impl-review fazy 5, :157) | nieudany start | — | tylko `RedditConfigurationTest` (konfiguracja) | A (start kontekstu kończy się błędem) |

Recenzje S-27 zgłosiły dwie rzeczy, których PR env nie pokazał:

- **Impl-review F3:** skrajne `created_utc` przerywało całe pobranie Reddita; znalazła to sonda w recenzji.
- **Phase-5 F4:** archiwum zatrzymane w dniu X daje pierwsze `empty` dopiero trzeciej nocy, a X+1 liczy się jako pokryty bez danych. Potwierdzone czytaniem kodu, bez uruchomienia.

#### Kroki innych wycinków (poza listą roadmapy)

Weryfikacje PR env mają też S-32 i S-34; roadmapa ich nie wymienia. Sekcje S-35 i S-38 w `deploy-plan.md` nie mają podsekcji PR-env verification, czego nie sprawdzałem dalej.

- **S-32** (`deploy-plan.md:2229-2240`): sam zapis już mówi, że dowodem wyłącznika jest `PipelineRunIT`, a PR env pokazuje tylko, że zwykły przebieg się nie zmienił.
- **S-34** (`deploy-plan.md:2364-2366`): cron → `POST /internal/pipeline-runs/nightly-summary` → `NIGHT_SUMMARY` w temacie ntfy. Dziś `InternalPipelineRunIT` sprawdza podsumowanie na zasianych przebiegach z kolejką jako mockiem (`InternalPipelineRunIT.java:147-149`), więc łańcuch do odebranej wiadomości byłby klasy A jak w S-18.

### 2. Infrastruktura testów, która już jest

Ścieżki względem `szczur-radar-api/src/test/java/pl/szczurradar/api/`, chyba że zaczynają się od `src/main` albo `src/test/resources`.

#### 2.1 Kontekst i baza

- `TestcontainersConfiguration.java:19-26`: `@ServiceConnection PostgreSQLContainer("postgres:18")`, importowany jawnie przez każde IT (`@Import`).
  - Nie ma klasy bazowej IT ani meta-adnotacji.
  - Jeden kontener przypada na klucz kontekstu Springa.
  - Kontenery nie są używane ponownie między uruchomieniami (brak `withReuse`).
- `DbIsolationExtension.java` (S-30) jest włączone globalnie przez `META-INF/services` i `junit-platform.properties:9`.
  - Po każdej klasie `@SpringBootTest` najwyższego poziomu czyści tabele (`:79-125`).
  - Przed klasą, która używa kontekstu ponownie, kończy się błędem, gdy w bazie zostały dane (`:56-77`).
  - Pomija klasy `@Nested` i `@DirtiesContext` (`:151-172`).
- Konwencja: `@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)` (Flyway jest wyłączony w main, `src/main/resources/application.properties:28`).
- `OperatorAlertQueue`, `RunWatchdog` i `PipelineStallSettings` są `@ConditionalOnWebApplication` (`src/main/.../alerts/AlertsConfiguration.java:21`, `src/main/.../pipeline/RunWatchdog.java:44`, `src/main/.../pipeline/PipelineStallConfiguration.java:21`). Scenariusz przebiegu potrzebuje więc środowiska `MOCK` albo `RANDOM_PORT`, nie `NONE`.
- `LLM_*` przychodzą ze środowiska procesu:
  - w CI z `ci.yml:56-61` (zmienne repozytorium + sekret `LLM_API_KEY`);
  - `config/LlmConfigurationCheck` odrzuca kontekst bez nich;
  - prawdziwy klient jest `@Lazy` (`src/main/.../llm/LlmConfiguration.java:22-26`), więc podmieniony nigdy nie powstaje.
- Sesja admina w IT to post-procesor `SecurityMockMvcRequestPostProcessors.user(...)` (`pipeline/api/PipelineRunControllerIT.java:6,630`), nie prawdziwe logowanie. Prawdziwe logowanie sprawdza `AuthIT`.

#### 2.2 Atrapy źródeł (prawdziwy klient, prawdziwe ponowienia)

- `sources/LocalApiServer.java:27-122`: serwer na `com.sun.net.httpserver.HttpServer`.
  - Zapisuje żądania z nagłówkami i odpowiada przez `Function<Request, Reply>`, z wątkiem na żądanie.
  - Ma pomocnik `fixture(dir, name)`.
  - Ma podklasy `sources/wykop/LocalWykopServer` i `sources/reddit/LocalRedditServer`.
  - Zamieniany jest tylko adres bazowy; `WykopApiClient`, `RedditApiClient`, `HttpRetry`, repozytorium, fetcher i mapper są prawdziwe.
  - W repo nie ma WireMocka, MockWebServera ani `MockRestServiceServer`.
- Adresy bazowe źródeł to stałe w kodzie, nie ustawienia: `WykopApiClient.DEFAULT_BASE_URL` (`src/main/.../sources/wykop/WykopApiClient.java:53`) i `RedditApiClient.DEFAULT_BASE_URL` (`src/main/.../sources/reddit/RedditApiClient.java:45`). Testy dają więc własny bean `WykopRepository` / `RedditRepository` z `@TestConfiguration`. Produkcyjne beany powstają tylko przy `WYKOP_KEY`/`WYKOP_SECRET` (`src/main/.../sources/wykop/WykopConfiguration.java:28-39`) i przy `REDDIT_SUBREDDITS` (`src/main/.../sources/reddit/RedditConfiguration.java:42`).
- `sources/reddit/LocalReddit.java:38-174` to jedyna współdzielona, publiczna `@TestConfiguration` źródła.
  - Ma statyczne przełączniki `volatile`: `configure`, `down`, `empty`, `page`, `hold`/`release`, `delay`, `reset`, `requests`.
  - Domyślnie zwraca cały dzień r/inwestowanie z 2026-10-27, 14 postów (`:41`).
- `LocalWykop` `@TestConfiguration` jest skopiowany w 6 klasach IT, np. `pipeline/PipelineRunIT.java:1271-1284` i `pipeline/api/PipelineRunControllerIT.java:648-657`. W `PipelineRunControllerIT` i `InternalPipelineRunIT` odpowiada zawsze pustym dniem (`PipelineRunControllerIT.java:88-103`).
- Z grepa wnioskuję, że lokalne serwery nie filtrują po dacie. Zwracają fixturę dla dowolnego zakresu, z jej `published_at`; Wykop filtruje po stronie serwera (`date_from`/`date_to`, `src/main/.../sources/wykop/WykopRepository.java:167-172`). Pokrycie dni (`MissingDays`) liczy się z zakresu przebiegu i wyniku źródła, nie z dat postów (`deploy-plan.md:1646, 1943`). Scenariusz S-13 może więc puścić fixturę na dowolny dzień zakresu, ale feed pokaże posty z dnia fixtury.

#### 2.3 Atrapy LLM

- `@MockitoBean LlmClient` występuje w 7 klasach: `PipelineRunIT:175`, `RunWatchdogIT:138`, `OrphanedRunIT:110`, `PipelineRunControllerIT:105`, `InternalPipelineRunIT:145`, `AdminRoleIT:46`, `LlmCheckControllerIT:36`.
  - Nie ma tu HTTP, `TransportRetry` ani parsowania odpowiedzi dostawcy.
  - Prawdziwe są `Assessor`, prefiltr, parser i walidator.
  - Testy skryptują go `doAnswer` i pomocnikami `response(...)` (`PipelineRunIT.java:1225-1237`), też według treści `userMessage` (`:372-377`).
- `llm/ScriptedProviderServer.java:25-138`: kolejka `Reply` (status, nagłówki, opóźnienie); serwer zapisuje żądania, a po wyczerpaniu skryptu odpowiada 500.
  - Klient to prawdziwy klient SDK z `LlmClientFactory.create(props, baseUrl, sleeper)` (`src/main/.../llm/LlmClientFactory.java:32-41`, szew testowy) z prawdziwym `TransportRetry`.
  - Z klas IT używa go tylko `pipeline/LlmOutageIT` (`:54-126`): odpowiedź na check LLM to prawdziwe `fixtures/deepseek/chat-completion.json`, potem HTTP 500.
- Fixtury LLM:
  - koperty dostawców: `fixtures/deepseek/chat-completion.json`, `gemini/generate-content.json`, `openrouter/chat-completion.json`;
  - `fixtures/llm/recordings/<config>/<gold-id>.json` (4 konfiguracje × 120): surowe odpowiedzi modelu z dostawcą, modelem i tokenami;
  - `fixtures/assessor/gold/posts.json` (120 wejść z #gielda 2026-10-01/02).
- Dopasowanie do dni źródeł (skrypt agenta): 7 z 16 elementów dnia Wykopu ma bliźniaka w złotej próbce z identycznym ciałem wejścia — wpisy 3, 6, 15, 16 i znaleziska 17, 18, 20 (gold `-003`, `-002`, `-001`, `-000`, `-057`, `-020`, `-013`). Pozostałe 9 i wszystkie posty Reddita nie mają nagrania.
- `measurement/Recording.java:77-85` (`response()`) zamienia nagranie w `LlmResponse`, a `Recording.readAll(configId)` wczytuje konfigurację. Klasy są package-private, a pakiet jest według swojego `CLAUDE.md` tylko do pomiarów. Jego narzędzia na żywo mają tag `measurement`, który `verify` pomija (`pom.xml:30, 211-220`).

#### 2.4 Alerty i powiadomienia

- W IT `NTFY_URL` nie jest ustawiony, więc bean `OperatorAlerts` to `NoOperatorAlerts` za prawdziwą kolejką (`src/main/.../alerts/AlertsConfiguration.java:27-36`).
  - Kolejka loguje `Operator alert <EVENT> (run …) taken` i `reported: …` (`src/main/.../alerts/OperatorAlertQueue.java:170-262`).
  - Okno powtórzeń 10 min i limit 2 na minutę liczy na `InstantSource.system()` (`:146`).
- Wzorce w IT:
  - `@MockitoSpyBean OperatorAlertQueue` z `verify(raise(...))` (`PipelineRunIT.java:231, 864`);
  - `OutputCaptureExtension` z `awaitLog(...)` (`:826, 863`);
  - kolejka jako `@MockitoBean` (`InternalPipelineRunIT.java:149`, `NotificationsIT:77`).
- `alerts/LocalNtfyServer` (package-private) używa tylko `NtfyAlertsTest`. `AlertSettings` przyjmuje wyłącznie `https` (`src/main/.../alerts/AlertSettings.java:58, 76`), więc lokalny serwer nie przejdzie przez `NTFY_URL`. Pakietowy konstruktor `NtfyAlerts(URI, token, connectTimeout, requestTimeout)` (`src/main/.../alerts/NtfyAlerts.java:66`, użyty w `NtfyAlertsTest.java:89`) pozwala jednak nadpisać bean `OperatorAlerts` z konfiguracji testowej w pakiecie `alerts`. Wtedy łańcuch przebieg → prawdziwa kolejka → prawdziwy `NtfyAlerts` → odebrane żądanie HTTP jest w CI bez zmiany kodu produkcyjnego.
- Powiadomienia w aplikacji testy czytają z tabeli `notifications` (`PipelineRunIT.java:1109`) albo przez `/api/admin/notifications` (`NotificationsIT`).

#### 2.5 Czas

- Bean `Clock` to `Clock.systemUTC()` (`src/main/.../config/ClockConfig.java:15-18`). Czytają go m.in. `InternalPipelineRunController` (zakres nocy), `PipelineRunController` (walidacja zakresu `:102` i „dziś” `missing-days` `:158`) oraz `NightSummary`.
- Testy podmieniają go `@Primary Clock`, każda klasa własną kopią: `MissingDaysIT.java:275-284` (stały 2026-10-24) i `InternalPipelineRunIT.java:142, 652-680` (`MutableClock`).
- Watchdog i sprzątanie osieroconych przebiegów liczą na `now()` Postgresa: `last_activity_at < now() - :millis` (`src/main/.../pipeline/state/RunRecorder.java:300-305, 343-364`). Testy sterują tym przez `PIPELINE_STALL_TIMEOUT` albo cofnięte daty w wierszach (`OrphanedRunIT`).

#### 2.6 Liczba kontekstów

Odczyt adnotacji, bez obserwacji logów Springa:

- **39 klas `*IT`.** 17 z nich dzieli 4 konteksty: `NONE` + Flyway (7 klas), `NONE` bez Flyway (2), `MOCK` + MockMvc + Flyway (6), `MOCK` + Flyway bez MockMvc (2).
- **22 klasy mają własny kontekst.** Należą do nich wszystkie klasy przebiegu i tras przebiegu: każda ma własny `@Import`, `@MockitoBean` albo własne właściwości.
- Nie ustawiono `spring.test.context.cache.maxSize`, więc obowiązuje domyślne 32.

### 3. Czas CI

Źródła: `.github/workflows/ci.yml`, `szczur-radar-api/pom.xml`, `gh run view --json jobs` dla 6 zielonych runów, log joba `backend` 131047906815.

- **Struktura joba `backend`:**
  - 8 jobów testowych biegnie równolegle, bez `needs:` (ci.yml:20-247); `railway-config` ma `needs:` na wszystkich ośmiu (ci.yml:263-271).
  - `backend` uruchamia `./mvnw -B verify` bez dodatkowych flag (ci.yml:56-61), z cache Mavena (ci.yml:28-33).
  - Nie ma `timeout-minutes`; jawny limit ma tylko `railway-config` (115 min, ci.yml:284).
- **Failsafe** (pom.xml:188-207): `**/*IT.java`, bez `forkCount`/`parallel`, czyli jedna JVM i klasy po kolei. `it.seed` losuje tylko kolejność metod (pom.xml:31-35, 195-197).
- **Czasy w 6 zielonych runach** (43178332505, 43176755783, 43173952241, 43171856054, 43053080627, 43049208403):
  - `backend` 190–224 s, cały run 210–252 s;
  - drugi najdłuższy job to `pipeline-trigger-test` (134–149 s), a `railway-config` 14–32 s;
  - krok „Build and test” zajmuje 182–207 s.
- **Rozbicie joba 131047906815** (`Total time: 03:24 min`):
  - surefire 32 s (1127 testów), failsafe ok. 159 s (388 testów);
  - 39 klas IT razem 134,5 s;
  - 23 logowane starty kontekstu razem 62,2 s, w tym pierwszy, zimny, 15,2 s w `CreateAdminCommandIT`;
  - 26 startów kontenerów razem 33,7 s.

  Run PR 131042754664: failsafe ok. 143 s, całość 03:00.
- **Najwolniejsze klasy IT:** `RunWatchdogIT` 36,2 s (prawdziwe oczekiwania: próg 5 s, opóźnienia 3 s), `CreateAdminCommandIT` 17,3 s (zimny kontekst), `DeploySafetyIT` ok. 11 s, `PipelineRunIT` 7,8 s.
- **Ostatnie 100 runów:** 96 zielonych i 4 czerwone (3 × `railway-config`, 1 × `backend` 41784416932 — flake S-30). Mediana czasu zielonego runu to 230 s.
- **Wniosek dla S-31:** koszt nowej klasy scenariuszy z własnym kontekstem to w tym jobie ok. 2,7 s (start kontekstu) + 1,3 s (kontener) + Flyway, plus czas przebiegów. Scenariusze w jednej klasie albo za jednym wspólnym `@Import` bez nadpisań per klasa dzielą jeden kontekst.

## Code References

- `szczur-radar-api/src/test/java/pl/szczurradar/api/TestcontainersConfiguration.java:19-26` — kontener Postgresa per kontekst
- `szczur-radar-api/src/test/java/pl/szczurradar/api/DbIsolationExtension.java:56-172` — izolacja klas IT (S-30)
- `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/LocalApiServer.java:27-122` — lokalny serwer HTTP źródeł
- `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/reddit/LocalReddit.java:38-174` — współdzielony Reddit z przełącznikami
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java:175, 231, 254-293, 1109, 1225-1237, 1271-1284` — mock LLM, spy kolejki, `reset`/`settle`, liczba powiadomień, pomocnik odpowiedzi, `LocalWykop`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java:88-103, 630` — Wykop z pustym dniem, sesja admina post-procesorem
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java:125-153` — pusty Wykop, `MutableClock`, kolejka jako mock
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/LlmOutageIT.java:54-126` — jedyny łańcuch prawdziwego transportu LLM w IT
- `szczur-radar-api/src/test/java/pl/szczurradar/api/llm/ScriptedProviderServer.java:25-138` — skryptowany serwer dostawcy
- `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/LlmClientFactory.java:32-41` — szew: klient dostawcy na zadanym adresie
- `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/Recording.java:77-85` — nagranie → `LlmResponse`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertsConfiguration.java:21-42` — bean `OperatorAlerts` i kolejka, tylko w serwerze
- `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertSettings.java:58, 76` — reguła `https` dla `NTFY_URL`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/NtfyAlerts.java:58, 66` — konstruktor z ustawień i pakietowy z `URI`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java:53`, `.../reddit/RedditApiClient.java:45` — adresy bazowe źródeł jako stałe
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ClockConfig.java:15-18` — bean `Clock`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStartupRecovery.java:30, 73` — `RAILWAY_DEPLOYMENT_ID` i przyczyna `deploy`
- `.github/workflows/ci.yml:20-61, 263-290` — job `backend`, `railway-config`
- `szczur-radar-api/pom.xml:29-35, 175-220` — surefire/failsafe, tag `measurement`

## Architecture Insights

- **„Pełne API” w tym repo to dziś kontekst Springa w JVM testu.** W tym kontekście są prawdziwy `RunSequence` na wątku kolejki, prawdziwy Postgres 18, prawdziwi klienci źródeł na lokalnym HTTP oraz LLM jako mock albo prawdziwy klient SDK na skryptowanym HTTP.
- **Test czarnej skrzynki na obrazie Dockera wymagałby zmian w kodzie produkcyjnym.** Musiałby dostać ustawienia adresów bazowych źródeł i dostawców LLM, bo dziś są stałymi, oraz obejście reguły `https` dla `NTFY_URL`. To byłaby zmiana kodu produkcyjnego tylko dla testów.
- **Wzorzec izolacji scenariuszy jest ustalony** przez `PipelineRunIT` i S-26/S-30:
  - `reset` jako jeden `TRUNCATE`;
  - `settle()` przed sprawdzeniem „bez ERROR”;
  - losowa kolejność metod z `it.seed`;
  - czyste tabele po klasie (`DbIsolationExtension`).

  Recenzje S-17 (plan-review F1/F2) pokazały, że bez `settle()` sprawdzenia „bez ERROR” są niestabilne.
- **Scenariusze łapią to, co przepuszczają strażnicy, ale tylko przy prawdziwym zapisie wartości.**
  - Audyt X1 (konwerter `RunKind` `value` → `name`) i X2 (literówka w złożonym `CHECK`) przeszły przez `ClosedSetsIT`, a zaczerwieniły testy zachowania, które akurat zapisują tę wartość (`context/changes/post-m3-verification-audit/research.md:65-68, 253`).
  - Scenariusze S-31, które przez prawdziwe zapisy przechodzą przez rodzaje przebiegu (`range`, `retry`, `posts`), statusy końcowe i przyczyny przerwania, dają ten efekt celowo, a nie przypadkiem.
  - Część z tego (X1/X2) od S-39 pilnują też strażnicy wyprowadzani z kodu.
- **Przebiegi porządkuje kolejka.** Przebiegi biegną po jednym, a start zakresu z resztkami najpierw kolejkuje RETRY (`deploy-plan.md:1742`). Scenariusz z kilkoma przebiegami musi więc albo czekać na koniec każdego, albo kontrolować resztki.

## Historical Context (from prior changes)

- `context/archive/2026-10-24-missed-night-detection/plan.md:280-305` — PR env S-13: przebieg D-3 → lista D-2 → wypełnienie → pusta lista. Zrobione przez agenta w pr-106 (dowód w issue #72). Recenzja F2, brak testu dwóch źródeł, zamknięta w S-20 (`MissingDaysTest`); F5 (dwa odczyty kontrolera bez transakcji) zostaje jako follow-up.
- `context/archive/2026-10-25-pipeline-without-embabel/plan.md:361-376, 433` — PR env S-17. Pchnięcie gałęzi nie przerwie przebiegu (strażnik S-14 wstrzymuje deploy), więc procedura przeszła na `railway redeploy` (impl-review F7a). Ogólne sprawdzenie „bez ERROR” dodano do `RunWatchdogIT` (F7b).
- `context/archive/2026-10-24-error-visibility/plan.md:356-377` — PR env S-18. Zakres 2004-01-01 wybrano, bo przebieg „na dziś” poszedłby do LLM (plan-review F1). Linia dostarczenia dostała id przebiegu (plan-review F4).
- `context/archive/2026-10-29-source-isolation/plan.md:511-524` — PR env S-20 bez drugiego źródła.
  - Recenzja F3 (pominięta): odrzucony zapis wyniku źródła nie kończy kroku, więc przebieg zatrzymany przez inną instancję pobiera kolejne źródło. Nie ma na to testu — kandydat na scenariusz.
  - Recenzja F6: opóźnienia `RunWatchdogIT` grożą niestabilnością.
- `context/archive/2026-10-29-reddit-source/plan.md:393-428` — PR env S-27, wiersze Automated wykonane przez agenta curl + jq na PR env.
  - Phase-5 F6: sesja admina przez `create-user`/`reset-password` po `railway ssh` łamie zasadę S-07, co przyjęto. Scenariusze w teście usuwają ten krok.
  - Phase-5 F4: pierwsze `empty` dopiero trzeciej nocy po zatrzymaniu archiwum — potwierdzone czytaniem kodu, bez uruchomienia.
- `context/foundation/lessons.md:127-132, 148-153, 162-167` — weryfikacja agent-first; kryterium sprawdzalne poleceniem idzie do Automated; konto admina w PR env zakłada agent. S-31 przenosi większość takich kontroli z PR env do testu.
- `context/foundation/verification-scope.md:30-35` — lista „Co zostanie ręczne”.
  - Wpis „Dostarczenie alertu do Telegrama (healthchecks.io)” jest od S-34 aktualny tylko dla cronów. Zdarzenia API idą do ntfy (`deploy-plan.md:1676`, PRD doprecyzowanie S-34).
  - Na liście brakuje kroków klasy C, które wyszły w tym badaniu: oględziny UI, czas startu na Railway, czytelność logów w Railway, przegląd dokumentów, migracja na danych produkcji (pre-deploy `migrate`, backfill V12) oraz przeniesienie zmiennej z CI do Railway.
- Rozjazd w dokumentacji: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md:657` mówi o „mocked `OperatorAlerts`” w `InternalPipelineRunIT`, a kod mockuje `OperatorAlertQueue` (`InternalPipelineRunIT.java:147-149`).

## Related Research

- `context/changes/post-m3-verification-audit/research.md`: eksperymenty X1–X6 i mapa strażników; nie wspomina S-31 ani PR env (`:65-68` X1/X2, `:253` wniosek o testach zachowania, `:279-302` „Materiał dla M-4”).
- `context/archive/2026-11-05-it-test-isolation/research.md`: S-30, izolacja klas IT i kontekstów — warunek wstępny S-31.
- `context/archive/2026-10-29-reddit-source/research.md`, `context/archive/2026-10-24-missed-night-detection/research.md`, `context/archive/2026-10-25-pipeline-without-embabel/research.md` — badania wycinków, których kroki S-31 odtwarza. Nie czytałem ich w tym badaniu; plany i recenzje wystarczyły do klasyfikacji.

## Open Questions

Decyzje dla `/10x-plan` i właściciela. Fakty są wyżej.

1. **Granica „pełnego API”.** Do wyboru są dwie drogi:
   - kontekst Springa w JVM testu (MockMvc albo `RANDOM_PORT`) z podmienionymi beanami źródeł, LLM i nadawcy alertów — da się bez zmian w kodzie produkcyjnym;
   - czarna skrzynka na obrazie Dockera — wymaga ustawień adresów bazowych źródeł i LLM oraz obejścia reguły `https` dla `NTFY_URL`, czyli zmian w kodzie produkcyjnym.
2. **Atrapa LLM „z prawdziwych payloadów”.** Opcje:
   - (a) `@MockitoBean LlmClient` z odpowiedziami pisanymi w teście — tak jak dziś;
   - (b) `ScriptedProviderServer` z prawdziwą kopertą dostawcy i odpowiedziami modelu z nagrań: 7 z 16 elementów dnia Wykopu ma nagranie, reszta potrzebuje zastępstwa;
   - (c) jednorazowe nagranie odpowiedzi modelu dla dni fixtur — wymaga prawdziwych wywołań LLM przy nagrywaniu i nowej fixtury;
   - (d) użycie `measurement/Recording` poza pakietem pomiarów — wbrew jego `CLAUDE.md`.

   Roadmapa mówi „z prawdziwych payloadów” (`roadmap.md:172`). Dziś spełniają to atrapy źródeł, a atrapa LLM tylko w `LlmOutageIT` i tylko dla checku.
3. **Jedna klasa scenariuszy (jeden kontekst) czy klasa na wycinek** — każda klasa z własnym kontekstem dokłada ok. 4 s plus Flyway (§3). Do decyzji też: czy dodać `timeout-minutes` do joba `backend` jako część „kontroli czasu CI” (`roadmap.md:181`).
4. **Lista ręczna a „done when” M-4.** Kroki klasy C spoza czterech pozycji `verification-scope.md:30-35` trzeba albo dopisać do listy (decyzja właściciela, bo to zmienia kryterium milestone'u), albo uznać za mieszczące się w pozycji „deploy na Railway”. Do tego aktualizacja wpisu o Telegramie po S-34.
5. **Zakres wycinków.** Roadmapa wymienia S-13, S-17, S-18, S-20 i S-27. Weryfikacje PR env mają też S-32 i S-34; S-34 — cron → podsumowanie nocy → ntfy — da się zrobić tym samym łańcuchem co S-18. Do decyzji, czy wchodzą.
6. **Sesja w scenariuszu.** Post-procesor `user(...)` jak w `PipelineRunControllerIT` albo prawdziwe logowanie przez `/api/auth/login` z kontem z `create-admin`/`create-user` — droga operatora z PR env, bliżej kroku ręcznego, ale dłużej.
7. **Przerwanie przez deploy (S-17, klasa B).** Do decyzji: czy dokładać scenariusz z dwoma kontekstami o różnym `RAILWAY_DEPLOYMENT_ID` na jednej bazie, czy uznać, że wystarczają `PipelineStartupRecoveryIT` i `PipelineRunIT`, a prawdziwy SIGTERM zostaje na liście ręcznej.
8. **Reguła na przyszłość.** Do decyzji: czy kolejne wycinki mają dopisywać scenariusz zamiast kroku PR env — lekcja albo zapis w `REPOSITORY_GUIDELINES.md` — aby „done when” M-4 nie rozjechało się z następnym wycinkiem.
