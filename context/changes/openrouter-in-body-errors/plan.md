# Błędy OpenRouter w treści odpowiedzi 200 — plan implementacji

## Overview

OpenRouter zgłasza część błędów (np. odmowę z braku mocy w trybie flex) jako HTTP 200 z treścią `{"error":{"code","message","metadata":{"error_type"}}}` zamiast `choices`. Nasz `OpenRouterClient` tego nie rozpoznaje: openai-java rzuca „`choices` is not set”, a klient traktuje to jak odrzucenie. Nie ma wtedy ponowienia ani przejścia flex → default, a post traci próbę. Plan uczy klienta czytać taki błąd i klasyfikować go jak status HTTP, a potem przywraca na produkcji tańszy tryb flex.

## Current State Analysis

- **Objaw na produkcji (2026-10-09, pierwszy prawdziwy ruch S-02 przez OpenRouter z `LLM_SERVICE_TIER=flex`):** 15 z 25 wywołań modelu skończyło się `provider_error` z komunikatem `com.openai.errors.OpenAIInvalidDataException: \`choices\` is not set` po ok. 6 s, przy 8 poprawnych ocenach (~33 s). Każde z tych 15 wywołań zużyło 1 z 3 prób posta. CI i PR env używają DeepSeek, więc ścieżka OpenRouter + flex nie była wcześniej ćwiczona.
- **Przyczyna (dokumentacja OpenRouter, „Errors and Debugging”):** status HTTP równa się `error.code` tylko dla błędów żądania (złe parametry, brak środków). Błąd, który wystąpi, gdy model już przetwarza żądanie, przychodzi jako **HTTP 200** z obiektem `error` w treści. Kod błędu dostawcy jest w `error.code`, a typ w `error.metadata.error_type`.
- **Ścieżka w kodzie:** `OpenRouterClient.call` (`szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterClient.java:143-169`) łapie `OpenAIServiceException` (status HTTP ≠ 2xx), a każdy inny `RuntimeException` zamienia przez `TransportException.ofThrowable` na `Kind.REJECTED` (bez ponowienia). Pusta lista `choices` też jest `REJECTED` (`:156-158`). `REJECTED` w `TransportRetry` daje `LlmCallException(PROVIDER_ERROR)`, czyli zapisaną nieudaną próbę.
- **Brak dostępu do treści:** Spring AI `OpenAiChatModel` woła openai-java i przy parsowaniu dostaje wyjątek, a sama treść odpowiedzi przepada. Builder `OpenAIOkHttpClient` nie ma interceptora. Jest za to seam: `ClientOptions.builder().httpClient(com.openai.core.http.HttpClient)` + `new OpenAIClientImpl(ClientOptions)`, a openai-java udostępnia publiczny `com.openai.client.okhttp.OkHttpClient` (openai-java 4.49.0).
- **Wzorzec w repo:** `DeepSeekRawResponse` (`llm/deepseek/DeepSeekRawResponse.java`) czyta surową odpowiedź 2xx DeepSeek, której nie widzi Spring AI. Robi to przez interceptor `RestClient` i zapamiętuje wynik **per wątek**, bo wywołanie i jego próby biegną na wątku wołającego.
- **Klasyfikacja statusów:** `TransportException.kindOf` (`llm/transport/TransportException.java`): 429 → `RATE_LIMITED`, 408 → `TIMEOUT`, 401/402/404 → `CONFIGURATION` (od przeglądu S-02), 5xx → `SERVER_ERROR`, reszta → `REJECTED`. `TransportRetry` ponawia `RATE_LIMITED`, `SERVER_ERROR`, `TIMEOUT` i `CONNECTION`, a po trzech 429 w trybie flex przechodzi na default.
- **Testy:** `OpenRouterClientTest` na `ScriptedProviderServer` liczy żądania. Prawdziwa odpowiedź sukcesu to `fixtures/openrouter/chat-completion.json`. Ciała błędów HTTP to krótki syntetyczny JSON w kształcie błędu dostawcy (reguła z `llm/CLAUDE.md`). Test `answerWithoutChoicesIsAProviderErrorWithoutRetry` oczekuje dziś odrzucenia bez ponowienia przy `choices: []`.

## Desired End State

- Odpowiedź 200 z obiektem `error` jest klasyfikowana jak status HTTP `error.code`:
  - 429 jest ponawiane, a po trzech odmowach flex wywołanie przechodzi na default;
  - 5xx jest ponawiane (do 3 prób);
  - 401/402/404 kończy się `LlmConfigurationException`, czyli zatrzymaniem przebiegu bez liczenia prób;
  - inne kody (np. 400, 403) kończą się odrzuceniem, jak dziś.
- Odpowiedź 200 **bez `choices` i bez `error`** (brak pola, pusta lista albo nierozpoznawalna treść) jest ponawiana jak błąd serwera (do 3 prób).
- Komunikat błędu zawiera status i treść dostawcy (`error_type`, jeśli jest), nigdy klucza.
- Produkcja po merge'u wraca na `LLM_SERVICE_TIER=flex` z `LLM_TIMEOUT=300s`. Kolejny przebieg nie zostawia ocen „`choices` is not set”.

Weryfikacja: `./mvnw -B verify`, CI na PR, opcjonalny test na żywo z ważnym kluczem OpenRouter, potem runbook na produkcji.

### Key Discoveries:

- `llm/openrouter/OpenRouterClient.java:82-87` buduje `OpenAIOkHttpClient` z `maxRetries(0)` i `timeout(LLM_TIMEOUT)`; to zachowanie musi zostać po zmianie budowania klienta.
- `llm/openrouter/OpenRouterClient.java:153-155`: `catch (RuntimeException)` → `ofThrowable` → `REJECTED` to miejsce, w którym dziś ginie błąd z treści.
- `llm/deepseek/DeepSeekRawResponse.java`: wzorzec odczytu surowej odpowiedzi per wątek (`take()`, `clear()`).
- `com.openai.core.http.HttpClient` (`execute(HttpRequest, RequestOptions)`, `executeAsync`, `close`) i `HttpResponse` (`statusCode`, `headers`, `body`, `close`) to interfejsy, które można owinąć.

## What We're NOT Doing

- Zmian u innych dostawców (decyzja: tylko OpenRouter). DeepSeek już czyta surową odpowiedź, Gemini zostaje bez audytu.
- Zwrotu prób 15 postom z produkcji ani usuwania ich ocen „`choices` is not set” (decyzja: próby bez zmian; każdy ma jeszcze 2).
- Odejścia od Spring AI `OpenAiChatModel` ani wywołań openai-java przez `withRawResponse()`.
- Zmian w kliencie asynchronicznym (`OpenAIOkHttpClientAsync`): Spring AI używa go tylko do streamingu, którego nie używamy.
- Zmian w `TransportRetry`, `kindOf` ani w pipeline: nowa klasyfikacja korzysta z istniejących rodzajów.
- Pracy nad UI (osobna zmiana).

## Implementation Approach

Owinąć `com.openai.client.okhttp.OkHttpClient` własnym `HttpClient`, który przy odpowiedzi 2xx czyta treść (odpowiedzi czatu mają kilka KB), zapamiętuje ją dla bieżącego wątku i oddaje dalej jako nowy strumień. Klient OpenRouter powstaje z `ClientOptions` z tym `HttpClient` (te same `baseUrl`, klucz, `timeout`, `maxRetries(0)`). Gdy po wywołaniu Spring AI poleci wyjątek albo wróci odpowiedź bez wyników, `OpenRouterClient.call` sprawdza zapamiętaną treść:
- jest `error` → `TransportException.ofStatus(error.code, …)`;
- brak `choices` i brak `error` → `SERVER_ERROR`;
- pozostałe przypadki jak dziś.

## Critical Implementation Details

- **Wątki:** jeden `OpenRouterClient` obsługuje równolegle do `PIPELINE_LLM_CONCURRENCY` wywołań. Zapamiętana treść musi być per wątek i czyszczona przed każdą próbą, tak jak w `DeepSeekRawResponse`. Inaczej wątek może odczytać błąd innego wywołania.
- **Kolejność w `call`:** czytać zapamiętaną treść przed `ofThrowable`. Przekroczenie czasu i błędy I/O zostają jak dziś (`TIMEOUT`/`CONNECTION`), bo wtedy nie ma treści 2xx.

## Phase 1: Odczyt błędów OpenRouter z treści odpowiedzi

### Overview

Klient rozpoznaje błąd w treści odpowiedzi 200 i odpowiedź bez `choices`, a testy pokazują liczbę żądań dla każdego przypadku.

### Changes Required:

#### 1. Surowa odpowiedź OpenRouter

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterRawResponse.java` (nowy)

**Intent**: Zapamiętać surową treść odpowiedzi 2xx OpenRouter per wątek, żeby klient mógł odczytać błąd, którego openai-java i Spring AI nie przekazują.

**Contract**: `final class OpenRouterRawResponse implements com.openai.core.http.HttpClient`, opakowuje `com.openai.client.okhttp.OkHttpClient`. `execute` przy 2xx buforuje treść i zwraca `HttpResponse` z tą samą treścią do ponownego odczytu. `executeAsync` i `close` delegują. Do tego `clear()` i `take()` → `Observed(@Nullable Integer errorCode, @Nullable String errorMessage, @Nullable String errorType, boolean choices)`, gdzie `choices` mówi, czy jest niepusta lista `choices`. Nieczytelny JSON daje `Observed` bez błędu i bez `choices`. Komunikat przechodzi przez `ProviderMessages.clean` po stronie klienta.

#### 2. Budowa klienta i klasyfikacja

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterClient.java`

**Intent**: Zbudować synchroniczny klient openai-java na `OpenRouterRawResponse` i w `call` zamienić błąd z treści na właściwy `TransportException`.

**Contract**:
- `create(...)`: synchroniczny `OpenAIClient` = `new OpenAIClientImpl(ClientOptions.builder().httpClient(raw).baseUrl(url).apiKey(key).timeout(LLM_TIMEOUT).maxRetries(0).build())`, gdzie `raw` opakowuje `OkHttpClient.builder().timeout(LLM_TIMEOUT).build()`. Klient asynchroniczny bez zmian.
- `call(prompt)`:
  - `raw.clear()` przed wywołaniem;
  - `OpenAIServiceException` jak dziś;
  - dla pozostałych `RuntimeException` i dla pustych wyników najpierw `raw.take()`:
    - `errorCode != null` → `TransportException.ofStatus(errorCode, cleaned("…" + errorType), null, ex)`;
    - inaczej, gdy odpowiedź 2xx była, ale bez `choices` → `TransportException(SERVER_ERROR, "answer without choices", …)`;
    - inaczej → `ofThrowable(ex, messages)` jak dziś.
  - Odmowa modelu, filtr treści i pusty tekst bez zmian.
- Javadoc klasy opisuje błędy z treści.

#### 3. Testy klienta

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/llm/openrouter/OpenRouterClientTest.java`

**Intent**: Udowodnić klasyfikację i liczbę żądań dla błędów w treści odpowiedzi 200.

**Contract**: Treści błędów to krótki syntetyczny JSON w kształcie z dokumentacji OpenRouter (`{"error":{"code":…,"message":…,"metadata":{"error_type":…}}}`) z HTTP 200. Nowe przypadki:
- trzy błędy 429 w treści w trybie flex, potem sukces → przejście na default, 4 żądania, `provider.only` jak w `threeFlexRefusalsFallBackToTheDefaultTier`;
- 502 w treści ×3 → `PROVIDER_ERROR` po 3 żądaniach;
- 401 w treści → `LlmConfigurationException` po 1 żądaniu, bez klucza w komunikacie;
- 400 w treści → odrzucenie po 1 żądaniu;
- odpowiedź 200 bez pola `choices` i bez `error`, potem sukces → 2 żądania, sukces.

Istniejący `answerWithoutChoicesIsAProviderErrorWithoutRetry` (`choices: []`) zmienia oczekiwanie na ponowienie (decyzja: 200 bez `choices` = jak 5xx) i nazwę. Wszystkie dotychczasowe testy, z timeoutami i liczbą żądań, przechodzą bez zmian.

#### 4. Dokumentacja pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md`

**Intent**: Zapisać, że OpenRouter zgłasza część błędów w treści odpowiedzi 200 i jak klient je klasyfikuje.

**Contract**: Nowy punkt w sekcji zasad obok DeepSeek (`DeepSeekRawResponse`), z odwołaniem do dokumentacji OpenRouter. Mapa pakietu wymienia `OpenRouterRawResponse`.

### Success Criteria:

#### Automated Verification:

- Testy klienta OpenRouter przechodzą, w tym nowe przypadki błędów w treści: `cd szczur-radar-api && ./mvnw -B test -Dtest=OpenRouterClientTest`
- Pełna weryfikacja backendu przechodzi: `cd szczur-radar-api && ./mvnw -B verify`

**Implementation Note**: Na początku tej fazy `/10x-implement` tworzy gałąź `feature/openrouter-in-body-errors` ze świeżego `origin/main` (po merge'u PR #38), nowe issue `openrouter-in-body-errors: …` i PR, zgodnie z `REPOSITORY_GUIDELINES.md`.

---

## Phase 2: Wdrożenie i powrót do flex

### Overview

Runbook przywrócenia flex na produkcji po merge'u, weryfikacja przed merge'em.

### Changes Required:

#### 1. Runbook

**File**: `context/deployment/deploy-plan.md` (sekcja `first-assessed-wykop-feed`, podsekcja o pierwszym deployu produkcji)

**Intent**: Zapisać obejście na czas poprawki (`LLM_SERVICE_TIER=default`) i kroki po merge'u tej zmiany.

**Contract**: Kroki operatora po merge'u:
1. nie w trakcie przebiegu `RUNNING`;
2. `LLM_SERVICE_TIER=flex` i `LLM_TIMEOUT=300s` na produkcji (`--skip-deploys`), redeploy API;
3. `/api/admin/llm/check` → `"ok":true`;
4. ponowienie nieudanych postów z `/admin`, a w ocenach nie ma nowego „`choices` is not set”;
5. komentarz z wynikiem i zamknięcie issue tej zmiany.

Do tego notka: 15 postów zostaje z 1/3 prób (decyzja).

### Success Criteria:

#### Automated Verification:

- CI zielone na PR: `gh pr checks`

#### Manual Verification:

- Opcjonalny test na żywo przed merge'em (klucz OpenRouter w powłoce, `measurement`, tryb flex): wywołania kończą się sukcesem albo sklasyfikowanym ponowieniem, bez „`choices` is not set”

---

## Testing Strategy

### Unit Tests:

- `OpenRouterClientTest`: błędy 429, 502, 401 i 400 w treści odpowiedzi 200; odpowiedź 200 bez `choices`; pusta lista `choices`; klucz nie trafia do komunikatów; dotychczasowe przypadki bez zmian.

### Integration Tests:

- Bez nowych: pipeline korzysta z istniejących rodzajów błędów (`PipelineAgentIT`, `ConcurrentAssessmentDispatcherTest` pokrywają konfigurację i błąd techniczny).

### Manual Testing Steps:

1. Opcjonalnie lokalnie: `./mvnw -Pmeasurement test -Dtest=LiveSmokeTest` z `LLM_API_PROVIDER=openrouter`, `LLM_MODEL=openai/gpt-6-luna`, `LLM_SERVICE_TIER=flex`, `LLM_TIMEOUT=300s` i ważnym kluczem (README pomiarów).
2. Po merge'u: runbook z fazy 2.

## Performance Considerations

Buforowanie treści odpowiedzi 2xx dotyczy kilku KB na wywołanie. Ponowienia 429 i 5xx mogą wydłużyć przebieg, ale to zamierzone, zamiast zjadania prób postów.

## Migration Notes

Brak zmian schematu. Produkcja do merge'a działa na `LLM_SERVICE_TIER=default` (obejście), a po merge'u wraca na flex według runbooka.

## References

- Diagnoza: rozmowa z 2026-10-09/10, oceny produkcyjne (`assessments.violations`: „`choices` is not set”, 15 przypadków)
- OpenRouter, Errors and Debugging: https://openrouter.ai/docs/api/reference/errors-and-debugging
- Wzorzec: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/deepseek/DeepSeekRawResponse.java`
- Klient: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterClient.java:79-169`
- Klasyfikacja: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/transport/TransportException.java` (`kindOf`)
- Runbook S-02: `context/deployment/deploy-plan.md`, sekcja `first-assessed-wykop-feed`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Odczyt błędów OpenRouter z treści odpowiedzi

#### Automated

- [ ] 1.1 Testy klienta OpenRouter przechodzą, w tym nowe przypadki błędów w treści
- [ ] 1.2 Pełna weryfikacja backendu przechodzi

### Phase 2: Wdrożenie i powrót do flex

#### Automated

- [ ] 2.1 CI zielone na PR

#### Manual

- [ ] 2.2 Opcjonalny test na żywo przed merge'em: bez „choices is not set”
