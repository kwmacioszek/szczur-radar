# F-04: kanał alarmowy poza aplikacją — Plan Brief

> Full plan: `context/changes/out-of-app-alert-channel/plan.md`
> Frame brief: `context/changes/out-of-app-alert-channel/frame.md`

## What & Why

> **Rzeczywisty problem**: nikt poza aplikacją nie oczekuje nocnego przebiegu ani backupu, więc ich
> brak jest niewidoczny, a API nie ma żadnej drogi, by wyprowadzić zdarzenie poza proces — sam kanał
> „do wysyłki” rozwiązuje tylko drugą połowę.

F-04 daje oba brakujące elementy: zewnętrznego obserwatora meldunków cronów (healthchecks.io) z
alarmem w Telegramie oraz nadawcę zdarzeń w API, który S-18 podłączy do konkretnych błędów.

## Starting Point

Jedyny alarm poza aplikacją to ręczna reguła konta Railway „deployment crashed” (tylko crash crona,
sprawdzona tylko w testach). Noc, w której cron nie wystartował, utknął w QUEUED albo został
podmieniony w trakcie merge'a, nie daje żadnego sygnału. API usypia i kończy błędy logiem albo
wpisem w dzwonku.

## Desired End State

Brak meldunku `pipeline-trigger` do ~03:45 UTC albo `db-backup` do ~04:15 UTC daje wiadomość w
Telegramie; porażka crona — od razu, z jego ostatnią linią logu; powrót — wiadomość „up”. Komenda
`send-test-alert` daje w Telegramie wiadomość z opisem i zaraz „up”. Awaria healthchecks.io nigdy nie
zmienia wyniku crona ani API. Nic nie budzi produkcji, koszt $0.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Rodzaj sygnału | Oczekiwany meldunek (dead-man's switch) + zdarzenie z API | Kanał „do wysyłki” nie wykryje nocy, której nie było | Frame |
| Koszt i usypianie | Tylko sygnały wychodzące, bez zegarów w API, darmowe plany | „Nie chcę przez to nabić sobie kosztów” | Frame |
| Kanał | healthchecks.io → Telegram (@HealthchecksBot) | Jedna usługa decyduje o alarmie, bez własnego bota | Plan |
| Zakres API | Nadawca + `send-test-alert` w F-04; ścieżki ERROR w S-18 | Cel F-04 „API może wysłać” bez podłączania błędów | Plan |
| Zdarzenie z API | `/fail` z opisem i od razu sukces | Healthchecks alarmuje tylko przy zmianie stanu — inaczej drugi błąd przepada | Plan |
| Crash crona | Meldunek kodu wyjścia od razu, z ostatnią linią logu | Każda zła noc w jednym miejscu, niezależnie od reguły Railway | Plan |
| Checki | Skrypt `tools/healthchecks/checks.sh` z API zarządzania, klucz u operatora | Konfiguracja w repo, klucz poza CI i Railway | Plan |
| Weryfikacja | Testowe checki w PR env przed merge'em | Prawdziwy meldunek i Telegram bez dotykania produkcji | Plan |
| Sekret | `HEALTHCHECK_PING_URL` per usługa, `preserve()`, zapieczętowany na produkcji | PR envy nie kopiują zapieczętowanych wartości | Plan |
| Uzbrojenie checków | `checks.sh apply` wysyła pierwszy meldunek i sprawdza stan „up” | Check w stanie „new” nigdy nie alarmuje o braku meldunku | Plan review |
| Zła wartość w API | Zatrzymuje start serwera, nie pre-deploy `migrate` (`@ConditionalOnWebApplication`) | Jak `PIPELINE_STALL_TIMEOUT`; migracje nie zależą od kanału alarmowego | Plan review |

## Scope

**In scope:**
- Meldunki `/start` i `/<kod>` w wrapperach obu cronów, `curl` w obrazie backupu, testy na stubie
- Pakiet `alerts` w API, walidacja ustawienia, komenda `send-test-alert`, testy ukrywania adresu
- `tools/healthchecks/` (skrypt, testy, job CI), `preserve()` na trzech usługach
- Weryfikacja w PR env, runbook F-04, PRD, infrastruktura, roadmapa + SVG, wytyczne

**Out of scope:**
- Ścieżki ERROR i log `/api/ready` (S-18); dni bez danych (S-13)
- Własny bot Telegrama; zmiany w `deploy-verification-hardening`; usunięcie reguły Railway
- NAS → Google Drive; alarm o awarii samego healthchecks.io; checki w CI lub IaC Railway

## Architecture / Approach

```
cron wrapper ──/start, /<kod> + ostatnia linia──▶ healthchecks.io ──▶ Telegram
API (gdy i tak pracuje) ──/fail + opis, potem sukces──▶ check api-events ──▶ Telegram
healthchecks.io: brak meldunku po harmonogramie + karencji ──▶ Telegram
```

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Meldunki cronów | Start i kod wyjścia z obu cronów, testy na stubie | Meldunek zmieniający wynik crona albo wyciek adresu |
| 2. Zdarzenia z API | Nadawca, ustawienie, `send-test-alert` | Adres w logu lub wyjątku; nadawca blokujący wywołującego |
| 3. Checki i IaC | `checks.sh`, testy w CI, `preserve()` | Zmienna połknięta przez `ignore` albo zablokowany plan IaC |
| 4. PR env i dokumenty | Scenariusze w Telegramie, runbook, fundament | Fałszywy alarm przez za krótką karencję |

**Prerequisites:** konto healthchecks.io z projektem i podpiętym Telegramem; klucz API zarządzania
(read-write) u operatora; PR env tej zmiany.
**Estimated effort:** ~3 sesje; fazy 1 i 2 największe.

## Open Risks & Assumptions

- Karencja 3 h 15 min opóźnia alarm braku nocy do ~03:45 UTC; krótsza dałaby fałszywe alarmy przy długim przebiegu.
- Awaria healthchecks.io albo Telegrama jest cicha (brak potwierdzenia w dokumentacji).
- Crash crona daje dwa alarmy (Railway i Telegram), po `deploy-verification-hardening` trzy.
- Telegram na planie darmowym potwierdzony w kodzie źródłowym, nie na stronie cennika.
- Zmiana `TRIGGER_TIMEOUT` / `BACKUP_TIMEOUT` bez `checks.sh apply` z nową karencją da fałszywy alarm.

## Success Criteria (Summary)

- Operator dostaje w Telegramie wiadomość o nocy lub backupie, które się nie odbyły albo padły.
- `send-test-alert` dociera do Telegrama; S-18 ma gotowego nadawcę.
- Produkcja śpi jak dotąd, a koszt nie rośnie.
