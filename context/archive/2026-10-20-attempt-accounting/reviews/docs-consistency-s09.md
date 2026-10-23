# S-09 attempt-accounting: spójność dokumentacji z kodem (krok 5.4)

- Data: 2026-10-21
- Commit: `2a29202` (gałąź `feature/attempt-accounting`)
- Charakter: tabelę przygotował agent (grep i czytanie kodu, bez uruchamiania testów, Mavena ani npm). Człowiek ją akceptuje i decyduje o każdej rozbieżności. To nie jest niezależny dowód: agent czytał kod tak samo jak autor dokumentów i mógł przeoczyć to samo.
- Konwencja ścieżek: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`, `web/…` = `szczur-radar-web/src/…`. Numery linii kodu według `HEAD`.
- Uwaga o stanie drzewa roboczego: przy pierwszym odczycie `api/pipeline/step/AssessmentSelection.java:63` miał dodatkowy warunek `getTechnicalFailures() < Post.MAX_TECHNICAL_FAILURES` w gałęzi POSTS. To był chwilowy break-check kroku 2.5 z innej sesji. Przy ponownym odczycie plik był równy `HEAD`, a `git status` nie pokazywał zmiany. Cytuję stan z `HEAD`. Test `AssessmentSelectionTest.aPostsRunTakesPostsOnHold` (`test/pipeline/step/AssessmentSelectionTest.java:79`) jest w commicie `805c912` (krok 2.5); w chwili odczytu agenta nie był jeszcze zacommitowany.

Werdykty: **ZGODNE**, **ROZBIEŻNE**, **NIEWERYFIKOWALNE** (z wyjaśnieniem).

## 1. `context/foundation/prd.md:263` (doprecyzowanie S-09)

| # | Twierdzenie | Kod / test | Werdykt |
| --- | --- | --- | --- |
| P1 | „awaria dostawcy LLM po ponowieniach (5xx, przekroczenie czasu, brak połączenia albo 429 …) nie zużywa próby” | `api/llm/transport/TransportRetry.java:112-121` (wyczerpany tier → `LlmUnavailableException`), `api/agents/assessor/Assessor.java:131-133` (rzuca dalej), `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:261-279` (`release`, brak `record`); testy `test/agents/assessor/AssessorTest.java:112`, `test/pipeline/assess/ConcurrentAssessmentDispatcherTest.java:420` | ZGODNE |
| P2 | „Próbę zużywa tylko odmowa dostawcy albo zła odpowiedź modelu” | `Assessor.java:134-136` (zwykły `LlmCallException` → `failed`), `:88-106` (parser i walidator), `api/pipeline/state/PostTransitions.java:202-208` (próba +1, trzecia → `dead`) | ZGODNE |
| P3 | Trzy awarie z rzędu → `failed` z błędem „the LLM provider is unavailable: 3 calls in a row failed after their retries …” | `ConcurrentAssessmentDispatcher.java:267-275`, `api/pipeline/flow/RunFlow.java:136,152-153` (prefiks „stopped, the remaining posts were put back: ”, status `FAILED`); testy `ConcurrentAssessmentDispatcherTest.java:425-427`, `test/pipeline/PipelineAgentIT.java:614-649`, `test/pipeline/LlmOutageIT.java:98-122` | ZGODNE |
| P4a | Sygnały: powiadomienie dla każdego admina i kod wyjścia crona 1 | `api/pipeline/run/PipelineRun.java:287-289` (`FAILED` → `needsAttention`), `tools/pipeline-trigger/trigger.sh:14,257-259,281-283`; testy `PipelineAgentIT.java:648`, `LlmOutageIT.java:119-121` | ZGODNE |
| P4b | … i push Railway „deployment crashed” | Reguła powiadomień konta Railway jest poza repozytorium. Kod daje tylko kod wyjścia ≠ 0 | NIEWERYFIKOWALNE (konfiguracja konta Railway) |
| P5 | Posty wracają bez prób i bez błędów technicznych; RETRY bierze je przed kolejnym przebiegiem zakresu | `ConcurrentAssessmentDispatcher.java:184-190` (przy `stoppedBy != null` bez odroczonego liczenia), `:276-278`; `api/pipeline/PipelineRunService.java:151` (RETRY przed RANGE przy `existsLeftover`); test `PipelineAgentIT.java:636-644` | ZGODNE |
| P6 | Pojedyncza awaria = błąd techniczny posta, przebieg `partial`, powiadomienie | `ConcurrentAssessmentDispatcher.java:184-189,345-363`, `RunFlow.java:155`, `PipelineRun.java:290`; test `ConcurrentAssessmentDispatcherTest.java:448` | ZGODNE |
| P7 | Trzy błędy techniczne z rzędu (awarie albo nasz kod) wstrzymują post: RETRY go nie bierze | `api/posts/Post.java:46` (`MAX_TECHNICAL_FAILURES = 3`), `api/posts/PostRepository.java:38-58`, `PostTransitions.java:130-157`; test `test/posts/PostRepositoryIT.java:106` | ZGODNE |
| P8 | Wstrzymany jest na liście „Nieudane posty” z liczbą błędów i ostatnim błędem; admin może go ponowić | `PostRepository.java:80-88`, `api/posts/api/FailedPostController.java:83-93`, `web/views/AdminView.vue:196,349-372`, `api/pipeline/step/AssessmentSelection.java:55-65` (POSTS bierze `stored`/`failed` bez filtra wstrzymania); testy `test/posts/api/PostsApiIT.java:396`, `web/views/AdminView.test.ts:362` | ZGODNE |
| P9 | Każdy zapisany wynik oceny zeruje licznik | `PostTransitions.java:94-95`; test `PostRepositoryIT.java:157` | ZGODNE |

## 2. `context/deployment/deploy-plan.md`: sekcja „## S-09” (`:1172-1244`)

| # | Twierdzenie | Kod / test | Werdykt |
| --- | --- | --- | --- |
| D1 | `:1174` „Status (2026-10-20): in progress … not merged” | To stan procesu, nie kodu | NIEWERYFIKOWALNE (nie dotyczy kodu) |
| D2 | `:1178` Wina posta: nieponawialny 4xx, odmowa albo blokada, brak tekstu, naruszenie kontraktu → zapisany `failed`; trzeci daje `dead` | `api/llm/transport/TransportException.java:40-44,88`, `TransportRetry.java:103-105`, `Assessor.java:134-136`, `PostTransitions.java:202-208` | ZGODNE |
| D3 | `:1179` Wina dostawcy: ponowienia wyczerpane na 429/5xx/timeoutach/błędach połączenia, przy flex oba tiery | `TransportRetry.java:93-121`; test `test/llm/transport/TransportRetryTest.java:208` | ZGODNE |
| D4 | `:1180` „3 z rzędu, w kolejności zakończenia, bez wywołania z odpowiedzią modelu między nimi; post z prefiltra nie przerywa serii” | `ConcurrentAssessmentDispatcher.java:151,244-247,267`; testy `ConcurrentAssessmentDispatcherTest.java:483,504` | ZGODNE (drobna nieścisłość niżej, U-c) |
| D5 | `:1180` Błąd przebiegu `stopped, the remaining posts were put back: the LLM provider is unavailable: 3 calls in a row failed after their retries; the last: failed after N attempts: <provider message>` | `RunFlow.java:136`, `ConcurrentAssessmentDispatcher.java:270-271`, `TransportRetry.java:120-121`. Gdy ostatnia „awaria” to przerwanie, końcówka brzmi „interrupted between attempts” (`TransportRetry.java:141`) | ZGODNE |
| D6 | `:1180` Każdy post wraca bez próby i bez błędu technicznego | `ConcurrentAssessmentDispatcher.java:184` (całe odroczone liczenie pominięte), `:276-278`; test `ConcurrentAssessmentDispatcherTest.java:436-438`, `LlmOutageIT.java:115-116` | ZGODNE |
| D7 | `:1181` Pojedyncza awaria: post wraca bez próby, z +1 błędem technicznym i komunikatem; przebieg liczy błąd przetwarzania i kończy się `partial` | `ConcurrentAssessmentDispatcher.java:345-363`, `PostTransitions.java:148-157`; test `ConcurrentAssessmentDispatcherTest.java:448-480` | ZGODNE |
| D8 | `:1182` Wina naszego kodu (mapper, nieznany wyjątek SDK, który od S-09 nie jest już `failed provider_error`, nieudany zapis) → bez próby, +1 błąd techniczny, `processing_errors` | `ConcurrentAssessmentDispatcher.java:237-239,280-306`, `TransportException.java:130-132`, `api/llm/openrouter/OpenRouterClient.java:196-204`, `api/llm/deepseek/DeepSeekClient.java:139-149`, `api/llm/gemini/GeminiClient.java:107-108,124-135`; testy `ConcurrentAssessmentDispatcherTest.java:224`, `anUnknownSdkErrorLeavesTheClientUnclassifiedAfterOneCall` (`OpenRouterClientTest.java:484`, `DeepSeekClientTest.java:227`, `GeminiClientTest.java:235`) | ZGODNE |
| D9 | `:1183` Wstrzymanie: V10; RETRY go nie bierze, sam nie kolejkuje RETRY; zostaje w `stored`/`failed`; lista z etykietą „wstrzymany · błędy techniczne N · <błąd>” i aktywnym polem wyboru; POSTS go bierze; zapisany wynik zeruje | `db/migration/V10__posts_technical_failures.sql`, `PostRepository.java:38-58`, `PipelineRunService.java:151`, `AdminView.vue:349-372` (pole `disabled` tylko dla `dead`), `AssessmentSelection.java:55-65`, `PostTransitions.java:94-95` | ZGODNE (etykieta posta `failed` niżej, U-d) |
| D10 | `:1184` Flex: dowolna mieszanka błędów przenosi wywołanie na default (`flex_fallback = true`); `maxCallDuration` i 39 min watchdoga bez zmian | `TransportRetry.java:112-117`, `:69-73` (identyczne z `origin/main`, tam `:66-69`); testy `TransportRetryTest.java:192,200,179-189` | ZGODNE |
| D11a | `:1185` Przebieg `failed` z bezpiecznika daje powiadomienie w aplikacji i kod wyjścia crona 1 | jak P4a | ZGODNE |
| D11b | `:1185` … i push Railway „deployment crashed” | jak P4b | NIEWERYFIKOWALNE (konfiguracja konta Railway) |
| D12 | `:1185` „No new variable, no new counter of the run” | `OUTAGE_STREAK` to stała (`ConcurrentAssessmentDispatcher.java:92`), V10 zmienia tylko `posts`, `git diff origin/main...HEAD -- .railway` jest pusty | ZGODNE |
| D13 | `:1185` „no change to `/api/admin/llm/check`” | `api/llm/api/LlmCheckController.java:48-53`: nowy `catch (RuntimeException)`. Nieznany błąd daje teraz 200 `ok:false` zamiast 500 (test `test/llm/api/LlmCheckControllerIT.java:80`). Kształt odpowiedzi się nie zmienił, zachowanie tak | ROZBIEŻNE (R3) |
| D14 | `:1186` V10 dodaje `technical_failures` (domyślnie `0`, `CHECK >= 0`) i `last_technical_error`; stara instancja pisze dalej; `tables.sh` bez zmian | `V10__posts_technical_failures.sql:8-11`, `git diff origin/main...HEAD -- tools/db-backup` jest pusty. Zgodność ze starą instancją wynika z `ADD COLUMN … DEFAULT` | ZGODNE |
| D15 | `:1187` Historia bez zmian, bez migracji danych | V10 zawiera samo DDL, bez `UPDATE` | ZGODNE |
| D16 | `:1193` Liczniki nie pokazują przy awarii nieudanego posta ani błędu technicznego (`assessed failed 0`, `processing errors 0`) | `ConcurrentAssessmentDispatcher.java:325-326,184`; testy `PipelineAgentIT.java:628-632`, `LlmOutageIT.java:107-110` | ZGODNE |
| D17 | `:1194` `WARN The LLM provider is unavailable in run <id>: 3 calls in a row failed after their retries; no further post is taken: failed after …` | `ConcurrentAssessmentDispatcher.java:272-273`; test `ConcurrentAssessmentDispatcherTest.java:440-442` | ZGODNE |
| D18 | `:1194` Linia postępu `Run <id> assess: 3/251 (ok 0, failed 0, dead 0, errors 0, unavailable 3), in flight …, retries …, flex fallbacks …` | `ConcurrentAssessmentDispatcher.java:505-508`; test `ConcurrentAssessmentDispatcherTest.java:476-478` | ZGODNE |
| D19 | `:1195` Linia crona `range …: failed (…, assessed failed 0, processing errors 0, dead 0)`, `FAILED: a run did not succeed: …`, wyjście 1 | `trigger.sh:261,14,281-283` | ZGODNE |
| D20 | `:1197` Pojedyncza awaria: `WARN Assessing post <id> in run <id> found the LLM provider unavailable; the post was put back without an attempt, counting a technical failure: LlmUnavailableException: failed after …`; błąd przebiegu `1 post(s) with a technical error, no assessment stored: <post id> (LlmUnavailableException: …)` | `ConcurrentAssessmentDispatcher.java:358-359,277`, `RunFlow.java:138-141` | ZGODNE |
| D21 | `:1199` `ERROR Assessing post <id> in run <id> failed; the post is put back without an attempt, technical failure N`, ze stack trace'em | `ConcurrentAssessmentDispatcher.java:302-304` (wyjątek jako ostatni argument) | ZGODNE |
| D22 | `:1203-1205` Runbook awarii: błąd kończy się ostatnim komunikatem dostawcy; `llm/check` → `"ok":true`; RETRY crona przed zakresem albo zakres z `/admin`; bez ponowienia tej samej nocy; odrzucony klucz → „refused the configuration” | `TransportRetry.java:121`, `LlmCheckController.java:42`, `PipelineRunService.java:151`, `ConcurrentAssessmentDispatcher.java:255` | ZGODNE |
| D23 | `:1209-1212` Runbook wstrzymania: pełny błąd w tooltipie; prefiks `LlmUnavailableException: …` oznacza awarię, inny prefiks nasz kod; błędy się mieszają; serię przerywa tylko zapisany wynik; SQL `technical_failures >= 3 AND stage IN ('stored','failed')` | `AdminView.vue:372` (`:title`), `ConcurrentAssessmentDispatcher.java:277,287`, `PostTransitions.java:94-95,117-121` (`release` nie zeruje), `FailedPostController.java:92` (ta sama definicja `onHold`) | ZGODNE |
| D24 | `:1216` Założenia zapytania: komunikat „failed after N attempts: …” (taki sam przed S-09), naruszenie z `rule` `call`, klucze `{failureClass, rule, path, message}`; posty z „`choices` is not set” odpadają | `TransportRetry.java:121` (`origin/main`: `:115`, ten sam format), `Assessor.java:158`, `api/agents/assessor/validation/Violation.java:16`. Błąd nieponawialny nie ma prefiksu „failed after” (`TransportRetry.java:103-105`) | ZGODNE (samo zapytanie sprawdza krok 5.3) |
| D25 | `:1235` „Checked locally on 2026-10-20 against `V1`–`V10` with test data …” | Brak artefaktu, a w `plan.md` krok 5.3 nadal ma `[ ]`, z metodą dopisaną 2026-10-21 | NIEWERYFIKOWALNE (R5) |
| D26 | `:1240` `railway-config` nie planuje zmian (S-09 nie dodaje zmiennej ani serwisu) | `git diff origin/main...HEAD -- .railway` jest pusty | ZGODNE |
| D27 | `:1241` `migrate` loguje „now at version v10” | Komunikat pochodzi z Flyway, nie z naszego kodu. Precedens: `deploy-plan.md:822` („now at version v9”) | NIEWERYFIKOWALNE bez uruchomienia |
| D28 | `:1243` W linii postępu pierwszej nocy jest `unavailable 0` | `ConcurrentAssessmentDispatcher.java:506` (pole zawsze obecne) | ZGODNE |
| D29 | `:1243` Bezpiecznika nie da się wywołać na produkcji; pokrywa go `LlmOutageIT` | `test/pipeline/LlmOutageIT.java:98-122` | ZGODNE |

### Linie poprawione poza sekcją S-09

| # | Twierdzenie | Kod / test | Werdykt |
| --- | --- | --- | --- |
| U1 | `:162` Flex przechodzi na default po każdej mieszance 429/5xx/timeoutów/błędów połączenia | `TransportRetry.java:112-117`; test `TransportRetryTest.java:192` | ZGODNE |
| U2 | `:216` „Update (S-09)”: wywołanie niedostępne po ponowieniach (odpowiedź bez `choices`, 5xx, timeout, błąd połączenia, 429) nie zużywa próby; 3 z rzędu zatrzymują przebieg | `OpenRouterClient.java:241,273-274` (brak `choices` = `SERVER_ERROR`, ponawiany), `TransportRetry.java:118-121`, dispatcher jak P1/P3 | ZGODNE |
| U3 | `:223` Odmowa flex widoczna jako ponowienia albo `flex_fallback`; od S-09 awaria po ponowieniach nie zużywa próby nawet przy padniętym default | `TransportRetry.java:112-121`, `Assessor.java:131-133` | ZGODNE |
| U4 | `:468` Noc w czasie awarii: bezpiecznik; przy timeoutach wywołanie trwa do `maxCallDuration` (34 min przy `300s` i `flex`), poniżej 39 min watchdoga | `TransportRetry.java:69-73`, `TransportRetryTest.java:185-188` (34 min) | ZGODNE (uwaga U-e) |
| U5 | `:730` Błąd techniczny obejmuje pojedynczą awarię; trzy awarie z rzędu dają `failed` | `ConcurrentAssessmentDispatcher.java:360` (`processingError`), `PipelineRun.java:289-290` | ZGODNE |
| U6 | `:853` To samo, w krótszej formie | jak U5 | ZGODNE |
| U7 | `:1168` Od S-09 pojedyncza awaria dostawcy daje powiadomienie | jak U5 | ZGODNE |

## 3. `api/pipeline/CLAUDE.md`

| # | Twierdzenie | Kod / test | Werdykt |
| --- | --- | --- | --- |
| C1 | `:57` RETRY bierze każdą resztkę poza wstrzymanymi; POSTS bierze podane `stored`/`failed`, wstrzymane albo nie | `AssessmentSelection.java:54,62-63`, `PostRepository.java:38-45` | ZGODNE |
| C2 | `:59` Dyspozytor: najpierw check; `LlmConfigurationException` zatrzymuje krok; `LlmUnavailableException` nic nie zapisuje i zwalnia post, a `OUTAGE_STREAK` (3) z rzędu zatrzymuje krok; inny wyjątek → `releaseFailed` | `ConcurrentAssessmentDispatcher.java:141-147,252-279,290-306` | ZGODNE |
| C3 | `:61` `PostTransitions`: `record` zeruje; `release` mówi, czy zwolnił; `releaseFailed` w tym samym zapytaniu; `countTechnicalFailure` tylko dla `stored`/`failed` i działającego przebiegu; komunikat „cut to 500 characters, without U+0000” | `PostTransitions.java:90-157,183-186` | ZGODNE (nieścisłość U-b: 500 znaków + „…”) |
| C4 | `:66-70` Liczenie per post; pojedyncza awaria liczona dopiero na końcu kroku | `ConcurrentAssessmentDispatcher.java:315-329,345-363` | ZGODNE |
| C5 | `:77-81` Format linii postępu i znaczenie `unavailable` (część `done`) | `ConcurrentAssessmentDispatcher.java:497-508` | ZGODNE |
| C6 | `:86-91` Trzy wpisy logu kroku: WARN bezpiecznika, WARN pojedynczej awarii, ERROR naszego kodu | `ConcurrentAssessmentDispatcher.java:272-273,358-359,302-304` | ZGODNE |
| C7 | `:155-157` `needsAttention`: `failed` z bezpiecznika, `partial` z błędem technicznym (w tym pojedynczą awarią) | `PipelineRun.java:287-294` | ZGODNE |
| C8 | `:169-179` Trzy winy i ich skutki | `Assessor.java:131-136`, `ConcurrentAssessmentDispatcher.java:261-306`, `RunFlow.java:133-145` | ZGODNE |
| C9 | `:180-186` Bezpiecznik: stała 3; „z rzędu” w kolejności zakończenia, jeden licznik na krok; zeruje zapisany wynik z `attempts() >= 1`; nie zerują prefiltr ani błąd naszego kodu; wywołanie anulowane przez `cancel()` → `NotTaken` | `ConcurrentAssessmentDispatcher.java:92,151,244-247,262-266`; testy `:483,504,524` | ZGODNE |
| C10 | `:187-192` Odroczone liczenie: po `await`, tylko przy `stoppedBy == null`, tylko w działającym przebiegu i tylko dla posta zwolnionego przez to zadanie | `ConcurrentAssessmentDispatcher.java:183-190,345-363`, `PostTransitions.java:148-157`; test `ConcurrentAssessmentDispatcherTest.java:557` | ZGODNE |
| C11 | `:193-198` Wstrzymanie: `technical_failures` (V10), zerowanie w `record`, próg 3, etap zostaje, `findLeftoverIds`/`existsLeftover` go pomijają, POSTS go bierze, lista z `technicalFailures`/`lastTechnicalError`/`onHold` | `Post.java:46`, `PostRepository.java:38-58`, `PostTransitions.java:94-95`, `AssessmentSelection.java:55-65`, `FailedPostController.java:83-93` | ZGODNE |
| C12 | `:226-241` Testy jednostkowe: przypadki bezpiecznika, `AssessorTest`, `TransportRetryTest` (`maxCallDuration`) | `ConcurrentAssessmentDispatcherTest.java:224,420,448,483,504,524,557`, `AssessorTest.java:112`, `TransportRetryTest.java:179` | ZGODNE |
| C13 | `:242-246` `PostRepositoryIT` (predykaty RETRY, `releaseFailed` tylko dla właściciela, przycięty błąd bez NUL, zerowanie, `countTechnicalFailure`) i `PostsApiIT` | `PostRepositoryIT.java:106,124,144,157,171`, `PostsApiIT.java:396` | ZGODNE |
| C14 | `:268-273` `PipelineAgentIT` Order 14 (bezpiecznik, bez próby, bez błędu technicznego, powiadomienie) i Order 15 (`unavailable 1`, nic nie liczone) | `PipelineAgentIT.java:614-649,653-686` | ZGODNE |
| C15a | `:276-280` `LlmOutageIT`: prawdziwy transport (DeepSeek z `LlmClientFactory`), check, potem HTTP 500; `failed` po 1 + 3 × 3 żądaniach; bez próby, bez błędu technicznego, z powiadomieniem | `LlmOutageIT.java:84-90,98-122` | ZGODNE |
| C15b | `:280-281` „Re-stub the `@MockitoBean` with `doAnswer(…)`” (w punkcie `LlmOutageIT`) | `LlmOutageIT.java:72` ma `@TestBean`, a nie `@MockitoBean`. Zdanie dotyczy `PipelineAgentIT` (`@MockitoBean` `:126`, `doAnswer` `:802`), gdzie było na `origin/main` | ROZBIEŻNE (R4) |

## 4. `api/llm/CLAUDE.md`

| # | Twierdzenie | Kod / test | Werdykt |
| --- | --- | --- | --- |
| L1 | `:7-8` Mapa wymienia `LlmUnavailableException` | `api/llm/LlmUnavailableException.java:11` | ZGODNE |
| L2 | `:25-30` Trzy wyniki błędu: konfiguracja, niedostępność (ponowienia wyczerpane albo przerwanie między próbami), zwykły `LlmCallException` (odmowa modelu, blokada, brak tekstu, inny 4xx) | `api/llm/LlmClient.java:35-44`, `TransportRetry.java:100-105,118-121,139-142`, `TransportException.java:40-44` | ZGODNE |
| L3 | `:30-35` Inny `RuntimeException` nie jest klasyfikowany: `ofTimeoutOrConnection` rozpoznaje tylko timeout albo I/O, reszta wychodzi jako `unclassified` (`IllegalStateException`, oczyszczony komunikat, oryginał jako przyczyna, bez ponowień, nigdy nie zapisywany jako błąd dostawcy) | `TransportException.java:108-132`, `TransportRetry.java:75-78` (inne wyjątki przechodzą), klienci jak D8; testy `TransportRetryTest.java:117`, `*ClientTest` jak D8 | ZGODNE |
| L4 | `:36-37` Odpowiedź bez tekstu → `REJECTED` | `api/llm/transport/ChatResponses.java:35` | ZGODNE |
| L5 | `:42-45` Ponowienia tylko w `TransportRetry`: 3 na tier, `Retry-After` ≤ 60 s albo `min(2^(n-1), 30)` s; flex → default po każdym wyczerpanym tierze flex (dowolna mieszanka), do 3 dodatkowych prób; wyczerpane → `LlmUnavailableException` (`timeout`, gdy ostatnia próba to timeout) | `TransportRetry.java:28-32,93-133`; testy `TransportRetryTest.java:60,145,152,166,192,200,208` | ZGODNE |
| L6 | `:48-51` Konfiguracja (401/402/404) → `LlmConfigurationException`, `Assessor` rzuca dalej, przebieg staje | `TransportException.java:85-86`, `TransportRetry.java:100-102`, `Assessor.java:131-133`, `ConcurrentAssessmentDispatcher.java:252-259` | ZGODNE |
| L7 | `:81-84` OpenRouter: błąd w ciele 200 klasyfikowany po `code` (flex → default po wyczerpanym tierze); 2xx bez `choices` i bez błędu ponawiany jak 5xx | `OpenRouterClient.java:196-204,241,273-274`, `TransportRetry.java:112-117` | ZGODNE |
| L8 | `:108-112` Wymagane testy dostawcy: wyjątek SDK bez I/O → `IllegalStateException` po jednym wywołaniu, oryginał jako przyczyna, klucz `<hidden>`; z `IOException` → ponawiany błąd połączenia | `OpenRouterClientTest.java:484,504`, `DeepSeekClientTest.java:227,247`, `GeminiClientTest.java:235,255` | ZGODNE |

## Podsumowanie werdyktów

| Dokument | ZGODNE | ROZBIEŻNE | NIEWERYFIKOWALNE |
| --- | --- | --- | --- |
| `prd.md` (P) | 9 | 0 | 1 |
| `deploy-plan.md`, sekcja S-09 (D) | 25 | 1 | 4 |
| `deploy-plan.md`, poprawione linie (U) | 7 | 0 | 0 |
| `pipeline/CLAUDE.md` (C) | 15 | 1 | 0 |
| `llm/CLAUDE.md` (L) | 8 | 0 | 0 |
| **Razem (71)** | **64** | **2** | **5** |

## Rozbieżności

O każdej decyduje człowiek. Proponowane poprawki to jedno zdanie, nie przepisywanie dokumentów.

- **R1 (znana, potwierdzona).** Javadoc w `api/pipeline/step/AssessmentSelection.java:21-25` (klasa: „a retry run every leftover regardless of its date”) i `:46-51` (`fromTargets`: „every leftover (stored, or failed with attempts left)”) nie mówi, że RETRY (`findLeftoverIds`) pomija wstrzymane posty, ani że POSTS je bierze. Poprawka: dopisać „except posts on hold (`PostRepository.findLeftoverIds`)” przy RETRY i „(on hold or not)” przy POSTS.
- **R2 (znana, potwierdzona).** `szczur-radar-api/README.md:113-114` nie opisuje bezpiecznika (3 awarie z rzędu → `failed`) ani tego, że awaria dostawcy po ponowieniach nie zużywa próby. Lista zasad przebiegu nie mówi też o wstrzymaniu po 3 błędach technicznych; wspomina o nim tylko opis trasy w `:243`. Fałszywego zdania README nie zawiera: `:113` („Błąd techniczny nie zużywa próby”), `:118` i zaktualizowane `:62`, `:243` są prawdziwe. Poprawka: jedno zdanie po `:114`: „Awaria dostawcy po ponowieniach (429/5xx/timeout/połączenie) nie zużywa próby; trzy takie z rzędu kończą przebieg `failed` (S-09), pojedyncza jest błędem technicznym posta, a trzy błędy techniczne z rzędu wstrzymują post (RETRY go pomija, „Ponów zaznaczone” go bierze).”
- **R3.** `deploy-plan.md:1185` mówi „no change to `/api/admin/llm/check`”. Tymczasem `LlmCheckController.java:48-53` dodał `catch (RuntimeException)`, więc nieznany błąd daje teraz 200 `ok:false, refused:false` zamiast 500 (`LlmCheckControllerIT.java:80`). Kształt odpowiedzi się nie zmienił. Poprawka: „no change to the response shape of `/api/admin/llm/check` (an unknown error is now `ok:false` instead of a 500)”.
- **R4.** `pipeline/CLAUDE.md:280-281`: zdanie „Re-stub the `@MockitoBean` with `doAnswer(…).when(…)` …” trafiło do punktu `LlmOutageIT`, który używa `@TestBean` (`LlmOutageIT.java:72`). Dotyczy ono `PipelineAgentIT` (`:126`, `:802`), w którego punkcie było na `origin/main`. Poprawka: przenieść zdanie z powrotem na koniec punktu `PipelineAgentIT`.
- **R5 (NIEWERYFIKOWALNE, niespójność z planem).** `deploy-plan.md:1235` twierdzi „Checked locally on 2026-10-20 against `V1`–`V10` with test data …”, a krok 5.3 w `plan.md:611` jest `[ ]`, z metodą dopisaną 2026-10-21. Po repozytorium nie widać, że to sprawdzenie się odbyło. Poprawka: zostawić zdanie dopiero po wykonaniu 5.3 (z datą i wynikiem) albo je usunąć.

### Drobne nieścisłości (bez wpływu na zachowanie; do decyzji, można pominąć)

- **U-a.** Javadoc klasy `PostTransitions.java:26-28` i komentarz `V10__posts_technical_failures.sql:1-2` opisują błąd techniczny jako „an error of our code”. Pomijają pojedynczą awarię dostawcy, którą liczy `countTechnicalFailure`. `V10` nie jest jeszcze wdrożona, więc jej komentarz można poprawić przed merge'em. Poprawka: „an error of our code or a single provider outage”.
- **U-b.** `pipeline/CLAUDE.md:61` mówi „the message is cut to 500 characters”. Kod zapisuje 500 znaków + „…”, czyli 501 (`PostTransitions.java:185`; `PostRepositoryIT` sprawdza `hasSize(501)`). Poprawka: „cut to 500 characters plus ‘…’”.
- **U-c.** `deploy-plan.md:1180`: „with no call answered by the model between them”. Serię zeruje każdy zapisany wynik z `attempts() >= 1` (`ConcurrentAssessmentDispatcher.java:244-247`), także odmowa dostawcy (np. 4xx), która nie jest odpowiedzią modelu. `pipeline/CLAUDE.md:184-185` mówi to precyzyjnie. Poprawka: „answered by the provider”.
- **U-d.** `deploy-plan.md:1183,1209` cytuje etykietę „wstrzymany · błędy techniczne N · <last error>”. Dosłownie tak wygląda tylko wstrzymany post `stored`. Wstrzymany `failed` pokazuje „wstrzymany · próby N · <klasa> · błędy techniczne N · <błąd>” (`AdminView.vue:359-372`). Poprawka niekonieczna; ewentualnie „(a `failed` one also shows its attempts)”.
- **U-e (obserwacja, nie zdanie z dokumentu).** `deploy-plan.md:468` (34 min wobec 39 min watchdoga) jest prawdziwe dla pojedynczego wywołania. `Unavailable` nie przesuwa jednak `last_activity_at`, bo `count` go pomija (`ConcurrentAssessmentDispatcher.java:325-326`), a aktywność ruszają tylko liczone posty i kroki. Przy `PIPELINE_LLM_CONCURRENCY` ≥ 3 trzy wywołania w locie kończą się w ciągu 34 min, więc bezpiecznik zdąży przed watchdogiem. Przy 1–2 wątkach i samych timeoutach (flex) watchdog zatrzyma przebieg pierwszy („no progress for 39m”), też jako `failed` i bez prób. Produkcja ma domyślne 4, a żaden test tego nie przypina. Do decyzji, czy wspomnieć.
- **U-f (sprzed S-09).** `README.md:69` podaje ścieżkę `llm/TransportRetry` (właściwa to `llm/transport/TransportRetry`) i „do 3 prób” bez +3 prób po przejściu z flex. Zdanie jest starsze niż S-09; wymieniam je tylko dlatego, że chodzi o te same ponowienia.

## Decyzje operatora (2026-10-21)

- Tabela zaakceptowana.
- R1–R5 i U-a poprawione w commicie zamykającym krok 5.4. U-a: tylko Javadoc `PostTransitions`. Komentarz w `V10` zostaje, bo migracja jest już w bazie PR env, a edycja wdrożonej migracji zmienia jej sumę kontrolną Flyway.
- U-b, U-c, U-d i U-f zostają bez zmian.
- U-e do odnotowania w przeglądzie implementacji (`/10x-impl-review`).
