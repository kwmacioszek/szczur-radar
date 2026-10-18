# The deploy gate's rules (S-14, safe-deploy-gate; see README.md), run by gate.sh as
#   jq --arg env <target-env> --arg pid <project-id> -f gate.jq plan.json
# Input: the output of `railway config plan --json`. Output: one report object
#   {envProblems: [text], readable: bool, count, changes: [{change, label, restarts, reasons}],
#    rejected: <number of changes with a reason>, restarts: [address]}
# The rules read the plan's structured fields (kind, field, severity, deployEffect, before, after),
# never its text. Anything the gate does not know is refused (fail closed); the only tolerance is a
# change without deployEffect, which counts as a restart.

# The kinds of change an unattended apply may make. Every other kind is refused, whatever its severity
# (resource.delete, variable.delete, domain.delete, a kind this gate has never seen).
def allowed_kinds: ["resource.create", "resource.update", "variable.set", "domain.create"];

# The keys whose values differ between two objects (a missing key counts as null).
def changed_keys($before; $after):
  [$before, $after] | map(keys) | add | unique | map(select($before[.] != $after[.]));

# A source change that only turns Railway "Wait for CI" on (source.checkSuites → true): it makes a
# deploy wait for CI and rewrites nothing else of the source (repo, branch, root directory).
def enables_wait_for_ci_only:
  (.before // {}) as $before | (.after // {}) as $after
  | ($before | type) == "object" and ($after | type) == "object"
    and $after.checkSuites == true
    and changed_keys($before; $after) == ["checkSuites"];

# Does applying the change redeploy its resource? Missing deployEffect counts as a deploy.
def restarts: .deployEffect != "none";

# Why the gate refuses the change (empty: allowed).
def reasons:
  [ if .severity == "destructive" then "destructive change"
    elif .severity != "safe" then "unknown severity \(.severity | tojson)"
    else empty end,
    if (.kind | type) != "string" then "change without a kind (unknown plan format)"
    elif (.kind as $kind | allowed_kinds | index([$kind])) == null then "kind \(.kind) is not allowed"
    else empty end,
    if .kind == "resource.update" then
      if (.field | type) != "string" then "resource.update without a field (unknown plan format)"
      elif .field == "source" and (enables_wait_for_ci_only | not) then
        "source change (only source.checkSuites → true may pass)"
      else empty end
    else empty end,
    if restarts and (.address | type) != "string" then
      "a change that redeploys a resource names no address (unknown plan format)"
    else empty end
  ];

# One line that says what the change is: its summary, else its kind and address.
def describe:
  .summary // ([.kind, .address, .field // .variable] | map(select(. != null) | tostring) | join(" "))
  | if . == "" then "(a change without kind, address or summary)" else . end;

# The array without its repeated values, in their first order.
def ordered_unique: reduce .[] as $item ([]; if index([$item]) == null then . + [$item] else . end);

(if type == "object" then . else {} end) as $plan
| [ if $plan.ok != true then "the plan is not ok (see its diagnostics)" else empty end,
    if $plan.currentEnvironment.environmentName != $env then
      "it targets environment \($plan.currentEnvironment.environmentName | tojson), expected \($env | tojson)"
    else empty end,
    if $plan.currentEnvironment.projectId != $pid then
      "it targets project \($plan.currentEnvironment.projectId | tojson), expected \($pid | tojson)"
    else empty end
  ] as $env_problems
| ($plan.changeSet.changes | type == "array" and all(.[]; type == "object")) as $readable
| (if $readable then $plan.changeSet.changes else [] end
   | map({change: ., label: describe, restarts: restarts, reasons: reasons})) as $changes
| {
    envProblems: $env_problems,
    readable: $readable,
    count: ($changes | length),
    changes: $changes,
    rejected: ($changes | map(select(.reasons | length > 0)) | length),
    restarts: ($changes | map(select(.restarts) | .change.address | select(type == "string")) | ordered_unique)
  }
