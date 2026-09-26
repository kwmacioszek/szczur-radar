---
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
---

## Why this stack

Szczur Radar's backend (this module only — the Vue frontend is a separate tech-stack-selector run) is a FastAPI-based REST API wrapping a LangGraph-orchestrated data and LLM pipeline. FastAPI was chosen because its native Pydantic typing aligns directly with LangGraph's structured input/output, keeping the pipeline-to-API boundary strongly typed end-to-end. Persistence is PostgreSQL via SQLAlchemy 2 and Alembic for migrations, assembled on top of FastAPI rather than inherited from the starter. Authentication uses FastAPI Users for the parts that fit (user model, password hashing, JWT, route guards); public self-registration is disabled, since the product has no public sign-up and no automated email — bootstrap, recovery, and user creation are handled by project-specific CLI commands instead. LangGraph is kept independent of the HTTP layer, so the pipeline can be invoked from scheduled jobs, CLI commands, API endpoints, or tests without change. Deployment targets Railway; CI runs on GitHub Actions with auto-deploy-on-merge, matching the solo, short-feedback profile.
