# Evidence: rules guarded in the code

**Zakres:** cały zakres kodu z `0-sources.md`: `szczur-radar-api/`, `szczur-radar-web/`, `tools/pipeline-trigger/`, `tools/db-backup/` (bez podziału na grupy). Stan: head 5b4ac92.

**Aliasy:** `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`; `web/` = `szczur-radar-web/src/`; `mig/` = `szczur-radar-api/src/main/resources/db/migration/`.

**Warstwy przeszukane:** baza (V1–V13), kod domenowy i aplikacyjny, wejścia HTTP/CLI/cron, UI, transakcje, praca w tle.

**Metoda:** przeczytane w całości V1–V13, `pipeline/**` (poza prostymi enumami), `posts/**`, `raw/**`, `notifications/**`, `auth/**`, `user/**`, `internal/**`, `cli/**` (poza `CommandLine`, `PasswordPrompt`), `config/` (Security, Session, ErrorCode, wyjątki), `sources/**` (poza dalszą częścią `WykopApiClient`/`RedditApiClient`), `agents/assessor/**` (poza `prompt/`), `trigger.sh`, `backup.sh` (poza liniami 31–59), `tables.sh`. Selektywnie (grep): `llm/**`, `alerts/**`, `restore-check.sh`, `nas-retention.sh`, `web/`.

**Czego nie pokryto:** `tools/deploy-gate`, `tools/healthchecks`, `.github` (wykluczone); `agents/assessor/prompt/` (treść promptu); klienci dostawców LLM poza grepem; testy tylko jako fakt istnienia (`AuthorizationCompletenessIT`, `ClosedSetsIT`, `JpaSchemaIT`, `SpaRulesTest`, `PostColumnsIT`, `web/contract/rules.test.ts`), bez czytania ich treści.

**Skażenie źródeł:** w trakcie pracy harness sam wstrzyknął treść `api/pipeline/CLAUDE.md` do kontekstu (nie była czytana z wyboru). Nie użyto jej jako dowodu: każdy wpis niżej ma cytat z kodu przeczytanego w tej sesji.

**Oznaczenia:** kolumna „reguła" to zawsze moje słowa (`inference`). Istnienie strażnika i cytat to `evidence`, o ile nie zaznaczono inaczej. Komentarze w kodzie przytaczam jako słowa kodu, nie jako dowód działania.

## Guarded rules

### 1. Kolejka i cykl życia przebiegu (`pipeline_runs`)

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| Co najwyżej jeden przebieg `running` | DB: `uq_pipeline_runs_one_running` `mig/V3:39`; wątek: `newSingleThreadExecutor` `api/pipeline/PipelineRunService.java:136-137` | każdy zapis `status`; jedyny zapis → `running` to `RunRecorder.start` `api/pipeline/state/RunRecorder.java:63-68` | hard (data) | named error po stronie DB; aplikacja nie mapuje tego błędu: `execute` łapie `RuntimeException` (`PipelineRunService.java:346`) |
| Nowe żądanie startu jest odrzucane, gdy cokolwiek jest `queued`/`running` | app: `queue()` `PipelineRunService.java:217-223`, `existsActive` `api/pipeline/run/PipelineRunRepository.java:32-34`, advisory lock `PipelineRunService.java:274-276` | oba wejścia (admin `api/pipeline/api/PipelineRunController.java:104-106`, cron `.../InternalPipelineRunController.java:68`) idą przez `startRange`/`startPosts` | hard (app), DB nie ma odpowiednika dla `queued` | named error `pipeline_busy` 409 `.../PipelineRunExceptionHandler.java:20-23`, `api/config/ErrorCode.java:51` |
| Start tylko z `queued` | `RunRecorder.java:64-65` | jedyna ścieżka | hard (app) | `false` → przebieg się nie wykonuje (`PipelineRunService.java:339`) |
| Stan końcowy się nie zmienia: każda zmiana jest warunkowym `UPDATE` na oczekiwany status | `finish:81-82`, `fail:98-99`, `count:114`, `source:145`, `touch:172`, `error:179`, `interrupt:253`, `stall:272-274`, `interruptAll:294`, `interruptOrphans:328` (wszystko `RunRecorder.java`) | wszyscy pisarze `pipeline_runs.status` znalezieni w kodzie | hard (app): baza przyjęłaby dowolne przejście (brak triggera) | cicho: 0 wierszy, bez logu; to działanie zamierzone |
| Encja nie zmienia stanu przebiegu | brak setterów w `api/pipeline/run/PipelineRun.java`; kolumny postępu `insertable=false, updatable=false` `:102-127`; „blind save" tylko przy tworzeniu `PipelineRunService.java:222` | jedyne `saveAll` | hard (app) | n/d |
| Rodzaj przebiegu spina pola: `range` ma zakres i źródła, `posts` ma identyfikatory, inni nie mają | DB: `ck_pipeline_runs_range` `mig/V4:19-22`, `ck_pipeline_runs_requested_posts` `mig/V3:35`, `ck_pipeline_runs_sources` `mig/V12:29-31`; app: fabryki `PipelineRun.java:155-160,179-181` | zawsze | hard (data) | `IllegalArgumentException` / naruszenie CHECK |
| `from <= to` | DB `mig/V4:22`; encja `PipelineRun.java:155`; kontroler `PipelineRunController.java:189` | trzy warstwy | hard (data) | 400 `invalid_request` |
| Zakres admina: `to <= dziś`, najwyżej 7 dni, `postIds` 1–100 bez nulli i bez pól zakresu | kontroler: `PipelineRunController.java:60,66,184-191`; SPA: `web/missingDays.ts:5` (tylko dzielenie podpowiedzi) | **tylko** `POST /api/admin/pipeline-runs`; `PipelineRunService.startRange` i `PipelineRun.range` limitów nie sprawdzają | hard (app): drugie wejście (cron) ma stałe D-2…D-1 `InternalPipelineRunController.java:67-68` | 400 `invalid_request` |
| Przyczyna przerwania tylko na `interrupted`; zbiór przyczyn zamknięty | `ck_pipeline_runs_interruption_status` `mig/V8:17-18`, `ck_pipeline_runs_interruption_cause` `mig/V11:10-11`; enum `.../run/InterruptionCause.java:12-28` | zawsze; odwrotności (`interrupted` ⇒ przyczyna) baza nie wymaga | hard (data), w jedną stronę | CHECK |
| Krok tylko w przebiegu `running`, zamykany raz | `openStep` `RunRecorder.java:347-351`, `endStep:366-367`, `closeSteps:389-390` | wszystkie kroki przez `RunRecorder.step` | hard (app) | `RunNoLongerRunningException` (INFO `PipelineRunService.java:358-359`) |
| Przebieg bez postępu przez `PIPELINE_STALL_TIMEOUT` kończy `failed` | `stall` `RunRecorder.java:269-281`, `stopStalled` `PipelineRunService.java:461-482`, `RunWatchdog.java:93-113`; granice ustawienia `PipelineStallSettings.java:42-51,108-115` | tylko przebiegi tej instancji (`own`) | hard (app) dla własnych; cudze: reguła niżej | run `failed` z tekstem „no progress for …" `RunRecorder.java:270` |
| Martwa kolejka: przebiegi bez aktywności są oznaczane `lost`, wszystkie albo żaden | `interruptOrphans` `RunRecorder.java:325-339`, wywołanie tylko gdy `own.isEmpty()` `PipelineRunService.java:246` | tylko przy żądaniu startu | soft: bez żądania nic tego nie zrobi; to samo kryterium żywotności drugi raz w SQL `PipelineRunRepository.java:58-63` | logs and continues + `ORPHAN_CLEANUP_FAILED` `PipelineRunService.java:250-256` |
| Deploy nie przerywa żywego przebiegu | `GET /api/deploy-safety` `.../DeploySafetyController.java:50-70` | tylko odpowiedź dla bramki CI (brama wykluczona z zakresu); kod nie blokuje deployu | soft; bypass: `tools/deploy-gate` poza zakresem, instancja i tak zamyka przebieg `onClose` `PipelineRunService.java:539-565` | 503 bez bazy `:55-57` |

### 2. Etapy postu (`posts`)

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| Spójność etap↔liczba prób: `stored`=0, `failed`=1–2, `dead`=3, `assessing`/`assessed`≤2 | DB `ck_posts_stage_attempts` `mig/V3:116-121`; stałe `api/posts/Post.java:40`, `web/api/posts.ts:8`, `web/contract/rules.json` (`maxAssessmentAttempts`) | każdy zapis | hard (data) | CHECK |
| `assessing` ⇔ ma właściciela | `ck_posts_assessing_owner` `mig/V3:122` | każdy zapis | hard (data) | CHECK |
| Przejścia etapów: `stored`/`failed`→`assessing`→`assessed`/`failed`/`dead` albo powrót | `api/pipeline/state/PostTransitions.java`: `claim:72-75`, `record:95-97`, `release:119`, `releaseFailed:132-133`, `releaseAll:162`, `releaseOrphans:173-174`, `Outcome.after:203-209` | jedyny pisarz `posts.stage` poza `INSERT` (grep po `api/`) | hard (app): baza przyjmie `assessed`→`failed` z attempts=1 | 0 wierszy → `NotTaken`, bez błędu |
| Claim tylko w przebiegu `running` | `PostTransitions.java:74` | jedyna ścieżka | hard (app) | pusty `Optional` → pomijamy post |
| Wynik zapisywany tylko gdy przebieg nadal trzyma post | `PostTransitions.java:97`, `:104-106` | jedyna ścieżka do `assessments` (`:107`) | hard (app) | `false` → wynik LLM porzucany, `NotTaken` `ConcurrentAssessmentDispatcher.java:294-297` |
| Post `dead` nie wraca: brak ożywiania | `claim` wymaga `stored`/`failed`; `startPosts` odrzuca `PipelineRunService.java:198-201`; `AssessmentSelection.java:63-64`; SPA `web/views/AdminView.vue:426,244`; żadnego pisarza z `dead` | wszystkie ścieżki | hard (app) | named error `post_dead` 400 |
| Próbę zużywa tylko zapisany `failed`; błąd kodu i awaria dostawcy nie | `PostTransitions.record` (jedyna inkrementacja `assessment_attempts`); dyspozytor `ConcurrentAssessmentDispatcher.java:308-337,338-358`; `Assessor.java:131-136` | jedyna ścieżka | hard (app) | różne, zob. niżej |
| Trzy kolejne błędy techniczne wstrzymują post („on hold"), admin nadal może go wziąć | `Post.java:46`, `PostRepository.java:43-44,56-57`, `countTechnicalFailure`/`releaseFailed` w `PostTransitions`; reset `:96`; CHECK tylko `>= 0` `mig/V10:11` | retry pomija; POSTS bierze `AssessmentSelection.java:60-65` | soft z założenia; bypass: `POST /api/admin/pipeline-runs` z `postIds` | brak stanu w DB: wyliczane z licznika `posts/api/FailedPostController.java:92` |
| Jeden przebieg zakresowy ocenia tylko to, co sam zapisał pierwszy raz | `AssessmentSelection.fromStored` `:37-44`, `PostRepository.java:65-66` | RANGE | hard (app) | n/d |
| Retry bierze resztki bez limitu liczby | `findLeftoverIds` `PostRepository.java:38-45`; komentarz „no cap in S-02" `AssessmentSelection.java:48-49` | RETRY; kolejkowany tylko przy `startRange` `PipelineRunService.java:185` | hard (app) | n/d |

### 3. Tożsamość i przechowywanie postu

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| Post raz na `(source, item_type, source_item_id)` | DB `uq_posts_source_item` `mig/V4:16`; app `PostStore.java:62-63,99-112` | `PostStore` jedyny `INSERT` (`posts.save` `:91`) | hard (data) | **swallowed silently** `PostStore.java:76-78`, zob. Failure handling |
| Źródło z zamkniętego zbioru, rejestrowane w czterech CHECK | `ck_posts_source` `mig/V12:25`, `ck_raw_payloads_source` `:27`, `ck_pipeline_runs_sources` `:29-31`, `ck_pipeline_run_sources_source` `:44`; `api/sources/Source.java:9-18`; `web/contract/rules.json:31` | każdy zapis | hard (data) | CHECK |
| Typ pozycji wspólnego modelu, nie nazwa źródła | `ck_posts_item_type` `mig/V4:15`; `api/agents/assessor/contract/ItemType.java:10`; `WykopMapper.java:49-61`; `RedditMapper.java:50` | każdy zapis | hard (data) | CHECK |
| Kanał niepusty; zbiór otwarty | `ck_posts_channel` `mig/V13:13`; `Post.java:141`; `SourcePost.java:35` | każdy zapis | hard (data) | CHECK, ale przy zapisie patrz swallowed silently |
| Treść surowa oddzielnie, jeden post = jeden payload | `uq_posts_raw_payload` `mig/V3:107`, `fk_posts_raw_payload :108`, `RawPayloadStore.java:14-27` | `PostStore.save` | hard (data) | CHECK |
| Integralność surowego payloadu | `ck_raw_payloads_sha256` `mig/V3:78`, weryfikacja przy każdym odczycie `api/raw/PostgresRawPayloadStore.java:43-45` | każdy `load` | hard (app) dla zawartości | `IllegalStateException` → błąd techniczny postu albo 500 |
| Ocena budowana z surowego payloadu, nie z kolumn postu | `api/pipeline/assess/AssessmentInputs.java:32-38` | każda ocena | hard (app) | `IllegalStateException` przy braku mappera `:34-36` |
| Autor, URL, id nie trafiają do modelu | `Item` nie ma takich pól `.../contract/Item.java:15-17`; mappery `WykopMapper.java:65`, `RedditMapper.java:56-57` | każda ocena | hard (app), z typu | n/d |
| NUL nie trafia do bazy | `api/text/Nul.java`; `Post.java:141-148`; `Assessment.java:153-158`; `PostTransitions.java:185` | teksty postu i oceny; `source_item_id` **nie** jest czyszczony `Post.java:140` | hard (app) | **silently corrects** |

### 4. Oceny (`assessments`) i kontrakt V2

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| `ok` ma output i brak klasy błędu; `failed` odwrotnie | DB `ck_assessments_outcome` `mig/V3:165-167`; `AssessmentResult.java:45-50` | każdy zapis | hard (data) | CHECK / `IllegalArgumentException` |
| Provenance: prefiltr bez szczegółów wywołania, model z wersją promptu, tierem i ≥1 próbą | `Provenance.java:69-77` | aplikacja; baza tylko `NOT NULL`/CHECK wartości `mig/V3:169-175` | hard (app) | `IllegalArgumentException` |
| Ocena niezmienna po zapisie | wszystkie kolumny `updatable=false` `api/posts/Assessment.java:64-140`; repozytorium bez `UPDATE` | jedyny pisarz `PostTransitions.java:107` | hard (app); baza przyjęłaby `UPDATE` | n/d |
| Najnowsza próba: `assessed_at DESC, id DESC` | `AssessmentRepository.java:29-35`, `PostRepository.java:113` | odczyty | hard (app) | n/d |
| Parser nic nie naprawia: ścisły JSON, potem schemat V2 | `OutputParser.java:68-94`; `ContractJson.java:16-23` | każda odpowiedź modelu | hard (app) | `invalid_structured_output`/`schema_violation`, zapisana próba `failed` |
| Limity kształtu: `targets` ≤12, `claims` ≤24, `anchor.quote` 1–100, `evidence.quote` 1–320, `statement` 1–240, `claim_ids` ≤4, `target_ids` ≤12, bez dodatkowych pól | `src/main/resources/assessor/assessment-output-v2.schema.json:19,24,67,83,102,106,116` | każda odpowiedź | hard (app) | jw. |
| D1–D15: identyfikatory, cytaty dosłowne, tabela stance, D14 stanowisko oparte na claimie autora, D15 nieinwestycyjne bez treści | `.../validation/RuleId.java:14-57`; `ContractV2Rules.java:30-35`; `validation/rules/*`; `StanceTable.java:13-18` | każda odpowiedź przez `Assessor.evaluate` `:100` | hard (app) | `failed` z listą naruszeń, próba się liczy |
| Pierwszeństwo klasy błędu | `ValidationResult.java:10-11,26-31` | jw. | hard (app) | n/d |
| Cytat: zgodność po normalizacji białych znaków, wielkość liter musi się zgadzać | `QuoteIsFragmentRule.java:27-29`, `Whitespace.java:36-53` | jw. | hard (app) | `fabricated_evidence` |
| Brak treści: mniej niż 3 punkty kodowe po usunięciu URL/tagów/wzmianek → bez wywołania LLM, `relevance=unclear`, `insufficient_content` | `Prefilter.java:20,27-29,35-44`, `Assessor.java:72-75` | każda ocena; wynik **omija** walidator D1–D15 | hard (app) | `ok` z `result_source=prefilter` |
| Tekst niepusty, `null` zamiast pustego; schema_version stała | `TextFields.java:16-28`; `AssessmentInputV1.java:18-23` | konstrukcja | hard (app) | `IllegalArgumentException` |
| Zamknięte zbiory dostawców, kanałów, tierów | `mig/V3:162-175` + enumy `api/llm/` | zapis | hard (data) | CHECK |
| `flex` i `reasoning_effort` tylko dla OpenRouter; timeout 10 s–30 min | `api/llm/LlmProviderProperties.java:63-66,104-113`; `api/config/LlmConfigurationCheck.java:17` | start każdego kontekstu | hard (app) | `InvalidSettingsException`, start pada |
| OpenRouter tylko do wskazanego upstreamu, bez fallbacków | `api/llm/openrouter/OpenRouterClient.java:170` | jedyna ścieżka | hard (app) | n/d |

### 5. Pobieranie źródeł

**Kod wspólny:**

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| Żądane źródło musi być skonfigurowane; puste = wszystkie skonfigurowane | `SourceIngest.resolve` `:48-60` przed `queue` `PipelineRunService.java:183` | oba wejścia | hard (app) | `source_not_configured` 503 |
| Awaria lub milczenie źródła jest jego wynikiem | `SourceIngest.fetch:104-130` | RANGE | hard (app) | **logs and continues** + wiersz `failed` |
| Brak postów ze wszystkich źródeł = przebieg `failed` | `SourceIngest.java:136-138`, `NoPostsFetchedException.java:22-27` | RANGE | hard (app) | named error, także dla pustego dnia |
| Wynik źródła zapisuje się razem z licznikami, raz na `(run, source)` | `RunRecorder.source:142-158`; `uq_pipeline_run_sources_run_source` `mig/V12:48`; `ck_..._counts :46`, `ck_..._error :47` | jedyny pisarz | hard (data) | CHECK |
| Dzień pokryty dla źródła tylko przy `succeeded`/`partial` i wyniku źródła `succeeded` | `PipelineRunRepository.java:29,92-99,112-117,132-136`; `MissingDays.java:23-33` | trzy zapytania z tą samą regułą; okno 14 dni do D-2 `MissingDays.java:48,56` | hard (app) | n/d |
| Retries źródła: 3 próby, 2 s/4 s, `Retry-After` ≤30 s, nic po deadline | `api/sources/transport/HttpRetry.java:42-48,97-99,112-156,164-174` | oba klienty | hard (app) | `*ApiException` → wynik `failed` |
| Deadline pobierania 10 min na źródło | `WykopRepository.java:61,123`, `RedditRepository.java:60,115` | każde pobranie | hard (app); aktywność przebiegu rusza się po każdym źródle (`RunRecorder.java:145`), więc próg strażnika działa na jedno źródło (`inference`) | `DEADLINE_EXCEEDED` |

**Zachowanie per źródło (brak tu nie jest jeszcze błędem; to niesymetryczności):**

| aspekt | Wykop | Reddit (Arctic Shift) |
|---|---|---|
| Konfiguracja | `WYKOP_KEY` i `WYKOP_SECRET` razem; jeden bez drugiego zabija start `WykopConfiguration.java:33-37` | `REDDIT_SUBREDDITS`; nazwa 2–21 znaków, zła zabija start `RedditConfiguration.java:39,63-66,71-73` |
| Kanał | stała `gielda` `WykopPosts.java:24`, także przy odtwarzaniu `:35` | z payloadu `subreddit` `RedditPosts.java:50,52` |
| Typ | `entry`→post, `link`→link, inne odrzucone `WykopMapper.java:43-61` | `is_self==true`→post, cokolwiek innego (także brak pola)→link `RedditMapper.java:50` |
| Tekst | wpis tylko body, link title+description `WykopMapper.java:50-59` | title+selftext `RedditMapper.java:51-52` |
| Usunięte posty | brak filtra (szukane w `sources/wykop`: tylko komentarze o paginacji) | wycięte **przed** liczeniem: ani fetched, ani skipped `RedditRepository.java:187-190`, `RedditItem.java:42-44`; druga kopia reguły `RedditMapper.java:44-46`, węższa (bez `removed_by_category`) |
| Skipped | `null` tekst/id, zły kształt → `skipped` → powód uwagi admina `WykopFetcher.java:48-55` | jw. `RedditFetcher.java:48-55` |
| Paginacja | strony + `pagination.total`, drugi odczyt przy niespójności `WykopRepository.java:138-148`, limit 200×50 `:48,51` | po czasie, bez drugiego odczytu, porażka całości przy luce `RedditRepository.java:161-164,196-203`; limit 50×100 na subreddit `:47,50` |
| Odpowiedź bez `total` | koniec na krótkiej stronie, **bez kontroli** `WykopRepository.java:198-203` | n/d (zawsze krótka strona) `:193-195` |
| Czas | `created_at` lokalny warszawski `WykopMapper.java:30,74` | `created_utc` `RedditMapper.java:68` |
| Autor | `author.username` `WykopPosts.java:52` | `[deleted]`→`null` `RedditPosts.java:51` |
| Statusy retry | 429, 5xx | dodatkowo 422 `RedditApiClient.java:53,87` |
| Filtr zakresu po stronie kodu | brak: zaufanie do API (`inference`, nie znaleziono porównania z `from`/`to` po pobraniu w `sources/wykop`) | brak: `after`/`before` w zapytaniu `RedditRepository.java:142-143,151-152` |

### 6. Konta, logowanie, sesje

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| Brak samorejestracji: konto tylko z komendy operatora lub migracji | pisarze `users`: `cli/AdminBootstrap.java:81`, `auth/activation/ActivationLinks.java:88`, `mig/V6:11-12` (grep po `api/`) | wszystkie | hard (app) | n/d |
| E-mail unikalny, małymi literami | `uq_users_email`, `ck_users_email_lower` `mig/V1:10-11`; `user/Emails.java:18-20` | login + CLI | hard (data) | **silently corrects** w `Emails.normalize`; CLI `EMAIL_TAKEN` |
| Rola zamknięta | `ck_users_role` `mig/V1:12`; `user/Role.java:15` | zapis | hard (data) | CHECK |
| Konto systemowe nigdy nie ma hasła | `ck_users_login_password` `mig/V6:9`; `ActivationLinks.java:119-121,162-166`; `AccountDetailsService.java:45`; `ActivationTokenRepository.java:35-36` | cztery warstwy | hard (data) | `SYSTEM_ACCOUNT` / `LINK_INVALID` / jak złe hasło |
| Pierwszy admin raz | `AdminBootstrap.java:55-62` pod advisory lock | tylko `create-admin`; **bypass z założenia**: `create-user --role admin` bez locka i bez sprawdzenia `CreateUserCommand.java:46-52` | soft | `ExitCode.REFUSED` |
| Polityka hasła 12–128 punktów kodowych | `auth/Passwords.java:12,14,30-38`; `ActivationLinks.java:150`; `AdminBootstrap.java:74`; SPA `web/views/ActivateView.vue:52-55`, `web/api/activation.ts:8-9`, `rules.json`; `@Size(max=1024)` `ActivationController.java:83`, `LoginRequest.java:14` | wszystkie ustawienia hasła | hard (app) | `password_policy` 400, link działa dalej |
| Konto bez hasła nie zaloguje się, odpowiedź nieodróżnialna | `AccountDetailsService.java:45`, `ErrorCode.java:17-21` | login | hard (app) | `invalid_credentials` |
| Link jednorazowy, tylko SHA-256, ważny do `expires_at`, jeden na konto | `activation_tokens` PK/uq/CHECK `mig/V7:11-14`; `findValidForUpdate` `ActivationTokenRepository.java:26-29`; `upsert :45-49`; `ActivationLinks.java:157-168` | jedyny pisarz | hard (data) dla jednego na konto; ważność hard (app) | `activation_link_invalid` 400 |
| TTL linku 1 min–7 dni | `ActivationSettings.java:40-44,81-83`, `ActivationLinks.java:211-214` | CLI | hard (app) | `ExitCode.USAGE` |
| Reset wylogowuje wszędzie i kasuje hasło | `ActivationLinks.java:113-128`, `deleteSessions :195-199` | CLI | hard (app) | `NO_ACCOUNT` / `SYSTEM_ACCOUNT` |
| Aktywacja też kasuje sesje | `ActivationLinks.java:171` | jedyna | hard (app) | n/d |
| Limit logowania: 5 błędów/15 min na e-mail, 20 na IP | `auth/LoginRateLimiter.java:32-36,91-110`, jedyny użytkownik `AuthController.java:83` | **tylko** login; aktywacja bez limitu | soft: stan w pamięci procesu `:26-27`, restart zeruje; IP z `X-Real-IP` `ClientIp.java:15-21`, którego wiarygodność zależy od krawędzi (`inference`) | `too_many_attempts` 429 + `Retry-After` |
| Zapis cross-origin odrzucony | `auth/OriginGuardFilter.java:39-52`; `SameSite=Strict` `config/SessionConfig.java:41` | `/api/**`; bez nagłówka `Origin` przechodzi `:47`; łańcuch `/internal` bez filtra | soft: bypass poza przeglądarką (curl) z założenia, ale potrzebna sesja | `cross_origin` 403 |

### 7. Dostęp i widoczność

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| Deny-by-default; publiczne tylko health, ready, deploy-safety, login, logout, aktywacja | `config/SecurityConfig.java:99-107` | cały `/api` | hard (app) | 401 `not_authenticated` |
| `/api/admin/**` tylko admin | `SecurityConfig.java:106`; SPA `web/router/index.ts:68-70`, `web/components/AppHeader.vue:13,36,39` | HTTP + UI | hard (app) | 403 `forbidden`; SPA przekierowanie |
| `/internal/**` tylko token, nigdy sesja | `SecurityConfig.java:75-76,81`; `internal/PipelineTriggerTokenFilter.java:47-54`; `PipelineTriggerToken.java:44-46,60-65` (≥32 znaki, stały czas, brak tokena = fail closed) | cron | hard (app) | 401 |
| Feed i szczegół tylko `assessed` | `PostRepository.java:98-105,112-118`; `PostController.java:124-128` (`list:91`, `detail:102`, `raw:113`) | wszystkie trzy trasy | hard (app) | `post_not_found` 404, nie odróżnia nieznanego id od innego etapu |
| Lista nieudanych tylko dla admina | `posts/api/FailedPostController.java:48`, `PostRepository.java:80-88` | jedyna | hard (app) | n/d |
| Filtr feedu działa na **jednym** celu najnowszej oceny `ok` | `PostRepository.java:112-118` | jedyna; prefiltr nigdy nie pasuje `:96` | hard (app) | nieznana wartość → 400; SPA **silently corrects** `web/feedFilter.ts:49-56` |
| Feed nie wycina `not_investment_related` ani wyniku prefiltra | szukane: `relevance` w `api/posts/`, `api/pipeline/`, `web/views/FeedView.vue`: brak filtra; tylko etykieta `web/components/PostCard.vue:27` | wszystkie | to brak strażnika, nie strażnik | dotyczy Unknowns |
| Powiadomienia widzi i oznacza tylko adresat | `NotificationRepository.java:33,64-66,75`; `NotificationController.java:55,74-75` | wszystkie | hard (app) | `not_found` 404 dla cudzego |
| Świeżość mówi tylko „kiedy" | `FreshnessController.java:34-41` | każda sesja | hard (app) | n/d |
| Deploy-safety ujawnia jeden bit i powód | `DeploySafetyController.java:50-70` | publiczne | hard (app) | 503 |

### 8. Powiadomienia i uwaga admina

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| Jedna reguła „wymaga uwagi", liczona przez API | `PipelineRun.java:316-347`; SPA tylko tekst `web/runs.ts:89-118` | notyfikacje, lista, strona, dzwonek | hard (app) | n/d |
| Powiadomienie raz na adresata i przebieg, tylko admin z `login_enabled` | `uq_notifications_recipient_kind_run` `mig/V9:19`; `NotificationRepository.java:51-53` | `runEnded` z pięciu miejsc: `PipelineRunService.java:270,386,480,551`, `PipelineStartupRecovery.java:113` | hard (data) dla `run_attention` (`ck_notifications_run :18`); dla wierszy bez `run_id` NULL byłby różny (`mig/V9:19`, `inference`) | `DO NOTHING` |
| Przebieg crona `failed`/`interrupted` nie wysyła alertu operatora | `RunNotifications.java:112,119-121`, `PipelineRun.java:295-297` | jedyna | hard (app) | cron zgłasza kodem wyjścia `trigger.sh:320-322,344-346` |
| Cron: `partial` = sukces | `trigger.sh:206-215`; stan zduplikowany `RunStatus.java:13`, `mig/V3:31`, `rules.json:26`, `web/api/pipeline.ts:7` | cron | hard (app) | exit 0 |

### 9. Kopia zapasowa

| reguła | strzeżona w | ścieżki | ścisłość | obsługa błędu |
|---|---|---|---|---|
| Tylko produkcja, chyba że `BACKUP_IN_ANY_ENVIRONMENT=true` | `backup.sh:35-37` (cron analogicznie `trigger.sh:24-27`) | Railway; poza Railway zmienna pusta i job działa | soft z założenia | exit 0, „skipped" |
| Pusta tabela `posts` nigdy nie idzie do kopii | `backup.sh:237-238` | każdy przebieg; zgoda tego nie znosi | hard (app) | `fail` |
| Żadna liczona tabela nie maleje względem ostatniego manifestu | `backup.sh:272-284`; klasyfikacja `tools/db-backup/tables.sh:17-30` | bypass jawny: `BACKUP_ACCEPT_SHRINK` musi nazwać manifest `:276-285` | soft z jawną zgodą | `fail`, nic nie zrzucone |
| Dane sesji nie trafiają do zrzutu | `backup.sh:26,293,300-302`; `restore-check.sh:116-117` | zrzut i weryfikacja | hard (app) | `fail` |
| Kopia wysyłana dopiero po pełnym teście odtworzenia | `backup.sh:329-330,336-337`; `restore-check.sh:91,105,114,130` (brak/nieznana tabela, zakres liczników) | każdy przebieg | hard (app) | `fail`, nic nie wysłane |
| Tabela nowa musi być sklasyfikowana | `restore-check.sh:114`, `tables.sh:7-8` | przy backupie | hard (app) | `fail` |
| Retencja 14+12, bieżący zrzut chroniony | `backup.sh:118-119,189-205` | `prune` | hard (app) | `fail` |

## Soft and client-only rules

| reguła | gdzie sprawdzana | bypass | typ |
|---|---|---|---|
| „Co najmniej jedno źródło" w formularzu | `web/views/AdminView.vue:322` | API: puste `sources` znaczy **wszystkie skonfigurowane** `SourceIngest.java:49-50`, `PipelineRunController.java:106`; bezpośrednie wywołanie nie jest odrzucane, ma inne znaczenie | client-only, semantyka rozjazdowa |
| Nieskonfigurowane źródło nie do zaznaczenia | `AdminView.vue:313` | API i tak odmawia `SourceIngest.java:55-58` | client + server |
| Martwego postu nie można zaznaczyć | `AdminView.vue:426` | API i tak odmawia `PipelineRunService.java:198-201` | client + server |
| Limit 7 dni | formularz nie ogranicza (`AdminView.vue:298-299`); tylko komunikat `web/api/pipeline.ts:180` i dzielenie `web/missingDays.ts:24-30` | serwer odmawia | server-only (UI pokazuje po fakcie) |
| Limit 100 postów w POSTS | tylko serwer; SPA czyta jedną stronę listy nieudanych `AdminView.vue:250` | n/d | server-only |
| Dostęp do widoków admina | `web/router/index.ts:68` | API 403 | client + server |
| Polityka hasła | `ActivateView.vue:52-55` | API 400 | client + server |
| Limit `q` | `FeedFilter.vue:57`, `feedFilter.ts:36` | API 400 `PostController.java:88` | client + server |
| Limit logowania | `LoginRateLimiter` | restart procesu zeruje; druga replika nie dzieli stanu `LoginRateLimiter.java:26-27` | soft |
| Limit logowania nie obejmuje aktywacji | `ActivationController` bez limitera | token 256 bitów `ActivationLinks.java:46`, więc nieistotne (`inference`) | soft |
| Sprzątanie osieroconych przebiegów | tylko przy żądaniu startu | zob. Scheduled | soft |
| Deploy safety | tylko odpowiedź | `onClose` przerwie przebieg niezależnie | soft |
| Wstrzymanie postu | filtr RETRY | POSTS bierze | soft z założenia |
| Pierwszy admin | `AdminBootstrap` | `create-user --role admin` | soft z założenia |

Jedyną regułą stanu po stronie klienta z założenia jest `currentUser` w pamięci SPA `web/auth/session.ts:10`: sesja i tak jest po stronie serwera.

## Writers that work against a guard

- `mig/V8:31-36` zamyka kroki wstecz (`interrupted`) i `mig/V12:51-88` wylicza wyniki źródeł z historii: migracje piszą poza `RunRecorder`, choć `PipelineRunSource` deklaruje pisarza jedynego (`.../PipelineRunSource.java:20-22`). Dotyczy tylko historii sprzed V12.
- `RunRecorder.interruptOrphans` oznacza `lost` przebieg innej instancji, choć ten mógł się poruszyć na progu: kod przyznaje to wprost `RunRecorder.java:315-321` i dopuszcza.
- Nie znaleziono żadnego pisarza wstecz z `dead`, `assessed` ani z końcowych statusów przebiegu (grep `UPDATE posts`, `UPDATE pipeline_runs` po `api/`). Nie znaleziono ścieżki usuwania `users`, `posts`, `assessments`, `pipeline_runs`, `notifications` (`DELETE FROM` tylko sesje, `tokens.delete` tylko tokeny): zgodne z komentarzem `tools/db-backup/tables.sh:13-14`.

## Failure handling

**Swallowed silently (znaleziska):**

- `PostStore.java:76-78`: `catch (DataIntegrityViolationException duplicate)` na zapisie pojedynczego postu, z komentarzem o unikacie. Łapie **każde** naruszenie integralności, także CHECK/NOT NULL, bez logu i licznika. Post znika: `fetched` go liczy, `skipped` nie, `new_posts` nie, przebieg może skończyć `succeeded`. Nie ustalono, czy dziś istnieje dane wejściowe, które to wywoła (`channel`, `published_at`, tekst są chronione wcześniej).
- `AssessmentSelection.java:60-65`: `POSTS` z nieznanym id albo postem `assessed`/`assessing` jest przyjmowany (202) i po cichu zawężany; `requested_post_ids` bez FK `mig/V3:13`. Przebieg może być `succeeded` bez żadnej pracy (`dispatch` z pustą listą `ConcurrentAssessmentDispatcher.java:173-175`).

**Silently corrects:**

- `Nul.removed`/`replaced`/`replacedInJson` `api/text/Nul.java:21-37`.
- `Emails.normalize` `user/Emails.java:18-20`.
- `Boolean.TRUE.equals(item.isSelf())` `RedditMapper.java:50`: brak pola = `link`.
- Normalizacja `REDDIT_SUBREDDITS`: `r/` i powtórki `RedditConfiguration.java:62,67`.
- `PostStore.java:63` deduplikuje w paczce.
- `web/feedFilter.ts:49-56` wyrzuca nieznane parametry.
- `web/auth/session.ts:25-27`: awaria sieci = traktowany jak wylogowany.

**Logs and continues:**

- `cleanUpOrphans` `PipelineRunService.java:250-256`, `PipelineStartupRecovery.java:101-108`, `close :508-523`, `onClose :553-561`, `RunNotifications.java:90-93`, `RunWatchdog.java:98-110`: każde z alertem operatora. Alert nie jest gwarantowany: kolejka odrzuca przy 20 oczekujących i ogranicza do 2/min `api/alerts/OperatorAlertQueue.java:56-64,196-200`.
- `count` `ConcurrentAssessmentDispatcher.java:401-405` + `RUN_COUNTERS_LOST :502-514`. Skutek: `attentionReasons` liczy się z liczników, a status z pamięci kroku, więc `dead > 0` może nie dać powodu uwagi (`inference`, potwierdza tekst logu `:507-509`).
- Reddit: post usunięty liczony tylko w logu INFO `RedditRepository.java:126-127`.
- Check-in healthchecks nie zmienia kodu wyjścia `trigger.sh:79-82`.

**Named errors (zamknięty zbiór):** `api/config/ErrorCode.java:21-72`, `api/alerts/AlertEvent.java:10-37`, `Violation`/`FailureClass`, `WykopFailure`, `RedditFailure`, `IssueOutcome.Reason`, `ExitCode`.

**Generic error:** `RejectedExecutionException` z `submit` po `shutdownNow` nie ma mapowania w `PipelineRunExceptionHandler.java:17-33`, więc 500 `internal_error`, po zatwierdzeniu transakcji (`inference` z kodu: `queue` nie czyta `stopping`, `PipelineRunService.java:216-226,282-287`).

**Klasyfikacja LLM (trzy winy):**

| sytuacja | klasyfikacja | zapis | zużywa próbę |
|---|---|---|---|
| 401, 402, 404 | `CONFIGURATION` `TransportException.java:87-88` → stop kroku | nic | nie |
| 429, 408, 5xx, timeout, połączenie po retry | `LlmUnavailableException` `TransportRetry.java:118-121` → 3 z rzędu stop `ConcurrentAssessmentDispatcher.java:112,324-332` | nic; pojedynczy = błąd techniczny | nie |
| inne 4xx (w tym **403**), brak tekstu `ChatResponses.java:34-37` | `REJECTED` → `LlmCallException` `TransportRetry.java:103-105` → `provider_error` | `failed` | **tak** |
| błąd modelu względem kontraktu | `Assessor.java:96-105` | `failed` | tak |
| nieklasyfikowany błąd klienta | `LlmClientException`, 3 z rzędu stop `:118,345-355` | nic, błąd techniczny | nie |

Znalezisko: systematyczna odmowa 403 lub filtr treści dostawcy zużywa próby i prowadzi do `dead`, a `dead` jest nieodwracalne (brak ścieżki ożywienia); nie ma wyłącznika dla serii `REJECTED`, a zapisany wynik zeruje liczniki serii `:298-303` (`inference` co do częstości, `evidence` co do mechaniki).

## Atomicity

| operacja | atomowa? | cytat |
|---|---|---|
| Utworzenie konta + token | tak, 1 TX | `ActivationLinks.java:82-90` |
| Reset: token, hasło, sesje | tak, 1 TX | `:113-128` |
| Aktywacja: hasło, token, sesje | tak, 1 TX + `FOR UPDATE` | `:156-173` |
| Pierwszy admin | tak, 1 TX + advisory lock; **prompt hasła wewnątrz transakcji** | `AdminBootstrap.java:54-83`; limit 300 s `application.properties:13-20` |
| Kolejkowanie retry + range | tak, 1 TX; `submit` **po** commicie, więc padnięcie między nimi zostawia `queued` | `PipelineRunService.java:217-224` |
| Sprzątanie osieroconych | tak, własna TX, zostaje także przy odmowie | `:244-248` |
| Zapis oceny + przejście postu | tak, 1 TX | `PostTransitions.java:93-109` |
| Zapis oceny + licznik przebiegu | **nie**: strata możliwa, sygnał `RUN_COUNTERS_LOST` | `ConcurrentAssessmentDispatcher.java:209,386-406` |
| Wynik źródła + liczniki | tak, jeden statement (CTE) | `RunRecorder.java:143-147` |
| Zapis paczki postów + payloady | tak dla paczki 500, **nie** dla kroku; zapisane posty zostają `stored` i bierze je retry | `PostStore.java:65-81` |
| Zamknięcie kroku + aktywność | **nie**: dwa statementy z założenia | `RunRecorder.java:361-363` |
| Koniec przebiegu w `close`: `fail` + `releaseAll` | **nie**: dwa osobne zapisy, awaria między nimi zostawia posty `assessing` do `releaseOrphans` | `PipelineRunService.java:497-498` |
| Koniec przez strażnika / shutdown / recovery | tak, 1 TX | `:463-469`, `:544-548`, `PipelineStartupRecovery.java:92-99` |
| Koniec przebiegu + powiadomienia | **nie**: po commicie; porażka to brak powiadomienia na zawsze (alert jednorazowy), brak ponowienia | `RunNotifications.java:68-100` |
| Powiadomienia + alert operatora | **nie** | `:108-114` |
| Upload: zrzut przed manifestem; kopia miesięczna | **nie**, ale kolejność celowa; pół pary miesięcznej zastępowane | `backup.sh:335-349` |

## Scheduled and asynchronous work

| praca | wyzwalacz | handler | dociera? |
|---|---|---|---|
| Dzienny przebieg | Railway cron `30 0 * * *` `.railway/railway.ts:286` | `trigger.sh:232` → `POST /internal/pipeline-runs/daily` → `InternalPipelineRunController.java:65-71` → `startRange` | tak; poza produkcją pominięty `trigger.sh:24-28` |
| Kopia zapasowa | Railway cron `0 2 * * *` `.railway/railway.ts:239` | `backup.sh` | tak; tylko produkcja |
| Strażnik przebiegu | `scheduleWithFixedDelay` `RunWatchdog.java:83` (1/5 progu, max 1 min, min 100 ms `:71-77`) | `stopStalled` | tak; `@ConditionalOnWebApplication` `:44`, bez własnych przebiegów nie pyta bazy `:94` |
| Recovery przy starcie | `SmartInitializingSingleton` `PipelineStartupRecovery.java:84-86` | `recover` | tak; tylko serwer `:53` |
| Zamknięcie instancji | `@EventListener(ContextClosedEvent)` `PipelineRunService.java:539` | `onClose` | tak |
| Migracje | Railway pre-deploy `.railway/railway.ts:118`; serwer ma Flyway wyłączony `application.properties:23` | `cli/MigrateCommand.java:46-58` | tak; porażka zatrzymuje deploy przez kod wyjścia `cli/ExitCode.java:4-6` |
| Sprzątanie osieroconych | **brak timera**: tylko żądanie startu `PipelineRunService.java:181,196` | `cleanUpOrphans` | zależne od żądania; w produkcji cron daje raz na dobę (`inference`) |
| Ponowienie resztek | tylko przy `startRange`, gdy są resztki `:185` | przebieg RETRY | tak; `startPosts` nie kolejkuje retry |
| Wygasanie linków aktywacyjnych | brak zadania; sprawdzenie przy odczycie `ActivationTokenRepository.java:28,36` | n/d | reguła działa bez zadania; **przeterminowane wiersze zostają** (nie znaleziono sprzątania w `api/`) |
| Wygasanie sesji | biblioteka Spring Session JDBC, `spring.session.timeout=30d` `application.properties:42` | n/d | `unknown`: w `api/` brak `@Scheduled` i brak ustawienia cleanup; zależne od biblioteki |
| Alert operatora | własny wątek `OperatorAlertQueue`, limity `:56-67` | `HealthchecksAlerts` | tak; tylko serwer; w CLI nic się nie dzieje `RunNotifications.java:27-28` |
| Dzwonek i listy w SPA | `useEvery` `web/components/NotificationBell.vue:27`, `AdminView.vue:129`, `RunDetailView.vue:121` | odczyt | tak; tylko karta widoczna |
| Retencja NAS | `tools/db-backup/nas-retention.sh` | ta sama reguła 14+12 | `unknown`: wyzwalacz poza repozytorium (nagłówek pliku mówi o harmonogramie DSM) |

**Handler bez wyzwalacza / zapis bez czytelnika:**

- `posts.content_hash`: pisany `Post.java:150`, CHECK `mig/V3:113`, komentarz V3 „to notice an edited post" (`mig/V3:97`), a getter `Post.java:212` nie ma czytelnika w `api/` (grep). Edytowany post nie jest wykrywany: `PostStore` zostawia istniejące bez zmian (`PostStore.java:29-30`).
- `failure_class = timeout`: jedyny konstruktor `new LlmCallException` daje `PROVIDER_ERROR` `TransportRetry.java:104`, a `TIMEOUT` idzie tylko w `LlmUnavailableException` `:118-121`, którego `Assessor.call` nie zapisuje `Assessor.java:131-133`. Wartość z CHECK `mig/V3:162` jest nieosiągalna z produkcyjnej ścieżki (`evidence`, grep po `api/`).
- `ItemType.COMMENT`, `Context.parentPost/parentComment`: dopuszczone w kontrakcie i CHECK, żaden mapper ich nie tworzy (`WykopMapper.java:65`, `RedditMapper.java:56-57` zawsze `Context.NONE`). Skutek (`inference`): cel z `reference=contextual` nie ma jak spełnić D5 i D10 jednocześnie, więc zawsze jest porażką.

## Duplicated rules

| reguła | miejsca |
|---|---|
| Polityka hasła | `Passwords.java:12,14`; `web/api/activation.ts:8-9`; `ActivateView.vue:53-55`; `rules.json` |
| Maks. prób | `Post.java:40`; `mig/V3:116-121`; `web/api/posts.ts:8`; `rules.json` |
| Maks. dni zakresu | `PipelineRunController.java:60`; `web/missingDays.ts:5`; `rules.json` |
| `q` ≤100 | `PostFilter.java:24`; `web/feedFilter.ts:7`; `rules.json` |
| Rozmiar strony | `PipelineRunController.java:63`; literał `@Max(100)` `PostController.java:87`, `FailedPostController.java:50`; `web/api/pipeline.ts:133` |
| Zbiór białych znaków | `Whitespace.java:17-29`; `web/feedFilter.ts:24`; `rules.json:2-5`; regex `Whitespace.java:72-75` |
| Statusy przebiegu | `RunStatus.java:13`; `mig/V3:31`; `trigger.sh:206-215`; `web/api/pipeline.ts:7`; `rules.json:26` |
| Żywotność kolejki (SQL) | `RunRecorder.java:328-333`; `PipelineRunRepository.java:58-63` |
| Pokrycie dnia | `PipelineRunRepository.java:29,92-99,112-117,132-136`; `MissingDays.java:23-33` |
| Reguła adresu check-in | `AlertSettings.java:49`; `trigger.sh:60`; `backup.sh:56` |
| Post usunięty | `RedditItem.java:42-44`; `RedditMapper.java:44-46` (węższa) |
| Strefa Warszawy | `PipelineRunService.java:97`; `WykopMapper.java:30`; `web/labels.ts:91,99`; `AdminView.vue:28` |
| Strip tokenu | `PipelineTriggerToken.java:40,64`; `trigger.sh:139-141` |
| Tekst „gave no post to store" | `RunFlow.java:106`; `mig/V12:57` (LIKE) |

## Unknowns

- Czy to, że feed pokazuje `not_investment_related` i wynik prefiltra, jest zamierzone: kod tego nie rozstrzyga.
- Czy kiedykolwiek zachodzi cichy spadek postu w `PostStore.java:76-78`: nie ustalono danych, które by go wywołały.
- Czy Spring Session JDBC faktycznie czyści wygasłe sesje przy usypianiu API: zachowanie biblioteki, nieprzeczytane.
- Czy po stronie Railway istnieje tylko jedna replika (limiter w pamięci): `LoginRateLimiter.java:26-27` tak twierdzi, `.railway/railway.ts` nie sprawdzono pod tym kątem.
- Treść promptu (`agents/assessor/prompt/`) nieprzeczytana: nie wiadomo, czy model jest informowany o niedostępności `contextual`.
- `ClientIp`: czy `X-Real-IP` faktycznie jest nadpisywany przez krawędź: tylko twierdzenie w komentarzu `ClientIp.java:6-8`.

## Commands run

- `git status --short`, `ls`, `ls -R`, `find`, `wc -l`, `cat -n`, `sed -n`
- `grep -rn` / `grep -rnE` po `api/`, `web/`, `tools/`, `.railway/railway.ts` (zapisy SQL, `.save(`, `@Scheduled`, `@Transactional`, `content_hash`, `relevance`, `CallFailure.TIMEOUT`, `Europe/Warsaw`, `LoginRateLimiter`)
- `python3` (odczyt limitów ze schematu JSON i kluczy `rules.json`)
