#!/usr/bin/env bash
# Installs self-hosted GitHub Actions runners for this repo on an Ubuntu 24.04 VM (README.md).
# Run as root on the VM: RUNNER_TOKEN=<registration token> ./setup.sh
# Idempotent: packages, the runner user and already registered runners are left as they are.
set -euo pipefail

: "${RUNNER_TOKEN:?RUNNER_TOKEN (repository registration token, valid 1 h) is required}"
readonly repo_url=${REPO_URL:-https://github.com/kwmacioszek/szczur-radar}
readonly count=${RUNNER_COUNT:-1}
readonly labels=${RUNNER_LABELS:-szczur-ci}
readonly user=${RUNNER_USER:-gha}

[ "$(id -u)" = 0 ] || { echo "run as root" >&2; exit 1; }
[ "$(uname -m)" = x86_64 ] || { echo "x86_64 only (Railway builds amd64)" >&2; exit 1; }

step() { printf '\n== %s\n' "$*"; }

step "packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q docker.io docker-buildx git jq curl unzip ca-certificates

step "docker daemon"
# Bounded container logs; persistent runners would otherwise grow them forever.
if [ ! -f /etc/docker/daemon.json ]; then
    printf '{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"}}\n' >/etc/docker/daemon.json
    systemctl restart docker
fi
systemctl enable --now docker

step "daily docker prune"
# Prunes only when the disk fills up. A prune by age would also drop the base images every day
# (`until` compares the image's creation time, not its pull time), and each run would pull them again.
# `until=24h` keeps images just built by a running job (built, then started in a later step).
cat >/etc/cron.daily/docker-prune <<'EOF'
#!/bin/sh
used=$(df --output=pcent / | tail -1 | tr -dc 0-9)
[ "$used" -ge 70 ] || exit 0
docker system prune -af --filter until=24h >/dev/null
docker volume prune -f >/dev/null
EOF
chmod 755 /etc/cron.daily/docker-prune

step "runner user $user"
# No sudo. Membership in `docker` is root-equivalent on this VM: the VM itself is the boundary.
id "$user" >/dev/null 2>&1 || useradd --create-home --shell /bin/bash "$user"
usermod -aG docker "$user"
home=$(getent passwd "$user" | cut -d: -f6)

step "runner release"
release=$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest)
version=$(jq -r '.tag_name | ltrimstr("v")' <<<"$release")
asset=actions-runner-linux-x64-$version.tar.gz
digest=$(jq -r --arg a "$asset" '.assets[] | select(.name == $a) | .digest // ""' <<<"$release")
tarball=$home/$asset
if [ ! -f "$tarball" ]; then
    curl -fsSL -o "$tarball" "https://github.com/actions/runner/releases/download/v$version/$asset"
fi
if [ -n "$digest" ]; then
    echo "${digest#sha256:}  $tarball" | sha256sum -c -
fi
echo "runner $version"

for i in $(seq 1 "$count"); do
    dir=$home/actions-runner-$i
    name=$(hostname)-$i
    step "runner $name"
    if [ -f "$dir/.runner" ]; then
        echo "already registered, skipped"
        continue
    fi
    install -d -o "$user" -g "$user" "$dir"
    tar -xzf "$tarball" -C "$dir"
    chown -R "$user:$user" "$dir"
    "$dir/bin/installdependencies.sh" >/dev/null
    (cd "$dir" && sudo -u "$user" ./config.sh --unattended --url "$repo_url" --token "$RUNNER_TOKEN" \
        --name "$name" --labels "$labels" --work _work --replace)
    (cd "$dir" && ./svc.sh install "$user" && ./svc.sh start)
done

step "done"
systemctl list-units --no-pager 'actions.runner.*'
