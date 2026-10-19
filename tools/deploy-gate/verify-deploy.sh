#!/usr/bin/env bash
# The check after a code deploy (S-14, safe-deploy-gate; see README.md), run by the workflow
# deploy-verify (.github/workflows/deploy-verify.yml) on each deployment_status event Railway sends.
# Railway deploys the code only after the CI workflow ends (Wait for CI), so no step of ci.yml can see
# it: this asks the deployed environment's web origin for GET /api/ready (the API reaches its database)
# and GET /healthz (the web serves), and leaves a trace the operator gets (an issue) when the deploy
# failed or a probe did.
#
#   verify-deploy.sh >> "$GITHUB_STEP_SUMMARY"
#
# Input (variables):
#   DEPLOY_STATE        the deployment status: success, failure or error
#   DEPLOY_ENVIRONMENT  the GitHub Deployment's environment, "szczur-radar / <Railway environment>"
#                       (Railway's target_url and environment_url point at its dashboard, not the web:
#                       not used)
#   DEPLOY_SHA          the deployed commit
#   GH_TOKEN, GITHUB_REPOSITORY  for gh (issues: write); GITHUB_SERVER_URL and GITHUB_RUN_ID give the
#                       run link in the issue
#   DEPLOY_GUARD_WAKE_TIMEOUT  how long the probes are retried after a success (default 3m; durations
#                       as in tools/pipeline-trigger: 30s, 3m, 2h)
#   DEPLOY_WEB_URL      optional, for tests and manual use only (the workflow never sets it): the web
#                       origin to probe instead of web-url.sh's (the environment name is still resolved)
#
# The web origin comes from the environment name after "szczur-radar / " (web-url.sh); another
# project or an unknown name is logged and exits 0. Railway publishes one GitHub Deployment per
# environment and commit, with several statuses without a service name (a failure may follow a
# success), so one deploy gives several runs, seconds apart; the alarm keeps one open issue per
# environment (runs within about 2 s of each other may still open two: no concurrency group, see
# deploy-verify.yml, and the issue list lags, see below):
#   success: both probes, retried while the API wakes; both 200 → exit 0, no gh call;
#   failure or error (each probe asked once, for the record), or a probe without 200: the label
#     deploy-health is created if missing (gh label create --force), then the oldest open issue with
#     that label naming the environment in its title gets a comment; without one a new issue
#     "Niezdrowe wdrożenie: <environment>" is opened (commit, state, probes, run link). Nothing closes
#     it: a human does, after the fix.
# The log goes to stderr (GitHub `::error::` / `::notice::` lines), a markdown summary to stdout.
# Exit codes: 0 checked (healthy, alarmed, or nothing to check), 1 gh failed (the alarm did not get
# through), 2 a bad invocation or variable. Never 1 for an unhealthy deploy: a red run of this workflow
# would make Wait for CI skip a redeploy of the same commit, which may be the fix.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)

log() { printf '%s\n' "$*" >&2; }
usage_error() { log "::error::deploy check: $*"; exit 2; }
# shellcheck source=lib.sh
. "$here/lib.sh"

# Railway names the GitHub Deployment environments "<project> / <environment>".
readonly environment_prefix="szczur-radar / "
readonly label=deploy-health
readonly title_prefix="Niezdrowe wdrożenie: "

[[ $# == 0 ]] || usage_error "usage: verify-deploy.sh (input in DEPLOY_STATE, DEPLOY_ENVIRONMENT, DEPLOY_SHA, GH_TOKEN, GITHUB_REPOSITORY)"
case ${DEPLOY_STATE:-} in
    success | failure | error) ;;
    *) usage_error "DEPLOY_STATE must be success, failure or error (it is '${DEPLOY_STATE:-}')" ;;
esac
[[ -n ${DEPLOY_ENVIRONMENT:-} ]] || usage_error "DEPLOY_ENVIRONMENT is not set"
[[ ${DEPLOY_SHA:-} =~ ^[0-9a-f]{7,40}$ ]] || usage_error "DEPLOY_SHA must be a commit SHA (it is '${DEPLOY_SHA:-}')"
[[ ${GITHUB_REPOSITORY:-} =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] \
    || usage_error "GITHUB_REPOSITORY must be <owner>/<repository> (it is '${GITHUB_REPOSITORY:-}')"
[[ -n ${GH_TOKEN:-} ]] || usage_error "GH_TOKEN is not set (gh opens the issue with it)"
: "${DEPLOY_GUARD_WAKE_TIMEOUT:=3m}"
duration_seconds DEPLOY_GUARD_WAKE_TIMEOUT; readonly wake_timeout=$REPLY
command -v curl >/dev/null || usage_error "curl is not installed"
command -v jq >/dev/null || usage_error "jq is not installed"
command -v gh >/dev/null || usage_error "gh is not installed"

if [[ $DEPLOY_ENVIRONMENT != "$environment_prefix"* ]]; then
    log "deploy check: '$DEPLOY_ENVIRONMENT' is not an environment of szczur-radar; nothing to check"
    exit 0
fi
readonly environment=${DEPLOY_ENVIRONMENT#"$environment_prefix"}
if ! web_url=$("$here/web-url.sh" "$environment" 2>/dev/null); then
    log "deploy check: no web address is known for the environment '$environment' (web-url.sh); nothing to check"
    exit 0
fi
if [[ -n ${DEPLOY_WEB_URL:-} ]]; then
    web_origin "$DEPLOY_WEB_URL"
    web_url=$REPLY
    log "deploy check: probing DEPLOY_WEB_URL=$web_url instead of the address of $environment"
fi
readonly web_url

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
HTTP_BODY=$work/body HTTP_ERROR=$work/error

if [[ -n ${GITHUB_RUN_ID:-} ]]; then
    run_link="${GITHUB_SERVER_URL:-https://github.com}/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
else
    run_link="(none: not run by GitHub Actions)"
fi
readonly run_link

# After a success the probes share one wake-up window; after a failure each is asked once.
if [[ $DEPLOY_STATE == success ]]; then
    probe_until=$(( SECONDS + wake_timeout ))
    log "deploy check: $DEPLOY_ENVIRONMENT deployed $DEPLOY_SHA; probing $web_url (retried for up to $DEPLOY_GUARD_WAKE_TIMEOUT)"
else
    probe_until=$SECONDS
    log "::error::deploy check: Railway reports the deployment of $DEPLOY_SHA to $DEPLOY_ENVIRONMENT as $DEPLOY_STATE; probing $web_url once for the record"
fi
readonly probe_until

# GET <web>$1 until it answers 200 or the window closes (at least once): returns 0 on 200, sets RESULT
# to its last outcome.
probe() {
    while true; do
        http_get "$web_url$1"
        if [[ $REPLY == 200 ]]; then
            RESULT="HTTP 200"
            log "deploy check: $1: $RESULT"
            return 0
        fi
        RESULT=$(http_outcome)
        if (( SECONDS >= probe_until )); then
            log "deploy check: $1: $RESULT"
            return 1
        fi
        log "deploy check: $1: $RESULT; retrying (the service may be waking up)"
        sleep "$WAKE_INTERVAL"
    done
}

healthy=1
rows=() outcomes=()
for path in /api/ready /healthz; do
    probe "$path" || healthy=""
    rows+=("| \`$path\` | $RESULT |")
    outcomes+=("$path $RESULT")
done
[[ $DEPLOY_STATE == success ]] || healthy=""

# The issue body, the comment and the step summary: the same report.
{
    if [[ -n $healthy ]]; then
        printf '### Deploy check: `%s`: healthy\n\n' "$environment"
    else
        printf '### Niezdrowe wdrożenie: `%s`\n\n' "$environment"
    fi
    printf '| | |\n| --- | --- |\n'
    printf '| Środowisko | `%s` |\n' "$DEPLOY_ENVIRONMENT"
    printf '| Commit | `%s` |\n' "$DEPLOY_SHA"
    printf '| Stan deployu (Railway) | `%s` |\n' "$DEPLOY_STATE"
    printf '| Web | %s |\n' "$web_url"
    printf '| Run | %s |\n\n' "$run_link"
    printf '| Sonda | Wynik |\n| --- | --- |\n'
    printf '%s\n' "${rows[@]}"
    if [[ -z $healthy ]]; then
        printf '\ndeploy-verify nie zamyka tego issue: zamknij je po naprawie. Runbook S-14: `context/deployment/deploy-plan.md`.\n'
    fi
} >"$work/report.md"

if [[ -n $healthy ]]; then
    log "deploy check: $DEPLOY_ENVIRONMENT is healthy (/api/ready and /healthz 200)"
    cat "$work/report.md"
    exit 0
fi

# The alarm did not get through: the one case that turns this run red.
gh_failed() {
    log "::error::deploy check: $1 failed, the unhealthy deployment of $DEPLOY_ENVIRONMENT is NOT reported in an issue: $(head -n 3 "$work/gh-error" | tr '\n' ' ')"
    cat "$work/report.md"
    exit 1
}

log "::error::deploy check: $DEPLOY_ENVIRONMENT is unhealthy after the deployment of $DEPLOY_SHA (state $DEPLOY_STATE; $(IFS=,; printf '%s' "${outcomes[*]}"))"
gh label create "$label" --repo "$GITHUB_REPOSITORY" --force --color B60205 \
    --description "Unhealthy deployment reported by deploy-verify (S-14)" >/dev/null 2>"$work/gh-error" \
    || gh_failed "gh label create $label"
# The REST list of issues, not `gh issue list --label`, which goes through GitHub's search index and
# lags further behind. The REST list lags too: a new issue shows up about 2 s after it is created
# (measured 2026-10-19), so a run within that window of another still opens a second issue (#86, #87).
# The list also returns pull requests.
issues=$(gh api "repos/$GITHUB_REPOSITORY/issues?labels=$label&state=open&per_page=100" \
    2>"$work/gh-error") || gh_failed "gh api repos/$GITHUB_REPOSITORY/issues"
# The environment as a whole word of the title: szczur-radar-pr-8 must not match szczur-radar-pr-81
# (web-url.sh admits only [a-z0-9-] in the name, so it needs no regex escaping).
number=$(jq -r --arg name "$environment" '
        if type != "array" then error("not a list") else
            [.[] | select(type == "object" and (has("pull_request") | not))
                | select((.title | type) == "string"
                and (.title | test("(^|[^A-Za-z0-9-])" + $name + "($|[^A-Za-z0-9-])")))
                | .number] | sort | (.[0] // "") | tostring
        end' <<<"$issues" 2>"$work/gh-error") || gh_failed "reading the list of open issues"
if [[ -n $number ]]; then
    url=$(gh issue comment "$number" --repo "$GITHUB_REPOSITORY" --body-file "$work/report.md" 2>"$work/gh-error") \
        || gh_failed "gh issue comment $number"
    log "::notice::deploy check: commented on the open issue #$number: $url"
else
    url=$(gh issue create --repo "$GITHUB_REPOSITORY" --title "$title_prefix$environment" --label "$label" \
        --body-file "$work/report.md" 2>"$work/gh-error") || gh_failed "gh issue create"
    log "::notice::deploy check: opened $url"
fi
cat "$work/report.md"
