---
date: 2026-10-24T15:08:14+02:00
researcher: Claude Opus 5.5 (Claude Code)
git_commit: 09171178c08099b6370ffff8f37fd11e7be3aa8a
branch: feature/missed-night-detection
repository: kwmacioszek/szczur-radar
topic: "S-13 missed-night-detection: co F-04 już wykrywa, a co zostaje — dni bez danych, uzupełnianie dni starszych niż D-2, nadzór NAS → Google Drive"
tags: [research, pipeline, pipeline-trigger, healthchecks, freshness, backfill, wykop, db-backup]
status: complete
last_updated: 2026-10-24
last_updated_by: Claude Opus 5.5 (Claude Code)
last_updated_note: "Decyzja użytkownika po badaniu: wariant B (pokazać dni bez danych w /admin), bez automatycznego uzupełniania i bez nowego alarmu poza aplikacją; potem aktualizacja po przewinięciu gałęzi na origin/main 0bea22f (F-04 zarchiwizowane, statusy poprawione)"
---

# Research: S-13 missed-night-detection — co zostaje po F-04

**Date**: 2026-10-24T15:08:14+02:00
**Researcher**: Claude Opus 5.5 (Claude Code)
**Git Commit**: `0917117` (gałąź jest 1 commit za `origin/main` = `4753bc4`, archiwizacja S-16 z drobnymi poprawkami w `WykopApiClient` / `WykopRepository`; nie zmienia ustaleń poniżej)
**Branch**: `feature/missed-night-detection`
**Repository**: kwmacioszek/szczur-radar

## Research Question

`change.md` nie podaje zamiaru, więc pytanie wynika z roadmapy (S-13, `context/foundation/roadmap.md`, sekcja „S-13: Brakująca noc nie przechodzi niezauważona”) i issue #72:

> operator dostaje alarm przez kanał z F-04, gdy nocny przebieg albo backup nie wystartował lub nie zgłosił końca na czas, a dni bez pobranych danych są widoczne (lub uzupełniane).

Konkretnie:
1. Co z tego celu już daje F-04 (healthchecks.io → Telegram), a czego nie?
2. Kiedy powstaje dzień bez danych i czy da się go wyznaczyć z istniejących danych?
3. Czy uzupełnianie dni starszych niż D-2 jest technicznie wykonalne i ile kosztuje?
4. Czy nadzór synchronizacji NAS → Google Drive mieści się w kanale F-04?
5. Jakie ograniczenia z wcześniejszych decyzji wiążą rozwiązanie?

## Summary

- **Alarm o brakującej nocy i backupie już działa.** Dostarczyło go F-04: zweryfikowany na produkcji 2026-10-24, issue #66 zamknięte o 05:45 UTC. Checki healthchecks.io `production/pipeline-trigger` (alarm ok. 03:45 UTC) i `production/db-backup` (ok. 04:15 UTC) alarmują w Telegramie w trzech przypadkach: cron nie wystartował, utknął w `QUEUED` albo nie zgłosił końca. Kod wyjścia ≠ 0 alarmuje od razu. Pierwszą część celu S-13 spełnia więc F-04. Zostaje widoczność (lub uzupełnianie) dni bez danych i ewentualny nadzór NAS → Google Drive. Tak też mówi uwaga F-04 w roadmapie.
- **Dziura w dniach powstaje dopiero po dwóch złych nocach z rzędu.** Nocny przebieg pobiera D-2…D-1 według kalendarza warszawskiego (`InternalPipelineRunController.java:65-71`), więc dzień X pobierają dwie noce: X+1 (jako D-1) i X+2 (jako D-2). Dzień zostaje bez przebiegu tylko wtedy, gdy żadna z tych dwóch nocy nie skończyła się `succeeded`/`partial`. Każda taka zła noc crona (brak startu, `failed`, `interrupted`, pusty zakres) już daje alarm F-04, ale nic nie mówi, który dzień został bez danych, i nic go nie uzupełnia (audyt C3).
- **Pokrycie dni da się wyprowadzić z istniejących danych, bez migracji.** Można to policzyć na dwa sposoby, które odpowiadają na różne pytania:
  - *pokrycie przebiegami*: dni warszawskie w `[range_from, range_to]` przebiegów RANGE `succeeded`/`partial` (`pipeline_runs`, V3);
  - *posty na dzień*: `posts.published_at` w strefie Europe/Warsaw (V3:89, indeks `ix_posts_stage_published`).
  Żadnego endpointu ani zapytania o pokrycie dziś nie ma.
- **Etykieta „Dane zaktualizowane” nie pokazuje dziur.** Pokazuje `finished_at` przebiegu RANGE `succeeded`/`partial` z najdalszym `range_to` (`PipelineRunRepository.java:44-53`). Decyzja z przeglądu S-05 (F4): przebieg admina po starszych dniach jej nie przesuwa.
- **Uzupełnianie jest wykonalne i tanie.** Wykop filtruje daty po stronie serwera, więc koszt pobrania zależy od liczby elementów, nie od wieku dnia (`WykopRepository.java:122-130,166-173`). Ponowne pobranie już zapisanego dnia nie wywołuje LLM (`PostStore`, `AssessmentSelection`). Ręczny przebieg admina przyjmuje zakres o dowolnym początku, najwyżej 7 dni (`PipelineRunController.java:54,145-146`). Ograniczeniem jest czas: zakres kilkudniowy z oceną LLM przekracza 90 min `TRIGGER_RUN_TIMEOUT`, jeśli uruchamia go cron.
- **Synchronizacji NAS → Google Drive nic dziś nie nadzoruje.** F-04 świadomie ją wyłączył (`out-of-app-alert-channel/plan.md:91`). Sygnał mógłby wyjść tylko z samego NAS-a, bo repozytorium i Railway jej nie widzą (wniosek).

## Detailed Findings

### 1. Co F-04 już wykrywa (stan produkcji 2026-10-24)

- Checki (`tools/healthchecks/checks.sh:38-41`, `context/deployment/deploy-plan.md:1218-1231`):

  | Check | Harmonogram (UTC) | Karencja | Alarm ok. |
  |---|---|---|---|
  | `<env>/pipeline-trigger` | `30 0 * * *` | 11700 s | 03:45 |
  | `<env>/db-backup` | `0 2 * * *` | 8100 s | 04:15 |
  | `<env>/api-events` | — (simple) | 365 d | tylko zdarzenia API |
  | `production/deploy-health` | — (simple) | 365 d | czerwony monitor deployu |

- Meldunki wysyła zewnętrzny proces z limitem czasu: `/start`, potem `/<kod wyjścia>` z ostatnią linią logu (`deploy-plan.md:1198-1210`, `trigger.sh:45-54`, `backup.sh:41-49`).
  - Brak meldunku do harmonogramu + karencji daje alarm. To obejmuje brak startu, `QUEUED` i kontener podmieniony przez merge.
  - Kod ≠ 0 daje alarm od razu.
- Wynik `pipeline-trigger` a alarm:
  - `failed`/`interrupted` (także pusty zakres, S-08): kod 1, Telegram.
  - `partial`: kod 0, bez Telegramu, tylko dzwonek w aplikacji (PRD `prd.md:263`, `deploy-plan.md:466`).
- Produkcja: issue #66, komentarze z 2026-10-24 05:32 i 05:45 UTC. Pierwsza noc z meldunkami `start` + `OK:` obu cronów (`trigger: OK: retry succeeded, range partial`, 3073 s). `HEALTHCHECK_PING_URL` są zapieczętowane i nie przechodzą do PR env.
- Nadawca API: `alerts/AlertEvent` ma jedną wartość, `TEST_SIGNAL` („S-18 adds the error paths”). Jedyny wywołujący to `cli/SendTestAlertCommand`. Zdarzenie z API wychodzi tylko wtedy, gdy API nie śpi.
- F-04 wyłączył z zakresu (`out-of-app-alert-channel/plan.md`, „What We're NOT Doing”, linie 87 i 91):
  - „Wykrywania dni bez danych i uzupełniania dni starszych niż D-2 (S-13)”;
  - „Nadzoru synchronizacji NAS → Google Drive (poza Railway) i alarmu o awarii samego healthchecks.io”.
- Czego F-04 nie łapie (`deploy-plan.md:1335`, audyt):
  - awarii samego healthchecks.io albo Telegrama;
  - `partial` z martwym postem albo błędem technicznym (celowo tylko dzwonek);
  - przebiegu udanego, ale z podejrzanie małą liczbą postów (S-08 łapie tylko zero);
  - NAS / Google Drive.
- Niezweryfikowane: tymczasowy harmonogram crona pozostawiony na częstszym niż dzienny (`deploy-plan.md:296,411`) mógłby maskować pominiętą noc, bo każdy ping zeruje check. Nie sprawdzono tego z semantyką checków cron healthchecks.io. Przypadek rzadszego harmonogramu F-04 łapie po karencji.

### 2. Kiedy powstaje dzień bez danych

- `daily()` (`pipeline/api/InternalPipelineRunController.java:65-71`) liczy `today = LocalDate.ofInstant(clock.instant(), ZONE)` (`ZONE` = Europe/Warsaw, `PipelineRunService.java:82`) i woła `startRange(today-2, today-1, wszystkie źródła, SCHEDULER)`. Javadoc (`:58-63`): „a day's late posts are caught the next night; a repeated day costs no LLM call”.
- `startRange` (`PipelineRunService.java:147-153`):
  - zakres to `from` 00:00:00 do `to` 23:59:59 czasu warszawskiego, obie granice włącznie, zapisany jako `Instant` w `range_from`/`range_to`;
  - gdy są zaległe posty, przed zakresem idzie przebieg RETRY;
  - gdy jakiś przebieg czeka albo trwa, start kończy się `PipelineBusyException`.
- Cron startuje o 00:30 UTC, czyli 01:30 lub 02:30 w Warszawie, ten sam dzień warszawski. Każdy dzień X jest więc w zakresie dokładnie dwóch nocy crona: X+1 i X+2.
  - Jedna zła noc: dziury nie ma, bo następna noc pobiera jej D-1 jako swoje D-2.
  - Dwie złe noce z rzędu (N, N+1): bez przebiegu zostaje jeden dzień, D(N)-1.
  - Każda kolejna zła noc z rzędu dokłada jeden dzień.
- Kod wyznaczający zakres (`daily()` i `startRange`, linie wyżej) nie patrzy na wcześniejsze przebiegi. Uzupełniania nie ma (audyt C3, `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:92`). W `src/main` nie ma też `@Scheduled` ani `EnableScheduling`. Jedyny timer w API to `RunWatchdog`, który nie pyta bazy, gdy nie ma własnego przebiegu (`pipeline/CLAUDE.md`, sekcja Watchdog).

### 3. Z czego da się wyprowadzić pokrycie dni

- **Pokrycie przebiegami** (`pipeline_runs`, V3:7-40, `sources TEXT[]` z V5):
  - zbiór dni to `kind = 'range' AND status IN ('succeeded','partial')`, a w nim dni warszawskie między `range_from` a `range_to`;
  - liczą się przebiegi crona i admina (`requested_by`), bo oba idą przez `startRange`.
  - Co znaczy `succeeded`/`partial`: `SourceIngest.fetch` (`pipeline/step/SourceIngest.java:69-102`) pobiera każde wskazane źródło. Źródło, które rzuci wyjątek, kończy krok błędem. Źródło bez posta daje `NoPostsFetchedException` i przebieg `failed`. Przebieg RANGE `succeeded`/`partial` znaczy więc, że każde źródło z `sources[]` pobrało się bez błędu i dało co najmniej jeden post w całym zakresie. Nie znaczy, że każdy dzień zakresu ma posty.
  - Liczniki (`fetched`, `new_posts`, `skipped`) są sumą po źródłach i dniach. Wyniku na dzień ani na źródło nie ma.
- **Posty na dzień:** `posts.published_at TIMESTAMPTZ NOT NULL` (V3:89). Dla Wykopu to `created_at` elementu w strefie Europe/Warsaw (`sources/wykop/WykopMapper.java:67-79`). Wyszukiwanie Wykopu filtruje `date_from`/`date_to` w czasie warszawskim (`WykopRepository.java:166-173`).
  - Że filtr Wykopu działa na tym samym `created_at`, zgadza się z żywym sprawdzeniem z 2026-10-04 (cały dzień zwrócony, `context/archive/2026-09-27-assessment-contract-harness/implementation-notes.md:107-112`), ale tego wprost nie udowodniono (wniosek).
  - Indeks `ix_posts_stage_published (stage, published_at DESC, id DESC)` (V3:125). Endpointu agregacji na dzień nie ma (`posts/api/PostController.java`: `GET /api/posts`, `/{id}`, `/{id}/raw`).
- **Dwa sposoby, dwie odpowiedzi.**
  - Pokrycie przebiegami odpowiada na pytanie „czy dzień pobrano”.
  - Posty na dzień odpowiadają na pytanie „czy dzień ma dane”.
  - Różnią się dla dnia, który pobrano, ale który jest naprawdę pusty albo bardzo mały. Obserwowane 2-dniowe weekendowe zakresy miały 74 elementy (`context/archive/2026-10-18-empty-run-signal/research.md:51`). Typowy dzień to ok. 270 postów (`context/archive/2026-10-06-first-assessed-wykop-feed/research.md:61`).
  - Przy jednym źródle oba sposoby zwykle wskażą te same dziury po złych nocach (wniosek).

### 4. Etykieta świeżości

- `GET /api/freshness` (`pipeline/api/FreshnessController.java:31-34`) zwraca `{lastUpdatedAt}`. Wartość pochodzi z `findLastUpdate()` (`pipeline/run/PipelineRunRepository.java:44-53`):
  - przebiegi RANGE `succeeded`/`partial`;
  - `order by rangeTo desc, finishedAt desc limit 1`.
- Przebiegi RETRY i POSTS oraz `failed`/`interrupted`/niezakończone etykiety nie przesuwają. Przebieg admina po starszych dniach też nie, co jest decyzją z `context/archive/2026-10-12-daily-scheduled-run-freshness/reviews/impl-review.md:92`.
- Front: `szczur-radar-web/src/api/freshness.ts:10-12` i `src/views/FeedView.vue:172-178` pokazują „Dane zaktualizowane: <czas>” albo „Dane nie były jeszcze aktualizowane.”. Nie ma progu ani ostrzeżenia o nieaktualności. US-01 wymaga tylko, żeby etykieta pokazywała datę ostatniego pomyślnego przebiegu.

### 5. Wykonalność uzupełniania dni starszych niż D-2

- **Pobieranie.** `WykopFetcher.fetch(from, to)` woła `WykopRepository.itemsAddedBetween(tag, from, to)`, czyli `search/entries`, potem `search/links` (`WykopRepository.java:122-130`). Parametry to `tags=[gielda]`, `date_from`, `date_to`, `sort=newest`, `limit=50`, `page` (`:166-173`). Stronicowanie kończy się na `pagination.total` z pierwszej strony (`:193-218`).
  - Koszt jest proporcjonalny do liczby elementów w zakresie, nie do wieku dnia. Jeden dzień to ok. 6 + 1 żądań z pauzą 300 ms (`:53`).
- **Limity jednego pobrania:**
  - `FETCH_DEADLINE` 10 min na cały zakres (`WykopRepository.java:55-61`), poniżej `PipelineStallSettings.MIN_DEFAULT` 15 min (`PipelineStallSettings.java:40`);
  - `MAX_PAGES` 200 na zasób, do 10 000 elementów (`:51`);
  - 7 dni to ok. 2 000 elementów i 40–45 żądań, ekstrapolacja z 6,0 s na 425 elementów (`deploy-plan.md:844`), niezmierzone.
- **Jak daleko wstecz Wykop serwuje:** nieustalone. Pobierano najwyżej ok. 12 dni wstecz. Zakres z 2008 r. dał 0 elementów (`deploy-plan.md:1177`), ale to nie był test głębokości. Limitów zapytań Wykopu nie znamy (`context/archive/2026-10-06-first-assessed-wykop-feed/research.md:183-189`).
- **Zakres nachodzący na już pobrane dni jest bezpieczny:**
  - `PostStore.store` zapisuje tylko nowe klucze `(source, item_type, source_item_id)` (`pipeline/step/PostStore.java:56-58,92-105`, `uq_posts_source_item`);
  - przebieg RANGE ocenia tylko posty, które sam zapisał pierwszy (`pipeline/step/AssessmentSelection.java:37-44`);
  - już zapisanych postów nikt ponownie nie wysyła do LLM. Kod już na tym polega: D-2 jest pobierane dwa razy.
- **Ręczny przebieg admina:**
  - `POST /api/admin/pipeline-runs` z `{from, to, sources?}` (`pipeline/api/PipelineRunController.java:86-97,133-134`);
  - walidacja (`:140-147`): `from ≤ to`, `to ≤ dziś` (Warszawa), `DAYS.between(from, to) < MAX_RANGE_DAYS` (7, `:54`), czyli najwyżej 7 dni włącznie;
  - początku zakresu nic nie ogranicza;
  - UI: `szczur-radar-web/src/views/AdminView.vue:249-251`, domyślnie wczoraj (`:21-29`).
- **Czas i koszt oceny LLM:**
  - przepustowość ok. 13–14 postów/min na flex: noc z 151 zaległymi + 469 nowymi trwała 46 min (`deploy-plan.md:547-549`);
  - ok. 270 postów na dzień, więc 3 dni to ok. 1 h, a 7 dni ok. 2–2,5 h (ekstrapolacja);
  - koszt ok. $0,07 (flex) – $0,13 (default) za 270 postów (`context/archive/2026-10-12-daily-scheduled-run-freshness/research.md:131`), więc 7 dni to poniżej $1, przy limicie OpenRouter $10/mies.
- **Ograniczenia, gdy uzupełnia cron:**
  - `TRIGGER_RUN_TIMEOUT` 90 min na przebieg i `TRIGGER_TIMEOUT` 3 h razem (`tools/pipeline-trigger/trigger.sh:31-33`). Przekroczenie daje kod 1, czyli fałszywy alarm Telegram, choć przebieg trwa dalej (audyt C8, odłożone w `roadmap.md` `## Parked`).
  - Karencja checka 3 h 15 min.
  - Okno bez merge'a: 00:30–01:30 UTC i dopóki przebieg `RUNNING`.
  - Nałożenie z backupem o 02:00 jest nieszkodliwe (audyt C8).
  - Przed zakresem idzie RETRY z wszystkimi zaległymi postami, bez limitu (`AssessmentSelection.java:47-55`).
  - Zakres złożony z samych pustych dni kończy się `failed` (`SourceIngest.java:92-101`).

### 6. NAS → Google Drive

- Konfiguracja (`deploy-plan.md:439-451`, F-01, stan 2026-10-12) to dwa zadania Synology Cloud Sync:
  - bucket → NAS: lustro, 14 dziennych + 12 miesięcznych, katalog `/volume2/ProxmoxBackup/szczur-radar/s3`;
  - NAS → Google Drive: tylko wysyłanie, bez usuwania w celu, więc Drive trzyma każdy zrzut.
- Nic w repozytorium tych synchronizacji nie sprawdza. Audyt opisuje je jako „synchronizacja NAS/Drive niewidoczna” (macierz obserwowalności, `post-mvp-pipeline-audit/research.md:116`). Roadmapa: „obejmuje też synchronizację NAS → Google Drive tylko wtedy, gdy kanał to umożliwia”.
- Kanał F-04 to umożliwia technicznie (wniosek, żaden dokument tego nie proponuje). Działałby tak: osobny check healthchecks.io dodany w `tools/healthchecks/checks.sh` plus zadanie DSM na NAS-ie, które pinguje go, gdy najnowszy zrzut jest w Drive albo na NAS-ie. To praca operatora na NAS-ie, poza Railway i CI. Ping URL byłby kolejnym sekretem.

## Code References

- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunController.java:58-71` — nocny zakres D-2…D-1, Javadoc o powtórzonym dniu
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:82,147-153` — `ZONE`, `startRange` (granice zakresu, RETRY przed RANGE)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java:44-53` — `findLastUpdate` (etykieta świeżości)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/FreshnessController.java:31-34` — `GET /api/freshness`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java:54,86-97,140-147` — ręczny zakres, `MAX_RANGE_DAYS = 7`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/SourceIngest.java:69-102` — źródło bez posta → `NoPostsFetchedException`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/PostStore.java:56-58,92-105` — zapis tylko nowych kluczy
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/AssessmentSelection.java:37-55` — RANGE ocenia tylko swoje nowe posty; RETRY wszystkie zaległe
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:51-61,122-130,166-173` — limity pobrania, zasoby, parametry wyszukiwania
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopMapper.java:67-79` — `publishedAt` = `created_at` (Warszawa)
- `szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:7-40,89,125` — `pipeline_runs`, `posts.published_at`, indeks
- `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertEvent.java` — tylko `TEST_SIGNAL`
- `szczur-radar-web/src/views/FeedView.vue:172-178`, `src/api/freshness.ts:10-12` — etykieta świeżości
- `szczur-radar-web/src/views/AdminView.vue:21-29,249-251` — formularz ręcznego zakresu
- `tools/healthchecks/checks.sh:38-41` — checki i karencje
- `tools/pipeline-trigger/trigger.sh:24-33,45-54` — pominięcie poza produkcją, limity czasu, meldunki

## Architecture Insights

- Wykrywanie, że coś się NIE stało, żyje poza aplikacją (healthchecks.io). Przyczyna: API usypia, a rama F-04 zakazuje odpytywania produkcji i nowych zegarów w API, przy koszcie $0 (`out-of-app-alert-channel/frame.md`). Dlatego wykrycie dni bez danych w samej aplikacji może być tylko leniwe: liczone przy żądaniu (widok admina, endpoint) albo przy nocnym `POST /internal/pipeline-runs/daily`, który i tak budzi API.
- Każdy start przebiegu idzie przez `PipelineRunService` (`pipeline/CLAUDE.md`, „Entries”). Uzupełnianie, ręczne czy automatyczne, nie potrzebuje nowej ścieżki startu, tylko innego `from`.
- Powiadomienia w aplikacji są przypięte do przebiegu: unikalny klucz `uq_notifications_recipient_kind_run` (V9) traktuje `NULL` `run_id` jako różne. Rodzaj powiadomienia bez przebiegu (np. „dzień bez danych”) wymaga nowej migracji z `UNIQUE NULLS NOT DISTINCT` (`pipeline/CLAUDE.md`, „Notifications”).
- Pokrycie liczone z `pipeline_runs` nie zależy od źródła, bo bierze `sources[]` i status. Reguła „nowe źródło zmienia tylko pobieranie i mapowanie” (REPOSITORY_GUIDELINES, Hard Rules) jest więc zachowana. Pokrycie liczone z postów zależy od `published_at`, które każde źródło mapuje samo.

## Historical Context (from prior changes)

- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:91-92,114,239,278`:
  - C2 („brak dead-man's switch”) zamknęło F-04;
  - C3 („dni starsze niż D-2 nigdy nie są uzupełniane… świeżość mierzy koniec ostatniego przebiegu, nie pokrycie dni”) jest nadal aktualne;
  - Open Question „czy automatycznie uzupełniać dni starsze niż D-2” wciąż bez odpowiedzi;
  - kotwica audytu `PipelineRunRepository.java:32-48` jest przesunięta, zapytanie jest w `:44-53`.
- `context/changes/out-of-app-alert-channel/plan.md:87,91` i `plan-brief.md:54-56`: dni bez danych i NAS → Drive przekazane do S-13. Ograniczenia ramy: tylko sygnały wychodzące, bez odpytywania produkcji, bez zegarów w API, $0.
- `context/archive/2026-10-18-empty-run-signal/plan.md:67`: „brakująca noc i dziury w dniach (S-13: C2, C3)” poza zakresem S-08. S-08 ustalił, że pusty zakres to `failed`.
- `context/archive/2026-10-18-backup-check-completeness/plan.md:88`: S-15 nie zmienia NAS ani Google Drive.
- `context/archive/2026-10-12-daily-scheduled-run-freshness/reviews/impl-review.md:92` (F4): przebieg admina po starszych dniach nie przesuwa etykiety.
- `context/foundation/roadmap.md`, S-13:
  - Unknown: „Czy automatycznie uzupełniać dni starsze niż D-2, czy tylko je pokazywać i zostawić ręczny przebieg zakresu adminowi? — Owner: user. Block: no”;
  - Uwaga F-04 (2026-10-20): „dla S-13 zostają dni bez danych (pokazanie lub uzupełnianie) i ewentualny nadzór NAS → Google Drive”.
- PRD:
  - NFR „sygnał awarii przebiegu” (`prd.md:259`, doprecyzowanie F-04 `:263` kończy się zdaniem „dni bez pobranych danych pokaże S-13”);
  - NFR „kopia bazy” (`prd.md:270`): jedna kopia poza platformą, czyli NAS. PRD nie wspomina Google Drive.
  - FR-002 (dzienny przebieg) i FR-001 (pobranie zakresu) są must-have. FR-022 (ręczny przebieg) jest nice-to-have i już zbudowany. MS-04 jest zdefiniowane tylko w `roadmap.md:80`.
- **Nieaktualne statusy F-04 (do odnotowania, nie poprawiane tutaj):**
  - `deploy-plan.md:1194` mówi „in progress … not merged, PR-env verification pending”, wbrew lekcji „Status zmiany w deploy-plan.md ma być prawdziwy także po merge'u”;
  - roadmapa ma F-04 jako `in-progress`;
  - `out-of-app-alert-channel/change.md` ma `implemented` i nie jest zarchiwizowane;
  - tymczasem issue #66 jest zamknięte po weryfikacji produkcji.

## Related Research

- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` — audyt C2/C3 i macierz obserwowalności
- `context/archive/2026-10-18-empty-run-signal/research.md` — pusty przebieg, obserwowane liczności zakresów
- `context/archive/2026-10-12-daily-scheduled-run-freshness/research.md` — nocny przebieg, etykieta, koszty
- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md` — API wyszukiwania Wykopu, liczności dnia

## Aktualizacja po przewinięciu na `origin/main` (2026-10-24)

Gałąź przewinięta z `0917117` na `0bea22f`: #104 (archiwizacja S-16), #101 (domknięcie i archiwizacja F-04), #105 (S-12 zaparkowane). Wpływ na ustalenia wyżej:
- **Zastąpione:** punkt „Nieaktualne statusy F-04” w Historical Context. `deploy-plan.md:1194` mówi teraz „**Status (2026-10-24): done.**”, roadmapa ma F-04 jako `done`, a zmiana jest w `context/archive/2026-10-20-out-of-app-alert-channel/`.
- **Przeniesione ścieżki:** odwołania `context/changes/out-of-app-alert-channel/…` (§1, Summary, Historical Context) wskazują teraz `context/archive/2026-10-20-out-of-app-alert-channel/…`. Treść cytatów bez zmian, numery linii `plan.md` mogły się przesunąć (plik zmieniony w #101).
- **Bez wpływu:** zmiany w `WykopApiClient`/`WykopRepository` (maskowanie sekretów, klucz elementu) i w `trigger.sh`/`backup.sh` (#101) nie zmieniają zakresu nocy, pokrycia dni ani tras użytych w planie.

## Decyzje po badaniu (2026-10-24)

- **Wariant B.** Dni bez danych są widoczne w `/admin`, razem z wypełnionym zakresem dla ręcznego przebiegu. Rozstrzyga to Open Questions 1 i 6:
  - bez automatycznego uzupełniania w nocnym przebiegu (wariant C odrzucony: ryzyko `TRIGGER_RUN_TIMEOUT` 90 min i fałszywego alarmu przy oszczędności jednego kliknięcia);
  - bez nowego alarmu poza aplikacją, bo każda zła noc crona już alarmuje przez F-04 (§1).
- Otwarte dla `/10x-plan`: pytania 2–5 poniżej (definicja dnia bez danych, miejsce poza `/admin`, okno wstecz, NAS → Google Drive).

## Open Questions

Decyzje użytkownika (dla `/10x-plan`; 1 i 6 rozstrzygnięte, sekcja wyżej):
1. **Pokazywać czy uzupełniać** dni bez danych (roadmapa, Owner: user)? Warianty:
   - (a) tylko pokazać, np. lista dni bez pokrycia w `/admin` z wypełnionym formularzem zakresu;
   - (b) nocny `daily()` zaczyna od najstarszego niepokrytego dnia w oknie (najwyżej 7 dni). Wtedy trzeba ustalić, jak to się ma do `TRIGGER_RUN_TIMEOUT` 90 min i karencji checka;
   - (c) oba.
2. **Definicja dnia bez danych:** brak udanego przebiegu RANGE nad dniem (pokrycie przebiegami) czy brak postów z `published_at` w tym dniu (posty na dzień)?
3. **Gdzie widać:**
   - tylko admin (`/admin`, dzwonek?). Dzwonek wymaga migracji klucza powiadomień bez `run_id`;
   - czy także etykieta w feedzie dla użytkowników (FR-030 / US-01 mówią tylko o dacie ostatniego pomyślnego przebiegu).
4. **Okno wstecz:** ile dni sprawdzać (np. 7, 14, od pierwszego przebiegu)?
5. **Nadzór NAS → Google Drive** w zakresie S-13 czy poza nim? Wymaga pracy operatora w DSM i nowego checka w `checks.sh`.
6. **Czy S-13 potrzebuje nowego alarmu poza aplikacją.** Każda zła noc crona już alarmuje (F-04). Dziura po dwóch złych nocach jest więc już sygnalizowana dwoma alarmami, brakuje tylko informacji, które dni zostały bez danych.

Fakty do sprawdzenia w planie:
- Jak głęboko wstecz serwuje wyszukiwanie Wykopu (sprawdzono najwyżej ok. 12 dni) i jakie ma limity zapytań. Dotyczy wariantu (b) i dłuższego okna.
- Czy `date_from`/`date_to` Wykopu filtruje po `created_at`. Dotyczy definicji „posty na dzień”.
- Semantyka checka cron healthchecks.io przy harmonogramie Railway częstszym niż dzienny (maskowanie pominiętej nocy).
