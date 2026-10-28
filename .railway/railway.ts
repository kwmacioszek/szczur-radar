import { bucket, defineRailway, github, postgres, preserve, project, ref, service, type ProjectDefinition } from "railway/iac";

// Infrastructure-as-Code for Szczur Radar. This is the source of truth for the
// api, web, and Postgres services, the db-backup cron service and its bucket,
// the pipeline-trigger cron service, and replaces railway.toml for them.
// Validate with `railway config plan` (dry run, deletes nothing) before
// `railway config apply` — see context/deployment/deploy-plan.md, phase 3.
// The `railway/iac` SDK resolves from node_modules at the REPO ROOT (see the
// root package.json), not this directory: run `npm install` at the root first.
//
// NOTE: this SDK is young; field names are written to railway@3.11.0's
// `railway/iac` DSL. If `railway config plan` rejects a field, reconcile it
// against the installed SDK version — the plan gate exists to catch exactly
// that before anything is applied.
//
// `checkSuites: true` on every GitHub source (api, web and the cron services
// db-backup, pipeline-trigger) enables Railway "Wait for CI": deploys wait for
// the whole GitHub Actions workflow conclusion, so a red test skips the deploy
// (CI tests both cron tools: db-backup-test, pipeline-trigger-test). Declared
// here so IaC does not reset the operator's toggle (a plan otherwise shows true
// → null). The CI gate (tools/deploy-gate) refuses every source change of an
// existing service except this one: `source.checkSuites → true` and nothing
// else (S-14, safe-deploy-gate).
//
// `sleepApplication: true` (serverless sleep) on api and web mirrors what the
// operator set in the dashboard. The setting MUST stay declared on these two:
// without it an apply would change it. Postgres and the cron services declare
// nothing (Postgres: see below). Keep this file in sync with production —
// `railway config plan --detailed-exit-code` on `production` must exit 0 before
// any automatic apply (lessons.md, Railway IaC in CI).
//
// PITR is disabled since `api-docker-image` (cost): the operator runs
// `railway postgres pitr disable` and deletes the `Postgres-PITR` bucket in each
// environment BEFORE an apply of this file — otherwise the plan shows a bucket
// delete, which the CI gate refuses. Database backup since F-01: the db-backup
// cron service below (Railway Hobby has no volume backups; deploy-plan.md, F-01).

const REPO = "kwmacioszek/szczur-radar";
const REGION = "europe-west4"; // Railway "EU West (Amsterdam)"
// Fixed private port for the API. Railway does not expose a service's PORT as a
// referenceable variable (a `${{svc.PORT}}` reference resolves to empty), so we
// pin it: the API binds it and the web Caddy proxy targets it deterministically
// over the private network.
const API_INTERNAL_PORT = "8000";
// Per-service resource caps (Railway counts memory in SI bytes): a cost guard
// the operator sets in the dashboard, first 2 vCPU / 4 GB on every service after
// the move to Hobby (F-01, production-data-gate), lowered per service on
// 2026-10-12. db-backup keeps 1 vCPU / 1 GB: it restores every dump into a
// throwaway Postgres cluster inside its own container. pipeline-trigger only
// runs curl and jq while it waits for the run. Declared so the plan
// matches production: CI applies this file on every merge, so a cap changed
// only in the dashboard would be reset here. (An apply of `limitOverride` →
// null left the cap in place in szczur-radar-pr-44 on 2026-10-11, so a missing
// declaration is plan noise, not a removal.)
const limits = (cpu: number, memoryBytes: number) => ({ containers: { cpu, memoryBytes } });
const POSTGRES_LIMITS = limits(1, 1_000_000_000);
const API_LIMITS = limits(2, 2_000_000_000);
const WEB_LIMITS = limits(1, 1_000_000_000);
const DB_BACKUP_LIMITS = limits(1, 1_000_000_000);
const PIPELINE_TRIGGER_LIMITS = limits(0.5, 500_000_000);

export default defineRailway(() => {
  // Railway PR environments track the PR's branch, not `main`. CI sets
  // IAC_SOURCE_BRANCH on pull_request events only, so an apply to a PR env
  // does not rewrite `source.branch`; unset (push to main, local) → `main`.
  // The IaC context does not reliably carry the target environment name, so
  // the production safety net lives in CI: the railway-config job refuses a
  // source change of an existing service, in every environment (creating a new
  // service, e.g. db-backup, is allowed since F-01; turning on Wait for CI,
  // `source.checkSuites → true`, since S-14).
  const branch = process.env.IAC_SOURCE_BRANCH || "main";

  // Postgres. `postgres()` takes no deploy options, so its deploy settings are
  // set on the node directly (DatabaseNode carries `deploy`); Object.assign keeps
  // the non-enumerable `env` refs. The region applies when the database is
  // created: IaC does not move an existing one (a plan with it shows no
  // change). Until 2026-10 production's and the PR envs' Postgres ran in `sfo`
  // — moving it is an operator action per environment (deploy-plan.md,
  // section first-assessed-wykop-feed).
  // Serverless sleep is OFF for Postgres since 2026-10-09: after its move to
  // europe-west4 the sleeping database did not wake on private-network traffic
  // (logs end at "Mounting volume"), so the pre-deploy `migrate` timed out and
  // production lost its database until a manual redeploy — Railway warns a
  // slept service "may require a rebuild to re-live" (docs: Serverless,
  // Caveats). The API and web still sleep.
  // `sleepApplication` is NOT declared for Postgres (since 2026-10-12): Railway
  // stores `false` as no value, so a declared `false` showed `null → false` in
  // every plan, and every apply (each merge to main) restarted the production
  // database. On 2026-10-12 such a restart hung at "Mounting volume" for 9 min
  // until a manual redeploy (deploy-plan.md, F-01). No value means no sleep.
  const db = Object.assign(postgres("postgres", { region: REGION }), {
    deploy: { limitOverride: POSTGRES_LIMITS },
  });

  // Spring Boot backend (Java 25, Maven). No public domain: reachable only
  // through the web proxy over the private network. The JVM binds the wildcard
  // address DUAL-STACK by default — IPv6 for private networking AND IPv4 for
  // Railway's healthcheck — so server.address is deliberately not set; the port
  // comes from PORT (application.properties: server.port=${PORT:8000}).
  // Built from szczur-radar-api/Dockerfile (auto-detected in rootDirectory, like
  // web): JDK 25 build stage with ./mvnw (tests skipped; CI runs them), JRE 25
  // runtime as a non-root user in /app. No `start`: the image ENTRYPOINT runs
  // the jar (no arguments = HTTP server); a Railway start command would replace
  // it and run without shell expansion.
  const api = service("szczur-radar-api", {
    source: github(REPO, { branch, checkSuites: true }),
    rootDirectory: "szczur-radar-api",
    // Watch paths belong under `build`: SDK 3.11 has no top-level
    // `watchPatterns` and silently dropped it, so until 2026-10-15 every commit
    // on main redeployed every service (and interrupted a running pipeline run).
    // Patterns match from the repo root, not from rootDirectory.
    build: { watchPatterns: ["szczur-radar-api/**"] },
    // Flyway migrations run BEFORE each new API version starts (production and
    // PR envs), never at server start (spring.flyway.enabled=false there). If a
    // migration fails, `migrate` exits non-zero, Railway stops the deploy and
    // the previous version keeps serving — the desired behavior. Runs in a
    // container of the same image; the path is relative to WORKDIR /app.
    preDeploy: "java -jar target/szczur-radar-api.jar migrate",
    healthcheck: "/api/health",
    replicas: { [REGION]: 1 },
    deploy: { sleepApplication: true, limitOverride: API_LIMITS },
    env: {
      PORT: API_INTERNAL_PORT,
      // Split Postgres variables for the JDBC URL
      // (jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}) — no DATABASE_URL parsing.
      PGHOST: db.env.PGHOST,
      PGPORT: db.env.PGPORT,
      PGUSER: db.env.PGUSER,
      PGPASSWORD: db.env.PGPASSWORD,
      PGDATABASE: db.env.PGDATABASE,
      // LLM provider of the assessment client (szczur-radar-api/README.md): set by the operator
      // in every environment BEFORE the API deploys — preserve() only keeps an
      // existing value, it never creates one. The key is a secret of that
      // environment (production's is never copied to PR envs). Never values here.
      LLM_API_PROVIDER: preserve(),
      LLM_MODEL: preserve(),
      LLM_API_KEY: preserve(),
      // Optional call settings of the assessment client (szczur-radar-api/README.md):
      // LLM_SERVICE_TIER (default | flex, flex only with openrouter),
      // LLM_REASONING_EFFORT (openrouter only), LLM_TIMEOUT (default 120s). Not secrets;
      // declared so that a value set by the operator is kept, never created here.
      LLM_SERVICE_TIER: preserve(),
      LLM_REASONING_EFFORT: preserve(),
      LLM_TIMEOUT: preserve(),
      // Wykop application keys for the pipeline (szczur-radar-api/README.md, Wykop):
      // optional — without them Wykop is a not-configured source. Production's are
      // set and sealed by the operator; PR envs get the same keys from the GitHub
      // secrets (CI step "Ensure PR-env Wykop keys"). Never values here.
      WYKOP_KEY: preserve(),
      WYKOP_SECRET: preserve(),
      // Optional: parallel LLM calls of a pipeline run (default 4). Not a secret;
      // declared so that a value set by the operator is kept, never created here.
      PIPELINE_LLM_CONCURRENCY: preserve(),
      // Optional: how long a run may go without progress before the watchdog
      // stops it, with a unit (default: the longest single LLM call + 5m, at
      // least 15m — 39m with production's flex and 300s). Not a secret; declared
      // so that a value set by the operator is kept, never created here.
      PIPELINE_STALL_TIMEOUT: preserve(),
      // Shared secret of the daily cron (pipeline-trigger below) on /internal/**
      // (internal/PipelineTriggerToken.java). Optional: unset, every /internal/** request
      // is 401. Set by the operator with --stdin (`openssl rand -hex 32`, at least
      // 32 characters) and sealed on production; a PR env gets one only for a
      // verification of the cron. The cron reads it by reference. Never a value here.
      PIPELINE_TRIGGER_TOKEN: preserve(),
      // Activation links (S-07; szczur-radar-api/README.md, Komendy operatora). Only
      // the operator commands create-user and reset-password read these two, never
      // the server, so a bad value never stops the API.
      // WEB_PUBLIC_URL: this environment's public web address, a Railway template
      // reference resolved per environment (production → its web domain, a PR env →
      // its own). Plain double quotes on purpose: a JS template literal would
      // interpolate `${…}`, and ref()/web.env cannot carry the `https://` prefix. A
      // plain string also does not depend on web being declared below.
      WEB_PUBLIC_URL: "https://${{szczur-radar-web.RAILWAY_PUBLIC_DOMAIN}}",
      // Optional link validity with a unit (default 12h, from 1m to 7d; a bare
      // number is refused). Declared so that a value set by the operator is kept,
      // never created here.
      ACTIVATION_LINK_TTL: preserve(),
      // Out-of-app alert channel (F-04; tools/healthchecks/README.md): the ping URL of
      // this environment's `<env>/api-events` healthchecks.io check, where the API
      // reports operator events (alerts/HealthchecksAlerts, `send-test-alert`). A
      // secret, set by the operator with --stdin and sealed on production, so a new
      // PR env does not copy it; unset (PR envs, by default) = no alerts sent. Never
      // a value here.
      HEALTHCHECK_PING_URL: preserve(),
    },
  });

  // Vue SPA served by Caddy (multi-stage Dockerfile auto-detected). The only
  // public service. Caddy reads the API's private host + port from these
  // reference variables to reverse-proxy /api/* (see szczur-radar-web/Caddyfile).
  const web = service("szczur-radar-web", {
    source: github(REPO, { branch, checkSuites: true }),
    rootDirectory: "szczur-radar-web",
    build: { watchPatterns: ["szczur-radar-web/**"] },
    healthcheck: "/healthz",
    replicas: { [REGION]: 1 },
    deploy: { sleepApplication: true, limitOverride: WEB_LIMITS },
    env: {
      API_PRIVATE_DOMAIN: api.env.RAILWAY_PRIVATE_DOMAIN,
      API_PORT: API_INTERNAL_PORT,
    },
  });

  // Daily database backup (F-01, production-data-gate; tools/db-backup/README.md).
  // Railway Hobby has no volume backups or PITR, so a cron service runs pg_dump,
  // checks the dump by restoring it into a throwaway cluster, and only then
  // uploads it to the bucket below (14 daily + 12 monthly copies); the operator's
  // Synology pulls the bucket with Cloud Sync, so a copy also lives outside
  // Railway. Every environment gets its own bucket with its own keys (a PR env is
  // a copy of production, so it gets the cron too), but backup.sh runs only in
  // production: elsewhere it logs "skipped" and exits 0 (BACKUP_IN_ANY_ENVIRONMENT
  // =true on the service runs it, to try a change of the tool in a PR env; IaC
  // ignores that hand-set value, see `variables.ignore` below; so is
  // BACKUP_ACCEPT_SHRINK, the one-run consent to fewer rows after a restore on
  // purpose, deploy-plan.md F-01 restore runbook). The schedule
  // is UTC and stays at 02:00 with S-05: the daily run (pipeline-trigger below,
  // 00:30 UTC) normally ends before it, and a dump taken while a run writes is
  // still consistent (pg_dump reads one snapshot). A cron run's status is its exit
  // code, and restartPolicyType NEVER keeps a failed run visible instead of retrying it.
  const backups = bucket("db-backups", { region: "ams" });
  // `checkSuites: true` (Wait for CI, since S-14): a merge that changes
  // tools/db-backup with a red `db-backup-test` job does not deploy the cron.
  // Railway leaves it unset on a service that IaC creates, so the first plan
  // after S-14 shows `source.checkSuites (null → true)`; the CI gate lets
  // exactly that source change through (tools/deploy-gate/README.md).
  const dbBackup = service("db-backup", {
    source: github(REPO, { branch, checkSuites: true }),
    rootDirectory: "tools/db-backup",
    build: { watchPatterns: ["tools/db-backup/**"] },
    replicas: { [REGION]: 1 },
    deploy: { cronSchedule: "0 2 * * *", restartPolicyType: "NEVER", limitOverride: DB_BACKUP_LIMITS },
    env: {
      PGHOST: db.env.PGHOST,
      PGPORT: db.env.PGPORT,
      PGUSER: db.env.PGUSER,
      PGPASSWORD: db.env.PGPASSWORD,
      PGDATABASE: db.env.PGDATABASE,
      // backup.sh builds its rclone remote (`:s3:<bucket>`) from S3_BUCKET.
      S3_ENDPOINT: ref(backups, "ENDPOINT"),
      S3_BUCKET: ref(backups, "BUCKET"),
      S3_ACCESS_KEY_ID: ref(backups, "ACCESS_KEY_ID"),
      S3_SECRET_ACCESS_KEY: ref(backups, "SECRET_ACCESS_KEY"),
      S3_REGION: ref(backups, "REGION"),
      BACKUP_KEEP_DAILY: "14",
      BACKUP_KEEP_MONTHLY: "12",
      // Check-ins to this environment's `<env>/db-backup` healthchecks.io check (F-04;
      // tools/db-backup/README.md): the start, then the exit code. A secret ping URL,
      // set by the operator and sealed on production, so a new PR env does not copy
      // it; unset = no check-ins. Never a value here.
      HEALTHCHECK_PING_URL: preserve(),
    },
  });

  // Daily pipeline run (S-05, daily-scheduled-run-freshness;
  // tools/pipeline-trigger/README.md). A cron service wakes the sleeping API over
  // the private network (straight to its port, not through the web's Caddy, which
  // serves only /api/*), starts the daily run with
  // POST /internal/pipeline-runs/daily (the day before yesterday and yesterday,
  // Warsaw calendar) and waits for its result: it exits non-zero when a run failed
  // or was interrupted, so Railway marks the cron run failed (CRASHED) and sends a
  // notification — only with the account's custom notification rule "deployment
  // crashed" for all environments; without it the failure is silent (no e-mail).
  // 00:30 UTC is 01:30 (winter) or 02:30 (summer) in Warsaw: always after
  // midnight, so yesterday is complete, and a normal run ends before the 02:00
  // backup. The token is the API's PIPELINE_TRIGGER_TOKEN by reference: one value,
  // set (and on production sealed) by the operator on the API only. Like db-backup,
  // Railway creates it in every environment, but trigger.sh runs only in
  // production: elsewhere it logs "skipped" and exits 0 without a request
  // (TRIGGER_IN_ANY_ENVIRONMENT=true on the service runs it, to verify it in a PR
  // env; IaC ignores that hand-set value, see `variables.ignore` below).
  // `checkSuites: true` like db-backup: a red `pipeline-trigger-test` job does
  // not deploy the cron.
  const pipelineTrigger = service("pipeline-trigger", {
    source: github(REPO, { branch, checkSuites: true }),
    rootDirectory: "tools/pipeline-trigger",
    build: { watchPatterns: ["tools/pipeline-trigger/**"] },
    replicas: { [REGION]: 1 },
    // TEMPORARY (S-21 Phase 2, reverted by the next commit): a PR-env-only
    // change that redeploys this cron, to exercise apply + the check after it.
    deploy: { cronSchedule: process.env.IAC_SOURCE_BRANCH ? "31 0 * * *" : "30 0 * * *", restartPolicyType: "NEVER", limitOverride: PIPELINE_TRIGGER_LIMITS },
    env: {
      API_PRIVATE_DOMAIN: api.env.RAILWAY_PRIVATE_DOMAIN,
      API_PORT: API_INTERNAL_PORT,
      PIPELINE_TRIGGER_TOKEN: api.env.PIPELINE_TRIGGER_TOKEN,
      // Check-ins to this environment's `<env>/pipeline-trigger` healthchecks.io check
      // (F-04; tools/pipeline-trigger/README.md): the start, then the exit code. A
      // secret ping URL, set by the operator and sealed on production, so a new PR env
      // does not copy it; unset = no check-ins. Never a value here. Not TRIGGER_*: the
      // ignore pattern below would swallow a declared key (a plan error).
      HEALTHCHECK_PING_URL: preserve(),
    },
  });

  // Tuning variables the operator may set by hand on a service
  // (tools/db-backup/README.md, tools/pipeline-trigger/README.md,
  // szczur-radar-api/README.md). IaC neither sets nor deletes them: the CLI
  // removes an ignored key from both sides before the diff, so a hand-set value
  // never shows up as `variable.delete`, which the CI gate refuses (S-14). A
  // pattern is `<service>/<KEY>`, `*` is its only wildcard (whole key,
  // case-sensitive). Ignore only keys this file does not declare: a key both
  // declared and ignored is a plan error, and an ignored literal (e.g.
  // BACKUP_KEEP_*) would vanish from both sides of the diff. A pattern that
  // matches no variable is only a plan warning. A new hand-set tuning variable
  // needs its pattern here (or a declaration above).
  //
  // The project `variables` policy needs Railway CLI >= 5.64.0
  // (railwayapp/cli#1256). railway@3.11.0's ProjectDefinition type has no
  // `variables` field, so the definition is typed wider below; project()
  // passes it through at runtime. `managed` is left out: it defaults to true
  // (this file owns every other variable). A field unknown to the SDK may
  // vanish silently (lessons.md): `railway config plan --json` must show
  // `variablePolicy` with these patterns.
  type VariablePolicy = { managed?: boolean; ignore?: string[] };
  const definition: Omit<ProjectDefinition, "name"> & { variables: VariablePolicy } = {
    variables: {
      ignore: [
        `${dbBackup.name}/BACKUP_IN_ANY_ENVIRONMENT`,
        `${dbBackup.name}/BACKUP_ACCEPT_SHRINK`,
        `${dbBackup.name}/BACKUP_TIMEOUT`,
        `${dbBackup.name}/S3_FORCE_PATH_STYLE`,
        `${pipelineTrigger.name}/TRIGGER_*`,
        `${api.name}/JAVA_TOOL_OPTIONS`,
      ],
    },
    resources: [db, api, web, backups, dbBackup, pipelineTrigger],
  };
  return project("szczur-radar", definition);
});
