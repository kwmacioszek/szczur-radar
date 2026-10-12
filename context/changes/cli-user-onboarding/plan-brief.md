# S-07 cli-user-onboarding — Plan Brief

> Full plan: `context/changes/cli-user-onboarding/plan.md`
> Research: `context/changes/cli-user-onboarding/research.md`

## What & Why

Dziś na produkcji jest jedno konto: admin z `create-admin`, i nie ma żadnej ścieżki odzyskania dostępu. S-07 pozwala operatorowi przez CLI zakładać konta i resetować hasła jednorazowym, wygasającym linkiem. Zaproszony sam ustawia hasło w SPA. Pokrywa to US-02, FR-018, FR-027 i FR-028 (`must-have`), bez wysyłki e-maili.

## Starting Point

Konto bez hasła już istnieje w schemacie i nie może się zalogować (wygląda jak złe hasło). Polityka haseł, argon2 i szkielet komend CLI są gotowe. Brakuje tabeli tokenów, publicznych tras, widoku SPA, adresu strony dla CLI i komendy recovery. Komendy CLI nie mają repozytorium sesji Springa, więc sesje usuwają SQL-em.

## Desired End State

`create-user --email jan@x [--role admin]` wypisuje `https://<web>/activate#<token>`, ważny 12 h. Jan widzi „Ustaw hasło dla jan@x”, ustawia hasło, trafia na `/login` z komunikatem i się loguje. `reset-password --email <e>` od razu wylogowuje konto, blokuje stare hasło i daje nowy link. Link zużyty, wygasły lub zastąpiony pokazuje „nieważny, poproś o nowy”.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Ważność linku | 12 h domyślnie, `ACTIVATION_LINK_TTL` z jednostką (1m–7d) | Decyzja użytkownika; jednostka chroni przed pułapką „liczba = ms” | Research (follow-up) |
| Recovery (FR-027) | `reset-password`: kasuje hasło i sesje, nowy link | Jedna ścieżka dla aktywacji, nowego linku i recovery; hasło nie przechodzi przez terminal | Research (follow-up) |
| Zakres resetu | Każde konto, nie tylko admin | Operator pomoże też userowi; doprecyzowanie dopisane do PRD | Research (follow-up) |
| Po ustawieniu hasła | Przejście na `/login`, bez automatycznego logowania | Sesję tworzy tylko `/login` (limiter, rotacja id) | Plan |
| Strona aktywacji | Najpierw sprawdzenie linku (lookup), potem formularz z e-mailem | Nikt nie wpisuje hasła na próżno; menedżer haseł zna login | Plan |
| Adres w linku | `WEB_PUBLIC_URL` w IaC = `https://${{szczur-radar-web.RAILWAY_PUBLIC_DOMAIN}}` | Właściwy adres w produkcji i każdym PR env bez ręcznej pracy | Plan |
| Rola w `create-user` | Domyślnie `user`, `--role admin` opcjonalnie | Spełnia PRD, najczęstszy przypadek najkrótszy | Plan |
| Token | 256 bitów, we fragmencie `#`, w bazie tylko SHA-256, jeden wiersz na konto | Nie trafia do logów Caddy ani `Referer`; nowy link unieważnia stary | Research + Plan |
| Spring One-Time Token | Odrzucony | Jawne tokeny w bazie, brak unieważnienia, loguje zamiast ustawiać hasło | Research |
| Limit prób | Brak | Tokenu nie da się zgadnąć; argon2 liczy się tylko dla ważnego tokenu | Plan |

## Scope

**In scope:**

- migracja V6 `activation_tokens` i serwis `ActivationLinks`;
- komendy `create-user` i `reset-password`, komunikat `create-admin`;
- publiczne `POST /api/auth/activation/lookup` i `POST /api/auth/activation`, kody `activation_link_invalid` i `password_policy`;
- widok `/activate` i komunikat w `LoginView`;
- zmienne w `.railway/railway.ts`, `deploy-plan.md` z runbookiem po merge'u, PRD, roadmapa i PNG, wytyczne repo;
- weryfikacja w PR env.

**Out of scope:**

- e-maile, panel kont, listowanie, usuwanie i zmiana roli kont;
- samodzielny reset przez użytkownika, automatyczne logowanie, Spring OTT;
- limiter i zadanie czyszczące tokeny, zmiany w `tools/db-backup/`;
- restyling `LoginView`;
- kroki produkcyjne w Progress (są w runbooku).

## Architecture / Approach

CLI (kontekst bez serwera WWW) → `ActivationLinks`, który w jednej transakcji zapisuje konto, token i ewentualne usunięcie sesji → link na stdout. Przeglądarka → `/activate#token` (SPA) → `POST /api/auth/activation/lookup` → formularz → `POST /api/auth/activation`. Ta transakcja kolejno sprawdza politykę, blokuje ważny token, liczy argon2, ustawia hasło i kasuje token. Potem `/login` jak dotąd. Endpointy aktywacji nie dotykają sesji; obrona CSRF przez Origin i `SameSite` bez zmian.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Schemat i logika linków | V6, `User.setPassword/clearPassword`, `ActivationLinks`, `ActivationSettings` | Kolejność w aktywacji (argon2 po blokadzie tokenu) |
| 2. Komendy CLI | `create-user`, `reset-password`, wspólne warunki komend, README | Refaktor `create-admin` bez zmiany zachowania |
| 3. Publiczne API | Kontroler, kody błędów, `SecurityConfig`, `PUBLIC_ROUTES` | Endpoint publiczny nie może tworzyć sesji ani ciasteczka |
| 4. Widok SPA | `/activate`, komunikat w `/login`, testy, kontrakt stylu, zrzuty | Token tylko we fragmencie; e-mail przez stan routera |
| 5. Wdrożenie i PR env | Zmienne IaC, dokumenty, pełny przebieg w PR env | Referencja `${{…}}` w IaC niesprawdzona na żywo |

**Prerequisites:** S-01 i F-03 zrobione; Docker do `./mvnw -B verify`; dostęp `railway ssh` do PR env; issue S-07 na GitHubie.
**Estimated effort:** ~3–4 sesje w 5 fazach (backend 1–3 to większość pracy, faza 5 to głównie weryfikacja).

## Open Risks & Assumptions

- `WEB_PUBLIC_URL` jako string z `${{…}}` jest sprawdzony offline na SDK i w dokumentacji Railway, nie na żywo. Faza 5 potwierdza to planem PR env. Fallback: zmienna z `preserve()` ustawiana przez operatora.
- Jeśli domena web pojawi się w PR env po pierwszym deployu API, `WEB_PUBLIC_URL` może być chwilowo `https://`. Komenda to wykryje i odmówi (kod 2); wystarczy redeploy API.
- Link widziany w transkrypcie agenta przy testach PR env to dane testowe. Na produkcji linki generuje operator.
- Merge zmienia `railway.ts`, więc będą dwa deploye API; nie merge'ować przy przebiegu `RUNNING`.
- Równoległy S-05 dzieli z S-07 migrację, `User.java`, `SecurityConfig`, `railway.ts`, `mock-api.js`, dokumenty i PNG roadmapy. Zmiana wchodząca druga:
  - przenumerowuje migrację;
  - dopisuje odmowę dla konta systemowego, jeśli S-05 je wprowadzi;
  - przed weryfikacją końcową robi rebase i ponowny `verify`.

  Szczegóły w planie: „Koordynacja z S-05”.

## Success Criteria (Summary)

- Operator zakłada konto i resetuje hasło jedną komendą; zaproszony sam ustawia hasło przez link i się loguje.
- Link działa raz, wygasa po ustawionym czasie, a nowy unieważnia stary; reset natychmiast wylogowuje konto.
- `./mvnw -B verify`, `npm test`, `npm run build` i CI zielone; pełny przebieg sprawdzony w PR env bez tokenu w logach.
