---
date: 2026-10-18T12:17:51+02:00
researcher: Claude (Opus 5.5) z kwmacioszek
git_commit: c7db08c164acd537c5a5e155800cc15c9a656a52
branch: feature/llm-config-fail-fast
repository: szczur-radar
topic: "S-10 llm-config-fail-fast: walidacja LLM_TIMEOUT przy starcie, jedna reguła dla ustawień czasu, enumy dostawcy poza config/"
tags: [research, codebase, config, llm, durations, cli, pipeline, auth-activation]
status: complete
last_updated: 2026-10-18
last_updated_by: Claude (Opus 5.5)
---

# Research: S-10 — zła konfiguracja LLM zatrzymuje start

**Date**: 2026-10-18T12:17:51+02:00
**Researcher**: Claude (Opus 5.5) z kwmacioszek
**Git Commit**: c7db08c164acd537c5a5e155800cc15c9a656a52
**Branch**: feature/llm-config-fail-fast
**Repository**: szczur-radar

Ścieżki kodu względem `szczur-radar-api/src/main/java/pl/szczurradar/api/`, testów względem `szczur-radar-api/src/test/java/pl/szczurradar/api/`, chyba że podano inaczej.

## Research Question

Pytanie wyprowadzone z roadmapy (`context/foundation/roadmap.md:95,173-183`, S-10) i audytu (B1, E w `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:72,149-180`), bo `change.md` nie ma opisu zamiaru:

1. Jak dziś jest walidowane `LLM_TIMEOUT` i co widzi operator, gdy konfiguracja LLM jest zła — przy starcie serwera i przy `migrate`?
2. Gdzie API samo parsuje ustawienia czasu, jakimi regułami i co musi spełnić jedna wspólna reguła?
3. Kto używa enumów `Provider` / `ServiceTier` / `ReasoningEffort` z `config/LlmProviderProperties` i co ogranicza ich przeniesienie?
4. Co trzeba sprawdzić przed merge'em, żeby nowa reguła nie zatrzymała produkcji?

## Summary

- **`LLM_TIMEOUT` przyjmuje gołą liczbę jako milisekundy.** `LlmProviderProperties.timeout` (`config/LlmProviderProperties.java:175-191`) woła `DurationStyle.detectAndParse` i odrzuca tylko wartości `> 30m` oraz `<= 0`; `120` przechodzi jako 120 ms. Pozostałe dwa ustawienia czasu, które API parsuje samo (`ACTIVATION_LINK_TTL`, `PIPELINE_STALL_TIMEOUT`), odrzucają gołą liczbę wyrażeniem `[+-]?\d+` (`auth/activation/ActivationSettings.java:137-141`, `pipeline/PipelineStallSettings.java:99-103`).
- **Walidacja już działa przy każdym starcie — brakuje tylko reguły.** `config/LlmConfigurationCheck` (`:14-19`, zarejestrowany w `src/main/resources/META-INF/spring.factories:1-2`) woła `LlmProviderProperties.from` na `ApplicationEnvironmentPreparedEvent`, czyli przed utworzeniem kontekstu: dla serwera, dla każdego kontekstu komendy CLI (w tym `migrate`) i dla testów. Nowa reguła w `from()` zatrzyma więc i serwer, i `migrate` bez nowego punktu wejścia.
- **Komunikat dla operatora nie jest dziś „czytelnym błędem startu”.** Repo nie ma `FailureAnalyzer` (jedyny wpis w `spring.factories` to ten listener). Według źródeł Spring Boot 4.0.8 przeczytanych przez pracownika (nieuruchamiane) operator widzi `ERROR … SpringApplication : Application run failed` ze stack trace'em, a treść `Invalid LLM configuration: …` jest w komunikacie wyjątku (`config/LlmProviderProperties.java:157-158`). `migrate` dopisuje po nim jedną linię `migrate failed: <message>` i zwraca `ExitCode.FAILED` (1) (`cli/MigrateCommand.java:44-51`), choć `ExitCode.USAGE` (2) opisuje „missing settings” jako błąd konfiguracji (`cli/ExitCode.java:19-23`).
- **Skutek gołej liczby w działaniu (wnioskowanie z kodu, niezmierzone):** serwer startuje, klient LLM jest leniwy (`llm/LlmConfiguration.java:25-29`), a pierwszy przebieg z postami woła `llm.check()` (`pipeline/assess/ConcurrentAssessmentDispatcher.java:124-130`). Przy 120 ms każda z 3 prób najpewniej kończy się time-outem, `AssessedBatch` dostaje `stoppedBy`, a przebieg kończy się `failed` bez zużycia prób postów. Powtarza się co noc, aż ktoś poprawi zmienną.
- **Jedna reguła obejmuje dokładnie 3 miejsca.** Przeszukanie `src/main` (`DurationStyle`, `Duration.parse`, `@Value`, `@ConfigurationProperties`, `getProperty`, `getenv`, `parseLong`) znalazło tylko te trzy miejsca, gdzie sami parsujemy czas ze zmiennej. `spring.session.timeout=30d` (`src/main/resources/application.properties:42`) wiąże Spring, a `Retry-After` (`llm/transport/TransportException.java:117-123`) to wartość protokołu — oba poza regułą.
- **Enumy dostawcy można przenieść bez zmiany bazy i JSON-a**, jeśli zostaną nazwy stałych i `id()`. Poza samym `config/` używa ich 16 plików w `src/main` i 20 w testach, wszystkie przez import zagnieżdżony, bez importów statycznych (wyjątek: `LlmProviderConfigTest` z tego samego pakietu pisze `LlmProviderProperties.X`). Wartości są w `CHECK` (`src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:170-174`). Precedens miejsca to `llm/Channel` i `llm/OutputMode`, enumy tego samego rodzaju już w korzeniu `llm/`.
- **Produkcja jest udokumentowana jako `LLM_TIMEOUT=300s`** (ostatni zapis 2026-10-15: `context/archive/2026-10-15-pipeline-run-history-dashboard/research.md:318`). Nikt nie sprawdził tego na żywo — przed merge'em trzeba odczytać zmienną z Railway.

## Detailed Findings

### 1. Ścieżka walidacji i co widzi operator

- **Kiedy działa walidacja**:
  - `LlmConfigurationCheck.onApplicationEvent` → `LlmProviderProperties.from(event.getEnvironment())` (`config/LlmConfigurationCheck.java:16-19`).
  - Javadoc (`:6-12`) mówi wprost: „Fails every start of the application context — server, operator commands and tests alike”.
  - Listener nie ma `@Order`, więc według pracownika (źródła Boot 4.0.8) działa po `EnvironmentPostProcessorApplicationListener` i `LoggingApplicationListener`. Profil `application-<provider>.properties` (`application.properties:53`) i logowanie są już wtedy gotowe.
- **Drugie wywołanie**: bean `llmProviderProperties(Environment)` w `llm/LlmConfiguration.java:20-23` woła `from()` jeszcze raz przy refresh. Gdy listener rzucił wyjątek, do tego nie dochodzi.
- **Serwer**: `SzczurRadarApiApplication.java:23-26` to zwykłe `SpringApplication.run`. Wyjątek z listenera kończy JVM kodem 1 po logu Boot „Application run failed” ze stack trace'em (źródła Boot, nieuruchamiane). Test `LlmProviderConfigTest` (helper `environment()`, `:217-239`) wycisza ten log przez `--logging.level.org.springframework.boot.SpringApplication=OFF`, co potwierdza, że log się pojawia.
- **`migrate`**:
  - Uruchamia pełny kontekst bez serwera WWW (`cli/MigrateCommand.java:54-58` → `SzczurRadarApiApplication.commandApplication()`, `:36-40`), więc potrzebuje `LLM_*` i je waliduje.
  - Na błąd łapie `RuntimeException` i wypisuje `migrate failed: <message>` z `ExitCode.FAILED` (`cli/MigrateCommand.java:44-51`).
  - Tak samo działają `create-admin` (`cli/CreateAdminCommand.java:76-82`) oraz `create-user` / `reset-password` (`cli/LinkCommand.java:109-115`). Ich kontrole przed kontekstem (`cli/CommandChecks`, `ActivationSettings`) nie obejmują `LLM_*` (pracownik; nie sprawdzałem tego osobno).
  - Precedens: PR env #34 padł na `migrate` z tego powodu (`context/deployment/deploy-plan.md:50`, `context/archive/2026-10-04-api-docker-image/change.md:22`).
- **Kod wyjścia**: `ExitCode.USAGE(2)` ma opis „A usage or configuration error, found before any database work: … missing settings” (`cli/ExitCode.java:19-23`). Błąd konfiguracji LLM wychodzi dziś jako `FAILED(1)`. Zmiana na 2 jest wyborem planu, nie wymogiem roadmapy.
- **Brak testu na wyjście operatora**: `MigrateCommandTest` (`:37-38`, według pracownika) obejmuje tylko nieudaną migrację Flyway. Żaden test nie sprawdza, co serwer albo CLI wypisuje przy złej konfiguracji LLM.

### 2. Gdzie trafia `timeout()` i co z niego wynika

- **Gemini**: `HttpOptions.timeout(Math.toIntExact(timeout.toMillis()))`, `attempts(1)` (`llm/gemini/GeminiClient.java:66-68`). Stąd górna granica 30m (`config/LlmProviderProperties.java:58-59`).
- **OpenRouter**: klient OkHttp `:119`, klienci openai-java sync/async `:125,:131` (`maxRetries(0)`), pole `:139`, `OpenAiChatOptions.timeout` `:162` (`llm/openrouter/OpenRouterClient.java`).
- **DeepSeek**: connect timeout `min(timeout, 30s)` `:77,:97-100`, read timeout `:79` (`llm/deepseek/DeepSeekClient.java`).
- **Najdłuższe wywołanie**: `TransportRetry.maxCallDuration(timeout, tier)` = `3×timeout + 2×60s`, przy flex razy 2 (`llm/transport/TransportRetry.java:66-70`).
- **Domyślny `PIPELINE_STALL_TIMEOUT` zależy od `LLM_TIMEOUT`**: `minimum = maxCallDuration + 5m`, a `default = max(minimum, 15m)` (`pipeline/PipelineStallSettings.java:61-62`):
  - przy 120s wychodzi 13m, więc 15m;
  - przy 300s z flex wychodzi 39m (`PipelineStallSettingsTest.java:35-45`);
  - przy 120 ms wychodzi ~7m, więc 15m.

  Jawna wartość poniżej minimum daje tylko WARN (`:69-75`).
- **Testy klientów budują rekord wprost z krótkimi czasami i omijają `from()`**: `GeminiClientTest.java:187`, `DeepSeekClientTest.java:129` i `OpenRouterClientTest.java:199` (300 ms), `OpenRouterClientTest.java:518` (5s). Dolna granica w `from()` ich nie dotknie. Dolna granica w konstruktorze kompaktowym rekordu by je zepsuła.

### 3. Trzy kopie parsowania czasu

Wszystkie wołają `DurationStyle.detectAndParse` bez wskazania stylu. Według semantyki `DurationStyle` przyjmują więc też ISO-8601 (`PT5M`), czego żaden test nie sprawdza. Każda łapie `IllegalArgumentException | ArithmeticException`.

| Ustawienie | Miejsce | Goła liczba | Dolna granica | Górna granica | Domyślnie | Jak zgłasza błąd |
|---|---|---|---|---|---|---|
| `LLM_TIMEOUT` | `config/LlmProviderProperties.java:175-191` | przyjęta jako ms | `> 0` | `30m` | `120s` (`:56`) | dopisuje do listy problemów, potem jeden `IllegalStateException` dla wszystkich `LLM_*` (`:156-159`) |
| `ACTIVATION_LINK_TTL` | `auth/activation/ActivationSettings.java:137-156` | odrzucona (`:138`) | `1m` włącznie | `7d` włącznie | `12h` (`:37`) | dopisuje do listy, zwraca `Result.Invalid` (`:58-84`); CLI wypisuje „Activation links are not configured: …” (`cli/LinkCommand.java:130-138`) |
| `PIPELINE_STALL_TIMEOUT` | `pipeline/PipelineStallSettings.java:99-124` | odrzucona (`:100`) | `> 0` | `24h` | obliczana (`:61-62`) | rzuca od razu `IllegalStateException("Invalid PIPELINE_STALL_TIMEOUT: …")` z podpowiedzią „or unset for the default” |

Pozostałe różnice:

- **Typ wejścia**: `PropertyResolver` w A i C, `Function<String, @Nullable String>` (`System::getenv`) w B (`ActivationSettings.java:75`). B jest czytane przez komendy CLI przed startem kontekstu Spring.
- **Przycinanie**: `trimmed` w A (`:193-195`) i B (`:158-160`), inline w C (`:63-64`). Ta sama funkcja jest jeszcze w `sources/wykop/WykopConfiguration.java:41-44` i `internal/PipelineTriggerToken.java:39-40`, ale tam nie dotyczy czasu.
- **Testy przypinają treść komunikatów**:
  - `LlmProviderConfigTest.java:176-197`: „must be a positive duration such as 120s or 5m”, „must be at most 30m”.
  - `ActivationSettingsTest.java:42-74`: „needs a unit … milliseconds”, „must be from 1m to 7d”, „must be a duration”.
  - `PipelineStallSettingsTest.java:74-92`: prefiks „Invalid PIPELINE_STALL_TIMEOUT: ”.
  - `cli/CreateUserCommandTest.java:111-120` i `cli/ResetPasswordCommandTest.java:100-112`.
- **Wartości poniżej sekundy**: `RunWatchdogTest.java:37-38,103-104` używa `PIPELINE_STALL_TIMEOUT` `500ms`, a `RunWatchdogIT.java:66` `5s`. Wspólna reguła z dolną granicą dla stall timeoutu zepsułaby te testy. Granice muszą więc być parametrem każdego ustawienia, nie stałą reguły.
- **Brak testu na gołą liczbę, wartość ujemną, ISO-8601 i spacje wokół wartości** dla `LLM_TIMEOUT` (`LlmProviderConfigTest.java:145-197`).

Co musi spełnić wspólny parser (wynika z trzech wywołujących):

- przyjmuje `String` (ustawienie B nie ma `PropertyResolver`);
- zwraca problem i nie decyduje, jak go zgłosić (A zbiera, B zwraca `Result`, C rzuca);
- granice i nazwa ustawienia są parametrami;
- domyślna wartość zostaje po stronie wywołującego (C ją oblicza).

Miejsce dla parsera:

- Dziś nie ma żadnego pomocnika do parsowania ustawień. `config/ContractValueBinding` to `@ControllerAdvice` od parametrów żądań, nie od zmiennych środowiska.
- Kierunki `pipeline → config` i `auth/activation → config` już istnieją: `pipeline/PipelineStallSettings.java:11`; `ActivationController.java:13-14` importuje `config.ApiError` / `ErrorCode`.
- `ActivationSettings` sam nie importuje dziś nic z `pl.szczurradar`.
- Wzorzec neutralnego pakietu-liścia to `text/Whitespace` (współdzielony przez `agents`, `llm`, `sources`).

### 4. Enumy `Provider` / `ServiceTier` / `ReasoningEffort`

- **Deklaracje**: `config/LlmProviderProperties.java:65-102`, każdy z `@JsonValue id()` (nazwa małymi literami). `Provider.fromId` jest package-private i wołane tylko w `:131`.
- **Użytkownicy w `src/main`** (import zagnieżdżony `import pl.szczurradar.api.config.LlmProviderProperties.X;`, bez importów statycznych):
  - **Encja**: `posts/Assessment.java:26-28` (pola `:98,:107,:113`). To krawędź encja → config, którą audyt i mapa nazywają złamaną granicą (`context/map/repo-map.md:102,127`).
  - **JSON**: `posts/api/FailedPostController.java:22` (`LastAttempt.provider`, `PostsApiIT.java:382` sprawdza `"gemini"`) i `llm/api/LlmCheckController.java:10` (`LlmCheck.provider`, `:51`).
  - **Konwertery JPA**: `persistence/converters/{Provider,ServiceTier,ReasoningEffort}Converter.java:5`, package-private, mapują przez `X::id`.
  - **`agents/assessor`**: `Provenance.java:10-12`, `Assessor.java:22`.
  - **`llm/`**:
    - korzeń: `LlmResponse.java:5-7`, `LlmCallException.java:3`, `LlmConfigurationException.java:3`;
    - podpakiety: `deepseek/DeepSeekClient.java:22-23`, `gemini/GeminiClient.java:19-20`, `openrouter/OpenRouterClient.java:29-31`, `transport/ProviderSchemas.java:12`, `transport/TransportRetry.java:8`.
  - **`pipeline`**: nie importuje enumów. Bierze cały rekord (`PipelineStallSettings.java:11`, `RunWatchdog.java:18,59`).
- **Testy** (import zagnieżdżony, ta sama zmiana importu):
  - `JpaSchemaIT.java:26-28,196-199` — przypina zapisane wartości `"openrouter"`, `"flex"`, `"low"`;
  - `agents/assessor/{AssessorTest,ProvenanceTest}`;
  - `config/LlmProviderConfigTest` (kwalifikowane `LlmProviderProperties.X`);
  - `llm/**` — 6 klas;
  - `measurement/Recording.java:25-27,82-84`;
  - `measurement/Replay.java:20-21`, w tym `:118` `ServiceTier.valueOf(…toUpperCase())`, który zależy od nazw stałych, nie od `id()`;
  - `pipeline/**` — 6 klas;
  - `posts/{PostRepositoryIT,PostSeeds}`.
- **Ograniczenia przeniesienia**:
  - Wartości są w `CHECK`: `ck_assessments_provider` (`V3__create_pipeline_and_posts.sql:170`), `ck_assessments_service_tier` (`:172`), `ck_assessments_reasoning_effort` (`:173-174`).
  - `Provider.id()` musi być równy nazwie profilu `application-<id>.properties` (`application.properties:53`).
  - `llm/CLAUDE.md` mówi, że te stałe są append-only, i każe dodać nowego dostawcę jako „A `Provider` constant in `config/LlmProviderProperties`”. Ten tekst trzeba poprawić razem z przeniesieniem.
  - `llm/CLAUDE.md` nie zabrania `llm → config`; wprost wskazuje `config/LlmProviderProperties` jako źródło ustawień.
- **Brak testu architektury**: w `pom.xml` nie ma ArchUnit, Spring Modulith ani kontroli importów. Przeniesienia pilnuje tylko kompilator, a wartości w bazie — `JpaSchemaIT`.

### 5. Produkcja, PR envy, CI i dokumenty

- **Produkcja**:
  - Udokumentowane `LLM_TIMEOUT=300s`: `context/deployment/deploy-plan.md:153,162,219,735`; `context/archive/2026-10-10-openrouter-in-body-errors/plan.md:26,142`; ostatnio `context/archive/2026-10-15-pipeline-run-history-dashboard/research.md:318` (z `LLM_SERVICE_TIER=flex`).
  - Incydent: `deploy-plan.md:153` — „was set as `120`, i.e. 120 ms”.
  - Reguła dla operatora: `deploy-plan.md:95` — „Durations always with a unit (`300s`, `5m`): Spring reads a bare number as **milliseconds**”.
- **PR envy**: krok „Ensure PR-env LLM settings” kasuje `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT` i `LLM_TIMEOUT` (`.github/workflows/ci.yml:298-306`), więc PR env działa na domyślnym `120s`. `.railway/railway.ts:151` ma `LLM_TIMEOUT: preserve()`.
- **CI**: joby backendu i `api-container-smoke` ustawiają tylko trzy wymagane `LLM_*` (`ci.yml:32-34,49-51,133-135,155-161`). Żaden test ani plik zasobów nie ustawia gołej liczby (`src/test/resources/config/application.properties` ma tylko `cleanup-cron`).
- **Wniosek**: ostrzejsza reguła w `from()` nie psuje żadnej wartości w CI ani w testach (pracownik; wynik przeszukania, bez uruchomienia). Jedynym ryzykiem jest żywa wartość produkcji.
- **Dokumenty do aktualizacji przy zmianie reguły**:
  - `szczur-radar-api/README.md:63` (opis `LLM_TIMEOUT` bez wzmianki o odrzuceniu gołej liczby; porównaj `:94`, `:190`);
  - `context/deployment/deploy-plan.md:93,95,162`;
  - Javadoc `config/LlmProviderProperties.java:34-35,58`;
  - przy przeniesieniu enumów: `llm/CLAUDE.md` (krok „adding a provider”) i opis pakietów w `REPOSITORY_GUIDELINES.md` (`config` / `llm`).
  - `context/foundation/tech-stack-api.md:35` wymienia tylko trzy `LLM_*` — bez zmian.

## Code References

- `config/LlmProviderProperties.java:122-161` — `from()`: zbiera problemy wszystkich `LLM_*`, rzuca jeden `IllegalStateException`.
- `config/LlmProviderProperties.java:175-191` — parser `LLM_TIMEOUT` bez odrzucania gołej liczby (B1).
- `config/LlmProviderProperties.java:65-102` — enumy `Provider` / `ServiceTier` / `ReasoningEffort`.
- `config/LlmConfigurationCheck.java:14-19` + `src/main/resources/META-INF/spring.factories:1-2` — walidacja przed kontekstem dla serwera, CLI i testów.
- `llm/LlmConfiguration.java:20-29` — drugie `from()` i leniwy `LlmClient`.
- `cli/MigrateCommand.java:44-58` — `migrate failed: …`, `ExitCode.FAILED`.
- `cli/ExitCode.java:19-23` — `USAGE(2)` dla błędów konfiguracji.
- `auth/activation/ActivationSettings.java:137-160` — wzorzec „needs a unit”, granice 1m–7d.
- `pipeline/PipelineStallSettings.java:60-124` — domyślna wartość zależna od `LLM_TIMEOUT`, wzorzec „needs a unit”, granica 24h.
- `llm/transport/TransportRetry.java:66-70` — `maxCallDuration`.
- `pipeline/assess/ConcurrentAssessmentDispatcher.java:124-130` — `llm.check()` przed oceną; tu kończy się przebieg przy zbyt krótkim czasie.
- `src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:170-174` — `CHECK` dla id dostawcy, poziomu i wysiłku.
- `src/test/java/pl/szczurradar/api/config/LlmProviderConfigTest.java:145-197,217-239` — obecne przypadki `LLM_TIMEOUT` i helper z prawdziwym `SpringApplication`.

## Architecture Insights

- Walidacja „przed kontekstem” przez `ApplicationListener` z `spring.factories` to już przyjęty wzorzec fail-fast tego repo. S-10 rozszerza regułę, a nie mechanizm.
- Trzy ustawienia czasu mają trzy style zgłaszania błędów (lista, `Result`, wyjątek) i dwa typy wejścia. Wspólny parser powinien być czystą funkcją `String → Duration | problem` z granicami w parametrach i zostawić zgłaszanie wywołującym. Wtedy nie wymusza ujednolicenia `Result` vs wyjątek.
- `llm/` ma już w korzeniu enumy przechowywane w bazie (`Channel`, `OutputMode`). Przeniesienie trzech enumów obok nich zgadza się z zasadą z `llm/CLAUDE.md`, że korzeń `llm/` to API importowane przez resztę aplikacji. Nie wprowadza też nowej zależności: `posts`, `persistence`, `agents` i `pipeline` już importują `llm` albo `config`.
- `ActivationSettings` celowo działa bez Springa (`System::getenv`) dla komend CLI. Parser w `config/` dałby mu pierwszą zależność od `config`, a parser w neutralnym pakiecie-liściu (jak `text/`) nie.

## Historical Context (from prior changes)

- `context/archive/2026-10-12-cli-user-onboarding/reviews/impl-review.md:147-162` (F4) — opisuje dwie kopie parsera z różnymi regułami i rekomenduje `config/DurationSetting.parse(name, text, min, max, problems)` z wymaganą jednostką. Werdykt dziś: **supported**, z wyjątkiem liczby kopii — są już trzy (S-06 dodało `PipelineStallSettings`, audyt `research.md:191`).
- `context/archive/2026-10-12-cli-user-onboarding/follow-ups/review-fixes.md:7-11` — F4 odłożone decyzją użytkownika z 2026-10-12 („not in S-07”) z warunkiem: przed merge'em odczytać `railway variable list -s szczur-radar-api -e production`. Werdykt: **supported**, nadal otwarte (niezaznaczone).
- `context/archive/2026-10-12-cli-user-onboarding/plan.md:49,106` — S-07 kazało `ACTIVATION_LINK_TTL` odrzucać gołą liczbę właśnie z powodu incydentu `LLM_TIMEOUT`. Werdykt: **supported** (`ActivationSettings.java:138`).
- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:72` (B1):
  - **supported** — „bare number = ms, tylko `>30m` i `<=0`”;
  - **supported** — skutek „przebieg `failed` co noc”, jako wnioskowanie z kodu przez `ConcurrentAssessmentDispatcher.java:124-130`;
  - **partial** — „brak przypadku w `LlmProviderConfigTest`”: brakuje przypadku gołej liczby, ale `0s` i `> 30m` są pokryte.
- Ten sam audyt (`:174`, E, „złamane granice”): encja `posts/Assessment` → enumy w `config`. Werdykt: **supported** (`posts/Assessment.java:26-28`).
- `context/deployment/deploy-plan.md:153` — incydent 2026-10-06 (`LLM_TIMEOUT=120` = 120 ms) i poprawka na `300s`. Wpis jest jedynym pierwotnym zapisem; archiwum tylko go cytuje.

## Related Research

- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` — audyt po MVP (B1, E, F).
- `context/archive/2026-10-12-cli-user-onboarding/research.md` — wprowadzenie `ACTIVATION_LINK_TTL` z wymaganą jednostką.
- `context/archive/2026-10-15-pipeline-run-history-dashboard/research.md` — `PIPELINE_STALL_TIMEOUT` i jego zależność od `LLM_TIMEOUT`.

## Open Questions

1. **Żywa wartość `LLM_TIMEOUT` na produkcji** — udokumentowana jako `300s` (2026-10-15), niesprawdzona w tej sesji. Trzeba ją odczytać przed merge'em (`railway variable list -s szczur-radar-api -e production`, tylko tę nazwę). Goła liczba tam zatrzymałaby pre-deploy `migrate`, a deploy by stanął na starej wersji. Owner: operator / implementacja przed merge'em.
2. **Ile to „absurdalnie krótko”?** Roadmapa nie podaje progu. Dowody:
   - testy klientów używają 300 ms, ale omijają `from()`;
   - produkcja ma `300s`, domyślnie jest `120s`;
   - jedna próba Gemini/OpenRouter z oceną trwa sekundy do minut (w tej sesji nie zmierzono).

   Wybór dolnej granicy (np. 10s albo 30s) należy do planu.
3. **Czy przyjmować ISO-8601 (`PT5M`)?** Dziś akceptują je wszystkie trzy parsery i nic tego nie testuje. Wspólna reguła powinna to rozstrzygnąć jawnie.
4. **Jak ma wyglądać „czytelny błąd startu”?** Czy wystarcza dzisiejszy komunikat w stack trace (serwer) i linia `migrate failed: …`, czy potrzeba `FailureAnalyzer` (blok „APPLICATION FAILED TO START” bez stack trace'u)? I czy błąd konfiguracji LLM w CLI ma dawać `USAGE` (2) zamiast `FAILED` (1)? Dokładnego wyjścia konsoli nie przechwycono — opis pochodzi z kodu Boot 4.0.8.
5. **Gdzie położyć wspólny parser?** `config/` (rekomendacja F4) czy neutralny pakiet-liść bez zależności od aplikacji (jak `text/`), żeby `ActivationSettings` nie zyskał zależności od `config`?
6. **Czy ujednolicić treść komunikatów trzech ustawień?** Testy przypinają obecne podciągi (sekcja 3), więc ujednolicenie oznacza zmianę asercji.
