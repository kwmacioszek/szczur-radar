# Bezpiecznik serii odmów dostawcy LLM (S-32) — Plan Brief

> Full plan: `context/changes/provider-refusal-breaker/plan.md`

## What & Why

Seria odmów dostawcy LLM (moderacja, odrzucane żądanie, odpowiedź bez tekstu) ma zatrzymywać krok `assess`, zanim zużyje próby pozostałych postów, i dawać sygnał jak każdy przebieg `failed`. Dziś każda odmowa jest zapisana jako ocena `failed`, zużywa próbę i zeruje oba istniejące bezpieczniki, więc seria jest cicha przez dwie noce, a w trzeciej posty stają się nieodwracalnie `dead` (audyt R-41, destylacja #3, PRD v3 „Wyłącznik serii odmów dostawcy”). Wycinek S-32 roadmapy M-4, issue #142.

## Starting Point

`ConcurrentAssessmentDispatcher` ma dwa bezpieczniki o jednej mechanice: `OUTAGE_STREAK` (S-09, awaria dostawcy po ponowieniach) i `CLIENT_ERROR_STREAK` (S-17, błąd klienta poza klasyfikacją) — stałe `3`, jeden licznik na krok, „z rzędu” w kolejności kończenia wywołań, zapisany wynik z wywołaniem zeruje oba. Odmowa dostawcy to wynik `failed` klasy `provider_error`/`timeout` budowany tylko przez `Assessor.failed` (bez tekstu modelu); zła odpowiedź modelu ma inne klasy. Na zwykłą noc odmów jest 0 (S-28), a naruszeń kontraktu dużo (Reddit: 16,6 % pierwszych prób).

## Desired End State

Trzecia odmowa z rzędu kończy przebieg `failed` z błędem `the LLM provider refused 3 calls in a row; the last: …`: trzy posty serii mają zapisany wynik i zużytą próbę jak dotąd, żaden dalszy post nie jest brany, reszta wraca bez próby. Dzwonek, kod wyjścia crona 1 (przebieg crona) albo `RUN_NEEDS_ATTENTION` (przebieg admina) — bez nowego kodu sygnału. Każda odpowiedź modelu z tekstem zeruje serię; odmowa nadal zeruje serie awarii i klienta. Zwykła noc bez zmian.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Co liczy bezpiecznik | Zapisane wyniki klasy wywołania (`provider_error`/`timeout`, bez tekstu modelu), nie naruszenia kontraktu | Naruszeń kontraktu jest 16,6 % pierwszych prób na Reddicie, odmów na zwykłą noc 0 | Research (PRD v3, S-28) |
| Próg serii | `REFUSAL_STREAK = 3`, stała w kodzie, bez zmiennej | Jedna reguła dla trzech bezpieczników; seria trzy noce uśmierca najwyżej 3 posty na noc | Plan (właściciel) |
| Co zeruje serię | Każda odpowiedź modelu z tekstem (`ok` albo naruszenie kontraktu); prefilter, awaria i błędy naszego kodu nic nie zmieniają | Mierzy, czy dostawca przyjmuje żądania; każdy licznik ma jedno wydarzenie zerujące | Plan (właściciel) |
| Odmowa a istniejące liczniki | Odmowa nadal zeruje serie awarii i klienta | Dostawca odpowiedział — dzisiejsza reguła i test `aRefusedCallEndsTheStreakOfOutages` zostają | Plan |
| Trzy posty serii | Zachowują zużytą próbę (trzecia → `dead`, do przywrócenia przez FR-034) | Wynik jest zapisany przed decyzją bezpiecznika; spójne z bezpiecznikiem klienta i Outcome S-32 | Plan |
| Sygnał | Istniejąca droga `stoppedBy` → `failed` → `AttentionReason.FAILED` | Bez nowego powodu, licznika ani zmiany SPA/crona | Research |
| Dowód integracyjny | Scenariusz w `PipelineRunIT` ze scriptowanym klientem; bez drugiego `LlmOutageIT` | Klasyfikację 4xx pokrywają testy klientów; nowy kontekst Springa kosztuje czas CI | Plan |

## Scope

**In scope:**
- `FailureClass.fromCall()` / `AssessmentResult.callFailed()`; stała, licznik, stop i WARN w dyspozytorze
- Testy jednostkowe dyspozytora na każdą regułę, `AssessorTest`; scenariusz w `PipelineRunIT`
- Doprecyzowanie S-32 w PRD; sekcja S-32 w `deploy-plan.md` (runbook); `pipeline/`, `llm/`, `agents/assessor/` CLAUDE.md; roadmapa, renderer, SVG
- Zwykły przebieg RANGE w PR env bez regresji; komentarze w issue #142

**Out of scope:**
- Zmienna środowiskowa progu; cofanie prób trzem postom serii; nowy `AttentionReason`; zmiany SPA, `rules.json`, mocka, crona; migracja; IaC
- Rzucanie wyjątku z `Assessor` dla odmowy; drugi IT na prawdziwym transporcie; zmiana `CHECK_REQUEST`
- Przywracanie `dead` (S-33) i dzienne podsumowanie martwych postów (S-34)

## Architecture / Approach

Trzeci `AtomicInteger` w gałęzi `stored` dyspozytora: wynik z wywołania zeruje — jak dotąd — serie awarii i klienta; jeśli `callFailed()`, zwiększa serię odmów (trzecia → `runStopped`, `stoppedBy`, jeden WARN), inaczej ją zeruje. Przebieg kończy się `failed` istniejącą drogą (A6 zamyka krok `assess` `failed`, `RunOutcome`, `attentionReasons`, `RunNotifications`). Dokumentacja w szablonie S-09/S-17.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Bezpiecznik w dyspozytorze | Predykat, stała, licznik, stop, testy jednostkowe, CLAUDE.md, roadmapa/SVG | Zerowanie liczników w złej kolejności (odmowa musi dalej zerować awarie i klienta) |
| 2. IT i dokumenty produktu | Scenariusz `PipelineRunIT`, doprecyzowanie PRD, sekcja S-32 w deploy-plan | 4 wątki w IT: liczba zapisanych odmów to przedział `3…6`, nie dokładnie 3 |
| 3. PR env i domknięcie | CI zielone, zwykły przebieg w PR env, komentarze w #142 | Odmowy nie da się wywołać w PR env — dowodem jest IT, PR env pokazuje tylko brak regresji |

**Prerequisites:** `main` świeżo pobrane; Docker dla `./mvnw -B verify`; dostęp `gh` i `railway`; właściciel loguje się w PR env w oknie otwartym przez agenta.
**Estimated effort:** ~1–2 sesje w 3 fazach; jedna gałąź `feature/provider-refusal-breaker`, jeden PR.

## Open Risks & Assumptions

- Trzy posty z treścią naprawdę odrzucaną przez moderację obok siebie zatrzymają zdrową noc; nocny RETRY w dwóch kolejnych nocach weźmie je wśród leftoverów i powtórzy stop (cron exit 1 trzy noce z rzędu, pozostałe leftovery czekają, RANGE za nim idzie dalej), aż staną się `dead` — przyjęte (na zwykłą noc odmów 0; runbook S-32 opisuje tę sekwencję; FR-034 przywraca `dead`).
- Dostawca odmawiający co drugiemu postowi nigdy nie zatrzyma przebiegu — przyjęte (semantyka „z rzędu” z PRD i roadmapy).
- Liczba wątków `PipelineRunIT` (domyślne 4) wyznacza górny kres asercji; zmiana `PIPELINE_LLM_CONCURRENCY` w teście wymaga zmiany kresu.

## Success Criteria (Summary)

- Scenariusz z odmową na każdy post kończy przebieg `failed` po trzech odmowach: zapisanych najwyżej `3 + (wątki − 1)` prób, reszta postów nietknięta, admin powiadomiony.
- Testy dyspozytora pokrywają każdą regułę serii; dzisiejsze testy odmowy zostają zielone bez zmian.
- PRD, `deploy-plan.md` i CLAUDE.md opisują regułę; zwykły przebieg w PR env kończy się jak dotąd.
