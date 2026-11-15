---
artifact: domain-distillation
created: 2026-11-15
repo_root: /Users/krzysztofmacioszek/Develop/Projects/10xDevs/szczur-radar-worktrees/pre-m5-discovery
code_scope: szczur-radar-api/, szczur-radar-web/, tools/pipeline-trigger/, tools/db-backup/
head: 66d4199 (kod jak ef56ddc, zamknięcie M-4)
dirty: tylko context/ — context/domain/ i context/archive/2026-11-01-0959-domain-distillation/ (to uruchomienie), context/map/repo-map.md (korekta mapy z tej samej sesji); kod czysty
path_alias: api/ = szczur-radar-api/src/main/java/pl/szczurradar/api/; web/ = szczur-radar-web/src/; mig/Vn = plik migracji n w szczur-radar-api/src/main/resources/db/migration/; test/ = szczur-radar-api/src/test/java/pl/szczurradar/api/; prd.md, roadmap.md, lessons.md = context/foundation/; contract-v2.md, agent-implementation.md = context/foundation/llm-assessment/; deploy-plan.md = context/deployment/; pipeline|sources|assessor/CLAUDE.md = api/<pakiet>/CLAUDE.md (assessor: api/agents/assessor/); system-prompt.md = szczur-radar-api/src/main/resources/assessor/system-prompt.md; S-NN/ = folder zmiany wycinka w context/archive/
documents: 8 requirements, 61 narrative (18 plan-briefów przeczytanych, 58 archiwów po tytule), 19 reference, 5 engineering
previous: context/archive/2026-11-01-0959-domain-distillation/domain-distillation.md
evidence: context/domain/evidence/
glossary: context/domain/glossary.md
---

# Domain distillation: Szczur Radar

## Summary

Domena to **ocenione wypowiedzi inwestycyjne z forów**. Produkt codziennie pobiera posty z Wykopu (#gielda) i z trzech subredditów (archiwum Arctic Shift), opisuje je modelem językowym według zamkniętego kontraktu V2 i pokazuje zaproszonym osobom jako przeszukiwalny feed. Kontrakt opisuje:
- trafność;
- cele ze stanowiskiem autora;
- tezy z dosłownym cytatem.

Wartość posta ocenia człowiek, nie model (`prd.md:311`).

Poddomeny:
- **Core** (trzy): zbieranie i wspólny model, ocena treści, przegląd ocenionych postów.
- **Supporting:** przebiegi, sygnał dla operatora (od M-4 z jakością ocen per źródło).
- **Generic:** konta i dostęp.

Terminy: z 77 wierszy Core i Supporting 4 są nieobecne w kodzie produktu (zestaw wzorcowy, horyzont Arctic Shift, megawątek, budżet LLM), a 10 istnieje tylko niejawnie, bez własnego typu.

M-4 zamknął wszystkie 10 otwartych rozbieżności z poprzedniego przebiegu i pięć pytań właściciela. Zmienił też status czterech polityk z rankingu z *declared* na *enforced*:
- jakość ocen per źródło;
- post odrzucony przy zapisie;
- seria odmów dostawcy;
- słowa Wykopu na Reddicie.

Najsilniejsza rozbieżność, jaka została, to nie konflikt dokumentów z kodem, tylko reguła Core bez żadnej miary. „Tagi kanału nigdy nie są celem” (`contract-v2.md:221`) pilnuje wyłącznie prompt. Prompt wymienia tylko tagi Wykopu (`system-prompt.md:21`), a na Reddicie kanałem jest nazwa subredditu.

**Kandydat #1 to ta reguła (R-39).** M-5 planuje widoki agregujące posty po celu, więc cel błędnie zrobiony z kanału trafiłby wprost do nowego UI.

Rozbieżności otwarte: 0 do naprawy w kodzie, 0 w dokumentach, 0 do decyzji; nieaktualne zdania dokumentów są w *Stale documents*. Pytania otwarte: 6 (Q-08, Q-11, Q-12, Q-15, Q-19, Q-20). Reguł *contradicted* i ekspozycji *undocumented* nie znaleziono.

## Changes since the previous run

Dopasowanie po ID z `previous:`.

**Zamknięte rozbieżności (wszystkie 10):**

| ID | Co się zmieniło |
|---|---|
| D-02, D-03, D-10 | PRD v3 ma nowsze zdania zgodne z kodem: sesja 30 dni `prd.md:152`, „zakresu godzin nie ma” `prd.md:84`, FR-022 w grupie (a) `prd.md:163`. Starsze zdania przeszły do *Stale documents*. |
| D-07, D-11 | Kontrakt dopisany: mapowanie Reddita `contract-v2.md:70`, `provider_error` to dziś tylko odmowa `contract-v2.md:363`. |
| D-16 | Wejście oceny opisane w PRD z trzema polami: `prd.md:311`. |
| D-12 | S-37: słowa typu posta per źródło (`web/labels.ts:92`). |
| D-13 | S-34: sam martwy post nie daje alarmu (`api/notifications/RunNotifications.java:134-136`). |
| D-14 | Decyzja Q-16 zapisana w kontrakcie: reguła bez wyjątków, pilnowana pomiarem, nie walidatorem (`contract-v2.md:262-263`). Pomiar to S-36. |
| D-15 | S-38: odrzucony wiersz liczony jako `skipped` z WARN (`api/pipeline/step/PostStore.java:155-161`). |

**Zmiany statusu reguł:**

| ID | Zmiana | Skąd |
|---|---|---|
| R-15 | declared → **enforced** | miara i sygnał FR-033 (S-35, S-36) |
| R-06 | declared → **enforced** | S-38 |
| R-41 | declared → **enforced** | S-32, wyłącznik serii odmów |
| R-42 | declared → **enforced** | S-34 |
| R-45 | declared → **enforced** | S-37 |
| R-23 | declared → **enforced** | strażnik testowy S-39 |
| R-04 | treść zmieniona przez FR-034 (z `dead` wraca tylko przywrócenie admina), status **enforced** | S-33 |
| R-30, R-17, R-43 | zostają **enforced**, a strażnicy liczą teraz zbiory z kodu | S-39–S-42 |
| R-38 | zostaje **declared**, teraz z decyzji | Q-16 |

**Pytania:** zamknięte Q-13, Q-14, Q-16, Q-17 i Q-18; odpowiedzi są w PRD v3 (`prd.md:19`). Otwarte zostają Q-08, Q-11, Q-12 i Q-15. Nowe: Q-19 i Q-20.

**Ranking:** poprzednie pozycje #1–#6 zamknięte. Pozycje #7–#10 przechodzą dalej. Nowe pozycje to R-39, R-38 i R-16 (dotąd niżej) oraz nieatomowe zamknięcie przebiegu (R-19).

**Nowe terminy:**
- T-70 przywrócenie martwych;
- T-71 jakość ocen per źródło;
- T-72 próg i przejście;
- T-73 język posta;
- T-74 podsumowanie nocy;
- T-75 noc;
- T-76 typ posta słowami źródła;
- T-77 post odrzucony przy zapisie.

**Nowe reguły:** R-46 przywrócenie martwych, R-47 alarm jakości tylko przy przejściu, R-48 podsumowanie nocy, R-49 kanał zdarzeń API to ntfy, R-50 język posta = język źródła, R-51 widoczność tylko ocenionych postów (dotąd reguła bez ID).

**Granice kontekstów:** B-04 („kanał”) rozszerzona o kanał alarmowy, B-13 („dead”) o martwy przebieg, B-06 („operator” vs „admin”) opisana jako dwie role w praktyce.

## Ubiquitous language

Terminy Core (C) i Supporting (S). Definicje to cytaty ze źródeł: dokumenty po polsku, instrukcje dla agentów po angielsku. Lokalizacje pochodzą z `evidence/2-code-language.md`, `evidence/3-code-rules.md` i z celowanych wyszukiwań tego przebiegu.

| id | term | kind | definition (quoted) | source | in code (path:line) or **missing in code** | other names in code |
|---|---|---|---|---|---|---|
| T-01 | źródło (danych) | closed set (C) | „Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu” | `prd.md:295` | `api/sources/Source.java:16`; CHECK `mig/V12:24-28` | `Source` (też `Provenance.Source`, B-03) |
| T-02 | element źródła | value (C) | „LLM analizuje semantykę pojedynczej wypowiedzi (`item`), nie surowy payload źródła.” | `contract-v2.md:25` | `api/sources/SourceFetcher.java:33`; `WykopItem`, `RedditItem` | `item`, `entry`, „element” |
| T-03 | wspólny model | value (C) | „mapuje je do wspólnego modelu” | `prd.md:38` | `api/sources/SourcePost.java:27`; `api/agents/assessor/contract/AssessmentInputV1.java:13` | `SourcePost` |
| T-04 | pozyskanie i mapowanie | operation (C) | „nowego pozyskania i mapowania do wspólnego modelu” | `prd.md:295` | `api/sources/SourceFetcher.java`, `WykopMapper`, `RedditMapper` | `fetch`, `map` |
| T-05 | post | entity (C) | `stage` ∈ {`stored`, `assessing`, `assessed`, `failed`, `dead`} | `S-02 first-assessed-wykop-feed/plan.md:228` | `api/posts/Post.java:38`; `mig/V3:84`; klucz `mig/V4:16` | `Post` (trzyma też `link`, B-11) |
| T-06 | typ posta (`item_type`) | closed set (C) | „`item.item_type` … `post`, `link`, `comment`” | `contract-v2.md:61` | `api/agents/assessor/contract/ItemType.java:10`; `mig/V4:15` | `ItemType` |
| T-07 | znalezisko | value (C) | Wykop: „`link` → „znalezisko”” | `prd.md:249` | `web/labels.ts:92` (tylko Wykop); `api/sources/wykop/WykopResource.java:15` | `LINK` |
| T-08 | komentarz | value (C, poza MVP) | „MVP ocenia wyłącznie posty; komentarze to jawnie odroczone rozszerzenie” | `prd.md:340` | `ItemType.COMMENT` `ItemType.java:10`, bez producenta | `comment` |
| T-09 | kanał posta | value (C) | „tag Wykopu (historyczne posty Wykopu dostały `gielda`) albo nazwa subredditu” | `prd.md:298` | `posts.channel` `mig/V13:10-13`; `SourcePost.channel` `api/sources/SourcePost.java:27`; `api/sources/wykop/WykopPosts.java:23` | `channel`, `channels`, `tag` (B-04) |
| T-10 | duplikat | policy (C) | „System odrzuca duplikaty postów po identyfikatorze źródłowym” | `prd.md:92` | `mig/V4:16`; `api/pipeline/step/PostStore.java:99-109,126-130` | `duplicates` |
| T-11 | RAW (surowy payload) | value (C) | „Oryginalny payload źródłowy … utrzymywany niezależnie od znormalizowanych danych” | `prd.md:305` | `api/raw/RawPayload.java:23`; `mig/V3:65` | `raw_payloads` |
| T-12 | zakres dat (D-2…D-1) | value (S) | „pełne dni kalendarza warszawskiego (przebieg dzienny: D-2…D-1 …); zakresu godzin nie ma” | `prd.md:84` | niejawne: `api/pipeline/api/InternalPipelineRunController.java:85-87`; CHECK `mig/V4:19-22` | `range_from`, `range_to` |
| T-13 | pominięty element (`skipped`) | measure (S) | „pobrane = nowe + duplikaty + pominięte” | `prd.md:294` | `api/sources/SourceFetcher.java:33`; `PostStore.java:155-161` | `skipped` |
| T-14 | ocena (próba oceny) | entity (C) | „wynik oceny to `output` … obok `provenance`” | `contract-v2.md:108` | `api/posts/Assessment.java:54`; `mig/V3:130` | `Assessment`, `AssessmentResult` |
| T-15 | kontrakt oceny V2 | policy (C) | „Normatywny kontrakt pojedynczej oceny treści przez LLM” | `contract-v2.md:13` | `api/agents/assessor/contract/AssessmentOutputV2.java:16`; schemat `assessor/assessment-output-v2.schema.json` | `contract_version` |
| T-16 | wejście oceny | value (C) | „treść posta (tytuł, treść …) z trzema polami opisu z kontraktu: typ posta, data publikacji i kanał” | `prd.md:311` | `AssessmentInputV1.java:13`; `Item.java:15-17` | `input` |
| T-17 | trafność | closed set (C) | `investment_related` — „autor mówi o rynku, spółce, aktywie, sektorze lub makro …” | `contract-v2.md:135-137` | `api/agents/assessor/contract/Relevance.java` | `relevance` |
| T-18 | cel (`target`) | value (C) | „Cel to podmiot (spółka, aktywo, sektor, temat), nie teza.” | `contract-v2.md:27` | `api/agents/assessor/contract/Target.java:11`, `TargetKind`; tylko JSONB `assessments.output` `mig/V3:139` | `target` |
| T-19 | anchor | value (C) | „najkrótszy ciągły fragment tekstu nazywający cel, dokładnie jak w tekście” | `contract-v2.md:219` | `Target.java:11`; filtr `api/posts/PostRepository.java:115-122` | `anchor.quote` |
| T-20 | stanowisko (`stance`, `direction`) | value (C) | „Stanowisko jest osobne dla każdego celu; ton emocjonalny ≠ stanowisko inwestycyjne.” | `contract-v2.md:26` | `api/agents/assessor/contract/Stance.java:13`, `Direction` | „sentyment” (historyczne) |
| T-21 | teza (`claim`) | value (C) | „Tezy (`claims`) opisują, co autor twierdzi; każda ma dokładnie jeden dosłowny cytat z `item`.” | `contract-v2.md:28` | `api/agents/assessor/contract/Claim.java:14`, `ClaimKind` | `Claim`, `statement`, „Tezy ogólne” (B-09) |
| T-22 | cytat (`evidence`) | policy (C) | „Cytaty są sprawdzane przez backend jako podciąg wskazanego pola po normalizacji białych znaków (§10) — i niczym więcej.” | `contract-v2.md:29` | `api/agents/assessor/validation/rules/QuoteIsFragmentRule.java:16`; `RuleId.java:14-57` | `quote` |
| T-23 | normalizacja białych znaków | policy (C) | „każdy znak biały → zwykła spacja … Nic więcej” | `contract-v2.md:293-299` | `api/text/Whitespace.java:11`; rejestr `web/contract/rules.json` | `Whitespace` |
| T-24 | flagi, modyfikatory | closed sets (C) | „Niepewność jest jawna (`unclear`, `hedged`, flagi); model nie zgaduje.” | `contract-v2.md:30` | `api/agents/assessor/contract/Flag.java:8`, `Modifier.java:8` | — |
| T-25 | klasa błędu oceny | closed set (C) | „`provider_error \| timeout \| invalid_structured_output \| …`” | `contract-v2.md:365-372` | `api/agents/assessor/validation/FailureClass.java:11`; CHECK `mig/V3:162-164` | `failure_class` |
| T-26 | walidator D1–D15 | policy (C) | „Wynik z jakimkolwiek naruszeniem nie jest poprawną oceną” | `contract-v2.md:372` | `RuleId.java:14-57`; `RuleBasedValidator.java:21-26,40-46` | `ContractV2Rules` |
| T-27 | pochodzenie oceny | value (C) | „źródło wyniku: model \| prefilter”, wersja promptu, wersja kontraktu … „poza payloadem LLM” | `contract-v2.md:427-441` | `api/agents/assessor/Provenance.java:43`; `mig/V3:143-151` | `provenance` |
| T-28 | źródło wyniku (`model` / `prefilter`) | closed set (C) | „źródło wyniku: model \| prefilter” | `contract-v2.md:434` | `Provenance.Source` `Provenance.java:50-56`; `assessments.result_source` `mig/V3:169` | `result_source` (B-03) |
| T-29 | wpis bez treści / odsiew | policy (C) | „Wpis bez treści nie trafia do modelu. Dostaje gotowy wynik zgodny z kontraktem” | `contract-v2.md:317-319` | `api/agents/assessor/Prefilter.java:17,20,35-42`; `Assessor.java:71-75` | `Prefilter`, `insufficient_content` |
| T-30 | konfiguracja produkcyjna (dostawca, model, tier) | value (C) | wersja promptu i model w pochodzeniu oceny | `contract-v2.md:427-441` | `api/llm/LlmProviderProperties.java:45`; `PROMPT_VERSION` `api/agents/assessor/prompt/AssessmentPrompt.java:39` | `Provider`, `ServiceTier` |
| T-31 | zestaw wzorcowy / progi modelu | policy (C) | „~120 zanonimizowanych postów #gielda … oznaczonych przez użytkownika”; „zgodność z kontraktem ≥ 95%” | `agent-implementation.md:102-116` | **missing in code** produktu: tylko testy (`git grep -i 'gold\|wzorcow'`: 30 plików w `src/test`, w `src/main` tylko komentarze promptu) | „gold set” (komentarze) |
| T-32 | feed / lista postów | view (C) | „listę chronologiczną z filtrowaniem po podstawowych polach klasyfikacji LLM (FR-011a)” | `prd.md:39` | niejawne: `api/posts/api/PostController.java:93`, `PostRepository.java:123-130`; `web/views/FeedView.vue` | „Posty” |
| T-33 | filtr (cel, rodzaj celu, stanowisko) | operation (C) | FR-011a | `prd.md:127-129` | `api/posts/PostFilter.java:18,24`; `web/components/FeedFilter.vue:57` | `q`, `kind`, `direction` |
| T-34 | widok szczegółowy posta | view (C) | „widok szczegółowy pojedynczego posta (wersja surowa obok przetworzonej przez LLM)” | `prd.md:39` | `api/posts/api/PostController.java:126-130`; `web/views/PostDetailView.vue` | — |
| T-35 | świeżość („Dane zaktualizowane”) | measure (C) | „data jest osobna dla każdego źródła i zawsze rozpisana” | `prd.md:232` | `api/pipeline/api/FreshnessController.java:48`; `GET /api/freshness` | `freshness` (B-10) |
| T-36 | przebieg | entity (S) | `status ∈ {queued, running, succeeded, partial, failed, interrupted}` | `S-02/plan.md:209-210` | `api/pipeline/run/PipelineRun.java:33`; `mig/V3:7` | „przepływ”, „uruchomienie”, `pipeline` |
| T-37 | rodzaj przebiegu | closed set (S) | „`RANGE` (zakres dat), `RETRY` (resztki …), `POSTS` (zaznaczone id)” | `S-02/plan-brief.md:29` | `RunKind`; CHECK `mig/V3:29` | `kind` |
| T-38 | status przebiegu | closed set (S) | „`partial` się liczy … „Przebieg zakończony błędem” to `failed` albo `interrupted`” | `prd.md:230` | `api/pipeline/run/RunStatus.java:13`; `api/pipeline/flow/RunFlow.java:299-304` | `status` (cztery inne `status`) |
| T-39 | krok przebiegu | entity (S) | RANGE: „fetch → store → select → assess → summarize” | `pipeline/CLAUDE.md:71` | `api/pipeline/run/RunStep.java:10`; CHECK `mig/V3:55` | `step` |
| T-40 | przebieg dzienny (cron) | event (S) | „serwis cron `pipeline-trigger` … czeka na jego wynik” | `prd.md:279` | niejawne: `RANGE` zlecony przez `SystemAccount.SCHEDULER` `api/user/SystemAccount.java:13`; `InternalPipelineRunController.java:83-89` | `daily` |
| T-41 | ręczne wywołanie przebiegu | operation (S) | FR-022, w grupie (a) | `prd.md:163` | `api/pipeline/api/PipelineRunController.java:58`; `web/views/AdminView.vue:417` | „Nowy przebieg” |
| T-42 | resztki | state (S) | „System najpierw ponawia resztki (`STORED` i nieudane oceny …)” | `S-02/plan-brief.md:16` | niejawne: `api/posts/PostRepository.java:34-45` | `leftovers`, „ponowienie resztek” |
| T-43 | próba oceny (policzona) | measure (S) | „Próbę zużywa tylko odmowa dostawcy albo zła odpowiedź modelu.” | `prd.md:283` | `posts.assessment_attempts`; `api/posts/Post.java:41`; `PostTransitions.java:224-232` | `attempts` (B-02) |
| T-44 | post martwy (`dead`) | state (S) | „Post po trzeciej nieudanej próbie oceny staje się martwy (`dead`)” | `S-33 restore-dead-posts/plan-brief.md:7` | `api/posts/PostStage.java:16`; UI „martwy” wpisane w `web/views/AdminView.vue:575` (bez wpisu w `labels`) | `DEAD` (B-13) |
| T-45 | błąd techniczny | measure (S) | „Pojedyncza awaria, po której przebieg idzie dalej, jest błędem technicznym posta” | `prd.md:283` | `posts.technical_failures` `mig/V10:9`; `pipeline_runs.processing_errors` `mig/V3:23` | `technical_failures`, `processingErrors` |
| T-46 | wstrzymany post | state (S) | „Trzy błędy techniczne posta z rzędu … wstrzymują post: RETRY go nie bierze” | `prd.md:283` | niejawne: `api/posts/Post.java:47`, `PostRepository.java:38-45` | `on hold` |
| T-47 | wyłącznik serii (awarie dostawcy, błędy klienta, odmowy) | policy (S) | „trzy odmowy dostawcy LLM z rzędu … kończą przebieg `failed`” | `prd.md:283,286,292` | `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:54-57,121,127,135` | bezpiecznik |
| T-48 | ponowienie transportu | policy (S) | „Ponawiane mogą być wyłącznie błędy transportu” | `contract-v2.md:373` | `api/llm/transport/TransportRetry.java:23`; `api/sources/transport/HttpRetry.java:39` | `retry` (B-07) |
| T-49 | strażnik (watchdog) | policy (S) | „Przebieg bez postępu przez ustalony próg API samo kończy jako `failed`” | `prd.md:280` | `api/pipeline/RunWatchdog.java:45,93-113`; `PipelineStallSettings.java:42-51` | `stall` |
| T-50 | przyczyna przerwania | closed set (S) | `deploy`, `restart`, `shutdown`, `lost` | `REPOSITORY_GUIDELINES.md` (blok Deployment, Watchdog) | `api/pipeline/run/InterruptionCause.java:12-28`; `mig/V11:10-11` | `interruption_cause` |
| T-51 | pusty przebieg | event (S) | „`failed` tylko gdy żadne źródło nie dało posta” | `prd.md:287` | `api/pipeline/step/SourceIngest.java:136-138` | `NoPostsFetchedException` |
| T-52 | „wymaga uwagi” | policy (S) | „Reguła „wymaga uwagi” zostaje jedna (S-22): różni się tylko kanał, nie powód.” | `prd.md:293` | `api/pipeline/run/PipelineRun.java:316-352`; `AttentionReason.java:14-43`; `web/runs.ts:104` | `attention`, `run_attention`, `RUN_NEEDS_ATTENTION` |
| T-53 | jeden przebieg naraz | policy (S) | „jeden naraz, 409 przy aktywnym” | `S-05/plan-brief.md:12` | niejawne: `api/pipeline/PipelineRunService.java:216-226`; `mig/V3:39` | `pipeline_busy` |
| T-54 | sieroty (`lost`) | policy (S) | „gdy żaden z nich nie wykazał aktywności przez `PIPELINE_STALL_TIMEOUT`” | `REPOSITORY_GUIDELINES.md` (blok Watchdog) | `PipelineRunService.java:240-271`; `RunRecorder.java:356-370` | `orphans` |
| T-55 | dni bez danych | measure (S) | „Takie dni z ostatnich 14 (do D-2) … osobno dla każdego skonfigurowanego źródła” | `prd.md:284` | `api/pipeline/run/MissingDays.java:45,48,55`; `PipelineRunController.java:153` | `MissingDays`, `CoveredRange` |
| T-56 | powiadomienie (w aplikacji) | entity (S) | „dzwoneczek z liczbą nieprzeczytanych w nagłówku i strona przebiegu … z powodem” | `prd.md:280` | `api/notifications/Notification.java:19`; `mig/V9:6` | `run_attention` |
| T-57 | alert operatora / kanał zdarzeń API | event (S) | „Kanał zdarzeń API to ntfy. Każde zdarzenie API dla operatora … idzie jako jedna wiadomość do tematu ntfy” | `prd.md:293` | `api/alerts/AlertEvent.java:13-59`; `api/alerts/NtfyAlerts.java:32`; `OperatorAlertQueue.java:56-67` | `OperatorAlerts` (B-04) |
| T-58 | operator | role (S) | „Administrator (operator wdrożenia) — zarządza dostępem” | `prd.md:33` | niejawne: tylko `api/alerts/OperatorAlerts.java:15`; `Role` go nie ma | (B-06) |
| T-59 | kopia bazy | entity (S) | „codzienna kopia logiczna bazy, którą robi osobny serwis infrastruktury … sprawdzana pełnym odtworzeniem” | `prd.md:302` | `tools/db-backup/backup.sh:330,336-337`; `restore-check.sh:107-114` | `db-backup` |
| T-60 | konto systemowe | role (S) | „ma `login_enabled = false` i nie ma hasła … nigdy się nie zaloguje” | `prd.md:332` | `api/user/SystemAccount.java:13`; `mig/V6:8-12` | `scheduler` |
| T-61 | wynik źródła | entity (S) | „Wynik każdego źródła jest zapisywany zaraz po jego pobraniu” | `prd.md:287` | `api/pipeline/run/PipelineRunSource.java:26`, `SourceFetchStatus`; `mig/V12:33,45` | `succeeded`/`empty`/`failed` |
| T-62 | źródło skonfigurowane | state (C) | „Bez niego Reddit nie jest skonfigurowanym źródłem … `source_not_configured`” | `prd.md:247,298` | `api/pipeline/step/SourceIngest.java:48-60`; `api/sources/reddit/RedditConfiguration.java:39` | `source_not_configured` |
| T-63 | post zdjęty / skasowany | state (C) | „Posty zdjęte przez moderację albo Reddit i skasowane przez autora nie są pobierane ani liczone” | `prd.md:247` | niejawne, tylko Reddit: `api/sources/reddit/RedditItem.java:42-43`, `RedditRepository.java:126-127,187-188` | — |
| T-64 | pochodzenie posta (źródło · kanał) | value (C) | „każdy post w feedzie i na stronie szczegółów jest oznaczony źródłem i kanałem” | `S-29 source-aware-feed/plan-brief.md` | `web/labels.ts:84` (`postOrigin`) | „Wykop · #gielda” |
| T-65 | horyzont Arctic Shift | policy (S) | dostęp może wygasnąć między styczniem a marcem 2027 | `deploy-plan.md` (S-27, runbook), release v0.3 | **missing in code**: szukane `horizon\|horyzont\|2027\|arctic.*expir` w `src/main`, `web/src` (0 trafień o tym znaczeniu) | — |
| T-66 | megawątek | value (C) | wątek zbiorczy subredditu (pomiar S-28, filtr w `## Parked`) | `roadmap.md` (`## Parked`, naprawy oceny Reddita) | **missing in code**: szukane `megaw\|megathread` w `src/main`, `web/src` (0); tylko 7 plików testów | — |
| T-67 | budżet LLM | policy (C) | „Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe.” | `prd.md:51` | **missing in code**: limit żyje na kluczu OpenRouter; podsumowanie nocy ma tokeny bez kosztu w pieniądzu (`prd.md:293`); `budget` w `src/main` tylko w `NtfyAlerts.java:117` (bajty wiadomości) | — |
| T-68 | trzy winy (post / dostawca / nasz kod) | policy (S) | „Próbę zużywa tylko odmowa dostawcy albo zła odpowiedź modelu.” | `prd.md:283`; `pipeline/CLAUDE.md:407-410` | niejawne: `Assessor.java`, `ConcurrentAssessmentDispatcher.java:54-57` | — |
| T-69 | aktywność przebiegu | measure (S) | „which is also progress for the watchdog” | `REPOSITORY_GUIDELINES.md` (blok Watchdog) | `pipeline_runs.last_activity_at`; `RunRecorder.java:140-158` | `last_activity_at` |
| T-70 | przywrócenie martwych | operation (S) | FR-034: „post traci martwy stan, a jego próby oceny są zerowane, więc kolejny przebieg ocenia go od nowa” | `prd.md:273` | `api/posts/api/DeadPostController.java:46-63`; `api/pipeline/state/PostTransitions.java:190-201` | `restore`, „Przywróć martwe” |
| T-71 | jakość ocen per źródło | measure (S) | „udział nieudanych prób oceny z wywołaniem modelu … w oknie 7 dni kalendarza warszawskiego” | `prd.md:271` | `api/posts/AssessmentQuality.java:54,57,60,63`; `QualityStatus.java:14-23` | `quality 7d`, `language 7d` |
| T-72 | próg i przejście przez próg | policy (S) | alarm „tylko przy przejściu przez próg — okno dzisiejsze ponad progiem, a okno, które widziało poprzednie podsumowanie, nie” | `prd.md:271` | progi `api/sources/Source.java:18,25`; `api/posts/QualityCrossing.java:13`, `LanguageCrossing` | `ABOVE_THRESHOLD`, `ASSESSMENT_QUALITY` |
| T-73 | język posta | value (C) | „Język posta to `posts.language`, dziś język źródła (Wykop `pl`, Reddit `en`).” | `prd.md:272` | `mig/V16:13-14`; `api/text/Language.java:10`; `api/posts/Post.java:150` | `language` |
| T-74 | podsumowanie nocy | event (S) | wysyłane „**zawsze**, także przy 0 martwych” | `prd.md:293` | `api/pipeline/NightSummary.java:124`; `AlertEvent.NIGHT_SUMMARY`; `tools/pipeline-trigger/trigger.sh:242-246` | `nightly-summary` |
| T-75 | noc | value (S) | linia nocy w podsumowaniu (zakres, status każdego przebiegu) | `prd.md:293`; `roadmap.md:213` | niejawne: bez typu i tabeli, lista id przebiegów w żądaniu (`trigger.sh:244`) | — |
| T-76 | typ posta słowami źródła | policy (C) | „Wykop: `post` → „wpis”, `link` → „znalezisko”. Reddit: `post` → „post”, `link` → „link”.” | `prd.md:249` | `web/labels.ts:92` (`ITEM_TYPE_WORDS`) — tylko SPA | „wpis”, „znalezisko” |
| T-77 | post odrzucony przy zapisie | state (C) | „nie znika po cichu. Jest liczony jako pominięty” | `prd.md:294` | `api/pipeline/step/PostStore.java:41,155-161`; `RunRecorder.storeRejected` `RunRecorder.java:177-190` | `rejected` |

Liczby:
- **Dowody:** ok. 75 terminów w dowodach dokumentów i 34 pojęcia w dowodach kodu; w tabeli jest 77 wierszy Core i Supporting.
- **Brak w kodzie produktu: 4.** Polityki T-31, T-65, T-67 i wartość T-66.
- **Niejawne (bez własnego typu albo nazwy): 10.** T-12, T-32, T-40, T-42, T-46, T-53, T-58, T-63, T-68, T-75.
- **Generic:** terminy kont i dostępu (konto, rola, sesja, link aktywacyjny, CLI operatora, limiter) mają spójne nazwy w kodzie, więc są tylko policzone (6).

Pojęcia tylko w kodzie (*searched in docs* = szukane w dokumentach czytanych przez agenta):
- **`posts.tags`, `posts.slug`, `posts.content_hash`:** zapisywane, bez czytelnika w `api/`. `content_hash` jest liczony (`api/posts/Post.java:159`) „to notice an edited post” (`mig/V3:97`), ale nic go nie porównuje. Jedyny odczyt jest w `test/JpaSchemaIT.java:181`, a duplikat zostaje „left as it is” (`PostStore.java:45-46`). *Searched in docs* → Q-12.
- **`Channel` w `api/llm`:** kanał wywołania LLM (`api/llm/Channel.java:10`), bez definicji w dokumentach. *Searched in docs*. B-04.
- **Post `prefilter` jako `assessed`:** wpis bez treści staje się `assessed` z syntetyczną oceną `unclear` i jest w feedzie (`Prefilter.java:41-42`, `Assessor.java:72-74`, `PostController.java:161`). Dokumenty mówią tylko „Dostaje gotowy wynik”. *Searched in docs* → Q-15.
- **„Najnowsza ocena `ok`”:** filtr działa na najnowszej udanej ocenie (`PostRepository.java:115-122,137-143`). *Not in the docs sample*.
- **`deployment_id`, „instancja”:** `mig/V8:13`, `PipelineStartupRecovery.java:54`. *Searched in docs*.
- **`/api/admin/llm/check`:** trasa bez klienta w SPA. Q-08.

## Multiple meanings and context boundaries

| id | word | meaning A (context, source) | meaning B (context, source) | suggested names |
|---|---|---|---|---|
| B-01 | post / wpis | każda zebrana jednostka (encja `Post`) | `ItemType.POST`: wpis Wykopu albo self-post Reddita (`api/sources/wykop/WykopMapper.java:49-50`, `api/sources/reddit/RedditMapper.java:50`) | „post” dla encji; „wpis” tylko jako słowo Wykopu |
| B-02 | próba | policzona próba posta 0–3 (`prd.md:283`) | wywołanie z ponowieniami w mierze jakości: „ponowienia włącznie” (`prd.md:271`); `attempts` transportu (`pipeline/CLAUDE.md:84-85`) | „próba posta” vs „wywołanie modelu” |
| B-03 | źródło / `Source` | Wykop, Reddit (`api/sources/Source.java:16`) | źródło wyniku `model`/`prefilter` (`Provenance.java:50-56`, `contract-v2.md:434`) | „źródło” vs „źródło wyniku” (`result_source`) |
| B-04 | kanał / `channel` | miejsce w źródle: tag albo subreddit (`prd.md:298`, `posts.channel`) | kanał alarmowy operatora: ntfy, Telegram (`prd.md:293`); w kodzie także kanał wywołania LLM (`api/llm/Channel.java:10`, `assessments.channel`) | „kanał posta”, „kanał alarmowy”, „kanał LLM” |
| B-05 | `failed` | etap posta z próbami do ponowienia (`PostStage.FAILED`) | status przebiegu, nieudana próba (`AssessmentResult.Status.FAILED`), wynik źródła; lista „Nieudane posty” obejmuje też `dead` i wstrzymane | „post do ponowienia”, „przebieg zakończony błędem” |
| B-06 | operator / admin | konto z rolą `admin` w aplikacji: dzwonek, `/admin`, przywracanie (`prd.md:326-328`) | osoba z dostępem do CLI, Railway, ntfy i Telegrama: alerty i podsumowanie nocy (`prd.md:33,285,293`) | „admin” = rola konta; „operator” = rola wdrożenia |
| B-07 | ponowienie / retry | przebieg `RETRY` resztek (`web/labels.ts:45`) | ponowienie wywołania transportu (`TransportRetry`, `HttpRetry`); „Ponów zaznaczone” to przebieg `POSTS` | „ponowienie resztek” vs „ponowienie wywołania” |
| B-08 | sygnał | sygnał inwestycyjny, oceniany przez człowieka (`prd.md:311`) | sygnał dla operatora o złej nocy (`prd.md:278`) | „sygnał inwestycyjny” vs „sygnał dla operatora” |
| B-09 | teza | każdy `Claim` (`contract-v2.md:28`; miara `MIN_THESES` `AssessmentQuality.java:63`; „Tezy ogólne” `web/components/PostAssessment.vue:57`) | `ClaimKind.INVESTMENT_THESIS`, „teza inwestycyjna” (`contract-v2.md:175`, `web/labels.ts:26`) | „teza” = claim; „teza inwestycyjna” = rodzaj |
| B-10 | udany / aktualizacja | status `succeeded` | dla świeżości `succeeded` albo `partial` z wynikiem źródła `succeeded` (`prd.md:230,232`) | „przebieg udany” vs „dzień pokryty dla źródła” |
| B-11 | `link` | znalezisko Wykopu (`WykopResource.LINK`) | każdy nie-self post Reddita: link, obraz, crosspost (`contract-v2.md:70`, `RedditMapper.java:50`) | słowo źródła przy wyświetlaniu (T-76) |
| B-12 | pobrane / `fetched` | w produkcie bez postów zdjętych (`prd.md:247`) | surowa liczba elementów API źródła | „pobrane” = po odsianiu zdjętych |
| B-13 | `dead` / martwy | post po trzeciej próbie (`PostStage.DEAD`) | martwy („zombie”) przebieg bez aktywności przez `PIPELINE_STALL_TIMEOUT` (`REPOSITORY_GUIDELINES.md`, blok Deploy guard) | „martwy post” vs „osierocony przebieg” (`lost`) |
| B-14 | kontrakt | kontrakt oceny LLM (`contract-v2.md:13`) | kontrakt SPA ↔ API: pliki `web/contract/*.json` i rejestr `rules.json` | „kontrakt oceny” vs „kontrakt API” |
| B-15 | ocena | wynik modelu, opis posta (`assessment`) | decyzja „wartościowy sygnał”, która należy do człowieka (`prd.md:311`) | „ocena” = opis; decyzja poza produktem |

## Subdomains

| subdomain | class | concepts | why (goal quote, or description and usage) |
|---|---|---|---|
| Zbieranie i wspólny model | **Core** | T-01…T-11, T-62…T-64, T-66, T-73, T-77 | „Codzienny przepływ … pobiera posty z Wykopu (#gielda) i wybranych subredditów Reddita …, mapuje je do wspólnego modelu, odrzuca duplikaty” (`prd.md:38`) |
| Ocena treści (kontrakt V2) | **Core** | T-14…T-31, T-67 | „Aplikacja opisuje znaczenie każdego zebranego posta tak, by użytkownik mógł szybko ocenić, czy jest wartościowym sygnałem inwestycyjnym” (`prd.md:309`); „Jakość danych z LLM musi być wysoka” (`prd.md:50`) |
| Przegląd ocenionych postów | **Core** | T-32…T-35, T-64, T-76 | „Użytkownik widzi przetworzone posty w aplikacji webowej” (`prd.md:39`); persona chce „w krótkim czasie zorientować się, czy w ostatnich postach pojawiło się coś wartego uwagi” (`prd.md:29`) |
| Przebiegi i ich niezawodność | Supporting | T-12, T-13, T-36…T-55, T-61, T-68…T-70 | potrzebne i specyficzne (kolejka, próby, resztki, strażnik, wyniki źródeł, przywracanie), ale nie tu produkt wygrywa |
| Sygnał dla operatora, jakość ocen i kopia danych | Supporting | T-56…T-60, T-65, T-71, T-72, T-74, T-75 | „Operator otrzymuje jednoznaczny, widoczny sygnał” (`prd.md:278`); FR-033 mierzy guardrail jakości, ale nie jest wartością dla użytkownika feedu |
| Konta i dostęp | Generic | konto, rola, sesja, link aktywacyjny, CLI, limiter | logowanie e-mailem i hasłem, role `user`/`admin`, sesje; specyficzna jest tylko polityka linku przez CLI bez poczty (`prd.md:319`) |

## Invariants and policies

Status: **enforced**, **declared**, **ignored**, **contradicted**, **undocumented**. Kolumny „strzeżone w” i „luka” pochodzą z `evidence/3-code-rules.md`, chyba że wskazano inaczej.

### Post (agregat: identyczność `(source, item_type, source_item_id)`, cykl `stored → assessing → assessed | failed → dead → stored`)

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-01 | Post | „System odrzuca duplikaty postów po identyfikatorze źródłowym” | `prd.md:92` | enforced | data `uq_posts_source_item` `mig/V4:16`; app `PostStore.java:99-109,126-130` | — |
| R-02 | Post | „Próbę zużywa tylko odmowa dostawcy albo zła odpowiedź modelu.” | `prd.md:283` | enforced | data `ck_posts_stage_attempts`, `ck_posts_assessing_owner` `mig/V3:116-122`; app `PostTransitions.java:72-112,224-232` | przejście etapów tylko w aplikacji (baza przyjmie `assessed → failed`) |
| R-03 | Post | „Trzy błędy techniczne posta z rzędu … wstrzymują post”; „Każdy zapisany wynik oceny … zeruje licznik.” | `prd.md:283` | enforced | app `PostRepository.java:38-45`, `PostTransitions.java:98`; data tylko `>= 0` `mig/V10:11` | bez własnego stanu (T-46); przebieg `POSTS` admina bierze wstrzymany post z założenia (`AssessmentSelection.java:50-51`) |
| R-04 | Post | „`dead`: no run takes any more: only an admin's restore … brings it back” | `pipeline/CLAUDE.md:410`; `prd.md:273` | enforced | data CHECK `mig/V3:116-121`; app `PostTransitions.java:75,190-201`; `PipelineRunService.java:198-201` (400 `post_dead`) | — |
| R-46 | przywrócenie martwych | „post traci martwy stan, a jego próby oceny są zerowane”; „Historia prób: wiersze `assessments` zostają” | `prd.md:273`; `S-33/plan-brief.md:22` | enforced | app `PostTransitions.java:190-201`, `DeadPostController.java:46-63` (1–100 id, tylko `dead`) | id niemartwe pomijane z INFO; w UI brak okna potwierdzenia (`grep confirm(` 0 w `web/views`, `web/components`) |
| R-44 | Post | „so a retry gets exactly the input of the first attempt” | `sources/CLAUDE.md:17` | enforced | app `api/pipeline/assess/AssessmentInputs.java` (poprzedni przebieg; w tym przebiegu niesprawdzane) | — |

### Źródło i pozyskanie

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-05 | niezależność źródeł | „awaria albo cisza jednego źródła jest jego wynikiem (`failed` albo `empty`) i nie zatrzymuje pobrania, zapisu ani oceny postów pozostałych źródeł” | `prd.md:297` | enforced | app `SourceIngest.java:105-129`; zapis `RunRecorder.java:140-158` | od M-4 rejestr `Source` niesie też język i progi jakości (T-72), więc nowe źródło dopisuje progi |
| R-06 | element, który nie staje się postem; post odrzucony przy zapisie | „pobrane = nowe + duplikaty + pominięte”; „nie znika po cichu. Jest liczony jako pominięty” | `prd.md:294` | enforced | app `PostStore.java:47-52,155-161`; liczniki `RunRecorder.java:177-190` | równania nie pilnuje baza (S-38 F5); licznik `new_posts` zapisuje się po kroku, więc nieudany zapis w połowie kończy przebieg `failed` z zapisanymi postami (`RunSequence.java:132-138`) |
| R-07 | pusty przebieg | `failed` tylko gdy „żadne źródło nie dało posta” | `prd.md:287` | enforced | app `SourceIngest.java:136-138` | — |
| R-08 | kompletność pobrania | niespójne stronicowanie = porażka źródła | `sources/CLAUDE.md:82` | enforced | app `WykopRepository.java:139-146,221`, `RedditRepository.java:162,197-207` | — |
| R-09 | RAW | „RAW JSON … zawsze przechowuj oddzielnie od znormalizowanych rekordów” | `lessons.md:16` | enforced | data `mig/V3:75-78,107-108` | — |
| R-35 | post zdjęty | „Posty zdjęte przez moderację albo Reddit … nie są pobierane ani liczone jako pominięte elementy” | `prd.md:247` | enforced | app `RedditItem.java:42-43`, `RedditRepository.java:126-127,187-188` | Wykop nie ma odpowiednika |
| R-36 | retencja Reddita | „post zdjęty albo skasowany na Reddicie po pobraniu zostaje w bazie i w RAW” | `prd.md:304` | enforced | brak ścieżki usuwania (lista pisarzy `evidence/3-code-rules.md`) | — |
| R-40 | rejestracja źródła | „cztery zbiory: `posts.source`, `raw_payloads.source`, `pipeline_runs.sources` i `pipeline_run_sources.source`” | `prd.md:296-297` | enforced | data `mig/V12:24-44`; `test/persistence/ClosedSetsIT.java` | — |
| R-45 | typ posta bez słów innego źródła | „Słowa Wykopu nie pojawiają się przy postach Reddita” | `prd.md:249` | enforced | `web/labels.ts:92` | słownik tylko w SPA (T-76); typy w bazie wspólne (`mig/V4:15`) |
| R-50 | język posta = język źródła | „Język posta to `posts.language`, dziś język źródła (Wykop `pl`, Reddit `en`).” | `prd.md:272` | enforced | data `mig/V16:13-14`; app `Post.java:150` | znane ograniczenie zapisane w PRD: 43 z 9 331 postów Wykopu jest po angielsku (`prd.md:272`) |

### Ocena (agregat: `Assessment` niezmienny po zapisie; kontrakt V2 jako polityka)

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-10 | ocena | „Wynik z jakimkolwiek naruszeniem nie jest poprawną oceną” | `contract-v2.md:372` | enforced | data `ck_assessments_outcome` `mig/V3:161-175`; app `RuleBasedValidator.java:21-26,40-46` | baza przyjęłaby `UPDATE` |
| R-11 | cytat i reguły D1–D15 | „Cytaty są sprawdzane przez backend jako podciąg … — i niczym więcej”; „backend nie „naprawia” odpowiedzi po cichu” | `contract-v2.md:29,372` | enforced | app `RuleId.java:14-57`, `QuoteIsFragmentRule.java:16` | ciała reguł nieczytane w tym przebiegu |
| R-12 | wejście do modelu | „Metadane źródłowe (źródło, autor, link, zaangażowanie) nie trafiają do LLM” | `prd.md:311`; `contract-v2.md:75` | enforced | typ `Item.java:15-17` | — |
| R-13 | odsiew | „Wpis bez treści nie trafia do modelu.” | `contract-v2.md:317` | enforced | app `Prefilter.java:20,35-42`, `Assessor.java:71-75` | wynik odsiewu jest `assessed` i widoczny w feedzie (Q-15) |
| R-14 | LLM nie rozstrzyga sygnału | „LLM nie wydaje decyzji „wartościowy / niewartościowy” ani nie ocenia nowości” | `prd.md:311` | enforced | schemat bez takich pól; walidator (R-11) | — |
| R-15 | jakość oceny | „Jakość danych z LLM musi być wysoka”; miara: „udział nieudanych prób … osobno dla każdego źródła … Wykop 5 %, Reddit 25 %” | `prd.md:50,271` | **enforced** (jako miara i sygnał FR-033) | app `AssessmentQuality.java:54-63,159-170`; progi `Source.java:18,25`; alert `ASSESSMENT_QUALITY` `InternalPipelineRunController.java:123-125` | miara tylko odczytem („nothing here is stored” `AssessmentQuality.java:28`); sygnał tylko przy przejściu (R-47) |
| R-16 | koszt LLM | „Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe.” | `prd.md:51` | **declared** | pośrednio: R-13, wyłączniki T-47; tokeny w podsumowaniu nocy bez kosztu w pieniądzu (`prd.md:293`) | pułap tylko na kluczu OpenRouter, poza repo; decyzja „bez osobnego pułapu” (`roadmap.md`, Open Roadmap Question 4) |
| R-17 | zbiory zamknięte | „append-only — never rename or remove a constant” | `assessor/CLAUDE.md` | enforced | `test/persistence/ClosedSetsIT.java:52-60` + CHECK | — |
| R-38 | język tez | „`statement` jest w języku posta”; „reguła obowiązuje bez wyjątków, także dla Reddita. Walidator jej nie sprawdza” | `contract-v2.md:261-262` | **declared** (z decyzji Q-16) | prompt `system-prompt.md:52`; pomiar S-36 `AssessmentQuality.java:63`; próg Reddit 30 % (`Source.java:25`) | na Reddicie okna 14,01–20,76 % tez w innym języku (`prd.md:272`), poniżej progu 30 %, więc alarm milczy; Q-19 |
| R-39 | tag kanału | „**Tagi kanału** — wartości `item.channels` oraz tagi … — nigdy nie są celem” | `contract-v2.md:221` | **declared** | tylko prompt `system-prompt.md:21`, który wymienia wyłącznie tagi Wykopu (#gielda, #gieldowegownowpisy, #finanse, #inwestowanie); prompt bez zmian od F-02 (`git log`: 663c687) | walidator nie ma reguły o kanale (`grep -i channel` w `api/agents/assessor/validation/`: 0); nie mierzone; na Reddicie `item.channels` to nazwa subredditu. Q-20 |

### Przebieg (agregat: `PipelineRun` z krokami i wynikami źródeł)

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-18 | kolejka | „jeden naraz, 409 przy aktywnym” | `S-05/plan-brief.md:12` | enforced | data `uq_pipeline_runs_one_running` `mig/V3:39`; app `PipelineRunService.java:216-226` | baza dopuszcza wiele `queued` |
| R-19 | przejścia stanu | „Every state change is one conditional `UPDATE` whose row count says whether it happened” | `pipeline/CLAUDE.md:404` | enforced | app `RunRecorder.java:63-126,140-158,203-213,377-388`, `PostTransitions.java` | **zamknięcie przebiegu nie jest jedną transakcją**: `fail` i `releaseAll` to dwa zapisy (`PipelineRunService.java:497-498`). Gdy drugi zawiedzie, posty zostają `assessing` do następnego żądania startu albo restartu (`PostTransitions.java:174-180`); wynik posta i liczniki przebiegu też osobno (`ConcurrentAssessmentDispatcher.java:81-86`, alert `RUN_COUNTERS_LOST`) |
| R-20 | wyłączniki serii | „Trzy takie awarie z rzędu kończą przebieg `failed`” | `prd.md:283,286` | enforced | app `ConcurrentAssessmentDispatcher.java:54-57,121,127,135` | — |
| R-41 | seria odmów | „trzy odmowy dostawcy LLM z rzędu … kończą przebieg `failed`”; posty „wracają bez prób” | `prd.md:292` | **enforced** | app `ConcurrentAssessmentDispatcher.java:54-57`; `RunSequence.java:215-218` | — |
| R-21 | strażnik i sieroty | „a run without progress for `PIPELINE_STALL_TIMEOUT` … is stopped `failed`” | `prd.md:280` | enforced | app `RunWatchdog.java:71-113`; sieroty `PipelineRunService.java:240-271` | sieroty sprzątane tylko przy żądaniu startu, bez timera („The API asks the database nothing more while idle” `PipelineRunService.java:76`) |
| R-22 | „wymaga uwagi” | „Reguła „wymaga uwagi” zostaje jedna (S-22): różni się tylko kanał, nie powód.” | `prd.md:293` | enforced | app `PipelineRun.java:316-352`; SPA tylko tekst `web/runs.ts:104` | utrata licznika może zgubić powód (R-19) |
| R-23 | powiadomienie z każdego końca | „Whoever ends a run reports it to `RunNotifications.runEnded`” | `pipeline/CLAUDE.md` | **enforced** | strażnik testowy `RunEndingGuard` (S-39, `evidence/3-code-rules.md`, Unknowns: refleksja); `RunNotifications.java:109-122` | — |
| R-24 | świeżość | „Datę źródła przesuwa tylko przebieg zakresu zakończony `succeeded` albo `partial`, w którym wynik tego źródła to `succeeded`” | `prd.md:232` | enforced | app `FreshnessController.java:48`, `PipelineRunRepository` | treść zapytania `findLastUpdates` niesprawdzona w tym przebiegu |
| R-25 | dni bez danych | dzień pokryty „tylko wtedy, gdy przebieg `succeeded` albo `partial` ma dla tego źródła wynik `succeeded`”; „Nic nie uzupełnia ich automatycznie” | `prd.md:284,287` | enforced | app `MissingDays.java:48,55,62-68`; `PipelineRunRepository.java:113-117,133-136` | — |
| R-26 | przebieg dzienny | „Przy `failed` albo `interrupted` cron kończy się błędem”; `partial` kończy go sukcesem | `prd.md:279` | enforced | `trigger.sh:357-361,380-389`; tylko produkcja `trigger.sh:26-29` | — |

### Operator, kopia danych, jakość, kontrakty

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-27 | sygnał awarii | „Operator otrzymuje jednoznaczny, widoczny sygnał, gdy zaplanowany dzienny przebieg … nie powiedzie się lub zakończy częściowym błędem” | `prd.md:278` | enforced | dzwonek `RunNotifications.java:109-122`; alert `RunNotifications.java:115-136`; kod wyjścia crona `trigger.sh:357-361` | kolejka alertów porzuca ponad limit z WARN (`OperatorAlertQueue.java:196-200`) |
| R-42 | martwy post bez alarmu co przebieg | „Sam martwy post nie daje wiadomości; daje dzwonek i podsumowanie nocy” | `prd.md:289,293` | **enforced** | app `RunNotifications.java:134-136` | — |
| R-47 | alarm jakości tylko przy przejściu | „sygnałem jest osobny alarm operatora (`ASSESSMENT_QUALITY`) tylko przy przejściu przez próg … a nie co noc” | `prd.md:271` | enforced | app `QualityCrossing.java:13`, `AssessmentQuality.java:241-248` | utracony alarm się nie powtarza; `/admin` i podsumowanie nocy pokazują stan (decyzja S-35 F5, `deploy-plan.md` S-35) |
| R-48 | podsumowanie nocy | wysyłane „**zawsze**, także przy 0 martwych” | `prd.md:293` | enforced | `trigger.sh:242-246`; `InternalPipelineRunController.java:112-127`; `NightSummary.java:124` | treść `NightSummary` (620 linii) nieczytana w tym przebiegu |
| R-49 | kanał zdarzeń API | „Kanał zdarzeń API to ntfy.” | `prd.md:293` | enforced | `api/alerts/NtfyAlerts.java:32`; crony nadal przez healthchecks.io (`trigger.sh:13,83`), zgodnie z `prd.md:293` | — |
| R-28 | kopia bazy | „Każda kopia jest sprawdzana pełnym odtworzeniem”; „Utracić można najwyżej dane z ostatniej doby.” | `prd.md:302` | enforced | `backup.sh:238,256-284,330-337`; `restore-check.sh:107-114` | kopia na NAS poza repo |
| R-29 | retencja bezterminowa | dane „przechowywane bezterminowo” | `prd.md:302` | enforced | brak pisarzy usuwających (lista pisarzy `evidence/3-code-rules.md`) | kaskady `ON DELETE` bez pisarza; wygasłe linki aktywacyjne i powiadomienia bez retencji |
| R-43 | kontrakt SPA ↔ API | „A new enum value with a label, a new error code, or a changed white space set or limit changes `rules.json` and both sides in the same PR” | `REPOSITORY_GUIDELINES.md` (Coding Style) | enforced | testy `SpaRulesTest`, `web/contract/rules.test.ts`, `shapes.test.ts`, `mockApi.test.ts` (S-41–S-43; `context/map/evidence/3-structure.md`) | treść testów nieczytana w tym przebiegu |

### Feed

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-37 | oznaczenie źródła | „każdy post w feedzie i na stronie szczegółów jest oznaczony źródłem i kanałem” | `S-29/plan-brief.md` | enforced | `web/labels.ts:84`; `PostController.java` | — |
| R-51 | widoczność tylko ocenionych | „Tylko `assessed`; inny etap i nieznane id dają 404 `post_not_found`” | `S-03 post-detail-view/plan-brief.md:29` | enforced | app `PostController.java:93,126-130`; `PostRepository.java:123-130,137-143` | — |

### Konta i dostęp (Generic, tylko polityki produktu)

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-30 | deny-by-default i role | „Każda chroniona funkcja lub zasób konsekwentnie wymaga uwierzytelnienia” | `prd.md:299` | enforced | app `SecurityConfig.java:100-108`; testy `AuthorizationCompletenessIT.java:31-39`, `AdminRoleIT.java:31-36` (zbiory z mapowań, S-40) | zapisy bez nagłówka `Origin` przechodzą (`OriginGuardFilter.java:47`; cookie `SameSite=Strict` `SessionConfig.java:41`) |
| R-31 | link jednorazowy | „Zaproszony użytkownik może użyć linku dokładnie raz” | `prd.md:77` | enforced | data `mig/V7:11-14`; app `ActivationTokenRepository.java:28`, `ActivationLinks.java:157,168` | wygasłe wiersze zostają (brak kodu usuwającego) |
| R-32 | konto systemowe | „komendy CLI kont (S-07) muszą je odrzucać” | `prd.md:332` | enforced | data `mig/V6:9`; app `AccountDetailsService.java:45`, `ActivationLinks.java:119-121,162-166` | — |
| R-33 | limiter logowań | 5 porażek na e-mail i 20 na IP w 15 min | `szczur-radar-api/README.md` (S-07) | enforced | app `LoginRateLimiter.java:32-36,91-110` | stan w pamięci: restart i usypianie API zerują licznik (`LoginRateLimiter.java:26-27,60-66`) |
| R-34 | sesja 30 dni | „sesja wygasa po 30 dniach bezczynności, a ciasteczko jest odnawiane po każdym udanym, uwierzytelnionym żądaniu API” | `prd.md:152` | enforced | `application.properties:47`, `SessionConfig.java:42`, `SessionCookieRenewalFilter.java:20-31` | sprzątanie wygasłych sesji przez Spring Session JDBC niezweryfikowane |

**Reguły tylko w kodzie** (bez dokumentu; *searched in docs*):

- **Przebieg `POSTS` z nieznanymi, `assessed` albo już ocenianymi id** zostaje zakolejkowany i pomija je bez komunikatu (`AssessmentSelection.java:60-65`). Przebieg złożony z samych takich id kończy się `succeeded` z 0 ocen.
- **Zamknięcie przebiegu w dwóch zapisach** (`PipelineRunService.java:497-498`). Pozostałe końce są w jednej transakcji (`evidence/3-code-rules.md`, Atomicity). Zob. R-19.
- **Limit 7 dni i `to <= dziś`** jest tylko na serwerze (`PipelineRunController.java:188-189`). Formularz ma tylko `required` (`web/views/AdminView.vue:419-420`).
- **`create-user --role admin` omija blokadę pierwszego admina** (`AdminBootstrap.java:57-60`).
- **`BACKUP_ACCEPT_SHRINK` nigdy nie znosi `posts > 0`** (`backup.sh:238`).
- **Brak okien potwierdzenia** dla przywrócenia martwych i ponowienia (`grep confirm(` w `web/views`, `web/components`: 0).
- **NUL jest usuwany z tekstów posta** (`Post.java:149,152-157`), a błędy są obcinane do 2000, 500 i 300 znaków (`RunRecorder.java:42`, `PostTransitions.java:37`, `RunNotifications.java:43`).

Ekspozycje nieudokumentowane: nie znaleziono. `/api/deploy-safety` jest publiczne, ale ujawnia jeden bit i powód; opisuje je `REPOSITORY_GUIDELINES.md` (Deployment, Deploy guard).

## Model vs code drift

Otwartych rozbieżności nie ma. Wszystkie 10 z poprzedniego przebiegu jest zamkniętych (*Changes since the previous run*), a w tym przebiegu nie znaleziono nowej sprzeczności między najnowszym zdaniem dokumentów a kodem.

Najbliżej rozbieżności są trzy reguły *declared*: R-38, R-39 i R-16. Kod robi to, co dokumenty każą, ale niczym tego nie wymusza. Są w rankingu, a nie w tej tabeli.

## Stale documents

| Nieaktualne | Zastąpione przez |
|---|---|
| `prd.md:148` (FR-029 „np. 5 minut”) | `prd.md:152` i nagłówek v3 `prd.md:19` (dawne D-02) |
| `shape-notes.md:122` („zakres dat/godzin”) | `prd.md:84` (dawne D-03) |
| `prd.md:157-160` (FR-015: FR-022 warunkowo) | `prd.md:163` (dawne D-10) |
| `prd.md:223` (jedna data świeżości) | `prd.md:232` (S-29) |
| `prd.md:231,281` (pusty przebieg `failed` przy jednym źródle) | `prd.md:287` (S-20) |
| `prd.md:280` („Kody wyjścia crona się nie zmieniają”) | `prd.md:281` |
| `prd.md:282,285` (sygnał przez healthchecks.io → Telegram, check `api-events`) | `prd.md:293` (ntfy, S-34) |
| `prd.md:285,280` (martwy post daje `RUN_NEEDS_ATTENTION`) | `prd.md:289,293` |
| `prd.md:6` (`updated: 2026-11-04`) | bloki „Doprecyzowanie” z 2026-11-11 i 2026-11-12 (`prd.md:271-272`) |
| `S-02 first-assessed-wykop-feed/plan.md:107` (`dead` nieodwracalny, „próba” = każdy zapisany `failed`) | `prd.md:273` (FR-034), `prd.md:283` (S-09) |
| `contract-v2.md:474` (V1: tezy po polsku) | `contract-v2.md:261-262` |
| `verification-scope.md:1` („M-4 (kandydat)”) | `roadmap.md:92,435` (M-4 zamknięty 2026-11-15) |
| `lessons.md:19` (PITR) | F-01 `prd.md:302` |
| `lessons.md:82` (Embabel FIRE_ONCE) | S-17 `pipeline/CLAUDE.md:3` |
| `shape-notes.md:333,367` (kopia jako odpowiedzialność platformy) | `prd.md:302-303,341` |

Kotwice `prd.md:NNN` w `roadmap.md:97,190,215` i w `ui-ux-proposals.md:48` są nieaktualne po dopisaniach PRD v3:
- sygnał dla operatora jest dziś w `prd.md:278`;
- nowe źródło w `prd.md:295`;
- autoryzacja w `prd.md:299`;
- Non-Goals entity resolution w `prd.md:344`.

## Ranking

| # | candidate | kind | subdomain | status | related IDs | reason | done when |
|---|---|---|---|---|---|---|---|
| 1 | tag kanału jako cel | policy | Core: ocena | declared | R-39, T-09, T-18, B-04, Q-20 | jedyny strażnik to prompt z przykładami tylko z Wykopu; nic nie mierzy naruszeń, a na Reddicie kanałem jest nazwa subredditu | na ocenach z wywołaniem modelu z ostatnich 7 dni na produkcji zapytanie o cele, których `anchor.quote` (bez `#`, wielkość liter pominięta) równa się kanałowi posta, zwraca 0 wierszy albo udział, który PRD nazywa dopuszczalnym |
| 2 | język tez | policy | Core: ocena | declared | R-38, T-21, T-73, Q-19 | reguła „bez wyjątków” jest łamana w 14–21 % tez na Reddicie, a próg alarmu (30 %) jest nad tym poziomem, więc sygnał milczy | PRD albo kontrakt nazywa 30 % poziomem przyjętym na stałe, albo udział tez w innym języku na Reddicie w oknie 7 dni spada poniżej progu, który kontrakt wskazuje jako docelowy |
| 3 | koszt LLM | policy | Core: ocena | declared | R-16, T-67 | jedyny pułap jest poza repo (klucz OpenRouter); wyczerpanie limitu zatrzymuje oba źródła naraz | podsumowanie nocy albo `/admin` pokazuje koszt nocy w pieniądzu, albo PRD zapisuje, że limit na kluczu jest jedynym strażnikiem i wyczerpanie zatrzymuje oba źródła |
| 4 | zamknięcie przebiegu w dwóch zapisach | aggregate | Supporting: przebiegi | enforced (z luką) | R-19, R-22, R-21 | przesunięte nad #5: tu leży strefa ryzyka mapy projektu (stan postów); posty mogą zostać `assessing` do następnej nocy, a utracony licznik gubi powód „wymaga uwagi” | test, który psuje drugi zapis zamknięcia, kończy się przebiegiem `failed` i żadnym postem w `assessing` dla tego przebiegu |
| 5 | przejścia stanu tylko w aplikacji | aggregate | Supporting: przebiegi | enforced | R-18, R-19, R-02 | baza dopuszcza wiele `queued` i każde przejście etapu | `UPDATE pipeline_runs SET status = 'running'` na przebiegu `succeeded` jest odrzucany przez bazę, albo decyzja, że strażnik aplikacyjny wystarcza, stoi w `pipeline/CLAUDE.md` |
| 6 | limiter logowań | policy | Generic: dostęp | enforced | R-33 | usypiane API zeruje licznik | decyzja, że zerowanie przy uśpieniu jest akceptowalne, stoi w dokumentach, albo próby przeżywają restart |
| 7 | retencja i kaskady bez pisarza | policy | Generic: konta | enforced | R-29, R-31, Q-11 | kaskady istnieją, ścieżki usuwania nie ma; wygasłe linki zostają | PRD nazywa usuwanie konta i sprzątanie wygasłych linków jako poza zakresem albo jako wymaganie |
| 8 | pola bez czytelnika | code-only | Core: zbieranie | undocumented (bez ekspozycji danych) | Q-12, T-09 | `tags`, `slug`, `content_hash` zapisywane bez konsumenta; `content_hash` obiecuje wykrywanie edycji, którego nie ma | każde pole ma czytelnika w kodzie albo zapisaną decyzję „usunąć” lub „zostawić” |

**#1: tag kanału jako cel (R-39).** To jedyna reguła Core kontraktu, której nie pilnuje ani walidator, ani pomiar. Prompt (`system-prompt.md:21`, bez zmian od F-02) wymienia tylko tagi Wykopu, a na Reddicie `item.channels` niesie nazwę subredditu (`prd.md:298`, `contract-v2.md:221`).
M-4 domknął pozostałe reguły jakości miarą (R-15, R-38), ale tej nie objął: nie wiadomo, czy „stocks” albo „ValueInvesting” stają się celami.
M-5 planuje „Kartę podmiotu”, widok agregujący posty po celu (`ui-ux-proposals.md:17,48`), więc takie cele trafiłyby wprost do nowego UI. Ranking na tym się kończy.

## Questions

| id | question | who can answer | unblocks |
|---|---|---|---|
| Q-08 | Kto i po co woła `/api/admin/llm/check`? | operator | code-only (trasa bez klienta w SPA) |
| Q-11 | Czy usuwanie lub deaktywacja konta ma istnieć (kaskady są, pisarza nie ma) i czy sprzątać wygasłe linki aktywacyjne? | właściciel produktu | R-29, R-31, #7 |
| Q-12 | Do czego mają służyć `content_hash`, `slug`, `tags`, skoro nikt ich nie czyta, a Reddit wypełnia je inaczej? | właściciel produktu | #8 |
| Q-15 | Czy feed ma domyślnie pokazywać posty `not_investment_related` i wpisy bez treści (wynik odsiewu, `unclear`)? Dokumenty milczą, a celem produktu jest wyjście z szumu. | właściciel produktu | R-13, code-only; M-5 |
| Q-19 | Czy 30 % tez w innym języku niż post na Reddicie to poziom przyjęty na stałe, czy tylko próg alarmu przed naprawą promptu? | właściciel kontraktu (ścieżka §16 `contract-v2.md`) | R-38, #2 |
| Q-20 | Czy nazwa subredditu może być celem (np. post o r/stocks), czy obowiązuje dosłownie §6.4 „nigdy”? Czy mierzyć to tak jak język tez? | właściciel kontraktu | R-39, #1 |

## Unknowns and limitations

- **Dokumenty:**
  - Agent przeczytał w całości PRD v3, kontrakt V2, `verification-scope.md` i `ui-ux-proposals.md`.
  - Archiwa: 18 plan-briefów z 58 archiwów. Archiwa M-4 S-31, S-34, S-39–S-45 tylko po tytule.
  - `roadmap.md` bez `## Done`; `deploy-plan.md` tylko wybrane fragmenty.
  - `REPOSITORY_GUIDELINES.md` agent czytał z kontekstu sesji, czyli ze starszej kopii. Cytaty z niego podaję bez numerów linii, po nazwie bloku. Sprawdzone ręcznie: wersja na `ef56ddc` opisuje już `NtfyAlerts` (S-34).
- **Kod nieczytany w tym przebiegu:**
  - ciała reguł D1–D15, `StanceTable`, `OutputParser`;
  - `NightSummary.java` i `AssessmentQuality.java` poza stałymi;
  - klienci LLM i `TransportRetry`;
  - `nas-retention.sh`, `mock-api.js`, widoki `FeedView`, `PostDetailView`, `RunDetailView`, `LoginView` (tylko grep);
  - ciała większości testów.

  R-11, R-17, R-23, R-43 i R-48 opierają się na istnieniu i wpięciu strażników, nie na lekturze ich asercji. Czy strażnicy łapią błędy, mierzył M-4 (PIT, scenariusze), nie ten raport.
- **Statusy niepewne:**
  - R-39: brak pomiaru, więc skala naruszeń jest nieznana;
  - R-24: zapytanie `findLastUpdates` niesprawdzone;
  - R-44: stan z poprzedniego przebiegu.
- **Konfiguracja poza repozytorium, której nie obejmuje żadne pytanie:**
  - sprzątanie sesji przez Spring Session;
  - harmonogram `nas-retention.sh` (DSM);
  - reguła konta Railway „deployment crashed”;
  - konta ntfy, healthchecks.io, Telegram, OpenRouter, Wykop;
  - limit na kluczu OpenRouter;
  - wiarygodność `X-Real-IP` (zależy od krawędzi).
- **M-5:** propozycje UI (`ui-ux-proposals.md:17-42`) wprowadzają słowa bez definicji w PRD: „karta podmiotu”, „wątek dnia”, „proporcje stanowisk”. Granica „rozpoznawanie spółek … poza zakresem” (`prd.md:344`) pozostaje otwarta wobec widoków agregujących po celu.
