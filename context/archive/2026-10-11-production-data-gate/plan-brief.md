# F-01 production-data-gate — Plan Brief

> Full plan: `context/changes/production-data-gate/plan.md`
> Research: `context/changes/production-data-gate/research.md`

## What & Why

Produkcja od S-02 trzyma prawdziwe posty i oceny z Wykopu, a nie ma żadnej kopii bazy. Do tego 500-megabajtowy wolumen może zapełnić sam WAL (domyślny `max_wal_size` to 1 GB). F-01 jest bramką przed S-05 (codzienny automat). Daje kopię, która dowodnie się odtwarza, pułap kosztów i dokumenty zgodne z rzeczywistością.

## Starting Point

- Railway Hobby z limitami $5 / $10.
- Wolumen Postgresa 500 MB, z czego zajęte 192 MB.
- PITR wyłączone, żadnych kopii. Wbudowane kopie wolumenu Railway to funkcja Pro.
- Sekrety produkcji (LLM, Wykop) są już zapieczętowane.
- Klucz OpenRouter nie ma limitu, a 403 z wyczerpanego limitu klient traktowałby jako odrzucenie pojedynczego posta.

## Desired End State

- Codziennie o 02:00 UTC serwis cron w Railway robi `pg_dump`, odtwarza go w tymczasowej bazie, sprawdza i dopiero wtedy wysyła do bucketu `db-backups` z retencją 14 dziennych + 12 miesięcznych.
- Synology pobiera kopie (Cloud Sync) i pilnuje tej samej retencji, więc kopia jest też poza Railway.
- Wolumen ma 5 GB.
- Wyczerpany limit $10/mies. na kluczu OpenRouter zatrzymuje przebieg czysto, bez palenia prób postów.
- PRD, roadmapa i dokumenty wdrożeniowe opisują ten stan, łącznie z runbookiem odtworzenia awaryjnego.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Plan Railway | Hobby, bez Pro | Użytkownik nie chce płacić $20/mies. za kopie platformy. | Research (Follow-up) |
| Mechanizm kopii | `pg_dump` 18 (kopia logiczna) | Na Hobby to jedyna opcja bez zmiany planu lub platformy. | Research (Follow-up) |
| Kto robi dump | Serwis cron `db-backup` w Railway | Działa niezależnie od domowego sprzętu, a hasło do bazy nie opuszcza Railway (klucz SSH na NAS dawałby roota we wszystkich kontenerach). | Plan |
| Gdzie kopie | Bucket `db-backups` (`ams`) + Synology Cloud Sync | Bucket chroni przed wyczyszczeniem wolumenu i złą migracją, NAS przed utratą projektu lub konta. | Plan |
| Restore drill | Każdy dump odtwarzany w tymczasowej bazie przed wysyłką; jednorazowo test z aplikacją | Kopia, której nie da się odtworzyć, nigdy nie trafia do bucketu. | Research + Plan |
| Retencja | 14 dziennych + 12 miesięcznych (bucket i NAS) | Ochrona przed błędem zauważonym po dniach i przed powolnym psuciem danych przy kilku GB rocznie. | Plan |
| Wolumen | 5 GB (maksimum Hobby) | Zapas na WAL i lata danych; Railway liczy opłatę za zajęte miejsce. | Research (delegowane) |
| Limity Railway | Zostają $5 / $10 | Decyzja użytkownika; kwoty $15/$25 w dokumentach są nieaktualne. | Research (Follow-up) |
| Dane sesji | Wykluczone z dumpa | Identyfikatory aktywnych sesji pozwalają je przejąć; po odtworzeniu wystarczy ponowne logowanie. | Plan |
| PRD | Aktualizacja NFR, Non-Goal i OQ4 | Usuwa sprzeczność „backup robi platforma” z nowym stanem. | Plan |
| Limit LLM | $10/mies. na kluczu OpenRouter + 403 „Key limit exceeded” jako błąd konfiguracji | Jedyny twardy pułap kosztów LLM przed automatem S-05, z czystym zatrzymaniem przebiegu. | Plan |
| Bramka CI | Przepuszcza tworzenie nowych zasobów IaC | Inaczej nowy serwis zablokowałby CI na `main` i przez Wait for CI także wdrożenie API. | Plan |

## Scope

**In scope:**
- Zmiana rozmiaru wolumenu i pomiar bazy.
- `tools/db-backup/` (obraz, dump, sprawdzenie, retencja, test lokalny).
- Bucket i cron w `railway.ts`, zmiana bramki CI, weryfikacja w PR env (w tym próba odtworzenia awaryjnego).
- Klasyfikacja 403 w `OpenRouterClient` z testami i limit $10.
- PRD, roadmapa z obrazkiem, `infrastructure.md`, `deploy-plan.md`, `REPOSITORY_GUIDELINES.md`, README API, nowa lekcja.

**Out of scope:**
- Plan Pro, kopie i PITR Railway, Google Drive.
- Odtwarzanie do punktu w czasie (możliwa utrata danych do ~24 h), automatyczne odtwarzanie produkcji.
- Zmiana limitów Railway, alerty i panel (S-06), godzina przebiegu dziennego (S-05).

## Architecture / Approach

```
Railway (prywatna sieć)                                  dom
postgres ──pg_dump──▶ db-backup (cron 02:00 UTC)         Synology
                      ├─ pg_restore do tymczasowej bazy  ▲
                      ├─ kontrole (tabele, Flyway,        │ Cloud Sync (S3, tylko pobieranie)
                      │   liczby w [przed, po])          │ + nas-retention.sh (DSM)
                      └─ rclone ──▶ bucket db-backups ────┘
                                    daily/ (14), monthly/ (12)
```

Jeden obraz `postgres:18` + `rclone` działa w cronie Railway, w lokalnym teście i przy ręcznym teście z aplikacją (`restore-check.sh --serve`). PR env dostają własny cron i bucket, co pozwala przećwiczyć całość bez produkcji.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Wolumen 5 GB i pomiar | Zapas miejsca na produkcji, pomiary bazy | System plików nie urośnie mimo zmiany rozmiaru (sprawdzamy `df -h`) |
| 2. Narzędzie `tools/db-backup/` | Obraz i skrypty sprawdzone lokalnym testem | Ciche niedopasowanie wykluczenia sesji (sprawdzamy `pg_restore -l`) |
| 3. IaC, bramka CI, PR env | Cron + bucket w każdym środowisku, przećwiczone odtworzenie | Poluzowanie bramki CI musi dalej blokować zmianę źródła istniejących serwisów |
| 4. Limit OpenRouter | 403 z limitu jako błąd konfiguracji, limit $10 | Docs OpenRouter niespójne (402 albo 403), więc obsługujemy oba |
| 5. Dokumenty | PRD, roadmapa, infra, deploy-plan, runbook po merge'u, lekcja | Treść zmian w PRD wymaga akceptacji użytkownika |

**Prerequisites:**
- Dostęp operatora do panelu Railway (zmiana rozmiaru, powiadomienia) i OpenRouter (limit klucza).
- Docker lokalnie.
- Synology z Container Managerem i Cloud Sync (po merge'u).
- Gałąź `feature/production-data-gate` już istnieje.

**Estimated effort:** ~3–4 sesje. Faza 3 wymaga czekania na przebiegi cron w PR env, a zmiana PRD akceptacji w przeglądzie.

## Open Risks & Assumptions

- Pole „tworzenie zasobu” w JSON z `railway config plan` ma nazwę zgodną z założeniem (`resource.create`). Faza 3 sprawdza to na planie uruchomionym na próbę przed zmianą bramki.
- Nowe PR env po merge'u dostaną `db-backup`. Gałęzie bez tego serwisu w `railway.ts` trzeba zrebase'ować, inaczej bramka odrzuci jego usunięcie.
- Kroki produkcyjne (pierwszy przebieg na produkcji, test z aplikacją na danych produkcji, Cloud Sync na NAS) dzieją się po merge'u i są śledzone w issue F-01, nie w Progress.
- Ochrona to kopia raz dziennie: awaria między dumpami traci do ~24 h danych. Posty da się częściowo pobrać ponownie z Wykopu, a ponowna ocena kosztuje LLM.

## Success Criteria (Summary)

- Codzienny przebieg `db-backup` kończy się sukcesem tylko wtedy, gdy dump odtworzył się do zdrowej bazy. Porażka daje e-mail.
- Test z aplikacją na odtworzonej bazie przechodzi: `migrate` bez zmian, logowanie admina, ta sama liczba postów. Runbook odtworzenia awaryjnego jest przećwiczony w PR env.
- Wyczerpany limit OpenRouter zatrzymuje przebieg bez zużycia prób postów, a dokumenty (z PRD) nie twierdzą już, że backup robi platforma albo że go nie ma.
