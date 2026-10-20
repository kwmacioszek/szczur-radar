# Awaria dostawcy LLM ani błąd naszego kodu nie zabija postów (S-09) — plan implementacji

## Overview

Model „próba = zapisany wynik `failed`” przestaje mieszać trzy różne winy:

- **Wina posta** (model odmówił albo dał złą odpowiedź) dalej zużywa próbę. Po trzech post jest `dead`.
- **Wina dostawcy** (5xx, timeout, brak połączenia, 429 po wyczerpaniu ponowień) nie zużywa próby. Trzy takie awarie z rzędu zatrzymują przebieg (bezpiecznik): status `failed`, reszta postów wraca bez prób. Pojedyncza awaria, po której przebieg idzie dalej, jest błędem technicznym posta.
- **Wina naszego kodu** (wyjątek w mapperze, w kliencie SDK, w zapisie) nie zużywa próby, ale ma limit. Po trzech błędach technicznych z rzędu post jest **wstrzymany**: wypada z automatycznego RETRY i trafia na listę „Nieudane posty” z ostatnim błędem. Admin może go ponowić.

Dodatkowo flex przechodzi na tier standardowy przy każdej mieszance błędów ponawialnych, a nie tylko przy 3×429. `RuntimeException` z SDK w kliencie LLM przestaje być klasyfikowany jako odmowa modelu (`REJECTED`).

Źródło: roadmapa S-09 (`context/foundation/roadmap.md:162-173`), audyt A1, B2, B3, B9 i „Architecture Insights” (`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:59,73,74,80,238`), issue #68.

## Current State Analysis

Konwencja ścieżek: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`, `web/…` = `szczur-radar-web/src/…`, `db/…` = `szczur-radar-api/src/main/resources/db/migration/…`.

- **Próba = zapisany `failed`.**
  - `PostTransitions.Outcome.after(boolean ok, attemptsBefore)` zna tylko ok/nie ok. Trzecia porażka daje `DEAD` (`api/pipeline/state/PostTransitions.java:137-147`).
  - Limit 3 jest w `Post.MAX_ASSESSMENT_ATTEMPTS` (`api/posts/Post.java:40`), w CHECK `ck_posts_stage_attempts` (`db/V3__create_pipeline_and_posts.sql:116-121`) i w komunikacie `post_dead` w SPA (`web/api/pipeline.ts:108-109`).
- **Awaria dostawcy jest nie do odróżnienia od odmowy.**
  - `TransportRetry.run` rzuca `LlmCallException(PROVIDER_ERROR|TIMEOUT)` zarówno po wyczerpaniu ponowień błędami ponawialnymi (`api/llm/transport/TransportRetry.java:113-116`), jak i od razu przy `REJECTED` (`:96-98`).
  - Przerwanie między próbami też daje `PROVIDER_ERROR` (`:134-137`).
  - `LlmCallException` nie ma pola z rodzajem błędu transportu (`api/llm/LlmCallException.java`).
  - `Assessor.call` robi z każdego `LlmCallException` poza konfiguracyjnym zapisany `failed` (`api/agents/assessor/Assessor.java:121-135,145-156`).
- **Wzorzec zatrzymania istnieje.** `LlmConfigurationException` jest rzucany dalej przez `Assessor`. W dyspozytorze ustawia `runStopped` i `stoppedBy` (`api/pipeline/assess/ConcurrentAssessmentDispatcher.java:215-223`), a `RunFlow.AssessedBatch.status()` daje `FAILED`, gdy `stoppedBy != null` (`api/pipeline/flow/RunFlow.java:150-155`). Kolejne zadania zwracają `NotTaken`, a ich posty wracają bez próby (`finally` → `transitions.release`, `:233-237`).
- **Flex przechodzi na default tylko przy 3×429** (`TransportRetry.java:108-112`). Przypina to test `flexFailingOtherwiseDoesNotFallBack` (`test/llm/transport/TransportRetryTest.java:139-148`). `maxCallDuration` już zakłada dwa tiery dla flex (`:66-70`), więc próg watchdoga (`PIPELINE_STALL_TIMEOUT`) się nie zmienia.
- **B9 ma jedno źródło.**
  - `TransportException.ofThrowable` zamienia każdy wyjątek bez timeoutu i bez `IOException` w łańcuchu przyczyn na `REJECTED` (`api/llm/transport/TransportException.java:102-114`).
  - Wołają go tylko trzej klienci: `OpenRouterClient.java:195-198`, `DeepSeekClient.java:138-145`, `GeminiClient.java:106-108,118-126`.
  - Javadoc `LlmClient.java:32-41` mówi, że nasz błąd ma wyjść jako pierwotny `RuntimeException`. Dyspozytor już traktuje taki wyjątek jako błąd techniczny bez próby (`ConcurrentAssessmentDispatcher.java:224-232`; test ze skryptowanym `IllegalStateException("a bug in the client")` w `ConcurrentAssessmentDispatcherTest`).
- **A1: błąd techniczny nie ma licznika per post.**
  - `Outcome.Error` zwalnia post bez zmiany stanu. RETRY bierze go co noc (`api/posts/PostRepository.java:36-50`: `stage = stored` albo `failed` z próbami < 3).
  - Takiego posta nie ma na liście nieudanych (`api/posts/api/FailedPostController.java:47-56`: `stage IN (failed, dead)`), bo nie ma wiersza w `assessments`.
  - Tę samą poprawkę („Fix B”) odłożono w S-02 (`context/archive/2026-10-06-first-assessed-wykop-feed/reviews/impl-review-phase-2.md:26-45`).
- **Sygnały przebiegu.**
  - `needsAttention`: `failed`/`interrupted` zawsze; `partial` przy `dead`, `processing_errors` albo `skipped` (`api/pipeline/run/PipelineRun.java:282-294`).
  - Przebieg `failed` daje powiadomienie, kod wyjścia crona 1 i push Railway.
  - Komunikat błędu technicznego już dziś trafia do `pipeline_runs.error` (`RunFlow.java:132-144`).
- **Inni wołający `assessor.assess`:** tylko kod pomiarowy w `src/test` (`test/measurement/GoldRecorder.java:61`, `test/measurement/LiveSmokeTest.java:84`). `LlmCallException` łapią poza tym check przed przebiegiem (`ConcurrentAssessmentDispatcher.java:125-131`) i `LlmCheckController.java:41`.
- **Najwyższa migracja: V9.** Tabela `posts` nie ma kolumny na błędy techniczne ani na ostatni błąd.

## Desired End State

- **Awaria dostawcy w środku przebiegu** (np. 20 min 5xx z OpenRouter):
  - po 3 kolejnych wywołaniach zakończonych awarią (bez udanego wywołania między nimi) przebieg przestaje brać posty;
  - kończy się `failed` z `error` w rodzaju „the LLM provider is unavailable: 3 calls in a row failed after their retries; the last: …”;
  - żaden post nie zyskuje próby ani błędu technicznego, a wszystkie niezakończone wracają do `stored`/`failed`;
  - admin dostaje powiadomienie, a cron kończy się kodem 1.
- **Pojedyncza awaria na jednym poście** (przebieg idzie dalej): post wraca bez próby, z `technical_failures + 1` i ostatnim błędem. Przebieg liczy go w `processing_errors` i kończy się `partial`, co daje powiadomienie jak każdy błąd techniczny.
- **Odmowa modelu albo zła odpowiedź** (`REJECTED`, `schema_violation`, …): bez zmian, zapisany `failed` z próbą.
- **Flex:** trzy wyczerpane próby na flex z dowolną mieszanką 429/5xx/timeoutu/błędu połączenia przenoszą to samo wywołanie na default (do 3 prób), z `flex_fallback = true`.
- **Wyjątek SDK albo nasz błąd w kliencie LLM** wychodzi jako pierwotny `RuntimeException`, czyli jako błąd techniczny posta, nie jako `failed provider_error`.
- **Post z trzecim błędem technicznym z rzędu** jest wstrzymany:
  - RETRY go nie bierze, a `existsLeftover` go nie widzi, więc sam wstrzymany post nie kolejkuje nocnego RETRY;
  - `GET /api/admin/posts/failed` zwraca go z `technicalFailures`, `lastTechnicalError` i `onHold = true`;
  - `/admin` pokazuje go na liście „Nieudane posty” jako „wstrzymany · błędy techniczne N · <błąd>”, a pole wyboru do „Ponów zaznaczone” jest aktywne.
- **Każdy zapisany wynik oceny** (ok albo `failed`) zeruje licznik błędów technicznych i ostatni błąd posta.
- **Dokumentacja** opisuje nową semantykę: PRD (doprecyzowanie S-09), `pipeline/CLAUDE.md`, `llm/CLAUDE.md`, `agents/assessor/CLAUDE.md` i sekcja S-09 w `context/deployment/deploy-plan.md`.

### Key Discoveries:

- `TransportRetry.run` dochodzi do końca tieru tylko wtedy, gdy każda próba była ponawialna, bo nieponawialny błąd rzuca od razu (`TransportRetry.java:93-98`). Warunek fallbacku sprowadza się więc do `tier == FLEX`.
- Dyspozytor ma gotowe `runStopped`/`stoppedBy` z porównaniem-i-ustawieniem (`ConcurrentAssessmentDispatcher.java:132-133,192-193,215-223`). Bezpiecznik dokłada tylko licznik awarii z rzędu i drugi powód zatrzymania.
- Wynik prefiltra nie robi wywołania (`Provenance.attempts() == 0`, `Assessor.java:70-74`), więc nie może zerować licznika awarii z rzędu.
- `release` w `PostTransitions` to jeden warunkowy `UPDATE` ze strażnikiem właściciela (`PostTransitions.java:33-35,99-103`). Wariant z błędem technicznym dokłada do niego licznik w tym samym zapytaniu.
- Komunikat błędu technicznego już jest zapisywany w `pipeline_runs.error`. Kolumna na poście nie tworzy nowej klasy ujawnienia, pod warunkiem że wyjątki z `llm/` dalej mają komunikat oczyszczony przez `ProviderMessages.clean` (Faza 1 #3). Klucze w przyczynach wyjątków w logach to osobne B7/S-18.

## What We're NOT Doing

- **Bez migracji danych historycznych.** Postom, którym awarie zużyły próby przed wdrożeniem (`failed` z `provider_error`/`timeout` „failed after N attempts”), nie przeliczamy prób, a `dead` z awarii zostają `dead`. Runbook S-09 ma zapytanie tylko do odczytu, które je liczy. Posty `failed` admin ponawia przyciskiem.
- **Bez wskrzeszania `dead`** (poza zakresem od S-02).
- **Bez bezpiecznika na odmowy (`REJECTED`).** Seria odmów dalej zużywa próby, bo to wina posta albo żądania, a nie dostępności.
- **Bez nowego licznika przebiegu.** Pojedyncza awaria liczy się w istniejącym `processing_errors`. Nowej kolumny w `pipeline_runs` ani nowego licznika w UI przebiegu nie ma.
- **Bez zmiany progów i ustawień przez zmienne środowiskowe.** Próg bezpiecznika (3) i limit błędów technicznych (3) to stałe w kodzie.
- **Bez zmiany B8** (trzy płatne wywołania przy deterministycznym naruszeniu kontraktu) **i B10** (anulowanie a gniazdo). Oba są Parked w roadmapie.
- **Bez ujednolicenia „brak `choices`”** (DeepSeek `REJECTED`, OpenRouter `SERVER_ERROR`). Produkcja używa OpenRouter.
- **Bez ponowienia przebiegu tej samej nocy po zadziałaniu bezpiecznika.** Posty podejmie RETRY następnej nocy albo przebieg admina.
- **Bez zmiany kształtu odpowiedzi `LlmCheckController`** (`GET /api/admin/llm/check`). Awaria nadal pokazuje się tam jako nieudany check z komunikatem; dochodzi tylko obsługa nieznanego wyjątku (Faza 1 #4).

## Implementation Approach

Wzorzec `LlmConfigurationException` dostaje rodzeństwo. Warstwa `llm/` mówi „dostawca niedostępny” osobnym wyjątkiem, `Assessor` go przepuszcza, a dyspozytor decyduje: liczy awarie z rzędu, zatrzymuje przebieg i dopiero na końcu kroku dolicza pojedyncze awarie jako błędy techniczne. Licznik błędów technicznych posta to jedna kolumna z warunkowymi zapisami w `PostTransitions`, w tym samym stylu co próby: jeden `UPDATE` na zmianę, bez blokad.

Kolejność faz: najpierw klasyfikacja w `llm/` (faza 1). Sama zmienia tylko B3 i B9, a B9 trafia w istniejącą ścieżkę błędu technicznego. Potem licznik posta (faza 2), który daje B9 i A1 limit. Dalej bezpiecznik (faza 3), który przełącza awarię z „zapisany `failed`” na „wstrzymanie albo błąd techniczny”. Na końcu widoczność w API/UI (faza 4) i dokumentacja (faza 5). Po każdej fazie gałąź jest zielona i spójna.

## Critical Implementation Details

### State sequencing

Pojedynczą awarię dolicza się postowi dopiero po zebraniu wyników całego kroku (`dispatch`), i tylko gdy `stoppedBy == null`. Bezpiecznik może zadziałać po tym, jak post z awarią został już zwolniony, więc licznik nie może rosnąć w `finally` zadania. Post wraca od razu przez zwykłe `release`. Licznik i `processing_errors` dopisuje osobny zapis po pętli `await`. Błąd naszego kodu (`Outcome.Error`) liczy się od razu, w tym samym `UPDATE` co zwolnienie (`releaseFailed`, ze strażnikiem właściciela).

Odroczony zapis **musi mieć strażnik „przebieg wciąż `running`”**, a nie tylko `stage IN (stored, failed)` (przegląd planu, F1). `dispatch` dochodzi za pętlę `await` ze `stoppedBy == null` także wtedy, gdy przebieg już nie biegnie:
- **deploy:** `onClose` przerywa przebieg i zwalnia posty (`PipelineRunService.java:330-342`), a wątek `dispatch` (pula Embabel, zamykana przez `shutdown()`, bez przerwania) dalej działa. `destroy()` dyspozytora przerywa wywołania w locie, a flaga `cancelled` jest wtedy `false` (ustawia ją tylko `cancel()` watchdoga, `ConcurrentAssessmentDispatcher.java:185`);
- **nakładka deployu:** nowa instancja przerywa stary przebieg (`PipelineStartupRecovery.java:82-92`);
- **watchdog:** futures zakończone jako `Unavailable` przed `cancel()` zachowują ten wynik.

Bez strażnika posty przerwane deployem dostałyby błąd techniczny. Strażnik jest ten sam co w `claim` (`EXISTS … status = :running`, `PostTransitions.java:59`). Gdy przebieg biegnie, nikt inny nie mógł wziąć posta (blokada doradcza i `existsActive`, `PipelineRunService.java:177-179`). Dodatkowo `Unavailable` liczy się tylko dla posta, którego to zadanie faktycznie zwolniło (`release` zwraca liczbę wierszy, 1 = zwolnił).

### Timing & lifecycle

Wyjątek „niedostępny” z zadania anulowanego przez `cancel()` (watchdog, zamknięcie) ma dać `NotTaken`: flaga `cancelled` jest sprawdzana przed jakimkolwiek liczeniem, tak jak dziś w gałęzi `RuntimeException` (`ConcurrentAssessmentDispatcher.java:224-229`). Przerwanie między próbami (`TransportRetry.java:134-137`) też rzuca „niedostępny”, nigdy zwykły `LlmCallException`. Dzięki temu nie może skończyć się zapisanym `failed`.

## Faza 1: Klasyfikacja błędów w `llm/`

### Overview

Warstwa `llm/` odróżnia „dostawca niedostępny” od „dostawca odmówił”. Flex przechodzi na default przy każdej mieszance błędów ponawialnych. Nieznany wyjątek w kliencie wychodzi bez zmian (B3, B9). Przebieg zachowuje się tak jak dziś, z wyjątkiem B9: taki wyjątek staje się błędem technicznym zamiast `failed provider_error`.

### Changes Required:

#### 1. Wyjątek „dostawca niedostępny”

**File**: `api/llm/LlmUnavailableException.java` (nowy), `api/llm/LlmCallException.java`, `api/llm/LlmClient.java`

**Intent**: osobny typ dla wywołania, które wyczerpało ponowienia na błędach ponawialnych albo zostało przerwane między próbami, żeby wyższe warstwy mogły nie liczyć go postowi.

**Contract**: `public class LlmUnavailableException extends LlmCallException` z tym samym konstruktorem (`CallFailure` zostaje `TIMEOUT`, gdy ostatnia próba to timeout, inaczej `PROVIDER_ERROR`). Javadoc `LlmClient.complete` wymienia trzy wyniki błędu: `LlmConfigurationException` (konfiguracja), `LlmUnavailableException` (dostawca niedostępny po ponowieniach), zwykły `LlmCallException` (odmowa albo błąd nieponawialny). Każdy inny `RuntimeException` to nasz błąd.

#### 2. `TransportRetry`: niedostępność i fallback flex

**File**: `api/llm/transport/TransportRetry.java`

**Intent**: po wyczerpaniu prób (`:113-116`) i przy przerwaniu między próbami (`:134-137`) rzucić `LlmUnavailableException`. Na flex przejść na default po wyczerpaniu tieru bez względu na mieszankę błędów (B3).

**Contract**: warunek `:108` to `tier == ServiceTier.FLEX` (licznik `rateLimited` znika). Javadoc klasy i `run` opisuje nowy warunek i nowe wyjątki. `maxCallDuration` bez zmian. Nieponawialny błąd (`:96-98`) zostaje zwykłym `LlmCallException(PROVIDER_ERROR)`.

#### 3. Nieznany wyjątek w kliencie wychodzi bez zmian (B9)

**File**: `api/llm/transport/TransportException.java`, `api/llm/openrouter/OpenRouterClient.java`, `api/llm/deepseek/DeepSeekClient.java`, `api/llm/gemini/GeminiClient.java`

**Intent**: `ofThrowable` przestaje zgadywać `REJECTED`. Wyjątek bez timeoutu i bez `IOException` w łańcuchu przyczyn klient rzuca dalej jako `RuntimeException` spoza `LlmCallException` (błąd techniczny), z oczyszczonym komunikatem. Dziś `ofThrowable` przepuszcza komunikat przez `ProviderMessages.clean` (klucz → `<hidden>`, 500 znaków). Rzucenie pierwotnego wyjątku ominęłoby to czyszczenie, a komunikat trafia do `pipeline_runs.error` i do nowego `posts.last_technical_error` (przegląd planu, F2).

**Contract**: `ofThrowable` zastępuje metoda zwracająca `Optional<TransportException>` (tylko `TIMEOUT` i `CONNECTION`). Klienci w `catch (RuntimeException ex)` rzucają wynik albo `new IllegalStateException(messages.clean(String.valueOf(ex)), ex)`: przyczyna zostaje dla logów (B7 osobno, S-18). W OpenRouter kolejność zostaje: najpierw `failedAnswer`, potem klasyfikacja I/O, potem oczyszczony `IllegalStateException`. W Gemini najpierw `ApiException` w łańcuchu, potem I/O, potem oczyszczony `IllegalStateException`. Javadoc `LlmClient.complete` mówi: nasz błąd albo nieznany błąd SDK wychodzi jako `RuntimeException` spoza `LlmCallException`. Javadoc `Kind.REJECTED` przestaje mówić „any other error” i wymienia: inny status 4xx, odmowę modelu, zablokowaną odpowiedź, brak tekstu. Taki wyjątek przerywa po tej fazie także pomiar (`test/measurement/GoldRecorder`, `LiveSmokeTest`). To celowe: to błąd do naprawy, nie wynik oceny.

#### 4. `LlmCheckController` przy nieznanym wyjątku

**File**: `api/llm/api/LlmCheckController.java`, `test/llm/api/LlmCheckControllerIT.java`

**Intent**: `GET /api/admin/llm/check` łapie dziś tylko `LlmCallException` (`:36-45`), a `ApiExceptionHandler` nie ma ogólnego handlera. Po zmianie z punktu 3 nieznany wyjątek z `check()` dałby więc domyślne 500 zamiast wyniku (przegląd planu, F5).

**Contract**: dodatkowy `catch (RuntimeException ex)` daje `ok = false`, `refused = false` i komunikat. Kształt odpowiedzi bez zmian. `LlmCheckControllerIT` (obok przypadku z `:70`) dostaje przypadek „`check()` rzuca `IllegalStateException`”.

#### 5. Testy warstwy `llm/`

**File**: `test/llm/transport/TransportRetryTest.java`, `test/llm/openrouter/OpenRouterClientTest.java`, `test/llm/deepseek/DeepSeekClientTest.java`, `test/llm/gemini/GeminiClientTest.java` (`TransportExceptionTest` nie istnieje; przypadki klasyfikacji idą do `TransportRetryTest`, który już przypina mapowanie statusów, `:83-89`)

**Intent**: przypiąć nową semantykę.

**Contract**:

- `flexFailingOtherwiseDoesNotFallBack` przepisany na `flexFailingWithMixedErrorsFallsBack` (429, 5xx, 429 na flex → kolejne próby na default, `flexFallback = true`).
- Nowe przypadki:
  - same timeouty na flex przechodzą na default;
  - wyczerpane ponowienia rzucają `LlmUnavailableException` (z `TIMEOUT`, gdy ostatni był timeout);
  - `REJECTED` rzuca zwykły `LlmCallException`, nie `LlmUnavailableException`;
  - przerwanie między próbami rzuca `LlmUnavailableException`.
- Per klient: `RuntimeException` z SDK bez I/O w łańcuchu wychodzi z `complete` jako `IllegalStateException` (nie `LlmCallException`). Oryginał jest jego przyczyną, a komunikat ma klucz zamieniony na `<hidden>` (komunikat wyjątku SDK zawiera klucz z testu). Wywołanie idzie raz, bez ponowień. Wyjątek z `IOException` w łańcuchu dalej daje `CONNECTION` i ponowienia.

#### 6. Dokumentacja pakietu

**File**: `api/llm/CLAUDE.md`

**Intent**: opis błędów (`:24-29`) i ponowień (`:34-38`) mówi o trzech wynikach błędu, o fallbacku flex po każdym wyczerpanym tierze i o tym, że nieznany wyjątek nie jest klasyfikowany.

**Contract**: sekcje o błędach i ponowieniach. Lista wymaganych testów dostawcy (`:96-103`) dostaje przypadek „wyjątek SDK bez I/O wychodzi bez zmian”.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe `llm/` przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest='TransportRetryTest,OpenRouterClientTest,DeepSeekClientTest,GeminiClientTest'` (`LlmCheckControllerIT` w pełnym zestawie)
- Test `flexFailingWithMixedErrorsFallsBack` istnieje, a `flexFailingOtherwiseDoesNotFallBack` nie
- Pełny zestaw testów przechodzi: `cd szczur-radar-api && ./mvnw -B verify`

#### Manual Verification:

- Przegląd: w `src/main` nie ma już wywołania, które zamienia dowolny `Throwable` na `Kind.REJECTED` (`grep -rn "Kind.REJECTED" szczur-radar-api/src/main` pokazuje tylko statusy, odmowy i brak tekstu)

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed następną fazą.

---

## Faza 2: Licznik błędów technicznych posta (A1)

### Overview

Post dostaje licznik błędów technicznych z rzędu i ostatni błąd. Błąd naszego kodu podczas oceny dolicza mu jeden w tym samym zapisie, który go zwalnia. Przy trzecim post jest wstrzymany: RETRY go nie bierze. Każdy zapisany wynik zeruje licznik.

### Changes Required:

#### 1. Migracja V10

**File**: `db/V10__posts_technical_failures.sql` (nowy)

**Intent**: miejsce na licznik i ostatni błąd techniczny posta.

**Contract**: `posts.technical_failures INTEGER NOT NULL DEFAULT 0` z nazwanym `CHECK (technical_failures >= 0)` oraz `posts.last_technical_error TEXT NULL`. Bez indeksu (tabela rzędu tysięcy wierszy, predykaty i tak filtrują po `stage`). Nowej tabeli nie ma, więc `tools/db-backup/tables.sh` się nie zmienia.

#### 2. Encja i stała

**File**: `api/posts/Post.java`, `api/posts/PostStage.java` (Javadoc)

**Intent**: pola encji odpowiadają kolumnom (`JpaSchemaIT`). Limit to nazwana stała obok limitu prób.

**Contract**: `int technicalFailures`, `@Nullable String lastTechnicalError` z getterami. `public static final int MAX_TECHNICAL_FAILURES = 3`. Javadoc `PostStage` opisuje, że wstrzymany post zostaje w `stored`/`failed` (wstrzymanie to licznik, nie stan).

#### 3. Zapisy w `PostTransitions`

**File**: `api/pipeline/state/PostTransitions.java`

**Intent**: zwolnienie z błędem technicznym dolicza licznik w tym samym warunkowym `UPDATE`. Doliczenie bez właściciela dla awarii (używane w fazie 3). Zerowanie przy zapisie wyniku.

**Contract**:

- `releaseFailed(UUID postId, UUID runId, String error)` = `RELEASE` + `technical_failures = technical_failures + 1, last_technical_error = :error`, ze strażnikiem `assessing_run_id = :run AND id = :id`.
- `countTechnicalFailure(UUID postId, UUID runId, String error)` = ten sam przyrost tylko dla `stage IN (stored, failed)` i tylko gdy przebieg `runId` jest wciąż `running` (`AND EXISTS (SELECT 1 FROM pipeline_runs WHERE id = :run AND status = :running)`, jak w `claim`; patrz „State sequencing”). Zwraca, czy zmienił wiersz.
- `release` zwraca `boolean`, czy zwolnił post (1 wiersz), żeby dyspozytor wiedział, że to zadanie oddało post.
- `record` ustawia dodatkowo `technical_failures = 0, last_technical_error = NULL`.
- Błąd jest przycinany do stałej długości (np. 500 znaków, jak `ProviderMessages`), żeby komunikat z SDK nie rozdmuchał wiersza.

#### 4. Selekcja RETRY bez wstrzymanych

**File**: `api/posts/PostRepository.java`

**Intent**: `findLeftoverIds` i `existsLeftover` pomijają posty z `technical_failures >= MAX_TECHNICAL_FAILURES`. Sam wstrzymany post nie kolejkuje więc RETRY przed nocnym RANGE (`PipelineRunService.java:151`). POSTS (`AssessmentSelection.java:62-63`) dalej przyjmuje wstrzymany post, bo jest `stored`/`failed`.

**Contract**: oba zapytania (`:39-41`, `:48-49`) dostają warunek `and p.technicalFailures < :maxTechnicalFailures`. Javadoc `findLeftoverIds` opisuje wyjątek.

#### 5. Dyspozytor: błąd naszego kodu liczy się postowi

**File**: `api/pipeline/assess/ConcurrentAssessmentDispatcher.java`

**Intent**: `Outcome.Error` z `assess` zwalnia post przez `releaseFailed` z tym samym komunikatem, który trafia do `processing_errors` i `pipeline_runs.error`. Błąd z `await` (zadanie, które nie wzięło posta) nadal nie zmienia posta.

**Contract**: `finally` w `assess` (`:233-237`) rozróżnia zwykłe zwolnienie od zwolnienia z błędem. Komunikat logu przy błędzie (`:230`) mówi „put back without an attempt, technical failure N”. Licznik `processingError` bez zmian.

#### 6. Testy

**File**: `test/pipeline/state/RunOutcomeTest.java`, `test/pipeline/assess/ConcurrentAssessmentDispatcherTest.java`, `test/posts/PostRepositoryIT.java` (predykaty RETRY obok przypadku z `:94`; tu też zapisy `PostTransitions` na bazie, jeśli nie ma dla nich osobnego IT), `test/pipeline/step/AssessmentSelectionTest.java` (`:56`, RETRY bez wstrzymanych), `test/JpaSchemaIT.java` (bez zmian, ma przejść)

**Intent**: przypiąć licznik, wstrzymanie i zerowanie.

**Contract**:

- IT na bazie:
  - `releaseFailed` dolicza tylko właścicielowi;
  - `record` zeruje licznik i błąd;
  - post z 3 błędami nie jest w `findLeftoverIds` ani w `existsLeftover`, a post z 2 jest;
  - `countTechnicalFailure` nie dotyka posta `assessing`/`assessed`/`dead` ani posta, którego przebieg nie jest już `running` (np. `interrupted`).
- Test dyspozytora: wyjątek z `inputs.of` daje `Outcome.Error`, `releaseFailed` z komunikatem i zero prób; przebieg kończy się `partial` jak dziś.

### Success Criteria:

#### Automated Verification:

- Migracja i walidacja encji przechodzą: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=JpaSchemaIT`
- Testy dyspozytora i przejść przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest='ConcurrentAssessmentDispatcherTest,RunOutcomeTest'`
- Pełny zestaw testów przechodzi: `cd szczur-radar-api && ./mvnw -B verify`
- Test backupu przechodzi z nową migracją: `tools/db-backup/test.sh`

#### Manual Verification:

- Lokalnie (`./mvnw spring-boot:run` + Postgres): post z ręcznie ustawionym `technical_failures = 3` nie trafia do przebiegu RETRY, a przebieg POSTS z jego id go bierze

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed następną fazą.

---

## Faza 3: Awaria dostawcy i bezpiecznik (B2)

### Overview

`Assessor` przepuszcza `LlmUnavailableException`. Dyspozytor liczy awarie z rzędu, po trzeciej zatrzymuje przebieg (`failed`) i dopiero na końcu kroku dolicza pozostałe awarie jako błędy techniczne, o ile przebiegu nic nie zatrzymało. Awaria dostawcy przestaje zużywać próby postów.

### Changes Required:

#### 1. `Assessor` przepuszcza niedostępność

**File**: `api/agents/assessor/Assessor.java`, `api/agents/assessor/CLAUDE.md`

**Intent**: tak jak `LlmConfigurationException`, `LlmUnavailableException` mówi coś o dostawcy, a nie o wpisie, więc nie staje się wynikiem `failed`.

**Contract**: `call` (`:126-131`) rzuca dalej `LlmUnavailableException`. Javadoc `assess` (`:110-113`) wymienia oba wyjątki. `failed(...)` zostaje jedynym miejscem budowania `failed` dla odmów. `agents/assessor/CLAUDE.md:11` mówi, że wynikiem `failed` jest odmowa albo błąd nieponawialny, a niedostępność i odrzucona konfiguracja to wyjątki.

#### 2. Bezpiecznik i odroczone liczenie w dyspozytorze

**File**: `api/pipeline/assess/ConcurrentAssessmentDispatcher.java`

**Intent**: awaria nie jest zapisywana jako próba. Trzy z rzędu zatrzymują przebieg. Pojedyncze są liczone dopiero na końcu kroku.

**Contract**:

- `Outcome` dostaje wariant `Unavailable(String message)`.
- `catch LlmUnavailableException` w `assess` (przed `catch RuntimeException`): przy `cancelled` → `NotTaken`. Inaczej licznik awarii z rzędu (`AtomicInteger` na krok) +1, a przy `>= OUTAGE_STREAK` (stała 3) `runStopped = true` i `stoppedBy.compareAndSet(null, "the LLM provider is unavailable: 3 calls in a row failed after their retries; the last: …")` z jednym WARN. Zwraca `Unavailable`. Post wraca przez zwykłe `release`.
- `Stored` z wywołaniem modelu (`provenance.attempts() >= 1`, czyli nie prefiltr) zeruje licznik awarii z rzędu. `Outcome.Error` go nie zmienia.
- `count` ignoruje `Unavailable`.
- `Unavailable` niesie informację, czy zadanie samo zwolniło post (wynik `release` w `finally`). Bez tego liczy się jak `NotTaken`.
- Po pętli `await`: gdy `stoppedBy.get() == null`, każdy `Unavailable` dostaje `transitions.countTechnicalFailure(postId, runId, message)` (dopiero teraz, ze strażnikiem przebiegu; patrz „State sequencing”). `recorder.count(processingError())` i wpis w liście `ProcessingError` dochodzą tylko wtedy, gdy zapis zmienił wiersz. Przebieg kończy się `partial`, a błąd jest w `errorSummary`. Gdy `stoppedBy != null` albo przebieg nie jest już `running`, `Unavailable` liczy się jak `NotTaken`.
- `Progress` pokazuje awarie w linii logu (np. jako `unavailable N`), żeby log kroku zgadzał się z licznikami.

#### 3. Kod pomiarowy i `AssessorAgent`

**File**: `test/measurement/GoldRecorder.java`, `test/measurement/LiveSmokeTest.java`, `api/agents/assessor/AssessorAgent.java`, `test/agents/AssessorAgentIT.java`

**Intent**: pomiar nie ma się wywracać na pierwszej awarii dostawcy. Awaria jest raportowana tak jak dotąd, czyli jako nieudane wywołanie danego wpisu. Akcja Embabel `AssessorAgent` (domyślna polityka ponowień, `AssessorAgent.java:28-31`; bez wywołań w `src/main`) zobaczy teraz wyjątki zamiast wyniku `failed`. Zgodnie z lekcją FIRE_ONCE (`context/foundation/lessons.md:82-87`) nie może ponawiać płatnego wywołania.

**Contract**: obie pętle pomiarowe mierzą czas wywołania i łapią `LlmUnavailableException`. Zapisują wtedy `assessor.failed(((Preparation.Prepared) assessor.prepare(input)).request(), ex, elapsed)` (wzorzec `prepare` z `test/measurement/Replay.java:99-100`; `prepare` i `failed` są publiczne). Ich wynik i pliki nagrań się nie zmieniają. `AssessorAgent`: akcja dostaje `actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE`, a `AssessorAgentIT` przypadek „klient rzuca `LlmUnavailableException` → akcja wykonana raz”.

#### 4. Testy

**File**: `test/pipeline/assess/ConcurrentAssessmentDispatcherTest.java`, `test/agents/assessor/AssessorTest.java`, `test/pipeline/PipelineAgentIT.java`

**Intent**: przypiąć bezpiecznik, odroczone liczenie i brak prób.

**Contract**:

- `AssessorTest`: `LlmUnavailableException` z klienta wychodzi z `assess`, a zwykły `LlmCallException` dalej daje `failed`.
- Dyspozytor (skryptowany `LlmClient`, współbieżność 1 dla deterministycznej kolejności):
  - 3 awarie z rzędu → `stoppedBy` z „unavailable”; pozostałe posty `NotTaken` i zwolnione; zero prób; zero `countTechnicalFailure`; zero `processing_errors`.
  - awaria, sukces, awaria, sukces → bez zatrzymania; dwa posty z `countTechnicalFailure`; `processing_errors = 2`; status `partial`.
  - 2 awarie, potem odmowa modelu (`failed`), potem awaria → odmowa zeruje serię, więc bez zatrzymania.
  - prefiltr między awariami nie zeruje serii.
  - awaria z zadania po `cancel()` → `NotTaken`, nic nie liczone.
  - przebieg przestaje być `running` przed końcem kroku (deploy: zwolnione posty, a potem pojedyncze awarie z przerwanych wywołań) → zero `countTechnicalFailure` ze zmianą wiersza i zero `processing_errors`.
- `PipelineAgentIT`, scenariusz deployu według wzorca Order 7 (`:363-419`): odpowiedź modelu trzymana na zatrzasku, `onClose()` drugiej instancji serwisu, potem zwolnienie zatrzasku z `LlmUnavailableException`. Posty mają `technical_failures = 0` i `assessment_attempts` bez zmian.
- `PipelineAgentIT`, obok przypadku odrzuconego klucza (`aRefusedLlmKeyFailsTheRunWithoutUsingAnAttempt`, `:421-442`): mockowany `LlmClient` (`:124-125`) przepuszcza `check()`, a `complete` rzuca `LlmUnavailableException`. Przebieg RANGE kończy się `failed` z błędem „unavailable”. Posty zostają `stored` z `assessment_attempts = 0` i `technical_failures = 0`, a admin ma powiadomienie.
- IT z prawdziwym transportem (osobna klasa, bo `PipelineAgentIT` mockuje `LlmClient`). Klient zbudowany przez `LlmClientFactory.create(...)` z jawnymi `LlmProviderProperties`, na `ScriptedProviderServer` (`test/llm/ScriptedProviderServer.java`): odpowiedź ok na check, potem HTTP 500 na wszystko. Wywołania przechodzą przez `TransportRetry`, kończą się `LlmUnavailableException`, a po trzech postach bezpiecznik kończy przebieg `failed` bez prób i bez błędów technicznych. To jedyny test łańcucha transport → `Assessor` → dyspozytor. W PR env awarii nie da się wywołać: deepseek-v4-flash odpowiada w ≤ 5,1 s, a `LLM_TIMEOUT` ma minimum 10 s (przegląd planu, F3).

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest='ConcurrentAssessmentDispatcherTest,AssessorTest'`
- Testy integracyjne przebiegu przechodzą (`PipelineAgentIT` i IT z prawdziwym transportem): `cd szczur-radar-api && ./mvnw -B verify`
- Kod pomiarowy się kompiluje: `cd szczur-radar-api && ./mvnw -B test-compile`
- Pełny zestaw testów przechodzi: `cd szczur-radar-api && ./mvnw -B verify`

#### Manual Verification:

- W PR env zwykły przebieg admina kończy się jak przed zmianą (`succeeded` albo `partial`), bez nowych błędów technicznych i bez nowych postów wstrzymanych (regresja na żywym dostawcy)

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed następną fazą.

---

## Faza 4: Wstrzymane posty na liście nieudanych (API i UI)

### Overview

Wstrzymany post jest widoczny tam, gdzie admin szuka nieudanych postów, z liczbą błędów technicznych i ostatnim błędem. Można go ponowić tym samym przyciskiem co post `failed`.

### Changes Required:

#### 1. API listy nieudanych

**File**: `api/posts/api/FailedPostController.java`, `api/posts/PostRepository.java`

**Intent**: lista obejmuje `failed`, `dead` i wstrzymane, najnowsze najpierw, z polami błędu technicznego.

**Contract**:

- Nowe zapytanie stronicowane: `stage IN (failed, dead) OR (stage = stored AND technical_failures >= MAX_TECHNICAL_FAILURES)`, z sortowaniem `NEWEST_FIRST`.
- `FailedPost` dostaje `int technicalFailures`, `@Nullable String lastTechnicalError` i `boolean onHold` (= `technicalFailures >= MAX_TECHNICAL_FAILURES` i nie `dead`). Javadoc rekordu opisuje trzy przypadki.
- `lastAttempt` zostaje: post `failed` z błędami technicznymi ma oba.
- Trasa, uprawnienia (`ADMIN_ROUTES`) i stronicowanie bez zmian.

#### 2. Test API

**File**: `test/posts/api/PostsApiIT.java` (przypadki listy nieudanych, `:360-385`)

**Intent**: wstrzymany `stored` jest na liście z polami. Post `stored` z 2 błędami nie jest. Post `assessed` po zerowaniu nie jest.

**Contract**: kształt JSON `technicalFailures`, `lastTechnicalError`, `onHold`.

#### 3. SPA: typy i lista w `/admin`

**File**: `web/api/posts.ts`, `web/views/AdminView.vue`, `web/labels.ts` (jeśli etykieta trafia do słownika)

**Intent**: wiersz wstrzymanego posta mówi „wstrzymany · błędy techniczne N · <ostatni błąd>”, a jego pole wyboru jest aktywne. Wiersz `failed` z błędami technicznymi pokazuje oba powody. `dead` bez zmian.

**Contract**:

- `FailedPost` (`web/api/posts.ts:102-118`) dostaje trzy pola.
- `PostStage` obejmuje `'stored'` (`:90`).
- `retryable` w `AdminView.vue:195` wyklucza tylko `dead`.
- Wiersz (`:341-370`) używa istniejących elementów i tokenów. Bez nowych kolorów, literałów i własnych stylów pól (zasady UI w `REPOSITORY_GUIDELINES.md`). Długi błąd jest przycinany wizualnie, z pełnym tekstem w `title`.

#### 4. Atrapa API i testy SPA

**File**: `szczur-radar-web/dev/mock-api.js` (`:159-163,334`), `web/views/AdminView.test.ts`, `web/api/pipeline.test.ts` (`:45-50`), stuby `FailedPost` w `web/views/PostDetailView.test.ts:16` i `web/views/FeedView.test.ts:17`

**Intent**: atrapa serwuje wstrzymany post (i `failed` z błędem technicznym), żeby dało się zrobić zrzuty. Test widoku przypina etykietę i aktywne pole wyboru. Pozostałe testy i stuby dostają nowe pola typu `FailedPost`, żeby typecheck przeszedł.

**Contract**: dane atrapy w kształcie z punktu 1. Test: wstrzymany wiersz ma tekst „wstrzymany” i „błędy techniczne 3”, a jego pole wyboru nie jest `disabled`. Ponowienie zaznaczonego wysyła jego id.

### Success Criteria:

#### Automated Verification:

- Testy API przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- Testy SPA przechodzą: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`

#### Manual Verification:

- Zrzuty `/admin` z `dev/mock-api.js` + `playwright-cli` w obu motywach i przy 375 px pokazują wstrzymany wiersz i wiersz `failed` z błędem technicznym. Długi błąd nie rozpycha układu
- Stany listy: default, hover, focus-visible (pole wyboru wstrzymanego), disabled (pole `dead`), empty, loading, error. Każdy ma zrzut albo N/A z powodem
- W PR env lista nieudanych ładuje się bez błędu (regresja kształtu odpowiedzi)

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed następną fazą.

---

## Faza 5: Dokumentacja i runbook

### Overview

PRD, dokumentacja pakietów i plan wdrożenia opisują nową semantykę prób, bezpiecznik i wstrzymanie. Runbook daje operatorowi zapytanie do oceny postów, którym awarie zużyły próby przed wdrożeniem.

### Changes Required:

#### 1. PRD

**File**: `context/foundation/prd.md`

**Intent**: doprecyzowanie S-09 pod NFR sygnału awarii (po `:262`), bo zmienia rozumienie „błędu technicznego” z doprecyzowania S-06 (`:261`).

**Contract**: `> Doprecyzowanie (<data>, S-09 attempt-accounting): …` mówi:

- awaria dostawcy po ponowieniach nie zużywa próby;
- trzy z rzędu kończą przebieg `failed` (te same sygnały co każdy `failed`), a posty wracają bez prób i bez błędów technicznych;
- pojedyncza awaria jest błędem technicznym posta (`partial`, powiadomienie);
- trzy błędy techniczne z rzędu wstrzymują post: bez automatycznego ponawiania, na liście nieudanych, z ponowieniem przez admina;
- RETRY nie bierze wstrzymanych.

#### 2. Dokumentacja pakietu przebiegów

**File**: `api/pipeline/CLAUDE.md`

**Intent**: zasady prób (`:160-163`) i opis dyspozytora (`:57`, `:59`) odpowiadają nowemu modelowi.

**Contract**:

- Trzy winy i ich skutki.
- Bezpiecznik: stała 3, „z rzędu” w kolejności zakończenia, zerowanie przez wywołanie z odpowiedzią.
- Odroczone liczenie awarii.
- Licznik `technical_failures`, wstrzymanie i zerowanie przy zapisie.
- Lista testów (`:191-196`, `:217-227`) z nowymi przypadkami.

#### 3. Plan wdrożenia

**File**: `context/deployment/deploy-plan.md`

**Intent**: nowa sekcja S-09 (runbook) i poprawki zdań, które przestają być prawdą.

**Contract**:

- Sekcja S-09:
  - co widać przy awarii dostawcy (przebieg `failed`, błąd „unavailable”, posty bez prób) i co robić (nic: RETRY następnej nocy albo przebieg admina po powrocie dostawcy);
  - co oznacza wstrzymany post i jak go odzyskać (naprawić przyczynę, „Ponów zaznaczone”);
  - zapytanie SQL tylko do odczytu, które liczy posty `failed`/`dead`, których każdy wiersz `failed` to `timeout` albo `provider_error` z komunikatem „failed after”;
  - weryfikacja po merge'u jako komentarz w issue #68, nie wiersz Progress.
- Poprawki:
  - `:162` (flex przechodzi na default po każdym wyczerpanym tierze);
  - `:216` i `:223` (awaria nie zużywa próby);
  - `:468` (noc po awarii dostawcy: przebieg zatrzymany przez bezpiecznik zamiast RETRY bez limitu);
  - reguła powiadomień S-06 (`:730-731`, `:852-853`, `:1168`): błąd techniczny obejmuje pojedynczą awarię dostawcy.

#### 4. Roadmapa

**File**: `context/foundation/roadmap.md`

**Intent**: niewiadoma S-09 (`:171`, `:321`) jest rozstrzygnięta.

**Contract**: wpis niewiadomej dostaje rozstrzygnięcie („awaria nie zużywa prób; bezpiecznik 3 z rzędu; S-09 plan”). Status pozycji zmieniają skille `/10x-implement` i `/10x-archive`, nie ta faza.

### Success Criteria:

#### Automated Verification:

- W `pipeline/CLAUDE.md` i `llm/CLAUDE.md` nie zostało zdanie o fallbacku tylko po trzech 429: `grep -rn "three 429\|3×429\|three consecutive 429" szczur-radar-api/src/main` nic nie zwraca
- PRD ma doprecyzowanie S-09: `grep -n "S-09" context/foundation/prd.md`

#### Manual Verification:

- Zapytanie z runbooka S-09 uruchomione lokalnie na bazie z testowymi danymi zwraca oczekiwane posty i nic nie zmienia
- Przegląd: PRD, `deploy-plan.md` i oba `CLAUDE.md` są spójne z zachowaniem z faz 1–4

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie. Weryfikacja na produkcji po merge'u to runbook i komentarz w issue #68, nie krok tego planu.

---

## Testing Strategy

### Unit Tests:

- `TransportRetryTest`: fallback flex przy mieszance i przy samych timeoutach; `LlmUnavailableException` po wyczerpaniu i po przerwaniu; `REJECTED` jako zwykły `LlmCallException`.
- Testy klientów: wyjątek SDK bez I/O wychodzi bez zmian i bez ponowień.
- `AssessorTest`: niedostępność przechodzi, odmowa daje `failed`.
- `ConcurrentAssessmentDispatcherTest`: seria 3 → zatrzymanie bez liczenia; przeplot awaria/sukces → odroczone liczenie i `partial`; odmowa zeruje serię, prefiltr nie; anulowanie → `NotTaken`; błąd naszego kodu → `releaseFailed`.

### Integration Tests:

- `PostTransitions`/`PostRepository` na Postgresie: przyrost, zerowanie, strażnik właściciela, predykaty RETRY i listy.
- `PipelineAgentIT`: przebieg z mockowanym klientem rzucającym `LlmUnavailableException` kończy się `failed`, posty bez prób i bez błędów technicznych, powiadomienie.
- IT z prawdziwym transportem (`LlmClientFactory` + `ScriptedProviderServer`, HTTP 500 po checku): bezpiecznik działa przez `TransportRetry`.
- `AssessorAgentIT`: akcja z `LlmUnavailableException` wykonana raz.
- IT listy nieudanych: wstrzymany post z polami.
- `JpaSchemaIT`: encja zgodna z V10.

### Manual Testing Steps:

1. Lokalnie: post z `technical_failures = 3` nie trafia do RETRY, trafia do POSTS i na listę nieudanych.
2. PR env: zwykły przebieg kończy się jak dotąd (regresja), bez nowych błędów technicznych, a lista nieudanych się ładuje. Bezpiecznika w PR env nie da się wywołać (deepseek-v4-flash ≤ 5,1 s wobec minimum `LLM_TIMEOUT` 10 s); pokrywa go IT z prawdziwym transportem.
4. Zrzuty `/admin` z atrapą w obu motywach i przy 375 px.

## Performance Considerations

Bezpiecznik skraca awarię: przebieg staje po ~9 nieudanych żądaniach HTTP zamiast przechodzić przez wszystkie posty po 3–6 prób każdy. Fallback flex przy mieszance wydłuża pojedyncze wywołanie w czasie awarii całego dostawcy najwyżej do istniejącego `maxCallDuration` (już liczonego na dwa tiery), więc próg watchdoga się nie zmienia. Nowe predykaty działają na tabeli `posts` rzędu tysięcy wierszy, bez indeksu.

## Migration Notes

- V10 tylko dodaje kolumny z wartościami domyślnymi: istniejące posty mają `technical_failures = 0`. Fix-forward jak każda migracja. Nowej tabeli nie ma, więc klasyfikacja w `tools/db-backup/tables.sh` się nie zmienia.
- Dane historyczne bez zmian (decyzja planu). Zapytanie z runbooka S-09 pozwala operatorowi ocenić skalę. Posty `failed` admin ponawia przyciskiem.
- Wdrożenie przerywa trwający przebieg jak każde. Obowiązują zwykłe zasady: bez merge'a przy `RUNNING` i w oknie 00:30–01:30 UTC.

## References

- Roadmapa: `context/foundation/roadmap.md:96,162-173`
- Audyt: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (A1, B2, B3, B9, Architecture Insights `:238`)
- Odłożony „Fix B”: `context/archive/2026-10-06-first-assessed-wykop-feed/reviews/impl-review-phase-2.md:26-45`
- Wzorzec konfiguracji, który nie zużywa prób: `context/archive/2026-10-06-first-assessed-wykop-feed/reviews/impl-review.md:27-44`
- Błędy w ciele 200 (pierwsza połowa B9): `context/archive/2026-10-10-openrouter-in-body-errors/plan.md`
- Fallback flex z 3×429: `context/archive/2026-09-27-assessment-contract-harness/reviews/plan-review-2026-10-01.md:36-45`
- Reguła powiadomień S-06: `context/foundation/prd.md:261`
- Wzorzec zatrzymania: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:215-223`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Klasyfikacja błędów w `llm/`

#### Automated

- [x] 1.1 Testy jednostkowe `llm/` przechodzą
- [x] 1.2 Test `flexFailingWithMixedErrorsFallsBack` istnieje, a `flexFailingOtherwiseDoesNotFallBack` nie
- [x] 1.3 Pełny zestaw testów przechodzi

#### Manual

- [ ] 1.4 Przegląd: w `src/main` nie ma zamiany dowolnego `Throwable` na `Kind.REJECTED`

### Phase 2: Licznik błędów technicznych posta (A1)

#### Automated

- [ ] 2.1 Migracja i walidacja encji przechodzą
- [ ] 2.2 Testy dyspozytora i przejść przechodzą
- [ ] 2.3 Pełny zestaw testów przechodzi
- [ ] 2.4 Test backupu przechodzi z nową migracją

#### Manual

- [ ] 2.5 Lokalnie wstrzymany post nie trafia do RETRY, a trafia do POSTS

### Phase 3: Awaria dostawcy i bezpiecznik (B2)

#### Automated

- [ ] 3.1 Testy jednostkowe dyspozytora i `Assessor` przechodzą
- [ ] 3.2 Testy integracyjne przebiegu (z prawdziwym transportem) przechodzą
- [ ] 3.3 Kod pomiarowy się kompiluje
- [ ] 3.4 Pełny zestaw testów przechodzi

#### Manual

- [ ] 3.5 PR env: zwykły przebieg kończy się jak przed zmianą (regresja)

### Phase 4: Wstrzymane posty na liście nieudanych (API i UI)

#### Automated

- [ ] 4.1 Testy API przechodzą
- [ ] 4.2 Testy SPA przechodzą
- [ ] 4.3 Typecheck i build SPA przechodzą

#### Manual

- [ ] 4.4 Zrzuty `/admin` z atrapą w obu motywach i przy 375 px
- [ ] 4.5 Macierz 7 stanów listy: zrzut albo N/A z powodem
- [ ] 4.6 W PR env lista nieudanych ładuje się bez błędu

### Phase 5: Dokumentacja i runbook

#### Automated

- [ ] 5.1 W dokumentacji pakietów nie zostało zdanie o fallbacku tylko po trzech 429
- [ ] 5.2 PRD ma doprecyzowanie S-09

#### Manual

- [ ] 5.3 Zapytanie z runbooka S-09 zwraca oczekiwane posty i nic nie zmienia
- [ ] 5.4 PRD, `deploy-plan.md` i oba `CLAUDE.md` są spójne z zachowaniem z faz 1–4
