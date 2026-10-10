# Implementation review fixes

## F1 — Błąd po częściowej odpowiedzi może zostać uznany za sukces

- **Decision**: FIXED — Napraw teraz w tym worktree, wybrano przez użytkownika 2026-10-10.
- **Scope**: Uzupełnienie bieżącej zmiany o `choices[0].error`; pierwotny zakres raportu (fazy 1, 2) pozostaje bez zmian.
- [x] Odczytać zagnieżdżony błąd w `OpenRouterRawResponse`, z pierwszeństwem obiektu błędu na najwyższym poziomie.
- [x] Sprawdzać obserwację przed akceptacją niepustego wyniku w `OpenRouterClient`; częściowego tekstu nie zwracać jako sukcesu.
- [x] Dodać testy: 502 → sukces bez częściowego tekstu, 502×3 → błąd, 401 → błąd konfiguracji bez ponowienia i bez ujawnienia klucza.
- [x] Uzupełnić zasadę w `llm/CLAUDE.md`.
- [x] Testy klienta: `./mvnw -B test -Dtest=OpenRouterClientTest` — 22 testy, bez błędów.
- [x] Pełne `./mvnw -B verify` po poprawce — 499 testów jednostkowych + 95 integracyjnych, bez błędów; log: `/private/tmp/openrouter-in-body-errors-triage-verify.log`.
