#!/usr/bin/env bash
# Local test of the daily pipeline trigger, without Railway (S-05, daily-scheduled-run-freshness; see
# README.md). The image runs in a Docker network next to a stub API: a small Python HTTP server that
# answers /internal/pipeline-runs like the API, with answers scripted per case. Checks:
#   1. the image runs as a non-root user; outside production (RAILWAY_ENVIRONMENT_NAME) a run is
#      skipped without a request;
#   2. 202 → succeeded or partial: exit 0; failed, interrupted (the log gives its cause), or a failed
#      retry run before a succeeded range run: exit 1; an empty range run (the API ends it failed with
#      fetched 0, skipped 0): exit 1; a succeeded run with skipped elements: exit 0, skipped in its
#      line; a running run logs a heartbeat with its step and progress every
#      TRIGGER_HEARTBEAT_INTERVAL, without an extra request, and an API without those fields gives "?"
#      in their place;
#   3. refused connections, then 503 without a body, then 202 (the API waking up): exit 0; 409
#      pipeline_busy, then 202: exit 0;
#   4. 401 (a wrong token) and 503 source_not_configured: exit 1 after one request; no answer to a POST
#      the API got (TRIGGER_REQUEST_TIMEOUT): exit 1 without a second POST; every time limit
#      (TRIGGER_WAKE_TIMEOUT, TRIGGER_BUSY_TIMEOUT, TRIGGER_RUN_TIMEOUT, the whole run's
#      TRIGGER_TIMEOUT): exit 1; a missing variable or a bad duration: exit 2;
#   5. the tokens never appear in the logs.
# Needs Docker. Run from anywhere: tools/pipeline-trigger/test.sh
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
readonly image=szczur-radar-pipeline-trigger
readonly stub_image=python:3.14-alpine
readonly id=sr-pipeline-trigger-test-$$
readonly net=$id
# Throwaway tokens, checked below never to appear in a log.
token=test-$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
wrong_token=wrong-$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
tmp=$(mktemp -d)

step() { printf '\n== %s\n' "$*"; }
pass() { printf 'PASS %s\n' "$*"; }
die() { printf 'FAIL %s\n' "$*" >&2; exit 1; }

cleanup() {
    # Every container of this test is named "$id-…" (the stubs and each trigger run).
    docker ps -aq --filter "name=$id-" | xargs docker rm -f >/dev/null 2>&1 || true
    docker network rm "$net" >/dev/null 2>&1 || true
    rm -rf "$tmp"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# The stub API. STUB_CASE (JSON) scripts it:
#   post: the answers to POST /daily in turn, the last one repeating: 202 (the runs below), 409, 503
#         (with their API error codes), "503-empty" (no JSON body, as while the API wakes) or "hang"
#         (the request is logged, then no answer for 30 s);
#   runs: [{kind, statuses}]: GET /{id} answers the statuses in turn, the last one repeating; a status
#         is its name, or an object {status, <field>: <value>, …} whose fields replace the answer's;
#   old_api: true leaves out the fields the API added in S-06 (startedAt, lastActivityAt,
#         interruptionCause, step, assessTotal) and S-08 (skipped), as an API from before them;
#   listen_after: seconds before it accepts connections (until then they are refused).
# Every request without "Bearer $STUB_TOKEN" gets 401 not_authenticated. One line per request on
# stdout: "<method> <path> <status>".
read -r -d '' stub_code <<'PY' || true
import json, os, time, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

case = json.loads(os.environ["STUB_CASE"])
token = os.environ["STUB_TOKEN"]
details = {409: "pipeline_busy", 503: "source_not_configured"}
runs = [dict(run, id=str(uuid.uuid4())) for run in case.get("runs", [])]
by_id = {run["id"]: run for run in runs}
posts = case.get("post", [202])
post_count = [0]


class Api(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def answer(self, status, body=None):
        data = b"upstream not ready" if body is None else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/plain" if body is None else "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        print(f"{self.command} {self.path} {status}", flush=True)

    def authorized(self):
        if self.headers.get("Authorization") == "Bearer " + token:
            return True
        self.answer(401, {"detail": "not_authenticated"})
        return False

    def do_POST(self):
        if not self.authorized():
            return
        if self.path != "/internal/pipeline-runs/daily":
            return self.answer(404, {"detail": "not_found"})
        status = posts[min(post_count[0], len(posts) - 1)]
        post_count[0] += 1
        if status == "hang":
            print(f"{self.command} {self.path} hang", flush=True)
            time.sleep(30)
            return
        if status == 202:
            return self.answer(202, [{"id": run["id"], "kind": run["kind"]} for run in runs])
        if status == "503-empty":
            return self.answer(503)
        self.answer(status, {"detail": details[status]})

    def do_GET(self):
        if not self.authorized():
            return
        run = by_id.get(self.path.removeprefix("/internal/pipeline-runs/"))
        if run is None:
            return self.answer(404, {"detail": "not_found"})
        statuses = run["statuses"]
        entry = statuses.pop(0) if len(statuses) > 1 else statuses[0]
        fields = entry if isinstance(entry, dict) else {"status": entry}
        status = fields["status"]
        queued, running = status == "queued", status == "running"
        ended = not (queued or running)
        body = {"id": run["id"], "kind": run["kind"], "status": status,
                "startedAt": None if queued else "2026-10-12T00:30:09Z",
                "finishedAt": "2026-10-12T00:41:07Z" if ended else None,
                "lastActivityAt": None if queued else "2026-10-12T00:40:58Z" if running else "2026-10-12T00:41:07Z",
                "interruptionCause": "deploy" if status == "interrupted" else None,
                "step": "assess" if running else None, "assessTotal": None if queued else 251,
                "fetched": 270, "newPosts": 251, "assessedOk": 249, "assessedFailed": 2, "processingErrors": 0,
                "dead": 0, "skipped": 0}
        body.update(fields)
        if case.get("old_api"):
            for name in ("startedAt", "lastActivityAt", "interruptionCause", "step", "assessTotal", "skipped"):
                del body[name]
        self.answer(200, body)


time.sleep(case.get("listen_after", 0))
server = ThreadingHTTPServer(("", 8000), Api)
print("listening", flush=True)
server.serve_forever()
PY

# Starts the stub of case $1 answering $2 (STUB_CASE) and waits until it listens, unless the case
# makes it listen late.
start_stub() {
    local name=$1 case=$2
    STUB_CASE=$case STUB_TOKEN=$token STUB_CODE=$stub_code docker run --detach --name "$id-stub-$name" \
        --network "$net" -e STUB_CASE -e STUB_TOKEN -e STUB_CODE "$stub_image" \
        python -u -c 'import os; exec(os.environ["STUB_CODE"])' >/dev/null
    [[ $case != *listen_after* ]] || return 0
    for attempt in $(seq 1 50); do
        if docker logs "$id-stub-$name" 2>&1 | grep -qx listening; then
            return 0
        fi
        [ "$attempt" -lt 50 ] || { docker logs "$id-stub-$name"; die "$name: the stub API did not start"; }
        sleep 0.2
    done
}

# One trigger run of case $1 against its stub, with the token $2 (empty: unset) and the `docker run`
# options after it. Log in $tmp/$1.log; sets REPLY to the exit code.
run_trigger() {
    local name=$1 trigger_token=$2
    shift 2
    REPLY=0
    PIPELINE_TRIGGER_TOKEN=$trigger_token docker run --rm --name "$id-$name" --network "$net" \
        -e API_PRIVATE_DOMAIN="$id-stub-$name" -e API_PORT=8000 -e PIPELINE_TRIGGER_TOKEN \
        -e TRIGGER_POLL_INTERVAL=1s -e TRIGGER_BUSY_INTERVAL=1s "$@" "$image" >"$tmp/$name.log" 2>&1 || REPLY=$?
}

show() {
    printf -- '--- trigger log (%s)\n' "$1"
    cat "$tmp/$1.log"
    if [[ -f $tmp/$1.requests ]]; then
        printf -- '--- requests to the stub API\n'
        cat "$tmp/$1.requests"
    fi
}

# Case $1: the stub answers $3 (STUB_CASE), the trigger gets the token $4 and the `docker run`
# options after it and must exit with $2. The stub's requests end up in $tmp/$1.requests.
check_case() {
    local name=$1 expected=$2 case=$3 trigger_token=$4
    shift 4
    start_stub "$name" "$case"
    run_trigger "$name" "$trigger_token" "$@"
    docker logs "$id-stub-$name" 2>&1 | grep -vx listening >"$tmp/$name.requests" || true
    docker rm -f "$id-stub-$name" >/dev/null
    [[ $REPLY == "$expected" ]] || { show "$name"; die "$name: exit code $REPLY, expected $expected"; }
}

# Case $1's log says $2.
log_says() {
    grep -qF -- "$2" "$tmp/$1.log" || { show "$1"; die "$1: the log does not say '$2'"; }
}

# Case $1's stub got $2 POST and $3 GET requests.
requests_were() {
    local posts gets
    posts=$(grep -c '^POST ' "$tmp/$1.requests" || true)
    gets=$(grep -c '^GET ' "$tmp/$1.requests" || true)
    [[ $posts == "$2" && $gets == "$3" ]] \
        || { show "$1"; die "$1: $posts POST and $gets GET requests, expected $2 and $3"; }
}

step "build $image"
docker build --quiet --tag "$image" "$here" >/dev/null
docker pull --quiet "$stub_image" >/dev/null
docker network create "$net" >/dev/null
pass "image built, stub API image $stub_image"

step "1. non-root, and skipped outside production"
uid=$(docker run --rm --entrypoint id "$image" -u)
[ "$uid" != "0" ] || die "the image runs as root"
pass "the image runs as uid $uid"
check_case skipped 0 '{"runs": [{"kind": "range", "statuses": ["succeeded"]}]}' "$token" \
    -e RAILWAY_ENVIRONMENT_NAME=szczur-radar-pr-1
log_says skipped 'skipped: the daily run is started only in production (this is szczur-radar-pr-1)'
requests_were skipped 0 0
pass "RAILWAY_ENVIRONMENT_NAME=szczur-radar-pr-1: skipped, exit 0, no request"

step "2. the runs' result is the exit code, and a heartbeat while they run"
check_case succeeded 0 '{"runs": [{"kind": "range", "statuses": ["queued", "running", "succeeded"]}]}' "$token" \
    -e RAILWAY_ENVIRONMENT_NAME=production
log_says succeeded ': succeeded (fetched 270, skipped 0, new posts 251, assessed ok 249, assessed failed 2, processing errors 0, dead 0)'
log_says succeeded 'OK: range succeeded'
requests_were succeeded 1 3
pass "production: 202 → queued → running → succeeded, exit 0"
check_case partial 0 '{"runs": [{"kind": "range", "statuses": ["running", "partial"]}]}' "$token" \
    -e RAILWAY_ENVIRONMENT_NAME=szczur-radar-pr-1 -e TRIGGER_IN_ANY_ENVIRONMENT=true
log_says partial 'OK: range partial'
pass "a PR env with TRIGGER_IN_ANY_ENVIRONMENT=true runs; partial → exit 0"
check_case failed 1 '{"runs": [{"kind": "range", "statuses": ["running", "failed"]}]}' "$token"
log_says failed 'FAILED: a run did not succeed: range failed'
pass "failed → exit 1"
check_case retry-failed 1 '{"runs": [{"kind": "retry", "statuses": ["running", "failed"]},
    {"kind": "range", "statuses": ["queued", "queued", "running", "succeeded"]}]}' "$token"
log_says retry-failed 'FAILED: a run did not succeed: retry failed, range succeeded'
pass "retry failed, range succeeded → both awaited, exit 1"
check_case interrupted 1 '{"runs": [{"kind": "range", "statuses": ["running", "interrupted"]}]}' "$token"
log_says interrupted ': interrupted (fetched 270, skipped 0, new posts 251, assessed ok 249, assessed failed 2, processing errors 0, dead 0) (cause deploy)'
log_says interrupted 'FAILED: a run did not succeed: range interrupted'
requests_were interrupted 1 2
pass "interrupted → its cause in the log, exit 1"
# An empty range run: the source gave no post to store, so the API ends it failed (S-08).
check_case empty 1 '{"runs": [{"kind": "range", "statuses": ["running", {"status": "failed", "fetched": 0,
    "skipped": 0, "newPosts": 0, "assessedOk": 0, "assessedFailed": 0, "assessTotal": null}]}]}' "$token"
log_says empty ': failed (fetched 0, skipped 0, new posts 0, assessed ok 0, assessed failed 0, processing errors 0, dead 0)'
log_says empty 'FAILED: a run did not succeed: range failed'
requests_were empty 1 2
pass "an empty range run (failed, fetched 0, skipped 0) → its counters in the log, exit 1"
# Skipped elements next to good posts are only a warning in the app: the run succeeds, the cron too.
check_case skipped-warning 0 '{"runs": [{"kind": "range", "statuses": ["running",
    {"status": "succeeded", "skipped": 3}]}]}' "$token"
log_says skipped-warning ': succeeded (fetched 270, skipped 3, new posts 251, assessed ok 249, assessed failed 2, processing errors 0, dead 0)'
log_says skipped-warning 'OK: range succeeded'
requests_were skipped-warning 1 2
pass "succeeded with skipped 3 → skipped in the log, exit 0"
check_case heartbeat 0 '{"runs": [{"kind": "range", "statuses": ["running",
    {"status": "running", "step": "assess", "assessTotal": 10, "assessedOk": 2, "assessedFailed": 1},
    {"status": "running", "step": "assess", "assessTotal": 10, "assessedOk": 2, "assessedFailed": 1},
    "succeeded"]}]}' "$token" -e TRIGGER_HEARTBEAT_INTERVAL=1s
log_says heartbeat ': running, step assess, assessed 3/10 (ok 2, failed 1, errors 0), last activity 2026-10-12T00:40:58Z'
log_says heartbeat 'OK: range succeeded'
# None at the status change (the first answer), one at each later answer while it runs.
beats=$(grep -c ', step ' "$tmp/heartbeat.log" || true)
[[ $beats == 2 ]] || { show heartbeat; die "heartbeat: $beats heartbeat lines, expected 2"; }
requests_were heartbeat 1 4
pass "TRIGGER_HEARTBEAT_INTERVAL=1s: a heartbeat with step and progress at each later poll, no extra request"
check_case queued-heartbeat 0 '{"runs": [{"kind": "range", "statuses": ["queued", "queued", "queued", "succeeded"]}]}' \
    "$token" -e TRIGGER_HEARTBEAT_INTERVAL=1s
log_says queued-heartbeat ': queued'
# A queued run has no progress to show: the run before it logs the heartbeats.
beats=$(grep -c ', step ' "$tmp/queued-heartbeat.log" || true)
[[ $beats == 0 ]] || { show queued-heartbeat; die "queued-heartbeat: $beats heartbeat lines, expected none"; }
requests_were queued-heartbeat 1 4
pass "a queued run logs its status once and no heartbeat"
check_case old-api 1 '{"old_api": true, "runs": [{"kind": "range", "statuses": ["running", "running", "interrupted"]}]}' \
    "$token" -e TRIGGER_HEARTBEAT_INTERVAL=1s
log_says old-api ': running, step ?, assessed 251/? (ok 249, failed 2, errors 0), last activity ?'
log_says old-api ': interrupted (fetched 270, skipped ?, new posts 251, assessed ok 249, assessed failed 2, processing errors 0, dead 0) (cause ?)'
log_says old-api 'FAILED: a run did not succeed: range interrupted'
requests_were old-api 1 3
pass "an API without the step, progress and skipped fields: \"?\" in the heartbeat, the cause and skipped, exit 1"

step "3. waking the API, and a busy pipeline"
check_case wake 0 '{"listen_after": 4, "post": ["503-empty", 202],
    "runs": [{"kind": "range", "statuses": ["succeeded"]}]}' "$token"
log_says wake 'waking the API: no answer yet (curl: (7)'
log_says wake 'the API answers (after'
requests_were wake 2 1
pass "connections refused for 4 s, then 503 without a body, then 202 → exit 0"
check_case busy 0 '{"post": [409, 409, 202], "runs": [{"kind": "range", "statuses": ["succeeded"]}]}' "$token"
log_says busy 'busy: a run is queued or running (HTTP 409 pipeline_busy)'
requests_were busy 3 1
pass "409 pipeline_busy twice, then 202 → exit 0"

step "4. refusals, time limits and configuration"
check_case unauthorized 1 '{"runs": [{"kind": "range", "statuses": ["succeeded"]}]}' "$wrong_token"
log_says unauthorized 'FAILED: the API refused the run: HTTP 401 not_authenticated'
requests_were unauthorized 1 0
pass "a wrong token: 401 → exit 1, no retry"
check_case no-source 1 '{"post": [503]}' "$token"
log_says no-source 'FAILED: the API refused the run: HTTP 503 source_not_configured'
requests_were no-source 1 0
pass "503 source_not_configured → exit 1, no retry"
check_case no-answer 1 '{"post": ["hang", 202], "runs": [{"kind": "range", "statuses": ["succeeded"]}]}' \
    "$token" -e TRIGGER_REQUEST_TIMEOUT=2s
log_says no-answer 'FAILED: no answer to the start after the request was sent (curl: (28)'
log_says no-answer 'the run may have started; check /admin'
requests_were no-answer 1 0
pass "no answer to a POST the API got (TRIGGER_REQUEST_TIMEOUT=2s) → exit 1, no second POST"
check_case wake-timeout 1 '{"listen_after": 60}' "$token" -e TRIGGER_WAKE_TIMEOUT=3s
log_says wake-timeout 'FAILED: waking the API: the API did not answer within 3s (curl: (7)'
pass "connections still refused after TRIGGER_WAKE_TIMEOUT=3s → exit 1"
check_case busy-timeout 1 '{"post": [409]}' "$token" -e TRIGGER_BUSY_TIMEOUT=2s
log_says busy-timeout 'FAILED: the pipeline was still busy after 2s'
pass "409 pipeline_busy after TRIGGER_BUSY_TIMEOUT=2s → exit 1"
check_case run-timeout 1 '{"runs": [{"kind": "range", "statuses": ["running"]}]}' "$token" \
    -e TRIGGER_RUN_TIMEOUT=3s
log_says run-timeout 'FAILED: not ended within 3s: range '
pass "a run still running after TRIGGER_RUN_TIMEOUT=3s → exit 1"
check_case time-limit 1 '{"runs": [{"kind": "range", "statuses": ["running"]}]}' "$token" \
    -e TRIGGER_TIMEOUT=3s
log_says time-limit 'FAILED: the run exceeded 3s'
pass "the whole run over TRIGGER_TIMEOUT=3s → exit 1"
check_case missing-token 2 '{"runs": [{"kind": "range", "statuses": ["succeeded"]}]}' ""
log_says missing-token 'FAILED: PIPELINE_TRIGGER_TOKEN is not set'
requests_were missing-token 0 0
pass "PIPELINE_TRIGGER_TOKEN unset → exit 2, no request"
check_case bad-heartbeat 2 '{"runs": [{"kind": "range", "statuses": ["succeeded"]}]}' "$token" \
    -e TRIGGER_HEARTBEAT_INTERVAL=5x
log_says bad-heartbeat 'FAILED: TRIGGER_HEARTBEAT_INTERVAL must be a duration such as 30s, 3m or 2h'
requests_were bad-heartbeat 0 0
pass "TRIGGER_HEARTBEAT_INTERVAL=5x → exit 2, no request"

step "5. no secrets in the logs"
if grep -lF -e "$token" -e "$wrong_token" "$tmp"/*.log; then
    die "a token appears in a trigger log"
fi
pass "neither token is in any trigger log"

echo
echo "trigger.sh log of the succeeded case (what Railway shows for a cron run):"
sed 's/^/    /' "$tmp/succeeded.log"

printf '\nAll checks passed.\n'
