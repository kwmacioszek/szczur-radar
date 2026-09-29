# Pierwszy admin przez CLI, logowanie/wylogowanie i ochrona tras — Implementation Plan

## Overview

Roadmapa S-01: operator tworzy pierwsze konto administratora komendą CLI, admin loguje się e-mailem i hasłem oraz wylogowuje, a niezalogowany użytkownik widzi wyłącznie ekran logowania. Zmiana wprowadza pierwszy schemat bazy i sposób bezpiecznego wdrażania migracji (Alembic z działającym downgrade, uruchamiany pre-deploy na Railway), własną, cienką warstwę uwierzytelniania (JWT w ciasteczku HttpOnly z `token_version`) oraz wzorzec „kompletnych testów autoryzacji”, pod który automatycznie trafia każda przyszła trasa API.

## Current State Analysis

- API to szkielet: `szczur-radar-api/main.py:17` montuje tylko router sond (`app/routes.py:15` — `/api/health`, `/api/ready`); `app/database.py:30` tworzy leniwy, procesowy async engine (psycopg) i wykonuje wyłącznie `SELECT 1`. Brak modeli, migracji, konfiguracji, CLI.
- Testy: pytest + httpx (`pyproject.toml` grupa `dev`), jeden plik `tests/test_health.py` — wzorzec: `monkeypatch` atrybutu modułu zamiast prawdziwej zależności. Brak `conftest.py` i bazy testowej.
- CI (`.github/workflows/ci.yml`): job `backend` = `uv sync --locked` + `uv run pytest -q` bez Postgresa; job `web-container-smoke` buduje prawdziwy Dockerfile (to jest też bramka typecheck/build frontendu). Workflow nie może być filtrowany ścieżkami (Railway „Wait for CI”).
- Web: `szczur-radar-web/src/App.vue` to statyczna strona „W przygotowaniu”; brak routera, klienta API, testów. `Caddyfile:26` proxy'uje `/api/*` do prywatnego API z zachowaniem ścieżki — SPA i API są same-origin.
- Infra: `.railway/railway.ts` deklaruje `postgres`, `szczur-radar-api` (bez publicznej domeny, 1 replika), `szczur-radar-web`. PR Environments włączone i izolowane (własna baza). Każdy merge do `main` = deploy na produkcję (lessons.md: jedna gałąź + jeden PR, weryfikacja w PR env).
- `tech-stack-api.md` zakładał FastAPI Users; w tej zmianie zdecydowano o własnej warstwie (patrz Key Discoveries i faza 2).
- Równoległa zmiana F-02 (`assessment-contract-harness`) też dotyka `pyproject.toml`, `uv.lock` i `[tool.pytest.ini_options]` (marker `live`, `addopts`) — przy merge konfiguracje pytest trzeba scalić, nie nadpisać.

## Desired End State

- `uv run python -m app.cli create-admin --email admin@example.com` pyta dwukrotnie o hasło ukrytym promptem i tworzy konto z rolą `admin`; ponowne wywołanie, gdy jakikolwiek admin istnieje, kończy się kodem 1 bez zmian w bazie.
- `POST /api/auth/login` z poprawnymi danymi ustawia ciasteczko `sr_session` (HttpOnly, Secure, SameSite=Strict, Path=/api, 30 dni); `GET /api/auth/me` zwraca `{id, email, role}` wyłącznie zalogowanego użytkownika; `POST /api/auth/logout` usuwa ciasteczko.
- Każda trasa API poza jawną allowlistą zwraca 401 bez ważnej sesji — wymuszone testem, który iteruje po wszystkich trasach aplikacji.
- W SPA niezalogowany użytkownik na dowolnym adresie trafia na `/login`; po zalogowaniu wraca na żądaną stronę; przycisk „Wyloguj” kończy sesję.
- Migracja `users` wchodzi na produkcję pre-deploy, a jej downgrade jest sprawdzany w CI na prawdziwym Postgresie.
- Weryfikacja: CI zielone (backend z Postgresem, testy web), ręczne sprawdzenie w Railway PR Environment, jeden merge, pierwszy admin utworzony na produkcji.

### Key Discoveries:

- SDK Railway IaC obsługuje pre-deploy na serwisie (`node_modules/railway/dist/index-C3uk0ruc.d.ts:328` — `preDeploy?: string | string[]`).
- `ctx.randomString()` z SDK jest deterministyczny: `sha256("railway-iac:<environment>:<label>")` (`node_modules/railway/dist/iac/index.js:84`) — każdy z dostępem do repo może go wyliczyć, więc NIE nadaje się na `JWT_SECRET`.
- `get_engine()` cache'uje engine globalnie (`app/database.py:14,36`) i nie wymaga `DATABASE_URL` przy imporcie — testy sond muszą dalej działać bez bazy.
- Wzorzec testowy: podmiana atrybutu modułu (`tests/test_health.py:29`) — trasy wołają `database.check_database()` jako atrybut modułu.
- Za Caddy `X-Forwarded-For` wskazuje krawędź Railway, nie klienta; krawędź Railway ustawia `X-Real-IP` — klucz per-IP limitu prób musi go czytać.
- Railway nie kopiuje zapieczętowanych (sealed) zmiennych do PR Environments (`context/deployment/deploy-plan.md:104,118`) — PR env potrzebuje własnego `JWT_SECRET`.

## What We're NOT Doing

- Aktywacja konta linkiem, tworzenie kont `user` przez CLI, generowanie nowego linku, recovery admina (FR-018, FR-027, FR-028) — to S-07; S-01 przygotowuje tylko nullable `password_hash` i `token_version`, których S-07 użyje.
- FastAPI Users (decyzja: własna warstwa; `tech-stack-api.md` dostaje notkę).
- Refresh tokeny, denylista tokenów, tabela sesji — logout czyści ciasteczko; skopiowany token działa do wygaśnięcia lub podbicia `token_version` (świadomie przyjęte).
- Wygasanie sesji po bezczynności (FR-029 — parked); sesja ma stałe 30 dni.
- Blokada konta po nieudanych próbach i rozproszony limit (Redis) — limit jest w pamięci procesu przy 1 replice API.
- Zmiana hasła przez użytkownika, samodzielny reset, 2FA, zewnętrzny IdP, panel kont (PRD §Non-Goals, parked).
- Trasy danych (lista postów, dashboard) — strona startowa po zalogowaniu jest zastępcza; treść wchodzi w S-02/S-06.
- Playwright/E2E w przeglądarce — frontend testowany Vitestem, przepływ end-to-end ręcznie w PR env.

## Implementation Approach

Wszystkie fazy trafiają na jedną gałąź `feature/admin-bootstrap-and-login` i jeden PR (lessons.md); `/10x-implement` commituje fazy na tej gałęzi, a merge do `main` następuje raz, w fazie 4, po weryfikacji w PR env.

Kolejność: najpierw warstwa danych (model, migracja z downgrade, Postgres w CI, fixture'y), bo auth i CLI jej potrzebują; potem API auth + CLI + test kompletności autoryzacji; potem SPA; na końcu provisioning sekretu, weryfikacja w PR env i bootstrap admina na produkcji.

**Kontrakt uwierzytelniania (wymagany przez AGENTS.md przed implementacją):**

- **Transport:** ciasteczko `sr_session` — `HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=2592000` (30 dni). SPA nigdy nie widzi tokenu; żądania idą same-origin przez proxy `/api/*`.
- **Token:** JWT HS256, claimy `sub` (UUID użytkownika), `ver` (`users.token_version`), `iat`, `exp` (= `iat` + 30 dni, stałe, bez przedłużania). Sekret: zmienna `JWT_SECRET` (≥ 32 znaki), zapieczętowana na produkcji, osobna wartość w PR env. Dekodowanie akceptuje wyłącznie `HS256` i wymaga `exp`, `sub`, `ver`.
- **Przechowywanie sesji:** bezstanowe; przy każdym żądaniu użytkownik jest ładowany z DB, a `ver` porównywany z `token_version` — podbicie wersji (S-07: zmiana hasła/recovery) unieważnia wszystkie tokeny użytkownika. Logout usuwa ciasteczko.
- **CSRF:** `SameSite=Strict` + same-origin + middleware odrzucające (403) żądania `POST/PUT/PATCH/DELETE` pod `/api/*`, gdy nagłówek `Origin` jest obecny i jego host różni się od `Host` żądania; login przyjmuje wyłącznie `application/json`.
- **Brute-force:** limit nieudanych prób w pamięci procesu: 5 / 15 min per e-mail i 20 / 15 min per IP (`X-Real-IP`, fallback: adres peera) → 429 z `Retry-After`; udane logowanie czyści licznik e-maila. Jednakowa odpowiedź 401 `invalid_credentials` i porównywalny czas (weryfikacja względem hasha-atrapy) dla nieznanego e-maila i złego hasła.

## Critical Implementation Details

**Engine a pętla zdarzeń w testach.** `get_engine()` trzyma jeden globalny engine; połączenia async są związane z pętlą, w której powstały, a `TestClient` uruchamia aplikację we własnej pętli. Fixture DB musi po każdym teście zrobić `dispose` i wyzerować `database._engine`, a CLI (`asyncio.run`) tworzyć i zamykać własny engine — inaczej testy losowo padają na „attached to a different loop”.

**Sekret JWT poza IaC.** `JWT_SECRET` nie może być deklarowany w `.railway/railway.ts` ani generowany przez `ctx.randomString` (deterministyczny, patrz Key Discoveries). Przed `railway config apply` sprawdź w `railway config plan`, że plan nie proponuje usunięcia zmiennej ustawionej ręcznie. Aplikacja bez `JWT_SECRET` ma nie wystartować (lifespan), żeby PR env bez sekretu failował głośno, a nie działał z pustym kluczem.

**Pre-deploy i healthcheck.** Migracja musi zakończyć się przed startem nowej wersji API (`preDeploy`), bo nowy kod zakłada istnienie `users`; pre-deploy, który padnie, zatrzymuje deploy, a stara wersja dalej serwuje — to pożądane zachowanie.

**Konfiguracja serwisu ≠ deploy kodu.** Zmiany w `.railway/railway.ts` (tu: `preDeploy`) nie wchodzą z merge — wymagają `railway config apply` (`context/deployment/deploy-plan.md:115`). Zanim cokolwiek zostanie zastosowane automatycznie, `railway.ts` musi odzwierciedlać produkcję (faza 1 §0) — dziś apply skasowałby bucket `Postgres-PITR` i wyłączył uśpienie serwisów. Kolejność zapewnia job CI `railway-config` (faza 1 §8, token workspace, środowisko przez `RAILWAY_PROJECT_ID` + `RAILWAY_ENVIRONMENT_ID`, bez `railway link`): Railway „Wait for CI” czeka na zakończenie całego workflow, więc konfiguracja jest zastosowana, zanim Railway zdeployuje kod — na `main` do `production`, na PR do środowiska `szczur-radar-pr-<N>` (PR env dziedziczy konfigurację po produkcji, ale zmiany z gałęzi PR trzeba zastosować do niego osobno). Job na PR nigdy nie może trafić w `production`, a `railway.ts` wskazuje `source` z gałęzi `main` — plan dla PR, który zmienia `source`/gałąź albo cokolwiek usuwa, musi zatrzymać job. Zapieczętowany `JWT_SECRET` się nie dziedziczy: w PR env ustawia się go ręcznie przed pushem fazy 2.

## Phase 1: Warstwa danych i migracje

### Overview

Pierwszy model (`users`), Alembic z migracją odwracalną, uruchamianie migracji pre-deploy na Railway, Postgres w CI i fixture'y testów bazodanowych — fundament, z którego korzystają fazy 2–4 i kolejne wycinki.

### Changes Required:

#### 0. Krok 0: `railway.ts` zgodny z produkcją (przed każdą inną zmianą IaC)

**File**: `.railway/railway.ts`

**Intent**: Dziś `railway config plan` na `production` pokazuje dryf: `sleepApplication (true → null)` na `postgres`, `szczur-radar-api`, `szczur-radar-web` oraz `Delete bucket Postgres-PITR` (ustawione w dashboardzie, nieobecne w pliku); plan na świeżym PR env też zawiera zmianę destrukcyjną. Automatyczny apply z §8 wyłączyłby uśpienie (koszty) i skasował bucket z backupami PITR — plik musi najpierw odzwierciedlać produkcję.

**Contract**: Dopisać do `railway.ts` uśpienie (serverless) trzech serwisów i bucket `Postgres-PITR` w składni SDK (wzór: `railway config pull --json`, bez zapisywania sekretów — `preserve()`), bez żadnych innych zmian. Kryterium: `railway config plan --detailed-exit-code` na `production` kończy się kodem 0 (0 zmian), a plan na PR env tej zmiany nie zawiera zmian destrukcyjnych. Dopiero potem §4 (preDeploy) i §8 (job CI).

#### 1. Zależności

**File**: `szczur-radar-api/pyproject.toml`, `szczur-radar-api/uv.lock`

**Intent**: Dodać Alembic przez `uv add` (nigdy `pip install`), żeby migracje były częścią zablokowanych zależności używanych przez CI i Railway.

**Contract**: `alembic` w `dependencies`; `uv.lock` zaktualizowany, `uv sync --locked` przechodzi.

#### 2. Model i sesja bazy

**File**: `szczur-radar-api/app/models.py`, `szczur-radar-api/app/database.py`

**Intent**: Zdefiniować `Base` (SQLAlchemy 2 `DeclarativeBase`) i model `User`, a w `database.py` dodać fabrykę sesji async i zależność FastAPI zwracającą sesję — bez naruszania leniwego tworzenia engine'u, na którym polegają testy sond.

**Contract**: Tabela `users`: `id UUID PK DEFAULT gen_random_uuid()`, `email TEXT NOT NULL UNIQUE` z `CHECK (email = lower(email))`, `password_hash TEXT NULL` (konto bez hasła nie może się zalogować; używane przez S-07), `role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user','admin'))`, `token_version INTEGER NOT NULL DEFAULT 0`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`. `database.py`: `get_sessionmaker()` (leniwe, na `get_engine()`), `async def get_session() -> AsyncIterator[AsyncSession]` jako zależność; `_normalize_async_url` reużyty przez Alembic. Normalizacja e-maila (`strip().lower()`) jako jedna funkcja pomocnicza w `app/models.py`, używana przez CLI i login.

#### 3. Alembic i pierwsza migracja

**File**: `szczur-radar-api/alembic.ini`, `szczur-radar-api/migrations/env.py`, `szczur-radar-api/migrations/script.py.mako`, `szczur-radar-api/migrations/versions/0001_create_users.py`

**Intent**: Wprowadzić migracje z jawnym, przetestowanym downgrade (reguła z roadmapy i `deploy-plan.md:113`), czytające `DATABASE_URL` z env tak samo jak aplikacja.

**Contract**: `env.py` bierze URL z `DATABASE_URL` (znormalizowany do `postgresql+psycopg://`, synchroniczny engine psycopg), `target_metadata = Base.metadata`. Rewizja `0001_create_users`: `upgrade()` tworzy `users` z ograniczeniami jak wyżej; `downgrade()` usuwa tabelę. Nazwy ograniczeń jawne (`ck_users_role`, `ck_users_email_lower`, `uq_users_email`), żeby przyszłe migracje mogły się do nich odwołać. Uruchamianie: `cd szczur-radar-api && uv run alembic upgrade head`.

#### 4. Migracja pre-deploy na Railway

**File**: `.railway/railway.ts`

**Intent**: Uruchamiać migracje przed startem każdej nowej wersji API (produkcja i PR env), a nie przy starcie procesu.

**Contract**: Serwis `szczur-radar-api` dostaje `preDeploy: "uv run alembic upgrade head"`; komentarz przy polu wyjaśnia, że nieudana migracja zatrzymuje deploy. Bez zmian w `env` (w szczególności brak `JWT_SECRET`). Walidacja: `railway config plan` pokazuje wyłącznie dodanie pre-deploy. Zastosowanie konfiguracji robi job CI z §8, nie ręczny apply.

#### 5. Postgres w CI i fixture'y testów

**File**: `.github/workflows/ci.yml`, `szczur-radar-api/tests/conftest.py`, `szczur-radar-api/pyproject.toml`

**Intent**: Uruchamiać testy bazodanowe na prawdziwym Postgresie w CI (kontener usługi), a lokalnie pozwolić je pominąć bez bazy — ale nigdy po cichu w CI.

**Contract**: Job `backend` dostaje `services.postgres` (Postgres **18** — ta sama wersja major co lokalny `compose.local.yaml`, obraz `ghcr.io/railwayapp-templates/postgres-ssl:18`, oraz Railway; potwierdź `SELECT version()` na produkcji; w CI wystarczy oficjalny `postgres:18`) z healthcheckiem oraz env `TEST_DATABASE_URL` i `REQUIRE_DB_TESTS=1`. `conftest.py`: fixture sesyjny czyści schemat `public` bazy testowej i robi `alembic upgrade head`; ustawia `DATABASE_URL = TEST_DATABASE_URL` dla aplikacji; fixture per test czyści tabele (`TRUNCATE users`) i po teście robi `dispose` engine'u + zeruje `database._engine`. Bez `TEST_DATABASE_URL`: testy oznaczone markerem `db` są pomijane, chyba że `REQUIRE_DB_TESTS=1` — wtedy fail. Marker `db` zarejestrowany w `[tool.pytest.ini_options]` (scalić z konfiguracją F-02, jeśli już jest na `main`).

#### 6. Test migracji w obie strony

**File**: `szczur-radar-api/tests/test_migrations.py`

**Intent**: Udowodnić, że każda migracja ma działające cofnięcie — wzorzec dla wszystkich kolejnych migracji.

**Contract**: Test `db`: `upgrade head` → tabela `users` istnieje z ograniczeniami (wstawienie `role='root'` i e-maila z wielką literą łamie CHECK) → `downgrade base` → tabeli nie ma → `upgrade head` ponownie przechodzi. Test sprawdza też, że `alembic heads` ma dokładnie jedną głowę.

#### 7. Dokumentacja uruchomienia lokalnego

**File**: `szczur-radar-api/README.md`

**Intent**: Opisać, jak lokalnie postawić Postgres (reużyć istniejący `compose.local.yaml` z głównego `README.md` — Postgres 18 na `127.0.0.1:5432`, osobna baza testowa w tej samej instancji), ustawić `DATABASE_URL`/`TEST_DATABASE_URL` i uruchomić migracje oraz testy DB.

**Contract**: Sekcje: „Baza lokalna”, „Migracje”, „Testy” (z opisem markera `db` i `REQUIRE_DB_TESTS`).

#### 8. Job CI `railway-config` (plan + apply konfiguracji serwisów)

**File**: `.github/workflows/ci.yml`, `context/deployment/deploy-plan.md`

**Intent**: Zastosować konfigurację z `.railway/railway.ts` automatycznie i zawsze przed deployem kodu — dzięki „Wait for CI” — tak, żeby `preDeploy` (i każda przyszła zmiana IaC) nie zależał od ręcznego `railway config apply` ani od pamiętania o kolejności.

**Contract**: Nowy job `railway-config` w tym samym workflow (bez filtrowania ścieżek), `npm ci` w katalogu głównym (SDK `railway/iac`), sekret `RAILWAY_API_TOKEN` = token **workspace** z GitHub Secrets (token projektu obejmuje tylko jedno środowisko, więc nie sięga do PR env). Środowisko docelowe zawsze jawne: `pull_request` → `szczur-radar-pr-${{ github.event.pull_request.number }}`; `push` na `main` → `production`; nigdy domyślne. Wybór środowiska **bez `railway link`** (z tokenem workspace `link`/`whoami` zwracają „Unauthorized”): env `RAILWAY_PROJECT_ID` (stała ID projektu) + `RAILWAY_ENVIRONMENT_ID` rozwiązane po nazwie przez `railway environment list --json` (działa z samym `RAILWAY_PROJECT_ID`); `RAILWAY_ENVIRONMENT` (nazwa) nie jest honorowane przez `config plan`. Kroki: `railway config plan --json` → job kończy się błędem, jeśli `changeSet.changes[]` zawiera `severity == "destructive"` lub `kind == "resource.delete"` albo (dla PR) zmianę `source`/gałęzi serwisu → `railway config apply --yes` (nigdy `--confirm-destructive`) dla tego środowiska. Brak środowiska PR (wyścig z jego tworzeniem) → krótkie ponawianie, potem błąd z czytelnym komunikatem. `deploy-plan.md`: nazwa sekretu `RAILWAY_API_TOKEN` (workspace) w GitHub (bez wartości), reguła „dodanie innego projektu do workspace poszerza zasięg tokena → przenieść szczur-radar do osobnego workspace” i opis joba w runbooku przy wpisie „IaC config change didn't take effect”.

Zweryfikowane 2026-09-28 testem na tymczasowym PR #10 (tylko `plan`): token projektu (production) zawsze trafia w `production` i nie widzi PR env; token workspace robi `plan` na `production` i na `szczur-radar-pr-10`; fałszywy token daje „Unauthorized” (brak cichego fallbacku na sesję użytkownika); w katalogu bez linku `RAILWAY_PROJECT_ID` + `RAILWAY_ENVIRONMENT_ID` wybierają środowisko poprawnie.

### Success Criteria:

#### Automated Verification:

- Zablokowane zależności synchronizują się: `cd szczur-radar-api && uv sync --locked`
- `railway config plan --detailed-exit-code` na `production` przed dodaniem preDeploy kończy się kodem 0 (railway.ts zgodny z produkcją)
- Testy backendu przechodzą z bazą testową: `TEST_DATABASE_URL=… REQUIRE_DB_TESTS=1 uv run pytest -q`
- Test migracji upgrade → downgrade → upgrade przechodzi na Postgresie
- Testy sond przechodzą bez żadnej bazy: `uv run pytest -q tests/test_health.py` przy braku `DATABASE_URL`
- Job `backend` w CI uruchamia testy `db` na kontenerze Postgres i jest zielony
- Job `railway-config` na PR jest zielony: plan dla `szczur-radar-pr-<N>` bez usunięć i bez zmiany `source`, a apply trafia wyłącznie do środowiska PR

#### Manual Verification:

- `railway config plan` pokazuje dodanie pre-deploy dla `szczur-radar-api` i nie proponuje usunięcia żadnej zmiennej
- W PR env ustawienia `szczur-radar-api` pokazują pre-deploy, deploy wykonał `alembic upgrade head`, a konfiguracja `production` pozostała bez zmian

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Uwierzytelnianie API i CLI bootstrapu

### Overview

Własna warstwa auth (hash argon2, JWT w ciasteczku, `current_user`/`require_admin`), trasy `/api/auth/*`, limit prób, ochrona Origin, komenda `create-admin` oraz test kompletności autoryzacji, który obejmuje każdą obecną i przyszłą trasę.

### Changes Required:

#### 1. Zależności

**File**: `szczur-radar-api/pyproject.toml`, `szczur-radar-api/uv.lock`

**Intent**: Dodać przez `uv add` bibliotekę JWT i hashowanie haseł argon2.

**Contract**: `pyjwt`, `pwdlib[argon2]` w `dependencies`.

#### 2. Hasła i tokeny

**File**: `szczur-radar-api/app/auth/__init__.py`, `szczur-radar-api/app/auth/passwords.py`, `szczur-radar-api/app/auth/tokens.py`

**Intent**: Zamknąć kryptografię w dwóch małych modułach z jasnym API, żeby trasy, CLI i S-07 używały jednego źródła prawdy.

**Contract**: `passwords.py`: `hash_password(plain) -> str`, `verify_password(plain, hash | None) -> bool` (przy `None` weryfikuje względem stałego hasha-atrapy i zwraca `False` — wyrównanie czasu), `validate_password_policy(plain) -> None` (12–128 znaków, inaczej `ValueError` z komunikatem). `tokens.py`: `SESSION_TTL = timedelta(days=30)`, `issue_token(user_id: UUID, token_version: int, now: datetime | None = None) -> str`, `decode_token(token) -> TokenClaims(sub: UUID, ver: int)` (tylko `HS256`, wymagane `exp`/`sub`/`ver`, każdy błąd → `InvalidToken`). `jwt_secret()` czyta `JWT_SECRET` leniwie; krótszy niż 32 znaki lub brak → `RuntimeError`.

#### 3. Zależności FastAPI

**File**: `szczur-radar-api/app/auth/dependencies.py`

**Intent**: Jedna zależność do ochrony tras i jedna do tras admina — kolejne wycinki tylko je dołączają.

**Contract**: `SESSION_COOKIE = "sr_session"`. `current_user(request, session) -> User`: brak/niepoprawny token, brak użytkownika, `ver != token_version` → `HTTPException(401, "not_authenticated")`. `require_admin(user = Depends(current_user)) -> User`: `role != "admin"` → `HTTPException(403, "forbidden")`.

#### 4. Limit nieudanych prób

**File**: `szczur-radar-api/app/auth/rate_limit.py`

**Intent**: Tanio ograniczyć zgadywanie haseł na publicznym adresie bez nowych usług.

**Contract**: Okno przesuwne w pamięci procesu; progi: 5 nieudanych prób / 15 min per znormalizowany e-mail, 20 / 15 min per IP. API: `check(email, ip) -> retry_after_seconds | None` (przed weryfikacją hasła), `record_failure(email, ip)`, `record_success(email)` (czyści licznik e-maila), `reset()` dla testów; wstrzykiwalny zegar. `client_ip(request)`: `X-Real-IP`, w przeciwnym razie `request.client.host`.

#### 5. Trasy auth

**File**: `szczur-radar-api/app/auth/routes.py`

**Intent**: Logowanie, wylogowanie i „kim jestem” pod `/api/auth`, zwracające wyłącznie dane zalogowanego konta (NFR: brak wglądu w cudze konta).

**Contract**:
- `POST /api/auth/login` — body JSON `{email: str, password: str}` (model z `extra="forbid"`; inny content-type → 415/422). Sukces: 200 `{id, email, role}` + `Set-Cookie: sr_session=<jwt>; HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=2592000`. Złe dane / nieznany e-mail / `password_hash IS NULL`: 401 `{"detail": "invalid_credentials"}` (identycznie). Limit: 429 `{"detail": "too_many_attempts"}` + `Retry-After`.
- `POST /api/auth/logout` — zawsze 204 i usunięcie ciasteczka (ten sam `Path`); nie wymaga sesji (idempotentne).
- `GET /api/auth/me` — `Depends(current_user)` → 200 `{id, email, role}`.
- Flaga `Secure` sterowana `COOKIE_SECURE` (domyślnie `true`; `false` tylko dla lokalnego http poza `localhost`).

#### 6. Ochrona Origin i podpięcie w aplikacji

**File**: `szczur-radar-api/app/security.py`, `szczur-radar-api/main.py`

**Intent**: Warstwa CSRF poza `SameSite` oraz fail-fast przy braku sekretu.

**Contract**: Middleware: dla `POST/PUT/PATCH/DELETE` pod `/api/`: jeśli `Origin` obecny i jego `host[:port]` ≠ nagłówek `Host` → 403 `{"detail": "cross_origin"}`; brak `Origin` → przepuść (klienci nie-przeglądarkowi). `main.py`: `include_router(auth_router)`, middleware, `lifespan` wywołujący `jwt_secret()` (brak sekretu = aplikacja nie startuje). `TestClient(app)` bez context managera w `test_health.py` nie uruchamia lifespan — testy sond dalej działają bez sekretu.

#### 7. CLI bootstrapu

**File**: `szczur-radar-api/app/cli.py`

**Intent**: FR-026 — pierwszy admin poza interfejsem webowym, bez hasła w historii powłoki ani logach, bez możliwości cichego dopisania kolejnego admina.

**Contract**: `uv run python -m app.cli create-admin --email <email>` (stdlib `argparse` + `getpass`). Kroki: normalizacja e-maila → jeśli istnieje jakikolwiek użytkownik z `role='admin'` → stderr „Admin already exists — use the recovery command (S-07).”, kod 1 → jeśli e-mail zajęty → kod 1 → hasło dwukrotnie przez `getpass`, niezgodne lub niespełniające polityki → komunikat i kod 1 (bez zapisu) → insert `role='admin'` → stdout „Created admin <email>”, kod 0. Błędy użycia → kod 2. Własny `asyncio.run` z engine'em zamykanym na końcu. Hasło nigdy nie jest logowane ani wypisywane.

#### 8. Testy

**File**: `szczur-radar-api/tests/test_auth.py`, `tests/test_authz_completeness.py`, `tests/test_cli.py`, `tests/test_security.py`, `tests/test_tokens.py`

**Intent**: Udowodnić kontrakt auth i ustanowić test kompletności autoryzacji jako strażnika dla każdej przyszłej trasy.

**Contract**:
- `test_authz_completeness.py` (`db` — `current_user` zależy od sesji DB, a `get_engine()` wymaga `DATABASE_URL`, więc bez bazy test dawałby 500 zamiast 401; w CI zawsze uruchamiany dzięki `REQUIRE_DB_TESTS`). Parametry ścieżki przyszłych tras są wypełniane atrapą właściwego typu z sygnatury trasy (UUID → zerowy UUID, `int` → `1`, `str` → `"x"`), żeby walidacja ścieżki nie dawała 422 zamiast 401. `PUBLIC_ROUTES = {("GET","/api/health"), ("GET","/api/ready"), ("POST","/api/auth/login"), ("POST","/api/auth/logout")}`. Test iteruje po wszystkich `APIRoute` w `app.routes` i każdej metodzie: trasa spoza allowlisty bez ciasteczka → 401; każdy wpis allowlisty musi istnieć w aplikacji (brak martwych wpisów). `ADMIN_ROUTES` — rejestr (na razie pusty) sprawdzany z ciasteczkiem użytkownika `role='user'` → 403; `require_admin` dodatkowo testowany na tymczasowej trasie w osobnej aplikacji testowej. Docstring opisuje regułę: nowa trasa albo wymaga sesji, albo trafia do allowlisty z uzasadnieniem.
- `test_auth.py` (`db`): login OK + atrybuty ciasteczka; e-mail wielkimi literami loguje; zły e-mail i złe hasło → identyczne 401; konto z `password_hash=NULL` → 401; `me` z ciasteczkiem zwraca tylko własne dane; podbicie `token_version` → 401; logout usuwa ciasteczko; 6. nieudana próba dla e-maila → 429 z `Retry-After`; 21. nieudana próba z jednego IP na różne e-maile → 429; udane logowanie zeruje licznik e-maila; body form-encoded → odrzucone.
- `test_tokens.py`: wygasły token, zmieniony podpis, `alg=none`, brak `ver` → `InvalidToken`; brak/krótki `JWT_SECRET` → `RuntimeError`.
- `test_security.py`: `POST` z obcym `Origin` → 403; z tym samym → przechodzi; bez `Origin` → przechodzi.
- `test_cli.py` (`db`, `getpass` podmieniony): tworzy admina (hash ≠ hasło, rola `admin`, e-mail znormalizowany); drugi `create-admin` → kod 1, liczba adminów 1; niezgodne hasła i zbyt krótkie → kod 1, brak wiersza.

#### 9. Notka o decyzji w stacku

**File**: `context/foundation/tech-stack-api.md`

**Intent**: Zapisać w miejscu (edit-in-place), że uwierzytelnianie to własna cienka warstwa zamiast FastAPI Users, żeby kolejne zmiany nie dodały FastAPI Users na podstawie nieaktualnego opisu.

**Contract**: Jeden akapit pod „Why this stack”: decyzja z 2026-09-27 (zmiana `admin-bootstrap-and-login`), powód (FastAPI Users w trybie maintenance; potrzeba `token_version`, roli i CLI bez rejestracji), składniki (PyJWT, pwdlib/argon2, `app/auth/`). Frontmatter bez zmian.

#### 10. `JWT_SECRET` w PR env przed pushem tej fazy

**File**: brak zmian w kodzie

**Intent**: Od tej fazy API nie startuje bez `JWT_SECRET` (lifespan), a zapieczętowana wartość z produkcji nie jest dziedziczona przez PR env — bez tego kroku PR env przestaje działać aż do fazy 4.

**Contract**: Operator ustawia w `szczur-radar-pr-<N>` na `szczur-radar-api` osobną, losową wartość `JWT_SECRET` (np. `openssl rand -hex 32`), różną od produkcyjnej, przed pushem commita fazy 2.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą z bazą testową: `TEST_DATABASE_URL=… REQUIRE_DB_TESTS=1 uv run pytest -q`
- Test kompletności autoryzacji przechodzi i każda trasa spoza allowlisty zwraca 401 bez sesji
- Testy logowania, limitu prób, Origin, tokenów i CLI przechodzą
- Job `backend` w CI jest zielony

#### Manual Verification:

- Lokalnie: `create-admin` tworzy admina z ukrytym promptem hasła, drugie wywołanie odmawia z kodem 1
- Lokalnie: `curl` na `/api/auth/login` zwraca ciasteczko z atrybutami `HttpOnly; Secure; SameSite=Strict; Path=/api`, a `/api/auth/me` z tym ciasteczkiem zwraca własne dane
- PR env ma własny `JWT_SECRET` i API w PR env startuje po pushu tej fazy

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Logowanie w SPA

### Overview

Router z publicznym `/login` i chronioną resztą, klient API reagujący na 401, ekran logowania z obsługą błędów, zastępcza strona startowa z wylogowaniem, proxy Vite do lokalnego API oraz Vitest w CI.

### Changes Required:

#### 1. Zależności i konfiguracja

**File**: `szczur-radar-web/package.json`, `szczur-radar-web/package-lock.json`, `szczur-radar-web/vite.config.ts`, `szczur-radar-web/vitest.config.ts`, `szczur-radar-web/tsconfig.node.json`

**Intent**: Dodać routing i narzędzia testowe oraz zachować same-origin także lokalnie — bez łamania typechecku `vue-tsc -b` w buildzie Docker.

**Contract**: `vue-router` (dependencies); `vitest`, `@vue/test-utils`, `jsdom` (devDependencies); skrypt `"test": "vitest run"`. `vite.config.ts`: tylko `server.proxy['/api'] → http://localhost:8000` (bez klucza `test` — `defineConfig` z `vite` go nie typuje). Osobny `vitest.config.ts`: `defineConfig`/`mergeConfig` z `vitest/config` na bazie `vite.config.ts`, `test.environment = 'jsdom'`; dopisany do `include` w `tsconfig.node.json`. Testy importują `describe/it/expect/vi` jawnie z `'vitest'` (bez globali), bo `src/**/*.test.ts` wchodzą do `tsconfig.app.json`. `npm run build` (vue-tsc) nadal przechodzi.

#### 2. Klient API i stan sesji

**File**: `szczur-radar-web/src/api/client.ts`, `szczur-radar-web/src/api/auth.ts`, `szczur-radar-web/src/auth/session.ts`

**Intent**: Jedno miejsce wywołań `/api/*` i jeden reaktywny stan „kto jest zalogowany”, bez przechowywania tokenu po stronie przeglądarki.

**Contract**: `apiFetch(path, init)` — `credentials: 'same-origin'`, JSON; odpowiedź 401 czyści sesję i wywołuje zarejestrowany handler (przekierowanie na `/login`); błędy jako typowany `ApiError { status, detail, retryAfter? }`. `auth.ts`: `login(email, password)`, `logout()`, `fetchMe()` na kontrakcie z fazy 2. `session.ts`: `currentUser` (ref `{id, email, role} | null`), `ensureSession()` (jednorazowe `GET /api/auth/me`; 401 → `null`), `clearSession()`. Bez Pinii.

#### 3. Router i guard

**File**: `szczur-radar-web/src/router/index.ts`, `szczur-radar-web/src/main.ts`

**Intent**: Niezalogowany użytkownik widzi wyłącznie ekran logowania; zalogowany nie wraca na `/login`.

**Contract**: Trasy: `/login` (`meta.public = true`), `/` (chroniona, `HomeView`), catch-all → `/`. `beforeEach`: `await ensureSession()`; chroniona i brak użytkownika → `/login?redirect=<fullPath>`; `/login` z użytkownikiem → `/`. `redirect` akceptowany tylko, gdy zaczyna się od pojedynczego `/` (nie `//`, nie schemat) — inaczej `/`. History mode (Caddy ma już SPA fallback).

#### 4. Widoki

**File**: `szczur-radar-web/src/views/LoginView.vue`, `szczur-radar-web/src/views/HomeView.vue`, `szczur-radar-web/src/App.vue`

**Intent**: Prosty ekran logowania i zastępcza strona po zalogowaniu (lista postów przyjdzie w S-02).

**Contract**: `LoginView`: pola e-mail/hasło (`type=email`, `type=password`, `autocomplete` `username`/`current-password`), przycisk zablokowany podczas wysyłki; mapowanie błędów: 401 → „Nieprawidłowy e-mail lub hasło.”, 429 → „Zbyt wiele prób. Spróbuj ponownie za N min.” (z `Retry-After`), inne → „Nie udało się zalogować. Spróbuj ponownie.”; po sukcesie przejście na zweryfikowany `redirect` lub `/`. `HomeView`: nagłówek z e-mailem użytkownika i przyciskiem „Wyloguj” (logout → `clearSession()` → `/login`) oraz dotychczasowa treść „W przygotowaniu”. `App.vue` → `<RouterView />` z zachowaniem istniejących stylów.

#### 5. Testy i CI

**File**: `szczur-radar-web/src/**/*.test.ts`, `.github/workflows/ci.yml`

**Intent**: Wzorzec testów web dla kolejnych wycinków i bramka w CI.

**Contract**: Testy: guard (niezalogowany na `/` → `/login?redirect=/`; zalogowany na `/login` → `/`), sanitacja `redirect` (`//evil.com`, `https://evil.com` → `/`), `apiFetch` przy 401 czyści sesję i wywołuje handler, `LoginView` pokazuje właściwy komunikat dla 401 i 429. CI: nowy job `web-unit` (Node 22, `npm ci`, `npm test`) w katalogu `szczur-radar-web`, bez filtrowania ścieżek.

#### 6. Aktualizacja AGENTS.md

**File**: `AGENTS.md`

**Intent**: Usunąć nieaktualne stwierdzenia („brak test runnera”, „`main.py` to stub”) i wskazać nowe polecenia, żeby kolejni agenci nie zgadywali.

**Contract**: Minimalne edycje w sekcjach „Project Structure”, „Build, Test, and Development Commands” i „Coding Style & Testing”: pytest + marker `db` + `TEST_DATABASE_URL`, `alembic upgrade head`, `npm test` (Vitest), `python -m app.cli create-admin`, test kompletności autoryzacji jako reguła dla nowych tras. Blok `@przeprogramowani/10x-cli` bez zmian.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Jobs `web-unit` i `web-container-smoke` w CI są zielone (smoke nadal serwuje `index.html` dla głębokich ścieżek)

#### Manual Verification:

- Lokalnie (API + `npm run dev`): wejście na `/` bez sesji kończy się na `/login`, logowanie wraca na żądaną stronę, „Wyloguj” wraca na `/login`, a odświeżenie nie przywraca sesji
- Lokalnie: złe hasło pokazuje komunikat błędu, a po przekroczeniu limitu komunikat o ponownej próbie

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Wdrożenie i pierwszy admin

### Overview

Provisioning `JWT_SECRET`, pełna weryfikacja w Railway PR Environment, jeden merge do `main` i utworzenie pierwszego admina na produkcji.

### Changes Required:

#### 1. Sekret JWT

**File**: brak zmian w kodzie; zapis w `context/deployment/deploy-plan.md`

**Intent**: Dostarczyć losowy, niewyliczalny sekret do produkcji i osobny do PR env, bez umieszczania go w repo.

**Contract**: Produkcja: operator ustawia na `szczur-radar-api` zmienną `JWT_SECRET` (np. `openssl rand -hex 32`) i ją pieczętuje (sealed) — **przed merge**, bo nowa wersja API bez sekretu nie przejdzie healthchecku. PR env: osobna wartość ustawiona już w fazie 2 (§10). `railway config plan` dla `production` nie proponuje usunięcia `JWT_SECRET`. `deploy-plan.md` dostaje wpis: nazwa zmiennej, sealed, sposób dostarczenia do PR env — bez wartości.

#### 2. Weryfikacja w PR Environment

**File**: brak zmian w kodzie

**Intent**: Sprawdzić cały przepływ na izolowanej bazie przed produkcją.

**Contract**: Na PR env: logi pre-deploy pokazują `alembic upgrade head` → `0001`; `/api/ready` = 200; `railway ssh` do API → `create-admin`; w przeglądarce logowanie, ochrona głębokiego linku, wylogowanie; `curl` z obcym `Origin` → 403; 6 złych prób → 429; w logach/odpowiedzi widoczny prawdziwy adres klienta z `X-Real-IP` (nie adres krawędzi Railway); `curl -H 'X-Real-IP: 1.2.3.4'` → API nadal widzi prawdziwy adres, nie `1.2.3.4` (krawędź nadpisuje nagłówek klienta — inaczej limit per IP da się obejść).

#### 3. Merge i bootstrap produkcji

**File**: `context/deployment/deploy-plan.md`

**Intent**: Jeden merge do `main`, migracja pre-deploy na produkcji i pierwszy admin.

**Contract**: Merge PR → CI na `main`, w tym job `railway-config` stosujący konfigurację do `production` → deploy (Wait for CI) → pre-deploy migracja na prod → `railway ssh` do `szczur-radar-api` → `uv run python -m app.cli create-admin --email <operator>` → logowanie na publicznym adresie. `deploy-plan.md`: wpis o wdrożeniu S-01 (deploy ID, wynik migracji, data utworzenia admina — bez sekretów i haseł).

### Success Criteria:

#### Automated Verification:

- CI na PR jest zielony (backend z Postgresem, `web-unit`, `web-container-smoke`)

#### Manual Verification:

- `JWT_SECRET` jest ustawiony i zapieczętowany na produkcji, a PR env ma własną wartość
- W PR env migracja pre-deploy przeszła, a logowanie, ochrona tras, wylogowanie, 403 dla obcego Origin i 429 po limicie działają
- Limit per IP w PR env używa prawdziwego adresu klienta z `X-Real-IP`
- Po merge migracja na produkcji przeszła, pierwszy admin został utworzony przez `railway ssh` i loguje się na publicznym adresie
- `deploy-plan.md` zawiera wpis o wdrożeniu S-01 i sekrecie (bez wartości)
- Po merge job `railway-config` na `main` zastosował pre-deploy do `production` przed deployem kodu (widoczne w ustawieniach serwisu i w kolejności logów)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- Tokeny: wygaśnięcie, podpis, `alg=none`, brakujące claimy, brak/krótki sekret.
- Hasła: polityka 12–128 znaków, weryfikacja względem `None` zwraca `False`.
- Limit prób: progi per e-mail i per IP, okno czasowe na wstrzykiwanym zegarze, reset po sukcesie.
- Web: guard, sanitacja `redirect`, obsługa 401 w `apiFetch`, komunikaty `LoginView`.

### Integration Tests:

- Migracja upgrade → downgrade → upgrade na Postgresie.
- Test kompletności autoryzacji po wszystkich trasach aplikacji (strażnik dla przyszłych wycinków).
- Login/me/logout z prawdziwą bazą, podbicie `token_version`, konto bez hasła.
- CLI `create-admin` na prawdziwej bazie (odmowa przy istniejącym adminie).

### Manual Testing Steps:

1. W PR env: `railway ssh` → `create-admin` → logowanie w przeglądarce → sprawdzenie atrybutów ciasteczka w devtools.
2. Wklejenie głębokiego linku w oknie prywatnym → ekran logowania → po zalogowaniu powrót na link.
3. Wylogowanie → `GET /api/auth/me` zwraca 401.
4. `curl -X POST -H 'Origin: https://evil.example' …/api/auth/logout` → 403.
5. 6 złych haseł → 429 i komunikat w UI.

## Performance Considerations

Weryfikacja argon2 trwa dziesiątki milisekund — akceptowalne przy małej grupie użytkowników (PRD: brak sztywnego wymogu czasu odpowiedzi). `current_user` robi jedno zapytanie po kluczu głównym na żądanie. Limit prób w pamięci działa poprawnie wyłącznie przy 1 replice API (stan obecny w `railway.ts`); zwiększenie liczby replik wymaga przeniesienia limitu do współdzielonego magazynu.

## Migration Notes

`0001_create_users` tworzy nową tabelę — brak danych do migracji. Downgrade usuwa `users`, więc na produkcji jest bezpieczny tylko przed utworzeniem kont; po bootstrapie admina preferowany jest fix-forward (nowa migracja), zgodnie z `deploy-plan.md:113`. Rollback kodu przez dashboard nie cofa schematu — wersja sprzed S-01 nie używa `users`, więc pozostawiona tabela jej nie przeszkadza.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-01)
- PRD: `context/foundation/prd.md` (FR-012–FR-015, FR-026, §Access Control, NFR autoryzacji)
- Stack: `context/foundation/tech-stack-api.md`, `context/foundation/tech-stack-web.md`
- Deploy: `context/deployment/deploy-plan.md` (PR env, sealed secrets, rollback), `.railway/railway.ts`
- Lessons: `context/foundation/lessons.md` (jedna gałąź + jeden PR, Railway trial)
- Wzorzec testów: `szczur-radar-api/tests/test_health.py:29`
- Proxy same-origin: `szczur-radar-web/Caddyfile:26`
- Równoległa zmiana: `context/changes/assessment-contract-harness/plan.md` (konfiguracja pytest)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Warstwa danych i migracje

#### Automated

- [x] 1.1 Zablokowane zależności synchronizują się: `cd szczur-radar-api && uv sync --locked` — 7139be8
- [x] 1.9 `railway config plan --detailed-exit-code` na `production` przed dodaniem preDeploy kończy się kodem 0 (railway.ts zgodny z produkcją) — 7139be8
- [x] 1.2 Testy backendu przechodzą z bazą testową: `TEST_DATABASE_URL=… REQUIRE_DB_TESTS=1 uv run pytest -q` — 7139be8
- [x] 1.3 Test migracji upgrade → downgrade → upgrade przechodzi na Postgresie — 7139be8
- [x] 1.4 Testy sond przechodzą bez żadnej bazy: `uv run pytest -q tests/test_health.py` przy braku `DATABASE_URL` — 7139be8
- [x] 1.5 Job `backend` w CI uruchamia testy `db` na kontenerze Postgres i jest zielony — 7abc0d4
- [x] 1.7 Job `railway-config` na PR jest zielony: plan dla `szczur-radar-pr-<N>` bez usunięć i bez zmiany `source`, a apply trafia wyłącznie do środowiska PR — 7abc0d4

#### Manual

- [x] 1.6 `railway config plan` pokazuje dodanie pre-deploy dla `szczur-radar-api` i nie proponuje usunięcia żadnej zmiennej — 7abc0d4
- [x] 1.8 W PR env ustawienia `szczur-radar-api` pokazują pre-deploy, deploy wykonał `alembic upgrade head`, a konfiguracja `production` pozostała bez zmian — 7abc0d4

### Phase 2: Uwierzytelnianie API i CLI bootstrapu

#### Automated

- [x] 2.1 Testy backendu przechodzą z bazą testową: `TEST_DATABASE_URL=… REQUIRE_DB_TESTS=1 uv run pytest -q` — 7c02cfb
- [x] 2.2 Test kompletności autoryzacji przechodzi i każda trasa spoza allowlisty zwraca 401 bez sesji — 7c02cfb
- [x] 2.3 Testy logowania, limitu prób, Origin, tokenów i CLI przechodzą — 7c02cfb
- [x] 2.4 Job `backend` w CI jest zielony — 60f8df1

#### Manual

- [x] 2.5 Lokalnie: `create-admin` tworzy admina z ukrytym promptem hasła, drugie wywołanie odmawia z kodem 1 — 7c02cfb
- [x] 2.6 Lokalnie: `curl` na `/api/auth/login` zwraca ciasteczko z atrybutami `HttpOnly; Secure; SameSite=Strict; Path=/api`, a `/api/auth/me` z tym ciasteczkiem zwraca własne dane — 7c02cfb
- [x] 2.7 PR env ma własny `JWT_SECRET` i API w PR env startuje po pushu tej fazy — 60f8df1

### Phase 3: Logowanie w SPA

#### Automated

- [x] 3.1 Testy web przechodzą: `cd szczur-radar-web && npm test` — f38ff8b
- [x] 3.2 Typecheck i build przechodzą: `cd szczur-radar-web && npm run build` — f38ff8b
- [x] 3.3 Jobs `web-unit` i `web-container-smoke` w CI są zielone (smoke nadal serwuje `index.html` dla głębokich ścieżek) — f38ff8b

#### Manual

- [x] 3.4 Lokalnie (API + `npm run dev`): wejście na `/` bez sesji kończy się na `/login`, logowanie wraca na żądaną stronę, „Wyloguj” wraca na `/login`, a odświeżenie nie przywraca sesji — cd261e1
- [x] 3.5 Lokalnie: złe hasło pokazuje komunikat błędu, a po przekroczeniu limitu komunikat o ponownej próbie — cd261e1

### Phase 4: Wdrożenie i pierwszy admin

#### Automated

- [x] 4.1 CI na PR jest zielony (backend z Postgresem, `web-unit`, `web-container-smoke`) — 2d4a248

#### Manual

- [x] 4.2 `JWT_SECRET` jest ustawiony i zapieczętowany na produkcji, a PR env ma własną wartość — 2d4a248
- [x] 4.3 W PR env migracja pre-deploy przeszła, a logowanie, ochrona tras, wylogowanie, 403 dla obcego Origin i 429 po limicie działają — 2d4a248
- [x] 4.4 Limit per IP w PR env używa prawdziwego adresu klienta z `X-Real-IP` — 2d4a248
- [x] 4.5 Po merge migracja na produkcji przeszła, pierwszy admin został utworzony przez `railway ssh` i loguje się na publicznym adresie — 9b9fbf8
- [x] 4.6 `deploy-plan.md` zawiera wpis o wdrożeniu S-01 i sekrecie (bez wartości) — 44f9ac2
- [x] 4.7 Po merge job `railway-config` na `main` zastosował pre-deploy do `production` przed deployem kodu (widoczne w ustawieniach serwisu i w kolejności logów) — 9b9fbf8
