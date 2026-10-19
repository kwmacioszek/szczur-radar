# ci-runner

Self-hosted runnery GitHub Actions dla `.github/workflows/ci.yml` na VM z Ubuntu 24.04 w domowym Proxmoxie, a w czasie developmentu także w VM-kach na PC (sekcja „Runnery na PC”) (zmiana `self-hosted-pr-runner`, issue #30). Powód: darmowe minuty GitHub Actions prywatnego repo kończą się przed końcem miesiąca (ok. 1800 min w 30 dni przy limicie 2000), a po ich wyczerpaniu żaden job nie startuje („The job was not started because … your spending limit needs to be increased”), workflow kończy się porażką i Railway pomija deploy.

## Pliki

| Plik | Co robi |
| --- | --- |
| `setup.sh` | zamienia VM z Ubuntu 24.04 w host runnera: Docker, użytkownik `gha` bez sudo, jeden runner z etykietą `szczur-ci` jako usługa systemd (uruchamiany jako root, niżej) |
| `pc_runners.py` | tworzy, usuwa i pokazuje VM-ki KVM z runnerami na PC (`create` / `delete` / `status`, sekcja „Runnery na PC”) |

## Przełącznik

`runs-on` każdego joba w `ci.yml` wybiera etykietę runnera ze zmiennych repozytorium (pierwsza niepusta wygrywa):

| Zmienna | Dotyczy |
| --- | --- |
| `CI_RUNNER_PR` | tylko `pull_request` (PR env) |
| `CI_RUNNER_MAIN` | tylko `push` na `main` (deploy produkcji) |
| `CI_RUNNER` | wszystkie zdarzenia |
| (żadna) | `ubuntu-latest`, runnery GitHuba |

```bash
gh variable set CI_RUNNER --body szczur-ci   # wszystko na self-hosted runnery (Proxmox + PC)
gh variable delete CI_RUNNER                 # wszystko z powrotem na GitHuba
gh variable list                             # co jest ustawione
```

Zmiana działa od następnego runu: nowego commita albo ponownego uruchomienia starego (`gh run rerun <id>` czyta zmienne na nowo — sprawdzone 2026-10-18). GitHub bierze `runs-on` z `ci.yml` gałęzi, więc gałąź PR-a sprzed tej zmiany trzeba zaktualizować z `main`.

**Uwaga:** etykieta, której nie ma żaden runner online (VM wyłączona, usługa padła), zostawia joby w kolejce. Railway czeka na wynik CI najwyżej 2 h, potem pomija deploy, a GitHub anuluje job po 24 h. Przy wyłączonej VM usuń zmienną, o ile limit minut na GitHubie na to pozwala, albo ustaw budżet w Billing.

## Instalacja na VM

VM: Ubuntu Server 24.04, x86_64 (Railway buduje `amd64`), min. 4 vCPU, ok. 7–8 GB RAM, 50 GB dysku (VM `github-runner` w Proxmoxie: 4 vCPU, 7 GB, 61 GB). Joby dostają sekrety CI (`LLM_API_KEY`, `RAILWAY_API_TOKEN`, `WYKOP_*`), więc VM nie powinna widzieć reszty sieci domowej (osobny VLAN albo firewall Proxmoxa: tylko wyjście do internetu). **Znane ryzyko (2026-10-18):** dziś ani VM w Proxmoxie, ani VM-ki na PC nie są odcięte od LAN; NAT libvirt na PC chroni tylko przed ruchem przychodzącym. `setup.sh` przyjmuje tylko `RUNNER_COUNT=1` (jeden runner na VM). Runner potrzebuje tylko ruchu wychodzącego HTTPS do GitHuba.

```bash
# na Macu: token rejestracyjny (ważny 1 h, wymaga uprawnień admina repo)
token=$(gh api -X POST repos/kwmacioszek/szczur-radar/actions/runners/registration-token -q .token)
scp tools/ci-runner/setup.sh <user>@<vm>:
ssh -t <user>@<vm> "sudo RUNNER_TOKEN=$token RUNNER_COUNT=1 ./setup.sh"
gh api repos/kwmacioszek/szczur-radar/actions/runners -q '.runners[] | [.name, .status, .busy] | @tsv'
```

`setup.sh` instaluje Docker (`docker.io`, `docker-buildx`), `git`, `jq`, `curl`; tworzy użytkownika `gha` (bez sudo, w grupie `docker`); pobiera najnowszy runner (sprawdza SHA-256 z wydania); rejestruje `RUNNER_COUNT` runnerów z etykietą `szczur-ci` jako usługi systemd (`actions.runner.*`); ustawia rotację logów kontenerów i codzienne sprzątanie Dockera, które działa tylko przy zajętości `/` od 70% (obrazy bazowe, cache builda i zależności zostają między runami, więc kolejne runy są szybsze niż pierwszy). Ponowne uruchomienie pomija to, co już jest.

Joby `ci.yml` działają na takim runnerze bez zmian (`services:`, `--network host`, Testcontainers). Na trwałym runnerze joby sprzątają po sobie kontenery o stałych nazwach (`web`, `api`).

**Jeden runner na VM.** Runner wykonuje joby po kolei (sam Proxmox: run PR ok. 7 min z cache, ok. 10 min zimny). Drugi runner w tej samej VM wymaga najpierw unikalnych portów i nazw kontenerów w smoke testach (`8080`, `8000`, `5432` na hoście, kontenery `web`/`api`), inaczej dwa równoległe joby się zderzą. Więcej runnerów = więcej VM-ek, każda z własnym Dockerem (niżej).

## Runnery na PC

Na czas developmentu dochodzą runnery w VM-kach KVM na Ubuntu na PC (`pc_runners.py`), z tą samą etykietą `szczur-ci` co Proxmox. GitHub daje job **dowolnemu wolnemu runnerowi online** z tą etykietą (bez priorytetu), więc:

- PC włączony: joby rozkładają się na pulę (Proxmox + VM-ki PC), run trwa mniej więcej tyle co najdłuższy job (`backend`);
- PC wyłączony albo uruchomiony Windows: runnery PC są `offline`, GitHub ich nie wybiera, wszystko bierze Proxmox — bez zmiany `CI_RUNNER`;
- **przed wyłączeniem PC** poczekaj, aż runnery `ci-pc-*` skończą joby (`python3 pc_runners.py status` → `busy`); job przerwany wyłączeniem kończy się porażką po kilku minutach i wymaga `gh run rerun <id>`.

Host (raz, kontem z sudo):

```bash
sudo apt install -y qemu-kvm libvirt-daemon-system virtinst cloud-image-utils
sudo usermod -aG libvirt,kvm $USER    # potem wyloguj się i zaloguj
gh auth login                         # opcjonalnie: status runnerów, wyrejestrowanie przy delete, token przy create
```

```bash
python3 tools/ci-runner/pc_runners.py           # menu: 1) create  2) delete  3) status
python3 tools/ci-runner/pc_runners.py create    # pyta: ile VM-ek, vCPU, RAM (GB), dysk (GB), klucze SSH
python3 tools/ci-runner/pc_runners.py create --count 4 --cpus 4 --ram 8 --disk 60 --yes
python3 tools/ci-runner/pc_runners.py status    # VM-ki (stan, vCPU, RAM, IP, autostart) i ich runnery w GitHubie
python3 tools/ci-runner/pc_runners.py delete    # pyta które; wyrejestrowuje runner (gh) i usuwa VM z dyskami
```

`create` nadaje VM-kom kolejne wolne numery (`ci-pc-1`, `ci-pc-2`, …; runner `<vm>-1`), trzyma dyski w puli libvirt `ci-runners` (`/var/lib/libvirt/images/ci-runners`, dysk qcow2 na obrazie Ubuntu 24.04 cloud sprawdzonym SHA-256, rośnie w miarę zapisu) i przez cloud-init ustawia nazwę hosta, użytkownika `ops` z kluczami SSH (domyślnie z `~/.ssh/authorized_keys`, sudo bez hasła) oraz uruchamia `setup.sh` (jeden runner). Runner jest online ok. 1 min po utworzeniu VM-ki. Token rejestracyjny (ważny 1 h) nie zostaje nigdzie: `setup.sh` przekazuje go runnerowi przez środowisko (nie trafia do `auth.log`), cloud-init po rejestracji kasuje swoje kopie user-data i wyłącza się na stałe (`/etc/cloud/cloud-init.disabled`), a `create` (z zalogowanym `gh`) czeka, aż runner będzie online, i wysuwa oraz usuwa seed ISO z tokenem; bez `gh` wypisuje polecenia `virsh` do zrobienia tego ręcznie. Nieudany `create` usuwa wolumeny tej VM-ki, a przerwany upload — obraz bazowy. Sieć: NAT libvirt (`default`), VM-ki mają adresy `192.168.122.x` widoczne tylko z hosta: `ssh -J <user>@<pc> ops@<ip>`. VM-ki startują razem z Ubuntu (autostart; `--no-autostart` wyłącza). Zatrzymanie bez usuwania: `virsh shutdown ci-pc-N`, start: `virsh start ci-pc-N`.

Zasoby: VM-ka z 4 vCPU i 8 GB RAM wystarcza na każdy job; pierwszy run na nowej VM-ce jest wolniejszy, bo pobiera obrazy Dockera i zależności od zera.

## Obsługa

```bash
systemctl status 'actions.runner.*'                       # stan usług
journalctl -u 'actions.runner.*' -f                       # logi runnera
cd /home/gha/actions-runner-1 && sudo ./svc.sh stop       # zatrzymanie
# wyrejestrowanie (token z: gh api -X POST repos/kwmacioszek/szczur-radar/actions/runners/remove-token -q .token)
cd /home/gha/actions-runner-1 && sudo ./svc.sh uninstall && sudo -u gha ./config.sh remove --token <token>
```

Runner aktualizuje się sam. Runner offline dłużej niż 14 dni GitHub usuwa i trzeba go zarejestrować ponownie (`setup.sh` po skasowaniu katalogu `actions-runner-N`).
