# F8f — niewidoczne znaki a §10 kontraktu (faza 1)

Data: 2026-10-06. Pozycja długu F-02: `context/archive/2026-09-27-assessment-contract-harness/follow-ups/review-fixes.md` (F8f).

## Pytanie

Czy U+200B (zero width space), U+00AD (soft hyphen) i U+FEFF (BOM / zero width no-break space) występują w danych Wykopu? §10 kontraktu V2 ich nie normalizuje („Znaki niebędące białymi (np. U+200B) zostają”), więc model, który usunie taki znak z cytatu, oblałby D6 (`fabricated_evidence`).

## Sprawdzenie

Skrypt przeszedł rekurencyjnie każdy string w plikach JSON (po zdekodowaniu, więc także znaki zapisane jako `\uXXXX`) oraz surowy tekst każdego pliku.

| Zbiór | Zawartość | U+200B | U+00AD | U+FEFF |
|---|---|---|---|---|
| `szczur-radar-api/src/test/resources/fixtures/wykop/` | 4 pliki, 18 elementów Wykopu (wpisy i znaleziska) | 0 | 0 | 0 |
| `szczur-radar-api/src/test/resources/fixtures/assessor/` (`gold/`, `real_posts.json`) | 120 postów zestawu wzorcowego + prawdziwe posty kontraktu | 0 | 0 | 0 |
| `context/archive/2026-09-27-assessment-contract-harness/pilot/` | próbka #gielda z 2026-09-29 (100 elementów), zestaw do oznaczania z 2026-09-28 (70), etykiety, 15 przebiegów pilotażowych | 0 w danych Wykopu | 0 | 0 |

Jedyne trafienie: jeden U+200B w poście z **Reddita** (`pilot/runs/pilot5-reddit-*.json`, `posts[126].input.item.body`, „…Gross Profit​. …”). Reddit jest poza S-02 (PRD: Wykop w MVP, Reddit później).

## Wniosek

W danych Wykopu (ok. 300 elementów z trzech niezależnych pobrań) tych znaków nie ma. Zgodnie z planem (faza 1, F8f) **bez zmian w kodzie i kontrakcie**. Procedura §16 (failing fixture → zmiana kontraktu → kod) zostaje na chwilę, gdy taki znak pojawi się w prawdziwym poście:

- przy dodaniu Reddita jako źródła (znany przypadek U+200B powyżej);
- przy ocenie `fabricated_evidence` z prawdziwego przebiegu S-02, której przyczyną jest niewidoczny znak (lista nieudanych w `/admin` pokazuje klasę błędu i naruszenia).

Status: zaakceptowane przez użytkownika 2026-10-06 (Progress 1.3).
