#!/usr/bin/env bash
# Local test of the deploy gate, without Railway or GitHub (S-14, safe-deploy-gate; see README.md).
# gate.sh classifies each plan in fixtures/ (the format of `railway config plan --json`, values
# redacted). Every rule has a case it allows and a case it refuses:
#   1. the plan is ok and targets the expected environment and project, else refused;
#   2. a destructive change is refused (variable.delete, resource.delete);
#   3. only resource.create, resource.update, variable.set and domain.create pass, whatever the severity
#      (variable.delete or an unknown kind marked safe is refused);
#   4. a source change is refused, except one that only turns source.checkSuites to true;
#   5. a change without a kind and a resource.update without a field are refused (unknown format);
#   6. a change without deployEffect is announced as a restart, deployEffect "none" is not.
# Also: the step summary (number of changes, the changes, the restarts), the restarts file
# (GATE_RESTARTS_FILE), the `::error::` lines, and exit code 2 for a bad invocation or an unreadable
# plan. Needs bash and jq only. Run from anywhere: tools/deploy-gate/test.sh
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

# Case $1's restarts file lists exactly the addresses after it, in that order.
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
pass "variable.delete (destructive): exit 1"
check_gate resource-delete 1
log_says resource-delete '::error::Delete bucket Postgres-PITR: destructive change'
pass "resource.delete (destructive): exit 1"
check_gate variable-set 0
summary_says variable-set '| Update variable szczur-radar-api.PIPELINE_LLM_CONCURRENCY | `variable.set` | `service.szczur-radar-api` | safe | deploy | allowed |'
summary_says variable-set '**Restarts:** `service.szczur-radar-api`'
restarts_are variable-set service.szczur-radar-api
pass "variable.set (safe): exit 0, the service announced as restarted"

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
restarts_are resource-create service.db-backup
pass "resource.create of a bucket and a service: exit 0, only the service restarts"
check_gate domain-create 0
restarts_are domain-create
pass "domain.create: exit 0"
check_gate mixed 1
errors_are mixed 1
log_says mixed '::error::Delete variable db-backup.BACKUP_TIMEOUT'
log_never_says mixed '::error::Update variable db-backup.BACKUP_KEEP_DAILY'
summary_says mixed '| Update variable db-backup.BACKUP_KEEP_DAILY | `variable.set` | `service.db-backup` | safe | deploy | allowed |'
pass "an allowed and a refused change: exit 1, only the refused one in the errors"

step "4. source changes are refused, except turning on Wait for CI"
check_gate source-branch 1
log_says source-branch '::error::Update szczur-radar-api source.branch: source change (only source.checkSuites → true may pass)'
pass "source.branch: exit 1"
check_gate checksuites-enable 0
summary_says checksuites-enable '| Update pipeline-trigger source.checkSuites | `resource.update` `source` | `service.pipeline-trigger` | safe | deploy | allowed |'
restarts_are checksuites-enable service.pipeline-trigger service.db-backup
pass "source.checkSuites null → true and false → true: exit 0, both crons announced"
check_gate checksuites-disable 1
log_says checksuites-disable 'source change'
pass "source.checkSuites true → null: exit 1"
check_gate checksuites-with-branch 1
log_says checksuites-with-branch 'source change'
pass "source.checkSuites → true together with source.branch: exit 1"

step "5. an unknown plan format is refused"
check_gate update-without-field 1
log_says update-without-field 'resource.update without a field (unknown plan format)'
pass "resource.update without a field: exit 1"
check_gate change-without-kind 1
log_says change-without-kind 'change without a kind (unknown plan format)'
pass "a change without a kind: exit 1"
check_gate postgres-limit-override 0
summary_says postgres-limit-override '`resource.update` `deploy` | `database.postgres` | safe | deploy | allowed |'
summary_says postgres-limit-override '**Restarts:** `database.postgres` (an apply redeploys them)'
restarts_are postgres-limit-override database.postgres
pass "limitOverride on Postgres (resource.update with a field): exit 0, the restart announced"

step "6. restarts: a missing deployEffect counts as a deploy"
check_gate no-deploy-effect 0
summary_says no-deploy-effect '| deploy (not stated) | allowed |'
restarts_are no-deploy-effect service.szczur-radar-web
pass "no deployEffect: announced as a restart; deployEffect none (groupId): not announced"

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

echo
echo "gate.sh step summary of the checksuites-enable case (what the railway-config job shows):"
sed 's/^/    /' "$tmp/checksuites-enable.md"

printf '\nAll checks passed.\n'
