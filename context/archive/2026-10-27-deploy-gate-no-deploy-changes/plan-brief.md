# S-21: zmiana IaC bez deployu nie blokuje wdrożeń — Plan Brief

> Full plan: `context/changes/deploy-gate-no-deploy-changes/plan.md`
> Research: `context/changes/deploy-gate-no-deploy-changes/research.md`

## What & Why

Kontrola po apply w jobie `railway-config` robi CI na `main` czerwonym, gdy zastosowana zmiana IaC nic nie deployuje (np. `source.checkSuites`) albo gdy w trakcie pojawi się deployment z innego pusha. Czerwony workflow sprawia, że Wait for CI pomija deploy kodu tego commita, więc fałszywy alarm bramki blokuje wdrożenie. Błąd jest uśpiony od 2026-10-19 i wróci przy następnej takiej zmianie, np. w IaC dla Reddita. Dokumenty S-14 i issue #73 opisują `2abc64c` błędnie.

## Starting Point

`verify-apply.sh check` bierze za deployment apply'a pierwszy deployment o ID innym niż w migawce (`--limit 1`) i ocenia tylko jego status. `SKIPPED` albo brak nowego ID przez 15 min daje exit 1. `gate.jq` liczy każdą zmianę jako redeploy, bo CLI nie daje `deployEffect`. Railway oznacza jednak deployment apply'a `meta.patchId` z przedrostkiem `iac-change-set/` (31 takich na produkcji, żaden `SKIPPED`), a CLI 5.67.1 to pole przepuszcza.

## Desired End State

- Apply bez deployu kończy kontrolę zielono: wiersz „no deploy” i `::notice::` po oknie 2 min.
- Pushe w tle są pomijane.
- Deploy apply'a, który padł (`FAILED`/`CRASHED`/`SKIPPED`), zawieszony Postgres i `/api/ready` ≠ 200 dalej dają wynik czerwony.
- `deploy-plan.md` ma notatkę „Production after the merge (2026-10-19)” zamiast nieodhaczonego runbooka S-14 i nową sekcję S-21, a w #73 jest komentarz korygujący.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Jak rozpoznać deployment apply'a | `meta.patchId` z `iac-change-set/`, nad granicą migawki na liście `--limit 50` | `reason` i `commitHash` są takie same jak przy pushu, a `commitHash` bywa cudzym commitem | Research |
| Zmiana bez deployu | brak deploymentu IaC w oknie `DEPLOY_VERIFY_DEPLOY_WINDOW` (2m) = zielono z `::notice::` | naprawia każdą przyszłą zmianę bez deployu (np. `domain.create`) bez listy do utrzymania | Plan |
| Lista „bez deployu” w `gate.jq` (R1) | nie; zmienia się tylko opis listy restartów („may redeploy”) | wewnętrzna reguła CLI myli się dla `source.*`, a dokumentacja Railway milczy | Plan |
| Usługi z Wait for CI | bez zmian: deployment IaC w `WAITING` przekazywany monitorowi | potwierdzone, że redeploy z apply'a czeka na CI (run 39140400096) | Research |
| Granica poza listą | exit 1 „cannot tell” | jak w migawce: lepiej czerwono niż zgadywać, który deployment jest nowy | Plan |
| #73 i runbook S-14 | komentarz korygujący w zamkniętym #73 i notatka „Production after the merge” zamiast 13 pól | stan produkcji żyje w issue, a plik ma być prawdziwy po merge'u (`lessons.md`) | Plan |
| Weryfikacja | testy na atrapie z prawdziwym `meta`, kontrola read-only na produkcji, 3 runy w PR env | agent-first; oba główne przypadki S-21 sprawdzone na żywym Railway | Plan |

## Scope

**In scope:**
- `tools/deploy-gate/verify-apply.sh`
- atrapa `railway` i przypadki w `test.sh`
- opis listy restartów w `gate.sh`/`gate.jq`
- `tools/deploy-gate/README.md`
- sekcje S-14 i S-21 w `deploy-plan.md`
- `REPOSITORY_GUIDELINES.md`
- komentarz kroku w `ci.yml`
- komentarz w #73

**Out of scope:**
- logika `gate.jq`
- wersja CLI i `apply --json`
- obsługa usług z Wait for CI
- strażnik, `/api/ready`, budżet 2 h, monitor
- `deploy-plan.md:967` (o `railway redeploy`)
- ponowne otwarcie #73
- nowa lekcja
- kroki po merge'u w Progress

## Architecture / Approach

Migawka bez zmian zapisuje granicę: ID najnowszego deploymentu usługi. Kontrola co 10 s pobiera 50 najnowszych deploymentów z `meta` i bierze tylko te nad granicą (lista jest od najnowszego, więc zegary nie są porównywane). Deploymentem apply'a jest najnowszy z nich z `patchId` `iac-change-set/`, a jego status oceniany jest jak dziś. Jeśli żaden się nie pojawi, a udany odczyt po końcu okna (liczonego od startu kontroli) to potwierdzi, usługa dostaje „no deploy”. Potem jak dziś następuje `/api/ready`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Kontrola rozpoznaje deployment swojego apply'a | nowa reguła w `verify-apply.sh`, atrapa z prawdziwym `meta`, przypadki (a)–(i), README; kontrola read-only na produkcji | atrapa rozjedzie się z prawdziwym `meta` — stąd 1.4 na produkcji |
| 2. Weryfikacja w PR env | trzy runy: bez deployu z równoległym pushem, z deployem, cofnięcie | PR env bez deploymentów — usługi uruchamiane ręcznie (`railway redeploy --from-source`) przed scenariuszami; trafienie z pushem zmiany 2 w okno runu 1; tymczasowy commit musi zostać cofnięty |
| 3. Dokumenty i #73 | poprawki S-14, notatka po merge'u, sekcja S-21 z runbookiem, wytyczne, komentarz w #73 | komentarz w issue jest publiczny — tekst zatwierdza operator |

**Prerequisites:** gałąź `feature/deploy-gate-no-deploy-changes` z PR-em i PR env (start `/10x-implement`), issue #115, lokalny Railway CLI zalogowany do odczytu produkcji, Railway CLI (`environment edit`, `redeploy --from-source`) do PR env i MCP do odczytu.
**Estimated effort:** ~2 sesje: faza 1 jedna sesja, fazy 2–3 druga (głównie czekanie na CI).

## Open Risks & Assumptions

- Zakładamy, że Railway dalej oznacza deploymenty apply'a `patchId` `iac-change-set/`. Zmiana formatu dałaby fałszywe „no deploy” (z notice, nie czerwono); test 1.4 i runbook po merge'u to wykryją.
- Zmiana, która powinna była zdeployować, a nie zdeployowała, przejdzie z samym `::notice::`. To decyzja świadoma; takiego przypadku nie znamy.
- Ręczny apply albo redeploy operatora w oknie kontroli może zostać wzięty za deployment tego apply'a (ten sam przedrostek).
- Okno 2 min zakłada opóźnienie utworzenia deploymentu rzędu sekund (obserwowane 1–4 s w pięciu przypadkach).

## Success Criteria (Summary)

- Apply zmiany bez deployu i równoległy push nie robią `railway-config` czerwonym. Dowód: przypadki w `test.sh` i runy w PR env.
- Deploy apply'a, który padł albo zawisł, nadal robi job czerwonym.
- Operator czyta w `deploy-plan.md` i #73 to, co naprawdę stało się z `2abc64c`.
