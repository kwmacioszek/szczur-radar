# Implementation review fixes

- [x] F1 — Poprawiono polecenie w opisowych kryteriach fazy 4 na `rg -n -F` i dopisano sprostowanie wyniku kontroli 4.5. Ponowne wykonanie: exit 0, oba pliki znalezione. Historyczna sekcja Progress bez zmian.
- [x] F2 — Sprostowano deklaracje zachowania selftext i dodano w README odtwarzalny filtr anonimizacji wzmianki posta 1xh5lkk. Oba filtry wykonane na obu fixture’ach zachowują ich zawartość; przypadek z syntetycznym nickiem potwierdza zamianę etykiety i URL-u bez zmian innych pól. Pliki JSON bez zmian.
- [x] F3 — RedditMapper zamienia DateTimeException na UnmappableItemException. Test fetchera dla Instant.MAX.getEpochSecond() i Long.MAX_VALUE zachowuje pozostałe 13 postów przy fetched=14 i skipped=1. Przed poprawką oba przypadki odtworzyły błąd; po poprawce Reddit*Test: 70 testów bez błędów i pominięć.

## Faza 5 (reviews/impl-review-phase-5.md)

- [x] F1 — Rollback S-27 opisuje warunki reverta (plik V13, `preserve()`, posty bez mappera); aktualizacja w S-20.
- [x] F2 — Teza o 20 ocenach zastąpiona pomiarem (70 ocenionych, 9 `dead`, wyłącznie walidacja wyjścia modelu, przyczyna nieustalona); wynik z `e2bafce`; krok trzeciej nocy.
- [x] F3 — Wyłączenie Reddita według S-19, z kontrolą `/sources`.
- [x] F4 — Opóźnienie sygnału `empty` w runbooku i README; zapytanie SQL po dniach sprawdzone w PR env.
- [x] F5 — Dokumenty i ostrzeżenie CI o PR env bez repository variable; `gh variable delete` przy wyłączeniu.
- [x] F6 — Zaakceptowane dla konta testowego w PR env.
- [x] F7 — „bez analizy prawnej” w PRD.
- [x] F8 — Krok 1 po merge'u sprawdza wdrożenie z commitu merge'u.
- [x] F9 — Nieaktualne odwołania i sformułowania poprawione.
- [x] F10 — Implementation Note w fazie 5; Progress bez zmian.
