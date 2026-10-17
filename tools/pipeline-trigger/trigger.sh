#!/usr/bin/env bash
# One run of the daily pipeline trigger (S-05, daily-scheduled-run-freshness; see README.md).
# Wakes the API over Railway's private network, starts the daily run (POST /internal/pipeline-runs/daily
# with the trigger token), waits until every run it created has ended and exits with their result:
# 0 when each one is succeeded or partial, 1 when one failed or was interrupted or the trigger itself
# failed, 2 on a configuration error. Railway marks a cron run that exits non-zero as crashed and
# notifies it (needs the account's custom notification rule "deployment crashed", all environments).
# While a run is running it logs a heartbeat with its step and progress (S-06,
# pipeline-run-history-dashboard), from the answers it polls anyway.
# Never `set -x`: PIPELINE_TRIGGER_TOKEN is the API's shared secret and is never printed.
set -euo pipefail

log() { printf '%s trigger: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() { log "FAILED: $*"; exit 1; }
config_error() { log "FAILED: $*"; exit 2; }

# The daily run is production's: Railway creates this cron in every environment (a PR env is a copy
# of production), but a PR env needs no nightly run, and one without a token on its API would e-mail a
# failure every night. TRIGGER_IN_ANY_ENVIRONMENT=true runs it anyway (to try this tool in a PR env).
# Outside Railway (test.sh, by hand) the variable is unset and it runs.
if [[ -n ${RAILWAY_ENVIRONMENT_NAME:-} && $RAILWAY_ENVIRONMENT_NAME != production
        && ${TRIGGER_IN_ANY_ENVIRONMENT:-} != true ]]; then
    log "skipped: the daily run is started only in production (this is $RAILWAY_ENVIRONMENT_NAME)"
    exit 0
fi

# Durations: a positive whole number with an optional unit s, m or h (a bare number is seconds).
: "${TRIGGER_TIMEOUT:=3h}" "${TRIGGER_WAKE_TIMEOUT:=3m}" "${TRIGGER_BUSY_TIMEOUT:=60m}"
: "${TRIGGER_BUSY_INTERVAL:=60s}" "${TRIGGER_POLL_INTERVAL:=30s}" "${TRIGGER_RUN_TIMEOUT:=90m}"
: "${TRIGGER_REQUEST_TIMEOUT:=60s}" "${TRIGGER_HEARTBEAT_INTERVAL:=5m}"

# Sets REPLY to the seconds of the duration in the variable named $1.
seconds() {
    [[ ${!1} =~ ^([1-9][0-9]*)([smh]?)$ ]] || config_error "$1 must be a duration such as 30s, 3m or 2h"
    case ${BASH_REMATCH[2]} in
        h) REPLY=$(( BASH_REMATCH[1] * 3600 )) ;;
        m) REPLY=$(( BASH_REMATCH[1] * 60 )) ;;
        *) REPLY=${BASH_REMATCH[1]} ;;
    esac
}

# A run that hangs would never fail, and Railway skips every later run while one is still active: the
# whole run gets a hard time limit (above the wake, busy and run limits together) and fails when it is
# exceeded.
if [[ -z ${TRIGGER_TIME_LIMITED:-} ]]; then
    seconds TRIGGER_TIMEOUT
    export TRIGGER_TIME_LIMITED=1
    status=0
    timeout --kill-after=30s "${REPLY}s" "${BASH_SOURCE[0]}" || status=$?
    if (( status == 124 || status == 137 )); then
        fail "the run exceeded $TRIGGER_TIMEOUT"
    fi
    exit "$status"
fi

for name in API_PRIVATE_DOMAIN API_PORT PIPELINE_TRIGGER_TOKEN; do
    [[ -n ${!name:-} ]] || config_error "$name is not set"
done
[[ $API_PRIVATE_DOMAIN =~ ^[A-Za-z0-9.-]+$ ]] || config_error "API_PRIVATE_DOMAIN must be a host name"
[[ $API_PORT =~ ^[0-9]+$ ]] || config_error "API_PORT must be a port number"
seconds TRIGGER_WAKE_TIMEOUT; readonly wake_timeout=$REPLY
seconds TRIGGER_BUSY_TIMEOUT; readonly busy_timeout=$REPLY
seconds TRIGGER_BUSY_INTERVAL; readonly busy_interval=$REPLY
seconds TRIGGER_POLL_INTERVAL; readonly poll_interval=$REPLY
seconds TRIGGER_RUN_TIMEOUT; readonly run_timeout=$REPLY
seconds TRIGGER_REQUEST_TIMEOUT; readonly request_timeout=$REPLY
seconds TRIGGER_HEARTBEAT_INTERVAL; readonly heartbeat_interval=$REPLY
# Between attempts while the API wakes (Railway's private network may also need a few seconds after
# this container starts).
readonly wake_interval=2
# Straight to the API over the private network (not through the web's Caddy, which serves only /api/*).
readonly base_url="http://$API_PRIVATE_DOMAIN:$API_PORT/internal/pipeline-runs"

# The token goes to curl as a config on stdin, never as an argument (visible in ps). Surrounding white
# space is stripped, as the API strips it (a value set with --stdin may end in a line break).
token=$PIPELINE_TRIGGER_TOKEN
token=${token#"${token%%[![:space:]]*}"}
token=${token%"${token##*[![:space:]]}"}
[[ -n $token ]] || config_error "PIPELINE_TRIGGER_TOKEN is blank"
[[ $token != *[[:cntrl:]]* ]] || config_error "PIPELINE_TRIGGER_TOKEN contains a control character"
token=${token//\\/\\\\}
readonly curl_auth="header = \"Authorization: Bearer ${token//\"/\\\"}\""
unset token PIPELINE_TRIGGER_TOKEN

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# One request to the API: sets REPLY to the HTTP status (000: no answer) and CONNECTED to whether a
# connection was made (an answer may then be missing although the API got the request), and leaves the
# body in $work/body and curl's error in $work/error. `--disable` first: no .curlrc is read.
request() {
    local out time_connect
    : >"$work/body"
    out=$(curl --disable --silent --show-error --request "$1" --output "$work/body" \
        --write-out '%{http_code} %{time_connect}' --connect-timeout 10 --max-time "$request_timeout" \
        --config - "$base_url$2" <<<"$curl_auth" 2>"$work/error") || true
    read -r REPLY time_connect <<<"$out" || true
    [[ $REPLY =~ ^[0-9]{3}$ ]] || REPLY=000
    CONNECTED=1
    [[ ${time_connect:-0} =~ ^0(\.0*)?$ ]] && CONNECTED=0
    return 0
}

# The `detail` of an API error body ({"detail":"<code>"}), empty when the body is not one.
detail() {
    jq -r 'if type == "object" and (.detail | type) == "string" then .detail else empty end' \
        "$work/body" 2>/dev/null || true
}

# The last request's outcome for the log: curl's error, or the HTTP status and the error code.
outcome() {
    local text
    if [[ $REPLY == 000 ]]; then
        text=$(head -n 1 "$work/error")
        printf '%s\n' "${text:-no answer}"
    else
        text=$(detail)
        printf 'HTTP %s%s\n' "$REPLY" "${text:+ $text}"
    fi
}

# No usable answer (no connection, or 502/503/504 while the API wakes or restarts): the first one is
# logged, and it fails once it has lasted TRIGGER_WAKE_TIMEOUT. $1 says what the trigger was doing.
unreachable_since=""
unreachable() {
    if [[ -z $unreachable_since ]]; then
        unreachable_since=$SECONDS
        log "$1: no answer yet ($(outcome)); retrying for up to $TRIGGER_WAKE_TIMEOUT"
    elif (( SECONDS - unreachable_since >= wake_timeout )); then
        fail "$1: the API did not answer within $TRIGGER_WAKE_TIMEOUT ($(outcome))"
    fi
}
answered() {
    if [[ -n $unreachable_since ]]; then
        log "the API answers (after $(( SECONDS - unreachable_since ))s)"
        unreachable_since=""
    fi
}

# The run statuses of the API (RunStatus), named only here: sets REPLY to ok, failed, interrupted (a
# failure whose log line gives the cause), waiting (queued) or active (running).
classify() {
    case $1 in
        succeeded | partial) REPLY=ok ;;
        failed) REPLY=failed ;;
        interrupted) REPLY=interrupted ;;
        queued) REPLY=waiting ;;
        running) REPLY=active ;;
        *) return 1 ;;
    esac
}

# The heartbeat of the run in $work/body (an object: its status was read): its status, step and
# progress. A field the API does not send (an older API) or sends as null shows as "?".
progress() {
    jq -r '(if (.assessedOk | type) == "number" and (.assessedFailed | type) == "number"
            then .assessedOk + .assessedFailed else "?" end) as $assessed
        | "\(.status), step \(.step // "?"), assessed \($assessed)/\(.assessTotal // "?")"
            + " (ok \(.assessedOk // "?"), failed \(.assessedFailed // "?"), errors \(.processingErrors // "?")),"
            + " last activity \(.lastActivityAt // "?")"' "$work/body" 2>/dev/null || true
}

# 1. Start the daily run. 409 pipeline_busy (e.g. an admin's run) is retried until it is free; every
# other refusal (401, 503 source_not_configured, any other 4xx) ends the trigger at once.
log "starting the daily run at $base_url/daily"
busy_since=""
while true; do
    request POST /daily
    case $REPLY in
        202)
            answered
            break
            ;;
        000 | 502 | 503 | 504)
            # 503 is also the API's refusal source_not_configured, which no retry changes.
            if [[ $REPLY == 503 && $(detail) == source_not_configured ]]; then
                fail "the API refused the run: $(outcome)"
            fi
            # No answer although the connection was made: the API may have queued the run. A second
            # POST would then wait for that run as "busy" and start another one, and the first run's
            # result would never reach the exit code. Only a request that never connected is retried.
            if [[ $REPLY == 000 && $CONNECTED == 1 ]]; then
                fail "no answer to the start after the request was sent ($(outcome)): the run may have started; check /admin"
            fi
            unreachable "waking the API"
            sleep "$wake_interval"
            ;;
        409)
            answered
            if [[ -z $busy_since ]]; then
                busy_since=$SECONDS
                log "busy: a run is queued or running ($(outcome)); retrying every $TRIGGER_BUSY_INTERVAL for up to $TRIGGER_BUSY_TIMEOUT"
            elif (( SECONDS - busy_since >= busy_timeout )); then
                fail "the pipeline was still busy after $TRIGGER_BUSY_TIMEOUT"
            fi
            sleep "$busy_interval"
            ;;
        401)
            fail "the API refused the run: $(outcome) (PIPELINE_TRIGGER_TOKEN must be set on the API and be the same here)"
            ;;
        *)
            fail "the API refused the run: $(outcome)"
            ;;
    esac
done

# The created runs in execution order (a retry run first when there were leftovers).
runs=$(jq -r 'if type == "array" and length > 0
        and all(.[]; (.id | type == "string" and test("^[0-9a-f-]{36}$")) and (.kind | type == "string"))
    then .[] | "\(.id) \(.kind)" else error("not a list of runs") end' "$work/body" 2>/dev/null) \
    || fail "the API answered 202 without a list of runs"
ids=() kinds=()
while read -r id kind; do
    ids+=("$id") kinds+=("$kind")
done <<<"$runs"
log "started: $(jq -r 'map("\(.kind) \(.id)") | join(", ")' "$work/body")"

# 2. Wait until every run has ended, polling each one that has not. An unfinished run logs its status
# when it changes; a running one then a heartbeat every TRIGGER_HEARTBEAT_INTERVAL (beat: when it last
# logged a line). A queued run has no progress to show: the run before it logs the heartbeats.
deadline=$(( SECONDS + run_timeout ))
declare -A seen=() ended=() beat=()
pending=${#ids[@]}
failed=0
while true; do
    for i in "${!ids[@]}"; do
        id=${ids[i]} kind=${kinds[i]}
        [[ -z ${ended[$id]:-} ]] || continue
        request GET "/$id"
        case $REPLY in
            200) answered ;;
            000 | 502 | 503 | 504)
                unreachable "waiting for $kind $id"
                continue
                ;;
            *) fail "cannot read the status of $kind $id: $(outcome)" ;;
        esac
        status=$(jq -r 'if (.status | type) == "string" then .status else "" end' "$work/body" 2>/dev/null) || status=""
        classify "$status" || fail "$kind $id: unexpected status '$status'"
        if [[ $REPLY == waiting || $REPLY == active ]]; then
            if [[ ${seen[$id]:-} != "$status" ]]; then
                log "$kind $id: $status"
                seen[$id]=$status beat[$id]=$SECONDS
            elif [[ $REPLY == active ]] && (( SECONDS - ${beat[$id]} >= heartbeat_interval )); then
                log "$kind $id: $(progress)"
                beat[$id]=$SECONDS
            fi
            continue
        fi
        ended[$id]=$status
        pending=$(( pending - 1 ))
        cause=""
        if [[ $REPLY == interrupted ]]; then
            cause=" (cause $(jq -r '.interruptionCause // "?"' "$work/body" 2>/dev/null || true))"
        fi
        if [[ $REPLY != ok ]]; then
            failed=1
        fi
        log "$kind $id: $status ($(jq -r '"fetched \(.fetched), new posts \(.newPosts), assessed ok \(.assessedOk), assessed failed \(.assessedFailed), processing errors \(.processingErrors), dead \(.dead)"' "$work/body"))$cause"
    done
    (( pending > 0 )) || break
    remaining=$(( deadline - SECONDS ))
    if (( remaining <= 0 )); then
        unfinished=""
        for i in "${!ids[@]}"; do
            [[ -n ${ended[${ids[i]}]:-} ]] \
                || unfinished+="${unfinished:+, }${kinds[i]} ${ids[i]} (${seen[${ids[i]}]:-no status yet})"
        done
        fail "not ended within $TRIGGER_RUN_TIMEOUT: $unfinished"
    fi
    sleep $(( remaining < poll_interval ? remaining : poll_interval ))
done

# 3. The result: a partial run counts as done (its failed posts are retried by the next run).
summary=""
for i in "${!ids[@]}"; do
    summary+="${summary:+, }${kinds[i]} ${ended[${ids[i]}]}"
done
if (( failed )); then
    fail "a run did not succeed: $summary"
fi
log "OK: $summary"
