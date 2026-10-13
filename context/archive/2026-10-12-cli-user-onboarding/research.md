---
date: 2026-10-12T17:02:12+02:00
researcher: Claude (Opus 5.5) for kwmacioszek
git_commit: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662
branch: feature/cli-user-onboarding
repository: szczur-radar
topic: "S-07 cli-user-onboarding — co w kodzie już wspiera tworzenie kont przez CLI, aktywację linkiem i recovery admina, czego brakuje i co wiąże projekt"
tags: [research, auth, cli, spring-security, spring-session, activation-link, recovery, spa, railway]
status: complete
last_updated: 2026-10-12
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Follow-up: decyzje użytkownika — link ważny domyślnie 12 h (parametr), recovery = reset przez link dla każdego konta. Zastępuje Open Questions 1 i 2."
---

# Research: S-07 cli-user-onboarding

**Date**: 2026-10-12T17:02:12+02:00
**Researcher**: Claude (Opus 5.5) for kwmacioszek
**Git Commit**: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662 (= `origin/main` w chwili badania)
**Branch**: feature/cli-user-onboarding
**Repository**: szczur-radar

Kod backendu (`user`, `auth`, `cli`, `config`, migracje V1/V2, testy auth/cli) przeczytał agent główny. Frontend (`szczur-radar-web/`) i historię decyzji (`context/archive/**`, `context/foundation/**`, `deploy-plan.md`, README API, `tools/db-backup/`) zbadali dwaj pracownicy tylko do odczytu; ich rozstrzygające kotwice zostały sprawdzone ponownie (router, Caddyfile, `auth.ts`, `styleContract.test.ts`, `mock-api.js`, `LoginView.vue`, `java-api-rewrite/research.md:253`, `plan.md:49-50`, `backup.sh`, `restore-check.sh`, `MigrationsIT.java:37`, `roadmap.md:263`). Testów ani buildu nie uruchamiano.

## Research Question

`change.md` nie ma opisu zamiaru, więc pytanie wynika z roadmapy. S-07 (`context/foundation/roadmap.md:237-248`): „admin może przez CLI utworzyć konto z rolą i dostać jednorazowy, wygasający link aktywacyjny; zaproszony może raz ustawić hasło i się zalogować; operator może wygenerować nowy link (unieważniając poprzedni) i odzyskać dostęp admina tym samym mechanizmem”. PRD: US-02 (`prd.md:67-76`), FR-018 (`prd.md:169`), FR-027 (`prd.md:196`), FR-028 (`prd.md:199`), Access Control (`prd.md:268`). Wszystkie cztery są `must-have`.

Pytania:
1. Co w obecnym kodzie już przygotowano pod S-07 i na czym można budować?
2. Czego brakuje (backend, schemat, SPA, konfiguracja wdrożenia)?
3. Jakie wcześniejsze decyzje i ograniczenia techniczne wiążą projekt (sesje, CSRF/Origin, trasy publiczne, kontekst CLI, backup, logi)?
4. Jakie decyzje produktowe są nadal otwarte przed `/10x-plan`?

## Summary

1. **Fundament jest gotowy po stronie konta i logowania.**
   - `users.password_hash` jest `NULL`-owalne (`V1__create_users.sql:6`), a encja opisuje to jako „activation pending, S-07” (`User.java:30`).
   - Konto bez hasła nie może się zalogować i jest nieodróżnialne od złego hasła: dostaje nieużywalny hash i pełną weryfikację argon2 (`AccountDetailsService.java:21-24,43`). Pokrywa to test `AuthIT.accountWithoutPasswordCannotLogIn` (`AuthIT.java:119-120`).
   - Polityka haseł 12–128 znaków (code points) i argon2id są w jednym miejscu, `Passwords` (`Passwords.java:12-38`), a jego javadoc już wymienia „S-07 (activation, recovery)” (`Passwords.java:7-8`). Enkoder istnieje także w kontekście komend CLI (`PasswordConfig.java:9-19`).
   - Szkielet CLI jest gotowy na nowe komendy: enum `CommandType` (`CommandType.java:7-11`), interfejs `Command` (`Command.java:4-15`) i rejestracja w `CommandLine.standard()` (`CommandLine.java:32-34`). Wzorzec transakcji z blokadą doradczą Postgresa jest w `AdminBootstrap` (`AdminBootstrap.java:56-83`).
2. **Nie istnieje nic z mechanizmu linku.** Brakuje:
   - tabeli lub kolumn tokenów: migracje kończą się na V5, a `MigrationsIT` sprawdza dokładnie wersje `1`–`5` (`MigrationsIT.java:37`);
   - zapisu hasła na istniejącym koncie: `User` nie ma setterów (`User.java:45-71`);
   - publicznej trasy API: `PUBLIC_ROUTES` ma 4 wpisy (`AuthorizationCompletenessIT.java:51-55`);
   - widoku w SPA: pracownik nie znalazł w `szczur-radar-web/src` ani `dev/` żadnego trafienia dla activat/invite/set.password/onboard;
   - ustawienia publicznego adresu aplikacji, z którego CLI mogłoby złożyć link (sekcja „Wdrożenie”);
   - komendy recovery. Komunikat `create-admin` odsyła do „the recovery command (S-07)” (`AdminBootstrap.java:60`).
3. **Komendy CLI nie mają repozytorium sesji.**
   - Komendy startują kontekst z `WebApplicationType.NONE` (`SzczurRadarApiApplication.java:36-40`).
   - `JdbcSessionAutoConfiguration` (spring-boot-session-jdbc 4.0.8) ma na klasie `@ConditionalOnWebApplication(type = SERVLET)` (sprawdzone `javap` na jarze z `~/.m2`).
   - Dlatego zaplanowane `findByPrincipalName` (`tech-stack-api.md:32`; wzorzec w `AuthIT.java:282-297`) działa tylko w procesie serwera. Komenda CLI, która ma unieważnić sesje (np. recovery admina), musi albo usunąć wiersze `spring_session` po `principal_name` = UUID użytkownika (indeks `SPRING_SESSION_IX3`, `V2__create_spring_session.sql:11,17`), albo zostawić unieważnienie serwerowi przy użyciu linku.
   - Uruchomienie komendy w trakcie przebiegu pipeline'u go nie przerywa. Odzyskiwanie przy starcie działa tylko w serwerze (`PipelineStartupRecovery.java:25-32`). Zamknięcie kontekstu CLI przerywa tylko przebiegi uruchomione przez ten proces (`PipelineRunService.java:185-196`), a CLI żadnych nie uruchamia.
4. **Spring Security One-Time Token (OTT), wskazany „do oceny w S-07” (`java-api-rewrite/research.md:253`), słabo pasuje do wymagań.** Sprawdzone w `spring-security-core-7.0.7.jar`:
   - `JdbcOneTimeTokenService` trzyma token jawnie (`token_value varchar(36)` jako klucz główny);
   - `generate` wykonuje tylko `INSERT`, a jedyne `DELETE` są po `token_value` i po `expires_at`. Unieważnienie poprzedniego linku dla tego samego konta (FR-028) wymagałoby własnego kodu;
   - jawne tokeny trafiłyby do dziennego `pg_dump`, który pomija tylko `public.spring_session*` (`tools/db-backup/backup.sh:16-18`).
   - Z wiedzy o OTT, niesprawdzone w kodzie: zużycie tokenu **loguje** użytkownika przez filtr `oneTimeTokenLogin()` zamiast ustawiać hasło (FR-018), a domyślna ważność to 5 min (`research.md:253`).
5. **Token w URL trafi do logów proxy, jeśli nie będzie we fragmencie (`#`).**
   - Caddy loguje każde żądanie w JSON bez filtrów (`Caddyfile:5-7,12-14`). Nie ma też `Referrer-Policy` (ani w Caddyfile, ani w `index.html`).
   - Strażnik routera woła `ensureSession()` → `GET /api/auth/me` przy każdej nawigacji, także na trasach publicznych (`router/index.ts:49-50`).
   - Z wiedzy o Caddy, niesprawdzone na działającym kontenerze: token w ścieżce lub query trafi do `request.uri` w logu, a przez nagłówek `Referer` także do logów kolejnych żądań `/api/*` (Caddy domyślnie redaguje Cookie i Authorization, ale nie Referer).
   - Fragment URL nie jest wysyłany do serwera ani w `Referer`. To decyzja projektowa dla planu.
6. **Frontend ma jeden mechanizm tras publicznych: `meta.public`** (`router/index.ts:18-25,28`).
   - Strażnik przekierowuje zalogowanego użytkownika tylko z trasy o nazwie `login` (`router/index.ts:54-56`). Na innej trasie publicznej zalogowany zostaje.
   - `LoginView` nie używa `AppButton` ani `StatusMessage`: ma własny przycisk i `<p role="alert">` (`LoginView.vue:49,64`). Nowy widok musi użyć współdzielonych komponentów i trafić na listę skanu „no literals” (`styleContract.test.ts:2-11,82`).
   - `dev/mock-api.js` zawsze zwraca zalogowanego admina (`mock-api.js:44`), więc zrzuty ekranu widoku anonimowego wymagają zmiany mocka.
7. **Otwarte decyzje produktowe** (szczegóły w Open Questions). *Czas ważności linku i recovery rozstrzygnięte 2026-10-12, patrz „Follow-up 2026-10-12”.*
   - czas ważności linku (`roadmap.md:246`, owner: user);
   - semantyka recovery: FR-028 dopuszcza nowy link tylko dla „jeszcze nieaktywowanego konta” (`prd.md:199`), a roadmapa chce recovery admina „tym samym mechanizmem” (`roadmap.md:239`), czyli linkiem dla konta już aktywnego;
   - czy ustawienie hasła od razu loguje;
   - źródło adresu bazowego linku;
   - limit prób na publicznym endpoincie;
   - sposób przechowywania tokenu.

## Detailed Findings

### Model konta i schemat

- Tabela `users`: `id UUID`, `email TEXT` (unikalny, wymuszone małe litery), `password_hash TEXT NULL`, `role TEXT DEFAULT 'user'` z `CHECK (role IN ('user','admin'))`, `created_at` (`V1__create_users.sql:3-13`). `MigrationsIT.usersDefaultsAndNoTokenVersion` sprawdza domyślne wartości i brak kolumny `token_version` (`MigrationsIT.java:47-61`).
- `User` jest niezmienny poza konstruktorem: ma gettery, nie ma setterów (`User.java:45-71`). Ustawienie hasła wymaga nowej metody encji albo warunkowego `UPDATE` przez `JdbcClient`. Repozytorium zna oba style: JPA (`UserRepository.java:9-17`) i warunkowe zapisy stanu w `pipeline/state/` (`REPOSITORY_GUIDELINES.md`).
- `UserRepository` ma `findByEmail`, `existsByEmail` i `existsByRole` (`UserRepository.java:11-15`); nie ma zapytań o konta bez hasła.
- `Role` to enum `USER`/`ADMIN` z wartością w bazie `user`/`admin` (`Role.java:13-29`). `Role.fromValue` rzuca `IllegalArgumentException` dla innej wartości, co przyda się przy parsowaniu `--role` w CLI.
- Niespójność z konwencją: `RoleConverter` leży w `user/` (`RoleConverter.java:7-8`), a `REPOSITORY_GUIDELINES.md` mówi „every enum converter in `persistence/converters/`” (pozostałe 17 konwerterów tam jest, wzorzec `EnumValueConverter`). Drobne; zostało sprzed tej reguły.
- E-mail: `Emails.normalize` (strip + lower) i `MAX_EMAIL_LENGTH = 320` (`Emails.java:12-20`). CLI i API muszą walidować go tak samo, co zapisano jako lekcję z przeglądu S-01 (raport historii: `java-api-rewrite/plan.md:202`).

### Logowanie, sesje i ochrona tras

- Logowanie: `POST /api/auth/login` (`AuthController.java:72-108`).
  - Limiter rezerwuje próbę przed uwierzytelnieniem.
  - Każda porażka to 401 `invalid_credentials`.
  - Sukces zmienia id istniejącej sesji i zapisuje kontekst przez `SecurityContextRepository` (`AuthController.java:136-145`).
- Principal sesji to UUID użytkownika, nie e-mail, bo `PRINCIPAL_NAME` ma `VARCHAR(100)` (`AccountDetailsService.java:18-19`, `V2__create_spring_session.sql:11`).
- Unieważnianie sesji: test `deletingTheUsersSessionsRevokesThem` opisuje „How S-07 will revoke access: find the user's sessions by principal (= user id) and delete them” (`AuthIT.java:282-297`). Działa przez wstrzyknięte `FindByIndexNameSessionRepository` (`AuthIT.java:76`), które w kontekście CLI nie istnieje (Summary 3).
- Ciasteczko `sr_session`: `HttpOnly; Secure; SameSite=Strict; Path=/api`, `Max-Age` = 30 dni, odnawiane po udanych uwierzytelnionych odpowiedziach (`SessionConfig.java:15-44`, `SessionCookieRenewalFilter.java:19-31`).
  - Renewal pomija żądania anonimowe (`SessionCookieRenewalFilter.java:88-90`), więc anonimowy endpoint aktywacji nie odnowi ciasteczka.
  - Nie może też tworzyć sesji, jeśli projekt nie przewiduje logowania po aktywacji (`NullRequestCache`, `SecurityConfig.java:63-64`).
- Ochrona tras: deny-by-default (`SecurityConfig.java:52-58`).
  - Nowa publiczna trasa wymaga `permitAll` w `SecurityConfig` **i** wpisu z uzasadnieniem w `PUBLIC_ROUTES` (`AuthorizationCompletenessIT.java:37-40,51-55`).
  - Test wywołuje każdą zmapowaną trasę bez sesji i oczekuje 401 (`AuthorizationCompletenessIT.java:84-107`).
- CSRF: tokeny Springa są wyłączone; obrona to `SameSite=Strict` plus `OriginGuardFilter` (`SecurityConfig.java:37-39,59`).
  - Filtr odrzuca POST/PUT/PATCH/DELETE pod `/api/**` z obcym lub `null` `Origin` (403 `cross_origin`) i przepuszcza żądania bez `Origin` (`OriginGuardFilter.java:20-52`).
  - Działa przed uwierzytelnieniem, więc obejmie też publiczny POST aktywacji wysłany z SPA.
- Limiter prób (`LoginRateLimiter.java:15-36`): 5 porażek na 15 min na e-mail, 20 na IP, w procesie, poprawny przy jednej replice. Klucz IP to `X-Real-IP` (`ClientIp.java:5-21`). Obejmuje wyłącznie `/login`, a w historii nie ma decyzji o limicie dla aktywacji.
- Błędy: body `{"detail":"<code>"}` z enuma `ErrorCode`, który ma 10 stałych (`ErrorCode.java:13-43`, `ApiError.java:9-28`). Kody błędów aktywacji (np. nieważny lub wygasły link) będą nowymi stałymi. Nieznane pola JSON → 400 `invalid_request` (`application.properties:37-38`, `ApiExceptionHandler.java:21-25`).
- `REPOSITORY_GUIDELINES.md` (§ Deployment) wymaga, by plan przed implementacją uwierzytelniania opisał transport, przechowywanie sesji lub tokenu i obsługę CSRF.

### Komendy CLI

- Dispatcher: `main` z argumentami → `CommandLine.standard().run(args)` → `System.exit(code)` (`SzczurRadarApiApplication.java:23-29`). Nieznana komenda kończy się kodem 2 i usage na stderr (`CommandLine.java:41-61`). `CommandLineTest.standardRegistersEveryCommandType` pilnuje, by każda stała enuma miała implementację (`CommandLineTest.java:65`).
- `create-admin` (`CreateAdminCommand.java:14-89`):
  - Najpierw walidacja bez bazy: `--email`, `@`, ≤ 320 znaków, zmienne `PG*`, interaktywna konsola. Błąd → kod 2.
  - Potem kontekst Springa i `AdminBootstrap.createAdmin`.
  - Hasło pochodzi z ukrytego promptu (`PasswordPrompt.java:14-24`), nigdy z argumentu.
  - Wyjście: `Created admin <email>` na stdout (`AdminBootstrap.java:84-86`), kody 0/1/2.
  - Zależności wstrzykiwane w konstruktorze pakietowym (`CreateAdminCommand.java:45-52`) pozwalają testować bez Springa (`CreateAdminCommandTest.java:32-71`) i z Testcontainers (`CreateAdminCommandIT.java:42-202`).
- `AdminBootstrap` w jednej transakcji z `pg_advisory_xact_lock(LOCK_ID)` odmawia, gdy istnieje jakikolwiek admin albo e-mail jest zajęty (`AdminBootstrap.java:57-66`). Blokada służy tylko wyścigowi bootstrapu. Przegląd S-01 zauważa, że zostawia to możliwość wielu adminów w S-07 (raport historii: `admin-bootstrap-and-login/reviews/impl-review-rereview-2026-09-29.md:42`).
- Komenda tworząca konto z linkiem nie potrzebuje ukrytego promptu (nie zna hasła). Może więc działać nieinteraktywnie, np. `railway ssh … -- java -jar … <komenda>`. Wtedy link trafia na terminal operatora albo do transkryptu agenta. Precedens ostrożności: `JWT_SECRET` zrotowano po wyświetleniu w sesji agenta (`deploy-plan.md:36`).
- Każda komenda startuje pełny kontekst Springa, więc wymaga też trzech zmiennych `LLM_*` (`REPOSITORY_GUIDELINES.md`, § Build; README API, wg raportu historii `:144`).

### Spring Security One-Time Token: ocena

- `java-api-rewrite/research.md:253` wymienia OTT (`oneTimeTokenLogin()`, `JdbcOneTimeTokenService`, „domyślna ważność 5 min, konfigurowalna”) ze statusem „do oceny w S-07”. Decyzji nie podjęto.
- Sprawdzone w `spring-security-core-7.0.7.jar` (`org/springframework/security/core/ott/jdbc/one-time-tokens-schema.sql` i stałe SQL w `JdbcOneTimeTokenService.class`):
  - schemat: `token_value varchar(36) not null primary key`, `username varchar_ignorecase(50)`, `expires_at timestamp`;
  - SQL: `INSERT … (token_value, username, expires_at)`, `SELECT … WHERE token_value = ?`, `DELETE … WHERE token_value = ?`, `DELETE … WHERE expires_at < ?`. Nie ma zapytania po `username`, więc nowy token nie unieważnia poprzedniego;
  - klasa ma `ThreadPoolTaskScheduler` z cronem czyszczenia (`setCleanupCron`). W kontekście CLI uruchomiłby się planista, chyba że bean powstaje tylko w serwerze.
- Wniosek (inferencja): OTT dostarcza „magic link login”, a S-07 potrzebuje „link → ustaw hasło raz”, z unieważnieniem poprzedniego linku i bez jawnych tokenów w kopii bazy. Użycie OTT wymagałoby obudowania go własnym kodem w kilku miejscach. Własna tabela z hashem tokenu (`persistence/Sha256` już istnieje, `Sha256.java:8-27`) to alternatywa do rozważenia w planie.

### Backup a dane tokenów

- Dzienny `pg_dump` wyklucza tylko dane `public.spring_session*`, z uzasadnieniem: „their ids would let whoever holds a dump take over a live session” (`tools/db-backup/backup.sh:16-18`). Ten sam argument dotyczy jawnych tokenów aktywacji.
- `COUNTED_TABLES=(users pipeline_runs posts assessments raw_payloads)` z założeniem „none of these tables has a delete path in the application” (`backup.sh:19-21`). Tabela tokenów z usuwaniem nie może trafić na tę listę. `users` nadal nie ma ścieżki usuwania, bo usuwanie kont jest non-goal (`prd.md:293`).
- `restore-check.sh` ma stałą listę `REQUIRED_TABLES` (`restore-check.sh:12-13`). Nowa tabela nie jest tam wymagana, ale plan może zdecydować, czy ją dodać.

### Frontend (SPA)

- Router (`szczur-radar-web/src/router/index.ts`):
  - trasy `/login` (`meta.public`), `/`, `/admin` (`meta.admin`), `/kitchen-sink` (tylko DEV, public) i catch-all → `/` (`:27-44`);
  - strażnik (`:49-61`) woła `ensureSession()`, chronioną trasę bez sesji przekierowuje do `login?redirect=…`, zalogowanego z `login` do `/`, a nie-admina z `meta.admin` do `/`;
  - przy 401 w trakcie pracy przekierowuje tylko z tras niepublicznych (`:64-69`);
  - `safeRedirect` przyjmuje wyłącznie ścieżki w obrębie strony (`router/redirect.ts:1-15`).
- Klient API: błędy to `ApiError(status, detail, retryAfter)`, a `detail` jest zwykłym `string`. Nie ma wspólnej unii kodów; mapowanie na polskie teksty robi każdy wywołujący (raport: `client.ts:5-54`, `pipeline.ts:72-86`). `auth.ts` ma `login`, `logout` i `fetchMe` (`auth.ts:11-27`). Komentarz `auth.ts:1` wciąż wskazuje na Pythonowe `app/auth/routes.py`.
- `LoginView.vue`:
  - formularz `novalidate` (`:49`), błędy mapowane po statusie HTTP (`:18-27`), `autocomplete="username"` i `current-password` (`:52,60`), komunikat w `<p role="alert">` (`:64`);
  - copy po polsku, pełne zdania, wielokropek w stanie zajętości (raport).
  - Widok ustawiania hasła będzie potrzebował `autocomplete="new-password"` i walidacji 12–128 zgodnej z `Passwords` (z inferencji).
- Testy:
  - `vi.mock('../api/auth', …)` z fabryką `{fetchMe, login, logout}` jest w 4 plikach: `router/index.test.ts:8-12`, `LoginView.test.ts:10-14`, `AdminView.test.ts:12`, `FeedView.test.ts:10`. Nowy eksport z `auth.ts` trzeba dopisać do tych fabryk, jeśli moduł go użyje przy imporcie;
  - `client.test.ts` stubuje `fetch` (raport).
- Kontrakt UI:
  - skan „no literals” obejmuje pliki importowane w `styleContract.test.ts:2-11`, z regułą „Add a view or component here once it is cleaned” (`:82`);
  - kitchen sink to `src/views/KitchenSinkView.vue`;
  - `dev/mock-api.js` przechwytuje tylko `/api/*` (`:41`), `/api/auth/me` zawsze zwraca admina (`:44`), a reszta dostaje 404 `not_found` (`:51`).
  - `CLAUDE.md`: pierwszy raz tworzony widok idzie zwykłym łańcuchem, nie przez `/10x-ui`.
- Dostarczanie SPA: Caddy serwuje `index.html` dla nieznanych ścieżek (`Caddyfile:36-41`), więc `/activate…` w każdej formie trafi do SPA. Vite dev zachowuje się tak samo (raport: Vite 8.3.1, sprawdzone w `node_modules` głównego checkoutu).

### Wdrożenie: adres bazowy linku i uruchamianie CLI

- **Brak ustawienia publicznego adresu.**
  - Środowisko API w `.railway/railway.ts` (`:91-143`, wg raportu) nie ma żadnej zmiennej URL. Web dostaje tylko `API_PRIVATE_DOMAIN: api.env.RAILWAY_PRIVATE_DOMAIN` (`railway.ts:161`), co jest precedensem referencji między usługami.
  - `application.properties` nie ma właściwości URL.
  - Domena produkcji `szczur-radar-web-production.up.railway.app` została wygenerowana ręcznie (`deploy-plan.md`, faza 3). PR env ma `szczur-radar-web-szczur-radar-pr-<N>.up.railway.app` (faza 5). Lokalnie jest `http://localhost:5173`.
  - Niesprawdzone: czy IaC pozwala zreferować `web.env.RAILWAY_PUBLIC_DOMAIN` w API i czy rozwiązuje się w PR env.
- `railway ssh` (`deploy-plan.md`, sekcja F-03 „Operator commands”):
  - najpierw obudzić API (`curl …/api/health`);
  - powłoka działa jako root;
  - ukryty prompt wymaga prawdziwego terminala, nie `!` agenta;
  - e-mail bez `< >`;
  - w niepodlinkowanym checkoucie trzeba poprzedzić komendy `RAILWAY_PROJECT_ID=…`.
- Niezbadane: czy wyjście sesji `railway ssh` trafia do logów usługi (retencja 7 dni na Hobby, `infrastructure.md` wg raportu).
- Lekcja: kroki produkcyjne po merge'u, np. założenie prawdziwych kont, idą do runbooka w `deploy-plan.md` i komentarzy w issue, nie do Progress (`lessons.md`, „Implementacja, przegląd i archiwizacja na jednej gałęzi”).

## Code References

- `szczur-radar-api/src/main/resources/db/migration/V1__create_users.sql:3-13` — tabela `users`, `password_hash NULL`
- `szczur-radar-api/src/main/resources/db/migration/V2__create_spring_session.sql:4-17` — sesje JDBC, `PRINCIPAL_NAME` + indeks IX3
- `szczur-radar-api/src/main/java/pl/szczurradar/api/user/User.java:30,45-71` — encja bez setterów; komentarz S-07
- `szczur-radar-api/src/main/java/pl/szczurradar/api/user/UserRepository.java:9-17`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/user/Role.java:13-29`, `RoleConverter.java:7-8`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/Passwords.java:7-38` — polityka 12–128, argon2id
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/AccountDetailsService.java:15-48` — konto bez hasła = nieużywalny hash
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/AuthController.java:72-145` — login, logout, me, start sesji
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/LoginRateLimiter.java:15-36` — limiter tylko dla logowania
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/OriginGuardFilter.java:20-52` — obrona przed cross-origin
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java:32-72` — deny-by-default, trasy publiczne
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ErrorCode.java:13-43` — 10 kodów błędów
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SessionConfig.java:15-56` — ciasteczko `sr_session`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/SzczurRadarApiApplication.java:23-40` — tryb komend bez serwera WWW
- `szczur-radar-api/src/main/java/pl/szczurradar/api/cli/CommandType.java:7-11`, `CommandLine.java:32-61`, `Command.java:4-15`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/cli/CreateAdminCommand.java:14-104`, `AdminBootstrap.java:15-88`, `PasswordPrompt.java:7-24`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/persistence/Sha256.java:8-27` — hash hex SHA-256
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStartupRecovery.java:25-32` — recovery tylko w serwerze
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthIT.java:119-130,282-297` — konto bez hasła; wzorzec unieważniania sesji
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java:51-63` — `PUBLIC_ROUTES`, `ADMIN_ROUTES`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/MigrationsIT.java:37,47-61` — dokładna lista wersji, domyślne wartości `users`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/cli/CreateAdminCommandIT.java`, `CreateAdminCommandTest.java`, `CommandLineTest.java:65`
- `szczur-radar-web/src/router/index.ts:18-69`, `src/router/redirect.ts:1-15`
- `szczur-radar-web/src/api/auth.ts:1-27`, `src/views/LoginView.vue:18-64`
- `szczur-radar-web/src/styleContract.test.ts:2-11,82,98`, `dev/mock-api.js:41-51`
- `szczur-radar-web/Caddyfile:5-14,29-41`
- `tools/db-backup/backup.sh:16-21`, `tools/db-backup/restore-check.sh:12-13`
- `.railway/railway.ts:91,153-161`

## Architecture Insights

- **Jeden jar, dwa tryby.** Komendy operatora dzielą konfigurację z serwerem, ale nie mają beanów warunkowych na aplikację webową: łańcucha filtrów Security, repozytorium Spring Session, recovery pipeline'u. Komenda S-07 może więc używać JPA, `JdbcClient`, `TransactionTemplate` i `PasswordEncoder`, ale nie `FindByIndexNameSessionRepository`.
- **Testowalność komend.** Logika żyje w beanie `@Service` (`AdminBootstrap`), a komenda tylko waliduje wejście i startuje kontekst. Wejście i wyjście (`PrintStream`, prompt, starter kontekstu) jest wstrzykiwane. Testy dzielą się na `*Test` bez Springa i `*IT` z Testcontainers.
- **Zamknięte zbiory jako enumy.** `CommandType`, `ErrorCode` i `Role` to enumy (`lessons.md`: „Zamknięte zbiory wartości trzymaj w enumach”). Nowe komendy, kody błędów i ewentualny status tokenu powinny być enumami.
- **Bezpieczeństwo przez nieodróżnialność.** Logowanie nie zdradza, czy konto istnieje i czy ma hasło (`AccountDetailsService.java:21-24`). Publiczny endpoint aktywacji powinien analogicznie nie ujawniać e-maili ani stanu kont. NFR: brak wglądu w dane cudzych kont (`prd.md:250`).
- **Granica SPA ↔ API.** SPA rozmawia tylko z `/api/*` tego samego originu (`REPOSITORY_GUIDELINES.md`, Hard Rules). Strona aktywacji jest więc trasą SPA wołającą publiczny `POST /api/…`.

## Historical Context (from prior changes)

- `context/archive/2026-09-27-admin-bootstrap-and-login/plan.md:37` (S-01, Python): aktywację linkiem, konta `user` przez CLI, nowy link i recovery admina odłożono do S-07. Stan: **nadal obowiązuje**.
- `context/archive/2026-10-01-java-api-rewrite/plan.md:49`: `token_version` usunięty, „unieważnianie sesji przejmuje Spring Session (S-07 użyje `findByPrincipalName`)”. Stan: **częściowo**. Mechanizm obowiązuje w serwerze, ale w kontekście CLI repozytorium sesji nie istnieje (Summary 3).
- `context/archive/2026-10-01-java-api-rewrite/plan.md:50`: komendy S-07 poza zakresem F-03. **Nadal obowiązuje.**
- `context/archive/2026-10-01-java-api-rewrite/research.md:253`: OTT „do oceny w S-07”. Ocena jest w tym dokumencie (sekcja OTT). Decyzja należy do planu.
- `context/deployment/deploy-plan.md:35`: „Password recovery arrives with S-07 — until then enter it carefully.” **Nadal obowiązuje** (to zapowiedź FR-027).
- `context/foundation/shape-notes.md:24,32` (wg raportu): aktywacja jednorazowym linkiem, nie tymczasowym hasłem. CLI to „narzędzie awaryjne/operatorskie, celowo NIE rozszerzane o pełny CRUD/listing/permissions userów”, więc bez komend listowania, usuwania czy zmiany roli. **Obowiązuje** (zgodne z `prd.md:293`).
- Decyzje z S-01 (JWT, `token_version`, pwdlib argon2 m=65536/t=3/p=4, 30 dni sesji stałej) **zastąpione** przez F-03: Spring Session JDBC, 30 dni bezczynności z odnawianiem, `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (`tech-stack-api.md:31-32`, `Passwords.java:19-22`).
- `context/foundation/roadmap.md:263` (Backlog Handoff): S-07 „Ready for /10x-plan: no”. **Nieaktualne**: oba warunki wstępne (S-01, F-03) są `done` (`roadmap.md:66,68`).

## Related Research

- `context/archive/2026-10-01-java-api-rewrite/research.md`: wybór Spring Security + Spring Session JDBC i wzmianka o OTT.
- `context/archive/2026-10-11-production-data-gate/research.md`: backup `pg_dump`, wykluczenie danych sesji.

## Open Questions

Decyzje dla użytkownika lub planu (bez nich plan nie może się zamknąć):

1. ~~**Czas ważności linku**: PRD podaje „np. 24h” (`prd.md:74`); unknown w roadmapie (`roadmap.md:246`, owner: user).~~ **Rozstrzygnięte 2026-10-12**, patrz „Follow-up 2026-10-12”.
2. ~~**Recovery admina (FR-027).**~~ **Rozstrzygnięte 2026-10-12** (wariant a, dla każdego konta), patrz „Follow-up 2026-10-12”. Pierwotne opcje:
   - (a) nowy link dla dowolnego konta, także aktywnego, co jest zgodne z roadmapą (`roadmap.md:239`), ale wykracza poza literę FR-028 (`prd.md:199`);
   - (b) osobna komenda resetu hasła admina z ukrytym promptem, analogiczna do `create-admin`.

   Przy (a) trzeba też ustalić, czy stare hasło i sesje tracą ważność już przy wygenerowaniu linku, czy dopiero przy jego użyciu. **Rozbieżność PRD ↔ roadmapa do zgłoszenia, nie do cichego rozstrzygnięcia w kodzie** (`REPOSITORY_GUIDELINES.md`, Hard Rules).
3. **Czy ustawienie hasła loguje od razu**, czy kieruje na `/login`. FR-018 („ustawia własne hasło przy pierwszym logowaniu za pomocą linku”) dopuszcza oba.
4. **Czy komenda tworzenia konta przyjmuje `--role admin`.** PRD tego wymaga: konta z rolą `user` lub `admin` (`prd.md:268`). Do ustalenia, czy `create-admin` zostaje tylko bootstrapem.
5. **Źródło adresu bazowego linku**: zmienna środowiskowa (np. referencja do domeny web), argument CLI czy wypisanie samej ścieżki do sklejenia przez operatora.
6. **Forma tokenu w URL**: fragment (`#`), query albo ścieżka (logi Caddy i Referer, Summary 5). Do tego przechowywanie: hash czy jawnie (backup, Summary 4).
7. **Limit prób na publicznym endpoincie aktywacji**: brak decyzji w historii. Przy tokenie o wysokiej entropii zgadywanie jest nieopłacalne, ale endpoint też wykonuje argon2 przy zapisie.

Luki dowodowe (nie blokują planu, ale wpływają na wybór w pyt. 5–6):

- Pola logu dostępu Caddy (pełne `uri` z query, nieredagowany `Referer`) opisano z dokumentacji, a nie sprawdzono na działającym kontenerze; logów brzegowych Railway nie badano.
- Nie wiadomo, czy wyjście `railway ssh` trafia do logów usługi.
- Niesprawdzone, czy `web.env.RAILWAY_PUBLIC_DOMAIN` da się zreferować w `.railway/railway.ts` i czy rozwiązuje się w PR env.
- Semantyka OTT „zużycie = logowanie” pochodzi z wiedzy o Spring Security i `research.md:253`. Bajtkod potwierdził tylko schemat i SQL.

## Follow-up 2026-10-12: decyzje użytkownika

Zastępuje Open Questions 1 i 2. Pozostałe pytania (3–7) są nadal otwarte.

### Czas ważności linku

- **Domyślnie 12 h, parametryzowane** (decyzja użytkownika). Mieści się w „np. 24h” z PRD (`prd.md:74`), więc nie jest sprzeczne z PRD. Zamyka unknown z `roadmap.md:246`.
- Wskazówki dla planu (inferencja, nie decyzja):
  - Link generuje komenda CLI, a ta działa w tym samym kontenerze i z tymi samymi zmiennymi co API. Wystarczy więc jedna właściwość czytana przy generowaniu, np. ze zmiennej środowiskowej usługi `szczur-radar-api`. Nazwę ustala plan.
  - Wartość jako `Duration` z jednostką (`12h`). Spring czyta liczbę bez jednostki jako milisekundy, co już raz zepsuło `LLM_TIMEOUT=120` (`deploy-plan.md`, Troubleshooting (Java), „Rule for variables”). Plan powinien to walidować przy starcie, jak `LLM_*`.

### Recovery dostępu (FR-027)

Decyzja użytkownika: **reset przez link, dla każdego konta.** Jedna komenda (nazwa robocza `reset-password --email <e-mail>`) przywraca konto do stanu „czeka na aktywację”:

1. kasuje hasło (`password_hash = NULL`), więc starym hasłem nie da się już zalogować, a login nadal odpowiada 401 `invalid_credentials` bez zdradzania stanu konta (`AccountDetailsService.java:21-24,43`);
2. usuwa wszystkie sesje konta bezpośrednio w bazie (`spring_session` po `principal_name` = UUID). Kontekst CLI nie ma `FindByIndexNameSessionRepository` (Summary 3);
3. unieważnia poprzedni link konta i wypisuje nowy.

Konto po resecie ma ten sam stan co nowo utworzone, więc aktywacja (FR-018), nowy link dla nieaktywowanego konta (FR-028) i recovery (FR-027) to jedna ścieżka: jedna tabela tokenów, jeden publiczny endpoint i jeden widok SPA.

Konsekwencje do uwzględnienia w planie:
- Do użycia linku konto jest zablokowane. Dotyczy to też jedynego admina, który odzyskuje dostęp: przechodzi przez link sam.
- `create-admin` może zostać bootstrapem (FR-026); jego komunikat odmowy wskazuje „recovery command (S-07)” (`AdminBootstrap.java:60`), więc warto go zaktualizować na nazwę nowej komendy.
- **Zakres szerszy niż litera PRD — do odnotowania w planie:**
  - FR-027 mówi o koncie administratora (`prd.md:196`), FR-028 o nowym linku dla konta „jeszcze nieaktywowanego” (`prd.md:199`). Reset aktywnego konta `user` przez operatora wykracza poza oba.
  - Nie jest to samodzielny reset hasła z non-goals (`prd.md:281,293`), bo wykonuje go operator przez CLI.
  - Roadmapa już zakłada „ten sam mechanizm” (`roadmap.md:239`). Plan powinien to jawnie zapisać i zdecydować, czy dopisać doprecyzowanie do PRD, zamiast rozstrzygać to po cichu w kodzie (`REPOSITORY_GUIDELINES.md`, Hard Rules).
