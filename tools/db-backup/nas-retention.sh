#!/usr/bin/env bash
# Retention of the backup copies on the NAS (F-01, production-data-gate; see README.md). Synology Cloud
# Sync downloads the bucket without deleting the files removed there, so an emptied bucket does not
# empty the NAS copy. This script, run daily by the DSM Task Scheduler after the sync, keeps the newest
# BACKUP_KEEP_DAILY (default 14) dumps in <dir>/daily and the newest BACKUP_KEEP_MONTHLY (default 12)
# in <dir>/monthly, each with its manifest, and deletes the older ones. The newest dump is never
# deleted. Plain bash and coreutils: no rclone, no Docker.
#   nas-retention.sh <dir> [--dry-run]
set -euo pipefail
export LC_ALL=C

readonly PREFIX=szczur-radar
readonly USAGE="usage: nas-retention.sh <dir> [--dry-run]"

log() { printf '%s nas-retention: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() { log "FAILED: $*"; exit 1; }

dir="" dry_run=false
for arg in "$@"; do
    case $arg in
        --dry-run) dry_run=true ;;
        -*) fail "unknown option $arg; $USAGE" ;;
        *) [[ -z $dir ]] || fail "$USAGE"; dir=$arg ;;
    esac
done
[[ -n $dir ]] || fail "$USAGE"
[[ -d $dir ]] || fail "not a directory: $dir"
keep_daily=${BACKUP_KEEP_DAILY:-14}
keep_monthly=${BACKUP_KEEP_MONTHLY:-12}
[[ $keep_daily =~ ^[1-9][0-9]*$ ]] || fail "BACKUP_KEEP_DAILY must be a positive integer"
[[ $keep_monthly =~ ^[1-9][0-9]*$ ]] || fail "BACKUP_KEEP_MONTHLY must be a positive integer"

# Today in UTC, as the dump names write it: a name dated later is not a dump this tool made (or the
# NAS clock is wrong) and is never counted or deleted.
readonly TODAY=$(date -u +%Y%m%d) THIS_MONTH=$(date -u +%Y-%m)

# Keeps the newest $2 dumps in <dir>/$1; prints every file it deletes (or would delete) on stdout. Only
# the exact names backup.sh writes count: daily <PREFIX>-<yyyymmddThhmmssZ>.dump, monthly
# <PREFIX>-<yyyy-mm>.dump; anything else in the folder is left alone and logged.
prune() {
    local sub=$1 keep=$2 file target base
    local -a dumps=() candidates=()
    if [[ ! -d $dir/$sub ]]; then
        log "$sub: no directory, nothing to do"
        return 0
    fi
    shopt -s nullglob
    # The glob is sorted (LC_ALL=C) and the names sort by time: oldest first.
    candidates=("$dir/$sub/"*.dump)
    shopt -u nullglob
    for file in "${candidates[@]}"; do
        base=${file##*/}
        if [[ $sub == daily && $base =~ ^$PREFIX-([0-9]{8})T[0-9]{6}Z\.dump$ && ! ${BASH_REMATCH[1]} > $TODAY ]] \
                || [[ $sub == monthly && $base =~ ^$PREFIX-([0-9]{4}-[0-9]{2})\.dump$ && ! ${BASH_REMATCH[1]} > $THIS_MONTH ]]; then
            dumps+=("$file")
        else
            log "$sub: ignoring $base (not a dump name this tool writes, or dated in the future)"
        fi
    done
    local excess=$(( ${#dumps[@]} - keep ))
    if (( excess <= 0 )); then
        log "$sub: ${#dumps[@]} dumps, keeping $keep, nothing to delete"
        return 0
    fi
    for file in "${dumps[@]:0:excess}"; do
        for target in "$file" "${file%.dump}.manifest.json"; do
            [[ -e $target ]] || continue
            if $dry_run; then
                echo "would delete $target"
            else
                rm -f -- "$target"
                echo "deleted $target"
            fi
        done
    done
    log "$sub: ${#dumps[@]} dumps, keeping the newest $keep"
}

prune daily "$keep_daily"
prune monthly "$keep_monthly"
