# Egzekwowany kontrakt oceny LLM V1 — Plan Brief

> Full plan: `context/changes/assessment-contract-harness/plan.md`

## What & Why

Roadmapa F-02: kontrakt oceny LLM V1 (`contract-v1.md`) ma być egzekwowany kodem i sprawdzony na prawdziwych modelach, zanim S-02 zbuduje przepływ pobierania, zapisu i widoku. Jeśli żaden model nie trzyma kontraktu (dosłowne cytaty, graf argumentów, stance per target), dowiadujemy się tego tanio — bez pipeline’u, bazy i UI.

## Starting Point

`szczur-radar-api` to szkielet FastAPI z sondami `/api/health` i `/api/ready` oraz pytestem; brak jakiejkolwiek integracji z LLM. Kontrakt V1 jest kompletny i zaakceptowany (enumy, 16 niezmienników, limity, 12 wymaganych przypadków, klasy błędów), ale istnieje tylko jako dokumentacja.

## Desired End State

`app/assessment/` zawiera zamknięte modele wejścia i wyjścia, walidator 16 niezmienników z klasyfikacją błędów i `assess()`, który woła dowolnego skonfigurowanego dostawcę (`provider:model`) i zwraca wynik z pochodzeniem albo jawny błąd. Co najmniej jeden model przechodzi 12 przypadków × 3 uruchomienia i jest domyślnym dla S-02; macierz uruchomiono też na DeepSeek i Gemini, a raport porównawczy (jakość, naruszenia, tokeny, koszt) leży w folderze zmiany. CI odtwarza nagrania prawdziwych odpowiedzi offline.

## Key Decisions Made

| Decyzja | Wybór | Dlaczego |
| --- | --- | --- |
| Dostawca LLM | Wielu dostawców (OpenAI / Gemini / DeepSeek / Claude), model z konfiguracji | Trzeba porównać, który działa najlepiej; nowy dostawca bez zmiany kodu |
| Integracja | Port `AssessmentProvider` + adapter per dostawca na LangChain (`init_chat_model` + `with_structured_output(include_raw=True)`): json_schema dla OpenAI/Claude/Gemini, function calling strict dla DeepSeek | Spójne z LangGraph ze stacku; każdy dostawca oddaje JSON gdzie indziej, więc odczyt jest per adapter |
| Gwarancja struktury | Surowa odpowiedź zawsze przez pełny model Pydantic + walidator | Dostawcy różnią się trybem strict (DeepSeek: tylko JSON) |
| „Przechodzi na modelu” | Walidator + asercje semantyczne z `examples.md`, 3 uruchomienia, wszystkie zaliczone | Wykrywa niestabilność modelu przed S-02 |
| Done przy wielu modelach | ≥1 model przechodzi 12×3 i zostaje domyślnym; macierz obowiązkowo także na DeepSeek i Gemini + raport | Odblokowuje S-02, porównanie zostaje jako dowód |
| Testy live w CI | Opt-in marker `live`; nagrania w repo odtwarzane offline w CI | Brak sekretów, kosztów i flaków blokujących deploy |
| Ponowienia | Brak — tylko transportowe; każde naruszenie to sklasyfikowany błąd | Testy mierzą prawdziwą niezawodność; retry wybierze S-02 na danych |
| Niezmiennik 13 | Obejmuje `mixed`: wymaga ≥1 claimu autora | Cudze opinie nie mogą udawać stanowiska autora (filtry S-04) |
| „Exact substring” | Dosłownie, bez normalizacji; niepusty cytat | Zgodnie z kontraktem; luki idą regułą zmiany §14 |

## Scope

**In scope:**
- Modele kontraktu, walidator, klasy błędów, testy offline (10 przypadków zabronionych + przykłady)
- Port dostawcy na LangChain, prompt V1 z wersją, `assess()` z pochodzeniem
- 12 przypadków z asercjami, przebieg `live` na macierzy modeli, nagrania, test nagrań w CI, skrypt raportu, wybór modelu domyślnego

**Out of scope:**
- Zapis do bazy, pobieranie z Wykopu, mapowanie RAW, UI, trasy API, graf LangGraph, cron (S-02/S-05)
- Ponowienia semantyczne i „naprawianie” outputu
- Sekrety LLM w Railway/CI (F-01); testy live w CI
- Entity resolution, novelty, trend, `is_signal`/`is_valuable`; rozszerzanie kontraktu w kodzie

## Architecture / Approach

`AssessmentInputV1` → `prompt.py` (wersjonowany prompt, wejście jako niezaufane dane) → `AssessmentProvider` (LangChain, `provider:model`) → surowy tekst → parsowanie → pełny model Pydantic z limitami → walidator domenowy → `AssessmentResult` (`ok` + output + pochodzenie | `failed` + klasa + lista naruszeń). Harness testowy: przypadki JSON + predykaty → przebieg `live` zapisuje nagrania → CI odtwarza nagrania → skrypt tworzy raport porównawczy.

## Phases at a Glance

| Faza | Co dostarcza | Główne ryzyko |
| --- | --- | --- |
| 1. Kontrakt jako kod | Modele, walidator 16 niezmienników, klasy błędów, testy offline | Rozjazd enumów/limitów z kontraktem |
| 2. Dostawcy, prompt i `assess()` | Port LangChain, prompt V1, `assess()` z pochodzeniem, testy na sztucznym dostawcy | Różne metody structured output per dostawca |
| 3. Przypadki na prawdziwych modelach i raport | 12×3 na macierzy, nagrania w CI, raport, model domyślny | Żaden model nie przechodzi 12×3 → prompt/§14 |

**Prerequisites:** klucze API do wybranych dostawców w lokalnej powłoce (co najmniej model kandydujący, DeepSeek i Gemini); jedna gałąź `feature/assessment-contract-harness` + jeden PR (lessons.md).
**Estimated effort:** ~3 sesje, po jednej na fazę; faza 3 zależy od liczby iteracji promptu.

## Open Risks & Assumptions

- Część asercji (np. `ambiguous_target` dla „Apple”) może systematycznie nie przechodzić na wszystkich modelach — wtedy reguła §14: zmiana `contract-v1.md` zaakceptowana przez użytkownika w tej zmianie i ponowny przebieg; luka kontraktu blokuje done, żaden przypadek nie jest wyłączany.
- DeepSeek bez strict schema może częściej zwracać `schema_violation` — to wynik do raportu, nie blokada done.
- Koszt na ocenę nie ma jeszcze pułapu (Open Roadmap Question 5); raport go mierzy, decyzja o budżecie zostaje przy S-05.

## Success Criteria (Summary)

- CI zielone: modele, walidator, `assess()` i wszystkie nagrania sprawdzane offline.
- Model domyślny: 36/36 zaliczonych uruchomień; DeepSeek i Gemini uruchomione i opisane w raporcie.
- Użytkownik zatwierdził model domyślny i szacowany koszt na podstawie raportu.
