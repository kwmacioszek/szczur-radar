#!/usr/bin/env bash
# The check after the apply of the CI job railway-config (S-14, safe-deploy-gate; S-21,
# deploy-gate-no-deploy-changes; see README.md). gate.sh lists in restarts.txt the services an apply may
# redeploy; on 2026-10-12 a "safe" apply restarted Postgres, which hung for 9 minutes before anyone
# looked. This waits until each deployment the apply started has deployed and the API reaches its
# database (GET <web>/api/ready), and fails the job when one did not, so the problem shows in minutes on
# the run that caused it. Not every change deploys (source.checkSuites null → true did not, 2026-10-19),
# and the plan does not tell which do, so a service the apply did not deploy passes with a notice.
#
#   verify-apply.sh snapshot <restarts.txt> > deployments-before.json        (just before the apply)
#   verify-apply.sh check <restarts.txt> <deployments-before.json> <web-base-url> >> "$GITHUB_STEP_SUMMARY"
#
# restarts.txt: one Railway service name per line (desiredGraph resource names, written by gate.sh;
# `railway deployment list -s` takes them, a database included: it is a service in Railway). The
# snapshot records the ID of each one's newest deployment, whatever started it ({"<service>": "<id>" |
# null}, null when it has none), and exits 1 when Railway cannot tell after 3 attempts (a service that
# does not exist yet included). That ID is the boundary: the check reads the newest 50 deployments, and
# those above the boundary in that list came after the snapshot, so no clock of the runner is compared
# with Railway's. Both rest on Railway's API listing deployments newest first (CLI 5.67.1 does not sort
# them and its query has no orderBy). The apply's deployment is the newest of those whose
# meta.patchId starts with "iac-change-set/": Railway marks so every deployment `railway config apply`
# starts (and sets ignoreWatchPatterns, so watch paths never skip one). Not meta.reason or
# meta.commitHash: an apply's deployment has reason "deploy" and the commitHash of the branch's tip at
# the apply, as a push's has, so a push meanwhile (its deployment SKIPPED by watch paths, WAITING or
# SUCCESS) would be taken for the apply's. The statuses of the apply's deployment (Railway's
# DeploymentStatus):
#   SUCCESS, SLEEPING                  deployed (SLEEPING: deployed, then put to sleep)
#   WAITING, QUEUED                    handed over to the monitor szczur-radar-monitor: Wait for CI holds
#                                      it until this workflow ends, so waiting here would deadlock
#   BUILDING, DEPLOYING, INITIALIZING  waited for
#   REMOVED, REMOVING                  replaced: the newest deployment above it that is not SKIPPED (of
#                                      any origin; a SKIPPED one replaces nothing) is judged instead,
#                                      and waited for while there is none
#   FAILED, CRASHED, SKIPPED           exit 1 (SKIPPED with Railway's meta.skippedReason)
#   NEEDS_APPROVAL, any other          exit 1 (a human must act, or a status this check does not know)
# No deployment of the apply in a list read after DEPLOY_VERIFY_DEPLOY_WINDOW is "no deploy": a
# ::notice:: and the next service. The boundary missing from the newest 50 is exit 1 (the apply's
# deployment cannot be told), and so is a deployment of the apply not finished within the limit.
# An empty restarts.txt checks only /api/ready.
#
# Variables: RAILWAY_PROJECT_ID and RAILWAY_ENVIRONMENT_ID (the environment by ID, never `railway
# link`; with a workspace token, RAILWAY_API_TOKEN); durations as in tools/pipeline-trigger:
#   DEPLOY_VERIFY_TIMEOUT        the limit for all the services together (default 15m)
#   DEPLOY_VERIFY_DEPLOY_WINDOW  how long after the start of the check the apply's deployment may still
#                                appear (default 2m; one window for all the services: the apply starts
#                                its deployments within seconds; at most DEPLOY_VERIFY_TIMEOUT)
#   DEPLOY_VERIFY_INTERVAL       between the questions to Railway (default 10s)
#   DEPLOY_GUARD_WAKE_TIMEOUT    how long /api/ready is retried until it answers 200 (default 3m)
# check: stdout is a markdown summary, stderr the log with GitHub `::notice::` / `::error::` lines.
# Exit codes: 0 every deployment of the apply deployed (or handed over, or there was none) and
# /api/ready 200, 1 not, 2 a bad invocation.
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

: "${DEPLOY_VERIFY_TIMEOUT:=15m}" "${DEPLOY_VERIFY_DEPLOY_WINDOW:=2m}" "${DEPLOY_VERIFY_INTERVAL:=10s}" \
    "${DEPLOY_GUARD_WAKE_TIMEOUT:=3m}"
duration_seconds DEPLOY_VERIFY_TIMEOUT; readonly verify_timeout=$REPLY
duration_seconds DEPLOY_VERIFY_DEPLOY_WINDOW; readonly deploy_window=$REPLY
duration_seconds DEPLOY_VERIFY_INTERVAL; readonly verify_interval=$REPLY
duration_seconds DEPLOY_GUARD_WAKE_TIMEOUT; readonly wake_timeout=$REPLY
(( deploy_window <= verify_timeout )) \
    || usage_error "DEPLOY_VERIFY_DEPLOY_WINDOW ($DEPLOY_VERIFY_DEPLOY_WINDOW) must not be longer than DEPLOY_VERIFY_TIMEOUT ($DEPLOY_VERIFY_TIMEOUT)"

# How many of a service's newest deployments the check reads: the apply's and any a push started
# meanwhile, down to the boundary.
readonly list_limit=50

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
HTTP_BODY=$work/body HTTP_ERROR=$work/error

# `railway deployment list --json` of service $1, at most $2 deployments, newest first: sets REPLY to
# what the CLI printed. Returns 1, with the reason in the log, when the CLI fails.
list_deployments() {
    if ! REPLY=$(railway deployment list --json --limit "$2" -s "$1" \
            -p "$RAILWAY_PROJECT_ID" -e "$RAILWAY_ENVIRONMENT_ID" 2>"$work/railway-error"); then
        log "post-apply check: railway deployment list -s $1 failed: $(head -n 3 "$work/railway-error" | tr '\n' ' ')"
        return 1
    fi
}

# The newest deployment of service $1: sets LATEST_ID and LATEST_STATUS (LATEST_ID "none": it has no
# deployment). Returns 1, with the reason in the log, when the CLI fails or prints no list (an empty
# answer too: `jq -e`).
latest() {
    local out
    list_deployments "$1" 1 || return 1
    if ! out=$(jq -er 'if type != "array" then error("not a list")
            elif length == 0 then "none -"
            elif (.[0].id | type) == "string" and (.[0].status | type) == "string" then "\(.[0].id) \(.[0].status)"
            else error("not a deployment") end' <<<"$REPLY" 2>/dev/null); then
        log "post-apply check: railway deployment list -s $1 printed no list of deployments"
        return 1
    fi
    read -r LATEST_ID LATEST_STATUS <<<"$out"
}

# Of a list of deployments (newest first) and the boundary $before (the ID of the newest one before the
# apply; "" for none), one line "<found> <apply-id> <apply-status> <id> <status> <skipped-reason>":
#   lost   the boundary is not in the list ("-" for the rest);
#   none   no deployment above it has a meta.patchId starting with "iac-change-set/" ("-" for the rest);
#   apply  the newest that has: its ID and status, then the deployment to judge (the same one, or when
#          it is REMOVED or REMOVING, the newest above it that is not SKIPPED, if there is one) and
#          that one's meta.skippedReason (may be empty).
# Fails on anything but a list of deployments with a string id and status.
read -r -d '' apply_deployment_jq <<'JQ' || true
def deployment: type == "object" and (.id | type) == "string" and (.status | type) == "string";
def meta($key): .meta | if type == "object" then .[$key] else null end;
def by_the_apply: meta("patchId") | type == "string" and startswith("iac-change-set/");
if type != "array" or (all(.[]; deployment) | not) then error("not a list of deployments")
else
  (if $before == "" then length else map(.id) | index([$before]) end) as $boundary
  | if $boundary == null then "lost - - - -"
    else .[:$boundary] as $after
    | ([range(0; $after | length) | select($after[.] | by_the_apply)] | first) as $i
    | if $i == null then "none - - - -"
      else $after[$i] as $apply
      | (if $apply.status == "REMOVED" or $apply.status == "REMOVING"
         then ([$after[:$i][] | select(.status != "SKIPPED")] | first) // $apply
         else $apply end) as $judged
      | ["apply", $apply.id, $apply.status, $judged.id, $judged.status,
         ($judged | meta("skippedReason") | if type == "string" then gsub("\\s+"; " ") else "" end)]
      | join(" ")
      end
    end
end
JQ

# The apply's deployment of service $1, whose newest deployment before the apply was $2 ("" for none),
# as apply_deployment_jq tells it: sets FOUND (lost, none or apply), APPLY_ID and APPLY_STATUS, JUDGED_ID
# and JUDGED_STATUS, SKIPPED_REASON. Returns 1, with the reason in the log, when the CLI fails or prints
# no list (an empty answer too: `jq -e`).
apply_deployment() {
    local out
    list_deployments "$1" "$list_limit" || return 1
    if ! out=$(jq -er --arg before "$2" "$apply_deployment_jq" <<<"$REPLY" 2>/dev/null); then
        log "post-apply check: railway deployment list -s $1 printed no list of deployments"
        return 1
    fi
    read -r FOUND APPLY_ID APPLY_STATUS JUDGED_ID JUDGED_STATUS SKIPPED_REASON <<<"$out"
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

# One window and one limit for all the services, from the start of the check (just after the apply):
# the apply starts its deployments within seconds, so a service checked after a long Postgres deploy
# gets its verdict from one list. SECONDS counts whole seconds, so the window ends a second later to
# last at least DEPLOY_VERIFY_DEPLOY_WINDOW, but never after the limit.
readonly deadline=$(( SECONDS + verify_timeout ))
window_end=$(( SECONDS + deploy_window + 1 ))
(( window_end <= deadline )) || window_end=$deadline
readonly window_end
for service in "${services[@]}"; do
    before=$(jq -r --arg service "$service" 'if has($service) then .[$service] // "" else error("missing") end' \
        "$before_file" 2>/dev/null) || usage_error "$before_file has no deployment of $service (no snapshot before the apply)"
    log "post-apply check: waiting for the apply's deployment of $service (the newest before the apply: ${before:-none})"
    seen="" logged="" cli_failed=""
    while true; do
        asked_at=$SECONDS
        cli_failed=1
        apply_deployment "$service" "$before" && cli_failed=""
        if [[ -z $cli_failed && $FOUND == lost ]]; then
            fail "cannot tell the apply's deployment of $service: its deployment before the apply $before is not among the newest $list_limit" \
                "| \`$service\` | — | **cannot tell** (\`$before\` not among the newest $list_limit) |"
        fi
        # "No deploy" only from a list read after the window, and never once a deployment of the apply
        # was seen.
        if [[ -z $cli_failed && $FOUND == none && -z $seen ]] && (( asked_at >= window_end )); then
            log "::notice::post-apply check: the apply triggered no deploy of $service within $DEPLOY_VERIFY_DEPLOY_WINDOW"
            rows+=("| \`$service\` | — | no deploy (none within $DEPLOY_VERIFY_DEPLOY_WINDOW of the apply) |")
            break
        fi
        if [[ -z $cli_failed && $FOUND == apply ]]; then
            if [[ $logged != "$APPLY_ID $APPLY_STATUS $JUDGED_ID $JUDGED_STATUS" ]]; then
                if [[ $JUDGED_ID == "$APPLY_ID" ]]; then
                    log "post-apply check: $service deployment $APPLY_ID: $APPLY_STATUS"
                else
                    log "post-apply check: $service deployment $APPLY_ID: $APPLY_STATUS, replaced by $JUDGED_ID: $JUDGED_STATUS"
                fi
                logged="$APPLY_ID $APPLY_STATUS $JUDGED_ID $JUDGED_STATUS"
            fi
            seen="$JUDGED_ID $JUDGED_STATUS"
            case $JUDGED_STATUS in
                SUCCESS | SLEEPING)
                    rows+=("| \`$service\` | \`$JUDGED_ID\` | deployed ($JUDGED_STATUS) |")
                    break
                    ;;
                WAITING | QUEUED)
                    log "::notice::post-apply check: the deployment of $service waits ($JUDGED_STATUS; Wait for CI holds it until this workflow ends): handed over to the monitor szczur-radar-monitor"
                    rows+=("| \`$service\` | \`$JUDGED_ID\` | $JUDGED_STATUS: handed over to the monitor szczur-radar-monitor |")
                    break
                    ;;
                BUILDING | DEPLOYING | INITIALIZING | REMOVED | REMOVING) ;;
                FAILED | CRASHED)
                    fail "the deployment $JUDGED_ID of $service ended $JUDGED_STATUS after the apply (Railway: the service's deployments and logs)" \
                        "| \`$service\` | \`$JUDGED_ID\` | **$JUDGED_STATUS** |"
                    ;;
                SKIPPED)
                    fail "the deployment $JUDGED_ID of $service ended SKIPPED after the apply${SKIPPED_REASON:+ (Railway: \"$SKIPPED_REASON\")}" \
                        "| \`$service\` | \`$JUDGED_ID\` | **SKIPPED**${SKIPPED_REASON:+ (${SKIPPED_REASON//|/\\|})} |"
                    ;;
                NEEDS_APPROVAL)
                    fail "the deployment $JUDGED_ID of $service needs an approval in Railway" \
                        "| \`$service\` | \`$JUDGED_ID\` | **$JUDGED_STATUS** |"
                    ;;
                *)
                    fail "the deployment $JUDGED_ID of $service has a status this check does not know: $JUDGED_STATUS" \
                        "| \`$service\` | \`$JUDGED_ID\` | **$JUDGED_STATUS** |"
                    ;;
            esac
        fi
        if (( SECONDS >= deadline )); then
            if [[ -z $seen ]]; then
                reason="no list of deployments read after the window of $DEPLOY_VERIFY_DEPLOY_WINDOW"
                [[ -z $cli_failed ]] || reason="the last railway deployment list failed"
                fail "cannot tell whether the apply deployed $service within $DEPLOY_VERIFY_TIMEOUT ($reason)" \
                    "| \`$service\` | — | **unknown** ($reason) |"
            fi
            fail "the deployment ${seen% *} of $service did not finish within $DEPLOY_VERIFY_TIMEOUT (last status ${seen#* })" \
                "| \`$service\` | \`${seen% *}\` | **${seen#* }, not finished** |"
        fi
        pause=$(( deadline - SECONDS ))
        (( pause < verify_interval )) || pause=$verify_interval
        (( pause > 0 )) || pause=0
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
