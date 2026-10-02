---
starter_id: vite-vue
package_manager: npm
project_name: szczur-radar-web
hints:
  language_family: js
  team_size: solo
  deployment_target: railway
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: verified
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
  has_background_jobs: false
---

## Why this stack

Szczur Radar's frontend (this module only — the backend, now Spring Boot since F-03, is a separate tech-stack decision) is a solo-built Vue 3 + TypeScript single-page app that consumes the backend's REST API; it has no server logic of its own. Vite+Vue was chosen over Nuxt: Nuxt's own server engine (Nitro) and SSR would duplicate what the separate backend already owns, while Vite+Vue is an unopinionated SPA shell built for exactly this "bring your own backend" shape. TypeScript keeps the frontend typed end-to-end, mirroring the backend's Java request/response records across the API boundary. The starter ships no routing or test runner — vue-router and a test tool (e.g. Vitest) are added on top, not inherited. Deployment targets Railway rather than the starter's default CDN hosts (Cloudflare Pages/Vercel/Netlify), a deliberate choice to keep both modules on one platform alongside the backend. CI runs on GitHub Actions with auto-deploy-on-merge, matching the backend and the solo, short-feedback profile.
