# S-27 reddit-source — Reddit jako drugie źródło przez Arctic Shift — Plan Brief

> Full plan: `context/changes/reddit-source/plan.md`
> Research: `context/changes/reddit-source/research.md`

## What & Why

Reddit (FR-023, nice-to-have) staje się drugim źródłem postów obok Wykopu: wybrane subreddity są pobierane w przebiegu zakresu, mapowane do wspólnego modelu i oceniane tym samym kontraktem, a silnik, zapis, ocena i API nie zmieniają się (NFR `prd.md:269`). To pierwszy prawdziwy test tego NFR i gwiazdy przewodniej S-20: nieskonfigurowany Reddit nie zmienia niczego dla Wykopu, a jego awaria jest własnym wynikiem źródła.

## Starting Point

Szew źródła to dwa porty (`SourceFetcher`, `RawInputMapper`) zbierane w mapy po `Source`; `Source.REDDIT` jest zarejestrowane w czterech `CHECK` od V12, bez implementacji. Wykop (`sources/wykop/`, 11 klas) jest wzorcem, w tym własna pętla ponowień w `WykopApiClient`. Testy silnika z S-20 używają dubletu `ScriptedSourceConfiguration` pod `Source.REDDIT`, który koliduje z każdym produkcyjnym beanem Reddita. Arctic Shift (publiczne archiwum, bez klucza) jest jedyną ścieżką dostępu, jaką projekt testował (pilot 5); czyta oficjalne Data API, które Reddit zamyka publicznie w marcu 2027.

## Desired End State

Z `REDDIT_SUBREDDITS=stocks,ValueInvesting,investing` przebieg zakresu ma wiersz `reddit succeeded` bez pominięć, posty Reddita są w feedzie z surowym JSON-em, a powtórzony zakres nie dodaje postów. Bez zmiennej Reddit jest w `/sources` jako nieskonfigurowany, noc i „Dni bez danych” go nie widzą. Posty zdjęte przez moderację nie są pobierane. Każdy post ma w bazie kanał (`posts.channel`: `gielda` albo subreddit). `./mvnw -B verify` jest zielone bez sieci, a `PipelineRunIT` dowodzi, że przy awarii Wykopu post Reddita przechodzi przez RAW, produkcyjny mapper i ocenę. Produkcja dostaje zmienną dopiero po S-28, runbookiem.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Dostęp do danych | Arctic Shift jako jedyny klient, z runbookiem na koniec archiwum | Jedyna przetestowana ścieżka, bez klucza, z filtrem po czasie; alternatywy wymagają zgody Reddita albo już nie działają | Research / Plan |
| Ryzyko horyzontu (marzec 2027) | Przyjęte przez właściciela; objaw to `reddit empty` co noc, działanie to usunięcie zmiennej | Utrzymujący Arctic Shift nie wie, co się stanie; abstrakcja ponad `SourceFetcher` nie ma drugiej implementacji | Plan |
| Subreddity pierwszej konfiguracji | `stocks`, `ValueInvesting`, `investing` | Trzy kanały pilota 5 z pomiarem; r/wallstreetbets i polskie kanały poza pierwszą konfiguracją | Plan |
| Przełącznik „skonfigurowane” | Zmienna `REDDIT_SUBREDDITS` (lista po przecinku); reszta stałymi w kodzie | Ten sam wzorzec co `WYKOP_*`: bean warunkowy steruje `/sources`, nocą i „Dniami bez danych”; produkcja włącza po S-28 jedną zmienną | Plan |
| PR env | Krok CI „Ensure PR-env Reddit settings” z repository variable | Bramka F1 ma dowód na żywo z adresów Railway; produkcja nietknięta | Plan |
| Posty zdjęte i skasowane | Poza elementami pobrania (ani `fetched`, ani `skipped`, ani RAW), liczone w logu | `skipped > 0` dałby `RUN_NEEDS_ATTENTION` co noc (44 z 58 w r/stocks); to stan elementu w źródle, nie filtr treści | Research / Plan |
| Megawątki, NSFW | Bez filtrów | `sources/CLAUDE.md:83-85` zabrania filtra treści w źródle | Plan |
| `url` + `selftext` | `is_self → post`, inaczej `link` (title + selftext jako opis) | Precedens pilota 5, zgodny z glosariuszem i mapowaniem Wykopu; bez nowej reguły w kontrakcie | Research / Plan |
| Retencja treści Reddita | Jak Wykop: bezterminowo, RAW bezstratnie; doprecyzowanie w PRD | Zero mechanizmu; reguły wiki Data API Reddita uznane za niewiążące dla archiwum trzeciej strony (decyzja właściciela) | Plan |
| Pętla ponowień | Wspólny `sources/transport/HttpRetry`, refaktor Wykopu bez zmiany zachowania w osobnej fazie | Wymóg `sources/CLAUDE.md:44-47` i plan-review S-16 F6; `WykopApiClientTest` niezmieniony jako pin | Research / Plan |
| Dublet testowy | `LocalReddit` (produkcyjny stos nad lokalnym serwerem, lista subredditów przełączana) zamiast `ScriptedSourceConfiguration` | Jedyny sposób bez duplikatu klucza `Source.REDDIT`; spełnia przejęcie F1 z S-20 | Research / Plan |
| Stronicowanie | Po czasie: `after`/`before` w sekundach z zakładką o 1 s, deduplikacja po `id`, strona < 100 kończy, pełna strona bez nowego `id` = niespójność | Arctic Shift nie ma `total` ani kursora, a granice są wyłączne | Research |
| RAW | Pełny obiekt posta, bez parametru `fields` | Lekcja RAW bezstratnie; rozmiar pomijalny | Plan |
| Kanał w bazie | Kolumna `posts.channel` (V13, backfill `gielda`, Wykop `TAG`, Reddit subreddit) | Właściciel chce kanał wprost w `posts`, nie tylko w `assessments.input` i RAW; pojęcie kanału już jest w kontrakcie | Plan |
| Język posta | Poza S-27; wpis w S-28/S-29 w roadmapie | Wymaga pola w wyjściu modelu (§16, nowa `PROMPT_VERSION`) albo detekcji — decyzja przy pomiarze po angielsku | Plan |

## Scope

**In scope:**
- `sources/transport/HttpRetry` i refaktor `WykopApiClient` na nim; `UnmappableItemException` w `sources/`; migracja V13 `posts.channel` z backfillem, `SourcePost.channel`, `Post.channel`, Wykop wpisuje `gielda`.
- `sources/reddit/`: `RedditApiClient`, `RedditApiException`/`RedditFailure`, `RedditItem`, `RedditSearchResult`, `RedditRepository`, `RedditMapper`, `RedditPosts`, `RedditFetcher`, `RedditConfiguration` (`REDDIT_SUBREDDITS`) z testami jednostkowymi na prawdziwych, zanonimizowanych fixture'ach (`fixtures/reddit/`).
- `LocalApiServer`/`LocalRedditServer`/`LocalReddit` w testach; usunięcie `ScriptedSourceConfiguration`; przepięcie `PipelineRunIT`, `RunWatchdogIT`, `MissingDaysIT`; bramka F1; scenariusz zdjętych postów; JSON `/sources` z dwoma wpisami.
- `railway.ts` (`REDDIT_SUBREDDITS: preserve()`), krok CI dla PR env, repository variable, README, `REPOSITORY_GUIDELINES.md`, oba `CLAUDE.md`, deploy-plan (sekcja S-27 z runbookami), doprecyzowania PRD, roadmapa i SVG, weryfikacja w PR env, komentarze w issue #121.

**Out of scope:**
- Komentarze Reddita; etykieta źródła i świeżość per źródło (S-29); pomiar jakości po angielsku i kosztu, zmiany promptu, prefiltru, kontraktu (S-28, §16).
- Włączenie na produkcji (runbook po S-28); synchronizacja usunięć, drugi odczyt po 36 h, wykrywanie edycji; filtry megawątków i NSFW; parametr `fields`; abstrakcja dostawcy ponad `SourceFetcher`; kolumna języka posta; `channel`/`source` w API feedu (S-29); zmiany w SPA, `tables.sh`, `WykopConfiguration.text`.

## Architecture / Approach

`RedditFetcher` (port) → `RedditRepository` (per subreddit: `GET /api/posts/search` po czasie, zakładka 1 s, deduplikacja, pauza, `FETCH_DEADLINE` 10 min, zdjęte poza wynikiem) → `RedditApiClient` (bez klucza, `User-Agent`, `HttpRetry`: 429/422/5xx/transport, `Retry-After` albo `X-RateLimit-Reset`) → Arctic Shift. `RedditPosts.post(element)` i `RedditPosts.map(raw)` przechodzą przez jeden `RedditMapper` (`is_self → post`, inaczej `link`; `created_utc` → `published_at` UTC; `channels = [subreddit]`). `RedditConfiguration` tworzy `RedditRepository` tylko z niepustej `REDDIT_SUBREDDITS`; `configured()` = niepusta lista. Silnik (`SourceIngest`, `PostStore`, `AssessmentInputs`), persystencja i API bez zmian. Testy silnika: Wykop na `LocalWykopServer`, Reddit na `LocalReddit` (bean `RedditRepository` nad `LocalRedditServer` z listą przełączaną per scenariusz).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Wspólna pętla ponowień w `sources/` | `HttpRetry`, `WykopApiClient` na nim bez zmiany zachowania, `UnmappableItemException` w `sources/`, kolumna `posts.channel` (V13) | Subtelna zmiana komunikatów lub czekań Wykopu (pin: niezmieniony `WykopApiClientTest`); backfill kanału na produkcji w pre-deploy `migrate` |
| 2. Transport i stronicowanie Arctic Shift | Klient, repozytorium, element, lokalny serwer, fixture'y, testy z liczbami żądań | Wyłączne granice i elementy z tą samą sekundą na granicy strony; 422/429 jako chwilowe |
| 3. Mapowanie i źródło (bez beanów) | Mapper, posts, fetcher, konfiguracja z walidacją nazw, testy jednostkowe | Wielkość liter nazw subredditów w Arctic Shift (niesprawdzona) |
| 4. Rejestracja i testy silnika | `@Component`, `LocalReddit`, usunięcie dubletu, przepięte IT, bramka F1, `/sources`, `CLAUDE.md` | Scenariusze IT zależne od szczegółów serwera (hold, delay); wszystko w jednej fazie, bo bean koliduje z dubletem |
| 5. IaC, CI, dokumentacja, PR env | `preserve()`, krok CI, repository variable, README, deploy-plan, PRD, roadmapa, dowód w PR env | Bramka S-14 (deklaracja przed wartością); dostępność Arctic Shift z adresów Railway |

**Prerequisites:** S-20 zmergowane (jest, `5a013ca`); gałąź `feature/reddit-source` na świeżym `origin/main`; issue #121; dostęp `gh` do repository variables; Docker dla `verify`; admin w PR env (runbook S-07).
**Estimated effort:** 5 faz, ok. 4–6 sesji; fazy 2 i 4 największe.

## Open Risks & Assumptions

- Arctic Shift może przestać dostawać nowe dane po marcu 2027 (zamknięcie publicznego Data API Reddita); nikt nie zna losu archiwum. Objaw: `reddit empty` co noc; działanie: usunięcie zmiennej (runbook). Daty zamknięcia znane z relacji przez mirror i z issue #76.
- Czy parametr `subreddit` Arctic Shift rozróżnia wielkość liter — niesprawdzone; nazwy przekazywane jak podane, a weryfikacja w PR env używa `ValueInvesting`.
- Zachowanie Arctic Shift z adresów chmurowych Railway (limity, Cloudflare) — niesprawdzone; PR env to pierwszy test.
- Kolejność elementów o tej samej sekundzie i domyślny `sort` — nieudokumentowane; zakładka 1 s i deduplikacja po `id` to obejście.
- Reguły wiki Data API Reddita (usuwanie skasowanych treści) uznane za niewiążące dla konsumenta archiwum trzeciej strony — decyzja właściciela bez analizy prawnej.
- Posty zdjęte po archiwizacji (sekundy od publikacji) zostają w bazie jako żywe; brak synchronizacji usunięć.

## Success Criteria (Summary)

- Admin w PR env widzi przebieg z `reddit succeeded`, bez pominięć, posty Reddita w feedzie z RAW, a powtórzony zakres nie dodaje postów; bez zmiennej Reddit jest nieskonfigurowany, a Wykop działa jak dotąd.
- `./mvnw -B verify` zielone bez sieci; `PipelineRunIT` dowodzi, że post Reddita przechodzi przez RAW, produkcyjny mapper i ocenę z wejściem równym mapowaniu fixture'a; `WykopApiClientTest` niezmieniony.
- Operator ma w deploy-plan runbook włączenia Reddita po S-28 i runbook końca Arctic Shift; PRD ma doprecyzowania FR-023 i retencji.
