# Widok szczegółowy posta (S-03) — plan implementacji

## Overview

Użytkownik otwiera post z listy pod adresem `/posts/:id` i widzi kolejno:

- pełny tekst posta;
- czytelną ocenę V2: cele ze stanowiskiem i z tezami z dosłownym cytatem, tezy ogólne, modyfikatory i flagi;
- link do oryginału na Wykopie;
- zwinięty przełącznik „Dane dla nerdów” z surowym JSON źródła, pobieranym dopiero po rozwinięciu.

Powrót prowadzi na to samo miejsce listy. Zakres: PRD US-01, FR-009 i FR-010 (oba `must-have`), roadmapa S-03, issue #16.

## Current State Analysis

**API** (bez zmian od `6bebf82`; research, sekcje 1–4):
- `GET /api/posts` zwraca `PostItem(id, itemType, publishedAt, title, body, author, webUrl, assessment)` z pełnym `AssessmentOutputV2` (`szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:43-88`). Trasy dla jednego posta nie ma.
- Ocena to najnowsza próba `ok` z `AssessmentRepository.findLatestOk(Collection<UUID>)` (`posts/AssessmentRepository.java:19-22`). Etap `ASSESSED` oznacza, że najnowsza próba jest `ok` (`posts/PostStage.java:8-14`), więc „ocena” w widoku szczegółu jest tą samą, którą pokazuje lista.
- `ErrorCode` to zamknięty kontrakt z SPA bez kodu 404 (`config/ErrorCode.java:13-43`). Test przypina dokładny zbiór kodów (`config/ErrorCodeTest.java:15-30`).
- Kontrolery mapują wyjątek domenowy na kod lokalnym `@ExceptionHandler` (`pipeline/api/PipelineRunController.java:112-122`). `ApiExceptionHandler` daje 400 `invalid_request` dla błędnego typu parametru, także zmiennej ścieżki (`config/ApiExceptionHandler.java:37-41`).
- **RAW.** `RawPayloadStore.load(RawPayloadRef)` zwraca dokładnie zapisane bajty (`raw/RawPayloadStore.java:19-25`). Zapisane bajty to element Wykopu (`JsonNode`, `sources/wykop/WykopSearchResult.java:22`) zapisany zwarto przez Jackson 3 (`sources/wykop/WykopPosts.java:25,52`). Fixture'y mają 1387 B i 2066 B. Jedynym produkcyjnym wywołującym `load` jest dziś pipeline (`pipeline/assess/AssessmentInputs.java:37`).
- **Bezpieczeństwo.** Domyślnie odmowa dostępu (`config/SecurityConfig.java:51-58`). `AuthorizationCompletenessIT` sam znajduje każdą trasę, podstawia zerowy UUID za `{id}` i oczekuje 401 (`auth/AuthorizationCompletenessIT.java:143-160`).

**SPA** (po `feed-view-ui-hardening`, `404679f`; research, follow-up 2026-10-13):
- **`PostCard` jest współdzielonym komponentem bez żadnej kontrolki** (`szczur-radar-web/src/components/PostCard.vue:18-43`).
  - Stanowisko to tekst w kolorze roli w `<dd>` z dopiskiem „(ostrożnie)” (`:29-36`, `:110-116`).
  - Hover i fokus karty są „N/A do S-03” (`context/archive/2026-10-12-feed-view-ui-hardening/ui-after/README.md:19`).
- **`FeedView` trzyma jedno źródło stanu strony** (`views/FeedView.vue:20-56`).
  - Zmiana `?page` przewija na górę i ustawia fokus na `h1` po wczytaniu.
  - Pierwsze wejście nie przewija i nie zmienia fokusu (`:67-77`).
- **Router** nie ma `scrollBehavior`, a trasa catch-all jest ostatnia (`router/index.ts:27-47`).
- **`apiFetch`** zwraca tylko JSON (`api/client.ts:56-82`).
- **Typy i etykiety.** `posts.ts` ma `claims: unknown[]`, a `kind`, `reference` i `modifiers` jako `string` (`api/posts.ts:11-27`). `labels.ts` nie ma zbiorów dla tez, celów ani modyfikatorów (`labels.ts:2-43`).
- **Styl.** `style.css` ma tylko `--sans` (`:39`). Skan `no literals` z listą `CLEANED` odrzuca kolory i `px`/`rem` w piśmie, odstępach i promieniach (`styleContract.test.ts:87-110`).
- **Mock i kitchen sink.**
  - `dev/mock-api.js` obsługuje feed. Każdy post ma tam `claims: []`, a inne ścieżki dostają 404 (`:47-106`).
  - Kitchen sink ma sekcję „Posty” (`views/KitchenSinkView.vue:61-75`, `:189-191`).

**Kontrakt V2** (`context/foundation/llm-assessment/contract-v2.md`):
- teza ma cele w `target_ids`, a teza ogólna ma `[]` (`:177`);
- `stance.claim_ids` wskazuje tezy, na których opiera się stanowisko, i każda z nich ma ten cel w `target_ids` (`:166`, `:255`);
- limity to 12 celów, 24 tezy i cytat dowodu do 320 znaków (`:266-281`);
- w MVP nie ma rodziców, więc `document` cytatu to zawsze `item`, a `reference = contextual` nie występuje (`:226`).

## Desired End State

**Z karty do widoku.** Na liście nagłówek każdej karty jest linkiem do `/posts/<id>`. Widok szczegółu pokazuje:

- nagłówek: tytuł, a bez tytułu „Wpis użytkownika X”;
- datę, typ i autora;
- link „Otwórz oryginał na wykop.pl”, otwierany w nowej karcie;
- pełny tekst posta z zachowanymi podziałami wierszy;
- sekcję „Ocena”: każdy cel z rodzajem, sposobem odniesienia, stanowiskiem i swoimi tezami (rodzaj, przypisanie, treść, dosłowny cytat, dopisek „podstawa stanowiska”), a dalej tezy ogólne, modyfikatory, flagi i datę oceny;
- zwinięte „Dane dla nerdów”: po rozwinięciu pobiera z `GET /api/posts/<id>/raw` sformatowany JSON elementu Wykopu i pokazuje go czcionką o stałej szerokości.

**Powrót.** Link „Posty” w widoku i przycisk Wstecz przeglądarki wracają do tej samej strony listy i do tego samego miejsca na niej.

**Brak posta.** Nieznane id, niepoprawne id i post nieoceniony dają komunikat „nie ma takiego posta” z linkiem do listy. API odpowiada wtedy 404 `post_not_found` (albo 400 dla niepoprawnego id).

**Weryfikacja:** `./mvnw -B verify`, `npm test`, `npm run build`, zrzuty 7 stanów w obu motywach i przy 375 px, przejście w Railway PR env na prawdziwych danych.

### Key Discoveries:

- `PostItem` ma już wszystko, czego potrzebuje widok poza RAW, więc trasa szczegółu zwraca ten sam rekord co lista (`PostController.java:66-74`). SPA i testy mają jeden kształt.
- Zapis RAW to `JSON.writeValueAsBytes(JsonNode)` (`WykopPosts.java:52`). Ponowne odczytanie przez Jackson i zapis z wcięciami nie zmieniają żadnej wartości. Dowodem jest test: odczyt odpowiedzi daje to samo drzewo co zapisane bajty, a duża liczba całkowita przechodzi dosłownie.
- Kod zgłasza 404 tak, jak `PipelineRunController` zgłasza `pipeline_busy`: wyjątek domenowy plus lokalny `@ExceptionHandler` (`PipelineRunController.java:112-122`). Globalny `ApiExceptionHandler` zostaje przy błędach kształtu żądania.
- Logika przewijania i fokusu z hardeningu (`FeedView.vue:67-77`) zostaje. Przywracanie pozycji dotyczy tylko powrotu z `post` do `home`.
- W MVP nie ma rodziców (`contract-v2.md:226`), więc widok nie potrzebuje etykiet dokumentu cytatu ani stanu „z kontekstu”.

## What We're NOT Doing

- **Widok i trasy dla postów `failed`/`dead` oraz link z listy nieudanych w `/admin`.** Decyzja: tylko `assessed`, jak lista (S-02, `archive/2026-10-06-first-assessed-wykop-feed/plan.md:51`).
- **RAW w odpowiedzi trasy szczegółu.** Decyzja: osobna trasa na żądanie.
- **Podświetlanie cytatów w treści posta.** Wymaga uwzględnienia normalizacji białych znaków (`contract-v2.md:287-305`).
- **Formatowanie treści Wykopu**: markup, linkowanie adresów w tekście, media i osadzenia. Są dostępne w RAW.
- **Graf argumentów** (`roadmap.md`, S-03 Risk) i nawigacja „poprzedni/następny post”.
- **Proweniencja poza `resultSource`/`assessedAt`**: model, wersje kontraktu i promptu.
- **Etykiety dokumentu cytatu** (`parent_post`/`parent_comment`), bo w MVP ich nie ma.
- **Podświetlenie „Posty” w `AppHeader` na stronie posta.** Link zostaje tylko nawigacją.
- **Migracje, zmiany danych i zmiana kształtu `GET /api/posts`.**
- **`/10x-ui` dla nowego widoku.** Nowy widok buduje zwykły łańcuch (CLAUDE.md). Przegląd `/10x-ui` może przyjść po merge'u.

## Implementation Approach

Kolejność: backend, potem komponenty oceny, potem widok i nawigacja, na końcu weryfikacja.

- **Backend.** Dwie trasy w `PostController` na istniejącym repozytorium, magazynie RAW i jednym nowym kodzie błędu. Brak migracji.
- **Ocena jako komponenty.** Zbudowana jako współdzielone komponenty (`StanceText`, `PostAssessment`), więc każdy wariant oceny da się obejrzeć na kitchen sinku, zanim powstanie trasa. To wydzielenie usuwa też powielanie stanowiska między kartą a widokiem (lekcja o wczesnym porządkowaniu).
- **Widok.** Powtarza wzorce `FeedView`: licznik żądań przeciw spóźnionym odpowiedziom, `StatusMessage` z `AppButton` „Spróbuj ponownie”, `aria-busy` w trakcie wczytywania, fokus na nagłówku po wczytaniu.
- **Przywracanie pozycji listy.** `scrollBehavior` routera czeka na sygnał FeedView „strona wczytana” tylko przy powrocie z widoku posta.
- **Proces.** Jedna gałąź, jeden PR i weryfikacja w Railway PR env. `/10x-impl-review` i `/10x-archive` idą na tej samej gałęzi, a merge robi człowiek (lessons.md).

## Critical Implementation Details

- **Bajty i kodowanie RAW.** Trasa `/raw` zwraca `byte[]` z jawnym `Content-Type: application/json`, a nie `String` ani `JsonNode`. `JsonNode` przeszedłby przez konwerter Jacksona i stracił wcięcia. `String` zależy od domyślnego zestawu znaków konwertera. Test sprawdza polskie znaki z fixture'u `entry.json`.
- **Przewijanie i `scrollBehavior`.** Zdefiniowanie `scrollBehavior` ustawia `history.scrollRestoration = 'manual'` dla całej aplikacji, więc funkcja musi jawnie objąć każdy przypadek:
  - powrót (z zapisaną pozycją) z `post` do `home`: przywróć pozycję po sygnale „strona wczytana” z FeedView albo nic nie rób po błędzie lub po kilku sekundach;
  - zmiana ścieżki: góra strony;
  - ta sama ścieżka (stronicowanie feedu, `?page`): nic, bo FeedView robi to sam (`FeedView.vue:67-77`).

  Przy powrocie FeedView montuje się od nowa (`previous === undefined`), więc nie przewija i nie zabiera fokusu. Pozycji nie nadpisze nic oprócz routera.

## Phase 1: API szczegółu posta i surowych danych

### Overview

Dwie trasy dla zalogowanego użytkownika: `GET /api/posts/{id}` (post z oceną) i `GET /api/posts/{id}/raw` (sformatowany JSON źródła). Obie obsługują tylko posty `assessed`. Nieznane id i inny etap dają 404 `post_not_found`.

### Changes Required:

#### 1. Kod błędu „nie znaleziono”

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ErrorCode.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/config/ErrorCodeTest.java`

**Intent**: Nowy kod w zamkniętym kontrakcie z SPA dla posta, którego nie ma albo który nie jest oceniony. Klient nie rozróżnia tych dwóch przypadków.

**Contract**: `POST_NOT_FOUND(HttpStatus.NOT_FOUND)`, na wyjściu `{"detail":"post_not_found"}`. Javadoc mówi „nie ma ocenionego posta o tym id: nieznane id albo etap inny niż `assessed`”. `ErrorCodeTest` dostaje parę `"post_not_found", 404`.

#### 2. Wyjątek domenowy

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/PostNotFoundException.java` (nowy)

**Intent**: Sygnał „brak ocenionego posta” z warstwy postów, mapowany na kod w kontrolerze. Wzorzec: `pipeline/PipelineBusyException.java`.

**Contract**: `public class PostNotFoundException extends RuntimeException`, konstruowany z `UUID id`.

#### 3. Trasy szczegółu i RAW

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java`

**Intent**: Szczegół i RAW jednego posta dla każdego zalogowanego. Obie trasy wybierają post tak samo: `findById` plus warunek `stage == ASSESSED`, inaczej `PostNotFoundException`. Javadoc klasy wymienia US-01, FR-009 i FR-010.

**Contract**:
- `GET /api/posts/{id}` (`UUID id`) zwraca `PostItem.of(post, findLatestOk(List.of(id)) …)`, czyli ten sam rekord i ten sam JSON co element listy.
- `GET /api/posts/{id}/raw` zwraca `ResponseEntity<byte[]>`, `Content-Type: application/json`. Treść to `RawPayloadStore.load(post.getRawPayload())` odczytana jako drzewo Jacksona 3 (`JsonMapper`) i zapisana z wcięciami. Wartości się nie zmieniają.
- Kontroler dostaje `RawPayloadStore` przez konstruktor.
- Lokalny `@ExceptionHandler(PostNotFoundException.class)` zwraca `ApiError.response(ErrorCode.POST_NOT_FOUND)`.
- Błąd odczytu RAW (`IllegalStateException` przy złej sumie SHA-256) zostaje błędem 500 z logiem Springa. Nie dostaje nowego kodu, bo nie powinien wystąpić.

#### 4. Testy API

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/PostSeeds.java`

**Intent**: Kontrakt obu tras, pokryty wzorcem `PostsApiIT` (`MockMvcTester`, `asUser()`, JSONPath; `:39-43`, `:171-177`).

**Contract**:
- **Szczegół posta `assessed` z oceną:** 200, a pola są takie jak element listy, w tym `assessment.output.claims` z `ContractFixtures.validOutput()`.
- **Odpowiedzi 404 `post_not_found`:**
  - post `stored`, `failed` i `dead`, na obu trasach;
  - nieznany UUID, na obu trasach.
- **Niepoprawne id** (`/api/posts/abc`): 400 `invalid_request`.
- **RAW posta z `entry.json`:**
  - 200 i `application/json`;
  - treść zawiera znaki nowego wiersza (wcięcia);
  - odczytana treść daje to samo drzewo JSON co zapisane bajty;
  - polskie znaki są nienaruszone.
- **Bezstratność:** RAW zapisany przez nową metodę `PostSeeds` z własnymi bajtami (np. liczba całkowita `12345678901234567890` i ułamek) wraca z tą liczbą dosłownie.
- **`AuthorizationCompletenessIT`** przechodzi bez zmian i obejmuje obie nowe trasy (401 bez sesji).

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `cd szczur-radar-api && ./mvnw -B verify` (w tym nowe przypadki `PostsApiIT`, `ErrorCodeTest` z `post_not_found` 404 i `AuthorizationCompletenessIT` z obiema nowymi trasami)

#### Manual Verification:

- W Railway PR env zalogowany użytkownik otwiera w przeglądarce `/api/posts/<id ocenionego posta>/raw` i widzi sformatowany JSON z polskimi znakami; `/api/posts/<losowy UUID>` odpowiada 404 `{"detail":"post_not_found"}`

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 2: Ocena posta jako komponenty

### Overview

Typy V2 w SPA, polskie etykiety i dwa nowe współdzielone komponenty: `StanceText` (stanowisko, używane też przez `PostCard`) i `PostAssessment` (pełna ocena). Oba są na kitchen sinku we wszystkich wariantach. Trasa jeszcze nie istnieje.

### Changes Required:

#### 1. Typy kontraktu V2

**File**: `szczur-radar-web/src/api/posts.ts`

**Intent**: Typy odzwierciedlają rekordy Javy (`agents/assessor/contract/*.java`) zamkniętymi uniami zamiast `string` i `unknown` (lessons.md, enumy). Komentarz „claims are for the detail view, S-03” znika.

**Contract**:
- Unie:
  - `ClaimKind` = `factual_claim | opinion | investment_thesis | question | trade_action`;
  - `Attribution` = `author | reported | unclear`;
  - `TargetKind` = `company | asset | sector | macro_topic | other | unknown`;
  - `Reference` = `explicit | contextual | ambiguous | unresolved`;
  - `Modifier` = `humor | promotion`;
  - `Assertiveness` = `hedged | unhedged | null`.
- `Quote { document: 'item' | 'parent_post' | 'parent_comment'; field: 'title' | 'body'; quote: string }`.
- `Stance { direction; assertiveness; claim_ids }` jako nazwany typ.
- `Claim { id; kind; attribution; target_ids: string[]; evidence: Quote; statement: string }`.
- Zmiany w istniejących typach:
  - `Target.kind: TargetKind`, `Target.reference: Reference`, `Target.anchor: Quote`;
  - `AssessmentOutput.claims: Claim[]`, `AssessmentOutput.modifiers: Modifier[]`.

#### 2. Etykiety

**File**: `szczur-radar-web/src/labels.ts`

**Intent**: Polskie nazwy nowych zbiorów, w tym samym wzorcu `label(set, value)`.

**Contract**:
- `claimKind`: fakt, opinia, teza inwestycyjna, pytanie, działanie.
- `attribution`: autor, przytoczone, nie wiadomo czyje.
- `targetKind`: spółka, aktywo, sektor, makro i rynek, inne, nieokreślony.
- `reference`: nazwany wprost, z kontekstu, niejednoznaczny, nierozpoznany.
- `modifier`: humor, promocja.

#### 3. `StanceText` — stanowisko jako tekst

**File**: `szczur-radar-web/src/components/StanceText.vue` (nowy), `szczur-radar-web/src/components/PostCard.vue`

**Intent**: Jedno miejsce dla zapisu stanowiska z hardeningu: kierunek w kolorze roli i „(ostrożnie)” przy `hedged`. `PostCard` go używa, a jej wygląd się nie zmienia.

**Contract**:
- Prop `stance: Stance`.
- Renderuje `label('direction', …)` z dopiskiem ` (ostrożnie)`, gdy `assertiveness === 'hedged'`.
- Kolory: `bullish` → `--success`, `bearish` → `--destructive`, reszta dziedziczy kolor.
- `PostCard` przenosi tę logikę i style z `:32-34` i `:110-116` do komponentu.

#### 4. `PostAssessment` — pełna ocena

**File**: `szczur-radar-web/src/components/PostAssessment.vue` (nowy)

**Intent**: Czytelna ocena V2 pogrupowana według celów, tak żeby stanowisko stało obok tez, na których się opiera (decyzja: „tezy pod swoimi celami”; `contract-v2.md:234`).

**Contract**: Prop `assessment: NonNullable<PostItem['assessment']>`. Kolejność treści:

1. **Linia meta:**
   - `StatusBadge neutral` z trafnością;
   - `StatusBadge neutral` dla każdego modyfikatora;
   - dopisek „bez treści, bez oceny modelu”, gdy `resultSource === 'prefilter'`;
   - „oceniono {formatDateTime(assessedAt)}”.
2. **Cele w kolejności modelu.** Każdy cel to sekcja z nagłówkiem `h3`, w kolejności:
   - nagłówek: cytat celu (`anchor.quote`, waga 600, `--text-h`);
   - „{targetKind} · {reference}”;
   - `StanceText`;
   - lista tez, które mają ten cel w `target_ids`, w kolejności modelu.

   Każda teza ma:
   - wiersz „{claimKind} · {attribution}” z dopiskiem „podstawa stanowiska”, gdy jej id jest w `stance.claim_ids`;
   - `statement`;
   - `evidence.quote` w `<blockquote>`.

   Teza o kilku celach pojawia się pod każdym z nich.
3. **Sekcja „Tezy ogólne”**: tezy z `target_ids: []`, tylko gdy są.
4. **Flagi** jako `StatusBadge neutral` z etykietą sekcji „Zastrzeżenia”.
5. **Brak celów i tez:** przy ocenie modelu „Model nie wskazał celów ani tez.”, przy prefiltrze tylko dopisek z linii meta.

Odstępy i pismo pochodzą ze skali. Długie cytaty (do 320 znaków) zawijają się bez przewijania w poziomie przy 375 px.

#### 5. Kitchen sink i kontrola stylu

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`, `szczur-radar-web/src/styleContract.test.ts`

**Intent**: Każdy nowy komponent we wszystkich wariantach, zgodnie z regułą repo dla komponentów współdzielonych. Oba pliki na liście `CLEANED`.

**Contract**:
- Sekcja „Stanowisko”: 6 kierunków z `assertiveness` zgodnym z tabelą §7.2.
- Sekcja „Ocena posta” z wariantami:
  - cele z tezami, w tym teza o dwóch celach i tezy ogólne;
  - modyfikatory i flagi;
  - teza `reported` i `unclear`;
  - cytat dowodu o długości 320 znaków;
  - ocena modelu bez celów i tez;
  - prefiltr.
- Dane muszą spełniać kontrakt: każde `claim_ids` wskazuje tezę z tym celem, a kierunkowe stanowisko ma tezę autora.
- `styleContract.test.ts`: importy `?raw` oraz wpisy `src/components/StanceText.vue` i `src/components/PostAssessment.vue` w `CLEANED`.

#### 6. Testy komponentów

**File**: `szczur-radar-web/src/components/StanceText.test.ts`, `szczur-radar-web/src/components/PostAssessment.test.ts` (nowe), `szczur-radar-web/src/components/PostCard.test.ts`

**Intent**: Grupowanie i oznaczenia oceny przypięte testami. Zachowanie karty bez zmian.

**Contract**:
- **`StanceText`:**
  - dopisek tylko przy `hedged`;
  - klasa kierunku.
- **`PostAssessment`:**
  - teza pod swoim celem;
  - teza o dwóch celach pod oboma;
  - „Tezy ogólne” tylko przy tezach bez celu;
  - „podstawa stanowiska” tylko dla tez z `stance.claim_ids`;
  - komunikat pustej oceny modelu, a przy prefiltrze tylko dopisek;
  - etykiety modyfikatorów, rodzaju i przypisania.
- **`PostCard.test.ts`** przechodzi bez zmian asercji.

### Success Criteria:

#### Automated Verification:

- Testy i build web przechodzą: `cd szczur-radar-web && npm test && npm run build`
- `StanceText.vue` i `PostAssessment.vue` są na liście `CLEANED`: `grep -c "StanceText.vue\|PostAssessment.vue" szczur-radar-web/src/styleContract.test.ts` zwraca co najmniej 2

#### Manual Verification:

- `/kitchen-sink` (`npm run dev`): sekcje „Stanowisko” i „Ocena posta” czytelne w obu motywach i przy 375 px, bez poziomego przewijania; sekcja „Posty” wygląda jak przed zmianą

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 3: Widok posta i nawigacja

### Overview

Trasa `/posts/:id` z widokiem posta, przełącznikiem RAW pobieranym na żądanie, linkiem z karty i powrotem na to samo miejsce listy. Mock dostaje trasy szczegółu do zrzutów.

### Changes Required:

#### 1. Klient i pobieranie

**File**: `szczur-radar-web/src/api/client.ts`, `szczur-radar-web/src/api/client.test.ts`, `szczur-radar-web/src/api/posts.ts`

**Intent**: Odpowiedź tekstowa obok JSON, z tą samą obsługą błędów, czyli `ApiError` i słuchaczami 401. Do tego dwie funkcje pobierające.

**Contract**:
- `apiFetchText(path, init?, options?): Promise<string>` dzieli z `apiFetch` jedną wewnętrzną ścieżkę żądania i błędów.
- `fetchPost(id): Promise<PostItem>` woła `/api/posts/${encodeURIComponent(id)}`.
- `fetchPostRaw(id): Promise<string>` woła `/api/posts/${encodeURIComponent(id)}/raw`.
- `client.test.ts`: tekst przy 200, `ApiError` z `detail` przy 404, słuchacz przy 401.

#### 2. Trasa i przewijanie

**File**: `szczur-radar-web/src/router/index.ts`, `szczur-radar-web/src/router/scroll.ts` (nowy), `szczur-radar-web/src/router/index.test.ts`, `szczur-radar-web/src/router/scroll.test.ts` (nowy)

**Intent**: Widok posta pod sesją, przed catch-all. Powrót z posta na listę przywraca pozycję dopiero po wczytaniu listy.

**Contract**:
- Trasa `{ path: '/posts/:id', name: 'post', component: PostDetailView }` stoi przed `/:pathMatch(.*)*`.
- `scroll.ts` eksportuje:
  - `scrollBehavior(to, from, savedPosition)`, przekazywane do `createRouter`, z regułami z „Critical Implementation Details”;
  - funkcję, którą FeedView zgłasza zakończenie wczytywania (`ok` albo błąd).
- Przywracanie czeka na pierwsze zgłoszenie po nawigacji, najwyżej kilka sekund.
- `index.test.ts`:
  - anonimowe `/posts/x` prowadzi na `login?redirect=/posts/x`;
  - zalogowany wchodzi na `post`.
- `scroll.test.ts`:
  - powrót `post` → `home` z pozycją przewija po zgłoszeniu `ok`;
  - po błędzie nie przewija;
  - inna ścieżka → góra;
  - ta sama ścieżka z innym `?page` → brak przewinięcia.

#### 3. FeedView zgłasza wczytanie

**File**: `szczur-radar-web/src/views/FeedView.vue`, `szczur-radar-web/src/views/FeedView.test.ts`

**Intent**: Sygnał dla `scrollBehavior`. Poza nim zachowanie z hardeningu się nie zmienia.

**Contract**:
- `load` (`:41-56`) po udanej odpowiedzi i wyrenderowaniu listy (`nextTick`) zgłasza `ok`, a po błędzie zgłasza błąd.
- Odpowiedzi odrzucone przez `latestLoad` nic nie zgłaszają.
- Test: zgłoszenie po wczytaniu i po błędzie.

#### 4. Link z karty

**File**: `szczur-radar-web/src/components/PostCard.vue`, `szczur-radar-web/src/components/PostCard.test.ts`

**Intent**: Wejście w post z listy przez nagłówek karty. Jeden link na kartę, karta sama nie jest klikalna.

**Contract**:
- `<h2 class="heading">` zawiera `RouterLink` do `{ name: 'post', params: { id: post.id } }` z tekstem nagłówka.
- Kolor `--accent`, bo w repo akcent oznacza interakcję. Podkreślenie przy hover, fokus z globalnego `:focus-visible`.
- `PostCard.test.ts` montuje kartę z routerem albo z `RouterLinkStub` i sprawdza `href` `/posts/1`. Kitchen sink działa w routerze aplikacji bez zmian.

#### 5. Widok posta

**File**: `szczur-radar-web/src/views/PostDetailView.vue` (nowy), `szczur-radar-web/src/views/PostDetailView.test.ts` (nowy), `szczur-radar-web/src/style.css`

**Intent**: Strona posta: tekst nad oceną, link do oryginału i zwinięty JSON źródła pobierany na żądanie. Wzorce stanów pochodzą z `FeedView`.

**Contract**:
- **Układ i powrót.** `AppHeader`, `<main class="post-detail">` o szerokości `max-width: 48rem`. Nad treścią link „← Posty”:
  - gdy poprzedni wpis historii (`router.options.history.state.back`) to `home`, wywołuje `router.back()`;
  - w przeciwnym razie (wejście z głębokiego linku) prowadzi na `/`.
- **Wczytywanie.** Pobiera dane przez `fetchPost(route.params.id)`. Zmiana `id` wczytuje od nowa, a licznik żądań odrzuca spóźnione odpowiedzi.
- **Stany:**
  - ładowanie: „Wczytywanie posta…” z `aria-busy`;
  - błąd 5xx i sieci: `StatusMessage error` „Nie udało się wczytać posta.” i `AppButton` „Spróbuj ponownie”;
  - 404 albo 400: „Nie ma takiego posta albo nie został jeszcze oceniony.” z linkiem do listy.
- **Dane.** Po wczytaniu fokus idzie na `h1` (`tabindex="-1"`). Kolejność treści:
  - **`h1` o rozmiarze `--font-size-lg`:** tytuł; bez tytułu „{Typ} użytkownika {autor}”; bez autora „{Typ}”.
  - **Metadane:** data, typ, autor.
  - **Link do oryginału.** Link „Otwórz oryginał na {host}” z `target="_blank"` i `rel="noopener noreferrer"`. Renderowany tylko dla adresu `https:`/`http:`; przy `webUrl = null` albo innym protokole linku nie ma.
  - **Tekst posta:** `body`, a dla znaleziska z tytułem opis pod nagłówkiem. `white-space: pre-wrap` i `overflow-wrap: anywhere`.
  - **Ocena:** `h2` „Ocena” i `PostAssessment`, a bez oceny „Brak oceny.”.
  - **Dane źródła:** `<details>` z `<summary>` „Dane dla nerdów: surowy JSON źródła”.
    - Przy pierwszym otwarciu woła `fetchPostRaw`. Stany:
      - ładowanie: „Wczytywanie danych…”;
      - błąd: `StatusMessage error` i „Spróbuj ponownie”;
      - dane: `<pre>` z `font-family: var(--mono)`, `--font-size-sm`, `white-space: pre-wrap` i `overflow-wrap: anywhere`, bez poziomego przewijania strony.
    - Ponowne otwarcie nie pobiera drugi raz.
- **`style.css`:** token `--mono` (np. `ui-monospace, SFMono-Regular, Menlo, Consolas, monospace`) w `:root` obok `--sans`.
- **Testy:**
  - stany ładowania, błędu z ponowieniem, 404, 400;
  - reguła `h1`;
  - pełny tekst;
  - link tylko dla `http(s)` i brak linku przy `null`;
  - RAW pobierany raz przy pierwszym otwarciu, błąd RAW z ponowieniem;
  - fokus na `h1`;
  - „← Posty”: wstecz, gdy poprzedni wpis to lista, inaczej `/`.

#### 6. Mock do zrzutów i lista oczyszczonych plików

**File**: `szczur-radar-web/dev/mock-api.js`, `szczur-radar-web/src/styleContract.test.ts`

**Intent**: Zrzuty widoku posta bez API. Nowy widok pod skanem stylu.

**Contract**:
- **Trasy:**
  - `/api/posts/feed-<n>` zwraca `feedItem(n)`;
  - inne id dostają 404 `{"detail":"post_not_found"}`;
  - `/api/posts/<id>/raw` zwraca tekst sformatowanego JSON na wzór elementu Wykopu, z polskimi znakami.
- **Tryby:**
  - `loading` i `error` obejmują też szczegół;
  - nowe `raw-loading` i `raw-error` dotyczą tylko `/raw`.
- **Dane:** część wariantów `feedAssessments` dostaje tezy zgodne z kontraktem, w tym tezę ogólną i tezę o dwóch celach.
- **Nagłówek** pliku opisuje nowe trasy i tryby.
- **`styleContract.test.ts`:** `src/views/PostDetailView.vue` w `CLEANED`.

### Success Criteria:

#### Automated Verification:

- Testy i build web przechodzą: `cd szczur-radar-web && npm test && npm run build` (w tym `PostDetailView.test.ts`, `scroll.test.ts`, nowe przypadki routera, karty, klienta i FeedView)
- `PostDetailView.vue` jest na liście `CLEANED`: `grep -c "PostDetailView.vue" szczur-radar-web/src/styleContract.test.ts` zwraca co najmniej 1

#### Manual Verification:

- Lokalnie z mockiem (`dev/mock-api.js`, procedura w nagłówku): z drugiej strony listy, przewiniętej w dół, wejście w post i „← Posty” (a także Wstecz przeglądarki) wracają na tę samą stronę i to samo miejsce; „Dane dla nerdów” pokazują JSON dopiero po rozwinięciu; nieznane id pokazuje komunikat „nie ma takiego posta” z linkiem do listy

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 4: Weryfikacja wizualna, dokumenty i PR env

### Overview

Zrzuty 7 stanów nowego widoku i karty z linkiem, reguła repo o nowych komponentach oraz sprawdzenie w Railway PR env na prawdziwych danych.

### Changes Required:

#### 1. Zrzuty i macierz stanów

**File**: `context/changes/post-detail-view/ui/` (PNG + `README.md`)

**Intent**: Dowód kontraktu wyglądu dla nowego widoku (REPOSITORY_GUIDELINES, sekcja UI). Zamyka „N/A do S-03” dla hover i fokusu karty z hardeningu.

**Contract**:
- **Zrzuty:** 1280×900 i 375×812, jasny i ciemny, `/posts/feed-<n>` w trybach `data`, `loading`, `error`, `raw-loading`, `raw-error` i z nieznanym id.
- **`README.md`:** tabela 7 stanów, w każdym wierszu plik albo N/A z powodem:
  - default;
  - hover i focus-visible: link w nagłówku karty, „← Posty”, link do oryginału, `<summary>`;
  - disabled: N/A, widok nie ma wyłączanych kontrolek;
  - error: błąd posta i błąd RAW;
  - empty: nieznany post, ocena modelu bez celów, prefiltr;
  - loading: post i RAW.
- **Kontrola szerokości:** brak poziomego przewijania strony przy 375 px.
- **Kitchen sink:** sekcje „Stanowisko” i „Ocena posta”.

#### 2. Reguła repo

**File**: `REPOSITORY_GUIDELINES.md` (sekcja „UI (szczur-radar-web)”)

**Intent**: Następny agent znajduje nowe komponenty i wie, że mock obsługuje widok posta.

**Contract**:
- Punkt o komponentach współdzielonych wymienia `StanceText` (stanowisko wobec celu) i `PostAssessment` (pełna ocena V2; wszystkie warianty w kitchen sinku).
- Punkt o zrzutach mówi, że mock obsługuje `/admin`, `/` i `/posts/:id`.

#### 3. Weryfikacja w Railway PR env

**File**: `context/changes/post-detail-view/ui/README.md` (sekcja „PR env”)

**Intent**: Sprawdzenie na prawdziwych postach i prawdziwym RAW z Wykopu.

**Contract**: Przed sprawdzeniem UI wdrożenie `szczur-radar-web` w PR env musi mieć commit z czubka gałęzi (`deploy-plan.md`, „PR-env verification: the web deployment's commit”). Zapis zawiera datę, adres, commit web, liczbę postów i zrzuty.

### Success Criteria:

#### Automated Verification:

- Testy i build web przechodzą: `cd szczur-radar-web && npm test && npm run build`
- Testy backendu przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- `REPOSITORY_GUIDELINES.md` wymienia nowe komponenty: `grep -c "PostAssessment" REPOSITORY_GUIDELINES.md` zwraca co najmniej 1
- CI na PR jest zielone: `gh pr checks <PR>`

#### Manual Verification:

- `ui/README.md` ma tabelę 7 stanów z plikami albo N/A z powodem, w obu motywach i przy 375 px, bez poziomego przewijania strony
- W Railway PR env (prawdziwe dane, zalogowany użytkownik): wejście w post z listy i powrót na to samo miejsce, JSON źródła z polskimi znakami po rozwinięciu, link otwiera oryginał na Wykopie, anonimowy głęboki link `/posts/<id>` prowadzi przez logowanie z powrotem do posta, nieznane id pokazuje komunikat „nie ma takiego posta”

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka. Potem `/10x-impl-review` i `/10x-archive` na tej samej gałęzi; merge robi człowiek.

---

## Testing Strategy

### Unit Tests:

- `ErrorCodeTest`: dokładny zbiór kodów z `post_not_found` 404.
- `StanceText`, `PostAssessment`: grupowanie tez według celu, teza o kilku celach, tezy ogólne, „podstawa stanowiska”, pusta ocena, prefiltr, etykiety.
- `client.ts`: `apiFetchText` (tekst, `ApiError`, 401).
- `scroll.ts`: cztery reguły `scrollBehavior` i rezygnacja po błędzie lub czasie.
- `PostDetailView`: stany, `h1`, link do oryginału, leniwy RAW, fokus, „← Posty”.
- `PostCard`, `FeedView`, router: link z karty, zgłoszenie wczytania, trasa pod sesją przed catch-all.

### Integration Tests:

- `PostsApiIT`:
  - szczegół (200);
  - 404 dla etapów `stored`, `failed`, `dead` i nieznanego id na obu trasach;
  - 400 dla niepoprawnego id;
  - RAW z wcięciami, to samo drzewo, polskie znaki, duża liczba dosłownie.
- `AuthorizationCompletenessIT`: obie trasy dają 401 bez sesji.

### Manual Testing Steps:

1. Mock: lista na stronie 2, przewinięcie, wejście w post, „← Posty” i Wstecz wracają na to samo miejsce.
2. Mock: „Dane dla nerdów” w trybach `data`, `raw-loading` i `raw-error`, „Spróbuj ponownie”.
3. Mock: `/posts/brak` pokazuje komunikat i link do listy.
4. PR env: te same kroki na prawdziwych danych oraz link do Wykopu i anonimowy głęboki link.

## Performance Considerations

Szczegół to dwa zapytania: post po kluczu głównym i najnowsza ocena `ok` przez istniejący indeks `ix_assessments_post_latest`. RAW ma 1–2 kB, jest rozpakowywany i formatowany tylko na żądanie przełącznika. Lista się nie zmienia.

## Migration Notes

Brak migracji i zmian danych. `post_not_found` dopisuje się do zamkniętego zbioru kodów, a istniejące kody i `GET /api/posts` zostają bez zmian. Nowe trasy są tylko do odczytu. Wycofanie to cofnięcie commitu.

## References

- Research: `context/changes/post-detail-view/research.md` (z follow-upem 2026-10-13)
- Roadmapa: `context/foundation/roadmap.md` (S-03); PRD: `context/foundation/prd.md` (US-01, FR-009, FR-010)
- Kontrakt: `context/foundation/llm-assessment/contract-v2.md` (słownik `:110-131`, §7.1.0 `:234`, §7.2 `:244-255`, limity `:266-281`, §6.8 `:226`)
- Wzorce: `PostController.java:43-88`, `PipelineRunController.java:112-122`, `PostsApiIT.java:39-43,171-177`, `PostSeeds.java:64-102`, `FeedView.vue:20-77`, `PostCard.vue:18-43`, `dev/mock-api.js:47-106`
- Poprzednia zmiana UI: `context/archive/2026-10-12-feed-view-ui-hardening/` (plan, `ui-after/README.md`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: API szczegółu posta i surowych danych

#### Automated

- [x] 1.1 Testy backendu przechodzą: `cd szczur-radar-api && ./mvnw -B verify` (w tym nowe przypadki `PostsApiIT`, `ErrorCodeTest` z `post_not_found` 404 i `AuthorizationCompletenessIT` z obiema nowymi trasami) — 25c388b

#### Manual

- [x] 1.2 W Railway PR env zalogowany użytkownik otwiera w przeglądarce `/api/posts/<id ocenionego posta>/raw` i widzi sformatowany JSON z polskimi znakami; `/api/posts/<losowy UUID>` odpowiada 404 `{"detail":"post_not_found"}` — add170b

### Phase 2: Ocena posta jako komponenty

#### Automated

- [x] 2.1 Testy i build web przechodzą: `cd szczur-radar-web && npm test && npm run build` — 04883a2
- [x] 2.2 `StanceText.vue` i `PostAssessment.vue` są na liście `CLEANED`: `grep -c "StanceText.vue\|PostAssessment.vue" szczur-radar-web/src/styleContract.test.ts` zwraca co najmniej 2 — 04883a2

#### Manual

- [x] 2.3 `/kitchen-sink` (`npm run dev`): sekcje „Stanowisko” i „Ocena posta” czytelne w obu motywach i przy 375 px, bez poziomego przewijania; sekcja „Posty” wygląda jak przed zmianą — 04883a2

### Phase 3: Widok posta i nawigacja

#### Automated

- [x] 3.1 Testy i build web przechodzą: `cd szczur-radar-web && npm test && npm run build` (w tym `PostDetailView.test.ts`, `scroll.test.ts`, nowe przypadki routera, karty, klienta i FeedView) — 16fe2de
- [x] 3.2 `PostDetailView.vue` jest na liście `CLEANED`: `grep -c "PostDetailView.vue" szczur-radar-web/src/styleContract.test.ts` zwraca co najmniej 1 — 16fe2de

#### Manual

- [x] 3.3 Lokalnie z mockiem (`dev/mock-api.js`, procedura w nagłówku): z drugiej strony listy, przewiniętej w dół, wejście w post i „← Posty” (a także Wstecz przeglądarki) wracają na tę samą stronę i to samo miejsce; „Dane dla nerdów” pokazują JSON dopiero po rozwinięciu; nieznane id pokazuje komunikat „nie ma takiego posta” z linkiem do listy — 16fe2de

### Phase 4: Weryfikacja wizualna, dokumenty i PR env

#### Automated

- [x] 4.1 Testy i build web przechodzą: `cd szczur-radar-web && npm test && npm run build` — add170b
- [x] 4.2 Testy backendu przechodzą: `cd szczur-radar-api && ./mvnw -B verify` — add170b
- [x] 4.3 `REPOSITORY_GUIDELINES.md` wymienia nowe komponenty: `grep -c "PostAssessment" REPOSITORY_GUIDELINES.md` zwraca co najmniej 1 — add170b
- [x] 4.4 CI na PR jest zielone: `gh pr checks <PR>` — add170b

#### Manual

- [x] 4.5 `ui/README.md` ma tabelę 7 stanów z plikami albo N/A z powodem, w obu motywach i przy 375 px, bez poziomego przewijania strony — add170b
- [x] 4.6 W Railway PR env (prawdziwe dane, zalogowany użytkownik): wejście w post z listy i powrót na to samo miejsce, JSON źródła z polskimi znakami po rozwinięciu, link otwiera oryginał na Wykopie, anonimowy głęboki link `/posts/<id>` prowadzi przez logowanie z powrotem do posta, nieznane id pokazuje komunikat „nie ma takiego posta” — add170b
