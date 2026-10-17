# Evidence: people

- Okno: cała historia, 2026-09-26 .. 2026-10-17 (`a6f9c23` .. `5b6eabb`), 47 commitów, wszystkie liczone (0 botów, 0 commitów autorstwa agenta, 0 masowych) — według `scan-contract.md`. `evidence`
- **Ludzie w oknie: 1** — właściciel repozytorium / jedyny autor, pod dwiema tożsamościami git (6 commitów z 2026-09-26: `Init`, shape-notes, bootstrap; 41 commitów od 2026-09-27). Brak `.mailmap`; tożsamości scalone ręcznie (to samo imię i nazwisko). `evidence`
- **Baseline: mały zespół — koncentracja jest stanem bazowym** (false-signals reguła 6). Każda capability ma 100% jednego człowieka; to nie jest ustalenie ani ryzyko. `evidence`
- Agent (Claude) nie jest liczony jako osoba: 42 z 47 commitów ma trailer `Co-authored-by: Claude …` (Opus 5.5 ×31, Opus 4.8 ×8, Sonnet 5 ×2, Sonnet 5.5 ×1). Commity bez trailera: 3 commity startowe (2026-09-26) oraz `#4` (decyzje o raw payloadach) i `#12` (lokalny stos Caddy). `evidence`
- Squash merge: 1 commit = 1 PR/zmiana (50–180 plików), więc „commity per capability” to liczba zmian, które ją dotknęły, nie liczba edycji. Szum (vendored `.claude/`, fixture'y, lockfile'y, media) wyłączony przez `attribute.py`. `evidence`

## Obszary

Wszystkie capability z krytycznością high/medium oraz top 5 wg commitów (docs-planning raportowane osobno jako proza; model-evaluation — low, poza top 5, podane dla kompletności).

| Capability | Kryterium włączenia | Commity | Pliki (zmiany) | Okres |
|---|---|---|---|---|
| platform | high; top 5 | 25 | 121 | 09-26 .. 10-17 |
| shared-ui | medium; top 5 | 13 | 62 | 09-26 .. 10-17 |
| auth | high; top 5 | 10 | 88 | 09-29 .. 10-17 |
| shared-foundations | medium; top 5 | 8 | 60 | 10-02 .. 10-17 |
| feed | high; top 5 | 8 | 55 | 09-29 .. 10-17 |
| llm-providers | high | 7 | 71 | 10-02 .. 10-17 |
| admin-console | medium | 6 | 39 | 10-09 .. 10-17 |
| pipeline-runs | high | 4 | 56 | 10-09 .. 10-17 |
| assessment | high | 4 | 88 | 10-02 .. 10-14 |
| accounts | high | 4 | 65 | 09-29 .. 10-13 |
| db-backup | high | 3 | 7 | 10-12 .. 10-13 |
| daily-run | medium | 2 | 18 | 10-13 .. 10-17 |
| ingest | high | 2 | 42 | 10-06 .. 10-09 |
| model-evaluation | low (kontekst) | 2 | 22 | 10-06 .. 10-09 |
| docs-planning (proza) | osobno | 46 | 477 | 09-26 .. 10-17 |

## People per capability

Jedna osoba we wszystkich wierszach: **właściciel repozytorium / jedyny autor**. Grupy tematów wywiedzione z tematów commitów (identyfikatory zmian) i ścieżek przypisanych w `commit-files.tsv`. `evidence` (autorstwo, okres) / `inference` (nazwy grup tematów).

| Capability | Osoba | Grupy tematów (zmiany, które ją dotknęły) | Okres |
|---|---|---|---|
| platform | właściciel | scaffold i pierwszy deploy Railway (#1, #2 bind dual-stack), przepisanie na Javę (F-03 #26/#27), obraz Dockera API (#33/#34), backup i limity kosztów (F-01 #44/#46), limity zasobów (#52), watchPatterns w IaC (#58); ponadto deklaracje IaC/CI przy każdym slice'ie S-0x | 09-26 .. 10-17 |
| shared-ui | właściciel | scaffold SPA, hardening UI panelu admina (#42) i feedu (#48), komponenty współdzielone dokładane przy S-02..S-07 | 09-26 .. 10-17 |
| auth | właściciel | logowanie/wylogowanie i ochrona tras (S-01 #13), port na Spring Security (F-03 #26), poprawki review: ścieżka filtra Origin (#28); reguły trasy dokładane z każdym nowym endpointem (S-02..S-07) | 09-29 .. 10-17 |
| shared-foundations | właściciel | fundament Javy (F-03), konwertery enumów / testy schematu dokładane przy migracjach S-02..S-07 | 10-02 .. 10-17 |
| feed | właściciel | pierwszy feed (S-02 #37), hardening UI feedu (#48), widok posta (S-03 #53), filtr (S-04 #54), etykieta świeżości (S-05 #50) | 09-29 .. 10-17 |
| llm-providers | właściciel | konfiguracja dostawcy (F-03), klienci i retry (F-02 #31), błędy OpenRouter w treści 200 (#40), postęp/limity w S-06 (#60) | 10-02 .. 10-17 |
| admin-console | właściciel | panel admina (S-02 #37), hardening UI (#42), historia przebiegów i powiadomienia (S-06 #60) | 10-09 .. 10-17 |
| pipeline-runs | właściciel | silnik przebiegów (S-02 #37), błędy OpenRouter (#40), start dzienny (S-05 #50), watchdog i przyczyny przerwań (S-06 #60) | 10-09 .. 10-17 |
| assessment | właściciel | kontrakt V2 jako kod (F-02 #31), użycie w pipeline (S-02), filtr po ocenie (S-04) | 10-02 .. 10-14 |
| accounts | właściciel | create-admin (S-01 #13, F-03), create-user / reset-password / linki aktywacyjne (S-07 #49), konto systemowe (S-05 #50) | 09-29 .. 10-13 |
| db-backup | właściciel | backup + restore check + retencja (F-01 #44, domknięcie #46), manifest po V6 (S-05) | 10-12 .. 10-13 |
| daily-run | właściciel | cron `pipeline-trigger` i `/internal` (S-05 #50), heartbeat (S-06 #60) | 10-13 .. 10-17 |
| ingest | właściciel | mapowanie Wykopu (F-02 #31), pobieranie i raw payloady (S-02 #37) | 10-06 .. 10-09 |
| model-evaluation | właściciel | gold sample i nagrania (F-02 #31), S-02 | 10-06 .. 10-09 |

## Concentration

- **Mały zespół (1 człowiek) — koncentracja jest stanem bazowym.** Bez języka „bus factor”. `evidence`
- Capability, których kiedykolwiek dotknęła tylko jedna osoba: **wszystkie 15** (łącznie z docs-planning). `evidence`
- Druga tożsamość git pojawia się tylko w scaffoldzie z 2026-09-26 (platform, shared-ui, docs-planning) — to ta sama osoba, nie drugi współtwórca. `evidence`
- Recenzje: `gh pr list` — 43 PR, wszystkie autorstwa właściciela, 0 z recenzją, merge'ujący to wyłącznie właściciel. Brak `CODEOWNERS`. Nie ma drugiej pary oczu w historii forge. `evidence`

## Agent co-authorship

| Capability | Commity z trailerem agenta |
|---|---|
| auth, shared-foundations, feed, llm-providers, admin-console, pipeline-runs, assessment, accounts, db-backup, daily-run, ingest, model-evaluation | 100% (wszystkie) |
| docs-planning | 41/46 (89%) |
| platform | 21/25 (84%) |
| shared-ui | 10/13 (77%) |

- Cały kod Javy (od F-03, 2026-10-02) powstał w commitach współtworzonych przez agenta; commity bez agenta to tylko scaffold, decyzje o raw payloadach (#4) i lokalny stos Caddy (#12). `evidence`
- Kontekst, nie filtr: człowiek sterował zmianami (plan, review, merge), ale wiedza o szczegółach implementacji jest zapisana raczej w śladzie planistycznym (`context/archive/*/plan.md`, `research.md`, `reviews/`) niż w pamięci autora. `inference`

## Implications

- Przed każdą zmianą: pytać właściciela — jest jedynym rozmówcą dla każdej capability. Ponieważ kod pisał głównie agent, drugim źródłem „kto już próbował” jest ślad zmian w `context/archive/`. `inference`
- Co przeczytać przed zmianą danego rodzaju (PR = squash, więc PR = cała zmiana):
  - platform / IaC / deploy: #1, #2, #33, #44, #46, #52, #58 (incydenty z restartami i watchPatterns), plus `context/deployment/deploy-plan.md`.
  - auth: #13, #26, #28 (filtr Origin po review).
  - accounts: #49 (S-07), #50 (konto systemowe, kolizja numerów migracji V6/V7).
  - llm-providers / assessment: #31 (F-02), #40 (błędy w treści 200).
  - pipeline-runs / daily-run / admin-console: #37 (S-02), #50 (S-05), #60 (S-06).
  - feed: #37, #48, #53, #54.
  - db-backup: #44, #46.
  `evidence` (numery PR z tematów commitów)

## Unknowns

- Czy ktoś poza właścicielem będzie pracował nad repo (brak CODEOWNERS, brak recenzentów) — `unknown`.
- Formalna własność obszarów — brak pliku; jedyna osoba = de facto właściciel wszystkiego. `unknown`
- Ile decyzji implementacyjnych podjął agent bez wyraźnej decyzji człowieka — git tego nie pokazuje (trailer mówi tylko o współautorstwie). `unknown`
- Merge PR #13 przez agenta (zapis w `deploy-plan.md`) jest w forge widoczny jako merge właściciela — atrybucja merge'y na forge nie odróżnia człowieka od agenta. `unknown`

## Komendy

- `python3 -I context/map/.work/people-analyze.py` (importuje dane z `commit-files.tsv` / `git-log.txt` wygenerowane przez `attribute.py`) → `context/map/.work/people-per-capability.txt`
- `python3 -I -c …` — commity bez trailera agenta z `git-log.txt`
- `git -c core.quotepath=off shortlog -sne HEAD` (e-maile usunięte z wyjścia)
- `ls .mailmap CODEOWNERS .github/CODEOWNERS docs/CODEOWNERS`
- `gh pr list -R kwmacioszek/szczur-radar --state all --limit 100 --json number,author,reviews,mergedBy`
