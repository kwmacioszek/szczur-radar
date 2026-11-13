# Dane z produkcji do progów S-36 (2026-11-12)

Trzy odczyty tylko do odczytu, wykonane przez agenta podczas `/10x-plan` za zgodą właściciela (procedura S-35):

```sh
RAILWAY_PROJECT_ID=<project> railway ssh -s postgres -e production -- \
  "echo <base64 pliku SQL> | base64 -d | psql -U postgres -d railway --csv -v ON_ERROR_STOP=1"
```

Każdy plik SQL zaczyna się od `SET default_transaction_read_only = on;`. Baza: `UTF8`, `en_US.utf8`.

## Co policzono

Udane próby z wywołaniem modelu (`status = 'ok'`, `result_source = 'model'`) z ostatnich 35 dni, tezy z `output->'claims'->i->>'statement'`. Każda teza dostała jedną z trzech klas:

- **`pl`** — ma polską literę (`ąćęłńóśźż`, obie wielkości) albo co najmniej 2 polskie słowa funkcyjne (`nie, jest, oraz, ale, czy, jednak, dla, przez, jako, lub`; w SQL bez `że, się, będzie, które`, bo te mają polską literę i reguła litery już je łapie);
- **`en`** — nie jest `pl` i ma co najmniej 2 angielskie słowa funkcyjne z listy 34 słów: `the, is, are, were, of, and, that, this, will, with, for, has, have, be, not, from, its, their, than, which, would, could, should, it, an, or, in, at, if, because, about, after, before, while`. Poza listą celowo zostały słowa, które są też polskie: `to, on, by, do, we, a, i, no, as, was, but`;
- **`und`** — nierozstrzygnięta (żaden z dowodów).

Teza jest „w innym języku niż post”, gdy jej klasa to drugi język niż język posta: na Wykopie `en`, na Reddicie `pl`. Nierozstrzygnięta nie jest naruszeniem.

Słowa w SQL to ciągi `a–z` po `lower()` (podział na `[^a-z]+`). Kod produkcyjny dzieli na ciągi liter Unicode (`\p{L}+`), więc wynik różni się tylko wtedy, gdy słowo funkcyjne sąsiaduje z literą spoza ASCII — a wtedy polska litera i tak rozstrzyga klasę.

## Pliki (same liczby, bez treści postów i tez)

- `prod-statement-language-2026-11-12.csv` — na dzień warszawski i źródło: posty z udaną oceną (`posts`), z tezami (`posts_with_theses`), tezy (`theses`) i ich klasy (`pl`, `en`, `und`), tezy `pl` z co najmniej 2 angielskimi słowami (`pl_with_en_words`), nierozstrzygnięte z jednym słowem (`und_one_en_word`, `und_one_pl_word`), posty z co najmniej jedną tezą w innym języku (`posts_with_other`).
- `prod-post-vs-statement-language-2026-11-12.csv` — na dzień i źródło: tezy według klasy tekstu posta (`post_cls`, ta sama reguła na `title + body`) i klasy tezy (`lang`), oraz posty według klasy tekstu.

Dzień 2026-11-12 jest niepełny (odczyt rano, po nocnym przebiegu).

## Wyniki

| Źródło | Okres | Tezy | W innym języku | Nierozstrzygnięte | Posty z tezą w innym języku |
|---|---|---|---|---|---|
| Wykop | 2026-10-09…2026-11-12 | 12 327 | 0,32 % (40) | 16,3 % (2 011) | 0,33 % (23 z 6 947 postów z tezami) |
| Reddit | 2026-11-01…2026-11-12 | 4 824 | 16,98 % (819) | 17,5 % (843) | 18,78 % (154 z 820) |

Okno kroczące 7 dni kalendarza warszawskiego `[D−6, D]`, tylko okna z co najmniej 500 tezami:

| Źródło | Okna | Tezy w oknie | Udział w innym języku |
|---|---|---|---|
| Wykop | 30 (od 2026-10-14) | 994–3 916 | 0,09–0,86 % |
| Reddit | 11 (od 2026-11-02) | 891–3 182 | 14,01–20,76 % |

- Maksimum Wykopu (0,86 %, okno 2026-10-29) to 14 angielskich tez jednego dnia z 2–3 postów.
- Progi planu (Wykop 2 %, Reddit 30 %, minimum 500 tez, alarm przy przejściu) dają na tych danych zero alarmów.
- Reguła S-28 w niezmienionej postaci (wszystko, co nie `pl`, jest angielskie) dałaby na Wykopie 16,6 % „naruszeń” (`en` + `und`), prawie w całości fałszywych.

## Język tekstu posta a język źródła

Ta sama reguła na `title + body` posta: na Wykopie 43 posty z 9 331 (0,5 %) mają tekst po angielsku (wklejone wyniki spółek, depesze), na Reddicie 1 z 847 po polsku.

| Źródło | Tezy na postach z tekstem w drugim języku | W języku tekstu posta | W języku źródła | Nierozstrzygnięte |
|---|---|---|---|---|
| Wykop (posty `en`) | 62 | 28 | 22 | 12 |
| Reddit (posty `pl`) | 9 | 7 | 0 | 2 |

Miara z językiem ze źródła liczy więc na Wykopie 28 poprawnych angielskich tez jako naruszenia i pomija 22 polskie tezy na angielskich postach. Różnica w udziale: 0,32 % wobec 0,28 % przy języku wykrytym z tekstu (Reddit 16,98 % wobec 16,83 %). Właściciel wybrał język ze źródła (2026-11-12).

## Ręczne sprawdzenie reguły

Agent przeczytał 145 tez (próbka deterministyczna po `md5`, zapisana tylko poza repo) i porównał klasę z własną oceną:

| Źródło / klasa | Tezy | Zgodne | Uwagi |
|---|---|---|---|
| Wykop `en` (wszystkie) | 40 | 40 | każda po angielsku |
| Wykop `und` | 30 | — | 29 po polsku (krótkie, bez polskich znaków), 1 po angielsku (przeoczone naruszenie) |
| Wykop `pl` | 10 | 10 | |
| Reddit `pl` | 25 | 24 | 1 po hiszpańsku (litera `ó`); naruszeniem jest i tak |
| Reddit `und` | 30 | — | 29 po angielsku, 1 po polsku (przeoczone naruszenie) |
| Reddit `en` | 10 | 10 | |

To sprawdzenie jednej osoby. Klasy `pl` i `en` zgadzają się w 84 przypadkach na 85. W klasie nierozstrzygniętej 2 tezy na 60 to przeoczone naruszenia, więc miara jest lekko zaniżona, a nie zawyżona.
