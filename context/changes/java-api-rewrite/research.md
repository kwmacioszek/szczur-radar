---
date: 2026-10-01T21:50:00+02:00
researcher: Claude (Opus 5.5) z kwmacioszek
git_commit: d2a52d3768f4aff3538ce4f68dfd1bcaf26ce9a3
branch: feature/java-api-rewrite
repository: kwmacioszek/szczur-radar
topic: "Co nowy backend w Javie (Spring Boot) musi zachować z obecnego szczur-radar-api (zakres S-01)"
tags: [research, codebase, szczur-radar-api, auth, migrations, railway, ci, java-rewrite]
status: complete
last_updated: 2026-10-01
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Kontynuacja 2 — badanie zewnętrzne: Spring Security (co daje z pudełka), Spring Session JDBC, Railpack Java, sieć prywatna Railway, Flyway vs Liquibase; JWT z S-01 do zastąpienia sesją serwerową (decyzja dla planu)"
---

# Research: Co nowy backend w Javie musi zachować z obecnego `szczur-radar-api`

**Date**: 2026-10-01T21:50:00+02:00
**Researcher**: Claude (Opus 5.5) z kwmacioszek
**Git Commit**: d2a52d3768f4aff3538ce4f68dfd1bcaf26ce9a3
**Branch**: feature/java-api-rewrite
**Repository**: kwmacioszek/szczur-radar

## Research Question

Zmiana `java-api-rewrite` (roadmap F-03): przepisać `szczur-radar-api` z Pythona/FastAPI na Javę (Spring Boot), bez przenoszenia kodu F-02. Pytanie badawcze: **jakie kontrakty, zachowania, dane produkcyjne i punkty integracji ma zachować wersja w Javie**, żeby SPA, Railway, CI i istniejące konto admina działały bez zmian — oraz które miejsca w repo trzeba zaktualizować.

Zakres: kod śledzony przez git w `szczur-radar-api/` na tym branchu (stan `main` = S-01), jego konsumenci (`szczur-radar-web`, `.railway/railway.ts`, `.github/workflows/ci.yml`) i decyzje z zarchiwizowanego S-01. Poza zakresem: wybór bibliotek Javy (to badanie zewnętrzne — patrz „Open Questions”) i kod F-02 z zamkniętego PR #23.

Metoda: kod jest mały (15 plików `.py` aplikacji/migracji + 10 plików testów, `git ls-files szczur-radar-api`), więc przeczytany w całości w głównym kontekście, bez sub-agentów.

## Summary

1. **Zakres do przepisania jest mały i dobrze opisany.** Aplikacja to 5 tras HTTP (`/api/health`, `/api/ready`, `/api/auth/login`, `/api/auth/logout`, `/api/auth/me`), middleware Origin, warstwa auth (argon2, JWT HS256 w ciasteczku, `token_version`, limiter prób logowania), jedna komenda CLI (`create-admin`) i jedna migracja (`users`). Kontrakt zachowań jest zapisany w 1194 liniach testów (`szczur-radar-api/tests/*.py`, `wc -l`), które służą jako specyfikacja do przeniesienia.
2. **Produkcja ma dane, które Java musi przejąć bez migracji danych**: tabela `users` z jednym adminem utworzonym 2026-09-29 (`context/deployment/deploy-plan.md:26`), hasło zahashowane przez pwdlib w formacie PHC `$argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>` (próbka z lokalnego `.venv`, pwdlib 0.3.1 / argon2-cffi 25.1.0), tabela `alembic_version` = `0001_create_users` (`deploy-plan.md:24`) oraz zapieczętowany `JWT_SECRET` (`deploy-plan.md:27`). Weryfikacja istniejącego hasha i istniejących ciasteczek w Javie to warunek, żeby admin nie stracił dostępu (recovery dopiero w S-07).
3. **SPA zależy tylko od wąskiego kontraktu**: ścieżek `/api/auth/{login,logout,me}`, kształtu `{id, email, role}`, statusów 401/429, pola `detail` jako stringa i nagłówka `Retry-After` (`szczur-radar-web/src/api/auth.ts:4-27`, `src/api/client.ts:37-81`, `src/views/LoginView.vue:18-23`, `src/auth/session.ts:22`). Format błędów 422 nie jest przez SPA czytany.
4. **Integracja z Railway i CI jest zaszyta w Pythonie** w 3 miejscach do zmiany: start i pre-deploy w `.railway/railway.ts:67,71` (`uv run … hypercorn` / `alembic upgrade head`), job `backend` w `.github/workflows/ci.yml:12-48` (`uv sync --locked`, `pytest`) oraz `.python-version` dla Railpack. Wymagania platformy, które muszą przetrwać: port 8000 (`railway.ts:32,76`), bind dual-stack IPv4+IPv6 (`railway.ts:56-60`, `deploy-plan.md:98,131`), healthcheck `/api/health` (`railway.ts:72`), migracje przed startem w pre-deploy (`railway.ts:68-71`), fail-fast bez `JWT_SECRET` (`main.py:23-28`).
5. **Dokumentacja do aktualizacji** (wzmianki o Pythonie/FastAPI/uv poza `context/archive/`): `REPOSITORY_GUIDELINES.md`, `README.md`, `szczur-radar-api/README.md`, `.gitignore`, `context/foundation/tech-stack-api.md`, `tech-stack-web.md`, `infrastructure.md`, `context/deployment/deploy-plan.md` (wynik `grep -rlE "uv run|fastapi|alembic|python|hypercorn|pytest"`, bez `node_modules`, `.git`, `context/archive`, `.claude`, `.agents`, `szczur-radar-api`, `assessment-contract-harness`).

## Detailed Findings

### 1. Trasy HTTP i ich kontrakt

| Metoda i ścieżka | Ochrona | Odpowiedzi | Źródło |
|---|---|---|---|
| `GET /api/health` | publiczna | 200 `{"status":"ok"}`, bez zależności od DB | `app/routes.py:18-21` |
| `GET /api/ready` | publiczna | 200 `{"status":"ready"}` gdy `SELECT 1` przechodzi; 503 `{"status":"unavailable"}` przy dowolnym wyjątku | `app/routes.py:24-35` |
| `POST /api/auth/login` | publiczna | 200 `{id, email, role}` + `Set-Cookie`; 401 `{"detail":"invalid_credentials"}`; 429 `{"detail":"too_many_attempts"}` + `Retry-After`; 422 dla nieznanych pól; 415/422 dla form-encoded | `app/auth/routes.py:68-110`; `tests/test_auth.py:175-189` |
| `POST /api/auth/logout` | publiczna | zawsze 204, kasuje ciasteczko z tymi samymi atrybutami | `app/auth/routes.py:113-118` |
| `GET /api/auth/me` | `current_user` | 200 `{id, email, role}`; 401 `{"detail":"not_authenticated"}` | `app/auth/routes.py:121-123`; `app/auth/dependencies.py:20-40` |

- Lista publicznych tras jest jawna w `tests/test_authz_completeness.py:31-35` (4 pary metoda+ścieżka), `ADMIN_ROUTES` jest pusty (`:38-40`). Test wywołuje każdą niepubliczną trasę bez ciasteczka i wymaga 401 (`:91-109`). Hard rule w `REPOSITORY_GUIDELINES.md` wymaga tego wzorca dla każdej nowej trasy — w Javie potrzebny jest odpowiednik (np. test iterujący po zarejestrowanych endpointach).
- `require_admin` zwraca 403 `{"detail":"forbidden"}` dla roli innej niż `admin` (`dependencies.py:43-46`); dziś nie używany przez żadną trasę, ale testowany na trasie-sondzie (`test_authz_completeness.py:121-146`).
- Nieznana ścieżka pod `/api/*` zwraca dziś JSON 404 `{"detail":"Not Found"}` przez proxy (`deploy-plan.md:102`) — zachowanie FastAPI, nie zapisane w testach.
- Format błędów: wszystkie błędy aplikacyjne mają kształt `{"detail": "<kod>"}` (powyższe źródła). 422 z FastAPI ma `detail` jako listę (zachowanie frameworka); SPA czyta `detail` tylko gdy jest stringiem (`client.ts:43-54`) i nie obsługuje 422 osobno (`LoginView.vue:18-23`).

### 2. Sesja i JWT

Kontrakt zapisany w S-01 jako „Kontrakt uwierzytelniania” (`context/archive/2026-09-27-admin-bootstrap-and-login/plan.md:52-57`), zaimplementowany w:

- **Ciasteczko** `sr_session`: `HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=2592000` (`app/auth/routes.py:29,59-65,100-105`; asercja w `tests/test_auth.py:45-47`). `Secure` można wyłączyć tylko `COOKIE_SECURE` ∈ {`0`,`false`,`no`} (bez rozróżniania wielkości liter, `routes.py:50-56`) — dla lokalnego http.
- **Token**: HS256, claimy `sub` (UUID jako string), `ver` (int), `iat`, `exp` = `iat` + 30 dni jako liczby sekund epoki (`app/auth/tokens.py:17,42-52`; próbka dekodowania z `.venv`: `{'sub': '…', 'ver': 0, 'iat': 1790884190, 'exp': 1790884190}`).
- **Dekodowanie** akceptuje wyłącznie `HS256`, wymaga `exp`, `iat`, `sub`, `ver`; `ver` musi być int i nie bool; `sub` musi być poprawnym UUID; każdy błąd → 401 (`tokens.py:55-72`). Testy przypadków brzegowych: `alg=none`, inny sekret, podmieniony podpis, brak claimu, `ver` jako string, śmieci (`tests/test_tokens.py:27-109`).
- **Weryfikacja przy każdym żądaniu**: użytkownik ładowany z DB po `sub`; brak użytkownika lub `ver != token_version` → 401 (`dependencies.py:26-40`; `tests/test_auth.py:97-120`).
- **Sekret**: `JWT_SECRET` ≥ 32 znaki, inaczej `RuntimeError`; sprawdzany przy starcie aplikacji (`tokens.py:32-39`, `main.py:23-28`). Produkcja: zapieczętowany; PR env: losowy, generowany przez CI (`ci.yml:159-180`).
- **Wniosek**: jeśli Java wydaje i weryfikuje tokeny w tym samym formacie i tym samym sekretem, istniejące sesje na produkcji przetrwają przełączenie. To nie jest wymóg PRD — to wybór do podjęcia w planie (alternatywa: wymuszone ponowne logowanie, akceptowalne przy jednym użytkowniku).

### 3. Hasła

- Hash: pwdlib `PasswordHash.recommended()` = argon2id (`app/auth/passwords.py:14`); format PHC `$argon2id$v=19$m=65536,t=3,p=4$<salt b64 bez paddingu>$<hash b64 bez paddingu>` (próbka z `.venv`). Ten format ma hasło admina na produkcji.
- Wyrównanie czasu: dla nieznanego e-maila i konta bez hasła (`password_hash IS NULL`) wykonywana jest pełna weryfikacja względem stałego hasha-atrapy (`passwords.py:16-18,31-33`); nieprawidłowy hash w DB → `False`, nie wyjątek (`:34-38`). Testy: `tests/test_passwords.py:12-36`, `tests/test_auth.py:55-76`.
- Polityka: 12–128 znaków (`passwords.py:11-12,41-50`); login dodatkowo odrzuca hasło > 1024 znaków na poziomie modelu żądania, zanim trafi do argon2 (`routes.py:36-37`); e-mail ≤ 320 znaków (`routes.py:35`, `models.py:19`).
- Argon2 jest liczony poza pętlą zdarzeń (`run_in_threadpool`, `routes.py:89-93`) — w Javie (model wątkowy servletów) ten problem nie występuje w tej samej formie.

### 4. Limiter prób logowania

- Okno przesuwne 15 min: 5 nieudanych prób na znormalizowany e-mail, 20 na IP (`app/auth/rate_limit.py:19-21`).
- Semantyka rezerwacji: `reserve()` atomowo sprawdza limit i liczy próbę w toku; `finish()` z wynikiem `failure`/`success`/`aborted` — tylko `failure` jest liczone, `success` zeruje licznik e-maila (nie IP), `aborted` (np. wyjątek/anulowanie) tylko zwalnia rezerwację (`rate_limit.py:106-134`; `routes.py:78-110`). Chroni przed równoległymi próbami, które wszystkie przeszłyby limit (`tests/test_rate_limit.py:71-148`).
- `Retry-After` = sekundy do wygaśnięcia najstarszej liczonej porażki, min. 1; gdy limit jest osiągnięty tylko przez próby w toku → 1 (`rate_limit.py:66-76`).
- Sprzątanie wygasłych kluczy raz na okno (`:58-64`; test `:150-170`).
- IP klienta: nagłówek `X-Real-IP` (ustawiany przez krawędź Railway), inaczej adres peera (`rate_limit.py:158-166`); w S-01 zweryfikowano, że krawędź nadpisuje podrobiony `X-Real-IP` (`archive/…/plan.md:384`).
- Stan tylko w pamięci procesu — poprawne przy 1 replice (`rate_limit.py:3-5`, `railway.ts:73`).

### 5. Ochrona Origin (CSRF)

- Dla `POST/PUT/PATCH/DELETE` pod `/api/`: jeśli nagłówek `Origin` jest obecny i jego `host[:port]` (małe litery) ≠ nagłówek `Host` → 403 `{"detail":"cross_origin"}`; brak `Origin` → przepuść (`app/security.py:18-34`). `Origin: null` jest odrzucany, ten sam host z innym portem też (`tests/test_security.py:15-46`).
- Działa za Caddy, bo `reverse_proxy` przekazuje oryginalny `Host` (`security.py:5-7`, `szczur-radar-web/Caddyfile:26-28`).
- Uwaga dla Javy: Spring Security ma własną ochronę CSRF (token) włączaną domyślnie — kontrakt S-01 jej nie używa (`archive/…/plan.md:57`), więc plan musi świadomie wybrać: wyłączyć ją i odtworzyć filtr Origin.

### 6. Model danych i migracje

- Tabela `users` (`migrations/versions/0001_create_users.py:25-50`): `id UUID PK DEFAULT gen_random_uuid()`, `email TEXT NOT NULL` + `uq_users_email` + `ck_users_email_lower (email = lower(email))`, `password_hash TEXT NULL`, `role TEXT NOT NULL DEFAULT 'user'` + `ck_users_role (role IN ('user','admin'))`, `token_version INTEGER NOT NULL DEFAULT 0`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
- Normalizacja e-maila `strip().lower()` w jednym miejscu, używana przez CLI i login (`app/models.py:22-28`).
- Reguły migracji: każda z działającym `downgrade()`, test round-trip upgrade→downgrade→upgrade na prawdziwym Postgresie i test jednej głowy (`tests/test_migrations.py:1-85`); ograniczenia z jawnymi nazwami (`szczur-radar-api/README.md:47-51`); hard rule w `REPOSITORY_GUIDELINES.md` („every migration needs a working `downgrade()`”).
- Na produkcji schemat już istnieje (`alembic current` = `0001_create_users (head)`, `deploy-plan.md:24`). Narzędzie migracji w Javie musi zacząć od istniejącego schematu (baseline), nie tworzyć tabeli ponownie. Tabela `alembic_version` zostanie osierocona — do decyzji w planie, czy ją usunąć migracją.
- `DATABASE_URL` z Railway ma postać `postgresql://user:pass@host:port/db`; kod Pythona normalizuje też `postgres://` (`app/database.py:26-36`). JDBC wymaga `jdbc:postgresql://host:port/db` z danymi logowania osobno — potrzebna konwersja albo osobne zmienne (`PGHOST`, `PGUSER`, … — do sprawdzenia, czy referencja Railway je udostępnia; `railway.ts:77` przekazuje dziś tylko `DATABASE_URL`).

### 7. CLI `create-admin`

- Wywołanie: `python -m app.cli create-admin --email <e-mail>`; na produkcji przez `railway ssh … -- uv run python -m app.cli create-admin` (`app/cli.py:1-11`, `deploy-plan.md:26`).
- Zachowanie (`cli.py:40-73`): advisory lock Postgresa `0x53525F41444D494E` w transakcji (serializuje równoległe procesy); odmowa, gdy istnieje jakikolwiek admin lub zajęty e-mail; hasło dwukrotnie z ukrytego promptu, nigdy jako argument; walidacja polityki; zapis `role='admin'`.
- Kody wyjścia: 0 utworzono, 1 odmowa, 2 błąd użycia/konfiguracji (brak `DATABASE_URL`, zły e-mail, e-mail > 320) (`cli.py:28-30,86-99`). Testy, w tym test równoległego bootstrapu: `tests/test_cli.py:49-163`.
- Wniosek: CLI musi działać w kontenerze produkcyjnym przez `railway ssh` bez startowania serwera HTTP (Spring Boot domyślnie startuje serwer — do rozstrzygnięcia w planie: osobny profil/klasa main bez web, albo `spring.main.web-application-type=none` dla komendy).

### 8. Railway

Z `.railway/railway.ts:56-85`:

- `rootDirectory: "szczur-radar-api"`, `watchPatterns: ["szczur-radar-api/**"]`; build Railpack (dziś wykrywa uv + `.python-version`).
- `start: 'uv run --no-dev hypercorn main:app --bind "[::]:$PORT"'` — bind dual-stack, bo healthcheck Railway idzie po IPv4, a proxy web→api po IPv6 (`railway.ts:56-60`; incydent: `deploy-plan.md:98-100,131`).
- `preDeploy: "uv run --no-dev alembic upgrade head"` — migracje w osobnym kontenerze przed startem nowej wersji; błąd zatrzymuje deploy (`railway.ts:68-71`).
- `healthcheck: "/api/health"`, `replicas: 1`, `sleepApplication: true`, `PORT: "8000"`, `DATABASE_URL` z referencji, `JWT_SECRET: preserve()` (`railway.ts:72-84`).
- Pułapka z historii: zmiana startu/pre-deploy w `railway.ts` działa dopiero po `railway config apply` — CI robi to w jobie `railway-config` przed deployem kodu (`deploy-plan.md:23,100,128`; `ci.yml:100-212`). Job blokuje zmiany `destructive`/`resource.delete` i zmiany `source` (`ci.yml:194-206`) — zmiana start/preDeploy/builder powinna przejść, ale do sprawdzenia `railway config plan` w PR env.
- `sleepApplication: true` oznacza zimny start po uśpieniu — czas startu JVM wpływa na pierwsze żądanie (obserwacja, bez pomiaru).

### 9. CI

- Job `backend` (`ci.yml:12-48`): Postgres 18 jako service, `TEST_DATABASE_URL`, `REQUIRE_DB_TESTS=1` (testy DB nie mogą być pominięte), `uv sync --locked`, `uv run pytest -q`. W Javie: odpowiednik z Gradle/Maven + ten sam Postgres service; zasada „testy DB nie są cicho pomijane” do zachowania.
- Workflow nie może mieć filtrów ścieżek (Wait for CI, `ci.yml:3-5`, `REPOSITORY_GUIDELINES.md` § Deployment).
- Lokalnie: `compose.local.yaml` daje Postgres 18 na `127.0.0.1:5432` i web z proxy na `host.docker.internal:8000`; `vite.config.ts` proxy `/api` → `localhost:8000` — port 8000 lokalnie też do zachowania.

### 10. Pliki do aktualizacji poza kodem

| Plik | Co mówi o Pythonie |
|---|---|
| `REPOSITORY_GUIDELINES.md` | struktura `szczur-radar-api/`, komendy `uv`, `alembic`, `pytest`, `PUBLIC_ROUTES` w `test_authz_completeness.py` |
| `README.md:21` | `uv run python main.py` |
| `szczur-radar-api/README.md` | cały plik (uv, Alembic, pytest) |
| `.gitignore:5-8` | sekcja Python |
| `context/foundation/tech-stack-api.md` | frontmatter `starter_id: fastapi`, `language_family: python`, sekcja „Why this stack” |
| `context/foundation/tech-stack-web.md:29` | „the FastAPI backend”, „Pydantic types” |
| `context/foundation/infrastructure.md:7-10,21,33,58,84,111,126` | Python 3.13, uv, Railpack `.python-version` |
| `context/deployment/deploy-plan.md` | komendy `uv run`, Hypercorn, troubleshooting Pythona |
| `.railway/railway.ts:56-71` | start, pre-deploy, komentarze |
| `.github/workflows/ci.yml:12-48` | job `backend` |

Dokumenty w `context/archive/` zostają bez zmian (niezmienne).

## Code References

- `szczur-radar-api/main.py:23-34` — fail-fast na `JWT_SECRET`, middleware, routery
- `szczur-radar-api/app/routes.py:18-35` — sondy health/ready
- `szczur-radar-api/app/security.py:18-34` — filtr Origin
- `szczur-radar-api/app/auth/routes.py:29-123` — login/logout/me, atrybuty ciasteczka
- `szczur-radar-api/app/auth/tokens.py:17-72` — JWT
- `szczur-radar-api/app/auth/passwords.py:11-50` — argon2, atrapa, polityka
- `szczur-radar-api/app/auth/rate_limit.py:19-166` — limiter i `client_ip`
- `szczur-radar-api/app/auth/dependencies.py:17-46` — `current_user`, `require_admin`
- `szczur-radar-api/app/models.py:17-62` — model `User`, normalizacja e-maila
- `szczur-radar-api/app/cli.py:28-99` — `create-admin`
- `szczur-radar-api/app/database.py:26-75` — normalizacja URL, `check_database`
- `szczur-radar-api/migrations/versions/0001_create_users.py:25-54` — schemat `users`
- `szczur-radar-api/tests/` — 1194 linie specyfikacji zachowań (10 plików)
- `szczur-radar-web/src/api/auth.ts:4-27`, `src/api/client.ts:37-81`, `src/views/LoginView.vue:18-23` — konsument API
- `szczur-radar-web/Caddyfile:26-28` — proxy `/api/*` z zachowaniem ścieżki i `Host`
- `.railway/railway.ts:32,56-85` — konfiguracja serwisu API
- `.github/workflows/ci.yml:12-48,100-212` — testy backendu, apply IaC

## Architecture Insights

- Warstwa auth jest celowo cienka i własna (decyzja z 2026-09-27, `tech-stack-api.md` „Update 2026-09-27”; FastAPI Users odrzucone). W Javie analogiczne pytanie: pełny Spring Security z własnym filtrem JWT-z-ciasteczka czy cieńsza warstwa — kontrakt zachowań i tak pozostaje ten sam.
- Wzorzec „test kompletności autoryzacji” jest zasadą repozytorium, nie szczegółem implementacji — musi mieć odpowiednik w Javie od pierwszej fazy.
- Zasada lekcji „proaktywnie szukaj okazji do abstrakcji” (`context/foundation/lessons.md`, ostatni wpis) i „jedna gałąź + jeden PR, weryfikacja w PR env” (`lessons.md`) dotyczą tej zmiany wprost.
- Kod Pythona można trzymać obok Javy do czasu przełączenia — `rootDirectory` w `railway.ts` wskazuje jeden katalog, więc przełączenie to zmiana tej wartości (lub zastąpienie zawartości katalogu).

## Historical Context (from prior changes)

- `context/archive/2026-09-27-admin-bootstrap-and-login/plan.md:52-57` — kontrakt uwierzytelniania (transport, token, sesja, CSRF): **nadal obowiązuje** — kod i testy są z nim zgodne (sprawdzone powyżej, sekcje 2 i 5).
- `…/plan.md:220` — kontrakt `/api/auth/login`: **nadal obowiązuje**, poza drobnym rozszerzeniem: limit 1024 znaków hasła w modelu żądania (`routes.py:36-37`) nie jest wymieniony w planie.
- `…/plan.md:384` — weryfikacja `X-Real-IP` w PR env: **dotyczy także Javy** — do powtórzenia po przepisaniu.
- `context/deployment/deploy-plan.md:98-100,131` — IPv4 healthcheck vs bind IPv6: **dotyczy Javy** (inny serwer, ten sam wymóg dual-stack).
- `context/deployment/deploy-plan.md:24-27` — stan produkcji (migracja `0001`, pierwszy admin, sekret): **aktualny** na dzień badania wg dokumentu; nie weryfikowano na żywo.

## Related Research

- `context/changes/assessment-contract-harness/research.md` — badanie F-02 (ocena LLM); nie dotyczy zakresu S-01. Uwaga na później: `context/foundation/llm-assessment/contract-v2.md:290` definiuje „znak biały” przez Pythonowe `str.isspace()` (m.in. U+00A0, U+2007, U+202F). Java `Character.isWhitespace` nie traktuje tych trzech znaków jako białych — przy F-02 w Javie kontrakt wymaga definicji niezależnej od języka albo jawnej listy znaków.

## Open Questions

Do badania zewnętrznego (Context7 / exa) przed `/10x-plan`:

1. Wersja Spring Boot i Javy (LTS) wspierana przez Railpack lub potrzeba własnego Dockerfile; jak Railpack wykrywa Gradle vs Maven.
2. Czy wbudowany serwer (Tomcat/Undertow/Netty) z `server.address=::` słucha dual-stack na Railway (IPv4 healthcheck + IPv6 private networking).
3. Czy `Argon2PasswordEncoder` ze Spring Security (Bouncy Castle) weryfikuje istniejący hash PHC z pwdlib (`m=65536,t=3,p=4`, sól 16 B, hash 32 B, base64 bez paddingu) — test na prawdziwym hashu.
4. Biblioteka JWT (jjwt vs Nimbus przez Spring Security OAuth2 Resource Server) i czy łatwo wymusić: tylko HS256, wymagane `exp`/`iat`/`sub`/`ver`, `ver` jako int (nie bool, nie string).
5. Flyway vs Liquibase: baseline istniejącej bazy (`baselineOnMigrate`/`baselineVersion=1`), migracje odwracalne (Flyway undo jest płatny w części wersji — sprawdzić; Liquibase ma rollback) — zasada „każda migracja ma działający downgrade” zależy od tego wyboru.
6. Konwersja `DATABASE_URL` (`postgresql://user:pass@host/db`) na JDBC w Spring Boot i czy Railway udostępnia `PGHOST`/`PGUSER`/… jako referencje.
7. CLI w Spring Boot bez startu serwera HTTP (Spring Shell vs `CommandLineRunner` z profilem, `web-application-type=none`) i ukryty prompt (`System.console().readPassword()`) przez `railway ssh`.
8. Testy: Postgres w CI jako service (jak dziś) vs Testcontainers; odpowiednik `REQUIRE_DB_TESTS=1`.
9. Czas zimnego startu JVM przy `sleepApplication: true` — czy potrzebne CDS/AOT.

Decyzje produktowe/planistyczne (nie do zbadania w kodzie):

- Czy zachować ważność istniejących sesji produkcyjnych (ten sam format JWT i sekret), czy zaakceptować jednorazowe ponowne logowanie.
- Czy usunąć tabelę `alembic_version` po przejściu.
- Czy Java powstaje w nowym katalogu obok Pythona (przełączenie `rootDirectory`), czy zastępuje `szczur-radar-api/` w miejscu.

## Follow-up 2026-10-01: decyzje użytkownika i Embabel

### Decyzje użytkownika (zastępują część ustaleń powyżej)

1. **Baza produkcyjna zostanie wyczyszczona.** Na produkcji jest tylko tabela `users` z jednym adminem (`deploy-plan.md:24-26`), więc użytkownik zgodził się na jej usunięcie. Skutki:
   - **nieaktualne:** wymóg weryfikacji istniejącego hasha pwdlib w Javie (Summary pkt 2, sekcja 3, Open Question 3), baseline istniejącego schematu (sekcja 6, Open Question 5 — część o baseline), decyzja o tabeli `alembic_version` i o zachowaniu istniejących sesji (Open Questions → decyzje planistyczne 1–2);
   - **nadal aktualne:** schemat `users` i jego ograniczenia jako kontrakt (sekcja 6), zasada odwracalnych migracji, migracje w pre-deploy;
   - nowy krok operacyjny dla planu: wyczyszczenie bazy produkcyjnej przed pierwszym deployem Javy i ponowne utworzenie admina przez CLI w Javie. To nieodwracalna operacja na produkcji — wykonuje ją operator, a PITR jest włączone (`deploy-plan.md:82`).
2. **Sesje:** po wyczyszczeniu bazy wszystkie istniejące sesje i tak tracą ważność (brak użytkowników), więc format JWT nie musi być zgodny wstecz. Kontrakt zachowań z sekcji 2 (claimy, wymagane pola, `token_version`, atrybuty ciasteczka) nadal obowiązuje, bo zależą od niego SPA i testy.
3. **Java zastępuje katalog `szczur-radar-api/` w miejscu** (bez katalogu obok). `rootDirectory` i `watchPatterns` w `railway.ts:63-64` zostają bez zmian; zmieniają się start, pre-deploy i sposób budowania. Kod w Pythonie znika w tej samej zmianie, więc testy w Pythonie przestają być wyrocznią w CI — ich treść trzeba przenieść jako testy w Javie przed usunięciem.
4. **Framework: Spring Boot** (potwierdzone). **Warstwa agentów LLM: Embabel** jako kandydat w miejsce LangGraph (dotyczy F-02, nie zakresu S-01 tej zmiany — ale wpływa na wybór wersji Spring Boot już teraz, patrz niżej).

### Embabel — badanie zewnętrzne

Źródła: https://github.com/embabel/embabel-agent (README, releases), https://docs.embabel.com/llms.txt, `pom.xml` tagu `v1.5.1` (pobrany 2026-10-01), wiki „Spring Boot 4 / Spring AI 2.0 Migration”.

- **Czym jest:** framework agentów na JVM od Roda Johnsona (twórcy Springa), Apache 2.0, ~4,3 tys. gwiazdek; napisany w Kotlinie, z naturalnym API w Javie (adnotacje `@Agent`, `@Action`, `@Goal`, `@Condition`, w stylu Spring MVC). Agenty są beanami Springa. README: „Spring AI is great. We build on it… Spring AI exists at the level of the Servlet API, while Embabel is more like Spring MVC.”
- **Wersja:** najnowsze wydanie 1.5.1 z 24.08.2026 (GitHub releases); dokumentacja wskazuje już 1.5.3-SNAPSHOT (`llms.txt`). Linia 1.0.0 → 1.5.x jest stabilna (wcześniej wydania 0.x z nazwami miejscowości).
- **Wymaga Spring Boot 4:** `pom.xml` v1.5.1 przypina `openai-java` 4.39.1 „matching spring-ai-openai 2.0.0” (linie 21-23); wydanie 1.5.0 zawiera „Spring AI 2.0.0 GA Migration” i przejście na Jackson 3 (`tools.jackson.*`); wiki migracji opisuje parę Spring Boot 4.0.x + Spring AI 2.0. **Wniosek: jeśli F-02 ma użyć Embabel, backend od początku powinien być na Spring Boot 4** (inaczej F-02 zaczyna się od podbicia wersji).
- **Java:** przykłady wymagają Java 21+ (`embabel-agent-examples`, sekcja „Prerequisites”); `llms.txt` ma osobny rozdział „Java 25 implications” (model wątków).
- **Dostawcy z naszego pilotażu F-02:** są startery dla OpenAI, DeepSeek (`embabel-agent-starter-deepseek`) i Gemini (Google GenAI natywnie lub przez API zgodne z OpenAI) — user guide 1.5.1, sekcja „LLM providers”; zmienne `OPENAI_API_KEY`, `DEEPSEEK_API_KEY`, `GEMINI_API_KEY`/`GOOGLE_GENAI_API_KEY` (README). Wydanie 0.5.0 dodało natywne structured output dla OpenAI; 1.5.0 przeniosło je na Spring AI 2.
- **Ustrukturyzowane wyjście:** `ai.withLlm(...).createObject(prompt, Klasa.class)` / `createObjectIfPossible` (README, przykład `StarNewsFinder`) — odpowiednik dzisiejszego schematu z Pydantic. Do sprawdzenia przy F-02: jak Embabel buduje schemat JSON z klas Javy i czy da się wymusić zamknięte modele (odrzucanie obcych pól) — w Pythonie to `extra="forbid"`.
- **Pasowanie do naszego kształtu:** ocena F-02 to deterministyczny łańcuch kroków (prefiltr → prompt → wywołanie → parsowanie → walidacja reguł), a nie otwarte planowanie. Embabel ma tryb **Focused** („user code calls a method to run a particular agent”) i planowanie GOAP wyprowadzane z typów wejść/wyjść akcji — łańcuch typowanych `@Action` powinien dać tę samą sekwencję. Planowanie dynamiczne to możliwość, z której nie musimy korzystać.
- **Sygnał jakości (CLAUDE.md, dyscyplina badawcza):** Embabel publikuje `llms.txt` i agent „Talk to the Docs” — dokumentacja przyjazna agentom.
- **Ryzyka:** młody projekt (repo od 04.2025), szybkie zmiany API między wersjami (README przykładów: „minor API incompatibilities” między snapshotami a szablonami); zależność od Spring AI 2.0, które dopiero wyszło z fazy milestone.

### Zaktualizowane pytania do badania zewnętrznego

Bez zmian: 1, 2, 4, 6, 7, 8, 9. Zmienione:

- **3 — nieaktualne** (czysta baza; nowe hashe tworzy Java).
- **5 — zawężone:** Flyway vs Liquibase już tylko pod kątem odwracalnych migracji (Flyway undo w wersji darmowej? Liquibase rollback?), bez baseline.
- **Nowe 10:** Spring Boot 4.0.x — czy Railpack/obraz bazowy i biblioteki z tej zmiany (Spring Security, jjwt/Nimbus, Flyway/Liquibase, Testcontainers) są już zgodne z Boot 4 i Jackson 3.
- **Nowe 11:** Maven czy Gradle — Embabel i jego szablony używają Mavena (`java-agent-template`, „Maven 3.9+”), co jest argumentem za Mavenem.

## Follow-up 2 (2026-10-01): badanie zewnętrzne — Spring Security i platforma

Pytanie użytkownika: „czy Spring Security nie ogarnie większości logowania i autoryzacji z pudełka?”. Źródła: dokumentacja Spring Security 7.x (docs.spring.io/spring-security/reference: CSRF, Persisting Authentication, Session Management, One-Time Token Login, What's New 7.0), Spring Session 4.x (JDBC, API), Railpack (railpack.com/languages/java, kod `core/providers/java/*.go`), dokumentacja Railway (Railpack, How Private Networking Works, Library Configuration, zmienne Postgresa), porównania Flyway/Liquibase z 2026 (bytebase.com 2026-09-15, stribog.com 2026-10-01, jusdb.com 2026-01-31).

### Odpowiedź: tak, większość — jeśli zamienimy JWT na sesję serwerową

Kontrakt S-01 (JWT HS256 w ciasteczku + `token_version`) był pisany pod FastAPI, który nie ma własnego mechanizmu sesji. W Spring Security idiomatyczny odpowiednik to **sesja serwerowa** (`HttpSession`) — i wtedy prawie cała sekcja 2–5 tego researchu jest „z pudełka”:

| Wymóg z S-01 (sekcje 2–5) | Spring Security / Spring Session | Własny kod? |
|---|---|---|
| Hash haseł argon2id, weryfikacja | `Argon2PasswordEncoder` (wymaga Bouncy Castle) lub `DelegatingPasswordEncoder` | nie |
| Wyszukanie użytkownika po e-mailu | `UserDetailsService` nad tabelą `users` | mały adapter |
| Wyrównanie czasu dla nieznanego e-maila | `DaoAuthenticationProvider` (ochrona przed timing attack przy „user not found”) | nie |
| Konto bez hasła nie loguje się | `UserDetails` z flagą `enabled=false` / brak hasła | mały adapter |
| Sesja w ciasteczku HttpOnly, Secure, SameSite=Strict | `HttpSession` + Spring Session JDBC (`DefaultCookieSerializer`: `sameSite`, `useSecureCookie`, nazwa ciasteczka) | konfiguracja |
| Unieważnienie wszystkich sesji użytkownika (rola `token_version`, S-07) | Spring Session JDBC: `findByPrincipalName(username)` → usunięcie sesji; Spring Security sam indeksuje nazwę użytkownika | kilka linii |
| Ochrona przed session fixation | wbudowana (zmiana ID sesji po logowaniu) | nie |
| Logout | `logout()` unieważnia sesję i kasuje ciasteczko | konfiguracja |
| Każda trasa wymaga sesji, wyjątki jawnie | `authorizeHttpRequests(...).requestMatchers(<publiczne>).permitAll().anyRequest().authenticated()` — deny-by-default z konstrukcji | konfiguracja + test |
| Rola admin → 403 | `hasRole("ADMIN")` | konfiguracja |
| CSRF | `csrf(csrf -> csrf.spa())` (nowość 7.0) **albo** wyłączone CSRF + filtr Origin jak dziś | wybór, patrz niżej |
| Link aktywacyjny (S-07, FR-027/028) | One-Time Token Login (`oneTimeTokenLogin()`, `JdbcOneTimeTokenService`; dostarczenie tokenu przez własny `OneTimeTokenGenerationSuccessHandler`, domyślna ważność 5 min, konfigurowalna) | do oceny w S-07 |

**Czego Spring Security nie daje i trzeba dopisać:**

1. **Endpoint logowania JSON.** Wbudowane `formLogin()` przyjmuje `application/x-www-form-urlencoded`, a SPA wysyła JSON (`szczur-radar-web/src/api/auth.ts:11-17`). Dokumentacja pokazuje wzorzec: kontroler `@PostMapping("/login")` → `authenticationManager.authenticate(...)` → `securityContextRepository.saveContext(...)` (od Spring Security 6 zapis kontekstu jest jawny — „Persisting Authentication”, „Session Management”). To ok. 15–20 linii.
2. **Limit nieudanych logowań** (sekcja 4). Spring Security nie ma limitera prób logowania. Do wyboru: przeniesienie dzisiejszej klasy (~130 linii, logika rezerwacji opisana w sekcji 4) albo Bucket4j. Semantyka „rezerwacji” z dzisiejszego kodu (równoległe próby) wymaga własnej logiki także przy Bucket4j.
3. **Odpowiedzi JSON w formacie `{"detail": "..."}`** dla 401/403/429 (SPA czyta `detail` i status, `client.ts:43-81`). Domyślnie Spring Security dla API bez sesji robi przekierowanie lub pustą 401 — potrzebny własny `AuthenticationEntryPoint` (401 `not_authenticated`) i `AccessDeniedHandler` (403 `forbidden`).
4. **CLI `create-admin`** (sekcja 7) — własny kod (Spring Boot bez serwera WWW, `System.console().readPassword()`).

### Konsekwencje zamiany JWT na sesję

- **Zalety:** mniej własnego kodu bezpieczeństwa (brak ręcznego JWT, `decode_token`, testów `alg=none` itp.); unieważnianie sesji bez `token_version`; standardowe wzorce, które autor zna i może ocenić.
- **Wymóg platformy:** API ma `sleepApplication: true` (`railway.ts:74`) i 1 replikę — sesje w pamięci Tomcata znikałyby przy każdym uśpieniu/restarcie (wylogowanie). **Sesje muszą być w Postgresie (Spring Session JDBC)**: tabele `SPRING_SESSION` i `SPRING_SESSION_ATTRIBUTES` (skrypt dla PostgreSQL w dokumentacji), sprzątanie wygasłych sesji co minutę (`spring.session.jdbc.cleanup-cron`).
- **Pułapka Boot 4:** w Spring Boot 4 trzeba dodać moduł `spring-boot-session`, inaczej `server.servlet.session.cookie.same-site` jest ignorowane (spring-session issue #3622).
- **Zmiana kontraktu S-01:** JWT, `iat/exp/sub/ver` i kolumna `token_version` przestają być potrzebne; ważność sesji: dziś 30 dni stałe (`tokens.py:17`) — w Spring Session to `maxInactiveInterval` (bezczynność) + max-age ciasteczka; automatyczne wygasanie sesji to FR-029 (nice-to-have, odłożone w roadmapie). SPA nie zależy od nazwy ani zawartości ciasteczka (HttpOnly) — zmiana jest dla SPA niewidoczna, o ile ścieżki i odpowiedzi się nie zmienią.
- Tę zmianę kontraktu trzeba zapisać w planie i w `tech-stack-api.md` (jak „Update 2026-09-27” dla FastAPI Users).

### CSRF: dwie poprawne opcje

- **A — jak dziś:** CSRF Springa wyłączone, `SameSite=Strict` + filtr Origin (sekcja 5, ~15 linii). Zero zmian w SPA. Zgodne z zapisanym kontraktem S-01.
- **B — `csrf.spa()`:** token w ciasteczku `XSRF-TOKEN`, SPA odsyła nagłówek `X-XSRF-TOKEN`. Standard Springa, ale wymaga zmiany w `client.ts` i pobrania tokenu przed logowaniem (dokumentacja: token jest czyszczony po zalogowaniu i wylogowaniu i trzeba go odświeżyć).
- Rekomendacja badania: **A**, bo `SameSite=Strict` przy same-origin już blokuje wysłanie ciasteczka z obcej strony, a A nie wymaga zmian w SPA. B to świadomy wybór „bardziej z pudełka”.

### Railpack i Java

- Railpack wykrywa Javę po `pom.xml` (Maven) lub `gradlew` (Gradle) w katalogu głównym serwisu; JDK domyślnie **21**, zmiana przez `RAILPACK_JDK_VERSION` (railpack.com/languages/java).
- Maven: build `./mvnw … -DskipTests clean dependency:list install` (z wrapperem, jeśli jest `mvnw` + `.mvn/wrapper/maven-wrapper.properties`); start `java -Dserver.port=$PORT $JAVA_OPTS -jar target/*jar` dla Spring Boot (`core/providers/java/maven.go`, `java.go`). Nasz start i pre-deploy i tak ustawia `railway.ts`.
- Spring Boot 4 i Embabel działają na Javie 21 (Railpack default), więc JDK 21 wystarcza; Java 25 przez `RAILPACK_JDK_VERSION=25`, jeśli plan tak zdecyduje.

### Sieć prywatna Railway (bind IPv4/IPv6)

- Środowiska utworzone po 16.10.2025 rozwiązują `*.railway.internal` na IPv4 **i** IPv6; starsze („legacy”) tylko na IPv6 (docs: „How Private Networking Works → DNS resolution”). Projekt `szczur-radar` powstał 2026-09-27 (`deploy-plan.md:71`), więc według tej reguły jest środowiskiem „nowym” — incydent z IPv4 healthcheck (`deploy-plan.md:98`) wynikał z bindu uvicorna tylko na IPv6.
- JVM domyślnie otwiera gniazda dual-stack (`java.net.preferIPv4Stack=false`), więc Tomcat z domyślnym adresem nasłuchuje na IPv4 i IPv6. Railway nie wymienia Javy w „Library Configuration” jako wymagającej specjalnej konfiguracji. **Weryfikacja w PR env nadal obowiązkowa** (healthcheck + proxy web→api), jak w S-01.

### Baza: `DATABASE_URL` → JDBC

- Usługa Postgres na Railway udostępnia `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` obok `DATABASE_URL` (docs Railway, „Provision the database”). W `railway.ts` można je zreferencjonować (`db.env.PGHOST` itd. — nazwa pola w SDK do sprawdzenia przy `railway config plan`) i złożyć `spring.datasource.url=jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}` bez parsowania URL w kodzie.

### Migracje: Flyway vs Liquibase

- **Flyway Community (darmowy):** brak `undo` — to funkcja płatnych edycji (Teams/Enterprise); „rollback” = ręcznie napisana migracja w przód (bytebase.com, stribog.com).
- **Liquibase Community:** `rollback`, `rollback-count`, `rollback-to-date` we wszystkich edycjach; automatyczny rollback dla typów wbudowanych (np. `createTable`), jawne bloki `rollback` dla SQL (jusdb.com, stribog.com). **Uwaga:** od Liquibase Community 5.0 (09.2025) licencja to Functional Source License (FSL), nie Apache 2.0 (stribog.com).
- Oba mają autokonfigurację w Spring Boot.
- Wniosek: zasada repo „każda migracja ma działający downgrade, testowany round-tripem” (`REPOSITORY_GUIDELINES.md`, `tests/test_migrations.py`) jest wprost spełnialna w Liquibase Community; w Flyway Community wymagałaby zmiany zasady (np. „odwracalność przez migrację w przód + PITR”). To decyzja dla planu — z flagą o licencji FSL.

### Zaktualizowane pytania otwarte

- 1 (Railpack/Java) — **rozstrzygnięte** (wyżej).
- 2 (dual-stack) — **rozstrzygnięte warunkowo**, do weryfikacji w PR env.
- 4 (biblioteka JWT) — **nieaktualne, jeśli plan wybierze sesję serwerową**.
- 5 (migracje) — **rozstrzygnięte faktograficznie**; wybór Flyway vs Liquibase to decyzja dla planu.
- 6 (`DATABASE_URL`) — **rozstrzygnięte** (zmienne `PG*`).
- 7 (CLI bez serwera) — wzorzec znany (aplikacja Spring Boot z `WebApplicationType.NONE` lub osobna klasa main); szczegóły w planie.
- 8 (testy: service Postgres vs Testcontainers) — otwarte, decyzja dla planu.
- 9 (zimny start JVM przy uśpieniu) — otwarte; pomiar w PR env.
- 10 (zgodność bibliotek z Boot 4) — Spring Security 7, Spring Session 4 i Embabel 1.5 są zgodne z Boot 4 według ich dokumentacji; Liquibase/Flyway do potwierdzenia wersją w POM przy implementacji.
- 11 (Maven vs Gradle) — Railpack obsługuje oba; Embabel i jego szablony używają Mavena.

### Decyzje dla `/10x-plan`

1. Sesja serwerowa (Spring Session JDBC) zamiast JWT — rekomendowane przez to badanie.
2. CSRF: A (Origin + SameSite, bez zmian w SPA) czy B (`csrf.spa()`).
3. ~~Flyway czy Liquibase~~ — **rozstrzygnięte przez użytkownika 2026-10-01: Flyway** (migracje jako pliki SQL). Konsekwencja dla planu: zasadę „każda migracja ma działający `downgrade()`” (`REPOSITORY_GUIDELINES.md`) zastępuje fix-forward (naprawa kolejną migracją) + PITR jako ostateczność; zaktualizować regułę w `REPOSITORY_GUIDELINES.md` i test round-trip zastąpić testem „migracje od zera na pustej bazie”.
4. Limiter logowania: port własnej klasy czy Bucket4j.
5. Java 21 czy 25; Maven (rekomendowany przez zgodność z Embabel).
