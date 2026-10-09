# S-02 first-assessed-wykop-feed — Plan Brief

> Full plan: `context/changes/first-assessed-wykop-feed/plan.md`
> Research: `context/changes/first-assessed-wykop-feed/research.md`

## What & Why

Gwiazda przewodnia MVP: zalogowany użytkownik widzi chronologiczną listę postów z Wykopu #gielda ocenionych przez LLM według kontraktu V2. To pierwszy moment, w którym widać, czy ocena oddziela sygnał od szumu. Przy okazji powstaje trwały, krokowy pipeline, do którego później dołożymy etapy ML i Batch API.

## Starting Point

Klocki są w kodzie: klient i repozytorium Wykopu, mapper, `Assessor` z podziałem `prepare`/`evaluate`, `Provenance`. Nic ich jednak nie łączy. Brakuje tabel, magazynu RAW, pipeline'u, tras API i widoku. `WykopRepository` gubi dziś surowy JSON, a 7 poprawek z przeglądu F-02 czeka „Before S-02”.

## Desired End State

Admin w SPA (`/admin`) uruchamia przebieg dla zakresu dat. System najpierw ponawia resztki (`STORED` i nieudane oceny; maks. 3 nieudane próby, potem `DEAD`), potem pobiera dzień z Wykopu, zapisuje RAW osobno, posty bez duplikatów i każdą próbę oceny z pełnym pochodzeniem, a każdy krok przebiegu loguje w bazie. `RANGE` ocenia wyłącznie nowo zapisane posty; niedokończony post z `RETRY` czeka na kolejne żądanie lub ręczne ponowienie, więc nie dostaje dwóch podejść w jednym żądaniu. Użytkownik pod `/` widzi stronicowaną listę ocenionych postów, najnowsze na górze. Admin widzi przebiegi i listę nieudanych z możliwością ponowienia. Całość jest sprawdzona w PR env na prawdziwych danych.

## Key Decisions Made

Błędy techniczne per post są raportowane osobno jako `processing_errors` i dają status przebiegu `partial`; licznik i komunikat błędu widać w panelu admina. Nie tworzą oceny `FAILED` ani nie zużywają limitu trzech nieudanych prób LLM.

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Magazyn RAW | Osobna tabela `raw_payloads`, element gzip w `bytea`, za `RawPayloadStore` | `jsonb` nie jest bezstratny, TOAST nie kompresuje wpisów < 2 KB, a interfejs pozwala później przejść na bucket | Research / Plan |
| Model danych | post 1:1 RAW, post 1:N próby oceny, przebieg 1:N próby | Pełny łańcuch surowy post → wejście LLM → wyjście LLM i historia prób per przebieg | Plan |
| Silnik pipeline'u | Embabel `@Agent` (GOAP, deterministyczny) z cienkimi akcjami | Jawny graf kroków, przyszłe kroki ML jako nowe typy i akcje, logika testowalna bez frameworka | Plan |
| Trwałość | Własny stan w Postgresie (`posts.stage`, `pipeline_runs`, `pipeline_run_steps`) | Persistence Embabela zapisuje tylko stany WAITING i końcowe; wznawianie per post jest dokładniejsze | Plan |
| Uruchomienie | `POST /api/admin/pipeline-runs` + formularz w SPA, przebieg w tle w API | Wybór użytkownika; zmienia regułę „osobny serwis cron” (aktualizacja dokumentów) | Plan |
| Rodzaje przebiegu | `RANGE` (zakres dat), `RETRY` (resztki, zawsze przed `RANGE`), `POSTS` (zaznaczone id) | Resztki schodzą pierwsze, a każdy przebieg ma jeden cel i własny rekord | Plan |
| Nieudane oceny | Widoczne tylko dla admina, maks. 3 próby, potem `DEAD` | Narzędzie operatorskie; ograniczony koszt ponowień; widać, co poszło nie tak | Plan |
| Równoległość LLM | `AssessmentDispatcher` + executor, `PIPELINE_LLM_CONCURRENCY=4` | ~4× szybciej bez zmiany ceny za token; interfejs gotowy na Batch API | Plan |
| Kolejność list i ocen | Posty: `published_at DESC, id DESC`, `page`/`size` z `total`; oceny: `assessed_at DESC, id DESC`; przebiegi: `created_at DESC, id DESC` | Spójne z wejściem LLM, stabilne strony i jawne rozstrzygnięcie remisów | Plan |
| Dług F-02 | F7, F8a–F8f jako faza 1; N19 (prompt) poza S-02 | Dane spójne od pierwszego zapisu; zmiana promptu wymaga pomiaru | Plan |
| Weryfikacja | PR env na prawdziwym Wykopie/LLM; te same klucze Wykopu na produkcji | Całość sprawdzona na Railway przed merge; świadome odstępstwo od F-01 | Plan |

## Scope

**In scope:**
- Poprawki F7–F8f.
- Migracja V3, encje, `RawPayloadStore`, surowy element z `WykopRepository`.
- Pipeline Embabel z trwałym stanem, limitem prób, kolejką przebiegów i recovery po restarcie.
- Trasy: lista postów, przebiegi (admin), nieudane (admin).
- SPA: lista pod `/` i panel `/admin`.
- Dokumenty, `railway.ts`, CI, runbook produkcyjny.

**Out of scope:**
- Harmonogram i etykieta świeżości (S-05), dashboard historii (S-06), widok szczegółowy (S-03), filtrowanie (S-04).
- Etapy ML i Batch API (tylko furtki).
- Zmiana promptu N19, komentarze Wykopu, Railway Bucket, LangGraph4j, komenda CLI przebiegu, wznawianie „od kroku”.

## Architecture / Approach

```
SPA /admin ─POST→ PipelineRunService ─(kolejka, 1 aktywny)→ PipelineAgent (Embabel GOAP)
  RunRequest ─┬─ RangeTarget → FetchedBatch (Wykop) → StoredBatch (RAW + posts, dedup) ─┐
              └─ SelectionTarget (RETRY / POSTS) ───────────────────────────────────────┴→ PendingAssessments
  → AssessedBatch (AssessmentDispatcher, 4 wątki, zapis per post) → RunSummary
Każda akcja: serwis Springa + wpis w pipeline_run_steps.   SPA / ─GET /api/posts→ posty ASSESSED
```

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Dług F-02 | Spójna provenance i serializacja, bezpieczne błędy Wykopu, cache schematu | F8f może wymagać zmiany kontraktu §10 |
| 2. Model danych i RAW | V3, encje, `RawPayloadStore`, surowy element Wykopu, opcjonalne klucze | Mapowanie JSONB i enumów przez Hibernate 7 |
| 3. Pipeline | Agent GOAP, dispatcher, limit prób, kolejka, recovery | Planowanie Embabela po typach (niejednoznaczne typy wychodzą w runtime) |
| 4. REST API | Lista, przebiegi, nieudane, kody błędów | Kompletność rejestrów tras i walidacji |
| 5. SPA | Lista pod `/`, panel `/admin` | Czytelność zwięzłej oceny bez S-03 |
| 6. Docs + PR env | Reguły wdrożenia, sekrety Wykopu, weryfikacja na prawdziwych danych | Uśpienie API w trakcie przebiegu, koszt, 429 Wykopu |

**Prerequisites:** S-01 i F-02 zrobione (tak); sekrety GitHub `WYKOP_KEY`/`WYKOP_SECRET` przed fazą 6; gałąź `feature/first-assessed-wykop-feed` z `origin/main` i issue S-02 (#14).
**Estimated effort:** ~6–8 sesji, po jednej na fazę, fazy 3 i 6 mogą zająć po dwie.

## Open Risks & Assumptions

- Przed F-01 produkcja nie ma backupu ani limitów kosztów. Pierwszy przebieg produkcyjny jest świadomym ryzykiem (runbook po merge).
- Limity API Wykopu są nieznane (brak publicznej dokumentacji). Przy ręcznym przebiegu dnia (~7 zapytań) ryzyko jest małe, a 429 przerwie przebieg statusem `failed`.
- Założenie: trwający przebieg utrzymuje API obudzone (`sleepApplication: true`). Weryfikowane w PR env; jeśli zawiedzie, przebieg kończy się `INTERRUPTED` bez utraty danych.
- Te same klucze Wykopu w PR env i na produkcji to wspólny limit zapytań i odstępstwo od F-01 (dokumenty do aktualizacji w fazie 6).
- Domyślne parametry wybrane w planie, do zmiany na życzenie: zakres maks. 7 dni, `postIds` 1–100, `size` maks. 100, ponowienie `DEAD` odrzucane.

## Success Criteria (Summary)

- Admin uruchamia przebieg dnia z SPA w PR env, a użytkownik widzi ocenione posty z tego dnia chronologicznie, bez duplikatów.
- Ponowny przebieg tego samego dnia nie dodaje postów ani ponownych ocen dla `ASSESSED`. `RETRY` ponawia resztki i nieudane oceny, a `RANGE` ocenia wyłącznie nowe posty; każdy post dostaje najwyżej jedno podejście na żądanie. Nieudane oceny widać w panelu admina i da się je ponowić (maks. 3 nieudane próby).
- Każdy zapisany post ma osobny, odczytywalny RAW i pełną historię prób oceny z pochodzeniem.
