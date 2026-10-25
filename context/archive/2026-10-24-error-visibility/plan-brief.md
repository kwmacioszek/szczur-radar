# S-18: błędy wymagające reakcji docierają do operatora — Plan Brief

> Full plan: `context/changes/error-visibility/plan.md`

## What & Why

Kilka ścieżek błędów w API kończy się dziś samą linią ERROR w logu Railway, którego nikt nie czyta: nieudane powiadomienie, odzyskiwanie przy starcie, zamknięcie przebiegu, watchdog, sprzątanie sierot i zapis licznika (A5). Awaria samej ścieżki sygnału jest więc cicha. Do tego `partial` z martwym postem i przebieg admina `failed` dają tylko dzwonek.

S-18 kieruje je do Telegrama przez kanał F-04, nie zalewając operatora. Domyka też dwa ustalenia audytu: klucz LLM w łańcuchu przyczyn wyjątków (B7) i 503 z `/api/ready` bez przyczyny w logu (C10).

## Starting Point

F-04 dał synchronicznego nadawcę `alerts/OperatorAlerts` (healthchecks.io → Telegram) z jednym zdarzeniem testowym. Nadawca blokuje do ~30 s, z przerwanego wątku nic nie wysyła, a jego bean istnieje tylko w serwerze. Dzwonek (`RunNotifications.runEnded`) widzi każdy koniec przebiegu i jest idempotentny. Klienci LLM czyszczą komunikat, ale zostawiają surowy wyjątek SDK jako przyczynę.

## Desired End State

Operator dostaje w Telegramie jedno zdarzenie, czyli wiadomość „down” z opisem i zaraz po niej „up”, które tylko uzbraja check. Dzieje się to, gdy:
- przebieg wymaga uwagi (wyjątek: nocny `failed`/`interrupted`, który zgłasza już cron);
- zawiedzie ścieżka sygnału albo kolejki;
- wyniki postów nie wejdą do liczników przebiegu.

To samo zdarzenie dla tego samego przebiegu przychodzi najwyżej raz na 10 min, łącznie najwyżej 2 na minutę. Zgłoszenie nigdy nie blokuje pracy. Wydruk żadnego wyjątku z klientów LLM nie zawiera klucza. 503 z `/api/ready` ma przyczynę w logu.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Sposób wysyłki | Web-only kolejka z własnym wątkiem, 10 min na zdarzenie i przebieg, ≤2/min | Wywołujący nie czeka, zdarzenie z wątku przerwanego przy zamykaniu wychodzi, limit healthchecks.io jest chroniony |
| Zakres alarmów | Ścieżki dziś nieme + każdy przebieg z powiadomieniem w dzwonku | Operator nie musi zaglądać do dzwonka; jedna wiadomość na przebieg, nie na post |
| Dubel crona | Nocny `failed`/`interrupted` bez alarmu API | Cron już zgłasza go kodem wyjścia; jedna wiadomość na jedno zdarzenie |
| Catch-all | Tylko jawne zdarzenia `AlertEvent`, bez appendera Logback | Każdy alarm przemyślany, treść kontrolowana, zgodnie z projektem F-04 |
| A5 | Jedno zdarzenie `RUN_COUNTERS_LOST` na przebieg, bez naprawy liczników | Roadmapa każe uczynić to widocznym; model liczenia S-06 bez zmian |
| `/api/ready` | WARN z przyczyną, bez alarmu | Po deployu alarmuje monitor; alarm na 503 zalałby operatora przy budzeniu API |
| B7 | Oczyszczona kopia łańcucha przyczyn z oryginalnym stack trace'em | Każdy obecny i przyszły log jest bezpieczny, ślad SDK zostaje do diagnozy |

## Scope

**In scope:**
- 7 nowych zdarzeń `AlertEvent`, kolejka `OperatorAlertQueue`;
- podłączenie w `RunNotifications`, `PipelineStartupRecovery`, `RunWatchdog`, `PipelineRunService` (z `catch` w `onClose`) i `ConcurrentAssessmentDispatcher`;
- `PipelineRun.scheduled()`;
- `cleanCause` w trzech klientach LLM;
- WARN w `/api/ready`;
- dokumenty, doprecyzowanie w PRD, sekcja w `deploy-plan.md`, weryfikacja w PR env.

**Out of scope:**
- appender Logback i alarm z każdego ERROR;
- osobne alarmy upadku przebiegu, checku LLM i błędu posta;
- naprawa liczników;
- alarm z `/api/ready` i log 503 `/api/deploy-safety`;
- error tracker i metryki, nowe zmienne i migracje, trwała kolejka;
- A6/S-12, S-13.

## Architecture / Approach

```
ścieżka błędu ─raise(event, runId, detal)─► OperatorAlertQueue ──wątek operator-alerts──► OperatorAlerts
  (nie blokuje, nie rzuca;                 (dedup 10 min, ≤2/min,                       (HealthchecksAlerts:
   ObjectProvider w beanach wspólnych z CLI) kolejka 20, dopróżnienie 5 s)               /fail + ok → Telegram)
```

Alarm „wymaga uwagi” wisi na `runEnded` (`created > 0`). B7 jest naprawiane u źródła: przyczyna dołączana przez klienta to zawsze `CleanedProviderException`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Nadawca w tle | Kolejka z deduplikacją, limitem i dopróżnieniem; nowe zdarzenia | Zamykanie: kolejność `destroy()` beanów i nieznane okno SIGTERM→SIGKILL |
| 2. Podłączenie ścieżek | Alarmy „wymaga uwagi” i awarie ścieżek sygnału, A5 raz na przebieg | Kontekst CLI bez beana; dubel crona zależy od kontraktu crona |
| 3. Klucze i 503 | Łańcuch przyczyn bez klucza w 3 klientach, WARN w `/api/ready` | Pominięte miejsce dołączające surową przyczynę (test na klienta) |
| 4. Dokumenty i PR env | CLAUDE.md, guidelines, PRD, deploy-plan; prawdziwy alarm w PR env | Testowy check PR env wymaga klucza operatora |

**Prerequisites:** F-04 na produkcji (`HEALTHCHECK_PING_URL` zapieczętowany); operator z `HEALTHCHECKS_API_KEY` do testowego checka PR env.
**Estimated effort:** ~2–3 sesje w 4 fazach.

## Open Risks & Assumptions

- Zdarzenie w kolejce ginie przy SIGKILL. Dopróżnienie trwa najwyżej 5 s, a okno Railway jest nieznane. Dzwonek i log zostają.
- Reguła „pomiń dubel crona” zakłada kontrakt crona `failed`/`interrupted` → kod ≠ 0 (`trigger.sh`). Zmiana tam wymaga zmiany tu (zapis w `pipeline/CLAUDE.md`). Gdy cron odpuści wcześniej (90 min, API nieosiągalne), jego exit 1 mówi tylko „not ended”, a późniejszy wynik przebiegu jest tylko w `/admin`.
- Błąd przebiegu w treści wiadomości to ten sam tekst co w `/admin`. Komunikaty z bazy mogą zawierać nazwy hosta albo tabel, ale nie hasła (jak ostatnia linia crona z F-04).
- Awaria bazy przy bezczynnym API nie daje alarmu, dopóki nie ruszy przebieg albo cron.

## Success Criteria (Summary)

- Przebieg admina za `2004-01-01` (sprzed Wykopu: 0 elementów → `failed`, bez LLM) w PR env daje wiadomość `RUN_NEEDS_ATTENTION` w Telegramie z id przebiegu i odnośnikiem.
- Każda ścieżka awarii ma test, który dowodzi jednego zgłoszenia; nocny `failed` nie daje dubla.
- Testy klientów LLM dowodzą, że klucza nie ma w wydruku łańcucha przyczyn.
