# S-19: dokumenty operatora mówią prawdę — plan implementacji

## Overview

Operator ma czytać runbooki i stan, które opisują produkcję taką, jaka jest. `deploy-plan.md` traci nieaktualne statusy „not merged” i martwe linki do zmian przeniesionych do archiwum. Sekcje z czasów FastAPI dostają notkę „historyczne”. Tabela JVM dostaje prawdziwy pomiar przy obecnych limitach. `infrastructure.md` przestaje mówić o LangGraph. Trzy martwe zmienne IaC (`RAILPACK_JDK_VERSION`, `DATABASE_URL`, `JWT_SECRET`) znikają z Railway i z `.railway/railway.ts` bez czerwonej bramki. Rozstrzygnięcie Hikari (#36) i zamknięcie #51 trafiają do dokumentu. Issue: #78.

## Current State Analysis

- **Nieaktualne statusy w `context/deployment/deploy-plan.md`:** każdy z tych PR-ów jest zmergowany, a issue zamknięte.
  - S-02 `:172` „in the PR env (#37), not merged” — `1a33fcc`, issue #14;
  - wzmianka o S-07 `:491` „PR #49, not merged” — `54c4fc2`;
  - S-04 `:685` „verified in the PR env (#54), not merged” — `c80e846`, issue #17;
  - self-hosted runner `:859` „in PR #83 (issue #30), not merged” — `35c47a2`;
  - S-14 `:889` „in the PR env (#81), not merged” — `2abc64c`, issue #73;
  - S-15 `:1041` „in progress, not merged” — `1a6d300`, issue #74;
  - S-08 `:1103` „in progress … not merged” — `a168731`, issue #67.
  - Sekcja S-06 (`:711-713`) jest już aktualna, ale roadmapa wymieniała ją jako nieaktualną. Pozostałe sekcje (S-03, S-05, F-01, S-07) mają kroki „after the merge”, które trzeba sprawdzić pod kątem wskazań typu „not merged” i „in progress”.
- **Martwe linki `context/changes/<id>/…` w `deploy-plan.md` (11):** linie `:30, :99, :138, :239, :685, :691, :782, :810, :1041, :1064, :1103`. Cele leżą dziś w `context/archive/<data>-<id>/…`. Jedyny inny martwy link poza archiwum jest w `context/foundation/lessons.md:57`, ale `lessons.md` jest tylko do dopisywania, więc go nie ruszamy.
- **Sekcje o FastAPI/Hypercorn/`uv run` w `deploy-plan.md`:**
  - `## Release status (2026-09-27)` (`:9`), `## S-01 release` (`:28`, m.in. sekret `JWT_SECRET` w `:36`), `## Phases` (`:1172`, fazy 1–6 ze scaffoldu), `## Summary` (`:5`).
  - Sekcja F-03 (`:40`) i późniejsze opisują już Javę.
- **Tabela JVM (`deploy-plan.md:108-119`, „measured 2026-10-06”):**
  - pomiar był przy limicie ≈ 954 MiB: heap ≈ 716 MiB, SerialGC, `nproc` 48;
  - dziś `API_LIMITS = limits(2, 2_000_000_000)` (`.railway/railway.ts:57`), a w `ENTRYPOINT` jest `-XX:MaxRAMPercentage=75` (`szczur-radar-api/Dockerfile:25`);
  - audyt C13 przewiduje ~1,5 GB heapu i G1, ale nikt tego nie zmierzył.
- **`context/foundation/infrastructure.md`:**
  - `:38` (Recommendation): „a cron service for the daily LangGraph pipeline”. Pipeline działa w procesie API, a cronem jest `pipeline-trigger`.
  - `:115` (Secrets): `DATABASE_URL` to referencja do Postgresa, a w GitHub jest tylko `RAILWAY_TOKEN`. To nieaktualne: API czyta `PG*`, a CI używa tokena workspace `RAILWAY_API_TOKEN` (lessons.md:33-38).
  - Porównanie platform (`:40-…`) jest zapisem decyzji z 2026-09-26 dla Pythona.
- **Zmienne IaC (`.railway/railway.ts`):**
  - `RAILPACK_JDK_VERSION: "25"` (`:127`), `DATABASE_URL: db.env.DATABASE_URL` (`:137`) i `JWT_SECRET: preserve()` (`:141`), każda z komentarzem „zostaje, bo bramka odrzuca `variable.delete`” (`:124-126, :135-136, :138-140`);
  - kod aplikacji nie używa żadnej z nich; `application.properties:7` i `szczur-radar-api/README.md:129` tylko wspominają, że API nie parsuje `DATABASE_URL`, i to zostaje.
- **#36 (Hikari) i #51:** oba zamknięte 2026-10-18.
  - #36: od 2026-10-13 nie ma ostrzeżeń; ostatnie pokrywają się z restartami Postgresa 2026-10-12; konfiguracji `spring.datasource.hikari.*` nie zmieniamy.
  - #51: `limitOverride` na produkcji zgadza się z IaC.
  - `deploy-plan.md` nie zapisuje żadnego z tych rozstrzygnięć.
- **Roadmapa:** S-19 `ready`. Zgodnie z regułą repo SVG roadmapy (`tools/render_roadmap_svg.py:174, :286`) trzeba aktualizować razem z `roadmap.md`.

## Desired End State

- W `deploy-plan.md` żaden status nie mówi „not merged” ani „in progress” o zmianie, której PR jest zmergowany. Każda istniejąca linia Status jest prawdziwa (data, commit merge'a, stan issue). W sekcjach, które nie mają linii Status, nowych nie dopisujemy.
- Każdy link `context/changes/<id>/…` w `deploy-plan.md` i `infrastructure.md` prowadzi do istniejącej ścieżki.
- Sekcje historyczne (Summary, Release status, S-01, Phases) zaczynają się jedną linią „Historyczne (stan z <data>); dziś obowiązuje <sekcja/plik>”, a ich treść zostaje bez zmian.
- Tabela JVM pokazuje wartości zmierzone w PR env przy `API_LIMITS` (2 vCPU / 2 GB), z datą i adnotacją „PR env, te same limity co produkcja”. Wiersze, których nie mierzyliśmy ponownie, zachowują datę 2026-10-06.
- `infrastructure.md` nie opisuje LangGraph jako bieżącego stanu. Linia Secrets opisuje dzisiejsze zmienne i tokeny.
- `.railway/railway.ts` nie deklaruje trzech zmiennych. PR env i produkcja ich nie mają. `railway-config` w PR i na `main` jest zielone przy planie bez `variable.delete`.
- `deploy-plan.md` ma sekcję S-19: rozstrzygnięcie Hikari, #51, usunięcie zmiennych i runbook kontroli po merge'u.

Weryfikacja: `grep` (kryteria niżej), zielone CI na PR, nazwy zmiennych w obu środowiskach (`jq 'keys'`).

### Key Discoveries:

- `railway variable delete` nie ma `--skip-deploys`, a `variable set` ma. Usunięcie zmiennej może przedeployować API, a deploy przerywa trwający przebieg pipeline'u (REPOSITORY_GUIDELINES, Deployment).
- `railway variable list --json` zwraca surowe wartości. Do sprawdzania bierzemy wyłącznie `| jq 'keys'`.
- PR env nie dziedziczy zapieczętowanych wartości (`deploy-plan.md:36`, `context/archive/2026-09-27-admin-bootstrap-and-login/plan.md:33`), więc `JWT_SECRET` najpewniej nie istnieje w PR env.
- Kolejność względem bramki obowiązuje w każdym środowisku osobno:
  - plik bez zmiennej i środowisko ze zmienną daje `variable.delete`, którego bramka odmawia;
  - środowisko bez zmiennej i plik z nią daje `variable.set` (dozwolone przez bramkę, README bramki :59), które przechodzi i odtwarza `DATABASE_URL` i `RAILPACK_JDK_VERSION`; `JWT_SECRET` jako `preserve()` się nie odtworzy.
- Merge tej zmiany nie deployuje API: zmienia tylko `.railway/` i `context/`, a API ma `watchPatterns: ["szczur-radar-api/**"]` (`.railway/railway.ts:112`).
- Obraz runtime to JRE (`eclipse-temurin:25-jre-noble`), bez `jcmd`. Drugi proces `java` w tym samym kontenerze widzi tę samą cgroup, więc dostaje tę samą ergonomię heapu i GC.

## What We're NOT Doing

- Nie zmieniamy konfiguracji Hikari (`max-lifetime`, `keepalive-time`). #36 jest zamknięte decyzją z dowodem z logów.
- Nie ruszamy `context/archive/**` ani `context/foundation/lessons.md` (martwy link `:57` zostaje, bo plik jest tylko do dopisywania).
- Nie przepisujemy sekcji historycznych na stan Javy i ich nie usuwamy. Dostają tylko notkę.
- Nie zmieniamy bramki CI (`tools/deploy-gate/`), żeby przepuściła `variable.delete`. Nie dodajemy usuwanych zmiennych do `variables.ignore`, bo zostałyby w Railway na zawsze.
- Nie mierzymy JVM na produkcji. Nie mierzymy ponownie rozmiaru obrazu, czasu budowania ani zimnego startu.
- Nie ruszamy innych pozycji audytu (C1–C11, C14) ani skilli i promptów w `.claude/` / `.agents/` z linkami przykładowymi.
- Nie zamykamy #78 przed weryfikacją produkcji po merge'u.

## Implementation Approach

Najpierw same dokumenty (faza 1). Ten commit zakłada gałąź `feature/ops-docs-debt` i PR, a od nich powstaje PR env. Potem PR env (faza 2): pomiar JVM, usunięcie zmiennych w PR env i od razu commit bez nich w `railway.ts`, aż CI będzie zielone. Na końcu (faza 3) sekcja S-19 w `deploy-plan.md` z dwoma runbookami: „przed merge'em” (agent usuwa zmienne na produkcji po wyraźnym potwierdzeniu człowieka) i „po merge'u”. Oba runbooki wykonuje się po `/10x-impl-review` i `/10x-archive` na tej samej gałęzi, tuż przed merge'em człowieka i po nim. Dlatego nie są wierszami Progress, tylko krokami śledzonymi komentarzami w #78 (lessons.md:75-80: archiwum jest niezmienne, a merge jest ostatni).

## Critical Implementation Details

**Kolejność w PR env.** Usuń zmienne w PR env i dopiero wtedy pushuj commit, który usuwa je z `railway.ts`. Między tymi krokami nie może być żadnego pusha ze starym plikiem, bo `railway-config` odtworzyłby `DATABASE_URL` i `RAILPACK_JDK_VERSION`. Jeśli tak się stanie, usuń je ponownie przed następnym pushem.

**Okno na produkcji.** Usuń zmienne na produkcji dopiero po `/10x-archive`, gdy PR jest gotowy do merge'a i człowiek jest gotów zmergować od razu. Każdy inny merge do `main` w międzyczasie odtworzy dwie zmienne, a wtedy apply po merge'u S-19 odmówi `variable.delete`. W takim przypadku usuń je ponownie przed merge'em S-19. To samo dotyczy innych otwartych PR-ów (dziś #92): ich PR env jest kopią produkcji sprzed usunięcia, więc gdy ich gałąź wciągnie `main` z S-19, plan pokaże `variable.delete` i bramka odmówi. Usuń 3 zmienne w każdym istniejącym PR env, zanim jego gałąź wciągnie `main`. Przed usunięciem: `GET <web>/api/deploy-safety` zwraca `safe:true`, godzina jest poza 00:30–01:30 UTC (przebieg dzienny) i 01:50–02:30 UTC (backup), a człowiek potwierdził właśnie ten krok.

## Phase 1: Dokumenty operatora

### Overview

Start pracy (gałąź, PR, issue) i wszystkie poprawki dokumentów, które nie potrzebują PR env ani produkcji.

### Changes Required:

#### 1. Start zmiany

**File**: gałąź, PR, issue #78, `context/changes/ops-docs-debt/`

**Intent**: Zgodnie z workflow repo: jedna gałąź, jeden PR i jedno issue od pierwszego commitu.

**Contract**:
- `git fetch`, potem gałąź `feature/ops-docs-debt` od `origin/main`. Obecny worktree jest już na tej gałęzi; sprawdź, że wychodzi od świeżego `origin/main`.
- Otwarty PR #92 zmienia `deploy-plan.md` (runbook S-14) i usuwa `deploy-verify`. Przed edycjami `deploy-plan.md` w każdej fazie: `git fetch` i rebase na `origin/main`. Konflikty w `deploy-plan.md` rozwiązuj na korzyść treści z `main`, a poprawki S-19 nakładaj na nią.
- Pierwszy commit zawiera folder zmiany (`change.md`, `plan.md`, `plan-brief.md`).
- PR ma w opisie `Refs #78`. Komentarz w #78 podaje change id, gałąź i link do PR.

#### 2. Statusy i linki w `deploy-plan.md`

**File**: `context/deployment/deploy-plan.md`

**Intent**: Każdy status zmiany ma mówić, co jest prawdą dziś, a każdy link ma prowadzić do istniejącego pliku.

**Contract**:
- Linie statusu S-02 `:172`, S-04 `:685`, runner `:859`, S-14 `:889`, S-15 `:1041` i S-08 `:1103` przyjmują wzorzec S-06 (`:713`): „**Status (<data>): live on production** after the human merge of PR #N (`<sha>`); issue #M closed.”
  - Datę merge'a i SHA bierzemy z `gh pr view N --json mergeCommit,mergedAt`.
  - Stan issue bierzemy z `gh issue view`.
  - Jeśli issue jest zamknięte, a weryfikacja produkcji nie jest opisana w sekcji, dopisz krótko, gdzie jest (komentarz zamykający issue), bez wymyślania wyników.
- `:491` (wzmianka o S-07 „not merged”): stwierdzenie w czasie przeszłym, że S-07 zmergowano jako `54c4fc2`. Reguła o numerach migracji zostaje.
- Przejrzyj pozostałe sekcje (S-03, S-05, F-01, S-07, F-02, api-docker-image) pod kątem fraz „not merged”, „in progress”, „pending” i popraw te, które opisują dziś zamknięty stan.
- 11 linków `context/changes/<id>/…` zamień na `context/archive/<data>-<id>/…`, sprawdzając, że cel istnieje (`ls`). Link z wildcardem (`ui-after/pr-env-*.png`) wskazuje katalog w archiwum.

#### 3. Notki „historyczne”

**File**: `context/deployment/deploy-plan.md`

**Intent**: Sekcje z czasów FastAPI zostają jako zapis, ale nie mogą udawać bieżącego runbooka.

**Contract**:
- Pod nagłówkami `## Summary`, `## Release status (2026-09-27)`, `## S-01 release …` i `## Phases` jedna linia w stylu `tech-stack-api.md:39`: „Historyczne (stan z <data>, backend FastAPI); dziś obowiązuje: <sekcja F-03 / api-docker-image / S-14 …>.”
- Treść sekcji bez zmian.

#### 4. Rozstrzygnięcie Hikari i #51 (zapis)

**File**: `context/deployment/deploy-plan.md` (sekcja S-19 powstaje w fazie 3; tu tylko wpis w sekcji api-docker-image)

**Intent**: Operator, który zobaczy ostrzeżenie Hikari, ma w runbooku przyczynę i decyzję.

**Contract**: W sekcji `## api-docker-image` (Troubleshooting albo osobny akapit) wpis o tym, że:
- ostrzeżenia `Failed to validate connection` zniknęły od 2026-10-13, po wyłączeniu usypiania Postgresa;
- pojedyncza seria po restarcie bazy jest oczekiwana;
- konfiguracji nie zmieniamy;
- powrót ostrzeżeń bez restartu bazy oznacza ponowne otwarcie #36.
`#51` dostaje wpis w sekcji S-19 (faza 3).

#### 5. `infrastructure.md`

**File**: `context/foundation/infrastructure.md`

**Intent**: Bieżące zdania opisują dzisiejszą topologię. Porównanie platform zostaje jako zapis decyzji.

**Contract**:
- `:38`: usługi to `szczur-radar-api` (Dockerfile, pipeline w procesie API od S-02), `szczur-radar-web`, crony `pipeline-trigger` (00:30 UTC) i `db-backup` (02:00 UTC), bucket `db-backups`, Postgres. Bez LangGraph.
- `:115` Secrets:
  - API czyta referencje `PG*`;
  - sekrety LLM i Wykop są per środowisko (produkcja zapieczętowana, PR env z GitHub secrets przez `railway-config`);
  - CI używa tokena workspace `RAILWAY_API_TOKEN` z `RAILWAY_PROJECT_ID` i `RAILWAY_ENVIRONMENT_ID`;
  - sprawdź z `.github/workflows/ci.yml` przed zapisem.
- Pod `## Platform Comparison` notka „Historyczne (2026-09-26, backend w Pythonie): kryteria i oceny dotyczą tamtego stacku; wybór Railway obowiązuje.”
- Frontmatter `updated`/daty bez zmian, chyba że plik ma pole do tego.

#### 6. Roadmapa i SVG

**File**: `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: S-19 w realizacji; SVG zgodny z roadmapą (reguła repo).

**Contract**: S-19 `Status: in-progress` w tabeli At a glance i w treści wycinka, `updated:` dzisiejsza data. W `render_roadmap_svg.py` S-19 → `"progress"` (`:174`, `:286`; słownik `STATUS` w `:26-32` zna tylko `done`, `progress`, `ready`, `proposed`, `blocked`). Potem `python3 tools/render_roadmap_svg.py`.

### Success Criteria:

#### Automated Verification:

- Brak statusów „not merged” w `deploy-plan.md`: `grep -n -i 'not merged' context/deployment/deploy-plan.md` zwraca tylko linie w sekcjach historycznych albo nic
- Każdy link `context/changes/<id>` w `deploy-plan.md` i `infrastructure.md` wskazuje istniejący katalog (pętla `grep -o` + `test -d`, 0 braków)
- `grep -n 'LangGraph' context/foundation/infrastructure.md` zwraca tylko linie w sekcji oznaczonej jako historyczna
- SVG wygenerowany: `python3 tools/render_roadmap_svg.py` kończy się 0, a `git diff --stat docs/visuals/roadmap.svg` pokazuje zmianę
- PR otwarty z `Refs #78`, komentarz startowy w #78

#### Manual Verification:

- Człowiek czyta zmienione statusy i notki w `deploy-plan.md` i potwierdza, że opisują stan faktyczny

**Implementation Note**: Po zielonych kontrolach automatycznych zatrzymaj się na potwierdzenie człowieka przed fazą 2.

---

## Phase 2: PR env — pomiar JVM i usunięcie zmiennych

### Overview

Prawdziwy pomiar JVM przy obecnych limitach i usunięcie trzech zmiennych z PR env oraz z `railway.ts`, aż CI będzie zielone.

### Changes Required:

#### 1. Pomiar JVM w PR env

**File**: `context/deployment/deploy-plan.md` (tabela „Memory, build and cold start”, `:108-119`)

**Intent**: Tabela opisuje kontener przy `API_LIMITS`, a nie przy starym limicie 1 GB.

**Contract**:
- Najpierw MCP `get-service-config` dla `szczur-radar-api` w PR env: `limitOverride` = 2 vCPU / 2 GB. Jeśli jest inaczej, zatrzymaj się i zgłoś.
- Potem `railway ssh -s szczur-radar-api -e <PR env>`:
  - `cat /sys/fs/cgroup/memory.max`, `cat /sys/fs/cgroup/cpu.max`, `nproc`;
  - `java -XX:MaxRAMPercentage=75 -XX:+PrintFlagsFinal -version | grep -E ' (MaxHeapSize|UseSerialGC|UseG1GC|ActiveProcessorCount) '`.
- Nagłówek tabeli: „measured <data>, PR env `szczur-radar-pr-<N>`, `API_LIMITS` 2 vCPU / 2 GB — the same limits as production (`.railway/railway.ts`)”. Wiersze, których nie mierzyliśmy (obraz, build, start, pierwsze żądanie), dostają adnotację „(2026-10-06)”.
- Zdanie o GC dopasuj do wyniku. Nie zakładaj z góry, że to G1.

#### 2. Usunięcie zmiennych w PR env

**File**: Railway, PR env tego PR-a, usługa `szczur-radar-api`

**Intent**: Środowisko bez zmiennych przed commitem, który usuwa je z pliku, więc plan nie pokaże `variable.delete`.

**Contract**:
- `railway variable delete <KEY> -s szczur-radar-api -e <PR env>` dla `RAILPACK_JDK_VERSION`, `DATABASE_URL` i `JWT_SECRET`.
- Błąd „not found” dla `JWT_SECRET` traktujemy jako sukces. Każdy inny błąd: zatrzymaj się.
- Sprawdzenie: `railway variable list -s szczur-radar-api -e <PR env> --json | jq 'keys'`. Nigdy bez `jq 'keys'`.
- Ustal i zapisz skutek usunięcia: MCP `get-staged-changes` (czy zmiana czeka jako staged) i `list-deployments` dla `szczur-radar-api` (czy powstał nowy deployment). Wynik trafia do komentarza w #78 i do runbooka „before the merge” (faza 3), który na nim się opiera zamiast na „może przedeployować”. Jeśli zmiana jest staged, runbook mówi, jak ją zatwierdzić.

#### 3. `railway.ts` bez trzech zmiennych

**File**: `.railway/railway.ts`

**Intent**: IaC deklaruje tylko zmienne, których API używa.

**Contract**:
- Usuń `RAILPACK_JDK_VERSION`, `DATABASE_URL` i `JWT_SECRET` razem z ich komentarzami (`:124-127, :135-141`). Komentarz przy `PG*` („no DATABASE_URL parsing”) zostaje.
- Nie dodawaj ich do `variables.ignore`.
- Push zaraz po kroku 2.

### Success Criteria:

#### Automated Verification:

- `grep -n -E 'RAILPACK_JDK_VERSION|DATABASE_URL:|JWT_SECRET' .railway/railway.ts` nic nie zwraca
- PR env bez trzech zmiennych: `railway variable list -s szczur-radar-api -e <PR env> --json | jq 'keys'` nie zawiera żadnej z nich
- Wszystkie workflow na commicie PR zielone, w tym `railway-config` (plan PR env bez `variable.delete`, bramka przepuszcza), i brak nowego alertu po deployu (`deploy-verify`/issue `deploy-health` albo monitor z #92, zależnie od tego, co jest na `main`)
- `GET <PR env web>/api/ready` zwraca 200 po deployu
- Tabela JVM w `deploy-plan.md` ma datę pomiaru z tej fazy i wartości `memory.max`, `nproc`, `MaxHeapSize`, GC
- Skutek `railway variable delete` w PR env (staged czy nowy deployment) zapisany w komentarzu #78

#### Manual Verification:

- Człowiek przegląda tabelę JVM i potwierdza, że wnioski (heap, GC) zgadzają się z wynikiem pomiaru

**Implementation Note**: Po zielonych kontrolach automatycznych zatrzymaj się na potwierdzenie człowieka przed fazą 3.

---

## Phase 3: Produkcja przed merge'em i przekazanie

### Overview

Sekcja S-19 w `deploy-plan.md` z runbookami „przed merge'em” i „po merge'u”. Sam krok produkcyjny agent wykonuje po `/10x-archive`, tuż przed merge'em człowieka, i raportuje w #78.

### Changes Required:

#### 1. Sekcja S-19 w `deploy-plan.md`

**File**: `context/deployment/deploy-plan.md`

**Intent**: Operator ma w jednym miejscu, co S-19 zmieniło, czego nie zmieniło i co sprawdzić po merge'u.

**Contract**: Nowa sekcja `## S-19: dokumenty operatora mówią prawdę (ops-docs-debt)`, w stylu sekcji S-14/S-15. Zawiera:
- status w formie prawdziwej także po merge'u, bez „not merged” i „in progress”: „**Status:** delivered by PR #N; production verification and closure in issue #78.”;
- co zmienia: statusy, linki, notki, JVM, `infrastructure.md`, zmienne;
- Hikari (#36) jako decyzję z odsyłaczem do wpisu z fazy 1;
- #51 (limity zgodne z IaC, zamknięte 2026-10-18);
- usunięte zmienne i kolejność „najpierw środowisko, potem plik”, jako runbook na przyszłe usuwanie zmiennej deklarowanej w IaC;
- `### Runbook — after the merge (agent/operator; tracked in issue #78, not in the plan's Progress)`:
  - `railway-config` na `main` zielone;
  - produkcja bez trzech zmiennych (`jq 'keys'`);
  - API nieprzedeployowane przez merge, a jeśli przedeployowane, to `/api/ready` 200;
  - otwarte PR-y (`gh pr list --state open`): w każdym istniejącym PR env usunięte 3 zmienne (sprawdzenie przez `jq 'keys'`), zanim jego gałąź wciągnie `main`; jeśli zrobiono to już w runbooku „before the merge”, tylko sprawdzenie;
  - zamknięcie #78 z podsumowaniem.
- jedno zdanie dla przyszłych PR-ów: PR env utworzony przed S-19 ma jeszcze 3 zmienne; usuń je w nim, zanim gałąź wciągnie `main`.

#### 2. Lekcja: status w `deploy-plan.md` odsyła do issue

**File**: `context/foundation/lessons.md` (dopisanie przez `/10x-lesson`)

**Intent**: Usunąć przyczynę długu. Wszystkie 8 nieaktualnych statusów powstało, bo status pisano na gałęzi przed merge'em („not merged”), a po merge'u nikt go nie poprawiał.

**Contract**: Nowy wpis na końcu `lessons.md` (plik tylko do dopisywania), `Applies to: plan, implement, impl-review, deploy`. Reguła: linia Status sekcji zmiany w `deploy-plan.md` ma być prawdziwa także po merge'u („delivered by PR #N; production verification and closure in issue #M”), nigdy „not merged” ani „in progress”. Stan produkcji żyje w issue.

#### 3. Runbook „before the merge”: usunięcie zmiennych na produkcji

**File**: `context/deployment/deploy-plan.md` (podsekcja `### Runbook — before the merge (agent, on the human's confirmation; tracked in issue #78, not in the plan's Progress)` w sekcji S-19); wykonanie: Railway, `production`, usługa `szczur-radar-api`

**Intent**: Środowisko produkcyjne bez zmiennych w chwili merge'a, więc apply na `main` nie zobaczy `variable.delete`. Runbook zapisuje się w tej fazie, a wykonuje po `/10x-archive`.

**Contract**:
- Warunki wstępne, wszystkie razem:
  - PR jest gotowy (CI zielone, fazy 1–2 potwierdzone);
  - `GET https://<web prod>/api/deploy-safety` zwraca `{"safe":true}`;
  - godzina jest poza 00:30–01:30 i 01:50–02:30 UTC;
  - człowiek wyraźnie potwierdził w tej chwili: „usuń teraz na produkcji”.
- Potem `railway variable delete <KEY> -s szczur-radar-api -e production` dla trzech kluczy. `JWT_SECRET` jest zapieczętowane i jego usunięcie jest nieodwracalne, ale nic go nie używa.
- Sprawdzenie przez `jq 'keys'`.
- Te same 3 zmienne usuń w PR env każdego innego otwartego PR-a (`gh pr list --state open`; dziś #92), z tym samym sprawdzeniem. To PR env, bez warunków produkcyjnych, ale wynik idzie do komentarza w #78 i w PR-ze, którego dotyczy.
- Skutek usunięcia według wyniku z fazy 2: jeśli zmiana jest staged, zatwierdź ją zgodnie z zapisanym sposobem; jeśli wywołała deploy API, poczekaj na `/api/ready` 200.
- Następnie komunikat do człowieka: „merge teraz; każdy inny merge przed tym odtworzy `DATABASE_URL`/`RAILPACK_JDK_VERSION`”.
- Agent nie merguje.
- Wynik (nazwy zmiennych po usunięciu, `/api/ready`, godzina) trafia jako komentarz do #78.

### Success Criteria:

#### Automated Verification:

- Sekcja `## S-19` istnieje w `deploy-plan.md` i zawiera runbooki „before the merge” i „after the merge” z odwołaniem do #78
- Runbook „before the merge” wymienia wszystkie warunki wstępne (`deploy-safety`, okna 00:30–01:30 i 01:50–02:30 UTC, potwierdzenie człowieka) i sprawdzenie przez `jq 'keys'`
- Status sekcji S-19 nie zawiera „not merged” ani „in progress” i odsyła do #78
- `lessons.md` ma na końcu nowy wpis o statusach w `deploy-plan.md` (żadna wcześniejsza linia pliku nie zmieniona: `git diff` pokazuje tylko dodane linie)

#### Manual Verification:

- Człowiek przegląda oba runbooki S-19 i potwierdza kolejność: impl-review → archive → usunięcie na produkcji → merge

**Implementation Note**: Usunięcie zmiennych na produkcji, merge i kontrola po merge'u nie są wierszami Progress. Agent wykonuje runbook „before the merge” po `/10x-archive`, gdy człowiek potwierdzi gotowość do merge'a, a wyniki obu runbooków zapisuje w komentarzach #78.

---

## Testing Strategy

### Unit Tests:

- Brak: zmiana nie dotyka kodu aplikacji.

### Integration Tests:

- CI na PR jako całość: `railway-config` (plan i bramka IaC dla PR env), `deploy-gate-test`, `api-container-smoke` i pozostałe joby bez zmian w zachowaniu; kontrola po deployu (`deploy-verify` albo monitor z #92).

### Manual Testing Steps:

1. Przeczytać zmienione sekcje `deploy-plan.md` i `infrastructure.md` z perspektywy operatora.
2. Sprawdzić, że tabela JVM pokazuje wynik pomiaru, nie założenie.
3. Po `/10x-archive`, przed merge'em: runbook „before the merge” (produkcja bez trzech zmiennych, `/api/ready` 200).
4. Po merge'u: `railway-config` na `main` zielone (runbook „after the merge”).

## Migration Notes

Wycofanie: przywrócenie zmiennych to `variable set` (dla `DATABASE_URL` referencja `${{postgres.DATABASE_URL}}`) i revert `railway.ts`. `JWT_SECRET` po usunięciu nie da się odzyskać, ale nic go nie używa od F-03. Gdyby kiedyś był potrzebny, generuje się nowy.

## References

- Roadmapa: `context/foundation/roadmap.md` § S-19; issue #78
- Audyt: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (C12, C13, E „IaC”, F, „Nieaktualna dokumentacja”)
- Issues #36, #51 (zamknięte 2026-10-18, komentarze zamykające)
- Bramka i zmienne ignorowane: `.railway/railway.ts:280-316`, `context/deployment/deploy-plan.md` § S-14
- Lekcje: `context/foundation/lessons.md:26-31, :33-38, :75-80, :99-111`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Dokumenty operatora

#### Automated

- [x] 1.1 Brak statusów „not merged” w `deploy-plan.md` poza sekcjami historycznymi
- [x] 1.2 Każdy link `context/changes/<id>` w `deploy-plan.md` i `infrastructure.md` wskazuje istniejący katalog
- [x] 1.3 `LangGraph` w `infrastructure.md` tylko w sekcji oznaczonej jako historyczna
- [x] 1.4 SVG roadmapy wygenerowany ze statusem S-19 in-progress
- [ ] 1.5 PR otwarty z `Refs #78`, komentarz startowy w #78

#### Manual

- [ ] 1.6 Człowiek potwierdza, że zmienione statusy i notki opisują stan faktyczny

### Phase 2: PR env — pomiar JVM i usunięcie zmiennych

#### Automated

- [ ] 2.1 `railway.ts` nie deklaruje `RAILPACK_JDK_VERSION`, `DATABASE_URL`, `JWT_SECRET`
- [ ] 2.2 PR env bez trzech zmiennych (`jq 'keys'`)
- [ ] 2.3 Wszystkie workflow na PR zielone, w tym `railway-config`; brak alertu po deployu
- [ ] 2.4 `/api/ready` w PR env zwraca 200 po deployu
- [ ] 2.5 Tabela JVM ma datę i wartości pomiaru z PR env
- [ ] 2.7 Skutek `railway variable delete` w PR env zapisany w #78

#### Manual

- [ ] 2.6 Człowiek potwierdza wnioski z tabeli JVM

### Phase 3: Produkcja przed merge'em i przekazanie

#### Automated

- [ ] 3.1 Sekcja `## S-19` w `deploy-plan.md` z runbookami „before the merge” i „after the merge” (#78)
- [ ] 3.2 Runbook „before the merge” wymienia warunki wstępne i sprawdzenie przez `jq 'keys'`
- [ ] 3.4 Status sekcji S-19 bez „not merged”/„in progress”, odsyła do #78
- [ ] 3.5 Nowy wpis w `lessons.md` o statusach, plik tylko dopisany

#### Manual

- [ ] 3.3 Człowiek potwierdza kolejność: impl-review → archive → usunięcie na produkcji → merge
