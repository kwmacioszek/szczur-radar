## Sprawdzenie klasyfikatora języka

Agent przeczytał 40 losowych tez z 2191 (ziarno 20261031) i ocenił je tak samo jak klasyfikator w 40 przypadkach na 40: 7 po polsku, 33 po angielsku. Jedna z tez to same liczby i skróty, więc jej język jest umowny. To sprawdzenie jednej osoby, a próba ma mało tez po polsku; może przeoczyć krótkie polskie tezy bez znaków diakrytycznych.

## Uwagi do liczb

Te liczby policzył agent z plików `sample/`, poza generatorem raportu.

- **Wolumen może być zaniżony.** Zdjęte posty to 393 z 789 pobranych (r/stocks 262 z 383). Udział jest podobny dla postów sprzed 2 i 8 dni, ale pomiar czytał je najwcześniej po dwóch dobach, a produkcja czyta świeższe i może ocenić post, który zostanie zdjęty później. Górna granica przy ocenie wszystkich pobranych (112,7 posta dziennie, ten sam koszt posta): Reddit $2,93 / mies. na `flex`, razem z Wykopem $4,67; na `default` razem $9,34.
- **Błędy `D6` to w większości nie zmyślone cytaty.** W 17 z 30 prób z `D6` wszystkie niepasujące cytaty zgadzają się z postem po usunięciu białych znaków i znaczników markdown (`*`, `_`, `` ` ``, `>`, `~`, `\`). W 10 próbach cytat naprawdę różni się od posta, a w 3 ta kontrola nie znalazła niepasującego pola `quote`. Kontrola agenta jest zgrubna i nie zastępuje walidatora.
- **`schema:maxItems` to głównie jedno pole.** 34 z 41 naruszeń dotyczy `targets[].stance.claim_ids` (limit 4), 5 `targets`, 2 `claims[].target_ids`. `schema:maxLength`: 7 `evidence.quote`, 5 `statement`. Jedna próba może mieć kilka naruszeń, więc te liczby są większe niż liczby prób w tabeli klas.
- **`dead` to posty powtarzalnie trudne, nie pech.** Pierwsza próba padła na 53 postach, ponowienia uratowały 43, a 10 padło trzy razy. Przy niezależnych próbach `dead` byłoby ok. 0,2 %, a jest 2,5 %. Te 10 postów padało na `D6`, `schema:maxItems`, `schema:maxLength` lub `D11`; tylko 1 z nich to megawątek.
- **Skutek dla alarmów.** 2,5 % z 56,6 posta dziennie to ok. 1,4 posta `dead` dziennie. Post `dead` daje powiadomienie w aplikacji i alert `RUN_NEEDS_ATTENTION`, więc alarm szedłby zgrubnie w 3 nocach na 4. Na produkcji `dead` pojawia się dopiero przy trzeciej próbie, czyli po około dwóch dobach od pierwszej oceny.
- **Oceny na Wykopie są stabilniejsze.** Nieudane próby na produkcji Wykopa z ostatnich 14 dni: 2,3 % (70 z 3057 ocen z odpowiedzią modelu), tu 17,3 % (81 z 467). Wyjście jest też ok. 3 razy dłuższe (2792 wobec ok. 970 tokenów na próbę).
- **Anonimizacja.** Model widział tekst po podmianie adresów i wzmianek (`<url>`, `@uzytkownikN`), czego na produkcji nie będzie; wpływ uznano za pomijalny.
- **Czas oceny właściciela.** Od otwarcia arkusza do ostatniego eksportu minęły ok. 34 minuty na 28 z 40 postów (ok. 78 tys. znaków), a plan zakładał około godziny. Część postów właściciel przejrzał pobieżnie i uznał oceny za dobre. Wynik 0 pominiętych celów to ocena z podpowiedzią modelu w takim tempie, więc nie dowodzi braku pominięć.
- **Limit długości `body` byłby słabą dźwignią.** Limit 1000 znaków odciąłby 44 % postów, 53 % stanowisk (cele `bullish` i `bearish`), 75 % nieudanych prób i 8 z 10 `dead`. Limit 3000 znaków: 10 % postów, 12 % stanowisk, 22 % nieudanych prób i 2 z 10 `dead`. Powyżej 5000 znaków nie ma żadnego `dead`, a przycinanie gubi wnioski, które w długich postach bywają na końcu.
