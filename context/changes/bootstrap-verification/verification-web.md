---
bootstrapped_at: 2026-09-25T09:31:21Z
starter_id: vite-vue
starter_name: "Vite + Vue"
project_name: szczur-radar-web
language_family: js
package_manager: npm
cwd_strategy: subdir-then-move
bootstrapper_confidence: verified
phase_3_status: ok
audit_command: "npm audit --json"
---

## Hand-off

```yaml
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
```

This is the frontend half of a two-module project (`szczur-radar-api` is the companion backend hand-off). The recommended default for `(web, js)` is 10x Astro Starter, but it bundles Supabase (database + auth) and Cloudflare edge deployment that this project doesn't need — the real backend, data store, and business logic already live in the separately-chosen FastAPI service, and the PRD explicitly has no auth. Custom path chosen deliberately: after comparing vite-vue, nuxt, vite-react, and angular, vite-vue (Vue 3 + TypeScript SPA, official Vite template, no bundled router or data layer) fits best given KISS scope discipline, the small MVP surface (a post list with sort/filter and a detail view), and the user's prior Vue experience. It clears all four agent-friendly gates in the registry. The five-point self-check came back mixed (typed and docs-current confirmed; official-starter recognition, convention-recognizability, and agent-output judgment marked as gaps) — this is a known, accepted gap in personal confidence, not a stack quality-gate failure, so `quality_override` stays false; router (`vue-router`) and any data layer will be added incrementally as the app grows, per an explicit "start minimal, extend later" decision. Deployment is self-host, alongside the backend; CI mirrors the backend (GitHub Actions, auto-deploy-on-merge).

## Pre-scaffold verification

| Signal      | Value                                       | Severity | Notes                                                              |
| ----------- | -------------------------------------------- | -------- | -------------------------------------------------------------------- |
| npm package | create-vite v9.2.1, published 2026-09-10     | fresh    | resolved from `cmd_template` (`npm create vite@latest`)              |
| GitHub repo | not run                                      | n/a      | card `docs_url` (`https://vuejs.org`) is not a `github.com` URL      |

## Scaffold log

**Resolved invocation**: `npm create vite@latest .bootstrap-scaffold -- --template vue-ts`
**Strategy**: subdir-then-move
**Exit code**: 0
**Files moved**: 11 top-level entries (`.gitignore`, `.vscode/`, `README.md`, `index.html`, `package.json`, `public/`, `src/`, `tsconfig.app.json`, `tsconfig.json`, `tsconfig.node.json`, `vite.config.ts`)
**Conflicts (.scaffold siblings)**: none (cwd was empty)
**.gitignore handling**: moved silently (no pre-existing `.gitignore` in cwd)
**.bootstrap-scaffold cleanup**: deleted

**Note**: `cmd_template` for this starter does not chain an install step (unlike, e.g., Next.js's template), so no `node_modules/` or lockfile existed immediately after scaffold — consistent with the skill's "does not install dependencies beyond what cmd_template itself runs" rule. `package.json`'s `name` field reads `"bootstrap-scaffold"` (inherited from the temp directory name); worth renaming manually to `szczur-radar-web` or similar.

## Post-scaffold audit

**Tool**: `npm audit --json`
**Status**: first attempt failed — `ENOLOCK` ("This command requires an existing lockfile"), because no install had run yet. Recovered by generating the lockfile only (`npm i --package-lock-only`, no full `node_modules` install beyond what that entails) per npm's own suggested recovery command, then re-ran the audit.
**Summary**: 0 CRITICAL, 0 HIGH, 0 MODERATE, 0 LOW
**Direct vs transitive**: 25 prod + 49 dev + 27 optional = 73 total dependencies scanned; audit tool does not break the zero-finding result down further since there is nothing to attribute.

Clean tree — no advisories found.

## Hints recorded but not acted on

| Hint                     | Value                                                              |
| ------------------------ | -------------------------------------------------------------------- |
| bootstrapper_confidence  | verified                                                              |
| quality_override         | false                                                                 |
| path_taken               | custom                                                                |
| self_check_answers       | typed: true, from_official_starter: false, conventions: false, docs_current: true, can_judge_agent: false |
| team_size                | solo                                                                  |
| deployment_target        | self-host                                                             |
| ci_provider              | github-actions                                                       |
| ci_default_flow          | auto-deploy-on-merge                                                  |
| has_auth                 | false                                                                 |
| has_payments             | false                                                                 |
| has_realtime              | false                                                                 |
| has_ai                   | false                                                                 |
| has_background_jobs      | false                                                                 |

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- `git init` at the monorepo root (not inside `web/`) once both `api/` and `web/` are scaffolded, to start one shared repo history.
- This is one of two modules in a monorepo (`api/` + `web/`); the companion backend hand-off (`context/foundation/tech-stack.md`) is scaffolded separately into `api/` with its own verification log (`verification-api.md`).
- Rename `package.json`'s `"name"` field from `"bootstrap-scaffold"` to something meaningful (e.g. `szczur-radar-web`).
- Run `npm install` for the full `node_modules` (only the lockfile was generated so far) before `npm run dev`.
- Add `vue-router` when you need more than the single list view, per the earlier "start minimal, extend later" decision.
- Address audit findings per your project's risk tolerance — none found this run.
