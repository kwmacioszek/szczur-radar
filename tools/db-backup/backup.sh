#!/usr/bin/env bash
# One run of the database backup (F-01, production-data-gate; see README.md).
# pg_dump of the database named by PG*, a full restore check of that dump in a throwaway cluster
# (restore-check.sh), then the upload of the dump and its manifest to BACKUP_REMOTE with rclone, a
# monthly copy and the retention. Nothing is uploaded unless the restore check passes, and every
# failure exits non-zero, so Railway marks the cron run as failed.
# Never `set -x`: the environment holds the database password and the S3 keys.
set -euo pipefail

# `docker run <image> restore-check.sh …` runs another tool of the image (ENTRYPOINT is this script).
if (( $# > 0 )); then
    exec "$@"
fi

readonly PREFIX=szczur-radar
# Session rows are not backed up: their ids would let whoever holds a dump take over a live session.
# After a restore everyone logs in again.
readonly EXCLUDED_DATA='public.spring_session*'
# Rows counted before and after the dump (restore-check.sh compares them); none of these tables has a
# delete path in the application, so the counts only grow while the dump runs.
readonly COUNTED_TABLES=(users pipeline_runs posts assessments raw_payloads)

log() { printf '%s backup: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() { log "FAILED: $*"; exit 1; }

for name in PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE; do
    [[ -n ${!name:-} ]] || fail "$name is not set"
done
keep_daily=${BACKUP_KEEP_DAILY:-14}
keep_monthly=${BACKUP_KEEP_MONTHLY:-12}
[[ $keep_daily =~ ^[1-9][0-9]*$ ]] || fail "BACKUP_KEEP_DAILY must be a positive integer"
[[ $keep_monthly =~ ^[1-9][0-9]*$ ]] || fail "BACKUP_KEEP_MONTHLY must be a positive integer"

# Where the copies go: an rclone remote, `:s3:<bucket>` on Railway (built from S3_BUCKET) or e.g.
# `:local:/out` in test.sh.
remote=${BACKUP_REMOTE:-}
if [[ -z $remote ]]; then
    [[ -n ${S3_BUCKET:-} ]] || fail "set BACKUP_REMOTE or S3_BUCKET"
    remote=":s3:$S3_BUCKET"
fi
if [[ $remote == :s3:* ]]; then
    for name in S3_ENDPOINT S3_ACCESS_KEY_ID S3_SECRET_ACCESS_KEY; do
        [[ -n ${!name:-} ]] || fail "$name is not set"
    done
    # rclone reads the options of an on-the-fly remote from RCLONE_S3_* variables, so the keys never
    # appear on a command line. Railway buckets use virtual-hosted-style URLs (an older bucket may need
    # S3_FORCE_PATH_STYLE=true, its Credentials tab says so), and their keys cannot create buckets.
    export RCLONE_S3_PROVIDER=Other RCLONE_S3_ENV_AUTH=false RCLONE_S3_NO_CHECK_BUCKET=true
    export RCLONE_S3_ENDPOINT=$S3_ENDPOINT RCLONE_S3_REGION=${S3_REGION:-auto}
    export RCLONE_S3_ACCESS_KEY_ID=$S3_ACCESS_KEY_ID RCLONE_S3_SECRET_ACCESS_KEY=$S3_SECRET_ACCESS_KEY
    export RCLONE_S3_FORCE_PATH_STYLE=${S3_FORCE_PATH_STYLE:-false}
fi
# No rclone config file: everything comes from the environment.
export RCLONE_CONFIG=/dev/null

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

sql() { psql -X -A -t -q -v ON_ERROR_STOP=1 -c "$1"; }

# Row counts of COUNTED_TABLES as a JSON object.
count_rows() {
    local query="select json_build_object(" separator="" table
    for table in "${COUNTED_TABLES[@]}"; do
        query+="$separator'$table', (select count(*) from public.$table)"
        separator=", "
    done
    sql "$query)"
}

# The backup files in a directory of the remote, one name per line, oldest first (the names sort by
# time). A directory that does not exist yet is empty.
list_files() {
    local listing
    if ! listing=$(rclone lsf --files-only "$remote/$1" 2>"$work/lsf.err"); then
        if grep -q 'directory not found' "$work/lsf.err"; then
            return 0
        fi
        cat "$work/lsf.err" >&2
        return 1
    fi
    grep -E "^$PREFIX-.+\.(dump|manifest\.json)\$" <<<"$listing" | LC_ALL=C sort || true
}

# Keeps the newest $2 dumps in directory $1 of the remote and deletes the older ones with their
# manifests. keep >= 1, so the newest dump is never deleted.
prune() {
    local dir=$1 keep=$2 files old manifest
    local -a dumps
    files=$(list_files "$dir")
    mapfile -t dumps < <(grep -E '\.dump$' <<<"$files" || true)
    local excess=$(( ${#dumps[@]} - keep ))
    for old in "${dumps[@]:0:$(( excess > 0 ? excess : 0 ))}"; do
        rclone deletefile "$remote/$dir/$old"
        manifest=${old%.dump}.manifest.json
        if grep -qxF "$manifest" <<<"$files"; then
            rclone deletefile "$remote/$dir/$manifest"
        fi
        log "retention: deleted $dir/$old"
    done
}

# Railway's private network may need a few seconds after the container starts.
for attempt in $(seq 1 30); do
    if pg_isready -q; then
        break
    fi
    (( attempt < 30 )) || fail "database $PGHOST:$PGPORT is not accepting connections"
    sleep 2
done

now=$(date -u +%s)
name="$PREFIX-$(date -u -d "@$now" +%Y%m%dT%H%M%SZ)"
monthly="$PREFIX-$(date -u -d "@$now" +%Y-%m)"
dump=$work/$name.dump
manifest=$work/$name.manifest.json

server_version=$(sql 'show server_version')
flyway_version=$(sql "select version from flyway_schema_history
    where success and version is not null order by installed_rank desc limit 1")
[[ -n $flyway_version ]] || fail "flyway_schema_history has no successful migration"

counts_before=$(count_rows)
log "dumping database $PGDATABASE on $PGHOST (server $server_version, Flyway V$flyway_version)"
pg_dump --format=custom --no-owner "--exclude-table-data=$EXCLUDED_DATA" --file="$dump"
counts_after=$(count_rows)

# --exclude-table-data does not complain when its pattern matches nothing: check the dump's contents.
toc=$(pg_restore --list "$dump") || fail "pg_restore cannot read the dump"
grep -qE ' TABLE DATA public posts( |$)' <<<"$toc" || fail "the dump has no data for public.posts"
if grep -qE ' TABLE DATA public spring_session' <<<"$toc"; then
    fail "the dump contains session data ($EXCLUDED_DATA)"
fi

size=$(stat -c %s "$dump")
sha256=$(sha256sum "$dump" | cut -d ' ' -f 1)
jq -n \
    --arg created_at "$(date -u -d "@$now" +%Y-%m-%dT%H:%M:%SZ)" \
    --arg file "$name.dump" \
    --arg sha256 "$sha256" \
    --argjson size "$size" \
    --arg server_version "$server_version" \
    --arg pg_dump_version "$(pg_dump --version)" \
    --arg flyway_version "$flyway_version" \
    --arg excluded "$EXCLUDED_DATA" \
    --argjson counts_before "$counts_before" \
    --argjson counts_after "$counts_after" \
    '{format: 1, created_at: $created_at, file: $file, sha256: $sha256, size_bytes: $size,
      server_version: $server_version, pg_dump_version: $pg_dump_version,
      flyway_version: $flyway_version, excluded_table_data: [$excluded],
      counts_before: $counts_before, counts_after: $counts_after}' >"$manifest"

log "dump $name.dump: $(numfmt --to=iec "$size")B, sha256 $sha256; checking the restore"
started=$SECONDS
restore-check.sh "$dump" "$manifest" || fail "restore-check failed; nothing uploaded"
jq --argjson seconds $(( SECONDS - started )) '.restore_check = {ok: true, seconds: $seconds}' \
    "$manifest" >"$manifest.tmp"
mv "$manifest.tmp" "$manifest"

# The dump first, its manifest second: a manifest next to a dump means the pair is complete.
rclone copyto "$dump" "$remote/daily/$name.dump"
rclone copyto "$manifest" "$remote/daily/$name.manifest.json"
log "uploaded daily/$name.dump and its manifest"

# The first successful dump of a UTC month becomes its monthly copy (a server-side copy: no upload).
existing_monthly=$(list_files monthly)
if ! grep -qxF "$monthly.dump" <<<"$existing_monthly"; then
    rclone copyto "$remote/daily/$name.dump" "$remote/monthly/$monthly.dump"
    rclone copyto "$remote/daily/$name.manifest.json" "$remote/monthly/$monthly.manifest.json"
    log "monthly copy: monthly/$monthly.dump"
fi

prune daily "$keep_daily"
prune monthly "$keep_monthly"
log "OK: daily/$name.dump; keeping $keep_daily daily and $keep_monthly monthly dumps"
