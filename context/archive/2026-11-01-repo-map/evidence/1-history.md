# Evidence: history

- Okno: cała historia, a6f9c23 (2026-09-26) .. 5b4ac92 (2026-11-01). Kubełki: tydzień ISO W39–W44. W44 podzielony na granicy M-3: `W44p` to 2 dni przed M-3 (26–27.10), `W44m` to M-3 (27.10–1.11). W39 to tylko 2 aktywne dni.
- Policzone commity: 90 z 90 (77 przed e5ab295, 13 od e5ab295 włącznie). Wykluczone: 0 botów, 0 commitów agentów, 0 masowych. Merge commitów nie ma: każdy commit to squash jednej zmiany.
- Wiersze plików: 3652, policzone 2231 (kod 1349: przed 1129, M-3 220; proza 882: przed 743, M-3 139). Szum: 522 binarne, 884 vendored/fixture, 15 lockfile'i.
- `docs-planning` raportowane osobno, poza rankingami kodu. Atrybucja wyłącznie z `.work/commit-files.tsv`.
- Baseline: 1 człowiek w oknie, więc koncentracja nie jest ustaleniem (reguła 6).

**Rozbieżności z kontraktem (`evidence`):**
1. Trailer Claude ma 83/90 commitów (przed 72/77, M-3 11/13), nie 85. Dwa z 85 to trailery ludzkie (197b740, 111ad27).
2. Kontrakt mówi, że poprawki wewnątrz PR są w git niewidoczne. To częściowo nieprawda: treść squashy zachowuje 505 tematów pod-commitów (przed 418, M-3 87). Używam ich w sekcji „Fix pressure” jako drugiej warstwy.

## Activity per capability

| capability | zmiany: razem (przed / M-3) | commity: razem (przed / M-3) | % kodu przed | % kodu M-3 | udział M-3 w zmianach capability |
|---|---|---|---|---|---|
| pipeline-runs | 168 (126 / 42) | 19 (13 / 6) | 11.2 | 19.1 | 25% |
| platform | 165 (158 / 7) | 37 (35 / 2) | 14.0 | 3.2 | 4% |
| llm-providers | 131 (131 / **0**) | 11 (11 / 0) | 11.6 | 0.0 | 0% |
| shared-foundations (kubełek) | 110 (80 / 30) | 20 (14 / 6) | 7.1 | 13.6 | 27% |
| admin-console | 105 (71 / 34) | 17 (12 / 5) | 6.3 | 15.5 | 32% |
| assessment | 100 (100 / **0**) | 7 (7 / 0) | 8.9 | 0.0 | 0% |
| ingest | 99 (55 / 44) | 8 (6 / 2) | 4.9 | **20.0** | **44%** |
| auth | 93 (91 / 2) | 13 (12 / 1) | 8.1 | 0.9 | 2% |
| feed | 87 (65 / 22) | 14 (11 / 3) | 5.8 | 10.0 | 25% |
| accounts | 81 (79 / 2) | 8 (7 / 1) | 7.0 | 0.9 | 2% |
| shared-ui (kubełek) | 80 (70 / 10) | 21 (17 / 4) | 6.2 | 4.5 | 12% |
| model-evaluation | 42 (31 / 11) | 8 (6 / 2) | 2.7 | 5.0 | 26% |
| daily-run | 34 (27 / 7) | 9 (5 / 4) | 2.4 | 3.2 | 21% |
| operator-alerts | 34 (29 / 5) | 5 (4 / 1) | 2.6 | 2.3 | 15% |
| db-backup | 20 (16 / 4) | 9 (6 / 3) | 1.4 | 1.8 | 20% |
| docs-planning (proza) | 882 (743 / 139) | 89 (76 / 13) | — | — | 16% |
| **unmapped** | **0** | 0 | 0 | 0 | — |

Baseline M-3: 16.3% zmian kodu, 14.4% commitów.

- `evidence` **Nieprzypisane: 0.** Commity autorstwa agenta: 0 (nie ma osobnej tożsamości), trailer Claude 83/90 (92%). Bez trailera: 5b4ac92, d9171df (pusta treść), 197b740, 111ad27 i trzy startowe.
- `evidence` **M-3 przesunęło ciężar:** ingest, pipeline-runs, admin-console, shared-foundations i feed dały 172 z 220 zmian kodu (78%). Przed M-3 te same capability miały 397 z 1129 (35%).
- `evidence` **llm-providers i assessment: 0 zmian w M-3,** także przy drugim źródle. `resources/assessor/system-prompt.md` ostatnio zmienił się w 663c687 (2026-10-06, F-02).
- `evidence` **Udział testów w zmianach kodu:** przed 31.0% (350/1128), M-3 52.7% (116/220). Heurystyka: `/src/test/`, `*.test.ts`, `test.sh`.
- `evidence` **Pełne zmiany vs dokumentowe:** commity tylko-docs: przed 31/77, M-3 3/13. Przekrojowe (> 3 capability, z docs): przed 23/77 (30%), M-3 7/13 (54%).
- `evidence` **Kierunek S-27 (78c57ca):** 24 wiersze kodu poza `ingest` i `docs-planning`, z tego 2 produkcyjne (`pipeline/step/PostStore.java`, `posts/Post.java`), 2 IaC/CI (`ci.yml`, `railway.ts`), 20 to testy i ich dane. `inference`: to zgodne z NFR „nowe źródło zmienia tylko pozyskanie i mapowanie”, z wyjątkiem `posts.channel` w `Post`/`PostStore`. Koszt to fala poprawek w testach, nie w produkcji. Wcześniejsze S-20 (5a013ca) wzięło na siebie zmianę silnika: 40 wierszy poza ingest, z migracją V12.
- `unknown` Zmian kodu nie da się porównać 1:1 z poprzednią mapą: footprinty się zmieniły (np. auth 122 wtedy vs 91 przed M-3 teraz, platform 115 vs 158, bo era Pythona jest zwinięta w platform).

## Top files in top capabilities

Pliki poza `.md`, z foldem zmian nazw. Format `przed+M-3`. Plik występuje w commicie raz, więc zmiany = commity. †: nie ma go w 5b4ac92.

| capability | pliki |
|---|---|
| pipeline-runs | `PipelineRunIT.java` 10+4 (do S-17 `PipelineAgentIT`) · `ConcurrentAssessmentDispatcherTest.java` 6+2 · `PipelineRunService.java` 6+1 · `PipelineRunRepository.java` 5+2 · `RunWatchdogIT.java` 4+3 |
| platform | `railway.ts` 21+1 · `ci.yml` 15+2 · `.gitignore` 7+0 · `pom.xml` 6+0 · `main.py`† 5+0 · `pyproject.toml`† 5+0 |
| llm-providers | `OpenRouterClientTest.java` 8+0 · `OpenRouterClient.java` 7+0 · `TransportException.java` 6+0 · `LlmProviderConfigTest.java` 6+0 · `DeepSeekClientTest.java` 6+0 · `GeminiClientTest.java` 6+0 |
| shared-foundations | `MigrationsIT.java` 7+2 · `JpaSchemaIT.java` 6+2 · `ErrorCode.java` 6+1 · `application.properties` 6+0 · `ErrorCodeTest.java` 5+1 · `ClosedSetsIT.java` 0+3 |
| admin-console | `AdminView.test.ts` 7+2 · `PipelineRunController.java` 5+3 · `PipelineRunControllerIT.java` 3+5 · `api/pipeline.ts` 5+3 · `AdminView.vue` 5+2 |
| assessment | `AssessorAgentIT.java`† 5+0 · `Assessor.java` 4+0 · `AssessorTest.java` 4+0 · `AssessorAgent.java`† 3+0 · `Provenance.java` 3+0 |
| ingest | `WykopApiClient.java` 4+1 · `WykopApiClientTest.java` 4+1 · `WykopRepositoryTest.java` 4+1 · `SourceIngest.java` 3+1 · `WykopRepository.java` 4+0 · `Source.java` 1+2 |
| feed | `PostSeeds.java` 5+3 · `api/posts.ts` 5+2 · `FeedView.test.ts` 5+2 · `FeedView.vue` 6+1 · `PostRepositoryIT.java` 5+1 |
| shared-ui | `mock-api.js` 11+3 · `KitchenSinkView.vue` 7+3 · `labels.ts` 5+3 · `style.css` 7+0 · `styleContract.test.ts` 6+0 |

- `evidence` Najgorętsze pliki M-3: `PipelineRunControllerIT.java` 5, `PipelineRunIT.java` 4, po 3: `PostSeeds.java`, `mock-api.js`, `labels.ts`, `KitchenSinkView.vue`, `ClosedSetsIT.java`, `RunWatchdogIT.java`, `PipelineRunController.java`. Większość to testy i dane testowe.
- `evidence` Pliki Reddita (`sources/reddit`) mają po 1 zmianie: powstały w S-27, więc ich historia jest za krótka na ranking. Pozycja ingest w M-3 to świeży kod, nie poprawianie.

## Trend

Zmiany per kubełek. Reguły etykiet (te same co w poprzedniej mapie, w tej kolejności): **seasonal** gdy ≥ 2/3 zmian w jednym tygodniu; **rising** gdy ≥ 50% commitów w W43–W44 (średnia repo 39/90 = 43%); **fading** gdy ≤ 17% commitów albo ≤ 10% zmian w W43–W44; inaczej **constant**. Korekta ręczna ma powód w ostatniej kolumnie i jest `inference`.

Commity w kubełkach: W39 15, W40 9, W41 9, W42 18, W43 23, W44p 3, W44m 13.

| capability | W39 | W40 | W41 | W42 | W43 | W44p | W44m | reguła → etykieta | kampanie |
|---|---|---|---|---|---|---|---|---|---|
| pipeline-runs | 0 | 0 | 30 | 26 | 49 | 21 | 42 | **rising** | S-02 29, S-06 23, W43: S-09/S-11/S-18 po 11, S-17 21, M-3: S-20 18, S-26 9, S-27 9 |
| admin-console | 0 | 0 | 10 | 29 | 32 | 0 | 34 | **rising** | S-06 25, S-13 11, S-08 9, M-3: S-20 13, S-22 9, S-24 6 |
| ingest | 0 | 0 | 42 | 0 | 12 | 1 | 44 | **rising** | S-02 28, F-02 14, S-16 6, M-3: S-27 38, S-20 6. Pojedyncze kampanie, 86% zmian M-3 to jeden commit |
| shared-foundations | 0 | 13 | 31 | 19 | 16 | 1 | 30 | **rising** | S-02 30, S-10 13, M-3: S-24 14 + strażnicy S-20/S-27/S-29 |
| feed | 0 | 1 | 17 | 37 | 9 | 1 | 22 | **constant** | S-02 14, S-03 15, S-04 14, M-3: S-29 10, S-24 9 |
| shared-ui | 17 | 5 | 16 | 24 | 8 | 0 | 10 | **constant** | admin-panel-ui-hardening 13, S-03 7, S-06 7 |
| platform | 53 | 44 | 15 | 9 | 34 | 3 | 7 | **constant**, w M-3 spadek do 2 commitów | scaffold, F-03 22, S-01 15, S-14 15, M-3: S-21 5 |
| llm-providers | 0 | 6 | 55 | 7 | 48 | 15 | 0 | constant → **seasonal**: 4 impulsy kampanijne, potem cisza | F-02 27, S-02 25, S-10 23, S-09 14, S-18 11, S-17 15 |
| assessment | 0 | 2 | 86 | 1 | 9 | 2 | 0 | **seasonal** | F-02 76, S-02 10 |
| operator-alerts | 0 | 0 | 0 | 0 | 29 | 0 | 5 | **seasonal** | F-04 19, S-18 7, M-3: S-25 5 |
| model-evaluation | 0 | 0 | 22 | 0 | 9 | 0 | 11 | rising → **seasonal**: 3 kampanie | F-02 16, S-02 6, M-3: S-28 10 |
| daily-run | 0 | 0 | 0 | 18 | 9 | 0 | 7 | rising → **constant**: 4 commity M-3 to 1–3 pliki | S-05 13, S-06 5, S-29 3, S-25 2 |
| db-backup | 0 | 0 | 0 | 7 | 9 | 0 | 4 | rising → **constant**: lockstep z migracjami i S-25 | F-01 5, S-15 5 |
| accounts | 0 | 24 | 0 | 41 | 14 | 0 | 2 | rising → **fading**: 4/8 commitów, ale 16/81 zmian | F-03 21, S-07 37, S-10 10 |
| auth | 0 | 60 | 6 | 22 | 3 | 0 | 2 | **fading** | S-01 20, F-03 37 |
| docs-planning | 57 | 141 | 166 | 127 | 210 | 42 | 139 | **constant** | każda zmiana niesie plan i archiwum |

- `inference` Dwie fale: W41–W42 budowa produktu, W43 utwardzanie M-2 (23 commity), M-3 drugie źródło na utwardzonym rdzeniu. Silnik (`pipeline-runs`) rośnie przez wszystkie trzy, reszta rdzenia działa impulsami.

## Fix pressure

**Heurystyka z kontraktu:** temat pasuje do `fix|bug|hotfix|revert|regression|broken|incident` albo odwołanie do issue z etykietą bug. Issue z etykietą `bug`: 0 (`gh issue list --label bug --state all`). W tym repo jest za to 7 issue `deploy-health` (#84–#88, #90, #98): 6 z 19.10 dotyczy PR-envów i env `lag-test` przy budowie S-14, 1 to produkcja (#98, 22–23.10), wszystkie zamknięte w 0–1 dnia. Treść to sygnał zdrowia wdrożenia, nie capability, więc nie wchodzi do tabeli (`inference`: platform, reguła 5).

- `evidence` **Wynik: 3 z 90 commitów, 0 revertów, 0 w M-3, 0 przekrojowych:** 646df78 (platform, bind Hypercorna), 9ef707e (auth, ścieżka filtra Origin), 427ebc3 (platform, watchPatterns). Temat commita jest po polsku i opisuje slice, więc ta liczba to dolna granica, nie miara jakości.

**Druga warstwa, ta sama heurystyka na 505 tematach pod-commitów.** 71 trafień. Odrzucone: 1 fałszywe (`fixture'y`), 3 poprawki samych docs. Zostaje 67 wierszy: 32 z przeglądu, 33 z nazwanym defektem, 2 cofnięcia tymczasowych zmian weryfikacyjnych (2abc64c, f2974f2; żaden nie cofa regresji). Przypisanie do capability, której wiersz dotyczy, zrobione ręcznie (`.work/inner-fixes.tsv`, reguła 12), więc to `inference`.

| capability | commity | fix w temacie (przed / M-3) | commity przekrojowe (przed / M-3) | wiersze z przeglądu (przed / M-3) | nazwane defekty (przed / M-3) | cofnięcia (przed / M-3) | commity naprawcze po merge'u |
|---|---|---|---|---|---|---|---|
| platform | 37 | 2/35 · 0/2 | 16 / 1 | 3 / 1 | **15** / 1 | 1 / 1 | 6 |
| pipeline-runs | 19 | 0/13 · 0/6 | 12 / 5 | 5 / 2 | 1 / 1 | 0 | 0 |
| llm-providers | 11 | 0/11 · 0/0 | 10 / 0 | 1 / 0 | **5** / 0 | 0 | 1 |
| assessment | 7 | 0/7 · 0/0 | 7 / 0 | 4 / 0 | 1 / 0 | 0 | 0 |
| admin-console | 17 | 0/12 · 0/5 | 12 / 5 | 1 / 1 | 2 / 0 | 0 | 0 |
| feed | 14 | 0/11 · 0/3 | 10 / 3 | 3 / 0 | 1 / 0 | 0 | 0 |
| ingest | 8 | 0/6 · 0/2 | 5 / 2 | 3 / 1 | 0 / 0 | 0 | 1 |
| operator-alerts | 5 | 0/4 · 0/1 | 3 / 1 | 2 / 1 | 0 / 1 | 0 | 1 |
| accounts | 8 | 0/7 · 0/1 | 6 / 1 | 1 / 0 | 2 / 0 | 0 | 0 |
| daily-run | 9 | 0/5 · 0/4 | 5 / 4 | 1 / 0 | 2 / 0 | 0 | 1 |
| db-backup | 9 | 0/6 · 0/3 | 4 / 3 | 2 / 0 | 1 / 0 | 0 | 2 |
| auth | 13 | 1/12 · 0/1 | 11 / 1 | 0 / 0 | 0 / 0 | 0 | 2 |
| shared-foundations | 20 | 0/14 · 0/6 | 14 / 5 | 0 / 0 | 0 / 0 | 0 | 1 |
| shared-ui | 21 | 0/17 · 0/4 | 12 / 4 | 0 / 0 | 0 / 0 | 0 | 0 |
| model-evaluation | 8 | 0/6 · 0/2 | 5 / 1 | 0 / 0 | 0 / 0 | 0 | 1 |

Łącznie wiersze: przed 57, M-3 10. Zmiany z wierszem naprawczym: 36 (30 / 6), z wierszem z przeglądu: 27 (21 / 6). Commity przekrojowe: każda zmiana liczona jest dla wszystkich capability, które dotyka, więc kolumna nie wskazuje autora problemu.

- `evidence` **Commity naprawcze po merge'u: 9 przed M-3, 0 w M-3.** To 787a1c3, 6bc15db, 9ef707e, 253d171, 77fde0e, 6bebf82, 427ebc3, 4753bc4, 3b86d99 (lista z czytania tematów, sama klasyfikacja to `inference`). W M-3 każda poprawka z przeglądu weszła przed merge'em. ef0d33b (S-25) to tylko archiwizacja, bez kodu.
- `inference` **Realna presja napraw leży w llm-providers:** 5 nazwanych defektów na 11 commitów, wszystkie przed M-3 (błędy OpenRouter w odpowiedzi 200, błąd po częściowej odpowiedzi, pusta odpowiedź, limit kosztów, klucze w logach). To krawędzie zewnętrznego dostawcy. W M-3 tej capability nikt nie dotykał, więc nie wiemy, czy jest stabilna, czy tylko nietknięta.
- `inference` **pipeline-runs:** 9 wierszy, z tego 7 z przeglądu. Nazwane: fałszywy ERROR przy zatrzymaniu (S-17) i okna fałszywych alarmów (S-26). Sama ta capability jest najczęściej przeglądana, nie najczęściej psuta.
- `inference` Wiersze przeglądu to rytm pracy (`/10x-impl-review`), nie sygnał awarii. Świadczą o tym, że przegląd znajduje rzeczy, nie o tym, że capability jest zepsuta.

## Co-change between capabilities

Wariant główny, bez `docs-planning` i `*.md` (poza `system-prompt.md`): 56 commitów z kodem, 6 odrzuconych (> 7 z 15), użyte 50 (przed 41, M-3 9), z tego 36 wieloobszarowych. Wariant z docs (> 8 z 16) odrzuca te same 6 commitów, a `docs-planning` współwystępuje niemal ze wszystkim (pewność 0.97–1.00), więc nic nie mówi.

Odrzucone: 1a33fcc (S-02), 13e371a (S-05), 5b6eabb (S-06), fa37101 (S-10), 59d667c (S-09), **78c57ca (S-27)**. Największa zmiana M-3 nie wchodzi do par.

Format: wsparcie/pewność. Pewność = wspólne ÷ commity mniej aktywnej strony, w tym samym okresie. Pary M-3 opierają się na 1–5 commitach, więc są orientacyjne.

| para | razem | przed | M-3 | co je łączy (pliki z commitów wspólnych) | ocena |
|---|---|---|---|---|---|
| admin-console ↔ shared-ui | 9/0.82 | 6/0.86 | 3/0.75 | `mock-api.js`, `KitchenSinkView.vue`, `labels.ts` + `api/pipeline.ts`, `AdminView.test.ts` | workflow: UI + mock + kitchen sink |
| platform ↔ shared-ui | 8/0.47 | 8/0.62 | 0 | scaffold: 5 z 8 to a6f9c23, 8763e5f, d8112e5, ca4cc43, 9b9fbf8 | mechaniczne, przeszłość |
| feed ↔ shared-ui | 7/0.88 | 5/0.83 | 2/1.00 | `KitchenSinkView.vue`, `mock-api.js`, `labels.ts`, `styleContract.test.ts` | workflow |
| admin-console ↔ pipeline-runs | 7/0.64 | 4/0.57 | 3/0.75 | `api/pipeline.ts`, `runs.ts` ↔ `PipelineRun.java`, `PipelineRunService.java` | **realna zależność domenowa**: status i powody przebiegu po obu stronach |
| shared-foundations ↔ shared-ui | 7/0.50 | 4/0.44 | 3/0.75 | `ErrorCode.java`, `MigrationsIT.java`, `post.json` ↔ `mock-api.js`, `labels.ts` | kubełki, kontrakt S-24 |
| platform ↔ shared-foundations | 7/0.50 | 7/0.78 | 0 | `railway.ts`, `ci.yml`, `pom.xml` ↔ `application.properties`, `MigrationsIT.java` | wspólna przyczyna: F-03, F-02, S-17 |
| llm-providers ↔ platform | 6/0.86 | 6/0.86 | 0 | `ci.yml`, `pom.xml`, `railway.ts` ↔ `LlmProviderConfigTest.java`, `LlmConfigurationCheck.java` | ustawienia `LLM_*` i zależności, koniec w M-3 |
| auth ↔ platform | 6/0.60 | 6/0.67 | 0 | `ci.yml`, `railway.ts` ↔ `SecurityConfig.java` | przeszłość (F-03, S-01) |
| auth ↔ shared-ui | 6/0.60 | 5/0.56 | 1/1.00 | widoki i `router/index.ts` | kolejne widoki |
| admin-console ↔ shared-foundations | 6/0.55 | 3/0.43 | 3/0.75 | jak wyżej | kubełek, strażnicy kontraktu |
| pipeline-runs ↔ shared-foundations | 6/0.46 | 2/0.25 | **4/0.80** | `MigrationsIT.java`, `ClosedSetsIT.java` ↔ `PipelineRunIT.java`, `RunRecorder.java` | **wzrost w M-3**: nowe kolumny i zbiory zamknięte, korytarz pilnowany testami |
| pipeline-runs ↔ shared-ui | 6/0.46 | 3/0.38 | 3/0.75 | `labels.ts`, `mock-api.js` | pośrednio przez admin-console |
| accounts ↔ platform | 5/0.83 | 5/1.00 | 0 | `ci.yml`, `railway.ts` ↔ `CommandLine.java`, `CommandType.java` | przeszłość |
| feed ↔ shared-foundations | 5/0.62 | 3/0.50 | 2/1.00 | `post.json`, kontrakt | kontrakt S-24, S-29 |
| assessment ↔ shared-foundations | 4/1.00 | 4/1.00 | 0 | `application.properties`, `LlmProviderProperties.java` | mała próba |
| accounts ↔ auth | 4/0.67 | 3/0.60 | 1/1.00 | `AuthController.java`, `ActivateView.vue`, `CommandLine` | oczekiwana |
| ingest ↔ pipeline-runs | 4/0.67 | 3/0.60 | 1/1.00 | `SourceIngest.java`, `SourceFetcher` ↔ `RunRecorder.java`, `RunSequence.java` | **realna**, plus S-27 odrzucone |
| db-backup ↔ platform | 4/0.57 | 4/0.80 | 0 | `railway.ts`, `ci.yml` | config crona |

- `evidence` Wszystkich par: 82, ze wsparciem ≥ 4: 25.
- `evidence` **W M-3 platform wypadła z par:** jedyny użyty commit platform to S-21, który nie dotyka innych capability. S-27 dotyka `ci.yml` i `railway.ts`, ale jest odrzucone.
- `inference` Pary z platform to w większości historia lub konfiguracja. Żywe pary domenowe to admin-console ↔ pipeline-runs i ingest ↔ pipeline-runs.

## Hub files and freshness

Wariant główny. Partnerzy = capability inne niż własna, widziane w ≥ 2 commitach. „commity” to tylko commity użyte w co-change, więc mniej niż w tabeli plików.

| plik | partnerzy | commity (przed / M-3) | czym jest | reguły |
|---|---|---|---|---|
| `.railway/railway.ts` | 10 | 18 (18 / 0) | IaC: usługi i zmienne | 3, 4: każda nowa zmienna; `gate.sh` i plan głośno odrzucają dryf. Mechaniczne |
| `.github/workflows/ci.yml` | 10 | 14 (13 / 1) | jeden workflow CI | 4: każdy nowy check. Mechaniczne |
| `szczur-radar-web/dev/mock-api.js` | 8 | 11 (8 / 3) | ręczny mock API dla widoków | 3 częściowo: `rules.test.ts` skanuje tylko kody błędów. Kształt odpowiedzi nie ma widocznego strażnika (`unknown`) |
| `KitchenSinkView.vue` | 8 | 9 (6 / 3) | katalog komponentów, dev only | 3: reguła repo. Tylko proces |
| `pipeline/PipelineRunService.java` | 7 | 5 (4 / 1) | wejście przebiegów | **kod produkcyjny, nie mechaniczne** |
| `AdminView.test.ts` | 6 | 6 (4 / 2) | test widoku | szeroka powierzchnia widoku |
| `labels.ts` | 6 | 6 (3 / 3) | etykiety wartości | 3: `rules.json` + `rules.test.ts`, od S-24 |
| `router/index.ts` + `index.test.ts` | 6 | 4 (4 / 0) | trasy SPA | 3: nowy widok |
| `PipelineRunIT.java` | 6 | 4 (1 / 3) | test silnika | test, fala zmian schematu |
| `ErrorCode.java` + `ErrorCodeTest.java` | 6 | 4 (3 / 1) | zamknięty zbiór błędów | 3: `SpaRulesTest`, `rules.json` |
| `MigrationsIT.java` | 6 | 4 (3 / 1) | test migracji | 3: każda migracja |
| `PipelineRunRepository.java` | 5 | 5 (3 / 2) | zapisy i odczyty przebiegu | **kod produkcyjny** |
| `AuthorizationCompletenessIT.java` | 5 | 4 (4 / 0) | strażnik tras | 3: pada na każdej nowej trasie. Pożądane |
| `pipeline/state/RunRecorder.java` | 5 | 3 (2 / 1) | warunkowe zapisy stanu | **kod produkcyjny** |

Huby prozy (wariant z docs): `roadmap.md` 15 partnerów, 51 commitów (39 / 12); `deploy-plan.md` 15, 49 (44 / 5); `REPOSITORY_GUIDELINES.md` 15, 33 (26 / 7); `tools/render_roadmap_svg.py` 14, 29 (17 / 12). Reguły 7 i 8: dokumentacja zmian i regenerowany obraz roadmapy z reguły repo, nie ryzyko.

**Świeżość** (`git cat-file -e 5b4ac92:<path>`): 62 pliki z rankingów i hubów plus wszystkie nazwane w tym pliku. Nie ma w HEAD:
- `PipelineAgentIT.java`: przemianowany na `PipelineRunIT.java` w S-17 (R066), historia zwinięta.
- `AssessorAgentIT.java`, `AssessorAgent.java`: usunięte w S-17 (e869a07), reguła 11.
- `tools/render_roadmap.swift`: 21 zmian, usunięty, zastąpiony przez `render_roadmap_svg.py`.
- `szczur-radar-api/main.py`, `pyproject.toml`, `app/database.py`: era Pythona, usunięte w 7a406f2 (F-03).

Zmiany nazw przekraczające capability (24 poza `context/`, 4 przekraczają): `LlmProviderProperties.java` (shared-foundations → llm-providers, 3 wiersze), `Whitespace.java` i `WhitespaceTest.java` (assessment → shared-foundations, po 1), `HomeView.vue` → `AppHeader.vue` (feed → shared-ui, 1). Razem 6 wierszy, nieprzeliczone, bo kontrakt zabrania przypisywania na nowo.

## Mechanical signals

- **`platform` jako lider nazwanych defektów (16 z 33, reguła 5):** Railway, IaC, runnery, Docker, bind portu. Iteracja na infrastrukturze, zwłaszcza wokół scaffoldu, F-03 i S-14. Poza rankingiem ryzyka.
- **6 wierszy w 2abc64c (S-14), 5 w 1a33fcc (S-02), 4 w 663c687 (F-02) (reguła 12):** duże commity. Przypisane do capability, której dotyczyły, nie do wszystkich dotkniętych.
- **Commity przekrojowe:** to, że admin-console ma 17/17 przekrojowych, wynika z kształtu squashy, nie z architektury.
- **`railway.ts`, `ci.yml` jako huby (reguły 3, 4):** wspólna przyczyna „każda zmiana dotyka configu”. Liczyć raz.
- **`MigrationsIT`, `JpaSchemaIT`, `ClosedSetsIT`, `ErrorCode*`, `labels.ts`, `AuthorizationCompletenessIT`, `tools/db-backup/tables.sh` (reguła 3):** lockstep pilnowany buildem, korytarz, nie ukryty kontrakt.
- **Wzrost shared-foundations w M-3:** to dodawanie strażników (S-23, S-24) plus pakiet testów kontraktu. `inference`: dobra zmiana, nie presja na fundamenty. To też kubełek, więc jego pary są zawyżone.
- **`docs-planning` (reguła 8), `render_roadmap_svg.py` 13 z 13 commitów M-3 (reguła 7):** proza i regeneracja. `render_roadmap_svg.py` siedzi w `docs-planning`, mimo że to kod.
- **Pary platform ↔ shared-ui, auth ↔ platform, accounts ↔ platform (reguła 11):** w dużej części era scaffoldu i przepisania na Javę.
- **Koncentracja jednego autora (reguła 6):** baseline, nie ustalenie.

## Unknowns

- `unknown` **Bliźniaki, których co-change nie pokaże, bo współzmiana jest za rzadka lub wypada z liczenia:**
  - `dev/mock-api.js` ↔ odpowiedzi API. Skan obejmuje kody błędów, kształt odpowiedzi nie jest potwierdzony.
  - `assessment` ↔ `feed` (kontrakt V2 ↔ `PostAssessment.vue`): 2 użyte commity + 3 odrzucone, 0 w M-3. Czy `post.json` obejmuje wszystkie pola oceny: nie wiadomo z historii.
  - `feed` ↔ `ingest` (`posts.channel` ↔ pochodzenie posta): 1 użyty, plus odrzucone S-02 i S-27.
  - `ingest` ↔ `db-backup` i `ingest` ↔ `shared-foundations`: pojedyncze użyte commity, resztę kryje odrzucone S-27.
  - `assessment`/`llm-providers` ↔ `model-evaluation`: 1 użyty + 3 odrzucone.
  - `tools/healthchecks` ↔ czasy i harmonogramy crona (`TRIGGER_TIMEOUT`, `BACKUP_TIMEOUT`, `railway.ts`): reguła mówi o ręcznym `apply`, operator-alerts ↔ platform ma 3 commity. Strażnika w historii nie widać.
  - `trigger.sh` i fałszywe API w `test.sh` ↔ `InternalPipelineRunController`: oba po stronie daily-run, więc para ich nie pokaże.
- `unknown` **Co-change M-3 bez S-27:** największa zmiana M-3 jest odrzucona regułą kontraktu. Wiersze S-27 są podane w „Activity”, jej pary nie są policzone.
- `unknown` **Stabilność llm-providers i assessment:** 0 zmian w M-3 nie dowodzi poprawności dla Reddita. Treść po angielsku przechodzi przez prompt niezmieniony od F-02, a historia nie mówi, czy to dobrze (dotyczy tego S-28).
- `unknown` **S-29 (d9171df) i 5b4ac92 mają pustą treść,** więc pod-commity S-29 nie weszły do warstwy poprawek. Wczesne commity też mają ich mało.
- `unknown` **Poprawki przez amend lub force-push przed squashem** są niewidoczne.
- `unknown (external)` Repo `szczur-radar-monitor` i Cloudflare Worker (od 9ef690b tam siedzi weryfikacja po deployu) oraz ręczne zmienne Railway nie mają historii tutaj. Od tego commita issue `deploy-health` powstają w repo monitora, więc nowsze niezdrowe wdrożenia są poza tym skanem.
- `unknown` Okno kończy się w dniu włączenia Reddita, więc zachowania produkcyjnego nowego źródła historia nie pokazuje.
- Lepsze narzędzie nie było potrzebne, nic nie instalowano.

## Polecenia

- `git rev-list --abbrev=7 --abbrev-commit e5ab295^..5b4ac92` (granica M-3), `git log --format=%h…%b 5b4ac92` (`.work/bodies.txt`, trailery `.work/coauthors.tsv`), `git log --diff-filter=D … -- <pliki>`, `git cat-file -e 5b4ac92:<path>`.
- `gh label list`, `gh issue list --label bug --state all`, `gh issue list --label deploy-health --state all`.
- `python3 -I context/map/.work/history.py context/map/.work` oraz `history2.py`, `history3.py`, `history4.py`, `history5.py`: aktywność, trend, fix, co-change, huby, świeżość, pod-commity, fold nazw. Wynik w `.work/history*-output.txt`. Ręczne przypisanie pod-commitów: `.work/inner-fixes.tsv`.
