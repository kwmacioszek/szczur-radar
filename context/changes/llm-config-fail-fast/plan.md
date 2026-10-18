# S-10 llm-config-fail-fast — Implementation Plan

## Overview

Zła wartość ustawienia czasu ma zatrzymać start serwera i komend operatora (`migrate`, `create-admin`, `create-user`, `reset-password`) czytelnym błędem, a nie dopiero co noc kończyć przebieg `failed`. Za złą wartość uznajemy:

- `LLM_TIMEOUT=120` (dziś 120 ms);
- zapis ISO-8601;
- wartość poza dozwolonym zakresem, w tym `LLM_TIMEOUT` poniżej `10s`.

Trzy ustawienia czasu w API będą parsowane jedną regułą. Kontrakt `LLM_*` (rekord `LlmProviderProperties` i enumy `Provider`, `ServiceTier`, `ReasoningEffort`) przechodzi z `config/` do `llm/`, co usuwa krawędź encja `posts/Assessment` → `config` (roadmapa S-10, audyt B1 i E).

## Current State Analysis

Pełne ustalenia z kotwicami są w `research.md`. Najważniejsze:

- **Walidacja przed kontekstem już działa.** `config/LlmConfigurationCheck` (`:14-19`, wpis w `src/main/resources/META-INF/spring.factories:1-2`) woła `LlmProviderProperties.from` na `ApplicationEnvironmentPreparedEvent`. Dzieje się to przy starcie serwera, w każdym kontekście komendy CLI i w każdym teście ze `SpringApplication`.
- **`LLM_TIMEOUT` przyjmuje gołą liczbę jako milisekundy.** Odrzucane są tylko wartości `> 30m` i `<= 0` (`config/LlmProviderProperties.java:175-191`).
- **Trzy kopie parsowania czasu mają różne reguły:**
  - `LLM_TIMEOUT` — `config/LlmProviderProperties.java:175-191`;
  - `ACTIVATION_LINK_TTL` — `auth/activation/ActivationSettings.java:137-156`;
  - `PIPELINE_STALL_TIMEOUT` — `pipeline/PipelineStallSettings.java:99-124`.

  Wszystkie trzy przyjmują ISO-8601 przez `DurationStyle.detectAndParse`. Mają też trzy style zgłaszania błędu: lista problemów, `Result`, wyjątek.
- **Operator widzi dziś stack trace.** Repo nie ma `FailureAnalyzer`, więc Boot loguje „Application run failed” ze stack trace'em. Komendy CLI łapią `RuntimeException` w trzech kopiach (`cli/MigrateCommand.java:44-51`, `cli/CreateAdminCommand.java:76-82`, `cli/LinkCommand.java:109-115`) i zwracają `ExitCode.FAILED` (1). `ExitCode.USAGE` (2) jest przewidziany dla „missing settings” (`cli/ExitCode.java:19-23`).
- **Enumy dostawcy to krawędź encja → `config`.** Siedzą w `config/LlmProviderProperties.java:65-102`, a importuje je m.in. encja `posts/Assessment.java:26-28`. Rekord albo jego enumy importuje 21 plików Java w `src/main` (11 z nich w `llm/`) i 23 pliki testów.

## Desired End State

- **Ustawienia czasu.** `LLM_TIMEOUT`, `ACTIVATION_LINK_TTL` i `PIPELINE_STALL_TIMEOUT` przyjmują tylko zapis `<liczba całkowita><jednostka>` (`ns`, `us`, `ms`, `s`, `m`, `h`, `d`), w zakresie podanym dla każdego ustawienia:

  | Ustawienie | Zakres (włącznie) |
  |---|---|
  | `LLM_TIMEOUT` | `10s`–`30m` |
  | `ACTIVATION_LINK_TTL` | `1m`–`7d` |
  | `PIPELINE_STALL_TIMEOUT` | `1ms`–`1d` (= `24h`) |

  Goła liczba, ISO-8601 (`PT5M`), ułamek, nieznana jednostka i wartość spoza zakresu dają komunikat, który nazywa ustawienie i podaną wartość. Pusta albo nieustawiona wartość dalej daje domyślną.
- **Gdzie operator widzi błąd:**
  - zły `LLM_*` → start serwera i każda komenda CLI logują blok Spring Boot `APPLICATION FAILED TO START` (opis z listą problemów, działanie wskazujące README, bez stack trace'u na poziomie ERROR), a komenda kończy się kodem `2` (`ExitCode.USAGE`);
  - zły `PIPELINE_STALL_TIMEOUT` → ten sam blok przy starcie serwera (czyta go tylko serwer, `RunWatchdog`);
  - zły `ACTIVATION_LINK_TTL` → bez zmian ścieżki: `create-user` / `reset-password` drukują „Activation links are not configured: …” i kończą się kodem `2`, zanim powstanie kontekst (`cli/LinkCommand.java:103-106,130-138`).

  Klucz `LLM_API_KEY` nigdy nie pojawia się w wyjściu.
- **Pakiety.** `Provider`, `ServiceTier`, `ReasoningEffort` i `LlmProviderProperties` są w korzeniu `pl.szczurradar.api.llm`. W `config/` nie ma żadnego typu LLM poza `LlmConfigurationCheck` i nowym analyzerem. Nowy pakiet-liść `settings/` niczego nie importuje z `pl.szczurradar.*`.
- **Weryfikacja:**
  - `./mvnw -B verify` jest zielone;
  - w Railway PR env ustawienie `LLM_TIMEOUT=120` zatrzymuje pre-deploy `migrate` blokiem `APPLICATION FAILED TO START`;
  - przed merge'em odczyt `LLM_TIMEOUT` na produkcji potwierdza wartość zgodną z nową regułą.

### Key Discoveries:

- **Boot raportuje błąd z listenera przez analyzery.** Wyjątek z `prepareEnvironment` trafia do `handleRunFailure` → `reportFailure` (Boot 4.0.8 `SpringApplication.java:314-331,842-866`). `FailureAnalyzers` ładuje się z kontekstem `null` (`ArgumentResolver.none()`, `:830-838`), więc analyzer potrzebuje konstruktora bez argumentów. Rozpoznany błąd trafia do `LoggingFailureAnalysisReporter`: blok na ERROR, stack trace tylko na DEBUG (`LoggingFailureAnalysisReporter.java:36-40`).
- **Błąd `PIPELINE_STALL_TIMEOUT` przychodzi owinięty.** Powstaje w beanie `RunWatchdog` (tylko serwer, `pipeline/RunWatchdog.java:58-61`), więc dociera owinięty w `BeanCreationException`. `AbstractFailureAnalyzer<T>` szuka przyczyny typu `T` w całym łańcuchu.
- **Testy wymagają `IllegalStateException`.** `LlmProviderConfigTest` używa `assertThatIllegalStateException` (np. `:116-206`), więc nowy wyjątek ustawień dziedziczy po `IllegalStateException`.
- **Granice zostają w `from()`, nie w rekordzie.** `RunWatchdogTest.java:100-104` ustawia przez `PipelineStallSettings.from` wartość `500ms` (stąd dolna granica `1ms`). Testy klientów budują `LlmProviderProperties` wprost z 300 ms (`GeminiClientTest.java:187`, `DeepSeekClientTest.java:129`, `OpenRouterClientTest.java:199`). Konstruktor kompaktowy rekordu nie może mieć granic.
- **Ustawienia linku są czytane przed kontekstem.** `ActivationSettings` czyta przez `Function<String, String>` (`System::getenv`) przed startem kontekstu i zwraca `Result`. `LinkCommand.settings()` drukuje problemy i zwraca `USAGE` (`cli/LinkCommand.java:103-106,130-138`), więc ta ścieżka nie potrzebuje analyzera.
- **Wartości enumów są zapisane w bazie.** `id()` i nazwy stałych muszą zostać bez zmian:
  - `CHECK` w `V3__create_pipeline_and_posts.sql:170-174`;
  - nazwy profili `application-<id>.properties` (`application.properties:53`);
  - `measurement/Replay.java:118` (`ServiceTier.valueOf(…toUpperCase())`);
  - `JpaSchemaIT.java:196-199`.

## What We're NOT Doing

- **Spring nadal wiąże `spring.session.timeout`** (`application.properties:42`) i nie trafia ono pod regułę. Nie jest też mapowane ze zmiennej w IaC.
- **`Retry-After` zostaje bez zmian** (`llm/transport/TransportException.java:117-123`). To wartość protokołu w sekundach, nie ustawienie.
- **Domyślne wartości i górne granice zostają bez zmian:** `120s`/`30m`, `12h`/`7d`, obliczany stall timeout/`24h`. Dalej działa też WARN, gdy jawny `PIPELINE_STALL_TIMEOUT` jest poniżej minimum.
- **Nie łączymy trzech stylów zgłaszania błędu** (lista, `Result`, wyjątek). Wspólny jest tylko parser.
- **Nie zmieniamy przycinania wartości w `sources/wykop/WykopConfiguration` i `internal/PipelineTriggerToken`.** Nie są to ustawienia czasu.
- **Nie wprowadzamy granic w konstruktorze rekordu `LlmProviderProperties`.** Nie sprawdzamy też LLM u dostawcy przy starcie, bo klient zostaje leniwy.
- **Nie zmieniamy `.railway/railway.ts` ani CI.** Same zmienne produkcji też zostają, chyba że odczyt przed merge'em pokaże wartość niezgodną z regułą — wtedy poprawia ją operator.
- **Nie edytujemy `context/archive/**`.** Pozycja F4 w `2026-10-12-cli-user-onboarding/follow-ups/review-fixes.md` zostaje w archiwum, a zamknięcie odnotowujemy w issue S-10.
- **Nie dodajemy testów architektury** (ArchUnit itp.).

## Implementation Approach

Fazy idą w kolejności zależności:

1. **Refaktor bez zmiany zachowania.** Kontrakt `LLM_*` przechodzi do `llm/` jako osobny commit potwierdzony testami (lekcja „Proaktywnie szukaj okazji do abstrakcji…”), żeby zmiana reguły nie mieszała się z przenosinami.
2. **Wspólny parser w `settings/`.** Dostaje go każde z trzech ustawień, z własnymi granicami.
3. **Czytelny błąd.** Wyjątek ustawień, `FailureAnalyzer` i jeden pomocnik CLI zwracający `USAGE`.
4. **Dokumenty i weryfikacja.** Dokumenty dla operatora i agentów, potem weryfikacja w PR env i odczyt produkcji przed merge'em.

Parser to czysta funkcja: tekst, nazwa i granice wchodzą, a wychodzi `Duration` albo problem dopisany do listy wywołującego. Każdy wywołujący zachowuje swój sposób zgłaszania błędu (A zbiera, B zwraca `Result`, C rzuca). Domyślna wartość zostaje po stronie wywołującego, bo C ją oblicza.

## Critical Implementation Details

### Timing & lifecycle

- **Listener rzuca przed kontekstem.** `LlmConfigurationCheck` rzuca przed utworzeniem kontekstu, więc analyzer ładuje się bez `BeanFactory` i nie może wstrzykiwać beanów ani `Environment`.
- **`LlmConfigurationCheck` zostaje bez `@Order`.** Działa wtedy po `LoggingApplicationListener` (`HIGHEST_PRECEDENCE+20`), więc logowanie ma już konfigurację aplikacji. Z wysokim priorytetem wystartowałby na domyślnym DEBUG Logbacka i reporter wydrukowałby stack trace. `DEBUG=true` w środowisku też dokłada stack trace na DEBUG — to oczekiwane.
- **Komendy CLI dostają wyjątek po raporcie Boot.** `SpringApplication.run` rzuca dalej po zalogowaniu bloku, więc pomocnik CLI widzi ten sam wyjątek, zwykle jako przyczynę w łańcuchu, i drukuje jedną krótką linię bez powtarzania całego bloku.

## Phase 1: Kontrakt LLM w `llm/`

### Overview

Refaktor bez zmiany zachowania: rekord `LlmProviderProperties` i trzy enumy przechodzą do korzenia `pl.szczurradar.api.llm`. W `config/` zostaje `LlmConfigurationCheck` (od teraz importuje `llm.LlmProviderProperties`).

### Changes Required:

#### 1. Enumy jako osobne typy w `llm/`

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/Provider.java`, `ServiceTier.java`, `ReasoningEffort.java` (nowe); `config/LlmProviderProperties.java` (usunięte zagnieżdżone enumy)

**Intent**: Wyjąć zagnieżdżone enumy do osobnych publicznych typów obok `Channel` i `OutputMode`, żeby encja, konwertery i JSON nie zależały od klasy konfiguracji.

**Contract**:
- Te same stałe w tej samej kolejności, ten sam `@JsonValue id()` (nazwa małymi literami) i ta sama Javadoc o znaczeniu wartości.
- `Provider.fromId` znika. `LlmProviderProperties.from` rozpoznaje dostawcę tym samym prywatnym `oneOf(...)`, co poziom i wysiłek, a komunikat „must be one of gemini, deepseek, openrouter (not set | got '…')” zostaje bez zmian.

#### 2. Rekord `LlmProviderProperties` w `llm/`

**File**: `config/LlmProviderProperties.java` → `llm/LlmProviderProperties.java`

**Intent**: `llm/` staje się właścicielem kontraktu `LLM_*` (już tak go opisuje `llm/CLAUDE.md`). Dzięki temu nie powstaje cykl `config ↔ llm`.

**Contract**: Ten sam publiczny API rekordu: komponenty, stałe nazw zmiennych, `DEFAULT_TIMEOUT`, `MAX_TIMEOUT`, `from(PropertyResolver)`, `toString` bez klucza. Zachowanie `from()` bez zmian w tej fazie.

#### 3. Importy w kodzie i testach

**File**: wszystkie pliki z `import pl.szczurradar.api.config.LlmProviderProperties[.X]` (lista w `research.md`, sekcja 4; 21 plików Java w `src/main` i 23 pliki testów, w tym `config/LlmConfigurationCheck`, `posts/Assessment`, `persistence/converters/*`, `agents/assessor/*`, `llm/**`, `pipeline/*`, `posts/api/FailedPostController`, `measurement/*`).

**Intent**: Mechaniczna zmiana importów, najlepiej przenosinami w IntelliJ (MCP `rename_refactoring` / move), żeby nie pominąć użycia.

**Contract**:
- `LlmProviderConfigTest` testuje teraz typ z `llm/`. Przenosimy go do `src/test/java/pl/szczurradar/api/llm/` razem z rekordem, a jego przypadki zostają bez zmian.
- Komentarz `application.properties:50` wskazuje `llm/LlmProviderProperties`.

#### 4. Opisy pakietów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md` (`:30`, `:85`), `REPOSITORY_GUIDELINES.md` (opis pakietów `config`, `llm`), `szczur-radar-api/README.md` (`:67`), `szczur-radar-api/pom.xml` (komentarz `:87`). `context/map/**` zostaje bez zmian: to datowane migawki mapy.

**Intent**: Następny agent ma szukać kontraktu `LLM_*` i enumów dostawcy w `llm/`.

**Contract**:
- `llm/CLAUDE.md`: lista typów korzenia obejmuje `LlmProviderProperties`, `Provider`, `ServiceTier`, `ReasoningEffort`. Krok „adding a provider” wskazuje `llm/Provider`. Reguła append-only dla zapisywanych stałych zostaje.

### Success Criteria:

#### Automated Verification:

- Pełny build i testy bez zmian zachowania: `cd szczur-radar-api && ./mvnw -B verify`
- W `src/main` i `src/test` nie ma importu `pl.szczurradar.api.config.LlmProviderProperties` (`grep -rn "config.LlmProviderProperties" szczur-radar-api/src` nic nie zwraca)
- `JpaSchemaIT` przechodzi z niezmienionymi wartościami `"openrouter"`, `"flex"`, `"low"`
- Poza `src` nie ma odwołań do starego miejsca (`grep -rn --exclude-dir=target "config/LlmProviderProperties\|config\.LlmProviderProperties" szczur-radar-api REPOSITORY_GUIDELINES.md` nic nie zwraca)

#### Manual Verification:

- Przegląd diffu: tylko przenosiny i importy, bez zmiany logiki

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie ręcznej części przez człowieka przed fazą 2.

---

## Phase 2: Jedna reguła czasu

### Overview

Nowy pakiet-liść `settings/` z parserem czasu i wspólnym przycinaniem. Trzy ustawienia czasu korzystają z niego z własnymi granicami, a `LLM_TIMEOUT` dostaje wymaganą jednostkę i dolną granicę `10s`.

### Changes Required:

#### 1. Pakiet `settings/`

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/settings/DurationSetting.java`, `settings/SettingText.java` (nowe)

**Intent**: Jedno miejsce na regułę czasu i na przycinanie wartości ze środowiska, bez zależności od reszty aplikacji (jak `text/Whitespace`), żeby `ActivationSettings` zostało bez zależności od Springa i od `config/`.

**Contract**:
- `SettingText.trimmed(@Nullable String) → String`: `null` → `""`, w pozostałych przypadkach `strip()`. Zastępuje kopie w A (`:193-195`), B (`:158-160`) i C (`:63-64`).
- `DurationSetting.parse(String name, String text, Duration min, Duration max, List<String> problems) → @Nullable Duration`. `text` jest już przycięty i niepusty; pusty obsługuje wywołujący (wartość domyślna). Przy błędzie dopisuje do `problems` jeden komunikat i zwraca `null`. Reguły w tej kolejności:
  1. `[+-]?\d+` → `<name> needs a unit, e.g. 300s or 5m (got '<text>'; a bare number would be milliseconds)`;
  2. nie pasuje do `[+-]?\d+(ns|us|ms|s|m|h|d)` (w tym ISO-8601, ułamki, spacje, `12x`) albo przepełnienie przy parsowaniu → `<name> must be a duration such as 300s or 5m (got '<text>')`;
  3. poza `[min, max]` (włącznie) → `<name> must be from <min> to <max> (got '<text>')`, gdzie granice są zapisane w największej całkowitej jednostce (`1ms`, `10s`, `1m`, `30m`, `1d`, `7d`).
- Parsowanie zatwierdzonego tekstu: `DurationStyle.SIMPLE.parse(text)` — tylko styl prosty, nigdy `detectAndParse`.
- Jednostki tylko małymi literami: `SIMPLE` sam przyjąłby `5M` i `300S` (`fromSuffix` ignoruje wielkość liter), ale regex z reguły 2 je odrzuca jako „must be a duration”.
- `-5m` i `+5m` przechodzą regex i `SIMPLE.parse`; wartości ujemne i zero odrzuca dopiero reguła 3 (zakres), więc `min` musi być dodatnie.
- Boot nie ma drukarki „największa całkowita jednostka” (`DurationStyle.print` drukuje ms, enum `Unit` jest package-private). `DurationSetting` ma własny mały formatter granic: od dni w dół do nanosekund, pierwsza jednostka, która dzieli wartość bez reszty.

#### 2. `LLM_TIMEOUT`

**File**: `llm/LlmProviderProperties.java`

**Intent**: Zamknąć klasę incydentu z 2026-10-06: goła liczba i wartość poniżej `10s` przestają przechodzić.

**Contract**:
- Nowa stała `MIN_TIMEOUT = 10s`.
- Prywatne `timeout(...)` zostaje zastąpione przez `DurationSetting.parse(TIMEOUT, text, MIN_TIMEOUT, MAX_TIMEOUT, problems)`.
- Problem dalej trafia na wspólną listę wszystkich `LLM_*`, a komunikat zbiorczy „Invalid LLM configuration: …” zostaje.

#### 3. `ACTIVATION_LINK_TTL`

**File**: `auth/activation/ActivationSettings.java`

**Intent**: Ta sama reguła i te same komunikaty co przy pozostałych ustawieniach.

**Contract**: `linkTtl(...)` deleguje do `DurationSetting.parse(LINK_TTL, text, MIN_LINK_TTL, MAX_LINK_TTL, problems)`. `Result.Valid` / `Result.Invalid` i drukowanie w `LinkCommand` zostają bez zmian.

#### 4. `PIPELINE_STALL_TIMEOUT`

**File**: `pipeline/PipelineStallSettings.java`

**Intent**: Ta sama reguła. Wyjątek nadal rzucany od razu, z podpowiedzią o wartości domyślnej.

**Contract**:
- Nowa stała `MIN = 1ms`; `MAX = 24h` bez zmian (komunikat i dokumenty zapisują go jako `1d`).
- `parse(...)` woła `DurationSetting.parse(NAME, text, MIN, MAX, problems)` na własnej liście i przy problemie rzuca `IllegalStateException` z komunikatem `Invalid configuration: <problem>, or unset for the default (<default>).` (problem zaczyna się od nazwy ustawienia; asercja prefiksu w `PipelineStallSettingsTest` zmienia się na `Invalid configuration: PIPELINE_STALL_TIMEOUT`). W fazie 3 ten wyjątek zmienia typ.
- WARN poniżej `minimum()` bez zmian.

#### 5. Testy

**File**: `src/test/java/pl/szczurradar/api/settings/DurationSettingTest.java` (nowy), `SettingTextTest.java` (nowy); `llm/LlmProviderConfigTest.java`, `auth/activation/ActivationSettingsTest.java`, `pipeline/PipelineStallSettingsTest.java`, `cli/CreateUserCommandTest.java`, `cli/ResetPasswordCommandTest.java` (zmienione asercje)

**Intent**: Przypiąć regułę raz w parserze, a w każdym ustawieniu jego granice i to, że błąd trafia we właściwą ścieżkę zgłaszania.

**Contract**:
- `DurationSettingTest`:
  - goła liczba: `120`, `+5`, `-5`;
  - ISO-8601: `PT5M`, `PT0.1S`;
  - ułamek, spacja i zła jednostka: `1.5h`, `12 hours`, `12x`, `soon`;
  - przepełnienie: `9999999999999999999d`;
  - wielkie litery: `300S`, `5M`;
  - wartość ujemna z jednostką: `-5m` (komunikat zakresu);
  - granice włącznie i tuż poza nimi;
  - każda jednostka `ns`…`d`;
  - format granic w komunikacie.
- `LlmProviderConfigTest`:
  - `LLM_TIMEOUT=120` → „needs a unit”;
  - `PT2M` → „must be a duration”;
  - `9s` → „must be from 10s to 30m”;
  - `10s` i `30m` przechodzą;
  - `0s`, `-5m`, `31m` → komunikat zakresu;
  - nowe komunikaty zastępują dotychczasowe asercje „positive duration” i „at most 30m”.
- Pozostałe testy ustawień: zmienione podciągi komunikatów; `500ms` w `RunWatchdogTest` i `5s` w `RunWatchdogIT` dalej przechodzą.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe parsera i ustawień przechodzą: `cd szczur-radar-api && ./mvnw -B test`
- Pełna bramka: `cd szczur-radar-api && ./mvnw -B verify`
- `DurationStyle.detectAndParse` nie występuje już w `src/main` (`grep -rn "detectAndParse" szczur-radar-api/src/main` nic nie zwraca)
- `settings/` nie importuje nic z aplikacji (`grep -rn "import pl.szczurradar" szczur-radar-api/src/main/java/pl/szczurradar/api/settings` nic nie zwraca)

#### Manual Verification:

- Lokalnie `LLM_TIMEOUT=120` zatrzymuje `migrate` z „LLM_TIMEOUT needs a unit”, a `300s` przechodzi (`java -jar target/szczur-radar-api.jar migrate`, lokalna baza z `README.md`)

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie ręcznej części przez człowieka przed fazą 3.

---

## Phase 3: Czytelny błąd startu

### Overview

Złe ustawienie daje blok `APPLICATION FAILED TO START` zamiast stack trace'u, a komendy CLI kończą się kodem `2`.

### Changes Required:

#### 1. Wyjątek złych ustawień

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/settings/InvalidSettingsException.java` (nowy); `llm/LlmProviderProperties.java`, `pipeline/PipelineStallSettings.java`

**Intent**: Jeden typ, po którym analyzer i CLI rozpoznają błąd konfiguracji, odróżniając go od awarii bazy czy migracji.

**Contract**:
- `InvalidSettingsException extends IllegalStateException`, z komunikatem w tej samej formie co dziś: „Invalid LLM configuration: …. See szczur-radar-api/README.md (LLM provider).” oraz komunikat `PIPELINE_STALL_TIMEOUT` z fazy 2.
- Komunikat nigdy nie zawiera wartości `LLM_API_KEY`.
- Rzucają go `LlmProviderProperties.from` i `PipelineStallSettings.from`.

#### 2. `FailureAnalyzer`

**File**: `config/InvalidSettingsFailureAnalyzer.java` (nowy), `src/main/resources/META-INF/spring.factories`

**Intent**: Boot pokazuje operatorowi opis i działanie zamiast stack trace'u, tak samo przy starcie serwera i przy komendach CLI.

**Contract**:
- `AbstractFailureAnalyzer<InvalidSettingsException>` z konstruktorem bez argumentów. Ma działać także z kontekstem `null`.
- `FailureAnalysis`: opis to komunikat wyjątku; działanie to „Fix the named setting(s) and start again; see szczur-radar-api/README.md.”
- Rejestracja w `spring.factories` kluczem `org.springframework.boot.diagnostics.FailureAnalyzer`, obok istniejącego wpisu listenera.

#### 3. Kod wyjścia komend CLI

**File**: `cli/` (wspólny pomocnik, np. w `CommandChecks`), `cli/MigrateCommand.java`, `cli/CreateAdminCommand.java`, `cli/LinkCommand.java`

**Intent**: Błąd konfiguracji z kontekstu komendy kończy się `ExitCode.USAGE` (2) zgodnie z opisem `ExitCode`. Inne awarie dalej dają `FAILED` (1). Trzy kopie bloku `catch` zastępuje jeden pomocnik.

**Contract**:
- Pomocnik przyjmuje komendę, wyjątek i `err`.
- Jeśli łańcuch przyczyn zawiera `InvalidSettingsException`, drukuje `<cmd>: invalid configuration: <message>` i zwraca `USAGE`.
- W przeciwnym razie drukuje jak dziś `<cmd> failed: <message>` i zwraca `FAILED`.
- Javadoc `ExitCode.USAGE` i komend obejmuje ten przypadek.

#### 4. Testy

**File**: `config/InvalidSettingsFailureAnalyzerTest.java` (nowy), `llm/LlmProviderConfigTest.java` albo nowy test startu, `cli/MigrateCommandTest.java`, `cli/CreateAdminCommandTest.java`, `cli/CreateUserCommandTest.java` / `ResetPasswordCommandTest.java`

**Intent**: Przypiąć, że operator dostaje blok bez stack trace'u i kod `2`, i że klucz nie wycieka.

**Contract**:
- **Analyzer:** rozpoznaje wyjątek wprost i owinięty w `BeanCreationException`, a ignoruje inny `IllegalStateException`.
- **Test startu** (`OutputCaptureExtension`, minimalny `SpringApplication` jak helper `environment()` w `LlmProviderConfigTest`, bez wyciszania loggera raportu) przy `LLM_TIMEOUT=120` i znanym kluczu syntetycznym:
  - wyjście zawiera `APPLICATION FAILED TO START` i „LLM_TIMEOUT needs a unit”;
  - nie zawiera „Application run failed” ani wartości klucza.
- **Komendy:** starter kontekstu rzucający `InvalidSettingsException` (także owinięty) daje `USAGE` i linię „invalid configuration”. Inny `RuntimeException` dalej daje `FAILED` i „failed: …”.
- **Cisza w testach negatywnych:** helper `environment()` w `LlmProviderConfigTest` dodaje `--logging.level.org.springframework.boot.diagnostics=OFF` (obok wyciszenia `SpringApplication`), żeby ~15 przypadków nie logowało bloku ERROR. Test startu z `OutputCaptureExtension` tego loggera nie wycisza, bo sprawdza właśnie ten blok.

### Success Criteria:

#### Automated Verification:

- Nowe i zmienione testy przechodzą: `cd szczur-radar-api && ./mvnw -B test`
- Pełna bramka: `cd szczur-radar-api && ./mvnw -B verify`

#### Manual Verification:

- Lokalnie `migrate` z `LLM_TIMEOUT=120` pokazuje blok `APPLICATION FAILED TO START` bez stack trace'u i kończy się kodem `2` (`echo $?`)
- Lokalnie serwer z `LLM_TIMEOUT=120` pokazuje ten sam blok i nie startuje (`java -jar target/szczur-radar-api.jar`, brak nasłuchu na porcie)

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie ręcznej części przez człowieka przed fazą 4.

---

## Phase 4: Dokumentacja i weryfikacja przed merge'em

### Overview

Dokumenty dla operatora i agentów opisują nową regułę. Weryfikacja w Railway PR env i odczyt produkcji przed merge'em.

### Changes Required:

#### 1. Dokumenty

**File**:
- `szczur-radar-api/README.md`: `:63` (`LLM_TIMEOUT`), `:65`, `:94` (`PIPELINE_STALL_TIMEOUT`), `:190` (`ACTIVATION_LINK_TTL`), sekcja komend operatora (kod `2`);
- `context/deployment/deploy-plan.md`: `:93` („Pre-deploy `migrate` fails”), `:95` („Rule for variables”), `:162`;
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` (`:125-130`);
- Javadoc `llm/LlmProviderProperties` (`:34-35`), `pipeline/PipelineStallSettings`, `auth/activation/ActivationSettings`;
- `REPOSITORY_GUIDELINES.md`: opis pakietu `settings`.

**Intent**: Operator i następny agent znajdują jedną regułę w jednym brzmieniu:

- wymagana jednostka;
- tylko zapis prosty;
- zakresy: `LLM_TIMEOUT` `10s`–`30m`, `ACTIVATION_LINK_TTL` `1m`–`7d`, `PIPELINE_STALL_TIMEOUT` `1ms`–`1d` (= `24h`; `pipeline/CLAUDE.md` i README zapisują `1d`, jak komunikat);
- blok `APPLICATION FAILED TO START` i kod wyjścia `2` przy złym ustawieniu.

**Contract**: `deploy-plan.md` jest edytowany w miejscu (to nie archiwum). „Rule for variables” mówi, że kod pilnuje teraz reguły dla trzech ustawień i że pre-deploy `migrate` zatrzyma deploy, a stara wersja dalej działa. W `REPOSITORY_GUIDELINES.md` lista pakietów dostaje `settings` (parser czasu, przycinanie, `InvalidSettingsException`; bez zależności od aplikacji).

#### 2. Weryfikacja w Railway PR env

**File**: komentarz w issue S-10 (wynik). Bez zmian w repo.

**Intent**: Potwierdzić na obrazie z Railway, że pre-deploy `migrate` zatrzymuje deploy czytelnym błędem, a poprawna wartość przechodzi.

**Contract**:
1. Na PR env (`szczur-radar-api`) ustaw `LLM_TIMEOUT=120`.
2. Logi pre-deploy pokazują blok `APPLICATION FAILED TO START` z „LLM_TIMEOUT needs a unit”, a deploy jest nieudany.
3. Usuń zmienną albo ustaw `300s`, a deploy przechodzi z `/api/ready` OK.

Przy następnym pushu job `railway-config` i tak usuwa `LLM_TIMEOUT` w PR env (`ci.yml:298-306`). Przed zmianą sprawdź, że w PR env żaden przebieg nie jest `RUNNING`.

#### 3. Odczyt produkcji przed merge'em

**File**: komentarz w issue S-10 (tylko nazwa zmiennej i wartość). Bez zmian w repo.

**Intent**: Nowa reguła nie może zatrzymać pre-deploy `migrate` na produkcji (ryzyko z roadmapy S-10, F4 w `cli-user-onboarding`).

**Contract**:
- Odczyt **tylko** wartości `LLM_TIMEOUT` (oraz `PIPELINE_STALL_TIMEOUT` i `ACTIVATION_LINK_TTL`, jeśli są ustawione) w `szczur-radar-api` na `production`, np. `railway variable list -s szczur-radar-api -e production --json | jq '{LLM_TIMEOUT, PIPELINE_STALL_TIMEOUT, ACTIVATION_LINK_TTL}'`. Nigdy pełna lista: zawiera sekrety.
- Każda wartość musi spełniać nową regułę. Udokumentowane `300s` ją spełnia.
- Gdyby nie spełniała, operator poprawia zmienną na produkcji przed merge'em, a agent nie merguje.

### Success Criteria:

#### Automated Verification:

- Pełna bramka CI na PR zielona (`backend`, `api-container-smoke`, `railway-config` i pozostałe joby `ci.yml`)
- Opis `LLM_TIMEOUT` w README podaje zakres `10s`–`30m` (`grep -n "10s" szczur-radar-api/README.md` trafia wiersz `LLM_TIMEOUT`)

#### Manual Verification:

- W Railway PR env `LLM_TIMEOUT=120` zatrzymuje pre-deploy `migrate` blokiem `APPLICATION FAILED TO START`, a po usunięciu zmiennej deploy przechodzi
- Odczyt produkcji: ustawienia czasu spełniają nową regułę, wynik w komentarzu issue S-10 (`LLM_TIMEOUT`, a jeśli ustawione, także `PIPELINE_STALL_TIMEOUT` i `ACTIVATION_LINK_TTL`)

**Implementation Note**: Po tej fazie agent przygotowuje PR do merge'a i zatrzymuje się; merge robi człowiek. Obserwacja pierwszego deployu produkcji po merge'u jest runbookiem w issue S-10, nie krokiem Progress (lekcja „Implementacja, przegląd i archiwizacja na jednej gałęzi…”).

---

## Testing Strategy

### Unit Tests:

- `DurationSettingTest`: wszystkie odrzucane klasy wartości (goła liczba, ISO-8601, ułamek, spacja, zła jednostka, przepełnienie), granice włącznie i tuż poza nimi, każda jednostka, format granic.
- Testy trzech ustawień: granice każdego ustawienia i ścieżka zgłoszenia (lista, `Result`, wyjątek z podpowiedzią o domyślnej).
- `InvalidSettingsFailureAnalyzerTest`: wyjątek wprost, owinięty, obcy.
- Komendy CLI: `USAGE` przy błędzie konfiguracji, `FAILED` przy innej awarii.

### Integration Tests:

- Test startu z `OutputCaptureExtension`: blok `APPLICATION FAILED TO START`, brak „Application run failed”, brak klucza.
- `./mvnw -B verify` z Testcontainers: `JpaSchemaIT` (wartości enumów w bazie), `RunWatchdogIT` (`5s`), `LinkCommandIT`, `CreateAdminCommandIT`.

### Manual Testing Steps:

1. Lokalnie `migrate` i serwer z `LLM_TIMEOUT=120`: blok bez stack trace'u, kod `2` dla `migrate`.
2. Lokalnie z `LLM_TIMEOUT=300s`: start i `migrate` przechodzą.
3. Railway PR env: `LLM_TIMEOUT=120` zatrzymuje pre-deploy, a po usunięciu zmiennej deploy przechodzi.
4. Odczyt produkcji przed merge'em.

## Performance Considerations

Brak: parsowanie raz przy starcie.

## Migration Notes

- **Bez migracji bazy.** Enumy zachowują nazwy stałych i `id()`.
- **Zmiana zachowania dla operatora:**
  - `LLM_TIMEOUT` bez jednostki, w ISO-8601 albo poniżej `10s` zatrzymuje start i pre-deploy `migrate`;
  - `ACTIVATION_LINK_TTL` i `PIPELINE_STALL_TIMEOUT` w ISO-8601 są teraz odrzucane;
  - komendy CLI przy złej konfiguracji kończą się kodem `2` zamiast `1`.

  Railway traktuje każdy niezerowy kod pre-deploy jako nieudany deploy, a stara wersja dalej działa.
- **Wycofanie:** revert commita faz 2–3. Faza 1 jest neutralna.

## References

- Research: `context/changes/llm-config-fail-fast/research.md`
- Roadmapa: `context/foundation/roadmap.md` (S-10, `:95,173-183`)
- Audyt: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (B1 `:72`, E `:149-180`)
- Odłożone F4: `context/archive/2026-10-12-cli-user-onboarding/reviews/impl-review.md:147-162`, `follow-ups/review-fixes.md:7-11`
- Wzorzec „needs a unit”: `auth/activation/ActivationSettings.java:137-156`, `pipeline/PipelineStallSettings.java:99-124`
- Wzorzec pakietu-liścia: `text/Whitespace.java`
- Boot 4.0.8: `SpringApplication.java:314-331,830-866`, `diagnostics/FailureAnalyzers.java:55-90`, `diagnostics/LoggingFailureAnalysisReporter.java:36-40`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Kontrakt LLM w llm/

#### Automated

- [x] 1.1 Pełny build i testy bez zmian zachowania: `cd szczur-radar-api && ./mvnw -B verify` — 6a2ee28
- [x] 1.2 W `src/main` i `src/test` nie ma importu `pl.szczurradar.api.config.LlmProviderProperties` (`grep -rn "config.LlmProviderProperties" szczur-radar-api/src` nic nie zwraca) — 6a2ee28
- [x] 1.3 `JpaSchemaIT` przechodzi z niezmienionymi wartościami `"openrouter"`, `"flex"`, `"low"` — 6a2ee28
- [x] 1.5 Poza `src` nie ma odwołań do starego miejsca (`grep -rn --exclude-dir=target "config/LlmProviderProperties\|config\.LlmProviderProperties" szczur-radar-api REPOSITORY_GUIDELINES.md` nic nie zwraca) — 6a2ee28

#### Manual

- [ ] 1.4 Przegląd diffu: tylko przenosiny i importy, bez zmiany logiki

### Phase 2: Jedna reguła czasu

#### Automated

- [x] 2.1 Testy jednostkowe parsera i ustawień przechodzą: `cd szczur-radar-api && ./mvnw -B test` — 0825b0f
- [x] 2.2 Pełna bramka: `cd szczur-radar-api && ./mvnw -B verify` — 0825b0f
- [x] 2.3 `DurationStyle.detectAndParse` nie występuje już w `src/main` (`grep -rn "detectAndParse" szczur-radar-api/src/main` nic nie zwraca) — 0825b0f
- [x] 2.4 `settings/` nie importuje nic z aplikacji (`grep -rn "import pl.szczurradar" szczur-radar-api/src/main/java/pl/szczurradar/api/settings` nic nie zwraca) — 0825b0f

#### Manual

- [ ] 2.5 Lokalnie `LLM_TIMEOUT=120` zatrzymuje `migrate` z „LLM_TIMEOUT needs a unit”, a `300s` przechodzi (`java -jar target/szczur-radar-api.jar migrate`, lokalna baza z `README.md`)

### Phase 3: Czytelny błąd startu

#### Automated

- [x] 3.1 Nowe i zmienione testy przechodzą: `cd szczur-radar-api && ./mvnw -B test` — 51245f0
- [x] 3.2 Pełna bramka: `cd szczur-radar-api && ./mvnw -B verify` — 51245f0

#### Manual

- [ ] 3.3 Lokalnie `migrate` z `LLM_TIMEOUT=120` pokazuje blok `APPLICATION FAILED TO START` bez stack trace'u i kończy się kodem `2` (`echo $?`)
- [ ] 3.4 Lokalnie serwer z `LLM_TIMEOUT=120` pokazuje ten sam blok i nie startuje (`java -jar target/szczur-radar-api.jar`, brak nasłuchu na porcie)

### Phase 4: Dokumentacja i weryfikacja przed merge'em

#### Automated

- [x] 4.1 Pełna bramka CI na PR zielona (`backend`, `api-container-smoke`, `railway-config` i pozostałe joby `ci.yml`) — b2f26af
- [x] 4.2 Opis `LLM_TIMEOUT` w README podaje zakres `10s`–`30m` (`grep -n "10s" szczur-radar-api/README.md` trafia wiersz `LLM_TIMEOUT`) — b2f26af

#### Manual

- [ ] 4.3 W Railway PR env `LLM_TIMEOUT=120` zatrzymuje pre-deploy `migrate` blokiem `APPLICATION FAILED TO START`, a po usunięciu zmiennej deploy przechodzi
- [x] 4.4 Odczyt produkcji: ustawienia czasu spełniają nową regułę, wynik w komentarzu issue S-10 (`LLM_TIMEOUT`, a jeśli ustawione, także `PIPELINE_STALL_TIMEOUT` i `ACTIVATION_LINK_TTL`) — #69 (issuecomment-6252645199)
