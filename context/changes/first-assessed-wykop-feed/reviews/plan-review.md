<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-02 first-assessed-wykop-feed

- **Plan**: context/changes/first-assessed-wykop-feed/plan.md
- **Mode**: Deep
- **Date**: 2026-10-06
- **Verdict**: REVISE → SOUND (po triage)
- **Findings**: 0 critical, 5 warnings, 1 observation; wszystkie 6 FIXED

## Verdicts

| Dimension | Before triage | Verdict |
|-----------|---------------|---------|
| End-State Alignment | WARNING | PASS |
| Lean Execution | PASS | PASS |
| Architectural Fitness | WARNING | PASS |
| Blind Spots | FAIL | PASS |
| Plan Completeness | WARNING | PASS |

## Grounding

12/12 paths ✓, 8/8 symbols ✓, brief↔plan ✓ po F2, Progress↔Phase ✓ (6/6 faz, 26/26 kryteriów). Nowe pliki są planowanymi artefaktami implementacji.

## Findings

Ustalenia opisują wersję sprzed bieżącego triage. Wszystkie poprawki zostały zaakceptowane przez użytkownika i naniesione na plan; to przegląd planu, bez zatwierdzania implementacji.

### F1 — Recovery nie izoluje zapisów starego workera

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details; Phase 2 §1; Phase 3 §2 i §4
- **Detail**: Sam warunek `stage = 'assessing'` nie identyfikował właściciela próby. Po recovery nowy przebieg mógł ponownie ustawić post na ASSESSING, a spóźniony zapis lub sprzątanie starego workera zmienić stan nowej pracy. Przewidziany test chronił tylko status INTERRUPTED przebiegu. Railway uruchamia nowy deployment przed wyłączeniem poprzedniego po healthchecku: https://docs.railway.com/deployments/healthchecks.
- **Fix**: Właściciel próby w `posts.assessing_run_id`; przejęcie i zapisy blokują wiersz przebiegu przez FOR UPDATE, sprawdzają RUNNING i atomowo zapisują wynik oraz stan posta. Recovery używa tych samych blokad, a finally i końcowe sprzątanie cofają wyłącznie posty własnego przebiegu. Stary worker po utracie uprawnienia kończy pracę. IT obejmuje nowego właściciela oraz spóźniony wynik i sprzątanie starego workera.
  - Strength: Chroni wyniki i stan nowej pracy, również przed ponownym wystąpieniem tego samego statusu.
  - Tradeoff: Dodatkowa kolumna w V3 oraz doprecyzowanie granic i kolejności transakcji.
  - Confidence: HIGH — sam warunek statusu przepuszcza opisany wyścig.
  - Blind spot: Zachowanie podczas rzeczywistego deployu pozostaje do weryfikacji w implementacji.
- **Decision**: FIXED — assessing_run_id, transakcyjne warunki zapisu i rozszerzony IT recovery

### F2 — RANGE może ponownie wybrać post próbowany w RETRY

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Critical Implementation Details; Phase 3 §1–2; plan-brief, Success Criteria
- **Detail**: RANGE wybierał wszystkie STORED z zakresu mimo obietnicy najwyżej jednego podejścia do posta na żądanie. Post cofnięty do STORED po błędzie technicznym w poprzedzającym RETRY mógł zostać wybrany drugi raz. Brief obiecywał brak nowych ocen po powtórzeniu dnia, mimo celowych ponowień failed przez RETRY.
- **Fix**: StoredBatch przekazuje wyłącznie nowe identyfikatory, a RANGE wybiera STORED z tego zbioru i z first_run_id równym bieżącemu RANGE. Resztki pozostają do następnego RETRY lub jawnego POSTS. Brief ogranicza brak ponownych ocen do ASSESSED. IT obejmuje błąd w RETRY, brak drugiego wyboru w RANGE i ponowienie przy następnym żądaniu.
  - Strength: Realizuje istniejącą obietnicę bez dodatkowego rejestru żądań.
  - Tradeoff: Niedokończony post czeka na kolejne żądanie lub ręczne ponowienie.
  - Confidence: HIGH — kontrprzykład wynika bezpośrednio z reguł wyboru i cofnięcia stanu.
  - Blind spot: None significant.
- **Decision**: FIXED — nowe ID z StoredBatch, first_run_id, brief i scenariusz ponowienia; użytkownik potwierdził zachowanie późniejszych ponowień

### F3 — Przebieg może dostać succeeded mimo niedokończonej oceny

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Phase 2 §1; Phase 3 §2–3; Phase 4 §2; Phase 5 §3
- **Detail**: Nieoczekiwany wyjątek per post był tylko logowany, bez zapisanej oceny FAILED. Reguła succeeded przy braku failed pozwalała więc ogłosić sukces mimo niedokończonej pracy. LlmClient.java:24–27 celowo odróżnia RuntimeException naszego kodu od błędu dostawcy.
- **Fix**: Dispatcher przekazuje błędy techniczne per post w AssessedBatch. RunRecorder zapisuje processing_errors i komunikat error; zakończony przebieg z takim błędem ma partial, a wyjątek przerywający cały krok ma failed. API udostępnia processingErrors i error, panel admina je pokazuje. Błąd techniczny nie tworzy fikcyjnej oceny FAILED ani nie zwiększa assessment_attempts. Kryteria testów pokrywają podsumowanie, kontynuację innych ocen i prezentację w API/UI.
  - Strength: Status i liczniki odzwierciedlają niedokończoną pracę bez błędnej klasyfikacji jako awaria LLM.
  - Tradeoff: Dodatkowy licznik w schemacie, DTO i panelu admina.
  - Confidence: HIGH — wcześniejsze podsumowanie pomijało tę ścieżkę.
  - Blind spot: None significant.
- **Decision**: FIXED — processing_errors, partial, widoczność w API/UI i kryteria weryfikacji

### F4 — Cache schematów nie ma pełnego klucza

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 1, F8e
- **Detail**: Plan określał cache per dostawca, ale ProviderSchemas.forProvider(provider, schema) obsługuje różne schematy dla tego samego providera (ProviderSchemas.java:48–56). ProviderSchemasTest.java:65–77 już korzysta z tego kontraktu. Cache po samym providerze mógłby zwrócić schemat wcześniejszego wywołania.
- **Fix**: Klucz cache (provider, pełny tekst schema); test różnych schematów tego samego providera, także przy wywołaniach równoległych.
- **Decision**: FIXED — jawny klucz provider + schema i kryteria testu współbieżnego

### F5 — Plan pomija konsumentów zmienianych kontraktów

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §1; Phase 2 §4
- **Detail**: Zmiana WykopSearchResult.items() dotyka measurement/WykopGoldSampler.java:88. Konstruktor Assessor jest używany również w GoldRecorder.java:51, LiveSmokeTest.java:63 i Replay.java:33–35. Kod pomiarowy kompiluje się podczas zwykłego buildu, mimo braku uruchamiania pomiarów.
- **Fix**: Dopisać konsumentów i WykopRepositoryTest do faz 1–2. Sampler wyciąga item z par bez zmiany wyników próbkowania. Konstruktor pomocniczy przyjmujący klienta, Provider, model i Clock pozwala zachować Replay offline, bez kluczy i środowiska; produkcja używa LlmProviderProperties, a evaluate nadal odczytuje provenance z nagranego LlmResponse.
- **Decision**: FIXED — konsumenci wymienieni, kontrakt offline Replay określony

### F6 — Brakuje reguły remisów dla ostatniej oceny i przebiegów

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 §1–2; Phase 4 §1–3; Performance Considerations; plan-brief
- **Detail**: Wybór ostatniej oceny opierał się tylko na assessed_at, a lista przebiegów na created_at. Przy równych timestampach wybór próby oraz kolejność stronicowanej listy pozostawały nieokreślone.
- **Fix**: assessed_at DESC, id DESC dla ocen; created_at DESC, id DESC dla przebiegów. Dopasować indeksy oraz IT równych timestampów. id jest jawnym rozstrzygnięciem remisu, nie numerem kolejności wykonania.
- **Decision**: FIXED — reguły remisów, indeksy, kontrakty zapytań, brief i kryteria testów

## Verification

- Przegląd statyczny dokumentów i istniejącego kodu; nie uruchamiano Maven, Vitest, CI ani przebiegów Railway.
- Jeden sub-agent zweryfikował ryzykowne założenia, konsumentów i wzorce. SomeOf/GOAP potwierdzono w zainstalowanym Embabel 1.5.2; dane Assessora/Provenance pozwalają zbudować dispatcher zgodnie z planem.
- Po triage ten sam sub-agent potwierdził spójność sześciu poprawek bez dodatkowych problemów w zmienionych fragmentach.
- Mechanicznie sprawdzono jeden końcowy Progress, zgodność sześciu faz i 26 kryteriów oraz brak checkboxów poza Progress. Identyfikatory, tytuły i checkboxy Progress pozostają bez zmian; 0/26 ukończonych.
- Ustalenia dotyczą planu. SOUND oznacza gotowość planu do implementacji, bez zatwierdzenia przyszłego kodu lub wykonania jego kryteriów.

## Triage Summary

- **Fixed**: F1, F2, F3, F4, F5, F6 (6)
- **Skipped**: none
- **Accepted**: none
- **Dismissed**: none
- **Verdict after fixes**: REVISE → SOUND
