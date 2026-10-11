---
date: 2026-10-11T15:49:19+0200
researcher: Claude (Opus 5.5) for kwmacioszek
git_commit: 771b451c3a20a1fc3be4fb69752d3f5087b3b289
branch: feature/production-data-gate
repository: szczur-radar
topic: "F-01 production-data-gate — co jest już zrobione, czego brakuje i jak bezpiecznie domknąć bramkę produkcyjnych danych (backup, restore drill, limity kosztów, sekrety)"
tags: [research, infra, railway, postgres, backup, restore, cost-limits, sealed-variables, openrouter]
status: complete
last_updated: 2026-10-11
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Follow-up: decyzje użytkownika (Hobby, pg_dump, restore drill przez pg_dump, limity $5/$10 bez zmian), szacunek rozmiaru wolumenu, flaga sprzeczności z PRD, wstępne opcje miejsca przechowywania dumpów dla /10x-plan. Luka 'Hobby czy Pro' zamknięta odpowiedzią użytkownika."
---

# Research: F-01 production-data-gate

**Date**: 2026-10-11T15:49:19+0200
**Researcher**: Claude (Opus 5.5) for kwmacioszek
**Git Commit**: 771b451c3a20a1fc3be4fb69752d3f5087b3b289 (= `origin/main` w chwili badania)
**Branch**: feature/production-data-gate
**Repository**: szczur-radar

Pierwsza wersja miała status `partial`, bo nie dało się odczytać **typu planu Railway**. CLI i MCP Railway go nie podają, a zapytanie do API tokenem sesji CLI zablokował tryb auto. **Zamknięte 2026-10-11:** użytkownik potwierdził plan **Hobby**. Decyzje i szacunki są w sekcji „Follow-up 2026-10-11” na końcu dokumentu i zastępują wnioski Summary 4 oraz Open Questions 1–4.

Stan Railway poniżej to odczyty z 2026-10-11 (CLI `railway` 5.64.2 i MCP Railway, tylko odczyt). Nic nie zostało zmienione.

## Research Question

Fundament F-01 z `context/foundation/roadmap.md:99-112`: „produkcja jest gotowa na zbieranie prawdziwych danych: aktywna subskrypcja, ustawione limity kosztów, włączona dzienna kopia wolumenu Postgres (zamiast PITR…), restore drill zakończony zdrowym, odpytywalnym odtworzeniem, sekret LLM produkcji zapieczętowany i niedostępny w środowiskach PR; klucze Wykopu zapieczętowane na produkcji…”. Zamiar zmiany: `context/changes/production-data-gate/change.md:12`.

Pytania:
1. Czego F-01 naprawdę wymaga (PRD, roadmapa, `deploy-plan.md`), a co jest już nieaktualne?
2. Co z tego jest już zrobione na produkcji (stan na żywo)?
3. Co Railway faktycznie oferuje: dostępność kopii wolumenu w planach, harmonogramy, semantyka odtwarzania, limity kosztów, zapieczętowane zmienne?
4. Jak przeprowadzić restore drill bez ryzyka dla danych produkcji i czym zweryfikować odtworzoną bazę?
5. Czy „limity kosztów” obejmują też koszt LLM (OpenRouter) i jak zachowa się wtedy klient LLM?

## Summary

1. **Zakres:** PRD wymaga tylko tego, by bezpieczeństwo danych zapewniała platforma, bez własnej logiki backupu w aplikacji (`prd.md:252`, `prd.md:287`; Open Question 4 nierozstrzygnięte, `prd.md:299`). Kwoty limitów ($15 alert / $25 twardy) pochodzą z `deploy-plan.md:14,272`, a nie z PRD. Guardrail kosztów LLM to tylko „minimalizować” (`prd.md:49`). Pułap budżetu LLM jest otwarty jako roadmap OQ5 (`roadmap.md:261`). S-05 potrzebuje od F-01 dziennej kopii, czystego testu odtwarzania i limitów kosztów (`roadmap.md:260`).
2. **Już zrobione (odczyt 2026-10-11):**
   - Na produkcji `szczur-radar-api` zwraca `JWT_SECRET`, `LLM_API_KEY`, `WYKOP_KEY` i `WYKOP_SECRET` bez wartości (`null` w `railway variable list --json`). Tak CLI pokazuje zmienne zapieczętowane.
   - PR env `szczur-radar-pr-42` ma własne ustawienia LLM z CI (inny dostawca) i klucze Wykopu z sekretów GitHub. To zgodne z decyzją S-02.
   - Limity kosztów workspace są ustawione na **soft $5 / hard $10**, a nie na planowane $15/$25.
3. **Nie zrobione:**
   - Postgres na produkcji nie ma harmonogramu kopii (`schedule list` → `[]`) ani żadnej kopii (`backup list` → `[]`). PITR jest wyłączone (`enabled: false`, `bucketWired: false`).
   - Wolumen ma nadal **500 MB** (rozmiar z trialu), z czego zajęte jest 191,9 MB.
   - Restore drill się nie odbył.
4. **Kluczowe ograniczenie zewnętrzne: kopie wolumenu są tylko w planie Pro.**
   - Tabela porównawcza na `railway.com/pricing` w wierszu „Built-in database and volume backups” ma znaczniki tylko w kolumnach Pro i Enterprise. Odczytałem to z HTML strony 2026-10-11: dwie pierwsze komórki puste, dwie ostatnie z ikoną ✓.
   - Odpowiedź z konta „Railway” na Central Station (2026-09-08) mówi to samo.
   - Na Hobby F-01 w obecnym brzmieniu jest niewykonalne. Do decyzji: Pro ($20/mies.), backup logiczny (sprzeczny z Non-Goal PRD `prd.md:287`, wymaga flagi) albo powrót do Render (risk #1).
5. **Odtworzenie działa tylko w tej samej usłudze i środowisku.**
   - Restore podmienia wolumen: nowy wolumen z datą kopii jest montowany w miejscu starego, stary zostaje odmontowany, a całość wymaga Deploy.
   - Restore drill na produkcji oznacza więc przestój i utratę zapisów sprzed restore, chyba że po drillu nastąpi powrót na stary wolumen.
   - Bezpieczne warianty opisuje sekcja D.
6. **Przesłanka „produkcja nie ma prawdziwych danych” jest nieaktualna.** Decyzja o braku backupu z 2026-10-06 opierała się na pustej bazie (`deploy-plan.md:116`). Od 2026-10-09/10 produkcja trzyma prawdziwe posty i oceny z S-02 (issue #14, komentarz 2026-10-10; issue #39), nadal bez żadnej kopii.
7. **Twardy limit wyłącza wszystkie usługi workspace, także Postgres i PR env.** Storage jest dalej naliczany (docs cost-control, odpowiedź Railway 2026-09-28).
8. **LLM:** OpenRouter pozwala ustawić limit kredytu na klucz. Dokumentacja OpenRouter podaje jednak dwa różne kody po wyczerpaniu limitu: 403 albo 402. Klient traktuje je różnie (`TransportException.java:79`):
   - 402 to `CONFIGURATION`: przebieg staje i nie zużywa prób.
   - 403 to `REJECTED`: każdy post traci jedną z trzech prób.

   Jeśli F-01 ma ustawić limit OpenRouter, plan musi to rozstrzygnąć.

## Detailed Findings

### A. Wymagania F-01 i ich źródła

- PRD, NFR: „Dane (posty, konta) są przechowywane bezterminowo w MVP … Bezpieczeństwo przed utratą danych jest odpowiedzialnością platformy hostingowej, nie logiki aplikacji.” (`context/foundation/prd.md:252`).
- PRD, Non-Goal: „Custom logika archiwizacji/backupu danych — … odpowiedzialność platformy hostingowej, nie kodu aplikacji.” (`prd.md:287`).
- PRD Open Question 4 („Backup/retencja na docelowej platformie…”) nie jest oznaczone jako rozstrzygnięte. OQ2 ma znacznik ROZSTRZYGNIĘTE, a OQ4 go nie ma (`prd.md:297-299`).
- Guardrail kosztów: „Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe.” (`prd.md:49`). PRD nie podaje pułapu ani limitu po stronie platformy czy dostawcy. Kryteria sukcesu PRD (`prd.md:33-50`) nie wspominają o backupie ani limitach.
- Roadmapa:
  - Wpis F-01: `roadmap.md:99-112`.
  - OQ4: „po subskrypcji dzienna kopia wolumenu Railway (Daily, 6 dni), czysty test odtwarzania i limity kosztów (`deploy-plan.md`)… Block: F-01 → S-05” (`roadmap.md:260`).
  - OQ5, budżet LLM: otwarte, nie blokuje (`roadmap.md:261`).
  - Sekcja S-05 (`roadmap.md:198-212`) wymienia F-01 tylko jako warunek wstępny (`:203`).
- `deploy-plan.md`:
  - Bramka „przed fazą 6”: `deploy-plan.md:17`, `:347`.
  - Faza 6 jest oznaczona jako zastąpiona przez S-02 (`:337`). Jedynym aktualnym sformułowaniem bramki pozostaje „przed S-05” w roadmapie (`roadmap.md:260`).
  - Przepis na backup po subskrypcji: Daily, ręczna kopia, restore i weryfikacja zapytaniem (`deploy-plan.md:117`).

### B. Stan na żywo (Railway, 2026-10-11, tylko odczyt)

| Obszar | Odczyt | Polecenie / źródło |
| --- | --- | --- |
| Środowiska | `production`, `szczur-radar-pr-42` (dokładnie te dwa) | `railway environment list --json` |
| Workspace | `kwmacioszek's Projects`, typ `personal`, 1 projekt | MCP `list-workspaces` |
| Limity kosztów | workspace: `softLimit: 5`, `hardLimit: 10`, `isOverLimit: false`; osobny limit „agent” soft/hard $5 | `railway usage limit status --json` |
| Okres rozliczeniowy | bieżący od 2026-10-10T13:39:22Z; poprzedni (2026-09-10 – 2026-10-10): $0,96, limity „not set” | `railway usage --json`, `railway usage --period previous` |
| Zużycie bieżące | $0,05 do teraz, szacunek $0,70 na okres | `railway usage --json` |
| Wolumen Postgres, produkcja | `postgres-volume`, `sizeMB: 500`, `currentSizeMB: 191.88`, region `europe-west4-drams3a` | `railway volume -e <prod-id> list --json`, MCP `describe-environment` |
| Wolumen Postgres, PR-42 | `sizeMB: 500`, `currentSizeMB: 106.63` | `railway volume -e <pr42-id> list --json` |
| Kopie, produkcja | harmonogram `[]`, kopie `[]`, PITR `enabled: false`, `bucketWired: false` | `railway postgres pitr schedule list / backup list / status --json` |
| Buckety | brak | `railway status --json`, MCP `describe-environment` |
| Sekrety API, produkcja | `JWT_SECRET`, `LLM_API_KEY`, `WYKOP_KEY`, `WYKOP_SECRET`: nazwa bez wartości; `LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT`, `LLM_TIMEOUT`: wartości jawne (to ustawienia, nie sekrety) | `railway variable list --json`, przefiltrowane lokalnie: wypisano tylko nazwy i długości, wartości nie trafiły do sesji |
| Sekrety API, PR-42 | `LLM_API_KEY`, `LLM_API_PROVIDER`, `LLM_MODEL`, `WYKOP_KEY`, `WYKOP_SECRET` mają wartości; brak `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT`, `LLM_TIMEOUT`, `JWT_SECRET` | jw. |
| Limit zasobów API, produkcja | `limitOverride: { cpu: 2, memoryBytes: 4000000000 }` | MCP `describe-service` |

Wnioski z tabeli. To interpretacje odczytów, nie fakty potwierdzone przez Railway:

- **Zapieczętowanie:**
  - `null` przy nazwie odpowiada sposobowi, w jaki CLI ≥ 2026-09-04 pokazuje zmienne zapieczętowane: nazwa widoczna, wartość ukryta (changelog Railway 2026-09-04). Opis narzędzia MCP `list-variables` mówi to samo: „Sealed variables are always listed by name with no value”.
  - `LLM_API_PROVIDER` ma długość 10 na produkcji i 8 w PR-42. Ustawienia LLM w PR env są więc inne niż na produkcji, zgodnie z krokiem CI „Ensure PR-env LLM settings” (`.github/workflows/ci.yml:239-282`).
  - Klucz LLM produkcji nie przeszedł zatem do PR env w tej sprawdzonej parze środowisk.
- **Subskrypcja:**
  - Na trialu `railway usage limit set` odmawiało („Usage limits require an active subscription”, `lessons.md:21`, `deploy-plan.md:272`). Dziś limity są ustawione, a nowy okres rozliczeniowy zaczął się 2026-10-10.
  - `limitOverride` pamięci API (4 GB) przekracza maksimum na usługę dla Trial (1 GB) i Free (0,5 GB) według tabeli „Plans” na `railway.com/pricing`.
  - Oba sygnały wskazują na aktywny płatny plan. **Hobby czy Pro, nie wiadomo.**
  - Uwaga: tabela porównawcza ma znacznik „Hard and soft limits” także w kolumnie Free, więc same limity nie dowodzą płatnego planu.
- **Wolumen:**
  - 500 MB to domyślny i maksymalny rozmiar dla Trial/Free (`docs.railway.com/volumes/reference`; `railway.com/pricing`, „Platform limits”). Wolumen nie urósł sam po zmianie planu.
  - Prawie pusta baza PR-42 zajmuje 106,6 MB. Około połowy z 191,9 MB na produkcji to więc najpewniej narzut Postgresa (WAL, katalog), a nie dane. To wniosek, nie pomiar `pg_database_size`.

### C. Kopie wolumenu Railway

- **Dostępność w planach:**
  - `railway.com/pricing`, tabela „Compare features”, wiersz „Built-in database and volume backups”: kolumny Free i Hobby puste, Pro i Enterprise ✓. Odczyt z HTML 2026-10-11: dwie puste komórki, potem dwie z ikoną check. Kolejność kolumn sprawdzona na sąsiednim wierszu „IOPS” z czterema wartościami.
  - Central Station, odpowiedź oznaczona „Railway”, 2026-09-08: „Volume backups (manual, scheduled, and restore) require the Pro plan ($20/month) and are not available on Hobby.” (https://station.railway.com/questions/volume-back-ups-and-cold-starts-adf65411).
  - Strony `docs.railway.com/volumes/backups` i `docs.railway.com/pricing/plans` nie mówią, w których planach są kopie (odczyt 2026-10-11).
  - Jedna nieoficjalna odpowiedź z 2026-06-04 twierdzi, że Hobby je ma. Tabela cennika jest nowsza i autorytatywna.
- **Harmonogramy** (`docs.railway.com/volumes/backups`, odczyt 2026-10-11):
  - „Daily – Backed up every 24 hours, kept for 6 days”, „Weekly … kept for 27 days”, „Monthly … kept for 89 days”.
  - Można wybrać kilka harmonogramów i robić kopie ręczne. Godzinowego nie ma.
  - Starszy przewodnik `guides/postgres-backups-restores` podaje inne okresy retencji i jest nieaktualny.
- **Koszt:** „Backups are incremental and Copy-on-Write…”, „Backups follow the same pricing as Volumes” ($0,15/GB-mies., `docs.railway.com/pricing/plans`).
- **Limity i pułapki:**
  - „Manual backups are limited to 50% of the volume's total size.” Przy wolumenie 500 MB i 191,9 MB zajętych zostaje mały zapas. Wniosek: przed pierwszą kopią warto powiększyć wolumen.
  - „Wiping a volume deletes all backups.”
  - Kopie są spójne na poziomie awarii (crash-consistent), nie aplikacji (odpowiedź Railway 2026-10-08, Station).
  - Limit liczby kopii (10 na usługę) pochodzi tylko z wątków Station z 2025 r. i jest niepotwierdzony.
- **CLI** (5.64.2, `railway postgres pitr --help`): `schedule set --daily [--weekly]`, `schedule list`, `backup create --name`, `backup list`, `backup lock`, `backup restore`.
- **IaC:** `.railway/railway.ts` nie deklaruje kopii ani rozmiaru wolumenu (`railway.ts:62-64`). Czy SDK `railway/iac` 3.11.0 w ogóle je obsługuje, tego nie sprawdzałem.

### D. Odtwarzanie i bezpieczny restore drill

- **Semantyka** (`docs.railway.com/volumes/backups`, odczyt 2026-10-11):
  - Restore „stage[s] the change for you to review”: nowy wolumen „mounted to the same location as the original volume, its name will be the date stamp of the backup”, a „The previous volume will be retained but has been unmounted from the service”.
  - Po kliknięciu Deploy usługa jest wdrażana ponownie.
  - „Backups can only be restored into the same project + environment.”
- **CLI różni się od panelu:** `railway postgres pitr backup restore <ID>` według kodu CLI (railwayapp/cli `src/commands/database/pitr.rs`, v5.64.2) sam zatwierdza i wdraża podmianę, razem ze wszystkim, co już było w staged. Sprawdził to tylko agent badawczy. Wniosek: drill przez panel daje bramkę przeglądu, a przez CLI jej nie daje.
- **Utrata danych:** zapisy po momencie kopii nie trafiają na nowy wolumen. Zostają tylko na starym, odmontowanym. Stary wolumen jest naliczany do czasu usunięcia (odpowiedź Railway 2026-09-28, Station).
- **Inny cel odtworzenia?**
  - Brak wspieranej ścieżki: „We don't have a supported sequence for attaching only that copy to a separate, isolated test service.” (Railway, 2026-10-08, Station).
  - Duplikacja środowiska kopiuje konfigurację, nie dane (`docs.railway.com/environments`). Funkcję „Copy Volumes on PR” wycofano (changelog 2025-05-02).
- **Dla porównania PITR:** odtwarza do nowej usługi obok („The source service is never touched”, `docs.railway.com/volumes/point-in-time-recovery`). Tak wyglądał drill z 2026-09-27 (`deploy-plan.md:278`). Dziś PITR jest wyłączone, a według źródeł z sekcji C wymaga Pro.
- **Przyczyna awarii drillu z 2026-09-27** („No space left on device”, `deploy-plan.md:278`):
  - W dokumentach: za mały wolumen na trialu.
  - Railway w podobnym przypadku (2026-08-31, Station) wskazał zapis na efemeryczny system plików kontenera.
  - Obie hipotezy są możliwe, żadna nie jest potwierdzona dla tego projektu.
- **Warianty drillu.** Wybór należy do planu i użytkownika.
  - **(a) Na produkcji, przez panel.** Kroki: kopia ręczna → wiersz-kanarek → Restore → przegląd staged (rozmiar nowego wolumenu) → Deploy → kanarka nie ma, `flyway_schema_history` jest, `/api/ready` zwraca 200.
    - Testuje dokładnie tę ścieżkę, której użyje się w awarii.
    - Koszt: krótki przestój bazy, zakaz przebiegów w trakcie (`deploy-plan.md:167`) i utrata zapisów od chwili kopii, chyba że potem nastąpi powrót na stary wolumen. Ta procedura powrotu nie jest udokumentowana przez Railway.
  - **(b) W osobnym środowisku.** Kroki: puste lub zduplikowane środowisko, zasiew danych, kopia, restore.
    - Nie dotyka produkcji, ale nie odtwarza prawdziwych danych.
    - Koszt: dodatkowy Postgres na czas drillu.
    - Środowisko dostaje usługi z IaC. Zapieczętowane zmienne się nie kopiują, więc API bez `LLM_*` nie wystartuje (`REPOSITORY_GUIDELINES.md`, Build). Wystarczy sam Postgres.
  - **(c) Logiczny:** `pg_dump` z produkcji, potem `pg_restore` do tymczasowej bazy.
    - Działa w każdym planie i pokazuje, że dane da się odzyskać.
    - Nie testuje kopii Railway.
    - Jako stały mechanizm backupu kłóci się z Non-Goal `prd.md:287`.

### E. Limity kosztów Railway

- Źródło: `docs.railway.com/pricing/cost-control` (odczyt 2026-10-11): „Yes, usage limits are available for all subscription plans.” oraz „The minimum amount you can specify as the hard limit is $10.” Limity ustawia się na stronie Workspace Usage, a robi to admin workspace („You must be a workspace admin to configure usage limits.”).
- **Alert e-mail** (soft) „Your resources will remain unaffected”. **Twardy limit:** „all your workloads will be taken offline”. Powiadomienia przychodzą przy 75%, 90% i 100%.
- **Po przekroczeniu:**
  - Wolumeny nie są usuwane (`docs.railway.com/pricing/faqs`).
  - Storage, w tym oba wolumeny po restore, dalej jest naliczany, więc limit nie jest twardym pułapem kwoty (odpowiedź Railway 2026-09-28, Station).
  - Po podniesieniu limitu Railway sam ponownie wdraża zatrzymane usługi.
- **Zakres skutków dla projektu** (wniosek z tego, że limit jest na workspace): wyłączenie obejmuje produkcję razem z bazą i wszystkie PR env, a zużycie PR env wlicza się do limitu.
- **CLI:** `railway usage limit set --target workspace --soft <$> --hard <$>`, `railway usage limit status`.
- **Bieżące wartości:** soft $5 / hard $10 (sekcja B). Dokumenty zakładają $15/$25 (`deploy-plan.md:14,272,282`). Odczyty nie pokazują, kto ustawił $5/$10 ani kiedy. $10 to minimum twardego limitu według docs cost-control. $5 to według dokumentacji CLI (`docs.railway.com/cli/usage`, odczyt agenta badawczego) najniższy alert.
- **Pro:** $20/mies. z $20 wliczonego zużycia, opłata za workspace (`railway.com/pricing`). Limity porównują się ze zużyciem brutto. Według agenta badawczego przy Pro alert $15 zadziała jeszcze w ramach wliczonych $20. Bieżące zużycie to ok. $1/mies. (poprzedni okres $0,96).

### F. Zapieczętowane sekrety i PR env

- Dokumentacja (`docs.railway.com/variables#sealed-variables`):
  - Wartość zapieczętowana „is never visible in the UI nor can it be retrieved via the API” i „cannot be un-sealed”.
  - Nie jest kopiowana do PR env, przy duplikacji środowiska ani przy duplikacji usługi.
  - Niezapieczętowane zmienne PR env dziedziczy z bazowego środowiska (`guides/managing-secrets-on-railway`).
- CI nigdy nie zapisuje zmiennych produkcji:
  - Kroki „Ensure PR-env LLM settings” (`ci.yml:239-282`) i „Ensure PR-env Wykop keys” (`ci.yml:283-314`) działają tylko na `pull_request` (`ci.yml:246`, `:289`). Mają też strażnik `TARGET_ENV = production` → `exit 1` (`ci.yml:253-256`, `:295-298`).
  - Ścieżka produkcyjna to wyłącznie plan i apply IaC. Bramka blokuje zmiany destrukcyjne, `resource.delete` i zmiany źródła (`ci.yml:320-339`).
  - Sekrety w IaC to tylko `preserve()` (`railway.ts:108-131`).
- Zakres F-01 wobec kluczy Wykopu jest już zmieniony decyzją S-02: te same klucze aplikacji w PR env to świadome odstępstwo (`deploy-plan.md:179`, `roadmap.md:101`).
- Weryfikacja zapieczętowania:
  - `railway variable list --json` pokazuje `null` (sekcja B).
  - Pole `isSealed` jest w schemacie konfiguracji środowiska i w API. Nie widziałem go w wyniku żadnego polecenia.
  - W panelu docs nie opisują żadnego znacznika.

### G. Guardrail kosztów LLM (OpenRouter)

- **Limit na klucz** (`openrouter.ai/docs/guides/best-practices/capping-shared-agent-keys`, `openrouter.ai/docs/api_reference/limits`):
  - Parametry: `limit` w USD i `limit_reset` (`daily` / `weekly` / `monthly`, reset o północy UTC; bez resetu limit jest dożywotni).
  - Ustawia się go w panelu (Key limit) albo przez `PATCH /api/v1/keys/{hash}` kluczem zarządzającym. Odczyt: `GET /api/v1/key` (`limit`, `limit_remaining`).
- **Kod odpowiedzi po wyczerpaniu limitu jest niejednoznaczny w docs OpenRouter.** Przewodnik podaje 403 „Key limit exceeded”, a strona limitów wymienia też 402 z `limit_source: openrouter_key_limit`.
- **Klient w kodzie:**
  - `TransportException.java:79` traktuje 401, 402 i 404 jako `Kind.CONFIGURATION`, nigdy nie ponawia, a przebieg staje bez zużycia prób (`llm/CLAUDE.md:38-42`).
  - 403 zostaje `REJECTED` dla jednego wywołania, bo OpenRouter używa go do moderacji treści (`llm/CLAUDE.md:42`).
  - Wniosek: jeśli wyczerpany limit klucza da 403, każdy oceniany post zużyje jedną z trzech prób. Jeśli da 402, przebieg zatrzyma się czysto.
  - Błędy w treści odpowiedzi 200 są klasyfikowane po `code` tak samo jak status HTTP (`deploy-plan.md:206`, `OpenRouterClient.java:197`).
- **Koszt dla skali:** Luna ~$5,9/mies. (default) lub ~$2,9/mies. (flex) przy 12 tys. postów (`deploy-plan.md:152`). Budżet nie jest ustalony (OQ5).

### H. Czym zweryfikować odtworzoną bazę

- **Schemat** (`szczur-radar-api/src/main/resources/db/migration/`):
  - Migracje V1–V5: `users`, `SPRING_SESSION*`, `pipeline_runs`, `pipeline_run_steps`, `raw_payloads`, `posts`, `assessments`, potem zmiany w V4 i V5.
  - Do tego `flyway_schema_history` z oczekiwaną ostatnią wersją 5.
- **Sondy HTTP:**
  - `/api/ready` wykonuje tylko `SELECT 1` (`health/HealthController.java:37-46`), więc dowodzi połączenia, nie danych.
  - Dane potwierdzają: `GET /api/posts` (pole `total` ocenionych, `posts/api/PostController.java:44-56`), `GET /api/admin/pipeline-runs` (`RunPage.total`, `pipeline/api/PipelineRunController.java:89,148`) oraz logowanie admina (`auth/AuthController.java:72,125`).
- **CLI:** są tylko `migrate` i `create-admin` (`cli/CommandType.java:9-11`). Nie ma polecenia sprawdzającego dane. Liczby wierszy i `flyway_schema_history` wymagają SQL przez `railway connect postgres` (wzorzec w `deploy-plan.md:76`).
- `migrate` na odtworzonej bazie powinien być no-opem z walidacją sum kontrolnych (`MigrateCommand.java:12-23`). To wniosek, nie sprawdziłem tego.

### I. Rozmiar i przyrost danych

- Około 270 postów dziennie (`deploy-plan.md:220`). RAW jest kompresowany gzip w `raw_payloads` (`V3__create_pipeline_and_posts.sql:60-65`).
- Stara aplikacja miała 11 767 wpisów z 29 dni w ~46 MB (`shape-notes.md:406`), czyli ~0,56 GB rocznie przed kompresją (`first-assessed-wykop-feed/plan.md:586-588`).
- Repo nie zawiera żadnego pomiaru rozmiaru bazy produkcji. Jedyne liczby to `currentSizeMB` wolumenów z sekcji B.
- Przy tym tempie 500 MB wolumenu i limit „50% na kopię ręczną” wyczerpią się w ciągu miesięcy, nie lat. To wniosek bez pomiaru przyrostu. Hobby ma maksimum 5 GB, Pro do 1 TB (`docs.railway.com/volumes/reference`).

## Code References

- `context/foundation/prd.md:49` — guardrail kosztów LLM (tylko „minimalizować”).
- `context/foundation/prd.md:252` — NFR: retencja bezterminowa, ochrona danych po stronie platformy.
- `context/foundation/prd.md:287` — Non-Goal: brak własnej logiki backupu.
- `context/foundation/prd.md:299` — Open Question 4, nierozstrzygnięte.
- `context/foundation/roadmap.md:99-112` — F-01; `:93` baseline („PITR włączone”, nieaktualne); `:198-212` S-05; `:260-261` OQ4/OQ5.
- `context/deployment/deploy-plan.md:14-19,116-117,167,179,220,272,278,282,323` — limity, backup, zakaz merge'a w trakcie przebiegu, odstępstwo Wykop, ryzyko bez backupu, drill 2026-09-27, procedura podnoszenia limitu, fallback Render.
- `context/foundation/infrastructure.md:102` — operacje tylko dla człowieka: usuwanie/odtwarzanie bazy lub wolumenu, włączanie i zmiana backupów, zmiana planu.
- `context/foundation/infrastructure.md:109` — risk #1 (backup) z aktualizacją 2026-10-06.
- `.railway/railway.ts:62-64` — Postgres bez deklaracji kopii i rozmiaru wolumenu; `:108-131` — `preserve()` dla sekretów.
- `.github/workflows/ci.yml:239-314` — kroki sekretów PR env ze strażnikiem produkcji; `:320-339` — bramka planu IaC.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/transport/TransportException.java:79` — 401/402/404 → `CONFIGURATION`, reszta 4xx → `REJECTED`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md:38-42` — dlaczego 403 zostaje `REJECTED`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/health/HealthController.java:37-46` — `/api/ready` = `SELECT 1`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/cli/CommandType.java:9-11` — tylko `migrate`, `create-admin`.

## Architecture Insights

- **F-01 to praca operatorska, nie kod produktu** (`roadmap.md:111`). Plan, kopie, odtwarzanie i limity są ustawieniami konta i projektu Railway. Według `infrastructure.md:102` plan, backupy i odtwarzanie wolumenu robi tylko człowiek. Agent może czytać stan i weryfikować (CLI/MCP w trybie odczytu). Zmiany w repo to dokumenty (`deploy-plan.md`, `infrastructure.md`, roadmapa, flaga przy PRD OQ4, nowy wpis w `lessons.md`). Kod tylko wtedy, gdy plan zdecyduje o obsłudze 403 z OpenRoutera lub o poleceniu weryfikacji danych.
- **Kroki na produkcji nie zależą od merge'a.** Lekcja „Implementacja, przegląd i archiwizacja na jednej gałęzi” (`lessons.md`) zabrania w Progress kroków możliwych dopiero po merge'u. Kroki F-01 na produkcji (plan, kopie, drill, limity) da się zrobić przed merge'em, bo nie zależą od kodu. Plan może je więc trzymać w Progress, a nie tylko w runbooku. To wniosek do potwierdzenia w `/10x-plan`.
- **IaC nie obejmuje kopii.** `railway.ts` nie deklaruje harmonogramu kopii ani rozmiaru wolumenu. Po restore Railway montuje nowy wolumen, więc trzeba sprawdzić, czy `railway config plan` na produkcji nadal wychodzi z kodem 0 (`lessons.md`, Railway IaC w CI). Niezweryfikowane.
- **Twardy limit działa na cały workspace.** Twardy limit chroni budżet, ale może wyłączyć produkcję razem z bazą. Procedura podniesienia limitu jest w `deploy-plan.md:282`.

## Historical Context (from prior changes)

Ocena każdego twierdzenia osobno:

- `deploy-plan.md:116`: „production database holds no real data (one admin)” (2026-10-06). **Nieaktualne od 2026-10-09:** S-02 działa na produkcji (issue #14, komentarz 2026-10-10; issue #39), a wolumen ma 191,9 MB.
- `deploy-plan.md:198`: „production holds only accounts and sessions”. **Nieaktualne** z tego samego powodu.
- `roadmap.md:110`: „baza bez prawdziwych danych”. **Nieaktualne.** Reszta tego wpisu (Daily 6 dni, restore tylko w tym samym środowisku) **jest poparta** docs Railway (sekcje C i D).
- `roadmap.md:93`: baseline „PITR włączone”. **Nieaktualne** od 2026-10-06 (`pitr status` → `enabled: false`).
- `lessons.md:19-23`: na trialu polegaj na PITR. **Częściowo:** opis trialu był prawdziwy 2026-09-27, ale PITR jest wyłączone, a według źródeł z sekcji C wymaga Pro. Plik jest append-only, więc korekta to nowy wpis.
- `deploy-plan.md:29`: krok CI „Ensure a PR-env JWT_SECRET”. **Nieaktualne:** kroku nie ma w `ci.yml`. `JWT_SECRET` zostało tylko jako nieużywane `preserve()` (`railway.ts:105-108`).
- `deploy-plan.md:14,272`: limity $15/$25. **Sprzeczne ze stanem:** ustawione są $5/$10 (sekcja B). Kwota to decyzja użytkownika.
- `deploy-plan.md:117`: przepis Daily + ręczna kopia + restore + weryfikacja. **Poparte** przez docs, **pod warunkiem planu Pro**, którego dokument nie wymienia.
- `deploy-plan.md:278`: drill PITR z 2026-09-27 padł przez zbyt mały wolumen. **Częściowo:** hipoteza możliwa. Railway w podobnym przypadku wskazał efemeryczny system plików.
- `context/archive/2026-10-04-api-docker-image/`:
  - Decyzja o wyłączeniu PITR bez Daily: `change.md:21`, `reviews/impl-review-phase-1.md:41`.
  - Restore test nadal blokuje S-05: `reviews/impl-review.md:25`.
  - `plan-brief.md:33` mówi o Daily „włączonym wcześniej”. **Sprzeczne** z `change.md:21`, gdzie Daily nie zostało włączone.
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:506-507,534`: odstępstwo dla kluczy Wykopu i zaakceptowane ryzyko „bez backupu i limitu przed F-01”. **Poparte**, ale ryzyko dotyczy już prawdziwych danych.

## Related Research

- `context/archive/2026-10-04-api-docker-image/research.md` — decyzja PITR → Daily.
- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md` — RAW w Postgresie a Railway Buckets, przyrost danych.

## External Sources (odczyt 2026-10-11)

- https://railway.com/pricing — tabele „Plans”, „Platform limits”, „Compare features” (wiersze „Built-in database and volume backups” i „Hard and soft limits”).
- https://docs.railway.com/volumes/backups — harmonogramy, restore, ograniczenia.
- https://docs.railway.com/volumes/reference — rozmiary wolumenów, zmiana rozmiaru.
- https://docs.railway.com/volumes/point-in-time-recovery — PITR do nowej usługi.
- https://docs.railway.com/pricing/plans, https://docs.railway.com/pricing/cost-control, https://docs.railway.com/pricing/faqs.
- https://docs.railway.com/variables#sealed-variables, https://docs.railway.com/environments.
- https://station.railway.com/questions/volume-back-ups-and-cold-starts-adf65411 (2026-09-08, Pro-only).
- https://station.railway.com/questions/pitr-restore-crashes-with-no-space-left-2891cd36 (2026-08-31).
- https://station.railway.com/questions/railway-pro-volume-backup-and-isolated-r-c838fa93 (2026-10-01).
- https://station.railway.com/questions/hobby-compute-hard-limit-maximum-billab-2e0dd303 (2026-09-28).
- https://openrouter.ai/docs/guides/best-practices/capping-shared-agent-keys, https://openrouter.ai/docs/api_reference/limits.

Wątki Station to odpowiedzi z forum, nie dokumentacja. Z nich tylko „Pro-only” jest rozstrzygające, a potwierdza je tabela cennika.

## Open Questions

Decyzje i luki dla `/10x-plan`. Punkty 1–4 rozstrzygnął użytkownik 2026-10-11 (sekcja „Follow-up 2026-10-11” niżej). Zostały tu w pierwotnym brzmieniu jako zapis.

1. **Plan Railway: Hobby czy Pro?** Do sprawdzenia w panelu: Workspace → Billing. Na Hobby dzienna kopia wolumenu jest niedostępna (sekcja C). Wtedy do wyboru:
   - przejście na Pro ($20/mies. za workspace, z $20 zużycia);
   - backup logiczny `pg_dump`, wymagający flagi przy Non-Goal `prd.md:287`;
   - Render jako fallback (risk #1, `infrastructure.md:109`).
2. **Wariant restore drillu:** (a) na produkcji przez panel, (b) w osobnym środowisku, (c) logiczny, albo ich połączenie (sekcja D). Dla (a) trzeba ustalić okno, zakaz przebiegów, sposób powrotu na stary wolumen i usunięcie starego wolumenu po drillu (koszt).
3. **Rozmiar wolumenu:** powiększyć 500 MB przed pierwszą kopią (limit 50% dla kopii ręcznej, przyrost ~270 postów dziennie). Do ustalenia, do ilu.
4. **Kwoty limitów:** zostawić $5/$10 czy ustawić $15/$25 z `deploy-plan.md`? Przy Pro limity liczone są od zużycia brutto, a twardy limit wyłącza cały workspace razem z bazą.
5. **Limit kredytu klucza OpenRouter:** w zakresie F-01 czy nie (PRD tego nie wymaga, OQ5 otwarte)? Jeśli tak, trzeba sprawdzić, jaki kod zwraca wyczerpany limit (402 czy 403), i ewentualnie zmienić klasyfikację w `TransportException`, żeby limit nie zużywał prób postów.
6. **Do sprawdzenia podczas drillu (brak w docs):** rozmiar wolumenu odtworzonego z kopii, czy harmonogram Daily przechodzi na nowy wolumen, czy `railway config plan` po restore nadal wychodzi z kodem 0.
7. **Aktualizacje dokumentów, jeśli plan je obejmie:**
   - `deploy-plan.md`: przesłanka o pustej bazie, bramka „faza 6” → „S-05”, nieistniejący krok CI z `JWT_SECRET`, kwoty limitów;
   - `roadmap.md:93,110`;
   - flaga przy PRD OQ4 (rozstrzygnięcie po drillu; PRD edytuje człowiek);
   - nowy wpis w `lessons.md` zastępujący lekcję o PITR na trialu.

## Follow-up 2026-10-11: decyzje użytkownika, szacunek wolumenu, miejsce dumpów

### Decyzje użytkownika (2026-10-11)

1. **Plan Railway: Hobby.** Zgodnie z sekcją C dzienna kopia wolumenu i PITR są w nim niedostępne. Zastępuje to Summary 4 oraz Open Question 1.
2. **Backup: `pg_dump`** (kopia logiczna). Na Hobby to jedyna możliwość bez zmiany planu lub platformy. Pozostałe opcje to przejście na Pro albo Render, a użytkownik żadnej z nich nie wybrał.
3. **Restore drill: przez `pg_dump`**, czyli wariant (c) z sekcji D. Zastępuje to Open Question 2.
4. **Limity kosztów zostają: soft $5 / hard $10** (sekcja B). Zastępuje to Open Question 4. Wartości $15/$25 w `deploy-plan.md:14,272,282` są nieaktualne.
5. **Pytanie do `/10x-plan`:** gdzie przechowywać dumpy, na przykład lokalnie u użytkownika. Wstępne opcje są niżej. To nie jest decyzja.

### Flaga: sprzeczność z PRD (reguła repo: „If code contradicts prd.md, flag it”)

- `pg_dump` przenosi odpowiedzialność za backup z platformy na operatora albo na własny mechanizm. PRD mówi:
  - „Bezpieczeństwo przed utratą danych jest odpowiedzialnością platformy hostingowej, nie logiki aplikacji.” (`prd.md:252`);
  - Non-Goal „Custom logika archiwizacji/backupu danych” (`prd.md:287`).
- Jeśli dump robi operator albo osobne zadanie infrastruktury poza kodem `szczur-radar-api`, sprzeczność z literą Non-Goal jest częściowa, bo to nie jest „logika aplikacji”. Założenie NFR, że platforma chroni dane, przestaje jednak obowiązywać.
- Plan powinien zaproponować aktualizację PRD: NFR, Non-Goal i rozstrzygnięcie OQ4 (`prd.md:299`). Zmianę zatwierdza człowiek.
- Ta sama decyzja zmienia też:
  - rejestr ryzyk (`infrastructure.md:109`, risk #1: „otherwise fall back to Render”);
  - wpis F-01 w roadmapie (`roadmap.md:101`, „włączona dzienna kopia wolumenu”). Zmiana `roadmap.md` pociąga za sobą aktualizację `tools/render_roadmap.swift` i ponowne wygenerowanie `docs/visuals/roadmap-mvp.png` (`REPOSITORY_GUIDELINES.md`, Project Structure).

### Szacunek rozmiaru wolumenu (Open Question 3)

Odczyt rozmiarów i liczby wierszy z produkcyjnej bazy (`railway ssh` + `psql`, transakcja tylko do odczytu) zablokował tryb auto („Production Reads”). Szacunek poniżej to **wnioski z danych pośrednich, bez pomiaru**:

| Składnik | Szacunek | Podstawa |
| --- | --- | --- |
| Post (`posts` + indeksy) | ~1–2 KB | treść wpisu i metadane (`V3__create_pipeline_and_posts.sql:84-125`) |
| RAW (`raw_payloads`) | ~0,5–1 KB | wpis JSON ~1,4 KB przed kompresją, gzip w aplikacji (`V3:60-73`; fixture z `first-assessed-wykop-feed/research.md:200`) |
| Ocena (`assessments`) | ~3–7 KB, więcej przy kolejnych próbach | `input` JSONB z treścią posta, `output` JSONB z cytatami i `raw_text` odpowiedzi modelu (`V3:130-160`) |
| **Razem na post** | **~5–10 KB** | suma powyższych |
| Przyrost dzienny (~270 postów, `deploy-plan.md:220`) | ~1,4–2,7 MB | 270 × 5–10 KB |
| **Przyrost roczny** | **~0,5–1,0 GB** | × 365; zgodne rzędem wielkości z ~0,56 GB/rok ze starej aplikacji (`shape-notes.md:406`) |
| Narzut Postgresa | ~0,1 GB stale, do ~1 GB chwilowo | prawie pusta baza PR-42 zajmuje 106,6 MB (sekcja B). Domyślne `max_wal_size` Postgresa to 1 GB: przy serii zapisów (zaległy zakres dat, `pg_restore`) katalog `pg_wal` może chwilowo urosnąć do ~1 GB. Konfiguracji obrazu Railway nie sprawdzałem. |

Wnioski:

- **500 MB to za mało już teraz.** Wolumen ma ~308 MB wolnego, a seria zapisów może zapełnić go samym WAL-em. Pasuje to do „No space left on device” z drillu 2026-09-27 (`deploy-plan.md:278`), ale tego nie potwierdzono.
- **Rekomendacja: 5 GB**, czyli maksimum Hobby (`railway.com/pricing`, „Platform limits”; zmiana rozmiaru w Hobby: `docs.railway.com/volumes/reference`, zmniejszyć się nie da). Po odjęciu ~1,1 GB narzutu i zapasu na WAL zostaje miejsce na ~4–8 lat danych według szacunku.
- **Koszt powiększenia jest pomijalny, dopóki dane nie urosną.** Wniosek z dzisiejszego rachunku: pozycja „Volume” to $0,00165 po ~1 dobie okresu. To zgadza się z rozliczeniem za zajęte miejsce (0,30 GB z dwóch wolumenów × $0,15 / 30 dni ≈ $0,0015). Rozliczenie za przydzielone 2 × 500 MB dałoby ok. $0,005. Plan powinien to potwierdzić w docs Railway.
- **Dokładniejszy pomiar:** właściciel konta może sam uruchomić zapytanie tylko do odczytu, np. `pg_database_size`, `pg_total_relation_size` per tabela i `count(*)` z `posts` / `assessments` / `raw_payloads` przez `railway connect postgres -e production`.
- **Rozmiar dumpa** (wniosek): `pg_dump -Fc` nie zawiera indeksów i kompresuje JSONB. RAW jest już spakowany gzipem, więc się nie skurczy. Pełny dump po roku powinien mieć rząd ~0,2–0,5 GB.

### Wstępne opcje miejsca dumpów (wejście do `/10x-plan`, nie decyzja)

- **Lokalnie u użytkownika (Mac):**
  - Dostęp jest możliwy bez publicznego proxy. `railway connect postgres -e production --tunnel-only` otwiera tunel SSH do prywatnej bazy („Works without a public domain; auto-enabled when the service has no public proxy URL”, `railway connect --help`, CLI 5.64.2).
  - Lokalny `pg_dump` ma wersję 18.6, zgodną z serwerem Postgres 18.
  - Restore drill da się zrobić do lokalnego Postgresa 18 z `compose.local.yaml` (ten sam obraz `ghcr.io/railwayapp-templates/postgres-ssl:18`) bez dotykania produkcji.
  - Zalety: kopia poza Railway przetrwa utratę projektu lub konta albo wyczyszczenie wolumenu. Koszt $0.
  - Wady:
    - Ręczne uruchamianie albo `launchd` przy włączonym Macu, więc częstotliwość zależy od dyscypliny. Od S-05 dane przybywają codziennie.
    - Tunel wymaga zalogowanego CLI.
    - Dump zawiera dane wrażliwe: e-maile i skróty argon2 w `users` oraz identyfikatory aktywnych sesji w tabelach Spring Session (`V2__create_spring_session.sql`), które pozwalają przejąć sesję. Do rozważenia: pominąć dane sesji (`--exclude-table-data`), przechowywać na zaszyfrowanym dysku i poza repo oraz folderami synchronizowanymi z chmurą.
- **Railway Bucket + serwis cron w projekcie:**
  - Automatycznie i tanio ($0,015/GB-mies., `infrastructure.md:125`).
  - Ten sam dostawca i projekt, więc nie chroni przed utratą projektu lub konta, ale chroni przed wyczyszczeniem wolumenu.
  - Upload liczy się jako egress, bo bucket jest poza siecią prywatną.
  - Dochodzi osobny serwis i jego sekrety.
- **Zewnętrzny magazyn S3-kompatybilny** (np. R2, B2): automatycznie i poza Railway, ale to kolejne konto i klucze.
- **Hybryda:** automatyczny dump do magazynu plus okresowa kopia lokalna.
- **GitHub Actions z harmonogramem:** wymagałby dostępu do bazy z runnera (publiczny proxy TCP albo token Railway w CI z tunelem) i przepuszczałby dane przez GitHub. Stoi to w sprzeczności z prywatną bazą (`REPOSITORY_GUIDELINES.md`, Deployment). Wstępnie odrzucam.

Do rozstrzygnięcia w `/10x-plan`: miejsce dumpów, częstotliwość i retencja (RPO wobec dziennych danych od S-05), automatyzacja, ochrona danych w dumpie, kto i jak często robi restore drill oraz jak udokumentować procedurę w `deploy-plan.md`.
