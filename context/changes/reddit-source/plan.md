# S-27 reddit-source — Reddit jako drugie źródło przez Arctic Shift — Implementation Plan

## Overview

Reddit staje się drugim źródłem postów obok Wykopu: pakiet `sources/reddit/` zbudowany 1:1 na wzorcu `sources/wykop/` pobiera posty wybranych subredditów z publicznego archiwum Arctic Shift (`https://arctic-shift.photon-reddit.com`, bez klucza), mapuje je do wspólnego modelu (`is_self → post`, inaczej `link`), a silnik, zapis, ocena i API nie zmieniają się ani w kodzie, ani w zachowaniu (NFR `prd.md:269`). Źródło jest skonfigurowane tylko wtedy, gdy zmienna `REDDIT_SUBREDDITS` ma niepustą listę kanałów; produkcja dostaje ją po S-28. Przy okazji drugie źródło wynosi wspólną pętlę ponowień do `sources/` (wymóg `sources/CLAUDE.md:44-47`), a testy silnika z S-20 przechodzą z dubletu `ScriptedSourceConfiguration` na prawdziwy stos Reddita nad lokalnym serwerem (przejęcie F1, `roadmap.md:242`). Wspólny model dostaje jedno nowe pole: `posts.channel` (kanał, z którego post przyszedł: `gielda` dla Wykopu, nazwa subredditu dla Reddita), bo właściciel chce mieć kanał w bazie wprost, nie tylko w `assessments.input` i RAW (decyzja 2026-10-29).

Decyzje właściciela z wywiadu `/10x-plan` (2026-10-29): Arctic Shift jako jedyna ścieżka z runbookiem na wypadek końca archiwum (Reddit zamyka publiczne Data API w marcu 2027); kanały pierwszej konfiguracji `stocks`, `ValueInvesting`, `investing`; przełącznik `REDDIT_SUBREDDITS`; PR env dostaje ją z repository variable krokiem CI; posty zdjęte i skasowane nie są elementami pobrania; `is_self → post`, inaczej `link`; retencja jak Wykop, z doprecyzowaniem w PRD; kolumna `posts.channel` w S-27, a język posta jako wpis w S-28/S-29 (roadmapa), nie tutaj.

## Current State Analysis

- Szew źródła to dwa porty: `SourceFetcher` (`source()`, `configured()`, `Fetched fetch(Instant from, Instant to)`; `Fetched(posts, fetched, skipped)` — `sources/SourceFetcher.java:11-44`) i `RawInputMapper` (`map(byte[] raw)` — `sources/RawInputMapper.java:9-20`). `SourceIngest` (`pipeline/step/SourceIngest.java:38-40`) i `AssessmentInputs` (`pipeline/assess/AssessmentInputs.java:27-30`) zbierają beany w mapy `Collectors.toUnmodifiableMap(…::source, identity())`: drugi bean pod tym samym `Source` kończy start kontekstu wyjątkiem o duplikacie klucza.
- `Source.REDDIT` jest zarejestrowane w czterech `CHECK` od `V12` (`V12__pipeline_run_sources.sql:23-31,44`); `ClosedSetsIT` ma cztery wpisy; etykieta `reddit: 'Reddit'` w `szczur-radar-web/src/labels.ts:72`. Migracja do S-27 nie jest potrzebna; `GET /api/admin/pipeline-runs/sources` listuje każdy bean fetchera w kolejności `Source` (`SourceIngest.java:63-69`), `missing-days` filtruje po `configured` (`PipelineRunController.java:151-158`), cron bierze wszystkie skonfigurowane (`InternalPipelineRunController.java:65-71`).
- Wykop (`sources/wykop/`, 11 klas): `WykopApiClient` z pętlą ponowień w `exchange` (`:221-267`; 429/5xx/`IOException`, `MAX_ATTEMPTS = 3`, `Retry-After` ≤ 30 s albo 2 s / 4 s, `requireTime` względem terminu, WARN na ponowienie, maskowanie sekretów), `WykopRepository` (`FETCH_DEADLINE = 10 min` (`:61`), stronicowanie po `pagination.total` ze spójnością, pauza 300 ms, `Pause` i `Sleeper` zastępowane w testach), `WykopMapper`/`WykopPosts` (ten sam mapper dla zapisu i oceny), `WykopFetcher` (`configured() = repository.isPresent()`), `WykopConfiguration` (bean warunkowy od `WYKOP_KEY`/`WYKOP_SECRET`, `:24-54`). `UnmappableItemException` leży w `sources/wykop`, choć pojęcie jest ogólne (audyt S8).
- Testy silnika dwóch źródeł (S-20): `TEST/sources/ScriptedSourceConfiguration.java:33-53` rejestruje `ScriptedSourceFetcher(Source.REDDIT)` i `RawInputMapper` delegujący do `WykopPosts`; importują ją `PipelineRunIT:110`, `RunWatchdogIT:95`, `MissingDaysIT:61`; `PipelineRunIT.LocalWykop` (`:1041-1053`) dostarcza `WykopRepository` na `LocalWykopServer` zamiast beana z kluczy. Dwa testy przypinają JSON `/sources` tylko z Wykopem (`PipelineRunControllerIT:155-157`, `PipelineRunWithoutSourceIT:78-80`); `PipelineRunControllerIT:255-266` sprawdza 503 dla `reddit`.
- Watchdog: w trakcie pobrania jednego źródła nic nie przesuwa `last_activity_at` (`pipeline/CLAUDE.md:85-92`); źródło musi skończyć poniżej `PipelineStallSettings.MIN_DEFAULT = 15 min` (`:42`); suma terminów źródeł może przekroczyć próg; `TRIGGER_RUN_TIMEOUT=90m` dla całej nocy.
- `posts` nie ma kolumny kanału ani języka (`posts/Post.java:35-117`): `tags TEXT[]` to tagi wpisu z Wykopu (`WykopItem.tags`, `WykopPosts.java:52`), a kanał (`gielda` / subreddit) jest tylko w `assessments.input` (`item.channels`) i w RAW. `PostColumnsIT` klasyfikuje każdą kolumnę `posts` (`SET_ONCE` / `STATE`, `:78-79`), `JpaSchemaIT` sprawdza encje względem schematu, a migracje są fix-forward (ostatnia `V12`).
- `skipped > 0` daje powód uwagi `SKIPPED` (`pipeline/run/PipelineRun.java:340-342`) → dzwonek i `RUN_NEEDS_ATTENTION`; `fetched` = każdy zwrócony element, `skipped` = elementy, które nie mogły stać się postem (`SourceFetcher.java:28-36`); baza wymaga `skipped <= fetched` (`V12:46`).
- Arctic Shift (research §1): `GET /api/posts/search?subreddit=&after=&before=&sort=asc&limit=100`, koperta `{"data":[…]}` / `{"data":null,"error":"…"}`, `created_utc` w sekundach UTC (int), granice `after`/`before` wyłączne (zaobserwowane), brak kursora i `total`, 429 z `X-RateLimit-Reset`, 422 „Timeout. Maybe slow down a bit”, 500 w incydentach; posty w archiwum sekundy po publikacji; `removed_by_category` ≠ null i `selftext` `[removed]`/`[deleted]` dla postów zdjętych (44 z 58 w r/stocks 2026-10-27); pełny obiekt posta Reddita (~100 pól) z prawdziwymi nickami.
- IaC/CI: `.railway/railway.ts:145-150` deklaruje `WYKOP_*` przez `preserve()` (niczego nie tworzy); `variables.ignore` (`:313-322`) tylko dla ręcznych zmiennych strojenia; krok CI „Ensure PR-env Wykop keys” (`.github/workflows/ci.yml:385-417`) idzie przed „Deploy guard” (`:443`), „Plan and gate” (`:455`) i „Apply” (`:495`). Zmienna ustawiona przed deklaracją → `variable.delete` w planie → bramka odmawia (S-14).

## Desired End State

Po `REDDIT_SUBREDDITS=stocks,ValueInvesting,investing` w PR env przebieg zakresu admina (i noc na produkcji po włączeniu) ma w `sourceResults` wiersz `reddit succeeded` z `fetched > 0` i `skipped 0`, posty Reddita są w feedzie i mają RAW w „Danych dla nerdów”, a ten sam zakres uruchomiony drugi raz nie dodaje postów. Bez zmiennej Reddit jest w `/sources` jako `configured:false`, przebieg nazywający go dostaje 503 `source_not_configured`, noc i „Dni bez danych” go nie widzą. Posty zdjęte przez moderację nie są pobierane ani liczone. Każdy post ma `posts.channel` (`gielda` dla Wykopu, także historyczne po backfillu; nazwa subredditu dla Reddita). `./mvnw -B verify` jest zielone bez sieci; `PipelineRunIT` dowodzi, że przy awarii Wykopu post Reddita przechodzi przez RAW, produkcyjny mapper i ocenę, a zapisane wejście równa się mapowaniu fixture Reddita. Klient Wykopu zachowuje się jak przed zmianą (ten sam `WykopApiClientTest`), a pętla ponowień istnieje w `sources/` raz.

### Key Discoveries:

- Dwa porty i mapy po `source()` (`SourceIngest.java:38-40`, `AssessmentInputs.java:27-30`): produkcyjny `RedditFetcher` i dublet pod `Source.REDDIT` nie mogą współistnieć w jednym kontekście — rejestracja beanów i przepięcie testów muszą być jedną fazą.
- Przepis dodania źródła (`sources/CLAUDE.md:29-60`) i tabela „Must not” (`:66-74`): klient nie zna stronicowania, mapper nie przekazuje autora/URL/id, fetcher nie zapisuje, źródło nie ma własnego filtra treści (`:83-85`); drugie źródło z ponowieniami wynosi wspólną pętlę (`:44-47`).
- `configured()` to własność beana fetchera, nie listy: jeden przełącznik steruje `/sources`, `resolve`, nocą i „Dniami bez danych” (research, Architecture Insights).
- Granice `after`/`before` Arctic Shift są wyłączne, bez `total` i kursora: stronicowanie po czasie z zakładką o jedną sekundę i deduplikacją po `id`; strona krótsza niż `limit` kończy (research §1).
- Zdjęte posty jako `skipped` dałyby alarm co noc (`PipelineRun.java:340-342`); jako elementy mapowane — koszt LLM za `[removed]`; decyzja: poza elementami pobrania, liczone w logu.
- `PipelineRunIT.LocalWykop` (`:1041-1053`) to wzorzec beana testowego nad lokalnym serwerem w miejsce beana z kluczy; `LocalWykopServer` jest generyczny (`TEST/sources/wykop/LocalWykopServer.java:1-70`).

## What We're NOT Doing

- Komentarzy Reddita (non-goal `prd.md:312`, `contract-v2.md:71`), kontekstu wątku ani endpointu `comments/search`.
- Etykiety źródła w feedzie i szczegółach posta ani świeżości per źródło — S-29 (`PostItem` bez `source` zostaje).
- Pomiaru jakości po angielsku, kosztu LLM, zmian promptu (`PROMPT_VERSION`), prefiltru (`u/`, `r/`, markdown) ani kontraktu §9/§10 — S-28 i ścieżka §16.
- Włączenia Reddita na produkcji: `REDDIT_SUBREDDITS` na produkcji ustawia operator po S-28 (runbook w deploy-plan), nie ten PR.
- Synchronizacji usunięć (`_meta.was_deleted_later`), drugiego odczytu po 36 h, aktualizacji `score`/`num_comments`, wykrywania edycji: posty i RAW Reddita przechowywane jak Wykopu (decyzja właściciela 2026-10-29, doprecyzowanie PRD w fazie 5).
- Filtrów megawątków (`stickied`, `AutoModerator`), NSFW (`over_18`) ani jakiegokolwiek filtra treści w źródle: tylko stan „zdjęty/skasowany” wyłącza element.
- Parametru `fields` Arctic Shift: RAW to pełny obiekt posta (bezstratnie, `lessons.md` RAW vs dane operacyjne).
- Abstrakcji dostawcy danych Reddita ponad `SourceFetcher` (odrzucona opcja „własny port klienta”).
- Zmian w `szczur-radar-web/` (etykieta `reddit` istnieje; checkboxy źródeł przychodzą z API), w `tools/db-backup/tables.sh` (bez nowej tabeli), w `WykopConfiguration.text` → `SettingText` (S8, osobno), w `KitchenSinkView` (wymyślony URL `/r/inwestowanie/new` zostaje).
- Pokazywania `channel` ani `source` w `PostItem` feedu i szczegółów (S-29) ani kolumny języka posta — język jako pole wyjścia modelu albo detekcja to wpis w S-28/S-29 w roadmapie (decyzja 2026-10-29).
- Narzędzi pomiarowych Reddita (`RedditGoldSampler` itp.) — S-28.

## Implementation Approach

1. Najpierw refaktor bez zmiany zachowania: pętla ponowień z `WykopApiClient.exchange` wychodzi do `sources/transport/HttpRetry` z polityką parametryzowaną predykatem statusów i listą nagłówków czekania; `WykopApiClientTest` zostaje bajt w bajt i pilnuje, że liczby żądań, czekania i komunikaty się nie zmieniły. `UnmappableItemException` przechodzi do `sources/`. W tej samej fazie wspólny model dostaje `channel` (migracja V13 z backfillem `gielda`, `SourcePost.channel`, `Post.channel`, Wykop wypełnia `TAG`), żeby Reddit w fazie 3 tylko wpisał subreddit.
2. Potem Reddit warstwami jak Wykop: transport i stronicowanie (`RedditApiClient`, `RedditRepository`, `RedditItem`) na lokalnym serwerze i prawdziwych, zanonimizowanych fixture'ach; mapowanie i źródło (`RedditMapper`, `RedditPosts`, `RedditFetcher`, `RedditConfiguration`) z testami jednostkowymi, jeszcze bez rejestracji w Springu.
3. Rejestracja beanów razem z przepięciem testów silnika: `ScriptedSourceConfiguration` znika, `LocalReddit` (bean `RedditRepository` nad `LocalRedditServer`, lista subredditów przełączana per scenariusz) wchodzi do `PipelineRunIT`, `RunWatchdogIT`, `MissingDaysIT`; bramka F1 z S-20 dowodzi obsługi formatu Reddita na produkcyjnym mapperze.
4. Na końcu IaC, CI, dokumenty, runbooki i dowód w PR env na prawdziwym Arctic Shift. Każda faza kończy się zielonym `./mvnw -B test` (fazy 1–3) albo `./mvnw -B verify` (fazy 4–5) i commitem; merge wykonuje człowiek po `/10x-impl-review` i `/10x-archive` na tej samej gałęzi.

## Critical Implementation Details

- **Kolizja beanów (kolejność faz).** Faza 3 pisze `RedditPosts` i `RedditFetcher` bez `@Component`, a `RedditConfiguration` testuje `ApplicationContextRunner` w izolacji; adnotacje `@Component` i import `LocalReddit` w miejsce `ScriptedSourceConfiguration` lądują razem w fazie 4, bo z `@Component` konteksty `PipelineRunIT`, `RunWatchdogIT` i `MissingDaysIT` padają na duplikacie klucza `Source.REDDIT`. Żaden test Springa nie może ustawiać `REDDIT_SUBREDDITS`, inaczej obok beana `LocalReddit` powstanie produkcyjny `RedditRepository` i wstrzyknięcie `Optional<RedditRepository>` stanie się niejednoznaczne.
- **Wyłączne granice czasu.** `after`/`before` Arctic Shift wykluczają równą sekundę: pierwsza strona subredditu to `after = from.epochSecond − 1`, `before = to.epochSecond + 1`; następna strona to `after = created_utc ostatniego elementu − 1` (ta sama sekunda czytana ponownie), a elementy już widziane po `id` są pomijane. Bez zakładki post z tą samą sekundą na granicy strony ginie.
- **`fetched` liczy tylko elementy pobrania.** Posty zdjęte/skasowane repozytorium wyłącza przed policzeniem (nie są w `fetched`, `skipped` ani RAW), bo `skipped > 0` daje `RUN_NEEDS_ATTENTION` co noc; liczba wyłączonych idzie do logu INFO per subreddit.
- **Deklaracja przed wartością (S-14).** `REDDIT_SUBREDDITS: preserve()` w `railway.ts` i krok CI ustawiający ją w PR env wchodzą w tym samym PR; krok CI stoi przed „Plan and gate”, więc plan PR env zachowuje wartość. Na produkcji nic nie jest ustawiane w tym PR.

## Phase 1: Wspólna pętla ponowień w `sources/` — refaktor Wykopu bez zmiany zachowania

### Overview

`HttpRetry` w `sources/transport/` przejmuje `exchange` z `WykopApiClient`; `UnmappableItemException` przechodzi do `sources/`; wspólny model dostaje `channel` (migracja V13, port, encja, Wykop). Zachowanie klienta Wykopu bez zmian, co pilnuje niezmieniony `WykopApiClientTest`. Na początku fazy, przed pierwszym commitem kodu: `git fetch` i potwierdzenie, że `feature/reddit-source` stoi na `origin/main` (dziś `5a013ca`; inaczej rebase), PR z `Refs #121`, komentarz w issue #121 (id zmiany, gałąź, PR); pierwszy commit to folder zmiany (`change.md`, `research.md`, `plan.md`, `plan-brief.md`) razem ze statusem `in-progress` S-27 w `context/foundation/roadmap.md` (tabela i blok) oraz w `tools/render_roadmap_svg.py` z regeneracją `docs/visuals/roadmap.svg`.

### Changes Required:

#### 1. Wspólna pętla ponowień

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/transport/HttpRetry.java` (nowy)

**Intent**: Jedna pętla ponowień dla klientów źródeł (dziś Wykop, od fazy 2 Reddit), żeby drugie źródło nie kopiowało jej trzeci raz (`sources/CLAUDE.md:44-47`, plan-review S-16 F6); komunikaty i maskowanie zostają w kliencie źródła.

**Contract**:
- `public final class HttpRetry` z `public interface Sleeper { void sleep(Duration) throws InterruptedException; Sleeper REAL = Thread::sleep; }` (przeniesiony z `WykopApiClient`), stałymi `MAX_ATTEMPTS = 3`, `MAX_RETRY_AFTER = 30 s`, `FIRST_BACKOFF = 2 s`.
- Konstruktor `HttpRetry(String source, IntPredicate retriedStatus, List<String> waitHeaders, Sleeper sleeper, InstantSource clock)`: `source` to nazwa do komunikatów („Wykop”, „Reddit”), `retriedStatus` decyduje, który status jest ponawiany, `waitHeaders` to nagłówki czekania w kolejności (Wykop: `Retry-After`; Reddit: `Retry-After`, `X-RateLimit-Reset`), pierwszy czytelny wygrywa; wartość w sekundach albo data RFC-1123 (przeszła = bez czekania), nieczytelna = backoff 2 s × 2^(n−1).
- `HttpResponse<String> exchange(HttpClient http, HttpRequest request, String call, Duration timeout, Instant deadline, UnaryOperator<String> clean)`: zwraca pierwszą odpowiedź o statusie nieponawianym; przed każdą próbą i każdym czekaniem sprawdza, że próba (`timeout`) zmieści się przed `deadline` (semantyka dzisiejszego `requireTime`); `IOException` jest ponawiany jak status; każde ponowienie to jeden WARN `Retrying <source> <call> in <wait> after attempt <n> of 3: <description>` przepuszczony przez `clean`; wyczerpane próby, termin i przerwanie kończą `HttpRetryException` z `Kind { HTTP_STATUS, TRANSPORT, DEADLINE_EXCEEDED, INTERRUPTED }`, `status` (0 poza `HTTP_STATUS`), `attempts` i opisem po `clean`; przerwanie zachowuje flagę wątku. Opisy błędów w dzisiejszym brzmieniu Wykopu (`… answered HTTP <status>: <body>`, `… failed: <ex>`, `… reached its deadline after <n> attempt(s): …`, `… after 3 attempts`), żeby `WykopApiClientTest` przeszedł bez zmian.
- `HttpRetryTest` (nowy, bez Springa, lokalny `HttpServer`, fałszywy `Sleeper` i `InstantSource`): predykat statusów (422 ponawiane tylko gdy predykat tak mówi), kolejność nagłówków czekania (`X-RateLimit-Reset` użyty bez `Retry-After`), limit 30 s, termin przed pierwszą próbą i przed czekaniem, przerwanie z zachowaną flagą, liczby żądań.

#### 2. Klient Wykopu na wspólnej pętli

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java`

**Intent**: Usunąć własną pętlę i delegować do `HttpRetry`; auth, odświeżenie tokena po 401, `requireSuccess`, `parse`, `clean` i `toString` zostają.

**Contract**: publiczne konstruktory bez zmian poza typem `Sleeper` (`HttpRetry.Sleeper`; lambdy w testach kompilują się bez zmian); `WykopFailure` i teksty wyjątków bez zmian; `HttpRetryException` mapowany na `WykopApiException` tego samego rodzaju (`HTTP_STATUS` z `httpStatus(status, message)`, `TRANSPORT`, `DEADLINE_EXCEEDED`, `INTERRUPTED`); w pliku nie ma już `for (int attempt`, `requireTime`, `wait(` ani `retryAfter(`. `WykopApiClientTest` niezmieniony i zielony — to jest dowód braku zmiany zachowania.

#### 3. Wyjątek elementu niemapowalnego w `sources/`

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/UnmappableItemException.java` (przeniesiony z `sources/wykop/`), importy w `WykopMapper`, `WykopPosts`, `WykopFetcher` i ich testach

**Intent**: Pojęcie wspólne dla każdego źródła (audyt S8); Reddit używa go w fazie 3.

**Contract**: ta sama klasa i konstruktor, pakiet `pl.szczurradar.api.sources`; javadoc bez odniesienia do Wykopu.

#### 4. Reguły pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md`

**Intent**: Przepis i tabela układu mówią o wspólnej pętli, nie o kopiowaniu.

**Contract**: krok 5 przepisu: ponowienia klienta idą przez `sources/transport/HttpRetry` (statusy per źródło, 3 próby, czekanie z nagłówków ≤ 30 s albo 2 s / 4 s, termin repozytorium); wiersz `<Source>ApiClient` w tabeli wskazuje `HttpRetry`; `UnmappableItemException` w `sources/`; wiersz `<Source>Posts` i reguły tekstu: `SourcePost.channel` to kanał źródła (tag / subreddit), ten sam co `channels[0]` wejścia oceny.

#### 5. Kolumna kanału

**File**: `szczur-radar-api/src/main/resources/db/migration/V13__posts_channel.sql` (nowy), `tools/db-backup/test.sh` (seed testu)

**Intent**: Kanał, z którego post przyszedł, wprost w `posts` (decyzja właściciela 2026-10-29), żeby feed, pomiary i S-29 nie musiały sięgać do `assessments.input` ani RAW.

**Contract**: `ALTER TABLE posts ADD COLUMN channel TEXT`; backfill `UPDATE posts SET channel = 'gielda' WHERE source = 'wykop'` (jedyny kanał Wykopu w kodzie: `WykopPosts.TAG`; wierszy `reddit` nie ma w żadnym środowisku); `ALTER TABLE posts ALTER COLUMN channel SET NOT NULL`; `CONSTRAINT ck_posts_channel CHECK (channel <> '')` (zbiór otwarty: subreddity, bez enumu); komentarz nagłówka: znaczenie (kanał w nazwach źródła: tag Wykopu bez `#`, nazwa subredditu jak w payloadzie), źródło backfillu, fix-forward. Bez nowej tabeli (`tools/db-backup/tables.sh` bez zmian).

**Seed testu backupu**: `tools/db-backup/test.sh` stosuje wszystkie migracje przed przygotowaniem danych. Jego `INSERT INTO posts` musi jawnie wypełniać `channel` wartością `'gielda'`, żeby seed działał po V13 (`NOT NULL`, bez wartości domyślnej).

#### 6. Kanał we wspólnym modelu i w Wykopie

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/SourcePost.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/Post.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/PostStore.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopPosts.java`, testy: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/PostColumnsIT.java`, `persistence/JpaSchemaIT.java`, `MigrationsIT.java`, `sources/ScriptedSourceFetcher.java`, `posts/PostSeeds.java` i każdy test budujący `SourcePost`/`Post`

**Intent**: Port i encja niosą kanał; Wykop wypełnia go stałą `TAG`; strażnicy schematu i kolumn znają nową kolumnę.

**Contract**: `SourcePost` dostaje pole `String channel` (po `sourceItemId`, null-check; „kanał źródła, ten sam co `channels[0]` wejścia oceny”); `Post` dostaje kolumnę `channel` (`@Column(nullable = false)`, parametr konstruktora po `sourceItemId`, U+0000 usuwane jak w innych polach tekstowych, getter); `PostStore.save` przekazuje `post.channel()`; `WykopPosts.post` wpisuje `TAG`; `PostColumnsIT.SET_ONCE` dostaje `channel`; `MigrationsIT`: V13 nadaje historycznym wierszom Wykopu `gielda`, pusty `channel` odrzucony; `JpaSchemaIT` i `PostRepositoryIT` zielone bez zmian poza konstruktorami. `PostItem` API bez zmian (S-29).

### Success Criteria:

#### Automated Verification:

- Testy Wykopu i pętli zielone: `cd szczur-radar-api && ./mvnw -B test -Dtest='WykopApiClientTest,WykopRepositoryTest,WykopFetcherTest,WykopMapperTest,WykopConfigurationTest,HttpRetryTest'`
- `WykopApiClientTest` niezmieniony: `git diff --stat origin/main -- szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopApiClientTest.java` nie wypisuje nic
- Pętla istnieje raz: `rg -n 'for \(int attempt' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/` znajduje tylko `sources/transport/HttpRetry.java`
- Wyjątek przeniesiony: `test -f szczur-radar-api/src/main/java/pl/szczurradar/api/sources/UnmappableItemException.java && ! test -e szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/UnmappableItemException.java`
- Cała jednostkowa część zielona: `cd szczur-radar-api && ./mvnw -B test`
- Kolumna kanału w schemacie, modelu i strażnikach: `rg -n 'channel' szczur-radar-api/src/main/resources/db/migration/V13__posts_channel.sql szczur-radar-api/src/main/java/pl/szczurradar/api/sources/SourcePost.java szczur-radar-api/src/main/java/pl/szczurradar/api/posts/Post.java szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/PostColumnsIT.java` znajduje każdy z czterech plików
- Schemat, encje i backfill zielone z Testcontainers: `cd szczur-radar-api && ./mvnw -B verify`
- Test backupu po migracji kanału zielony z Dockerem: `tools/db-backup/test.sh`

**Implementation Note**: Faza bez wierszy Manual: refaktor bez zmiany zachowania, którego dowodem jest niezmieniony test klienta.

---

## Phase 2: Transport i stronicowanie Arctic Shift

### Overview

Klient i repozytorium Reddita nad Arctic Shift, element w nazwach Reddita, lokalny serwer testowy i prawdziwe, zanonimizowane fixture'y. Nic jeszcze nie jest beanem; nic w silniku się nie zmienia.

### Changes Required:

#### 1. Fixture'y Arctic Shift

**File**: `szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-inwestowanie.json`, `szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-stocks.json`, `szczur-radar-api/src/test/resources/fixtures/reddit/README.md` (nowe)

**Intent**: Prawdziwe odpowiedzi `GET /api/posts/search` jako podstawa testów klienta, repozytorium, mappera i bramki F1 (`sources/CLAUDE.md:100-104`, `roadmap.md:242`).

**Contract**: dwie nagrane odpowiedzi (adres z `after`/`before` dnia 2026-10-27 czasu Warszawy, `sort=asc`, `limit` ≤ 25, pełne obiekty bez `fields`): r/inwestowanie (posty po polsku, w tym `is_self=false` z niepustym `selftext` i bez `selftext`, bez zdjętych) oraz r/stocks (posty zdjęte z `removed_by_category` `moderator`/`reddit` i `selftext` `[removed]` obok widocznych). Anonimizacja: `author` → `uzytkownikN` (stały numer na nick w pliku), `author_fullname` → `t2_uzytkownikN`, `profile_img`, `thumbnail` i inne adresy awatarów → `<url>`, `media`, `secure_media`, `preview`, `media_metadata`, `gallery_data` → `<media>`; `id`, `permalink`, `url`, `subreddit`, `created_utc`, `title`, `selftext`, `removed_by_category`, `_meta` bez zmian (publiczne identyfikatory i treść). Błędy i koperty błędów (`{"data":null,"error":"Timeout. Maybe slow down a bit"}`) jako krótki syntetyczny JSON w teście. `README.md` fixture'ów: data nagrania, dokładne adresy, co zanonimizowano i jak (polecenie `jq`), liczby postów i zdjętych.

#### 2. Klient Arctic Shift

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditApiClient.java`, `RedditApiException.java`, `RedditFailure.java` (nowe)

**Intent**: Transport do Arctic Shift bez klucza, z ponowieniami przez `HttpRetry` i terminem z repozytorium; jedno żądanie → sparsowana koperta.

**Contract**: `DEFAULT_BASE_URL = https://arctic-shift.photon-reddit.com/`, `DEFAULT_TIMEOUT = 30 s`; konstruktory `()`, `(URI baseUrl, Duration timeout)` i `(URI, Duration, HttpRetry.Sleeper, InstantSource)`; nagłówki `User-Agent: szczur-radar (+https://github.com/kwmacioszek/szczur-radar)` i `Accept: application/json`, bez `Authorization`; `Instant deadline(Duration budget)`; `JsonNode get(String path, Map<String, ?> query, Instant deadline)` zwraca węzeł `data` (tablica) odpowiedzi 2xx; ponawiane: 429, 422, 500–599 i błędy transportu, czekanie z `Retry-After` albo `X-RateLimit-Reset` (sekundy) ≤ 30 s, inaczej 2 s / 4 s, nigdy poza `deadline`; inne 4xx → `RedditApiException(HTTP_STATUS, status)` od razu; 2xx z `data` równym `null` albo nie-tablicą, z polem `error`, albo nie-JSON → `MALFORMED_ANSWER`; `RedditFailure { TRANSPORT, HTTP_STATUS, MALFORMED_ANSWER, PAGE_LIMIT, INCOMPLETE_PAGING, INTERRUPTED, DEADLINE_EXCEEDED }`; komunikaty `Reddit GET <path> answered HTTP <status>: <body ≤ 500 znaków>` (bez sekretów, bo ich nie ma); `toString` z `baseUrl`. `queryString` jak w Wykopie (bez list).

#### 3. Element i wynik wyszukiwania

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditItem.java`, `RedditSearchResult.java` (nowe)

**Intent**: Pola potrzebne do mapowania i wyłączania, w nazwach Reddita; element obok surowego `JsonNode`.

**Contract**: `record RedditItem(@JsonProperty("id") String id, String subreddit, String title, String selftext, @JsonProperty("is_self") Boolean isSelf, String url, String permalink, String author, @JsonProperty("created_utc") Long createdUtc, @JsonProperty("removed_by_category") String removedByCategory)` z `@JsonIgnoreProperties(ignoreUnknown = true)`; `String webUrl()` = `https://www.reddit.com` + `permalink` (null gdy brak permalinku); `boolean removed()` = `removedByCategory != null` albo `selftext` równy dokładnie `[removed]` lub `[deleted]`. `record RedditSearchResult(List<Element> elements, int skippedElements, int removed)` z `record Element(RedditItem item, JsonNode raw)`.

#### 4. Repozytorium: stronicowanie po czasie

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditRepository.java` (nowy)

**Intent**: Wszystkie posty wybranych subredditów z zakresu, każdy raz, bez dziur na granicach stron, w terminie poniżej watchdoga; posty zdjęte poza wynikiem (decyzja właściciela).

**Contract**:
- Stałe: `PAGE_SIZE = 100`, `MAX_PAGES = 50` (na subreddit), `DEFAULT_PAUSE = 300 ms`, `FETCH_DEADLINE = 10 min` (całe pobranie, wszystkie subreddity; `FETCH_DEADLINE + 2 × RedditApiClient.DEFAULT_TIMEOUT < PipelineStallSettings.MIN_DEFAULT`, przypięte testem jak `WykopRepositoryTest:318-323`).
- Klasa nie-`final`; konstruktory `(RedditApiClient client, List<String> subreddits)` i `(client, subreddits, Pause pause, Duration between)` (`Pause` jak w `WykopRepository`); `List<String> subreddits()` (niemodyfikowalna kopia; `LocalReddit` w fazie 4 nadpisze ją w podklasie).
- `RedditSearchResult postsCreatedBetween(Instant from, Instant to)`: dla każdego subredditu po kolei `GET api/posts/search` z `subreddit=<nazwa jak podana>`, `after=<from.getEpochSecond() − 1>`, `before=<to.getEpochSecond() + 1>`, `sort=asc`, `limit=100`; kolejna strona, gdy poprzednia miała 100 elementów: `after = created_utc ostatniego − 1`; elementy kluczowane `id` (duplikat z zakładki pomijany bez liczenia); strona krótsza niż 100 kończy subreddit; strona pełna bez żadnego nowego `id` albo z `created_utc` malejącym względem poprzedniego elementu → `INCOMPLETE_PAGING`; `MAX_PAGES` → `PAGE_LIMIT`; pauza między stronami i między subredditami; termin `client.deadline(FETCH_DEADLINE)` przekazywany do każdego `get`.
- Element bez `id` (string), bez `created_utc` (liczba ≥ 0) albo bez `subreddit` → `skippedElements` (nieoczekiwany kształt); element `removed()` → `removed`, poza `elements`; INFO per subreddit `Reddit r/<name>: <n> post(s) in range, <r> removed or deleted left out, <s> of unexpected shape`.
- Błąd klienta po ponowieniach, termin i przerwanie kończą pobranie wyjątkiem źródła; nigdy wynik częściowy jako kompletny.

#### 5. Lokalny serwer i testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/LocalApiServer.java` (nowy: ciało dzisiejszego `LocalWykopServer` z `static String fixture(String dir, String name)`), `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/LocalWykopServer.java` (zostaje jako cienka podklasa: konstruktor delegujący i `fixture(name)` dla `fixtures/wykop/`, bez zmian w jego użyciach), `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/reddit/LocalRedditServer.java` (podklasa z `fixture(name)` dla `fixtures/reddit/`), `RedditApiClientTest.java`, `RedditRepositoryTest.java` (nowe)

**Intent**: Testy klienta i repozytorium bez sieci, z liczbami żądań jako dowodem ponowień i stronicowania.

**Contract**: `RedditApiClientTest`: ścieżka, query, `User-Agent`, brak `Authorization`; 5xx → ponowienie → sukces; 5xx × 3; 429 z `X-RateLimit-Reset` (nagrane czekanie); 422 ponowione; 400 i 404 raz; `{"data":null,"error":…}` jako `MALFORMED_ANSWER`; termin przed próbą; przerwanie z flagą; błąd połączenia × 3. `RedditRepositoryTest`: dwa subreddity w kolejności i z `after`/`before` o sekundę szerszymi; dwie strony z elementem o tej samej sekundzie na granicy policzonym raz; strona krótsza kończy; pełna strona bez nowego `id` → `INCOMPLETE_PAGING`; `MAX_PAGES` → `PAGE_LIMIT`; zdjęte i skasowane poza `elements` i policzone; element bez `id`/`created_utc` → `skippedElements`; pauzy nagrane; `aFetchEndsBeforeTheWatchdogsShortestDefault`; błąd klienta kończy pobranie bez wyniku. Strony ponad nagranie buduje test z fixture'a przez przenumerowanie `id` i `created_utc`, z komentarzem co zmieniono.

### Success Criteria:

#### Automated Verification:

- Testy transportu Reddita zielone: `cd szczur-radar-api && ./mvnw -B test -Dtest='RedditApiClientTest,RedditRepositoryTest,HttpRetryTest,Wykop*Test'`
- Fixture'y są prawdziwe i zanonimizowane: `for f in szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-*.json; do jq -e '.data | length >= 10' "$f" >/dev/null && test -z "$(jq -r '.data[].author' "$f" | grep -v -E '^(uzytkownik[0-9]+|\[deleted\])$')"; done`
- Fixture r/stocks ma posty zdjęte i widoczne: `jq -e '[.data[] | select(.removed_by_category != null)] | length > 0' szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-stocks.json && jq -e '[.data[] | select(.removed_by_category == null)] | length > 0' szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-stocks.json`
- Termin pobrania przypięty poniżej watchdoga: `rg -n 'aFetchEndsBeforeTheWatchdogsShortestDefault' szczur-radar-api/src/test/java/pl/szczurradar/api/sources/reddit/RedditRepositoryTest.java`
- Cała jednostkowa część zielona: `cd szczur-radar-api && ./mvnw -B test`

---

## Phase 3: Mapowanie i źródło (klasy bez rejestracji w Springu)

### Overview

`RedditMapper`, `RedditPosts`, `RedditFetcher` i `RedditConfiguration` z testami jednostkowymi. `@Component` na `RedditPosts` i `RedditFetcher` dopiero w fazie 4 (kolizja z `ScriptedSourceConfiguration`); `RedditConfiguration` jest już `@Configuration`, ale bez zmiennej nie tworzy beana, więc żaden kontekst się nie zmienia.

### Changes Required:

#### 1. Mapper

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditMapper.java`, `RedditMapperTest.java` (nowe)

**Intent**: `RedditItem` → `AssessmentInputV1` wg kontraktu §2 i decyzji właściciela (`is_self → post`, inaczej `link`), tekst bez zmian poza U+0000, puste → `null`.

**Contract**: `static AssessmentInputV1 map(RedditItem item)`: `isSelf == true` → `ItemType.POST`, `false`/`null` → `ItemType.LINK`; `title = textOrNull(title)`, `body = textOrNull(selftext)` (`stored = Nul.removed(text)`; gdy `stored == null` albo `Whitespace.normalize(stored).isEmpty()`, zwracamy `null`; w przeciwnym razie zwracamy `stored` — normalizacja służy wyłącznie do sprawdzenia pustki, jak `WykopMapper:82-85`); `selftext` równy `[removed]`/`[deleted]` → `UnmappableItemException` (defensywnie; repozytorium już je wyłącza); oba `null` → `UnmappableItemException`; `createdUtc` `null` albo ujemne → `UnmappableItemException`; `published_at = OffsetDateTime.ofInstant(Instant.ofEpochSecond(createdUtc), ZoneOffset.UTC)`; `channels = List.of(subreddit)` (nazwa z payloadu); `Context.NONE`; żadnego `url`, `author`, `id` w wejściu. Test: self-post z tytułem i treścią → `post`; link-post z `selftext` → `link` z `body`; link-post bez `selftext` → `link` z samym `title`; białe znaki → `null`; `[removed]` → wyjątek; U+0000 usunięte, twarde spacje i markdown zachowane; znacznik styczniowy i lipcowy 2026 oba z offsetem `Z`; brak `created_utc` → wyjątek.

#### 2. Posty i mapper RAW

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditPosts.java`, `RedditPostsTest.java` (nowe)

**Intent**: `SourcePost` z elementu i wejście oceny z zapisanego RAW przez ten sam mapper (`sources/CLAUDE.md:41-43`).

**Contract**: `public class RedditPosts implements RawInputMapper` (bez `@Component` do fazy 4); `source() = Source.REDDIT`; `map(byte[] raw) = RedditMapper.map(JSON.readValue(raw, RedditItem.class))`; `static SourcePost post(RedditSearchResult.Element element)`: `id` `null` → `UnmappableItemException`; `new SourcePost(REDDIT, input.item().itemType(), item.id(), channel = item.subreddit() (jak w payloadzie, ten sam co `channels[0]`), input.item().publishedAt().toInstant(), title, body, author (null gdy `null` albo `[deleted]`), slug null, item.webUrl(), List.of(subreddit), JSON.writeValueAsBytes(element.raw()))`. Test na fixture: `map(post(e).raw())` równe `RedditMapper.map(e.item())` dla każdego elementu; `channel` równy `channels[0]` wejścia; `webUrl` zaczyna się od `https://www.reddit.com/r/`; `tags` = `[subreddit]`.

#### 3. Fetcher

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditFetcher.java`, `RedditFetcherTest.java` (nowe)

**Intent**: Port `SourceFetcher` dla Reddita: zakres w sekundach UTC, `skipped` z elementów niemapowalnych, zdjęte poza `fetched`.

**Contract**: `public class RedditFetcher implements SourceFetcher` (bez `@Component` do fazy 4), konstruktor `(Optional<RedditRepository> repository)`; `source() = REDDIT`; `configured() = repository.map(r -> !r.subreddits().isEmpty()).orElse(false)`; `fetch(from, to)`: nieskonfigurowane → `IllegalStateException("Reddit is not configured (REDDIT_SUBREDDITS)")`; `repository.postsCreatedBetween(from, to)`; każdy element przez `RedditPosts.post`, `UnmappableItemException` → `skipped++`; `new Fetched(posts, result.elements().size() + result.skippedElements(), result.skippedElements() + skipped)`; `removed` nie wchodzi do żadnego licznika. Test z mockiem repozytorium: zakres przekazany bez zmian; posty i `skipped`; pusta lista subredditów = nieskonfigurowane; brak repozytorium = nieskonfigurowane i wyjątek przy `fetch`.

#### 4. Konfiguracja

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditConfiguration.java`, `RedditConfigurationTest.java` (nowe)

**Intent**: Bean `RedditRepository` tylko z niepustej `REDDIT_SUBREDDITS`; nazwy sprawdzone przy starcie (nie sekret, wolno je wypisać).

**Contract**: `@Configuration(proxyBeanMethods = false)`, `public static final String SUBREDDITS = "REDDIT_SUBREDDITS"`; `@Bean @Conditional(SubredditsSet.class) RedditRepository redditRepository(Environment)`: wartość `strip()` (wzorzec `WykopConfiguration.text`), rozdzielona po `,`, każdy element `strip()`, opcjonalny przedrostek `r/` zdjęty, wielkość liter zachowana (Arctic Shift może rozróżniać wielkość liter; `channels` i tak bierze nazwę z payloadu), duplikaty po małych literach usunięte, każda nazwa musi pasować do `[A-Za-z0-9_]{2,21}`; lista pusta po tych krokach albo zła nazwa → `IllegalStateException("Invalid Reddit configuration: REDDIT_SUBREDDITS … See szczur-radar-api/README.md (Reddit).")` z nazwą winnego elementu; bean = `new RedditRepository(new RedditApiClient(), names)`. `SubredditsSet` dopasowuje, gdy wartość nie jest pusta. Test `ApplicationContextRunner`: bez zmiennej brak beana; `stocks, r/ValueInvesting ,investing,STOCKS` → `[stocks, ValueInvesting, investing]`; `" , "` → start pada z nazwą zmiennej; `r/bad name` → start pada z nazwą elementu.

### Success Criteria:

#### Automated Verification:

- Testy mapowania i źródła zielone: `cd szczur-radar-api && ./mvnw -B test -Dtest='Reddit*Test'`
- Nic nie jest jeszcze beanem: `! rg -n '@Component' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/`
- Wejście z RAW równe wejściu z elementu dla każdego posta fixture'a (`RedditPostsTest` zielony): `cd szczur-radar-api && ./mvnw -B test -Dtest='RedditPostsTest'`
- Cała jednostkowa część zielona: `cd szczur-radar-api && ./mvnw -B test`
- Kanał Reddita to subreddit: `rg -n 'subreddit\(\)' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditPosts.java` i `rg -n 'channel' szczur-radar-api/src/test/java/pl/szczurradar/api/sources/reddit/RedditPostsTest.java`

---

## Phase 4: Rejestracja i testy silnika na prawdziwym Reddicie

### Overview

`RedditPosts` i `RedditFetcher` stają się beanami; `ScriptedSourceConfiguration` znika; `LocalReddit` daje testom silnika drugie źródło na prawdziwym kliencie, repozytorium i mapperze nad `LocalRedditServer`; bramka F1 z S-20 i nowe scenariusze; JSON `/sources` z dwoma wpisami; `CLAUDE.md` obu pakietów mówią prawdę.

### Changes Required:

#### 1. Rejestracja beanów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditPosts.java`, `RedditFetcher.java`

**Intent**: Reddit widoczny dla silnika: w `/sources` jako `configured:false` bez zmiennej, skonfigurowany z nią.

**Contract**: `@Component` na obu; `RedditFetcher` wstrzykuje `Optional<RedditRepository>` jak `WykopFetcher:23-27`.

#### 2. Drugie źródło testów silnika

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/reddit/LocalReddit.java` (nowy), `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/ScriptedSourceConfiguration.java` (usunięty), `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/ScriptedSourceFetcher.java` (javadoc: tylko testy jednostkowe silnika)

**Intent**: Zastąpić dublet pod `Source.REDDIT` produkcyjnym stosem nad lokalnym serwerem, z przełączaniem „skonfigurowane” per scenariusz (`roadmap.md:242`).

**Contract**: `public @TestConfiguration(proxyBeanMethods = false) class LocalReddit` ze statycznym `LocalRedditServer` (`answers`: domyślnie fixture r/inwestowanie dla każdego `api/posts/search`; przełączniki `volatile`: `down` → 500 z kopertą błędu, `empty` → `{"data":[]}`, `page(String fixture)`, `hold` (zatrzask bez ograniczenia, `release()`), `delay(Duration)` tylko dla watchdoga) i beanem `RedditRepository` = anonimowa podklasa nad `new RedditApiClient(server.baseUrl(), 5 s, wait -> {}, InstantSource.system())`, `Pause` bez czekania, której `subreddits()` zwraca listę scenariusza (`configure(List<String>)`, domyślnie pusta = nieskonfigurowane); `reset()` przywraca stan początkowy i zwalnia zatrzask; `requests()`/`calls()` do liczenia żądań. Żaden test nie ustawia `REDDIT_SUBREDDITS`.

#### 3. `PipelineRunIT`

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java`

**Intent**: Scenariusze dwóch źródeł na prawdziwym Reddicie; bramka F1; zdjęte posty poza licznikami.

**Contract**: `@Import` z `LocalReddit.class` zamiast `ScriptedSourceConfiguration.class`; `reset()` i `settle()` wołają `LocalReddit.reset()`/`release()`; scenariusze: źródło rzucające → `LocalReddit.down()` (`reddit failed: RedditApiException: …` po 3 próbach bez czekania); puste → `empty` (`reddit:empty:0:0:`); Wykop padł → `aWykopOutageLeavesTheRedditPostAssessedFromItsRawPayload`: `partial`, `source_failed`, wiersz `reddit:succeeded:<n>:0:`, dla posta Reddita `stage = assessed`, a zapisane `assessments.input` równe `ContractJson` z `RedditMapper.map(item)` dla elementu fixture'a o tym `source_item_id`, `item/body` równe `selftext` fixture'a, `item/channels` `["inwestowanie"]`, `posts.channel` równe `inwestowanie`, `item/item_type` zgodne z `is_self`; oba bez posta; przebieg zatrzymany, gdy Reddit trzymany (`hold`; INFO `reddit ended with the run's stop`); nowy scenariusz `removedRedditPostsAreNeitherFetchedNorSkipped`: `page("search-posts-stocks.json")` → `fetched` = liczba widocznych, `skipped 0`, `attentionReasons` bez `SKIPPED`, w `posts` tylko widoczne, w logu linia `removed or deleted left out`; nowy scenariusz dedup: ten sam zakres drugi raz → `new_posts 0` dla Reddita (jeśli istniejący scenariusz „the same range again” go nie obejmuje, rozszerzyć go).

**Asercja kanału Wykopu**: w istniejącym scenariuszu `aSourceThatFailsLeavesTheOtherSourcesPostsAssessedAndTheRunPartial` (Reddit zawodzi, Wykop dostarcza posty) sprawdzić, że zapisane posty Wykopu z tego przebiegu mają `channel = gielda`. Scenariusz awarii Wykopu sprawdza wyłącznie kanał Reddita; nie wymaga postów Wykopu.

#### 4. `RunWatchdogIT`, `MissingDaysIT`

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/RunWatchdogIT.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/MissingDaysIT.java`

**Intent**: Te same scenariusze na `LocalReddit`.

**Contract**: `RunWatchdogIT`: scenariusz dwóch źródeł z `LocalReddit.delay(3 s)` i liczeniem żądań serwera zamiast `awaitCalled`; `MissingDaysIT`: `LocalReddit.configure(List.of("stocks"))` w scenariuszu z Redditem, `reset()` w `@BeforeEach`; oczekiwane JSON-y bez zmian.

#### 5. Testy `/sources`

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java`, `PipelineRunWithoutSourceIT.java`

**Intent**: Reddit widoczny jako zarejestrowane, nieskonfigurowane źródło.

**Contract**: `theSourcesListSaysWhichAreConfigured` → `[{"source":"wykop","configured":true},{"source":"reddit","configured":false}]`; `PipelineRunWithoutSourceIT` → `[{"source":"wykop","configured":false},{"source":"reddit","configured":false}]`; `aRegisteredSourceWithoutAFetcherIsNotConfigured` przemianowany na `aRegisteredSourceWithoutItsSettingIsNotConfigured` z komentarzem „fetcher bez `REDDIT_SUBREDDITS`”, nadal 503.

#### 6. Reguły pakietów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: Dokumenty opisują Reddit jako drugą implementację referencyjną i testy silnika na `LocalReddit`.

**Contract**: `sources/CLAUDE.md`: krok 1 (oba źródła zarejestrowane; nowe źródło nadal dodaje migrację), krok 7 (testy dwóch źródeł: Wykop na `LocalWykopServer`, Reddit na `LocalReddit`, `ScriptedSourceFetcher` tylko w `SourceIngestTest`), tabela układu z uwagami Reddita (stronicowanie po czasie z zakładką zamiast `total`; `configured()` z listy subredditów; posty zdjęte poza elementami — stan elementu, nie filtr treści), sekcja Tests (fixture'y `fixtures/reddit/`, `LocalApiServer`), Live use (`RedditConfiguration`, `REDDIT_SUBREDDITS`). `pipeline/CLAUDE.md`: akapity `PipelineRunIT` (`:586-597`), `RunWatchdogIT` (`:566`) i `SourceIngestTest` (`:470`) bez `ScriptedSourceConfiguration`, z F1 i scenariuszem zdjętych postów.

### Success Criteria:

#### Automated Verification:

- Cały backend zielony z Testcontainers: `cd szczur-radar-api && ./mvnw -B verify`
- Dubletu nie ma: `! rg -n 'ScriptedSourceConfiguration' szczur-radar-api/src REPOSITORY_GUIDELINES.md`
- Bramka F1 istnieje i sprawdza wejście z fixture'a Reddita: `rg -n 'aWykopOutageLeavesTheRedditPostAssessedFromItsRawPayload|RedditMapper.map' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java`
- Zdjęte posty poza licznikami: `rg -n 'removedRedditPostsAreNeitherFetchedNorSkipped' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java`
- Reddit widoczny w `/sources`: `rg -n -F '\"source\":\"reddit\",\"configured\":false' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunWithoutSourceIT.java` znajduje oba pliki
- Reguły pakietów wymieniają `LocalReddit`: `rg -n 'LocalReddit' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` znajduje oba pliki
- Kanał zapisany per źródło w przebiegu dwóch źródeł: `rg -n "channel" szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java` znajduje asercje `inwestowanie` i `gielda`

**Implementation Note (2026-10-30, impl-review F1)**: Pierwotne polecenie kontroli 4.5 miało nadmiarowe backslashe we wzorcu regex i zwracało kod 1, mimo obecności asercji. Poprawione powyżej wyszukiwanie literalne `rg -n -F` zwraca kod 0 i znajduje oba pliki: `PipelineRunControllerIT.java:159` oraz `PipelineRunWithoutSourceIT.java:80`. Historyczny tytuł i odhaczenie 4.5 w Progress pozostają bez zmian; ta nota prostuje dowód jego weryfikacji.

---

## Phase 5: IaC, CI, dokumentacja, weryfikacja w PR env

### Overview

Zmienna zadeklarowana w IaC i ustawiana w PR env z CI, dokumenty i runbooki (włączenie po S-28, koniec Arctic Shift), doprecyzowania PRD, dowód na prawdziwym Arctic Shift w PR env, komentarz w issue #121. Merge wykonuje człowiek po `/10x-impl-review` i `/10x-archive`.

### Changes Required:

#### 1. IaC

**File**: `.railway/railway.ts`

**Intent**: Zadeklarować `REDDIT_SUBREDDITS`, zanim ktokolwiek ją ustawi (S-14).

**Contract**: po `WYKOP_SECRET: preserve()` wpis `REDDIT_SUBREDDITS: preserve()` z komentarzem: opcjonalna, nie sekret, lista subredditów po przecinku; bez niej Reddit jest nieskonfigurowanym źródłem; PR env dostaje ją z repository variable (krok CI „Ensure PR-env Reddit settings”); produkcję ustawia operator po S-28 (`deploy-plan.md`, sekcja S-27); nigdy wartości tutaj. Nie w `variables.ignore`.

#### 2. CI

**File**: `.github/workflows/ci.yml`

**Intent**: PR env pobiera naprawdę, żeby bramka F1 miała dowód z adresów Railway.

**Contract**: krok `Ensure PR-env Reddit settings` tuż po „Ensure PR-env Wykop keys” (przed „Deploy guard”), `if: github.event_name == 'pull_request'`, `env: CI_REDDIT_SUBREDDITS: ${{ vars.REDDIT_SUBREDDITS }}`; odmawia na `production` (`::error`, exit 1); pusta wartość → `::warning` „repository variable REDDIT_SUBREDDITS missing: $TARGET_ENV runs without Reddit” i exit 0; ustawia `railway variable set REDDIT_SUBREDDITS --stdin -s szczur-radar-api -e "$TARGET_ENV" --skip-deploys` tylko gdy wartość w `railway variable list --json` się różni; wartość może być wypisana (nie sekret). Repository variable: `gh variable set REDDIT_SUBREDDITS --body "stocks,ValueInvesting,investing"` (agent, raz).

#### 3. README i wytyczne

**File**: `szczur-radar-api/README.md`, `REPOSITORY_GUIDELINES.md`

**Intent**: Operator i agent czytają aktualne reguły.

**Contract**: README — sekcja `## Reddit` po `## Wykop`: tabela `REDDIT_SUBREDDITS` (lista po przecinku, opcjonalny `r/`, wielkość liter jak na Reddicie, 2–21 znaków `[A-Za-z0-9_]`; opcjonalna: bez niej Reddit nieskonfigurowany, zła nazwa albo pusta lista przerywa start z komunikatem); Arctic Shift: publiczne archiwum bez klucza, `GET /api/posts/search` po czasie, posty w archiwum sekundy po publikacji, liczniki po ok. 36 h (nie odświeżane), posty zdjęte/skasowane pomijane i liczone w logu, limity dynamiczne (429/422/5xx ponawiane do 3 razy, czekanie z nagłówków ≤ 30 s), termin pobrania 10 min poniżej watchdoga; horyzont: Arctic Shift czyta oficjalne Data API, które Reddit zamyka publicznie w marcu 2027 — objaw końca to `reddit empty` co noc, runbook w deploy-plan; lokalnie `REDDIT_SUBREDDITS=stocks ./mvnw …`; PR env z repository variable; produkcja po S-28. Wiersz pakietu `sources` (`:27`) z `sources/reddit` i `sources/transport`; w opisie migracji wzmianka o V13 (`posts.channel`, backfill `gielda`). `REPOSITORY_GUIDELINES.md`: opis `sources` (`sources/wykop`, `sources/reddit`, `sources/transport/HttpRetry`), punkt Deployment o kluczach Wykopu uzupełniony o `REDDIT_SUBREDDITS` (nie sekret; PR env z repository variable; produkcja po S-28).

#### 4. PRD

**File**: `context/foundation/prd.md`

**Intent**: Doprecyzowania pod FR-023 i NFR retencji; bez zmiany treści wymagań.

**Contract**: pod FR-023 (`:237`): „Doprecyzowanie (<data>, S-27 `reddit-source`)” — źródłem danych Reddita jest publiczne archiwum Arctic Shift (bez klucza i rejestracji), kanały wybiera `REDDIT_SUBREDDITS`, posty zdjęte/skasowane nie są pobierane, `is_self → post`, inaczej `link`; ryzyko horyzontu (publiczne Data API Reddita zamykane w marcu 2027, los archiwum nieznany) przyjęte przez właściciela 2026-10-29 z runbookiem wyłączenia. Pod NFR retencji (`:275`): treści Reddita z archiwum trzeciej strony są przechowywane jak Wykopu (bezterminowo, RAW bezstratnie); właściciel 2026-10-29 uznał reguły wiki Data API Reddita o usuwaniu skasowanych treści za niewiążące dla tej ścieżki; synchronizacji usunięć nie ma. Pod NFR źródeł (`:269`): S-27 dodało drugie źródło zmieniając `sources/`, testy, IaC i dokumenty; jedyną zmianą wspólnego modelu jest kolumna `posts.channel` (kanał źródła: tag Wykopu, subreddit), wprowadzona na życzenie właściciela, a nie przez wymóg źródła.

#### 5. Runbook

**File**: `context/deployment/deploy-plan.md`

**Intent**: Sekcja S-27 w konwencji S-20/S-21 (status prawdziwy także po merge'u), runbooki włączenia i końca archiwum, aktualizacja zdania z S-20.

**Contract**: `## S-27: Reddit jako drugie źródło (reddit-source)`; `**Status:** delivered by PR #<N>; production verification and closure in issue #121.`; „What changed for the operator” (nowe źródło, bez zmiany silnika; `/sources` z `reddit`; bez zmiennej nic się nie zmienia); „Variables” (`REDDIT_SUBREDDITS`: produkcja — nieustawiona do S-28; PR env — z repository variable); „PR-env verification” (wyniki fazy); „Runbook — enabling Reddit on production (after S-28; operator; tracked in issue #121)”: 0. zasady (bez `RUNNING`, poza oknem nocy); 1. `railway variable set REDDIT_SUBREDDITS "stocks,ValueInvesting,investing" -s szczur-radar-api -e production` (nie sekret, nie pieczętować; zmiana zmiennej redeployuje API — poza oknem nocy); 2. `/admin` pokazuje Reddit jako skonfigurowany; przebieg za D-2 z `reddit succeeded`; 3. pierwsza noc: wiersz `reddit` na stronie przebiegu, cron 0; 4. komentarz w #121. „Runbook — Arctic Shift stops delivering (horizon March 2027)”: objawy (`reddit empty` co noc: `partial`, `source_empty`, `RUN_NEEDS_ATTENTION`, „Dni bez danych” dla Reddita; status `https://status.arctic-shift.photon-reddit.com`, issues repozytorium), działanie: `railway variable delete REDDIT_SUBREDDITS -s szczur-radar-api -e production` → Reddit nieskonfigurowany, noce tylko Wykop, „Dni bez danych” przestają liczyć Reddit; posty już zebrane zostają. „Rollback: fix-forward only”. W sekcji S-20 przy zdaniu o `GET …/sources` (`:1938`): „**Update (S-27):** `reddit` jest listowane jako `configured:false` bez `REDDIT_SUBREDDITS`”.

#### 6. Weryfikacja w PR env

**File**: komentarze w issue #121, sekcja S-27 w `deploy-plan.md`

**Intent**: Dowód na prawdziwym Arctic Shift z adresów Railway, że pobranie, mapowanie, deduplikacja i przełącznik działają.

**Contract**: w PR env (`szczur-radar-pr-<N>`, admin z runbooka S-07, sesja `<cookie>`): `GET /api/admin/pipeline-runs/sources` → `reddit` `configured:true`; `POST /api/admin/pipeline-runs {"from":"<D-2>","to":"<D-2>","sources":["reddit"]}` → 202; po zakończeniu `GET /api/admin/pipeline-runs/{id}`: `sourceResults` z `reddit succeeded`, `fetched > 0`, `skipped 0`, `attentionReasons` bez `skipped`; `GET /api/posts?page=0&size=100` (w razie braku dopasowania sprawdzać kolejne strony, dopóki `(page + 1) * size < total` z odpowiedzi; brak dopasowania po ostatniej stronie to błąd weryfikacji) zawiera post z `webUrl` zaczynającym się od `https://www.reddit.com/r/ValueInvesting/` (dowód, że nazwa z wielką literą działa) i `GET /api/posts/{id}/raw` z polem `subreddit`; ten sam zakres drugi raz → `newPosts 0` oraz wynik źródła `reddit succeeded` z `fetched > 0` i `skipped 0`; `GET /api/admin/pipeline-runs/missing-days` nie wymienia `reddit` dla tego dnia. Wyniki (id przebiegów, odpowiedzi, log `removed or deleted left out`) w komentarzu issue #121 i w sekcji runbooka.

### Success Criteria:

#### Automated Verification:

- CI PR-a zielone: `gh pr checks --watch` kończy się bez porażki
- Repository variable ustawiona: `gh variable get REDDIT_SUBREDDITS` wypisuje `stocks,ValueInvesting,investing`
- PR env: Reddit skonfigurowany: `curl -sb <cookie> <web-origin>/api/admin/pipeline-runs/sources | jq -e '.[] | select(.source == "reddit") | .configured == true'`
- PR env: przebieg Reddita udany bez pominięć: `curl -sb <cookie> <web-origin>/api/admin/pipeline-runs/<id> | jq -e '(.sourceResults[] | select(.source == "reddit") | .status == "succeeded" and .fetched > 0 and .skipped == 0) and (.attentionReasons | index("skipped") | not)'`
- PR env: post Reddita w feedzie z RAW: `curl -sb <cookie> '<web-origin>/api/posts?page=<page>&size=100' | jq -e '[.items[] | select((.webUrl // "") | startswith("https://www.reddit.com/r/ValueInvesting/"))] | length > 0'` (zacząć od `page=0`; brak dopasowania na stronie oznacza sprawdzenie kolejnej strony, dopóki `(page + 1) * size < total` z odpowiedzi; brak dopasowania po ostatniej stronie kończy weryfikację błędem) i `curl -sb <cookie> <web-origin>/api/posts/<id>/raw | jq -e '.subreddit == "ValueInvesting"'` dla id znalezionego posta
- PR env: ten sam zakres drugi raz nie dodaje postów: `curl -sb <cookie> <web-origin>/api/admin/pipeline-runs/<id2> | jq -e '.newPosts == 0 and any(.sourceResults[]; .source == "reddit" and .status == "succeeded" and .fetched > 0 and .skipped == 0)'`
- Deklaracja IaC i krok CI istnieją: `rg -n 'REDDIT_SUBREDDITS: preserve\(\)' .railway/railway.ts && rg -n 'Ensure PR-env Reddit settings' .github/workflows/ci.yml`
- Dokumenty wymieniają S-27: `rg -n 'S-27' context/foundation/prd.md context/deployment/deploy-plan.md REPOSITORY_GUIDELINES.md szczur-radar-api/README.md` znajduje każdy z czterech plików

#### Manual Verification:

- Operator ocenia treść doprecyzowań PRD (FR-023, retencja) i sekcji runbooka S-27 (włączenie po S-28, koniec Arctic Shift) i podejmuje decyzję o merge'u po `/10x-impl-review` i `/10x-archive`

**Implementation Note**: Merge wykonuje wyłącznie człowiek; włączenie na produkcji, weryfikacja i zamknięcie issue #121 idą runbookiem po S-28, nie przez Progress.

---

## Testing Strategy

### Unit Tests:

- `HttpRetryTest`: polityka statusów, nagłówki czekania, limity, termin, przerwanie, liczby żądań.
- `WykopApiClientTest` niezmieniony jako pin refaktoru; pozostałe testy Wykopu zielone.
- `RedditApiClientTest`, `RedditRepositoryTest` (stronicowanie z zakładką, deduplikacja, zdjęte poza wynikiem, niespójność, limit stron, termin poniżej `MIN_DEFAULT`), `RedditMapperTest` (typy, tekst, offset UTC zimą i latem, `[removed]`), `RedditPostsTest` (RAW ↔ element), `RedditFetcherTest`, `RedditConfigurationTest` — wszystkie bez Springa (poza `ApplicationContextRunner`), bez Dockera, bez sieci, na prawdziwych zanonimizowanych fixture'ach.

### Integration Tests:

- `PipelineRunIT` z `LocalReddit`: wszystkie dotychczasowe scenariusze dwóch źródeł, bramka F1 (wejście oceny = mapowanie fixture'a Reddita), zdjęte posty poza licznikami, deduplikacja drugiego przebiegu.
- `RunWatchdogIT` (dwa źródła z opóźnionym Redditem), `MissingDaysIT` (Reddit skonfigurowany/nie), `PipelineRunControllerIT` i `PipelineRunWithoutSourceIT` (`/sources` z dwoma wpisami, 503 dla nieskonfigurowanego `reddit`).
- `ClosedSetsIT` bez zmian (rejestracja z V12); `JpaSchemaIT` i `PostColumnsIT` z kolumną `channel`; `MigrationsIT` z backfillem V13.

### Manual Testing Steps:

1. PR env: przebieg za D-2 z `sources: ["reddit"]`, strona `/admin/runs/:id` z wierszem `Reddit — pobrane`, feed z postami Reddita, „Dane dla nerdów” z surowym JSON-em Reddita.
2. PR env: ten sam zakres drugi raz → brak nowych postów; „Dni bez danych” bez Reddita dla tego dnia.
3. Operator czyta doprecyzowania PRD i runbooki S-27 przed merge'em.

## Performance Considerations

- Pobranie: 3 subreddity × ≤ 3 strony po 100 (r/stocks ~52, r/ValueInvesting ~35, r/investing ~20 postów/dzień z zdjętymi; dwa dni ≤ 220 elementów na subreddit) + pauzy 300 ms ≪ `FETCH_DEADLINE` 10 min; Wykop (≤ ~11 min) i Reddit (≤ ~11 min) po kolei mieszczą się w `TRIGGER_RUN_TIMEOUT` 90 min razem z oceną.
- RAW: pełny obiekt posta (~2–3 KB, gzip w `raw_payloads`), ~60–110 postów/dzień po wyłączeniu zdjętych — pomijalne wobec Wykopu (~150–250/dzień).
- Koszt LLM Reddita (długie posty, mediana 606–1042 znaków) mierzy S-28; w PR env ocena kilkudziesięciu postów na przebieg.

## Migration Notes

- Jedna migracja `V13__posts_channel.sql` (kolumna `channel`, backfill `gielda` dla każdego wiersza `wykop`, `NOT NULL`, `CHECK (channel <> '')`), w pre-deploy `migrate`; bez nowej tabeli (`tools/db-backup/tables.sh` bez zmian). Rejestracja `reddit` w czterech `CHECK` jest od V12.
- Wdrożenie zmiennej: deklaracja `preserve()` i krok CI w tym PR; PR env dostaje wartość z repository variable; produkcja nieustawiona do S-28 (runbook). Usunięcie zmiennej = wyłączenie źródła, posty zostają.
- Rollback: fix-forward; bez zmiennej Reddit jest nieskonfigurowany, więc awaria kodu Reddita nie dotyka nocy Wykopu (izolacja S-20).

## References

- Research: `context/changes/reddit-source/research.md`
- Roadmapa S-27: `context/foundation/roadmap.md:236-250` (przejęcie F1 `:242`); issue #121
- Przepis źródła: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md:29-107`
- Wzorzec: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/` (`WykopApiClient.java:221-319`, `WykopRepository.java:48-135`, `WykopMapper.java:42-85`, `WykopPosts.java:23-53`, `WykopFetcher.java:23-56`, `WykopConfiguration.java:24-54`)
- Testy silnika: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java:110,204-260,703-830,1041-1053`, `sources/ScriptedSourceConfiguration.java:33-53`, `sources/wykop/LocalWykopServer.java`
- Silnik: `pipeline/step/SourceIngest.java:38-69,95-145`, `pipeline/CLAUDE.md:85-92,566-597`, `pipeline/run/PipelineRun.java:325-345`
- IaC/CI: `.railway/railway.ts:145-150,313-322`, `.github/workflows/ci.yml:385-417,443-500`; runbook Wykopu `context/deployment/deploy-plan.md:187-215`, S-20 `:1938-1963`
- Arctic Shift: `https://github.com/ArthurHeitmann/arctic_shift/blob/master/api/README.md`, issues #26, #39, #53, #66, #72, #76; wiki Data API Reddita (2026-05-11) i Responsible Builder Policy (2026-10-05) na `support.reddithelp.com`
- Poprzednie plany tej samej konwencji: `context/archive/2026-10-29-source-isolation/plan.md`, `context/archive/2026-10-20-wykop-ingest-completeness/plan.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Wspólna pętla ponowień w `sources/` — refaktor Wykopu bez zmiany zachowania

#### Automated

- [x] 1.1 Testy Wykopu i pętli zielone: `cd szczur-radar-api && ./mvnw -B test -Dtest='WykopApiClientTest,WykopRepositoryTest,WykopFetcherTest,WykopMapperTest,WykopConfigurationTest,HttpRetryTest'` — 11aa2cd
- [x] 1.2 `WykopApiClientTest` niezmieniony: `git diff --stat origin/main -- szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopApiClientTest.java` nie wypisuje nic — 11aa2cd
- [x] 1.3 Pętla istnieje raz: `rg -n 'for \(int attempt' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/` znajduje tylko `sources/transport/HttpRetry.java` — 11aa2cd
- [x] 1.4 Wyjątek przeniesiony: `test -f szczur-radar-api/src/main/java/pl/szczurradar/api/sources/UnmappableItemException.java && ! test -e szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/UnmappableItemException.java` — 11aa2cd
- [x] 1.5 Cała jednostkowa część zielona: `cd szczur-radar-api && ./mvnw -B test` — 11aa2cd
- [x] 1.6 Kolumna kanału w schemacie, modelu i strażnikach: `rg -n 'channel' szczur-radar-api/src/main/resources/db/migration/V13__posts_channel.sql szczur-radar-api/src/main/java/pl/szczurradar/api/sources/SourcePost.java szczur-radar-api/src/main/java/pl/szczurradar/api/posts/Post.java szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/PostColumnsIT.java` znajduje każdy z czterech plików — 11aa2cd
- [x] 1.7 Schemat, encje i backfill zielone z Testcontainers: `cd szczur-radar-api && ./mvnw -B verify` — 11aa2cd
- [x] 1.8 Test backupu po migracji kanału zielony z Dockerem: `tools/db-backup/test.sh` — 11aa2cd

### Phase 2: Transport i stronicowanie Arctic Shift

#### Automated

- [x] 2.1 Testy transportu Reddita zielone: `cd szczur-radar-api && ./mvnw -B test -Dtest='RedditApiClientTest,RedditRepositoryTest,HttpRetryTest,Wykop*Test'` — bb0f135
- [x] 2.2 Fixture'y są prawdziwe i zanonimizowane: `for f in szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-*.json; do jq -e '.data | length >= 10' "$f" >/dev/null && test -z "$(jq -r '.data[].author' "$f" | grep -v -E '^(uzytkownik[0-9]+|\[deleted\])$')"; done` — bb0f135
- [x] 2.3 Fixture r/stocks ma posty zdjęte i widoczne: `jq -e '[.data[] | select(.removed_by_category != null)] | length > 0' szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-stocks.json && jq -e '[.data[] | select(.removed_by_category == null)] | length > 0' szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-stocks.json` — bb0f135
- [x] 2.4 Termin pobrania przypięty poniżej watchdoga: `rg -n 'aFetchEndsBeforeTheWatchdogsShortestDefault' szczur-radar-api/src/test/java/pl/szczurradar/api/sources/reddit/RedditRepositoryTest.java` — bb0f135
- [x] 2.5 Cała jednostkowa część zielona: `cd szczur-radar-api && ./mvnw -B test` — bb0f135

### Phase 3: Mapowanie i źródło (klasy bez rejestracji w Springu)

#### Automated

- [x] 3.1 Testy mapowania i źródła zielone: `cd szczur-radar-api && ./mvnw -B test -Dtest='Reddit*Test'` — afe6103
- [x] 3.2 Nic nie jest jeszcze beanem: `! rg -n '@Component' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/` — afe6103
- [x] 3.3 Wejście z RAW równe wejściu z elementu dla każdego posta fixture'a (`RedditPostsTest` zielony): `cd szczur-radar-api && ./mvnw -B test -Dtest='RedditPostsTest'` — afe6103
- [x] 3.4 Cała jednostkowa część zielona: `cd szczur-radar-api && ./mvnw -B test` — afe6103
- [x] 3.5 Kanał Reddita to subreddit: `rg -n 'subreddit\(\)' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditPosts.java` i `rg -n 'channel' szczur-radar-api/src/test/java/pl/szczurradar/api/sources/reddit/RedditPostsTest.java` — afe6103

### Phase 4: Rejestracja i testy silnika na prawdziwym Reddicie

#### Automated

- [x] 4.1 Cały backend zielony z Testcontainers: `cd szczur-radar-api && ./mvnw -B verify` — 59138a3
- [x] 4.2 Dubletu nie ma: `! rg -n 'ScriptedSourceConfiguration' szczur-radar-api/src REPOSITORY_GUIDELINES.md` — 59138a3
- [x] 4.3 Bramka F1 istnieje i sprawdza wejście z fixture'a Reddita: `rg -n 'aWykopOutageLeavesTheRedditPostAssessedFromItsRawPayload|RedditMapper.map' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java` — 59138a3
- [x] 4.4 Zdjęte posty poza licznikami: `rg -n 'removedRedditPostsAreNeitherFetchedNorSkipped' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java` — 59138a3
- [x] 4.5 Reddit widoczny w `/sources`: `rg -n '"source\\\\":\\\\"reddit\\\\",\\\\"configured\\\\":false' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunWithoutSourceIT.java` znajduje oba pliki — 59138a3
- [x] 4.6 Reguły pakietów wymieniają `LocalReddit`: `rg -n 'LocalReddit' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` znajduje oba pliki — 59138a3
- [x] 4.7 Kanał zapisany per źródło w przebiegu dwóch źródeł: `rg -n "channel" szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java` znajduje asercje `inwestowanie` i `gielda` — 59138a3

### Phase 5: IaC, CI, dokumentacja, weryfikacja w PR env

#### Automated

- [x] 5.1 CI PR-a zielone: `gh pr checks --watch` kończy się bez porażki — 68bc2de
- [x] 5.2 Repository variable ustawiona: `gh variable get REDDIT_SUBREDDITS` wypisuje `stocks,ValueInvesting,investing` — 68bc2de
- [x] 5.3 PR env: Reddit skonfigurowany: `curl -sb <cookie> <web-origin>/api/admin/pipeline-runs/sources | jq -e '.[] | select(.source == "reddit") | .configured == true'` — 68bc2de
- [x] 5.4 PR env: przebieg Reddita udany bez pominięć: `curl -sb <cookie> <web-origin>/api/admin/pipeline-runs/<id> | jq -e '(.sourceResults[] | select(.source == "reddit") | .status == "succeeded" and .fetched > 0 and .skipped == 0) and (.attentionReasons | index("skipped") | not)'` — 68bc2de
- [x] 5.5 PR env: post Reddita w feedzie z RAW: `curl -sb <cookie> '<web-origin>/api/posts?page=<page>&size=100' | jq -e '[.items[] | select((.webUrl // "") | startswith("https://www.reddit.com/r/ValueInvesting/"))] | length > 0'` (zacząć od `page=0`; brak dopasowania na stronie oznacza sprawdzenie kolejnej strony, dopóki `(page + 1) * size < total` z odpowiedzi; brak dopasowania po ostatniej stronie kończy weryfikację błędem) i `curl -sb <cookie> <web-origin>/api/posts/<id>/raw | jq -e '.subreddit == "ValueInvesting"'` dla id znalezionego posta — 68bc2de
- [x] 5.6 PR env: ten sam zakres drugi raz nie dodaje postów: `curl -sb <cookie> <web-origin>/api/admin/pipeline-runs/<id2> | jq -e '.newPosts == 0 and any(.sourceResults[]; .source == "reddit" and .status == "succeeded" and .fetched > 0 and .skipped == 0)'` — 68bc2de
- [x] 5.7 Deklaracja IaC i krok CI istnieją: `rg -n 'REDDIT_SUBREDDITS: preserve\(\)' .railway/railway.ts && rg -n 'Ensure PR-env Reddit settings' .github/workflows/ci.yml` — 68bc2de
- [x] 5.8 Dokumenty wymieniają S-27: `rg -n 'S-27' context/foundation/prd.md context/deployment/deploy-plan.md REPOSITORY_GUIDELINES.md szczur-radar-api/README.md` znajduje każdy z czterech plików — 68bc2de

#### Manual

- [ ] 5.9 Operator ocenia treść doprecyzowań PRD (FR-023, retencja) i sekcji runbooka S-27 (włączenie po S-28, koniec Arctic Shift) i podejmuje decyzję o merge'u po `/10x-impl-review` i `/10x-archive`
