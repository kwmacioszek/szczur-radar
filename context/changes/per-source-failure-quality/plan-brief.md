# Jakość ocen per źródło — nieudane próby według klas (S-35) — Plan Brief

> Full plan: `context/changes/per-source-failure-quality/plan.md`
> Dane z produkcji: `context/changes/per-source-failure-quality/evidence/README.md`

## What & Why

Guardrail „jakość danych z LLM musi być wysoka” (R-15) nie ma dziś żadnej miary na produkcji (destylacja #1, FR-033 must-have). S-35 pokazuje operatorowi osobno dla każdego źródła udział nieudanych prób oceny w oknie 7 dni, w podziale na klasy błędu kontraktu, i daje mu alarm ntfy, gdy źródło przejdzie przez swój próg — bez czytania logów.

## Starting Point

Każda próba jest już wierszem `assessments` (`status`, `failure_class`, `result_source`), ale nic nie liczy jej po klasach, a podsumowanie nocy (S-34, ntfy + log crona) podaje tylko liczby ok/failed na noc. Odczyt produkcji z 2026-11-11: okno 7 dni Wykopa 2,0–2,8 %, Reddita 14,6–18,4 %; pojedyncza mała noc Wykopa skacze do 10–11 %.

## Desired End State

Co noc podsumowanie ma linię `… quality 7d (…): failed X.X% of N attempts (klasy…); threshold T%` dla każdego źródła, z `ABOVE THRESHOLD` ponad progiem. W noc przejścia przez próg przychodzi osobne `ASSESSMENT_QUALITY` (priorytet 4). `/admin` ma sekcję „Jakość ocen (7 dni)” z tym samym oknem, klasami, progiem i stanem.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Mianownik | Próby z modelem (`result_source='model'`, ponowienia włącznie, bez prefiltra) | Ta sama definicja co 17,3 % (S-28) i 2,3 % Wykopu, więc punkty odniesienia są porównywalne. |
| Okno | 7 dni kalendarzowych Warszawy `[D−6, D]`, próg sprawdzany przy ≥ 100 próbach | Wygładza małe noce Wykopa; to samo okno dostanie S-36. |
| Dane do progu | Jeden odczyt produkcji tylko do odczytu przez agenta (35 dni, w `evidence/`) | FR-033: „progi ustala plan wycinka na danych z produkcji”. |
| Próg | Na źródło na sumie klas: Wykop 5 %, Reddit 25 %; „ponad” = ostro większy | Na 35 dniach zero fałszywych alarmów, incydent 2026-10-09 (19 %) złapany. |
| Miejsce progu | Stała przy `sources/Source` (`WYKOP(5)`, `REDDIT(25)`), zmiana = PR | Wzór S-32; `pipeline/` nie nazywa źródeł, nowe źródło musi zadeklarować próg. |
| Kanał | Linia jakości w podsumowaniu nocy + osobne `ASSESSMENT_QUALITY` (ntfy, priorytet 4) | Codzienny obraz zostaje cichy, alarm ma własny tytuł i priorytet; kanał gotowy dla S-36. |
| Powtórki alarmu | Tylko przy przejściu: dziś ponad progiem, w oknie poprzedniej nocy (ucięte do `now − 24 h`) nie (albo < 100 prób), liczone z danych | Jeden alarm na epizod, bez zapisanego stanu; trwanie widać w podsumowaniu i `/admin`. |
| Widok | Także sekcja w `/admin` (`GET /api/admin/assessment-quality`) | Decyzja właściciela: obraz jakości również w aplikacji. |

## Scope

**In scope:**
- próg przy `Source`, zapytanie źródło × klasa po oknie, komponent `posts/AssessmentQuality` z regułą stanu i przejścia, indeks V14 `assessments(assessed_at)`
- linie jakości w `NightSummary`, `AlertEvent.ASSESSMENT_QUALITY`, testy API i crona, README crona
- trasa admina, kontrakt SPA (`assessment-quality.json`, `rules.json` `qualityStatus`), sekcja w `AdminView`, mock, zrzuty 7 stanów
- PRD FR-033 (doprecyzowanie z progami), runbook S-35, `pipeline/CLAUDE.md`, `sources/CLAUDE.md`, `REPOSITORY_GUIDELINES.md`, poprawka odnośnika §9 → §12 w roadmapie
- weryfikacja w PR env przez agenta

**Out of scope:** udział tez w innym języku (S-36); miara „posty na pierwszej próbie”; progi per klasa lub względne; zmienna środowiskowa dla progów; alarm co noc ponad progiem; `AttentionReason`, dzwonek, nowa tabela; historia/wykres w `/admin`; cicha zmiana modelu u dostawcy (Parked).

## Architecture / Approach

`posts/AssessmentQuality` liczy okno z jednego zapytania zbiorczego (indeks po `assessed_at`) i stosuje próg z `Source` w liczbach całkowitych (`failed·100 > T·attempts` przy ≥ 100 próbach). Zasila trzy miejsca: `NightSummary` (linie, data nocy z `Clock`), `InternalPipelineRunController` (alarm przy przejściu: okno dziś vs okno wczoraj, obok `NIGHT_SUMMARY`) i `GET /api/admin/assessment-quality` (sekcja w `AdminView`).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Miara i reguła progu | `Source` z progiem, zapytanie, `AssessmentQuality`, V14 | Granice dni Warszawy (25-godzinna doba) i brzegi progu |
| 2. Podsumowanie nocy i alarm | Linie jakości, `ASSESSMENT_QUALITY`, testy crona, docs + PRD | Format przypięty w wielu miejscach naraz (testy API, `test.sh`, README) |
| 3. Sekcja w `/admin` | Trasa, kontrakt SPA, sekcja z 7 stanami | Rejestry kontraktu (`ContractRegistry`, `rules.json`, warianty) |
| 4. Weryfikacja w PR env | CI, trasa i sekcja przez agenta, noc w PR env | Noc w PR env kosztuje prawdziwe wywołania; alarm tam się nie pojawi |

**Prerequisites:** gałąź `feature/per-source-failure-quality` z świeżego `origin/main`, issue S-35; PR env z bazą.
**Estimated effort:** ~3–4 sesje w 4 fazach.

## Open Risks & Assumptions

- Próg Reddita 25 % ma ~7 pkt proc. zapasu nad najgorszym oknem; powolny dryf z 17 % do 24 % przejdzie bez alarmu (widać go w podsumowaniu i `/admin`).
- Przy przejściu tylko jeden alarm: przegapiony nie wraca, trwanie problemu mówi już tylko `ABOVE THRESHOLD` w cichym podsumowaniu.
- Zła noc trzyma okno ponad progiem do 7 dni; po spadku i ponownym wzroście przyjdzie drugi alarm (nowy epizod).
- Utracone podsumowanie nocy (kolejka nie jest trwała) gubi też alarm tej nocy; następnej nocy alarm nie wróci, jeśli wczorajsze okno było już ponad progiem.

## Success Criteria (Summary)

- Operator widzi co noc w ntfy i w `/admin` udział nieudanych prób per źródło i klasa w oknie 7 dni, z progiem i stanem.
- Przejście źródła przez próg daje jedno `ASSESSMENT_QUALITY` o priorytecie 4; na danych z 35 dni byłby to jeden alarm (Wykop 2026-10-13).
- Testy przypinają regułę, okno i format; PR env pokazuje linie i sekcję na żywo.
