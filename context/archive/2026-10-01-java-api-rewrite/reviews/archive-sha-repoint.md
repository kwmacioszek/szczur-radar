# Archive SHA repoint — java-api-rewrite

## 2026-10-03

- **Target:** `origin/main` (base branch of PR #26, `git ls-remote --symref origin HEAD` → `refs/heads/main`), snapshot `9ef707e4b21ad32b38c4aa78327be730e1fdb00d` (fetched before diagnosis; repository not shallow).
- **Integration commit:** `7a406f2` — "F-03: Backend w Javie (Spring Boot) — java-api-rewrite (#26)", the squash merge of https://github.com/kwmacioszek/szczur-radar/pull/26 (base `main`).
- **Evidence:** all five replaced SHAs are in PR #26's commit list; each fails `git merge-base --is-ancestor` against the target (exit 1) while `7a406f2` passes (exit 0). The squash diff contains the implementation covered by the rows (170 files; 62 under `szczur-radar-api/src/`, incl. `HealthController`, `AuthController`, `CreateAdminCommand`, `SkeletonAgent`, `V2__create_spring_session.sql`; `pom.xml`, `.github/workflows/ci.yml`, `.railway/railway.ts`, `deploy-plan.md`).
- **Decision:** user selected „Zaktualizuj i archiwizuj” during `/10x-archive java-api-rewrite`.

| Row ID | Old suffix (resolved OID) | New SHA |
| --- | --- | --- |
| 1.1 | 75fe622 (75fe622f80fbe9c2442110df97e900c582b66e72) | 7a406f2 |
| 1.2 | 75fe622 (75fe622f80fbe9c2442110df97e900c582b66e72) | 7a406f2 |
| 1.3 | 383cfc5 (383cfc5ba30239aa00defc70f6b9e47482cc5098) | 7a406f2 |
| 1.4 | 383cfc5 (383cfc5ba30239aa00defc70f6b9e47482cc5098) | 7a406f2 |
| 1.5 | 383cfc5 (383cfc5ba30239aa00defc70f6b9e47482cc5098) | 7a406f2 |
| 1.6 | 383cfc5 (383cfc5ba30239aa00defc70f6b9e47482cc5098) | 7a406f2 |
| 2.1 | e0c455c (e0c455ca1fab805d70908d7daa5b0e686eae1eab) | 7a406f2 |
| 2.2 | e0c455c (e0c455ca1fab805d70908d7daa5b0e686eae1eab) | 7a406f2 |
| 2.3 | e0c455c (e0c455ca1fab805d70908d7daa5b0e686eae1eab) | 7a406f2 |
| 2.4 | e0c455c (e0c455ca1fab805d70908d7daa5b0e686eae1eab) | 7a406f2 |
| 2.5 | e0c455c (e0c455ca1fab805d70908d7daa5b0e686eae1eab) | 7a406f2 |
| 2.6 | e0c455c (e0c455ca1fab805d70908d7daa5b0e686eae1eab) | 7a406f2 |
| 2.7 | e0c455c (e0c455ca1fab805d70908d7daa5b0e686eae1eab) | 7a406f2 |
| 3.1 | ecdbd42 (ecdbd427a84611c63b19f648c2fe49ea806eb5ac) | 7a406f2 |
| 3.2 | ecdbd42 (ecdbd427a84611c63b19f648c2fe49ea806eb5ac) | 7a406f2 |
| 3.3 | ecdbd42 (ecdbd427a84611c63b19f648c2fe49ea806eb5ac) | 7a406f2 |
| 3.4 | ecdbd42 (ecdbd427a84611c63b19f648c2fe49ea806eb5ac) | 7a406f2 |
| 3.5 | ecdbd42 (ecdbd427a84611c63b19f648c2fe49ea806eb5ac) | 7a406f2 |
| 4.1 | 4c6fb00 (4c6fb009ec9cdefc91df5c242218c8d1df2b7ecc) | 7a406f2 |
| 4.2 | 4c6fb00 (4c6fb009ec9cdefc91df5c242218c8d1df2b7ecc) | 7a406f2 |
| 4.3 | 4c6fb00 (4c6fb009ec9cdefc91df5c242218c8d1df2b7ecc) | 7a406f2 |
| 4.4 | 4c6fb00 (4c6fb009ec9cdefc91df5c242218c8d1df2b7ecc) | 7a406f2 |
| 4.5 | 4c6fb00 (4c6fb009ec9cdefc91df5c242218c8d1df2b7ecc) | 7a406f2 |

Affected rows: 23. Rows 4.6–4.8 already pointed at 7a406f2 and were not changed.
