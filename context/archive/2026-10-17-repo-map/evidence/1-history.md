# Evidence: history

- Okno: cała historia, `a6f9c23` (2026-09-26) .. `5b6eabb` (2026-10-17), HEAD `5b6eabb`; kubełki: tydzień ISO 2026-W39 .. W42 (commity/tydzień: 15, 9, 9, 14).
- Commity: 47 w oknie, 47 policzonych (0 botów, 0 autorstwa agenta, 0 masowych). Wszystkie PR-y squash-merge: **jeden commit = cała zmiana** (plan + kod + testy + dokumentacja), więc sygnały per commit mierzą zmiany, nie edycje.
- 1 człowiek (dwie tożsamości git). 42 z 47 commitów ma trailer `Co-authored-by: Claude` (evidence); kod pisał agent pod kierunkiem jednej osoby — to aktywność, nie osoba (false-signal 7).
- Wiersze plik×commit: 2475; policzone 1271 (bez szumu: `.claude/` i inne narzędzia agentów, fixture'y nagrań LLM/Wykop, wrapper Mavena, lockfile'y, binaria). Atrybucja: `context/map/.work/commit-files.tsv` (z `attribute.py`), bez własnych reguł.

## Activity per capability

`evidence`. Zmiany = wiersze plik×commit; commity liczone raz na zdolność. Udział liczony od zmian w kodzie (794 = 1271 − 477 prozy).

| capability | changes | % kodu | commits |
|---|---:|---:|---:|
| docs-planning (proza, osobno) | 477 | — | 46 |
| platform | 121 | 15.2% | 25 |
| auth | 88 | 11.1% | 10 |
| assessment | 88 | 11.1% | 4 |
| llm-providers | 71 | 8.9% | 7 |
| accounts | 65 | 8.2% | 4 |
| shared-ui | 62 | 7.8% | 13 |
| shared-foundations | 60 | 7.6% | 8 |
| pipeline-runs | 56 | 7.1% | 4 |
| feed | 55 | 6.9% | 8 |
| ingest | 42 | 5.3% | 2 |
| admin-console | 39 | 4.9% | 6 |
| model-evaluation | 22 | 2.8% | 2 |
| daily-run | 18 | 2.3% | 2 |
| db-backup | 7 | 0.9% | 3 |

- Unmapped: 0 zmian (evidence). Udział autorstwa agenta: 0% wg autora; 89% commitów (42/47) współtworzonych przez Claude (evidence).
- `inference`: liczba zmian ≠ liczba decyzji. `assessment` (88 zmian) to 4 commity, z czego 85 zmian w jednym tygodniu (F-02 `663c687`, narodziny kontraktu). `auth` ma 10 commitów, ale większość to dopiski do plików rejestracji tras (niżej: Hub files).

## Top files in top capabilities

`evidence` (liczba commitów, w których plik się zmienił; pełna lista: `.work/history-topfiles.tsv`).

| capability | top files (commits) |
|---|---|
| platform | `.railway/railway.ts` (16), `.github/workflows/ci.yml` (10), `.gitignore` (7), `szczur-radar-api/main.py` (5, **usunięty**) |
| auth | `szczur-radar-web/src/router/index.ts` (6), `router/index.test.ts` (6), `api/auth/AuthorizationCompletenessIT.java` (4), `views/LoginView.test.ts` (3) |
| shared-ui | `web/dev/mock-api.js` (7), `src/style.css` (7), `src/styleContract.test.ts` (6), `views/KitchenSinkView.vue` (5) |
| shared-foundations | `config/ErrorCode.java` (6), `JpaSchemaIT.java` (5), `MigrationsIT.java` (5), `config/ErrorCodeTest.java` (5) |
| feed | `views/FeedView.vue` (6), `components/PostCard.vue` (5), `views/FeedView.test.ts` (5), `posts/PostSeeds.java` (4) |
| llm-providers | `config/LlmProviderConfigTest.java` (4), `llm/openrouter/OpenRouterClient.java` (3), `OpenRouterClientTest.java` (3), `config/LlmProviderProperties.java` (3) |
| admin-console | `views/AdminView.test.ts` (4), `pipeline/api/PipelineRunController.java` (3), `web/src/api/pipeline.test.ts` (3), `views/AdminView.vue` (3) |
| pipeline-runs | `pipeline/PipelineStartupRecovery.java` (3), `PipelineAgentIT.java` (3), `PipelineRunService.java` (2), `assess/AssessmentDispatcher.java` (2) |
| accounts | `user/User.java` (3), `cli/CreateAdminCommandIT.java` (3), `cli/AdminBootstrap.java` (2), `cli/Command.java` (2) |
| assessment | `agents/assessor/Assessor.java`, `Prefilter.java`, `Provenance.java`, `ContractFixtures.java` (po 2) — plików wiele, każdy rzadko |
| ingest | `sources/wykop/WykopApiClient.java`, `WykopMapper.java`, `WykopRepository.java`, `WykopSearchResult.java` (po 2) |

## Trend

`evidence` (commity c / zmiany f na tydzień; `.work/history-trend.tsv`); etykiety i kampanie: `inference` z tematów commitów.

| capability | W39 | W40 | W41 | W42 | label | kampania |
|---|---|---|---|---|---|---|
| docs-planning | 15c/57f | 9c/141f | 9c/167f | 13c/112f | constant | plany/archiwum każdej zmiany |
| platform | 5c/53f | 6c/44f | 7c/15f | 7c/9f | constant (commity), fading (objętość) | W39 scaffold Railway, W40 F-03 + api-docker-image, potem drobne poprawki IaC |
| shared-ui | 4c/17f | 1c/5f | 2c/16f | 6c/24f | rising | W42: feed-view-ui-hardening, S-03, S-04, S-06 |
| auth | — | 4c/60f | 2c/6f | 4c/22f | constant | W40 S-01 + F-03 (przepisanie na Javę); dalej spillover tras |
| shared-foundations | — | 2c/12f | 1c/29f | 5c/19f | rising | każda zmiana z migracją/kodem błędu |
| feed | — | 1c/1f | 2c/17f | 5c/37f | rising | S-02 → S-03, S-04, feed hardening |
| llm-providers | — | 2c/7f | 3c/57f | 2c/7f | seasonal (szczyt W41) | F-02 + openrouter-in-body-errors |
| admin-console | — | — | 2c/10f | 4c/29f | rising | S-02, admin-panel-ui-hardening, S-06 |
| pipeline-runs | — | — | 2c/30f | 2c/26f | constant od narodzin (W41) | S-02 → S-05, S-06 |
| assessment | — | 1c/2f | 2c/85f | 1c/1f | fading | F-02 (kontrakt V2), potem tylko dotknięcia |
| accounts | — | 2c/24f | — | 2c/41f | seasonal | W40 S-01/F-03, W42 S-07 |
| ingest | — | — | 2c/42f | — | fading | F-02 + S-02 (Wykop) |
| model-evaluation | — | — | 2c/22f | — | fading | F-02 (gold sample, nagrania) |
| db-backup | — | — | — | 3c/7f | new (W42) | F-01 |
| daily-run | — | — | — | 2c/18f | new (W42) | S-05, S-06 |

Uwaga: historia ma 3 tygodnie — etykiety opisują fazy budowy, nie dojrzałe trendy (`inference`).

## Fix pressure

Heurystyka (`evidence`): temat commita pasuje do `fix|bug|hotfix|revert|regression|broken|incident`. Ponieważ każdy commit to squash całej zmiany, dodano **sygnał drugi**: linie `* fix(...)` w treści squasha (oryginalne commity przed scaleniem); `.work/history-body-fix.tsv`.

| capability | commits | fix (temat) | % | reverts | cross-cutting wśród fix |
|---|---:|---:|---:|---:|---:|
| platform | 25 | 2 (`646df78` Hypercorn dual-stack, `427ebc3` watchPatterns) | 8% | 0 | 0 |
| auth | 10 | 1 (`9ef707e` ścieżka filtra Origin, poprawki z przeglądu F-03) | 10% | 0 | 0 |
| docs-planning | 46 | 3 (te same commity) | 7% | 0 | 0 |
| pozostałe 12 | — | 0 | 0% | 0 | 0 |

- Reverts: **0** w całej historii (temat i treść; `git log --all | grep -i revert` pusty) — `evidence`.
- Sygnał drugi: 225 linii pod-commitów, z tego 36 `fix`, w tym 15 to „poprawki z przeglądu implementacji/fazy” (proces 10x-impl-review, nie błąd) — `evidence`. Pozostałe ~21 fixów przypisane do zdolności po treści (`inference`):

| obszar | fixy behawioralne w treści squashy | charakter |
|---|---|---|
| platform / IaC / deploy | ~13: port API i SDK w roocie (`ca4cc43`), `uv --no-dev` (`787a1c3`), DATABASE_URL/JWT_SECRET dla bramki IaC i retry proxy (`7a406f2`), ustawienia LLM w PR env (`253d171`), sleep Postgresa (`77fde0e`, `6bebf82`), db-backup bez Wait for CI (`b7ee254`), limit zasobów crona (`13e371a`), + 2 z tematów | trial-and-error konfiguracji Railway — **mechaniczne** (rule 5), choć 3 z nich wynikały z incydentów produkcyjnych (Postgres nie wstał po uśpieniu, merge docs przerwał przebieg) |
| llm-providers | 3–4: pusta odpowiedź = `provider_error` (`1a33fcc`), błędy OpenRouter w odpowiedzi 200 ×2 (`771b451` — cała zmiana to fix po utracie prób na produkcji), limit kosztów OpenRouter (`b7ee254`) | **realny błąd produkcyjny** — najsilniejszy sygnał naprawczy w kodzie |
| ingest | 2: sekret Wykop w przyczynie błędu, NUL w zapisie (`1a33fcc`) | wykryte w przeglądzie przed merge |
| assessment | dług F-02 F7/F8 (`1a33fcc`) + 4 rundy poprawek przeglądu (`663c687`) | intensywny przegląd, nie błąd produkcyjny |
| daily-run, accounts, feed, admin-console, shared-foundations | po 1: heartbeat crona, link dla konta systemowego, powrót z posta, loading przy odzyskaniu, czyste logi testów | drobne, przed merge |

- Cross-cutting: 3 commity tematyczne fix dotykają ≤ 2 zdolności — brak spilloveru (rule 12 nie ma zastosowania). Trzy największe commity (`1a33fcc` 12 zdolności, `13e371a` 11, `5b6eabb` 10) niosą fixy z treści do wszystkich dotkniętych zdolności — przypisano je wyżej do obszaru, którego dotyczyły.

## Co-change between capabilities

`evidence`. Reguła kontraktu: pominięte commity > 7 z 15 zdolności — **3 pominięte** (`1a33fcc` S-02, `13e371a` S-05, `5b6eabb` S-06). Support = wspólne commity; confidence = support ÷ commity mniej aktywnej strony (po pominięciu). Pełna tabela: `.work/history-cochange.tsv`.

| pair (kod) | support | confidence | uwaga |
|---|---:|---:|---|
| platform – shared-ui | 7 | 0.70 | głównie scaffold W39 (`a6f9c23`, `8763e5f`, `d8112e5`, `ca4cc43`) — historia bootstrapu |
| feed – shared-ui | 5 | 1.00 | każda zmiana feedu dotyka komponentów/tokenów lub `mock-api.js` |
| auth – platform | 5 | 0.71 | F-03, S-01, S-07 (sesja, Caddy, IaC) |
| auth – shared-foundations | 4 | 0.80 | `ErrorCode` + router/AuthorizationCompletenessIT w każdej nowej trasie |
| llm-providers – platform | 4 | 0.80 | zmienne `LLM_*` w `.railway/railway.ts` / CI |
| auth – shared-ui | 4 | 0.57 | |
| admin-console – feed | 3 | 1.00 | S-03, S-04, admin hardening (wspólne `PostCard`/`runs.ts`?) — `inference` |
| admin-console – shared-ui | 3 | 1.00 | |
| accounts – auth | 3 | 1.00 | S-01, F-03, S-07 |
| accounts – platform | 3 | 1.00 | |
| db-backup – platform | 2 | 1.00 | F-01 (IaC crona) |
| assessment – llm-providers | 2 | 0.67 | F-02, F-03 |

Proza: docs-planning współzmienia się z prawie wszystkim (np. z platform 21/22) — każda zmiana niesie plan i archiwum (rule 8), poza rankingiem.

## Hub files and freshness

`evidence` (partnerzy = inne zdolności kodu widziane z plikiem w ≥ 2 commitach; `.work/history-hubs.tsv`).

| file | partnerzy | commity | co to jest | ocena |
|---|---:|---:|---|---|
| `context/deployment/deploy-plan.md`, `context/foundation/roadmap.md`, `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`, `context/foundation/prd.md` | 12–14 | 11–30 | proza planistyczna | mechaniczne (rule 8) |
| `tools/render_roadmap.swift` | 14 | 20 | generator obrazu roadmapy, zmieniany razem z `roadmap.md` z reguły w wytycznych | mechaniczne (rule 3/7; brak strażnika w buildzie — `inference`) |
| `.railway/railway.ts` | 13 | 16 | IaC wszystkich usług | mechaniczne (rule 4: każda nowa zmienna/usługa go dotyka); jednak to plik, którego zły apply restartował produkcję (deploy-plan) |
| `.github/workflows/ci.yml` | 10 | 10 | CI | mechaniczne (rule 4) |
| `config/ErrorCode.java` + `ErrorCodeTest.java` | 9–10 | 5–6 | zamknięty zbiór kodów błędów | config as code, strzeżone testem (rule 2/3) |
| `MigrationsIT.java`, `JpaSchemaIT.java` | 10 | 5 | testy schematu (lista wersji migracji) | lockstep strzeżony buildem (rule 3) |
| `auth/AuthorizationCompletenessIT.java` | 9 | 4 | rejestr tras publicznych/admina | lockstep strzeżony testem (rule 3) — źródło sztucznej aktywności `auth` |
| `web/src/router/index.ts` (+ test) | 8 | 6 | tablica tras SPA | config as code (rule 2) — przypisany do `auth`, zawyża jego commity |
| `web/dev/mock-api.js` | 8 | 7 | ręcznie utrzymywana atrapa API do zrzutów UI | **niestrzeżony bliźniak** kontraktu API (`inference`): build nie wykrywa rozjazdu |
| `api/src/main/resources/application.properties` | 8 | 4 | wspólna konfiguracja Springa | mechaniczne |
| `pipeline/PipelineStartupRecovery.java` | 8 | 3 | odzyskiwanie przebiegów przy starcie | realny węzeł: zmieniany w S-02, S-05 (przed portem), S-06 (przyczyna przerwania) — `inference` |
| `pipeline/api/PipelineRunController.java`, `web/src/api/pipeline.test.ts` | 8 | 3 | API przebiegów admina | |
| `posts/PostSeeds.java` | 8 | 4 | dane testowe postów | test helper, mechaniczne |

- Freshness (`git cat-file -e HEAD:<path>` dla każdego nazwanego pliku): wszystkie istnieją poza **`szczur-radar-api/main.py`** (usunięty w F-03 `7a406f2`, era Pythona — historia, rule 11). Cały kod Pythona (`app/`, `tests/`, `migrations/`) zniknął w F-03; jego aktywność z W39–W40 opisuje przeszłość.

## Mechanical signals

- `platform` #1 w kodzie (25 commitów, 15.2%): wspólna przyczyna — każda zmiana dotyka IaC/CI (rule 4), a fixy to iteracja konfiguracji Railway (rule 5). Objętość spada z 53 do 9 zmian/tydzień.
- `auth` #2 (88 zmian, 10 commitów): aktywność głównie z przepisania F-03 (W40) i z plików rejestracji tras (`router/index.ts`, `AuthorizationCompletenessIT`) dotykanych przez każdą nową trasę (rule 2/3). Tylko 1 fix (temat).
- `assessment` (88 zmian): jedna kampania F-02 — rozmiar narodzin, nie churn.
- `shared-foundations`: `ErrorCode`, `MigrationsIT`, `JpaSchemaIT` — lockstep strzeżony testami (rule 3).
- Hub-y prozy i `tools/render_roadmap.swift` — rule 8/7.
- Koncentracja autorstwa: 1 człowiek = 100% — baseline (rule 6), nie ryzyko.
- „Poprawki z przeglądu” (15 linii fix w squashach) — proces przeglądu, nie błędy.

## Unknowns

- **Co-change zdolności rdzenia jest ślepy**: `pipeline-runs` (3 z 4 commitów), `daily-run` (2/2), `ingest` i `model-evaluation` (1/2) żyją głównie w pominiętych mega-commitach S-02/S-05/S-06. Brak par ≠ brak zależności — `unknown`; zależności rdzenia musi pokazać graf struktury.
- Granularność: squash ukrywa sekwencję edycji; dostępne tylko tematy pod-commitów w treści. Lepiej: `gh pr view --json commits` dla oryginalnych commitów (wymaga forge; nie instalowano niczego).
- Pary, które powinny zmieniać się razem, a historia tego nie pokaże: DTO backendu ↔ ręczne typy w `szczur-radar-web/src/api/*.ts` (brak generowanego klienta); kontrakt API ↔ `web/dev/mock-api.js`; odpowiedź `/internal/pipeline-runs/{id}` ↔ `tools/pipeline-trigger/trigger.sh` (testowany na atrapie API); zbiory `CHECK` w migracjach ↔ enumy (`EnumValueConverter`) — dla migracji↔encji strażnikiem jest `JpaSchemaIT`, dla pozostałych strażnik nieznany.
- Fix pressure w 3 tygodniach i 47 commitach to próbka zbyt mała na ranking; incydenty produkcyjne (flex/OpenRouter, Postgres po uśpieniu, merge docs przerywający przebieg) widać w tematach tylko pośrednio — szczegóły należą do agenta dyskusji/deploy-plan.
- Brak etykiet „bug” w issue — nie sprawdzano forge (poza zakresem historii).

## Commands

- `python3 -I <scratchpad>/hist.py context/map/.work` — czyta `commit-files.tsv`; pisze `.work/history-{activity,topfiles,trend,fix,commit-caps,cochange,hubs}.tsv`
- `git -c core.quotepath=off log --format=%h` + `git log -1 --format=%B <sha>` z `grep` linii `* fix(...)` / `revert` → `.work/history-body-fix.tsv`, `.work/history-body-fixlines.tsv`
- `git log --all --format='%h %s' | grep -i revert`
- `grep '^@' .work/git-log.txt | grep -ci claude` (trailery współautorstwa)
- `git cat-file -e HEAD:<path>` dla każdego nazwanego pliku
