# Backend w Javie (Spring Boot) zamiast Pythona/FastAPI — Implementation Plan

## Overview

Roadmapa F-03 (`java-api-rewrite`): katalog `szczur-radar-api/` zostaje zastąpiony aplikacją Spring Boot 4 na Javie 25 (Maven), która zachowuje zachowanie S-01 widoczne dla SPA i Railway: te same trasy `/api/*`, kształty odpowiedzi, statusy, ciasteczko sesji, CLI do utworzenia pierwszego admina i migracje przed startem nowej wersji. Własny JWT z S-01 zostaje zastąpiony standardową sesją Spring Security przechowywaną w Postgresie (Spring Session JDBC). Dochodzi szkielet Embabel (framework agentów od twórców Springa) pod F-02. Zmiana kończy się przełączeniem produkcji na czystą bazę.

Powód: autor zna Javę i może rzetelnie oceniać kod generowany przez agenta; w Pythonie nie może (`tech-stack-api.md`: `can_judge_agent: false`).

## Current State Analysis

Pełne ustalenia: `context/changes/java-api-rewrite/research.md` (sekcje 1–10, Follow-up, Follow-up 2). Najważniejsze:

- Backend to 15 plików `.py` aplikacji/migracji i 10 plików testów (~1200 linii) opisujących zachowanie: 5 tras (`app/routes.py:18-35`, `app/auth/routes.py:68-123`), filtr Origin (`app/security.py:18-34`), limiter prób logowania (`app/auth/rate_limit.py`), argon2 z wyrównaniem czasu (`app/auth/passwords.py`), CLI `create-admin` z advisory lockiem (`app/cli.py`), tabela `users` (`migrations/versions/0001_create_users.py`).
- SPA zależy od: ścieżek `/api/auth/{login,logout,me}`, kształtu `{id, email, role}`, statusów 401/429, pola `detail` jako stringa i nagłówka `Retry-After` (`szczur-radar-web/src/api/auth.ts:4-27`, `src/api/client.ts:37-81`, `src/views/LoginView.vue:18-23`, `src/auth/session.ts:22`). Nie czyta 422 ani nazwy ciasteczka (HttpOnly).
- Railway (`.railway/railway.ts:56-85`): `rootDirectory: "szczur-radar-api"`, start i pre-deploy przez `uv`, healthcheck `/api/health`, port 8000, 1 replika, `sleepApplication: true`, `JWT_SECRET: preserve()`. CI (`.github/workflows/ci.yml:12-48`): job `backend` na `uv` + Postgres service + `REQUIRE_DB_TESTS=1`; krok „Ensure a PR-env JWT_SECRET” (`ci.yml:159-180`).
- Produkcja: tabela `users` z jednym adminem i `alembic_version` = `0001_create_users` (`context/deployment/deploy-plan.md:24-26`). Użytkownik zgodził się na wyczyszczenie bazy.
- Spring Security + Spring Session JDBC pokrywają z pudełka: argon2, wyszukanie użytkownika, wyrównanie czasu dla nieznanego e-maila, sesję w ciasteczku, ochronę przed session fixation, logout, deny-by-default dla tras, role i unieważnianie sesji użytkownika. Brakuje: logowania JSON, limitu prób, odpowiedzi JSON 401/403, CLI (research, Follow-up 2).

## Desired End State

- `szczur-radar-api/` zawiera wyłącznie projekt Maven (Spring Boot 4, Java 25) z pakietem bazowym `pl.szczurradar.api`; Python, `uv.lock`, Alembic i `.python-version` są usunięte.
- Przez publiczny adres produkcji: `/api/health` → 200 `{"status":"ok"}`; `/api/ready` → 200 `{"status":"ready"}`; logowanie w SPA, ochrona tras, wylogowanie, 403 dla obcego `Origin`, 429 z `Retry-After` po przekroczeniu limitu — działają jak w S-01.
- Sesja przeżywa uśpienie i restart API (jest w Postgresie).
- Admin produkcyjny utworzony na nowo przez `java -jar … create-admin`.
- Aplikacja wybiera jednego dostawcę przez `LLM_API_PROVIDER` (`gemini`, `deepseek`, `openrouter`), model przez `LLM_MODEL` i startuje z prawdziwym `LLM_API_KEY` tego dostawcy dostarczonym jako sekret środowiska. Embabel rejestruje wybrany model i szkieletowego agenta; start i szkielet nie wykonują wywołań LLM.
- CI: job `backend` uruchamia `./mvnw -B verify` (testy jednostkowe + integracyjne z Testcontainers) i jest zielony; dokumentacja repo opisuje stack Javy.

Weryfikacja: kryteria sukcesu faz 1–4, w tym ręczna weryfikacja w Railway PR env i na produkcji.

### Key Discoveries:

- Kontrakt odpowiedzi do zachowania: `{"detail": "invalid_credentials" | "not_authenticated" | "forbidden" | "too_many_attempts" | "cross_origin"}` (`app/auth/routes.py:96-98,80-84`, `dependencies.py:20-23,43-46`, `security.py:33`).
- Ciasteczko: `HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=2592000` (`tests/test_auth.py:45-47`); `Secure` wyłączalne tylko przez `COOKIE_SECURE` ∈ {`0`,`false`,`no`} (`app/auth/routes.py:50-56`).
- Limiter: 5 porażek / 15 min na znormalizowany e-mail, 20 / 15 min na IP; sukces zeruje licznik e-maila (nie IP); IP z `X-Real-IP`, inaczej adres peera (`app/auth/rate_limit.py:19-21,133-134,158-166`).
- Railpack: Java wykrywana po `pom.xml`, JDK z `RAILPACK_JDK_VERSION` (domyślnie 21), Maven przez `./mvnw`, jeśli jest wrapper; build z `-DskipTests` (railpack.com/languages/java).
- Railway Postgres udostępnia `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` (docs Railway).
- Spring Boot 4: bez modułu `spring-boot-session` ustawienie `same-site` ciasteczka jest ignorowane (spring-session issue #3622).
- Embabel 1.5.x wymaga Spring Boot 4 / Spring AI 2; bez zarejestrowanego modelu potrafi nie wystartować („Default LLM … not found in available models: []”, issue #1358). Decyzja z triage F5: prawdziwy klucz wybranego dostawcy przez `LLM_API_KEY`, bez klucza zastępczego i bez wyłączania platformy.
- Java 25 widzi limit CPU kontenera (może być `availableProcessors() = 1`); rdzeń Embabel używa własnego executora i nie jest dotknięty (issue #1454), ale własny kod równoległy musi podawać executor.

## What We're NOT Doing

- Logiki F-02 (kontrakt oceny V2, walidator, prompty, pomiar na zestawie wzorcowym) — tylko szkielet Embabel bez wywołań LLM.
- Przenoszenia danych z bazy Pythona — baza produkcyjna jest czyszczona (decyzja użytkownika).
- Zgodności wstecznej sesji/JWT ani hashy haseł z Pythona.
- Powrotu do backendu Python po przełączeniu — decyzja użytkownika w triage F4; ścieżką awaryjną jest naprawa i ponowne wdrożenie Javy.
- Zmian w SPA (`szczur-radar-web/`) — kontrakt API jest zachowany; CSRF zostaje na filtrze Origin + `SameSite=Strict`, bez `csrf.spa()`.
- Sztywnego 30-dniowego limitu sesji i automatycznego wygasania krótkich sesji (FR-029, nice-to-have, odłożone) — sesja wygasa po 30 dniach bezczynności.
- Kolumny `token_version` — unieważnianie sesji przejmuje Spring Session (S-07 użyje `findByPrincipalName`).
- Komend CLI z S-07 (tworzenie kont `user`, linki aktywacyjne, recovery).
- AOT cache / CDS dla szybszego startu JVM — najpierw pomiar zimnego startu w PR env.
- Narzędzia do cofania migracji — Flyway Community bez `undo`; obowiązuje fix-forward + PITR.

## Implementation Approach

Zastąpienie w miejscu na jednej gałęzi (`feature/java-api-rewrite`) z jednym PR i weryfikacją w Railway PR env (lessons.md: jedna gałąź + jeden PR). Faza 1 od razu usuwa Pythona i stawia szkielet Javy z sondami, bazą i CI, żeby każda kolejna faza kończyła się zielonym `./mvnw verify`. Faza 2 dokłada bezpieczeństwo z testami przeniesionymi z Pythona. Faza 3 domyka CLI i szkielet Embabel. Faza 4 aktualizuje dokumentację, weryfikuje całość w PR env i opisuje przełączenie produkcji, które wykonuje operator (czyszczenie bazy, merge przez człowieka).

Konwencje kodu i testów:

- Pakiety według funkcji: `pl.szczurradar.api.{health, user, auth, cli, config, agents}` (później `llm`, `sources` — zgodnie z dzisiejszym podziałem `app/` i lekcją o niemieszaniu warstw).
- Testy jednostkowe `*Test.java` (Surefire, bez Springa i Dockera): JUnit 6, AssertJ, Mockito. Testy integracyjne `*IT.java` (Failsafe): `@SpringBootTest` + `MockMvcTester` + `spring-security-test` + Testcontainers Postgres 18 z `@ServiceConnection`. Bez `disabledWithoutDocker` — brak Dockera ma wywalić `verify`, nie pominąć testy (odpowiednik `REQUIRE_DB_TESTS=1`).
- Prawdziwe odpowiedzi usług zewnętrznych (Wykop, dostawcy LLM) jako fixture'y JSON w `src/test/resources/fixtures/<usługa>/` — konwencja dla F-02/S-02, opisana w README API.

## Critical Implementation Details

**Timing & lifecycle.** Pierwsza migracja Flyway (`create table users`) nie przejdzie na produkcji, dopóki leży tam tabela z Pythona — dlatego operator czyści schemat **przed** merge (faza 4). Railway „Wait for CI” sprawia, że job `railway-config` stosuje nowy start/pre-deploy przed deployem kodu; apply sam wyzwala dodatkowy deploy API (`deploy-plan.md:24,29`) — oba deploye powinny dotyczyć nowego commitu; obserwować kolejność w logach.

**Timing & lifecycle (logowanie).** Konto z `password_hash IS NULL` nie może być oznaczone jako `disabled` w `UserDetails` — `DaoAuthenticationProvider` sprawdza `disabled` przed hasłem, więc odpowiedź byłaby szybsza i ujawniałaby istnienie konta. Zamiast tego takie konto dostaje stały hash-atrapę, który nigdy nie pasuje; każdy błąd uwierzytelnienia mapuje się na to samo 401 `invalid_credentials`.

**State sequencing (sesja).** Nazwa principala w sesji = UUID użytkownika (36 znaków), nie e-mail — kolumna `SPRING_SESSION.PRINCIPAL_NAME` ma `VARCHAR(100)`, a e-mail do 320 znaków. Od Spring Security 6 kontroler logowania musi jawnie zapisać kontekst przez `SecurityContextRepository.saveContext(...)`, inaczej sesja nie przetrwa do następnego żądania.

**Timing & lifecycle (limiter).** Port algorytmu z Pythona (`rate_limit.py:43-134`): ruchome okno timestampów zakończonych porażek oraz osobne liczniki trwających prób dla e-maila i IP. `reserve` atomowo sprawdza historię porażek i trwające rezerwacje dla obu limitów **przed** sprawdzeniem hasła; dopiero gdy oba limity pozwalają, zwiększa oba liczniki rezerwacji. `finish` zawsze zwalnia własną rezerwację w obu licznikach: porażka dopisuje timestamp zakończenia do obu historii; sukces usuwa tylko historię porażek e-maila, zachowując historię IP i pozostałe trwające rezerwacje; przerwanie nie zmienia historii porażek. Sprawdzenie i zmiana stanu obu limitów odbywają się pod wspólną blokadą. Inaczej równoległe próby ominą limit (`tests/test_rate_limit.py:71-148`).

## Faza 1: Szkielet Java i budowanie

### Overview

Usunięcie Pythona, projekt Maven z sondami, połączeniem z bazą, migracjami Flyway, komendą `migrate`, testami na Testcontainers oraz przestawienie CI i Railway na Javę. Po tej fazie PR env może już wystartować API (bez logowania).

### Changes Required:

#### 1. Usunięcie Pythona

**File**: `szczur-radar-api/` (`main.py`, `app/`, `migrations/`, `alembic.ini`, `pyproject.toml`, `uv.lock`, `.python-version`, `tests/`, `scripts/` oraz nieśledzone pozostałości `__pycache__`, `.venv`, `.pytest_cache`)

**Intent**: Railpack musi jednoznacznie wykryć Javę (`pom.xml`), a w katalogu nie może zostać nic z Pythona.

**Contract**: Po fazie `git ls-files szczur-radar-api` nie zawiera plików `.py`, `pyproject.toml`, `uv.lock`, `.python-version`, `alembic.ini`. Zachowanie z testów Pythona jest przenoszone w fazach 1–3 (lista w Testing Strategy).

#### 2. Projekt Maven

**File**: `szczur-radar-api/pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `src/main/java/pl/szczurradar/api/SzczurRadarApiApplication.java`, `src/main/resources/application.properties`

**Intent**: Szkielet aplikacji Spring Boot 4 na Javie 25 z wrapperem Mavena (Railpack i CI używają `./mvnw`).

**Contract**: parent `spring-boot-starter-parent` 4.0.x; `<java.version>25</java.version>`; `<finalName>szczur-radar-api</finalName>` (stała ścieżka `target/szczur-radar-api.jar`); zależności: `spring-boot-starter-web`, `spring-boot-starter-jdbc` (lub `-data-jdbc`), `spring-boot-starter-validation`, `spring-boot-starter-flyway` (lub `flyway-core` + `flyway-database-postgresql`), `postgresql`; test: `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`. Wtyczki Surefire (`*Test`) i Failsafe (`*IT`, cele `integration-test` + `verify`). `server.port=${PORT:8000}`; `spring.flyway.enabled=false` w trybie serwera (migracje wyłącznie przez komendę `migrate`).

#### 3. Połączenie z bazą

**File**: `src/main/resources/application.properties`

**Intent**: JDBC z rozbitych zmiennych Railway zamiast parsowania `DATABASE_URL`.

**Contract**: `spring.datasource.url=jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}`, `spring.datasource.username=${PGUSER}`, `spring.datasource.password=${PGPASSWORD}`. Import aplikacji i `/api/health` nie mogą wymagać działającej bazy przy starcie (pula łączy leniwa — Hikari nie łączy się przy starcie domyślnie; potwierdzić testem).

#### 4. Sondy

**File**: `src/main/java/pl/szczurradar/api/health/HealthController.java`

**Intent**: Te same sondy co `app/routes.py:18-35`.

**Contract**: `GET /api/health` → 200 `{"status":"ok"}` bez dostępu do bazy; `GET /api/ready` → 200 `{"status":"ready"}`, gdy `SELECT 1` przechodzi; przy dowolnym wyjątku 503 `{"status":"unavailable"}`.

#### 5. Migracje Flyway

**File**: `src/main/resources/db/migration/V1__create_users.sql`, `V2__create_spring_session.sql`

**Intent**: Schemat od zera na pustej bazie.

**Contract**: `V1`: tabela `users` jak `0001_create_users.py:25-50` **bez** `token_version`: `id UUID PK DEFAULT gen_random_uuid()`, `email TEXT NOT NULL` + `uq_users_email` + `ck_users_email_lower`, `password_hash TEXT NULL`, `role TEXT NOT NULL DEFAULT 'user'` + `ck_users_role`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`. `V2`: `SPRING_SESSION` i `SPRING_SESSION_ATTRIBUTES` według skryptu PostgreSQL z dokumentacji Spring Session (z indeksami). Fix-forward: istniejących plików migracji nie edytuje się po wdrożeniu.

#### 6. Komenda `migrate` i dyspozytor komend

**File**: `src/main/java/pl/szczurradar/api/cli/CommandLine.java` (punkt wejścia/dyspozytor), `cli/MigrateCommand.java`, zmiana w `SzczurRadarApiApplication.java`

**Intent**: Jeden jar uruchamia serwer (bez argumentów) albo komendę operatora (`migrate`, a w fazie 3 `create-admin`) bez serwera WWW; pre-deploy Railway uruchamia `migrate`.

**Contract**: `java -jar target/szczur-radar-api.jar` → serwer; `java -jar target/szczur-radar-api.jar migrate` → kontekst Springa z `WebApplicationType.NONE`, uruchomienie Flyway, kod wyjścia 0 przy sukcesie, ≠0 przy błędzie; nieznana komenda → kod 2 i komunikat użycia na stderr.

#### 7. Testy fazy 1

**File**: `src/test/java/pl/szczurradar/api/...`

**Intent**: Przeniesienie `tests/test_health.py` i `tests/test_migrations.py` oraz baza dla testów integracyjnych.

**Contract**: wspólna konfiguracja Testcontainers (`postgres:18`, `@ServiceConnection`) używana przez wszystkie `*IT`; `HealthIT`: health 200 bez bazy, ready 200 z bazą, ready 503 przy niedostępnej bazie; `MigrationsIT`: migracje od zera na pustej bazie przechodzą, `ck_users_email_lower` i `ck_users_role` odrzucają złe wiersze, tabele sesji istnieją; `CommandLineTest`: nieznana komenda → 2.

#### 8. CI

**File**: `.github/workflows/ci.yml`

**Intent**: Job `backend` na Javie; bez filtrów ścieżek (Wait for CI).

**Contract**: job `backend`: `actions/setup-java` (Temurin 25, cache `maven`), `working-directory: szczur-radar-api`, `./mvnw -B verify`; usunięte: service `postgres`, `TEST_DATABASE_URL`, `REQUIRE_DB_TESTS`, kroki `uv`. Krok „Ensure a PR-env JWT_SECRET” usunięty (`ci.yml:159-180`). Nazwa joba może pozostać `backend` (sprawdzana przez Wait for CI jako część całego workflow).

#### 9. Railway IaC

**File**: `.railway/railway.ts`

**Intent**: Start i pre-deploy na Javie, JDK 25, zmienne bazy dla JDBC; bez `JWT_SECRET`.

**Contract**: serwis `szczur-radar-api`: `start: "java $JAVA_OPTS -jar target/szczur-radar-api.jar"`, `preDeploy: "java -jar target/szczur-radar-api.jar migrate"`, `env`: `PORT: "8000"`, `RAILPACK_JDK_VERSION: "25"`, `PGHOST`/`PGPORT`/`PGUSER`/`PGPASSWORD`/`PGDATABASE` jako referencje do `db.env.*` (nazwy pól SDK potwierdzić przez `railway config plan`); `DATABASE_URL` i `JWT_SECRET` usunięte. Jeśli bramka w CI (`ci.yml:194-206`) uzna usunięcie `JWT_SECRET` za destrukcyjne, zostaje `JWT_SECRET: preserve()` z komentarzem „nieużywane od F-03”. Komentarze o Hypercornie/uv zastąpione opisem Javy (bind dual-stack JVM).

#### 10. `.gitignore`

**File**: `.gitignore`

**Intent**: Sekcja Python zastąpiona przez `target/` i pliki IDE Javy; `.idea/` ignorowane.

**Contract**: `szczur-radar-api/target/` nie trafia do gita.

### Success Criteria:

#### Automated Verification:

- `cd szczur-radar-api && ./mvnw -B verify` przechodzi (unit + IT z Testcontainers)
- `git ls-files szczur-radar-api` nie zawiera plików Pythona ani `uv.lock`
- Job `backend` w CI jest zielony na PR
- `railway config plan` dla PR env przechodzi bramkę CI (job `railway-config` zielony)

#### Manual Verification:

- W PR env pre-deploy loguje zastosowanie migracji `V1` i `V2`, a `/api/health` i `/api/ready` przez publiczny adres PR env zwracają 200
- Healthcheck Railway (IPv4) przechodzi i proxy web→api działa (brak 502)

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przez człowieka.

---

## Faza 2: Uwierzytelnianie i autoryzacja

### Overview

Spring Security z sesją w Postgresie, logowanie JSON, `/me`, logout, odpowiedzi JSON, filtr Origin, port limitera ruchomego okna z Pythona i test kompletności autoryzacji — z zachowaniem kontraktu S-01 widocznego dla SPA.

### Changes Required:

#### 1. Zależności

**File**: `szczur-radar-api/pom.xml`

**Intent**: Bezpieczeństwo, sesja JDBC, argon2, limiter.

**Contract**: `spring-boot-starter-security`, `spring-boot-session` (moduł Boot 4 — wymagany dla `same-site`), `spring-session-jdbc`, `org.bouncycastle:bcprov-jdk18on` (dla `Argon2PasswordEncoder`); test: `spring-security-test`. Limiter korzysta z kolekcji i blokady ze standardowej biblioteki Javy, bez dodatkowej zależności.

#### 2. Model użytkownika

**File**: `src/main/java/pl/szczurradar/api/user/User.java`, `UserRepository.java`, `Emails.java`

**Intent**: Dostęp do tabeli `users` i jedna normalizacja e-maila dla loginu i CLI.

**Contract**: `User(id UUID, email, passwordHash nullable, role, createdAt)`; role `user`/`admin`; `Emails.normalize(s) = s.strip().toLowerCase(Locale.ROOT)`; `MAX_EMAIL_LENGTH = 320`; repozytorium: `findByEmail`, `findById`, `existsByRole`, `existsByEmail`, `insert`.

#### 3. Hasła

**File**: `src/main/java/pl/szczurradar/api/auth/Passwords.java` (lub konfiguracja `PasswordEncoder`)

**Intent**: Argon2 z pudełka Springa i polityka haseł dla CLI (i S-07).

**Contract**: bean `PasswordEncoder` = `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`; `validatePolicy(plain)`: 12–128 znaków, inaczej `IllegalArgumentException` z komunikatem dla użytkownika (`app/auth/passwords.py:41-50`).

#### 4. `UserDetailsService`

**File**: `src/main/java/pl/szczurradar/api/auth/AccountDetailsService.java`

**Intent**: Logowanie po e-mailu; principal = UUID; konto bez hasła nie loguje się, ale kosztuje tyle samo czasu.

**Contract**: `loadUserByUsername(email)` → znormalizowany e-mail, użytkownik z bazy; `username` w zwróconych `UserDetails` = `id.toString()`; uprawnienie `ROLE_ADMIN`/`ROLE_USER`; `password_hash IS NULL` → stały hash-atrapa (wygenerowany raz przy starcie), konto `enabled`; brak użytkownika → `UsernameNotFoundException` (DaoAuthenticationProvider wyrównuje czas).

#### 5. Konfiguracja bezpieczeństwa

**File**: `src/main/java/pl/szczurradar/api/config/SecurityConfig.java`, `auth/JsonAuthenticationEntryPoint.java`, `auth/JsonAccessDeniedHandler.java`

**Intent**: Deny-by-default, jawne trasy publiczne, odpowiedzi JSON zamiast przekierowań, CSRF Springa wyłączony na rzecz filtra Origin.

**Contract**: publiczne: `GET /api/health`, `GET /api/ready`, `POST /api/auth/login`, `POST /api/auth/logout`; wszystko inne `authenticated()`; `csrf.disable()`; `formLogin`/`httpBasic` wyłączone; brak sesji / nieprawidłowa sesja → 401 `{"detail":"not_authenticated"}`; brak roli → 403 `{"detail":"forbidden"}`; logout: `POST /api/auth/logout` → zawsze 204, unieważnia sesję i kasuje ciasteczko (z tymi samymi atrybutami), bez wymagania sesji.

Zapisywanie żądań przed uwierzytelnieniem wyłączone jawnie przez `requestCache(cache -> cache.requestCache(new NullRequestCache()))`. Anonimowe żądanie zakończone 401 nie tworzy sesji w JDBC ani nie ustawia `Set-Cookie`; dotyczy także wejścia z `Accept: text/html`. Uwierzytelnienie po poprawnym logowaniu nadal jest zapisywane w sesji Spring Session JDBC.

#### 6. Sesja i ciasteczko

**File**: `src/main/resources/application.properties`, `src/main/java/pl/szczurradar/api/config/SessionConfig.java`, `src/main/java/pl/szczurradar/api/auth/SessionCookieRenewalFilter.java`

**Intent**: Sesja w Postgresie z atrybutami ciasteczka z S-01; 30 dni bezczynności.

**Contract**: `spring.session.timeout=30d`; ciasteczko: nazwa `sr_session`, `HttpOnly`, `Secure` (wyłączalne tylko przez `COOKIE_SECURE` ∈ {`0`,`false`,`no`}, bez rozróżniania wielkości liter), `SameSite=Strict`, `Path=/api`, `Max-Age=2592000`; `spring.session.jdbc.initialize-schema=never` (schemat z Flyway); sprzątanie wygasłych sesji domyślne (co minutę).

`SessionCookieRenewalFilter` odnawia ciasteczko istniejącej sesji przy odpowiedzi 2xx na uwierzytelnione żądanie pod `/api/`. Wykluczone: `/api/health`, `/api/ready`, `/api/auth/login` (wystawia ciasteczko samodzielnie) i `/api/auth/logout` (wyłącznie kasuje ciasteczko). Warunki: nieanonimowe uwierzytelnienie oraz nadal ważna sesja dostępna przez `getSession(false)`; brak sesji, jej unieważnienie lub odpowiedź błędu nie powodują odnowienia ani utworzenia sesji. Odnowienie zachowuje aktualne ID sesji i wszystkie atrybuty, ustawiając ponownie `Max-Age=2592000`; nie rotuje ID. Zapis nagłówka następuje przed zatwierdzeniem odpowiedzi, z użyciem tego samego `HttpSessionIdResolver` i skonfigurowanego `CookieSerializer` co Spring Session, bez drugiego ciasteczka o innej ścieżce lub z innymi atrybutami. Samo odświeżanie `lastAccessedTime` w bazie nie odnawia terminu ciasteczka w przeglądarce — potrzebny jest nowy `Set-Cookie`.

#### 7. Kontroler auth

**File**: `src/main/java/pl/szczurradar/api/auth/AuthController.java`, `LoginRequest.java`, `UserResponse.java`

**Intent**: Logowanie JSON, `/me`, z kontraktem z `app/auth/routes.py:68-123`.

**Contract**: `POST /api/auth/login` przyjmuje wyłącznie `application/json` (inaczej 415) `{email ≤320, password ≤1024}`; nieznane pola → 400 `{"detail":"invalid_request"}`; e-mail normalizowany; limiter (`reserve` przed uwierzytelnieniem); każdy błąd uwierzytelnienia → 401 `{"detail":"invalid_credentials"}`; sukces → 200 `{id, email, role}`, zapis kontekstu przez `SecurityContextRepository.saveContext`, zmiana ID sesji (ochrona przed fixation) i `Set-Cookie: sr_session=…`. `GET /api/auth/me` → 200 `{id, email, role}` zalogowanego użytkownika (z bazy po UUID; usunięty użytkownik → 401). Żadna odpowiedź błędu nie ustawia ciasteczka sesji.

#### 8. Limiter prób logowania

**File**: `src/main/java/pl/szczurradar/api/auth/LoginRateLimiter.java`, `auth/ClientIp.java`

**Intent**: Port obecnego algorytmu ruchomego okna i rezerwacji z Pythona (Critical Implementation Details), zachowujący dokładny kontrakt S-01.

**Contract**: `LoginRateLimiter` przechowuje w pamięci procesu mapy e-mail/IP do kolejek timestampów porażek oraz osobne liczniki trwających rezerwacji; jedna replika API. Limit: 5 dla znormalizowanego e-maila, 20 dla IP, liczony jako liczba porażek w ostatnich 15 minutach + trwające rezerwacje. Timestampy `<= now - 15 min` wypadają z okna. `reserve(email, ip) → Optional<Duration retryAfter>` sprawdza i rezerwuje oba limity atomowo pod wspólną blokadą; odmowa nie zmienia żadnego licznika rezerwacji. `finish(email, ip, outcome ∈ {FAILURE, SUCCESS, ABORTED})` wykonywane dokładnie raz dla udanej rezerwacji, zgodnie z Critical Implementation Details. `Retry-After`: czas do wygaśnięcia porażki zwalniającej miejsce (jak `_retry_after` w Pythonie), zaokrąglony w górę do pełnych sekund, min. 1; jeżeli blokadę powodują trwające rezerwacje, 1 s; gdy blokują oba limity, większy z czasów. Przekroczenie → 429 `{"detail":"too_many_attempts"}`. Zegar monotoniczny wstrzykiwany (testy). Przy obsłudze żądań, nie częściej niż co 15 min, sprzątanie wygasłych timestampów i pustych historii także dla nieaktywnych kluczy; liczniki rezerwacji usuwane dopiero przy zejściu do zera, nigdy przez upływ czasu. `ClientIp`: `X-Real-IP` (przycięty, niepusty), inaczej `request.getRemoteAddr()`.

#### 9. Filtr Origin

**File**: `src/main/java/pl/szczurradar/api/auth/OriginGuardFilter.java`

**Intent**: Ochrona CSRF z S-01 (`app/security.py:18-34`).

**Contract**: dla `POST/PUT/PATCH/DELETE` pod `/api/`: nagłówek `Origin` obecny i jego `host[:port]` (małe litery) ≠ nagłówek `Host` → 403 `{"detail":"cross_origin"}`; `Origin: null` → 403; brak `Origin` → przepuść; działa przed uwierzytelnieniem.

#### 10. Testy fazy 2

**File**: `src/test/java/pl/szczurradar/api/auth/...`

**Intent**: Przeniesienie `test_auth.py`, `test_authz_completeness.py`, `test_rate_limit.py`, `test_security.py`, `test_passwords.py` (z wyjątkiem przypadków JWT).

**Contract**: `AuthIT` (lista w Testing Strategy), `AuthorizationCompletenessIT` (iteruje po wszystkich mapowaniach `RequestMappingHandlerMapping`, wypełnia zmienne ścieżki atrapami typu, wywołuje bez sesji, oczekuje 401 poza jawną listą `PUBLIC_ROUTES` z uzasadnieniami; `ADMIN_ROUTES` puste, sprawdzane, że odnoszą się do istniejących tras), `LoginRateLimiterTest`, `OriginGuardFilterTest`, `PasswordsTest`, `ClientIpTest`.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` przechodzi, w tym `AuthIT`, `AuthorizationCompletenessIT`, `LoginRateLimiterTest`, `OriginGuardFilterTest`, `PasswordsTest`
- `AuthIT` potwierdza atrybuty ciasteczka `HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=2592000`
- `AuthorizationCompletenessIT` sprawdza co najmniej jedną chronioną trasę (`GET /api/auth/me`) i nie znajduje niechronionych
- Job `backend` w CI jest zielony

#### Manual Verification:

- W PR env (po utworzeniu konta testowego przez SQL lub fazę 3): logowanie w SPA, odświeżenie strony zachowuje sesję, głęboki link bez sesji prowadzi na ekran logowania, wylogowanie działa
- `curl` z obcym `Origin` na `POST /api/auth/login` w PR env → 403 `cross_origin`; 6 złych prób na ten sam e-mail → 429 z `Retry-After`
- Sesja przeżywa uśpienie/restart API w PR env (zalogowany użytkownik nadal zalogowany po ponownym wybudzeniu)

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przez człowieka.

---

## Faza 3: CLI `create-admin` i szkielet Embabel

### Overview

Komenda operatora do utworzenia pierwszego admina (odpowiednik `app/cli.py`) i szkielet Embabel startujący z prawdziwym kluczem wybranego dostawcy dostarczonym jako sekret środowiska, bez wywołań LLM.

### Changes Required:

#### 1. Komenda `create-admin`

**File**: `src/main/java/pl/szczurradar/api/cli/CreateAdminCommand.java`, rejestracja w dyspozytorze komend z fazy 1

**Intent**: Bootstrap pierwszego admina z zachowaniem `app/cli.py:40-99`.

**Contract**: `java -jar target/szczur-radar-api.jar create-admin --email <e-mail>` (bez serwera WWW); w jednej transakcji `pg_advisory_xact_lock(0x53525F41444D494E)`; odmowa (kod 1), gdy istnieje jakikolwiek admin lub zajęty e-mail; hasło dwukrotnie z ukrytego promptu (`System.console().readPassword`; brak konsoli → kod 2 z komunikatem), nigdy jako argument i nigdy w logach; walidacja polityki (kod 1 przy złym haśle lub niezgodnych hasłach, nic nie zapisane); e-mail znormalizowany, bez `@` lub > 320 znaków → kod 2; brak konfiguracji bazy → kod 2; sukces → `Created admin <email>`, kod 0. Źródło hasła wstrzykiwalne w testach.

#### 2. Szkielet Embabel

**File**: `szczur-radar-api/pom.xml`, `src/main/java/pl/szczurradar/api/agents/...`, `src/main/java/pl/szczurradar/api/config/LlmProviderProperties.java`, `src/main/resources/application.properties`, `src/main/resources/application-gemini.properties`, `src/main/resources/application-deepseek.properties`, `src/main/resources/application-openrouter.properties`, `.github/workflows/ci.yml`, `.railway/railway.ts`, `szczur-radar-api/README.md`

**Intent**: Embabel na classpathie i w kontekście Springa od teraz (zgodność z Boot 4 / Java 25 sprawdzona wcześnie), z jednym wybranym dostawcą, prawdziwym kluczem i bez wywołań LLM. Wspólny kontrakt konfiguracji dla Gemini (Google AI Studio), DeepSeek bezpośrednio i Luny przez OpenRouter.

**Contract**: BOM/wersja Embabel 1.5.x (najnowsza stabilna z Maven Central); `embabel-agent-starter` oraz moduły dostawców `embabel-agent-starter-google-genai`, `embabel-agent-starter-deepseek`, `embabel-agent-starter-openai-custom`. Konfiguracja aplikacji: wymagane `LLM_API_PROVIDER` ∈ {`gemini`, `deepseek`, `openrouter`}, `LLM_MODEL` (dokładne ID modelu wybranego dostawcy) i prawdziwy `LLM_API_KEY` tego dostawcy. Jeden aktywny dostawca na uruchomienie; brak kluczy pozostałych dostawców nie blokuje startu. `embabel.models.default-llm=${LLM_MODEL}`. `LlmProviderProperties` waliduje dozwolonego dostawcę i niepuste wartości; błąd konfiguracji przerywa start z komunikatem bez wartości sekretu. Profile dostawców wybierane na podstawie `LLM_API_PROVIDER` mapują wspólne zmienne na właściwości Embabel; autokonfiguracje niewybranych dostawców są wyłączone. Platforma Embabel i wybrany dostawca pozostają włączeni w serwerze i teście — bez kluczy zastępczych ani fallbacku wyłączającego platformę.

Mapowanie profili (bez wymagania dostawco-specyficznych nazw zmiennych w środowisku):

| `LLM_API_PROVIDER` | Dostawca i konfiguracja Embabel |
| --- | --- |
| `gemini` | Natywny Google GenAI, uwierzytelnienie kluczem z Google AI Studio; `embabel.agent.platform.models.googlegenai.api-key=${LLM_API_KEY}`. Model Gemini wskazany przez `LLM_MODEL`; bez konfiguracji Vertex AI. |
| `deepseek` | DeepSeek bezpośrednio; `embabel.agent.platform.models.deepseek.api-key=${LLM_API_KEY}`, endpoint dostawcy `https://api.deepseek.com`; model wskazany przez `LLM_MODEL`. |
| `openrouter` | OpenRouter przez protokół zgodny z OpenAI; `embabel.agent.platform.models.openai.custom.api-key=${LLM_API_KEY}`, `base-url=https://openrouter.ai/api/v1`, `models=${LLM_MODEL}`. `LLM_MODEL` to dokładny identyfikator Luny z katalogu OpenRouter, nie sama nazwa „Luna”. Skonfigurować klienta tak, aby docelowy endpoint completions był `https://openrouter.ai/api/v1/chat/completions` (bez podwójnego `/v1`). |

Jeden agent szkieletowy w pakiecie `agents` (`@Agent` z akcją, która nie woła LLM). Embabel nie wystawia żadnych tras HTTP (pilnuje `AuthorizationCompletenessIT`). Start i test szkieletu nie wykonują wywołań LLM ani sieciowej walidacji klucza — potwierdzają lokalną rejestrację platformy, wybranego modelu i agenta, a nie skuteczność wywołania dostawcy. Ten krok nie wybiera zwycięzcy pomiarów F-02; dostawca i dokładne ID modelu są jawną konfiguracją środowiska. README zawiera przykłady trzech wariantów i informację, że zmiana dostawcy wymaga spójnej zmiany całej trójki ustawień.

Dostarczenie konfiguracji od fazy 3: lokalnie `LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_API_KEY` w środowisku procesu; w CI job `backend` przekazuje `LLM_API_PROVIDER` i `LLM_MODEL` z GitHub Actions variables o tych nazwach oraz `LLM_API_KEY` z GitHub Actions secret do `./mvnw -B verify`. Operator dostarcza spójną trójkę ustawień do CI oraz osobno do serwisu API w PR env przed weryfikacją fazy 3; do produkcji przed merge przełączającym backend. Sekret produkcyjny nie jest kopiowany do PR env ani CI. `.railway/railway.ts` od fazy 3 deklaruje `LLM_API_PROVIDER: preserve()`, `LLM_MODEL: preserve()`, `LLM_API_KEY: preserve()`; samo `preserve()` nie tworzy wartości — wymagane jest ich wcześniejsze ustawienie w każdym środowisku. Wartość klucza nie trafia do repo ani logów. Brak wymaganej konfiguracji w CI powoduje jawny błąd, nie pominięcie `AgentSkeletonIT`. README API opisuje te wymagania także dla komend uruchamiających wspólny kontekst Springa.

#### 3. Testy fazy 3

**File**: `src/test/java/pl/szczurradar/api/cli/...`, `.../agents/...`

**Intent**: Przeniesienie `tests/test_cli.py` i test szkieletu Embabel.

**Contract**: `CreateAdminCommandIT`: tworzy admina z hashem argon2; odmawia drugiego admina; równoległy bootstrap z dwóch wątków/procesów tworzy dokładnie jednego admina; odmawia zajętego e-maila; niezgodne hasła i za krótkie hasło nic nie tworzą; błędy użycia → 2; e-mail > 320 → 2; utworzony admin loguje się przez `POST /api/auth/login`. `AgentSkeletonIT`: kontekst startuje z `LLM_API_PROVIDER`, `LLM_MODEL`, prawdziwym `LLM_API_KEY` z sekretów środowiska i tą samą konfiguracją Embabel co serwer, bez testowej flagi włączającej platformę; model domyślny pochodzi od wybranego dostawcy, `AgentPlatform` zawiera agenta szkieletowego, a start i wykonanie jego akcji nie wywołują LLM. Brak kluczy niewybranych dostawców nie blokuje tego testu. `LlmProviderConfigTest`: dla każdego z trzech wariantów sprawdza mapowanie klucza/modelu i aktywację wyłącznie wybranego dostawcy, w tym docelowy URL OpenRouter; używa syntetycznych danych konfiguracji, bez wywołań sieciowych, a nie fallbacków runtime. Sprawdza także błędy przy nieznanym dostawcy oraz brakującym/pustym kluczu lub modelu.

**Korekta kryterium 3.2 (triage F5 — decyzja użytkownika):** dotychczasowy tytuł „`AgentSkeletonIT` przechodzi w CI bez żadnych sekretów LLM” poniżej i w Progress pozostaje jako utrwalony identyfikator kroku zgodnie z `references/progress-format.md`. Obowiązująca treść kryterium: „`AgentSkeletonIT` przechodzi w CI z wybranymi `LLM_API_PROVIDER` i `LLM_MODEL` oraz prawdziwym `LLM_API_KEY` dostarczonym jako sekret, rejestruje model i agenta w konfiguracji serwera oraz nie wykonuje wywołań LLM”. Ta decyzja zastępuje wymaganie startu bez sekretów; nie oznacza oznaczenia kroku jako wykonanego.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` przechodzi, w tym `CreateAdminCommandIT` i `AgentSkeletonIT`
- `AgentSkeletonIT` przechodzi w CI bez żadnych sekretów LLM
- Job `backend` w CI jest zielony

#### Manual Verification:

- W PR env `railway ssh -s szczur-radar-api -e <pr-env> -- java -jar target/szczur-radar-api.jar create-admin --email …` pyta o hasło bez echa, tworzy admina, a admin loguje się w SPA PR env
- Logi startu API w PR env pokazują rejestrację platformy Embabel bez błędów o brakującym LLM

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przez człowieka.

---

## Faza 4: Dokumentacja, weryfikacja w PR env i przełączenie produkcji

### Overview

Dokumentacja repo opisuje stack Javy, PR env jest zweryfikowany end-to-end, a produkcja zostaje przełączona na czystą bazę przez operatora i merge człowieka.

### Changes Required:

#### 1. Zasady i dokumentacja repo

**File**: `REPOSITORY_GUIDELINES.md`, `README.md`, `szczur-radar-api/README.md`

**Intent**: Agenci i ludzie dostają aktualne komendy i zasady dla Javy.

**Contract**: `REPOSITORY_GUIDELINES.md`: opis `szczur-radar-api/` (Spring Boot 4, Java 25, Maven, pakiety według funkcji), komendy (`./mvnw verify`, `./mvnw spring-boot:run`, `java -jar … migrate|create-admin`), zasada migracji zmieniona z „działający `downgrade()`” na fix-forward Flyway (nowa migracja zamiast edycji, PITR jako ostateczność), reguła trasy publicznej → `PUBLIC_ROUTES` w `AuthorizationCompletenessIT`, konwencja `*Test`/`*IT` i fixture'ów JSON, usunięta wzmianka o `JWT_SECRET` w PR env. `README.md:21`: uruchomienie lokalne Javy. `szczur-radar-api/README.md`: przepisany (baza lokalna z `compose.local.yaml` przez zmienne `PG*`, migracje, testy z Dockerem, CLI, konwencja fixture'ów, reguła „własny executor przy kodzie równoległym” dla Javy 25).

#### 2. Dokumenty foundation

**File**: `context/foundation/tech-stack-api.md`, `context/foundation/tech-stack-web.md`, `context/foundation/infrastructure.md`, `context/foundation/roadmap.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`

**Intent**: Decyzja o stacku zapisana jak „Update 2026-09-27”, bez cichych rozbieżności.

**Contract**: `tech-stack-api.md`: frontmatter `starter_id: spring`, `language_family: java`, `can_judge_agent: true`; nowa sekcja „Update 2026-10-01 — Java/Spring Boot” (powód, Spring Boot 4 + Java 25 + Maven, Spring Security + Spring Session JDBC zamiast JWT, Flyway fix-forward, Embabel zamiast LangGraph, limiter ruchomego okna z rezerwacjami przeniesiony z Pythona); sekcja o FastAPI/LangGraph oznaczona jako zastąpiona. `tech-stack-web.md:29`: „FastAPI backend”/„Pydantic” → backend Spring Boot. `infrastructure.md`: runtime Java 25 przez Railpack (`RAILPACK_JDK_VERSION`), usunięte zależności od `.python-version`/uv w treści i rejestrze ryzyk.

Roadmapa (triage F6): opis F-03 w `roadmap.md` odzwierciedla sesje JDBC bez `token_version`, Flyway fix-forward, czystą bazę z adminem tworzonym od nowa, brak powrotu do Pythona i szkielet Embabel z konfiguracją `LLM_API_PROVIDER`/`LLM_MODEL`/`LLM_API_KEY`. Pytanie o zachowanie kont jest rozstrzygnięte, a F-02 bazuje na Embabel; pomiar jakości modeli nadal należy do F-02. Te decyzje zsynchronizowano już podczas triage; faza 4 sprawdza zgodność z końcową implementacją. Każda dalsza edycja `roadmap.md` wymaga synchronizacji `tools/render_roadmap.swift` oraz regeneracji i wizualnej kontroli `docs/visuals/roadmap-mvp.png` zgodnie z regułą repo. Status F-03 zmienia się dopiero zgodnie z rzeczywistym postępem; sama aktualizacja dokumentacji nie oznacza ukończenia wdrożenia.

#### 3. Deploy plan

**File**: `context/deployment/deploy-plan.md`

**Intent**: Zapis procedury przełączenia i nowych komend operatora.

**Contract**: nowa sekcja „F-03: przełączenie na Javę” z krokami z Migration Notes (czyszczenie schematu, merge, pre-deploy `migrate`, `create-admin`, weryfikacja), troubleshooting Javy (build Railpack, JDK, healthcheck), komendy `uv run …` w aktywnych instrukcjach zastąpione komendami Javy; wpisy historyczne bez zmian.

#### 4. PR i issue

**File**: GitHub (PR do `main`, issue F-03)

**Intent**: Zgodnie z workflow repo: issue roadmapy z komentarzami kamieni milowych, PR z `Refs #<issue>`.

**Contract**: issue „F-03: Backend w Javie (Spring Boot)” (jeśli nie istnieje) z komentarzem startowym (change id, gałąź, PR); PR z `Refs #<issue>` i `Refs #24` (F-02 czeka na tę zmianę); komentarz w issue po weryfikacji PR env. Agent nie merguje.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` przechodzi
- Cały workflow CI (backend, web-unit, web-container-smoke, railway-config) jest zielony na PR
- `grep` po aktywnych instrukcjach nie zwraca `uv run|uv sync|alembic|pytest|hypercorn|JWT_SECRET` (poza komentarzem o nieużywanym `preserve()`); pliki: `REPOSITORY_GUIDELINES.md`, `README.md`, `szczur-radar-api/README.md`, `.railway/railway.ts`, `.github/workflows/ci.yml`

#### Manual Verification:

- PR env end-to-end: logowanie, ochrona głębokiego linku, wylogowanie, 403 dla obcego `Origin`, 429 po limicie, `X-Real-IP` liczy prawdziwy adres i nie da się go podrobić
- Zimny start API po uśpieniu w PR env zmierzony i zapisany w `deploy-plan.md`
- Operator wyczyścił schemat bazy produkcyjnej przed merge, PITR był włączony
- Po merge przez człowieka pre-deploy na produkcji zastosował `V1` i `V2`, a `/api/health` i `/api/ready` na publicznym adresie zwracają 200
- Admin produkcyjny utworzony przez `create-admin` loguje się na publicznym adresie; issue F-03 zamknięte z podsumowaniem dopiero po tej weryfikacji

**Implementation Note**: Kroki na produkcji wykonuje operator; agent przygotowuje PR i zatrzymuje się na „PR gotowy, CI i PR env zielone”.

---

## Testing Strategy

### Unit Tests (`*Test`, bez Springa i Dockera):

- `LoginRateLimiterTest` (z `test_rate_limit.py`): blokada po 5 porażkach na e-mail; limit IP między różnymi e-mailami (20); okno się przesuwa (sztuczny zegar, porażki rozłożone w czasie); po 5 porażkach w t=0 próba w t=3 min pozostaje zablokowana z `Retry-After=720`, a w t=15 min jest dopuszczona; sukces usuwa tylko historię porażek e-maila; równoległe próby nie przechodzą wszystkie przez limit e-maila; przy 5 rezerwacjach sukces jednej zachowuje pozostałe 4 i pozwala na dokładnie jedną nową rezerwację, kolejna jest blokowana; odmowa limitu IP nie zużywa rezerwacji e-maila i odwrotnie; przerwana próba zwalnia własną rezerwację; wygasłe historie nieaktywnych kluczy są usuwane, ale upływ czasu nie usuwa trwających rezerwacji; `Retry-After` zaokrąglane w górę, ≥ 1, z większego czasu obu limitów.
- `ClientIpTest`: `X-Real-IP` ma pierwszeństwo; fallback na adres peera.
- `OriginGuardFilterTest` (z `test_security.py`): obcy Origin → 403; ten sam → przechodzi; ten sam host z innym portem → 403; `Origin: null` → 403; brak Origin → przechodzi; metody bezpieczne nie są sprawdzane.
- `PasswordsTest` (z `test_passwords.py`): hash to argon2 i weryfikuje się; polityka akceptuje 12 i 128, odrzuca 0, 11, 129.
- `CommandLineTest`: nieznana komenda → 2.

### Integration Tests (`*IT`, Spring + Testcontainers Postgres 18):

- `HealthIT`, `MigrationsIT` (faza 1).
- `AuthIT` — brak sesji dla anonimowych błędów (triage F3): `GET /api/auth/me` bez ciasteczka, osobno z `Accept: application/json` i `Accept: text/html`, zwraca 401 `{"detail":"not_authenticated"}`, bez nagłówka `Set-Cookie` i bez nowego rekordu w `SPRING_SESSION` ani `SPRING_SESSION_ATTRIBUTES`; porównanie stanu bazy w izolowanym teście.
- `AuthIT` (z `test_auth.py`): login ustawia ciasteczko z atrybutami i zwraca `{id, email, role}`; e-mail z wielkimi literami i spacjami loguje; zły e-mail i złe hasło dają identyczne 401; konto bez hasła → 401; `/me` zwraca wyłącznie własne konto; `/me` bez sesji → 401; sesja usuniętego użytkownika → 401; usunięcie sesji użytkownika z repozytorium sesji (mechanizm S-07) → 401; logout kasuje ciasteczko; 6. porażka e-maila → 429 z `Retry-After`; 21. porażka z jednego IP na różne e-maile → 429; sukces zeruje licznik e-maila; form-encoded → 415 bez ciasteczka; nieznane pola → 400 bez ciasteczka.
- `AuthIT` — odnawianie sesji (triage F2: Fix A): poprawne `/me` ponownie ustawia dokładnie jedno ciasteczko `sr_session` z tym samym ID, pełnym `Max-Age=2592000` i wszystkimi atrybutami z kontraktu. Deterministyczny scenariusz z kontrolowanym `lastAccessedTime` w repozytorium sesji i czasem w testowym magazynie ciasteczek: logowanie w dniu 0, poprawne `/me` w dniu 29 przesuwa termin ciasteczka do dnia 59 i sesja działa po pierwotnym terminie z dnia 30; osobny scenariusz 30 dni bezczynności daje 401 nawet po ręcznym dosłaniu starego ID i nie odnawia ciasteczka. Bez rzeczywistego oczekiwania na upływ czasu. Brak odnowienia dla odpowiedzi błędu (w tym `/me` usuniętego użytkownika), sond oraz logout; logout nie dopisuje ciasteczka z dodatnim `Max-Age` po jego skasowaniu. Weryfikacja zarówno nagłówków i terminu ciasteczka po stronie klienta, jak i ważności sesji JDBC.
- `AuthorizationCompletenessIT` (z `test_authz_completeness.py`), `CreateAdminCommandIT` (z `test_cli.py`), `AgentSkeletonIT`.
- Przypadki z `test_tokens.py` (JWT) nie są przenoszone — JWT nie istnieje; ich rolę przejmują testy sesji w `AuthIT`.

### Manual Testing Steps:

1. W PR env: utworzyć admina przez `railway ssh … create-admin`, zalogować się w SPA, odświeżyć, otworzyć głęboki link w oknie prywatnym (ekran logowania), wylogować się.
2. `curl -i -X POST https://<pr-env>/api/auth/login -H 'Origin: https://evil.example' -H 'Content-Type: application/json' -d '{"email":"x@y.z","password":"x"}'` → 403 `cross_origin`.
3. 6 złych logowań na jeden e-mail → 429 z `Retry-After`; sprawdzić w logach, że IP klienta to adres z `X-Real-IP`, a `curl -H 'X-Real-IP: 1.2.3.4'` go nie podmienia.
4. Poczekać na uśpienie API, wywołać `/api/health`, zmierzyć czas; sprawdzić, że sesja z kroku 1 nadal działa.
5. Na produkcji po przełączeniu: kroki 1 (bez głębokiego testu limitu) na publicznym adresie.

## Performance Considerations

- Zimny start JVM + Spring + Embabel po uśpieniu API wydłuża pierwszą odpowiedź; mierzymy w fazie 4. Jeśli okaże się nieakceptowalny: AOT cache (Java 25) albo wyłączenie `sleepApplication` — osobna decyzja, poza tym planem.
- Argon2 (`defaultsForSpringSecurity_v5_8`: 16 MB, 2 iteracje) przy 1 replice i pojedynczym użytkowniku nie jest wąskim gardłem; limiter ogranicza próby.
- Spring Session JDBC dodaje zapis do bazy przy zmianie sesji i sprzątanie co minutę — pomijalne przy tej skali.

## Migration Notes

Przełączenie produkcji (wykonuje operator, w tej kolejności):

1. PR zielony (CI + weryfikacja PR env z fazy 4).
2. Potwierdzić, że PITR jest włączone (`railway postgres pitr status --json`).
3. Wyczyścić schemat produkcji: `railway connect postgres` (env `production`) → `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`. Od tej chwili stary Python nie zaloguje nikogo (brak tabeli `users`) — okno trwa do deployu Javy.
4. Człowiek merguje PR do `main`. CI na `main`: `railway-config` stosuje start/pre-deploy/zmienne, potem deploy kodu; pre-deploy `migrate` zakłada `V1`, `V2`.
5. `railway ssh -s szczur-radar-api -e production -- java -jar target/szczur-radar-api.jar create-admin --email …`.
6. Weryfikacja na publicznym adresie (faza 4, kryteria ręczne), wpis w `deploy-plan.md`, zamknięcie issue F-03.

Ścieżka awaryjna (triage F4 — decyzja użytkownika): powrót do backendu Python jest wykluczony. Po przełączeniu awarie usuwamy przez poprawkę Javy lub konfiguracji oraz ponowne wdrożenie; błędy schematu naprawiamy kolejną migracją Flyway (fix-forward). Poprawka przechodzi CI i weryfikację PR env, a merge do `main` wykonuje człowiek. Jeżeli pierwsze wdrożenie Javy nie powiedzie się po wyczyszczeniu schematu, przerwa w działaniu trwa do skutecznego wdrożenia poprawki. PITR służy odzyskaniu danych, nie przywróceniu runtime'u Pythona; przed podłączeniem odtworzonej bazy do Javy trzeba potwierdzić zgodność jej schematu z migracjami Flyway. Stan sprzed czyszczenia zawiera schemat Pythona i nie jest gotową bazą dla nowego backendu. Nie odtwarzamy `JWT_SECRET` na potrzeby awaryjnego powrotu do Pythona.

## References

- Research: `context/changes/java-api-rewrite/research.md`
- Kontrakt S-01: `context/archive/2026-09-27-admin-bootstrap-and-login/plan.md:52-57,220`
- Roadmapa: `context/foundation/roadmap.md` (F-03)
- Obecny backend (spec zachowania): `szczur-radar-api/app/**`, `szczur-radar-api/tests/**` (stan `main` / commit `d2a52d3`)
- Konsument API: `szczur-radar-web/src/api/auth.ts`, `src/api/client.ts`, `src/views/LoginView.vue`
- Spring Security — Persisting Authentication, Session Management, CSRF: https://docs.spring.io/spring-security/reference/
- Spring Session JDBC: https://docs.spring.io/spring-session/reference/configuration/jdbc.html
- Railpack Java: https://railpack.com/languages/java
- Embabel: https://docs.embabel.com/llms.txt, https://hub.embabel.com/reference/asynch-mode
- Konfiguracja dostawców (triage F5): https://github.com/embabel/embabel-agent/blob/main/embabel-agent-docs/src/main/asciidoc/getting-started/installing/page.adoc — natywny Google GenAI, DeepSeek i OpenAI Custom; nazwy zmiennych `LLM_*` są kontraktem naszej aplikacji mapowanym na właściwości Embabel.
- Identyfikatory modeli OpenRouter: https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Szkielet Java i budowanie

#### Automated

- [x] 1.1 `cd szczur-radar-api && ./mvnw -B verify` przechodzi (unit + IT z Testcontainers) — 75fe622
- [x] 1.2 `git ls-files szczur-radar-api` nie zawiera plików Pythona ani `uv.lock` — 75fe622
- [x] 1.3 Job `backend` w CI jest zielony na PR — 383cfc5
- [x] 1.4 `railway config plan` dla PR env przechodzi bramkę CI (job `railway-config` zielony) — 383cfc5

#### Manual

- [x] 1.5 W PR env pre-deploy loguje zastosowanie migracji `V1` i `V2`, a `/api/health` i `/api/ready` przez publiczny adres PR env zwracają 200 — 383cfc5
- [x] 1.6 Healthcheck Railway (IPv4) przechodzi i proxy web→api działa (brak 502) — 383cfc5

### Phase 2: Uwierzytelnianie i autoryzacja

#### Automated

- [x] 2.1 `./mvnw -B verify` przechodzi, w tym `AuthIT`, `AuthorizationCompletenessIT`, `LoginRateLimiterTest`, `OriginGuardFilterTest`, `PasswordsTest` — e0c455c
- [x] 2.2 `AuthIT` potwierdza atrybuty ciasteczka `HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=2592000` — e0c455c
- [x] 2.3 `AuthorizationCompletenessIT` sprawdza co najmniej jedną chronioną trasę (`GET /api/auth/me`) i nie znajduje niechronionych — e0c455c
- [x] 2.4 Job `backend` w CI jest zielony — e0c455c

#### Manual

- [x] 2.5 W PR env (po utworzeniu konta testowego przez SQL lub fazę 3): logowanie w SPA, odświeżenie strony zachowuje sesję, głęboki link bez sesji prowadzi na ekran logowania, wylogowanie działa — e0c455c
- [x] 2.6 `curl` z obcym `Origin` na `POST /api/auth/login` w PR env → 403 `cross_origin`; 6 złych prób na ten sam e-mail → 429 z `Retry-After` — e0c455c
- [x] 2.7 Sesja przeżywa uśpienie/restart API w PR env (zalogowany użytkownik nadal zalogowany po ponownym wybudzeniu) — e0c455c

### Phase 3: CLI `create-admin` i szkielet Embabel

#### Automated

- [x] 3.1 `./mvnw -B verify` przechodzi, w tym `CreateAdminCommandIT` i `AgentSkeletonIT` — ecdbd42
- [x] 3.2 `AgentSkeletonIT` przechodzi w CI bez żadnych sekretów LLM — ecdbd42
- [x] 3.3 Job `backend` w CI jest zielony — ecdbd42

#### Manual

- [x] 3.4 W PR env `railway ssh -s szczur-radar-api -e <pr-env> -- java -jar target/szczur-radar-api.jar create-admin --email …` pyta o hasło bez echa, tworzy admina, a admin loguje się w SPA PR env — ecdbd42
- [x] 3.5 Logi startu API w PR env pokazują rejestrację platformy Embabel bez błędów o brakującym LLM — ecdbd42

### Phase 4: Dokumentacja, weryfikacja w PR env i przełączenie produkcji

#### Automated

- [x] 4.1 `./mvnw -B verify` przechodzi
- [ ] 4.2 Cały workflow CI (backend, web-unit, web-container-smoke, railway-config) jest zielony na PR
- [x] 4.3 `grep` po aktywnych instrukcjach nie zwraca `uv run|uv sync|alembic|pytest|hypercorn|JWT_SECRET` (poza komentarzem o nieużywanym `preserve()`); pliki: `REPOSITORY_GUIDELINES.md`, `README.md`, `szczur-radar-api/README.md`, `.railway/railway.ts`, `.github/workflows/ci.yml`

#### Manual

- [ ] 4.4 PR env end-to-end: logowanie, ochrona głębokiego linku, wylogowanie, 403 dla obcego `Origin`, 429 po limicie, `X-Real-IP` liczy prawdziwy adres i nie da się go podrobić
- [ ] 4.5 Zimny start API po uśpieniu w PR env zmierzony i zapisany w `deploy-plan.md`
- [ ] 4.6 Operator wyczyścił schemat bazy produkcyjnej przed merge, PITR był włączony
- [ ] 4.7 Po merge przez człowieka pre-deploy na produkcji zastosował `V1` i `V2`, a `/api/health` i `/api/ready` na publicznym adresie zwracają 200
- [ ] 4.8 Admin produkcyjny utworzony przez `create-admin` loguje się na publicznym adresie; issue F-03 zamknięte z podsumowaniem dopiero po tej weryfikacji
