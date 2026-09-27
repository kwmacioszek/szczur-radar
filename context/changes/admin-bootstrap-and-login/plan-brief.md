# Pierwszy admin przez CLI, logowanie/wylogowanie i ochrona tras — Plan Brief

> Full plan: `context/changes/admin-bootstrap-and-login/plan.md`

## What & Why

Roadmapa S-01: operator tworzy pierwsze konto administratora przez CLI, admin loguje się e-mailem i hasłem oraz wylogowuje, a niezalogowany widzi wyłącznie ekran logowania. Bez tego żadne dane (S-02) nie mogą trafić na publiczny adres; zmiana ustala też sposób wdrażania migracji i wzorzec testów autoryzacji, z którego korzystają wszystkie kolejne trasy.

## Starting Point

API ma tylko sondy `/api/health` i `/api/ready` oraz leniwy async engine (`SELECT 1`); brak modeli, migracji, CLI i Postgresa w CI. SPA to statyczna strona „W przygotowaniu” bez routera i testów; SPA i API są same-origin przez proxy Caddy `/api/*`.

## Desired End State

`python -m app.cli create-admin --email …` tworzy pierwszego admina (ukryty prompt hasła, odmowa gdy admin już istnieje). Logowanie ustawia ciasteczko HttpOnly z JWT na 30 dni, `/api/auth/me` zwraca tylko własne dane, wylogowanie czyści sesję. Każda trasa API spoza jawnej allowlisty zwraca 401 bez sesji — wymusza to test iterujący po wszystkich trasach. W SPA każdy adres bez sesji prowadzi na `/login`. Migracja `users` jest uruchamiana pre-deploy na Railway, a jej downgrade sprawdzany w CI.

## Key Decisions Made

| Decyzja | Wybór | Dlaczego |
| --- | --- | --- |
| Biblioteka auth | Własna cienka warstwa: PyJWT + pwdlib/argon2 (zamiast FastAPI Users) | Skrojona pod rolę, `token_version`, CLI i brak rejestracji; FastAPI Users jest w trybie maintenance |
| Transport i token | JWT HS256 w ciasteczku `HttpOnly; Secure; SameSite=Strict; Path=/api` | Token niedostępny dla JS, same-origin przez proxy, bezstanowy |
| Unieważnianie | `token_version` w claimie `ver`; logout czyści ciasteczko | Awaryjne unieważnienie wszystkich tokenów użytkownika (S-07) bez tabeli sesji |
| Ważność sesji | 30 dni, stałe, bez przedłużania | Nie wylogowuje w trakcie czytania; FR-029 odłożone |
| CSRF | SameSite=Strict + middleware sprawdzające `Origin` + login tylko JSON | Obrona warstwowa bez tokenów CSRF |
| Bootstrap CLI | Hasło 2× przez `getpass`; odmowa (kod 1), gdy jakikolwiek admin istnieje | Brak hasła w historii powłoki; recovery zostaje w S-07 |
| Brute-force | Limit w pamięci: 5/15 min per e-mail, 20/15 min per IP (`X-Real-IP`) → 429 | Tania ochrona publicznego adresu przy 1 replice API |
| Migracje | Alembic, pre-deploy na Railway, test upgrade→downgrade→upgrade | Nowy kod nie startuje bez schematu; każda migracja ma działające cofnięcie |
| Testy DB | Prawdziwy Postgres jako kontener usługi w CI | SQLite nie sprawdzi migracji ani ograniczeń Postgresa |
| Testy web | Vitest + @vue/test-utils (guard, klient, widok logowania) | Wzorzec testów frontendu dla kolejnych wycinków |
| Sekret JWT | Ustawiany ręcznie, sealed na prod, osobny w PR env; nigdy w `railway.ts` | `ctx.randomString` z SDK jest deterministyczny, więc wyliczalny |

## Scope

**In scope:**
- Model `users` (UUID, e-mail lowercase unique, nullable `password_hash`, rola `user|admin`, `token_version`) + migracja z downgrade
- Postgres w CI, fixture'y testów DB, pre-deploy migracji w `railway.ts`
- `/api/auth/login|logout|me`, `current_user`, `require_admin`, limit prób, middleware Origin
- CLI `create-admin`, test kompletności autoryzacji, notka w `tech-stack-api.md`
- SPA: vue-router, ekran logowania, guard, strona startowa z wylogowaniem, Vitest w CI, aktualizacja AGENTS.md
- `JWT_SECRET`, weryfikacja w PR env, jeden merge, pierwszy admin na produkcji

**Out of scope:**
- Aktywacja linkiem, konta `user` przez CLI, recovery admina (S-07)
- Refresh tokeny, denylista, tabela sesji, wygasanie po bezczynności (FR-029)
- Blokada konta, rozproszony limit, 2FA, reset hasła, panel kont
- Trasy danych i E2E w przeglądarce

## Architecture / Approach

Przeglądarka → Caddy (`/api/*`) → FastAPI. `POST /api/auth/login` weryfikuje hasło (argon2) po sprawdzeniu limitu i ustawia ciasteczko z JWT (`sub`, `ver`, `exp`). Każda chroniona trasa dołącza `current_user`, który dekoduje JWT, ładuje użytkownika z DB i porównuje `ver` z `token_version`. SPA trzyma tylko reaktywny stan „kto jest zalogowany” z `GET /api/auth/me`; guard routera przekierowuje na `/login`, a 401 z dowolnego wywołania czyści sesję. CLI i trasy dzielą moduły `app/auth/passwords.py` i `app/models.py`.

## Phases at a Glance

| Faza | Co dostarcza | Główne ryzyko |
| --- | --- | --- |
| 1. Warstwa danych i migracje | Model `users`, Alembic z downgrade, pre-deploy, Postgres w CI, fixture'y | Engine związany z pętlą zdarzeń w testach |
| 2. Uwierzytelnianie API i CLI | Trasy auth, zależności, limit, Origin, `create-admin`, test kompletności autoryzacji | Wyciek danych przez trasę spoza testu — łagodzone iteracją po wszystkich trasach |
| 3. Logowanie w SPA | Router, guard, ekran logowania, wylogowanie, Vitest w CI | Otwarte przekierowanie przez `?redirect=` — sanitacja + test |
| 4. Wdrożenie i pierwszy admin | Sekret, weryfikacja w PR env, merge, admin na produkcji | PR env bez `JWT_SECRET`; `X-Real-IP` inny niż zakładany |

**Prerequisites:** lokalny Postgres (np. Docker) do testów DB; dostęp operatora do Railway (zmienne, `railway ssh`); jedna gałąź `feature/admin-bootstrap-and-login` + jeden PR (lessons.md).
**Estimated effort:** ~4 sesje, po jednej na fazę; faza 4 to głównie praca operatorska.

## Open Risks & Assumptions

- Zakładamy, że krawędź Railway ustawia `X-Real-IP` z prawdziwym adresem klienta; jeśli nie, limit per IP zbiega do jednego kubełka — weryfikacja w PR env (4.4), limit per e-mail działa niezależnie.
- Zakładamy, że Caddy przekazuje oryginalny nagłówek `Host` (domyślne zachowanie v2) — inaczej middleware Origin odrzuci poprawne żądania; wykrywane w PR env.
- PR env wymaga ręcznego ustawienia `JWT_SECRET` dla każdego nowego PR, dopóki nie powstanie lepszy mechanizm — aplikacja bez sekretu nie wystartuje (fail-fast).
- Kolizja z F-02 w `pyproject.toml`/`uv.lock` i konfiguracji pytest — scalić przy merge.

## Success Criteria (Summary)

- CI zielone: testy backendu na Postgresie (w tym migracja w obie strony i test kompletności autoryzacji) oraz testy web.
- W PR env i na produkcji: pierwszy admin utworzony przez CLI loguje się i wylogowuje, a niezalogowany widzi tylko ekran logowania.
- Każda przyszła trasa API bez sesji zwraca 401 albo świadomie trafia do allowlisty.
