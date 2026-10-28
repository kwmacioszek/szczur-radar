#!/usr/bin/env bash
# One run of the database backup (F-01, production-data-gate; see README.md).
# Before the dump, a plausibility check (S-15, backup-check-completeness): posts must not be empty, and
# no counted table may have fewer rows than in the newest manifest in daily/, unless
# BACKUP_ACCEPT_SHRINK names that manifest (once, after a restore on purpose). Then pg_dump of the
# database named by PG*, a full restore check of that dump in a throwaway cluster (restore-check.sh),
# then the upload of the dump and its manifest to BACKUP_REMOTE with rclone, a monthly copy and the
# retention. Nothing is uploaded unless both checks pass, and every failure exits non-zero, so Railway
# marks the cron run as failed. With HEALTHCHECK_PING_URL set it checks in to healthchecks.io (F-04,
# out-of-app-alert-channel): the start, then the exit code with the last log line.
# Never `set -x`: the environment holds the database password, the S3 keys and the secret address
# HEALTHCHECK_PING_URL.
set -euo pipefail

# `docker run <image> restore-check.sh …` runs another tool of the image (ENTRYPOINT is this script).
if (( $# > 0 )); then
    exec "$@"
fi

# TABLES, counted_tables: the classification lives next to this script.
source "$(dirname "${BASH_SOURCE[0]}")/tables.sh"

readonly PREFIX=szczur-radar
# Session rows are not backed up: their ids would let whoever holds a dump take over a live session.
# After a restore everyone logs in again.
readonly EXCLUDED_DATA='public.spring_session*'

log() { printf '%s backup: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() { log "FAILED: $*"; exit 1; }

# Backups run in production only: Railway creates this cron in every environment (a PR env is a copy
# of production), but a PR env's database needs no backup, and a PR env without a database would
# e-mail a failure every night. BACKUP_IN_ANY_ENVIRONMENT=true runs it anyway (to try a change of
# this tool in a PR env). Outside Railway (test.sh, by hand) the variable is unset and it runs.
if [[ -n ${RAILWAY_ENVIRONMENT_NAME:-} && $RAILWAY_ENVIRONMENT_NAME != production
        && ${BACKUP_IN_ANY_ENVIRONMENT:-} != true ]]; then
    log "skipped: backups run only in production (this is $RAILWAY_ENVIRONMENT_NAME)"
    exit 0
fi

# Check-ins to healthchecks.io (F-04, out-of-app-alert-channel; see README.md), only when
# HEALTHCHECK_PING_URL is set: /start before the run, then /<exit code> with the run's last log line as
# the body. The check alarms when the end does not come in time or is not 0. Whoever knows the address
# can check in, so it is a secret: it goes to curl as a config on stdin, never as an argument (visible in
# ps), and is never logged. A failed check-in only logs a warning: it never changes the exit code. The
# address follows the one rule of the API (AlertSettings, S-25): https in any case, a host name (no user
# info, no IP address), an optional port of at most 5 digits, a path without ? or # (which would swallow
# /start and /<exit code>) and with % only as %XX, at most 2048 characters, all ASCII (bash's [A-Za-z]
# also takes non-ASCII letters under a UTF-8 locale of glibc, so the block refuses them itself; the cap
# comes first). Any other address: a warning and no check-in (the check then alarms after its grace time).
# The block between the two "ping-url rule (S-25)" lines is byte for byte the one in
# tools/pipeline-trigger/trigger.sh (another image and build context). Step 9 of tools/healthchecks/test.sh
# compares the two blocks, compares the pattern with AlertSettings.PING_PATTERN and tools/healthchecks/checks.sh,
# and runs the block over tools/healthchecks/fixtures/ping-urls.txt. It may use only log from outside.
# ping-url rule (S-25): begin
readonly ping_pattern='^[Hh][Tt][Tt][Pp][Ss]://([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?[.])*[A-Za-z]([A-Za-z0-9-]*[A-Za-z0-9])?(:[0-9]{1,5})?(/([A-Za-z0-9._~:/@!$&()*+,;=-]|%[0-9A-Fa-f]{2})*)?$'
ping_url=${HEALTHCHECK_PING_URL:-}
ping_url=${ping_url#"${ping_url%%[![:space:]]*}"}
ping_url=${ping_url%"${ping_url##*[![:space:]]}"}
ping_url=${ping_url%/}
unset HEALTHCHECK_PING_URL
if [[ -n $ping_url ]] && { (( ${#ping_url} > 2048 )) || [[ $ping_url == *[![:ascii:]]* || ! $ping_url =~ $ping_pattern ]]; }; then
    log "warning: HEALTHCHECK_PING_URL is not an https:// address with a host name and without ? or #; no check-in"
    ping_url=""
fi
readonly ping_url
# ping-url rule (S-25): end

# One check-in: POST <ping URL>/$1 with the file $2 (default: none) as a text/plain body.
check_in() {
    [[ -n $ping_url ]] || return 0
    local error status=0
    error=$(printf 'url = "%s/%s"\n' "$ping_url" "$1" | curl --disable -fsS -m 10 --retry 3 -o /dev/null \
        --header 'Content-Type: text/plain' --data-binary "@${2:-/dev/null}" --config - 2>&1) || status=$?
    if (( status != 0 )); then
        error=$(tail -n 1 <<<"$error")
        log "warning: check-in $1 to healthchecks.io failed (${error:-curl exit $status}); the exit code stays as it is"
    fi
}

# The last non-empty line of the file $1, at most 1000 bytes (what the check's alarm shows; awk counts
# bytes, so a long non-ASCII line may end in a cut character).
last_line() { awk 'NF { line = $0 } END { printf "%s", substr(line, 1, 1000) }' "$1"; }

# A run that hangs (a lock, a stalled connection) would never fail, and Railway skips every later run
# while one is still active: the whole run gets a hard time limit and fails when it is exceeded. Only
# this outer process sees every result, the time limit too, so it checks in.
if [[ -z ${BACKUP_TIME_LIMITED:-} ]]; then
    export BACKUP_TIME_LIMITED=1
    # A broken /tmp (full, read-only) costs only the check-in's body, never the run or its exit code.
    if checkin_dir=$(mktemp -d 2>/dev/null); then
        trap 'rm -rf "$checkin_dir"' EXIT
        log_copy=$checkin_dir/log body=$checkin_dir/body
    else
        log "warning: no temporary directory; the end check-in goes without the last log line"
        log_copy=/dev/null body=/dev/null
    fi
    check_in start
    # The run's log goes to stderr as before and to a copy, for its last line.
    set +e
    { timeout --kill-after=60s "${BACKUP_TIMEOUT:-2h}" "${BASH_SOURCE[0]}" 2>&1 1>&3 3>&- \
        | tee "$log_copy" >&2; status=${PIPESTATUS[0]}; } 3>&1
    set -e
    if (( status == 124 || status == 137 )); then
        # What fail does, but the check-in goes before the exit: the time limit is 1 like in
        # tools/pipeline-trigger/trigger.sh (S-25).
        log "FAILED: the run exceeded ${BACKUP_TIMEOUT:-2h}" 2>&1 | tee -a "$log_copy" >&2 || true
        status=1
    fi
    last_line "$log_copy" >"$body" 2>/dev/null || true
    check_in "$status" "$body"
    exit "$status"
fi

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

# Row counts of the counted tables (tables.sh) as a JSON object; restore-check.sh compares them.
count_rows() {
    local query="select json_build_object(" separator="" table
    for table in "${counted[@]}"; do
        query+="$separator'$table', (select count(*) from public.$table)"
        separator=", "
    done
    sql "$query)"
}

# The exact names this tool writes: daily/<PREFIX>-<yyyymmddThhmmssZ>, monthly/<PREFIX>-<yyyy-mm>, each
# a .dump and a .manifest.json. Retention counts and deletes only these, so the names sort by time.
name_pattern() {
    case $1 in
        daily) echo "^$PREFIX-[0-9]{8}T[0-9]{6}Z\\.(dump|manifest\\.json)\$" ;;
        monthly) echo "^$PREFIX-[0-9]{4}-[0-9]{2}\\.(dump|manifest\\.json)\$" ;;
    esac
}

# The backup files in a directory of the remote, one name per line, oldest first. A directory that
# does not exist yet is empty. Any other file there is left alone and logged.
list_files() {
    local listing other status=0
    listing=$(rclone lsf --files-only "$remote/$1" 2>"$work/lsf.err") || status=$?
    if (( status == 3 )); then
        return 0 # rclone's exit code for "directory not found"
    fi
    if (( status != 0 )); then
        cat "$work/lsf.err" >&2
        return 1
    fi
    while IFS= read -r other; do
        [[ -z $other ]] || log "ignoring $1/$other (not a name this tool writes)"
    done < <(grep -vE "$(name_pattern "$1")" <<<"$listing" || true)
    grep -E "$(name_pattern "$1")" <<<"$listing" | LC_ALL=C sort || true
}

# Keeps the newest $2 dumps in directory $1 of the remote and deletes the older ones with their
# manifests. keep >= 1, and $3 (this run's dump in that directory) is never deleted.
prune() {
    local dir=$1 keep=$2 current=$3 files old manifest
    local -a dumps
    files=$(list_files "$dir") || fail "cannot list $dir/"
    mapfile -t dumps < <(grep -E '\.dump$' <<<"$files" || true)
    local excess=$(( ${#dumps[@]} - keep ))
    for old in "${dumps[@]}"; do
        (( excess > 0 )) || break
        [[ $old != "$current" ]] || continue
        rclone deletefile "$remote/$dir/$old" || fail "retention: delete $dir/$old"
        manifest=${old%.dump}.manifest.json
        if grep -qxF "$manifest" <<<"$files"; then
            rclone deletefile "$remote/$dir/$manifest" || fail "retention: delete $dir/$manifest"
        fi
        log "retention: deleted $dir/$old"
        excess=$(( excess - 1 ))
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

server_version=$(sql 'show server_version') || fail "cannot query the database"
flyway_version=$(sql "select version from flyway_schema_history
    where success and version is not null order by installed_rank desc limit 1") \
    || fail "cannot read flyway_schema_history (has the API's migrate run?)"
[[ -n $flyway_version ]] || fail "flyway_schema_history has no successful migration"
# Only the tables this schema has: a counted table newer than the source's Flyway version is skipped.
counted=$(counted_tables "$flyway_version") || fail "tables.sh cannot list the tables of Flyway V$flyway_version (see above)"
mapfile -t counted <<<"$counted"

counts_before=$(count_rows) || fail "cannot count the rows of ${counted[*]}"

# Plausibility, before the dump: a copy of a wiped or cut-down database must never reach the bucket,
# where the retention and the NAS mirror would let it push out the good copies. First a minimum that
# no consent lifts.
jq -e '.posts > 0' <<<"$counts_before" >/dev/null \
    || fail "posts is empty: refusing to back up an empty database; nothing dumped"
# Then no counted table may shrink since the newest manifest in daily/ (a name dated after this run is
# skipped, as nas-retention.sh does). No manifest: the bucket's first copy, nothing to compare.
daily_files=$(list_files daily) || fail "cannot list daily/; nothing dumped"
previous=""
skipped=0
while IFS= read -r file; do
    [[ $file == *.manifest.json ]] || continue
    if [[ ${file%.manifest.json} > $name ]]; then
        log "ignoring daily/$file (dated after this run)"
        skipped=$(( skipped + 1 ))
    else
        previous=$file
    fi
done <<<"$daily_files"
# Manifests that are all dated later are not a first copy: the clock or the names are wrong.
[[ -n $previous || $skipped -eq 0 ]] || fail "every manifest in daily/ is dated after this run ($name):" \
    "check the clock, or rename them outside the name pattern (e.g. <name>.manifest.json.broken); nothing dumped"
accept=${BACKUP_ACCEPT_SHRINK:-}
# A pasted value may carry spaces or a newline; a manifest name has none.
accept=${accept//[[:space:]]/}
accepted_shrink=false
if [[ -z $previous ]]; then
    [[ -z $accept ]] || log "ignoring BACKUP_ACCEPT_SHRINK=$accept: there is no manifest in daily/"
else
    # Unreadable or not counts: the shrink cannot be checked, and no consent covers that.
    broken="if it is broken, rename it outside the name pattern (e.g. $previous.broken) and this tool skips it"
    previous_json=$(rclone cat "$remote/daily/$previous" 2>"$work/cat.err") \
        || { cat "$work/cat.err" >&2; fail "cannot read daily/$previous ($broken); nothing dumped"; }
    # posts is always counted, so a manifest without it has nothing worth comparing.
    jq -e '.counts_after | type == "object" and all(.[]; type == "number") and (.posts | type == "number")' \
        <<<"$previous_json" \
        >/dev/null 2>&1 || fail "daily/$previous has no counts_after of numbers ($broken); nothing dumped"
    # Every table counted now and in that manifest (a table newer than the manifest is skipped).
    shrunk=$(jq -r --argjson current "$counts_before" '.counts_after as $before
        | [$current | to_entries[] | select($before[.key] != null and .value < $before[.key])
            | "\(.key) \(.value) < \($before[.key])"] | join(", ")' <<<"$previous_json") \
        || fail "cannot compare the row counts with daily/$previous; nothing dumped"
    if [[ -n $accept && $accept != "${previous%.manifest.json}" ]]; then
        log "ignoring BACKUP_ACCEPT_SHRINK=$accept: the newest manifest is daily/$previous"
        accept=""
    fi
    if [[ -n $shrunk ]]; then
        [[ -n $accept ]] || fail "rows lost since daily/$previous: $shrunk; if the database was restored" \
            "on purpose, set BACKUP_ACCEPT_SHRINK=${previous%.manifest.json} on db-backup and redeploy it;" \
            "nothing dumped"
        log "accepted: $shrunk since daily/$previous (BACKUP_ACCEPT_SHRINK)"
        accepted_shrink=true
    elif [[ -n $accept ]]; then
        log "BACKUP_ACCEPT_SHRINK=$accept not needed: no table shrank since daily/$previous"
    fi
fi

log "dumping database $PGDATABASE on $PGHOST (server $server_version, Flyway V$flyway_version)"
# A lock held by a migration must not stall the run: pg_dump gives up after the lock wait.
pg_dump --format=custom --no-owner --lock-wait-timeout=5min "--exclude-table-data=$EXCLUDED_DATA" \
    --file="$dump" || fail "pg_dump"
counts_after=$(count_rows) || fail "cannot count the rows after the dump"

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
    --arg compared_with "$previous" \
    --argjson accepted_shrink "$accepted_shrink" \
    '{format: 1, created_at: $created_at, file: $file, sha256: $sha256, size_bytes: $size,
      server_version: $server_version, pg_dump_version: $pg_dump_version,
      flyway_version: $flyway_version, excluded_table_data: [$excluded],
      counts_before: $counts_before, counts_after: $counts_after,
      compared_with: (if $compared_with == "" then null else $compared_with end),
      accepted_shrink: $accepted_shrink}' >"$manifest"

log "dump $name.dump: $(numfmt --to=iec "$size")B, sha256 $sha256; checking the restore"
started=$SECONDS
# The restore runs code from the dump as a superuser: the S3 keys stay out of its environment.
env -u RCLONE_S3_ACCESS_KEY_ID -u RCLONE_S3_SECRET_ACCESS_KEY -u S3_ACCESS_KEY_ID -u S3_SECRET_ACCESS_KEY \
    restore-check.sh "$dump" "$manifest" || fail "restore-check failed; nothing uploaded"
jq --argjson seconds $(( SECONDS - started )) '.restore_check = {ok: true, seconds: $seconds}' \
    "$manifest" >"$manifest.tmp"
mv "$manifest.tmp" "$manifest"

# The dump first, its manifest second: a manifest next to a dump means the pair is complete.
rclone copyto "$dump" "$remote/daily/$name.dump" || fail "upload of daily/$name.dump"
rclone copyto "$manifest" "$remote/daily/$name.manifest.json" || fail "upload of daily/$name.manifest.json"
log "uploaded daily/$name.dump and its manifest"

# The first successful dump of a UTC month becomes its monthly copy (a server-side copy: no upload).
# If an earlier run left only half of the month's pair (a failed copy), this run's pair replaces it.
existing_monthly=$(list_files monthly) || fail "cannot list monthly/"
if ! grep -qxF "$monthly.dump" <<<"$existing_monthly" \
        || ! grep -qxF "$monthly.manifest.json" <<<"$existing_monthly"; then
    rclone copyto "$remote/daily/$name.dump" "$remote/monthly/$monthly.dump" || fail "monthly copy"
    rclone copyto "$remote/daily/$name.manifest.json" "$remote/monthly/$monthly.manifest.json" \
        || fail "monthly copy of the manifest"
    log "monthly copy: monthly/$monthly.dump"
fi

prune daily "$keep_daily" "$name.dump"
prune monthly "$keep_monthly" "$monthly.dump"
log "OK: daily/$name.dump; keeping $keep_daily daily and $keep_monthly monthly dumps"
