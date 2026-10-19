<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Self-hosted runnery CI (Proxmox + PC)

- **Plan**: context/changes/self-hosted-pr-runner/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-19
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 6 warnings, 3 observations

Faza 3 przejrzana w zakresie zmian w repo (dokumentacja); jej kroki 3.4–3.6 (przeczytanie runbooka, ten przegląd, archiwizacja) są w toku. Zakres git: `c7db08c..debf01c` + niezacommitowany wpis Progress 3.3. Kryteria automatyczne ponowione 2026-10-19: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 3.1 (SVG odtwarzalny), 3.2 — PASS; PR #83 9/9 checków `pass`.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Nieudany `create` zostawia osierocone wolumeny i może utrwalić zepsuty obraz bazowy

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: tools/ci-runner/pc_runners.py:129-152, 208-225
- **Detail**: `create_vm` tworzy `ci-pc-N.qcow2` przed `cloud-localds`/uploadem seeda/`virt-install`; błąd później zostawia wolumeny bez domeny. `vm_names()` liczy tylko domeny, więc kolejny `create` wybiera to samo N i pada na „volume exists”, a `delete` sierot nie widzi. Podobnie `upload()` bazy: przerwany `vol-upload` zostawia pusty/częściowy `noble-base.qcow2`, a `ensure_base_image()` wraca wcześnie, gdy wolumen istnieje — każda kolejna VM dostaje zepsuty obraz.
- **Fix**: W `create_vm` przy wyjątku usuwać `ci-pc-N.qcow2`, `ci-pc-N-seed.iso` (i domenę, jeśli powstała), potem rzucić dalej; obraz bazowy wgrywać pod tymczasową nazwą i dopiero po sukcesie nadawać `noble-base.qcow2` (albo kasować wolumen przy błędzie).
  - Strength: Błąd w połowie nie blokuje kolejnych uruchomień; zgodne z idempotencją `setup.sh`.
  - Tradeoff: Kilkanaście linii obsługi błędów w narzędziu.
  - Confidence: HIGH — ścieżki błędu widać wprost w kodzie.
  - Blind spot: Nie sprawdzone, czy `virsh vol-clone`/rename w puli dir zachowuje format qcow2 bez ponownego wykrywania.
- **Decision**: FIXED — `remove_vm` sprząta po nieudanym `create`, przerwany upload usuwa wolumen; sprawdzone: `create` + `delete` testowej `ci-pc-5` na PC

### F2 — Token rejestracyjny zostaje w VM i w puli, a README mówi, że jest usuwany

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: tools/ci-runner/pc_runners.py:200-218; tools/ci-runner/README.md:68; tools/ci-runner/setup.sh:76
- **Detail**: runcmd usuwa tylko `/root/ci-runner/token`. Token zostaje w `ci-pc-N-seed.iso` (wolumen w puli, podpięty jako cdrom na całe życie VM), w `/var/lib/cloud/instance/user-data.txt` w VM i chwilowo w `ps` (`config.sh --token`). Joby działają jako `gha` w grupie `docker` (≈ root), więc przez 1 h ważności tokenu job mógłby zarejestrować obcy runner `szczur-ci`. Praktycznie mała eskalacja (joby z tego repo i tak dostają `RAILWAY_API_TOKEN`; forki wyłączone), ale zdanie w README jest nieprawdziwe.
- **Fix A ⭐ Recommended**: Po utworzeniu VM wysunąć i usunąć seed ISO (`virsh change-media … --eject`, `vol-delete`) gdy runner jest online, w runcmd usunąć też `user-data.txt`; poprawić README.
  - Strength: Token faktycznie znika po rejestracji; README staje się prawdą.
  - Tradeoff: `create` musi poczekać na rejestrację albo sprzątanie przenosi się do `status`/osobnego kroku; cloud-init bez seeda przy ponownym starcie nie uruchomi się ponownie (instance-id już zapisany — oczekiwane).
  - Confidence: MED — mechanizm eject/vol-delete standardowy, ale niesprawdzony na tych VM-kach.
  - Blind spot: Zachowanie cloud-init po odłączeniu źródła NoCloud przy reboocie.
- **Fix B**: Tylko poprawić README (token w seed ISO i user-data, ważny 1 h) i zostawić kod.
  - Strength: Zero ryzyka zmian w działającym narzędziu.
  - Tradeoff: Token-artefakt zostaje (wygasły po 1 h, więc bez wartości później).
  - Confidence: HIGH — tylko dokumentacja.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — `create` czeka na runner online i wysuwa/usuwa seed ISO, runcmd kasuje kopie user-data i wyłącza cloud-init; dodatkowo token szedł przez `sudo` do `auth.log`/journala — `setup.sh` podaje go teraz przez `ACTIONS_RUNNER_INPUT_TOKEN` i `runuser`; sprawdzone: 0 trafień tokenu w VM (pliki i journal); seedy `ci-pc-1…4` usunięte, ich kopie cloud-init wyczyszczone; README poprawione

### F3 — Tarball runnera może zostać niezweryfikowany, a przerwane pobranie jest używane ponownie

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/ci-runner/setup.sh:54-61
- **Detail**: Pusty `digest` w danych wydania po cichu pomija SHA-256 (plan: „z weryfikacją SHA-256 z wydania”); `curl -o` przy przerwaniu zostawia częściowy plik, który kolejny przebieg pomija jako istniejący.
- **Fix**: Kończyć błędem przy braku `digest`; pobierać do `$tarball.tmp`, weryfikować, potem `mv`.
- **Decision**: FIXED — brak `digest` kończy skrypt błędem, pobieranie do `.tmp` i `mv` po weryfikacji

### F4 — `apt-get` przy pierwszym starcie VM może przegrać z blokadą dpkg

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/ci-runner/setup.sh:20-21
- **Detail**: Na świeżym obrazie cloud `unattended-upgrades`/`apt-daily` mogą trzymać blokadę; `set -e` przerywa skrypt, token i tak jest kasowany, runner się nie rejestruje (widać tylko „not registered” w `status`). 5 z 5 VM-ek przeszło, ale to wyścig.
- **Fix**: `apt-get -o DPkg::Lock::Timeout=600 …` w `setup.sh` (i ten sam limit dla apt w `installdependencies.sh`, np. przez plik w `/etc/apt/apt.conf.d/`).
- **Decision**: FIXED — `/etc/apt/apt.conf.d/90-ci-runner-lock` z `DPkg::Lock::Timeout "600"` (obejmuje też `installdependencies.sh`)

### F5 — `setup.sh` przyjmuje `RUNNER_COUNT>1`, choć `ci.yml` tego nie wytrzyma

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/ci-runner/setup.sh:9, 64; .github/workflows/ci.yml:94, 172
- **Detail**: Stałe nazwy `web`/`api`, tagi obrazów i porty 8080/8000/5432 na jednym Dockerze: drugi runner w tej samej VM sprawi, że `docker rm -f web|api` zabije kontener równoległego joba. README ostrzega (l. 42), skrypt nie.
- **Fix**: `setup.sh` odmawia `RUNNER_COUNT` > 1 z komunikatem o unikalnych portach/nazwach (Parked: drugi runner w Proxmoxie).
- **Decision**: FIXED — `setup.sh` odmawia `RUNNER_COUNT` innego niż 1

### F6 — Nieaktualne zdania w planie, briefie, README i opisie PR #83

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: plan.md:90, 266, 269; plan-brief.md:8, 53; tools/ci-runner/README.md:17, 28; tools/render_roadmap_svg.py:194; PR #83 (tytuł i opis)
- **Detail**: plan.md:90 nadal „docker-prune … starsze niż 72 h” (zmienione na próg 70%); plan.md:266/269 szacunki 9–10 min / ok. 3 min zamiast zmierzonych ok. 7 min / ok. 2 min (pomiar z 2.7 nie jest zapisany w folderze zmiany); plan-brief.md:8 i :53 nie wspominają puli PC; README:17 komentarz „wszystko na Proxmoxa” (dziś pula Proxmox + PC); README:28 „min. 8 GB RAM” przy VM w Proxmoxie z 7 GB; README nie nazywa braku izolacji sieci znanym ryzykiem (jest tylko w deploy-plan); tag SVG `roadmap` zamiast `#30`. PR #83: tytuł i opis z fazy 1 („faza 1 z 2”), bez puli PC, `pc_runners.py`, poprawki `CreateAdminCommandIT`, dokumentacji.
- **Fix**: Poprawić wymienione zdania (w planie tylko jako adnotacja przy fazie, bez ruszania Progress), dopisać do planu pomiary czasów runów, zaktualizować tytuł i opis PR #83 przez `gh pr edit`.
- **Decision**: FIXED — plan (adnotacja prune, zmierzone czasy), brief (pula PC), README (komentarz, RAM, znane ryzyko izolacji), tag SVG `#30`, tytuł i opis PR #83

### F7 — Trwałe runnery dzielą stan między jobami PR i `main`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml (railway-config: `npm install --global @railway/cli`); context/deployment/deploy-plan.md (runbook, krok 5)
- **Detail**: Toolcache (globalny `railway` w katalogu setup-node), cache obrazów Dockera i `_work` przeżywają joby; job PR mógłby podmienić binarkę, którą później uruchomi job z `push` na `main` z `RAILWAY_API_TOKEN`. Dziś bez eskalacji (te same sekrety w PR-ach z repo, forki wyłączone, brak Dependabota), ale model zaufania jest nieopisany.
- **Fix**: Dopisać w deploy-plan (krok 5, znane ryzyko): przy zmianie modelu zaufania (więcej osób, Dependabot, osobny token produkcji) trzymać `CI_RUNNER_MAIN=ubuntu-latest` albo runnery `--ephemeral`.
- **Decision**: FIXED — model zaufania trwałych runnerów dopisany do znanego ryzyka w `deploy-plan.md` (runbook, krok 5)

### F8 — Drobne utwardzenia `pc_runners.py`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/ci-runner/pc_runners.py:141-147, 173-174, 183-202, 319-334
- **Detail**: Hash obrazu ok. 600 MB liczony z całego pliku w pamięci (`read_bytes`); `urlopen`/`urlretrieve` bez timeoutu; klucze SSH i token wstawiane do YAML cloud-init bez cytowania (komentarz klucza z `: ` psuje listę; klucze `sk-…` są pomijane); `delete --yes` niszczy VM z zajętym runnerem tylko z ostrzeżeniem. Wejścia kontroluje operator — nie wektor ataku.
- **Fix**: `hashlib.file_digest`, `timeout=` w pobieraniu, wartości do YAML przez `json.dumps`, akceptacja kluczy `sk-`, `delete` pomija zajęte runnery bez `--force`.
- **Decision**: FIXED — strumieniowe pobieranie z timeoutem i `hashlib.file_digest`, wartości w cloud-init jako JSON (sprawdzone `yaml.safe_load` i `cloud-init schema`: Valid), klucze `sk-…`, `delete` pomija zajęte runnery bez `--force`

### F9 — Konwencje: `.editorconfig` bez `[*.py]`, README bez tabeli plików, `setup.sh` bez notki o `set -x`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: .editorconfig; tools/ci-runner/README.md; tools/ci-runner/setup.sh:1-4
- **Detail**: `[*]` ustawia `indent_size = 2`, a `pc_runners.py` i `render_roadmap_svg.py` mają 4 spacje; `tools/db-backup/README.md` i `tools/pipeline-trigger/README.md` mają sekcję „## Pliki”, nowy README nie; skrypty w `tools/` mają nagłówkową uwagę „Never `set -x`” przy sekretach — `setup.sh` (token) nie.
- **Fix**: Dodać `[*.py] indent_size = 4`, tabelę „## Pliki” w README, notkę o `set -x` w `setup.sh`.
- **Decision**: FIXED — `[*.py] indent_size = 4` w `.editorconfig`, tabela „## Pliki” w README, notka „Never `set -x`” w `setup.sh`

## Triage summary (2026-10-19)

- Fixed: F1, F2 (Fix A + wyciek tokenu przez `sudo`), F3, F4, F5, F6, F7, F8, F9 (9)
- Skipped / accepted / rules: none
