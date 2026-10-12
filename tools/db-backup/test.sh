#!/usr/bin/env bash
# Local end-to-end test of the backup tool, without Railway and S3 (F-01, production-data-gate; see
# README.md). A Postgres 18 database (production's image) gets the repo's Flyway migrations, a
# flyway_schema_history like Flyway's and sample rows including a session; then:
#   1. backup.sh into a local rclone remote: dump and manifest uploaded, no session data in the dump,
#      restore check passed, monthly copy made;
#   2. retention: 20 older daily and 15 older monthly dumps are cut to 14 and 12; a file with another
#      name is left alone;
#   3. a truncated dump fails restore-check.sh, a failing restore check uploads nothing, a run over
#      its time limit fails, and outside production (RAILWAY_ENVIRONMENT_NAME) a run is skipped;
#   4. nas-retention.sh --dry-run names the right files and deletes nothing, then a real run prunes;
#      other names and future-dated dumps are left alone;
#   5. the throwaway database password never appears in the logs.
# Needs Docker. Run from anywhere: tools/db-backup/test.sh
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
migrations=$(cd "$here/../.." && pwd)/szczur-radar-api/src/main/resources/db/migration
readonly image=szczur-radar-db-backup
readonly db_image=ghcr.io/railwayapp-templates/postgres-ssl:18
readonly id=sr-db-backup-test-$$
readonly net=$id db=$id-db out=$id-out
readonly db_user=backup_test db_name=backup_test posts=40
# A throwaway password, checked below never to appear in a log.
db_password=test-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')
tmp=$(mktemp -d)

step() { printf '\n== %s\n' "$*"; }
pass() { printf 'PASS %s\n' "$*"; }
die() { printf 'FAIL %s\n' "$*" >&2; exit 1; }

cleanup() {
    # Every container of this test is named "$id-…" (the database and each backup run).
    docker ps -aq --filter "name=$id-" | xargs docker rm -f >/dev/null 2>&1 || true
    docker volume rm "$out" >/dev/null 2>&1 || true
    docker network rm "$net" >/dev/null 2>&1 || true
    rm -rf "$tmp"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# psql in the database container (local socket, trust).
db_psql() { docker exec -i "$db" psql -X -q -v ON_ERROR_STOP=1 -U "$db_user" -d "$db_name" "$@"; }
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

step "build $image"
docker build --quiet --tag "$image" "$here" >/dev/null
pass "image built"

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

# The table Flyway creates (Flyway 11, PostgreSQL), filled the way `migrate` would fill it.
db_psql <<'SQL'
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
    rank=$((rank + 1))
    script=${file%.sql}
    last_version=${script%%__*}
    last_version=${last_version#V}
    description=$(printf '%s' "${script#*__}" | tr _ ' ')
    db_psql <"$migrations/$file"
    db_psql -c "insert into flyway_schema_history (installed_rank, version, description, type, script,
        checksum, installed_by, execution_time, success)
        values ($rank, '$last_version', '$description', 'SQL', '$file', 0, '$db_user', 0, true)"
done
[ "$rank" -gt 0 ] || die "no migrations in $migrations"
pass "$rank migrations, Flyway V$last_version"

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
insert into spring_session values ('00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000002', 0, 0, 1800, 0, 'admin@example.com');
insert into spring_session_attributes values ('00000000-0000-0000-0000-000000000001',
    'SPRING_SECURITY_CONTEXT', decode('00', 'hex'));
SQL
# The migrations add system accounts (V6: the daily run's scheduler), so count the users instead of
# assuming one.
users=$(db_psql -Atc 'select count(*) from users')
pass "sample rows: 1 admin ($users users with the migrations' system accounts), 1 run, $posts posts with RAW and assessments, 1 session"

step "1. backup.sh into :local:/out"
run_backup run1 || { cat "$tmp/run1.log"; die "the first backup failed"; }
grep -q 'restore-check: .*OK' "$tmp/run1.log" || { cat "$tmp/run1.log"; die "no restore-check OK in the log"; }
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
     and .counts_before.posts == $posts and .counts_after.posts == $posts and .counts_after.users == $users' \
    "${manifests[0]}" >/dev/null || { echo "manifest:"; cat "${manifests[0]}"; exit 1; }
month=$(date -u +%Y-%m)
[[ -s /out/monthly/szczur-radar-$month.dump && -s /out/monthly/szczur-radar-$month.manifest.json ]] \
    || { echo "no monthly copy for $month"; ls -l /out/monthly || true; exit 1; }
SH
pass "dump + manifest in daily/ (sha256, Flyway, counts, restore_check), no session data, monthly copy"
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
pass "daily 22 → 14 (both real dumps kept), monthly 16 → 12 (current month kept, not copied twice), other names left alone"

step "3. a broken dump fails, and a failed restore check uploads nothing"
in_image <<'SH' || die "a truncated dump passed restore-check.sh"
set -uo pipefail
dump=$(ls /out/daily/szczur-radar-*T*Z.dump | grep -v '/szczur-radar-2020' | tail -n 1)
head -c "$(( $(stat -c %s "$dump") / 2 ))" "$dump" >/tmp/truncated.dump
if restore-check.sh /tmp/truncated.dump; then exit 1; fi
SH
pass "restore-check.sh fails on a truncated dump"
before=$(in_image <<<'ls /out/daily /out/monthly')
if run_backup run4 -e BACKUP_TIMEOUT=1s; then
    cat "$tmp/run4.log"
    die "a run over its time limit exited 0"
fi
grep -q 'backup: FAILED: the run exceeded 1s' "$tmp/run4.log" || { cat "$tmp/run4.log"; die "the time limit is not named"; }
pass "a run over its time limit (BACKUP_TIMEOUT=1s) fails and says so"
run_backup run5 -e RAILWAY_ENVIRONMENT_NAME=szczur-radar-pr-1 || { cat "$tmp/run5.log"; die "a skipped run failed"; }
grep -q 'skipped: backups run only in production (this is szczur-radar-pr-1)' "$tmp/run5.log" \
    || { cat "$tmp/run5.log"; die "outside production the run was not skipped"; }
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "a skipped or timed-out run changed the backups"
pass "outside production the run is skipped (exit 0, nothing written)"
db_psql -c 'drop table pipeline_run_steps'
if run_backup run3; then
    cat "$tmp/run3.log"
    die "a backup whose restore check fails exited 0"
fi
grep -q 'restore-check failed; nothing uploaded' "$tmp/run3.log" \
    && grep -q 'tables: missing pipeline_run_steps' "$tmp/run3.log" \
    || { cat "$tmp/run3.log"; die "the failed run does not name the failed check"; }
[ "$(in_image <<<'ls /out/daily /out/monthly')" = "$before" ] || die "the failed run changed the backups"
pass "missing table → restore check fails, exit ≠ 0, backups unchanged"

step "4. nas-retention.sh"
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

step "5. no secrets in the logs"
if grep -lF "$db_password" "$tmp"/*.log; then
    die "the database password appears in a log"
fi
pass "the database password is in none of the backup logs"

printf '\nAll checks passed.\n'
