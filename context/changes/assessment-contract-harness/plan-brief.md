# Kontrakt oceny LLM V2 z walidatorem i pomiarem na wzorcu — Plan Brief

> Full plan: `context/changes/assessment-contract-harness/plan.md`
> Frame brief: `context/changes/assessment-contract-harness/frame.md`
> Research: `context/changes/assessment-contract-harness/research.md`

## What & Why

Roadmapa F-02. Rzeczywisty problem (z briefu ramowego, runda 2): nie źródło danych ani samo ocenianie pojedynczych postów, tylko brak definicji w kontrakcie — co jest celem i od kiedy wzmianka jest stanowiskiem — oraz brak kryterium „wystarczająco dobrej” oceny. Ta zmiana doprowadza kontrakt do wersji V2 opartej na pilotażach, zapisuje go jako kod z walidatorem i mierzy tanie modele względem wzorca z prawdziwych postów, zanim S-02 zbuduje przepływ.

## Starting Point

Kontrakt V1 został przyjęty bez kontaktu z modelem; pilotaże na Wykopie i Reddicie pokazały, że tanie modele przechodzą jego walidację w 95–98%, ale różnią się wyborem celów i progiem stanowiska, a większość odrzuceń cytatów to twarda spacja, nie zmyślenie. W `szczur-radar-api` jest już S-01 (auth, modele, migracje), ale żadnej integracji z LLM. Poprzedni plan (macierz 4 dostawców, 12 wymyślonych przypadków × 3) powstał przed pilotażami i jest zastąpiony.

## Desired End State

`contract-v2.md` jest źródłem prawdy, a PRD i roadmapa są z nim zgodne. `app/assessment/` ma modele, walidator i jednoetapowe `assess()`, które S-02 może wywołać dla każdego posta i zapisać wynik z pochodzeniem. Model domyślny (pierwszy wybór: GPT-6 Luna w trybie flex; zapas: DeepSeek Flash) jest oceniony na ~120 postach oznaczonych przez użytkownika, a podstawowy raport pokazuje jakość i koszt w rozbiciu na kanały dla Luny, DeepSeek Flash i Gemini 3.1 Flash-Lite jako punktu odniesienia. Badanie eskalacji nie blokuje MVP.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Źródło i widok MVP | Wykop; lista postów (zbiorczo później) | Reddit nie ocenia się lepiej, ma tylko mniej szumu; zgodne z PRD | Frame |
| Próg stanowiska | Szeroko + oznaczenie pewności (`hedged`) | Fałszywe stanowisko widać przy surowym poście, pominiętego filtr nie pokaże | Frame |
| Kształt kontraktu | Rdzeń + tezy z jednym cytatem; bez grafu argumentów | Usuwa źródło większości naruszeń, zostawia cytat jako ochronę przed halucynacją | Plan |
| Reguła cytatu | Normalizacja białych znaków, reszta znak w znak | Odrzucenia w pilotażu nie były halucynacjami | Plan |
| Wersja kontraktu | Nowy `contract-v2.md`, V1 do archiwum | Zmiana kształtu ma być jawna | Plan |
| PRD i roadmapa | Poprawione w tej samej zmianie | Dokumenty nie mogą rozjechać się z kontraktem | Plan |
| Kryterium „przechodzi” | Progi na wzorcu: kontrakt 95%, trafność 80%, recall celów 80%, precision celów 70%, kierunek na dopasowanych celach ze stanowiskiem 75%, dominujący kierunek posta 75%, sprzeczności ≤3% | Walidacja formy i kierunek całego posta nie wykrywają pominiętych celów | Plan / triage F1 |
| Wzorzec | ~120 świeżych postów z dwóch dni, instrukcja v1.0 | Niezależny od postów, z których powstały reguły | Plan |
| Integracja | LangChain (Gemini, DeepSeek, OpenAI) + kontrola na żywo | Wybór użytkownika; ścieżka niesprawdzona w pilotażach, stąd wczesna kontrola | Plan |
| Modele | GPT-6 Luna (flex) — pierwszy wybór; DeepSeek Flash — zapas; Gemini 3.1 Flash-Lite — punkt odniesienia | Luna: czyste cytaty i ~$3/mies. we flex przy nocnym przebiegu; DeepSeek najtańszy i najszybszy; Gemini najbardziej powtarzalny, ale po korekcie cen najdroższy | Decyzja użytkownika 2026-10-01 (zastępuje triage F2) |
| Eskalacja | Pomiar i przełącznik w `assess()` odłożone po MVP | Rozstrzygnięcie kaskady nie blokuje pierwszego przepływu oceniania | Triage F2 |
| Sygnały niepewności | Jev, Span-01 i niezgoda modeli odłożone po MVP | Pilotaże są zachowane jako dowód, ale nie są bramką F-02 | Triage F2 |
| Wpisy bez treści | Odsiew w `assess()`, bez wywołania modelu | ~8% wywołań mniej, jedna definicja w kontrakcie | Plan |
| Sposób wywołania | Pojedyncze posty, skrócony prompt; ponowienia wyłącznie transportowe (429, 5xx, timeout) | Paczkowanie zmienia co trzecią ocenę (pilot 3); flex odmawia przez 429 | Frame / Plan |
| Flex i batch | Luna w trybie flex; `assess()` dzielony na przygotowanie zapytania i ocenę odpowiedzi; Batch API poza F-02 | Flex daje cenę batcha bez przebudowy przepływu; podział pozwala dodać batch w S-02 bez zmiany walidacji | Plan |
| Dane pilotaży | Cały `pilot/` w repo | Pełny ślad dowodów | Plan |

## Scope

**In scope:**
- `contract-v2.md`, archiwizacja V1, przykłady, wytyczne, PRD, roadmapa z grafiką
- Modele, walidator z normalizacją cytatów, odsiew, klasy błędów, testy offline
- Adaptery LangChain dla Gemini, DeepSeek i OpenAI (z trybem flex i opcjonalnym OpenRouter), prompt V2, jednoetapowe `assess()` z pochodzeniem, podzielone na przygotowanie zapytania i ocenę odpowiedzi
- Zestaw wzorcowy (próbka, arkusz, etykiety, miary), przebieg na żywo, nagrania w CI, raport, model domyślny

**Out of scope:**
- Zapis do bazy, pobieranie w pipeline, UI, trasy API, LangGraph, harmonogram (S-02/S-05)
- Reddit, komentarze, widok zbiorczy, rozpoznawanie spółek, graf argumentów
- Paczkowanie, Batch API, model pomocniczy przed LLM, Gemini 3.8 Flash, Jev/Span-01 i analiza oraz wdrożenie eskalacji
- Odłożone po MVP: embeddingi (widok zbiorczy, dopasowanie slangu), klasyfikator uczony na zapisanych ocenach, ekstraktor celów typu GLiNER, klasyfikatory „finansowe”
- Sekrety LLM w Railway/CI, testy na żywych modelach w CI

## Architecture / Approach

`AssessmentInputV1` → odsiew (wpis bez treści → gotowy wynik bez modelu) → prompt V2 → `AssessmentProvider` (LangChain, `provider:model`) → surowa odpowiedź → pełny model Pydantic → walidator (cytaty po normalizacji białych znaków, referencje, reguły stanowiska) → `AssessmentResult` (`ok` + wynik + pochodzenie | `failed` + klasa + naruszenia). Pomiar: wzorzec → jeden przebieg `live` zapisuje nagrania Luny (flex), DeepSeek Flash i Gemini 3.1 Flash-Lite → CI odtwarza nagrania i sprawdza progi → skrypt tworzy podstawowy raport jakości i kosztu.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Kontrakt V2 i dokumenty | `contract-v2.md`, V1 w archiwum, spójne PRD/roadmapa, `pilot/` w repo | Definicje nadal niejednoznaczne na nowych postach |
| 2. Kontrakt jako kod | Modele, walidator, odsiew, testy offline | Rozjazd kodu z tekstem kontraktu |
| 3. Dostawcy, prompt i `assess()` | Adaptery LangChain, prompt V2, jednoetapowe `assess()` | LangChain zachowuje się inaczej niż wywołania z pilotaży |
| 4. Zestaw wzorcowy | ~120 oznaczonych postów, miary | Czas użytkownika; nowe reguły z notatek cofają do faz 1–2 |
| 5. Pomiar, raport i model domyślny | Jeden przebieg trzech modeli, nagrania w CI, raport z kosztami per kanał, model domyślny | Ani Luna, ani DeepSeek nie spełnia progów; odmowy flex wydłużają przebieg |

**Prerequisites:** lokalne klucze API (Gemini, DeepSeek, OpenAI lub OpenRouter, Wykop); czas użytkownika na akceptację V2 i oznaczenie ~120 postów; gałąź `feature/assessment-contract-harness` (obecne pliki zmiany leżą niezacommitowane na `chore/archive-s01`).
**Estimated effort:** ~5–6 sesji w 5 fazach; arkusz z fazy 4 można przygotować po akceptacji kontraktu w fazie 1, równolegle z fazą 2. Pobranie i przygotowanie próbki wymaga modeli wejścia i odsiewu z fazy 2; oznaczanie może iść równolegle z fazą 3.

## Open Risks & Assumptions

- Plan V2 przeszedł głęboki `/10x-plan-review` i triage 2026-09-30: F1–F5 poprawione, werdykt po poprawkach SOUND. Poprzedni `reviews/plan-review.md` nadal dotyczy wersji 1 planu.
- Progi kontraktu/trafności/dominującego kierunku/sprzeczności (95 / 80 / 75 / 3) oparto na zgodności między modelami i etykietach rundy 1. Progi recall/precision celów i kierunku na dopasowanych celach (80 / 70 / 75) są decyzją akceptacyjną z triage F1, nie pomiarem z pilotażu; względem wzorca według instrukcji v1.0 wyniki mogą być inne. Przy ~120 postach jeden post waży ~1 punkt procentowy w miarach per-post, a pojedynczy cel może ważyć więcej.
- LangChain dla DeepSeek wymaga wyłączenia myślenia i nie gwarantuje pól wymaganych; jeśli kontrola w fazie 3 nie przejdzie, potrzebna będzie decyzja o innej warstwie integracji dla tego dostawcy.
- Ceny modeli i dostępność nazw (`gemini-3.1-flash-lite`, `deepseek-flash`, `gpt-6-luna`, dostawca `openai/flex`) sprawdzono 2026-09-29/30 przez API; rzeczywisty koszt zostanie zapisany w raporcie.
- Zmiany z 2026-10-01 (role modeli, flex, ponowienia, podział `assess()`) przeszły przegląd `reviews/plan-review-2026-10-01.md`: F1–F5 poprawione, werdykt SOUND. Ponowienia tylko w adapterze (do 3 prób), po 3 odmowach flex powrót do trybu standardowego oznaczony w pochodzeniu (`flex_fallback`), konfiguracja wywołania zapisywana przy każdej ocenie. Zachowanie flex dla GPT-6 Luna (odsetek odmów, czas) nie zostało jeszcze zmierzone.
- Usunięcie grafu argumentów zmienia opis S-03 w roadmapie i zdanie w PRD — wymaga potwierdzenia użytkownika w fazie 1.
- Zaakceptowany wzorzec pochodzi od jednego oznaczającego.

## Success Criteria (Summary)

- Użytkownik zaakceptował kontrakt V2, a PRD i roadmapa mówią to samo co kontrakt.
- Wybrany model (Luna flex, a jeśli nie spełni progów — DeepSeek Flash) spełnia progi na wzorcu, a CI sprawdza to offline na nagraniach prawdziwych odpowiedzi.
- Raport daje liczby do decyzji o modelu domyślnym i jego koszcie miesięcznym w wybranym kanale; analiza eskalacji jest odłożona po MVP.
