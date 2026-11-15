# Evidence: history

- Okno: cała historia, a6f9c23 (2026-09-26) .. ef56ddc (2026-11-15). Kubełki: tydzień ISO W39–W46 oraz kamienie (M-1 47 commitów, M-2 30, M-3 13, przed M-4 3, **M-4 19**). Główne porównanie: M-4 (po c8a6488) wobec wszystkiego wcześniej ("wcześniej" = 93 commity, 1380 zmian kodu).
- Policzone commity: 112 z 112 (0 botów, 0 agentów, 0 masowych, 0 merge). Trailer `Co-authored-by: Claude` ma 102/112 (M-4: 18/19; bez trailera w M-4: 5853642, S-40). Agent jest współautorem, nie autorem: liczone jako człowiek.
- Wiersze plików policzone: 2840 (kod 1719: M-1 798, M-2 362, M-3 220, M-4 339; proza `docs-planning` 1121). Szum odfiltrowany w kontrakcie (vendored, fixture, binary, lockfile, svg). Nieprzypisane: **0**.
- Baseline: 1 człowiek (3 tożsamości) — koncentracja nie jest ustaleniem (reguła 6). `docs-planning` poza rankingami.
- Zmiany liczone per plik, commity raz na capability (reguły z kontraktu). Kolumny "przed / M-4".
- Ograniczenie `evidence`: footprinty zmieniły się od poprzedniej mapy (nowe capability `regression-harness`, `quality-monitoring`; `dev/mock-api.js` i `src/contract/**` przeszły z shared-ui do regression-harness), więc liczby capability nie są 1:1 z archiwum — tam, gdzie porównuję, używam liczb z tej sesji dla obu okresów.

## Activity per capability

| capability | zmiany: razem (przed / M-4) | commity (przed / M-4) | % kodu przed | % kodu M-4 | udział M-4 w zmianach |
|---|---|---|---|---|---|
| pipeline-runs | 220 (179 / 41) | 30 (19 / 11) | 13.0 | 12.1 | 19% |
| platform | 185 (173 / 12) | 41 (37 / 4) | 12.5 | 3.5 | 6% |
| shared-foundations (kubełek) | 154 (130 / 24) | 34 (23 / 11) | 9.4 | 7.1 | 16% |
| admin-console | 145 (97 / 48) | 24 (17 / 7) | 7.0 | **14.2** | 33% |
| llm-providers | 124 (124 / **0**) | 10 (10 / 0) | 9.0 | 0.0 | 0% |
| feed | 116 (88 / 28) | 22 (13 / 9) | 6.4 | 8.3 | 24% |
| assessment | 109 (100 / 9) | 9 (7 / 2) | 7.2 | 2.7 | 8% |
| ingest | 107 (99 / 8) | 12 (8 / 4) | 7.2 | 2.4 | 7% |
| auth | 100 (92 / 8) | 17 (13 / 4) | 6.7 | 2.4 | 8% |
| accounts | 86 (81 / 5) | 11 (8 / 3) | 5.9 | 1.5 | 6% |
| **regression-harness** (nowe) | 79 (24 / 55) | 27 (15 / 12) | 1.7 | **16.2** | 70% |
| **operator-alerts** | 72 (34 / 38) | 10 (5 / 5) | 2.5 | **11.2** | 53% |
| shared-ui (kubełek) | 72 (65 / 7) | 22 (18 / 4) | 4.7 | 2.1 | 10% |
| daily-run | 55 (32 / 23) | 16 (9 / 7) | 2.3 | 6.8 | 42% |
| model-evaluation | 43 (42 / 1) | 9 (8 / 1) | 3.0 | 0.3 | 2% |
| **quality-monitoring** (nowe) | 31 (0 / 31) | 4 (0 / 4) | 0.0 | **9.1** | 100% |
| db-backup | 21 (20 / 1) | 10 (9 / 1) | 1.4 | 0.3 | 5% |
| docs-planning (proza, osobno) | 1121 (916 / 205) | 111 (92 / 19) | — | — | 18% |
| **nieprzypisane** | **0** | 0 | 0 | 0 | — |

- `evidence` Pięć capability w M-4 niesie 213 z 339 zmian kodu (63%): regression-harness 55, admin-console 48, pipeline-runs 41, operator-alerts 38, quality-monitoring 31. Wcześniej ten kształt był inny: pipeline-runs 13.0%, platform 12.5%, shared-foundations 9.4%, llm-providers 9.0%.
- `evidence` Tempo kodu jest takie samo: 1380 zmian w 41 dni (33.7/dzień) wcześniej, 339 w 10 dni (33.9/dzień) w M-4. Udział testów w zmianach kodu: przed 34.8% (480/1380), M-4 **55.5%** (188/339). Heurystyka: `/src/test/`, `*.test.ts`, `test.sh`, `/dev/`.
- `evidence` Commity tylko-docs: przed 37/93, M-4 2/19 (b57962b renderer roadmapy, ef56ddc zamknięcie). Przekrojowe (> 3 capability kodu): przed 24/93 (26%), M-4 **11/19 (58%)**, maks. 11 capability w jednym commicie (5baf6fa, S-36).
- `evidence` Agent: 0 commitów autorstwa agenta; trailer Claude 102/112. W M-4 bez trailera 1 z 19.
- `evidence` Nowe capability: quality-monitoring ma 4 commity (S-34 2deaf39, S-35 f2ee5fa, S-36 5baf6fa, S-45 ffbdf6e), regression-harness 12 w M-4 (S-30, S-31, S-33, S-35, S-36, S-37, S-40–S-45, S-44).
- `inference` Przesunięcie ciężaru z "budowy funkcji" (M-1: auth, accounts, assessment, llm-providers, platform = 59% zmian) na "utwardzanie i obserwowalność" (M-4: sieć regresji, alarmy, jakość, konsola admina) — zgodne z tematami M-4 (strażniki, kontrakty, mutacje, podsumowanie nocy).

## Top files in top capabilities

Pliki poza `.md`; format `przed+M-4` (plik jest w commicie raz, więc zmiany = commity). Ścieżki z `commit-files.tsv` (current path).

| capability | pliki |
|---|---|
| pipeline-runs | `ConcurrentAssessmentDispatcherTest.java` 8+2 · `PipelineRunRepository.java` 7+1 · `RunRecorder.java` 6+1 · `PipelineRunIT.java` 5+2 · `ConcurrentAssessmentDispatcher.java` 6+1 · `PipelineAgentIT.java`† 9+0 |
| platform | `railway.ts` 22+1 · `ci.yml` 17+2 · `pom.xml` 6+1 · `.gitignore` 7+0 · `pyproject.toml`† 5+0 |
| shared-foundations | `MigrationsIT.java` 9+3 · `ErrorCode.java` 7+1 · `JpaSchemaIT.java` 8+0 · `application.properties` 6+1 · `ClosedSetsIT.java` 3+2 |
| admin-console | `AdminView.test.ts` 9+5 · `AdminView.vue` 7+4 · `PipelineRunController.java` 8+3 · `PipelineRunControllerIT.java` 8+3 · `api/pipeline.ts` 8+2 |
| regression-harness | `dev/mock-api.js` 14+6 · `rules.test.ts` 2+5 · `samples.ts` 2+4 · `rules.json` 1+4 · `SpaRulesTest.java` 1+3 · `ContractRegistry.java` 0+3 |
| feed | `api/posts.ts` 7+4 · `FeedView.vue` 7+2 · `PostSeeds.java` 8+1 · `PostsApiIT.java` 6+3 · `PostRepositoryIT.java` 6+2 |
| quality-monitoring | `AssessmentQualityTest.java` 0+3 · `AssessmentQuality.java` 0+2 · `QualityStatus.java` 0+2 · `AssessmentQualityController(IT).java` 0+2 · `AssessmentQualityIT.java` 0+2 |
| operator-alerts | `NightSummary.java` + `NightSummaryIT/Test.java` 0+4 · `AlertEvent.java` 2+3 · `tools/healthchecks/test.sh` 4+1 · `checks.sh` 3+1 |
| daily-run | `InternalPipelineRunIT.java` 5+6 · `pipeline-trigger/test.sh` 6+5 · `trigger.sh` 6+3 · `nightly-summary.json` 0+2 |
| assessment (M-4 = 9) | `AssessorTest.java` 4+2 · `Assessor.java` 4+0 · `ProvenanceTest.java` 2+1 |

- † plik nie istnieje w HEAD (reguła 11): `pyproject.toml` (era Pythona, F-03), `PipelineAgentIT.java` i `AssessorAgent(IT).java` (era Embabel, przed S-17; następcy nie sprawdzałem — `unknown`). Poza tym wszystkie nazwane pliki istnieją (`git cat-file -e`).
- `evidence` Dwa pliki nowe i gorące w M-4: `NightSummary*` (4 commity każdy, S-34/35/36 + closeout) i `AssessmentQuality*` (S-35, S-36).

## Trend

Zmiany kodu w kamieniach (M-1 / M-2 / M-3 / M-4):

| capability | M-1 | M-2 | M-3 | M-4 | etykieta |
|---|---|---|---|---|---|
| pipeline-runs | 57 | 74 | 48 | 41 | **constant** (od M-1 gorący; M-4: S-32, S-38, S-39) |
| platform | 101 | 65 | 7 | 12 | **fading** (scaffold/IaC/CI z M-1/M-2; M-4: S-31, S-45 dotykają ci.yml) |
| shared-foundations | 94 | 20 | 16 | 24 | fading po M-1, w M-4 lekko w górę (migracje V14–V16, ClosedSetsIT, MigrationsIT) |
| admin-console | 39 | 29 | 29 | 48 | **rising** (S-33 przywracanie postów, S-32/S-34/S-35 w widokach) |
| llm-providers | 64 | 60 | 0 | 0 | **fading** — 0 zmian w M-3 i M-4 |
| feed | 54 | 10 | 24 | 28 | constant w M-3→M-4 (S-37, S-44, S-33, S-35) |
| assessment | 89 | 11 | 0 | 9 | fading; M-4 to S-36 (5baf6fa), nie kontrakt V2 |
| ingest | 42 | 13 | 44 | 8 | **seasonal** — kampania S-27/S-28 (Reddit) w M-3, w M-4 cicho |
| auth | 87 | 3 | 2 | 8 | fading |
| accounts | 65 | 14 | 2 | 5 | fading |
| regression-harness | 7 | 4 | 13 | 55 | **rising** (nowa; kampania S-30, S-31, S-41–S-45) |
| operator-alerts | 0 | 29 | 5 | 38 | **seasonal** (kampanie F-04/S-18 w M-2, S-34 w M-4) |
| shared-ui | 54 | 4 | 7 | 7 | fading |
| daily-run | 16 | 8 | 8 | 23 | rising w M-4, głównie przez `test.sh` i `nightly-summary.json` (S-34/35/36) — `inference` |
| model-evaluation | 22 | 9 | 11 | 1 | fading |
| quality-monitoring | 0 | 0 | 0 | 31 | nowa; kampania S-35→S-36 (11–13.11) |
| db-backup | 7 | 9 | 4 | 1 | fading (stabilna, rzadko dotykana) |

Tygodnie ISO (liczba commitów w tygodniu): W39 15, W40 9, W41 9, W42 18, W43 23, W44 17, **W45 7, W46 14**. W45 (2–8.11) ma 7 commitów, z czego 3 przed M-4 i 4 pierwsze slice'e M-4 (S-30, S-37, S-44, S-39); W46 (od 9.11) ma 14 commitów i największy wolumen zmian kodu: admin-console 45, regression-harness 48, operator-alerts 38, quality-monitoring 31 (zmiany liczone tygodniowo, nie na kamień).

- `evidence` Kampanie z tematów commitów: M-4 to 16 slice'ów S-30..S-45 w 10 dni; 12 z 19 commitów zapadło 10–13.11.

## Fix pressure

Heurystyka (`evidence` + oznaczona jako mechaniczna): commit liczy się jako "z poprawką", gdy temat lub punktor treści squasha (tematy pod-commitów) pasuje do `(?<![-\w])(fix|fixes|fixed|fixing|bug|bugs|hotfix|revert\w*|regression|broken|incident)(?![-\w])`. Słowo `fixture` i złożenia z myślnikiem (np. `regression-harness`, `feed-regression-and-...` w nazwie zmiany) są wykluczone, bo bez tego "fix" łapało nazwy plików.

| capability | commity przed | z poprawką (%) | commity M-4 | z poprawką (%) | cross-cutting wśród poprawek przed / M-4 |
|---|---|---|---|---|---|
| pipeline-runs | 19 | 15 (79) | 11 | 6 (55) | 13 / 5 |
| platform | 37 | 28 (76) | 4 | 2 (50) | 12 / 1 |
| shared-foundations | 23 | 18 (78) | 11 | 7 (64) | 15 / 5 |
| admin-console | 17 | 15 (88) | 7 | 6 (86) | 15 / 6 |
| feed | 13 | 11 (85) | 9 | 7 (78) | 10 / 7 |
| regression-harness | 15 | 12 (80) | 12 | 9 (75) | 11 / 7 |
| shared-ui | 18 | 12 (67) | 4 | 3 (75) | 10 / 3 |
| daily-run | 9 | 7 (78) | 7 | 4 (57) | 5 / 3 |
| operator-alerts | 5 | 4 (80) | 5 | 3 (60) | 1 / 3 |
| ingest | 8 | 7 (88) | 4 | 3 (75) | 6 / 3 |
| quality-monitoring | 0 | — | 4 | 3 (75) | — / 3 |
| inne (auth, accounts, assessment, llm-providers, db-backup, model-evaluation) | 55 | 41 (74) | 7 | 2 (29) | — |
| **razem commity z kodem** | 56 | 41 (73%) | 17 | 11 (65%) | 24 przekrojowe / 11 |

- `evidence` Rewertów: **0 w M-4**; w całej historii 2 punktory `revert(...)` i oba tymczasowe (2abc64c: cofnięcie tymczasowej zmiany crona w S-14, platform; f2974f2: cofnięcie tymczasowych zmian PR env w S-21, platform). Żaden nie dotyczy cofnięcia funkcji. Cross-cutting wśród nich: 2abc64c dotyka 2 capability, f2974f2 dotyka 1.
- `evidence` **Prawie cały "fix" to mechanika workflow (reguła 5, 12):** w M-4 10 z 11 commitów z poprawką to wyłącznie punktory "poprawki z przeglądu implementacji" (`/10x-impl-review` kończy każdą zmianę fazą poprawek), a wcześniej 13 z 41. Jedyny M-4 z poprawką innego rodzaju: S-44 `a6aa7aa` — granica numeru strony na listach, NUL i ogromny `page` w linku feedu (feed, `evidence`: realna usterka znaleziona testami regresji). Poza tym S-36 (NFC przed rozpoznaniem języka tezy, wiersz języka pod minimum) to poprawki z przeglądu własnej nowej funkcji.
- `evidence` Wcześniejsze wysokie wskaźniki w platform (28/37) to iteracja po CI/Railway/IaC (`fix(railway)`, `fix(iac)`, watchPatterns, Hypercorn) — reguła 5, nie ranking ryzyka.
- `inference` Presja poprawek nie rośnie w M-4 (65% vs 73% commitów z kodem); tam, gdzie rośnie względnie (feed 78%, admin-console 86%), to udział przeglądu przy dużych slice'ach, nie regresje.
- Spillover (reguła 12): 5baf6fa (S-36, 45 wierszy kodu, 11 capability), 2deaf39 (S-34, 39, 9), 7b263b0 (S-41, 36, 6), f2ee5fa (S-35, 35, 9), ffbdf6e (S-45, 35, 7) dotykają wielu małych capability; ich poprawki z przeglądu nie są usterkami capability, które musnęły. Dotyczy m.in. daily-run, db-backup i shared-ui w 5baf6fa; co dokładnie tam zmieniono, `unknown` (nie czytałem plików).

## Co-change between capabilities

Wariant główny, bez `docs-planning`. Wszystkie okresy: 73 commity z kodem, 69 po odrzuceniu 4 dotykających > 9 capability (1a33fcc S-02, 13e371a S-05, 5b6eabb S-06, 5baf6fa S-36). Tylko M-4: 17 commitów z kodem, 16 po odrzuceniu (S-36 5baf6fa). Pewność = wspólne ÷ commity mniej aktywnej strony.

| para | wsparcie (wszystko) | pewność | wsparcie M-4 | pewność M-4 |
|---|---|---|---|---|
| admin-console + regression-harness | 15 | 0.75 | 5 | 0.83 |
| feed + regression-harness | 14 | 0.78 | 7 | 0.88 |
| regression-harness + shared-ui | 14 | 0.74 | — | — |
| admin-console + feed | 13 | 0.72 | 6 | 1.00 |
| admin-console + pipeline-runs | 13 | 0.65 | — | — |
| pipeline-runs + shared-foundations | 15 | 0.58 | 7 | 0.70 |
| platform + shared-foundations | 15 | 0.50 | — | — |
| regression-harness + shared-foundations | 13 | 0.54 | 6 | 0.60 |
| daily-run + operator-alerts | 7 | 0.78 | 4 | 1.00 |
| daily-run + shared-foundations | — | — | 5 | 0.83 |
| auth + regression-harness | 9 | 0.64 | 4 | 1.00 |
| feed + quality-monitoring | 3 | 1.00 | — | — |

- `evidence` Kręgosłup co-change M-4 to regression-harness z feed, admin-console, auth, pipeline-runs i shared-foundations. `inference` (jedno wspólne źródło, reguła 4): to skutek S-41–S-43 i S-24, gdzie pliki kontraktu SPA ↔ API i atrapa API są wspólne dla widoków; i skutek przypisania w `capabilities.tsv` (`src/contract/post.json` → feed, `run.json` → admin-console, reszta kontraktu → regression-harness): ten sam commit zmieniający kontrakt dotyka z definicji trzech capability.
- `evidence` daily-run + operator-alerts (4 z 4 commitów operator-alerts w M-4): `nightly-summary.json` i `tools/pipeline-trigger/test.sh` w S-34/S-35/S-36 — wspólne źródło to jedno podsumowanie nocy.
- `evidence` Przed M-4 najsilniejsze pary to pipeline-runs/shared-foundations/platform/admin-console (budowa silnika i konsoli) — M-4 dokłada regression-harness do każdej z nich.

## Hub files and freshness

Pliki współzmieniane z największą liczbą różnych capability (partnerzy widziani w ≥ 2 commitach, po odrzuceniu commitów > 9 capability):

| plik (capability) | commity | partnerzy | co to jest / reguła |
|---|---|---|---|
| `MigrationsIT.java` (shared-foundations) | 8 | 15 | test schematu; każda migracja go dotyka — **mechaniczny** (reguła 3: build pada przy dryfie) |
| `.railway/railway.ts` (platform) | 20 | 14 | IaC; każda zmiana ustawień/usługi — **mechaniczny** (reguła 4, jeden właściciel infry) |
| `.github/workflows/ci.yml` (platform) | 17 | 14 | CI; każdy nowy test dopisuje job — **mechaniczny** (reguła 4) |
| `JpaSchemaIT.java` (shared-foundations) | 5 | 13 | walidacja encji względem schematu — **mechaniczny** (reguła 3) |
| `PipelineRunControllerIT.java` (admin-console) | 9 | 12 | test API admina; używa danych wielu capability — `inference` |
| `tools/pipeline-trigger/test.sh` (daily-run) | 8 | 12 | test crona z fałszywym API; w M-4 tkają go S-34/35/36 (kształt `nightly-summary`) — `inference`; **do sprawdzenia** czy zdublowana kopia kształtu |
| `szczur-radar-web/dev/mock-api.js` (regression-harness) | 17 | 11 | atrapa API SPA; od S-43 związana z kontraktem (reguła 3) — wcześniej ręczna kopia, 6 trafień w M-4 |
| `InternalPipelineRunIT.java` (daily-run) | 8 | 11 | test `/internal/**` |
| `PostSeeds.java` (feed) | 7 | 11 | pomocnik danych testowych — **mechaniczny** (fan-in z założenia, reguła 2) |
| `KitchenSinkView.vue` (shared-ui) | 11 | 9 | strona stanów komponentów; reguła zespołu ("nowy komponent na kitchen sink"), nie guard w buildzie — konwencja |

- Freshness: wszystkie dziesięć powyższych plików istnieje w HEAD (`git cat-file -e`), podobnie wszystkie pliki z tabeli top files poza trzema oznaczonymi †.
- `evidence` Wcześniejszy hub `PipelineAgentIT.java` (10 partnerów, 7 commitów) już nie istnieje w HEAD: opisuje przeszłość (reguła 11); nie liczyć go jako aktualnego ryzyka.

## Mechanical signals

- Wysoki odsetek commitów z poprawką we wszystkich capability: w M-4 10 z 11 to wyłącznie "poprawki z przeglądu implementacji" (faza workflow), więc nie diagnozują kruchości (reguła 5, 12).
- `railway.ts`, `ci.yml` jako huby: wspólne źródło (jedna osoba robi infrę, każdy nowy test dopisuje job) — policzone raz (reguła 4).
- `MigrationsIT`, `JpaSchemaIT`, `ClosedSetsIT`: lockstep migracji ze strażnikiem w buildzie (reguła 3).
- regression-harness jako partner wszystkich: pliki kontraktu przypisane do trzech capability w jednym commicie + nowa capability jest "zajęta, bo jest budowana" (reguła 4); nie czytać jako ukryty kontrakt.
- `docs-planning` (1121 wierszy, M-4 205): dyskusja o capability, nie aktywność kodu (reguła 8). Raportowana osobno.
- Zmiany kodu zdominowane przez testy (M-4 55.5% to testy) — wolumen zmian capability zawyża testy i `test.sh`, nie logikę produkcyjną (reguła 7 w sensie churnu bez decyzji; `inference`).
- Wolumen fixture (nagrane payloady) poza liczbą, zgodnie z kontraktem: nie raportowany tu per capability.

## Unknowns

- `unknown` Czy 4 wygaszone capability (llm-providers 0 zmian w M-3/M-4, auth, accounts, db-backup) są stabilne, czy tylko nietykane; sama historia git tego nie rozstrzyga (potrzebne: logi z produkcji, incydenty w issues — to robi agent discussion).
- `unknown` Pary, które powinny się zmieniać razem, ale nie widać tego w co-change: model prompta `assessment` ↔ kolumna `posts.language`/`QualityStatus` (S-36 zmienia oba, ale assessment dotknięty tylko 2 commitami w M-4); kształt `nightly-summary` w `pipeline-trigger/test.sh` ↔ `NightSummary` w API (kopia po stronie cron/shell — nie wiadomo, czy jest guard w buildzie poza testem tego skryptu); `docs/.../deploy-plan.md` ↔ `.railway/railway.ts` (proza vs IaC).
- `unknown` (external) Konsumenci poza repo: monitor `kwmacioszek/szczur-radar-monitor` (Cloudflare Worker + `verify.yml`) i ntfy/healthchecks.io — ich zmiany nie są w tej historii.
- `unknown` Następcy usuniętych `AssessorAgent(IT)` i `PipelineAgentIT` — niesprawdzone, bo nie czytam źródeł; historia pliku pod nową nazwą jest zwinięta tylko tam, gdzie `commit-files.tsv` zna ścieżkę (poprzedni kod: rename śledzony przez `-M` w logu).
- `unknown` Commit 5853642 (S-40) jest jedynym w M-4 bez trailera Claude; przyczyny historia nie podaje.
- Rekomendacja narzędzia (bez instalacji): tabela rename (`git log -M --name-status`) dołączona do `commit-files.tsv` zwinęłaby ścieżki z ery Embabel/Pythona; teraz ten fold jest niepełny.

## Komendy

- `git -c core.quotepath=off log --reverse --no-merges --format=... --date=short` (treści squashy do `.work/bodies.txt`, tematy i trailery)
- `git -c core.quotepath=off rev-list --count c8a6488..HEAD` (19 commitów M-4)
- `python3 -I context/map/.work/history_analysis.py` (aktywność, top pliki, trend, co-change, huby; wynik `.work/history_analysis.txt`; fix w nim jest zawyżony przez "fixture" — nieużywany)
- `python3 -I context/map/.work/history_fix.py` (fix pressure z poprawioną heurystyką, wynik `.work/history_fix.txt`)
- `git cat-file -e HEAD:<path>` dla plików z tabel top files i hubów
