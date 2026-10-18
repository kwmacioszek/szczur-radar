#!/usr/bin/env bash
# Proves that a dump restores into a healthy, queryable database (F-01, production-data-gate; see
# README.md): a throwaway Postgres 18 cluster in a temporary directory of the container (never a
# volume), pg_restore in one transaction, then named checks: the tables that tables.sh requires at the
# restored Flyway version exist, and no table is missing from its classification. With the manifest
# written by backup.sh it also compares the Flyway version and the row counts. With --serve the
# restored database stays up on port 5432 (user postgres, password RESTORE_CHECK_PASSWORD, database
# `restored`) for a test with the application, until the container stops.
#   restore-check.sh <dump> [<manifest>] [--serve]
# Exit 0 when every check passes; otherwise non-zero with the failed check on stderr.
set -euo pipefail

# TABLES, required_tables, classified_tables: the classification lives next to this script.
source "$(dirname "${BASH_SOURCE[0]}")/tables.sh"

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
wal_level = minimal
max_wal_senders = 0
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

# The Flyway version first: which tables are required depends on it (an older dump has fewer).
flyway=$(sql "select version from flyway_schema_history
    where success and version is not null order by installed_rank desc limit 1") \
    || fail "flyway: cannot read flyway_schema_history"
[[ -n $flyway ]] || fail "flyway: no successful migration in flyway_schema_history"
required=$(required_tables "$flyway") || fail "flyway: V$flyway is not a version tables.sh can compare"
mapfile -t required <<<"$required"

table_list=$(printf "'%s'," "${required[@]}")
missing=$(sql "select coalesce(string_agg(t, ', '), '') from unnest(array[${table_list%,}]) as t
    where to_regclass('public.' || t) is null") || fail "tables: query"
[[ -z $missing ]] || fail "tables: missing $missing"

# A table without a classification (a new migration's) would be backed up but never checked.
mapfile -t known < <(classified_tables)
known=$(printf "'%s'," "${known[@]}")
unknown=$(sql "select coalesce(string_agg(table_name::text, ', ' order by table_name::text), '')
    from information_schema.tables
    where table_schema = 'public' and table_type = 'BASE TABLE'
        and table_name::text <> all (array[${known%,}])") || fail "tables: query"
[[ -z $unknown ]] || fail "tables: unknown $unknown; classify it in tables.sh"

sessions=$(sql "select (select count(*) from spring_session) + (select count(*) from spring_session_attributes)")
[[ $sessions == 0 ]] || fail "sessions: $sessions session rows restored, expected none"

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

log "OK in $(( SECONDS - started )) s: Flyway V$flyway, its ${#required[@]} required tables, no unclassified table, no session rows${manifest:+, row counts within [before, after]}"

if $serve; then
    # The password comes from the environment (psql \getenv), never from a command line.
    psql -X -q -v ON_ERROR_STOP=1 -d postgres <<'SQL' || fail "serve: set the password"
\getenv password RESTORE_CHECK_PASSWORD
alter role postgres password :'password';
SQL
    as_postgres pg_ctl --pgdata="$data" --mode=fast --wait --silent stop
    # The last listen_addresses in postgresql.conf wins: every interface of the container. pg_hba.conf
    # takes the first matching line, so it is rewritten: the socket stays trusted (this script), every
    # TCP connection, loopback included, needs the password.
    echo "listen_addresses = '*'" >>"$data/postgresql.conf"
    printf '%s\n' 'local all all trust' 'host all all all scram-sha-256' >"$data/pg_hba.conf"
    trap - EXIT
    log "serving database '$DB' on port 5432 (user postgres); stop the container to end"
    if (( EUID == 0 )); then
        exec gosu postgres postgres -D "$data"
    fi
    exec postgres -D "$data"
fi
