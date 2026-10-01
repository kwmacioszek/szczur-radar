

## Reguły dla forum #gielda (instrukcja v0.2)

### Trafność
- `investment_related` — rynek, spółka, aktywo, sektor lub makro w kontekście ceny, wyceny, transakcji, portfela, wyników; także krótko, żartem lub wulgarnie, jeśli da się wskazać, o czym mowa.
- `not_investment_related` — rozmowa o forum lub tagu, żale do użytkowników, pozdrowienia; żart bez konkretnego aktywa i bez kierunku; narzekania na brokera, bank lub aplikację (chyba że autor mówi o akcjach lub wynikach brokera jako spółki).
- `unclear` — z samego tekstu nie da się ustalić, czy i o co chodzi: treść jest w obrazku, sam link bez komentarza lub z komentarzem bez kierunku, zbyt ogólne westchnienie.
- Pytanie o wydarzenie lub osobę poruszającą rynkiem (np. wypowiedź polityka) → `investment_related`, cel = to wydarzenie/osoba (`macro_topic`), `not_expressed`.
- Link z komentarzem, który sam wyraża kierunek wobec aktywa → `investment_related` + stanowisko z komentarza.

### Cele
- Cel to podmiot, nie teza: spółka, ticker, aktywo, indeks, sektor, temat makro. Anchor = najkrótszy fragment nazywający cel, dokładnie jak w tekście (także slang i odmiana). Nie używaj całych zdań jako anchorów.
- Każda nazwana spółka lub aktywo jest celem — także bez stanowiska (`not_expressed`) i gdy występuje tylko w tle jako narzędzie (np. aplikacja brokera).
- Hashtag nazywający spółkę lub aktywo może być anchorem celu. Tagi kanału (#gielda, #gieldowegownowpisy, #finanse itp.) nigdy nie są celem.
- Spekulacyjna spółka nazwana tylko slangiem, bez nazwy → cel ogólny (`kind = other`) z kierunkiem.
- Przegląd wiadomości lub cytowany artykuł: cele = podmioty, o których mowa; fakty bez komentarza autora → `not_expressed`.
- Słownik slangu (pomaga rozpoznać cel; anchor zostaje jak w tekście): szczur = XTB; pamięciorze = producenci pamięci; mikrus/micrus = Micron; en wideo, jensen = NVIDIA; guglorz = Google; korłejw = CoreWeave; makdolan = McDonald's; redzi, wiedźminiarz = CD Projekt; śmieciuch = spekulacyjna spółka; gruby = duży gracz / animator rynku (NIE jest celem); peron / na peronie = pozostanie poza rynkiem, przegapienie wzrostu; pompa = wzrost; kiblage, zwała = spadek.

### Kierunek
- Scenariusz dwustronny bez preferencji („albo X, albo Y”) → `unclear`, nie `mixed`. `mixed` tylko, gdy autor wyraża oba kierunki wobec tego samego celu.
- Pytanie retoryczne wyrażające nadzieję lub oczekiwanie ruchu ceny → stanowisko w tym kierunku. Szczere pytanie o informację → `not_expressed`.
- Nadzieja autora na wydarzenie korzystne dla rynku → `bullish` (`hedged`) wobec tego wydarzenia/rynku. Cudza opinia przytoczona bez przyjęcia nie jest stanowiskiem autora.
- Autor pisze, że jest „na peronie” podczas wzrostów → cel „rynek” (`macro_topic`, anchor = fragment z „peronie”), `bullish`.
- Ironia: oceniaj zamierzony sens; jeśli nie da się go ustalić → `unclear`.
