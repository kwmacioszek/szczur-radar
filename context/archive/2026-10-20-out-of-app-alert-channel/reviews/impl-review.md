<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: F-04: kanał alarmowy poza aplikacją

- **Plan**: context/changes/out-of-app-alert-channel/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-24
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 8 observations

Implementacja: squash `45d18c4` (PR #95), pliki czytane w stanie z `45d18c4`; późniejsze zmiany `tools/healthchecks/*` z `9ef690b` (PR #99, `production/deploy-health`) poza zakresem. Produkcja zweryfikowana (issue #66, zamknięte 2026-10-24).

Kryteria automatyczne (2026-10-24, na `main` + `dcf094a`): `tools/healthchecks/test.sh` zielony; `shellcheck` v0.10.0 (Docker) na `tools/healthchecks/*.sh` — 0 uwag; `tools/pipeline-trigger/test.sh` i `tools/db-backup/test.sh` zielone, po testach brak kontenerów i sieci; `./mvnw -B verify` i pozostałe joby — CI run 39984643636 na `45d18c4`, 9/9 zielone. Wiersze Manual mają dowody w komentarzach #66.

Udokumentowane odstępstwa (nie są ustaleniami): `db-backup` melduje przy limicie czasu `/124` lub `/137` zamiast `/1` (zachowany kod wyjścia; README, runbook, test); treść zdarzenia API to `<EVENT> (<opis>): <detail>` zamiast `<event>: <detail>` (javadoc `HealthchecksAlerts`, README, runbook). Dodatki poza planem (krok 0 runbooka, `--help` i `HEALTHCHECKS_API_URL` w `checks.sh`, obraz `$id-ping` w teście triggera, linia INFO przy starcie) są nieszkodliwe i opisane.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Sekcja F-04 w runbooku mówi „in progress, not merged”

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:1194
- **Detail**: Status sekcji to nadal „(2026-10-20): in progress … not merged, PR-env verification pending”, 12 pól listy weryfikacji w PR env jest `[ ]`, a krok „Record” mówi „here and as a comment on issue #66”, choć wyniki są tylko w #66. Inne sekcje (S-05, S-08) dostają po merge'u status z commitem i datą.
- **Fix**: Status „Done (2026-10-24)”: merge `45d18c4` (PR #95), weryfikacja PR env w `szczur-radar-pr-95` (2026-10-23), produkcja kroki 0–6 (#66, zamknięte); pola listy PR env odhaczone z odnośnikiem do komentarzy #66.
- **Decision**: FIXED — status „done (2026-10-24)” z merge'em, PR env 95 i produkcją; 12 pól listy PR env odhaczonych; krok Record wskazuje #66.

### F2 — Postęp M-2 na wykresie: „4 / 13” obok „6 / 13”

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: tools/render_roadmap_svg.py:334, :421
- **Detail**: Węzeł osi czasu i pasek M-2 pokazują „4 / 13”, a karta postępu i legenda (`:284`, `:291`) — „6 / 13”, „6 ukończone” (zgodnie z `roadmap.md`: S-08, S-09, S-10, S-14, S-15, S-19). Kontrola 4.11 objęła tylko dane F-04 i datę.
- **Fix**: Przy archiwizacji F-04 (F-04 → done, 7 / 13) ustawić jedną wartość we wszystkich czterech miejscach i wygenerować SVG.
- **Decision**: FIXED — przy archiwizacji F-04 (patrz commit archiwizacji).

### F3 — Opis fixture'ów mówi „według dokumentacji”

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: tools/healthchecks/test.sh:5, tools/healthchecks/README.md:13
- **Detail**: Oba miejsca mówią, że `fixtures/` są zbudowane według dokumentacji („do zastąpienia nagranymi”), a `fixtures/README.md` i commit `a778d44` — że są nagrane z prawdziwego API 2026-10-23.
- **Fix**: Oba zdania: „nagrane z prawdziwego API zarządzania (`fixtures/README.md`)”.
- **Decision**: FIXED — oba zdania mówią o nagraniu z prawdziwego API.

### F4 — Wzorzec adresu w cronach dopuszcza `?` i `#`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/pipeline-trigger/trigger.sh:52, tools/db-backup/backup.sh:48
- **Detail**: `ping_pattern` zawiera `?` i `#`, a meldunek to `"%s/%s"` (`trigger.sh:68`, `backup.sh:64`). Przy adresie z zapytaniem albo fragmentem `/start` i `/<kod>` trafiłyby do zapytania lub fragmentu, więc każdy meldunek byłby gołym pingiem sukcesu, także porażka. `AlertSettings.java:53-55` odrzuca zapytanie i fragment, więc obie strony się różnią. Tylko przy ręcznie przerobionej wartości (`checks.sh` drukuje goły `ping_url`).
- **Fix**: Usunąć `?` i `#` z obu `ping_pattern` (i z testów, jeśli je przypinają), zgodnie z regułą Javy.
- **Decision**: FIXED — `?` i `#` usunięte z obu `ping_pattern`, komunikat „without ? or #”; przypadek `ping-query` w `tools/pipeline-trigger/test.sh` (break-check: czerwony ze starym wzorcem).

### F5 — `set -e` w wrapperze może pominąć meldunek końca

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/pipeline-trigger/trigger.sh:84, :95, :98; tools/db-backup/backup.sh:80, :89, :91
- **Detail**: `mktemp -d`, `log … | tee -a` (z pipefail) i `last_line … >body` działają przy `set -e`. Gdy `/tmp` jest pełne lub zepsute, cron kończy się kodem awk/tee zamiast kodu przebiegu i bez meldunku końca — wbrew „meldunek nigdy nie zmienia wyniku crona”. Teoretyczne.
- **Fix**: `|| true` przy `tee -a` i `last_line`; przy nieudanym `mktemp` przebieg bez kopii logu (meldunek bez treści).
- **Decision**: FIXED — nieudany `mktemp` daje ostrzeżenie i meldunki bez treści; `tee -a` i `last_line` z `|| true`; przypadek `ping-no-tmp` w `tools/pipeline-trigger/test.sh`.

### F6 — `last_line` przycina 1000 bajtów, nie znaków

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/pipeline-trigger/trigger.sh:77, tools/db-backup/backup.sh:73
- **Detail**: `substr` w busybox awk (alpine) i mawk (obraz db-backup) liczy bajty (`substr("ąąą",1,1)` → `\304`), więc długa linia spoza ASCII może być ucięta w środku znaku UTF-8; komentarz mówi „characters”. Strona Javy (`HealthchecksAlerts.body`) robi to poprawnie. Linie logu są prawie zawsze ASCII.
- **Fix**: Poprawić komentarz na „bajtów” (limit 1000 bajtów mieści się w limicie healthchecks.io).
- **Decision**: FIXED — komentarz: 1000 bajtów, możliwy ucięty znak spoza ASCII.

### F7 — Etykieta środowiska: `pr-<N>` w runbooku, `szczur-radar-pr-<N>` w README

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md (sekcja F-04, Checks), tools/healthchecks/README.md:28
- **Detail**: Plan i runbook używają `checks.sh apply pr-<N>`, README i prawdziwa weryfikacja — `szczur-radar-pr-95` / `szczur-radar-pr-99`. Etykieta jest dowolnym tekstem, ale `delete` musi dostać tę samą.
- **Fix**: Jedna forma w runbooku: nazwa środowiska Railway `szczur-radar-pr-<N>`.
- **Decision**: FIXED — sekcja F-04 runbooka używa nazwy środowiska Railway `szczur-radar-pr-<N>` (15 miejsc).

### F8 — Nadawca API: ograniczenia, o których musi wiedzieć S-18

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/HealthchecksAlerts.java:86-90, AlertsConfiguration.java:19-21
- **Detail**: (1) `report()` na przerwanym wątku zwraca `FAILED` bez wysyłki (test `anInterruptIsFailedAndTheFlagIsKept`), a `PipelineRunService.java:341` i `RunWatchdog.java:106` używają `shutdownNow()` — zdarzenie z workera przy zamknięciu nie wyjdzie. (2) Wysyłka blokuje wywołującego do ~2 × (5 s + 10 s). (3) Bean jest tylko w serwerze (`@ConditionalOnWebApplication`), więc bean wspólny z kontekstem komend, który wstrzyknie `OperatorAlerts`, zepsuje `migrate`. W F-04 nic tego nie woła; to wejście dla S-18.
- **Fix**: Dopisać trzy punkty do notatki S-18 w `roadmap.md` (albo do przyszłego `research.md` S-18): zdarzenia z przerwanych wątków, czas blokowania, `ObjectProvider<OperatorAlerts>` lub wywołujący też web-only.
  - Strength: S-18 dostaje ograniczenia nadawcy przed planowaniem, zamiast odkryć je w kodzie.
  - Tradeoff: Edycja roadmapy poza zamknięciem F-04 (jeden punkt w istniejącej notatce).
  - Confidence: HIGH — każde z trzech potwierdzone w kodzie.
  - Blind spot: Nie sprawdzono, które ścieżki ERROR S-18 faktycznie biegną na przerywanych wątkach.
- **Decision**: FIXED — notatka „przegląd implementacji F-04” w S-18 (`roadmap.md`).

### F9 — Ostatnia linia logu crona wychodzi poza Railway

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/backup.sh:91-92, tools/pipeline-trigger/trigger.sh:98-99
- **Detail**: Treść meldunku końca trafia do healthchecks.io i Telegrama. Gdy `set -e` przerywa `backup.sh`, to surowy stderr `pg_dump`/`rclone`/`psql` — może zawierać nazwę hosta, użytkownika lub bucketu, nigdy hasła ani kluczy (istniejące logowanie ich unika). Nowy przepływ danych na zewnątrz, nigdzie nie zapisany jako zaakceptowany.
- **Fix**: Zdanie w `infrastructure.md` (healthchecks.io): treść meldunku = ostatnia linia logu crona, bez sekretów, może zawierać nazwy hosta/bucketu.
- **Decision**: FIXED — zdanie o treści meldunku w `infrastructure.md` (healthchecks.io), zaakceptowane.

### F10 — Drobiazgi w `send-test-alert` i nadawcy

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/cli/SendTestAlertCommand.java:87, alerts/HealthchecksAlerts.java:69, alerts/AlertsConfiguration.java:27
- **Detail**: (1) Komenda działa bez Springa, a w `src/main/resources` nie ma `logback.xml`, więc jej WARN idzie domyślnym Logbackiem (DEBUG, stdout, inny format), a linia wyniku na stderr. (2) `HealthchecksAlerts.report(AlertEvent, @Nullable String)` przy `OperatorAlerts.report(AlertEvent, String)` — bez `@NullMarked` nic tego nie wymusza. (3) `Environment::getProperty` rozwija `${…}`, więc wartość z nierozwiązywalnym `${x}` dałaby błąd startu cytujący wartość — prawdziwy adres pingu nie zawiera `${`. Wszystko kosmetyczne lub teoretyczne.
- **Fix**: Bez zmian teraz; (2) wyrównać przy pierwszej edycji pakietu w S-18.
- **Decision**: FIXED — bez zmian w kodzie; (2) w notatce S-18 razem z F8.
