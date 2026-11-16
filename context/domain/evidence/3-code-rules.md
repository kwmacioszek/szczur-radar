# Evidence: rules guarded in the code

Zakres: cały `code_scope` z `0-sources.md` (jeden agent, bez podziału): `szczur-radar-api/` (main + wybrane testy-strażnicy), `szczur-radar-web/src/` i `dev/` (poza `KitchenSinkView.vue`), `tools/pipeline-trigger/`, `tools/db-backup/`. Harmonogramy przeczytane tylko jako konfiguracja w `.railway/railway.ts`. HEAD `66d4199` (kod jak `ef56ddc`). Aliasy jak w inwentaryzacji: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `web/` = `szczur-radar-web/src/`, `mig/` = `szczur-radar-api/src/main/resources/db/migration/`; `test/` = `szczur-radar-api/src/test/java/pl/szczurradar/api/`.

Warstwy przeszukane i sposób:
- **trwałość**: wszystkie 16 migracji przeczytane w całości (CHECK, UNIQUE, NOT NULL, FK, indeksy częściowe, backfille); każdy zapis w kodzie main znaleziony grepem `UPDATE|INSERT INTO|DELETE FROM|.save(|.saveAll(|.saveAndFlush(|.delete(|@Modifying` (pełna lista pisarzy niżej);
- **wejścia HTTP**: wszystkie `@*Mapping` (grep) + `config/SecurityConfig.java`; przeczytane kontrolery: pipeline-runs (admin i internal), deploy-safety, posts, dead/failed posts, notifications, auth, activation; pozostałe (freshness, llm check, assessment-quality, health) tylko nagłówki;
- **CLI**: `cli/AdminBootstrap.java` w całości, pozostałe komendy grepem warunków / kodów wyjścia;
- **logika**: przeczytane `PipelineRunService`, `RunRecorder`, `PostTransitions`, `RunSequence`, `SourceIngest`, `PostStore`, `AssessmentSelection`, `RunWatchdog`, `PipelineStartupRecovery`, `RunNotifications`, `ActivationLinks`, `AccountDetailsService`, `Assessor`, nagłówek `ConcurrentAssessmentDispatcher`; grepem `throw new|if (|static final` w `sources/**`, `alerts/**`, `posts/**`, `agents/assessor/validation/**`;
- **UI**: router, `AppHeader`, `AdminView` (formularz, lista nieudanych), `FeedFilter`, `ActivateView`, `missingDays.ts`, `api/client.ts`, `auth/session.ts`, `contract/rules.json` — grep `confirm(|:disabled|maxlength|role ===|MAX_`;
- **zadania**: `trigger.sh`, `backup.sh`, `restore-check.sh` grepem warunków i `exit`;
- **testy-strażnicy**: lista wszystkich `*IT.java`, nagłówki pięciu strażników strukturalnych.

Nie przeszukane (niewiadome, nie „brak reguł”): ciała 15 strategii reguł D1–D15 w `api/agents/assessor/validation/rules/` (cytuję tylko enum `RuleId`), `OutputParser`, `StanceTable`, `NightSummary.java` (620 wierszy), `AssessmentQuality.java` poza stałymi i `status`, klienci LLM (`api/llm/**`), `HttpRetry` / `TransportRetry` poza stałymi, `WykopRepository` / `RedditRepository` poza grepem, `tools/db-backup/nas-retention.sh`, `tables.sh` poza grepem, `szczur-radar-web/dev/mock-api.js`, widoki `FeedView`, `PostDetailView`, `RunDetailView`, `LoginView` poza grepem, ciała ok. 100 plików `*Test.java`.

## Guarded rules

Kolumna „reguła” to moje sformułowanie (inference); cytaty źródła podaję w `„…”`.

### Konta, logowanie, dostęp

| reguła (inference) | strzeżona w (warstwa: path:line) | pokryte ścieżki | ścisłość | obsługa porażki |
|---|---|---|---|---|
| E-mail konta jest unikalny i zapisany małymi literami | DB: `mig/V1__create_users.sql:10` `uq_users_email`, `:11` `ck_users_email_lower`; app: `api/user/Emails.java:18-19` (`strip().toLowerCase`), `api/auth/activation/ActivationLinks.java:83-86`, `:92-98` (wyścig rozpoznany po `DataIntegrityViolationException`), `api/cli/AdminBootstrap.java:63-66` | wszyscy trzej pisarze `users` (`ActivationLinks.java:88`, `AdminBootstrap.java:81`, seed `mig/V6…:11-12`) | hard (data) | named error: `IssueOutcome.Reason.EMAIL_TAKEN`; CLI „An account with e-mail … already exists.” |
| Rola konta to `user` albo `admin` | DB: `mig/V1__create_users.sql:12` `ck_users_role` | wszyscy pisarze | hard (data) | generic (constraint) |
| Konto systemowe nigdy się nie loguje i nigdy nie ma hasła | DB: `mig/V6__users_login_enabled_scheduler.sql:9` `ck_users_login_password CHECK (login_enabled OR password_hash IS NULL)`; app: `api/auth/AccountDetailsService.java:45` (niezdatny hash zamiast hasła), `ActivationLinks.java:119-121` (reset odmawia), `:162-166` (aktywacja = „invalid link”), `ActivationTokenRepository.java:35-36` (`u.loginEnabled = true` w lookup) | logowanie, reset, aktywacja, lookup | hard (data) + hard (app) | named: `SYSTEM_ACCOUNT`, `LINK_INVALID`; logowanie = zwykłe `invalid_credentials` (celowo nierozróżnialne, `AccountDetailsService.java:21-26`) |
| Konto bez hasła (oczekujące aktywacji) nie loguje się | app: `AccountDetailsService.java:45` | logowanie | hard (app) | `invalid_credentials` (`AuthController.java:104-107`) |
| Pierwszego admina tworzy się tylko wtedy, gdy żaden admin nie istnieje | app: `api/cli/AdminBootstrap.java:55` (`pg_advisory_xact_lock`), `:56-62` (`existsByRole(Role.ADMIN)`) | tylko `create-admin`; `create-user --role admin` celowo omija (komunikat `:57-60`) | hard (app) dla bootstrapu | named: `ExitCode.REFUSED` + „Admin already exists — …” |
| Hasło ma 12–128 znaków | app: `api/auth/Passwords.java:12`, `:14`, `:32-37`; wywołania `ActivationLinks.java:150-154`, `AdminBootstrap.java:73-79`; UI: `web/views/ActivateView.vue:52-57` (liczy punkty kodowe); rejestr `web/contract/rules.json` (`passwordMinLength`, `passwordMaxLength`) | aktywacja (API), create-admin (CLI); UI powtarza | hard (app) | named: `password_policy` (link nadal ważny, `ActivationController.java:50-51`) |
| Hasło powtórzone dwa razy musi się zgadzać | CLI: `AdminBootstrap.java:69-72`; UI: `web/views/ActivateView.vue:56` (`mismatch`) | CLI i formularz aktywacji; API aktywacji przyjmuje jedno pole (`ActivationController.java:82-83`) | client-only na ścieżce aktywacji (stan formularza — inference: właściwe miejsce) | named (UI / CLI) |
| Limit prób logowania: 5 porażek na e-mail i 20 na IP w 15 min | app: `api/auth/LoginRateLimiter.java:32-36`, `:91-110`; użycie `api/auth/AuthController.java:83-88`, `:114-116` | jedyna ścieżka logowania | hard (app), ale stan w pamięci procesu („correct while the API runs a single replica”, `LoginRateLimiter.java:26-27`) | named: 429 `too_many_attempts` + `Retry-After` |
| Awaria magazynu kont nie jest werdyktem o poświadczeniach | app: `AuthController.java:97-103` | logowanie | hard (app) | 500 `internal_error`, próba nie liczy się do limitu (`:71-75`) |
| Każda trasa wymaga sesji, poza jawną listą publicznych; `/api/admin/**` wymaga roli admin | app: `api/config/SecurityConfig.java:100-108`; testy: `test/auth/AuthorizationCompletenessIT.java:31-39`, `test/auth/AdminRoleIT.java:31-36` (wszystkie zmapowane trasy, bez listy) | wszystkie trasy `/api/**` | hard (app) + strażnik testowy | 401 / 403 JSON (`JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`) |
| `/internal/**` przyjmuje tylko token crona, nigdy sesję; token ma ≥ 32 znaki | app: `SecurityConfig.java:73-91`; `api/internal/PipelineTriggerToken.java:24`, `:44-46` (krótki token psuje start), `:61-64` (porównanie stałoczasowe SHA-256); `api/internal/PipelineTriggerTokenFilter.java:48-52` (principal = `SystemAccount.SCHEDULER`) | `/daily`, `/{id}`, `/nightly-summary` | hard (app) | 401; brak tokenu na serwerze = każde żądanie 401 (`SecurityConfig.java:47-48`) |
| Zapisy cross-origin na `/api/**` są odrzucane | app: `api/auth/OriginGuardFilter.java:40`, `:46-50`; cookie `SameSite=Strict` `api/config/SessionConfig.java:41` | POST/PUT/PATCH/DELETE pod `/api/**` | soft: żądanie **bez** nagłówka `Origin` przechodzi (`OriginGuardFilter.java:47`) | named: 403 `cross_origin` |
| Sesja wygasa po 30 dniach bezczynności | config: `szczur-radar-api/src/main/resources/application.properties:47`; `SessionConfig.java:42`; odnowienie cookie `api/auth/SessionCookieRenewalFilter.java:20-31` | każda uwierzytelniona odpowiedź 2xx | hard (app/framework) | — |
| Logowanie zmienia id istniejącej sesji (fixation) | app: `AuthController.java:145-149` | logowanie | hard (app) | — |

### Linki aktywacyjne

| reguła (inference) | strzeżona w | pokryte ścieżki | ścisłość | obsługa porażki |
|---|---|---|---|---|
| Konto ma najwyżej jeden link; nowy unieważnia poprzedni | DB: `mig/V7__create_activation_tokens.sql:11` (PK `user_id`), `:13` (`uq_activation_tokens_token_hash`); app: `api/auth/activation/ActivationTokenRepository.java:45-48` (`ON CONFLICT (user_id) DO UPDATE`) | create-user, reset-password | hard (data) | — |
| W bazie tylko hex SHA-256 tokenu | DB: `mig/V7…:14` `ck_activation_tokens_token_hash`; app: `ActivationLinks.java:187`, `:155` | wszyscy pisarze | hard (data) na kształt, hard (app) na hash | — |
| Link jest jednorazowy i wygasa | app: `ActivationTokenRepository.java:28` (`t.expiresAt > :now`), `ActivationLinks.java:157` (`findValidForUpdate`, blokada wiersza), `:168` (usunięcie po użyciu) | lookup, aktywacja | hard (app) | named: 400 `activation_link_invalid` („unknown, used, expired or replaced link (indistinguishable)”, `ActivationController.java:40`) |
| Ważność linku dodatnia; ustawienie `ACTIVATION_LINK_TTL` 1 min–7 dni, domyślnie 12 h | app: `ActivationLinks.java:211-215`; `api/auth/activation/ActivationSettings.java:40-44` | create-user, reset-password | hard (app) | `IllegalArgumentException` / błąd ustawień |
| Reset hasła i aktywacja kończą wszystkie sesje konta | app: `ActivationLinks.java:125-126`, `:171`, `:195-199` (`DELETE FROM spring_session WHERE principal_name = :principal`) | reset (CLI), aktywacja (API) | hard (app) | — |

### Przebiegi potoku (runs)

| reguła (inference) | strzeżona w | pokryte ścieżki | ścisłość | obsługa porażki |
|---|---|---|---|---|
| Rodzaj przebiegu: `range` / `retry` / `posts`; status z zamkniętego zbioru | DB: `mig/V3__create_pipeline_and_posts.sql:29`, `:30-31` | wszyscy pisarze | hard (data) | generic |
| Przebieg `range` ma oba końce zakresu i `from <= to`; inne nie mają żadnego | DB: `mig/V4__posts_shared_item_type.sql:19-22` `ck_pipeline_runs_range`; app: `api/pipeline/run/PipelineRun.java:155-157` | `PipelineRunService.java:182-184` | hard (data) | `IllegalArgumentException` / constraint |
| Przebieg `range` nazywa ≥ 1 zarejestrowane źródło; inne nie nazywają | DB: `mig/V12__pipeline_run_sources.sql:29-31`; app: `PipelineRun.java:158-160`, `api/pipeline/step/SourceIngest.java:48-60` | admin i cron (`startRange`) | hard (data) + hard (app) | named: 503 `source_not_configured` (`PipelineRunExceptionHandler.java:30-32`) |
| Przebieg `posts` ma listę postów (≥ 1, ≤ 100, bez `null`); inne jej nie mają | DB: `mig/V3…:35`; app: `PipelineRun.java:179-181`; `api/pipeline/api/PipelineRunController.java:184-186`; `api/config/PostIdLimits.java:11` | admin | hard (data) na obecność, hard (app) na liczbę | 400 `invalid_request` |
| Zakres żądania admina: oba dni, `from <= to <= dziś` (Warszawa), najwyżej 7 dni | app: `PipelineRunController.java:62`, `:99-101`, `:188-189`; UI: brak walidacji zakresu w formularzu (`web/views/AdminView.vue:419-420` tylko `required`); `web/missingDays.ts:5`, `:28` używa limitu tylko do grupowania dni bez danych | admin; cron nie przechodzi przez `valid` — stałe D-2…D-1 (`api/pipeline/api/InternalPipelineRunController.java:85-87`) | hard (app) — obaj znalezieni pisarze spełniają | 400 `invalid_request` |
| Najwyżej jeden przebieg naraz jest `running` | DB: `mig/V3…:39` `uq_pipeline_runs_one_running` (komentarz `:38` „second line of defence”); app: jednowątkowy executor `api/pipeline/PipelineRunService.java:136-137` | wszyscy | hard (data) | constraint → przebieg kończy się `failed` przez `execute` |
| Nowe przebiegi powstają tylko, gdy żaden nie jest `queued`/`running` | app: `PipelineRunService.java:216-226` (`lock()` `:274-276` = `pg_advisory_xact_lock`, `existsActive()` `:219-221`) | admin i cron (oba przez `startRange` / `startPosts`) | hard (app); baza przyjęłaby wiele `queued` | named: 409 `pipeline_busy`; cron ponawia 409 do `TRIGGER_BUSY_TIMEOUT` (`tools/pipeline-trigger/trigger.sh:284-290`) |
| Nie można uruchomić przebiegu `posts` z martwym postem | app: `PipelineRunService.java:198-201` | admin | hard (app) | named: 400 `post_dead` |
| Przebieg startuje tylko z `queued`; każda zmiana przebiegu wymaga oczekiwanego statusu | app: `api/pipeline/state/RunRecorder.java:63-68`, `:79-87`, `:96-105`, `:108-126`, `:140-158`, `:203-213`, `:377-388` (każdy `UPDATE … WHERE … status = :running`) | wszystkie zapisy `pipeline_runs` (pełna lista pisarzy niżej) | hard (app); „no row locks” (`RunRecorder.java:28-31`) | zapis „nic nie zmienia”, zwraca `false`; krok odmawia `RunNoLongerRunningException` (`:252`) |
| Przyczyna przerwania tylko przy `interrupted`, ze zbioru `deploy`/`restart`/`shutdown`/`lost` | DB: `mig/V8__pipeline_run_progress.sql:17-18`, `mig/V11__pipeline_run_interruption_lost.sql:10-11` | `RunRecorder.java:278-290`, `:320-333`, `:356-370` | hard (data) | — |
| Wynik źródła w przebiegu: jeden wiersz na (przebieg, źródło); `failed` ⇔ ma błąd; `skipped <= fetched` | DB: `mig/V12…:44-48`; app: `RunRecorder.java:140-158` (tylko gdy przebieg `running`); `api/pipeline/flow/RunFlow.java:91-92`, `:215`, `:219` | jedyny pisarz `RunRecorder.source` / `storeRejected` | hard (data) | constraint / `IllegalArgumentException` |
| Przebieg `range`, w którym żadne źródło nie dało posta, kończy się `failed` | app: `SourceIngest.java:136-138` (`NoPostsFetchedException`) | fetch każdego `range` | hard (app) | named exception → `failed` |
| Źródło, które rzuciło lub nic nie dało, jest osobnym wynikiem; reszta idzie dalej | app: `SourceIngest.java:105-107`, `:114-116`, `:118-129` | fetch | hard (app) | logs and continues (WARN), wynik `failed`/`empty` |
| Status końcowy: `failed` gdy ocena zatrzymana; `succeeded` bez porażek, martwych i błędów technicznych; inaczej `partial`; `succeeded` z gorszym źródłem → `partial` | app: `RunFlow.java:299-304`; `api/pipeline/flow/RunSequence.java:200-203` | koniec każdego przebiegu | hard (app) | — |
| Kiedy przebieg „wymaga uwagi” (jedna reguła dla dzwonka, listy, strony, alertu) | app: `api/pipeline/run/PipelineRun.java:316-347`, `:350-352` | `RunNotifications.java:111`, `PipelineRunController.java:254` | hard (app) | — |
| Przebieg bez postępu przez `PIPELINE_STALL_TIMEOUT` (domyślnie najdłuższe wywołanie LLM + 5 min, min. 15 min; zakres 1 ms–1 d) zostaje zatrzymany `failed` | app: `RunRecorder.java:300-312`; `PipelineRunService.java:461-482`; `api/pipeline/PipelineStallSettings.java:42-51`; `api/pipeline/RunWatchdog.java:71-77`, `:93-113` | tylko przebiegi tej instancji (`RunWatchdog.java:28-30`) | hard (app) | named (status `failed`, błąd „no progress for … : the run was stopped”) |
| Osierocone przebiegi (żaden aktywny bez aktywności przez timeout, żaden nie skończył się w tym oknie) → `interrupted`/`lost`, wszystkie albo żaden | app: `RunRecorder.java:356-370`; `PipelineRunService.java:240-271` | tylko żądanie startu przebiegu (`:181`, `:196`) | hard (app), wyzwalane wyłącznie żądaniem | porażka: logs and continues + alert `ORPHAN_CLEANUP_FAILED` (`:250-256`) |
| Przy starcie serwera każdy `queued`/`running` → `interrupted` (`deploy` vs `restart` po `deployment_id`) | app: `api/pipeline/PipelineStartupRecovery.java:84-115`; `RunRecorder.java:320-333` | start serwera (nie komendy CLI, `@ConditionalOnWebApplication` `:53`) | hard (app) | porażka: logs and continues + `RECOVERY_FAILED` (`:101-107`) |
| Deploy jest „bezpieczny”, gdy nie ma żywego aktywnego przebiegu | app: `api/pipeline/api/DeploySafetyController.java:64-70`; `PipelineRunRepository.java:53` | `GET /api/deploy-safety` (publiczny) | hard (app) | błąd bazy → 503 `{"status":"unavailable"}` (`:55-57`) |

### Posty: zapis, etapy, próby

| reguła (inference) | strzeżona w | pokryte ścieżki | ścisłość | obsługa porażki |
|---|---|---|---|---|
| Post jest zapisany raz na (źródło, typ elementu, id w źródle) | DB: `mig/V4…:16` `uq_posts_source_item`; app: `api/pipeline/step/PostStore.java:99-109` (wcześniejsze klucze i powtórki w pobraniu), `:126-130` (wyścig: „Stored meanwhile by another run”) | jedyny pisarz `posts` przy INSERT (`PostStore.java:213`) | hard (data) | duplikat liczony, post „left as it is” (`PostStore.java:45-46`) |
| Typ elementu to model współdzielony `post`/`link`/`comment`; źródło `wykop`/`reddit`; język `pl`/`en` = język źródła; kanał niepusty | DB: `mig/V4…:15`, `mig/V12…:25`, `mig/V16__posts_language.sql:13-14`, `mig/V13__posts_channel.sql:12-13`; app: `api/posts/Post.java:150` (`this.language = source.language()`), `api/sources/Source.java:18`, `:25` | INSERT | hard (data) | odrzucony wiersz → `rejected`, liczony jako `skipped` (`PostStore.java:47-52`, `:155-161`) |
| Surowy payload: jeden na post, gzip JSON, SHA-256 | DB: `mig/V3…:75-78`, `:107` `uq_posts_raw_payload`, `:108` FK | `api/raw/PostgresRawPayloadStore.java:32` | hard (data) | — |
| Etap posta z `stored/assessing/assessed/failed/dead` sprzężony z liczbą prób: `stored`=0, `failed`=1–2, `dead`=3, `assessing`/`assessed` ≤ 2 | DB: `mig/V3…:114`, `:116-121` `ck_posts_stage_attempts`; app: `api/pipeline/state/PostTransitions.java:224-232` (`Outcome.after`), `api/posts/Post.java:41` (`MAX_ASSESSMENT_ATTEMPTS = 3`) | wszystkie zmiany etapu (tylko `PostTransitions`) | hard (data) | constraint |
| Post `assessing` ma właściciela-przebieg i tylko wtedy | DB: `mig/V3…:122` `ck_posts_assessing_owner`; app: `PostTransitions.java:72-83` (claim tylko gdy przebieg `running`), `:93-112` (wynik tylko, gdy przebieg nadal posiada post) | dispatcher | hard (data) + hard (app) | wynik niezapisany → `false` (post nie zmieniony) |
| Ocenić można tylko post `stored` lub `failed` (nie `assessed`, nie `dead`) | app: `PostTransitions.java:75`; `api/pipeline/step/AssessmentSelection.java:60-65` | wszystkie przebiegi | hard (app) | post pominięty bez komunikatu (patrz „Failure handling”) |
| Trzecia zapisana nieudana próba czyni post martwym; martwy opuszcza etap tylko przez przywrócenie admina | app: `PostTransitions.java:229-231`, `:190-201`; `api/posts/api/DeadPostController.java:46-56` | dispatcher; `POST /api/admin/posts/restore` | hard (data) (CHECK) + hard (app) | — |
| Przywrócenie: tylko posty `dead`, 1–100 id; wraca do `stored` z 0 prób i 0 błędów technicznych; stare próby zostają | app: `PostTransitions.java:190-201`; `DeadPostController.java:61-63` | jedyny pisarz | hard (app) | 400 `invalid_request`; inne id „left as it is” (zwraca liczbę przywróconych) |
| 3 błędy techniczne z rzędu = post „wstrzymany”: przebieg `retry` go nie bierze, przebieg `posts` admina tak; każdy zapisany wynik zeruje licznik | DB: `mig/V10__posts_technical_failures.sql:11` (tylko `>= 0`); app: `api/posts/Post.java:47`; `api/posts/PostRepository.java:38-45`, `:52-58`; `PostTransitions.java:98` (zerowanie), `:133-141`, `:151-160` | retry (leftovers), `existsLeftover` | hard (app) | — |
| Przebieg `range` ocenia tylko posty, które sam zapisał jako pierwszy | app: `AssessmentSelection.java:37-44`; `PostRepository.java:65-66` | range | hard (app) | — |
| Przebieg `retry` powstaje tylko obok żądania zakresu, gdy są leftovers | app: `PipelineRunService.java:185-186` | admin i cron | hard (app) | — |
| Ocena: wynik `ok` ma output i nie ma klasy porażki; `failed` odwrotnie; klasy, źródło wyniku, dostawca, kanał, tier, effort, tryb — zbiory zamknięte | DB: `mig/V3…:161-175`; test: `test/persistence/ClosedSetsIT.java:52-60` | `PostTransitions.java:109` (jedyny pisarz `assessments`) | hard (data) | — |
| Wpis bez treści (< 3 punkty kodowe po usunięciu URL, tagów, wzmianek) nie trafia do modelu; dostaje gotową ocenę `unclear` bez celów | app: `api/agents/assessor/Prefilter.java:20`, `:35-37`, `:41-42`; `api/agents/assessor/Assessor.java:71-75` | każda ocena | hard (app) | silently corrects (inference): post kończy jako `assessed` z oceną `prefilter`, widoczny w feedzie (`PostController.java:161`) |
| Odpowiedź modelu musi spełnić schemat i reguły domenowe D1–D15 kontraktu V2; każde naruszenie = nieudana próba z klasą porażki | app: `api/agents/assessor/validation/RuleId.java:14-57`; `RuleBasedValidator.java:21-26`, `:40-46`; `Assessor.java:96-105` | każda odpowiedź modelu | hard (app) | named: `AssessmentResult.failed(failureClass, violations…)` |
| Zatrzymanie kroku oceny: 3 odmowy z rzędu, 3 niedostępności z rzędu, 3 nieklasyfikowane błędy klienta z rzędu, odrzucona konfiguracja; wstępne sprawdzenie klucza przed pierwszym postem | app: `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:54-57`, `:121`, `:127`, `:135` | assess | hard (app) | krok `failed` z przyczyną → przebieg `failed` (`RunSequence.java:215-218`, `RunFlow.java:300-301`) |

### Feed, widoczność, powiadomienia

| reguła (inference) | strzeżona w | pokryte ścieżki | ścisłość | obsługa porażki |
|---|---|---|---|---|
| Użytkownik widzi w feedzie, szczególe i surowych danych tylko posty `assessed` | app: `api/posts/api/PostController.java:93`, `:126-130`; `api/posts/PostRepository.java:123-130`, `:137-143` | `GET /api/posts`, `/{id}`, `/{id}/raw` | hard (app) | named: 404 `post_not_found` („an unknown id and any other stage are alike not found”) |
| Filtr feedu działa na najnowszej ocenie `ok` i na jednym celu spełniającym wszystkie warunki | app: `PostRepository.java:115-122`, `:137-143` | `GET /api/posts` | hard (app) | — |
| Fraza filtra ≤ 100 znaków, bez NUL; nieznana wartość `kind`/`direction` = błąd | app: `PostController.java:90-92`; `api/posts/PostFilter.java:24`; UI: `web/components/FeedFilter.vue:57` (`maxlength`) | feed | hard (app) | 400 `invalid_request` |
| Nieudane, martwe i wstrzymane posty widzi tylko admin | app: `api/posts/PostRepository.java:105-113`; trasa `/api/admin/posts/failed` (`FailedPostController.java`) za `SecurityConfig.java:107` | admin | hard (app) | 403 |
| Powiadomienie o przebiegu: jedno na (odbiorca, rodzaj, przebieg); dostaje je każdy admin, który może się logować | DB: `mig/V9__create_notifications.sql:17-19`; app: `api/notifications/NotificationRepository.java:51-53` (`ON CONFLICT … DO NOTHING`), `api/notifications/RunNotifications.java:109-122` | każdy koniec przebiegu (własny, watchdog, shutdown, osieroty, recovery) | hard (data) — idempotentne | — |
| Admin widzi i oznacza tylko swoje powiadomienia; ponowne przeczytanie zachowuje pierwszy czas | app: `api/notifications/api/NotificationController.java:54-57`, `:73-79`; `NotificationRepository.java:64-65` (`COALESCE(read_at, now())`, `recipient_id = :recipientId`), `:75` | `/api/admin/notifications/**` | hard (app) | named: 404 `not_found` „for an unknown id and for another admin's” |
| Alert operatora `RUN_NEEDS_ATTENTION` tylko gdy powstały powiadomienia; nie dla przebiegu crona `failed`/`interrupted` i nie, gdy jedyny powód to martwe posty | app: `RunNotifications.java:115-120`, `:125-127`, `:134-136` | `runEnded` | hard (app) | — |
| Kolejka alertów: to samo zdarzenie i przebieg raz na 10 min, ≤ 2 na minutę, ≤ 20 czekających | app: `api/alerts/OperatorAlertQueue.java:56`, `:59`, `:64`, `:184`, `:196` | każdy `raise` | hard (app) | logs and continues: odrzucony alert = INFO/WARN (`:174`, `:184`, `:196-200`) |
| Jakość ocen źródła: powyżej progu, gdy udział porażek (> 5 % Wykop, > 25 % Reddit) lub tez w innym języku (> 2 % / > 30 %) przekracza próg przy ≥ 100 próbach / ≥ 500 tezach w oknie 7 dni | app: `api/sources/Source.java:18`, `:25`; `api/posts/AssessmentQuality.java:57`, `:60`, `:63`, `:166-170` | podsumowanie nocy, `/admin` | hard (app) (tylko odczyt, „nothing here is stored”, `AssessmentQuality.java:28`) | alert `ASSESSMENT_QUALITY` (`InternalPipelineRunController.java:123-125`) |
| „Dni bez danych”: okno 14 dni do D-2; dzień pokryty dla źródła tylko jego wynikiem `succeeded` w przebiegu `range` `succeeded`/`partial` | app: `api/pipeline/run/MissingDays.java:48`, `:55`, `:62-68`; `PipelineRunController.java:153-170`; `PipelineRunRepository.java:113-117`, `:133-136` | `GET …/missing-days` | hard (app) (tylko odczyt; „nothing here starts a run”) | — |

### Źródła (pozyskanie i mapowanie)

| reguła (inference) | strzeżona w | pokryte ścieżki | ścisłość | obsługa porażki |
|---|---|---|---|---|
| Element bez id, bez tekstu, bez poprawnej daty, bez subreddita, usunięty lub skasowany nie staje się postem | app: `api/sources/wykop/WykopPosts.java:46-47`, `WykopMapper.java:62-63`, `:70-71`; `api/sources/reddit/RedditMapper.java:44-55`, `:63-73`, `RedditPosts.java:47` | oba fetchery | hard (app) | `UnmappableItemException` → `skipped++` (`WykopFetcher.java:52-53`, `RedditFetcher.java:52-53`), logs and continues |
| Posty Reddita zdjęte przez moderację lub autora są pomijane przed liczeniem | app: `api/sources/reddit/RedditItem.java:42-43`; `RedditRepository.java:187-188`, `:126-127` | Reddit | hard (app) | logs and continues (INFO z liczbą; nie wchodzą do `fetched` ani `skipped`) |
| Wykop: tylko tag `gielda` | app: `api/sources/wykop/WykopPosts.java:24` | Wykop | hard (app) | — |
| `REDDIT_SUBREDDITS`: nazwy `[A-Za-z0-9_]{2,21}`, bez powtórzeń, ≥ 1 | app: `api/sources/reddit/RedditConfiguration.java:39`, `:63-64`, `:71-72` | start aplikacji | hard (app) | błąd ustawień psuje start |
| Niespójne stronicowanie lub limit stron źródła = porażka źródła | app: `WykopRepository.java:139-146`, `:221`; `RedditRepository.java:162`, `:197-207` | fetch | hard (app) | named `WykopApiException` / `RedditApiException` → wynik źródła `failed` |

### UI (SPA)

| reguła (inference) | strzeżona w | ścisłość | uwagi |
|---|---|---|---|
| Strony wymagają sesji poza `/login`, `/activate`; `/admin` i `/admin/runs/:id` tylko dla admina | `web/router/index.ts:40-41`, `:62-69` | client-only, ale API strzeże tego samego (`SecurityConfig.java:107`; komentarz `web/router/index.ts:29` „the API answers 403 anyway”) | właściwa dwuwarstwowość |
| Link do panelu i dzwonek tylko dla admina | `web/components/AppHeader.vue:13`, `:36`, `:39` | client-only (ukrycie), API strzeże | — |
| „Uruchom” wyłączone bez wybranego źródła; nieskonfigurowane źródło nie do zaznaczenia | `web/views/AdminView.vue:51-56`, `:434`, `:443` | client + API (`SourceIngest.java:48-60`) | — |
| „Ponów zaznaczone” bierze tylko nie-martwe; „Przywróć martwe” tylko martwe; przyciski wzajemnie blokowane | `web/views/AdminView.vue:326-330`, `:597`, `:605` | client + API (`PipelineRunService.java:198-201`, `PostTransitions.java:196`) | — |
| 401 z dowolnego wywołania kończy sesję w SPA | `web/api/client.ts:96`; `web/auth/session.ts:55-56` | client (stan po stronie klienta z założenia) | — |
| Brak okien potwierdzenia | grep `confirm(` w `web/views/*.vue`, `web/components/*.vue` — 0 trafień | — | przywrócenie martwych i ponowienie działają bez potwierdzenia (evidence) |
| Reguły kopiowane z API do SPA zebrane w rejestrze | `web/contract/rules.json` (`limits`: `feedQueryMaxLength`, `runRangeMaxDays`, `pageMaxSize`, `passwordMinLength`, `passwordMaxLength`, `maxAssessmentAttempts`, `pageMax`) | strażnik testowy (nie czytałem `rules.test.ts` ani `SpaRulesTest`) | — |

### Zadania cykliczne

| reguła (inference) | strzeżona w | ścisłość | obsługa porażki |
|---|---|---|---|
| Codzienny przebieg tylko na produkcji (poza `TRIGGER_IN_ANY_ENVIRONMENT=true`) | `tools/pipeline-trigger/trigger.sh:26-29` | hard (job) | exit 0 „skipped” |
| Cron kończy się błędem, gdy któryś przebieg nocy `failed`/`interrupted`; `partial` = sukces | `trigger.sh:357-361`, `:380-389` | hard (job) | exit 1 (`:19`); błąd konfiguracji exit 2 (`:20`) |
| Kopia tylko na produkcji; odmowa przy pustym `posts` lub mniejszej liczbie wierszy niż w ostatnim manifeście, chyba że `BACKUP_ACCEPT_SHRINK` wskazuje ten manifest | `tools/db-backup/backup.sh:35-38`, `:238`, `:256-284` | hard (job) | `fail` → exit 1 (`:29`), nic nie wysłane |
| Wiersze sesji nigdy w kopii | `backup.sh:26`, `:293`, `:300`; `restore-check.sh:134` | hard (job) | exit 1 |
| Kopia wysyłana dopiero po udanym pełnym odtworzeniu; każda tabela musi być sklasyfikowana | `backup.sh:330`, `:336-337`; `tools/db-backup/restore-check.sh:107-114` | hard (job) | „restore-check failed; nothing uploaded” |
| Retencja 14 dziennych + 12 miesięcznych (dodatnie liczby całkowite) | `backup.sh:118-121`, `:189-203` | hard (job) | `fail` |

## Soft and client-only rules

- **Odrzucanie zapisów cross-origin** — soft. Brak nagłówka `Origin` przepuszcza żądanie (`api/auth/OriginGuardFilter.java:47`: `if (origin != null && isCrossOrigin(…))`). Ochronę dla przeglądarki daje też cookie `SameSite=Strict` (`api/config/SessionConfig.java:41`). Obejście: klient spoza przeglądarki bez `Origin` z ważnym cookie (inference).
- **Limit logowania** — hard (app) w jednej replice. Stan jest w pamięci (`LoginRateLimiter.java:60-66`), więc restart procesu go zeruje, a druga replika miałaby osobny licznik (`:26-27`, evidence z komentarza).
- **Zgodność dwóch wpisanych haseł przy aktywacji** — client-only (`web/views/ActivateView.vue:56`). API przyjmuje jedno hasło (`ActivationController.java:82-83`). To stan formularza, więc inference: właściwe miejsce.
- **Limit 7 dni i `to <= dziś`** — tylko serwer (`PipelineRunController.java:188-189`). Formularz ma tylko `required` (`AdminView.vue:419-420`), więc błąd użytkownik widzi dopiero z 400 (evidence).
- **Czyszczenie osieroconych przebiegów** — soft co do czasu. Działa tylko przy żądaniu startu (`PipelineRunService.java:181`, `:196`; „The API asks the database nothing more while idle: only a request does it.” `:76`). Bez żądań martwy przebieg zostaje `running` w bazie. `/api/deploy-safety` uznaje go za bezpieczny (`DeploySafetyController.java:65`).
- **Wstrzymanie posta (3 błędy techniczne)** — soft z założenia. `retry` go pomija (`PostRepository.java:43-45`), ale przebieg `posts` admina go bierze (`AssessmentSelection.java:50-51`, `:60-65`). Baza ma tylko `technical_failures >= 0` (`mig/V10…:11`).

## Writers that work against a guard

Pełna lista pisarzy w kodzie main (grep, `api/**`): `posts` — `PostStore.java:213` (INSERT), `PostTransitions.java:44/74/97/152/195`; `assessments` — `PostTransitions.java:109`; `pipeline_runs`, `pipeline_run_steps`, `pipeline_run_sources` — `PipelineRunService.java:222` (INSERT) i `RunRecorder.java:64-420`; `users` — `ActivationLinks.java:88`, `AdminBootstrap.java:81`, encja przez `User.setPassword`/`clearPassword` (`User.java:92-102`); `activation_tokens` — `ActivationTokenRepository.java:45`, `ActivationLinks.java:168`; `spring_session` — `ActivationLinks.java:196`; `raw_payloads` — `PostgresRawPayloadStore.java:32`; `notifications` — `NotificationRepository.java:51/64/75`. Nie znalazłem innych pisarzy `posts` poza `PostTransitions` i INSERT w `PostStore`, co potwierdza też strażnik `test/pipeline/state/PostColumnsIT.java:56-64`.

- **`PostTransitions.restore`** (`PostTransitions.java:190-201`) robi celowo coś odwrotnego niż reguła „trzecia porażka = martwy”: zeruje `assessment_attempts` i `technical_failures` martwego posta. To jedyne wyjście z `dead` (`:30-31`), a stare próby zostają w `assessments` (`:185`).
- **`ActivationLinks.resetPassword`** (`ActivationLinks.java:111-129`) zdejmuje hasło dowolnemu kontu z logowaniem, adminowi też. Na koncie systemowym odmawia.
- **`create-user --role admin`** omija regułę „jeden admin z bootstrapu”. `AdminBootstrap.java:57-60` wprost na nią wskazuje.
- **Migracje z backfillem** zapisują dane z pominięciem aplikacji: `mig/V8…:31-36` zamyka kroki, `mig/V12…:51-88` wylicza wyniki źródeł heurystyką `f.error LIKE '%gave no post to store%'` (`:57`), `mig/V13…:11` ustawia kanał, `mig/V16…:12` ustawia język.

## Failure handling

Wpisuję tylko przypadki inne niż *named error*.

- **silently corrects**:
  - Znak U+0000 jest usuwany z tekstów posta (`api/posts/Post.java:149`, `:152-157`; `RedditMapper.java:78`) i zastępowany w błędzie technicznym (`PostTransitions.java:207-210`).
  - Błędy są obcinane: przebiegu i kroku do 2000 znaków (`RunRecorder.java:42`, `:435-437`), posta do 500 (`PostTransitions.java:37`), w alercie do 300 (`RunNotifications.java:43`), a podsumowanie fetch do 1000 obok odrzuconych (`RunSequence.java:68`, `:209-212`).
  - Fraza filtra jest przycinana z białych znaków, a pusta znaczy brak warunku (`PostFilter.java:29-36`).
  - Wpis bez treści dostaje syntetyczną ocenę `unclear` i staje się `assessed` (`Prefilter.java:41-42`, `Assessor.java:72-74`).
- **swallowed silently**:
  - Przebieg `posts` z id nieznanymi, `assessed` albo już ocenianymi zostaje zakolejkowany i ten post po prostu pomija (`AssessmentSelection.java:60-65`, bez logu; walidacja odrzuca tylko martwe, `PipelineRunService.java:198-201`). Przebieg z samymi takimi id kończy się `succeeded` z 0 ocen (inference z `RunFlow.java:303`).
  - Przywracanie ignoruje id niemartwe. Wynik zwraca tylko liczbę, ale `DeadPostController.java:53-54` loguje INFO.
- **logs and continues**:
  - Elementy niemapowalne źródła trafiają do `skipped` (`SourceIngest.java:109-111`), a usunięte z Reddita są pomijane z INFO (`RedditRepository.java:126-127`).
  - Wiersz posta odrzucony przez bazę idzie do `skipped` z WARN bez treści (`PostStore.java:155-161`).
  - Porażka sprzątania osieroconych przebiegów nie blokuje decyzji (`PipelineRunService.java:250-256`).
  - Porażka recovery przy starcie nie zatrzymuje serwera (`PipelineStartupRecovery.java:101-107`).
  - Porażka powiadomień jest ponawiana raz, potem idzie alert (`RunNotifications.java:74-106`, „It never throws”).
  - Utracone liczniki przebiegu dają alert `RUN_COUNTERS_LOST` (`ConcurrentAssessmentDispatcher.java:81-86`).
  - Alerty ponad limit są porzucane z WARN (`OperatorAlertQueue.java:196-200`).
  - Nieudany check-in healthchecks nie zmienia kodu wyjścia crona (`trigger.sh:83`).
- **generic error**: naruszenia CHECK, UNIQUE i NOT NULL poza ścieżką `PostStore` kończą się wyjątkiem bazy. Przebieg wtedy kończy się `failed` z `"<SimpleName>: <message>"` (`PipelineRunService.java:367-369`).

## Atomicity

| operacja | jedna transakcja? | cytat |
|---|---|---|
| Utworzenie przebiegów (retry + range) z kontrolą „nic aktywnego” | tak, pod blokadą doradczą; kolejkowanie dopiero po commicie | `PipelineRunService.java:216-226` |
| Sprzątanie osierot (przebiegi `lost` + zwolnienie postów) | tak, osobno od decyzji: zostaje zatwierdzone nawet przy odmowie | `PipelineRunService.java:243-248`, `:210-214` |
| Zatrzymanie przez watchdog (przebieg `failed` + posty z powrotem) | tak | `PipelineRunService.java:463-469` |
| **Zamknięcie przebiegu po końcu / wyjątku** (`fail` + `releaseAll`) | **nie**: `fail` ma własną transakcję (`RunRecorder.java:96-105`), a `releaseAll` to osobne polecenie (`PipelineRunService.java:497-498`) | Gdy `releaseAll` zawiedzie, przebieg jest `failed`, a posty zostają `assessing`. Naprawia je dopiero `releaseOrphans` przy następnym żądaniu startu lub starcie serwera (`PostTransitions.java:174-180`). **Finding** (inference) |
| Zamknięcie przy wyłączaniu instancji (interrupt + release) | tak | `PipelineRunService.java:544-548` |
| Recovery przy starcie (przebiegi + osierocone posty) | tak | `PipelineStartupRecovery.java:92-99` |
| Przebieg kończy się z zamknięciem otwartych kroków (`fail`, `interrupt`, `stall`, `interruptAll`, `interruptOrphans`) | tak | `RunRecorder.java:96-105`, `:278-290`, `:300-312`, `:320-333`, `:356-370` |
| Zamknięcie pojedynczego kroku | nie, celowo: dwa polecenia bez transakcji | `RunRecorder.java:390-394` |
| Wynik źródła (liczniki przebiegu + wiersz źródła) i `storeRejected` | tak, jedno polecenie `WITH … UPDATE … INSERT/UPDATE` | `RunRecorder.java:140-158`, `:177-190` |
| Zapis oceny (etap posta + wiersz `assessments`) | tak | `PostTransitions.java:93-112` |
| **Wynik posta a liczniki przebiegu** | **nie**: `record` (`ConcurrentAssessmentDispatcher.java:312`) i `recorder.count` (`:423-431`) to osobne zapisy | Licznik może się zgubić, wtedy idzie ERROR i alert `RUN_COUNTERS_LOST`. Status przebiegu „may not show that it needs attention” (`:81-86`). **Finding** (evidence z komentarza) |
| Zapis nowych postów | paczki po 500 (surowe + posty w jednej transakcji), przy błędzie integralności po jednym | `PostStore.java:116`, `:123` |
| **Nowe posty a licznik `new_posts`** | **nie**: licznik idzie po kroku (`RunSequence.java:138`). Według komentarza „A failed write ends the run as any exception” (`:132-133`), a zapisane posty zostają | inference |
| Utworzenie konta z linkiem / reset (link + bez hasła + usunięcie sesji) / aktywacja (hasło + usunięcie tokenu + sesji) | tak, każda osobno | `ActivationLinks.java:82-90`, `:113-128`, `:156-173` |
| Bootstrap admina | tak, z blokadą doradczą | `AdminBootstrap.java:54-83` |
| Powiadomienia dla adminów | jedno polecenie `INSERT … SELECT … ON CONFLICT DO NOTHING`; alert po nim, poza transakcją | `NotificationRepository.java:51-53`; `RunNotifications.java:114-120` |

## Scheduled and asynchronous work

| reguła zależna od późniejszego działania | wyzwalacz | handler | dochodzi? |
|---|---|---|---|
| Codzienny przebieg D-2…D-1 + podsumowanie nocy | `.railway/railway.ts:289` `cronSchedule: "30 0 * * *"` → `tools/pipeline-trigger/trigger.sh:261` (`/daily`), `:246` (`/nightly-summary`) | `InternalPipelineRunController.java:83-89`, `:112-127` | tak (evidence: harmonogram + trasy) |
| Codzienna kopia bazy | `.railway/railway.ts:242` `cronSchedule: "0 2 * * *"` | `tools/db-backup/backup.sh` | tak |
| Watchdog przebiegu | `RunWatchdog.java:81-83` `scheduleWithFixedDelay` w `afterPropertiesSet`, tylko serwer (`@ConditionalOnWebApplication` `:44`) | `PipelineRunService.stopStalled` `:461` | tak, ale tylko dla przebiegów tej instancji (`RunWatchdog.java:94`) |
| Recovery przy starcie | `SmartInitializingSingleton.afterSingletonsInstantiated` (`PipelineStartupRecovery.java:84-86`) | `recover()` `:89` | tak |
| Przerwanie przy wyłączaniu | `@EventListener(ContextClosedEvent.class)` (`PipelineRunService.java:539`) | `onClose` | tak |
| Sprzątanie osieroconych przebiegów i postów | **brak timera**: tylko żądanie startu (`PipelineRunService.java:181`, `:196`) | `cleanUpOrphans` `:240` | tak, ale przy braku żądań nie działa wcale (celowo, `:76`; uzasadnienie w `RunWatchdog.java:31-33`) |
| Ponowna ocena leftovers (`stored` / `failed` z próbami) | tylko przy żądaniu zakresu (`PipelineRunService.java:185`), czyli w nocy przez cron | `AssessmentSelection.fromTargets` RETRY | tak |
| Ponowna ocena przywróconego martwego posta | następny przebieg (komentarz `DeadPostController.java:23-24`) | jak wyżej | tak |
| Wygaśnięcie linku aktywacyjnego | sprawdzane przy odczycie (`ActivationTokenRepository.java:28`, `:35-36`) | — | nie potrzebuje wyzwalacza. Nie znalazłem kodu usuwającego wygasłe tokeny (grep `expires_at <`, `delete.*activation` w `api/**`), więc wiersze zostają do nowego linku lub użycia (evidence) |
| Wygaśnięcie sesji po 30 dniach | `application.properties:47` | sprzątanie wygasłych wierszy `SPRING_SESSION`: **unknown**. W kodzie main nie ma `@Scheduled` ani `cleanup-cron` (grep), więc to domyślne zachowanie Spring Session JDBC, którego nie weryfikowałem. Komentarze o uśpieniu API (`RunWatchdog.java:31-33`) sugerują, że cykliczne zapytania mają znaczenie | unknown |
| Wysyłka alertów operatora | własny wątek `OperatorAlertQueue` (`:56-67`), 5 s na opróżnienie przy zamknięciu (`:67`, `:241-244`) | `NtfyAlerts` (`api/alerts/NtfyAlerts.java`) | tak. Kanał w kodzie to ntfy (`NtfyAlerts.java:34-39`; `trigger.sh:234` „sends it to the operator (ntfy)”) (evidence) |
| Odświeżanie dzwonka | `web/components/NotificationBell.vue:38`, `:91` (po zmianie strony, przy widocznej karcie, tylko na stronach admina) | `GET /api/admin/notifications` | tak (UI) |

## Unknowns

- Ciała reguł D1–D15 (`api/agents/assessor/validation/rules/*.java`), `StanceTable` (§7.2) i `OutputParser` — cytuję tylko deklaracje `RuleId.java:14-57`. Jak każda reguła sprawdza dane, nie weryfikowałem.
- `NightSummary.java` (treść i progi podsumowania nocy) oraz `AssessmentQuality.java` poza `status` i stałymi — nie czytane.
- Klienci LLM i `TransportRetry` (retry, flex fallback, czyszczenie klucza z komunikatów) — nie czytane. Reguły „klucz nigdy w logu” nie weryfikowałem.
- Odświeżanie ról w sesji: rola trafia do sesji przy logowaniu (`AccountDetailsService.java:48`). Nie znalazłem pisarza zmieniającego `users.role` (lista pisarzy wyżej), więc ryzyko starej roli w sesji nie ma dziś ścieżki (inference).
- Sprzątanie wygasłych sesji Spring Session (wyżej) — unknown.
- `content_hash` („to notice an edited post”, `mig/V3…:97`) jest liczony (`Post.java:159`), ale w kodzie main go nie porównuję: jedyny odczyt `getContentHash` jest w `test/JpaSchemaIT.java:181`, a duplikat „left as it is” (`PostStore.java:45-46`). Edycja posta w źródle nie jest więc wykrywana (inference; grep `contentHash|content_hash` w `src/main` i `src/test`).
- `tools/db-backup/nas-retention.sh`, `tables.sh` (klasyfikacja tabel) i `szczur-radar-web/dev/mock-api.js` — nie czytane poza grepem.
- Widoki `FeedView`, `PostDetailView`, `RunDetailView`, `LoginView` — tylko grep `:disabled|confirm(|maxlength`. Innych strażników UI w nich nie wykluczam.
- Strażnicy testowi: znam nagłówki `AuthorizationCompletenessIT`, `AdminRoleIT`, `ClosedSetsIT`, `PostColumnsIT`, `RunRecorderIT` i grep nazw constraintów w `MigrationsIT.java:114-190`. Testy `rules.test.ts`, `SpaRulesTest`, `PrEnvScenariosIT` i rejestr mutantów PIT (`test/mutation/`) tylko zlokalizowałem.

Polecenia: `git ls-files --cached --others --exclude-standard` (zakres); `wc -l` (rozmiary); `cat -n` / `sed -n` (odczyt plików); `grep -n -E` po: `@(Get|Post|…)Mapping`, `throw new|if \(|static final`, `UPDATE|INSERT INTO|DELETE FROM|.save(|@Modifying`, `@Scheduled|@EventListener|scheduleWithFixedDelay|cleanup`, `contentHash|content_hash`, `confirm\(|:disabled|maxlength|role ===|MAX_`, `cronSchedule`, `exit [0-9]|fail` w skryptach; `python3 -c` (klucze `rules.json`).
