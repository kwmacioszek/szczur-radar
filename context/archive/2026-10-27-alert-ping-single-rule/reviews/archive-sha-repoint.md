# Archive SHA repoint: alert-ping-single-rule

## 2026-10-29

- **Target**: `origin/main` (remote default branch, `git ls-remote --symref origin HEAD`), snapshot `39a3697c2c3f225ece56857f7537b03df72db1cd`
- **Integration commit**: `9e47c87` "S-25: jedna reguła adresu check-in alarmów (alert-ping-single-rule) (#125)" — ancestor of the target snapshot (`git merge-base --is-ancestor`, exit 0)
- **PR**: https://github.com/kwmacioszek/szczur-radar/pull/125 (MERGED, base `main`, squash merge `9e47c87a74fd0ef3ea8ffe2624093e23552fe9a6`)
- **Evidence**: the PR's commit list contains `f9d920c`, `704747b`, `7a3a68b` (and the later review fixes and main merges); none of the three is an ancestor of the target (exit 1, repository not shallow). The tree of `9e47c87` equals the PR's last commit `5585870`, and its diff against its parent `ffb82a8` carries the implementation: `AlertSettings`, both cron blocks (`backup.sh`, `trigger.sh`), `checks.sh`, step 9 of `tools/healthchecks/test.sh`, the case table and the tests.
- **Decision**: the user chose "Zaktualizuj i archiwizuj" (update and archive) in `/10x-archive`.

| Row ID | Old suffix (resolved OID) | New SHA |
|---|---|---|
| 1.1 | `f9d920c` (feat(alert-ping-single-rule): Reguła i tabela przypadków w API (p1)) | `9e47c87` |
| 1.2 | `f9d920c` | `9e47c87` |
| 1.3 | `f9d920c` | `9e47c87` |
| 1.4 | `f9d920c` | `9e47c87` |
| 2.1 | `704747b` (feat(alert-ping-single-rule): Kopie w shellu i strażnik zgodności (p2)) | `9e47c87` |
| 2.2 | `704747b` | `9e47c87` |
| 2.3 | `704747b` | `9e47c87` |
| 2.4 | `704747b` | `9e47c87` |
| 2.5 | `704747b` | `9e47c87` |
| 3.1 | `7a3a68b` (feat(alert-ping-single-rule): Limit czasu jako 1 i dokumenty operatora (p3)) | `9e47c87` |
| 3.2 | `7a3a68b` | `9e47c87` |
| 3.3 | `7a3a68b` | `9e47c87` |
| 3.4 | `7a3a68b` | `9e47c87` |

Affected rows: 13. Rows 3.5 and 3.6 already pointed at `9e47c87` and were not changed.
