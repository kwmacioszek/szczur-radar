---
bootstrapped_at: 2026-09-26T12:50:30Z
starter_id: fastapi
starter_name: FastAPI
project_name: szczur-radar-api
language_family: python
package_manager: uv
cwd_strategy: native-cwd
bootstrapper_confidence: first-class
phase_3_status: ok
audit_command: pip-audit
---

> Backend module of a two-module repo (`szczur-radar-api/` + `szczur-radar-web/`). Scaffolded from inside
> `api/` with the hand-off at `context/foundation/tech-stack-api.md`; this log is written to the root
> `context/` instead of a nested `api/context/`. The frontend module gets its own `verification-web.md`.
>
> **Post-run change (2026-09-26):** the folder was renamed `api/` → `szczur-radar-api/` so it matches
> `project_name`. `[project].name` in `pyproject.toml` was changed from `api` to `szczur-radar-api`, `.venv/`
> was deleted and recreated with `uv sync` (a moved venv keeps stale absolute paths), and the root
> `.gitignore` entries were updated to `szczur-radar-api/.venv/` and `szczur-radar-web/node_modules/`.
> The scaffold log below records the original run under the old `api/` path.

## Hand-off

```yaml
starter_id: fastapi
package_manager: uv
project_name: szczur-radar-api
hints:
  language_family: python
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
    can_judge_agent: false
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: true
  has_background_jobs: true
```

### Why this stack

Szczur Radar's backend (this module only — the Vue frontend is a separate tech-stack-selector run) is a FastAPI-based REST API wrapping a LangGraph-orchestrated data and LLM pipeline. FastAPI was chosen because its native Pydantic typing aligns directly with LangGraph's structured input/output, keeping the pipeline-to-API boundary strongly typed end-to-end. Persistence is PostgreSQL via SQLAlchemy 2 and Alembic for migrations, assembled on top of FastAPI rather than inherited from the starter. Authentication uses FastAPI Users for the parts that fit (user model, password hashing, JWT, route guards); public self-registration is disabled, since the product has no public sign-up and no automated email — bootstrap, recovery, and user creation are handled by project-specific CLI commands instead. LangGraph is kept independent of the HTTP layer, so the pipeline can be invoked from scheduled jobs, CLI commands, API endpoints, or tests without change. Deployment targets Railway; CI runs on GitHub Actions with auto-deploy-on-merge, matching the solo, short-feedback profile.

## Pre-scaffold verification

| Signal      | Value   | Severity | Notes                                                                 |
| ----------- | ------- | -------- | --------------------------------------------------------------------- |
| npm package | not run | —        | non-JS starter (python)                                               |
| GitHub repo | not run | —        | card `docs_url` is https://fastapi.tiangolo.com (not a GitHub URL); no recency signal available |

## Scaffold log

**Resolved invocation**: `uv init . && uv add fastapi uvicorn` (run inside `api/`)
**Strategy**: native-cwd
**Exit code**: 0
**Pre-flight files-to-touch**: pyproject.toml, main.py, README.md, .python-version, uv.lock, .venv/
**Files written by CLI**: 5 files + `.venv/` (pyproject.toml, main.py, README.md, .python-version, uv.lock)
**Pre-existing files preserved**: none (`api/` was created empty for this run)

Notes:
- `uv init` named the project `api` (from the directory name), not `szczur-radar-api`. `project_name` from the hand-off is metadata only.
- `uv init` created no `.gitignore` (it skips VCS setup inside an existing git repo). The root `.gitignore` already excludes `api/.venv/` — verified with `git check-ignore`.
- Resolved interpreter: CPython 3.13.13; `requires-python = ">=3.13"`.
- Installed: fastapi 0.141.1, uvicorn 0.54.0, plus 11 transitive packages (pydantic 2.13.5, starlette 1.7.0, anyio 4.15.1, …).

## Post-scaffold audit

**Tool**: pip-audit (invoked as `uvx pip-audit -r <uv export> --format json`, because `pip-audit` is not installed locally)
**Summary**: 0 CRITICAL, 0 HIGH, 0 MODERATE, 0 LOW
**Direct vs transitive**: not distinguished by this tool. 13 packages audited: 2 direct (fastapi, uvicorn), 11 transitive. No known vulnerabilities in either group.

#### CRITICAL findings

None.

#### HIGH findings

None.

#### MODERATE findings

None.

#### LOW / INFO findings

None.

## Hints recorded but not acted on

| Hint                     | Value                                                                 |
| ------------------------ | --------------------------------------------------------------------- |
| bootstrapper_confidence  | first-class                                                           |
| quality_override         | false                                                                 |
| path_taken               | custom                                                                |
| self_check_answers       | typed: true, from_official_starter: true, conventions: true, docs_current: true, can_judge_agent: false |
| team_size                | solo                                                                  |
| deployment_target        | railway                                                               |
| ci_provider              | github-actions                                                        |
| ci_default_flow          | auto-deploy-on-merge                                                  |
| has_auth                 | true                                                                  |
| has_payments             | false                                                                 |
| has_realtime             | false                                                                 |
| has_ai                   | true                                                                  |
| has_background_jobs      | true                                                                  |

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- `git init` (if you have not already) to start your own repo history.
- Review any `.scaffold` siblings the conflict policy created and decide which version of each file to keep.
- Address audit findings per your project's risk tolerance — the full breakdown is in this log.

Module-specific follow-ups (from the hand-off, not scaffolded in v1):
- The starter ships only FastAPI + Uvicorn. LangGraph, SQLAlchemy 2, Alembic, a PostgreSQL driver, and FastAPI Users are added on top.
- `main.py` is a `uv init` placeholder (prints "Hello from api!"), not a FastAPI app yet.
- Separate GitHub Actions deploys for `szczur-radar-api/` and `szczur-radar-web/` to Railway are not configured yet.
- Frontend: run `/10x-bootstrapper context/foundation/tech-stack-web.md` from inside `szczur-radar-web/`.
