---
date: 2026-10-17T17:46:45+0200
researcher: kwmacioszek (Claude Opus 5.5)
git_commit: 5b6eabb3935d2e32acd02597cb35efa2fb36f196
branch: feature/post-mvp-pipeline-audit
repository: kwmacioszek/szczur-radar
topic: "Audyt diagnostyczny pipeline'u i operacji po MVP: błędy i ryzyka, dług, testowalność, architektura"
tags: [research, audit, pipeline, llm, wykop, assessment, railway, ci, db-backup, pipeline-trigger, observability]
status: complete
last_updated: 2026-10-17
last_updated_by: kwmacioszek (Claude Opus 5.5)
---

# Research: audyt diagnostyczny pipeline'u i operacji po MVP

**Date**: 2026-10-17T17:46:45+0200
**Researcher**: kwmacioszek (Claude Opus 5.5)
**Git Commit**: 5b6eabb3935d2e32acd02597cb35efa2fb36f196
**Branch**: feature/post-mvp-pipeline-audit
**Repository**: kwmacioszek/szczur-radar

## Research Question

Kompleksowy audyt diagnostyczny przed kolejną roadmapą. Zakres (decyzja użytkownika 2026-10-17): **pipeline + operacje**. Obejmuje silnik przebiegów, klientów LLM, kontrakt oceny, źródło Wykop, codzienny cron, backup, deploy/IaC/CI i obserwowalność. Cele (wszystkie cztery wybrane): **błędy i ryzyka**, **dług i follow-upy**, **testowalność**, **architektura**. Poza zakresem: auth/konta, UI (poza tym, co dotyczy sygnałów o przebiegach), kontrakt SPA↔API.

Punkt wyjścia: mapa projektu `context/map/repo-map.md` (strefy ryzyka 1–4: silnik przebiegów, klienci LLM, kontrakt oceny, deploy/IaC Railway; obserwowane: backup).

## Summary

Silnik ma solidny rdzeń. Zmiany stanu to warunkowe `UPDATE`-y ze strażnikiem statusu. Jeden przebieg naraz pilnuje blokada doradcza i unikalny indeks. Watchdog, odzyskiwanie przy starcie i przyczyny przerwań z S-06 zamykają klasę „przebieg wiszący 22 h”. Audyt nie znalazł błędu, który dziś niszczy dane. Znalazł natomiast **luki w sygnalizacji i w odporności na awarie zewnętrzne**: część złych stanów jest dla operatora niewidoczna albo kosztuje próby postów, choć nie powinna.

Najważniejsze ustalenia (pełna lista w Detailed Findings):

1. **Przebieg, który nic nie pobrał, kończy się `succeeded` i przesuwa etykietę świeżości.** Status zależy tylko od nieudanych, martwych i błędów (`RunFlow.java:149-154`). Elementy Wykopu o nieoczekiwanym kształcie są liczone jako `skipped` i porzucane (`WykopRepository.java:131-135`). Zmiana API Wykopu dałaby co noc „sukces” z 0 postami: bez powiadomienia i z wyjściem crona 0.
2. **Awaria dostawcy LLM zużywa próby postów.** Brak bezpiecznika: każdy błąd wywołania poza konfiguracyjnym staje się wynikiem `failed` (`Assessor.java:130-131`), a 3 takie okna robią z posta `dead`. Flex przełącza się na tier standardowy tylko wtedy, gdy wszystkie 3 próby to 429 (`TransportRetry.java:108`).
3. **`LLM_TIMEOUT=120` nadal oznacza 120 ms.** Ta sama klasa błędu co incydent z 2026-10-06. Dwa inne ustawienia czasu odrzucają gołą liczbę (`LlmProviderProperties.java:175-183` wobec `ActivationSettings.java:137`).
4. **Część stanów jest niewidoczna albo blokuje kolejkę do restartu:** brakujący nocny przebieg (brak dead-man's switch), dni starsze niż D-2 bez uzupełnienia, przebieg `running` po martwej instancji, utracone powiadomienie, post z błędem technicznym ponawiany co noc bez końca.
5. **Bramka CI/IaC ma dwie dziury.** Job `railway-config` nie ma `needs:` i stosuje IaC na produkcji równolegle z testami (`ci.yml:205`). Crony deployują się bez Wait for CI (`railway.ts:218-222,262`).
6. **Dług:** 18 pozycji z follow-upów jest rozwiązanych w kodzie (checkboxy nieodhaczone), 14 nadal otwartych, w tym 7 Parked. `deploy-plan.md` ma nieaktualne statusy S-02, S-04 i S-06 oraz linki do `context/changes/…` przeniesionych do archiwum.
7. **Architektura:** Embabel w przebiegu kosztuje więcej, niż daje (liniowy graf, FIRE_ONCE, zombie po zatrzymaniu). Drugie źródło (Reddit) wymaga zmian w silniku wbrew regule NFR: awaria jednego źródła blokuje wszystkie, a świeżość jest globalna.

Ograniczenia: audyt nie dotykał żywego Railway ani produkcji. Lokalizacja bazy (S-04), zachowanie deployu wywołanego przez `config apply` wobec Wait for CI i okno SIGTERM→SIGKILL pozostają otwarte (Open Questions).

## Detailed Findings

Konwencja ścieżek: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`. **[O]** = zaobserwowane w kodzie (główny agent zweryfikował fragmenty oznaczone ✓), **[I]** = wnioskowanie bez testu, który by to potwierdzał. Waga: H/M/L.

### A. Silnik przebiegów (`api/pipeline/**`)

Działa poprawnie [O]:
- `start`, `finish`, `fail`, `count`, `error`, `stall` i `interrupt` filtrują po statusie (`api/pipeline/state/RunRecorder.java:61-127,166-221`).
- `claim` wymaga przebiegu `running` (`api/pipeline/state/PostTransitions.java:55-66` ✓).
- `record` i `release` wymagają `assessing_run_id = run` (`PostTransitions.java:78-80,100` ✓).
- Blokada doradcza (`api/pipeline/PipelineRunService.java:177`) i indeks `uq_pipeline_runs_one_running` (`V3__create_pipeline_and_posts.sql:39`) wykluczają dwa przebiegi `running` naraz.
- Zakres D-2…D-1 z DST jest liczony przez `atStartOfDay(ZONE)` i pokryty parametryzowanym `InternalPipelineRunIT`.

| # | Waga | Ustalenie | Dowód | Scenariusz |
|---|---|---|---|---|
| A1 | M | Post, którego ocena kończy się błędem technicznym w naszym kodzie (mapper, rozjazd SHA payloadu, naruszenie CHECK), wraca bez zużycia próby i nie ma licznika per post, więc jest ponawiany co noc bez końca [O ✓] | `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:224-236`; brak kolumny na posta w `V3…sql:84-123` (`processing_errors` tylko na przebiegu); `raw/PostgresRawPayloadStore.java:43-44` | Zmiana mappera odrzuca stare RAW, a wtedy każdy nocny RETRY kończy się `partial` z błędem i powiadomieniem admina, w nieskończoność (`PipelineRun.needsAttention`, `run/PipelineRun.java:283-286`) |
| A2 | M | Przebieg `running` po instancji, która zniknęła bez `onClose`, blokuje kolejkę do następnego restartu [O + I] | Odzyskiwanie działa raz, przed portem (`PipelineStartupRecovery.java:77`); watchdog widzi tylko przebiegi z pamięci tej instancji (`RunWatchdog.java:91-93`, `PipelineRunService.java:193`); błąd odzyskiwania jest tylko logowany (`PipelineStartupRecovery.java:94-97`) | SIGKILL starej instancji albo przebieg zakolejkowany na starej instancji po odzyskiwaniu nowej: `existsActive` daje 409 (`PipelineRunService.java:178`), cron ponawia 409 przez 60 min i kończy CRASHED; pomaga dopiero uśpienie lub restart API |
| A3 | M | Powiadomienie może przepaść: `runEnded` połyka `RuntimeException`, nie ma ponowienia ani outboxa [O ✓] | `api/notifications/RunNotifications.java:41-55`; wołane raz po commicie (`PipelineRunService.java:245,284,338`, `PipelineStartupRecovery.java:103`) | Chwilowy błąd bazy w tej chwili: nieudany przebieg admina nigdy nie trafia do dzwonka (cron ma jeszcze sygnał Railway, przebieg admina nie ma żadnego) |
| A4 | M | Wyścig `claim` z odzyskiwaniem: claim widzi przebieg `running` w migawce sprzed interrupt. Post zostaje `assessing` przy przerwanym przebiegu, a `record` (bez sprawdzania statusu przebiegu) zapisuje ocenę, której liczniki przebiegu nie policzą [I; READ COMMITTED] | `PostTransitions.java:55-66,74-92` ✓; znane resztkowe ryzyko S-06 F3 („`FOR SHARE` w `claim` pominięte”, `context/archive/2026-10-15-pipeline-run-history-dashboard/reviews/impl-review.md:74`) | Post niewidoczny w feedzie i na liście nieudanych do następnego `releaseOrphans` przy starcie; liczniki przebiegu rozjeżdżają się z `assessments` |
| A5 | L-M | `needsAttention` czyta liczniki z bazy, a status pochodzi z wyników w pamięci. Nieudany `count` to tylko WARN [O] | `ConcurrentAssessmentDispatcher.java:257-260`; `flow/RunFlow.java:149-154` ✓; `PipelineRun.java:278-286` | Przebieg `partial` z martwym postem, którego `count` padł, ma w bazie `dead=0`, więc nikt nie dostaje powiadomienia |
| A6 | L | Krok `assess` zapisany jako `succeeded` w przebiegu `failed` (nieudany check LLM albo odrzucony klucz) [O] | Dispatcher zwraca `stoppedBy` zamiast rzucać (`ConcurrentAssessmentDispatcher.java:126-131` ✓, `:215-222`); `RunRecorder.step` zamyka SUCCEEDED (`RunRecorder.java:158`) | Sprzeczny obraz w `/admin` i w logu crona |
| A7 | L | Zombie po zatrzymaniu w trakcie `fetch`/`store` dalej zapisuje posty z `first_run_id` martwego przebiegu. `llm.check()` nie jest wśród anulowanych zadań [I] | `step/PostStore.java:56-77` nie sprawdza statusu przebiegu; anulowanie `ConcurrentAssessmentDispatcher.java:180-188` | Posty podejmie kolejny RETRY, więc nic nie ginie; zombie może trzymać wątek Embabel do timeoutu checku (rozmiar puli Embabel nieznany) |
| A8 | L | Przypadki brzegowe zostawiające przebieg `queued`: `submit` po commicie przy zamkniętym executorze; koniec zakresu `23:59:59` gubi czasy z ułamkiem sekundy [O] | `PipelineRunService.java:183-189,149` | Tylko przy zamykaniu instancji albo przy źródle z milisekundami; naprawia następne odzyskiwanie |

### B. Klienci LLM, kontrakt oceny, Wykop

| # | Waga | Ustalenie | Dowód | Scenariusz |
|---|---|---|---|---|
| B1 | M | `LLM_TIMEOUT` przyjmuje gołą liczbę jako milisekundy; brak dolnej granicy. `PIPELINE_STALL_TIMEOUT` i `ACTIVATION_LINK_TTL` ją odrzucają [O ✓] | `api/config/LlmProviderProperties.java:175-183` ✓ (tylko `>30m` i `<=0`); `auth/activation/ActivationSettings.java:137-141` ✓; `pipeline/PipelineStallSettings.java:103`; brak przypadku w `LlmProviderConfigTest` | `LLM_TIMEOUT=300` = 0,3 s: check przed oceną padnie, więc przebieg kończy się `failed` co noc, dopóki ktoś nie poprawi zmiennej (incydent 2026-10-06 tej samej klasy) |
| B2 | M | Brak bezpiecznika: awaria dostawcy (5xx/timeout po 3 próbach HTTP) zużywa próbę każdego ocenianego posta. Przebieg zatrzymuje tylko `LlmConfigurationException` [O ✓] | `api/agents/assessor/Assessor.java:127-131` ✓; `PostTransitions.Outcome.after` (`PostTransitions.java:143-144`); `ConcurrentAssessmentDispatcher.java:215` | 20 min awarii OpenRouter w środku przebiegu: każdy post z tego okna traci próbę; 3 takie okna robią z posta `dead` (bez dalszych ponowień) |
| B3 | M | Flex przełącza się na tier standardowy tylko przy 3×429; mieszanka 429 i timeoutu/5xx kończy się `provider_error` i zużywa próbę. Zachowanie celowe (test `flexFailingOtherwiseDoesNotFallBack`) [O ✓] | `api/llm/transport/TransportRetry.java:100-111` ✓ | Wolny tier flex z timeoutami zużywa próby zamiast przejść na standard |
| B4 | M | Brak żywej ochrony przed dryfem modelu. `RecordingsReplayTest` odtwarza zamrożone nagrania (łapie dryf parsera, schematu i walidatora), ale nie wykryje cichej zmiany `openai/gpt-6-luna` ani różnicy flex wobec `default`. Nic nie alarmuje o rosnącym udziale `schema_violation` / `fabricated_evidence` [O] | `Assessor.java:89-93` (Provenance zapisuje model); `RecordingsReplayTest` | Jakość ocen spada po cichu; widać to tylko po rosnącej liczbie nieudanych postów |
| B5 | M | Paginacja Wykopu może po cichu gubić elementy: `received` liczy też duplikaty, a pusta strona kończy pętlę bez błędu przy `received < total` [O ✓] | `api/sources/wykop/WykopRepository.java:127,141-142` ✓ | Element usunięty lub przesunięty w trakcie stronicowania: pętla kończy się „kompletna”. Jedyną mitygacją jest ponowne pobranie D-2 następnej nocy |
| B6 | L-M | Jeden przejściowy błąd Wykopu (429, 5xx, transport) kończy cały przebieg `failed`; celowo brak ponowień w kliencie i w `SourceIngest` [O] | `sources/wykop/WykopApiClient.java:135-157`; odświeżenie tokenu przy 401 tylko raz (`:82-85`) | Nocny przebieg pada przez chwilowy błąd; cron exit 1, a dzień uzupełni dopiero następna noc jako D-2 |
| B7 | L | Pełny łańcuch przyczyn wyjątku SDK (nieoczyszczony z kluczy) trafia do logu ze stack trace'em. Komunikaty są maskowane, przyczyna nie [O dla logowania, I dla wycieku] | `ConcurrentAssessmentDispatcher.java:129` ✓ (`log.error(…, ex)`); `llm/openrouter/OpenRouterClient.java:193,197`; `llm/gemini/GeminiClient.java:121-122`; `TransportException.java:105-113`; `ProviderMessages.java:4-5` (niektórzy dostawcy cytują klucz w 401). Wykop celowo nie dołącza przyczyny (`WykopApiClient.java:32-33`) | Klucz LLM w logach Railway, jeśli dostawca zacytuje go w ciele błędu |
| B8 | L | Deterministyczne naruszenia kontraktu kosztują 3 płatne wywołania przed `dead`. OpenRouter nie dostaje `maxItems`/`maxLength`/`uniqueItems`; ucięcie na `MAX_OUTPUT_TOKENS` daje `invalid_structured_output` [O] | `llm/transport/ProviderSchemas.java`; `llm/LlmClient.java:21` | Długi post przy temperaturze 0 powtarza to samo naruszenie 3 razy |
| B9 | L | `RuntimeException` w próbie (nasz błąd albo błąd mapowania SDK) jest w czterech wskazanych miejscach klientów klasyfikowany jako `REJECTED` i zużywa próbę, wbrew Javadocowi [O] | `LlmClient.java:36-38`; `OpenRouterClient.java:195-197`; `DeepSeekClient.java:138-144`; `GeminiClient.java:106-107,125` | Błąd programisty wygląda jak odmowa modelu i zabija posty |
| B10 | L | Anulowanie: pule na wątkach platformowych, więc przerwanie nie zrywa blokującego odczytu z gniazda (koniec w ciągu jednego `LLM_TIMEOUT`, zgodnie z dokumentacją). `InterruptedIOException` jest klasyfikowany jako TIMEOUT do ponowienia; przy przejściu flex→default nie ma pauzy, więc może ruszyć jeszcze jedna próba [I] | `ConcurrentAssessmentDispatcher.java:291`; `TransportException.java:104`; `TransportRetry.java:130-137` | Zatrzymany przebieg wykonuje jeszcze jedno płatne wywołanie; brak testu przerwania w `llm/` |
| B11 | L | Czas `created_at` w powtórzonej jesiennej godzinie dostaje wcześniejszy offset [O] | `sources/wykop/WykopMapper.java:73` | `publishedAt` przesunięte o 1 h; najbliższa zmiana czasu 2026-10-25 |

Najgorszy czas jednego wywołania: `maxCallDuration` = (3×T + 2×60 s), ×2 dla flex (`TransportRetry.java:66-70`). Przy T=300 s i flex daje to 34 min, z czego wynika domyślny próg watchdoga 39 min. Faza połączenia DeepSeek (do 30 s na próbę) nie jest w tym ujęta [O].

### C. Operacje: cron, backup, CI/IaC, obserwowalność

| # | Waga | Ustalenie | Dowód | Scenariusz |
|---|---|---|---|---|
| C1 | **H** | Przebieg z 0 pobranych albo z samymi pominiętymi elementami kończy się `succeeded`. `skipped` nie trafia do `needsAttention`, do odpowiedzi `/internal`, do ostatniej linii crona ani do linii końcowej API (jest tylko w UI admina). `/api/freshness` przesuwa etykietę [O ✓] | `api/pipeline/flow/RunFlow.java:149-154` ✓; `WykopRepository.java:131-135` ✓; `PipelineRun.java:283-288`; `pipeline/api/InternalPipelineRunController.java:103-106`; `tools/pipeline-trigger/trigger.sh:259`; `PipelineRunService.java:306-310` | Wykop zmienia kształt elementu, nazwę tagu albo zwraca pustą listę: co noc „sukces” z 0 postami, cron exit 0, brak powiadomienia, etykieta „Dane zaktualizowane” wygląda świeżo |
| C2 | M | Brak dead-man's switch: cron, który nie wystartował (QUEUED, pominięty, zostawiony tymczasowy harmonogram), nie daje żadnego sygnału. Dotyczy też `db-backup` i synchronizacji NAS→Google Drive. Jedyny alarm to reguła konta Railway „deployment crashed”, której nie ma w IaC [O + I] | `.railway/railway.ts:228,268` (harmonogramy); deploy-plan S-05 krok 3 (ręczny harmonogram tymczasowy); cron QUEUED widziany 2026-10-11 | Kilka nocy bez danych i bez sygnału |
| C3 | M | Dni starsze niż D-2 nigdy nie są uzupełniane automatycznie, a świeżość mierzy koniec ostatniego przebiegu, nie pokrycie dni [O] | `InternalPipelineRunController.java:67-69`; `run/PipelineRunRepository.java:32-48` | 2+ nieudane noce zostawiają dziurę, dopóki admin ręcznie nie uruchomi zakresu |
| C4 | M | Job `railway-config` nie ma `needs:`, więc na `main` stosuje IaC na produkcji równolegle z testami. Apply wywołuje osobny deploy tego samego commita (deploy-plan „Two api deploys after one merge”). Czy taki deploy omija Wait for CI: nieustalone [O ✓ dla braku `needs:`, I dla obejścia] | `.github/workflows/ci.yml:12,54,72,104,180,193,205` ✓ (brak `needs:` w pliku) | Commit z czerwonym `backend` może trafić na produkcję przez deploy wywołany apply'em |
| C5 | M | Crony (`db-backup`, `pipeline-trigger`) deployują się bez Wait for CI. Komentarz-uzasadnienie „CI does not test tools/db-backup” jest nieaktualny, bo CI ma `db-backup-test` i `pipeline-trigger-test`. Faktyczną przeszkodą jest bramka, która odmawia zmiany `source.checkSuites` [O ✓] | `.railway/railway.ts:218-222,262` ✓; `ci.yml:180,193` ✓ | Merge zmieniający `tools/pipeline-trigger/**` deployuje cron mimo czerwonego testu; błąd z exit 0 (np. strażnik „skipped”) przechodzi po cichu |
| C6 | M | Bramka przepuszcza „bezpieczne” zmiany, które restartują usługi na produkcji (np. `limitOverride` Postgresa). Po apply/deployu nic nie sprawdza `/api/ready` ani tego, czy przebieg jest `RUNNING`. Dopasowanie zmiany źródła to regex na tekście CLI, bez testów [O] | `ci.yml:354-366,365`; `.railway/railway.ts:87-89` | Powtórka zawieszenia Postgresa z 2026-10-12 wyjdzie dopiero o 00:30 (cron) lub 02:00 (backup) |
| C7 | M | Każda zmienna ustawiona ręcznie bez deklaracji `preserve()` daje w planie `variable.delete`; bramka odmawia, CI na `main` jest czerwone, a deploye stają. Niezadeklarowane są m.in. `BACKUP_IN_ANY_ENVIRONMENT`, `BACKUP_TIMEOUT`, `BACKUP_KEEP_*`, `TRIGGER_HEARTBEAT_INTERVAL` i inne `TRIGGER_*`, a README każą je czasem ustawiać [O konfiguracja, I scenariusz] | `.railway/railway.ts:229-243,273` | Operator ustawia `BACKUP_TIMEOUT` i blokuje wszystkie kolejne merge'e |
| C8 | L-M | `TRIGGER_RUN_TIMEOUT=90m` obejmuje wszystkie przebiegi razem; po jego przekroczeniu cron kończy exit 1, ale przebieg w API trwa dalej (fałszywy alarm, prawdziwy wynik tylko w aplikacji) [O] | `tools/pipeline-trigger/trigger.sh:29,221,262-269` | Długi przebieg nadrabiający zaległości (~13 postów/min na flex) przekracza 90 min. Nakładka z backupem o 02:00 jest nieszkodliwa: `pg_dump` czyta jedną migawkę, a liczone tabele nie mają ścieżki usuwania |
| C9 | L | Listy tabel backupu odstają od migracji: brak `activation_tokens` (V7) i `notifications` (V9). Restore check nie ma dolnej granicy liczności: pusta tabela `posts` przechodzi [O ✓] | `tools/db-backup/restore-check.sh:12-13` ✓, `backup.sh:21` ✓, `backup.sh:171` | Nowa tabela nie jest sprawdzana. Zrzut przypadkiem wyczyszczonej bazy przechodzi, a 14 dni retencji i lustro NAS wypierają dobre kopie (zostaje tylko Google Drive). Usunięcie albo zmiana nazwy tabeli z listy wywala `db-backup-test` (`test.sh:93-105`) |
| C10 | L | `/api/ready` połyka wyjątek bez logu [O ✓] | `api/health/HealthController.java:42-44` ✓ | Brak przyczyny 503 w logach API |
| C11 | L | Ścieżki zakończone tylko logiem ERROR, bez alertu: powiadomienia (`RunNotifications.java:53-54`), odzyskiwanie (`PipelineStartupRecovery.java:94-97`), watchdog (`RunWatchdog.java:96-98`), zamykanie przebiegu (`PipelineRunService.java:313-314`), licznik (`ConcurrentAssessmentDispatcher.java:257-259`). Brak error trackera, actuatora, metryk i konfiguracji logback (tylko tekst na konsoli) [O] | j.w.; `pom.xml` bez actuator/micrometer | Awaria samej ścieżki alarmowej jest cicha |
| C12 | L | Ostrzeżenia Hikari (#36) nadal bez reakcji: brak `keepaliveTime` i `maxLifetime` [O] | `szczur-radar-api/src/main/resources/application.properties:7-20` | Pierwsze żądanie po bezczynności może trafić na zamknięte połączenie |
| C13 | L | Pomiary JVM w deploy-plan są nieaktualne: przy limicie 2 GB (`API_LIMITS`) `MaxRAMPercentage=75` daje ~1,5 GB heapu, a przy 2 vCPU JVM wybiera raczej G1 niż SerialGC [I] | `szczur-radar-api/Dockerfile:25`; `.railway/railway.ts:54` | Dokumentacja nie opisuje produkcji |
| C14 | L | Okno ponowień Caddy (`lb_try_duration 30s`) jest wąskie: pierwsza noc potrzebowała 19 s na obudzenie API. CI nie sprawdza proxy z upstreamem [O] | `szczur-radar-web/Caddyfile:31`; `ci.yml:85-99` | Wolniejsze budzenie daje użytkownikowi 502 |
| C15 | — | Kontrakt `trigger.sh` ↔ `/internal` jest spójny: parsowane pola odpowiadają `RunState`, klucze przypina `InternalPipelineRunIT.java:221-223`, a atrapa w `test.sh` je odwzorowuje. Brak wspólnego schematu [O] | `trigger.sh:156-160,238,254,259`; `InternalPipelineRunController.java:103-106` | — |

Macierz obserwowalności (jak dziś widać daną awarię):

| Awaria | Jak się pokazuje | Luka |
|---|---|---|
| Przebieg crona `failed`/`interrupted` | dzwonek + exit 1 crona → push (reguła konta) | brak, jeśli reguła istnieje |
| Przebieg admina `failed`/`interrupted` | tylko dzwonek | brak pusha; dzwonek może przepaść (A3) |
| `partial` z martwym postem albo błędem | tylko dzwonek, cron exit 0 | brak pusha |
| 0 pobranych albo same `skipped` | nic; `succeeded`, etykieta się przesuwa | **niewidoczne** (C1) |
| Brakująca noc / dziura w dniach | nic | **niewidoczne** (C2, C3) |
| Cron nie wystartował / QUEUED | nic | **niewidoczne** |
| Awaria backupu | CRASHED → push (reguła) | synchronizacja NAS/Drive niewidoczna |
| Zawieszony Postgres po apply | następny cron albo użytkownik | brak kontroli po apply (C6) |
| Skok kosztu LLM | nic do limitu $10 OpenRouter → 402 → przebieg `failed` | brak wczesnego ostrzeżenia |
| Dryf modelu / jakości | rosnące `failed` w UI | brak progu i alertu (B4) |
| Pełny dysk / WAL | nic, dopóki zapisy nie padną | **niewidoczne** |
| Linie ERROR w logu | tylko logi Railway | brak alertów (C11) |

### D. Testowalność

| Obszar | Pokrycie | Luka |
|---|---|---|
| Zakres, powtórka, próba na żądanie, awaria źródła, odmowa klucza | `PipelineAgentIT`, `ConcurrentAssessmentDispatcherTest` | awaria jednego z wielu źródeł; krok `succeeded` w przebiegu `failed` (A6) |
| Watchdog, zombie, następny przebieg, cudzy przebieg | `RunWatchdogIT` (4), `RunWatchdogTest` | zatrzymanie w trakcie `fetch`/`store` (A7) |
| Odzyskiwanie, przyczyna, kolejność przed portem | `PipelineStartupRecoveryIT`, `PipelineAgentIT` | claim zatwierdzony po odzyskiwaniu (A4); nieudane odzyskiwanie (A2); SIGKILL bez `onClose` |
| Powiadomienia | `NotificationsIT` (idempotencja) | utracone powiadomienie (A3) |
| Błąd techniczny | `ConcurrentAssessmentDispatcherTest` | ten sam post z błędem noc po nocy (A1) |
| Retry, backoff, fallback flex | `TransportRetryTest` (11) | przerwanie w trakcie wywołania (B10) |
| Klienci dostawców | `OpenRouterClientTest` (21), `DeepSeekClientTest`, `GeminiClientTest` (po 10) | 403 / nieważny klucz per wywołanie (Gemini 400); klucz cytowany w przyczynie (B7) |
| Ustawienia LLM | `LlmProviderConfigTest` | goła liczba w `LLM_TIMEOUT` (B1) |
| Parser, walidator, prompt | `OutputParserTest`, `ContractV2RulesTest`, `AssessmentPromptTest`, `RecordingsReplayTest` (481 nagrań) | żywy dryf modelu (B4) |
| Wykop | 6 klas testów na `LocalWykopServer` + 4 prawdziwe fixture'y | przesunięcie strony, pusta strona przed `total`, 429, DST, usunięte elementy (B5, B6, B11) |
| Kontrakt cron ↔ `/internal` | `InternalPipelineRunIT`, `tools/pipeline-trigger/test.sh` | brak wspólnego schematu |
| Backup | `tools/db-backup/test.sh` (w CI) | dolna granica liczności (C9) |
| Bramka IaC w CI | brak | logika jq/regex bez fixture'ów `plan.json` (C6) |
| Tylko ręczny runbook | — | odpalenie crona na Railway, reguła powiadomień, budzenie API przez Caddy, zawieszenie Postgresa, przywrócenie do żywej bazy, kopie NAS/Drive, przerwanie przez deploy, watchdog na produkcji, pola IaC gubione przez SDK |

Testy szerokie i wolne:
- `PipelineAgentIT` ma 645 linii, działa na Spring + Testcontainers, a jego testy zależą od kolejności (`@TestMethodOrder`, `@Order`).
- Czysta logika testowana tylko przez IT: wyliczanie D-2/D-1 i `RunState.of` (przez `InternalPipelineRunIT`), gałęzie `PipelineRunService.execute`/`stopStalled`/`onClose`.
- SQL `PostTransitions` i `RunRecorder` nie ma skupionych testów na poziomie bazy.

Żaden test w CI nie woła żywych API: tag `measurement` jest wykluczony w `pom.xml:28`.

### E. Architektura

- **Embabel w przebiegu (M)** [O]:
  - Graf GOAP jest liniowy: 6 akcji, jedno rozgałęzienie (`flow/RunFlow.java:23-28`).
  - Koszt:
    - każda akcja musi mieć `FIRE_ONCE` (`PipelineAgent.java:67-119`);
    - przyszłość `invokeAsync` plus semantyka zombie (`PipelineRunService.java:216-219`), a przerwanie nie dociera do akcji;
    - `migrate` i komendy CLI potrzebują `LLM_*`.
  - Ocena i tak omija `createObject` Embabel.
  - Zgodne z pozycją Parked „przebieg bez Embabel” (`context/foundation/roadmap.md:328-331`).
- **Drugie źródło (Reddit) dotknie silnika (M)**, wbrew NFR „nowe źródło zmienia tylko pozyskanie i mapowanie” [O]:
  - `SourceIngest.fetch` pobiera źródła po kolei, a jedno nieudane zawala krok i porzuca posty pozostałych (`pipeline/step/SourceIngest.java:65-79`). Awaria Reddita blokowałaby więc co noc Wykop (`InternalPipelineRunController.java:67-68`).
  - Cały pobrany batch leży w pamięci na tablicy Embabel (`RunFlow.FetchedBatch`).
  - Etykieta świeżości jest globalna, a nie per źródło (`PipelineRunRepository.java:39-48`).
  - Wymagane migracje 3 zbiorów CHECK: `V3…sql:75,111`, `V5__pipeline_run_sources.sql:10`.
  - Porty po stronie źródła są czyste: `sources/SourceFetcher`, `RawInputMapper`. Według przeszukania `src/main` przez pracownika audytu Wykop pojawia się poza `sources/` w dwóch miejscach: w Javadocu `raw/RawPayloadStore` i w `pipeline/CLAUDE.md`.
  - Komentarze z Reddita wymagają zapisu rodzica w RAW.
- **Złamane granice (L)** [O]:
  - encja `posts/Assessment` → enumy w `config/LlmProviderProperties` (`posts/Assessment.java:26-28`); z tych enumów korzystają też 3 konwertery, `Provenance`, `LlmResponse`, `FailedPostController` i `PipelineStallSettings`. Przeniesienie `Provider`/`ServiceTier`/`ReasoningEffort` do `llm/` usuwa krawędź encja→config;
  - cykl `pipeline` ↔ `notifications` (`RunNotifications.java:9-10`, `notifications/api/NotificationController.java:25-29`);
  - `posts` zapisywane dwoma mechanizmami: JPA w `PostStore` i surowym SQL w `PostTransitions`;
  - `persistence/converters/*` importują enumy z `pipeline.run`;
  - `api/PipelineRunController` sięga do `state/RunRecorder.inFlight` (`PipelineRunController.java:156`).
- **Duplikacja (L)** [O]:
  - reguła „najnowsza ocena `ok`” w dwóch dialektach SQL: `PostRepository.java:89-90` i `AssessmentRepository.java:29-30` (też w Parked);
  - predykat resztek dwa razy w `PostRepository.java:40,49`;
  - „oceniany = stored/failed” w `AssessmentSelection.java:63` i `PostTransitions.java:58`;
  - parsowanie czasów `DurationStyle.detectAndParse` w 3 kopiach (`LlmProviderProperties`, `ActivationSettings`, `PipelineStallSettings`) z różnymi regułami (B1);
  - bloki catch/klasyfikacji/filtra treści w 3 klientach LLM.
- **Prawdopodobnie martwy kod (L)** [I]: OpenRouter buduje dodatkowy `OpenAIOkHttpClientAsync`, raczej nieużywany.
- **IaC (L)** [O]: zmienne `RAILPACK_JDK_VERSION`, `DATABASE_URL` i `JWT_SECRET` (zapieczętowany, nieużywany) zostają w `railway.ts:123,133,137` tylko dlatego, że bramka odmawia `variable.delete`. Da się je usunąć tak jak bucket PITR: operator najpierw kasuje je w Railway, potem zmiana usuwa je z pliku i plan zostaje pusty.
- **Sprzężenie przez referencje env**: cron czyta zapieczętowany token API przez referencję (`railway.ts:272`), co sprawdza dopiero runtime. `db-backup` odwołuje się do bucketu po nazwie projektowej, co dało kolizję 2026-10-12.

### F. Dług i follow-upy (stan na HEAD)

| Pozycja | Werdykt | Dowód |
|---|---|---|
| F-02 F7, F8a–F8e | rozwiązane w kodzie, checkboxy nieodhaczone | `Assessor.java:62-65,150`; `LlmProviderProperties.java:59,69,85,97`; `ChatResponses.java:33-37`; `WykopApiClient.java:30,42,172-181`; `ProviderSchemas.java:34,50,60` |
| F-02 F8f (niewidoczne znaki) | zamknięte decyzją; wraca z Redditem (U+200B w pilotażu) | `context/archive/2026-10-06-first-assessed-wykop-feed/follow-ups/f8f-invisible-characters.md` |
| S-02: spalanie prób przy 401 całego przebiegu, wolny `store` | rozwiązane | `llm/LlmConfigurationException.java`; `step/PostStore.java:42,60`; `application.properties:10,37` |
| S-02: PR env deployuje web ze starego commitu | otwarte, przyczyna nieznana (powtórzyło się w S-05) | deploy-plan, sekcja „PR-env verification: the web deployment's commit” |
| api-docker-image F4 / issue #36 Hikari | otwarte (L) | `application.properties:7-20` |
| cli-user-onboarding F4 (wspólny parser czasów) | otwarte i urosło do 3 kopii (M) | B1 |
| S-06 F3 (wyścig claim) | zawężone, nie zamknięte (L) | A4 |
| Parked: wydajność filtra feedu | otwarte, OK do ~20k postów | `posts/PostRepository.java:100` |
| Parked: przebieg bez Embabel | otwarte (M) | E |
| Parked: LLM hardening (kierunek, ironia) | otwarte (M, jakość); progi nadal 0,70/0,70/0,10 wobec celu ≥75%/≤3% | `src/test/java/…/measurement/GoldMetrics.java:55,63,72` |
| Parked: komunikat błędu świeżości, ETA przebiegu, obrazki, self-hosted runner (#30) | otwarte (L) | `szczur-radar-web/src/views/FeedView.vue:172`; `ci.yml` (wszystkie joby na `ubuntu-latest`) |
| Issue #51 railway-resource-limits | rozwiązane w `002da67` (#52), issue nadal otwarte | `.railway/railway.ts:52-57` |
| Issue #17 S-04 | runbook po merge'u niewykonany: lokalizacja bazy produkcji (M: `C`/`POSIX` po cichu psuje `KĘTY`→`Kęty`) | ostatni komentarz issue sprzed merge'a |
| Issue #19 S-06 | w toku zgodnie z planem; kroki 1–2 zrobione, pierwsza noc 2026-10-18 | komentarz 2026-10-17 15:07 |

Licznik: 18 rozwiązanych (+F8f decyzją), 14 otwartych (w tym 7 Parked i #19 w toku), 3 grupy poza zakresem (auth/UI).

Nieaktualna dokumentacja:
- `context/deployment/deploy-plan.md`:
  - `:170` S-02 „not merged” (zmergowane `1a33fcc`);
  - `:672` S-04 „not merged” (`c80e846`);
  - `:700` S-06 „not merged” (`5b6eabb`);
  - linki do `context/changes/{admin-bootstrap-and-login, api-docker-image, production-data-gate, filter-by-assessment, pipeline-run-history-dashboard}`, dziś w `context/archive/…` (m.in. `:30,99,138,237,678,769,797`);
  - Fazy 1–6 opisują FastAPI/Hypercorn;
  - tabela JVM (C13).
- `context/foundation/roadmap.md`:
  - Parked: „backup po stronie platformy (F-01)”, a w rzeczywistości to własny `pg_dump`;
  - `:325` link do `context/changes/daily-scheduled-run-freshness/…`.
- `context/foundation/infrastructure.md:38`: „cron service for the daily LangGraph pipeline”.
- Komentarz w `.railway/railway.ts:221-222` (C5).

## Code References

- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunFlow.java:149-154`: status przebiegu, bez warunku na liczbę pobranych (C1)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:127-142`: `skipped`, `received` z duplikatami, koniec pętli (C1, B5)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/LlmProviderProperties.java:175-183`: `LLM_TIMEOUT` bez odrzucania gołej liczby (B1)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationSettings.java:137-141`: wzorzec odrzucenia gołej liczby
- `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/Assessor.java:127-131`: `LlmCallException` inny niż `LlmConfigurationException` (ten jest rzucany dalej) → wynik `failed` (B2)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/transport/TransportRetry.java:100-111`: fallback flex tylko przy 3×429 (B3)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:124-131,220-236`: check LLM logowany z przyczyną, błąd techniczny bez próby (B7, A1)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/PostTransitions.java:55-92`: `claim` ze strażnikiem przebiegu, `record` tylko po właścicielu (A4)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/RunNotifications.java:41-55`: powiadomienie połyka wyjątki (A3)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStartupRecovery.java:77,94-97`: jednorazowe odzyskiwanie, błąd tylko logowany (A2)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/health/HealthController.java:42-44`: 503 bez logu (C10)
- `.github/workflows/ci.yml:205,354-366`: `railway-config` bez `needs:`, reguły bramki (C4, C6)
- `.railway/railway.ts:218-222,262`: crony bez `checkSuites` (C5)
- `tools/db-backup/restore-check.sh:12-13`, `tools/db-backup/backup.sh:21,171`: listy tabel, brak dolnej granicy (C9)
- `tools/pipeline-trigger/trigger.sh:29,221,262-269`: wspólny limit 90 min (C8)

## Architecture Insights

- **Wzorzec stanu jest dobry i warto go utrzymać:** jeden warunkowy `UPDATE` na zmianę, liczba wierszy jako odpowiedź, zero blokad wierszy. Wszystkie luki z sekcji A są na styku mechanizmów (odzyskiwanie ↔ claim, pamięć ↔ baza, po commicie ↔ powiadomienie), a nie w pojedynczych zapisach.
- **Model „próba = zapisany wynik `failed`” nie odróżnia winy posta od winy dostawcy ani od winy naszego kodu.** To wspólne źródło A1, B2, B3, B8 i B9: awaria dostawcy zużywa próby postów (B2, B3). Błąd w naszym kodzie poza klientem LLM (ścieżka `Outcome.Error`, A1) nie zużywa żadnej, więc post wraca bez końca. Z kolei `RuntimeException` wewnątrz klienta LLM (B9) zużywa próbę jako `REJECTED`. Każda zmiana tu zmienia kontrakt `PostTransitions.Outcome` i liczniki przebiegu.
- **Sygnalizacja opiera się na statusie przebiegu**, który mierzy błędy oceny, ale nie kompletność danych (C1, C3). Brakuje pojęcia „pokrycie dnia” albo „oczekiwana liczba postów”.
- **Bramka IaC chroni przed zniszczeniem, nie przed restartem ani przed zablokowaniem pracy** (C4, C6, C7). Wszystkie trzy luki wynikają z tego, że jedyną kontrolą jest jq/regex na planie, bez kontroli po fakcie.
- **Embabel i model jednej instancji** (kolejka i watchdog w pamięci) to dwa założenia, które odpowiadają za większość złożoności S-06 (zombie, przyszłości, wyścigi przy nakładce deployu).

## Historical Context (from prior changes)

- `context/archive/2026-10-06-first-assessed-wykop-feed/`: incydenty pierwszego przebiegu produkcyjnego (401, flex w HTTP 200); lekcja FIRE_ONCE (`context/foundation/lessons.md:82-87`). Wspiera B2/B3 (supported: naprawiono klasyfikację błędu w ciele 200; nie naprawiono zużywania prób przez awarię dostawcy, bo to inna klasa).
- `context/archive/2026-10-10-openrouter-in-body-errors/`: błąd w ciele 200 klasyfikowany po `code`, brak `choices` ponawiany. Werdykt dla „flex nie traci prób”: partial, bo mieszanka 429 i timeoutu nadal zużywa próbę (B3).
- `context/archive/2026-10-15-pipeline-run-history-dashboard/reviews/impl-review.md:74`: S-06 F3. Wyścig claim/stall świadomie zawężony, `FOR SHARE` pominięte (A4: supported, residual).
- `context/archive/2026-10-12-cli-user-onboarding/follow-ups/review-fixes.md`: F4 (wspólny parser czasów, `LLM_TIMEOUT=120` = 120 ms) nadal otwarte. Werdykt: supported, bo S-06 dodało trzecią kopię.
- `context/archive/2026-10-04-api-docker-image/follow-ups/review-fixes.md:3-7`: Hikari F4, otwarte (#36).
- `context/deployment/deploy-plan.md`, „Every merge redeploys every service”: przyczyna przerwania 2026-10-13 naprawiona w #58. Czy merge samej dokumentacji jest teraz `SKIPPED`: niesprawdzone (pierwszy taki merge `eee1eda`, wynik niezapisany).
- `context/foundation/roadmap.md:328-331`: Parked „przebieg bez Embabel” (z plan-review S-06). Werdykt audytu: supported (E).

## Related Research

- `context/map/repo-map.md` i `context/map/evidence/` (mapa projektu z 2026-10-17, ten sam branch): strefy ryzyka, które wyznaczyły zakres.
- Brak wcześniejszego `research.md` o przekrojowym audycie. Najbliższe: researche zmian S-02 i S-06 w `context/archive/`.

## Open Questions

Do rozstrzygnięcia poza kodem (wymaga żywego Railway lub produkcji):
1. Lokalizacja bazy produkcji (`LC_CTYPE`) dla filtra S-04: runbook z issue #17, krok 2.
2. Czy deploy wywołany przez `railway config apply` czeka na Wait for CI (C4).
3. Czy merge samej dokumentacji po #58 jest `SKIPPED` (`eee1eda`).
4. Czy reguła konta „deployment crashed” obejmuje crash loop API i czy nadal jest skonfigurowana.
5. Okno SIGTERM→SIGKILL Railway i to, czy stara instancja przyjmuje ruch po odzyskiwaniu nowej (A2, A4).
6. Aktualne ostrzeżenia Hikari w logach produkcji (#36): po wyłączeniu uśpienia Postgresa przyczyna mogła się zmienić.
7. Przyczyna przebiegu „trwającego” 22 h z 2026-10-09: S-06 go ograniczyło, ale nie wyjaśniło.

Do rozstrzygnięcia w kodzie lub bibliotekach:

8. Rozmiar puli wątków Embabel i zachowanie `invokeAsync`, gdy plan nie istnieje (A7).
9. Semantyka przerwania OkHttp/Okio (czy czyści flagę przerwania) i read-timeout `JdkClientHttpRequestFactory` dla ciała odpowiedzi DeepSeek (B10).
10. Czy wyszukiwarka Wykopu zwraca usunięte elementy i w jakim kształcie (B5).

Decyzje produktowe dla planu (nie fakty):
- Czy awaria dostawcy LLM ma w ogóle zużywać próby postów i czy wprowadzić bezpiecznik dla przebiegu (B2/B3).
- Czy „0 pobranych” to błąd, ostrzeżenie czy normalny stan (np. pusty dzień na tagu) i jaki próg (C1).
- Czy automatycznie uzupełniać dni starsze niż D-2 (C3).
- Czy wprowadzić zewnętrzny sygnał (error tracker, heartbeat/dead-man's switch) i jaki koszt jest akceptowalny przy limicie $10.
- Kolejność: Embabel out → potem Reddit, czy odwrotnie (E).
