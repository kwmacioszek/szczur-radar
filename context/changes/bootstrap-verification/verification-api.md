---
bootstrapped_at: 2026-09-25T09:29:06Z
starter_id: fastapi
starter_name: FastAPI
project_name: szczur-radar-api
language_family: python
package_manager: uv
cwd_strategy: native-cwd
bootstrapper_confidence: first-class
phase_3_status: ok
audit_command: "pip-audit (via `uv run --with pip-audit pip-audit --format json`)"
---

## Hand-off

```yaml
starter_id: fastapi
package_manager: uv
project_name: szczur-radar-api
hints:
  language_family: python
  team_size: solo
  deployment_target: self-host
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: first-class
  path_taken: standard
  quality_override: false
  self_check_answers: null
  has_auth: false
  has_payments: false
  has_realtime: false
  has_ai: true
  has_background_jobs: true
```

Solo backend for a personal investment-signal pipeline with a 3-week after-hours timeline and small scale — the priors favor a battle-tested, agent-friendly pick over a bespoke assembly. FastAPI is the recommended default for `(api, python)` and clears all four agent-friendly gates (typed via Pydantic, convention-based, popular within Python training data, well-documented), and it directly matches the project's earlier architectural decision to run the ingestion/LLM-classification/persistence flow as a LangGraph-orchestrated pipeline sitting behind a thin FastAPI layer — FastAPI's lack of a built-in ORM is not a gap here since the pipeline owns persistence directly. Deployment targets self-host rather than the card's nominal Fly default, because the PRD's access-control decision restricts the app to the home network/VPN with no public URL. CI runs on GitHub Actions with auto-deploy-on-merge, the standard solo/small-team shape. The background-job and AI feature flags are set (scheduled daily ingestion, LLM-based classification and trend analysis); auth, payments, and realtime are out of scope per the PRD's non-goals and access-control model.

## Pre-scaffold verification

| Signal      | Value    | Severity | Notes                                                                                     |
| ----------- | -------- | -------- | ------------------------------------------------------------------------------------------ |
| npm package | not run  | n/a      | `python` language family; no npm-distributed CLI involved                                  |
| GitHub repo | not run  | n/a      | card `docs_url` (`https://fastapi.tiangolo.com`) is not a `github.com` URL — no recency signal available |

## Scaffold log

**Resolved invocation**: `uv init . && uv add fastapi uvicorn`
**Strategy**: native-cwd
**Exit code**: 0
**Pre-flight files-to-touch**: could not enumerate ahead of time (standard `uv init` output only known after running)
**Files written by CLI**: `.gitignore`, `.python-version`, `README.md`, `main.py`, `pyproject.toml`, `uv.lock`, `.venv/` (7 top-level entries)
**Pre-existing files preserved**: none (cwd was empty before scaffold)

**Note**: `uv init .` also initialised its own empty (no-commit) `.git/` as a side effect. Since this project is one module of a monorepo (root repo lives at the parent directory alongside `web/` and `context/`), the nested `.git/` was removed immediately after scaffold, with explicit user confirmation, to avoid a submodule-shaped conflict when the root repo is initialised.

## Post-scaffold audit

**Tool**: `pip-audit` — not pre-installed; first attempt via `uvx pip-audit` audited the wrong (ephemeral, isolated) environment and was discarded. Re-run via `uv run --with pip-audit pip-audit --format json` to audit the project's actual `.venv`.
**Summary**: 0 CRITICAL, 0 HIGH, 0 MODERATE, 0 LOW
**Direct vs transitive**: not distinguished by this tool

Clean tree — no advisories found across `fastapi`, `uvicorn`, `starlette`, `pydantic`, `pydantic-core`, `anyio`, `h11`, `click`, `idna`, `annotated-types`, `annotated-doc`, `typing-extensions`, `typing-inspection` (39 packages scanned total, including pip-audit's own toolchain).

## Hints recorded but not acted on

| Hint                     | Value             |
| ------------------------ | ------------------ |
| bootstrapper_confidence  | first-class         |
| quality_override         | false               |
| path_taken               | standard            |
| self_check_answers       | null                |
| team_size                | solo                |
| deployment_target        | self-host           |
| ci_provider              | github-actions      |
| ci_default_flow          | auto-deploy-on-merge |
| has_auth                 | false               |
| has_payments             | false               |
| has_realtime             | false               |
| has_ai                   | true                |
| has_background_jobs      | true                |

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- `git init` at the monorepo root (not inside `api/`) once both `api/` and `web/` are scaffolded, to start one shared repo history.
- This is one of two modules in a monorepo (`api/` + `web/`); the companion frontend hand-off (`context/foundation/tech-stack-web.md`) is scaffolded separately into `web/` with its own verification log (`verification-web.md`).
- Address audit findings per your project's risk tolerance — none found this run, but re-run `pip-audit` after adding dependencies (e.g. LangGraph, a DB driver).
