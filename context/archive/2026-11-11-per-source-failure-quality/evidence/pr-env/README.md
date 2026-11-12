# PR env verification (szczur-radar-pr-166, 2026-11-11, plan Phase 4)

PR env `szczur-radar-pr-166`, every service deployed from `cb55f3d`. Secrets (activation link, password, session cookie, trigger token, ntfy topic) stayed in shell variables and scratchpad files outside the repo; none is in this folder.

The three `quality-section-*.png` were retaken 2026-11-11 after review F2 (new wording "próby z wywołaniem modelu (bez prefiltra), z ponowieniami") on the PR env at commit d14f053 (API deployment `SUCCESS`; the web build of 76f2952 still served, `SKIPPED` at d14f053 with no web change between them), with the same admin after `reset-password`.

## Steps

1. One-time admin (lesson "W PR env agent sam zakłada jednorazowe konto admina…"): `railway ssh -s szczur-radar-api -e szczur-radar-pr-166 -- java -jar /app/target/szczur-radar-api.jar create-user --email agent-per-source-failure-quality@example.com --role admin`, link captured into a shell variable, random password from `openssl rand`, `POST /api/auth/activation` → 204, `POST /api/auth/login` (header `Origin`) → 200, cookie jar in the scratchpad.
2. **4.2** `GET /api/admin/assessment-quality` with the session and `Origin` → 200, `jq -e 'has("from") and has("to") and has("windowDays") and has("minAttempts") and has("sources")'` → `true`; without a session → 401. Before the night `sources` was `[]` (fresh database); after it:

   ```json
   {"from":"2026-11-05","to":"2026-11-11","windowDays":7,"minAttempts":100,"sources":[
     {"source":"wykop","attempts":734,"failed":43,"classes":[{"failureClass":"schema_violation","count":30},{"failureClass":"fabricated_evidence","count":6},{"failureClass":"domain_invariant_violation","count":7}],"thresholdPercent":5,"status":"above_threshold"},
     {"source":"reddit","attempts":105,"failed":23,"classes":[{"failureClass":"invalid_structured_output","count":5},{"failureClass":"schema_violation","count":16},{"failureClass":"fabricated_evidence","count":1},{"failureClass":"domain_invariant_violation","count":1}],"thresholdPercent":25,"status":"ok"}]}
   ```

3. **4.3** Night by the S-34 procedure: a test `PIPELINE_TRIGGER_TOKEN` and `NTFY_URL` (a random test topic, never production's) on the API with `--stdin --skip-deploys` and a redeploy (log: `Operator alerts: sent to the ntfy topic in NTFY_URL`); `TRIGGER_IN_ANY_ENVIRONMENT=true` on `pipeline-trigger`; a temporary schedule `25 14 * * *` (Railway MCP `update-service` + redeploy of the cron; a first try at `15 14 * * *` did not fire). The cron ran at 14:25 UTC: RANGE D-2…D-1 on live Wykop, Reddit and LLM, `partial` (952 fetched, 886 ok, 66 failed, 0 dead). Cron log (read with Railway MCP `get-logs`; `railway logs` follows a running cron deployment):

   ```
   trigger: range 9fff64d4-…: partial (fetched 952, skipped 0, new posts 952, assessed ok 886, assessed failed 66, processing errors 0, dead 0)
   trigger: summary: Night 2026-11-11 (range 2026-11-09…2026-11-10): range partial; errors 0, skipped 0
   trigger: summary: wykop: fetched 847, skipped 0, new 847, leftovers 0; assessed 804, failed 43, dead 0; tokens in 2703617 (cached 2545408), out 134877
   trigger: summary: reddit: fetched 105, skipped 0, new 105, leftovers 0; assessed 82, failed 23, dead 0; tokens in 416942 (cached 385017), out 62377
   trigger: summary: wykop quality 7d (2026-11-05…2026-11-11): failed 5.9% of 734 attempts (schema_violation 4.1%, fabricated_evidence 0.8%, domain_invariant_violation 1.0%); threshold 5%; ABOVE THRESHOLD
   trigger: summary: reddit quality 7d (2026-11-05…2026-11-11): failed 21.9% of 105 attempts (invalid_structured_output 4.8%, schema_violation 15.2%, fabricated_evidence 1.0%, domain_invariant_violation 1.0%); threshold 25%
   trigger: summary: Dead in admin runs since the previous night: none
   trigger: summary: /admin
   trigger: OK: range partial
   ```

   The test topic (`curl -s "https://ntfy.sh/<topic>/json?poll=1&since=2h"`) had two messages at 14:32:53 UTC: `Szczur Radar NIGHT_SUMMARY` (priority 3) with the same lines, and — unlike the plan's expectation — `Szczur Radar ASSESSMENT_QUALITY` (priority 4), because Wykop's first PR-env window (734 attempts, 5.9 %) crossed its 5 % threshold against an empty previous window:

   ```
   ASSESSMENT_QUALITY (the share of failed assessment attempts of a source crossed its threshold over the last 7 days; check the classes in /admin and the failed posts): wykop: failed 5.9% of 734 attempts in 7 days (2026-11-05…2026-11-11), above its threshold 5% (the day before: 0 attempts, fewer than 100) (schema_violation 4.1%, fabricated_evidence 0.8%, domain_invariant_violation 1.0%); /admin
   ```

4. **4.4** `playwright-cli` (headless Chromium, the session cookie loaded with `state-load` from a scratchpad file), `/admin`, element screenshot of the region "Jakość ocen (7 dni)": `quality-section-light-1280.png`, `quality-section-dark-1280.png`, `quality-section-light-375.png`. Wykop shows "ponad progiem", Reddit "w normie", the classes with their Polish labels.

## Cleanup

`railway variable delete NTFY_URL -s szczur-radar-api` and `railway variable delete TRIGGER_IN_ANY_ENVIRONMENT -s pipeline-trigger` in the PR env, cron schedule back to `30 0 * * *` (MCP `update-service`), both services redeployed; checked with `railway variables … --json` (keys absent) and MCP `get-service-config` (`cronSchedule: "30 0 * * *"`). The test `PIPELINE_TRIGGER_TOKEN` stays on the PR env's API, as in S-34 (production does not copy it); the one-time admin account stays in the PR env's database and goes with it.
