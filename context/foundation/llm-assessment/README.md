# LLM Assessment

Kanoniczna dokumentacja kontraktu pojedynczej oceny treści przez LLM w Szczur Radarze.

## Status

- kontrakt: **V1 / accepted**
- zakres: pojedynczy post / link / komentarz
- źródła: Wykop, Reddit / Arctic Shift i przyszłe źródła po normalizacji
- rozstrzyga: **PRD Open Question #2 — kształt input/output oceny LLM**
- powiązane wymagania: `FR-004`, `FR-011a`, `FR-011b`

## Czytaj w tej kolejności

1. [`contract-v1.md`](./contract-v1.md) — **SOURCE OF TRUTH**
2. [`examples.md`](./examples.md) — przykłady i edge cases
3. [`agent-implementation.md`](./agent-implementation.md) — zasady implementacyjne i testowe

Jeżeli którykolwiek plik pomocniczy przeczy `contract-v1.md`, obowiązuje `contract-v1.md`.

## Granica odpowiedzialności

```text
source-native RAW
        ↓
Pipeline / source adapter
        ↓
AssessmentInputV1
        ↓
LLM
        ↓
AssessmentOutputV1
        ↓
validation / persistence / downstream logic
```

LLM opisuje **semantykę pojedynczej wypowiedzi**.

LLM nie wylicza końcowego `is_signal`, `is_valuable`, novelty, trendu ani engagement score.

## Najważniejsze decyzje V1

- brak globalnego sentimentu posta;
- stance jest osobny dla każdego targetu;
- brak liczbowych `score` / `confidence`;
- `claims` są jednym źródłem prawdy dla treści wypowiedzi;
- argumenty są zwykłymi claims powiązanymi przez `supported_by_claim_ids`;
- evidence to dosłowne cytaty możliwe do sprawdzenia przez backend;
- canonical entity resolution odbywa się poza LLM assessment;
- engagement nie jest przekazywany do LLM;
- parent context może wyjaśniać odniesienia, ale nie jest wypowiedzią autora analizowanego `item`;
- niepewność jest jawna — model nie zgaduje.

## Zmiany kontraktu

Nie rozszerzaj enumów ani struktury ad hoc w kodzie.

Jeżeli realny przypadek z Wykopu lub Reddita nie mieści się w V1:

1. zachowaj failing fixture;
2. opisz brak kontraktu;
3. zaproponuj minimalną zmianę `contract-v1.md`;
4. dopiero po zaakceptowaniu zmiany aktualizuj schema/code/tests.
