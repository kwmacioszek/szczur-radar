# deploy-gate

Bramka wdrożeń Szczur Radar (S-14, `safe-deploy-gate`). Job CI `railway-config` (`.github/workflows/ci.yml`) stosuje `.railway/railway.ts` bez nadzoru: w PR env przy każdym pushu na PR, na produkcji przy każdym pushu na `main`. Przed `railway config apply` bramka sprawdza plan i decyduje, czy wolno go zastosować. Reguły czytają `kind` i `severity` zmiany oraz jej `details` i `summary` w stałych formatach CLI, a zasób, który apply zrestartuje, odnajdują w `desiredGraph` planu. Czego bramka nie zna, to odrzuca.

## Pliki

| Plik | Co robi |
| --- | --- |
| `gate.sh` | klasyfikuje plan (niżej): kod wyjścia, podsumowanie joba, lista restartów |
| `gate.jq` | reguły bramki; `gate.sh` uruchamia je na planie |
| `fixtures/*.json` | plany w formacie `railway config plan --json`, po jednym na przypadek testu; `pr-env-*.json` to prawdziwe plany z PR env |
| `test.sh` | lokalny test reguł na fixture'ach, bez Railway i bez Dockera |

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

- **stdout**: podsumowanie w markdownie dla `$GITHUB_STEP_SUMMARY`: liczba zmian, tabela zmian (opis, rodzaj, `details`, `severity`, restartowany zasób, decyzja bramki z powodem) i linia `Restarts:` z nazwami zasobów, które apply wdroży ponownie.
- **stderr**: log joba. Każda odmowa daje linię `::error::` (GitHub pokazuje ją jako adnotację), a pod nią pełne odrzucone zmiany z planu.
- **`GATE_RESTARTS_FILE`** (opcjonalna): plik z nazwami zasobów, które apply wdroży ponownie (`desiredGraph.resources[].name`, np. `db-backup`, `postgres`), jedna na linię, bez powtórzeń, w kolejności planu. Bramka czyści go na starcie i zapisuje tylko dla planu dozwolonego, więc odrzucony albo nieczytelny plan zostawia go pustym. To wejście dla kontroli po apply (`verify-apply.sh`, Phase 4 planu S-14): nazwa zasobu to nazwa usługi dla `railway deployment list -s <usługa>`.

### Reguły

W tej kolejności:

1. **Środowisko.** Plan musi mieć `ok == true`, `currentEnvironment.environmentName` równe środowisku docelowemu i `currentEnvironment.projectId` równe ID projektu. Inaczej odmowa całego planu z jego `diagnostics` w logu. Pozostałe reguły już się nie liczą.
2. **Destrukcja.** Odmowa każdej zmiany z `severity == "destructive"`. Dziś to m.in. `resource.delete` i `variable.delete`, którą CLI zawsze oznacza jako destrukcyjną. Zmiana z `severity` innym niż `safe` i `destructive` (także bez `severity`) też jest odrzucana.
3. **Lista dozwolonych rodzajów** (`allowed_kinds` w `gate.jq`). Przechodzą tylko `resource.create`, `resource.update`, `variable.set` i `domain.create`. Każdy inny `kind` jest odrzucany niezależnie od `severity`: `resource.delete`, `variable.delete`, `domain.delete` albo rodzaj, którego bramka nie zna.
4. **Źródło usługi.** `resource.update` dotyka źródła (repo, gałąź, katalog, Wait for CI), gdy któryś `details[]` zaczyna się od `source.`, `summary` pasuje do `(^|[ ,])source([.]|$| )` albo CLI poda `field == "source"`. Taka zmiana przechodzi tylko wtedy, gdy ma co najmniej jeden detal i **każdy** detal pasuje dokładnie do `^source\.checkSuites \((null|false) → true\)$` (`wait_for_ci_detail` w `gate.jq`), czyli włącza Wait for CI. Odrzucane są m.in. `source.branch`, `checkSuites true → null`, `checkSuites` razem z `branch` i `summary` o źródle bez detali. PR env śledzi gałąź PR, produkcja `main`.
5. **Nieznany format.** Odmowa zmiany bez `kind` i `resource.update`, której `details` nie jest tablicą napisów (np. jej brak). Brak `field` nie jest odmową. CLI nie podaje `details` dla zmiany nazwy i `networking`, więc takie zmiany bramka też odrzuca.
6. **Restarty.** Zmiana z `deployEffect == "deploy"` albo bez `deployEffect` (w CLI 5.67.1 każda) trafia do listy restartów. Bramka ich nie odrzuca, tylko je ogłasza (także na Postgresie). `deployEffect == "none"` nie trafia na listę. Restartowany zasób to dokładnie jeden zasób z `desiredGraph.resources`:
   - po `address` zmiany, gdy CLI go poda (adres zasobu to jego `address` albo `<typ>.<nazwa>`);
   - inaczej po `summary` w formacie jej rodzaju (tabela wyżej): nazwa zaraz po `Create <typ> `, `Update `, `Set variable ` / `Update variable ` (przed `.<KLUCZ>`), po ` to ` w `Rename …`, po `Resize volume `; dla `domain.create` (format nieznany) nazwa zasobu jako całe słowo w `summary`.

   Zmiana z restartem, dla której nie da się ustalić dokładnie jednego zasobu z `desiredGraph`, jest odrzucana, bo kontrola po apply nie wiedziałaby, na co czekać. Bez `deployEffect` także `resource.create` kubełka i `domain.create` są ogłaszane jako restart.

Plan bez listy zmian (`changeSet.changes`) przy `ok == true` jest nieczytelny (kod 2).

### Kody wyjścia

| Kod | Znaczenie |
| --- | --- |
| `0` | plan dozwolony (także pusty) |
| `1` | plan odrzucony: zła odpowiedź planu albo środowisko, albo co najmniej jedna niedozwolona zmiana (linie `::error::`) |
| `2` | złe wywołanie (liczba argumentów, puste środowisko albo ID projektu, brak `jq`) albo nieczytelny plan (brak pliku, nie JSON, brak `changeSet.changes`) |

## W CI

Job `railway-config` startuje dopiero po wszystkich jobach testowych (`needs:`, w tym `deploy-gate-test`). Krok „Plan and gate”:

1. `railway config plan --json --out plan.pin > plan.json` (CLI `@railway/cli@5.67.1`). Wartości zmiennych CLI redaguje (bez `--show-values`). `--out` zapisuje przypięty plan tylko przy `ok == true`.
2. `.diff` planu do logu, potem `gate.sh` (podsumowanie do `$GITHUB_STEP_SUMMARY`, `restarts.txt`). Gdy CLI zakończyło się błędem, krok pada także po przepuszczeniu przez bramkę.
3. `plan.json` jako artefakt joba `railway-plan-<środowisko>` (7 dni), także przy odmowie. `plan.pin` nie jest zachowywany.
4. Apply tylko przy liczbie zmian > 0: `railway config apply --plan plan.pin --yes`. Stosuje dokładnie sprawdzony plan i odmawia, jeśli środowisko zmieniło się od planu. Nigdy `--confirm-destructive`.

`--json` i `--out` działają razem w jednym wywołaniu w CLI 5.67.1: `plan.json` jest czystym JSON-em, a `plan.pin` powstaje obok. Potwierdzone w PR env tej zmiany (`szczur-radar-pr-81`, runy 39124165646 i 39124555511). Fallback z dwoma wywołaniami planu nie jest potrzebny.

## Test lokalny

```bash
tools/deploy-gate/test.sh
```

Wymaga tylko `bash` i `jq`, działa z dowolnego katalogu. Uruchamia `gate.sh` na każdym fixture'ze i sprawdza kod wyjścia, podsumowanie, linie `::error::` i plik restartów. Każda reguła ma przypadek dozwolony i odrzucony:
- pusty plan dla produkcji i prawdziwy pusty plan `szczur-radar-pr-81` (0) oraz zła nazwa środowiska, inny projekt i `ok:false` (1);
- `variable.delete` i `resource.delete` (destrukcyjne, 1), `variable.set` (`Update variable` i `Set variable`, 0, ogłoszone restarty usług);
- `variable.delete` i nieznany rodzaj (`domain.delete`) z `severity: "safe"`, odrzucone przez listę dozwolonych, nie przez `severity` (1); `resource.create` i `domain.create` (0); plan z jedną dozwoloną i jedną odrzuconą zmianą (1, błąd tylko dla odrzuconej);
- prawdziwy plan `szczur-radar-pr-81` z `checkSuites null → true` na obu cronach (0, restart `db-backup` i `pipeline-trigger`), `checkSuites null → true` i `false → true` (0); `source.branch`, `checkSuites true → null`, `checkSuites` razem z `branch` i `summary` o `source.checkSuites` bez detali (1);
- `resource.update` bez `details` i zmiana bez `kind` (1); `limitOverride` na Postgresie (0, restart `postgres`);
- `address` podany przez CLI wskazuje zasób, `deployEffect: "none"` nie jest restartem, `deployEffect: "deploy"` jest (0); restart zasobu spoza `desiredGraph` po `summary` albo po `address` (1);
- złe wywołanie, brak pliku, plan, który nie jest JSON-em, i plan bez `changeSet.changes` (2).

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
