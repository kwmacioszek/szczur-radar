---
artifact: domain-glossary
created: 2026-11-15
source: context/domain/domain-distillation.md
---

# Glossary: Szczur Radar

Używaj tych słów w kodzie, testach, commitach i rozmowach. Gdy nazwa w kodzie różni się od terminu, w nowym kodzie wygrywa termin. Zmiana nazw w starym kodzie to osobna decyzja.

## Zbieranie i wspólny model

| term | means | name in code | don't call it |
|---|---|---|---|
| źródło | platforma, z której pobieramy posty: Wykop albo Reddit | `Source` (`sources/Source`) | `Provenance.Source` (to źródło wyniku, kontekst oceny) |
| element źródła | jedna pozycja z API źródła, zanim stanie się postem | `WykopItem`, `RedditItem` | post (jeszcze nim nie jest) |
| wspólny model | kształt posta niezależny od źródła, do zapisu i do oceny | `SourcePost`, `AssessmentInputV1` | model Wykopu |
| post | zebrana jednostka: wpis albo link, z etapem oceny | `Post`, tabela `posts` | wpis (słowo Wykopu), entry, item |
| typ posta | wspólna wartość `post` / `link` / `comment` | `ItemType`, `posts.item_type` | resource, typ Wykopu |
| kanał posta | miejsce w źródle: tag Wykopu albo nazwa subredditu | `posts.channel`, `SourcePost.channel` | kanał (bez dopowiedzenia), `llm.Channel`, kanał alarmowy |
| język posta | język posta zapisany przy poście; dziś język źródła | `posts.language`, `Language` | język tezy |
| duplikat | post o tym samym kluczu (źródło, typ, id w źródle), nie zapisywany drugi raz | `duplicates` | podobny post (podobne treściowo nie są duplikatami) |
| pominięty element | element pobrany, który nie stał się postem, także odrzucony przy zapisie | `skipped`, `rejected` | błąd, zgubiony post |
| RAW | oryginalny payload źródła, trzymany osobno od danych aplikacji | `RawPayload`, `raw_payloads` | dane posta |
| post zdjęty | post Reddita usunięty przez moderację lub autora; nie jest pobierany ani liczony | (bez typu; `RedditItem`) | pominięty element |
| źródło skonfigurowane | źródło, które ma ustawienia i może brać udział w przebiegu | `source_not_configured` (gdy nie) | źródło zarejestrowane (to stała i CHECK) |
| megawątek | zbiorczy wątek subredditu; filtru nie ma | *missing in code* | — |
| horyzont Arctic Shift | data, po której dostęp do danych Reddita może wygasnąć | *missing in code* | — |

## Ocena treści (kontrakt V2)

| term | means | name in code | don't call it |
|---|---|---|---|
| ocena | opis posta przez model według kontraktu V2; każda próba to osobny wiersz | `Assessment`, `AssessmentResult`, `assessments` | decyzja o sygnale (należy do człowieka), sentyment |
| kontrakt oceny | zamknięta specyfikacja wejścia, wyjścia i reguł oceny | `AssessmentOutputV2`, `ContractV2Rules` | kontrakt API (to pliki SPA ↔ API) |
| wejście oceny | treść posta plus typ, data publikacji i kanał; bez autora i źródła | `AssessmentInputV1`, `Item` | payload |
| trafność | czy post dotyczy inwestowania: `investment_related`, `not_investment_related`, `unclear` | `Relevance` | ważność, wartość |
| cel | podmiot, o którym mówi autor: spółka, aktywo, sektor, temat | `Target`, `TargetKind` | encja, ticker, spółka rozpoznana |
| anchor | najkrótszy dosłowny fragment tekstu nazywający cel | `anchor.quote` | nazwa spółki, ticker |
| stanowisko | kierunek autora wobec jednego celu, z ostrożnością | `Stance`, `Direction`, `Assertiveness` | sentyment, sentyment posta |
| teza | każdy element `claims`: twierdzenie, opinia, fakt, pytanie, działanie, z cytatem | `Claim`, `statement` | teza inwestycyjna (to jeden rodzaj tezy) |
| teza inwestycyjna | rodzaj tezy: ocena wyceny lub przyszłego zachowania inwestycji | `ClaimKind.INVESTMENT_THESIS` | teza (ogólnie) |
| cytat | dosłowny fragment pola posta, sprawdzany jako podciąg po normalizacji białych znaków | `evidence.quote`, `QuoteIsFragmentRule` | parafraza |
| klasa błędu oceny | powód, dla którego próba nie jest poprawną oceną | `FailureClass`, `failure_class` | status przebiegu |
| odsiew | wpis bez treści dostaje gotowy wynik bez wywołania modelu | `Prefilter`, `result_source = prefilter` | ocena modelu |
| źródło wyniku | czy wynik dał model, czy odsiew | `Provenance.Source`, `result_source` | źródło (to Wykop/Reddit) |
| pochodzenie oceny | dostawca, model, kanał LLM, wersje promptu i kontraktu przy ocenie | `Provenance` | — |
| kanał LLM | ścieżka wywołania modelu u dostawcy | `llm.Channel`, `assessments.channel` | kanał posta, kanał alarmowy |
| zestaw wzorcowy | ręcznie oznaczone posty do pomiaru modelu; tylko w testach | *missing in code* (tylko `measurement/`) | dane produkcyjne |
| budżet LLM | pułap kosztu modelu; dziś tylko limit na kluczu poza repo | *missing in code* | — |

## Przegląd ocenionych postów

| term | means | name in code | don't call it |
|---|---|---|---|
| feed | chronologiczna lista ocenionych postów z filtrem | `GET /api/posts`, `FeedView` | dashboard |
| filtr | wybór po tekście celu, rodzaju celu i kierunku stanowiska | `PostFilter`, `feedFilter.ts` | wyszukiwarka spółek |
| widok posta | ocena obok surowych danych posta | `PostDetailView`, `GET /api/posts/{id}` | — |
| pochodzenie posta | źródło i kanał przy poście, np. „Reddit · r/stocks” | `postOrigin` | źródło (samo) |
| typ posta słowami źródła | słowo typu wyświetlane per źródło: Wykop „wpis”/„znalezisko”, Reddit „post”/„link” | `ITEM_TYPE_WORDS` | typ posta (to wartość wspólna) |
| świeżość | data ostatniej udanej aktualizacji, osobno dla każdego źródła | `GET /api/freshness` | data ostatniego przebiegu |

## Przebiegi

| term | means | name in code | don't call it |
|---|---|---|---|
| przebieg | jedno wykonanie pobrania i oceny: `range`, `retry` albo `posts` | `PipelineRun`, `pipeline_runs` | przepływ, pipeline, job |
| przebieg dzienny | przebieg zakresu D-2…D-1 zlecony przez cron | `RANGE` od `SystemAccount.SCHEDULER` | osobny rodzaj przebiegu |
| zakres dat | pełne dni kalendarza warszawskiego; godzin nie ma | `range_from`, `range_to` | zakres godzin |
| status przebiegu | `queued`, `running`, `succeeded`, `partial`, `failed`, `interrupted` | `RunStatus` | etap (to post), wynik źródła |
| krok | faza przebiegu: pobranie, zapis, wybór, ocena, podsumowanie | `RunStep` | etap (to post) |
| wynik źródła | wynik jednego źródła w przebiegu: `succeeded`, `empty`, `failed` | `PipelineRunSource`, `SourceFetchStatus` | status przebiegu |
| etap posta | `stored`, `assessing`, `assessed`, `failed`, `dead` | `PostStage`, `posts.stage` | krok, status |
| próba posta | nieudana ocena z winy posta (odmowa albo zła odpowiedź); trzecia czyni post martwym | `assessment_attempts` | wywołanie modelu, ponowienie |
| martwy post | post po trzeciej próbie; wraca tylko przywróceniem admina | `PostStage.DEAD` | martwy przebieg |
| przywrócenie martwych | admin zeruje próby martwych postów; następny przebieg ocenia je od nowa | `restore`, `DeadPostController` | ponowienie (to przebieg `posts`) |
| resztki | posty `stored` i `failed` z próbami, brane przez przebieg `retry` | `leftovers`, `RunKind.RETRY` | martwe posty |
| błąd techniczny | awaria przy poście, po której przebieg idzie dalej; nie zużywa próby | `technical_failures`, `processing_errors` | próba |
| wstrzymany post | post po trzech błędach technicznych z rzędu; `retry` go pomija | (bez stanu; licznik) | martwy post |
| wyłącznik serii | trzy awarie dostawcy, błędy klienta albo odmowy z rzędu kończą przebieg `failed` | `ConcurrentAssessmentDispatcher` | bezpiecznik posta |
| strażnik | zatrzymuje przebieg bez postępu przez `PIPELINE_STALL_TIMEOUT` | `RunWatchdog` | — |
| osierocony przebieg | aktywny przebieg bez aktywności, oznaczany `interrupted`/`lost` przy żądaniu startu | `orphans`, `InterruptionCause.LOST` | martwy post |
| wymaga uwagi | jedna reguła powodów przebiegu dla dzwonka, strony i alertu | `AttentionReason`, `attentionReasons()` | `run_attention`, `RUN_NEEDS_ATTENTION` jako osobne pojęcia |
| dni bez danych | dni z ostatnich 14 bez udanego wyniku źródła | `MissingDays` | luka w danych |
| ponowienie wywołania | powtórzenie wywołania transportu (LLM albo źródła) | `TransportRetry`, `HttpRetry` | ponowienie resztek (`retry`) |

## Sygnał dla operatora i jakość ocen

| term | means | name in code | don't call it |
|---|---|---|---|
| admin | konto z rolą `admin`: dzwonek, panel, przywracanie | `Role.ADMIN` | operator |
| operator | osoba z dostępem do CLI, Railway, ntfy i Telegrama | (bez typu; `OperatorAlerts`) | admin |
| powiadomienie | wpis w dzwonku admina o przebiegu, który wymaga uwagi | `Notification` | alert |
| alert operatora | wiadomość do operatora poza aplikacją; zdarzenia API idą przez ntfy | `AlertEvent`, `NtfyAlerts` | powiadomienie |
| kanał alarmowy | droga alertów: ntfy dla API, healthchecks.io → Telegram dla cronów | `NTFY_URL`, `HEALTHCHECK_PING_URL` | kanał posta, kanał LLM |
| podsumowanie nocy | codzienna wiadomość z wynikami nocy, także przy 0 martwych | `NightSummary`, `NIGHT_SUMMARY` | alarm |
| noc | przebiegi jednego wywołania crona | (bez typu; lista id w żądaniu) | doba, dzień |
| jakość ocen per źródło | udział nieudanych prób i tez w innym języku w oknie 7 dni | `AssessmentQuality`, `QualityStatus` | jakość modelu (pomiar testowy) |
| przejście przez próg | noc, w której miara przekracza próg, a poprzednia nie; jedyny moment alarmu | `QualityCrossing`, `ASSESSMENT_QUALITY` | stan ponad progiem |
| kopia bazy | codzienny zrzut sprawdzony pełnym odtworzeniem, robiony poza API | `tools/db-backup` | backup w aplikacji |
| konto systemowe | konto crona, które nigdy się nie loguje | `SystemAccount.SCHEDULER` | admin |
