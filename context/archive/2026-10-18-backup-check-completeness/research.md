---
date: 2026-10-18T12:19:35+02:00
researcher: Claude (Opus 5.5) z kwmacioszek
git_commit: c7db08c164acd537c5a5e155800cc15c9a656a52
branch: feature/backup-check-completeness
repository: szczur-radar
topic: "S-15 backup-check-completeness: co musi wiedzieć sprawdzenie kopii bazy, żeby objąć wszystkie tabele z migracji i odrzucić kopię podejrzanie pustej bazy"
tags: [research, db-backup, restore-check, railway, flyway, F-01, S-15]
status: complete
last_updated: 2026-10-18
last_updated_by: Claude (Opus 5.5)
---

# Research: S-15 — kopia bazy sprawdzana w całości

**Date**: 2026-10-18T12:19:35+02:00
**Researcher**: Claude (Opus 5.5) z kwmacioszek
**Git Commit**: c7db08c164acd537c5a5e155800cc15c9a656a52
**Branch**: feature/backup-check-completeness
**Repository**: szczur-radar

## Research Question

Roadmapa S-15 (`context/foundation/roadmap.md:236-246`): „codzienne sprawdzenie kopii bazy zna wszystkie tabele z migracji (także `activation_tokens` i `notifications`) i odrzuca kopię bazy, która jest podejrzanie pusta, zanim wyprze dobre kopie z retencji”. Źródło: audyt C9 (`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:98`).

Pytania badawcze:
1. Których tabel z migracji nie znają listy w `backup.sh` i `restore-check.sh`?
2. Dla których tabel działa reguła liczności [przed, po], czyli czy mogą tracić wiersze?
3. Co już zdecydowano o tych listach i o dolnej granicy liczności?
4. Jakie ograniczenia narzucają weryfikacja w PR env, IaC Railway i runbook odtworzenia?

## Summary

- **Luka w listach jest potwierdzona.** Migracje V1–V9 tworzą 10 tabel, a Flyway tworzy 11. (`flyway_schema_history`). `REQUIRED_TABLES` w `restore-check.sh:12-13` ma 9 z nich: brak `activation_tokens` (V7) i `notifications` (V9). `COUNTED_TABLES` w `backup.sh:21` ma 5 tabel.
- **Reguła [przed, po] działa tylko dla tabel, które rosną.** Kod aplikacji, komendy CLI i Spring Session usuwają wiersze z dokładnie 3 tabel (przeszukano `szczur-radar-api/src/main` i migracje V1–V9): `activation_tokens`, `spring_session` i `spring_session_attributes`. Pozostałe 8 tabel nie ma żadnej sprawdzonej ścieżki usuwania. Wśród nich są `pipeline_run_steps` i `notifications`, których dziś nie liczymy.
- **Pominięcie `activation_tokens` było świadomą decyzją** (`context/archive/2026-10-12-cli-user-onboarding/plan.md:60`). Tabela ma ścieżkę usuwania, a dodanie jej do `REQUIRED_TABLES` złamałoby sprawdzanie starszych dumpów. O `notifications` w kontekście backupu nigdzie nie zdecydowano.
- **Zgodność wstecz to realne ograniczenie.** Najstarsza kopia produkcyjna jest z Flyway V5 (`deploy-plan.md:388`). Kopia miesięczna 2026-10 to pierwszy udany dump miesiąca (`backup.sh:208-217`), więc powstała przed V7 i zostaje w retencji 12 miesięcy. Runbook odtworzenia każe uruchomić `restore-check.sh` na pobranym dumpie (`deploy-plan.md:302-308`). Płaska lista wymaganych tabel odrzuciłaby więc dumpy sprzed V7 i V9.
- **Dolnej granicy liczności nigdy nie omawiano ani nie odrzucono.** Przeszukano archiwum F-01, deploy-plan, lessons i PRD. Jedyna istniejąca kontrola, `backup.sh:171`, sprawdza, że TOC dumpa ma wpis `TABLE DATA public posts`. Taki wpis istnieje także dla pustej tabeli.
- **Weryfikacja w PR env napotka 3 przeszkody:**
  - PR env nie ma danych z produkcji. Przy PR zmieniającym tylko `tools/db-backup` może nie mieć nawet bazy (`deploy-plan.md:343-346`).
  - `BACKUP_IN_ANY_ENVIRONMENT` nie jest zadeklarowane `preserve()` (`.railway/railway.ts:229-243`), więc ręcznie ustawiona wartość wywoła odmowę bramki IaC (audyt C7).
  - Cron `db-backup` wdraża się bez Wait for CI (audyt C5).

## Detailed Findings

### 1. Tabele: migracje kontra listy narzędzia

| Tabela | Migracja | `REQUIRED_TABLES` (`restore-check.sh:12-13`) | `COUNTED_TABLES` (`backup.sh:21`) | Usuwanie wierszy |
|---|---|---|---|---|
| `users` | V1:3 (+ wiersz systemowy V6:11) | tak | tak | brak |
| `spring_session` | V2:4 | tak | nie (dane wykluczone z dumpa, `backup.sh:18`) | tak |
| `spring_session_attributes` | V2:19 | tak | nie (j.w.) | tak (kaskada) |
| `pipeline_runs` | V3:7 | tak | tak | brak |
| `pipeline_run_steps` | V3:43 | tak | **nie** | brak |
| `raw_payloads` | V3:65 | tak | tak | brak |
| `posts` | V3:84 | tak | tak | brak |
| `assessments` | V3:130 | tak | tak | brak |
| `activation_tokens` | V7:5 | **nie** | nie | **tak** |
| `notifications` | V9:6 | **nie** | **nie** | brak |
| `flyway_schema_history` | Flyway | tak | nie | brak w kodzie |

- W żadnej migracji V1–V9 nie ma `DROP TABLE` ani `ALTER TABLE … RENAME`. Szukano `CREATE TABLE|DROP TABLE|RENAME TO` w `szczur-radar-api/src/main/resources/db/migration/*.sql`.
- Jedyna migracja wstawiająca wiersze to V6 (`V6__users_login_enabled_scheduler.sql:11-12`, konto systemowe scheduler). Po samym `migrate` `users` ma więc ≥ 1 wiersz, a każda pozostała tabela aplikacji ma 0.
- `pg_dump` zrzuca wszystkie tabele, w tym V7 i V9, i odtwarza je. Brakuje jedynie kontroli tych tabel w `restore-check.sh` (`context/map/repo-map.md:108`).

### 2. Ścieżki zapisu: które tabele mogą tracić wiersze

Przeszukano `szczur-radar-api/src/main` (Java, SQL, properties) wzorcami `delete|truncate|removeIf|.remove(`, `insert into|update|on conflict|save|persist` oraz adnotacjami kaskad JPA. Encje nie mają relacji JPA ani kaskad. Rzeczywiste `DELETE` w bazie są tylko w 2 miejscach:
- `ActivationLinks.java:168`: `tokens.delete(...)` usuwa wiersz `activation_tokens`, gdy link zostaje użyty. Sprawdzone w kodzie.
- `ActivationLinks.java:196`: `DELETE FROM spring_session WHERE principal_name = :principal` przy reset-password i aktywacji, także z CLI (`cli/ResetPasswordCommand.java:43`).

Inne sposoby znikania wierszy:
- **Sesje.** Wylogowanie (`AuthController.java:115`, `session.invalidate()`) i zadanie czyszczące Spring Session. To zadanie ma domyślny cron `0 * * * * *`, którego `application.properties` nie nadpisuje. `spring_session_attributes` traci wiersze kaskadą (V2:24).
- **`activation_tokens`.** Nowy link to upsert `ON CONFLICT (user_id) DO UPDATE` (`ActivationTokenRepository.java:45-48`, sprawdzone), a nie usunięcie z ponownym wstawieniem. Liczba wierszy maleje tylko przy aktywacji. Kaskada z `users` (V7:12) jest martwa, bo `users` nie ma ścieżki usuwania. Usuwanie kont jest celem nierealizowanym (`prd.md:293`).
- **`notifications`.** Tylko `INSERT … ON CONFLICT … DO NOTHING` (`NotificationRepository.java:51-53`, sprawdzone) oraz `UPDATE read_at` (`:64`, `:75`). Kaskada z `users` (V9:15) jest martwa z tego samego powodu. FK `run_id` (V9:16) nie ma kaskady.
- **`pipeline_run_steps`.** Tylko INSERT (`RunRecorder.java:230-233`) i UPDATE (`:248`, `:273`). FK do `pipeline_runs` nie ma kaskady (V3:54).
- **`flyway_schema_history`.** `MigrateCommand` nie woła `repair` ani `clean`. Wiersze mogłoby usunąć tylko ręczne `flyway repair`.

Wniosek dla reguły [przed, po], którą `restore-check.sh:108-116` stosuje do każdej tabeli z manifestu:
- **Można dodać:** `pipeline_run_steps` i `notifications`. W sprawdzonym kodzie tylko przybywa im wierszy.
- **Nie można dodać:** `activation_tokens`. `spring_session*` jest wykluczone z dumpa.
- Kontrola liczności iteruje po kluczach `counts_before` z manifestu (`restore-check.sh:108`). Dodanie tabel do `COUNTED_TABLES` nie psuje więc sprawdzania starszych manifestów: starszy manifest po prostu nie ma nowych kluczy.

### 3. Dotychczasowe decyzje i to, czego nie zdecydowano

Decyzje zapisane wprost:
- `COUNTED_TABLES` to tabele bez ścieżki usuwania. Uzasadnienie: bezterminowa retencja z PRD (`context/archive/2026-10-11-production-data-gate/plan.md:101`; `backup.sh:19-20`).
- `REQUIRED_TABLES` to 9 tabel istniejących przy V5 (`production-data-gate/plan.md:219-222`).
- Zmiana `cli-user-onboarding` celowo nie dotknęła `tools/db-backup/`: „nowa tabela ma ścieżkę usuwania. Dodanie jej do `REQUIRED_TABLES` złamałoby też sprawdzanie starszych dumpów” (`context/archive/2026-10-12-cli-user-onboarding/plan.md:60`, sprawdzone). To samo ustalenie jest w jej `research.md:133-134`.
- Zmiana `daily-scheduled-run-freshness` (V6) zostawiła `restore-check.sh` i `COUNTED_TABLES` bez zmian (`context/archive/2026-10-12-daily-scheduled-run-freshness/plan.md:598`).
- Dryf schematu łapie job CI `db-backup-test`, wybrany w impl-review F3 (`production-data-gate/reviews/impl-review.md:70-91`).
  - Usunięcie albo zmiana nazwy tabeli z listy wywala test, bo `test.sh:93-105` stosuje wszystkie migracje.
  - **Nowej tabeli, której nie ma na liście, test nie wykrywa.** Właśnie tak przeszły V7 i V9.
- Kopie robimy tylko na produkcji. Na innych środowiskach przebieg uruchamia `BACKUP_IN_ANY_ENVIRONMENT=true` (impl-review F4, `backup.sh:26-34`).

Czego nie znaleziono w `production-data-gate` (research, plan, plan-brief, impl-review), `deploy-plan.md` F-01, `lessons.md`, PRD ani pozostałym archiwum:
- dolnej granicy liczności;
- progu;
- porównania z poprzednim manifestem;
- kroku runbooka na spadek liczności po odtworzeniu starszego dumpa.

Wymóg pochodzi wyłącznie z audytu C9 i roadmapy S-15.

`notifications` (V9) nie ma żadnej decyzji w kontekście backupu. W archiwum S-06 (`context/archive/2026-10-15-pipeline-run-history-dashboard/`) nie ma słów backup, `pg_dump` ani restore.

### 4. Zgodność wstecz ze starszymi dumpami

- Pierwszy dump produkcyjny to `szczur-radar-20261012T124334Z.dump` z Flyway V5 (`deploy-plan.md:388`).
- V7 trafiło na produkcję 2026-10-13 (`deploy-plan.md:643`), a V8 i V9 z `5b6eabb` (`deploy-plan.md:809`).
- Kopia miesięczna to pierwszy udany dump miesiąca UTC (`backup.sh:208-217`), trzymany przez `BACKUP_KEEP_MONTHLY=12` (`.railway/railway.ts:242`). Kopia `monthly/…-2026-10` pochodzi więc sprzed V7.
- Runbook odtworzenia, krok 2, uruchamia `restore-check.sh <dump> <manifest>` lokalnie na pobranym dumpie (`deploy-plan.md:302-308`).
- Wniosek: kontrola wymaganych tabel musi zależeć od wersji Flyway odtworzonego dumpa albo od tego, co zapisał manifest. Inaczej odrzuci prawdziwe starsze kopie. To ograniczenie, a nie wybór.

### 5. Dolna granica: co dziś przechodzi i jakie dane widzi przebieg

- `backup.sh:171` wymaga wpisu `TABLE DATA public posts` w TOC. `pg_dump` zapisuje ten wpis także dla pustej tabeli, więc dump wyczyszczonej bazy przechodzi.
- `restore-check.sh:114` porównuje liczności tylko z przedziałem [przed, po] tego samego przebiegu. Liczniki 0 i 0 dają przedział [0, 0], więc pusta baza przechodzi.
- Skutek opisany w audycie C9:
  - kopia pustej bazy jest wysyłana do bucketu;
  - 14 dziennych kopii i lustro NAS (`deploy-plan.md:409-421`) wypierają w ciągu 14 dni dobre kopie;
  - zostaje Google Drive (bez retencji) i kopie miesięczne.
- Przebieg zakończony porażką kończy się statusem `CRASHED`. Dociera do operatora tylko dzięki niestandardowej regule powiadomień konta „deployment crashed” (`deploy-plan.md:283-285`). Porażka przed wysyłką (`backup.sh:197-198`, „nothing uploaded”) już dziś nie dotyka retencji, bo `prune` działa po wysyłce (`backup.sh:219-220`).
- Liczności z produkcji (wartości orientacyjne, rosną):
  - 2026-10-11/12: `users` 1, `pipeline_runs` 4, `posts` 282, `assessments` 55, `raw_payloads` 282 (`deploy-plan.md:267,390`);
  - PR env pr-44 po jednodniowym przebiegu: `posts` 39 (`deploy-plan.md:355`).
- Po celowym odtworzeniu starszego dumpa na produkcji kolejna kopia będzie miała **mniej** wierszy niż ostatni manifest w buckecie. To prawidłowy stan. Runbook (`deploy-plan.md:297-336`) nie ma dziś kroku dotyczącego następnej nocnej kopii.

### 6. PR env, IaC i CI

- **Dane w PR env.**
  - PR env ma własną bazę bez danych produkcji. „PR env is a copy of production” w `railway.ts:210` i `backup.sh:26-27` dotyczy usług i konfiguracji.
  - Przy PR, który nie zmienia API ani weba, Railway wdrożył tylko `db-backup`, bez wolumenu Postgresa (`deploy-plan.md:343-346`): „Expect the same in a PR env of any PR that changes neither the API nor the web.”
  - W pr-44 kopia przeszła dopiero po ręcznym dołączeniu wolumenu, redeployu `postgres`, API i weba oraz po jednodniowym przebiegu pipeline'u (`deploy-plan.md:350-356`).
  - Świeża baza po `migrate` ma 0 postów, więc każda dolna granica oparta na `posts` odrzuci tam kopię, dopóki nie przejdzie przebieg pipeline'u.
- **Zmienne `db-backup`** (`.railway/railway.ts:229-243`, sprawdzone):
  - zadeklarowane są tylko referencje `PG*` i `S3_*` oraz literały `BACKUP_KEEP_DAILY` i `BACKUP_KEEP_MONTHLY`;
  - nie ma żadnego `preserve()`;
  - `BACKUP_IN_ANY_ENVIRONMENT`, `BACKUP_TIMEOUT`, `BACKUP_REMOTE` i `S3_FORCE_PATH_STYLE` nie są zadeklarowane.
- **Bramka IaC** (`ci.yml:353-354`) odrzuca zmiany `destructive`/`resource.delete`. Zmienna ustawiona ręcznie bez deklaracji daje w planie usunięcie zmiennej, job `railway-config` jest czerwony, a na `main` Wait for CI zatrzymuje deploye API i weba (audyt C7; `deploy-plan.md:942`).
  - Plan dla PR env używa `railway.ts` z gałęzi PR (`ci.yml:227,230`), więc `preserve()` dodane w tym PR chroni ręczną wartość w jego PR env.
  - Wzorzec do skopiowania: `TRIGGER_IN_ANY_ENVIRONMENT: preserve()` (`.railway/railway.ts:273`, sprawdzone). Runbook usuwania nadpisania jest w `tools/pipeline-trigger/README.md:86,125,127`. Po usunięciu zmiennej cron trzeba przedeployować (`deploy-plan.md:502`).
  - Każda nowa zmienna nadpisania wprowadzona przez S-15 potrzebuje tej samej deklaracji.
  - S-14 (`safe-deploy-gate`) obejmuje C7 ogólnie i dotyka tego samego pliku. Oba wycinki mogą iść równolegle (`roadmap.md:241`), więc grozi konflikt w `railway.ts`.
- **Job CI** `db-backup-test` (`ci.yml:180-191`) uruchamia `tools/db-backup/test.sh` bez filtrów ścieżek i bez `timeout-minutes`.
  - Komentarz w `railway.ts:221-222` i zdanie w `deploy-plan.md:347` („CI does not test tools/db-backup anyway”) są nieaktualne. Poprawka należy do S-19 albo do tego wycinka, jeśli i tak dotyka tych miejsc.
- **Brak Wait for CI dla crona** (audyt C5, `railway.ts:218-222`). Po merge'u `db-backup` buduje się z `main` nawet przy czerwonym `db-backup-test`. Test musi więc przejść w PR, bo nic nie zatrzyma wdrożenia crona.

### 7. Test lokalny (`tools/db-backup/test.sh`)

- Stosuje wszystkie migracje w kolejności numerycznej z ręcznie zapisaną `flyway_schema_history` (`test.sh:91-105`).
- Wstawia wiersze do `users`, `pipeline_runs`, `pipeline_run_steps`, `raw_payloads`, `posts` (40), `assessments` i sesji (`test.sh:107-135`). **Nie wstawia** wierszy do `activation_tokens` ani `notifications`.
- Sprawdza manifest przez `jq` na `counts_*.posts` i `counts_after.users` (`test.sh:153-157`).
- Krok 3 usuwa `pipeline_run_steps` i oczekuje komunikatu `tables: missing pipeline_run_steps` (`test.sh:223-232`). To wzorzec dla testów nowych kontroli.
- Nie ma dziś przypadku z pustą bazą, przypadku ze spadkiem liczności ani dumpa z wcześniejszą wersją Flyway.

## Code References

- `tools/db-backup/restore-check.sh:12-13`: `REQUIRED_TABLES` (9 tabel, bez V7 i V9).
- `tools/db-backup/restore-check.sh:92-95`: kontrola obecności tabel (`to_regclass`).
- `tools/db-backup/restore-check.sh:104-117`: wersja Flyway i liczności [przed, po] z manifestu.
- `tools/db-backup/backup.sh:16-21`: `EXCLUDED_DATA`, `COUNTED_TABLES` i uzasadnienie „no delete path”.
- `tools/db-backup/backup.sh:84-91`: `count_rows` (JSON z licznościami).
- `tools/db-backup/backup.sh:104-118`: `list_files` (dokładne nazwy, posortowane). Można jej użyć do znalezienia poprzedniego manifestu.
- `tools/db-backup/backup.sh:169-174`: kontrole TOC (`posts` ma dane, sesje ich nie mają).
- `tools/db-backup/backup.sh:178-192`: manifest (`format: 1`).
- `tools/db-backup/backup.sh:196-220`: restore check, potem wysyłka, kopia miesięczna i retencja.
- `tools/db-backup/test.sh:107-135,141-162,203-232`: dane testowe, sprawdzenie manifestu, przypadki porażek.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationLinks.java:168,196`: jedyne `DELETE` w kodzie aplikacji.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationTokenRepository.java:45-48`: upsert tokenu.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/NotificationRepository.java:51-53,64,75`: `notifications` tylko INSERT `DO NOTHING` i UPDATE `read_at`.
- `.railway/railway.ts:223-244`: serwis `db-backup` bez `preserve()`; `:273`: wzorzec `TRIGGER_IN_ANY_ENVIRONMENT: preserve()`.
- `.github/workflows/ci.yml:180-191`: `db-backup-test`; `:353-370`: reguła bramki.
- `context/deployment/deploy-plan.md:297-336`: runbook odtworzenia; `:338-366`: weryfikacja F-01 w PR env.

## Architecture Insights

- **Dwie kontrole o różnym celu.**
  - Przedział [przed, po] dowodzi spójności migawki: odtworzony dump zgadza się z bazą w chwili zrzutu.
  - Nie mówi nic o wiarygodności danych. Pusta baza jest spójna sama ze sobą.
  - Dolna granica to osobna, nowa kontrola. Przedział jej nie zastąpi.
- **Klasyfikacja tabel jest potrzebna niezależnie od sposobu wykrywania tabel.**
  - Nawet gdyby listę tabel brać dynamicznie z katalogu bazy źródłowej i zapisywać w manifeście, każda tabela musi zostać zaklasyfikowana: wymagana, liczona [przed, po] albo tylko obecna (`activation_tokens`, sesje).
  - Ta klasyfikacja wymaga wiedzy o ścieżkach zapisu, więc nie da się jej wyprowadzić automatycznie.
  - V7 i V9 przeszły niezauważone, bo `test.sh` nie porównuje tabel w zmigrowanej bazie z klasyfikacją narzędzia.
- **Miejsce kontroli przed retencją.**
  - Tylko `backup.sh` ma dostęp do bucketu, a więc do poprzednich manifestów.
  - `restore-check.sh` działa też lokalnie w runbooku, bez bucketu.
  - Kontrola „podejrzanie pusta” musi zakończyć przebieg przed wysyłką (`backup.sh:204`), żeby nie ruszyć retencji.
  - Kontrola zależna od wersji (wymagane tabele) należy do `restore-check.sh`, które zna wersję Flyway odtworzonej bazy.
- **Manifest ma pole `format: 1`** (`backup.sh:189`). Nowe pole (np. lista tabel) można dodać zgodnie wstecz. `restore-check.sh` czyta przez `jq` tylko `flyway_version` i `counts_*`.

## Historical Context (from prior changes)

- `context/archive/2026-10-11-production-data-gate/plan.md:101,219-222`: uzasadnienie list i reguły [przed, po]. **Supported**: twierdzenie „none of these tables has a delete path” jest nadal prawdziwe dla 5 liczonych tabel (§2).
- `context/archive/2026-10-11-production-data-gate/reviews/impl-review.md:70-91` (F3): CI łapie dryf schematu. **Partial**: łapie usunięcie i zmianę nazwy, ale nie nową tabelę (§3).
- `context/archive/2026-10-12-cli-user-onboarding/plan.md:60`: `activation_tokens` poza listami. **Supported**: ścieżka usuwania istnieje (`ActivationLinks.java:168`), a dumpy sprzed V7 nadal są w retencji (§4). Wynika z tego, że tabela może wejść do kontroli obecności tylko warunkowo względem wersji, a do liczności nigdy.
- `context/archive/2026-10-12-daily-scheduled-run-freshness/plan.md:598`: listy bez zmian przy V6. **Supported** (V6 nie dodaje tabeli).
- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:96` (C7) i `:98` (C9):
  - C9 **supported** w obu częściach: brak tabel na listach i brak dolnej granicy;
  - C7 **supported** dla `db-backup`: brak `preserve()`.
  - Zdanie z C9 „usunięcie albo zmiana nazwy tabeli z listy wywala `db-backup-test`” też jest prawdziwe (`test.sh:93-105`, `restore-check.sh:92-95`).
- `context/foundation/lessons.md:88-97` (Railway Hobby): kopia jest ważna dopiero po pełnym odtworzeniu z kontrolami tabel, Flyway i liczności. S-15 rozszerza tę regułę.

## Related Research

- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md`: audyt C9 i C7.
- `context/archive/2026-10-11-production-data-gate/research.md`: projekt narzędzia F-01.
- `context/map/repo-map.md:77,108,144,189`: strefa ryzyka `db-backup` i pytanie „zamierzone czy dryf?”. Na to pytanie odpowiada §3: `activation_tokens` pominięto celowo, `notifications` przez dryf.

## Open Questions

Nie ma luk dowodowych. Do rozstrzygnięcia w `/10x-plan` zostają wybory projektowe i produktowe:

1. **Definicja „podejrzanie pustej”.** Możliwe podejścia:
   - bezwzględne minimum, np. `posts` ≥ N;
   - brak spadku względem poprzedniego manifestu w buckecie dla tabel, które tylko rosną (`now.counts_before ≥ prev.counts_after`);
   - oba naraz.
   Od wyboru zależy, czy wykrywamy tylko wyczyszczoną bazę, czy także częściową utratę danych.
2. **Wyjście awaryjne po celowym odtworzeniu** (§5) i w PR env bez postów (§6). Do ustalenia:
   - nazwa i semantyka zmiennej nadpisania, z deklaracją `preserve()`;
   - nowy krok w runbooku odtworzenia.
3. **Źródło listy tabel.** Dwie opcje:
   - statyczna klasyfikacja w skryptach, z wersją Flyway, od której tabela jest wymagana;
   - lista z katalogu bazy źródłowej w manifeście.
   Obie potrzebują strażnika w `test.sh`, który porówna tabele zmigrowanej bazy z klasyfikacją, żeby następna tabela nie przeszła po cichu.
4. **Koordynacja z S-14** przy edycji `.railway/railway.ts` (`preserve()` dla zmiennych `BACKUP_*`).
5. **Nieaktualne zdania „CI does not test tools/db-backup”** (`railway.ts:221-222`, `deploy-plan.md:347`): poprawić tutaj czy zostawić S-19.
