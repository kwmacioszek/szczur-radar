# Podsumowanie nocy w ntfy zamiast alarmu o martwym poście (S-34) — Plan Brief

> Full plan: `context/changes/dead-posts-daily-digest/plan.md`

## What & Why

Przebieg `partial` z martwym postem daje dziś wiadomość `RUN_NEEDS_ATTENTION` w Telegramie. Po włączeniu Reddita zdarza się to w ok. 3 nocach na 4, więc alarm przestaje znaczyć „zareaguj” (PRD v3, Q-14). Właściciel chce przy tym rozdzielić role kanałów: healthchecks.io ma obserwować produkcję, a powiadomienia mają iść osobnym kanałem, czyli ntfy.

## Starting Point

API zgłasza zdarzenia przez `alerts/OperatorAlertQueue` do checka healthchecks.io `api-events` (para down/up → Telegram). Reguła „wymaga uwagi” (`PipelineRun.attentionReasons`) jest jedna dla dzwonka, strony przebiegu i alarmu. API nie ma zegara, a co noc budzi je cron `pipeline-trigger`.

## Desired End State

Wszystkie zdarzenia API przychodzą do operatora przez ntfy (temat w sekrecie `NTFY_URL`), a crony i deploy-health dalej przez healthchecks.io → Telegram. Przebieg z samym martwym postem jest widoczny tylko w dzwonku. Co noc, po przebiegach crona, operator dostaje w ntfy podsumowanie nocy: dla każdego źródła pobrane, nowe, resztki, ocenione, nieudane, martwe i tokeny, do tego martwe posty z przebiegów admina od poprzedniej nocy.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kto wyzwala podsumowanie | Cron po końcu swoich przebiegów wywołuje `POST /internal/pipeline-runs/nightly-summary` | Podsumowanie jest świeże, a API nie potrzebuje zegara, który nie pozwoliłby mu spać | Plan |
| Kanał | ntfy dla **wszystkich** zdarzeń API, `api-events` znika; crony zostają w healthchecks.io | Healthchecks.io obserwuje stan, a ntfy powiadamia | Plan (właściciel) |
| Hosting ntfy | ntfy.sh, losowy temat jako sekret `NTFY_URL`, ta sama reguła adresu S-25 | Ten sam model co dzisiejszy sekretny ping URL; krok 9 `test.sh` bez zmian | Plan |
| Token ntfy | Opcjonalny drugi sekret `NTFY_TOKEN` → `Authorization: Bearer`; na produkcji ustawiony | Limity ntfy.sh liczą się per konto zamiast per współdzielone IP egress Railway | Plan review (F3) |
| IaC przed merge'em | `HEALTHCHECK_PING_URL: preserve()` znika z API; operator usuwa zmienną przed merge'em, agent dowodzi tego krokiem 4.9 (`railway variables --json`) | Czysty IaC od razu; CI na PR tego nie pokaże, więc dowód musi być osobny | Plan review (F2, Fix B) |
| Dzwonek | Bez zmian: martwy post dalej „wymaga uwagi” | Reguła zostaje jedna, różni się tylko kanał (ryzyko z roadmapy) | Plan |
| Kiedy wysyłać | Zawsze, także przy 0 martwych i po złej nocy | Właściciel chce codziennego obrazu nocy, nie tylko alarmu | Plan (właściciel) |
| Treść | Linia nocy + linia na każde źródło (pobrane, pominięte, nowe, resztki, ocenione, nieudane, martwe, tokeny) + martwe poza nocą + `/admin` | Operator widzi całą noc w jednej wiadomości | Plan (właściciel) |
| Koszt | Same tokeny (wejście, cache, wyjście), bez $ | Dokładne, zero konfiguracji cen | Plan (właściciel) |
| Martwe poza nocą | Posty zabite przez przebiegi admina utworzone między poprzednią a tą nocą, z `pipeline_runs`, bez tabeli | Każdy martwy post trafia do dokładnie jednego podsumowania bez migracji | Plan |
| Alarm z martwym postem + inny powód | Dalej `RUN_NEEDS_ATTENTION` jak dziś | Zmienia się tylko przypadek, gdy jedynym powodem jest `dead_posts` | Plan |

## Scope

**In scope:**
- `NtfyAlerts` zamiast `HealthchecksAlerts`, `NTFY_URL` i opcjonalny `NTFY_TOKEN`, `send-test-alert` przez ntfy
- usunięcie `api-events` z `checks.sh`, `NTFY_URL: preserve()` w `railway.ts`
- filtr alarmu dla samego martwego posta w `RunNotifications`
- `NightSummary`, `AlertEvent.NIGHT_SUMMARY`, nowa trasa `/internal/`
- wywołanie podsumowania w `trigger.sh` i jego test
- PRD, roadmapa i SVG, runbook S-34, wytyczne, weryfikacja w PR env

**Out of scope:**
- koszt w $, ceny modeli
- zmiany SPA, dzwonka i `rules.json`
- przeniesienie cronów i deploy-health z healthchecks.io
- własny serwer ntfy, rezerwacja tematu (ACL), kliknięcia
- nowe tabele i migracje
- FR-034
- ponowienia i trwała kolejka zdarzeń

## Architecture / Approach

Cron: `/daily` (202, id przebiegów) → odpytywanie do końca → `POST /internal/pipeline-runs/nightly-summary {runs}`. API składa tekst z `pipeline_run_sources`, `assessments` (z `posts.source`) i liczników przebiegów, podnosi `NIGHT_SUMMARY` przez `OperatorAlertQueue` (w kontrolerze przez `ObjectProvider`, bo komendy operatora dzielą kontekst) → `NtfyAlerts` → `POST https://ntfy.sh/<temat>` (z `Authorization: Bearer`, gdy jest `NTFY_TOKEN`) i zwraca tekst. Cron loguje go przed swoją linią końcową, więc check-in w healthchecks.io dalej niesie `OK: …` albo błąd.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Kanał ntfy dla zdarzeń API | Zdarzenia API w ntfy (z tokenem), `api-events` usunięty z kodu i fixture'ów `test.sh`, IaC | Plan IaC na `main` odrzuci `variable.delete`, jeśli operator nie usunie zmiennej przed merge'em; CI na PR tego nie pokaże (dowód: 4.9) |
| 2. Podsumowanie nocy w API | Filtr alarmu, `NightSummary`, trasa `/internal/`, `NIGHT_SUMMARY` | Poprawne okno „poza nocą” i liczby per źródło z ocen dwóch przebiegów |
| 3. Cron wysyła podsumowanie | `trigger.sh` wywołuje trasę na każdej ścieżce po 202 | Linia końcowa (treść check-inu) przestałaby być ostatnią |
| 4. Dokumenty i weryfikacja w PR env | PRD, runbook, wytyczne, roadmapa; wiadomości odczytane z testowego tematu | Żywe źródła w PR env mogą nie dać martwego posta, więc ta ścieżka jest dowiedziona tylko w IT |

**Prerequisites:** brak zależności z roadmapy. Do weryfikacji w PR env potrzebny jest testowy temat ntfy, który ustawia agent. Przed merge'em operator zakłada konto ntfy.sh z tokenem, ustawia `NTFY_URL` i `NTFY_TOKEN` na produkcji i usuwa z API `HEALTHCHECK_PING_URL` (`--skip-deploys`); agent dowodzi tego krokiem 4.9.
**Estimated effort:** ~3–4 sesje w 4 fazach.

## Open Risks & Assumptions

- Temat ntfy.sh jest publiczny dla znającego nazwę: treść nie ma sekretów (zasada S-18), ale tytuły zdarzeń i liczby nocy może przeczytać każdy, kto zna temat.
- Awaria ntfy.sh jest cicha, tak jak dziś awaria healthchecks.io. Brak podsumowania rano to jedyny sygnał; martwe posty nocy, której podsumowanie przepadło (kolejka nietrwała, bez ponowień), nie wracają w żadnym późniejszym podsumowaniu — widzi je tylko dzwonek.
- Między usunięciem `HEALTHCHECK_PING_URL` a deployem merge'a stary kod produkcji wysyła do healthchecks.io z wartości w pamięci, pod warunkiem że zmiana zmiennej nie zrestartuje API (`--skip-deploys`); restart w tym oknie = cisza kanału do deployu. PR envy nie mają zapieczętowanej wartości, więc CI na PR nie sprawdzi, czy operator usunął zmienną — robi to krok 4.9.
- Limity ntfy.sh liczą się per „visitor”: bez tokenu per IP, a API wychodzi przez współdzielone IP egress Railway; `NTFY_TOKEN` przenosi limity na konto (250 wiadomości/dzień, kolejka wysyła najwyżej 2/min). 429 i zły token kończą się `FAILED` z WARN-em w logu.

## Success Criteria (Summary)

- Przebieg z samym martwym postem nie daje żadnej wiadomości operatorowi, a dzwonek pokazuje go jak dotąd.
- Co noc, także przy 0 martwych, w ntfy przychodzi jedno podsumowanie z liczbami i tokenami każdego źródła.
- Pozostałe zdarzenia API przychodzą przez ntfy, a crony dalej meldują się w healthchecks.io.
