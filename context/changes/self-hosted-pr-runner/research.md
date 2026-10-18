---
date: 2026-10-18T13:27:30Z
researcher: Claude (Opus 5.5) z kwmacioszek
git_commit: c7db08c
branch: feature/self-hosted-pr-runner
repository: kwmacioszek/szczur-radar
topic: "Self-hosted runner GitHub Actions na lokalnym Macu z przełącznikiem runnera (osobno PR i main)"
tags: [research, ci, github-actions, self-hosted-runner, railway, wait-for-ci, docker, testcontainers]
status: complete
last_updated: 2026-10-18
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Kontynuacja 2026-10-18: wariant Proxmox VM zamiast Maca; wyczerpany limit Actions"
---

# Research: self-hosted runner GitHub Actions z przełącznikiem runnera

**Date**: 2026-10-18T13:27:30Z
**Researcher**: Claude (Opus 5.5) z kwmacioszek
**Git Commit**: c7db08c
**Branch**: feature/self-hosted-pr-runner
**Repository**: kwmacioszek/szczur-radar (prywatne)

## Research Question

Z `change.md`: zaoszczędzić darmowe minuty GitHub Actions (prywatne repo), uruchamiając CI na lokalnym runnerze, z łatwym przełącznikiem (zmienna/flaga) wskazującym runner — najlepiej osobno dla PR env (zdarzenie `pull_request`) i dla produkcji (`push` na `main`).

Pytania robocze: (1) czy oszczędność jest realna i ile minut zużywamy; (2) jak zbudować przełącznik w `runs-on`; (3) które z jobów `ci.yml` mogą działać na Macu użytkownika i co trzeba zmienić; (4) co się dzieje z Railway „Wait for CI”, gdy runner jest offline; (5) bezpieczeństwo sekretów; (6) wcześniejsze decyzje i kolizje z trwającą pracą.

## Summary

- **Oszczędność jest realna, jeśli konto jest na planie Free.** W 30 dniach (runy `ci.yml` utworzone od 2026-09-18, 200 runów: 166 `pull_request`, 34 `push`) joby zużyły ok. **1815 rozliczanych minut** (suma czasu jobów zaokrąglonego w górę do pełnej minuty, wyliczone z API jobów), z czego **1499 (~83%) na PR-ach** i 316 na `push`. Plan Free ma 2000 min/mies., Pro 3000 ([GitHub docs](https://docs.github.com/en/billing/concepts/product-billing/github-actions)). Planu konta nie udało się odczytać (`gh api` wymaga zakresu `user`) — luka.
- **Self-hosted runnery są dziś darmowe.** Zapowiedziana na 2026-03-01 opłata $0,002/min za self-hosted została **odroczona bez nowej daty** ([changelog 2025-12-16, aktualizacja](https://github.blog/changelog/2025-12-16-coming-soon-simpler-pricing-and-a-better-experience-for-github-actions/)); w razie wejścia liczyłaby się do darmowego limitu.
- **Przełącznik jest prosty:** `runs-on` akceptuje wyrażenia z kontekstami `github` i `vars` ([contexts](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability)), więc dwie zmienne repozytorium (np. `CI_RUNNER_PR`, `CI_RUNNER_MAIN`) z domyślnym `ubuntu-latest` dają przełączanie bez commita (`gh variable set`/`delete`).
- **Najmocniejsze ograniczenie: Wait for CI.** Railway czeka na wynik każdego workflow; jeśli nie skończą się w **2 h, deploy jest pomijany** ([Railway docs](https://docs.railway.com/deployments/github-autodeploys)), a zakolejkowany job czeka na runner do 24 h. Uśpiony Mac przy `push` na `main` = pominięty deploy produkcji. Dlatego `main` powinien domyślnie zostać na `ubuntu-latest`, a self-hosted dotyczyć PR-ów (zgodne z issue #30).
- **Nie wszystkie joby przejdą na natywny runner macOS.** `api-container-smoke` używa `services:` (na self-hosted tylko Linux) i `--network host` (Docker Desktop ≥ 4.34 z włączoną opcją; lokalnie jest **4.18**). `railway-config` trzyma token workspace Railway i klucze Wykop — rekomendacja issue #30 i dokumentacji GitHub: zostawić na runnerze GitHuba. Pozostałe 5 jobów wygląda na przenośne (z poprawkami czyszczenia kontenerów).
- **Kolizja z PR #81 (S-14 `safe-deploy-gate`, OPEN)**: zmienia `ci.yml` (nowy job `deploy-gate-test`, `railway-config` dostaje `needs:` na wszystkie joby). Ta zmiana musi wyjść z `origin/main` po merge S-14 albo się na niego zrebase'ować.

## Detailed Findings

### 1. Zużycie minut i cennik

- Pomiar (ta sesja, `gh run list --workflow ci.yml --created '>=2026-09-18'` + `actions/runs/<id>/jobs`): 200 runów, 1815 minut po zaokrągleniu per job. Rozkład po jobach (nazwy z różnych gałęzi, więc liczby runów się różnią):

  | Job | Runy | Minuty | Śr. |
  | --- | --- | --- | --- |
  | backend (Maven verify) | 188 | 511 | 2,7 |
  | api container smoke test | 159 | 465 | 2,9 |
  | railway config (plan + apply IaC) | 200 | 205 | 1,0 |
  | web unit tests (Vitest) | 200 | 200 | 1,0 |
  | web container smoke test | 200 | 200 | 1,0 |
  | pipeline trigger test | 60 | 113 | 1,9 |
  | db backup test | 99 | 106 | 1,1 |
  | (inne/stare nazwy: `backend (lint-lock + tests)`, `deploy gate test`) | 15 | 15 | 1,0 |

- Zaokrąglanie dominuje przy krótkich jobach: run PR #81 (`gh run view 39130125679`) ma web-unit ~50 s, web-smoke ~32 s, railway-config ~20 s, a każdy liczy się jako pełna minuta ([pricing](https://docs.github.com/en/billing/reference/actions-runner-pricing)). Wall clock jednego runu ~3,7 min przy jobach równoległych, ~10 runner-minut przed zaokrągleniem.
- Stawka `ubuntu-latest` (Linux 2-core x64) po obniżce od 2026-01-01: $0,006/min ([pricing](https://docs.github.com/en/billing/reference/actions-runner-pricing)). Bez metody płatności Actions blokuje się po wyczerpaniu limitu ([billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)).
- Wniosek: przeniesienie samych jobów PR na self-hosted zdejmuje ~1499 z 1815 minut; zostawienie `railway-config` na GitHubie dokłada z powrotem ~1 min na każdy run PR (w pomiarze: 200 minut tego joba na 200 runów).

### 2. Przełącznik runnera

- `jobs.<id>.runs-on` dopuszcza konteksty `github, needs, strategy, matrix, vars, inputs` — nie `env` ani `secrets` ([contexts](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability)). Wyrażenie musi być w cudzysłowie; nieustawiona zmienna daje pusty string (fałszywy) ([choose the runner](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job)).
- Wzorce:
  - etykieta jako string: `runs-on: ${{ github.event_name == 'pull_request' && vars.CI_RUNNER_PR || vars.CI_RUNNER_MAIN || 'ubuntu-latest' }}` — uwaga: przy pustym `CI_RUNNER_PR` łańcuch `&& ||` spada do `CI_RUNNER_MAIN`, nie do `ubuntu-latest`;
  - czytelniej: funkcja `case(pred, val, …, default)` (w dokumentacji od 2026-01-26, tylko github.com) ([expressions](https://docs.github.com/en/actions/reference/workflows-and-actions/expressions#case));
  - tablica etykiet: `fromJSON(vars.X)` z guardem `vars.X && fromJSON(vars.X) || 'ubuntu-latest'` (wzorzec z praktyki, nie przykład z docs — UNCONFIRMED w docs).
- Repo osobiste nie ma runner groups (tylko organizacje) — wybór po etykietach ([runner groups](https://docs.github.com/en/actions/concepts/runners/runner-groups)). Zmienne środowisk (environment-level) — nie wiadomo, czy są rozwiązywane w `runs-on` (UNCONFIRMED); przełącznik na poziomie repozytorium.
- Brak wbudowanego fallbacku na runnery GitHuba, gdy self-hosted jest offline. Workaround (job sondujący `GET /repos/{o}/{r}/actions/runners`) wymaga uprawnień admina/PAT — `GITHUB_TOKEN` nie ma zakresu `administration` ([REST](https://docs.github.com/en/rest/actions/self-hosted-runners)). Przełącznik pozostaje więc ręczny.
- Przełącznik powinien być jeden dla wszystkich przenoszonych jobów (np. wspólne wyrażenie w każdym `runs-on`); GitHub Actions nie ma kotwic YAML współdzielonych między jobami w tym workflow — każde `runs-on` powtarza wyrażenie (do decyzji w planie: powtórzenie vs job `pick-runner` z `outputs` i `needs`, który sam kosztuje ~1 min hosted na run).

### 3. Maszyna lokalna (zmierzone w tej sesji)

- macOS 14.8.9 (Darwin 23), `arm64`, 8 CPU, 16 GiB RAM.
- Docker Desktop **4.18.0** (silnik 20.10.24), VM: 4 CPU, ~7,7 GiB. Brak Colimy i OrbStacka. Brak zainstalowanego runnera (`~/actions-runner` nie istnieje).
- `/bin/bash` to 3.2.57; w PATH użytkownika jest Homebrew bash 5.2 i `jq` (`/opt/homebrew/bin`). Runner jako LaunchAgent dostaje PATH z pliku `.path` zapisanego przy konfiguracji — trzeba upewnić się, że zawiera `/opt/homebrew/bin` (wniosek; nie sprawdzone na żywym runnerze).

### 4. Zgodność jobów `ci.yml` z natywnym runnerem macOS

| Job (`.github/workflows/ci.yml`) | Na natywnym macOS | Co blokuje / co zmienić |
| --- | --- | --- |
| `backend` (`:12-52`) | tak (prawdopodobnie) | Testcontainers z Docker Desktop for Mac jest wykrywany bez konfiguracji ([Testcontainers](https://java.testcontainers.org/supported_docker_environment/)); brak `testcontainers.properties`, `TestcontainersConfiguration.java:18-25` to zwykły `PostgreSQLContainer("postgres:18")`. `setup-java` Temurin 25 macOS-aarch64 — UNCONFIRMED w praktyce. Komentarz `:44-46` mówi o ubuntu-latest. |
| `web-unit` (`:54-70`) | tak | `setup-node` 22 + `npm ci`. |
| `web-container-smoke` (`:72-102`) | tak, z poprawką | `-p 8080:8080` + `curl localhost` działa z Docker Desktop. Stała nazwa kontenera `web` (`:84`) bez `docker rm` — na trwałym runnerze drugi run padnie na kolizji nazwy. Port 8080 koliduje przy równoległych runnerach. |
| `api-container-smoke` (`:104-178`) | **nie** bez przeróbki | `services: postgres` (`:112-125`) — „Container operations are only supported on Linux runners” ([runner source](https://github.com/actions/runner/blob/main/src/Runner.Worker/ContainerOperationProvider.cs)). `--network host` (`:153,159`) wymaga Docker Desktop ≥ 4.34 z opcją „Enable host networking” ([Docker docs](https://docs.docker.com/engine/network/drivers/host/)); lokalnie 4.18. Stała nazwa `api` (`:159`). Opcja: przepisać na sieć Dockera (`docker network create` + `docker run postgres:18`), jak robią testy narzędzi. |
| `db-backup-test` (`:180-191`) | tak (prawdopodobnie) | `tools/db-backup/test.sh`: sieć i wolumen nazwane per PID z pułapką sprzątającą (`:21-39`, `:60-63`), migracje przez stdin (`:99`), brak bind mountów z hosta; komendy GNU tylko w obrazie (`in_image`, `:45`). Na hoście bash bez cech 4.x (grep w tej sesji). Nie uruchomione na Macu. |
| `pipeline-trigger-test` (`:193-203`) | tak (prawdopodobnie) | `tools/pipeline-trigger/test.sh`: sieć Dockera per PID (`:203`), sprzątanie (`:38`), brak `-v`/`--network host`. Czułe czasowo (limity 2–3 s). Nie uruchomione na Macu. |
| `railway-config` (`:205-377`) | technicznie tak, **rekomendacja: zostaje na GitHubie** | Sekrety: `RAILWAY_API_TOKEN` to token workspace sięgający wszystkich projektów (`deploy-plan.md:869`), `WYKOP_KEY/SECRET` = klucze aplikacji jak na produkcji (`ci.yml:308-339`). `npm install --global @railway/cli` (`:240`) zmienia trwały runner. Issue #30 stawia to jako pytanie. |

- Architektura: obrazy budowane na Apple Silicon są `arm64`, a Railway buduje i uruchamia `amd64` (wniosek; nic w repo nie testuje parytetu). Smoke testy na Macu sprawdzają więc inny wariant obrazu niż produkcja — dla PR-ów prawdopodobnie akceptowalne, `main` i tak zostaje na GitHubie (do decyzji w planie).
- Docker Hub: pull z self-hosted podlega limitom Docker Hub, hosted runnery są z nich zwolnione ([limits](https://docs.github.com/en/actions/reference/limits)); lokalny cache obrazów łagodzi problem.

### 5. Alternatywa: runner Linux w kontenerze na Macu

- Runner w kontenerze odmawia funkcji kontenerowych, gdy wykryje, że sam działa w kontenerze („Container feature is not supported when runner is already running inside container”, [ContainerOperationProvider.cs](https://github.com/actions/runner/blob/main/src/Runner.Worker/ContainerOperationProvider.cs)); na cgroup v2 kontrola może przejść, ale to UNCONFIRMED, a porty `services:` i tak lądują w VM Dockera, nie w kontenerze runnera.
- Przy podmontowanym sockecie `localhost` w jobie to kontener runnera, nie host Dockera — psuje `curl localhost` w obu smoke testach; Testcontainers potrzebuje `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` i zgodnych ścieżek workspace ([DinD patterns](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/)).
- Zaleta: izolacja (issue #30: „runner w kontenerze z ograniczonymi uprawnieniami”), tryb ephemeral (`myoung34/docker-github-actions-runner`, `EPHEMERAL=true`). Koszt: więcej konfiguracji i te same przeróbki smoke testów co na macOS, plus problemy z siecią. Wniosek: natywny runner macOS na dedykowanym użytkowniku jest prostszy; kontener daje lepszą izolację kosztem złożoności.

### 6. Runner offline i współbieżność

- Job bez pasującego runnera online czeka do 24 h, potem jest anulowany ([self-hosted reference](https://docs.github.com/en/actions/reference/runners/self-hosted-runners), [limits](https://docs.github.com/en/actions/reference/limits)).
- Railway Wait for CI: czeka na wynik każdego workflow (nie jobów); failure → deploy pominięty od razu; skipped/neutral nie blokują; cancelled blokuje, chyba że inny workflow tego commita przeszedł; **nie skończone w 2 h → deploy pominięty** ([Railway docs](https://docs.railway.com/deployments/github-autodeploys), sprawdzone w tej sesji). Repo dokumentuje tylko stan `WAITING` (`deploy-plan.md:938`).
- Konsekwencja: Mac offline + PR → PR env się nie wdraża (do przełączenia zmiennej i re-run); Mac offline + `push` na `main` → produkcja pominięta po 2 h. Po S-14 (PR #81) Wait for CI obejmie też crony (`checkSuites: true`), więc zakres skutków rośnie.
- Jeden runner = jeden job naraz ([routing](https://docs.github.com/en/actions/reference/runners/self-hosted-runners)). Joby poza `railway-config` puszczone sekwencyjnie to suma ich czasów (~9 min dla 7 jobów runu 39130125679, z `deploy-gate-test` z PR #81) zamiast ~3,7 min. Kilka runnerów na jednej maszynie wymaga osobnych katalogów; wtedy kolidują stałe porty (8080, 8000, 5432) i nazwy kontenerów (`web`, `api`). VM Dockera ma 4 CPU / 7,7 GiB — równoległe Testcontainers + buildy obrazów mogą ją wysycić (wniosek).
- Po PR #81 `railway-config` ma `needs:` na wszystkie pozostałe joby — hosted `railway-config` czeka na self-hosted joby, więc czas runu PR wyznacza najwolniejsza ścieżka na Macu.

### 7. Bezpieczeństwo

- Self-hosted runner może zostać trwale skompromitowany przez kod z PR; w prywatnym repo dotyczy osób z prawem odczytu/forka ([secure use](https://docs.github.com/en/actions/reference/security/secure-use#hardening-for-self-hosted-runners)). Repo ma jednego autora; fork PR-y nie dostają sekretów, a `railway-config` jest dla nich pomijany (`ci.yml:213-216`).
- Na Macu wylądowałyby: `LLM_API_KEY` (klucz CI, nieprodukcyjny — `ci.yml:30-52,133-135`), a jeśli `railway-config` też przejdzie — token workspace Railway i klucze Wykop. GitHub zaleca dedykowanego użytkownika bez kluczy SSH/tokenów chmurowych na dysku; `config.sh` odmawia roota; `svc.sh install` na macOS tworzy LaunchAgent działający jako ten użytkownik (prawdopodobnie tylko po zalogowaniu — UNCONFIRMED w docs GitHub) ([darwin.svc.sh.template](https://github.com/actions/runner/blob/main/src/Misc/layoutbin/darwin.svc.sh.template)).
- Brak czyszczenia między jobami domyślnie; `actions/checkout` robi `clean: true`; hooki `ACTIONS_RUNNER_HOOK_JOB_STARTED/COMPLETED` mogą sprzątać kontenery ([run scripts](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/run-scripts)).

### 8. Wersje akcji i runnera

- Ostatni run (2026-10-18, PR #81) pokazuje ostrzeżenia: `actions/checkout@v4` „forced to run on Node.js 24”, `setup-java v4 is deprecated … migrate to actions/setup-java@v5`. Runy przechodzą (`success`), więc to nie bloker, ale self-hosted runner musi mieć wersję wspierającą Node 24 (setup-java wymaga runnera ≥ 2.327.1; najnowszy v2.338.0 z 2026-10-06 — wg raportu agenta webowego). Podbicie akcji to osobny temat, poza zakresem tej zmiany (do decyzji).

## Code References

- `.github/workflows/ci.yml:3-5` — workflow celowo bez path-filtra (Wait for CI ocenia cały workflow).
- `.github/workflows/ci.yml:14,56,74,106,182,195,207` — siedem `runs-on: ubuntu-latest` (miejsca przełącznika).
- `.github/workflows/ci.yml:112-125` — `services: postgres` w `api-container-smoke` (Linux-only na self-hosted).
- `.github/workflows/ci.yml:153,159` — `docker run --network host`.
- `.github/workflows/ci.yml:84,159` — stałe nazwy kontenerów `web`/`api` bez usuwania.
- `.github/workflows/ci.yml:213-227` — warunek fork, concurrency i sekrety `railway-config`.
- `.railway/railway.ts:16-20` — `checkSuites: true` (Wait for CI) dla api i web.
- `context/deployment/deploy-plan.md:869` — token workspace sięga wszystkich projektów workspace'u.
- `context/deployment/deploy-plan.md:938` — „deploy stuck in WAITING” w troubleshootingu.
- `tools/db-backup/test.sh:21-39,45,60-63` — zasoby Dockera per PID, sprzątanie, wolumen nazwany.
- `tools/pipeline-trigger/test.sh:38,203` — sieć per PID i sprzątanie.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/TestcontainersConfiguration.java:18-25` — kontener Postgres 18 dla `*IT`.

## Architecture Insights

- Jedyny workflow i brak path-filtra to twarda reguła (Wait for CI): przełącznik musi zmieniać tylko `runs-on`, nie `if:` na poziomie jobów ani `on:` — job pominięty przez `if:` nie blokuje bramki, ale też niczego nie testuje.
- Testy narzędzi (`tools/*/test.sh`) już są „runner-agnostyczne” (sieci per PID, sprzątanie, bez hosta); smoke testy w `ci.yml` nie — zakładają czysty, jednorazowy runner Linux (`services:`, `--network host`, stałe nazwy). Przepisanie `api-container-smoke` na wzorzec z `tools/*/test.sh` usuwa oba blokery i działa też na `ubuntu-latest`.
- Alternatywna/uzupełniająca oszczędność bez self-hosted: łączenie krótkich jobów (web-unit, web-smoke, railway-config po ~1 min każdy z zaokrąglenia) — np. połączenie `web-unit` i `web-container-smoke` zdjęłoby ~200 min/30 dni wg pomiaru. Wybór należy do planu/użytkownika.
- Dokumenty do aktualizacji przy zmianie `runs-on`: `REPOSITORY_GUIDELINES.md:58` (lista jobów CI), `context/deployment/deploy-plan.md` (sekcje CI `:866-876`, troubleshooting `:938-942`), `szczur-radar-api/README.md:231`, komentarz `ci.yml:44-46`, `context/foundation/roadmap.md:347` (Parked → wykonane; wtedy też `tools/render_roadmap_svg.py` i `docs/visuals/roadmap.svg`).

## Historical Context (from prior changes)

- Issue #30 (OPEN) „CI: self-hosted runner (lokalny Docker) dla PR, GitHub Actions dla main — nice-to-have po MVP” — już formułuje: wybór runnera po zdarzeniu w jednym `ci.yml`, Docker dla Testcontainers, zapasowy przełącznik zmienną repozytorium, runner w kontenerze bez lokalnych kluczy, otwarte pytanie o `railway-config`; produkcyjny przepływ deployu poza zakresem. Ta zmiana realizuje to issue (wg reguły repo zmiana spoza roadmapy dostaje nowe issue `<change-id>: …` — ale tu istnieje dedykowane issue #30; do decyzji, którego użyć).
- `context/foundation/roadmap.md:347` — pozycja w `## Parked` („najwcześniej po F-02”); `roadmap.md:29` — wpis v2.6 z 2026-10-03.
- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:196` — #30 jako otwarte/L.
- `context/archive/2026-10-15-pipeline-run-history-dashboard/reviews/impl-review.md:102` — test z `PIPELINE_STALL_TIMEOUT=2s` ma 1 s zapasu, „a slow CI runner could flake it” — istotne dla słabszej lokalnej maszyny.
- PR #81 (S-14 `safe-deploy-gate`, OPEN, `feature/safe-deploy-gate`) — zmienia `ci.yml` (job `deploy-gate-test`, `needs:` w `railway-config`, Railway CLI 5.67.1, `--plan plan.pin`) i `railway.ts` (`checkSuites` dla cronów).

## Related Research

- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` — audyt po MVP (C4: czy deploy wywołany `railway config apply` czeka na CI — nierozstrzygnięte; C5: nieaktualny komentarz o testach db-backup w `railway.ts:221-222`).
- `context/changes/safe-deploy-gate/research.md` (na gałęzi PR #81, nieprzeczytany w tej sesji).

## Open Questions

1. **Plan konta GitHub (Free 2000 vs Pro 3000 min)** — nieodczytany; przy Pro obecne zużycie (~1815/30 dni) mieści się w limicie i zysk jest mniejszy.
2. **Zakres przełącznika dla `main`** — czy w ogóle pozwalać na self-hosted dla `push` na `main` (ryzyko pominiętego deployu po 2 h), czy zmienna `CI_RUNNER_MAIN` istnieje tylko „na wszelki wypadek”.
3. **`railway-config`** — zostaje na `ubuntu-latest` (rekomendacja z dowodów) czy idzie za przełącznikiem.
4. **`api-container-smoke`** — przepisać na sieć Dockera (działa wszędzie) czy zostawić na hosted; alternatywnie upgrade Docker Desktop ≥ 4.34 z host networking (nadal nie rozwiązuje `services:`).
5. **Forma runnera** — natywny macOS (dedykowany użytkownik, LaunchAgent) vs kontener Linux (izolacja, ephemeral, więcej problemów sieciowych); liczba runnerów (1 = sekwencyjnie, kilka = kolizje portów/nazw).
6. **Nieprzetestowane na Macu**: `setup-java` Temurin 25 aarch64, `tools/*/test.sh` (zależność czasowa), PATH LaunchAgenta (`/opt/homebrew/bin`), parytet `arm64` vs `amd64` obrazów.
7. **Kolejność z S-14** — start implementacji po merge PR #81 czy rebase w trakcie.
8. **Issue** — użyć istniejącego #30 czy założyć `self-hosted-pr-runner: …`.

## Follow-up 2026-10-18: wariant Proxmox VM, wyczerpany limit

Zastępuje wnioski o Macu jako hoście runnera (sekcje 3–5 pozostają jako uzasadnienie odrzucenia tej opcji).

- **Limit wyczerpany (obserwacja):** run `39160654420` (2026-10-18 19:51 UTC, `feature/safe-deploy-gate`) — każdy job `failure` z 0 krokami i adnotacją „The job was not started because recent account payments have failed or your spending limit needs to be increased”. Konsekwencja: przy wyczerpanym limicie każdy job pozostawiony na `ubuntu-latest` kończy workflow porażką, a Railway pomija deploy od razu (Railway docs, sekcja 6). Dlatego wszystkie joby, także `railway-config` i `api-container-smoke`, muszą móc iść za przełącznikiem — zastępuje to rekomendację „railway-config zawsze na GitHubie” z sekcji 4.
- **VM (zmierzone przez SSH, `krzysiek@192.168.10.123`, hostname `github-runner`):** Ubuntu 24.04.5 LTS, `x86_64`, 4 vCPU, 7 GiB RAM, `/` 31 GB (LVM, 23 GB wolne; wolne miejsce w VG nieznane bez sudo), wychodzący HTTPS do `api.github.com` działa; brak `docker`, `git`, `jq`; `sudo` wymaga hasła.
- **Zgodność:** na Linuksie z Dockerem działają `services:` i `--network host` (wymóg GitHuba dla self-hosted: Linux + Docker, sekcja 4), więc wszystkie joby `ci.yml` przechodzą bez przeróbek; obrazy budują się jako `amd64` (parytet z Railway). Pozostaje tylko trwałość runnera: stałe nazwy kontenerów `web`/`api` (`ci.yml:84,159`) i porty hosta 8080/8000/5432 — przy 1 runnerze wystarczy sprzątanie nazw, przy 2 runnerach potrzebne unikalne porty.
- **Repo:** workflowy z forków prywatnego repo są wyłączone (`gh api repos/kwmacioszek/szczur-radar/actions/permissions/fork-pr-workflows-private-repos` → wszystkie pola `false`).
- **Adnotacja w runach:** „The ubuntu-latest label will migrate to Ubuntu 26 beginning October 19, 2026” — dotyczy tylko runnerów GitHuba; VM zostaje na 24.04.
