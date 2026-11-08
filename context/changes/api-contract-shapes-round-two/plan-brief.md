# S-41: kontrakt SPA ↔ API, druga runda — kształty — Plan Brief

> Full plan: `context/changes/api-contract-shapes-round-two/plan.md`
> Research: `context/changes/post-m3-verification-audit/research.md` (audyt po M-3, §2 i X3)

## What & Why

Każdy kształt odpowiedzi, który czyta SPA, ma mieć plik kontraktu sprawdzany po obu stronach, z wariantami `null`. Pole, które API zawsze wysyła, ma być wymagane w TS. Audyt po M-3 pokazał dwie rzeczy:
- usunięcie `attentionReasons` z pliku i próbki (X3) przeszło przez 513 testów i `vue-tsc`, bo pole jest w TS opcjonalne;
- sześć kształtów (konto, aktywacja, powiadomienia, źródła, dni bez danych, koperty stron) nie ma żadnego powiązania z typami TS.

## Starting Point

S-24 dał cztery pliki (`run.json`, `post.json`, `failed-post.json`, `freshness.json`), maskowane porównanie w IT (`ContractFiles`) i typowane próbki w Vitest. Każdy plik to jeden zasiany przypadek, więc większość pól nullable jest w nim zawsze `null` albo zawsze niepusta. Jackson zawsze wysyła każdy klucz, a jedynymi `?:` w typach odpowiedzi są `Run.attentionReasons` i `Run.sourceResults`.

## Desired End State

- Każda trasa `/api/**` jest w rejestrze kontraktu z plikiem i typem Javy albo na liście wyjątków z powodem.
- Strażnik czyta `@Nullable` z rekordów Javy i wymaga w plikach obu wariantów każdego takiego pola oraz niepustej listy przynajmniej raz.
- Vitest wymaga próbki dla każdego pliku i odrzuca `?:` w typach odpowiedzi.
- Powtórzenie X3 jest czerwone.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kompletność wariantów `null` | Strażnik z `@Nullable` (`ContractVariantsTest`) + `KNOWN_GAPS` z powodem | Ręcznie zasiane warianty przepuszczą następne pole po cichu, czyli tę samą słabość, którą wskazał audyt | Plan |
| Nieosiągalne `run: null` w powiadomieniu | Plik z serializacji rekordu `JsonMapper` aplikacji w IT | Schemat V9 nie pozwala go zasiać, a roadmapa wymienia ten wariant wprost; API się nie zmienia | Research + Plan |
| Koperty stron | Pełne ciało strony na listę (`posts-`, `failed-posts-`, `runs-page.json`) z elementami niosącymi warianty | Koperta i warianty w jednym miejscu, dokładnie to, co czyta SPA | Plan |
| Pola opcjonalne w TS | Wymagane + skan `?:` w Vitest z listą dozwolonych i minimum trafień | Łapie w `npm test` lokalnie, nie tylko w buildzie obrazu web | Research + Plan |
| Kompletność tras | `ContractRoutesIT`: trasy `/api/**` = rejestr ∪ wyjątki | Bez tego nowa trasa bez pliku łamie „każdy kształt ma plik” po cichu | Plan |
| Dostęp testów `contract` do rekordów package-private | Nazwy binarne przez `Class.forName` w rejestrze; `routes()`/`Route` w publicznym helperze testowym `web/MappedRoutes` | Kod produkcyjny i konwencja pakietów bez zmian; zmiana nazwy klasy daje głośny błąd testu | Plan review (F1) |
| Zgodność z API sprzed S-20/S-22 | Usunięta (`?? []`, test „legacy”) | Produkcja ma oba od dawna, a Jackson nigdy nie pomija klucza | Research |

## Scope

**In scope:**
- pliki, IT i próbki dla: User, ActivationLookup, powiadomień (z `run: null`), SourceState, MissingDays, startu przebiegu i trzech stron;
- `shapes.test.ts` wyliczający pliki;
- wymagane `attentionReasons` i `sourceResults`, skan `?:`;
- `ContractRegistry`, `ContractVariantsTest`, `ContractRoutesIT`;
- wpis w `REPOSITORY_GUIDELINES.md`.

**Out of scope:**
- atrapy (S-43); `mock-api.js` już wysyła oba pola, które stają się wymagane;
- `err.detail`, limit strony, enumy spoza rejestru, warunek `skipped` (S-42);
- `vue-tsc` w `npm test`;
- zmiana zachowania API, `/internal/**`, odwrotny kierunek nullability, OpenAPI.

## Architecture / Approach

Mechanizm S-24 zostaje: złoty plik w `szczur-radar-web/src/contract/`, maskowane porównanie w IT, typowana próbka i `toEqual` w Vitest. Dochodzą trzy strażniki wyprowadzane z kodu, każdy z listą wyjątków z powodem i kontrolą, że nie przechodzi po cichu:
- **rejestr Javy** (trasa → pliki + typ) z IT wyliczającym trasy przez `AuthorizationCompletenessIT.routes`;
- **test wariantów**, który przechodzi rekordy przez refleksję (`@Nullable`, `List`, `@JsonProperty`) i zbiera wartości ze wszystkich plików;
- **wyliczanie plików** przez `import.meta.glob` po stronie SPA.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Nowe kształty po obu stronach | 7 nowych plików z IT i próbkami; plik bez próbki jest czerwony | Stałe wartości w ciałach (e-mail, daty) w testach, które dziś używają licznika |
| 2. Pełne strony z wariantami i wymagane pola TS | 3 strony z wariantami `null`, wymagane pola, skan `?:`, X3 czerwone | Izolacja danych stron (`total`) zgodnie z S-30; wiele plików testów SPA naraz |
| 3. Strażnicy wyprowadzani z kodu | Rejestr, strażnik wariantów, IT tras, reguła w wytycznych | Przejście refleksyjne po typach generycznych i rekordach package-private |

**Prerequisites:** Docker (Testcontainers, build obrazu web); gałąź `feature/api-contract-shapes-round-two` od świeżego `origin/main`; issue #151.
**Estimated effort:** ~2–3 sesje w 3 fazach; zmiany w testach API, typach i testach web, bez migracji.

## Open Risks & Assumptions

- Lista `KNOWN_GAPS` (`PostItem.assessment`, `LastAttempt.failureClass`, `NotifiedRun.finishedAt`) to przewidywanie. `provider`/`model` dostają wariant `null` w seedzie. Każdy wpis zostanie sprawdzony w implementacji, a wariant osiągalny będzie zasiany zamiast wyjątku.
- `import.meta.glob` w testach przechodzi `vue-tsc` w obrazie web (`types: ["vite/client"]` jest w `tsconfig.app.json`). Potwierdza to kryterium 2.4.
- Wariant `run: null` dowodzi serializacji rekordu, a nie przejścia kontrolera przez gałąź `NotificationController.java:64`.

## Success Criteria (Summary)

- Nowe pole, pole nullable bez wariantu, nowa trasa albo nowy plik bez drugiej strony dają czerwony test Javy albo Vitest.
- Powtórzenie X3 daje błąd `vue-tsc`, a przywrócenie `?:` czerwony `npm test`.
- Właściciel widzi w plikach stron realistyczne warianty, które czyta SPA.
