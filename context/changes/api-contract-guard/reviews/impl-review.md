<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-24: kontrakt SPA ↔ API pilnowany testem

- **Plan**: context/changes/api-contract-guard/plan.md
- **Scope**: Full plan (3 of 3 phases)
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-30
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — `run.json`: liczby kroków, których przebieg zakresu nie zapisuje

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java:430-437 (seed) → szczur-radar-web/src/contract/run.json:53,62
- **Detail**: Krok `store` ma `itemsIn: 12`, a serwer zapisuje `batch.posts().size()`, czyli pobrane minus pominięte = 10 (`RunSequence.java:114`). Krok `select` ma `itemsIn: null`, a w przebiegu zakresu dostaje `batch.newPostIds().size()` = 10 (`RunSequence.java:121`; null tylko w ponowieniu, `:126`). Plan wymaga „jak najwięcej pól z wartością” i danych zgodnych z regułą serwera. Kształt (klucze) jest poprawny, więc strażnik działa; nietrafne są tylko dwie wartości przykładowe, które ktoś może kiedyś przepisać do testu widoku.
- **Fix**: W seedzie ustawić `items_in` = 10 dla `store` i `select`, potem przepisać `run.json` i `sampleRun` w `samples.ts`.
- **Decision**: FIXED — seed `items_in` 10 dla `store` i `select`; `run.json` i `sampleRun` zaktualizowane

### F2 — Test nieudanego posta nie sprawdza, który post porównuje

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java:213
- **Detail**: `failedPostMatchesTheSharedContract` porównuje `items[0]` bez sprawdzenia identyfikatora, a `id` jest maskowany. Sąsiednie testy listy nieudanych najpierw sprawdzają `$.items[*].id … containsExactly(...)`. Gdy inna klasa IT ze wspólnym kontenerem zostawi zatwierdzony nieudany post, test padnie mylącym diffem całego ciała zamiast „obcy post na liście”. Fałszywie zielony być nie może (tytuł, treść i naruszenie są tylko tego testu).
- **Fix**: Przed porównaniem dodać `assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(failed.toString());`.
- **Decision**: FIXED — asercja `$.items[*].id` `containsExactly(failed)` przed porównaniem

### F3 — Komunikat „gotowe do wklejenia” ma inny format niż pliki

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractFiles.java:40-49
- **Detail**: `writerWithDefaultPrettyPrinter()` Jacksona pisze `"a" : [ "x" ]` (spacja przed dwukropkiem, tablice w jednej linii), a złote pliki mają styl `JSON.stringify` z wcięciem 2. Porównanie drzew działa, ale wklejenie ciała z komunikatu, jak radzi Javadoc, przepisze cały plik i da hałaśliwy diff przy każdym nowym polu.
- **Fix**: Zmienić Javadoc i komunikat na „zamaskowane ciało (sformatuj przed commitem)” albo skonfigurować `DefaultPrettyPrinter` w stylu plików.
- **Decision**: FIXED — Javadoc i komunikaty: „masked body (reformat before committing)”

### F4 — `okByModel` kopiuje 16-argumentowy literał `Provenance`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/posts/PostSeeds.java:95-117
- **Detail**: `okByModel` i `failed` mają ten sam pozycyjny konstruktor `Provenance` modelu (`MODEL, GEMINI, "gemini-3.1-flash-lite", … 1_000, 1`). Od obu wartości zależą `post.json` i `failed-post.json`, a dwie kopie łatwo rozjechać.
- **Fix**: Wydzielić prywatne `modelProvenance(String assessedAt)` używane przez obie metody.
- **Decision**: FIXED — prywatne `PostSeeds.modelProvenance(assessedAt)` dla `okByModel` i `failed`

## Scope and evidence

Stan wejściowy: HEAD `440c259`; w drzewie roboczym zmienione tylko `plan.md` (wiersz Manual 2.8 zaznaczony po potwierdzeniu właściciela 2026-10-30) i `change.md`. Progress 21/21. Zakres Git: `5a013ca..440c259`, faza 1 `a90397f`, faza 2 `8318374`, faza 3 `ce7865b`, epilog `440c259`.

Ten raport zastępuje wcześniejszy raport z 2026-10-30, który obejmował tylko fazy 1 i 3 (APPROVED, 0 ustaleń), bo faza 2 czekała na wiersz 2.8. Kod faz 1 i 3 się od tamtej pory nie zmienił (ten sam HEAD), więc ich ocena i dowody zostają przejęte:

- Faza 1 MATCH: ErrorCode (`INTERNAL_ERROR`, `forStatus`), ApiErrorController, jawny status w `ApiError.write`, awaria magazynu kont w AuthController → 500 `internal_error` z limiterem `ABORTED`, ApiErrorHttpIT, ErrorCodeTest, AuthIT, dokumentacja.
- Faza 3 MATCH: rules.json, SpaRulesTest, eksportowane stałe w adnotacjach i widokach, komunikaty ze stałych, rules.test.ts ze skanem kodów błędów, dokumentacja.

Faza 2 przejrzana teraz przez dwóch niezależnych agentów (zgodność z planem; bezpieczeństwo, jakość i wzorce):

- Złote pliki MATCH poza F1. `run.json`: przebieg zakresu `partial`, 4 kroki, 2 wyniki źródeł (`succeeded`, `failed`), `attentionReasons` w kolejności serwera, `fetched`/`skipped` jako sumy źródeł. `post.json`: ocena modelu (`resultSource: "model"`), cel bullish z `claim_ids`. `failed-post.json`: `failed` z `lastAttempt` i realnym naruszeniem. Nulle uzasadnione stanem (`interruptionCause`, `requestedPostIds`, `error` kroków, `itemsIn` kroku `fetch`).
- `ContractFiles` MATCH: maskowanie całych wartości (`matches()`), rekurencyjne, także w tablicach; porównanie drzew niezależne od kolejności kluczy. Ścieżka `../szczur-radar-web/…` działa w CI (job `backend` uruchamia Mavena w `szczur-radar-api` po pełnym checkout), tak jak `AlertSettingsTest`.
- Determinizm: kroki sortowane po `started_at`, wyniki źródeł po enumie, czasy z literałów i maskowane, sprzątanie seeda w `finally` w kolejności kluczy obcych.
- `samples.ts` MATCH: literały typowane adnotacją, bez `as` i spreadu, więc kontrola nadmiarowych pól działa także w zagnieżdżeniach. `shapes.test.ts`: trzy `toEqual`.
- Obraz web: `COPY . ./` + `vue-tsc -b`, `src/contract` nie jest w `.dockerignore`, JSON importuje tylko test (nie trafia do bundla).
- Dane testów SPA (pkt 4) MATCH; `assertiveness` w `StanceText.test.ts` wynika z tej samej reguły `StanceTable`. Asercje zmienione tylko tam, gdzie zależały od zmienionej wartości (`RunDetailView.test.ts`).
- EXTRA uzasadnione: `PostSeeds.okByModel` (istniejące `ok()` daje provenance prefiltra), helper `namePost`.
- Sekrety i dane osobowe: brak; treść posta to syntetyczny `ContractFixtures.BODY`.

## Verification

Faza 2, ponownie uruchomione 2026-10-30 na HEAD `440c259`:

| Kryterium | Polecenie | Wynik |
|-----------|-----------|-------|
| 2.1, 2.5 | `./mvnw -B verify` (API, atrapowe `LLM_*`) | PASS: 915 unit + 315 IT, 0 failures/errors; `PipelineRunControllerIT` 14/14, `PostsApiIT` 22/22; BUILD SUCCESS |
| 2.2 | `npx vitest run src/contract/shapes.test.ts` | PASS: 3/3 |
| 2.3 | `npm test && npm run build` (Node 24.21.0) | PASS; build 194 ms |
| 2.4 | `docker build -t sr-web-contract ./szczur-radar-web && docker rmi sr-web-contract` | PASS, image sha256:7e64d7c9f5bd88ec77bd16a302fdd4bdd2509e0fb5234bf2433f1ad62eb875c3 |
| 2.6, 2.7 | celowe zepsucia | wykonane przy implementacji (`8318374`): nowe pole `RunView` → czerwony `runDetailMatchesTheSharedContract`; pole usunięte z `Run` → `vue-tsc` TS2353 w `samples.ts`; pole usunięte z próbki → czerwony `shapes.test.ts` |
| 2.8 (Manual) | przegląd właściciela | `[x]` — właściciel potwierdził pliki 2026-10-30 |

Fazy 1 i 3 (z raportu wcześniejszego, ten sam HEAD): 1.1–1.5 i 3.1–3.8 PASS, w tym mutacje (bez `ApiErrorController` → 3/3 czerwone; `Passwords.MIN_LENGTH`; nowa wartość `RunStatus`; etykieta, powód, znak białej spacji, nieznany kod w atrapie → czerwone) oraz CI run 41022117099 zielony.

## Pending human verification

Brak — wszystkie wiersze Manual zaznaczone.

## Triage

Fixed: F1, F2, F3, F4 (4). Po poprawkach (2026-10-30): `./mvnw -B verify` PASS (915 unit + 315 IT; `PipelineRunControllerIT` 14/14, `PostsApiIT` 22/22), `shapes.test.ts` 3/3, `npm test` 499/499, `npm run build` PASS.
