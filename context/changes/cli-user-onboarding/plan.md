# S-07 cli-user-onboarding — plan implementacji

## Overview

S-07 daje operatorowi dwie komendy CLI, które wypisują jednorazowy, wygasający link do ustawienia hasła:

- `create-user --email <e-mail> [--role user|admin]` zakłada konto bez hasła;
- `reset-password --email <e-mail>` przywraca dowolne istniejące konto do stanu „czeka na aktywację”: kasuje hasło, usuwa sesje konta i wydaje nowy link, unieważniając poprzedni.

Zaproszony (albo admin odzyskujący dostęp) otwiera link w SPA. Strona sprawdza link, pokazuje e-mail konta i pozwala raz ustawić hasło. Potem użytkownik loguje się zwykłym `/login`. Pokrywa to US-02, FR-018, FR-027 i FR-028 (wszystkie `must-have`) bez wysyłki e-maili.

## Current State Analysis

Źródło: `context/changes/cli-user-onboarding/research.md` (w tym „Follow-up 2026-10-12”).

- **Konto i logowanie są gotowe:**
  - `users.password_hash` może być `NULL` (`V1__create_users.sql:6`). Konto bez hasła nie zaloguje się i wygląda jak złe hasło (`AccountDetailsService.java:21-24,43`; test `AuthIT.java:119-130`).
  - Polityka haseł 12–128 znaków (code points) i argon2id są w `Passwords` (`Passwords.java:12-38`). `PasswordEncoder` istnieje też w kontekście komend (`PasswordConfig.java:9-19`).
- **Szkielet CLI:**
  - enum `CommandType` (`CommandType.java:7-11`), interfejs `Command`, rejestracja w `CommandLine.standard()` (`CommandLine.java:32-34`);
  - `create-admin` waliduje wejście przed bazą i wstrzykuje środowisko, prompt i starter kontekstu (`CreateAdminCommand.java:45-89`);
  - `AdminBootstrap` robi całą pracę w jednej transakcji (`AdminBootstrap.java:56-83`).
- **Brakuje:** tabeli tokenów, zapisu hasła na istniejącym koncie (`User` nie ma setterów, `User.java:45-71`), publicznych tras (4 wpisy w `PUBLIC_ROUTES`, `AuthorizationCompletenessIT.java:51-55`), widoku SPA, adresu strony dla CLI i komendy recovery (komunikat `AdminBootstrap.java:60` odsyła do „recovery command (S-07)”).
- **Komendy CLI nie mają repozytorium sesji.** Startują z `WebApplicationType.NONE` (`SzczurRadarApiApplication.java:36-40`), a `JdbcSessionAutoConfiguration` wymaga aplikacji SERVLET. `findByPrincipalName` (wzorzec `AuthIT.java:282-297`) działa więc tylko w serwerze. Sesje konta z CLI usuwa się SQL-em: `spring_session.principal_name` = UUID konta (indeks IX3, `V2__create_spring_session.sql:11,17`), a atrybuty kasują się kaskadowo (`V2:24`).
- **Uruchomienie komendy nie przerywa przebiegu pipeline'u.** Recovery przebiegów działa tylko w serwerze (`PipelineStartupRecovery.java:25-32`).
- **Token w URL wyciekałby do logów:** Caddy loguje żądania w JSON bez filtrów (`Caddyfile:5-14`), a strażnik routera woła `/api/auth/me` przy każdej nawigacji (`router/index.ts:49-50`). Fragment (`#`) nie trafia do serwera ani do `Referer`.
- **Spring Security One-Time Token odrzucony.** Trzyma token jawnie, nie unieważnia poprzedniego tokenu konta i loguje użytkownika zamiast ustawiać hasło (research, sekcja OTT).
- **Adres strony.** Żadna zmienna go dziś nie niesie. Zwykły string `"https://${{szczur-radar-web.RAILWAY_PUBLIC_DOMAIN}}"` w `env` serwisu API przechodzi przez SDK bez zmian i Railway rozwiązuje go osobno w każdym środowisku (sprawdzone offline na SDK `railway@3.11.0` i w dokumentacji Railway „variables/reference#template-syntax”). Na żywo niesprawdzone; potwierdzi to faza 5.
- **SPA:**
  - trasy publiczne oznacza `meta.public` (`router/index.ts:18-25,28`);
  - `LoginView` nie używa `AppButton`/`StatusMessage` (`LoginView.vue:49,64`);
  - `dev/mock-api.js` zawsze zwraca zalogowanego admina (`mock-api.js:44`);
  - nowy widok powstaje zwykłym łańcuchem, nie przez `/10x-ui` (`CLAUDE.md`).

## Desired End State

- Operator w kontenerze API (`railway ssh`) uruchamia `create-user --email jan@example.com`. Dostaje konto z rolą `user` i link `https://<domena web>/activate#<token>` ważny 12 h (albo tyle, ile mówi `ACTIVATION_LINK_TTL`).
- Jan otwiera link. Strona pokazuje „Ustaw hasło dla jan@example.com”. Po ustawieniu hasła trafia na `/login` z komunikatem i wpisanym e-mailem i loguje się.
- Ponowne użycie linku, link wygasły albo link wydany przed nowszym pokazują „Link jest nieważny lub wygasł. Poproś administratora o nowy.”
- `reset-password --email admin@…` natychmiast wylogowuje wszystkie sesje admina i blokuje jego stare hasło. Nowy link pozwala ustawić nowe hasło.
- Weryfikacja: `./mvnw -B verify`, `npm test`, `npm run build` są zielone, a pełny przebieg (konto, link, hasło, logowanie, reset admina) jest sprawdzony w Railway PR env.

### Key Discoveries:

- Konto bez hasła jest już nieodróżnialne od złego hasła; reset tylko wraca do tego stanu (`AccountDetailsService.java:21-24`).
- `AuthorizationCompletenessIT` wywołuje każdą trasę bez sesji. Publiczna trasa wymaga `permitAll` **i** wpisu z uzasadnieniem (`AuthorizationCompletenessIT.java:37-40,84-107`).
- `OriginGuardFilter` obejmuje każdy POST pod `/api/**` przed uwierzytelnieniem (`OriginGuardFilter.java:20-52`), więc publiczne endpointy aktywacji dostają tę samą ochronę co login.
- `MigrationsIT` sprawdza dokładną listę wersji Flyway (`MigrationsIT.java:37`); V6 trzeba tam dopisać.
- Wartości `Duration` w Springu bez jednostki to milisekundy (`deploy-plan.md`, „Rule for variables”). `LlmProviderProperties` używa `DurationStyle.detectAndParse` (`LlmProviderProperties.java:175-190`), które przyjmuje gołą liczbę.
- Dzienny `pg_dump` pomija tylko `public.spring_session*` (`tools/db-backup/backup.sh:16-18`). Hash tokenu w kopii jest bezużyteczny bez tokenu.

## What We're NOT Doing

- Wysyłki e-maili, panelu kont w SPA, listowania, usuwania ani zmiany roli kont (FR-016/017/019 nice-to-have; `shape-notes.md:32`).
- Samodzielnego resetu hasła przez użytkownika (non-goal, `prd.md:281,293`). Reset robi wyłącznie operator przez CLI.
- Automatycznego logowania po ustawieniu hasła. Sesję tworzy tylko `/login` (decyzja 2026-10-12).
- Spring Security One-Time Token Login.
- Limitu prób na endpointach aktywacji. Token ma 256 bitów, a argon2 liczy się dopiero po zablokowaniu ważnego tokenu.
- Zadania czyszczącego wygasłe tokeny. Jeden wiersz na konto jest nadpisywany przy następnym linku, a zużyty jest kasowany.
- Zmian w `tools/db-backup/`. `COUNTED_TABLES` i `REQUIRED_TABLES` zostają, bo nowa tabela ma ścieżkę usuwania. Dodanie jej do `REQUIRED_TABLES` złamałoby też sprawdzanie starszych dumpów.
- Zmiany wyglądu `LoginView` poza komunikatem po aktywacji. Ewentualne utwardzenie UI logowania to osobna zmiana `/10x-ui`.
- Kroków produkcyjnych po merge'u w Progress. Są runbookiem w `deploy-plan.md` i komentarzami w issue S-07 (`lessons.md`).

## Implementation Approach

Kolejność: schemat → logika domenowa → CLI → API → SPA → wdrożenie (wzorzec „nowe funkcje” skilla). Każda faza kończy się zielonymi testami.

Proces (`REPOSITORY_GUIDELINES.md`, Workflow):

- gałąź `feature/cli-user-onboarding` od świeżego `origin/main`; to jest gałąź tego worktree, więc na starcie trzeba ją sprawdzić względem `origin/main`;
- issue roadmapy `S-07: …` z komentarzem o starcie;
- jeden PR z `Refs #<issue>`; folder zmiany idzie do pierwszego commitu.

Implementacja, weryfikacja w PR env, `/10x-impl-review` i `/10x-archive` dzieją się na tej jednej gałęzi; merge robi człowiek.

**Kontrakt uwierzytelniania** (wymagany przez `REPOSITORY_GUIDELINES.md`, § Deployment, przed implementacją):

- **Transport:**
  - link `<WEB_PUBLIC_URL>/activate#<token>`, gdzie token jest tylko we fragmencie adresu;
  - SPA wysyła token wyłącznie w body JSON:
    - `POST /api/auth/activation/lookup` `{token}` → 200 `{email}`;
    - `POST /api/auth/activation` `{token, password}` → 204.
  - Obie trasy są publiczne, same-origin `/api/*` przez proxy Caddy.
- **Przechowywanie tokenu:**
  - 32 bajty z `SecureRandom`, zakodowane base64url bez paddingu (43 znaki);
  - w bazie tylko `Sha256.hex(token)`, jeden wiersz na konto, `expires_at`;
  - tokenu ani linku nigdy nie logujemy; link trafia tylko na stdout komendy.
- **Sesja:** endpointy aktywacji nie tworzą ani nie zmieniają sesji i nie ustawiają ciasteczka. Sesję daje wyłącznie `POST /api/auth/login`. Reset usuwa sesje konta w bazie.
- **CSRF:** bez zmian. `SameSite=Strict` plus `OriginGuardFilter`, który odrzuca obcy `Origin` na obu POST-ach. Endpointy są anonimowe i nie czytają ciasteczka, więc nie ma czego podrobić poza samym tokenem.

## Critical Implementation Details

**Kolejność przy ustawianiu hasła.** W jednej transakcji:

1. Sprawdź politykę hasła (`Passwords.validatePolicy`). Błąd → 400 `password_policy`, token nienaruszony.
2. `SELECT … FOR UPDATE` wiersza tokenu po hashu z `expires_at > now()`. Brak → 400 `activation_link_invalid`.
3. Policz argon2.
4. Ustaw `password_hash` konta i usuń wiersz tokenu.

Argon2 (16 MB, ~dziesiątki ms) liczy się dopiero po zablokowaniu ważnego tokenu, więc anonimowe żądania z nieważnym tokenem nie mogą obciążać serwera argonem. Blokada wiersza serializuje dwa równoległe użycia tego samego linku: drugie widzi brak wiersza.

**Reset w CLI to jedna transakcja:** wyczyść `password_hash`, `DELETE FROM spring_session WHERE principal_name = <uuid konta>`, nadpisz wiersz tokenu (upsert po `user_id`). Komenda nie może użyć `FindByIndexNameSessionRepository`, bo kontekst CLI go nie ma.

**IaC: zwykłe cudzysłowy, nie backticki.** `WEB_PUBLIC_URL: "https://${{szczur-radar-web.RAILWAY_PUBLIC_DOMAIN}}"`. W template literal JS `${…}` zostałoby zinterpolowane, a referencja `ref()`/`web.env` nie przyjmuje prefiksu `https://`. Kolejność deklaracji serwisów nie ma wtedy znaczenia.

**`ACTIVATION_LINK_TTL` wymaga jednostki.** `12h` lub `90m`. Gołą liczbę komenda odrzuca, mimo że `DurationStyle` przyjąłby ją jako milisekundy. Zakres: od `1m` do `7d`.

**E-mail do `/login` przez stan routera, nie query.** `router.replace({ name: 'login', state: { activatedEmail } })`. Query zostałoby w adresie i przy odświeżeniu trafiłoby do logu Caddy.

### Koordynacja z S-05 (daily-scheduled-run-freshness)

S-05 jest planowany równolegle w osobnym worktree (2026-10-12: ma tylko `research.md`). Punkty 2 i 5 opierają się na jego zapowiadanym projekcie: kolumna `users.login_enabled`, konto systemowe automatu, przebieg dzienny o 00:30 UTC. Jeśli plan S-05 wyjdzie inaczej, trzeba je dopasować.

1. **Numer migracji.** S-07 planuje `V6__create_activation_tokens.sql`, a S-05 też doda migrację. Flyway nie zastosuje migracji o numerze niższym niż już zastosowana. Dlatego zmiana, która wchodzi do `main` druga, przed merge'em przenumerowuje swoją migrację na następny wolny numer i poprawia listę wersji w `MigrationsIT`. Nigdy po deployu (fix-forward).
2. **Konto systemowe a komendy S-07.** Jeśli S-05 wprowadzi konto z `login_enabled = false`:
   - `create-user` i `reset-password` odmawiają takiemu kontu (kod 1, komunikat „konto systemowe”);
   - `lookup` i `activate` traktują je jak nieważny link.

   Kod tej odmowy dopisuje zmiana, która wchodzi druga, bo dopiero wtedy istnieją i kolumna, i `ActivationLinks` (faza 2, zmiana 5).
3. **Wspólne pliki, czyli konflikty przy rebase:**
   - `SecurityConfig.java` i `AuthorizationCompletenessIT.java`: publiczne trasy aktywacji tutaj, trasy na token w S-05;
   - `User.java`: `setPassword`/`clearPassword` tutaj, pole `loginEnabled` w S-05;
   - `.railway/railway.ts`: oba dopisują zmienne w `env` API;
   - `dev/mock-api.js`: także `feed-view-ui-hardening`;
   - dokumenty: `deploy-plan.md`, `REPOSITORY_GUIDELINES.md`, `prd.md`, `roadmap.md`.

   Przed weryfikacją końcową: rebase na świeży `origin/main` i ponowne uruchomienie całej weryfikacji (faza 5).
4. **PNG roadmapy.** Obie zmiany generują `docs/visuals/roadmap-mvp.png` na nowo. Przy konflikcie nie scalać pliku binarnego, tylko po rebase uruchomić `tools/render_roadmap.swift` jeszcze raz.
5. **Okno merge'a.** Gdy S-05 jest na produkcji, przebieg dzienny startuje o 00:30 UTC i trwa ok. 15–50 min. Reguła „nie merge'uj przy `RUNNING`” oznacza wtedy w praktyce brak merge'a w godzinach ok. 00:30–01:30 UTC.

## Phase 1: Schemat i logika linków aktywacyjnych

### Overview

Tabela tokenów, metody hasła na koncie i serwis domenowy, który wydaje, sprawdza i zużywa link oraz resetuje konto. Bez CLI i HTTP; pokryte testami integracyjnymi.

### Changes Required:

#### 1. Migracja V6

**File**: `szczur-radar-api/src/main/resources/db/migration/V6__create_activation_tokens.sql`

**Intent**: Tabela jednorazowych tokenów aktywacji, najwyżej jeden na konto.

**Contract**: tabela `activation_tokens`:

- `user_id UUID` jako PK i FK do `users(id)` z `ON DELETE CASCADE`;
- `token_hash TEXT NOT NULL` z `CHECK (token_hash ~ '^[0-9a-f]{64}$')` i nazwanym `UNIQUE`;
- `expires_at TIMESTAMPTZ NOT NULL`;
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`.

Nazwane ograniczenia jak w V1. Komentarz fix-forward jak w V1/V2.

#### 2. Konto: zapis i kasowanie hasła

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/user/User.java`

**Intent**: Encja dostaje dwie jawne operacje zamiast ogólnego settera. Hasło zmienia się tylko przez aktywację albo reset.

**Contract**: `void setPassword(String passwordHash)` i `void clearPassword()`. Javadoc pola zostaje („activation pending, S-07”).

#### 3. Encja i repozytorium tokenu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationToken.java`, `ActivationTokenRepository.java`

**Intent**: Mapowanie JPA tabeli V6, sprawdzane przez `JpaSchemaIT`.

**Contract**:

- encja z `userId` (id), `tokenHash`, `expiresAt`, `createdAt`;
- repozytorium z wyszukaniem ważnego tokenu po hashu z blokadą zapisu, np. `@Lock(PESSIMISTIC_WRITE)` i zapytanie z `expiresAt > :now`.

Tworzy nowy pakiet `auth/activation`.

#### 4. Ustawienia linku

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationSettings.java`

**Intent**: Jedno miejsce, które czyta i waliduje `WEB_PUBLIC_URL` oraz `ACTIVATION_LINK_TTL` i składa link. Czyta je tylko CLI, bo serwer nie wydaje linków, więc zła wartość nie zatrzymuje startu API.

**Contract**: fabryka z `Function<String, @Nullable String>` środowiska (jak `CreateAdminCommand`), zwracająca ustawienia albo listę problemów.

- **`WEB_PUBLIC_URL`:** wymagany, absolutny `http(s)` URL z hostem, bez ścieżki, zapytania i fragmentu. Końcowy `/` jest obcinany. Samo `https://` (brak domeny web) to błąd.
- **`ACTIVATION_LINK_TTL`:** opcjonalny, domyślnie `12h`, wymagana jednostka, zakres od `1m` do `7d`.
- **Link:** `String link(String token)` zwraca `<base>/activate#<token>`.

Ścieżka `/activate` to kontrakt z trasą SPA z fazy 4.

#### 5. Serwis linków

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationLinks.java`

**Intent**: Logika domenowa S-07 w jednym beanie `@Service`, używanym przez CLI (faza 2) i kontroler (faza 3).

**Contract** (nazwy robocze):

- `IssuedLink createUser(String email, Role role, Duration ttl)` — konto bez hasła i token w jednej transakcji. Zajęty e-mail → wynik „istnieje”, nic nie zapisane.
- `IssuedLink resetPassword(String email, Duration ttl)` — transakcja jak w Critical Implementation Details. Nieznany e-mail → wynik „brak konta”.
- `Optional<String> lookup(String token)` — e-mail konta dla ważnego tokenu, bez zmian w bazie.
- `ActivationResult activate(String token, String password)` — kolejność jak w Critical Implementation Details. Wyniki: OK, link nieważny, hasło niezgodne z polityką.

`IssuedLink` niesie surowy token (do złożenia linku przez wywołującego) i `expiresAt`. Generowanie tokenu: `SecureRandom`, 32 bajty, base64url bez paddingu; hash `Sha256.hex` (`persistence/Sha256.java`). Serwis nigdy nie loguje tokenu. Zamknięte zbiory wyników jako enumy lub sealed typy, nie stringi (`lessons.md`).

### Success Criteria:

#### Automated Verification:

- `MigrationsIT` przechodzi z listą wersji `1`–`6` oraz testami ograniczeń `activation_tokens`: format hasha, unikalność hasha, jeden wiersz na konto i kaskada przy usunięciu konta
- `JpaSchemaIT` przechodzi z encją `ActivationToken`
- `ActivationSettingsTest` przechodzi: domyślne `12h`, odrzucenie gołej liczby, granice `1m`/`7d`, odrzucenie `https://` bez hosta i URL ze ścieżką, obcięcie końcowego `/`, kształt linku `/activate#`
- `ActivationLinksIT` przechodzi: utworzenie konta bez hasła z tokenem; lookup ważnego, wygasłego i nieznanego tokenu; aktywacja ustawia hash argon2 i kasuje token; drugi link unieważnia pierwszy; reset kasuje hasło i sesje konta (i tylko jego); polityka hasła nie zużywa tokenu; równoległe dwie aktywacje tym samym tokenem dają dokładnie jedną udaną
- `./mvnw -B verify` przechodzi w całości

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Komendy CLI `create-user` i `reset-password`

### Overview

Dwie komendy operatora wokół `ActivationLinks`. Wspólne parsowanie argumentów i sprawdzanie środowiska wydzielone z `create-admin`. Komunikat `create-admin` wskazuje nową komendę.

### Changes Required:

#### 1. Wydzielenie wspólnych warunków komend (refactor bez zmiany zachowania)

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/cli/CreateAdminCommand.java` i nowy helper w `cli/`

**Intent**: `--email` (dwie formy), walidacja e-maila (`@`, ≤ 320) i sprawdzenie zmiennych `PG*` są dziś prywatne w `CreateAdminCommand` (`:61-75,91-100`). Trzy komendy mają je współdzielić (`lessons.md`: abstrakcja, póki kod mały). Osobny commit, istniejące testy `CreateAdminCommand*` bez zmian.

**Contract**: helper pakietowy z parsowaniem opcji (`--name value` / `--name=value`, nieznana opcja = błąd użycia), walidacją e-maila i listą brakujących zmiennych bazy. Kody wyjścia bez zmian.

#### 2. Nowe komendy

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/cli/CommandType.java`, `CommandLine.java`, `CreateUserCommand.java`, `ResetPasswordCommand.java`

**Intent**: `create-user --email <e> [--role user|admin]` (domyślnie `user`) i `reset-password --email <e>`.

Przed kontekstem Springa komenda sprawdza: argumenty, e-mail, `PG*` oraz `ActivationSettings` (`WEB_PUBLIC_URL`, `ACTIVATION_LINK_TTL`). Błąd → kod 2 z nazwą zmiennej.

Komendy nie potrzebują konsoli, bo nie pytają o hasło.

**Contract**:

- `CommandType.CREATE_USER("create-user", …)` i `RESET_PASSWORD("reset-password", …)`, zarejestrowane w `CommandLine.standard()`;
- kody wyjścia: 0 link wydany, 1 odmowa (e-mail zajęty / brak konta), 2 błąd użycia lub konfiguracji;
- stdout po sukcesie: jedna linia opisu (konto, rola, ważność do `<ISO UTC>`, jednorazowy), potem link w osobnej linii;
- `create-user` przy zajętym e-mailu podpowiada `reset-password`;
- konstruktor pakietowy wstrzykuje środowisko, starter kontekstu i strumienie, jak w `CreateAdminCommand.java:45-52`.

#### 3. Komunikat `create-admin`

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/cli/AdminBootstrap.java`

**Intent**: Odmowa przy istniejącym adminie wskazuje konkretne komendy zamiast „recovery command (S-07)”.

**Contract**: tekst `AdminBootstrap.java:60`, np. „Admin already exists — use reset-password --email <e-mail> to recover access, or create-user --role admin for another admin.”

#### 4. Dokumentacja komend

**File**: `szczur-radar-api/README.md`

**Intent**: Opis obu komend (zmienne, kody wyjścia, przykład z `railway ssh`), link jako sekret (nie wklejać do issue ani logów), lokalne `WEB_PUBLIC_URL=http://localhost:5173`. Zdanie „kolejne konta i odzyskiwanie to S-07” (`README.md:144`) zastąpione odsyłaczem do nowych komend.

**Contract**: sekcje `## Struktura` (wiersz `cli`) i `## Komendy operatora`.

#### 5. Odmowa dla konta systemowego (warunkowo, gdy S-05 jest już w `main`)

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationLinks.java`, `cli/CreateUserCommand.java`, `cli/ResetPasswordCommand.java`

**Intent**: Konto automatu z S-05 (`login_enabled = false`) nie może dostać linku ani hasła („Koordynacja z S-05”, punkt 2). Jeśli S-05 wejdzie do `main` po S-07, ten kod dopisuje S-05, a ta zmiana jest tu N/A.

**Contract**:

- `createUser` i `resetPassword` zwracają wynik „konto systemowe”, a komendy kończą się kodem 1 z tym komunikatem;
- `lookup` i `activate` dla takiego konta zachowują się jak przy nieważnym linku.

### Success Criteria:

#### Automated Verification:

- Istniejące `CreateAdminCommandTest`, `CreateAdminCommandIT` i `CommandLineTest` przechodzą po refaktorze bez zmian w asercjach
- `CreateUserCommandTest` i `ResetPasswordCommandTest` przechodzą: brak lub zły e-mail, nieznana rola, nieznana opcja, brak `PG*`, brak lub zły `WEB_PUBLIC_URL`, `ACTIVATION_LINK_TTL` bez jednostki → kod 2 bez startu kontekstu
- IT komend przechodzi: `create-user` wypisuje link `<WEB_PUBLIC_URL>/activate#<43 znaki>` i tworzy konto `user` bez hasła; `--role admin` tworzy admina; zajęty e-mail → 1; `reset-password` nieznanego e-maila → 1; reset konta z sesją usuwa jej wiersz
- `./mvnw -B verify` przechodzi w całości
- Warunkowo (S-05 już w `main`): testy odmowy dla konta z `login_enabled = false` przechodzą: `create-user`/`reset-password` → kod 1, `lookup`/`activate` → `activation_link_invalid`; w przeciwnym razie wiersz zamknięty jako N/A z adnotacją

#### Manual Verification:

- Lokalnie (`compose.local.yaml`, jar) `create-user` i `reset-password` wypisują czytelny opis i link; log startu Springa nie zawiera tokenu

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Publiczne API aktywacji

### Overview

Dwa anonimowe endpointy nad `ActivationLinks`, nowe kody błędów, wpisy w regułach bezpieczeństwa i testach kompletności autoryzacji.

### Changes Required:

#### 1. Kody błędów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ErrorCode.java`

**Intent**: Dwa nowe kody w kontrakcie `{"detail":"<code>"}`.

**Contract**:

- `ACTIVATION_LINK_INVALID(BAD_REQUEST)`: link nieznany, użyty, wygasły albo zastąpiony nowszym. Jeden kod dla wszystkich, nieodróżnialnie.
- `PASSWORD_POLICY(BAD_REQUEST)`: hasło spoza 12–128 znaków (code points).

#### 2. Kontroler

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationController.java` (+ rekordy żądań i odpowiedzi)

**Intent**: `POST /api/auth/activation/lookup` i `POST /api/auth/activation`. Zamknięte modele JSON (nieznane pola → 400 `invalid_request`), bez dostępu do sesji.

**Contract**:

- lookup: body `{token}` (`@NotNull`, `@Size(max = 64)`) → 200 `{email}` | 400 `activation_link_invalid`;
- aktywacja: body `{token, password}` (`password` `@Size(max = 1024)` jak `LoginRequest`) → 204 | 400 `activation_link_invalid` | 400 `password_policy`;
- kontroler nie woła `getSession` i nie zapisuje kontekstu bezpieczeństwa.

#### 3. Reguły bezpieczeństwa

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java`

**Intent**: Obie trasy publiczne, z uzasadnieniem w allowliście testu.

**Contract**:

- `requestMatchers(HttpMethod.POST, "/api/auth/activation", "/api/auth/activation/lookup").permitAll()`;
- `PUBLIC_ROUTES` dostaje `"POST /api/auth/activation"` („setting the first password cannot require a session; the one-time token is the credential”) i `"POST /api/auth/activation/lookup"` („checks a one-time link before the password form; reveals only the e-mail to the link holder”).

### Success Criteria:

#### Automated Verification:

- `AuthorizationCompletenessIT` przechodzi z dwoma nowymi `PUBLIC_ROUTES`
- `ActivationIT` (HTTP, `MockMvcTester`) przechodzi:
  - lookup ważnego linku → 200 z e-mailem, nieważnego → 400 `activation_link_invalid`;
  - aktywacja → 204, potem logowanie nowym hasłem → 200;
  - drugie użycie → 400;
  - hasło 11 znaków → 400 `password_policy`, a link dalej działa;
  - nieznane pole → 400 `invalid_request`;
  - obcy `Origin` → 403 `cross_origin`;
  - żadna odpowiedź nie ma `Set-Cookie` i nie przybywa wierszy `spring_session`
- `./mvnw -B verify` przechodzi w całości

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Widok aktywacji w SPA

### Overview

Publiczna trasa `/activate`: sprawdzenie linku, formularz hasła, przejście do logowania z komunikatem. Na współdzielonych komponentach i tokenach stylu, z testami i zrzutami ekranu.

### Changes Required:

#### 1. Klient API aktywacji

**File**: `szczur-radar-web/src/api/activation.ts`

**Intent**: Dwie funkcje nad `apiFetch`, w osobnym module, żeby nie ruszać czterech fabryk `vi.mock('../api/auth')` (research, Frontend).

**Contract**: `lookupActivation(token): Promise<{ email: string }>` i `activate(token, password): Promise<void>`, z komentarzem nagłówka wskazującym `auth/activation/ActivationController.java`.

#### 2. Trasa

**File**: `szczur-radar-web/src/router/index.ts`

**Intent**: `{ path: '/activate', name: 'activate', component: ActivateView, meta: { public: true } }`. Strażnik zostaje bez zmian: trasa publiczna, a zalogowany użytkownik nie jest z niej przekierowywany.

**Contract**: nowy wpis w `routes` przed catch-all.

#### 3. Widok

**File**: `szczur-radar-web/src/views/ActivateView.vue`

**Intent**: Stany:

- **brak tokenu we fragmencie:** od razu „link nieważny”, bez wywołania API;
- **sprawdzanie:** stan ładowania;
- **link nieważny:** `StatusMessage` error: „Link jest nieważny lub wygasł. Poproś administratora o nowy.”;
- **formularz:** „Ustaw hasło dla <e-mail>”;
- **wysyłanie:** `AppButton` z `busy`;
- **błąd:** `StatusMessage` error, osobny tekst dla `password_policy` i dla błędu sieci lub 5xx.

Formularz:

- ukryte lub tylko do odczytu pole e-mail z `autocomplete="username"` (dla menedżera haseł);
- dwa pola hasła z `autocomplete="new-password"`;
- walidacja po stronie klienta: 12–128 code points i zgodność obu pól, z komunikatami zgodnymi z `Passwords`.

Po sukcesie: jeśli w tej przeglądarce jest sesja innego konta, `logout()` i `clearSession()`; potem `router.replace({ name: 'login', state: { activatedEmail } })`.

Copy po polsku w stylu `LoginView`. Bez literałów kolorów; odstępy i fonty ze skali.

**Contract**: token z `route.hash` (bez `#`). Mapowanie błędów po `ApiError.detail`, nie po statusie.

#### 4. Komunikat na ekranie logowania

**File**: `szczur-radar-web/src/views/LoginView.vue`

**Intent**: Gdy `history.state.activatedEmail` jest ustawione: e-mail wpisany w pole i `StatusMessage` ok „Hasło ustawione. Zaloguj się.”. Poza tym bez zmian wyglądu.

**Contract**: odczyt `history.state` przy montowaniu; brak nowych parametrów query.

#### 5. Kontrakt stylu, mock i testy

**File**: `szczur-radar-web/src/styleContract.test.ts`, `szczur-radar-web/dev/mock-api.js`, `src/views/ActivateView.test.ts`, `src/views/LoginView.test.ts`, `src/router/index.test.ts`

**Intent**:

- `ActivateView.vue` na liście skanu „no literals”;
- `mock-api.js` dostaje lookup i aktywację oraz tryb anonimowy (`/api/auth/me` → 401), z opisem w nagłówku, żeby dało się zrobić zrzuty widoku publicznego;
- testy każdego stanu widoku, komunikatu w `LoginView` i dostępności `/activate` bez sesji.

**Contract**: wzorzec `vi.mock` jak w `LoginView.test.ts:10-14`, ale dla `../api/activation`.

### Success Criteria:

#### Automated Verification:

- `npm test` przechodzi, w tym:
  - `ActivateView.test.ts`: brak tokenu, lookup OK, lookup nieważny, walidacja klienta (za krótkie, niezgodne), `password_policy` z serwera, błąd sieci, sukces → `/login` ze stanem, wylogowanie cudzej sesji;
  - `LoginView.test.ts`: komunikat i wpisany e-mail po aktywacji;
  - `router/index.test.ts`: `/activate` bez sesji nie przekierowuje;
  - `styleContract.test.ts` z `ActivateView.vue`
- `npm run build` przechodzi (vue-tsc + Vite)

#### Manual Verification:

- Zrzuty `/activate` przez `dev/mock-api.js` + `playwright-cli` w obu motywach i przy 375 px: sprawdzanie, formularz, błąd walidacji, link nieważny, wysyłanie; `/login` z komunikatem po aktywacji
- Tabela 7 stanów (default, hover, focus-visible, disabled, error, empty, loading) ze zrzutem albo N/A z powodem (np. empty: widok nie ma listy)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: Wdrożenie, dokumentacja i weryfikacja w PR env

### Overview

Zmienne w IaC, opis wdrożenia z runbookiem produkcji po merge'u, aktualizacja dokumentów fundamentów i pełny przebieg w Railway PR env.

### Changes Required:

#### 1. IaC

**File**: `.railway/railway.ts`

**Intent**:

- `WEB_PUBLIC_URL: "https://${{szczur-radar-web.RAILWAY_PUBLIC_DOMAIN}}"` (zwykłe cudzysłowy);
- `ACTIVATION_LINK_TTL: preserve()`, żeby wartość ustawiona przez operatora nie była kasowana.

Komentarz wyjaśnia, że czyta je tylko CLI.

Gdyby plan PR env odrzucił string z `${{…}}` albo go zmienił, wariant zapasowy to `WEB_PUBLIC_URL: preserve()` i wartość ustawiona przez operatora w każdym środowisku. Taką zmianę trzeba zapisać w `deploy-plan.md`.

**Contract**: dwa wpisy w `env` serwisu `szczur-radar-api`. Plan PR env pokazuje jedną nową zmienną (`WEB_PUBLIC_URL`) i nic destrukcyjnego. Kolejny plan nie pokazuje stałego diffu (`lessons.md`: stałe diffy w `railway.ts`).

#### 2. Dokument wdrożenia

**File**: `context/deployment/deploy-plan.md`

**Intent**: Nowa sekcja „S-07: onboarding przez CLI (cli-user-onboarding)”:

- komendy (`railway ssh`, najpierw obudzić API, e-mail bez `< >`);
- zmienne (nazwy, `WEB_PUBLIC_URL` z referencji, `ACTIVATION_LINK_TTL` z jednostką);
- link jako sekret: nie do issue ani logów, operator przekazuje go poza systemem;
- wynik weryfikacji w PR env;
- **runbook po merge'u** (operator, śledzony w issue S-07, nie w Progress):
  1. plan produkcji pokazuje tylko nową zmienną, a następny jest pusty;
  2. `railway variable list` pokazuje `WEB_PUBLIC_URL` = adres web produkcji;
  3. `reset-password` lub `create-user` na produkcji;
  4. logowanie.

Zdanie „Password recovery arrives with S-07” (`deploy-plan.md:35`) dostaje odsyłacz do nowej sekcji.

**Contract**: nowa sekcja `## S-07: …` przed `## Phases`.

#### 3. Dokumenty fundamentów

**File**: `context/foundation/prd.md`, `context/foundation/roadmap.md`, `REPOSITORY_GUIDELINES.md`, `context/foundation/tech-stack-api.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`

**Intent**:

- **PRD:** „Doprecyzowanie (2026-10-12, S-07)” pod FR-027/FR-028. Operator może przez CLI zresetować dowolne konto: kasuje hasło i sesje i wydaje nowy link (decyzja użytkownika). To nie jest samodzielny reset z non-goals. Link ważny domyślnie 12 h.
- **Roadmapa:** unknown S-07 rozstrzygnięte (12 h, parametr `ACTIVATION_LINK_TTL`); wiersz Backlog Handoff (`roadmap.md:263`) nieaktualny „no” → zgodny ze stanem. Status prowadzą skille `/10x-implement` i `/10x-archive`. Każda zmiana roadmapy pociąga aktualizację `tools/render_roadmap.swift` i ponowne wygenerowanie `docs/visuals/roadmap-mvp.png` (`REPOSITORY_GUIDELINES.md`).
- **`REPOSITORY_GUIDELINES.md`:** pakiet `auth` (aktywacja), komendy `cli` (`migrate`, `create-admin`, `create-user`, `reset-password`), linia „First admin” w Build commands z odsyłaczem do kolejnych kont.
- **`tech-stack-api.md`:** linia Auth wspomina jednorazowe linki (hash SHA-256 w bazie, reset usuwa sesje SQL-em w CLI).

**Contract**: edycje w miejscu; `lessons.md` tylko dopisywany, jeśli wyjdzie nowa reguła.

### Success Criteria:

#### Automated Verification:

- CI na PR zielone w całości (backend `verify`, web, smoke kontenerów, `railway-config`)
- `railway-config` na PR stosuje jedną nową zmienną `WEB_PUBLIC_URL` bez zmian destrukcyjnych ani zmian źródła, a ponowny plan PR env jest pusty
- Przed weryfikacją końcową gałąź jest po rebase na świeży `origin/main`: numer migracji wolny (przenumerowana razem z `MigrationsIT`, jeśli S-05 jest już w `main`), PNG roadmapy wygenerowany ponownie, a `./mvnw -B verify`, `npm test` i `npm run build` znowu zielone

#### Manual Verification:

- `railway variable list -s szczur-radar-api -e szczur-radar-pr-<N>` pokazuje `WEB_PUBLIC_URL` = `https://szczur-radar-web-szczur-radar-pr-<N>.up.railway.app`; web PR env wdrożony z głowy gałęzi (`meta.commitHash`)
- PR env: `create-user` przez `railway ssh` → link otwarty w przeglądarce → e-mail widoczny → hasło ustawione → `/login` z komunikatem → logowanie nowym kontem, które widzi listę postów i nie ma dostępu do `/admin`
- PR env: ponowne użycie tego samego linku i link sprzed nowszego `reset-password` pokazują „link nieważny”
- PR env: `reset-password` admina z otwartą sesją w przeglądarce → sesja wylogowana, stare hasło odrzucone, nowe hasło przez link działa
- PR env: logi Caddy (`railway logs -s szczur-radar-web`) po przejściu linku nie zawierają tokenu
- `deploy-plan.md` ma sekcję S-07 z wynikiem weryfikacji i runbookiem po merge'u; PRD, roadmapa, PNG roadmapy i `REPOSITORY_GUIDELINES.md` zaktualizowane

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- `ActivationSettingsTest`: parsowanie i granice zmiennych, kształt linku.
- `CreateUserCommandTest`, `ResetPasswordCommandTest`: błędy użycia i konfiguracji bez startu Springa (wzorzec `CreateAdminCommandTest.java:32-71`).
- Vitest: stany `ActivateView`, komunikat w `LoginView`, trasa publiczna.

### Integration Tests:

- `ActivationLinksIT`: pełny cykl tokenu, unieważnienie, reset z usunięciem sesji tylko tego konta, równoległe użycie linku.
- IT komend: wyjście, kody i stan bazy.
- `ActivationIT`: kontrakt HTTP, brak sesji i ciasteczka, Origin, logowanie po aktywacji.
- `AuthorizationCompletenessIT`, `MigrationsIT`, `JpaSchemaIT`: istniejące bramki z nowymi wpisami.

### Manual Testing Steps:

1. Lokalnie: `create-user`, otwarcie linku w `npm run dev`, ustawienie hasła, logowanie.
2. PR env: przebieg z fazy 5 (konto, link, unieważnienie, reset admina, logi Caddy).
3. Zrzuty ekranu widoku w obu motywach i przy 375 px.

## Performance Considerations

- Lookup i nieważne tokeny kosztują jedno zapytanie po indeksie unikalnym `token_hash`. Argon2 (16 MB) liczy się tylko dla ważnego, zablokowanego tokenu, czyli najwyżej raz na link.
- Komendy CLI startują pełny kontekst Springa (~6–8 s), jak `create-admin`; akceptowalne dla narzędzia operatora.

## Migration Notes

- V6 tylko dodaje tabelę. Istniejące konta (admin produkcji) się nie zmieniają. Fix-forward: błąd poprawia nowa migracja.
- Numer V6 jest roboczy: jeśli S-05 wejdzie do `main` pierwszy, S-07 przed merge'em przenumerowuje migrację na następny wolny numer („Koordynacja z S-05”, punkt 1).
- Kopia `pg_dump` obejmie `activation_tokens` (hashe, wygasające). `restore-check.sh` i `COUNTED_TABLES` bez zmian; starsze dumpy przechodzą sprawdzenie jak dotąd.
- Merge zmienia `.railway/railway.ts`, więc API wdroży się dwa razy (znane zachowanie, `deploy-plan.md`, Troubleshooting (Java)). Zasada: nie merge'ować przy przebiegu `RUNNING`.

## References

- Research: `context/changes/cli-user-onboarding/research.md` (z „Follow-up 2026-10-12”)
- PRD: US-02 (`prd.md:67-76`), FR-018 (`:169`), FR-027 (`:196`), FR-028 (`:199`), Access Control (`:268`)
- Roadmapa: S-07 (`roadmap.md:237-248`)
- Wzorzec komendy: `szczur-radar-api/src/main/java/pl/szczurradar/api/cli/CreateAdminCommand.java:23-104`, `AdminBootstrap.java:24-88`
- Wzorzec unieważniania sesji: `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthIT.java:282-297`
- Wzorzec widoku i testów: `szczur-radar-web/src/views/LoginView.vue`, `src/views/LoginView.test.ts:10-14`
- Lekcje: `context/foundation/lessons.md` (enumy dla zamkniętych zbiorów; abstrakcja póki kod mały; jedna gałąź do archiwizacji; stałe diffy w IaC)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Schemat i logika linków aktywacyjnych

#### Automated

- [ ] 1.1 MigrationsIT z wersjami 1–6 i ograniczeniami activation_tokens
- [ ] 1.2 JpaSchemaIT z encją ActivationToken
- [ ] 1.3 ActivationSettingsTest
- [ ] 1.4 ActivationLinksIT
- [ ] 1.5 ./mvnw -B verify

### Phase 2: Komendy CLI create-user i reset-password

#### Automated

- [ ] 2.1 Testy create-admin i CommandLine bez zmian po refaktorze
- [ ] 2.2 CreateUserCommandTest i ResetPasswordCommandTest
- [ ] 2.3 IT komend create-user i reset-password
- [ ] 2.4 ./mvnw -B verify
- [ ] 2.6 Warunkowo: odmowa dla konta systemowego S-05

#### Manual

- [ ] 2.5 Lokalny przebieg komend bez tokenu w logu startu

### Phase 3: Publiczne API aktywacji

#### Automated

- [ ] 3.1 AuthorizationCompletenessIT z nowymi PUBLIC_ROUTES
- [ ] 3.2 ActivationIT
- [ ] 3.3 ./mvnw -B verify

### Phase 4: Widok aktywacji w SPA

#### Automated

- [ ] 4.1 npm test (ActivateView, LoginView, router, styleContract)
- [ ] 4.2 npm run build

#### Manual

- [ ] 4.3 Zrzuty /activate i /login w obu motywach i przy 375 px
- [ ] 4.4 Tabela 7 stanów

### Phase 5: Wdrożenie, dokumentacja i weryfikacja w PR env

#### Automated

- [ ] 5.1 CI na PR zielone w całości
- [ ] 5.2 railway-config stosuje tylko WEB_PUBLIC_URL, ponowny plan pusty
- [ ] 5.9 Rebase na świeży origin/main i ponowna pełna weryfikacja

#### Manual

- [ ] 5.3 WEB_PUBLIC_URL rozwiązany w PR env i web z głowy gałęzi
- [ ] 5.4 PR env: create-user, link, hasło, logowanie
- [ ] 5.5 PR env: zużyty i zastąpiony link nieważny
- [ ] 5.6 PR env: reset-password admina wylogowuje i odzyskuje dostęp
- [ ] 5.7 PR env: logi Caddy bez tokenu
- [ ] 5.8 Dokumenty i PNG roadmapy zaktualizowane
