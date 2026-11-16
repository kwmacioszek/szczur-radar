#!/usr/bin/env bash
# Runs the visual tests (F-06, playwright.visual.config.ts) in Playwright's pinned image on linux/amd64 — the image and
# architecture of CI's web-e2e job, the only place the baselines are rendered (under emulation on an arm64 host).
# Arguments go to `playwright test`: `npm run visual:update` passes --update-snapshots=changed.
# The module is mounted at /work; the container's node_modules (linux binaries) live in a named volume over
# /work/node_modules, so the host's node_modules stay as they are. The volume's name comes from the module's path:
# two worktrees never share one install.
set -euo pipefail

module_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

version="$(node -p "require('$module_dir/package.json').devDependencies['@playwright/test'] ?? ''")"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "visual-docker: @playwright/test w package.json musi mieć dokładną wersję (bez ^ i ~), jest: '$version'" >&2
  exit 1
fi
image="mcr.microsoft.com/playwright:v$version-noble"

if ! docker info >/dev/null 2>&1; then
  echo "visual-docker: Docker nie działa (docker info się nie udał). Uruchom Dockera (np. OrbStack) i spróbuj ponownie." >&2
  exit 1
fi

path_hash="$(printf '%s' "$module_dir" | shasum -a 256 | cut -c1-12)"
volume="szczur-radar-web-visual-node-modules-$path_hash"
owner="$(stat -c '%u:%g' "$module_dir" 2>/dev/null || stat -f '%u:%g' "$module_dir")"

# The test's exit code is kept; the chown runs after a red test too and gives the files the container wrote in the
# mount (baselines, reports) back to the module's owner.
docker run --platform linux/amd64 --rm --init --ipc=host \
  -v "$module_dir:/work" \
  -v "$volume:/work/node_modules" \
  -w /work \
  -e OWNER="$owner" \
  "$image" \
  bash -c 'status=0
npm ci --no-audit --no-fund && npm run visual -- "$@" || status=$?
chown -R "$OWNER" /work/visual /work/test-results-visual /work/playwright-report-visual /work/dist 2>/dev/null || true
exit "$status"' visual-docker "$@"
