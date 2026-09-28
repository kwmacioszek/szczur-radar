# Szczur Radar

## Local development on macOS

Docker Compose runs a local Postgres and builds the web service from the same
Dockerfile and Caddyfile used on Railway. The API runs on the Mac with reload
enabled. The containers publish ports only on localhost. The API development
server binds to all host interfaces so the web container can reach it through
Docker Desktop; use this workflow on a trusted local network.

Start the database and web container from the repository root:

```sh
docker compose -f compose.local.yaml up -d --build
```

Start the API in a second terminal:

```sh
cd szczur-radar-api
DATABASE_URL=postgresql://szczur_local:local_only@127.0.0.1:5432/szczur_radar_local uv run python main.py
```

Open `http://localhost:8080`. The web container proxies `/api/*` to the API
through Docker Desktop's `host.docker.internal`. Check
`http://localhost:8080/api/health` and `http://localhost:8080/api/ready`.
Rebuild the web container after changing Vue code:

```sh
docker compose -f compose.local.yaml up -d --build web
```

Stop the containers with `docker compose -f compose.local.yaml down`. The named
Postgres volume persists across restarts. The database credentials above are
local-only test values; this setup reads no Railway secrets.

`railway dev up` is not used with the current production environment. Its
generated local Compose file includes the production Postgres `WAL_ARCHIVE_*`
settings, which could make local writes use the production PITR bucket. The
committed local Compose file uses Railway's Postgres image without those
settings. Do not copy production variables into this local setup.
