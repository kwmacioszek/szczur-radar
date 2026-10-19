# Self-hosted runnery CI (Proxmox + PC) — Plan Brief

> Full plan: `context/changes/self-hosted-pr-runner/plan.md`
> Research: `context/changes/self-hosted-pr-runner/research.md`

## What & Why

Darmowe minuty GitHub Actions prywatnego repo skończyły się 2026-10-18 (ok. 1815 min w 30 dni przy limicie 2000). Żaden job CI nie startuje, więc Railway nie wdraża ani PR env, ani produkcji, a development stoi. Przenosimy CI na własny runner na VM w domowym Proxmoxie z jedną flagą, która przełącza je z powrotem na runnery GitHuba.

## Starting Point

Siedem jobów `.github/workflows/ci.yml` ma na sztywno `runs-on: ubuntu-latest`. Jest gotowa VM `github-runner` (Ubuntu 24.04, x86_64, 4 vCPU, 7 GB, dostęp SSH dla agenta), jeszcze bez Dockera i runnera. W working tree leży szkic przełącznika i skryptu instalacyjnego (bez commita), który faza 1 domyka.

## Desired End State

`gh variable set CI_RUNNER --body szczur-ci` przenosi całe CI (PR i `main`) na VM, `gh variable delete CI_RUNNER` przywraca GitHuba — bez commita. PR tej zmiany przechodzi na VM i wdraża PR env, VM jest odtwarzalna skryptem z repo, a runbook mówi, co robić, gdy VM nie działa. Na czas developmentu dochodzą 4 runnery w VM-kach KVM na PC (ta sama etykieta): przy włączonym PC joby rozkładają się na pulę 5 runnerów, przy wyłączonym bierze je Proxmox.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Host runnera | VM Ubuntu w Proxmoxie | Linux uruchamia wszystkie joby bez przeróbek, buduje `amd64` jak Railway, działa całą dobę i jest oddzielony od laptopa. | Plan |
| Mac jako runner | odrzucony | `services:` i `--network host` nie działają na macOS, laptop zasypia. | Research |
| Przełącznik | `CI_RUNNER` (wszystko) + opcjonalne `CI_RUNNER_PR` / `CI_RUNNER_MAIN`, pusto = `ubuntu-latest` | Jedna flaga na co dzień, osobne nadpisanie PR / produkcji, gdy trzeba. | Plan |
| `railway-config` | za flagą jak reszta | Przy wyczerpanym limicie job na GitHubie pada i blokuje deploy; token trafia do VM, nie na laptopa. | Plan |
| `api-container-smoke` | za flagą, bez przeróbek | Na Linuksie działa jak jest; drugi największy konsument minut. | Plan |
| Liczba runnerów | 1 teraz, drugi osobną zmianą | Odblokowanie bez ruszania bramek; równoległość wymaga unikalnych portów 8080/8000/5432. | Plan |
| Izolacja sieci VM | na razie brak | Decyzja użytkownika; ryzyko zapisane w runbooku. | Plan |
| Instalacja | `tools/ci-runner/setup.sh` + README | Odtwarzalne, wzorem `tools/*/`. | Plan |
| Issue | istniejące #30 | Opisuje dokładnie tę zmianę. | Research |
| Fallback przy VM offline | ręczny (usunięcie flagi) | Automatyczny wymaga PAT z prawami admina. | Research |
| Runnery na PC (2026-10-19) | 4 VM KVM × 1 runner na Ubuntu PC, etykieta `szczur-ci` | Pula z Proxmoxem bez zmiany flagi; własny Docker na VM = brak kolizji portów bez zmian w `ci.yml`. | Plan |
| Hiperwizor na PC | KVM/libvirt (nie VirtualBox, nie WSL2) | Natywny na Ubuntu, bez konfliktu modułów, prosty skrypt z cloud-init. | Plan |
| Wyłączenie PC w trakcie joba | runbook + `gh run rerun` | Zero dodatkowego kodu. | Plan |
| Wykonanie fazy 2 | sesja na Macu przez SSH na `ci-admin` (bez sudo poza fazą) | Jedna sesja, ograniczone konto techniczne na PC. | Plan |
| Forma narzędzia na PC | interaktywne CLI w Pythonie: create / delete / status | Decyzja użytkownika: VM-ki w miarę potrzeb, parametry podawane przy uruchomieniu. | Plan |

## Scope

**In scope:**
- Przełącznik `runs-on` we wszystkich jobach `ci.yml`, sprzątanie kontenerów `web`/`api`
- `tools/ci-runner/setup.sh` i runbook; instalacja 1 runnera na VM; flaga `CI_RUNNER`
- `tools/ci-runner/pc_runners.py`: interaktywne CLI (create / delete / status) dla VM-ek z runnerami na PC w puli z Proxmoxem
- Dokumentacja (guidelines, deploy-plan, README API, roadmapa + SVG), impl-review i archiwizacja

**Out of scope:**
- Drugi runner w Proxmoxie i unikalne porty w smoke testach (osobna zmiana, Parked); priorytet PC przed Proxmoxem (GitHub tego nie wspiera)
- Izolacja sieci VM, automatyczny fallback, runner w kontenerze, runner na Macu
- Podbicie wersji akcji, łączenie jobów, zmiany w przepływie deployu produkcji

## Architecture / Approach

Każdy job ma w `runs-on` wyrażenie wybierające etykietę: `CI_RUNNER_PR` (PR) / `CI_RUNNER_MAIN` (push na `main`), potem `CI_RUNNER`, na końcu `ubuntu-latest`. Runner `github-runner-1` na VM ma etykietę `szczur-ci`, działa jako usługa systemd na użytkowniku `gha` (bez sudo, w grupie `docker`) i łączy się z GitHubem tylko ruchem wychodzącym. Railway Wait for CI nadal czeka na wynik całego workflow.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Przełącznik i runner na VM | CI na VM, flaga działa, PR env wdrożony | kolejność: runner online i flaga przed pierwszym pushem; dysk 31 GB |
| 2. Runnery na PC | CLI `pc_runners.py`, 4 VM-ki KVM z runnerami, pula z Proxmoxem, test przejęcia | wyłączenie PC w trakcie joba; uprawnienia libvirt bez sudo |
| 3. Dokumentacja i domknięcie | runbook, guidelines, roadmapa, impl-review, archiwizacja | konflikt z PR #81 (S-14) w `ci.yml` |

**Prerequisites:** VM z SSH (jest); tymczasowe NOPASSWD sudo dodane przez użytkownika na czas instalacji; uprawnienia admina repo dla `gh` (token rejestracyjny).
**Estimated effort:** ~2–3 sesje, 3 fazy (faza 1 zrobiona; faza 2 przez SSH na PC).

## Open Risks & Assumptions

- VM wyłączona lub runner offline → joby w kolejce, Railway pomija deploy po 2 h; przy wyczerpanym limicie nie ma do czego się przełączyć.
- Sekrety CI (`RAILWAY_API_TOKEN` workspace, `WYKOP_*`, `LLM_API_KEY`) leżą na VM, która widzi LAN (izolacja świadomie odłożona).
- Po merge'u przy ustawionym `CI_RUNNER` także bramka deployu produkcji idzie na VM.
- Inne otwarte PR-y odblokują się dopiero po wciągnięciu `main` z tą zmianą.
- Proxmox sam: run ok. 7 min z cache (10,4 min zimny); limity pull Docker Hub dla self-hosted łagodzi lokalny cache obrazów — przy 4 nowych VM-kach PC każda pobiera obrazy osobno.
- Wyłączenie PC w trakcie joba = czerwony job do `gh run rerun`.

## Success Criteria (Summary)

- Wszystkie joby PR-a zielone na `github-runner-1`, PR env wdrożony przez Railway.
- Flaga przełącza CI w obie strony bez commita.
- Runbook pozwala odtworzyć runner i zareagować na awarię VM.
