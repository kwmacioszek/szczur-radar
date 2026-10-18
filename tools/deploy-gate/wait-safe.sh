#!/usr/bin/env bash
# The deploy guard of the CI job railway-config (S-14, safe-deploy-gate; see README.md). A deploy
# interrupts a queued or running pipeline run, so before the plan, the apply and (through the
# workflow's conclusion, which Railway's Wait for CI waits for) the code deploy, it waits until the API
# says no run is queued or running: GET <web>/api/deploy-safety.
#
#   wait-safe.sh <web-base-url>
#
# Answers: 200 {"safe":true} → exit 0. 200 {"safe":false,"reason":…} → logged and asked again every
# DEPLOY_GUARD_INTERVAL, exit 1 after DEPLOY_GUARD_TIMEOUT or at DEPLOY_GUARD_DEADLINE. 401 or 404 (an
# API without the route: an older one answers 401, a route outside PUBLIC_ROUTES needs a session) →
# exit 0 with a warning. No connection or 5xx → retried for DEPLOY_GUARD_WAKE_TIMEOUT (a sleeping API
# wakes), then exit 0 with a warning: an API that cannot answer runs no pipeline, and the deploy may be
# its fix. A 200 without a readable verdict or any other answer → exit 1.
#
# Variables (durations as in tools/pipeline-trigger: 30s, 3m, 2h; a bare number is seconds):
#   DEPLOY_GUARD               off: skip the guard with a warning (repository variable); empty or on: guard
#   DEPLOY_GUARD_TIMEOUT       how long a busy pipeline is waited for (default 90m)
#   DEPLOY_GUARD_INTERVAL      between the questions while it is busy (default 60s)
#   DEPLOY_GUARD_WAKE_TIMEOUT  how long an API without an answer is retried (default 3m)
#   DEPLOY_GUARD_DEADLINE      optional: seconds since the epoch at which the guard gives up whatever
#                              the timeout (the workflow sets it to the run's creation + 120 min Wait for
#                              CI window − DEPLOY_VERIFY_TIMEOUT − 5 min); passed at the start → exit 1
# The log goes to stderr, with GitHub `::warning::` / `::error::` lines.
# Exit codes: 0 deploy (safe, or no decision), 1 do not deploy, 2 a bad invocation or variable.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)

log() { printf '%s\n' "$*" >&2; }
usage_error() { log "::error::deploy guard: $*"; exit 2; }
# shellcheck source=lib.sh
. "$here/lib.sh"

[[ $# == 1 ]] || usage_error "usage: wait-safe.sh <web-base-url>"
case ${DEPLOY_GUARD:-} in
    off)
        log "::warning::deploy guard: off (DEPLOY_GUARD=off): not checking whether a pipeline run is queued or running; a deploy now interrupts one"
        exit 0
        ;;
    "" | on) ;;
    *) usage_error "DEPLOY_GUARD must be off, on or empty (it is '$DEPLOY_GUARD')" ;;
esac
web_origin "$1"
readonly url=$REPLY/api/deploy-safety
: "${DEPLOY_GUARD_TIMEOUT:=90m}" "${DEPLOY_GUARD_INTERVAL:=60s}" "${DEPLOY_GUARD_WAKE_TIMEOUT:=3m}"
duration_seconds DEPLOY_GUARD_TIMEOUT; readonly timeout=$REPLY
duration_seconds DEPLOY_GUARD_INTERVAL; readonly interval=$REPLY
duration_seconds DEPLOY_GUARD_WAKE_TIMEOUT; readonly wake_timeout=$REPLY
deadline=""
if [[ -n ${DEPLOY_GUARD_DEADLINE:-} ]]; then
    [[ $DEPLOY_GUARD_DEADLINE =~ ^[1-9][0-9]*$ ]] \
        || usage_error "DEPLOY_GUARD_DEADLINE must be a time in seconds since the epoch (it is '$DEPLOY_GUARD_DEADLINE')"
    deadline=$DEPLOY_GUARD_DEADLINE
fi
readonly deadline
command -v curl >/dev/null || usage_error "curl is not installed"
command -v jq >/dev/null || usage_error "jq is not installed"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
HTTP_BODY=$work/body HTTP_ERROR=$work/error

now() { date +%s; }
# A time in seconds since the epoch, for the log (GNU date, else BSD date).
utc() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -r "$1" +%Y-%m-%dT%H:%M:%SZ; }

readonly start=$(now)
readonly give_up_at=$(( start + timeout ))
reason=""

# Past the deadline Railway skips the code deploy anyway (Wait for CI gives up 2 h after the push and
# marks the GitHub Deployment inactive, which no workflow sees): fail loudly instead of a green run
# that deploys nothing.
check_deadline() {
    if [[ -n $deadline ]] && (( $(now) >= deadline )); then
        log "::error::deploy guard: the Wait for CI window (2 h) is closing (deadline $(utc "$deadline")${reason:+, the pipeline still $reason}): Railway will not deploy the code of this commit. Push again or run \`railway redeploy\` once it is safe (runbook S-14, context/deployment/deploy-plan.md)"
        exit 1
    fi
}

log "deploy guard: asking $url whether a pipeline run is queued or running"
check_deadline
unreachable_since=""
while true; do
    http_get "$url"
    case $REPLY in
        200)
            unreachable_since=""
            verdict=$(jq -r 'if type == "object" and (.safe | type) == "boolean" then
                    if .safe then "safe"
                    elif (.reason | type) == "string" and .reason != "" then "busy \(.reason)"
                    else error("no reason") end
                else error("not a verdict") end' "$HTTP_BODY" 2>/dev/null) || {
                log "::error::deploy guard: $url answered 200 without a readable verdict: $(head -c 300 "$HTTP_BODY")"
                exit 1
            }
            if [[ $verdict == safe ]]; then
                log "deploy guard: safe, no pipeline run is queued or running${reason:+ (after waiting $(( $(now) - start ))s)}"
                exit 0
            fi
            if [[ -z $reason ]]; then
                log "deploy guard: not safe: ${verdict#busy }; asking again every $DEPLOY_GUARD_INTERVAL for up to $DEPLOY_GUARD_TIMEOUT${deadline:+ (at most until $(utc "$deadline"))}"
            else
                log "deploy guard: still ${verdict#busy } (waiting $(( $(now) - start ))s)"
            fi
            reason=${verdict#busy }
            check_deadline
            if (( $(now) >= give_up_at )); then
                log "::error::deploy guard: the pipeline is still $reason after $DEPLOY_GUARD_TIMEOUT; nothing applied or deployed. If no run is really progressing (a zombie run), restart the API, then push again or run \`railway redeploy\` (\"Re-run failed jobs\" usually comes after the 2 h Wait for CI window). DEPLOY_GUARD=off (repository variable) turns the guard off. Runbook S-14, context/deployment/deploy-plan.md"
                exit 1
            fi
            # Never past the timeout or the deadline: the next question is then the last one.
            pause=$(( give_up_at - $(now) ))
            if [[ -n $deadline ]] && (( deadline - $(now) < pause )); then
                pause=$(( deadline - $(now) ))
            fi
            (( pause < interval )) || pause=$interval
            (( pause > 0 )) || pause=0
            sleep "$pause"
            check_deadline
            ;;
        401 | 404)
            log "::warning::deploy guard: $url answered HTTP $REPLY: the API has no deploy-safety route yet (an older version; it answers 401 because the route needs a session there). Not waiting"
            exit 0
            ;;
        000 | 5??)
            if [[ -z $unreachable_since ]]; then
                unreachable_since=$(now)
                log "deploy guard: no answer yet ($(http_outcome)); retrying for up to $DEPLOY_GUARD_WAKE_TIMEOUT (the API may be waking up)"
            elif (( $(now) - unreachable_since >= wake_timeout )); then
                log "::warning::deploy guard: the API did not answer within $DEPLOY_GUARD_WAKE_TIMEOUT ($(http_outcome)): it is unavailable, so no pipeline run can be running. Not waiting"
                exit 0
            fi
            sleep "$WAKE_INTERVAL"
            check_deadline
            ;;
        *)
            log "::error::deploy guard: unexpected answer from $url: $(http_outcome): $(head -c 300 "$HTTP_BODY")"
            exit 1
            ;;
    esac
done
