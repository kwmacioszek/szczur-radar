# deploy-gate

Bramka wdrożeń Szczur Radar (S-14, `safe-deploy-gate`). Kontroli po deployu kodu tu nie ma: od zmiany `deploy-verification-hardening` robi ją zewnętrzny monitor [`szczur-radar-monitor`](https://github.com/kwmacioszek/szczur-radar-monitor) (sekcja `deploy-verification-hardening` w `context/deployment/deploy-plan.md`). Job CI `railway-config` (`.github/workflows/ci.yml`) stosuje `.railway/railway.ts` bez nadzoru: w PR env przy każdym pushu na PR, na produkcji przy każdym pushu na `main`. Przed `railway config apply` bramka sprawdza plan i decyduje, czy wolno go zastosować. Reguły czytają `kind` i `severity` zmiany oraz jej `details` i `summary` w stałych formatach CLI, a zasób, który apply może wdrożyć ponownie, odnajdują w `desiredGraph` planu. Czego bramka nie zna, to odrzuca.

## Pliki

| Plik | Co robi |
| --- | --- |
| `gate.sh` | klasyfikuje plan (niżej): kod wyjścia, podsumowanie joba, lista restartów |
| `gate.jq` | reguły bramki; `gate.sh` uruchamia je na planie |
| `wait-safe.sh` | strażnik: przed planem i apply czeka, aż żaden przebieg pipeline'u nie czeka ani nie trwa (`/api/deploy-safety`) |
| `verify-apply.sh` | kontrola po apply: deploymenty, które apply uruchomił (po `meta.patchId`), i `/api/ready` 200 |
| `web-url.sh` | adres weba środowiska (jedno mapowanie nazwy środowiska na origin) |
| `lib.sh` | wspólne helpery skryptów (format czasów, adres weba, zapytanie HTTP); dołączany przez `.`, nie uruchamiany |
| `fixtures/*.json` | plany w formacie `railway config plan --json`, po jednym na przypadek testu; `pr-env-*.json` to prawdziwe plany z PR env |
| `test.sh` | lokalny test bramki na fixture'ach oraz strażnika i kontroli na atrapach API i `railway`, bez Railway, GitHuba i Dockera |

## Format planu (CLI 5.67.1)

`railway config plan --json` w CLI 5.67.1 podaje w każdej zmianie tylko cztery pola:

```json
{"summary": "Update db-backup source.checkSuites", "severity": "safe", "kind": "resource.update",
 "details": ["source.checkSuites (null → true)"]}
```

Silnik CLI (`src/iac/change_set.rs`) liczy też `address`, `field`, `before`, `after` i `deployEffect`, a Railway przepisuje listę zmian podglądem po stronie serwera (`environmentPreviewChangeSet`, `src/iac/engine.rs`). Wyjście `--json` przechodzi jednak przez strukturę `Change` runnera (`src/commands/config/runner.rs`), która zostawia tylko `summary`, `severity`, `kind` i `details`. Pola strukturalne bramka uwzględnia, gdy CLI je poda (`field`, `deployEffect`, `address`), ale ich nie wymaga.

Formaty `summary` i `details` w źródle CLI v5.67.1:

| Rodzaj | `summary` | `details` |
| --- | --- | --- |
| `resource.create` | `Create <typ> <nazwa>`, `Create <typ> <nazwa> (branch: <gałąź> (PR branch\|repo default))` | brak albo `["branch: …"]` |
| `resource.update` | `Update <nazwa> <ścieżka>[, <ścieżka>…][ and N more]`, `Update <nazwa> <pole>` (np. `networking`), `Rename <typ> <stara> to <nazwa>`, `Resize volume <nazwa> from …`, `Move database <nazwa> to <region>` (destrukcyjne) | `["<ścieżka> (<przed> → <po>)", …]`, brak dla `name` i `networking` |
| `variable.set` | `Set variable <nazwa>.<KLUCZ>` (nowa), `Update variable <nazwa>.<KLUCZ>` | `["<nazwa>.<KLUCZ> («hidden» → «hidden»)"]`, nowa: `unset → «hidden»` |
| `variable.delete` | `Delete variable <nazwa>.<KLUCZ>` | brak |
| `resource.delete` | `Delete <typ> <nazwa>` | brak |
| `domain.create` | CLI 5.67.1 sam go nie tworzy (domeny usługi są poza diffem), może go dać tylko podgląd serwera; format nieznany | — |

Wartość brakującego klucza w `details` to `null`. Wartości zmiennych CLI redaguje jako `«hidden»` (bez `--show-values`).

## `gate.sh`

```bash
GATE_RESTARTS_FILE=restarts.txt tools/deploy-gate/gate.sh plan.json "$TARGET_ENV" "$RAILWAY_PROJECT_ID" >> "$GITHUB_STEP_SUMMARY"
```

- **stdout**: podsumowanie w markdownie dla `$GITHUB_STEP_SUMMARY`: liczba zmian, tabela zmian (opis, rodzaj, `details`, `severity`, restartowany zasób, decyzja bramki z powodem) i linia `Restarts:` z nazwami zasobów, które apply może wdrożyć ponownie.
- **stderr**: log joba. Każda odmowa daje linię `::error::` (GitHub pokazuje ją jako adnotację), a pod nią pełne odrzucone zmiany z planu.
- **`GATE_RESTARTS_FILE`** (opcjonalna): plik z nazwami usług i baz, które apply może wdrożyć ponownie (`desiredGraph.resources[].name` zasobów typu `service` albo `database`, np. `db-backup`, `postgres`), jedna na linię, bez powtórzeń, w kolejności planu. Bramka czyści go na starcie i zapisuje tylko dla planu dozwolonego, więc odrzucony albo nieczytelny plan zostawia go pustym. To wejście dla kontroli po apply (`verify-apply.sh`): nazwa zasobu to nazwa usługi dla `railway deployment list -s <usługa>`. Zasób innego typu (kubełek `bucket`, wolumen) nie ma deployów, więc tabela pokazuje go jako `` `<nazwa>` (<typ>: no deploy) `` i nie trafia do pliku.

### Reguły

W tej kolejności:

1. **Środowisko.** Plan musi mieć `ok == true`, `currentEnvironment.environmentName` równe środowisku docelowemu i `currentEnvironment.projectId` równe ID projektu. Inaczej odmowa całego planu z jego `diagnostics` w logu. Pozostałe reguły już się nie liczą.
2. **Destrukcja.** Odmowa każdej zmiany z `severity == "destructive"`. Dziś to m.in. `resource.delete` i `variable.delete`, którą CLI zawsze oznacza jako destrukcyjną. Zmiana z `severity` innym niż `safe` i `destructive` (także bez `severity`) też jest odrzucana.
3. **Lista dozwolonych rodzajów** (`allowed_kinds` w `gate.jq`). Przechodzą tylko `resource.create`, `resource.update`, `variable.set` i `domain.create`. Każdy inny `kind` jest odrzucany niezależnie od `severity`: `resource.delete`, `variable.delete`, `domain.delete` albo rodzaj, którego bramka nie zna. `domain.create` przechodzi tylko dla `szczur-radar-web` (`public_service` w `gate.jq`). Domena na każdej innej usłudze, także takiej, której bramka nie rozpozna, jest odrzucana, bo `szczur-radar-api` nie może mieć własnej publicznej domeny.
4. **Źródło usługi.** `resource.update` dotyka źródła (repo, gałąź, katalog, Wait for CI), gdy któryś `details[]` dotyczy źródła (`^source([. (]|$)`: pole `source.*` albo cały obiekt `source (… → …)`), `summary` pasuje do `(^|[ ,])source([.]|$| )` albo CLI poda `field == "source"`. Taka zmiana przechodzi tylko wtedy, gdy ma co najmniej jeden detal i **każdy** detal pasuje dokładnie do `^source\.checkSuites \((null|false) → true\)$` (`wait_for_ci_detail` w `gate.jq`), czyli włącza Wait for CI. Odrzucane są m.in. `source.branch`, `checkSuites true → null`, `checkSuites` razem z `branch` i `summary` o źródle bez detali. PR env śledzi gałąź PR, produkcja `main`.
5. **Nieznany format.** Odmowa zmiany bez `kind` i `resource.update`, której `details` nie jest tablicą napisów (np. jej brak). Brak `field` nie jest odmową. CLI nie podaje `details` dla zmiany nazwy i `networking`, więc takie zmiany bramka też odrzuca.
6. **Restarty.** Lista restartów to zasoby, które apply **może** wdrożyć ponownie. Zmiana z `deployEffect == "deploy"` albo bez `deployEffect` (w CLI 5.67.1 każda) dopisuje do niej swój zasób, `deployEffect == "none"` nie. Nie każda taka zmiana deployuje (`source.checkSuites (null → true)` 2026-10-19 nie wywołał żadnego deployu), a plan CLI tego nie mówi, więc kontrola po apply (`verify-apply.sh`, niżej) czeka tylko na deploymenty, które apply naprawdę uruchomił. Bramka restartów nie odrzuca, tylko je ogłasza (także na Postgresie). Zasób zmiany to dokładnie jeden zasób z `desiredGraph.resources`:
   - po `address` zmiany, gdy CLI go poda (adres zasobu to jego `address` albo `<typ>.<nazwa>`);
   - inaczej po `summary` w formacie jej rodzaju (tabela wyżej): nazwa zaraz po `Create <typ> `, `Update `, `Set variable ` / `Update variable ` (przed `.<KLUCZ>`), po ` to ` w `Rename …`, po `Resize volume `; dla `domain.create` (format nieznany) nazwa zasobu jako całe słowo w `summary`.

   Zmiana z możliwym restartem, dla której nie da się ustalić dokładnie jednego zasobu z `desiredGraph`, jest odrzucana, bo kontrola po apply nie wiedziałaby, deploymenty której usługi sprawdzać. Bez `deployEffect` także `domain.create` jest ogłaszane jako restart swojej usługi. `resource.create` kubełka ma zasób, ale bez deployów: tabela go pokazuje, lista restartów (`deployable_types` w `gate.jq`: `service`, `database`) nie.

Plan bez listy zmian (`changeSet.changes`) przy `ok == true` jest nieczytelny (kod 2).

### Kody wyjścia

| Kod | Znaczenie |
| --- | --- |
| `0` | plan dozwolony (także pusty) |
| `1` | plan odrzucony: zła odpowiedź planu albo środowisko, albo co najmniej jedna niedozwolona zmiana (linie `::error::`) |
| `2` | złe wywołanie (liczba argumentów, puste środowisko albo ID projektu, brak `jq`) albo nieczytelny plan (brak pliku, nie JSON, brak `changeSet.changes`) |

## `web-url.sh`

```bash
tools/deploy-gate/web-url.sh "$TARGET_ENV"
```

Wypisuje origin weba środowiska, bez ukośnika na końcu: `production` → `https://szczur-radar-web-production.up.railway.app`, `szczur-radar-pr-<N>` → `https://szczur-radar-web-szczur-radar-pr-<N>.up.railway.app`. Nieznana nazwa albo złe wywołanie: `::error::` i kod 2, nic na stdout. Tylko web ma publiczną domenę i przekazuje `/api/*` do API, więc obie kontrole pytają API przez ten adres.

## `wait-safe.sh` (strażnik)

```bash
DEPLOY_GUARD_DEADLINE=<epoka> tools/deploy-gate/wait-safe.sh "$WEB_URL"
```

Deploy przerywa przebieg pipeline'u, który czeka albo trwa. Strażnik pyta `GET <web>/api/deploy-safety` i wstrzymuje plan, apply oraz — przez konkluzję workflow, na którą czeka Wait for CI — deploy kodu:

| Odpowiedź | Wynik |
| --- | --- |
| 200 `{"safe":true}` | kod 0 |
| 200 `{"safe":false,"reason":…}` | log z powodem (`pipeline_running`, `pipeline_queued`), pytanie co `DEPLOY_GUARD_INTERVAL`; po `DEPLOY_GUARD_TIMEOUT` kod 1 z `::error::` wskazującym runbook S-14 (zombie: restart API, potem nowy push albo `railway redeploy`; wyłącznik `DEPLOY_GUARD=off`) |
| 401, 404 | kod 0 z `::warning::`: API bez trasy (starsza wersja; trasa spoza `PUBLIC_ROUTES` wymaga tam sesji, stąd 401) |
| brak połączenia, 5xx (też 503 `{"status":"unavailable"}`) | ponawianie co 2 s do `DEPLOY_GUARD_WAKE_TIMEOUT` (budzenie uśpionego API), potem kod 0 z `::warning::`: API niedostępne, więc żaden przebieg nie może trwać, a deploy może być naprawą |
| 200 bez czytelnego werdyktu (np. HTML, `safe:false` bez powodu), każdy inny status (3xx, 403…) | kod 1 |

Termin `DEPLOY_GUARD_DEADLINE` (sekundy epoki) obowiązuje niezależnie od limitu: strażnik czeka do min(limit, termin). Termin minięty na starcie (bez zapytania) albo w trakcie daje kod 1 z `::error::` „the Wait for CI window (2 h) is closing … Railway will not deploy the code of this commit. Push again or run `railway redeploy`”. Po 2 h od pusha Railway i tak pomija deploy i oznacza GitHub Deployment jako `inactive`, czego nie widzi żaden workflow; czerwony job zamiast zielonego bez deployu.

| Zmienna | Domyślnie | Znaczenie |
| --- | --- | --- |
| `DEPLOY_GUARD` | puste | `off`: pomiń z `::warning::` (zmienna repo, wyłącznik awaryjny); puste albo `on`: strażnik działa; inna wartość: kod 2 |
| `DEPLOY_GUARD_TIMEOUT` | `90m` | jak długo czekać na zajęty pipeline |
| `DEPLOY_GUARD_INTERVAL` | `60s` | odstęp pytań przy zajętym pipelinie |
| `DEPLOY_GUARD_WAKE_TIMEOUT` | `3m` | jak długo ponawiać brak odpowiedzi (też `/api/ready` w `verify-apply.sh`) |
| `DEPLOY_GUARD_DEADLINE` | brak | bezwzględny termin; workflow: `created_at` runu + 120 min − `DEPLOY_VERIFY_TIMEOUT` − `DEPLOY_GUARD_WAKE_TIMEOUT` − 5 min |

Czasy w formacie `tools/pipeline-trigger`: dodatnia liczba z jednostką `s`, `m`, `h` (sama liczba to sekundy). Zła wartość którejkolwiek zmiennej albo zły adres: kod 2. Log na stderr.

## `verify-apply.sh` (kontrola po apply)

```bash
tools/deploy-gate/verify-apply.sh snapshot restarts.txt > deployments-before.json     # tuż przed apply
tools/deploy-gate/verify-apply.sh check restarts.txt deployments-before.json "$WEB_URL" >> "$GITHUB_STEP_SUMMARY"
```

`restarts.txt` z `gate.sh` to usługi, które apply może wdrożyć ponownie; 2026-10-12 „bezpieczny” apply zrestartował Postgresa, który wisiał 9 min, zanim ktoś spojrzał. Kontrola czeka na deploymenty, które apply uruchomił, i kończy job na czerwono w minutach, gdy któryś się nie udał. Usługa, której apply nie wdrożył, przechodzi z `::notice::` (S-21: 2026-10-19 zmiana `source.checkSuites` nie wywołała deployu, a kontrola wzięła `SKIPPED` równoległego pusha za deployment apply'a; czerwony workflow sprawił, że Wait for CI pominął deploy kodu):

- **`snapshot`** zapisuje ID najnowszego deploymentu każdej usługi, dowolnego pochodzenia (`railway deployment list --json --limit 1`; `{"<usługa>": "<id>" | null}`; `null`: usługa bez deploymentu). To **granica** dla `check`. Gdy Railway po 3 próbach nie poda deploymentów usługi, snapshot kończy się exit 1 z `::error::` i apply się nie wykonuje. Gdyby taka usługa trafiła do snapshotu jako `null`, `check` uznałby za deploymenty po apply'u także te sprzed niego. Dotyczy to też usługi, której jeszcze nie ma: plan tworzący nową usługę (rzadki) stosuje się ręcznie (`railway config apply` lokalnie, po sprawdzeniu planu), a kolejny push ma już tę usługę w snapshocie.
- **`check`** co `DEPLOY_VERIFY_INTERVAL` pyta `railway deployment list --json --limit 50 -s <usługa> -p "$RAILWAY_PROJECT_ID" -e "$RAILWAY_ENVIRONMENT_ID"`. Deploymenty po apply'u to te nad granicą na liście (lista jest od najnowszego, więc bez porównywania zegarów runnera i Railway; granica `null`: cała lista). **Deployment apply'a** to najnowszy z nich, którego `meta.patchId` zaczyna się od `iac-change-set/`. Nie `meta.reason` ani `meta.commitHash`: deployment apply'a ma `reason: "deploy"` i `commitHash` czubka gałęzi w chwili apply'a, tak jak pushowy, więc deployment równoległego pusha (`SKIPPED` przez watch paths, `WAITING` albo `SUCCESS`) wyglądałby jak deployment apply'a. Kształty `meta` na produkcji (research S-21):

| Pochodzenie | `meta` |
| --- | --- |
| apply (`railway config apply`) | `patchId: "iac-change-set/<ID środowiska>/<32 hex>"`, `reason: "deploy"`, `commitHash` (czubek gałęzi), `branch`, `ignoreWatchPatterns: true` (watch paths go nie pomijają); Postgres, który deployuje się tylko łatkami: bez `commitHash` i `ignoreWatchPatterns` |
| push | `reason: "deploy"`, `commitHash`, `branch`; bez `patchId` |
| push poza watch paths (`SKIPPED`) | `commitHash`, `branch`, `skippedReason: "No changes to watched files"`; bez `reason` |

  Status deploymentu apply'a (`DeploymentStatus` Railway):

| Status | Wynik |
| --- | --- |
| `SUCCESS`, `SLEEPING` | wdrożona (`SLEEPING`: wdrożona i uśpiona) |
| `WAITING`, `QUEUED` | „handed over to the monitor szczur-radar-monitor” (`::notice::`): Wait for CI trzyma deploy do końca tego workflow, więc czekanie tu dałoby zakleszczenie; wynik tego deployu ocenia monitor |
| `BUILDING`, `DEPLOYING`, `INITIALIZING` | czekaj |
| `REMOVED`, `REMOVING` | zastąpiony: oceniany jest najnowszy deployment nad nim o statusie innym niż `SKIPPED`, dowolnego pochodzenia (`SKIPPED` niczego nie zastępuje); dopóki go nie ma, czekaj |
| `FAILED`, `CRASHED`, `SKIPPED` | kod 1 (`SKIPPED` z `meta.skippedReason` Railway, gdy jest) |
| `NEEDS_APPROVAL`, każdy inny | kod 1 (potrzebny człowiek albo status nieznany kontroli) |

  **„No deploy”**: brak deploymentu apply'a na liście odczytanej po oknie `DEPLOY_VERIFY_DEPLOY_WINDOW` (domyślnie `2m`) daje wiersz „no deploy (none within <okno> of the apply)” w podsumowaniu, `::notice::` „the apply triggered no deploy of <usługa> within <okno>” w logu i przejście do następnej usługi. Okno liczy się od startu `check` (tuż po apply'u), jedno dla wszystkich usług: apply tworzy deploymenty w ciągu sekund, więc usługa sprawdzana po długim deployu Postgresa dostaje werdykt po jednym odczycie. Werdykt daje tylko udany odczyt po końcu okna: `railway` zawodzący do limitu to kod 1 („cannot tell whether the apply deployed <usługa>”). Granica spoza 50 najnowszych deploymentów: kod 1 „cannot tell the apply's deployment of <usługa>”. Deployment apply'a niezakończony w limicie: kod 1. Łączny limit dla wszystkich usług: `DEPLOY_VERIFY_TIMEOUT` (domyślnie `15m`); okno dłuższe niż limit albo w złym formacie: kod 2 już w `snapshot`, przed apply.
- Potem `GET <web>/api/ready` (API widzi bazę) z ponowieniami co 2 s do `DEPLOY_GUARD_WAKE_TIMEOUT` (`3m`); bez 200 kod 1.
- Pusty `restarts.txt`: tylko `/api/ready`, bez wywołań `railway`.

Środowisko wyłącznie przez `RAILWAY_PROJECT_ID` i `RAILWAY_ENVIRONMENT_ID` (token workspace, nigdy `railway link`). Nazwy w `restarts.txt` to nazwy zasobów z `desiredGraph`, a te są nazwami usług Railway: `railway deployment list -s` szuka usługi po nazwie bez względu na wielkość liter albo po ID (`src/controllers/project.rs` w CLI v5.67.1), także bazy — Postgres (`postgres`, typ `database`) jest w Railway usługą z własnymi deployami. Wyjście `--json` to tablica `[{"id", "status", "createdAt", "meta"}]`, a `meta` CLI przepuszcza z API bez zmian (`src/commands/deployment.rs`). CLI 5.67.1 listy nie sortuje, a jego zapytanie nie ma `orderBy`: kolejność od najnowszego daje API i na niej opierają się migawka i granica. `check`: stdout to podsumowanie w markdownie (usługa, deploy, wynik), stderr log. Kod 2: złe wywołanie, brak pliku, migawka bez usługi z listy, brak `RAILWAY_*_ID` przy niepustej liście, zła nazwa usługi, zły czas (także okno dłuższe niż limit) albo adres.

## Kontrola po deployu kodu: monitor `szczur-radar-monitor`

Wait for CI uruchamia deploy kodu dopiero po zakończeniu `ci.yml`, więc żaden krok `ci.yml` go nie widzi. Sprawdza go zewnętrzny monitor w publicznym repo [`kwmacioszek/szczur-radar-monitor`](https://github.com/kwmacioszek/szczur-radar-monitor): webhook Railway → Cloudflare Worker → `workflow_dispatch` workflow `verify.yml` w repo monitora. Run ocenia jedną wdrożoną usługę: dla `szczur-radar-api` i `szczur-radar-web` stan deployu w Railway oraz `/api/ready`, `/healthz` i `GET /`, dla pozostałych usług (crony, `postgres`) tylko stan ich deployu. Czerwony run otwiera albo komentuje jedno issue `deploy-health` na środowisko i usługę w repo monitora, przypisane do operatora; pierwszy zielony run tej usługi je zamyka. Monitor żyje w innym repo, więc jego czerwony run nie wpływa na Wait for CI w szczur-radar. Opis, runbook i ograniczenia: sekcja `deploy-verification-hardening` w `context/deployment/deploy-plan.md` i README monitora.

## W CI

Job `railway-config` startuje dopiero po wszystkich jobach testowych (`needs:`, w tym `deploy-gate-test`), ma `permissions: contents: read, actions: read` i `timeout-minutes: 115` (strażnik 90 min + kontrola 15 min + `/api/ready` 3 min + plan i apply). Limity są w `env:` joba; ich suma musi się mieścić w 2 h okna Wait for CI. Kolejność:

1. „Web address and deploy window”: `WEB_URL` z `web-url.sh "$TARGET_ENV"` i `DEPLOY_GUARD_DEADLINE` z `created_at` runu (`curl` do `$GITHUB_API_URL/repos/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID` z `GITHUB_TOKEN: github.token`, bez `gh`, którego self-hosted runnery nie mają; wspólne dla wszystkich prób runu) + 120 min − `DEPLOY_VERIFY_TIMEOUT` − `DEPLOY_GUARD_WAKE_TIMEOUT` − 5 min.
2. Strażnik `wait-safe.sh "$WEB_URL"` z `DEPLOY_GUARD: ${{ vars.DEPLOY_GUARD }}`, zawsze, także przy planie bez zmian, bo chroni też deploy kodu. Stoi przed planem: `apply --plan` odmawia, gdy środowisko zmieniło się od planu, więc przypięcie ma trwać sekundy, nie do 90 min czekania. Czekający strażnik trzyma grupę `concurrency`: trzeci push w tym czasie anuluje oczekujący drugi run (trzeci zawiera jego commit).
3. „Plan and gate”: `railway config plan --json --out plan.pin > plan.json` (CLI `@railway/cli@5.67.1`). Wartości zmiennych CLI redaguje (bez `--show-values`). `--out` zapisuje przypięty plan tylko przy `ok == true`. `.diff` planu do logu, potem `gate.sh` (podsumowanie do `$GITHUB_STEP_SUMMARY`, `restarts.txt`). Gdy CLI zakończyło się błędem, krok pada także po przepuszczeniu przez bramkę.
4. `plan.json` jako artefakt joba `railway-plan-<środowisko>` (7 dni), także przy odmowie. `plan.pin` nie jest zachowywany.
5. Tylko przy liczbie zmian > 0: `verify-apply.sh snapshot restarts.txt > deployments-before.json`, potem apply `railway config apply --plan plan.pin --yes` (stosuje dokładnie sprawdzony plan i odmawia, jeśli środowisko zmieniło się od planu; nigdy `--confirm-destructive`).
6. Tylko gdy apply się wykonał: `verify-apply.sh check restarts.txt deployments-before.json "$WEB_URL"` (podsumowanie do `$GITHUB_STEP_SUMMARY`).
7. Tylko gdy apply padł: „API after a failed apply”, czyli `verify-apply.sh check` z pustą listą restartów, który pyta tylko `/api/ready`. Job jest i tak czerwony, ale apply mógł już zrestartować usługi (także Postgresa), więc log mówi, czy API nadal widzi bazę.

`--json` i `--out` działają razem w jednym wywołaniu w CLI 5.67.1: `plan.json` jest czystym JSON-em, a `plan.pin` powstaje obok. Potwierdzone w PR env tej zmiany (`szczur-radar-pr-81`, runy 39124165646 i 39124555511). Fallback z dwoma wywołaniami planu nie jest potrzebny.

## Test lokalny

```bash
tools/deploy-gate/test.sh
```

Wymaga `bash`, `jq`, `curl` i `python3` (bez Dockera, bez Railway, bez GitHuba), działa z dowolnego katalogu, trwa około minuty (limity i okna 1–5 s). Uruchamia `gate.sh` na każdym fixture'ze i sprawdza kod wyjścia, podsumowanie, linie `::error::` i plik restartów. Każda reguła ma przypadek dozwolony i odrzucony:
- pusty plan dla produkcji i prawdziwy pusty plan `szczur-radar-pr-81` (0) oraz zła nazwa środowiska, inny projekt i `ok:false` (1);
- `variable.delete` i `resource.delete` (destrukcyjne, 1), `variable.set` (`Update variable` i `Set variable`, 0, ogłoszone restarty usług);
- `variable.delete` i nieznany rodzaj (`domain.delete`) z `severity: "safe"`, odrzucone przez listę dozwolonych, nie przez `severity` (1); `resource.create` i `domain.create` na webie (0), `domain.create` na API (1); plan z jedną dozwoloną i jedną odrzuconą zmianą (1, błąd tylko dla odrzuconej);
- prawdziwy plan `szczur-radar-pr-81` z `checkSuites null → true` na obu cronach (0, `db-backup` i `pipeline-trigger` na liście restartów jako usługi, które apply może wdrożyć ponownie), `checkSuites null → true` i `false → true` (0); `source.branch`, `checkSuites true → null`, `checkSuites` razem z `branch`, `summary` o `source.checkSuites` bez detali i detal całego obiektu `source` ukryty w `summary` za „and 1 more” (1);
- `resource.update` bez `details` i zmiana bez `kind` (1); `limitOverride` na Postgresie (0, restart `postgres`);
- `address` podany przez CLI wskazuje zasób, `deployEffect: "none"` nie jest restartem, `deployEffect: "deploy"` jest (0); restart zasobu spoza `desiredGraph` po `summary` albo po `address` (1);
- złe wywołanie, brak pliku, plan, który nie jest JSON-em, i plan bez `changeSet.changes` (2);
- `resource.create` kubełka: pokazany w tabeli jako „no deploy”, bez wpisu w pliku restartów.

Dalej, na atrapach:
- `web-url.sh`: `production`, `szczur-radar-pr-81`, nieznana nazwa albo brak argumentu (2);
- `wait-safe.sh` na atrapie API (mały serwer HTTP w Pythonie ze skryptowanymi odpowiedziami): `safe`; `queued` → `running` → `safe`; zajęty do limitu i do terminu krótszego niż limit (1); termin minięty na starcie (1, bez zapytania); 401 i 404 (0 z ostrzeżeniem); odmowa połączenia → 503 → `safe`; stałe 503 i stała odmowa połączenia (0 z ostrzeżeniem); 200 bez werdyktu i 403 (1); `DEPLOY_GUARD=off`; złe wartości (2);
- `verify-apply.sh` na atrapie `railway` w `PATH` (skryptowane `deployment list --json`: deploymenty od najnowszego z `meta` apply'a, pusha albo pusha pominiętego przez watch paths jak na produkcji; sprawdza też `--limit` 1 albo 50 i `-p`/`-e` z ID) i atrapie API: migawka (najnowszy deployment dowolnego pochodzenia, `null`, ponowienie po błędzie CLI; pusta albo nieczytelna odpowiedź CLI z kodem 0 trzy razy: 1, nie `null`); deployment apply'a `SUCCESS` (zapytanie o 50 najnowszych), `SLEEPING`; `FAILED`, `CRASHED`, `SKIPPED`, `NEEDS_APPROVAL`, nieznany status (1, bez `/api/ready`), `SKIPPED` ze `skippedReason` w błędzie; `WAITING` i `QUEUED` (przekazane); brak deploymentu apply'a → `INITIALIZING` → `DEPLOYING` → `SUCCESS`; `REMOVED` → nowszy `SUCCESS`, także pod pushem `SKIPPED` przez watch paths; usługa tworzona przez apply (`null` w migawce); nad granicą tylko pushe (`SKIPPED`, `WAITING`, `SUCCESS`; 0, „no deploy” z `::notice::`, `/api/ready` zapytane, druga usługa po jednym odczycie); push `SKIPPED` nad `WAITING` apply'a (przekazany deployment apply'a); push `SUCCESS` nad `FAILED` apply'a (1); deployment apply'a dopiero w trzecim odczycie w oknie (czekanie); granica spoza listy (1, „cannot tell”); błąd CLI do limitu, także po odczycie w oknie, i pusta odpowiedź CLI z kodem 0 do limitu (1, nigdy „no deploy”); `DEPLOYING` do limitu (1); `/api/ready` 503 (1); pusta lista (tylko `/api/ready`, 503 → 200); złe wywołania, okno dłuższe niż limit albo w złym formacie (2, bez wywołania `railway`);

CI uruchamia ten test przy każdym PR i pushu na `main` (job `deploy gate test`).

## Odświeżenie fixture'a z prawdziwego planu

Prawdziwy plan jest wzorcem, gdy CLI zmieni format:

1. Pobierz artefakt `railway-plan-<środowisko>` z joba `railway-config` (GitHub: run → Artifacts, np. `gh run download <run-id> -n railway-plan-szczur-radar-pr-<N>`) albo zaplanuj lokalnie, tylko do odczytu:
   ```bash
   railway config plan --environment szczur-radar-pr-<N> --json > plan.json
   ```
   Bez `--show-values`: CLI redaguje wartości zmiennych.
2. Przytnij go do tego, co bramka czyta i co pokazuje format:
   ```bash
   jq '{ok, currentEnvironment, changeSet, diff, variablePolicy,
        desiredGraph: {resources: [.desiredGraph.resources[] | {name, type, address}]}}' plan.json > fixtures/<przypadek>.json
   ```
   Bez `currentGraph`, `stagedPatch` i zmiennych z `desiredGraph`. Sprawdź, że w pliku nie ma żadnej wartości sekretu (`grep` po fragmentach kluczy).
3. Dopisz przypadek w `test.sh`: dla planu z PR env `run_gate <przypadek> "$fixtures/<przypadek>.json" <kod> szczur-radar-pr-<N> "$project_id"`, dla planu produkcji `check_gate <przypadek> <kod>`, plus oczekiwane podsumowanie i restarty. Fixture'y syntetyczne używają projektu `decf00e4-f5a4-43df-b338-6698be87c5a1` i środowiska `production`.
4. Jeśli prawdziwy plan ma inne pola albo inne formaty `summary`/`details` niż fixture'y, popraw najpierw `gate.jq` (i tabelę formatów wyżej), a potem fixture'y.
