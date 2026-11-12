# S-43: atrapy odpowiadają jak prawdziwe API — brief planu

> Full plan: `context/changes/contract-bound-mocks/plan.md`
> Research: `context/changes/contract-bound-mocks/research.md`

## What & Why

Atrapa API w SPA (`szczur-radar-web/dev/mock-api.js`), atrapa API crona (`tools/pipeline-trigger/test.sh`) i stuby API bramki deployu (`tools/deploy-gate/test.sh`) mają odpowiadać tak jak prawdziwe API, a rozjazd ma czerwienić test. M-5 postawi na atrapie SPA testy E2E (decyzja z 2026-11-04). Bez tego wycinka sprawdzałyby one atrapę, nie API.

## Starting Point

Klucze odpowiedzi atrapy SPA są dziś zgodne z plikami kontraktu z S-41, ale pilnują ich tylko dwa skany tekstu, więc zmiana nazwy pola przechodzi (eksperyment X4). Rozjeżdża się 12 zachowań i danych, m.in.:
- start przebiegu zawsze daje 409, logowanie nie istnieje;
- id nie są UUID, źródła to tylko Wykop;
- tryb anonimowy daje 401 tylko na `/me`, a nieudane próby mają puste naruszenia.

Atrapy narzędzi kopiują kształty API ręcznie, a ich trasy (`/internal/**`, `/api/deploy-safety`, `/api/ready`) nie mają plików kontraktu.

## Desired End State

- Vitest wykonuje atrapę SPA bez przeglądarki i na każdej trasie porównuje ją z plikami kontraktu (klucze, typy, enumy z `rules.json`) oraz z tabelą zachowań API (401/403/400/404/202/409/503 z kodami).
- Atrapa SPA ma sesję z rolami admin/user/anonim, logowanie, start 202, UUID, Reddita i walidację stronicowania. Jest więc gotową podłogą pod E2E w M-5.
- Atrapy narzędzi budują odpowiedzi z plików w `tools/<narzędzie>/fixtures/api/`, które przypinają testy integracyjne Javy, a `classify` crona jest sprawdzane wobec `rules.json`.

## Key Decisions Made

| Decyzja | Wybór | Dlaczego | Source |
| --- | --- | --- | --- |
| Powiązanie atrapy SPA | Test Vitest wykonuje tekst atrapy z `?raw`; atrapa zostaje skryptem `run-code` | `run-code` nie ma `require`/`import()`/`process`, więc atrapa nie przeczyta plików; procedura zrzutów i `/10x-ui` bez zmian | Plan (fakt: Research + sprawdzenie) |
| Ścisłość strażnika | Kształt ciał + tabela zachowań tras | Sam kształt łapie X4, ale nie zatrzyma powrotu 12 rozbieżności | Plan |
| Warianty atrapy spoza kontraktu | Muszą być w pliku kontraktu (dopisane przez IT), bez listy wyjątków | Każdy wariant, na którym stanie E2E, jest potwierdzony przez prawdziwe API; dziś żadnej luki typów nie ma | Plan |
| Identyfikatory | Stałe UUID, id bez UUID → 400 jak w API | Zgodność z API i realistyczne adresy w E2E | Plan |
| Poprawki danych | Wszystkie (naruszenia, czasy źródeł, `/raw` Reddita, `assessTotal` crona), z niezmiennikiem w teście tam, gdzie tanio | „Odpowiada jak API” obejmuje też dane | Plan |
| Dom kontraktu narzędzi | Pliki w `tools/<narzędzie>/fixtures/api/` przypięte przez IT (wzór `ping-urls.txt`) | Rejestr web zostaje tylko dla SPA, bez zmian w trzech strażnikach | Plan (wzór: Research) |

## Scope

**In scope:**
- strażnik atrapy SPA (`src/contract/mockApi.ts`, `mockApi.test.ts`);
- poprawki atrapy SPA i jej nagłówka;
- przeciążenie `ContractFiles.assertMatches(json, Path)`;
- pliki API crona i bramki z IT (`InternalPipelineRunIT`, `DeploySafetyIT`, `HealthIT`);
- atrapy `test.sh` z plików;
- `classify` i kody błędów wobec `rules.json`;
- `REPOSITORY_GUIDELINES.md`;
- renderer i SVG roadmapy.

**Out of scope:**
- przepisanie atrapy na ESM lub middleware Vite;
- odpowiadanie wyłącznie z plików;
- wspólny plik tras Java/TS;
- pliki narzędzi w rejestrze web;
- powody `deploy-safety` w `rules.json`;
- stub CLI `railway` i plany IaC;
- zmiany w działaniu `trigger.sh`;
- Playwright (M-5);
- weryfikacja w PR env.

## Architecture / Approach

Jeden wzór na trzy atrapy: prawda o kształcie jest w pliku, który test integracyjny Javy porównuje z prawdziwą odpowiedzią po zamaskowaniu UUID i instantów, a test atrapy czyta ten sam plik. Dla SPA pliki już istnieją (`src/contract/`). Strażnik wykonuje atrapę z podstawionymi `page`/`route` i sztucznymi zegarami, a tryby ustawia podmianą stałych w tekście. Dla narzędzi pliki powstają w `tools/<narzędzie>/fixtures/api/`; `test.sh` czyta je na hoście, przekazuje atrapie przez env i podstawia wartości zamaskowane. Zbiory wartości (statusy, kody błędów) sprawdza jq wobec `rules.json`.

## Phases at a Glance

| Faza | Co daje | Główne ryzyko |
| --- | --- | --- |
| 1. Strażnik kształtu atrapy SPA | Zmiana nazwy pola, typu albo wartości enumu w atrapie czerwieni `npm test`; każdy plik kontraktu ma trasę w atrapie | Reguła typów zbyt luźna (suma plików rekordu) albo zbyt ścisła dla rekordów zagnieżdżonych |
| 2. Atrapa SPA odpowiada jak API | 12 rozbieżności poprawionych, tabela zachowań i niezmienniki w strażniku, nowe tryby `AUTH`/`LOGIN`/`START` | Szeroki diff atrapy (UUID we wszystkich builderach); adresy do zrzutów się zmieniają |
| 3. Atrapa crona z kontraktu | 6 plików `/internal/**` przypiętych przez IT; atrapa z plików; `classify` wobec `rules.json` | Tekst podsumowania nocy w pliku wymaga stałych danych przypadku IT |
| 4. Stuby `deploy-gate` z kontraktu | 5 plików `deploy-safety`/`ready` przypiętych przez IT; stuby z plików | Kolizja z planami IaC w `fixtures/` (rozwiązana podkatalogiem `api/`) |

**Prerequisites:** S-41 (pliki kontraktu drugiej rundy) jest `done`. Docker do `./mvnw -B verify` i `tools/pipeline-trigger/test.sh`. `playwright-cli` do kryteriów 2.5–2.6.
**Estimated effort:** ~3–4 sesje: faza 2 jest największa, fazy 3–4 małe.

## Open Risks & Assumptions

- Restore z elementem bez UUID daje w API 400. To wniosek z obsługi błędów Jacksona, żaden test go nie przypina, więc atrapa i tabela przyjmują to założenie.
- Strażnik pobiera każdy przebieg i post po id. Gdyby był wolny, plan pozwala ograniczyć pobrania do próbki każdego rodzaju.
- Nowa trasa SPA, która używa istniejącego pliku kontraktu, nie czerwieni strażnika sama. Wymaga wpisu w tabeli tras razem z `ContractRegistry.SHAPES` (zapis w `REPOSITORY_GUIDELINES.md`).

## Success Criteria (Summary)

- Każde z celowych zepsuć (X4, typ, enum, start 409, puste naruszenia, klucz w pliku crona lub bramki, rozjazd `classify`) czerwieni test, a cofnięte daje zieleń.
- Atrapa SPA w przeglądarce renderuje `/admin`, przebieg, feed, post z Reddita, aktywację i przepływ logowania bez błędów w konsoli.
- CI (`npm test`, budowa obrazu web, `./mvnw -B verify`, testy obu narzędzi) zielone bez nowych jobów.
