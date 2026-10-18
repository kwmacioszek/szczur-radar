#!/usr/bin/env bash
# The check after the apply of the CI job railway-config (S-14, safe-deploy-gate; see README.md). An
# apply redeploys the services it changes (gate.sh lists them in restarts.txt); on 2026-10-12 a "safe"
# apply restarted Postgres, which hung for 9 minutes before anyone looked. This waits until each one
# has deployed again and the API reaches its database (GET <web>/api/ready), and fails the job when one
# did not, so the problem shows in minutes on the run that caused it.
#
#   verify-apply.sh snapshot <restarts.txt> > deployments-before.json        (just before the apply)
#   verify-apply.sh check <restarts.txt> <deployments-before.json> <web-base-url> >> "$GITHUB_STEP_SUMMARY"
#
# restarts.txt: one Railway service name per line (desiredGraph resource names, written by gate.sh;
# `railway deployment list -s` takes them, a database included: it is a service in Railway). The
# snapshot records the ID of each one's newest deployment ({"<service>": "<id>" | null}, null when it
# has none) and exits 1 when Railway cannot tell after 3 attempts (a service that does not exist yet
# included); the check waits for a deployment with another ID, so no clock of the runner is compared
# with Railway's. Statuses of that new deployment (Railway's DeploymentStatus):
#   SUCCESS, SLEEPING                  deployed (SLEEPING: deployed, then put to sleep)
#   WAITING, QUEUED                    handed over to deploy-verify: Wait for CI holds it until this
#                                      workflow ends, so waiting here would deadlock
#   BUILDING, DEPLOYING, INITIALIZING  waited for
#   REMOVED, REMOVING                  a newer deployment replaced it: the newest is asked again
#   FAILED, CRASHED, SKIPPED           exit 1
#   NEEDS_APPROVAL, any other          exit 1 (a human must act, or a status this check does not know)
# No new deployment within the limit is exit 1 too ("the apply did not trigger a deploy").
# An empty restarts.txt checks only /api/ready.
#
# Variables: RAILWAY_PROJECT_ID and RAILWAY_ENVIRONMENT_ID (the environment by ID, never `railway
# link`; with a workspace token, RAILWAY_API_TOKEN); durations as in tools/pipeline-trigger:
#   DEPLOY_VERIFY_TIMEOUT      the limit for all the services together (default 15m)
#   DEPLOY_VERIFY_INTERVAL     between the questions to Railway (default 10s)
#   DEPLOY_GUARD_WAKE_TIMEOUT  how long /api/ready is retried until it answers 200 (default 3m)
# check: stdout is a markdown summary, stderr the log with GitHub `::notice::` / `::error::` lines.
# Exit codes: 0 every service deployed (or handed over) and /api/ready 200, 1 not, 2 a bad invocation.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)

log() { printf '%s\n' "$*" >&2; }
usage_error() { log "::error::post-apply check: $*"; exit 2; }
# shellcheck source=lib.sh
. "$here/lib.sh"

usage="usage: verify-apply.sh snapshot <restarts.txt> | check <restarts.txt> <deployments-before.json> <web-base-url>"
(( $# >= 2 )) || usage_error "$usage"
readonly mode=$1 restarts_file=$2
case $mode in
    snapshot) [[ $# == 2 ]] || usage_error "$usage" ;;
    check) [[ $# == 4 ]] || usage_error "$usage" ;;
    *) usage_error "$usage" ;;
esac
command -v jq >/dev/null || usage_error "jq is not installed"

[[ -f $restarts_file && -r $restarts_file ]] || usage_error "the restarts list $restarts_file is not a readable file"
services=()
while IFS= read -r line || [[ -n $line ]]; do
    line=${line//[[:space:]]/}
    [[ -n $line ]] || continue
    [[ $line =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || usage_error "'$line' in $restarts_file is not a service name"
    services+=("$line")
done <"$restarts_file"

if (( ${#services[@]} > 0 )); then
    for name in RAILWAY_PROJECT_ID RAILWAY_ENVIRONMENT_ID; do
        [[ -n ${!name:-} ]] || usage_error "$name is not set (the environment is selected by ID, never by railway link)"
    done
    command -v railway >/dev/null || usage_error "the Railway CLI is not installed"
fi

: "${DEPLOY_VERIFY_TIMEOUT:=15m}" "${DEPLOY_VERIFY_INTERVAL:=10s}" "${DEPLOY_GUARD_WAKE_TIMEOUT:=3m}"
duration_seconds DEPLOY_VERIFY_TIMEOUT; readonly verify_timeout=$REPLY
duration_seconds DEPLOY_VERIFY_INTERVAL; readonly verify_interval=$REPLY
duration_seconds DEPLOY_GUARD_WAKE_TIMEOUT; readonly wake_timeout=$REPLY

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
HTTP_BODY=$work/body HTTP_ERROR=$work/error

# The newest deployment of service $1: sets LATEST_ID and LATEST_STATUS (LATEST_ID "none": it has no
# deployment). Returns 1, with the reason in the log, when the CLI fails or prints no list.
latest() {
    local out
    if ! out=$(railway deployment list --json --limit 1 -s "$1" \
            -p "$RAILWAY_PROJECT_ID" -e "$RAILWAY_ENVIRONMENT_ID" 2>"$work/railway-error"); then
        log "post-apply check: railway deployment list -s $1 failed: $(head -n 3 "$work/railway-error" | tr '\n' ' ')"
        return 1
    fi
    if ! out=$(jq -r 'if type != "array" then error("not a list")
            elif length == 0 then "none -"
            elif (.[0].id | type) == "string" and (.[0].status | type) == "string" then "\(.[0].id) \(.[0].status)"
            else error("not a deployment") end' <<<"$out" 2>/dev/null); then
        log "post-apply check: railway deployment list -s $1 printed no list of deployments"
        return 1
    fi
    read -r LATEST_ID LATEST_STATUS <<<"$out"
}

if [[ $mode == snapshot ]]; then
    snapshot='{}'
    for service in "${services[@]}"; do
        id=""
        # A few attempts: an ID missed here would make the check take the old deployment for the new one.
        # A failure is not taken for "no deployment": the check would then take the deployment from before
        # the apply for the new one and pass without waiting. So the job stops before the apply.
        for attempt in 1 2 3; do
            if latest "$service"; then
                [[ $LATEST_ID == none ]] || id=$LATEST_ID
                break
            fi
            if (( attempt == 3 )); then
                log "::error::post-apply check: cannot read the deployments of $service before the apply (3 attempts); not applying, since the check could not tell its new deployment from the old one (a plan that creates a service: apply it by hand, see README.md)"
                exit 1
            fi
            sleep "$WAKE_INTERVAL"
        done
        snapshot=$(jq --arg service "$service" --arg id "$id" '.[$service] = (if $id == "" then null else $id end)' <<<"$snapshot")
        log "post-apply check: $service before the apply: ${id:-no deployment}"
    done
    printf '%s\n' "$snapshot"
    exit 0
fi

readonly before_file=$3
web_origin "$4"
readonly ready_url=$REPLY/api/ready
jq -e 'type == "object" and all(.[]; . == null or type == "string")' "$before_file" >/dev/null 2>&1 \
    || usage_error "$before_file is not a snapshot of verify-apply.sh (a JSON object of deployment IDs)"

rows=()
summary() {
    printf '### After the apply\n\n'
    (( ${#services[@]} > 0 )) || printf 'No service to redeploy.\n\n'
    if (( ${#rows[@]} > 0 )); then
        printf '| Service | Deployment | Result |\n| --- | --- | --- |\n'
        printf '%s\n' "${rows[@]}"
        printf '\n'
    fi
}
fail() {
    log "::error::post-apply check: $1"
    [[ -z ${2:-} ]] || rows+=("$2")
    summary
    exit 1
}

readonly deadline=$(( SECONDS + verify_timeout ))
for service in "${services[@]}"; do
    before=$(jq -r --arg service "$service" 'if has($service) then .[$service] // "" else error("missing") end' \
        "$before_file" 2>/dev/null) || usage_error "$before_file has no deployment of $service (no snapshot before the apply)"
    log "post-apply check: waiting for a new deployment of $service (before the apply: ${before:-none})"
    seen="" cli_failed=""
    while true; do
        cli_failed=1
        latest "$service" && cli_failed=""
        if [[ -z $cli_failed && $LATEST_ID != none && $LATEST_ID != "$before" ]]; then
            if [[ $seen != "$LATEST_ID $LATEST_STATUS" ]]; then
                log "post-apply check: $service deployment $LATEST_ID: $LATEST_STATUS"
                seen="$LATEST_ID $LATEST_STATUS"
            fi
            case $LATEST_STATUS in
                SUCCESS | SLEEPING)
                    rows+=("| \`$service\` | \`$LATEST_ID\` | deployed ($LATEST_STATUS) |")
                    break
                    ;;
                WAITING | QUEUED)
                    log "::notice::post-apply check: the deployment of $service waits ($LATEST_STATUS; Wait for CI holds it until this workflow ends): handed over to deploy-verify"
                    rows+=("| \`$service\` | \`$LATEST_ID\` | $LATEST_STATUS: handed over to deploy-verify |")
                    break
                    ;;
                BUILDING | DEPLOYING | INITIALIZING | REMOVED | REMOVING) ;;
                FAILED | CRASHED | SKIPPED)
                    fail "the deployment $LATEST_ID of $service ended $LATEST_STATUS after the apply (Railway: the service's deployments and logs)" \
                        "| \`$service\` | \`$LATEST_ID\` | **$LATEST_STATUS** |"
                    ;;
                NEEDS_APPROVAL)
                    fail "the deployment $LATEST_ID of $service needs an approval in Railway" \
                        "| \`$service\` | \`$LATEST_ID\` | **$LATEST_STATUS** |"
                    ;;
                *)
                    fail "the deployment $LATEST_ID of $service has a status this check does not know: $LATEST_STATUS" \
                        "| \`$service\` | \`$LATEST_ID\` | **$LATEST_STATUS** |"
                    ;;
            esac
        fi
        if (( SECONDS >= deadline )); then
            if [[ -z $seen ]]; then
                fail "the apply did not trigger a deploy of $service within $DEPLOY_VERIFY_TIMEOUT (its newest deployment is still ${before:-none}${cli_failed:+; the last railway deployment list failed})" \
                    "| \`$service\` | — | **no new deployment** |"
            fi
            fail "the deployment ${seen% *} of $service did not finish within $DEPLOY_VERIFY_TIMEOUT (last status ${seen#* })" \
                "| \`$service\` | \`${seen% *}\` | **${seen#* }, not finished** |"
        fi
        pause=$(( deadline - SECONDS ))
        (( pause < verify_interval )) || pause=$verify_interval
        sleep "$pause"
    done
done

# The API reaches its database (a restarted Postgres, or an API that came up without it).
log "post-apply check: asking $ready_url"
ready_since=$SECONDS
while true; do
    http_get "$ready_url"
    if [[ $REPLY == 200 ]]; then
        log "post-apply check: /api/ready answers 200"
        rows+=("| \`/api/ready\` | — | 200 |")
        break
    fi
    if (( SECONDS - ready_since >= wake_timeout )); then
        fail "/api/ready did not answer 200 within $DEPLOY_GUARD_WAKE_TIMEOUT ($(http_outcome)): the API does not reach its database or is down" \
            "| \`/api/ready\` | — | **$(http_outcome)** |"
    fi
    log "post-apply check: /api/ready: $(http_outcome); retrying for up to $DEPLOY_GUARD_WAKE_TIMEOUT"
    sleep "$WAKE_INTERVAL"
done
summary
