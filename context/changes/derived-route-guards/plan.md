# Strażnik tras admina i `/internal` bez ręcznej listy (S-40) Implementation Plan

## Overview

Testy autoryzacji mają liczyć zbiory tras z mapowań aplikacji zamiast z ręcznej listy: każda zmapowana trasa `/api/admin/**` daje 403 `forbidden` zalogowanemu zwykłemu użytkownikowi, a każda zmapowana trasa `/internal/**` daje 401 bez ważnego tokenu crona. Lista `ADMIN_ROUTES` i trzy zakodowane adresy `/internal/…` znikają. Zmiana dotyczy wyłącznie testów (`szczur-radar-api/src/test/`) i kilku zdań dokumentacji; kod produkcyjny (poza jednym zdaniem javadocu `SecurityConfig`) zostaje bez zmian.

Issue: #150 (`S-40: strażnik tras admina i /internal bez ręcznej listy`). Audyt: `context/changes/post-m3-verification-audit/research.md` §1 R-30.

## Current State Analysis

- `AuthorizationCompletenessIT` (`auth/AuthorizationCompletenessIT.java:87-110`) już wylicza każdą trasę z `RequestMappingHandlerMapping` (`web/MappedRoutes.java:34-50`, zmienne ścieżki wypełnione atrapą typu, `:61-89`) i woła ją bez sesji: wszystko poza `PUBLIC_ROUTES` ma dać 401. W jego kontekście `PIPELINE_TRIGGER_TOKEN` nie jest ustawiony, więc `/internal/**` też daje 401 — ale tylko w wariancie „bez tokenu na serwerze, bez nagłówka”.
- 403 dla zwykłego użytkownika sprawdza `AdminRoleIT.listedAdminRoutesRejectPlainUsers` (`auth/AdminRoleIT.java:64-79`), tylko dla ręcznej listy `ADMIN_ROUTES` (11 tras, `AuthorizationCompletenessIT.java:56-68`). Nowa trasa admina bez wpisu nie ma testu 403; jej ochrona zależy od matchera `SecurityConfig.java:106` (`/api/admin/**` → `hasRole(ADMIN)`).
- `/internal/**` ma własny łańcuch (`SecurityConfig.java:69-91`, `securityMatcher("/internal/**")`, `anyRequest().hasAuthority(...)`). Przy skonfigurowanym tokenie 401 dla złego tokenu i dla sesji admina (bez `Set-Cookie`, bez nowego wiersza sesji — helper `internal(...)`, `InternalPipelineRunIT.java:404-420`) sprawdzają `InternalPipelineRunIT.java:156-199` i `InternalPipelineRunWithoutTokenIT.java:38-59` tylko dla trzech zakodowanych URI (`/daily`, `/{id}`, `/nightly-summary`).
- `MappedRoutes` widzi tylko `RequestMappingHandlerMapping`. Dziś w `src/main` nie ma `RouterFunction`, `WebMvcConfigurer` ani katalogu `static/`, więc to wszystkie trasy aplikacji; audyt (R-30) zaznacza tę granicę jako niesprawdzaną.
- Testowa trasa-sonda już istnieje: `pl.szczurradar.probe.AdminProbeController` (`GET /api/admin/probe`, `src/test/java/pl/szczurradar/probe/AdminProbeController.java`), poza `pl.szczurradar.api`, dołączana tylko przez `@Import` w `AdminRoleIT`.
- `ADMIN_ROUTES` wymieniają: `REPOSITORY_GUIDELINES.md:40`, javadoc `AuthorizationCompletenessIT:31-35`, `AdminRoleIT:27-32`, `MissingDaysIT:56-57`.

## Desired End State

- `AdminRoleIT` woła jako zwykły użytkownik każdą zmapowaną trasę, której ścieżka pasuje do `/api/admin/**`, i dla każdej oczekuje 403 `{"detail":"forbidden"}`. Wyprowadzony zbiór musi zawierać sondę `GET /api/admin/probe` i prawdziwą trasę `POST /api/admin/pipeline-runs`.
- `ADMIN_ROUTES` nie istnieje w kodzie ani w dokumentacji poza `context/`.
- `AuthorizationCompletenessIT` czerwienieje, gdy kontekst ma bean `RouterFunction` (wyliczenie tras by go nie widziało) i gdy wpis `PUBLIC_ROUTES` leży pod `/api/admin/**` albo `/internal/**`.
- `InternalPipelineRunIT` (token skonfigurowany: brak, zły i obcy nagłówek, sesja admina) i `InternalPipelineRunWithoutTokenIT` (token nieskonfigurowany) wołają każdą zmapowaną trasę `/internal/**` i oczekują 401 `{"detail":"not_authenticated"}` bez nowego przebiegu; `InternalPipelineRunIT` sprawdza przy tym (helper `internal(...)`), że żadna odpowiedź nie ustawia ciasteczka ani nie tworzy wiersza sesji. Zbiór musi zawierać nową sondę `GET /internal/probe` i `POST /internal/pipeline-runs/daily`.
- Każdy strażnik ma zapisany jednorazowy przebieg ze zmianą psującą kod, po którym zmiana jest cofnięta.
- `./mvnw -B verify` i CI PR zielone.

### Key Discoveries:

- Reguły `SecurityConfig` są ścieżkowe i działają przed dispatcherem, więc 401/403 przychodzi przed wiązaniem ciała: trasy `POST` z wymaganym ciałem można wołać bez ciała (dziś tak działa `everyNonPublicRouteRequiresASession`).
- Zbiór ma być dopasowany **własnym literałem** wzorca testu (`/api/admin/**`, `/internal/**`, kontrakt z `REPOSITORY_GUIDELINES.md`), a nie stałą z `SecurityConfig`: zawężony matcher zawęziłby inaczej i strażnik, i ochronę, a test zostałby zielony.
- `AdminProbeController` w `AdminRoleIT` to trwały dowód, że wyprowadzony zbiór łapie trasę, której nie wymienia żadna lista; ten sam wzorzec dostaje `/internal`.
- Dodanie `@Import` sondy do `InternalPipelineRunIT` i `InternalPipelineRunWithoutTokenIT` nie tworzy nowego kontekstu Springa ponad dzisiejsze: oba mają już własny klucz (`TestBeans`, właściwość tokenu).

## What We're NOT Doing

- Żadnych zmian zachowania kodu produkcyjnego (`SecurityConfig`, kontrolery); jedynie zdanie javadocu `SecurityConfig`.
- Nie sprawdzamy, że admin przechodzi przez każdą trasę admina: wywołałoby to handlery ze skutkami ubocznymi (np. `GET /api/admin/llm/check` łączy się z prawdziwym dostawcą LLM).
- Nie wykrywamy trasy przeznaczonej dla admina, a zmapowanej poza `/api/admin/` (zamiaru nie da się wyprowadzić z kodu); zasada „trasy admina idą pod `/api/admin/`” zostaje w `REPOSITORY_GUIDELINES.md`.
- Nie sprawdzamy, że zwykły użytkownik nie dostaje 403 na trasach spoza `/api/admin/` (wołanie ich jako użytkownik uruchamia handlery ze skutkami ubocznymi).
- Nie rozszerzamy `MappedRoutes` o `RouterFunction` ani handlery zasobów: strażnik tylko czerwienieje przy pierwszym `RouterFunction`, a rozszerzenie robi PR, który go wprowadza. Handlery zasobów statycznych zostają poza zakresem (API nie ma `static/`; ich żądania i tak przechodzą przez łańcuch `apiSecurity`).
- Nie dodajemy sond do kontekstu `AuthorizationCompletenessIT` (nowy kontekst Springa bez zysku: 401 bez sesji dla `/internal/**` sprawdza już ten test).
- Nie ruszamy `theTokenMeansNothingOnApiRoutes` ani testów zachowania tras `/internal` (dzienny zakres, podsumowanie nocy, 400/404).

## Implementation Approach

`MappedRoutes` dostaje jedną funkcję wyboru tras pod wzorcem (dopasowanie przez `PathPattern`, tą samą semantyką co matchery Spring Security) i dwie stałe z literałami kontraktu. Strażnicy zamieniają listy na ten wybór i dostają kotwice: sondę (trasa spoza każdej listy) i jedną prawdziwą trasę (filtr dopasowuje prawdziwe kontrolery). Czerwień każdego strażnika jest raz sprawdzona przebiegiem ze zmianą psującą, która omija dzisiejsze listy — tak jak w S-39 — i cofnięta przed commitem.

## Critical Implementation Details

- **Dopasowanie ścieżki**: porównuj `Route.concretePath()` (zmienne wypełnione) ze wzorcem przez `PathPatternParser.defaultInstance.parse(pattern).matches(PathContainer.parsePath(path))`; `/api/admin/**` pasuje wtedy też do samego `/api/admin`, jak matcher w `SecurityConfig`. Prefiks tekstowy (`startsWith`) tego nie robi.
- **Kotwice, nie listy**: asercja `contains(...)` na sondzie i jednej prawdziwej trasie chroni przed pustym, zielonym zbiorem (zły wzorzec, zmiana `MappedRoutes`); nie wymienia tras, więc nowa trasa niczego nie wymaga.

## Phase 1: Trasy admina z mapowań

### Overview

`AdminRoleIT` sprawdza 403 na każdej trasie `/api/admin/**` wyliczonej z mapowań; `ADMIN_ROUTES` znika; `AuthorizationCompletenessIT` pilnuje granic wyliczenia.

### Changes Required:

#### 1. Wybór tras pod wzorcem

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/web/MappedRoutes.java`

**Intent**: Jedno miejsce, które wybiera trasy leżące pod wzorcem ścieżki, i literały kontraktu dla obu strażników; javadoc mówi, że wyliczenie widzi tylko trasy `@RequestMapping` i że `AuthorizationCompletenessIT` czerwienieje przy `RouterFunction`.

**Contract**: `public static final String ADMIN_PATHS = "/api/admin/**"`, `public static final String INTERNAL_PATHS = "/internal/**"`; `public static List<Route> routesUnder(RequestMappingHandlerMapping handlerMapping, String pathPattern)` — trasy z `routes(...)`, których `concretePath()` pasuje do wzorca (`PathPattern`, patrz Critical Implementation Details).

#### 2. 403 na każdej trasie admina

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AdminRoleIT.java`

**Intent**: `listedAdminRoutesRejectPlainUsers` zamienia się w test po każdej trasie z `routesUnder(..., ADMIN_PATHS)`: zwykły użytkownik dostaje 403 z treścią `{"detail":"forbidden"}`; zbiór zawiera kotwice. Javadoc klasy bez `ADMIN_ROUTES`.

**Contract**: test `everyAdminRouteRejectsPlainUsers`; zebrane klucze tras zawierają `GET /api/admin/probe` i `POST /api/admin/pipeline-runs`; błąd wymienia każdą trasę, która nie dała 403, z jej statusem (wzór zbierania z `everyNonPublicRouteRequiresASession`), zamiast padać na pierwszej.

#### 3. Bez `ADMIN_ROUTES`, granice wyliczenia

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java`

**Intent**: Usuń `ADMIN_ROUTES` i jego asercje w `allowlistsReferenceExistingRoutes` (kontrola martwych `PUBLIC_ROUTES` zostaje). Dodaj dwie kontrole: żaden wpis `PUBLIC_ROUTES` nie leży pod `ADMIN_PATHS` ani `INTERNAL_PATHS` (zastępuje rozłączność z `ADMIN_ROUTES` i utrwala regułę „`/internal` nigdy w `PUBLIC_ROUTES`”); kontekst nie ma żadnego beana `org.springframework.web.servlet.function.RouterFunction`, z komunikatem, że `MappedRoutes` trzeba najpierw rozszerzyć. Javadoc klasy: trasy admina idą pod `/api/admin/`, a `AdminRoleIT` sprawdza każdą z nich bez listy.

**Contract**: testy `allowlistsReferenceExistingRoutes` (bez `ADMIN_ROUTES`, z kontrolą wzorców) i `everyRouteIsWalkedByMappedRoutes` (`ApplicationContext.getBeanNamesForType(RouterFunction.class)` puste); `everyNonPublicRouteRequiresASession` bez zmian.

#### 4. Dokumentacja

**Files**: `REPOSITORY_GUIDELINES.md` (sekcja Coding Style & Testing, zdanie o trasach admina, `:40`), `szczur-radar-api/README.md` (punkt „Autoryzacja”, `:284`), `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/MissingDaysIT.java` (javadoc, `:56-57`), `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java` (javadoc klasy, `:38-52`)

**Intent**: Zamiast „listed in `ADMIN_ROUTES`”: każda zmapowana trasa pod `/api/admin/` jest sprawdzana przez `AdminRoleIT` (403 dla zwykłego użytkownika) bez listy, a wyliczenie widzi tylko trasy `@RequestMapping` (`RouterFunction` czerwieni `AuthorizationCompletenessIT`). `MissingDaysIT` wskazuje `AdminRoleIT` bez nazwy listy; javadoc `SecurityConfig` wymienia `AdminRoleIT` obok `AuthorizationCompletenessIT`.

**Contract**: tylko tekst; `git grep ADMIN_ROUTES` poza `context/` pusty.

### Success Criteria:

#### Automated Verification:

- Strażnik tras admina zielony: `cd szczur-radar-api && ./mvnw -B verify -Dit.test='AdminRoleIT,AuthorizationCompletenessIT' -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- Przebieg psujący (nowa trasa bez roli): dodaj tymczasowo w `llm/api/LlmCheckController` metodę `@GetMapping("/break")` zwracającą `Map.of()` i w `SecurityConfig` przed regułą `/api/admin/**` wiersz `.requestMatchers("/api/admin/llm/break").authenticated()`; uruchom to samo polecenie, oczekuj czerwonego `AdminRoleIT.everyAdminRouteRejectsPlainUsers` z `GET /api/admin/llm/break -> 200` (dawna lista byłaby zielona, bo trasy na niej nie ma); cofnij ręcznie obie dodane zmiany (metodę w `LlmCheckController` i wiersz matchera w `SecurityConfig` — nie `git checkout`, który zmazałby też niezatwierdzony javadoc `SecurityConfig` ze zmiany 4) i z katalogu głównego repo potwierdź, że `git diff --stat -- szczur-radar-api/src/main` pokazuje tylko `SecurityConfig.java`, a `git diff -- szczur-radar-api/src/main` tylko zmianę javadocu.
- Przebieg psujący (`RouterFunction`): dodaj tymczasowo do `AuthorizationCompletenessIT` zagnieżdżoną `@TestConfiguration` z beanem `RouterFunction<ServerResponse>` (`RouterFunctions.route().GET("/api/admin/router", r -> ServerResponse.ok().build()).build()`), uruchom `./mvnw -B verify -Dit.test=AuthorizationCompletenessIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`, oczekuj czerwonego `everyRouteIsWalkedByMappedRoutes`; usuń dodaną konfigurację.
- `ADMIN_ROUTES` zniknęło poza `context/`: `! git grep -n ADMIN_ROUTES -- ':!context'`
- Pełny zestaw: `cd szczur-radar-api && ./mvnw -B verify`

**Implementation Note**: Przebiegi psujące nie trafiają do commita; po nich `src/main` zawiera tylko zmianę javadocu `SecurityConfig`.

---

## Phase 2: Trasy `/internal` z mapowań

### Overview

Testy tokenu crona wołają każdą trasę `/internal/**` wyliczoną z mapowań, w obu konfiguracjach serwera, zamiast trzech zakodowanych adresów.

### Changes Required:

#### 1. Sonda `/internal`

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/probe/InternalProbeController.java` (nowy)

**Intent**: Testowa trasa crona spoza każdej listy, wzór `AdminProbeController`: poza `pl.szczurradar.api`, więc skanowanie komponentów jej nie widzi, a testy dołączają ją przez `@Import`.

**Contract**: `@RestController`, `@GetMapping("/internal/probe")` → `Map.of("status", "ok")`; javadoc wskazuje `InternalPipelineRunIT` i `InternalPipelineRunWithoutTokenIT`.

#### 2. 401 na każdej trasie przy skonfigurowanym tokenie

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java`

**Intent**: `withoutTheTokenEveryInternalRouteIs401AndCreatesNothing` (wszystkie dzisiejsze wartości nagłówka) i `anAdminSessionIsNotAcceptedOnInternalRoutes` przechodzą pętlą po `routesUnder(..., INTERNAL_PATHS)` przez helper `internal(...)` (bez `Set-Cookie`, bez nowego wiersza sesji) i dla każdej trasy oczekują 401 `{"detail":"not_authenticated"}`; liczba przebiegów i brak `alerts.raise` sprawdzane jak dziś. `@Import` dostaje `InternalProbeController`; javadoc klasy mówi, że trasy są wyliczane.

**Contract**: zbiór zawiera `GET /internal/probe` i `POST /internal/pipeline-runs/daily`; trasy wołane bez ciała (401 przychodzi przed wiązaniem); stałe `DAILY`/`SUMMARY` zostają dla testów zachowania.

#### 3. 401 na każdej trasie bez tokenu na serwerze

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunWithoutTokenIT.java`

**Intent**: `everyInternalRouteIs401` przechodzi pętlą po `routesUnder(..., INTERNAL_PATHS)` dla każdej wartości nagłówka i oczekuje 401 `{"detail":"not_authenticated"}` bez nowego przebiegu; `@Import` dostaje `InternalProbeController`.

**Contract**: te same kotwice co w `InternalPipelineRunIT`.

#### 4. Dokumentacja

**Files**: `REPOSITORY_GUIDELINES.md` (zdanie o nowej trasie `/internal/`, `:41`), `szczur-radar-api/README.md` (punkt „Autoryzacja”, `:284`)

**Intent**: „A new `/internal/` route answers 401 without the token” dostaje wskazanie strażnika: `InternalPipelineRunIT` i `InternalPipelineRunWithoutTokenIT` sprawdzają każdą zmapowaną trasę `/internal/**`, bez listy.

**Contract**: tylko tekst.

### Success Criteria:

#### Automated Verification:

- Strażnik tras `/internal` zielony: `cd szczur-radar-api && ./mvnw -B verify -Dit.test='InternalPipelineRunIT,InternalPipelineRunWithoutTokenIT' -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- Przebieg psujący: w `SecurityConfig.internalSecurity` zamień `anyRequest().hasAuthority(...)` na `.requestMatchers("/internal/pipeline-runs/**").hasAuthority(PipelineTriggerTokenFilter.AUTHORITY).anyRequest().permitAll()`; uruchom to samo polecenie, oczekuj czerwonych testów obu klas z `GET /internal/probe` (dawne trzy adresy byłyby zielone); cofnij z katalogu głównego repo (`git checkout -- szczur-radar-api/src/main`; javadoc `SecurityConfig` z fazy 1 jest już w commicie) i tamże potwierdź `git status --porcelain szczur-radar-api/src/main` puste.
- Pełny zestaw: `cd szczur-radar-api && ./mvnw -B verify`
- CI PR zielone: `gh pr checks <PR> --watch` kończy się bez nieudanego joba

---

## Testing Strategy

### Unit Tests:

- Brak nowych testów jednostkowych: dopasowanie wzorca to jedno wywołanie `PathPattern`, a kotwice w strażnikach (sonda i prawdziwa trasa) czerwienieją, gdy wybór zwróci za mało.

### Integration Tests:

- `AdminRoleIT`: 403 `forbidden` na każdej trasie `/api/admin/**`, sonda w zbiorze.
- `AuthorizationCompletenessIT`: 401 bez sesji na każdej trasie poza `PUBLIC_ROUTES` (bez zmian), brak `RouterFunction`, `PUBLIC_ROUTES` poza `/api/admin/**` i `/internal/**`.
- `InternalPipelineRunIT`, `InternalPipelineRunWithoutTokenIT`: 401 na każdej trasie `/internal/**` w obu konfiguracjach tokenu, bez sesji i przebiegu, sonda w zbiorze.

### Manual Testing Steps:

- Brak: zmiana dotyczy samych testów; nie ma nic do sprawdzenia w PR env poza zielonym CI (kryterium 2.4).

## Performance Considerations

Pętle dokładają kilka wywołań MockMvc na trasę (11 tras admina, 3 + sonda `/internal` × 7 wartości nagłówka); bez nowego kontekstu Springa, więc czas CI rośnie o sekundy.

## Migration Notes

Brak: bez migracji, bez zmian w IaC i zmiennych.

## References

- Audyt: `context/changes/post-m3-verification-audit/research.md` §1 R-30
- Roadmapa: `context/foundation/roadmap.md`, S-40
- Wzór strażnika wyprowadzanego z kodu i przebiegów psujących: `context/archive/2026-11-06-derived-persistence-guards/plan.md`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java:56-110`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AdminRoleIT.java:64-79`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/web/MappedRoutes.java:34-89`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java:156-199,404-420`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunWithoutTokenIT.java:38-59`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java:69-121`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Trasy admina z mapowań

#### Automated

- [x] 1.1 Strażnik tras admina zielony (`AdminRoleIT`, `AuthorizationCompletenessIT`)
- [x] 1.2 Przebieg psujący: nowa trasa admina bez roli czerwieni `AdminRoleIT`, zmiana cofnięta
- [x] 1.3 Przebieg psujący: bean `RouterFunction` czerwieni `AuthorizationCompletenessIT`, zmiana cofnięta
- [x] 1.4 `ADMIN_ROUTES` zniknęło poza `context/` (`git grep`)
- [x] 1.5 Pełny `./mvnw -B verify` zielony

### Phase 2: Trasy `/internal` z mapowań

#### Automated

- [ ] 2.1 Strażnik tras `/internal` zielony (`InternalPipelineRunIT`, `InternalPipelineRunWithoutTokenIT`)
- [ ] 2.2 Przebieg psujący: token tylko pod `/internal/pipeline-runs/**` czerwieni oba IT na `/internal/probe`, zmiana cofnięta
- [ ] 2.3 Pełny `./mvnw -B verify` zielony
- [ ] 2.4 CI PR zielone (`gh pr checks`)
