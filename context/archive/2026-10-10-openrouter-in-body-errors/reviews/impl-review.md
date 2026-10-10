<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Błędy OpenRouter w treści odpowiedzi 200 — plan implementacji

- **Plan**: context/changes/openrouter-in-body-errors/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2
- **Date**: 2026-10-10
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 1 observation
- **Triage**: COMPLETE — F1 fixed, 0 pending
- **Git scope**: `77fde0ec45a375819de28711e460372c52a56034..e47f44ef7169b594ee276a825a8243616cfff99e`, branch `feature/openrouter-in-body-errors`
- **Worktree**: `.claude/worktrees/openrouter-in-body-errors`
- **PR**: [#40](https://github.com/kwmacioszek/szczur-radar/pull/40), OPEN; checked head matches reviewed HEAD
- **Progress at review**: 4/4; both phases complete. Optional manual check explicitly waived, not executed.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS — F1 fixed and verified during triage |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Błąd po częściowej odpowiedzi może zostać uznany za sukces

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterRawResponse.java:96; szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterClient.java:175
- **Detail**: Parser odczytuje tylko `root.error`, a klient sprawdza obserwację wyłącznie po wyjątku lub przy pustych wynikach. [Dokumentacja OpenRouter](https://openrouter.ai/docs/api/reference/errors-and-debugging#chat-completions-apiv1chatcompletions) opisuje również błąd po częściowej odpowiedzi w `choices[0].error` z `finish_reason: "error"`. Jeśli wiadomość zawiera niepusty tekst, obecna ścieżka zwraca go jako sukces, bez klasyfikacji kodu i ponowienia. Lokalny probe na minimalnie zmienionym nagraniu odpowiedzi potwierdził: zagnieżdżony kod 502/`provider_unavailable` → `errorCode=null, choices=true` → `SUCCESS, attempts=1, requests=1, sleeps=[]`. Zachowanie istniało już przed tą zmianą; plan obejmuje obiekt `error` na najwyższym poziomie i brak `choices`, dlatego obserwacja nie blokuje zatwierdzenia.
- **Fix**: W osobnej zmianie rozszerzyć odczyt o `choices[0].error`, sprawdzać rozpoznany błąd przed zaakceptowaniem niepustego wyniku i dodać test odpowiedzi z częściowym tekstem oraz błędem 502.
  - Strength: Wykorzystuje istniejącą klasyfikację i ponowienia; obejmuje drugi udokumentowany format błędów bez zmian pipeline.
  - Tradeoff: Poszerza zaakceptowany zakres o błędy po częściowej generacji; ponowienie może zwiększyć koszt wywołania.
  - Confidence: HIGH — format opisuje dokumentacja dostawcy, a lokalny probe odtwarza przyjęcie błędnej odpowiedzi.
  - Blind spot: Nie sprawdzano tego formatu na żywo z kluczem OpenRouter; probe korzysta z nagrania sukcesu zmienionego syntetycznie.
- **Decision**: FIXED — użytkownik wybrał „napraw teraz w tym worktree” 2026-10-10. Rozpoznawanie `choices[0].error` i sprawdzenie błędu przed przyjęciem wyniku wdrożono w bieżącej zmianie; dodano trzy testy regresji. Zobacz `follow-ups/review-fixes.md`.

## Plan adherence and scope evidence

| File | Result | Evidence |
|------|--------|----------|
| `llm/openrouter/OpenRouterRawResponse.java` | MATCH | Final package-private `HttpClient`; buforowanie 2xx i ponowny odczyt identycznych bajtów, zamykanie odpowiedzi, delegacja async/close, `ThreadLocal`, `clear/take`, odczyt code/message/type/choices i fallback nieczytelnego JSON. Dodatkowe `answered` odróżnia odebraną odpowiedź od timeout/I/O i realizuje intencję planu. |
| `llm/openrouter/OpenRouterClient.java` | MATCH | Sync `OpenAIClientImpl` z własnym HTTP clientem, te same URL/key/timeout i `maxRetries(0)`; async bez zmian. Klasyfikacja błędów 200 przed `ofThrowable`, brak/puste choices jako SERVER_ERROR, odmowa/filtr/pusty tekst bez zmian. |
| `llm/openrouter/OpenRouterClientTest.java` | MATCH | 429×3 flex→default, 502×3, 401 z redakcją klucza, 400×1, brak i puste choices z ponowieniem; stare timeouty, statusy HTTP i liczniki żądań przechodzą. |
| `llm/CLAUDE.md` | MATCH | Mapa wymienia raw wrapper; zasada opisuje HTTP-200 error, klasyfikację i dokumentację dostawcy. |
| `context/deployment/deploy-plan.md` | MATCH | Obejście default, brak RUNNING, flex+300s przez skip-deploys, redeploy API, check ok, ponowienie postów i kontrola ocen, zamknięcie issue; 15 postów zachowuje 1/3 prób. |
| `pipeline/PipelineAgentIT.java` | EXTRA — documented and justified | Selekcja posta ocenionego przez model zapewnia wywołanie LLM w teście przerwania; wcześniej możliwy był post odrzucony przez prefilter. Uzasadnienie w `change.md:15`; brak zmiany kodu produkcyjnego pipeline. Test przeszedł w pełnym verify. |
| `context/changes/openrouter-in-body-errors/{change.md,plan.md,plan-brief.md}` | MATCH — workflow artifacts | Tożsamość zmiany, plan, brief, jawna decyzja o pominięciu opcjonalnego testu i stan Progress. |

Ścieżki `llm/...` oraz `pipeline/...` w powyższej tabeli odnoszą się do odpowiednich katalogów main/test modułu `szczur-radar-api`. Diff obejmuje 9 plików. Wszystkie planowane pliki zmieniono. Nie zmieniono innych dostawców, transportowych ponowień/klasyfikacji, asynchronicznej ścieżki, schematu, danych produkcyjnych ani UI. Porównano wzorce z DeepSeekRawResponse/DeepSeekClient oraz wspólnymi transportowymi wyjątkami, komunikatami i testami. Nie stwierdzono istotnego naruszenia granic modułów ani nowych ryzyk sekretów, timeoutów, izolacji wątków lub zamykania odpowiedzi.

## Success criteria verification

| Phase / criterion | Command | Result |
|-------------------|---------|--------|
| 1.1 OpenRouter unit tests | `cd szczur-radar-api && ./mvnw -B test -Dtest=OpenRouterClientTest` | PASS — 19 tests, 0 failures/errors/skipped; BUILD SUCCESS. |
| 1.2 Full backend verification | `cd szczur-radar-api && env LLM_API_PROVIDER=deepseek LLM_MODEL=deepseek-v4-flash LLM_API_KEY=impl-review-test-key ./mvnw -B verify` | PASS — 496 unit tests + 95 integration tests, 0 failures/errors/skipped; BUILD SUCCESS (55.235 s). |
| 2.1 PR CI | `gh pr checks` | PASS — backend verify, web tests, API and web container smoke, Railway config, API deployment all successful. `gh pr view --json ...` confirmed reviewed head `e47f44e`. |
| Hygiene | `git diff --check 77fde0e..HEAD` | PASS — no output. |

Actual verification summary:

```text
OpenRouterClientTest: Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
Unit suite:          Tests run: 496, Failures: 0, Errors: 0, Skipped: 0
Integration suite:   Tests run: 95, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Pełne verify uruchomiono z syntetyczną konfiguracją LLM: testy integracyjne wywołujące LLM używają mocka, a pomiary na żywo są wykluczone. Testcontainers uruchomił PostgreSQL 18. Pierwsze próby były ograniczone środowiskiem: sandbox blokował bind lokalnego serwera testowego; po uruchomieniu poza sandboxem testy OpenRouter przeszły. Pierwsze pełne verify bez LLM_* nie mogło uruchomić kontekstów Spring; powtórka z wymaganymi ustawieniami przeszła. Te błędy nie są usterkami implementacji.

Dowód pełnego lokalnego uruchomienia: `/private/tmp/openrouter-in-body-errors-impl-review-verify-configured.log`. [CI na sprawdzonym HEAD](https://github.com/kwmacioszek/szczur-radar/actions/runs/38054160737) potwierdza również pełne verify.

### Manual verification

- Progress 2.2 jest `[x]` z jawnym dopiskiem o pominięciu opcjonalnego testu na żywo decyzją użytkownika 2026-10-10. To udokumentowane odstąpienie, nie dowód wykonania testu.
- Brak weryfikacji na żywo OpenRouter/flex i produkcji w tym przeglądzie. Operacje po merge pozostają runbookiem w `context/deployment/deploy-plan.md:207`, śledzonym w issue #39.
- Raport i oznaczenie statusu nie zmieniają Progress ani kodu aplikacji.

### F1 reproduction evidence

Probe `/private/tmp/openrouter-review-safety-quality/PartialErrorProbe.java` użył `ScriptedProviderServer`, istniejącego nagrania `fixtures/openrouter/chat-completion.json` i classpath z Surefire. Zmienił pierwszy choice na `finish_reason="error"`, częściowy niepusty tekst i `error={code:502,message:...,metadata:{error_type:"provider_unavailable"}}`. Wywołanie przez `LlmClientFactory.create(...).complete(...)` zwróciło:

```text
observed=Observed[answered=true, errorCode=null, errorMessage=null, errorType=null, choices=true]
result=SUCCESS text=partial output... attempts=1
requests=1 sleeps=[]
```

## Triage completion — 2026-10-10

- **Fixed**: F1 (1).
- **Pending**: none.
- **Reviewed phases**: 1, 2 — pierwotne pokrycie przeglądu zachowane.
- Użytkownik wyraźnie rozszerzył bieżącą zmianę o poprawkę F1 w tym worktree. `OpenRouterRawResponse` odczytuje `choices[0].error`, gdy nie ma obiektu błędu na najwyższym poziomie. `OpenRouterClient` sprawdza obserwację przed zaakceptowaniem niepustego wyniku i zwraca wyłącznie tekst udanej próby.
- Dodano testy na minimalnie zmienionym nagraniu sukcesu: częściowy tekst + 502 → ponowienie i właściwy wynik; trzy takie 502 → PROVIDER_ERROR po trzech żądaniach; częściowy tekst + 401 → LlmConfigurationException po jednym żądaniu, bez ujawnienia klucza.
- `cd szczur-radar-api && ./mvnw -B test -Dtest=OpenRouterClientTest`: **PASS**, 22 testy, 0 failures/errors/skipped.
- `cd szczur-radar-api && env LLM_API_PROVIDER=deepseek LLM_MODEL=deepseek-v4-flash LLM_API_KEY=impl-review-test-key ./mvnw -B verify`: **PASS**, 499 testów jednostkowych + 95 integracyjnych, 0 failures/errors/skipped; BUILD SUCCESS (56.203 s).
- `git diff --check`: **PASS**.
- Log pełnego verify po poprawce: `/private/tmp/openrouter-in-body-errors-triage-verify.log`.
- Uzupełniono zasadę w `llm/CLAUDE.md` i uzasadnienie rozszerzenia zakresu w `change.md`. Progress planu pozostaje bez zmian.
- Poprawkę zweryfikowano lokalnie. Zielone CI opisane wyżej dotyczy wcześniejszego HEAD `e47f44e`; nie obejmuje zmian triage. Testu na żywo nie wykonywano.
