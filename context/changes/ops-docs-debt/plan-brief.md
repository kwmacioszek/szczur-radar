# S-19: dokumenty operatora mówią prawdę — Plan Brief

> Full plan: `context/changes/ops-docs-debt/plan.md`

## What & Why

Operator czyta `deploy-plan.md` i `infrastructure.md` jako runbook i opis produkcji. Dziś dokumenty kłamią w trzech miejscach:
- osiem sekcji mówi „not merged” o zmianach od dawna działających na produkcji;
- 11 linków prowadzi do folderów przeniesionych do archiwum;
- tabela JVM opisuje stary limit 1 GB.

IaC wciąż deklaruje trzy martwe zmienne tylko dlatego, że bramka CI odmawia `variable.delete`. S-19 (issue #78, audyt C12, C13, E, F) spłaca ten dług.

## Starting Point

- **`deploy-plan.md`:** nieaktualne statusy (S-02, S-04, S-07, runner, S-14, S-15, S-08), linki `context/changes/…` do archiwum, sekcje z czasów FastAPI bez oznaczenia, tabela JVM z 2026-10-06 (heap ≈ 716 MiB, SerialGC).
- **`infrastructure.md:38`:** mówi o „LangGraph pipeline”.
- **`.railway/railway.ts:127,137,141`:** deklaruje `RAILPACK_JDK_VERSION`, `DATABASE_URL` i `JWT_SECRET`.
- **#36 (Hikari) i #51:** zamknięte 2026-10-18 z dowodami, ale nie zapisane w runbooku.

## Desired End State

- Każdy status w `deploy-plan.md` jest prawdziwy, a każdy link działa.
- Sekcje historyczne są oznaczone jedną linią.
- Tabela JVM pokazuje wynik pomiaru przy 2 vCPU / 2 GB.
- `infrastructure.md` opisuje dzisiejszą topologię.
- Trzech zmiennych nie ma ani w Railway (PR env, produkcja), ani w `railway.ts`, a `railway-config` jest zielone.
- `deploy-plan.md` ma sekcję S-19 z rozstrzygnięciem Hikari i #51 oraz runbookami „before the merge” i „after the merge”.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Zakres porządków | Wszystkie znalezione nieaktualne statusy i linki, nie tylko trzy z roadmapy | Pięć dodatkowych sekcji „not merged” to dokładnie ten dług, który S-19 ma spłacić. |
| Sekcje historyczne (FastAPI, porównanie platform) | Notka „historyczne” na początku, treść bez zmian | Zachowuje zapis decyzji (wzorzec z `tech-stack-api.md`), nie udaje bieżącego runbooka. |
| Źródło pomiaru JVM | PR env tego PR-a przez `railway ssh` | Ten sam obraz i te same `API_LIMITS` z IaC, a produkcji nie dotykamy. |
| Kto usuwa zmienne | Agent w PR env i na produkcji; na produkcji po wyraźnym potwierdzeniu człowieka | Decyzja użytkownika; człowiek tylko potwierdza i merguje. |
| Kolejność usuwania | Najpierw środowisko, potem plik; produkcja tuż przed merge'em | Plik bez zmiennej przy środowisku z nią daje `variable.delete`, a odwrotna różnica daje `create`, który inny merge odtworzy. |
| Hikari (#36) | Bez zmian w konfiguracji, tylko zapis decyzji | Logi od 2026-10-13 bez ostrzeżeń; issue zamknięte z dowodem. |
| Krok produkcyjny poza Progress | Runbook „before the merge” wykonywany po `/10x-archive` | Archiwum jest niezmienne, a merge jest ostatni (lessons.md:75-80). |
| Inne otwarte PR env (plan-review F1) | Usunąć w nich 3 zmienne, zanim ich gałąź wciągnie `main` | Inaczej ich plan IaC pokaże `variable.delete` i bramka odmówi. |
| Status sekcji S-19 (plan-review F2) | „delivered by PR #N; production verification and closure in issue #78” + lekcja w `lessons.md` | Wszystkie 8 nieaktualnych statusów powstało z „not merged” pisanego przed merge'em. |

## Scope

**In scope:**
- statusy, linki i notki historyczne w `deploy-plan.md`;
- nowa tabela JVM;
- `infrastructure.md` (topologia, Secrets, notka przy porównaniu platform);
- usunięcie trzech zmiennych (Railway + `railway.ts`);
- sekcja S-19 z runbookami;
- roadmapa i SVG;
- gałąź, PR i komentarze w #78.

**Out of scope:**
- zmiana konfiguracji Hikari;
- `context/archive/**` i `lessons.md` (martwy link `:57` zostaje);
- przepisywanie lub usuwanie sekcji historycznych;
- zmiana bramki CI i `variables.ignore` dla usuwanych zmiennych;
- pomiar JVM na produkcji;
- inne pozycje audytu.

## Architecture / Approach

Trzy fazy rosnącego ryzyka:
1. Same dokumenty. Ten commit zakłada gałąź i PR, a od nich powstaje PR env.
2. PR env: pomiar JVM, potem usunięcie zmiennych w PR env i od razu push `railway.ts` bez nich, aż CI (`railway-config`) będzie zielone.
3. Runbooki S-19 i lekcja o statusach. Po impl-review i archiwizacji agent, po potwierdzeniu człowieka, przy `deploy-safety` `safe:true` i poza oknami 00:30–01:30 i 01:50–02:30 UTC, usuwa zmienne na produkcji i w PR env innych otwartych PR-ów (dziś #92), a potem oddaje merge.

Wartości zmiennych sprawdza się wyłącznie przez `jq 'keys'`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Dokumenty operatora | Prawdziwe statusy i linki, notki, Hikari, `infrastructure.md`, roadmapa i SVG, PR | Nadpisanie statusu wynikiem, którego nikt nie sprawdził (bierzemy z `gh`) |
| 2. PR env — JVM i zmienne | Zmierzona tabela JVM; PR env i `railway.ts` bez trzech zmiennych; CI zielone | Push ze starym plikiem między usunięciem a commitem odtworzy zmienne |
| 3. Produkcja przed merge'em | Sekcja S-19 z runbookami „before/after the merge”, lekcja o statusach | Inny merge między usunięciem na produkcji a merge'em S-19 odtworzy dwie zmienne; otwarty PR #92 bez usunięcia zrobi się czerwony |

**Prerequisites:** dostęp agenta do Railway CLI (PR env i produkcja), `gh`, działające self-hosted runnery CI (`CI_RUNNER=szczur-ci`).
**Estimated effort:** ~1–2 sesje w 3 fazach, głównie edycja dokumentów.

## Open Risks & Assumptions

- `railway variable delete` nie ma `--skip-deploys`, więc może przedeployować API albo zostawić zmianę jako staged. Faza 2 ustala to w PR env, a na produkcji chronią warunki wstępne (`deploy-safety`, okna nocne).
- Otwarty PR #92 zmienia `deploy-plan.md` i usuwa `deploy-verify`, więc każda faza zaczyna od rebase'u na `origin/main`.
- Usunięcie zapieczętowanego `JWT_SECRET` jest nieodwracalne. Zakładamy, że nic go nie używa (od F-03 sesje JDBC).
- Zakładamy, że PR env ma te same `API_LIMITS` co produkcja. Faza 2 sprawdza to przed pomiarem.

## Success Criteria (Summary)

- Operator nie trafia w `deploy-plan.md` ani `infrastructure.md` na fałszywy status, martwy link ani niezmierzoną liczbę.
- `.railway/railway.ts` deklaruje tylko używane zmienne, a `railway-config` na PR i na `main` jest zielone.
- #78 zamknięte po weryfikacji produkcji z komentarzem o wyniku obu runbooków.
