# S-14: Bezpieczna bramka wdrożeń — Plan Brief

> Full plan: `context/changes/safe-deploy-gate/plan.md`
> Research: `context/changes/safe-deploy-gate/research.md`

## What & Why

Bramka IaC w CI chroni przed zniszczeniem zasobów, ale nie przed trzema innymi szkodami:
- wdrożeniem czerwonego commita;
- cichym restartem produkcji;
- zablokowaniem pracy.

S-14 zamyka luki C4–C7 z audytu po MVP (MS-02, MS-03), żeby każdy kolejny merge kamienia M-2 był bezpieczny.

## Starting Point

- **Testy:** `railway-config` stosuje IaC na produkcji równolegle z testami (brak `needs:`).
- **Bramka:** to nietestowany `jq` z regexem na tekście planu, a po apply nic nie sprawdza zdrowia.
- **Crony:** deployują się bez Wait for CI.
- **Ręczne zmienne:** każda zmienna ustawiona ręcznie bez `preserve()` czerwieni CI na `main`.
- **Przebieg `RUNNING`:** automat go nie widzi; zasadę „nie merguj przy RUNNING” pilnuje człowiek.

## Desired End State

- **Kolejność:** merge do `main` stosuje IaC dopiero po zielonych testach.
- **Strażnik:** przed apply i deployem kodu czeka do 90 min, aż przebieg pipeline'u się skończy.
- **Po apply:** job sprawdza zdrowie produkcji i robi się czerwony, gdy Postgres albo API nie wstaną.
- **Po deployu kodu:** osobny workflow alarmuje przez issue.
- **Crony:** czekają na CI.
- **Zmienne strojenia:** ustawione ręcznie nie blokują wdrożeń.
- **Testy:** cała logika bramki ma testy.

## Key Decisions Made

| Decyzja | Wybór | Dlaczego | Źródło |
| --- | --- | --- | --- |
| Kolejność apply | `needs:` na wszystkie joby testowe | Wait for CI czeka na cały workflow, więc „config przed kodem” zostaje | Research |
| Reguły bramki | Pola `kind`/`field`/`severity`/`deployEffect` zamiast regexu, fail closed na nieznany format | Testowalne na fixture'ach, nie zależą od brzmienia CLI | Research / Plan |
| Restart Postgresa i usług | Ogłaszać i sprawdzać po apply, nie odrzucać | Ręczny apply poza CI jest ciężki i łatwo o nim zapomnieć | Plan (użytkownik) |
| Przebieg `RUNNING` | Nowy endpoint `GET /api/deploy-safety` + strażnik w CI | Automat egzekwuje zasadę, której dziś pilnuje człowiek | Plan (użytkownik) |
| Dostęp do endpointu | Publiczny, tylko `safe` + powód | Bez nowego sekretu; ta sama klasa ekspozycji co `/api/ready` | Plan |
| Gdy zajęte | Czekaj do 90 min, potem padnij | Merge w trakcie przebiegu wdroży się sam po jego końcu; minuty liczone tylko przy kolizji | Plan (użytkownik) |
| Brak decyzji API | 401/404/niedostępne/503 → przepuść z ostrzeżeniem; `DEPLOY_GUARD=off` | Przebieg wymaga żywego API z bazą; nie blokować deployu naprawy | Plan |
| Ręczne zmienne (C7) | `variables.ignore` w `railway.ts` + CLI 5.67.1 | Mechanizm Railway zamiast dopisywania `preserve()` po incydencie | Plan (użytkownik) |
| Crony (C5) | `checkSuites: true` + wąski wyjątek bramki dla `→ true` | Włączenie Wait for CI zaostrza ochronę, nie przepina źródła | Research / Plan |
| Kontrola po deployu kodu | Workflow `deploy-verify` na `deployment_status`, alarm przez issue, job zawsze zielony | Czerwony check mógłby przez Wait for CI zablokować redeploy tego commita | Plan (użytkownik) |
| Miejsce logiki | `tools/deploy-gate/` z `test.sh` | Wzorzec `tools/pipeline-trigger/`; jedno miejsce z testami | Plan |

## Scope

**In scope:**
- `needs:` na `railway-config`, CLI 5.67.1, przypięty plan;
- `tools/deploy-gate/`: gate, strażnik, kontrola po apply, kontrola po deployu, testy;
- endpoint `/api/deploy-safety` z IT;
- `checkSuites` na cronach i `variables.ignore`;
- workflow `deploy-verify`;
- runbook S-14 i wytyczne.

**Out of scope:**
- odrzucanie restartów;
- okno nocne w strażniku;
- naprawa zombie (S-11);
- zmiana healthchecka Railway;
- usuwanie zbędnych zmiennych (S-19);
- dead-man's switch (C2);
- zmiany SPA.

## Architecture / Approach

```
push → [6 jobów testowych + deploy-gate-test] → railway-config:
        wait-safe.sh (GET /api/deploy-safety, ≤90 min) → plan --out (przypięty) → gate.sh
        → apply --plan → verify-apply.sh (deploye restartowanych usług + /api/ready)
workflow OK → Railway Wait for CI → deploy kodu → deployment_status
        → deploy-verify.yml → verify-deploy.sh (/api/ready, /healthz) → issue `deploy-health`
```

## Phases at a Glance

| Faza | Co daje | Główne ryzyko |
| --- | --- | --- |
| 1. Bramka jako narzędzie + kolejność | Testowane reguły planu, `needs:`, CLI 5.67.1, przypięty plan | Nowe CLI zmienia format planu albo diff produkcji |
| 2. Crony i zmienne | `checkSuites` na cronach, `variables.ignore` | Pole spoza typów SDK znika bez błędu; Postgres przy `managed` |
| 3. Endpoint | `GET /api/deploy-safety` z IT | Ekspozycja stanu przebiegu (minimalna, uzasadniona) |
| 4. Strażnik + kontrola po apply | Czekanie na `safe`, kontrola deployu i `/api/ready` | Deploy po apply w `WAITING` (obsłużone jako „przekazane”) |
| 5. `deploy-verify` | Alarm przez issue po deployu kodu | Wyzwalacz `deployment_status` i plik workflow z SHA deployu |
| 6. Dokumentacja + produkcja | Runbook S-14, wytyczne, weryfikacja po merge'u | Merge robi człowiek; pierwszy merge przez 401 |

**Prerequisites:**
- issue #73;
- PR env z działającym Railway;
- lokalny dostęp `railway` do planu produkcji (tylko odczyt).

**Estimated effort:** ~4–5 sesji w 6 fazach, jeden PR.

## Open Risks & Assumptions

- **Wait for CI dla deployu po apply:** nieznane, czy deploy wywołany apply czeka na Wait for CI. Kontrola po apply traktuje `WAITING` jako przekazanie, więc nie ma zakleszczenia.
- **`deploy-verify` w PR env:** zakładamy, że workflow `deployment_status` czyta plik z SHA deployu; sprawdzone w Phase 5.
- **Budżet 2 h Wait for CI:** testy + 90 min strażnika + 15 min kontroli ≈ 110 min. Strażnik liczy bezwzględny termin od utworzenia runu i po nim pada na czerwono, bo Railway po 2 h pomija deploy po cichu (`inactive`, bez `deploy-verify`).
- **Wyścig sprawdź–wdroż:** przebieg uruchomiony tuż po `safe=true` nadal może zostać przerwany; zgłasza go dzwonek S-06.
- **Zombie (A2):** stary wiersz `running` blokuje strażnika na 90 min; runbook każe zrestartować API, a S-11 naprawia przyczynę.

## Success Criteria (Summary)

- Commit z czerwonym testem nie zmienia konfiguracji produkcji ani nie wdraża kodu.
- Merge w trakcie przebiegu czeka na jego koniec, zamiast go przerwać.
- Zawieszona usługa po apply albo deployu daje sygnał w minutach (czerwony job albo issue), nie o 00:30.
- Ręcznie ustawiona zmienna strojenia nie zatrzymuje kolejnych merge'y.
