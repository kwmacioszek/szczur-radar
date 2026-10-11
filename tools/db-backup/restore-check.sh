#!/usr/bin/env bash
# Proves that a dump restores into a healthy, queryable database (F-01, production-data-gate; see
# README.md): a throwaway Postgres 18 cluster in a temporary directory of the container (never a
# volume), pg_restore in one transaction, then named checks. With the manifest written by backup.sh it
# also compares the Flyway version and the row counts. With --serve the restored database stays up on
# port 5432 (user postgres, password RESTORE_CHECK_PASSWORD, database `restored`) for a test with the
# application, until the container stops.
#   restore-check.sh <dump> [<manifest>] [--serve]
# Exit 0 when every check passes; otherwise non-zero with the failed check on stderr.
set -euo pipefail

readonly REQUIRED_TABLES=(users spring_session spring_session_attributes pipeline_runs pipeline_run_steps
    raw_payloads posts assessments flyway_schema_history)
readonly DB=restored
readonly USAGE="usage: restore-check.sh <dump> [<manifest>] [--serve]"

log() { printf '%s restore-check: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() { log "FAILED: $*"; exit 1; }

dump="" manifest="" serve=false
for arg in "$@"; do
    case $arg in
        --serve) serve=true ;;
        -*) fail "unknown option $arg; $USAGE" ;;
        *)
            if [[ -z $dump ]]; then
                dump=$arg
            elif [[ -z $manifest ]]; then
                manifest=$arg
            else
                fail "$USAGE"
            fi
            ;;
    esac
done
[[ -n $dump ]] || fail "$USAGE"
[[ -r $dump ]] || fail "cannot read $dump"
[[ -z $manifest || -r $manifest ]] || fail "cannot read $manifest"
if $serve && [[ -z ${RESTORE_CHECK_PASSWORD:-} ]]; then
    fail "--serve needs RESTORE_CHECK_PASSWORD (the password of user postgres on the served database)"
fi

# The caller's PG* variables (backup.sh runs with production's) must not reach this cluster.
unset PGHOST PGHOSTADDR PGPORT PGUSER PGPASSWORD PGDATABASE PGSERVICE PGSSLMODE PGOPTIONS
base=$(mktemp -d /tmp/restore-check.XXXXXX)
data=$base/data
export PGHOST=$base PGPORT=5432 PGUSER=postgres

# initdb and the server refuse to run as root: the image's postgres user runs them.
as_postgres() {
    if (( EUID == 0 )); then
        gosu postgres "$@"
    else
        "$@"
    fi
}

cleanup() {
    as_postgres pg_ctl --pgdata="$data" --mode=immediate stop >/dev/null 2>&1 || true
    rm -rf "$base"
}
trap cleanup EXIT

if (( EUID == 0 )); then
    chown postgres:postgres "$base"
fi
as_postgres initdb --pgdata="$data" --username=postgres --auth=trust --encoding=UTF8 --locale=C.UTF-8 \
    >"$base/initdb.log" 2>&1 || { cat "$base/initdb.log" >&2; fail "initdb"; }
# A throwaway copy: durability settings off for speed; only the socket in $base until --serve.
cat >>"$data/postgresql.conf" <<EOF
listen_addresses = ''
port = 5432
unix_socket_directories = '$base'
fsync = off
full_page_writes = off
synchronous_commit = off
EOF
as_postgres pg_ctl --pgdata="$data" --log="$base/server.log" --wait --silent start \
    || { cat "$base/server.log" >&2; fail "the temporary server did not start"; }

sql() { psql -X -A -t -q -v ON_ERROR_STOP=1 -d "$DB" -c "$1"; }

started=$SECONDS
psql -X -q -v ON_ERROR_STOP=1 -d postgres -c "create database $DB template template0" \
    || fail "create database"
log "restoring $(basename "$dump")"
pg_restore --dbname="$DB" --no-owner --no-acl --single-transaction --exit-on-error "$dump" \
    2>"$base/restore.err" || { tail -n 20 "$base/restore.err" >&2; fail "pg_restore"; }

table_list=$(printf "'%s'," "${REQUIRED_TABLES[@]}")
missing=$(sql "select coalesce(string_agg(t, ', '), '') from unnest(array[${table_list%,}]) as t
    where to_regclass('public.' || t) is null") || fail "tables: query"
[[ -z $missing ]] || fail "tables: missing $missing"

sessions=$(sql "select (select count(*) from spring_session) + (select count(*) from spring_session_attributes)")
[[ $sessions == 0 ]] || fail "sessions: $sessions session rows restored, expected none"

flyway=$(sql "select version from flyway_schema_history
    where success and version is not null order by installed_rank desc limit 1")
[[ -n $flyway ]] || fail "flyway: no successful migration in flyway_schema_history"

if [[ -n $manifest ]]; then
    expected=$(jq -er '.flyway_version' "$manifest") || fail "manifest: no flyway_version"
    [[ $flyway == "$expected" ]] || fail "flyway: restored V$flyway, the manifest says V$expected"
    # One line per counted table: name, rows before the dump, rows after it.
    counts=$(jq -er '. as $m | .counts_before | to_entries[]
        | [.key, .value, $m.counts_after[.key]] | @tsv' "$manifest") || fail "manifest: no counts"
    while IFS=$'\t' read -r table before after; do
        [[ $table =~ ^[a-z_]+$ && $before =~ ^[0-9]+$ && $after =~ ^[0-9]+$ ]] \
            || fail "manifest: bad counts line for '$table'"
        actual=$(sql "select count(*) from public.$table") || fail "counts: query $table"
        (( before <= actual && actual <= after )) \
            || fail "counts: $table has $actual rows, expected $before..$after"
    done <<<"$counts"
fi

log "OK in $(( SECONDS - started )) s: ${#REQUIRED_TABLES[@]} tables, no session rows, Flyway V$flyway${manifest:+, row counts within [before, after]}"

if $serve; then
    psql -X -q -v ON_ERROR_STOP=1 -d postgres -v password="$RESTORE_CHECK_PASSWORD" \
        <<<"alter role postgres password :'password';" || fail "serve: set the password"
    as_postgres pg_ctl --pgdata="$data" --mode=fast --wait --silent stop
    # Later lines win: listen on every interface of the container, with a password from outside it.
    echo "listen_addresses = '*'" >>"$data/postgresql.conf"
    echo "host all all all scram-sha-256" >>"$data/pg_hba.conf"
    trap - EXIT
    log "serving database '$DB' on port 5432 (user postgres); stop the container to end"
    if (( EUID == 0 )); then
        exec gosu postgres postgres -D "$data"
    fi
    exec postgres -D "$data"
fi
