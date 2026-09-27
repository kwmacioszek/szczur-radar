# LLM Assessment V1 — Examples

Ten plik jest **pomocniczy**. Normatywnym źródłem prawdy jest [`contract-v1.md`](./contract-v1.md).

Przykłady pokazują oczekiwane zachowanie; nie rozszerzają enumów ani zasad kontraktu.

---

# 1. Teza z argumentem

Input body:

```text
CD Projekt wygląda ciekawie po ostatnich spadkach.
Rynek moim zdaniem za mocno wycenia ryzyko opóźnienia Wiedźmina.
Jeżeli premiera pójdzie zgodnie z planem, obecna wycena może być atrakcyjna.
```

Istotne elementy output:

```json
{
  "relevance": "investment_related",
  "targets": [
    {
      "id": "t1",
      "kind": "company",
      "reference": "explicit",
      "anchor": {
        "document": "item",
        "field": "body",
        "quote": "CD Projekt"
      },
      "stance": {
        "direction": "bullish",
        "assertiveness": "hedged",
        "claim_ids": ["c2"]
      }
    }
  ],
  "claims": [
    {
      "id": "c1",
      "kind": "opinion",
      "attribution": "author",
      "target_ids": ["t1"],
      "statement": "Zdaniem autora rynek nadmiernie wycenia ryzyko opóźnienia Wiedźmina.",
      "evidence": [
        {
          "document": "item",
          "field": "body",
          "quote": "Rynek moim zdaniem za mocno wycenia ryzyko opóźnienia Wiedźmina."
        }
      ],
      "supported_by_claim_ids": []
    },
    {
      "id": "c2",
      "kind": "investment_thesis",
      "attribution": "author",
      "target_ids": ["t1"],
      "statement": "Obecna wycena CD Projektu może być atrakcyjna, jeśli premiera odbędzie się zgodnie z planem.",
      "evidence": [
        {
          "document": "item",
          "field": "body",
          "quote": "Jeżeli premiera pójdzie zgodnie z planem, obecna wycena może być atrakcyjna."
        }
      ],
      "supported_by_claim_ids": ["c1"]
    }
  ]
}
```

---

# 2. Pytanie bez stance

Input:

```text
Dlaczego KGHM dzisiaj tak mocno spada? Coś się wydarzyło?
```

Kluczowe zachowanie:

```json
{
  "relevance": "investment_related",
  "targets": [
    {
      "id": "t1",
      "kind": "company",
      "reference": "explicit",
      "anchor": {
        "document": "item",
        "field": "body",
        "quote": "KGHM"
      },
      "stance": {
        "direction": "not_expressed",
        "assertiveness": null,
        "claim_ids": []
      }
    }
  ],
  "claims": [
    {
      "id": "c1",
      "kind": "question",
      "attribution": "author",
      "target_ids": ["t1"],
      "statement": "Autor pyta o przyczynę dzisiejszego spadku kursu KGHM.",
      "evidence": [
        {
          "document": "item",
          "field": "body",
          "quote": "Dlaczego KGHM dzisiaj tak mocno spada? Coś się wydarzyło?"
        }
      ],
      "supported_by_claim_ids": []
    }
  ]
}
```

Pytanie o spadek nie oznacza automatycznie `bearish`.

---

# 3. Dwa targety, przeciwne stance

Input:

```text
Sprzedaję Allegro, bo nie podoba mi się kierunek marż.
Za to coraz bardziej podoba mi się XTB i dzisiaj zwiększyłem pozycję.
```

Oczekiwane:

```text
Allegro -> bearish
XTB     -> bullish
```

Claims powinny oddzielić:

```text
trade_action Allegro
opinion o marżach Allegro
opinion o XTB
trade_action XTB
```

Sama sprzedaż nie musi zawsze oznaczać bearish; w tym przykładzie dodatkową podstawą stance jest jawna negatywna ocena marż.

---

# 4. Informacja bez opinii

Input:

```text
Spółka podała dziś wyniki.
Przychody wzrosły 18% r/r, EBITDA spadła 4%.
```

Oczekiwane:

```text
relevance = investment_related
target.reference = unresolved
stance.direction = not_expressed
claims.kind = factual_claim
```

`factual_claim` oznacza „tekst przedstawia jako fakt”, a nie „LLM potwierdził prawdziwość”.

---

# 5. Negatywny ton, bullish stance

Input:

```text
Zarząd tej spółki to dramat, ale po spadku o 70% kupuję,
bo wycena jest absurdalnie niska.
```

Oczekiwane:

```text
opinion: negatywna ocena zarządu
investment_thesis / trade_action: pozytywne inwestycyjnie
stance.direction: bullish
```

Nie używamy globalnego sentimentu emocjonalnego.

---

# 6. Mocna teza bez argumentu

Input:

```text
CDR 300 zł do końca roku 🚀
```

Oczekiwane:

```text
investment_thesis
bullish
supported_by_claim_ids = []
```

Brak argumentu pozostaje widoczny strukturalnie. Nie potrzebujemy pola `quality`.

---

# 7. Reported claim ≠ stance autora

Input:

```text
Analitycy twierdzą, że CDR wzrośnie o 30%.
```

Oczekiwane:

```text
claim.kind = factual_claim lub investment_thesis zależnie od zachowanej semantyki wypowiedzi
claim.attribution = reported
```

Samo przytoczenie prognozy nie tworzy automatycznie `bullish` stance autora.

---

# 8. Contextual target

Parent:

```text
Dzisiaj dokupiłem XTB.
```

Item:

```text
Ja też.
```

Target:

```json
{
  "reference": "contextual",
  "anchor": {
    "document": "parent_post",
    "field": "body",
    "quote": "XTB"
  }
}
```

Claim autora musi nadal mieć evidence z `item`, np.:

```json
[
  {
    "document": "item",
    "field": "body",
    "quote": "Ja też."
  },
  {
    "document": "parent_post",
    "field": "body",
    "quote": "Dzisiaj dokupiłem XTB."
  }
]
```

---

# 9. Ambiguous vs unresolved

## Ambiguous

```text
Apple wygląda teraz dobrze.
```

Jeżeli dostarczony kontekst nie rozstrzyga, do czego odnosi się `Apple`:

```text
reference = ambiguous
```

## Unresolved

```text
Ta spółka będzie rosła.
```

bez parent context:

```text
reference = unresolved
stance.direction = bullish
```

Nierozwiązany target nie oznacza automatycznie niejasnego stance.

---

# 10. Sarkazm

Input:

```text
Świetna spółka, naprawdę.
Trzeci profit warning w pół roku.
To na pewno tym razem poleci na księżyc.
```

Jeżeli model prawidłowo rozpoznaje sarkazm, klasyfikuje semantykę bez dodatkowej flagi.

Jeżeli sarkazm może materialnie zmieniać wynik i model nie może go rozstrzygnąć:

```text
possible_sarcasm
```

---

# 11. Treść nieinwestycyjna

Input:

```text
Dzięki za odpowiedź!
```

Minimalny output:

```json
{
  "relevance": "not_investment_related",
  "modifiers": [],
  "targets": [],
  "claims": [],
  "flags": []
}
```

---

# 12. Anti-examples

## Nie rób tego

```json
{
  "sentiment": 0.72,
  "quality": 8.1,
  "is_valuable": true
}
```

Powody:

- miesza różne pojęcia;
- skala jest niejednoznaczna;
- utrudnia reinterpretację;
- nie daje atomowych danych downstream.

## Nie wymyślaj canonical identity

Input:

```text
CDR wygląda dobrze.
```

LLM nie powinien sam dopisywać:

```text
CD Projekt S.A.
WSE:CDR
ISIN
internal entity_id
```

To zadanie resolvera.
