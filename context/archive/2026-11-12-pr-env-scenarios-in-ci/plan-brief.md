# S-31: ręczne weryfikacje PR env jako scenariusze w CI — Plan Brief

> Full plan: `context/changes/pr-env-scenarios-in-ci/plan.md`
> Research: `context/changes/pr-env-scenarios-in-ci/research.md`

## What & Why

Kroki weryfikacji, które dotąd agent albo właściciel klikał w PR env (S-13, S-17, S-18, S-20, S-27 i noc S-34), mają chodzić w jobie `backend` przy każdym PR. To gwiazda przewodnia M-4: weryfikacja wycinka przestaje zależeć od prawdziwego Wykopu, Arctic Shift i LLM, a regresja biegnie sama.

## Starting Point

Testy mają dziś dwie połowy, które się nie spotykają. `PipelineRunIT` doprowadza przebiegi do końca, ale startuje je przez serwis i sprawdza SQL-em. Testy startujące przez HTTP mają Wykop z pustym dniem, więc ich przebiegi kończą się na pobieraniu. Atrapy źródeł z prawdziwych odpowiedzi istnieją; atrapy LLM z prawdziwych odpowiedzi dla tych dni nie ma.

## Desired End State

`./mvnw -B verify` uruchamia klasę `PrEnvScenariosIT` z 8 scenariuszami. Każdy startuje przebieg przez trasę admina albo crona, czeka na jego koniec i czyta wynik przez API: stronę przebiegu, feed, `/raw`, „Dni bez danych”, dzwonek, podsumowanie nocy i wiadomości odebrane przez lokalny ntfy. Lista tego, co zostaje ręczne, jest nazwana w `verification-scope.md`, a kolejne wycinki dopisują scenariusz zamiast kroku w PR env.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Granica „pełnego API” | Kontekst Springa w JVM testu z Testcontainers | Test na obrazie Dockera wymagałby kodu produkcyjnego tylko dla testów (adresy źródeł i LLM są stałymi). | Research |
| Atrapa LLM | Prawdziwy klient OpenRoutera na lokalnym serwerze z nagranymi ciałami odpowiedzi dla 33 postów fixtur | Roadmapa wymaga prawdziwych payloadów, a odpowiedź przechodzi wtedy przez transport, parser i walidator. | Plan |
| Model nagrania | `openai/gpt-6-luna` przez OpenRouter | To model produkcji. | Plan |
| Zakres wycinków | Pięć z roadmapy + noc S-34 | Trasa crona i ntfy są potrzebne dla S-20 i S-18, więc S-34 to jeden scenariusz więcej. | Plan |
| Przerwanie przez deploy (S-17) | Bez scenariusza, zostaje na liście ręcznej | Logikę kryją istniejące testy, a prawdziwego SIGTERM CI nie odtworzy. | Plan |
| Lista ręczna | Uzupełniona z 4 do 8 pozycji w `verification-scope.md` | „Done when” M-4 ma opierać się na liście zgodnej z rzeczywistością. | Plan |
| Budżet czasu CI | Do +60 s na `backend`, `timeout-minutes: 15` | Zapas na szacunek 15–30 s i twardy limit na zawieszony test. | Plan |
| Reguła na przyszłość | Zapis w `REPOSITORY_GUIDELINES.md` | Wytyczne są w kontekście każdej sesji planowania. | Plan |
| Kolejka alertów w teście | Prawdziwa kolejka i `NtfyAlerts`, limity w czasie wirtualnym | Limit 2 zdarzeń na minutę wydłużyłby klasę o minuty. | Plan |
| Organizacja | Jedna klasa, jeden kontekst | Każdy dodatkowy kontekst to ok. 4 s i kontener Postgresa. | Research |

## Scope

**In scope:**
- 33 nagrane odpowiedzi dostawcy i narzędzie nagrywające (poza CI)
- Serwer dostawcy odpowiadający według treści żądania
- Wspólne atrapy: `LocalWykop`, `LocalNtfy`, `MutableClock`; Reddit według subreddita
- 8 scenariuszy w `PrEnvScenariosIT`
- `timeout-minutes` joba `backend` i pomiar czasu
- Lista ręczna, reguła w wytycznych, sekcja S-31 w `deploy-plan.md`
- Cztery celowe zepsucia jako dowód

**Out of scope:**
- Test na obrazie Dockera i jakakolwiek zmiana kodu produkcyjnego
- Scenariusz przerwania przez deploy, grep formatu logu, czas startu
- Prawdziwe logowanie w scenariuszach
- Przepisanie istniejących klas IT na wspólny `LocalWykop`
- Playwright i oględziny UI (M-5)

## Architecture / Approach

Jedna klasa `@SpringBootTest` z MockMvc. Źródła to lokalne serwery HTTP z prawdziwymi klientami i fixturami. LLM to prawdziwy klient OpenRoutera, którego lokalny serwer rozpoznaje post po wiadomości użytkownika (deterministyczny JSON wejścia) i odpowiada jego nagranym ciałem. Zdarzenia operatora idą przez prawdziwą kolejkę i `NtfyAlerts` do lokalnego serwera ntfy. Scenariusz czeka na status końcowy, potem na oddanie przebiegu przez instancję, i dopiero wtedy czyta trasy.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Nagrania i atrapa dostawcy LLM | 33 nagrania, serwer odpowiadający według treści, test kompletności | Wymaga klucza OpenRoutera właściciela; płatne wywołania |
| 2. Kontekst scenariuszy i zwykły przebieg | Wspólne atrapy i scenariusze A–B (zakres do feedu, powtórka) | Asynchroniczne dostarczenie alertów (cisza kolejki) |
| 3. Scenariusze sygnałów, dni bez danych i nocy | Scenariusze C–H | Wyścig między statusem końcowym a powiadomieniem; RETRY przy odrzuconych nagraniach |
| 4. CI, lista ręczna i dowód | Limit i pomiar czasu, dokumenty, zepsucia, stabilność | Przekroczenie budżetu 60 s |

**Prerequisites:** S-30 (zrobione); plik `~/.config/szczur-radar/openrouter.env` z kluczem dla fazy 1.
**Estimated effort:** ok. 3–4 sesje; faza 1 interaktywnie, fazy 2–4 mogą iść bez nadzoru.

## Open Risks & Assumptions

- Wynik walidacji nagrań (ile odpowiedzi modelu przejdzie kontrakt) będzie znany dopiero po nagraniu; oczekiwane liczniki scenariuszy są wpisywane po fazie 1.
- Konfiguracja testowa w pakiecie `alerts` podmienia `OperatorAlerts` i kolejkę beanami `@Primary` o innych nazwach, bo nadpisywanie definicji jest wyłączone. Przegląd planu sprawdził to w kodzie; szew w kodzie produkcyjnym nie jest potrzebny. Nie zostało to jeszcze uruchomione.
- Kolejka alertów wysyła do ntfy na własnym wątku, po oddaniu przebiegu, i nie pokazuje, czy jest bezczynna. Scenariusze czekają więc, aż liczba zakończonych zgłoszeń zrówna się z liczbą linii `taken`.
- Zmiana kontraktu oceny albo fixtur źródeł wymaga ponownego nagrania (ok. 33 płatnych wywołań).
- Szacunek 15–30 s czasu scenariuszy nie jest zmierzony.

## Success Criteria (Summary)

- CI na PR jest zielone z 8 scenariuszami `PrEnvScenariosIT`, w budżecie +60 s.
- Każde z czterech celowych zepsuć kodu produkcyjnego czerwieni scenariusz.
- Właściciel akceptuje listę ręczną i regułę dla kolejnych wycinków.
