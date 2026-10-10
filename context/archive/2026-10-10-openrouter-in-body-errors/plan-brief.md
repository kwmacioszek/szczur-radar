# Błędy OpenRouter w treści odpowiedzi 200 — Plan Brief

> Full plan: `context/changes/openrouter-in-body-errors/plan.md`

## What & Why

OpenRouter zgłasza część błędów, np. odmowę z braku mocy w trybie flex, jako HTTP 200 z obiektem `error` zamiast `choices`. Nasz klient traktuje to jak odrzucenie: nie ma ponowienia ani przejścia flex → default, a post traci próbę. Na produkcji tak skończyło się 15 z 25 wywołań pierwszego przebiegu S-02. Poprawka uczy klienta czytać taki błąd, żeby wrócić na tańszy flex.

## Starting Point

`OpenRouterClient` woła OpenRouter przez Spring AI i openai-java. Przy treści bez `choices` openai-java rzuca „`choices` is not set”, a klient zamienia każdy taki wyjątek na `REJECTED`. Produkcja działa tymczasowo na `LLM_SERVICE_TIER=default`. DeepSeek ma już podobny odczyt surowej odpowiedzi (`DeepSeekRawResponse`).

## Desired End State

Błąd z treści odpowiedzi 200 jest klasyfikowany jak status HTTP `error.code`: 429 to ponowienie i przejście z flex na default, 5xx to ponowienie, 401/402/404 to błąd konfiguracji, czyli stop przebiegu bez liczenia prób. Odpowiedź 200 bez `choices` i bez `error` jest ponawiana jak 5xx. Produkcja wraca na flex, a przebiegi nie zostawiają ocen „`choices` is not set”.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Odczyt treści | Własny `HttpClient` wokół `OkHttpClient` openai-java (`ClientOptions.httpClient`), zapamiętanie per wątek | Spring AI i openai-java gubią treść, builder nie ma interceptora, a wzorzec już jest w `DeepSeekRawResponse` |
| Klasyfikacja | `error.code` jak status HTTP (`TransportException.ofStatus`) | Ponowienia, przejście z flex i błąd konfiguracji działają bez zmian w `TransportRetry` |
| 200 bez `choices` i bez `error` (także `choices: []`) | Ponowienie jak 5xx | Uszkodzona odpowiedź to raczej chwilowy problem dostawcy niż wina posta |
| Zakres | Tylko OpenRouter | Realny błąd produkcyjny; DeepSeek już czyta surową odpowiedź, Gemini bez audytu |
| Produkcja po poprawce | Powrót do flex z `LLM_TIMEOUT=300s`, próby 15 postów bez zmian | Bez ręcznych zmian danych; każdy post ma jeszcze 2 próby |
| Testy | Syntetyczne treści błędów w kształcie z dokumentacji OpenRouter, HTTP 200 | Reguła fixture'ów dopuszcza syntetyczne błędy, a takiej odpowiedzi nie da się nagrać na żądanie |

## Scope

**In scope:**
- `OpenRouterRawResponse` (nowy) i klasyfikacja w `OpenRouterClient.call`
- testy w `OpenRouterClientTest`, w tym zmiana oczekiwania przy `choices: []`
- `llm/CLAUDE.md`
- runbook powrotu do flex w `deploy-plan.md`

**Out of scope:**
- inni dostawcy
- zwrot prób postom na produkcji
- odejście od Spring AI
- klient asynchroniczny
- zmiany w pipeline i `TransportRetry`
- UI

## Architecture / Approach

Synchroniczny klient openai-java powstaje z `ClientOptions` z naszym `HttpClient`. Ten zapamiętuje treść każdej odpowiedzi 2xx dla bieżącego wątku i oddaje ją dalej. Po wyjątku albo pustych wynikach `OpenRouterClient.call` sprawdza tę treść: `error` → `ofStatus(code)`; brak `choices` → `SERVER_ERROR`; inaczej jak dziś. `TransportRetry` robi resztę: ponawia i przechodzi z flex na default.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Odczyt błędów z treści | Klasyfikacja błędów w treści odpowiedzi 200 i odpowiedzi bez `choices`, testy z liczbą żądań | Nowy sposób budowy klienta może zmienić timeouty; pilnują ich istniejące testy |
| 2. Wdrożenie i powrót do flex | Runbook, zielone CI, opcjonalny test na żywo | Prawdziwy błąd w treści może mieć inny kształt niż w dokumentacji |

**Prerequisites:** PR #38 zmergowany (gałąź wychodzi z `origin/main`); do testu na żywo ważny klucz OpenRouter.
**Estimated effort:** ~1 sesja, 2 fazy.

## Open Risks & Assumptions

- Kształt błędu w treści przyjmujemy z dokumentacji OpenRouter, bez prawdziwego nagrania. Jeśli produkcja pokaże inny kształt, zadziała ścieżka „bez `choices`” (ponowienie), a log wskaże treść.
- Ponowienia przy trwałej awarii flex wydłużają przebieg i kosztują do 2 dodatkowych wywołań. Po trzech 429 wywołanie i tak przechodzi na default.

## Success Criteria (Summary)

- W kolejnych przebiegach na flex nie ma ocen „`choices` is not set”, a odmowy flex kończą się ponowieniem albo przejściem na default.
- Żaden post nie traci próby przez błąd dostawcy zgłoszony w treści odpowiedzi.
