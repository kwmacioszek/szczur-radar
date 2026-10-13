---
starter_id: spring
package_manager: maven
project_name: szczur-radar-api
hints:
  language_family: java
  team_size: solo
  deployment_target: railway
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: first-class
  path_taken: custom
  quality_override: false
  self_check_answers:
    typed: true
    from_official_starter: true
    conventions: true
    docs_current: true
    can_judge_agent: true
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: true
  has_background_jobs: true
---

## Update 2026-10-01 — Java / Spring Boot replaces FastAPI (F-03)

Decided in change `java-api-rewrite` (roadmap F-03); supersedes the FastAPI/LangGraph sections below, kept for history. The author knows Java and can reliably judge agent-generated code in it; in Python they could not (`can_judge_agent` was `false`). The behaviour visible to the SPA and Railway stays the S-01 contract (routes, `{id, email, role}`, `{"detail": …}` errors, the `sr_session` cookie, CLI bootstrap, migrations before deploy).

- **Runtime and build:** Spring Boot 4 on Java 25 (LTS), Maven with the wrapper (`./mvnw`); on Railway it is built from `szczur-radar-api/Dockerfile` (`eclipse-temurin:25-jdk-noble` build, `25-jre-noble` runtime, non-root user; since `api-docker-image`, 2026-10-06 — Railpack with `RAILPACK_JDK_VERSION=25` before). One jar runs the server or an operator command (`migrate`, `create-admin`, `create-user`, `reset-password`).
- **Auth:** Spring Security + Spring Session JDBC instead of the project-owned JWT layer: a server-side session in Postgres (survives API sleep and restarts) behind the HttpOnly `sr_session` cookie, 30 days of inactivity with the cookie renewed on successful API calls, argon2 via `Argon2PasswordEncoder`, deny-by-default routes, JSON 401/403. `token_version` is gone: revoking a user's sessions means deleting them from the session repository (`findByPrincipalName`, principal = user UUID). CSRF stays `SameSite=Strict` + an Origin filter (no `csrf.spa()`, no SPA change). Since S-07 (`cli-user-onboarding`, 2026-10-12): accounts get their password through one-time activation links printed by the CLI (`create-user`, `reset-password`). The token is 32 random bytes; the database keeps only its SHA-256 (`activation_tokens`, one expiring row per account, deleted on use). Two public routes check the link and set the password without creating a session. The CLI context has no session repository (`WebApplicationType.NONE`), so `reset-password` deletes the account's sessions with SQL (`spring_session.principal_name` = user UUID). Spring Security's One-Time Token Login is not used: it stores the token in clear and logs the user in instead of setting a password.
- **Login limiter:** the Python sliding window with reservations is ported as is (5 failures / 15 min per e-mail, 20 per IP, in-process, single replica).
- **Persistence:** PostgreSQL via Spring Data JPA (Hibernate 7) — chosen by the user over plain JDBC on 2026-10-02 (standard repositories and entities instead of hand-written SQL). The schema belongs to Flyway: Hibernate never generates or alters it (`ddl-auto=none`) and does not touch the database at boot (explicit dialect, no JDBC metadata access), so the server still starts without a database; `JpaSchemaIT` validates the entities against the migrated schema. Migrations are Flyway SQL files, **fix-forward** (Flyway Community has no undo; restoring a database backup is the last resort) — replaces the Alembic "every migration has a working downgrade" rule.
- **LLM orchestration:** Embabel (JVM agent framework on Spring AI 2, from Spring's creator) instead of LangGraph. One provider per run via `LLM_API_PROVIDER` (`gemini` | `deepseek` | `openrouter`), `LLM_MODEL`, `LLM_API_KEY`; F-03 ships only a skeleton agent without LLM calls; the assessment logic and model measurement belong to F-02.
- **Tests:** JUnit + AssertJ (`*Test`) and Spring + Testcontainers Postgres 18 (`*IT`); missing Docker fails the build instead of skipping DB tests.
- The production database is wiped for the switch (it held one admin, recreated via `create-admin`); there is no way back to the Python backend — failures are fixed forward in Java.

## Why this stack (superseded 2026-10-01 — FastAPI/LangGraph, kept for history)

Szczur Radar's backend (this module only — the Vue frontend is a separate tech-stack-selector run) is a FastAPI-based REST API wrapping a LangGraph-orchestrated data and LLM pipeline. FastAPI was chosen because its native Pydantic typing aligns directly with LangGraph's structured input/output, keeping the pipeline-to-API boundary strongly typed end-to-end. Persistence is PostgreSQL via SQLAlchemy 2 and Alembic for migrations, assembled on top of FastAPI rather than inherited from the starter. Authentication uses FastAPI Users for the parts that fit (user model, password hashing, JWT, route guards); public self-registration is disabled, since the product has no public sign-up and no automated email — bootstrap, recovery, and user creation are handled by project-specific CLI commands instead. LangGraph is kept independent of the HTTP layer, so the pipeline can be invoked from scheduled jobs, CLI commands, API endpoints, or tests without change. Deployment targets Railway; CI runs on GitHub Actions with auto-deploy-on-merge, matching the solo, short-feedback profile.

**Update 2026-09-27 (superseded by the 2026-10-01 update above) — authentication is a thin, project-owned layer, not FastAPI Users** (decided in change `admin-bootstrap-and-login`, roadmap S-01; supersedes the FastAPI Users sentence above). FastAPI Users is in maintenance mode, and the product needs things it does not fit cleanly: a `token_version` claim that revokes every session of a user on password change or recovery, a `user`/`admin` role, and account bootstrap/creation only through CLI commands with no registration flow. The layer lives in `app/auth/`: PyJWT (HS256 session token in an HttpOnly cookie), pwdlib with argon2 for password hashing, `current_user`/`require_admin` dependencies, and an in-process login rate limiter. Do not add FastAPI Users.

## Raw source payload storage boundary

Queryable, normalized post data and LLM-derived fields belong in PostgreSQL. The original source payload is a different concern: treat it as an immutable raw artifact behind a dedicated storage interface and keep only a stable reference from the application model. Do not embed the full raw JSON in the primary post row merely because PostgreSQL can store JSONB.

The raw-payload interface must preserve the exact source payload, support transparent lossless compression, and allow the physical backend to change without changing the domain model or API contract. The initial backend is a downstream implementation decision; Railway Buckets are the preferred infrastructure candidate if RAW growth would otherwise consume the PostgreSQL volume. A database-backed implementation, if used temporarily, must still keep RAW physically/logically separate from the primary query path so it can be migrated later without rewriting post records.

