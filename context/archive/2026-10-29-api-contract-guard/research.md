---
date: 2026-10-29T17:27:56+0000
researcher: kwmacioszek (Claude Opus 5.5)
git_commit: 5a013ca
branch: feature/api-contract-guard
repository: kwmacioszek/szczur-radar
topic: "S-24 api-contract-guard: stan kontraktu SPA ↔ API na HEAD — kształt odpowiedzi 500 (K2), pełne ciała dużych odpowiedzi (K3), kopie reguł Java ↔ TS (K4), resztka Q-05"
tags: [research, spa-api-contract, error-handling, testability, szczur-radar-api, szczur-radar-web, mock-api]
status: complete
last_updated: 2026-10-29
last_updated_by: kwmacioszek (Claude Opus 5.5)
---

# Research: kontrakt SPA ↔ API pilnowany testem (S-24)

**Date**: 2026-10-29T17:27:56+0000
**Researcher**: kwmacioszek (Claude Opus 5.5)
**Git Commit**: 5a013ca (`main` po S-20; S-22, S-23, S-25 też już na `main`)
**Branch**: feature/api-contract-guard
**Repository**: kwmacioszek/szczur-radar

## Research Question

Slice S-24 roadmapy M-3 (`context/foundation/roadmap.md:197-208`). Outcome: duże odpowiedzi API (przebieg z krokami, post z oceną, nieudany post) mają w testach zapisany pełny kształt; odpowiedź na nieoczekiwany błąd serwera ma zdefiniowany kod, a atrapa i testy SPA używają tego samego; reguły powtórzone po obu stronach (białe znaki, limity, listy wartości) nie rozjadą się bez czerwonego testu. Źródła: audyt `post-m2-audit` K2, K3, K4 (`context/changes/post-m2-audit/research.md:119-121`), destylacja Q-05 (`context/domain/domain-distillation.md:252`).

Pytania:
1. Jaki JSON API wysyła dziś na błąd 500 (jedyna niewiadoma S-24, „do sprawdzenia w `/10x-research` lokalnym testem”) i kto go czyta?
2. Które duże odpowiedzi nie mają zapisanego pełnego kształtu i jakie ręczne dane w web łamią reguły serwera?
3. Które reguły są skopiowane między Javą, TS i atrapą, czy są dziś równe i jakimi mechanizmami repo już pilnuje kopii?
4. Co zostało z Q-05 po S-22?

Audyt pochodzi z `3f744c6`, sprzed S-20/S-22/S-23/S-25. Każde ustalenie poniżej jest sprawdzone ponownie na `5a013ca`. Oznaczenia: [O] zaobserwowane (kod albo pomiar), [I] wniosek z kodu/konfiguracji, [U] nierozstrzygnięte.

## Summary

- **K2 — odpowiedź 500 zmierzona.** Ten sam jar co w obrazie (`./mvnw -DskipTests package`, Postgres 18, `Accept: application/json` jak w `client.ts:66`). Wyjątek w kontrolerze daje 500 z domyślnym ciałem Spring Boot `{"timestamp":"…","status":500,"error":"Internal Server Error","path":"/api/posts"}`, z sesją i bez niej (scenariusze A, B, F, G) [O]. Ciało nie ma `detail`, więc SPA dostaje `ApiError(500, null)` (`client.ts:43-54`) [O]. Atrapa i testy Vitest używają `{"detail":"internal_error"}` w 19 miejscach (7 w `dev/mock-api.js`, 12 w 6 plikach testów). `ErrorCode.java:19-64` (14 kodów) tego kodu nie ma i nic w `src/` SPA na nim nie rozgałęzia [O]. Dwa przypadki obok: (a) awaria bazy przy logowaniu daje **401 `not_authenticated`**, a `LoginView.vue:26` pokazuje „Nieprawidłowy e-mail lub hasło.” (scenariusze E, I) [O]; (b) przy niedostępnej bazie zapytanie z sesją dostaje po ~90 s **stronę HTML Tomcata**, bo `/error` też nie może odczytać sesji (scenariusz H) [O].
- **K3 — kształty.** Pola rekordów Javy i interfejsów TS na trasach SPA zgadzają się 1:1 (brak pola po jednej stronie) [O]. Żaden IT nie zapisuje całego `RunView`/`StepView`/`SourceResultView`, `PostItem`/`AssessmentView` ani `FailedPost`/`LastAttempt` [O]. Pól nigdy nie asercjonowanych jest dużo, m.in. `RunView.createdAt`, `startedAt`, `finishedAt`, `fetched`, `newPosts`, `assessedFailed`, `dead`, `skipped` oraz `PostItem.publishedAt`, `title`, `body`, `author`, `webUrl`. Ręczne dane web łamią reguły serwera w co najmniej 4 miejscach: `FeedView.test.ts:43`, `PostCard.test.ts:26`, `StanceText.test.ts:18` (bullish/bearish/neutral bez `claim_ids`) oraz `RunDetailView.test.ts:258-271` (przebieg `skipped: 0` przy źródle `skipped: 2`, a powody bez `skipped`) [O]. `dev/mock-api.js` to nietypowany JS poza `tsconfig` [O].
- **K4 — kopie reguł.** Wszystkie kopie z audytu są dziś równe i żadna nie ma strażnika porównującego strony [O]. Doszło 8 bliźniaków, m.in. długość hasła 12/128, „3 nieudane próby”, „maks. 7 dni” wpisane literałem w tekst komunikatu (`pipeline.ts:172,176`) oraz listy enumów (`AttentionReason`, `SourceFetchStatus`, `RunStatus`, …) ↔ unie TS i `labels.ts` [O].
- **Q-05** jest rozstrzygnięte przez S-22: SPA nie liczy już powodów, tylko mapuje kody na tekst (`runs.ts:93-129`) [O]. Zostały dwie rzeczy. Brakuje testu, że każdy `AttentionReason` z Javy ma tekst w SPA [O]. Wisi też follow-up F1 z S-22: `runTone` i `runStatusLabel` nadal czytają licznik `skipped` (`runs.ts:38-41,70-82`) [O].
- **Mechanika strażnika.** Oba joby CI robią pełny checkout (`ci.yml:27,70`). Testy Javy już czytają pliki spoza modułu przez `Path.of("../…")` (`AlertSettingsTest.java:29-30`, `ContractExamples.java:20`) [O]. Obraz web typecheckuje także `*.test.ts` (`tsconfig.app.json:14`, kontekst `./szczur-radar-web`), więc import JSON-a spoza `szczur-radar-web/` najpewniej wywróci `vue-tsc` w obrazie [I]. Import `?raw` przechodzi typecheck bez pliku (`vite/client` deklaruje `*?raw`) i ma precedens w `styleContract.test.ts` [I].

## Detailed Findings

### 1. Odpowiedź na nieoczekiwany błąd (K2)

**Kto dziś pisze ciała błędów** [O]:
- `ApiError(ErrorCode detail)` daje `{"detail":"<kod>"}`: `config/ApiError.java:15,18-28`.
- `ErrorCode` serializuje się przez `@JsonValue code()` (`ErrorCode.java:77-80`). Ma 14 wartości, w tym `not_found` 404 i `source_not_configured` 503. Kodu dla 500 nie ma.
- `ApiExceptionHandler.java:18-47` mapuje tylko wyjątki 400/415 (nieczytelne ciało, walidacja, zły typ lub brak parametru, nieobsługiwany media type). Handlera `Exception`/`RuntimeException` nie ma.
- Filtry i Security piszą przez `ApiError.write`:
  - `OriginGuardFilter.java:48` — 403 `cross_origin`;
  - `JsonAuthenticationEntryPoint.java:19` — 401 `not_authenticated`, oba łańcuchy;
  - `JsonAccessDeniedHandler.java:19` — 403 `forbidden`.
- Poza `ApiError` są jeszcze dwa ciała: `HealthController.java:50` i `DeploySafetyController.java:56` (503 `{"status":"unavailable"}`).
- `application.properties` nie ma kluczy `spring.web.error.*`, `server.error.*` ani `problemdetails`. W Boot 4.0.8 właściwości błędów to `spring.web.error.*`; `spring.mvc.problemdetails.enabled` domyślnie ma `false` (`WebMvcAutoConfiguration.java:718` w jarze autoconfigure).
- `SecurityConfig.java:101`: `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` to pierwsza reguła łańcucha `/api`, więc dispatch do `/error` przechodzi z sesją i bez niej.

**Pomiar lokalny** [O]. Jar z HEAD, `PORT=18000`, Postgres 18 w kontenerze, jedno konto admina utworzone przez `create-user` i aktywację. Błędy wywołane przez `ALTER TABLE … RENAME` albo zatrzymanie bazy. Wszystkie zapytania z `Accept: */*` lub JSON, poza D.

| # | Scenariusz | Status | Ciało |
|---|---|---|---|
| A | z sesją, `GET /api/posts`, brak tabeli `posts` (wyjątek w kontrolerze) | 500 | `{"timestamp":"2026-10-29T17:22:48.732Z","status":500,"error":"Internal Server Error","path":"/api/posts"}` |
| B | z sesją, `GET /api/admin/posts/failed`, j.w. | 500 | to samo, `path` tej trasy |
| F | bez sesji, `POST /api/auth/activation/lookup`, brak tabeli `users` | 500 | to samo, `path` tej trasy |
| G | z sesją, `GET /api/auth/me`, brak tabeli `users` | 500 | to samo, `path` tej trasy |
| E | bez sesji, `POST /api/auth/login`, brak tabeli `users` | **401** | `{"detail":"not_authenticated"}` |
| I | bez sesji, `POST /api/auth/login`, baza zatrzymana | **401** | `{"detail":"not_authenticated"}` |
| H | z sesją, `GET /api/posts`, baza zatrzymana | 500 po 90 s | HTML Tomcata „HTTP Status 500 – Internal Server Error” (`text/html`); w logu `Exception Processing [ErrorPage[errorCode=0, location=/error]]` |
| D | z sesją, `Accept: text/html`, j.w. jak A | 500 | Whitelabel HTML (SPA zawsze wysyła `Accept: application/json`, `client.ts:66`, więc jej to nie dotyczy) |
| — | z sesją, nieznana trasa `GET /api/nope` | 404 | `{"timestamp":…,"status":404,"error":"Not Found","path":"/api/nope"}` |
| — | zła para e-mail/hasło (punkt odniesienia) | 401 | `{"detail":"invalid_credentials"}` |

Ścieżki scenariuszy [I, z kodu]:
- **A/B/F/G.** Wyjątek idzie przez `FrameworkServlet`, Tomcat robi ERROR dispatch do `/error`, a ten trafia do `BasicErrorController`. Ciało pochodzi z `ErrorAttributeOptions.defaults()` (PATH, STATUS, ERROR) plus `timestamp`.
- **E/I.** `DaoAuthenticationProvider` owija błąd bazy w `InternalAuthenticationServiceException`. `AuthController.java:92-93` celowo go przepuszcza („not a verdict on the credentials”). `ExceptionTranslationFilter` znajduje w łańcuchu przyczyn `AuthenticationException` i woła entry point, który odpowiada 401. Intencja z komentarza („to nie werdykt o danych”) nie dochodzi więc do klienta.
- **H.** Spring Session JDBC rzuca przy odczycie sesji przed kontrolerem. Dispatch `/error` znów próbuje sesji i znów czeka na pulę Hikari (30 s). Tomcat oddaje wtedy własną stronę. 90 s to wynik zmierzony (`time_total`); dokładny podział na timeouty to [I].

**Co dzisiejszy kształt znaczy dla SPA** [O]:
- `readDetail` (`client.ts:43-54`) zwraca `null`, gdy ciało nie ma `detail` typu string albo nie jest JSON-em. Każdy scenariusz 500 powyżej kończy się więc w SPA jako `ApiError(500, null)`.
- Kody, na których `src/` rozgałęzia: `pipeline_busy`, `post_dead`, `source_not_configured`, `invalid_request` (`api/pipeline.ts:168-177`) oraz `activation_link_invalid`, `invalid_request`, `password_policy` (`api/activation.ts:41-48`). Każdy z nich jest w `ErrorCode`.
- Wszystkie 19 wystąpień `internal_error` w `szczur-radar-web` są w atrapie i testach:
  - `dev/mock-api.js`: 388, 395, 398, 413, 430, 438, 458;
  - `src/notificationsStore.test.ts`: 86, 121;
  - `src/components/NotificationBell.test.ts`: 214, 271, 310;
  - `src/views/AdminView.test.ts`: 219, 222, 275, 496, 684;
  - `src/views/RunDetailView.test.ts`: 171;
  - `src/views/PostDetailView.test.ts`: 139.

  Inne testy modelują już prawdziwe 5xx jako `new ApiError(500|503, null)`: `ActivateView.test.ts:160`, `notificationsStore.test.ts:95`, `NotificationBell.test.ts:361`, `RunDetailView.test.ts:490`. Testy są więc dziś niespójne między sobą.
- Catch-all atrapy (`mock-api.js:463`) odpowiada 404 `{detail:'not_found'}`. Prawdziwe API na nieznaną trasę zwraca ciało Boot z sesją albo 401 bez niej.
- `api/pipeline.test.ts:90-96` daje `pipeline_busy` i `source_not_configured` status 400. API wysyła 409 i 503. SPA rozgałęzia po `detail`, więc to nieszkodliwe, ale też jest rozjazdem z serwerem.

**Fakty pod test** [O/I]:
- `MockMvcTester` nie przekazuje błędów do kontenera. Nieobsłużony wyjątek wychodzi jako błąd wyniku bez ciała `/error` [I]. Prawdziwe ciało pokaże tylko IT `@SpringBootTest(webEnvironment = RANDOM_PORT)` z klientem HTTP. Precedensy: `auth/OriginGuardHttpIT.java:21-26,35,50` (`java.net.http.HttpClient`, asercja surowego ciała) i `pipeline/PipelineStartupRecoveryIT.java:48` [O].
- Żaden IT nie asercjonuje ciała 5xx. Jedyne asercje 5xx to trzy 503 przez MockMvc: `PipelineRunWithoutSourceIT:73`, `PipelineRunControllerIT:261`, `DeploySafetyIT:221` [O].
- `AuthorizationCompletenessIT.routes()` (`:123-138`) zbiera też mapowanie `/error` z `BasicErrorController` i oczekuje 401 dla bezpośredniego REQUEST [O]. Własny `ErrorController` też musi przez to przejść [I].
- Zasięg mechanizmów [I]:
  - `@RestControllerAdvice` z catch-all pokryłby A/B/F/G (wyjątki w kontrolerach), ale nie błędy z filtrów ani `sendError` (404/405).
  - Własne `ErrorAttributes`/`ErrorController` pokryłby wszystko, co dochodzi do `/error` (A/B/F/G, 404, 405).
  - H nie dochodzi do skutku nawet w `/error`, więc żaden z tych mechanizmów sam go nie naprawi.

### 2. Pełne kształty dużych odpowiedzi (K3)

Serializacja [O]:
- Nie ma strategii nazewnictwa ani `JsonInclude`. Jedyna linia Jacksona to `application.properties:46`. `null` jest wysyłany jako klucz (`NotificationsIT:174`).
- `Instant` serializuje się jako ISO `…Z`, `LocalDate` jako `YYYY-MM-DD`.
- Enumy idą przez `@JsonValue`: małe litery albo jawne stringi (`InterruptionCause`, `AttentionReason`, `NotificationKind`).
- Rekordy kontraktu oceny używają snake_case (`claim_ids`, `target_ids`).
- Stos to Jackson 3 (`tools.jackson`) z adnotacjami `com.fasterxml.jackson.annotation`.

Rekordy i ich pokrycie w IT [O]:

| Rekord (plik:linia) | Pola | Pokrycie IT | Nigdy nie asercjonowane |
|---|---|---|---|
| `RunView` (`pipeline/api/PipelineRunController.java:241-263`) | 30: `id`, `kind`, `status`, `interruptionCause`, `needsAttention`, `attentionReasons`, `scheduled`, `rangeFrom`, `rangeTo`, `sources`, `requestedPostIds`, `createdAt`, `startedAt`, `finishedAt`, `lastActivityAt`, 12 liczników (`fetched` … `inFlight`), `error`, `steps`, `sourceResults` | `PipelineRunControllerIT` — 64 `extractingPath`, nigdy całość (:160-440) | `createdAt`, `startedAt`, `finishedAt`, `fetched`, `newPosts`, `assessedFailed`, `dead`, `skipped` (trafienia `fetched`/`skipped`/`finishedAt` dotyczą `sourceResults[*]`) |
| `StepView` (`:286`) | `step`, `status`, `startedAt`, `finishedAt`, `itemsIn`, `itemsOut`, `error` | j.w. | `startedAt`, `finishedAt`, `error` |
| `SourceResultView` (`:275`, S-20) | `source`, `status`, `fetched`, `skipped`, `error`, `finishedAt` | wszystkie 6 po ścieżce (:391-399) | — (brak ścisłego zbioru kluczy) |
| `PostItem` (`posts/api/PostController.java:140-141`) + `AssessmentView` (`:155`) + `AssessmentOutputV2` (`agents/assessor/contract/*`) | `id`, `itemType`, `publishedAt`, `title`, `body`, `author`, `webUrl`, `assessment{output, resultSource, assessedAt}` | `PostsApiIT` — 50 `extractingPath`; `claims` porównane drzewem z `ContractFixtures.validOutput()` (:179-180); szczegół = element listy (:181-182) | `publishedAt`, `title`, `body`, `author`, `webUrl`, `targets`/`modifiers`/`flags` po wartości |
| `FailedPost` (`posts/api/FailedPostController.java:83-85`) + `LastAttempt` (`:98-99`) + `Violation` | `id`, `itemType`, `publishedAt`, `title`, `body`, `webUrl`, `stage`, `assessmentAttempts`, `lastAttempt{failureClass, violations, assessedAt, provider, model}`, `technicalFailures`, `lastTechnicalError`, `onHold` | `PostsApiIT:365-445` po ścieżkach | `itemType`, `publishedAt`, `title`, `body`, `webUrl`, `lastAttempt.assessedAt`, `violations[].failureClass/path/message` |
| `NotificationItem`/`NotifiedRun` (`notifications/api/NotificationController.java:105,123-125`) | 5 + 10 | `NotificationsIT:169-187` `containsOnlyKeys` | — (pełny zbiór kluczy jest) |

Rekordy z pełnym zbiorem kluczy albo całym ciałem [O]:
- `NotificationsIT:169-187`;
- `InternalPipelineRunIT:215,229-240`;
- `MissingDaysIT:253-257`;
- `FreshnessIT:171,201,204`;
- `PipelineRunControllerIT:157` (`/sources`);
- małe ciała auth/aktywacji (audyt K3).

Gotowe narzędzia [O/I]:
- `MockMvcTester … bodyJson()` z `extractingPath`, `isStrictlyEqualTo` i `isLenientlyEqualTo` (oparte na JSONAssert [I]);
- `JsonPath` z mapami AssertJ;
- porównanie drzew `JsonNode`;
- `ContractFixtures`.

Własnego narzędzia do porównywania JSON-a w repo nie ma.

Inwarianty serwera, które warto zakodować w danych testowych [O]:
- `needsAttention == !attentionReasons.isEmpty()`, z kolejnością powodów z `PipelineRun.java:316-352`;
- `run.fetched` i `run.skipped` to sumy wyników źródeł (`RunRecorder.java:143-144`);
- `sourcesFailed`/`sourcesEmpty` to liczby źródeł o danym statusie;
- `POST` przebiegu zwraca `steps: []` i `sourceResults: []` (`PipelineRunController.java:105`);
- `stage` nieudanego posta to tylko `failed|dead|stored` (z 5 wartości `PostStage`).

Porównanie z TS [O]:
- Każde pole ma odpowiednik po drugiej stronie.
- Różnice dotyczą typu i opcjonalności:
  - `Run.attentionReasons?` i `Run.sourceResults?` są opcjonalne „dla starego API”, a Java zawsze je wysyła (`pipeline.ts:70,106`);
  - `sources`, `SourceResult.source` i `lastAttempt.provider` mają typ `string` wobec enumów Javy (decyzja: SPA nie zna nazw źródeł);
  - `FailedPost.stage` to zawężona unia.

Ręczne dane w web [O]:
- Typowane buildery `Partial<T>` (vue-tsc je sprawdza): `FeedView.test.ts:25`, `PostDetailView.test.ts:22`, `PostCard.test.ts:8`, `PostAssessment.test.ts:8-30`, `AdminView.test.ts:30,67`, `RunDetailView.test.ts:26,39,51,87`, `runs.test.ts:20,32,182`, `NotificationBell.test.ts:27`, `notificationsStore.test.ts:33`. Importów JSON w testach jest 0, moduły API są mockowane przez `vi.mock`.
- `KitchenSinkView.vue:76-101,159-298` jest typowany przez `import type`.
- `dev/mock-api.js` to nietypowany JS (bez `@ts-check`, poza `include`). Buildery `nightly` (:106-123), `allRuns` (:124+), `notifiedRun` (:221), `feedItem` (:326-336).
- Złamane reguły serwera:
  - `FeedView.test.ts:43` i `PostCard.test.ts:26`: bullish z `claim_ids: []`;
  - `StanceText.test.ts:18`: każdy kierunek z `claim_ids: []`. Według `StanceTable.java:13-18,70-73` bullish/bearish/neutral/mixed wymagają 1–4 `claim_ids` i tezy autora;
  - `RunDetailView.test.ts:258-271`: źródło `wykop` ma `skipped: 2` (domyślne z `:44`), przebieg `skipped: 0`, powody `['source_failed']`. Serwer dałby `skipped: 2` i `['source_failed','skipped']`.
  - [I, drobne] `AdminView.test.ts:61` ma krok `succeeded` z `finishedAt: null`. Buildery `Run` pomijają `sourceResults`, które serwer zawsze wysyła.

### 3. Reguły skopiowane między stronami (K4 i nowe)

Skróty ścieżek: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `web/` = `szczur-radar-web/`.

| Reguła | Java | TS / Vue | Atrapa | Równe? |
|---|---|---|---|---|
| zbiór białych znaków §10 (29 punktów kodowych) | `api/text/Whitespace.java:17-30`; `regexClass()` `:72-75` w składni `\x{..}` | `web/src/feedFilter.ts:24` | `mock-api.js:445` | tak [O]; TS = atrapa bajt w bajt, Java w innej formie, więc porównanie tekstu nie wystarczy |
| limit `q` 100 (UTF-16, przed strip) | `api/posts/api/PostController.java:87` `@Size(max = 100)` | `feedFilter.ts:7`, `FeedFilter.vue:57` | `mock-api.js:444` | tak |
| brak NUL w `q` | `PostController.java:87` `@Pattern` | **brak** | `mock-api.js:444` | 2 z 3 stron [O]; SPA wyśle NUL z adresu i dostanie 400 [I] |
| zakres ≤ 7 dni | `api/pipeline/api/PipelineRunController.java:60,187` | `web/src/missingDays.ts:5`; literał w tekście `api/pipeline.ts:176` | — | tak |
| strona przebiegów ≤ 100 | `PipelineRunController.java:111` `@Max(100)` | `AdminView.vue:128` | — | tak |
| `KINDS`/`DIRECTIONS` | `agents/assessor/contract/TargetKind.java:10`, `Direction.java:10` | `api/posts.ts:10,14` | `mock-api.js:351-352` | tak, ta sama kolejność |
| wynik prefiltra | `agents/assessor/Prefilter.java:41-43` | `KitchenSinkView.vue:242-251,518-522` | `mock-api.js:334` | tak |
| teksty przerwania | `pipeline/run/InterruptionCause.java:12,18,21,28` | — | `mock-api.js:178,188` | tak (SPA nie parsuje `run.error`) |
| hasło 12–128 punktów kodowych | `auth/Passwords.java:12,14,31` | `ActivateView.vue:14-15,51` | — | tak (nowe) |
| martwy po 3 próbach | `posts/Post.java:40` | literał „3 nieudane próby” `api/pipeline.ts:172` | — | tak (nowe) |
| okno „Dni bez danych” 14 dni | `pipeline/run/MissingDays.java:48` | tylko komentarz `pipeline.ts:152,160` | — | — |
| ponowienie ≤ 100 postów | `PipelineRunController.java:63,183` | brak limitu w SPA | — | 400 `invalid_request` pokaże tekst o zakresie (`pipeline.ts:176`) [I] |

Listy wartości Java ↔ TS. Wszystkie są dziś równe [O]. Na nieznaną wartość SPA reaguje na dwa sposoby: `label()` pokazuje wartość tak, jak przyszła (`labels.ts:76-78`), a mapy tonów `Record<…, BadgeTone>` (`runs.ts:24,43,55`) dają `undefined`, czyli badge bez koloru [I].

Listy:
- `AttentionReason` (7, `pipeline/run/AttentionReason.java:14-42`) ↔ unia `pipeline.ts:21-28` ↔ teksty `runs.ts:99-128`. Atrapa używa 6 z 7 (bez `processing_errors`).
- `SourceFetchStatus` ↔ `pipeline.ts:33`, `labels.ts:73`, `runs.ts:55-64`.
- `RunStatus`, `StepStatus`, `RunStep`, `RunKind`, `InterruptionCause` ↔ `pipeline.ts:5-16`, `labels.ts:46-62`.
- `PostStage` ↔ zawężona unia `posts.ts:91`.
- Typy kontraktu oceny (`ItemType`, `Relevance`, `Flag`, `Modifier`, `ClaimKind`, `Attribution`, `Reference`, `Assertiveness`, `Document`, `Field`) ↔ `posts.ts:5-30`, `labels.ts:3-45`.
- `FailureClass` ↔ `posts.ts:94-101`, `labels.ts:63-71`.
- `Provenance.Source` ↔ `posts.ts:78`.
- `Role` ↔ `api/auth.ts:7`.
- `NotificationKind` ↔ `api/notifications.ts:6`.
- `Source` ↔ `string` (decyzja).

Istniejące testy porównują stałą tylko z wartością wpisaną w ten sam test, nigdy z drugą stroną [O]: `missingDays.test.ts:38`, `feedFilter.test.ts:25-36`, `WhitespaceTest.java:17-19` (ręczna lista 29 punktów kodowych).

### 4. Q-05 po S-22

- SPA tylko mapuje kody na tekst [O]: `runs.ts:93-129`. Tekst zastępczy jest przy `:96`, nieznany kod pokazuje się surowo (`:123-127`), a test tego jest w `runs.test.ts:248`.
- Lista kodów żyje w Javie (`AttentionReason.java`) i w unii TS (`pipeline.ts:21-28`). Łączą je tylko komentarze (`AttentionReason.java:8-9`, `pipeline.ts:18-19`). Sprawdzenie `never` (`runs.ts:125`) obejmuje tylko unię TS [O].
- Ranking destylacji #3 uznaje warunek „SPA nie liczy już powodów samodzielnie” za „done when” (`domain-distillation.md:233`). S-22 wprost oddał S-24 wspólny plik przypadków i pełny kontrakt odpowiedzi (`context/archive/2026-10-27-run-attention-single-rule/plan.md:38`) [O].
- Follow-up F1 z S-22 (`context/archive/2026-10-27-run-attention-single-rule/follow-ups/review-fixes.md`): `runTone` (`runs.ts:38-41`) i `runStatusLabel`/`hasSkipped` (`:70-82`) czytają `skipped > 0`. Ta część reguły ma więc kopię w SPA. F1 wskazuje S-24 jako kandydata albo zmianę, która da `NotifiedRun` kody powodów; dziś `NotifiedRun` nie ma `attentionReasons` [O].

### 5. Mechanika wspólnych danych między modułami

- **CI.** `backend` (`ci.yml:20-61`) i `web-unit` (`ci.yml:63-79`) robią pełny `actions/checkout@v4` i ustawiają tylko `working-directory` [O].
- **Java czyta spoza modułu.** Precedensy: `alerts/AlertSettingsTest.java:29-30` (`Path.of("../tools/healthchecks/fixtures/ping-urls.txt")`, „relative to the module directory, where Maven runs the tests”) i `agents/assessor/ContractExamples.java:20` (`../context/foundation/…`) [O]. Obraz API robi `-DskipTests package` (`szczur-radar-api/Dockerfile:12`), więc ścieżka spoza modułu go nie psuje [O].
- **Web czyta spoza modułu.** Obraz web buduje z kontekstu `./szczur-radar-web` (`ci.yml:91`, Railway `rootDirectory` `.railway/railway.ts:193`). `npm run build` = `vue-tsc -b && vite build`, a `tsconfig.app.json:14` (`include: src/**/*.ts…`, bez exclude) typecheckuje testy [O]. Wnioski [I]:
  - zwykły import `../../../szczur-radar-api/….json` w teście padnie w obrazie, bo pliku nie ma w kontekście;
  - `?raw` przejdzie, bo jest deklarowany jako `string` w `vite/client` (precedens `styleContract.test.ts:2-11`), ale wymaga `JSON.parse` w teście;
  - `node:fs` nie przejdzie typechecku, bo `types: ["vite/client"]`;
  - import JSON-a daje poszerzone `string`, więc nie da się go przypisać do unii TS bez rzutowania.
- **Redeploy.** Wzorce obserwowanych ścieżek to `szczur-radar-api/**` dla API (`.railway/railway.ts:112`) i `szczur-radar-web/**` dla web (`:194`) [O]. Plik danych testowych pod `szczur-radar-api/` przy każdej zmianie przedeployuje API i przerwie trwający przebieg; pod `szczur-radar-web/` przedeployuje tylko web [I, z reguły wzorców].
- **Wzorce strażników w repo** [O]:
  - S-25: wspólna tabela przypadków `tools/healthchecks/fixtures/ping-urls.txt`, czytana przez Javę i bash, plus porównanie bloków bajt w bajt w `test.sh:446-485`;
  - S-23 `ClosedSetsIT:77,151-245`: rejestr i kompletność w obie strony;
  - `AuthorizationCompletenessIT:51,63,82-91`: ręczne listy wobec mapowań, z wykrywaniem martwych wpisów;
  - `styleContract.test.ts`: skan źródeł przez `?raw`.
- **Odrzucone wcześniej warianty.** S-25 odrzucił generowanie skryptów z Javy i wspólny plik shell (bramka S-14, brak Javy w obrazach cronów) oraz nowy job CI (`context/archive/2026-10-27-alert-ping-single-rule/plan.md:34-50`). Decyzji o OpenAPI lub generowaniu typów nie ma nigdzie; są tylko odroczenia do S-24 (S-22 `plan.md:38`, S-25 `plan-brief.md:31`) [O]. W `pom.xml` i `package.json` nie ma springdoc/OpenAPI ani narzędzi schematów (audyt K6, potwierdzone grepem na HEAD) [O].

## Code References

- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ApiError.java:15-28` — jedyny kształt błędu aplikacji `{"detail":…}`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ErrorCode.java:13-80` — 14 kodów, brak 500.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ApiExceptionHandler.java:18-47` — tylko 400/415.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java:99-102` — `DispatcherType.ERROR` `permitAll`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/AuthController.java:86-97` — przepuszczenie `InternalAuthenticationServiceException`.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/OriginGuardHttpIT.java:21-50` — wzór IT na prawdziwym porcie.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java:123-138` — `/error` w zbiorze tras.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java:202-290` — `RunPage`, `RunView`, `SourceResultView`, `StepView`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:84-155` — parametry filtra, `PostItem`, `AssessmentView`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/FailedPostController.java:60-99` — `FailedPost`, `LastAttempt`.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java:157-440`, `posts/api/PostsApiIT.java:99-445` — dzisiejsze asercje po ścieżkach.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/alerts/AlertSettingsTest.java:29-30` — Java czyta plik spoza modułu.
- `szczur-radar-web/src/api/client.ts:43-81` — `readDetail`, `Accept: application/json`.
- `szczur-radar-web/src/api/pipeline.ts:5-177`, `src/api/posts.ts:5-108`, `src/api/notifications.ts:6-34` — typy TS i rozgałęzienia po `detail`.
- `szczur-radar-web/src/runs.ts:24-129`, `src/labels.ts:3-91` — tony, etykiety, teksty powodów.
- `szczur-radar-web/src/feedFilter.ts:7,24`, `src/missingDays.ts:5` — kopie reguł.
- `szczur-radar-web/dev/mock-api.js:106-463` — atrapa (buildery, `internal_error`, białe znaki, limity).
- `szczur-radar-web/tsconfig.app.json:14`, `.github/workflows/ci.yml:20-91`, `.railway/railway.ts:112,193-194` — typecheck testów w obrazie, checkout, kontekst builda, wzorce redeployu.

## Architecture Insights

- Kontrakt błędów ma dwa domy. Aplikacja pisze `{"detail":kod}` (`ApiError`), a wszystko, co trafia do kontenera (`/error`), dostaje kształt Spring Boot. SPA jest odporna na oba, bo czyta `detail` miękko, ale atrapa i część testów udają trzeci kształt (`internal_error`).
- „Pełny kształt w testach” ma dziś tylko jedno źródło prawdy: IT `MockMvcTester`. Web nie widzi żadnego JSON-a serwera; jej dane to ręczne buildery typowane przez TS (typy pilnują nazw pól, nie inwariantów) i nietypowana atrapa.
- Strażniki tego repo mają jeden wzór: jawne oczekiwanie plus test kompletności, który pada, gdy rzeczywistość ma coś, czego oczekiwanie nie zna (S-23, `AuthorizationCompletenessIT`). Kopie między językami S-25 sprowadził do wspólnej tabeli przypadków czytanej przez obie strony.
- Granica S-24 według roadmapy (`roadmap.md:207`) to wykrycie rozjazdu, bez generatora klienta i bez OpenAPI. Do strażnika wystarczą mechanizmy już obecne w repo: `bodyJson()`/`JsonPath` w IT, `?raw` w Vitest, `Path.of("../…")` w Javie. Wniosek [I], nie decyzja.

## Historical Context (from prior changes)

- `context/changes/post-m2-audit/research.md:112-134` — K0–K6. Stan na HEAD:
  - **K0** zgodność pól 1:1 — potwierdzone; doszły pola S-20/S-22, też zgodne.
  - **K1** — rozstrzygnięte przez S-22, resztka F1.
  - **K2** — potwierdzone; korekta liczby: 19 wystąpień `internal_error` (7 atrapa + 12 testy), a nie „8 + 18”. JSON z 500 już nie jest [U], został zmierzony.
  - **K3** — potwierdzone; przykład `FeedView.test.ts:43` nadal jest, doszły `PostCard.test.ts:26`, `StanceText.test.ts:18`, `RunDetailView.test.ts:258-271`.
  - **K4** — potwierdzone, linie przesunięte. „Teksty błędu przerwania” są tylko w atrapie, nie w SPA.
  - **K5** (atrapa poza testami) i **K6** (brak OpenAPI, schematów, wspólnych fixture'ów) — potwierdzone; poza zakresem S-24 według `roadmap.md:201`, które wymienia K2, K3, K4.
- `context/archive/2026-10-27-run-attention-single-rule/plan.md:38` — S-22 zostawił S-24 wspólny plik przypadków API ↔ SPA i typy; F1 w `follow-ups/review-fixes.md`.
- `context/archive/2026-10-27-alert-ping-single-rule/plan.md:16,34-50` — wzór „wspólna tabela przypadków + porównanie kopii” i odrzucone warianty (generacja, nowy job).
- `context/archive/2026-10-27-closed-sets-guard/plan.md:44-55` — wzór „jawne oczekiwanie + kompletność w obie strony”.
- `context/foundation/roadmap.md:304` — notatka M-4 („S-24 do decyzji przy otwieraniu M-4”) stoi obok wpisu S-24 w fali M-3 (S-27, S-24). Ta zmiana zakłada M-3.

## Related Research

- `context/changes/post-m2-audit/research.md` — audyt po M-2, obszar 4 (kontrakt SPA ↔ API).
- `context/domain/domain-distillation.md` — ranking #3, Q-05.

## Open Questions

Decyzje dla `/10x-plan` (produkt/zakres, nie fakty):
1. **Kod 500.** Nowy `ErrorCode` (np. `internal_error`, którego atrapa i 12 miejsc w testach już używają) albo zostawienie ciała Boot i przejście atrapy i testów na `ApiError(500, null)`. Do tego wybór warstwy: catch-all w `@RestControllerAdvice` (tylko wyjątki kontrolerów) albo własne `ErrorAttributes`/`ErrorController` (wszystko, co trafia do `/error`, także 404/405 z sesją).
2. **Logowanie przy awarii bazy** (E/I: 401 i „Nieprawidłowy e-mail lub hasło.”). Czy wchodzi do S-24 jako „nieoczekiwany błąd serwera”, czy zostaje znaną różnicą?
3. **Awaria magazynu sesji** (H: HTML Tomcata po ~90 s). Zgłosić, objąć, czy zostawić poza zakresem? Żaden z mechanizmów z pkt 1 go nie pokrywa [I].
4. **Źródło pełnego kształtu.** Pliki JSON zapisane przez IT (złote ciała) i czytane przez Vitest `?raw` czy asercje w samej Javie? Gdzie je trzymać (`szczur-radar-web/` = redeploy tylko web; `szczur-radar-api/` = redeploy API) i jak traktować pola zmienne (`id`, czasy)?
5. **Które bliźniaki dostają strażnika.** Lista z sekcji 3 jest dłuższa niż K4. Ograniczenie zakresu do K2–K4 (`roadmap.md:207`) wymaga wyboru: białe znaki, limity, listy enumów ↔ unie/etykiety, literały w tekstach komunikatów, `AttentionReason` ↔ teksty.
6. **F1 (`skipped` w `runTone`/`runStatusLabel`).** Przejąć w S-24 czy zostawić?
7. **Atrapa (K5).** Roadmapa wyłącza K5, ale outcome mówi „atrapa i testy SPA używają tego samego” kodu 500. Minimum to zamiana `internal_error` w atrapie zgodnie z decyzją z pkt 1; więcej (typowanie atrapy, wspólne dane) tylko na prośbę.

Fakty niesprawdzone:
- Zachowanie Vitest i `vue-tsc` w obrazie przy imporcie spoza `szczur-radar-web/` (zwykłym i `?raw`) wynika z konfiguracji, nie z uruchomienia [I].
- Pomiar 500 był lokalny, na tym samym jarze. Na Railway przed API stoi Caddy web, który przekazuje `/api/*`; zmiana ciała przez Caddy jest mało prawdopodobna, ale nie zmierzona [U].
