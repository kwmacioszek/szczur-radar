# Krok 1.4: przegląd `Kind.REJECTED` i `catch` w `llm/` (dowody)

- Data: 2026-10-21, commit `805c912`, wykonał agent.
- Polecenia: `grep -rn "Kind.REJECTED" szczur-radar-api/src/main` oraz `grep -rn "catch (RuntimeException\|catch (Exception\|catch (Throwable" szczur-radar-api/src/main/java/pl/szczurradar/api/llm`.

## `Kind.REJECTED`: 7 trafień

Ścieżki względem `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/`.

| Miejsce | Kiedy | Klasa |
|---|---|---|
| `transport/TransportException.java:88` | `kindOf(status)`: status HTTP < 500, inny niż 401/402/404/408/429 | status |
| `transport/ChatResponses.java:35` | `requireText`: odpowiedź bez tekstu | brak tekstu |
| `gemini/GeminiClient.java:111` | brak kandydatów (odpowiedź zablokowana) | odmowa |
| `deepseek/DeepSeekClient.java:156` | brak `choices` w odpowiedzi | odmowa |
| `deepseek/DeepSeekClient.java:159` | `content_filter` | odmowa |
| `openrouter/OpenRouterClient.java:215` | `refusal` modelu | odmowa |
| `openrouter/OpenRouterClient.java:218` | `content_filter` | odmowa |

Każde trafienie tworzy wyjątek z konkretnego warunku odpowiedzi lub statusu. Żadne nie powstaje z przechwyconego dowolnego wyjątku.

## `catch` szerokich wyjątków w `llm/`: 4 trafienia

| Miejsce | Co robi z nieznanym wyjątkiem |
|---|---|
| `openrouter/OpenRouterClient.java:196` | `failedAnswer` (błąd w ciele 200), potem `ofTimeoutOrConnection`, inaczej `TransportException.unclassified` |
| `gemini/GeminiClient.java:107` → `classify` (`:123`) | status `ApiException` z łańcucha, potem `ofTimeoutOrConnection`, inaczej `unclassified` |
| `deepseek/DeepSeekClient.java:139` | serwer uciął odpowiedź, potem `ofTimeoutOrConnection`, inaczej `unclassified` |
| `api/LlmCheckController.java:48` | `ok=false` z komunikatem wyjątku (F5 z przeglądu planu) |

`TransportException.unclassified` (`transport/TransportException.java:130`) zwraca `IllegalStateException(messages.clean(...), ex)`: błąd techniczny, nie `REJECTED`, z oczyszczonym komunikatem (F2 z przeglądu planu).

## Testy pokrywające zachowanie

- `anUnknownSdkErrorLeavesTheClientUnclassifiedAfterOneCall` w `GeminiClientTest`, `DeepSeekClientTest` i `OpenRouterClientTest`.
- `TransportRetryTest`.

## Wynik

PASS: w `src/main` nie ma miejsca, które zamienia dowolny `Throwable` na `Kind.REJECTED`.

Uwaga do przeglądu implementacji: `LlmCheckController:48` zwraca `ex.getMessage()` bez `messages.clean`. Wyjątki z klientów są już oczyszczone, ale `RuntimeException` spoza klienta, czyli błąd w naszym kodzie, poszedłby do odpowiedzi trasy admina w surowej postaci.
