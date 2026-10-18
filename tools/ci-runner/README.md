# ci-runner

Self-hosted runnery GitHub Actions dla `.github/workflows/ci.yml` na VM z Ubuntu 24.04 w domowym Proxmoxie (zmiana `self-hosted-pr-runner`, issue #30). Powód: darmowe minuty GitHub Actions prywatnego repo kończą się przed końcem miesiąca (ok. 1800 min w 30 dni przy limicie 2000), a po ich wyczerpaniu żaden job nie startuje („The job was not started because … your spending limit needs to be increased”), workflow kończy się porażką i Railway pomija deploy.

## Przełącznik

`runs-on` każdego joba w `ci.yml` wybiera etykietę runnera ze zmiennych repozytorium (pierwsza niepusta wygrywa):

| Zmienna | Dotyczy |
| --- | --- |
| `CI_RUNNER_PR` | tylko `pull_request` (PR env) |
| `CI_RUNNER_MAIN` | tylko `push` na `main` (deploy produkcji) |
| `CI_RUNNER` | wszystkie zdarzenia |
| (żadna) | `ubuntu-latest`, runnery GitHuba |

```bash
gh variable set CI_RUNNER --body szczur-ci   # wszystko na Proxmoxa
gh variable delete CI_RUNNER                 # wszystko z powrotem na GitHuba
gh variable list                             # co jest ustawione
```

Zmiana działa od następnego runu (nowy commit). GitHub bierze `runs-on` z `ci.yml` gałęzi, więc gałąź PR-a sprzed tej zmiany trzeba zaktualizować z `main`.

**Uwaga:** etykieta, której nie ma żaden runner online (VM wyłączona, usługa padła), zostawia joby w kolejce. Railway czeka na wynik CI najwyżej 2 h, potem pomija deploy, a GitHub anuluje job po 24 h. Przy wyłączonej VM usuń zmienną, o ile limit minut na GitHubie na to pozwala, albo ustaw budżet w Billing.

## Instalacja na VM

VM: Ubuntu Server 24.04, x86_64 (Railway buduje `amd64`), min. 4 vCPU, 8 GB RAM, 50 GB dysku. Joby dostają sekrety CI (`LLM_API_KEY`, `RAILWAY_API_TOKEN`, `WYKOP_*`), więc VM nie powinna widzieć reszty sieci domowej (osobny VLAN albo firewall Proxmoxa: tylko wyjście do internetu). Runner potrzebuje tylko ruchu wychodzącego HTTPS do GitHuba.

```bash
# na Macu: token rejestracyjny (ważny 1 h, wymaga uprawnień admina repo)
token=$(gh api -X POST repos/kwmacioszek/szczur-radar/actions/runners/registration-token -q .token)
scp tools/ci-runner/setup.sh <user>@<vm>:
ssh -t <user>@<vm> "sudo RUNNER_TOKEN=$token RUNNER_COUNT=1 ./setup.sh"
gh api repos/kwmacioszek/szczur-radar/actions/runners -q '.runners[] | [.name, .status, .busy] | @tsv'
```

`setup.sh` instaluje Docker (`docker.io`, `docker-buildx`), `git`, `jq`, `curl`; tworzy użytkownika `gha` (bez sudo, w grupie `docker`); pobiera najnowszy runner (sprawdza SHA-256 z wydania); rejestruje `RUNNER_COUNT` runnerów z etykietą `szczur-ci` jako usługi systemd (`actions.runner.*`); ustawia rotację logów kontenerów i codzienne sprzątanie Dockera, które działa tylko przy zajętości `/` od 70% (obrazy bazowe, cache builda i zależności zostają między runami, więc kolejne runy są szybsze niż pierwszy). Ponowne uruchomienie pomija to, co już jest.

Joby `ci.yml` działają na takim runnerze bez zmian (`services:`, `--network host`, Testcontainers). Na trwałym runnerze joby sprzątają po sobie kontenery o stałych nazwach (`web`, `api`).

**Jeden runner** wykonuje joby po kolei (run PR ok. 10 min zamiast ok. 4). Drugi runner wymaga najpierw unikalnych portów i nazw kontenerów w smoke testach (`8080`, `8000`, `5432` na hoście), inaczej dwa równoległe runy się zderzą.

## Obsługa

```bash
systemctl status 'actions.runner.*'                       # stan usług
journalctl -u 'actions.runner.*' -f                       # logi runnera
cd /home/gha/actions-runner-1 && sudo ./svc.sh stop       # zatrzymanie
# wyrejestrowanie (token z: gh api -X POST repos/kwmacioszek/szczur-radar/actions/runners/remove-token -q .token)
cd /home/gha/actions-runner-1 && sudo ./svc.sh uninstall && sudo -u gha ./config.sh remove --token <token>
```

Runner aktualizuje się sam. Runner offline dłużej niż 14 dni GitHub usuwa i trzeba go zarejestrować ponownie (`setup.sh` po skasowaniu katalogu `actions-runner-N`).
