---
change_id: api-docker-image
title: Api docker image i wyłączenie PITR
status: implementing
created: 2026-10-04
updated: 2026-10-05
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- Triage F1: wspólny Dockerfile lokalnie, w CI i na Railway; Railway buduje z GitHub tak jak web. Ruchome tagi Temurin pozostają; plan i brief doprecyzowane.
- Decyzja użytkownika (2026-10-04): w tej zmianie wyłączyć Postgres PITR i usunąć bucket `Postgres-PITR`; backup bazy i test odtwarzania zaplanujemy później ze względu na koszty. Operacje infrastrukturalne są częścią implementacji, obecnie aktualizujemy plan.
- Triage F3: opis wycofania dopasowany do nowego zakresu — cofnięcie Dockerfile’a przywraca poprzedni start API i nie przywraca PITR.
- Plan review 2026-10-04 (`reviews/plan-review.md`): F1 — zamiast PITR dzienny backup wolumenu Railway (Daily) włączony na produkcji przed `pitr disable`, test odtwarzania warunkiem przed S-05; F4 — ręczny redeploy web w PR env; F2 odrzucone, F3 zaakceptowane.
