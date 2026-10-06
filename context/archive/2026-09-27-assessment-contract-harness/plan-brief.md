# Kontrakt oceny LLM V2 z walidatorem i pomiarem na wzorcu — Plan Brief

> Full plan: `context/changes/assessment-contract-harness/plan.md`
> Frame brief: `context/changes/assessment-contract-harness/frame.md`
> Research: `context/changes/assessment-contract-harness/research.md` (follow-up 2026-10-03: Java/Embabel)

## What & Why

Roadmapa F-02. Rzeczywisty problem (frame, runda 2): brak definicji w kontrakcie — co jest celem i od kiedy wzmianka jest stanowiskiem — oraz brak kryterium „wystarczająco dobrej” oceny. Kontrakt V2 to rozwiązał (faza 1, zrobiona); ta wersja planu zapisuje go jako kod w Javie po F-03 i mierzy trzy modele na wzorcu z prawdziwych postów, zanim S-02 zbuduje przepływ.

## Starting Point

Backend jest w Javie (Spring Boot 4, Embabel 1.5.2, Spring AI 2.0.1) z jednym dostawcą LLM na uruchomienie (`LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_API_KEY`) i szkieletem agenta bez wywołań LLM. Badanie pokazało, że `createObject` Embabel parsuje pobłażliwie, ponawia także po błędzie parsowania i gubi tokeny cache, a ponowienia działają w kilku warstwach. Implementacja Pythonowa faz 2–4 (gałąź `_old`) nie jest przenoszona.

## Desired End State

`AssessorAgent` (Embabel) ocenia post jednym wywołaniem przez własnych klientów Spring AI, a ścisły parser i walidator D1–D15 odrzucają każdą niezgodną odpowiedź z jawną klasą błędu. Model domyślny (Luna flex albo DeepSeek Flash) spełnia progi na ~120 postach oznaczonych przez użytkownika, CI sprawdza to offline na nagraniach, a raport podaje jakość i koszt trzech modeli.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Wywołanie modelu | Własny Spring AI `ChatModel` per dostawca, ponowienia frameworka i SDK wyłączone | Surowy tekst, schemat per dostawca, `extraBody` dla flex i dokładnie 3 próby transportowe | Plan (research F/E) |
| Rola Embabel | `AssessorAgent` jako cienka powłoka nad serwisem `Assessor` | S-02 dostaje krok agenta, logika testowalna bez Springa | Plan |
| Pomiar trzech modeli | Trzy przebiegi z różnymi `LLM_*`, nagrania się sumują | Bez nowych zmiennych kluczy; nagrywa ścieżkę produkcyjną | Plan |
| Klucze do przebiegów | Jednorazowe klucze od użytkownika, usuwane po nagraniu | Nigdy w repo, CI ani Railway | Plan |
| Luna | Pierwszy wybór, flex przez OpenRouter (`provider.only`), powrót do standardu po 3× 429 | Decyzja 2026-10-01; cena flex ~$3/mies. | Plan (frame, pilot 8) |
| Ustawienia kanału | Opcjonalne `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT`, `LLM_TIMEOUT` (enumy, `preserve()` w Railway) | Flex bez drugiej ścieżki konfiguracji | Plan |
| Białe znaki §10 | Jawna lista znaków w kontrakcie + klasa `Whitespace` | Neutralne językowo; `StringUtils`/`isWhitespace` pomijają U+2007, U+202F | Plan |
| Kształt wyjścia | Jeden plik JSON Schema V2 (walidacja networknt + schemat dla dostawców) i rekordy z testem zgodności | Ścisłe odrzucanie obcych/brakujących kluczy, limity w code pointach | Plan |
| Walidator | Wzorzec strategii: reguła na klasę, uporządkowany zestaw `ContractV2Rules`, tabela §7.2 jako enum | Nowa reguła = nowa klasa w zestawie, bez zmian walidatora | Plan |
| DeepSeek | JSON mode + schemat w prompcie, myślenie wyłączone | Jedyny tryb JSON w Spring AI; najlepsza zgodność w smoke | Plan |
| Pakiety | `llm`, `agents.assessor`, `sources.wykop`; narzędzia w `src/test/.../measurement` z README; `CLAUDE.md` w `llm` i `assessor` | Produkt oddzielony od narzędzi; lekcja o warstwach | Plan |
| Przebiegi na żywo | Profil Maven `measurement` + tag JUnit, wyłączone w CI | CI nigdy nie wywołuje LLM | Plan |
| Testy klientów | Lokalny `HttpServer` z prawdziwymi payloadami | Testuje prawdziwy kod Spring AI/SDK i liczbę żądań | Plan |
| Próbka Wykopu | Narzędzie testowe w Javie z produkcyjnym mapperem i odsiewem | Jedna definicja „wpisu bez treści” | Plan |
| Progi, wzorzec, role modeli, brak eskalacji/paczkowania | Bez zmian względem wersji 2 | Decyzje użytkownika i triage z 2026-09-30/10-01 | Frame / Plan v2 |

## Scope

**In scope:** poprawka §10 i dokumentów; kontrakt jako kod, parser, walidator, odsiew; klienci trzech dostawców; prompt, `Assessor`, `AssessorAgent`; kontrola na żywo; mapper Wykopu, próbka, arkusz, miary; nagrania, test progów w CI, raport, wybór modelu, runbook produkcji.

**Out of scope:** zapis do bazy, pipeline, UI, trasy API, harmonogram (S-02+); `createObject` Embabel; przenoszenie kodu Pythona; paczkowanie, Batch API, eskalacja; bezpośrednie API OpenAI; wywołania LLM w CI; ustawienie modelu na produkcji (krok po merge).

## Architecture / Approach

`AssessmentInputV1` → `Prefilter` (bez treści → wynik `prefilter`) → prompt V2 → `LlmClient` (Gemini | DeepSeek | OpenRouter, z `LLM_*`) → surowy tekst → `OutputParser` (JSON + schemat V2) → `RuleBasedValidator` (strategie D1–D15) → `AssessmentResult` z pochodzeniem; `AssessorAgent` opakowuje `Assessor` dla Embabel. Pomiar: `WykopGoldSampler` → oznaczanie → 3× `GoldRecorder` → `RecordingsReplayTest` w CI + `GoldReport`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Kontrakt V2 i dokumenty (zrobione) | `contract-v2.md`, PRD, roadmapa | — |
| 2. Kontrakt jako kod | Rekordy, schemat, parser, walidator, odsiew, poprawka §10 | Rozjazd schematu/rekordów z kontraktem |
| 3. Infrastruktura LLM (3A) | Klienci trzech dostawców, ponowienia, flex, testy HTTP | Ukryte ponowienia/timeouty SDK, cache DeepSeek |
| 4. Prompt, Assessor, agent (3B) | Prompt, `Assessor`, `AssessorAgent`, kontrola na żywo | Zgodność <90% u któregoś dostawcy |
| 5. Zestaw wzorcowy | Mapper Wykopu, próbka, etykiety, miary | Czas użytkownika; nowe reguły cofają do fazy 2 |
| 6. Pomiar i model domyślny | 3 przebiegi, test progów w CI, raport, runbook | Ani Luna, ani DeepSeek nie spełnia progów |

**Prerequisites:** jednorazowe klucze Gemini, DeepSeek, OpenRouter (fazy 3, 4, 6) i Wykop (faza 5) od użytkownika; Docker lokalnie dla `./mvnw verify`; czas użytkownika na oznaczenie ~120 postów.
**Estimated effort:** ~6–8 sesji w 5 fazach do wykonania; oznaczanie może iść równolegle z fazą 4.

## Open Risks & Assumptions

- Nazwa modelu DeepSeek Flash (`deepseek-flash` vs `deepseek-v4-flash`) i rejestracja `gemini-3.1-flash-lite` w Embabel — do potwierdzenia w kontroli na żywo.
- Mapowanie tokenów cache DeepSeek przez Spring AI niepewne — plan przewiduje odczyt surowego `usage`.
- Przekazywanie `service_tier` przez OpenRouter niezweryfikowane — flex idzie przez `provider.only`, sprawdzone w pilocie 8.
- Progi recall/precision/kierunku (80/70/75) to decyzja akceptacyjna, nie pomiar z pilotażu; wzorzec od jednego oznaczającego.
- Zmiana §10 jest redakcyjna (ten sam zbiór znaków) — wymaga akceptacji użytkownika.

## Success Criteria (Summary)

- Ścisły kontrakt jako kod: każda niezgodna odpowiedź jest odrzucana z jawną klasą; CI zielone bez wywołań LLM.
- Model domyślny spełnia progi na wzorcu, sprawdzane w CI na nagraniach prawdziwych odpowiedzi.
- Raport daje liczby do decyzji o modelu i koszcie; runbook pozwala ustawić model na produkcji po merge.
