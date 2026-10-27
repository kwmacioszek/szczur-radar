---
artifact: domain-glossary
created: 2026-10-27
source: context/domain/domain-distillation.md
---

# Glossary: Szczur Radar

Use these words in code, tests, commits and conversations. When a name in the code differs from the term, the term wins in new code; renaming old code is a separate decision.

## Pozyskanie i wspólny model (B-01, B-03, B-04)

| term | means | name in code | don't call it |
|---|---|---|---|
| źródło danych | serwis, z którego pobieramy wypowiedzi (dziś Wykop; w przyszłości Reddit) | `Source` (`api/sources`) | „źródło wyniku” (to `Provenance.Source`: model / prefilter) |
| element źródła | jedna pozycja w odpowiedzi API źródła, zanim stanie się postem | `WykopItem`, `SourceFetcher.Fetched` | „post” (dopóki nie został zapisany) |
| pozyskanie i mapowanie | pobranie elementów ze źródła i odwzorowanie ich na wspólny model | `SourceFetcher`, `RawInputMapper`, `WykopMapper` | „ingest” tylko dla kroku silnika (`SourceIngest`) |
| wspólny model | kształt posta niezależny od źródła: do zapisu i do oceny | `SourcePost` (zapis), `AssessmentInputV1` (ocena) | „model” (to także model językowy) |
| post | każda zapisana wypowiedź ze źródła, niezależnie od typu (wpis, znalezisko, komentarz) | `Post`, tabela `posts` | „wpis” (to tylko typ `post`) |
| typ elementu | rodzaj wypowiedzi we wspólnym modelu: `post`, `link`, `comment` | `ItemType` | nazwy źródła (`entry`, `link` Wykopu) |
| wpis | typ `post`: tekstowa wypowiedź (wpis mikroblogowy Wykopu, tekstowy post Reddita) | `ItemType.POST` | „post” w znaczeniu encji |
| znalezisko | typ `link`: wypowiedź, której treścią jest odnośnik z opisem | `ItemType.LINK` | — (słowo wykopowe; w kodzie neutralne `link`) |
| komentarz | typ `comment`: odpowiedź pod inną wypowiedzią; przewidziany w kontrakcie, nieużywany w MVP | `ItemType.COMMENT`, `Context.parentComment` | — |
| tag / kanał treści | miejsce, z którego pochodzi element (tag `#gielda`, subreddit) | `Item.channels`, `posts.tags`, `WykopPosts.TAG` | „kanał alarmowy”; `llm.Channel` (trasa wywołania modelu) |
| duplikat | post o tym samym `(source, item_type, source_item_id)`; odrzucany; podobna treść od innego autora to nie duplikat | `uq_posts_source_item`, `PostStore` | — |
| surowy payload (RAW) | oryginalny JSON źródła, zapisany bezstratnie i osobno od posta | `RawPayload`, `RawPayloadStore` | „dane dla nerdów” (tylko etykieta UI) |
| pominięte elementy | elementy pobrane, z których nie powstał post (zły kształt, nie do zmapowania) | licznik `skipped`, `UnmappableItemException` | „skipped” crona (pominięcie poza produkcją) |
| zakres dat | dni objęte przebiegiem zakresu; nocny przebieg to D-2…D-1 w kalendarzu warszawskim | `RangeTarget`, `InternalPipelineRunController.daily` | „zakres godzin” (nie istnieje) |

## Ocena treści (kontrakt V2) (B-02, B-09)

| term | means | name in code | don't call it |
|---|---|---|---|
| ocena | jedna próba opisania posta przez model według kontraktu; udana (`ok`) albo nieudana (`failed`) | `Assessment`, `AssessmentResult`, tabela `assessments` | „klasyfikacja”, „sentyment” |
| kontrakt oceny V2 | zamknięty kształt i reguły wyjścia modelu; przy sprzeczności obowiązuje `contract-v2.md` | `AssessmentOutputV2`, schemat JSON, prompt, `ContractV2Rules` | — |
| wejście oceny | tytuł, treść, typ i kanały wypowiedzi; bez źródła, autora, linku i języka | `AssessmentInputV1`, `Item`, `Context` | — |
| trafność | czy wypowiedź dotyczy inwestowania: `investment_related`, `not_investment_related`, `unclear` | `Relevance` | „istotność”, „wartościowość” |
| cel | podmiot, o którym mówi autor: spółka, aktywo, sektor, temat makro | `Target`, `TargetKind` | `RangeTarget`/`SelectionTarget` (cele kroków silnika) |
| anchor | najkrótszy fragment tekstu nazywający cel, dokładnie jak w tekście | `Anchor` | „nazwa spółki” (nie ma rozpoznawania spółek) |
| stanowisko | kierunek autora wobec celu (`bullish`, `bearish`, `neutral`, `mixed`, `not_expressed`, `unclear`) i sposób sformułowania (`hedged` / `unhedged`) | `Stance`, `Direction`, `Assertiveness` | „sentyment”, „ton” |
| wypowiedź autora (claim) | to, co autor twierdzi: fakt, opinia, teza inwestycyjna, pytanie albo działanie, zawsze z jednym dosłownym cytatem | `Claim`, `ClaimKind` | „teza” jako nazwa całości |
| teza inwestycyjna | jeden rodzaj wypowiedzi autora: przewidywanie co do ceny lub wyceny | `ClaimKind.INVESTMENT_THESIS` | „teza” dla każdego `Claim` |
| atrybucja | czyje jest stanowisko w wypowiedzi: autora, przytoczone, niejasne | `Attribution` | — |
| cytat | dosłowny podciąg wskazanego pola po normalizacji białych znaków | `Evidence`, `Quote` | „parafraza” |
| normalizacja białych znaków | każdy znak biały → spacja, ciągi → jedna spacja, obcięcie końców; nic więcej | `text/Whitespace` | `trim()` |
| zastrzeżenia (flagi) | ostrzeżenia modelu: brak kontekstu, możliwy sarkazm, za mało treści, limit ekstrakcji | `Flag` | — |
| modyfikatory | humor lub promocja istotne dla interpretacji | `Modifier` | „flagi” |
| klasa błędu oceny | powód nieudanej oceny: błąd dostawcy, przekroczony czas, zła struktura, schemat, odwołanie, zmyślony cytat, naruszenie reguł | `FailureClass` | „błąd techniczny” (to licznik posta, nie klasa oceny) |
| walidator domenowy | reguły D1–D15 sprawdzane po parsowaniu; naruszenie = ocena nieudana | `RuleId`, `ContractV2Rules` | „schemat” (to osobna warstwa) |
| pochodzenie oceny | dane wywołania: dostawca, model, tier, wersje promptu i kontraktu, tokeny, próby HTTP | `Provenance` | — |
| źródło wyniku | czy ocenę dał model, czy odsiew bez wywołania (`model` / `prefilter`) | `Provenance.Source`, `result_source` | „źródło danych” |
| odsiew (prefiltr) | wpis bez treści (< 3 znaki po zdjęciu linków, tagów, wzmianek) dostaje gotowy wynik bez modelu | `Prefilter` | „filtr” (to filtr feedu) |
| dostawca / model / tier | skąd i jakim modelem wołamy; `flex` to tańszy tryb OpenRouter | `Provider`, `ServiceTier`, `LlmProviderProperties` | — |
| ponowienie wywołania | kolejna próba HTTP tego samego wywołania modelu po błędzie transportu | `TransportRetry`, `llm_retries` | „próba oceny” |
| zestaw wzorcowy | ~120 oznaczonych postów do pomiaru modeli; tylko w kodzie testowym | `measurement/` (testy) | — |

## Przegląd ocenionych postów (B-10)

| term | means | name in code | don't call it |
|---|---|---|---|
| feed (lista postów) | chronologiczna lista postów `assessed` z najnowszą oceną `ok` | `PostRepository.findAssessedPage`, `FeedView` | „lista nieudanych” |
| widok szczegółowy posta | pełny tekst, ocena pogrupowana po celach, link do oryginału, RAW pod przełącznikiem | `GET /api/posts/{id}`, `PostDetailView` | — |
| filtr | zawężenie feedu po fragmencie celu (`q`), rodzaju celu i stanowisku; wszystkie warunki na jednym celu | `PostFilter`, `feedFilter.ts` | „wyszukiwarka”, „prefiltr” |
| świeżość danych | koniec ostatniego udanego przebiegu zakresu (`succeeded` lub `partial`), pokazywany nad feedem | `findLastUpdate`, `Freshness` | „aktualizacja” w sensie zmiany istniejących postów; „pokrycie” (to per dzień) |

## Przebiegi pipeline'u (B-05, B-07)

| term | means | name in code | don't call it |
|---|---|---|---|
| przebieg | jedno wykonanie pipeline'u w tle: zakres dat, resztki albo wybrane posty | `PipelineRun`, tabela `pipeline_runs` | „przepływ”, „uruchomienie”, „pipeline” (to cały moduł) |
| przebieg zakresu | przebieg pobierający i oceniający dni z zakresu | `RunKind.RANGE` | — |
| przebieg resztek | przebieg ponawiający posty do oceny (bez wstrzymanych); zawsze przed zakresem | `RunKind.RETRY` | „ponowienie” (to też HTTP), `retryPosts()` (tworzy przebieg wybranych postów) |
| przebieg wybranych postów | przebieg nad postami zaznaczonymi przez admina, także wstrzymanymi | `RunKind.POSTS`, „Ponów zaznaczone” | „retry” |
| przebieg dzienny | nocny przebieg zakresu D-2…D-1 zlecony przez konto systemowe o 00:30 UTC | `InternalPipelineRunController.daily`, `SystemAccount.SCHEDULER`, `scheduled()` | „daily”, „cron”, „trigger” (narzędzie) |
| status przebiegu | `queued`, `running`, `succeeded`, `partial`, `failed`, `interrupted`; `partial` liczy się do świeżości, ale może wymagać uwagi | `RunStatus` | „status kroku”, „status oceny” |
| krok | etap przebiegu: `fetch`, `store`, `select`, `assess`, `summarize`; otwiera się tylko w przebiegu `running` | `RunStep`, `PipelineRunStep`, `StepStatus` | — |
| kolejka | jeden przebieg naraz; nowe żądanie przy aktywnym przebiegu dostaje 409 `pipeline_busy` | wątek `pipeline-run`, blokada doradcza, `uq_pipeline_runs_one_running` | — |
| resztki | posty jeszcze do oceny: `stored` albo `failed` z próbami, bez wstrzymanych | `findLeftoverIds` | „nieudane posty” (szersze) |
| próba oceny | policzona nieudana ocena posta (odmowa dostawcy albo zła odpowiedź modelu); trzecia daje `dead` | `assessment_attempts`, `MAX_ASSESSMENT_ATTEMPTS` | „próba HTTP” (`attempts` w `assessments`) |
| post po nieudanej próbie | post z 1–2 policzonymi próbami, do ponowienia | `PostStage.FAILED` | „nieudana ocena” |
| post martwy | post po trzeciej policzonej próbie; nie wraca do oceny | `PostStage.DEAD` | — |
| błąd techniczny | nieudana ocena nie z winy treści ani dostawcy (błąd kodu, pojedyncza awaria); nie zużywa próby | `technical_failures`, `processing_errors`, `ProcessingError` | „próba” |
| wstrzymany post | post z trzema błędami technicznymi z rzędu; przebieg resztek go pomija, admin może ponowić | `onHold` (pochodna `technical_failures >= 3`) | „martwy” |
| awaria dostawcy | wywołanie modelu nieudane po ponowieniach (5xx, timeout, 429); nie zużywa próby | `LlmUnavailableException` | „odmowa modelu” |
| bezpiecznik | trzy awarie dostawcy albo trzy błędy klienta z rzędu kończą przebieg `failed` | `OUTAGE_STREAK`, `CLIENT_ERROR_STREAK` | „limit prób” |
| strażnik (watchdog) | kończy przebieg bez postępu przez `PIPELINE_STALL_TIMEOUT` jako `failed` i oddaje posty | `RunWatchdog`, `PipelineStallSettings` | „timeout wywołania” |
| przyczyna przerwania | dlaczego przebieg jest `interrupted`: `deploy`, `restart`, `shutdown`, `lost` | `InterruptionCause` | — |
| sieroty | przebiegi aktywne bez instancji, która je wykonuje; sprzątane przy żądaniu startu | `cleanUpOrphans`, `interruptOrphans`, `InterruptionCause.LOST` | — |
| pusty przebieg | przebieg zakresu, w którym źródło nie dało ani jednego posta; kończy się `failed` | `NoPostsFetchedException` | „pusty dzień” |
| wymaga uwagi | przebieg `failed`, `interrupted`, `partial` z martwym postem albo błędem technicznym, lub z pominiętymi elementami | `needsAttention`, `run_attention`, `RUN_NEEDS_ATTENTION` | „zakończony błędem” (to tylko `failed`/`interrupted`) |
| dni bez danych | dni z ostatnich 14 (do D-2) bez udanego przebiegu zakresu dla danego źródła | `MissingDays`, `Coverage` | „luka świeżości” |

## Sygnał dla operatora i kopia danych (B-06, B-08)

| term | means | name in code | don't call it |
|---|---|---|---|
| admin | rola konta w aplikacji z dostępem do panelu `/admin` i dzwonka | `Role.ADMIN`, `/api/admin/**` | „operator” |
| operator | osoba z dostępem do wdrożenia, CLI i sekretów; odbiorca alertów; nie jest kontem | tylko w nazwach `OperatorAlerts`, `OperatorAlertQueue` | „admin” |
| konto systemowe | konto bez hasła i logowania, zleceniodawca przebiegu dziennego | `SystemAccount.SCHEDULER`, `login_enabled = false` | „cron” |
| powiadomienie | wpis w aplikacji dla każdego admina o przebiegu, który wymaga uwagi; dzwonek w nagłówku | `Notification`, `NotificationKind.RUN_ATTENTION`, `NotificationBell` | „alert” |
| alert operatora | zdarzenie wysyłane poza aplikację (healthchecks.io → Telegram) | `AlertEvent`, `OperatorAlertQueue`, `HealthchecksAlerts` | „sygnał inwestycyjny”, „powiadomienie” |
| kanał alarmowy | droga alertu poza aplikacją: check healthchecks.io i wiadomość w Telegramie | `HEALTHCHECK_PING_URL`, `tools/healthchecks` | „kanał treści” |
| sygnał inwestycyjny | ocena człowieka, że post jest wart uwagi; poza oceną modelu | — (celowo brak) | `is_signal`, `score`, `confidence` |
| kopia bazy | codzienny `pg_dump` sprawdzony pełnym odtworzeniem, w buckecie i na NAS; 14 dziennych + 12 miesięcznych | `tools/db-backup/backup.sh`, `restore-check.sh`, `tables.sh` | — |
