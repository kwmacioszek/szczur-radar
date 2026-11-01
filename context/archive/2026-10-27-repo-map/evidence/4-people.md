# Evidence: people

- **Ludzie w oknie: 1** (`humans_in_window: 1`, okno a6f9c23 2026-09-26 .. 047b65d 2026-10-27, 77 commitów policzonych). `evidence`
- Jedna osoba pod trzema tożsamościami git (scalone; adresy e-mail celowo pominięte): `kwmacioszek` (adres A, 67 commitów, 2026-09-27..10-27 — squash merge PR-ów), `kwmacioszek` (adres B, 4, 2026-10-18..10-27 — bezpośrednie commity docs), `krzysztofmacioszek` (adres B, 6, wszystkie 2026-09-26 — bootstrap). Brak `.mailmap`. `evidence`
- Forge potwierdza: 72 PR-y, wszystkie autorstwa `kwmacioszek`, 67 scalonych przez niego, **0 review**; komentarze PR: `railway-app` 63 (status automatu, reguła 7 — nie review), `kwmacioszek` 11. 40 issues (wszystkie zamknięte): 38 od `kwmacioszek`, 2 od `github-actions` (etykieta `deploy-health`); komentarze: `kwmacioszek` 166, `github-actions` 5. Drugiego człowieka nie ma nigdzie. `evidence`
- **Baseline (reguła 6): mały zespół — koncentracja jest stanem wyjściowym.** Każda liczba „jedna osoba = 100%” poniżej to tautologia, nie ryzyko. Pytanie „kogo zapytać” ma jedną odpowiedź; użyteczna treść tego pliku to **gdzie czytać zamiast pytać** i **kto spoza repo trzyma wiedzę**.
- Filtry: reguły kontraktu (lockfile'y, binaria, nagrania LLM wyłączone; 0 botów, 0 commitów agentów, 0 masowych). Commity per capability zgodne z kontraktem 1:1; liczby zmian plików różnią się o ≤5 (obsługa renamów) — raportuję commity. Unmapped: 0. Trailer `Co-authored-by: kwmacioszek` (62×) to dopisek GitHuba przy squashu — ta sama osoba, nie agent.

## People per capability

Wszystkie capability: autor `kwmacioszek` (1 osoba). Kolumna „tematy” pochodzi z tematów commitów i dotykanych ścieżek; „skupione” = commity dotykające ≤2 capability kodu (bez `docs-planning`, które każdy PR niesie jako folder zmiany). `evidence` dla liczb, tematy to `inference` z tematów commitów.

| capability (kryt.) | commity / okres | skupione / >3 cap. | tematy pracy | PR-y do przeczytania |
|---|---|---|---|---|
| pipeline-runs (high) | 14 / 10-09..10-27 | 1 / 11 | kolejka i stan przebiegu, watchdog i samoleczenie, rozliczanie prób, pusty przebieg, dni bez danych, widoczność błędów, wyjście z Embabel | #37 S-02, #60 S-06, #96 S-09, #103 S-11, #107 S-18, #110 S-17 |
| llm-providers (high) | 10 / 10-02..10-27 | 1 / 9 | klienci dostawców i transport, błędy w treści odpowiedzi 200, fail-fast `LLM_*`, Spring AI wprost | #40 openrouter-in-body-errors, #82 S-10, #96 S-09, #110 S-17 |
| assessment (high) | 7 / 10-02..10-27 | 0 / 7 | kontrakt V2 jako kod (schemat, parser, walidator), ocena w filtrze feedu | #31 F-02, #54 S-04 |
| ingest (high) | 6 / 10-06..10-27 | 0 / 4 | fetcher Wykopu, stronicowanie, kompletność pobierania, pusty przebieg | #94 S-16, #104 (poprawki z przeglądu), #80 S-08 |
| feed (high) | 11 / 10-09..10-27 | 1 / 10 | feed i widok posta, filtr q/kind/direction, hardening UI | #53 S-03, #54 S-04, #48 feed-view-ui-hardening |
| auth (high) | 17 / 09-26..10-25 | 1 / 14 | logowanie i sesje (Python → Java), filtr Origin, deny-by-default; każda nowa trasa dopisuje się do `AuthorizationCompletenessIT` i routera | #13 S-01, #26 F-03, #28 (poprawki przeglądu: Origin) |
| accounts (high) | 7 / 09-29..10-23 | 0 / 6 | komendy CLI operatora, link aktywacyjny, reset hasła | #13 S-01, #27, #49 S-07 |
| db-backup (high) | 7 / 10-12..10-24 | 2 / 4 | `pg_dump`, pełny restore check, retencja, check-in crona | #44 F-01, #46, #79 S-15 |
| platform (high) | 32 / 09-26..10-27 | 15 / 14 | Railway IaC i limity, CI i self-hosted runnery, bramka wdrożeń, monitor zewnętrzny, obrazy Docker | #81 S-14, #83, #99, #52, #58, #33 |
| admin-console (medium) | 12 / 10-09..10-25 | 0 / 12 | historia przebiegów i dzwonek, nieudane posty i ponowienia, dni bez danych, hardening UI | #42, #60 S-06, #106 S-13 |
| daily-run (medium) | 6 / 10-13..10-24 | 0 / 5 | cron z tokenem, etykieta świeżości, strażnik wdrożeń, check-in | #50 S-05 |
| operator-alerts (medium) | 4 / 10-23..10-25 | 1 / 2 | healthchecks.io → Telegram, `OperatorAlerts`, zdarzenia błędów | #95 F-04, #101, #107 S-18 |
| shared-foundations (bucket, top 5) | 23 / 09-26..10-27 | 2 / 18 | README API, `MigrationsIT`, `pom.xml` — przelew z dużych squashy (reguła 12) | — |
| shared-ui (bucket, top 5) | 17 / 09-26..10-25 | 3 / 14 | `dev/mock-api.js`, kitchen sink, `style.css` — przelew z dużych squashy | #42, #48 |

`model-evaluation` (low, 6 commitów, 0 skupionych) i `docs-planning` (prosa, 76 commitów, reguła 8) pominięte w rankingu.

## Concentration

- **Mały zespół — koncentracja jest stanem wyjściowym.** Wszystkie 16 capability dotknęła wyłącznie jedna osoba; lista „tylko jedna osoba kiedykolwiek” = wszystkie. Bez języka bus-factor. `evidence`
- Ryzyko odpowiadające bus-factorowi przenosi się tu z ludzi na **dokumenty**: wiedza nieobecna w prozie istnieje tylko w głowie jednej osoby albo w kontach zewnętrznych (patrz Implications). `inference`

## Agent co-authorship

Kontekst, nie osoba. Agenci występują tylko jako trailery; autorem jest człowiek, który sterował zmianą. `evidence`

- Ogółem **72/77 (94%)** commitów ma trailer agenta Claude. Bez trailera jest 5 najwcześniejszych commitów (09-26..09-28: Init, shape-notes, bootstrap FastAPI/Vue, decyzja o surowych payloadach, lokalny Caddy).
- Per capability: **100%** w pipeline-runs, llm-providers, assessment, ingest, feed, accounts, db-backup, admin-console, daily-run, operator-alerts, model-evaluation; shared-foundations 96% (22/23), auth 94% (16/17), docs-planning 93% (71/76), platform 88% (28/32), shared-ui 82% (14/17) — niższe wartości to wyłącznie commity bootstrapu.
- Modele (commity z trailerem): Opus 5.5 ×60 (09-26..10-27), Opus 4.8 ×8 (tylko 09-27), Sonnet 5 ×2 (09-26), Sonnet 5.5 ×1 (10-11), Fable 5.1 ×1 (10-27).
- Wniosek: kod jest pisany głównie przez agentów pod kierunkiem człowieka, a uzasadnienie decyzji zostaje w folderach zmian: 33 foldery w `context/archive/`, w tym 28 `plan.md` i 29 `reviews/impl-review.md`, plus 13 plików `follow-ups`. `evidence` (liczby) / `inference` (gdzie żyje uzasadnienie)

## Implications

**Gdzie czytać zamiast pytać** (liczba w nawiasie = ile commitów capability zmieniło ten dokument w tym samym commicie; `evidence`):

| capability | najpierw przeczytaj |
|---|---|
| pipeline-runs | `szczur-radar-api/.../pipeline/CLAUDE.md` (13/14; 506 linii: Where things are, Entries, Progress/interruption/watchdog/notifications, Rules, Tests); `deploy-plan.md` sekcje S-06, S-08, S-11, S-17 |
| llm-providers | `.../llm/CLAUDE.md` (8/10; 141 linii: Bumping Spring AI or Spring Boot, Adding a provider); `deploy-plan.md` F-02 (ustawienia `LLM_*` produkcji) |
| assessment | `.../agents/assessor/CLAUDE.md` (4/7; 25 linii: Adding or changing a rule); `context/foundation/llm-assessment/` (`contract-v2.md`, `examples.md`, `hardening-sources.md`, `agent-implementation.md`) |
| ingest | `.../sources/CLAUDE.md` (5/6; 105 linii: Adding a source (e.g. Reddit), Live use); ostatnia lekcja w `lessons.md` (ponowienia źródła w kliencie, stronicowanie sprawdza spójność) |
| platform | `deploy-plan.md` (24/32; sekcje S-14 155 linii, self-hosted-pr-runner, deploy-verification-hardening); `tools/deploy-gate/README.md`, `tools/ci-runner/README.md`; 4 lekcje Railway w `lessons.md` (IaC w CI, bucket, pole nieznane SDK znika bez błędu, Hobby bez kopii) |
| db-backup | `tools/db-backup/README.md` (6/7); `deploy-plan.md` F-01 (206 linii — największa sekcja, runbook odtwarzania) |
| daily-run | `tools/pipeline-trigger/README.md` (6/6); `deploy-plan.md` S-05 |
| operator-alerts | `tools/healthchecks/README.md` (4/4); `deploy-plan.md` F-04 (160 linii) |
| accounts | `szczur-radar-api/README.md` „Komendy operatora” (7/7); `deploy-plan.md` S-07 (115 linii) |
| admin-console | `pipeline/CLAUDE.md` (9/12; sekcje Entries i S-06); `deploy-plan.md` S-06 |
| auth, feed | **brak pakietowego `CLAUDE.md`** — reguły tylko w `REPOSITORY_GUIDELINES.md` (deny-by-default, `PUBLIC_ROUTES`/`ADMIN_ROUTES`, sekcja UI) i w folderach zmian w archiwum. `evidence` (brak pliku w HEAD), `inference` (to luka w prozie) |

- Stan produkcji po merge'u żyje w **issue GitHuba** slice'a (lekcja „Status zmiany w deploy-plan.md ma być prawdziwy także po merge'u — stan produkcji żyje w issue”; 166 komentarzy właściciela w 40 issues). Przed zmianą capability przeczytaj issue jej slice'a. `evidence`
- `deploy-plan.md` (1976 linii, zmieniany w 49 commitach) to jeden runbook dla wszystkich capability operacyjnych; wzmianki: „operator” 134×, „sealed” 27×, ręczne kroki („ręczn/by hand/manual”) 46×, panel/dashboard Railway 40×. `evidence`

**Wiedza poza repo — kto ją trzyma** (`inference` z liczby wzmianek w `deploy-plan.md`; zawartość = `unknown`):

- **Rola operatora** (ta sama osoba, inna rola): zapieczętowane sekrety produkcji (`LLM_API_KEY`, `WYKOP_KEY`/`WYKOP_SECRET`, `PIPELINE_TRIGGER_TOKEN`, `HEALTHCHECK_PING_URL`, sekret sesji), wartości ręcznych zmiennych z `variables.ignore`, klucz `HEALTHCHECKS_API_KEY` używany tylko lokalnie.
- **Railway** (konto/projekt): reguła powiadomień „deployment crashed” istnieje tylko w ustawieniach konta, Wait for CI, sealed variables; `railway-app` komentuje każdy PR (63).
- **healthchecks.io + Telegram (@HealthchecksBot)**: konfiguracja checków jest kodem (`tools/healthchecks/`), ale stan i historia alertów są tylko w serwisie (42 / 30 wzmianek).
- **Wykop API**: klucze aplikacji i zachowanie API na żywo (36 wzmianek; utrwalone w fixture'ach `fixtures/wykop` i w `sources/CLAUDE.md` „Live use”).
- **OpenRouter** (konto produkcji, tier flex; 11 wzmianek) — koszty i limity konta poza repo.
- **NAS operatora (Synology Cloud Sync)** — kopia poza Railway (15 wzmianek); **Proxmox w domu + PC dewelopera** — self-hosted runnery (6).
- **Repo `kwmacioszek/szczur-radar-monitor`** (Cloudflare Worker): monitor po deployu od #99; jego historia nie jest w tym logu — `unknown (external)`.

## Unknowns

- Czy dojdzie drugi człowiek — formalna własność nie jest zapisana (brak `CODEOWNERS`; zakres sprawdzony tylko w commitach i forge). `unknown`
- Wiedza w głowie autora, nieutrwalona w prozie, szczególnie dla auth i feed (brak pakietowego `CLAUDE.md`). `unknown`
- Zawartość kont zewnętrznych (Railway, healthchecks.io, OpenRouter, Wykop, NAS) i historia `szczur-radar-monitor`. `unknown (external)`
- Udział agenta w pojedynczej linii kodu — trailer mówi tylko „współautor commitu”, nie ile napisał. `unknown`

Komendy:
- `python3 -I people.py .work/git-log.txt .work/capabilities.tsv .work` (atrybucja wg reguł kontraktu → `.work/people-by-capability.tsv`, `people-subjects.txt`, `people-crosscut.tsv`, `people-prose-by-capability.tsv`, `people-prose-touch.txt`)
- `awk`/`grep` na `.work/git-log.txt` (tożsamości, trailery, modele i ich daty, commity bez trailera)
- `gh pr list --state all --json number,author,reviews,comments,mergedBy,state` → `.work/people-prs.json`; `gh issue list --state all --json number,title,author,comments,labels,state` → `.work/people-issues.json`
- `wc -l` i `grep '^## '` na dokumentach prozy (`deploy-plan.md`, `lessons.md`, pakietowe `CLAUDE.md`, `tools/*/README.md`); `grep -ciE` na wzmiankach podmiotów zewnętrznych; `grep` na `.work/head-paths.txt` (foldery archiwum, plany, przeglądy)
