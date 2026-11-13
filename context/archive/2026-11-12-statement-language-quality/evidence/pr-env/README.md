# PR env verification (szczur-radar-pr-169, 2026-11-12, plan Phase 5)

PR env `szczur-radar-pr-169`, services deployed from `8b3488d` (CI run 43259530900 green: every job and the four Railway deployments). Secrets (activation link, password, session cookie, trigger token, ntfy topic) stayed in shell variables and scratchpad files outside the repo, deleted after the check; none is in this folder.

## Steps

1. **5.1** `gh pr checks 169`: 12/12 pass (backend verify, Vitest, API and web container smoke, db-backup, pipeline-trigger, deploy-gate, healthchecks, railway config, four PR-env deployments).
2. One-time admin (lesson "W PR env agent sam zakłada jednorazowe konto admina…"): `railway ssh -s szczur-radar-api -e szczur-radar-pr-169 -- java -jar /app/target/szczur-radar-api.jar create-user --email agent-statement-language-quality@example.com --role admin`. The link of that call was not captured (the output was redacted while diagnosing a first failed `railway ssh` call), so a new one came from `reset-password` into a shell variable; random password from `openssl rand`; `POST /api/auth/activation` → 204, `POST /api/auth/login` (header `Origin`) → 200, cookie jar in the scratchpad.
3. **5.2** `GET /api/admin/assessment-quality` with the session and `Origin` → 200. Before the night: `{"from":"2026-11-06","to":"2026-11-12","windowDays":7,"minAttempts":100,"minTheses":500,"sources":[]}` (fresh database). After the night (200 in 1.27 s), `jq -e '.minTheses == 500 and (.sources|length)==2 and (.sources | all(.language != null))'` → `true`:

   ```json
   {"source":"wykop","attempts":662,"failed":33,"status":"ok","language":{"theses":691,"otherLanguage":0,"thresholdPercent":2,"status":"ok"}}
   {"source":"reddit","attempts":123,"failed":28,"status":"ok","language":{"theses":323,"otherLanguage":312,"thresholdPercent":30,"status":"not_enough_data"}}
   ```

   Without a session → 401.
4. **5.3** Night by the S-34/S-35 procedure: a test `PIPELINE_TRIGGER_TOKEN` and `NTFY_URL` (a random test topic on ntfy.sh, never production's) on the API with `--stdin --skip-deploys` and a redeploy; `TRIGGER_IN_ANY_ENVIRONMENT=true` on `pipeline-trigger`; a temporary schedule `5 17 * * *` (Railway MCP `update-service` + redeploy of the cron). The cron ran at 17:05 UTC: RANGE `8e69b208` D-2…D-1 on live Wykop, Reddit and LLM, `partial` (877 fetched, 816 ok, 61 failed, 0 dead), `OK: range partial`. The cron's log (Railway MCP `get-logs`, deployment `b6ed1128`) and the test topic's `Szczur Radar NIGHT_SUMMARY` (priority 3, 17:13:44 UTC) carry the same lines:

   ```
   Night 2026-11-12 (range 2026-11-10…2026-11-11): range partial; errors 0, skipped 0
   wykop: fetched 754, skipped 0, new 754, leftovers 0; assessed 721, failed 33, dead 0; tokens in 2441019 (cached 2300402), out 128083
   reddit: fetched 123, skipped 0, new 123, leftovers 0; assessed 95, failed 28, dead 0; tokens in 488021 (cached 446459), out 68147
   wykop quality 7d (2026-11-06…2026-11-12): failed 5.0% of 662 attempts (invalid_structured_output 0.3%, schema_violation 3.5%, fabricated_evidence 0.6%, domain_invariant_violation 0.6%); threshold 5%
   reddit quality 7d (2026-11-06…2026-11-12): failed 22.8% of 123 attempts (invalid_structured_output 5.7%, schema_violation 14.6%, fabricated_evidence 1.6%, domain_invariant_violation 0.8%); threshold 25%
   wykop language 7d (2026-11-06…2026-11-12): other language 0.0% of 691 theses; threshold 2%
   reddit language 7d (2026-11-06…2026-11-12): 323 theses, fewer than 500, threshold not checked
   Dead in admin runs since the previous night: none
   /admin
   ```

   No `ASSESSMENT_QUALITY`, as the plan expected (no source above a threshold).
5. **5.4** `playwright-cli` (headless Chromium, the session cookie loaded with `state-load` from a scratchpad file), `/admin`, element screenshot of the region "Jakość ocen (7 dni)": `quality-section-light-1280.png`, `quality-section-dark-1280.png`, `quality-section-light-375.png`. Wykop: "tezy w innym języku niż post 0,0% z 691 tez · próg 2%" with "w normie"; Reddit: "tezy w innym języku niż post: 323 tezy · próg 30%" with "za mało danych".

## Observation for the owner

Reddit in the PR env: 312 of 323 theses (96.6 %) are in another language than their English posts. The PR env assesses with CI's LLM settings (`LLM_*` from GitHub variables), not production's model; that model writes the theses of English posts in Polish. Production measured 16.98 % on Reddit (`../README.md`). Below 500 theses the threshold is not checked, so no alarm; with production's model the first night is expected near the production figure. This is the measure doing its job, not a defect of the code: Wykop's 691 theses on the same night give 0.0 %.

## Cleanup

`railway variables delete NTFY_URL -s szczur-radar-api` and `railway variables delete TRIGGER_IN_ANY_ENVIRONMENT -s pipeline-trigger` in the PR env, cron schedule back to `30 0 * * *` (MCP `update-service`), both services redeployed (API `ceaeb49c`, cron `6106be33`); checked with `railway variables … --json` (`NTFY_URL` absent) and MCP `get-service-config` (`cronSchedule: "30 0 * * *"`, variables `API_PORT`, `API_PRIVATE_DOMAIN`, `PIPELINE_TRIGGER_TOKEN`). The test `PIPELINE_TRIGGER_TOKEN` stays on the PR env's API, as in S-34/S-35; the one-time admin account stays in the PR env's database and goes with it.
