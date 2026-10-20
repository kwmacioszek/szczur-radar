# Wiarygodna weryfikacja wdrożeń — plan implementacji

> Przepisany po `/10x-plan-review` 2026-10-20 (`reviews/plan-review.md`, F1): monitor zostaje
> zewnętrzny, ale odchudzony do jednego sygnału: „czy środowisko po deployu paczki wstało
> i odpowiada”.

## Overview

Workflow `deploy-verify` (S-14) w szczur-radar zastępuje mały zewnętrzny monitor w publicznym repo
`szczur-radar-monitor`. Webhook Railway trafia do Cloudflare Workera, który tylko przekazuje
zdarzenie przez `workflow_dispatch` do GitHub Actions monitora. Jeden skrypt czyta z Railway stan
wdrażanej usługi, dla API i weba sonduje `/api/ready`, `/healthz` i `/`, a dla crona ocenia tylko
jego deploy, i kończy się zielono albo **czerwono**. Czerwień daje jedno issue `deploy-health`
na środowisko i usługę w repo monitora, przypisane do operatora (push w GitHub Mobile), zamykane
przez pierwszy zielony run tej usługi. W szczur-radar nie ma żadnego
workflow poza CI, więc Wait for CI nie ma czego blokować.

## Current State Analysis

Z frame'u (`frame.md`), potwierdzone w tym planowaniu:

- `deploy-verify.yml` (`on: deployment_status`) dostaje od Railway jeden GitHub Deployment na
  środowisko i commit, ze statusami bez nazwy usługi. Każdy `in_progress` daje run `skipped`
  (29 z 49 runów). `failure` dowolnej usługi, np. celowy `CRASHED` crona w #90, oznacza całe
  środowisko, a późniejszy `success` je maskuje.
- Workflow celowo nigdy nie jest czerwony (`verify-deploy.sh:37-39`): Wait for CI liczy
  konkluzję każdego workflow GitHub Actions przy commicie w szczur-radar. Workflow w innym repo
  nie jest dla niego widoczny („Checks from other GitHub apps are ignored”, docs Railway,
  sprawdzone 2026-10-20).
- Runy w szczur-radar idą na `szczur-ci` (minuty GitHub wyczerpane od 2026-10-18); repo publiczne
  ma darmowe runnery hostowane.
- Sondy: `/healthz` to stałe `respond "ok" 200` (`szczur-radar-web/Caddyfile:18-20`),
  `/api/ready` to `SELECT 1` (`HealthController.java:37-46`). Dla celu „wstało i odpowiada” to
  wystarcza; stan `FAILED`/`CRASHED` deployu bierzemy z Railway, nie z sond.
- Duplikaty issue wynikają z ~2 s opóźnienia listy issue przy braku concurrency
  (`verify-deploy.sh:173-175`).

## Desired End State

- Commity szczur-radar nie mają żadnych runów `deploy-verify` (ani `skipped`).
- Każdy finalny deploy usługi (także w PR envach) daje jeden run `verify.yml` w
  `szczur-radar-monitor`: **zielony** albo **czerwony**.
- Run ocenia **usługę, która się wdrożyła** (z webhooka), nie całe środowisko:
  - deploy `szczur-radar-api` lub `szczur-radar-web` → **kontrola gotowości środowiska**:
    najnowszy deploy tej usługi `SUCCESS`/`SLEEPING` oraz `/api/ready` (API + DB), `/healthz`
    i `GET /` (SPA z Caddy) odpowiadają 200 w oknie 3 min od ustalenia stanu;
  - deploy crona (`pipeline-trigger`, `db-backup`) → **tylko stan tego deployu crona**, bez sond.
    Stan innych cronów, także wcześniejszy `CRASHED` nocnego wykonania, nigdy nie wpływa na
    wynik deployu API lub weba.
- **Zielony** znaczy: powyższa kontrola przeszła. Błędy po starcie (wyjątki w logach, awarie
  w ruchu) są poza zakresem tej zmiany.
- **Czerwony** znaczy: `FAILED` / `CRASHED` wdrażanej usługi, sonda bez 200 po oknie ponowień,
  albo brak możliwości weryfikacji (stan przejściowy ponad 5 min, Railway API niedostępne).
  Raport nazywa usługę i stan.
- Jedno otwarte issue `deploy-health` na **środowisko i usługę** w repo monitora, przypisane do
  operatora: otwierane lub komentowane przy czerwieni, zamykane przez pierwszy zielony run **tej
  samej usługi**. Zielony deploy weba nie zamyka issue API ani crona.
- Stary `deploy-verify` usunięty z szczur-radar.

### Key Discoveries:

- Railway webhook: per projekt, z opcją PR envów; stała treść (`type`, `details.commitHash`,
  `resource.environment{id,name,isEphemeral}`, `resource.service{id,name}`,
  `resource.deployment{id}`); bez podpisu, do 20 własnych nagłówków; best-effort, bez kolejności,
  3 ponowienia, 30 s timeout. Nie ma zdarzenia „skipped” ani deklaracji w IaC (SDK 3.11).
  GitHub `repository_dispatch` wymaga własnego JSON-a, więc webhook potrzebuje przekaźnika.
- GitHub: `workflow_dispatch` przez REST wymaga tylko **Actions: write**; `concurrency` z
  `queue: max` trzyma do 100 oczekujących runów w grupie, nadmiar jest anulowany, kolejność nie
  jest gwarantowana, nie łączy się z `cancel-in-progress`. Przypisanie issue daje push w GitHub
  Mobile.
- Railway CLI: `railway deployment list --json -s <usługa> -e <env>` (używane w
  `verify-apply.sh:82`) zwraca `status`, `createdAt`, `meta.commitHash`. Stany: BUILDING,
  CRASHED, DEPLOYING, FAILED, INITIALIZING, NEEDS_APPROVAL, QUEUED, REMOVED, REMOVING, SKIPPED,
  SLEEPING, SUCCESS, WAITING. Wiersze `SKIPPED` niosą nowszy `commitHash`; zdrowy, bezczynny
  serwis jest `SLEEPING`. Do PR envów potrzebny jest token workspace (token projektu obejmuje jedno
  środowisko); Railway nie ma tokenów tylko do odczytu.
- Cloudflare Workers: `crypto.subtle.timingSafeEqual`; plan darmowy 100 000 żądań/dzień.
- Wzorce do powtórzenia z `tools/deploy-gate/`: `lib.sh` (`http_get` :29, `http_outcome` :40,
  `duration_seconds` :6), `web-url.sh` (origin weba z nazwy środowiska), stuby w `test.sh`
  (`railway` :498-540, `gh` :691-714), wyszukiwanie issue po nazwie środowiska jako całym słowie
  (`verify-deploy.sh:177-186`).

## What We're NOT Doing

- **Zmiany w aplikacji.** Żadnego `commit` w `/api/ready`, `/version.json`, `ARG` w Dockerfile
  ani zmian w Caddyfile. Stan deployu z Railway mówi, czy nowa paczka wstała (plan-review F2).
- **Status commita w szczur-radar.** Sygnałem jest czerwony run i issue w repo monitora; bez PAT
  do szczur-radar i bez testu „status nie blokuje Wait for CI”.
- **Historia awarii w issue** (znaczniki zbiorów, `superseded`, osobne ścieżki REMOVED). Każdy
  run patrzy na bieżący stan środowiska (plan-review F3).
- **Reconcile, harmonogram, wykrywanie zgubionych webhooków.** Zgubiony webhook to opisana luka;
  operator może uruchomić `verify.yml` ręcznie.
- **Błędy po starcie** (logi, error rate). Osobna zmiana, jeśli zajdzie potrzeba (plan-review F4).
- **Bramkowanie deployów.** Monitor nie zastępuje deploy guarda (`/api/deploy-safety`,
  `wait-safe.sh` w `railway-config`, S-14) ani reguły no-merge na czas nocnego przebiegu (S-05).
  Oba zostają bez zmian: guard trzyma deploy przed przebiegiem, monitor tylko czyta stan po
  deployu i niczego nie wyzwala.
- **Push na telefon innym kanałem niż przypisanie issue.** To F-04.
- **Sprawdzanie schematu Flyway**, **E2E Playwright**, **webhook w IaC**, **deploy Workera z CI**
  (operator robi `wrangler deploy`), **bramka produkcji w Workerze** (monitor niczego nie zmienia
  w szczur-radar, więc może od pierwszego dnia obserwować każde środowisko).

## Implementation Approach

```
Railway webhook (projekt szczur-radar, PR envy włączone, nagłówek X-Monitor-Secret)
  → Cloudflare Worker: sekret, projekt, typ zdarzenia, formaty pól
     → workflow_dispatch verify.yml w szczur-radar-monitor (PAT: Actions write, tylko to repo)
        2xx dla Railway dopiero po 2xx z GitHub, inaczej 502 → Railway ponawia
  → verify.yml: concurrency per środowisko (queue: max), środowisko GitHub "monitor"
     → verify.sh: stan każdej usługi (railway CLI) + /api/ready + /healthz → werdykt
     → zielony run (zamyka issue) albo czerwony run (exit 1) + issue przypisane do operatora
```

Kolejność faz:

1. Worker, testowany lokalnie.
2. Skrypt, workflowy i testy na stubach, lokalnie.
3. Publikacja repo, Worker i webhook (operator); sprawdzenie na PR env tej zmiany. Stary
   `deploy-verify` nadal działa.
4. Usunięcie `deploy-verify` z szczur-radar w tym samym PR; dokumentacja i runbook.

Produkcja: webhook obejmuje ją od fazy 3, bo monitor tylko czyta i pisze w swoim repo. Po merge'u
commit produkcji nie ma już starego workflow; runbook w fazie 4 opisuje sprawdzenie pierwszego
runu i wycofanie.

## Critical Implementation Details

**Reguła werdyktu (wdrażana usługa, bieżący stan, bez historii).**

- Wejście z dispatchu: `environment_id`, `environment_name`, `service_name`, `deployment_id`,
  `event_type`. `service_name` wybiera, co jest oceniane; stan liczy się z Railway, nie z treści
  webhooka.
- Stan wdrażanej usługi: `latest` = najnowszy deploy tej usługi wg `createdAt` poza `SKIPPED`,
  `REMOVED`, `REMOVING`.
  - `FAILED` / `CRASHED` → RED.
  - `BUILDING`, `DEPLOYING`, `INITIALIZING`, `QUEUED`, `WAITING`, `NEEDS_APPROVAL` → ponawiaj
    odczyt co 10 s, do 5 min łącznie; po budżecie → RED („nieweryfikowalne: <stan>”).
  - `SUCCESS` / `SLEEPING` → OK.
  - brak deployów → RED („brak deployu”).
- Dwie klasy usług (`READINESS_SERVICES`, domyślnie `szczur-radar-api,szczur-radar-web`; reszta
  to crony):
  - **usługa gotowości** (api, web): po OK stanu sondy przez origin weba (nazwa środowiska →
    origin jak w `web-url.sh`; `WEB_URL` nadpisuje w testach): `GET /api/ready` (API i `SELECT 1`),
    `GET /healthz` (Caddy żyje) i `GET /` (200 i `text/html` z odwołaniem do `/assets/`, bo
    `/healthz` to statyczna odpowiedź Caddy i nie dowodzi, że SPA jest serwowane). Ponawiane
    w oknie `WAKE_TIMEOUT=3m` (budzenie API, wymiana instancji). Brak 200 po oknie → RED.
  - **cron** (`pipeline-trigger`, `db-backup`): tylko stan deployu tego crona, bez sond. Deploy
    crona nie ocenia API ani weba, a deploy API lub weba nie czyta stanu cronów: wcześniejszy
    `CRASHED` nocnego wykonania nie oznacza nieudanego nowego deployu weba lub API.
- GREEN = stan OK i (dla usługi gotowości) wszystkie sondy 200. Inaczej RED z tabelą (usługa,
  stan, sondy).
- Webhook `Deployment.crashed` z nocnego wykonania crona daje run z werdyktem tego crona
  (RED i issue crona). To zgodne z decyzją „deploy nieudany = każda usługa”, ale nie dotyka issue
  API i weba. Jeśli payload wykonania okaże się nieodróżnialny od deployu, zachowanie jest takie
  samo i zostaje opisane w README.
- Duplikaty i zdarzenia poza kolejnością nie wymagają obsługi: każdy run liczy bieżący stan
  swojej usługi. Kolejka per środowisko (`queue: max`, bez `cancel-in-progress`) szereguje runy,
  więc issue nie ściga się samo ze sobą.

**Issue (jedno na środowisko i usługę).**

- Repo monitora, etykieta `deploy-health`, tytuł `Niezdrowe wdrożenie: <environment_name> /
  <service_name>`, `--assignee` z `MONITOR_ASSIGNEE` (push w GitHub Mobile), `GITHUB_TOKEN`
  z `issues: write`.
- RED: najstarsze otwarte issue z etykietą, którego tytuł zawiera `<environment_name> /
  <service_name>` jako całe słowa, dostaje komentarz z raportem; bez takiego issue powstaje nowe.
- GREEN: issue **tej samej usługi** dostaje komentarz „zielone po deployu <deployment_id>” i jest
  zamykane. Issue innych usług tego środowiska zostają otwarte: zielony deploy weba nie zamyka
  nierozwiązanej awarii API ani crona.
- Raport (issue i `$GITHUB_STEP_SUMMARY`): środowisko, usługa, zdarzenie, stan deployu, tabela
  sond (dla usługi gotowości), link do runu. Bez sekretów.

**Kody wyjścia `verify.sh`:** 0 GREEN; 1 RED (w tym nieweryfikowalne, błąd Railway lub `gh`);
2 złe wejście lub brak zmiennej.

**Bezpieczeństwo publicznego repo.**

- `RAILWAY_API_TOKEN` (dedykowany token workspace) tylko w środowisku GitHub `monitor`
  ograniczonym do `main`; `main` z rulesetem (bez force-push, zmiany przez PR); runy z forków
  wymagają zatwierdzenia.
- `verify.yml` ma wyłącznie `workflow_dispatch`; `ci.yml` (`pull_request`, `push`) nie używa
  środowiska ani sekretów; nigdy `pull_request_target`.
- Akcje przypięte po SHA; `permissions` jawne na górze workflow.
- Wejścia z dispatchu walidowane w Workerze (UUID, `[a-z0-9-]{1,64}`) i ponownie w skrypcie;
  przekazywane tylko przez `env:`.
- Wyciek `GITHUB_DISPATCH_TOKEN` (Actions: write, jedno repo) pozwala tylko uruchamiać i anulować
  runy monitora.
- Rotacja: PAT z datą wygaśnięcia; token Railway rotowany razem z nim (README `SECURITY.md`).

---

## Phase 1: Cloudflare Worker (przekaźnik)

### Overview

Nowe repo `szczur-radar-monitor` powstaje lokalnie (`../szczur-radar-monitor`, `git init`).
Worker przyjmuje webhook Railway i uruchamia `verify.yml`. Nic więcej.

### Changes Required:

#### 1. Worker

**File**: `worker/src/index.ts`, `worker/wrangler.toml`, `worker/package.json`,
`worker/test/index.test.ts`

**Intent**: Odrzucić wszystko, co nie jest finalnym zdarzeniem deployu projektu szczur-radar,
a resztę przekazać do GitHub. Railway dostaje 2xx dopiero, gdy GitHub przyjął dispatch.

**Contract**:
- Tylko `POST /railway`; inne ścieżki i metody → 404.
- `X-Monitor-Secret` porównany przez `timingSafeEqual` na skrótach SHA-256; zły lub brak → 401.
- `resource.project.id` ≠ `RAILWAY_PROJECT_ID` → 204.
- Przekazywane `type`: `Deployment.deployed`, `redeployed`, `failed`, `crashed`, `oom_killed`.
  Inne → 204.
- Walidacja: UUID dla id środowiska, usługi i deployu; `[a-z0-9-]{1,64}` dla nazw; niepoprawne
  → 400.
- Dispatch: `POST /repos/<GITHUB_REPO>/actions/workflows/verify.yml/dispatches`, `ref: main`,
  inputs `event_type`, `environment_id`, `environment_name`, `service_name`, `deployment_id`.
- GitHub ≠ 2xx albo błąd sieci → 502 (Railway ponawia).
- Sekrety: `WEBHOOK_SECRET`, `GITHUB_DISPATCH_TOKEN`. Zmienne: `RAILWAY_PROJECT_ID`,
  `GITHUB_REPO`.

#### 2. Szkielet repo

**File**: `README.md`, `SECURITY.md`, `.gitignore`, `LICENSE`

**Intent**: Opisać architekturę w pięciu zdaniach, sekrety z minimalnymi uprawnieniami i
rotację. Treść uzupełniana w kolejnych fazach.

### Success Criteria:

#### Automated Verification:

- Testy Workera przechodzą: `cd worker && npm test`
- Typy Workera: `cd worker && npx tsc --noEmit`

#### Manual Verification:

- `wrangler dev` lokalnie: `curl` z poprawnym sekretem i przykładowym payloadem Railway daje 502 bez tokena GitHub (ścieżka do dispatchu przebyta), zły sekret daje 401

**Implementation Note**: Po fazie zatrzymaj się na ręczne potwierdzenie.

---

## Phase 2: Skrypt weryfikacji, workflowy i testy na stubach

### Overview

`verify.sh` liczy werdykt według reguły z „Critical Implementation Details”, `verify.yml` go
uruchamia, `ci.yml` testuje repo bez sekretów. Wszystko sprawdzone lokalnie na stubach.

### Changes Required:

#### 1. Skrypt weryfikacji

**File**: `verifier/lib.sh` (HTTP i czas trwania, skopiowane z `tools/deploy-gate/lib.sh`),
`verifier/web-url.sh` (skopiowane z `tools/deploy-gate/web-url.sh`), `verifier/verify.sh`

**Intent**: Jeden skrypt: stan usług z `railway deployment list --json`, sondy, raport, issue,
kod wyjścia.

**Contract**:
- Wejścia w env: `EVENT_TYPE`, `ENVIRONMENT_ID`, `ENVIRONMENT_NAME`, `SERVICE_NAME`,
  `DEPLOYMENT_ID`, `RAILWAY_API_TOKEN`, `READINESS_SERVICES`, `MONITOR_ASSIGNEE`, `GH_TOKEN`,
  `GITHUB_REPOSITORY`; opcjonalnie `WAKE_TIMEOUT` (`3m`), `SETTLE_TIMEOUT` (`5m`), `WEB_URL`
  (testy).
- Reguła werdyktu, issue i kody wyjścia jak w „Critical Implementation Details”.
- Log na stderr z liniami `::error::` / `::notice::`, raport markdown na stdout.

#### 2. Workflowy

**File**: `.github/workflows/verify.yml`, `.github/workflows/ci.yml`

**Contract**:
- `verify.yml`: `on: workflow_dispatch` z wejściami Workera; `concurrency: { group:
  env-${{ inputs.environment_id }}, queue: max }`; `environment: monitor`; `permissions:
  { contents: read, issues: write }`; `runs-on: ubuntu-latest`; `timeout-minutes: 15`;
  instalacja `railway` CLI przypięta do wersji; `verify.sh >> $GITHUB_STEP_SUMMARY`.
- `ci.yml`: `pull_request` + `push` na `main`; `verifier/test.sh`, `shellcheck`, `npm test`
  Workera; bez środowiska i sekretów.
- Akcje przypięte po SHA.

#### 3. Testy

**File**: `verifier/test.sh`

**Intent**: Stub `railway` i `gh` w `PATH` oraz stub HTTP w Pythonie (wzorzec
`tools/deploy-gate/test.sh`); przypadki z „Testing Strategy”. Bez Dockera i bez sieci.

### Success Criteria:

#### Automated Verification:

- Testy weryfikatora przechodzą: `verifier/test.sh`
- `shellcheck verifier/*.sh` bez błędów
- Workflowy przechodzą walidację składni: `actionlint`

#### Manual Verification:

- Przegląd `SECURITY.md` i workflowów: sekret Railway tylko w środowisku `monitor`, żaden workflow z sekretami nie ma wyzwalacza z forka ani `pull_request_target`

**Implementation Note**: Po fazie zatrzymaj się na ręczne potwierdzenie przed jakąkolwiek
publikacją repo lub zmianą w Cloudflare i Railway.

---

## Phase 3: Uruchomienie monitora i sprawdzenie na PR env tej zmiany

### Overview

Operator (albo agent po jego wyraźnej zgodzie) publikuje repo, wdraża Worker i podłącza
webhook. Monitor obserwuje wszystkie środowiska, także produkcję, bo niczego w niej nie zmienia.
Stary `deploy-verify` w szczur-radar nadal działa.

### Changes Required:

#### 1. Publikacja repo i środowisko GitHub (operator)

**File**: `README.md` (sekcja „Uruchomienie”)

**Contract**:
- Repo `kwmacioszek/szczur-radar-monitor`, publiczne, ruleset na `main`, runy z forków
  wymagają zatwierdzenia.
- Środowisko `monitor` (deployment branches: tylko `main`) z sekretem `RAILWAY_API_TOKEN`
  (dedykowany token workspace).
- Zmienne repo: `RAILWAY_PROJECT_ID`, `READINESS_SERVICES`, `MONITOR_ASSIGNEE`.

#### 2. Worker i webhook (operator)

**File**: `README.md`

**Contract**:
- `wrangler deploy`; `wrangler secret put WEBHOOK_SECRET` i `GITHUB_DISPATCH_TOKEN` (fine-grained
  PAT: tylko repo monitora, Actions: write, data wygaśnięcia).
- Webhook Railway na projekcie szczur-radar: URL Workera, nagłówek `X-Monitor-Secret`, PR envy
  włączone, zdarzenia `Deployment.*` z listy Workera. To jedyna zmiana w projekcie Railway;
  wymaga zgody operatora.

### Success Criteria:

#### Automated Verification:

- CI monitora zielone na `main`
- `gh api repos/kwmacioszek/szczur-radar-monitor/environments/monitor` pokazuje ograniczenie do `main`

#### Manual Verification:

- Push do PR tej zmiany daje zielony run `verify.yml` na usługę z raportem środowiska `szczur-radar-pr-<N>`; raport weba pokazuje 200 z `/api/ready`, `/healthz` i `/`
- Wymuszona awaria w PR env (np. `LLM_TIMEOUT=0s` na API, start pada na `LlmConfigurationCheck`) daje czerwony run z nazwą usługi i stanem `FAILED`, choć starszy deploy API nadal serwuje, oraz jedno issue `Niezdrowe wdrożenie: szczur-radar-pr-<N> / szczur-radar-api` przypisane do operatora
- Przypisanie issue daje rzeczywiste powiadomienie push w GitHub Mobile na telefonie operatora (zrzut ekranu lub potwierdzenie w issue)
- Przy otwartym issue API commit zmieniający tylko `szczur-radar-web/` daje zielony run weba, a issue API zostaje otwarte
- Po usunięciu zmiennej redeploy API daje zielony run i zamknięte issue API z komentarzem
- Ponowne wysłanie tego samego webhooka (curl do Workera z sekretem) daje drugi run w kolejce z tym samym wynikiem i bez nowego issue
- Webhook z błędnym sekretem dostaje 401 i nie uruchamia runu
- Deploy produkcji w tym czasie (jeśli nastąpi) daje zielony run bez issue; w szczur-radar nie pojawia się nic nowego
- Payload deployu crona (`db-backup` lub `pipeline-trigger`) daje run z werdyktem tylko tego crona, bez sond; cron w stanie `CRASHED` (np. `TRIGGER_IN_ANY_ENVIRONMENT=true` bez działającego API) nie zmienia wyniku następnego deployu weba lub API

**Implementation Note**: Po fazie zatrzymaj się na ręczne potwierdzenie.

---

## Phase 4: Wycofanie starego deploy-verify, dokumentacja i runbook

### Overview

Stary `deploy-verify` znika z szczur-radar w tym samym PR. Dokumentacja i runbook opisują nowy
monitor oraz sprawdzenie po merge'u.

### Changes Required:

#### 1. Usunięcie starego mechanizmu

**File**: `.github/workflows/deploy-verify.yml` (usunięty), `tools/deploy-gate/verify-deploy.sh`
(usunięty), `tools/deploy-gate/test.sh`

**Intent**: Usunąć workflow, skrypt i krok 12 testów, łącznie ze stubem `gh`, jeśli nic innego
go nie używa. `web-url.sh`, `verify-apply.sh` i `wait-safe.sh` zostają.

**Contract**: `tools/deploy-gate/test.sh` bez `deploy_case`/`run_deploy_check`; numeracja
kroków w nagłówku spójna.

#### 2. Odwołania w kodzie i CI

**File**: `tools/deploy-gate/verify-apply.sh:18,165-166`, `.github/workflows/ci.yml:489`

**Intent**: Zastąpić „handed over to deploy-verify” przekazaniem do monitora
`szczur-radar-monitor`. Tylko komentarze i komunikaty, logika bez zmian.

#### 3. Dokumentacja

**File**: `tools/deploy-gate/README.md`, `REPOSITORY_GUIDELINES.md:23,62`,
`context/deployment/deploy-plan.md`

**Intent**: Opisać nowy monitor; sekcję S-14 oznaczyć jako zastąpioną w części `deploy-verify`.

**Contract**: nowa sekcja `deploy-verification-hardening` w `deploy-plan.md`:
- architektura, repo monitora, Worker, webhook;
- sekrety, uprawnienia i rotacja (PAT z datą wygaśnięcia, token Railway);
- **runbook po merge'u** (poza Progress):
  1. merge przez człowieka;
  2. sprawdzenie, że deploy produkcji dał zielone runy `verify.yml` dla każdej wdrożonej usługi;
  3. wycofanie: przywrócenie `deploy-verify.yml` revertem i wyłączenie webhooka w Railway;
- ograniczenia: zgubiony webhook lub przepełnienie kolejki (>100) pomija weryfikację; ręczne
  uruchomienie `verify.yml` z danymi usługi; nocne `CRASHED` crona daje czerwony run i issue
  tego crona, bez wpływu na deploye API i weba;
  token Railway w publicznym repo.

### Success Criteria:

#### Automated Verification:

- `tools/deploy-gate/test.sh` przechodzi
- `grep -rn "verify-deploy.sh\|deploy-verify.yml" --exclude-dir=context .` nic nie znajduje
- CI szczur-radar na PR zielone

#### Manual Verification:

- Na head SHA po pushu tej fazy nie ma żadnego runu `deploy-verify` (`gh run list --workflow deploy-verify.yml --commit <sha>` puste)
- Runbook w `deploy-plan.md` przeczytany i zaakceptowany przez operatora

---

## Testing Strategy

### Unit Tests (Worker, Vitest, mock `fetch`):

- zły lub brakujący sekret → 401;
- obcy projekt → 204 bez dispatchu;
- typ spoza listy → 204;
- pola z niepoprawnym formatem → 400;
- poprawne zdarzenie → dispatch z oczyszczonymi wejściami i `ref: main`;
- GitHub 5xx lub błąd sieci → 502.

### Integration Tests (`verifier/test.sh`, stuby):

**Werdykt:**
- api `SUCCESS`/`SLEEPING` + trzy sondy 200 → GREEN, exit 0;
- api `FAILED` przy sondach 200 (stara wersja serwuje) → RED z nazwą usługi, exit 1;
- deploy weba przy crona `CRASHED` → GREEN (stan cronów nieczytany; stub `railway` to loguje);
- deploy crona `CRASHED` → RED bez wywołań sond; deploy crona `SUCCESS` → GREEN bez sond;
- `DEPLOYING` → ponowienia → `SUCCESS` → GREEN; `FAILED` → RED;
- stan przejściowy do końca `SETTLE_TIMEOUT` → RED „nieweryfikowalne”;
- `SKIPPED` z nowszym commitem ignorowane; brak deployów → RED;
- `/api/ready` 503 przez całe okno → RED; 503 potem 200 → GREEN;
- `GET /` 200 bez `text/html` lub bez `/assets/` → RED.

**Issue:**
- RED bez issue → nowe `Niezdrowe wdrożenie: <env> / <usługa>`, z etykietą i assignee;
- RED z otwartym issue tej usługi → komentarz, bez nowego;
- `szczur-radar-pr-8 / szczur-radar-api` nie pasuje do `szczur-radar-pr-81 / szczur-radar-api`;
- GREEN z otwartym issue tej usługi → komentarz i zamknięcie;
- GREEN weba przy otwartym issue API → issue API zostaje otwarte, bez komentarza;
- GREEN bez issue → brak wywołań `gh`.

**Błędy:** `railway` niezerowy kod → RED, exit 1; `gh` niezerowy → exit 1; brak zmiennej → exit 2.

### Manual Testing Steps:

1. Fazy 1 i 3: scenariusze z sekcji Manual Verification.
2. Faza 4: brak runów `deploy-verify` przy nowym head SHA.
3. Po merge'u: runbook z `deploy-plan.md`, poza Progress.

## Performance Considerations

- Jeden run na finalne zdarzenie usługi: około 2–4 na deploy, na darmowych runnerach repo
  publicznego; każdy run to kilka wywołań `railway` i dwie sondy.
- Sondy budzą uśpione API tylko po deployu, nie cyklicznie.

## Migration Notes

- Otwarte issue `deploy-health` w szczur-radar zamyka operator ręcznie po przeglądzie; monitor
  prowadzi issue tylko w swoim repo.
- Do merge'a produkcję obsługuje stary `deploy-verify`, a monitor obserwuje ją równolegle od
  fazy 3. Commit z merge'u nie ma już starego workflow.

## References

- Frame: `context/changes/deploy-verification-hardening/frame.md`
- Przegląd planu: `context/changes/deploy-verification-hardening/reviews/plan-review.md`
- S-14: `context/archive/2026-10-18-safe-deploy-gate/`, `context/deployment/deploy-plan.md:887-1038`
- Kod: `tools/deploy-gate/verify-deploy.sh`, `lib.sh`, `web-url.sh`, `test.sh`,
  `verify-apply.sh:82`, `HealthController.java:37-46`, `Caddyfile:18-20`
- Railway docs: `deployments/github-autodeploys#wait-for-ci`, `observability/webhooks`,
  `integrations/api/manage-deployments`
- GitHub docs: control-workflow-concurrency (`queue: max`),
  permissions-required-for-fine-grained-personal-access-tokens, rest/actions/workflows
- Cloudflare docs: workers/runtime-apis/web-crypto (`timingSafeEqual`), workers/platform/limits

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Cloudflare Worker (przekaźnik)

#### Automated

- [x] 1.1 Testy Workera przechodzą: `cd worker && npm test` — szczur-radar-monitor@61ec310
- [x] 1.2 Typy Workera: `cd worker && npx tsc --noEmit` — szczur-radar-monitor@61ec310

#### Manual

- [ ] 1.3 `wrangler dev`: poprawny sekret i payload dają 502 bez tokena GitHub, zły sekret 401

### Phase 2: Skrypt weryfikacji, workflowy i testy na stubach

#### Automated

- [x] 2.1 Testy weryfikatora przechodzą: `verifier/test.sh` — szczur-radar-monitor@7eb658c
- [x] 2.2 `shellcheck verifier/*.sh` bez błędów — szczur-radar-monitor@7eb658c
- [x] 2.3 Workflowy przechodzą walidację składni: `actionlint` — szczur-radar-monitor@7eb658c

#### Manual

- [ ] 2.4 Przegląd `SECURITY.md` i workflowów: sekret tylko w środowisku `monitor`, bez wyzwalaczy z forka

### Phase 3: Uruchomienie monitora i sprawdzenie na PR env tej zmiany

#### Automated

- [x] 3.1 CI monitora zielone na `main` — szczur-radar-monitor@450083d
- [x] 3.2 Środowisko GitHub `monitor` ograniczone do `main` — szczur-radar-monitor@450083d

#### Manual

- [ ] 3.3 Push do PR daje zielony run na usługę; raport weba ma 200 z `/api/ready`, `/healthz` i `/`
- [ ] 3.4 Wymuszona awaria daje czerwony run z nazwą usługi i jedno issue środowiska i usługi przypisane do operatora
- [ ] 3.5 Przypisanie issue daje rzeczywisty push w GitHub Mobile
- [ ] 3.6 Zielony deploy samego weba nie zamyka otwartego issue API
- [ ] 3.7 Redeploy API po naprawie daje zielony run i zamknięte issue API
- [ ] 3.8 Duplikat webhooka: ten sam wynik, bez nowego issue
- [ ] 3.9 Błędny sekret: 401, brak runu
- [ ] 3.10 Deploy produkcji daje zielony run bez issue; w szczur-radar nic nowego
- [ ] 3.11 Deploy crona oceniany osobno, bez sond; `CRASHED` crona nie zmienia wyniku deployu weba lub API

### Phase 4: Wycofanie starego deploy-verify, dokumentacja i runbook

#### Automated

- [x] 4.1 `tools/deploy-gate/test.sh` przechodzi — ad27491
- [x] 4.2 Brak odwołań do `verify-deploy.sh` i `deploy-verify.yml` poza `context/` — ad27491
- [x] 4.3 CI szczur-radar na PR zielone

#### Manual

- [ ] 4.4 Brak runów `deploy-verify` przy nowym head SHA
- [ ] 4.5 Runbook zaakceptowany przez operatora
