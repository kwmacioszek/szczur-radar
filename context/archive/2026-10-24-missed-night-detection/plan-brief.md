# S-13: dni bez danych w /admin — Plan Brief

> Full plan: `context/changes/missed-night-detection/plan.md`
> Research: `context/changes/missed-night-detection/research.md`

## What & Why

Admin widzi w `/admin` dni z ostatnich 14 dni (do D-2), których nie pobrał żaden udany przebieg zakresu, osobno dla każdego źródła. Jednym kliknięciem wpisuje taki zakres do formularza ręcznego przebiegu.

Alarm o złej nocy już działa: daje go F-04 (healthchecks.io → Telegram). Brakowało tylko informacji, który dzień wypadł, i szybkiej drogi do jego uzupełnienia (audyt C3).

## Starting Point

Nocny przebieg pobiera D-2…D-1, więc każdy dzień ma dwie noce. Dziura powstaje po dwóch złych nocach z rzędu. Dziś nic jej nie pokazuje.

Admin może uruchomić zakres ręcznie (do 7 dni), ale sam musi wywnioskować z alarmów, które dni wpisać. Dane do policzenia pokrycia (`pipeline_runs`: status, zakres, `sources[]`) już są.

## Desired End State

W `/admin`, pod „Nowy przebieg”, jest sekcja „Dni bez danych”. Pokazuje zakresy dni bez danych z brakującymi źródłami i przy każdym przycisk „Wpisz do formularza”. Gdy dziur nie ma, mówi o tym razem z sprawdzanym oknem.

Przykład: dziś 2026-10-24, Wykop pokryty 9–18 i 21–22 października → zakres 19–20 października · Wykop, a przycisk wpisuje 2026-10-19…2026-10-20 i źródło Wykop.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Pokazać czy uzupełniać | Tylko pokazać w `/admin` (wariant B) | Samo-naprawa oszczędza jedno kliknięcie, a dokłada ryzyko limitu 90 min crona i fałszywego alarmu | Research |
| Nowy alarm poza aplikacją | Nie | Każdą złą noc crona już zgłasza F-04 | Research |
| Definicja dnia bez danych | Brak udanego przebiegu RANGE (`succeeded`/`partial`) nad dniem | Odpowiada na pytanie „czy noc go pobrała”, bez migracji i bez zależności od mapowania dat źródła | Plan |
| Okno | D-15…D-2 (14 dni), najwcześniej od pierwszego pokrytego dnia źródła | Mieści się w sprawdzonej głębokości Wykopu. D-1 ma jeszcze swoją noc. Czas sprzed startu nie udaje dziury | Plan |
| Źródła | Pokrycie osobno na każde skonfigurowane źródło | Po dodaniu Reddita API się nie zmienia, a udany Wykop nie ukryje dziury innego źródła | Plan |
| Zakresy do formularza | Front grupuje kolejne dni o tych samych źródłach, po ≤ 7 dni | 7 dni to reguła startu przebiegu, nie kontraktu dni bez danych | Plan |
| Dodatki | Bez dzwonka i NAS → Drive (statusy F-04 poprawił już #101) | „Zróbmy prostą zmianę” | Plan |

## Scope

**In scope:**
- trasa `GET /api/admin/pipeline-runs/missing-days` z czystą funkcją pokrycia i testami;
- sekcja „Dni bez danych” w `/admin` z wypełnianiem formularza, testami, trybem atrapy i zrzutami;
- runbook S-13 w `deploy-plan.md`, zdanie w `REPOSITORY_GUIDELINES.md`;
- scenariusz w PR env.

**Out of scope:**
- automatyczne uzupełnianie;
- nowy alarm;
- dzwonek;
- etykieta feedu;
- definicja „brak postów w dniu”;
- dni starsze niż 14;
- NAS → Google Drive;
- migracje, crony, IaC, CI.

## Architecture / Approach

Kontroler bierze „dziś” (Warszawa) z `Clock`, źródła z `SourceIngest.states()`, a udane przebiegi RANGE i pierwszy pokryty dzień każdego źródła z `PipelineRunRepository`. Wszystko podaje czystej funkcji `MissingDays`, która zwraca okno i listę `{day, sources}`. Front pobiera ją `fetchMissingDays()`, grupuje w zakresy (`src/missingDays.ts`) i wypełnia istniejące `from`/`to`/`chosenSources` formularza. Przebieg uruchamia admin jak dotąd.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. API — dni bez danych | Trasa admina z regułami pokrycia przypiętymi testami | Granice dni (D-2, zmiana czasu 2026-10-25, okno od pierwszego dnia źródła) |
| 2. Web — sekcja w /admin | Lista zakresów z „Wpisz do formularza”, stany, zrzuty | Kontrakt stylu (`CLEANED`) i fokus po wypełnieniu |
| 3. Dokumenty i PR env | Runbook S-13 i scenariusz D-3 → D-2 na prawdziwym Wykopie | Dwa krótkie przebiegi z LLM w PR env; nie pushować w trakcie `RUNNING` |

**Prerequisites:**
- F-04 na produkcji (zrobione, issue #66);
- gałąź `feature/missed-night-detection` na świeżym `origin/main` (przewinięta 2026-10-24 do `0bea22f`);
- issue #72.

**Estimated effort:** ~1–2 sesje w 3 fazach.

## Open Risks & Assumptions

- Po jednej złej nocy D-2 jest przez ok. 2–3 h po północy na liście, choć noc go zaraz pobierze. Sekcja mówi o tym zdaniem pod listą.
- Dzień pobrany, ale z bardzo małą liczbą postów, nie trafia na listę (definicja z przebiegów, nie z postów).
- Źródło, które nigdy nie miało udanego przebiegu, nie daje dni. Jego awarie zgłasza F-04 i dzwonek.

## Success Criteria (Summary)

- Po dwóch złych nocach admin widzi w `/admin` brakujący dzień z jego źródłem i uzupełnia go dwoma kliknięciami.
- Gdy dziur nie ma, sekcja to mówi i pokazuje sprawdzane okno.
- Testy przypinają granice (D-2, zmiana czasu, okno), a scenariusz w PR env potwierdza całość na prawdziwym Wykopie.
