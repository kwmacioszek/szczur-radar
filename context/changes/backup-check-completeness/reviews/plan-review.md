<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-15: kopia bazy sprawdzana w całości

- **Plan**: context/changes/backup-check-completeness/plan.md
- **Mode**: Deep
- **Date**: 2026-10-18
- **Verdict**: SOUND
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | WARNING |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

8/8 ścieżek ✓, 6/6 symboli ✓, brief↔plan ✓, Progress↔Fazy ✓.

Twierdzenia sprawdzone w kodzie (wszystkie potwierdzone):
- Wiersze znikają tylko w `activation_tokens` (`ActivationLinks.java:168`, a także upsert w `ActivationTokenRepository.java:45-49`) oraz w `spring_session` (`:196`, kaskada, wbudowane czyszczenie Spring Session).
- „Odkładanie postów” to `UPDATE` (`PostTransitions.java:33-35`). Kroki przebiegu są tylko dopisywane (`RunRecorder.java:230-233`). Powiadomienia mają `ON CONFLICT DO NOTHING` (`NotificationRepository.java:51-53`).
- `ON DELETE CASCADE` jest tylko od `users` (V7:12, V9:15), a żaden kod nie usuwa użytkownika.
- `drop table activation_tokens` oraz `truncate assessments, posts, raw_payloads` przechodzą bez `CASCADE`.
- `db-backup` ma `watchPatterns: ["tools/db-backup/**"]` (`.railway/railway.ts:225-226`).
- `AGENTS.md` tylko importuje `REPOSITORY_GUIDELINES.md`.

## Findings

### F1 — Plan nie określa, jak porównywać wersje Flyway w tables.sh

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 1 — #1 Klasyfikacja tabel, #3 restore-check.sh
- **Detail**:
  - Wersja Flyway jest tekstem. Przy V10 porównanie tekstowe daje `'10' < '2'`, więc dump z V10 nie wymagałby tabel z V2–V9, a `count_rows` pominąłby rosnące tabele. Test tego nie wykryje, bo najwyższa wersja to dziś V9.
  - Kontrakt nie mówił też, co zrobić z tabelą usuniętą w przyszłej migracji. Usunięcie jej wpisu sprawiłoby, że kopie miesięczne zaczną padać na `tables: unknown`.
- **Fix**: Porównanie liczbowe `(( ))`, błąd dla wersji spoza `^[0-9]+$`, zasada „wpisów się nie usuwa” w komentarzu nagłówka.
- **Decision**: FIXED

### F2 — Miejsce przypadku „nieznana tabela” w test.sh nie jest ustalone

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 — #5 Test; Critical Implementation Details
- **Detail**: `restore-check.sh` sprawdza `missing` przed `unknown`, a krok 3 usuwa `activation_tokens` i nie przywraca tej tabeli (`test.sh:223`). Przypadek `stray` uruchomiony po tym kroku padłby na `tables: missing activation_tokens`.
- **Fix**: Kolejność dopisana do Critical Implementation Details: `stray` biegnie przed `drop table activation_tokens`, a w fazie 2 po kroku „spadek i zgoda”.
- **Decision**: FIXED

### F3 — Uszkodzony najnowszy manifest blokuje kopie co noc

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 — #1 „Najnowszy manifest”
- **Detail**: Niepoprawny JSON w najnowszym manifeście kończy przebieg błędem i `BACKUP_ACCEPT_SHRINK` tego nie obejmuje. Brakowało instrukcji, jak odblokować kopie.
- **Fix**: Komunikat podaje nazwę pliku i wskazówkę: zmień nazwę pliku na taką spoza wzorca, a `list_files` go pominie. Dodane jedno zdanie w runbooku F-01.
- **Decision**: FIXED

### F4 — Posty w PR env z przebiegu pipeline'u, gdy wystarczyłby SQL

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Faza 3 — Manual 3.4
- **Detail**: Weryfikacja potrzebuje tylko `posts > 0` i kilku rosnących tabel. Przebieg pipeline'u wymaga `pipeline-trigger`, tymczasowego harmonogramu oraz prawdziwych wywołań Wykopu i LLM.
- **Fix**: Dane przykładowe z `test.sh:107-135` wgrane SQL-em przez tunel. Przebieg pipeline'u zostaje jako opcja. Zaktualizowane są tytuł kroku 3.4 i brief.
- **Decision**: FIXED

### F5 — Kryterium 1.4: zdanie o porcie przy trybie bez --serve

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 — Manual Verification
- **Detail**: Bez `--serve` `restore-check.sh` nie otwiera żadnego portu.
- **Fix**: Zdanie zastąpione odsyłaczem do kroku 2 runbooka odtworzenia (`deploy-plan.md:302-306`).
- **Decision**: FIXED
