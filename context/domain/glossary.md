---
artifact: domain-glossary
created: 2026-11-01
source: context/domain/domain-distillation.md
---

# Glossary: Szczur Radar

Use these words in code, tests, commits and conversations. When a name in the code differs from the term, the term wins in new code; renaming old code is a separate decision.

## Pozyskanie i wspólny model

| term | means | name in code | don't call it |
|---|---|---|---|
| źródło | Serwis, z którego produkt pobiera posty; dziś Wykop i Reddit. | `Source` | źródło wyniku (to `model`/`prefilter`, kontekst oceny) |
| źródło zarejestrowane / skonfigurowane | Znane schematowi (stała i cztery `CHECK`) / mające ustawienia, więc pobierane. | `Source`, `SourceFetcher.configured()` | włączone |
| element | Pozycja zwrócona przez źródło, zanim stanie się postem. | `WykopItem`, `RedditItem` | post, entry |
| pozyskanie i mapowanie | Jedyna rzecz, którą zmienia nowe źródło: pobranie i przełożenie na wspólny model. | `SourceFetcher`, `RawInputMapper`, `<Source>Mapper` | ingest (tylko jako nazwa kroku) |
| wspólny model | Kształt posta niezależny od źródła. | `SourcePost`, `AssessmentInputV1` | — |
| post | Zapisana wypowiedź ze źródła, każdego typu. | `Post`, `posts` | wpis, entry, item |
| typ posta | Wartość wspólnego modelu: `post`, `link`, `comment`. | `ItemType`, `item_type` | zasób, `resource` (to pojęcie Wykopu) |
| wpis / znalezisko | Słowa Wykopu dla typów `post` i `link`; na Reddicie decyzja otwarta (Q-13). | `WykopResource.ENTRY`, `WykopResource.LINK` | nazwa typu niezależna od źródła |
| komentarz | Typ zarezerwowany, poza MVP; nic go dziś nie tworzy. | `ItemType.COMMENT` | — |
| kanał posta | Miejsce w źródle: tag Wykopu albo subreddit. | `posts.channel`, `SourcePost.channel` | kanał (bez dopełnienia), `Channel` (to trasa modelu) |
| pochodzenie posta | Źródło i kanał razem, np. `Reddit · r/stocks`. | `postOrigin` | — |
| duplikat | Post o tym samym kluczu źródłowym; treściowo podobne posty duplikatami nie są. | `uq_posts_source_item`, `PostKey` | — |
| RAW | Oryginalny payload źródła, trzymany bezstratnie osobno. | `RawPayload`, `raw_payloads` | „Dane dla nerdów” (tylko etykieta UI) |
| pominięty element | Pobrany element, z którego nie powstał post. | `skipped` | post zdjęty |
| post zdjęty | Post usunięty w źródle przed pobraniem; nie jest elementem pobrania. | `RedditItem.removed()` | skipped, pominięty |
| megawątek | Cykliczny wątek zbiorczy Reddita; produkt go nie filtruje. | *missing in code* | — |
| horyzont Arctic Shift | Ryzyko końca danych Reddita w styczniu–marcu 2027; runbook operatora. | *missing in code* | — |

## Ocena

| term | means | name in code | don't call it |
|---|---|---|---|
| ocena | Opis posta wykonany przez model według kontraktu; nie werdykt. | `Assessment`, `assessments` | osąd, decyzja (to rola człowieka), signal |
| kontrakt oceny | Normatywny kształt i reguły oceny, wersja V2. | `AssessmentOutputV2`, `RuleId` | kontrakt (bez dopełnienia; jest też kontrakt SPA ↔ API) |
| wejście oceny | To, co widzi model: typ, data, kanały, tytuł, treść. | `AssessmentInputV1`, `Item` | — |
| trafność | Czy post dotyczy inwestowania. | `Relevance` | — |
| cel | Podmiot wypowiedzi: spółka, aktywo, sektor, temat; bez rozpoznania tożsamości. | `Target` | spółka (cel nią nie musi być), `RangeTarget` (to zakres przebiegu) |
| anchor | Najkrótszy fragment tekstu nazywający cel. | `Anchor` | ticker, nazwa spółki |
| stanowisko | Kierunek autora wobec jednego celu. | `Stance`, `Direction` | sentyment, wydźwięk |
| teza | Każde twierdzenie autora z cytatem. | `Claim` | teza inwestycyjna (to jeden `ClaimKind`) |
| teza inwestycyjna | Rodzaj tezy: ocena wyceny lub przyszłego zachowania inwestycji. | `ClaimKind.INVESTMENT_THESIS` | teza (bez dopełnienia) |
| cytat | Dosłowny fragment posta popierający tezę. | `Evidence`, `Quote` | dowód |
| flagi, modyfikatory | Jawna niepewność i charakter treści. | `Flag`, `Modifier` | — |
| klasa błędu | Zamknięty powód nieudanej oceny. | `FailureClass` | `fabricated_evidence` = zmyślenie (to niedopasowany cytat) |
| pochodzenie oceny | Czym i którą wersją powstała ocena. | `Provenance` | pochodzenie posta |
| źródło wyniku | `model` albo `prefilter`. | `Provenance.Source`, `result_source` | źródło (bez dopełnienia) |
| wpis bez treści | Post bez 3 znaków treści; dostaje wynik bez modelu. | `Prefilter` | — |
| zestaw wzorcowy | Oznaczone ręcznie posty do pomiaru progów; tylko testy. | *missing in code* (produktu), `measurement/` | — |
| budżet LLM | Limit $10/mies. na kluczu dostawcy; bez pułapu w kodzie. | *missing in code* | — |

## Feed

| term | means | name in code | don't call it |
|---|---|---|---|
| feed | Chronologiczna lista ocenionych postów. | `findAssessedPage`, `FeedView` (bez typu) | — |
| filtr | Zawężenie feedu po celu i stanowisku. | `PostFilter`, `q`/`kind`/`direction` | — |
| świeżość | Per źródło: data ostatniego udanego pobrania tego źródła. | `Freshness`, `SourceUpdate` | aktualizacja (bez dopełnienia), pokrycie dnia |

## Przebiegi

| term | means | name in code | don't call it |
|---|---|---|---|
| przebieg | Jedno wykonanie sekwencji kroków. | `PipelineRun` | przepływ, uruchomienie, pipeline |
| rodzaj przebiegu | `RANGE`, `RETRY` albo `POSTS`. | `RunKind` | — |
| przebieg resztek / wybranych postów | `RETRY` ponawia resztki; `POSTS` uruchamia „Ponów zaznaczone”. | `RunKind.RETRY`, `RunKind.POSTS` | ponowienie (bez dopełnienia), `retryPosts` |
| przebieg dzienny | `RANGE` z crona dla dni D-2…D-1 czasu warszawskiego. | `scheduled()`, `SCHEDULER` (bez typu) | osobny rodzaj przebiegu |
| krok | Etap: `fetch`, `store`, `select`, `assess`, `summarize`. | `RunStep`, `PipelineRunStep` | — |
| status przebiegu | `queued`, `running`, `succeeded`, `partial`, `failed`, `interrupted`. | `RunStatus` | — |
| przebieg zakończony błędem | Przebieg `failed` albo `interrupted`; `failed` bez żadnego źródła z postem to pusty przebieg. | `RunStatus`, `NoPostsFetchedException` | failed (bez rzeczownika) |
| wynik źródła | `succeeded`, `empty` albo `failed` jednego źródła w przebiegu. | `PipelineRunSource`, `SourceFetchStatus` | outcome, sourceStatus |
| etap posta | `stored`, `assessing`, `assessed`, `failed`, `dead`. | `PostStage` | status posta |
| próba oceny | Zapisana nieudana ocena; trzy czynią post martwym. | `assessment_attempts` | `assessments.attempts` (to próby HTTP), wiersz oceny |
| post martwy | Post bez poprawnej oceny po trzech próbach; stan końcowy. | `PostStage.DEAD` | przebieg `dead` (to sierota) |
| resztki | Posty do ponowienia przez przebieg resztek. | `findLeftoverIds` (bez typu) | — |
| błąd techniczny | Awaria niezawiniona przez post; nie zużywa próby. | `technical_failures`, `ProcessingError` | próba |
| post wstrzymany | Post po trzech błędach technicznych z rzędu; zachowuje etap. | `onHold` (bez stanu) | etap |
| trzy winy | Podział porażki: post, dostawca, nasz kod. | `Assessor`, `ConcurrentAssessmentDispatcher` (bez typu) | — |
| bezpiecznik | Zatrzymanie kroku po serii awarii. | `OUTAGE_STREAK`, `CLIENT_ERROR_STREAK` | strażnik |
| ponowienie wywołania | Ponowienie HTTP, poniżej kroku. | `TransportRetry`, `HttpRetry` | próba, retry przebiegu |
| strażnik | Kończy przebieg własnej instancji bez aktywności. | `RunWatchdog` | bramka wdrożeń, deploy guard |
| przerwanie | Koniec `interrupted` z przyczyną. | `InterruptionCause` | — |
| sierota | Przebieg bez wykonawcy, oznaczany `lost` przy żądaniu startu. | `interruptOrphans`, `lost` | dead run, zombie |
| wymaga uwagi | Jedna reguła API: przebieg ma powody uwagi. | `attentionReasons`, `AttentionReason` | — |
| dni bez danych | Dni bez pokrycia per źródło, uzupełniane ręcznie. | `MissingDays` | świeżość |
| pokrycie dnia | Dzień w udanym przebiegu z wynikiem `succeeded` źródła. | `CoveredRange` | aktualizacja |
| pobrane | Elementy pobrania bez postów zdjętych. | `fetched` | „Pobrane” z raportu S-28 (ze zdjętymi) |

## Operacje i dostęp

| term | means | name in code | don't call it |
|---|---|---|---|
| admin | Rola konta w aplikacji; odbiorca dzwonka. | `Role.ADMIN` | operator |
| operator | Człowiek z dostępem do CLI i Railway; odbiorca alertów. | bez typu (`OperatorAlerts`) | admin |
| konto systemowe | Konto bez hasła, zleceniodawca przebiegu dziennego. | `SystemAccount.SCHEDULER` | — |
| powiadomienie | Wiersz w aplikacji dla każdego admina; dzwonek. | `Notification` | alert |
| alert | Wiadomość do operatora poza aplikacją. | `AlertEvent`, `OperatorAlertQueue` | powiadomienie, sygnał |
| kanał alarmowy | Droga alertu: healthchecks.io i Telegram. | `HealthchecksAlerts` | kanał (bez dopełnienia) |
| kopia bazy | Dump sprawdzony pełnym odtworzeniem; poza API. | `tools/db-backup/` | — |
| sygnał inwestycyjny | Decyzja człowieka, że post jest wart uwagi; model jej nie wydaje. | brak (celowo) | sygnał (bez dopełnienia), alert |
