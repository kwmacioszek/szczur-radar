<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-25: jedna reguła adresu check-in alarmów

- **Plan**: context/changes/alert-ping-single-rule/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-28
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 2 observations

Phase 3 is reviewed although its Manual rows 3.5 and 3.6 are open: both can only be done after the human merge, and all its Automated rows are done (7a3a68b).

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria at HEAD (dac3faa): 1.1–1.3, 2.1, 2.5, 3.2 re-run locally, PASS. 2.2, 2.3, 3.1, 3.3 and 3.4 PASS on the CI run of 7a3a68b (conclusion `success`, all 9 jobs); since 7a3a68b only `plan.md` and `change.md` changed. 1.4 and 2.4 (break-checks) were done during implementation. 3.5 and 3.6 (Manual) pending until after the merge, as planned.

## Findings

### F1 — The shell copies accept non-ASCII addresses under a UTF-8 locale

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/backup.sh:56 (the shared block; also tools/pipeline-trigger/trigger.sh, tools/healthchecks/checks.sh:54)
- **Detail**: In bash, `[A-Za-z]` under a UTF-8 locale of glibc matches non-ASCII letters. The `postgres:18` image (the db-backup cron) sets `LANG=en_US.utf8`, and there the block accepts `https://hé.example/x` and `https://hc-ping.example/é` (checked: `docker run postgres:18`). Java and the musl trigger image refuse both. checks.sh runs on the operator's Mac under `en_US.UTF-8`, where bash also accepts them. So "one rule" holds only for ASCII input. Step 9 runs in the runner's locale and the table has no non-ASCII row, so the guard cannot see this. The plan's claim that the image tests cover glibc vs musl was true only for the cases it listed. Low practical risk (the operator sets the value; production values are ASCII from `checks.sh apply`), but it is exactly the kind of silent divergence S-25 is meant to close.
- **Fix**: Refuse non-ASCII explicitly in the shared block and in checks.sh (e.g. `|| $ping_url == *[![:ascii:]]*` in the refusal condition). Add rows such as `refuse https://hé.example/…` and `refuse https://hc-ping.example/é` to `ping-urls.txt`. Run step 9's per-row `bash -c` under a UTF-8 locale when one is available (`locale -a`), so the guard sees what glibc does.
  - Strength: Java (`Pattern` is ASCII-only without flags) already refuses these, so the four copies decide the same everywhere. The guard was checked in both images by the reviewer.
  - Tradeoff: One more condition in a byte-identical block (both crons) and one in checks.sh; step 9's literal check is unaffected.
  - Confidence: HIGH — reproduced in the real `postgres:18` image.
  - Blind spot: Whether every CI runner has a UTF-8 locale for step 9 to use; without one the new rows still pass through the ASCII guard, but step 9 would not prove the locale effect.
- **Decision**: FIXED

### F2 — A port past five digits passes the pattern and leaves the API without a host

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertSettings.java:69
- **Detail**: `https://hc-ping.example:99999999999/x` matches `PING_PATTERN`. `new URI` then parses it as a registry-based authority with `getHost() == null` (checked). The old code refused this at start through `uri.getHost() == null`. Now the server starts and every alert fails in `HttpRequest.newBuilder` (the channel is dead without a start failure; `HealthchecksAlerts` logs only the exception class, so nothing leaks). No table row covers it, so `anAddressOfTheTableIsAccepted`'s host check never fires on it.
- **Fix**: Change `(:[0-9]+)?` to `(:[0-9]{1,5})?` in all four literals and add `refuse https://hc-ping.example:99999999999/…` to the table. Optionally also keep `uri.getHost() != null` in Java as a second guard; with `{1,5}` it cannot break parity.
- **Decision**: FIXED

### F3 — The roadmap changed without its SVG data

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/render_roadmap_svg.py:191
- **Detail**: `context/foundation/roadmap.md` now says S-25 is `in-progress` (commit f9d920c). REPOSITORY_GUIDELINES.md requires updating `tools/render_roadmap_svg.py` and regenerating `docs/visuals/roadmap.svg` whenever the roadmap changes. The script still has S-25 as `"ready"`. Its `STATUS` map has `"progress"` ("W toku"), so the in-progress state can be shown (the earlier note "the script has no planning category" was right for `planning`, not for `in-progress`).
- **Fix**: Set S-25's status to `"progress"` in `tools/render_roadmap_svg.py` and run `python3 tools/render_roadmap_svg.py`.
- **Decision**: FIXED

### F4 — A very long value fails the start with StackOverflowError instead of the settings message

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertSettings.java:44
- **Detail**: `java.util.regex` recurses on the `(…|%XX)*` path group and the `(label[.])*` group: a path of about 3,850 characters (or about 5k labels) throws `StackOverflowError` (reviewer's measurement, Java 25). No ReDoS (failing inputs are linear) and nothing leaks (the error has no message), but the start fails without the formatted `APPLICATION FAILED TO START` block. Reachable only through an operator-set variable.
- **Fix**: Optional: refuse a value longer than e.g. 2048 characters before matching, mirrored as `${#ping_url}` in the shell block and checks.sh (and one table row).
- **Decision**: FIXED (differently, after a red CI on c93184f: the first fix, a cap of 2048, still overflowed the stack in `anAddressOfAtMostTheCapIsAcceptedAndALongerOneRefused` on the CI runner. The path part of the pattern is rewritten as `(/C*(%XX C*)*)?`, the same language without recursion per character, and the cap is 512 in all copies; measured margin at least 3.4x with `-Xss512k`. New test `theDeepestShapesAtTheCapAreDecided`.)

### F5 — checks.sh's refusal text still says "no https:// ping URL"

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/healthchecks/checks.sh:223, tools/healthchecks/checks.sh:242
- **Detail**: `fail "$name: the answer has no https:// ping URL"` and `"…the check has no https:// ping URL"` now also fire for an https address with user info, an IP host or a bad `%`, which can mislead the operator. The plan did not ask for a message change; the deploy-plan S-25 section quotes the current text.
- **Fix**: Reword both to e.g. "…has no ping URL the API and the crons accept (see AlertSettings.PING_PATTERN)", and update the deploy-plan quote if it repeats the text.
- **Decision**: FIXED
