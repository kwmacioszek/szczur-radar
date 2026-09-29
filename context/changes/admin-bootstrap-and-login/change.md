---
change_id: admin-bootstrap-and-login
title: Pierwszy admin przez CLI, logowanie/wylogowanie i ochrona tras
status: impl_reviewed
created: 2026-09-27
updated: 2026-09-29
archived_at: null
---

## Notes

Roadmap S-01. Własna warstwa auth (PyJWT + argon2, JWT w ciasteczku HttpOnly z `token_version`), pierwsza migracja Alembic (pre-deploy na Railway), CLI `create-admin`, logowanie/wylogowanie i guard w SPA, test kompletności autoryzacji jako wzorzec dla kolejnych tras.

2026-09-29: trzeci przegląd implementacji (`8ecedd8`) — APPROVED dla faz 1–3, 0 nowych ustaleń; status pozostaje `impl_reviewed`. Merge czeka na warunki fazy 4, w szczególności brakujący produkcyjny `JWT_SECRET`. Raport: `reviews/impl-review-rereview3-2026-09-29.md`.
