import { defineRailway, github, postgres, project, service } from "railway/iac";

// Infrastructure-as-Code for Szczur Radar. This is the source of truth for the
// api, web, and Postgres services and replaces railway.toml for them.
// Validate with `railway config plan` (dry run, deletes nothing) before
// `railway config apply` — see context/deployment/deploy-plan.md, phase 3.
//
// NOTE: this SDK is young; field names are written to railway@3.11.0's
// `railway/iac` DSL. If `railway config plan` rejects a field, reconcile it
// against the installed SDK version — the plan gate exists to catch exactly
// that before anything is applied.

const REPO = "kwmacioszek/szczur-radar";
const REGION = "europe-west4"; // Railway "EU West (Amsterdam)"

export default defineRailway(() => {
  // Postgres. Backups/PITR are enabled by the operator in the dashboard
  // (risk register item #1) — that is not expressible here.
  const db = postgres("postgres");

  // FastAPI backend. No public domain: reachable only through the web proxy
  // over the private network, so uvicorn binds :: (IPv6). Railpack auto-detects
  // uv + .python-version (3.13); only the start command and healthcheck are
  // explicit (the default `uvicorn main:app` would not bind PORT/host).
  const api = service("szczur-radar-api", {
    source: github(REPO, { branch: "main" }),
    rootDirectory: "szczur-radar-api",
    watchPatterns: ["szczur-radar-api/**"],
    start: "uv run uvicorn main:app --host :: --port $PORT",
    healthcheck: "/api/health",
    replicas: { [REGION]: 1 },
    env: {
      DATABASE_URL: db.env.DATABASE_URL,
    },
  });

  // Vue SPA served by Caddy (multi-stage Dockerfile auto-detected). The only
  // public service. Caddy reads the API's private host + port from these
  // reference variables to reverse-proxy /api/* (see szczur-radar-web/Caddyfile).
  const web = service("szczur-radar-web", {
    source: github(REPO, { branch: "main" }),
    rootDirectory: "szczur-radar-web",
    watchPatterns: ["szczur-radar-web/**"],
    healthcheck: "/healthz",
    replicas: { [REGION]: 1 },
    env: {
      API_PRIVATE_DOMAIN: api.env.RAILWAY_PRIVATE_DOMAIN,
      API_PORT: api.env.PORT,
    },
  });

  return project("szczur-radar", {
    resources: [db, api, web],
  });
});
