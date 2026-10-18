# Classification of every table in the database's `public` schema (S-15, backup-check-completeness;
# see README.md), sourced by backup.sh and restore-check.sh: no side effects, only TABLES and the
# functions below.
#   counted  the table is required and only grows: backup.sh counts its rows before and after the dump,
#            restore-check.sh checks the restored count lies between them;
#   present  the table is only required: its rows may disappear, or its data is not in the dump.
# A migration that adds a table adds it here, in the same PR: restore-check.sh rejects a restored table
# that is not listed (`tables: unknown`), and test.sh applies every migration.
# Entries are never removed: a table that a later migration drops gets a fourth field, the last Flyway
# version that has it (and the functions below learn to read it). Without its entry, the older dumps
# that still have the table (monthly copies are kept 12 months) would fail with `tables: unknown`.
# Counted tables have no delete path in the application, so their counts only grow while the dump
# runs (and a counted table that shrinks is a lost row, given the PRD's indefinite retention).

# <table> <first Flyway version that has it> <counted|present>
readonly TABLES=(
    "users 1 counted"
    "flyway_schema_history 1 present"     # Flyway's own (`flyway repair` deletes failed rows); its version is checked separately
    "spring_session 2 present"            # rows deleted (logout, expiry); data excluded from the dump
    "spring_session_attributes 2 present" # deleted with their session; data excluded from the dump
    "pipeline_runs 3 counted"
    "pipeline_run_steps 3 counted"
    "raw_payloads 3 counted"
    "posts 3 counted"
    "assessments 3 counted"
    "activation_tokens 7 present"         # a used activation link deletes its row (auth/activation)
    "notifications 9 counted"
)

# The tables of TABLES that a database at Flyway version $1 has, one per line, whose mode is $2
# (counted or present; `any` for both).
tables_at() {
    local version=$1 want=$2 entry table since mode
    if [[ ! $version =~ ^[0-9]+$ ]]; then
        printf 'tables.sh: Flyway version "%s" is not a whole number\n' "$version" >&2
        return 1
    fi
    for entry in "${TABLES[@]}"; do
        read -r table since mode <<<"$entry"
        # As numbers, never as text: as text '10' < '2', and the next migration is V10 (10#: no octal).
        if (( 10#$since <= 10#$version )) && [[ $want == any || $mode == "$want" ]]; then
            printf '%s\n' "$table"
        fi
    done
}

# The tables a database at Flyway version $1 must have.
required_tables() { tables_at "$1" any; }

# The counted tables of a database at Flyway version $1.
counted_tables() { tables_at "$1" counted; }

# Every classified table, whatever its version: restore-check.sh rejects any other table.
classified_tables() { printf '%s\n' "${TABLES[@]%% *}"; }
