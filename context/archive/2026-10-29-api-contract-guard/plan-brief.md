# S-24: kontrakt SPA ↔ API pilnowany testem — Plan Brief

> Full plan: `context/changes/api-contract-guard/plan.md`
> Research: `context/changes/api-contract-guard/research.md`

## What & Why

Kontrakt między API a SPA trzyma się dziś na ręcznej dyscyplinie (audyt po M-2: K2, K3, K4). S-24 dokłada trzy strażniki:
- odpowiedź na nieoczekiwany błąd serwera ma zdefiniowany kod, zgodny z tym, którego używają atrapa i testy SPA;
- duże odpowiedzi mają zapisany pełny kształt, który czytają obie strony;
- reguły skopiowane między Javą a TS nie mogą się rozjechać bez czerwonego testu.

Celem jest wykrycie rozjazdu, a nie nowa warstwa: bez generatora klienta i bez OpenAPI.

## Starting Point

Prawdziwy błąd 500 (zmierzony na jarze z obrazu) ma ciało Spring Boot `{timestamp,status,error,path}` bez `detail`. Atrapa i 12 miejsc w testach SPA udają `{"detail":"internal_error"}`, którego `ErrorCode` nie zna. Awaria bazy przy logowaniu daje 401 i komunikat o złym haśle.

Żaden IT nie zapisuje całego `RunView`, `PostItem` ani `FailedPost`, a ręczne dane testów web łamią reguły serwera w co najmniej 4 miejscach. Kopie reguł (białe znaki, limity, listy enumów z etykietami) są dziś równe, ale nic nie porównuje stron.

## Desired End State

Każdy błąd z `/error` ma kształt `{"detail":kod}`, a nieoczekiwany dostaje 500 `internal_error`. `szczur-radar-web/src/contract/` zawiera trzy złote pełne ciała (`run.json`, `post.json`, `failed-post.json`) i rejestr reguł `rules.json`.

Strona Javy (IT, `SpaRulesTest`) i strona SPA (`shapes.test.ts`, `rules.test.ts`) porównują te pliki ze swoim kodem. Nowe pole, wartość enumu z etykietą, kod błędu albo zmieniony limit bez aktualizacji drugiej strony daje czerwony `verify` albo `npm test`.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kształt i kod błędu | Własny `ApiErrorController`: wszystko z `/error` to `{"detail":kod}`, 5xx → nowy `internal_error`, 404 → `not_found`, inne 4xx → `invalid_request` | Jeden kształt błędu w całym API; atrapa już używa tych kodów, a kontener dalej loguje wyjątek | Plan |
| Logowanie przy awarii bazy | 500 `internal_error` z logiem ERROR, zwracane wprost z `AuthController` | Komunikat o złym haśle przy padniętej bazie wprowadza w błąd; owinięcie wyjątku nie wystarczy, bo Security przeszukuje łańcuch przyczyn | Research + Plan |
| Awaria magazynu sesji | Poza zakresem (znana różnica) | Wymaga obejścia Spring Session w `/error`; przy padniętej bazie `/api/ready` i tak to pokazuje | Plan |
| Źródło pełnego kształtu | Złote pliki JSON w `szczur-radar-web/src/contract/`, maskowane `<uuid>`/`<instant>`; IT porównuje ciało, Vitest typowane próbki | Jedno źródło prawdy dla obu stron, mieści się w kontekście obrazu web i nie redeployuje API | Research + Plan |
| Zakres strażnika kopii | K4 + stałe (hasło, 3 próby) + każdy enum z etykietą w `labels.ts` + `AttentionReason` + kody błędów | Kompletność wyznacza istniejąca mapa etykiet; jeden mechanizm na wszystko | Plan |
| F1 z S-22 (`skipped` w `runTone`) | Poza zakresem | S-24 wykrywa rozjazd, nie przenosi reguł (lekcja „Uzasadniaj zakres celem”) | Plan |
| Atrapa (K5) | Minimum: tylko jej literały kodów błędów są skanowane | Roadmapa wymienia K2–K4; reszta K5 osobno | Plan |
| Komunikaty z liczbami | Budowane ze stałych („maks. 7 dni”, „3 nieudane próby”) | Literał w tekście to kopia reguły, której test nie widzi | Research |

## Scope

**In scope:**
- `ErrorCode.INTERNAL_ERROR`, `ErrorCode.forStatus`, `ApiErrorController`, logowanie przy awarii bazy, `ApiErrorHttpIT` na prawdziwym porcie
- `run.json`, `post.json`, `failed-post.json`; `ContractFiles` + trzy testy w `PipelineRunControllerIT`/`PostsApiIT`; `samples.ts` + `shapes.test.ts`
- Naprawa ręcznych danych testów web, które łamią reguły serwera
- `rules.json`, `SpaRulesTest`, `rules.test.ts` (etykiety, tony, powody, pełność unii TS, skan kodów błędów w SPA, testach i atrapie)
- Eksport stałych SPA, publiczne stałe Javy w adnotacjach, wpisy w `REPOSITORY_GUIDELINES.md`

**Out of scope:**
- OpenAPI, generator, walidacja odpowiedzi w runtime
- Awaria magazynu sesji
- F1
- Typowanie i dane atrapy (K5)
- NUL w `q` i limit ponowienia w SPA
- Prefiltr w KitchenSink
- Powiadomienia, „Dni bez danych” i świeżość (już mają ścisłe ciała)
- Migracja wszystkich builderów testów

## Architecture / Approach

Wspólne pliki leżą w `szczur-radar-web/src/contract/`, bo obraz web typecheckuje testy z własnego kontekstu, a zmiana tam redeployuje tylko web. Java czyta je przez `Path.of("../szczur-radar-web/src/contract/…")`, jak `AlertSettingsTest` czyta tabelę S-25.

Złote ciała: IT maskuje UUID i czasy, a potem porównuje drzewo z plikiem. Typowana próbka TS z kontrolą nadmiarowych pól jest porównywana z plikiem przez `toEqual`, więc pole dodane lub usunięte po każdej stronie jest czerwone.

Rejestr reguł ma kompletność kluczy w obie strony: w Javie jawne mapy nazwa → stała/enum, w TS zbiory `labels` i krotki z kontrolą pełności unii.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Kod nieoczekiwanego błędu (API) | Jeden kształt `{"detail"}` dla `/error`, `internal_error`, uczciwe logowanie przy awarii bazy | Zamiana `BasicErrorController` musi zostawić 401 dla bezpośredniego `/error` (`AuthorizationCompletenessIT`) |
| 2. Wspólny pełny kształt dużych odpowiedzi | Trzy złote pliki czytane przez IT i Vitest; poprawne dane testowe | Seed z jak najmniejszą liczbą `null`; import JSON w typecheck obrazu web |
| 3. Rejestr skopiowanych reguł | `rules.json` + test po każdej stronie + skan kodów błędów | Skan literałów nie może przechodzić po cichu (minimum trafień) |

**Prerequisites:** Docker (Testcontainers, build obrazu web); gałąź `feature/api-contract-guard` od świeżego `origin/main`; issue S-24.
**Estimated effort:** ~2–3 sesje w 3 fazach; zmiany w API i web, bez migracji.

## Open Risks & Assumptions

- `?raw` atrapy (`../../dev/mock-api.js?raw`) i import JSON w testach przechodzą `vue-tsc` w obrazie. To wniosek z konfiguracji, nie z uruchomienia; potwierdzają go kryteria 2.4 i 3.4.
- Maskowanie po wartości (każdy UUID, każdy instant) zakłada, że żadne pole kontraktu nie potrzebuje porównania konkretnej wartości UUID albo czasu. Dziś to prawda.
- Ciało błędu za Caddy web na Railway nie było mierzone; zakładamy, że Caddy przekazuje je bez zmian.

## Success Criteria (Summary)

- Błąd serwera w SPA ma kod `internal_error`, taki sam jak w atrapie i testach, a logowanie przy awarii bazy nie mówi już o złym haśle.
- Nowe lub usunięte pole `RunView`/`PostItem`/`FailedPost` albo typu TS daje czerwony test, dopóki obie strony i złoty plik nie są zgodne.
- Nowa wartość enumu z etykietą, kod błędu, zmiana białych znaków albo limitu bez drugiej strony daje czerwony `SpaRulesTest` albo `rules.test.ts`.
