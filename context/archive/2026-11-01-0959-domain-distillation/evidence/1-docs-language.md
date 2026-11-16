# Evidence: language in the documents

Sesja: 2026-11-01, HEAD `5b4ac92`. Źródło: dokumenty z `0-documents.md`, bez kodu. Cytaty ze źródeł stoją w `«…»` i są dosłowne; `…` wewnątrz cytatu oznacza pominięcie. Terminy zostają w języku źródła.

**Oznaczenia.** Każdy wiersz bez znacznika to `evidence` (zapis wprost w dokumencie). `[inference]` to wniosek z zestawienia kilku zapisów, `[unknown]` to rzecz, której dokumenty nie rozstrzygają. Brak wyniku wyszukiwania podaję z zakresem, nigdy jako „nie istnieje”.

**Skróty ścieżek w cytowaniach.**

| skrót | ścieżka |
|---|---|
| `prd.md`, `roadmap.md`, `shape-notes.md`, `lessons.md`, `verification-scope.md`, `ui-ux-proposals.md`, `tech-stack-api.md`, `infrastructure.md` | `context/foundation/` |
| `contract-v2.md`, `examples.md`, `agent-implementation.md`, `hardening-sources.md`, `llm-assessment/README.md` | `context/foundation/llm-assessment/` |
| `deploy-plan.md` | `context/deployment/deploy-plan.md` |
| `pipeline/CLAUDE.md`, `sources/CLAUDE.md`, `llm/CLAUDE.md`, `assessor/CLAUDE.md` | `szczur-radar-api/src/main/java/pl/szczurradar/api/{pipeline,sources,llm,agents/assessor}/CLAUDE.md` |
| `api/README.md` | `szczur-radar-api/README.md` |
| `measurement/README.md` | `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/README.md` |
| `trigger/README.md`, `backup/README.md` | `tools/pipeline-trigger/README.md`, `tools/db-backup/README.md` |
| `S-02/…` | `context/archive/2026-10-06-first-assessed-wykop-feed/` |
| `S-03/…`, `S-04/…`, `S-05/…`, `S-07/…` | `context/archive/2026-10-12-{post-detail-view,filter-by-assessment,daily-scheduled-run-freshness,cli-user-onboarding}/` |
| `S-06/…` | `context/archive/2026-10-15-pipeline-run-history-dashboard/` |
| `S-08/…`, `S-09/…` | `context/archive/2026-10-18-empty-run-signal/`, `context/archive/2026-10-20-attempt-accounting/` |
| `S-12/…`, `S-13/…`, `S-18/…` | `context/archive/2026-10-24-{reliable-run-notifications,missed-night-detection,error-visibility}/` |
| `S-17/…`, `PCR/…` | `context/archive/2026-10-25-pipeline-without-embabel/`, `context/archive/2026-10-27-pipeline-concurrency-review/` |
| `S-22/…`, `S-23/…`, `S-25/…` | `context/archive/2026-10-27-{run-attention-single-rule,closed-sets-guard,alert-ping-single-rule}/` |
| `S-20/…`, `S-24/…`, `S-27/…` | `context/archive/2026-10-29-{source-isolation,api-contract-guard,reddit-source}/` |
| `S-28/…`, `S-29/…` | `context/archive/2026-10-30-{reddit-quality-and-cost,source-aware-feed}/` |
| `F-02/frame.md` | `context/archive/2026-09-27-assessment-contract-harness/frame.md` |

**Dokumenty przeczytane w całości.** Requirements: `prd.md`, `shape-notes.md`, `contract-v2.md`, `examples.md`, `agent-implementation.md`, `roadmap.md`, `verification-scope.md`, `ui-ux-proposals.md`. Reference: `llm-assessment/README.md`, `hardening-sources.md`, `sources/CLAUDE.md`, `assessor/CLAUDE.md`, root `README.md`, `REPOSITORY_GUIDELINES.md` (z kontekstu sesji, numery linii sprawdzone `grep`), `lessons.md`.

**Przeczytane częściowo, w partiach niosących reguły.** `pipeline/CLAUDE.md` (linie 1–523; 524–623 to opis testów, przejrzane `grep` pod kątem reguł, bez trafień), `llm/CLAUDE.md` (1–80), `api/README.md` (Reddit, Pipeline, API i sesja), `trigger/README.md` (1–70), `backup/README.md` (1–70), `measurement/README.md` (140–179). `deploy-plan.md` (ok. 2300 linii): nagłówki całości oraz sekcje S-05, S-06, S-07, S-08, F-04 „How it works”, S-09, S-11, S-13, S-18 „What changes”, S-20, S-27, S-28, S-29. `tech-stack-api.md:40` i `infrastructure.md`: tylko linie z `grep`.

**Próbka narracyjna.** Czytane w całości (23 dokumenty w 19 folderach): `S-28/frame.md`, `S-28/report-2026-10-31.md`, `S-28/change.md`, `S-12/frame.md`, `PCR/change.md`, `PCR/reviews/impl-review.md` oraz `plan-brief.md` zmian S-02, S-03, S-04, S-05, S-06, S-07, S-08, S-09, S-13, S-20, S-22, S-23, S-24, S-25, S-27, S-28, S-29. Częściowo: `plan-brief.md` S-17 (1–45) i S-18 (1–50). Pojedyncze linie z `grep`: `plan.md` S-02, S-04, S-27, S-28, S-29, `S-27/research.md`, `F-02/frame.md:259,290`. Razem 22 z 41 folderów zmian dotkniętych. Pozostałe 19 folderów tylko po tytule; S-11, S-14, S-15, S-16, S-19, S-21, S-26 i F-01–F-04 pokrywają `roadmap.md` (`## Done`) i `deploy-plan.md`.

**Pominięte i dlaczego.** Zakazane przez polecenie: `context/map/`, `context/archive/*-repo-map/`, `context/archive/*-domain-distillation/`, `context/domain/domain-distillation.md`, `context/changes/post-m2-audit/`. Superseded, tylko tytuł: `context/foundation/archive/2026-10-01-llm-assessment-contract-v1.md`, `context/foundation/archive/shape-notes-2026-09-26-1137.md`, `context/changes/bootstrap-verification/` (logi scaffoldu). Bez znaczenia domenowego, nieotwarte: `szczur-radar-web/README.md`, `tools/{healthchecks,deploy-gate,ci-runner}/README.md` (ich reguły biorę z `REPOSITORY_GUIDELINES.md` i `deploy-plan.md`), `tech-stack-web.md`, `llm-assessment` prompt (`system-prompt.md` to zasób kodu).

**Uwaga o identyfikatorach.** Dokumenty M-3 cytują `R-nn`, `D-nn`, `Q-nn` z poprzedniej destylacji. Traktuję je jako ślad decyzji. Dowodem jest tylko zapisana odpowiedź właściciela. Identyfikatory w tym pliku (`T`, `DR`, `L`, `OD`, `M`, `C`, `U`) są lokalne dla tego pliku.

## Terms

### Produkt, ludzie, role

| term | variants | kind | definition (quoted) | source |
|---|---|---|---|---|
| sygnał inwestycyjny | wartościowy sygnał, sygnał, `is_signal`, `is_valuable`, signal decision | thesis | «wśród nich pojawiają się wartościowe sygnały i informacje, ale giną w szumie»; decyzję podejmuje człowiek: «LLM nie wydaje decyzji „wartościowy / niewartościowy” ani nie ocenia nowości — w MVP tę ocenę robi użytkownik na podstawie opisanej struktury» | `prd.md:21`, `prd.md:288`, `contract-v2.md:419-420` |
| szum | — | thesis | «wartościowe sygnały i informacje, ale giną w szumie». Powtórzenia treści szumem nie są: «ich powtarzalność jest sygnałem zainteresowania tematem, nie szumem do odfiltrowania» | `prd.md:21`, `prd.md:89` |
| użytkownik | indywidualny inwestor, persona podstawowa, zaproszona osoba, zaproszony | role | «indywidualny inwestor śledzący dyskusje inwestycyjne na Wykopie i Reddicie (twórca narzędzia oraz zaproszone przez niego osoby)» | `prd.md:27` |
| `user` | rola user | role | «przegląda listę przetworzonych postów i filtruje po podstawowych polach klasyfikacji LLM» | `prd.md:299` |
| `admin` | administrator, Administrator | role | «wszystko co `user`, plus: tworzenie kont z przypisaną rolą przez CLI (FR-026/FR-027/FR-028) oraz» dashboard historii | `prd.md:303-304` |
| operator | Administrator (operator wdrożenia) | role | «Administrator (operator wdrożenia) — zarządza dostępem: tworzy konta dla zaproszonych osób z przypisaną rolą (przez CLI)» | `prd.md:31` |
| właściciel | użytkownik (w planach), właściciel kontraktu, owner | role | w planach M-3 osoba decydująca: «Decyzje właściciela z wywiadu `/10x-plan`» | `S-27/plan.md:7`, `S-29/plan.md:7`, `S-28/change.md:14` |
| konto systemowe | `scheduler@system.invalid`, `SystemAccount.SCHEDULER` | entity | «Jest zleceniodawcą przebiegu dziennego. Ma `login_enabled = false` i nie ma hasła» | `prd.md:309` |
| konto | account | entity | tworzy je tylko administrator: «Brak publicznej rejestracji. Konta tworzy wyłącznie administrator, przypisując rolę» | `prd.md:296` |

### Źródła i pozyskanie

| term | variants | kind | definition (quoted) | source |
|---|---|---|---|---|
| źródło | source, `Source`, platforma źródłowa, Wykop, Reddit | entity | «A source (Wykop, Reddit; others later) only **acquires** elements and **maps** them to the shared model» | `sources/CLAUDE.md:3-4`, `prd.md:272` |
| wspólny model | shared model, `SourcePost`, `AssessmentInputV1` | value | «Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu»; typ posta to «typ wspólnego modelu (`post` / `link` / `comment`), na który źródło mapuje własne typy» | `prd.md:272-273`, `sources/CLAUDE.md:19-25` |
| źródło zarejestrowane | rejestracja źródła | state | «jego stała (`sources/Source`) i dopisanie jej do reguły `CHECK` kolumny `source` jedną migracją»; od S-20 «cztery zbiory» | `prd.md:273`, `prd.md:274` |
| źródło skonfigurowane | configured, nieskonfigurowane, `source_not_configured` | state | «skonfigurowanym źródłem jest wtedy, gdy `REDDIT_SUBREDDITS` wymienia co najmniej jeden subreddit» | `prd.md:275`, `sources/CLAUDE.md:16` |
| kanał | channel, `posts.channel`, `item.channels`, tag, subreddit, wybrane kanały | value | «kanał źródła w jego pisowni — tag Wykopu (historyczne posty Wykopu dostały `gielda`) albo nazwa subredditu» | `prd.md:275`, `sources/CLAUDE.md:92-94`, `contract-v2.md:62` |
| Wykop | #gielda, tag `gielda` | value | «System pobiera posty z Wykopu (tag #gielda) dla zadanego zakresu dat/godzin» | `prd.md:81` |
| wpis / znalezisko | wpis Wykopu, znalezisko Wykopu | value | «wpis Wykopu → `post`, znalezisko Wykopu → `link`, post Reddita → `post` (tekstowy) lub `link`, komentarz → `comment`» | `contract-v2.md:70` |
| Reddit | subreddit, r/stocks, self-post, `is_self`, Arctic Shift | value | «źródłem danych Reddita jest publiczne archiwum Arctic Shift … bez klucza i rejestracji; projekt nie korzysta z API Reddita bezpośrednio» | `prd.md:242` |
| typ posta | `item_type`, `ItemType`, `post`, `link`, `comment` | value | «Self-post Reddita (`is_self`) to typ `post`, każdy inny (link, obraz, crosspost) to `link`, także gdy ma treść» | `prd.md:242`, `contract-v2.md:60` |
| post | posty, element po zapisie | entity | wiersz wspólnego modelu; raz na klucz: «new posts with their raw payload, once per `(source, item_type, source_item_id)`» | `pipeline/CLAUDE.md:66`, `sources/CLAUDE.md:19-21` |
| element | element źródła, `fetched`, pobrane | value | jednostka odczytu przed mapowaniem: «elements that cannot become a post are counted as `skipped`, never thrown» | `sources/CLAUDE.md:16` |
| pominięty element | `skipped`, pominięte | measure | «`skipped` to pobrane elementy, z których nie powstał post» | `trigger/README.md:66` |
| post zdjęty / skasowany | removed, deleted, `removed_by_category`, `[removed]`, `[deleted]` | state | «to stan posta w źródle, nie treść: nie są elementami pobrania (ani `fetched`, ani `skipped`, bo pominięty element dawałby alarm co noc)» | `api/README.md:101`, `prd.md:242` |
| RAW | surowy payload, oryginalny payload, `raw_payloads`, dane dla nerdów, wersja surowa | entity | «Oryginalny payload źródłowy każdego zebranego posta pozostaje dostępny bez utraty informacji, ale jest utrzymywany niezależnie od znormalizowanych danych» | `prd.md:282`, `lessons.md:16` |
| duplikat | deduplikacja, dedup | policy | «System odrzuca duplikaty postów po identyfikatorze źródłowym (ten sam post nie jest przetwarzany dwukrotnie)» | `prd.md:89` |
| wynik źródła | `pipeline_run_sources`, `SourceFetchStatus`, `succeeded`/`empty`/`failed`, Wynik per źródło | entity | «`succeeded` (it gave at least one post), `empty` (it answered, but gave no post: no element in the range, or only skipped ones) or `failed` (it threw; error `<SimpleName>: <message>`)» | `deploy-plan.md:1930`, `prd.md:274` |
| megawątek | Daily/Weekly thread | value | «tytuł pasujący do wzorca pilota» cyklicznych wątków; bez filtra w produkcie | `S-28/report-2026-10-31.md:131`, `S-27/plan-brief.md:28` |
| horyzont Arctic Shift | ryzyko horyzontu, koniec archiwum | policy | «Arctic Shift czyta oficjalne Data API Reddita, które Reddit zamyka publicznie w marcu 2027, a co wtedy stanie się z archiwum, nie wiadomo» | `prd.md:242`, `deploy-plan.md:2099` |

### Ocena (kontrakt V2)

| term | variants | kind | definition (quoted) | source |
|---|---|---|---|---|
| ocena | assessment, wynik oceny, `assessments`, `AssessmentOutputV2`, `output` | entity | «Normatywny kontrakt pojedynczej oceny treści przez LLM: **rdzeń** (trafność, cele, stanowisko autora wobec każdego celu) **+ tezy z cytatem**» | `contract-v2.md:13`, `prd.md:288` |
| kontrakt oceny | kontrakt V2, contract V2, `contract-v2.md` | policy | «**SOURCE OF TRUTH**»; «Dokumenty pomocnicze nie rozszerzają kontraktu; przy sprzeczności obowiązuje ten plik» | `llm-assessment/README.md:15`, `contract-v2.md:377` |
| `item` | pojedyncza wypowiedź, wpis | value | «LLM analizuje semantykę pojedynczej wypowiedzi (`item`), nie surowy payload źródła» | `contract-v2.md:25` |
| trafność | `relevance`, `investment_related`, `not_investment_related`, `unclear` | value | `investment_related`: «autor mówi o rynku, spółce, aktywie, sektorze lub makro w kontekście ceny, wyceny, transakcji, portfela lub wyników» | `contract-v2.md:135-137` |
| cel | target, `targets[]`, spółka/temat, podmiot, `kind` (`company`, `asset`, `sector`, `macro_topic`, `other`, `unknown`) | value | «Cel to podmiot (spółka, aktywo, sektor, temat), nie teza» | `contract-v2.md:27`, `contract-v2.md:153` |
| anchor | fragment wskazujący cel, `anchor.quote` | value | «najkrótszy ciągły fragment tekstu nazywający cel, dokładnie jak w tekście»; «`explicit` nie oznacza rozpoznania encji („CDR” i „CD Projekt” to dla kontraktu różne anchory)» | `contract-v2.md:219`, `contract-v2.md:158` |
| `reference` | `explicit`, `contextual`, `ambiguous`, `unresolved` | value | sposób nazwania celu; «W MVP rodziców nie ma, więc `contextual` nie występuje» | `contract-v2.md:154`, `contract-v2.md:226` |
| stanowisko | stance, `direction`, kierunek, „sentyment”, wydźwięk | value | «Stanowisko jest osobne dla każdego celu; ton emocjonalny ≠ stanowisko inwestycyjne. Brak globalnego sentymentu posta»; «„sentyment” = stanowisko wobec konkretnego» celu | `contract-v2.md:26`, `prd.md:125-126` |
| `direction` | `bullish`, `bearish`, `neutral`, `mixed`, `not_expressed`, `unclear`, wzrostowo | value | słownik wartości i reguły kierunku | `contract-v2.md:119`, `contract-v2.md:164`, `S-04/plan-brief.md:16` |
| osłabienie stanowiska | `assertiveness`, `hedged`, `unhedged`, „(ostrożnie)” | value | «`assertiveness` opisuje sposób sformułowania stanowiska przez autora — nie pewność modelu, jakość argumentów ani skalę ruchu» | `contract-v2.md:168`, `contract-v2.md:165` |
| próg stanowiska | „szeroko + pewność” | policy | «Kierunek przypisuje się, gdy rozsądny czytelnik odczyta go z tekstu — także z sugestii»; «Kierunek odczytany z sugestii lub pośrednio ma `assertiveness = hedged`» | `contract-v2.md:234` |
| teza | claim, `claims[]`, tezy/opinie/fakty/pytania/działania | value | «Tezy (`claims`) opisują, co autor twierdzi; każda ma dokładnie jeden dosłowny cytat z `item`» | `contract-v2.md:28`, `prd.md:286` |
| `claim.kind` | `factual_claim`, `opinion`, `investment_thesis`, `question`, `trade_action` | value | «`investment_thesis` — ocena wyceny, atrakcyjności lub przyszłego zachowania inwestycji» | `contract-v2.md:175` |
| `attribution` | `author`, `reported`, `unclear` | value | «`reported` — cudze stanowisko przytoczone bez przyjęcia» | `contract-v2.md:176` |
| `statement` | teza w języku posta | value | «krótka, samodzielna reprezentacja znaczenia tezy **w języku posta**» | `contract-v2.md:178` |
| cytat | evidence, dowód, `quote`, obiekt cytatu | value | «Cytaty są sprawdzane przez backend jako podciąg wskazanego pola po normalizacji białych znaków (§10) — i niczym więcej» | `contract-v2.md:29`, `contract-v2.md:299-303` |
| niepewność | flagi, `missing_context`, `possible_sarcasm`, `insufficient_content`, `extraction_limit` | value | «Niepewność jest jawna (`unclear`, `hedged`, flagi); model nie zgaduje» | `contract-v2.md:30`, `contract-v2.md:187-192` |
| modyfikator | `modifiers[]`, `humor`, `promotion` | value | «`promotion` — jawna treść promocyjna (reklama, polecenie z korzyścią autora)» | `contract-v2.md:144` |
| tag kanału | tag tematyczny, tag nastroju | value | «**nigdy nie są celem**» | `contract-v2.md:221` |
| slang rynku | pamięciorze, śmieciuch, „na peronie”, pompa, kibel, zwała | value | «Słownik slangu jest materiałem pomocniczym promptu i instrukcji oznaczania, nie częścią kontraktu» | `contract-v2.md:222-223`, `contract-v2.md:240` |
| wpis bez treści | odsiew, prefilter, wynik bez wywołania modelu | operation | «jeśli wynik ma **mniej niż 3 znaki** (code points), wpis jest „bez treści”»; gotowy wynik z pochodzeniem `prefilter` | `contract-v2.md:315`, `contract-v2.md:329` |
| klasa błędu | `FailureClass`, `provider_error`, `timeout`, `invalid_structured_output`, `schema_violation`, `invalid_reference`, `fabricated_evidence`, `domain_invariant_violation` | value | «Pełna lista klas» | `contract-v2.md:361-365` |
| halucynacja | zmyślony cytat, `fabricated_evidence` | policy | guardrail: «Jakość danych z LLM musi być wysoka — ekstrakcja/klasyfikacja bez halucynacji»; D6 | `prd.md:48`, `contract-v2.md:348` |
| pochodzenie oceny | provenance, `Provenance`, źródło wyniku (`model`/`prefilter`), `contract_version`, `prompt_version` | value | «wynik oceny to `output` (ten JSON od modelu, bez zmian) obok `provenance` z polami `contract_version` i `prompt_version`, wypełnianymi przez backend» | `contract-v2.md:108`, `contract-v2.md:423-437` |
| rozpoznawanie spółek | entity resolution, canonical entity resolution, resolver | operation | poza zakresem: «MVP nie ustala, o którą konkretnie spółkę chodzi (ticker/ISIN), ani nie wylicza nowości, trendu czy końcowej decyzji o sygnale» | `prd.md:321`, `contract-v2.md:402` |
| zestaw wzorcowy | gold, gold sample, złota próbka, posty „trudne” | value | «~120 zanonimizowanych postów #gielda z dwóch dni», «oznaczonych przez użytkownika» | `agent-implementation.md:102` |
| progi modelu domyślnego | zgodność z kontraktem, trafność binarna, recall celów, precision celów, kierunek, dominujący kierunek, sprzeczne kierunki | measure | tabela progów, kierunek ≥ 70 %, sprzeczne ≤ 10 % | `agent-implementation.md:106-114`, `measurement/README.md:151-159` |
| pominięty cel | missed target | measure | «pominięty cel to spółka lub aktywo, o których autor coś twierdzi (sama lista tickerów i wzmianka w tle się nie liczą)» | `S-28/plan.md:7`, `S-28/report-2026-10-31.md:100` |
| konfiguracja produkcyjna | model domyślny, Luna, `openai/gpt-6-luna`, prompt v2.5, tier `flex` | value | «`openai/gpt-6-luna`, prompt v2.5» | `roadmap.md:257`, `roadmap.md:35` |
| budżet LLM | limit klucza, $10/mies. | policy | «limit $10/mies. zostaje, bez osobnego pułapu» | `roadmap.md:303`, `S-28/change.md:18` |

### Przebieg i rozliczanie postów

| term | variants | kind | definition (quoted) | source |
|---|---|---|---|---|
| przebieg | przepływ, uruchomienie, run, pipeline run, pipeline | entity | «A run is a plain sequence of steps in Java» | `pipeline/CLAUDE.md:3`, `prd.md:36`, `prd.md:182` |
| rodzaj przebiegu | `RunKind`, `RANGE`, `RETRY`, `POSTS`, przebieg zakresu, ponowienie resztek, wybrane posty | value | «`RANGE` (zakres dat), `RETRY` (resztki, zawsze przed `RANGE`), `POSTS` (zaznaczone id)» | `S-02/plan-brief.md:29`, `deploy-plan.md:1642` |
| przebieg dzienny | noc, nocny przebieg, cron, `pipeline-trigger`, D-2…D-1, przebieg zaplanowany, `scheduled` | event | «Przebieg dzienny obejmuje dni D-2 i D-1 według kalendarza warszawskiego, ze wszystkich skonfigurowanych źródeł» | `trigger/README.md:5`, `deploy-plan.md:466-468` |
| krok | step, `fetch`, `store`, `select`, `assess`, `summarize` | entity | «RANGE: fetch → store → select → assess → summarize; RETRY / POSTS: select → assess → summarize» | `pipeline/CLAUDE.md:64` |
| status przebiegu | `RunStatus`, `queued`, `running`, `succeeded`, `partial`, `failed`, `interrupted` | state | `partial`: «zakończone kroki, ale co najmniej jedna nieudana ocena, `DEAD` albo błąd techniczny per post», od S-20 także źródło nie-`succeeded` | `S-02/plan.md:354`, `pipeline/CLAUDE.md:64`, `prd.md:271` |
| pusty przebieg | `NoPostsFetchedException` | event | «przebiegu, w którym **żadne** źródło nie dało posta» | `prd.md:271`, `api/README.md:131` |
| wymaga uwagi | `needsAttention`, `attentionReasons`, `AttentionReason`, powody | policy | «`runEnded` notifies only a run with a non-empty `PipelineRun.attentionReasons()` (`needsAttention()`, the one rule)» | `pipeline/CLAUDE.md:259-260`, `deploy-plan.md:1931` |
| przerwanie | `interrupted`, `interruption_cause`, `InterruptionCause`, `deploy`, `restart`, `shutdown`, `lost` | event | cztery przyczyny; `lost`: «nobody executed it any more (its instance was lost without closing it)» | `pipeline/CLAUDE.md:131-140` |
| strażnik | watchdog, `RunWatchdog`, brak postępu, `PIPELINE_STALL_TIMEOUT` | policy | «A run of this instance without activity for `PIPELINE_STALL_TIMEOUT` (production: `39m`) ends `failed`» | `deploy-plan.md:735`, `api/README.md:115` |
| aktywność przebiegu | `last_activity_at`, postęp | measure | «Set by the start, every counted post, every post whose call found the provider» unavailable | `pipeline/CLAUDE.md:84-87` |
| sierota / zombie | orphan, utracona instancja, `cleanUpOrphans` | event | «a run left `queued` or `running` while nothing executed it (a "zombie", audit A2)» | `deploy-plan.md:1555` |
| bezpiecznik | breaker, `OUTAGE_STREAK`, bezpiecznik klienta, `CLIENT_ERROR_STREAK` | policy | «`OUTAGE_STREAK` (3, a constant, no variable) outages in a row stop the step» | `pipeline/CLAUDE.md:330-332`, `pipeline/CLAUDE.md:339-342` |
| kolejka przebiegów | jeden naraz, `pipeline_busy` | policy | «Przebiegi trafiają do kolejki i idą po jednym» | `api/README.md:109`, `deploy-plan.md:472` |
| bramka wdrożeń | deploy guard, `/api/deploy-safety`, Wait for CI | policy | «holds the apply and the code deploy for up to 90 min while a live run is queued or running» | `REPOSITORY_GUIDELINES.md:67` |
| etap posta | `posts.stage`, `PostStage`, `stored`, `assessing`, `assessed`, `failed`, `dead` | state | «`stage` ∈ {`stored`, `assessing`, `assessed`, `failed`, `dead`}» | `S-02/plan.md:228` |
| próba oceny | attempt, `assessment_attempts` | measure | «Próba liczy się tylko wtedy, gdy zapisano wiersz `assessments` ze statusem `FAILED`»; od S-09 «Only this uses an attempt» | `S-02/plan.md:107`, `pipeline/CLAUDE.md:318-320` |
| martwy post | `dead`, martwe, `post_dead` | state | «post bez poprawnej oceny po trzech próbach» | `S-28/report-2026-10-31.md:128`, `api/README.md:132` |
| resztki | leftover, „ponowienie nieudanych” | value | «resztki: posty `STORED` oraz `FAILED` z `assessment_attempts < 3`» | `S-02/plan.md:248`, `pipeline/CLAUDE.md:67` |
| trzy winy | wina posta, wina dostawcy, wina naszego kodu | policy | «*the post's fault*», «*the provider's fault*», «*our code's fault*» | `pipeline/CLAUDE.md:318`, `pipeline/CLAUDE.md:321`, `pipeline/CLAUDE.md:324` |
| błąd techniczny | `processing_errors`, `technical_failures`, `last_technical_error` | measure | pojedyncza awaria: «is a technical error of its post: the post goes back without an attempt»; błąd kodu: «puts the post back without an attempt and counts one technical failure» | `deploy-plan.md:1358-1359`, `pipeline/CLAUDE.md:360-361` |
| post wstrzymany | on hold, `onHold`, „Ponów zaznaczone” | state | «A post with three technical failures in a row»; «It keeps its stage (`stored` or `failed`)» | `deploy-plan.md:1360`, `pipeline/CLAUDE.md:362-365` |
| Dni bez danych | dzień pokryty, luka, okno D-15…D-2, `missing-days` | measure | «A day is covered for a source only when a `succeeded` / `partial` range run has a `succeeded` result for that source» | `deploy-plan.md:1937`, `deploy-plan.md:1643` |
| świeżość | udana aktualizacja, data aktualizacji, etykieta „Dane zaktualizowane”, `/api/freshness` | measure | «Datę źródła przesuwa tylko przebieg zakresu zakończony `succeeded` albo `partial`, w którym wynik tego źródła to `succeeded`» | `prd.md:227`, `prd.md:225` |
| feed | lista postów, lista chronologiczna | value | «Użytkownik widzi listę przetworzonych postów, domyślnie posortowaną chronologicznie» | `prd.md:106` |
| pochodzenie posta | `Wykop · #gielda`, `Reddit · r/stocks`, oznaczenie źródła | value | «każdy post w feedzie i na stronie szczegółów jest oznaczony źródłem i kanałem» | `prd.md:243` |

### Konta i dostęp

| term | variants | kind | definition (quoted) | source |
|---|---|---|---|---|
| link aktywacyjny | activation link, token, `activation_tokens`, `/activate#<token>` | entity | «jednorazowy, czasowo ograniczony link aktywacyjny»; «Whoever holds it sets the account's password» | `prd.md:296`, `deploy-plan.md:604` |
| aktywacja | ustawienie hasła | operation | «Zaproszony klika link i samodzielnie ustawia hasło przy pierwszym logowaniu» | `prd.md:296` |
| reset | `reset-password`, recovery | operation | «Reset kasuje hasło, usuwa wszystkie sesje konta i wydaje nowy link, unieważniając poprzedni» | `prd.md:212` |
| bootstrap | `create-admin`, pierwszy admin | operation | «Operator może utworzyć pierwsze konto administratora poza interfejsem webowym» | `prd.md:197` |
| sesja | `sr_session`, Spring Session | entity | «wygasa po 30 dniach bezczynności, ciasteczko jest odnawiane po każdym udanym, uwierzytelnionym żądaniu API» | `api/README.md:293` |

### Sygnały operatorskie i bezpieczeństwo danych

| term | variants | kind | definition (quoted) | source |
|---|---|---|---|---|
| powiadomienie | notification, `notifications`, dzwonek, dzwoneczek, bell | entity | «one row per admin with `login_enabled`, `ON CONFLICT DO» NOTHING | `pipeline/CLAUDE.md:264`, `api/README.md:291` |
| alert | zdarzenie, `AlertEvent`, `RUN_NEEDS_ATTENTION`, `OperatorAlertQueue`, Telegram | event | «One event = two Telegram messages» | `deploy-plan.md:1694`, `deploy-plan.md:1674-1684` |
| kanał alarmowy | out-of-app alert channel, F-04, healthchecks.io, check, check-in, `api-events` | policy | «sygnał poza aplikacją daje usługa healthchecks.io, która alarmuje w Telegramie» | `prd.md:266` |
| kopia bazy | backup, `db-backup`, manifest, kontrola wiarygodności, `BACKUP_ACCEPT_SHRINK` | entity | «Każdy dump trafia tam dopiero wtedy, gdy w całości odtworzy się w tymczasowej bazie» | `backup/README.md:5`, `prd.md:279` |
| kolumny wolumenu raportu S-28 | Pobrane, Zdjęte, Do oceny | measure | kolumny raportu, inne niż `fetched` produktu (zob. M-13) | `S-28/report-2026-10-31.md:12` |

## Lifecycles

### L-1. Post

- Stany: «`stage` ∈ {`stored`, `assessing`, `assessed`, `failed`, `dead`}» (`S-02/plan.md:228`). `on hold` nie jest stanem: «it keeps its stage (`stored` / `failed`)» (`pipeline/CLAUDE.md:362-363`).
- Powstaje: krok `store` przebiegu RANGE. «new posts with their raw payload, once per `(source, item_type, source_item_id)`» (`pipeline/CLAUDE.md:66`).
- `stored`/`failed` → `assessing`: claim, «only while the run is running» (`pipeline/CLAUDE.md:71`). Właściciel: «CHECK: właściciel jest niepusty wtedy i tylko wtedy, gdy `stage = 'assessing'`» (`S-02/plan.md:229`).
- `assessing` → `assessed`: zapisany wynik ok. Tylko posty `assessed` trafiają do feedu (`S-03/plan-brief.md:29`).
- `assessing` → `failed`: zapisany wynik nieudany, wina posta. «the third makes the post `dead`» (`pipeline/CLAUDE.md:319-320`).
- `assessing` → `stored`/`failed` bez próby: wina dostawcy lub kodu (release, releaseFailed), przerwanie, recovery: «`assessment_attempts = 0` → `STORED`, `> 0` → `FAILED`» (`S-02/plan.md:364`), watchdog: «the posts go back without an attempt» (`pipeline/CLAUDE.md:150`).
- Kto wybiera posty: «range → only the posts it stored first; retry → every leftover except posts on hold … posts → the given ones still assessable» (`pipeline/CLAUDE.md:67`).
- Brakujące przejścia, zapisane jawnie: `dead` → cokolwiek: «a `dead` one stays `dead`» (`deploy-plan.md:1412`), ponowienie dostaje `post_dead` (`S-02/plan.md:107`). `assessed` → ponowna ocena: brak, «oceny zostają: retencja bezterminowa, brak ukrywania, filtra po źródle i ponownej oceny» (`S-28/frame.md:32`). Usunięcie posta: brak, «zebrane posty zostają» (`prd.md:242`), «Synchronizacji usunięć nie ma» (`prd.md:281`).

### L-2. Przebieg

- Stany: `queued` → `running` → `succeeded` / `partial` / `failed` / `interrupted`.
- Warunek startu: «409 `pipeline_busy` while a run is queued or running» (`deploy-plan.md:472`). Żądanie zakresu tworzy do dwóch przebiegów: «`RETRY` (tylko jeśli istnieje post `STORED` albo `FAILED` z próbami < 3) i `RANGE`» (`S-02/plan.md:96`). Przed decyzją każde żądanie sprząta sieroty (`deploy-plan.md:1559`).
- Kto startuje: admin (`POST /api/admin/pipeline-runs`) albo cron: «the same runs as the admin's button» (`deploy-plan.md:468`).
- `running` → `succeeded`/`partial`: krok `summarize`. Status wynika z oceny, «but `partial` when that succeeded and a source did not» (`pipeline/CLAUDE.md:64`). Historyczny zakres ról statusów: `S-02/plan.md:353-354`.
- → `failed`: krok rzucił wyjątek, «No run stays `queued`/`running` without a thread: `PipelineRunService.execute` ends it `failed` on» any exception (`pipeline/CLAUDE.md:388-389`); żadne źródło bez posta (`prd.md:271`); bezpiecznik (`pipeline/CLAUDE.md:330-333`); watchdog (`deploy-plan.md:735`); nieudany check LLM (`api/README.md:134`).
- → `interrupted`: recovery przy starcie, zamykanie instancji, sprzątanie sierot (`pipeline/CLAUDE.md:133-140`). Runy `interrupted` mają przyczynę, «Runs interrupted before `V8`» nie (`pipeline/CLAUDE.md:141-142`).
- Koniec zawsze zgłaszany: «Whoever ends a run reports it to `RunNotifications.runEnded`» (`pipeline/CLAUDE.md:421`); pięć miejsc (`pipeline/CLAUDE.md:249-258`).
- Brakujące przejścia `[inference]`: brak zapisu o wznowieniu przebiegu „od kroku” (poza zakresem `S-02/plan-brief.md:49`) i o ręcznym zatrzymaniu („przycisk „Zatrzymaj”” poza zakresem, `S-06/plan-brief.md:49`).

### L-3. Krok przebiegu

`running` → `succeeded` / `failed` / `interrupted`. «a step opens only in a running run and closes only once» (`pipeline/CLAUDE.md:72`). Krok `assess` zatrzymany przez dyspozytor kończy się po wyniku: «a `failed` run never shows its `assess` step» `succeeded` (`pipeline/CLAUDE.md:129-130`). Kroki bez porządku ponowień: «A step with side effects runs once» (`pipeline/CLAUDE.md:383`).

### L-4. Wynik źródła w przebiegu

Brak wiersza → `succeeded` / `empty` / `failed`, zapis raz, w chwili końca źródła: «`RunRecorder.source`: the row in» tabeli razem z licznikami i aktywnością (`pipeline/CLAUDE.md:370-372`). «the source the stop interrupted gets no row» (`pipeline/CLAUDE.md:380`). Backfill historii: reguły w `deploy-plan.md:1948-1954`. Dotyczy tylko RANGE: «Retry and posts runs fetch nothing and are not affected» (`pipeline/CLAUDE.md:380-381`).

### L-5. Źródło jako takie

- nieznane → zarejestrowane: stała i migracja w czterech zbiorach `CHECK` (`prd.md:274`).
- zarejestrowane → skonfigurowane: Wykop klucze, Reddit `REDDIT_SUBREDDITS` (`prd.md:275`, `REPOSITORY_GUIDELINES.md:76`).
- skonfigurowane → włączone na produkcji: decyzja i runbook operatora; «Only after the operator has read the S-28 report and recorded the decision in issue #121; no threshold has to be met» (`deploy-plan.md:2073`). Wykonane: «Reddit (r/stocks, r/ValueInvesting, r/investing) włączony na produkcji 2026-11-01» (`roadmap.md:84`).
- włączone → wyłączone: «turn Reddit off» przez usunięcie zmiennej; «The posts already collected stay with their raw payloads and assessments» (`deploy-plan.md:2109`, `deploy-plan.md:2118`).
- Wyrejestrowanie: niemożliwe, «closed sets are append-only» (`deploy-plan.md:2013`, `deploy-plan.md:2128`). Etykieta świeżości pamięta wyłączone źródło: «A source switched off stays listed with its last date» (`deploy-plan.md:2152`).

### L-6. Konto

- Powstaje: `create-user` «creates an account without a password (role `user` by default) and prints its link» (`deploy-plan.md:579`); `create-admin` tylko pierwszy (`deploy-plan.md:587`); konto systemowe migracją (`prd.md:309`).
- bez hasła → aktywne: aktywacja linkiem, potem zwykłe logowanie, «The page creates no session: they log in as usual» (`deploy-plan.md:601`).
- Po wygaśnięciu linku: «konto pozostaje bez hasła — logowanie niemożliwe do czasu wygenerowania nowego linku» (`prd.md:76`).
- aktywne → bez hasła: `reset-password`, «any existing account, the admin's included» (`deploy-plan.md:580`).
- Brakujące przejścia: zmiana roli, deaktywacja, usunięcie. Poza zakresem: «rozbudowana edycja/deaktywacja kont» (`prd.md:323`), «listowanie, usuwanie i zmiana roli kont» (`S-07/plan-brief.md:46`).

### L-7. Link aktywacyjny

wydany → użyty / wygasły / zastąpiony. «Link jest ważny domyślnie 12 h (parametr wdrożenia, od 1 min do 7 dni) i działa raz» (`prd.md:214-215`). Jeden na konto: «w bazie tylko SHA-256, jeden wiersz na konto» (`S-07/plan-brief.md:29`). Hasło spoza polityki «nie zużywa linku» (`api/README.md:289`). Wszystkie stany końcowe wyglądają tak samo dla osoby: «link nieznany, użyty, wygasły albo zastąpiony nowszym — nieodróżnialnie» (`api/README.md:292`).

### L-8. Sesja

logowanie → aktywna (30 dni bezczynności, odnawiana) → wylogowanie / wygaśnięcie / usunięcie przy resecie i aktywacji. «aktywacja usuwa wszystkie sesje konta» (`api/README.md:289`). Znana luka: «Logowanie, które sprawdziło hasło przed zatwierdzeniem `reset-password`, zapisuje sesję już po `DELETE` resetu» (`roadmap.md:331`).

### L-9. Ocena (próba)

Każda próba to osobny wiersz: «post 1:N próby oceny, przebieg 1:N próby» (`S-02/plan-brief.md:25`). Wynik `ok` albo `failed`, źródło `model` albo `prefilter` (`contract-v2.md:430`, `assessor/CLAUDE.md:23`). Brak modyfikacji po zapisie `[inference]`: dokumenty mówią tylko o dopisywaniu prób. Odpowiedź modelu nie jest naprawiana: «backend nie „naprawia” odpowiedzi po cichu» (`contract-v2.md:368`).

### L-10. Powiadomienie i alert

- Powiadomienie: powstaje przy końcu przebiegu wymagającego uwagi, po jednym na admina (`pipeline/CLAUDE.md:259-264`); nieprzeczytane → przeczytane per admin (`api/README.md:291`). Usuwania i retencji dokumenty nie opisują `[unknown]`.
- Alert: zgłoszony → wysłany albo zgubiony. «no retries and nothing durable: an event still waiting when the process is killed is lost, while the bell and the log stay» (`deploy-plan.md:1691`). W Telegramie „down”, potem „up”, które «only re-arms the check for the next event; it does not mean the problem is over» (`deploy-plan.md:1694`).

### L-11. Kopia bazy

Przebieg `backup.sh`: kontrola wiarygodności → dump → sprawdzenie → odtworzenie → wysyłka → retencja (`backup/README.md:28-39`). Kopia nie wychodzi bez odtworzenia: «Dopiero po udanym sprawdzeniu wysyła» (`backup/README.md:37`). Tylko produkcja (`backup/README.md:22`). Retencja 14 + 12 (`prd.md:280`).

### L-12. Zmiana kontraktu

Reguła §16: «zachowaj failing fixture», «zaproponuj minimalną zmianę tego pliku», «nie rozszerzaj schematu ani kodu ad hoc» (`contract-v2.md:446-449`). Właściciel decyduje (`assessor/CLAUDE.md:16`). Nowy tekst promptu to nowa wersja: «Zmiana treści promptu = nowa wersja promptu zapisywana w pochodzeniu oceny» (`agent-implementation.md:45`).

## Rules

Zakres: `entity` to reguła jednego bytu, `policy` to reguła całego produktu. Enforcement nieoceniany: kodu nie widziałem.

### Źródła i dane

| id | rule (quoted) | binds | scope | source |
|---|---|---|---|---|
| DR-01 | «Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu; warstwa oceny, zapisu i udostępniania danych pozostaje niezmieniona» | źródło, wspólny model | policy | `prd.md:272`, `REPOSITORY_GUIDELINES.md:11` |
| DR-02 | «posts store the shared model's item type (`post`/`link`/`comment`), never a source's own type names» | post, typ posta | policy | `REPOSITORY_GUIDELINES.md:11` |
| DR-03 | Rejestracja to «cztery zbiory: `posts.source`, `raw_payloads.source`, `pipeline_runs.sources` i `pipeline_run_sources.source`» | źródło | policy | `prd.md:274` |
| DR-04 | «a new source that fails or stays silent does not fail the other sources' run» | źródło, przebieg | policy | `sources/CLAUDE.md:71-72` |
| DR-05 | «System odrzuca duplikaty postów po identyfikatorze źródłowym»; «Treściowo podobne posty od różnych autorów NIE są odrzucane» | post, duplikat | policy | `prd.md:89` |
| DR-06 | «Not in a source: scheduling, deduplication against stored posts, persistence, assessment» | źródło | policy | `sources/CLAUDE.md:103-104` |
| DR-07 | Źródło nie filtruje treści: «a source adds no own content filter» | źródło, prefilter | policy | `sources/CLAUDE.md:101-102` |
| DR-08 | «Posty zdjęte przez moderację albo Reddit i skasowane przez autora nie są pobierane ani liczone jako pominięte elementy; ich liczbę podaje tylko log» | post zdjęty, `skipped` | entity | `prd.md:242` |
| DR-09 | Stronicowanie nie oddaje dziur: «return an incomplete result as complete» to czego repozytorium nie wolno; «one second read from page 1 that replaces the first» | źródło, element | policy | `sources/CLAUDE.md:82`, `lessons.md:145` |
| DR-10 | «RAW JSON z zewnętrznych źródeł zawsze przechowuj oddzielnie od znormalizowanych rekordów aplikacyjnych. Zachowaj RAW bezstratnie» | RAW | policy | `lessons.md:16`, `prd.md:282` |
| DR-11 | «so a retry gets exactly the input of the first attempt» (wejście oceny odbudowane z RAW) | RAW, próba | entity | `sources/CLAUDE.md:17` |
| DR-12 | «Text goes to the model unchanged (hard spaces, line breaks, emoji) except U+0000» | element, `item` | policy | `sources/CLAUDE.md:89-90`, `contract-v2.md:72` |
| DR-13 | «Dane (posty, konta) są przechowywane bezterminowo w MVP — brak automatycznej retencji/czyszczenia» | post, konto | policy | `prd.md:279` |
| DR-14 | Reddit jak Wykop: «bezterminowo, a RAW bezstratnie»; «post zdjęty albo skasowany na Reddicie po pobraniu zostaje w bazie i w RAW» | post, RAW | policy | `prd.md:281` |
| DR-15 | «SourcePost.channel» niepusty i równy wejściu: «never empty, and the same value as the input's `channels[0]`» | kanał | entity | `sources/CLAUDE.md:92-93` |
| DR-16 | Stany zamknięte są append-only: «append-only — never rename or remove a constant» | klasa błędu, typ posta | policy | `assessor/CLAUDE.md:23-24` |

### Ocena (kontrakt V2)

| id | rule (quoted) | binds | scope | source |
|---|---|---|---|---|
| DR-20 | «Do modelu NIE trafiają jako osobne pola: źródło, autor / nick, zaangażowanie, URL, ID źródła, ID artefaktu RAW, hash treści, kandydaci na encje, język» | `item`, model | policy | `contract-v2.md:75` |
| DR-21 | «MVP:» oceniane są wyłącznie `post` i `link`; «Przetwarzanie komentarzy pod postami» poza MVP | typ posta | policy | `contract-v2.md:71`, `prd.md:317` |
| DR-22 | Stanowisko per cel, bez globalnego sentymentu | stanowisko, cel | entity | `contract-v2.md:26`, `prd.md:124` |
| DR-23 | «Każda nazwana spółka lub aktywo jest celem» | cel | entity | `contract-v2.md:220` |
| DR-24 | «**nigdy nie są celem**» (tagi kanału) | cel, kanał | entity | `contract-v2.md:221` |
| DR-25 | «Jeden cel na podmiot» | cel | entity | `contract-v2.md:225` |
| DR-26 | Każda teza «ma dokładnie jeden dosłowny cytat z `item`» | teza, cytat | entity | `contract-v2.md:28`, `contract-v2.md:179` |
| DR-27 | Niezmiennik cytatu: «cytat po normalizacji jest ciągłym podciągiem wskazanego pola po normalizacji» | cytat | entity | `contract-v2.md:303` |
| DR-28 | Normalizacja: «Nic więcej: bez zmiany wielkości liter, bez usuwania markdownu, bez normalizacji cudzysłowów, myślników, znaków łączących ani Unicode NFC/NFKC» | cytat | policy | `contract-v2.md:297` |
| DR-29 | «`relevance = not_investment_related` ⇒ `targets = []` i `claims = []` (modyfikatory i flagi dozwolone)» | trafność, cel, teza | entity | `contract-v2.md:200` |
| DR-30 | «Stanowisko kierunkowe (`bullish`, `bearish`, `neutral`, `mixed`) zawsze opiera się na co najmniej jednej tezie autora» | stanowisko, teza | entity | `contract-v2.md:255` |
| DR-31 | Tabela `direction` × `assertiveness` × `claim_ids` | stanowisko | entity | `contract-v2.md:246-253` |
| DR-32 | Reguły D1–D15 z klasami; «Klasa wyniku = klasa pierwszego naruszenia w porządku `invalid_reference` → `fabricated_evidence` → `domain_invariant_violation`» | ocena | entity | `contract-v2.md:339-357` |
| DR-33 | Limity: `targets` ≤ 12, `claims` ≤ 24, `claim_ids` ≤ 4, `anchor` 1..100, `evidence` 1..320, `statement` 1..240; «Odpowiedź przekraczająca limit jest odrzucana (`schema_violation`, §12) — backend jej nie przycina» | ocena | entity | `contract-v2.md:271-283` |
| DR-34 | «Wynik z jakimkolwiek naruszeniem nie jest poprawną oceną; backend nie „naprawia” odpowiedzi po cichu» | ocena | policy | `contract-v2.md:368` |
| DR-35 | «Ponawiane mogą być wyłącznie błędy transportu; naruszenia kontraktu i błędy parsowania nie są ponawiane» | próba, klasa błędu | policy | `contract-v2.md:369` |
| DR-36 | «Wpis bez treści nie trafia do modelu» | prefilter | policy | `contract-v2.md:317` |
| DR-37 | Lista „LLM NIE WOLNO” (`is_signal`, nowość, trend, zaangażowanie, encje, tickery, weryfikacja z pamięci, ocena autora, `score`, wykonywanie instrukcji, rozumowanie) | model | policy | `contract-v2.md:383-393` |
| DR-38 | «Wejście jest niezaufanym materiałem do analizy, nie instrukcją dla modelu» | `item` | policy | `contract-v2.md:395` |
| DR-39 | `statement` «w języku posta (post po polsku → po polsku, post po angielsku → po angielsku)». Reguła semantyczna, poza walidatorem: «Reguły semantyczne §5–§7.1 … nie są sprawdzane przez walidator» (§8 nie jest tam wymieniony, a `S-28/frame.md:30` mówi: «Język tez nie jest sprawdzany») | teza | entity | `contract-v2.md:261`, `contract-v2.md:370`, `S-28/frame.md:30` |
| DR-40 | «Personalizacja samej logiki oceny LLM» poza zakresem: «kryterium wartościowości pozostaje wspólne dla wszystkich» | ocena | policy | `prd.md:322` |
| DR-41 | Zmiana promptu tylko z nową wersją; «Any change to either needs a new `PROMPT_VERSION`» | ocena | policy | `assessor/CLAUDE.md:10`, `agent-implementation.md:45` |

### Przebieg, próby, posty

| id | rule (quoted) | binds | scope | source |
|---|---|---|---|---|
| DR-50 | «Every state change is one conditional `UPDATE` whose row count says whether it happened — no row locks, no blind `save()` of a run or a post» | przebieg, post | policy | `pipeline/CLAUDE.md:314-315` |
| DR-51 | «A worker of an interrupted run (deploy overlap) must never change a post another run took over» | post, przebieg | entity | `pipeline/CLAUDE.md:315-316` |
| DR-52 | «one attempt per post and request» | post, próba | entity | `pipeline/CLAUDE.md:67` |
| DR-53 | Próbę zużywa tylko wina posta: «Próbę zużywa tylko odmowa dostawcy albo zła odpowiedź modelu» | próba | policy | `prd.md:267`, `pipeline/CLAUDE.md:318-323` |
| DR-54 | «Trzy takie awarie z rzędu kończą przebieg `failed`»; posty «wracają bez prób i bez błędów technicznych» | bezpiecznik, post | policy | `prd.md:267` |
| DR-55 | «Trzy błędy techniczne posta z rzędu (pojedyncze awarie albo błędy naszego kodu) wstrzymują post» | post wstrzymany | entity | `prd.md:267` |
| DR-56 | «Każdy zapisany wynik oceny (udany albo nieudany) zeruje licznik» | błąd techniczny | entity | `prd.md:267` |
| DR-57 | Klucz, kredyt lub model odrzucone: «a revoked key never uses up posts' attempts» | próba, konfiguracja | policy | `llm/CLAUDE.md:53-57` |
| DR-58 | «Retries live only in `TransportRetry`» | ponowienie | policy | `llm/CLAUDE.md:47` |
| DR-59 | «A step with side effects runs once; retries live only below the step» | krok | policy | `pipeline/CLAUDE.md:383` |
| DR-60 | «No run stays `queued`/`running` without a thread» | przebieg | policy | `pipeline/CLAUDE.md:388` |
| DR-61 | Sieroty «all or none, the queue being one» | sierota, kolejka | policy | `pipeline/CLAUDE.md:405` |
| DR-62 | «**A source's failure or silence is its own result** (S-20)»; przebieg kończy się `failed` tylko bez posta z żadnego źródła | wynik źródła, przebieg | policy | `pipeline/CLAUDE.md:367-374`, `prd.md:271` |
| DR-63 | Liczba dni: «zakres maks. 7 dni, `postIds` 1–100»; «`to` nie później niż dziś (czas warszawski)» | przebieg | entity | `S-02/plan.md:409` |
| DR-64 | «Ponowny zakres tego samego dnia nie dodaje postów» | przebieg, duplikat | policy | `api/README.md:130` |
| DR-65 | «A source bounds its fetch with a deadline below `PipelineStallSettings.MIN_DEFAULT`» | źródło, strażnik | policy | `pipeline/CLAUDE.md:90` |
| DR-66 | «Code that runs work in parallel passes its own executor» | przebieg | policy | `pipeline/CLAUDE.md:366` |
| DR-67 | Pierwszeństwo recovery: «Start-up recovery runs before the web server opens its port» | przebieg | policy | `pipeline/CLAUDE.md:427` |
| DR-68 | «Nothing else asks: no timer, so an idle API still sleeps; reads (`/admin`, `/api/deploy-safety`) never» zapisują | przebieg | policy | `pipeline/CLAUDE.md:419-420` |

### Sygnały i świeżość

| id | rule (quoted) | binds | scope | source |
|---|---|---|---|---|
| DR-70 | «Operator otrzymuje jednoznaczny, widoczny sygnał, gdy zaplanowany dzienny przebieg zbierania/przetwarzania nie powiedzie się lub zakończy częściowym błędem — nie dowiaduje się o tym pośrednio, przez brak nowych danych» | operator, przebieg | policy | `prd.md:262` |
| DR-71 | «Etykieta daty aktualizacji zawsze pokazuje datę ostatniego POMYŚLNEGO przebiegu, nigdy nieudanego» | świeżość | policy | `prd.md:64` |
| DR-72 | Reguła daty per źródło (FR-030, S-29): «Wyniki `empty` i `failed` jej nie ruszają» | świeżość, wynik źródła | policy | `prd.md:227` |
| DR-73 | «Przebiegi ponowienia (RETRY) i wybranych postów (POSTS) nie pobierają nowych danych, więc się nie liczą» | świeżość | policy | `prd.md:225` |
| DR-74 | «ręczne przeliczenie starszych dni przez admina nie przesuwa daty» | świeżość | policy | `prd.md:225` |
| DR-75 | «W etykiecie są tylko źródła z co najmniej jedną taką aktualizacją, liczone z historii przebiegów, bez czytania konfiguracji» | świeżość | policy | `prd.md:227` |
| DR-76 | Dzień pokryty tylko z wyniku `succeeded`; «a failed or empty source covers nothing» | Dni bez danych | policy | `pipeline/CLAUDE.md:44`, `prd.md:271` |
| DR-77 | «Nic nie uzupełnia ich automatycznie i nie ma nowego alarmu» | Dni bez danych | policy | `prd.md:268` |
| DR-78 | «a source's window starts at its first covered day ever, so one never covered misses none» | Dni bez danych | entity | `pipeline/CLAUDE.md:44` |
| DR-79 | Jedna reguła uwagi po stronie API: «the reasons themselves (the codes in `attentionReasons`) are computed by the API» | wymaga uwagi | policy | `REPOSITORY_GUIDELINES.md:50` |
| DR-80 | `partial` z samymi postami `failed` do ponowienia: «nie daje powiadomienia» | wymaga uwagi | policy | `prd.md:264` |
| DR-81 | Dubel crona: «Przebieg crona zakończony `failed` albo `interrupted` zgłasza tylko cron» | alert, przebieg | policy | `prd.md:269` |
| DR-82 | Limity alertów: «To samo zdarzenie dla tego samego przebiegu idzie najwyżej raz na 10 min, a wszystkie razem najwyżej 2 na minutę» | alert | policy | `prd.md:269` |
| DR-83 | «Treść nie zawiera sekretów» | alert | policy | `prd.md:269` |
| DR-84 | «A new path that ends in an ERROR line and that nothing else reports» dostaje własny `AlertEvent` | alert | policy | `pipeline/CLAUDE.md:422-424` |
| DR-85 | «A check-in never changes a cron's result» | check-in, cron | policy | `deploy-plan.md:1211` |
| DR-86 | Kod wyjścia crona: «Kod 0, gdy każdy przebieg jest `succeeded` albo `partial`. Kod 1, gdy któryś jest `failed` albo `interrupted`» | cron, przebieg | policy | `trigger/README.md:34` |
| DR-87 | «Nothing polls production» | operator | policy | `deploy-plan.md:1210` |

### Dostęp i konta

| id | rule (quoted) | binds | scope | source |
|---|---|---|---|---|
| DR-90 | «Niezalogowany/nieuprawniony użytkownik nigdy nie widzi danych ani funkcji spoza swojej roli» | rola | policy | `prd.md:50` |
| DR-91 | «Każda chroniona funkcja lub zasób konsekwentnie wymaga uwierzytelnienia» | rola | policy | `prd.md:276` |
| DR-92 | «Every API route requires a session by default» | rola | policy | `REPOSITORY_GUIDELINES.md:40` |
| DR-93 | «Użytkownik nie ma wglądu w dane kont innych userów (e-maile, aktywność) — widoczne wspólnie są wyłącznie przetworzone posty» | konto | policy | `prd.md:277` |
| DR-94 | «Brak publicznej rejestracji» | konto | policy | `prd.md:296`, `REPOSITORY_GUIDELINES.md:10` |
| DR-95 | «E-mail jest stałym identyfikatorem logowania» | konto | entity | `prd.md:294` |
| DR-96 | «Zaproszony użytkownik może użyć linku dokładnie raz, żeby ustawić własne hasło» | link | entity | `prd.md:75` |
| DR-97 | «The link is a secret»; «Never paste it into an issue, a PR, this document or a log» | link | policy | `deploy-plan.md:604-605` |
| DR-98 | Konto systemowe: «nigdy się nie zaloguje, a komendy CLI kont (S-07) muszą je odrzucać» | konto systemowe | entity | `prd.md:309` |
| DR-99 | «trasa nie przyjmuje sesji, a token nie działa na trasach aplikacji (`/api/**`)» | `/internal/**` | policy | `prd.md:309` |
| DR-100 | Hasło 12–128 znaków (`password_policy`) | konto | entity | `api/README.md:292`, `deploy-plan.md:601` |
| DR-101 | Limiter: «5 nieudanych prób / 15 min na e-mail i 20 na IP» | konto | policy | `api/README.md:295` |
| DR-102 | «`szczur-radar-web/` talks to the backend only through its REST API: no direct DB access and no LLM calls from the browser (FR-032)» | SPA | policy | `REPOSITORY_GUIDELINES.md:9` |
| DR-103 | «Trasa `GET /api/freshness` jest dostępna dla każdej zalogowanej roli i nie ujawnia szczegółów przebiegów» | świeżość, rola | policy | `prd.md:225` |

### Operacje i ochrona danych

| id | rule (quoted) | binds | scope | source |
|---|---|---|---|---|
| DR-110 | «never merge to `main` (or push to a PR branch under test) while a run is `RUNNING`» | przebieg, wdrożenie | policy | `REPOSITORY_GUIDELINES.md:66` |
| DR-111 | «never merge to `main` between about 00:30 and 01:30 UTC» | noc | policy | `REPOSITORY_GUIDELINES.md:69` |
| DR-112 | «Only a human merges to `main` (= production deploy)» | wdrożenie | policy | `REPOSITORY_GUIDELINES.md:83`, `lessons.md:44` |
| DR-113 | «Never mark an issue done because code merged — only after production verification» | wdrożenie | policy | `REPOSITORY_GUIDELINES.md:92` |
| DR-114 | Fix-forward: «never edit a deployed `V<n>__*.sql`, add a new migration» | migracja | policy | `REPOSITORY_GUIDELINES.md:31` |
| DR-115 | Kopia refuje pustą bazę: «posts is empty: refusing to back up an empty database» | kopia bazy | policy | `backup/README.md:29` |
| DR-116 | Sesji nie ma w kopii: «Danych sesji nie ma w kopii, bo identyfikator sesji z dumpa pozwoliłby ją przejąć» | kopia, sesja | policy | `backup/README.md:33` |
| DR-117 | «Utracić można najwyżej dane z ostatniej doby» | kopia | policy | `prd.md:279` |
| DR-118 | «If code contradicts `context/foundation/prd.md`, flag it — don't silently resolve it in code» | PRD | policy | `REPOSITORY_GUIDELINES.md:8` |
| DR-119 | «Check an FR's priority in the PRD before building it: `must-have` first, `nice-to-have` only when asked» | FR | policy | `REPOSITORY_GUIDELINES.md:12` |

### Decyzje właściciela

Cytaty decyzji z datą. Nowe na górze.

| id | data | decyzja (quoted) | źródło |
|---|---|---|---|
| OD-01 | 2026-11-01 | «otwarta weryfikacja produkcji S-27 (issue #121: noce z Redditem 2.11 i 4.11, porównanie `dead` z raportem S-28, feed i strona przebiegu w aplikacji) nie blokuje zamknięcia; ewentualne poprawki pójdą jako wydanie poprawkowe v0.3.1» | `roadmap.md:84` |
| OD-02 | 2026-10-31 | «**Wariant:** włączyć Reddita bez napraw»; «**Tezy po polsku:** nie są warunkiem» | `S-28/change.md:16`, `S-28/change.md:19` |
| OD-03 | 2026-10-31 | «**Budżet (Q-03):** limit $10/mies. zostaje, bez osobnego pułapu» | `S-28/change.md:18` |
| OD-04 | 2026-10-31 | Powód: «zamyka się etapami od 12 stycznia do marca 2027, więc naprawy (`claim_ids`, `D6`, filtr megawątków) by się nie zwróciły» | `S-28/change.md:20` |
| OD-05 | 2026-10-31 | Pomiar nie jest bramką: «Czy wynik pomiaru jest warunkiem włączenia Reddita na produkcji» → «nie, raport jest materiałem do decyzji, bez progów» | `roadmap.md:265` |
| OD-06 | 2026-10-30 | «przy hipotetycznie co czwartej błędnej ocenie „znośne i tak”» | `S-28/frame.md:37` |
| OD-07 | 2026-10-30 | «pominięty cel to spółka lub aktywo, o których autor coś twierdzi (sama lista tickerów i wzmianka w tle się nie liczą)» | `S-28/plan.md:7` |
| OD-08 | 2026-10-30 | «**Świeżość per źródło, zawsze rozpisana:**»; «**Bez filtra i sortowania po źródle:** FR-008 zostaje kandydatem do M-5» | `S-29/plan.md:9`, `S-29/plan.md:13` |
| OD-09 | 2026-10-30 | «**Które źródła są w etykiecie:** tylko te z co najmniej jedną taką aktualizacją, liczone z historii przebiegów, bez czytania konfiguracji» | `S-29/plan.md:11` |
| OD-10 | 2026-10-29 | «Arctic Shift jako jedyna ścieżka z runbookiem na wypadek końca archiwum» | `S-27/plan.md:7` |
| OD-11 | 2026-10-29 | «posty zdjęte i skasowane nie są elementami pobrania; `is_self → post`, inaczej `link`; retencja jak Wykop, z doprecyzowaniem w PRD» | `S-27/plan.md:7` |
| OD-12 | 2026-10-29 | Retencja Reddita: «właściciel uznał je 2026-10-29, bez analizy prawnej, za niewiążące dla tej ścieżki» | `prd.md:281` |
| OD-13 | 2026-10-29 | Ryzyko horyzontu: «Właściciel przyjął to ryzyko 2026-10-29 razem z runbookiem wyłączenia źródła» | `prd.md:242` |
| OD-14 | 2026-10-29 | Kanał w bazie: «Wprowadził ją właściciel 2026-10-29, żeby kanał był w bazie wprost, a nie wymóg nowego źródła» | `prd.md:275` |
| OD-15 | 2026-10-29 | Język posta: «stanowisko właściciela z 2026-10-29 („ma być zapisany”) pozostaje» | `roadmap.md:341` |
| OD-16 | 2026-10-27 | Kolejność kamieni: «M-4 (kandydat) to weryfikacja i regresja», «nowe UI/UX przesuwa się na M-5» | `roadmap.md:81` |
| OD-17 | 2026-10-24 | S-12 zaparkowany: «**zaparkowany** (decyzja użytkownika po `/10x-frame`» | `roadmap.md:69` |
| OD-18 | 2026-10-12 | Reset: «operator może przez CLI zresetować dowolne konto, także aktywne i konto admina, nie tylko jeszcze nieaktywowane (decyzja użytkownika)» | `prd.md:210-212` |
| OD-19 | 2026-10-06 | Progi: «złagodzone decyzją użytkownika (≥70 % / ≤10 % zamiast ≥75 % / ≤3 %» | `roadmap.md:35` |
| OD-20 | 2026-10-06 | Pozycja autora: «decyzja użytkownika: samo „trzymam X” = `bullish`, `hedged`» | `contract-v2.md:477` |
| OD-21 | 2026-10-05 | Ruch ceny: «kierunek tego ruchu, `hedged` (wcześniej `not_expressed`)» | `contract-v2.md:479` |
| OD-22 | 2026-10-01 | Kontrakt V2: «zaakceptowany przez użytkownika 2026-10-01» | `contract-v2.md:5` |
| OD-23 | 2026-09-30 | Próg stanowiska: «decyzja użytkownika 2026-09-30» | `contract-v2.md:465` |
| OD-24 | przed 2026-10-01 `[inference]` | Reddit na pilocie 5: «decyzja użytkownika: tezy po angielsku są akceptowalne, tłumaczenie ewentualnie później» (dnia nie czytałem; V2 powołuje się na pilot 5 w `contract-v2.md:470`) | `F-02/frame.md:259` |
| OD-25 | 2026-09-26 | Duplikaty: «dedup celowo ogranicza się do ID posta; powtórki tej samej treści od różnych autorów to zamierzenie, nie błąd» | `prd.md:91-92` |

## Multiple meanings

| id | słowo | znaczenia | cytaty | wniosek |
|---|---|---|---|---|
| M-1 | post | (a) każdy element wspólnego modelu, także link i znalezisko; (b) `item_type = post`, tekstowy; (c) u Wykopu „wpis”, a kontrakt używa „wpis” dla każdego `item` | (a) «zapis nowych postów» w sensie ogólnym: `api/README.md:130`; (b) «wpis Wykopu → `post`, znalezisko Wykopu → `link`» `contract-v2.md:70`; (c) «Wpis bez treści nie trafia do modelu» `contract-v2.md:317` | granica między językiem produktu i wartością pola |
| M-2 | `failed` | post z próbami do ponowienia; przebieg zakończony błędem; krok; wynik źródła; wynik oceny (`status`) | post: «`failed` posts with attempts left» `deploy-plan.md:750`; przebieg: `prd.md:225`; źródło: `deploy-plan.md:1930`; ocena: «`status != ok`» `agent-implementation.md:94` | jedno słowo, pięć bytów; ten sam kłopot ma `succeeded` (przebieg, krok, wynik źródła) i `interrupted` (przebieg, krok) |
| M-3 | próba / attempt | (a) próba oceny, licznik posta; (b) próba HTTP w transporcie, `provenance.attempts`; (c) w raporcie S-28 jedno wywołanie `Assessor.assess`; (d) próba logowania | (a) «Only this uses an attempt» `pipeline/CLAUDE.md:320`; (b) «up to 3 attempts per tier» `llm/CLAUDE.md:47`; (c) «**Próba:** jedno wywołanie `Assessor.assess`» `S-28/report-2026-10-31.md:127`; (d) «5 nieudanych prób» `api/README.md:295` | liczniki pod tą samą nazwą nie są porównywalne |
| M-4 | ponowienie / retry | (a) rodzaj przebiegu `RETRY`; (b) „Ponów zaznaczone”, czyli przebieg `POSTS`; (c) ponowienia transportu LLM `llmRetries`; (d) ponowienia klienta źródła | (a) `S-02/plan-brief.md:29`; (b) «Retry it:» `deploy-plan.md:1388`; (c) `llm/CLAUDE.md:47`; (d) `sources/CLAUDE.md:46` | cztery mechanizmy; tylko (a) i (b) dotyczą prób oceny |
| M-5 | kanał | (a) miejsce w źródle; (b) kanał alarmowy; (c) kanał cenowy nagrania LLM | (a) `prd.md:275`; (b) `prd.md:266`; (c) «Koszt (kanał nagrania)» `S-28/report-2026-10-31.md:43`, typ `Channel` `llm/CLAUDE.md:8` | granica: źródła / operacje / LLM |
| M-6 | sygnał | (a) inwestycyjny; (b) operacyjny; (c) post jako przedmiot pytania | (a) `prd.md:286`; (b) «sygnał awarii» `roadmap.md:93`; (c) «zadać LLM pytanie o dany sygnał» `prd.md:253` | (c) i FR-024 «trendu wykrytych sygnałów» `prd.md:244` zakładają wykrywanie, którego produkt nie robi (DR-37) |
| M-7 | źródło | (a) Wykop/Reddit; (b) źródło wyniku oceny: `model` / `prefilter`; (c) `source_of_truth` | (a) `sources/CLAUDE.md:3`; (b) «źródło wyniku: model \| prefilter» `contract-v2.md:430`, `Provenance.Source` `assessor/CLAUDE.md:23` | (b) to pochodzenie oceny, nie pozyskanie |
| M-8 | kontrakt | (a) kontrakt oceny LLM; (b) kontrakt SPA ↔ API; (c) kontrakt crona; (d) kontrakt portu źródła | (a) `contract-v2.md:13`; (b) `roadmap.md:202`; (c) «Kontrakt crona i alertu» `S-20/plan-brief.md:29`; (d) «Contract» `sources/CLAUDE.md:14` | `web/contract/` nie ma związku z `contract-v2.md` `[inference]` |
| M-9 | trafność | (a) `relevance` posta; (b) dokładność pomiaru | (a) «Reguły trafności» `contract-v2.md:206`; (b) «obniża trafność kierunku» `roadmap.md:322` | w tekście pomiarów wymaga kontekstu |
| M-10 | użytkownik | (a) persona; (b) rola `user`; (c) właściciel projektu w dokumentach planistycznych | (a) `prd.md:27`; (b) `prd.md:299`; (c) «decyzja użytkownika» `roadmap.md:69` | (c) to `właściciel` |
| M-11 | admin / administrator / operator | PRD łączy je w jednej personie, a reguły je rozdzielają: operator to dostęp do CLI i Railway, `admin` to rola w aplikacji | «Administrator (operator wdrożenia)» `prd.md:31`; «robi go wyłącznie operator z dostępem do CLI» `prd.md:213-214`; alert idzie do operatora, dzwonek do każdego admina `prd.md:269` | dwa konteksty: dostęp i operacje; dziś ta sama osoba `[inference]` |
| M-12 | surowy | (a) pełny tekst posta obok oceny; (b) JSON źródła | (a) «wersja surowa obok przetworzonej» `prd.md:37`; (b) «surowy JSON jest dostępny opcjonalnie» `prd.md:116`; rozstrzygnięcie «Godzi US-01» `S-03/plan-brief.md:25` | rozstrzygnięte w S-03 |
| M-13 | pobrane / `fetched` | (a) w produkcie bez zdjętych; (b) w raporcie S-28 ze zdjętymi | (a) `prd.md:242`; (b) wiersz «razem \| 789 \| 393 \| 0 \| 396» `S-28/report-2026-10-31.md:17` | liczby S-28 „Pobrane” nie są porównywalne z `fetched` przebiegu |
| M-14 | `link` | (a) znalezisko Wykopu; (b) Reddit: link, obraz, crosspost, także z treścią; (c) link aktywacyjny; (d) link do oryginału | (a) `contract-v2.md:70`; (b) `prd.md:242`; etykieta SPA «SPA etykietuje `link` jako „znalezisko”» `S-27/research.md:150` | etykieta z (a) dla (b) `[unknown]`, zob. U-08 |
| M-15 | teza | (a) `claim` jako parasol na tezy/opinie/fakty/pytania/działania; (b) `investment_thesis`, jeden z pięciu rodzajów | (a) `prd.md:286`; (b) `contract-v2.md:175` | „tezy po polsku” w S-28 to (a) |
| M-16 | ocena | (a) `assessment`; (b) decyzja czytającego; (c) przegląd właściciela | (b) «tę ocenę robi użytkownik» `prd.md:288`; (c) «Pominięte cele (ocena właściciela)» `S-28/report-2026-10-31.md:86` | (a) jest opisem, nie werdyktem |
| M-17 | wynik | wynik oceny, wynik źródła, wynik przebiegu, wynik oceny właściciela | `prd.md:288`, `prd.md:271`, `prd.md:182` | zawsze z dopełnieniem |
| M-18 | strażnik | (a) watchdog; (b) test w CI; (c) deploy guard | (a) `api/README.md:136`; (b) «Strażnik zamkniętych zbiorów» `S-23/plan-brief.md:49`; (c) «Deploy guard» `REPOSITORY_GUIDELINES.md:67` | (a) to reguła produktu, (b) i (c) to inżynieria |

## Conflicting statements

„Status” mówi, czy dokument sam rozstrzyga konflikt. Syntezie zostaje porównać kod z nowszym zdaniem.

| id | temat | starsze | nowsze | status |
|---|---|---|---|---|
| C-01 | Pusty przebieg | 2026-10-18: «przebieg RANGE, w którym źródło nie dało ani jednego posta do zapisu» kończy się `failed` `prd.md:226`, `deploy-plan.md:1121` | 2026-10-29: dotyczy «przebiegu, w którym **żadne** źródło nie dało posta» `prd.md:271`, `deploy-plan.md:1935` | rozstrzygnięte w `prd.md:271`; `prd.md:226` bez noty |
| C-02 | Dzień pokryty | 2026-10-24: `prd.md:268`, «A range run ends `succeeded` / `partial` only when each source fetched without an error and gave at least one post» `deploy-plan.md:1642` | 2026-10-29: pokrycie tylko z wyniku `succeeded` `prd.md:271`, «This replaces S-13's sentence» `deploy-plan.md:1937` | rozstrzygnięte; `deploy-plan.md:1642` bez noty |
| C-03 | Data aktualizacji | 2026-10-12: jedna data: `prd.md:218-220`, `prd.md:225` | 2026-10-30: per źródło: `prd.md:227` | rozstrzygnięte, także Q-02 |
| C-04 | Reguła wyboru przebiegu dla daty | 2026-10-12: «`max(finished_at)` przebiegów RANGE» `S-05/plan-brief.md:49` | 2026-10-13: najdalszy zakres, potem najpóźniejszy koniec `prd.md:225`; per źródło `prd.md:227` | rozstrzygnięte |
| C-05 | Sygnał poza aplikacją | 2026-10-16: «Powiadomień poza aplikacją (push, e-mail) nie ma» `prd.md:264` | 2026-10-20 F-04 `prd.md:266`, 2026-10-25 S-18: zdanie F-04 «nie jest więc już prawdziwe» `prd.md:269` | rozstrzygnięte |
| C-06 | Sygnał z crona | 2026-10-12: «`failed`/`interrupted` → e-mail Railway» `S-05/plan-brief.md:29` | 2026-10-12: «nie przyszedł ani e-mail, ani powiadomienie» bez reguły `prd.md:263`; od F-04 Telegram `prd.md:266` | rozstrzygnięte |
| C-07 | Ton przerwania przez wdrożenie | S-06: «a neutral tone: expected after a merge» `deploy-plan.md:746`; S-11: «only `deploy` is neutral» `deploy-plan.md:1565` | 2026-10-27…29 S-22: «SPA → `warning` (`runTone` i `stepTone`)» `S-22/plan-brief.md:23` | **`deploy-plan.md` bez noty**, starsze zdania zostały |
| C-08 | Reddit na produkcji | «production stays without it until the operator sets it after S-28» `REPOSITORY_GUIDELINES.md:76`; «Reddit stays off on production» `deploy-plan.md:2155`; `deploy-plan.md:2017`, `api/README.md:105` | 2026-11-01: «włączony na produkcji 2026-11-01» `roadmap.md:84`, `roadmap.md:349` | starsze zdania prawdziwe w chwili pisania; **wymagają aktualizacji** |
| C-09 | Rola S-28 | `S-28/frame.md:12` (cytat starego roadmapowego ujęcia: progi) | 2026-10-31: «pomiar nie rozstrzyga progów kierunku celów i sprzeczności» `roadmap.md:257`, `deploy-plan.md:2073` | rozstrzygnięte |
| C-10 | Status implementacji Reddita | 2026-10-29: «bez implementacji» `prd.md:274` | 2026-10-30: «nie jest już prawdziwe» `prd.md:275` | rozstrzygnięte |
| C-11 | Klucze Reddita | «(klucze Reddita)» `roadmap.md:127`; «jego klucze nie zatrzymują wdrożeń» `roadmap.md:241`, `roadmap.md:385` | «bez klucza i rejestracji» `prd.md:242` | **roadmapa bez noty** |
| C-12 | Czas sesji | FR-029 «np. 5 minut» `prd.md:145` | 2026-10-02: 30 dni `roadmap.md:25`, `roadmap.md:314`, `api/README.md:293` | FR-029 nice-to-have, rozstrzygnięte tylko w roadmapie |
| C-13 | Ważność linku | «np. 24h» `prd.md:74` | 2026-10-12: «domyślnie 12 h» `prd.md:214` | rozstrzygnięte |
| C-14 | Zakres resetu | «jeszcze nieaktywowanego konta» `prd.md:204` | «dowolne konto» `prd.md:210-211` | rozstrzygnięte |
| C-15 | FR-022 | warunkowe: `prd.md:186`, `prd.md:304-305` | 2026-10-13: «zrealizowane w S-02» `prd.md:190` | rozstrzygnięte, stare zdanie zostało |
| C-16 | Co ocena robi | 2026-09-26: «czy jest wartościowy, dla jakiej spółki/tematu, jaki kierunek i siłę wydźwięku niesie» `shape-notes.md:322` | 2026-10-01: `prd.md:286-288`, `contract-v2.md:32` | rozstrzygnięte; `shape-notes.md` to notatki historyczne |
| C-17 | Wejście oceny | «Wejściem oceny jest wyłącznie treść posta (tytuł, treść; docelowo też komentarze z kontekstem rodzica)» `prd.md:288`; `shape-notes.md:322` mówi o metadanych | kontrakt daje też `item_type`, `published_at`, `channels`: `contract-v2.md:44-46`; `published_at` «służy do interpretacji względnego czasu» `contract-v2.md:61` | **nierozstrzygnięte w PRD**; kontrakt ma pierwszeństwo dla kształtu. Na Reddicie `channels` niesie subreddit `[inference]` |
| C-18 | Język tez | V1 «po polsku» `contract-v2.md:470`; pilot: «tezy po angielsku są akceptowalne» `F-02/frame.md:259` | reguła V2 `contract-v2.md:261`; 2026-10-30 «„Tezy po polsku” są problemem» `S-28/frame.md:39`; pomiar 22,1 % `S-28/report-2026-10-31.md:60`; 2026-10-31 «nie są warunkiem» `S-28/change.md:19` | **reguła stoi, odstępstwo przyjęte**; naprawa w Parked `roadmap.md:342` |
| C-19 | Progi | ≥ 75 % / ≤ 3 % `hardening-sources.md:7` | 2026-10-06: ≥ 70 % / ≤ 10 % `agent-implementation.md:116`, `measurement/README.md:161` | rozstrzygnięte; powrót to „LLM hardening” |
| C-20 | Backup | «odpowiedzialnością platformy hostingowej» `shape-notes.md:333`, `shape-notes.md:367` | 2026-10-11: `prd.md:280`, `prd.md:318` | rozstrzygnięte |
| C-21 | PITR | lekcja trial: «polegaj na PITR» `lessons.md:23` | 2026-10-11: «kopie wolumenu i PITR są tylko w planie Pro» `lessons.md:93` | rozstrzygnięte (append-only) |
| C-22 | Silnik przebiegu | Embabel: `S-02/plan-brief.md:26`, lekcja `lessons.md:86` | 2026-10-27: «bez Embabel w całym API» `roadmap.md:75` | rozstrzygnięte; lekcja bez noty |
| C-23 | Mapowanie posta Reddita | «post Reddita → `post` (tekstowy) lub `link`» `contract-v2.md:70` | 2026-10-29: «`is_self → post`, inaczej `link`» `S-27/plan.md:7`, `prd.md:242` | doprecyzowanie, kontrakt bez dopisku |
| C-24 | Komplet powodów uwagi | 2026-10-27: pięć kodów `S-22/plan-brief.md:16` | 2026-10-29: dodane `source_failed`, `source_empty` `deploy-plan.md:1931` | rozstrzygnięte |
| C-25 | `succeeded` z powodem uwagi | zasada: źródło nie-`succeeded` daje `partial` `pipeline/CLAUDE.md:64` | historia: «reason `source_empty` of a `succeeded` run» `deploy-plan.md:1956` | zamierzone, dotyczy przebiegów sprzed S-08 |
| C-26 | Rzadkość sygnału `dead` | S-06: «sygnał przy każdym `partial` byłby co noc; `dead` to 1 na ~1150 postów» `S-06/plan-brief.md:25` | 2026-10-31: «alarm szedłby zgrubnie w 3 nocach na 4» `S-28/report-2026-10-31.md:118`, `deploy-plan.md:2094` | **nierozstrzygnięte `[inference]`**: reguła „bez sygnału co noc” kontra Reddit; decyzja włączenia (OD-02) nie nazywa tego wprost |
| C-27 | „Zakres dat/godzin” | `prd.md:81`, `prd.md:36` | start przebiegu po dniach: «`{from, to, sources?}`» `api/README.md:290`, «`to` nie później niż dziś» `S-02/plan.md:409` | **PRD bez noty `[inference]`**; pytanie otwarte `roadmap.md:304` |
| C-28 | Aktywacja a logowanie | «ustawia własne hasło przy pierwszym logowaniu» `prd.md:169` | «The page creates no session: they log in as usual» `deploy-plan.md:601` | PRD bez noty |
| C-29 | Status S-09 w runbooku | «in progress … not merged» `deploy-plan.md:1350` | «Archived 2026-10-23» `roadmap.md:370`; reguła `lessons.md:138` | **`deploy-plan.md` bez noty** |
| C-30 | Limit czasu `db-backup` | `124`/`137` `deploy-plan.md:1196` | «Since S-25: `db-backup` reports a time limit as `1` too» `deploy-plan.md:1198` | rozstrzygnięte |
| C-31 | Ocena S-17 | v3.6 `roadmap.md:75` | 2026-10-27: «nota v3.6 zawyża» `roadmap.md:79` | rozstrzygnięte |
| C-32 | Pytania otwarte S-27 | `roadmap.md:249-251`, Backlog `roadmap.md:294-296` | S-27 i S-29 `done`; dostęp `prd.md:242`, noc: «Reddit is a source of every range run that names no sources» `deploy-plan.md:2022` | **roadmapa bez noty** przy S-27 |
| C-33 | Frontmatter roadmapy | «top_blocker: decisions # 7 pytań» `roadmap.md:9` | `roadmap.md:84`; Q-03, Q-04 rozstrzygnięte `roadmap.md:82` | **nieaktualne** |
| C-34 | Data PRD | «updated: 2026-10-13» `prd.md:6` | bloki do 2026-10-30 `prd.md:227`; pytanie o PRD v3 `roadmap.md:304` | otwarte |
| C-35 | Kotwice linii PRD | NFR nowego źródła jako «`prd.md:268`» `roadmap.md:93`; rozpoznawanie spółek «`prd.md:314`» `ui-ux-proposals.md:48` | dziś NFR stoi w `prd.md:272`, a Non-Goal w `prd.md:321` | **kotwice rozjechane**; nie czytać ich dosłownie |
| C-36 | Zakres M-4 a S-24 | «S-24 do decyzji przy otwieraniu M-4» `roadmap.md:308`, `verification-scope.md:39` | S-24 `done` `roadmap.md:113` | nieaktualne, warunek w `verification-scope.md:39` obejmuje przypadek |
| C-37 | Zakres MVP a Reddit | US-01 tylko Wykop `prd.md:54-58`; FR-023 pod nagłówkiem «Rozszerzenia (poza MVP)» `prd.md:237` | Reddit na produkcji `roadmap.md:349`; `prd.md:42` jako kryterium secondary | zgodne co do treści, **brak historyjki dla drugiego źródła `[inference]`** |

## Unknowns

| id | pytanie | zakres szukania | stan |
|---|---|---|---|
| U-01 | Czym jest „obiecujący trend” liczbowo i dokąd idzie powiadomienie (FR-025) | `prd.md:329`, `roadmap.md:302` | jawnie otwarte, owner: użytkownik |
| U-02 | Czy odświeżyć PRD do v3 | `roadmap.md:304` | jawnie otwarte |
| U-03 | Czy agregacja po tekście anchora wystarczy dla widoków wokół spółki w M-5, czy trzeba zmienić PRD | `ui-ux-proposals.md:48` | jawnie otwarte |
| U-04 | Gdzie powstaje język posta: pole wyjścia modelu czy detekcja backendu; kontrakt mówi, że język nie jest polem wejścia (`contract-v2.md:75`) | `roadmap.md:341` | Parked, wraca, «gdy właściciel wróci do tej potrzeby» |
| U-05 | Zakres M-4 | `verification-scope.md:3`, `roadmap.md:84` | M-4 nieotwarty, zakres do potwierdzenia |
| U-06 | Wynik nocy z Redditem 2.11 i 4.11 | `roadmap.md:84` | issue #121 otwarte; dokumenty kończą się na 2026-11-01 |
| U-07 | Retencja `notifications` i usuwanie powiadomień | dokumenty z listy „przeczytane”, `deploy-plan.md` w czytanych sekcjach | brak zapisu w tym zakresie |
| U-08 | Jaką etykietą SPA nazywa `link` Reddita po S-29 (dziś `link` = „znalezisko” wg S-27) | `S-27/research.md:150`, `S-29/plan-brief.md`, `deploy-plan.md` S-29 | dokumenty nie mówią; sprawdzić w kodzie |
| U-09 | Czy dzień przebiegu dla Reddita to dzień warszawski przeliczony na sekundy UTC; `sources/CLAUDE.md:87` mówi tylko «the range in the source's own clock» | `sources/CLAUDE.md`, `api/README.md` Reddit, `deploy-plan.md` S-27 | niedopowiedziane; sprawdzić w kodzie |
| U-10 | Jaki odsetek tez w złym języku jest dopuszczalny | `contract-v2.md`, `S-28/*`, `roadmap.md` | brak progu w czytanym zakresie |
| U-11 | Czy post zdjęty przez moderację po ocenie ma być kiedyś ukryty | `prd.md:281`, `S-27/plan-brief.md:74` | dziś zostaje żywy; przyszłość niezapisana |
| U-12 | Kto i kiedy wycofuje Reddita po styczniu–marcu 2027 | `deploy-plan.md:2097-2118` | runbook jest, daty decyzji nie ma |
| U-13 | Pełne znaczenia słownika slangu | `contract-v2.md:222` wskazuje instrukcję oznaczania i prompt; oba poza czytanym zakresem | nieczytane |

## Komendy

- `grep -n '^#' context/deployment/deploy-plan.md`, `wc -l` na dokumentach, `ls context/archive`, `ls context/changes`
- `find … -name '*.md' -exec wc -l` na folderach próbki
- `grep -n -iE` po decyzjach właściciela w `plan.md`, `research.md`, `change.md` zmian M-3
- `grep -n` po `REPOSITORY_GUIDELINES.md`, `api/README.md`, `tech-stack-api.md`, `infrastructure.md`, `measurement/README.md`, planie S-02, planie S-04
- `sed -n` dla pojedynczych linii planów i README
- `git log -1 --format='%h %ad'` dla `prd.md`, `contract-v2.md`, `shape-notes.md`; `git log` dla `deploy-plan.md`
- własny skrypt sprawdzający cytaty względem wskazanych linii (Python, w katalogu scratchpad)
