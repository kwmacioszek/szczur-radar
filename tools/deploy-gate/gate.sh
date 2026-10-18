#!/usr/bin/env bash
# The deploy gate of the CI job railway-config (S-14, safe-deploy-gate; see README.md). Classifies the
# output of `railway config plan --json` before an unattended `railway config apply`: whether the plan
# may be applied, why not, which resources the apply redeploys and how many changes it has. The rules
# live in gate.jq and read each change's kind, severity and details (the CLI's --json gives no other
# field) and resolve the resource it redeploys in the plan's desiredGraph.
#
#   gate.sh <plan.json> <target-env> <project-id> >> "$GITHUB_STEP_SUMMARY"
#
# stdout: a markdown summary (number of changes, the changes, the resources the apply restarts).
# stderr: the log, with a GitHub `::error::` line per refusal (stdout goes to the step summary).
# GATE_RESTARTS_FILE (optional): the name of every resource the apply redeploys (desiredGraph
# resources[].name), one per line; emptied first and written only for an allowed plan, so a refused or
# unreadable plan leaves it empty.
# Exit codes: 0 the plan may be applied, 1 refused, 2 a bad invocation or an unreadable plan.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)

log() { printf '%s\n' "$*" >&2; }
usage_error() { log "::error::deploy gate: $*"; exit 2; }

if [[ -n ${GATE_RESTARTS_FILE:-} ]]; then
    : >"$GATE_RESTARTS_FILE" || usage_error "cannot write GATE_RESTARTS_FILE ($GATE_RESTARTS_FILE)"
fi
[[ $# == 3 ]] || usage_error "usage: gate.sh <plan.json> <target-env> <project-id>"
plan=$1 target_env=$2 project_id=$3
[[ -n $target_env && -n $project_id ]] || usage_error "the target environment and the project ID must not be empty"
command -v jq >/dev/null || usage_error "jq is not installed"
[[ -f $plan && -r $plan ]] || usage_error "the plan $plan is not a readable file"
jq -e 'type == "object"' "$plan" >/dev/null 2>&1 || usage_error "the plan $plan is not a JSON object"

report=$(jq --arg env "$target_env" --arg pid "$project_id" -f "$here/gate.jq" "$plan") \
    || usage_error "the plan $plan could not be classified"

# The plan must target exactly the environment the job resolved (rule 1): nothing else is checked then.
if [[ $(jq '.envProblems | length' <<<"$report") != 0 ]]; then
    jq -r --arg env "$target_env" '"::error::plan failed or targets the wrong environment (expected \($env)): "
        + (.envProblems | join("; "))' <<<"$report" >&2
    jq '{ok, currentEnvironment, diagnostics}' "$plan" >&2
    jq -r --arg env "$target_env" '"### Railway config plan for `\($env)`\n\n**Refused:** "
        + (.envProblems | join("; ")) + ". Nothing applied."' <<<"$report"
    exit 1
fi
[[ $(jq '.readable' <<<"$report") == true ]] \
    || usage_error "the plan $plan has no list of changes (changeSet.changes): unknown plan format"

# The step summary: what the apply would change and restart, and what the gate refused.
jq -r --arg env "$target_env" '
    def cell: tostring | gsub("\\|"; "\\|") | gsub("\n"; " ");
    def code: if . == null then "" else "`\(cell)`" end;
    "### Railway config plan for `\($env)`\n",
    "**Changes: \(.count)**\n",
    if .count > 0 then
        "| Change | Kind | Details | Severity | Restarts | Gate |",
        "| --- | --- | --- | --- | --- | --- |",
        (.changes[] | "| \(.label | cell) | \(.change.kind | code) "
            + "| \(if (.change.details | type) == "array" then .change.details | map(code) | join("<br>") else "" end) "
            + "| \(.change.severity // "?" | cell) "
            + "| \(if .restarts then (.target // "?" | code) else "none" end) "
            + "| \(if (.reasons | length) > 0 then "refused: " + (.reasons | join("; ") | cell) else "allowed" end) |"),
        ""
    else empty end,
    if (.restarts | length) > 0 then
        "**Restarts:** \(.restarts | map("`\(cell)`") | join(", ")) (an apply redeploys them)"
    else "**Restarts:** none" end,
    if .rejected > 0 then "\n**Refused:** \(.rejected) change(s) the gate does not allow. Nothing applied." else empty end
' <<<"$report"

if [[ $(jq '.rejected' <<<"$report") != 0 ]]; then
    jq -r --arg env "$target_env" '
        "::error::plan contains changes the deploy gate does not allow; refusing to apply to \($env)",
        (.changes[] | select(.reasons | length > 0) | "::error::\(.label): \(.reasons | join("; "))")
    ' <<<"$report" >&2
    jq '[.changes[] | select(.reasons | length > 0) | .change]' <<<"$report" >&2
    exit 1
fi
if [[ -n ${GATE_RESTARTS_FILE:-} ]]; then
    jq -r '.restarts[]' <<<"$report" >"$GATE_RESTARTS_FILE"
fi
log "deploy gate: $(jq -r '"\(.count) change(s) allowed; restarts: \(if (.restarts | length) > 0 then .restarts | join(", ") else "none" end)"' <<<"$report")"
