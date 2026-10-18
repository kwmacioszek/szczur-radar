# S-14: Bezpieczna bramka wdrożeń — plan implementacji

## Overview

Bramka wdrożeń dziś chroni tylko przed zniszczeniem zasobów. Ten plan zamyka luki C4–C7 z audytu po MVP:
- IaC stosuje się dopiero po zielonych testach;
- przed apply i deployem strażnik pyta API, czy nie trwa przebieg pipeline'u, i czeka do 90 min;
- po apply job sprawdza zdrowie produkcji, a po deployu kodu robi to osobny workflow, który alarmuje przez issue;
- crony czekają na CI;
- ręcznie ustawione zmienne strojenia nie blokują wdrożeń.

Logika bramki wychodzi z YAML do testowanego narzędzia `tools/deploy-gate/`.

Roadmapa: S-14 (`context/foundation/roadmap.md:223-234`), MS-02, MS-03. Issue: #73.

## Current State Analysis

Pełne ustalenia: `context/changes/safe-deploy-gate/research.md`.

- **CI** (`.github/workflows/ci.yml`, jedyny workflow):
  - 7 jobów i żaden nie ma `needs:`, więc `railway-config` (`:205`) stosuje IaC na produkcji równolegle z testami (C4).
  - Jedyny `concurrency:` jest na `railway-config` (`:218-220`), z `cancel-in-progress: false`.
  - CLI jest przypięte do `@railway/cli@5.62.1` (`:240`).
- **Bramka** (`ci.yml:340-377`) to inline `jq`:
  - kontrola środowiska (`:345-352`);
  - odrzucenie `severity == "destructive"` lub `kind == "resource.delete"` (`:354`);
  - odrzucenie każdej zmiany innej niż `resource.create`, której `summary`/`details` pasuje do regexu `source` (`:362-366`);
  - apply wywołaniem `railway config apply --yes` (`:377`), osobnym od planu (brak przypiętego planu).
  - Nic nie dzieje się po apply: brak czekania na deploy, brak `/api/ready`.
  - Brak testów i fixture'ów.
- **Plan JSON** (źródło CLI `src/iac/change_set.rs`) ma pola strukturalne:
  - `kind` (`resource.create|update|delete`, `variable.set|delete`, `domain.create`), `address`, `field`, `before`, `after`, `summary`, `details`, `severity` (`safe|destructive`), `deployEffect` (`deploy|none`);
  - `variable.delete` ma zawsze `severity: "destructive"`.
- **Crony bez Wait for CI (C5):**
  - `db-backup` (`.railway/railway.ts:223-224`) i `pipeline-trigger` (`:263-264`) nie mają `checkSuites`.
  - Na merge'u `5b6eabb` `pipeline-trigger` deployował się w chwili pusha, przed CI.
  - Przeszkoda: regex `source` odrzuca `source.checkSuites (null → true)` (`deploy-plan.md:347`).
  - Komentarz `railway.ts:221-222` („CI does not test tools/db-backup”) jest nieaktualny (`ci.yml:180-203`).
- **Ręczne zmienne (C7):**
  - Na cronach jedyne `preserve()` to `TRIGGER_IN_ANY_ENVIRONMENT` (`railway.ts:273`).
  - Niezadeklarowane, a zalecane w dokumentacji: `BACKUP_IN_ANY_ENVIRONMENT`, `BACKUP_TIMEOUT`, `S3_FORCE_PATH_STYLE`, `TRIGGER_*`, `JAVA_TOOL_OPTIONS`.
  - Ręczne ustawienie którejkolwiek daje `variable.delete`, bramka odmawia, a na `main` stają deploye.
- **Zdrowie (C6):**
  - `/api/health` to liveness bez bazy (`HealthController.java:28-31`) i to on jest healthcheckiem Railway (`railway.ts:115`).
  - `/api/ready` robi `SELECT 1` (`:37-46`).
  - Ręczna lista kontrolna po merge'u: `deploy-plan.md:142-145`.
  - Incydent 2026-10-12: apply „bezpiecznej” zmiany zrestartował Postgresa, który zawisł na 9 min (`deploy-plan.md:384-386`).
- **Stan przebiegów:**
  - Trwały w Postgresie (`pipeline_runs`, statusy `QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, INTERRUPTED` — `pipeline/run/RunStatus.java:13`).
  - Predykat zajętości: `PipelineRunRepository.existsActive()` (`pipeline/run/PipelineRunRepository.java:19-23`), używany pod advisory lockiem w `PipelineRunService.queue()` (`PipelineRunService.java:175-183`).
  - Publicznie widać tylko `/api/health` i `/api/ready` (`config/SecurityConfig.java:102`).

## Desired End State

- **Kolejność na `main` i na PR:** testy (6 jobów + `deploy-gate-test`) → `railway-config`:
  1. strażnik `deploy-safety`;
  2. plan przypięty do pliku;
  3. bramka z testowanymi regułami;
  4. apply przypiętego planu;
  5. kontrola po apply.
  Czerwony test pomija apply i — przez Wait for CI — deploy kodu.
- **Bramka:**
  - odrzuca destrukcję, usunięcia i zmiany źródła;
  - przepuszcza tylko włączenie Wait for CI (`checkSuites → true`);
  - w podsumowaniu joba wypisuje usługi, które apply zrestartuje.
- **`GET /api/deploy-safety`** (publiczny) zwraca `{"safe":true,"reason":null}` albo `{"safe":false,"reason":"pipeline_running"|"pipeline_queued"}`.
- **Strażnik:**
  - czeka do 90 min na `safe=true`, potem pada;
  - brak decyzji (401/404 starego API, niedostępne API, 503) przepuszcza z ostrzeżeniem;
  - zmienna repo `DEPLOY_GUARD=off` go wyłącza.
- **Po apply:**
  - job czeka na deploy restartowanych usług i `/api/ready` 200;
  - błąd kończy job na czerwono;
  - deploy w `WAITING` (czeka na ten workflow) jest przekazany do `deploy-verify`.
- **Workflow `deploy-verify`** (`on: deployment_status`):
  - po deployu sprawdza `/api/ready` i `/healthz`;
  - przy problemie otwiera lub uzupełnia issue z etykietą `deploy-health`;
  - sam kończy się na zielono.
- **Crony:** `checkSuites: true`.
- **`railway.ts`:** ma politykę `variables.ignore` dla zmiennych strojenia, a plan produkcji wychodzi 0.
- **Weryfikacja:**
  - `tools/deploy-gate/test.sh` zielony lokalnie i w CI;
  - `./mvnw -B verify` zielony;
  - w PR env widać wszystkie etapy w logu `railway-config` i run `deploy-verify`;
  - po merge'u produkcja przechodzi tę samą ścieżkę według runbooka S-14 w `deploy-plan.md` (Phase 6 §3, poza Progress).

### Key Discoveries:

- `variable.delete` → `severity: "destructive"` (`change_set.rs:832-841`). Reguła `severity` już go łapie, więc C7 rozwiązuje się po stronie deklaracji, nie bramki.
- `deployEffect` jest `"none"` tylko dla pól `config`, `groupId`, `cdn` i samych `tracing.autoInstrumentation` (`change_set.rs:1886-1905`). Każda inna zmiana usługi wywołuje deploy.
- Polityka `variables: { managed, ignore }` jest w CLI od v5.64.0 (PR railwayapp/cli#1256): ignorowane klucze „are removed from both sides before diffing: never set, never deleted”. Typy SDK `railway` 3.11.0 nie mają tego pola (`ProjectDefinition`, `d.ts:248-261`), runtime je przekazuje.
- Wait for CI (dokumentacja Railway): deploy czeka na konkluzję **każdego** workflow przy commicie. `failure` pomija deploy, `skipped`/`neutral` nie blokuje. Po 2 h deploy jest pomijany.
- Railway publikuje w GitHubie Deployments per środowisko (nazwa `szczur-radar / production`) i statusy commita per usługa (obserwacja na tym repo).
- Wzorzec narzędzia operacyjnego: `tools/<narzędzie>/` z `README.md` i `test.sh` uruchamianym przez osobny job CI (`tools/pipeline-trigger/test.sh`, atrapa API w Pythonie).
- Wzorce testów API:
  - `health/HealthIT.java` (`MockMvcTester`, `@Nested WithoutDatabase` dla 503);
  - `pipeline/api/FreshnessIT.java` (statusy ustawiane JDBC; najpierw zakończyć istniejące `queued`/`running`; indeks `uq_pipeline_runs_one_running` dopuszcza jeden `running`).
- `PUBLIC_ROUTES` to `Map.of` w `AuthorizationCompletenessIT.java:51-59` (6 z 10 możliwych par).
- Adresy weba: produkcja `https://szczur-radar-web-production.up.railway.app`, PR env `https://szczur-radar-web-szczur-radar-pr-<N>.up.railway.app` (`deploy-plan.md:11,582`).
- Repo jest prywatne, więc minuty Actions się liczą. Push kosztuje ~13 min (7 jobów, zaokrąglenie do minuty); strażnik dodaje 1 min, a czekanie kosztuje tylko przy kolizji z przebiegiem.

## What We're NOT Doing

- **Odrzucanie restartów:** zmiany z `deployEffect: "deploy"` (także na Postgresie) są tylko ogłaszane i sprawdzane po apply (decyzja planu).
- **Okno nocne w strażniku:** API nie zna harmonogramu crona. Zostaje ludzka zasada „nie merguj 00:30–01:30 UTC”, łagodzona strażnikiem.
- **Wyścig sprawdź–wdroż:** przebieg uruchomiony między `safe=true` a startem deployu nadal zostanie przerwany. S-06 zgłasza go dzwonkiem.
- **Naprawa zombie (audyt A2)** to S-11. Tu tylko runbook i wyłącznik.
- **Zmiana healthchecka Railway** API z `/api/health` na `/api/ready`.
- **Usuwanie zbędnych zmiennych IaC** (`RAILPACK_JDK_VERSION`, `DATABASE_URL`, `JWT_SECRET`) to S-19.
- **Przeniesienie istniejących `preserve()` na API do `ignore`** (sekrety `LLM_*`, `WYKOP_*`, `PIPELINE_*` zostają zadeklarowane).
- **Dead-man's switch dla cronów** (C2) to osobny slice.
- **Zmiany w SPA i w `dev/mock-api.js`** (endpoint służy tylko CI).
- **Strażnik dla fork-PR-ów** (`railway-config` je pomija).

## Implementation Approach

Narzędzie `tools/deploy-gate/` składa się z czterech skryptów i jednego helpera:
- `web-url.sh <env-name>` — adres weba środowiska (`production` → `https://szczur-radar-web-production.up.railway.app`, `szczur-radar-pr-<N>` → `https://szczur-radar-web-szczur-radar-pr-<N>.up.railway.app`, nieznana nazwa → exit 2), wspólny dla `railway-config` i `deploy-verify`;
- `gate.sh` — klasyfikuje plan JSON;
- `wait-safe.sh` — strażnik `deploy-safety`;
- `verify-apply.sh` — kontrola po apply;
- `verify-deploy.sh` — kontrola i alarm po deployu kodu.

Każdy skrypt ma kontrakt wejścia/wyjścia i kody wyjścia. `test.sh` sprawdza je na fixture'ach planu oraz na atrapie API i atrapach `railway`/`gh` w `PATH`, bez Railway i bez GitHuba. CI tylko wywołuje skrypty, więc logika ma jedno miejsce i testy.

Kolejność faz:
1. Najpierw bramka i `needs:`, bo chronią każdy kolejny merge.
2. Potem deklaracje (crony, zmienne), które polegają na nowej regule `checkSuites`.
3. Następnie endpoint API.
4. Strażnik i kontrola po apply, które go używają.
5. Workflow po deployu.
6. Dokumentacja i weryfikacja produkcji.

Wszystko trafia jednym PR-em (`feature/safe-deploy-gate`), weryfikowanym w PR env. Na `main` merguje człowiek.

## Critical Implementation Details

### Timing & lifecycle

**Kontrola po apply nie może czekać na deploy wstrzymany przez Wait for CI.** Deploy wywołany przez apply na usłudze z `checkSuites: true` (api, web, a po Phase 2 także crony) może stać w `WAITING` aż do końca tego samego workflow (nieustalone, research Open Question 1). Czekanie na `SUCCESS` dałoby wtedy zakleszczenie. `verify-apply.sh` traktuje `WAITING`/`QUEUED` po apply jako „przekazane do `deploy-verify`” i nie czeka. Czeka do końca tylko na usługi bez Wait for CI (Postgres) i na deploye, które już ruszyły.

**Budżet Wait for CI to 2 h od pusha:** testy (~4 min) + strażnik (≤ 90 min) + apply i kontrola (≤ 15 min) ≈ 110 min. Limity strażnika i kontroli są parametrami, a ich sumy nie wolno podnosić bez skrócenia drugiego. Limity względne nie wystarczą: kolejka runnerów, wolny `backend` albo czekanie w grupie `concurrency` przesuwają start joba. Po 2 h Railway pomija deploy i oznacza GitHub Deployment jako `inactive`, a `inactive` nie uruchamia żadnego workflow (dokumentacja GitHuba, `deployment_status`). `ci.yml` byłby wtedy zielony, `deploy-verify` by milczał, a kod by się nie wdrożył. Dlatego strażnik liczy **bezwzględny termin od utworzenia runu** (`created_at` runu, wspólne dla wszystkich prób) i po jego przekroczeniu kończy się na czerwono.

**Kolejka `concurrency`:** strażnik czeka wewnątrz `railway-config`, więc trzyma grupę. Trzeci merge w tym czasie anuluje oczekujący drugi. Commit drugiego nie wdroży się osobno, ale trzeci go zawiera. To akceptowalne i trafia do runbooka.

### State sequencing

**Pierwszy merge na produkcję:** strażnik pyta produkcyjne API, które jeszcze nie ma endpointu. Stare API odpowiada **401**, nie 404: deny-by-default (`anyRequest().authenticated()` + `JsonAuthenticationEntryPoint`, `SecurityConfig.java:86-87`) odrzuca żądanie bez sesji, zanim zadziała routing. 401 i 404 muszą przepuszczać z ostrzeżeniem, inaczej S-14 nigdy by się nie wdrożyło. To samo dotyczy pierwszego pusha w PR env.

## Phase 1: Bramka jako testowane narzędzie i kolejność jobów

### Overview

Logika bramki przechodzi z YAML do `tools/deploy-gate/gate.sh` z regułami na polach strukturalnych planu, fixture'ami i `test.sh`. Nowy job `deploy-gate-test` uruchamia te testy. `railway-config` dostaje `needs:` na wszystkie joby testowe, CLI 5.67.x i przypięty plan. Domyka C4 i część C6 (testy bramki, ogłaszanie restartów).

### Changes Required:

#### 1. Klasyfikator planu

**File**: `tools/deploy-gate/gate.sh` (+ ewentualnie `tools/deploy-gate/gate.jq`)

**Intent**: Jedno miejsce, które z `plan.json` wyznacza: czy plan jest dozwolony, dlaczego nie, które usługi zrestartuje i ile jest zmian. Reguły na polach `kind`/`field`/`severity`/`deployEffect`/`before`/`after` zamiast regexu na tekście.

**Contract**:
- Wywołanie: `gate.sh <plan.json> <target-env> <project-id>`.
- Kody wyjścia: `0` — dozwolony; `1` — odrzucony (komunikaty `::error::` z listą zmian); `2` — złe wywołanie albo nieczytelny plan.
- Na stdout markdown do `$GITHUB_STEP_SUMMARY`: liczba zmian, lista zmian, sekcja „Restartuje: …” z adresami zmian `deployEffect == "deploy"`.
- Do pliku `restarts.txt` (ścieżka z `GATE_RESTARTS_FILE`): jeden adres usługi na linię, wejście dla `verify-apply.sh`.
- Reguły, w tej kolejności:
  1. `.ok == true`, `currentEnvironment.environmentName == target-env`, `currentEnvironment.projectId == project-id`, inaczej odrzuć.
  2. Odrzuć `severity == "destructive"` (dziś łapie m.in. `resource.delete` i `variable.delete`).
  3. Lista dozwolonych rodzajów zmian (fail closed): `resource.create`, `resource.update`, `variable.set`, `domain.create`. Każdy inny `kind` jest odrzucany, także `resource.delete`, `variable.delete` i rodzaj nieznany bramce, niezależnie od `severity`.
  4. Odrzuć `kind == "resource.update"` z `field == "source"`, **chyba że** `before` i `after` różnią się wyłącznie kluczem `checkSuites`, a `after.checkSuites == true`.
  5. Zmiana bez pola `kind` albo `resource.update` bez pola `field` to odrzucenie (fail closed na nieznany format).
  6. Brak `deployEffect` traktuj jak `"deploy"` (ogłoś restart).

#### 2. Fixture'y i test

**File**: `tools/deploy-gate/fixtures/*.json`, `tools/deploy-gate/test.sh`, `tools/deploy-gate/README.md`

**Intent**: Każda reguła ma przypadek pozytywny i negatywny. Fixture'y odwzorowują prawdziwy format planu. Pierwszy powstaje z prawdziwego `railway config plan --json --out` złapanego w PR env tej zmiany, z wartościami zredagowanymi (CLI redaguje je domyślnie, bez `--show-values`).

**Contract**:
- Przypadki:
  - pusty plan;
  - zła nazwa środowiska;
  - `ok:false`;
  - `variable.delete`;
  - `resource.delete`;
  - zmiana `source.branch`;
  - `checkSuites null → true` (dozwolone);
  - `checkSuites true → null` (odrzucone);
  - `checkSuites` razem z `branch` (odrzucone);
  - `limitOverride` na Postgresie (dozwolone, ogłoszony restart);
  - `variable.set`;
  - `resource.create`;
  - zmiana bez `field`;
  - nieznany `kind` (np. `domain.delete`) z `severity: "safe"` (odrzucone);
  - `variable.delete` z `severity: "safe"` (odrzucone przez listę dozwolonych, nie przez `severity`).
- `test.sh` w stylu `tools/pipeline-trigger/test.sh` (`step`/`pass`/`die`) i bez Dockera dla `gate.sh`. Uruchamialny z dowolnego katalogu.
- `README.md` opisuje reguły, kody wyjścia i sposób odświeżenia fixture'a z prawdziwego planu.

#### 3. Workflow: kolejność, CLI, przypięty plan

**File**: `.github/workflows/ci.yml`

**Intent**:
- Nowy job `deploy-gate-test` uruchamia `tools/deploy-gate/test.sh`.
- `railway-config` dostaje `needs:` na wszystkie joby testowe (`backend`, `web-unit`, `web-container-smoke`, `api-container-smoke`, `db-backup-test`, `pipeline-trigger-test`, `deploy-gate-test`).
- Krok „Plan and gate” wywołuje `gate.sh`.
- Apply stosuje przypięty plan.

**Contract**:
- `npm i -g @railway/cli@5.67.1`.
- `railway config plan --json --out plan.pin > plan.json`, potem `gate.sh plan.json "$TARGET_ENV" "$RAILWAY_PROJECT_ID" >> "$GITHUB_STEP_SUMMARY"`, potem `railway config apply --plan plan.pin --yes`. Nigdy `--confirm-destructive`.
- Apply tylko gdy liczba zmian > 0 (jak dziś `PENDING_CHANGES`).
- `plan.json` jako artefakt joba (`actions/upload-artifact`, krótka retencja). Wartości są redagowane przez CLI.
- Komentarz przy jobie wyjaśnia, dlaczego `needs:` nie łamie zasady „konfiguracja przed kodem” (Wait for CI czeka na cały workflow).
- Jeśli `--json` i `--out` nie działają razem w 5.67.1, zostaje fallback: dwa wywołania planu, a apply przypiętego pliku. Ustalić w PR env i zapisać w README.

### Success Criteria:

#### Automated Verification:

- `tools/deploy-gate/test.sh` przechodzi lokalnie (każda reguła z przypadkiem pozytywnym i negatywnym)
- Job `deploy-gate-test` zielony w CI na PR
- `railway-config` na PR startuje dopiero po zakończeniu wszystkich jobów testowych (widać w grafie runu) i jest zielony
- `grep -n "needs:" .github/workflows/ci.yml` pokazuje `needs:` na `railway-config`

#### Manual Verification:

- W PR env plan z CLI 5.67.1 zawiera `field` i `deployEffect`; fixture z prawdziwego planu dodany do `fixtures/`
- Podsumowanie joba `railway-config` w PR env pokazuje liczbę zmian i sekcję restartów
- `railway config plan --environment production --detailed-exit-code` z CLI 5.67.1 (lokalnie, tylko odczyt) pokazuje tylko oczekiwane różnice — zmiana wersji CLI nie wprowadza nowego diffu na produkcji

**Implementation Note**: Po automatycznej weryfikacji zatrzymaj się na potwierdzenie ręcznych kroków przez człowieka.

---

## Phase 2: Crony z Wait for CI i ignorowane zmienne strojenia

### Overview

Crony deklarują `checkSuites: true`, co nowa reguła bramki przepuszcza. `project(...)` dostaje politykę `variables.ignore` dla zmiennych strojenia. Nieaktualne komentarze i README zostają poprawione. Domyka C5 i C7.

### Changes Required:

#### 1. Crony z `checkSuites`

**File**: `.railway/railway.ts`

**Intent**: `db-backup` i `pipeline-trigger` czekają na CI jak api i web. Merge zmieniający `tools/**` z czerwonym testem nie wdroży crona.

**Contract**:
- `source: github(REPO, { branch, checkSuites: true })` na obu cronach.
- Komentarze `:16-20`, `:218-222`, `:262` przepisane: crony mają `checkSuites`; bramka przepuszcza tylko `checkSuites → true`; CI testuje oba narzędzia (`db-backup-test`, `pipeline-trigger-test`).

#### 2. Polityka zmiennych

**File**: `.railway/railway.ts`

**Intent**: Zmienna strojenia ustawiona ręcznie w Railway nie daje `variable.delete`. IaC jej nie ustawia i nie usuwa.

**Contract**:
- `project("szczur-radar", { variables: { ignore: [...] }, ... })`.
- Wzorce per usługa, wyłącznie dla zmiennych strojenia, których IaC nie deklaruje:
  - `db-backup/BACKUP_IN_ANY_ENVIRONMENT`, `db-backup/BACKUP_TIMEOUT`, `db-backup/S3_FORCE_PATH_STYLE`;
  - `pipeline-trigger/TRIGGER_*`;
  - `szczur-radar-api/JAVA_TOOL_OPTIONS`.
- **Nie** ignorować kluczy zadeklarowanych literałem (`BACKUP_KEEP_*`), bo ignorowany klucz znika z obu stron diffu.
- `TRIGGER_IN_ANY_ENVIRONMENT: preserve()` usunięte, bo przejmuje je `TRIGGER_*`.
- Pole spoza typów SDK 3.11.0: rzutowanie typu z komentarzem (wersja CLI ≥ 5.64.0, PR railwayapp/cli#1256).
- Pole `managed` pominięte, chyba że plan pokaże, że jest wymagane. Wtedy `managed: true` i sprawdzenie, że Postgres (zasób bez `env`) nie daje błędu planu.
- Weryfikacja działania (`lessons.md:106-111`, pole nieznane SDK może zniknąć bez błędu):
  - plan `--json` pokazuje `variablePolicy` z `ignoredCount`/`byPattern`;
  - próbna zmienna ustawiona ręcznie w PR env nie daje `variable.delete`.

#### 3. Dokumentacja narzędzi

**File**: `tools/db-backup/README.md`, `tools/pipeline-trigger/README.md`

**Intent**: README mówią, że zmienne strojenia można ustawiać ręcznie, bo `railway.ts` je ignoruje. Zmienna spoza listy wymaga dopisania wzorca do `variables.ignore`.

**Contract**: `tools/pipeline-trigger/README.md:90` przepisane z `preserve()` na `variables.ignore`. W `tools/db-backup/README.md` dopisana ta sama reguła.

### Success Criteria:

#### Automated Verification:

- `railway-config` w PR env zielony; bramka przepuszcza zmianę `checkSuites → true` na obu cronach i stosuje ją
- Plan `--json` w PR env zawiera `variablePolicy` z wzorcami z `railway.ts`
- `tools/deploy-gate/test.sh` dalej przechodzi (fixture `checkSuites` odpowiada prawdziwej zmianie z PR env)

#### Manual Verification:

- W PR env `TRIGGER_HEARTBEAT_INTERVAL=1m` ustawione ręcznie (`--skip-deploys`) nie daje `variable.delete` w kolejnym planie; po sprawdzeniu zmienna usunięta
- Żywa konfiguracja obu cronów w PR env pokazuje Wait for CI włączone (MCP `get-service-config` albo panel), nie tylko pusty plan
- Kolejny push na PR: cron deployuje się dopiero po zakończeniu CI (status commita `szczur-radar - pipeline-trigger` po czasie zakończenia workflow)
- `railway config plan --environment production --detailed-exit-code` (lokalnie) pokazuje tylko `checkSuites → true` na cronach, bez `variable.delete`

**Implementation Note**: Po automatycznej weryfikacji zatrzymaj się na potwierdzenie ręcznych kroków przez człowieka.

---

## Phase 3: Endpoint `GET /api/deploy-safety`

### Overview

Publiczny, minimalny sygnał dla CI: czy można teraz bezpiecznie wdrożyć, bo żaden przebieg nie jest w kolejce ani nie trwa.

### Changes Required:

#### 1. Powód jako enum i zapytanie

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java`, nowy enum w `pipeline/api/`

**Intent**: Odpowiedź podaje, czy przebieg trwa, czy czeka. Zamknięty zbiór powodów jest enumem (`lessons.md`, enumy).

**Contract**:
- Bez nowego zapytania: istniejące `existsByStatusIn(Collection<RunStatus>)` (`PipelineRunRepository.java:23`) z `List.of(RunStatus.RUNNING)` i `List.of(RunStatus.QUEUED)`, ewentualnie opakowane default-metodami obok `existsActive()`.
- Enum powodu z wartościami JSON `pipeline_running` i `pipeline_queued` (`@JsonValue`).
- `running` ma pierwszeństwo, bo `running` i `queued` mogą współistnieć (retry + range).

#### 2. Kontroler

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/DeploySafetyController.java`

**Intent**: Trasa obok `FreshnessController`: zredukowany, niewymagający sesji widok stanu przebiegów.

**Contract**:
- `GET /api/deploy-safety` →
  - 200 `{"safe":true,"reason":null}`;
  - 200 `{"safe":false,"reason":"pipeline_running"|"pipeline_queued"}`;
  - 503 przy niedostępnej bazie, z tym samym kształtem co `/api/ready` (`{"status":"unavailable"}`).
- Odpowiedź jako rekord. Brak ID, dat i liczników.
- Javadoc wyjaśnia, że odpowiedź służy bramce CI i ujawnia tylko „trwa/nie trwa”.

#### 3. Bezpieczeństwo i lista tras publicznych

**File**: `config/SecurityConfig.java`, `src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java`

**Intent**: Trasa publiczna zgodnie z regułą deny-by-default (`REPOSITORY_GUIDELINES.md`, Coding Style).

**Contract**:
- `"/api/deploy-safety"` w matcherze GET permitAll (`SecurityConfig.java:102`).
- Wpis `"GET /api/deploy-safety"` w `PUBLIC_ROUTES` z uzasadnieniem „CI deploy gate; reveals only whether a pipeline run is queued or running”.

#### 4. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/DeploySafetyIT.java`

**Intent**: Pokrycie każdej odpowiedzi.

**Contract**:
- Wzorzec `FreshnessIT`: `@Transactional`, najpierw zakończ istniejące `queued`/`running`, nigdy dwa `running`.
- Przypadki: brak aktywnych (safe); tylko `queued`; `running`; `running` + `queued` (reason `pipeline_running`); tylko terminalne (`interrupted`, `failed`) — safe; dostęp bez sesji 200.
- Wariant bez bazy → 503 (wzorzec `HealthIT` `@Nested WithoutDatabase`).

#### 5. Dokumentacja pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: Nowa trasa w tabeli wejść `api/` i nowy test na liście testów.

**Contract**: Tabela „Entries (`api/`)” i lista testów.

### Success Criteria:

#### Automated Verification:

- `cd szczur-radar-api && ./mvnw -B verify` przechodzi (w tym `DeploySafetyIT` i `AuthorizationCompletenessIT`)
- Job `backend` zielony w CI

#### Manual Verification:

- W PR env `curl https://szczur-radar-web-szczur-radar-pr-<N>.up.railway.app/api/deploy-safety` zwraca `{"safe":true,"reason":null}`
- W PR env podczas przebiegu uruchomionego przez admina ta sama trasa zwraca `safe:false` z powodem `pipeline_running`

**Implementation Note**: Po automatycznej weryfikacji zatrzymaj się na potwierdzenie ręcznych kroków przez człowieka.

---

## Phase 4: Strażnik i kontrola po apply w `railway-config`

### Overview

Przed apply (i tym samym przed deployem kodu) strażnik czeka na `safe=true`. Po apply job czeka na deploy restartowanych usług i na `/api/ready`. Domyka C6 dla apply i automatyzuje zasadę „nie wdrażaj przy RUNNING”.

### Changes Required:

#### 1. Strażnik

**File**: `tools/deploy-gate/wait-safe.sh`

**Intent**: Wstrzymać apply i — przez konkluzję workflow — deploy kodu, dopóki przebieg trwa albo czeka. Nie blokować wdrożenia, gdy API nie może odpowiedzieć, bo wtedy żaden przebieg nie może się wykonywać, a to może być deploy naprawy.

**Contract**:
- Wywołanie: `wait-safe.sh <web-base-url>`.
- Zmienne: `DEPLOY_GUARD` (`off` → pomiń z `::warning::`), `DEPLOY_GUARD_TIMEOUT` (domyślnie `90m`), `DEPLOY_GUARD_INTERVAL` (`60s`), `DEPLOY_GUARD_WAKE_TIMEOUT` (`3m`), `DEPLOY_GUARD_DEADLINE` (bezwzględny termin w sekundach epoki; workflow wylicza go jako `created_at` runu z `gh api repos/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID` + 120 min − `DEPLOY_VERIFY_TIMEOUT` − 5 min zapasu). Format czasów jak w `tools/pipeline-trigger/trigger.sh`.
- Strażnik czeka do min(`DEPLOY_GUARD_TIMEOUT`, `DEPLOY_GUARD_DEADLINE`). Termin już minięty na starcie albo minięty w trakcie → exit 1 z `::error::` „okno Wait for CI (2 h) się kończy — deploy kodu tego commita nie nastąpi; nowy push albo `railway redeploy`”.
- Odpowiedzi:
  - 200 `safe:true` → exit 0;
  - 200 `safe:false` → log z powodem, ponowienie co `INTERVAL`, po `TIMEOUT` albo terminie exit 1 z `::error::` wskazującym runbook S-14 (zombie: restart API, potem nowy push albo `railway redeploy`, bo „Re-run failed jobs” zwykle wypada już po oknie 2 h; wyłącznik `DEPLOY_GUARD=off`);
  - 401 albo 404 → exit 0 z `::warning::` „API bez endpointu (starsza wersja)” (stare API odpowiada 401, bo trasa spoza `PUBLIC_ROUTES` wymaga sesji);
  - błąd połączenia/5xx → ponawianie do `WAKE_TIMEOUT` (budzenie uśpionego API), potem exit 0 z `::warning::` „API niedostępne — żaden przebieg nie może trwać”;
  - nieczytelna odpowiedź 200 → exit 1.
- Zła wartość zmiennej → exit 2.

#### 2. Kontrola po apply

**File**: `tools/deploy-gate/verify-apply.sh`

**Intent**: Wykryć w minutach to, co 2026-10-12 wyszło po 9 minutach ręcznie: usługa zrestartowana przez apply nie wstała albo API nie widzi bazy.

**Contract**:
- Wywołanie w dwóch krokach (bez porównywania zegarów runnera i Railway):
  - przed apply: `verify-apply.sh snapshot <restarts.txt> > deployments-before.json` zapisuje ID najnowszego deployu każdej usługi z `restarts.txt`;
  - po apply: `verify-apply.sh check <restarts.txt> <deployments-before.json> <web-base-url>`.
- Środowisko z `RAILWAY_PROJECT_ID`/`RAILWAY_ENVIRONMENT_ID`, nigdy `railway link`.
- Dla każdej usługi z `restarts.txt` odpytuj `railway deployment list --json --limit 1 -s <service> -e <env>` i czekaj na deploy o ID innym niż w `deployments-before.json`:
  - to samo ID co przed apply → czekaj; brak nowego deployu do limitu → exit 1 z `::error::` „apply nie wywołał deployu <usługa>”;
  - `SUCCESS` → ok;
  - `FAILED`/`CRASHED`/`SKIPPED` → exit 1;
  - `REMOVED`/`REMOVING` → nowszy deploy go zastąpił, odpytaj ponownie najnowszy;
  - `WAITING`/`QUEUED` → „przekazane do deploy-verify” (Wait for CI czeka na ten workflow), ok z adnotacją;
  - `BUILDING`/`DEPLOYING`/`INITIALIZING` → czekaj.
- Łączny limit `DEPLOY_VERIFY_TIMEOUT` (domyślnie `15m`).
- Potem `/api/ready` 200 z ponowieniami do `DEPLOY_GUARD_WAKE_TIMEOUT`, inaczej exit 1.
- Mapowanie adresu z planu na nazwę usługi ustalić na prawdziwym planie z Phase 1 i zapisać w README.
- Pusty `restarts.txt` → tylko `/api/ready`.

#### 3. Testy strażnika i kontroli

**File**: `tools/deploy-gate/test.sh`, `tools/deploy-gate/fixtures/`

**Intent**: Każda gałąź obu skryptów jest sprawdzona bez Railway.

**Contract**:
- Atrapa API: mały serwer HTTP w Pythonie (wzorzec `tools/pipeline-trigger/test.sh`) ze skryptowanymi odpowiedziami per przypadek.
- Atrapa `railway` w `PATH` zwraca skryptowane `deployment list --json`.
- Przypadki strażnika: safe; busy → safe; busy do limitu (exit 1); busy do terminu krótszego niż limit (exit 1); termin minięty na starcie (exit 1); 401; 404; odmowa połączenia → 200; stała niedostępność (exit 0 z ostrzeżeniem); `DEPLOY_GUARD=off`; zła wartość czasu.
- Przypadki kontroli: SUCCESS; FAILED; SKIPPED; WAITING (przekazane); DEPLOYING → SUCCESS; REMOVED → nowszy SUCCESS; brak nowego deployu (to samo ID do limitu, exit 1); limit; `/api/ready` 503.
- Przypadki `web-url.sh`: `production`, `szczur-radar-pr-<N>`, nieznana nazwa (exit 2).
- Krótkie limity w testach.

#### 4. Podłączenie w workflow

**File**: `.github/workflows/ci.yml`

**Intent**: Kolejność w `railway-config`: strażnik → plan → gate → apply → kontrola po apply. Strażnik stoi przed planem, bo `apply --plan` odmawia, gdy etag środowiska zmienił się od planu (`src/iac/saved_plan.rs:245-256` w CLI v5.67.1). Przypięcie musi więc obejmować sekundy, a nie do 90 min czekania.

**Contract**:
- Strażnik działa zawsze (także przy zerowej liczbie zmian), bo chroni też deploy kodu. Krok „Plan and gate” z Phase 1 przesuwa się za strażnika.
- Adres weba z `tools/deploy-gate/web-url.sh "$TARGET_ENV"` (jedno mapowanie dla obu workflow).
- `DEPLOY_GUARD: ${{ vars.DEPLOY_GUARD }}`.
- Krok przed strażnikiem wylicza `DEPLOY_GUARD_DEADLINE` z `created_at` runu (`gh api`, `GH_TOKEN: ${{ github.token }}`; job dostaje `permissions: actions: read`).
- `timeout-minutes` joba ≥ suma limitów strażnika i kontroli z zapasem (np. 115).
- `verify-apply.sh snapshot` tuż przed apply, `verify-apply.sh check` tylko gdy apply się wykonał.

### Success Criteria:

#### Automated Verification:

- `tools/deploy-gate/test.sh` przechodzi ze wszystkimi przypadkami strażnika i kontroli
- `railway-config` w PR env zielony; w logu kolejno: strażnik (`safe`), plan i gate, apply, kontrola po apply

#### Manual Verification:

- W PR env push w trakcie przebiegu uruchomionego przez admina: strażnik loguje `pipeline_running`, czeka, po zakończeniu przebiegu przepuszcza; deploy kodu następuje po przebiegu
- Pierwszy push po dodaniu strażnika (PR env bez endpointu) przechodzi z ostrzeżeniem 401 (stare API wymaga sesji)
- Zmiana wywołująca restart w PR env (np. tymczasowa zmiana `limitOverride` crona) jest ogłoszona w podsumowaniu, a kontrola po apply kończy się sukcesem albo „przekazane”; zmiana cofnięta

**Implementation Note**: Po automatycznej weryfikacji zatrzymaj się na potwierdzenie ręcznych kroków przez człowieka.

---

## Phase 5: Workflow `deploy-verify` po deployu kodu

### Overview

Osobny workflow na zdarzenie `deployment_status` od Railway sprawdza zdrowie po deployu kodu i alarmuje przez issue. Nigdy nie kończy się na czerwono, żeby nie zablokować przez Wait for CI redeployu tego samego commita.

### Changes Required:

#### 1. Skrypt kontroli i alarmu

**File**: `tools/deploy-gate/verify-deploy.sh`

**Intent**: Po zakończonym deployu środowiska sprawdzić `/api/ready` i `/healthz`. Przy porażce deployu albo sondy zostawić ślad, który dotrze do operatora.

**Contract**:
- Wejście (zmienne): `DEPLOY_STATE` (`success|failure|error`), `DEPLOY_ENVIRONMENT` (np. `szczur-radar / production`), `DEPLOY_SHA`, `GH_TOKEN`, `GITHUB_REPOSITORY`. `target_url`/`environment_url` statusu Railway wskazują panel Railway, nie web, więc skrypt ich nie używa.
- Adres weba: nazwa środowiska po `szczur-radar / ` → `web-url.sh`. Nieznana nazwa → log i exit 0.
- Railway publikuje jeden GitHub Deployment na środowisko i commit, z kilkoma statusami bez nazwy usługi; po `success` może przyjść `failure`. Jeden deploy daje więc kilka runów, a alarm deduplikuje otwarte issue (poniżej).
- `success` → sondy z ponowieniami (budzenie); obie 200 → exit 0.
- `failure`/`error` albo sonda nieudana → issue:
  - szukaj otwartego issue z etykietą `deploy-health` i nazwą środowiska w tytule;
  - jest → komentarz;
  - nie ma → nowe issue „Niezdrowe wdrożenie: <środowisko>” z SHA, stanem, wynikami sond i linkiem do runu.
- Exit 0 także po alarmie. Exit ≠ 0 tylko przy błędzie samego `gh` (nie da się zaalarmować).
- Utworzenie etykiety `deploy-health`, jeśli jej nie ma.
- Brak automatycznego zamykania issue: zamyka człowiek po naprawie.

#### 2. Workflow

**File**: `.github/workflows/deploy-verify.yml`

**Intent**: Wyzwalacz i uprawnienia dla skryptu.

**Contract**:
- `on: deployment_status`.
- Job `if:` stan ∈ {`success`, `failure`, `error`} i środowisko zaczyna się od `szczur-radar /`. Inne zdarzenia → job `skipped` (nie blokuje Wait for CI, nie zużywa minut).
- `permissions: { issues: write, contents: read }`.
- Checkout na SHA deployu, wywołanie `tools/deploy-gate/verify-deploy.sh`.
- Komentarz w pliku: dlaczego job nie kończy się na czerwono (Wait for CI patrzy na każdy workflow przy commicie) i dlaczego to osobny plik (deploy kodu startuje po zakończeniu `ci.yml`).

#### 3. Testy

**File**: `tools/deploy-gate/test.sh`

**Intent**: Gałęzie `verify-deploy.sh` bez GitHuba i Railway.

**Contract**:
- Atrapa API jak w Phase 4 i atrapa `gh` w `PATH`, która zapisuje wywołania do pliku.
- Przypadki: `success` + sondy ok (brak `gh`); `success` + `/api/ready` 503 (nowe issue); `failure` (nowe issue); istniejące otwarte issue (komentarz zamiast nowego); nieznane środowisko (exit 0, brak `gh`).

### Success Criteria:

#### Automated Verification:

- `tools/deploy-gate/test.sh` przechodzi z przypadkami `verify-deploy.sh`
- Push na PR uruchamia `deploy-verify` po deployu PR env, a run kończy się na zielono

#### Manual Verification:

- Run `deploy-verify` w PR env używa pliku workflow z gałęzi PR (SHA deployu) i loguje sondy 200
- Zdarzenia `in_progress` dają job `skipped`, a Wait for CI kolejnego pusha nie czeka na nie dłużej
- Wymuszona porażka sondy w PR env (np. tymczasowo zły adres w kopii skryptu na gałęzi) tworzy issue z etykietą `deploy-health`; issue zamknięte, zmiana cofnięta

**Implementation Note**: Po automatycznej weryfikacji zatrzymaj się na potwierdzenie ręcznych kroków przez człowieka.

---

## Phase 6: Dokumentacja, runbook S-14 i weryfikacja produkcji

### Overview

Dokumentacja opisuje nową bramkę, a runbook — co robić, gdy strażnik czeka albo pada. Po merge'u (robi go człowiek) produkcja przechodzi pełną ścieżkę.

### Changes Required:

#### 1. Runbook i opis bramki

**File**: `context/deployment/deploy-plan.md`

**Intent**: Nowa sekcja S-14 plus aktualizacja istniejących opisów bramki.

**Contract**:
- Sekcja S-14:
  - kolejność jobów;
  - reguły bramki;
  - strażnik (limity, 401/404/niedostępne API, `DEPLOY_GUARD=off` jako zmienna repo);
  - zombie (restart API, potem nowy push albo `railway redeploy`; „Re-run failed jobs” tylko przed terminem okna 2 h, bo termin liczy się od utworzenia runu);
  - porażka strażnika po terminie: deploy kodu tego commita nie nastąpi, Railway oznaczy go `inactive` bez alarmu;
  - kontrola po apply;
  - `deploy-verify` i issue `deploy-health`;
  - kolejka `concurrency` (anulowany oczekujący run);
  - zmienne ignorowane.
- Aktualizacja `:347` (crony mają `checkSuites`), `:942` (bramka w `tools/deploy-gate/`, wyjątek `checkSuites`, `variables.ignore` zamiast samego `preserve()`), `:142-145` (część ręcznej listy jest zautomatyzowana).

#### 2. Wytyczne repozytorium

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Sekcje Deployment i Project Structure opisują stan po S-14.

**Contract**:
- `tools/deploy-gate/` na liście narzędzi.
- Lista jobów CI z `deploy-gate-test` i `needs:` na `railway-config`.
- Workflow `deploy-verify`.
- Endpoint `/api/deploy-safety`.
- Zasada „nigdy nie merguj przy RUNNING” uzupełniona o strażnika (wstrzymuje apply i deploy do 90 min), bez jej usuwania (wyścig sprawdź–wdroż, noc).
- Zmienne strojenia przez `variables.ignore`.

#### 3. Runbook weryfikacji produkcji (wykonywany po merge'u przez człowieka)

**File**: `context/deployment/deploy-plan.md` (sekcja S-14); wynik później jako komentarz w issue #73

**Intent**: Potwierdzić na produkcji to, co PR env sprawdził. Kroki po merge'u nie trafiają do Progress (`lessons.md`, „Implementacja, przegląd i archiwizacja na jednej gałęzi”), więc zmiana daje się przejrzeć i zarchiwizować przed merge'em.

**Contract**: Runbook w sekcji S-14 z checklistą do odhaczenia po merge'u:
- `railway-config` startuje po testach, strażnik produkcji zwraca `safe` (albo 401 przy pierwszym merge'u), apply stosuje `checkSuites → true` na cronach, kontrola po apply zielona;
- deploy kodu produkcji przechodzi, `deploy-verify` loguje `/api/ready` i `/healthz` 200, brak issue `deploy-health`;
- `curl https://szczur-radar-web-production.up.railway.app/api/deploy-safety` zwraca `{"safe":true,"reason":null}`;
- żywa konfiguracja obu cronów na produkcji ma Wait for CI włączone; `railway config plan --environment production --detailed-exit-code` kończy się 0;
- wynik jako komentarz w #73 i dopisek w S-14; zamknięcie #73 dopiero po tej weryfikacji, bez kolejnego PR.

### Success Criteria:

#### Automated Verification:

- Pełne CI na PR zielone (wszystkie joby, w tym `deploy-gate-test` i `railway-config`)
- `grep -rn "deploy-gate\|deploy-safety\|deploy-verify" REPOSITORY_GUIDELINES.md context/deployment/deploy-plan.md` pokazuje opisane elementy

#### Manual Verification:

- Sekcja S-14 w `deploy-plan.md` zawiera runbook weryfikacji produkcji po merge'u (checklista z §3)

**Implementation Note**: Merge na `main` robi wyłącznie człowiek (`lessons.md`, „Tylko człowiek merguje do main”). Agent kończy na zielonym CI i weryfikacji w PR env; weryfikację produkcji człowiek wykonuje według runbooka.

---

## Testing Strategy

### Unit Tests:

- `tools/deploy-gate/test.sh`:
  - `gate.sh` na fixture'ach (każda reguła pozytywnie i negatywnie, plus fail closed na nieznany format);
  - `wait-safe.sh`, `verify-apply.sh`, `verify-deploy.sh` na atrapach API, `railway` i `gh`.
- Gałęzie czasowe z krótkimi limitami.

### Integration Tests:

- `DeploySafetyIT`: każdy stan przebiegów i wariant bez bazy.
- `AuthorizationCompletenessIT`: trasa w `PUBLIC_ROUTES` odpowiada bez sesji.
- `./mvnw -B verify` w jobie `backend`.

### Manual Testing Steps:

1. PR env: plan z CLI 5.67.1 → fixture, sprawdzenie `field`/`deployEffect`/`variablePolicy`.
2. PR env: przebieg admina + push → strażnik czeka i przepuszcza po przebiegu.
3. PR env: ręczna zmienna strojenia → brak `variable.delete`.
4. PR env: crony deployują się po CI.
5. PR env: `deploy-verify` zielony; wymuszona porażka tworzy issue.
6. Produkcja po merge'u (runbook S-14, poza Progress): cała ścieżka, plan = 0, wynik w #73.

## Performance Considerations

- Minuty Actions (repo prywatne): strażnik kosztuje +1 min za push, a czekanie (≤ 90 min) tylko przy kolizji z przebiegiem. `deploy-verify` kosztuje +1 min za zakończony deploy środowiska; pozostałe zdarzenia dają job `skipped`.
- Odpytywanie `/api/deploy-safety` utrzymuje API obudzone w czasie czekania. Koszt Railway jest pomijalny przy rzadkich kolizjach.
- `existsByStatusIn` dla `running` trafia w częściowy indeks `uq_pipeline_runs_one_running` (V3:39); `queued` nie ma indeksu, ale tabela przebiegów jest mała (kilka wierszy dziennie), więc skan jest tani.

## Migration Notes

- **Brak migracji bazy.**
- **Kolejność wdrożenia:** pierwszy merge przechodzi strażnika przez 401 (stare API), a apply ustawia `checkSuites` na cronach.
- **Zmienne strojenia:** zmienne ustawione ręcznie przed S-14 przestają dawać `variable.delete` dopiero z polityką `variables.ignore`. Przed merge'em sprawdź plan produkcji (Phase 2).
- **Wycofanie:** revert PR-a przywraca poprzedni `ci.yml` i `railway.ts`. Revert nie wycofa jednak dwóch rzeczy:
  - `checkSuites: true` na cronach (bramka sprzed S-14 odrzuciłaby zmianę `true → null`); wyłączenie Wait for CI na cronach w panelu przywraca stan sprzed S-14;
  - endpoint `/api/deploy-safety`, który zniknie z revertem, a stara bramka go nie używa.
- **Awaryjnie bez revertu:** `DEPLOY_GUARD=off` (zmienna repo) wyłącza strażnika.

## References

- Research: `context/changes/safe-deploy-gate/research.md`
- Audyt C4–C7: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:93-96`
- Roadmapa S-14: `context/foundation/roadmap.md:223-234`
- Bramka dziś: `.github/workflows/ci.yml:340-377`
- Crony: `.railway/railway.ts:218-275`
- Predykat zajętości: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java:19-23`
- Wzorzec narzędzia z testem: `tools/pipeline-trigger/test.sh`
- Wzorce testów: `szczur-radar-api/src/test/java/pl/szczurradar/api/health/HealthIT.java`, `.../pipeline/api/FreshnessIT.java`
- Lekcje: `context/foundation/lessons.md` (Railway IaC w CI; stałe diffy; pole nieznane SDK; tylko człowiek merguje)
- Railway Wait for CI: https://docs.railway.com/deployments/github-autodeploys#wait-for-ci
- Polityka zmiennych w CLI: https://github.com/railwayapp/cli/pull/1256

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Bramka jako testowane narzędzie i kolejność jobów

#### Automated

- [x] 1.1 `tools/deploy-gate/test.sh` przechodzi lokalnie (każda reguła z przypadkiem pozytywnym i negatywnym)
- [ ] 1.2 Job `deploy-gate-test` zielony w CI na PR
- [ ] 1.3 `railway-config` na PR startuje dopiero po zakończeniu wszystkich jobów testowych (widać w grafie runu) i jest zielony
- [x] 1.4 `grep -n "needs:" .github/workflows/ci.yml` pokazuje `needs:` na `railway-config`

#### Manual

- [ ] 1.5 W PR env plan z CLI 5.67.1 zawiera `field` i `deployEffect`; fixture z prawdziwego planu dodany do `fixtures/`
- [ ] 1.6 Podsumowanie joba `railway-config` w PR env pokazuje liczbę zmian i sekcję restartów
- [ ] 1.7 `railway config plan --environment production --detailed-exit-code` z CLI 5.67.1 (lokalnie, tylko odczyt) pokazuje tylko oczekiwane różnice — zmiana wersji CLI nie wprowadza nowego diffu na produkcji

### Phase 2: Crony z Wait for CI i ignorowane zmienne strojenia

#### Automated

- [ ] 2.1 `railway-config` w PR env zielony; bramka przepuszcza zmianę `checkSuites → true` na obu cronach i stosuje ją
- [ ] 2.2 Plan `--json` w PR env zawiera `variablePolicy` z wzorcami z `railway.ts`
- [ ] 2.3 `tools/deploy-gate/test.sh` dalej przechodzi (fixture `checkSuites` odpowiada prawdziwej zmianie z PR env)

#### Manual

- [ ] 2.4 W PR env `TRIGGER_HEARTBEAT_INTERVAL=1m` ustawione ręcznie (`--skip-deploys`) nie daje `variable.delete` w kolejnym planie; po sprawdzeniu zmienna usunięta
- [ ] 2.5 Żywa konfiguracja obu cronów w PR env pokazuje Wait for CI włączone (MCP `get-service-config` albo panel), nie tylko pusty plan
- [ ] 2.6 Kolejny push na PR: cron deployuje się dopiero po zakończeniu CI (status commita `szczur-radar - pipeline-trigger` po czasie zakończenia workflow)
- [ ] 2.7 `railway config plan --environment production --detailed-exit-code` (lokalnie) pokazuje tylko `checkSuites → true` na cronach, bez `variable.delete`

### Phase 3: Endpoint `GET /api/deploy-safety`

#### Automated

- [ ] 3.1 `cd szczur-radar-api && ./mvnw -B verify` przechodzi (w tym `DeploySafetyIT` i `AuthorizationCompletenessIT`)
- [ ] 3.2 Job `backend` zielony w CI

#### Manual

- [ ] 3.3 W PR env `curl https://szczur-radar-web-szczur-radar-pr-<N>.up.railway.app/api/deploy-safety` zwraca `{"safe":true,"reason":null}`
- [ ] 3.4 W PR env podczas przebiegu uruchomionego przez admina ta sama trasa zwraca `safe:false` z powodem `pipeline_running`

### Phase 4: Strażnik i kontrola po apply w `railway-config`

#### Automated

- [ ] 4.1 `tools/deploy-gate/test.sh` przechodzi ze wszystkimi przypadkami strażnika i kontroli
- [ ] 4.2 `railway-config` w PR env zielony; w logu kolejno: strażnik (`safe`), plan i gate, apply, kontrola po apply

#### Manual

- [ ] 4.3 W PR env push w trakcie przebiegu uruchomionego przez admina: strażnik loguje `pipeline_running`, czeka, po zakończeniu przebiegu przepuszcza; deploy kodu następuje po przebiegu
- [ ] 4.4 Pierwszy push po dodaniu strażnika (PR env bez endpointu) przechodzi z ostrzeżeniem 401 (stare API wymaga sesji)
- [ ] 4.5 Zmiana wywołująca restart w PR env (np. tymczasowa zmiana `limitOverride` crona) jest ogłoszona w podsumowaniu, a kontrola po apply kończy się sukcesem albo „przekazane”; zmiana cofnięta

### Phase 5: Workflow `deploy-verify` po deployu kodu

#### Automated

- [ ] 5.1 `tools/deploy-gate/test.sh` przechodzi z przypadkami `verify-deploy.sh`
- [ ] 5.2 Push na PR uruchamia `deploy-verify` po deployu PR env, a run kończy się na zielono

#### Manual

- [ ] 5.3 Run `deploy-verify` w PR env używa pliku workflow z gałęzi PR (SHA deployu) i loguje sondy 200
- [ ] 5.4 Zdarzenia `in_progress` dają job `skipped`, a Wait for CI kolejnego pusha nie czeka na nie dłużej
- [ ] 5.5 Wymuszona porażka sondy w PR env (np. tymczasowo zły adres w kopii skryptu na gałęzi) tworzy issue z etykietą `deploy-health`; issue zamknięte, zmiana cofnięta

### Phase 6: Dokumentacja, runbook S-14 i weryfikacja produkcji

#### Automated

- [ ] 6.1 Pełne CI na PR zielone (wszystkie joby, w tym `deploy-gate-test` i `railway-config`)
- [ ] 6.2 `grep -rn "deploy-gate\|deploy-safety\|deploy-verify" REPOSITORY_GUIDELINES.md context/deployment/deploy-plan.md` pokazuje opisane elementy

#### Manual

- [ ] 6.3 Sekcja S-14 w `deploy-plan.md` zawiera runbook weryfikacji produkcji po merge'u (checklista z §3)
