import { bucket, defineRailway, github, postgres, preserve, project, ref, service } from "railway/iac";

// Infrastructure-as-Code for Szczur Radar. This is the source of truth for the
// api, web, and Postgres services, the db-backup cron service and its bucket,
// and replaces railway.toml for them.
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
// `checkSuites: true` on each source enables Railway "Wait for CI": deploys
// wait for the whole GitHub Actions workflow conclusion. Declared here so IaC
// does not reset the operator's toggle (a plan otherwise shows true → null).
//
// `sleepApplication: true` (serverless sleep) on api and web mirrors what the
// operator set in the dashboard; Postgres declares `false` (see below). The
// setting MUST stay declared on all three: without it an apply would change it. Keep this file in sync with production —
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
// Per-service resource cap (2 vCPU, 4 GB; Railway counts memory in SI bytes),
// a cost guard the operator set in the dashboard on all services after the
// move to Hobby (F-01, production-data-gate); db-backup gets it too. Declared
// so the plan matches the environment: without it a plan shows `limitOverride`
// 2 → null and 4000000000 → null (an apply of those nulls left the cap in place
// in szczur-radar-pr-44 on 2026-10-11, so the diff was noise, not a removal).
const RESOURCE_LIMITS = { containers: { cpu: 2, memoryBytes: 4_000_000_000 } };

export default defineRailway(() => {
  // Railway PR environments track the PR's branch, not `main`. CI sets
  // IAC_SOURCE_BRANCH on pull_request events only, so an apply to a PR env
  // does not rewrite `source.branch`; unset (push to main, local) → `main`.
  // The IaC context does not reliably carry the target environment name, so
  // the production safety net lives in CI: the railway-config job refuses ANY
  // source change, in every environment.
  const branch = process.env.IAC_SOURCE_BRANCH || "main";

  // Postgres. `postgres()` takes no deploy options, so serverless sleep is set
  // on the node directly (DatabaseNode carries `deploy`); Object.assign keeps
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
  // Caveats). The API and web still sleep. The setting applies to a container
  // when it is created: redeploy Postgres after the apply.
  const db = Object.assign(postgres("postgres", { region: REGION }), {
    deploy: { sleepApplication: false, limitOverride: RESOURCE_LIMITS },
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
    watchPatterns: ["szczur-radar-api/**"],
    // Flyway migrations run BEFORE each new API version starts (production and
    // PR envs), never at server start (spring.flyway.enabled=false there). If a
    // migration fails, `migrate` exits non-zero, Railway stops the deploy and
    // the previous version keeps serving — the desired behavior. Runs in a
    // container of the same image; the path is relative to WORKDIR /app.
    preDeploy: "java -jar target/szczur-radar-api.jar migrate",
    healthcheck: "/api/health",
    replicas: { [REGION]: 1 },
    deploy: { sleepApplication: true, limitOverride: RESOURCE_LIMITS },
    env: {
      PORT: API_INTERNAL_PORT,
      // Nieużywane od `api-docker-image` (JDK pochodzi z obrazu Dockera). Zostaje
      // zadeklarowane, bo usunięcie zmiennej to `variable.delete`, które bramka CI
      // odrzuca jako destrukcyjne.
      RAILPACK_JDK_VERSION: "25",
      // Split Postgres variables for the JDBC URL
      // (jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}) — no DATABASE_URL parsing.
      PGHOST: db.env.PGHOST,
      PGPORT: db.env.PGPORT,
      PGUSER: db.env.PGUSER,
      PGPASSWORD: db.env.PGPASSWORD,
      PGDATABASE: db.env.PGDATABASE,
      // Nieużywane od F-03 (Java czyta PG*). Zostaje zadeklarowane, bo usunięcie
      // zmiennej to `variable.delete`, które bramka CI odrzuca jako destrukcyjne.
      DATABASE_URL: db.env.DATABASE_URL,
      // Nieużywane od F-03 (sesje Spring Session JDBC zamiast JWT). Produkcja ma
      // zapieczętowaną wartość; bez preserve() plan na main proponowałby
      // `Delete variable JWT_SECRET`, które bramka CI odrzuca. Nigdy wartość tutaj.
      JWT_SECRET: preserve(),
      // LLM provider for Embabel (szczur-radar-api/README.md): set by the operator
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
    },
  });

  // Vue SPA served by Caddy (multi-stage Dockerfile auto-detected). The only
  // public service. Caddy reads the API's private host + port from these
  // reference variables to reverse-proxy /api/* (see szczur-radar-web/Caddyfile).
  const web = service("szczur-radar-web", {
    source: github(REPO, { branch, checkSuites: true }),
    rootDirectory: "szczur-radar-web",
    watchPatterns: ["szczur-radar-web/**"],
    healthcheck: "/healthz",
    replicas: { [REGION]: 1 },
    deploy: { sleepApplication: true, limitOverride: RESOURCE_LIMITS },
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
  // Railway. Every environment gets its own bucket with its own keys, so a PR env
  // backs up its own database and never sees production's dumps. The schedule
  // is UTC; S-05 (daily run) may move it. A cron run's status is its exit code,
  // and restartPolicyType NEVER keeps a failed run visible instead of retrying it.
  const backups = bucket("db-backups", { region: "ams" });
  const dbBackup = service("db-backup", {
    source: github(REPO, { branch, checkSuites: true }),
    rootDirectory: "tools/db-backup",
    watchPatterns: ["tools/db-backup/**"],
    replicas: { [REGION]: 1 },
    deploy: { cronSchedule: "0 2 * * *", restartPolicyType: "NEVER", limitOverride: RESOURCE_LIMITS },
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
    },
  });

  return project("szczur-radar", {
    resources: [db, api, web, backups, dbBackup],
  });
});
