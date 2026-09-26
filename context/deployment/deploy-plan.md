# Railway integration and first deployment

## Summary

Deploy the scaffold first: a neutral Vue page, a minimal FastAPI app, and PostgreSQL in one Railway project in Amsterdam. Only the web service gets a public domain. It proxies /api/\* to the API over Railway’s private network, so the browser uses one origin. No collection job or protected data endpoint goes live in this release.

During implementation, update the existing context/deployment/deploy-plan.md in place. Use its checkboxes to record completion, deployment IDs, test results, and the web URL—never secret values.

## Phases

### [ ] 1. Prepare the application

Replace the backend stub with FastAPI. Add GET /api/health (process is running) and GET /api/ready (PostgreSQL query succeeds; return 503 on failure). Add SQLAlchemy 2 and the PostgreSQL driver through uv; keep data and admin routes absent until authentication is implemented.

Replace the Vue starter screen with a neutral “in preparation” page. Build it with npm ci && npm run build in a multi-stage Dockerfile, then serve it with Caddy. Configure SPA fallback, a web health endpoint, and a /api/\* proxy that preserves the path. Railway documents this Caddy proxy pattern (https://docs.railway.com/guides/spa-routing-configuration).

Keep DATABASE_URL in the API service. Pass the API’s private hostname and port to the web service through Railway reference variables; Caddy reads those variables rather than hardcoded upstream coordinates. Do not put database or future LLM credentials in the frontend build.

### [ ] 2. Define infrastructure and CI

Add one .railway/railway.ts for szczur-radar-api, szczur-radar-web, and Postgres, with pinned TypeScript SDK dependencies. Set each service’s GitHub source and root directory; use Railpack for the API and the detected web Dockerfile. Make the API bind to Railway’s PORT, set its healthcheck, and reference Postgres. Reference the API service’s private domain and PORT from the web service. Railway’s Infrastructure as Code (https://docs.railway.com/infrastructure-as-code) replaces railway.toml for new services.

Add a GitHub Actions workflow that runs on every push to main and on PRs: locked backend dependency sync and health/readiness tests, frontend typecheck/build, and a web container smoke test. Avoid path filtering the required push workflow because Railway’s Wait for CI (https://docs.railway.com/deployments/github-autodeploys) evaluates whole workflow conclusions.

Use Railway native GitHub autodeploy for each service, gated by Wait for CI. GitHub Actions performs checks and does not issue a second deploy. Configure service watch paths for their respective module directories.

Make targeted corrections to the infrastructure and stack guidance for the selected private API route and current IaC flow. Correct the stale deployment line in AGENTS.md while preserving the user’s other working changes.

### [ ] 3. Provision and protect production

Operator: connect the Railway account to kwmacioszek/szczur-radar, choose the plan, and configure a $15 compute usage email alert and a $25 monthly hard compute usage limit before provisioning. The hard limit can take workloads offline when reached; record how to raise it during an incident. Install/login to the CLI, create or link the production project, run railway config plan, review that it adds exactly the intended services and deletes nothing, then run railway config apply. Verify all services and Postgres are in Amsterdam. Railway documents the usage-limit behavior (https://docs.railway.com/pricing/cost-control).

Generate a Railway domain only for web; leave API and Postgres without public domains or TCP proxies. Set Postgres DATABASE_URL by service reference. Confirm the web proxy reaches the API using its referenced private hostname and port.

Before collecting production data, enable scheduled Postgres volume backups and PITR, wait for the first restorable point, and perform a restore drill into a separate database service. Record the restored test result and recovery steps. Railway notes that PITR begins only after its first base backup (https://docs.railway.com/guides/postgres-backups-restores).

### [ ] 4. Release and verify

Merge the checked scaffold to main, confirm GitHub Actions succeeds, then enable autodeploy and Wait for CI for both services. Check the deployed commit and deployment status for each service.

Verify /, /api/health, and /api/ready through the web domain; verify API and Postgres have no public URL. Test that readiness returns 503 with the database unavailable in a local test, and that the proxy does not turn API errors into the SPA page.

Record the web URL, Railway project/environment and deployment IDs, configured variable names, backup/restore result, and smoke-check results in the deployment plan. Railway healthchecks run at deployment, not as continuous monitoring (https://docs.railway.com/deployments/healthchecks), so include a routine operational check of logs and readiness.

### [ ] 5. Enable safe PR previews and document recovery

Before enabling PR previews, seal all production-only secrets. Verify that preview environments cannot inherit those values; add separate preview credentials only when needed. Railway does not copy sealed values into PR environments (https://docs.railway.com/variables).

Enable full isolated PR environments after production works. Verify a preview has its own Postgres and that its /api/\* proxy cannot reach production. Keep bot PR previews off. This replaces the Focused setting in infrastructure.md: Railway’s guide says a Focused web preview can reuse the production backend (https://docs.railway.com/guides/preview-deployments-with-pr-environments).

Add support steps to the plan: for a build failure check Python version or Dockerfile detection; for a proxy 502 check the referenced hostname, port, and API bind address; for readiness 503 check the database reference and API logs; for a CI wait inspect GitHub permissions and workflow conclusions. For a bad release, use Railway’s deployment rollback, then assess database state separately. If backups or PITR cannot be enabled and restore-tested, stop before data collection and reopen the documented Render fallback decision.

### [ ] 6. Gate later external integrations

When the MVP pipeline exists, add it as a separate Railway cron service from the backend codebase. Give it only the database and required Wykop/LLM credentials, make it exit after each run, and check its schema revision before writing. Railway uses UTC and skips a run while the previous run remains active (https://docs.railway.com/cron-jobs).

Enable real outbound Wykop and LLM calls only in production after credentials, spend limits, failure visibility, and a dry run are checked. Keep them out of PR previews. CLI account bootstrap and recovery remain backend operations; the MVP adds no public registration or automated email.

## Acceptance and assumptions

The first release passes when the web page and both API probes work through one HTTPS domain, the API and database remain private, CI gates deployments, the usage alert and hard limit are active, and a Postgres restore drill succeeds. The daily pipeline, accounts, Wykop, and LLM integration are later phases.

This plan follows the chosen Railway platform and the confirmed scaffold-first, private-proxy, native-autodeploy, full-preview decisions. No repository or Railway changes are made while this plan is under review.
