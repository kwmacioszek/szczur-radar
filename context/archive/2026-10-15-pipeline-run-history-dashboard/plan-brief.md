# Historia przebiegów, postęp i powiadomienia admina (S-06) — Plan Brief

> Full plan: `context/changes/pipeline-run-history-dashboard/plan.md`
> Research: `context/changes/pipeline-run-history-dashboard/research.md`

## What & Why

Admin ma widzieć historię przebiegów i dostawać jednoznaczny sygnał, gdy przebieg wymaga uwagi (FR-021, FR-015, NFR). Dziś trwającego przebiegu nie da się obserwować bez czytania bazy: liczniki zapisują się dopiero po końcu oceny, a API nic nie loguje. Zawieszony przebieg blokuje kolejkę do restartu API (z 2026-10-09 „trwał” ok. 22 h). Przerwanie przez deploy wygląda jak awaria.

## Starting Point

Lista przebiegów w `/admin` i rozdział ról istnieją od S-02. Cron S-05 startuje noc i czeka na wynik. Obie noce na produkcji (14–15.10) skończyły się `partial` z ok. 2% postów `failed` do ponowienia, bez `dead` i bez błędów technicznych. Najdłuższa przerwa między ocenami to 42 s.

## Desired End State

- **Dzwoneczek w nagłówku** (tylko admin) pokazuje liczbę nieprzeczytanych powiadomień o przebiegach wymagających uwagi. Kliknięcie otwiera stronę przebiegu `/admin/runs/:id` z postępem na żywo, przyczyną i krokami.
- **`/admin`** pokazuje zakres, czas trwania, kto uruchomił i znacznik uwagi, a trwający przebieg sam się odświeża.
- **Strażnik** zatrzymuje zawieszony przebieg po progu (domyślnie 15 min, nie krócej niż najdłuższe wywołanie LLM + 5 min; produkcja ok. 39 min), a kolejny rusza.
- **Log API i cron** pokazują postęp przebiegu.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Co „wymaga uwagi” | `failed`, `interrupted` albo `partial` z nowym `dead` lub błędem technicznym; zwykłe `failed` do ponowienia nie | Obie noce to `partial` z ~2% `failed`, więc sygnał przy każdym `partial` byłby co noc; `dead` to 1 na ~1150 postów. | Plan (użytkownik, dane z produkcji) |
| Kanał sygnału | Tylko w aplikacji: dzwoneczek z okienkiem i przejściem do szczegółów; bez pusha, cron bez zmian | Wybór użytkownika: prosty system powiadomień widoczny na stronie. | Plan (użytkownik) |
| „Przeczytane” | Osobna tabela `notifications`, wiersz na admina, przeczytanie per powiadomienie | Wybór użytkownika; tabela jest gotowa na przyszłe rodzaje. | Plan (użytkownik) |
| Szczegóły | Strona `/admin/runs/:id` | Stały adres także dla przebiegu spoza 20 ostatnich. | Plan (użytkownik) |
| Zawieszenie | Jeden próg (`PIPELINE_STALL_TIMEOUT`): domyślnie `max(15m, najdłuższe wywołanie LLM + 5m)`, na produkcji `39m`; po nim API kończy przebieg jako `failed` i zwalnia kolejkę | W nocy nikt nie zareaguje na samo ostrzeżenie; zatrzymanie i tak daje powiadomienie; próg nie może być krótszy niż jedno wywołanie LLM, bo `llm.check()` i ogon przebiegu mają w toku jedno wywołanie. | Plan (użytkownik), przegląd planu F2 |
| Postęp | Liczniki i `last_activity_at` w `pipeline_runs` po każdym poście (warunkowy `UPDATE`) | Wzorzec `RunRecorder`; lista i cron widzą postęp bez kosztownych zapytań. | Research / Plan |
| Przyczyna przerwania | Enum `deploy`/`restart`/`shutdown` z `RAILWAY_DEPLOYMENT_ID` zapisanym przy przebiegu; otwarte kroki → `interrupted` | Dziś deploy i awaria mają ten sam tekst, a krok zostaje „w toku” na zawsze. | Research / Plan |
| Ponowienia i flex | Liczone po zakończeniu wywołania z `Provenance`, `max(attempts − 1, 0)` | Prefiltr ma `attempts = 0`; warstwa `llm/` bez zmian. | Research (dane z produkcji) |
| Heartbeat crona | Linia co `TRIGGER_HEARTBEAT_INTERVAL` (5 min) z body istniejącego GET-a | Bez dodatkowych żądań i bez zmiany kodów wyjścia. | Plan |

## Scope

**In scope:**
- migracje `V8` (postęp, przyczyna, status kroku) i `V9` (`notifications`);
- liczenie po poście, logi INFO;
- strażnik z anulowaniem przyszłości przebiegu;
- powiadomienia z 4 dróg końca przebiegu;
- `GET /api/admin/pipeline-runs/{id}`, `GET /api/admin/notifications`, `POST /api/admin/notifications/{id}/read`, rozszerzony `RunState`;
- heartbeat crona;
- `NotificationBell`, `RunDetailView`, historia w `/admin`;
- atrapa API, kitchen sink, macierz 7 stanów, dokumentacja, PR env.

**Out of scope:**
- push, e-mail, webhook; zmiana kodów wyjścia crona;
- przycisk „Zatrzymaj”, osobne ostrzeżenie;
- dokończenie przebiegu mimo deployu;
- ponowienia w toku i postęp w trakcie `fetch`;
- przybliżony czas zakończenia;
- powiadomienia dla `user`, retencja, tabela nieudanych postów, UI sprawdzenia LLM.

## Architecture / Approach

Dyspozytor oceny po każdym poście dopisuje wynik do liczników przebiegu i przesuwa `last_activity_at`. Kroki przesuwają ją na starcie i końcu. `RunWatchdog` (tylko serwer, własny wątek) zatrzymuje przebieg tej instancji, który nie ma aktywności dłużej niż próg: warunkowy `UPDATE` na `failed`, posty z powrotem, anulowanie przyszłości przebiegu (akcje Embabel działają w osobnej puli, więc przerwanie wątku kolejki do nich nie dociera), a ta sama kolejka bierze następny przebieg.

Każda droga końca przebiegu (podsumowanie lub wyjątek, zamknięcie instancji, odzyskiwanie, strażnik) woła `RunNotifications.runEnded`. Ta funkcja według `PipelineRun.needsAttention()` wstawia idempotentnie po jednym powiadomieniu na admina. SPA czyta powiadomienia w dzwoneczku (co 60 s), a przebiegi na liście i na stronie (co 10 s, gdy trwają). Cron loguje heartbeat z rozszerzonego `RunState`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Postęp, przyczyna, logi | Liczniki po poście, `last_activity_at`, `deploy`/`restart`/`shutdown`, kroki `interrupted`, linie INFO | Podwójne liczenie, jeśli zbiorczy `count` zostanie obok liczenia po poście |
| 2. Strażnik | Zatrzymanie po `PIPELINE_STALL_TIMEOUT`, kolejny przebieg rusza | Akcja-zombie w wątku Embabel i nadpisanie zamkniętego kroku (stąd warunkowy `endStep` i wstawianie kroku) |
| 3. Powiadomienia i API | `notifications`, 3 trasy admina, rozszerzone `RunView`/`RunState` | Pominięta droga końca przebiegu bez powiadomienia |
| 4. Heartbeat crona | Linie postępu i przyczyna przerwania w logu crona | Rozjazd liczby żądań w `test.sh` |
| 5. SPA | Dzwoneczek, strona przebiegu, historia z odświeżaniem | Dostępność okienka (focus, Escape) i 375 px |
| 6. Stany, dokumentacja, PR env | Macierz 7 stanów, docs, weryfikacja na Railway | Redeploy w PR env musi trafić w trwającą ocenę |

**Prerequisites:**
- gałąź `feature/pipeline-run-history-dashboard` przebudowana na świeży `origin/main` (od `c80e846` doszły #58 `watchPatterns` i #59 docs S-05);
- issue S-06 w GitHub;
- Docker do `./mvnw -B verify`;
- Railway PR env z kluczami Wykop/LLM.

**Estimated effort:** ok. 5–7 sesji w 6 fazach (backend 3 fazy, cron 1, SPA 1, weryfikacja 1).

## Open Risks & Assumptions

- **`RAILWAY_DEPLOYMENT_ID`.** Zakładamy, że Railway go wstrzykuje (docs) i że stary deployment zatrzymuje się dopiero po starcie nowego. Jeśli nie, przerwanie przez deploy pokaże się jako `restart`. Sprawdza to krok 6.9 w PR env.
- **Akcja-zombie.** Anulowanie przyszłości nie przerywa akcji w wątku Embabel. Zatrzymany przebieg dalej działa, aż skończy bieżące wywołania. Nie zmienia stanu (zapisy warunkowe), ale zajmuje wątek puli LLM; zawieszona na czymś nieprzerywalnym (np. odczyt gniazda JDBC) zostaje do restartu. Trwałe rozwiązanie to pipeline bez Embabel (roadmapa, `## Parked`).
- **Wolny przebieg zatrzymany jako zawieszony.** Jedno wywołanie LLM trwa najdłużej ok. 34 min (`flex`, `LLM_TIMEOUT=300s`: 6 prób po 300 s i 4 przerwy po 60 s). `llm.check()`, ogon przebiegu i mały RETRY mają w toku tylko jedno wywołanie, więc domyślny próg jest liczony z tej granicy (+5 min). Ryzyko zostaje tylko przy jawnie ustawionym krótszym progu, o czym serwer ostrzega przy starcie. Próg zmienia się zmienną bez deployu kodu.
- **Powiadomienia mogą się rozjechać z przebiegiem**, jeśli błąd zapisu powiadomienia zostanie zalogowany, a nie ponowiony. Wtedy przebieg nadal widać w historii ze znacznikiem uwagi.

## Success Criteria (Summary)

- Admin przy czytaniu feedu widzi w dzwoneczku przebieg, który wymaga uwagi, i jednym kliknięciem otwiera jego szczegóły; noc z samymi `failed` do ponowienia nie daje powiadomienia.
- Trwający przebieg widać na żywo w aplikacji, w logu API i w logu crona; zawieszony kończy się sam po progu (na produkcji ok. 39 min), a kolejny rusza.
- Przebieg przerwany przez deploy ma przyczynę „nowe wdrożenie” i zamknięte kroki; `user` nie widzi niczego z tego.
