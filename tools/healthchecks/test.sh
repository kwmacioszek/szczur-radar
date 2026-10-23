#!/usr/bin/env bash
# Local test of checks.sh (F-04, out-of-app-alert-channel; see README.md), without healthchecks.io or
# Docker. A stub `curl` in PATH answers every request from a script per case (method, URL, status and a
# fixture as the body, in order) and records what checks.sh sent: argv, the config read from stdin (URL
# and headers), the headers and the body. fixtures/ follow the documented shape of the Management API v3.
# Cases:
#   1. apply on a fresh project: three upserts (name, tags, schedule, tz, grace, channels, unique), one
#      arming check-in per cron check (no API key on it), the read-back with both up, the ping URLs on
#      stdout and nowhere in the log;
#   2. apply when the checks exist and are up: no arming check-in;
#   3. a cron check still not up after arming (also: an arming check-in that fails) → 1;
#   4. --schedule and --grace reach both cron bodies, not the API's check;
#   5. delete: the three checks found by name (another environment's check left alone), a missing one skipped;
#   6. API errors: 401, 500, no answer, an answer that is not the check → 1, no request after the failure;
#   7. usage: no key, bad arguments, bad environment names, bad overrides → 2 without a request;
#   8. production also gets production/deploy-health (the monitor's check), applied and deleted with the rest
#      (cases 1–6 use a PR env and so check that no other environment gets it);
#   and in every case the API key is never in curl's argv, stdout or the log.
# Needs bash and jq. Run from anywhere: tools/healthchecks/test.sh
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
readonly checks=$here/checks.sh
readonly fixtures=$here/fixtures
readonly env_name=szczur-radar-pr-99
readonly api=https://hc.test/api/v3
readonly key='hcw_TEST-key-never-shown_0123456789'
readonly u1=00000000-0000-4000-8000-0000000000a1
readonly u2=00000000-0000-4000-8000-0000000000a2
readonly u3=00000000-0000-4000-8000-0000000000a3
readonly ping1=https://hc-ping.com/$u1
readonly ping2=https://hc-ping.com/$u2
readonly ping3=https://hc-ping.com/$u3
readonly u4=00000000-0000-4000-8000-0000000000a4
readonly ping4=https://hc-ping.com/$u4
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
trap 'exit 130' INT TERM

step() { printf '\n== %s\n' "$*"; }
pass() { printf 'PASS %s\n' "$*"; }
die() { printf 'FAIL %s\n' "$*" >&2; exit 1; }

command -v jq >/dev/null || die "jq is not installed"

# The stub `curl`. Each call takes the next line of $HC_STUB/script: "<METHOD> <URL> <STATUS> <body file
# or ->"; STATUS "fail" makes curl fail with no answer. It writes the body to --output and prints the
# status for --write-out '%{http_code}'. Call n goes to $HC_STUB/calls ("<METHOD> <URL>"), its argv to
# $HC_STUB/argv, its config from stdin to $HC_STUB/config.n, its headers (config and argv) to
# $HC_STUB/headers.n, its body to $HC_STUB/body.n. A URL in argv, an unknown option, a request the script
# does not expect or one past its end is recorded in $HC_STUB/errors and fails with exit 3.
mkdir -p "$tmp/bin"
cat >"$tmp/bin/curl" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
n=$(( $(cat "$HC_STUB/n" 2>/dev/null || echo 0) + 1 ))
echo "$n" >"$HC_STUB/n"
printf '%s\n' "$*" >>"$HC_STUB/argv"
error() { printf 'call %s: %s\n' "$n" "$*" >>"$HC_STUB/errors"; echo "stub curl: $*" >&2; exit 3; }
method="" output="" write_out="" body="" config="" url=""
: >"$HC_STUB/headers.$n"
[[ ${1:-} == --disable ]] || error "--disable is not the first option"
while (( $# > 0 )); do
    case $1 in
        --disable | --silent | --show-error) shift ;;
        --request) method=$2; shift 2 ;;
        --output) output=$2; shift 2 ;;
        --write-out) write_out=$2; shift 2 ;;
        --max-time) shift 2 ;;
        --header) printf '%s\n' "$2" >>"$HC_STUB/headers.$n"; shift 2 ;;
        --data-binary)
            [[ $2 == @* ]] || error "--data-binary without a file"
            body=${2#@}
            shift 2
            ;;
        --config)
            [[ $2 == - ]] || error "--config is not stdin"
            config=$(cat)
            shift 2
            ;;
        -*) error "unexpected option $1" ;;
        *) error "a URL or value in argv" ;;
    esac
done
printf '%s\n' "$config" >"$HC_STUB/config.$n"
while IFS= read -r line; do
    [[ -n $line ]] || continue
    [[ $line =~ ^(url|header)\ =\ \"(.*)\"$ ]] || error "unexpected config line"
    value=${BASH_REMATCH[2]}
    value=${value//\\\"/\"}
    value=${value//\\\\/\\}
    if [[ ${BASH_REMATCH[1]} == url ]]; then url=$value; else printf '%s\n' "$value" >>"$HC_STUB/headers.$n"; fi
done <<<"$config"
[[ -n $url ]] || error "no URL in the config"
[[ -z $body ]] || cp "$body" "$HC_STUB/body.$n"
printf '%s %s\n' "$method" "$url" >>"$HC_STUB/calls"
answer=$(sed -n "${n}p" "$HC_STUB/script")
[[ -n $answer ]] || error "unexpected request $method $url (past the end of the script)"
read -r want_method want_url status file <<<"$answer"
[[ $method == "$want_method" && $url == "$want_url" ]] || error "request $method $url, expected $want_method $want_url"
if [[ $status == fail ]]; then
    echo "curl: (7) Failed to connect to hc.test port 443 after 3 ms: Couldn't connect to server" >&2
    exit 7
fi
[[ -n $output ]] || error "no --output"
if [[ $file == - ]]; then : >"$output"; else cp "$file" "$output"; fi
[[ $write_out == '%{http_code}' ]] || error "unexpected --write-out"
printf '%s' "$status"
SH
chmod +x "$tmp/bin/curl"

# Writes the stub script of case $1 from the lines after it.
script() {
    local name=$1
    shift
    mkdir -p "$tmp/$name.stub"
    printf '%s\n' "$@" >"$tmp/$name.stub/script"
}

# fixtures/get-checks.json changed by the jq filter $2, as the file $tmp/fixture-$1.json (prints its path).
fixture() {
    local file="$tmp/fixture-$1.json"
    jq "$2" "$fixtures/get-checks.json" >"$file"
    printf '%s\n' "$file"
}

show() {
    printf -- '--- stdout (%s)\n' "$1"
    cat "$tmp/$1.out"
    printf -- '--- log\n'
    cat "$tmp/$1.log"
    printf -- '--- requests\n'
    cat "$tmp/$1.stub/calls" 2>/dev/null || true
    printf -- '--- stub errors\n'
    cat "$tmp/$1.stub/errors" 2>/dev/null || true
}

# Case $1: checks.sh with the arguments after it must exit with $2 (the key set unless NO_KEY=1; extra
# variables in CASE_ENV, NAME=value words). Then: no stub error, and the key never in argv, stdout or the log.
run_case() {
    local name=$1 expected=$2 status=0 vars=()
    shift 2
    mkdir -p "$tmp/$name.stub"
    [[ -f $tmp/$name.stub/script ]] || : >"$tmp/$name.stub/script"
    [[ ${NO_KEY:-} == 1 ]] || vars+=("HEALTHCHECKS_API_KEY= $key ")
    # shellcheck disable=SC2206 # CASE_ENV is a list of NAME=value words
    vars+=(${CASE_ENV:-})
    env -u HEALTHCHECKS_API_KEY PATH="$tmp/bin:$PATH" HC_STUB="$tmp/$name.stub" HEALTHCHECKS_API_URL="$api/" \
        ${vars[@]+"${vars[@]}"} "$checks" "$@" >"$tmp/$name.out" 2>"$tmp/$name.log" || status=$?
    [[ $status == "$expected" ]] || { show "$name"; die "$name: exit code $status, expected $expected"; }
    [[ ! -s $tmp/$name.stub/errors ]] || { show "$name"; die "$name: the stub curl refused a request"; }
    ! grep -qF -- "$key" "$tmp/$name.out" "$tmp/$name.log" || { show "$name"; die "$name: the key is in the output"; }
    if [[ -f $tmp/$name.stub/argv ]]; then
        ! grep -qF -- "$key" "$tmp/$name.stub/argv" || { show "$name"; die "$name: the key is in curl's argv"; }
        ! grep -qF -- "$u1" "$tmp/$name.stub/argv" || { show "$name"; die "$name: a check UUID is in curl's argv"; }
    fi
    # A ping URL (its UUID) only ever on stdout.
    for uuid in "$u1" "$u2" "$u3" "$u4"; do
        ! grep -qF -- "$uuid" "$tmp/$name.log" || { show "$name"; die "$name: a check UUID is in the log"; }
    done
}

# Case $1 made exactly the requests after it ("<METHOD> <URL>", in order).
requests_are() {
    local name=$1 expected=""
    shift
    (( $# == 0 )) || expected=$(printf '%s\n' "$@")
    [[ $(cat "$tmp/$name.stub/calls" 2>/dev/null) == "$expected" ]] \
        || { show "$name"; die "$name: the requests are not: ${*:-(none)}"; }
}

# Case $1's request $2 has a body for which the jq expression $3 is true.
body_matches() {
    jq -e "$3" "$tmp/$1.stub/body.$2" >/dev/null \
        || { show "$1"; cat "$tmp/$1.stub/body.$2"; die "$1: the body of request $2 does not match: $3"; }
}

# Case $1's request $2 carries the API key (yes) or not (no).
key_header() {
    local found=no
    grep -qxF -- "X-Api-Key: $key" "$tmp/$1.stub/headers.$2" && found=yes
    [[ $found == "$3" ]] || { show "$1"; die "$1: request $2 X-Api-Key header present: $found, expected $3"; }
}

log_says() {
    grep -qF -- "$2" "$tmp/$1.log" || { show "$1"; die "$1: the log does not say '$2'"; }
}

out_is() {
    local name=$1 expected=""
    shift
    (( $# == 0 )) || expected=$(printf '%s\n' "$@")
    [[ $(cat "$tmp/$name.out") == "$expected" ]] || { show "$name"; die "$name: stdout is not: ${*:-(empty)}"; }
}

readonly checks_url="$api/checks/"
readonly created1=$fixtures/post-pipeline-trigger-created.json
readonly created2=$fixtures/post-db-backup-created.json
readonly created3=$fixtures/post-api-events-created.json
readonly listed=$fixtures/get-checks.json
readonly urls_out=("$env_name/pipeline-trigger $ping1" "$env_name/db-backup $ping2" "$env_name/api-events $ping3")

step "1. apply on a fresh project"
script fresh \
    "POST $checks_url 201 $created1" \
    "POST $ping1 200 -" \
    "POST $checks_url 201 $created2" \
    "POST $ping2 200 -" \
    "POST $checks_url 201 $created3" \
    "GET $checks_url 200 $listed"
run_case fresh 0 apply "$env_name"
requests_are fresh "POST $checks_url" "POST $ping1" "POST $checks_url" "POST $ping2" "POST $checks_url" "GET $checks_url"
body_matches fresh 1 '. == {name: "szczur-radar-pr-99/pipeline-trigger", tags: "szczur-radar szczur-radar-pr-99",
    desc: .desc, schedule: "30 0 * * *", tz: "UTC", grace: 11700, channels: "*", unique: ["name"]}
    and (.desc | test("pipeline-trigger"))'
body_matches fresh 3 '. == {name: "szczur-radar-pr-99/db-backup", tags: "szczur-radar szczur-radar-pr-99",
    desc: .desc, schedule: "0 2 * * *", tz: "UTC", grace: 8100, channels: "*", unique: ["name"]}'
body_matches fresh 5 '. == {name: "szczur-radar-pr-99/api-events", tags: "szczur-radar szczur-radar-pr-99",
    desc: .desc, timeout: 31536000, grace: 31536000, channels: "*", unique: ["name"]}'
grep -qxF 'Content-Type: application/json' "$tmp/fresh.stub/headers.1" || { show fresh; die "fresh: no JSON content type"; }
key_header fresh 1 yes
key_header fresh 2 no
key_header fresh 6 yes
grep -qxF 'Content-Type: text/plain' "$tmp/fresh.stub/headers.2" || { show fresh; die "fresh: the check-in is not text/plain"; }
out_is fresh "${urls_out[@]}"
log_says fresh "$env_name/pipeline-trigger: created (cron '30 0 * * *' UTC, grace 11700s), status new"
log_says fresh "$env_name/pipeline-trigger: armed with one success check-in"
log_says fresh "$env_name/api-events: created (simple, period 31536000s, grace 31536000s), status new"
log_says fresh "OK: the checks of $env_name are in place, both cron checks up"
pass "three checks created with their schedules, grace and all channels; both cron checks armed (no key on the check-in); the ping URLs on stdout only"

step "2. apply when the checks exist and are up"
existing1=$(fixture existing1 '.checks[0]')
existing2=$(fixture existing2 '.checks[1]')
existing3=$(fixture existing3 '.checks[2]')
script existing \
    "POST $checks_url 200 $existing1" \
    "POST $checks_url 200 $existing2" \
    "POST $checks_url 200 $existing3" \
    "GET $checks_url 200 $listed"
run_case existing 0 apply "$env_name"
requests_are existing "POST $checks_url" "POST $checks_url" "POST $checks_url" "GET $checks_url"
log_says existing "$env_name/db-backup: updated (cron '0 2 * * *' UTC, grace 8100s), status up"
out_is existing "${urls_out[@]}"
pass "existing up checks: updated, no arming check-in, the ping URLs on stdout"

step "3. a cron check not up after the upsert → 1"
still_new=$(fixture still-new '.checks[1].status = "new" | .checks[1].n_pings = 0')
script still-new \
    "POST $checks_url 201 $created1" \
    "POST $ping1 200 -" \
    "POST $checks_url 201 $created2" \
    "POST $ping2 fail -" \
    "POST $checks_url 201 $created3" \
    "GET $checks_url 200 $still_new"
run_case still-new 1 apply "$env_name"
log_says still-new "error: arming $env_name/db-backup failed (no answer: curl: (7)"
log_says still-new "FAILED: not up: $env_name/db-backup (new); a cron check alerts only once it is up"
out_is still-new
pass "an arming check-in that fails and a cron check still new: exit 1, no ping URL printed"
down=$(fixture down '.checks[0].status = "down"')
script down \
    "POST $checks_url 200 $existing1" \
    "POST $checks_url 200 $existing2" \
    "POST $checks_url 200 $existing3" \
    "GET $checks_url 200 $down"
run_case down 1 apply "$env_name"
log_says down "FAILED: not up: $env_name/pipeline-trigger (down)"
pass "a cron check down: exit 1"
missing=$(fixture missing 'del(.checks[2])')
script missing \
    "POST $checks_url 200 $existing1" \
    "POST $checks_url 200 $existing2" \
    "POST $checks_url 200 $existing3" \
    "GET $checks_url 200 $missing"
run_case missing 1 apply "$env_name"
log_says missing "FAILED: $env_name/api-events: 0 checks with this name after the upsert (expected 1)"
pass "a check missing from the read-back: exit 1"

step "4. --schedule and --grace override both cron checks"
script override \
    "POST $checks_url 200 $existing1" \
    "POST $checks_url 200 $existing2" \
    "POST $checks_url 200 $existing3" \
    "GET $checks_url 200 $listed"
run_case override 0 apply "$env_name" --schedule '*/5 * * * *' --grace 60
body_matches override 1 '.schedule == "*/5 * * * *" and .grace == 60 and .tz == "UTC" and .name == "szczur-radar-pr-99/pipeline-trigger"'
body_matches override 2 '.schedule == "*/5 * * * *" and .grace == 60 and .name == "szczur-radar-pr-99/db-backup"'
body_matches override 3 '.timeout == 31536000 and .grace == 31536000 and (has("schedule") | not)'
log_says override "$env_name/pipeline-trigger: updated (cron '*/5 * * * *' UTC, grace 60s), status up"
pass "the overrides reach both cron bodies; the API's check keeps its period and grace"
script grace-only \
    "POST $checks_url 200 $existing1" \
    "POST $checks_url 200 $existing2" \
    "POST $checks_url 200 $existing3" \
    "GET $checks_url 200 $listed"
run_case grace-only 0 apply "$env_name" --grace 600
body_matches grace-only 1 '.schedule == "30 0 * * *" and .grace == 600'
body_matches grace-only 2 '.schedule == "0 2 * * *" and .grace == 600'
pass "--grace alone keeps each cron check's own schedule"

step "5. delete"
script delete \
    "GET $checks_url 200 $listed" \
    "DELETE $api/checks/$u1 200 $existing1" \
    "DELETE $api/checks/$u2 200 $existing2" \
    "DELETE $api/checks/$u3 200 $existing3"
run_case delete 0 delete "$env_name"
requests_are delete "GET $checks_url" "DELETE $api/checks/$u1" "DELETE $api/checks/$u2" "DELETE $api/checks/$u3"
key_header delete 2 yes
log_says delete "$env_name/pipeline-trigger: deleted"
log_says delete "OK: 3 check(s) of $env_name deleted"
out_is delete
pass "the three checks of the environment deleted by UUID (another environment's check left alone), nothing on stdout"
partial=$(fixture partial 'del(.checks[0])')
script delete-partial \
    "GET $checks_url 200 $partial" \
    "DELETE $api/checks/$u2 200 $existing2" \
    "DELETE $api/checks/$u3 200 $existing3"
run_case delete-partial 0 delete "$env_name"
log_says delete-partial "$env_name/pipeline-trigger: not found, nothing to delete"
log_says delete-partial "OK: 2 check(s) of $env_name deleted"
pass "a check already gone: skipped, exit 0"
script delete-error \
    "GET $checks_url 200 $listed" \
    "DELETE $api/checks/$u1 500 -"
run_case delete-error 1 delete "$env_name"
log_says delete-error "FAILED: deleting $env_name/pipeline-trigger: HTTP 500"
requests_are delete-error "GET $checks_url" "DELETE $api/checks/$u1"
pass "a failed delete: exit 1, nothing after it"

step "6. API errors → 1"
script unauthorized "POST $checks_url 401 $fixtures/error-401.json"
run_case unauthorized 1 apply "$env_name"
log_says unauthorized "FAILED: creating or updating $env_name/pipeline-trigger: HTTP 401: wrong api key"
requests_are unauthorized "POST $checks_url"
pass "401: exit 1 with the API's message, no further request"
script server-error \
    "POST $checks_url 200 $existing1" \
    "POST $checks_url 200 $existing2" \
    "POST $checks_url 200 $existing3" \
    "GET $checks_url 500 -"
run_case server-error 1 apply "$env_name"
log_says server-error "FAILED: reading the checks: HTTP 500"
out_is server-error
pass "500 on the read-back: exit 1, no ping URL printed"
script no-answer "GET $checks_url fail -"
run_case no-answer 1 delete "$env_name"
log_says no-answer "FAILED: reading the checks: no answer: curl: (7) Failed to connect"
pass "no answer: exit 1"
script wrong-check "POST $checks_url 200 $created2"
run_case wrong-check 1 apply "$env_name"
log_says wrong-check "FAILED: creating or updating $env_name/pipeline-trigger: the answer is not that check"
pass "an answer for another check: exit 1"
not_a_list=$(fixture not-a-list '{checks: "none"}')
script not-a-list "GET $checks_url 200 $not_a_list"
run_case not-a-list 1 delete "$env_name"
log_says not-a-list "the answer is not a list of checks"
pass "a read that is not a list of checks: exit 1"

step "7. usage errors → 2, no request"
NO_KEY=1 run_case no-key 2 apply "$env_name"
log_says no-key "FAILED: HEALTHCHECKS_API_KEY is not set"
requests_are no-key
CASE_ENV='HEALTHCHECKS_API_KEY=' NO_KEY=1 run_case blank-key 2 apply "$env_name"
pass "no key or a blank key: exit 2, no request"
run_case no-arguments 2
log_says no-arguments "usage: checks.sh apply <environment>"
run_case unknown-command 2 create "$env_name"
log_says unknown-command "unknown command (apply or delete)"
run_case no-environment 2 apply
log_says no-environment "missing environment"
i=0
for bad in Production 'szczur/radar' '' 'pr env'; do
    i=$(( i + 1 ))
    run_case "bad-environment-$i" 2 apply "$bad"
    log_says "bad-environment-$i" "the environment must be a Railway environment name"
done
run_case key-as-argument 2 apply "$env_name" "$key"
log_says key-as-argument "FAILED: unexpected argument"
run_case delete-option 2 delete "$env_name" --grace 60
log_says delete-option "--grace is an option of apply only"
run_case grace-without-value 2 apply "$env_name" --grace
log_says grace-without-value "--grace needs a value"
for bad in 59 31536001 abc 1h 0; do
    run_case "bad-grace-$bad" 2 apply "$env_name" --grace "$bad"
    log_says "bad-grace-$bad" "--grace must be a number of seconds from 60 to 31536000"
done
i=0
for bad in '* * * *' '*/5 * * * * *' '*/5 * * * ?' '30 0 * * "x"'; do
    i=$(( i + 1 ))
    run_case "bad-schedule-$i" 2 apply "$env_name" --schedule "$bad"
    log_says "bad-schedule-$i" "--schedule must be a cron expression with 5 fields"
done
CASE_ENV='HEALTHCHECKS_API_URL=ftp://hc.test' run_case bad-api-url 2 apply "$env_name"
log_says bad-api-url "HEALTHCHECKS_API_URL must be an http(s) URL"
run_case help 0 --help
grep -qF 'usage: checks.sh apply' "$tmp/help.out" || { show help; die "help: no usage on stdout"; }
pass "bad arguments, environment names, overrides and API URL: exit 2 without a request (an argument is never echoed)"

step "8. production: also the monitor's check production/deploy-health"
# The PR env's recorded answers renamed to production; the fourth check is the API's one as deploy-health.
to_production() { jq 'tojson | gsub("szczur-radar-pr-99"; "production") | fromjson' "$1"; }
as_deploy_health() {
    jq --arg a "$u3" --arg b "$u4" \
        'tojson | gsub("szczur-radar-pr-99"; "production") | gsub("api-events"; "deploy-health") | gsub($a; $b) | fromjson' "$1"
}
to_production "$existing1" >"$tmp/prod1.json"
to_production "$existing2" >"$tmp/prod2.json"
to_production "$existing3" >"$tmp/prod3.json"
as_deploy_health "$created3" >"$tmp/prod4.json"
jq --slurpfile d "$tmp/prod4.json" '.checks += [$d[0] | .status = "new"]' <(to_production "$listed") >"$tmp/prod-listed.json"
script production \
    "POST $checks_url 200 $tmp/prod1.json" \
    "POST $checks_url 200 $tmp/prod2.json" \
    "POST $checks_url 200 $tmp/prod3.json" \
    "POST $checks_url 201 $tmp/prod4.json" \
    "GET $checks_url 200 $tmp/prod-listed.json"
run_case production 0 apply production
requests_are production "POST $checks_url" "POST $checks_url" "POST $checks_url" "POST $checks_url" "GET $checks_url"
body_matches production 4 '. == {name: "production/deploy-health", tags: "szczur-radar production",
    desc: .desc, timeout: 31536000, grace: 31536000, channels: "*", unique: ["name"]}
    and (.desc | test("szczur-radar-monitor"))'
out_is production "production/pipeline-trigger $ping1" "production/db-backup $ping2" "production/api-events $ping3" \
    "production/deploy-health $ping4"
log_says production "production/deploy-health: created (simple, period 31536000s, grace 31536000s), status new"
pass "production: the three checks and production/deploy-health (simple, not armed), its ping URL on stdout"
script delete-production \
    "GET $checks_url 200 $tmp/prod-listed.json" \
    "DELETE $api/checks/$u1 200 $tmp/prod1.json" \
    "DELETE $api/checks/$u2 200 $tmp/prod2.json" \
    "DELETE $api/checks/$u3 200 $tmp/prod3.json" \
    "DELETE $api/checks/$u4 200 $tmp/prod4.json"
run_case delete-production 0 delete production
log_says delete-production "OK: 4 check(s) of production deleted"
pass "delete production: deploy-health too"

printf '\nAll healthchecks tests passed.\n'
