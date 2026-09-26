---
starter_id: vite-vue
package_manager: npm
project_name: szczur-radar-web
hints:
  language_family: js
  team_size: solo
  deployment_target: self-host
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: verified
  path_taken: custom
  quality_override: false
  self_check_answers:
    typed: true
    from_official_starter: false
    conventions: false
    docs_current: true
    can_judge_agent: false
  has_auth: false
  has_payments: false
  has_realtime: false
  has_ai: false
  has_background_jobs: false
---

## Why this stack

This is the frontend half of a two-module project (`szczur-radar-api` is the companion backend hand-off). The recommended default for `(web, js)` is 10x Astro Starter, but it bundles Supabase (database + auth) and Cloudflare edge deployment that this project doesn't need — the real backend, data store, and business logic already live in the separately-chosen FastAPI service, and the PRD explicitly has no auth. Custom path chosen deliberately: after comparing vite-vue, nuxt, vite-react, and angular, vite-vue (Vue 3 + TypeScript SPA, official Vite template, no bundled router or data layer) fits best given KISS scope discipline, the small MVP surface (a post list with sort/filter and a detail view), and the user's prior Vue experience. It clears all four agent-friendly gates in the registry. The five-point self-check came back mixed (typed and docs-current confirmed; official-starter recognition, convention-recognizability, and agent-output judgment marked as gaps) — this is a known, accepted gap in personal confidence, not a stack quality-gate failure, so `quality_override` stays false; router (`vue-router`) and any data layer will be added incrementally as the app grows, per an explicit "start minimal, extend later" decision. Deployment is self-host, alongside the backend; CI mirrors the backend (GitHub Actions, auto-deploy-on-merge).
