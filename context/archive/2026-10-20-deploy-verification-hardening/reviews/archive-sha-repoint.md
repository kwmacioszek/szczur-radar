# Archive SHA repoint: deploy-verification-hardening

- **Date**: 2026-10-23T17:40:41Z
- **Target**: `origin/main` (base of PR #99) @ `9ef690b`
- **Integration commit**: `9ef690b` "deploy-verification-hardening: monitor po deployu zamiast deploy-verify (#99)" — squash merge of https://github.com/kwmacioszek/szczur-radar/pull/99
- **Evidence**: PR #99 was squash-merged (one parent); `git diff 9774d8c 9ef690b` (the PR head before the merge vs the squash) is empty, so the squash integrates both old commits. `ad27491` and `76c7110` are not ancestors of `origin/main`.
- **Scope**: rows whose suffix is a szczur-radar commit. Rows with `szczur-radar-monitor@<sha>` point to the monitor repo's history and stay unchanged.
- **Decision**: operator chose "Update and archive" (2026-10-23).

| Row | Old suffix (resolved) | New SHA |
| --- | --- | --- |
| 4.1 | `ad27491` (`ad274911ee310ca3242ff26d097093c18b68fb8e`) | `9ef690b` |
| 4.2 | `ad27491` | `9ef690b` |
| 4.3 | `ad27491` | `9ef690b` |
| 4.4 | `76c7110` (`76c7110173ae3c8da10301651f3e4b0baf12ad42`) | `9ef690b` |
| 4.5 | `76c7110` | `9ef690b` |

Rows repointed: 5.
