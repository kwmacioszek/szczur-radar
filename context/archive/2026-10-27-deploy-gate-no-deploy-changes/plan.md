# S-21: zmiana IaC bez deployu nie blokuje wdrożeń — plan implementacji

## Overview

Kontrola po apply w jobie `railway-config` (`tools/deploy-gate/verify-apply.sh check`) przestaje brać „pierwszy deployment o nowym ID” za deployment swojego apply'a. Rozpoznaje go po `meta.patchId` z przedrostkiem `iac-change-set/`, pomija deploymenty pushy i przechodzi z `::notice::`, gdy apply nie wywołał żadnego deployu. Deploy apply'a, który naprawdę padł (`FAILED`, `CRASHED`, `SKIPPED`, zawieszony Postgres) i `/api/ready` ≠ 200 dalej robią job czerwonym. Runbook S-14 w `deploy-plan.md` i issue #73 dostają prawdę o `2abc64c`.

Wycinek roadmapy S-21 (M-3), issue #115. Wejście: `context/changes/deploy-gate-no-deploy-changes/research.md` (uzupełnia audyt `context/changes/post-m2-audit/research.md`, P1–P3).

## Current State Analysis

- `gate.jq:42-43` liczy każdą dozwoloną zmianę jako redeploy (`.deployEffect != "none"`; CLI 5.67.1 nie daje `deployEffect`), więc `restarts.txt` zawiera każdą zmienioną usługę lub bazę.
- `verify-apply.sh check` (`:146-196`) pyta `railway deployment list --json --limit 1` (`:82`) i bierze pierwszy deployment o ID innym niż migawka (`:154`), oceniając tylko `status` (`:159-182`). `SKIPPED` → exit 1 (`:170-173`); to samo ID do 15 min → exit 1 „the apply did not trigger a deploy” (`:184-187`).
- Skutek (audyt P1, oś czasu `2abc64c`): apply `source.checkSuites (null → true)` nie wywołał deployu, kontrola wzięła `SKIPPED` z równoległego pusha `fa37101` (`2f602f86`) za deployment apply'a i zrobiła workflow czerwonym; czerwony workflow na `main` sprawia, że Wait for CI pomija deploy kodu commita (API `981111be` `SKIPPED`, „CI check suite failed”).
- Railway oznacza deployment apply'a: `meta.patchId` = `iac-change-set/<envId>/<hash>`, `ignoreWatchPatterns: true`; 31 takich w 298 deploymentach produkcji, 0 `SKIPPED`; pushe nie mają `patchId` (research §1). Powstaje 1–4 s po apply'u; na usługach z `checkSuites: true` (API, web, db-backup, pipeline-trigger) stoi w `WAITING` do konkluzji workflow, na Postgresie buduje się od razu (research §2).
- CLI 5.67.1 przepuszcza `meta` z API bez zmian (`src/commands/deployment.rs:62,136`, `meta: Option<serde_json::Value>`), `--limit` 1–1000 (`src/commands/output/limit.rs`), lista od najnowszego (`tools/deploy-gate/README.md:138`).
- `test.sh` (zielony, 47 s) utrwala starą regułę: atrapa `railway` (`:488-530`) przyjmuje tylko `--limit 1` i daje `meta: {}` (`:526`); `same-id` (`:633-637`) oczekuje exit 1; podsumowanie bramki „(an apply redeploys them)” (`:188`).
- Dokumenty: `deploy-plan.md:908` (Status S-14), `:939` (reguła 6), `:976-981` (semantyka kontroli), `:1020` („announces both crons as redeployed”), `:1037-1059` (runbook z 13 polami `[ ]`, bez notatki po merge'u); `tools/deploy-gate/README.md:61-65,120-138,168,177`; komentarze `gate.jq:7-13`, `verify-apply.sh:2-25`, `ci.yml:500-504`; komentarz zamykający #73 (pkt 1 częściowo, pkt 2 błędnie dla API).

## Desired End State

- Apply, który nie wywołał deployu, kończy kontrolę zielono: wiersz „no deploy” w podsumowaniu i `::notice::` w logu, po oknie `DEPLOY_VERIFY_DEPLOY_WINDOW` (domyślnie `2m`).
- Deploymenty pushy (`SKIPPED` przez watch paths, `WAITING`, `SUCCESS`) nie są brane za deployment apply'a; kontrola ocenia tylko deploymenty z `patchId` `iac-change-set/` nowsze od migawki.
- Deployment apply'a w `FAILED`/`CRASHED`/`SKIPPED`/`NEEDS_APPROVAL`/nieznanym stanie, deploy niezakończony w `DEPLOY_VERIFY_TIMEOUT`, granica migawki nie do odnalezienia i `/api/ready` ≠ 200 dalej dają exit 1.
- `deploy-plan.md` (S-14 i nowa sekcja S-21), `tools/deploy-gate/README.md`, `REPOSITORY_GUIDELINES.md` i komentarz w #73 opisują to, co się stało i jak kontrola działa teraz.
- Weryfikacja: `tools/deploy-gate/test.sh` zielony; kontrola tylko do odczytu na prawdziwych deploymentach produkcji; trzy przebiegi w PR env (bez deployu z równoległym pushem, z deployem, cofnięcie).

### Key Discoveries:

- `meta.patchId` z `iac-change-set/` jest jedynym rozróżnikiem: `reason` jest `"deploy"`, a `commitHash` to czubek gałęzi w chwili apply'a, czyli przy równoległym pushu cudzy commit (research §1: run `427ebc3` → deploymenty z `eee1eda`).
- Redeploy zachowuje `patchId` oryginału (`416b4a1e`, `reason: "redeploy"`), a hash w `patchId` nie jest jednoznaczny dla jednego runu (`d794b8f1…` w trzech partiach 2026-10-11) — do rozpoznania wystarcza przedrostek plus pozycja nad granicą migawki.
- Postgres deployuje się wyłącznie łatkami (20/20 z `patchId`, 0 z `commitHash`) — równoległy push go nie dotyczy; to jedyna usługa, na której kontrola czeka do `SUCCESS` (monitor nie wykrywa zawieszenia w `DEPLOYING`).
- Krok PR env „Ensure PR-env LLM settings” (`ci.yml:381`, `variable delete` bez `--skip-deploys`) daje, jeśli w ogóle, łatkę spoza `iac-change-set/` — nowa reguła ją pomija.

## What We're NOT Doing

- Lista zmian „bez deployu” w `gate.jq` (R1) i jakakolwiek zmiana logiki bramki: `restarts.txt` zostaje listą usług, które apply **może** zredeployować; zmienia się tylko opis.
- Zmiana wersji CLI (zostaje `@railway/cli@5.67.1`) i `railway config apply --json` do wiązania deploymentu z `stagedPatchId` (równość hasha [U], niepotrzebna przy przedrostku + granicy).
- Zmiana obsługi usług z Wait for CI: deployment apply'a w `WAITING`/`QUEUED` nadal przekazywany monitorowi; bez rozróżniania usług po `checkSuites`.
- Zmiany w `/api/ready`, kroku „API after a failed apply”, strażniku, budżecie 2 h (`DEPLOY_VERIFY_TIMEOUT` 15m bez zmian; okno mieści się w nim) i w monitorze `szczur-radar-monitor`.
- `deploy-plan.md:967` („Not verified yet” o `railway redeploy` czerwonego commita) — dotyczy `railway redeploy`, nie apply'a; zostaje.
- Ponowne otwieranie #73 i odhaczanie 13 pól runbooka wstecz.
- Nowa lekcja w `lessons.md` (kandydat: pochodzenie deploymentu po `patchId`) — tylko na prośbę, np. przy `/10x-archive`.
- Kroki po merge'u w Progress: weryfikacja produkcji po merge'u i zamknięcie #115 żyją w runbooku nowej sekcji S-21 i w komentarzach issue (`lessons.md`, „Implementacja, przegląd i archiwizacja na jednej gałęzi”).

## Implementation Approach

Migawka zostaje bez zmian: ID najnowszego deploymentu każdej usługi z `restarts.txt` (dowolnego pochodzenia) to **granica**. Kontrola po apply pyta o kilkadziesiąt najnowszych deploymentów z `meta` i bierze pod uwagę tylko te powyżej granicy na liście (lista jest od najnowszego, więc to deploymenty utworzone po migawce — bez porównywania zegarów). Spośród nich deploymentem apply'a jest najnowszy z `patchId` `iac-change-set/`; jego status ocenia się jak dziś. Gdy takiego nie ma do końca okna liczonego od startu kontroli, usługa dostaje werdykt „no deploy” i kontrola idzie dalej. Testy na atrapie wyrażają prawdziwe kształty `meta` z researchu, a kontrolę sprawdza się dodatkowo na prawdziwych danych produkcji (tylko odczyt) i w PR env tej zmiany. Dokumenty operatora i komentarz w #73 po weryfikacji, żeby opisywały zachowanie potwierdzone na żywo.

## Critical Implementation Details

**Timing & lifecycle.** Okno `DEPLOY_VERIFY_DEPLOY_WINDOW` liczy się od startu `check` (krok tuż po apply'u), raz dla wszystkich usług: usługi są sprawdzane po kolei, a apply tworzy deploymenty wszystkich usług w ciągu sekund, więc usługa sprawdzana po 10-minutowym Postgresie dostaje werdykt po jednym odczycie. Werdykt „no deploy” wolno wydać tylko z **udanego** odczytu listy wykonanego po końcu okna; same nieudane wywołania `railway` aż do `DEPLOY_VERIFY_TIMEOUT` kończą się exit 1 jak dziś.

## Phase 1: Kontrola rozpoznaje deployment swojego apply'a

### Overview

`verify-apply.sh check` wybiera deployment apply'a po `meta.patchId` i granicy migawki, przechodzi z `::notice::` przy braku deployu; atrapa i przypadki w `test.sh` wyrażają prawdziwe `meta`; opis bramki i README narzędzia mówią, że lista to usługi, które apply może zredeployować.

### Changes Required:

#### 1. Kontrola po apply

**File**: `tools/deploy-gate/verify-apply.sh`

**Intent**: Zastąpić „pierwsze nowe ID” wyborem deploymentu apply'a po `patchId` nad granicą, dodać werdykt „no deploy” po oknie i usunąć porażkę „the apply did not trigger a deploy”, zachowując resztę statusów, limit i `/api/ready`.

**Contract**:
- `snapshot`: wywołanie, wyjście `{"<usługa>": "<id>" | null}` i 3 próby bez zmian (`--limit 1`, najnowszy deployment dowolnego pochodzenia = granica).
- `check`, co `DEPLOY_VERIFY_INTERVAL`: `railway deployment list --json --limit 50 -s <usługa> -p "$RAILWAY_PROJECT_ID" -e "$RAILWAY_ENVIRONMENT_ID"`. Deploymenty po apply'u = elementy listy przed granicą (granica `null` → cała lista). Granica różna od `null` i nieobecna na liście → exit 1 „cannot tell the apply's deployment of <usługa>: its deployment before the apply <id> is not among the newest 50”.
- Deployment apply'a = najnowszy z deploymentów po apply'u, którego `meta.patchId` jest stringiem zaczynającym się od `iac-change-set/`. Statusy jak dziś (`SUCCESS`/`SLEEPING` wdrożony; `WAITING`/`QUEUED` przekazany monitorowi; `BUILDING`/`DEPLOYING`/`INITIALIZING` czekaj; `FAILED`/`CRASHED`/`SKIPPED`/`NEEDS_APPROVAL`/nieznany exit 1). `REMOVED`/`REMOVING` → oceniany jest najnowszy deployment nad nim o statusie innym niż `SKIPPED`, dowolnego pochodzenia (zastąpił go; `SKIPPED` niczego nie zastępuje); brak takiego → czekaj. Komunikat `SKIPPED` dołącza `meta.skippedReason`, gdy jest.
- Brak deploymentu apply'a w udanym odczycie po końcu okna → wiersz podsumowania „no deploy (none within <okno> of the apply)” i `::notice::post-apply check: the apply triggered no deploy of <usługa> within <okno>`; następna usługa.
- `DEPLOY_VERIFY_DEPLOY_WINDOW` (domyślnie `2m`, format jak inne czasy w `lib.sh`); większe niż `DEPLOY_VERIFY_TIMEOUT` → exit 2. Walidowane razem z innymi czasami (`verify-apply.sh:69-72`, w obu trybach), więc zła wartość zatrzymuje job już w `snapshot`, przed apply.
- Nagłówek skryptu (`:2-33`) opisuje nową regułę, w tym dlaczego `patchId`, a nie `reason`/`commitHash`, i założenie, że API zwraca listę od najnowszego (CLI 5.67.1 nie sortuje, zapytanie nie ma `orderBy`; na tym opierają się i migawka, i granica).

#### 2. Opis bramki

**File**: `tools/deploy-gate/gate.sh`, `tools/deploy-gate/gate.jq`

**Intent**: Podsumowanie i komentarze mówią prawdę: lista restartów to usługi, które apply może zredeployować; logika bez zmian.

**Contract**: Linia podsumowania `**Restarts:** … (an apply redeploys them)` (`gate.sh:66-67`) zmienia nawias na „(an apply may redeploy them; the check after it waits for the deployments the apply starts)”. Komentarze `gate.sh:4,12-14` i `gate.jq:7-13,42` zgodnie z tym; `def restarts`, `.restarts` i `GATE_RESTARTS_FILE` bez zmian.

#### 3. Atrapa `railway` i przypadki testowe

**File**: `tools/deploy-gate/test.sh`

**Intent**: Atrapa wyraża kilka deploymentów od najnowszego z prawdziwym `meta` każdego pochodzenia, a przypadki dowodzą reguły S-21 i nie utrwalają starej.

**Contract**:
- Atrapa przyjmuje `deployment list --json --limit 1` (migawka) i `--limit 50` (kontrola), inne wartości → exit 3; jedna odpowiedź na wywołanie, ostatnia się powtarza (jak dziś); odpowiedź to lista deploymentów od najnowszego, każdy z ID, statusem i pochodzeniem: IaC (`meta.patchId` `iac-change-set/<env>/<hash>`, `ignoreWatchPatterns: true`, `reason: "deploy"`, `commitHash`), push (`reason: "deploy"`, `commitHash`, bez `patchId`), push pominięty przez watch paths (bez `reason`, `skippedReason: "No changes to watched files"`); przy `--limit 1` tylko pierwszy.
- Istniejące przypadki sekcji 11 przechodzą na deploymenty IaC nad granicą i zachowują wyniki, z wyjątkiem `same-id` (zastąpiony przypadkiem „bez deployu”). `run_verify` zeruje też `DEPLOY_VERIFY_DEPLOY_WINDOW`; przypadki z `DEPLOY_VERIFY_TIMEOUT=2s` (`cli-fails`, `limit`, (h)) dostają okno ≤ limit.
- Nowe przypadki: (a) nad granicą tylko pushe (`SKIPPED` watch paths, `WAITING`, `SUCCESS`), `DEPLOY_VERIFY_DEPLOY_WINDOW=2s` → exit 0, `::notice::` „triggered no deploy”, wiersz „no deploy”, `/api/ready` zapytane; (b) push `SKIPPED` nad deploymentem IaC `WAITING` → przekazanie deploymentu IaC; (c) push `SUCCESS` nad deploymentem IaC `FAILED` → exit 1 o deploymencie IaC; (d) deployment IaC pojawia się po dwóch odczytach w oknie → czekanie i `SUCCESS`; (e) granica nieobecna na liście → exit 1 „cannot tell”; (f) `REMOVED` IaC zastąpiony nowszym `SUCCESS`, nad nim push `SKIPPED` (watch paths) → wdrożony (`SUCCESS`); (g) `SKIPPED` IaC → exit 1 z `skippedReason`; (h) `railway` zawodzi do limitu → exit 1 (bez werdyktu „no deploy”); (i) okno większe niż limit albo zły format → exit 2.
- Nagłówek testu (`:2-31`, reguła 6 i sekcja 11) i asercja podsumowania bramki (`:188`) zgodnie z nowymi tekstami; `restarts_are` bez zmian.

#### 4. README narzędzia

**File**: `tools/deploy-gate/README.md`

**Intent**: Reguła 6, sekcja `verify-apply.sh` i lista testów opisują nowe zachowanie.

**Contract**: `:61-65` (reguła 6: lista usług, które apply może zredeployować); `:120-138` (granica migawki, wybór po `patchId`, okno i werdykt „no deploy”, „cannot tell”, `REMOVED`, tabela statusów, `--limit 50`, kształt `meta`); `:168,177` (opisy przypadków testowych).

### Success Criteria:

#### Automated Verification:

- `tools/deploy-gate/test.sh` kończy się „All checks passed.” (exit 0)
- `test.sh` zawiera przypadki (a)–(i) z sekcji 11 i nie zawiera oczekiwania „the apply did not trigger a deploy”
- `bash -n tools/deploy-gate/verify-apply.sh` i `bash -n tools/deploy-gate/test.sh` bez błędów

#### Manual Verification:

- Kontrola tylko do odczytu na prawdziwych deploymentach produkcji (CLI 5.67.1, jak w CI): migawka z granicą o 2–3 deploymenty wstecz dla `db-backup` i `pipeline-trigger` (nad nią pushe `SKIPPED`) daje exit 0, „no deploy” dla obu i `/api/ready` 200
  - Metoda: agent. `npx -y @railway/cli@5.67.1` w `PATH`, `RAILWAY_PROJECT_ID`/`RAILWAY_ENVIRONMENT_ID` produkcji, `DEPLOY_VERIFY_DEPLOY_WINDOW=20s`, `verify-apply.sh check` z ręcznie zapisaną migawką; żadnego `config apply`, `redeploy` ani zapisu. Dowód: log i podsumowanie w komentarzu #115, w tym sprawdzenie, że `createdAt` w odpowiedzi `--limit 50` maleje.

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie kontroli ręcznej, zanim przejdziesz do fazy 2.

---

## Phase 2: Weryfikacja w PR env

### Overview

PR nie dotyka katalogów usług, więc Railway nie uruchamia w PR env żadnej usługi; przed scenariuszami agent uruchamia je ręcznie (zmiana 0). Trzy pushe na gałęzi tej zmiany potwierdzają w PR env na prawdziwym Railway: zmianę bez deployu przy równoległym pushu, zmianę z deployem i jej cofnięcie. Gałąź kończy bez zmian w `.railway/railway.ts`.

### Changes Required:

#### 0. Usługi PR env działają

**File**: — (stan PR env)

**Intent**: PR nie dotyka katalogów usług, więc PR env nie ma żadnego deploymentu (`deploy-plan.md`, S-19 „PR env of a PR that touches no service directory”): bez API każdy run z apply'em kończy się na `/api/ready`, a `pipeline-trigger` bez deploymentu nie dostanie deploymentu IaC.

**Contract**: Przed zmianą 1, przy zielonym CI czubka gałęzi: `railway deployment list --json` w PR env dla `postgres`, `szczur-radar-api`, `szczur-radar-web`, `db-backup`, `pipeline-trigger`; każdej bez działającego deploymentu `railway redeploy --from-source -s <usługa> -e szczur-radar-pr-<N>` (tylko PR env, projekt przez `RAILWAY_PROJECT_ID`, nigdy `railway link`; kolejność Postgres → API → web → crony; wzór: `deploy-plan.md`, S-14 „Guard without a deployed web”). Gotowe, gdy `GET <web PR env>/api/ready` = 200. Gdy CLI odmówi usłudze bez żadnego deploymentu: commit z linią komentarza w README czterech katalogów usług, close i reopen PR-a (Railway odtwarza env, `deploy-plan.md:1455`), a zmiana 3 cofa też te linie.

#### 1. Zmiana bez deployu

**File**: — (stan PR env)

**Intent**: Wywołać apply, który nic nie deployuje, tak jak `source.checkSuites` 2026-10-19.

**Contract**: Jeśli plan PR env już pokazuje `source.checkSuites (null → true)`, ten run jest scenariuszem; inaczej `railway environment edit -e szczur-radar-pr-<N> --service-config db-backup source.checkSuites false` (tylko PR env, nigdy bez `-e`; najpierw `--help` lokalnej wersji CLI; Railway MCP `update-service` nie zmienia źródła), potwierdzenie przez MCP `get-service-config`, potem pusty commit (`git commit --allow-empty`) i push, plan `source.checkSuites (false → true)`. Gdy CLI tego nie przyjmie: operator odznacza „Wait for CI” dla `db-backup` w tym env w panelu.

#### 2. Tymczasowa zmiana z deployem i równoległy push

**File**: `.railway/railway.ts`

**Intent**: W oknie kontroli runu ze zmiany 1 wypchnąć commit z tymczasową zmianą `deploy.cronSchedule` `pipeline-trigger` tylko w PR env (wzór `ec1fe66`: `process.env.IAC_SOURCE_BRANCH ? "31 0 * * *" : "30 0 * * *"`); jego pushowe deploymenty pojawiają się nad granicą runu 1, a jego własny run daje deployment IaC.

**Contract**: Produkcyjna wartość `cronSchedule` bez zmian w każdym commicie; komentarz `TEMPORARY (S-21 Phase 2, reverted by the next commit)`. Push, gdy krok „Deployments before the apply” runu 1 ma `completed` (`gh run view <id> --json jobs`), najpóźniej 90 s później. Chybienie albo brak deploymentu `db-backup` z tego pusha w PR env → 2.4 zapisane jako „nieodtworzone w PR env”, dowodem zostają testy (a)–(c) i 1.4.

#### 3. Cofnięcie

**File**: `.railway/railway.ts`

**Intent**: Commit cofający zmianę 2; jego apply też deployuje `pipeline-trigger` i jest drugim przypadkiem „z deployem”.

**Contract**: Push dopiero po konkluzji runu 2 i `SUCCESS` jego deploymentu IaC `pipeline-trigger` (wcześniejszy push daje temu deploymentowi `commitHash` commita 3, a push w czasie, gdy `railway-config` runu 2 czeka w grupie `concurrency`, anuluje go — `ci.yml:285-290`). `git diff origin/main -- .railway/railway.ts szczur-radar-api szczur-radar-web tools/db-backup tools/pipeline-trigger` pusty po tym commicie.

### Success Criteria:

#### Automated Verification:

- CI PR-a zielone na każdym z trzech commitów, w tym `deploy-gate-test` i `railway-config` (`gh pr checks`)
- `git diff origin/main -- .railway/railway.ts szczur-radar-api szczur-radar-web tools/db-backup tools/pipeline-trigger` pusty na czubku gałęzi

#### Manual Verification:

- Bez deployu: run zmiany 1 ma w kontroli „no deploy” dla `db-backup` z `::notice::`, exit 0 i `/api/ready` 200; lista deploymentów `db-backup` PR env nie ma deploymentu `iac-change-set/` po apply'u
  - Metoda: agent. Dowód: URL runu, linie logu `post-apply check`, `railway deployment list --json` PR env.
- Równoległy push: deploymenty z pusha zmiany 2 powstałe w oknie runu 1 nie są w jego logu ani podsumowaniu jako deployment apply'a
  - Metoda: agent. Dowód: ID i `createdAt` tych deploymentów obok logu runu 1.
- Z deployem: runy zmian 2 i 3 mają deployment `pipeline-trigger` z `patchId` `iac-change-set/` w `WAITING`, przekazany monitorowi, exit 0; po konkluzji workflow ten deployment się buduje (`SUCCESS`)
  - Metoda: agent. Dowód: URL-e runów, ID deploymentów, ich `meta.patchId` i status po CI.
- PR env po fazie: `railway config plan` dla PR env z czubka gałęzi bez zmian (`--detailed-exit-code` 0)
  - Metoda: agent (tylko odczyt). Dowód: wyjście planu.

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie kontroli ręcznej, zanim przejdziesz do fazy 3. Wyniki zapisz komentarzem w #115 (kamień milowy: faza zweryfikowana w PR env).

---

## Phase 3: Dokumenty i #73

### Overview

Dokumenty operatora opisują zachowanie potwierdzone w fazach 1–2, sekcja S-14 dostaje notatkę „Production after the merge” zamiast nieodhaczonego runbooka, nowa sekcja S-21 ma runbook po merge'u, a #73 komentarz korygujący.

### Changes Required:

#### 1. Sekcja S-14 w `deploy-plan.md`

**File**: `context/deployment/deploy-plan.md`

**Intent**: Poprawić zdania, które fakty obaliły, i zastąpić jednorazowy runbook zapisem tego, co się stało.

**Contract**:
- `:908` Status w formie z `lessons.md`: „**Status:** delivered by PR #81 (`2abc64c`); production verification and closure in issue #73 (closed 2026-10-24, corrected <data>).”, reszta akapitu bez zmian.
- `:939` (reguła 6), `:976-981` (kontrola po apply), `:1020` (crony): dopiski „**Update (S-21):** …” z odesłaniem do sekcji S-21 — `source.checkSuites` nie deployuje; kontrola czeka tylko na deploymenty, które apply uruchomił.
- `:1037-1059` (runbook „after the merge”, 13 pól) → `### Production after the merge (2026-10-19)`: CI 39285519093 (strażnik 401, dwie zmiany `source.checkSuites` zastosowane, na żywo `checkSuites: true`), apply bez deployu, kontrola fałszywie czerwona przez `SKIPPED` `2f602f86` z `fa37101`, API `981111be` `SKIPPED` („CI check suite failed”) — `2abc64c` nie wdrożył się na API, crony zbudowane przy pushu (Wait for CI włączył dopiero ten apply), kod S-14 w API z `fa37101` (16:46:21Z), `deploy-verify` sondował stare API; naprawa: S-21; źródło: audyt `post-m2-audit` P1–P2.

#### 2. Nowa sekcja S-21 w `deploy-plan.md`

**File**: `context/deployment/deploy-plan.md`

**Intent**: Widok operatora na nową regułę, wynik PR env i runbook po merge'u, wzorem sekcji S-17.

**Contract**: `## S-21: zmiana IaC bez deployu nie blokuje wdrożeń (deploy-gate-no-deploy-changes)` po sekcji S-17, przed `## Phases`:
- „**Status:** delivered by PR #<N>; production verification and closure in issue #115.”;
- `### What changed for the operator`: rozpoznanie po `patchId`, werdykt „no deploy” (co znaczy `::notice::` i co zrobić, gdy dotyczy zmiany, która miała deployować: sprawdzić usługę w Railway, `railway redeploy`), `DEPLOY_VERIFY_DEPLOY_WINDOW`, „cannot tell”, deploy apply'a na usługach z Wait for CI czeka na CI (research §2);
- `### PR-env verification (szczur-radar-pr-<N>)`: wyniki fazy 2 (URL-e runów, ID deploymentów);
- `### Runbook — after the merge (operator; tracked in issue #115)`: CI na `main` zielone (plan bez zmian, kontrola pominięta), kontrola tylko do odczytu na produkcji z kodu `main` (jak 1.4), komentarz z wynikami w #115 i zamknięcie; pierwszy prawdziwy apply ze zmianami na produkcji (np. przy S-27) odnotować w #115 albo w issue tamtej zmiany.

#### 3. Wytyczne repo i komentarz kroku CI

**File**: `REPOSITORY_GUIDELINES.md`, `.github/workflows/ci.yml`

**Intent**: Jedno zdanie o kontroli po apply zgodne z nową regułą.

**Contract**: `REPOSITORY_GUIDELINES.md`, sekcja Deployment, punkt o CI: „…applies exactly that plan and then checks the deployments that apply started (by `meta.patchId`; none is a notice) and `/api/ready` (…section S-14, S-21)”. `ci.yml:500-504`: komentarz kroku „Check after the apply” w tym samym duchu; żadnej zmiany kroków, `env:` ani `if:`.

#### 4. Komentarz korygujący w #73

**File**: — (GitHub issue #73, zostaje zamknięte)

**Intent**: Poprawić pkt 1 i 2 komentarza zamykającego z 2026-10-24.

**Contract**: Komentarz po polsku: pkt 2 obalony dla API (`981111be` `SKIPPED` 3 s po konkluzji `failure`, „CI check suite failed”; „Deployed 2abc64c / success” to statusy GitHub Deployment), prawdziwy dla cronów; pkt 1 doprecyzowany (apply bez deployu + `SKIPPED` równoległego pusha); odesłania: audyt `context/changes/post-m2-audit/research.md` (P1, P2), #115, PR S-21, sekcja „Production after the merge (2026-10-19)”. Agent pokazuje tekst operatorowi przed wysłaniem.

### Success Criteria:

#### Automated Verification:

- W sekcji S-14 `deploy-plan.md` nie ma pola `- [ ]`, jest `### Production after the merge (2026-10-19)`
- `grep -n "announces both crons as redeployed\|an apply redeploys them" context/deployment/deploy-plan.md tools/deploy-gate/README.md tools/deploy-gate/gate.sh` bez trafień
- `deploy-plan.md` ma sekcję `## S-21:` z linią „**Status:** delivered by PR #” i „issue #115”
- `tools/deploy-gate/test.sh` dalej zielony

#### Manual Verification:

- Komentarz korygujący w #73 wysłany
  - Metoda: agent pokazuje tekst, operator zatwierdza, agent wysyła (`gh issue comment 73`). Dowód: URL komentarza.
- Operator przeczytał notatkę S-14 i sekcję S-21 i potwierdza, że zgadzają się z tym, co widział na produkcji
  - Metoda: operator (ocena treści); agent podaje dla każdego faktu źródło (audyt, research, run).

**Implementation Note**: Po fazie: PR gotowy, CI i PR env zielone — merge robi człowiek; kroki po merge'u wg runbooka sekcji S-21.

---

## Testing Strategy

### Unit Tests:

- `tools/deploy-gate/test.sh` sekcja 11: przypadki (a)–(i) z fazy 1 plus dotychczasowe (migawka, statusy, `REMOVED`, limit, `/api/ready`, złe wywołania) na deploymentach IaC; sekcje 1–8 bramki bez zmian poza tekstem podsumowania.
- Kluczowe brzegi: tylko pushe nad granicą; push nad deploymentem IaC; deployment IaC pojawiający się w oknie; granica poza listą; `railway` zawodzący przez całe okno.

### Integration Tests:

- Kontrola na prawdziwych deploymentach produkcji tylko do odczytu (1.4) — prawdziwe kształty `meta` z CLI 5.67.1.
- Trzy runy `railway-config` w PR env (faza 2).

### Manual Testing Steps:

1. Faza 1: `verify-apply.sh check` lokalnie na produkcji z migawką 2–3 deploymenty wstecz.
2. Faza 2: push zmiany bez deployu; w jej oknie push tymczasowego `cronSchedule`; potem cofnięcie; sprawdzić logi trzech runów i listy deploymentów PR env.
3. Faza 3: przeczytać notatkę S-14, sekcję S-21 i tekst komentarza #73.

## Performance Considerations

Apply ze zmianami, które nie deployują, wydłuża job o okno (domyślnie 2 min) zamiast dzisiejszych 15 min porażki. `--limit 50` co 10 s na usługę to jedno lekkie zapytanie API; okno mieści się w `DEPLOY_VERIFY_TIMEOUT`, więc budżet 2 h i termin strażnika bez zmian.

## Migration Notes

Brak danych i migracji. Wycofanie: revert PR-a przywraca starą kontrolę (z fałszywym czerwonym wynikiem przy zmianie bez deployu). Kontrola nie ma wyłącznika (`DEPLOY_GUARD=off` dotyczy tylko strażnika), więc awaryjnie revert albo poprawka w kolejnym PR.

## References

- Research: `context/changes/deploy-gate-no-deploy-changes/research.md`
- Audyt: `context/changes/post-m2-audit/research.md` (sekcja 3, P1–P3)
- S-14: `context/archive/2026-10-18-safe-deploy-gate/plan.md` (Phase 4, Plan Amendment wariant A), `context/deployment/deploy-plan.md:906-1060`
- Monitor: `context/deployment/deploy-plan.md`, sekcja `deploy-verification-hardening`
- Kod: `tools/deploy-gate/verify-apply.sh:80-196`, `tools/deploy-gate/gate.jq:42-43`, `tools/deploy-gate/test.sh:488-686`, `.github/workflows/ci.yml:486-518`
- Issue: #115 (S-21), #73 (S-14)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Kontrola rozpoznaje deployment swojego apply'a

#### Automated

- [x] 1.1 `tools/deploy-gate/test.sh` kończy się „All checks passed.” (exit 0) — dd321c2
- [x] 1.2 `test.sh` zawiera przypadki (a)–(i) z sekcji 11 i nie zawiera oczekiwania „the apply did not trigger a deploy” — dd321c2
- [x] 1.3 `bash -n tools/deploy-gate/verify-apply.sh` i `bash -n tools/deploy-gate/test.sh` bez błędów — dd321c2

#### Manual

- [x] 1.4 Kontrola tylko do odczytu na prawdziwych deploymentach produkcji (CLI 5.67.1): granica 2–3 deploymenty wstecz dla `db-backup` i `pipeline-trigger` daje exit 0, „no deploy” dla obu i `/api/ready` 200 — dd321c2

### Phase 2: Weryfikacja w PR env

#### Automated

- [x] 2.1 CI PR-a zielone na każdym z trzech commitów, w tym `deploy-gate-test` i `railway-config` (`gh pr checks`) — 9077846
- [x] 2.2 `git diff origin/main -- .railway/railway.ts szczur-radar-api szczur-radar-web tools/db-backup tools/pipeline-trigger` pusty na czubku gałęzi — 9077846

#### Manual

- [x] 2.3 Bez deployu: run zmiany 1 ma „no deploy” dla `db-backup` z `::notice::`, exit 0 i `/api/ready` 200; brak deploymentu `iac-change-set/` po apply'u — 6d13b41
- [x] 2.4 Równoległy push: deploymenty z pusha zmiany 2 w oknie runu 1 nie są wzięte za deployment apply'a — 4637f86
- [x] 2.5 Z deployem: runy zmian 2 i 3 mają deployment IaC `pipeline-trigger` w `WAITING`, przekazany monitorowi, exit 0; po CI `SUCCESS` — 9077846
- [x] 2.6 PR env po fazie: `railway config plan --detailed-exit-code` z czubka gałęzi daje 0 — 4637f86

### Phase 3: Dokumenty i #73

#### Automated

- [x] 3.1 W sekcji S-14 `deploy-plan.md` nie ma pola `- [ ]`, jest `### Production after the merge (2026-10-19)` — 3f204e0
- [x] 3.2 `grep` „announces both crons as redeployed” / „an apply redeploys them” w `deploy-plan.md`, `tools/deploy-gate/README.md`, `gate.sh` bez trafień — 3f204e0
- [x] 3.3 `deploy-plan.md` ma sekcję `## S-21:` z linią „**Status:** delivered by PR #” i „issue #115” — 3f204e0
- [x] 3.4 `tools/deploy-gate/test.sh` dalej zielony — 3f204e0

#### Manual

- [x] 3.5 Komentarz korygujący w #73 wysłany (tekst zatwierdzony przez operatora) — 4e21617
- [x] 3.6 Operator potwierdza, że notatka S-14 i sekcja S-21 zgadzają się z tym, co widział na produkcji — 4e21617
