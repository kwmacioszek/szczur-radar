<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Testy mutacyjne rdzenia (S-45)

- **Plan**: context/changes/core-mutation-testing/plan.md
- **Scope**: Full plan (fazy 1–6; w Fazie 6 tylko wiersze Automated, Manual 6.6 czeka na decyzję właściciela)
- **Reviewed phases**: 1, 2, 3, 4, 5, 6
- **Date**: 2026-11-14
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Success criteria evidence

- `./mvnw -B test` lokalnie (HEAD `88c53b5`): 1309 testów zielonych. `MutantRegistryTest` uruchomiony (16 testów), `SurvivingMutantsTest` nie.
- `./mvnw -B -Pmutation test` lokalnie: kod 0 w 274 s. 1126 mutantów, 1079 wykrytych, 47 ocalałych (w tym 4 `NO_COVERAGE`), strażnik zielony.
- `git diff --stat origin/main...HEAD -- szczur-radar-api/src/main`: zmienione tylko dwa pliki `CLAUDE.md`, kod produkcyjny bez zmian.
- `mutation` jest w `needs:` joba `railway-config` (`ci.yml:309`). `REPOSITORY_GUIDELINES.md` i `README.md` wymieniają `-Pmutation` i `AcceptedMutants`.
- CI na PR #172, HEAD `88c53b5`: wszystkie checki zielone. Job `mutation` zielony w 5 przebiegach na 4 SHA, w tym rerun na `2bede0e`. Czasy: 6:27, 7:04, 8:26, 6:37, 6:50.
- Manual 6.6 (właściciel akceptuje powody w `AcceptedMutants` i tabelę `sql-breaks.md`) czeka. To decyzja człowieka, nie praca agenta; ustalenia F2 i F4 dotyczą właśnie tych powodów.

## Findings

### F1 — `RunRecorder$Counters` poza PIT i bez testu mapowania

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/pom.xml:274; szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/RunRecorder.java:470-490
- **Detail**: To luka planu, a kod robi to, co plan kazał. Wzorzec `pl.szczurradar.api.pipeline.state.RunRecorder*` wyklucza też zagnieżdżone `RunRecorder$Counters` i `RunRecorder$StepOutcome`. Plan uzasadniał wykluczenie zdaniem „żaden `*Test` nie tworzy tych klas”. Dla `Counters` to nieprawda: dyspozytor go buduje, a fabryki `assessedOk`/`assessedFailed` liczą martwe posty i fallback flex (`dead ? 1 : 0`, `flexFallback ? 1 : 0`) — to logika rdzenia. Testy jednostkowe budują oczekiwanie tą samą fabryką (`verify(recorder).count(RUN, Counters.assessedFailed(true, 1, false))`), więc zamiana `dead` na 0 w fabryce przejdzie przez nie niezauważona. `sql-breaks.md` też nie ma tego wiersza. Plan robi wyjątek dla `PostTransitions$Outcome` z tego samego powodu, ale dla `Counters` go nie zrobił.
- **Fix A ⭐ Recommended**: Wykluczyć tylko `pl.szczurradar.api.pipeline.state.RunRecorder` i `…RunRecorder$StepOutcome` (bez `*`, jak `PostTransitions`). Potem dodać `CountersTest`, który sprawdza pola każdej fabryki, i uruchomić `-Pmutation test` jeszcze raz.
  - Strength: Mapowanie licznika jest mierzone tak samo jak `PostTransitions$Outcome`, a komentarz w `pom.xml` staje się prawdziwy.
  - Tradeoff: Kolejne uruchomienie PIT (~5 min) i być może kilka nowych wpisów w rejestrze. Zmiana zakresu po Fazie 6, więc trzeba jeszcze raz przejść przez CI.
  - Confidence: HIGH — klasa to czysty record z fabrykami, bez SQL.
  - Blind spot: Nie sprawdzono, czy jakiś IT (`PipelineRunIT`) przypadkiem nie łapie złego `dead` przez licznik `dead` w przebiegu.
- **Fix B**: Zostawić wykluczenie i dopisać `Counters` jako znaną lukę do `sql-breaks.md` (ręczne zepsucie `dead ? 1 : 0` → `0` z IT, który je łapie).
  - Strength: Bez zmiany zakresu PIT i bez nowego przebiegu CI joba `mutation`.
  - Tradeoff: Ochrona zostaje ręczna i jednorazowa, a nie regresyjna.
  - Confidence: MED — zależy od tego, czy jakiś IT złapie zepsucie.
  - Blind spot: Nie wiadomo, czy taki IT istnieje.
- **Decision**: FIXED (Fix A): excluded only `RunRecorder` (no `*`), new `pipeline/state/CountersTest`; `-Pmutation test` 269 s, exit 0, all 10 `Counters` mutants KILLED; docs updated (REPOSITORY_GUIDELINES.md, README.md, pipeline/CLAUDE.md)

### F2 — Wpisy rejestru zależne od kolejności PIT mogą losowo czerwienić strażnika

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/mutation/AcceptedMutants.java:44-53, :256-260
- **Detail**: Strażnik jest ścisły w obie strony: wpis bez ocalałego czerwieni CI i blokuje deploy. Dwa wpisy `OutputParser.readSchemaText` przeżywają tylko dlatego, że `OutputParser.SchemaText.<clinit>` w procesie PIT (minion) już się wykonał, zanim PIT wstawił mutanta — sam powód mówi „in PIT's run only”. Kiedy proces PIT ruszy od nowa (po timeoucie albo błędzie innego mutanta tej klasy, przy zmianie kolejności, po aktualizacji PIT), mutant może zostać zabity. Wtedy wpis staje się zbędny, a CI czerwienieje przez zmianę bez związku. Wpis `ConcurrentAssessmentDispatcher$Progress.finished` sam przyznaje, że wyścigu „nie da się wymusić”. Z tym mutantem przeplot wątków może dać drugą linię postępu, a testy sprawdzają linie przez `containsExactly`. Dowody stabilności: 2 przebiegi bazowe, 5 w CI i 1 lokalny, wszystkie zielone. Ryzyko jest więc niskie, ale realne dla joba, który blokuje deploy (S-30).
- **Fix A ⭐ Recommended**: Wyjąć `readSchemaText` spod PIT przez `<excludedMethods>` z komentarzem w `pom.xml` i usunąć jego dwa wpisy. Wpis `Progress.finished` zostawić, ale opisać w Javadocu `AcceptedMutants` i w README, co zrobić, gdy wpis nagle okaże się zbędny.
  - Strength: Usuwa źródło losowości, które zależy od kolejności ładowania klas w PIT. Zgodne z „Key Discoveries” planu (`excludedMethods` to sposób na mutanty poza zasięgiem testu).
  - Tradeoff: `excludedMethods` dopasowuje nazwę metody we wszystkich klasach z zakresu. Trzeba sprawdzić, że `readSchemaText` jest jedyna. Zostaje jeden wpis z teoretycznym wyścigiem.
  - Confidence: MED — mechanizm PIT jest pewny, unikalności nazwy nie sprawdzono.
  - Blind spot: Nie mierzono, jak często mutant `Progress.finished` byłby zabity pod obciążeniem CI.
- **Fix B**: Zostawić wpisy jak są i tylko udokumentować ryzyko i procedurę (w README i w komentarzu joba).
  - Strength: Zero zmian zakresu; 8 zielonych przebiegów to jakiś dowód.
  - Tradeoff: Jeśli wpis się przełączy, czerwony PR bez związku ze zmianą trzeba będzie diagnozować.
  - Confidence: MED.
  - Blind spot: Jak wyżej.
- **Decision**: FIXED (Fix A): `excludedMethods` `readSchemaText` in profile `mutation` with a comment, 3 entries removed, procedure for an entry that turns needless in the `AcceptedMutants` Javadoc and README; `Progress.finished` kept

### F3 — Zmiana `roadmap.md` bez aktualizacji `render_roadmap_svg.py` i `roadmap.svg`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/foundation/roadmap.md (niezacommitowane); tools/render_roadmap_svg.py:207; docs/visuals/roadmap.svg
- **Detail**: Niezacommitowana zmiana w `roadmap.md` przestawia S-45 z `proposed` na `in-progress`, w tabeli i w statusie slice'a. Reguła repo mówi, że każda zmiana `roadmap.md` aktualizuje dane w `tools/render_roadmap_svg.py` i regeneruje `docs/visuals/roadmap.svg`. Skrypt nadal ma `"proposed"` (`:207`; klucz dla „w toku” to `"progress"`, `:31`), a svg nie był regenerowany.
- **Fix**: Zmienić `"proposed"` na `"progress"` w `render_roadmap_svg.py:207`, uruchomić `python3 tools/render_roadmap_svg.py` i zacommitować wszystkie trzy pliki razem.
- **Decision**: FIXED: `render_roadmap_svg.py:207` → `"progress"`, `docs/visuals/roadmap.svg` regenerated (only the S-45 card and M-4 counters change)

### F4 — Kategorie i treść kilku powodów w `AcceptedMutants` się nie zgadzają

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/mutation/AcceptedMutants.java:34-38, :44-53, :130-134, :156-165, :226-255, :256-260
- **Detail**: Plan definiuje `równoważny:` jako „zachowanie się nie zmienia”, a `obronny:` jako „gałąź nieosiągalna, z odwołaniem do miejsca, które ją wyklucza”. Kilka wpisów to pomyliło albo ma nieścisły powód. Właściciel będzie je oceniał w 6.6.
  - `readSchemaText` ×2 (`:44-53`) oznaczone `równoważny:`, choć poza PIT mutant psuje każdy test `OutputParser`. To artefakt narzędzia (zob. F2), nie równoważność.
  - `PipelineRunService.execute`, kopia `finally` „dla Error” (`:130-134`): twierdzenie „catch never ran” jest nieścisłe. Wspólny handler javac obejmuje też wyjątek rzucony wewnątrz `catch`, już po `interrupted = true` (`PipelineRunService.java:353`). W praktyce gałąź jest nieosiągalna, bo dalej są tylko `log.info`/`log.debug`. To więc `obronny:` z takim uzasadnieniem.
  - `ConcurrentAssessmentDispatcher.assess`, kopie z catch-all i z `RuntimeException` (`:226-255`): argumentem jest nieosiągalność („only a VM error could escape”), czyli `obronny:`, ale bez odwołania do miejsca (`ConcurrentAssessmentDispatcher.java:317-343`, konstruktor `AssessmentResult.java:48`).
  - Kopie „record refused” (`:156-165`): równoważność opiera się na tym, że `WHERE` w `PostTransitions.record` (`:97-99`) jest taki sam jak w `release` (`:121`). `PostTransitions` jest poza PIT, więc powód powinien wskazać te linie.
  - `AssessmentResult.callFailed` (`:34-38`) opiera się na niezmienniku konstruktora (`AssessmentResult.java:48`), więc to raczej `obronny:`.
  - `Progress.finished` (`:256-260`) jako `tekst:`: mutant zmienia liczbę linii logu, a nie treść komunikatu.
- **Fix**: Poprawić kategorie i treść tych 8–10 wpisów (`obronny:` z cytowanymi liniami), bez zmiany kluczy. `MutantRegistryTest` i strażnik pozostaną zielone.
- **Decision**: FIXED: 5 reasons recategorised `równoważny:` → `obronny:` with line references (execute catch-all copy, 4 dispatcher `assess` copies); line references added to the record-refused and `callFailed` entries; `Progress.finished` reworded; `MutantRegistryTest` and guard green

### F5 — Nieaktualny tekst w `mutation-baseline.md`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/core-mutation-testing/mutation-baseline.md:173, :179, :300
- **Detail**: `:173` mówi „Czeka na przegląd właściciela”, choć `:157` zapisuje już decyzję właściciela. `:179` mówi „Decyzja … należy do Fazy 4”, choć `:177` zapisuje decyzję. `:300` to resztka zrzutu słownika Pythona (`statuses: {'KILLED': 1068, …}`).
- **Fix**: Usunąć zdania z `:173` i `:179` oraz linię `:300`.
- **Decision**: FIXED: stale lines :173, :179, :300 removed; section „Po przeglądzie implementacji (2026-11-14)” added (1133 mutants, 44 survivors and entries, `RUN_ERROR` of `PostStore#cause` noted)

### F6 — Testy getterów przez refleksję i testy przypięte do implementacji

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/run/PipelineRunTest.java (`aRunLoadedFromItsRowReadsBackWhatOnlyTheDatabaseWrites`), PipelineRunStepTest.java, PipelineRunSourceTest.java, PipelineRunServiceTest.java:151, PipelineRunRepositoryTest.java:40-44
- **Detail**: To skutek decyzji właściciela, żeby klasy pokryte wcześniej tylko przez IT zostały w zakresie PIT (`88c53b5`). Faza 4 kazała w takim przypadku najpierw zapytać, a pytanie i decyzja przyszły po napisaniu testów. Kilka testów ustawia pola przez `ReflectionTestUtils.setField(…, "lastActivityAt", …)` i czyta je getterem. Zabijają mutanty getterów, ale o zachowaniu mówią niewiele, a zmiana nazwy pola psuje je dopiero w czasie uruchomienia. `PipelineRunServiceTest:151` przypina dosłowny SQL `SELECT pg_advisory_xact_lock(:lock)`. `PipelineRunRepositoryTest` stubuje `existsByStatusIn(List.of(QUEUED, RUNNING))`, więc sama zmiana kolejności listy go zepsuje. Ryzyko niskie, plan nie wymagał przeglądu jakości testów.
- **Fix**: Bez zmian teraz. Jeśli zakres PIT urośnie o kolejne encje, wykluczać ich gettery (`excludedMethods`) zamiast pisać testy refleksyjne.
- **Decision**: SKIPPED

### F7 — Job `mutation` jest najdłuższym jobem CI, a komentarz podaje tylko czas lokalny

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:66-105; szczur-radar-api/pom.xml:37
- **Detail**: W CI job trwał 6:27–8:26 (5 przebiegów), a `backend` 3:43. `mutation` jest więc teraz krytyczną ścieżką do `railway-config` i Wait for CI, ok. 3–5 min ponad `backend`, a najdłuższy przebieg jest blisko planowej granicy 10 min. Komentarz `timeout-minutes` cytuje tylko czasy lokalne (266–336 s). `pit.threads=4` jest na stałe, więc na zapasowych runnerach `szczur-ci` z mniejszą liczbą CPU zagłodzenie może zmienić `SURVIVED` w `TIMED_OUT`, a wtedy wpis staje się zbędny i strażnik czerwienieje.
- **Fix**: Wpisać do komentarza joba (i do „Po S-45” w `mutation-baseline.md`) czasy zmierzone w CI. W `tools/ci-runner/README.md` dodać notę o `-Dpit.threads` na mniejszych maszynach.
- **Decision**: FIXED: CI timings (6:27–8:26) in the `mutation` job comment; note on `pit.threads` for smaller VMs in `tools/ci-runner/README.md`
