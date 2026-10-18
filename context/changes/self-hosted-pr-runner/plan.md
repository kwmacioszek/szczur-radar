# Self-hosted runner CI na VM w Proxmoxie — plan implementacji

## Overview

Darmowe minuty GitHub Actions prywatnego repo skończyły się 2026-10-18 i żaden job CI nie startuje, więc Railway nie wdraża ani PR env, ani produkcji. Dodajemy przełącznik runnera sterowany zmiennymi repozytorium we wszystkich jobach `.github/workflows/ci.yml` oraz self-hosted runner GitHub Actions na VM z Ubuntu w domowym Proxmoxie. Jedna flaga (`CI_RUNNER`) przenosi całe CI na VM, a jej usunięcie przywraca runnery GitHuba.

## Current State Analysis

- Wszystkie 7 jobów `ci.yml` ma `runs-on: ubuntu-latest` (`research.md`, Code References). W 30 dniach zużyły ok. 1815 rozliczanych minut, ~83% na PR-ach (`research.md`, sekcja 1).
- Limit wyczerpany: run `39160654420` — każdy job `failure` bez kroków, „The job was not started because … your spending limit needs to be increased” (`research.md`, Follow-up). Railway: failure → deploy pominięty od razu; nieskończony workflow → pominięty po 2 h (`research.md`, sekcja 6).
- VM `github-runner` (`192.168.10.123`, użytkownik `krzysiek`, klucz `~/.ssh/szczur_ci_runner_ed25519` na Macu): Ubuntu 24.04.5, x86_64, 4 vCPU, 7 GiB, `/` 31 GB; brak Dockera, `git`, `jq`; sudo z hasłem (`research.md`, Follow-up).
- Na Linuksie z Dockerem joby działają bez przeróbek (`services:`, `--network host`, Testcontainers); jedyny problem trwałego runnera to stałe nazwy kontenerów `web`/`api` bez usuwania (`ci.yml` web-container-smoke, api-container-smoke) i porty hosta 8080/8000/5432 (ważne dopiero przy 2 runnerach).
- **Szkic w working tree (bez commita, powstał przed planem):** przełącznik `runs-on` w 7 jobach z komentarzem nagłówkowym, sprzątanie `web`/`api` przed i po smoke testach, `tools/ci-runner/setup.sh` i `tools/ci-runner/README.md`. Faza 1 go przegląda i domyka zamiast pisać od zera.
- PR #81 (S-14 `safe-deploy-gate`, OPEN) zmienia `ci.yml`: nowy job `deploy-gate-test` i `needs:` na wszystkie joby w `railway-config` (`research.md`, Historical Context).
- Issue #30 opisuje tę zmianę (self-hosted runner, przełącznik zmienną, offline fallback, sekrety).

## Desired End State

- `gh variable set CI_RUNNER --body szczur-ci` → każdy job każdego runu (PR i `push` na `main`) wykonuje się na runnerze `github-runner-1` na VM; `gh variable delete CI_RUNNER` → joby żądają `ubuntu-latest`. `CI_RUNNER_PR` / `CI_RUNNER_MAIN` nadpisują `CI_RUNNER` dla jednego zdarzenia.
- PR tej zmiany przechodzi całe CI na VM (7 jobów zielone), `railway-config` stosuje IaC do PR env, a Railway wdraża PR env (Wait for CI).
- VM jest odtwarzalna skryptem z repo (`tools/ci-runner/setup.sh`), a runbook przełącznika i awarii VM jest w `tools/ci-runner/README.md` i `context/deployment/deploy-plan.md`.
- Weryfikacja: API jobów (`actions/runs/<id>/jobs`) pokazuje `runner_name` = `github-runner-1` i `labels` = `["szczur-ci"]`; po kolejnym pushu brak kolizji nazw kontenerów.

### Key Discoveries:

- `runs-on` akceptuje konteksty `github` i `vars`, nie `env`/`secrets`; pusta zmienna jest fałszywa (`research.md`, sekcja 2).
- GitHub bierze `runs-on` z `ci.yml` gałęzi, na której idzie run — inne otwarte PR-y potrzebują `main` z tą zmianą (`research.md`, sekcja 2 i Follow-up).
- `tools/db-backup/test.sh` i `tools/pipeline-trigger/test.sh` już są bezpieczne na trwałym runnerze: zasoby Dockera per PID i sprzątanie (`research.md`, sekcja 4).
- `RAILWAY_API_TOKEN` to token workspace sięgający wszystkich projektów (`context/deployment/deploy-plan.md:869`) — po tej zmianie trafia na VM.
- Workflowy z forków są w repo wyłączone (`research.md`, Follow-up).

## What We're NOT Doing

- Drugi runner i unikalne porty/nazwy kontenerów w smoke testach — osobna, późniejsza zmiana (wpis w roadmapie `## Parked`).
- Izolacja sieci VM od LAN (VLAN/firewall Proxmoxa) — świadomie odłożona decyzją użytkownika; ryzyko zapisane w runbooku.
- Automatyczny fallback na runnery GitHuba, gdy VM jest offline (wymaga PAT z prawami admina; przełączenie zostaje ręczne).
- Runner w kontenerze / ephemeral, runner na Macu (odrzucone w `research.md`, sekcje 3–5).
- Podbicie wersji akcji (`checkout@v4`, `setup-java@v4` — ostrzeżenia o Node 20) i łączenie krótkich jobów dla oszczędności minut.
- Zmiana przepływu deployu produkcji (merge robi człowiek, autodeploy z Wait for CI bez zmian).

## Implementation Approach

Najpierw odblokowanie: przełącznik w `ci.yml` (domyślnie zachowanie bez zmian), runner na VM i flaga `CI_RUNNER` ustawiona, zanim gałąź trafi na GitHuba — PR tej zmiany jest wtedy pierwszym testem runnera. Potem dokumentacja i domknięcie na tej samej gałęzi; człowiek merguje raz, a pozostałe PR-y odblokowują się po wciągnięciu `main`.

## Critical Implementation Details

- **Kolejność w fazie 1:** runner musi być online, a `CI_RUNNER=szczur-ci` ustawione **przed** pierwszym pushem gałęzi. Push wcześniej daje run na `ubuntu-latest`, który przy wyczerpanym limicie od razu pada (nic się nie psuje, ale trzeba pushnąć ponownie).
- **Wyrażenie przełącznika:** `${{ (github.event_name == 'pull_request' && vars.CI_RUNNER_PR) || (github.event_name == 'push' && vars.CI_RUNNER_MAIN) || vars.CI_RUNNER || 'ubuntu-latest' }}` — nawiasy są konieczne: bez nich pusty `CI_RUNNER_PR` w łańcuchu `&& ||` daje zły wynik. To samo wyrażenie w każdym `runs-on` (także w `deploy-gate-test` z S-14, gdy wejdzie).
- **Tymczasowe sudo:** instalacja wymaga roota; użytkownik dodaje `/etc/sudoers.d/90-claude-setup` (NOPASSWD dla `krzysiek`), agent usuwa go na końcu fazy 1. Runner działa jako `gha` bez sudo.
- **S-14 (PR #81):** kto merguje drugi, rozwiązuje konflikt w `ci.yml` i dodaje przełącznik do `deploy-gate-test`.

## Phase 1: Przełącznik i runner na VM

### Overview

CI działa na VM w Proxmoxie, a jedna flaga przełącza je z powrotem na GitHuba. PR tej zmiany przechodzi na VM i wdraża PR env.

### Changes Required:

#### 1. Przełącznik runnera w CI

**File**: `.github/workflows/ci.yml`

**Intent**: Każdy job wybiera runner ze zmiennych repozytorium, domyślnie `ubuntu-latest`, żeby jedna flaga przenosiła całe CI. Komentarz nagłówkowy opisuje przełącznik i ryzyko kolejki przy runnerze offline; komentarz w `backend` o Dockerze przestaje zakładać `ubuntu-latest`.

**Contract**: Wszystkie 7 `runs-on` = wyrażenie z „Critical Implementation Details”; kolejność pierwszeństwa `CI_RUNNER_PR` (tylko `pull_request`) / `CI_RUNNER_MAIN` (tylko `push`) → `CI_RUNNER` → `ubuntu-latest`. Bez innych zmian w `on:`, `if:` ani krokach poza sprzątaniem niżej (workflow dalej bez path-filtra).

#### 2. Sprzątanie kontenerów o stałych nazwach

**File**: `.github/workflows/ci.yml` (joby `web-container-smoke`, `api-container-smoke`)

**Intent**: Na trwałym runnerze kontener z poprzedniego runu (także po przerwanym jobie) blokowałby nazwę `web`/`api`. Usuwamy go przed startem i w kroku „Container logs” (`if: always()`).

**Contract**: `docker rm -f <name>` przed `docker run` i po `docker logs`, błędy ignorowane; nazwy i porty bez zmian (zachowanie na `ubuntu-latest` identyczne).

#### 3. Skrypt instalacji runnera i runbook

**File**: `tools/ci-runner/setup.sh`, `tools/ci-runner/README.md`

**Intent**: VM odtwarzalna jednym poleceniem; runbook mówi, jak przełączać, instalować i obsługiwać runner.

**Contract**: `setup.sh` uruchamiany jako root z `RUNNER_TOKEN` (opcjonalnie `RUNNER_COUNT`=1, `RUNNER_LABELS`=`szczur-ci`, `RUNNER_USER`=`gha`, `REPO_URL`); idempotentny; instaluje `docker.io`, `docker-buildx`, `git`, `jq`, `curl`; rotacja logów Dockera w `/etc/docker/daemon.json`; `/etc/cron.daily/docker-prune` (starsze niż 72 h); użytkownik `gha` w grupie `docker`, bez sudo; najnowszy runner z weryfikacją SHA-256 z wydania; rejestracja `<hostname>-N` jako usługa systemd. README: tabela zmiennych przełącznika, polecenia `gh variable`, ostrzeżenie o kolejce i 2 h Railway, instalacja (token rejestracyjny przez `gh api`), obsługa (status, logi, wyrejestrowanie), ograniczenie 1 runnera i brak izolacji sieci jako znane ryzyko.

#### 4. Gałąź, issue i PR

**File**: (GitHub) issue #30, gałąź `feature/self-hosted-pr-runner`

**Intent**: Zgodnie z workflow repo: gałąź powiązana z issue #30 przez Development, PR z `Refs #30`, komentarz w issue z change id, gałęzią i linkiem do PR.

**Contract**: `gh issue develop 30 --name feature/self-hosted-pr-runner --base main` przed pierwszym pushem (lokalna gałąź już istnieje na `c7db08c` = `origin/main`; przed pushem `git fetch` i rebase, jeśli `main` się zmienił). Pierwszy commit zawiera folder zmiany (`change.md`, `research.md`, `plan.md`, `plan-brief.md`) i `context/foundation/lessons.md` (nowa lekcja z 2026-10-18).

#### 5. Instalacja na VM (operator + agent)

**File**: VM `github-runner` (poza repo)

**Intent**: Postawić 1 runner z etykietą `szczur-ci` i włączyć flagę.

**Contract**: Kolejno: użytkownik dodaje tymczasowe NOPASSWD; agent sprawdza wolne miejsce w VG i — jeśli jest — rozszerza `/` (`lvextend -r`); kopiuje `setup.sh`, pobiera token rejestracyjny (`gh api -X POST repos/kwmacioszek/szczur-radar/actions/runners/registration-token`), uruchamia `setup.sh` z `RUNNER_COUNT=1`; sprawdza runner `online` w `gh api repos/kwmacioszek/szczur-radar/actions/runners`; ustawia `gh variable set CI_RUNNER --body szczur-ci`; usuwa `/etc/sudoers.d/90-claude-setup`.

### Success Criteria:

#### Automated Verification:

- Składnia skryptu: `bash -n tools/ci-runner/setup.sh`
- `ci.yml` parsuje się, a każdy z 7 jobów ma wyrażenie przełącznika: `ruby -ryaml -e 'y=YAML.load_file(".github/workflows/ci.yml"); exit(y["jobs"].all?{|_,j| j["runs-on"].include?("vars.CI_RUNNER ||")} ? 0 : 1)'`
- Runner online: `gh api repos/kwmacioszek/szczur-radar/actions/runners -q '.runners[] | select(.name=="github-runner-1") | .status'` zwraca `online`
- Flaga ustawiona: `gh variable get CI_RUNNER` zwraca `szczur-ci`
- CI PR-a zielone na VM: wszystkie joby runu PR `success`, a `actions/runs/<id>/jobs` pokazuje `runner_name` = `github-runner-1` dla każdego joba
- Ponowne uruchomienie `setup.sh` na VM kończy się bez błędu i pomija zarejestrowany runner (idempotencja)

#### Manual Verification:

- Railway wdrożył PR env tej zmiany po CI (Wait for CI) i `/api/health` w PR env odpowiada
- Drugi push na gałąź: CI znów zielone, na VM `docker ps -a` nie pokazuje pozostałych kontenerów `web`/`api`
- Przełącznik w drugą stronę: po `gh variable delete CI_RUNNER` i pushu joby żądają `ubuntu-latest` (`labels` w API jobów), potem flaga ustawiona z powrotem
- Plik `/etc/sudoers.d/90-claude-setup` usunięty z VM (`sudo -n true` znowu wymaga hasła)

**Implementation Note**: Po fazie 1 zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka przed fazą 2.

---

## Phase 2: Dokumentacja i domknięcie

### Overview

Dokumentacja repo opisuje przełącznik i runner; roadmapa odzwierciedla zrobiony #30 i odłożony drugi runner; przegląd i archiwizacja na tej samej gałęzi przed merge'em człowieka.

### Changes Required:

#### 1. Wytyczne repozytorium

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Agent i człowiek wiedzą, gdzie idzie CI i jak je przełączyć.

**Contract**: Sekcja Project Structure: wpis `tools/ci-runner/` (setup VM, runbook). Sekcja Deployment, akapit o CI: joby idą na runner z `CI_RUNNER_PR` / `CI_RUNNER_MAIN` / `CI_RUNNER` (domyślnie `ubuntu-latest`); self-hosted runner na VM w Proxmoxie; runner offline = joby w kolejce, Railway pomija deploy po 2 h.

#### 2. Plan wdrożeń

**File**: `context/deployment/deploy-plan.md`

**Intent**: Runbook operatora w miejscu, gdzie są pozostałe runbooki zmian.

**Contract**: Nowa sekcja `## self-hosted-pr-runner: CI na runnerze w Proxmoxie` (jak działa przełącznik, zmienne, co zrobić gdy VM nie działa / limit wyczerpany / po odnowieniu limitu, ponowna rejestracja runnera, znane ryzyko: sekrety CI i brak izolacji sieci VM). Wpis troubleshootingu „CI wait / deploy stuck in WAITING” uzupełniony o zakolejkowane joby na runnerze offline. Runbook po merge'u (weryfikacja `push` na `main` na VM i deployu produkcji, zamknięcie #30) — w tej sekcji, nie w Progress.

#### 3. README API i komentarze

**File**: `szczur-radar-api/README.md`

**Intent**: Opis testów w CI nie zakłada już `ubuntu-latest` jako jedynego runnera.

**Contract**: Zdanie o jobie `backend` i Dockerze (okolice `:231`) wskazuje, że runner (GitHub albo VM z `tools/ci-runner`) ma Dockera.

#### 4. Roadmapa i SVG

**File**: `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Pozycja `#30` w `## Parked` odzwierciedla zrobiony przełącznik i runner; drugi runner (unikalne porty/nazwy, `RUNNER_COUNT=2`) zapisany jako nowa pozycja Parked z powodem.

**Contract**: Edycja w miejscu pozycji Parked (`roadmap.md:347`) i dopisana nowa; dane w `tools/render_roadmap_svg.py` (lista Parked, `:194`) zgodne; SVG wygenerowany `python3 tools/render_roadmap_svg.py`.

#### 5. Przegląd i archiwizacja

**File**: `context/changes/self-hosted-pr-runner/`

**Intent**: Zgodnie z lekcją „Implementacja, przegląd i archiwizacja na jednej gałęzi”: `/10x-impl-review` z poprawkami i `/10x-archive` przed merge'em człowieka.

**Contract**: Raport przeglądu w folderze zmiany; zmiana zarchiwizowana na tej gałęzi; PR gotowy, CI zielone na VM; merge wykonuje człowiek.

### Success Criteria:

#### Automated Verification:

- SVG roadmapy wygenerowany bez błędu: `python3 tools/render_roadmap_svg.py`
- Każdy odsyłacz do `tools/ci-runner` w dokumentach istnieje: `grep -rn "tools/ci-runner" REPOSITORY_GUIDELINES.md context/deployment/deploy-plan.md szczur-radar-api/README.md` zwraca trafienia, a `test -f tools/ci-runner/README.md`
- CI PR-a zielone na VM po commitach dokumentacji

#### Manual Verification:

- Runbook w `deploy-plan.md` przeczytany przez człowieka: wiadomo, co zrobić, gdy VM nie działa i gdy limit się odnowi
- `/10x-impl-review` wykonany, ustalenia rozstrzygnięte
- Zmiana zarchiwizowana na gałęzi; PR przekazany człowiekowi do merge'a

---

## Testing Strategy

### Unit Tests:

- Brak — zmiana dotyczy konfiguracji CI i skryptu infrastruktury. `bash -n` na `setup.sh`.

### Integration Tests:

- Właściwym testem jest CI samego PR-a na VM: 7 jobów (Testcontainers, `services: postgres`, `--network host`, buildy obrazów, `tools/*/test.sh`, `railway-config`) zielone na `github-runner-1`.

### Manual Testing Steps:

1. Push na gałąź z `CI_RUNNER=szczur-ci` → wszystkie joby na `github-runner-1`, zielone; PR env wdrożony.
2. Drugi push → brak kolizji nazw kontenerów, CI zielone.
3. `gh variable delete CI_RUNNER` + push → joby żądają `ubuntu-latest`; flaga z powrotem.
4. Ponowne `setup.sh` → bez zmian, bez błędu.

## Performance Considerations

- 1 runner wykonuje joby po kolei: run ok. 9–10 min zamiast ok. 3,7 min (`research.md`, sekcja 6). Dwa PR-y naraz czekają w kolejce.
- Obrazy i cache Mavena/npm zostają na VM między runami, więc kolejne buildy są szybsze; `docker-prune` usuwa obrazy starsze niż 72 h.
- `/` ma 31 GB — przy braku wolnego miejsca w VG ryzyko zapełnienia dysku obrazami.

## Migration Notes

- Domyślnie (bez zmiennych) zachowanie `ci.yml` jest identyczne jak dziś.
- Inne otwarte PR-y (m.in. #81) dostają przełącznik dopiero po wciągnięciu `main` po merge'u tej zmiany; do tego czasu ich CI pada na limicie.
- Po merge'u, przy ustawionym `CI_RUNNER`, także `push` na `main` (bramka deployu produkcji) idzie na VM. Żeby trzymać produkcję na GitHubie po odnowieniu limitu: `gh variable set CI_RUNNER_MAIN --body ubuntu-latest`.
- Wycofanie: `gh variable delete CI_RUNNER` (bez commita); zatrzymanie i wyrejestrowanie runnera wg `tools/ci-runner/README.md`.

## References

- Research: `context/changes/self-hosted-pr-runner/research.md` (z sekcją Follow-up 2026-10-18)
- Issue #30: „CI: self-hosted runner (lokalny Docker) dla PR, GitHub Actions dla main”
- PR #81 (S-14 `safe-deploy-gate`) — zmienia `ci.yml`
- Railway Wait for CI: https://docs.railway.com/deployments/github-autodeploys
- Wzorzec narzędzia z runbookiem: `tools/db-backup/README.md`, `tools/pipeline-trigger/README.md`
- Lekcje: „Implementacja, przegląd i archiwizacja na jednej gałęzi”, „Wiąż gałąź zmiany z jej issue przez Development”, „Przed /10x-implement nie modyfikuj plików kodu ani konfiguracji” (`context/foundation/lessons.md`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Przełącznik i runner na VM

#### Automated

- [x] 1.1 Składnia skryptu: `bash -n tools/ci-runner/setup.sh`
- [x] 1.2 `ci.yml` parsuje się, a każdy z 7 jobów ma wyrażenie przełącznika
- [x] 1.3 Runner online: `github-runner-1` ma status `online`
- [x] 1.4 Flaga ustawiona: `gh variable get CI_RUNNER` zwraca `szczur-ci`
- [ ] 1.5 CI PR-a zielone na VM: wszystkie joby `success` na `github-runner-1`
- [x] 1.6 Ponowne uruchomienie `setup.sh` na VM kończy się bez błędu i pomija zarejestrowany runner

#### Manual

- [ ] 1.7 Railway wdrożył PR env tej zmiany po CI i `/api/health` w PR env odpowiada
- [ ] 1.8 Drugi push: CI znów zielone, na VM brak pozostałych kontenerów `web`/`api`
- [ ] 1.9 Przełącznik w drugą stronę: bez `CI_RUNNER` joby żądają `ubuntu-latest`, potem flaga z powrotem
- [ ] 1.10 Plik `/etc/sudoers.d/90-claude-setup` usunięty z VM

### Phase 2: Dokumentacja i domknięcie

#### Automated

- [ ] 2.1 SVG roadmapy wygenerowany bez błędu: `python3 tools/render_roadmap_svg.py`
- [ ] 2.2 Każdy odsyłacz do `tools/ci-runner` w dokumentach istnieje
- [ ] 2.3 CI PR-a zielone na VM po commitach dokumentacji

#### Manual

- [ ] 2.4 Runbook w `deploy-plan.md` przeczytany przez człowieka
- [ ] 2.5 `/10x-impl-review` wykonany, ustalenia rozstrzygnięte
- [ ] 2.6 Zmiana zarchiwizowana na gałęzi; PR przekazany człowiekowi do merge'a
