---
artifact: domain-distillation
created: 2026-11-01
repo_root: /Users/krzysztofmacioszek/Develop/Projects/10xDevs/szczur-radar-worktrees/pre-m4-discovery
code_scope: szczur-radar-api/, szczur-radar-web/, tools/pipeline-trigger/, tools/db-backup/
head: 5b4ac92
dirty: tylko context/ — context/domain/ i context/archive/2026-10-27-1829-domain-distillation/ (to uruchomienie), context/map/ i context/archive/2026-10-27-repo-map/ (równoległa sesja mapy projektu); kod czysty
path_alias: api/ = szczur-radar-api/src/main/java/pl/szczurradar/api/; web/ = szczur-radar-web/src/; mig/Vn = plik migracji n w szczur-radar-api/src/main/resources/db/migration/; prd.md, roadmap.md, lessons.md = context/foundation/; contract-v2.md, agent-implementation.md = context/foundation/llm-assessment/; deploy-plan.md = context/deployment/; pipeline|sources|llm|assessor/CLAUDE.md = api/<pakiet>/CLAUDE.md (assessor: api/agents/assessor/); api/README.md = szczur-radar-api/README.md; trigger|backup/README.md = tools/<narzędzie>/README.md; S-NN/ = folder zmiany wycinka w context/archive/ (tabela w evidence/1-docs-language.md)
documents: 8 requirements, 45 narrative (23 read w całości, 22 z 41 folderów zmian dotknięte), 19 reference, 5 engineering
previous: context/archive/2026-10-27-1829-domain-distillation/domain-distillation.md
evidence: context/domain/evidence/
glossary: context/domain/glossary.md
---

# Domain distillation: Szczur Radar

## Summary

Domena to **ocenione wypowiedzi inwestycyjne z forów**: produkt codziennie pobiera posty z Wykopu (#gielda) i, od 2026-11-01 na produkcji, z trzech subredditów przez archiwum Arctic Shift, opisuje je modelem językowym według zamkniętego kontraktu V2 (trafność, cele ze stanowiskiem autora, tezy z dosłownym cytatem) i pokazuje zaproszonym osobom jako przeszukiwalny feed; człowiek, nie model, rozstrzyga, czy post jest wartościowym sygnałem (`prd.md:288`). Core to nadal trzy poddomeny: zbieranie i wspólny model, ocena treści oraz przegląd ocenionych postów; przebiegi i sygnał dla operatora są Supporting, konta i dostęp Generic. Z 69 wierszy terminów Core i Supporting 4 są nieobecne w kodzie produktu (zestaw wzorcowy, horyzont Arctic Shift, megawątek, budżet LLM), a 9 istnieje tylko niejawnie, bez własnego typu. M-3 zamknął poprzedniego kandydata #1: awaria lub cisza źródła jest teraz jego wynikiem, a nie porażką przebiegu (R-05 *enforced*, D-01 zamknięte), i zamknął 6 z 10 rozbieżności oraz 9 z 12 pytań. Najsilniejsza rozbieżność jest dziś inna: reguła kontraktu „`statement` w języku posta” nie ma strażnika, a pomiar S-28 dał 22,1 % tez po polsku na postach angielskich (D-14). Kandydat #1 to jakość oceny bez żywej ochrony (R-15): guardrail produktu sprawdzany jest tylko pomiarem poza CI, a Reddit właśnie wszedł z 17,3 % nieudanych prób wobec 2,3 % Wykopu, bez miary jakości na danych produkcyjnych. Reguł *contradicted* i ekspozycji *undocumented* nie znaleziono.

Rozbieżności według strony naprawy: kod 2, dokumentacja 6, decyzja 2; otwartych pytań 9.

## Changes since the previous run

Dopasowanie po identyfikatorach raportu z 2026-10-27 (`head 3f744c6`).

**Zamknięte rozbieżności**

- D-01 (niezależność źródeł): `api/pipeline/step/SourceIngest.java:100-139` zamienia awarię i pustkę źródła na jego wynik; `prd.md:275` potwierdza, że Reddit nie zmienił silnika, oceny ani API.
- D-04 (druga kopia „wymaga uwagi” w SPA): powody liczy tylko API (`api/pipeline/run/PipelineRun.java:316-347`), SPA daje im tekst (`web/runs.ts:1-3`), a brak tekstu dla kodu wykrywa `rules.test.ts` (S-22).
- D-05 (roadmapa zawyżała S-17): sprostowane w `roadmap.md:79`.
- D-06 (trasa „failed posts” w złym pakiecie): `pipeline/CLAUDE.md:50-51` opisuje to już poprawnie.
- D-08 (feed bez oznaczenia źródła): `web/labels.ts:84`, `web/components/PostCard.vue:25`, decyzja `prd.md:243`.
- D-09 (wyjątek i konfiguracja w pakiecie Wykopu): `api/sources/UnmappableItemException.java` leży we wspólnym pakiecie. Zostało: `WykopConfiguration.text` obok `SettingText` oraz `IllegalStateException` zamiast `InvalidSettingsException`, opisane wprost w `api/settings/InvalidSettingsException.java:11-12`, więc to nie jest już rozbieżność.

**Zmiany statusu**

- R-05 declared → **enforced**.
- R-17 declared → **enforced** (`ClosedSetsIT`, S-23).
- R-06 enforced → **declared**: dowody z tego uruchomienia pokazują ścieżkę bez licznika (D-15), której poprzedni raport nie widział jako luki w tej regule.
- R-24 zmienia treść: świeżość jest per źródło (`prd.md:227`), status bez zmian.
- D-07: *decision* → **docs**. Decyzję `is_self → post` podjął właściciel (`S-27/plan.md:7`); została tylko niezaktualizowana linia kontraktu.

**Rozstrzygnięte pytania**

- Q-01: Arctic Shift, trzy subreddity (`prd.md:242`, `roadmap.md:84`).
- Q-02: oznaczenie i świeżość per źródło (`prd.md:227,243`).
- Q-03: limit $10/mies., bez osobnego pułapu (`S-28/change.md:18`).
- Q-04: pomiar nie jest bramką (`roadmap.md:265`).
- Q-05: powody z API (`REPOSITORY_GUIDELINES.md:50`).
- Q-06: Reddit bierze udział w każdym przebiegu zakresu bez wskazanych źródeł (`deploy-plan.md:2022`).
- Q-07: `is_self → post`, inaczej `link` (`prd.md:242`).
- Q-09: jedna reguła w kopiach pilnowanych testem (S-25, `tools/pipeline-trigger/trigger.sh:55-59`).
- Q-10: jedna replika w IaC (`.railway/railway.ts:120`).

**Nowe:** terminy T-61…T-69, granice B-11…B-15, reguły R-35…R-45, rozbieżności D-11…D-16, pytania Q-13…Q-18.

## Ubiquitous language

Terminy Core (C) i Supporting (S). Definicje to cytaty ze źródeł: dokumenty po polsku, instrukcje dla agentów po angielsku. Lokalizacje z `evidence/1-docs-language.md` i `evidence/2-code-language.md`.

| id | term | kind | definition (quoted) | source | in code (path:line) or **missing in code** | other names in code |
|---|---|---|---|---|---|---|
| T-01 | źródło (danych) | entity / closed set (C) | „A source (Wykop, Reddit; others later) only **acquires** elements and **maps** them to the shared model” | `sources/CLAUDE.md:3-4` | `api/sources/Source.java:9`; `posts.source` `mig/V3:86`, `pipeline_run_sources.source` `mig/V12:36` | `Source` koliduje z `Provenance.Source` (B-03); `HttpRetry.source` to `String` |
| T-02 | element źródła | value (C) | „elements that cannot become a post are counted as `skipped`, never thrown” | `sources/CLAUDE.md:16` | `api/sources/wykop/WykopItem.java:25`, `api/sources/reddit/RedditItem.java:27`; `SourceFetcher.Fetched` `api/sources/SourceFetcher.java:36` | `item`, `entry`, `RedditSearchResult.Element` |
| T-03 | wspólny model | value (C) | „mapuje je do wspólnego modelu” | `prd.md:36` | `api/sources/SourcePost.java:27` (zapis), `api/agents/assessor/contract/AssessmentInputV1.java:13` (ocena); słowo tylko w komentarzach | `SourcePost` + `ItemType` |
| T-04 | pozyskanie i mapowanie | operation (C) | „Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu” | `prd.md:272` | `api/sources/SourceFetcher.java:26`, `api/sources/RawInputMapper.java:19`, `api/sources/wykop/WykopMapper.java:49`, `api/sources/reddit/RedditMapper.java:50` | `fetch`, `ingest` |
| T-05 | post | entity (C) | „LLM analizuje semantykę pojedynczej wypowiedzi (`item`), nie surowy payload źródła.” | `contract-v2.md:25` | `api/posts/Post.java:37`; `mig/V3:84`; klucz `mig/V4:16` | `wpis`, `item`, `entry`, `SourcePost` (B-01) |
| T-06 | typ posta (`item_type`) | closed set (C) | „posts store the shared model's item type (`post`/`link`/`comment`), never a source's own type names” | `REPOSITORY_GUIDELINES.md:11` | `api/agents/assessor/contract/ItemType.java:10`; `mig/V4:15` | UI „wpis”, „znalezisko”, „komentarz” `web/labels.ts:3` (D-12) |
| T-07 | znalezisko | value (C) | „wpis Wykopu → `post`, znalezisko Wykopu → `link`” | `contract-v2.md:70` | `ItemType.LINK`, `api/sources/wykop/WykopResource.java:14`; etykieta także dla Reddita `web/labels.ts:3` | B-11 |
| T-08 | komentarz | value (C, poza MVP) | „MVP ocenia wyłącznie posty; komentarze to jawnie odroczone rozszerzenie na przyszłość.” | `prd.md:317` | `ItemType.COMMENT` `ItemType.java:10`, `mig/V4:15` — bez producenta (`Context.NONE` `WykopMapper.java:65`, `RedditMapper.java:56-57`) | `Context.parentComment` |
| T-09 | kanał posta | value (C) | „kanał źródła w jego pisowni — tag Wykopu (historyczne posty Wykopu dostały `gielda`) albo nazwa subredditu” | `prd.md:275` | `posts.channel` `mig/V13:10`; `api/sources/SourcePost.java:27`; `api/agents/assessor/contract/Item.java:16` | `tag`, `subreddit`, `channels` (B-04) |
| T-10 | duplikat / deduplikacja | policy (C) | „System odrzuca duplikaty postów po identyfikatorze źródłowym” | `prd.md:89` | `mig/V4:16`; `api/pipeline/step/PostStore.java:61-63` | — |
| T-11 | RAW (surowy payload) | value (C) | „Oryginalny payload źródłowy każdego zebranego posta pozostaje dostępny bez utraty informacji” | `prd.md:282` | `api/raw/RawPayload.java:23`, `api/raw/RawPayloadStore.java:17,25`; `mig/V3:65` | UI „Dane dla nerdów” `web/views/PostDetailView.vue:179` |
| T-12 | zakres dat (D-2…D-1) | value (S) | „Przebieg dzienny obejmuje dni D-2 i D-1 według kalendarza warszawskiego, ze wszystkich skonfigurowanych źródeł” | `trigger/README.md:5` | bez typu: `api/pipeline/api/InternalPipelineRunController.java:65-71`, `ZONE` `api/pipeline/PipelineRunService.java:97` | `RangeTarget` `api/pipeline/flow/RunFlow.java:55` |
| T-13 | pominięty element (`skipped`) | measure (S) | „`skipped` to pobrane elementy, z których nie powstał post” | `trigger/README.md:66` | `SourceFetcher.Fetched.skipped` `api/sources/SourceFetcher.java:36`; `api/sources/UnmappableItemException.java` | B-12 |
| T-14 | ocena (próba oceny) | entity (C) | „Normatywny kontrakt pojedynczej oceny treści przez LLM” | `contract-v2.md:13` | `api/posts/Assessment.java:55`; `mig/V3:130`; `api/agents/assessor/AssessmentResult.java:26` | B-02, B-15 |
| T-15 | kontrakt oceny V2 | policy (C) | „Dokumenty pomocnicze nie rozszerzają kontraktu; przy sprzeczności obowiązuje ten plik” | `contract-v2.md:377` | `api/agents/assessor/contract/AssessmentOutputV2.java:16`; `szczur-radar-api/src/main/resources/assessor/` (schemat, prompt v2.5) | B-14 |
| T-16 | wejście oceny | value (C) | „Do modelu NIE trafiają jako osobne pola: źródło, autor / nick, zaangażowanie, URL” | `contract-v2.md:75` | `AssessmentInputV1.java:13`, `Item.java:15-17`; `assessments.input` `mig/V3:136` | — |
| T-17 | trafność (`relevance`) | closed set (C) | „autor mówi o rynku, spółce, aktywie, sektorze lub makro w kontekście ceny, wyceny, transakcji, portfela lub wyników” | `contract-v2.md:135-137` | `api/agents/assessor/contract/Relevance.java:10` | UI `web/labels.ts:4` |
| T-18 | cel (`target`) | value (C) | „Cel to podmiot (spółka, aktywo, sektor, temat), nie teza” | `contract-v2.md:27` | `.../contract/Target.java:11`, `TargetKind.java:10`; tylko JSONB `assessments.output` | koliduje z `RangeTarget`, „link post's target” |
| T-19 | anchor | value (C) | „najkrótszy ciągły fragment tekstu nazywający cel, dokładnie jak w tekście” | `contract-v2.md:219` | `.../contract/Target.java:11`; filtr `api/posts/PostRepository.java:112-118` | `q`, `quote`, UI „Spółka lub temat” `web/components/FeedFilter.vue:56` |
| T-20 | stanowisko (`stance`, `direction`) | value (C) | „Stanowisko jest osobne dla każdego celu; ton emocjonalny ≠ stanowisko inwestycyjne. Brak globalnego sentymentu posta” | `contract-v2.md:26` | `.../contract/Stance.java:13`, `Direction.java:10`; `api/agents/assessor/validation/StanceTable.java:11` | „sentyment”, UI `web/components/StanceText.vue:18` |
| T-21 | teza (`claim`) | value (C) | „Tezy (`claims`) opisują, co autor twierdzi; każda ma dokładnie jeden dosłowny cytat z `item`” | `contract-v2.md:28` | `.../contract/Claim.java:14`, `ClaimKind.java:10` | B-09 |
| T-22 | cytat (`evidence`) | policy (C) | „Cytaty są sprawdzane przez backend jako podciąg wskazanego pola po normalizacji białych znaków (§10) — i niczym więcej” | `contract-v2.md:29` | `api/agents/assessor/validation/RuleId.java:12`; `QuoteIsFragmentRule.java:27-29` | UI „zmyślony cytat” `web/labels.ts:69` (zob. R-11) |
| T-23 | normalizacja białych znaków | policy (C) | „Nic więcej: bez zmiany wielkości liter, bez usuwania markdownu” | `contract-v2.md:297` | `api/text/Whitespace.java:17-29`; `web/feedFilter.ts:24`; rejestr `web/contract/rules.json:2-5` | — |
| T-24 | flagi, modyfikatory | closed sets (C) | „Niepewność jest jawna (`unclear`, `hedged`, flagi); model nie zgaduje” | `contract-v2.md:30` | `.../contract/Flag.java:10`, `Modifier.java:10` | UI „Zastrzeżenia” `web/components/PostAssessment.vue:72` |
| T-25 | klasa błędu oceny | closed set (C) | „Pełna lista klas” | `contract-v2.md:361` | `api/agents/assessor/validation/FailureClass.java:11`; `mig/V3:162` | `timeout` nieosiągalny (D-11) |
| T-26 | walidator domenowy D1–D15 | policy (C) | „Klasa wyniku = klasa pierwszego naruszenia” | `contract-v2.md:339-357` | `validation/RuleId.java:12`, `ContractV2Rules.java:30`, `RuleBasedValidator.java:40` | — |
| T-27 | pochodzenie oceny (provenance) | value (C) | „`output` (ten JSON od modelu, bez zmian) obok `provenance` z polami `contract_version` i `prompt_version`” | `contract-v2.md:108` | `api/agents/assessor/Provenance.java:43`; `mig/V3:143-151` | — |
| T-28 | źródło wyniku (`model` / `prefilter`) | closed set (C) | „źródło wyniku: model \| prefilter” | `contract-v2.md:430` | `Provenance.java:49-50`; `result_source` `mig/V3:142` | B-03 |
| T-29 | wpis bez treści / odsiew | policy (C) | „jeśli wynik ma **mniej niż 3 znaki** (code points), wpis jest „bez treści”” | `contract-v2.md:315` | `api/agents/assessor/Prefilter.java:20,35` | UI „bez treści, bez oceny modelu” `PostCard.vue:28` |
| T-30 | konfiguracja produkcyjna (dostawca, model, tier) | value (C) | „`openai/gpt-6-luna`, prompt v2.5” | `roadmap.md:257` | `api/llm/LlmProviderProperties.java:60`, `Provider`, `ServiceTier`, `PROMPT_VERSION` `api/agents/assessor/prompt/AssessmentPrompt.java:39` | `Channel` (B-04) |
| T-31 | zestaw wzorcowy / progi modelu | policy (C) | „~120 zanonimizowanych postów #gielda z dwóch dni” | `agent-implementation.md:102` | **missing in code** produktu: tylko `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/` (poza jarem); szukane w `api/` | pominięty cel, próg kierunku |
| T-32 | feed / lista postów | view (C) | „Użytkownik widzi listę przetworzonych postów, domyślnie posortowaną chronologicznie” | `prd.md:106` | bez typu: `api/posts/PostRepository.java:98`, `api/posts/api/PostController.java:85`, `web/views/FeedView.vue:173` | UI „Posty” (słowo „feed” tylko w nazwach kodu) |
| T-33 | filtr (spółka/temat, rodzaj celu, stanowisko) | operation (C) | „po spółce/temacie (dopasowanie tekstowe do fragmentu, którym post wskazuje cel” | `prd.md:124` | `api/posts/PostFilter.java:18`; `PostController.java:88-90`; `web/feedFilter.ts:41-45` | `q`/`quote` |
| T-34 | widok szczegółowy posta | view (C) | „Widok pokazuje pełny tekst posta, a pod nim ocenę” | `S-03/plan-brief.md:17` | `api/posts/api/PostController.java:100`, `web/views/PostDetailView.vue:155` | — |
| T-35 | świeżość danych („Dane zaktualizowane”) | measure (C) | „Datę źródła przesuwa tylko przebieg zakresu zakończony `succeeded` albo `partial`, w którym wynik tego źródła to `succeeded`” | `prd.md:227` | `api/pipeline/run/PipelineRunRepository.java:82,98`; `api/pipeline/api/FreshnessController.java:34,48,57`; `web/views/FeedView.vue:176` | „aktualizacja” (B-10) |
| T-36 | przebieg (pipeline run) | entity (S) | „A run is a plain sequence of steps in Java” | `pipeline/CLAUDE.md:3` | `api/pipeline/run/PipelineRun.java:33`; `mig/V3:7` | „przepływ”, „uruchomienie”, `run` |
| T-37 | rodzaj przebiegu | closed set (S) | „`RANGE` (zakres dat), `RETRY` (resztki, zawsze przed `RANGE`), `POSTS` (zaznaczone id)” | `S-02/plan-brief.md:29` | `api/pipeline/run/RunKind.java:13`; `mig/V3:29` | B-07 |
| T-38 | status przebiegu | closed set (S) | „Przebieg, w którym jedno źródło padło” → `partial` (S-20) | `prd.md:271` | `api/pipeline/run/RunStatus.java:13`; `mig/V3:30`; `tools/pipeline-trigger/trigger.sh:206-213` | B-05 |
| T-39 | krok przebiegu | entity (S) | „RANGE: fetch → store → select → assess → summarize; RETRY / POSTS: select → assess → summarize” | `pipeline/CLAUDE.md:64` | `api/pipeline/run/RunStep.java:10`, `PipelineRunStep.java:21`; `mig/V3:43` | TS `RunStep` = wiersz, Java `RunStep` = nazwa |
| T-40 | przebieg dzienny (cron) | event (S) | „Przebieg dzienny obejmuje dni D-2 i D-1 według kalendarza warszawskiego” | `trigger/README.md:5` | bez typu: `InternalPipelineRunController.java:65`, `PipelineRun.scheduled()` `:295`, `api/user/SystemAccount.java:13`, `tools/pipeline-trigger/trigger.sh:3` | `daily`, `scheduled`, „cron”, `SCHEDULER` |
| T-41 | ręczne wywołanie przebiegu | operation (S) | „Administrator może ręcznie wywołać nowy przepływ na żądanie z poziomu dashboardu” | `prd.md:186` | `api/pipeline/api/PipelineRunController.java:98`; UI `web/views/AdminView.vue:296` | — |
| T-42 | resztki (leftovers) | state (S) | „resztki: posty `STORED` oraz `FAILED` z `assessment_attempts < 3`” | `S-02/plan.md:248` | bez typu: `api/posts/PostRepository.java:38-45` | UI „ponowienie resztek” `web/labels.ts:46` |
| T-43 | próba oceny (policzona) | measure (S) | „Próba liczy się tylko wtedy, gdy zapisano wiersz `assessments` ze statusem `FAILED`” | `S-02/plan.md:107` | `posts.assessment_attempts` `mig/V3:100`, `Post.java:40`, `PostTransitions.java:203-209` | B-02 |
| T-44 | post martwy (`dead`) | state (S) | „post bez poprawnej oceny po trzech próbach” | `S-28/report-2026-10-31.md:128` | `api/posts/PostStage.java:16`; UI „martwy” `web/views/AdminView.vue:431` | B-13 |
| T-45 | błąd techniczny | measure (S) | „is a technical error of its post: the post goes back without an attempt” | `deploy-plan.md:1358-1359` | `posts.technical_failures` `mig/V10:9`; `RunFlow.java:209` | `processing_errors`, `ProcessingError` |
| T-46 | wstrzymany post (on hold) | state (S) | „Trzy błędy techniczne posta z rzędu (pojedyncze awarie albo błędy naszego kodu) wstrzymują post” | `prd.md:267` | bez stanu: `Post.java:46`, `PostRepository.java:43-45`, `api/posts/api/FailedPostController.java:92` | UI „wstrzymany” `AdminView.vue:432` |
| T-47 | awaria dostawcy / bezpiecznik | policy (S) | „Trzy takie awarie z rzędu kończą przebieg `failed`” | `prd.md:267` | `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:112,118` | „breaker” |
| T-48 | ponowienie transportu | policy (S) | „Ponawiane mogą być wyłącznie błędy transportu” | `contract-v2.md:369` | `api/llm/transport/TransportRetry.java:98-124`; `api/sources/transport/HttpRetry.java:42` | B-07 |
| T-49 | strażnik (watchdog) | policy (S) | „A run of this instance without activity for `PIPELINE_STALL_TIMEOUT` (production: `39m`) ends `failed`” | `deploy-plan.md:735` | `api/pipeline/RunWatchdog.java:93-113`; `api/pipeline/PipelineStallSettings.java:42-51` | — |
| T-50 | przyczyna przerwania | closed set (S) | „nobody executed it any more (its instance was lost without closing it)” | `pipeline/CLAUDE.md:131-140` | `api/pipeline/run/InterruptionCause.java:12-28`; `mig/V11:10-11` | — |
| T-51 | pusty przebieg | event (S) | „przebiegu, w którym **żadne** źródło nie dało posta” | `prd.md:271` | `api/pipeline/step/SourceIngest.java:136-138`, `NoPostsFetchedException.java:22-27` | — |
| T-52 | „wymaga uwagi” | policy (S) | „`runEnded` notifies only a run with a non-empty `PipelineRun.attentionReasons()`” | `pipeline/CLAUDE.md:259-260` | `PipelineRun.java:316-350`; `api/pipeline/run/AttentionReason.java:11`; tekst `web/runs.ts:93` | `RUN_NEEDS_ATTENTION`, `run_attention` |
| T-53 | kolejka / jeden przebieg naraz | policy (S) | „Przebiegi trafiają do kolejki i idą po jednym” | `api/README.md:109` | bez typu: `PipelineRunService.java:136-137,217-223`, `mig/V3:39` | `PipelineBusyException` |
| T-54 | sieroty (`lost`) | policy (S) | „all or none, the queue being one” | `pipeline/CLAUDE.md:405` | `PipelineRunService.java:181`; `api/pipeline/state/RunRecorder.java:325` | `orphan`, `lost`, „zombie”, `dead` run (B-13) |
| T-55 | dni bez danych | measure (S) | „A day is covered for a source only when a `succeeded` / `partial` range run has a `succeeded` result for that source” | `deploy-plan.md:1937` | `api/pipeline/run/MissingDays.java:45,79`; `PipelineRunController.java:154` | `CoveredRange`, pokrycie |
| T-56 | powiadomienie (w aplikacji) | entity (S) | „one row per admin with `login_enabled`” | `pipeline/CLAUDE.md:264` | `api/notifications/Notification.java:19`; `mig/V9:6`; `web/components/NotificationBell.vue:95` | dzwonek |
| T-57 | alert operatora / kanał alarmowy | event (S) | „sygnał poza aplikacją daje usługa healthchecks.io, która alarmuje w Telegramie” | `prd.md:266` | `api/alerts/AlertEvent.java:8`, `OperatorAlertQueue.java:53`, `HealthchecksAlerts.java:34` | B-04, B-08 |
| T-58 | operator | role (S) | „Administrator (operator wdrożenia) — zarządza dostępem” | `prd.md:31` | bez typu: tylko `api/alerts/OperatorAlerts.java:18`; `api/user/Role.java:15` nie ma takiej roli | B-06 |
| T-59 | kopia bazy | entity (S) | „Każdy dump trafia tam dopiero wtedy, gdy w całości odtworzy się w tymczasowej bazie” | `backup/README.md:5` | `tools/db-backup/backup.sh:3`, `restore-check.sh:9`, `tables.sh:17` (tylko bash) | manifest |
| T-60 | konto systemowe | role (S) | „Jest zleceniodawcą przebiegu dziennego. Ma `login_enabled = false` i nie ma hasła” | `prd.md:309` | `api/user/SystemAccount.java:10-13`; `mig/V6:8-12` | — |
| T-61 | wynik źródła | entity (S) | „`succeeded` (it gave at least one post), `empty` (it answered, but gave no post…) or `failed` (it threw…)” | `deploy-plan.md:1930` | `api/pipeline/run/PipelineRunSource.java:26`, `SourceFetchStatus.java:14`; `mig/V12:33`; UI „Wynik per źródło” `web/views/RunDetailView.vue:236` | `SourceFetch`, `SourceResultView`, `sourceStatus` |
| T-62 | źródło zarejestrowane / skonfigurowane | state (C) | „skonfigurowanym źródłem jest wtedy, gdy `REDDIT_SUBREDDITS` wymienia co najmniej jeden subreddit” | `prd.md:275` | `Source.java:9` + `mig/V12:25-44`; `api/sources/SourceFetcher.java:26`; `SourceIngest.java:48-60` | `source_not_configured` |
| T-63 | post zdjęty / skasowany | state (C) | „to stan posta w źródle, nie treść: nie są elementami pobrania” | `api/README.md:101` | bez wspólnego typu: `RedditItem.java:42-44`, `RedditMapper.java:30,33`; tylko Reddit | `removed`, `[deleted]` |
| T-64 | pochodzenie posta | value (C) | „każdy post w feedzie i na stronie szczegółów jest oznaczony źródłem i kanałem” | `prd.md:243` | `web/labels.ts:84`; `PostItem` `api/posts/api/PostController.java:144` | `postOrigin`, `Wykop · #gielda` |
| T-65 | horyzont Arctic Shift | policy (S) | „Arctic Shift czyta oficjalne Data API Reddita, które Reddit zamyka publicznie w marcu 2027” | `prd.md:242` | **missing in code**: runbook `deploy-plan.md:2097-2118`; szukane w `api/`, `web/` | — |
| T-66 | megawątek | value (C) | „tytuł pasujący do wzorca pilota” | `S-28/report-2026-10-31.md:131` | **missing in code**: tylko raport i pomiar; filtra w produkcie brak (szukane w `api/`, `web/`) | Daily/Weekly thread |
| T-67 | budżet LLM | policy (C) | „limit $10/mies. zostaje, bez osobnego pułapu” | `S-28/change.md:18` | **missing in code**: limit żyje na kluczu OpenRouter; w kodzie tylko reakcja na wyczerpanie (`llm/CLAUDE.md:53-61`) | — |
| T-68 | trzy winy | policy (S) | „*the post's fault*”, „*the provider's fault*”, „*our code's fault*” | `pipeline/CLAUDE.md:318-324` | bez typu: `api/agents/assessor/Assessor.java:131-136`, `ConcurrentAssessmentDispatcher.java:308-358`, `TransportException.java:81-90` | `CONFIGURATION`, `REJECTED` |
| T-69 | aktywność przebiegu | measure (S) | „Set by the start, every counted post, every post whose call found the provider” | `pipeline/CLAUDE.md:84-87` | `pipeline_runs.last_activity_at`, `RunRecorder.java:140-158,272-274` | postęp |

Liczby: w dowodach dokumentów ok. 90 terminów, w tabeli 69 wierszy Core i Supporting. **Brak w kodzie produktu: 4** — polityki T-31, T-65, T-67, wartość T-66. **Niejawne (lokalizacja bez własnego typu lub nazwy): 9** — T-12, T-32, T-40, T-42, T-46, T-53, T-58, T-63, T-68. Terminy Generic (konta i dostęp): 12 — wszystkie z lokalizacją w `api/auth/**`, `api/user/**`, `api/cli/**`; **właściciel** to rola planistyczna bez znaczenia w działaniu produktu. Terminy tezy (poza zakresem, nie liczone): „wartościowy sygnał”, szum, rozpoznawanie spółek, trend, „Ask szczur”.

Pojęcia tylko w kodzie (*searched in docs* = szukane w dokumentach czytanych przez agenta, `evidence/1-docs-language.md`):

- `posts.tags`, `posts.slug`, `posts.content_hash` — zapisywane, bez czytelnika w `api/` (`api/posts/Post.java:150,212`). `tags` i `slug` to pojęcia Wykopu we wspólnym modelu: Reddit daje `List.of(subreddit)` i `null` (`api/sources/reddit/RedditPosts.java:53-54`). Cel `content_hash` tylko w komentarzu `mig/V3:97`. *Searched in docs*.
- `Context.parentPost/parentComment` — zawsze `Context.NONE`. *Searched in docs*: `contract-v2.md:226`.
- `llm.Channel`, `OutputMode` — bez definicji w czytanych dokumentach poza `llm/CLAUDE.md:8`. *Searched in docs*.
- `deployment_id` (`mig/V8:13`) — tekst, bez typu. *Searched in docs*.
- „najnowsza ocena” — reguła tylko w SQL (`api/posts/AssessmentRepository.java:29-35`, `api/posts/PostRepository.java:113`). *Not in the docs sample*.
- autor (`posts.author` `mig/V3:92`) — bez tożsamości. *Searched in docs*.
- `/api/admin/llm/check` — trasa bez klienta w SPA; `api/README.md:134` wspomina nieudany check. Q-08.

## Multiple meanings and context boundaries

| id | word | meaning A (context, source) | meaning B (context, source) | suggested names |
|---|---|---|---|---|
| B-01 | post / wpis | każda zebrana jednostka (`api/README.md:130`, encja `Post`) | wartość `item_type = post`; u Wykopu „wpis”, a kontrakt mówi „wpis” o każdym `item` (`contract-v2.md:70,317`); do tego `entry` w asesorze (`api/agents/assessor/Assessor.java:31`) | „post” = encja; „typ posta” = wartość pola |
| B-02 | próba (`attempt`) | policzona próba oceny (`pipeline/CLAUDE.md:320`) | wiersz `assessments`, także `ok` (`api/posts/Assessment.java:35`); próba HTTP (`assessments.attempts`, `llm/CLAUDE.md:47`); wywołanie `Assessor.assess` w raporcie S-28 (`S-28/report-2026-10-31.md:127`) | „próba oceny”, „wiersz oceny”, „wywołanie” |
| B-03 | źródło / `Source` | Wykop, Reddit (`api/sources/Source.java:9`) | źródło wyniku `model`/`prefilter` (`Provenance.java:49-50`, `contract-v2.md:430`) | „źródło danych” vs „źródło wyniku” |
| B-04 | kanał / `channel` | miejsce w źródle (`prd.md:275`, `Post.channel`) | kanał alarmowy (`prd.md:266`); trasa wywołania modelu (`api/llm/Channel.java:10`, `Assessment.channel`) | „kanał posta”, „kanał alarmowy”, „trasa modelu” |
| B-05 | `failed` | post z próbami do ponowienia (`PostStage.FAILED`) | koniec przebiegu, krok, wynik źródła, wynik oceny, `ExitCode`, dostarczenie alertu; „Nieudane posty” obejmują też `dead` i wstrzymane (`FailedPostController.java:30`) | zawsze z rzeczownikiem: „post po nieudanej próbie”, „przebieg zakończony błędem” |
| B-06 | operator / admin | rola konta (`Role.ADMIN`, `prd.md:303-304`) | człowiek z dostępem do CLI i Railway (`prd.md:213-214`), odbiorca alertów (`prd.md:269`); PRD łączy je w personie (`prd.md:31`) | „admin” = konto, „operator” = rola wdrożeniowa |
| B-07 | ponowienie / retry | przebieg `RETRY` (`web/labels.ts:46`) | „Ponów zaznaczone” tworzy przebieg `POSTS` (`web/api/pipeline.ts:125`, `web/views/AdminView.vue:451`); ponowienia HTTP modelu i źródła | „przebieg resztek”, „przebieg wybranych postów”, „ponowienie wywołania” |
| B-08 | sygnał | sygnał inwestycyjny, poza oceną modelu (`prd.md:286,288`) | sygnał awarii (`prd.md:262`); FR-024 „trendu wykrytych sygnałów” (`prd.md:244`) zakłada wykrywanie, którego produkt nie robi | „sygnał inwestycyjny” vs „alarm” |
| B-09 | teza | każdy `Claim` (`contract-v2.md:28`, „Tezy ogólne” `PostAssessment.vue:57`) | jeden rodzaj `investment_thesis` (`web/labels.ts:27`) | „teza” vs „teza inwestycyjna” |
| B-10 | aktualizacja / świeżość / pokrycie | etykieta feedu per źródło (`prd.md:227`) | pokrycie dnia (`deploy-plan.md:1937`); oba biorą `succeeded`, ale inaczej wybierają przebieg | „świeżość” vs „pokrycie dnia” |
| B-11 | `link` | znalezisko Wykopu (`contract-v2.md:70`) | Reddit: link, obraz, crosspost, także z treścią (`prd.md:242`); link aktywacyjny; link do oryginału | „typ `link`” vs „link aktywacyjny” |
| B-12 | pobrane / `fetched` | w produkcie bez postów zdjętych (`prd.md:242`, `api/sources/SourceFetcher.java:36`) | w raporcie S-28 ze zdjętymi (`S-28/report-2026-10-31.md:17`: 789 pobranych, 393 zdjęte) | liczb „Pobrane” z S-28 nie porównywać z `fetched` |
| B-13 | `dead` | post (`PostStage.DEAD`) | przebieg bez wykonawcy (`api/pipeline/api/DeploySafetyController.java:25`), obok `lost`, `orphan`, `stall` | „martwy post” vs „przebieg-sierota” |
| B-14 | kontrakt | kontrakt oceny LLM (`contract-v2.md:13`) | kontrakt SPA ↔ API (`web/contract/`, `roadmap.md:202`); kontrakt crona (`S-20/plan-brief.md:29`); port źródła (`sources/CLAUDE.md:14`) | „kontrakt oceny”, „kontrakt SPA ↔ API” |
| B-15 | ocena | wynik modelu, opis (`assessment`) | decyzja czytającego (`prd.md:288`); przegląd właściciela (`S-28/report-2026-10-31.md:86`) | „ocena” = opis modelu; „osąd” = człowiek |

## Subdomains

| subdomain | class | concepts | why (goal quote, or description and usage) |
|---|---|---|---|
| Zbieranie i wspólny model | **Core** | T-01…T-11, T-62…T-64, T-66 | „Codzienny przepływ … pobiera posty z Wykopu (#gielda) …, mapuje je do wspólnego modelu, odrzuca duplikaty” (`prd.md:36`); „Drugie źródło danych — Reddit (wybrane kanały) — dochodzi jako kolejne źródło w tym samym pipeline.” (`prd.md:42`) |
| Ocena treści (kontrakt V2) | **Core** | T-14…T-31, T-67 | „Aplikacja opisuje znaczenie każdego zebranego posta tak, by użytkownik mógł szybko ocenić, czy jest wartościowym sygnałem” (`prd.md:286`); „Jakość danych z LLM musi być wysoka” (`prd.md:48`) |
| Przegląd ocenionych postów | **Core** | T-32…T-35, T-64 | „Użytkownik widzi przetworzone posty w aplikacji webowej” (`prd.md:37`); persona sięga po narzędzie, „gdy chce w krótkim czasie zorientować się, czy w ostatnich postach pojawiło się coś wartego uwagi” (`prd.md:27`) |
| Przebiegi i ich niezawodność | Supporting | T-12, T-13, T-36…T-55, T-61, T-68, T-69 | potrzebne i specyficzne (kolejka, próby, resztki, strażnik, wyniki źródeł, dni bez danych), ale nie tu produkt wygrywa; M-2 i część M-3 (`roadmap.md:90`) |
| Sygnał dla operatora i kopia danych | Supporting | T-56…T-60, T-65 | „Operator otrzymuje jednoznaczny, widoczny sygnał” (`prd.md:262`); kopia to osobny serwis (`prd.md:318`) |
| Konta i dostęp | Generic | konto, rola, sesja, link aktywacyjny, CLI | logowanie e-mailem i hasłem, role `user`/`admin`, sesje; specyficzna jest tylko polityka linku przez CLI bez poczty (`prd.md:296`) |

## Invariants and policies

Status: **enforced**, **declared**, **ignored**, **contradicted**, **undocumented**. „Strzeżone w” i „luka” z `evidence/3-code-rules.md`.

### Post (agregat: identyczność `(source, item_type, source_item_id)`, cykl `stored → assessing → assessed | failed → dead`)

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-01 | Post | „System odrzuca duplikaty postów po identyfikatorze źródłowym”; „Treściowo podobne posty od różnych autorów NIE są odrzucane” | `prd.md:89` | enforced | data `uq_posts_source_item` `mig/V4:16`; app `PostStore.java:61-63` | blok `catch` łapie więcej niż duplikaty (R-06) |
| R-02 | Post | „the third makes the post `dead`”; „Próbę zużywa tylko odmowa dostawcy albo zła odpowiedź modelu” | `pipeline/CLAUDE.md:319-320`, `prd.md:267` | enforced | data `ck_posts_stage_attempts`, `ck_posts_assessing_owner` `mig/V3:116-122`; app `PostTransitions.java:91-109,203-209` | przejście etapów tylko w app (baza przyjmie `assessed → failed`) |
| R-03 | Post | „Trzy błędy techniczne posta z rzędu (pojedyncze awarie albo błędy naszego kodu) wstrzymują post”; „Każdy zapisany wynik oceny (udany albo nieudany) zeruje licznik” | `prd.md:267` | enforced | app `PostRepository.java:38-45`, `PostTransitions.java:96`; data `mig/V10:11` (tylko `>= 0`) | bez własnego stanu (T-46); POSTS bierze taki post z założenia |
| R-04 | Post | „a `dead` one stays `dead`” | `deploy-plan.md:1412` | enforced | app `PipelineRunService.java:198-201`, `AssessmentSelection.java:63-64`, brak pisarza z `dead` (`evidence/3-code-rules.md`, Writers) | nieodwracalność jest też ceną R-41 |
| R-44 | Post | „so a retry gets exactly the input of the first attempt” | `sources/CLAUDE.md:17` | enforced | app `api/pipeline/assess/AssessmentInputs.java:32-38` | — |

### Źródło i pozyskanie

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-05 | niezależność źródeł | „Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu; warstwa oceny, zapisu i udostępniania danych pozostaje niezmieniona”; „**A source's failure or silence is its own result** (S-20)” | `prd.md:272`, `pipeline/CLAUDE.md:367-374` | enforced | app `SourceIngest.java:100-139`; zapis `RunRecorder.java:140-158`; kod `pipeline/` nie nazywa źródła (`evidence/2-code-language.md`); dowód `prd.md:275` | NFR spełniony dosłownie: prompt i walidator bez zmian, cena to jakość na Reddicie (R-15); `posts.tags`/`slug` to pojęcia Wykopu |
| R-06 | element, który nie staje się postem | „elements that cannot become a post are counted as `skipped`, never thrown” | `sources/CLAUDE.md:16` | **declared** | app `api/sources/wykop/WykopFetcher.java:48-55`, `api/sources/reddit/RedditFetcher.java:48-55` | ścieżka zapisu: `PostStore.java:76-78` połyka każde `DataIntegrityViolationException` jako duplikat; post ginie bez logu i licznika. D-15. Nie ustalono danych, które dziś to wywołają |
| R-07 | pusty przebieg | „przebiegu, w którym **żadne** źródło nie dało posta” kończy się `failed` | `prd.md:271` | enforced | app `SourceIngest.java:136-138`, `NoPostsFetchedException.java:22-27` | — |
| R-08 | ponowienia i kompletność | „return an incomplete result as complete” to czego repozytorium nie wolno; „A source bounds its fetch with a deadline below `PipelineStallSettings.MIN_DEFAULT`” | `sources/CLAUDE.md:82`, `pipeline/CLAUDE.md:90` | enforced | app `api/sources/transport/HttpRetry.java:42-48,112-156`, `WykopRepository.java:61,123`, `RedditRepository.java:60,115,196-203` | Wykop bez `pagination.total`: krótka strona kończy odczyt bez kontroli (`WykopRepository.java:198-203`) |
| R-09 | RAW | „RAW JSON z zewnętrznych źródeł zawsze przechowuj oddzielnie od znormalizowanych rekordów aplikacyjnych. Zachowaj RAW bezstratnie” | `lessons.md:16` | enforced | data `mig/V3:78,107`; app `api/raw/PostgresRawPayloadStore.java:43-45` | — |
| R-35 | post zdjęty | „Posty zdjęte przez moderację albo Reddit i skasowane przez autora nie są pobierane ani liczone jako pominięte elementy; ich liczbę podaje tylko log” | `prd.md:242` | enforced | app `RedditRepository.java:187-190`, `RedditItem.java:42-44` | druga kopia reguły węższa (`RedditMapper.java:44-46`); Wykop nie ma odpowiednika (szukane w `sources/wykop/`) |
| R-36 | retencja Reddita | „post zdjęty albo skasowany na Reddicie po pobraniu zostaje w bazie i w RAW” | `prd.md:281` | enforced | brak ścieżki usuwania (`evidence/3-code-rules.md`, Writers) | decyzja „bez analizy prawnej” (`prd.md:281`); poza kodem |
| R-40 | rejestracja źródła | „cztery zbiory: `posts.source`, `raw_payloads.source`, `pipeline_runs.sources` i `pipeline_run_sources.source`” | `prd.md:274` | enforced | data `mig/V12:25-44`; test `ClosedSetsIT`; `web/contract/rules.json:31` | — |
| R-45 | typ posta bez słów źródła | „never a source's own type names” | `REPOSITORY_GUIDELINES.md:11` | **declared** | data `mig/V4:15`; `RedditMapper.java:50` | SPA nazywa typy słowami Wykopu także na Reddicie (`web/labels.ts:3`, `PostCard.vue:26`). D-12 |

### Ocena (agregat: `Assessment` niezmienny po zapisie; kontrakt V2 jako polityka)

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-10 | ocena | „Wynik niespełniający kontraktu nie jest poprawną oceną (`status != ok`).” | `agent-implementation.md:94` | enforced | data `ck_assessments_outcome` `mig/V3:165-167`; app `AssessmentResult.java:45-50`, `Assessment.java:64-140` (`updatable=false`) | baza przyjęłaby `UPDATE` |
| R-11 | cytat i reguły D1–D15 | „Cytaty są sprawdzane przez backend jako podciąg wskazanego pola po normalizacji białych znaków (§10) — i niczym więcej”; „backend nie „naprawia” odpowiedzi po cichu” | `contract-v2.md:29,368` | enforced | app `RuleId.java:12`, `ContractV2Rules.java:30-35`, `QuoteIsFragmentRule.java:27-29`, `OutputParser.java:68-94` | nazwa myli: 17 z 30 prób z D6 różni się tylko białymi znakami i znacznikami markdown (`S-28/report-2026-10-31.md:115`), a UI mówi „zmyślony cytat” (`web/labels.ts:69`) |
| R-12 | wejście do modelu | „Do modelu NIE trafiają jako osobne pola: źródło, autor / nick, zaangażowanie, URL, ID źródła” | `contract-v2.md:75` | enforced | typ `Item.java:15-17`; `WykopMapper.java:65`, `RedditMapper.java:56-57` | subreddit idzie w `channels` (D-16) |
| R-13 | odsiew | „Wpis bez treści nie trafia do modelu” | `contract-v2.md:317` | enforced | app `Prefilter.java:20,27-29,35-44`, `Assessor.java:72-75` | kod zgodny z §11; kontrakt nie zna `r/…`, `u/…` |
| R-14 | LLM nie rozstrzyga sygnału | „LLM nie wydaje decyzji „wartościowy / niewartościowy” ani nie ocenia nowości” | `prd.md:288` | enforced | schemat bez takich pól, parser ścisły `OutputParser.java:68-94` | — |
| R-15 | jakość oceny | „Jakość danych z LLM musi być wysoka — ekstrakcja/klasyfikacja bez halucynacji.” | `prd.md:48` | **declared** | tylko harness poza CI (`szczur-radar-api/pom.xml:30`, `measurement/`); w CI `RecordingsReplayTest` (dryf parsera i walidatora, nie modelu) | brak miary na produkcji; dryf w Parked (`roadmap.md:323`); Reddit: 17,3 % nieudanych prób wobec 2,3 % Wykopu (`S-28/report-2026-10-31.md:119`); progi nierozstrzygnięte (`roadmap.md:257`); „Wynik 0 pominiętych celów … nie dowodzi braku pominięć” (`S-28/report-2026-10-31.md:121`) |
| R-16 | koszt LLM | „Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe.” | `prd.md:49` | **declared** | pośrednio: R-13, jedno wywołanie logiczne, bezpieczniki; limit poza repo | decyzja o braku pułapu (`S-28/change.md:18`); koszt $3,21/mies. na `flex` (`roadmap.md:303`); wyczerpany limit zatrzymuje też Wykop (`S-28/frame.md:32`) |
| R-17 | zbiory zamknięte | „append-only — never rename or remove a constant” | `assessor/CLAUDE.md:23-24` | enforced | `ClosedSetsIT` + `CHECK` | — |
| R-38 | język tez | „`statement` jest w języku posta (post po polsku → po polsku, post po angielsku → po angielsku).” | `contract-v2.md:261` | **declared** | tylko prompt `szczur-radar-api/src/main/resources/assessor/system-prompt.md:52`; walidator tego nie sprawdza („Język tez nie jest sprawdzany” `S-28/frame.md:30`) | 22,1 % tez po polsku (`S-28/report-2026-10-31.md:60`). D-14 |
| R-39 | tag kanału | „**nigdy nie są celem**” | `contract-v2.md:221` | **declared** | tylko prompt `system-prompt.md:21` (wymienia tagi Wykopu) | brak reguły dla subredditów w prompcie; poza walidatorem; nie mierzone na Reddicie w czytanym zakresie |

### Przebieg (agregat: `PipelineRun` z krokami i wynikami źródeł)

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-18 | kolejka | „Przebiegi trafiają do kolejki i idą po jednym”; „409 `pipeline_busy` while a run is queued or running” | `api/README.md:109`, `deploy-plan.md:472` | enforced | data `mig/V3:39`; app `PipelineRunService.java:217-223,274-276` | baza dopuszcza dwa `queued` |
| R-19 | przejścia stanu | „Every state change is one conditional `UPDATE` whose row count says whether it happened” | `pipeline/CLAUDE.md:314-315` | enforced | app `RunRecorder.java` (`evidence/3-code-rules.md`, grupa 1), `PostTransitions.java` | brak `CHECK` przejść w bazie |
| R-20 | bezpieczniki | „Trzy takie awarie z rzędu kończą przebieg `failed`” | `prd.md:267` | enforced | app `ConcurrentAssessmentDispatcher.java:112,118,324-355` | zob. R-41 |
| R-21 | strażnik i sieroty | „No run stays `queued`/`running` without a thread” | `pipeline/CLAUDE.md:388` | enforced | app `RunWatchdog.java:93-113`, `PipelineStartupRecovery.java:84-108`, `RunRecorder.java:325-339` | sieroty tylko przy żądaniu startu (zamierzone: „no timer” `pipeline/CLAUDE.md:419`); `RejectedExecutionException` z `submit` po zamknięciu daje 500 po commicie |
| R-22 | „wymaga uwagi” | „the reasons themselves (the codes in `attentionReasons`) are computed by the API” | `REPOSITORY_GUIDELINES.md:50` | enforced | app `PipelineRun.java:316-350`; `web/runs.ts:1-3`; `rules.test.ts` | utrata licznika może zgubić powód (`ConcurrentAssessmentDispatcher.java:502-514`) |
| R-23 | powiadomienie z każdego końca | „Whoever ends a run reports it to `RunNotifications.runEnded`” | `pipeline/CLAUDE.md:421` | **declared** | konwencja; pięć wywołań (`PipelineRunService.java:270,386,480,551`, `PipelineStartupRecovery.java:113`) | brak strażnika na nową ścieżkę; powiadomienie po commicie bez ponowienia (`RunNotifications.java:68-100`, S-12 w Parked) |
| R-24 | świeżość | „Datę źródła przesuwa tylko przebieg zakresu zakończony `succeeded` albo `partial`, w którym wynik tego źródła to `succeeded`”; „Wyniki `empty` i `failed` jej nie ruszają” | `prd.md:227` | enforced | app `PipelineRunRepository.java:29,82,92-99`, `FreshnessController.java:34-41` | — |
| R-25 | dni bez danych | „a failed or empty source covers nothing”; „Nic nie uzupełnia ich automatycznie i nie ma nowego alarmu” | `pipeline/CLAUDE.md:44`, `prd.md:268` | enforced | app `MissingDays.java:79`, `PipelineRunRepository.java:29,112-117,132-136` | reguła pokrycia w trzech zapytaniach |
| R-26 | przebieg dzienny | „Kod 0, gdy każdy przebieg jest `succeeded` albo `partial`. Kod 1, gdy któryś jest `failed` albo `interrupted`” | `trigger/README.md:34` | enforced | app `InternalPipelineRunController.java:65-71`; `trigger.sh:206-215` | zakres w literale; doba Reddita to doba warszawska przez `Instant` (`PipelineRunService.java:182-183`), co rozstrzyga U-09 dokumentów |
| R-41 | próbę zużywa tylko wina posta | „so a revoked key never uses up posts' attempts” | `llm/CLAUDE.md:53-57` | **declared** | app `TransportException.java:81-90`, `TransportRetry.java:98-105`, `Assessor.java:131-136`; wyjątek OpenRouter 403 budżetu `llm/CLAUDE.md:58-61` | seria odmów `REJECTED` (403, brak tekstu) nie ma wyłącznika, a zapisany wynik zeruje serie (`ConcurrentAssessmentDispatcher.java:298-303`); w trzy noce wszystkie posty mogą stać się `dead` bez drogi powrotu. Częstość: `inference` |

### Operator, kopia danych, kontrakty

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-27 | sygnał awarii | „Operator otrzymuje jednoznaczny, widoczny sygnał, gdy zaplanowany dzienny przebieg zbierania/przetwarzania nie powiedzie się lub zakończy częściowym błędem” | `prd.md:262` | enforced | app dzwonek `RunNotifications.java:68`, alert `OperatorAlertQueue.java:53`, kod wyjścia `trigger.sh:320-322` | alert bez trwałości (`deploy-plan.md:1691`); limit kolejki gubi zdarzenia |
| R-28 | kopia bazy | „Każdy dump trafia tam dopiero wtedy, gdy w całości odtworzy się w tymczasowej bazie” | `backup/README.md:5` | enforced | bash `backup.sh:237-238,272-284,329-337`, `restore-check.sh:91-130`, `tables.sh:17-30` | kopia na NAS poza repo |
| R-29 | retencja bezterminowa | „Dane (posty, konta) są przechowywane bezterminowo w MVP — brak automatycznej retencji/czyszczenia” | `prd.md:279` | enforced | brak ścieżki usuwania (`evidence/3-code-rules.md`, Writers) | kaskady `ON DELETE` bez pisarza; retencja `notifications` nieopisana |
| R-42 | sygnał rzadki, nie co noc | „sygnał przy każdym `partial` byłby co noc; `dead` to 1 na ~1150 postów” | `S-06/plan-brief.md:25` | **declared** | reguła jest w kodzie (`PipelineRun.java:316-347`), ale jej założenie nie | na Reddicie „alarm szedłby zgrubnie w 3 nocach na 4” (`S-28/report-2026-10-31.md:118`). D-13 |
| R-43 | kontrakt SPA ↔ API | „A new enum value with a label, a new error code, or a changed white space set or limit changes `rules.json` and both sides in the same PR” | `REPOSITORY_GUIDELINES.md:43` | enforced | testy `SpaRulesTest`, `web/contract/rules.test.ts`, `web/contract/shapes.test.ts` | treść testów nieczytana |

### Feed

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-37 | oznaczenie źródła | „każdy post w feedzie i na stronie szczegółów jest oznaczony źródłem i kanałem” | `prd.md:243` | enforced | `PostController.java:144`; `web/labels.ts:84`; `PostCard.vue:25` | lista „Nieudane posty” admina nie ma źródła ani kanału (`FailedPostController.java:83`, `web/contract/failed-post.json`); PRD tego nie wymaga |

### Konta i dostęp (Generic — tylko polityki produktu)

| id | candidate | invariant (quoted) | source | status | guarded at | gap |
|---|---|---|---|---|---|---|
| R-30 | deny-by-default i role | „Każda chroniona funkcja lub zasób konsekwentnie wymaga uwierzytelnienia” | `prd.md:276` | enforced | app `api/config/SecurityConfig.java:99-107`; `AuthorizationCompletenessIT`; SPA `web/router/index.ts:68-70` | — |
| R-31 | link jednorazowy | „Zaproszony użytkownik może użyć linku dokładnie raz, żeby ustawić własne hasło” | `prd.md:75` | enforced | data `mig/V7:11-14`; app `ActivationTokenRepository.java:26-29`, `ActivationLinks.java:157-168` | wygasłe wiersze zostają |
| R-32 | konto systemowe | „nigdy się nie zaloguje, a komendy CLI kont (S-07) muszą je odrzucać” | `prd.md:309` | enforced | data `mig/V6:9`; app `AccountDetailsService.java:45`, `ActivationLinks.java:119-121,162-166` | — |
| R-33 | limiter logowań | „5 nieudanych prób / 15 min na e-mail i 20 na IP” | `api/README.md:295` | enforced | app `api/auth/LoginRateLimiter.java:32-36,91-110`; jedna replika `.railway/railway.ts:120` | stan w pamięci: restart i usypianie API zerują; zaufanie do `X-Real-IP` tylko w komentarzu `ClientIp.java:6-8` |
| R-34 | sesja 30 dni | „wygasa po 30 dniach bezczynności, ciasteczko jest odnawiane po każdym udanym, uwierzytelnionym żądaniu API” | `api/README.md:293` | enforced | `application.properties:42`, `SessionConfig.java:34` | czyszczenie wygasłych sesji to mechanizm biblioteki; D-02 |

**Reguły tylko w kodzie** (bez dokumentu; *searched in docs*):

- Feed pokazuje też `not_investment_related` i wynik odsiewu, z etykietą (`web/components/PostCard.vue:27-28`); filtr po celu wyklucza je pośrednio (`PostRepository.java:96`). Q-15.
- `POSTS` z nieznanym id albo postem `assessed` daje 202 i pusty przebieg (`AssessmentSelection.java:60-65`, `mig/V3:13` bez FK).
- Puste `sources` w API = wszystkie skonfigurowane (`SourceIngest.java:49-50`), a formularz wymaga co najmniej jednego (`web/views/AdminView.vue:322`).
- `close` kończy przebieg i zwalnia posty w dwóch zapisach (`PipelineRunService.java:497-498`); pozostałe końce w jednej transakcji.
- Limit 7 dni i 100 postów tylko w kontrolerze admina (`PipelineRunController.java:60,66,184-191`).
- `BACKUP_ACCEPT_SHRINK` nigdy nie znosi `posts > 0` (`backup.sh:237-238`).
- `create-user --role admin` omija blokadę pierwszego admina (`CreateUserCommand.java:46-52`).

Ekspozycje nieudokumentowane: nie znaleziono. `/api/deploy-safety` jest publiczne, ale ujawnia jeden bit i powód i jest opisane w `REPOSITORY_GUIDELINES.md:67`.

## Model vs code drift

Tylko rozbieżności otwarte; zamknięte są w *Changes since the previous run*.

| id | the documents say (newest) | the code does | evidence (path:line) | consequence | fix: code / docs / decision |
|---|---|---|---|---|---|
| D-02 | FR-029: „Sesja użytkownika wygasa automatycznie po określonym czasie bezczynności (np. 5 minut” (`prd.md:145`) | sesja 30 dni bezczynności | `szczur-radar-api/src/main/resources/application.properties:42`; `api/config/SessionConfig.java:34` | PRD opisuje politykę, której kod świadomie nie realizuje | **docs** — decyzja F-03 `roadmap.md:25` (v2.4, 2026-10-02) i `roadmap.md:314` |
| D-03 | „dla zadanego zakresu dat/godzin” (`prd.md:36`, `prd.md:81`) | zakres tylko w dniach | `api/pipeline/PipelineRunService.java:182-183`; `api/pipeline/api/InternalPipelineRunController.java:65-71` | słowo „godzin” nie ma odpowiednika | **docs** — `prd.md:225` (S-05: „przebieg zakresu dat”), `api/README.md:290` |
| D-07 | `contract-v2.md:70`: „post Reddita → `post` (tekstowy) lub `link`” | `is_self → post`, inaczej `link`, także gdy ma treść | `api/sources/reddit/RedditMapper.java:50` | kontrakt jest źródłem prawdy, a nie ma doprecyzowania z PRD | **docs** — decyzja `S-27/plan.md:7`, `prd.md:242` |
| D-10 | FR-022 „opcjonalne — dostępne, jeśli to nice-to-have zostanie zrealizowane” (`prd.md:305`) | zrealizowane | `api/pipeline/api/PipelineRunController.java:98` | wewnętrzna niespójność PRD | **docs** — `prd.md:190` (2026-10-13) |
| D-11 | „`provider_error` (błąd dostawcy, odmowa, błąd transportu po wyczerpaniu prób) i `timeout` (przekroczenie czasu po wyczerpaniu prób)” (`contract-v2.md:359`) | po wyczerpaniu prób powstaje `LlmUnavailableException`, którego `Assessor` nie zapisuje; `timeout` z `CHECK` jest nieosiągalny, a `provider_error` znaczy już tylko odmowę | `api/llm/transport/TransportRetry.java:118-121`; `api/agents/assessor/Assessor.java:131-133`; `mig/V3:162` | wartość zamkniętego zbioru bez producenta; kontrakt opisuje zachowanie sprzed S-09 | **docs** — S-09 `prd.md:267`, `pipeline/CLAUDE.md:318-323` |
| D-12 | „never a source's own type names” (`REPOSITORY_GUIDELINES.md:11`); typ `link` Reddita to „każdy inny (link, obraz, crosspost)” (`prd.md:242`) | każdy post Reddita dostaje słowo Wykopu: self-post to „wpis”, reszta „znalezisko” | `web/labels.ts:3`; `web/components/PostCard.vue:26`; `web/views/PostDetailView.vue:38-40` | karta mówi „Reddit · r/stocks … znalezisko”; dokumenty ani S-29 tego nie rozstrzygają | **decision** → Q-13 |
| D-13 | „sygnał przy każdym `partial` byłby co noc; `dead` to 1 na ~1150 postów” (`S-06/plan-brief.md:25`) | reguła ta sama, a dane inne: „alarm szedłby zgrubnie w 3 nocach na 4” | `api/pipeline/run/PipelineRun.java:316-347`; `S-28/report-2026-10-31.md:118`; `deploy-plan.md:2094` | alarm może przestać znaczyć „zareaguj”; decyzja o włączeniu Reddita tego nie nazywa | **decision** → Q-14 (noce w #121) |
| D-14 | „post po angielsku → po angielsku” (`contract-v2.md:261`); „Reguła kontraktu pasuje tu do oczekiwań właściciela” (`S-28/frame.md:39`) | model pisze po polsku 22,1 % tez na postach angielskich; prompt ma regułę, walidator nie | `szczur-radar-api/src/main/resources/assessor/system-prompt.md:52`; `S-28/report-2026-10-31.md:60`; `contract-v2.md:370` | czytelnik dostaje mieszany język; reguła stoi, a odstępstwo jest przyjęte jako „nie są warunkiem” (`S-28/change.md:19`) | **code** — reguła obowiązuje, naprawa świadomie odłożona (`roadmap.md:342`); Q-16 |
| D-15 | „elements that cannot become a post are counted as `skipped`, never thrown” (`sources/CLAUDE.md:16`) | `catch (DataIntegrityViolationException duplicate)` łapie też `CHECK` i `NOT NULL`; post znika: `fetched` go liczy, `skipped` i `new_posts` nie, przebieg może być `succeeded` | `api/pipeline/step/PostStore.java:71-78` | cicha utrata posta w Core; R-27 jej nie widzi | **code** |
| D-16 | „Wejściem oceny jest wyłącznie treść posta (tytuł, treść; docelowo też komentarze z kontekstem rodzica)” (`prd.md:288`) | wejście niesie też `item_type`, `published_at`, `channels`; na Reddicie `channels` to subreddit | `contract-v2.md:44-46`; `api/agents/assessor/contract/Item.java:15-17`; `api/sources/reddit/RedditMapper.java:50-57` | PRD mówi mniej, niż model widzi | **docs** — kontrakt ma pierwszeństwo (`contract-v2.md:377`), `sources/CLAUDE.md:92-94` (S-27) |

## Stale documents

- `prd.md:145` (FR-029) ← `roadmap.md:25,314` (D-02).
- `prd.md:36,81` („zakres dat/godzin”) ← `prd.md:225`, `api/README.md:290` (D-03).
- `prd.md:186,305` (FR-022 warunkowe) ← `prd.md:190` (D-10).
- `prd.md:226` (S-08, pusty przebieg) ← `prd.md:271` (S-20); `prd.md:218-220,225` (jedna data) ← `prd.md:227` (S-29); `prd.md:264` („Powiadomień poza aplikacją … nie ma”) ← `prd.md:266,269`. Łańcuch doprecyzowań jest poprawny, ale trzeba go czytać od końca.
- `prd.md:6` (`updated: 2026-10-13`) ← bloki do 2026-10-30; `prd.md:169` („przy pierwszym logowaniu”) ← `deploy-plan.md:601`.
- `contract-v2.md:70` ← `prd.md:242` (D-07); `contract-v2.md:359` ← `prd.md:267` (D-11).
- `REPOSITORY_GUIDELINES.md:3,76`, `deploy-plan.md:2017,2155`, `api/README.md:105` („Reddit poza produkcją”) ← `roadmap.md:84,349`.
- `deploy-plan.md:746,1565` (ton `deploy` neutralny) ← `S-22/plan-brief.md:23`, `web/runs.ts:29,34-38`.
- `deploy-plan.md:1642` (zdanie S-13 o dniu pokrytym) ← `deploy-plan.md:1937`; `deploy-plan.md:1350` (S-09 „not merged”) ← `roadmap.md:370`.
- `roadmap.md:9` (`top_blocker: decisions`) ← `roadmap.md:84`; `roadmap.md:127,241,385` („klucze Reddita”) ← `prd.md:242`; `roadmap.md:249-251,294-296` (otwarte pytania S-27) ← `prd.md:242`, `deploy-plan.md:2022`.
- Kotwice `prd.md:NNN` w `roadmap.md:93` i `ui-ux-proposals.md:48` są rozjechane o kilka linii.
- `lessons.md:86` (Embabel) ← `roadmap.md:75`; `lessons.md:23` (PITR) ← `lessons.md:93`.
- `verification-scope.md:39` (S-24 „do decyzji”) ← `roadmap.md:113`.

## Ranking

| # | candidate | kind | subdomain | status | related IDs | reason | done when |
|---|---|---|---|---|---|---|---|
| 1 | jakość oceny bez żywej ochrony | policy | Core: ocena | declared | R-15, R-38, R-39, D-14, Q-16 | guardrail produktu nie ma miary na produkcji; Reddit na prompcie strojonym pod Wykop daje ok. 7× więcej nieudanych prób (pomiar S-28 wobec produkcji Wykopa) | istnieje liczona z danych produkcyjnych miara jakości per źródło (udział nieudanych prób według klas i udział tez w innym języku niż post), a jej przekroczenie daje operatorowi sygnał bez czytania logów |
| 2 | cichy spadek posta przy zapisie | policy | Core: zbieranie | declared | R-06, R-01, D-15 | utrata danych bez licznika i logu, której żaden sygnał nie widzi | test z postem odrzuconym przez `CHECK` lub `NOT NULL` kończy przebieg z tym postem w `skipped` albo w błędzie, a dla każdego przebiegu `fetched` = nowe posty + duplikaty + `skipped` |
| 3 | seria odmów bez wyłącznika | policy | Supporting: przebiegi | declared | R-41, R-04, R-20, Q-18 | przesunięte nad #4: nieodwracalne `dead` to utrata oceny całego okna przy jednej złej decyzji dostawcy | test z serią odpowiedzi `REJECTED` na wszystkie posty kończy się bez zużycia prób wszystkich postów, albo Q-18 zapisuje w PRD, że to przyjęte ryzyko |
| 4 | słowa Wykopu na postach Reddita | policy | Core: przegląd | declared | R-45, D-12, B-11, Q-13 | jedyne miejsce, gdzie słownictwo jednego źródła wychodzi do użytkownika; koszt zmiany mały, ale potrzebna decyzja | post Reddita na karcie i na stronie szczegółów nie pokazuje „wpis” ani „znalezisko”, albo decyzja Q-13 mówi, że te słowa są słowami produktu i stoi w dokumentach |
| 5 | alarm `dead` po Reddicie | policy | Supporting: operator | declared | R-42, R-27, D-13, Q-14 | założenie S-06 wygasło; pierwsze noce #121 pokażą częstość | PRD zapisuje, ile alarmów `dead` tygodniowo jest dopuszczalne, a noce po włączeniu Reddita mieszczą się w tym limicie |
| 6 | nowy koniec przebiegu bez powiadomienia | policy | Supporting: przebiegi | declared | R-23 | konwencja w pięciu miejscach bez strażnika | test przechodzi przez każdą ścieżkę kończącą przebieg i każda woła `runEnded`, a nowa ścieżka bez tego wywołania czerwieni test |
| 7 | przejścia stanu tylko w aplikacji | aggregate | Supporting: przebiegi | enforced | R-18, R-19, R-21 | baza dopuszcza dwa `queued` i każde przejście | `UPDATE pipeline_runs SET status = 'running'` na przebiegu `succeeded` jest odrzucany przez bazę, albo decyzja, że strażnik aplikacyjny wystarcza, stoi w `pipeline/CLAUDE.md` |
| 8 | limiter logowań | policy | Generic: dostęp | enforced | R-33 | usypiane API zeruje licznik; replika jest już jawna w IaC | decyzja, że zerowanie przy uśpieniu jest akceptowalne, stoi w dokumentach, albo próby przeżywają restart |
| 9 | retencja i kaskady bez pisarza | policy | Generic: konta | enforced | R-29, Q-11 | kaskady istnieją, ścieżki usuwania nie ma | PRD nazywa usuwanie konta jako poza zakresem albo jako wymaganie |
| 10 | pola bez czytelnika | code-only | Core: zbieranie | undocumented (bez ekspozycji danych) | Q-12, T-09 | `tags`, `slug`, `content_hash` zapisywane bez konsumenta; dwa z nich to pojęcia Wykopu | każde pole ma czytelnika w kodzie albo zapisaną decyzję „usunąć” lub „zostawić” |

**#1: jakość oceny bez żywej ochrony (R-15, D-14).** To jedyna polityka Core z guardrailów PRD, której nic nie mierzy po wdrożeniu: progi rozstrzyga harness poza CI, S-28 świadomie ich nie rozstrzygał, a ochrona przed dryfem leży w Parked.
Reddit spełnił NFR dosłownie, bo ocena się nie zmieniła, więc cena wyszła w jakości: 17,3 % nieudanych prób wobec 2,3 % Wykopu i 22,1 % tez w innym języku niż post, przy `dead` skupionym na długich postach.
Właściciel przyjął to na czas życia Arctic Shift; luka zostaje dla Wykopu i każdej zmiany modelu, a M-4 to naturalne miejsce na jej domknięcie. Ranking na tym się kończy.

## Questions

| id | question | who can answer | unblocks |
|---|---|---|---|
| Q-08 | Kto i po co woła `/api/admin/llm/check`? | operator | code-only (trasa bez klienta w SPA) |
| Q-11 | Czy usuwanie lub deaktywacja konta ma istnieć (kaskady są, pisarza nie ma)? | właściciel produktu | R-29, #9 |
| Q-12 | Do czego mają służyć `content_hash`, `slug`, `tags`, skoro nikt ich nie czyta, a Reddit wypełnia je inaczej? | właściciel produktu | #10 |
| Q-13 | Czy „wpis” i „znalezisko” są słowami produktu, czy mają zostać na Wykopie (a Reddit dostaje inne albo żadne)? | właściciel produktu | D-12, R-45, #4 |
| Q-14 | Ile alarmów `dead` na tydzień jest dopuszczalne po włączeniu Reddita i czy `dead` z Reddita ma dalej iść do Telegrama? | właściciel produktu + operator (dane z nocy #121) | D-13, R-42, #5 |
| Q-15 | Czy feed ma domyślnie pokazywać posty `not_investment_related` i wpisy bez treści? Dokumenty milczą, a celem produktu jest wyjście z szumu. | właściciel produktu | code-only, M-5 |
| Q-16 | Czy reguła „`statement` w języku posta” zostaje bez odstępstw po wygaszeniu Reddita i czy naruszenie ma być mierzone? Jaki odsetek jest dopuszczalny? | właściciel kontraktu (ścieżka §16 `contract-v2.md:446-449`) | D-14, R-38, #1 |
| Q-17 | Czy odświeżyć PRD do v3 (`roadmap.md:304`)? Zamknie D-02, D-03, D-10, D-16 i większość *Stale documents*. | właściciel produktu | rozbieżności *docs* |
| Q-18 | Czy `dead` ma mieć drogę powrotu (ponowna ocena po zmianie promptu albo po serii odmów dostawcy)? | właściciel produktu | R-41, R-04, #3 |

## Unknowns and limitations

- **Niezależność agentów kodu:** harness wstrzyknął obu agentom kodu treść `api/pipeline/CLAUDE.md`, a jedno wyszukiwanie zwróciło linie z `api/sources/CLAUDE.md`. Żadne ustalenie nie opiera się na tych plikach, ale wpływu na dobór pytań nie da się wykluczyć; zgodność w obszarze przebiegów jest przez to nieco słabszym dowodem niż w pozostałych.
- **Dane produkcyjne Reddita:** dokumenty kończą się 2026-11-01, dzień włączenia. Liczby jakości pochodzą z pomiaru S-28 (396 postów, inna droga prób niż na produkcji, zanonimizowany tekst), nie z produkcji; noce 2.11 i 4.11 to otwarte issue #121.
- **Dokumenty:** agent dotknął 22 z 41 folderów zmian (23 dokumenty w całości), reszta tylko po tytule; `deploy-plan.md` tylko wybrane sekcje; README `tools/healthchecks`, `tools/deploy-gate`, `tools/ci-runner` nieotwarte. `context/changes/post-m2-audit/` i poprzednie analizy wyłączone celowo.
- **Kod:** `api/llm/**`, `api/alerts/**`, `restore-check.sh`, `nas-retention.sh` i reszta `web/` tylko grepem; testy tylko jako fakt istnienia, bez czytania asercji. R-17, R-22, R-30 i R-43 opierają się na istnieniu testów. Czy te testy łapią błędy, to pytanie M-4, nie tego raportu. `tools/deploy-gate`, `tools/healthchecks`, `.github` poza zakresem.
- **Statusy nierozstrzygnięte:** R-39 (czy subreddit trafia jako cel) bez pomiaru; D-15 — nie ustalono, czy jakiekolwiek dzisiejsze dane wywołują ten spadek; R-41 — mechanika to `evidence`, częstość to `inference`.
- **Konfiguracja poza repozytorium, której nie obejmuje żadne pytanie:** sprzątanie sesji przez Spring Session, wyzwalacz `nas-retention.sh` (harmonogram DSM), reguła konta Railway „deployment crashed”, konta healthchecks.io, Telegram, OpenRouter, Wykop. Wiarygodność `X-Real-IP` zależy od krawędzi.
- **Niepomierzone na Reddicie:** „obiecujący trend” (FR-025) i agregacja po anchorze dla widoków spółki w M-5 (`ui-ux-proposals.md:48`) pozostają otwarte; bez bytu spółki `q` to `ILIKE` po `anchor.quote`.
- **Moment uruchomienia:** równolegle w tym samym worktree działała sesja mapy projektu; jej pliki (`context/map/`) nie były źródłem tego raportu.
