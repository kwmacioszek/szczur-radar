---
change_id: post-m4-ui-readiness-audit
title: Audyt gotowości po M-4: na czym stanie nowe UI i Playwright w M-5
status: preparing
created: 2026-11-15
updated: 2026-11-15
archived_at: null
---

## Notes

Zawężony audyt przed otwarciem M-5 (decyzja użytkownika 2026-11-15, ta sama kolejność co przed M-4: mapa projektu i destylacja domeny w PR #174, potem ten audyt, decyzje właściciela, PRD v4, `/10x-roadmap`). Pytanie: na czym stanie nowe UI/UX i testy E2E na Playwright — co jest gotowe, czego brakuje. Zakres: (1) fundament SPA (tokeny, wspólne komponenty, kitchen sink, lista `CLEANED`, `AdminView.vue`); (2) luki API i PRD wobec makiet z `context/foundation/ui-ux-proposals.md`; (3) podłoga E2E (`dev/mock-api.js` z S-43, logowanie i sesja, tryby, 7 stanów, brak `@playwright/test`); (4) dane pod agregację po celu (R-39 kanał jako cel, Q-15 feed a `not_investment_related`). Wynik: `research.md` jako materiał do decyzji właściciela, PRD v4 i wycinków M-5, nie plan.
