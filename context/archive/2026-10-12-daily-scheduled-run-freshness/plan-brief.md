# S-05 daily-scheduled-run-freshness — Plan Brief

> Full plan: `context/changes/daily-scheduled-run-freshness/plan.md`
> Research: `context/changes/daily-scheduled-run-freshness/research.md`

## What & Why

Przebieg pipeline'u (Wykop → ocena LLM) ma się na produkcji uruchamiać sam codziennie (FR-002), a zalogowany użytkownik ma widzieć nad listą datę i godzinę ostatniej udanej aktualizacji danych (FR-030, US-01; issue #18). Dziś przebieg startuje tylko z przycisku admina, więc bez człowieka dane nie przybywają.

## Starting Point

S-02 dało kolejkę przebiegów w procesie API (jeden naraz, 409 przy aktywnym), statusy z `finished_at` i odzyskiwanie po restarcie. Wejście jest jednak tylko jedno: `POST /api/admin/pipeline-runs` z sesją admina, a `requested_by` musi wskazywać konto. API usypia się po kilku minutach bez ruchu, więc zegar w nim (`@Scheduled`) nie zadziała. Wzorzec serwisu cron istnieje już w `db-backup`.

## Desired End State

Codziennie o 00:30 UTC serwis cron Railway budzi API przez sieć prywatną i uruchamia przebieg dni D-2 i D-1 (czas warszawski) jako konto systemowe. Potem czeka na wynik; gdy przebieg się nie uda, kończy się kodem ≠ 0 i Railway wysyła e-mail. Użytkownik widzi na `/` „Dane zaktualizowane: 13.10.2026, 02:51” albo „Dane nie były jeszcze aktualizowane.”

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Wyzwalacz | Serwis cron `pipeline-trigger` (`curl`) w Railway | `@Scheduled` nie działa przy uśpionym API; cron na obrazie API odrzucony wcześniej. | Research / Plan |
| Wejście crona | Cienka trasa `POST /internal/pipeline-runs/daily`, bez treści | Poza `/api/` jest nieosiągalna z internetu (Caddy), a zakres dnia liczy API w Javie z testami. | Plan |
| Uwierzytelnienie | Token Bearer w bezstanowym łańcuchu `/internal/**` | Bez sesji i ciasteczek; token nie działa na `/api/**`, sesja nie działa na `/internal/**`. | Plan |
| Kto zleca | Konto systemowe `scheduler@system.invalid` + flaga `users.login_enabled` | Prawdziwy `requested_by` bez zmian w `pipeline_runs`; flaga i `CHECK` blokują logowanie i hasło. | Plan (propozycja użytkownika) |
| Godzina i zakres | 00:30 UTC, dni D-2 i D-1 | Po północy Warszawy w obu porach roku, przed backupem o 02:00; zakładka łata F9 i noc przerwaną deployem. | Plan |
| Udany przebieg (etykieta) | `succeeded` lub `partial` | Przy 96–100% zgodności z kontraktem prawie każda noc jest `partial`; „błędem” są `failed`/`interrupted`. | Plan |
| Które przebiegi | RANGE, też ręczne; czas = `finished_at` | „Aktualizacja” to pobranie z Wykopu; ręczna naprawa po awarii od razu odświeża etykietę. | Plan |
| Sygnał awarii | Cron czeka na wynik; `failed`/`interrupted` → e-mail Railway | Aktywny sygnał od pierwszej nocy, bez czekania na S-06; `partial` zostaje w `/admin`. | Plan |
| Wersja schematu | Ryzyko wycofane z uzasadnieniem | Pipeline działa w procesie API z pre-deployowym `migrate`; okno deployu pokrywa fix-forward. | Plan |
| Limit RETRY | Bez limitu | Resztki są ograniczone (3 próby → DEAD); noc po awarii kończy się przed 02:00. | Plan |
| Etykieta przy braku/błędzie | Tekst zastępczy / brak etykiety, lista działa | Użytkownik wie, na czym stoi; awaria etykiety nie psuje feedu. | Plan |
| Kolejność UI | Etykieta po merge'u `feed-view-ui-hardening` (z wyjściem awaryjnym) | Bez konfliktów w `FeedView` i mocku; etykieta wchodzi w nową strukturę widoku. | Plan |

## Scope

**In scope:**
- migracja: `users.login_enabled`, konto systemowe, `CHECK`; logowanie odrzuca konto systemowe;
- łańcuch tokenu `/internal/**`, trasy `daily` i statusu, `Clock`, odzyskiwanie przed otwarciem portu;
- `GET /api/freshness` dla każdej zalogowanej roli;
- `tools/pipeline-trigger/` (obraz, skrypt, test, job CI) i serwis w `railway.ts`;
- etykieta w `FeedView`, mock, testy, zrzuty;
- dokumenty: `deploy-plan.md` z runbookiem, PRD, roadmapa z PNG, `infrastructure.md`, reguły repo.

**Out of scope:** osobny worker, `@Scheduled`, token na `/api/**`, sprawdzanie wersji schematu w kodzie, limit RETRY, e-mail przy `partial`, dashboard (S-06), zmiany `/api/posts` i godziny backupu, zmiana statusu FR-022 (tylko sygnalizowana).

## Architecture / Approach

`pipeline-trigger` (cron Railway, 00:30 UTC) → sieć prywatna, Bearer → `POST /internal/pipeline-runs/daily` → `PipelineRunService.startRange(D-2, D-1, konto systemowe)` → 202 `[{id, kind}]` → cron odpytuje `GET /internal/pipeline-runs/{id}` → kod wyjścia → e-mail Railway przy awarii. SPA: `FeedView` → `GET /api/freshness` → `max(finished_at)` przebiegów RANGE `succeeded`/`partial` → etykieta.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Konto systemowe i wejście crona | Migracja, token `/internal/**`, trasy crona, odzyskiwanie przed portem | Kolizja numeru migracji z S-07; wyścig przy budzeniu |
| 2. Odczyt świeżości | `GET /api/freshness` z regułą z wywiadu | Niewielkie |
| 3. Cron i IaC | `tools/pipeline-trigger/`, test w CI, serwis w `railway.ts`, PR env | Budzenie przez sieć prywatną bez Caddy; referencja do zapieczętowanego tokenu |
| 4. Etykieta w `FeedView` | Etykieta, mock, testy, zrzuty | Zależność od `feed-view-ui-hardening` |
| 5. Dokumenty i weryfikacja | Runbook, PRD, roadmapa, reguły, łańcuch w PR env | Konflikty po rebase (S-07, hardening) |

**Prerequisites:** S-02 i F-01 zrobione; do fazy 4 merge `feed-view-ui-hardening` (albo wyjście awaryjne); dostęp operatora do Railway (token produkcji przed merge'em).
**Estimated effort:** ok. 4–6 sesji w 5 fazach, w tym dwie weryfikacje w PR env.

## Open Risks & Assumptions

- S-07 idzie równolegle: zmiana, która wchodzi do `main` druga, przenumerowuje migrację i dopisuje odmowę konta systemowego w `ActivationLinks`.
- Nieudokumentowane: czy referencja do zapieczętowanej zmiennej innego serwisu się rozwiązuje; runbook sprawdza to na produkcji i ma wariant zapasowy.
- Nieudokumentowane: co dokładnie widzi bezpośredni wywołujący podczas budzenia (odmowa połączenia czy 502); cron ponawia oba przypadki.
- Powód odłożenia FR-022 w roadmapie jest nieaktualny; decyzja o statusie FR-022 należy do użytkownika.
- Nocne okno ok. 00:30–01:30 UTC dochodzi do reguły „nie merge'uj przy `RUNNING`”.

## Success Criteria (Summary)

- Co noc bez udziału człowieka przybywają posty z D-1 (i braki z D-2), a nieudana noc daje e-mail.
- Użytkownik widzi na `/` czas ostatniej udanej aktualizacji, nigdy przebiegu zakończonego błędem.
- Konto systemowe nie loguje się, a trasa crona nie jest osiągalna z internetu.
