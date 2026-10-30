<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-27 reddit-source — faza 5 (IaC, CI, dokumentacja, weryfikacja w PR env)

- **Plan**: context/changes/reddit-source/plan.md
- **Scope**: Phase 5 of 5
- **Reviewed phases**: 5
- **Date**: 2026-10-30
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 6 observations
- **Triage**: COMPLETE — F1–F5, F7–F10 FIXED; F6 ACCEPTED; 0 pending

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Kod, który merge uruchamia, jest poprawny: deklaracja `preserve()`, krok CI, migracja V13 i bramka nie mają ustaleń. Nic z zakresu planu nie jest pominięte. Wszystkie ustalenia dotyczą tekstu runbooków i dokumentów, czyli tego, co operator przeczyta podczas włączenia Reddita, końca archiwum albo awarii. Nie blokują one działania wdrożenia, ale po merge'u każda poprawka tych plików to osobny PR do `main`. Werdykt NEEDS ATTENTION opisuje stan sprzed triage; szczegóły ustaleń poniżej też, a pola Decision dokumentują rozwiązania.

## Findings

### F1 — Rollback „revert plus V14” nie zadziała dosłownie

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/deployment/deploy-plan.md:2112 (to samo sformułowanie: :2013)
- **Detail**: Tekst mówi, że naprawą jest revert w PR razem z `V14__…`. Zwykły revert usuwa `V13__posts_channel.sql`. `application.properties:23` ustawia tylko `spring.flyway.enabled=false`, a `MigrateCommand` tylko je włącza, więc Flyway waliduje z domyślnym `ignoreMigrationPatterns=*:future`: zastosowana V13 poniżej rozwiązanej V14 nie jest „future” i `migrate` kończy się błędem „applied migration not resolved locally”. Po włączeniu Reddita revert usuwa też `REDDIT_SUBREDDITS: preserve()`; plan proponuje wtedy `variable.delete`, którego `tools/deploy-gate/gate.jq:19` nie dopuszcza, więc `railway-config` jest czerwony, a naprawa nie wchodzi. Do tego zdanie „Without the variable Reddit's code is never called” jest za szerokie: `RedditPosts` jest beanem bezwarunkowym i `AssessmentInputs.of` woła go dla pozostawionych postów Reddita w przebiegach RETRY i POSTS, a po revercie bez mappera te posty rzucają `no RawInputMapper bean`. Prześledzone w konfiguracji i kodzie, nie wykonane.
- **Fix**: W rollbacku S-27 dopisać, że revert zostawia `V13__posts_channel.sql` w drzewie, a po włączeniu Reddita także deklarację `preserve()` (albo najpierw kolejność S-19), i co dzieje się z pozostawionymi postami Reddita; zawęzić „never called” do pobierania. Tę samą uwagę o zachowaniu pliku migracji dodać jako aktualizację w S-20 (:2013).
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: rollback S-27 mówi teraz, że revert zostawia `V13__posts_channel.sql`, po włączeniu Reddita także deklarację `preserve()`, i co dzieje się z pozostawionymi postami; „never called” zawężone do pobierania; aktualizacja dopisana w S-20.

### F2 — Teza o 20 nieudanych ocenach bez dowodu; runbook kończy się przed martwymi postami

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:2051, :2092-2093
- **Detail**: Linia 2051 podaje „(English posts)” i „not a defect of the source” bez dowodu, a plan tego zdania nie wymaga. Pomiar w PR env (2026-10-30, agregaty z `assessments` i `posts`): z 79 postów Reddita 70 ma ocenę, 9 jest `dead` (r/ValueInvesting 5, r/stocks 2, r/investing 2). Nieudane próby to wyłącznie walidacja wyjścia modelu: `schema_violation` 28 prób na 12 postach, `fabricated_evidence` 7 na 4, `invalid_structured_output` 5 na 5, `domain_invariant_violation` 1 na 1; bez `provider_error` i `timeout`. Przyczyna nie jest więc źródłem ani transportem, ale nie jest też potwierdzona jako „język”: to model z ustawień CI, nie produkcyjny, a związku z językiem nikt nie zmierzył. Wskaźnik `dead` wynosi 9/79 (11%), a nocny RETRY stoi przed zakresem (`PipelineRunService.java:185`), więc `dead_posts` pojawi się dopiero po trzeciej próbie, czyli około 2.–3. nocy. Runbook włączenia zamyka #121 po pierwszej nocy (:2092-2093), zanim ten sygnał może wystąpić.
- **Fix**: Zastąpić linię 2051 powyższymi liczbami z zaznaczeniem „przyczyna nieustalona, pomiar należy do S-28”; dopisać do sekcji PR-env wynik z `e2bafce` (przebiegi z sekcji Verification); w runbooku włączenia sprawdzić `dead_posts` po trzeciej nocy przed zamknięciem #121.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: linia 2051 zastąpiona pomiarem z zastrzeżeniem „przyczyna nieustalona”; dopisany wynik z `e2bafce`; runbook włączenia ma krok trzeciej nocy przed zamknięciem #121.

### F3 — Wyłączenie Reddita może dać dwa wdrożenia albo zostawić go włączonym

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:2103-2106
- **Detail**: Runbook woła `railway variable delete`, a potem bezwarunkowo `railway redeploy`, bez kontroli wyniku. CLI nie ma `--skip-deploys` dla `delete` (`railway variable delete --help`), a sekcja S-19 mówi, że na produkcji usunięcie może przedeployować usługę (:1445) albo zostać zaplanowane jako staged i wymagać zatwierdzenia w panelu (:1463). Efekt to dwa wdrożenia wbrew regule F-03 albo Reddit nadal włączony mimo zdania, że jest wyłączony. Sam dodatek `redeploy` jest uzasadniony, bo plan zakładał, że usunięcie zadziała samo (P2 niżej).
- **Fix**: Zastąpić bezwarunkowy redeploy krokami S-19: staged → zatwierdź w panelu; redeploy wystartował → czekaj na `/api/ready`; brak wdrożenia → `railway redeploy`; na końcu `/sources` pokazuje `reddit` `configured:false`.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: wyłączenie idzie za krokami S-19 (staged, redeploy albo jego brak) z kontrolą `/sources`.

### F4 — Runbook końca archiwum zawyża szybkość sygnału

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/deployment/deploy-plan.md:2099-2100, szczur-radar-api/README.md:103
- **Detail**: Status źródła bierze się z `sourcePosts.isEmpty()` (`SourceIngest.java:114`), a noc obejmuje D-2…D-1. Gdy archiwum przestaje dostarczać w dniu X: noc X+1 (X-1…X) i noc X+2 (X…X+1) mają jeszcze posty z X, więc są `succeeded`, a X+1 liczy się jako pokryty bez danych. Pierwszy `empty` i pierwszy alarm przychodzą trzeciej nocy, a X+1 nie pojawia się w „Dni bez danych”. Awaria trwająca około dwóch dni nie daje alarmu ani wpisu. Krok „fill from Dni bez danych” omija więc dni. Ścieżka `failed` działa zgodnie z tekstem. Potwierdzone czytaniem kodu, nie uruchomione.
- **Fix**: Podać opóźnienie (pierwszy sygnał `empty` trzeciej nocy) i po epizodzie `empty` wyznaczać ostatni dzień z danymi zapytaniem SQL po dniach dla `source = 'reddit'`, a wypełniać od niego.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: runbook i README podają opóźnienie sygnału `empty`; dopisane zapytanie SQL po dniach, sprawdzone w PR env.

### F5 — „Bez repository variable nie ma Reddita” przestanie być prawdą

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/deployment/deploy-plan.md:2037, szczur-radar-api/README.md:105, .github/workflows/ci.yml:437
- **Detail**: Zmienna nie jest zapieczętowana, więc po włączeniu na produkcji nowe PR envy skopiują jej wartość. Krok CI z pustą zmienną repozytorium ostrzeże wtedy „runs without Reddit”, choć środowisko będzie działać z listą z produkcji; krok LLM kopie usuwa, ten nie. Runbook wyłączenia nie wspomina też o `gh variable delete REDDIT_SUBREDDITS`, więc PR envy czytałyby martwe archiwum. Bez wpływu na bezpieczeństwo, crony poza produkcją są pomijane.
- **Fix**: Poprawić oba zdania w dokumentach i ostrzeżenie CI, dopisać `gh variable delete` do wyłączenia.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: poprawione oba zdania w dokumentach i ostrzeżenie CI; `gh variable delete` dopisane do wyłączenia.

### F6 — Agent uruchomił operatorskie polecenia z linkiem aktywacyjnym we własnej sesji

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:593, :2045
- **Detail**: S-07 mówi: „Run it in your own terminal, not through an AI agent's session”, bo link trafiłby do transkrypcji. Plan fazy 5 kazał użyć admina „z runbooka S-07”. Agent uruchomił `create-user` (2026-10-30, `68bc2de`) i ponownie `reset-password` podczas tego przeglądu przez `railway ssh`, z wyjściem przekierowanym do prywatnego pliku tymczasowego, poza transkrypcją; link i hasło nie zostały wypisane, pliki skasowano. Dotyczy to konta testowego w efemerycznym PR env, nie produkcji. Mimo to jest to dosłowne odstępstwo od reguły, a nie jej spełnienie.
- **Fix**: Zaakceptować dla PR env albo doprecyzować w S-07 wyjątek (wyjście tylko do pliku, konto testowe, PR env).
- **Decision**: ACCEPTED — 2026-10-30, na decyzję użytkownika: odstępstwo zaakceptowane dla konta testowego w PR env, bez zmiany S-07.

### F7 — PRD dodaje uzasadnienie decyzji prawnej bez zastrzeżenia

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/prd.md:279
- **Detail**: Doprecyzowanie retencji dodaje zdanie „bo projekt nie korzysta z API Reddita bezpośrednio, a Arctic Shift nie publikuje własnych warunków”, którego nie ma w kontrakcie planu (plan:370). `plan-brief.md:73` zapisuje tę decyzję jako „bez analizy prawnej”, a `research.md:30` zostawia pytanie otwarte. W PRD to zastrzeżenie znika i decyzja brzmi na mocniej ugruntowaną, niż jest. To treść wiersza Manual 5.9.
- **Fix**: Dopisać w PRD „(decyzja bez analizy prawnej)”.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: PRD mówi teraz „bez analizy prawnej”.

### F8 — `/api/ready` po merge'u może odpowiadać stary API

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/deployment/deploy-plan.md:2056
- **Detail**: V13 to `NOT NULL` bez domyślnej wartości, więc stary API nie zapisze posta. Jeśli `migrate` przejdzie, a nowy API nie wystartuje, stary zostaje na schemacie V13: krok 1 widzi `/api/ready` 200 od starego procesu, a każda noc pada na `store`. Cron i zewnętrzny monitor to zgłoszą, ale runbook tego nie odróżnia. Przebieg rozpoczęty po kontroli strażnika i piszący podczas nakładki wdrożeń też skończy się `failed` na `store`, nie `interrupted`.
- **Fix**: W kroku 1 potwierdzić, że `SUCCESS` ma wdrożenie z commitu merge'u.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: krok 1 wymaga wdrożenia `SUCCESS` z commitu merge'u przed `/api/ready`.

### F9 — Nieaktualne odwołania i luźne sformułowania

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/deployment/deploy-plan.md:1879, :2013, :2017, :2025, :2027, :2048, :2050; szczur-radar-api/README.md:102; context/foundation/prd.md:273
- **Detail**: S-21 krok 4 (:1879) wskazuje S-27 jako pierwszy prawdziwy apply na produkcji, a S-27 nie planuje żadnej zmiany. S-20 (:2013) nadal mówi `V13__…`, a ten numer jest zajęty. „A failing archive ends … `DEADLINE_EXCEEDED`” (:2025, README:102) jest nieścisłe: trwale odrzucane żądanie kończy się po 3 próbach błędem HTTP albo transportu, a termin wymaga wolnego archiwum. „Shortest threshold (15 min)” to domyślne minimum `MIN_DEFAULT`, jawne `PIPELINE_STALL_TIMEOUT` może być niższe. „The engine does not change” (:2017, :2027) pomija przekazanie `channel` w `PostStore.java` (bez zmiany zachowania, ujawnione w prd.md:273). `item_type` (:2048) w API nazywa się `itemType`. Wynik `missing-days` (:2050) niczego nie dowodzi: źródło nigdy niepokryte nie ma okna (`MissingDays.java:30-32,86-88`), więc `reddit` brakuje niezależnie od wyniku przebiegu.
- **Fix**: Poprawić te miejsca jednym przejściem po tekście.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: poprawione wszystkie wymienione miejsca, także „Zachowanie silnika” w prd.md:273.

### F10 — Błędy w samym planie bez noty

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/reddit-source/plan.md:354, :362, :378, :386
- **Detail**: Implementacja słusznie odeszła od planu, ale plan tego nie odnotowuje. P1: `railway variable set REDDIT_SUBREDDITS "stocks,…"` nie jest poprawną formą CLI (wymaga `KEY=VALUE` albo `--stdin`) i pomija `--skip-deploys` z reguły F-03. P2: krok usunięcia zakłada, że zadziała samo (źródło F3). P3: „do 3 razy” w planie, a kod robi 3 próby, czyli 2 ponowienia; „pusta lista przerywa start” jest niejednoznaczne, bo pusta wartość oznacza brak konfiguracji, a ` , ` przerywa start. P4: kontrola `missing-days` nie może się nie udać (F9). P5: tekst ostrzeżenia z F5 pochodzi z planu.
- **Fix**: Dopisać Implementation Note w fazie 5, bez zmiany sekcji Progress.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw wszystkie F1–F10”: dopisana Implementation Note w fazie 5; Progress bez zmian.

## Scope and evidence

- Kanoniczny Progress: 33/34; jedyny oczekujący wiersz to 5.9 (Manual). Przegląd zawężony do fazy 5 na prośbę użytkownika, bo domyślny zakres „ukończonych faz” nigdy by jej nie objął: 5.9 czeka na decyzję po `/10x-archive`.
- Zakres Git: `68bc2de`, `7479a5e`, plan i pliki fazy w stanie `e2bafce` (po merge'u S-24 `ded07e9`). Zmiany poza fazą 5 oceniał raport `reviews/impl-review.md`.
- Dwa niezależne przeglądy: zgodność z Changes Required oraz bezpieczeństwo/jakość/wzorce. Żaden nie czytał `reviews/impl-review.md` ani wniosków autora implementacji. Kluczowe tezy sprawdzono potem w kodzie, dokumentach i `--help` CLI.
- Bez ustaleń: krok CI (kolejność przed strażnikiem i planem, `--skip-deploys`, `vars` tylko przez `env:`, odmowa na produkcji), deklaracja `preserve()` poza `variables.ignore`, bezpieczeństwo V13 (jedna transakcja, brak wierszy `reddit`), produkcja bez `REDDIT_SUBREDDITS`, zero zmian w `szczur-radar-web/` i `tools/db-backup/tables.sh`, linia Status zgodna z lekcją, pierwsze włączenie Reddita nie tworzy luki w „Dni bez danych”.
- Dodatki `### Migration` i `### Runbook — after the merge` nie są w kontrakcie planu, ale S-20 i S-21 mają te same sekcje i plan prosi o tę konwencję, więc Scope Discipline pozostaje PASS.

## Verification

Kryteria 5.1–5.8 wykonano ponownie na `e2bafce`, a nie tylko na `68bc2de`.

| Kryterium | Polecenie / kontrola | Rzeczywisty wynik |
|-----------|---------------------|------------------|
| 5.1 | CI przebieg 41084155107 i statusy PR | success we wszystkich 9 zadaniach; Railway API, web, db-backup `SUCCESS`; `MERGEABLE`/`CLEAN`. `gh pr checks --watch` tuż po pushu zakończył się exit 0 przy 8 oczekujących, więc wynik odczytano z `gh run list`/`gh run view` |
| 5.2 | `gh variable get REDDIT_SUBREDDITS` | `stocks,ValueInvesting,investing` |
| 5.3 | `/api/admin/pipeline-runs/sources` | `wykop` i `reddit` `configured:true`; jq exit 0 |
| 5.4 | przebieg zakresu `097aeb68-37fc-44bd-9b50-30a02081e790` za 28.10 | `reddit succeeded`, fetched 79, skipped 0, bez powodu `skipped`; jq exit 0 |
| 5.5 | feed i RAW | strona 0, post `208e17e8-b3d3-4b85-b73b-ade3ec6226e8`, `.subreddit == "ValueInvesting"`; jq exit 0 |
| 5.6 | ten sam przebieg | `newPosts` 0, `reddit succeeded`, fetched 79, skipped 0; jq exit 0 |
| 5.7 | `rg` deklaracji i kroku CI | `railway.ts:158`, `ci.yml:417` |
| 5.8 | `rg 'S-27'` w czterech plikach | każdy plik ma trafienia |
| 5.9 | Manual | oczekuje na operatora, bez dowodu do oceny |

Pozostałe dowody z tej samej weryfikacji:

- `/api/ready` 200, `/api/deploy-safety` `{"safe":true,"reason":null}`, `missing-days` `{"from":"2026-10-15","to":"2026-10-28","days":[]}` (zob. F9).
- Żądanie przebiegu zakresu zakolejkowało też przebieg `retry` `d1de65fc-af3d-43c5-87d0-787253ecb8fc`: 12 postów, 3 ok, 9 failed/dead, `partial`, `dead_posts`, powiadomienie w PR env. Niewielki koszt LLM w PR env, trwałe `dead` na 9 postach tego środowiska.
- Sesja admina odtworzona przez `reset-password` na koncie testowym (zob. F6); lokalne pliki z hasłem i ciasteczkiem skasowane.

Nie zweryfikowano: planu IaC na produkcji (bez `railway config plan` na `main`, „no change” wynika z `preserve()` i precedensu `PIPELINE_STALL_TIMEOUT`), zachowania `railway variable delete` na produkcji, `railway redeploy` przy najnowszym wdrożeniu `SKIPPED`, ścieżki `migrate` przy złej wartości (prześledzona w kodzie, `RedditConfigurationTest` obejmuje tylko konfigurację), bieżącej liczby wierszy produkcji, a także strony statusu Arctic Shift. CLI lokalnie 5.71.0, w CI 5.67.1.
