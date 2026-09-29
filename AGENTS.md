@REPOSITORY_GUIDELINES.md

<!-- BEGIN @przeprogramowani/10x-cli -->

## Zestaw narzędzi AI 10xDevs — Moduł 2, Lekcja 4

Przygotuj się na trudniejszy strumień implementacji z **łańcuchem planowania opartym na badaniach**:

```
internal research (/10x-research) + external research (exa.ai, Context7) -> /10x-plan -> /10x-implement -> success
```

Lekcja koncentruje się na odróżnianiu badań wewnętrznych od zewnętrznych oraz wykorzystywaniu dowodów do uzasadniania decyzji planistycznych.

### Router zadań — od czego zacząć

| Skill | Użyj, gdy |
| --- | --- |
| **Badania wewnętrzne (temat lekcji)** | |
| `/10x-research <change-id>` | Potrzebujesz dowodów z istniejącego codebase’u — wzorców, konwencji, punktów integracji lub istniejących implementacji. Uruchamia równoległych sub-agentów w repozytorium i zapisuje ustrukturyzowane ustalenia w `research.md`. |
| **Badania zewnętrzne (temat lekcji)** | |
| exa.ai | Potrzebujesz wyszukiwania w sieci natywnie wspieranego przez AI do porównań bibliotek, najlepszych praktyk lub kontekstu ekosystemu, na które codebase nie potrafi odpowiedzieć. |
| Context7 (`resolve-library-id` → `get-library-docs`) | Potrzebujesz aktualnej, bieżącej dokumentacji dla konkretnej biblioteki lub frameworka. Najpierw rozwiązuje ID biblioteki, a następnie pobiera odpowiednie strony dokumentacji. |
| **Koło zapasowe do ramowania problemu** | |
| `/10x-frame <change-id>` | Plan nie może się ustabilizować, plan nie daje oczekiwanych wyników albo uporczywy dryf stale psuje implementację. Użyj jako wyjścia awaryjnego dla osobnego problemu (zademonstrowanego na przykładzie Space Explorers), a nie jako rytuału przed badaniami. |
| **Planowanie i wykonanie** | |
| `/10x-plan <change-id>` / `/10x-implement <change-id> phase <n>` | Użyj tego samego łańcucha planowania i wykonania co w Lekcji 2, teraz z dowodami z wcześniejszych badań zasilającymi plan. |

### Dyscyplina badawcza

- Badania wewnętrzne (`/10x-research`) odpowiadają na pytanie „co nasz codebase już robi?” — wzorce, schematy, konwencje, punkty integracji.
- Badania zewnętrzne (exa.ai, Context7) odpowiadają na pytanie „co powinniśmy zrobić?” — możliwości bibliotek, dokumentacja API, najlepsze praktyki ekosystemu.
- Połącz oba jako wkład oparty na dowodach dla `/10x-plan`. Plan bez dowodów badawczych w nietrywialnym strumieniu jest zgadywaniem.
- Dokumentacja przyjazna agentom (`llms.txt`, markdown-for-agents, endpointy `/md`) jest sygnałem jakości przy wyborze bibliotek — biblioteki publikujące dokumentację czytelną dla agentów integrują się szybciej.

### `/10x-frame` jako koło zapasowe

Trzy sygnały, by sięgnąć po `/10x-frame`:
1. Plan nie może się ustabilizować — badania stale otwierają kolejne pytania, zamiast zawężać się do kontraktu.
2. Plan nie dostarcza rezultatów — implementacja wielokrotnie nie spełnia kryteriów sukcesu.
3. Uporczywy dryf — implementacja stale odbiega od planu w sposób sugerujący, że problem został błędnie sformułowany.

Zademonstrowano na przykładzie Space Explorers, a nie na ścieżce SRS. To wyjście awaryjne, a nie obowiązkowy krok.

### Ścieżki używane przez tę lekcję

- `context/changes/<change-id>/research.md` - wynik badań wewnętrznych
- `context/changes/<change-id>/frame.md` - wynik ramowania problemu, gdy jest potrzebny
- `context/changes/<change-id>/plan.md` - kontrakt implementacyjny oparty na dowodach
- `context/foundation/lessons.md` - powtarzające się reguły i pułapki

Skills nie mogą zapisywać do `context/archive/`. Zarchiwizowane zmiany są niezmienne; jeśli rozstrzygnięta docelowa ścieżka zaczyna się od `context/archive/`, przerwij z komunikatem: „This change is archived. Open a new change with `/10x-new` instead.”

<!-- END @przeprogramowani/10x-cli -->
