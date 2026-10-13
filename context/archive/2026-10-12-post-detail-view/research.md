---
date: 2026-10-12T17:01:02+02:00
researcher: Claude Code (claude-opus-5-5)
git_commit: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662
branch: feature/post-detail-view
repository: szczur-radar
topic: "S-03 post-detail-view — co już istnieje dla widoku szczegółowego posta i czego brakuje"
tags: [research, codebase, S-03, posts, raw, PostController, RawPayloadStore, szczur-radar-web, router, PostCard, contract-v2]
status: complete
last_updated: 2026-10-13
last_updated_by: Claude Code (claude-opus-5-5)
last_updated_note: "Follow-up po merge'u feed-view-ui-hardening (#48, 404679f): nowe kotwice SPA, rozstrzygnięte pytanie 6"
---

# Research: S-03 post-detail-view — stan kodu i dokumentów przed planem

**Date**: 2026-10-12T17:01:02+02:00
**Researcher**: Claude Code (claude-opus-5-5)
**Git Commit**: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662 (kod bez lokalnych zmian względem tego commitu; nowy jest tylko folder `context/changes/post-detail-view/`)
**Branch**: feature/post-detail-view
**Repository**: szczur-radar

## Research Question

`change.md` nie ma notatek, więc pytanie wynika ze slice'a S-03 (`context/foundation/roadmap.md:184-194`): użytkownik otwiera post i widzi czytelną ocenę V2, surowe dane źródłowe pod opcjonalnym przełącznikiem oraz link do oryginału na Wykopie (PRD US-01, FR-009, FR-010 — oba FR `must-have`, `prd.md:116`, `prd.md:120`). Co z tego już istnieje w backendzie, w SPA i w dokumentach, czego brakuje i jakie decyzje zostały podjęte wcześniej?

## Summary

Prawie wszystkie dane dla S-03 już są zapisane, a część z nich już wraca z API:

- **Ocena i link już są w odpowiedzi listy.** `GET /api/posts` zwraca dla każdego posta pełny obiekt `AssessmentOutputV2` (z `claims`) oraz `author` i `webUrl` (`PostController.java:66-86`). Lista pokazuje tylko trafność, cele ze stanowiskiem i flagi (`PostCard.vue:18-39`). `claims`, `modifiers`, `kind`/`reference` celu, `author` i `webUrl` nie są nigdzie renderowane, a w typach SPA `claims` to `unknown[]` (`szczur-radar-web/src/api/posts.ts:20-26`).
- **Surowy payload da się odczytać, ale nie przez API.** `RawPayloadStore.load(RawPayloadRef)` zwraca dokładnie zapisane bajty JSON (gunzip + kontrola SHA-256; `RawPayloadStore.java:19-25`, `PostgresRawPayloadStore.java:36-47`). Post ma dokładnie jeden payload (`posts.raw_payload_id` UNIQUE, V3). Jedynym produkcyjnym wywołującym `load` jest dziś pipeline (`AssessmentInputs.java:37`). Żadna trasa API nie zwraca RAW.
- **Brakuje trasy dla jednego posta.** Jedyne trasy postów to `GET /api/posts` i `GET /api/admin/posts/failed`. Nie ma `GET /api/posts/{id}`, a `AssessmentRepository` ma tylko zapytania zbiorcze (`findLatestOk`/`findLatest` po kolekcji id). Nie ma też kodu błędu „nie znaleziono”: `ErrorCode` ma 10 wartości, żadna nie jest 404 (`ErrorCode.java:13-43`), i nikt nie obsługuje 404 w `ApiExceptionHandler`.
- **W SPA brakuje trasy z parametrem, linku z karty i elementów UI dla RAW.** Trasa `/posts/:id` musi stać przed catch-all `/:pathMatch(.*)*` (`router/index.ts:43`), bo inaczej głęboki link bez sesji traci ścieżkę w `?redirect=`. Nie ma rozwijanego elementu (`<details>`, `aria-expanded`), stylu kodu ani tokenu czcionki o stałej szerokości. W `labels.ts` nie ma polskich nazw dla `claim.kind`, `claim.attribution`, `target.kind`, `target.reference`, `modifiers` ani `assertiveness`.
- **Decyzje z wcześniejszych zmian.** S-02 świadomie odłożyło do S-03 widok szczegółowy, surowy JSON w UI i link do oryginału, a zapisało wszystko, czego S-03 potrzebuje: RAW, slug/URL, autora (`archive/2026-10-06-first-assessed-wykop-feed/plan.md:71`). Zmiana `admin-panel-ui-hardening` zapowiedziała `feed-view-ui-hardening` „przed S-03 i S-04, żeby nowe widoki dziedziczyły kontrakt” (`archive/2026-10-10-admin-panel-ui-hardening/plan.md:39`). Ta zmiana nie istnieje, a roadmapa jej nie śledzi.
- **Sprzeczność w dokumentach do rozstrzygnięcia w planie.** PRD i kontrakt mówią o „surowym poście obok oceny” (`prd.md:37`, `prd.md:58`, `contract-v2.md:234`). FR-009 mówi z kolei, że surowy JSON jest „opcjonalnie, nie pokazywany domyślnie” (`prd.md:116`). Odczyt pogodzony („tekst posta widoczny obok oceny, JSON źródła pod przełącznikiem”) jest wnioskiem, nie zapisaną decyzją.

## Detailed Findings

### 1. Model danych: post, oceny, RAW

- **Tożsamość posta.** Klucz główny to `posts.id UUID DEFAULT gen_random_uuid()` (`szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:85`; JPA `Post.java:42-44`). Klucz naturalny to `UNIQUE (source, item_type, source_item_id)` (`V4__posts_shared_item_type.sql:16`). API wystawia klientowi `posts.id` (UUID) jako `PostItem.id` (`PostController.java:66,70`). Komentarz w V3:81 nadal opisuje stary klucz `(source, resource, source_item_id)`, który V4 zastąpiło (nieaktualny komentarz, nie błąd schematu).
- **Kolumny posta** (V3:84-123 + V4): `source`, `item_type` (`post|link|comment`), `source_item_id`, `published_at`, nullowalne `title`, `body`, `author`, `slug`, `web_url`, a także `tags TEXT[]`, `raw_payload_id UUID NOT NULL` (UNIQUE + FK do `raw_payloads`, V3:107-108), `content_hash`, `stage` (`stored|assessing|assessed|failed|dead`), `assessment_attempts` i pola przebiegu.
- **Tekst posta = tekst, z którego model cytuje.** `WykopPosts.post` zapisuje do posta `title`/`body` wprost z `AssessmentInputV1.item()` zbudowanego przez `WykopMapper.map` (`sources/wykop/WykopPosts.java:48-51`). Mapper przekazuje tekst bez zmian, tylko pusty tekst zamienia na `null` (`WykopMapper.java:20-24`). Wpis daje `title = null` i `body = content`, znalezisko daje `title` i `body = description` (`WykopMapper.java:48-59`). Cytaty w ocenie są dosłownymi fragmentami tych pól po normalizacji białych znaków (`contract-v2.md:287-305`). Tę normalizację trzeba będzie uwzględnić, gdyby plan chciał podświetlać cytaty w treści.
- **Oceny.** Post ma wiele wierszy w `assessments` (FK `post_id`), po jednym na próbę, ze statusem `ok|failed`. `ok` ⇔ `output IS NOT NULL` (V3:132, V3:161-167). Encja nie ma relacji JPA: `Assessment.postId` to zwykły UUID (`Assessment.java:64-65`). „Najnowsza” oznacza największe `assessed_at`, a przy remisie największe `id` (`AssessmentRepository.java:29-35`, indeks V3:178). Dostępne są tylko metody zbiorcze `findLatestOk(Collection<UUID>)` i `findLatest(Collection<UUID>)` (`AssessmentRepository.java:20-27`). Wynik V2 leży w `assessments.output JSONB` (V3:139), a odczytuje go `Assessment.getOutput()` przez `ContractJson.MAPPER` (`Assessment.java:209-211`).
- **Etap `ASSESSED`** oznacza, że najnowsza próba jest `ok` (`PostStage.java:8-14`). Lista pokazuje wyłącznie ten etap (`PostController.java:47`).
- **RAW.** Relacja post→RAW jest 1:1 (`posts.raw_payload_id` UNIQUE, V3:96,107-108). W Javie jest to `RawPayloadRef` z `Post.getRawPayload()` (`Post.java:177-179`). Zapis kompresuje gzipem w aplikacji (`content_encoding` CHECK `'gzip'`, V3:76), a `media_type` to `application/json` (V3:77). `sha256` liczony jest z bajtów nieskompresowanych (V3:71-72). `load` rzuca `IllegalArgumentException`, gdy payloadu nie ma, i `IllegalStateException` przy niezgodnym haśle (`RawPayloadStore.java:19-25`). Implementacja `PostgresRawPayloadStore` jest package-private (`:21`), więc wstrzykuje się interfejs.
- **Co dokładnie jest „surowe”.** To jeden element strumienia Wykopu, zserializowany ponownie przez Jacksona (`JSON.writeValueAsBytes(element.raw())`, `WykopPosts.java:52`). Ma wszystkie pola i wartości odpowiedzi źródła, ale nie są to oryginalne bajty HTTP (`RawPayloadStore.java:11-12`; decyzja S-02 w `archive/2026-10-06-first-assessed-wykop-feed/plan.md:262`). JSON jest zwarty, bez wcięć. Przykładowe fixture'y mają 1387 B (`fixtures/wykop/entry.json`, 25 kluczy najwyższego poziomu, m.in. `author`, `content`, `votes`, `media`, `tags`, `comments`) i 2066 B (`link.json`).

### 2. API listy i to, czego brakuje dla jednego posta

- **Lista**: `GET /api/posts?page=0..&size=1..100` (domyślnie 50), sortowana od najnowszych po `publishedAt`, potem po `id` (`PostController.java:43-53`, `PostRepository.java:59-63`).
- **DTO** (rekordy zagnieżdżone w `PostController`):
  - `PostPage(items, page, size, total)` (`:56`);
  - `PostItem(id, itemType, publishedAt, title, body, author, webUrl, assessment)` (`:66-67`);
  - `AssessmentView(output: AssessmentOutputV2, resultSource, assessedAt)` (`:81-86`).

  Pola zewnętrzne są w camelCase. Pola kontraktu zostają w snake_case dzięki `@JsonProperty` (`target_ids`, `claim_ids`; `Claim.java`, `Stance.java`), a enumy serializują się małymi literami przez `@JsonValue`.
- **Lista nie zwraca** pól `source`, `sourceItemId`, `slug`, `tags`, `stage`, referencji RAW, innych prób oceny, `input`, `violations` ani proweniencji poza `resultSource`/`assessedAt`.
- **Brak trasy dla jednego posta.** Wyszukiwanie `@GetMapping` w `posts/api/` znajduje dwie trasy: `/api/posts` (`PostController.java:44`) i `/api/admin/posts/failed` (`FailedPostController.java:47`).
- **Odpowiedź dla nieznanego id nie jest zdefiniowana.** `ErrorCode` ma 10 stałych, żadna nie oznacza 404 (`config/ErrorCode.java:13-43`), a `ErrorCodeTest` przypina dokładny zbiór kodów i statusów (`ErrorCodeTest.java:15`). `ApiExceptionHandler` obsługuje błędy 400 i 415 (`ApiExceptionHandler.java:21-47`). Niepoprawny UUID w ścieżce zostałby więc obsłużony jako `MethodArgumentTypeMismatchException` → 400 `invalid_request` (`:37-41`). Brak obsługi 404 sprawdziłem wyszukiwaniem `NoResourceFound|ErrorController|ResponseStatusException|NOT_FOUND` w `szczur-radar-api/src/main` (0 trafień). Nie sprawdzałem uruchomieniowo, jakie ciało odpowiedzi daje domyślna strona błędu Spring Boot dla 404. SPA czyta z ciała tylko `detail` (`client.ts:43-54`), więc dla ciała bez `detail` dostanie `ApiError(404, null)`.
- **Autoryzacja.** `SecurityConfig` odmawia domyślnie (`anyRequest().authenticated()`, `SecurityConfig.java:51-58`). Nowa trasa dla zalogowanego użytkownika poza `/api/admin/**` nie wymaga zmian ani w `SecurityConfig`, ani w rejestrach `AuthorizationCompletenessIT` (`AuthorizationCompletenessIT.java:37-40`). Test sam wypełnia zmienną ścieżki typu `UUID` wartością `00000000-0000-0000-0000-000000000000` i oczekuje 401 (`:143-160`). Trasa pod `/api/admin/` trafiłaby do `ADMIN_ROUTES` (`:58-63`), a `AdminRoleIT` woła te ścieżki dosłownie, bez podstawiania `{id}`.
- **Testy, na których można się wzorować.** `PostsApiIT` (`@SpringBootTest` + `MockMvcTester` + Testcontainers, `asUser()`/`asAdmin()`, asercje JSONPath; `posts/api/PostsApiIT.java:39-43,87-98,171-177`) i `PostSeeds`. W `PostSeeds` każdy post dostaje `webUrl "https://wykop.pl/wpis/<uuid>"` i RAW z `/fixtures/wykop/entry.json` zapisany prawdziwym `RawPayloadStore`, a `ok(...)` zapisuje `ContractFixtures.validOutput()` (`posts/PostSeeds.java:64-102`).

### 3. Link do oryginału (FR-010)

- `WykopItem.webUrl()` buduje adres `https://wykop.pl/{wpis|link}/{id}[/{slug}]`, gdzie `link` oznacza znalezisko, a `wpis` wpis. Zwraca `null`, gdy brak id albo zasób nie jest `entry`/`link` (`sources/wykop/WykopItem.java:29-36`, testy `WykopItemTest.java:30-34`). Adres trafia do `posts.web_url` (V3:94, `WykopPosts.java:51`, `PostStore.java:86-87`).
- Lista już zwraca `webUrl` (`PostController.java:67,71`), ale SPA go nie renderuje: w `src` nie ma żadnego użycia `webUrl` poza typami i mockami.
- `web_url` jest nullowalny w schemacie i w typach (`posts.ts:35-36`). Dla postów z Wykopu (zawsze `entry`/`link` z id, bo `WykopPosts.post` odrzuca element bez id, `:45-47`) będzie w praktyce wypełniony. Ryzyko martwego linku zostało zaakceptowane w PRD (`prd.md:121-122`). Brak URL nie jest opisany w żadnym dokumencie.

### 4. Kontrakt V2: kształt do prezentacji

- **Rekord**: `AssessmentOutputV2(relevance, modifiers, targets, claims, flags)` (`agents/assessor/contract/AssessmentOutputV2.java:15-17`).
  - `Target(id, kind, anchor, stance, reference)`.
  - `Stance(direction, assertiveness, claim_ids)`.
  - `Claim(id, kind, attribution, target_ids, evidence, statement)`.
  - `Anchor` i `Evidence` mają postać `(document, field, quote)` (pliki w tym samym pakiecie).
- **Słownik wartości** (`context/foundation/llm-assessment/contract-v2.md:110-131`):
  - `claim.kind`: `factual_claim | opinion | investment_thesis | question | trade_action` — to „fakty/opinie/tezy/pytania/działania” z outcome'u S-03;
  - `claim.attribution`: `author | reported | unclear`;
  - `target.kind`: `company | asset | sector | macro_topic | other | unknown`;
  - `target.reference`: `explicit | contextual | ambiguous | unresolved`;
  - `modifiers[]`: `humor | promotion`;
  - `assertiveness`: `hedged | unhedged | null`.
- **Powiązania między celami a tezami.** `stance.claim_ids` wskazuje tezy uzasadniające stanowisko, a `claim.target_ids` cele tezy (tabela §7.2, `contract-v2.md:244-255`). To pozwala pokazać cel razem z jego tezami bez grafu argumentów. Graf jest poza zakresem (`roadmap.md:193`).
- **Limity** (`contract-v2.md:266-281`): `targets` ≤ 12, `claims` ≤ 24, `anchor.quote` ≤ 100 znaków, `evidence.quote` ≤ 320, `statement` ≤ 240. `statement` jest w języku posta (`:178`). `assertiveness` opisuje sposób sformułowania przez autora, nie pewność modelu (`:168`).
- **Wynik prefiltra** (post bez treści) jest stały: `unclear` + `insufficient_content`, `resultSource = prefilter` (`contract-v2.md:309-329`). Karta pokazuje go dopiskiem „(bez treści, bez oceny modelu)” (`PostCard.vue:25-28`).
- **Brak wskazówek prezentacji.** Dokumenty kontraktu (`contract-v2.md`, `README.md`, `agent-implementation.md`, `examples.md`, `hardening-sources.md`) nie mają etykiet UI ani polskich nazw. Polskie etykiety są tylko w `szczur-radar-web/src/labels.ts:2-43`, dla zbiorów: `itemType`, `relevance`, `direction`, `flag` (oraz zbiorów admina). „Ostrożnie” dla `hedged` istnieje tylko jako tekst w `PostCard.vue`.

### 5. SPA: trasy, klient, lista, kontrakt wyglądu

- **Router** (`szczur-radar-web/src/router/index.ts`):
  - trasy `/login` (public), `/` (`home`, FeedView), `/admin` (`meta.admin`), dev-only `/kitchen-sink` (lazy, tylko gdy `import.meta.env.DEV`) i catch-all `/:pathMatch(.*)*` → `/` jako ostatnia (`:27-44`);
  - sesja jest domyślna, a `meta.public` z niej zwalnia (`:18-25`);
  - guard: brak sesji → `login?redirect=<fullPath>`, zalogowany na `/login` → `/`, nie-admin na trasie admina → `/` (`:49-61`);
  - sesja wygasła w trakcie (401) → powrót do logowania z `redirect` (`:64-69`);
  - `safeRedirect` przepuszcza tylko ścieżki tej samej witryny (`router/redirect.ts:5-15`);
  - nieznana ścieżka trafia w catch-all przed guardem, więc anonimowy głęboki link kończy się `login?redirect=/` (`router/index.test.ts:33-36`);
  - widoki są importowane statycznie, z wyjątkiem KitchenSink (`:12-14,38`).
- **Klient**: `apiFetch<T>` (`api/client.ts:56-82`) rzuca `ApiError(status, detail, retryAfter?)` dla każdej odpowiedzi nie-OK. Dla 401 dodatkowo uruchamia słuchaczy `onUnauthorized`. 404 nie ma osobnej obsługi. Błąd sieci przechodzi jako `TypeError`. Mapowanie `detail` na polski komunikat istnieje w jednym miejscu: `startErrorMessage` (`api/pipeline.ts:72-86`).
- **Typy**: `posts.ts` ma `PostItem`, `AssessmentOutput` (`claims: unknown[]`, `modifiers: string[]`, `Target.kind: string`, `Target.reference: string`) i jedną funkcję `fetchPosts`, bez pobierania po id (`posts.ts:5-90`).
- **Lista** (`views/FeedView.vue`):
  - stan w lokalnych `ref`; numer strony w `?page=` (od 0), więc odświeżenie strony go zachowuje (`:13-40`);
  - błąd, ładowanie i stan pusty są pisane ręcznie (`<p role="alert">`, `<p class="status">`), bez `StatusMessage`/`AppButton` (`:47-58`);
  - odstępy i rozmiary czcionek w px, nie z tokenów (`:63-90`).
- **Karta** (`components/PostCard.vue`):
  - `<article>` z datą i typem, tytułem albo początkiem treści (200 znaków), trafnością, celami ze stanowiskiem i flagami (`:9-39`);
  - żadnego `RouterLink`, `<a>` ani obsługi kliknięcia (`RouterLink` występuje tylko w `AppHeader.vue:33-36`).
- **AppHeader** podświetla link „Posty” tylko przy dokładnym dopasowaniu trasy (`.router-link-exact-active`, `AppHeader.vue:66-69`), więc na `/posts/:id` nie będzie podświetlony.
- **Kontrakt wyglądu**:
  - tokeny w `:root` pliku `style.css`: role kolorów `--bg`, `--text`, `--text-h`, `--accent`, `--on-accent`, `--success`, `--warning`, `--destructive`, `--ring`, `--border`, `--border-strong` (`:9-21`); skala `--font-size-sm|md|lg|xl|display` (`:23-27`); `--space-0…5` (`:29-34`); `--radius`, `--radius-sm` (`:36-37`); `--sans` (`:39`);
  - motyw ciemny przedefiniowuje tylko role kolorów (`:56-67`);
  - **nie ma tokenu czcionki o stałej szerokości** ani stylu dla `<pre>`/`<code>`.
- **Komponenty współdzielone**:
  - `AppButton` (`variant`, `type`, `disabled`, `busy`, `busyLabel`);
  - `StatusMessage` (`tone: ok|error` → `role=status|alert`);
  - `StatusBadge` (`tone: success|warning|destructive|neutral|info`);
  - `AppHeader` (bez propsów).

  Wyszukiwanie `<details|<summary|<pre|monospace|aria-expanded` w `szczur-radar-web/src` nie znajduje nic, więc przełącznik dla RAW i blok kodu byłyby nowymi elementami.
- **`styleContract.test.ts`**:
  - kontrast ≥ 4.5:1 dla 7 par ról w obu motywach (`:21-29,101-108`);
  - skan literałów (kolory hex/rgb/hsl/oklch, arbitralne wartości i klasy palety; nie łapie zwykłych `px`), linia po linii, dla 9 plików z listy `CLEANED` importowanych przez `?raw` (`:2-11,85-99,111-114`);
  - nowy widok trzeba dopisać i do importów, i do `CLEANED`.
- **Testy i wizualna kontrola**:
  - testy widoków mockują całe moduły API (`vi.mock('../api/posts')`), montują z prawdziwym routerem na `createMemoryHistory()` i czekają `flushPromises()` (`FeedView.test.ts:10-58`); stan ładowania testuje się obietnicą, która nigdy się nie rozwiązuje (`AdminView.test.ts:71-73`);
  - `dev/mock-api.js` mockuje tylko `/api/auth/me` i trasy admina; `/api/posts` i każda inna trasa dostaje 404 (`dev/mock-api.js:44-51`). Zrzuty widoku szczegółowego wymagają dopisania mocków;
  - REPOSITORY_GUIDELINES wymaga zrzutów obu motywów i szerokości 375 px oraz 7 stanów (default, hover, focus-visible, disabled, error, empty, loading) albo N/A z powodem.

## Code References

- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:43-88` — lista, DTO `PostItem`/`AssessmentView` z pełnym `AssessmentOutputV2`, `author`, `webUrl`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/AssessmentRepository.java:20-35` — `findLatestOk`/`findLatest` (tylko kolekcje id, `DISTINCT ON`)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/Post.java:42-44,177-179` — UUID id, `getRawPayload()`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/raw/RawPayloadStore.java:5-27` — granica magazynu RAW, `load`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/raw/PostgresRawPayloadStore.java:36-47` — gunzip + SHA-256
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/AssessmentInputs.java:37` — jedyny dzisiejszy wywołujący `load` w produkcji
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopPosts.java:43-53` — post = tekst wejścia oceny + `webUrl` + RAW (Jackson)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopItem.java:29-36` — budowa `webUrl`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ErrorCode.java:13-43` — zamknięty zbiór kodów błędów, brak 404
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ApiExceptionHandler.java:21-47` — obsługa 400/415
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java:51-58` — deny-by-default
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java:37-63,143-160` — rejestry tras, atrapa UUID
- `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java:39-43,87-98,171-177` — wzorzec testu API postów
- `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/PostSeeds.java:64-102` — post z RAW z fixture'a i oceną `ok`
- `szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:71-178` — `raw_payloads`, `posts`, `assessments`
- `szczur-radar-web/src/router/index.ts:18-69` — trasy, meta, guard, catch-all
- `szczur-radar-web/src/api/client.ts:5-82` — `ApiError`, `apiFetch`
- `szczur-radar-web/src/api/posts.ts:5-90` — typy postów, `claims: unknown[]`, `fetchPosts`
- `szczur-radar-web/src/components/PostCard.vue:9-39` — co karta pokazuje dziś
- `szczur-radar-web/src/views/FeedView.vue:13-90` — stan, paginacja z `?page=`, ręczne stany
- `szczur-radar-web/src/labels.ts:2-58` — polskie etykiety, `formatDateTime`
- `szczur-radar-web/src/style.css:8-67` — tokeny
- `szczur-radar-web/src/styleContract.test.ts:21-29,85-114` — kontrast i skan literałów
- `szczur-radar-web/dev/mock-api.js:1-51` — mock do zrzutów (tylko auth i admin)
- `context/foundation/llm-assessment/contract-v2.md:79-131,244-281,287-329` — kształt V2, słownik, §7.2, limity, cytaty, prefiltr

## Architecture Insights

- **Dane w jednym miejscu, RAW za interfejsem.** Odczyt RAW ma iść przez `RawPayloadStore` (lekcja „Oddziel RAW od danych operacyjnych”, `lessons.md:12-17`; `tech-stack-api.md:45-49`). Magazyn można przenieść (np. do bucketu) bez zmiany modelu domenowego i API. Widok RAW nie powinien więc zależeć od `raw_payloads` ani od gzipa.
- **Nowe źródło nie zmienia warstwy API** (Hard Rules w `REPOSITORY_GUIDELINES.md`). Link do oryginału jest liczony przez źródło i zapisany jako `web_url`, więc API i SPA nie muszą znać reguł URL Wykopu.
- **Kontrakt V2 jest kodem.** Typy SPA dla `claims`, `modifiers`, `target.kind` i `reference` mają odzwierciedlać rekordy Javy (`tech-stack-web.md:29`). Zamknięte zbiory trzyma się w typach unijnych i enumach, a nie w wolnych stringach (`lessons.md:68-73`).
- **Kody błędów to kontrakt z SPA.** `ErrorCode` + `ErrorCodeTest` + `client.ts`: nowy kod (np. dla 404) zmienia wszystkie trzy (`ErrorCode.java:8-11`).
- **UI wymaga kontraktu wyglądu.** Tokeny ze skali, komponenty z `src/components/`, nowy plik na liście `CLEANED`, nowy komponent współdzielony również w kitchen sinku (REPOSITORY_GUIDELINES, sekcja UI). Widok budowany pierwszy raz nie idzie przez `/10x-ui` (CLAUDE.md). `/10x-ui` dla nowego widoku przychodzi dopiero po tym, jak się renderuje.

## Historical Context (from prior changes)

- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:71` — **obowiązuje.** Widok szczegółowy, surowy JSON w UI i link do oryginału odłożone do S-03. S-02 zapisuje RAW, slug/URL i autora.
- `…/first-assessed-wykop-feed/plan.md:464` — **obowiązuje.** Karta ma zwięzłą ocenę (trafność, cele ze stanowiskiem, flagi), a „pełna prezentacja oceny to S-03”.
- `…/first-assessed-wykop-feed/plan.md:262` — **obowiązuje.** „Bezstratnie” znaczy: element Wykopu zserializowany przez Jacksona bez utraty pól i wartości.
- `…/first-assessed-wykop-feed/plan.md:51`, `:420` — **obowiązuje.** Wyniki prefiltra są na liście użytkownika. Posty FAILED/DEAD widzi tylko admin (`/api/admin/posts/failed`). Czy widok szczegółowy ma być osiągalny dla FAILED/DEAD, nie jest nigdzie ustalone.
- `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:39` — **częściowo nieaktualne.** Zapowiada `feed-view-ui-hardening` (FeedView + PostCard na `AppButton`/`StatusBadge`, px na skalę) przed S-03 i S-04. Nie istnieje jednak ani folder tej zmiany, ani wpis w roadmapie, a S-03 ma w roadmapie tylko S-02 jako warunek (`roadmap.md:189`). Kolejność trzeba więc ustalić od nowa.
- `context/archive/2026-09-27-assessment-contract-harness/frame.md:63` i `contract-v2.md:234` (§7.1.0) — **obowiązuje jako założenie projektowe.** Szeroki próg stanowiska jest uzasadniony tym, że „surowy post stoi obok oceny”. Widok szczegółowy jest więc miejscem, w którym użytkownik weryfikuje stanowisko jednym spojrzeniem.
- `…/assessment-contract-harness/implementation-notes.md:246-249` — **obowiązuje.** `WykopItem.webUrl()` zostaje w `src/main` jako zakres produktu S-03.
- `…/assessment-contract-harness/frame.md:113,122` — **nieaktualne.** Cytują dawne brzmienie S-03 („tezy i argumenty z cytatami”). Roadmapa ma teraz „bez grafu argumentów” (`roadmap.md:193`).
- `context/foundation/shape-notes.md:322` — **nieaktualne.** Opisuje ocenę jako „wartościowy / siła wydźwięku / streszczenie tezy”, czego V2 nie zawiera (`prd.md:260`).
- `context/foundation/roadmap.md:193` — **częściowo.** „Pierwszy odczyt surowego payloadu przez aplikację” nie jest dosłownie prawdą, bo pipeline czyta RAW już w S-02 (`AssessmentInputs.java:37`). S-03 to pierwszy odczyt widoczny dla użytkownika.
- `…/first-assessed-wykop-feed/follow-ups/review-fixes.md:76-82` i `deploy-plan.md` („PR-env verification: the web deployment's commit”) — **obowiązuje.** Przed sprawdzeniem UI w PR env potwierdź, że wdrożenie web ma commit z czubka gałęzi.

## Related Research

- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md` — S-02: pipeline, RAW, lista; otwarte wtedy pytania o metadane dla S-03 (`:237`), rozstrzygnięte w planie S-02 (`plan.md:226`)
- `context/archive/2026-10-10-admin-panel-ui-hardening/research.md` — audyt UI panelu admina, źródło kontraktu wyglądu i `mock-api.js`

## Open Questions

Decyzje dla `/10x-plan` (produktowe lub projektowe — badanie ich nie rozstrzyga):

1. **„Obok” czy „pod przełącznikiem”.** Czy domyślnie widać tekst posta (`title`/`body`, już zapisany w `posts`) obok oceny, a surowy JSON źródła jest pod przełącznikiem „dane dla nerdów”? To wniosek godzący `prd.md:37,58,62` z FR-009 (`prd.md:116`), nie zapisana decyzja.
2. **Kształt API.** Jedna trasa `GET /api/posts/{id}` z oceną i RAW naraz, czy ocena z `GET /api/posts/{id}` i RAW z osobnej trasy (np. `/api/posts/{id}/raw`) pobieranej dopiero po rozwinięciu przełącznika? Czy RAW ma przyjść jako zagnieżdżony JSON, czy jako tekst, i czy formatować go (wcięcia) w API, czy w SPA?
3. **Nieznane id i inne etapy.** Jaki kod i `detail` dla nieistniejącego posta (nowa stała w `ErrorCode` + `ErrorCodeTest` + obsługa w SPA)? Czy szczegóły są dostępne tylko dla `ASSESSED` (spójnie z listą), czy także dla `failed`/`dead` (np. dla admina z listy nieudanych)?
4. **Nawigacja.** Trasa `/posts/:id` przed catch-all. Jak karta linkuje do szczegółów (cała karta czy nagłówek)? Czy powrót ma zachować `?page=` listy? Czy „Posty” w `AppHeader` ma być podświetlone na stronie szczegółów?
5. **Polskie etykiety** dla `claim.kind`, `claim.attribution`, `target.kind`, `target.reference`, `modifiers`, `assertiveness` oraz układ: tezy pogrupowane według rodzaju czy według celu (przez `stance.claim_ids` / `claim.target_ids`)?
6. **Kolejność wobec `feed-view-ui-hardening`.** Zrobić ją przed S-03, włączyć do S-03 (karta i tak dostaje link), czy odłożyć? Wpływa na to, czy S-03 dotyka `PostCard`/`FeedView` tylko minimalnie.
7. **Nowe elementy kontraktu wyglądu.** Token czcionki o stałej szerokości i blok kodu dla RAW. Przełącznik jako natywny `<details>`/`<summary>` czy komponent współdzielony (wtedy także kitchen sink)?
8. **Opcjonalne.** Podświetlanie cytatów w treści posta (wymaga uwzględnienia normalizacji białych znaków, `contract-v2.md:287-305`), proweniencja oceny (model, data) i zachowanie przy `webUrl = null`.

## Follow-up 2026-10-13: po merge'u `feed-view-ui-hardening`

Gałąź przewinięta do `origin/main` `002da67`. Zawiera `404679f` (`feed-view-ui-hardening`, PR #48) i `002da67` (`railway-resource-limits`, tylko `.railway/railway.ts`). Backend od `6bebf82` się nie zmienił, więc kotwice API w sekcjach 1–4 obowiązują. Kotwice SPA z sekcji 5 i z Code References są nieaktualne dla plików wymienionych niżej. Obowiązują te:

- **`PostCard.vue` jest współdzielonym komponentem na kontrakcie wyglądu.** Lista `src/components` w `REPOSITORY_GUIDELINES.md` (sekcja UI) wymienia go z adnotacją „every variant on the kitchen sink”.
  - Nagłówek to tytuł albo pierwsze 200 znaków treści (`:10-13`).
  - Trafność jest `StatusBadge neutral` (`:23`), a dopisek prefiltra brzmi „bez treści, bez oceny modelu” (`:24`).
  - Cele są w `<dl>`: nazwa celu ma wagę 600 i `--text-h`, a kierunek jest tekstem w kolorze roli (`.bullish` → `--success`, `.bearish` → `--destructive`) z dopiskiem „(ostrożnie)” przy `hedged` (`:29-36`, `:91-116`).
  - Flagi to `StatusBadge neutral` (`:37-39`).
  - Klasa to `.post-card`, nie `.card`, bo globalne `.card` należy do logowania (`:46`).
  - Karta nadal nie ma żadnego linku ani kontrolki (`:18-43`). Hover i fokus karty są „N/A do S-03” (`archive/2026-10-12-feed-view-ui-hardening/ui-after/README.md:19`, deferred D1 w `…/research.md:98`).
- **`FeedView.vue` jest na `AppButton`/`StatusMessage`.**
  - Widok trzyma ostatnią pokazaną parę strona+odpowiedź (`:20-25`).
  - Odpowiedzi spóźnione są odrzucane licznikiem `latestLoad` (`:41-56`).
  - Zmiana `?page` przewija na górę i ustawia fokus na `h1` po wczytaniu. Pierwsze wejście nie przewija i nie zmienia fokusu (`:67-77`).
  - Błąd ma „Spróbuj ponownie”, a strona poza zakresem ma przycisk do ostatniej strony (`:86-97`).
  - Router nie ma `scrollBehavior` (`router/index.ts:46-47`). Powrót z widoku szczegółu montuje FeedView od nowa i wczytuje stronę jeszcze raz.
- **`dev/mock-api.js` obsługuje feed.**
  - `/api/posts` z trybami `data|loading|error|empty|loading-next|error-next` (`:4-11`, `:95-105`).
  - 120 postów w 6 wariantach (`:47-81`). Każdy ma `claims: []` i `modifiers: []` (`:78-79`), więc zrzuty S-03 potrzebują wariantów z tezami.
  - Inne ścieżki dostają 404 `not_found` (`:106`).
- **`styleContract.test.ts`.**
  - `FeedView.vue` i `PostCard.vue` są na liście `CLEANED` (`:87-96`).
  - Skan łapie też `px`/`rem` w `font-size`, `margin*`, `padding*`, `gap`, `letter-spacing` i `border-radius` (`OFF_SCALE`, `:107-108`).
  - `LoginView.vue` zdjęto z listy do `login-view-ui-hardening` (`:80-86`).
  - Nowy widok szczegółu trzeba dopisać do importów i do `CLEANED`.
- **Kitchen sink** ma sekcję „Posty” ze wszystkimi wariantami karty (`KitchenSinkView.vue:61-75`, `:189-191`). Fabryka `modelAssessment` daje zawsze `claims: []`.
- **`PostCard.test.ts`** montuje kartę bez routera (`:1-30`). Gdy karta dostanie `RouterLink`, testy karty i kitchen sinka będą potrzebować routera.
- **Brak tokenu czcionki o stałej szerokości.** `style.css` ma tylko `--sans` (`:39`).

**Rozstrzygnięte pytanie 6 (kolejność):** `feed-view-ui-hardening` jest zrobione i zmergowane przed S-03, zgodnie z decyzją użytkownika z 2026-10-12. S-03 dziedziczy z niego trzy rzeczy:

- wzorzec prezentacji stanowiska i flag;
- skan skali;
- mock `/api/posts`.

Granica z hardeningu obowiązuje: link do oryginału, autor i widok posta to S-03 (`archive/2026-10-12-feed-view-ui-hardening/plan.md:42`). Wpis o tej zmianie w „Historical Context” jest **zastąpiony**: zmiana istnieje i jest zarchiwizowana w `context/archive/2026-10-12-feed-view-ui-hardening/`.
