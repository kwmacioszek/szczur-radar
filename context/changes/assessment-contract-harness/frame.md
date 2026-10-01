# Frame Brief: Kontrakt oceny LLM V1 przed harnessem F-02

> Etap ustalania ram przed /10x-plan. Ten dokument rejestruje, co jest *faktycznie*
> przedmiotem problemu, oddzielone od tego, co początkowo założono.
>
> **Struktura pliku:** poniżej najpierw **Runda 2 (2026-09-30)** — aktualny brief, który zastępuje wniosek rundy 1 jako wejście do /10x-plan. Dalej, bez zmian, **Runda 1 (2026-09-29)** oraz dziennik pilotaży, na których opiera się runda 2.

# Runda 2 (2026-09-30): czy ocena pojedynczego posta to właściwe zadanie i którego źródła dotyczy MVP

## Zgłoszona obserwacja

Na prawdziwych postach odpowiedzi tanich modeli przechodzą walidację kontraktu w 74–98%, ale dwa modele zgadzają się ze sobą w „rdzeniu” oceny (trafność + zbiór celów ze stanowiskiem + kierunek) tylko w 48–49% postów, tak samo na Wykopie i na Reddicie; z etykietami użytkownika (runda 1, bez wytycznych) zgadzają się w 25–41%, a użytkownik sam uznał 28% postów z #gielda za nierozstrzygalne. Kierunek stanowiska wobec wspólnego celu zgadza się w 82–85%.

## Początkowe ujęcie (zachowane)

- **Podana przez użytkownika przyczyna lub podejście**: „może się okazać, że Wykop to zły pomysł jako źródło danych (wtedy byłby po MVP Wykop)”; „może trzeba … przemyśleć jednak jeszcze raz analizę postów przez AI”.
- **Proponowany przez użytkownika kierunek**: rozważyć Reddit jako źródło MVP i wrócić do pytania, czy ocena każdego posta osobno według kontraktu V1 to właściwe zadanie.
- **Zawężenie przed delegowaniem**: główna obawa — „nie jestem pewien”; przy czym użytkownik zatrzymuje się, czytając #gielda — tezy i transakcje, informacje **oraz** nastrój tagu (wszystkie trzy); wątpliwość co do źródła — „Reddit wygląda lepiej” (nie: „Wykop jest zły”).

## Mapa wymiarów

Obserwacja może pochodzić z każdego z tych wymiarów:

1. **Materiał źródłowy** — wpisy z Wykopu są zbyt krótkie/memiczne, by je oceniać. ← początkowe ujęcie
2. **Definicja zadania** — kontrakt nie określa, co jest celem ani od kiedy wzmianka jest stanowiskiem.
3. **Jednostka analizy** — wartość powstaje zbiorczo (nastrój tagu), a kontrakt i PRD oceniają każdy post osobno.
4. **Artefakt pomiaru** — miara „rdzenia” wymaga identycznego zbioru celów i zaniża zgodność użyteczną w praktyce.
5. **Koncentracja wartości** — niezgoda siedzi w szumie, a na postach treściwych modele są zgodne.

## Badanie hipotez

Bez nowych wywołań modeli; przeliczenie wyników pilotów 2 i 5 (`pilot/scripts/frame2_checks.py`), para gemini-3.1-flash-lite vs deepseek-flash.

| Hipoteza | Dowody | Werdykt |
| --- | --- | --- |
| 4. Artefakt pomiaru | Dominujący kierunek posta zgodny w 78% (Wykop, n=143) i 74% (Reddit, n=243); gdy oba modele widzą stanowisko — wspólny cel w 88% / 93% postów, wspólny cel z tym samym kierunkiem w 75% / 83%; dominujący kierunek wprost sprzeczny (bullish ↔ bearish): 0 / 5 postów. Względem etykiet użytkownika: sprzeczny kierunek w 1 z 69 postów; trafność binarna (bez jego „nierozstrzygalnych”) 73–82%. | SILNE |
| 2. Definicja zadania | Pozostała niezgoda to „czy post wyraża stanowisko i wobec czego” (zgodność 78% / 83%). Użytkownik widzi kierunek w 10–11 z 69 postów, modele w 17–30 (w 11–23 z nich użytkownik nie oznaczył żadnego). Tag kanału `#gielda` jest u obu modeli najczęstszym „celem” (15 i 19 wystąpień). Spójne z rundą 1 (≥15 niedookreślonych reguł) i instrukcją v0.2. | SILNE |
| 1. Materiał źródłowy | Wykop: zgodność trafności 76% (Reddit 100%), posty z tezą/transakcją autora wg obu modeli 24% (Reddit 48%). Ale zgodność dominującego kierunku taka sama (78% vs 74%) — Reddit nie jest czytelniejszy w ocenie stanowiska, ma tylko mniej szumu; ma też ~45 postów dziennie i treść po angielsku. | ŚREDNIE (tylko odsiew szumu) |
| 3. Jednostka analizy | Obraz zbiorczy odporny na różnice modeli w tematach: wspólne 9/10 najczęstszych celów dnia (Wykop) i 7/10 (Reddit); słabszy w bilansie byczy/niedźwiedzi per cel: 5/5 (Wykop, mała próbka), 20/32 (Reddit). PRD umieszcza trend i sygnał zbiorczy poza MVP (`prd.md:228`, `:232`, `:289`). | ŚREDNIE |
| 5. Koncentracja wartości | Zgodność kierunku na postach treściwych vs pozostałych: 83% vs 77% (Wykop), 73% vs 76% (Reddit) — brak różnicy. | BRAK |

## Sygnały zawężające

- Trzy rozstrzygające pytania o pozycję produktową — który błąd jest gorszy (fałszywe vs pominięte stanowisko), na jakie pytanie odpowiada pierwszy ekran (co przeczytać vs o czym się mówi), czego dotyczy MVP (puls polskiego rynku vs czytelne analizy) — użytkownik zamknął odpowiedzią **„nie jestem pewien”** (3/3). Kryterium „wystarczająco dobrej” oceny nie istnieje i nie wynika z dotychczasowych danych.
- PRD nie definiuje jakości oceny jako kryterium sukcesu: Primary Success Criteria to dostarczenie przepływu, listy z filtrem i widoku szczegółowego z surowym postem obok (`prd.md:36-37`); ocenę wartości w MVP „robi użytkownik na podstawie opisanej struktury” (`prd.md:258`); jedyny guardrail jakości to brak halucynacji (`prd.md:48`), który pilnuje walidator cytatów.
- Roadmapa sama wskazuje moment weryfikacji hipotezy produktu: S-02 to „pierwszy moment, w którym widać, czy ocena LLM faktycznie oddziela sygnał od szumu” (`roadmap.md:38`).
- Koszt i wykonalność techniczna przestały być ryzykiem: tanie modele ~$0,5–8/mies., sprzeczne oceny kierunku na poziomie 0–2% postów.

## Konwencja między systemami

Przy ekstrakcji opinii z mediów społecznościowych zgodność anotatorów-ludzi rzędu ~70% jest typowa (arXiv 2212.11311, runda 1), więc 74–78% zgodności kierunku między modelami mieści się w normie dla tej klasy zadań, a nie wskazuje na wadę źródła. Schematy ekstrakcji stabilizuje się iteracyjnie na danych, a o przydatności rozstrzyga użycie wyniku przez człowieka — zgodnie z tym, jak PRD definiuje rolę oceny w MVP (pomoc w przeglądaniu, weryfikowana przez użytkownika na surowym poście).

## Przeformułowany (lub potwierdzony) opis problemu

> **Rzeczywisty problem, wokół którego należy planować, to**: nie źródło danych ani samo ocenianie pojedynczych postów, tylko brak dwóch definicji — (1) w kontrakcie: co jest celem i od kiedy wzmianka jest stanowiskiem; (2) w produkcie: co znaczy „wystarczająco dobra” ocena, czyli na jakie pytanie użytkownika aplikacja ma odpowiadać — przy czym tej drugiej nie da się dziś rozstrzygnąć kolejnymi pomiarami, bo użytkownik nie ma jeszcze stanowiska.

Początkowe ujęcie („Wykop to zły materiał”) potwierdza się tylko częściowo: Wykop wymaga mocniejszego odsiewu szumu, ale ocena kierunku jest na nim tak samo stabilna jak na Reddicie. Hipoteza „ocena pojedynczego posta jest niewykonalna” nie znajduje potwierdzenia: alarmujące 48% było skutkiem ostrej miary, a realna niezgoda dotyczy wyboru celów i progu stanowiska — rzeczy definiowalnych regułami. Pytanie o źródło MVP i o widok zbiorczy pozostaje decyzją produktową (PRD: Wykop = FR-001 must-have, Reddit = FR-023 nice-to-have, trend = FR-024/025 poza MVP), której dowody nie wymuszają.

## Decyzje użytkownika po ramowaniu (2026-09-30)

Po przedstawieniu briefu użytkownik rozstrzygnął trzy pytania, na które wcześniej odpowiedział „nie jestem pewien” (każdorazowo wybierając opcję rekomendowaną, po przykładach z własnych danych):

1. **Próg stanowiska: szeroko + oznaczenie pewności.** Model przypisuje kierunek, gdy rozsądny czytelnik odczyta go z tekstu — także z sugestii („bańka na X”, „X kusi”); słabe przypadki oznaczane jako niepewne (`hedged`). Uzasadnienie: surowy post stoi obok oceny, więc fałszywe stanowisko weryfikuje się jednym spojrzeniem, a pominiętego filtr nie pokaże wcale. Konsekwencja: etykiety rundy 1 (użytkownik ostrożniejszy niż modele) nie są wzorcem dla tego progu.
2. **Pierwszy ekran MVP: lista postów; widok zbiorczy później.** Zgodnie z PRD (`prd.md:37`). Widok „o czym dziś się mówi” jako następny krok, liczony z tych samych zapisanych ocen — bez zmiany kontraktu.
3. **Źródło MVP: Wykop; Reddit jako drugie.** Bez zmiany PRD (FR-001 must-have, FR-023 nice-to-have). Wykop wymaga mocniejszego odsiewu szumu (wpisy bez tekstu, memy, żale — instrukcja v0.2, reguły 2.1–2.3).

Skutek: z dwóch brakujących definicji druga (produktowa) jest rozstrzygnięta na poziomie potrzebnym do planowania; zostaje pierwsza — definicja celu i progu stanowiska w kontrakcie.

## Pewność

**WYSOKA** (po decyzjach użytkownika; przed nimi ŚREDNIA) — dowody liczbowe dla wymiarów 4 i 2 są silne i zgodne z konwencją, a pozycja produktowa, która była nierozstrzygnięta („nie jestem pewien” ×3), została ustalona. Zastrzeżenie: zgodność mierzono między modelami i z jednym oznaczającym w rundzie bez wytycznych; żaden pomiar nie weryfikuje jeszcze modeli względem wzorca oznaczonego według przyjętego progu.

## Co zmienia się dla /10x-plan

Plan F-02 powinien dotyczyć doprecyzowania w kontrakcie definicji celu i progu stanowiska (materiał: `pilot/labeling-guidelines.md`, procedura §14) dla Wykopu jako źródła MVP i listy postów jako widoku MVP — a nie zmiany źródła danych ani rezygnacji z oceny pojedynczych postów. Widok zbiorczy i Reddit pozostają poza MVP zgodnie z PRD; pozostałe wskazówki z rundy 1 („Uzupełnienie po pilotach”) pozostają aktualne.

## Odniesienia (runda 2)

- Pomiary: `pilot/scripts/frame2_checks.py` na `pilot/runs/pilot2-results.json`, `pilot/runs/pilot5-reddit-*.json`, `pilot/labels-2026-09-28.json`
- PRD: `context/foundation/prd.md:36-37`, `:48`, `:228`, `:232`, `:258`, `:289`; roadmapa: `context/foundation/roadmap.md:38`
- Dziennik pilotaży i runda 1: sekcje poniżej; instrukcja oznaczania: `pilot/labeling-guidelines.md`
- Zadania badawcze: przeliczenia wykonane w głównej sesji (bez pod-agentów — dowody liczbowe z istniejących przebiegów)

---

# Runda 1 (2026-09-29): kontrakt V1 przed harnessem — zachowana bez zmian

## Zgłoszona obserwacja

Plan `assessment-contract-harness` (status `plan_reviewed`, przegląd: SOUND) budzi wątpliwość użytkownika przed implementacją — w trzech obszarach: sam kontrakt input/output LLM, założenie 3–4 dostawców, oraz LangGraph, którego plan nie dotyka.

## Początkowe ujęcie (zachowane)

- **Podana przez użytkownika przyczyna lub podejście**: „może wystarczy 2 dostawców (Gemini + DeepSeek)”; LangGraph trzeba „dobrze sprawdzić”.
- **Proponowany przez użytkownika kierunek**: przeramować F-02 przed `/10x-implement` — zmniejszyć macierz dostawców i włączyć weryfikację LangGraph.
- **Zawężenie przed delegowaniem**: główna obawa to **kontrakt**; niepokój o LangGraph — „nie jestem pewien”, czy dotyczy F-02, czy ścieżki do S-02; stan wiedzy o tym, czy jakiś model trzyma kontrakt V1 — „nic nie sprawdzałem”.

## Mapa wymiarów

Obawa o kontrakt może pochodzić z każdego z tych wymiarów:

1. **Wykonalność dla modelu** — jedno wywołanie nie utrzyma grafu ID, dosłownych cytatów i 16 niezmienników.
2. **Niedookreślenie kontraktu** — kontrakt (łącznie z przykładami) nie jest wykonywalną specyfikacją; harness zakodowałby zgadywanie.
3. **Kontrakt vs potrzeby MVP** — kontrakt opisuje więcej, niż MVP konsumuje; F-02 dowodziłby niepotrzebnych zdolności.
4. **Definicja „przechodzi” i macierz dostawców** — 36/36 × ≥3 modele zamienia sprawdzenie kontraktu w porównanie modeli. ← początkowe ujęcie

## Badanie hipotez

| Hipoteza | Dowody | Werdykt |
| --- | --- | --- |
| 1. Wykonalność dla modelu | Brak pomiaru dla tego kontraktu i języka polskiego. Zewnętrznie: dosłowne cytaty 64–98% zależnie od badania (arXiv 2609.15964; quote-provenance-eval), jednoprzebiegowa ekstrakcja grafów argumentów traci spójność globalną (arXiv 2605.13793), stance wobec celu w postach społecznościowych ~70–85% zgodności (arXiv 2212.11311; univr target-based FSA). | SŁABE (nierozstrzygalne bez kontaktu z modelem) |
| 2. Niedookreślenie kontraktu | Jedyny pełny przykład normatywny łamie niezmiennik 8: body `contract-v1.md:43` to jedno zdanie, a cytaty `:129`, `:142` go nie zawierają; statements `:125`, `:138` dopisują fakty wbrew `:340`. 7 z 11 przykładów w `examples.md` nie ma wyjściowego JSON (§3–§7, §9, §10). ≥15 reguł do rozstrzygnięcia przez autora walidatora (m.in. „rozstrzygnięty stance” `:282`/`:433`; `claim_ids` przy `mixed`/`unclear` `:434`; „może” `:233` vs „musi” `:430` dla anchora contextual; anchor dla `unresolved`/`ambiguous`; pusty cytat; normalizacja i jednostka „chars” `:427`, `:447-449`; `extraction_limit` vs limity schematu `:454`). Plan już łata 4 z nich w kodzie (`plan.md:56`, `:58`), wbrew §14 (`contract-v1.md:518-528`). Kontrakt przyjęto w tym samym PR co plan (#11, `ef4041b`), bez kontaktu z modelem. | SILNE |
| 3. Kontrakt vs potrzeby MVP | Żadne FR must-have nie konsumuje `modifiers`, `assertiveness`, `reference`, `attribution`, `flags`; `comment`/parent jawnie odłożone (`prd.md:285`, `roadmap.md:237`). FR-011a używa tylko anchora, rodzaju celu i kierunku stance (`prd.md:124`). Claims/cytaty/graf uzasadnia tylko opis S-03 (`roadmap.md:139`), który sam nazywa to „bogatą strukturą” (`roadmap.md:147`), oraz opis oceny w `prd.md:258`. | ŚREDNIE |
| 4. Dostawcy / definicja done | Macierz 4 dostawców pochodzi z `change.md:12` i planu, nie z PRD/roadmapy (roadmapa: „na prawdziwym modelu”, `roadmap.md:47`, `:97`); wybór dostawcy i budżet to otwarte pytania użytkownika (`roadmap.md:105`, `:222`). Zmniejszenie macierzy nie dotyka wymiarów 2–3. | ŚREDNIE (wtórne) |

## Sygnały zawężające

- Użytkownik traktował „V1 accepted” jako **dobry szkic**, który kontakt z modelem i danymi ma doszlifować — nie jako zamrożoną specyfikację. Plan F-02 traktuje go jako zamrożony (luki idą przez §14 jako wyjątek, `plan.md:248`).
- Rdzeń wartości MVP według użytkownika: **trafność + cele (anchor, rodzaj) + stance per target**. Tezy z cytatami, graf argumentów i niuanse (assertiveness, attribution, modifiers, flags, reference) nie zostały wskazane jako niezbędne.
- Prawdziwe posty z Wykopu #gielda **można pobrać** — pilot na realnych danych jest wykonalny.
- Kontrprzesłanka z presji: w V1 rdzeń nie jest oddzielalny — niezmienniki 16 (`contract-v1.md:435`), 13 (`:433`) i 9 (`:428`) wiążą stance `bullish|bearish|neutral` z claimem autora z dosłownym cytatem z `item`. Rdzeń MVP dziedziczy więc całe ryzyko cytatów i grafu ID.
- Rozbieżność z PRD/roadmapą (do zgłoszenia, nie do rozstrzygnięcia tutaj): S-02 obiecuje „streszczenie głównej tezy” (`roadmap.md:124`), S-03 tezy i argumenty z cytatami (`roadmap.md:139`), PRD opisuje ocenę z claimami i cytatami (`prd.md:258`) — szerzej niż rdzeń wskazany przez użytkownika.

## Konwencja między systemami

W praktyce projektowania schematów ekstrakcji i wytycznych anotacji schemat stabilizuje się po rundzie pilotażowej na kilkunastu prawdziwych próbkach, która dodaje, łączy lub usuwa kategorie (arXiv 2605.20809; DiZiNER arXiv 2604.15866; MLR v225 goel23a — dowody umiarkowane, bez miary skali zmian). Wiodąca hipoteza jest zgodna z tą konwencją: kontrakt V1 nie przeszedł jeszcze tej rundy.

## Przeformułowany (lub potwierdzony) opis problemu

> **Rzeczywisty problem, wokół którego należy planować, to**: kontrakt V1 jest szkicem, który nigdy nie zetknął się z modelem ani z prawdziwym postem z Wykopu, jest niedookreślony (łącznie z błędnym przykładem normatywnym), a jego rdzeń ważny dla MVP (trafność, cele, stance) jest związany niezmiennikami z bogatszą strukturą — tymczasem plan F-02 traktuje go jako zamrożoną specyfikację i buduje na nim harness 12×3 oraz porównanie dostawców.

Liczba dostawców i miejsce LangGraph są realnymi pytaniami, ale nie są źródłem obawy: przy obecnym kontrakcie każda macierz modeli mierzyłaby zgodność z niedookreśloną specyfikacją, a asercje i walidator utrwaliłyby w kodzie decyzje, które kontrakt pozostawia otwarte. Gdyby kontrakt był zweryfikowany na realnych danych i doprecyzowany tam, gdzie dziś jest cichy, harness i wybór modelu mierzyłyby coś stabilnego.

## Pewność

**WYSOKA** — silne dowody wewnętrzne (zweryfikowany błąd przykładu `contract-v1.md:43` vs `:129`/`:142`, lista niedookreśleń), zgodność z konwencją pilotażu schematów oraz rozstrzygający sygnał zawężający (kontrakt = „dobry szkic”, nic nie sprawdzono). Otwarta pozostaje wyłącznie wykonalność dla modeli (wymiar 1), której nie da się rozstrzygnąć bez kontaktu z modelem.

## Co zmienia się dla /10x-plan

Plan powinien dotyczyć najpierw doprowadzenia kontraktu V1 od szkicu do wykonywalnej specyfikacji na realnych postach — w tym decyzji, czy rdzeń MVP (trafność, cele, stance) ma pozostać związany z claimami i cytatami, co wymaga zgłoszenia rozbieżności z `prd.md:258` i `roadmap.md:124`/`:139` — a dopiero potem harnessu, macierzy dostawców (tu 2 dostawców nie jest sprzeczne z dowodami) i ewentualnej weryfikacji LangGraph, która według dowodów należy do ścieżki pipeline'u (S-02), nie do samego kontraktu.

**Uzupełnienie po pilotach (2026-09-30)** — wskazówki wynikające z sekcji pilotażowych poniżej:

1. **Kolejność pracy:** zmiany kontraktu przez §14 na podstawie `pilot/labeling-guidelines.md` (v0.2, decyzje użytkownika) → zestaw wzorcowy oznaczony według nowych reguł → dopiero harness i wybór modelu. Obecny `plan.md` zakłada odwrotną kolejność (kontrakt zamrożony, luki jako wyjątek).
2. **Kandydaci na model:** zamiast macierzy 4 dostawców — tanie modele zmierzone w pilotach (gemini-3.1-flash-lite, deepseek-flash; koszt ~$3–8/mies. przy 12k postów); mocniejszy model ewentualnie tylko dla przypadków trudnych. „Przechodzi” wymaga wzorca, nie samej walidacji kontraktu (walidację przechodzi 95–98% odpowiedzi, a zgodność rdzenia ze wzorcem w rundzie 1 to 25–41%).
3. **Sposób wywołania:** skrócony prompt, pojedyncze wywołania; paczkowanie odradzone pomiarem (pilot 3); Batch API / okno off-peak i cache to otwarte pytania pipeline'u (S-02/S-05).
4. **Rozbieżność z PRD do zgłoszenia, nie do cichego rozstrzygnięcia:** rdzeń MVP wskazany przez użytkownika (trafność, cele, stance) vs „tezy z cytatami i powiązaniami argumentów” w `prd.md:258`, `roadmap.md:124`, `roadmap.md:139`.
5. **Znane luki kontraktu z danych:** normalizacja białych znaków w niezmienniku 8 (twarda spacja z Wykopu), posty z treścią w obrazie, anchor jako slang vs filtr tekstowy FR-011a, granulacja celów, granice `neutral`/`mixed`/`unclear`/`not_expressed`.

## Pilot na realnych danych (2026-09-29)

Dopisane po ramowaniu. Pilot wykonany ad hoc, poza repo: skrypt w scratchpadzie, klucze tymczasowe użytkownika, bez commitów kodu. Nie zmienia przeformułowania, tylko je wzmacnia i daje pierwszy pomiar dla wymiaru 1 (wykonalność).

**Dane.** 100 elementów z Wykop API v3 `GET /tags/gielda/stream` (`sort=all`), 2026-09-29 11:58–22:29: 97 wpisów, 3 znaleziska, 0 elementów z `parent`. Zanonimizowana próbka: `pilot/wykop-gielda-sample-2026-09-29.json` (nicki → `@uzytkownikN`, URL → `<url>`, twarde spacje zachowane celowo).

Charakterystyka próbki (97 wpisów):

- 42 ze zdjęciem; 8 bez tekstu poza tagami/wzmiankami/URL (7 z nich ze zdjęciem), więc treść jest w obrazie, którego kontrakt nie przekazuje.
- Długość: mediana 87 znaków, p90 316, 8 powyżej 320, 2 powyżej 2000 (max 6072, wielotematyczne przeglądy wiadomości).
- 58 z łamaniem linii, 9 z „lenny face” (znaki łączące Unicode), 6 ze słowami cenzurowanymi przez Wykop (`w------c`), 2 z twardą spacją, 8 ze wzmianką `@` bez rodzica.
- Nazwy spółek często slangiem: „en wideo”, „guglorz”, „makdolan”, „kurłejw”, „mikrus”, „redzi”, „śmieciuchy”. Częsta ironia i memy; część treści poza tematem.
- Wolumen ~100 elementów / 10,5 h, czyli rząd 200–250 dziennie (jeden dzień, szacunek).

**Przebieg.** 23 zróżnicowane wpisy × 4 modele, 1 uruchomienie, `temperature=0`. Prompt: wymagania `agent-implementation.md` §2 + pełny `contract-v1.md`. Walidator: niezmienniki 6–16 i limity §11.

| Model | Tryb structured output | Zgodne z kontraktem | Tokeny/post wej./wyj. (z myśleniem) | Mediana czasu |
| --- | --- | --- | --- | --- |
| gemini-3.5-flash-lite | `responseJsonSchema` | 20/23 (2× fabricated_evidence, 1× HTTP 503) | 4,2k / 0,4k | 1,8 s |
| gemini-3.8-flash | `responseJsonSchema` | 23/23 | 4,4k / 3,5k | 9,5 s |
| deepseek-flash | strict function calling `/beta`, myślenie wyłączone | 20/23 (3× fabricated_evidence) | 5,2k / 0,45k | 1,8 s |
| deepseek-v4-pro | jw. | 9/23 (12× brak wymaganego klucza, 1× fabricated, 1× niezmiennik 13) | 5,2k / 0,4k | 2,9 s |

**Ustalenia.**

1. **Rozumienie polskiego nie jest wąskim gardłem.** Wszystkie 4 modele rozpoznały slang jako cele, odrzuciły treść nieinwestycyjną i pusty `#gielda` (`insufficient_content`), a ironię w większości oznaczyły jako `humor`. Kierunek stance był zgodny między modelami przy wpisach jednoznacznych; w 7/23 wpisach co najmniej jeden model dał inny kierunek (wpisy dwuznaczne: Micron, Google vs Nvidia, CD Projekt po darmowym remasterze, Sivers).
2. **Wszystkie 6 odrzuceń za `fabricated_evidence` to nie halucynacje treści**, tylko: (a) twarda spacja `\xa0` z Wykopu oddana jako zwykła spacja, 2×; (b) skrót cytatu przez „[...]”, 1×; (c) zmiana odmiany („rynku robotów humanoidalnych” → „rynek …”) w anchorze, 2×, plus pokrewny przypadek. Niezmiennik 8 bez normalizacji (`contract-v1.md:427`) traktuje je jak zmyślenie.
3. **Anchor to slang lub hashtag** („#enterair”, „redzi”, „#cdprojektred”, „Nadaq100”). Filtr tekstowy FR-011a (`prd.md:124`) po nazwie spółki takich wpisów nie znajdzie; rozpoznawanie spółek jest poza MVP (`prd.md:289`).
4. **Ograniczenia dostawców potwierdzone empirycznie.** Gemini zwraca HTTP 400 dla schematu z `minItems`/`maxItems`; bez nich przyjmuje schemat z `maxLength` i `additionalProperties:false`. DeepSeek w trybie myślenia odrzuca wymuszony `tool_choice`, więc trzeba wyłączyć myślenie. Strict function calling w `deepseek-v4-pro` pominął wymagany klucz `supported_by_claim_ids` w 12/23 odpowiedzi, czyli strict nie gwarantuje `required`; `deepseek-flash` tego nie robił. Limity i kompletność egzekwuje wyłącznie backend (`contract-v1.md:437`).
5. **Koszt jest zdominowany przez stały prompt** (~4k tokenów kontraktu na każde wywołanie) i, przy Gemini 3.8 Flash, przez tokeny myślenia (~8× więcej wyjścia niż Flash-Lite).

**Wpływ na przeformułowanie.** Wymiar 1 (wykonalność) przesuwa się z SŁABE na częściowo zmierzone: rdzeń MVP (trafność, cele, stance) tanie modele obsługują; straty generuje dosłowność cytatów i kompletność struktury claimów, czyli części kontraktu, których użytkownik nie wskazał jako niezbędnych. Wymiar 2 (niedookreślenie) zyskuje trzy konkretne luki z danych: normalizacja białych znaków w niezmienniku 8, obrazy bez tekstu, anchor jako slang vs FR-011a. Pewność bez zmian: WYSOKA.

**Ograniczenia pilotu.** 23 wpisy z jednego dnia, jedno uruchomienie na model, prompt nieiterowany, brak ręcznej oceny poprawności semantycznej. Ceny do kosztorysu pochodzą z niezweryfikowanego streszczenia stron cenników.

### Pilot 2: pełny dzień, tanie modele, prompt pełny vs skrócony (2026-09-30)

**Dane.** Wszystkie elementy #gielda z 2026-09-28: 156 (152 wpisy, 4 znaleziska); 12 pominiętych przed wywołaniem LLM, bo poza tagami, wzmiankami i URL nie miały tekstu. Oceniono 144 posty × 3 modele × 2 prompty = 864 wywołania, ok. 2 min przy 12 równoległych wątkach, `temperature=0`.

**Prompty.** „Pełny” = wymagania §2 + cały `contract-v1.md` (~4,4k tokenów wejścia). „Skrócony” = wyciąg semantyki enumów, reguł stance, zasad cytatu i zakazów §13 (~1,3k tokenów); dodatkowo wprost ostrzega przed trzema błędami cytatów z pilotu 1 (nietypowe spacje, „[...]”, zmiana odmiany), więc porównanie nie jest czystym A/B długości.

| Model | Prompt | Zgodne z kontraktem | Tokeny wej. (z cache) / wyj. | Koszt przy 12k postów/mies.* |
| --- | --- | --- | --- | --- |
| deepseek-flash | pełny | 139/144 (97%) | 5166 (4943) / 325 | ~$6 |
| deepseek-flash | skrócony | 137/144 (95%) | 2388 (1977) / 412 | ~$8 |
| gemini-3.1-flash-lite | pełny | 140/144 (97%) | 4355 / 273 | ~$6,5 |
| gemini-3.1-flash-lite | skrócony | 141/144 (98%) | 1337 / 347 | ~$3,3 |
| gemini-3.5-flash-lite | pełny | 138/144 (96%) | 4355 / 279 | ~$24 |
| gemini-3.5-flash-lite | skrócony | 140/144 (97%) | 1337 / 332 | ~$15 |

\* Ceny z niezweryfikowanego streszczenia cenników; cache DeepSeek raportowany przez API, dla Gemini nie mierzony. **Koszty gemini-3.1-flash-lite w tej tabeli są zaniżone — patrz „Korekta cen (2026-09-30)”.**

**Zgodność rdzenia między modelami** (trafność + cele ze stanowiskiem innym niż `not_expressed`, anchory dopasowane po znormalizowanym zawieraniu):

| Para | Trafność (pełny / skrócony) | Rdzeń (pełny / skrócony) |
| --- | --- | --- |
| deepseek-flash vs gemini-3.1-flash-lite | 74% / 76% | 56% / 48% |
| deepseek-flash vs gemini-3.5-flash-lite | 68% / 78% | 53% / 47% |
| gemini-3.1-flash-lite vs gemini-3.5-flash-lite | 83% / 95% | 62% / 72% |
| wszystkie 3 zgodne | — | 42% / 41% |

**Symulacja kaskady** (tani model → eskalacja do mocniejszego): eskalacja tylko po błędzie walidacji objęłaby 2–5% postów; po sygnałach niepewności (`relevance=unclear`, stance `unclear|mixed`, reference `ambiguous|unresolved`, flagi `possible_sarcasm|missing_context`) 7–13% dla Gemini Lite i 47–56% dla DeepSeek; po niezgodzie dwóch tanich modeli 44–53%.

**Ustalenia.**

1. **Koszt nie jest już ograniczeniem.** Wszystkie tanie warianty mieszczą się w ~$3–25 miesięcznie przy 12k postów; zgodność z kontraktem 95–98% niezależnie od promptu.
2. **Skrócony prompt nie pogarsza zgodności z kontraktem** i tnie wejście 3,3×. Dla DeepSeek różnica kosztu jest pomijalna, bo cache obejmuje ~96% długiego promptu.
3. **„Tani model wystarczy w ~90%” jest dziś niemierzalne.** Tanie modele zgadzają się ze sobą w rdzeniu w 47–72% par, a we wszystkich trzech naraz w ~41% postów. Bez wzorca nie wiadomo, który ma rację; Gemini 3.8 Flash jako arbiter byłby czwartą opinią, nie prawdą.
4. **Rozbieżności wynikają głównie z niedookreślenia zadania, nie z błędów walidacji.** Przy skróconym prompcie, para gemini-3.1-flash-lite vs deepseek-flash: 34 z niezgodnych postów różnią się trafnością (najczęściej `investment_related` vs `unclear`), 29 liczbą celów ze stanowiskiem, 12 kierunkiem lub anchorem. Źródła: (a) temperament modeli — DeepSeek ostrożny (`not_expressed`, `unclear`, `missing_context` przy ok. połowie postów), Gemini Lite rozstrzyga śmielej; (b) granulacja celów — ten sam przegląd wiadomości: 5 celów vs 11 (fragmenty zdań jako cele); (c) slang i memy — „na peronie” (slang: wyjście z rynku) oznaczone jako cel „peronie → bullish”, „CD Projekt jutro 250 albo 150” jako `neutral` / `mixed` / `unclear`; (d) trafność memów i wpisów o forum.
5. **Kontrakt nie definiuje jednoznacznie**, co jest celem (granulacja, tematy vs fragmenty zdań), kiedy mem jest `investment_related`, ani granicy `neutral` / `mixed` / `unclear` / `not_expressed` dla typowego wpisu z #gielda. To dopisuje się do wymiaru 2 (niedookreślenie) jako obserwacja z danych, a nie tylko z lektury.

**Wpływ na przeformułowanie.** Wzmacnia je: modele „coś zwracają” i przechodzą walidację, ale bez wzorca nie da się ocenić, czy rdzeń jest poprawny. Następny krok dowodowy to ręcznie oznaczony zestaw wzorcowy postów (trafność, cele, kierunek stance), względem którego mierzy się modele i z którego wynikają brakujące reguły kontraktu.

**Zestaw wzorcowy.** 70 postów z 2026-09-28 (45 z niezgodą tanich modeli, 25 ze zgodą; kolejność losowa, wyniki modeli ukryte): `pilot/labeling-set-2026-09-28.json`, arkusz `pilot/labeling.html`, etykiety użytkownika `pilot/labels-2026-09-28.json` (2026-09-30; 69 oznaczonych, #61 bez etykiety — treść w obrazku). Porównanie: `pilot/scripts/compare_gold.py`.

### Porównanie ze wzorcem, runda 1 (2026-09-30)

Wzorzec: trafność 34 inwestycyjne / 16 nieinwestycyjne / 19 nierozstrzygalne; 24 cele w 17 postach, z czego 23 ze stanowiskiem; 9 postów oznaczonych „trudny”. „Rdzeń” = trafność + zbiór celów ze stanowiskiem (anchory dopasowane po znormalizowanym zawieraniu) + kierunek.

| Wariant | Trafność = wzorzec | Rdzeń = wzorzec | Kierunek na dopasowanych celach |
| --- | --- | --- | --- |
| deepseek-flash, pojedynczo, skrócony | 61% | 41% | 80% |
| deepseek-flash, pojedynczo, pełny | 57% | 38% | 83% |
| gemini-3.1-flash-lite, pojedynczo, pełny | 61% | 35% | 75% |
| gemini-3.1-flash-lite, pojedynczo, skrócony | 59% | 30% | 86% |
| gemini-3.5-flash-lite, pojedynczo, pełny / skrócony | 58% / 57% | 30% / 25% | 100% / 71% |
| gemini-3.1-flash-lite, paczki (7 wariantów) | 55–59% | 22–32% | 57–86% |
| deepseek-flash, paczki (7 wariantów; 12–25 brakujących ocen) | 30–45% | 13–26% | 50–100% |

**Ustalenia.**

1. **Jedyny twardy wniosek o wariantach:** pojedyncze wywołania są zgodne ze wzorcem co najmniej tak dobrze jak paczki, a paczki deepseek-flash wyraźnie gorzej — potwierdza pomiar z pilota 3.
2. **Rankingu modeli nie da się z tego ułożyć**, bo rozjazd wynika głównie z różnicy definicji między oznaczającym a kontraktem/modelami, nie z błędów modeli:
   - **cel:** oznaczający często zaznaczał całą tezę („te akcje zaraz będą po 150 zł…”, „Kruk jest 23% niżej niż na początku roku”), modele — podmiot („CD Projekt”, „Kruk”); interpretację często zapisywał w notatce zamiast jako cel (#28 Enel-Med, #58 pamięciorze, #18 „bańka na cyber security”); modele dodają cele znacznie częściej (wzorzec: cele w 17/69 postów);
   - **trafność `unclear`:** 19/69 (28%) we wzorcu; gemini-3.1-flash-lite oznaczył 17 z tych 19 jako `investment_related`, deepseek-flash trafił 8/19;
   - **narzekania na brokera/produkt i posty o forum** (#3, #5, #20, #22) — wzorzec: nieinwestycyjne; modele w 7–8 z 16 postów nieinwestycyjnych we wzorcu dały `investment_related`.
3. **Oznaczający też zgaduje bez reguł:** #64 `#wittchen` odczytany jako CD Projekt (pomyłka Wittchen/Wiedźmin, potwierdzona przez użytkownika); 9 postów „trudnych”.
4. **Notatki oznaczającego to gotowy materiał na reguły kontraktu:** memy i żarty „do odsiania” (#14, #22, #40, #68); slang („szczur” = XTB #41, „pamięciorze” #58, „na peronie” #32, „korłejw” #12); hashtag jako wskazówka celu (#meczennicyxtb → XTB #67); posty z samym linkiem (#10, #38, #42); sugestie bez konkretu (#6, #7, #18).

**Wniosek.** Wzorzec bez wspólnych wytycznych mierzy różnicę definicji, nie jakość modeli. Kolejny krok: instrukcja oznaczania (szkic: `pilot/labeling-guidelines.md`), korekta etykiet według niej, ponowne porównanie. Reguły zaakceptowane w instrukcji to kandydaci na zmianę `contract-v1.md` (§14) i prompt.

**Runda 2 (przygotowana, niewykonana).** Arkusz `pilot/labeling-r2.html` (te same 70 postów, etykiety z rundy 1 jako punkt wyjścia, skrót instrukcji v0.2); modele już policzone z regułami v0.2 w prompcie (`pilot/scripts/prompt-v2-rules.md`, wyniki `pilot/runs/pilot4-results.json`: zgodne z kontraktem gemini-3.1-flash-lite 70/70, gemini-3.5-flash-lite 66/70, deepseek-flash 64/70). Brakuje etykiet użytkownika (`labels-2026-09-28-r2.json`). Uwaga: reguły powstały z tych samych 70 postów, więc wynik rundy 2 będzie optymistyczny — test niezależny wymaga świeżej próbki.

### Pilot 5: Reddit jako źródło (2026-09-30)

Pytanie użytkownika: czy problemem jest materiał z Wykopu (wtedy Wykop po MVP), czy samo podejście.

**Dane.** Arctic Shift (publiczne API, bez klucza), r/stocks, r/ValueInvesting, r/investing, 2026-09-24…29 UTC: 560 postów, z czego 305 zdjętych (głównie przez moderatorów; w archiwum zostaje `[removed]`), 255 żywych; 12 automatycznych wątków cyklicznych (Daily/Weekly Thread) wyłączonych → **243 posty**. Wszystkie tekstowe; mediana treści 606–1042 znaków zależnie od kanału (Wykop: 87), 9 postów > 4000 znaków (max 12 193). Wolumen ~40–55 żywych postów dziennie z trzech kanałów (~1 300/mies.) vs ~150–250 dziennie z #gielda.

**Przebiegi** (pojedyncze wywołania, `temperature=0`, skrócony prompt; `statement` w języku posta — decyzja użytkownika: tezy po angielsku są akceptowalne, tłumaczenie ewentualnie później):

| Model · prompt | Zgodne z kontraktem | Cele / claimy na post (mediana) | Sygnał niepewności | Tokeny wyj./post | Koszt/post* |
| --- | --- | --- | --- | --- | --- |
| deepseek-flash · PL | 179/243 (74%) | 4 / 6 | 13% | 1 245 | $0,0017 |
| deepseek-flash · EN | 180/243 (74%) | 4 / 6 | 22% | 1 250 | $0,0017 |
| gemini-3.1-flash-lite · EN | 212/243 (87%) | 2 / 3 | 3% | 668 | $0,0004 |

\* Ceny z niezweryfikowanego streszczenia cenników. **Koszty gemini-3.1-flash-lite w tej tabeli są zaniżone — patrz „Korekta cen (2026-09-30)”.** Prompt EN (`pilot/scripts/prompt-short-en.md`) poza językiem dopisuje limit 4 claimów, kopiowanie markdownu i poprawne pole cytatu — nie jest czystym A/B języka.

**Ustalenia.**

1. **Trafność przestaje być problemem:** 242–243 z 243 postów `investment_related` w każdym przebiegu (Wykop, ten sam model i prompt: 102/143).
2. **Język promptu steruje językiem tez:** przy prompcie PL 65% `statement` po polsku mimo instrukcji „w języku posta”; przy prompcie EN 0%. Liczba tokenów wyjścia bez zmian (1 245 vs 1 250) — angielskie tezy nie obniżają kosztu.
3. **Odrzucenia wynikają z reguł kontraktu, nie z rozumienia treści:** limity §11 za ciasne dla długich analiz (`statement` > 240 znaków: 18–21 naruszeń; „max 4” przy stance/uzasadnieniach: 38 → 1 po dopisaniu limitu do promptu); naruszenia niezmienników domenowych, głównie 12 (stance oparty na claimie niewskazującym celu): 36–53 u deepseek-flash, 7 u gemini; cytaty — z 34–37 niezgodnych 19–29 różni się tylko markdownem Reddita, białymi znakami lub wielkością liter.
4. **Nadmiar celów:** deepseek-flash 1 104 cele na 243 posty (707 bez stanowiska), w tym fragmenty zdań („3.7% year-over-year baseline”, „China is weak”); gemini-3.1-flash-lite 538.
5. **Zgodność rdzenia między modelami: 49% — tyle samo co na Wykopie (48%), ale z innej przyczyny:**

   | Skąd niezgoda (udział postów; deepseek-flash vs gemini-3.1-flash-lite) | Reddit | Wykop |
   | --- | --- | --- |
   | zgodne w całości | 49% | 48% |
   | ten sam kierunek, inny zestaw celów | 40% | 24% |
   | różna trafność | 0% | 24% |
   | inny kierunek wobec tego samego celu | 11% | 5% |

   Na wspólnych celach ze stanowiskiem kierunek zgadza się w 82% (Reddit, 199 celów) i 85% (Wykop, 48 celów); ocena wprost przeciwna (bullish ↔ bearish): 1 raz na Reddicie, 0 na Wykopie.

**Wnioski dla ramowania.**

- **Kierunek stanowiska jest stabilny w obu źródłach; niestabilny jest wybór celów** (które podmioty są celami i którym przypisać stanowisko) — przyczyna niezależna od źródła, leżąca w definicji kontraktu (por. instrukcja v0.2, reguły 3.1–3.3).
- **Wykop ma dodatkowy, własny problem trafności** (memy, żale, westchnienia: niezgoda przy co czwartym poście), którego Reddit nie ma. Hipoteza „problemem jest materiał z Wykopu” potwierdza się więc częściowo: dla odsiewu szumu tak, dla wyboru celów nie.
- **Reddit to inny produkt niż Wykop:** mało postów, długie analizy i pytania, dużo dyskusji w komentarzach i wątkach cyklicznych (komentarze poza MVP — `prd.md:285`), ponad połowa postów zdejmowana przez moderatorów. Zmiana źródła MVP byłaby zmianą PRD (Wykop = FR-001 must-have, Reddit = FR-023 nice-to-have) — do zgłoszenia, nie do rozstrzygnięcia tutaj.
- **Do kontraktu (przy Reddicie):** język `statement` (dziś „po polsku”, `contract-v1.md:336`), limity §11 dla długich treści, normalizacja markdownu w niezmienniku 8, uproszczenie powiązań cel–claim (niezmiennik 12).
- **Ograniczenie:** zgodność modeli między sobą, bez wzorca ludzkiego dla Reddita; trzy kanały, sześć dni.

Wyniki: `pilot/runs/pilot5-reddit-*.json`; skrypty `pilot/scripts/pilot5.py`, `analyze5.py`.

**Pytanie otwarte na kolejne ramowanie (zgłoszone przez użytkownika 2026-09-30):** czy ocena każdego posta osobno przez LLM według kontraktu V1 jest właściwym zadaniem dla tego produktu i którego źródła (Wykop, Reddit, oba) ma dotyczyć MVP. Dotyka założeń PRD (FR-001, FR-004) i gwiazdy przewodniej S-02, więc wykracza poza F-02.

### Pilot 6: Jev (TypeSafe) jako model decyzyjny i sygnał niepewności (2026-09-30)

Dopisane po rundzie 2 ramowania, w trakcie planowania. Pytanie użytkownika: czy lekki model decyzyjny (encoder) może pomóc LLM w analizie.

**Model.** Jev (`typesafe/jev-1.13`, TypeSafe AI, wydany 2026-09-15) — hostowany model decyzyjny, nie generuje tekstu; odpowiada na pytania typu `choice` (wybór z opisanych opcji), `score`, `noul` (tak/nie jako prawdopodobieństwo), zwracając prawdopodobieństwa i `confidence`; działa bez trenowania (opcje opisane w zapytaniu). Wywołanie przez OpenRouter: `POST https://openrouter.ai/api/alpha/decisions` (interfejs „alpha”). Cena wg katalogu OpenRouter: $0,042 / 1M tokenów wejścia, wyjście bezpłatne.

**Przebieg.** 143 posty #gielda z 2026-09-28 (te same co pilot 2), jedno wywołanie na post z pytaniami: trafność (3 opcje, kryteria z instrukcji v0.3), czy autor wyraża stanowisko (`noul`), dominujący kierunek posta, kierunek wobec każdego celu wskazanego wcześniej przez gemini-3.1-flash-lite (167 celów). 9 s, $0,0076; powtórka 40 postów. Skrypty: `pilot/scripts/pilot6.py`, `analyze6.py`; wyniki: `pilot/runs/pilot6-jev-*.json`.

| Względem etykiet użytkownika z rundy 1 (n=69) | Jev | gemini-3.1-flash-lite | deepseek-flash |
| --- | --- | --- | --- |
| Trafność binarna (bez „nierozstrzygalnych” użytkownika, n=49) | 82% | 82% | 73% |
| Dominujący kierunek posta | 54% | 56% | 74% |
| Sprzeczny kierunek (bullish ↔ bearish) | 3 | 1 | 1 |

**Ustalenia.**

1. **Jako model decyzyjny Jev nie jest lepszy od tanich LLM:** zgodność z modelami 69–76% (gemini vs deepseek: 76–78%); kierunek wobec celu wskazanego przez Gemini zgodny w 70% (167 celów, 3 sprzeczne).
2. **`confidence` Jev jest użytecznym sygnałem niepewności dla trafności:** w ćwiartce postów o najniższym confidence 49% to posty, przy których gemini i deepseek różnią się trafnością (kolejna ćwiartka 31%, górna połowa 8%); ta ćwiartka zawiera 50% wszystkich takich postów. Dla postów oznaczonych przez użytkownika jako „nierozstrzygalne” lub „trudne”: 59% / 29% / 29%. Dla kierunku wobec celu: mediana confidence 0,86 przy zgodności z Gemini, 0,61 przy niezgodności. Dla dominującego kierunku całego posta sygnał jest słaby i niemonotoniczny (20% / 39% / 14%).
3. **Cechy użytkowe:** powtarzalność na 40 postach — ta sama trafność 100%, ten sam dominujący kierunek 98%; ~1 250 tokenów wejścia na post (głównie treść pytań), ~$0,00005/post, ~$0,63/mies. przy 12k postów; mediana 0,44 s.

**Wniosek.** Jev nie zastępuje LLM w ocenie, ale jest kandydatem na tani, powtarzalny wyzwalacz eskalacji (spójniejszy niż flagi niepewności samych LLM: 7–13% postów u gemini, ~50% u deepseek). Do planu: jeden z mierzonych na wzorcu sygnałów niepewności, bez wpisywania go w architekturę na stałe. Zastrzeżenia: etykiety rundy 1 były oznaczane ostrożniej i bez instrukcji; jeden dzień danych; interfejs „alpha” i model dwutygodniowy; deklaracje producenta o kalibracji niezweryfikowane poza tym pomiarem.

### Pilot 7: Span-01 (Respan) jako model decyzyjny i sygnał niepewności (2026-09-30)

**Model.** Span-01 (`respan/span-01`, $0,02 / 1M tokenów wejścia) i Span-01 Lite (`respan/span-01-lite`, bezpłatny), wydane 2026-09-24…26 — klasyfikator „zachowań” opisanych zdaniem, zbudowany do oceny rozmów i śladów agentów AI. Przez OpenRouter (`POST /api/alpha/decisions`) przyjmuje wyłącznie pytania tak/nie (`noul`) i wejście jako zwykły tekst lub rozmowę; zwraca jedno prawdopodobieństwo na pytanie (odpowiedź „nie da się zaobserwować” z dokumentacji producenta nie jest w tym interfejsie dostępna).

**Przebieg.** Te same 143 posty #gielda z 2026-09-28; siedem pytań po polsku (treść inwestycyjna, nieinwestycyjna, „nie da się ustalić, o co chodzi”, stanowisko, bullish, bearish, żart); format „tekst + pytania po polsku” wybrany po próbie na 5 postach. 27 s na model, $0,0018 (pełny) i $0 (Lite). Skrypty: `pilot/scripts/pilot7.py`, `analyze7.py`; wyniki: `pilot/runs/pilot7-span01*.json`.

**Ustalenia.**

1. **Pełny i Lite dają praktycznie te same odpowiedzi** (średnia różnica prawdopodobieństw 0,004).
2. **Jako model decyzyjny jest zbyt zachowawczy dla przyjętego progu stanowiska:** kierunek (bullish/bearish ≥ 0,5) wskazał w 15 z 143 postów; trafność binarna względem etykiet użytkownika 65% (Jev 82%, gemini 82%, deepseek 73%). Zgodność dominującego kierunku z etykietami rundy 1 wynosi 79%, ale głównie dlatego, że zarówno model, jak i ostrożne etykiety rundy 1 najczęściej mówią „brak”; zgodność z gemini 55%, z deepseek 66%, z Jev 48%.
3. **Pytanie „nie da się ustalić, o co chodzi” działa jako sygnał niepewności porównywalnie z confidence Jev:** w ćwiartce postów o najwyższym wyniku 46% to posty, przy których gemini i deepseek różnią się trafnością (kolejna ćwiartka 28%, pozostała połowa 11%), co obejmuje 47% wszystkich takich postów (Jev: 50%); dla postów „nierozstrzygalnych”/„trudnych” wg użytkownika: 59% / 18% / 35%; mediana wyniku 0,60 dla „nierozstrzygalnych” użytkownika wobec 0,22 dla pozostałych. Sama odległość odpowiedzi „treść inwestycyjna” od 0,5 sygnałem nie jest (29% / 17% / 25%).
4. **Koszt:** ~630 tokenów wejścia na post, ~$0,15/mies. przy 12k postów (Lite: $0); mediana 0,53 s.

**Wniosek.** Span-01 nie nadaje się na model decyzyjny dla tego zadania, ale jego pytanie o nierozstrzygalność jest tanim kandydatem na wyzwalacz eskalacji, do zmierzenia na wzorcu obok Jev. Zastrzeżenia jak w pilocie 6; dodatkowo model był trenowany na rozmowach z agentami, nie na postach forum.

### Pilot 8: GPT-6 Luna (OpenAI) jako kandydat (2026-09-30)

**Przebieg.** `openai/gpt-6-luna` przez OpenRouter (`/api/v1/chat/completions`, structured outputs `json_schema` strict, schemat bez limitów długości i liczności), te same 143 posty #gielda z 2026-09-28, skrócony prompt, pojedyncze wywołania, `temperature=0`, rozumowanie domyślne. 222 s przy 8 wątkach, łącznie $0,075. Skrypty: `pilot/scripts/pilot8.py`, `analyze8.py`; wyniki: `pilot/runs/pilot8-gpt6luna.json`.

| | gpt-6-luna | gemini-3.1-flash-lite | deepseek-flash |
| --- | --- | --- | --- |
| Zgodność z kontraktem | 141/144 (98%) | 98% | 95% |
| Naruszenia `fabricated_evidence` | 0 | 1 | 2 |
| Trafność binarna wzgl. etykiet rundy 1 (n=49) | 73% | 82% | 73% |
| Dominujący kierunek wzgl. etykiet rundy 1 (n=67) | 66% | 55% | 73% |
| Sprzeczny kierunek wzgl. etykiet | 1 | 1 | 1 |
| Sygnał niepewności w odpowiedzi (udział postów) | 35% | 7% | 55% |
| Mediana czasu na post | 8,7 s | 1,4 s | 1,8 s |

**Ustalenia.**

1. **Forma:** jedyny model bez ani jednego przerobionego cytatu; 3 odpowiedzi nie-ok to 2 przekroczenia czasu po stronie dostawcy (HTTP 504) i 1 naruszenie niezmiennika 14.
2. **Treść nie jest wyraźnie lepsza od tanich modeli:** 133 ze 141 postów uznane za `investment_related` (tylko 5 `not_investment_related`), stąd trafność 73%; kierunek między gemini a deepseek. Zgodność z gemini: trafność 91%, dominujący kierunek 74%, sprzeczne 0; z deepseek: 77% / 78% / 1.
3. **Koszt i czas:** średnio 1 753 tokeny wejścia (1 581 z cache) i 985 wyjścia, z czego 699 to rozumowanie; zmierzony koszt $0,00053/post, ~$6,4/mies. przy 12k postów. Wariant z niskim lub wyłączonym rozumowaniem niezmierzony.

**Wniosek.** Luna jest sensownym kandydatem do pomiaru na wzorcu (czyste cytaty, umiarkowany koszt), ale pilot nie pokazuje, że jest „mocnym modelem” w trafności i progu stanowiska; porównania z gemini-3.8-flash na tych postach brak. Decyzja użytkownika: dodać Lunę do planu jako mocnego kandydata.

### Korekta cen (2026-09-30)

Koszty modelu **gemini-3.1-flash-lite** w sekcjach „Pilot 2”, „Pilot 3” (pomiar paczkowania) i „Pilot 5” były liczone z ceną $0,10 / $0,40 za 1M tokenów (wejście / wyjście), przyjętą bez źródła. Katalog OpenRouter (odczyt API 2026-09-30) podaje $0,25 / $1,50 (cache $0,025). Wartości poprawione, przy tych samych zmierzonych tokenach:

| Miejsce | Było | Jest |
| --- | --- | --- |
| Pilot 2, prompt pełny (12k postów/mies.) | ~$6,5 | ~$18 |
| Pilot 2, prompt skrócony | ~$3,3 | ~$10 |
| Pilot 3, pojedynczo / paczki 5 / 10 / 15 / godzinowe | ~$3,3 / 2,0 / 1,7 / 1,6 / 1,9 | ~$10 / 6,7 / 6,1 / 5,8 / 6,4 |
| Pilot 5 (Reddit), koszt na post | $0,0004 | ~$0,0014 (~$1,8/mies. przy ~1 300 postach) |

Ceny **deepseek-flash** użyte w pilotach ($0,30 wejście bez cache, $0,006 z cache, $1,20 wyjście) potwierdzone na stronie cennika DeepSeek jako stawki w godzinach szczytu; poza szczytem połowa; w katalogu OpenRouter ten sam model (`deepseek/deepseek-v4.1-flash`) ma $0,02 / $0,003 / $0,396. Cena gemini-3.5-flash-lite z pilotów ($0,30 / $2,50) pochodzi ze streszczenia strony cennika i nie była ponownie sprawdzana.

**Ranking kosztu po korekcie** (12k postów/mies., skrócony prompt, tokeny z pilotów na Wykopie): deepseek-flash przez OpenRouter ~$2; deepseek-flash bezpośrednio poza szczytem ~$4; gpt-6-luna ~$6,4 (zmierzone, z domyślnym rozumowaniem); deepseek-flash bezpośrednio w szczycie ~$7,6; gemini-3.1-flash-lite ~$10. Wniosek jakościowy bez zmian (tanie modele to kilka–kilkanaście dolarów miesięcznie), ale gemini-3.1-flash-lite nie jest najtańszym kandydatem, a oszczędność z paczkowania liczona w dolarach jest większa niż podano w pilocie 3 (~$4 zamiast ~$1,5/mies.) — nadal przy zmianie co trzeciej oceny. Wszędzie w tym pliku, gdzie pada zakres „~$3–8/mies.” dla tanich modeli, właściwy zakres to „~$2–10/mies.”.

## Otwarte pytania dla /10x-plan (koszt i sposób wywołania)

Zgłoszone przez użytkownika 2026-09-30; nierozstrzygnięte — do decyzji w planie, najlepiej z pomiarem na zestawie wzorcowym.

1. **Paczkowanie postów (wiele postów w jednym wywołaniu).** Pilot 1 i 2 wysyłały każdy post osobnym wywołaniem. Paczka amortyzuje stały prompt (~1,2k tokenów skróconego promptu vs ~100 tokenów typowego posta), co szacunkowo tnie koszt o ~40–50% (np. gemini-3.1-flash-lite: ~$3,3 → ~$1,8/mies. przy 12k postów; szacunek, nie pomiar). Ryzyka: przeciekanie między postami (cytat lub stance z sąsiedniego posta — walidator łapie zły cytat, nie łapie pomylonej oceny), jedna błędna odpowiedź psuje całą paczkę, ryzyko ucięcia długiego wyjścia, słabsza uwaga tanich modeli, rozliczanie tokenów/provenance per paczka zamiast per ocena (kontrakt opisuje ocenę pojedynczej wypowiedzi, `contract-v1.md:12`). Podobny efekt kosztowy bez tych ryzyk daje cache promptu (DeepSeek: ~96% wejścia z cache w pilocie 2; cache Gemini niezmierzony). Pytanie: czy paczkować, a jeśli tak — jaki rozmiar paczki i jaki spadek zgodności ze wzorcem jest akceptowalny?
   **Pomiar (pilot 3, 2026-09-30).** Te same 144 posty z 2026-09-28, skrócony prompt + dopisek o trybie wsadowym (każdy post oceniać niezależnie, cytaty tylko z własnego posta, lokalne `post_id`), modele gemini-3.1-flash-lite i deepseek-flash. Warianty: pojedyncze wywołania powtórzone (pomiar szumu), paczki chronologiczne i losowe po 5/10/15, paczki godzinowe (1–14 postów). Punkt odniesienia: pojedyncze wywołania z pilota 2. „Rdzeń” = trafność + cele ze stanowiskiem, jak w pilocie 2.

   | Wariant | gemini-3.1-flash-lite: rdzeń = pojedyncze | deepseek-flash: brak oceny / rdzeń = pojedyncze | Koszt przy 12k postów/mies. (Gemini / DeepSeek)* |
   | --- | --- | --- | --- |
   | pojedynczo, powtórzone | 100% | 2 / 82% | ~$3,3 / ~$7,4 |
   | paczki 5 (chronologicznie / losowo) | 74% / 72% | 44 / 30 brakujących | ~$2,0 / ~$5,1 |
   | paczki 10 (chronologicznie / losowo) | 65% / 72% | 30 / 20 | ~$1,7 / ~$4,6 |
   | paczki 15 (chronologicznie / losowo) | 64% / 62% | 60 / 45 | ~$1,6 / ~$4,5 |
   | paczki godzinowe | 65% | 48 | ~$1,9 / ~$5,0 |

   \* Ceny z niezweryfikowanego streszczenia cenników. **Koszty gemini-3.1-flash-lite w tej tabeli są zaniżone — patrz „Korekta cen (2026-09-30)”.**

   Ustalenia: (a) gemini-3.1-flash-lite jest w pojedynczych wywołaniach w pełni powtarzalny (100% identycznego rdzenia), więc 26–38% zmian w paczkach to efekt paczkowania, nie szum; rośnie z rozmiarem paczki. (b) Kolejność losowa nie różni się istotnie od chronologicznej — to nie przeciek tematyczny między sąsiednimi postami, tylko skutek oceniania wielu postów naraz; przeciek mechaniczny (cytat z cudzego posta) wystąpił 1–2 razy na wariant. (c) deepseek-flash w 15–40% paczek zwrócił odpowiedź, z której nie dało się odczytać żadnej oceny (`finish_reason=tool_calls`, surowe odpowiedzi nie zapisane, przyczyna nieustalona) — 20–60 brakujących ocen na 144. (d) Oszczędność ~$1–3/mies., mniejsza niż −50% z Batch API. (e) deepseek-flash nawet pojedynczo powtarza ten sam rdzeń tylko w 82% postów. Wniosek wstępny: paczkowanie zmienia co trzecią ocenę i przy DeepSeek gubi dane za ~$1–3/mies. oszczędności; czy zmiany są na gorsze — do rozstrzygnięcia porównaniem ze wzorcem (wyniki: `pilot/runs/pilot3-results.json`).

2. **Gemini Batch API.** −50% za tokeny wejścia i wyjścia, wyniki asynchronicznie (do 24 h), każdy post pozostaje osobnym żądaniem, więc bez ryzyka dla jakości; koszt: bardziej złożony przebieg (wysłanie zadania, odpytywanie, pobranie wyników) i dzienny przebieg nie kończy się natychmiast. DeepSeek zamiast batch ma −50% poza godzinami szczytu (poza 01–04 i 06–10 UTC pn–pt), w które trafi nocny cron. Pytanie: czy przebieg dzienny ma używać Batch API / okna off-peak — decyzja należy do projektu pipeline'u (S-02/S-05), nie do samego kontraktu, ale wpływa na interfejs `assess()` (pojedyncze wywołanie synchroniczne vs zlecenie wsadowe).
3. **Budżet LLM** (Open Roadmap Question 5, `roadmap.md:222`): po pilotach rząd wielkości to ~$2–25/mies. przy 12k postów dla tanich modeli; pułap nadal nieustalony.

## Odniesienia

- Pliki źródłowe: `context/foundation/llm-assessment/contract-v1.md:43`, `:125`, `:129`, `:138`, `:142`, `:233`, `:282`, `:340`, `:427-435`, `:447-454`, `:518-528`; `context/foundation/llm-assessment/examples.md` §3–§10; `context/foundation/prd.md:124`, `:258`, `:285`; `context/foundation/roadmap.md:47`, `:97`, `:105`, `:124`, `:139`, `:147`, `:222`, `:237`; `context/changes/assessment-contract-harness/plan.md:56`, `:58`, `:248`; commit `ef4041b` (PR #11)
- Powiązane badania: `context/changes/assessment-contract-harness/research.md`
- Próbka pilotu (zanonimizowana): `context/changes/assessment-contract-harness/pilot/wykop-gielda-sample-2026-09-29.json`
- Przegląd planu: `context/changes/assessment-contract-harness/reviews/plan-review.md` (F3)
- Zadania badawcze: audyt przykładów kontraktu vs niezmienniki; użycie pól kontraktu przez MVP; zewnętrzne dowody niezawodności złożonego structured output (bez TaskCreate — koordynacja w rozmowie)
