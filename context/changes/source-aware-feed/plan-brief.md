# S-29: źródło widoczne w feedzie, świeżość bez kłamstwa — Plan Brief

> Full plan: `context/changes/source-aware-feed/plan.md`

## What & Why

Po S-27 w feedzie mogą być posty z dwóch źródeł, ale użytkownik ich nie odróżnia, a etykieta „Dane zaktualizowane” pokazuje jedną datę z przebiegu. Przebieg tylko z Redditem przesuwa więc też datę Wykopu, choć Wykopu nie odświeżył. Zmiana rozstrzyga destylację Q-02: oznacza pochodzenie posta i liczy świeżość osobno dla każdego źródła.

## Starting Point

`PostItem` (jeden rekord listy i szczegółów) nie ma `source` ani `channel`, choć encja `Post` ma oba pola. `/api/freshness` odpowiada `{lastUpdatedAt}` z reguły bez źródła, a wyniki per źródło czekają w `pipeline_run_sources` od S-20 i są już używane przez „Dni bez danych”.

## Desired End State

Karta w feedzie i strona posta pokazują `Wykop · #gielda` albo `Reddit · r/stocks`. Etykieta mówi `Dane zaktualizowane: Wykop 30.10.2026, 01:12 · Reddit 30.10.2026, 01:12`, a przebieg przesuwa wyłącznie datę źródła, które dało posty. Na produkcji do S-28 widać sam Wykop, z tą samą godziną co dziś.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Świeżość (Q-02) | Data per źródło, zawsze rozpisana | Jedna ścieżka wyświetlania i nigdy nie miesza stanu źródeł, kosztem powtórzonej daty w typową noc. |
| Co przesuwa datę | Tylko wynik `succeeded` w przebiegu `succeeded`/`partial` | Ta sama reguła co „Dni bez danych”, a `empty` i tak oznacza „wymaga uwagi”. |
| Źródła w etykiecie | Tylko te z aktualizacją, z historii przebiegów | Etykieta opisuje to, co jest w feedzie, i nie zależy od konfiguracji. |
| Oznaczenie posta | Źródło i kanał w karcie i szczegółach | Kanał jest już w bazie, a przy kilku subredditach samo źródło nie wystarcza. |
| Filtr i sortowanie | Poza zakresem | FR-008 to nice-to-have i kandydat do M-5. |
| Przedrostki `#`, `r/` | W SPA obok etykiety źródła, z testem na każde źródło | API oddaje dane bez przedrostka, a SPA je podpisuje. |
| Stare pole `lastUpdatedAt` | Usunięte | Dwie reguły świeżości to błąd, który usuwamy. |
| Kontrakt świeżości | Nowy `freshness.json` | Kształt jest zagnieżdżony, a dziś nic nie porównuje obu stron. |

## Scope

**In scope:**

- `source` i `channel` w odpowiedzi posta, w karcie i na stronie posta.
- `/api/freshness` per źródło, etykieta w feedzie, `freshness.json`.
- Atrapa, kitchen sink, zrzuty, PRD z rozstrzygnięciem Q-02, runbook, dowód w PR env.

**Out of scope:**

- Filtr, sortowanie, kanał jako link.
- Język posta (S-28).
- Oznaczenie na admińskiej liście nieudanych postów.
- Włączenie Reddita na produkcji.
- Edycja `context/domain/` (uzgodni ją kolejny przebieg destylacji).

## Architecture / Approach

Bez migracji i bez zmian w pipeline'ie. `PostItem` dostaje dwa pola z encji. `PipelineRunRepository.findLastUpdates()` zastępuje `findLastUpdate` i pyta raz na źródło (wzorzec `findFirstCoveredFrom`): wynik `succeeded`, przebieg `succeeded`/`partial`, najdalszy koniec zakresu, potem najpóźniejszy koniec przebiegu. `FreshnessController` zamienia mapę na listę w kolejności `Source`. SPA czyta `freshness?.sources`, więc w oknie wdrożenia ze starym API etykiety po prostu nie ma, a lista działa.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Post ze źródłem i kanałem | `source`, `channel` od API do karty i strony posta | Niski: czyste dodanie pól; długi kanał w kolumnie 9rem |
| 2. Świeżość per źródło | Nowy kształt `/api/freshness`, etykieta, `freshness.json` | Zmiana kształtu i przepisany `FreshnessIT`; okno wdrożenia |
| 3. Zrzuty, dokumenty i dowód w PR env | Macierz stanów, PRD, runbook, przebieg tylko z Redditem | PR env potrzebuje prawdziwego Wykopu, Reddita i LLM |

**Prerequisites:** S-27 na `main` (78c57ca), `REDDIT_SUBREDDITS` w PR env z repository variable, issue #123.
**Estimated effort:** ~1–2 sesje na 3 fazy.

## Open Risks & Assumptions

- Stary SPA w otwartej karcie pokaże „Dane nie były jeszcze aktualizowane.” do odświeżenia strony. Przyjęte świadomie: kilka zaproszonych osób, krótki czas.
- W typową noc obie daty będą równe, bo oba źródła kończą się w jednym przebiegu. To cena jednej ścieżki wyświetlania, do oceny na zrzutach.
- Źródło bez wyniku `succeeded` w przebiegu `succeeded`/`partial` może mieć posty w feedzie, np. oceniane przez późniejszy RETRY po nieudanym przebiegu. Wtedy nie ma daty w etykiecie, co jest zgodne z regułą i rzadkie.
- S-28 idzie równolegle i też może dotknąć `PostItem` (język), ale konflikt ogranicza się do listy pól.

## Success Criteria (Summary)

- Użytkownik widzi przy każdym poście źródło i kanał, w feedzie i szczegółach.
- Prawdziwy przebieg tylko z Redditem w PR env przesuwa datę Reddita, a daty Wykopu nie.
- Etykieta produkcji po wdrożeniu pokazuje tę samą godzinę z nazwą `Wykop`.
