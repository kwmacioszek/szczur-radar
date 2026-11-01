# Evidence: history

- Okno: cała historia, a6f9c23 (2026-09-26) .. 047b65d (2026-10-27). Kubełki: tydzień ISO W39–W44. W39 to tylko 2 dni (26–27.09), W44 też 2 dni (26–27.10).
- Policzone commity: 77 z 77. Wykluczone: 0 botów, 0 commitów agentów, 0 masowych. Merge wykluczone; historia to squashe PR-ów plus kilka bezpośrednich commitów docs.
- Szum (wg kontraktu): 946 zmian plików wykluczone (452 binarne, 480 nagrań LLM, 14 lockfile'i). Policzone: 2288 zmian plików, z tego kod 1265 i docs-planning 1023.
- Historia ścieżek: 120 zmian nazw (R) zwiniętych do nazwy z HEAD. Pliki `web/**` git zapisał jako D+A, nie jako rename; oba końce to shared-ui, więc atrybucja się nie zmienia.
- Liczenie wg kontraktu: commit liczy się raz dla każdej capability, której dotyka; co-change odrzuca commity dotykające > 8 z 16 capability.
- Surowe dane: `context/map/.work/history/` (TSV + skrypty w `scripts/`).

## Activity per capability

| capability | zmiany | % kodu | commity | zmiany „żywe” (ścieżka jest w HEAD) | commity „żywe” | martwa historia | `.md` w zmianach | commity z trailerem Claude |
|---|---|---|---|---|---|---|---|---|
| pipeline-runs | 146 | 11.5 | 14 | 146 | 14 | 0% | 9% | 14/14 |
| llm-providers | 145 | 11.5 | 11 | 139 | 11 | 4% | 6% | 11/11 |
| auth | 122 | 9.6 | 16 | 70 | 13 | **43%** | 0% | 15/16 |
| shared-foundations (kubełek) | 117 | 9.2 | 23 | 108 | 21 | 8% | 15% | 22/23 |
| platform | 115 | 9.1 | **32** | 101 | 31 | 12% | 4% | 28/32 |
| shared-ui (kubełek) | 110 | 8.7 | 17 | 68 | 15 | **38%** | 3% | 14/17 |
| assessment | 106 | 8.4 | 7 | 96 | 6 | 9% | 5% | 7/7 |
| accounts | 79 | 6.2 | 7 | 73 | 6 | 8% | 0% | 7/7 |
| feed | 71 | 5.6 | 11 | 71 | 11 | 0% | 0% | 11/11 |
| admin-console | 69 | 5.5 | 12 | 69 | 12 | 0% | 0% | 12/12 |
| ingest | 59 | 4.7 | 6 | 59 | 6 | 0% | 8% | 6/6 |
| model-evaluation | 37 | 2.9 | 6 | 37 | 6 | 0% | 14% | 6/6 |
| operator-alerts | 34 | 2.7 | 4 | 34 | 4 | 0% | 15% | 4/4 |
| daily-run | 33 | 2.6 | 6 | 33 | 6 | 0% | 18% | 6/6 |
| db-backup | 22 | 1.7 | 7 | 22 | 7 | 0% | 27% | 7/7 |
| docs-planning (kubełek) | 1023 | — | 76 | 967 | 76 | 5% | 79% | 71/76 |
| **unmapped** | **0** | **0%** | 0 | | | | | |

- `evidence` **Nieprzypisane: 0%.** Bez folda liczby zgadzają się co do sztuki z kontraktem. Fold przenosi 4 zmiany i 1 commit z auth do llm-providers (`test/config/LlmProviderConfigTest` → `test/llm/`) oraz 2 zmiany z assessment do shared-foundations (`agents/assessor/text/Whitespace` → `text/Whitespace`).
- `evidence` **Commity agentów: 0** (nie ma osobnej tożsamości agenta). Trailer `Co-authored-by: Claude` ma 72 z 77 commitów (94%). Bez trailera jest tylko 5 wczesnych commitów z W39–W40 (a6f9c23, 8763e5f, d8112e5, 111ad27, 197b740). Ten udział nie rozróżnia capability: w każdej wynosi ≥ 82%.
- `evidence` Ranking po zmianach „żywych”: pipeline-runs 146 > llm-providers 139 > shared-foundations 108 > platform 101 > assessment 96 > accounts 73 > feed 71 > **auth 70** > admin-console 69 > shared-ui 68. Auth spada z 3. na 8. miejsce, gdy odjąć Python API usunięte w F-03.
- `evidence` Platform ma najwięcej commitów (32, czyli 42% wszystkich), ale średni wolumen: każdy slice dotyka `railway.ts` lub `ci.yml`. Assessment to odwrotny przypadek: 7 commitów i 106 zmian, prawie wszystko z jednej kampanii F-02.

## Top files in top capabilities

Plik po foldzie występuje w commicie raz, więc dla każdego pliku zmiany = commity. †: pliku nie ma w HEAD.

| capability | najczęściej zmieniane pliki (commity) |
|---|---|
| pipeline-runs | `pipeline/CLAUDE.md` 13 (notatki agenta, proza) · `PipelineRunIT.java` 10 (do S-17 `PipelineAgentIT`) · `PipelineRunService.java` 6 · `assess/ConcurrentAssessmentDispatcherTest.java` 6 · `RunWatchdog.java` 5 · `assess/ConcurrentAssessmentDispatcher.java` 5 · `run/PipelineRunRepository.java` 5 |
| llm-providers | `llm/CLAUDE.md` 8 · `openrouter/OpenRouterClientTest.java` 8 · `openrouter/OpenRouterClient.java` 7 · `transport/TransportException.java` 6 · `LlmProviderConfigTest.java` 6 · `DeepSeekClientTest` 6 · `GeminiClientTest` 6 |
| auth | `AuthorizationCompletenessIT.java` 6 · web `router/index.ts` 6 + `index.test.ts` 6 · `szczur-radar-api/main.py`† 5 (Python) · `config/ErrorCodeTest.java` 5 · `config/SecurityConfig.java` 4 · `app/database.py`† 3 |
| platform | `.railway/railway.ts` **21** · `.github/workflows/ci.yml` **15** · `.gitignore` 7 · `deploy-verify.yml`† 2 (usunięty w #99) · `api/*`† po 2 (pierwszy scaffold) |
| assessment | `AssessorAgentIT.java`† 5 · `assessor/Assessor.java` 4 · `assessor/CLAUDE.md` 4 · `AssessorTest.java` 4 · `AssessorAgent.java`† 3 · `Provenance.java` 3 · `SkeletonAgent.java`† 2 (trzy ostatnie usunięte w S-17) |
| accounts | `CreateAdminCommandIT.java` 4 · `cli/CommandLine`, `CommandType`, `CreateAdminCommand`, `ExitCode`, `MigrateCommand`, `user/User` po 3 |
| feed | `FeedView.vue` 6 · `PostRepositoryIT` 5 · `PostSeeds` 5 · `api/posts.ts` 5 · `PostCard.vue` 5 · `labels.ts` 5 · `FeedView.test.ts` 5 |
| admin-console | `AdminView.test.ts` 7 · `PipelineRunController.java` 5 · `api/pipeline.ts` 5 · `AdminView.vue` 5 · `api/pipeline.test.ts` 4 · `FailedPostController` 3 · `NotificationsIT` 3 |
| ingest | `sources/CLAUDE.md` 5 · `WykopApiClient` 4 · `WykopRepository` 4 · `WykopApiClientTest` 4 · `WykopRepositoryTest` 4 · `LocalWykopServer` 3 |
| shared-foundations | `szczur-radar-api/README.md` **17** · `MigrationsIT` 7 · `pom.xml` 6 · `config/ErrorCode` 6 · `application.properties` 6 · `JpaSchemaIT` 6 · `pyproject.toml`† 5 |
| shared-ui | `dev/mock-api.js` **11** · `style.css` 7 · `KitchenSinkView.vue` 7 · `styleContract.test.ts` 6 · `AppHeader.vue` 4 |
| małe | daily-run: `tools/pipeline-trigger/README.md` 6, `test.sh` 5, `trigger.sh` 5 · db-backup: `README.md` 6, `backup.sh` 4, `test.sh` 4 · operator-alerts: `tools/healthchecks/README.md` 4, `test.sh` 3, `alerts/*` po 2 · model-evaluation: `GoldRecorder` 4, `LiveSmokeTest` 4 |

- `evidence` W 7 capability plik nr 1 to proza: `CLAUDE.md` w pipeline-runs, llm-providers i ingest, `README.md` w daily-run, db-backup, operator-alerts i shared-foundations. Notatki agenta w pakietach aktualizuje prawie każdy slice, który dotyka pakietu.
- `inference` Najgorętsze pliki kodu żywego to `PipelineRunIT` (10), `PipelineRunService` (6), `OpenRouterClient` (7), `railway.ts` (21), `ci.yml` (15), `mock-api.js` (11), `AdminView.test.ts` (7) i `MigrationsIT` (7).

## Trend

Reguły etykiet, w tej kolejności:
1. **seasonal**: ≥ 2/3 zmian capability w jednym tygodniu.
2. **rising**: ≥ 50% commitów w W43–W44. Dla porównania średnia dla całego repo to 26/77 = 34%.
3. **fading**: ≤ 17% commitów albo ≤ 10% zmian w W43–W44.
4. **constant**: wszystko inne.

| capability | commity W39 W40 W41 W42 W43 W44 | zmiany per tydzień | etykieta | kampanie (commit, zmiany) |
|---|---|---|---|---|
| pipeline-runs | 0 0 2 2 **9** 1 | 0,0,33,28,62,23 | **rising** | S-02 (1a33fcc, 32) · S-06 (5b6eabb, 24) · W43: S-08 9, S-09 12, S-10 8, S-11 12, S-18 12 · S-17 (e869a07, 23) |
| admin-console | 0 0 2 4 6 0 | 0,0,10,29,30,0 | **rising** | admin-panel-ui-hardening · S-06 (25) · S-08 (9), S-13 (9) |
| daily-run | 0 0 0 2 4 0 | 0,0,0,20,13,0 | **rising** (od W42) | S-05 (13e371a, 14) · S-08 · S-14 · F-04 ×2 |
| db-backup | 0 0 0 3 4 0 | 0,0,0,9,13,0 | **rising** (od W42) | F-01 (6) · S-15 (6) · F-04 (4) |
| llm-providers | 0 2 3 2 3 1 | 0,7,63,8,51,16 | constant (dwa zrywy) | F-02 (32), S-02 (27) · S-10 (24), S-09 (15), S-18 (12) · S-17 (16) |
| platform | 5 6 6 7 7 1 | 20,11,12,9,61,2 | constant | S-14 safe-deploy-gate (2abc64c, 44) |
| shared-foundations | 3 4 3 6 6 1 | 5,30,36,20,23,3 | constant | F-03 (21) · S-02 (29) · S-10 (14) |
| feed | 0 0 2 5 3 1 | 0,0,19,40,11,1 | constant | S-03 (16), S-04 (14) w W42 |
| accounts | 0 2 0 2 3 0 | 0,24,0,41,14,0 | constant | F-03 (21) · S-07 (54c4fc2, 37) · S-10 (10) |
| assessment | 0 1 2 1 2 1 | 0,2,**90**,1,10,3 | **seasonal** | F-02 (663c687, 79) |
| ingest | 0 0 2 0 3 1 | 0,0,**46**,0,12,1 | **seasonal** | S-02 (27) + F-02 (19) · drugi zryw S-16 (7) + poprawki (4) |
| model-evaluation | 0 0 2 0 4 0 | 0,0,**26**,0,11,0 | **seasonal** | F-02 (19) |
| operator-alerts | 0 0 0 0 4 0 | 0,0,0,0,**34**,0 | **seasonal** (nowa w W43) | F-04 (45d18c4, 21) · S-18 (8) |
| auth | 3 4 2 4 3 0 | 8,**78**,7,25,4,0 | **fading** | F-03 przepisanie na Javę (48) + S-01 (27) · w W43 już tylko 4 zmiany z cudzych slice'ów |
| shared-ui | 4 1 2 6 4 0 | **58**,10,15,21,6,0 | **fading** | scaffold w W39 · UI-hardening W41–W42 |
| docs-planning | 15 9 9 17 23 3 | — | constant (proza, poza rankingiem) | |
| *wszystkie commity* | 15 9 9 18 23 3 | | | |

- `evidence` Porównanie z poprzednią mapą (stan na 5b6eabb): 47 commitów do 5b6eabb włącznie, 30 po nim.
  - Po 5b6eabb wyraźnie przybyło: pipeline-runs 4 → **10**, operator-alerts 0 → 4, daily-run 2 → 4, ingest 2 → 4, model-evaluation 2 → 4, db-backup 3 → 4.
  - Wyraźnie ubyło: auth 13 → 3, shared-ui 13 → 4, platform 24 → 8, shared-foundations 16 → 7, feed 7 → 4.
- `inference` Po MVP fokus przeszedł z funkcji dla użytkownika (auth, UI, feed) na silnik przebiegów i niezawodność operacyjną. To kampania M-2 „MVP Quality & Reliability Hardening” (00aec93 → 047b65d).
- `unknown` Okno ma miesiąc, a skrajne tygodnie są częściowe. Etykiety opisują kampanie, nie długoterminowy kierunek.

## Fix pressure

**Heurystyka:**
- **(a) ścisła:** temat commita pasuje do `fix|bug|hotfix|revert|regression|broken|incident` (bez rozróżniania wielkości liter).
- **(b) czytana:** (a) plus follow-upy wskazane w kontrakcie, po przeczytaniu tematów: `poprawki z przeglądu`, `follow-up`, `openrouter-in-body-errors`. Odrzucone po przeczytaniu: S-09, S-10 i S-18 mają „błąd/błędy” w temacie, ale to funkcje obsługi błędów, nie poprawki.
- **(c) etykiety:** żaden PR nie ma etykiety, nie ma issue `bug`. Etykietę `deploy-health` ma 7 issue: #84–#88 i #90 (2026-10-19, PR env) oraz #98 (2026-10-22, produkcja), wszystkie zamknięte. Otwierał je workflow deploy-verify, czyli automat, a nie człowiek (reguła 7).
- **(d) przed squashem:** 468 commitów z 67 zmergowanych PR-ów (GraphQL, tylko nagłówki; żaden nie ma wyłącznie autora-agenta).
- Każdy commit liczy się dla każdej capability, której dotyka; potem przypisanie do capability, której naprawdę dotyczył (reguła 12).

**Main, 77 commitów:** heurystyka (a) daje 3 (3.9%), (b) daje 7 (9.1%). **Revertów: 0.** Spośród 7 commitów (b) cztery dotykają > 3 capability (646df78, 6bc15db, 3b86d99, 4753bc4), ale głównie przez docs-planning. Bez docs-planning przekrojowy jest tylko 6bc15db.

| capability | commity | (a) | (b) | udział (b) | dotyczył tej capability | spillover |
|---|---|---|---|---|---|---|
| auth | 16 | 2 | 3 | 19% | 1: 9ef707e (ścieżka filtra Origin, z impl-review) | 2: 646df78 (`main.py`†, Python), 6bc15db (`PasswordsTest`) |
| llm-providers | 11 | 0 | 2 | 18% | 1: 771b451 (błędy OpenRouter w treści odpowiedzi 200) | 1: 6bc15db |
| operator-alerts | 4 | 0 | 1 | 25% | 1: 3b86d99 (poprawki F-04 z impl-review) | 0 |
| ingest | 6 | 0 | 1 | 17% | 1: 4753bc4 (poprawki S-16 z przeglądu, Wykop client/repo) | 0 |
| platform | 32 | 2 | 3 | 9% | 2: 646df78 (bind Hypercorn pod healthcheck Railway), 427ebc3 (watchPatterns) | 1 |
| shared-foundations | 23 | 1 | 2 | 9% | 1: 6bc15db (czyste logi testów) | 1 |
| pipeline-runs | 14 | 0 | 2 | 14% | 0 | 2: 771b451 (test), 4753bc4 (`CLAUDE.md`) |
| daily-run / db-backup | 6 / 7 | 0 | 1 / 1 | 17% / 14% | 0 | 3b86d99: check-in F-04 żyje w `trigger.sh` / `backup.sh` |
| model-evaluation | 6 | 0 | 1 | 17% | 0 | 4753bc4 (`WykopPagingProbe`) |
| accounts, admin-console, assessment, feed, shared-ui | | 0 | 0 | 0% | | |

**Wewnątrz PR-ów, przed squashem** (`inference`: przypisanie po przeczytaniu nagłówków, plików nie pobierano):
- 73 nagłówki pasują do szerokiej heurystyki. 31 z nich (w 26 PR-ach) to „poprawki z przeglądu implementacji”: workflow uruchamia /10x-impl-review na każdym slice, więc to krok procesu, nie sygnał defektu (mechaniczne).
- Po odjęciu fałszywego trafienia `fixture'y` (#95), dwóch resztek docs i jednego commita narzędzi agenta zostaje **38 poprawek konkretnych defektów**:

| dotyczy | liczba | PR-y | przykłady |
|---|---|---|---|
| platform | **22** | #1 ×2, #2, #13 ×2, #21, #26 ×3, #34, #38, #44 ×2, #46, #50, #58, **#81 ×5**, #83 | IaC `preserve()` JWT_SECRET / DATABASE_URL, Postgres sleep, przypięcie portu i SDK Railway, gh/REST w deploy-verify, sprzątanie runnerów. W #81 jest **jedyny revert w historii**: cofnięcie tymczasowej zmiany crona |
| llm-providers | 3 | #37, #40 ×2 | błąd OpenRouter w odpowiedzi 200, błąd po częściowym wyjściu, pusta odpowiedź = provider_error |
| ingest | 2 | #37 ×2 | sekret Wykopu w przyczynie wyjątku, NUL w zapisie i wspólny typ elementu |
| accounts | 2 | #49, #83 | konto systemowe nie dostaje linku aktywacyjnego; izolacja `CreateAdminCommandIT` na trwałym runnerze |
| admin-console / shared-ui | 2 | #42, #80 | loading podczas odzyskiwania po błędzie; miejsce w kitchen sink (szlif UI) |
| assessment, auth, feed, daily-run, pipeline-runs, shared-foundations, S-18 | po 1 | #37, #13, #53, #60, #110, #27, #107 | dług F-02 · proxy Vite i nagłówek Host dla filtra Origin · powrót z posta · heartbeat crona · zatrzymany przebieg · logi testów · klucze poza logami i przyczyna 503 |

**Remediacja M-2** (`inference`):
- 13 commitów z kodem nosi tytuły F-04 i S-08…S-19 i powstało po audycie 00aec93 („audyt po MVP i roadmapa M-2”).
- Tematy nazywają usuwaną wadę: „pusty przebieg daje sygnał”, „awaria dostawcy LLM ani błąd naszego kodu nie zabija postów”, „utknięty przebieg sam wraca do obiegu”, „Wykop nie gubi postów po cichu”, „kopia bazy sprawdzana w całości”.
- Udział tych commitów per capability: **pipeline-runs 9/14**, admin-console 6/12, platform 6/32, shared-foundations 6/23, daily-run 4/6, db-backup 4/7, llm-providers 4/11, feed 4/11, shared-ui 4/17, operator-alerts 3/4, ingest 3/6, assessment 3/7, auth 3/16, accounts 2/7.

**Wniosek** (`inference`):
- Platform zdegradowana regułą 5: 22 z 38 poprawek w PR-ach i 2 z 7 na main to iteracja na IaC i CI.
- Po odjęciu spilloveru (reguła 12) realna presja defektów leży w **llm-providers** (1 na main + 3 w PR-ach; niespodzianki w kontrakcie zewnętrznego API) i w **ingest** (1 + 2, a S-16 sam jest remediacją).
- **pipeline-runs** niesie największą kampanię remediacji (9 z 14 commitów), choć ma mało bezpośrednich commitów `fix`.
- Presja w **auth** to głównie nieistniejący już Python plus jedna poprawka z przeglądu.

## Co-change between capabilities

- Zbiór co-change: 72 commity. Odrzucone (> 8 capability): 1a33fcc S-02 (12), 13e371a S-05 (11), 5b6eabb S-06 (10), fa37101 S-10 (10), 59d667c S-09 (9).
- docs-planning zmienia się razem z każdą capability z confidence 0.97–1.00, bo każdy slice commituje swój folder zmiany. To mechaniczne (reguły 3 i 8): pokazane tu raz i poza rankingiem.
- Confidence = commity wspólne ÷ commity mniej aktywnej strony.

**Pary biznesowe** (support ≥ 3 oraz pary istotne):

| para | support | commity a/b | confidence | support bez `*.md` | pliki napędzające | ocena |
|---|---|---|---|---|---|---|
| admin-console – pipeline-runs | 4 | 7/9 | 0.57 | 4 | `api/pipeline.ts`, `PipelineRunController` ↔ `PipelineRunService`, `PipelineRunIT` (S-08, S-11, S-13, S-18) | `inference` realna: model stanu przebiegu pokazany w /admin |
| admin-console – feed | 4 | 7/6 | 0.67 | 4 | `labels.ts`, `api/posts.ts`, `PostCard.vue` ↔ `AdminView.test.ts` | `inference` wspólne słownictwo web; UI-hardening |
| **ingest – pipeline-runs** | 4 | 5/9 | **0.80** | **1** (0.33) | w 3 z 4 commitów wspólne są tylko `sources/CLAUDE.md` ↔ `pipeline/CLAUDE.md`; kod Wykopu zmienił się razem z testem pipeline'u tylko w S-16 | `evidence` granica „nowe źródło = pozyskanie + mapowanie” trzyma się w historii; most `pipeline/step/SourceIngest.java` zmienia się po stronie pipeline-runs |
| assessment – llm-providers | 3 | 4/7 | 0.75 | 3 | `AssessorAgentIT`†, `AssessorAgent`† ↔ `LlmProviderProperties`, `LlmProviderConfigTest` | reguła 11: sprzężenie szło przez agenta Embabel, usuniętego w S-17 |
| accounts – auth | 3 | 5/12 | 0.60 | 3 | `cli/AdminBootstrap`, `CommandLine` ↔ `SecurityConfig`, `SessionCookieRenewalFilter`, `AuthorizationCompletenessIT` | realna para tożsamości; pilnuje jej IT (reguła 3) |
| llm-providers – pipeline-runs | 3 (+4 w mega-slice'ach) | 7/9 | 0.43 | 3 | `OpenRouterClientTest`, `TransportException` ↔ `PipelineRunIT`, `PipelineRunService` | realna zależność runtime; większość ukryta w S-02, S-06, S-09, S-10 |
| ingest – model-evaluation | 3 | 5/3 | 1.00 | 3 | `WykopApiClient` i `WykopRepository` ↔ `measurement/WykopPagingProbe` | sondy pomiarowe tylko w testach |
| daily-run – db-backup | 3 | 4/6 | 0.75 | 2 | `trigger.sh` ↔ `backup.sh` (F-04, F-04 domknięcie, S-14) | reguła 4, wspólna przyczyna: wrapper check-in (F-04) i `checkSuites` (S-14) na obu cronach |
| daily-run / db-backup – operator-alerts | 2 / 2 | | 0.50–0.67 | 2 / 2 | check-iny F-04 w narzędziach cronów | folder ≠ capability |
| admin-console – auth | 3 | 7/12 | 0.43 | 3 | `router/index.ts` | reguła 2: każdy nowy widok dopisuje trasę |

**Pary kubełków** (mechaniczne, reguły 3 i 4):
- platform – shared-foundations: support 15, confidence 0.83 (13 bez `.md`). Napędzają ją `ci.yml` (10) i `railway.ts` (10) po jednej stronie oraz `szczur-radar-api/README.md` (12) i `pom.xml` (5) po drugiej.
- accounts – platform i accounts – shared-foundations: 5/5 (1.00) każda. Komendy CLI są opisane w README i dotykają `ci.yml` i `railway.ts`.
- db-backup – platform: 5 (0.83). `railway.ts` deklaruje crona, a `ci.yml` uruchamia `db-backup-test`.
- shared-ui z admin-console 6 (0.86), z feed 5 (0.83) i z auth 7 (0.58). Napędzają je `dev/mock-api.js`, `KitchenSinkView.vue`, `style.css` i `styleContract.test.ts`: reguły UI każą dodać każdy stan do mocka, a każdy komponent na kitchen sink.

**Mega-slice'e** (`evidence`): w 5 odrzuconych commitach pipeline-runs, feed i admin-console występują razem we wszystkich pięciu; llm-providers z każdą z nich w czterech. `inference`: sprzężenie silnik ↔ widoki jest realne, ale ląduje w pionowych slice'ach, więc przefiltrowany co-change je zaniża.

## Hub files and freshness

Kolumna „commity” liczy commity z przefiltrowanego zbioru co-change; „partnerzy” to inne capability widziane razem z plikiem w co najmniej 2 commitach.

| plik | commity | partnerzy | co to jest | reguła | HEAD |
|---|---|---|---|---|---|
| `context/deployment/deploy-plan.md` | 44 | 15 | runbook każdego slice'a | 8 (proza) | jest |
| `context/foundation/roadmap.md` | 39 | 15 | roadmapa | 8 | jest |
| `REPOSITORY_GUIDELINES.md` | 26 | 15 | instrukcje agentów | 8 | jest |
| `docs/visuals/roadmap.svg` + `tools/render_roadmap_svg.py` | 17 + 17 | 13 | SVG regenerowany przy każdej zmianie roadmapy | 7/3 (lockstep regeneracji) | jest |
| `context/foundation/prd.md` | 13 | 13 | PRD | 8 | jest |
| `.railway/railway.ts` | 18 | 11 | IaC: usługi i zmienne | 4; pilnuje go plan + `gate.sh` (reguła 3) | jest |
| `.github/workflows/ci.yml` | 13 | 11 | joby CI | 4 | jest |
| `pipeline/CLAUDE.md` | 8 | 11 | notatki agenta silnika | 8 | jest |
| `tools/render_roadmap.swift` | 18 | 10 | dawny renderer roadmapy | 11 | **usunięty** |
| `szczur-radar-api/README.md` | 12 | 9 | komendy operatora, ustawienia | 8 | jest |
| `PipelineRunIT.java` | 6 | 8 | test integracyjny silnika (do S-17 `PipelineAgentIT`) | **nie mechaniczny**, realny hub kodu | jest |
| `PipelineRunService.java` | 4 | 8 | orkiestrator i kolejka przebiegów | **realny hub kodu** | jest |
| `szczur-radar-web/dev/mock-api.js` | 8 | 7 | ręczny mock REST API do zrzutów ekranu | lockstep bez znanego strażnika → unknown | jest |
| `router/index.ts` (+ test) | 4 | 7 | tabela tras i guard sesji | 2 (konfiguracja jako kod) | jest |
| `AuthorizationCompletenessIT.java` | 4 | 6 | test deny-by-default | 3 (pilnowane) | jest |
| `llm/transport/TransportException.java` | 4 | 6 | typ błędu transportu LLM (S-09, S-10, S-18) | `inference` realny wspólny typ klasyfikacji błędów | jest |
| `style.css` / `KitchenSinkView.vue` / `styleContract.test.ts` | 6 / 6 / 5 | 6 / 6 / 5 | tokeny, katalog, test kontraktu | 3 (pilnowane testem) | jest |
| `pom.xml`, `.gitignore` | 5, 7 | 6, 6 | zależności, ignore | 4 | jest |

**Świeżość** (`git cat-file -e HEAD:<path>` na 205 wymienionych plikach):
- Brakuje 28.
  - Usunięte: `.github/workflows/deploy-verify.yml` i `tools/deploy-gate/verify-deploy.sh` (#99); całe Python API (`api/**`, `szczur-radar-api/main.py`, `app/**`, `migrations/**`, `pyproject.toml`, `alembic.ini`, `.python-version`; F-03); `agents/SkeletonAgent.java`, `agents/assessor/AssessorAgent.java`, `AssessorAgentIT.java` (S-17); `tools/render_roadmap.swift`; zasoby szablonu Vite.
  - Przeniesione (zwinięte w tabelach): `PipelineAgentIT` → `PipelineRunIT`, `flow/PipelineAgent` → `flow/RunSequence`, `test/config/LlmProviderConfigTest` → `test/llm/`, `agents/assessor/text/Whitespace` → `text/Whitespace`.
- Udział martwej historii: auth 43%, shared-ui 38%, platform 12%, assessment 9%, accounts i shared-foundations po 8%, llm-providers 4%; pozostałe 0%.

## Mechanical signals

1. **docs-planning:** 45% wszystkich zmian, co-change ≈ 1.00 z każdą capability (reguła 8 + lockstep folderu zmiany). Poza rankingiem.
2. **Platform – liczba commitów i fixów:** `railway.ts` i `ci.yml` dotyka każdy slice (reguła 4). 22 z 38 poprawek w PR-ach i 2 z 7 na main to iteracja IaC/CI (reguła 5). Jedyny revert (#81) cofa tymczasową zmianę testową.
3. **Commity „poprawki z przeglądu”:** 31 w PR-ach. To krok procesu na prawie każdym PR, nie dowód defektu. Ich treść należy do dowodów dyskusji (`reviews/impl-review.md`).
4. **Spillover (reguła 12):** 3b86d99 zawyża daily-run i db-backup, 4753bc4 zawyża pipeline-runs i model-evaluation, 6bc15db zawyża auth i llm-providers, 646df78 zawyża auth przez usunięty `main.py`.
5. **Notatki agentów:** `CLAUDE.md` w pipeline (13), llm (8), sources (5) i agents/assessor (4) są plikami nr 1 swoich capability i same tworzą parę ingest – pipeline-runs (reguła 8).
6. **Martwa historia (reguła 11):** pozycja auth to w 43% Python usunięty w F-03; najczęściej zmieniany test assessment (`AssessorAgentIT`) i `AssessorAgent` usunięto w S-17.
7. **Pary kubełków** (README, CI, IaC, mock-api, kitchen sink): reguły 3 i 4.
8. **Jeden człowiek, trailer Claude na 94% commitów:** to nie jest sygnał koncentracji ani separacji (reguły 6 i 7).
9. **Fałszywe trafienia heurystyki:** `fixture'y` w #95; „błędy” w tematach funkcji S-09, S-10 i S-18.
10. **Issue `deploy-health`:** otwierał je automat (reguła 7).

## Unknowns

- **Ręcznie utrzymywane bliźniaki, których co-change nie sprawdzi:**
  - typy `szczur-radar-web/src/api/*.ts` wobec DTO i kontrolerów Javy (brak generowanego klienta): `posts.ts` ↔ `posts/api`, `pipeline.ts` i `notifications.ts` ↔ `PipelineRunController` i `notifications/api`, `freshness.ts` ↔ `FreshnessController`;
  - `dev/mock-api.js` wobec prawdziwego API (w historii nie widać strażnika);
  - nazwy zmiennych w `.railway/railway.ts` wobec ustawień Springa (`LLM_*`, `PIPELINE_*`, `HEALTHCHECK_PING_URL`);
  - harmonogram i grace w `tools/healthchecks/checks.sh` wobec cronów w `railway.ts` i `TRIGGER_TIMEOUT` / `BACKUP_TIMEOUT` (ponowny `apply` jest ręczny).
- **`tools/db-backup/tables.sh` wobec migracji:** strażnikiem jest `db-backup-test`. Od powstania `tables.sh` w S-15 migracje V10 i V11 nie dodały tabeli (`CREATE TABLE` = 0), więc strażnik nie zadziałał ani razu w historii.
- **Poprawki wewnątrz PR-ów:** pliki tych commitów nie zostały pobrane, więc przypisanie po nagłówkach to inference. Treść ustaleń z przeglądów należy do dowodów dyskusji.
- **Zewnętrzny konsument (reguła 10):** monitor po deployu przeniesiono do repo `szczur-radar-monitor` (#99); sonduje `/api/ready` i `/healthz`. Jego historii tu nie widać.
- **Incydenty produkcyjne poza gitem** (powiadomienia Railway, alarmy healthchecks/Telegram): nieznane. Nie wiadomo też, czy slice'e M-2 naprawiały defekty widziane na produkcji, czy planowy hardening; to dowody dyskusji (audyt 00aec93).
- Trend z miesiąca z częściowymi W39 i W44 ma niską pewność.
- **Narzędzia:** nic nie instalowano. `git log --follow` per plik zwinąłby rename `web/**` (bez wpływu na atrybucję).

## Commands

- `awk` na `.work/git-log.txt` (lista commitów, statusy A/M/D/R)
- `python3 -I .work/history/scripts/hist.py` (szum, fold renames, atrybucja TSV, aktywność, top pliki, trend, co-change, huby)
- `python3 -I .work/history/scripts/hist2.py` (presja fixów, udział `.md`, pary w mega-slice'ach)
- `python3 -I .work/history/scripts/hist3.py` (martwa historia, aktywność żywa wobec `git ls-tree -r HEAD`)
- `python3 -I .work/history/scripts/hist4.py` (pliki napędzające pary, co-change bez `.md`)
- `git -c core.quotepath=off cat-file -e HEAD:<path>` (świeżość 205 plików)
- `git -c core.quotepath=off log --diff-filter=A -- db/migration/` + `git show --name-only` (migracje wobec `tables.sh`); `grep -ci 'create table'` na V10 i V11
- `grep` na `context/foundation/roadmap.md` (skład M-2)
- `gh label list`; `gh pr list --json labels`; `gh issue list --label deploy-health`
- `gh api graphql` (commity zmergowanych PR-ów przed squashem → `.work/history/pr-commits.json`)
