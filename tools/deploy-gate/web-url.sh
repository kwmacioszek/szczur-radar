#!/usr/bin/env bash
# The public web address of a Railway environment of Szczur Radar (S-14, safe-deploy-gate; see
# README.md): the one mapping from an environment name to the origin the deploy gate's checks call
# (/api/deploy-safety, /api/ready). Only szczur-radar-web has a public domain; it proxies /api/* to the API.
#
#   web-url.sh <environment-name>
#
#   production             → https://szczur-radar-web-production.up.railway.app
#   szczur-radar-pr-<N>    → https://szczur-radar-web-szczur-radar-pr-<N>.up.railway.app (a PR env)
#
# stdout: the address, without a trailing slash. Exit codes: 0 printed, 2 an unknown environment name
# or a bad invocation (nothing printed).
set -euo pipefail

usage_error() { printf '::error::web-url: %s\n' "$*" >&2; exit 2; }

[[ $# == 1 ]] || usage_error "usage: web-url.sh <environment-name>"
case $1 in
    production)
        echo "https://szczur-radar-web-production.up.railway.app"
        ;;
    *)
        # Railway names a PR env after the project and the PR number (railway-config, TARGET_ENV).
        [[ $1 =~ ^szczur-radar-pr-[1-9][0-9]*$ ]] \
            || usage_error "unknown environment '$1' (expected production or szczur-radar-pr-<N>)"
        echo "https://szczur-radar-web-$1.up.railway.app"
        ;;
esac
