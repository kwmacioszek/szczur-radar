---
bootstrapped_at: 2026-09-26T12:54:09Z
starter_id: vite-vue
starter_name: Vite + Vue
project_name: szczur-radar-web
language_family: js
package_manager: npm
cwd_strategy: subdir-then-move
bootstrapper_confidence: verified
phase_3_status: ok
audit_command: npm audit --json
---

> Frontend module of a two-module repo (`szczur-radar-api/` + `szczur-radar-web/`). Scaffolded from inside
> `szczur-radar-web/` with the hand-off at `context/foundation/tech-stack-web.md`; this log is written to the
> root `context/` instead of a nested `szczur-radar-web/context/`. The backend module's log is
> `verification-api.md`.

## Hand-off

```yaml
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
```

### Why this stack

Szczur Radar's frontend (this module only — the FastAPI backend is a separate tech-stack-selector run) is a solo-built Vue 3 + TypeScript single-page app that consumes the backend's REST API; it has no server logic of its own. Vite+Vue was chosen over Nuxt: Nuxt's own server engine (Nitro) and SSR would duplicate what the separate FastAPI backend already owns, while Vite+Vue is an unopinionated SPA shell built for exactly this "bring your own backend" shape. TypeScript keeps the frontend typed end-to-end, mirroring the backend's Pydantic types across the API boundary. The starter ships no routing or test runner — vue-router and a test tool (e.g. Vitest) are added on top, not inherited. Deployment targets Railway rather than the starter's default CDN hosts (Cloudflare Pages/Vercel/Netlify), a deliberate choice to keep both modules on one platform alongside the backend. CI runs on GitHub Actions with auto-deploy-on-merge, matching the backend and the solo, short-feedback profile.

## Pre-scaffold verification

| Signal      | Value                                          | Severity | Notes                                                        |
| ----------- | ---------------------------------------------- | -------- | ------------------------------------------------------------ |
| npm package | create-vite v9.2.1 published 2026-09-10        | fresh    | resolved from cmd_template (`npm create vite@latest`)        |
| GitHub repo | not run                                        | —        | card `docs_url` is https://vuejs.org (not a GitHub URL)      |

## Scaffold log

**Resolved invocation**: `npm create vite@latest .bootstrap-scaffold -- --template vue-ts` (run inside `szczur-radar-web/`)
**Strategy**: subdir-then-move
**Exit code**: 0
**Files moved**: 18
**Conflicts (.scaffold siblings)**: none (`szczur-radar-web/` was created empty for this run)
**.gitignore handling**: moved silently (module-level `szczur-radar-web/.gitignore`; the root `.gitignore` is separate and unchanged by this step)
**.bootstrap-scaffold cleanup**: deleted

Notes:
- Verified beforehand that `create-vite` runs non-interactively outside a TTY, so it does not stop on the "install and start now?" prompt.
- `create-vite` set `package.json` `name` to `bootstrap-scaffold` (from the temp dir name). Changed to `szczur-radar-web` after the move-up.
- Files: `.gitignore`, `.vscode/extensions.json`, `README.md`, `index.html`, `package.json`, `public/favicon.svg`, `public/icons.svg`, `src/App.vue`, `src/assets/{hero.png,vite.svg,vue.svg}`, `src/components/HelloWorld.vue`, `src/main.ts`, `src/style.css`, `tsconfig.app.json`, `tsconfig.json`, `tsconfig.node.json`, `vite.config.ts`.

## Post-scaffold audit

**Tool**: npm audit --json
**Summary**: 0 CRITICAL, 0 HIGH, 0 MODERATE, 0 LOW
**Direct vs transitive**: 0/0/0/0 direct of total 0/0/0/0. 73 dependencies audited (prod 25, dev 49, optional 27, with overlap); 7 direct: `vue` (prod), `@types/node`, `@vitejs/plugin-vue`, `@vue/tsconfig`, `typescript`, `vite`, `vue-tsc` (dev).

First attempt failed with `ENOLOCK` because the scaffold command does not install dependencies, so no `package-lock.json` existed. With the user's approval, `npm install` was run once (48 packages added, `package-lock.json` created; `node_modules/` is ignored via the module `.gitignore`), then the audit was re-run. The result above is from the second run.

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
| bootstrapper_confidence  | verified                                                              |
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
| has_background_jobs      | false                                                                 |

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- `git init` (if you have not already) to start your own repo history.
- Review any `.scaffold` siblings the conflict policy created and decide which version of each file to keep.
- Address audit findings per your project's risk tolerance — the full breakdown is in this log.

Module-specific follow-ups (from the hand-off, not scaffolded in v1):
- The starter is a bare SPA shell. vue-router, a test runner (e.g. Vitest), and an API client layer for the backend are added on top.
- `src/App.vue` and `src/components/HelloWorld.vue` are Vite demo content, to be replaced.
- Railway deploy for a static Vite build (build command + static serving) and the GitHub Actions workflow for `szczur-radar-web/` are not configured yet.
