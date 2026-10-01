# Instrukcja oznaczania postów #gielda — v0.3

> Status: v0.3 — dodany próg stanowiska (4.0); decyzje użytkownika z 2026-09-30 wpisane (dawne punkty [DO DECYZJI] oznaczone ✅). Źródła: notatki i etykiety z rundy 1 (`labels-2026-09-28.json`), różnice względem modeli (`frame.md` → „Porównanie ze wzorcem, runda 1”), `contract-v1.md`.
> Zaakceptowane reguły są kandydatami na zmianę `contract-v1.md` (§14) i prompt — ta instrukcja nie zmienia kontraktu sama.

Oznaczamy tylko rdzeń: **trafność**, **cele** i **kierunek stanowiska autora** wobec każdego celu.

## 1. Co oceniamy

- Wyłącznie tekst posta (tytuł + treść). Obrazków, filmów i linków nie widzimy — tak samo jak model.
- Tagi kanału (`#gielda`, `#gieldowegownowpisy`, `#finanse`…) nie są treścią ani celem.
- Tagi nazywające spółkę/aktywo (`#cdprojektred`, `#wittchen`, `#xtb`, `#kioxia`, `#meczennicyxtb`) **są** wskazówką celu (patrz 3.4).

## 2. Trafność

| Wartość | Kiedy | Przykłady z rundy 1 |
| --- | --- | --- |
| `investment_related` | Autor mówi o rynku, konkretnym aktywie/spółce/sektorze/makro w kontekście ceny, wyceny, transakcji, portfela, wyników — także krótko, żartem lub wulgarnie, jeśli da się wskazać o czym | „AMD jeszcze na minus skończy… śmieciuch”, „Promocja na pamięciorzach przed środą”, „Chyba grubiej zapakuję się w AMZN” |
| `not_investment_related` | Brak treści rynkowej: rozmowa o forum/tagu, żale do użytkowników, pozdrowienia, żart bez aktywa i bez kierunku, narzekania na brokera/aplikację | „Jaki jest lore tagu #gielda?” (#22), „oprócz tego ludzie” (#14), „Muse — korzystaliście?” (#20) |
| `unclear` | Z samego tekstu nie da się ustalić, czy chodzi o rynek i o co: treść w obrazku, sam link, zbyt ogólne westchnienie | „Mam takie poczucie, że jutro będzie tak:” + obrazek (#61), „ile jeszcze tego gnojuuu” (#26), „śledzenie przepływów ropy <url>” (#42) |

Reguły szczegółowe:

- **2.1 Memy i żarty** ✅ Żart bez konkretnego aktywa i bez kierunku (np. „jak rośnie to dorzucamy, jak spada to uśredniamy” #68, #40) → `not_investment_related`. Żart, z którego wynika kierunek wobec konkretnego aktywa lub rynku („będzie zwała, a nie rajd świętego Mikołaja”) → `investment_related` + stanowisko.
  *Adnotacja (poza MVP):* fala takich postów bywa objawem desperacji przy spadkach — to sygnał zbiorczy, nie cecha pojedynczego posta; kandydat na przyszłą analizę ML/AI na poziomie strumienia (kontrakt §12: trend/narracja poza oceną pojedynczej wypowiedzi).
- **2.2 Narzekania na brokera/bank/aplikację** ✅ (UI XTB, awaria mBanku, „szczur dodał same śmieci na CFD”) → `not_investment_related`, chyba że autor odnosi się do akcji lub wyników brokera jako spółki („XTB po wynikach poleci”).
- **2.3 Sam link** ✅ Komentarz z kierunkiem („META bullish” #10, „Digital Network bulisz” #38) → `investment_related` + stanowisko z komentarza. Sam link bez komentarza lub z komentarzem bez kierunku („śledzenie przepływów ropy <url>” #42) → `unclear` (sens jest w linku, którego nie widzimy).
- **2.4 Pytania o wydarzenia poruszające rynkiem** ✅ („co powiedział Trump?” #6, #7) → `investment_related`, cel „Trump” (`macro_topic`) z `not_expressed`. Uzasadnienie: na #gielda wypowiedzi Trumpa są traktowane jako czynnik ruszający rynkiem.

## 3. Cele

- **3.1 Cel to podmiot, nie teza.** Spółka, ticker, aktywo (ropa, złoto, BTC), indeks, sektor („cyber security”, „pamięciorze”), temat makro („stopy”, „pokój z Iranem”, „Trump”). Zdanie „te akcje zaraz będą po 150 zł” to teza o celu „CD Projekt”, nie cel.
- **3.2 Anchor = najkrótszy fragment tekstu nazywający cel, dokładnie jak w tekście** — także slang i odmiana („pamięciorzach”, „korłejw”, „mikrusa”, „szczurze”). W arkuszu: zaznacz tylko nazwę, nie całe zdanie.
- **3.3 Każda nazwana spółka/aktywo to cel** ✅ — także gdy post jest o niej bez stanowiska (pytanie, informacja: „ataku hakerów na Enel-Med” → `not_expressed`) i gdy występuje tylko jako narzędzie w tle („odpalam XTB i patrzę na konto” → cel XTB, `not_expressed`). Reguła mechaniczna: mniej osądu dla modelu; szum trafia do filtra po nazwie, ale nie do filtra po stanowisku.
- **3.4 Hashtag jako cel.** Jeśli treść odnosi się do spółki tylko przez tag (`#wittchen leci na covidowy dołek` #64, `#meczennicyxtb` #67) → cel z anchorem = hashtag. Uwaga: w rundzie 1 `#wittchen` został odczytany jako CD Projekt (Wittchen ≠ Wiedźmin) — czytać tag dosłownie.
- **3.5 Słownik slangu** (anchor zostaje w oryginale; słownik tylko pomaga rozpoznać, że to cel):

  | Slang | Znaczenie |
  | --- | --- |
  | szczur | XTB (broker/spółka) |
  | pamięciorze | producenci pamięci (Micron, SK Hynix, Kioxia…) |
  | mikrus, micrus | Micron |
  | en wideo, jensen | NVIDIA (jensen = CEO, zwykle metonimia spółki) |
  | guglorz | Google/Alphabet |
  | korłejw | CoreWeave |
  | makdolan | McDonald's |
  | redzi, wiedźminiarz | CD Projekt |
  | gierkorz | Take-Two (wniosek z jednego posta z `#ttwo` — do potwierdzenia) |
  | śmieciuch(y) | spekulacyjna spółka / spółki (znaczenie przybliżone — do potwierdzenia) |
  | gruby | duży gracz / animator rynku — **nie** cel |
  | peron, na peronie | pozostanie poza rynkiem / przegapienie wzrostu |
  | pompa / kiblage, zwała | wzrost / spadek |

  ✅ „Śmieciuch” bez nazwy → cel ogólny (`kind = other`) z anchorem „śmieciucha” i kierunkiem („ostatni sprzedaje śmieciucha” → bearish).

- **3.6 Przeglądy wiadomości i artykuły** (#36, #12, #19): cele = podmioty, o których post mówi; fakty z artykułu bez komentarza autora → `not_expressed`. Stanowisko tylko, jeśli autor je dodaje („byczego dla korłejw” = bullish, jeśli to opinia autora, nie cytat).

## 4. Kierunek stanowiska

| Wartość | Kiedy | Przykład |
| --- | --- | --- |
| `bullish` | autor oczekuje wzrostu / ocenia pozytywnie / kupuje / liczy na wzrost | „Promocja na pamięciorzach przed środą”, „META bullish”, „kiedy kruk po 500?” |
| `bearish` | oczekuje spadku / ocenia negatywnie / sprzedaje | „to koniec dla pamięciorzy” (#58), „Bańka na cyber security” (#18) |
| `neutral` | jawnie neutralnie („trzymam, nic nie robię”) | — |
| `mixed` | autor wyraża **oba** kierunki wobec tego samego celu | „długo w górę, krótko w dół” |
| `not_expressed` | cel jest, stanowiska brak (informacja, szczere pytanie) | „ktoś wie, czy Trump coś powiedział?” |
| `unclear` | stanowisko jest, ale kierunku nie da się ustalić | „CD Projekt jutro 250 albo 150” |

- **4.0 Próg stanowiska: szeroko + pewność** ✅ (decyzja 2026-09-30, `frame.md` runda 2). Przypisz kierunek, gdy rozsądny czytelnik odczyta go z tekstu — także z sugestii („bańka na X” → bearish, „X kusi” → bullish, „zapakowany w X pod wyniki” → bullish). Słabe lub pośrednie przypadki oznacz jako niepewne (`hedged`; w arkuszu: flaga „trudny” + notatka). Powód: surowy post stoi obok oceny, więc fałszywe stanowisko łatwo zweryfikować, a pominiętego filtr nie pokaże. Uwaga: etykiety rundy 1 były oznaczane ostrożniej niż ten próg.
- **4.1 Scenariusz dwustronny bez preferencji** („albo 250 albo 150”, „albo kibel albo pompa stulecia”) → `unclear`, nie `mixed`. `mixed` tylko przy wyrażeniu obu kierunków (np. krótki i długi termin).
- **4.2 Ironia/sarkazm:** oznacz zamierzony sens; jeśli nie da się go ustalić → `unclear` (i zaznacz „trudny”).
- **4.3 Ton ≠ stanowisko:** „co za śmieciuch” przy spadku = bearish; „zarząd to dramat, ale kupuję” = bullish.
- **4.4 Pytanie retoryczne z nadzieją** ✅ („kiedy kruk po 500?”) → `bullish`. Szczere pytanie o informację („ktoś wie, co powiedział Trump?”) → `not_expressed`. Zgodne z kontraktem: pytanie o ruch ceny nie jest *automatycznie* stanowiskiem (`agent-implementation.md` §2), ale może nim być.
- **4.5 Przyjęta cudza opinia / nadzieja na wydarzenie** ✅ („Cała nadzieja, że Trump o 20 ogłosi pokój”) → cel wydarzenia/rynku (`macro_topic`), `bullish`, `hedged`. Cudza opinia przytoczona bez przyjęcia (analityk, artykuł) → nie jest stanowiskiem autora.
- **4.6 „Na peronie”** ✅ („a my na peronie od piątku” #32) → cel „rynek” (`macro_topic`), `bullish` — autor stwierdza wzrosty, które przegapił.

## 5. Flaga „trudny”

Zaznacz, gdy po zastosowaniu reguł nadal masz wątpliwość. Takie posty nie liczą się do porównania modeli, ale są materiałem na kolejne reguły.

## 6. Korekta rundy 1

Zaimportuj `labels-2026-09-28.json` w arkuszu (przycisk „Importuj”) i popraw głównie cele (3.1–3.4: podmiot zamiast tezy, cele bez stanowiska, hashtagi, broker w tle) oraz trafność postów objętych regułami 2.1–2.4. Szacunkowo 20–30 min.

## 7. Kandydaci na zmiany kontraktu i promptu

Reguły, których kontrakt V1 dziś nie rozstrzyga wprost (do procedury §14 po rundzie 2): 2.1–2.4 (trafność memów, narzekań, linków, pytań o wydarzenia), 3.3 (każda nazwana spółka to cel), 3.4 (hashtag jako anchor), 4.1 (dwustronny scenariusz = `unclear`), 4.4 (pytanie retoryczne), 4.6 (slang „peron”). Słownik slangu (3.5) to materiał do promptu, nie do kontraktu.
