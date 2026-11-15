# Evidence: people

Okno: cała historia (a6f9c23 2026-09-26 .. ef56ddc 2026-11-15), 112 commitów, wszystkie policzone (0 botów, 0 autorstwa agenta, 0 masowych). Atrybucja z `.work/commit-files.tsv` (tylko `noise == "-"`), commit liczony raz na możliwość.

- `humans_in_window: 1` (`evidence`, kontrakt): jedna osoba, kwmacioszek (dalej: operator), pod trzema tożsamościami git (106 commitów jako `kwmacioszek`, 6 jako `krzysztofmacioszek`, te 6 z 2026-09-26, bootstrap). To **linia bazowa, nie ustalenie** (false-signal 6). Rankingu osób nie ma — jest jedna.
- Trailer `Co-Authored-By: Claude` ma 102/112 commitów (`evidence`, zgodne z kontraktem; w poprzedniej mapie trzeba było korygować 85 → 83, tu liczba po treści commita zgadza się z 102). Modele w trailerach (Opus 5.5 520 wystąpień w całej historii treści, Sonnet 5.5 37, Opus 4.8 16, Fable 5.1 16, Sonnet 5 2) są kontekstem, nigdy osobami.
- `Claude-Session:` jest w 20 commitach, 19 unikalnych sesji (`evidence`); trailer dopisywany dopiero od późnego okresu (nie wszystkie sesje są śladem).
- Pokrycie: 1719 zmian kodu, nieprzypisane 0 (kontrakt); proza 1121 wierszy osobno (false-signal 8).
- Formalny właściciel: brak `CODEOWNERS` i `CONTRIBUTING` (`git ls-files`, 0 trafień).

## Obszary
Wszystkie high/medium z inwentarza plus top 5 wg commitów (platform 41, shared-foundations 34, pipeline-runs 30, regression-harness 27, admin-console/feed 24/22). Dla jednej osoby tabela służy odpowiedzi „co jest tylko w głowie operatora / agenta”, nie „kogo pytać”.

## People per capability
Wszędzie ten sam człowiek (operator); okres aktywny: pierwszy 2026-09-26/27, ostatni 2026-11-15. Różni się tylko wolumen i liczba sesji agenta.

| Możliwość | Commity (liczone) | Z trailerem Claude | Z Claude-Session | Kryt. | Osoby |
|---|---|---|---|---|---|
| platform | 41 | 37 (90%) | 5 | high | operator |
| shared-foundations | 34 | 32 (94%) | 11 | kubełek | operator |
| pipeline-runs | 30 | 28 (93%) | 11 | high | operator |
| regression-harness | 27 | 25 (93%) | 11 | medium | operator |
| admin-console | 24 | 24 (100%) | 9 | medium | operator |
| feed | 22 | 21 (95%) | 9 | high | operator |
| shared-ui | 22 | 18 (82%) | 5 | kubełek | operator |
| auth | 17 | 16 (94%) | 4 | high | operator |
| daily-run | 16 | 14 (88%) | 7 | medium | operator |
| ingest | 12 | 12 (100%) | 6 | high | operator |
| accounts | 11 | 11 (100%) | 4 | high | operator |
| operator-alerts | 10 | 10 (100%) | 5 | medium | operator |
| llm-providers | 10 | 10 (100%) | 0 | high | operator |
| db-backup | 10 | 10 (100%) | 3 | high | operator |
| assessment | 9 | 9 (100%) | 2 | high | operator |
| model-evaluation | 9 | 9 (100%) | 3 | low | operator |
| quality-monitoring | 4 | 4 (100%) | 4 | medium | operator (całość w M-4) |
| docs-planning (proza) | 111 | 101 | 20 | — | operator |

Grupy tematyczne: `inference` z tematów (numery S-NN/F-NN w tytułach, zob. `git-log.txt`) — jedna zmiana = jeden PR = jeden `change-id` w `context/changes/` → `context/archive/` (62 zarchiwizowane zmiany, 3 aktywne). Zatem „kogo zapytać” zastępuje „który plan/PR przeczytać”.

## Concentration
- Baza: 1 człowiek → koncentracja 100% wszędzie, normalna (false-signal 6). Język bus-factor pominięty.
- Możliwości, które kiedykolwiek dotknęła tylko jedna osoba: **wszystkie** (18/18).
- Jedyna różnica jakościowa: bootstrap 2026-09-26 pod tożsamością `krzysztofmacioszek` (6 commitów) — ta sama osoba, inna konfiguracja git; nie jest to drugi człowiek.
- Spoza tabeli: M-4 (c8a6488..ef56ddc) to 19 commitów w 8 dniach (2026-11-06..15; 4/1/4/2/3/3/1/1 dziennie) — tempo, nie koncentracja.

## Agent co-authorship
- 102/112 (91%) całości; 10 commitów bez trailera Claude (m.in. bootstrap i część docs). Udział per możliwość powyżej: najniższy shared-ui 82%, najwyższe 100% (ingest, accounts, alerts, db-backup, llm-providers, assessment, admin-console, quality-monitoring).
- Agent jest faktycznym wykonawcą kodu, człowiek zatwierdza plan i merge (zasada „tylko człowiek merguje”, `REPOSITORY_GUIDELINES.md`). Wiedzę „dlaczego” niesie więc `plan.md`/`change.md` i `lessons.md`, nie historia gita.
- Recenzja: 0 recenzji PR przez człowieka na GitHubie (`gh pr list`, pole `reviews`: 0 dla 107 PR; autor i mergujący to ten sam login w 103 z 107, 4 bez mergującego). Komentarze PR: `railway-app` 98 (automat PR env, nie recenzja — false-signal 7), operator 16. Issues: 64 komentowane tylko przez operatora, 2 z udziałem `github-actions`; przypisań 0. Recenzja jest zatem agentowa (skille `10x-impl-review`) — jej ślady w `context/changes/*/reviews`, nie w forge (`inference`).

## Implications
- Przed zmianą w danej możliwości: nie ma kogo zapytać poza operatorem; czytaj najpierw `context/archive/<change>/plan.md` i `context/foundation/lessons.md` oraz sekcję runbooka w `context/deployment/deploy-plan.md` (S-05, S-06, S-08, S-14, F-01, F-04, S-18).
- Wiedza poza repo, którą agent nie odtworzy z gita (`inference` z `REPOSITORY_GUIDELINES.md`, bez weryfikacji dostępu): sekrety produkcyjne ustawiane ręcznie (`LLM_API_KEY`, `WYKOP_*`, `PIPELINE_TRIGGER_TOKEN`, ping URL healthchecks.io), konto Railway i reguła „deployment crashed”, bot Telegram/ntfy, NAS z kopią (Synology Cloud Sync), klucz `HEALTHCHECKS_API_KEY` uruchamiany tylko lokalnie, runnery samodzielnie hostowane/VM-y (Proxmox, KVM), repo `szczur-radar-monitor` (Worker Cloudflare, webhook Railway). Dotyczy: platform, db-backup, operator-alerts, daily-run.
- Ryzyko jednej osoby i jednej maszyny jest strukturalne (false-signal 4: wspólna przyczyna — projekt jednoosobowy), nie sygnał na jedną możliwość.
- Przy zmianach równoległych (wiele worktree w M-4) pytanie „kto już próbował” = grep po `context/changes/` i `context/archive/` oraz po `Claude-Session`, nie po autorach.

## Unknowns
- `unknown`: czy ktokolwiek poza operatorem ma dostęp do Railway, kopii NAS, healthchecks.io, repo monitora (`gh api collaborators` zwrócił tylko operatora, ale uprawnienia org/Railway niewidoczne).
- `unknown`: czy sesje agenta są wznawialne — 19 URL-i `Claude-Session` to ślady, nie dowód dostępności zapisu.
- `unknown`: dlaczego 10 commitów nie ma trailera Claude (praca ręczna czy narzędzie) — nie badano.
- `unknown (external)`: kto zużywa API/ntfy/Telegram po stronie odbiorców alertów; repo monitora poza zakresem skanu.
- Porównanie z poprzednią mapą (do 5b4ac92, 90 commitów): ten sam wniosek (1 człowiek, 3 tożsamości); wolumen +22 commity, `quality-monitoring` i `regression-harness` nowe; liczby tam i tu nie są wprost porównywalne dla `shared-ui` (przesunięcie `contract/**`, `dev/**` do `regression-harness`, kontrakt skanu).

## Polecenia
- `git -c core.quotepath=off log --format=%H%x09%B%x1e` + awk (trailery Co-authored-by: Claude, Claude-Session → `.work/pt.tsv`)
- awk join `.work/pt.tsv` × `.work/commit-files.tsv` (noise == "-") → commity per możliwość, udział trailerów
- `git log --format=%aN|%ad --date=short` (tożsamości, okresy); `git log --format=%B | grep Co-Authored-By` (modele)
- `git ls-files | grep -i codeowners|CONTRIBUTING`
- `gh pr list --state all --json author,mergedBy,reviews,comments`; `gh issue list --json assignees,comments`; `gh api repos/.../collaborators`
- `git log c8a6488..ef56ddc --format=%ad` (tempo M-4); `git log --grep` (ntfy/Telegram)
