# Repository Guidelines

Szczur Radar collects investment posts (Wykop, later Reddit), classifies them with an LLM, and serves them to invited users. One repo contains two independently deployed application modules — a Spring Boot (Java) backend and a Vue 3 + TypeScript SPA — plus a separate scheduled pipeline job.

## Hard Rules

- `context/` is the planning trail: never overwrite or reformat it; never write to `context/archive/` (archived changes are immutable — see the 10x-cli block in @CLAUDE.md / @AGENTS.md). `context/foundation/` docs are edited in place (@context/foundation/README.md); `lessons.md` is append-only.
- If code contradicts @context/foundation/prd.md, flag it — don't silently resolve it in code.
- `szczur-radar-web/` talks to the backend only through its REST API: no direct DB access and no LLM calls from the browser (FR-032).
- No public registration and no automated email in MVP. Accounts are created, bootstrapped, and recovered via backend CLI commands (PRD § Access Control, FR-026–028).
- A new data source changes only acquisition and mapping to the shared model; evaluation, persistence, and API layers stay untouched (PRD § Non-Functional Requirements). Registering the source — its `sources/Source` constant plus one migration adding it to the `source` `CHECK` sets — is part of adding it; posts store the shared model's item type (`post`/`link`/`comment`), never a source's own type names.
- Check an FR's priority in the PRD before building it: `must-have` first, `nice-to-have` only when asked.

## Project Structure & Module Organization

- `szczur-radar-api/` — Spring Boot 4, Java 25, Maven (wrapper `./mvnw`). Deployed as the container image from `szczur-radar-api/Dockerfile` (multi-stage: JDK 25 build → JRE 25 runtime as a non-root user) — the same image locally, in CI and on Railway. Base package `pl.szczurradar.api`, packages by feature: `health`, `user` (JPA entity + Spring Data repository), `auth` (Spring Security + Spring Session JDBC; session in the `sr_session` HttpOnly cookie, argon2, login limiter, Origin filter), `cli` (operator commands `migrate`, `create-admin`), `config`, `agents` (Embabel; `agents/assessor` — LLM assessment contract V2 as code: model, output schema, parser, D1–D15 validator, no-content prefilter; see its `CLAUDE.md`), `llm` (provider clients: the API at the root, one subpackage per provider, `transport/` with the only retries; see its `CLAUDE.md`), `sources` (source → `AssessmentInputV1` mappers, e.g. `sources/wykop`), `text` (`Whitespace`: the contract §10 white space set, shared by `agents`, `llm` and `sources`), `pipeline` (pipeline runs: entry points at the root, `run/` model, `flow/` Embabel agent, `step/` steps, `assess/` assessment, `state/` conditional state writes; see its `CLAUDE.md`), `posts` (posts and every assessment attempt), `raw` (`RawPayloadStore`: raw source payloads apart from application data), `persistence` (`EnumValueConverter`, `Sha256`; every enum converter in `persistence/converters/`). Measurement code (live smoke, gold sample, recordings, metrics) lives only in `src/test/java/.../measurement/` and never ships in the jar. Persistence: Spring Data JPA (Hibernate) over a Flyway-owned schema — Hibernate never creates or alters tables (`ddl-auto=none`), and `JpaSchemaIT` validates every entity against the migrated schema; add a Flyway migration for every entity change. Migrations: Flyway SQL in `src/main/resources/db/migration/`. Closed value sets (roles, error codes, command names) are enums, not free strings. Stack: @context/foundation/tech-stack-api.md. Local DB, migrations, LLM settings and tests: @szczur-radar-api/README.md.
- `szczur-radar-web/` — Vite + Vue 3 + TypeScript SPA: `vue-router` with a session guard (`src/router/`), the single API client (`src/api/client.ts`), session state (`src/auth/session.ts`), views in `src/views/`; no store library. Stack: @context/foundation/tech-stack-web.md.
- `context/foundation/` — `prd.md` (requirements, FR priorities), `shape-notes.md`, `tech-stack-*.md`, `lessons.md`.
- `context/changes/bootstrap-verification/` — scaffold and audit logs per module.
- Whenever context/foundation/roadmap.md changes, update `tools/render_roadmap.swift` and regenerate `docs/visuals/roadmap-mvp.png`

## Build, Test, and Development Commands

- Backend (JDK 25): `cd szczur-radar-api && ./mvnw -q -DskipTests package`, then `java -jar target/szczur-radar-api.jar` (server on :8000) or `./mvnw spring-boot:run`. Add dependencies in `pom.xml` (versions from the Spring Boot / Embabel BOMs where managed). Every Spring context needs the `PG*` variables (`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`) and the LLM settings `LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_API_KEY` (one provider; the key is a secret, never in the repo) — it refuses to start without them.
- API image (what Railway runs): `docker build -t szczur-radar-api ./szczur-radar-api`, then `docker run … szczur-radar-api migrate` and `docker run -p 8000:8000 -e PORT=8000 … szczur-radar-api` with the `PG*` and `LLM_*` variables (`-e NAME`). No start command: the image `ENTRYPOINT` runs the jar; extra JVM flags via `JAVA_TOOL_OPTIONS`.
- Migrations: `java -jar target/szczur-radar-api.jar migrate` (Railway runs it as the API pre-deploy, in a container of the same image). Flyway is fix-forward: never edit a deployed `V<n>__*.sql`, add a new migration; restoring a database backup is the last resort — there is none until the Railway subscription (@context/deployment/deploy-plan.md).
- First admin: `java -jar target/szczur-radar-api.jar create-admin --email <email>` (hidden password prompt; refuses if an admin exists).
- Backend tests: `./mvnw -B verify` — unit tests `*Test` (no Spring context, no Docker) and integration tests `*IT` (Spring + Testcontainers Postgres 18); without Docker `verify` fails instead of skipping.
- Frontend: `cd szczur-radar-web && npm run dev` (proxies `/api` to the API on :8000), `npm test` (Vitest), `npm run build` (vue-tsc typecheck + Vite); scripts in @szczur-radar-web/package.json.

## Coding Style & Testing

- Test runners: JUnit + AssertJ + Mockito (`*Test`) and `@SpringBootTest` + `MockMvcTester` + Testcontainers (`*IT`) in `szczur-radar-api/src/test/java/`; Vitest (+ `@vue/test-utils`, jsdom) as `src/**/*.test.ts` with explicit imports from `'vitest'` (no globals). No linter or formatter runs in CI — don't assume Checkstyle, Spotless or ESLint exist. Code style lives in `.editorconfig` (read natively by IntelliJ: tabs in Java, import groups static / `java.*` / others / `pl.szczurradar.*`).
- If an IntelliJ MCP server is available locally, use it for the inner loop (inspections as the only lint, build, unit tests, refactoring); the gates stay `./mvnw -B test` / `./mvnw -B verify`. Its config (`.mcp.json`) is machine-specific and git-ignored.
- Every API route requires a session by default (deny-by-default in `config/SecurityConfig`). A public route must be permitted there AND listed in `PUBLIC_ROUTES` (with a justification) in `AuthorizationCompletenessIT`, which calls every mapped route without a session and fails on anything but 401. Admin-only routes live under `/api/admin/` and are listed in `ADMIN_ROUTES`.
- External-service responses used in tests (Wykop, LLM providers) are real payloads stored as JSON fixtures in `szczur-radar-api/src/test/resources/fixtures/<service>/`.
- Code that runs work in parallel passes its own executor: on Java 25 the container's CPU limit may make `availableProcessors()` return 1.
- Frontend TypeScript settings: @szczur-radar-web/tsconfig.app.json.

## UI (szczur-radar-web)

- Tokens: colour roles and the type, space and radius scales live in `:root` of `szczur-radar-web/src/style.css`; the dark theme redefines only the colour roles there (`prefers-color-scheme`). Text on `--accent` is always `--on-accent`. A new text colour role gets a contrast pair in `src/styleContract.test.ts` (≥4.5:1 in both themes).
- Shared components live in `src/components/`: `AppButton` (primary / secondary, `busy`), `StatusMessage` (ok → `role="status"`, error → `role="alert"`), `StatusBadge` (the state as text, the tone as colour). Check there before writing a button, message or badge in a view; a new shared component goes there and onto the kitchen sink.
- No colour literals (hex, `rgb()`, `hsl()`, `oklch()`), palette classes or arbitrary values in views and components; spacing and font sizes come from the scale (`--space-*`, `--font-size-*`). The `no literals` scan in `src/styleContract.test.ts` enforces this for the files on its list — add a file once it is cleaned.
- Visual check: `/kitchen-sink` (dev only, `npm run dev`, not in the production build) shows every role and component in every state; real views are screenshotted with `szczur-radar-web/dev/mock-api.js` + `playwright-cli` (procedure in its header), in both themes and at 375 px. Each of the 7 states (default, hover, focus-visible, disabled, error, empty, loading) gets a screenshot or an N/A with a reason.
- UI work on an existing view goes through `/10x-ui`.

## Deployment

- Target platform: Railway. The platform decision is finalized in @context/foundation/infrastructure.md.
- `szczur-radar-web` is the only publicly exposed Railway service and serves the Vue SPA.
- Browser requests to `/api/*` on the web application's public origin are reverse-proxied to the private `szczur-radar-api` service over Railway private networking.
- `szczur-radar-api` must not have its own public domain.
- CI runs via `.github/workflows/ci.yml` on every push to `main` and PR: backend `./mvnw -B verify` (Testcontainers; LLM settings from GitHub Actions variables + secret), web unit tests (Vitest), a web container smoke test whose image build (the real Dockerfile) also covers frontend typecheck/build, an API container smoke test (`api-container-smoke`: the real `szczur-radar-api/Dockerfile`, non-root user, the Railway pre-deploy `migrate` against Postgres 18, then `/api/health` and `/api/ready`), and `railway-config` (plans + applies `.railway/railway.ts` to the PR env or production; see @context/deployment/deploy-plan.md). Do not path-filter this workflow — Railway's Wait for CI evaluates the whole workflow conclusion.
- Production deployment is via Railway native GitHub autodeploy from `main`, gated by Wait for CI. Service topology and variables are declared as code in `.railway/railway.ts` (validate with `railway config plan` before `railway config apply`).
- The Java/Embabel pipeline runs inside the API process, in the background (since S-02): an admin starts a run with `POST /api/admin/pipeline-runs`, runs are queued and executed one at a time, their state is durable in Postgres, and on start the API marks unfinished runs interrupted and puts their posts back. A deploy or sleep of the API interrupts a running run — never merge to `main` (or push to a PR branch under test) while a run is `RUNNING`. The daily trigger is S-05's decision. Real Wykop and LLM calls are allowed in PR environments.
- Wykop keys `WYKOP_KEY` / `WYKOP_SECRET` are optional (without them Wykop is a not-configured source). Production's are sealed and set by the operator; PR envs get the same application keys from the GitHub secrets via the `railway-config` job ("Ensure PR-env Wykop keys"); `.railway/railway.ts` only `preserve()`s the names. `PIPELINE_LLM_CONCURRENCY` (default 4) limits parallel LLM calls of a run.
- Before implementing authentication, document the selected transport, session or token storage, and CSRF handling in the change plan. Browser authentication requests follow the same-origin `/api/*` route above.
- Production's `LLM_API_KEY` is sealed and set by the operator (with `LLM_API_PROVIDER`, `LLM_MODEL`) before the API deploys there; it is never copied to CI or PR envs. PR envs get the CI settings (GitHub variables + the `LLM_API_KEY` secret) from the `railway-config` job ("Ensure PR-env LLM settings"), which overwrites any copied value and removes the optional `LLM_SERVICE_TIER` / `LLM_REASONING_EFFORT` / `LLM_TIMEOUT` copied from production (they fit production's provider, not the CI one); `.railway/railway.ts` only `preserve()`s the names.

## Workflow: branches, PRs and GitHub issues

- Only a human merges to `main` (= production deploy). Agents prepare the PR, get CI and PR-env verification green, then stop and hand the merge over — never `gh pr merge` or push to `main` (@context/foundation/lessons.md).
- Every change (`context/changes/<change-id>/`) has one branch, one PR and one GitHub issue, all created at the start of `/10x-implement` phase 1, before the first code commit:
  - branch `feature/<change-id>` from a freshly fetched `origin/main` (`git fetch` first) — never from a local `main`, which may be stale, nor from another change's branch;
  - issue: a roadmap slice uses its existing `S-NN: …` issue; a change outside the roadmap gets a new issue titled `<change-id>: …`;
  - the change folder (`research.md`, `plan.md`, `plan-brief.md`, `change.md`) goes into the first commit on that branch.
- Roadmap slices are tracked as GitHub issues (`S-NN: …`). Keep each issue (slice or not) in sync with reality:
  - when work starts: comment on the issue with the change id, branch and PR link; every PR body carries `Refs #<issue>`;
  - while in progress: comment when a milestone lands (e.g. a phase verified in the PR env) or when something blocks it;
  - after the human merges and production verification passes: close the issue with a short summary (merge commit, what was verified).
- Never mark an issue done because code merged — only after production verification.
