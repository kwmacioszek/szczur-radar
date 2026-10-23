#!/usr/bin/env bash
# The healthchecks.io checks of one Railway environment (F-04, out-of-app-alert-channel; see README.md).
# Run by the operator on their own machine, never in CI or Railway:
#
#   checks.sh apply <environment> [--schedule '<cron>'] [--grace <seconds>]
#   checks.sh delete <environment>
#
# apply creates or updates (upsert by name) the three checks of the environment: <environment>/pipeline-trigger
# and <environment>/db-backup (cron checks, UTC, grace from the crons' time limits) and
# <environment>/api-events (a simple check: the API's events, every one ending with a success). Every
# check alerts all integrations of the project (the Telegram one). A new check never alerts before its
# first check-in, so each cron check still "new" gets one success check-in (armed). Then it reads the
# checks back and fails unless both cron checks are up. Only then it prints "<name> <ping URL>" per check
# to stdout: the values of HEALTHCHECK_PING_URL. --schedule and --grace override both cron checks (only to
# verify alerts in a PR env; apply again without them afterwards). delete deletes the three checks.
#
# HEALTHCHECKS_API_KEY: the project's read-write API key (never an argument). HEALTHCHECKS_API_URL
# (optional, for test.sh): the API's base URL, default https://healthchecks.io/api/v3.
# Exit codes: 0 done, 1 an API error or a cron check not up, 2 a usage error (arguments, the key).
# A ping URL (and the UUID in it) is a secret: whoever knows it can check in. The key and every URL go to
# curl as a config on stdin, never as an argument (visible in ps); the log never shows them. Never `set -x`.
set -euo pipefail

readonly usage="usage: checks.sh apply <environment> [--schedule '<cron>'] [--grace <seconds>]
       checks.sh delete <environment>"

log() { printf '%s healthchecks: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() { log "FAILED: $*"; exit 1; }
usage_error() { log "FAILED: $*"; printf '%s\n' "$usage" >&2; exit 2; }

# The checks of an environment, in this order: the service part of the name, the kind, the schedule (cron
# checks, UTC) and the grace time in seconds. The cron checks' grace covers the cron's whole time limit
# from its /start (TRIGGER_TIMEOUT 3h, BACKUP_TIMEOUT 2h) plus 15 min: changing a limit needs an apply with
# a new grace. The API's check has no schedule: a period and a grace of 365 days, renewed by every event.
readonly services=(pipeline-trigger db-backup api-events)
readonly kinds=(cron cron simple)
readonly schedules=("30 0 * * *" "0 2 * * *" "")
readonly graces=(11700 8100 31536000)
readonly descriptions=(
    "Daily pipeline run (tools/pipeline-trigger): /start, then the exit code with the last log line."
    "Daily database backup (tools/db-backup): /start, then the exit code with the last log line."
    "Events of szczur-radar-api (alerts/HealthchecksAlerts): /fail with the event, then a success."
)
readonly simple_timeout=31536000
readonly tag=szczur-radar
# A URL goes into a curl config between double quotes: no quote, backslash, space or control character.
readonly url_pattern='^https?://[A-Za-z0-9._~:/?#@!$&()*+,;=%-]+$'
readonly ping_pattern='^https://[A-Za-z0-9._~:/?#@!$&()*+,;=%-]+$'
readonly uuid_pattern='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'

# Arguments. An unexpected one is never echoed: it might be the key, pasted in the wrong place.
(( $# > 0 )) || usage_error "missing command"
command=$1
shift
case $command in
    apply | delete) ;;
    -h | --help) printf '%s\n' "$usage"; exit 0 ;;
    *) usage_error "unknown command (apply or delete)" ;;
esac
(( $# > 0 )) || usage_error "missing environment"
environment=$1
shift
[[ $environment =~ ^[a-z0-9-]+$ ]] \
    || usage_error "the environment must be a Railway environment name ([a-z0-9-]+), e.g. production"
schedule="" grace=""
while (( $# > 0 )); do
    case $1 in
        --schedule | --grace)
            [[ $command == apply ]] || usage_error "$1 is an option of apply only"
            (( $# > 1 )) || usage_error "$1 needs a value"
            if [[ $1 == --schedule ]]; then schedule=$2; else grace=$2; fi
            shift 2
            ;;
        *) usage_error "unexpected argument" ;;
    esac
done
if [[ -n $schedule ]]; then
    [[ $schedule =~ ^[0-9A-Za-z*/,-]+([[:blank:]]+[0-9A-Za-z*/,-]+){4}$ ]] \
        || usage_error "--schedule must be a cron expression with 5 fields, e.g. '*/5 * * * *'"
fi
if [[ -n $grace ]]; then
    if [[ ! $grace =~ ^[1-9][0-9]{0,7}$ ]] || (( grace < 60 || grace > 31536000 )); then
        usage_error "--grace must be a number of seconds from 60 to 31536000"
    fi
fi
readonly command environment schedule grace

api=${HEALTHCHECKS_API_URL:-https://healthchecks.io/api/v3}
api=${api%/}
[[ $api =~ $url_pattern ]] || usage_error "HEALTHCHECKS_API_URL must be an http(s) URL"
readonly api

# The key goes to curl as a header line of a config on stdin. Surrounding white space is stripped.
key=${HEALTHCHECKS_API_KEY:-}
key=${key#"${key%%[![:space:]]*}"}
key=${key%"${key##*[![:space:]]}"}
unset HEALTHCHECKS_API_KEY
[[ -n $key ]] || usage_error "HEALTHCHECKS_API_KEY is not set (the project's read-write API key, see README.md)"
[[ $key != *[[:cntrl:]]* ]] || usage_error "HEALTHCHECKS_API_KEY contains a control character"
key=${key//\\/\\\\}
readonly auth_header="header = \"X-Api-Key: ${key//\"/\\\"}\""
unset key

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# One request: $1 the method, $2 the URL, $3 a body file (optional), $4 its content type (default JSON),
# $5 "nokey" to send it without the API key (a check-in). Sets REPLY to the HTTP status (000: no answer)
# and leaves the body in $work/body and curl's error in $work/error. `--disable` first: no .curlrc is read.
request() {
    local config extra=()
    config=$(printf 'url = "%s"' "$2")
    [[ ${5:-} == nokey ]] || config+=$'\n'"$auth_header"
    if [[ -n ${3:-} ]]; then
        extra=(--header "Content-Type: ${4:-application/json}" --data-binary "@$3")
    fi
    : >"$work/body"
    REPLY=$(curl --disable --silent --show-error --request "$1" --output "$work/body" --write-out '%{http_code}' \
        --max-time 30 ${extra[@]+"${extra[@]}"} --config - <<<"$config" 2>"$work/error") || true
    [[ $REPLY =~ ^[0-9]{3}$ ]] || REPLY=000
    return 0
}

# The last request's outcome for the log: curl's error, or the HTTP status and the API's error message.
outcome() {
    local text
    if [[ $REPLY == 000 ]]; then
        text=$(head -n 1 "$work/error")
        printf 'no answer: %s\n' "${text:-curl failed}"
    else
        text=$(jq -r 'if type == "object" and (.error | type) == "string" then .error[0:200] else empty end' \
            "$work/body" 2>/dev/null || true)
        printf 'HTTP %s%s\n' "$REPLY" "${text:+: $text}"
    fi
}

# Reads every check of the project (GET /checks/) into $work/checks.json.
read_checks() {
    request GET "$api/checks/"
    [[ $REPLY == 200 ]] || fail "reading the checks: $(outcome)"
    jq -e '.checks | type == "array"' "$work/body" >/dev/null 2>&1 \
        || fail "reading the checks: the answer is not a list of checks"
    cp "$work/body" "$work/checks.json"
}

# Sets REPLY to the number of checks named $1 in $work/checks.json.
count_named() {
    REPLY=$(jq --arg n "$1" '[.checks[] | select(.name == $n)] | length' "$work/checks.json")
}

# The field $2 of the check named $1 in $work/checks.json ("" when it is not a string).
field_of() {
    jq -r --arg n "$1" --arg f "$2" \
        '.checks[] | select(.name == $n) | .[$f] | if type == "string" then . else "" end' "$work/checks.json"
}

if [[ $command == delete ]]; then
    read_checks
    deleted=0
    for service in "${services[@]}"; do
        name="$environment/$service"
        count_named "$name"
        if (( REPLY == 0 )); then
            log "$name: not found, nothing to delete"
            continue
        fi
        (( REPLY == 1 )) || fail "$name: $REPLY checks with this name (expected 1); delete them in the panel"
        uuid=$(jq -r --arg n "$name" '.checks[] | select(.name == $n)
            | (.uuid // ((.update_url // "") | sub("/+$"; "") | split("/") | last)) | tostring' "$work/checks.json")
        [[ $uuid =~ $uuid_pattern ]] || fail "$name: the answer has no check UUID (update_url)"
        request DELETE "$api/checks/$uuid"
        [[ $REPLY == 200 ]] || fail "deleting $name: $(outcome)"
        log "$name: deleted"
        deleted=$(( deleted + 1 ))
    done
    log "OK: $deleted check(s) of $environment deleted"
    exit 0
fi

# 1. Upsert each check; arm each cron check that is still new with one success check-in.
printf 'armed by tools/healthchecks/checks.sh apply\n' >"$work/arm.txt"
for i in "${!services[@]}"; do
    name="$environment/${services[i]}"
    kind=${kinds[i]}
    if [[ $kind == cron ]]; then
        check_schedule=${schedule:-${schedules[i]}}
        check_grace=${grace:-${graces[i]}}
        jq -n --arg name "$name" --arg tags "$tag $environment" --arg desc "${descriptions[i]}" \
            --arg schedule "$check_schedule" --argjson grace "$check_grace" \
            '{name: $name, tags: $tags, desc: $desc, schedule: $schedule, tz: "UTC", grace: $grace,
              channels: "*", unique: ["name"]}' >"$work/upsert.json"
        settings="cron '$check_schedule' UTC, grace ${check_grace}s"
    else
        jq -n --arg name "$name" --arg tags "$tag $environment" --arg desc "${descriptions[i]}" \
            --argjson timeout "$simple_timeout" --argjson grace "${graces[i]}" \
            '{name: $name, tags: $tags, desc: $desc, timeout: $timeout, grace: $grace,
              channels: "*", unique: ["name"]}' >"$work/upsert.json"
        settings="simple, period ${simple_timeout}s, grace ${graces[i]}s"
    fi
    request POST "$api/checks/" "$work/upsert.json"
    case $REPLY in
        201) action=created ;;
        200) action=updated ;;
        *) fail "creating or updating $name: $(outcome)" ;;
    esac
    jq -e --arg n "$name" '.name == $n and (.status | type) == "string"' "$work/body" >/dev/null 2>&1 \
        || fail "creating or updating $name: the answer is not that check"
    status=$(jq -r .status "$work/body")
    log "$name: $action ($settings), status $status"
    if [[ $kind == cron && $status == new ]]; then
        ping_url=$(jq -r '.ping_url | if type == "string" then . else "" end' "$work/body")
        [[ $ping_url =~ $ping_pattern ]] || fail "$name: the answer has no https:// ping URL"
        request POST "$ping_url" "$work/arm.txt" text/plain nokey
        if [[ $REPLY == 2?? ]]; then
            log "$name: armed with one success check-in"
        else
            log "error: arming $name failed ($(outcome))"
        fi
    fi
done

# 2. Read the checks back: both cron checks must be up, or they never alert.
read_checks
not_up="" lines=""
for i in "${!services[@]}"; do
    name="$environment/${services[i]}"
    count_named "$name"
    (( REPLY == 1 )) || fail "$name: $REPLY checks with this name after the upsert (expected 1)"
    status=$(field_of "$name" status)
    ping_url=$(field_of "$name" ping_url)
    [[ $ping_url =~ $ping_pattern ]] || fail "$name: the check has no https:// ping URL"
    if [[ ${kinds[i]} == cron && $status != up ]]; then
        not_up+="${not_up:+, }$name (${status:-no status})"
    fi
    lines+="$name $ping_url"$'\n'
done
if [[ -n $not_up ]]; then
    fail "not up: $not_up; a cron check alerts only once it is up (see its last check-in in the panel)"
fi
printf '%s' "$lines"
log "OK: the checks of $environment are in place, both cron checks up; their ping URLs are on stdout (secrets)"
