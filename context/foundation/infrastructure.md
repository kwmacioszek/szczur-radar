---
project: "Szczur Radar"
researched_at: 2026-09-26
recommended_platform: Railway
runner_up: Fly.io
context_type: mvp
tech_stack:
  language: python
  framework: fastapi
  runtime: "Python >=3.13, uv-managed"
---

## Recommendation

**Deploy the backend (`api/`) on Railway.**

Railway is a full container/VM platform (no serverless-sandbox risk for LangGraph's native-dependency tree, unlike Cloudflare Workers' Pyodide/WASM runtime), has the most mature agent-facing tooling of the shortlist (official GA MCP server, `llms-full.txt` docs built for AI agents), and its usage-based billing with a serverless/sleep mode fits this project's actual usage pattern — a single user checking in sporadically, plus one scheduled daily job — far better than a fixed always-on cost. The developer explicitly prioritized DX over minimizing cost (interview), has no strong existing platform familiarity, and expects single-region traffic only, all of which favor Railway's straightforward Nixpacks-based deploy over a more edge-optimized or DIY self-hosted path.

## Platform Comparison

Hard filter applied first: **Netlify was dropped** — it has no Python function runtime at all (JS/TS/Go only; the "Python support" in its own docs is build-time only for static-site generators). This is a tech-stack incompatibility, not a low score.

| Platform | CLI-first | Managed/serverless | Agent-readable docs | Stable deploy API | MCP / Integration | Total |
|---|---|---|---|---|---|---|
| **Railway** | Partial | Pass | Pass | Pass | Pass | 4 Pass, 1 Partial |
| **Fly.io** | Pass | Pass | Pass | Pass | Partial | 4 Pass, 1 Partial |
| Self-hosted Proxmox | Partial | Fail | Pass | Pass | Partial | 2 Pass, 2 Partial, 1 Fail |
| Render | Partial | Pass | Fail | Pass | Pass | 3 Pass, 1 Partial, 1 Fail |
| Vercel | Pass | Pass* | Pass | Pass | Partial | 4 Pass, 1 Partial* |
| Cloudflare Workers | Pass | Pass* | Pass | Pass | Partial | 4 Pass, 1 Partial* |
| ~~Netlify~~ | — | — | — | — | — | dropped — no Python runtime |

\* Vercel and Cloudflare score well on paper but carry a workload-specific structural risk not captured by the generic criteria: this project needs a long-running, LangGraph-orchestrated background process, not just request/response. Vercel has no persistent-worker primitive (Celery beat explicitly unsupported; Workflows/Queues are Beta/pre-1.0). Cloudflare's Python support runs through Pyodide/WASM (GA only since 2026-09-21) and **LangGraph's compatibility with that sandbox is unconfirmed in any source found** — several of LangGraph's transitive dependencies (tokenizers, tiktoken, grpcio) have native C/Rust extensions that may lack WASM wheels. Both were excluded from the shortlist on this workload-fit basis, not the raw score.

### Shortlisted Platforms

#### 1. Railway (Recommended)

Full Nixpacks/Dockerfile-based Python deploy (GA, confirmed `uv`/`pyproject.toml` support), always-on services for the LangGraph orchestrator plus a distinct Cron Job primitive for the daily ingestion run, managed Postgres, and the most mature agent tooling in the shortlist — an official hosted MCP server (`mcp.railway.com`, GA) and Claude connector, plus docs published as clean markdown per-page and a combined `llms-full.txt`. Its serverless/sleep mode (scale-to-zero after 10 min without outbound traffic, billed per-second average usage) fits this project's low, bursty traffic far better than a fixed-allocation always-on cost — realistic cost is close to the $5/mo Hobby base if the ingestion job is configured as a true Cron Job (not an always-on worker) and the API avoids things that defeat sleep (see Risk Register). Its one real gap: no CLI rollback command — recovery is dashboard-driven within the plan's retention window.

#### 2. Fly.io

Near-identical technical fit — full VM/container ("Machines"), zero WASM/native-extension risk, cheapest raw compute (~$2-6/mo for a single small VM), GA Managed Postgres and Tigris (S3-compatible) object storage, and a complete `flyctl` CLI (deploy/logs; rollback is a two-step `fly releases` + `fly deploy --image` reconstruction rather than one command). Its MCP/agent tooling (`flymcp`, "Sprites") is explicitly early/marketing-stage rather than Railway's mature GA integration, and its native Scheduled Machines only support fuzzy `hourly|daily|weekly|monthly` cycles with no exact time-of-day control — the PRD's FR-003 ("codziennie o określonej godzinie") would need a workaround (Supercronic inside the container, or an external trigger). Both Railway and Fly require accepting a public URL and adding minimal access control, identically — this was not a differentiator between them.

#### 3. Self-hosted Proxmox

The only option that requires **zero changes** to the PRD's existing Access Control decision (self-hosted, VPN-only, no public URL, no auth) — everything else on this shortlist requires accepting a public endpoint and adding an API key. Technically equivalent to Railway/Fly on the one axis that matters most for this workload (it's a normal Linux LXC/VM, no sandbox risk for LangGraph). Scores weakest on "managed/serverless" (the operator owns OS patching, TLS, and backups regardless of which self-hosted layer — Dokku, CapRover, or Coolify — sits on top) and has no cost, since the hardware is already owned. Real trade-off: setup work is needed *now* (LXC/VM + Docker or a self-hosted PaaS layer + reverse proxy + Tailscale/WireGuard for VPN access), and there is no vendor redundancy — a home power or internet outage takes the app down with no automatic failover.

## Anti-Bias Cross-Check: Railway

### Devil's Advocate — Weaknesses

1. No CLI rollback command — recovery is dashboard-only, within the plan's retention window; under time pressure this is a UI click-through, not one command.
2. The serverless/sleep mode can be silently defeated by common Python defaults — a SQLAlchemy connection pool, framework keep-alives, or health-check pings can keep a service "awake" indefinitely, undermining the cost story the recommendation partly rests on, with no obvious warning that it's happening.
3. Nixpacks' default build can start multiple Uvicorn workers; a FastAPI-community thread reports this causing "task is pending" hangs specifically on Railway. Railway's own guidance is 1 worker per instance, scale via replicas — an easy-to-miss configuration detail.
4. Storage Buckets (S3-compatible object storage) carries no explicit GA/beta label in Railway's docs — a shaky foundation if the project later needs object storage.
5. Choosing Railway means accepting a public URL and adding a new access-control mechanism (API key) that the original PRD didn't require — new secret-management surface that self-hosting would not have introduced.

### Pre-Mortem — How This Could Fail

Six months in, the Railway decision has quietly cost more than expected. The team assumed serverless mode would keep costs near the $5 base, but a SQLAlchemy connection pool kept the API "awake" around the clock without anyone noticing — the bill crept to $18/month before anyone investigated why. The daily ingestion job was first built as an always-on service rather than a proper Cron Job, because the distinction wasn't obvious from the quick-start guide, doubling always-on cost for weeks. One evening a bad deploy needed a rollback, and the team discovered mid-incident that Railway's CLI has no rollback command — ten stressful minutes were lost navigating the dashboard instead of running one command. The API key added for public access was committed to a `.env` file early in the project's history, before anyone thought to rotate it or add it to `.gitignore`. None of these were individually fatal, but together they eroded the "simple and cheap" assumption that justified Railway over self-hosting in the first place.

### Unknown Unknowns

1. The exact sleep-trigger mechanics (what counts as "outbound traffic" that resets the idle timer) aren't fully transparent from the docs alone — verify empirically against the actual monthly bill rather than trusting the feature description.
2. Storage Buckets is newly shipped with no clear stability label — building on it now risks an unplanned migration later.
3. Free/Trial tiers are not viable for this workload (0.5GB RAM/1 vCPU cap on Free; one-time, non-rolling $5 Trial credit) — Hobby ($5/mo base) is the real starting point, not a free option to fall back on.
4. Billing reflects *average* resource consumption over time, not peak — a CPU/memory spike during heavier LLM processing (e.g., once trend-analysis in FR-014 is built) could move the bill in ways a "quiet month" baseline wouldn't predict.
5. Adding public-facing auth is a genuinely new attack surface for this project — previously (VPN-only) there were zero exposed endpoints; now credential storage, leakage, and rotation become real operational concerns for the first time.

## Operational Story

- **Preview deploys**: Not verified in this research — Railway's PR/branch-preview environment behavior should be confirmed against current docs before relying on it for a CI workflow; do not assume specifics not covered here.
- **Secrets**: Stored as Railway service Variables (`railway variables set KEY=value` or dashboard), scoped per service/environment. Exact rotation workflow was not verified in this research — confirm before treating any secret as easily rotatable.
- **Rollback**: No CLI rollback command exists. Recovery is dashboard-driven (Deployments tab → "…" → Rollback), limited to the plan's retention window. `railway redeploy --service <name>` re-runs the *latest* deployment — it is not a rollback to an older one.
- **Approval**: Require human sign-off before: adding/rotating the new public API key, any deploy that changes the ingestion schema or database migration, or deleting/resetting data. An agent may safely, unattended: check deploy status, tail logs, read metrics, and trigger a redeploy of the current known-good image.
- **Logs**: `railway logs` (CLI, GA) tails deploy/build/runtime logs. The official MCP server (`mcp.railway.com`, GA; add via `claude mcp add railway --transport http https://mcp.railway.com`) gives an agent structured, read-capable access to deployments and logs without shell-parsing CLI output.

## Risk Register

| Risk | Source | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| Serverless sleep silently defeated by DB connection pooling / keep-alives, causing unexpected cost | Devil's advocate / Unknown unknowns | H | M | Configure the DB client with short pool recycle / close-on-idle for this low-traffic use case; check Railway's usage dashboard monthly for the first few months to confirm sleep is actually happening. |
| Ingestion pipeline built as an always-on service instead of a Cron Job, doubling cost | Pre-mortem | M | M | Explicitly configure the daily ingestion job as a Railway **Cron Job** service type from day one — not an always-on service. |
| Multiple Uvicorn workers spawned by default causing request hangs | Research finding | M | H | Explicitly pin the start command to `--workers 1`; scale via Railway replicas only if ever needed, not via multi-worker Uvicorn. |
| No CLI rollback — recovery is dashboard-only, slower under incident pressure | Devil's advocate | M | M | Document the exact dashboard rollback click-path in `AGENTS.md`; ship small, frequent deploys to reduce the blast radius of any single bad one. |
| New public API key leaked or mismanaged (e.g., committed to git) | Devil's advocate / Pre-mortem / Unknown unknowns | L | H | Store the key only in Railway Variables, never in a committed file; add `.env` to `.gitignore` before the first commit; rotate immediately if leakage is ever suspected. |
| Storage Buckets feature has unclear GA/beta status | Research finding | L | M | Do not build on Storage Buckets until Railway's docs clarify its status; use Postgres for structured data now, revisit object storage only if genuinely needed. |
| Billing reflects average, not peak, usage — a heavier future LLM workload (e.g., FR-014 trend analysis) could raise costs unpredictably | Unknown unknowns | M | L | Review the Railway usage dashboard after adding any new LLM-calling feature, before assuming the existing cost baseline still holds. |

## Getting Started

1. Wire up the actual FastAPI app in `api/main.py` before deploying — it currently still holds the default `uv init` stub, per `AGENTS.md`.
2. Install the Railway CLI and authenticate: `npm i -g @railway/cli && railway login` (verify against Railway's current install docs — CLI distribution methods change).
3. From `api/`, create/link the Railway project: `railway init` (new project) or `railway link` (existing project) — Nixpacks should auto-detect the `pyproject.toml` + `uv.lock` already present.
4. Set required secrets before first deploy: `railway variables set APP_API_KEY=<new-access-key> LLM_API_KEY=<your-llm-provider-key>` (the `APP_API_KEY` is the new minimal access control this platform choice requires — reflect it in `context/foundation/prd.md`'s Access Control section).
5. Deploy: `railway up`. Confirm the start command pins `--workers 1` to avoid the multi-worker Uvicorn hang risk noted above.
6. Configure the daily ingestion pipeline as a separate **Cron Job** service (not an always-on service) in Railway's service settings, so it only bills for its actual run time.
7. Tail logs to confirm a clean boot: `railway logs`.

## Out of Scope

The following were not evaluated in this research:
- Docker image configuration
- CI/CD pipeline setup
- Production-scale architecture (multi-region, HA, DR)
- The frontend (`web/`) module's hosting — it is a static SPA with no persistent-process requirement and was treated as trivial to host anywhere (including alongside the backend or self-hosted); it was not put through the full platform comparison.
