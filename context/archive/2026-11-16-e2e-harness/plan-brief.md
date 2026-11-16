# F-05: testy w przeglądarce w CI na atrapie API — Plan Brief

> Full plan: `context/changes/e2e-harness/plan.md`
> Research: `context/changes/post-m4-ui-readiness-audit/research.md` (audyt po M-4, sekcja „Podłoga E2E”)

## What & Why

Do repo wchodzą testy w przeglądarce (Playwright), uruchamiane w CI przy każdym PR na atrapie API zgodnej z kontraktem, której tryby ustawia każdy test. Do tego smoke obecnych przepływów. M-5 przebuduje cały interfejs (S-46–S-51), a testy widoków w Vitest mockują API, więc przepięcia układu i routera mogą przez nie przejść. F-05 to siatka regresji tej przebudowy i podstawa zrzutów porównawczych F-06.

## Starting Point

Atrapa `szczur-radar-web/dev/mock-api.js` (S-43) obsługuje wszystkie 20 tras czytanych przez SPA i jest pilnowana kontraktem. To jednak skrypt `playwright-cli run-code` z trybami wpisanymi w tekst: inny tryb wymaga kopii pliku, a stan resetuje się tylko ponowną instalacją. W repo nie ma `@playwright/test`, konfiguracji, testów ani joba z przeglądarką.

## Desired End State

`npm run e2e` buduje SPA, uruchamia `vite preview` i przechodzi 18 testów Chromium. Każdy test ma świeżą atrapę w swoich trybach (`test.use({ modes })`) i stały zegar `2026-10-20T10:00Z`. Job `web-e2e` w CI blokuje wdrożenie przy czerwonym teście, a `playwright-cli run-code` działa dalej, teraz także z dowolnym trybem jedną komendą. Kolejne wycinki dopisują testy przez `/10x-e2e`, bo `context/foundation/test-stack.md` opisuje stos.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Forma atrapy | ten sam plik jako `async (page, modes = {})`, walidowane tryby, uchwyt `endSession` / `dispose` | jedno źródło dla zrzutów `/10x-ui`, strażnika S-43 i E2E, bez podmiany tekstu regexem; `run-code` bez zmian | Plan (Research: odłożone przez S-43) |
| Serwer pod testem | `npm run build` + `vite preview` (port 4173, `E2E_PORT`) | testuje produkcyjny bundle, który serwuje Caddy; `/kitchen-sink` (tylko dev) to sprawa F-06 | Plan |
| Nowe warianty atrapy w F-05 | tylko wygaśnięcie sesji w trakcie pracy | pokrywa obecny strażnik sesji, który S-47 może zepsuć; reszta w wycinkach, które jej potrzebują (S-48, S-49, S-50) | Plan (Research: lista luk) |
| Zakres smoke | przepływy + stany dowodzące trybów, 18 testów | siatka regresji i wzór każdego rodzaju testu dla kolejnych wycinków, bez pełnej macierzy 7 stanów na widokach do przebudowy | Plan |
| Zegar | stały `FIXED_NOW` w atrapie (`now`) i przeglądarce (`page.clock.setFixedTime`) | atrapa działa w Node, SPA w przeglądarce; bez tego daty formularza admina i okna D-n zależą od północy | Plan |
| Bramka CI | job `web-e2e` w `needs:` joba `railway-config`, raport jako artefakt przy błędzie, `failOnFlakyTests` w CI | od razu prawdziwa siatka jak reszta CI; flaky test się naprawia, nie obchodzi | Plan |
| Środowisko CI | kontener `mcr.microsoft.com/playwright:v1.65.1-noble`, wersja równa `@playwright/test` | rezerwowa pula self-hosted nie ma sudo dla `--with-deps`; F-06 potrzebuje przypiętych czcionek | Plan |
| Sesja w testach | tryb `AUTH` atrapy, bez `storageState` | atrapa trzyma sesję sama; tylko testy logowania idą przez formularz | Plan |

## Scope

**In scope:**
- atrapa z trybami jako parametr, `now`, `endSession`, `dispose`, typ `MockModes` i przepięty strażnik S-43;
- Playwright Test 1.65.1: konfiguracja, fixture, `tsconfig.e2e.json` (typecheck przez `vue-tsc -b`), wyłączenie `e2e/` z Vitest, pliki ignorowane;
- seed `session-guard` oraz smoke `auth` (4), `activation` (1), `feed` (6), `post` (1), `admin` (5), każdy plik sprawdzony celowym zepsuciem (`e2e-breaks.md`);
- job `web-e2e`, wytyczne repo, `verification-scope.md`, `test-stack.md`.

**Out of scope:**
- zrzuty porównawcze, test dostępności, 375 px i drugi motyw (F-06);
- warianty atrapy poza wygaśnięciem sesji;
- pełna macierz 7 stanów;
- atrapa jako ESM, serwer albo middleware;
- inne przeglądarki;
- zmiany w widokach SPA i w API;
- poprawka F2 z S-43;
- weryfikacja w Railway PR env (serwowana aplikacja bez zmian);
- `test-plan.md`.

## Architecture / Approach

```
playwright test ──► webServer: npm run build && vite preview :4173
   │                              ▲
   ├─ fixture mock (Node): clock.setFixedTime(FIXED_NOW)
   │     eval(dev/mock-api.js)(page, {...modes, now}) ──► page.route('/api/*')
   │     test ◄── control { endSession, dispose }
   └─ spec: test.use({ modes }) → goto → getByRole/getByLabel → expect
strażnik Vitest: eval(dev/mock-api.js)(fakePage, modes) → kształty i zachowania vs src/contract/*.json
playwright-cli run-code: ten sam plik, tryby domyślne albo obiekt w komendzie
```

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Atrapa z trybami jako parametr | `mock-api.js` z parametrem trybów, `now`, `endSession`, `dispose`; strażnik i wytyczne przepięte | zepsucie procedury zrzutów `run-code` albo skanu literałów w `rules.test.ts` |
| 2. Infrastruktura Playwright i seed | konfiguracja, fixture, typecheck, izolacja od Vitest, seed na zimnym serwerze, `test-stack.md` | zawieszenia atrapy po zamknięciu strony, nieaktualny serwer reużyty lokalnie |
| 3. Smoke obecnych przepływów | 17 testów obok seeda, dowody celowego psucia, akceptacja właściciela | flaky testy przez polling `/admin` i dzwonka; lokatory w trybie strict |
| 4. CI i dokumentacja | job `web-e2e` w bramce wdrożenia, wytyczne, zielony PR | dłuższa bramka CI; rozjazd wersji obrazu i paczki |

**Prerequisites:** gałąź `feature/e2e-harness` od świeżego `origin/main` (`b050037`), issue #176; lokalnie Chromium Playwrighta (`npx playwright install chromium`) i `playwright-cli`.
**Estimated effort:** około 2–3 sesji w 4 fazach. Faza 3 jest największa.

## Open Risks & Assumptions

- `page.clock.setFixedTime` zostawia timery, więc polling `/admin` co 10 s chodzi w testach. `Date` stoi, więc dzwonek po pierwszym odczycie uznaje listę za świeżą i nie czyta jej ponownie; test, który tego potrzebuje, przesuwa czas kolejnym `setFixedTime`. Testy nie mogą zależeć od stanu, który polling zmienia. Sprawdza to bieg `--repeat-each=5 --workers=4`.
- `run-code` może wypisywać wartość zwróconą przez atrapę (obiekt `control`). Kryterium 1.4 sprawdza, że procedura dalej działa.
- Liczba postów pasujących do filtra `orlen` + `bullish` jest wyznaczana z danych atrapy przy implementacji, a nie zakładana w planie.
- Job w kontenerze pobiera obraz około 1–2 min przy zimnym runnerze. Szacowany czas joba to 3–5 min, krócej niż backend, więc bramka się nie wydłuża.

## Success Criteria (Summary)

- 18 testów w przeglądarce chodzi w CI przy każdym PR na produkcyjnym buildzie, a czerwony test zatrzymuje wdrożenie i zostawia raport.
- Każdy plik smoke czerwienieje przy celowym zepsuciu swojego przepływu i jest stabilny przy powtórzeniach.
- Kolejny wycinek M-5 dopisuje test przez `/10x-e2e`, ustawiając tryby atrapy w jednej linii, bez kopii pliku i bez logowania przez UI.
