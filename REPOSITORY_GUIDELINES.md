# Repository Guidelines

Szczur Radar collects investment posts (Wykop, later Reddit), classifies them with an LLM, and serves them to invited users. One repo contains two independently deployed application modules — a Spring Boot (Java) backend and a Vue 3 + TypeScript SPA — plus a separate scheduled pipeline job.

## Hard Rules

- `context/` is the planning trail: never overwrite or reformat it; never write to `context/archive/` (archived changes are immutable — see the 10x-cli block in @CLAUDE.md / @AGENTS.md). `context/foundation/` docs are edited in place (@context/foundation/README.md); `lessons.md` is append-only.
- If code contradicts @context/foundation/prd.md, flag it — don't silently resolve it in code.
- `szczur-radar-web/` talks to the backend only through its REST API: no direct DB access and no LLM calls from the browser (FR-032).
- No public registration and no automated email in MVP. Accounts are created, bootstrapped, and recovered via backend CLI commands (PRD § Access Control, FR-026–028).
- A new data source changes only acquisition and mapping to the shared model; evaluation, persistence, and API layers stay untouched (PRD § Non-Functional Requirements).
- Check an FR's priority in the PRD before building it: `must-have` first, `nice-to-have` only when asked.

## Project Structure & Module Organization

- `szczur-radar-api/` — Spring Boot 4, Java 25, Maven (wrapper `./mvnw`). Base package `pl.szczurradar.api`, packages by feature: `health`, `user` (JdbcClient repository), `auth` (Spring Security + Spring Session JDBC; session in the `sr_session` HttpOnly cookie, argon2, login limiter, Origin filter), `cli` (operator commands `migrate`, `create-admin`), `config`, `agents` (Embabel). Migrations: Flyway SQL in `src/main/resources/db/migration/`. Stack: @context/foundation/tech-stack-api.md. Local DB, migrations, LLM settings and tests: @szczur-radar-api/README.md.
- `szczur-radar-web/` — Vite + Vue 3 + TypeScript SPA: `vue-router` with a session guard (`src/router/`), the single API client (`src/api/client.ts`), session state (`src/auth/session.ts`), views in `src/views/`; no store library. Stack: @context/foundation/tech-stack-web.md.
- `context/foundation/` — `prd.md` (requirements, FR priorities), `shape-notes.md`, `tech-stack-*.md`, `lessons.md`.
- `context/changes/bootstrap-verification/` — scaffold and audit logs per module.
- Whenever context/foundation/roadmap.md changes, update `tools/render_roadmap.swift` and regenerate `docs/visuals/roadmap-mvp.png`

## Build, Test, and Development Commands

- Backend (JDK 25): `cd szczur-radar-api && ./mvnw -q -DskipTests package`, then `java -jar target/szczur-radar-api.jar` (server on :8000) or `./mvnw spring-boot:run`. Add dependencies in `pom.xml` (versions from the Spring Boot / Embabel BOMs where managed). Every Spring context needs the `PG*` variables (`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`) and the LLM settings `LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_API_KEY` (one provider; the key is a secret, never in the repo) — it refuses to start without them.
- Migrations: `java -jar target/szczur-radar-api.jar migrate` (Railway runs it as the API pre-deploy). Flyway is fix-forward: never edit a deployed `V<n>__*.sql`, add a new migration; PITR is the last resort.
- First admin: `java -jar target/szczur-radar-api.jar create-admin --email <email>` (hidden password prompt; refuses if an admin exists).
- Backend tests: `./mvnw -B verify` — unit tests `*Test` (no Spring context, no Docker) and integration tests `*IT` (Spring + Testcontainers Postgres 18); without Docker `verify` fails instead of skipping.
- Frontend: `cd szczur-radar-web && npm run dev` (proxies `/api` to the API on :8000), `npm test` (Vitest), `npm run build` (vue-tsc typecheck + Vite); scripts in @szczur-radar-web/package.json.

## Coding Style & Testing

- Test runners: JUnit + AssertJ + Mockito (`*Test`) and `@SpringBootTest` + `MockMvcTester` + Testcontainers (`*IT`) in `szczur-radar-api/src/test/java/`; Vitest (+ `@vue/test-utils`, jsdom) as `src/**/*.test.ts` with explicit imports from `'vitest'` (no globals). No linter or formatter is configured — don't assume Checkstyle, Spotless or ESLint exist.
- Every API route requires a session by default (deny-by-default in `config/SecurityConfig`). A public route must be permitted there AND listed in `PUBLIC_ROUTES` (with a justification) in `AuthorizationCompletenessIT`, which calls every mapped route without a session and fails on anything but 401. Admin-only routes live under `/api/admin/` and are listed in `ADMIN_ROUTES`.
- External-service responses used in tests (Wykop, LLM providers) are real payloads stored as JSON fixtures in `szczur-radar-api/src/test/resources/fixtures/<service>/`.
- Code that runs work in parallel passes its own executor: on Java 25 the container's CPU limit may make `availableProcessors()` return 1.
- Frontend TypeScript settings: @szczur-radar-web/tsconfig.app.json.

## Deployment

- Target platform: Railway. The platform decision is finalized in @context/foundation/infrastructure.md.
- `szczur-radar-web` is the only publicly exposed Railway service and serves the Vue SPA.
- Browser requests to `/api/*` on the web application's public origin are reverse-proxied to the private `szczur-radar-api` service over Railway private networking.
- `szczur-radar-api` must not have its own public domain.
- CI runs via `.github/workflows/ci.yml` on every push to `main` and PR: backend `./mvnw -B verify` (Testcontainers; LLM settings from GitHub Actions variables + secret), web unit tests (Vitest), a web container smoke test whose image build (the real Dockerfile) also covers frontend typecheck/build, and `railway-config` (plans + applies `.railway/railway.ts` to the PR env or production; see @context/deployment/deploy-plan.md). Do not path-filter this workflow — Railway's Wait for CI evaluates the whole workflow conclusion.
- Production deployment is via Railway native GitHub autodeploy from `main`, gated by Wait for CI. Service topology and variables are declared as code in `.railway/railway.ts` (validate with `railway config plan` before `railway config apply`).
- The scheduled LangGraph pipeline runs as a separate private Railway job/service and must not depend on the lifecycle of either the web or API service.
- Before implementing authentication, document the selected transport, session or token storage, and CSRF handling in the change plan. Browser authentication requests follow the same-origin `/api/*` route above.
- Each environment has its own `LLM_API_KEY` (production's is never copied to CI or PR envs); the operator sets `LLM_API_PROVIDER`, `LLM_MODEL` and `LLM_API_KEY` on `szczur-radar-api` before the API deploys there (`.railway/railway.ts` only `preserve()`s them).

## Workflow: branches, PRs and GitHub issues

- Only a human merges to `main` (= production deploy). Agents prepare the PR, get CI and PR-env verification green, then stop and hand the merge over — never `gh pr merge` or push to `main` (@context/foundation/lessons.md).
- Roadmap slices are tracked as GitHub issues (`S-NN: …`). Keep each issue in sync with reality:
  - when work on a slice starts: comment on its issue with the change id, branch and PR link; every PR body carries `Refs #<issue>`;
  - while in progress: comment when a milestone lands (e.g. a phase verified in the PR env) or when something blocks it;
  - after the human merges and production verification passes: close the issue with a short summary (merge commit, what was verified).
- Never mark an issue done because code merged — only after production verification.
