# Follow-ups z przeglądu implementacji (`reviews/impl-review-phase-1.md`)

## F4 — Hikari traci całą pulę połączeń przy bezczynności (osobna zmiana)

- **Obserwacja (PR env `szczur-radar-pr-33`, 2026-10-06 06:17:00, 06:22:00):** `HikariPool-1 - Failed to validate connection … (This connection has been closed.)` dla wszystkich 10 połączeń w puli, gdy rusza czyszczenie sesji Spring Session (`spring-session-1`). Hikari odrzuca martwe połączenia i otwiera nowe — nie jest to błąd żądań.
- **Prawdopodobna przyczyna:** uśpiony Postgres (`sleepApplication`) albo sieć prywatna Railway zamykają bezczynne połączenia. Niezależne od obrazu Dockera.
- **Produkcja (2026-10-06, po merge #33):** potwierdzone — te same ostrzeżenia z wątków `HikariPool-1:housekeeper` i `spring-session-1` po kilku minutach bezczynności. Do sprawdzenia: czy występowały też przed `api-docker-image` (Railpack).
- **Kierunek poprawki (osobna zmiana, poza `api-docker-image`):** `spring.datasource.hikari.keepalive-time` / `max-lifetime` krótsze niż czas zamknięcia połączeń po stronie Railway albo mniejsze `minimum-idle`; zweryfikować w PR env.
