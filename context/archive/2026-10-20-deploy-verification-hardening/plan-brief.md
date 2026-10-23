# Wiarygodna weryfikacja wdrożeń — Plan Brief

> Full plan: `context/changes/deploy-verification-hardening/plan.md`
> Frame brief: `context/changes/deploy-verification-hardening/frame.md`
> Przegląd planu: `context/changes/deploy-verification-hardening/reviews/plan-review.md`

## What & Why

Werdykt po deployu powstaje dziś z sygnału bez nazwy usługi i jest raportowany tylko konkluzją
workflow w szczur-radar, czyli jedynym kanałem, który Wait for CI liczy i który przez to musi
zostać zielony. Cel jest prosty: po deployu paczki wiedzieć, czy środowisko wstało i odpowiada.
Plan przenosi tę kontrolę do małego zewnętrznego monitora, który może być czerwony.

## Starting Point

`deploy-verify.yml` (S-14) w szczur-radar: run `skipped` przy każdym `in_progress`, zawsze
zielony, `failure` bez nazwy usługi maskowane późniejszym `success`, runy na `szczur-ci`.

## Desired End State

- Zero workflowów poza CI w szczur-radar.
- Każdy finalny deploy usługi (także PR envy) daje zielony albo czerwony run w publicznym repo
  `szczur-radar-monitor`.
- Run ocenia wdrażaną usługę: API i web = stan deployu `SUCCESS`/`SLEEPING` + `/api/ready`,
  `/healthz` i `/` 200 w oknie 3 min; cron = tylko stan jego deployu, bez sond i bez wpływu na
  wynik deployu API lub weba. Błędy po starcie są poza zakresem.
- Czerwień = jedno issue `deploy-health` na środowisko i usługę w repo monitora, przypisane do
  operatora (push w GitHub Mobile), zamykane przez pierwszy zielony run tej samej usługi.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| „Deploy nieudany” | Każda usługa, także crony, każda oceniana osobno | Decyzja użytkownika; nocne `CRASHED` crona daje issue crona, nie psuje wyniku deployu API i weba | Frame + doprecyzowanie |
| Lokalizacja | Osobne publiczne repo | Użytkownik: zero runów w szczur-radar, czerwony run ma być widoczny, bez zależności od `szczur-ci` | Review F1 |
| Wyzwalacz | Webhook Railway → Cloudflare Worker → `workflow_dispatch` | Webhook ma stałą treść, GitHub wymaga własnego JSON-a; Worker to ~40 linii | Plan |
| Zakres monitora | Stan usług z Railway + dwie sondy | Stan `FAILED` mówi, że paczka nie wstała; tożsamość commita w aplikacji zbędna | Review F2 |
| Issue | Bieżący stan, bez historii | RED → otwórz lub skomentuj, GREEN → zamknij; kolejka per środowisko eliminuje wyścigi | Review F3 |
| Status commita | Brak | Bez PAT do szczur-radar i bez testu blokady Wait for CI | Review F1 |
| Produkcja | Obserwowana od uruchomienia | Monitor niczego nie zmienia w szczur-radar ani w Railway | Plan |
| Push | Przypisanie issue | GitHub Mobile powiadamia o przypisaniu; F-04 zostaje na inne kanały | Plan |

## Scope

**In scope:** Worker, `verify.sh`, `verify.yml`, testy na stubach, uruchomienie dla wszystkich
środowisk, sprawdzenie na PR env tej zmiany, usunięcie `deploy-verify`, runbook.

**Out of scope:** zmiany w aplikacji (`commit`, `/version.json`), status commita, reconcile i
harmonogram, błędy po starcie (logi), E2E, webhook w IaC, deploy Workera z CI, bramkowanie
deployów (deploy guard S-14 i reguła no-merge S-05 zostają bez zmian).

## Architecture / Approach

```
Railway webhook → Worker (sekret, projekt, typ) → workflow_dispatch verify.yml
  → verify.sh: railway deployment list per usługa + /api/ready + /healthz
  → zielony run (zamyka issue) | czerwony run + issue przypisane do operatora
```

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Worker | Przekaźnik z testami | Format payloadu Railway sprawdzony dopiero na żywo w fazie 3 |
| 2. Skrypt i workflowy | `verify.sh`, `verify.yml`, `ci.yml`, testy na stubach | Reguła „stan przejściowy” i okno sond |
| 3. Uruchomienie | Repo publiczne, Worker, webhook, 9 scenariuszy na PR env (w tym push w GitHub Mobile) | Webhook crona: deploy vs wykonanie |
| 4. Wycofanie deploy-verify | Brak runów w szczur-radar, dokumentacja, runbook | Okno po merge'u bez starego workflow |

**Prerequisites:** konto Cloudflare; fine-grained PAT (tylko repo monitora, Actions: write);
dedykowany token workspace Railway; zgoda operatora na publikację repo i webhook.
**Estimated effort:** ~3 sesje; faza 2 największa.

## Open Risks & Assumptions

- **Token workspace Railway** w sekretach publicznego repo (środowisko `monitor` tylko z `main`,
  ruleset, zatwierdzanie runów z forków, rotacja). Railway nie ma tokenów tylko do odczytu.
- **Webhook best-effort.** Zgubione zdarzenie zostaje niewykryte; operator może uruchomić
  `verify.yml` ręcznie.
- **Nocne `CRASHED` crona** (np. celowo niezerowe wyjście `pipeline-trigger`) daje czerwony run
  i issue tego crona przy każdym takim webhooku, dopóki payload wykonania nie zostanie odróżniony
  od deployu (sprawdzane w fazie 3). Nie wpływa na deploye API i weba.
- `queue: max` anuluje nadmiar ponad 100 runów w grupie; przy 2–4 runach na deploy bez znaczenia.

## Success Criteria (Summary)

- Po deployu operator widzi zielony lub czerwony run z nazwą usługi i stanem; czerwień daje push.
- W szczur-radar nie ma już runów `deploy-verify` ani `skipped`.
- Awaria ma jedno issue, które zamyka pierwszy zielony run środowiska.
