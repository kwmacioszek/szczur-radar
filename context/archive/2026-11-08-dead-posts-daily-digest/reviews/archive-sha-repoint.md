# Archive SHA repoint — dead-posts-daily-digest

## 2026-11-11

- **Target**: `origin/main` (remote `origin`, default branch per `git ls-remote --symref origin HEAD`; PR #162 base `main`), snapshot `7b263b0`.
- **Integration commit**: `2deaf39` — "S-34: podsumowanie nocy w ntfy zamiast alarmu o martwym poście (dead-posts-daily-digest) (#162)".
- **PR**: https://github.com/kwmacioszek/szczur-radar/pull/162 (MERGED, squash).
- **Evidence**: all four old SHAs are in PR #162's commit list (66dd928 … 71e2e97); the tree of `2deaf39` is identical to the PR head `71e2e97`; `git merge-base --is-ancestor 2deaf39 7b263b0` → 0; each old SHA → 1 (not in target history).
- **Scope**: the implementation of phases 1–4 (API alert channel to ntfy, night summary, cron call, docs). Rows 4.7, 4.8, 4.9 had no SHA and stay unchanged.
- **Decision**: user chose „Zaktualizuj i archiwizuj” in `/10x-archive`.

| Row ID | Old suffix (resolved) | New SHA |
| --- | --- | --- |
| 1.1–1.7 | `8a8f040` (feat … kanał ntfy dla zdarzeń API (p1)) | `2deaf39` |
| 2.1–2.5 | `aced7b1` (feat … podsumowanie nocy w API … (p2)) | `2deaf39` |
| 3.1–3.3 | `2fba6ad` (feat … cron wysyła podsumowanie nocy (p3)) | `2deaf39` |
| 4.1–4.6 | `cb9255f` (docs … PRD, runbook S-34, wytyczne i roadmapa (p4)) | `2deaf39` |

Affected rows: 21.
