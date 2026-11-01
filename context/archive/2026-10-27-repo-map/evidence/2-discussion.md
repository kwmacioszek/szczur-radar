# Evidence: discussion

Źródła: `gh` (kwmacioszek/szczur-radar, tylko odczyt) + git (`.work/git-log.txt`) + artefakty planowania (`context/archive/*`, `context/foundation/roadmap.md`, `context/deployment/deploy-plan.md`, `lessons.md`). Okno: cała historia, 2026-09-26 … 2026-10-27 (HEAD `047b65d`); „M-2” = od 2026-10-18 (po poprzedniej mapie `5b6eabb`). Limity: brak — 72 PR i 40 issues zmieściły się w jednej stronie (`--limit 100`). PR przypisane do capability przez numer `(#N)` w subjectach squash i pliki tego commita (`.work/capabilities.tsv`, reguły kontraktu: commit liczy się dla każdej capability, której dotyka). PR zamknięte bez merge'a nie mają commita — przypisane po tytule i gałęzi (`inference`). Surowe dane: `.work/disc-commits.json`, `disc-prs.json`, `disc-issues.json`, `disc-issues-by-cap.json`, `disc-plans.json`, `disc-plans-by-cap.json`, `disc-markers.json`.

Baza zespołu (reguła 6): **1 człowiek** w oknie; 0 recenzji, wszystkie komentarze ludzkie na PR to komentarze autora (11), a 63 komentarze PR to `railway-app` (status PR env — nie review, reguła 7). Przegląd odbywa się poza forge'em: pliki `reviews/impl-review*.md` / `plan-review*.md` pisane przez agenta w `context/archive/` — to jedyny „review signal” w repo. `evidence`

## Buzz per capability

Kolumny: **PR** = PR-y (squash) dotykające capability, w nawiasie przekrojowe (>3 capability); **M-2** = z tego od 2026-10-18. **fix-PR** = PR z subjectem/gałęzią `fix…`, „follow-up”, „poprawki z przeglądu”. **Issues** = issues slice'ów/zmian przypisane po tytule (`inference`), w nawiasie `deploy-health`; otwartych: 0 we wszystkich. **Plany** = zmiany w `context/archive/` (w nawiasie od 2026-10-18). **Review** = ustalenia impl-review WARNING/OBSERVATION · plan-review CRITICAL (wszystkie impl-review: 0 CRITICAL). **Parked** = pozycje `roadmap.md ## Parked` dotyczące capability: dług/ryzyko (D) / odroczona funkcja (F) (`inference` po treści; pozycje wspólne liczone w każdej). Reverty: **0 wszędzie**. Markery TODO/FIXME w kodzie: **0 wszędzie**.

| capability | PR (cross) | M-2 | closed-unmerged | human review | fix-PR | issues (dh) | plany (M-2) | review W/O · planCRIT | Parked D/F |
|---|---|---|---|---|---|---|---|---|---|
| pipeline-runs | 14 (13) | **10** | 0 | 0 | 1 (#104) | 7 | **9 (6)** | **27/37 · 4** | **4**/0 |
| platform | **29** (16) | 8 | **3** (#10, #92, #97) | 0 | **4** (#2, #52, #58, #46) | **13 (7)** | 5 (4) | 19/21 · 3 | **4**/0 (+2 z #73 poza Parked) |
| llm-providers | 10 (9) | 4 | 1 (#23, wspólne) | 0 | 1 (#40) | 4 | 4 (3) | 8/15 · 2 | 1/0 |
| operator-alerts *(nowa)* | 4 (3) | 4 | 0 | 0 | 1 (#101) | 2 | 2 (2) | 4/14 · 2 | 2/0 |
| admin-console | 12 (12) | 6 | 0 | 0 | 0 | 4 | 4 (2) | 4/12 · 1 | 3/1 |
| daily-run | 6 (6) | 4 | 0 | 0 | 1 (#101) | 3 | 3 (2) | 5/12 · 0 | 1/1 |
| ingest | 6 (6) | 4 | 0 | 0 | 1 (#104) | 2 | 2 (1) | 13/15 · 0 | 1/1 |
| db-backup | 7 (5) | 4 | 0 | 0 | 2 (#46, #101) | 2 | 2 (1) | 8/10 · 0 | 1/1 |
| assessment | 7 (7) | 3 | 1 (#23) | 0 | 0 | 1 | 1 (0) | 19/21 · 0 | 2/**5** |
| feed | 11 (10) | 4 | 0 | 0 | 0 | 4 | 4 (**0**) | 18/21 · 0 | 1/3 |
| auth | 16 (15) | 3 | 0 | 0 | 2 (#27, #28) | 2 | 2 (0) | 9/0 · 2 | 1/2 |
| accounts | 7 (7) | 3 | 0 | 0 | 1 (#27) | 2 | 2 (0) | 10/5 · 1 | 1/2 |
| model-evaluation | 6 (6) | 4 | 0 | 0 | 0 | 0 | (w F-02) | — | (w assessment) |
| shared-foundations | 22 (20) | 7 | 0 | 0 | 1 (#27) | 1 (#36 Hikari, zamk.) | — | — | 0 |
| shared-ui | 14 (13) | 4 | 0 | 0 | 0 | 0 | (w ui-hardening) | — | 0 |
| docs-planning (proza) | 66 (27); 24 czysto dokumentacyjnych | 26 | — | — | — | 1 (#64) | — | — | — |

Uwagi do tabeli (`evidence` + reguła 4/12): w M-2 każdy PR kodowy pipeline-runs jest przekrojowy (10/10), bo slice'y M-2 to squash całych pionowych zmian — liczby PR nie należy czytać jako 10 niezależnych sygnałów. 27 z 67 PR-ów jest przekrojowych.

## Friction

- **Reverty: 0, hotfixy (subject lub gałąź `fix…`): 4, wszystkie w M-1, + follow-up #27** (#2 Hypercorn/healthcheck, #28 filtr Origin po F-03, #52 limity zasobów, #58 `watchPatterns` po tym, jak merge dokumentacji #55 przerwał przebieg 2026-10-13). W M-2 presja poprawek nie jest w subjectach, tylko w prozie: domknięcia „+ poprawki z przeglądu” (#101 F-04, #104 S-16) i sekcje `## Triage` w impl-review (np. S-17: 9 ustaleń, wszystkie naprawione przed merge'em). `evidence`
- **platform — wdrożenia, trzy podejścia w 5 dni (najsilniejszy sygnał tarcia w M-2).** S-14 (#81, merge 2026-10-19) dodało `deploy-verify` + issues `deploy-health` → 6 issues `deploy-health` 2026-10-19 podczas testów w PR env (#84–#88, #90) i #98 na produkcji 2026-10-22/23 (sondy `/api/ready` i `/healthz` 200, alarm ze stanu deployu Railway w czasie awarii Railway — fałszywy) → `deploy-verification-hardening` zastąpiło mechanizm zewnętrznym monitorem (#99, 2026-10-23) po **dwóch PR zamkniętych bez merge'a** (#92, #97: Railway po incydencie JUA1PC5H nie odtworzył PR env). Issue #91 tej zmiany ma 11 komentarzy (najwięcej w M-2). `evidence`. Część przyczyny jest zewnętrzna (awaria Railway) i reguła 5 (CI/deploy poprawia się iteracyjnie) — ale iteracje dotyczyły mechanizmu bezpieczeństwa, nie kosmetyki. `inference`
- **Otwarte po zamknięciu M-2 (S-14, issue #73).** #73 zamknięte 2026-10-24 z dwoma „otwartymi ustaleniami … do osobnej zmiany”: (1) `verify-apply.sh` traktuje `SKIPPED` (zwykły wynik watch paths) jako błąd — kontrola po apply padła fałszywie; (2) produkcja wdrożyła `2abc64c` **mimo czerwonego CI** (failure 16:39:31, deploy 16:39:39) — Wait for CI tego nie zatrzymał, przyczyna niezbadana. Brak dla nich issue, folderu zmiany i pozycji w `## Parked`; wspominają je tylko komentarz #73 oraz `roadmap.md:77` i `:382`. Wiąże się z nierozstrzygniętym Open Roadmap Question 3 („czy deploy po `config apply` czeka na CI”). `evidence`
- **pipeline-runs — ten sam silnik przerabiany w kółko.** S-02 (silnik na Embabel, 10-09) → S-06 watchdog (10-17) → S-08 pusty przebieg → S-09 rozliczanie prób → S-11 samonaprawa utkniętego przebiegu → S-18 błędy do operatora → **S-17 usunięcie Embabel** (`PipelineAgent.java` → `RunSequence.java`, R052; 10-27) → tego samego dnia osobny `pipeline-concurrency-review`. Ten ostatni pisze wprost: „**F3 nie jest całkowicie wyeliminowane**” (anulowanie przez watchdog nie jest zsynchronizowane ze sprzątaniem; fałszywy `RUN_CLOSE_FAILED` wciąż możliwy) oraz utrzymuje A4/A8 jako przyjęte ryzyka. Do tego niestabilny test `ConcurrentAssessmentDispatcherTest…cancellingARunInterrupts…` (Timeout, run 39133095273, komentarz #73). Wspólna przyczyna (reguła 4): M-2 celowo utwardzał ten obszar po audycie (sekcja A: 8 ustaleń) — jednak 8 zmian w 18 dni w jednym silniku, zakończonych przepisaniem i resztkowym oknem wyścigu, to realne drugie podejścia. `evidence` + `inference`
- **llm-providers — obsługa błędów dostawcy w pięciu podejściach:** #40 `openrouter-in-body-errors` (incydent M-1: błąd w HTTP 200 spalił próby 15 postów) → S-09 (awaria dostawcy nie zużywa prób; bezpiecznik 3 awarii) → S-10 (fail-fast `LLM_*`) → S-18 → S-17 (Spring AI wprost, nowy `LlmClientException`). `evidence`
- **Zamknięte bez merge'a: 4.** #10 (test tokenu Railway — celowy), #23 (F-02 w Pythonie → ponownie jako #31 w Javie; zmiana stosu, nie awaria, M-1), #92 i #97 (wyżej). Do tego zamknięcia-i-ponowne-otwarcia #93/#94/#95 dla odtworzenia PR env po awarii Railway i ręczne usuwanie zmiennych w PR env pr-94/95/96 (runbook S-19). `evidence`
- **Issues: 40, otwartych 0, z etykietą bug 0** (etykiety: `deploy-health` ×7, `enhancement` ×1). Najdłużej otwarte to issues M-1 zamykane dopiero po weryfikacji produkcji (S-04 #17 i S-06 #19: 19,6 dnia). W M-2 issues zamykane w 1–9 dni. Mediana PR created→merged 0,1 dnia, maks. 3 dni (#93–#96 podczas awarii Railway). `evidence`
- **CI na self-hosted runnerach:** losowe `failed to prepare extraction snapshot` przy budowie obrazów (Parked, od S-08 #80; obejście `gh run rerun --failed`). `evidence`; reguła 5 — tryb pracy CI, nie zepsuta capability.

## Planning attention

- **31 zarchiwizowanych zmian** (+ żywa `bootstrap-verification`; poprzednia mapa pominięta), **16 od 2026-10-18**. Pliki review: 37 impl-review z łącznie 0 CRITICAL / 101 WARNING / 135 OBSERVATION; plan-review: **10 CRITICAL — wszystkie złapane przed implementacją** (S-14 ×2; po 1: S-01, F-03, S-06, S-09, deploy-verification-hardening, F-04, S-18, S-17). `evidence`
- **pipeline-runs przyciąga najwięcej planowania:** 9 zmian (6 w M-2), najwięcej ustaleń impl-review (27 W / 37 O) i plan-review CRITICAL (4); w `deploy-plan.md` 6 sekcji (S-06 148 linii, S-11 81, S-09 75, S-17 86, S-18 89, S-08 71); issues tej capability mają 44 komentarze autora (najwięcej). Przesunięcie od poprzedniej mapy (17.10), gdzie prowadził F-02/assessment (2996 linii prozy, 20 plików — nadal największy pojedynczy folder). `evidence`
- **platform:** 5 zmian (4 w M-2: S-14 1350 linii prozy, self-hosted-pr-runner, deploy-verification-hardening z `frame.md`, S-19 ops-docs-debt), 13 issues (7 `deploy-health`), audyt sekcja C (operacje) 15 ustaleń — najwięcej z sekcji audytu. `deploy-plan.md` to dziennik incydentów/runbooków: 49 commitów (19 w M-2), 1976 linii. `evidence`
- **operator-alerts — nowa capability, w całości z M-2:** F-04 (z `frame.md`, 1042 linie prozy) + S-18; 2 pozycje długu z przeglądu S-18 (F4, F5) w Parked. `evidence`
- **Wyciszone od M-1 (0 zmian od 2026-10-18): feed, auth, accounts, assessment.** feed miał 4 zmiany w M-1 (S-03, S-04, feed-view-ui-hardening, S-02). assessment ma 0 nowych planów, ale w Parked trzyma największy dług jakości: „LLM hardening” — żaden z 12 modeli nie spełnił progów kierunku, progi złagodzono do ≥70 %/≤10 % (cel ≥75 %/≤3 %) oraz brak ochrony przed dryfem modelu (audyt B4). `evidence`
- **Co dalej (po zamknięciu M-2):** `roadmap.md:77` (v3.7) — M-3 nieotwarty; decyzja użytkownika: najpierw ta mapa i audyt zawężony pod kolejny kamień — **drugie źródło (Reddit, FR-023 → ingest), silnik po S-17 (pipeline-runs), ustalenia S-14 z #73 (platform)**. Open Roadmap Questions: 1 z 5 rozstrzygnięte (Q1); otwarte Q2 (locale bazy dla filtra S-04), Q3 (żywy Railway: deploy po apply vs CI, crash loop, SIGTERM, Hikari, przebieg 22 h), Q4 (próg FR-025), Q5 (budżet LLM na przebieg). `evidence`
- **Parked urósł z 18 (mapa 17.10) do 32 pozycji**; ~14 nowych pochodzi z audytu M-2 i przeglądów M-2 (S-12 zaparkowany po `/10x-frame` — PR #105 tylko dla parkowania; dni bez danych F2/F5; kolejka alarmów F4; watchdog F5; NAS→Drive; drugi runner CI; cache Dockera; start API). `evidence`
- **Lekcje (`lessons.md`, 20 wpisów):** ~6 o Railway/IaC/platformie, po 1 o backupie, kontrakcie LLM, źródłach (ponowienia w kliencie), Embabel (`FIRE_ONCE` — **już historia**, Embabel usunięty w S-17, reguła 11), reszta o procesie agentowym. `inference`

## Debt markers

- `git grep -w 'TODO|FIXME|HACK|XXX'` na HEAD: **0 w kodzie** wszystkich 15 capability niebędących prozą (`szczur-radar-api`, `szczur-radar-web`, `tools`, `.github`, `.railway`): 0,00 / 1000 linii. 57 trafień w prozie: 50 w lustrach skilli (`.claude/skills`, `.agents/skills`), 2 w `roadmap.md` (TODO wydajności filtra feedu), 2 w archiwum, 3 w starej mapie. `evidence`
- Dług żyje w prozie (konwencja repo, nie brak długu): `## Parked` (32), `follow-ups/*.md` (12 plików w 11 zmianach; w M-2 tylko S-09, S-19, S-13), 2 niezamknięte `[ ]` w archiwalnych planach (self-hosted-pr-runner 3.6, stuck-run-self-heals 3.3 — zrzuty `/admin`) i 13 `[ ]` w `deploy-plan.md` — **wszystkie w runbooku „after the merge” S-14** (wiersze 1043–1059), choć zamykający komentarz #73 opisuje wykonaną weryfikację. Inne runbooki M-2 nie mają otwartych pól → sekcja S-14 jest nieuaktualniona (por. `lessons.md:134`). `evidence` + `inference`

## Mechanical signals

- **Liczba PR i komentarzy platform/docs-planning** — wysoka, bo każda zmiana dotyka `ci.yml`/`.railway/railway.ts`/`deploy-plan.md` (reguła 4, 8); proza liczona tu jako dyskusja o capability, którą opisuje, nie jako gorący obszar.
- **Komentarze `railway-app` (63) i `github-actions` (5 na #90, #98)** — automatyka, nie review (reguła 7). Komentarze autora na PR to procedura (reopen PR env, info o zmiennych), nie dyskusja.
- **7 issues `deploy-health`** — 6 to testy mechanizmu w PR env pr-80/pr-81 i `lag-test` jednego dnia; #98 fałszywy alarm z awarii Railway. Liczą się jako tarcie mechanizmu wdrożeń, nie jako 7 incydentów produkcji.
- **Wielkość PR:** #31 F-02 (32 128 linii) zawiera 480 nagranych odpowiedzi LLM (szum wg kontraktu); #62 audyt i #45/#102 aktualizacje skilli to proza/narzędzia.
- **Rozmiar `deploy-plan.md` per sekcja** i słownictwo „interrupted/przerwanie” — to nazwy stanów przebiegu w runbookach, nie licznik incydentów.
- **Tabela `## Backlog Handoff` w roadmap** nadal mówi „Run `/10x-plan …`” przy zrobionych S-08–S-11, S-14–S-16, S-19 — dryf prozy, nie sygnał o kodzie.
- **Przepisania F-03/F-02 (M-1)** — spillover zmiany stosu na auth/accounts/assessment (reguła 12); poza M-2.
- **Poza skanowanym worktree:** w głównym checkoutcie leży nieśledzony `context/changes/deploy-verification-hardening/` (pliki z 2026-10-20), kopia zmiany zarchiwizowanej 2026-10-23 — pozostałość, nie żywa zmiana.

## Unknowns

- **Brak drugiego recenzenta** — nie da się zmierzyć jakości review ani sporów; jedyny sygnał przeglądu to raporty agenta w `context/archive/**/reviews/`. `unknown`
- **Pierwsza nocna praca crona na silniku S-17** wypada 2026-10-28 (po HEAD) — zachowanie nowego silnika na produkcji w nocnym przebiegu jest niezweryfikowane. `unknown`
- **Przyczyna deployu `2abc64c` mimo czerwonego CI** (#73) i to, czy Wait for CI wstrzymuje `railway redeploy` (`deploy-plan.md:967`: „Not verified yet”). `unknown`
- **Błędy produkcyjne:** brak error trackera; alarmy idą healthchecks.io → Telegram (F-04) i do zewnętrznego repo `szczur-radar-monitor` (monitor po deployu, #99) — ich historia jest poza tym repo. `unknown (external)`
- **Open Roadmap Questions 2–5** (locale bazy, żywy Railway, FR-025, budżet LLM) bez odpowiedzi. `unknown`
- Przypisanie issues, zmian i pozycji Parked do capability jest po nazwie/treści, nie po plikach. `inference`

### Polecenia
- `python3 caps.py` (scratchpad) — parsowanie `.work/git-log.txt`, filtr szumu, mapowanie `capabilities.tsv` → `.work/disc-commits.json`
- `gh pr list -R kwmacioszek/szczur-radar --state all --limit 100 --search "created:>=2026-09-01" --json number,title,createdAt,mergedAt,closedAt,state,reviews,comments,additions,deletions,headRefName,author`
- `gh issue list -R kwmacioszek/szczur-radar --state all --limit 100 --search "created:>=2026-09-01" --json number,title,labels,createdAt,closedAt,state,comments,author`
- `gh issue view 73|98 --json body,comments`
- `python3 plans.py` — pliki, nagłówki `**Severity**` i `[ ]`/`[x]` w `context/archive/*/` → `.work/disc-plans.json`
- `grep`/`awk` po `roadmap.md` (`## Parked`, Open Questions, Backlog Handoff), `deploy-plan.md` (sekcje, `[ ]`), `lessons.md` (nagłówki)
- `git grep -c -w -E 'TODO|FIXME|HACK|XXX' HEAD`
