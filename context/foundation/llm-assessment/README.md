# LLM Assessment

Kanoniczna dokumentacja kontraktu pojedynczej oceny treści przez LLM w Szczur Radarze.

## Status

- kontrakt: **V2 / accepted** (zaakceptowany przez użytkownika 2026-10-01; V1 przeniesiony 2026-10-01 do `context/foundation/archive/`)
- zakres: pojedynczy post / link (komentarz i kontekst rodzica w kształcie, poza MVP)
- źródła: Wykop (MVP), Reddit / Arctic Shift i przyszłe źródła po normalizacji
- rozstrzyga: **PRD Open Question #2 — kształt input/output oceny LLM**
- powiązane wymagania: `FR-004`, `FR-011a`, `FR-011b`

## Czytaj w tej kolejności

1. [`contract-v2.md`](./contract-v2.md) — **SOURCE OF TRUTH**
2. [`examples.md`](./examples.md) — przykłady z prawdziwych, zanonimizowanych postów #gielda
3. [`agent-implementation.md`](./agent-implementation.md) — walidacje, testy, zestaw wzorcowy i definicja „gotowe”

Jeżeli którykolwiek plik pomocniczy przeczy `contract-v2.md`, obowiązuje `contract-v2.md`.

## Granica odpowiedzialności

```text
source-native RAW
        ↓
Pipeline / source adapter
        ↓
AssessmentInputV1
        ↓
odsiew wpisów bez treści  ──→  gotowy wynik (źródło: prefilter)
        ↓
LLM (jedno wywołanie)
        ↓
AssessmentOutputV2
        ↓
walidacja / persistence / downstream logic
```

LLM opisuje **semantykę pojedynczej wypowiedzi**: trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności.

LLM nie wylicza końcowego `is_signal`, `is_valuable`, nowości, trendu ani zaangażowania.

## Najważniejsze decyzje V2

- rdzeń oceny: trafność + cele + stanowisko autora wobec każdego celu; brak globalnego sentymentu posta;
- cel to podmiot, nie teza; każda nazwana spółka lub aktywo jest celem; hashtag spółki może być anchorem, tag kanału nigdy;
- próg stanowiska „szeroko + pewność”: kierunek odczytany z sugestii jest przypisywany jako `hedged`;
- scenariusz dwustronny bez preferencji to `unclear`, nie `mixed`;
- jawna tabela `direction` × `assertiveness` × `claim_ids`; stanowisko kierunkowe opiera się na tezie autora;
- każda teza ma dokładnie jeden dosłowny cytat z `item`; brak grafu argumentów (`supported_by_claim_ids` usunięte);
- cytat sprawdzany jako podciąg po normalizacji białych znaków (twarda spacja, łamania linii) — i niczego więcej;
- wpis bez treści dostaje gotowy wynik bez wywołania modelu;
- `statement` w języku posta;
- brak liczbowych `score` / `confidence`; rozpoznawanie spółek poza oceną;
- niepewność jest jawna — model nie zgaduje.

## Zmiany kontraktu

Nie rozszerzaj enumów ani struktury ad hoc w kodzie.

Jeżeli realny przypadek z Wykopu lub Reddita nie mieści się w V2:

1. zachowaj failing fixture;
2. opisz brak kontraktu;
3. zaproponuj minimalną zmianę `contract-v2.md`;
4. dopiero po zaakceptowaniu zmiany aktualizuj schema/code/tests (i instrukcję oznaczania, jeśli zmienia się semantyka).

- [`hardening-sources.md`](./hardening-sources.md) — źródła do toru „LLM hardening” po MVP (ironia, sarkazm, stanowisko); niezweryfikowane.
