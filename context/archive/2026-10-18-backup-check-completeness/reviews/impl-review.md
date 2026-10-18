<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-15: kopia bazy sprawdzana w całości

- **Plan**: context/changes/backup-check-completeness/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-18
- **Verdict**: NEEDS ATTENTION → po triage wszystkie ustalenia FIXED
- **Findings**: 0 critical, 3 warnings, 6 observations

Uwaga o zakresie: wszystkie wiersze Automated faz 1–3 są odhaczone; wiersze Manual (1.4, 3.4–3.9) są oczekujące i nie były oceniane jako wykonane.

Kryteria automatyczne uruchomione ponownie: `bash -n tools/db-backup/*.sh` → OK; `tools/db-backup/test.sh` → „All checks passed.” (18 PASS); CI PR #79 na `fdbc5dc` → wszystkie joby zielone. Pierwsze uruchomienie miało czerwony `backend (Maven verify)`, a w nim `ConcurrentAssessmentDispatcherTest.cancellingARunInterruptsItsCallInFlightAndLeavesAnotherRunAlone`. To niestabilny test: zmiana nie dotyka Javy, a ponowne uruchomienie przeszło.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Błędny wpis w tables.sh po cichu usuwa tabelę z kontroli

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/tables.sh:39-41
- **Detail**: `tables_at` czyta wpis przez `read -r table since mode`, a porównanie `(( 10#$since <= 10#$version ))` stoi w liście `&&`, więc `set -e` go nie łapie. Sprawdzone: wpis `"notifications 9x counted"` daje tylko komunikat bash na stderr, a `counted_tables 9` zwraca 0 bez `notifications`. Tabela nie jest wtedy ani liczona, ani wymagana. Opisane w nagłówku przyszłe czwarte pole (`"users 1 counted 12"`) daje `mode="counted 12"`, więc `users` też po cichu wypada z liczonych. Literówka w trybie (`countd`) robi z tabeli `present`. `test.sh` sprawdza liczby tylko dla 4 tabel, więc literówka przy `assessments`, `raw_payloads` albo `pipeline_runs` przejdzie CI. A to jest plik, który od teraz edytuje każda migracja dodająca tabelę.
- **Fix**: W `tables_at` czytaj `read -r table since mode until _` i zwracaj 1, gdy `since` nie pasuje do `^[0-9]+$` albo `mode` nie jest `counted`/`present`. Dodaj w `test.sh` asercję, że `counted_tables`/`required_tables` przy V9 zwracają dokładnie 7 i 11 tabel.
  - Strength: Kontrakt planu („wersja niepasująca do `^[0-9]+$` daje błąd”) obejmie wtedy także wpisy, a nie tylko argument; literówka zatrzyma CI.
  - Tradeoff: Kilka linii w `tables.sh` i jedna asercja w teście.
  - Confidence: HIGH — zachowanie potwierdzone eksperymentem w bash.
  - Blind spot: None significant.
- **Decision**: FIXED — walidacja wpisów w `tables_at` (wersja liczbowa, tryb counted|present, brak 4. pola), komunikaty wywołań w `backup.sh:165` i `restore-check.sh:99`, asercja w `test.sh` (11/7 tabel przy V9, dwa zepsute wpisy kończą się błędem); `test.sh` zielony, asercja czerwona na poprzedniej wersji `tables.sh`.

### F2 — Test nie dowodzi, że wygasła zgoda nie przepuszcza nowego spadku

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/test.sh:292-297
- **Detail**: Krok „zgoda wygasła” (run10) używa starej wartości `BACKUP_ACCEPT_SHRINK`, gdy nic nie spadło, i sprawdza tylko log `ignoring …` oraz `accepted_shrink == false`. Żaden przebieg nie sprawdza, że stara zgoda nie przepuszcza nowego spadku, a to jest właściwa gwarancja bezpieczeństwa zgody. Dziś pilnuje tego `backup.sh:203-206` (`accept=""`), ale regresja, która tylko loguje i nie czyści `accept`, przeszłaby test.
- **Fix**: Po run10 usuń jeszcze jeden post i uruchom przebieg ze starym `$accept`: oczekuj kodu ≠ 0, `rows lost` i nazwy najnowszego manifestu w komunikacie; backupy bez zmian.
- **Decision**: FIXED — `test.sh` run12: stara zgoda + nowy spadek → porażka (`rows lost since daily/<run10>: posts 34 < 35`, nowa wartość zgody, backupy bez zmian); run13 z nową wartością przechodzi, żeby kolejne kroki miały zgodny manifest. Test zielony; czerwony, gdy `backup.sh` nie czyści starej zgody.

### F3 — Runbook każe czekać na accepted_shrink: true, które może nie nadejść

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:345, tools/db-backup/README.md:29
- **Detail**: Krok 8.4 runbooka i README mówią: usuń zmienną po pierwszej kopii z `accepted_shrink: true`. Kod ma trzecią ścieżkę (`backup.sh:213-214`): zgoda pasuje, ale nic nie spadło, więc log ma `BACKUP_ACCEPT_SHRINK=… not needed`, a manifest `accepted_shrink: false`. Tak będzie np. po odtworzeniu najnowszego dumpa albo gdy przebieg pipeline'u o 00:30 doda wiersze przed 02:00. Operator czekałby na `true`, które nie nadejdzie. Zostawiona zmienna jest nieszkodliwa, ale warunek sprzątania jest błędny. To samo sformułowanie jest w planie, więc to luka planu przeniesiona do dokumentów.
- **Fix**: Zmień warunek na „po pierwszej udanej kopii porównanej z tym manifestem (`accepted_shrink: true` albo log `not needed`)” w obu miejscach; przy okazji wspomnij w README log `not needed` i `there is no manifest in daily/`.
- **Decision**: FIXED — runbook krok 8.4: usuń zmienną po pierwszej udanej kopii porównanej z tym manifestem (`compared_with`), z `accepted_shrink: true` albo `false` + log `not needed`; F-01 nie wiąże już wygaśnięcia z `true`; README opisuje oba logi. README „Test lokalny” dostał też przypadki z F1 i F2.

### F4 — Ścieżki, w których kontrola spadku po cichu się wyłącza

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/backup.sh:180-197
- **Detail**: Dwie ścieżki przechodzą bez porównania:
  - **(a)** Każdy manifest w `daily/` ma datę późniejszą niż `$name` (np. zegar kontenera spóźniony o ponad dobę). Wtedy `previous=""` i przebieg traktowany jest jak pierwsza kopia, choć bucket ma manifesty. Wynik to tylko linia `ignoring` w logu.
  - **(b)** Walidacja `all(.[]; type == "number")` przepuszcza `counts_after: {}` albo klucze bez wspólnej tabeli, więc nic nie jest porównywane. Manifesty pisze tylko `backup.sh`, więc to wymaga ręcznie zrobionego albo uszkodzonego pliku.

  Prawdopodobieństwo jest niskie, ale obie ścieżki są fail-open w narzędziu, którego celem jest fail-closed.
- **Fix**: Kończ przebieg błędem, gdy `daily/` ma manifesty, ale wszystkie zostały pominięte (ucieczką jest zmiana nazwy na `.broken`), a w walidacji wymagaj `.counts_after.posts | type == "number"`.
  - Strength: Zamyka obie ścieżki kilkoma liniami; zgodne z intencją planu („nie da się sprawdzić spadku → błąd”).
  - Tradeoff: Nowa ścieżka porażki i nowy przypadek testowy; plan tego nie wymagał.
  - Confidence: MED — logika pewna, scenariusze rzadkie.
  - Blind spot: Nie sprawdzono, jak zachowuje się zegar kontenera crona na Railway.
- **Decision**: FIXED — `backup.sh`: przebieg kończy się błędem, gdy `daily/` ma manifesty, ale wszystkie są z datą późniejszą niż bieżący przebieg; walidacja wymaga `counts_after.posts` jako liczby. `test.sh` (osobny remote `/out/alt`): same manifesty z przyszłości → porażka; najnowszy manifest bez liczb → porażka z nazwą i wskazówką `.broken`; po zmianie nazwy przebieg przechodzi z `compared_with: null`. README i F-01 w deploy-plan opisują nową porażkę. Oba sprawdziany (cofnięcie każdej z dwóch zmian) dały czerwony test.

### F5 — Gałęzie uszkodzonego manifestu bez testu

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/backup.sh:192-197
- **Detail**: `test.sh` nie sprawdza nieczytelnego manifestu (`rclone cat` pada), `counts_after` z nieliczbami ani opisanej w runbooku ucieczki przez zmianę nazwy na `.broken`. Plan tego nie wymagał.
- **Fix**: Dodaj przypadek: najnowszy manifest z niepoprawnym JSON → porażka z nazwą pliku i wskazówką `.broken`; po zmianie nazwy przebieg przechodzi.
- **Decision**: FIXED — pokryte przypadkami z F4 (`counts_after: {}` → porażka z nazwą i wskazówką; zmiana nazwy na `.broken` odblokowuje przebieg).

### F6 — Tablica known nadpisana skalarem w restore-check.sh

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/db-backup/restore-check.sh:108-109
- **Detail**: `mapfile -t known` tworzy 11-elementową tablicę, a `known=$(printf …)` nadpisuje tylko `known[0]`. `${known%,}` działa, bo czyta element 0, ale elementy 1–10 zostają nieaktualne. Kilka linii wyżej ten sam kod używa osobnego skalara `table_list`.
- **Fix**: Użyj osobnego skalara (`known_list`) jak `table_list` w linii 102.
- **Decision**: FIXED — osobny skalar `known_list` jak `table_list`.

### F7 — Plik .broken nigdy nie znika, a „ignoring” loguje się dwa razy

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/db-backup/backup.sh:177, :278
- **Detail**: `list_files daily` jest wołane teraz dwa razy w przebiegu (kontrola i `prune`), więc każda nazwa spoza wzorca loguje się jako „ignoring” dwukrotnie. Manifest przemianowany na `….manifest.json.broken` (ucieczka z runbooka) nie jest usuwany przez retencję, bo `prune` usuwa tylko dokładne nazwy. Plik zostaje w buckecie i w lustrze NAS.
- **Fix**: Przekaż `$daily_files` do `prune daily` zamiast listować drugi raz, a w runbooku dopisz, że plik `.broken` usuwa się ręcznie po wypadnięciu jego dumpa z retencji.
- **Decision**: FIXED (tylko dokumenty) — README i F-01 w deploy-plan: plik `.broken` usuwa się ręcznie, gdy jego dump wypadnie z retencji. Kod retencji bez zmian: ponowne użycie `$daily_files` (listowane przed wysyłką, bez bieżącego dumpa) zmieniłoby liczenie retencji, którą plan wyklucza. Podwójny log „ignoring” zostaje (nieszkodliwy).

### F8 — Polskie placeholdery „Wynik” w angielskiej sekcji deploy-plan

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/deployment/deploy-plan.md:881-891
- **Detail**: Treść sekcji S-15 jest po angielsku jak reszta pliku, a 6 placeholderów `Wynik: — (do uzupełnienia przy weryfikacji)` jest po polsku i nigdzie indziej w pliku nie występuje.
- **Fix**: Zamień na `Result: — (pending)` albo wpisz wyniki w czasie weryfikacji w PR env.
- **Decision**: FIXED — 6 placeholderów zamienionych na `Result: — (pending)`.

### F9 — Zgoda porównywana dokładnie, bez obcięcia białych znaków

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/backup.sh:203
- **Detail**: Wartość `BACKUP_ACCEPT_SHRINK` wklejona ze spacją albo znakiem nowej linii jest „ignorowana” i przebieg znów pada. To bezpieczne (log mówi, co zignorowano), ale operator traci noc, a znak nowej linii rozbija linię logu.
- **Fix**: Obetnij białe znaki z `BACKUP_ACCEPT_SHRINK` przed porównaniem.
- **Decision**: FIXED — `backup.sh` obcina białe znaki z `BACKUP_ACCEPT_SHRINK`; `test.sh` run9 podaje wartość ze spacją na końcu; README o obcinaniu. Test zielony; czerwony bez obcinania.

## Triage

- Fixed: F1, F2, F3, F4, F5 (przez F4), F6, F7 (tylko dokumenty), F8, F9 (9)
- Weryfikacja po poprawkach: `bash -n tools/db-backup/*.sh` OK; `tools/db-backup/test.sh` → „All checks passed.” (21 PASS); każda poprawka kodu ze sprawdzianem (cofnięcie zmiany → test czerwony, plik przywrócony).
