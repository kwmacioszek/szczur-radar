# Evidence: language in the code

**Zakres:** `szczur-radar-api/`, `szczur-radar-web/`, `tools/pipeline-trigger/`, `tools/db-backup/`, HEAD `5b4ac92`. Skróty ścieżek z `0-sources.md`: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `web/` = `szczur-radar-web/src/`, `mig/` = `szczur-radar-api/src/main/resources/db/migration/`.

**Warstwy pokryte:** persistence (wszystkie migracje V1–V13 przeczytane w całości; 9 encji `@Table`), typy domenowe (wszystkie enumy i rekordy: `agents/assessor/contract/`, `pipeline/run/`, `posts/`, `sources/`, `llm/`, `alerts/`, `notifications/`, `user/`), operacje (`pipeline/state/`, `pipeline/step/`, `pipeline/flow/`, `pipeline/assess/`, `sources/wykop/`, `sources/reddit/`, `sources/transport/`), wejścia (wszystkie `@*Mapping`, `cli/CommandType`, `trigger.sh`, `backup.sh`), UI (`labels.ts`, `runs.ts`, `feedFilter.ts`, `missingDays.ts`, `router/`, `api/*.ts`, widoki, komponenty, `contract/`).

**Próbkowane, nie pokryte w całości:** `api/llm/{openrouter,gemini,deepseek}/` i `api/llm/transport/` (tylko nazwy typów), ciała reguł `validation/rules/` (tylko klasy + `RuleId`), `auth/` (publiczne metody), `dev/mock-api.js` (nagłówek + grep), `style.css` (poza słownictwem), testy (tylko sprawdzenie użycia `itemsOfDay`), `tools/db-backup/nas-retention.sh` (grep).

**Oznaczenia:** `evidence` = potwierdzone w tej sesji; `inference` = wniosek autora; `unknown` = nie ustalono; `comment` = opiera się tylko na komentarzu/Javadocu (proza w kodzie, ważyć słabiej).

**Zastrzeżenie o niezależności:** w trakcie pracy harness automatycznie dołączył do kontekstu `api/pipeline/CLAUDE.md`, a jeden `rg` bez wykluczenia `*.md` zwrócił linie z `api/sources/CLAUDE.md` i `api/pipeline/CLAUDE.md`. Żadnego z tych plików nie cytuję i nie opieram na nich ustaleń; każde poniższe stwierdzenie zweryfikowano w kodzie. Wpływu na dobór pytań nie da się jednak w pełni wykluczyć. Celowo otwarto jeden plik `.md`: `resources/assessor/system-prompt.md`, zasób ładowany przez kod (warstwa „application logic (resources)” w `0-sources.md`).

---

## Concepts and their names across layers

Wszystkie wiersze: `evidence`. „—" = w tej warstwie nie znaleziono nazwy (zakres: warstwa wg `0-sources.md`).

### Pozyskanie i posty

| concept | persistence | domain type | operations | entry points | UI | user-facing label (język) |
|---|---|---|---|---|---|---|
| **Post** (zapisana wypowiedź ze źródła) | `posts` `mig/V3__create_pipeline_and_posts.sql:84` | `Post` `api/posts/Post.java:37`; `SourcePost` `api/sources/SourcePost.java:27` | `PostStore.store` `api/pipeline/step/PostStore.java:61` | `GET /api/posts/{id}` `api/posts/api/PostController.java:100` | `PostCard` `web/components/PostCard.vue:26`; `PostDetailView` `web/views/PostDetailView.vue:155` | „Posty" `web/components/AppHeader.vue:35` (pl) |
| **Typ elementu** | `posts.item_type` `mig/V4__posts_shared_item_type.sql:8` | `ItemType` POST/LINK/COMMENT `api/agents/assessor/contract/ItemType.java:10` | `WykopMapper.map` `api/sources/wykop/WykopMapper.java:49`; `RedditMapper.map` `api/sources/reddit/RedditMapper.java:50` | `PostItem.itemType` `api/posts/api/PostController.java:144` | `label('itemType', …)` `web/components/PostCard.vue:26` | „wpis", „znalezisko", „komentarz" `web/labels.ts:3` (pl) |
| **Tożsamość posta** | `uq_posts_source_item` `mig/V4__posts_shared_item_type.sql:16` | `PostKey` `api/posts/PostKey.java:6` | `findKeys` `api/posts/PostRepository.java:30` | — | — | — |
| **Źródło** | `posts.source` `mig/V3__create_pipeline_and_posts.sql:86`; `raw_payloads.source` `:67`; `pipeline_runs.sources` `mig/V5__pipeline_run_sources.sql:6`; `pipeline_run_sources.source` `mig/V12__pipeline_run_sources.sql:36` | `Source` `api/sources/Source.java:9` | `SourceFetcher.fetch` `api/sources/SourceFetcher.java:26`; `SourceIngest.fetch` `api/pipeline/step/SourceIngest.java:95` | `GET /api/admin/pipeline-runs/sources` `api/pipeline/api/PipelineRunController.java:141` | `web/views/AdminView.vue:315` | „Wykop", „Reddit" `web/labels.ts:72`; „Źródła" `web/views/AdminView.vue:301` (pl) |
| **Kanał posta** | `posts.channel` `mig/V13__posts_channel.sql:10` | `SourcePost.channel` `api/sources/SourcePost.java:27`; `Post.channel` `api/posts/Post.java:67`; `Item.channels` `api/agents/assessor/contract/Item.java:16` | `WykopPosts.TAG` `api/sources/wykop/WykopPosts.java:24`; `RedditPosts.post` `api/sources/reddit/RedditPosts.java:50` | `PostItem.channel` `api/posts/api/PostController.java:144` | `postOrigin`, `CHANNEL_PREFIXES` `web/labels.ts:81,84` | prefiks `#` / `r/` `web/labels.ts:81` |
| **Surowy ładunek** | `raw_payloads` `mig/V3__create_pipeline_and_posts.sql:65` | `RawPayload` `api/raw/RawPayload.java:23`; `RawPayloadRef` `api/raw/RawPayloadRef.java:7` | `RawPayloadStore.store/load` `api/raw/RawPayloadStore.java:17,25`; `RawInputMapper.map` `api/sources/RawInputMapper.java:19` | `GET /api/posts/{id}/raw` `api/posts/api/PostController.java:111` | `fetchPostRaw` `web/api/posts.ts:164` | „Dane dla nerdów: surowy JSON źródła" `web/views/PostDetailView.vue:179` (pl) |
| **Feed** (posty ocenione) | — (brak tabeli; `posts.stage='assessed'`) | — (`PostFilter` `api/posts/PostFilter.java:18` to tylko filtr) | `findAssessedPage` `api/posts/PostRepository.java:98` | `GET /api/posts` `api/posts/api/PostController.java:85` | `FeedView`, `web/router/index.ts:38`; `FeedFilter` `web/components/FeedFilter.vue:54` | „Posty" `web/views/FeedView.vue:173` (pl) — słowo „feed" w UI nie występuje w znalezionych etykietach |
| **Filtr feedu** | JSONB `output -> 'targets'` `api/posts/PostRepository.java:114` | `PostFilter(quote, kind, direction)` `api/posts/PostFilter.java:18` | `findMatching` `api/posts/PostRepository.java:123` | parametry `q`, `kind`, `direction` `api/posts/api/PostController.java:88-90` | `web/feedFilter.ts:41-45` | „Spółka lub temat", „Rodzaj celu", „Stanowisko" `web/components/FeedFilter.vue:56,60,67` (pl) |

### Przebieg potoku

| concept | persistence | domain type | operations | entry points | UI | user-facing label (język) |
|---|---|---|---|---|---|---|
| **Przebieg** | `pipeline_runs` `mig/V3__create_pipeline_and_posts.sql:7` | `PipelineRun` `api/pipeline/run/PipelineRun.java:33` | `PipelineRunService.startRange/startPosts` `api/pipeline/PipelineRunService.java:180,195`; `RunSequence.run` `api/pipeline/flow/RunSequence.java:80` | `POST /api/admin/pipeline-runs` `api/pipeline/api/PipelineRunController.java:98` | `/admin/runs/:id` `web/router/index.ts:41` | „Nowy przebieg" `web/views/AdminView.vue:296`; „Ostatnie przebiegi" `:361` (pl) |
| **Rodzaj przebiegu** | `kind`, `ck_pipeline_runs_kind` `mig/V3__create_pipeline_and_posts.sql:9,29` | `RunKind` RANGE/RETRY/POSTS `api/pipeline/run/RunKind.java:13` | `PipelineRun.range/retry/posts` `api/pipeline/run/PipelineRun.java:153,169,178` | `StartRequest` `api/pipeline/api/PipelineRunController.java:177` | `web/api/pipeline.ts:6` | „zakres dat", „ponowienie resztek", „wybrane posty" `web/labels.ts:46` (pl) |
| **Krok** | `pipeline_run_steps` `mig/V3__create_pipeline_and_posts.sql:43` | `PipelineRunStep` `api/pipeline/run/PipelineRunStep.java:21`; `RunStep` `api/pipeline/run/RunStep.java:10` | `RunRecorder.step` `api/pipeline/state/RunRecorder.java:202` | `StepView` `api/pipeline/api/PipelineRunController.java:289` | `RunStepName`, `RunStep` `web/api/pipeline.ts:8,36` | „pobranie, zapis, wybór, ocena, podsumowanie" `web/labels.ts:62`; „Kroki" `web/views/RunDetailView.vue:260` (pl) |
| **Wynik źródła w przebiegu** | `pipeline_run_sources` `mig/V12__pipeline_run_sources.sql:33`; `sources_failed/empty` `:20-21` | `PipelineRunSource` `api/pipeline/run/PipelineRunSource.java:26`; `SourceFetchStatus` `api/pipeline/run/SourceFetchStatus.java:12` | `RunFlow.SourceFetch` `api/pipeline/flow/RunFlow.java:81`; `RunRecorder.source` `api/pipeline/state/RunRecorder.java:140` | `SourceResultView` `api/pipeline/api/PipelineRunController.java:278` | `SourceResult` `web/api/pipeline.ts:47`; `sourceTone` `web/runs.ts:62` | „Wynik per źródło" `web/views/RunDetailView.vue:236`; „pobrane, bez postów, błąd" `web/labels.ts:73` (pl) |
| **Wymaga uwagi** | — (obliczane; wejście: liczniki) | `AttentionReason` `api/pipeline/run/AttentionReason.java:11` | `PipelineRun.attentionReasons/needsAttention` `api/pipeline/run/PipelineRun.java:316,350` | `RunView` `api/pipeline/api/PipelineRunController.java:244` | `attentionReasons` `web/runs.ts:93` | „wymaga uwagi" `web/views/AdminView.vue:373`; „Wymaga uwagi:" `web/views/RunDetailView.vue:173` (pl) |
| **Uruchomienie dzienne** | `requested_by` = `SystemAccount.SCHEDULER` `mig/V6__users_login_enabled_scheduler.sql:11` | brak własnego `RunKind`; `PipelineRun.scheduled()` `api/pipeline/run/PipelineRun.java:295` | `trigger.sh` `tools/pipeline-trigger/trigger.sh:3` | `POST /internal/pipeline-runs/daily` `api/pipeline/api/InternalPipelineRunController.java:65` | `requester` `web/runs.ts:137` | „cron" / „admin" `web/runs.ts:138` |
| **Dni bez danych** | zapytanie `findCoveringSince` `api/pipeline/run/PipelineRunRepository.java:108` | `MissingDays`, `CoveredRange` `api/pipeline/run/MissingDays.java:45`, `api/pipeline/run/CoveredRange.java:17` | `MissingDays.of` `api/pipeline/run/MissingDays.java:79` | `GET …/missing-days` `api/pipeline/api/PipelineRunController.java:154` | `missingRanges` `web/missingDays.ts:38` | „Dni bez danych" `web/views/AdminView.vue:336` (pl) |
| **Świeżość** | zapytanie `findNewestStoredRangeEnd` `api/pipeline/run/PipelineRunRepository.java:98` | `Freshness`, `SourceUpdate` `api/pipeline/api/FreshnessController.java:48,57` | `findLastUpdates` `api/pipeline/run/PipelineRunRepository.java:82` | `GET /api/freshness` `api/pipeline/api/FreshnessController.java:34` | `web/api/freshness.ts:4` | „Dane zaktualizowane:" `web/views/FeedView.vue:176` (pl) |
| **Bezpieczeństwo wdrożenia** | zapytanie `existsWithActivityWithin` `api/pipeline/run/PipelineRunRepository.java:64` | `DeploySafetyReason` `api/pipeline/api/DeploySafetyReason.java:13` | `existsLiveActive` `api/pipeline/run/PipelineRunRepository.java:52` | `GET /api/deploy-safety` `api/pipeline/api/DeploySafetyController.java:50` | — | — |

### Ocena (kontrakt modelu)

| concept | persistence | domain type | operations | entry points | UI | user-facing label (język) |
|---|---|---|---|---|---|---|
| **Ocena / próba oceny** | `assessments` `mig/V3__create_pipeline_and_posts.sql:130` | `Assessment` `api/posts/Assessment.java:55`; `AssessmentResult` `api/agents/assessor/AssessmentResult.java:26` | `Assessor.assess/prepare/evaluate` `api/agents/assessor/Assessor.java:118,71,88`; `PostTransitions.record` `api/pipeline/state/PostTransitions.java:91` | `AssessmentView` `api/posts/api/PostController.java:161` | `PostAssessment` `web/components/PostAssessment.vue:42` | „Ocena" `web/views/PostDetailView.vue:173`; „oceniono" `web/components/PostAssessment.vue:48` (pl) |
| **Wejście / wyjście oceny** | `input`, `output` JSONB `mig/V3__create_pipeline_and_posts.sql:136,139` | `AssessmentInputV1` `.../contract/AssessmentInputV1.java:13`; `AssessmentOutputV2` `.../contract/AssessmentOutputV2.java:16`; `Item`, `Context`, `Parent` | `AssessmentInputs.of` `api/pipeline/assess/AssessmentInputs.java:32` | — (tylko wyjście w `AssessmentView`) | `AssessmentOutput` `web/api/posts.ts:62` | — |
| **Istotność** | tylko w JSONB `output` | `Relevance` `.../contract/Relevance.java:10` | D15 `api/agents/assessor/validation/RuleId.java:57` | — | `web/api/posts.ts:10` | „inwestycyjny, nie inwestycyjny, niejasny" `web/labels.ts:4` (pl) |
| **Cel** | tylko w JSONB `output` | `Target`, `TargetKind`, `Anchor`, `Reference` `.../contract/Target.java:11`, `TargetKind.java:10` | `ClaimTargetsExistRule` `api/agents/assessor/validation/rules/ClaimTargetsExistRule.java:17` | parametr `kind` `api/posts/api/PostController.java:89` | `web/api/posts.ts:43` | „spółka, aktywo, sektor, makro i rynek, inne, nieokreślony" `web/labels.ts:32`; „Rodzaj celu" `web/components/FeedFilter.vue:60` (pl) |
| **Stanowisko** | tylko w JSONB `output` | `Stance`, `Direction`, `Assertiveness` `.../contract/Stance.java:13`, `Direction.java:10`; `StanceTable` `api/agents/assessor/validation/StanceTable.java:11` | `StanceAssertivenessRule` `.../validation/rules/StanceAssertivenessRule.java:15` | parametr `direction` `api/posts/api/PostController.java:90` | `StanceText` `web/components/StanceText.vue:18` | „wzrostowo, spadkowo, neutralnie, mieszanie, bez stanowiska, niejasno" `web/labels.ts:9`; „(ostrożnie)" `web/components/StanceText.vue:18` (pl) |
| **Teza (Claim)** | tylko w JSONB `output` | `Claim`, `ClaimKind`, `Attribution`, `Evidence` `.../contract/Claim.java:14`, `ClaimKind.java:10` | `EvidenceFromItemRule` `.../validation/rules/EvidenceFromItemRule.java:14` | — | `web/api/posts.ts:52` | „Tezy ogólne" `web/components/PostAssessment.vue:57`; `claimKind` `web/labels.ts:24` (pl) |
| **Flagi / modyfikatory** | tylko w JSONB `output` | `Flag`, `Modifier` `.../contract/Flag.java:10`, `Modifier.java:10` | `Prefilter.noContentOutput` `api/agents/assessor/Prefilter.java:41` | — | `web/api/posts.ts:20-21` | „Zastrzeżenia" `web/components/PostAssessment.vue:72`; `web/labels.ts:17,23` (pl) |
| **Prefiltr** | `result_source` `mig/V3__create_pipeline_and_posts.sql:142` | `Provenance.Source.PREFILTER` `api/agents/assessor/Provenance.java:50` | `Prefilter.isAssessable` `api/agents/assessor/Prefilter.java:35` | `resultSource` `api/posts/api/PostController.java:161` | `web/api/posts.ts:85` | „bez treści, bez oceny modelu" `web/components/PostCard.vue:28` (pl) |
| **Naruszenie / klasa błędu** | `failure_class`, `violations` `mig/V3__create_pipeline_and_posts.sql:135,140` | `FailureClass` `.../validation/FailureClass.java:11`; `RuleId` `.../validation/RuleId.java:12`; `Violation` `.../validation/Violation.java:16` | `RuleBasedValidator.validate` `.../validation/RuleBasedValidator.java:40`; `OutputParser.parse` `.../validation/OutputParser.java:68` | `LastAttempt` `api/posts/api/FailedPostController.java:98` | `web/api/posts.ts:101` | `failureClass` `web/labels.ts:63`, np. „zmyślony cytat" (pl) |
| **Pochodzenie oceny** | `provider`, `model`, `channel`, `service_tier`, `flex_fallback`, `prompt_version`, `contract_version` `mig/V3__create_pipeline_and_posts.sql:143-151` | `Provenance` `api/agents/assessor/Provenance.java:43`; `Provider`, `Channel`, `ServiceTier` | `LlmClient.complete` `api/llm/LlmClient.java:46` | `GET /api/admin/llm/check` `api/llm/api/LlmCheckController.java:25,43` | — | „ponowienia LLM", „powroty z flex" `web/views/RunDetailView.vue:77-78` (pl) |

### Dostęp, sygnały, operacje

| concept | persistence | domain type | operations | entry points | UI | user-facing label (język) |
|---|---|---|---|---|---|---|
| **Konto / użytkownik** | `users` `mig/V1__create_users.sql:3` | `User` `api/user/User.java:22`; `Role` `api/user/Role.java:15`; `SystemAccount` `api/user/SystemAccount.java:10` | `AccountDetailsService` `api/auth/AccountDetailsService.java:29`; `AdminBootstrap` `api/cli/AdminBootstrap.java:27` | `POST /api/auth/login` `api/auth/AuthController.java:77`; CLI `create-admin`, `create-user`, `reset-password` `api/cli/CommandType.java:11-17` | `currentUser` `web/auth/session.ts:10` | „Logowanie" `web/views/LoginView.vue:57`; „Panel admina" `web/components/AppHeader.vue:36` (pl) |
| **Link aktywacyjny** | `activation_tokens` `mig/V7__create_activation_tokens.sql:5` | `ActivationToken` `api/auth/activation/ActivationToken.java:19`; `IssuedLink` `.../IssuedLink.java:12`; `ActivationResult` `.../ActivationResult.java:4` | `ActivationLinks.createUser/resetPassword/lookup/activate` `api/auth/activation/ActivationLinks.java:79,111,132,148` | `POST /api/auth/activation`, `/lookup` `api/auth/activation/ActivationController.java:53,42` | `/activate` `web/router/index.ts:37` | „Ustawienie hasła" `web/views/ActivateView.vue:125`; „Link jest nieważny lub wygasł" `:128` (pl) |
| **Powiadomienie (w aplikacji)** | `notifications` `mig/V9__create_notifications.sql:6` | `Notification` `api/notifications/Notification.java:19`; `NotificationKind` `api/notifications/NotificationKind.java:9` | `RunNotifications.runEnded` `api/notifications/RunNotifications.java:68`; `notifyAdmins` `api/notifications/NotificationRepository.java:44` | `GET /api/admin/notifications` `api/notifications/api/NotificationController.java:37,53` | `NotificationBell` `web/components/NotificationBell.vue:95`, `NotificationMenu`; `AdminNotification` `web/api/notifications.ts:24` | „Powiadomienia" `web/components/NotificationMenu.vue:142`; „nowe" `:166` (pl) |
| **Alert operatora (poza aplikacją)** | — | `AlertEvent` `api/alerts/AlertEvent.java:8`; `OperatorAlerts.Delivery` `api/alerts/OperatorAlerts.java:18` | `OperatorAlerts.report` `:35`; `OperatorAlertQueue` `api/alerts/OperatorAlertQueue.java:53`; `HealthchecksAlerts` `api/alerts/HealthchecksAlerts.java:34` | CLI `send-test-alert` `api/cli/CommandType.java:21` | — (grep `operator|telegram|healthcheck` w `web/`: tylko komentarze o linku operatora) | — |
| **Kopia bazy** | klasyfikacja tabel `counted` / `present` `tools/db-backup/tables.sh:4,6,17` | — | `backup.sh` `tools/db-backup/backup.sh:3`; `restore-check.sh` `tools/db-backup/restore-check.sh:9`; `prune` `tools/db-backup/backup.sh:189` | zadanie cykliczne | — | — |

---

## Porównanie adapterów źródeł

Ten sam kontrakt (`SourceFetcher`, `RawInputMapper`, `SourcePost`) ma dwa komplety klas o równoległych nazwach: `<Source>ApiClient`, `…ApiException`, `…Configuration`, `…Failure`, `…Fetcher`, `…Item`, `…Mapper`, `…Posts`, `…Repository`, `…SearchResult`. Różnice w słownictwie: `evidence`.

| pojęcie wspólne | Wykop | Reddit |
|---|---|---|
| element źródła | `WykopItem` `api/sources/wykop/WykopItem.java:25`, „stream element" `:10` | `RedditItem` `api/sources/reddit/RedditItem.java:27`, „a Reddit post" `:8` |
| własny typ źródła | `resource` + enum `WykopResource` ENTRY/LINK `api/sources/wykop/WykopResource.java:9` | `isSelf` Boolean, bez enumu `api/sources/reddit/RedditItem.java:28` |
| tekst | `content` (entry), `description` (link) `api/sources/wykop/WykopMapper.java:53,58` | `selftext` dla obu `api/sources/reddit/RedditMapper.java:52` |
| tytuł | `null` dla entry `api/sources/wykop/WykopMapper.java:52` | zawsze z `title` `api/sources/reddit/RedditMapper.java:51` |
| czas publikacji | `created_at` lokalny warszawski `api/sources/wykop/WykopItem.java:15`, `WYKOP_ZONE` `api/sources/wykop/WykopMapper.java:30` | `created_utc` sekundy UTC `api/sources/reddit/RedditItem.java:22` |
| id | `Long id` `api/sources/wykop/WykopItem.java:25` | `String id` base-36 `api/sources/reddit/RedditItem.java:12` |
| kanał | stała `TAG = "gielda"` `api/sources/wykop/WykopPosts.java:24` | `subreddit` z każdego posta `api/sources/reddit/RedditPosts.java:50`; lista `REDDIT_SUBREDDITS` `api/sources/reddit/RedditConfiguration.java:35` |
| `SourcePost.tags` | tagi elementu `api/sources/wykop/WykopPosts.java:53` | `List.of(subreddit)` `api/sources/reddit/RedditPosts.java:54` |
| `SourcePost.slug` | `item.slug()` `api/sources/wykop/WykopPosts.java:52` | `null` `api/sources/reddit/RedditPosts.java:53` |
| metoda odczytu | `itemsAddedBetween(tag, from, to)` `api/sources/wykop/WykopRepository.java:122`; także `itemsOfDay` `:109` (w `api/` bez wywołań, użycie tylko w teście `WykopRepositoryTest.java:72`) | `postsCreatedBetween(from, to)` `api/sources/reddit/RedditRepository.java:112` |
| jednostka odczytu | zasób wewnętrzny `Resource` ENTRIES/LINKS `api/sources/wykop/WykopRepository.java:68-70` | subreddit `api/sources/reddit/RedditRepository.java:23` |
| usunięte | brak kategorii; `rg -i 'deleted|removed|hidden|usuni'` w `sources/wykop/`: tylko komentarze o spójności stron i sekrety | `removed()` `api/sources/reddit/RedditItem.java:42`, licznik `removed` `api/sources/reddit/RedditSearchResult.java:13`, `REMOVED`/`DELETED` `api/sources/reddit/RedditMapper.java:30,33`, `DELETED_AUTHOR` `api/sources/reddit/RedditPosts.java:24` |
| konfiguracja | `WYKOP_KEY`, `WYKOP_SECRET` `api/sources/wykop/WykopConfiguration.java:24,26` | `REDDIT_SUBREDDITS` `api/sources/reddit/RedditConfiguration.java:35` |
| limity | `PAGE_SIZE=50`, `MAX_PAGES=200` `api/sources/wykop/WykopRepository.java:48,51` | `PAGE_SIZE=100`, `MAX_PAGES=50` `api/sources/reddit/RedditRepository.java:47,50` |
| awaria | `WykopFailure` 7 wartości `api/sources/wykop/WykopFailure.java:4` | `RedditFailure` te same 7 nazw `api/sources/reddit/RedditFailure.java:4` |

**Ta sama nazwa, inne znaczenie między adapterami:**

- `link` — Wykop: `/** A found link → {@code link}. */` `api/sources/wykop/WykopResource.java:14`, tytuł + opis. Reddit: każdy post nie-self, `ItemType type = Boolean.TRUE.equals(item.isSelf()) ? ItemType.POST : ItemType.LINK;` `api/sources/reddit/RedditMapper.java:50`, z `selftext` jako treścią. `evidence`
- `post` — Wykop: tylko mikrowpis (`/** A microblog entry → {@code post}. */` `api/sources/wykop/WykopResource.java:11`). Reddit: `RedditItem` nazywa postem wszystko, także link post (`:8`, `:17`), a typ `post` dostaje tylko self-post. `evidence`
- `resource` w `WykopRepository` to jednocześnie pole elementu i prywatny enum ze ścieżką (`:67-70`); `WykopResource` to osobny publiczny enum tych samych dwóch rzeczy. Dwa enumy jednego pojęcia. `evidence`
- `element` — `RedditSearchResult`: „The posts a fetch returned … how many elements could not be read" (`:9`) i `elements()`; w Wykopie `element` jest słowem głównym. Reddit miesza `post` i `element` dla tej samej rzeczy. `comment` + `evidence` (`RedditSearchResult.Element` `:23`).
- `removed` — `RedditItem.removed()` obejmuje też usunięcie przez autora (`[deleted]`, `:43`), podczas gdy `RedditMapper.REMOVED` to tylko `[removed]` (`:30`). `evidence`
- `[deleted]` — ta sama wartość w dwóch polach i znaczeniach: post skasowany (`selftext`, `RedditMapper.java:33`) i skasowane konto (`author`, `RedditPosts.java:24`). `evidence`

**Duplikaty między adapterami** (`evidence`): `WykopFailure` i `RedditFailure` mają identyczne wartości, a 4 z nich powielają `HttpRetryException.Kind` (`api/sources/transport/HttpRetryException.java:14`: HTTP_STATUS, TRANSPORT, DEADLINE_EXCEEDED, INTERRUPTED). `textOrNull` jest skopiowane (`WykopMapper.java:83`, `RedditMapper.java:77`). `Fetcher.fetch` ma tę samą pętlę (`WykopFetcher.java:47-56`, `RedditFetcher.java:47-56`). Rekord `Element(item, raw)` ma tę samą postać w obu (`WykopSearchResult.java:22`, `RedditSearchResult.java:23`).

### Nazwy po jednym źródle we wspólnych miejscach

| # | miejsce | co | status |
|---|---|---|---|
| 1 | `web/labels.ts:3` | `itemType: { post: 'wpis', link: 'znalezisko', comment: 'komentarz' }` — wspólna etykieta; wyświetlana każdemu postowi (`web/components/PostCard.vue:26`) i wchodzi do tytułu („Wpis użytkownika X", `web/views/PostDetailView.vue:38-40`). Self-post z Reddita dostaje „wpis", link post „znalezisko". „Znalezisko" i „wpis" to słowa Wykopu: adres `/wpis/` `api/sources/wykop/WykopItem.java:34`, „found link" `WykopResource.java:14`. | `evidence` dla użycia; `inference`: że to słownictwo jednego serwisu |
| 2 | `api/agents/assessor/prompt/AssessmentPrompt.java:39` + `resources/assessor/system-prompt.md:21,37` | Wspólny prompt `v2.5` wymienia „Tagi kanału (#gielda, #gieldowegownowpisy, #finanse, #inwestowanie itp.)" i „Na #gielda częsta jest ironiczna pochwała własnej straty". Żadnej reguły o subreddicie; grep `wykop|reddit|subreddit` w tym pliku: brak trafień. | `evidence`; `inference`: posty Reddita idą przez prompt strojony na Wykop |
| 3 | `api/posts/Post.java:83-85`, `:78`, `mig/V3__create_pipeline_and_posts.sql:93,95` | `tags` i `slug` to pojęcia Wykopu we wspólnym modelu. Reddit wstawia do `tags` subreddit, a do `slug` `null`, choć jego permalink ma slug (`RedditItem.java:19`). Dokumentacja zmiękcza: „the source's tags or channels of the post" `api/sources/SourcePost.java:24`. | `evidence` |
| 4 | `api/agents/assessor/Prefilter.java:27-29` | Wycina `#tag` i `@mention`; nie zna `r/…` ani `u/…`. Składnia dobrana pod jedną platformę. | `evidence` dla wzorców; `inference` dla pochodzenia |
| 5 | `api/agents/assessor/Assessor.java:31-32`, `Prefilter.java:14`, `AssessmentResult.java:15`, `Preparation.java:6`, `PreparedRequest.java:8` | Wspólny asesor nazywa jednostkę oceny `entry` — nazwą zasobu Wykopu (`WykopResource.ENTRY`). Kod nazywa ją `item`/`AssessmentInputV1`. | `comment` |
| 6 | `mig/V3__create_pipeline_and_posts.sql:5` | „a date range of Wykop (range)" — nieaktualne po `mig/V5`; migracji się nie edytuje. | `comment` |
| 7 | `api/pipeline/run/RunKind.java:8` | „a date range of the source" — liczba pojedyncza, przy `sources[]` od V5. | `comment` |
| 8 | `api/raw/RawPayloadStore.java:11` | „In S-02 a Wykop payload is the element as Jackson wrote it". | `comment` |
| 9 | `api/config/ErrorCode.java:59`, `api/sources/SourceFetcher.java:16` | Przykład „(e.g. no keys)", „its keys are set"; Reddit nie ma kluczy (`RedditApiException.java:8`). | `comment` |
| 10 | `api/pipeline/PipelineRunService.java:97` vs `api/sources/wykop/WykopMapper.java:30` | Oba `ZoneId.of("Europe/Warsaw")`: `ZONE` (kalendarz zakresów i dni wszystkich źródeł) oraz `WYKOP_ZONE`. `WYKOP_ZONE` nie wycieka poza `sources/wykop/`, ale doba zakresu Reddita też jest warszawska, a `RedditFetcher` przyjmuje `Instant`. Nazwy są poprawne. | `evidence`; to obserwacja, nie wada |

Zweryfikowano negatywnie: `rg -i 'wykop|gielda|reddit|subreddit|arctic'` po `api/` poza `sources/wykop/`, `sources/reddit/` i plikami `.md` daje poza `sources/Source.java` tylko komentarze (`alerts/HealthchecksAlerts.java:31`, `raw/RawPayloadStore.java:11`, `sources/transport/HttpRetry.java:24,81`, `settings/InvalidSettingsException.java:11`, `sources/SourcePost.java:17`, `posts/Post.java:62-63`, `posts/api/PostController.java:137-138`). Żaden kod `pipeline/` nie nazywa źródła. W `web/` poza próbkami kontraktu i typami-komentarzami nazwy źródeł mają tylko rejestry `web/labels.ts:72,81` i `web/contract/rules.json:31`. Prompt i schemat wyjścia: `rg -i 'wykop|reddit|subreddit'` bez trafień (prompt mówi `#gielda`, ale nie „Wykop"). `evidence` w tym zakresie.

---

## One concept, many names

| pojęcie | nazwy | cytaty | status |
|---|---|---|---|
| wypowiedź/element ze źródła | `post`, `item`, `element`, `entry`, „statement", „wypowiedź" | `Post` `api/posts/Post.java:37`; `Item` `.../contract/Item.java:15` („the statement being assessed" `:11`); `WykopSearchResult.Element`; `Assessor.java:31` „one entry"; prompt „jedną wypowiedź" `resources/assessor/system-prompt.md:1` | `evidence`; `comment` dla `entry` |
| przebieg | `pipeline run`, `run`, „przebieg" | `PipelineRun` `api/pipeline/run/PipelineRun.java:33`; `/admin/runs/:id` `web/router/index.ts:41`; „Nowy przebieg" `web/views/AdminView.vue:296` | `evidence` |
| wynik źródła w przebiegu | `PipelineRunSource`, `SourceFetch`, `SourceResultView`, `SourceResult`, `sourceResults`, `outcome`, `sourceStatus` (`rules.json`, `labels.ts`) ↔ `SourceFetchStatus` (Java, TS) | `api/pipeline/run/PipelineRunSource.java:26`; `api/pipeline/flow/RunFlow.java:81`; `api/pipeline/api/PipelineRunController.java:278`; `web/api/pipeline.ts:47`; `api/pipeline/step/SourceIngest.java:102`; `web/contract/rules.json:32`; `web/api/pipeline.ts:34` | `evidence` |
| krok jako wiersz | Java `PipelineRunStep` + `StepView`; TS `RunStep`. Nazwa kroku: Java `RunStep`; TS `RunStepName` | `api/pipeline/run/PipelineRunStep.java:21`; `api/pipeline/api/PipelineRunController.java:289`; `web/api/pipeline.ts:36`; `api/pipeline/run/RunStep.java:8`; `web/api/pipeline.ts:8` | `evidence` |
| tekst filtra feedu | `q` (route, TS), `quote` (Java), „Spółka lub temat" (UI), `anchor.quote` (JSON) | `api/posts/api/PostController.java:88`; `api/posts/PostFilter.java:18`; `web/components/FeedFilter.vue:56`; `api/posts/PostRepository.java:116` | `evidence` |
| kto zażądał przebiegu | `requested_by`, `requester`, `scheduled`, `SCHEDULER`, „cron", `daily`, `pipeline-trigger` | `mig/V3__create_pipeline_and_posts.sql:14`; `web/runs.ts:137`; `api/pipeline/run/PipelineRun.java:295`; `api/user/SystemAccount.java:13`; `api/pipeline/api/InternalPipelineRunController.java:65`; `api/internal/PipelineTriggerToken.java:20` | `evidence` |
| przebieg bez wykonawcy | `dead` (DeploySafety), `lost` (cause), `orphan` (`interruptOrphans`, `ORPHAN_CLEANUP_FAILED`, `Orphans`), `stalled` (`stopStalled`, `stall`) — `stall` dotyczy przebiegu własnej instancji, reszta obcej | `api/pipeline/api/DeploySafetyController.java:25`; `api/pipeline/run/InterruptionCause.java:28`; `api/pipeline/state/RunRecorder.java:325`; `api/alerts/AlertEvent.java:32`; `api/pipeline/PipelineRunService.java:461` | `evidence`; podział własna/obca instancja `inference` z `:246` |
| zwrot posta | `release`, `releaseFailed`, `releaseAll`, `releaseOrphans`, „put back" | `api/pipeline/state/PostTransitions.java:118,131,161,172`; `api/pipeline/PipelineStartupRecovery.java:94` | `evidence`; „put back" `comment` |
| konto | `user`/`users`/`User`, `account`/`AccountDetailsService`/`SystemAccount`/`NO_ACCOUNT` | `mig/V1__create_users.sql:3`; `api/auth/AccountDetailsService.java:29`; `api/auth/activation/IssueOutcome.java:20` | `evidence` |
| administrator | `admin`, `administrator`, „Panel admina", „Poproś administratora" | `api/user/Role.java:15`; `api/cli/CommandType.java:11`; `web/views/ActivateView.vue:128` | `evidence` |
| link aktywacyjny / reset hasła | `activation` obejmuje też reset: `resetPassword` wystawia „activation link" | `api/cli/CommandType.java:18`; `api/auth/activation/ActivationLinks.java:111` | `evidence` |
| strefa Warszawy | `ZONE`, `WYKOP_ZONE` | tabela wyżej, #10 | `evidence` |
| kanał | `Item.channels` (lista), `SourcePost.channel`, `posts.channel` (jedna wartość), `tag`, `subreddit` | `.../contract/Item.java:16`; `api/sources/SourcePost.java:27`; `mig/V13__posts_channel.sql:1-3` (`comment`: `channels[0]`) | `evidence` |

**Etykieta „ponowienie" nie odpowiada `RunKind.RETRY`** (`evidence`): `RunKind.RETRY` ma etykietę „ponowienie resztek" (`web/labels.ts:46`), ale przycisk „Ponów zaznaczone" (`web/views/AdminView.vue:451`), funkcja `retryPosts` (`web/api/pipeline.ts:125`) i komunikat „Ponowienie N post(ów) w kolejce" (`web/views/AdminView.vue:266`) wysyłają `{ postIds }` i tworzą przebieg `POSTS` (`api/pipeline/PipelineRunService.java:202`), oznaczany „wybrane posty".

**Etykieta „teza" jest szersza i węższa jednocześnie** (`evidence`): UI nazywa każdy `Claim` tezą („Tezy ogólne" `web/components/PostAssessment.vue:57`, „Model nie wskazał celów ani tez" `:69`), a `claimKind.investment_thesis` to „teza inwestycyjna", jeden z pięciu rodzajów (`web/labels.ts:27`).

---

## One name, many concepts

| nazwa | znaczenia | status |
|---|---|---|
| **`channel`** | (1) kanał posta w źródle, tekst otwarty: `posts.channel` `mig/V13__posts_channel.sql:10`. (2) trasa wywołania LLM: `assessments.channel`, `Channel` `api/llm/Channel.java:10`, `/** The route a call took */` `:6`, `Provenance.channel` „the route inside the provider" `api/agents/assessor/Provenance.java:29`. (3) kanał alertów: „test of the operator alert channel" `api/alerts/AlertEvent.java:11`. Encje `Post.channel` (`String`) i `Assessment.channel` (`Channel`) mają to samo pole, różne typy (`api/posts/Post.java:67`, `api/posts/Assessment.java:104`). | `evidence` |
| **`source`** | (1) dostawca danych `Source` `api/sources/Source.java:9`. (2) „What produced the output" `Provenance.Source` MODEL/PREFILTER `api/agents/assessor/Provenance.java:49-50`, kolumna `result_source`. (3) `HttpRetry source` — nazwa w komunikatach, `String` `api/sources/transport/HttpRetry.java:81`. `Assessment.java` importuje (2), `Post.java` importuje (1). | `evidence` |
| **`sources`** (przebieg) | `pipeline_runs.sources` = żądane `mig/V5__pipeline_run_sources.sql:6`; `pipeline_run_sources` = wyniki `mig/V12__pipeline_run_sources.sql:33`. Pliki migracji V5 i V12 mają identyczną końcówkę nazwy `pipeline_run_sources`, a V5 tej tabeli nie tworzy. `MissingDays.Day.sources` = źródła bez danych `api/pipeline/run/MissingDays.java:102`; `Freshness.sources` = źródła z aktualizacją `api/pipeline/api/FreshnessController.java:46`. | `evidence` |
| **`attempt`** | (1) wiersz `assessments` = „One assessment attempt of a post" `api/posts/Assessment.java:35`, także `ok`, `LastAttempt` `api/posts/api/FailedPostController.java:98`. (2) `posts.assessment_attempts` liczy tylko nieudane: „an attempt counts only when a failed assessment was stored" `mig/V3__create_pipeline_and_posts.sql:81-82` (`comment`), `Outcome.after` `api/pipeline/state/PostTransitions.java:203-208` (`evidence`). (3) `assessments.attempts` = „HTTP attempts made" `api/agents/assessor/Provenance.java:41`. (4) `HttpRetry.MAX_ATTEMPTS` `api/sources/transport/HttpRetry.java:42`. (5) próba logowania `TOO_MANY_ATTEMPTS` `api/config/ErrorCode.java:33`. Sens (1) i (2) się rozjeżdża: post `assessed` ma więcej wierszy prób niż `assessment_attempts`. | `evidence`; ostatnie zdanie `inference` z `ck_posts_stage_attempts` |
| **`failed`** | `RunStatus.FAILED` (koniec przebiegu) `api/pipeline/run/RunStatus.java:13`; `StepStatus.FAILED`; `SourceFetchStatus.FAILED` (źródło rzuciło) `:14`; `PostStage.FAILED` (1–2 próby, **do ponowienia**) `api/posts/PostStage.java:16`; `AssessmentResult.Status.FAILED` `:32`; `AttentionReason.FAILED`; `ExitCode.FAILED` `api/cli/ExitCode.java:17`; `OperatorAlerts.Delivery.FAILED` `:24`. Lista `/api/admin/posts/failed` obejmuje też `dead` i `stored` wstrzymane (`api/posts/api/FailedPostController.java:30`). UI: „błąd" dla przebiegu (`web/labels.ts:52`), „Nieudane posty" dla listy (`web/views/AdminView.vue:414`). | `evidence` |
| **`dead`** | post: `PostStage.DEAD`, `pipeline_runs.dead`, `DEAD_POSTS`, `POST_DEAD`, UI „martwy" `web/views/AdminView.vue:431`. Przebieg: „is dead: no instance executes it" `api/pipeline/api/DeploySafetyController.java:25`, bez typu, tylko `existsLiveActive`. | `evidence`; sens przebiegu `comment` + nazwa metody |
| **`interrupted`** | stan przebiegu i kroku; `WykopFailure.INTERRUPTED`/`RedditFailure.INTERRUPTED`/`HttpRetryException.Kind.INTERRUPTED` = przerwany wątek, `Thread.interrupted()` `api/notifications/RunNotifications.java:78`. | `evidence` |
| **`link`** | `ItemType.LINK`; link aktywacyjny (`ActivationLinks`, `IssuedLink`); `webUrl` jako „its original's link" `api/posts/api/PostController.java:46` (`comment`). | `evidence` |
| **`post`** | encja każdego typu `api/posts/Post.java:37`; `ItemType.POST` (obok `LINK`); `RunKind.POSTS` (wybrane); `Document.PARENT_POST` `.../contract/Document.java:10`; „posts" w `/api/posts` = tylko ocenione `api/posts/api/PostController.java:124-127`. W TS `PostItem` to pozycja feedu, a `PostStage` to podzbiór 3 z 5 wartości Javy: `web/api/posts.ts:98` vs `api/posts/PostStage.java:16`. | `evidence` |
| **`item`** | kontraktowy `Item`; `ItemType`; `WykopItem`/`RedditItem` (pola surowe); `PostItem`, `NotificationItem` (wiersz widoku); `RunPage.items` (strona); `pipeline_run_steps.items_in/out`; `source_item_id`. | `evidence` |
| **`target`** | `Target` kontraktu `.../contract/Target.java:11`; `RangeTarget`, `SelectionTarget`, `fromTargets` (zakres pracy przebiegu) `api/pipeline/flow/RunFlow.java:55,64`, `api/pipeline/step/AssessmentSelection.java:53`; „a link post's target" (URL) `api/sources/reddit/RedditItem.java:18`. | `evidence` |
| **`reference`** | `Reference` = jak nazwano cel (`explicit…`) `.../contract/Reference.java:10`; `FailureClass.INVALID_REFERENCE` = „Broken identifiers or references (D1–D5)" `.../validation/FailureClass.java:25-26`; `NoRepeatedReferencesRule`; `RawPayloadRef`. | `evidence` |
| **`retry`** | `RunKind.RETRY`; `llm_retries` (HTTP) `mig/V8__pipeline_run_progress.sql:11`; `HttpRetry`, `TransportRetry`; `web/api/pipeline.ts:125` `retryPosts` (kind POSTS); `retry()` w widokach = ponowne wczytanie `web/views/RunDetailView.vue:138`. | `evidence` |
| **`model`** | model LLM `Provenance.model`; `Provenance.Source.MODEL`; „the shared model" `api/sources/SourcePost.java:12,15`, `api/posts/Post.java:29`. Ten ostatni nie ma klasy. | `evidence` |
| **`lost`** | `InterruptionCause.LOST`; `RUN_COUNTERS_LOST` (liczniki) `api/alerts/AlertEvent.java:36`; `LostCounts` `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:475`. | `evidence` |
| **`stage`** | `PostStage`; „Future ML stages" = kroki `api/pipeline/flow/RunFlow.java:29`. | `comment` dla drugiego |
| **`fetched`** | liczba **elementów**, nie postów: „every element the source returned" `api/sources/SourceFetcher.java:32`; TS: „its posts are `fetched − skipped`" `web/api/pipeline.ts:50`. UI: „pobrane" `web/views/RunDetailView.vue:70`. Reddit wyłącza usunięte z tej liczby (`RedditFetcher.java:18-20`, `comment`). | `evidence` |
| **`użytkownik`** (UI) | „użytkownika {author}" = autor posta, nie konto `web/views/PostDetailView.vue:40`. | `evidence` |

---

## Implicit concepts

| pojęcie | gdzie żyje | status |
|---|---|---|
| **Wstrzymany post (on hold)** | Bez stanu. „has no stage of its own" `api/posts/PostStage.java:10-11`; `technical_failures` `mig/V10__posts_technical_failures.sql:9`; próg `Post.MAX_TECHNICAL_FAILURES` `api/posts/Post.java:46`; obliczane w `FailedPost.of` `api/posts/api/FailedPostController.java:92` i w predykatach `findLeftoverIds`; UI „wstrzymany" `web/views/AdminView.vue:432`. | `evidence` |
| **Resztki (leftovers)** | Predykat: `findIdsInStageOrFailedBelow` `api/posts/PostRepository.java:43-45`. Nazwa w `RunKind` to `RETRY`, nie „leftover". | `evidence` |
| **Feed** | Bez typu ani tabeli: reguła `stage = assessed` (`api/posts/api/PostController.java:124-127`) + najnowsza ocena `ok`. | `evidence` |
| **Najnowsza ocena** | Reguła tylko w SQL: `DISTINCT ON`, `api/posts/AssessmentRepository.java:29,33`; `ORDER BY a.assessed_at DESC, a.id DESC` `api/posts/PostRepository.java:113`. | `evidence` |
| **Cel, teza, stanowisko jako dane** | Tylko JSONB `assessments.output`; brak tabel. Filtr przez `jsonb_array_elements` `api/posts/PostRepository.java:114`. Brak bytu spółki/instrumentu: dopasowanie to `ILIKE` po `anchor.quote` `:116`. | `evidence` |
| **Dzienne uruchomienie** | Brak `RunKind`; to RANGE z `requested_by = SCHEDULER`, rozpoznawane przez `scheduled()` `api/pipeline/run/PipelineRun.java:295`. | `evidence` |
| **Martwy/osierocony przebieg** | Bez stanu; obliczane z `COALESCE(last_activity_at, started_at)` `api/pipeline/state/RunRecorder.java:273`. | `evidence` |
| **Pokrycie dnia** | Bez tabeli: `STORED_RANGE_STATUSES` + `SourceFetchStatus.SUCCEEDED`; `api/pipeline/run/PipelineRunRepository.java:29,98`; `api/pipeline/run/MissingDays.java:111`. | `evidence` |
| **Operator** | Bez typu, roli i konta: 40 plików `api/` zawiera to słowo, jedyne identyfikatory to `OperatorAlerts`, `OperatorAlertQueue`, `NoOperatorAlerts`. `Role` ma tylko USER, ADMIN `api/user/Role.java:15`. Czy operator to ta sama osoba co admin: `unknown` w kodzie. | `evidence`, plus `unknown` |
| **Instancja / wdrożenie** | Luźny `deployment_id` `mig/V8__pipeline_run_progress.sql:13` i pamięciowe `ownRuns` `api/pipeline/PipelineRunService.java:306`. | `evidence` |
| **Autor** | Tylko `posts.author` `TEXT` `mig/V3__create_pipeline_and_posts.sql:92`; brak tożsamości autora. | `evidence` |
| **Wspólny model** | Słowo z Javadoców („the shared model"), bez klasy; faktycznie `SourcePost` + `ItemType`. | `comment` + `inference` |
| **Pominięty element** | Licznik `skipped`, bez wiersza: „their raw payload is not stored" `api/sources/SourceFetcher.java:33-34`. | `evidence` |
| **Usunięty post Reddita** | Tylko int `removed` i linia logu `api/sources/reddit/RedditRepository.java:126`; nie trafia do `SourceFetcher.Fetched` `api/sources/SourceFetcher.java:36`. | `evidence` |
| **Treść błędu** | Tekst `<SimpleName>: <message>` w `error` `api/pipeline/step/SourceIngest.java:128`. Sens tekstu czytany przez `LIKE '%gave no post to store%'` `mig/V12__pipeline_run_sources.sql:57` (tylko backfill). | `evidence` |

**Kolumny bez czytelnika** (`evidence`, zakres: `api/`, poza `*Test`/`*IT`, wyszukane `getTags|getSlug|getContentHash|contentHash|content_hash|\.tags\(\)|\.slug\(\)|p\.tags|p\.slug`): `posts.tags`, `posts.slug` i `posts.content_hash` są zapisywane (`api/pipeline/step/PostStore.java:92`, `api/posts/Post.java:150`), a gettery nie mają wywołań. `content_hash`: „to notice an edited post" `mig/V3__create_pipeline_and_posts.sql:97` (`comment`) — logiki wykrywania edycji nie znaleziono w tym zakresie. Testów nie przeszukano.

**`FailedPost` nie ma `source` ani `channel`** (`evidence`): `api/posts/api/FailedPostController.java:83`, `web/contract/failed-post.json` (klucze), w przeciwieństwie do `PostItem` i `web/contract/post.json`.

---

## States and transitions

Wszystkie zapisy to warunkowe `UPDATE`; nie znaleziono maszyny stanów jako typu. `evidence`.

**`RunStatus`** `api/pipeline/run/RunStatus.java:13`: `queued, running, succeeded, partial, failed, interrupted`; `CHECK` `mig/V3__create_pipeline_and_posts.sql:30`; etykiety `web/labels.ts:47`.

| przejście | gdzie |
|---|---|
| → queued | konstruktor `api/pipeline/run/PipelineRun.java:139` |
| queued → running | `RunRecorder.start` `api/pipeline/state/RunRecorder.java:64` |
| running → succeeded/partial/failed | `finish` `:81`; status: `AssessedBatch.status` `api/pipeline/flow/RunFlow.java:199-204`, korekta na `partial` `api/pipeline/flow/RunSequence.java:170-172` |
| queued/running → failed | `fail` `api/pipeline/state/RunRecorder.java:98` |
| running → failed (zatrzymany) | `stall` `:272` |
| → interrupted | `interrupt` `:252`, `interruptAll` `:291`, `interruptOrphans` `:327` |

Cztery miejsca czytają zbiór statusów niezależnie: `attentionReasons` (Java), `classify` `tools/pipeline-trigger/trigger.sh:206-213`, `STORED_RANGE_STATUSES` `api/pipeline/run/PipelineRunRepository.java:29`, oraz `isActive` `web/runs.ts:132`.

**`StepStatus`** `api/pipeline/run/StepStatus.java:13`: `running, succeeded, failed, interrupted`. Otwarcie `api/pipeline/state/RunRecorder.java:348-350`; zamknięcie `:366`; zamknięcie otwartych `:389`.

**`PostStage`** `api/posts/PostStage.java:16`: `stored, assessing, assessed, failed, dead`.

| przejście | gdzie |
|---|---|
| → stored | konstruktor `api/posts/Post.java:151` |
| stored/failed → assessing | `claim` `api/pipeline/state/PostTransitions.java:72-74` |
| assessing → assessed/failed/dead | `record` `:95`; decyzja `Outcome.after` `:203-208` |
| assessing → stored/failed | `RELEASE_SET` `:42` (`release` `:118`, `releaseFailed` `:131`, `releaseAll` `:161`, `releaseOrphans` `:172`) |
| bez zmiany stanu, +1 błąd techniczny | `countTechnicalFailure` `:150` |

**`SourceFetchStatus`** `api/pipeline/run/SourceFetchStatus.java:14`: `succeeded, empty, failed`. Wybór `api/pipeline/step/SourceIngest.java:114,127`; zapis jednorazowy `api/pipeline/state/RunRecorder.java:146`. Bez przejść.

**`AssessmentResult.Status`** `api/agents/assessor/AssessmentResult.java:32`: `ok, failed`. Niezmienne po zapisie (`updatable = false`, `api/posts/Assessment.java:70-71`).

**`InterruptionCause`** `api/pipeline/run/InterruptionCause.java:12-28`: `deploy, restart, shutdown, lost`; wyłącznie przy `interrupted` (`ck_pipeline_runs_interruption_status` `mig/V8__pipeline_run_progress.sql:17`).

**`AttentionReason`** `api/pipeline/run/AttentionReason.java:14-42`: nieutrwalane („nothing stores it" `:7`).

**Powiadomienie:** nieprzeczytane → przeczytane: `read_at` `api/notifications/NotificationRepository.java:64,75`.

**Konto:** `password_hash` NULL ↔ ustawione: `User.setPassword/clearPassword` `api/user/User.java:92,100`; `login_enabled` tylko migracją `mig/V6__users_login_enabled_scheduler.sql:8`.

**Link aktywacyjny:** nowy zastępuje wiersz, użyty kasuje: `mig/V7__create_activation_tokens.sql:2-3` (`comment`); wynik `ActivationResult` `api/auth/activation/ActivationResult.java:7-13`.

**Zadania cykliczne:** `trigger.sh` mapuje statusy na `ok|failed|interrupted|waiting|active` `tools/pipeline-trigger/trigger.sh:206-213`. `ExitCode` `api/cli/ExitCode.java:11-25`.

**Sekwencja kroków** `api/pipeline/flow/RunSequence.java:37-38` (`comment`): `RANGE: fetch → store → select → assess → summarize`; `RETRY / POSTS: select → assess → summarize`.

---

## Where business logic lives

Osobnej warstwy domenowej brak; `0-sources.md` to potwierdza. Logika jest rozłożona między rekordy-niezmienniki, serwisy i bazę.

| pojęcie | warstwa | cytaty |
|---|---|---|
| Niezmienniki oceny (D1–D15) | serwis/strategie + tabela jako enum | `api/agents/assessor/validation/ContractV2Rules.java:30`, `RuleId.java:12`, `StanceTable.java:11` |
| Poprawność wejścia modelu | typ domenowy (konstruktory rekordów) | `.../contract/AssessmentInputV1.java:18`, `Item.java:19`, `TextFields.java:16` |
| Spójność wyniku oceny | typ domenowy + baza | `api/agents/assessor/AssessmentResult.java:41-50`, `ck_assessments_outcome` `mig/V3__create_pipeline_and_posts.sql:165` |
| Reguła „wymaga uwagi" | typ domenowy, jedyna prawdziwa metoda domenowa encji | `api/pipeline/run/PipelineRun.java:316-347` |
| Status końcowy przebiegu | rekordy przepływu | `api/pipeline/flow/RunFlow.java:199-204`, `api/pipeline/flow/RunSequence.java:170-172` |
| Przejścia stanów posta i przebiegu | warstwa zapisu, surowy SQL obok JPA | `api/pipeline/state/PostTransitions.java`, `api/pipeline/state/RunRecorder.java` |
| Stany posta (reguła progu prób) | **dwa miejsca**: Java i baza | `Outcome.after` `api/pipeline/state/PostTransitions.java:203-208`; `ck_posts_stage_attempts` `mig/V3__create_pipeline_and_posts.sql:116-121` |
| Jeden przebieg naraz | baza | `uq_pipeline_runs_one_running` `mig/V3__create_pipeline_and_posts.sql:39` |
| Kolejka i odmowy | serwis | `api/pipeline/PipelineRunService.java:216`, `PipelineBusyException`, `PostDeadException` |
| Izolacja źródeł | serwis kroku | `api/pipeline/step/SourceIngest.java:95-139` |
| Kształt danych po przebiegu | baza | `ck_pipeline_runs_range` `mig/V4__posts_shared_item_type.sql:19`, `ck_pipeline_run_sources_counts/error` `mig/V12__pipeline_run_sources.sql:46-47` |
| Filtr feedu i „najnowsza ocena" | repozytorium (SQL w Javie) | `api/posts/PostRepository.java:112-118`, `api/posts/AssessmentRepository.java:29-35` |
| Dostęp do posta (tylko assessed) | handler HTTP | `api/posts/api/PostController.java:124-127` |
| `onHold` | handler HTTP (DTO) | `api/posts/api/FailedPostController.java:92` |
| Pokrycie dni | typ domenowy + zapytania | `api/pipeline/run/MissingDays.java:79`, `api/pipeline/run/PipelineRunRepository.java:108` |
| Mapowanie źródła na model wspólny | adaptery | `api/sources/wykop/WykopMapper.java:42`, `api/sources/reddit/RedditMapper.java:43` |
| Tekst przyczyn, tony, zakresy dni | UI | `web/runs.ts:38,93`, `web/missingDays.ts:38`; przyczyny liczy API (`web/runs.ts:3`, `comment`) |
| Dostęp ról | konfiguracja | `api/config/SecurityConfig.java:101-106`; `web/router/index.ts:68` |
| Wiek przebiegu a wdrożenie | zapytanie + kontroler | `api/pipeline/run/PipelineRunRepository.java:52-64`, `api/pipeline/api/DeploySafetyController.java:64-70` |
| Rejestr reguł wspólnych API↔SPA | plik kontraktu | `web/contract/rules.json:1`; `api/config/ErrorCode.java:12` (`comment`) |

Triggerów, funkcji, polityk i widoków w bazie brak: `rg -i 'TRIGGER|CREATE (OR REPLACE )?FUNCTION|POLICY|CREATE VIEW'` po `mig/` (13 plików) bez trafień. `evidence`

---

## Unknowns

1. Czy `posts.tags`, `posts.slug` i `posts.content_hash` są czytane przez testy lub narzędzia pomiarowe; przeszukano tylko `api/` (kod główny). Czy wykrywanie edycji jest planowane: `unknown`.
2. Czy operator i admin to w praktyce jedna osoba: kod tego nie rozstrzyga.
3. Czy prompt `v2.5` działa na postach Reddita gorzej niż na Wykopie: kod zawiera nazwy, ale nie miary. Pomiarów nie czytano (`measurement/` poza zakresem nazw).
4. Czy `Prefilter` powinien znać `r/…`, `u/…`: wynika z produktu, nie z kodu.
5. `ItemType.COMMENT`, `Parent`, `parent_post`, `parent_comment`, `Context`: zdefiniowane, a „In the MVP both parents are `null`" `.../contract/Context.java:6`; `ItemType` „In the MVP only `post` and `link` are assessed" `.../contract/ItemType.java:7` (`comment`). Oba adaptery używają `Context.NONE` i nie tworzą `COMMENT` (`WykopMapper.java:65`, `RedditMapper.java:56-57`). Czy to trwałe: `unknown`.
6. `flex_fallback`, `ServiceTier`: pojęcia dostawcy LLM widoczne w UI („powroty z flex"); czy dotyczą domeny, czy tylko eksploatacji: `unknown`.
7. `ClaimKind.TRADE_ACTION`, `Modifier.PROMOTION`: znane z enumów i etykiet, bez zasad w kodzie poza promptem; ciała promptu nie analizowano poza grepem.
8. Znaczenie `FR-…`, `S-…`, `contract-v2.md §…` cytowanych w komentarzach: odsyłacze do dokumentów, celowo niesprawdzane.

---

## Commands

- `find` po `api/`, `web/`, `tools/`; `cat -n` i `sed -n` na migracjach V1–V13 oraz wymienionych klasach.
- `rg -n` po `api/` i `web/`, m.in.: `Mapping\(`, `@GetMapping$`, `record [A-Z]…(View|Request|Response|Item|Page)`, `Wykop|wykop|gielda|Reddit|reddit|subreddit|Arctic` (`-i`), `getTags|getSlug|getContentHash|contentHash|content_hash`, `class .*Rule|RuleId\.`, `WYKOP_ZONE|Europe/Warsaw|\bZONE\b`, `itemsOfDay|Media`, `\bentr(y|ies)\b`, `public .*evaluate|classif`, `TRIGGER|FUNCTION|POLICY|CREATE VIEW` (`-i`, `mig/`), `@Table\(`, `operator|telegram|healthcheck` (`-i`, `web/`), `requestMatchers|permitAll|hasRole`.
- `rg -n` po `tools/pipeline-trigger/trigger.sh`, `tools/db-backup/*.sh` i `szczur-radar-web/dev/mock-api.js`.
- `python3 -I` do wypisania kluczy `web/contract/{run,post,failed-post,freshness}.json`.
- `rg -c` słów (`entry`, `statement`, `element`, `item`, `classif`, `evaluate`, `stalled`, `orphan`, `leftover`, `on hold`, `dead`) po `api/*.java`.
- `git rev-parse --short HEAD`.
