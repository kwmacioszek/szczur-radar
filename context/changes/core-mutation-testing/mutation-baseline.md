# Pomiar bazowy PIT (S-45, Faza 1)

Stan rdzenia przed zabijaniem mutantów (Fazy 3 i 4).

## Nagłówek

- **Kod:** `5baf6fa` (`5baf6fa763c585dced88df5751c9f895618c7f95`) plus niezacommitowany profil `mutation` w `szczur-radar-api/pom.xml` (Faza 1). Kod produkcyjny i testy są bez zmian.
- **Polecenie:** `./mvnw -B -Pmutation test` z `szczur-radar-api/`.
- **Wersje:** PIT (`pitest-maven`) 1.30.2, `pitest-junit5-plugin` 1.2.3, JUnit Jupiter i Platform 6.0.3 (BOM Spring Boot 4.0.8), Mockito 5.20.0, OpenJDK 25.0.4.1.
- **Ustawienia:** `threads=4`, mutatory `STRONGER`, domyślne `timeoutFactor` i `timeoutConstant`.
- **Maszyna:** lokalny Mac, 8 CPU.
- **Czas ściany:** wtyczka była już pobrana, a klasy testów skompilowane.

  | Przebieg | Start (lokalnie) | Czas ściany | Czas PIT |
  |---|---|---:|---:|
  | 1 | 2026-11-13 20:13:48 | 266 s (4 min 26 s) | 4 min 22 s |
  | 2 | 2026-11-13 20:18:30 | 293 s (4 min 53 s) | 4 min 49 s |

  Oba przebiegi mieszczą się w limicie 10 min.
- **Raporty:** `target/pit-reports/` (`mutations.xml` i `index.html`) to raport z przebiegu 2. Tabela niżej pochodzi z przebiegu 1.
- **Liczby:** 1126 mutantów i 6388 uruchomień testów (5,67 na mutanta).
- **Stabilność:** status 1125 z 1126 mutantów jest identyczny w obu przebiegach. Jeden mutant zmienił status z `KILLED` na `TIMED_OUT`. Podział „wykryty / ocalały” jest identyczny (szczegóły w sekcji „Stabilność”).

## Tabela per klasa (przebieg 1)

Kolumny:

- **Wygenerowane:** liczba mutantów w klasie.
- **KILLED, SURVIVED, NO_COVERAGE, TIMED_OUT:** liczby mutantów o tym statusie z `mutations.xml`.
- **Siła testów:** wykryte / pokryte, tak jak liczy ją PIT. Wykryte to wszystko poza `SURVIVED` i `NO_COVERAGE`. Pokryte to wygenerowane minus `NO_COVERAGE`.

W obu przebiegach nie pojawił się żaden inny status (`MEMORY_ERROR`, `RUN_ERROR`, `NON_VIABLE`).

Klasy są podane bez prefiksu `pl.szczurradar.api.`. Klasy zagnieżdżone (`$`) są osobnymi wierszami.

Trzy obszary:

- **walidator V2:** `agents.assessor.*`;
- **jakość ocen:** `posts.AssessmentQuality*`;
- **przebieg:** `pipeline.*`, czyli `assess`, `flow`, `state` (tylko `PostTransitions$Outcome`, `Durations` i `RunInterruptedException`), `run`, `step`, `PipelineRunService` i `RunWatchdog`.

| Klasa | Wygenerowane | KILLED | SURVIVED | NO_COVERAGE | TIMED_OUT | Siła testów |
|---|---:|---:|---:|---:|---:|---:|
| `agents.assessor.AssessmentResult` | 28 | 18 | 10 | 0 | 0 | 64% |
| `agents.assessor.AssessmentResult$Status` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.Assessor` | 16 | 15 | 1 | 0 | 0 | 94% |
| `agents.assessor.Prefilter` | 8 | 8 | 0 | 0 | 0 | 100% |
| `agents.assessor.Provenance` | 38 | 20 | 18 | 0 | 0 | 53% |
| `agents.assessor.Provenance$Source` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Assertiveness` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.AssessmentInputV1` | 5 | 5 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Attribution` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.ClaimKind` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Direction` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Document` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Field` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Flag` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Item` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.ItemType` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Modifier` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Parent` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Reference` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.Relevance` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.TargetKind` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.contract.TextFields` | 10 | 10 | 0 | 0 | 0 | 100% |
| `agents.assessor.prompt.AssessmentPrompt` | 2 | 2 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.ContractV2Rules` | 1 | 1 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.FailureClass` | 6 | 6 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.OutputParser` | 17 | 13 | 3 | 1 | 0 | 81% |
| `agents.assessor.validation.OutputParser$Rejected` | 3 | 2 | 1 | 0 | 0 | 67% |
| `agents.assessor.validation.RuleBasedValidator` | 5 | 5 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.RuleId` | 2 | 2 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.StanceTable` | 29 | 22 | 7 | 0 | 0 | 76% |
| `agents.assessor.validation.ValidationResult` | 9 | 7 | 2 | 0 | 0 | 78% |
| `agents.assessor.validation.rules.AnchorFromItemRule` | 9 | 9 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.ClaimTargetsExistRule` | 10 | 10 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.ContextualAnchorFromParentRule` | 9 | 9 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.DirectionalStanceAuthorClaimRule` | 15 | 15 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.EvidenceFromItemRule` | 7 | 7 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.IrrelevantHasNoContentRule` | 9 | 8 | 1 | 0 | 0 | 89% |
| `agents.assessor.validation.rules.NoRepeatedReferencesRule` | 15 | 15 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.QuoteIsFragmentRule` | 8 | 8 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.QuoteNotEmptyRule` | 8 | 8 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.QuoteReferenceRule` | 6 | 6 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.QuotedText` | 17 | 15 | 2 | 0 | 0 | 88% |
| `agents.assessor.validation.rules.StanceAssertivenessRule` | 9 | 7 | 2 | 0 | 0 | 78% |
| `agents.assessor.validation.rules.StanceClaimCountRule` | 7 | 7 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.StanceClaimsCoverTargetRule` | 13 | 13 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.StanceClaimsExistRule` | 10 | 10 | 0 | 0 | 0 | 100% |
| `agents.assessor.validation.rules.UniqueIdsRule` | 12 | 12 | 0 | 0 | 0 | 100% |
| **Suma: walidator V2 (agents.assessor.*)** | **359** | **311** | **47** | **1** | **0** | **87%** |
| `posts.AssessmentQuality` | 56 | 52 | 4 | 0 | 0 | 93% |
| **Suma: jakość ocen (posts.AssessmentQuality*)** | **56** | **52** | **4** | **0** | **0** | **93%** |
| `pipeline.PipelineRunService` | 137 | 72 | 37 | 23 | 5 | 68% |
| `pipeline.RunWatchdog` | 10 | 7 | 2 | 0 | 1 | 80% |
| `pipeline.assess.AssessmentInputs` | 3 | 0 | 1 | 0 | 2 | 67% |
| `pipeline.assess.AssessmentPool` | 12 | 0 | 0 | 12 | 0 | — |
| `pipeline.assess.ConcurrentAssessmentDispatcher` | 160 | 113 | 32 | 10 | 5 | 79% |
| `pipeline.assess.ConcurrentAssessmentDispatcher$LostCounts` | 6 | 6 | 0 | 0 | 0 | 100% |
| `pipeline.assess.ConcurrentAssessmentDispatcher$Progress` | 17 | 13 | 4 | 0 | 0 | 76% |
| `pipeline.flow.RunFlow$AssessedBatch` | 18 | 17 | 1 | 0 | 0 | 94% |
| `pipeline.flow.RunFlow$FetchedBatch` | 6 | 6 | 0 | 0 | 0 | 100% |
| `pipeline.flow.RunFlow$RejectedPost` | 1 | 1 | 0 | 0 | 0 | 100% |
| `pipeline.flow.RunFlow$RunRequest` | 1 | 0 | 1 | 0 | 0 | 0% |
| `pipeline.flow.RunFlow$SourceFetch` | 13 | 12 | 1 | 0 | 0 | 92% |
| `pipeline.flow.RunFlow$SourceStored` | 16 | 12 | 4 | 0 | 0 | 75% |
| `pipeline.flow.RunFlow$StoredBatch` | 8 | 7 | 1 | 0 | 0 | 88% |
| `pipeline.flow.RunSequence` | 53 | 50 | 3 | 0 | 0 | 94% |
| `pipeline.run.AttentionReason` | 1 | 1 | 0 | 0 | 0 | 100% |
| `pipeline.run.InterruptionCause` | 2 | 2 | 0 | 0 | 0 | 100% |
| `pipeline.run.PipelineRun` | 65 | 39 | 6 | 20 | 0 | 87% |
| `pipeline.run.PipelineRunRepository` | 14 | 0 | 0 | 14 | 0 | — |
| `pipeline.run.PipelineRunSource` | 8 | 0 | 0 | 8 | 0 | — |
| `pipeline.run.PipelineRunStep` | 9 | 0 | 0 | 9 | 0 | — |
| `pipeline.run.RunKind` | 1 | 1 | 0 | 0 | 0 | 100% |
| `pipeline.run.RunStatus` | 1 | 1 | 0 | 0 | 0 | 100% |
| `pipeline.run.RunStep` | 1 | 1 | 0 | 0 | 0 | 100% |
| `pipeline.run.SourceFetchStatus` | 1 | 1 | 0 | 0 | 0 | 100% |
| `pipeline.run.StepStatus` | 1 | 1 | 0 | 0 | 0 | 100% |
| `pipeline.state.Durations` | 24 | 19 | 5 | 0 | 0 | 79% |
| `pipeline.state.PostTransitions$Outcome` | 8 | 8 | 0 | 0 | 0 | 100% |
| `pipeline.state.RunInterruptedException` | 3 | 3 | 0 | 0 | 0 | 100% |
| `pipeline.step.AssessmentSelection` | 8 | 8 | 0 | 0 | 0 | 100% |
| `pipeline.step.NoPostsFetchedException` | 3 | 3 | 0 | 0 | 0 | 100% |
| `pipeline.step.PostStore` | 63 | 56 | 7 | 0 | 0 | 89% |
| `pipeline.step.PostStore$SourceKey` | 1 | 1 | 0 | 0 | 0 | 100% |
| `pipeline.step.SourceIngest` | 36 | 34 | 0 | 2 | 0 | 100% |
| **Suma: przebieg (pipeline.*)** | **711** | **495** | **105** | **98** | **13** | **83%** |
| **Łącznie** | **1126** | **858** | **156** | **99** | **13** | **85%** |

Przebieg 2 różni się tylko wierszem `pipeline.assess.ConcurrentAssessmentDispatcher`: KILLED 112, TIMED_OUT 6. Sumy przebiegu: KILLED 494, TIMED_OUT 14. Łącznie: KILLED 857, TIMED_OUT 14.

## Zakres

- W raporcie nie ma mutanta w klasie spoza zakresu. Grep z kryterium Fazy 1 na `<mutatedClass>` (podpakiety `api`, `MissingDays`, `NightSummary`, `RunRecorder`, `PostTransitions` bez `$`) daje 0.
- `PostTransitions$Outcome` ma w raporcie 8 mutantów, wszystkie `KILLED`.
- `agents.assessor.validation` ma 217 mutantów `KILLED`. To rozstrzyga spike JUnit 6 pod Mavenem: PIT wysłał do miniona 95 klas testów i testy naprawdę chodzą, bez jawnej zależności `junit-platform-launcher`.

## Jakie testy uruchamia PIT

Sprawdziliśmy to jednorazowym przebiegiem z `-DfullMutationMatrix=true`. Trwał 551 s (czas ściany) i uruchomił 33 977 testów (30,17 na mutanta). Raportu z tego przebiegu nie ma w `target/`: tam leży zwykły raport z przebiegu 2.

Elementy `coveringTests`, `killingTests` i `succeedingTests` z tego raportu wymieniają 54 klasy testów:

- żadna klasa nie jest `*IT`;
- żadna nie ma tagu `measurement` (`LiveSmokeTest`, `*Recorder`, `*Sampler`, `*Report`, `WykopPagingProbe`, `RedditSheet`);
- żadna nie ma tagu `mutation`.

Grep z kryterium 1.4 (`IT[.$\[]|\.measurement\.|measurement(Method|Class|ThroughTheContainer)`) daje jednak 567 trafień. Wszystkie pochodzą z czterech klas pakietu `measurement`: `GoldDataTest`, `RecordingsReplayTest`, `RedditAnonymizerTest` i `RedditMeasuresTest`.

To zwykłe testy jednostkowe bez tagu. Chodzą też w każdym `./mvnw -B test` (są w `target/surefire-reports`). Wzorzec `\.measurement\.` łapie pakiet, a nie tag. Ten sam grep bez `\.measurement\.`, za to z nazwami klas z tagiem `measurement`, daje 0.

Pełna macierz zmienia status mutantów tylko między `KILLED` a `TIMED_OUT`, bo uruchamia każdy pokrywający test, także wolne. W podziale „wykryty / ocalały” wynik jest identyczny jak w przebiegu 1.

## Stabilność

Porównaliśmy status każdego mutanta w obu przebiegach. Klucz to `mutatedClass`, `mutatedMethod` z numerem lambdy obciętym do `lambda$name`, `methodDescription`, `mutator`, `description` i `lineNumber`. Porównanie zrobiliśmy też z `indexes` i `blocks` i wynik jest ten sam.

Status 1125 z 1126 mutantów jest identyczny. Różni się jeden:

- **Klasa i metoda:** `pl.szczurradar.api.pipeline.assess.ConcurrentAssessmentDispatcher#dispatch(Ljava/util/UUID;Ljava/util/List;)Lpl/szczurradar/api/pipeline/flow/RunFlow$AssessedBatch;`
- **Linia:** 190
- **Mutator:** `org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_IF`
- **Opis:** „removed conditional - replaced equality check with true”
- **Statusy:** w przebiegu 1 `KILLED`, w przebiegu 2 `TIMED_OUT`.

Linia 190 to `if (postIds.isEmpty())`. Mutant sprawia, że `dispatch` zawsze zwraca pusty wynik. Test, który na to trafi, albo od razu dostaje złą asercję (`KILLED`), albo czeka na pracę, która nie przychodzi (`TIMED_OUT`). Który z tych testów PIT uruchomi pierwszy, zależy od przebiegu.

Według reguły „Statusy” z planu oba statusy to „wykryty”. Strażnik (Faza 2) dzieli mutanty tylko na „wykryte” (`KILLED`, `TIMED_OUT`, `MEMORY_ERROR`, `RUN_ERROR`, `NON_VIABLE`) i „ocalałe” (`SURVIVED`, `NO_COVERAGE`). Pod tym podziałem oba przebiegi są identyczne, więc strażnik nie zmieniłby wyniku. Ustawień czasu PIT (`timeoutFactor`, `timeoutConstant`) ani `threads` nie zmienialiśmy.

To interpretacja agenta kryterium 1.6 („identyczny status każdego mutanta”). Czeka na przegląd właściciela.

## Klasy bez pokrycia testami jednostkowymi

To tylko fakt z pomiaru. Decyzja, czy wykluczyć te klasy, czy zabić ich mutanty testem jednostkowym, należy do Fazy 4.

Wszystkie mutanty tych klas w zakresie są `NO_COVERAGE`: żaden `*Test` ich nie wykonuje.

| Klasa | NO_COVERAGE |
|---|---:|
| `pipeline.assess.AssessmentPool` | 12 |
| `pipeline.run.PipelineRunRepository` | 14 |
| `pipeline.run.PipelineRunSource` | 8 |
| `pipeline.run.PipelineRunStep` | 9 |

Duże części `NO_COVERAGE` mają też klasy:

- `pipeline.PipelineRunService`: 23 ze 137;
- `pipeline.run.PipelineRun`: 20 z 65;
- `pipeline.assess.ConcurrentAssessmentDispatcher`: 10 ze 160.

