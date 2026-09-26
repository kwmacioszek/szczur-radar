---
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
---

## Why this stack

Solo backend for a personal investment-signal pipeline with a 3-week after-hours timeline and small scale — the priors favor a battle-tested, agent-friendly pick over a bespoke assembly. FastAPI is the recommended default for `(api, python)` and clears all four agent-friendly gates (typed via Pydantic, convention-based, popular within Python training data, well-documented), and it directly matches the project's earlier architectural decision to run the ingestion/LLM-classification/persistence flow as a LangGraph-orchestrated pipeline sitting behind a thin FastAPI layer — FastAPI's lack of a built-in ORM is not a gap here since the pipeline owns persistence directly. Deployment targets self-host rather than the card's nominal Fly default, because the PRD's access-control decision restricts the app to the home network/VPN with no public URL. CI runs on GitHub Actions with auto-deploy-on-merge, the standard solo/small-team shape. The background-job and AI feature flags are set (scheduled daily ingestion, LLM-based classification and trend analysis); auth, payments, and realtime are out of scope per the PRD's non-goals and access-control model.
