# Evidence: discussion

Źródła: `gh` (kwmacioszek/szczur-radar, tylko odczyt) + git + artefakty planistyczne w `context/`. Okno: cała historia, 2026-09-26 … 2026-10-17 (HEAD `5b6eabb`). Limity: nie przekroczone (43 PR, 17 issues, jedna strona `--limit 100`). Surowe dane: `context/map/.work/discussion-*.{tsv,json}`.

Kontekst bazowy (`evidence`): jeden człowiek, 47 commitów, każdy PR squash-merge'owany (1 commit = 1 zmiana), prawie wszystkie z trailerem `Co-authored-by: Claude`. **Na żadnym z 43 PR nie ma ani jednej recenzji** (`reviews` = 0 wszędzie); komentarze PR to wyłącznie bot `railway-app` (41) i dwie notatki właściciela przy zamknięciu. Wszystkie 68 komentarzy w issues napisał właściciel. Recenzja odbywa się poza forge'em — w plikach `reviews/impl-review*.md` i `plan-review*.md` w `context/archive/` (`inference`: pisane przez sesje agenta na polecenie właściciela).

## Buzz per capability

PR = squash-commity `(#n)` dotykające kodu capability (pliki bez szumu, przypisanie przez `attribute.py`); „cross>3” = ile z nich dotyka > 3 capability kodowych. Plany = zmiany w `context/archive/` przypisane po nazwie (jedna zmiana może iść do kilku capability). Review = pliki `reviews/*.md` tych zmian; ~findings = nagłówki `F<n>` w impl-review (przybliżenie, mechaniczne). Open FU = otwarte `- [ ]` w `follow-ups/` w chwili archiwizacji (stan obecny `unknown`, część na pewno zamknięta później). Incydenty = wpisy w `context/deployment/deploy-plan.md`.

| capability | PR (cross>3) | closed-unmerged | human review | reverts | fix-PR | issues (open) | plany | review files / ~findings | open FU | incydenty prod/PR-env | markery |
|---|---|---|---|---|---|---|---|---|---|---|---|
| platform | 22 (9) | 1 (#10 test tokenu) | 0 | 0 | 4 (#2, #27, #52, #58) | #30, #51 otwarte | api-docker-image, java-api-rewrite, bootstrap-verification | 4 / ~6 | 0 (F4 Hikari → issue #36) | **8** (zob. Friction) | 0 |
| auth | 10 (9) | 0 | 0 | 0 | 2 (#27, #28) | #15, #25 zamknięte | admin-bootstrap-and-login, java-api-rewrite | 5+3 / ~10 | 0 | 2 (merge bez zgody, JWT_SECRET ujawniony → rotacja) | 0 |
| shared-foundations | 8 (8) | 0 | 0 | 0 | 1 (#27) | #36 otwarte (Hikari, `enhancement`) | — (część F-03) | — | 1 (wspólne parsowanie durations, cli-user-onboarding F4) | 1 (Hikari warnings) | 0 |
| feed | 8 (7) | 0 | 0 | 0 | 0 | #16 zamk., **#17 otwarte** | post-detail-view, filter-by-assessment, feed-view-ui-hardening, (S-02) | 3 / ~21 | 0 | 0 (locale `ILIKE` = check w runbooku) | 0 |
| llm-providers | 7 (5) | 1 (#23, z F-02) | 0 | 0 | 1 (#40 openrouter-in-body-errors) | #24, #39 zamk. | assessment-contract-harness, openrouter-in-body-errors | 6+1 / ~12 | 7 (F-02, wspólne z assessment) | **3** (klucz 401, flex „choices not set”, `LLM_TIMEOUT=120` = 120 ms) | 0 |
| admin-console | 6 (6) | 0 | 0 | 0 | 0 | #41 zamk., **#19 otwarte** | admin-panel-ui-hardening, pipeline-run-history-dashboard, (S-02) | 7+2 / ~12 | 0 | 0 | 0 |
| pipeline-runs | 4 (3) | 0 | 0 | 0 | 0 | #14 zamk., **#19 otwarte** | first-assessed-wykop-feed, pipeline-run-history-dashboard | 4+2 / ~28 | 6 (S-02) | **3** (run „trwający” ~22 h, przerwania przez deploy 2026-10-13, luka w obserwowalności) | 0 |
| assessment | 4 (4) | 1 (#23) | 0 | 0 | 0 | #24 zamk. | assessment-contract-harness (61 plików, 8548 linii — największa) + `foundation/llm-assessment` | 6 / ~40 | 7 | 0 | 0 |
| accounts | 4 (4) | 0 | 0 | 0 | 0 | #20 zamk. | cli-user-onboarding, admin-bootstrap-and-login | 1+5 / ~8 | 1 | 3 (ssh rozłączony w trakcie, link w transkrypcie agenta, checksum V6 w PR env #49) | 0 |
| db-backup | 3 (1) | 0 | 0 | 0 | 0 (#46 = domknięcie) | #43 zamk. | production-data-gate | 1 / ~9 | 0 | **4** (bucket niewdrożony na prod, cron `QUEUED`, ciche awarie bez reguły, restart Postgresa wisiał) | 0 |
| shared-ui | 10 (8) | 0 | 0 | 0 | 0 | #41, #47 zamk. | admin-panel- i feed-view-ui-hardening | (wspólne z admin/feed) | 0 | 0 | 0 |
| daily-run | 2 (2) | 0 | 0 | 0 | 0 | #18 zamk. | daily-scheduled-run-freshness | 1 / ~5 | 0 | 2 (ręczny run przerwany przez merge docs, brak sygnału crash bez reguły) | 0 |
| ingest | 2 (2) | 0 | 0 | 0 | 0 | #14 zamk. | first-assessed-wykop-feed | (wspólne z S-02) | 2 (store wolny w PR env; maskowanie błędów Wykopu F8d) | 1 (Postgres w `sfo` → store 1 m 44 s) | 0 |
| model-evaluation | 2 (2) | 0 | 0 | 0 | 0 | #24 zamk. | assessment-contract-harness | (wspólne z F-02) | — | 0 | 0 |
| docs-planning (proza) | 40 PR, 16 czysto dokumentacyjnych | — | — | — | — | — | — | — | — | — | 7 (roadmap, review) |

Pokrycie: 47/47 commitów przypisanych; 0 nieprzypisanych plików (kontrakt). Issues: 17, z czego 0 z etykietą bug (jedyna użyta etykieta: `enhancement` na #36).

## Friction

- **Reverty: 0** (`git log | grep -iE 'revert|hotfix|rollback'` → brak). `evidence`.
- **Zamknięte bez merge'a: 2.** #23 (F-02 w Pythonie, 5543 linii) — „implementacja F-02 zostanie wykonana w Javie po java-api-rewrite” → ponownie wylądowało jako #31 (31210 linii z fiksturami) — **druga próba assessment/llm-providers** wymuszona zmianą stosu, nie awarią. #10 — test zakresu tokenu Railway (platform). `evidence`.
- **Drugie podejścia / ten sam zakres poprawiany w krótkich odstępach (`evidence` z subjectów i deploy-plan):**
  - **platform / Railway — najwięcej iteracji:** #1 → #2 dzień później (healthcheck IPv4, Hypercorn); config ≠ merge (start command nie zmienił się bez `config apply`); `API_PORT` pusty; Postgres sleep → zawieszony start 2026-10-09 (#38 „Postgres without sleep”) → ta sama deklaracja powodowała restart przy każdym apply i zawieszenie 2026-10-12 (#46 usuwa ją); `watchPatterns` w złym miejscu → każdy merge redeployował wszystko i przerwał run 2026-10-13 (#58); limity zasobów (#52); `checkSuites` na usłudze tworzonej przez IaC; bucket `db-backups` niewdrożony na produkcji. Pięć lekcji w `lessons.md` dotyczy Railway/IaC. Po regule 5 (`false-signals`) część to normalna iteracja infra — ale **trzy z tych zdarzeń dotknęły produkcji (DB 503, przerwany run)**, więc to nie tylko szum (`inference`).
  - **auth:** S-01 w Pythonie (#13, 2 re-review w jeden dzień) → całość przepisana w F-03 (#26) → follow-upy #27 i #28 (ścieżka filtra Origin) dzień później. Przepisanie to decyzja stosu (reguła 12: spillover F-03 na auth/accounts/platform), poprawki #28 są realne.
  - **llm-providers:** po pierwszym produkcyjnym runie: zły klucz (401), potem flex zwracający błąd w HTTP 200 → 15 postów straciło próbę → dedykowany PR #40 dzień później.
  - **pipeline-runs / admin-console:** S-02 → S-06 jako „hardening monitorowania” wynikły z produkcji (run zawieszony ~22 h, 7 min bez logów, run przerwany przez deploy). S-06 dodało watchdog, przyczynę przerwania, powiadomienia.
  - **feed / admin-console UI:** każdy widok przeszedł osobny „ui-hardening” (#42, #48) po zbudowaniu w S-02 — zaplanowana praca stylu, nie awaria (reguła 5, `mechanical`).
- **Otwarte issues (wiek na 2026-10-17):** #17 S-04 (19 dni; kod zmergowany 10-14, brak weryfikacji produkcji — deploy-plan nadal mówi „not merged”, dokument w tyle); #19 S-06 (19 dni; zmergowane 10-17, weryfikacja produkcji w toku); #36 Hikari (11 dni, jedyny dług techniczny w trackerze); #51 railway-resource-limits (5 dni, PR #52 zmergowany); #30 self-hosted runner (14 dni, nice-to-have). Proces: issue zamyka się dopiero po weryfikacji produkcji, więc „otwarte” ≠ „błąd” (`inference`).
- **Czas PR (created→merged):** duże zmiany 1–3 dni (#13 1 d, #31 3 d, #37 3 d, #49/#50/#60 1 d). `evidence`.

## Planning attention

- 14 zmian w `context/archive/` + 1 w `context/changes/` (bootstrap-verification). Każdy slice/foundation ma plan, plan-review (część), impl-review i często follow-upy.
- **Najwięcej uwagi: assessment + llm-providers + model-evaluation (F-02)** — 61 plików, 8548 linii, 6 plików review (impl-review po fazach 3, 5, 6 + końcowy; 2 plan-review), 7 otwartych follow-upów przy archiwizacji (m.in. F8d maskowanie błędów Wykopu, F8f niewidoczne znaki — osobny plik). Plus katalog `context/foundation/llm-assessment` i 2 pozycje Parked („LLM hardening: ironia”, „obrazki”). `evidence`.
- **pipeline-runs / ingest (S-02)**: 4 review, 2 pliki follow-up, 6 otwartych pozycji (spalanie prób przy błędzie całego runu, wolny store, PR env deployujący stary commit — przyczyna nieznana). S-06: plan-review + impl-review; roadmap Parked: „przebieg bez Embabel” (z plan-review S-06).
- **auth**: S-01 — 5 plików review, w tym 2 re-review i rereview3 tego samego dnia (najwięcej rund dla jednej zmiany).
- **admin-console**: admin-panel-ui-hardening — 7 plików review (z dowodami Playwright F1/F2).
- **Dokumenty fundamentów (liczba commitów)**: deploy-plan 30, roadmap 22, lessons 12, prd 11, infrastructure 11. deploy-plan to de facto dziennik incydentów platformy (`inference`).
- Świeżość: ostatnie plany to S-06 (2026-10-15), S-03/S-04/S-05/S-07 (2026-10-12).

## Debt markers

- `TODO|FIXME|HACK|XXX` na HEAD w plikach kodu wszystkich 14 capability: **0** (0,00 / 1000 linii). Jedyne 7 trafień to proza (`roadmap.md` 3, impl-review S-04 2, próbka pilota 1, `render_roadmap.swift` 1). `evidence`.
- Dług jest prowadzony w prozie, nie w kodzie: `follow-ups/*.md` (17 otwartych `[ ]` łącznie przy archiwizacji), `roadmap.md ## Parked` (18 pozycji, m.in. wydajność filtra feedu, unieważnianie sesji wersją poświadczeń, komunikat świeżości) i issues #36/#30. Brak markerów w kodzie to konwencja, nie brak długu (`inference`).

## Mechanical signals

- **docs-planning** — 46/47 commitów, 16 PR czysto dokumentacyjnych (głównie zapis stanu deploy-planu i domknięcia slice'ów): churn prozy, przypisany wyżej do omawianych capability (reguła 8), nie gorący obszar.
- **cross>3** — prawie każdy feature PR dotyka 5–11 capability (squash całej zmiany: plan + kod + testy + docs). Liczby PR per capability mierzą „ile zmian ją zahaczyło”, nie edycje (kontrakt).
- **F-03 (#26) i F-02 (#31)** — największe PR; F-03 to przepisanie stosu (spillover na auth/accounts/platform/shared-foundations, reguła 12), 480 plików F-02 to fikstury (szum).
- **Fix-ratio platform (4 fix-PR)** — iteracja infra (reguła 5); utrzymane jako friction tylko dla zdarzeń z wpływem na produkcję.
- **ui-hardening #42/#48** — zaplanowane dopracowanie UI, nie naprawy defektów.
- **Komentarze `railway-app`** na 41 PR — status bota, nie recenzja (reguła 7).
- **Licznik ~findings** — nagłówki `F<n>`, pliki fazowe F-02 mogą powtarzać te same findings (wszystkie mają po 10) — przybliżenie.
- **„Open FU”** — checkboxy w archiwum są niezmienne; co najmniej F8b (`LLM_TIMEOUT` bez górnej granicy) jest dziś rozwiązane (README: maks. `30m`), więc liczba zawyża dług.

## Unknowns

- **Brak ludzkiej recenzji na forge'u** — nie da się zmierzyć mediany review ani sporów; jedyny ślad dyskusji to artefakty agenta w `context/`. Jakość tych recenzji: `unknown`.
- **Aktualny stan follow-upów** z archiwum (17 `[ ]`) — część rozwiązana później bez odhaczenia; wymagałoby przejścia per pozycja (`/10x-research`).
- **Błędy produkcyjne** — brak error trackera (Sentry itp.) w repo; incydenty znane tylko z ręcznych zapisów w deploy-plan i z logów Railway (poza zakresem). Nie wiadomo, ile awarii nie zostało zapisanych.
- **Przyczyna deployu starego commitu web w PR env** (2026-10-09, 2026-10-13) — `unknown` wg deploy-plan.
- **Stan S-04 na produkcji** — kod zmergowany, issue #17 otwarte, deploy-plan nieaktualny (sprawdzenie locale `ILIKE` nieodnotowane).
- Issues sprzed `gh` (zadania w czacie, decyzje „użytkownika” w roadmapie) — poza trackerem.

### Polecenia
- `python3 -I` nad `.work/commit-files.tsv` → `.work/discussion-commit-caps.tsv` (capability per commit, cross-cut, fix)
- `gh pr list --state all --limit 100 --json number,title,createdAt,mergedAt,closedAt,state,reviews,comments,additions,deletions,author,headRefName` → `.work/discussion-prs.json`
- `gh issue list --state all --limit 100 --json number,title,labels,createdAt,closedAt,state,comments,author` → `.work/discussion-issues.json`
- `gh pr view 23|10 --json comments` (powód zamknięcia)
- `git log --format='%h %s' | grep -iE 'revert|hotfix|rollback'`
- `git ls-files context/archive` + `grep -c` (`reviews/`, `follow-ups/`, `- [ ]`, nagłówki `F<n>`)
- `grep -n '^#'` w `lessons.md`, `roadmap.md`; `git log --oneline -- <foundation docs> | wc -l`
- skan `TODO|FIXME|HACK|XXX` po plikach z `git ls-files` przez `attribute.capability/noise` → `.work/discussion-markers.tsv`
