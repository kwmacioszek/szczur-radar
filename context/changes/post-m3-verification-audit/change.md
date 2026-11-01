---
change_id: post-m3-verification-audit
title: Audyt weryfikacji po M-3: czy testy i strażnicy łapią błędy, na które się powołujemy
status: preparing
created: 2026-11-01
updated: 2026-11-01
archived_at: null
---

## Notes

Zawężony audyt weryfikacji przed otwarciem M-4 (decyzja użytkownika 2026-11-01, kolejność: mapa projektu i destylacja domeny w PR #136, potem ten audyt, decyzje właściciela, PRD v3, `/10x-roadmap`). Zamiast pełnego audytu jak po M-2 sprawdza jedno pytanie: czy obecne testy i strażnicy faktycznie łapią błędy, na które powołują się `context/domain/domain-distillation.md` i `context/map/repo-map.md`. Zakres: strażnicy R-17, R-22, R-30, R-43; kształty JSON bez strażnika; zgodność `dev/mock-api.js` i atrapy API w `tools/pipeline-trigger/test.sh`; testy feedu; R-23 (`runEnded`); D-15 (`PostStore`) i R-41 (seria odmów). Wynik: `research.md` jako materiał do wycinków M-4 (`context/foundation/verification-scope.md`).
