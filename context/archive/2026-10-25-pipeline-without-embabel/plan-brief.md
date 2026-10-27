# S-17: przebieg pipeline'u bez Embabel — Plan Brief

> Full plan: `context/changes/pipeline-without-embabel/plan.md`
> Research: `context/changes/pipeline-without-embabel/research.md`

## What & Why

Przebieg pipeline'u (fetch → store → select → assess → summarize) przestaje być agentem Embabel i staje się zwykłą sekwencją kroków na wątku kolejki `pipeline-run`. Embabel wychodzi z całego API, bo po przebiegu nie ma w kodzie produkcyjnym żadnego konsumenta, a kosztuje przy każdym starcie (skan beanów, eager klienci SDK, cudza konfiguracja logów). Celem jest przerwanie docierające do kroku i oceny bez akcji „zombie”, szybsze budzenie API ze snu i testy silnika, które są skupione i niezależne od kolejności (roadmapa S-17, MS-01, MS-03; issue #76).

## Starting Point

Embabel w przebiegu to 3 pliki i 7 symboli; logika kroków i stan trwały są już poza frameworkiem. Jedyną realną funkcją Embabel jest wykonanie akcji na własnym wątku, przez co `stopStalled` może tylko anulować czekanie (`cancel(false)`), a zatrzymana akcja biegnie dalej jako zombie chroniona wyłącznie przez warunkowe `UPDATE`. Poza przebiegiem Embabel to `AssessorAgent` bez wywołań, rejestr modeli używany tylko przez jego IT, `logback-embabel.xml` i kilkadziesiąt artefaktów. `PipelineAgentIT` ma 871 linii i 15 metod `@Order` na jednej bazie; `RunRecorder` nie ma testu na bazie.

## Desired End State

Watchdog i zamknięcie instancji przerywają wątek przebiegu, a krok kończy się w ciągu sekund (w `await`, w kliencie Wykopu, między paczkami `store`); w logu INFO, nie ERROR. Serwer, `migrate` i testy startują bez żadnej klasy Embabel, z `LLM_*` nadal wymaganymi i logami w domyślnym formacie Boota. Krok `assess` zatrzymany przez bezpiecznik jest `failed` (A6), watchdog nie pada na błędzie alertu (F5), a trzy niesklasyfikowane błędy klienta LLM z rzędu zatrzymują przebieg (F7). Testy silnika działają w losowej kolejności i pojedynczo. PR env potwierdza przebieg, przerwanie przez deploy i zmierzony czas startu.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Zakres | Embabel z całego API (pom, properties, `AssessorAgent`, decyzja w `tech-stack-api.md`) | Po przebiegu nie ma konsumenta w main, a platforma kosztuje każdy start; wariant „tylko przebieg” zostawiałby framework bez treści. | Plan (research C–D) |
| Fail-fast `LLM_*` w komendach | `LlmConfigurationCheck` bez zmian | Decyzja S-10 jest niezależna od Embabel: zły `LLM_*` ma zatrzymać pre-deploy `migrate`, nie nocny przebieg. | Plan (research C) |
| Pozycje Parked | A6 + F5 + F7 w zakresie | Wszystkie trzy czekają na „najbliższą pracę w tym kodzie”, każda to kilkadziesiąt linii w przepisywanych plikach. | Plan |
| Testy | Pełna redystrybucja: `RunSequenceTest` bez Springa, `RunRecorderIT` na bazie, `PipelineRunIT`/`RunWatchdogIT` z `MethodOrderer.Random` i resetem bazy | Spełnia MS-03 dosłownie; błąd lokalizuje się w jednej warstwie. | Plan |
| Format logów | Domyślny Boota, bez własnego logback | Nic nie czyta logów API maszynowo; własny plik kopiowałby decyzję usuwanego frameworka. | Plan |
| Model wątków | Kroki na wątku `pipeline-run`; `FutureTask` rejestrowany przed `execute`; `stopStalled` → `cancel(true)` + `dispatcher.cancel` | Przerwanie już dziś sięga `await`, klienta Wykopu i `TransportRetry.sleep`; `dispatcher.cancel` zostaje dla wątku stojącego w `llm.check()` na OkHttp. | Research A–B |
| Klasyfikacja końca | INFO/ERROR po `recorder.isRunning`, nie po typie wyjątku | Po przerwaniu wyjątki wyglądają różnie; stan w bazie mówi, kto zakończył przebieg. | Plan |
| Strażniki warunkowe | Wszystkie 18 zostają | Żaden nie istnieje tylko dla zombie; chronią nakładkę deployu i odzyskiwanie. | Research E |

## Scope

**In scope:**
- `flow/RunSequence` zamiast `PipelineAgent`/GOAP; `PipelineRunService` bez `AgentPlatform`, `waiting`, `unwrap`; `stopStalled` z `cancel(true)`; checkpointy przerwania w `PostStore`/`SourceIngest` (A7)
- A6 (`RunRecorder.step` z `StepOutcome`), F5 (`RunWatchdog`), F7 (`LlmClientException`, seria 3 w dyspozytorze, doprecyzowanie w PRD)
- pom bez 4 starterów i BOM Embabel, z `spring-ai-{openai,deepseek,google-genai}` i `openai-java-client-okhttp` wprost; usunięte `application-<provider>.properties`, `AssessorAgent`, `AssessorAgentIT`
- `RunSequenceTest`, `PipelineRunServiceTest`, `RunRecorderIT`, `PostRepositoryIT` (+3 metody), `PipelineRunIT`, `RunWatchdogIT` bez `@Order`
- Dokumentacja (CLAUDE.md ×3, README API, guidelines, tech-stack, infrastructure, deploy-plan S-17, komentarze ci/railway) i weryfikacja w PR env

**Out of scope:**
- Reddit, porządki granic pakietów (cykl `pipeline ↔ notifications`, dwa zapisy `posts`), `FOR SHARE` w `claim`, A8, B10
- Zmiana `LlmConfigurationCheck`, własny logback, zmiana `PIPELINE_STALL_TIMEOUT`, dyspozytor Batch API, kontrakty `/internal` i SPA
- Testy jednostkowe `D-2…D-1`/`RunState.of`, odświeżenie `context/map`, regeneracja SVG roadmapy przed `done`

## Architecture / Approach

`PipelineRunService.submit` tworzy `FutureTask` przebiegu, rejestruje go w mapie `running` i oddaje jednowątkowemu executorowi `pipeline-run`; `execute` woła `RunSequence.run(RunRequest)`, która wykonuje kroki po kolei przez `RunRecorder.step` (RANGE: fetch → store → select → assess → summarize; RETRY/POSTS: select → assess → summarize). Watchdog po zapisie `stall` + `releaseAll` woła `task.cancel(true)` (przerwanie dociera do kroku) i `dispatcher.cancel(runId)` (zadania puli LLM). `execute` po wyjątku pyta bazę, czy przebieg nadal `running`: jeśli nie, loguje INFO i nic nie zamyka; jeśli tak, ERROR i `failed` z `<SimpleName>: <message>`. Embabel znika z classpath; klienci `llm/` budują `ChatModel` Spring AI 2.0.1 jak dotąd, deklarowany wprost.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Silnik bez Embabel | `RunSequence`, `PipelineRunService` na własnym wątku, A6, A7, F5, `RunSequenceTest`, `pipeline/CLAUDE.md` | Klasyfikacja INFO/ERROR i flaga przerwania (Hikari) — testowane jednostkowo, dowodzone przez `PipelineAgentIT`/`RunWatchdogIT` |
| 2. Embabel poza projektem | pom, properties, `AssessorAgent`, `LlmProviderConfigTest`, zero `com.embabel` w drzewie | `openai-java-client-okhttp` i `spring-ai-bom` wymagają sieci i zgodnych wersji; start każdego kontekstu bez platformy |
| 3. F7 | `LlmClientException`, seria 3 w dyspozytorze, PRD | Zmiana semantyki prób — ograniczona do błędów klienta, z resetem jak `OUTAGE_STREAK` |
| 4. Testy | `RunRecorderIT`, `PostRepositoryIT`, `PipelineRunIT` i `RunWatchdogIT` losowe i z resetem | Zgubiony scenariusz przy przenoszeniu — mapa 15 scenariuszy w planie |
| 5. Dokumentacja i PR env | Dokumenty bez Embabel, sekcja S-17 w deploy-plan, przerwanie przez deploy, pomiar startu, format logów | Weryfikacja przerwania wymaga przebiegu kilku dni i pusha w trakcie `assess` |

**Prerequisites:** S-09 `done` (jest); gałąź `feature/pipeline-without-embabel` i issue #76 (są); Docker lokalnie do `verify`; dostęp do PR env i logów Railway; raz sieć do Mavena po `spring-ai-bom`.
**Estimated effort:** 5 faz, ok. 5–7 sesji; fazy 1 i 4 największe (silnik + ok. 1,7 tys. linii testów do przepisania).

## Open Risks & Assumptions

- Przerwanie wywołania na OkHttp (Gemini, OpenRouter) nadal kończy się dopiero po `LLM_TIMEOUT`; plan tego nie zmienia, więc zatrzymany przebieg na produkcji (OpenRouter) kończy krok najpóźniej po jednej próbie.
- `spring-ai-bom` 2.0.1 nie ma w `~/.m2`; jeśli nie rozwiąże się, moduły dostają wersję wprost (ta sama 2.0.1).
- Skrócenie startu nie jest zmierzone przed zmianą; plan mierzy je przed i po i zapisuje wynik, nie obiecuje liczby.
- Generator SVG roadmapy nie zna statusów `planning`/`in-progress`; SVG zmienia się przy `done` (archiwizacja).
- Testy całego przebiegu z resetem bazy przed każdą metodą będą wolniejsze niż dziś (każdy scenariusz z ocenionymi postami wykonuje własny przebieg RANGE na mocku LLM).

## Success Criteria (Summary)

- Zatrzymany lub przerwany przebieg kończy swój krok w ciągu sekund i bez linii ERROR, a stan w bazie jest taki jak dziś (`failed`/`interrupted`, posty z powrotem, powiadomienie).
- `./mvnw -q dependency:tree | grep -ci embabel` = 0, serwer i `migrate` startują, `LLM_*` nadal wymagane; `./mvnw -B verify` zielony dwa razy z rzędu przy losowej kolejności metod.
- Na PR env: przebieg RANGE, przerwanie przez deploy (`interrupted`/`deploy`), zmierzony czas startu przed i po, logi w formacie Boota — zapisane w `deploy-plan.md`, sekcja S-17.
