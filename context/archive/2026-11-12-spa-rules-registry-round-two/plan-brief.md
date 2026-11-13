# S-42: rejestr reguł SPA, druga runda — Plan Brief

> Full plan: `context/changes/spa-rules-registry-round-two/plan.md`
> Research: `context/changes/post-m3-verification-audit/research.md` (audyt po M-3, §1 R-22, R-43, X5)

## What & Why

`rules.json` to rejestr reguł skopiowanych między API a SPA. Audyt po M-3 pokazał cztery krawędzie, przez które dryf przechodzi bez czerwieni:
- porównanie `err.detail === '<kod>'` z literówką (X5);
- kopie limitu rozmiaru strony;
- osiem enumów w odpowiedziach spoza rejestru;
- kopia warunku powodu `skipped` w `src/runs.ts` (R-22).

S-42 zamyka każdą z nich testem albo kompilatorem.

## Starting Point

- `ApiError.detail` ma typ `string`, a skan kodów zna trzy formy zapisu.
- Rejestr ma `runsPageMaxSize`, ale feed, nieudane i atrapa trzymają własne literały.
- `SpaRulesTest` ma ręczną mapę 19 enumów.
- Dzwonek dostaje przebieg bez `attentionReasons`, więc `runTone`/`runStatusLabel` liczą warunek `skipped` same.

## Desired End State

- Literówka w kodzie błędu nie kompiluje się i czerwieni `npm test`.
- Jeden limit `pageMaxSize`: jego obniżenie w API czerwieni każdy rozmiar strony SPA i atrapę.
- Nowy enum w odpowiedzi czytanej przez SPA czerwieni `SpaRulesTest`, dopóki nie trafi do rejestru, unii TS i sprawdzeń atrapy.
- Ton i status przebiegu (lista, strona, dzwonek) czytają kody powodów z API.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Strażnik X5 | Unia `ErrorCode \| null` na `ApiError.detail` + skan rozpoznający `=== / !==` i `toBe` | Kompilator blokuje deploy przez budowę obrazu, a skan daje ten sam sygnał w lokalnym `npm test`, bez zmiany CI. | Plan |
| `vue-tsc` w jobie `web-unit` | Nie | Budowa obrazu web już jest w `needs:` przed deployem, więc druga kopia typecheck niczego nie dodaje. | Plan (S-41 odłożył tę decyzję na S-42) |
| Kopia warunku `skipped` | `attentionReasons` w `NotifiedRun` (zmiana kontraktu powiadomień), `runs.ts` czyta kody | SPA nie liczy żadnego warunku, a każdy powód, jaki API da przebiegowi `succeeded`, dostaje ton `warning`. | Plan |
| Enumy spoza rejestru | 8 zbiorów do `values` + kompletność wyprowadzana z `ContractRegistry.SHAPES` w obie strony | Ręczna mapa przestaje być krawędzią: dziewiąty enum nie przejdzie po cichu. | Plan (audyt: „wyprowadzać zbiory z ręcznych list”) |
| Limit strony | `runsPageMaxSize` → `pageMaxSize` (`PageLimits.MAX_SIZE`), nazwane rozmiary SPA ≤ limitu, atrapa sprawdzana tekstowo | Wszystkie listy mają jedną granicę, a rozmiar SPA ponad nią to 400 w feedzie. | Plan |
| `Provider` | Ósmy zbiór (`provider`) z unią TS, choć SPA pokazuje go dosłownie | Wyprowadzony strażnik i tak go znajduje, a nazwany wyjątek byłby kolejną ręczną listą. | Plan |
| `PostStage` w TS | Pięć wartości jak w Javie, komentarz o trzech z listy nieudanych | Typ pola ma odpowiadać enumowi na kablu, a widok kompiluje się bez zmian. | Plan |
| Zbiory bez etykiet | Nazwana mapa `NO_LABEL` (zbiór → powód) w `rules.test.ts` | Wyjątek jest widoczny i uzasadniony, a nie zaszyty w filtrze. | Plan |

## Scope

**In scope:**
- unia `ErrorCode`, krotka `Exhaustive`, rozszerzony skan z testem każdej formy;
- `pageMaxSize`, `MAX_PAGE_SIZE`, `FEED_PAGE_SIZE`, `FAILED_PAGE_SIZE`, `RUNS_PAGE_SIZE`, literały atrapy i jej testu z `rules.json`;
- osiem zbiorów w `rules.json`, wyprowadzanie w `SpaRulesTest`, unie TS, ścieżki w `mockApi.test.ts`;
- `NotifiedRun.attentionReasons` (API, plik, próbka, typ, atrapa), `runs.ts` bez warunku;
- `REPOSITORY_GUIDELINES.md` i roadmapa.

**Out of scope:**
- `vue-tsc` w `npm test` / `web-unit`;
- etykiety nowych zbiorów i zmiany widoków;
- `needsAttention` w powiadomieniu;
- walidacja nieznanego `detail` w runtime;
- unie z AST;
- domyślne `size` po stronie API;
- atrapy narzędzi (S-43).

## Architecture / Approach

Każda krawędź dostaje strażnika według wzorca, który repo już ma:
- krotka `Exhaustive` + skan Vitest dla kodów błędów;
- nazwane stałe z testem granic i tekstowe sprawdzenie literału atrapy dla limitu;
- chodzenie refleksją po typach `SHAPES`, jak w `ContractVariantsTest`, dla enumów;
- pole kontraktu przypinane przez `NotificationsIT` i czytane przez `runs.ts` dla powodów.

Łańcuch enumów: enum Javy osiągalny z odpowiedzi → wpis w `SpaRulesTest.VALUES` i `rules.json` → krotka TS i ścieżka w `mockApi.test.ts`. Każde ogniwo czerwieni się, gdy brakuje następnego.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Kody błędów jako unia | X5 nie kompiluje się i czerwieni `npm test` | Testy z `new ApiError(…, '<dowolny tekst>')` przestaną się kompilować |
| 2. Jeden limit rozmiaru strony | `pageMaxSize` i nazwane rozmiary SPA w granicach | Atrapa bez importów zostaje z literałami, pilnowanymi tylko tekstowo |
| 3. Enumy z rejestru | 8 zbiorów + kompletność wyprowadzana w obie strony | `Document` koliduje z typem DOM (→ `QuoteDocument`); `PostStage` rośnie do 5 wartości |
| 4. Powody w powiadomieniu | Dzwonek i lista czytają kody API; dokumentacja; zielony PR | Zmiana kontraktu powiadomień dotyka pliku, próbki, typu, atrapy i fikstur testów naraz |

**Prerequisites:** S-41 i S-43 na `main` (są). Gałąź `feature/spa-rules-registry-round-two` z świeżego `origin/main`, issue S-42.
**Estimated effort:** ~1–2 sesje w 4 fazach; każda z nich to głównie testy i typy, a faza 4 to jedyna zmiana kontraktu API.

## Open Risks & Assumptions

- Ton `warning` dla przebiegu `succeeded` z jakimkolwiek kodem powodu to celowe uogólnienie. Dziś taki przebieg ma tylko kod `skipped`, więc widoczny wynik się nie zmienia.
- Skan kodów błędów to regex. Formę porównania, której nie zna, łapie dalej tylko kompilator w budowie obrazu.
- Kod błędu z nowszego API niż SPA jest rzutowany na `ErrorCode` na granicy i trafia do gałęzi domyślnej `switch`, jak dziś.

## Success Criteria (Summary)

- Powtórzone X5 jest czerwone w `npm test` i w `vue-tsc`.
- Obniżenie `pageMaxSize`, usunięcie enuma z rejestru albo nowy enum w odpowiedzi daje czerwony test z nazwą tego, czego brakuje.
- Dzwonek i lista przebiegów pokazują to samo co przed zmianą, ale z kodów API. Powrót liczenia `skipped` w SPA czerwieni `runs.test.ts`.
