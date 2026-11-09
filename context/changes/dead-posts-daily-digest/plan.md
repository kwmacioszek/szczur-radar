# Podsumowanie nocy w ntfy zamiast alarmu o martwym poście (S-34) — plan implementacji

## Overview

Martwy post zdarza się dziś na tyle często (Reddit: ~1,4 dziennie, alarm w ok. 3 nocach na 4 według S-28), że wiadomość `RUN_NEEDS_ATTENTION` w Telegramie przestaje znaczyć „zareaguj” (destylacja #5, R-42, D-13, Q-14; PRD v3, NFR `prd.md:285`). S-34 robi trzy rzeczy:

1. **Kanał zdarzeń API przechodzi z healthchecks.io na ntfy.** Healthchecks.io zostaje do obserwowania stanu (crony, deploy-health), a ntfy staje się kanałem powiadomień operatora: wszystkie `AlertEvent` API, `send-test-alert` i nowe podsumowanie. Check `<env>/api-events` znika.
2. **Przebieg, którego jedynym powodem „wymaga uwagi” jest martwy post, nie daje już wiadomości operatorowi.** Dzwonek i strona przebiegu pokazują go bez zmian, a reguła `attentionReasons` zostaje jedna.
3. **Operator dostaje co noc podsumowanie nocy**, zawsze, także przy 0 martwych. Podsumowanie podaje dla każdego źródła liczby pobrane, nowe, resztki, ocenione, nieudane, martwe i tokeny, a do tego martwe posty z przebiegów admina od poprzedniej nocy. Wysyła je API przez ntfy, gdy nocny cron po swoich przebiegach wywoła nową trasę `/internal/`.

## Current State Analysis

- **Reguła „wymaga uwagi” jest jedna**: `PipelineRun.attentionReasons` (`szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java:321-347`, `DEAD_POSTS` przy `partial && dead > 0`, :334). Korzystają z niej dzwonek, strona przebiegu, lista `/admin` i alarm.
- **Alarm operatora**: `RunNotifications.notifyAdmins` (`notifications/RunNotifications.java:103-116`) podnosi `AlertEvent.RUN_NEEDS_ATTENTION` przez `OperatorAlertQueue`, gdy powstały powiadomienia (`created > 0`). Wyjątek to przebieg crona zakończony `failed`/`interrupted` (:119-121). Format szczegółu jest przypięty testami `RunNotificationsTest.java:86-88,111-120,139,153,207` i `NotificationsIT.java:97-108`.
- **Kanał**: `alerts/OperatorAlerts` (port, `Delivery` DELIVERED/FAILED/NOT_CONFIGURED) → `HealthchecksAlerts`, czyli `POST <url>/fail` z treścią ucinaną do 1000 znaków, a potem `POST <url>` „ok” (`alerts/HealthchecksAlerts.java:17-41`). Bean wybiera `AlertsConfiguration` (tylko serwer, `@ConditionalOnWebApplication`) na podstawie `AlertSettings` (`HEALTHCHECK_PING_URL`, `alerts/AlertSettings.java:43`). Kolejka `OperatorAlertQueue` ma klucz deduplikacji `(event, runId)` w oknie 10 min, limit `MAX_PER_MINUTE = 2` (uzasadniony limitem 5 pingów/min healthchecks.io), `CAPACITY = 20` i drenaż 5 s (`alerts/OperatorAlertQueue.java:56-67`).
- **Reguła S-25**: `AlertSettings.PING_PATTERN` / `PING_MAX_LENGTH` to ten sam literał co w `tools/db-backup/backup.sh`, `tools/pipeline-trigger/trigger.sh` i `tools/healthchecks/checks.sh`. Pilnuje tego krok 9 `tools/healthchecks/test.sh:449-503`, a tabela przypadków `tools/healthchecks/fixtures/ping-urls.txt` służy też `AlertSettingsTest`.
- **Checki**: `tools/healthchecks/checks.sh:38` (`services=(pipeline-trigger db-backup api-events)`) i production-only `deploy-health`. IaC: `HEALTHCHECK_PING_URL: preserve()` na API (`.railway/railway.ts:192`) i na obu cronach (:258, :296).
- **API nie ma zegara** z założenia (F-04, `context/deployment/deploy-plan.md:1210`): śpi na Railway. Jedynym codziennym wybudzeniem jest cron `pipeline-trigger` (00:30 UTC). Cron `POST /internal/pipeline-runs/daily` dostaje 202 z `[{id, kind}]` (RETRY najpierw, gdy są resztki), odpytuje `GET /internal/pipeline-runs/{id}` co 30 s do `TRIGGER_RUN_TIMEOUT` 90 min i kończy linią `OK: retry partial, range partial` albo błędem. Ostatnią niepustą linię logu wysyła w check-inie (`tools/pipeline-trigger/trigger.sh:87,114-115,272-347`). Kontroler: `pipeline/api/InternalPipelineRunController.java:65-83`.
- **Dane do podsumowania** (bez migracji):
  - Wynik źródła w przebiegu zakresu: `pipeline_run_sources` (status, fetched, skipped, error; V12).
  - Nowe posty: `posts.first_run_id`.
  - Oceny: `assessments.run_id NOT NULL` (V3:133) z `status`, `input_tokens`, `cached_tokens`, `output_tokens` (V3:153-155); źródło przez `posts.source`.
  - Martwy post: `posts.stage = 'dead'`. Zabił go przebieg ostatniej oceny posta (`AssessmentRepository.findLatest`, `posts/AssessmentRepository.java:25-35`), a `posts.updated_at` po śmierci już się nie zmienia (`pipeline/state/PostTransitions.java:91-109`).
  - Liczniki przebiegu (`dead`, `processingErrors`, `skipped`…) są w `RunState` (`InternalPipelineRunController.java:104-107`). Błędów technicznych nie da się rozbić na źródła: istnieje tylko licznik przebiegu, a per post `posts.technical_failures`.
  - Przebieg crona: `PipelineRun.scheduled()` (requester `SystemAccount.SCHEDULER`).
- **Cen nie ma w kodzie produkcyjnym.** Ceny są tylko w `src/test/.../measurement` i `fixtures/llm/models.json`. Produkcja: OpenRouter `openai/gpt-6-luna`, flex.
- **ntfy.sh** (docs.ntfy.sh/publish): `POST https://ntfy.sh/<temat>` z treścią do 4096 bajtów (dłuższa staje się załącznikiem); nagłówki `Title` (≤1 KB), `Priority` 1–5, `Tags` (≤512 B). Limity ntfy.sh: 250 wiadomości dziennie, kubełek 60 żądań odnawiany jedno na 5 s — liczone **per „visitor”**, czyli per adres IP bez uwierzytelnienia, a per konto z tokenem (`Authorization: Bearer tk_…`). API na Railway wychodzi przez współdzielone IP egress, więc bez tokenu dzieli kubełek z każdym innym najemcą Railway piszącym do ntfy.sh; objawem byłoby 429 → `FAILED` → jedna linia WARN. Temat jest publiczny dla każdego, kto zna nazwę, więc losowa nazwa jest sekretem.

## Desired End State

- **Kanał API**: `NTFY_URL` (sekret, zapieczętowany na produkcji, `preserve()` w IaC) wskazuje temat ntfy; opcjonalny `NTFY_TOKEN` (drugi sekret, `preserve()`) to token dostępu konta ntfy.sh, dodawany jako `Authorization: Bearer`, dzięki czemu limity liczą się per konto, nie per współdzielone IP Railway. API wysyła każde `AlertEvent` jako **jedną** wiadomość ntfy: tytuł z nazwą zdarzenia, treść `<description>: <detail>` ucięta do 4096 bajtów UTF-8, priorytet według zdarzenia. Bez `NTFY_URL` nic nie wysyła (`NoOperatorAlerts`); `NTFY_TOKEN` bez `NTFY_URL` to błąd ustawień. `send-test-alert` sprawdza kanał API → ntfy. API nie czyta już `HEALTHCHECK_PING_URL`, a `checks.sh` nie zakłada `api-events`. Crony meldują się w healthchecks.io jak dotąd (Telegram), a `deploy-health` się nie zmienia.
- **Martwy post**: przebieg z `attentionReasons() == [DEAD_POSTS]` daje adminom powiadomienie w aplikacji jak dziś, ale nie podnosi `RUN_NEEDS_ATTENTION`. Przebieg z martwym postem **i** innym powodem podnosi je jak dotąd, ze szczegółem `dead N, …`.
- **Podsumowanie nocy**: `POST /internal/pipeline-runs/nightly-summary` z id przebiegów tej nocy składa tekst, podnosi `AlertEvent.NIGHT_SUMMARY` (zawsze, także przy 0 martwych) i zwraca tekst cronowi. Przykład treści (format przypina test):

  ```
  Night 2026-11-08 (range 2026-11-06…2026-11-07): retry partial, range partial; errors 0, skipped 0
  reddit: fetched 104, skipped 0, new 54, leftovers 12; assessed 60, failed 5, dead 1; tokens in 412000 (cached 120000), out 38000
  wykop: fetched 970, skipped 0, new 518, leftovers 3; assessed 515, failed 6, dead 0; tokens in 2210000 (cached 640000), out 190000
  Dead in admin runs since the previous night: reddit 1
  /admin
  ```

  Źródło, które w przebiegu zakresu padło albo nic nie dało, ma w swojej linii status i ucięty błąd zamiast liczb pobrania. Przebieg, który jeszcze trwa, bo cron przestał czekać, ma status `running`.
- **Cron**: po swoich przebiegach, także gdy któryś skończył się `failed`/`interrupted` albo cron przestał czekać, wywołuje trasę podsumowania, loguje zwrócony tekst i dopiero potem pisze swoją linię końcową. Błąd wywołania daje tylko ostrzeżenie: kod wyjścia i treść check-inu zostają.
- **Weryfikacja**: `./mvnw -B verify`, `tools/pipeline-trigger/test.sh`, `tools/healthchecks/test.sh`, `tools/deploy-gate/test.sh`, a w PR env wiadomości odczytane z testowego tematu ntfy (`GET …/json?poll=1`).

### Key Discoveries:

- `RunNotifications.java:109-115`: alarm idzie tylko wtedy, gdy powstały powiadomienia. Filtr „tylko `DEAD_POSTS`” trafia właśnie tu, obok `reportedByTheCron`, a reguła w `PipelineRun` zostaje nietknięta (ryzyko z roadmapy S-34: „różnica dotyczy tylko kanału”).
- `AlertSettings.PING_PATTERN` może zostać regułą adresu `NTFY_URL`. To ten sam kształt (https, nazwa hosta, ścieżka bez `?`/`#`), więc krok 9 `tools/healthchecks/test.sh` i `ping-urls.txt` działają bez zmian, a jedna reguła sekretnych URL-i zostaje jedna.
- Usunięcie `HEALTHCHECK_PING_URL: preserve()` z API w `railway.ts` przy zmiennej obecnej na produkcji sprawiłoby, że plan zaproponowałby `variable.delete`, gate by go odrzucił, a deploy na `main` by stanął. Operator usuwa więc zmienną z API na produkcji **przed** merge'em (Migration Notes).
- `trigger.sh` wysyła w check-inie ostatnią niepustą linię logu (:87). Tekst podsumowania musi więc trafić do logu przed linią końcową `OK: …` / błędem, bo inaczej check-in i Telegram crona dostałyby ostatnią linię podsumowania.
- Rozbicia błędów technicznych na źródła nie ma w danych. Podsumowanie podaje je w linii nocy z liczników przebiegów, a nie per źródło.

## What We're NOT Doing

- Kosztu w dolarach: podsumowanie podaje tylko tokeny (decyzja właściciela). Bez cen w konfiguracji i bez kosztu od dostawcy.
- Zmian reguły `attentionReasons`, dzwonka, strony przebiegu, SPA, `rules.json` i kontraktu web: dzwonek dalej pokazuje martwe posty.
- Przenoszenia cronów (`pipeline-trigger`, `db-backup`) ani `deploy-health` (repo `szczur-radar-monitor`) z healthchecks.io: zostają tam i w Telegramie.
- Własnego serwera ntfy, rezerwacji tematu (ACL), kliknięcia (`Click`) ani linków absolutnych: treść ma ścieżki względne jak dziś (`/admin/runs/<id>`). Token (`NTFY_TOKEN`) jest w zakresie tylko jako nagłówek `Authorization` — bez zarządzania kontem z kodu.
- Nowej tabeli ani migracji: okno „poza nocą” wynika z `pipeline_runs`.
- Zmiany limitów kolejki (`MAX_PER_MINUTE`, `REPEAT_WINDOW`, `CAPACITY`): mieszczą się w limitach ntfy.sh. Zmieniają się tylko opisy, które powołują się na healthchecks.io.
- FR-034 (przywracanie martwych postów): osobny wycinek.
- Ponowień wysyłki ani trwałej kolejki: zdarzenie zgubione przy zabiciu procesu ginie jak dotąd.

## Implementation Approach

Kolejność: najpierw kanał (faza 1), żeby nowe zdarzenie od razu szło przez ntfy. Potem logika w API (faza 2), następnie cron (faza 3), a na końcu dokumenty i weryfikacja end-to-end w PR env (faza 4). Każda faza jest osobno zielona w CI. Faza 2 nie zależy od crona: trasę da się wywołać testem IT z tokenem.

## Critical Implementation Details

- **Kolejność w PR i na produkcji.** `railway.ts` zamienia na API `HEALTHCHECK_PING_URL: preserve()` na `NTFY_URL: preserve()`. Przed merge'em operator ustawia `NTFY_URL` (zapieczętowany) i usuwa `HEALTHCHECK_PING_URL` z `szczur-radar-api` na produkcji, oba z `--skip-deploys` (jak w `deploy-plan.md:183`), bo inaczej `railway-config` na `main` zatrzyma deploy na `variable.delete` (`railway.ts:303-305`, `gate.jq:19`). CI na PR tego **nie** wykryje: PR envy nie mają zapieczętowanej wartości, więc ich plan IaC jest pusty, a kryterium 1.6 dowodzi tylko braku innych zmian. Jedynym dowodem przed merge'em jest kryterium 4.9 (lista kluczy produkcyjnego API przez `railway variables --json`; wartość zapieczętowana jest nieczytelna, liczy się klucz). Do deployu merge'a stary kod produkcji wysyła dalej do healthchecks.io z wartością w pamięci procesu; restart API w tym oknie oznacza ciszę kanału do deployu.
- **Okno „poza nocą”.** Bierzemy martwe posty, których ostatnia ocena należy do przebiegu nie-crona (`scheduled() == false`), utworzonego po ostatnim przebiegu crona sprzed tej nocy i przed najwcześniejszym przebiegiem tej nocy. Każdy przebieg admina leży między dwiema nocami, więc trafia do dokładnie jednego podsumowania, także gdy noc została pominięta (okno obejmuje wtedy dwie doby). Bez poprzedniej nocy okno zaczyna się od początku danych.
- **Linia końcowa crona.** Podsumowanie loguje się przed `OK: …` / `fail …`, także na ścieżce `not ended within` (`trigger.sh:334`). `fail` kończy skrypt, więc wywołanie musi poprzedzić każdą ścieżkę wyjścia po 202.
- **Kolejka w kontrolerze `/internal/`.** `InternalPipelineRunController` nie jest web-only: komendy operatora budują ten sam kontekst bez serwera (`SzczurRadarApiApplication.java:36-39`), a `OperatorAlertQueue` jest beanem tylko serwera (`AlertsConfiguration`, `@ConditionalOnWebApplication`). Bezpośrednia iniekcja zabiłaby każdą komendę, w tym pre-deploy `migrate`. Kontroler bierze `ObjectProvider<OperatorAlertQueue>` i woła statyczne `OperatorAlertQueue.raise(provider, event, runId, detail)`, jak `RunNotifications.java:46,113` i `DeploySafetyController.java:43-45`.

## Phase 1: Kanał ntfy dla zdarzeń API

### Overview

API wysyła swoje zdarzenia do tematu ntfy zamiast do checka healthchecks.io. Zachowanie kolejki, deduplikacji i zasada „nigdy nie rzuca” się nie zmieniają.

### Changes Required:

#### 1. Ustawienie `NTFY_URL`

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertSettings.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/alerts/AlertSettingsTest.java`

**Intent**: Klasa czyta `NTFY_URL` zamiast `HEALTHCHECK_PING_URL`, z tą samą regułą adresu (S-25), tym samym limitem i tym samym nieujawnianiem wartości, oraz opcjonalny token `NTFY_TOKEN`. Komunikat reguły podaje przykład `https://ntfy.sh/<topic>`.

**Contract**: `AlertSettings.NAME = "NTFY_URL"`, `AlertSettings.TOKEN_NAME = "NTFY_TOKEN"`; akcesory zwracają adres tematu i token (`null`, gdy brak). `PING_PATTERN` i `PING_MAX_LENGTH` zostają tymi samymi literałami (krok 9 `tools/healthchecks/test.sh` je czyta). Token: przycięty `SettingText`, 1–256 znaków drukowalnego ASCII bez spacji (kształt `tk_…` nie jest wymagany); inny → `InvalidSettingsException` nazywający tylko zmienną; ustawiony bez `NTFY_URL` → ten sam wyjątek („`NTFY_TOKEN` needs `NTFY_URL`”). Nigdy nie jest logowany ani powtarzany w komunikacie. Javadoc nazywa adres regułą sekretnych adresów https, wspólną z cronami. `AlertSettingsTest` dalej czyta `fixtures/ping-urls.txt` i dostaje przypadki tokenu.

#### 2. Nadawca ntfy

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/NtfyAlerts.java` (nowy; zastępuje `HealthchecksAlerts.java`, który znika razem z `HealthchecksAlertsTest`), `.../alerts/OperatorAlerts.java`, `.../alerts/AlertsConfiguration.java`, `.../alerts/AlertEvent.java`

**Intent**: Jeden raport to jeden `POST` na adres tematu: tytuł z nazwą zdarzenia, treść jak dziś `"<EVENT> (<description>): <detail>"` ucięta bezpiecznie dla surogatów do 4096 bajtów UTF-8, priorytet ze zdarzenia. Te same limity czasu i ta sama zasada logowania (WARN ze zdarzeniem i statusem albo klasą wyjątku, nigdy URL). Ucinanie: przenieść logikę `HealthchecksAlerts.body()` (:105-115, jedyny w API ucinacz z kontrolą high surrogate — nie pisać od zera) do `NtfyAlerts` i rozszerzyć ją z limitu znaków na limit **bajtów**: po zakodowaniu w UTF-8 cofnąć się do granicy punktu kodowego, potem `…`; test z 3- i 4-bajtowym znakiem na granicy.

**Contract**:
- `AlertEvent` dostaje priorytet ntfy (`TEST_SIGNAL` i `NIGHT_SUMMARY` z fazy 2: 3, czyli domyślny; zdarzenia błędów: 4, czyli wysoki).
- Nagłówki `Title: Szczur Radar <EVENT>` (ASCII), `Priority: <n>`, `Content-Type: text/plain; charset=utf-8`; z ustawionym `NTFY_TOKEN` także `Authorization: Bearer <token>`, bez niego nagłówka nie ma.
- `DELIVERED` przy 2xx, `FAILED` przy każdej innej odpowiedzi, błędzie transportu, time-oucie albo przerwaniu (flaga przywrócona). Odpowiedź 429 (limit ntfy) i 401/403 (zły token) kończą się `FAILED` z WARN-em, który nazywa zdarzenie i status — nigdy URL ani token. Javadoc `OperatorAlerts`, `Delivery` i `AlertsConfiguration` mówi o ntfy zamiast o checku i stanie „up”.
- Test `NtfyAlertsTest` na lokalnym serwerze HTTP sprawdza: metodę, ścieżkę, nagłówki (z tokenem i bez), treść UTF-8, ucięcie na granicy bajtów z wielobajtowym znakiem, odpowiedzi 4xx/5xx/429, time-out oraz brak URL i tokenu w logu.

#### 3. Kolejka i komenda testowa

**File**: `.../alerts/OperatorAlertQueue.java`, `.../cli/SendTestAlertCommand.java`, `.../cli/CommandType.java` (:21-22, opis komendy nazywa healthchecks.io i `HEALTHCHECK_PING_URL`), `.../settings/InvalidSettingsException.java` (javadoc :6,15), `szczur-radar-api/src/test/java/pl/szczurradar/api/cli/SendTestAlertCommandTest.java`, `szczur-radar-api/README.md`

**Intent**: Uzasadnienie limitów w kolejce przestaje powoływać się na pingi healthchecks.io (wartości bez zmian). `send-test-alert` czyta `NTFY_URL` i `NTFY_TOKEN` i raportuje przez `NtfyAlerts`. Kody wyjścia i komunikaty mówią o ntfy.

**Contract**: Kody wyjścia komendy bez zmian (2 przy złym ustawieniu, sukces przy `DELIVERED`, osobny kod przy `NOT_CONFIGURED`/`FAILED`, jak dziś). README (sekcja komend operatora) opisuje `NTFY_URL` i `NTFY_TOKEN`.

#### 4. Checki i IaC

**File**: `tools/healthchecks/checks.sh`, `tools/healthchecks/test.sh`, `tools/healthchecks/README.md`, `.railway/railway.ts`, `tools/deploy-gate/` (fixtures, jeśli przypinają nazwy zmiennych API)

**Intent**: `checks.sh apply` nie zakłada już `<env>/api-events`. Usunięcie istniejącego checka to krok operatora z runbooka, bo `apply` niczego nie kasuje. Na API w IaC `NTFY_URL` zastępuje `HEALTHCHECK_PING_URL`, a crony zostają przy `HEALTHCHECK_PING_URL`.

**Contract**: `services=(pipeline-trigger db-backup)`. W `test.sh` to więcej niż jedno oczekiwanie: `fixtures/get-checks.json` (nagranie z 2026-10-23) ma `api-events` jako `.checks[2]` — do edycji razem z `fixtures/README.md`; `fixtures/post-api-events-created.json` jest jedynym nagranym checkiem typu „simple” i źródłem, z którego krok 8 (`test.sh:409-444`) wyprowadza przez `gsub` fixture produkcyjny `deploy-health` — zostaje pod neutralną nazwą (np. `post-simple-created.json`) tylko w tej roli; liczniki żądań, `DELETE` i wierszy `out_is` w krokach 1–4 i 8 schodzą o jeden check (`created3`/`existing3`/`u3`/`ping3`/`urls_out`, :38-41, :207-209, :243). Krok 9 zostaje: nadal porównuje literał `AlertSettings.PING_PATTERN` i `PING_MAX_LENGTH` (tylko te dwie linie, :479, :498) z cronami i `checks.sh`; nagłówek `test.sh:21-22`, `fixtures/README.md:15` i `tools/healthchecks/README.md` (:3,14,32,50,53,59,78) mówią o `NTFY_URL`/ntfy zamiast o `api-events`/`HealthchecksAlerts`. `railway.ts:192`: `NTFY_URL: preserve()` i `NTFY_TOKEN: preserve()` (komentarz przy :186-191 przepisany na ntfy).

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe i integracyjne API przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- `NtfyAlertsTest` przypina żądanie (ścieżka, `Title`, `Priority`, treść UTF-8, ucięcie do 4096 bajtów) i brak URL w logu przy błędzie: `./mvnw -B test -Dtest=NtfyAlertsTest`
- W kodzie produkcyjnym API nie ma już `HEALTHCHECK_PING_URL` ani `HealthchecksAlerts`: `! grep -rnE "HEALTHCHECK_PING_URL|HealthchecksAlerts" szczur-radar-api/src/main`
- Test skryptu checków (z krokiem 9 reguły S-25) przechodzi: `tools/healthchecks/test.sh`
- Test bramki deployu przechodzi: `tools/deploy-gate/test.sh`
- Plan IaC dla PR env nie proponuje usunięcia zmiennej ani zasobu (job `railway-config` w CI zielony): `gh run view <run> --log | grep -E "gate: (ok|pass)"`
- Z `NTFY_TOKEN` żądanie ma `Authorization: Bearer`, bez niego nie ma; 429 i 401 kończą się `FAILED` z WARN-em ze statusem; token nigdy w logu ani w komunikacie ustawień: `./mvnw -B test -Dtest='NtfyAlertsTest,AlertSettingsTest'`

---

## Phase 2: Podsumowanie nocy w API zamiast alarmu o martwym poście

### Overview

Przebieg z samym martwym postem przestaje alarmować operatora. API umie złożyć i wysłać podsumowanie nocy na żądanie crona.

### Changes Required:

#### 1. Filtr alarmu dla samego martwego posta

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/RunNotifications.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/notifications/RunNotificationsTest.java`, `.../notifications/api/NotificationsIT.java`

**Intent**: Powiadomienia adminów powstają jak dziś, ale `RUN_NEEDS_ATTENTION` nie jest podnoszone, gdy `attentionReasons()` to dokładnie `[DEAD_POSTS]`. Taki post trafi do podsumowania nocy.

**Contract**: Obok `reportedByTheCron` nowy warunek „reportedByTheNightSummary”: lista powodów równa `List.of(AttentionReason.DEAD_POSTS)`. Testy:
- przebieg crona `partial` z `dead 1` i bez innych powodów: powiadomienie powstaje, alarmu brak (zastępuje `aCronRunThatEndedPartialWithADeadPostIsRaised`, `RunNotificationsTest.java:111-120`);
- przebieg admina z samym martwym postem: to samo;
- `dead 1` + `processing errors 1`: alarm ze szczegółem `dead 1, errors 1, …` jak dotąd (`NotificationsIT.java:97-108`).

#### 2. Składanie podsumowania nocy

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/NightSummary.java` (nowy; pakiet wybiera implementujący zgodnie z `pipeline/CLAUDE.md`), zapytania w `posts/AssessmentRepository.java` / `posts/PostRepository.java` / `pipeline/run/PipelineRunRepository.java`

**Intent**: Z id przebiegów tej nocy składa tekst podsumowania: linię nocy, linię na każde źródło z wynikiem w przebiegu zakresu albo z ocenami w nocy, linię martwych postów z przebiegów admina od poprzedniej nocy i `/admin`.

**Contract**:
- Linia nocy: data nocy (Warsaw, z `Clock`), zakres przebiegu RANGE (`from…to`), status każdego przebiegu w kolejności wykonania (`retry partial, range partial`; trwający to `running`), suma `processingErrors` i `skipped` z liczników przebiegów.
- Linia źródła, w kolejności `Source`:
  - `fetched`, `skipped` z `pipeline_run_sources` przebiegu RANGE; źródło `failed`/`empty` zamiast nich ma `failed: <błąd ucięty do 200 znaków>` / `empty`;
  - `new`: posty z `first_run_id` = RANGE;
  - `leftovers`: liczba różnych postów ocenianych w przebiegu RETRY (0 bez RETRY);
  - `assessed`, `failed`: oceny udane i nieudane w przebiegach nocy;
  - `dead`: posty `dead`, których ostatnia ocena należy do przebiegu nocy;
  - `tokens in (cached), out`: sumy z ocen przebiegów nocy (`NULL` liczy się jako 0).
- Linia „poza nocą” zawsze, także `none`, według reguły z Critical Implementation Details, rozbita na źródła.
- Całość mieści się w 4096 bajtach. Linie źródeł są ograniczone liczbą źródeł, a błąd źródła ucięty.
- Testy: jednostkowy formatu (`NightSummaryTest`) oraz IT na Testcontainers. IT obejmuje: dwa źródła, RETRY + RANGE, źródło `failed`, przebieg trwający, martwy post z przebiegu admina między nocami (raz w podsumowaniu), martwy post z przebiegu admina sprzed poprzedniej nocy (pominięty), pierwszą noc bez poprzedniej, tokeny `NULL` (prefiltr).

#### 3. Zdarzenie i trasa

**File**: `.../alerts/AlertEvent.java`, `.../pipeline/api/InternalPipelineRunController.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java`, `.../pipeline/api/InternalPipelineRunWithoutTokenIT.java`

**Intent**: Cron prosi o podsumowanie swoich przebiegów. API podnosi je zawsze jako `NIGHT_SUMMARY` przez `OperatorAlertQueue` i oddaje tekst do logu crona.

**Contract**:
- `AlertEvent.NIGHT_SUMMARY("the nightly pipeline summary; nothing needs attention by itself")`, priorytet 3.
- `POST /internal/pipeline-runs/nightly-summary`, ciało `{"runs":[<uuid>, …]}` (1–10 id; inaczej 400 `invalid_request`; nieznane id → 404 `not_found`), odpowiedź 200 `{"text": "<podsumowanie>"}`.
- Zdarzenie podnoszone z `runId` przebiegu RANGE (albo pierwszego, gdy RANGE brak), żeby powtórzone w ciągu 10 min wywołanie się zdeduplikowało; przez `ObjectProvider<OperatorAlertQueue>` i statyczne `raise`, nigdy przez bezpośrednią iniekcję (Critical Implementation Details).
- Trasa tylko z tokenem (`/internal/**`, 401 bez niego: test w `InternalPipelineRunWithoutTokenIT`), nigdy w `PUBLIC_ROUTES`.
- IT sprawdza: odpowiedź, zdarzenie w kolejce (atrapa `OperatorAlerts`) i 400/404.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe i integracyjne API przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- Przebieg z samym martwym postem tworzy powiadomienie bez `RUN_NEEDS_ATTENTION`, a z martwym postem i błędem technicznym podnosi je: `./mvnw -B test -Dtest=RunNotificationsTest`
- Format podsumowania (linia nocy, źródła, „poza nocą”, `/admin`, ≤4096 bajtów) jest przypięty: `./mvnw -B test -Dtest=NightSummaryTest`
- Trasa podsumowania zwraca tekst, podnosi `NIGHT_SUMMARY` i odrzuca złe ciało oraz brak tokenu: `./mvnw -B verify -Dit.test='InternalPipelineRun*IT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`
- Okno „poza nocą” liczy martwy post z przebiegu admina dokładnie raz (IT z dwiema nocami)

---

## Phase 3: Cron wysyła podsumowanie

### Overview

`trigger.sh` po swoich przebiegach prosi API o podsumowanie i loguje je przed linią końcową.

### Changes Required:

#### 1. Wywołanie podsumowania

**File**: `tools/pipeline-trigger/trigger.sh`, `tools/pipeline-trigger/README.md`

**Intent**: Po 202 z `/daily`, na każdej ścieżce końcowej (wszystkie przebiegi skończone, któryś `failed`/`interrupted`, `not ended within`, błąd odpytywania), cron wywołuje `POST /internal/pipeline-runs/nightly-summary` z id z odpowiedzi 202 i loguje zwrócony `text` wiersz po wierszu. Dopiero potem pisze swoją linię końcową.

**Contract**: Jedna próba z krótkim limitem czasu (np. `curl -m 30`) i tym samym tokenem. Nie-200, brak odpowiedzi albo brak `text` daje jedną linię `WARN: nightly summary not sent: <status>`. Kod wyjścia i linia końcowa (treść check-inu) się nie zmieniają. Bez 202 (cron nie wystartował przebiegów) wywołania nie ma.

#### 2. Test na atrapie API

**File**: `tools/pipeline-trigger/test.sh` (i jego atrapa API)

**Intent**: Atrapa dostaje trasę podsumowania. Test dowodzi wywołania i kolejności logu.

**Contract**: Przypadki:
- noc OK: podsumowanie w logu przed `OK: …`, exit 0;
- przebieg `failed`: podsumowanie, potem błąd, exit 1;
- `not ended within`: podsumowanie, potem błąd, exit 1;
- trasa odpowiada 500 albo milczy: `WARN …`, exit i linia końcowa jak bez podsumowania;
- brak 202: atrapa nie dostaje wywołania;
- ciało żądania zawiera dokładnie id z 202.

### Success Criteria:

#### Automated Verification:

- Test crona na atrapie API przechodzi: `tools/pipeline-trigger/test.sh`
- Ostatnia niepusta linia logu pozostaje linią końcową crona na wszystkich ścieżkach (asercje w `test.sh`)
- CI zielone na PR (wszystkie joby, w tym `pipeline-trigger-test`): `gh pr checks <pr>`

---

## Phase 4: Dokumenty i weryfikacja w PR env

### Overview

Dokumenty mówią, jak jest po zmianie. Agent sprawdza całość w PR env na testowym temacie ntfy. Operator przygotowuje produkcję przed merge'em.

### Changes Required:

#### 1. PRD i roadmapa

**File**: `context/foundation/prd.md`, `context/foundation/infrastructure.md` (:35,37,134 — trzy `HEALTHCHECK_PING_URL` „declared with preserve()”, w tym API; edycja w miejscu), `context/foundation/roadmap.md` (także :67,453), `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**:
- Nowe doprecyzowanie NFR (S-34, z datą):
  - zdarzenia API idą przez ntfy, a healthchecks.io zostaje dla cronów i deploy-health;
  - przebieg z samym martwym postem nie daje wiadomości, dzwonek bez zmian;
  - podsumowanie nocy zawsze, z jego treścią i tokenami bez kosztu;
  - zdania z doprecyzowań F-04 i S-18, które przestają być prawdziwe (check `api-events`, Telegram dla zdarzeń API), są nazwane.
- `infrastructure.md`: API ma `NTFY_URL` / `NTFY_TOKEN` (`preserve()`), `HEALTHCHECK_PING_URL` zostaje tylko na cronach; check `api-events` znika z listy checków.
- Roadmapa S-34: outcome i rozstrzygnięte Unknowns. SVG wygenerowane ponownie.

**Contract**: Doprecyzowanie dopisane pod NFR o sygnale dla operatora (`prd.md:275` i dalej), bez przepisywania wcześniejszych. Po zmianie `roadmap.md`: `python3 tools/render_roadmap_svg.py`.

#### 2. Runbook i wytyczne

**File**: `context/deployment/deploy-plan.md` (nowa sekcja S-34), `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`, `tools/healthchecks/README.md`, `tools/pipeline-trigger/README.md`

**Intent**: Sekcja S-34 opisuje kanał ntfy, podsumowanie i runbook operatora:
- przed merge'em: konto ntfy.sh i jego token dostępu, aplikacja ntfy i subskrypcja tematu, `NTFY_URL` i `NTFY_TOKEN` zapieczętowane na produkcyjnym API, usunięcie `HEALTHCHECK_PING_URL` z produkcyjnego API — wszystko z `--skip-deploys`; dlaczego token: limity ntfy.sh per konto zamiast per współdzielone IP Railway;
- po merge'u: `send-test-alert`, pierwsze podsumowanie nocy, usunięcie checka `production/api-events` (i w PR envach) w healthchecks.io;
- co rano: brak podsumowania w ntfy to jedyny sygnał awarii kanału API → ntfy (kolejka nietrwała, bez ponowień): sprawdź `/admin` (dzwonek, martwe posty tej nocy) i log API (`WARN` z `NtfyAlerts`); martwe posty nocy, której podsumowanie przepadło, nie wracają w żadnym późniejszym podsumowaniu.

Wytyczne repo (pakiet `alerts`, „Out-of-app alert channel”, `tools/healthchecks`, „Daily run”) i `pipeline/CLAUDE.md` (alarm przebiegu S-18, nowa trasa) mówią o ntfy i podsumowaniu.

**Contract**: Linia statusu sekcji według lekcji: „**Status:** delivered by PR #N; production verification and closure in issue #M.”

#### 3. Weryfikacja w PR env (agent)

**Intent**: Agent ustawia w PR env na `szczur-radar-api` `NTFY_URL` z losowym testowym tematem (nie produkcyjnym), a na `pipeline-trigger` `TRIGGER_IN_ANY_ENVIRONMENT=true`. Uruchamia `send-test-alert` i nocny przebieg crona, a potem czyta wiadomości tematu.

**Contract**: Odczyt `curl -s "https://ntfy.sh/<topic>/json?poll=1&since=2h"` daje wiadomość `TEST_SIGNAL` i wiadomość `NIGHT_SUMMARY` z liniami źródeł. Log crona ma podsumowanie przed linią końcową. Temat testowy po weryfikacji nie jest nigdzie zapisany w repo.

#### 4. Sprawdzenie produkcji przed merge'em (agent)

**Intent**: Po kroku operatora (4.8) agent dowodzi, że produkcyjne API ma klucz `NTFY_URL` i nie ma już `HEALTHCHECK_PING_URL`, zanim PR zostanie oddany do merge'a. Bez tego `railway-config` na `main` odrzuci `variable.delete` i zatrzyma deploy (Critical Implementation Details).

**Contract**: Odczyt tylko listy kluczy (wartości zapieczętowanej CLI nie pokazuje): `railway variables -s szczur-radar-api -e production --json | jq -e 'has("NTFY_URL") and (has("HEALTHCHECK_PING_URL") | not)'`. Wynik i czas sprawdzenia trafiają do komentarza w issue S-34. Kroki crona (`db-backup`, `pipeline-trigger`) zachowują `HEALTHCHECK_PING_URL` — sprawdzenie ich nie dotyczy.

### Success Criteria:

#### Automated Verification:

- Roadmapa i jej SVG są zgodne: `python3 tools/render_roadmap_svg.py && git diff --exit-code docs/visuals/roadmap.svg`
- W wytycznych, dokumentach pakietów, `infrastructure.md` i kodzie API nie ma już opisu zdarzeń API przez `api-events` ani `HEALTHCHECK_PING_URL` na API: `! grep -n "api-events" REPOSITORY_GUIDELINES.md szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md context/foundation/infrastructure.md && ! grep -rn "HEALTHCHECK_PING_URL" szczur-radar-api/src/main`
- PR env: `send-test-alert` dostarcza wiadomość na testowy temat: `curl -s "https://ntfy.sh/<topic>/json?poll=1&since=2h" | jq -e 'select(.title | contains("TEST_SIGNAL"))'`
- PR env: nocny przebieg crona daje na temacie `NIGHT_SUMMARY` z linią każdego skonfigurowanego źródła: `curl -s "https://ntfy.sh/<topic>/json?poll=1&since=2h" | jq -e 'select(.title | contains("NIGHT_SUMMARY")) | .message | test("reddit: ") and test("wykop: ")'`
- PR env: log crona ma podsumowanie przed linią `OK: …`, a przebieg z samym martwym postem (jeśli wystąpi) nie daje na temacie `RUN_NEEDS_ATTENTION`: `railway logs -s pipeline-trigger -e <pr-env>` oraz odczyt tematu przez `jq`
- CI zielone na PR po ostatnim commicie: `gh pr checks <pr>`
- Przed merge'em produkcyjne API ma klucz `NTFY_URL` i nie ma `HEALTHCHECK_PING_URL`: `railway variables -s szczur-radar-api -e production --json | jq -e 'has("NTFY_URL") and (has("HEALTHCHECK_PING_URL") | not)'`

#### Manual Verification:

- Operator ocenia czytelność treści podsumowania z PR env (zrzut wiadomości od agenta) i akceptuje format
- Operator przed merge'em: zainstalował aplikację ntfy, subskrybuje produkcyjny temat, ustawił zapieczętowane `NTFY_URL` i `NTFY_TOKEN` (token konta ntfy.sh) na produkcyjnym `szczur-radar-api` i usunął stamtąd `HEALTHCHECK_PING_URL`, wszystko z `--skip-deploys`

---

## Testing Strategy

### Unit Tests:

- `NtfyAlertsTest`: żądanie, nagłówki, ucięcie po bajtach z wielobajtowym znakiem na granicy, błędy HTTP, time-out, przerwanie, brak URL w logach.
- `AlertSettingsTest`: `NTFY_URL` na tej samej tabeli `ping-urls.txt`.
- `RunNotificationsTest`: sam `DEAD_POSTS` → bez alarmu; `DEAD_POSTS` + inny powód → alarm; przebieg crona `failed` bez zmian.
- `NightSummaryTest`: format linii, kolejność źródeł, źródło `failed`/`empty`, `running`, `none` poza nocą, limit 4096 bajtów.
- `SendTestAlertCommandTest`: `NTFY_URL` i kody wyjścia.

### Integration Tests:

- `InternalPipelineRunIT`: trasa podsumowania na prawdziwej bazie (RETRY + RANGE, dwa źródła, tokeny, martwe w nocy i poza nocą w dwóch kolejnych nocach), zdarzenie w kolejce, 400/404.
- `InternalPipelineRunWithoutTokenIT`: 401 na nowej trasie.
- `NotificationsIT`: alarm tylko dla przebiegu z innym powodem niż sam martwy post.
- `tools/pipeline-trigger/test.sh`: kolejność logu i kody wyjścia na atrapie API.

### Manual Testing Steps:

1. Agent: PR env z testowym tematem, `send-test-alert`, przebieg crona, odczyt tematu `jq`.
2. Operator: ocena treści podsumowania i przygotowanie produkcji przed merge'em.

## Performance Considerations

Podsumowanie to kilka zapytań agregujących po `assessments` i `posts` dla 1–2 przebiegów raz na noc. Wystarczą istniejące indeksy (`assessments.run_id`, `ix_assessments_post_latest`). Wywołanie odbywa się, gdy API i tak jest obudzone przez cron, a API nie dostaje zegara.

## Migration Notes

- Bez migracji schematu i bez nowej tabeli (`tables.sh` bez zmian).
- **Przed merge'em (operator, produkcja):** `NTFY_URL` zapieczętowany na `szczur-radar-api`, potem usunięcie `HEALTHCHECK_PING_URL` z `szczur-radar-api`, oba z `--skip-deploys`. Bez tego `railway-config` na `main` odrzuci `variable.delete`; CI na PR tego nie pokaże, dowodem jest kryterium 4.9 (agent, `railway variables --json`). Crony zachowują swoje `HEALTHCHECK_PING_URL`.
- **Po merge'u (runbook S-34 i komentarz w issue, nie Progress):** `send-test-alert` na produkcji, pierwsze podsumowanie nocy, usunięcie checków `api-events` w healthchecks.io (produkcja i PR envy).
- Wycofanie: revert PR, a operator przywraca `HEALTHCHECK_PING_URL` na API (wartość z `checks.sh apply production`).

## References

- Roadmapa: `context/foundation/roadmap.md` (S-34, :208-219)
- PRD: `context/foundation/prd.md:275-288` (NFR o sygnale, doprecyzowania F-04, S-18, PRD v3)
- Destylacja: `context/domain/domain-distillation.md` (R-42 :242, D-13 :285, #5 :314, Q-14 :333)
- Dane nocy: issue #121; `context/archive/2026-10-30-reddit-quality-and-cost/report-2026-10-31.md:117-118`
- Kod: `notifications/RunNotifications.java:103-150`, `pipeline/run/PipelineRun.java:316-352`, `alerts/` (cały pakiet), `pipeline/api/InternalPipelineRunController.java:65-120`, `tools/pipeline-trigger/trigger.sh:87-347`, `tools/healthchecks/checks.sh:38`, `tools/healthchecks/test.sh:449-503`, `.railway/railway.ts:192`
- Runbooki: `context/deployment/deploy-plan.md` sekcje F-04 (:1186), S-18 (:1668)
- ntfy: https://docs.ntfy.sh/publish/ (Limitations)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Kanał ntfy dla zdarzeń API

#### Automated

- [x] 1.1 Testy jednostkowe i integracyjne API przechodzą: `cd szczur-radar-api && ./mvnw -B verify` — 8a8f040
- [x] 1.2 `NtfyAlertsTest` przypina żądanie (ścieżka, `Title`, `Priority`, treść UTF-8, ucięcie do 4096 bajtów) i brak URL w logu przy błędzie — 8a8f040
- [x] 1.3 W kodzie produkcyjnym API nie ma już `HEALTHCHECK_PING_URL` ani `HealthchecksAlerts` — 8a8f040
- [x] 1.4 Test skryptu checków (z krokiem 9 reguły S-25) przechodzi: `tools/healthchecks/test.sh` — 8a8f040
- [x] 1.5 Test bramki deployu przechodzi: `tools/deploy-gate/test.sh` — 8a8f040
- [x] 1.6 Plan IaC dla PR env nie proponuje usunięcia zmiennej ani zasobu (job `railway-config` w CI zielony) — 8a8f040
- [x] 1.7 Z `NTFY_TOKEN` żądanie ma `Authorization: Bearer`, bez niego nie ma; 429 i 401 kończą się `FAILED` z WARN-em ze statusem; token nigdy w logu ani w komunikacie ustawień — 8a8f040

### Phase 2: Podsumowanie nocy w API zamiast alarmu o martwym poście

#### Automated

- [x] 2.1 Testy jednostkowe i integracyjne API przechodzą: `cd szczur-radar-api && ./mvnw -B verify` — aced7b1
- [x] 2.2 Przebieg z samym martwym postem tworzy powiadomienie bez `RUN_NEEDS_ATTENTION`, a z martwym postem i błędem technicznym podnosi je — aced7b1
- [x] 2.3 Format podsumowania (linia nocy, źródła, „poza nocą”, `/admin`, ≤4096 bajtów) jest przypięty — aced7b1
- [x] 2.4 Trasa podsumowania zwraca tekst, podnosi `NIGHT_SUMMARY` i odrzuca złe ciało oraz brak tokenu — aced7b1
- [x] 2.5 Okno „poza nocą” liczy martwy post z przebiegu admina dokładnie raz (IT z dwiema nocami) — aced7b1

### Phase 3: Cron wysyła podsumowanie

#### Automated

- [x] 3.1 Test crona na atrapie API przechodzi: `tools/pipeline-trigger/test.sh` — 2fba6ad
- [x] 3.2 Ostatnia niepusta linia logu pozostaje linią końcową crona na wszystkich ścieżkach — 2fba6ad
- [x] 3.3 CI zielone na PR (wszystkie joby, w tym `pipeline-trigger-test`) — 2fba6ad

### Phase 4: Dokumenty i weryfikacja w PR env

#### Automated

- [x] 4.1 Roadmapa i jej SVG są zgodne
- [x] 4.2 W wytycznych, dokumentach pakietów, `infrastructure.md` i kodzie API nie ma już opisu zdarzeń API przez `api-events` ani `HEALTHCHECK_PING_URL` na API
- [x] 4.3 PR env: `send-test-alert` dostarcza wiadomość na testowy temat
- [x] 4.4 PR env: nocny przebieg crona daje na temacie `NIGHT_SUMMARY` z linią każdego skonfigurowanego źródła
- [x] 4.5 PR env: log crona ma podsumowanie przed linią `OK: …`, a przebieg z samym martwym postem nie daje na temacie `RUN_NEEDS_ATTENTION`
- [ ] 4.6 CI zielone na PR po ostatnim commicie
- [ ] 4.9 Przed merge'em produkcyjne API ma klucz `NTFY_URL` i nie ma `HEALTHCHECK_PING_URL` (`railway variables … --json | jq -e …`)

#### Manual

- [ ] 4.7 Operator ocenia czytelność treści podsumowania z PR env i akceptuje format
- [ ] 4.8 Operator przed merge'em: aplikacja ntfy, subskrypcja produkcyjnego tematu, zapieczętowane `NTFY_URL` i `NTFY_TOKEN` na produkcyjnym API i usunięty `HEALTHCHECK_PING_URL`, wszystko z `--skip-deploys`
