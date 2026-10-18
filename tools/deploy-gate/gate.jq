# The deploy gate's rules (S-14, safe-deploy-gate; see README.md), run by gate.sh as
#   jq --arg env <target-env> --arg pid <project-id> -f gate.jq plan.json
# Input: the output of `railway config plan --json`. Output: one report object
#   {envProblems: [text], readable: bool, count, changes: [{change, label, restarts, target, reasons}],
#    rejected: <number of changes with a reason>, restarts: [resource name]}
# The CLI's --json (5.67.1) gives each change only kind, severity, summary and details (its runner
# keeps no other field), so the rules read those: kind and severity as values, details and summary in
# the CLI's own fixed formats (src/iac/change_set.rs). Optional structured fields (field, deployEffect,
# address) are honoured when a CLI gives them. Anything the gate does not know is refused (fail
# closed); the only tolerance is a change without deployEffect, which counts as a restart.

# The kinds of change an unattended apply may make. Every other kind is refused, whatever its severity
# (resource.delete, variable.delete, domain.delete, a kind this gate has never seen).
def allowed_kinds: ["resource.create", "resource.update", "variable.set", "domain.create"];

# The only source detail an unattended apply may make: turning Railway "Wait for CI" on. The CLI writes
# a changed leaf as `<path> (<before> → <after>)`; a missing key is null.
def wait_for_ci_detail: "^source\\.checkSuites \\((null|false) → true\\)$";

# A summary that names the source field (`Update <name> source.branch, source.checkSuites`).
def source_summary: "(^|[ ,])source([.]|$| )";

def is_strings: type == "array" and all(.[]; type == "string");

# Does the resource.update touch the service source (repo, branch, root directory, Wait for CI)?
def touches_source:
  .field == "source"
  or ((.details | is_strings) and any(.details[]; startswith("source.")))
  or ((.summary | type) == "string" and (.summary | test(source_summary)));

# A source change that only turns Wait for CI on: at least one detail, and every detail is that one.
def enables_wait_for_ci_only:
  (.details | is_strings) and (.details | length) > 0 and all(.details[]; test(wait_for_ci_detail));

# Does applying the change redeploy its resource? Missing deployEffect counts as a deploy.
def restarts: .deployEffect != "none";

# The address of a desiredGraph resource (the CLI writes `<type>.<name>` when it gives none).
def resource_address: .address // "\(.type).\(.name)";

# Does the change's summary name the desiredGraph resource $r? The CLI's summary formats per kind:
#   resource.create  `Create <type> <name>`, `Create <type> <name> (branch: … )`
#   resource.update  `Update <name> <path>[, <path>…][ and N more]`, `Update <name> <field>`,
#                    `Rename <type> <old> to <name>`, `Resize volume <name> from …`
#   variable.set     `Set variable <name>.<KEY>` (new), `Update variable <name>.<KEY>`
#   domain.create    not produced by the CLI itself (only by Railway's server-side preview), so its
#                    format is unknown: the summary must name the resource as a whole word.
def names_resource($r):
  .kind as $kind | .summary as $s | $r.name as $n
  | if $kind == "resource.create" then
      $s == "Create \($r.type) \($n)" or ($s | startswith("Create \($r.type) \($n) ("))
    elif $kind == "resource.update" then
      ($s | startswith("Update \($n) "))
      or (($s | startswith("Rename ")) and ($s | endswith(" to \($n)")))
      or ($s | startswith("Resize volume \($n) from "))
    elif $kind == "variable.set" then
      ["Set variable \($n).", "Update variable \($n)."] | any(. as $prefix
        | ($s | startswith($prefix)) and ($s | ltrimstr($prefix) | test("^[^ .]+$")))
    elif $kind == "domain.create" then
      any($s | splits("[ ,()]+"); . == $n)
    else false end;

# The name of the one desiredGraph resource the change redeploys, or null when it is not exactly one:
# by the change's address when the CLI gives one, else by its summary.
def target($resources):
  . as $change
  | [ $resources[]
      | select(if ($change.address | type) == "string" then resource_address == $change.address
               elif ($change.summary | type) == "string" then . as $r | $change | names_resource($r)
               else false end)
      | .name ]
  | unique
  | if length == 1 then .[0] else null end;

# Why the gate refuses the change (empty: allowed).
def reasons($target):
  [ if .severity == "destructive" then "destructive change"
    elif .severity != "safe" then "unknown severity \(.severity | tojson)"
    else empty end,
    if (.kind | type) != "string" then "change without a kind (unknown plan format)"
    elif (.kind as $kind | allowed_kinds | index([$kind])) == null then "kind \(.kind) is not allowed"
    else
      if .kind == "resource.update" and (.details | is_strings | not) then
        "resource.update without a details list (unknown plan format)"
      elif .kind == "resource.update" and touches_source and (enables_wait_for_ci_only | not) then
        "source change (only source.checkSuites → true may pass)"
      else empty end,
      if restarts and $target == null then
        "a change that redeploys a resource names no single resource of desiredGraph (unknown plan format)"
      else empty end
    end
  ];

# One line that says what the change is: its summary, else its kind.
def describe:
  .summary // .kind // null
  | if . == null or . == "" then "(a change without kind or summary)" else tostring end;

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
| ($plan.desiredGraph.resources
   | if type == "array" then map(select(type == "object" and (.name | type) == "string")) else [] end
  ) as $resources
| ($plan.changeSet.changes | type == "array" and all(.[]; type == "object")) as $readable
| (if $readable then $plan.changeSet.changes else [] end
   | map((if restarts then target($resources) else null end) as $target
         | {change: ., label: describe, restarts: restarts, target: $target, reasons: reasons($target)})
  ) as $changes
| {
    envProblems: $env_problems,
    readable: $readable,
    count: ($changes | length),
    changes: $changes,
    rejected: ($changes | map(select(.reasons | length > 0)) | length),
    restarts: ($changes | map(select(.restarts) | .target | select(type == "string")) | ordered_unique)
  }
