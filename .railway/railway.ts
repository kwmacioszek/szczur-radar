import { bucket, defineRailway, github, postgres, preserve, project, service } from "railway/iac";

// Infrastructure-as-Code for Szczur Radar. This is the source of truth for the
// api, web, and Postgres services and replaces railway.toml for them.
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
// `sleepApplication: true` (serverless sleep) on all three services and the
// `Postgres-PITR` bucket mirror what the operator set in the dashboard. They
// MUST stay declared: without them an apply would switch sleep off (cost) and
// DELETE the bucket holding the PITR backups. Keep this file in sync with
// production — `railway config plan --detailed-exit-code` on `production`
// must exit 0 before any automatic apply (lessons.md, Railway IaC in CI).

const REPO = "kwmacioszek/szczur-radar";
const REGION = "europe-west4"; // Railway "EU West (Amsterdam)"
// Fixed private port for the API. Railway does not expose a service's PORT as a
// referenceable variable (a `${{svc.PORT}}` reference resolves to empty), so we
// pin it: the API binds it and the web Caddy proxy targets it deterministically
// over the private network.
const API_INTERNAL_PORT = "8000";

export default defineRailway(() => {
  // Railway PR environments track the PR's branch, not `main`. CI sets
  // IAC_SOURCE_BRANCH on pull_request events only, so an apply to a PR env
  // does not rewrite `source.branch`; unset (push to main, local) → `main`.
  // The IaC context does not reliably carry the target environment name, so
  // the production safety net lives in CI: the railway-config job refuses ANY
  // source change, in every environment.
  const branch = process.env.IAC_SOURCE_BRANCH || "main";

  // Postgres. PITR was enabled by the operator (`railway postgres pitr
  // enable`, risk register item #1); the enable itself is not expressible
  // here, but its storage bucket is (below). `postgres()` takes no deploy
  // options, so serverless sleep is set on the node directly (DatabaseNode
  // carries `deploy`); Object.assign keeps the non-enumerable `env` refs.
  const db = Object.assign(postgres("postgres"), {
    deploy: { sleepApplication: true },
  });

  // Bucket created by `railway postgres pitr enable` (base backups + WAL
  // archive). Declared so IaC never deletes it; region as provisioned.
  const pitrBucket = bucket("Postgres-PITR", { region: "sjc" });

  // Spring Boot backend (Java 25, Maven). No public domain: reachable only
  // through the web proxy over the private network. The JVM binds the wildcard
  // address DUAL-STACK by default — IPv6 for private networking AND IPv4 for
  // Railway's healthcheck — so server.address is deliberately not set; the port
  // comes from PORT (application.properties: server.port=${PORT:8000}).
  // Railpack detects Java from pom.xml and builds with ./mvnw (tests skipped;
  // CI runs them); RAILPACK_JDK_VERSION selects JDK 25 (Railpack default: 21).
  const api = service("szczur-radar-api", {
    source: github(REPO, { branch, checkSuites: true }),
    rootDirectory: "szczur-radar-api",
    watchPatterns: ["szczur-radar-api/**"],
    // Fixed artifact name (pom.xml <finalName>). No arguments = HTTP server.
    start: "java $JAVA_OPTS -jar target/szczur-radar-api.jar",
    // Flyway migrations run BEFORE each new API version starts (production and
    // PR envs), never at server start (spring.flyway.enabled=false there). If a
    // migration fails, `migrate` exits non-zero, Railway stops the deploy and
    // the previous version keeps serving — the desired behavior.
    preDeploy: "java -jar target/szczur-radar-api.jar migrate",
    healthcheck: "/api/health",
    replicas: { [REGION]: 1 },
    deploy: { sleepApplication: true },
    env: {
      PORT: API_INTERNAL_PORT,
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
    deploy: { sleepApplication: true },
    env: {
      API_PRIVATE_DOMAIN: api.env.RAILWAY_PRIVATE_DOMAIN,
      API_PORT: API_INTERNAL_PORT,
    },
  });

  return project("szczur-radar", {
    resources: [db, api, web, pitrBucket],
  });
});
