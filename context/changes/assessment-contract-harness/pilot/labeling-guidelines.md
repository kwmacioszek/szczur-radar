# Instrukcja oznaczania postów #gielda — v1.0

> Status: v1.0 (2026-10-01) — reguły v0.3 przejął kontrakt `context/foundation/llm-assessment/contract-v2.md`; ta instrukcja odsyła do jego sekcji zamiast powtarzać definicje. Zostają tu: zakres oznaczania, słownik slangu (materiał do promptu, nie część kontraktu), flaga „trudny” i praktyka arkusza. Numery reguł 2.x / 3.x / 4.x z v0.3 są zachowane, bo odwołują się do nich `frame.md` i `plan.md`.
> Przy sprzeczności tej instrukcji z `contract-v2.md` obowiązuje kontrakt; brakującą regułę zgłasza się przez regułę zmiany kontraktu (§16), nie przez dopisanie jej tylko tutaj.

Oznaczamy tylko rdzeń: **trafność**, **cele** i **kierunek stanowiska autora** wobec każdego celu (+ czy stanowisko jest pewne, czy osłabione / z sugestii). Tez i cytatów nie oznaczamy.

## 1. Co oceniamy

- Wyłącznie tekst posta (tytuł + treść), tak samo jak model (`contract-v2.md` §2). Obrazków, filmów i linków nie widzimy.
- Wpisy bez treści (sam tag, wzmianka, link lub `<url>`) nie trafiają do oznaczania — odsiewa je reguła `contract-v2.md` §11.
- Tagi kanału nie są treścią ani celem; tagi nazywające spółkę lub aktywo mogą być anchorem (`contract-v2.md` §6.4).

## 2. Trafność

Wartości i definicje: `contract-v2.md` §3 (`relevance`). Reguły szczegółowe:

| Reguła v0.3 | Kontrakt V2 | Skrót |
| --- | --- | --- |
| 2.1 Memy i żarty | §5.1 | żart bez aktywa i kierunku → `not_investment_related`; z kierunkiem wobec aktywa/rynku → `investment_related` + stanowisko |
| 2.2 Narzekania na brokera/bank/aplikację | §5.2 | → `not_investment_related`, chyba że o akcjach lub wynikach brokera jako spółki |
| 2.3 Sam link | §5.3 | komentarz z kierunkiem → stanowisko; bez komentarza / bez kierunku → `unclear` |
| 2.4 Pytania o wydarzenia poruszające rynkiem | §5.4 | → `investment_related`, cel `macro_topic`, `not_expressed` |
| — Treść w obrazku | §5.5 | → `unclear` |

Przykłady z rundy 1: „Jaki jest lore tagu #gielda?” (#22) → `not_investment_related`; „Mam takie poczucie, że jutro będzie tak:” + obrazek (#61) → `unclear`; „śledzenie przepływów ropy <url>” (#42) → `unclear` z celem „ropy”.

*Adnotacja (poza MVP):* fala żartów przy spadkach bywa objawem nastroju — to sygnał zbiorczy, nie cecha pojedynczego posta (`contract-v2.md` §15: trend i narracja poza oceną pojedynczej wypowiedzi).

## 3. Cele

Definicje: `contract-v2.md` §6.

| Reguła v0.3 | Kontrakt V2 | Skrót |
| --- | --- | --- |
| 3.1 Cel to podmiot, nie teza | §6.1 | spółka, ticker, aktywo, indeks, sektor, temat makro |
| 3.2 Anchor | §6.2 | najkrótszy fragment nazywający cel, dokładnie jak w tekście; w arkuszu zaznacz tylko nazwę |
| 3.3 Każda nazwana spółka/aktywo to cel | §6.3 | także bez stanowiska i w tle; nie dotyczy postów `not_investment_related` (§4) |
| 3.4 Hashtag jako cel | §6.4 | tag spółki → anchor; czytaj dosłownie (`#wittchen` ≠ Wiedźmin) |
| 3.5 Slang | §6.5 | „śmieciuch” bez nazwy → `other`; słownik poniżej |
| 3.6 Przeglądy wiadomości | §6.6 | cele = podmioty; stanowisko tylko, gdy autor je dodaje |
| — Jeden cel na podmiot | §6.7 | kolejne wzmianki o tym samym podmiocie to ten sam cel |

### 3.5 Słownik slangu (materiał do promptu)

Anchor zostaje w oryginale; słownik tylko pomaga rozpoznać, że to cel.

| Slang | Znaczenie |
| --- | --- |
| szczur | XTB (broker/spółka) |
| pamięciorze | producenci pamięci (Micron, SK Hynix, Kioxia…) |
| mikrus, micrus | Micron |
| en wideo, jensen | NVIDIA (jensen = CEO, zwykle metonimia spółki) |
| guglorz | Google/Alphabet |
| korłejw, kurłejw | CoreWeave |
| makdolan | McDonald's |
| redzi, wiedźminiarz | CD Projekt |
| gierkorz | Take-Two (wniosek z jednego posta z `#ttwo` — do potwierdzenia) |
| śmieciuch(y) | spekulacyjna spółka / spółki (znaczenie przybliżone — do potwierdzenia) |
| gruby | duży gracz / animator rynku — **nie** cel |
| peron, na peronie | pozostanie poza rynkiem / przegapienie wzrostu — **nie** cel (patrz 4.6) |
| pompa / kiblage, zwała, kibel | wzrost / spadek — **nie** cel |

## 4. Kierunek stanowiska

Wartości `direction` i `assertiveness`: `contract-v2.md` §3 (`stance`); reguły: §7.1. W arkuszu „niepewne” = `hedged`.

| Reguła v0.3 | Kontrakt V2 | Skrót |
| --- | --- | --- |
| 4.0 Próg: szeroko + pewność | §7.1.0 | kierunek także z sugestii („bańka na X” → bearish, „X kusi” → bullish); sugestia = niepewne (`hedged`) |
| 4.1 Scenariusz dwustronny | §7.1.1 | „albo 250 albo 150” → `unclear`, nie `mixed` |
| 4.2 Ironia/sarkazm | §7.1.2 | zamierzony sens; nierozstrzygalny → `unclear` + „trudny” |
| 4.3 Ton ≠ stanowisko | §7.1.3 | „co za śmieciuch” przy spadku = bearish |
| 4.4 Pytanie retoryczne z nadzieją | §7.1.4 | „kiedy kruk po 500?” → bullish; szczere pytanie → `not_expressed` |
| 4.5 Przyjęta nadzieja / cudza opinia | §7.1.5 | „Cała nadzieja, że Trump ogłosi pokój” → `macro_topic`, bullish, niepewne |
| 4.6 „Na peronie” | §7.1.6 | cel „rynek” (`macro_topic`), bullish |

Uwaga: etykiety rundy 1 (`labels-2026-09-28.json`) były oznaczane bez instrukcji i ostrożniej niż próg 4.0 — nie są wzorcem.

## 5. Flaga „trudny”

Zaznacz, gdy po zastosowaniu reguł nadal masz wątpliwość, i opisz ją w notatce. Posty „trudne” nie liczą się do mianowników miar na zestawie wzorcowym, ale są materiałem na kolejne reguły kontraktu (reguła zmiany `contract-v2.md` §16).

## 6. Praktyka arkusza

- Arkusz rundy 2: `labeling-r2.html` (etykiety rundy 1 jako punkt wyjścia; przycisk „Importuj”). Arkusz zestawu wzorcowego F-02 powstaje w fazie 4 planu (`labeling-gold.html`) ze skrótem tej instrukcji.
- Najpierw trafność; przy `not_investment_related` nie dodawaj celów.
- Cel: zaznacz w tekście tylko nazwę (anchor), wybierz kierunek i pewność.
- Szacunkowo 20–30 min na 70 postów przy korekcie, ~1 h na nową próbkę ~120 postów.
