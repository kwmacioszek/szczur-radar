# Archive SHA repoint — wykop-ingest-completeness

- **Date**: 2026-10-24
- **Target**: `origin/main` @ `0917117` (fetched before the repoint)
- **Integration commit**: `2ed995c` — S-16: wykop-ingest-completeness — Wykop nie gubi postów po cichu (#94)
- **PR**: https://github.com/kwmacioszek/szczur-radar/pull/94 (merged, base `main`, squash)
- **Evidence**: every old SHA is in the PR's commit list and is not an ancestor of the target; `2ed995c` is an ancestor of the target; the tree of `2ed995c` equals the PR head `12cd465` (`git diff 2ed995c 12cd465`: 0 lines), so the squash integrates exactly the implementation these rows cover.
- **Decision**: operator chose "Zaktualizuj i archiwizuj" (2026-10-24).

| Row | Old suffix (resolved OID) | New SHA |
| --- | --- | --- |
| 1.1 | `7cdd713` (`7cdd713b661ca8da4589fa8650ec05db7644dae7`) | `2ed995c` |
| 1.2 | `7cdd713` (`7cdd713b661ca8da4589fa8650ec05db7644dae7`) | `2ed995c` |
| 1.3 | `9a6e7ab` (`9a6e7ab1acdf9b6b1aa0f1edf73992b741bcbf73`) | `2ed995c` |
| 1.4 | `9a6e7ab` (`9a6e7ab1acdf9b6b1aa0f1edf73992b741bcbf73`) | `2ed995c` |
| 2.1 | `f08c06a` (`f08c06a8fcd39d5baed6b6b32dac1eae9b917496`) | `2ed995c` |
| 2.2 | `f08c06a` (`f08c06a8fcd39d5baed6b6b32dac1eae9b917496`) | `2ed995c` |
| 2.3 | `f08c06a` (`f08c06a8fcd39d5baed6b6b32dac1eae9b917496`) | `2ed995c` |
| 2.4 | `f08c06a` (`f08c06a8fcd39d5baed6b6b32dac1eae9b917496`) | `2ed995c` |
| 2.5 | `0a64721` (`0a64721a5fd41b2b6144453dd6e52a1f7a6e7ec6`) | `2ed995c` |
| 3.1 | `8ab8bc7` (`8ab8bc7529ab248588b28f21089aad4a12af088d`) | `2ed995c` |
| 3.2 | `8ab8bc7` (`8ab8bc7529ab248588b28f21089aad4a12af088d`) | `2ed995c` |
| 3.3 | `8ab8bc7` (`8ab8bc7529ab248588b28f21089aad4a12af088d`) | `2ed995c` |
| 3.4 | `c63b1ef` (`c63b1efc516e319c8b421e77f3801066760711f3`) | `2ed995c` |
| 4.1 | `220f3af` (`220f3afb588649c5ea41444f013e1aee7ac16e4f`) | `2ed995c` |
| 4.2 | `1cf5f7b` (`1cf5f7be8bf3224704f0575ab4fa0f2e55386993`) | `2ed995c` |
| 4.3 | `60b2541` (`60b25411612aaa72614a1d81f5fb1279380b6512`) | `2ed995c` |
| 4.4 | `ef73646` (`ef7364649741b5aea51930ddc71548c2d1fe6136`) | `2ed995c` |

Rows repointed: 17.
