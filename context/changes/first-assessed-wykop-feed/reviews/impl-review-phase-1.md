<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-02 first-assessed-wykop-feed — plan implementacji

- **Plan**: context/changes/first-assessed-wykop-feed/plan.md
- **Scope**: Phase 1 of 6
- **Reviewed phases**: 1
- **Date**: 2026-10-06
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Oba ostrzeżenia dotyczą niepełnego domknięcia F8d i wymagają poprawki przed produkcyjnym podłączeniem Wykopu w fazie 2. Nie są drobnymi uwagami stylistycznymi, dlatego ogólny werdykt to NEEDS ATTENTION mimo zielonych bramek.

## Findings

### F1 — Surowa przyczyna wyjątku ujawnia sekret mimo maskowania

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java:158
- **Detail**: F8d wymaga maskowania klucza, sekretu i tokenu w błędach, również z `auth`. `parse()` maskuje komunikat nadrzędny przez `clean()`, ale przekazuje oryginalny `JacksonException` jako `cause`. Przy niepoprawnym JSON zawierającym skonfigurowany sekret Jackson umieszcza ten sekret w swoim komunikacie, więc `getCause().getMessage()` i `printStackTrace()` nadal go ujawniają. Analogicznie `send()` zachowuje surowy `IOException` jako przyczynę na linii 135. Zachowywanie przyczyn było obecne przed tą fazą; nowa redakcja F8d pozostawiła tę ścieżkę niezabezpieczoną. Niezależna reprodukcja na skompilowanym kliencie i rzeczywistym Jacksonie, wyłącznie z syntetycznym sekretem, dała `wrapperLeaksSecret=false`, `causeLeaksSecret=true`, `stackTraceLeaksSecret=true`. Obecne testy maskowania sprawdzają tylko `getMessage()`.
- **Fix**: Po zbudowaniu bezpiecznego komunikatu nie dołączać surowych przyczyn z `parse()` i `send()`; dodać test całego `printStackTrace()` dla odpowiedzi zawierającej syntetyczny sekret/klucz/token, również podczas `auth`.
- **Decision**: PENDING

### F2 — Odświeżenie tokenu wyłącza maskowanie wcześniejszego żądania

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java:168
- **Detail**: F8d obejmuje tokeny w komunikatach błędów, a klient deklaruje obsługę współbieżnych wywołań. `get()` zachowuje użyty bearer w lokalnej zmiennej `used` (linia 76), lecz `clean()` maskuje wyłącznie aktualną wartość współdzielonego pola `token` (linie 168–170). Inny wątek może odświeżyć to pole (linia 116), zanim nadejdzie odpowiedź pierwszego żądania. Potwierdzono rzeczywisty scenariusz HTTP na lokalnym serwerze: A wysyła opóźnione żądanie z `token1`; B dostaje 401, wykonuje drugie `auth` i ponawia z `token2`; następnie odpowiedź A zwraca 403 z `token1 rejected`. Rezultat: `AUTHS=2`, komunikat `Wykop GET lagged answered HTTP 403: token1 rejected`, reprodukcja zakończona kodem 0. Maskowanie już zna tylko `token2` i przepuszcza wcześniejszy bearer. To luka nowego sanitizatora, a nie regresja samego mechanizmu odświeżania.
- **Fix**: Przekazywać do maskowania błędów HTTP, parsowania i transportu token użyty przez dane żądanie, aktualizować go przy ponowieniu po 401 i dodać test opóźnionej odpowiedzi podczas równoległego odświeżenia.
- **Decision**: PENDING

## Review scope and execution state

- Branch: `feature/first-assessed-wykop-feed`.
- Baseline: `eb1ab546cccaf1f4f013ac6abc4145f6aeed2ec8` (`origin/main` i merge base).
- Reviewed HEAD: `5c10179ce4efcbb8a0c54d57a4e1f0c1ea1b22ec`.
- Zakres ustalono przez `git log origin/main..HEAD` oraz `git diff origin/main...HEAD`: osiem commitów, 47 zmienionych plików. Plan nie ma daty w nazwie, a historia tej gałęzi jednoznacznie identyfikuje zmianę.
- Uwzględniono refaktoring `0b87984`, główną implementację `378b05b` i poprawkę pustych odpowiedzi wszystkich klientów `5c10179`.
- Kanoniczny Progress: **3/26** ukończonych kroków; faza 1 ma 3/3 `[x]`, bieżąca faza to 2, pozostałe kroki są oczekujące. Przegląd nie zmienia Progress.
- `change.md` przed przeglądem: `status: implementing`, `updated: 2026-10-06`.
- Istniejące zmiany robocze użytkownika w `.agents/`, `AGENTS.md` i `.codex/` wyłączono z zakresu.
- Dwaj niezależni agenci sprawdzili odpowiednio zgodność z planem oraz bezpieczeństwo, jakość i wzorce. Ustalenia zweryfikowano na źródłach; F1 dodatkowo odtworzył główny agent.

## Planned changes versus implementation

Ścieżki Java w tabeli są względne do `szczur-radar-api/`.

| Element | Pliki / stan rzeczywisty | Result |
|---------|--------------------------|--------|
| F7: pochodzenie nieudanej oceny | `agents/assessor/Assessor.java`, `Provenance.java`, `AssessorTest.java`: konstruktor produkcyjny deleguje z properties, publiczne `failed()` wspólne z `assess()`, provider/model zachowane dla obu klas błędów | MATCH |
| Konsumenci F7 | `measurement/GoldRecorder.java`, `LiveSmokeTest.java`, `Replay.java`: zgodna konfiguracja live; replay offline z odrzucającym klientem, pochodzenie w `evaluate()` z nagranej odpowiedzi | MATCH |
| F8a: serializacja enumów | `config/LlmProviderProperties.java`, `ProvenanceTest.java`: `@JsonValue` na wszystkich trzech `id()`, test wszystkich wartości i round-trip Provenance | MATCH |
| F8b: timeout | `LlmProviderProperties.java`, `LlmProviderConfigTest.java`: maksimum 30 minut, błąd zbiorczy i obsługa przepełnienia; testy granicy i przekroczenia | MATCH |
| F8c: pusta odpowiedź Gemini | `llm/GeminiClient.java`, `ChatResponses.java`, `GeminiClientTest.java`: `Whitespace`, `REJECTED` → `PROVIDER_ERROR`, bez retry; zmodyfikowana prawdziwa fixture | MATCH |
| F8d: maskowanie Wykopu | `sources/wykop/WykopApiClient.java`, `WykopApiClientTest.java`: maskowanie komunikatu nadrzędnego działa w prostych przypadkach, ale pomija surowe przyczyny i token wcześniejszego żądania | DRIFT — F1, F2 |
| F8e: cache | `agents/assessor/validation/OutputParser.java`, `llm/ProviderSchemas.java`, `ProviderSchemasTest.java`: inicjalizacja tekstu przez holder klasy; `ConcurrentHashMap.computeIfAbsent` z pełnym kluczem `(provider, schema)`; testy różnych schematów i współbieżności | MATCH |
| F8f: niewidoczne znaki | `follow-ups/f8f-invisible-characters.md`: wynik i zapis akceptacji; niezależny skan potwierdza brak znaków, więc brak zmian kontraktu i normalizacji jest zgodny z warunkiem planu | MATCH |

### Additional and conditionally unchanged files

- Przeniesienie `Whitespace` do wspólnego `api/text` oraz aktualizacje importów w assessorze, Wykopie i measurement są uzasadnionym dodatkiem. Osobny commit zachowuje zachowanie i pozwala klientom LLM korzystać ze wspólnej definicji bez zależności od assessora; odpowiada lekcji o granicach infrastruktury i domeny.
- Kontrola pustych odpowiedzi w `DeepSeekClient` i `OpenRouterClient`, wspólne `ChatResponses.requireText`, ich testy oraz dokumentacja rozszerzają F8c w zgodzie z kontraktem portu LLM. Nie zmieniają promptu, schematu ani metryk.
- README, lokalne CLAUDE.md i `REPOSITORY_GUIDELINES.md` opisują wprowadzony kod. Roadmapa wskazuje S-02 jako `in-progress`; renderer i PNG zmieniono razem, zgodnie z regułą repozytorium.
- Dokumenty przygotowania zmiany i plan-review weszły w pierwszym commicie `a6bca8a`.
- `contract-v2.md` i implementacja normalizacji nie wymagają zmian: warunek F8f nie wystąpił. Pliki faz 2–6 pozostają poza tym przeglądem i nie są zgłaszane jako brakujące.
- Bariery zakresu zachowane: bez zmian promptu N19, ponownego pomiaru gold, nowych źródeł, komentarzy Wykopu ani implementacji pipeline'u, DB, API i SPA z późniejszych faz.

## Success criteria verification

### Automated

| Polecenie / kontrola | Result | Rzeczywiste wyjście / dowód |
|---------------------|--------|----------------------------|
| `cd szczur-radar-api && ./mvnw -B test` | PASS | `Tests run: 449, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`; 12.070 s |
| `cd szczur-radar-api && ./mvnw -B verify` | PASS | Jednostkowe: 449/0/0/0; integracyjne: `Tests run: 57, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`; 38.899 s |
| Skan F8f: tekst plików + wszystkie zdekodowane stringi/klucze JSON | PASS | `fixtures/wykop`: 4 JSON; `fixtures/assessor/gold`: 3 JSON; U+200B/U+00AD/U+FEFF: po 0 w obu sposobach skanowania |
| Inspekcje IntelliJ sześciu głównych zmienionych plików | PASS | Brak błędów; pominięto nieistotne, wcześniej istniejące ostrzeżenia dokumentacyjne/nullability |
| `git diff --check origin/main...HEAD` | PASS | Kod wyjścia 0, brak wyjścia |

Pierwsze uruchomienie `test` w sandboxie nie przeszło z powodu blokady lokalnych socketów (`Operation not permitted`) i dołączania Mockito. Po ponowieniu poza sandboxem wszystkie testy przeszły; nie jest to błąd implementacji. `verify` wykonano poza sandboxem z JDK 25.0.4.1, Dockerem/Testcontainers PostgreSQL 18 i lokalną konfiguracją LLM, zgodnie z README. Profil live `measurement` pozostawał wyłączony.

Logi tego przeglądu: `/private/tmp/szczur-first-assessed-wykop-feed-phase-1-unit.log` i `/private/tmp/szczur-first-assessed-wykop-feed-phase-1-verify.log`. Wyniki powyżej utrwalają ich istotne wyjście; agregaty XML w `target/` mogą obejmować raporty pozostałe z wcześniejszych uruchomień, więc liczby pochodzą z podsumowań bieżących poleceń Maven.

### Manual

- Progress **1.3 `[x]` — 378b05b**: wynik F8f jest zapisany w `follow-ups/f8f-invisible-characters.md`, z adnotacją „zaakceptowane przez użytkownika 2026-10-06”. Niezależnie potwierdzono wynik dla obu zbiorów wymaganych przez plan. Stan ukończenia ma obserwowalne uzasadnienie w dokumentacji i danych.
- Oczekujące kryteria ręczne faz 5–6 pozostają poza zakresem tego przeglądu fazy 1.

## Triage

F1 i F2 oczekują na decyzję. Przegląd nie wprowadził poprawek w kodzie. Wznowienie: `/10x-impl-review context/changes/first-assessed-wykop-feed/reviews/impl-review-phase-1.md`.
