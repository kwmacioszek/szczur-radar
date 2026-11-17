#!/usr/bin/env bash
# Runs the visual tests (F-06, playwright.visual.config.ts) in Playwright's pinned image on linux/amd64 — the image and
# architecture of CI's web-e2e job, the only place the baselines are rendered (under emulation on an arm64 host).
# Arguments go to `playwright test`: `npm run visual:update` passes --update-snapshots=changed.
# The module is mounted at /work; the container's node_modules (linux binaries) live in a named volume over
# /work/node_modules, so the host's node_modules stay as they are. The volume's name comes from the module's path:
# two worktrees never share one install. npm's download cache is one volume for all of them (`npm ci` empties
# node_modules on every run, the cache keeps it from downloading every package again).
set -euo pipefail

module_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

version="$(node -p 'require(process.argv[1]).devDependencies["@playwright/test"] ?? ""' "$module_dir/package.json")"
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
# mount (baselines, reports) back to the module's owner. Without a TTY, Ctrl-C reaches tini (--init) only, which
# signals its child alone, and the waiting bash defers it: TINI_KILL_PROCESS_GROUP=1 signals the whole group, so npm
# and Playwright stop, and the trap still gives the files back (exit 130).
docker run --platform linux/amd64 --rm --init --ipc=host \
  -e TINI_KILL_PROCESS_GROUP=1 \
  -v "$module_dir:/work" \
  -v "$volume:/work/node_modules" \
  -v szczur-radar-web-visual-npm-cache:/root/.npm \
  -w /work \
  -e OWNER="$owner" \
  "$image" \
  bash -c 'status=0
give_back() { chown -R "$OWNER" /work/visual /work/test-results-visual /work/playwright-report-visual /work/dist 2>/dev/null || true; }
trap "give_back; exit 130" INT TERM
npm ci --no-audit --no-fund && npm run visual -- "$@" || status=$?
give_back
exit "$status"' visual-docker "$@"
