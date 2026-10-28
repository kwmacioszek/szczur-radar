#!/usr/bin/env bash
# Local test of the deploy gate, without Railway or GitHub (S-14, safe-deploy-gate; see README.md).
# gate.sh classifies each plan in fixtures/ (the format of `railway config plan --json` from CLI
# 5.67.1: each change has only kind, severity, summary and details; values redacted). pr-env-*.json
# are real plans from the PR env, trimmed. Every rule has a case it allows and a case it refuses:
#   1. the plan is ok and targets the expected environment and project, else refused;
#   2. a destructive change is refused (variable.delete, resource.delete);
#   3. only resource.create, resource.update, variable.set and domain.create pass, whatever the severity
#      (variable.delete or an unknown kind marked safe is refused);
#   4. a source change (a source.* detail or a summary naming source) is refused, except one whose
#      every detail is source.checkSuites (null|false → true);
#   5. a change without a kind and a resource.update without a details list are refused (unknown format);
#   6. a change without deployEffect may redeploy the desiredGraph resource its summary (or address)
#      names, so that resource goes on the restarts list; deployEffect "none" does not; a change that
#      may redeploy a resource the gate cannot resolve is refused.
# Also: the step summary (number of changes, the changes, the restarts), the restarts file
# (GATE_RESTARTS_FILE, names of services and databases only: a bucket has no deployment), the
# `::error::` lines, and exit code 2 for a bad invocation or an unreadable plan.
# Then, without Railway or GitHub (9–11):
#   9.  web-url.sh: production, szczur-radar-pr-<N>, an unknown name (exit 2);
#   10. wait-safe.sh against a stub API (a small Python HTTP server with answers scripted per case):
#       safe; busy → safe; busy until the timeout and until a deadline shorter than it (exit 1); a
#       deadline passed at the start (exit 1, no request); 401 and 404 (exit 0, warning); refused
#       connections → 200; an API that stays unavailable, refused or 503 (exit 0, warning); a 200
#       without a verdict (exit 1); DEPLOY_GUARD=off; bad values (exit 2);
#   11. verify-apply.sh against a stub `railway` in PATH (scripted `deployment list --json`: deployments
#       newest first, each with the meta of an apply's, a push's or a push skipped by watch paths) and
#       the stub API: the snapshot (the newest deployment of any origin); the apply's deployment (the
#       newest with an iac-change-set/ patchId above the snapshot) SUCCESS; FAILED, CRASHED, SKIPPED
#       (with Railway's skippedReason) (exit 1); WAITING (handed over); none yet → DEPLOYING → SUCCESS;
#       REMOVED → a newer SUCCESS, also under a push skipped by watch paths; only pushes above the
#       snapshot (SKIPPED, WAITING, SUCCESS: "no deploy" with a notice after the window, exit 0); a
#       push SKIPPED above the apply's WAITING (handed over) and a push SUCCESS above the apply's FAILED
#       (exit 1); the apply's deployment after two lists without it; the snapshot's deployment not
#       among the newest 50 (exit 1, "cannot tell"); railway failing until the limit, also after a list
#       within the window (exit 1, never "no deploy"); DEPLOYING until the limit (exit 1); /api/ready
#       503 (exit 1); an empty restarts list (only /api/ready); bad invocations, a window longer than
#       the limit (exit 2).
# Needs bash, jq, curl and python3 (no Docker). Short limits and windows (1–5 s) keep it at about a
# minute.
# Run from anywhere: tools/deploy-gate/test.sh
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
readonly gate=$here/gate.sh
readonly fixtures=$here/fixtures
# The project of every fixture (the real one, as in .github/workflows/ci.yml).
readonly project_id=decf00e4-f5a4-43df-b338-6698be87c5a1
tmp=$(mktemp -d)

step() { printf '\n== %s\n' "$*"; }
pass() { printf 'PASS %s\n' "$*"; }
die() { printf 'FAIL %s\n' "$*" >&2; exit 1; }

trap 'rm -rf "$tmp"' EXIT
trap 'exit 130' INT TERM

command -v jq >/dev/null || die "jq is not installed"

show() {
    printf -- '--- step summary (%s)\n' "$1"
    cat "$tmp/$1.md"
    printf -- '--- log\n'
    cat "$tmp/$1.log"
    printf -- '--- restarts file\n'
    cat "$tmp/$1.restarts"
}

# Case $1: gate.sh on the plan $2 (a path) with the arguments after it must exit with $3.
# Its summary goes to $tmp/$1.md, its log to $tmp/$1.log, its restarts file to $tmp/$1.restarts
# (filled with a stale line first: the gate must empty it).
run_gate() {
    local name=$1 plan=$2 expected=$3
    shift 3
    local status=0
    echo service.stale-from-an-earlier-run >"$tmp/$name.restarts"
    GATE_RESTARTS_FILE=$tmp/$name.restarts "$gate" "$plan" "$@" >"$tmp/$name.md" 2>"$tmp/$name.log" || status=$?
    [[ $status == "$expected" ]] || { show "$name"; die "$name: exit code $status, expected $expected"; }
}

# Case $1: gate.sh on fixtures/$1.json for production must exit with $2.
check_gate() {
    run_gate "$1" "$fixtures/$1.json" "$2" production "$project_id"
}

# Case $1's step summary says $2.
summary_says() {
    grep -qF -- "$2" "$tmp/$1.md" || { show "$1"; die "$1: the step summary does not say '$2'"; }
}

# Case $1's log says $2.
log_says() {
    grep -qF -- "$2" "$tmp/$1.log" || { show "$1"; die "$1: the log does not say '$2'"; }
}

# Case $1's log does not say $2.
log_never_says() {
    ! grep -qF -- "$2" "$tmp/$1.log" || { show "$1"; die "$1: the log says '$2'"; }
}

# Case $1's restarts file lists exactly the resource names after it, in that order.
restarts_are() {
    local name=$1
    shift
    local expected=""
    (( $# == 0 )) || expected=$(printf '%s\n' "$@")
    [[ $(cat "$tmp/$name.restarts") == "$expected" ]] \
        || { show "$name"; die "$name: restarts file is not: ${*:-(empty)}"; }
}

# Case $1 refused exactly $2 change(s), each with its own ::error:: line after the header.
errors_are() {
    local count
    count=$(grep -c '^::error::' "$tmp/$1.log" || true)
    [[ $count == $(( $2 + 1 )) ]] || { show "$1"; die "$1: $count ::error:: lines, expected $(( $2 + 1 ))"; }
    log_says "$1" "::error::plan contains changes the deploy gate does not allow; refusing to apply to production"
}

step "1. the plan targets the expected environment and project"
check_gate empty 0
summary_says empty '### Railway config plan for `production`'
summary_says empty '**Changes: 0**'
summary_says empty '**Restarts:** none'
restarts_are empty
pass "an empty plan for production: exit 0, 0 changes, no restarts, the stale restarts file emptied"
run_gate pr-env-empty "$fixtures/pr-env-empty.json" 0 szczur-radar-pr-81 "$project_id"
summary_says pr-env-empty '**Changes: 0**'
restarts_are pr-env-empty
pass "the real empty plan of szczur-radar-pr-81 (CLI 5.67.1): exit 0"
check_gate pr-env-empty 1
log_says pr-env-empty 'it targets environment "szczur-radar-pr-81", expected "production"'
pass "the same real plan when production is expected: exit 1"
check_gate wrong-env 1
log_says wrong-env '::error::plan failed or targets the wrong environment (expected production): it targets environment "szczur-radar-pr-7", expected "production"'
summary_says wrong-env '**Refused:** it targets environment "szczur-radar-pr-7"'
restarts_are wrong-env
pass "a plan for szczur-radar-pr-7 when production is expected: exit 1"
run_gate wrong-project "$fixtures/empty.json" 1 production 11111111-2222-4333-8444-555555555555
log_says wrong-project 'it targets project "decf00e4-f5a4-43df-b338-6698be87c5a1", expected "11111111-2222-4333-8444-555555555555"'
pass "a plan for another project: exit 1"
run_gate pr-env "$fixtures/wrong-env.json" 0 szczur-radar-pr-7 "$project_id"
summary_says pr-env '### Railway config plan for `szczur-radar-pr-7`'
pass "the same plan for szczur-radar-pr-7 when that PR env is expected: exit 0"
check_gate not-ok 1
log_says not-ok 'the plan is not ok (see its diagnostics)'
log_says not-ok 'Variable LLM_API_KEY is referenced but not declared'
pass "ok:false: exit 1, its diagnostics in the log"

step "2. destructive changes are refused"
check_gate variable-delete 1
errors_are variable-delete 1
log_says variable-delete '::error::Delete variable pipeline-trigger.TRIGGER_HEARTBEAT_INTERVAL: destructive change'
summary_says variable-delete '**Refused:** 1 change(s)'
restarts_are variable-delete
pass "variable.delete (destructive): exit 1, no restarts file for a refused plan"
check_gate resource-delete 1
log_says resource-delete '::error::Delete bucket Postgres-PITR: destructive change'
pass "resource.delete (destructive): exit 1"
check_gate variable-set 0
summary_says variable-set '| Update variable szczur-radar-api.PIPELINE_LLM_CONCURRENCY | `variable.set` | `szczur-radar-api.PIPELINE_LLM_CONCURRENCY («hidden» → «hidden»)` | safe | `szczur-radar-api` | allowed |'
summary_says variable-set '**Restarts:** `szczur-radar-api`, `db-backup`'
restarts_are variable-set szczur-radar-api db-backup
pass "variable.set (Update variable and Set variable, safe): exit 0, each service announced as restarted"

step "3. only the allowed kinds of change pass, whatever their severity"
check_gate variable-delete-safe 1
log_says variable-delete-safe 'kind variable.delete is not allowed'
log_never_says variable-delete-safe 'destructive change'
pass "variable.delete marked safe: exit 1 by the allow list, not by its severity"
check_gate unknown-kind-safe 1
log_says unknown-kind-safe '::error::Delete domain szczur-radar-web-production.up.railway.app: kind domain.delete is not allowed'
pass "an unknown kind (domain.delete) marked safe: exit 1"
check_gate resource-create 0
summary_says resource-create '**Changes: 2**'
summary_says resource-create '| Create bucket db-backups | `resource.create` |  | safe | `db-backups` (bucket: no deploy) | allowed |'
summary_says resource-create '**Restarts:** `db-backup` (an apply may redeploy them; the check after it waits for the deployments the apply starts)'
restarts_are resource-create db-backup
pass "resource.create of a bucket and a service (no deployEffect): exit 0, only the service listed (a bucket has no deployment)"
check_gate domain-create 0
restarts_are domain-create szczur-radar-web
pass "domain.create: exit 0, its service announced"
check_gate domain-create-api 1
log_says domain-create-api '::error::Create domain for szczur-radar-api: a public domain only on szczur-radar-web (szczur-radar-api stays private)'
restarts_are domain-create-api
pass "domain.create on szczur-radar-api: exit 1 (only the web is public)"
check_gate mixed 1
errors_are mixed 1
log_says mixed '::error::Delete variable db-backup.BACKUP_TIMEOUT'
log_never_says mixed '::error::Update variable db-backup.BACKUP_KEEP_DAILY'
summary_says mixed '| Update variable db-backup.BACKUP_KEEP_DAILY | `variable.set` | `db-backup.BACKUP_KEEP_DAILY («hidden» → «hidden»)` | safe | `db-backup` | allowed |'
restarts_are mixed
pass "an allowed and a refused change: exit 1, only the refused one in the errors"

step "4. source changes are refused, except turning on Wait for CI"
run_gate pr-env-checksuites "$fixtures/pr-env-checksuites.json" 0 szczur-radar-pr-81 "$project_id"
summary_says pr-env-checksuites '**Changes: 2**'
summary_says pr-env-checksuites '| Update db-backup source.checkSuites | `resource.update` | `source.checkSuites (null → true)` | safe | `db-backup` | allowed |'
summary_says pr-env-checksuites '**Restarts:** `db-backup`, `pipeline-trigger` (an apply may redeploy them; the check after it waits for the deployments the apply starts)'
restarts_are pr-env-checksuites db-backup pipeline-trigger
pass "the real plan of szczur-radar-pr-81 (checkSuites null → true on both crons): exit 0, both crons announced"
check_gate source-branch 1
log_says source-branch '::error::Update szczur-radar-api source.branch: source change (only source.checkSuites → true may pass)'
pass "source.branch: exit 1"
check_gate checksuites-enable 0
restarts_are checksuites-enable pipeline-trigger db-backup
pass "source.checkSuites null → true and false → true: exit 0, both crons announced"
check_gate checksuites-disable 1
log_says checksuites-disable 'source change'
pass "source.checkSuites true → null: exit 1"
check_gate checksuites-from-unknown 1
log_says checksuites-from-unknown 'source change'
pass "source.checkSuites from a value other than null or false → true: exit 1"
check_gate checksuites-with-branch 1
log_says checksuites-with-branch 'source change'
pass "source.checkSuites → true together with source.branch: exit 1"
check_gate source-summary-without-details 1
log_says source-summary-without-details '::error::Update db-backup source.checkSuites: source change'
pass "a summary naming source.checkSuites with no details: exit 1"
check_gate source-whole-object 1
log_says source-whole-object '::error::Update szczur-radar-api deploy.startCommand and 1 more: source change'
pass "a detail of the whole source object, hidden from the summary behind \"and 1 more\": exit 1"

step "5. an unknown plan format is refused"
check_gate update-without-details 1
log_says update-without-details 'resource.update without a details list (unknown plan format)'
pass "resource.update without details: exit 1"
check_gate change-without-kind 1
log_says change-without-kind 'change without a kind (unknown plan format)'
pass "a change without a kind: exit 1"
check_gate postgres-limit-override 0
summary_says postgres-limit-override '| `resource.update` | `deploy.limitOverride.containers.cpu (2 → 1)`<br>`deploy.limitOverride.containers.memoryBytes (4000000000 → 2000000000)` | safe | `postgres` | allowed |'
summary_says postgres-limit-override '**Restarts:** `postgres` (an apply may redeploy them; the check after it waits for the deployments the apply starts)'
restarts_are postgres-limit-override postgres
pass "limitOverride on Postgres: exit 0, the restart announced"

step "6. restarts: a missing deployEffect counts as a possible redeploy of the resource the change names"
check_gate optional-fields 0
summary_says optional-fields '| Update szczur-radar-api groupId | `resource.update` | `groupId (null → "group-fixture")` | safe | none | allowed |'
restarts_are optional-fields szczur-radar-web postgres
pass "address given: its resource; deployEffect none: not announced; deployEffect deploy: announced"
check_gate unknown-restart-target 1
log_says unknown-restart-target '::error::Update ghost-service deploy.sleepApplication: a change that redeploys a resource names no single resource of desiredGraph'
pass "a restart of a resource missing from desiredGraph: exit 1"
check_gate unknown-address 1
log_says unknown-address '::error::Update szczur-radar-web deploy.healthcheckTimeout: a change that redeploys a resource names no single resource of desiredGraph'
pass "an address missing from desiredGraph (the summary is not consulted then): exit 1"

step "7. a bad invocation or an unreadable plan: exit 2"
run_gate no-arguments "" 2
log_says no-arguments 'usage: gate.sh <plan.json> <target-env> <project-id>'
pass "missing arguments: exit 2"
run_gate missing-file "$tmp/no-such-plan.json" 2 production "$project_id"
log_says missing-file 'is not a readable file'
pass "a plan file that does not exist: exit 2"
printf 'Error: Unauthorized. Please login with `railway login`\n' >"$tmp/not-json.json"
run_gate not-json "$tmp/not-json.json" 2 production "$project_id"
log_says not-json 'is not a JSON object'
pass "a plan that is not JSON (the CLI failed before planning): exit 2"
check_gate no-change-set 2
log_says no-change-set 'has no list of changes (changeSet.changes): unknown plan format'
pass "an ok plan without changeSet.changes: exit 2"
run_gate empty-env "$fixtures/empty.json" 2 "" "$project_id"
pass "an empty target environment: exit 2"
for name in no-arguments missing-file not-json no-change-set; do
    restarts_are "$name"
done
pass "the restarts file is emptied in every case"

step "8. runnable from any directory"
(cd / && GATE_RESTARTS_FILE=$tmp/elsewhere.restarts "$gate" "$fixtures/empty.json" production "$project_id" >/dev/null 2>&1) \
    || die "gate.sh fails when run from /"
pass "gate.sh runs from /"

step "9. web-url.sh: the web address of an environment"
[[ $("$here/web-url.sh" production) == https://szczur-radar-web-production.up.railway.app ]] \
    || die "web-url.sh production"
pass "production → https://szczur-radar-web-production.up.railway.app"
[[ $("$here/web-url.sh" szczur-radar-pr-81) == https://szczur-radar-web-szczur-radar-pr-81.up.railway.app ]] \
    || die "web-url.sh szczur-radar-pr-81"
pass "szczur-radar-pr-81 → https://szczur-radar-web-szczur-radar-pr-81.up.railway.app"
for name in staging szczur-radar-pr-0 szczur-radar-pr-7x ""; do
    status=0
    "$here/web-url.sh" "$name" >"$tmp/web-url.out" 2>"$tmp/web-url.log" || status=$?
    [[ $status == 2 && ! -s $tmp/web-url.out ]] || die "web-url.sh '$name': exit $status, expected 2 and no output"
done
grep -qF "::error::web-url: unknown environment '' (expected production or szczur-radar-pr-<N>)" "$tmp/web-url.log" \
    || die "web-url.sh: no ::error:: line for an unknown name"
status=0
"$here/web-url.sh" >/dev/null 2>&1 || status=$?
[[ $status == 2 ]] || die "web-url.sh without an argument: exit $status, expected 2"
pass "an unknown name (staging, pr-0, pr-7x, empty) or no argument: exit 2, nothing printed"

command -v python3 >/dev/null || die "python3 is not installed"
command -v curl >/dev/null || die "curl is not installed"

# The stub API. STUB_CASE (JSON) scripts it:
#   paths: {"<path>": [answer, …]}: GET <path> gives the answers in turn, the last one repeating; an
#          answer is {"status": <code>, "body": <JSON, or a string sent as text>}; any other path: 404;
#   listen_after: seconds before it accepts connections (until then they are refused).
# One line per request on stdout: "GET <path> <status>".
read -r -d '' stub_code <<'PY' || true
import json, os, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

case = json.loads(os.environ["STUB_CASE"])
paths = {path: list(answers) for path, answers in case.get("paths", {}).items()}


class Api(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        answers = paths.get(self.path)
        if answers is None:
            entry = {"status": 404, "body": {"detail": "not_found"}}
        else:
            entry = answers.pop(0) if len(answers) > 1 else answers[0]
        body = entry.get("body")
        text = isinstance(body, str)
        data = b"" if body is None else (body if text else json.dumps(body)).encode()
        self.send_response(entry["status"])
        self.send_header("Content-Type", "text/plain" if text else "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        print(f"GET {self.path} {entry['status']}", flush=True)


time.sleep(case.get("listen_after", 0))
server = ThreadingHTTPServer(("127.0.0.1", int(os.environ["STUB_PORT"])), Api)
print("listening", flush=True)
server.serve_forever()
PY

# A local TCP port nothing listens on (yet).
free_port() {
    python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()'
}

# Starts the stub API of case $1 answering $2 (STUB_CASE) and sets STUB_URL. Its requests go to
# $tmp/$1.requests. Waits until it listens, unless the case makes it listen late.
stub_pid=""
start_stub() {
    local name=$1 case=$2 port
    port=$(free_port)
    STUB_URL=http://127.0.0.1:$port
    STUB_CASE=$case STUB_PORT=$port python3 -u -c "$stub_code" >"$tmp/$name.requests" 2>"$tmp/$name.stub-errors" &
    stub_pid=$!
    [[ $case != *listen_after* ]] || return 0
    for _ in $(seq 1 50); do
        grep -qx listening "$tmp/$name.requests" && return 0
        sleep 0.1
    done
    cat "$tmp/$name.stub-errors" >&2
    die "$name: the stub API did not start"
}

# Stops the stub API of case $1.
stop_stub() {
    if [[ -n $stub_pid ]]; then
        kill "$stub_pid" 2>/dev/null || true
        wait "$stub_pid" 2>/dev/null || true
    fi
    stub_pid=""
}
trap 'stop_stub; rm -rf "$tmp"' EXIT

show_run() {
    printf -- '--- log (%s)\n' "$1"
    cat "$tmp/$1.log"
    if [[ -s $tmp/$1.out ]]; then
        printf -- '--- stdout\n'
        cat "$tmp/$1.out"
    fi
    if [[ -f $tmp/$1.requests ]]; then
        printf -- '--- requests to the stub API\n'
        grep -vx listening "$tmp/$1.requests" || true
    fi
    if [[ -f $tmp/$1.railway/calls ]]; then
        printf -- '--- railway calls\n'
        cat "$tmp/$1.railway/calls"
    fi
}

# Case $1's log (stderr) says $2.
run_says() {
    grep -qF -- "$2" "$tmp/$1.log" || { show_run "$1"; die "$1: the log does not say '$2'"; }
}

# Case $1's log (stderr) does not say $2.
run_never_says() {
    ! grep -qF -- "$2" "$tmp/$1.log" || { show_run "$1"; die "$1: the log says '$2'"; }
}

# Case $1's stdout says $2.
out_says() {
    grep -qF -- "$2" "$tmp/$1.out" || { show_run "$1"; die "$1: stdout does not say '$2'"; }
}

# Case $1's stub API got $2 requests.
api_requests_were() {
    local count
    count=$(grep -c '^GET ' "$tmp/$1.requests" || true)
    [[ $count == "$2" ]] || { show_run "$1"; die "$1: $count requests to the stub API, expected $2"; }
}

# Case $1 asked the stub railway about service $2 exactly $3 times.
railway_calls_were() {
    local count
    count=$(grep -c -- "-s $2 " "$tmp/$1.railway/calls" || true)
    [[ $count == "$3" ]] || { show_run "$1"; die "$1: railway was asked about $2 $count times, expected $3"; }
}

# Case $1: wait-safe.sh against $3 (a URL) with the variables after it (NAME=value) must exit with $2.
# Every DEPLOY_GUARD* variable of the caller is dropped; the interval is 1 s unless the case sets it.
run_guard() {
    local name=$1 expected=$2 url=$3 status=0
    shift 3
    env -u DEPLOY_GUARD -u DEPLOY_GUARD_TIMEOUT -u DEPLOY_GUARD_WAKE_TIMEOUT -u DEPLOY_GUARD_DEADLINE \
        DEPLOY_GUARD_INTERVAL=1s "$@" "$here/wait-safe.sh" "$url" >"$tmp/$name.out" 2>"$tmp/$name.log" || status=$?
    [[ $status == "$expected" ]] || { show_run "$name"; die "$name: exit code $status, expected $expected"; }
}

# Case $1: wait-safe.sh against a stub API whose /api/deploy-safety answers $3 (a JSON list of
# answers), with the variables after it, must exit with $2. STUB_LISTEN_AFTER=<s> makes the stub
# refuse connections for that long first.
guard_case() {
    local name=$1 expected=$2 answers=$3
    shift 3
    start_stub "$name" "{${STUB_LISTEN_AFTER:+\"listen_after\": $STUB_LISTEN_AFTER, }\"paths\": {\"/api/deploy-safety\": $answers}}"
    run_guard "$name" "$expected" "$STUB_URL" "$@"
    stop_stub
}

readonly safe='{"status": 200, "body": {"safe": true, "reason": null}}'
readonly running='{"status": 200, "body": {"safe": false, "reason": "pipeline_running"}}'
readonly queued='{"status": 200, "body": {"safe": false, "reason": "pipeline_queued"}}'
readonly unavailable='{"status": 503, "body": {"status": "unavailable"}}'

step "10. wait-safe.sh: the deploy guard"
guard_case safe 0 "[$safe]"
run_says safe 'deploy guard: safe, no pipeline run is queued or running'
api_requests_were safe 1
pass "safe: exit 0 after one request"
guard_case busy-then-safe 0 "[$queued, $running, $safe]"
run_says busy-then-safe 'deploy guard: not safe: pipeline_queued; asking again every 1s for up to 90m'
run_says busy-then-safe 'deploy guard: still pipeline_running'
run_says busy-then-safe 'deploy guard: safe, no pipeline run is queued or running (after waiting'
api_requests_were busy-then-safe 3
pass "queued, running, then safe (DEPLOY_GUARD_INTERVAL=1s): exit 0, each reason in the log"
guard_case busy-timeout 1 "[$running]" DEPLOY_GUARD_TIMEOUT=2s
run_says busy-timeout '::error::deploy guard: the pipeline is still pipeline_running after 2s; nothing applied or deployed. If no run is really progressing (a zombie run), restart the API, then push again or run `railway redeploy`'
run_says busy-timeout 'DEPLOY_GUARD=off (repository variable) turns the guard off. Runbook S-14'
pass "running until DEPLOY_GUARD_TIMEOUT=2s: exit 1, the runbook in the error"
# The timeout is longer than the deadline but short enough that a guard ignoring the deadline fails
# this case (with the timeout's error) instead of hanging the test.
guard_case busy-deadline 1 "[$running]" DEPLOY_GUARD_TIMEOUT=20s DEPLOY_GUARD_DEADLINE=$(( $(date +%s) + 2 ))
run_says busy-deadline '::error::deploy guard: the Wait for CI window (2 h) is closing (deadline '
run_says busy-deadline ', the pipeline still pipeline_running): Railway will not deploy the code of this commit. Push again or run `railway redeploy`'
pass "running until a DEPLOY_GUARD_DEADLINE 2 s away (shorter than DEPLOY_GUARD_TIMEOUT=20s): exit 1, the Wait for CI window error"
guard_case deadline-passed 1 "[$safe]" DEPLOY_GUARD_DEADLINE=$(( $(date +%s) - 1 ))
run_says deadline-passed '::error::deploy guard: the Wait for CI window (2 h) is closing'
api_requests_were deadline-passed 0
pass "a DEPLOY_GUARD_DEADLINE passed at the start: exit 1 without a request"
guard_case old-api-401 0 '[{"status": 401, "body": {"detail": "not_authenticated"}}]'
run_says old-api-401 '::warning::deploy guard: '
run_says old-api-401 'answered HTTP 401: the API has no deploy-safety route yet (an older version'
pass "401 (an API before S-14: the route needs a session there): exit 0 with a warning"
guard_case old-api-404 0 '[{"status": 404, "body": {"detail": "not_found"}}]'
run_says old-api-404 '::warning::deploy guard: '
run_says old-api-404 'answered HTTP 404: the API has no deploy-safety route yet'
pass "404: exit 0 with a warning"
STUB_LISTEN_AFTER=1 guard_case wake 0 "[$unavailable, $safe]"
run_says wake 'deploy guard: no answer yet (curl: (7)'
run_says wake 'deploy guard: safe'
api_requests_were wake 2
pass "connections refused for 1 s, then 503, then safe: exit 0"
guard_case unavailable 0 "[$unavailable]" DEPLOY_GUARD_WAKE_TIMEOUT=2s
run_says unavailable 'deploy guard: no answer yet (HTTP 503); retrying for up to 2s (the API may be waking up)'
run_says unavailable '::warning::deploy guard: the API did not answer within 2s (HTTP 503): it is unavailable, so no pipeline run can be running. Not waiting'
pass "503 until DEPLOY_GUARD_WAKE_TIMEOUT=2s: exit 0 with a warning"
run_guard refused 0 "http://127.0.0.1:$(free_port)" DEPLOY_GUARD_WAKE_TIMEOUT=2s
run_says refused '::warning::deploy guard: the API did not answer within 2s (curl: (7)'
pass "connections refused until DEPLOY_GUARD_WAKE_TIMEOUT=2s: exit 0 with a warning"
guard_case unreadable 1 '[{"status": 200, "body": "<html>Application not found</html>"}]'
run_says unreadable '::error::deploy guard: '
run_says unreadable 'answered 200 without a readable verdict: <html>Application not found</html>'
guard_case no-reason 1 '[{"status": 200, "body": {"safe": false, "reason": null}}]'
run_says no-reason 'answered 200 without a readable verdict'
pass "a 200 that is not a verdict (HTML; safe:false without a reason): exit 1"
guard_case forbidden 1 '[{"status": 403, "body": {"detail": "forbidden"}}]'
run_says forbidden '::error::deploy guard: unexpected answer from '
pass "any other answer (403): exit 1"
run_guard off 0 "http://127.0.0.1:$(free_port)" DEPLOY_GUARD=off DEPLOY_GUARD_TIMEOUT=bad
run_says off '::warning::deploy guard: off (DEPLOY_GUARD=off)'
pass "DEPLOY_GUARD=off: exit 0 with a warning, no request"
run_guard bad-timeout 2 "http://127.0.0.1:1" DEPLOY_GUARD_TIMEOUT=90x
run_says bad-timeout "::error::deploy guard: DEPLOY_GUARD_TIMEOUT must be a duration such as 30s, 3m or 2h (it is '90x')"
run_guard bad-interval 2 "http://127.0.0.1:1" DEPLOY_GUARD_INTERVAL=0s
run_says bad-interval 'DEPLOY_GUARD_INTERVAL must be a duration'
run_guard bad-switch 2 "http://127.0.0.1:1" DEPLOY_GUARD=no
run_says bad-switch "::error::deploy guard: DEPLOY_GUARD must be off, on or empty (it is 'no')"
run_guard bad-deadline 2 "http://127.0.0.1:1" DEPLOY_GUARD_DEADLINE=2026-10-18T12:00:00Z
run_says bad-deadline 'DEPLOY_GUARD_DEADLINE must be a time in seconds since the epoch'
run_guard bad-url 2 "szczur-radar-web-production.up.railway.app/api"
run_says bad-url 'the web address must be an http(s) origin'
pass "a bad duration (90x, 0s), switch, deadline or web address: exit 2"

# The stub `railway`: answers `railway deployment list --json --limit <1|50> -s <service> -p <project>
# -e <environment>` of the project and environment below from $RAILWAY_STUB/<service>, one answer per
# call, the last one repeating: "none" (an empty list), "empty" (nothing at all, exit 0), "garbage"
# (not JSON, exit 0), "fail" (the CLI fails) or the deployments,
# newest first, separated by commas, each "<id> <STATUS> <origin>[ <skippedReason>]" (e.g.
# "b3 SKIPPED watch, b2 WAITING iac, b1 SUCCESS push"). The origin gives the meta Railway gives on
# production (research of S-21):
#   iac    an apply's: patchId "iac-change-set/<environment>/<hash>", reason "deploy", commitHash (the
#          branch's tip), branch, ignoreWatchPatterns true; on postgres, which deploys only by patches,
#          just patchId and reason;
#   push   a push's: reason "deploy", commitHash, branch;
#   watch  a push skipped by watch paths: commitHash, branch, skippedReason "No changes to watched
#          files", no reason.
# Words after the origin are the deployment's skippedReason (replacing watch's). createdAt falls a minute
# per deployment down the list; --limit 1 gives only the newest. A service without a file is unknown
# (the CLI's error). Every call goes to $RAILWAY_STUB/calls; any other command, option or limit, or an
# answer it cannot read, fails with exit 3.
readonly environment_id=00000000-0000-4000-8000-000000000001
mkdir -p "$tmp/bin"
cat >"$tmp/bin/railway" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$RAILWAY_STUB/calls"
[[ ${1:-} == deployment && ${2:-} == list ]] || { echo "stub railway: unexpected command: $*" >&2; exit 3; }
shift 2
json="" limit="" service="" project="" environment=""
while (( $# > 0 )); do
    case $1 in
        --json) json=1; shift ;;
        --limit) limit=$2; shift 2 ;;
        -s) service=$2; shift 2 ;;
        -p) project=$2; shift 2 ;;
        -e) environment=$2; shift 2 ;;
        *) echo "stub railway: unexpected option $1" >&2; exit 3 ;;
    esac
done
[[ $json == 1 && ($limit == 1 || $limit == 50) && $project == "$STUB_PROJECT_ID" \
        && $environment == "$STUB_ENVIRONMENT_ID" ]] \
    || { echo "stub railway: unexpected arguments" >&2; exit 3; }
answers=$RAILWAY_STUB/$service
[[ -f $answers ]] || { echo "Service '$service' not found" >&2; exit 1; }
answer=$(head -n 1 "$answers")
if (( $(wc -l <"$answers") > 1 )); then
    tail -n +2 "$answers" >"$answers.next"
    mv "$answers.next" "$answers"
fi
case $answer in
    fail) echo "Failed to fetch: error sending request" >&2; exit 1 ;;
    none) echo "[]" ;;
    empty) ;;
    garbage) echo "Something went wrong" ;;
    *)
        jq -n --arg answer "$answer" --arg service "$service" --arg env "$STUB_ENVIRONMENT_ID" \
                --argjson limit "$limit" '
            def meta($origin):
              {commitHash: "4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f", branch: "main"} as $git
              | if $origin == "iac" then
                  {patchId: "iac-change-set/\($env)/9c2b7e4a1d0f4c3e8b5a6d7e8f901234", reason: "deploy"}
                  + (if $service == "postgres" then {} else $git + {ignoreWatchPatterns: true} end)
                elif $origin == "push" then {reason: "deploy"} + $git
                elif $origin == "watch" then $git + {skippedReason: "No changes to watched files"}
                else error("unknown origin \($origin)") end;
            [$answer | split(",")[] | [splits(" +") | select(. != "")]]
            | to_entries
            | map(.key as $n | .value as [$id, $status, $origin]
                  | {id: $id, status: $status,
                     createdAt: ("2026-10-18T12:00:00Z" | fromdate - 60 * $n | todate),
                     meta: (meta($origin)
                            + (.value[3:] | if length > 0 then {skippedReason: join(" ")} else {} end))})
            | .[:$limit]' || { echo "stub railway: cannot read the answer '$answer'" >&2; exit 3; }
        ;;
esac
SH
chmod +x "$tmp/bin/railway"

# The stub railway of case $1 answers `deployment list -s $2` with the answers after it, in turn.
railway_answers() {
    mkdir -p "$tmp/$1.railway"
    printf '%s\n' "${@:3}" >"$tmp/$1.railway/$2"
}

# Case $1: verify-apply.sh with the variables (NAME=value) before `--` and the arguments after it must
# exit with $2. Its stdout goes to $tmp/$1.out, its log to $tmp/$1.log. The caller's limits are dropped
# (the defaults apply unless the case sets them); the interval is 1 s.
run_verify() {
    local name=$1 expected=$2 status=0 vars=()
    shift 2
    while [[ $1 != -- ]]; do
        vars+=("$1")
        shift
    done
    shift
    mkdir -p "$tmp/$name.railway"
    env -u DEPLOY_VERIFY_TIMEOUT -u DEPLOY_VERIFY_DEPLOY_WINDOW -u DEPLOY_GUARD_WAKE_TIMEOUT PATH="$tmp/bin:$PATH" \
        RAILWAY_STUB="$tmp/$name.railway" STUB_PROJECT_ID=$project_id STUB_ENVIRONMENT_ID=$environment_id \
        RAILWAY_PROJECT_ID=$project_id RAILWAY_ENVIRONMENT_ID=$environment_id DEPLOY_VERIFY_INTERVAL=1s \
        "${vars[@]}" "$here/verify-apply.sh" "$@" >"$tmp/$name.out" 2>"$tmp/$name.log" || status=$?
    [[ $status == "$expected" ]] || { show_run "$name"; die "$name: exit code $status, expected $expected"; }
}

readonly ready='{"status": 200, "body": {"status": "ok"}}'

# Case $1: `verify-apply.sh check` of the services $3 (space-separated) with the snapshot $4 (JSON),
# while the stub API's /api/ready answers $5 (a JSON list), with the variables after it, must exit
# with $2. The stub railway's answers are set before (railway_answers).
check_case() {
    local name=$1 expected=$2 services=$3 before=$4 answers=$5
    shift 5
    printf '%s\n' $services >"$tmp/$name.restarts"
    printf '%s\n' "$before" >"$tmp/$name.before.json"
    start_stub "$name" "{\"paths\": {\"/api/ready\": $answers}}"
    run_verify "$name" "$expected" "$@" -- check "$tmp/$name.restarts" "$tmp/$name.before.json" "$STUB_URL"
    stop_stub
}

step "11. verify-apply.sh: the check after the apply"
railway_answers snapshot postgres "p1 SUCCESS iac, p0 REMOVED iac"
railway_answers snapshot db-backup none
railway_answers snapshot szczur-radar-api fail "a1 SLEEPING push, a0 REMOVED iac"
railway_answers snapshot pipeline-trigger "t1 SKIPPED watch, t0 SUCCESS iac"
printf '%s\n' postgres db-backup szczur-radar-api pipeline-trigger "" >"$tmp/snapshot.restarts"
run_verify snapshot 0 -- snapshot "$tmp/snapshot.restarts"
jq -e '. == {"postgres": "p1", "db-backup": null, "szczur-radar-api": "a1", "pipeline-trigger": "t1"}' \
    "$tmp/snapshot.out" >/dev/null || { show_run snapshot; die "snapshot: not the expected deployment IDs"; }
grep -qxF "deployment list --json --limit 1 -s postgres -p $project_id -e $environment_id" "$tmp/snapshot.railway/calls" \
    || { show_run snapshot; die "snapshot: railway was not called with the project and environment IDs"; }
pass "snapshot: each service's newest deployment ID, whatever started it (null: none; a failed call retried), by project and environment ID"
railway_answers snapshot-unknown postgres "p1 SUCCESS iac"
railway_answers snapshot-unknown db-backup fail fail fail
printf '%s\n' postgres db-backup >"$tmp/snapshot-unknown.restarts"
run_verify snapshot-unknown 1 -- snapshot "$tmp/snapshot-unknown.restarts"
run_says snapshot-unknown '::error::post-apply check: cannot read the deployments of db-backup before the apply (3 attempts)'
railway_calls_were snapshot-unknown db-backup 3
pass "snapshot: a service Railway cannot tell about after 3 attempts: exit 1 (no apply), not taken for one without deployments"
railway_answers snapshot-empty db-backup empty garbage empty
printf 'db-backup\n' >"$tmp/snapshot-empty.restarts"
run_verify snapshot-empty 1 -- snapshot "$tmp/snapshot-empty.restarts"
run_says snapshot-empty 'post-apply check: railway deployment list -s db-backup printed no list of deployments'
run_says snapshot-empty '::error::post-apply check: cannot read the deployments of db-backup before the apply (3 attempts)'
pass "snapshot: an empty or unreadable answer (exit 0) 3 times: exit 1 (no apply), not taken for a service without deployments"

railway_answers success postgres "p2 SUCCESS iac, p1 SUCCESS iac"
check_case success 0 postgres '{"postgres": "p1"}' "[$ready]"
out_says success '| `postgres` | `p2` | deployed (SUCCESS) |'
out_says success '| `/api/ready` | — | 200 |'
run_says success 'post-apply check: /api/ready answers 200'
grep -qxF "deployment list --json --limit 50 -s postgres -p $project_id -e $environment_id" "$tmp/success.railway/calls" \
    || { show_run success; die "success: railway was not asked for the newest 50 deployments"; }
pass "the apply's deployment SUCCESS (the newest 50 asked), then /api/ready 200: exit 0"
for status in FAILED CRASHED SKIPPED NEEDS_APPROVAL BOGUS; do
    railway_answers "ended-$status" postgres "p2 $status iac, p1 SUCCESS iac"
    check_case "ended-$status" 1 postgres '{"postgres": "p1"}' "[$ready]"
    case $status in
        NEEDS_APPROVAL) run_says "ended-$status" '::error::post-apply check: the deployment p2 of postgres needs an approval in Railway' ;;
        BOGUS) run_says "ended-$status" '::error::post-apply check: the deployment p2 of postgres has a status this check does not know: BOGUS' ;;
        *) run_says "ended-$status" "::error::post-apply check: the deployment p2 of postgres ended $status after the apply" ;;
    esac
    out_says "ended-$status" "| \`postgres\` | \`p2\` | **$status** |"
    api_requests_were "ended-$status" 0
done
pass "the apply's deployment FAILED, CRASHED, SKIPPED, NEEDS_APPROVAL or an unknown status: exit 1, /api/ready not asked"
railway_answers skipped-reason szczur-radar-web "w2 SKIPPED iac Deployment cancelled, w1 SUCCESS push"
check_case skipped-reason 1 szczur-radar-web '{"szczur-radar-web": "w1"}' "[$ready]"
run_says skipped-reason '::error::post-apply check: the deployment w2 of szczur-radar-web ended SKIPPED after the apply (Railway: "Deployment cancelled")'
out_says skipped-reason '| `szczur-radar-web` | `w2` | **SKIPPED** (Deployment cancelled) |'
api_requests_were skipped-reason 0
pass "the apply's deployment SKIPPED with a skippedReason: exit 1, Railway's reason in the error and the summary"
railway_answers skipped-pipe db-backup "b2 SKIPPED iac Paused | by operator, b1 SUCCESS push"
check_case skipped-pipe 1 db-backup '{"db-backup": "b1"}' "[$ready]"
out_says skipped-pipe '| `db-backup` | `b2` | **SKIPPED** (Paused \| by operator) |'
pass "a skippedReason with a | : escaped in the summary's table cell"
railway_answers sleeping szczur-radar-api "a2 SLEEPING iac, a1 SUCCESS push"
check_case sleeping 0 szczur-radar-api '{"szczur-radar-api": "a1"}' "[$ready]"
pass "SLEEPING (deployed, then asleep): exit 0"
railway_answers waiting db-backup "b2 WAITING iac, b1 SUCCESS push"
check_case waiting 0 db-backup '{"db-backup": "b1"}' "[$ready]"
run_says waiting '::notice::post-apply check: the deployment of db-backup waits (WAITING; Wait for CI holds it until this workflow ends): handed over to the monitor szczur-radar-monitor'
out_says waiting '| `db-backup` | `b2` | WAITING: handed over to the monitor szczur-radar-monitor |'
pass "WAITING (Wait for CI): exit 0, handed over to the monitor szczur-radar-monitor"
railway_answers deploying postgres "p1 SUCCESS iac" "p2 INITIALIZING iac, p1 SUCCESS iac" \
    "p2 DEPLOYING iac, p1 SUCCESS iac" "p2 SUCCESS iac, p1 SUCCESS iac"
check_case deploying 0 postgres '{"postgres": "p1"}' "[$ready]"
run_says deploying 'post-apply check: postgres deployment p2: DEPLOYING'
out_says deploying '| `postgres` | `p2` | deployed (SUCCESS) |'
pass "no deployment of the apply yet, then INITIALIZING, DEPLOYING, SUCCESS: exit 0"
railway_answers removed db-backup "b2 REMOVED iac, b1 SUCCESS push" "b3 SUCCESS push, b2 REMOVED iac, b1 SUCCESS push"
check_case removed 0 db-backup '{"db-backup": "b1"}' "[$ready]"
run_says removed 'post-apply check: db-backup deployment b2: REMOVED, replaced by b3: SUCCESS'
out_says removed '| `db-backup` | `b3` | deployed (SUCCESS) |'
pass "the apply's deployment REMOVED, waited for until a newer one replaces it, then that one SUCCESS: exit 0"
railway_answers removing db-backup "b2 REMOVING iac, b1 SUCCESS push" "b3 WAITING push, b2 REMOVING iac, b1 SUCCESS push"
check_case removing 0 db-backup '{"db-backup": "b1"}' "[$ready]" DEPLOY_VERIFY_TIMEOUT=3s DEPLOY_VERIFY_DEPLOY_WINDOW=2s
run_says removing 'post-apply check: db-backup deployment b2: REMOVING, replaced by b3: WAITING'
out_says removing '| `db-backup` | `b3` | WAITING: handed over to the monitor szczur-radar-monitor |'
pass "the apply's deployment REMOVING, then replaced by a newer one WAITING: exit 0, handed over"
railway_answers removed-under-skipped db-backup "b4 SKIPPED watch, b3 SUCCESS push, b2 REMOVED iac, b1 SUCCESS push"
check_case removed-under-skipped 0 db-backup '{"db-backup": "b1"}' "[$ready]"
out_says removed-under-skipped '| `db-backup` | `b3` | deployed (SUCCESS) |'
run_never_says removed-under-skipped 'ended SKIPPED'
pass "the apply's deployment REMOVED by a push's SUCCESS, a push skipped by watch paths above both: exit 0, the SUCCESS judged"
railway_answers several db-backup "b2 QUEUED iac, b1 SUCCESS push"
railway_answers several postgres "p2 BUILDING iac, p1 SUCCESS iac" "p2 SUCCESS iac, p1 SUCCESS iac"
railway_answers several pipeline-trigger none "t1 SUCCESS iac"
check_case several 0 "db-backup postgres pipeline-trigger" '{"db-backup": "b1", "postgres": "p1", "pipeline-trigger": null}' "[$ready]"
out_says several '| `db-backup` | `b2` | QUEUED: handed over to the monitor szczur-radar-monitor |'
out_says several '| `pipeline-trigger` | `t1` | deployed (SUCCESS) |'
pass "three services, one created by the apply (null before): exit 0"

# 2026-10-19: source.checkSuites deployed nothing, and a push meanwhile gave db-backup a deployment
# SKIPPED by watch paths, which the check took for the apply's.
railway_answers no-deploy db-backup "b2 SKIPPED watch, b1 SUCCESS push"
railway_answers no-deploy pipeline-trigger "t4 WAITING push, t3 SUCCESS push, t2 SKIPPED watch, t1 SUCCESS iac"
check_case no-deploy 0 "db-backup pipeline-trigger" '{"db-backup": "b1", "pipeline-trigger": "t1"}' "[$ready]" \
    DEPLOY_VERIFY_DEPLOY_WINDOW=2s
run_says no-deploy '::notice::post-apply check: the apply triggered no deploy of db-backup within 2s'
run_says no-deploy '::notice::post-apply check: the apply triggered no deploy of pipeline-trigger within 2s'
out_says no-deploy '| `db-backup` | — | no deploy (none within 2s of the apply) |'
out_says no-deploy '| `pipeline-trigger` | — | no deploy (none within 2s of the apply) |'
out_says no-deploy '| `/api/ready` | — | 200 |'
run_never_says no-deploy 'handed over'
run_never_says no-deploy '::error::'
api_requests_were no-deploy 1
# One window for all the services: pipeline-trigger, checked after the window, gets one read.
railway_calls_were no-deploy pipeline-trigger 1
pass "only pushes above the snapshot (SKIPPED by watch paths, WAITING, SUCCESS), DEPLOY_VERIFY_DEPLOY_WINDOW=2s: exit 0, \"no deploy\" with a notice, /api/ready asked; the second service decided by one list"
railway_answers push-over-waiting db-backup "b3 SKIPPED watch, b2 WAITING iac, b1 SUCCESS push"
check_case push-over-waiting 0 db-backup '{"db-backup": "b1"}' "[$ready]"
out_says push-over-waiting '| `db-backup` | `b2` | WAITING: handed over to the monitor szczur-radar-monitor |'
run_never_says push-over-waiting 'ended SKIPPED'
pass "a push SKIPPED by watch paths above the apply's deployment WAITING: exit 0, the apply's handed over"
railway_answers push-over-failed szczur-radar-api "a3 SUCCESS push, a2 FAILED iac, a1 SUCCESS push"
check_case push-over-failed 1 szczur-radar-api '{"szczur-radar-api": "a1"}' "[$ready]"
run_says push-over-failed '::error::post-apply check: the deployment a2 of szczur-radar-api ended FAILED after the apply'
out_says push-over-failed '| `szczur-radar-api` | `a2` | **FAILED** |'
api_requests_were push-over-failed 0
pass "a push SUCCESS above the apply's deployment FAILED: exit 1 about the apply's"
railway_answers appears-later pipeline-trigger "t1 SUCCESS push" "t2 SKIPPED watch, t1 SUCCESS push" \
    "t3 SUCCESS iac, t2 SKIPPED watch, t1 SUCCESS push"
check_case appears-later 0 pipeline-trigger '{"pipeline-trigger": "t1"}' "[$ready]" DEPLOY_VERIFY_DEPLOY_WINDOW=5s
run_says appears-later 'post-apply check: pipeline-trigger deployment t3: SUCCESS'
out_says appears-later '| `pipeline-trigger` | `t3` | deployed (SUCCESS) |'
run_never_says appears-later 'triggered no deploy'
railway_calls_were appears-later pipeline-trigger 3
pass "the apply's deployment in the third list within the window (before it nothing, then a push SKIPPED): exit 0, waited for"
railway_answers boundary-lost postgres "p3 SUCCESS iac, p2 REMOVED iac"
check_case boundary-lost 1 postgres '{"postgres": "p1"}' "[$ready]"
run_says boundary-lost "::error::post-apply check: cannot tell the apply's deployment of postgres: its deployment before the apply p1 is not among the newest 50"
out_says boundary-lost '| `postgres` | — | **cannot tell** (`p1` not among the newest 50) |'
api_requests_were boundary-lost 0
pass "the snapshot's deployment not in the list: exit 1, cannot tell"
railway_answers cli-fails postgres fail
check_case cli-fails 1 postgres '{"postgres": "p1"}' "[$ready]" DEPLOY_VERIFY_TIMEOUT=2s DEPLOY_VERIFY_DEPLOY_WINDOW=2s
run_says cli-fails 'post-apply check: railway deployment list -s postgres failed: Failed to fetch'
run_says cli-fails "::error::post-apply check: cannot tell whether the apply deployed postgres within 2s (the last railway deployment list failed)"
pass "railway failing until the limit: exit 1, the failure in the log"
railway_answers cli-fails-after-window db-backup "b1 SUCCESS push" fail
check_case cli-fails-after-window 1 db-backup '{"db-backup": "b1"}' "[$ready]" \
    DEPLOY_VERIFY_TIMEOUT=2s DEPLOY_VERIFY_DEPLOY_WINDOW=2s
run_says cli-fails-after-window "::error::post-apply check: cannot tell whether the apply deployed db-backup within 2s (the last railway deployment list failed)"
out_says cli-fails-after-window '| `db-backup` | — | **unknown** (the last railway deployment list failed) |'
run_never_says cli-fails-after-window 'triggered no deploy'
api_requests_were cli-fails-after-window 0
pass "no deployment of the apply in a list within the window, then railway failing until the limit: exit 1, never \"no deploy\""
railway_answers check-empty db-backup empty
check_case check-empty 1 db-backup '{"db-backup": "b1"}' "[$ready]" DEPLOY_VERIFY_TIMEOUT=2s DEPLOY_VERIFY_DEPLOY_WINDOW=2s
run_says check-empty 'post-apply check: railway deployment list -s db-backup printed no list of deployments'
run_says check-empty "::error::post-apply check: cannot tell whether the apply deployed db-backup within 2s (the last railway deployment list failed)"
run_never_says check-empty 'triggered no deploy'
api_requests_were check-empty 0
pass "an empty answer (exit 0) until the limit: exit 1 as a failed list, never \"no deploy\""
railway_answers limit postgres "p2 DEPLOYING iac, p1 SUCCESS iac"
check_case limit 1 postgres '{"postgres": "p1"}' "[$ready]" DEPLOY_VERIFY_TIMEOUT=2s DEPLOY_VERIFY_DEPLOY_WINDOW=2s
run_says limit '::error::post-apply check: the deployment p2 of postgres did not finish within 2s (last status DEPLOYING)'
pass "DEPLOYING until DEPLOY_VERIFY_TIMEOUT=2s: exit 1"
railway_answers not-ready postgres "p2 SUCCESS iac, p1 SUCCESS iac"
check_case not-ready 1 postgres '{"postgres": "p1"}' "[$unavailable]" DEPLOY_GUARD_WAKE_TIMEOUT=2s
run_says not-ready '::error::post-apply check: /api/ready did not answer 200 within 2s (HTTP 503): the API does not reach its database or is down'
out_says not-ready '| `/api/ready` | — | **HTTP 503** |'
pass "/api/ready 503 until DEPLOY_GUARD_WAKE_TIMEOUT=2s: exit 1"
check_case ready-wakes 0 "" '{}' "[$unavailable, $ready]"
run_says ready-wakes 'post-apply check: /api/ready: HTTP 503; retrying'
out_says ready-wakes 'No service to redeploy.'
[[ ! -f $tmp/ready-wakes.railway/calls ]] || { show_run ready-wakes; die "ready-wakes: railway was called"; }
pass "an empty restarts list: only /api/ready (503, then 200), no railway call: exit 0"

printf 'postgres\n' >"$tmp/bad.restarts"
printf '{"postgres": "p1"}\n' >"$tmp/bad.before.json"
run_verify no-arguments 2 --
run_says no-arguments 'usage: verify-apply.sh snapshot <restarts.txt> | check'
run_verify bad-mode 2 -- apply "$tmp/bad.restarts"
run_verify no-restarts 2 -- snapshot "$tmp/no-such-restarts.txt"
run_says no-restarts 'is not a readable file'
printf '["p1"]\n' >"$tmp/not-a-snapshot.json"
run_verify not-in-snapshot 2 -- check "$tmp/bad.restarts" "$tmp/not-a-snapshot.json" "http://127.0.0.1:1"
run_says not-in-snapshot 'is not a snapshot of verify-apply.sh'
printf '{"db-backup": "b1"}\n' >"$tmp/other.before.json"
run_verify missing-service 2 -- check "$tmp/bad.restarts" "$tmp/other.before.json" "http://127.0.0.1:1"
run_says missing-service 'has no deployment of postgres (no snapshot before the apply)'
run_verify no-environment 2 RAILWAY_ENVIRONMENT_ID= -- snapshot "$tmp/bad.restarts"
run_says no-environment 'RAILWAY_ENVIRONMENT_ID is not set (the environment is selected by ID, never by railway link)'
run_verify bad-duration 2 DEPLOY_VERIFY_TIMEOUT=15min -- snapshot "$tmp/bad.restarts"
run_says bad-duration 'DEPLOY_VERIFY_TIMEOUT must be a duration'
printf 'service:postgres\n' >"$tmp/bad-name.restarts"
run_verify bad-name 2 -- snapshot "$tmp/bad-name.restarts"
run_says bad-name 'is not a service name'
run_verify bad-url 2 -- check "$tmp/bad.restarts" "$tmp/bad.before.json" "https://example.test/api"
run_says bad-url 'the web address must be an http(s) origin'
pass "bad invocations (arguments, files, snapshot, RAILWAY_ENVIRONMENT_ID, duration, service name, web address): exit 2"
run_verify window-too-long 2 DEPLOY_VERIFY_DEPLOY_WINDOW=16m -- snapshot "$tmp/bad.restarts"
run_says window-too-long '::error::post-apply check: DEPLOY_VERIFY_DEPLOY_WINDOW (16m) must not be longer than DEPLOY_VERIFY_TIMEOUT (15m)'
run_verify bad-window 2 DEPLOY_VERIFY_DEPLOY_WINDOW=2min -- snapshot "$tmp/bad.restarts"
run_says bad-window "DEPLOY_VERIFY_DEPLOY_WINDOW must be a duration such as 30s, 3m or 2h (it is '2min')"
for name in window-too-long bad-window; do
    [[ ! -f $tmp/$name.railway/calls ]] || { show_run "$name"; die "$name: railway was called"; }
done
run_verify window-in-check 2 DEPLOY_VERIFY_TIMEOUT=1m -- check "$tmp/bad.restarts" "$tmp/bad.before.json" "http://127.0.0.1:1"
run_says window-in-check 'DEPLOY_VERIFY_DEPLOY_WINDOW (2m) must not be longer than DEPLOY_VERIFY_TIMEOUT (1m)'
pass "a DEPLOY_VERIFY_DEPLOY_WINDOW longer than DEPLOY_VERIFY_TIMEOUT (also the default 2m) or not a duration: exit 2, in the snapshot before any railway call (no apply)"

echo
echo "gate.sh step summary of the real szczur-radar-pr-81 plan (what the railway-config job shows):"
sed 's/^/    /' "$tmp/pr-env-checksuites.md"

printf '\nAll checks passed.\n'
