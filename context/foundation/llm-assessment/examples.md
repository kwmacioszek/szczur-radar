# LLM Assessment V2 — Examples

Ten plik jest **pomocniczy**. Normatywnym źródłem prawdy jest [`contract-v2.md`](./contract-v2.md); przykłady nie rozszerzają enumów ani reguł kontraktu.

Wszystkie wejścia to **prawdziwe, zanonimizowane posty #gielda** z pilotaży (`context/changes/assessment-contract-harness/pilot/`): nicki → `@uzytkownikN`, adresy → `<url>`, białe znaki bez zmian (twarda spacja zapisana w JSON jako ` `). Każde wyjście jest kompletnym `AssessmentOutputV2` zgodnym z kontraktem: cytaty są podciągami wskazanych pól po normalizacji białych znaków (§10), referencje są spójne (§12). Etykiety rundy 1 (`labels-2026-09-28.json`) były oznaczane bez instrukcji i ostrożniej niż próg V2 — tam, gdzie się różnią, obowiązuje ten plik.

Przykłady są danymi testowymi: kompletne wyjścia z sekcji 1–4 i 6–7 muszą przechodzić walidator bez naruszeń; przykład 5 sprawdza odsiew.

---

# 1. Slang jako anchor

Źródło: `labeling-set-2026-09-28.json`, `entry-87765763` (#58).

Wejście:

```json
{
  "schema_version": "AssessmentInputV1",
  "item": {
    "item_type": "post",
    "published_at": "2026-09-28T16:36:16+02:00",
    "channels": ["gielda"],
    "title": null,
    "body": "to koniec dla pamięciorzy \n#gielda "
  },
  "context": {"parent_post": null, "parent_comment": null}
}
```

Wyjście:

```json
{
  "relevance": "investment_related",
  "modifiers": [],
  "targets": [
    {
      "id": "t1",
      "kind": "sector",
      "reference": "explicit",
      "anchor": {"document": "item", "field": "body", "quote": "pamięciorzy"},
      "stance": {"direction": "bearish", "assertiveness": "unhedged", "claim_ids": ["c1"]}
    }
  ],
  "claims": [
    {
      "id": "c1",
      "kind": "investment_thesis",
      "attribution": "author",
      "target_ids": ["t1"],
      "statement": "Autor uważa, że to koniec dla producentów pamięci.",
      "evidence": {"document": "item", "field": "body", "quote": "to koniec dla pamięciorzy"}
    }
  ],
  "flags": []
}
```

Dlaczego: „pamięciorze” to slang na producentów pamięci — cel `sector`; anchor zostaje w oryginale i w tej odmianie, w jakiej stoi w tekście (§6.2, §6.5). `#gielda` to tag kanału, nigdy cel (§6.4). Stwierdzenie wprost → `unhedged`.

---

# 2. Twarda spacja w cytacie

Źródło: `wykop-gielda-sample-2026-09-29.json`, `gielda-2026-09-29-010`.

Wejście (pole `body` zawiera twardą spację U+00A0 między „myślałem” a „że”):

```json
{
  "schema_version": "AssessmentInputV1",
  "item": {
    "item_type": "post",
    "published_at": "2026-09-29T21:00:59+02:00",
    "channels": ["gielda"],
    "title": null,
    "body": "czy mi sie wydaje czy micron zachowuje sie idealnie przed wynikami tak zeby w------c w kosmos? szarpniecia małe, myślałem że realizacja zyskow bardziej p--------e\n\n#gielda "
  },
  "context": {"parent_post": null, "parent_comment": null}
}
```

Wyjście:

```json
{
  "relevance": "investment_related",
  "modifiers": [],
  "targets": [
    {
      "id": "t1",
      "kind": "company",
      "reference": "explicit",
      "anchor": {"document": "item", "field": "body", "quote": "micron"},
      "stance": {"direction": "bullish", "assertiveness": "hedged", "claim_ids": ["c1"]}
    }
  ],
  "claims": [
    {
      "id": "c1",
      "kind": "investment_thesis",
      "attribution": "author",
      "target_ids": ["t1"],
      "statement": "Autor ma wrażenie, że Micron zachowuje się przed wynikami tak, jakby miał mocno wzrosnąć.",
      "evidence": {"document": "item", "field": "body", "quote": "czy mi sie wydaje czy micron zachowuje sie idealnie przed wynikami tak zeby w------c w kosmos?"}
    },
    {
      "id": "c2",
      "kind": "opinion",
      "attribution": "author",
      "target_ids": ["t1"],
      "statement": "Autor zauważa, że wahania kursu są małe, a realizacja zysków słabsza, niż się spodziewał.",
      "evidence": {"document": "item", "field": "body", "quote": "szarpniecia małe, myślałem że realizacja zyskow bardziej p--------e"}
    }
  ],
  "flags": []
}
```

Dlaczego: cytat `c2` ma zwykłą spację tam, gdzie pole ma twardą spację — po normalizacji białych znaków (§10) obie strony są równe, więc cytat przechodzi. W V1 ten sam cytat był odrzucany jako `fabricated_evidence` (pilot 1). Literówki („sie”, „zyskow”) i słowa ocenzurowane przez Wykop („w------c”) zostają w cytacie bez zmian. Pytanie retoryczne z oczekiwaniem wzrostu → `bullish` (§7.1.4), osłabione („czy mi sie wydaje”) → `hedged`.

---

# 3. Mem bez aktywa

Źródło: `labeling-set-2026-09-28.json`, `entry-87763101` (#68).

Wejście:

```json
{
  "schema_version": "AssessmentInputV1",
  "item": {
    "item_type": "post",
    "published_at": "2026-09-28T10:33:56+02:00",
    "channels": ["gielda"],
    "title": null,
    "body": "#gielda #gieldowegownowpisy\n\nPamietajcie, że mechanizm wygląda tak:\n-Jak rośnie to dorzucamy kasę \n-Jak spada to usredniamy\n-Jak spada mocniej, to pracujemy więcej i usredniamy bardziej \n-Jak odbija, to dalej usredniamy, tym razem w górę, przez chwilę ciesząc się zielenią. \n-nigdy nie wyciągamy kasy i umieramy biedni, przekazując dzieciom, żeby usredniali to co dostaną w spadku ( ͡º ͜ʖ͡º)"
  },
  "context": {"parent_post": null, "parent_comment": null}
}
```

Wyjście:

```json
{
  "relevance": "not_investment_related",
  "modifiers": ["humor"],
  "targets": [],
  "claims": [],
  "flags": []
}
```

Dlaczego: żart bez konkretnego aktywa i bez kierunku → `not_investment_related` (§5.1), więc `targets = []` i `claims = []` (§4, reguła D15). Modyfikator `humor` jest dozwolony przy każdej trafności.

---

# 4. Sam link

Źródło: `labeling-set-2026-09-28.json`, `entry-87764313` (#42).

Wejście:

```json
{
  "schema_version": "AssessmentInputV1",
  "item": {
    "item_type": "post",
    "published_at": "2026-09-28T13:25:46+02:00",
    "channels": ["gielda"],
    "title": null,
    "body": "#gielda śledzenie przepływów ropy\n<url>"
  },
  "context": {"parent_post": null, "parent_comment": null}
}
```

Wyjście:

```json
{
  "relevance": "unclear",
  "modifiers": [],
  "targets": [
    {
      "id": "t1",
      "kind": "asset",
      "reference": "explicit",
      "anchor": {"document": "item", "field": "body", "quote": "ropy"},
      "stance": {"direction": "not_expressed", "assertiveness": null, "claim_ids": []}
    }
  ],
  "claims": [],
  "flags": ["missing_context"]
}
```

Dlaczego: link z komentarzem bez kierunku → `unclear`, bo sens jest w linku, którego model nie widzi (§5.3); `missing_context` to jawnie zaznacza. Nazwane aktywo („ropy”) pozostaje celem bez stanowiska (§6.3) — filtr po nazwie je znajdzie, filtr po stanowisku nie. `not_expressed` ⇒ `assertiveness = null` i `claim_ids = []` (§7.2); wpis nie ma tezy, więc `claims = []`.

---

# 5. Wpis bez treści (wynik bez wywołania modelu)

Źródło: `wykop-gielda-sample-2026-09-29.json`, `gielda-2026-09-29-017` (wpis z osadzonym linkiem).

Wejście:

```json
{
  "schema_version": "AssessmentInputV1",
  "item": {
    "item_type": "post",
    "published_at": "2026-09-29T20:35:22+02:00",
    "channels": ["gielda"],
    "title": null,
    "body": "#gielda <url>"
  },
  "context": {"parent_post": null, "parent_comment": null}
}
```

Odsiew (§11): po usunięciu tagu `#gielda` i znacznika `<url>` oraz obcięciu białych znaków zostaje 0 znaków (< 3) → wpis „bez treści”, model nie jest wywoływany.

Wyjście (gotowe, z backendu):

```json
{
  "relevance": "unclear",
  "modifiers": [],
  "targets": [],
  "claims": [],
  "flags": ["insufficient_content"]
}
```

Pochodzenie oceny: źródło `prefilter`, bez dostawcy, modelu i tokenów (§11, §15).

---

# 6. Scenariusz dwustronny

Źródło: `labeling-set-2026-09-28.json`, `entry-87769877` (#54).

Wejście:

```json
{
  "schema_version": "AssessmentInputV1",
  "item": {
    "item_type": "post",
    "published_at": "2026-09-28T21:14:48+02:00",
    "channels": ["gielda"],
    "title": null,
    "body": "Albo indeks w końcu poleci w kibel albo czeka nas pompa stulecia\n\n#gielda "
  },
  "context": {"parent_post": null, "parent_comment": null}
}
```

Wyjście:

```json
{
  "relevance": "investment_related",
  "modifiers": [],
  "targets": [
    {
      "id": "t1",
      "kind": "asset",
      "reference": "explicit",
      "anchor": {"document": "item", "field": "body", "quote": "indeks"},
      "stance": {"direction": "unclear", "assertiveness": null, "claim_ids": ["c1"]}
    }
  ],
  "claims": [
    {
      "id": "c1",
      "kind": "investment_thesis",
      "attribution": "author",
      "target_ids": ["t1"],
      "statement": "Autor uważa, że indeks albo w końcu mocno spadnie, albo czeka go bardzo silny wzrost.",
      "evidence": {"document": "item", "field": "body", "quote": "Albo indeks w końcu poleci w kibel albo czeka nas pompa stulecia"}
    }
  ],
  "flags": []
}
```

Dlaczego: autor przedstawia dwa skrajne scenariusze bez preferencji → `unclear`, nie `mixed` (§7.1.1). „Kibel” i „pompa” to slang ruchu ceny; ruch dotyczy nazwanego celu, więc nie tworzą osobnego celu „rynek” (§6.5a) — celem jest indeks. Przy `unclear` `assertiveness = null`, a `claim_ids` są dozwolone (§7.2) i tu wskazują tezę, z której wynika ocena.

---

# 7. Stanowisko odczytane z sugestii

Źródło: `labeling-set-2026-09-28.json`, `entry-87767889` (#18).

Wejście:

```json
{
  "schema_version": "AssessmentInputV1",
  "item": {
    "item_type": "post",
    "published_at": "2026-09-28T20:22:50+02:00",
    "channels": ["gielda"],
    "title": null,
    "body": "#gielda Banka na cyber security "
  },
  "context": {"parent_post": null, "parent_comment": null}
}
```

Wyjście:

```json
{
  "relevance": "investment_related",
  "modifiers": [],
  "targets": [
    {
      "id": "t1",
      "kind": "sector",
      "reference": "explicit",
      "anchor": {"document": "item", "field": "body", "quote": "cyber security"},
      "stance": {"direction": "bearish", "assertiveness": "hedged", "claim_ids": ["c1"]}
    }
  ],
  "claims": [
    {
      "id": "c1",
      "kind": "investment_thesis",
      "attribution": "author",
      "target_ids": ["t1"],
      "statement": "Autor sugeruje, że na spółkach cyberbezpieczeństwa jest bańka.",
      "evidence": {"document": "item", "field": "body", "quote": "Banka na cyber security"}
    }
  ],
  "flags": []
}
```

Dlaczego: próg „szeroko + pewność” (§7.1.0) — „bańka na X” rozsądny czytelnik odczyta jako stanowisko niedźwiedzie, więc `bearish`; kierunek odczytany z sugestii, a nie wyrażony wprost → `hedged`. Cytat zachowuje pisownię autora („Banka” bez „ń”). W rundzie 1 ten post nie miał celu („za mało info, sama sugestia”) — to różnica progu, nie błąd modelu.

---

# 8. Anty-przykłady

## Nie zwracaj ocen liczbowych ani decyzji o sygnale

```json
{
  "sentiment": 0.72,
  "quality": 8.1,
  "is_valuable": true
}
```

Obce pola → `schema_violation`; ponadto zakazane przez §14 (pkt 1, 9).

## Nie wymyślaj tożsamości encji

Dla `wykop-gielda-sample-2026-09-29.json` / `gielda-2026-09-29-025` (`"#gielda akcje makdolana może w końcu odbija "`) anchor to `"makdolana"`. Model nie dopisuje w anchorze ani w `statement` „McDonald's Corp.”, „NYSE:MCD”, ISIN ani identyfikatora encji — to zadanie resolvera poza MVP (§14 pkt 5–6, §15).

## Nie poprawiaj cytatu

Dla przykładu 6 cytat `"Albo indeks w końcu poleci w kibel [...] pompa stulecia"` (skrót) albo `"indeksu"` (zmieniona odmiana) jest naruszeniem `fabricated_evidence` (§10, reguła D6), mimo że sens się zgadza.
