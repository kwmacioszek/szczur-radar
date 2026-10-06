# Archive SHA repoint

## 2026-10-06

- **Target**: `origin/main` @ `721bb8ad19e5c3bcb0b4a460a6bd2f46fbe133ae` (refreshed before archive)
- **Integration commit**: `721bb8a` — "api-docker-image: obraz Dockera dla szczur-radar-api i wyłączenie PITR (#33)" (squash merge)
- **Association**: https://github.com/kwmacioszek/szczur-radar/pull/33 — commit list `16fb1cb, f96804a, c5f0b23, 0c6c003`; base `main`, state MERGED
- **Evidence**: `721bb8a` adds `szczur-radar-api/Dockerfile`, `.dockerignore`, changes `.railway/railway.ts`, `.github/workflows/ci.yml` and the change folder; `git diff f96804a 721bb8a -- szczur-radar-api/Dockerfile szczur-radar-api/.dockerignore` is empty. `f96804a` is not an ancestor of the target (exit 1), `721bb8a` is (exit 0).
- **Decision**: user chose "Zaktualizuj i archiwizuj" (2026-10-06).
- **Not repointed**: rows 2.1–2.7 keep `90c49c9` (PR #34 not merged at archive time).

| Row ID | Old suffix (resolved OID) | New SHA |
| --- | --- | --- |
| 1.1 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.2 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.3 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.4 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.5 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.6 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.12 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.7 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.8 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.9 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.10 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.11 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |
| 1.13 | f96804a (f96804aaf65fbb1d3c5e887736c83dc715310813) | 721bb8a |

Affected rows: 13.
