# deploy-gate

Bramka wdrożeń Szczur Radar (S-14, `safe-deploy-gate`). Job CI `railway-config` (`.github/workflows/ci.yml`) stosuje `.railway/railway.ts` bez nadzoru: w PR env przy każdym pushu na PR, na produkcji przy każdym pushu na `main`. Przed `railway config apply` bramka sprawdza plan i decyduje, czy wolno go zastosować. Reguły czytają pola strukturalne planu (`kind`, `field`, `severity`, `deployEffect`, `before`, `after`), nigdy jego tekst. Czego bramka nie zna, to odrzuca.

## Pliki

| Plik | Co robi |
| --- | --- |
| `gate.sh` | klasyfikuje plan (niżej): kod wyjścia, podsumowanie joba, lista restartów |
| `gate.jq` | reguły bramki; `gate.sh` uruchamia je na planie |
| `fixtures/*.json` | plany w formacie `railway config plan --json`, po jednym na przypadek testu |
| `test.sh` | lokalny test reguł na fixture'ach, bez Railway i bez Dockera |

## `gate.sh`

```bash
GATE_RESTARTS_FILE=restarts.txt tools/deploy-gate/gate.sh plan.json "$TARGET_ENV" "$RAILWAY_PROJECT_ID" >> "$GITHUB_STEP_SUMMARY"
```

- **stdout**: podsumowanie w markdownie dla `$GITHUB_STEP_SUMMARY`: liczba zmian, tabela zmian (opis, rodzaj i pole, adres, `severity`, `deployEffect`, decyzja bramki z powodem) i linia `Restarts:` z adresami zasobów, które apply wdroży ponownie.
- **stderr**: log joba. Każda odmowa daje linię `::error::` (GitHub pokazuje ją jako adnotację), a pod nią pełne odrzucone zmiany z planu.
- **`GATE_RESTARTS_FILE`** (opcjonalna): plik z adresami zasobów, które apply wdroży ponownie, jeden na linię, bez powtórzeń, w kolejności planu. Pusty, gdy nic się nie restartuje. Bramka czyści go na starcie, więc odrzucony albo nieczytelny plan nie zostawia starej listy. To wejście dla kontroli po apply (`verify-apply.sh`, Phase 4 planu S-14).

Adres to `address` zmiany w planie, czyli `<typ>.<nazwa>` z CLI (np. `service.szczur-radar-api`, `database.postgres`). Mapowanie adresu na nazwę usługi dla `railway deployment list -s <usługa>` ustala Phase 4 na prawdziwym planie z PR env i zapisuje je tutaj.

### Reguły

W tej kolejności:

1. **Środowisko.** Plan musi mieć `ok == true`, `currentEnvironment.environmentName` równe środowisku docelowemu i `currentEnvironment.projectId` równe ID projektu. Inaczej odmowa całego planu z jego `diagnostics` w logu. Pozostałe reguły już się nie liczą.
2. **Destrukcja.** Odmowa każdej zmiany z `severity == "destructive"`. Dziś to m.in. `resource.delete` i `variable.delete`, którą CLI zawsze oznacza jako destrukcyjną. Zmiana z `severity` innym niż `safe` i `destructive` (także bez `severity`) też jest odrzucana.
3. **Lista dozwolonych rodzajów.** Przechodzą tylko `resource.create`, `resource.update`, `variable.set` i `domain.create`. Każdy inny `kind` jest odrzucany niezależnie od `severity`: `resource.delete`, `variable.delete`, `domain.delete` albo rodzaj, którego bramka nie zna.
4. **Źródło usługi.** Odmowa `resource.update` z `field == "source"` (repo, gałąź, katalog), w każdym środowisku. PR env śledzi gałąź PR, produkcja `main`. Jedyny wyjątek to włączenie Wait for CI: `before` i `after` różnią się wyłącznie kluczem `checkSuites` (brak klucza = `null`), a `after.checkSuites == true`. `true → null` i `checkSuites` razem z `branch` są odrzucane.
5. **Nieznany format.** Odmowa zmiany bez `kind` i `resource.update` bez `field`. Odrzucana jest też zmiana, która wdraża zasób ponownie, a nie podaje `address`, bo bez adresu kontrola po apply nie wie, na co czekać.
6. **Restarty.** Zmiana z `deployEffect == "deploy"` albo bez `deployEffect` trafia do listy restartów. Bramka ich nie odrzuca, tylko je ogłasza (także na Postgresie). `deployEffect == "none"` (w CLI: pola `config`, `groupId`, `cdn`, sam `tracing.autoInstrumentation`, tworzenie zasobu innego niż usługa i baza) nie trafia na listę.

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

`--json` i `--out` działają razem w jednym wywołaniu (źródło CLI: `--out` zapisuje plan przed wypisaniem JSON-a, a przy `--json` niczego nie dopisuje na stdout). Potwierdzić w PR env tej zmiany. Jeśli tak nie jest, zostaje fallback: `railway config plan --json > plan.json` i osobno `railway config plan --out plan.pin`, a apply przypiętego pliku. Wynik zapisać tutaj.

## Test lokalny

```bash
tools/deploy-gate/test.sh
```

Wymaga tylko `bash` i `jq`, działa z dowolnego katalogu. Uruchamia `gate.sh` na każdym fixture'ze i sprawdza kod wyjścia, podsumowanie, linie `::error::` i plik restartów. Każda reguła ma przypadek dozwolony i odrzucony:
- pusty plan dla produkcji (0) oraz zła nazwa środowiska, inny projekt i `ok:false` (1);
- `variable.delete` i `resource.delete` (destrukcyjne, 1), `variable.set` (0, ogłoszony restart);
- `variable.delete` i nieznany rodzaj (`domain.delete`) z `severity: "safe"`, odrzucone przez listę dozwolonych, nie przez `severity` (1); `resource.create` i `domain.create` (0); plan z jedną dozwoloną i jedną odrzuconą zmianą (1, błąd tylko dla odrzuconej);
- zmiana `source.branch`, `checkSuites true → null` i `checkSuites` razem z `branch` (1); `checkSuites null → true` i `false → true` (0, oba crony ogłoszone);
- `resource.update` bez `field` i zmiana bez `kind` (1); `limitOverride` na Postgresie (0, ogłoszony restart);
- zmiana bez `deployEffect` ogłoszona jako restart, `deployEffect: "none"` nie;
- złe wywołanie, brak pliku, plan, który nie jest JSON-em, i plan bez `changeSet.changes` (2).

CI uruchamia ten test przy każdym PR i pushu na `main` (job `deploy gate test`).

## Odświeżenie fixture'a z prawdziwego planu

Fixture'y odwzorowują format `railway config plan --json` (pola zmian ze źródła CLI, `src/iac/change_set.rs`). Prawdziwy plan jest wzorcem, gdy CLI zmieni format:

1. Pobierz artefakt `railway-plan-<środowisko>` z joba `railway-config` (GitHub: run → Artifacts) albo zaplanuj lokalnie, tylko do odczytu:
   ```bash
   railway config plan --environment szczur-radar-pr-<N> --json > plan.json
   ```
   Bez `--show-values`: CLI redaguje wartości zmiennych.
2. Sprawdź, że w pliku nie ma żadnej wartości sekretu (`grep` po fragmentach kluczy). Zostaw `ok`, `command`, `file`, `currentEnvironment`, `changeSet` (z `changes`), `diff` i `diagnostics`. Usuń `currentGraph` i `desiredGraph`, bo bramka ich nie czyta.
3. Zapisz go w `fixtures/<przypadek>.json` i dopisz przypadek w `test.sh` (`check_gate <przypadek> <kod>` plus oczekiwane podsumowanie i restarty). Fixture'y testów używają projektu `decf00e4-f5a4-43df-b338-6698be87c5a1` i środowiska `production`. Plan z PR env sprawdź przez `run_gate` z jego nazwą środowiska.
4. Jeśli prawdziwy plan ma inne pola niż fixture'y (np. brak `field` albo `deployEffect`), popraw najpierw `gate.jq`, a potem fixture'y.
