#!/usr/bin/env bash
# Local end-to-end test of the backup tool, without Railway and S3 (F-01, production-data-gate; see
# README.md). A Postgres 18 database (production's image) gets the repo's Flyway migrations, a
# flyway_schema_history like Flyway's and sample rows including an activation link, a notification and
# a session (tables.sh is checked first: 11 required and 7 counted tables at V9, a malformed entry
# fails); then:
#   1. backup.sh into a local rclone remote: dump and manifest uploaded (counts of every counted table
#      in tables.sh, notifications and pipeline_run_steps among them, never activation_tokens; compared
#      with no earlier manifest), no session data in the dump, restore check passed (so every migrated
#      table is classified), monthly copy made;
#   2. retention: 20 older daily and 15 older monthly dumps are cut to 14 and 12; a file with another
#      name is left alone; the run compares its counts with the first run's manifest;
#   3. shrink and consent: a manifest dated in the future is skipped; 5 deleted posts fail the run before
#      the dump (posts 35 < 40) with the BACKUP_ACCEPT_SHRINK value to set; that value lets the next run
#      through (accepted_shrink); the run after it ignores the stale value, which lets no new shrink through;
#      a bucket with only future-dated manifests, or a newest manifest without counts, fails the run, and
#      renaming that manifest .broken lets the next run through;
#   4. a dump of a second database at Flyway V5 (before activation_tokens and notifications) passes
#      restore-check.sh; a truncated dump fails it; a run over its time limit fails; outside production
#      (RAILWAY_ENVIRONMENT_NAME) a run is skipped; a table tables.sh does not classify (tables: unknown)
#      and a missing required table (tables: missing) fail the restore check, which uploads nothing;
#   5. an empty posts table fails the run before the dump, even with BACKUP_ACCEPT_SHRINK naming the
#      newest manifest (last: it empties the database);
#   6. nas-retention.sh --dry-run names the right files and deletes nothing, then a real run prunes;
#      other names and future-dated dumps are left alone;
#   7. the throwaway database password and the check-in key never appear in the logs.
# Check-ins (F-04, out-of-app-alert-channel) go to a stub of healthchecks.io over https (a throwaway
# certificate) and are checked at the runs above: the first run checks in /start, then /0 with its OK
# line (at an HTTPS:// address: the rule takes https in any case); the shrink refusal /1 with its line;
# the run over its time limit /124 with the time limit's line; a skipped run nothing; an unreachable
# check-in address leaves the run's result as it is; an address outside the rule (S-25: not https, with a
# query, without a host name) gives a warning and no check-in, in runs without PGHOST that end before the
# dump.
# Needs Docker. Run from anywhere: tools/db-backup/test.sh
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
migrations=$(cd "$here/../.." && pwd)/szczur-radar-api/src/main/resources/db/migration
readonly image=szczur-radar-db-backup
readonly db_image=ghcr.io/railwayapp-templates/postgres-ssl:18
readonly id=sr-db-backup-test-$$
readonly net=$id db=$id-db out=$id-out
readonly db_user=backup_test db_name=backup_test posts=40
# The stub of healthchecks.io: its container, its name (a network alias, in its certificate), its image,
# and the CA option for a run that checks in to it (the certificate is on the backup volume).
readonly ping_stub=$id-ping ping_host=hc-ping.test stub_image=python:3.14-alpine
readonly ping_ca=CURL_CA_BUNDLE=/out/ping/ca.pem
# A throwaway password and check-in key, checked below never to appear in a log.
db_password=test-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')
ping_key=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
# Run <name> checks in at $ping_url/<name>, so the check-ins of each run can be told apart.
readonly ping_url=https://$ping_host:8443/ping/$ping_key
tmp=$(mktemp -d)

step() { printf '\n== %s\n' "$*"; }
pass() { printf 'PASS %s\n' "$*"; }
die() { printf 'FAIL %s\n' "$*" >&2; exit 1; }

cleanup() {
    # Every container of this test is named "$id-…" (the database, the check-in stub and each backup run).
    docker ps -aq --filter "name=$id-" | xargs docker rm -f >/dev/null 2>&1 || true
    docker volume rm "$out" >/dev/null 2>&1 || true
    docker network rm "$net" >/dev/null 2>&1 || true
    rm -rf "$tmp"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# psql in the database container (local socket, trust): psql_in <database> …, db_psql … on the test database.
psql_in() {
    local database=$1
    shift
    docker exec -i "$db" psql -X -q -v ON_ERROR_STOP=1 -U "$db_user" -d "$database" "$@"
}
db_psql() { psql_in "$db_name" "$@"; }
# The repo's migrations in database $1 (up to version $2 if given), with a flyway_schema_history like
# the one `migrate` fills (Flyway 11, PostgreSQL). Sets rank (the number applied) and last_version.
migrate() {
    local database=$1 up_to=${2:-} file script version description
    psql_in "$database" <<'SQL'
CREATE TABLE flyway_schema_history (
    installed_rank INTEGER       NOT NULL,
    version        VARCHAR(50),
    description    VARCHAR(200)  NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    script         VARCHAR(1000) NOT NULL,
    checksum       INTEGER,
    installed_by   VARCHAR(100)  NOT NULL,
    installed_on   TIMESTAMP     NOT NULL DEFAULT now(),
    execution_time INTEGER       NOT NULL,
    success        BOOLEAN       NOT NULL,
    CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
);
SQL
    rank=0
    last_version=""
    for file in $(ls "$migrations" | grep -E '^V[0-9]+__.+\.sql$' | sed -E 's/^V([0-9]+)__/\1 &/' | sort -n | cut -d ' ' -f 2); do
        script=${file%.sql}
        version=${script%%__*}
        version=${version#V}
        if [ -n "$up_to" ] && [ "$version" -gt "$up_to" ]; then
            break
        fi
        rank=$((rank + 1))
        last_version=$version
        description=$(printf '%s' "${script#*__}" | tr _ ' ')
        psql_in "$database" <"$migrations/$file"
        psql_in "$database" -c "insert into flyway_schema_history (installed_rank, version, description, type,
            script, checksum, installed_by, execution_time, success)
            values ($rank, '$last_version', '$description', 'SQL', '$file', 0, '$db_user', 0, true)"
    done
    [ "$rank" -gt 0 ] || die "no migrations in $migrations"
}
# A bash script (stdin) in the image, with the backup volume at /out.
in_image() { docker run --rm -i -v "$out:/out" --entrypoint bash "$image" -s -- "$@"; }
# One backup run against the test database; output in $tmp/$1.log, returns backup.sh's exit code.
run_backup() {
    local log=$tmp/$1.log
    shift
    PGPASSWORD=$db_password docker run --rm --name "$id-$(basename "$log" .log)" --network "$net" -v "$out:/out" \
        -e PGHOST="$db" -e PGPORT=5432 -e PGUSER="$db_user" -e PGPASSWORD -e PGDATABASE="$db_name" \
        -e BACKUP_REMOTE=:local:/out "$@" "$image" >"$log" 2>&1
}
# The name (szczur-radar-<ts>, no extension) of the daily dump that run $1 uploaded, from its log.
uploaded() { sed -nE 's/.* backup: uploaded daily\/(.+)\.dump and its manifest$/\1/p' "$tmp/$1.log"; }
# check_manifest <run> <jq filter> [<jq option>…]: jq -e (the image's) on the manifest that run uploaded.
check_manifest() {
    local base
    base=$(uploaded "$1")
    [ -n "$base" ] || { echo "run $1 uploaded nothing"; return 1; }
    shift
    in_image "$base" "$@" <<'SH'
set -euo pipefail
manifest=/out/daily/$1.manifest.json filter=$2
shift 2
jq -e "$@" "$filter" "$manifest" >/dev/null || { echo "manifest:"; cat "$manifest"; exit 1; }
SH
}
# The newest manifest in daily/ by name, without its extension (the 2020 ones of step 2 sort first).
newest_manifest() { in_image <<<'cd /out/daily && LC_ALL=C ls | grep -E "^szczur-radar-[0-9]{8}T[0-9]{6}Z\.manifest\.json$" | tail -n 1 | sed "s/\.manifest\.json$//"'; }

# The stub of healthchecks.io: answers 200 to every POST on port 8443 over https, with the certificate
# from the backup volume. One line per check-in on stdout: "POST <path> 200 <body as a JSON string>".
read -r -d '' ping_stub_code <<'PY' || true
import json, ssl
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Ping(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length") or 0)).decode(errors="replace")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"OK")
        print(f"{self.command} {self.path} 200 {json.dumps(body)}", flush=True)


tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
tls.load_cert_chain("/out/ping/ca.pem", "/out/ping/key.pem")
server = ThreadingHTTPServer(("", 8443), Ping)
server.socket = tls.wrap_socket(server.socket, server_side=True)
print("listening", flush=True)
server.serve_forever()
PY
# Run $1's check-ins at the stub were $2, in this order (start or the exit code, separated by spaces;
# empty: none).
pings_were() {
    local pings
    pings=$(docker logs "$ping_stub" 2>&1 | sed -nE "s|^POST /ping/$ping_key/$1/([^ ]+) .*|\1|p" | paste -sd ' ' -)
    [[ $pings == "$2" ]] || { cat "$tmp/$1.log"; die "$1: check-ins '$pings', expected '$2'"; }
}
# The body of run $1's last check-in says $2.
last_ping_says() {
    local body
    body=$(docker logs "$ping_stub" 2>&1 | grep "^POST /ping/$ping_key/$1/" | tail -n 1 | cut -d ' ' -f 4-)
    [[ $body == *"$2"* ]] || { cat "$tmp/$1.log"; die "$1: the last check-in's body $body does not say '$2'"; }
}

step "build $image"
docker build --quiet --tag "$image" "$here" >/dev/null
pass "image built"
# tables.sh: V9's classification, and a malformed entry fails instead of dropping its table.
tables_v9=$(docker run --rm --entrypoint bash "$image" -c 'source /usr/local/bin/tables.sh
    echo "$(required_tables 9 | wc -l) $(counted_tables 9 | wc -l)"')
[[ $tables_v9 == "11 7" ]] || die "tables.sh at V9: $tables_v9 required and counted tables, not 11 7"
for bad in 's/"notifications 9 counted"/"notifications 9x counted"/' 's/"users 1 counted"/"users 1 counted 12"/'; do
    docker run --rm -e BAD="$bad" --entrypoint bash "$image" -c 'sed "$BAD" /usr/local/bin/tables.sh >/tmp/t.sh
        grep -q "9x counted\|counted 12" /tmp/t.sh && source /tmp/t.sh && ! counted_tables 9 >/dev/null 2>&1' \
        || die "tables.sh accepts a malformed entry ($bad)"
done
pass "tables.sh: 11 required and 7 counted tables at V9; a malformed entry fails"

step "test database ($db_image) with the repo migrations"
docker network create "$net" >/dev/null
docker volume create "$out" >/dev/null
# PGDATA as in compose.local.yaml: the image refuses one outside /var/lib/postgresql/data.
POSTGRES_PASSWORD=$db_password docker run --detach --name "$db" --network "$net" \
    -e PGDATA=/var/lib/postgresql/data/pgdata -e POSTGRES_USER="$db_user" -e POSTGRES_PASSWORD \
    -e POSTGRES_DB="$db_name" "$db_image" >/dev/null
# TCP answers only once the image's init is over (its temporary server listens on the socket only).
for attempt in $(seq 1 60); do
    if docker exec "$db" pg_isready -q -h 127.0.0.1 -U "$db_user" -d "$db_name" 2>/dev/null; then
        break
    fi
    [ "$attempt" -lt 60 ] || { docker logs "$db" 2>&1 | tail -n 20; die "the test database did not start"; }
    sleep 1
done

migrate "$db_name"
pass "$rank migrations, Flyway V$last_version"

# A throwaway certificate (the image's openssl) on the backup volume, for the stub and the runs' curl.
in_image "$ping_host" <<'SH' || die "the check-in stub's certificate"
set -euo pipefail
mkdir /out/ping
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=$1" -addext "subjectAltName=DNS:$1" \
    -keyout /out/ping/key.pem -out /out/ping/ca.pem 2>/dev/null
SH
docker pull --quiet "$stub_image" >/dev/null
PING_STUB_CODE=$ping_stub_code docker run --detach --name "$ping_stub" --network "$net" \
    --network-alias "$ping_host" -v "$out:/out:ro" -e PING_STUB_CODE "$stub_image" \
    python -u -c 'import os; exec(os.environ["PING_STUB_CODE"])' >/dev/null
for attempt in $(seq 1 50); do
    if docker logs "$ping_stub" 2>&1 | grep -qx listening; then
        break
    fi
    [ "$attempt" -lt 50 ] || { docker logs "$ping_stub"; die "the check-in stub did not start"; }
    sleep 0.2
done
pass "a stub of healthchecks.io ($stub_image) at https://$ping_host:8443"

db_psql <<SQL
insert into users (email, password_hash, role) values ('admin@example.com', 'not-a-hash', 'admin');
insert into pipeline_runs (kind, status, range_from, range_to, sources, requested_by, started_at, finished_at)
    select 'range', 'succeeded', now() - interval '1 day', now(), array['wykop'], id, now(), now()
    from users where email = 'admin@example.com';
insert into pipeline_run_steps (run_id, step, status, finished_at)
    select id, 'fetch', 'succeeded', now() from pipeline_runs;
with raw as (
    insert into raw_payloads (source, content_encoding, media_type, payload, sha256)
    select 'wykop', 'gzip', 'application/json', decode('1f8b08000000000000ff', 'hex'), repeat('a', 64)
    from generate_series(1, $posts)
    returning id
)
insert into posts (source, item_type, source_item_id, published_at, title, body, raw_payload_id,
        content_hash, stage, first_run_id)
    select 'wykop', 'post', (row_number() over ())::text, now(), 'title', 'body', raw.id, repeat('b', 64),
        'assessed', (select id from pipeline_runs)
    from raw;
insert into assessments (post_id, run_id, status, input, input_hash, output, violations, result_source,
        provider, model, channel, service_tier, flex_fallback, output_mode, prompt_version, contract_version,
        assessed_at, duration_ms, attempts)
    select id, first_run_id, 'ok', '{}', repeat('c', 64), '{}', '[]', 'model', 'openrouter', 'test-model',
        'openrouter', 'default', false, 'json_schema', 'test', 'v2', now(), 1, 1
    from posts;
insert into activation_tokens (user_id, token_hash, expires_at)
    select id, repeat('d', 64), now() + interval '1 day' from users where email = 'admin@example.com';
insert into notifications (recipient_id, kind, run_id)
    select users.id, 'run_attention', pipeline_runs.id from users, pipeline_runs
    where users.email = 'admin@example.com';
insert into spring_session values ('00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000002', 0, 0, 1800, 0, 'admin@example.com');
insert into spring_session_attributes values ('00000000-0000-0000-0000-000000000001',
    'SPRING_SECURITY_CONTEXT', decode('00', 'hex'));
SQL
# The migrations add system accounts (V6: the daily run's scheduler), so count the users instead of
# assuming one.
users=$(db_psql -Atc 'select count(*) from users')
pass "sample rows: 1 admin ($users users with the migrations' system accounts), 1 run with 1 step, $posts posts with RAW and assessments, 1 activation link, 1 notification, 1 session"

step "1. backup.sh into :local:/out"
# The scheme in capitals: the rule takes https in any case.
run_backup run1 -e "$ping_ca" -e HEALTHCHECK_PING_URL="HTTPS://$ping_host:8443/ping/$ping_key/run1" \
    || { cat "$tmp/run1.log"; die "the first backup failed"; }
grep -q 'restore-check: .*OK' "$tmp/run1.log" || { cat "$tmp/run1.log"; die "no restore-check OK in the log"; }
pings_were run1 'start 0'
last_ping_says run1 ' backup: OK: daily/'
pass "with HEALTHCHECK_PING_URL (HTTPS:// in capitals): /start, then /0 with the OK line"
in_image "$posts" "$last_version" "$users" <<'SH' || die "backup contents"
set -euo pipefail
posts=$1 version=$2 users=$3
dumps=(/out/daily/*.dump) manifests=(/out/daily/*.manifest.json)
[[ ${#dumps[@]} == 1 && ${#manifests[@]} == 1 ]] || { echo "daily: ${#dumps[@]} dumps, ${#manifests[@]} manifests"; exit 1; }
[[ ${dumps[0]%.dump} == "${manifests[0]%.manifest.json}" ]] || { echo "dump and manifest names differ"; exit 1; }
toc=$(pg_restore --list "${dumps[0]}")
if grep -E ' TABLE DATA public spring_session' <<<"$toc"; then echo "session data in the dump"; exit 1; fi
grep -qE ' TABLE DATA public posts( |$)' <<<"$toc" || { echo "no posts data in the dump"; exit 1; }
jq -e --argjson posts "$posts" --argjson users "$users" --arg version "$version" --arg file "$(basename "${dumps[0]}")" \
    --arg sha "$(sha256sum "${dumps[0]}" | cut -d ' ' -f 1)" \
    '.file == $file and .sha256 == $sha and .flyway_version == $version and .restore_check.ok == true
     and .counts_before.posts == $posts and .counts_after.posts == $posts and .counts_after.users == $users
     and .counts_before.notifications == 1 and .counts_after.pipeline_run_steps == 1
     and (.counts_before | has("activation_tokens") | not)
     and .compared_with == null and .accepted_shrink == false' \
    "${manifests[0]}" >/dev/null || { echo "manifest:"; cat "${manifests[0]}"; exit 1; }
month=$(date -u +%Y-%m)
[[ -s /out/monthly/szczur-radar-$month.dump && -s /out/monthly/szczur-radar-$month.manifest.json ]] \
    || { echo "no monthly copy for $month"; ls -l /out/monthly || true; exit 1; }
SH
pass "dump + manifest in daily/ (sha256, Flyway, counts with notifications and pipeline_run_steps, no activation_tokens, restore_check, compared with nothing), no session data, monthly copy"
echo "backup.sh log (what Railway shows for a cron run):"
sed 's/^/    /' "$tmp/run1.log"

step "2. retention: 20 older daily and 15 older monthly dumps"
in_image <<'SH'
set -euo pipefail
for day in $(seq -w 1 20); do
    touch "/out/daily/szczur-radar-202001${day}T020000Z.dump" "/out/daily/szczur-radar-202001${day}T020000Z.manifest.json"
done
for i in $(seq 0 14); do
    month="$(( 2019 + i / 12 ))-$(printf %02d $(( i % 12 + 1 )))"
    touch "/out/monthly/szczur-radar-$month.dump" "/out/monthly/szczur-radar-$month.manifest.json"
done
# A hand-copied file sorts after every timestamped name ('b' > '2'): it must not count as the newest.
touch /out/daily/szczur-radar-before-restore.dump
SH
run_backup run2 || { cat "$tmp/run2.log"; die "the second backup failed"; }
in_image <<'SH' || die "retention"
set -euo pipefail
shopt -s nullglob
daily=(/out/daily/szczur-radar-*T*Z.dump) daily_manifests=(/out/daily/*.manifest.json)
monthly=(/out/monthly/*.dump) monthly_manifests=(/out/monthly/*.manifest.json)
real=()
for dump in "${daily[@]}"; do [[ $dump == */szczur-radar-2020* ]] || real+=("$dump"); done
[[ ${#daily[@]} == 14 && ${#daily_manifests[@]} == 14 ]] \
    || { echo "daily: ${#daily[@]} dumps, ${#daily_manifests[@]} manifests, expected 14"; exit 1; }
[[ ${#real[@]} == 2 ]] || { echo "the two real dumps are not both kept"; exit 1; }
[[ ! -e /out/daily/szczur-radar-20200108T020000Z.dump && -e /out/daily/szczur-radar-20200109T020000Z.dump ]] \
    || { echo "the wrong daily dumps were deleted"; exit 1; }
[[ ${#monthly[@]} == 12 && ${#monthly_manifests[@]} == 12 ]] \
    || { echo "monthly: ${#monthly[@]} dumps, ${#monthly_manifests[@]} manifests, expected 12"; exit 1; }
[[ -s /out/monthly/szczur-radar-$(date -u +%Y-%m).dump ]] || { echo "the current monthly copy is gone"; exit 1; }
[[ ! -e /out/monthly/szczur-radar-2019-04.dump && -e /out/monthly/szczur-radar-2019-05.dump ]] \
    || { echo "the wrong monthly dumps were deleted"; exit 1; }
[[ -e /out/daily/szczur-radar-before-restore.dump ]] || { echo "a file with another name was deleted"; exit 1; }
SH
grep -q 'ignoring daily/szczur-radar-before-restore.dump' "$tmp/run2.log" \
    || { cat "$tmp/run2.log"; die "the other file is not logged as ignored"; }
check_manifest run2 '.compared_with == $previous and .accepted_shrink == false' \
    --arg previous "$(uploaded run1).manifest.json" || die "the second run did not compare with the first one's manifest"
pass "daily 22 → 14 (both real dumps kept), monthly 16 → 12 (current month kept, not copied twice), other names left alone; compared with the first run's manifest"

step "3. shrink and consent: fewer rows than the newest manifest fail the run unless BACKUP_ACCEPT_SHRINK names it"
# A manifest dated in the future (a wrong clock, a hand-made file) is not the newest: its huge counts
# would fail every run. Removed afterwards, so the next steps see only the names they expect.
in_image <<'SH'
set -euo pipefail
echo '{"format": 1, "counts_after": {"users": 1000000, "posts": 1000000, "assessments": 1000000}}' \
    >/out/daily/szczur-radar-29991231T020000Z.manifest.json
SH
# Its check-in address is unreachable (nothing listens on port 9): the run's result stays as it is.
run_backup run7 -e "$ping_ca" -e HEALTHCHECK_PING_URL="https://$ping_host:9/ping/$ping_key/run7" \
    || { cat "$tmp/run7.log"; die "a run next to a future-dated manifest failed"; }
grep -q 'ignoring daily/szczur-radar-29991231T020000Z.manifest.json' "$tmp/run7.log" \
    || { cat "$tmp/run7.log"; die "the future-dated manifest is not logged as ignored"; }
grep -qF 'warning: check-in start to healthchecks.io failed (curl: (7)' "$tmp/run7.log" \
    && grep -qF 'warning: check-in 0 to healthchecks.io failed (curl: (7)' "$tmp/run7.log" \
    || { cat "$tmp/run7.log"; die "the unreachable check-ins are not logged as warnings"; }
pings_were run7 ''
check_manifest run7 '.compared_with == $previous and .accepted_shrink == false' \
    --arg previous "$(uploaded run2).manifest.json" || die "the run did not compare with the newest manifest before it"
in_image <<<'rm /out/daily/szczur-radar-29991231T020000Z.manifest.json'
pass "a manifest dated in the future is skipped: compared with the second run's; an unreachable check-in address only gives warnings"
# 5 posts with their assessments go (their RAW stays): both tables shrink below the newest manifest.
db_psql <<'SQL'
delete from assessments where post_id in (select id from posts order by id limit 5);
delete from posts where id in (select id from posts order by id limit 5);
SQL
before=$(in_image <<<'ls /out/daily /out/monthly')
if run_backup run8 -e "$ping_ca" -e HEALTHCHECK_PING_URL="$ping_url/run8"; then
    cat "$tmp/run8.log"
    die "a run with fewer posts than the newest manifest exited 0"
fi
accept=$(sed -nE 's/.*set BACKUP_ACCEPT_SHRINK=([^ ]+) on db-backup and redeploy it.*/\1/p' "$tmp/run8.log")
grep -qF "posts $(( posts - 5 )) < $posts" "$tmp/run8.log" \
    && grep -q 'nothing dumped' "$tmp/run8.log" \
    && [ "$accept" = "$(uploaded run7)" ] \
    || { cat "$tmp/run8.log"; die "the failed run does not name the shrink and the BACKUP_ACCEPT_SHRINK value"; }
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "the run with fewer posts changed the backups"
pings_were run8 'start 1'
last_ping_says run8 " backup: FAILED: rows lost since daily/$accept.manifest.json: "
pass "fewer posts → fails before the dump (posts $(( posts - 5 )) < $posts), names BACKUP_ACCEPT_SHRINK=$accept, backups unchanged; /1 with that line"
# Pasted with a trailing space, as from a log line: the value is trimmed.
run_backup run9 -e BACKUP_ACCEPT_SHRINK="$accept " || { cat "$tmp/run9.log"; die "the run with the consent failed"; }
grep -qF "accepted: posts $(( posts - 5 )) < $posts" "$tmp/run9.log" \
    || { cat "$tmp/run9.log"; die "the accepted shrink is not logged"; }
check_manifest run9 '.compared_with == $previous and .accepted_shrink == true and .counts_before.posts == $posts' \
    --arg previous "$accept.manifest.json" --argjson posts $(( posts - 5 )) || die "the accepted run's manifest"
pass "BACKUP_ACCEPT_SHRINK naming the newest manifest lets the shrink through (accepted_shrink: true)"
run_backup run10 -e BACKUP_ACCEPT_SHRINK="$accept" || { cat "$tmp/run10.log"; die "the run after the consent failed"; }
grep -qF "ignoring BACKUP_ACCEPT_SHRINK=$accept: the newest manifest is daily/$(uploaded run9).manifest.json" "$tmp/run10.log" \
    || { cat "$tmp/run10.log"; die "the stale BACKUP_ACCEPT_SHRINK is not logged as ignored"; }
check_manifest run10 '.compared_with == $previous and .accepted_shrink == false' \
    --arg previous "$(uploaded run9).manifest.json" || die "the run after the consent's manifest"
pass "the same BACKUP_ACCEPT_SHRINK on the next run is ignored (accepted_shrink: false): the consent expires"
# The expired value must not let a new shrink through: one more post goes.
db_psql <<'SQL'
delete from assessments where post_id in (select id from posts order by id limit 1);
delete from posts where id in (select id from posts order by id limit 1);
SQL
before=$(in_image <<<'ls /out/daily /out/monthly')
if run_backup run12 -e BACKUP_ACCEPT_SHRINK="$accept"; then
    cat "$tmp/run12.log"
    die "a stale BACKUP_ACCEPT_SHRINK let a new shrink through"
fi
grep -qF "rows lost since daily/$(uploaded run10).manifest.json: posts $(( posts - 6 )) < $(( posts - 5 ))" "$tmp/run12.log" \
    && grep -qF "set BACKUP_ACCEPT_SHRINK=$(uploaded run10) on db-backup" "$tmp/run12.log" \
    || { cat "$tmp/run12.log"; die "the run with a stale BACKUP_ACCEPT_SHRINK does not name the new shrink"; }
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "the run with a stale consent changed the backups"
# The new value lets it through, so the newest manifest matches the database again for the next steps.
run_backup run13 -e BACKUP_ACCEPT_SHRINK="$(uploaded run10)" || { cat "$tmp/run13.log"; die "the run with the new consent failed"; }
check_manifest run13 '.accepted_shrink == true' || die "the new consent's manifest"
pass "a stale BACKUP_ACCEPT_SHRINK does not let a new shrink through (posts $(( posts - 6 )) < $(( posts - 5 ))); the new value does"
# A bucket whose manifests cannot be compared fails instead of passing as a first copy. Its own remote
# (/out/alt), so the backups above stay as they are; removed afterwards.
in_image <<<'mkdir -p /out/alt/daily && echo "{\"counts_after\": {\"posts\": 1}}" >/out/alt/daily/szczur-radar-29991231T020000Z.manifest.json'
if run_backup run14 -e BACKUP_REMOTE=:local:/out/alt; then
    cat "$tmp/run14.log"
    die "a run whose every manifest is dated in the future exited 0"
fi
grep -q 'every manifest in daily/ is dated after this run' "$tmp/run14.log" \
    || { cat "$tmp/run14.log"; die "the run with only future-dated manifests does not say so"; }
in_image <<<'echo "{\"counts_after\": {}}" >/out/alt/daily/szczur-radar-20200101T000000Z.manifest.json'
if run_backup run15 -e BACKUP_REMOTE=:local:/out/alt; then
    cat "$tmp/run15.log"
    die "a run whose newest manifest has no counts exited 0"
fi
grep -qF 'daily/szczur-radar-20200101T000000Z.manifest.json has no counts_after of numbers (if it is broken, rename it outside the name pattern (e.g. szczur-radar-20200101T000000Z.manifest.json.broken)' "$tmp/run15.log" \
    || { cat "$tmp/run15.log"; die "the run with a broken manifest does not name it and the rename"; }
# The documented way out: the renamed file is skipped, and the run is the bucket's first copy.
in_image <<<'cd /out/alt/daily && mv szczur-radar-20200101T000000Z.manifest.json szczur-radar-20200101T000000Z.manifest.json.broken && rm szczur-radar-29991231T020000Z.manifest.json'
run_backup run16 -e BACKUP_REMOTE=:local:/out/alt || { cat "$tmp/run16.log"; die "the run after renaming the broken manifest failed"; }
in_image "$(uploaded run16)" <<<'jq -e ".compared_with == null" "/out/alt/daily/$1.manifest.json" >/dev/null' \
    || die "the run after renaming the broken manifest compared with something"
in_image <<<'rm -r /out/alt'
pass "only future-dated manifests, or a newest manifest without counts, fail the run; renaming it .broken lets the next run through"

step "4. an older dump passes, a broken dump or schema fails, and a failed restore check uploads nothing"
in_image <<'SH' || die "a truncated dump passed restore-check.sh"
set -uo pipefail
dump=$(ls /out/daily/szczur-radar-*T*Z.dump | grep -v '/szczur-radar-2020' | tail -n 1)
head -c "$(( $(stat -c %s "$dump") / 2 ))" "$dump" >/tmp/truncated.dump
if restore-check.sh /tmp/truncated.dump; then exit 1; fi
SH
pass "restore-check.sh fails on a truncated dump"
# Production's oldest copies (the monthly one of 2026-10) are from Flyway V5: before activation_tokens
# (V7) and notifications (V9). A second database gets V1–V5 only; its dump must pass without a manifest.
db_psql -c 'create database backup_test_v5'
migrate backup_test_v5 5
[ "$last_version" = 5 ] || die "the V5 database got Flyway V$last_version"
docker exec "$db" pg_dump --format=custom -U "$db_user" -d backup_test_v5 >"$tmp/v5.dump" \
    || die "pg_dump of the V5 database"
docker run --rm -i --name "$id-v5" --entrypoint bash "$image" \
    -c 'cat >/tmp/v5.dump && restore-check.sh /tmp/v5.dump' <"$tmp/v5.dump" >"$tmp/v5.log" 2>&1 \
    || { cat "$tmp/v5.log"; die "a Flyway V5 dump failed restore-check.sh"; }
grep -qE 'restore-check: OK .*Flyway V5([^0-9]|$)' "$tmp/v5.log" || { cat "$tmp/v5.log"; die "no Flyway V5 in the OK line"; }
pass "a Flyway V5 dump (no activation_tokens, no notifications) passes restore-check.sh"
before=$(in_image <<<'ls /out/daily /out/monthly')
if run_backup run4 -e BACKUP_TIMEOUT=1s -e "$ping_ca" -e HEALTHCHECK_PING_URL="$ping_url/run4"; then
    cat "$tmp/run4.log"
    die "a run over its time limit exited 0"
fi
grep -q 'backup: FAILED: the run exceeded 1s' "$tmp/run4.log" || { cat "$tmp/run4.log"; die "the time limit is not named"; }
pings_were run4 'start 124'
last_ping_says run4 ' backup: FAILED: the run exceeded 1s'
pass "a run over its time limit (BACKUP_TIMEOUT=1s) fails and says so; /124 with that line, before the exit"
run_backup run5 -e RAILWAY_ENVIRONMENT_NAME=szczur-radar-pr-1 -e "$ping_ca" -e HEALTHCHECK_PING_URL="$ping_url/run5" \
    || { cat "$tmp/run5.log"; die "a skipped run failed"; }
grep -q 'skipped: backups run only in production (this is szczur-radar-pr-1)' "$tmp/run5.log" \
    || { cat "$tmp/run5.log"; die "outside production the run was not skipped"; }
pings_were run5 ''
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "a skipped or timed-out run changed the backups"
pass "outside production the run is skipped (exit 0, nothing written, no check-in)"
# An address outside the rule (S-25): a warning that does not repeat it, no check-in, and the run goes on.
# Without PGHOST (emptied over run_backup's value) it ends at once, before the dump.
for ping_case in "ping-not-https http://$ping_host:8443/ping/$ping_key/ping-not-https" \
        "ping-query $ping_url/ping-query?x=1" "ping-no-host https:///ping/$ping_key/ping-no-host"; do
    name=${ping_case%% *}
    if run_backup "$name" -e PGHOST= -e "$ping_ca" -e HEALTHCHECK_PING_URL="${ping_case#* }"; then
        cat "$tmp/$name.log"
        die "$name: a run without PGHOST exited 0"
    fi
    grep -qF 'backup: warning: HEALTHCHECK_PING_URL is not an https:// address with a host name and without ? or #; no check-in' \
        "$tmp/$name.log" && grep -qF 'backup: FAILED: PGHOST is not set' "$tmp/$name.log" \
        || { cat "$tmp/$name.log"; die "$name: no warning about the address, or not the PGHOST failure"; }
    pings_were "$name" ''
done
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "a run with a refused check-in address changed the backups"
pass "an address that is not https://, has a query or no host name: a warning, no check-in, the run goes on (fails on PGHOST)"
# Before the missing table below: restore-check.sh checks missing tables before unknown ones.
db_psql -c 'create table stray (id int)'
if run_backup run6; then
    cat "$tmp/run6.log"
    die "a backup with an unclassified table exited 0"
fi
grep -q 'restore-check failed; nothing uploaded' "$tmp/run6.log" \
    && grep -q 'tables: unknown stray' "$tmp/run6.log" \
    || { cat "$tmp/run6.log"; die "the failed run does not name the unclassified table"; }
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "the run with an unclassified table changed the backups"
db_psql -c 'drop table stray'
pass "unclassified table → restore check fails (tables: unknown), exit ≠ 0, backups unchanged"
# A required table that is not counted (counting would fail before the dump): activation_tokens (V7).
db_psql -c 'drop table activation_tokens'
if run_backup run3; then
    cat "$tmp/run3.log"
    die "a backup whose restore check fails exited 0"
fi
grep -q 'restore-check failed; nothing uploaded' "$tmp/run3.log" \
    && grep -q 'tables: missing activation_tokens' "$tmp/run3.log" \
    || { cat "$tmp/run3.log"; die "the failed run does not name the failed check"; }
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "the failed run changed the backups"
pass "missing table → restore check fails, exit ≠ 0, backups unchanged"

# Last of the backup.sh runs: every run after it would fail on the minimum.
step "5. an empty posts table fails the run before the dump, even with BACKUP_ACCEPT_SHRINK"
db_psql -c 'truncate assessments, posts, raw_payloads'
newest=$(newest_manifest)
[ -n "$newest" ] || die "no manifest in daily/"
before=$(in_image <<<'ls /out/daily /out/monthly')
if run_backup run11 -e BACKUP_ACCEPT_SHRINK="$newest"; then
    cat "$tmp/run11.log"
    die "a backup of an empty database exited 0"
fi
grep -q 'posts is empty: refusing to back up an empty database; nothing dumped' "$tmp/run11.log" \
    || { cat "$tmp/run11.log"; die "the failed run does not say that posts is empty"; }
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "the run of an empty database changed the backups"
pass "empty posts → fails before the dump although BACKUP_ACCEPT_SHRINK=$newest names the newest manifest, backups unchanged"

step "6. nas-retention.sh"
in_image <<'SH' || die "nas-retention"
set -euo pipefail
nas=/tmp/nas
mkdir -p "$nas/daily" "$nas/monthly"
for day in $(seq -w 1 20); do
    touch "$nas/daily/szczur-radar-202001${day}T020000Z.dump" "$nas/daily/szczur-radar-202001${day}T020000Z.manifest.json"
done
for i in $(seq 0 14); do
    month="$(( 2019 + i / 12 ))-$(printf %02d $(( i % 12 + 1 )))"
    touch "$nas/monthly/szczur-radar-$month.dump" "$nas/monthly/szczur-radar-$month.manifest.json"
done
# Neither may count as the newest: another name, and a dump dated in the future.
touch "$nas/daily/szczur-radar-before-restore.dump" "$nas/daily/szczur-radar-29991231T020000Z.dump"
expected=$(
    for day in 01 02 03 04 05 06; do
        for ext in dump manifest.json; do echo "would delete $nas/daily/szczur-radar-202001${day}T020000Z.$ext"; done
    done
    for month in 2019-01 2019-02 2019-03; do
        for ext in dump manifest.json; do echo "would delete $nas/monthly/szczur-radar-$month.$ext"; done
    done
)
actual=$(nas-retention.sh "$nas" --dry-run 2>/dev/null)
[[ $actual == "$expected" ]] || { echo "dry run:"; echo "$actual"; exit 1; }
[[ $(ls "$nas/daily" | wc -l) == 42 && $(ls "$nas/monthly" | wc -l) == 30 ]] || { echo "the dry run deleted files"; exit 1; }
nas-retention.sh "$nas" >/dev/null 2>&1
[[ $(ls "$nas/daily" | wc -l) == 30 && $(ls "$nas/monthly" | wc -l) == 24 ]] || { echo "after the run:"; ls "$nas"/*; exit 1; }
[[ -e $nas/daily/szczur-radar-before-restore.dump && -e $nas/daily/szczur-radar-29991231T020000Z.dump ]] \
    || { echo "another name or a future-dated dump was deleted"; exit 1; }
[[ -e $nas/daily/szczur-radar-20200120T020000Z.dump && ! -e $nas/daily/szczur-radar-20200106T020000Z.dump ]] \
    || { echo "the wrong NAS files were deleted"; exit 1; }
SH
pass "--dry-run lists the 6 oldest daily and 3 oldest monthly pairs and deletes nothing; the run keeps 14 + 12; other names and future dates are left alone"

step "7. no secrets in the logs"
if grep -lF -e "$db_password" -e "$ping_key" "$tmp"/*.log; then
    die "the database password or the check-in key appears in a log"
fi
pass "neither the database password nor the check-in key is in any of the backup logs"

printf '\nAll checks passed.\n'
