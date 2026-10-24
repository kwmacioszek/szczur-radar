<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-11 — utknięty przebieg sam wraca do obiegu

- **Plan**: context/changes/stuck-run-self-heals/plan.md
- **Scope**: Full plan (fazy 3–4 mają zrobione wszystkie wiersze Automated; czekają ich wiersze Manual 3.3, 4.3–4.5)
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-24
- **Verdict**: NEEDS ATTENTION (przed triage; po triage wszystkie ustalenia FIXED)
- **Findings**: 0 critical, 3 warnings, 3 observations

Range: `0917117..7a5d20d` (ee0885e p1, f47fb4c p2, 9b23757 p3, 9377d4e p4, 7a5d20d epilogue), 31 files.
Automated criteria re-run: `./mvnw -B verify` 758 unit / 245 IT BUILD SUCCESS; `npm test` 400/400; `npm run build` OK; `tools/deploy-gate/test.sh` 69 PASS / 0 FAIL; CI `success` on 9377d4e and 7a5d20d. Manual rows 3.3, 4.3, 4.4, 4.5 pending (none ticked without evidence).

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

## Findings

### F1 — Bramka i sprzątanie biorą żywą kolejkę za martwą w przerwie między przebiegami

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java:43-46, state/RunRecorder.java:245-257
- **Detail**: Jedynym sygnałem życia przebiegu `queued` jest jego `created_at`. RETRY trwający dłużej niż `PIPELINE_STALL_TIMEOUT` (39 min na produkcji) zostawia za sobą RANGE z `created_at` sprzed ponad progu. W przerwie po końcu RETRY (`close`, `runEnded`, `own.remove`), a przed `recorder.start` RANGE, `existsLiveActive` zwraca false i `/api/deploy-safety` odpowiada `safe:true`. Deploy może wtedy przerwać żywą kolejkę, czyli dokładnie to, czego bramka ma pilnować. Na tej samej instancji `interruptOrphans` się nie uruchamia (`own` niepuste), a na innej robi to i tak odzyskiwanie startowe. Okno trwa milisekundy przy pollingu co 60 s, ale plan go nie wymienia.
- **Fix A ⭐ Recommended**: W obu predykatach liczyć przebieg zakończony w progu (`finished_at >= now() - threshold`) jako znak żywej kolejki
  - Strength: Zamyka przypadek jedną klauzulą w dwóch zapytaniach o tym samym kształcie; zombie po świeżym zakończeniu zostaje sprzątnięty najwyżej o jeden próg później.
  - Tradeoff: Sprzątnięcie sieroty, która powstała tuż po zakończonym przebiegu, opóźnia się o próg (cron 60 min i bramka 90 min nadal się mieszczą).
  - Confidence: HIGH — kolejka jest sekwencyjna, więc niedawny koniec przebiegu wiarygodnie oznacza, że wykonawca żyje.
  - Blind spot: Nie sprawdzono, ile trwa `runEnded` przy wielu adminach (od tego zależy długość okna).
- **Fix B**: Udokumentować jako zaakceptowany przypadek brzegowy (plan, `pipeline/CLAUDE.md`)
  - Strength: Bez zmiany kodu.
  - Tradeoff: Bramka deployu z S-14 ma udokumentowaną lukę w swoim jedynym zadaniu.
  - Confidence: MED — prawdopodobieństwo małe, ale skutek to przerwany nocny przebieg.
  - Blind spot: Długie RETRY zdarzają się po nocach z wieloma zaległościami — częstość nieznana.
- **Decision**: FIXED (Fix A) — `NOT EXISTS`/`OR EXISTS` na `finished_at` w progu w `RunRecorder.interruptOrphans` i `PipelineRunRepository.existsWithActivityWithin`; testy `OrphanedRunIT.aQueuedRunBehindARunThatJustEndedIsLeftAlone`, `DeploySafetyIT.aQueuedRunBehindARunThatJustEndedIsNotSafe` (break-check: czerwone bez klauzuli); `OrphanedRunIT` `@AfterEach` postarza `finished_at`; Javadoc, `pipeline/CLAUDE.md`, `deploy-plan.md` S-11

### F2 — Błąd sprzątania sierot blokuje każde żądanie startu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:199-200, 221-241
- **Detail**: `cleanUpOrphans()` nie ma `catch`: dowolny wyjątek (np. deadlock z `interruptAll`/`stall` innej instancji, błąd w kodzie) kończy start przebiegu 500, zanim zapadnie decyzja. `PipelineStartupRecovery.recover` (87-99) i `RunWatchdog.check` (85-95) logują i idą dalej. Sprzątanie, które może zablokować każdy start, odtwarza klasę awarii, którą ta zmiana usuwa.
- **Fix**: Złapać `RuntimeException` w `cleanUpOrphans`, zalogować ERROR i kontynuować; transakcja decyzyjna i tak odmówi 409, gdy przebieg jest aktywny. Dodać test z rzucającym `recorder`.
- **Decision**: FIXED — `try/catch (RuntimeException)` z ERROR `The clean-up of orphaned pipeline runs failed; the request is decided without it` w `PipelineRunService.cleanUpOrphans` (+ Javadoc); nowy `PipelineRunServiceTest.aFailedCleanUpIsLoggedAndTheRequestIsStillDecided` (break-check: czerwony bez `catch`)

### F3 — interruptOrphans może oznaczyć `lost` przebieg, który właśnie zapisał postęp

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/RunRecorder.java:245-257
- **Detail**: Podzapytanie `NOT EXISTS` jest nieskorelowane. Postgres liczy je raz na migawce instrukcji i nie liczy ponownie, gdy `UPDATE` czeka na zablokowany wiersz. Dlatego przebieg innej instancji, który jest tuż za progiem i właśnie commituje postęp, zostanie mimo to oznaczony `lost`. `stall()` nie ma tego problemu, bo jego test wieku dotyczy samego wiersza. Javadoc twierdzi, że „run its own watchdog would not stop is never taken from it here”, co nie jest ściśle prawdą. Możliwe tylko w oknie overlapu deployu (dwie instancje), na granicy progu; plan akceptuje wyścig w overlapie, ale nie nazywa tego przypadku.
- **Fix A ⭐ Recommended**: Poprawić Javadoc i `pipeline/CLAUDE.md`: przypadek brzegowy zaakceptowany, tylko w overlapie i na granicy progu
  - Strength: Zgodne z decyzją planu („What We're NOT Doing”: brak zatrzymania po stronie właściciela w overlapie) i z regułą pakietu „no row locks”.
  - Tradeoff: Rzadki fałszywy `lost` dla żywego przebiegu starej instancji, która i tak zaraz ginie.
  - Confidence: HIGH — wymaga jednocześnie dwóch instancji, pustego `own` nowej i postępu dokładnie na granicy 39 min.
  - Blind spot: Nie zmierzono, jak długo stara instancja żyje w overlapie przy wolnym starcie nowej.
- **Fix B**: Najpierw `SELECT … FOR UPDATE` aktywnych wierszy, potem decyzja
  - Strength: Zamyka wyścig całkowicie.
  - Tradeoff: Łamie regułę pakietu „no row locks” i dokłada blokady do ścieżki, którą dotykają wszystkie zapisy postępu.
  - Confidence: MED — poprawne, ale wymaga zmiany zasady i nowych testów współbieżności.
  - Blind spot: Interakcja z `openStep`/`count` pod blokadą niesprawdzona.
- **Decision**: FIXED (Fix A) — Javadoc `RunRecorder.interruptOrphans` i `pipeline/CLAUDE.md` opisują wyścig jako zaakceptowany przypadek brzegowy (overlap, granica progu, reguła „no row locks”); obietnica „never taken from it” usunięta

### F4 — Odmowa `source_not_configured` pomija sprzątanie

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:167-170
- **Detail**: Plan (Desired End State): „Każde żądanie startu (także zakończone 409 lub 400) oddaje posty `assessing`…”. `startRange` wywołuje `sources.resolve()` przed `queue()`, więc 400 `source_not_configured` kończy się przed `cleanUpOrphans()`; 409 i 400 `post_dead` sprzątają.
- **Fix**: Przenieść `sources.resolve(...)` i budowę przebiegu RANGE do dostawcy `toCreate` (po sprzątaniu) albo dopisać wyjątek w planie i `pipeline/CLAUDE.md`.
- **Decision**: FIXED (inaczej niż Fix: zachowana kolejność odmów) — `cleanUpOrphans()` przeniesione z `queue()` na początek `startRange` (przed `sources.resolve`) i `startPosts`; 503/409/400 zachowują dotychczasową kolejność; test `PipelineRunServiceTest.aSourceNotConfiguredIsRefusedAfterTheCleanUp` (break-check: czerwony bez sprzątania w `startRange`); `pipeline/CLAUDE.md` i `deploy-plan.md` S-11 wymieniają 503

### F5 — Warunek pustego `own` nie jest przypięty testem

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/OrphanedRunIT.java:234-248
- **Detail**: W `anInstanceWithARunOfItsOwnCleansUpNothing` własny przebieg ma świeżą aktywność, więc sam predykat „wszystko albo nic” już chroni martwy cudzy wiersz. Usunięcie `own.isEmpty() ?` z `PipelineRunService.java:225` zostawiłoby test zielonym. Postarzenie własnego przebiegu w IT koliduje z 5-sekundowym watchdogiem.
- **Fix**: Test jednostkowy `PipelineRunService` z mockami: przy niepustym `own` `recorder.interruptOrphans` nie jest wołane, a `releaseOrphans` jest.
- **Decision**: FIXED — `PipelineRunServiceTest.anInstanceWithARunOfItsOwnInterruptsNoOrphan` (własny przebieg trzymany w wątku kolejki; break-check: czerwony bez `own.isEmpty()`)

### F6 — Drobne nieścisłości w dokumentach względem kodu

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: PipelineRunRepository.java:32-36, RunRecorder.java (Javadoc), pipeline/CLAUDE.md; context/changes/stuck-run-self-heals/plan.md (Migration Notes); szczur-radar-web/dev/mock-api.js:23-25; REPOSITORY_GUIDELINES.md:66
- **Detail**: (a) „by the database's clock” — `created_at` pisze `@CreationTimestamp` zegarem JVM (`PipelineRun.java:63`), więc dla `queued` mieszają się dwa zegary (różnica pomijalna przy 39 min). (b) Migration Notes mówią „niesprawdzone”, a jest potwierdzone: stary `EnumValueConverter.convertToEntityAttribute` rzuca `IllegalArgumentException` przy `lost` (`persistence/EnumValueConverter.java:42`), więc po wycofaniu lista przebiegów admina się wysypie. (c) Nagłówek mocka mówi „po 39 min”, dane dają ~81 min. (d) Punkt „Deploy guard” w wytycznych nadal mówi „while a run is queued or running” bez „żywy”.
- **Fix**: Poprawić sformułowania: (a) „created_at z zegara aplikacji” albo `@CreationTimestamp(source = SourceType.DB)`, (b) „potwierdzone: stara wersja nie odczyta wiersza `lost`”, (c) „po progu”, (d) „żywy przebieg”.
- **Decision**: FIXED — (a) Javadoc `PipelineRunRepository`/`RunRecorder`/`DeploySafetyController` i `pipeline/CLAUDE.md`: `created_at` z zegara aplikacji, różnica pomijalna; (b) plan Migration Notes: potwierdzone, stary konwerter rzuca; (c) nagłówek mocka: „pierwsze żądanie startu po progu”; (d) `REPOSITORY_GUIDELINES.md` Deploy guard: „live run”, martwy nie trzyma
