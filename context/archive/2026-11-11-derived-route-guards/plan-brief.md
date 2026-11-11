# Strażnik tras admina i `/internal` bez ręcznej listy (S-40) — Plan Brief

> Full plan: `context/changes/derived-route-guards/plan.md`
> Research: `context/changes/post-m3-verification-audit/research.md` (§1 R-30)

## What & Why

Każda zmapowana trasa `/api/admin/**` ma dawać 403 zalogowanemu zwykłemu użytkownikowi, a każda `/internal/**` 401 bez tokenu crona — a zbiory tras mają być liczone z mapowań, nie z ręcznej listy (NFR `prd.md:293`, FR-014, FR-015). Dziś nowa trasa admina bez wpisu w `ADMIN_ROUTES` nie ma testu 403, a nowa trasa crona testu złego tokenu. Zmiana dotyczy samych testów.

## Starting Point

`AuthorizationCompletenessIT` już wylicza wszystkie trasy (`MappedRoutes`) i sprawdza 401 bez sesji. 403 sprawdza `AdminRoleIT` tylko dla 11 tras z `ADMIN_ROUTES`, a zły token i sesję admina na `/internal` — `InternalPipelineRunIT` i `InternalPipelineRunWithoutTokenIT` tylko dla trzech zakodowanych adresów. Sonda `GET /api/admin/probe` istnieje w testach.

## Desired End State

Strażnicy biorą trasy z `MappedRoutes.routesUnder(...)` dla literałów `/api/admin/**` i `/internal/**`; `ADMIN_ROUTES` znika. Trasa dodana przez `RouterFunction` (niewidoczna dla wyliczenia) i publiczna trasa pod admin/internal czerwienieją CI. Sondy dowodzą na stałe, że trasa spoza każdej listy jest sprawdzana.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Źródło zbioru | `MappedRoutes` + dopasowanie `PathPattern` do literału testu | Ta sama semantyka co matcher Spring Security, a zawężony matcher w `SecurityConfig` nie zawęża strażnika. | Plan |
| Granica wyliczenia | Czerwień przy beanie `RouterFunction` | Jedna asercja zamyka jedyną znaną lukę enumeracji z R-30 zamiast polegać na przeglądzie. | Plan (użytkownik) |
| Trwały dowód | Sondy `/api/admin/probe` i nowa `/internal/probe` + kotwica prawdziwej trasy | Pokazują, że zbiór łapie trasę spoza list, i chronią przed pustym zielonym zbiorem. | Plan (użytkownik) |
| Dowód czerwieni | Jeden przebieg psujący na strażnika, cofnięty | Czerwień sprawdzona raz na realnym kodzie, jak w S-39. | Plan (użytkownik) |
| Treść 403 | Status i `{"detail":"forbidden"}` dla każdej trasy | Tanio, a mocniej niż sam status. | Plan |
| Admin przechodzi? | Nie sprawdzamy | Wołałoby handlery ze skutkami ubocznymi (`/api/admin/llm/check` → prawdziwy LLM). | Plan |
| Zakres | Tylko testy i dokumentacja (plus zdanie javadocu `SecurityConfig`) | Cel nie wymaga zmiany zachowania kodu produkcyjnego. | Research |

## Scope

**In scope:** `MappedRoutes.routesUnder` i stałe wzorców, `AdminRoleIT`, `AuthorizationCompletenessIT` (bez `ADMIN_ROUTES`, kontrola `RouterFunction` i wzorców `PUBLIC_ROUTES`), `InternalProbeController`, pętle w `InternalPipelineRunIT` i `InternalPipelineRunWithoutTokenIT`, `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`, javadoc `MissingDaysIT` i `SecurityConfig`.

**Out of scope:** zmiana `SecurityConfig` i kontrolerów; sprawdzanie, że admin przechodzi; trasa admina zmapowana poza `/api/admin/`; brak 403 na trasach zwykłego użytkownika; rozszerzenie `MappedRoutes` o `RouterFunction` i handlery zasobów; sondy w kontekście `AuthorizationCompletenessIT`.

## Architecture / Approach

`MappedRoutes.routesUnder(handlerMapping, pattern)` wybiera trasy, których `concretePath()` pasuje do `PathPattern`. Każdy strażnik woła wszystkie wybrane trasy, zbiera niepowodzenia z ich statusem i wymaga kotwic (sonda + jedna prawdziwa trasa). Sondy żyją w `pl.szczurradar.probe`, poza skanowaniem komponentów, dołączane `@Import` tylko tam, gdzie są potrzebne — bez nowych kontekstów Springa.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Trasy admina z mapowań | 403 na każdej trasie `/api/admin/**`, `ADMIN_ROUTES` usunięte, czerwień przy `RouterFunction` i publicznej trasie admin/internal, dokumentacja | Prefiks tekstowy zamiast `PathPattern` przepuściłby `/api/admin` bez ukośnika |
| 2. Trasy `/internal` z mapowań | 401 na każdej trasie `/internal/**` w obu konfiguracjach tokenu, bez sesji i przebiegu, sonda `/internal/probe` | Pętla × 7 wartości nagłówka musi zachować kontrole „bez `Set-Cookie`” i „bez przebiegu” |

**Prerequisites:** brak (S-40 bez zależności); issue #150.
**Estimated effort:** ok. 1 sesja, 2 fazy, 1 nowy plik testowy (`InternalProbeController`) i zmiany w 6 klasach testów (w `MissingDaysIT` tylko javadoc).

## Open Risks & Assumptions

- Zakładamy, że wszystkie trasy aplikacji to `@RequestMapping`; pierwszy `RouterFunction` czerwieni CI i wymaga rozszerzenia `MappedRoutes` w tym samym PR.
- Trasa przeznaczona dla admina, a zmapowana poza `/api/admin/`, nie jest wykrywana; chroni ją tylko reguła z `REPOSITORY_GUIDELINES.md` i przegląd.
- Przebiegi psujące dotykają `SecurityConfig` i `LlmCheckController` tymczasowo; muszą być cofnięte przed commitem (`git status --porcelain szczur-radar-api/src/main`).

## Success Criteria (Summary)

- Nowa trasa admina bez roli i nowa trasa crona bez tokenu czerwienieją CI bez dopisywania czegokolwiek do listy (oba przebiegi psujące czerwone, dawne listy byłyby zielone).
- `ADMIN_ROUTES` nie istnieje poza `context/`; `./mvnw -B verify` i CI PR zielone.
