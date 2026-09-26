# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## Nie myl wyboru startera z pełnym projektem stacku technologicznego

- **Context**: Praca greenfield z `/10x-tech-stack-selector`, gdy projekt ma więcej zależności i elementów niż sam framework startowy.
- **Problem**: `tech-stack.md` może wyglądać zbyt skromnie, jeśli oczekuje się od niego pełnej specyfikacji technologicznej projektu. Skill jest jednak zaprojektowany przede wszystkim jako kontrakt przekazania do `/10x-bootstrapper`: wybiera agent-friendly starter, zapisuje sygnały projektu i uzasadnienie wyboru. Nie musi zawierać kompletnej listy bibliotek i decyzji implementacyjnych.
- **Rule**: Najpierw sprawdź kontrakt artefaktu i rolę skilla w całym workflow. Nie rozszerzaj `tech-stack.md` na siłę o ORM, auth, biblioteki domenowe, test tooling i inne dependencies, jeśli handoff schema tego nie wymaga — szczegółowy stack materializuje się podczas bootstrapu i późniejszego planowania implementacji. Na ścieżce custom warto zakwestionować domyślną rekomendację startera, jeśli nie pasuje do charakteru aplikacji, ale po decyzji wróć do kontraktu skilla zamiast zamieniać selector w pełny proces projektowania architektury.
- **Applies to**: plan
