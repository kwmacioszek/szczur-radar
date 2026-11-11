# Post odrzucony przy zapisie nie znika po cichu (S-38) — Plan Brief

> Full plan: `context/changes/post-store-loss-visible/plan.md`

## What & Why

Krok store przebiegu (`PostStore.java:71-78`) traktuje każde naruszenie ograniczenia bazy przy zapisie posta jako „zapisany w międzyczasie przez inny przebieg”. Post odrzucony przez `CHECK`, `NOT NULL` albo błąd danych znika bez logu i licznika, a przebieg może być `succeeded` (destylacja #2, R-06, D-15; issue #148). Dziś nie znamy danych, które to wywołują, ale ryzyko leży w przyszłych ograniczeniach i nowych źródłach.

## Starting Point

Źródła liczą `fetched = posty + skipped` i zapisują to w kroku fetch (przebieg i wiersz źródła). Store dopisuje tylko `new_posts`. Licznika duplikatów nie ma, więc równość „pobrane = nowe + duplikaty + pominięte” jest dziś prawdziwa z definicji. `skipped` > 0 już daje powód `skipped`, czyli dzwonek, `RUN_NEEDS_ATTENTION`, linię w podsumowaniu nocy i „pominięte: N” w SPA.

## Desired End State

Gdy zapis posta się nie uda, store sprawdza jego klucz w bazie: jest, to duplikat, nie ma, to odrzucenie. Odrzucony post:

- jest dopisany do `skipped` przebiegu i swojego źródła;
- dostaje linię WARN z nazwą ograniczenia (bez treści posta);
- trafia do błędu przebiegu (`store rejected N post(s), counted as skipped: …`).

Dzwonek, `RUN_NEEDS_ATTENTION` i podsumowanie nocy pokazują go bez zmian w ich kodzie. Równość per źródło jest w typie kroku (`SourceStored`) i w testach na Postgresie.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Jak liczyć odrzucony post | Jako `skipped` (powód `skipped`, status bez zmian) | Zgodne z regułą „counted as skipped, never thrown” i z S-08; bez migracji, kontraktu i zmian w SPA |
| Wiersz źródła | Dopisany razem z przebiegiem, jednym warunkowym poleceniem (`RunRecorder.storeRejected`) | Równość obowiązuje też per źródło; widać to w „Wynik per źródło” i w podsumowaniu nocy |
| Duplikaty | W `StoredBatch`, w linii INFO `store <source>` i w testach; bez kolumny | Równość sprawdzana w każdym scenariuszu bez migracji i zmiany kontraktu |
| Widoczność | WARN na post + część `store rejected …` w błędzie przebiegu | Admin i operator widzą, że to odrzucenie przez bazę, a nie pominięcie przy mapowaniu |
| Duplikat a odrzucenie | Ponowne odczytanie klucza po nieudanym zapisie | Nie zależy od nazw ograniczeń; działa też dla przyszłych kluczy unikalnych |
| Dowód | Prawdziwe ograniczenia Postgresa: schemat (`PostStoreIT`) i `CHECK` dodane na czas testu (`PipelineRunIT`, PR env) | To dokładnie ryzyko z roadmapy: przyszłe ograniczenie albo nowe źródło |

## Scope

**In scope:**
- `PostStore`: trzy wyniki (nowy, duplikat, odrzucony), WARN i INFO; `RunFlow.StoredBatch`/`SourceStored`/`RejectedPost`
- `RunRecorder.storeRejected`; `RunSequence`: liczniki i błąd przebiegu `źródła | store | ocena`
- Testy: `PostStoreTest`, nowy `PostStoreIT`, `RunSequenceTest`, `RunEndingTest`, `RunRecorderIT`, `PipelineRunIT` (scenariusz i równość)
- Dokumenty: `pipeline/CLAUDE.md`, `sources/CLAUDE.md`, `AttentionReason`, `REPOSITORY_GUIDELINES.md`, doprecyzowanie w PRD, sekcja S-38 w `deploy-plan.md`
- Weryfikacja agenta w PR env z tymczasowym `CHECK`

**Out of scope:**
- Nowy licznik, powód, kolumna albo migracja; zmiany w SPA, `rules.json` i kontrakcie
- Zmiana statusu źródła albo przebiegu przy odrzuceniach; ponowny zapis odrzuconego posta
- Odtwarzanie postów, które mogły zginąć przed S-38; edycja destylacji domeny

## Architecture / Approach

`PostStore` (wynik każdego posta) → `StoredBatch` (liczby per źródło i odrzucone posty) → `RunSequence` (`storeRejected` per źródło, błąd przebiegu) → istniejące liczniki `skipped`, które czytają reguła „wymaga uwagi”, powiadomienia, podsumowanie nocy i SPA.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Store odróżnia duplikat od odrzucenia | Trzy wyniki, WARN/INFO, `PostStoreIT` na Postgresie | Wyjątek przychodzi z commita (batch JDBC), więc nazwę ograniczenia trzeba znaleźć w łańcuchu przyczyn |
| 2. Przebieg liczy i pokazuje odrzucone | `skipped` przebiegu i źródła, błąd przebiegu, scenariusz `PipelineRunIT`, dokumenty | DDL w teście musi po sobie sprzątać, bo scenariusze idą w losowej kolejności |
| 3. Weryfikacja w PR env (agent) | Odrzucenie na wdrożonym obrazie z prawdziwym Redditem, dowody w #148 | `CHECK` musi łapać tylko nowe posty (`created_at < T`), żeby nie psuć zmian starych wierszy |

**Prerequisites:** brak (S-38 ma status ready); issue #148; gałąź `feature/post-store-loss-visible` z `origin/main`.
**Estimated effort:** ~1–2 sesje w 3 fazach; kod w czterech klasach, większość pracy w testach.

## Open Risks & Assumptions

- U+0000 nie dociera do bazy: `Post` usuwa je z pól tekstowych, a w `sourceItemId` wywraca się wcześniej odczyt kluczy (głośno). Przyczynę bez nazwy ograniczenia (`SQLState`) przypina więc test jednostkowy, a nie `PostStoreIT` (przegląd planu, F1).
- Źródło, którego wszystkie posty baza odrzuciła, zostaje `succeeded`, więc „Dni bez danych” liczą ten dzień jako pokryty. To przyjęty koszt.
- Przebieg w PR env kosztuje prawdziwe wywołania LLM dla jednego dnia Reddita.

## Success Criteria (Summary)

- Post odrzucony przez ograniczenie bazy jest policzony jako pominięty, daje powód „wymaga uwagi” i nazwany w błędzie przebiegu, zamiast znikać.
- Dla każdego przebiegu i źródła pobrane = nowe + duplikaty + pominięte, sprawdzone na Postgresie i w PR env.
- `./mvnw -B verify` i `npm test` zielone; SPA i kontrakt bez zmian.
