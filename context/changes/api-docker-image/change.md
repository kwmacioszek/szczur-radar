---
change_id: api-docker-image
title: Api docker image i wyłączenie PITR
status: implemented
created: 2026-10-04
updated: 2026-10-06
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- Triage F1: wspólny Dockerfile lokalnie, w CI i na Railway; Railway buduje z GitHub tak jak web. Ruchome tagi Temurin pozostają; plan i brief doprecyzowane.
- Decyzja użytkownika (2026-10-04): w tej zmianie wyłączyć Postgres PITR i usunąć bucket `Postgres-PITR`; backup bazy i test odtwarzania zaplanujemy później ze względu na koszty. Operacje infrastrukturalne są częścią implementacji, obecnie aktualizujemy plan.
- Triage F3: opis wycofania dopasowany do nowego zakresu — cofnięcie Dockerfile’a przywraca poprzedni start API i nie przywraca PITR.
- Plan review 2026-10-04 (`reviews/plan-review.md`): F1 — zamiast PITR dzienny backup wolumenu Railway (Daily) włączony na produkcji przed `pitr disable`, test odtwarzania warunkiem przed S-05; F4 — ręczny redeploy web w PR env; F2 odrzucone, F3 zaakceptowane.
- Implementacja 2026-10-05: operator usunął wcześniej bucket `Postgres-PITR` na produkcji (koszty; baza bez danych, backup niepilny). PR env `szczur-radar-pr-33` skopiował stan bez bucketa: PITR `enabled` z pustą konfiguracją S3 (archiwizator nie działał). Operator wykonał `pitr disable` w PR env → `enabled: false`, `bucketWired: false`; bucketa do usunięcia w PR env nie było.
- Weryfikacja PR env 2026-10-06 (1.10, decyzja użytkownika „Adapt and continue”): `railway ssh` loguje się jako root (uid=0) niezależnie od `USER` w Dockerfile; proces aplikacji działa jako `app` (log „started by app in /app”, CI `id -u` = 999). Kryterium spełnione przez proces aplikacji; dokumenty fazy 2 mówią „aplikacja bez roota; `railway ssh` = root”, nie „ssh bez roota”.
- Pomiary PR env 2026-10-06: `memory.max` 999997440 B (~954 MiB), `nproc` 48, `MaxHeapSize` 750780416 B (~716 MiB przy `MaxRAMPercentage=75`), SerialGC (pamięć < 1792 MB dla G1); pierwszy start na Railway 21,8 s (pre-deploy na pustej bazie).
- Produkcja 2026-10-06 (impl-review F1): bucket zniknął z `bucket list`, ale PITR nadal archiwizował WAL (`bucketWired: true`, 9 zestawów). Operator wykonał `pitr disable` na produkcji bez Daily backupu (decyzja użytkownika: pusta baza, backup do czasu subskrypcji brak) → `enabled: false`, `bucketWired: false`, `/api/ready` = 200. CLI: „existing backups are kept” — sprawdzić w billing, czy storage ukrytego bucketa przestał być naliczany.
- Weryfikacja fazy 2, 2026-10-06 (decyzja użytkownika: poprawka w tym PR): nowy PR env `szczur-radar-pr-34` skopiował z produkcji `LLM_SERVICE_TIER=flex` i `LLM_REASONING_EFFORT` (F-02, openrouter); krok CI „Ensure PR-env LLM settings” nadpisywał tylko trzy `LLM_*` (CI: deepseek) → `migrate` padał na walidacji. Krok usuwa teraz skopiowane opcjonalne `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT`, `LLM_TIMEOUT` w PR env (nigdy na produkcji). Poza pierwotnym zakresem planu — błąd integracji F-02 ujawniony przez pierwszy PR env po zmianie zmiennych produkcji.
