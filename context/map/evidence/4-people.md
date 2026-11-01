# Evidence: people

Okno: cała historia (a6f9c23 2026-09-26 .. 5b4ac92 2026-11-01), 90 commitów, wszystkie policzone (0 botów, 0 autorstwa agenta, 0 masowych). Przypisanie z `.work/commit-files.tsv`, bez wierszy z `noise`.

- `humans_in_window: 1` — jedna osoba, kwmacioszek (dalej: operator), pod trzema tożsamościami git. To **linia bazowa, nie ustalenie** (false-signal 6). Tożsamości scalone prywatnie; dwie poboczne: 6 commitów 2026-09-26 (bootstrap) i 3 commity docs 2026-10-18..21.
- Commit = cała zmiana (squash), więc „commity” liczą zmiany, nie edycje. 30 z 90 commitów dotyka więcej niż 3 możliwości; to zwykłe PR-y funkcjonalne, nie osobne sygnały.
- Podział „przed / M-3”: granica e5ab295 włącznie; M-3 to 13 z 90 commitów.
- Modele Claude z trailerów są kontekstem pracy, nigdy osobami: nie ma ich w tabeli osób.
- **Korekta do kontraktu skanowania (`evidence`):** „85/90 z trailerem `Co-authored-by: Claude`” to liczba commitów z *jakimkolwiek* `Co-authored-by`. Trailer Claude ma 83/90; 2 kolejne (111ad27, 197b740) mają tylko trailer drugiej tożsamości tego samego operatora.
- Pokrycie: 1349 zmian kodu, nieprzypisane 0; 882 zmiany prozy raportowane osobno (false-signal 8).

## Obszary

Wszystkie możliwości o krytyczności high lub medium plus top 5 wg zmian. `model-evaluation` (low, poza top 5) i `docs-planning` (proza) dopisane tylko po to, żeby pytanie „każda możliwość” miało odpowiedź.

| Możliwość | Dlaczego w zakresie |
|---|---|
| pipeline-runs | high; najwięcej zmian kodu (168) |
| platform | high; zły apply IaC lub bramka CI może położyć produkcję; 165 zmian |
| llm-providers | high; koszt na wywołanie, zależą od niej inne możliwości; 131 zmian |
| assessment | high; każdy widok czyta jej wynik |
| ingest | high; wartość podstawowa, integracja zewnętrzna |
| auth | high; tożsamość, publiczne wejście bez sesji |
| accounts | high; tożsamość i dane użytkowników |
| feed | high; powód, dla którego użytkownicy przychodzą |
| db-backup | high; jedyna kopia na Railway Hobby |
| shared-foundations | medium; zależy od niej większość backendu; top 5 zmian kodu |
| admin-console | medium; uruchamia płatne przebiegi LLM; top 5 zmian kodu |
| shared-ui | medium; zależy od niej każdy widok |
| daily-run | medium; wejście chronione tokenem, codzienna świeżość |
| operator-alerts | medium; jedyny sygnał złej nocy poza aplikacją |

## People per capability

Każdy wiersz: jedna osoba, operator (`evidence`). Zamiast powtarzać nazwisko, tabela pokazuje grupy problemów. Grupy tematów to `inference` z tematów commitów i ścieżek.

| Możliwość | Zmiany (przed / M-3) | Commity (przed / M-3) | Okres | Grupy tematów | PR do przeczytania |
|---|---|---|---|---|---|
| pipeline-runs | 168 (126 / 42) | 19 (13 / 6) | 10-09..10-31 | start silnika (S-02), postęp i watchdog (S-06), liczenie prób (S-09), samoleczenie (S-11), wyjście z Embabel (S-17), izolacja źródeł (S-20), testy bez zegara (S-26) | #37, #60, #103, #110, #130 |
| platform | 165 (158 / 7) | 37 (35 / 2) | 09-26..10-30 | scaffold Railway, obraz API, `.railway/railway.ts` (22 commity) i `ci.yml` (17), bramka deployu (S-14, S-21), runnery, monitor po deployu; wczesny Python to historia (false-signal 11) | #81, #83, #99, #124 |
| llm-providers | 131 (131 / 0) | 11 (11 / 0) | 10-02..10-27 | klienci dostawców (F-02, S-02), błędy w treści odpowiedzi 200, fail-fast konfiguracji (S-10), awaria dostawcy a próby (S-09), przejście bez Embabel (S-17) | #31, #40, #82, #96 |
| shared-foundations | 110 (80 / 30) | 20 (14 / 6) | 10-02..10-31 | fundamenty przy rewrite (F-03), start persystencji (S-02, 27% zmian), ustawienia (S-10), zamknięte zbiory (S-23), kontrakt SPA↔API (S-24) | #37, #82, #126, #131 |
| admin-console | 105 (71 / 34) | 17 (12 / 5) | 10-09..10-30 | historia przebiegów i dzwonek (S-06), pusty przebieg (S-08), dni bez danych (S-13), wynik per źródło (S-20), „wymaga uwagi” (S-22) | #60, #80, #106, #128, #130 |
| assessment | 100 (100 / 0) | 7 (7 / 0) | 10-02..10-27 | kontrakt V2 jako kod (F-02: 76 z 100 zmian, jeden PR); reszta to drobne dostosowania | #31 |
| ingest | 99 (55 / 44) | 8 (6 / 2) | 10-06..10-30 | mapowanie Wykopu (F-02, S-02), kompletność Wykopu (S-16), izolacja źródeł (S-20), Reddit (S-27: 38 zmian, 38%) | #37, #94, #130, #132 |
| auth | 93 (91 / 2) | 13 (12 / 1) | 09-29..10-30 | logowanie i ochrona tras (S-01), przepisanie na Spring Security (F-03: 40% zmian), filtr Origin; `AuthorizationCompletenessIT` i router zmieniają się przy nowych trasach (po 6 commitów, mechaniczne) | #13, #26, #28 |
| feed | 87 (65 / 22) | 14 (11 / 3) | 09-29..10-31 | widok feedu (S-02, #48), szczegół posta (S-03), filtr (S-04), widoczne źródło (S-29), kontrakt (S-24) | #53, #54, #48, #133 |
| accounts | 81 (79 / 2) | 8 (7 / 1) | 09-29..10-30 | pierwszy admin przez CLI (S-01), przepisanie CLI (F-03), linki aktywacyjne i reset (S-07: 46% zmian) | #13, #26, #49 |
| shared-ui | 80 (70 / 10) | 21 (17 / 4) | 09-26..10-31 | bootstrap Vue, hardening panelu (#42) i feedu (#48), komponenty współdzielone dorastające z kolejnymi slice'ami | #42, #48 |
| daily-run | 34 (27 / 7) | 9 (5 / 4) | 10-13..10-31 | cron i token (S-05), pusty przebieg (S-08), check-iny (F-04), reguła adresu (S-25), świeżość (S-29) | #50, #95, #125, #133 |
| operator-alerts | 34 (29 / 5) | 5 (4 / 1) | 10-23..10-28 | kanał poza aplikacją (F-04: 56% zmian), kolejka zdarzeń (S-18), reguła adresu (S-25) | #95, #107, #125 |
| db-backup | 20 (16 / 4) | 9 (6 / 3) | 10-12..10-30 | backup i próba odtworzenia (F-01), kompletność sprawdzenia (S-15), check-iny (F-04), klasyfikacja tabel przy nowych migracjach | #44, #79, #95 |
| model-evaluation | 42 (31 / 11) | 8 (6 / 2) | 10-06..11-01 | próbka złota i nagrania (F-02), jakość i koszt Reddita (S-28) | #31, #134 |
| docs-planning (proza) | 882 (743 / 139) | 89 (76 / 13) | 09-26..11-01 | plany, runbooki, roadmapa; nie jest obszarem gorącym (false-signal 8) | — |

Daty to rok 2026. Commity liczone raz na możliwość, więc sumy przekraczają 90.

## Concentration

**Mały zespół — koncentracja jest linią bazową.** Pomijam język bus factor (false-signal 6, 4).

- Tylko jedna osoba dotykała **wszystkich 16 możliwości** (`evidence`). Żadna nie odróżnia się od reszty; poboczne tożsamości widać tylko w `platform` (3), `shared-ui` (3), `docs-planning` (9) i to wciąż ta sama osoba.
- GitHub to potwierdza: 85 PR-ów, wszystkie autorstwa operatora; 1 współpracownik repozytorium; 0 formalnych recenzji; brak CODEOWNERS. Komentarze: operator w 11 PR-ach, `railway-app` w 76 (automatyka statusu, nie recenzja, false-signal 7).
- `szczur-radar-monitor`: 16 commitów, jeden autor, 2026-10-20..23, repozytorium publiczne.

Sygnał, który zostaje po odjęciu linii bazowej: nie *kto*, tylko *gdzie* leży wiedza. Patrz sekcja „Wiedza poza repozytorium”.

## Agent co-authorship

Kontekst, nie filtr. Udział commitów z trailerem Claude:

| Możliwość | Udział | Możliwość | Udział |
|---|---|---|---|
| llm-providers | 11/11 (100%) | pipeline-runs | 18/19 (95%) |
| admin-console | 17/17 (100%) | shared-foundations | 19/20 (95%) |
| auth | 13/13 (100%) | feed | 13/14 (93%) |
| db-backup | 9/9 (100%) | docs-planning | 82/89 (92%) |
| accounts | 8/8 (100%) | platform | 33/37 (89%) |
| ingest | 8/8 (100%) | daily-run | 8/9 (89%) |
| model-evaluation | 8/8 (100%) | shared-ui | 17/21 (81%) |
| assessment | 7/7 (100%) | | |
| operator-alerts | 5/5 (100%) | **całe repo** | **83/90 (92%)** |

Niższe wyniki to głównie artefakt: 3 commity bootstrapu z 09-26, 2 commity tylko z trailerem człowieka (111ad27, 197b740) oraz dwa squashe bez trailera (d9171df S-29, 5b4ac92 M-3). Te dwa nie są pracą ręczną: wszystkie commity gałęzi #133 (10/10) i #135 (1/1) mają trailer Claude, a opisy PR-ów stopkę Claude Code (`evidence`). Faktyczny udział to **co najmniej 85/90**; 111ad27 i 197b740 mają opisy z tą stopką, ale commity bez trailera, więc pozostają `unknown`.

Modele według trailerów (dolna granica, bo squash potrafi je zgubić):

| Tydzień | Commity | Opus 5.5 | Opus 4.8 | Sonnet 5 / 5.5 | Fable 5.1 | Bez trailera |
|---|---|---|---|---|---|---|
| W39 | 15 | 1 | 8 | 2 / 0 | 0 | 4 |
| W40 | 9 | 8 | 0 | 0 | 0 | 1 |
| W41 | 9 | 8 | 0 | 0 / 1 | 0 | 0 |
| W42 | 18 | 18 | 0 | 0 | 0 | 0 |
| W43 | 23 | 23 | 0 | 0 | 0 | 0 |
| W44 | 16 | 13 | 0 | 0 | 4 | 2 |

Fable 5.1 to 4 commity: dwie największe zmiany silnika S-17 (e869a07) i S-20 (5a013ca), plus dwa zapisy roadmapy/dokumentów (d7c6113, a783195). Stąd w kodzie `pipeline-runs`, `ingest` i `shared-foundations` mają po dwa commity Fable, pozostałe możliwości jeden lub zero. 4 commity (od 2026-10-29) niosą w sumie 31 linii `Claude-Session`.

`inference`: kod pisze agent pod kierunkiem jednej osoby, więc „kto to napisał” nie wskazuje, kto pamięta powód decyzji. Powód jest w `context/` albo w sesji, nie w autorstwie.

## Wiedza poza repozytorium

Główne ustalenie tego pliku. Stan z poleceń `gh` i z `context/deployment/deploy-plan.md` przy HEAD; wartości sekretów nie były czytane.

| Co | Gdzie żyje | Co jest w repozytorium | Czego repozytorium nie powie | Dotyczy |
|---|---|---|---|---|
| Sealed zmienne produkcji: `LLM_API_KEY`, `WYKOP_KEY`, `WYKOP_SECRET`, `PIPELINE_TRIGGER_TOKEN`, `HEALTHCHECK_PING_URL` (3 usługi) | Railway, środowisko `production` | nazwy (`preserve()` w `.railway/railway.ts`), procedury w deploy-plan | wartości; zmiennej sealed nie da się odczytać, a jej usunięcia cofnąć (deploy-plan:1434) | llm-providers, ingest, daily-run, operator-alerts, db-backup |
| Sekrety GitHub: `LLM_API_KEY`, `RAILWAY_API_TOKEN`, `WYKOP_KEY`, `WYKOP_SECRET` | ustawienia repozytorium | nazwy w `ci.yml` | wartości, zakres tokena workspace | platform |
| Reguła konta Railway „deployment crashed” (wszystkie środowiska) | Account Settings, poza IaC | opis: deploy-plan:303, :480, :561, `.railway/railway.ts:269` | czy reguła nadal istnieje (kontrola tylko ręczna) | db-backup, daily-run |
| Synology Cloud Sync: bucket → NAS, NAS → Google Drive | DSM na NAS operatora | runbook deploy-plan:439–451 | stan zadań, czy lustro działa dziś; klucze bucketu są wpisane w DSM | db-backup |
| `szczur-radar-monitor` (Worker Cloudflare, `verify.yml`) | osobne repozytorium, Cloudflare, Railway webhook | opis i tabela sekretów: deploy-plan:1486–1527 | wartości `WEBHOOK_SECRET`, `GITHUB_DISPATCH_TOKEN`, `RAILWAY_API_TOKEN`, ping URL; **data wygaśnięcia PAT** | platform, operator-alerts |
| Przypomnienie o rotacji PAT monitora | kalendarz operatora (deploy-plan:1523) | sam fakt zalecenia | czy istnieje i na kiedy; wygasły PAT zatrzymuje monitor po cichu, Worker zwraca Railway 502 (deploy-plan:1527) | platform |
| Projekt healthchecks.io, integracja Telegram, `HEALTHCHECKS_API_KEY` | healthchecks.io, lokalny plik operatora (deploy-plan:1233) | checks jako kod w `tools/healthchecks/` | klucz, dostarczenie wiadomości (API tego nie mówi, deploy-plan:1265) | operator-alerts |
| Pula runnerów CI | Proxmox w domu, VM na PC operatora | `tools/ci-runner/`, runbook | stan bieżący: 7 zarejestrowanych, 1 online, 6 offline | platform |
| Konta dostawców (OpenRouter, Wykop, Railway Hobby) | panele dostawców | zmienne i runbooki | limity wydatków i właściciel konta poza runbookiem: `unknown` | llm-providers, ingest, platform |
| Pamięć agenta i sesje Claude | katalog domowy operatora, claude.ai | linki `Claude-Session` w 4 commitach | treść sesji, czyli rozważane i odrzucone warianty, które nie trafiły do `context/` | wszystkie (powody decyzji) |

**Rozjazd dokumentacji ze stanem poza repozytorium (`evidence`).** `REPOSITORY_GUIDELINES.md` mówi „Since 2026-10-18 … `CI_RUNNER=szczur-ci`”, ale zmiennej `CI_RUNNER` nie ma na liście zmiennych repozytorium (są tylko `LLM_API_PROVIDER`, `LLM_MODEL`, `REDDIT_SUBREDDITS`), a ostatni przebieg CI (push na `main`, 2026-11-01 07:32) działał w całości na `ubuntu-latest`. Przełącznik celowo działa bez commita, więc repozytorium tego nie zobaczy. Powód i moment zmiany: `unknown`.

**Przeciwwaga (`evidence`).** `deploy-plan.md` ma 2301 linii i zmieniło go 55 z 90 commitów. Większość pozycji z tabeli ma runbook; poza repozytorium zostają głównie wartości i bieżący stan, nie sposób działania.

`inference`: agent nie zweryfikuje samodzielnie tych pozycji, które wymagają dostępu konta (reguła Railway, NAS, Telegram, data PAT). Każda zmiana, która od nich zależy, kończy się krokiem operatora.

## Implications

Adresat jest zawsze ten sam, więc różnica leży w tym, *o co* zapytać przed zmianą.

- **Sekret lub nowa zmienna na produkcji:** operator ustawia ją przed deployem; nowa zmienna ręczna bez wzorca w `variables.ignore` lub `preserve()` zatrzymuje deploye na `main`. Przeczytaj #44, #81.
- **`db-backup`, `pipeline-trigger` (exit code, harmonogram, timeout):** zapytaj, czy reguła „deployment crashed” stoi, i przypomnij o ponownym `checks.sh apply`. Przeczytaj #44, #50, #95.
- **Format lub położenie kopii bazy:** NAS czyta bucket przez Cloud Sync; zmiana układu `daily/` i `monthly/` dotyka zadania DSM, którego repozytorium nie widzi. Przeczytaj #44, #79.
- **Zmiana weryfikacji po deployu:** kod siedzi w `szczur-radar-monitor`; czerwony run tam nie rusza Wait for CI. Zapytaj o datę PAT. Przeczytaj #99.
- **Silnik przebiegów:** zacznij od #110 i #130, bo przebudowały przepływ; #37 i #60 opisują już częściowo nieaktualny stan (`inference`).
- **Kontrakt oceny:** praktycznie cała wiedza w jednym PR #31, a w M-3 zero zmian. Przed zmianą czytaj go razem z planem w `context/archive/`.
- **CI i runnery:** nie wnioskuj o stanie z dokumentacji; sprawdź `gh variable list` i runnery.

## Unknowns

- `unknown`: formalna własność — brak CODEOWNERS i wiązań poza faktem, że operator jest jedynym współpracownikiem.
- `unknown`: czy reguła Railway, zadania Cloud Sync i integracja Telegram działają dziś; repozytorium ma tylko wyniki z dnia wdrożenia. Odpowiada operator.
- `unknown`: data wygaśnięcia `GITHUB_DISPATCH_TOKEN` oraz limity wydatków u dostawców.
- `unknown`: dlaczego zniknął `CI_RUNNER` i czy to stan docelowy.
- `unknown`: autorstwo modeli przed squashem. Trailery są dolną granicą; 111ad27 i 197b740 mają stopkę w opisie PR, ale nie w commitach.
- `unknown`: treść recenzji. `gh` nie pokazuje formalnych recenzji, a przeglądy `/10x-impl-review` żyją w `context/`; to materiał agenta dyskusji, nie tego pliku.
- `unknown`: czy lista sealed jest kompletna. Railway nie był odpytywany; lista pochodzi z deploy-plan i `.railway/railway.ts`.

## Polecenia

- `python3 -I` na `.work/commit-files.tsv` z `git log --format=%h…%B` (skrypty w scratchpadzie; surowe tematy: `.work/people-subjects.tsv`)
- `git -c core.quotepath=off log --format=…%(trailers:…)`, `rev-list --count e5ab295^..5b4ac92`
- `git -c core.quotepath=off grep` i `git show 5b4ac92:context/deployment/deploy-plan.md` (tylko potwierdzenie trafień)
- `gh pr list --json author,reviews,comments`, `gh pr view 133|135|12|4`, `gh api …/pulls/N/commits`, `gh api …/collaborators`
- `gh secret list` / `gh variable list` (tylko nazwy, oba repozytoria), `gh api …/actions/runners`, `gh run list`, `gh api …/runs/<id>/jobs`
- `gh repo view` i `gh api` (`commits`, `git/trees`, `readme`, `environments`) dla `szczur-radar-monitor`; `gh issue view 121`, `gh issue list --state open`
